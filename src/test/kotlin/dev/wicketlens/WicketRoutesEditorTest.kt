package dev.wicketlens

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.util.Disposer
import com.intellij.psi.PsiDocumentManager
import com.intellij.testFramework.PlatformTestUtil
import dev.wicketlens.model.SourceLocation
import dev.wicketlens.routes.*
import dev.wicketlens.ui.ReportToolbarAction
import dev.wicketlens.ui.WicketLensService
import javax.swing.JLabel

class WicketRoutesEditorTest : WicketProjectPsiTestCase() {
    private val routes get() = project.service<WicketRoutesService>()
    private val components get() = project.service<WicketLensService>()
    override fun tearDown() {
        try { routes.cancel(); components.cancel() } finally { super.tearDown() }
    }
    private fun application(name: String = "App", path: String = "/people") = myFixture.addFileToProject("src/main/java/example/$name.java", """
        package example; public class $name extends org.apache.wicket.protocol.http.WebApplication {
            public Class<? extends org.apache.wicket.Page> getHomePage() { return ExamplePage.class; }
            protected void init() { super.init(); mountPage("$path", ExamplePage.class); }
        }
    """.trimIndent())
    private fun refresh() {
        routes.refresh()
        PlatformTestUtil.waitWithEventsDispatching({ routes.state.message }, { !routes.state.running }, 15)
        assertNotNull(routes.state.message, routes.state.snapshot)
        assertTrue(routes.current())
    }
    private fun panel() = WicketRoutesPanel(project, routes).also { Disposer.register(testRootDisposable, it) }
    private fun click(action: ReportToolbarAction) = action.actionPerformed(AnActionEvent.createFromAnAction(action, null, "test", DataContext.EMPTY_CONTEXT))
    private fun assertOpened(location: SourceLocation) {
        val editors = FileEditorManager.getInstance(project)
        PlatformTestUtil.waitWithEventsDispatching("Route source navigation", {
            editors.selectedFiles.singleOrNull()?.path == location.file && editors.selectedTextEditor?.caretModel?.offset == location.offset
        }, 15)
    }

    fun testProjectViewDoesNotDependOnTheCurrentEditorOrComponentsSelection() {
        pageFile(); companion(); application()
        val unrelated = myFixture.addFileToProject("notes.txt", "not a Wicket file")
        myFixture.openFileInEditor(unrelated.virtualFile)
        val before = components.state
        refresh()
        assertSame(before, components.state)
        assertEquals("notes.txt", FileEditorManager.getInstance(project).selectedFiles.single().name)
        assertEquals("/people", panel().rows.single().mount.path)
    }

    fun testSearchAndApplicationFiltersRetainTheGroupedDeclarations() {
        pageFile(); companion(); application("FirstApp", "/people"); application("OtherApp", "/orders")
        refresh(); val panel = panel()
        assertEquals(2, panel.rows.size)
        panel.search.text = "orders examplepage"
        assertEquals("example.OtherApp", panel.rows.single().application.className)
        panel.search.text = ""; panel.applications.selectedItem = "example.FirstApp"
        assertEquals("/people", panel.rows.single().mount.path)
        panel.modules.selectedItem = module.name
        assertEquals(1, panel.rows.size)
        panel.sourceSet.selectedItem = "test"
        assertTrue(panel.rows.isEmpty())
    }

    fun testToolbarNavigatesToJavaHtmlAndTheMountDeclaration() {
        pageFile(); companion(); application(); refresh()
        val panel = panel(); panel.table.setRowSelectionInterval(0, 0)
        val mount = panel.rows.single().mount
        assertTrue(panel.pageAction.isEnabled); click(panel.pageAction); assertOpened(mount.pageSource!!)
        assertTrue(panel.htmlAction.isEnabled); click(panel.htmlAction); assertOpened(mount.markupSource!!)
        assertTrue(panel.registrationAction.isEnabled); click(panel.registrationAction); assertOpened(mount.registration)
    }

    fun testStaleEditDisablesActionsAndRejectsOldNavigationUntilRefresh() {
        pageFile(); companion(); val app = application(); refresh()
        val panel = panel(); panel.table.setRowSelectionInterval(0, 0)
        val old = panel.rows.single().mount
        WriteCommandAction.runWriteCommandAction(project) {
            PsiDocumentManager.getInstance(project).getDocument(app)!!.setText(app.text.replace("/people", "/updated"))
        }
        assertFalse(routes.current()); assertTrue(routes.state.stale)
        assertFalse(panel.pageAction.isEnabled); assertFalse(panel.htmlAction.isEnabled)
        assertFalse(panel.registrationAction.isEnabled); assertFalse(panel.analyzeAction.isEnabled)
        assertFalse(routes.navigate(old.registration)); assertFalse(routes.analyze(old))
        PsiDocumentManager.getInstance(project).commitAllDocuments(); refresh()
        assertEquals("/updated", panel.rows.single().mount.path)
    }

    fun testNavigationRejectsAnOffsetOutsideTheSnapshotLocations() {
        pageFile(); companion(); application(); refresh()
        val mount = routes.state.snapshot!!.routes.applications.single().mounts.single()
        assertFalse(routes.navigate(mount.registration.copy(offset = mount.registration.offset + 1)))
        assertFalse(routes.navigate(SourceLocation("/tmp/other-project/Page.java", 0)))
    }

    fun testAnalyzePageReusesTheComponentsServiceAndPreservesFollowPreference() {
        pageFile(); companion(); application(); refresh()
        var selected = false
        val panel = WicketRoutesPanel(project, routes) { selected = true }.also { Disposer.register(testRootDisposable, it) }
        val follow = components.followEditor
        panel.table.setRowSelectionInterval(0, 0); assertTrue(panel.analyzeAction.isEnabled); click(panel.analyzeAction)
        assertTrue(selected)
        PlatformTestUtil.waitWithEventsDispatching({ components.state.message }, { !components.state.running }, 15)
        assertEquals("example.ExamplePage", components.state.prepared!!.report.qualifiedClassName)
        assertEquals(follow, components.followEditor)
    }

    fun testLiteralPathsAreDisplayedAsTextRatherThanHtml() {
        pageFile(); application(path = "<html><b>literal</b>"); refresh()
        val panel = panel()
        val renderer = panel.table.prepareRenderer(panel.table.getCellRenderer(0, 0), 0, 0) as JLabel
        assertEquals("<html><b>literal</b>", renderer.text)
        assertEquals(true, renderer.getClientProperty("html.disable"))
    }

    fun testNoApplicationsStillExplainsDiscoveryCoverage() {
        pageFile(); refresh(); val panel = panel()
        assertTrue(panel.rows.isEmpty())
        assertTrue(panel.details.text.contains("not a complete active runtime route table"))
        assertFalse(panel.pageAction.isEnabled)
    }
}
