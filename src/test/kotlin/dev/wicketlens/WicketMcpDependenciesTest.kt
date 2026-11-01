package dev.wicketlens

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.intellij.openapi.components.service
import com.intellij.psi.PsiDocumentManager
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.PsiTestUtil
import dev.wicketlens.mcp.*
import dev.wicketlens.spring.*
import dev.wicketlens.ui.WicketLensService
import kotlinx.coroutines.*
import org.jetbrains.jps.model.java.JavaSourceRootType

class WicketMcpDependenciesTest : WicketSpringInspectionTestCase() {
    private val query = WicketPageAnalysisRequest()
    private fun config(methods: String = "@org.springframework.context.annotation.Bean({\"orders\",\"alias\"}) Service orders(){return null;}") =
        source("Config", "@org.springframework.context.annotation.Configuration public class Config { $methods }")
    private fun capture(name: String = "ExamplePage", sourceSet: String = "main") =
        query.capture(project, "example.$name", sourceSet = sourceSet, view = WicketMcpReport.DEPENDENCIES)
    private fun render(snapshot: PageAnalysisSnapshot = capture()) =
        JsonParser.parseString(query.render(snapshot, WicketMcpReport.DEPENDENCIES)).asJsonObject
    private fun payload(snapshot: PageAnalysisSnapshot = capture()) = render(snapshot)["dependencies"].asJsonObject
    private fun firstField(value: JsonObject = payload()) = value["owners"].asJsonArray.first().asJsonObject["fields"].asJsonArray.first().asJsonObject
    private fun candidate(value: JsonObject = firstField()) = value["candidates"].asJsonArray.single().asJsonObject
    private fun failure(code: String, action: () -> Unit) {
        try { action(); fail("Expected $code") } catch (failure: PageAnalysisFailure) { assertEquals(code, failure.code) }
    }
    private fun execute(): String {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val result = scope.async { query.execute(project, "example.ExamplePage", view = WicketMcpReport.DEPENDENCIES) }
            PlatformTestUtil.waitWithEventsDispatching("MCP dependencies", { result.isCompleted }, 20)
            return runBlocking { result.await() }
        } finally { scope.cancel() }
    }

    fun testVersionedEnvelopeReusesTheCoreSnapshotAndItsSourceLocations() {
        val config = config(); val java = page("@SpringBean(name=\"alias\", required=false) Service orders;")
        val snapshot = capture()
        val expected = WicketDependencyAnalyzer().analyze(project, snapshot.report)
        assertEquals(expected, snapshot.dependencies)
        val envelope = render(snapshot)
        assertEquals(1, envelope["schemaVersion"].asInt)
        assertEquals("wicket_get_injected_dependencies", envelope["tool"].asString)
        assertFalse(envelope.has("analysis"))
        assertEquals(module.name, envelope["target"].asJsonObject["moduleName"].asString)
        val data = envelope["dependencies"].asJsonObject
        assertEquals(1, data["schemaVersion"].asInt)
        assertEquals("source_candidates", data["evidence"].asString)
        assertFalse(data["runtimeBindingsVerified"].asBoolean)
        assertFalse(data["profileActivationEvaluated"].asBoolean)
        assertTrue(data["registeredApplication"].isJsonNull)
        val owner = data["owners"].asJsonArray.single().asJsonObject
        assertEquals(expected.owners.single().id, owner["nodeId"].asString)
        assertEquals("ExamplePage", owner["displayPath"].asString)
        val field = firstField(data)
        assertEquals("NAME", field["lookup"].asString)
        assertFalse(field["required"].asBoolean)
        assertEquals(java.virtualFile.path, field["location"].asJsonObject["file"].asString)
        val bean = candidate(field)
        assertEquals(listOf("alias","orders"), bean["names"].asJsonArray.map { it.asString })
        assertTrue(bean["compatible"].asBoolean)
        assertEquals(config.virtualFile.path, bean["location"].asJsonObject["file"].asString)
        assertTrue(snapshot.version.sources.any { it.path == config.virtualFile.path })
    }
    fun testProfilesAndFactoryProductInstanceLookupsStayDistinct() {
        config("@org.springframework.context.annotation.Bean(\"orders\") @org.springframework.context.annotation.Profile({\"prod & eu\",\"staging\"}) org.springframework.beans.factory.FactoryBean<Service> orders(){return null;}")
        page("@SpringBean Service product; @SpringBean(name=\"&orders\") org.springframework.beans.factory.FactoryBean<Service> factory;")
        val fields = payload()["owners"].asJsonArray.single().asJsonObject["fields"].asJsonArray
        val product = candidate(fields[0].asJsonObject)
        assertEquals("example.Service", product["type"].asString)
        assertEquals("((prod & eu) OR (staging))", product["profiles"].asJsonArray.single().asString)
        assertTrue(product["factory"].asBoolean)
        assertFalse(product["factoryLookup"].asBoolean)
        val factory = candidate(fields[1].asJsonObject)
        assertEquals("&orders", factory["names"].asJsonArray.single().asString)
        assertTrue(factory["factoryLookup"].asBoolean)
        assertEquals(factory["declaredFactoryType"], factory["type"])
    }
    fun testRawFactoryProductAndUnknownProfileKeepNullableCompatibility() {
        config("@org.springframework.context.annotation.Bean @org.springframework.context.annotation.Profile(Missing.VALUE) org.springframework.beans.factory.FactoryBean orders(){return null;}")
        page("@SpringBean(name=\"orders\") Service orders;")
        val bean = candidate()
        assertTrue(bean["compatible"].isJsonNull)
        assertEquals("<unknown product type>", bean["type"].asString)
        assertTrue(bean["profilesUnknown"].asBoolean)
    }
    fun testMissingNamedBeanDoesNotBecomeADiagnostic() {
        page("@SpringBean(name=\"external\",required=false) Service orders;")
        assertTrue(firstField()["candidates"].asJsonArray.isEmpty)
        assertFalse(payload().has("findings"))
        assertTrue(payload()["scopeNotes"].asJsonArray.any { "not establish" in it.asString })
    }
    fun testUnsupportedAndUnknownLookupsKeepTheirOriginalNotes() {
        page("static final String NAME=\"orders\"; @SpringBean static Service shared; @SpringBean int number; @SpringBean(name=NAME) Service unknown;")
        val fields = payload()["owners"].asJsonArray.single().asJsonObject["fields"].asJsonArray
        assertEquals(listOf("UNSUPPORTED","UNSUPPORTED","UNKNOWN"), fields.map { it.asJsonObject["lookup"].asString })
        assertTrue(fields.all { !it.asJsonObject["notes"].asJsonArray.isEmpty })
    }
    fun testGenericInjectStillRequiresSpringEvidence() {
        page("@Inject Service orders;")
        assertTrue(payload()["owners"].asJsonArray.isEmpty)
    }
    fun testRegisteredApplicationEvidenceIsPreserved() {
        application(); page("@Inject Service orders;")
        assertEquals("example.SpringApplication", payload()["registeredApplication"].asString)
        assertEquals("Inject", firstField()["annotation"].asString)
    }
    fun testDiscoveredChildFieldsHaveTheSameGraphNodeIdentity() {
        source("OrdersLabel", "public class OrdersLabel extends org.apache.wicket.markup.html.basic.Label { @org.apache.wicket.spring.injection.annot.SpringBean Service orders; public OrdersLabel(String id){super(id);} }")
        page("", "add(new OrdersLabel(\"title\"));")
        val snapshot = capture()
        val owner = payload(snapshot)["owners"].asJsonArray.single().asJsonObject
        assertEquals(snapshot.report.nodes.single { it.wicketId == "title" }.nodeId, owner["nodeId"].asString)
        assertEquals("ExamplePage:title", owner["displayPath"].asString)
    }
    fun testProductionAndTestBeanCatalogsStaySeparate() {
        PsiTestUtil.addSourceRoot(module, myFixture.tempDirFixture.findOrCreateDir("src/test/java"), JavaSourceRootType.TEST_SOURCE)
        source("TestConfig", "public class TestConfig { @org.springframework.context.annotation.Bean Service onlyTest(){return null;} }", "src/test/java")
        page("@SpringBean(name=\"onlyTest\") Service orders;")
        assertTrue(firstField()["candidates"].asJsonArray.isEmpty)
        source("TestPage", "public class TestPage extends org.apache.wicket.markup.html.WebPage { @org.apache.wicket.spring.injection.annot.SpringBean Service orders; }", "src/test/java")
        assertEquals("onlyTest", candidate(firstField(payload(capture("TestPage","test"))))["names"].asJsonArray.single().asString)
    }
    fun testCandidateLimitsAreNotHiddenByTheEnvelope() {
        config((0..33).joinToString(" ") { "@org.springframework.context.annotation.Bean Service bean$it(){return null;}" })
        page("@SpringBean Service orders;")
        val response = render()
        assertTrue(response["truncated"].asBoolean)
        assertTrue(response["dependencies"].asJsonObject["discoveryLimited"].asBoolean)
        val field = firstField(response["dependencies"].asJsonObject)
        assertTrue(field["candidatesTruncated"].asBoolean)
        assertEquals(32, field["candidates"].asJsonArray.size())
    }
    fun testFieldLimitAndPartialOwnersAreExplicit() {
        page((0..256).joinToString(" ") { "@SpringBean Service field$it;" })
        val result = render()
        assertTrue(result["truncated"].asBoolean)
        assertTrue(result["dependencies"].asJsonObject["truncated"].asBoolean)
        assertEquals(256, result["dependencies"].asJsonObject["fieldCount"].asInt)
    }
    fun testResponseLimitCountsDependencyUnicodeAndReturnsNoPartialJson() {
        page("@SpringBean Service orders;")
        val old = capture()
        val dependencies = old.dependencies!!
        val owner = dependencies.owners.single()
        val field = owner.fields.single().copy(notes=listOf("界".repeat(180_000)))
        val large = old.copy(dependencies=dependencies.copy(owners=listOf(owner.copy(fields=listOf(field)))))
        failure("RESPONSE_TOO_LARGE") { render(large) }
    }
    fun testEmptySpringPageDoesNotEnableOrChangeOtherToolContracts() {
        page("@SpringBean Service orders;")
        val before = query.capture(project,"example.ExamplePage")
        assertNull(before.dependencies)
        val after = capture()
        for (view in listOf(WicketMcpReport.STRUCTURE,WicketMcpReport.VALIDATION))
            assertEquals(query.render(before,view),query.render(after,view))
    }
    fun testDependencyEditsInvalidateTheSnapshotWithoutChangingItsValues() {
        val config = config("@org.springframework.context.annotation.Bean Service orders(){return null;}"); page("@SpringBean Service orders;")
        val snapshot = capture()
        val old = render(snapshot)
        edit(config,config.text.replace("orders()","renamed()"))
        failure("STALE_SNAPSHOT") { query.requireCurrent(project,snapshot) }
        assertEquals(old,render(snapshot))
        assertEquals("renamed",candidate()["names"].asJsonArray.single().asString)
    }
    fun testAsyncDependencyRequestIsReadOnlyAndDoesNotPublishSidebarState() {
        val config = config(); val java = page("@SpringBean Service orders;")
        val service = project.service<WicketLensService>()
        val state = service.state
        val before = java.text to config.text
        assertEquals(execute(),execute())
        assertSame(state,service.state)
        assertEquals(before,java.text to config.text)
    }
    fun testUncommittedBeanEditsPreventCaptureAndStaleUse() {
        val config = config(); page("@SpringBean Service orders;")
        val snapshot = capture()
        edit(config,config.text.replace("orders()","changed()"),commit=false)
        try {
            failure("UNCOMMITTED_DOCUMENTS") { capture() }
            failure("STALE_SNAPSHOT") { query.requireCurrent(project,snapshot) }
        } finally { PsiDocumentManager.getInstance(project).commitAllDocuments() }
    }
    fun testErrorsIdentifyTheNewTool() {
        failure("CLASS_NOT_FOUND") { capture() }
        val error = JsonParser.parseString(WicketPageAnalysisRequest.errorJson("CLASS_NOT_FOUND","No source",WicketMcpReport.DEPENDENCIES)).asJsonObject
        assertEquals("wicket_get_injected_dependencies",error["tool"].asString)
    }
}

class WicketMcpDependenciesWithoutSpringTest : WicketProjectPsiTestCase() {
    fun testPlainWicketWithoutSpringReturnsAnExplicitEmptyDependencySnapshot() {
        pageFile()
        val query = WicketPageAnalysisRequest()
        val snapshot = query.capture(project,"example.ExamplePage",view=WicketMcpReport.DEPENDENCIES)
        val response = JsonParser.parseString(query.render(snapshot,WicketMcpReport.DEPENDENCIES)).asJsonObject
        assertTrue(response["dependencies"].asJsonObject["owners"].asJsonArray.isEmpty)
        assertFalse(response["truncated"].asBoolean)
    }
}
