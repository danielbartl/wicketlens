package dev.wicketlens

import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.refactoring.rename.RenameProcessor
import dev.wicketlens.editor.*

class WicketIdEditingTest : WicketProjectPsiTestCase() {
    override fun tearDown() {
        try { FileEditorManager.getInstance(project).openFiles.forEach { FileEditorManager.getInstance(project).closeFile(it) } }
        finally { super.tearDown() }
    }

    fun testContributedReferencesNavigateBothDirectionsAtIdValues() {
        val java = pageFile()
        val html = companion()
        val literal = java.idValue("title")
        val attribute = html.idValue("title")
        val report = WicketEditorAnalysis.pages(java).single().report
        assertNotNull("Java anchor: $report", WicketIdPsi.javaLiteral(project, report.nodes.single { it.wicketId == "title" }))
        assertNotNull("HTML anchor: $report", WicketIdPsi.markupValue(project, report.markup!!.elements.single { it.wicketId == "title" }))
        assertNotNull("Pair: $report", WicketIdPsi.pair(literal))
        assertEquals(attribute, reference(literal).resolve())
        assertEquals(literal, reference(attribute).resolve())
        assertEquals("title", reference(literal).rangeInElement.substring(literal.text))
        assertEquals("title", reference(attribute).rangeInElement.substring(attribute.text))
    }

    fun testUnpairedAndDynamicIdsCannotNavigateOrRename() {
        val java = pageFile(body = "add(new Label(\"javaOnly\"), new Label(System.getProperty(\"config\")));")
        val html = companion(html = "<span wicket:id=\"htmlOnly\"></span>")
        assertNull(reference(java.idValue("javaOnly")).resolve())
        assertNull(reference(html.idValue("htmlOnly")).resolve())
        assertNull(WicketIdRenameTarget.at(java, java.text.indexOf("javaOnly")))
        assertNull(WicketIdRenameTarget.at(html, html.text.indexOf("htmlOnly")))
        assertTrue(java.idValue("config").references.none { it is WicketIdReference })
    }

    fun testNativeRenameFromJavaUpdatesOnlyThePairedIds() {
        val java = pageFile(body = "add(new Label(\"title\", \"title\"));")
        val html = companion(html = "<!-- title --><span wicket:id=\"title\">title</span>")
        rename(java, "title", "heading")
        assertTrue(java.text.contains("new Label(\"heading\", \"title\")"))
        assertEquals("<!-- title --><span wicket:id=\"heading\">title</span>", html.text)
        assertEquals(html.idValue("heading"), reference(java.idValue("heading")).resolve())
    }

    fun testNativeRenameFromHtmlAcceptsNonJavaIdentifierNames() {
        val java = pageFile()
        val html = companion()
        rename(html, "title", "page-heading.2")
        assertTrue(java.text.contains("\"page-heading.2\""))
        assertTrue(html.text.contains("wicket:id=\"page-heading.2\""))
    }

    fun testRenameCannotExpandIntoUnrelatedTextEvenWhenTextSearchIsRequested() {
        val java = pageFile(body = "add(new Label(\"title\", \"title\")); // title\n")
        val html = companion(html = "<!-- title --><span wicket:id=\"title\">title</span>")
        val target = WicketIdRenameTarget.at(java, java.text.indexOf("\"title\"") + 1)!!
        RenameProcessor(project, target, "heading", true, true).run()
        PsiDocumentManager.getInstance(project).commitAllDocuments()
        assertTrue(java.text.contains("Label(\"heading\", \"title\")"))
        assertTrue(java.text.contains("// title"))
        assertEquals("<!-- title --><span wicket:id=\"heading\">title</span>", html.text)
    }

    fun testNativePreviewContainsTheHtmlUsageAndRenameUndoesBothFilesTogether() {
        val java = pageFile()
        val html = companion()
        myFixture.configureFromExistingVirtualFile(java.virtualFile)
        val before = java.text to html.text
        val target = WicketIdRenameTarget.at(java, java.text.indexOf("title"))!!
        val processor = RenameProcessor(project, target, "heading", false, false)
        val usages = processor.findUsages()
        assertEquals(1, usages.size)
        assertEquals(html.idValue("title"), usages.single().element)
        assertEquals("Wicket ID", com.intellij.usageView.UsageViewUtil.getType(target))
        processor.run()
        assertTrue(java.text.contains("\"heading\""))
        assertTrue(html.text.contains("\"heading\""))
        val previousDialog = com.intellij.openapi.ui.TestDialogManager.setTestDialog(com.intellij.openapi.ui.TestDialog.OK)
        try { myFixture.performEditorAction(com.intellij.openapi.actionSystem.IdeActions.ACTION_UNDO) }
        finally { com.intellij.openapi.ui.TestDialogManager.setTestDialog(previousDialog) }
        PsiDocumentManager.getInstance(project).commitAllDocuments()
        assertEquals(before, java.text to html.text)
    }

    fun testNativeGoToDeclarationOpensTheCounterpartEditorInBothDirections() {
        val java = pageFile()
        val html = companion()
        for ((from, to) in listOf(java to html, html to java)) {
            myFixture.configureFromExistingVirtualFile(from.virtualFile)
            myFixture.editor.caretModel.moveToOffset(from.text.indexOf("\"title\"") + 2)
            myFixture.performEditorAction(com.intellij.openapi.actionSystem.IdeActions.ACTION_GOTO_DECLARATION)
            com.intellij.testFramework.PlatformTestUtil.waitWithEventsDispatching("Counterpart should open", {
                FileEditorManager.getInstance(project).selectedFiles.contains(to.virtualFile)
            }, 10)
            assertEquals(to.virtualFile, FileEditorManager.getInstance(project).selectedFiles.single())
        }
    }

    fun testRenameEscapesBothLanguagesAndKeepsTheDecodedPair() {
        val java = pageFile()
        val html = companion()
        val newId = "heading\"&<>\\'"
        rename(java, "title", newId)
        val report = WicketEditorAnalysis.pages(java).single().report
        assertEquals(newId, report.nodes.single { it.wicketId != null }.wicketId)
        assertEquals(newId, report.markup!!.elements.single { it.hasWicketId }.wicketId)
        assertEquals(1, report.matching!!.findings.count { it.code == dev.wicketlens.model.DiagnosticCode.MATCHED })
    }

    fun testRepeatedIdsUnderDifferentParentsRenameIndependently() {
        val (java, html) = repeated()
        rename(java, "name", "leftName")
        assertEquals(1, "\"leftName\"".toRegex().findAll(java.text).count())
        assertEquals(1, "\"name\"".toRegex().findAll(java.text).count())
        assertEquals(1, "\"leftName\"".toRegex().findAll(html.text).count())
        assertEquals(1, "\"name\"".toRegex().findAll(html.text).count())
        assertEquals(html.idValue("name"), reference(java.idValue("name")).resolve())
    }

    fun testRenameDetectsJavaAndMarkupSiblingConflicts() {
        val java = pageFile(body = "add(new Label(\"title\"), new Label(\"javaOnly\"));")
        companion(html = "<span wicket:id=\"title\"></span><span wicket:id=\"htmlOnly\"></span>")
        val target = WicketIdRenameTarget.at(java, java.text.indexOf("title"))!!
        assertNotNull(target.conflict("javaOnly"))
        assertNotNull(target.conflict("htmlOnly"))
        assertNull(target.conflict("other"))
        for (id in listOf("", "a:b", "a~b", "a\nb")) assertNotNull(target.conflict(id))
    }

    fun testStaleRenameTargetRevalidatesBeforeEitherWrite() {
        val java = pageFile()
        val html = companion()
        val target = WicketIdRenameTarget.at(java, java.text.indexOf("title"))!!
        edit(html, html.text.replace("title", "different"))
        val before = java.text to html.text
        assertNotNull(target.conflict("newId"))
        try {
            WriteCommandAction.runWriteCommandAction(project) { target.setName("newId") }
            fail("A stale pair must not be renamed")
        } catch (_: com.intellij.util.IncorrectOperationException) { }
        assertEquals(before, java.text to html.text)
    }

    fun testRenameHandlerIsAvailableOnBothIdsButNotDisplayText() {
        val java = pageFile(body = "add(new Label(\"title\", \"display\"));")
        val html = companion()
        for ((file, id, available) in listOf(Triple(java, "title", true), Triple(html, "title", true), Triple(java, "display", false))) {
            myFixture.configureFromExistingVirtualFile(file.virtualFile)
            myFixture.editor.caretModel.moveToOffset(file.text.indexOf("\"$id\"") + 2)
            val context = DataContext { key -> when (key) {
                CommonDataKeys.EDITOR.name -> myFixture.editor
                CommonDataKeys.PSI_FILE.name -> file
                CommonDataKeys.PROJECT.name -> project
                else -> null
            } }
            assertEquals(available, WicketIdRenameHandler().isAvailableOnDataContext(context))
        }
    }

    fun testDuplicateIdsAndCustomInheritedScopeCannotRename() {
        val java = pageFile(body = "add(new Label(\"title\"), new Label(\"title\"));")
        val html = companion()
        assertNull(WicketIdRenameTarget.at(java, java.text.indexOf("title")))
        assertNull(WicketIdRenameTarget.at(html, html.text.indexOf("title")))
        edit(java, java.text.replace("add(new Label(\"title\"), new Label(\"title\"));", "add(new Label(\"title\")); removeAll();"))
        assertNull(WicketIdRenameTarget.at(java, java.text.indexOf("title")))
    }

    fun testFieldNavigationAndRenameUseTheOriginalConstruction() {
        val java = myFixture.addFileToProject("src/main/java/example/ExamplePage.java", """
            package example; public class ExamplePage extends org.apache.wicket.markup.html.WebPage {
                private final org.apache.wicket.markup.html.basic.Label title = new org.apache.wicket.markup.html.basic.Label("title");
                public ExamplePage() { add(title); }
            }
        """.trimIndent())
        val html = companion()
        assertEquals(java.idValue("title"), reference(html.idValue("title")).resolve())
        rename(html, "title", "heading")
        assertTrue(java.text.contains("Label(\"heading\")"))
        assertTrue(java.text.contains("add(title)"))
    }

    fun testUncommittedCounterpartDoesNotResolveAStaleReference() {
        val java = pageFile()
        val html = companion()
        assertNotNull(reference(java.idValue("title")).resolve())
        val manager = PsiDocumentManager.getInstance(project)
        (manager as com.intellij.psi.impl.PsiDocumentManagerEx).disableBackgroundCommit(testRootDisposable)
        WriteCommandAction.runWriteCommandAction(project) { manager.getDocument(html)!!.setText("<span wicket:id=\"other\"></span>") }
        try { assertNull(reference(java.idValue("title")).resolve()) }
        finally { manager.commitAllDocuments() }
    }

    private fun rename(file: PsiFile, id: String, newName: String) {
        myFixture.configureFromExistingVirtualFile(file.virtualFile)
        val target = WicketIdRenameTarget.at(file, file.text.indexOf("\"$id\"") + 1)!!
        RenameProcessor(project, target, newName, false, false).run()
        PsiDocumentManager.getInstance(project).commitAllDocuments()
    }

    private fun repeated(): Pair<PsiFile, PsiFile> {
        val java = pageFile(body = """
            org.apache.wicket.markup.html.form.Form<?> left = new org.apache.wicket.markup.html.form.Form<>("left");
            org.apache.wicket.markup.html.form.Form<?> right = new org.apache.wicket.markup.html.form.Form<>("right");
            left.add(new Label("name")); right.add(new Label("name")); add(left, right);
        """.trimIndent())
        val html = companion(html = "<form wicket:id=\"left\"><span wicket:id=\"name\"></span></form><form wicket:id=\"right\"><span wicket:id=\"name\"></span></form>")
        return java to html
    }

    private fun reference(element: PsiElement) = element.references.filterIsInstance<WicketIdReference>().single()

    private fun edit(file: PsiFile, text: String) {
        WriteCommandAction.runWriteCommandAction(project) { PsiDocumentManager.getInstance(project).getDocument(file)!!.setText(text) }
        PsiDocumentManager.getInstance(project).commitAllDocuments()
    }
}

internal fun PsiFile.idValue(id: String): PsiElement = WicketIdPsi.valueAt(this, text.indexOf("\"$id\"") + 1)!!
