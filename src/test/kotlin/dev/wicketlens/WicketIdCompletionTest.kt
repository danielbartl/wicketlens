package dev.wicketlens

import com.intellij.codeInsight.CodeInsightSettings
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.psi.PsiFile
import dev.wicketlens.editor.WicketIdCompletionContributor

class WicketIdCompletionTest : WicketProjectPsiTestCase() {
    private var autoComplete = true

    override fun setUp() {
        super.setUp()
        autoComplete = CodeInsightSettings.getInstance().AUTOCOMPLETE_ON_CODE_COMPLETION
        CodeInsightSettings.getInstance().AUTOCOMPLETE_ON_CODE_COMPLETION = false
    }

    override fun tearDown() {
        try {
            CodeInsightSettings.getInstance().AUTOCOMPLETE_ON_CODE_COMPLETION = autoComplete
            FileEditorManager.getInstance(project).openFiles.forEach { FileEditorManager.getInstance(project).closeFile(it) }
        } finally { super.tearDown() }
    }

    fun testJavaCompletionSuggestsMarkupIdsInsideAnEmptyLiteral() {
        val java = pageFile(body = "add(new Label(\"\"));")
        companion(html = "<span wicket:id=\"title\"></span><span wicket:id=\"subtitle\"></span>")
        assertEquals(setOf("title", "subtitle"), complete(java, "\"<caret>\"").map { it.lookupString }.toSet())
    }

    fun testHtmlCompletionSuggestsJavaIdsInsideAnEmptyValue() {
        pageFile(body = "add(new Label(\"title\"), new Label(\"subtitle\"));")
        val html = companion(html = "<span wicket:id=\"\"></span>")
        assertEquals(setOf("title", "subtitle"), complete(html, "wicket:id=\"<caret>\"").map { it.lookupString }.toSet())
    }

    fun testJavaCompletionFiltersByPrefixAndReplacesTheWholeExistingValue() {
        val java = pageFile(body = "add(new Label(\"heSuffix\"));")
        companion(html = "<span wicket:id=\"heading\"></span><span wicket:id=\"subtitle\"></span>")
        val items = complete(java, "\"he<caret>Suffix\"")
        assertEquals(listOf("heading"), items.map { it.lookupString })
        myFixture.lookup.currentItem = items.single()
        myFixture.finishLookup('\n')
        assertTrue(java.text.contains("Label(\"heading\")"))
        assertFalse(java.text.contains("Suffix"))
    }

    fun testHtmlCompletionRespectsTheParentAndExcludesAlreadyUsedSiblings() {
        pageFile(body = """
            org.apache.wicket.markup.html.form.Form<?> form = new org.apache.wicket.markup.html.form.Form<>("form");
            form.add(new Label("name"), new Label("email")); add(form); add(new Label("outside"));
        """.trimIndent())
        val html = companion(html = "<form wicket:id=\"form\"><span wicket:id=\"name\"></span><input wicket:id=\"\"/></form>")
        assertEquals(listOf("email"), complete(html, "<input wicket:id=\"<caret>\"").map { it.lookupString })
    }

    fun testJavaCompletionRespectsTheParentAndExcludesAlreadyUsedSiblings() {
        val java = pageFile(body = """
            org.apache.wicket.markup.html.form.Form<?> form = new org.apache.wicket.markup.html.form.Form<>("form");
            form.add(new Label("name"), new Label("")); add(form);
        """.trimIndent())
        companion(html = "<form wicket:id=\"form\"><span wicket:id=\"name\"></span><span wicket:id=\"email\"></span></form><span wicket:id=\"outside\"></span>")
        assertEquals(listOf("email"), complete(java, "Label(\"<caret>\"").map { it.lookupString })
    }

    fun testDisplayStringsAndUnrelatedAttributesReceiveNoWicketCompletion() {
        val java = pageFile(body = "add(new Label(\"title\", \"\"));")
        val html = companion(html = "<span wicket:id=\"title\" class=\"\"></span>")
        assertEmpty(complete(java, "\"title\", \"<caret>\""))
        assertEmpty(complete(html, "class=\"<caret>\""))
    }

    fun testUnpairedHtmlAndAmbiguousCompanionsReceiveNoWicketCompletion() {
        val java = pageFile(body = "add(new Label(\"\"));")
        companion()
        myFixture.addFileToProject("src/main/java/example/ExamplePage.html", "<span wicket:id=\"other\"></span>")
        assertEmpty(complete(java, "\"<caret>\""))
        val html = companion("Ordinary", "<span wicket:id=\"\"></span>")
        assertEmpty(complete(html, "wicket:id=\"<caret>\""))
    }

    fun testUnsupportedSetupAndDuplicateParentIdsReceiveNoWicketCompletion() {
        val java = pageFile(body = "add(new Label(\"\")); removeAll();")
        val html = companion(html = "<span wicket:id=\"title\"></span>")
        assertEmpty(complete(java, "\"<caret>\""))
        assertEmpty(complete(html, "wicket:id=\"t<caret>itle\""))
    }

    fun testJavaInsertionEscapesQuotesAndBackslashes() {
        val java = pageFile(body = "add(new Label(\"\"));")
        companion(html = "<span wicket:id=\"say&amp;&quot;hello\\\"></span>")
        val items = complete(java, "\"<caret>\"")
        assertEquals(listOf("say&\"hello\\"), items.map { it.lookupString })
        myFixture.lookup.currentItem = items.single()
        myFixture.finishLookup('\n')
        assertTrue(java.text.contains("\"say&\\\"hello\\\\\""))
    }

    fun testHtmlInsertionEscapesEntitiesAndPreservesSingleQuotes() {
        pageFile(body = "add(new Label(\"say&'hello\\\"\"));")
        val html = companion(html = "<span wicket:id=''></span>")
        val items = complete(html, "wicket:id='<caret>'")
        assertEquals(listOf("say&'hello\""), items.map { it.lookupString })
        myFixture.lookup.currentItem = items.single()
        myFixture.finishLookup('\n')
        assertEquals("<span wicket:id='say&amp;&#39;hello&quot;'></span>", html.text)
    }

    fun testPanelPreviewIsNotACompletionCandidate() {
        val java = myFixture.addFileToProject("src/main/java/example/ExamplePanel.java", """
            package example; public class ExamplePanel extends org.apache.wicket.markup.html.panel.Panel {
                public ExamplePanel(String id) { super(id); add(new org.apache.wicket.markup.html.basic.Label("")); }
            }
        """.trimIndent())
        companion("ExamplePanel", "<span wicket:id=\"preview\"></span><wicket:panel><span wicket:id=\"active\"></span></wicket:panel>")
        assertEquals(listOf("active"), complete(java, "Label(\"<caret>\"").map { it.lookupString })
    }

    private fun complete(file: PsiFile, snippet: String): List<LookupElement> {
        myFixture.configureFromExistingVirtualFile(file.virtualFile)
        val caret = snippet.indexOf("<caret>")
        val raw = snippet.replace("<caret>", "")
        val start = file.text.indexOf(raw)
        check(start >= 0) { "Missing completion site $raw" }
        myFixture.editor.caretModel.moveToOffset(start + caret)
        return myFixture.completeBasic().orEmpty().filter { it.getUserData(WicketIdCompletionContributor.CANDIDATE) == true }
    }
}
