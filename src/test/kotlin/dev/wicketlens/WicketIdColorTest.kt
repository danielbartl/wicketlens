package dev.wicketlens

import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.fileEditor.FileEditorManager
import dev.wicketlens.editor.WicketIdAnnotator
import dev.wicketlens.editor.WicketIdColorSettingsPage
import java.awt.Color

class WicketIdColorTest : WicketProjectPsiTestCase() {
    override fun tearDown() {
        try { FileEditorManager.getInstance(project).openFiles.forEach { FileEditorManager.getInstance(project).closeFile(it) } }
        finally { super.tearDown() }
    }

    fun testOnlyTheReviewedConstructorIdIsColoredInJava() {
        val java = pageFile(body = "add(new Label(\"title\", \"display\")); String unrelated = \"other\";")
        companion()
        myFixture.configureFromExistingVirtualFile(java.virtualFile)
        val colored = myFixture.doHighlighting().filter { it.forcedTextAttributesKey == WicketIdAnnotator.ID }
        assertEquals(listOf("title"), colored.map { java.text.substring(it.startOffset, it.endOffset) })
    }

    fun testOnlyWicketIdValuesAreColoredInHtml() {
        val html = companion(html = "<!-- wicket:id='comment' --><span wicket:id=\"title\" class=\"other\">text</span>")
        myFixture.configureFromExistingVirtualFile(html.virtualFile)
        val colored = myFixture.doHighlighting().filter { it.forcedTextAttributesKey == WicketIdAnnotator.ID }
        assertEquals(listOf("title"), colored.map { html.text.substring(it.startOffset, it.endOffset) })
    }

    fun testBothBundledSchemesUseWicketOrangeAndOfferAColorSetting() {
        val manager = EditorColorsManager.getInstance()
        for (name in listOf("Default", "Darcula")) {
            val scheme = manager.getScheme(name)!!
            assertEquals(Color(0xE9601A), scheme.getAttributes(WicketIdAnnotator.ID).foregroundColor)
        }
        assertEquals(WicketIdAnnotator.ID, WicketIdColorSettingsPage().attributeDescriptors.single().key)
    }

    fun testColorDoesNotTurnUnresolvedIdsIntoProblemsOrNavigationTargets() {
        val java = pageFile(body = "add(new Label(\"title\")); removeAll();")
        companion()
        myFixture.configureFromExistingVirtualFile(java.virtualFile)
        assertEquals(1, myFixture.doHighlighting().count { it.forcedTextAttributesKey == WicketIdAnnotator.ID })
        assertNull(dev.wicketlens.editor.WicketIdPsi.pair(java.idValue("title")))
    }
}
