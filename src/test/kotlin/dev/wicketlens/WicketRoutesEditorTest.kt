package dev.wicketlens

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.impl.ApplicationImpl
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.util.Disposer
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.util.PsiModificationTracker
import com.intellij.psi.xml.XmlFile
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.PsiTestUtil
import com.intellij.ui.components.ActionLink
import com.intellij.util.ui.UIUtil
import dev.wicketlens.model.SourceLocation
import dev.wicketlens.routes.*
import dev.wicketlens.ui.ReportToolbarAction
import dev.wicketlens.ui.WicketLensService
import java.awt.Cursor
import java.awt.event.MouseEvent
import java.awt.font.TextAttribute
import javax.swing.JLabel
import org.jetbrains.jps.model.java.JavaSourceRootType

class WicketRoutesEditorTest : WicketProjectPsiTestCase() {
    private val routes get() = project.service<WicketRoutesService>()
    private val components get() = project.service<WicketLensService>()
    override fun tearDown() {
        try { routes.cancel(); components.cancel() } finally { super.tearDown() }
    }
    private fun application(name: String = "App", path: String = "/people", target: String = "ExamplePage", sourceSet: String = "main") =
        myFixture.addFileToProject("src/$sourceSet/java/example/$name.java", """
        package example; public class $name extends org.apache.wicket.protocol.http.WebApplication {
            public Class<? extends org.apache.wicket.Page> getHomePage() { return $target.class; }
            protected void init() { super.init(); mountPage("$path", $target.class); }
        }
        """.trimIndent())
    private fun refresh() {
        routes.refresh()
        awaitRoutes()
    }
    private fun awaitRoutes() {
        PlatformTestUtil.waitWithEventsDispatching({ routes.state.message }, {
            val state = routes.state
            !state.running && (state.snapshot == null || state.stale || routes.current())
        }, 15)
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

    private fun clickPath(panel: WicketRoutesPanel, row: Int) {
        val table = panel.table
        table.setSize(500, 100)
        table.doLayout()
        val cell = table.getCellRect(row, 0, false)
        val x = cell.x + 8
        val y = cell.y + 4
        table.dispatchEvent(MouseEvent(table, MouseEvent.MOUSE_MOVED, 0, 0, x, y, 0, false))
        assertEquals(Cursor.HAND_CURSOR, table.cursor.type)
        table.dispatchEvent(MouseEvent(table, MouseEvent.MOUSE_CLICKED, 0, 0, x, y, 1, false, MouseEvent.BUTTON1))
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

    fun testGutterPageFilterShowsItsAliasesAcrossSourceSetsAndCanBeCleared() {
        val page = pageFile()
        val html = companion()
        pageFile("ExamplePageExtra")
        application("MainApp", "/people")
        application("OtherApp", "/other", "ExamplePageExtra")
        PsiTestUtil.addSourceRoot(module, myFixture.tempDirFixture.findOrCreateDir("src/test/java"), JavaSourceRootType.TEST_SOURCE)
        application("TestApp", "/test-people", sourceSet = "test")
        refresh()
        val panel = panel()
        panel.search.text = "/other"
        assertEquals("/other", panel.rows.single().mount.path)

        panel.showMountedPage(page.virtualFile.path, "example.ExamplePage")
        awaitRoutes()
        assertEquals(setOf("/people", "/test-people"), panel.rows.map { it.mount.path }.toSet())
        assertNotNull(panel.selectedRow())

        panel.showMountedPage(html.virtualFile.path, null)
        assertEquals(setOf("/people", "/test-people"), panel.rows.map { it.mount.path }.toSet())
        val clear = UIUtil.findComponentOfType(panel, ActionLink::class.java)!!
        assertTrue(clear.parent.isVisible)
        clear.doClick()
        assertFalse(clear.parent.isVisible)
        assertEquals("/other", panel.rows.single().mount.path)
    }

    fun testToolbarNavigatesToJavaHtmlAndTheMountDeclaration() {
        pageFile(); companion(); application(); refresh()
        val panel = panel(); panel.table.setRowSelectionInterval(0, 0)
        assertTrue(panel.mountedTestAction.isEnabled)
        val mount = panel.rows.single().mount
        assertTrue(panel.pageAction.isEnabled); click(panel.pageAction); assertOpened(mount.pageSource!!)
        assertTrue(panel.htmlAction.isEnabled); click(panel.htmlAction); assertOpened(mount.markupSource!!)
        assertTrue(panel.registrationAction.isEnabled); click(panel.registrationAction); assertOpened(mount.registration)
    }

    fun testMountedTestActionAcceptsResourceRows() {
        pageFile()
        myFixture.addFileToProject("src/main/java/example/ResourcesApplication.java", """
            package example;
            public class ResourcesApplication extends org.apache.wicket.protocol.http.WebApplication {
                public Class<? extends org.apache.wicket.Page> getHomePage() { return ExamplePage.class; }
                protected void init() {
                    super.init();
                    mountResource("/logo.svg", new org.apache.wicket.request.resource.PackageResourceReference(ExamplePage.class, "logo.svg"));
                }
            }
        """.trimIndent())
        refresh()
        val panel = panel()
        panel.table.setRowSelectionInterval(0, 0)
        assertEquals("/logo.svg", panel.rows.single().mount.path)
        assertTrue(panel.mountedTestAction.isEnabled)
        assertFalse(panel.pageAction.isEnabled)
    }

    fun testClickingThePathOpensItsPageAndTheTableKeepsTwoColumns() {
        pageFile(); companion(); application("FirstApp", "/people"); application("OtherApp", "/orders"); refresh()
        val panel = panel()
        val table = panel.table
        assertEquals(listOf("Mounted path", "Page"), (0 until table.columnCount).map(table::getColumnName))
        val row = panel.rows.indexOfFirst { it.mount.path == "/orders" }
        val page = panel.rows[row].mount.pageSource!!
        val pathLabel = table.prepareRenderer(table.getCellRenderer(row, 0), row, 0) as JLabel
        assertEquals(com.intellij.util.ui.JBUI.CurrentTheme.Link.Foreground.ENABLED, pathLabel.foreground)
        val pageLabel = table.prepareRenderer(table.getCellRenderer(row, 1), row, 1) as JLabel
        assertEquals("ExamplePage · OtherApp", pageLabel.text)
        assertEquals(table.foreground, pageLabel.foreground)
        clickPath(panel, row)
        assertOpened(page)
        table.setRowSelectionInterval(row, row)
        assertTrue(panel.details.text.contains("example.OtherApp"))
        assertTrue(panel.details.text.contains("Module/source set"))
        assertTrue(panel.details.text.contains("DECLARED"))
    }

    fun testRoutesCanBeInvalidatedFromALockFreeUiCallback() {
        pageFile(); companion(); application(); refresh()
        val events = project.messageBus.syncPublisher(PsiModificationTracker.TOPIC)
        (ApplicationManager.getApplication() as ApplicationImpl).withLocksProhibited("Mounted URL test callback") {
            events.modificationCountChanged()
            assertFalse(routes.current())
        }
        PlatformTestUtil.waitWithEventsDispatching("Route freshness rechecked in the background", { routes.current() }, 15)
    }

    fun testStaleEditDisablesActionsAndRejectsOldNavigationUntilRefresh() {
        pageFile(); companion(); val app = application(); refresh()
        val panel = panel(); panel.table.setRowSelectionInterval(0, 0)
        val old = panel.rows.single().mount
        WriteCommandAction.runWriteCommandAction(project) {
            PsiDocumentManager.getInstance(project).getDocument(app)!!.setText(app.text.replace("/people", "/updated"))
        }
        assertFalse(routes.current())
        assertFalse(panel.pageAction.isEnabled); assertFalse(panel.htmlAction.isEnabled)
        assertFalse(panel.registrationAction.isEnabled); assertFalse(panel.analyzeAction.isEnabled)
        assertFalse(routes.navigate(old.registration)); assertFalse(routes.analyze(old))
        PlatformTestUtil.waitWithEventsDispatching("Old route snapshot marked stale", { routes.state.stale }, 15)
        PsiDocumentManager.getInstance(project).commitAllDocuments(); refresh()
        assertEquals("/updated", panel.rows.single().mount.path)
    }

    fun testClickingAStalePathRefreshesAndOpensThePageAtItsNewOffset() {
        val page = pageFile()
        companion(); application(); refresh()
        val panel = panel()
        val before = panel.rows.single().mount.pageSource!!
        val documents = PsiDocumentManager.getInstance(project)
        val prefix = "// Moved class declaration\n"
        WriteCommandAction.runWriteCommandAction(project) {
            documents.getDocument(page)!!.insertString(0, prefix)
        }
        documents.commitAllDocuments()
        PlatformTestUtil.waitWithEventsDispatching("Mounted URLs marked stale", { routes.state.stale }, 15)
        val table = panel.table
        val label = table.prepareRenderer(table.getCellRenderer(0, 0), 0, 0) as JLabel
        assertEquals(TextAttribute.UNDERLINE_ON, label.font.attributes[TextAttribute.UNDERLINE])
        assertTrue(label.toolTipText.contains("refresh and open"))

        clickPath(panel, 0)

        assertOpened(before.copy(offset = before.offset + prefix.length))
    }

    fun testClickSurvivesAnEditBeforeBackgroundNavigationStarts() {
        val page = pageFile()
        companion(); application(); refresh()
        val panel = panel()
        val before = panel.rows.single().mount.pageSource!!
        val documents = PsiDocumentManager.getInstance(project)
        val prefix = "// Shifted after click\n"

        WriteCommandAction.runWriteCommandAction(project) {
            clickPath(panel, 0)
            val document = documents.getDocument(page)!!
            document.insertString(0, prefix)
            documents.commitDocument(document)
        }

        assertOpened(before.copy(offset = before.offset + prefix.length))
    }

    fun testClickingARemovedMountRefreshesWithoutOpeningTheOldPage() {
        pageFile(); companion(); val app = application()
        myFixture.openFileInEditor(app.virtualFile)
        refresh()
        val panel = panel()
        val documents = PsiDocumentManager.getInstance(project)
        WriteCommandAction.runWriteCommandAction(project) {
            documents.getDocument(app)!!.setText(app.text.replace("/people", "/updated"))
        }
        documents.commitAllDocuments()
        PlatformTestUtil.waitWithEventsDispatching("Mounted URLs marked stale", { routes.state.stale }, 15)

        clickPath(panel, 0)

        PlatformTestUtil.waitWithEventsDispatching("Updated mounts displayed", { routes.current() }, 15)
        assertEquals("/updated", panel.rows.single().mount.path)
        assertTrue(routes.state.message.contains("That mount changed"))
        assertEquals(app.virtualFile, FileEditorManager.getInstance(project).selectedFiles.single())
    }

    fun testMountedPageGuttersListAliasesAndFollowRouteEditsWithoutTheToolWindow() {
        val java = pageFile()
        val html = companion() as XmlFile
        application("FirstApp", "/people")
        application("OtherApp", "/people")
        val aliasApp = myFixture.addFileToProject("src/main/java/example/AliasApp.java", """
            package example; public class AliasApp extends org.apache.wicket.protocol.http.WebApplication {
                public Class<? extends org.apache.wicket.Page> getHomePage() { return ExamplePage.class; }
                protected void init() { super.init(); if (true) mountPage("/older<&>", ExamplePage.class); }
            }
        """.trimIndent())
        val other = pageFile("UnrelatedPage")
        val browserSnapshot = routes.state.snapshot
        fun marker(anchor: PsiElement): LineMarkerInfo<*>? {
            val markers = mutableListOf<LineMarkerInfo<*>>()
            WicketMountedUrlLineMarkerProvider().collectSlowLineMarkers(listOf(anchor), markers)
            return markers.singleOrNull()
        }
        fun tooltip(anchor: PsiElement) = marker(anchor)?.lineMarkerTooltip
        val htmlAnchor = html.rootTag!!.firstChild
        val gutter = marker(htmlAnchor)!!.createGutterRenderer()!!
        assertTrue(gutter.isNavigateAction)
        assertNotNull(gutter.clickAction)
        fun javaTooltip() = tooltip(java.classes.single().nameIdentifier!!)!!
        val first = javaTooltip()
        assertEquals(first, tooltip(htmlAnchor))
        assertEquals(1, Regex("/people").findAll(first).count())
        assertTrue(first.contains("/older&lt;&amp;&gt; (conditional)"))
        assertNull(tooltip(other.classes.single().nameIdentifier!!))
        val documents = PsiDocumentManager.getInstance(project)
        WriteCommandAction.runWriteCommandAction(project) {
            val document = documents.getDocument(aliasApp)!!
            document.setText(document.text.replace("/older<&>", "/newer"))
        }
        documents.commitAllDocuments()
        assertTrue(javaTooltip().contains("/newer (conditional)"))
        assertFalse(javaTooltip().contains("/older"))
        assertEquals(javaTooltip(), tooltip(html.rootTag!!.firstChild))
        assertSame(browserSnapshot, routes.state.snapshot)
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
        assertTrue(panel.details.text.contains("Runtime route selection is unknown"))
        assertFalse(panel.pageAction.isEnabled)
    }

    fun testInheritedHelperRegistrationNavigatesToItsOwnSourceAndAnalyzesThePage() {
        pageFile(); companion()
        val parent = myFixture.addFileToProject("src/main/java/example/BaseApp.java", """
            package example; public abstract class BaseApp extends org.apache.wicket.protocol.http.WebApplication {
                protected void init() { super.init(); routes(); }
                private void routes() { mount(new org.apache.wicket.core.request.mapper.MountedMapper("/inherited", ExamplePage.class)); }
            }
        """.trimIndent())
        myFixture.addFileToProject("src/main/java/example/App.java", """
            package example; public class App extends BaseApp {
                public Class<? extends org.apache.wicket.Page> getHomePage() { return ExamplePage.class; }
            }
        """.trimIndent())
        refresh(); val panel = panel(); panel.table.setRowSelectionInterval(0, 0)
        val mount = panel.rows.single().mount
        assertEquals(parent.virtualFile.path, mount.registration.file)
        assertTrue(panel.details.text.contains("BaseApp.routes"))
        assertTrue(panel.details.text.contains("Source MountedMapper registration"))
        click(panel.registrationAction); assertOpened(mount.registration)
        click(panel.analyzeAction)
        PlatformTestUtil.waitWithEventsDispatching({ components.state.message }, { !components.state.running }, 15)
        assertEquals("example.ExamplePage", components.state.prepared!!.report.qualifiedClassName)
    }
}
