package dev.wicketlens

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.util.Disposer
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.psi.impl.PsiDocumentManagerEx
import com.intellij.testFramework.PlatformTestUtil
import dev.wicketlens.ui.ReportTreeEntry
import dev.wicketlens.ui.WicketLensService
import dev.wicketlens.ui.WicketReportPanel
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.TreePath

class WicketEditorFollowingTest : WicketProjectPsiTestCase() {
    private val service get() = project.service<WicketLensService>()
    private val editors get() = FileEditorManager.getInstance(project)

    override fun setUp() { super.setUp(); service.setEditorFollowing(true); service.cancel() }
    override fun tearDown() {
        try {
            service.setEditorFollowing(false)
            service.cancel()
            editors.openFiles.forEach(editors::closeFile)
        } finally { super.tearDown() }
    }

    fun testOpeningSidebarFollowsTheCurrentJavaFileWithoutManualAnalysis() {
        val java = pageFile("Alpha")
        companion("Alpha")
        open(java)
        assertFalse(service.state.running)
        assertNull(service.state.prepared)
        val panel = panel()
        awaitPage("Alpha")
        assertTrue(panel.followEditorAction.isSelected(reportActionEvent(panel.followEditorAction)))
        assertTrue(panel.copyAction.isEnabled)
    }

    fun testSwitchingJavaEditorsReplacesTheReportedPage() {
        val alpha = pageFile("Alpha")
        companion("Alpha")
        val beta = pageFile("Beta")
        companion("Beta")
        open(alpha)
        panel()
        awaitPage("Alpha")
        open(beta)
        assertNull(service.state.prepared)
        awaitPage("Beta")
        open(alpha)
        awaitPage("Alpha")
    }

    fun testOpeningSidebarDoesNotReplaceAnExplicitClassSelectionInFlight() {
        val java = myFixture.addFileToProject("src/main/java/example/Multiple.java", """
            package example;
            class First extends org.apache.wicket.markup.html.WebPage {}
            class Second extends org.apache.wicket.markup.html.WebPage {}
        """.trimIndent())
        open(java)
        service.analyze(java.virtualFile.url, java.text.indexOf("class Second") + 8)
        panel()
        awaitPage("Second")
    }

    fun testOpeningSidebarOnHtmlFindsItsJavaPage() {
        val java = pageFile("Alpha")
        val html = companion("Alpha")
        open(html)
        panel()
        awaitPage("Alpha")
        assertTrue(service.state.prepared!!.version.sources.any { it.path == java.virtualFile.path })
        assertEquals(html.virtualFile.path, service.state.prepared!!.report.markupPath)
    }

    fun testSwitchingToAnotherPagesHtmlFollowsThatPage() {
        val alpha = pageFile("Alpha")
        val alphaHtml = companion("Alpha")
        val beta = pageFile("Beta")
        val betaHtml = companion("Beta")
        open(alpha)
        panel()
        awaitPage("Alpha")
        open(betaHtml)
        awaitPage("Beta")
        val snapshot = service.state.prepared
        open(beta)
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
        assertSame(snapshot, service.state.prepared)
        open(alphaHtml)
        awaitPage("Alpha")
    }

    fun testOwnCompanionNavigationPreservesTheSnapshotAndTree() {
        val java = pageFile("Alpha")
        val html = companion("Alpha")
        open(java)
        val panel = panel()
        awaitPage("Alpha")
        select(panel, "title : Label")
        val snapshot = service.state.prepared
        val tree = panel.hierarchyTree.model
        performReportAction(panel.markupAction)
        awaitEditor(html)
        assertSame(snapshot, service.state.prepared)
        assertSame(tree, panel.hierarchyTree.model)
        performReportAction(panel.javaAction)
        awaitEditor(java)
        assertSame(snapshot, service.state.prepared)
    }

    fun testClassRowHtmlNavigationPreservesTheFollowedPage() {
        val java = pageFile("Alpha")
        val html = companion("Alpha")
        open(java)
        val panel = panel()
        awaitPage("Alpha")
        panel.hierarchyTree.setSelectionRow(0)
        val snapshot = service.state.prepared
        performReportAction(panel.markupAction)
        awaitEditor(html)
        assertSame(snapshot, service.state.prepared)
        assertEquals(0, editors.selectedTextEditor!!.caretModel.offset)
    }

    fun testFollowToggleCanPinAndResumeOnTheCurrentFile() {
        val alpha = pageFile("Alpha")
        companion("Alpha")
        val beta = pageFile("Beta")
        companion("Beta")
        open(alpha)
        val panel = panel()
        awaitPage("Alpha")
        val snapshot = service.state.prepared
        performReportAction(panel.followEditorAction)
        assertFalse(service.followEditor)
        open(beta)
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
        assertSame(snapshot, service.state.prepared)
        performReportAction(panel.followEditorAction)
        awaitPage("Beta")
        assertTrue(service.followEditor)
    }

    fun testNonWicketAndNonSourceFilesClearThePreviousSnapshot() {
        val alpha = pageFile("Alpha")
        companion("Alpha")
        val plain = myFixture.addFileToProject("src/main/java/example/Plain.java", "package example; public class Plain {}")
        val text = myFixture.addFileToProject("notes.txt", "Not a Wicket source")
        open(alpha)
        val panel = panel()
        awaitPage("Alpha")
        open(plain)
        awaitEmpty()
        assertFalse(panel.copyAction.isEnabled)
        open(alpha)
        awaitPage("Alpha")
        open(text)
        awaitEmpty()
        assertFalse(panel.reanalyzeAction.isEnabled)
    }

    fun testClosingAllEditorsClearsTheFollowedView() {
        val alpha = pageFile("Alpha")
        companion("Alpha")
        open(alpha)
        val panel = panel()
        awaitPage("Alpha")
        editors.openFiles.forEach(editors::closeFile)
        awaitEmpty()
        assertFalse(panel.copyAction.isEnabled)
        assertFalse(panel.hierarchyTree.isRootVisible)
    }

    fun testLatestEditorWinsWhileDocumentsAreWaitingForCommit() {
        val alpha = pageFile("Alpha")
        companion("Alpha")
        val beta = pageFile("Beta")
        companion("Beta")
        val manager = PsiDocumentManager.getInstance(project)
        (manager as PsiDocumentManagerEx).disableBackgroundCommit(testRootDisposable)
        edit(alpha) { "\n$it" }
        val published = mutableListOf<String>()
        service.listen(testRootDisposable) { it.prepared?.report?.qualifiedClassName?.let(published::add) }
        try {
            open(alpha)
            panel()
            assertTrue(service.state.running)
            open(beta)
        } finally { manager.commitAllDocuments() }
        awaitPage("Beta")
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
        assertEquals(listOf("example.Beta"), published)
    }

    fun testEditsStayStaleUntilExplicitReanalysis() {
        val alpha = pageFile("Alpha")
        companion("Alpha")
        open(alpha)
        val panel = panel()
        awaitPage("Alpha")
        val snapshot = service.state.prepared
        edit(alpha) { it.replace("\"title\"", "\"updated\"") }
        assertTrue(service.state.stale)
        assertFalse(service.state.running)
        assertSame(snapshot, service.state.prepared)
        assertFalse(panel.copyAction.isEnabled)
        PsiDocumentManager.getInstance(project).commitAllDocuments()
        performReportAction(panel.reanalyzeAction)
        awaitPage("Alpha")
        assertEquals("updated", service.state.prepared!!.report.children().single().wicketId)
    }

    fun testPinningCancelsAnAutomaticRequestWaitingForCommit() {
        val alpha = pageFile("Alpha")
        companion("Alpha")
        val manager = PsiDocumentManager.getInstance(project)
        (manager as PsiDocumentManagerEx).disableBackgroundCommit(testRootDisposable)
        edit(alpha) { "\n$it" }
        try {
            open(alpha)
            val panel = panel()
            assertTrue(service.state.running)
            performReportAction(panel.followEditorAction)
            assertFalse(service.followEditor)
        } finally { manager.commitAllDocuments() }
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
        assertNull(service.state.prepared)
        assertFalse(service.state.running)
        service.analyze(alpha.virtualFile.url, -1)
        awaitPage("Alpha")
    }

    fun testDisposedViewStopsFollowingEditorSwitches() {
        val alpha = pageFile("Alpha")
        companion("Alpha")
        val beta = pageFile("Beta")
        companion("Beta")
        open(alpha)
        val panel = panel()
        awaitPage("Alpha")
        val snapshot = service.state.prepared
        Disposer.dispose(panel)
        open(beta)
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
        assertSame(snapshot, service.state.prepared)
        assertFalse(service.state.running)
    }

    fun testNavigationToInheritedMarkupKeepsContextButManualSwitchFollowsBase() {
        myFixture.addFileToProject("src/main/java/example/BasePage.java", """
            package example; public class BasePage extends org.apache.wicket.markup.html.WebPage {}
        """.trimIndent())
        val baseHtml = companion("BasePage", "<span wicket:id=\"header\"></span><wicket:child/>")
        val child = myFixture.addFileToProject("src/main/java/example/ChildPage.java", """
            package example; public class ChildPage extends BasePage {
                public ChildPage() { add(new org.apache.wicket.markup.html.basic.Label("title")); }
            }
        """.trimIndent())
        companion("ChildPage", "<wicket:extend><span wicket:id=\"title\"></span></wicket:extend>")
        open(child)
        val panel = panel()
        awaitPage("ChildPage")
        select(panel, "header : <span>")
        val snapshot = service.state.prepared
        performReportAction(panel.markupAction)
        awaitEditor(baseHtml)
        assertSame(snapshot, service.state.prepared)
        open(child)
        open(baseHtml)
        awaitPage("BasePage")
    }

    fun testDelayedSourceSelectionKeepsDerivedContextUntilAManualFileSwitch() {
        val base = myFixture.addFileToProject("src/main/java/example/BasePage.java", """
            package example; public class BasePage extends org.apache.wicket.markup.html.WebPage {
                public BasePage() { add(new org.apache.wicket.markup.html.basic.Label("base")); }
            }
        """.trimIndent())
        companion("BasePage", "<span wicket:id=\"base\"></span>")
        val child = myFixture.addFileToProject("src/main/java/example/ChildPage.java", """
            package example; public class ChildPage extends BasePage {
                public ChildPage() { add(new org.apache.wicket.markup.html.basic.Label("title")); }
            }
        """.trimIndent())
        companion("ChildPage", "<span wicket:id=\"base\"></span><span wicket:id=\"title\"></span>")
        open(child)
        val panel = panel()
        awaitPage("ChildPage")
        select(panel, "base : Label")
        val snapshot = service.state.prepared
        performReportAction(panel.javaAction)
        awaitEditor(base)
        val provider = com.intellij.openapi.fileEditor.impl.text.TextEditorProvider.getInstance()
        val event = com.intellij.openapi.fileEditor.FileEditorManagerEvent(editors,
            child.virtualFile, editors.getEditors(child.virtualFile).single(), provider,
            base.virtualFile, editors.selectedEditor, provider)
        project.messageBus.syncPublisher(com.intellij.openapi.fileEditor.FileEditorManagerListener.FILE_EDITOR_MANAGER).selectionChanged(event)
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
        assertFalse(service.state.running)
        assertSame(snapshot, service.state.prepared)
        open(child)
        open(base)
        awaitPage("BasePage")
    }

    private fun panel() = WicketReportPanel(service).also { Disposer.register(testRootDisposable, it) }
    private fun open(file: PsiFile) { editors.openFile(file.virtualFile, true) }
    private fun awaitPage(name: String) {
        PlatformTestUtil.waitWithEventsDispatching({ "Expected $name: ${service.state.message}" }, {
            !service.state.running && !service.state.stale && service.state.prepared?.report?.qualifiedClassName == "example.$name"
        }, 30)
    }
    private fun awaitEmpty() {
        PlatformTestUtil.waitWithEventsDispatching({ "Old snapshot was retained: ${service.state.message}" }, {
            !service.state.running && service.state.prepared == null
        }, 30)
    }
    private fun awaitEditor(file: PsiFile) {
        PlatformTestUtil.waitWithEventsDispatching({ "Editor did not open ${file.name}" }, {
            editors.selectedFiles.contains(file.virtualFile)
        }, 10)
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
    }
    private fun select(panel: WicketReportPanel, prefix: String) {
        val all = (panel.hierarchyTree.model.root as DefaultMutableTreeNode).depthFirstEnumeration()
        while (all.hasMoreElements()) {
            val node = all.nextElement() as DefaultMutableTreeNode
            if ((node.userObject as? ReportTreeEntry)?.label?.startsWith(prefix) == true) {
                panel.hierarchyTree.selectionPath = TreePath(node.path)
                return
            }
        }
        error("Missing row: $prefix")
    }
    private fun edit(file: PsiFile, change: (String) -> String) {
        val document = requireNotNull(PsiDocumentManager.getInstance(project).getDocument(file))
        WriteCommandAction.runWriteCommandAction(project) { document.setText(change(document.text)) }
    }
}
