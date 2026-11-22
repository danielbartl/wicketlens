package dev.wicketlens

import com.intellij.codeInsight.CodeInsightSettings
import com.intellij.codeInsight.lookup.Lookup
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.psi.PsiFile
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.PsiTestUtil
import dev.wicketlens.localization.WicketMessageCompletionContributor
import dev.wicketlens.localization.WicketMessageDeclarationHandler
import org.jetbrains.jps.model.java.JavaResourceRootType
import org.jetbrains.jps.model.java.JavaSourceRootType

class WicketMessageEditorTest : WicketJavaPsiTestCase() {
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
        PsiTestUtil.addSourceRoot(module, dir("src/test/resources"), JavaResourceRootType.TEST_RESOURCE)
    }

    fun testCompletesOneKeyAcrossBaseAndVariantInHtml() {
        page()
        val markup = html("""<wicket:message key="wel<caret>old">Fallback</wicket:message>""")
        bundle("Messages.properties", "welcome=Hello\n")
        bundle("Messages_fr.properties", "welcome=Bonjour\n")
        bundle("Other.properties", "welcoming=Wrong owner\n")
        myFixture.addFileToProject("src/test/resources/example/Messages.properties", "welcomeTest=Test only\n")
        open(markup, "wel<caret>old")

        assertEquals(listOf("welcome"), completeKeys())
        myFixture.finishLookup(Lookup.NORMAL_SELECT_CHAR)
        assertTrue(myFixture.editor.document.text.contains("""<wicket:message key="welcome">Fallback</wicket:message>"""))
    }

    fun testCompletesResourceModelKeyAndPreservesItsJavaFallbackOnTab() {
        val java = page("""new ResourceModel("esc<caret>old", "fallback");""")
        bundle("Messages.properties", """escaped"key=Value""" + "\n")
        open(java, "esc<caret>old")
        assertEquals(listOf("escaped\"key"), completeKeys())
        myFixture.finishLookup(Lookup.REPLACE_SELECT_CHAR)
        assertTrue(myFixture.editor.document.text.contains("""new ResourceModel("escaped\"key", "fallback");"""))
    }

    fun testAttributeMessageCompletesOneMappingAndNavigatesDecodedKeys() {
        page()
        val markup = html("""<input wicket:message="title:tiold,aria-label:access&amp;hint">""")
        val base = bundle("Messages.properties", "title=Title\naccess&hint=Hint\n")
        open(markup, "access&amp;<caret>hint")
        assertEquals(base, targets().single().containingFile)
        open(markup, "aria-<caret>label")
        assertEmpty(targets())
        assertEmpty(completeKeys())
        open(markup, "title:ti<caret>old")
        assertEquals(listOf("title"), completeKeys())
        myFixture.finishLookup(Lookup.NORMAL_SELECT_CHAR)
        assertTrue(myFixture.editor.document.text.contains("""wicket:message="title:title,aria-label:access&amp;hint""""))
    }

    fun testMarkupCompletionEscapesAnHtmlKey() {
        page()
        val markup = html("""<wicket:message key='hel<caret>'>Fallback</wicket:message>""")
        bundle("Messages.properties", "hello'&more=Hello\n")
        open(markup, "hel<caret>")
        assertEquals(listOf("hello'&more"), completeKeys())
        myFixture.finishLookup(Lookup.NORMAL_SELECT_CHAR)
        val newText = myFixture.editor.document.text
        assertTrue(newText, "hello&apos;&amp;more" in newText || "hello&#39;&amp;more" in newText)
    }

    fun testNavigationShowsBaseAndVariantAndUsesDecodedMarkupKey() {
        page()
        val markup = html("""<wicket:message key="hello&amp;<caret>more">Fallback</wicket:message>""")
        val base = bundle("Messages.properties", "hello&more=Hello\n")
        val variant = bundle("Messages_fr.properties", "hello&more=Bonjour\n")
        open(markup, "hello&amp;<caret>more")
        val targets = targets()
        assertEquals(listOf(base, variant), targets.map { it.navigationElement.containingFile })
        assertEquals(listOf("hello&more=Hello", "hello&more=Bonjour"), targets.map { it.navigationElement.text })
        assertTrue(targets.all { it is com.intellij.navigation.NavigationItem && it.presentation?.locationString?.endsWith(".properties") == true })
    }

    fun testNativeGotoOpensDeclarationBesideJava() {
        val java = page("""new StringResourceModel("wel<caret>come", this);""")
        val bundle = myFixture.addFileToProject("src/main/java/example/Messages.properties", "welcome=Hello\n")
        open(java, "wel<caret>come")
        myFixture.performEditorAction(IdeActions.ACTION_GOTO_DECLARATION)
        val editors = FileEditorManager.getInstance(project)
        PlatformTestUtil.waitWithEventsDispatching("The Wicket bundle should open", {
            editors.selectedFiles.singleOrNull() == bundle.virtualFile
        }, 10)
    }

    fun testNamedComponentReceiverUsesItsOwnBundle() {
        val java = page(
            """new StringResourceModel("cap<caret>tion", new Card("card"));""",
            """static class Card extends org.apache.wicket.markup.html.panel.Panel {
                Card(String id) { super(id); }
            }""",
        )
        bundle("Messages.properties", "caption=Outer\n")
        val nested = bundle("Messages\$Card.properties", "caption=Nested\n")
        open(java, "cap<caret>tion")
        assertEquals(nested, targets().single().navigationElement.containingFile)
    }

    fun testGetStringOverloadsCompleteAndNavigateOnlyTheKey() {
        val java = page("""
            getString("title");
            this.getString("title", null);
            super.getString("title", null, "fallback");
            super.getString("tiold", null, "preserve me");
        """)
        val base = bundle("Messages.properties", "title=Title\nfallback=Fallback\n")
        val variant = bundle("Messages_fr.properties", "title=Titre\n")
        for (sample in listOf("""getString("ti<caret>tle");""", """this.getString("ti<caret>tle""", """super.getString("ti<caret>tle""")) {
            open(java, sample)
            assertEquals(listOf(base, variant), targets().map { it.navigationElement.containingFile })
        }
        open(java, "fall<caret>back")
        assertEmpty(targets())
        assertEmpty(completeKeys())
        open(java, """super.getString("ti<caret>old""")
        assertEquals(listOf("title"), completeKeys())
        myFixture.finishLookup(Lookup.NORMAL_SELECT_CHAR)
        assertTrue(myFixture.editor.document.text.contains("""super.getString("title", null, "preserve me")"""))
    }

    fun testGetStringUsesTheAddressedComponentInNestedCode() {
        val java = page("""Card card = new Card(); card.getString("title");""", """
            class Card extends org.apache.wicket.markup.html.panel.Panel {
                Card() { super("card"); }
                void renderText() {
                    String localTitle = getString("title");
                    Messages.this.getString("title");
                    Runnable deferred = new Runnable() { public void run() { Card.super.getString("title"); } };
                }
            }
        """)
        val outer = bundle("Messages.properties", "title=Page\n")
        val inner = bundle("Messages\$Card.properties", "title=Panel\n")
        for (prefix in listOf("card.", "localTitle = ", "Card.super.")) {
            open(java, """${prefix}getString("ti<caret>tle""")
            assertEquals(inner, targets().single().navigationElement.containingFile)
        }
        open(java, """Messages.this.getString("ti<caret>tle""")
        assertEquals(outer, targets().single().navigationElement.containingFile)
    }

    fun testGetStringIgnoresUnrelatedCallsDynamicKeysAndUnknownComponentOwners() {
        val java = page("""
            new Other().getString("title");
            getString("title" + System.nanoTime());
            new org.apache.wicket.markup.html.panel.Panel("id") { void renderText() { getString("title"); } };
        """, """
            static class Other { String getString(String key) { return key; } }
            void renderText(org.apache.wicket.Component component) { component.getString("title"); }
        """)
        bundle("Messages.properties", "title=Page\n")
        for (prefix in listOf("new Other().", "void renderText() { ", "component.")) {
            open(java, """${prefix}getString("ti<caret>tle""")
            assertEmpty(targets())
            assertEmpty(completeKeys())
        }
        open(java, """getString("ti<caret>tle" +""")
        assertEmpty(targets())
        assertEmpty(completeKeys())
    }

    fun testBundleComparisonShowsAbsentAndVariantOnlyKeys() {
        val java = page() as com.intellij.psi.PsiJavaFile
        bundle("Messages.properties", "title=Title\ncommon=Common\n")
        bundle("Messages_fr.properties", "common=Commun\nvariant.only=Variant\n")
        val report = dev.wicketlens.localization.CompareWicketBundlesAction().compare(java.classes.single())
        assertTrue(report.contains("Reference is Messages.properties."))
        val locale = report.substringAfter("Messages_fr.properties")
        assertTrue(locale.contains("Absent (1)\n  title\n"))
        assertTrue(locale.contains("Variant-only (1)\n  variant.only\n"))
        assertTrue(report.contains("inherited at runtime"))
    }

    fun testOtherJavaArgumentsAndHtmlKeysRemainOutsideWicketLocalization() {
        val java = page(
            """new ResourceModel("unused", "wel<caret>come"); new Other("welcome");""",
            "static class Other { Other(String value) {} }",
        )
        bundle("Messages.properties", "welcome=Hello\n")
        open(java, "wel<caret>come")
        assertEmpty(targets())
        open(java, """Other("wel<caret>come")""")
        assertEmpty(targets())
        assertEmpty(completeKeys())
        val markup = html("""<span key="wel<caret>come"></span>""")
        open(markup, "wel<caret>come")
        assertEmpty(targets())
        assertEmpty(completeKeys())
    }

    private fun completeKeys(): List<String> {
        val settings = CodeInsightSettings.getInstance()
        val old = settings.AUTOCOMPLETE_ON_CODE_COMPLETION
        settings.AUTOCOMPLETE_ON_CODE_COMPLETION = false
        return try {
            myFixture.completeBasic().orEmpty().filter { it.getUserData(WicketMessageCompletionContributor.CANDIDATE) == true }
                .map { it.lookupString }
        } finally {
            settings.AUTOCOMPLETE_ON_CODE_COMPLETION = old
        }
    }

    private fun targets(): List<com.intellij.psi.PsiElement> {
        val offset = myFixture.caretOffset
        val file = myFixture.file
        return WicketMessageDeclarationHandler().getGotoDeclarationTargets(file.findElementAt(offset), offset, myFixture.editor)
            .orEmpty().toList()
    }

    private fun open(file: PsiFile, sample: String) {
        myFixture.configureFromExistingVirtualFile(file.virtualFile)
        val text = sample.replace("<caret>", "")
        val start = file.text.indexOf(text)
        check(start >= 0) { "Cannot locate completion sample $sample" }
        myFixture.editor.caretModel.moveToOffset(start + sample.indexOf("<caret>"))
    }

    private fun page(body: String = "", members: String = "") = myFixture.addFileToProject(
        "src/main/java/example/Messages.java",
        """
        package example;
        import org.apache.wicket.markup.html.WebPage;
        import org.apache.wicket.model.ResourceModel;
        import org.apache.wicket.model.StringResourceModel;
        public class Messages extends WebPage {
            public Messages() { $body }
            $members
        }
        """.trimIndent().replace("<caret>", ""),
    )

    private fun html(body: String) = myFixture.addFileToProject("src/main/resources/example/Messages.html",
        """<html xmlns:wicket="http://wicket.apache.org"><body>$body</body></html>""".replace("<caret>", ""))

    private fun bundle(name: String, text: String) = myFixture.addFileToProject("src/main/resources/example/$name", text)
    private fun dir(path: String) = myFixture.tempDirFixture.findOrCreateDir(path)

    companion object {
        private val DESCRIPTOR = object : LightProjectDescriptor() {
            override fun getSdk() = Java25ProjectDescriptor.getSdk()
        }
    }
}
