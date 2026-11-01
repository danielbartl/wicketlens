package dev.wicketlens

import com.google.gson.JsonParser
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.components.service
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.impl.PsiDocumentManagerBase
import com.intellij.testFramework.DumbModeTestUtils
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.PsiTestUtil
import dev.wicketlens.mcp.*
import dev.wicketlens.spring.WicketSpringTestGenerator
import dev.wicketlens.ui.WicketLensService
import kotlinx.coroutines.*
import org.jetbrains.jps.model.java.JavaSourceRootType

class WicketMcpSpringTestPlanTest : WicketSpringInspectionTestCase() {
    private val view = WicketMcpReport.SPRING_TEST_PLAN
    private val query = WicketPageAnalysisRequest()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    override fun tearDown() { try { scope.cancel() } finally { super.tearDown() } }
    private fun capture(name: String = "ExamplePage", sourceSet: String = "main") =
        query.capture(project, "example.$name", sourceSet = sourceSet, view = view)
    private fun render(snapshot: PageAnalysisSnapshot = capture()) = JsonParser.parseString(query.render(snapshot, view)).asJsonObject
    private fun data(snapshot: PageAnalysisSnapshot = capture()) = render(snapshot)["springTestPlan"].asJsonObject
    private fun failure(code: String, block: () -> Unit) {
        try { block(); fail("Expected $code") } catch (failure: PageAnalysisFailure) { assertEquals(code, failure.code) }
    }
    private fun launch(request: WicketPageAnalysisRequest = query) = scope.async {
        request.execute(project, "example.ExamplePage", view = view)
    }
    private fun <T> await(result: Deferred<T>): T {
        PlatformTestUtil.waitWithEventsDispatching("Spring test plan", { result.isCompleted }, 20)
        return runBlocking { result.await() }
    }
    private fun pending(result: Deferred<*>) { await(scope.async { delay(150) }); assertFalse(result.isCompleted) }

    fun testPlanAndSourceExactlyReuseTheExistingGeneratorWithoutProductionBeanSelection() {
        source("Config", "public class Config { @org.springframework.context.annotation.Bean(\"production\") Service production(){return null;} }")
        val file = page("@SpringBean Service orders;")
        val expected = WicketSpringTestGenerator().prepare(project, file.virtualFile.url, -1)
        val snapshot = capture()
        assertEquals(expected.plan, snapshot.springTest!!.plan)
        assertEquals(expected.roots, snapshot.springTest!!.roots)
        val response = render(snapshot)
        assertEquals(1, response["schemaVersion"].asInt)
        assertEquals(view.tool, response["tool"].asString)
        assertFalse(response["truncated"].asBoolean)
        assertFalse(response.has("analysis"))
        val plan = data(snapshot)
        assertEquals(1, plan["schemaVersion"].asInt)
        assertEquals("PAGE", plan["constructorStrategy"].asString)
        assertEquals("ExamplePageSpringTest", plan["suggestedTestName"].asString)
        assertEquals("orders", plan["mocks"].asJsonArray.single().asJsonObject["beanName"].asString)
        assertEquals(WicketSpringTestGenerator().render(expected.plan), plan["source"].asString)
        assertFalse(plan["runtimeValidityVerified"].asBoolean)
        assertFalse(plan["dependencyAvailabilityChecked"].asBoolean)
        assertEquals(WicketSpringTestGenerator.REQUIRED_DEPENDENCIES, plan["requiredDependencies"].asJsonArray.map { it.asString })
        assertTrue(plan["source"].asString.contains("registerSingleton(\"orders\""))
        assertFalse(plan["source"].asString.contains("registerSingleton(\"production\""))
    }
    fun testUnsupportedBindingsAndFactoryLookupsRetainManualGuards() {
        page("@SpringBean Service initialized = null; @SpringBean(name=\"&orders\") org.springframework.beans.factory.FactoryBean<Service> factory; @SpringBean String finalType;")
        val plan = data()
        assertEquals(3, plan["manualBindings"].asJsonArray.size())
        assertTrue(plan["mocks"].asJsonArray.isEmpty)
        assertTrue(plan["source"].asString.contains("throw new UnsupportedOperationException("))
        assertEquals(capture().springTest!!.plan.manual, plan["manualBindings"].asJsonArray.map { it.asString })
    }
    fun testBoundedDiscoveryKeepsAnExplicitManualReviewGuard() {
        page((0..256).joinToString(" ") { "@SpringBean Service field$it;" })
        val result = data()
        assertEquals(256, result["mocks"].asJsonArray.size())
        assertTrue(result["manualBindings"].asJsonArray.any { it.asString.contains("discovery is incomplete") })
        assertTrue(result["source"].asString.contains("throw new UnsupportedOperationException("))
    }
    fun testManualConstructorRetainsTheThrowingCreateSubjectHook() {
        source("ExamplePage", "public class ExamplePage extends org.apache.wicket.markup.html.WebPage { @org.apache.wicket.spring.injection.annot.SpringBean Service service; public ExamplePage(int value){} }")
        val plan = data()
        assertEquals("MANUAL_PAGE", plan["constructorStrategy"].asString)
        assertTrue(plan["source"].asString.contains("throw new UnsupportedOperationException(\"Configure the subject constructor\")"))
    }
    fun testPanelUsesTheExistingComponentConstructorAndRenderingStrategy() {
        source("ExamplePage", "public class ExamplePage extends org.apache.wicket.markup.html.panel.Panel { @org.apache.wicket.spring.injection.annot.SpringBean Service service; public ExamplePage(String id){super(id);} }")
        val plan = data()
        assertEquals("COMPONENT", plan["constructorStrategy"].asString)
        assertTrue(plan["source"].asString.contains("tester.startComponentInPage(new example.ExamplePage(\"subject\"))"))
    }
    fun testPageParametersConstructorIsPreserved() {
        source("ExamplePage", "public class ExamplePage extends org.apache.wicket.markup.html.WebPage { @org.apache.wicket.spring.injection.annot.SpringBean Service service; public ExamplePage(org.apache.wicket.request.mapper.parameter.PageParameters p){super(p);} }")
        assertEquals("PARAMETERS", data()["constructorStrategy"].asString)
    }
    fun testRegisteredSpringApplicationAllowsAPageWithoutInjectionFields() {
        application(); page("")
        assertTrue(data()["mocks"].asJsonArray.isEmpty)
    }
    fun testPlainWicketWithSpringOnTheClasspathStillRequiresInjectionIntent() {
        page("")
        failure("TEST_PLAN_UNAVAILABLE") { capture() }
    }
    fun testAbstractGenericAndNestedTargetsKeepTheGeneratorsUnsupportedBoundary() {
        source("AbstractPage", "public abstract class AbstractPage extends org.apache.wicket.markup.html.WebPage { @org.apache.wicket.spring.injection.annot.SpringBean Service service; }")
        source("GenericPage", "public class GenericPage<T> extends org.apache.wicket.markup.html.WebPage { @org.apache.wicket.spring.injection.annot.SpringBean Service service; }")
        source("Outer", "public class Outer { public static class Nested extends org.apache.wicket.markup.html.WebPage { @org.apache.wicket.spring.injection.annot.SpringBean Service service; } }")
        for (name in listOf("AbstractPage", "GenericPage", "Outer.Nested")) failure("TEST_PLAN_UNAVAILABLE") { capture(name) }
    }
    fun testTestRootsAreOptionalAndNeverCreatedByTheRequest() {
        page("@SpringBean Service service;")
        assertTrue(data()["testRoots"].asJsonArray.isEmpty)
        val root = myFixture.tempDirFixture.findOrCreateDir("existing-tests")
        PsiTestUtil.addSourceRoot(module, root, JavaSourceRootType.TEST_SOURCE)
        val result = data()
        assertEquals(root.path, result["testRoots"].asJsonArray.single().asJsonObject["path"].asString)
        assertEquals(0, root.children.size)
    }
    fun testSourceSetSelectsItsOwnSubjectAndDoesNotFallBack() {
        page("@SpringBean Service mainService;")
        PsiTestUtil.addSourceRoot(module, myFixture.tempDirFixture.findOrCreateDir("src/test/java"), JavaSourceRootType.TEST_SOURCE)
        source("ExamplePage", "public class ExamplePage extends org.apache.wicket.markup.html.WebPage { @org.apache.wicket.spring.injection.annot.SpringBean Service testService; }", "src/test/java")
        assertEquals("mainService", data()["mocks"].asJsonArray.single().asJsonObject["beanName"].asString)
        assertEquals("testService", data(capture(sourceSet="test"))["mocks"].asJsonArray.single().asJsonObject["beanName"].asString)
        failure("AMBIGUOUS_CLASS") { capture(sourceSet="any") }
    }
    fun testDependencyAndRootEditsInvalidateTheSnapshotWithoutMutatingCapturedValues() {
        val dependency = source("Dependency", "public class Dependency {}")
        page("@SpringBean Dependency service;")
        val captured = capture(); val before = render(captured)
        assertTrue(captured.version.sources.any { it.path == dependency.virtualFile.path })
        edit(dependency, dependency.text.replace("class Dependency", "final class Dependency"))
        failure("STALE_SNAPSHOT") { query.requireCurrent(project, captured) }
        assertEquals(before, render(captured))
        assertTrue(data()["mocks"].asJsonArray.isEmpty)
        val newer = capture()
        PsiTestUtil.addSourceRoot(module, myFixture.tempDirFixture.findOrCreateDir("tests"), JavaSourceRootType.TEST_SOURCE)
        failure("STALE_SNAPSHOT") { query.requireCurrent(project, newer) }
    }
    fun testResponseBudgetIncludesUnicodeNotesAndTheEntireScaffold() {
        page("@SpringBean Service service;")
        val snapshot = capture(); val prepared = snapshot.springTest!!
        failure("RESPONSE_TOO_LARGE") { render(snapshot.copy(springTest=prepared.copy(plan=prepared.plan.copy(notes=listOf("界".repeat(180_000)))))) }
        failure("RESPONSE_TOO_LARGE") { render(snapshot.copy(springTest=prepared.copy(plan=prepared.plan.copy(manual=listOf("界".repeat(70_000)))))) }
        failure("ANALYSIS_UNAVAILABLE") { render(snapshot.copy(springTest=null)) }
    }
    fun testIndexingCancellationTimeoutAndRecoveryUseTheSharedRequestLifecycle() {
        page("@SpringBean Service service;")
        val token = DumbModeTestUtils.startEternalDumbModeTask(project)
        try {
            val request = launch(); pending(request); request.cancel()
            try { await(request); fail("Expected cancellation") } catch (_: CancellationException) { }
            failure("TIMED_OUT") { await(launch(WicketPageAnalysisRequest(500))) }
        } finally { DumbModeTestUtils.endEternalDumbModeTaskAndWaitForSmartMode(project, token) }
        assertEquals("ok", JsonParser.parseString(await(launch())).asJsonObject["status"].asString)
    }
    fun testDocumentWaitDoesNotCommitSaveOrPublishAndConcurrentCancellationIsIsolated() {
        val file = page("@SpringBean Service before;")
        val original = capture()
        val documents = PsiDocumentManager.getInstance(project)
        documents.commitAllDocuments()
        val state = project.service<WicketLensService>().state
        val disk = file.virtualFile.contentsToByteArray().toList()
        val save = com.intellij.ide.SaveAndSyncHandler.getInstance().disableAutoSave()
        com.intellij.openapi.util.Disposer.register(testRootDisposable, com.intellij.openapi.Disposable { save.finish() })
        (documents as PsiDocumentManagerBase).disableBackgroundCommit(testRootDisposable)
        WriteCommandAction.runWriteCommandAction(project) { documents.getDocument(file)!!.setText(file.text.replace("before", "after")) }
        failure("UNCOMMITTED_DOCUMENTS") { capture() }
        failure("STALE_SNAPSHOT") { query.requireCurrent(project, original) }
        val cancelled = launch(); val survivor = launch()
        pending(survivor); cancelled.cancel()
        try { await(cancelled); fail("Expected cancellation") } catch (_: CancellationException) { }
        failure("TIMED_OUT") { await(launch(WicketPageAnalysisRequest(500))) }
        assertTrue(documents.isUncommited(documents.getDocument(file)!!))
        assertEquals(disk, file.virtualFile.contentsToByteArray().toList())
        documents.commitAllDocuments()
        val result = await(survivor)
        assertEquals("after", JsonParser.parseString(result).asJsonObject["springTestPlan"].asJsonObject["mocks"].asJsonArray.single().asJsonObject["beanName"].asString)
        assertEquals(result, await(launch()))
        assertSame(state, project.service<WicketLensService>().state)
        assertTrue(documents.getDocument(file)!!.text.contains("Service after;"))
        assertEquals(disk, file.virtualFile.contentsToByteArray().toList())
    }
}

class WicketMcpSpringTestPlanWithoutSpringTest : WicketProjectPsiTestCase() {
    fun testSpringFreePageReturnsAVersionedUnsupportedPlanError() {
        pageFile()
        val view = WicketMcpReport.SPRING_TEST_PLAN
        try { WicketPageAnalysisRequest().capture(project,"example.ExamplePage",view=view); fail("Expected no Spring intent") }
        catch (failure: PageAnalysisFailure) {
            assertEquals("TEST_PLAN_UNAVAILABLE",failure.code)
            val error = JsonParser.parseString(WicketPageAnalysisRequest.errorJson(failure.code,failure.message!!,view)).asJsonObject
            assertEquals(view.tool,error["tool"].asString)
            assertEquals("error",error["status"].asString)
        }
    }
}
