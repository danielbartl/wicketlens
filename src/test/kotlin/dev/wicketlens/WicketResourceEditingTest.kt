package dev.wicketlens

import com.intellij.lang.properties.psi.PropertiesFile
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.command.undo.UndoManager
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.ui.TestDialog
import com.intellij.openapi.ui.TestDialogManager
import com.intellij.openapi.ui.TestInputDialog
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiJavaFile
import com.intellij.refactoring.rename.RenameProcessor
import com.intellij.refactoring.rename.naming.AutomaticRenamer
import com.intellij.refactoring.rename.naming.AutomaticRenamerFactory
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.PsiTestUtil
import dev.wicketlens.analysis.AnalysisUnavailable
import dev.wicketlens.editor.WicketCompanionFileRenamerFactory
import dev.wicketlens.localization.WicketMessageExtraction
import org.jetbrains.jps.model.java.JavaResourceRootType
import org.jetbrains.jps.model.java.JavaSourceRootType

class WicketResourceEditingTest : WicketJavaPsiTestCase() {
    override fun getProjectDescriptor(): LightProjectDescriptor = DESCRIPTOR

    override fun setUp() {
        super.setUp()
        ModuleRootModificationUtil.updateModel(module) { model ->
            model.contentEntries.forEach { entry -> entry.sourceFolders.forEach { entry.removeSourceFolder(it) } }
        }
        PsiTestUtil.addSourceRoot(module, dir("src/main/java"), JavaSourceRootType.SOURCE)
        PsiTestUtil.addSourceRoot(module, dir("src/main/resources"), JavaResourceRootType.RESOURCE)
        PsiTestUtil.addSourceRoot(module, dir("src/test/resources"), JavaResourceRootType.TEST_RESOURCE)
    }

    fun testNativeClassRenameMovesMarkupAndBundlesIncludingNestedAndVariantFiles() {
        val java = page(members = """
            static class Card extends org.apache.wicket.markup.html.panel.Panel {
                Card(String id) { super(id); }
            }
        """.trimIndent())
        resource("Messages.html", "<p>Welcome</p>")
        resource("Messages_fr.html", "<p>Bonjour</p>")
        resource("Messages.properties", "welcome=Welcome\n")
        resource("Messages_fr.properties", "welcome=Bonjour\n")
        resource("Messages\$Card.html", "<wicket:panel></wicket:panel>")
        resource("Messages\$Card_en.properties", "welcome=Card\n")
        val testOnly = myFixture.addFileToProject("src/test/resources/example/Messages.properties", "key=test\n")
        val type = java.classes.single()
        val factory = AutomaticRenamerFactory.EP_NAME.extensionList.filterIsInstance<WicketCompanionFileRenamerFactory>().single()
        assertTrue(factory.isApplicable(type))
        val processor = object : RenameProcessor(project, type, "WelcomePage", false, false) {
            override fun showAutomaticRenamingDialog(renamer: AutomaticRenamer) = true
        }
        processor.addRenamerFactory(factory)
        processor.run()

        val root = dir("src/main/resources/example")
        for (name in listOf("WelcomePage.html", "WelcomePage_fr.html", "WelcomePage.properties", "WelcomePage_fr.properties",
            "WelcomePage\$Card.html", "WelcomePage\$Card_en.properties")) {
            assertNotNull(name, root.findChild(name))
        }
        assertNull(root.findChild("Messages.html"))
        assertNull(root.findChild("Messages_fr.properties"))
        assertEquals("WelcomePage.java", java.virtualFile.name)
        assertEquals("Messages.properties", testOnly.virtualFile.name)
    }

    fun testRenameWithholdsACompanionFamilyWhenTheDestinationAlreadyHasABundle() {
        val type = page().classes.single()
        resource("Messages.html", "<p>Welcome</p>")
        resource("Other_fr.properties", "welcome=Already exists\n")
        val factory = WicketCompanionFileRenamerFactory()
        assertEmpty(factory.createRenamer(type, "Other", mutableListOf()).renames.keys)
        assertTrue(factory.createRenamer(type, "Welcome", mutableListOf()).hasAnythingToRename())
    }

    fun testRenameWithholdsMarkupThatCanAlsoBeAnExactClassCompanion() {
        val type = page().classes.single()
        myFixture.addFileToProject("src/main/java/example/Messages_fr.java",
            "package example; class Messages_fr extends org.apache.wicket.markup.html.WebPage {}")
        resource("Messages_fr.html", "<p>Bonjour</p>")
        assertFalse(WicketCompanionFileRenamerFactory().isApplicable(type))
    }

    fun testExtractsHtmlIntoDefaultBundleAndUndoesBothEdits() {
        assertNotNull(ActionManager.getInstance().getAction("WicketLens.ExtractMessage"))
        page()
        val markup = resource("Messages.html", """<html xmlns:wicket="http://wicket.apache.org"><body><p>Hello world!</p></body></html>""")
        val bundle = resource("Messages.properties", "greeting=Welcome\n")
        val original = markup.text
        val plan = plan(markup, "Hello world!")
        WicketMessageExtraction.extract(project, plan, plan.destinations.single(), "home.welcome")
        assertTrue(sourceText(markup).contains("""<wicket:message key="home.welcome" escape="true">Hello world!</wicket:message>"""))
        assertEquals("Hello world!", (bundle as PropertiesFile).findPropertyByKey("home.welcome")?.unescapedValue)
        assertEquals("Welcome", bundle.findPropertyByKey("greeting")?.unescapedValue)

        val previous = TestDialogManager.setTestDialog(TestDialog.OK)
        try {
            UndoManager.getInstance(project).undo(null)
        } finally {
            TestDialogManager.setTestDialog(previous)
        }
        assertEquals(original, sourceText(markup))
        assertEquals("greeting=Welcome\n", sourceText(bundle.containingFile))
        PsiDocumentManager.getInstance(project).commitAllDocuments()
        assertNull(bundle.findPropertyByKey("home.welcome"))
    }

    fun testExtractsJavaLabelTextAndCreatesBundleWithEscapedValue() {
        val java = page(body = """add(new org.apache.wicket.markup.html.basic.Label("heading", "First\nSecond"));""")
        val original = java.text
        val plan = plan(java, """First\nSecond""")
        val bundle = WicketMessageExtraction.extract(project, plan, plan.destinations.single(), "home.heading") as PropertiesFile
        assertEquals(dir("src/main/resources/example"), bundle.virtualFile.parent)
        assertEquals("Messages.properties", bundle.virtualFile.name)
        assertEquals("First\nSecond", bundle.findPropertyByKey("home.heading")?.unescapedValue)
        assertTrue(sourceText(java).contains("""Label("heading", new org.apache.wicket.model.ResourceModel("home.heading", "First\nSecond"))"""))
        val previous = TestDialogManager.setTestDialog(TestDialog.OK)
        try {
            UndoManager.getInstance(project).undo(null)
        } finally {
            TestDialogManager.setTestDialog(previous)
        }
        assertEquals(original, sourceText(java))
        assertNull(dir("src/main/resources/example").findChild("Messages.properties"))
    }

    fun testExtractActionUsesTheEditorSelection() {
        page()
        val markup = resource("Messages.html", "<p>Visible text</p>")
        val bundle = resource("Messages.properties", "greeting=Welcome\n")
        myFixture.configureFromExistingVirtualFile(markup.virtualFile)
        val selected = "Visible text"
        val start = markup.text.indexOf(selected)
        myFixture.editor.selectionModel.setSelection(start, start + selected.length)
        val previous = TestDialogManager.setTestInputDialog(TestInputDialog { "home.visible" })
        try {
            myFixture.performEditorAction("WicketLens.ExtractMessage")
            PlatformTestUtil.waitWithEventsDispatching("The selected text should be extracted", {
                sourceText(markup).contains("key=\"home.visible\"")
            }, 10)
        } finally {
            TestDialogManager.setTestInputDialog(previous)
        }
        assertEquals(selected, (bundle as PropertiesFile).findPropertyByKey("home.visible")?.unescapedValue)
    }

    fun testExtractionPreservesAKeyThatAlreadyExistsInAVariant() {
        page()
        val markup = resource("Messages.html", "<p>Hello world!</p>")
        val variant = resource("Messages_fr.properties", "greeting=Bonjour\n")
        val plan = plan(markup, "Hello world!")
        assertThrows(AnalysisUnavailable::class.java) {
            WicketMessageExtraction.extract(project, plan, plan.destinations.single(), "greeting")
        }
        assertNull(dir("src/main/resources/example").findChild("Messages.properties"))
        assertEquals("<p>Hello world!</p>", sourceText(markup))
        assertEquals("greeting=Bonjour\n", variant.text)
    }

    fun testExtractionRejectsStaleSelectionsAndJavaComponentIds() {
        val java = page(body = """add(new org.apache.wicket.markup.html.basic.Label("heading", "Hello world!"));""")
        assertThrows(AnalysisUnavailable::class.java) { plan(java, "heading") }
        val plan = plan(java, "Hello world!")
        val document = requireNotNull(PsiDocumentManager.getInstance(project).getDocument(java))
        WriteCommandAction.runWriteCommandAction(project) { document.insertString(0, "// edit\n") }
        assertThrows(AnalysisUnavailable::class.java) {
            WicketMessageExtraction.extract(project, plan, plan.destinations.single(), "greeting")
        }
        assertNull(dir("src/main/resources/example").findChild("Messages.properties"))
    }

    private fun sourceText(file: PsiFile) = PsiDocumentManager.getInstance(project).getDocument(file)?.text ?: file.text

    private fun plan(file: PsiFile, selection: String): WicketMessageExtraction.Plan {
        val start = file.text.indexOf(selection)
        check(start >= 0)
        return WicketMessageExtraction.prepare(project, file.virtualFile, TextRange(start, start + selection.length))
    }

    private fun page(body: String = "", members: String = "") = myFixture.addFileToProject(
        "src/main/java/example/Messages.java",
        """
        package example;
        public class Messages extends org.apache.wicket.markup.html.WebPage {
            public Messages() { $body }
            $members
        }
        """.trimIndent(),
    ) as PsiJavaFile

    private fun resource(name: String, text: String) = myFixture.addFileToProject("src/main/resources/example/$name", text)
    private fun dir(path: String) = myFixture.tempDirFixture.findOrCreateDir(path)

    companion object {
        private val DESCRIPTOR = object : LightProjectDescriptor() {
            override fun getSdk() = Java25ProjectDescriptor.getSdk()
        }
    }
}
