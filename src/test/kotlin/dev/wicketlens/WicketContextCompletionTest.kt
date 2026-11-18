package dev.wicketlens

import com.intellij.codeInsight.CodeInsightSettings
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementPresentation
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.psi.*
import com.intellij.psi.util.PsiTreeUtil
import dev.wicketlens.analysis.*
import dev.wicketlens.editor.*
import dev.wicketlens.export.WicketContextExporter
import dev.wicketlens.mcp.*
import dev.wicketlens.model.*

class WicketContextCompletionTest : WicketProjectPsiTestCase() {
    private var automatic = true
    override fun setUp() { super.setUp();automatic=CodeInsightSettings.getInstance().AUTOCOMPLETE_ON_CODE_COMPLETION;CodeInsightSettings.getInstance().AUTOCOMPLETE_ON_CODE_COMPLETION=false }
    override fun tearDown() {
        try { CodeInsightSettings.getInstance().AUTOCOMPLETE_ON_CODE_COMPLETION=automatic;FileEditorManager.getInstance(project).openFiles.forEach { FileEditorManager.getInstance(project).closeFile(it) } }
        finally { super.tearDown() }
    }
    private fun file(path:String)=PsiManager.getInstance(project).findFile(myFixture.findFileInTempDir(path))!!
    private fun literal(java:PsiFile,id:String="")=PsiTreeUtil.findChildrenOfType(java,PsiLiteralExpression::class.java).single {
        it.value==id && (id.isNotEmpty() || (it.parent as? PsiExpressionList)?.let { args -> (args.parent is PsiNewExpression || args.parent is PsiAnonymousClass) && args.expressions.firstOrNull()==it }==true)
    }
    private fun value(html:PsiFile,id:String="")=PsiTreeUtil.findChildrenOfType(html,com.intellij.psi.xml.XmlAttributeValue::class.java).single { it.value==id && (it.parent as? com.intellij.psi.xml.XmlAttribute)?.name=="wicket:id" }
    private fun suggestions(at:PsiElement)=WicketIdCompletionContributor().suggestions(at)
    private fun complete(file:PsiFile,snippet:String):List<LookupElement> {
        myFixture.configureFromExistingVirtualFile(file.virtualFile)
        val raw=snippet.replace("<caret>","");val start=file.text.indexOf(raw);check(start>=0) { raw }
        myFixture.editor.caretModel.moveToOffset(start+snippet.indexOf("<caret>"))
        return myFixture.completeBasic().orEmpty().filter { it.getUserData(WicketIdCompletionContributor.CANDIDATE)==true }
    }
    private fun edit(file:PsiFile,before:String,after:String,commit:Boolean=true) {
        assertTrue(file.text,before in file.text)
        WriteCommandAction.runWriteCommandAction(project) { PsiDocumentManager.getInstance(project).getDocument(file)!!.setText(file.text.replace(before,after)) }
        if(commit) PsiDocumentManager.getInstance(project).commitAllDocuments()
    }
    fun testEmptyJavaEventChildHasInferredPopupAndReplacesOnlyTheLiteral() {
        val java=WicketEventContentFixture.install(myFixture,pageSource=WicketEventContentFixture.page.replace("DetailList(\"list\")","DetailList(\"\")"))
        val html=file("src/main/resources/example/EventPage.html");val htmlBefore=html.text
        val query=WicketPageAnalysisRequest();val snapshot=query.capture(project,"example.EventPage",includeNavigationContexts=true)
        assertTrue(snapshot.navigationContexts!!.contexts.none { it.report.lifecycle?.directContent!=null })
        val before=WicketContextExporter().export(snapshot.report)
        val items=complete(java,"DetailList(\"<caret>\")")
        assertEquals(listOf("list"),items.map { it.lookupString })
        val display=LookupElementPresentation();items.single().renderElement(display)
        assertTrue(display.typeText.orEmpty(),display.typeText.orEmpty().contains("DetailList"))
        assertTrue(display.tailText.orEmpty(),display.tailText.orEmpty().contains("inferred"))
        assertTrue(display.tailText.orEmpty(),display.tailText.orEmpty().contains("event"))
        assertFalse(WicketCompletionAnalysis.active)
        assertEquals(before,WicketContextExporter().export(WicketPageAnalyzer().analyze(java.classes.single())!!))
        assertNull(WicketIdRenameTarget.at(java,literal(java).textOffset))
        myFixture.lookup.currentItem=items.single();myFixture.finishLookup('\n')
        assertTrue(java.text.contains("DetailList(\"list\")"));assertEquals(htmlBefore,html.text)
        PsiDocumentManager.getInstance(project).commitAllDocuments()
        assertNull(WicketIdRenameTarget.at(java,literal(java,"list").textOffset))
    }
    fun testEmptyHtmlEventChildUsesItsFragmentContext() {
        WicketEventContentFixture.install(myFixture,markupSource=WicketEventContentFixture.markup.replace("'list'","''"))
        val html=file("src/main/resources/example/EventPage.html")
        val choices=suggestions(value(html))
        assertEquals(choices.toString(),listOf("list"),choices.map { it.id })
        assertEquals(Confidence.INFERRED,choices.single().confidence)
        assertTrue(choices.single().contexts.single().contains("event"))
        assertEquals(listOf("list"),complete(html,"wicket:id='<caret>'").map { it.lookupString })
    }
    fun testPartialJavaEventIdReplacesTheSuffix() {
        val java=WicketEventContentFixture.install(myFixture,pageSource=WicketEventContentFixture.page.replace("DetailList(\"list\")","DetailList(\"liSuffix\")"))
        val items=complete(java,"DetailList(\"li<caret>Suffix\")")
        assertEquals(listOf("list"),items.map { it.lookupString })
        myFixture.lookup.currentItem=items.single();myFixture.finishLookup('\n')
        assertTrue(java.text.contains("DetailList(\"list\")"));assertFalse(java.text.contains("liSuffix"))
    }
    fun testInitialDialogJavaFieldExcludesUsedSiblingAndOtherContexts() {
        val java=WicketInitialContentFixture.install(myFixture)
        edit(java,"Label(\"name\",\"Ada\")","Label(\"\",\"Ada\")")
        val choices=suggestions(literal(java))
        assertEquals(choices.toString(),listOf("name"),choices.map { it.id })
        assertEquals(Confidence.INFERRED,choices.single().confidence)
        assertTrue(choices.single().contexts.single().contains("initial content"))
    }
    fun testInitialDialogHtmlFieldUsesTheOwnedFragmentParent() {
        WicketInitialContentFixture.install(myFixture)
        val html=file("src/main/resources/example/ContentPage.html");edit(html,"'name'","''")
        assertEquals(listOf("name"),suggestions(value(html)).map { it.id })
    }
    fun testEmptyFactoryJavaFieldHasItsFormScope() {
        val java=WicketContentFactoryFixture.install(myFixture);edit(java,"SearchField(\"search\")","SearchField(\"\")")
        val choices=suggestions(literal(java))
        assertEquals(choices.toString(),listOf("search"),choices.map { it.id })
        assertTrue(choices.single().contexts.single().contains("factory"))
    }
    fun testEmptyFactoryHtmlFieldHasItsFormScope() {
        WicketContentFactoryFixture.install(myFixture)
        val html=file("src/main/resources/example/FactoryPage.html");edit(html,"'search'","''")
        assertEquals(listOf("search"),suggestions(value(html)).map { it.id })
    }
    fun testEmptyRegisteredRowJavaChildUsesOnlyItsRow() {
        val java=WicketRegisteredListFixture.install(myFixture);edit(java,"Label(\"body\",model.body())","Label(\"\",model.body())")
        val choices=suggestions(literal(java))
        assertEquals(choices.toString(),listOf("body"),choices.map { it.id })
        assertTrue(choices.single().contexts.single().contains("row"))
        assertEquals(Confidence.INFERRED,choices.single().confidence)
    }
    fun testEmptyRegisteredRowHtmlChildUsesOnlyItsRow() {
        WicketRegisteredListFixture.install(myFixture)
        val html=file("src/main/resources/example/CallbackPage.html");edit(html,"'body'","''")
        assertEquals(listOf("body"),suggestions(value(html)).map { it.id })
    }
    fun testSharedPopulationHelperIntersectsItsOuterAndNestedRowUses() {
        val java=WicketPopulationFixture.install(myFixture);edit(java,"Label(\"badge\",Model.of(\"1\"))","Label(\"\",Model.of(\"1\"))")
        val choices=suggestions(literal(java))
        assertEquals(choices.toString(),listOf("badge"),choices.map { it.id })
        assertEquals(Confidence.INFERRED,choices.single().confidence)
        assertEquals(2,choices.single().contexts.size)
    }
    fun testSharedFragmentCannotOfferAnIdThatOnlyFitsOneInstance() {
        val java=pageFile(body="""
            var a=new org.apache.wicket.markup.html.panel.Fragment("a","shared",this);a.add(new Label("left"));add(a);
            var b=new org.apache.wicket.markup.html.panel.Fragment("b","shared",this);b.add(new Label("right"));add(b);
        """)
        val html=companion(html="<div wicket:id='a'></div><div wicket:id='b'></div><wicket:fragment wicket:id='shared'><b wicket:id=''></b></wicket:fragment>")
        assertEmpty(suggestions(value(html)))
        assertTrue(java.isValid)
    }
    fun testCommittedCounterpartEditsInvalidateCompletionCache() {
        val java=WicketEventContentFixture.install(myFixture,pageSource=WicketEventContentFixture.page.replace("DetailList(\"list\")","DetailList(\"\")"))
        val html=file("src/main/resources/example/EventPage.html")
        assertEquals(listOf("list"),suggestions(literal(java)).map { it.id })
        edit(html,"'list'","'records'")
        assertEquals(listOf("records"),suggestions(literal(java)).map { it.id })
    }
    fun testUncommittedCounterpartDoesNotUseCachedCandidates() {
        val java=WicketEventContentFixture.install(myFixture,pageSource=WicketEventContentFixture.page.replace("DetailList(\"list\")","DetailList(\"\")"))
        val html=file("src/main/resources/example/EventPage.html")
        assertEquals(listOf("list"),suggestions(literal(java)).map { it.id })
        edit(html,"'list'","'records'",false)
        assertEmpty(suggestions(literal(java)))
        PsiDocumentManager.getInstance(project).commitAllDocuments()
        assertEquals(listOf("records"),suggestions(literal(java)).map { it.id })
    }
    fun testCompletionDiscoveryIsReadOnlyAndDoesNotLeakIntoDefaultGraphs() {
        val java=WicketEventContentFixture.install(myFixture,pageSource=WicketEventContentFixture.page.replace("DetailList(\"list\")","DetailList(\"\")"))
        val files=listOf(java,file("src/main/java/example/DialogBox.java"),file("src/main/java/example/DrawerBox.java"),file("src/main/resources/example/EventPage.html"))
        val before=files.map { it.text to it.modificationStamp }
        val query=WicketPageAnalysisRequest();val snapshot=query.capture(project,"example.EventPage",includeNavigationContexts=true)
        val response=query.render(snapshot)
        assertEquals(listOf("list"),suggestions(literal(java)).map { it.id })
        assertEquals(response,query.render(query.capture(project,"example.EventPage",includeNavigationContexts=true)))
        assertFalse(WicketCompletionAnalysis.active);query.requireCurrent(project,snapshot)
        assertEquals(before,files.map { it.text to it.modificationStamp })
    }
    fun testValueProjectionBoundsWithholdAllSuggestions() {
        val java=pageFile(body="add(new Label(\"title\"));");val html=companion(html="<b wicket:id=''></b>")
        val report=WicketPageAnalyzer().analyze(java.classes.single())!!
        val attribute=value(html).parent
        val at=SourceLocation(html.virtualFile.path,attribute.textOffset)
        for(limit in listOf(WicketCompletionCandidates.Limits(steps=0),WicketCompletionCandidates.Limits(scopes=0),
            WicketCompletionCandidates.Limits(candidates=0),WicketCompletionCandidates.Limits(depth=0))) {
            val result=WicketCompletionCandidates(limit).project(listOf(report),htmlAttribute=at)
            assertFalse(result.toString(),result.complete);assertEmpty(result.candidates)
        }
    }
    fun testCancellationRestoresTheAnalysisPurpose() {
        val java=WicketEventContentFixture.install(myFixture,pageSource=WicketEventContentFixture.page.replace("DetailList(\"list\")","DetailList(\"\")"))
        val indicator=com.intellij.openapi.progress.EmptyProgressIndicator()
        assertThrows(com.intellij.openapi.progress.ProcessCanceledException::class.java) {
            com.intellij.openapi.progress.ProgressManager.getInstance().runProcess(Runnable { indicator.cancel();suggestions(literal(java)) },indicator)
        }
        assertFalse(WicketCompletionAnalysis.active)
    }
    fun testSyntheticCompletionCopiesCannotProvideContextSuggestions() {
        val java=WicketEventContentFixture.install(myFixture)
        val copy=java.copy() as PsiFile
        assertEmpty(suggestions(literal(copy,"list")))
    }

    fun testEmptyJavaBaseLayoutChildUsesItsDeclaredParent() {
        val java=WicketBaseLayoutFixture.install(myFixture);edit(java,"Label(\"name\",\"App\")","Label(\"\",\"App\")")
        val choices=suggestions(literal(java))
        assertEquals(choices.toString(),listOf("name"),choices.map { it.id })
        assertEquals(Confidence.INFERRED,choices.single().confidence)
        assertTrue(choices.single().contexts.single().contains("base layout"))
    }
    fun testEmptyHtmlBaseLayoutChildUsesItsDeclaredParent() {
        WicketBaseLayoutFixture.install(myFixture)
        val html=file("src/main/resources/example/Shell.html");edit(html,"'name'","''")
        assertEquals(listOf("name"),suggestions(value(html)).map { it.id })
    }
    fun testHtmlInsertionInEventContextPreservesQuotesAndEscapesTheId() {
        WicketEventContentFixture.install(myFixture,pageSource=WicketEventContentFixture.page.replace("DetailList(\"list\")","DetailList(\"say&'hello\\\"\")"),markupSource=WicketEventContentFixture.markup.replace("'list'","''"))
        val html=file("src/main/resources/example/EventPage.html")
        val items=complete(html,"wicket:id='<caret>'")
        assertEquals(listOf("say&'hello\""),items.map { it.lookupString })
        myFixture.lookup.currentItem=items.single();myFixture.finishLookup('\n')
        assertTrue(html.text,html.text.contains("wicket:id='say&amp;&#39;hello&quot;'"))
    }
    fun testUnknownTransferSuppliesOnlyPossibleCompanionCandidates() {
        val java=WicketEventContentFixture.install(myFixture,pageSource=WicketEventContentFixture.page.replace("DetailList(\"list\")","DetailList(\"\")").replace("details.open(content,target);","details.open(content,null);"))
        val choices = suggestions(literal(java))
        assertTrue(choices.any { it.id == "list" })
        assertTrue(choices.all { it.possible && it.confidence == Confidence.UNRESOLVED })
    }
    fun testUnknownParentMarksItsCompanionCandidatesPossible() {
        val java=WicketEventContentFixture.install(myFixture,pageSource=WicketEventContentFixture.page.replace("content.add(new DetailList(\"list\")","((org.apache.wicket.MarkupContainer)EventPage.this.get(\"unknown\")).add(new DetailList(\"\")"))
        val choices = suggestions(literal(java))
        assertTrue(choices.any { it.id == "list" && it.contexts.any { scope -> scope.contains("personDetails") } })
        assertTrue(choices.all { it.possible && it.confidence == Confidence.UNRESOLVED })
    }
    fun testComputedLiteralIsNotReinterpretedAsAnEditingHole() {
        val java=pageFile(body="add(new Label(\"\"+\"title\"));");companion(html="<b wicket:id='title'></b>")
        val empty=PsiTreeUtil.findChildrenOfType(java,PsiLiteralExpression::class.java).single { it.value=="" }
        assertEmpty(suggestions(empty))
    }
    fun testTwoMissingSourceDeclarationIdsOnlySupplyPossibleCandidates() {
        val java=WicketInitialContentFixture.install(myFixture);edit(java,"Label(\"name\",\"Ada\")","Label(\"\",\"Ada\")");edit(java,"Label(\"email\",\"ada@example.test\")","Label(\"\",\"ada@example.test\")")
        val holes=PsiTreeUtil.findChildrenOfType(java,PsiLiteralExpression::class.java).filter { it.value=="" }
        assertEquals(2, holes.size)
        holes.forEach { hole ->
            val choices = suggestions(hole)
            assertTrue(choices.map { it.id }.containsAll(listOf("email", "name")))
            assertTrue(choices.all { it.possible })
        }
    }

    fun testEmptyIdToleranceRequiresTheExactPhysicalProbeSite() {
        val java=pageFile(body="add(new Label(\"\"));")
        val original=literal(java)
        val copy=java.copy() as PsiFile;val copied=literal(copy)
        assertFalse(WicketCompletionAnalysis.active)
        assertFalse(WicketCompletionAnalysis.allowsEmpty(original))
        assertFalse(WicketCompletionAnalysis.allowsEmpty(copied))
        WicketCompletionAnalysis.at(SourceLocation(java.virtualFile.path,original.textOffset)) {
            assertTrue(WicketCompletionAnalysis.allowsEmpty(original))
            assertFalse(WicketCompletionAnalysis.allowsEmpty(copied))
        }
        assertFalse(WicketCompletionAnalysis.active)
        assertFalse(WicketCompletionAnalysis.allowsEmpty(original))
    }
}
