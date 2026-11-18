package dev.wicketlens

import com.google.gson.JsonParser
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.psi.*
import com.intellij.psi.util.PsiTreeUtil
import dev.wicketlens.editor.*
import dev.wicketlens.export.WicketContextExporter
import dev.wicketlens.mcp.*
import dev.wicketlens.model.*

class WicketConfirmationMcpTest : WicketProjectPsiTestCase() {
    private val query=WicketPageAnalysisRequest()
    private fun file(path:String)=PsiManager.getInstance(project).findFile(myFixture.findFileInTempDir(path))!!
    private fun response(s:PageAnalysisSnapshot,id:String?=null)=JsonParser.parseString(query.render(s,WicketMcpReport.NAVIGATION,contextId=id)).asJsonObject
    private fun selection(s:PageAnalysisSnapshot)=response(s)["navigationContexts"].asJsonObject["contexts"].asJsonArray.single {it.asJsonObject["relationship"].asString=="CONFIRMATION_TEMPLATE"}.asJsonObject["contextId"].asString
    fun testNativeLegacyAndSelectedContextsShareValuesWithoutChangingDefaultViewsOrFiles() {
        val java=WicketConfirmationFixture.install(myFixture)
        val files=listOf(java,file("src/main/java/example/ConfirmBox.java"),file("src/main/java/example/DialogBox.java"),file("src/main/java/example/Actions.java"),file("src/main/resources/example/DialogBox.html"),file("src/main/resources/example/ConfirmationPage.html"))
        val before=files.map {it.text to it.modificationStamp}
        val plain=query.capture(project,"example.ConfirmationPage")
        val snapshot=query.capture(project,"example.ConfirmationPage",includeNavigationContexts=true)
        assertEquals(WicketContextExporter().export(plain.report),WicketContextExporter().export(snapshot.report))
        val context=snapshot.navigationContexts!!.contexts.single {it.relationship=="CONFIRMATION_TEMPLATE"}
        assertEquals(WicketContextExporter().export(context.report),WicketContextExporter().export(WicketEditorAnalysis.navigationPages(java).single {it.report.submissionTemplate?.confirmation!=null}.report))
        val selected=response(snapshot,selection(snapshot))["navigationContexts"].asJsonObject["contexts"].asJsonArray.single().asJsonObject
        selected.remove("contextId");assertEquals(WicketNavigationExport.context(context),selected)
        val metadata=selected["submissionTemplate"].asJsonObject
        assertEquals("SOURCE_DECLARED_CONFIRMATION",metadata["kind"].asString)
        assertEquals("CONFIRMATION_TEMPLATE",metadata["phase"].asString)
        assertEquals("SOURCE_STORED_CONSUMER_ON_SUBMIT_ROUTE",metadata["dispatchCondition"].asString)
        assertEquals("not_evaluated",metadata["callbackExecution"].asString)
        val confirmation=metadata["confirmation"].asJsonObject
        for(key in listOf("initiatingDispatch","currentRegistration","slotPresence","cleanupReachability","rows","dataMutationOutcome","equalsEffects")) assertEquals(key,"unknown",confirmation[key].asString)
        assertEquals("removeAll",confirmation["dataCalls"].asJsonArray.single().asJsonObject["mutationMethod"].asString)
        assertEquals(5,selected["bindings"].asJsonArray.size())
        assertTrue(selected["bindings"].asJsonArray.all {it.asJsonObject["findings"].asJsonArray.single().asJsonObject["confidence"].asString=="INFERRED"})
        val legacy=JsonParser.parseString(query.render(snapshot)).asJsonObject["navigationContexts"].asJsonObject["contexts"].asJsonArray.single {it.asJsonObject["relationship"].asString=="CONFIRMATION_TEMPLATE"}
        assertEquals(selected,legacy)
        assertEquals(query.render(plain,WicketMcpReport.VALIDATION),query.render(snapshot,WicketMcpReport.VALIDATION))
        assertEquals(query.render(plain,WicketMcpReport.CONTEXT,"rows"),query.render(snapshot,WicketMcpReport.CONTEXT,"rows"))
        query.requireCurrent(project,snapshot);assertEquals(before,files.map {it.text to it.modificationStamp})
    }
    private fun stale(path:String,from:String,to:String,withheld:Boolean=false) {
        WicketConfirmationFixture.install(myFixture,body=if(withheld) WicketConfirmationFixture.page.replace("t.add(table);","table.removeAll();t.add(table);") else WicketConfirmationFixture.page)
        val snapshot=query.capture(project,"example.ConfirmationPage",view=WicketMcpReport.NAVIGATION)
        val old=if(withheld) null else selection(snapshot)
        val source=file(path);assertTrue(snapshot.version.sources.any {it.path==source.virtualFile.path})
        WriteCommandAction.runWriteCommandAction(project) {PsiDocumentManager.getInstance(project).getDocument(source)!!.setText(source.text.replace(from,to))}
        try {query.requireCurrent(project,snapshot);fail("Expected stale snapshot")}catch(e:PageAnalysisFailure){assertEquals("STALE_SNAPSHOT",e.code)}
        if(old!=null) {
            PsiDocumentManager.getInstance(project).commitAllDocuments()
            val fresh=query.capture(project,"example.ConfirmationPage",view=WicketMcpReport.NAVIGATION)
            try {response(fresh,old);fail("Expected stale selection")}catch(e:PageAnalysisFailure){assertEquals("STALE_CONTEXT_SELECTION",e.code)}
        }
    }
    fun testUnsavedConsumerSlotGuardInvalidatesSnapshots()=stale("src/main/java/example/ConfirmBox.java","pending!=null","pending==null")
    fun testUnsavedDataHelperInvalidatesSnapshots()=stale("src/main/java/example/ConfirmationPage.java","records.removeAll(rows);","records.clear();")
    fun testUnsavedInitiatingRegistrationInvalidatesSnapshots()=stale("src/main/java/example/Actions.java","action=handler;","action=null;")
    fun testUnsavedSubmitBridgeInvalidatesSnapshots()=stale("src/main/java/example/DialogBox.java","onConfirm(target);","if(target!=null) onConfirm(target);")
    fun testUnsavedMarkupInvalidatesSnapshots()=stale("src/main/resources/example/DialogBox.html","'confirm'","'changed'")
    fun testWithheldProtocolSourcesStayFresh()=stale("src/main/java/example/ConfirmBox.java","pending!=null","pending==null",true)
    fun testProjectAndModuleSelectorsCannotCrossConfirmationContexts() {
        WicketConfirmationFixture.install(myFixture);val s=query.capture(project,"example.ConfirmationPage",view=WicketMcpReport.NAVIGATION);val id=selection(s)
        for(other in listOf(s.copy(navigationProjectScope="other"),s.copy(moduleName="other"),s.copy(sourceSet="test"))) {
            try {response(other,id);fail("Expected project/module isolation")}catch(e:PageAnalysisFailure){assertEquals("STALE_CONTEXT_SELECTION",e.code)}
        }
    }
    fun testConfirmationResponseRetainsTheWholeResponseCap() {
        WicketConfirmationFixture.install(myFixture);val s=query.capture(project,"example.ConfirmationPage",view=WicketMcpReport.NAVIGATION)
        val nav=s.navigationContexts!!;val context=nav.contexts.single {it.relationship=="CONFIRMATION_TEMPLATE"}
        val large=context.copy(report=context.report.copy(warnings=listOf(Diagnostic(DiagnosticCode.PARTIAL_ANALYSIS,"x".repeat(600000)))))
        val big=s.copy(navigationContexts=nav.copy(contexts=listOf(large)))
        try {response(big,selection(big));fail("Expected response bound")}catch(e:PageAnalysisFailure){assertEquals("RESPONSE_TOO_LARGE",e.code)}
    }
    fun testTriggerNavigatesToTheLambdaWithoutRenameAuthority() {
        WicketConfirmationFixture.install(myFixture);val host=file("src/main/java/example/ConfirmBox.java")
        val hook=PsiTreeUtil.findChildrenOfType(host,PsiMethod::class.java).single {it.name=="onConfirm"}
        val trigger=WicketSubmissionNavigation.targets(hook).single()
        val reverse=WicketSubmissionNavigation.targets(trigger)
        assertEquals(1,reverse.size);assertTrue(reverse.single() is PsiParameterList)
        assertTrue(reverse.single().parent is PsiLambdaExpression)
        assertNull(WicketIdRenameTarget.at(trigger.containingFile,trigger.textOffset))
        val report=query.capture(project,"example.ConfirmationPage",view=WicketMcpReport.NAVIGATION).navigationContexts!!.contexts.single {it.relationship=="CONFIRMATION_TEMPLATE"}.report
        val text=dev.wicketlens.ui.WicketReportFormatter().formatWithLinks(report)
        assertTrue(text.text.contains("conditional stored confirmation"));assertTrue(text.text.contains("outcome unknown"))
    }
}
