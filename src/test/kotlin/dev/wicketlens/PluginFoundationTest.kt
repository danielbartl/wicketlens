package dev.wicketlens

import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.html.HtmlTag
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import dev.wicketlens.actions.AnalyzeWicketPageAction

/** Real platform fixtures: no regex parsing or mocked PSI. */
class PluginFoundationTest : LightJavaCodeInsightFixtureTestCase() {
    override fun getProjectDescriptor(): LightProjectDescriptor = Java25ProjectDescriptor

    fun testJavaPsiResolvesTypes() {
        val file = myFixture.configureByText(
            "ExamplePage.java",
            "class ExamplePage { String title; }",
        ) as PsiJavaFile

        val page = file.classes.single()
        assertEquals("ExamplePage", page.name)
        val type = page.fields.single().type as PsiClassType
        assertEquals("java.lang.String", type.resolve()?.qualifiedName)
    }

    fun testHtmlPsiExposesWicketAttributes() {
        val file = myFixture.configureByText(
            "ExamplePage.html",
            "<html><body><span wicket:id=\"title\">Hello</span></body></html>",
        )

        val tag = PsiTreeUtil.findChildrenOfType(file, HtmlTag::class.java)
            .single { it.name == "span" }
        assertEquals("title", tag.getAttributeValue("wicket:id"))
    }

    fun testAnalyzeActionIsRegistered() {
        val action = ActionManager.getInstance().getAction("WicketLens.AnalyzeWicketPage")
        assertTrue(action is AnalyzeWicketPageAction)
        assertEquals("Analyze Wicket Page", action.templatePresentation.text)
        assertEquals(ActionUpdateThread.BGT, action.actionUpdateThread)
    }

    fun testCorePluginLoadsWithMcpServerDisabled() {
        val manager = com.intellij.ide.plugins.PluginManagerCore
        val wicket = manager.getPlugin(com.intellij.openapi.extensions.PluginId.getId("dev.wicketlens"))!!
        assertTrue(wicket.isEnabled)
        assertFalse(manager.getPlugin(com.intellij.openapi.extensions.PluginId.getId("com.intellij.mcpServer"))?.isEnabled == true)
        assertNotNull(ActionManager.getInstance().getAction("WicketLens.GenerateSpringTest"))
        assertNotNull(ActionManager.getInstance().getAction("WicketLens.CopyWicketContext"))
    }
}
