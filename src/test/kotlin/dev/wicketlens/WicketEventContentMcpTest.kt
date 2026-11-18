package dev.wicketlens

import com.google.gson.JsonParser
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.psi.*
import dev.wicketlens.editor.*
import dev.wicketlens.export.WicketContextExporter
import dev.wicketlens.mcp.*
import dev.wicketlens.model.*

class WicketEventContentMcpTest:WicketProjectPsiTestCase() {
    private fun file(path:String)=PsiManager.getInstance(project).findFile(myFixture.findFileInTempDir(path))!!
    fun testOptionalEventContextSharesNativeValuesWithoutChangingInitialSelectors() {
        val java=WicketEventContentFixture.install(myFixture)
        val files=listOf(java,file("src/main/java/example/DialogBox.java"),file("src/main/java/example/DrawerBox.java"),
            file("src/main/java/example/DetailList.java"),file("src/main/resources/example/EventPage.html"),file("src/main/resources/example/DialogBox.html"))
        val before=files.map { it.text to it.modificationStamp }
        val query=WicketPageAnalysisRequest();val plain=query.capture(project,"example.EventPage")
        val snapshot=query.capture(project,"example.EventPage",includeNavigationContexts=true)
        assertEquals(WicketContextExporter().export(plain.report),WicketContextExporter().export(snapshot.report))
        val structure=JsonParser.parseString(query.render(snapshot)).asJsonObject
        val context=snapshot.navigationContexts!!.contexts.single { it.report.lifecycle?.directContent!=null }
        assertEquals("EVENT_TEMPLATE",context.relationship)
        val native=WicketEditorAnalysis.navigationPages(java).single { it.report.lifecycle?.directContent!=null }.report
        assertEquals(WicketContextExporter().export(native),WicketContextExporter().export(context.report))
        val exported=structure["navigationContexts"].asJsonObject["contexts"].asJsonArray.single { it.asJsonObject["lifecycle"]?.asJsonObject?.get("directContent")!=null }.asJsonObject
        val lifecycle=exported["lifecycle"].asJsonObject
        assertEquals("SOURCE_DECLARED_EVENT_CONTENT",lifecycle["kind"].asString);assertEquals("EVENT_TEMPLATE",lifecycle["phase"].asString)
        val placement=lifecycle["directContent"].asJsonObject
        assertEquals("rows:details",placement["triggerComponentPath"].asString)
        assertEquals("SYMBOLIC_ITEMS",placement["triggerPathScope"].asString)
        assertEquals("PAGE_DECLARED_ATTACHMENT",placement["pathScope"].asString)
        for(key in listOf("runtimeRowIds","rowCount","modelValues","validation","visibility","openCloseState","otherEventEffects","laterTreeState")) assertEquals(key,"unknown",placement[key].asString)
        for(key in listOf("callbackExecution","configurationExecution")) assertEquals("not_evaluated",placement[key].asString)
        assertEquals(2,placement["transferLocations"].asJsonArray.size())
        assertEquals(5,exported["bindings"].asJsonArray.size())
        assertTrue(exported["bindings"].asJsonArray.all { it.asJsonObject["findings"].asJsonArray.single().asJsonObject["confidence"].asString=="INFERRED" && it.asJsonObject.has("ancestors") })
        assertEquals(query.render(plain,WicketMcpReport.VALIDATION),query.render(snapshot,WicketMcpReport.VALIDATION))
        assertEquals(query.render(plain,WicketMcpReport.CONTEXT,"detailsDrawer:dialog:form:content"),query.render(snapshot,WicketMcpReport.CONTEXT,"detailsDrawer:dialog:form:content"))
        try { query.render(snapshot,WicketMcpReport.CONTEXT,"detailsDrawer:dialog:form:content:list");fail("Event-only paths cannot select initial components") }
        catch(e:PageAnalysisFailure) { assertTrue(e.code,e.code in setOf("COMPONENT_NOT_FOUND","UNRESOLVED_COMPONENT_PATH")) }
        query.requireCurrent(project,snapshot);assertEquals(before,files.map { it.text to it.modificationStamp })
    }
    private fun stale(path:String,before:String,after:String,eligible:Boolean=true) {
        WicketEventContentFixture.install(myFixture)
        val source=file(path)
        if(!eligible) WriteCommandAction.runWriteCommandAction(project) {
            PsiDocumentManager.getInstance(project).getDocument(source)!!.setText(source.text.replace(before,after))
            PsiDocumentManager.getInstance(project).commitAllDocuments()
        }
        val query=WicketPageAnalysisRequest();val snapshot=query.capture(project,"example.EventPage",includeNavigationContexts=true)
        assertTrue(snapshot.version.sources.toString(),snapshot.version.sources.any { it.path==source.virtualFile.path })
        WriteCommandAction.runWriteCommandAction(project) { PsiDocumentManager.getInstance(project).getDocument(source)!!.setText(source.text.replace(if(eligible) before else after,if(eligible) after else before)) }
        try { query.requireCurrent(project,snapshot);fail("Expected stale event snapshot") } catch(e:PageAnalysisFailure) { assertEquals("STALE_SNAPSHOT",e.code) }
    }
    fun testUnsavedHandlerInvalidatesSnapshot()=stale("src/main/java/example/EventPage.java","details.open(content,target);","details.open(content,null);")
    fun testUnsavedTransferInvalidatesSnapshot()=stale("src/main/java/example/DialogBox.java","setContent(content);open=true;","setContent(new WebMarkupContainer(CONTENT_ID));open=true;")
    fun testUnsavedSetterOverrideInvalidatesSnapshot()=stale("src/main/java/example/DrawerBox.java","super.setContent(content);","")
    fun testUnsavedFluentHelperInvalidatesSnapshot()=stale("src/main/java/example/DetailList.java","return addItem(property);","return new DetailList(\"other\");")
    fun testWithheldFluentSourceStillInvalidatesSnapshot()=stale("src/main/java/example/DetailList.java","return addItem(property);","return new DetailList(\"other\");",false)
    fun testUnsavedFragmentDefinitionInvalidatesSnapshot()=stale("src/main/resources/example/EventPage.html","wicket:id='list'","wicket:id='other'")
    fun testWholeEventResponseIsBounded() {
        WicketEventContentFixture.install(myFixture);val query=WicketPageAnalysisRequest();val snapshot=query.capture(project,"example.EventPage",includeNavigationContexts=true)
        val navigation=snapshot.navigationContexts!!;val event=navigation.contexts.single { it.report.lifecycle?.directContent!=null }
        val large=event.copy(report=event.report.copy(warnings=listOf(Diagnostic(DiagnosticCode.PARTIAL_ANALYSIS,"x".repeat(600000)))))
        try { query.render(snapshot.copy(navigationContexts=navigation.copy(contexts=listOf(large))));fail("Expected response bound") }
        catch(e:PageAnalysisFailure) { assertEquals("RESPONSE_TOO_LARGE",e.code) }
    }
    fun testOptionalBindingBudgetReportsIncompleteDiscovery() {
        val java=WicketEventContentFixture.install(myFixture);val primary=dev.wicketlens.analysis.WicketPageAnalyzer().analyze(java.classes.single())!!
        val result=dev.wicketlens.analysis.WicketNavigationContextsAnalyzer(dev.wicketlens.analysis.WicketNavigationContextsAnalyzer.Limits(bindings=1)).analyze(java.classes.single(),primary)
        assertFalse(result.complete);assertTrue(result.contexts.none { it.report.lifecycle?.directContent!=null });assertTrue(result.diagnostics.isNotEmpty())
    }
}
