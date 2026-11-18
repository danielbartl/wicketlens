@file:Suppress("UnstableApiUsage")

package dev.wicketlens

import com.intellij.codeInsight.CodeInsightSettings
import com.intellij.codeInsight.lookup.LookupElementPresentation
import com.intellij.codeInsight.navigation.actions.GotoDeclarationAction
import com.intellij.codeInsight.navigation.impl.NavigationActionResult
import com.intellij.codeInsight.navigation.impl.fromGTDProviders
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.backend.navigation.impl.SourceNavigationRequest
import com.intellij.platform.backend.presentation.TargetPresentation
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import dev.wicketlens.editor.WicketIdCompletionContributor
import dev.wicketlens.editor.WicketIdPsi
import dev.wicketlens.editor.WicketIdRenameTarget
import dev.wicketlens.mcp.WicketPageAnalysisRequest
import com.intellij.util.concurrency.AppExecutorUtil

class WicketCompanionIdsTest : WicketProjectPsiTestCase() {
    fun testAmbiguousNavigationUsesTheNativeChooserWithSourceContext() {
        val java = pageFile(body = """
            var table = new org.apache.wicket.markup.html.WebMarkupContainer("people");
            table.add(new org.apache.wicket.markup.html.list.ListView<String>("rows", java.util.List.of("a")) {
                protected void populateItem(org.apache.wicket.markup.html.list.ListItem<String> item) {
                    item.add(new Label("email", "row"));
                }
            });
            var fields = new org.apache.wicket.markup.html.panel.Fragment("editor", "editFields", this);
            fields.add(new Label("email", "dialog"));
            add(table, fields);
            java.util.List.of("email");
            removeAll();
        """.trimIndent())
        val html = companion(html = """
            <div wicket:id="people"><div wicket:id="rows"><span wicket:id="email"></span></div></div>
            <div wicket:id="editor"></div>
            <wicket:fragment wicket:id="editFields"><b wicket:id="email"></b></wicket:fragment>
        """.trimIndent())
        val unrelated = pageFile("OtherPage", """add(new Label("email"));""")
        companion("OtherPage", """<i wicket:id="email"></i>""")

        fun presentations(file: PsiFile, snippet: String, expectedFile: PsiFile): List<TargetPresentation> {
            myFixture.configureFromExistingVirtualFile(file.virtualFile)
            val caret = offset(file, snippet)
            val editor = myFixture.editor
            editor.caretModel.moveToOffset(caret)
            val expected = expectedFile.virtualFile
            val rows = AppExecutorUtil.getAppExecutorService().submit<List<ChooserRow>> {
                runReadAction {
                    // Use the chooser data computed by the Go to Declaration action for registered handlers.
                    val result = fromGTDProviders(project, editor, caret)?.result() as NavigationActionResult.MultipleTargets
                    result.targets.map { target ->
                        val request = target.requestor.navigationRequest() as SourceNavigationRequest
                        ChooserRow(target.presentation, request.file, requireNotNull(request.offsetMarker).startOffset)
                    }
                }
            }.get()
            assertEquals(listOf(expected, expected), rows.map { it.file })
            assertTrue(rows.none { it.file == unrelated.virtualFile })
            assertEquals(2, rows.map { it.offset }.toSet().size)
            rows.forEach { row ->
                val line = expectedFile.text.take(row.offset).count { it == '\n' } + 1
                assertTrue(row.presentation.containerText.orEmpty(), row.presentation.containerText.orEmpty().endsWith("line $line"))
            }
            return rows.map { it.presentation }
        }
        val javaTargets = presentations(html, """<span wicket:id="e<caret>mail">""", java)
        assertEquals(setOf("""new Label("email", "row")""", """new Label("email", "dialog")"""), javaTargets.map { it.presentableText }.toSet())
        assertTrue(javaTargets.any { it.containerText.orEmpty().contains("rows / populateItem") })
        assertTrue(javaTargets.any { it.containerText.orEmpty().contains("fragment editFields") })
        val htmlTargets = presentations(java, """Label("e<caret>mail", "row")""", html)
        assertEquals(setOf("""<span wicket:id="email">""", """<b wicket:id="email">"""), htmlTargets.map { it.presentableText }.toSet())
        assertTrue(htmlTargets.any { it.containerText.orEmpty().contains("people / rows") })
        assertTrue(htmlTargets.any { it.containerText.orEmpty().contains("editFields") })
        val literal = WicketIdPsi.valueAt(java, offset(java, """Label("e<caret>mail", "row")"""))!!
        assertEmpty(WicketIdPsi.pairs(literal))
        assertNull(WicketIdRenameTarget.at(java, literal.textOffset + 1))
    }

    private data class ChooserRow(val presentation: TargetPresentation, val file: VirtualFile, val offset: Int)

    fun testPossibleCompletionAndUniqueNavigationAreLocalAndFresh() {
        val java = pageFile(body = """add(new Label("title"), new Label("occupied")); removeAll();""")
        val html = companion(html = """<span wicket:id="title"></span><b wicket:id="occupied"></b>""")
        pageFile("OtherPage", """add(new Label("otherPageOnly"));""")
        companion("OtherPage", """<b wicket:id="otherPageOnly"></b>""")
        val query = WicketPageAnalysisRequest()
        val before = query.render(query.capture(project, "example.ExamplePage"))
        assertEquals(html, goto(java, """Label("t<caret>itle")""").single().containingFile)
        assertEquals(java, goto(html, "wicket:id=\"t<caret>itle\"").single().containingFile)
        assertEquals(before, query.render(query.capture(project, "example.ExamplePage")))

        edit(java, "\"title\"", "\"tiSuffix\"")
        assertPopup(java, """Label("ti<caret>Suffix")""", "title")
        edit(java, "\"tiSuffix\"", "\"title\"")
        edit(html, "\"title\"", "\"\"")
        assertPopup(html, "wicket:id=\"<caret>\"", "title")
        edit(java, "\"title\"", "\"changed\"")
        assertPopup(html, "wicket:id=\"<caret>\"", "changed")
        assertTrue(html.text.contains("wicket:id=\"\""))
    }

    fun testAmbiguousCompanionsWithholdFallbackInBothEditors() {
        val java = pageFile(body = """add(new Label("title")); removeAll();""")
        val html = companion()
        myFixture.addFileToProject("src/main/java/example/ExamplePage.html", """<b wicket:id="title"></b>""")
        assertEmpty(goto(java, """Label("t<caret>itle")"""))
        assertEmpty(goto(html, "wicket:id=\"t<caret>itle\""))
    }

    fun testPanelFallbackSkipsPreviewMarkupAndOpensItsCompanion() {
        val java = myFixture.addFileToProject("src/main/java/example/ExamplePanel.java", """
            package example;
            public class ExamplePanel extends org.apache.wicket.markup.html.panel.Panel {
                public ExamplePanel(String id) {
                    super(id);
                    add(new org.apache.wicket.markup.html.basic.Label("title"));
                    removeAll();
                }
            }
        """.trimIndent())
        val html = companion("ExamplePanel", """
            <i wicket:id="title"></i>
            <wicket:panel><b wicket:id="title"></b></wicket:panel>
        """.trimIndent())
        val target = goto(java, """Label("t<caret>itle")""").single()
        assertEquals(html, target.containingFile)
        assertEquals(html.text.lastIndexOf("\"title\""), target.textRange.startOffset)
        myFixture.performEditorAction(com.intellij.openapi.actionSystem.IdeActions.ACTION_GOTO_DECLARATION)
        val editors = FileEditorManager.getInstance(project)
        com.intellij.testFramework.PlatformTestUtil.waitWithEventsDispatching("Panel companion should open", {
            editors.selectedFiles.contains(html.virtualFile)
        }, 10)
    }

    private fun assertPopup(file: PsiFile, snippet: String, expected: String) {
        val settings = CodeInsightSettings.getInstance()
        val original = settings.AUTOCOMPLETE_ON_CODE_COMPLETION
        settings.AUTOCOMPLETE_ON_CODE_COMPLETION = false
        try {
            myFixture.configureFromExistingVirtualFile(file.virtualFile)
            myFixture.editor.caretModel.moveToOffset(offset(file, snippet))
            val items = myFixture.completeBasic().orEmpty()
            assertEquals(listOf(expected), items.map { it.lookupString })
            val item = items.single()
            assertEquals(true, item.getUserData(WicketIdCompletionContributor.CANDIDATE))
            val presentation = LookupElementPresentation()
            item.renderElement(presentation)
            assertTrue(presentation.tailText.orEmpty().contains("possible"))
        } finally {
            myFixture.lookup?.hideLookup(false)
            settings.AUTOCOMPLETE_ON_CODE_COMPLETION = original
        }
    }

    private fun goto(file: PsiFile, snippet: String): List<com.intellij.psi.PsiElement> {
        myFixture.configureFromExistingVirtualFile(file.virtualFile)
        val offset = offset(file, snippet)
        myFixture.editor.caretModel.moveToOffset(offset)
        return GotoDeclarationAction.findAllTargetElements(project, myFixture.editor, offset).toList()
    }

    private fun offset(file: PsiFile, snippet: String): Int {
        val start = file.text.indexOf(snippet.replace("<caret>", ""))
        assertTrue(file.text, start >= 0)
        return start + snippet.indexOf("<caret>")
    }

    private fun edit(file: PsiFile, before: String, after: String) {
        val documents = PsiDocumentManager.getInstance(project)
        val document = documents.getDocument(file)!!
        WriteCommandAction.runWriteCommandAction(project) { document.setText(file.text.replace(before, after)) }
        documents.commitAllDocuments()
        FileDocumentManager.getInstance().saveDocument(document)
    }

    override fun tearDown() {
        try {
            val editors = FileEditorManager.getInstance(project)
            editors.openFiles.forEach(editors::closeFile)
        } finally {
            super.tearDown()
        }
    }
}
