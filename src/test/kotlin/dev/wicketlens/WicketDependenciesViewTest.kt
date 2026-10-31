package dev.wicketlens

import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.util.Disposer
import com.intellij.psi.PsiFile
import com.intellij.testFramework.PlatformTestUtil
import dev.wicketlens.model.SourceLocation
import dev.wicketlens.ui.*
import java.awt.event.ActionEvent
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.TreePath

class WicketDependenciesViewTest : WicketSpringInspectionTestCase() {
    private val service get() = project.service<WicketLensService>()
    override fun setUp() { super.setUp(); service.setEditorFollowing(false); service.cancel() }
    override fun tearDown() {
        try {
            service.cancel()
            FileEditorManager.getInstance(project).openFiles.forEach { FileEditorManager.getInstance(project).closeFile(it) }
        } finally { super.tearDown() }
    }
    private fun config(methods: String = "@org.springframework.context.annotation.Bean(name={\"orders\",\"alias\"}) Service orders() { return new Service(); }") =
        source("Config", "@org.springframework.context.annotation.Configuration public class Config { $methods }")
    private fun analyze(java: PsiFile) { service.analyze(java.virtualFile.url, -1); await() }
    private fun await() {
        PlatformTestUtil.waitWithEventsDispatching({ "Analysis failed: ${service.state.message}" }, { !service.state.running }, 30)
        assertNotNull(service.state.message, service.state.prepared)
    }
    private fun panel() = WicketReportPanel(service).also { Disposer.register(testRootDisposable, it); it.reportTabs.selectedIndex = 2 }
    private fun entries(panel: WicketReportPanel) = (panel.dependenciesPanel.tree.model.root as DefaultMutableTreeNode).preorderEnumeration().toList()
        .map { it as DefaultMutableTreeNode }.filter { it.userObject is DependencyTreeEntry }
    private fun select(panel: WicketReportPanel, predicate: (DependencyTreeEntry) -> Boolean) = entries(panel).single { predicate(it.userObject as DependencyTreeEntry) }.also {
        panel.dependenciesPanel.tree.selectionPath = TreePath(it.path)
    }
    private fun opened(location: SourceLocation) {
        PlatformTestUtil.waitWithEventsDispatching({ "Navigation failed to $location" }, {
            val editor = FileEditorManager.getInstance(project).selectedTextEditor
            editor != null && FileDocumentManager.getInstance().getFile(editor.document)?.path == location.file && editor.caretModel.offset == location.offset
        }, 10)
    }

    fun testDependenciesTabShowsOwnersFieldsAndSourceCandidates() {
        config()
        val java = page("@SpringBean Service orders;")
        analyze(java)
        val panel = panel()
        assertEquals("Dependencies", panel.reportTabs.getTitleAt(2))
        assertEquals("Text report", panel.reportTabs.getTitleAt(1))
        assertEquals(3, entries(panel).size)
        select(panel) { it.kind == "field" }
        assertTrue(panel.dependenciesPanel.details.text.contains("example.ExamplePage.orders"))
        assertTrue(panel.dependenciesPanel.details.text.contains("runtime selection"))
        assertTrue(panel.dependenciesPanel.summary.text.contains("1 of 1"))
        assertFalse(panel.dependenciesPanel.details.isEditable)
    }
    fun testFieldAndBeanButtonsNavigateToTheirOwnSources() {
        val config = config()
        val java = page("@SpringBean Service orders;")
        analyze(java)
        val panel = panel()
        val field = select(panel) { it.kind == "field" }.userObject as DependencyTreeEntry
        assertTrue(panel.dependenciesPanel.fieldAction.isEnabled)
        assertTrue(panel.dependenciesPanel.beanAction.isEnabled)
        performReportAction(panel.dependenciesPanel.fieldAction)
        opened(field.fieldLocation!!)
        performReportAction(panel.dependenciesPanel.beanAction)
        opened(field.beanLocation!!)
        assertEquals(config.virtualFile.path, field.beanLocation.file)
    }
    fun testGlobalJavaActionUsesTheDependencySelectionAndHtmlIsDisabled() {
        config()
        analyze(page("@SpringBean Service orders;"))
        val panel = panel()
        val entry = select(panel) { it.kind == "bean" }.userObject as DependencyTreeEntry
        assertTrue(panel.javaAction.isEnabled)
        assertFalse(panel.markupAction.isEnabled)
        performReportAction(panel.javaAction)
        opened(entry.beanLocation!!)
    }
    fun testAmbiguousFieldDoesNotChooseTheFirstBeanSource() {
        config("@org.springframework.context.annotation.Bean Service first() { return new Service(); } @org.springframework.context.annotation.Bean Service second() { return new Service(); }")
        analyze(page("@SpringBean Service orders;"))
        val panel = panel()
        select(panel) { it.kind == "field" }
        assertFalse(panel.dependenciesPanel.beanAction.isEnabled)
        val bean = select(panel) { it.kind == "bean" && it.name == "second" }.userObject as DependencyTreeEntry
        assertTrue(panel.dependenciesPanel.beanAction.isEnabled)
        performReportAction(panel.dependenciesPanel.beanAction)
        opened(bean.beanLocation!!)
    }
    fun testEnterOnACandidateNavigatesToItsDeclaration() {
        config()
        analyze(page("@SpringBean Service orders;"))
        val panel = panel()
        val bean = select(panel) { it.kind == "bean" }.userObject as DependencyTreeEntry
        panel.dependenciesPanel.tree.actionMap.get("wicketlens.dependency.navigate").actionPerformed(ActionEvent(panel, 0, ""))
        opened(bean.beanLocation!!)
    }
    fun testSearchMatchesBeanAliasesAndKeepsOwnerAndFieldContext() {
        config()
        analyze(page("@SpringBean Service orders; @SpringBean String title;"))
        val panel = panel()
        panel.dependenciesPanel.search.text = "alias"
        assertEquals(listOf("owner", "field", "bean"), entries(panel).map { (it.userObject as DependencyTreeEntry).kind })
        assertTrue(panel.dependenciesPanel.summary.text.contains("1 of 2"))
        panel.dependenciesPanel.search.text = "nothing"
        assertTrue(entries(panel).isEmpty())
        assertTrue(panel.dependenciesPanel.summary.text.contains("0 of 2"))
        panel.dependenciesPanel.search.text = ""
        assertEquals(4, entries(panel).size)
    }
    fun testStaleSnapshotRemainsReadableButAllNavigationIsBlocked() {
        val config = config()
        analyze(page("@SpringBean Service orders;"))
        val panel = panel()
        val field = select(panel) { it.kind == "field" }.userObject as DependencyTreeEntry
        val model = panel.dependenciesPanel.tree.model
        edit(config, "\n" + config.text, commit = false)
        assertTrue(service.state.stale)
        assertSame(model, panel.dependenciesPanel.tree.model)
        assertFalse(panel.dependenciesPanel.fieldAction.isEnabled)
        assertFalse(panel.dependenciesPanel.beanAction.isEnabled)
        assertFalse(panel.javaAction.isEnabled)
        assertFalse(service.navigate(field.beanLocation!!))
        assertTrue(panel.dependenciesPanel.details.text.contains("orders"))
        com.intellij.psi.PsiDocumentManager.getInstance(project).commitAllDocuments()
    }
    fun testReanalysisRefreshesBeanOffsetsAndPreservesTheSelectedTab() {
        val config = config()
        analyze(page("@SpringBean Service orders;"))
        val panel = panel()
        val before = select(panel) { it.kind == "field" }.userObject as DependencyTreeEntry
        edit(config, "\n" + config.text)
        service.reanalyze(); await()
        val after = select(panel) { it.kind == "field" }.userObject as DependencyTreeEntry
        assertEquals(2, panel.reportTabs.selectedIndex)
        assertEquals(before.beanLocation!!.offset + 1, after.beanLocation!!.offset)
        assertTrue(panel.dependenciesPanel.beanAction.isEnabled)
    }
    fun testCandidateSourcesAreIncludedInTheSharedVersionSnapshot() {
        val config = config()
        val app = application()
        analyze(page("@SpringBean Service orders;"))
        val sources = service.state.prepared!!.version.sources.map { it.path }
        assertTrue(sources.containsAll(listOf(config.virtualFile.path, app.virtualFile.path)))
    }
    fun testNoSourceCandidateIsNotPresentedAsAMissingBean() {
        analyze(page("@SpringBean(name=\"external\") Service orders;"))
        val panel = panel()
        select(panel) { it.kind == "field" }
        assertTrue(panel.dependenciesPanel.details.text.contains("not a missing-bean finding"))
        assertFalse(panel.dependenciesPanel.beanAction.isEnabled)
        assertTrue(panel.dependenciesPanel.fieldAction.isEnabled)
    }
    fun testCancelClearsTheDependencySnapshotAndActions() {
        config()
        analyze(page("@SpringBean Service orders;"))
        val panel = panel()
        select(panel) { it.kind == "field" }
        service.cancel()
        assertTrue(entries(panel).isEmpty())
        assertFalse(panel.dependenciesPanel.fieldAction.isEnabled)
        assertFalse(panel.dependenciesPanel.beanAction.isEnabled)
    }
    fun testAnUnrelatedOrUnlistedLocationCannotNavigate() {
        val config = config()
        analyze(page("@SpringBean Service orders;"))
        assertFalse(service.navigate(SourceLocation(config.virtualFile.path, config.text.indexOf("return"))))
    }
    fun testFieldNavigationPreservesFollowedPageContext() {
        val config = config()
        val java = page("@SpringBean Service orders;")
        FileEditorManager.getInstance(project).openFile(java.virtualFile, true)
        val panel = panel()
        service.setEditorFollowing(true)
        await()
        val bean = select(panel) { it.kind == "bean" }.userObject as DependencyTreeEntry
        performReportAction(panel.dependenciesPanel.beanAction)
        opened(bean.beanLocation!!)
        assertEquals(config.virtualFile.path, bean.beanLocation.file)
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
        assertEquals("example.ExamplePage", service.state.prepared!!.report.qualifiedClassName)
        assertEquals(1, service.state.prepared!!.injectedDependencies.fieldCount)
    }
    fun testPlainWicketPageHasAClearEmptyState() {
        analyze(page(""))
        val panel = panel()
        assertTrue(entries(panel).isEmpty())
        assertTrue(panel.dependenciesPanel.tree.emptyText.text.contains("No Wicket–Spring"))
        assertTrue(panel.dependenciesPanel.details.text.contains("runtime bindings are not verified"))
    }
    fun testSwitchingToAnotherPageReplacesOwnerAndFieldRows() {
        val first = page("@SpringBean Service orders;")
        val second = page("@SpringBean Service different;", name = "OtherPage")
        analyze(first)
        val panel = panel()
        analyze(second)
        val fields = entries(panel).map { it.userObject as DependencyTreeEntry }.filter { it.kind == "field" }
        assertEquals(listOf("different"), fields.map { it.name })
        assertTrue(fields.single().details.contains("OtherPage"))
    }
    fun testDeletingABeanSourceDisablesNavigationAndRefreshRemovesItsCandidate() {
        val config = config()
        analyze(page("@SpringBean Service orders;"))
        val panel = panel()
        select(panel) { it.kind == "field" }
        com.intellij.openapi.command.WriteCommandAction.runWriteCommandAction(project) { config.delete() }
        assertTrue(service.state.stale)
        assertFalse(panel.dependenciesPanel.beanAction.isEnabled)
        service.reanalyze(); await()
        select(panel) { it.kind == "field" }
        assertFalse(panel.dependenciesPanel.beanAction.isEnabled)
        assertEquals(0, entries(panel).count { (it.userObject as DependencyTreeEntry).kind == "bean" })
    }
    fun testCopyKeepsTheComponentSchemaAndDoesNotExportDependencyRows() {
        config()
        analyze(page("@SpringBean Service orders;"))
        val panel = panel()
        select(panel) { it.kind == "field" }
        assertTrue(service.copyContext())
        val json = service.state.prepared!!.json
        assertFalse(json.contains("injectedDependencies"))
        assertFalse(json.contains("orderService"))
        assertTrue(json.contains("\"schemaVersion\": 1"))
    }
    fun testLiteralMarkupCharactersInBeanNamesAreRenderedAsText() {
        config("@org.springframework.context.annotation.Bean(\"<html>orders\") Service orders() { return new Service(); }")
        analyze(page("@SpringBean Service orders;"))
        val panel = panel()
        select(panel) { it.kind == "bean" }
        assertTrue(panel.dependenciesPanel.details.text.contains("<html>orders"))
        assertFalse(panel.dependenciesPanel.details.isEditable)
    }
    fun testBeanRowIdsStayDistinctForTwoDeclarationsWithTheSameOwnerAndName() {
        config("@org.springframework.context.annotation.Bean Service orders() { return new Service(); } @org.springframework.context.annotation.Bean Service orders(int ignored) { return new Service(); }")
        analyze(page("@SpringBean(name=\"orders\") Service orders;"))
        val beans = entries(panel()).map { it.userObject as DependencyTreeEntry }.filter { it.kind == "bean" }
        assertEquals(2, beans.size)
        assertEquals(2, beans.map { it.id }.distinct().size)
        assertEquals(2, beans.map { it.javaLocation }.distinct().size)
    }
}
