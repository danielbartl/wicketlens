package dev.wicketlens

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.util.Disposer
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.testFramework.PlatformTestUtil
import dev.wicketlens.model.DiagnosticCode
import dev.wicketlens.model.SourceLocation
import dev.wicketlens.ui.*
import java.awt.event.ActionEvent
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.TreePath

class WicketHierarchyExplorationViewTest : WicketProjectPsiTestCase() {
    private val service get() = project.service<WicketLensService>()
    override fun setUp() { super.setUp(); service.setEditorFollowing(false); service.cancel() }
    override fun tearDown() {
        try {
            service.cancel()
            FileEditorManager.getInstance(project).openFiles.forEach { FileEditorManager.getInstance(project).closeFile(it) }
        } finally { super.tearDown() }
    }

    fun testFocusKeepsOneBranchAndItsParentContext() {
        fixture(); val panel = panel()
        select(panel, "left")
        performReportAction(panel.focusAction)
        assertEquals(setOf("ExamplePage", "left", "name", "postcode"), rows(panel).map { it.name }.toSet())
        assertTrue(panel.focusSummary.isVisible)
        assertEquals("Focused: left", panel.focusSummary.text)
        assertTrue(panel.showFullAction.isEnabled)
        performReportAction(panel.showFullAction)
        assertTrue(rows(panel).any { it.name == "right" })
        assertFalse(panel.focusSummary.isVisible)
    }

    fun testFocusAndShowFullRetainQueryAndStatus() {
        fixture(); val panel = panel()
        panel.hierarchyStatus.selectedItem = HierarchyStatusFilter.UNPAIRED
        select(panel, "left"); performReportAction(panel.focusAction)
        panel.hierarchySearch.text = "postcode"
        assertEquals(setOf("ExamplePage", "left", "postcode"), rows(panel).map { it.name }.toSet())
        panel.hierarchySearch.text = "absent"
        assertEquals(setOf("ExamplePage", "left"), rows(panel).map { it.name }.toSet())
        performReportAction(panel.showFullAction)
        assertEquals("absent", panel.hierarchySearch.text)
        assertEquals(HierarchyStatusFilter.UNPAIRED, panel.hierarchyStatus.selectedItem)
        assertEquals(listOf("ExamplePage"), rows(panel).map { it.name })
    }

    fun testNextAndPreviousUseTreeOrderAndWrapAround() {
        fixture(); val panel = panel()
        performReportAction(panel.nextFindingAction)
        assertEquals("name", selected(panel).name)
        performReportAction(panel.nextFindingAction)
        assertEquals("postcode", selected(panel).name)
        performReportAction(panel.nextFindingAction)
        assertEquals("name", selected(panel).name)
        performReportAction(panel.previousFindingAction)
        assertEquals("postcode", selected(panel).name)
    }

    fun testPreviousWithoutSelectionStartsAtTheLastFinding() {
        fixture(); val panel = panel()
        performReportAction(panel.previousFindingAction)
        assertEquals("postcode", selected(panel).name)
    }

    fun testFindingNavigationRevealsCollapsedBranchesWithoutOpeningAnEditor() {
        fixture(); val panel = panel()
        performReportAction(panel.collapseAction)
        val open = FileEditorManager.getInstance(project).openFiles.toList()
        panel.hierarchyTree.actionMap.get("wicketlens.nextFinding").actionPerformed(ActionEvent(panel.hierarchyTree, 0, ""))
        val selected = panel.hierarchyTree.lastSelectedPathComponent as DefaultMutableTreeNode
        assertEquals("name", selected.entry().name)
        assertTrue(panel.hierarchyTree.isVisible(TreePath(selected.path)))
        assertEquals(open, FileEditorManager.getInstance(project).openFiles.toList())
    }

    fun testNavigationSkipsMatchedAndContextOnlyRowsAndRespectsFocus() {
        fixture(); val panel = panel()
        panel.hierarchyStatus.selectedItem = HierarchyStatusFilter.MARKUP_ONLY
        performReportAction(panel.nextFindingAction)
        assertEquals("postcode", selected(panel).name)
        panel.hierarchyStatus.selectedItem = HierarchyStatusFilter.ALL
        select(panel, "right"); performReportAction(panel.focusAction)
        assertFalse(panel.nextFindingAction.isEnabled)
        assertFalse(panel.previousFindingAction.isEnabled)
        assertEquals("right", selected(panel).name)
    }

    fun testTextTabDisablesExplorationActions() {
        fixture(); val panel = panel()
        select(panel, "left"); performReportAction(panel.focusAction)
        panel.reportTabs.selectedIndex = 1
        assertFalse(panel.focusAction.isEnabled)
        assertFalse(panel.showFullAction.isEnabled)
        assertFalse(panel.nextFindingAction.isEnabled)
        assertFalse(panel.previousFindingAction.isEnabled)
    }

    fun testStaleSnapshotsStillAllowExplorationButCannotNavigateSources() {
        val (java, _) = fixture(); val panel = panel()
        edit(java, "\n${java.text}")
        select(panel, "left"); performReportAction(panel.focusAction)
        performReportAction(panel.nextFindingAction)
        assertTrue(service.state.stale)
        assertFalse(panel.javaAction.isEnabled)
        assertFalse(panel.markupAction.isEnabled)
        assertFalse(panel.copyAction.isEnabled)
        assertEquals("name", selected(panel).name)
    }

    fun testSelectionAndExpansionSurviveShiftedJavaOffsets() {
        val (java, _) = fixture(); val panel = panel()
        hierarchyExpand(panel, "left")
        select(panel, "name", "left")
        val old = selected(panel).javaLocation!!
        edit(java, "// shifted source\n${java.text}"); reanalyze()
        assertEquals("name", selected(panel).name)
        assertEquals("left", ((panel.hierarchyTree.lastSelectedPathComponent as DefaultMutableTreeNode).parent as DefaultMutableTreeNode).entry().name)
        assertTrue(panel.hierarchyTree.isExpanded(TreePath(node(panel, "left").path)))
        assertFalse(panel.hierarchyTree.isExpanded(TreePath(node(panel, "right").path)))
        assertTrue(selected(panel).javaLocation!!.offset > old.offset)
        performReportAction(panel.javaAction)
        assertEditor(selected(panel).javaLocation!!)
    }

    fun testMarkupSelectionUsesFreshLocationsAfterMarkupOffsetsChange() {
        val (_, html) = fixture(); val panel = panel()
        select(panel, "postcode")
        val old = selected(panel).markupLocation!!
        edit(html, "\n\n${html.text}"); reanalyze()
        assertEquals("postcode", selected(panel).name)
        assertTrue(selected(panel).markupLocation!!.offset > old.offset)
        performReportAction(panel.markupAction)
        assertEditor(selected(panel).markupLocation!!)
    }

    fun testFocusAndFiltersSurviveReanalysis() {
        val (java, _) = fixture(); val panel = panel()
        panel.hierarchyStatus.selectedItem = HierarchyStatusFilter.UNPAIRED
        select(panel, "left"); performReportAction(panel.focusAction)
        panel.hierarchySearch.text = "postcode"
        select(panel, "postcode")
        edit(java, "\n${java.text}"); reanalyze()
        assertEquals("Focused: left", panel.focusSummary.text)
        assertEquals("postcode", selected(panel).name)
        assertEquals("postcode", panel.hierarchySearch.text)
        assertEquals(HierarchyStatusFilter.UNPAIRED, panel.hierarchyStatus.selectedItem)
        assertTrue(rows(panel).none { it.name == "right" })
    }

    fun testCollapsedFilteredBranchesStayCollapsedAfterReanalysis() {
        val (java, _) = fixture(); val panel = panel()
        panel.hierarchyStatus.selectedItem = HierarchyStatusFilter.UNPAIRED
        panel.hierarchyTree.collapsePath(TreePath(node(panel, "left").path))
        hierarchySelectRoot(panel)
        edit(java, "\n${java.text}"); reanalyze()
        assertFalse(panel.hierarchyTree.isExpanded(TreePath(node(panel, "left").path)))
    }

    fun testRemovedSelectionIsNotRestoredToAnotherComponent() {
        val (java, _) = fixture(); val panel = panel()
        select(panel, "name", "left")
        edit(java, java.text.replace("left.add(new Label(\"name\"));", "")); reanalyze()
        assertNull(panel.hierarchyTree.selectionPath)
        assertFalse(panel.javaAction.isEnabled)
    }

    fun testAChangedStatusKeepsSelectionButHiddenRowsDoNot() {
        val (_, html) = fixture(); val panel = panel()
        select(panel, "name", "left")
        edit(html, html.text.replace("<input wicket:id=\"postcode\">", "<span wicket:id=\"name\"></span><input wicket:id=\"postcode\">")); reanalyze()
        assertEquals("name", selected(panel).name)
        assertEquals(DiagnosticCode.MATCHED, selected(panel).status)
        assertTrue(panel.markupAction.isEnabled)
        panel.hierarchyStatus.selectedItem = HierarchyStatusFilter.JAVA_ONLY
        assertNull(panel.hierarchyTree.selectionPath)
    }

    fun testLostFocusReturnsToTheFullHierarchy() {
        val (java, _) = fixture(); val panel = panel()
        select(panel, "left"); performReportAction(panel.focusAction)
        edit(java, java.text.replace("\"left\"", "\"renamed\"")); reanalyze()
        assertFalse(panel.focusSummary.isVisible)
        assertFalse(panel.showFullAction.isEnabled)
        assertTrue(rows(panel).any { it.name == "right" })
    }

    fun testPageSwitchesDoNotApplyAnotherPagesFocusOrSelection() {
        fixture(); val panel = panel()
        select(panel, "left"); performReportAction(panel.focusAction)
        select(panel, "postcode")
        val other = pageFile("OtherPage", "add(new Label(\"other\"));")
        companion("OtherPage", "<span wicket:id=\"other\"></span>")
        service.analyze(other.virtualFile.url, -1); await()
        assertNull(panel.hierarchyTree.selectionPath)
        assertFalse(panel.focusSummary.isVisible)
        val original = myFixture.findFileInTempDir("src/main/java/example/ExamplePage.java")
        service.analyze(original.url, -1); await()
        assertEquals("Focused: left", panel.focusSummary.text)
        assertEquals("postcode", selected(panel).name)
    }

    fun testFocusAndTraversalLeaveTextAndExportComplete() {
        fixture(); val panel = panel()
        val prepared = service.state.prepared!!
        select(panel, "left"); performReportAction(panel.focusAction)
        performReportAction(panel.nextFindingAction)
        assertSame(prepared, service.state.prepared)
        assertEquals(prepared.text, panel.reportText.text)
        assertTrue(prepared.json.contains("right"))
        performReportAction(panel.copyAction)
        assertEquals("Focused: left", panel.focusSummary.text)
    }

    fun testExplicitlyCollapsedPageRootStaysCollapsedAfterReanalysis() {
        val (java, _) = fixture(); val panel = panel()
        panel.hierarchyTree.collapseRow(0)
        edit(java, "\n${java.text}"); reanalyze()
        assertFalse(panel.hierarchyTree.isExpanded(0))
    }

    fun testFocusNamesAndTooltipsRemainLiteralText() {
        val java = pageFile(body = """
            org.apache.wicket.markup.html.form.Form<Void> form = new org.apache.wicket.markup.html.form.Form<>("<html>scope");
            form.add(new Label("title")); add(form);
        """.trimIndent())
        companion(html = "<form wicket:id=\"&lt;html&gt;scope\"><span wicket:id=\"title\"></span></form>")
        service.analyze(java.virtualFile.url, -1); await()
        val panel = panel()
        select(panel, "<html>scope"); performReportAction(panel.focusAction)
        assertNull(panel.focusSummary.getClientProperty("html"))
        val tooltip = panel.focusSummary.createToolTip()
        tooltip.tipText = panel.focusSummary.toolTipText
        assertNull(tooltip.getClientProperty("html"))
    }

    fun testDynamicIdSelectionIsNotGuessedAfterReanalysis() {
        val java = myFixture.addFileToProject("src/main/java/example/ExamplePage.java", """
            package example; public class ExamplePage extends org.apache.wicket.markup.html.WebPage {
                public ExamplePage(String id) { add(new org.apache.wicket.markup.html.basic.Label(id)); }
            }
        """.trimIndent())
        companion()
        service.analyze(java.virtualFile.url, -1); await()
        val panel = panel()
        select(panel, "<unresolved ID>")
        edit(java, "\n${java.text}"); reanalyze()
        assertNull(panel.hierarchyTree.selectionPath)
        assertFalse(panel.javaAction.isEnabled)
    }

    private fun fixture(): Pair<PsiFile, PsiFile> {
        val java = pageFile(body = """
            org.apache.wicket.markup.html.form.Form<Void> left = new org.apache.wicket.markup.html.form.Form<>("left");
            org.apache.wicket.markup.html.form.Form<Void> right = new org.apache.wicket.markup.html.form.Form<>("right");
            left.add(new Label("name"));
            right.add(new Label("name")); add(left, right);
        """.trimIndent())
        val html = companion(html = "<form wicket:id=\"left\"><input wicket:id=\"postcode\"></form><form wicket:id=\"right\"><span wicket:id=\"name\"></span></form>")
        service.analyze(java.virtualFile.url, -1); await()
        return java to html
    }
    private fun panel() = WicketReportPanel(service).also { Disposer.register(testRootDisposable, it) }
    private fun DefaultMutableTreeNode.entry() = userObject as ReportTreeEntry
    private fun rows(panel: WicketReportPanel) = (panel.hierarchyTree.model.root as DefaultMutableTreeNode).preorderEnumeration().toList()
        .mapNotNull { (it as DefaultMutableTreeNode).userObject as? ReportTreeEntry }
    private fun node(panel: WicketReportPanel, name: String, parent: String? = null) = (panel.hierarchyTree.model.root as DefaultMutableTreeNode).preorderEnumeration().toList()
        .map { it as DefaultMutableTreeNode }.single {
            (it.userObject as? ReportTreeEntry)?.name == name && (parent == null || (it.parent as? DefaultMutableTreeNode)?.entry()?.name == parent)
        }
    private fun selected(panel: WicketReportPanel) = (panel.hierarchyTree.lastSelectedPathComponent as DefaultMutableTreeNode).entry()
    private fun select(panel: WicketReportPanel, name: String, parent: String? = null) { panel.hierarchyTree.selectionPath = TreePath(node(panel, name, parent).path) }
    private fun hierarchySelectRoot(panel: WicketReportPanel) = select(panel, "ExamplePage")
    private fun hierarchyExpand(panel: WicketReportPanel, name: String) = panel.hierarchyTree.expandPath(TreePath(node(panel, name).path))
    private fun edit(file: PsiFile, text: String) { WriteCommandAction.runWriteCommandAction(project) { PsiDocumentManager.getInstance(project).getDocument(file)!!.setText(text) } }
    private fun reanalyze() { PsiDocumentManager.getInstance(project).commitAllDocuments(); service.reanalyze(); await() }
    private fun await() {
        PlatformTestUtil.waitWithEventsDispatching({ "Exploration report: ${service.state.message}" }, { !service.state.running }, 30)
        assertNotNull(service.state.message, service.state.prepared)
    }
    private fun assertEditor(location: SourceLocation) {
        PlatformTestUtil.waitWithEventsDispatching("Restored source navigation", {
            val editor = FileEditorManager.getInstance(project).selectedTextEditor
            editor != null && FileDocumentManager.getInstance().getFile(editor.document)?.path == location.file && editor.caretModel.offset == location.offset
        }, 10)
    }
}
