package dev.wicketlens

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbService
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.impl.PsiDocumentManagerBase
import com.intellij.testFramework.DumbModeTestUtils
import com.intellij.testFramework.PlatformTestUtil
import dev.wicketlens.mcp.*
import dev.wicketlens.ui.WicketLensService
import kotlinx.coroutines.*

class WicketMcpReliabilityTest : WicketProjectPsiTestCase() {
    private val query = WicketPageAnalysisRequest()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun tearDown() {
        try { scope.cancel() } finally { super.tearDown() }
    }

    private fun launch(view: WicketMcpReport, request: WicketPageAnalysisRequest = query, name: String = "ExamplePage") = scope.async {
        request.execute(project, "example.$name", view = view, componentPath = if (view == WicketMcpReport.CONTEXT) "title" else null)
    }
    private fun <T> await(result: Deferred<T>): T {
        PlatformTestUtil.waitWithEventsDispatching("MCP lifecycle request", { result.isCompleted }, 15)
        return runBlocking { result.await() }
    }
    private fun assertPending(results: List<Deferred<*>>) {
        val interval = scope.async { delay(150) }
        await(interval)
        assertTrue("Blocked requests must not return a snapshot", results.all { !it.isCompleted })
    }
    private fun response(result: Deferred<String>): JsonObject = JsonParser.parseString(await(result)).asJsonObject
    private fun expectFailure(code: String, result: Deferred<String>) {
        try { await(result); fail("Expected $code") }
        catch (failure: PageAnalysisFailure) { assertEquals(code, failure.code) }
    }
    private fun expectCancelled(result: Deferred<String>) {
        try { await(result); fail("Expected caller cancellation") }
        catch (_: CancellationException) { /* Cancellation is not serialized as a tool failure. */ }
    }
    private fun editBoth(): List<com.intellij.psi.PsiFile> {
        // Hold only automatic background commits; explicit commits below remain real PSI commits.
        (PsiDocumentManager.getInstance(project) as PsiDocumentManagerBase).disableBackgroundCommit(testRootDisposable)
        val java = pageFile(); val html = companion()
        WriteCommandAction.runWriteCommandAction(project) {
            val documents = PsiDocumentManager.getInstance(project)
            documents.getDocument(java)!!.setText(java.text.replace("title", "updated"))
            documents.getDocument(html)!!.setText(html.text.replace("title", "updated"))
        }
        return listOf(java, html)
    }

    fun testAllViewsWaitForIndexingThenReturnTheSameCoreEvidence() {
        pageFile(); companion()
        val expected = WicketMcpReport.entries.associateWith { query.render(query.capture(project, "example.ExamplePage"), it,
            componentPath = if (it == WicketMcpReport.CONTEXT) "title" else null) }
        val token = DumbModeTestUtils.startEternalDumbModeTask(project)
        val requests: Map<WicketMcpReport, Deferred<String>>
        try {
            assertTrue(DumbService.isDumb(project))
            requests = WicketMcpReport.entries.associateWith { launch(it) }
            assertPending(requests.values.toList())
        } finally { DumbModeTestUtils.endEternalDumbModeTaskAndWaitForSmartMode(project, token) }
        requests.forEach { (view, result) -> assertEquals(expected[view], await(result)) }
    }
    fun testAllViewsWaitForBothEditorDocumentsAndReturnTheirLatestText() {
        editBoth()
        val requests = WicketMcpReport.entries.associateWith { view -> scope.async {
            query.execute(project, "example.ExamplePage", view = view, componentPath = if (view == WicketMcpReport.CONTEXT) "updated" else null)
        } }
        assertPending(requests.values.toList())
        PsiDocumentManager.getInstance(project).commitAllDocuments()
        val structure = response(requests.getValue(WicketMcpReport.STRUCTURE))["analysis"].asJsonObject
        val validation = response(requests.getValue(WicketMcpReport.VALIDATION))["validation"].asJsonObject
        val context = response(requests.getValue(WicketMcpReport.CONTEXT))["context"].asJsonObject
        assertEquals("updated", context["component"].asJsonObject["wicketId"].asString)
        assertEquals(structure["matching"].asJsonObject["findings"], validation["findings"])
        assertEquals(validation["findings"], context["findings"])
        assertEquals("MATCHED", context["findings"].asJsonArray.single().asJsonObject["code"].asString)
    }
    fun testCancellingIndexedWaitsDoesNotPreventLaterRequests() {
        pageFile(); companion()
        val token = DumbModeTestUtils.startEternalDumbModeTask(project)
        try {
            val requests = WicketMcpReport.entries.map { launch(it) }
            assertPending(requests)
            requests.forEach { it.cancel() }
            requests.forEach(::expectCancelled)
        } finally { DumbModeTestUtils.endEternalDumbModeTaskAndWaitForSmartMode(project, token) }
        WicketMcpReport.entries.forEach { assertEquals("ok", response(launch(it))["status"].asString) }
    }
    fun testCancellingOneDocumentWaitLeavesTheOtherViewsAlive() {
        editBoth()
        val cancelled = launch(WicketMcpReport.STRUCTURE)
        val survivors = listOf(WicketMcpReport.VALIDATION, WicketMcpReport.CONTEXT).map { view -> scope.async {
            query.execute(project, "example.ExamplePage", view = view, componentPath = if (view == WicketMcpReport.CONTEXT) "updated" else null)
        } }
        assertPending(survivors + cancelled)
        cancelled.cancel(); expectCancelled(cancelled)
        assertTrue(survivors.all { it.isActive })
        PsiDocumentManager.getInstance(project).commitAllDocuments()
        survivors.forEach { assertEquals("ok", response(it)["status"].asString) }
    }
    fun testIndexingTimeoutIsBoundedAndDoesNotPoisonRecovery() {
        pageFile(); companion()
        query.capture(project, "example.ExamplePage")
        val token = DumbModeTestUtils.startEternalDumbModeTask(project)
        try {
            val short = WicketPageAnalysisRequest(1_000)
            val requests = WicketMcpReport.entries.map { launch(it, short) }
            requests.forEach { expectFailure("TIMED_OUT", it) }
        } finally { DumbModeTestUtils.endEternalDumbModeTaskAndWaitForSmartMode(project, token) }
        assertEquals("ok", response(launch(WicketMcpReport.STRUCTURE))["status"].asString)
    }
    fun testDocumentTimeoutDoesNotCommitOrSavePendingChanges() {
        val files = editBoth()
        val diskBefore = files.map { it.virtualFile.contentsToByteArray().toList() }
        val documents = PsiDocumentManager.getInstance(project)
        val before = documents.uncommittedDocuments.toSet()
        assertEquals(2, before.size)
        val requests = WicketMcpReport.entries.map { launch(it, WicketPageAnalysisRequest(1_000)) }
        requests.forEach { expectFailure("TIMED_OUT", it) }
        assertEquals(before, documents.uncommittedDocuments.toSet())
        assertTrue(before.all { "updated" in it.text })
        assertEquals(diskBefore, files.map { it.virtualFile.contentsToByteArray().toList() })
        documents.commitAllDocuments()
        val recovered = scope.async { query.execute(project, "example.ExamplePage", view = WicketMcpReport.CONTEXT, componentPath = "updated") }
        assertEquals("MATCHED", response(recovered)["context"].asJsonObject["findings"].asJsonArray.single().asJsonObject["code"].asString)
    }
    fun testConcurrentViewsAndTargetsRemainIndependentAndReadOnly() {
        val java = pageFile(); val html = companion()
        val otherJava = pageFile("OtherPage"); val otherHtml = companion("OtherPage")
        val files = listOf(java, html, otherJava, otherHtml)
        val before = files.map { it.text }
        val sidebar = project.service<WicketLensService>().state
        val requests = (0 until 12).map { n ->
            val view = WicketMcpReport.entries[n % 3]
            val name = if (n % 2 == 0) "ExamplePage" else "OtherPage"
            Triple(view, name, launch(view, name = name))
        }
        val previous = mutableMapOf<Pair<WicketMcpReport, String>, JsonObject>()
        for ((view, name, result) in requests) {
            val value = response(result)
            assertEquals(view.tool, value["tool"].asString)
            assertEquals("example.$name", value["target"].asJsonObject["className"].asString)
            previous.put(view to name, value)?.let { assertEquals(it, value) }
        }
        assertEquals(before, files.map { it.text })
        assertSame(sidebar, project.service<WicketLensService>().state)
    }
    fun testFailedRequestDoesNotCancelConcurrentSuccessfulViews() {
        pageFile(); companion()
        val failure = launch(WicketMcpReport.STRUCTURE, name = "MissingPage")
        val survivors = WicketMcpReport.entries.map { launch(it) }
        expectFailure("CLASS_NOT_FOUND", failure)
        survivors.forEach { assertEquals("ok", response(it)["status"].asString) }
    }
}
