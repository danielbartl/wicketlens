package dev.wicketlens

import com.intellij.openapi.command.WriteCommandAction
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
import dev.wicketlens.model.DiagnosticCode
import dev.wicketlens.model.SourceLocation
import dev.wicketlens.ui.*
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.TreePath

class WicketHierarchyFilteringViewTest : WicketProjectPsiTestCase() {
    private val service get() = project.service<WicketLensService>()
    override fun setUp() { super.setUp(); service.setEditorFollowing(false); service.cancel() }
    override fun tearDown() {
        try {
            service.cancel()
            FileEditorManager.getInstance(project).openFiles.forEach { FileEditorManager.getInstance(project).closeFile(it) }
        } finally { super.tearDown() }
    }

    fun testUnpairedRowsKeepMatchedContainersAndHideUnrelatedRows() {
        fixture()
        val panel = panel()
        panel.hierarchyStatus.selectedItem = HierarchyStatusFilter.UNPAIRED
        assertEquals(setOf("ExamplePage", "form", "address", "postcode", "javaOnly"), rows(panel).map { it.name }.toSet())
        assertTrue(rows(panel).single { it.name == "form" }.contextOnly)
        assertFalse(rows(panel).single { it.name == "postcode" }.contextOnly)
        assertEquals("2 matching rows · parents kept for context", panel.filterSummary.text)
    }

    fun testMarkupOnlyFindingsUseTheirRealMatchedParentHierarchy() {
        fixture()
        val panel = panel()
        panel.hierarchySearch.text = "postcode"
        val child = node(panel, "postcode")
        assertEquals("address", (child.parent as DefaultMutableTreeNode).entry().name)
        assertEquals("form", (child.parent.parent as DefaultMutableTreeNode).entry().name)
        assertEquals(listOf("ExamplePage", "form", "address", "postcode"), child.path.map { (it as DefaultMutableTreeNode).entry().name })
        assertTrue(panel.hierarchyTree.isVisible(TreePath(child.path)))
    }

    fun testQualifiedTypeSearchAndStatusSelectionWorkTogether() {
        fixture()
        val panel = panel()
        panel.hierarchySearch.text = "org.apache.wicket.markup.html.basic.Label"
        panel.hierarchyStatus.selectedItem = HierarchyStatusFilter.JAVA_ONLY
        assertEquals(setOf("ExamplePage", "form", "javaOnly"), rows(panel).map { it.name }.toSet())
    }

    fun testQueryCanFindAFullComponentPath() {
        fixture()
        val panel = panel()
        panel.hierarchySearch.text = "form:address:postcode"
        assertEquals(4, rows(panel).size)
        assertEquals("postcode", rows(panel).last().name)
    }

    fun testEmptyResultsClearHiddenSelectionDetailsAndNavigation() {
        fixture()
        val panel = panel()
        select(panel, "title")
        assertTrue(panel.javaAction.isEnabled)
        panel.hierarchySearch.text = "does-not-exist"
        assertEquals(listOf("ExamplePage"), rows(panel).map { it.name })
        assertNull(panel.hierarchyTree.selectionPath)
        assertEquals("", panel.detailText.text)
        assertFalse(panel.javaAction.isEnabled)
        assertFalse(panel.markupAction.isEnabled)
        assertTrue(panel.copyAction.isEnabled)
        assertEquals("No matching rows", panel.filterSummary.text)
    }

    fun testSelectionAndSourceActionsSurviveIfTheSelectedRowStillMatches() {
        val (_, html) = fixture()
        val panel = panel()
        select(panel, "postcode")
        panel.hierarchyStatus.selectedItem = HierarchyStatusFilter.MARKUP_ONLY
        assertEquals("postcode", (panel.hierarchyTree.lastSelectedPathComponent as DefaultMutableTreeNode).entry().name)
        assertFalse(panel.javaAction.isEnabled)
        assertTrue(panel.markupAction.isEnabled)
        performReportAction(panel.markupAction)
        assertEditor(SourceLocation(html.virtualFile.path, html.text.indexOf("<input")))
    }

    fun testFilteringDoesNotChangeTheFullReportTextOrExport() {
        fixture()
        val panel = panel()
        val prepared = service.state.prepared!!
        val text = panel.reportText.text
        panel.hierarchyStatus.selectedItem = HierarchyStatusFilter.MARKUP_ONLY
        panel.hierarchySearch.text = "postcode"
        assertSame(prepared, service.state.prepared)
        assertEquals(text, panel.reportText.text)
        assertEquals(prepared.json, service.state.prepared!!.json)
        assertTrue(prepared.json.contains("javaOnly"))
        assertTrue(prepared.json.contains("title"))
        performReportAction(panel.copyAction)
        assertEquals(4, rows(panel).size)
        assertEquals("postcode", panel.hierarchySearch.text)
    }

    fun testClearingFiltersRestoresTheFullTreeAndVisibleExpansion() {
        fixture()
        val panel = panel()
        performReportAction(panel.expandAction)
        val count = panel.hierarchyTree.rowCount
        panel.hierarchySearch.text = "postcode"
        panel.hierarchySearch.text = ""
        assertEquals(count, panel.hierarchyTree.rowCount)
        assertTrue(rows(panel).any { it.name == "title" })
        assertFalse(panel.filterSummary.isVisible)
    }

    fun testSearchAndStatusRemainAppliedAfterReanalysis() {
        val (java, _) = fixture()
        val panel = panel()
        panel.hierarchyStatus.selectedItem = HierarchyStatusFilter.MARKUP_ONLY
        panel.hierarchySearch.text = "postcode"
        edit(java, "\n${java.text}")
        PsiDocumentManager.getInstance(project).commitAllDocuments()
        service.reanalyze(); await()
        assertEquals("postcode", panel.hierarchySearch.text)
        assertEquals(HierarchyStatusFilter.MARKUP_ONLY, panel.hierarchyStatus.selectedItem)
        assertEquals(setOf("ExamplePage", "form", "address", "postcode"), rows(panel).map { it.name }.toSet())
    }

    fun testFiltersRemainUsefulOnAStaleSnapshotButNavigationStaysBlocked() {
        val (java, _) = fixture()
        val panel = panel()
        edit(java, "\n${java.text}")
        assertTrue(service.state.stale)
        panel.hierarchySearch.text = "postcode"
        select(panel, "postcode")
        assertEquals(4, rows(panel).size)
        assertFalse(panel.javaAction.isEnabled)
        assertFalse(panel.markupAction.isEnabled)
        assertFalse(panel.copyAction.isEnabled)
    }

    fun testFilterChangesDoNotStealTextReportNavigation() {
        val (java, _) = fixture()
        val panel = panel()
        panel.reportTabs.selectedIndex = 1
        panel.reportText.caretPosition = panel.reportText.text.indexOf("title : Label")
        panel.hierarchySearch.text = "postcode"
        assertTrue(panel.javaAction.isEnabled)
        performReportAction(panel.javaAction)
        assertEditor(SourceLocation(java.virtualFile.path, java.text.indexOf("new Label(\"title\")")))
    }

    fun testQueryAndStatusRemainAvailableWhenAnotherPageReplacesTheReport() {
        fixture()
        val panel = panel()
        panel.hierarchyStatus.selectedItem = HierarchyStatusFilter.MARKUP_ONLY
        panel.hierarchySearch.text = "postcode"
        val other = pageFile("OtherPage", "")
        companion("OtherPage", "<span wicket:id=\"postcode\"></span>")
        service.analyze(other.virtualFile.url, -1); await()
        assertEquals(HierarchyStatusFilter.MARKUP_ONLY, panel.hierarchyStatus.selectedItem)
        assertEquals("postcode", panel.hierarchySearch.text)
        assertEquals(setOf("OtherPage", "Markup findings", "postcode"), rows(panel).map { it.name }.toSet())
    }

    fun testUnresolvedFilterIncludesScopeDiagnosticsButNotPartialNotice() {
        val java = pageFile(body = "if (System.nanoTime() > 0) add(new Label(\"title\"));")
        companion()
        service.analyze(java.virtualFile.url, -1); await()
        val panel = panel()
        panel.hierarchyStatus.selectedItem = HierarchyStatusFilter.UNRESOLVED
        assertTrue(rows(panel).any { it.status == DiagnosticCode.UNRESOLVED_SCOPE })
        assertTrue(rows(panel).none { it.status == DiagnosticCode.PARTIAL_ANALYSIS })
    }

    fun testFilteringHonorsCancellation() {
        fixture()
        val indicator = EmptyProgressIndicator()
        assertThrows(ProcessCanceledException::class.java) {
            ProgressManager.getInstance().runProcess(Runnable {
                indicator.cancel()
                ReportHierarchyFilter().apply(service.state.prepared!!.hierarchy, "postcode", HierarchyStatusFilter.ALL)
            }, indicator)
        }
    }

    private fun fixture(): Pair<PsiFile, PsiFile> {
        val java = pageFile(body = """
            org.apache.wicket.markup.html.form.Form<Void> form = new org.apache.wicket.markup.html.form.Form<>("form");
            org.apache.wicket.markup.html.WebMarkupContainer address = new org.apache.wicket.markup.html.WebMarkupContainer("address");
            form.add(address); form.add(new Label("title"), new Label("javaOnly")); add(form);
        """.trimIndent())
        val html = companion(html = "<form wicket:id=\"form\"><div wicket:id=\"address\"><input wicket:id=\"postcode\"></div><span wicket:id=\"title\"></span></form>")
        service.analyze(java.virtualFile.url, -1); await()
        return java to html
    }
    private fun panel() = WicketReportPanel(service).also { Disposer.register(testRootDisposable, it) }
    private fun DefaultMutableTreeNode.entry() = userObject as ReportTreeEntry
    private fun rows(panel: WicketReportPanel) = (panel.hierarchyTree.model.root as DefaultMutableTreeNode).preorderEnumeration().toList()
        .mapNotNull { (it as DefaultMutableTreeNode).userObject as? ReportTreeEntry }
    private fun node(panel: WicketReportPanel, name: String) = (panel.hierarchyTree.model.root as DefaultMutableTreeNode).preorderEnumeration().toList()
        .map { it as DefaultMutableTreeNode }.single { (it.userObject as? ReportTreeEntry)?.name == name }
    private fun select(panel: WicketReportPanel, name: String) { panel.hierarchyTree.selectionPath = TreePath(node(panel, name).path) }
    private fun edit(file: PsiFile, text: String) {
        WriteCommandAction.runWriteCommandAction(project) { PsiDocumentManager.getInstance(project).getDocument(file)!!.setText(text) }
    }
    private fun await() {
        PlatformTestUtil.waitWithEventsDispatching({ "Filter report: ${service.state.message}" }, { !service.state.running }, 30)
        assertNotNull(service.state.message, service.state.prepared)
    }
    private fun assertEditor(location: SourceLocation) {
        PlatformTestUtil.waitWithEventsDispatching("Filtered source navigation", {
            val editor = FileEditorManager.getInstance(project).selectedTextEditor
            editor != null && FileDocumentManager.getInstance().getFile(editor.document)?.path == location.file && editor.caretModel.offset == location.offset
        }, 10)
    }
}
