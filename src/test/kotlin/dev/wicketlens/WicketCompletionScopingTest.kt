package dev.wicketlens

import com.intellij.codeInsight.CodeInsightSettings
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.psi.*
import com.intellij.psi.util.PsiTreeUtil
import dev.wicketlens.editor.*

/** User-visible popup assertions include all contributors, not just WicketLens-tagged items. */
class WicketCompletionScopingTest : WicketProjectPsiTestCase() {
    private var automatic=true
    override fun setUp() { super.setUp();automatic=CodeInsightSettings.getInstance().AUTOCOMPLETE_ON_CODE_COMPLETION;CodeInsightSettings.getInstance().AUTOCOMPLETE_ON_CODE_COMPLETION=false }
    override fun tearDown() {
        try { CodeInsightSettings.getInstance().AUTOCOMPLETE_ON_CODE_COMPLETION=automatic;FileEditorManager.getInstance(project).openFiles.forEach { FileEditorManager.getInstance(project).closeFile(it) } }
        finally { super.tearDown() }
    }
    private fun file(path:String)=PsiManager.getInstance(project).findFile(myFixture.findFileInTempDir(path))!!
    private fun edit(file:PsiFile,before:String,after:String) {
        assertTrue(file.text,before in file.text)
        WriteCommandAction.runWriteCommandAction(project) { PsiDocumentManager.getInstance(project).getDocument(file)!!.setText(file.text.replace(before,after)) }
        PsiDocumentManager.getInstance(project).commitAllDocuments()
        com.intellij.openapi.fileEditor.FileDocumentManager.getInstance().saveDocument(PsiDocumentManager.getInstance(project).getDocument(file)!!)
    }
    private fun popup(file:PsiFile,snippet:String):List<LookupElement> {
        myFixture.configureFromExistingVirtualFile(file.virtualFile)
        val start=file.text.indexOf(snippet.replace("<caret>",""));assertTrue(file.text,start>=0)
        myFixture.editor.caretModel.moveToOffset(start+snippet.indexOf("<caret>"))
        return myFixture.completeBasic().orEmpty().toList()
    }
    private fun noise() {
        myFixture.addFileToProject("src/main/java/example/OtherPage.java","package example;public class OtherPage extends org.apache.wicket.markup.html.WebPage { public OtherPage() { add(new org.apache.wicket.markup.html.basic.Label(\"otherPageOnly\",\"randomDisplayText\")); } }")
        companion("OtherPage","<b wicket:id='otherPageOnly'></b>")
    }
    fun testJavaPopupContainsOnlyCurrentPageCounterparts() {
        noise();val java=pageFile(body="add(new Label(\"\",\"randomDisplayText\"));");companion(html="<b wicket:id='title'></b>")
        val items=popup(java,"Label(\"<caret>\"")
        assertEquals(items.toString(),listOf("title"),items.map { it.lookupString })
    }
    fun testHtmlPopupContainsOnlyCurrentPageCounterparts() {
        noise();pageFile(body="add(new Label(\"title\",\"randomDisplayText\"));");val html=companion(html="<b wicket:id=''></b>")
        val items=popup(html,"wicket:id='<caret>'")
        assertEquals(items.toString(),listOf("title"),items.map { it.lookupString })
    }
    fun testEmptyJavaDialogHostIdRetainsItsPageParent() {
        val java=WicketInitialContentFixture.install(myFixture);edit(java,"DialogBox(\"dialogBox\",", "DialogBox(\"\",")
        val items=popup(java,"DialogBox(\"<caret>\"")
        assertEquals(items.toString(),listOf("dialogBox"),items.map { it.lookupString })
    }
    fun testEmptyHtmlDialogHostIdRetainsItsPageParent() {
        WicketInitialContentFixture.install(myFixture);val html=file("src/main/resources/example/ContentPage.html");edit(html,"'dialogBox'","''")
        val items=popup(html,"wicket:id='<caret>'")
        assertEquals(items.toString(),listOf("dialogBox"),items.map { it.lookupString })
    }
    fun testPartialHtmlDialogHostIdUsesTheCounterpart() {
        WicketInitialContentFixture.install(myFixture);val html=file("src/main/resources/example/ContentPage.html");edit(html,"'dialogBox'","'diaSuffix'")
        val items=popup(html,"wicket:id='dia<caret>Suffix'")
        assertEquals(items.toString(),listOf("dialogBox"),items.map { it.lookupString })
    }
    fun testPartialHtmlDialogFragmentChildUsesItsOwnParent() {
        WicketInitialContentFixture.install(myFixture);val html=file("src/main/resources/example/ContentPage.html");edit(html,"'name'","'naSuffix'")
        val items=popup(html,"wicket:id='na<caret>Suffix'")
        assertEquals(items.toString(),listOf("name"),items.map { it.lookupString })
    }
    fun testPopupDoesNotOfferIdsAlreadyUsedByOtherTags() {
        noise();pageFile(body="add(new Label(\"title\",\"randomDisplayText\"));");val html=companion(html="<b wicket:id='title'></b><b wicket:id=''></b>")
        val items=popup(html,"wicket:id='<caret>'")
        assertEmpty(items)
    }
    fun testUnprovedWicketIdOffersCompanionIdsWithoutGenericDisplayWords() {
        val java=pageFile(body="add(new Label(\"\",\"randomDisplayText\"));removeAll();");companion(html="<b wicket:id='title'></b>")
        assertEquals(listOf("title"),popup(java,"Label(\"<caret>\"").map { it.lookupString })
    }
    fun testDisplayModelStringsKeepTheirNormalWordCompletion() {
        val java=pageFile(body="add(new Label(\"title\",\"\"),new Label(\"description\",\"randomDisplayText\"));")
        companion(html="<b wicket:id='title'></b><b wicket:id='description'></b>")
        val items=popup(java,"\"title\",\"<caret>\"")
        assertTrue(items.toString(),items.any { it.lookupString=="randomDisplayText" })
        assertTrue(items.all { it.getUserData(WicketIdCompletionContributor.CANDIDATE)!=true })
    }
    private fun transparentClass() {
        myFixture.addFileToProject("src/main/java/example/Html.java", "package example;public class Html extends org.apache.wicket.markup.html.TransparentWebMarkupContainer { public Html(String id) { super(id); } }")
    }
    fun testJavaIdRetainsTransparentFallbackScopeWithoutARealBinding() {
        transparentClass();val java=pageFile(body="add(new Html(\"shell\"),new Label(\"\"));")
        companion(html="<div wicket:id='shell'><b wicket:id='title'></b></div>")
        assertEquals(listOf("title"),popup(java,"Label(\"<caret>\"").map { it.lookupString })
    }
    fun testHtmlIdCanUseKnownOrdinaryAndTransparentFallbackChildren() {
        transparentClass();pageFile(body="Html shell=new Html(\"shell\");shell.add(new Label(\"inside\"));add(shell,new Label(\"outside\"));")
        val html=companion(html="<div wicket:id='shell'><b wicket:id=''></b></div>")
        assertEquals(setOf("inside","outside"),popup(html,"wicket:id='<caret>'").map { it.lookupString }.toSet())
    }
    fun testTransparentOrdinaryChildWinsBeforeThePageChildWithTheSameId() {
        transparentClass();pageFile(body="Html shell=new Html(\"shell\");shell.add(new org.apache.wicket.markup.html.WebMarkupContainer(\"shared\"));add(shell,new Label(\"shared\"));")
        val html=companion(html="<div wicket:id='shell'><b wicket:id=''></b></div>")
        val attribute=PsiTreeUtil.findChildrenOfType(html,com.intellij.psi.xml.XmlAttributeValue::class.java).single { it.value=="" }
        val choices=WicketIdCompletionContributor().suggestions(attribute)
        assertEquals(choices.toString(),listOf("shared"),choices.map { it.id })
        assertEquals(listOf("org.apache.wicket.markup.html.WebMarkupContainer"),choices.single().componentTypes)
    }
    fun testJavaFallbackDoesNotStealTheIdOfAKnownOrdinaryChild() {
        transparentClass();val java=pageFile(body="Html shell=new Html(\"shell\");shell.add(new Label(\"title\"));add(shell,new Label(\"\",\"randomDisplayText\"));")
        companion(html="<div wicket:id='shell'><b wicket:id='title'></b></div>")
        assertEmpty(popup(java,"Label(\"<caret>\""))
    }
    fun testNestedTransparentLookupKeepsThePageScope() {
        transparentClass();pageFile(body="Html outer=new Html(\"outer\");outer.add(new Html(\"inner\"));add(outer,new Label(\"title\"));")
        val html=companion(html="<div wicket:id='outer'><div wicket:id='inner'><b wicket:id=''></b></div></div>")
        assertEquals(listOf("title"),popup(html,"wicket:id='<caret>'").map { it.lookupString })
    }
    fun testOrdinaryContainerRemainsABoundaryUnderTransparentMarkup() {
        transparentClass();pageFile(body="Html shell=new Html(\"shell\");shell.add(new org.apache.wicket.markup.html.WebMarkupContainer(\"box\"));add(shell,new Label(\"outside\"));")
        val html=companion(html="<div wicket:id='shell'><div wicket:id='box'><b wicket:id=''></b></div></div>")
        assertEmpty(popup(html,"wicket:id='<caret>'"))
    }
    fun testAmbiguousTransparentParentSuppliesNoLookupHypothesis() {
        transparentClass();pageFile(body="add(new Html(\"shell\"),new Label(\"title\"));")
        val html=companion(html="<div wicket:id='shell'><b wicket:id=''></b></div><div wicket:id='shell'></div>")
        assertEmpty(popup(html,"wicket:id='<caret>'"))
    }
    fun testTransparentCompletionHypothesesDoNotEnterNormalReportsOrMcp() {
        transparentClass();val java=pageFile(body="add(new Html(\"shell\"),new Label(\"\"));")
        val html=companion(html="<div wicket:id='shell'><b wicket:id='title'></b></div>")
        myFixture.configureFromExistingVirtualFile(java.virtualFile)
        val before=listOf(java,html).map { it.text to it.modificationStamp }
        val query=dev.wicketlens.mcp.WicketPageAnalysisRequest();val snapshot=query.capture(project,"example.ExamplePage",includeNavigationContexts=true)
        val exported=query.render(snapshot)
        assertTrue(snapshot.report.markup!!.completionLookupScopes.isEmpty())
        assertEquals(listOf("title"),popup(java,"Label(\"<caret>\"").map { it.lookupString })
        val fresh=query.capture(project,"example.ExamplePage",includeNavigationContexts=true)
        assertEquals(exported,query.render(fresh))
        assertTrue(dev.wicketlens.editor.WicketEditorAnalysis.pages(java).all { it.report.markup!!.completionLookupScopes.isEmpty() })
        query.requireCurrent(project,fresh);assertEquals(before,listOf(java,html).map { it.text to it.modificationStamp })
    }
    fun testTransparentCounterpartEditInvalidatesTheCompletionScopeCache() {
        transparentClass();val java=pageFile(body="add(new Html(\"shell\"),new Label(\"\"));")
        val html=companion(html="<div wicket:id='shell'><b wicket:id='title'></b></div>")
        val literal=PsiTreeUtil.findChildrenOfType(java,PsiLiteralExpression::class.java).single { it.value=="" }
        assertEquals(listOf("title"),WicketIdCompletionContributor().suggestions(literal).map { it.id })
        edit(html,"'title'","'changed'")
        assertEquals(listOf("changed"),WicketIdCompletionContributor().suggestions(literal).map { it.id })
    }
    fun testTransparentUncommittedCounterpartWithholdsCachedSuggestions() {
        transparentClass();val java=pageFile(body="add(new Html(\"shell\"),new Label(\"\"));")
        val html=companion(html="<div wicket:id='shell'><b wicket:id='title'></b></div>")
        val literal=PsiTreeUtil.findChildrenOfType(java,PsiLiteralExpression::class.java).single { it.value=="" }
        assertEquals(listOf("title"),WicketIdCompletionContributor().suggestions(literal).map { it.id })
        WriteCommandAction.runWriteCommandAction(project) { PsiDocumentManager.getInstance(project).getDocument(html)!!.setText(html.text.replace("'title'","'changed'")) }
        assertEmpty(WicketIdCompletionContributor().suggestions(literal))
    }
    fun testNestedCompletionProbeRestoresBothJavaAndMarkupSitesAfterFailure() {
        val origin=dev.wicketlens.model.SourceLocation("/page.html",10);val attribute=dev.wicketlens.model.SourceLocation("/page.html",1)
        val other=dev.wicketlens.model.SourceLocation("/other.java",5)
        dev.wicketlens.analysis.WicketCompletionAnalysis.at(origin,attribute) {
            assertTrue(dev.wicketlens.analysis.WicketCompletionAnalysis.editingAttribute(attribute))
            try { dev.wicketlens.analysis.WicketCompletionAnalysis.at(other) { throw IllegalStateException("stop") } } catch(_:IllegalStateException) {}
            assertTrue(dev.wicketlens.analysis.WicketCompletionAnalysis.editingOrigin(origin))
            assertTrue(dev.wicketlens.analysis.WicketCompletionAnalysis.editingAttribute(attribute))
        }
        assertFalse(dev.wicketlens.analysis.WicketCompletionAnalysis.active)
        assertFalse(dev.wicketlens.analysis.WicketCompletionAnalysis.editingAttribute(attribute))
    }
    fun testTypingAnEmptyDialogIdThenInvokingCompletion() {
        val java=WicketInitialContentFixture.install(myFixture);myFixture.configureFromExistingVirtualFile(java.virtualFile)
        val literal=PsiTreeUtil.findChildrenOfType(java,PsiLiteralExpression::class.java).single { it.value=="name" }
        val start=literal.textRange.startOffset+1;val end=literal.textRange.endOffset-1
        myFixture.editor.caretModel.moveToOffset(end)
        myFixture.editor.selectionModel.setSelection(start,end)
        myFixture.performEditorAction(com.intellij.openapi.actionSystem.IdeActions.ACTION_EDITOR_BACKSPACE)
        val items=myFixture.completeBasic().orEmpty().toList()
        assertEquals(items.toString(),listOf("name"),items.map { it.lookupString })
    }
}
