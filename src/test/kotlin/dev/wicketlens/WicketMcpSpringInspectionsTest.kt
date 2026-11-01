package dev.wicketlens

import com.google.gson.JsonParser
import com.intellij.codeHighlighting.HighlightDisplayLevel
import com.intellij.codeInsight.daemon.HighlightDisplayKey
import com.intellij.openapi.components.service
import com.intellij.profile.codeInspection.InspectionProjectProfileManager
import com.intellij.psi.PsiDocumentManager
import com.intellij.testFramework.PlatformTestUtil
import dev.wicketlens.mcp.*
import dev.wicketlens.spring.*
import dev.wicketlens.ui.WicketLensService
import kotlinx.coroutines.*

class WicketMcpSpringInspectionsTest : WicketSpringInspectionTestCase() {
    private val query = WicketPageAnalysisRequest()
    private val profile get() = InspectionProjectProfileManager.getInstance(project).currentProfile
    override fun setUp() {
        super.setUp()
        myFixture.enableInspections(WicketSpringInjectionInspection(),WicketSpringSerializationInspection(),
            WicketSpringAnnotationInspection(),WicketSpringBeanTypeInspection(),WicketSpringBootstrapInspection())
        WicketSpringInspectionReport.IDS.forEach { profile.setErrorLevel(HighlightDisplayKey.find(it)!!,HighlightDisplayLevel.WEAK_WARNING,project) }
    }
    private fun capture() = query.capture(project,"example.ExamplePage",view=WicketMcpReport.SPRING_INSPECTIONS)
    private fun findings() = capture().springInspections!!.findings
    private fun render(value: PageAnalysisSnapshot = capture()) = JsonParser.parseString(query.render(value,WicketMcpReport.SPRING_INSPECTIONS)).asJsonObject
    private fun failure(code: String, block: () -> Unit) {
        try { block(); fail("Expected $code") } catch (failure: PageAnalysisFailure) { assertEquals(code,failure.code) }
    }
    private fun config(annotations: String = "") = source("Config", """
        @org.springframework.context.annotation.Configuration $annotations public class Config {
          @org.springframework.context.annotation.Bean String orders(){return "text";}
        }
    """.trimIndent())

    fun testFindingsKeepExactNativeMessagesSeverityAndRanges() {
        val java = page("@SpringBean transient Service orders = null;")
        val expected = (injection(java).map { "WicketSpringInjection" to it } + serialization(java).map { "WicketSpringSerialization" to it })
        val response = render()
        assertEquals("wicket_get_spring_inspections",response["tool"].asString)
        assertEquals(1,response["schemaVersion"].asInt)
        assertFalse(response["truncated"].asBoolean)
        val snapshot = capture().springInspections!!
        assertEquals(2,snapshot.findings.size)
        for ((id, native) in expected) {
            val finding = snapshot.findings.single { it.inspectionId == id }
            assertEquals(native.descriptionTemplate,finding.message)
            assertEquals("WEAK WARNING",finding.severity)
            assertEquals(native.psiElement!!.textRange.startOffset,finding.location.offset)
            assertEquals(native.psiElement!!.textRange.endOffset,finding.endOffset)
            assertEquals(native.highlightType.name,finding.highlightType)
            assertEquals(listOf("page"),finding.nodeIds)
        }
        assertFalse(response["springInspections"].asJsonObject["runtimeValidityVerified"].asBoolean)
    }
    fun testConfiguredSeverityIsPreserved() {
        page("@SpringBean Service orders = null;")
        profile.setErrorLevel(HighlightDisplayKey.find("WicketSpringInjection")!!,HighlightDisplayLevel.ERROR,project)
        assertEquals("ERROR",findings().single().severity)
    }
    fun testDisabledInspectionsStayExcludedAndAreReportedAsDisabled() {
        page("@SpringBean transient Service orders = null;")
        profile.setToolEnabled("WicketSpringInjection",false,project)
        val snapshot = capture().springInspections!!
        assertEquals(listOf("WicketSpringSerialization"),snapshot.findings.map { it.inspectionId })
        assertFalse(snapshot.settings.single { it.inspectionId == "WicketSpringInjection" }.enabled)
    }
    fun testNativeFieldSuppressionIsHonored() {
        page("@SuppressWarnings({\"WicketSpringInjection\",\"WicketSpringSerialization\"}) @SpringBean transient Service orders = null;")
        assertTrue(findings().isEmpty())
    }
    fun testNativeNoInspectionCommentIsHonored() {
        val java = page("@SpringBean Service orders;","//noinspection WicketSpringInjection\norders = null;")
        assertTrue(injection(java).isEmpty())
        assertTrue(findings().isEmpty())
    }
    fun testMissingBootstrapFindingComesFromTheReviewedApplication() {
        val app = application(statements="super.init();")
        page("@SpringBean Service orders;")
        val finding = findings().single()
        val native = problems(app,WicketSpringBootstrapInspection()).single()
        assertEquals("WicketSpringBootstrap",finding.inspectionId)
        assertEquals(native.descriptionTemplate,finding.message)
        assertEquals(app.virtualFile.path,finding.location.file)
        assertTrue(finding.nodeIds.isEmpty())
    }
    fun testMultipleBootstrapRegistrationsKeepTheirNativeFinding() {
        application(statements="""super.init();
            getComponentInstantiationListeners().add(new org.apache.wicket.spring.injection.annot.SpringComponentInjector(this));
            getComponentInstantiationListeners().add(new org.apache.wicket.spring.injection.annot.SpringComponentInjector(this));""")
        page("@SpringBean Service orders;")
        assertTrue(findings().single().message.contains("multiple Spring component injectors"))
    }
    fun testPlainApplicationWithoutIntentDoesNotGetAMissingInjectorFinding() {
        application(statements="super.init();"); page("Service orders;")
        assertTrue(findings().isEmpty())
    }
    fun testAnnotationTargetChecksReuseTheNativeInspection() {
        val java = page("@SpringBean static Service shared; @SpringBean int count;")
        val actual = findings()
        assertEquals(problems(java,WicketSpringAnnotationInspection()).map { it.descriptionTemplate }.toSet(),actual.map { it.message }.toSet())
        assertEquals(2,actual.size)
    }
    fun testAutowiredFindingStillRequiresRecognizedRegistration() {
        application(); page("@org.springframework.beans.factory.annotation.Autowired Service orders;")
        assertEquals("WicketSpringAnnotation",findings().single().inspectionId)
    }
    fun testGenericInjectWithoutSpringIntentStaysQuiet() {
        page("@Inject transient Service orders = null;")
        assertTrue(findings().isEmpty())
    }
    fun testProxyAndSerializationRulesUseExistingApplicationEvidence() {
        source("FinalService","public final class FinalService {}")
        application(); page("@SpringBean FinalService orders;")
        assertTrue(findings().single().message.contains("cannot be subclass-proxied"))
    }
    fun testBeanSourceTypeWarningRetainsItsNativeText() {
        config()
        application(arguments="this, new org.springframework.context.annotation.AnnotationConfigApplicationContext(Config.class)")
        val java = page("@SpringBean(name=\"orders\") Service orders;")
        val actual = findings().single()
        assertEquals("WicketSpringBeanType",actual.inspectionId)
        assertEquals(problems(java,WicketSpringBeanTypeInspection()).single().descriptionTemplate,actual.message)
    }
    fun testUnknownProfilesDoNotBecomeDefinitiveBeanTypeFindings() {
        config("@org.springframework.context.annotation.Profile(\"prod\")")
        application(arguments="this, new org.springframework.context.annotation.AnnotationConfigApplicationContext(Config.class)")
        page("@SpringBean(name=\"orders\") Service orders;")
        assertTrue(findings().isEmpty())
    }
    fun testRepeatedComponentTypeHasOneFindingWithBothGraphNodes() {
        source("Child","public class Child extends org.apache.wicket.markup.html.basic.Label { @org.apache.wicket.spring.injection.annot.SpringBean transient Service orders; public Child(String id){super(id);} }")
        page("","add(new Child(\"left\")); add(new Child(\"right\"));")
        val snapshot = capture()
        val finding = snapshot.springInspections!!.findings.single()
        assertEquals(snapshot.report.nodes.filter { it.wicketId != null }.map { it.nodeId }.sorted(),finding.nodeIds)
        assertEquals("example.Child",finding.declaringClass)
    }
    fun testSourceAncestorsAreInspectedAndTheirLocationsTracked() {
        val base = source("BasePage","public class BasePage extends org.apache.wicket.markup.html.WebPage { @org.apache.wicket.spring.injection.annot.SpringBean transient Service orders; }")
        page("",base="BasePage")
        val snapshot = capture()
        assertEquals(base.virtualFile.path,snapshot.springInspections!!.findings.single().location.file)
        assertTrue(snapshot.version.sources.any { it.path == base.virtualFile.path })
    }
    fun testUnobservedHelpersAndSiblingClassesDoNotLeakIntoThePageReport() {
        source("Model","public class Model extends org.apache.wicket.model.Model<String> { @org.apache.wicket.spring.injection.annot.SpringBean Service orders; }")
        val java = page("")
        edit(java,java.text+"\nclass Other { @org.apache.wicket.spring.injection.annot.SpringBean Service orders; }")
        assertTrue(findings().isEmpty())
    }
    fun testProfileDisableChangeInvalidatesCapturedFindings() {
        page("@SpringBean Service orders = null;")
        val snapshot = capture()
        profile.setToolEnabled("WicketSpringInjection",false,project)
        failure("STALE_SNAPSHOT") { query.requireCurrent(project,snapshot) }
        assertTrue(findings().isEmpty())
    }
    fun testProfileSeverityChangeInvalidatesCapturedFindings() {
        page("@SpringBean Service orders = null;")
        val snapshot = capture()
        profile.setErrorLevel(HighlightDisplayKey.find("WicketSpringInjection")!!,HighlightDisplayLevel.WARNING,project)
        failure("STALE_SNAPSHOT") { query.requireCurrent(project,snapshot) }
        assertEquals("WARNING",findings().single().severity)
    }
    fun testSourceEditsAndUncommittedDocumentsInvalidateTheSnapshot() {
        val java = page("@SpringBean Service orders = null;")
        val snapshot = capture()
        edit(java,java.text.replace(" = null",""),commit=false)
        try {
            failure("STALE_SNAPSHOT") { query.requireCurrent(project,snapshot) }
            failure("UNCOMMITTED_DOCUMENTS") { capture() }
        } finally { PsiDocumentManager.getInstance(project).commitAllDocuments() }
        assertTrue(findings().isEmpty())
    }
    fun testFindingLimitIsExplicitAndDoesNotImplyCompleteCoverage() {
        page((0..256).joinToString(" ") { "@SpringBean Service field$it = null;" })
        val snapshot = capture()
        assertEquals(256,snapshot.springInspections!!.findings.size)
        assertTrue(snapshot.springInspections!!.truncated)
        assertTrue(render(snapshot)["truncated"].asBoolean)
        assertTrue(snapshot.springInspections!!.limitations.any { it.contains("not completed") })
    }
    fun testUnicodeResponseLimitRejectsWholeResponse() {
        page("@SpringBean Service orders = null;")
        val snapshot = capture()
        val data = snapshot.springInspections!!
        val large = snapshot.copy(springInspections=data.copy(findings=listOf(data.findings.single().copy(message="界".repeat(180_000)))))
        failure("RESPONSE_TOO_LARGE") { render(large) }
    }
    fun testSourceTypeLimitIsExplicitForLargeDiscoveredHierarchies() {
        for (i in 0..63) source("Child$i","public class Child$i extends org.apache.wicket.markup.html.basic.Label { @org.apache.wicket.spring.injection.annot.SpringBean Service orders = null; public Child$i(String id){super(id);} }")
        page("",(0..63).joinToString(" ") { "add(new Child$it(\"child$it\"));" })
        val result = capture().springInspections!!
        assertTrue(result.truncated)
        assertEquals(64,result.sourceFiles.size)
        assertTrue(result.limitations.any { it.contains("64 types") })
    }
    fun testDeepSourceAncestryIsMarkedLimitedInsteadOfReportingCompleteCoverage() {
        for (i in 12 downTo 0) source("Base$i","public class Base$i extends ${if(i==12) "org.apache.wicket.markup.html.WebPage" else "Base${i+1}"} {}")
        page("",base="Base0")
        val result = capture().springInspections!!
        assertTrue(result.truncated)
        assertEquals(12,result.sourceFiles.size)
        assertTrue(result.limitations.any { it.contains("12 types") })
    }
    fun testOtherResponsesAndComponentSchemaStayUnchanged() {
        page("@SpringBean Service orders = null;")
        val plain = query.capture(project,"example.ExamplePage")
        val inspected = capture()
        assertNull(plain.springInspections)
        for (view in listOf(WicketMcpReport.STRUCTURE,WicketMcpReport.VALIDATION))
            assertEquals(query.render(plain,view),query.render(inspected,view))
    }
    fun testAsyncInspectionIsDeterministicReadOnlyAndDoesNotPublishSidebarState() {
        val java = page("@SpringBean transient Service orders = null;")
        val before = java.text
        val state = project.service<WicketLensService>().state
        val scope = CoroutineScope(SupervisorJob()+Dispatchers.Default)
        try {
            val responses = (0..1).map { scope.async { query.execute(project,"example.ExamplePage",view=WicketMcpReport.SPRING_INSPECTIONS) } }
            responses.forEach { PlatformTestUtil.waitWithEventsDispatching("MCP Spring inspections",{it.isCompleted},20) }
            assertEquals(runBlocking { responses[0].await() },runBlocking { responses[1].await() })
            assertEquals(before,java.text)
            assertSame(state,project.service<WicketLensService>().state)
        } finally { scope.cancel() }
    }
}

class WicketMcpSpringInspectionsWithoutSpringTest : WicketProjectPsiTestCase() {
    fun testSpringFreeWicketProjectRemainsQuietAndAvailable() {
        pageFile()
        val query = WicketPageAnalysisRequest()
        val snapshot = query.capture(project,"example.ExamplePage",view=WicketMcpReport.SPRING_INSPECTIONS)
        assertTrue(snapshot.springInspections!!.findings.isEmpty())
        assertFalse(snapshot.springInspections!!.truncated)
        assertFalse(JsonParser.parseString(query.render(snapshot,WicketMcpReport.SPRING_INSPECTIONS)).asJsonObject["springInspections"].asJsonObject["runtimeValidityVerified"].asBoolean)
    }
}
