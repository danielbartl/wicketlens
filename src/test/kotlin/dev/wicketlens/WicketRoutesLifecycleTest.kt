package dev.wicketlens

import com.google.gson.JsonParser
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.components.service
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.impl.PsiDocumentManagerBase
import com.intellij.testFramework.DumbModeTestUtils
import com.intellij.testFramework.PlatformTestUtil
import dev.wicketlens.mcp.PageAnalysisFailure
import dev.wicketlens.routes.WicketRoutesRequest
import dev.wicketlens.ui.WicketLensService
import kotlinx.coroutines.*

class WicketRoutesLifecycleTest : WicketProjectPsiTestCase() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    override fun tearDown() { try { scope.cancel() } finally { super.tearDown() } }
    private fun application() = myFixture.addFileToProject("src/main/java/example/App.java", """
        package example; public class App extends org.apache.wicket.protocol.http.WebApplication {
            public Class<? extends org.apache.wicket.Page> getHomePage() { return ExamplePage.class; }
            protected void init() { super.init(); mountPage("/old", ExamplePage.class); }
        }
    """.trimIndent())
    private fun request(query: WicketRoutesRequest = WicketRoutesRequest()) = scope.async { query.execute(project) }
    private fun <T> await(result: Deferred<T>): T {
        PlatformTestUtil.waitWithEventsDispatching("Mounted URL request", { result.isCompleted }, 15)
        return runBlocking { result.await() }
    }
    private fun pending(result: Deferred<*>) { await(scope.async { delay(150) }); assertFalse("Request should still wait", result.isCompleted) }
    private fun path(json: String) = JsonParser.parseString(json).asJsonObject["routes"].asJsonObject["applications"].asJsonArray.single()
        .asJsonObject["mounts"].asJsonArray.single().asJsonObject["path"].asString

    fun testFreshnessGuardAcquiresReadAccessForCallersWithoutALock() {
        pageFile(); application()
        val request = WicketRoutesRequest()
        val snapshot = request.capture(project)
        await(scope.async {
            assertFalse(com.intellij.openapi.application.ApplicationManager.getApplication().isReadAccessAllowed)
            request.requireCurrent(project, snapshot)
        })
    }

    fun testOffLockFreshnessGuardRejectsPendingEditsWithoutCommittingThem() {
        pageFile(); val app = application()
        val request = WicketRoutesRequest()
        val snapshot = request.capture(project)
        val documents = PsiDocumentManager.getInstance(project)
        (documents as PsiDocumentManagerBase).disableBackgroundCommit(testRootDisposable)
        WriteCommandAction.runWriteCommandAction(project) {
            documents.getDocument(app)!!.setText(app.text.replace("/old", "/new"))
        }
        await(scope.async {
            assertFalse(com.intellij.openapi.application.ApplicationManager.getApplication().isReadAccessAllowed)
            try { request.requireCurrent(project, snapshot); fail("Expected stale snapshot") }
            catch (failure: PageAnalysisFailure) { assertEquals("STALE_SNAPSHOT", failure.code) }
        })
        assertTrue(documents.uncommittedDocuments.isNotEmpty())
    }

    fun testIndexingWaitCancellationLeavesOtherRequestsAndRecoveryAlive() {
        pageFile(); application()
        val token = DumbModeTestUtils.startEternalDumbModeTask(project)
        val survivor: Deferred<String>
        try {
            val cancelled = request(); survivor = request(); pending(cancelled); pending(survivor)
            cancelled.cancel()
            try { await(cancelled); fail("Expected cancellation") } catch (_: CancellationException) { }
            assertTrue(survivor.isActive)
        } finally { DumbModeTestUtils.endEternalDumbModeTaskAndWaitForSmartMode(project, token) }
        assertEquals("/old", path(await(survivor)))
        assertEquals("/old", path(await(request())))
    }

    fun testIndexingTimeoutDoesNotPreventLaterRouteDiscovery() {
        pageFile(); application()
        val token = DumbModeTestUtils.startEternalDumbModeTask(project)
        try {
            try { await(request(WicketRoutesRequest(300))); fail("Expected deadline") }
            catch (failure: PageAnalysisFailure) { assertEquals("TIMED_OUT", failure.code) }
        } finally { DumbModeTestUtils.endEternalDumbModeTaskAndWaitForSmartMode(project, token) }
        assertEquals("/old", path(await(request())))
    }

    fun testRequestWaitsForEditedJavaAndHtmlWithoutCommittingOrSavingThem() {
        pageFile(); val html = companion(); val app = application()
        val documents = PsiDocumentManager.getInstance(project)
        documents.commitAllDocuments()
        val save = com.intellij.ide.SaveAndSyncHandler.getInstance().disableAutoSave()
        com.intellij.openapi.util.Disposer.register(testRootDisposable, com.intellij.openapi.Disposable { save.finish() })
        (documents as PsiDocumentManagerBase).disableBackgroundCommit(testRootDisposable)
        val files = listOf(app, html)
        val disk = files.map { it.virtualFile.contentsToByteArray().toList() }
        WriteCommandAction.runWriteCommandAction(project) {
            documents.getDocument(app)!!.setText(app.text.replace("/old", "/new"))
            documents.getDocument(html)!!.insertString(0, "<!-- edited -->")
        }
        val result = request(); pending(result)
        assertEquals(2, documents.uncommittedDocuments.size)
        assertEquals(disk, files.map { it.virtualFile.contentsToByteArray().toList() })
        documents.commitAllDocuments()
        assertEquals("/new", path(await(result)))
    }

    fun testMcpDiscoveryDoesNotChangeTheComponentsSelectionOrFollowPreference() {
        val page = pageFile(); companion(); val app = application()
        val service = project.service<WicketLensService>()
        service.analyze(page.virtualFile.url, 0)
        PlatformTestUtil.waitWithEventsDispatching("Existing component report", { !service.state.running }, 15)
        val before = service.state; val follow = service.followEditor; val text = app.text
        assertEquals("/old", path(await(request())))
        assertSame(before, service.state); assertEquals(follow, service.followEditor); assertEquals(text, app.text)
    }

    fun testRequestWaitsForTheLatestInheritedHelperDocument() {
        pageFile()
        val parent = myFixture.addFileToProject("src/main/java/example/BaseApp.java", """
            package example; public abstract class BaseApp extends org.apache.wicket.protocol.http.WebApplication {
                protected void init() { super.init(); routes(); }
                private void routes() { mount(new org.apache.wicket.core.request.mapper.MountedMapper("/old", ExamplePage.class)); }
            }
        """.trimIndent())
        myFixture.addFileToProject("src/main/java/example/App.java", """
            package example; public class App extends BaseApp {
                public Class<? extends org.apache.wicket.Page> getHomePage() { return ExamplePage.class; }
            }
        """.trimIndent())
        val documents = PsiDocumentManager.getInstance(project)
        documents.commitAllDocuments()
        val save = com.intellij.ide.SaveAndSyncHandler.getInstance().disableAutoSave()
        com.intellij.openapi.util.Disposer.register(testRootDisposable, com.intellij.openapi.Disposable { save.finish() })
        (documents as PsiDocumentManagerBase).disableBackgroundCommit(testRootDisposable)
        WriteCommandAction.runWriteCommandAction(project) { documents.getDocument(parent)!!.setText(parent.text.replace("/old", "/new")) }
        val result = request(); pending(result)
        assertEquals(1, documents.uncommittedDocuments.size)
        documents.commitAllDocuments(); assertEquals("/new", path(await(result)))
    }
}
