package dev.wicketlens

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.command.undo.UndoManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.ui.TestDialog
import com.intellij.openapi.ui.TestDialogManager
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.PsiManager
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.PsiTestUtil
import com.intellij.testFramework.PlatformTestUtil
import dev.wicketlens.actions.WicketCompanionNavigation
import dev.wicketlens.actions.WicketComponentFiles
import dev.wicketlens.analysis.AnalysisUnavailable
import dev.wicketlens.analysis.WicketPageResolver
import org.jetbrains.jps.model.java.JavaResourceRootType
import org.jetbrains.jps.model.java.JavaSourceRootType

class WicketCompanionActionsTest : WicketJavaPsiTestCase() {
    override fun getProjectDescriptor(): LightProjectDescriptor = DESCRIPTOR

    override fun setUp() {
        super.setUp()
        ModuleRootModificationUtil.updateModel(module) { model ->
            model.contentEntries.forEach { entry ->
                entry.sourceFolders.forEach { entry.removeSourceFolder(it) }
            }
        }
        PsiTestUtil.addSourceRoot(module, dir("src/main/java"), JavaSourceRootType.SOURCE)
        PsiTestUtil.addSourceRoot(module, dir("src/main/resources"), JavaResourceRootType.RESOURCE)
        PsiTestUtil.addSourceRoot(module, dir("src/test/java"), JavaSourceRootType.TEST_SOURCE)
        PsiTestUtil.addSourceRoot(module, dir("src/test/resources"), JavaResourceRootType.TEST_RESOURCE)
    }

    fun testActionsAreRegistered() {
        val actions = ActionManager.getInstance()
        for (name in listOf("SwitchCompanion", "CreateMarkup", "NewPage", "NewPanel")) {
            assertNotNull(actions.getAction("WicketLens.$name"))
        }
    }

    fun testSwitchActionOpensTheCompanionEditor() {
        val java = page()
        val markup = html("src/main/resources/example/ExamplePage.html")
        myFixture.configureFromExistingVirtualFile(java.virtualFile)
        myFixture.performEditorAction("WicketLens.SwitchCompanion")
        val editors = FileEditorManager.getInstance(project)
        PlatformTestUtil.waitWithEventsDispatching("The markup editor should open", {
            editors.selectedFiles.singleOrNull() == markup.virtualFile
        }, 10)
    }

    fun testSwitchesBetweenNestedClassAndItsMarkupVariants() {
        val java = page(members = "static class Nested extends org.apache.wicket.markup.html.WebPage {}")
        val nested = java.classes.single().innerClasses.single()
        val basename = "ExamplePage\$Nested"
        val base = html("src/main/resources/example/$basename.html")
        val variant = html("src/main/resources/example/${basename}_fr.html")
        html("src/test/resources/example/${basename}_de.html")
        assertEquals(listOf(base.virtualFile, variant.virtualFile),
            WicketCompanionNavigation.destinations(project, java.virtualFile, nested.textOffset).map { it.file })
        val reverse = WicketCompanionNavigation.destinations(project, variant.virtualFile, 0).single()
        assertEquals(java.virtualFile, reverse.file)
        assertEquals(nested.textOffset, reverse.offset)
    }

    fun testAmbiguousCompanionsAreWithheld() {
        val java = page()
        html("src/main/java/example/ExamplePage.html")
        html("src/main/resources/example/ExamplePage.html")
        assertThrows(AnalysisUnavailable::class.java) {
            WicketCompanionNavigation.destinations(project, java.virtualFile, 0)
        }
        assertThrows(AnalysisUnavailable::class.java) {
            WicketComponentFiles.markupPlan(project, java.virtualFile, 0)
        }
    }

    fun testCreatesMissingPanelMarkupInItsOwnTestResourceRoot() {
        val java = myFixture.addFileToProject("src/test/java/example/TestPanel.java", """
            package example;
            public class TestPanel extends org.apache.wicket.markup.html.panel.Panel {
                public TestPanel(String id) { super(id); }
            }
        """.trimIndent()) as PsiJavaFile
        val plan = WicketComponentFiles.markupPlan(project, java.virtualFile, java.classes.single().textOffset)
        val destination = plan.destinations.single()
        assertEquals(dir("src/test/resources"), destination.root)
        val markup = WicketComponentFiles.createMarkup(project, plan, destination)
        assertTrue(markup.text.contains("<wicket:panel>"))
        assertEquals(markup.virtualFile.url, WicketPageResolver().resolve(java.classes.single()).markupUrl)
        assertEquals(java.virtualFile, WicketCompanionNavigation.destinations(project, markup.virtualFile, 0).single().file)
    }

    fun testDoesNotMaskInheritedMarkup() {
        val java = page(members = "static class Nested extends ExamplePage {}")
        html("src/main/resources/example/ExamplePage.html")
        val nested = java.classes.single().innerClasses.single()
        assertThrows(AnalysisUnavailable::class.java) {
            WicketComponentFiles.markupPlan(project, java.virtualFile, nested.textOffset)
        }
    }

    fun testNewPanelCreatesPairInChosenResourceRootAndUndoesBothFiles() {
        val source = requireNotNull(PsiManager.getInstance(project).findDirectory(dir("src/main/java/example")))
        val extraRoot = dir("extra-resources")
        PsiTestUtil.addSourceRoot(module, extraRoot, JavaResourceRootType.RESOURCE)
        val plan = WicketComponentFiles.componentPlan(source, "ProfilePanel", WicketComponentFiles.Kind.PANEL)
        assertEquals(2, plan.destinations.size)
        val destination = plan.destinations.single { it.root == extraRoot }
        val java = WicketComponentFiles.createComponent(project, plan, destination) as PsiJavaFile
        val panel = java.classes.single()
        assertEquals("example.ProfilePanel", panel.qualifiedName)
        assertEquals("org.apache.wicket.markup.html.panel.Panel", panel.superClass?.qualifiedName)
        assertEquals("java.lang.String", panel.constructors.single().parameterList.parameters.single().type.canonicalText)
        val markup = requireNotNull(extraRoot.findFileByRelativePath("example/ProfilePanel.html"))
        assertEquals(markup.url, WicketPageResolver().resolve(panel).markupUrl)

        val previous = TestDialogManager.setTestDialog(TestDialog.OK)
        try {
            UndoManager.getInstance(project).undo(null)
        } finally {
            TestDialogManager.setTestDialog(previous)
        }
        assertNull(source.findFile("ProfilePanel.java"))
        assertNull(extraRoot.findFileByRelativePath("example/ProfilePanel.html"))
    }

    fun testNewPageFallsBackToAdjacentMarkupWithoutResourceRoots() {
        ModuleRootModificationUtil.updateModel(module) { model ->
            model.contentEntries.forEach { entry ->
                entry.sourceFolders.filter { it.rootType == JavaResourceRootType.RESOURCE }.forEach { entry.removeSourceFolder(it) }
            }
        }
        val source = requireNotNull(PsiManager.getInstance(project).findDirectory(dir("src/main/java/example")))
        val plan = WicketComponentFiles.componentPlan(source, "HomePage", WicketComponentFiles.Kind.PAGE)
        val java = WicketComponentFiles.createComponent(project, plan, plan.destinations.single()) as PsiJavaFile
        val page = java.classes.single()
        assertEquals("org.apache.wicket.markup.html.WebPage", page.superClass?.qualifiedName)
        val markup = requireNotNull(source.findFile("HomePage.html"))
        assertTrue(markup.text.contains("<head>"))
        assertEquals(markup.virtualFile.url, WicketPageResolver().resolve(page).markupUrl)
    }

    fun testNewComponentRechecksForMarkupCreatedAfterPlanning() {
        val source = requireNotNull(PsiManager.getInstance(project).findDirectory(dir("src/main/java/example")))
        val plan = WicketComponentFiles.componentPlan(source, "HomePage", WicketComponentFiles.Kind.PAGE)
        val markup = html("src/main/java/example/HomePage.html")
        val original = markup.text
        assertThrows(AnalysisUnavailable::class.java) {
            WicketComponentFiles.createComponent(project, plan, plan.destinations.single())
        }
        assertNull(source.findFile("HomePage.java"))
        assertEquals(original, markup.text)
        assertNull(dir("src/main/resources").findChild("example"))
    }

    private fun dir(path: String) = myFixture.tempDirFixture.findOrCreateDir(path)

    private fun html(path: String) = myFixture.addFileToProject(path, "<html><body></body></html>")

    private fun page(members: String = "") = myFixture.addFileToProject(
        "src/main/java/example/ExamplePage.java",
        "package example; class ExamplePage extends org.apache.wicket.markup.html.WebPage { $members }",
    ) as PsiJavaFile

    companion object {
        private val DESCRIPTOR = object : LightProjectDescriptor() {
            override fun getSdk() = Java25ProjectDescriptor.getSdk()
        }
    }
}
