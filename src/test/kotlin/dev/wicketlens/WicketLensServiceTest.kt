package dev.wicketlens

import com.google.gson.JsonParser
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.components.service
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.util.Disposer
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.impl.PsiDocumentManagerEx
import com.intellij.testFramework.PlatformTestUtil
import dev.wicketlens.actions.CopyWicketContextAction
import dev.wicketlens.ui.WicketLensService
import dev.wicketlens.ui.WicketReportPanel
import java.awt.datatransfer.DataFlavor

class WicketLensServiceTest : WicketProjectPsiTestCase() {
    private val service get() = project.service<WicketLensService>()

    override fun setUp() {
        super.setUp()
        service.setEditorFollowing(false)
        service.cancel()
    }

    override fun tearDown() {
        try { service.cancel() } finally { super.tearDown() }
    }

    fun testBackgroundAnalysisPublishesACompletePreparedSnapshot() {
        val file = pageFile()
        companion()
        service.analyze(file.virtualFile.url, -1)
        assertTrue(service.state.running)
        assertNull(service.state.prepared)
        awaitCompletion()
        val prepared = requireNotNull(service.state.prepared) { service.state.message }
        assertEquals("example.ExamplePage", prepared.report.qualifiedClassName)
        assertTrue(prepared.text.contains("[MATCHED]"))
        assertEquals("example.ExamplePage", JsonParser.parseString(prepared.json).asJsonObject["qualifiedClassName"].asString)
    }

    fun testAnalysisWaitsForUncommittedJavaEdits() = assertCommittedSnapshot(editJava = true, editMarkup = false)

    fun testAnalysisWaitsForUncommittedMarkupEdits() = assertCommittedSnapshot(editJava = false, editMarkup = true)

    fun testAnalysisWaitsForBothEditedDocuments() = assertCommittedSnapshot(editJava = true, editMarkup = true)

    fun testCancellationWhileWaitingForCommitCannotPublishAfterCommit() {
        val java = pageFile()
        companion()
        val manager = PsiDocumentManager.getInstance(project)
        (manager as PsiDocumentManagerEx).disableBackgroundCommit(testRootDisposable)
        val document = requireNotNull(manager.getDocument(java))
        WriteCommandAction.runWriteCommandAction(project) {
            document.setText(document.text.replace("\"title\"", "\"newTitle\""))
        }
        try {
            service.analyze(java.virtualFile.url, -1)
            assertWaitingForCommit()
            service.cancel()
        } finally {
            manager.commitAllDocuments()
        }
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
        assertFalse(service.state.running)
        assertNull(service.state.prepared)
        assertFalse(service.copyContext())
    }

    fun testCopyActionUsesTheCompletedSnapshotAndPanelReflectsIt() {
        val file = pageFile()
        companion()
        val panel = WicketReportPanel(service)
        Disposer.register(testRootDisposable, panel)
        assertFalse(panel.copyAction.isEnabled)
        service.analyze(file.virtualFile.url, -1)
        assertTrue(panel.cancelAction.isEnabled)
        awaitCompletion()
        assertTrue(panel.copyAction.isEnabled)
        assertFalse(panel.cancelAction.isEnabled)
        assertEquals(service.state.prepared?.text, panel.reportText.text)
        assertFalse(panel.reportText.isEditable)
        val action = ActionManager.getInstance().getAction("WicketLens.CopyWicketContext")
        assertTrue(action is CopyWicketContextAction)
        action.actionPerformed(AnActionEvent.createFromAnAction(action, null, ActionPlaces.UNKNOWN, SimpleDataContext.getProjectContext(project)))
        assertEquals(service.state.prepared?.json, CopyPasteManager.getInstance().getContents<String>(DataFlavor.stringFlavor))
    }

    fun testNewRequestSupersedesTheOldRequest() {
        val first = pageFile(name = "First")
        companion(name = "First")
        val second = pageFile(name = "Second")
        companion(name = "Second")
        service.analyze(first.virtualFile.url, -1)
        service.analyze(second.virtualFile.url, -1)
        awaitCompletion()
        assertEquals("example.Second", service.state.prepared?.report?.qualifiedClassName)
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
        assertEquals("example.Second", service.state.prepared?.report?.qualifiedClassName)
    }

    fun testCancellationClearsCopyableStateAndDisablesControls() {
        val file = pageFile()
        companion()
        val panel = WicketReportPanel(service)
        Disposer.register(testRootDisposable, panel)
        service.analyze(file.virtualFile.url, -1)
        performReportAction(panel.cancelAction)
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
        assertFalse(service.state.running)
        assertNull(service.state.prepared)
        assertFalse(panel.copyAction.isEnabled)
        assertFalse(panel.cancelAction.isEnabled)
        assertFalse(service.copyContext())
    }

    fun testNonWicketInputIsReportedWithoutAStaleSnapshot() {
        val file = myFixture.addFileToProject("src/main/java/example/Plain.java", "package example; class Plain {}")
        service.analyze(file.virtualFile.url, -1)
        awaitCompletion()
        assertNull(service.state.prepared)
        assertTrue(service.state.message.contains("WebPage, Panel, or FormComponentPanel"))
        assertFalse(service.copyContext())
    }

    fun testDisposedPanelListenerIsRemoved() {
        val disposable = Disposer.newDisposable()
        var calls = 0
        service.listen(disposable) { calls++ }
        assertEquals(1, calls)
        Disposer.dispose(disposable)
        service.cancel()
        assertEquals(1, calls)
    }

    private fun awaitCompletion() {
        PlatformTestUtil.waitWithEventsDispatching({ "Analysis did not complete: ${service.state.message}" }, { !service.state.running }, 20)
    }

    private fun assertCommittedSnapshot(editJava: Boolean, editMarkup: Boolean) {
        val java = pageFile()
        val markup = companion()
        val manager = PsiDocumentManager.getInstance(project)
        (manager as PsiDocumentManagerEx).disableBackgroundCommit(testRootDisposable)
        val javaDocument = requireNotNull(manager.getDocument(java))
        val markupDocument = requireNotNull(manager.getDocument(markup))
        WriteCommandAction.runWriteCommandAction(project) {
            if (editJava) javaDocument.setText(javaDocument.text.replace("\"title\"", "\"newJavaTitle\""))
            if (editMarkup) markupDocument.setText(markupDocument.text.replace("\"title\"", "\"newJavaTitle\""))
        }
        assertEquals(!editJava, manager.isCommitted(javaDocument))
        assertEquals(!editMarkup, manager.isCommitted(markupDocument))
        assertTrue(java.text.contains("\"title\""))
        assertTrue(markup.text.contains("\"title\""))
        try {
            service.analyze(java.virtualFile.url, -1)
            assertWaitingForCommit()
            assertEquals(!editJava, manager.isCommitted(javaDocument))
            assertEquals(!editMarkup, manager.isCommitted(markupDocument))
        } finally {
            manager.commitAllDocuments()
        }
        awaitCompletion()
        val prepared = requireNotNull(service.state.prepared) { service.state.message }
        val report = prepared.report
        assertEquals(if (editJava) "newJavaTitle" else "title", report.children().single().wicketId)
        assertEquals(if (editMarkup) "newJavaTitle" else "title",
            requireNotNull(report.markup).elements.single { it.hasWicketId }.wicketId)
        val json = JsonParser.parseString(prepared.json).asJsonObject
        val component = json.getAsJsonArray("components").single { it.asJsonObject["wicketId"].isJsonPrimitive }.asJsonObject
        assertEquals(report.children().single().wicketId, component["wicketId"].asString)
        assertEquals(javaDocument.text.indexOf("new Label"), component.getAsJsonObject("javaLocation")["offset"].asInt)
        assertEquals(if (editJava == editMarkup) "MATCHED" else "JAVA_ONLY", component["status"].asString)
    }

    private fun assertWaitingForCommit() {
        // Keep PSI deliberately stale while giving the worker a chance to read it.
        val deadline = System.nanoTime() + 1_000_000_000L
        PlatformTestUtil.waitWithEventsDispatching({ "Worker did not settle" },
            { !service.state.running || System.nanoTime() >= deadline }, 5)
        assertTrue("Must wait for document commits: ${service.state.message}", service.state.running)
        assertNull(service.state.prepared)
    }
}
