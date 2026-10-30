package dev.wicketlens

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.util.Disposer
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.ui.SimpleColoredComponent
import com.intellij.ui.speedSearch.SpeedSearchSupply
import dev.wicketlens.model.SourceLocation
import dev.wicketlens.ui.ReportHierarchyBuilder
import dev.wicketlens.ui.ReportTreeEntry
import dev.wicketlens.ui.ReportEntryKind
import dev.wicketlens.ui.WicketLensService
import dev.wicketlens.ui.WicketReportPanel
import java.awt.event.ActionEvent
import javax.swing.JComponent
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.TreePath

class WicketHierarchyViewTest : WicketProjectPsiTestCase() {
    private val service get() = project.service<WicketLensService>()
    override fun setUp() { super.setUp(); service.setEditorFollowing(false); service.cancel() }
    override fun tearDown() {
        try {
            service.cancel()
            FileEditorManager.getInstance(project).openFiles.forEach { FileEditorManager.getInstance(project).closeFile(it) }
        } finally { super.tearDown() }
    }

    fun testHierarchyShowsParentsStatusDetailsAndDiagnostics() {
        val java = pageFile(body = """
            org.apache.wicket.markup.html.form.Form<?> form = new org.apache.wicket.markup.html.form.Form<>("form");
            form.add(new Label("title")); add(form);
        """.trimIndent())
        companion(html = "<form wicket:id=\"form\"><span wicket:id=\"title\"></span></form>")
        analyze(java)
        val panel = panel()
        assertEquals("Hierarchy", panel.reportTabs.getTitleAt(panel.reportTabs.selectedIndex))
        val child = select(panel) { it.label.startsWith("title :") }
        assertTrue((child.parent as DefaultMutableTreeNode).userObject.toString().startsWith("form :"))
        assertTrue(child.userObject.toString().contains("[MATCHED]"))
        assertTrue(panel.detailText.text.contains("Type: org.apache.wicket.markup.html.basic.Label"))
        assertTrue(panel.detailText.text.contains("Confidence: CONFIRMED"))
        assertFalse(panel.detailText.isEditable)
        assertTrue(entries().any { it.parentId == "diagnostics" && it.label.contains("PARTIAL_ANALYSIS") })
    }

    fun testExpandAndCollapseControlBranchVisibility() {
        val java = pageFile(body = """
            org.apache.wicket.markup.html.form.Form<?> form = new org.apache.wicket.markup.html.form.Form<>("form");
            form.add(new Label("title")); add(form);
        """.trimIndent())
        companion(html = "<form wicket:id=\"form\"><span wicket:id=\"title\"></span></form>")
        analyze(java)
        val panel = panel()
        val child = node(panel) { it.label.startsWith("title :") }
        performReportAction(panel.expandAction)
        assertTrue(panel.hierarchyTree.isVisible(TreePath(child.path)))
        performReportAction(panel.collapseAction)
        assertFalse(panel.hierarchyTree.isVisible(TreePath(child.path)))
        assertTrue(panel.hierarchyTree.isExpanded(0))
    }

    fun testTreeNavigationOpensBothObservedSources() {
        val java = pageFile()
        val html = companion()
        analyze(java)
        val panel = panel()
        select(panel) { it.label.startsWith("title :") }
        assertTrue(panel.javaAction.isEnabled)
        assertTrue(panel.markupAction.isEnabled)
        performReportAction(panel.javaAction)
        assertEditor(SourceLocation(java.virtualFile.path, java.text.indexOf("new Label")))
        performReportAction(panel.markupAction)
        assertEditor(SourceLocation(html.virtualFile.path, html.text.indexOf("<span")))
    }

    fun testMarkupOnlyTreeEnterPrefersHtml() {
        val java = pageFile(body = "")
        val html = companion()
        analyze(java)
        val panel = panel()
        select(panel) { it.label.startsWith("title : <span>") }
        assertFalse(panel.javaAction.isEnabled)
        assertTrue(panel.markupAction.isEnabled)
        panel.hierarchyTree.actionMap.get("wicketlens.navigate").actionPerformed(ActionEvent(panel.hierarchyTree, 0, ""))
        assertEditor(SourceLocation(html.virtualFile.path, html.text.indexOf("<span")))
    }

    fun testRootAndGroupRowsOfferOnlyAvailableNavigation() {
        val java = pageFile()
        val html = companion()
        analyze(java)
        val panel = panel()
        select(panel) { it.id == "report" }
        assertTrue(panel.javaAction.isEnabled)
        assertTrue(panel.markupAction.isEnabled)
        performReportAction(panel.markupAction)
        assertEditor(SourceLocation(html.virtualFile.path, 0))
        select(panel) { it.id == "diagnostics" }
        assertFalse(panel.javaAction.isEnabled)
        assertFalse(panel.markupAction.isEnabled)
    }

    fun testRepeatedIdsKeepDistinctTreeDestinations() {
        val java = pageFile(body = """
            org.apache.wicket.markup.html.form.Form<?> left = new org.apache.wicket.markup.html.form.Form<>("left");
            org.apache.wicket.markup.html.form.Form<?> right = new org.apache.wicket.markup.html.form.Form<>("right");
            left.add(new Label("title")); right.add(new Label("title")); add(left, right);
        """.trimIndent())
        companion(html = "<form wicket:id=\"right\"><span wicket:id=\"title\"></span></form>" +
            "<form wicket:id=\"left\"><b wicket:id=\"title\"></b></form>")
        analyze(java)
        val children = entries().filter { it.label.startsWith("title : Label") }
        assertEquals(2, children.size)
        assertEquals(2, children.map { it.parentId }.distinct().size)
        assertEquals(2, children.map { it.markupLocation }.distinct().size)
        val panel = panel()
        children.forEach { entry ->
            select(panel) { it.id == entry.id }
            performReportAction(panel.markupAction)
            assertEditor(requireNotNull(entry.markupLocation))
        }
    }

    fun testStaleTreeRemainsReadableAndReanalysisRebuildsIt() {
        val java = pageFile()
        companion()
        analyze(java)
        val panel = panel()
        val oldModel = panel.hierarchyTree.model
        select(panel) { it.label.startsWith("title :") }
        edit(java) { it.replace("\"title\"", "\"renamed\"") }
        assertTrue(service.state.stale)
        assertSame(oldModel, panel.hierarchyTree.model)
        assertFalse(panel.javaAction.isEnabled)
        assertFalse(panel.markupAction.isEnabled)
        assertFalse(panel.copyAction.isEnabled)
        assertTrue(panel.expandAction.isEnabled)
        performReportAction(panel.reanalyzeAction)
        awaitCompletion()
        assertNotSame(oldModel, panel.hierarchyTree.model)
        select(panel) { it.label.startsWith("renamed :") }
        assertTrue(panel.javaAction.isEnabled)
        assertFalse(panel.markupAction.isEnabled)
    }

    fun testCopyDoesNotResetExpansionOrSelection() {
        val java = pageFile()
        companion()
        analyze(java)
        val panel = panel()
        val selected = select(panel) { it.label.startsWith("title :") }
        performReportAction(panel.expandAction)
        val rows = panel.hierarchyTree.rowCount
        performReportAction(panel.copyAction)
        assertSame(selected, panel.hierarchyTree.lastSelectedPathComponent)
        assertEquals(rows, panel.hierarchyTree.rowCount)
        assertTrue(panel.detailText.text.contains("Status: MATCHED"))
    }

    fun testUnattachedContainersRetainChildBranches() {
        val java = pageFile(body = """
            org.apache.wicket.markup.html.form.Form<?> form = new org.apache.wicket.markup.html.form.Form<>("form");
            form.add(new Label("title"));
        """.trimIndent())
        companion()
        analyze(java)
        val form = entries().single { it.label.startsWith("form :") }
        val child = entries().single { it.label.startsWith("title : Label") }
        assertEquals("unattached", form.parentId)
        assertEquals(form.id, child.parentId)
        assertNull(child.markupLocation)
    }

    fun testLabelsRemainLiteralTextAndEscapeLineBreaks() {
        val java = pageFile(body = "add(new Label(\"<html>\\ntitle\"));")
        companion(html = "<div>No IDs</div>")
        analyze(java)
        val panel = panel()
        val selected = select(panel) { it.label.startsWith("<html>") }
        val entry = selected.userObject as ReportTreeEntry
        assertTrue(entry.label.contains("\\n"))
        assertFalse(entry.label.contains('\n'))
        val renderer = panel.hierarchyTree.cellRenderer.getTreeCellRendererComponent(panel.hierarchyTree, selected, false, false, true, 1, false) as JComponent
        assertNull(renderer.getClientProperty("html"))
        assertTrue(panel.detailText.text.contains("<html>\ntitle"))
    }

    fun testBackgroundHierarchyPreparationHonorsCancellation() {
        val java = pageFile()
        companion()
        analyze(java)
        val report = service.state.prepared!!.report
        val indicator = EmptyProgressIndicator()
        assertThrows(ProcessCanceledException::class.java) {
            ProgressManager.getInstance().runProcess(Runnable { indicator.cancel(); ReportHierarchyBuilder().build(report) }, indicator)
        }
    }

    fun testInheritedMarkupNavigationAndStaleDependencies() {
        val (java, base) = inheritedPage()
        analyze(java)
        val panel = panel()
        val header = select(panel) { it.label.startsWith("header : <span>") }.userObject as ReportTreeEntry
        assertEquals(base.virtualFile.path, header.markupLocation!!.file)
        performReportAction(panel.markupAction)
        assertEditor(header.markupLocation)
        edit(base) { "\n$it" }
        assertTrue(service.state.stale)
        assertFalse(panel.markupAction.isEnabled)
        assertFalse(service.navigate(header.markupLocation))
        assertFalse(service.copyContext())
    }

    fun testEmptyExtensionIsIncludedInSnapshotDependencies() {
        val (java, _) = inheritedPage(emptyExtension = true)
        analyze(java)
        val prepared = service.state.prepared!!
        val paths = prepared.version.sources.map { it.path }
        val html = myFixture.findFileInTempDir("src/main/resources/example/DetailPage.html")
        assertTrue(paths.contains(html.path))
        assertEquals(4, paths.size) // The source superclass is now an analysis dependency too.
        val file = requireNotNull(com.intellij.psi.PsiManager.getInstance(project).findFile(html))
        edit(file) { "\n$it" }
        assertFalse(service.refreshFreshness())
    }

    fun testSidebarUsesNativeToolbarWithLiveActionPresentations() {
        val panel = panel()
        assertSame(panel, panel.actionToolbar.targetComponent)
        for (action in listOf(panel.javaAction, panel.markupAction, panel.copyAction, panel.cancelAction, panel.expandAction, panel.collapseAction)) {
            val event = reportActionEvent(action)
            action.update(event)
            assertFalse(event.presentation.isEnabled)
            assertNotNull(action.templatePresentation.icon)
            assertEquals(ActionUpdateThread.EDT, action.actionUpdateThread)
        }
        val java = pageFile()
        companion()
        analyze(java)
        select(panel) { it.label.startsWith("title :") }
        for (action in listOf(panel.javaAction, panel.markupAction, panel.copyAction, panel.expandAction, panel.collapseAction)) {
            val event = reportActionEvent(action)
            action.update(event)
            assertTrue(event.presentation.isEnabled)
        }
    }

    fun testNativeRendererShowsSemanticIconsAndReadableStatus() {
        val java = pageFile(body = """
            org.apache.wicket.markup.html.form.Form<?> form = new org.apache.wicket.markup.html.form.Form<>("form");
            form.add(new Label("title")); add(form);
        """.trimIndent())
        companion(html = "<form wicket:id=\"form\"><span wicket:id=\"title\"></span></form><span wicket:id=\"extra\"></span>")
        analyze(java)
        val panel = panel()
        val icons = mapOf(ReportEntryKind.PAGE to AllIcons.Nodes.Class, ReportEntryKind.CONTAINER to AllIcons.Nodes.Class,
            ReportEntryKind.COMPONENT to AllIcons.Nodes.Field, ReportEntryKind.MARKUP to AllIcons.Nodes.Tag,
            ReportEntryKind.GROUP to AllIcons.Nodes.Folder, ReportEntryKind.DIAGNOSTIC to AllIcons.General.Information)
        for ((kind, icon) in icons) {
            val row = node(panel) { it.kind == kind }
            val rendered = panel.hierarchyTree.cellRenderer.getTreeCellRendererComponent(panel.hierarchyTree, row, false, false, true, 0, false) as SimpleColoredComponent
            assertEquals(icon, rendered.icon)
        }
        val title = node(panel) { it.name == "title" }
        val rendered = panel.hierarchyTree.cellRenderer.getTreeCellRendererComponent(panel.hierarchyTree, title, false, false, true, 0, false) as SimpleColoredComponent
        assertEquals("title : Label  matched", rendered.getCharSequence(false).toString())
    }

    fun testDetailsAreOptionalAndRetainSelectedSourceInformation() {
        val java = pageFile()
        companion()
        analyze(java)
        val panel = panel()
        val selected = select(panel) { it.name == "title" }
        assertNull(panel.detailsSplitter.secondComponent)
        performReportAction(panel.detailsAction)
        assertNotNull(panel.detailsSplitter.secondComponent)
        assertTrue(panel.detailText.text.contains("Confidence: CONFIRMED"))
        assertTrue(panel.detailText.text.contains(java.virtualFile.path))
        performReportAction(panel.detailsAction)
        assertNull(panel.detailsSplitter.secondComponent)
        assertSame(selected, panel.hierarchyTree.lastSelectedPathComponent)
    }

    fun testTypeAheadSearchFindsAndExpandsCollapsedComponents() {
        val java = pageFile(body = """
            org.apache.wicket.markup.html.form.Form<?> form = new org.apache.wicket.markup.html.form.Form<>("form");
            form.add(new Label("title")); add(form);
        """.trimIndent())
        companion(html = "<form wicket:id=\"form\"><span wicket:id=\"title\"></span></form>")
        analyze(java)
        val panel = panel()
        performReportAction(panel.collapseAction)
        val search = requireNotNull(SpeedSearchSupply.getSupply(panel.hierarchyTree, true))
        search.findAndSelectElement("title")
        val selected = panel.hierarchyTree.lastSelectedPathComponent as DefaultMutableTreeNode
        assertEquals("title", (selected.userObject as ReportTreeEntry).name)
        assertTrue(panel.hierarchyTree.isVisible(TreePath(selected.path)))
    }

    fun testTextReportKeepsSourceActionsAndDisablesTreeOnlyActions() {
        val java = pageFile()
        companion()
        analyze(java)
        val panel = panel()
        panel.reportTabs.selectedIndex = 1
        panel.reportText.caretPosition = panel.reportText.text.indexOf("title : Label")
        assertTrue(panel.javaAction.isEnabled)
        assertTrue(panel.markupAction.isEnabled)
        assertFalse(panel.expandAction.isEnabled)
        assertFalse(panel.collapseAction.isEnabled)
        val detailsEvent = reportActionEvent(panel.detailsAction)
        panel.detailsAction.update(detailsEvent)
        assertFalse(detailsEvent.presentation.isEnabled)
        performReportAction(panel.javaAction)
        assertEditor(SourceLocation(java.virtualFile.path, java.text.indexOf("new Label")))
    }

    fun testStaleNativeActionsCannotNavigateOrCopyButDetailsRemainAvailable() {
        val java = pageFile()
        companion()
        analyze(java)
        val panel = panel()
        select(panel) { it.name == "title" }
        edit(java) { "\n$it" }
        assertTrue(service.state.stale)
        for (action in listOf(panel.javaAction, panel.markupAction, panel.copyAction)) {
            val event = reportActionEvent(action)
            action.update(event)
            assertFalse(event.presentation.isEnabled)
            action.actionPerformed(event)
            assertTrue(service.state.stale)
        }
        performReportAction(panel.detailsAction)
        assertNotNull(panel.detailsSplitter.secondComponent)
        assertTrue(panel.detailText.text.contains("ID: title"))
    }

    private fun inheritedPage(emptyExtension: Boolean = false): Pair<PsiFile, PsiFile> {
        myFixture.addFileToProject("src/main/java/example/BasePage.java", """
            package example; public class BasePage extends org.apache.wicket.markup.html.WebPage {}
        """.trimIndent())
        val base = companion("BasePage", "<span wicket:id=\"header\"></span><wicket:child/>")
        val java = myFixture.addFileToProject("src/main/java/example/DetailPage.java", """
            package example; public class DetailPage extends BasePage {
                public DetailPage() { add(new org.apache.wicket.markup.html.basic.Label("title")); }
            }
        """.trimIndent())
        companion("DetailPage", if (emptyExtension) "<wicket:extend/>" else "<wicket:extend><span wicket:id=\"title\"></span></wicket:extend>")
        return java to base
    }

    private fun entries() = service.state.prepared!!.hierarchy
    private fun panel() = WicketReportPanel(service).also { Disposer.register(testRootDisposable, it) }
    private fun node(panel: WicketReportPanel, predicate: (ReportTreeEntry) -> Boolean): DefaultMutableTreeNode {
        val all = (panel.hierarchyTree.model.root as DefaultMutableTreeNode).depthFirstEnumeration()
        while (all.hasMoreElements()) {
            val node = all.nextElement() as DefaultMutableTreeNode
            val entry = node.userObject as? ReportTreeEntry
            if (entry != null && predicate(entry)) return node
        }
        error("Missing tree entry")
    }
    private fun select(panel: WicketReportPanel, predicate: (ReportTreeEntry) -> Boolean) = node(panel, predicate).also {
        panel.hierarchyTree.selectionPath = TreePath(it.path)
    }
    private fun analyze(java: PsiFile) { service.analyze(java.virtualFile.url, -1); awaitCompletion() }
    private fun awaitCompletion() {
        PlatformTestUtil.waitWithEventsDispatching({ "Analysis did not finish: ${service.state.message}" }, { !service.state.running }, 30)
        assertNotNull(service.state.message, service.state.prepared)
    }
    private fun edit(file: PsiFile, change: (String) -> String) {
        val document = requireNotNull(PsiDocumentManager.getInstance(project).getDocument(file))
        WriteCommandAction.runWriteCommandAction(project) { document.setText(change(document.text)) }
    }
    private fun assertEditor(location: SourceLocation) {
        PlatformTestUtil.waitWithEventsDispatching({ "Navigation did not reach $location" }, {
            val editor = FileEditorManager.getInstance(project).selectedTextEditor
            editor != null && FileDocumentManager.getInstance().getFile(editor.document)?.path == location.file && editor.caretModel.offset == location.offset
        }, 10)
    }
}
