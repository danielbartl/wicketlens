package dev.wicketlens

import com.intellij.codeInsight.CodeInsightSettings
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementPresentation
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.psi.*
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.refactoring.rename.RenameProcessor
import dev.wicketlens.spring.*

class WicketSpringBeanEditorTest : WicketSpringInspectionTestCase() {
    private var autoComplete = true
    override fun setUp() {
        super.setUp()
        autoComplete = CodeInsightSettings.getInstance().AUTOCOMPLETE_ON_CODE_COMPLETION
        CodeInsightSettings.getInstance().AUTOCOMPLETE_ON_CODE_COMPLETION = false
        myFixture.enableInspections(WicketSpringBeanTypeInspection())
        val profile = com.intellij.profile.codeInspection.InspectionProjectProfileManager.getInstance(project).currentProfile
        profile.setErrorLevel(com.intellij.codeInsight.daemon.HighlightDisplayKey.find("WicketSpringBeanType")!!, com.intellij.codeHighlighting.HighlightDisplayLevel.WEAK_WARNING, project)
    }
    override fun tearDown() {
        try {
            CodeInsightSettings.getInstance().AUTOCOMPLETE_ON_CODE_COMPLETION = autoComplete
            FileEditorManager.getInstance(project).openFiles.forEach { FileEditorManager.getInstance(project).closeFile(it) }
        } finally { super.tearDown() }
    }
    private fun config(methods: String = "@org.springframework.context.annotation.Bean Service orders() { return new Service(); }", name: String = "Config") =
        source(name, "@org.springframework.context.annotation.Configuration public class $name { $methods }")
    private fun pageName(name: String = "orders", type: String = "Service") = page("@SpringBean(name = \"$name\") $type orders;")
    private fun literal(file: PsiFile) = PsiTreeUtil.findChildrenOfType(file, PsiLiteralExpression::class.java).single { it.value is String }
    private fun reference(file: PsiFile) = literal(file).references.filterIsInstance<WicketSpringBeanReference>().single()
    private fun complete(file: PsiFile, snippet: String = "name = \"<caret>\""): List<LookupElement> {
        myFixture.configureFromExistingVirtualFile(file.virtualFile)
        val start = file.text.indexOf(snippet.replace("<caret>", ""))
        check(start >= 0)
        myFixture.editor.caretModel.moveToOffset(start + snippet.indexOf("<caret>"))
        return myFixture.completeBasic().orEmpty().filter { it.getUserData(WicketSpringBeanCompletionContributor.CANDIDATE) == true }
    }
    private fun highlights(file: PsiFile): List<com.intellij.codeInsight.daemon.impl.HighlightInfo> {
        myFixture.configureFromExistingVirtualFile(file.virtualFile)
        return myFixture.doHighlighting().filter { it.description?.startsWith("The source bean '") == true }
    }

    fun testNativeSourceReferenceNavigatesToBeanMethod() {
        val config = config()
        val ref = reference(pageName())
        assertTrue(ref.isSoft)
        assertEquals("orders", (ref.resolve() as PsiMethod).name)
        assertEquals(config, ref.resolve()!!.containingFile)
        assertEquals("orders", ref.element.text.substring(ref.rangeInElement.startOffset, ref.rangeInElement.endOffset))
    }
    fun testAliasNavigatesToItsFactoryMethod() {
        config("@org.springframework.context.annotation.Bean({\"main\", \"alias\"}) Service orders() { return new Service(); }")
        assertEquals("orders", (reference(pageName("alias")).resolve() as PsiMethod).name)
    }
    fun testStereotypeNameNavigatesToClass() {
        source("Orders", "@org.springframework.stereotype.Service(\"orders\") public class Orders extends Service {}")
        assertEquals("Orders", (reference(pageName()).resolve() as PsiClass).name)
    }
    fun testDuplicateBeanNamesRemainSoftAndPolyvariant() {
        config()
        config(name = "OtherConfig")
        val ref = reference(pageName())
        assertNull(ref.resolve())
        assertEquals(2, ref.multiResolve(false).size)
        assertTrue(ref.isSoft)
    }
    fun testUnknownNameHasASoftUnresolvedReference() {
        config()
        val ref = reference(pageName("missing"))
        assertNull(ref.resolve())
        assertTrue(ref.isSoft)
        assertTrue(highlights(ref.element.containingFile).isEmpty())
    }
    fun testEmptyNameDoesNotResolveToAConfigurationDeclaration() {
        config()
        assertTrue(reference(pageName("")).multiResolve(false).isEmpty())
    }
    fun testJavaFactoryRenameDoesNotRewriteExplicitBeanNameOrAlias() {
        config("@org.springframework.context.annotation.Bean(\"alias\") Service orders() { return new Service(); }")
        val java = pageName("alias")
        val target = reference(java).resolve()!!
        RenameProcessor(project, target, "renamedFactory", false, false).run()
        PsiDocumentManager.getInstance(project).commitAllDocuments()
        assertTrue(java.text.contains("name = \"alias\""))
        assertEquals("renamedFactory", (reference(java).resolve() as PsiMethod).name)
    }
    fun testCompletionIncludesOnlyAssignableSourceTypes() {
        config("@org.springframework.context.annotation.Bean Service orders() { return new Service(); } @org.springframework.context.annotation.Bean String title() { return \"title\"; }")
        val items = complete(pageName(""))
        assertEquals(listOf("orders"), items.map { it.lookupString })
        val presentation = LookupElementPresentation()
        items.single().renderElement(presentation)
        assertTrue(presentation.tailText!!.contains("context not verified"))
    }
    fun testCompletionIncludesAliasesAndDeduplicatesNames() {
        config("@org.springframework.context.annotation.Bean({\"orders\", \"alias\"}) Service orders() { return new Service(); }")
        config(name = "OtherConfig")
        assertEquals(listOf("alias", "orders"), complete(pageName("")).map { it.lookupString })
    }
    fun testCompletionReplacesTheWholeSuffixAndKeepsTheQuotes() {
        config()
        val java = pageName("orSuffix")
        val item = complete(java, "name = \"or<caret>Suffix\"").single()
        myFixture.lookup.currentItem = item
        myFixture.finishLookup('\n')
        assertTrue(java.text.contains("name = \"orders\""))
        assertFalse(java.text.contains("Suffix"))
    }
    fun testCompletionEscapesBeanNamesInJavaStrings() {
        config("@org.springframework.context.annotation.Bean(\"say\\\"hi\\\\\") Service orders() { return new Service(); }")
        val java = pageName("")
        val item = complete(java).single()
        assertEquals("say\"hi\\", item.lookupString)
        myFixture.lookup.currentItem = item
        myFixture.finishLookup('\n')
        assertEquals("say\"hi\\", literal(java).value)
    }
    fun testNamedInjectHasNativeCompletionAndNavigationWhenSpringIsRegistered() {
        config()
        application()
        val java = page("@Inject @jakarta.inject.Named(\"\") Service orders;")
        val item = complete(java, "Named(\"<caret>\")").single()
        myFixture.lookup.currentItem = item
        myFixture.finishLookup('\n')
        assertEquals("orders", (reference(java).resolve() as PsiMethod).name)
    }
    fun testUnregisteredNamedInjectHasNoWicketSpringCompletion() {
        config()
        assertTrue(complete(page("@Inject @jakarta.inject.Named(\"\") Service orders;"), "Named(\"<caret>\")").isEmpty())
    }
    fun testUnrelatedStringDoesNotGetBeanNameCompletion() {
        config()
        assertTrue(complete(page("String name = \"\";"), "name = \"<caret>\"").isEmpty())
    }
    fun testFactoryBeanGenericProductTypesAreIncludedByCompletion() {
        source("OrdersFactory", "public final class OrdersFactory implements org.springframework.beans.factory.FactoryBean<Service> { public Service getObject() { return null; } public Class<?> getObjectType() { return Service.class; } }")
        config("@org.springframework.context.annotation.Bean OrdersFactory orders() { return new OrdersFactory(); }")
        val java = pageName("")
        assertEquals(listOf("orders"), complete(java).map { it.lookupString })
        edit(java, java.text.replace("name = \"\"", "name = \"orders\""))
        assertEquals("orders", (reference(java).resolve() as PsiMethod).name)
    }
    fun testCompletionLabelsProfileConditionsWithoutClaimingActivation() {
        config("@org.springframework.context.annotation.Bean @org.springframework.context.annotation.Profile(\"prod\") Service orders() { return null; }")
        val item = complete(pageName("")).single()
        val presentation = LookupElementPresentation()
        item.renderElement(presentation)
        assertTrue(presentation.tailText!!.contains("profiles: (prod)"))
        assertTrue(presentation.tailText!!.contains("context not verified"))
    }
    fun testFactoryInstanceCompletionInsertsAmpersandAndNavigatesToAliasDeclaration() {
        source("OrdersFactory", "public final class OrdersFactory implements org.springframework.beans.factory.FactoryBean<Service> { public Service getObject(){return null;} public Class<?> getObjectType(){return null;} }")
        config("@org.springframework.context.annotation.Bean({\"orders\",\"alias\"}) OrdersFactory orders() { return null; }")
        val java = pageName("", "OrdersFactory")
        val items = complete(java)
        assertEquals(listOf("&alias", "&orders"), items.map { it.lookupString })
        val item = items.first()
        val presentation = LookupElementPresentation()
        item.renderElement(presentation)
        assertTrue(presentation.tailText!!.contains("FactoryBean instance"))
        myFixture.lookup.currentItem = item
        myFixture.finishLookup('\n')
        assertEquals("&alias", literal(java).value)
        assertEquals("orders", (reference(java).resolve() as PsiMethod).name)
    }
    fun testUnknownFactoryProductDoesNotOfferNormalNamesButFactoryReferencesWork() {
        config("@org.springframework.context.annotation.Bean org.springframework.beans.factory.FactoryBean orders(){return null;}")
        assertTrue(complete(pageName("")).isEmpty())
    }
    fun testProductCompletionPresentationNamesItsGenericSourceType() {
        config("@org.springframework.context.annotation.Bean org.springframework.beans.factory.FactoryBean<Service> orders(){return null;}")
        val item = complete(pageName("")).single()
        val presentation = LookupElementPresentation()
        item.renderElement(presentation)
        assertTrue(presentation.tailText!!.contains("FactoryBean product: example.Service"))
    }
    fun testSourceTypeFindingAppearsAsANativeWeakWarning() {
        config("@org.springframework.context.annotation.Bean String orders() { return \"text\"; }")
        application(arguments = "this, new org.springframework.context.annotation.AnnotationConfigApplicationContext(Config.class)")
        val finding = highlights(pageName()).single()
        assertEquals(HighlightSeverity.WEAK_WARNING, finding.severity)
    }
    fun testNativeSuppressionHidesTheTypeFinding() {
        config("@org.springframework.context.annotation.Bean String orders() { return \"text\"; }")
        application(arguments = "this, new org.springframework.context.annotation.AnnotationConfigApplicationContext(Config.class)")
        assertTrue(highlights(page("@SuppressWarnings(\"WicketSpringBeanType\") @SpringBean(name = \"orders\") Service orders;")).isEmpty())
    }
    fun testSourceMethodRenameInvalidatesNavigationCandidates() {
        val config = config()
        val java = pageName()
        assertNotNull(reference(java).resolve())
        edit(config, config.text.replace("Service orders()", "Service changed()"))
        assertNull(reference(java).resolve())
    }
}
