package dev.wicketlens

import com.google.gson.JsonParser
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.psi.*
import dev.wicketlens.editor.*
import dev.wicketlens.mcp.*
import dev.wicketlens.model.*

class WicketRecordActionMcpTest : WicketProjectPsiTestCase() {
    private val query=WicketPageAnalysisRequest()
    private fun capture()=query.capture(project,"example.ConfirmationPage",view=WicketMcpReport.NAVIGATION)
    private fun response(s:PageAnalysisSnapshot,id:String?=null)=JsonParser.parseString(query.render(s,WicketMcpReport.NAVIGATION,contextId=id)).asJsonObject
    private fun file(path:String)=PsiManager.getInstance(project).findFile(myFixture.findFileInTempDir(path))!!
    private fun id(s:PageAnalysisSnapshot)=response(s)["navigationContexts"].asJsonObject["contexts"].asJsonArray.first {it.asJsonObject["relationship"].asString=="ACTION_TEMPLATE"}.asJsonObject["contextId"].asString
    fun testNativeLegacyAndSelectedActionContextsHaveTheSameValuesWithoutNewInitialBindings() {
        val java=WicketRecordActionFixture.install(myFixture)
        val files=listOf(java,file("src/main/java/example/TableBox.java"),file("src/main/java/example/SelectionBox.java"),file("src/main/resources/example/ConfirmationPage.html"))
        val before=files.map {it.text to it.modificationStamp}
        val s=capture();val plain=query.capture(project,"example.ConfirmationPage")
        assertEquals(plain.report,s.report)
        val actions=s.navigationContexts!!.contexts.filter {it.relationship=="ACTION_TEMPLATE"}
        assertEquals(2,actions.size)
        assertEquals(actions.map {it.report},WicketEditorAnalysis.navigationPages(java).filter {it.report.actionTemplate!=null}.map {it.report})
        val index=response(s)["navigationContexts"].asJsonObject["contexts"].asJsonArray
        actions.forEach {native ->
            val position=s.navigationContexts.contexts.indexOf(native)
            val selected=response(s,index[position].asJsonObject["contextId"].asString)["navigationContexts"].asJsonObject["contexts"].asJsonArray.single().asJsonObject
            selected.remove("contextId");assertEquals(WicketNavigationExport.context(native),selected)
            assertFalse(selected.has("submissionTemplate"))
            val action=selected["actionTemplate"].asJsonObject
            assertEquals("ACTION_TEMPLATE",action["phase"].asString);assertEquals("SOURCE_DECLARED_RECORD_ACTION",action["kind"].asString)
            assertEquals("not_reconstructed",action["uiTrigger"].asString)
            assertEquals("not_evaluated",action["callbackExecution"].asString)
            assertFalse(action.has("triggerNodeId"));assertFalse(action.has("formNodeId"))
            for(key in listOf("runtimePresence","runtimeSubclass","constructorEffects","configurationEffects","registrationDispatch","registrationEffects","callbackStorage","callbackReachability","authorization","visibility","rowValues","loopCounts","equalityEffects","predicateResults","enumInitialization","dataValues","mutationOutcome","businessOutcome","feedbackOutcome","modelEffects","laterTreeState")) assertEquals(key,"unknown",action[key].asString)
            val update=action["updates"].asJsonArray.single().asJsonObject
            assertEquals("java.util.List.replaceAll",update["collectionOperation"].asString)
            assertEquals("not_evaluated",update["recordConstruction"].asString)
            assertEquals("example.ConfirmationPage.State.PAID",update["componentArguments"].asJsonArray.last().asJsonObject["declarationName"].asString)
            assertEquals(1,selected["bindings"].asJsonArray.size())
            assertTrue(selected["bindings"].asJsonArray.all {it.asJsonObject["findings"].asJsonArray.single().asJsonObject["confidence"].asString=="INFERRED" && it.asJsonObject.has("ancestors")})
            val formatted=dev.wicketlens.ui.WicketReportFormatter().formatWithLinks(native.report)
            assertTrue(formatted.text.contains("UI dispatch"));assertTrue(formatted.text.contains("State.PAID"))
            assertEquals(1,Regex("MATCHED / INFERRED").findAll(formatted.text).count())
        }
        val legacy=query.capture(project,"example.ConfirmationPage",includeNavigationContexts=true)
        assertEquals(WicketNavigationExport.render(s.navigationContexts),JsonParser.parseString(query.render(legacy)).asJsonObject["navigationContexts"])
        assertEquals(query.render(plain,WicketMcpReport.VALIDATION),query.render(legacy,WicketMcpReport.VALIDATION))
        assertEquals(query.render(plain,WicketMcpReport.CONTEXT,"rows"),query.render(legacy,WicketMcpReport.CONTEXT,"rows"))
        assertFalse(JsonParser.parseString(query.render(plain)).asJsonObject["analysis"].asJsonObject.has("actionTemplate"))
        query.requireCurrent(project,s);assertEquals(before,files.map {it.text to it.modificationStamp})
    }
    private fun external(body:String=""):PsiJavaFile {
        myFixture.addFileToProject("src/main/java/example/Status.java","package example;public enum Status {OPEN,PAID}")
        myFixture.addFileToProject("src/main/java/example/InvoiceRow.java","""
            package example;public record InvoiceRow(String id,Status status) implements java.io.Serializable {
                public InvoiceRow paid() {$body return new InvoiceRow(id,Status.PAID);}
                @Override public boolean equals(Object other) {return other instanceof InvoiceRow r && r.id.equals(id);}
                @Override public int hashCode() {return id.hashCode();}
            }
        """)
        val original=WicketRecordActionFixture.page
        val page=original.removeRange(original.indexOf("public enum State"),original.indexOf("private final List<Row>"))
            .replace(Regex("\\bRow\\b"),"InvoiceRow")
        return WicketRecordActionFixture.install(myFixture,page,WicketSelectionClearFixture.table.replace("ConfirmationPage.Row","InvoiceRow"))
    }
    private fun stale(path:String,from:String,to:String,withheld:Boolean=false) {
        external(if(withheld) "System.gc();" else "")
        val s=capture();val selector=if(withheld) null else id(s)
        val source=file(path);assertTrue(s.version.sources.toString(),s.version.sources.any {it.path==source.virtualFile.path})
        assertTrue(source.text.contains(from))
        WriteCommandAction.runWriteCommandAction(project) {PsiDocumentManager.getInstance(project).getDocument(source)!!.setText(source.text.replace(from,to))}
        try {query.requireCurrent(project,s);fail("Expected stale snapshot")}catch(e:PageAnalysisFailure){assertEquals("STALE_SNAPSHOT",e.code)}
        if(selector!=null) {
            PsiDocumentManager.getInstance(project).commitAllDocuments();val fresh=capture()
            try {response(fresh,selector);fail("Expected stale selection")}catch(e:PageAnalysisFailure){assertEquals("STALE_CONTEXT_SELECTION",e.code)}
        }
    }
    fun testUnsavedHelperLoopInvalidatesSnapshotsAndSelectors()=stale("src/main/java/example/ConfirmationPage.java","current.equals(row)","row.equals(current)")
    fun testUnsavedRecordTransformInvalidatesSnapshotsAndSelectors()=stale("src/main/java/example/InvoiceRow.java","new InvoiceRow(id,Status.PAID)","new InvoiceRow(id,Status.OPEN)")
    fun testUnsavedEqualityBodyInvalidatesSnapshotsAndSelectors()=stale("src/main/java/example/InvoiceRow.java","r.id.equals(id)","false")
    fun testUnsavedEnumInvalidatesSnapshotsAndSelectors()=stale("src/main/java/example/Status.java","OPEN,PAID","OPEN,PAID,OTHER")
    fun testUnsavedSelectionHelperInvalidatesSnapshotsAndSelectors()=stale("src/main/java/example/TableBox.java","selectionColumn!=null","selectionColumn==null")
    fun testUnsavedTargetMarkupInvalidatesSnapshotsAndSelectors()=stale("src/main/resources/example/ConfirmationPage.html","'rows'","'changed'")
    fun testWithheldTransformRemainsAFreshnessDependency()=stale("src/main/java/example/InvoiceRow.java","System.gc();","System.gc();System.gc();",true)
    fun testProjectModuleAndSourceSetSelectorsCannotCrossActionContexts() {
        WicketRecordActionFixture.install(myFixture);val s=capture();val selector=id(s)
        for(other in listOf(s.copy(navigationProjectScope="other-project"),s.copy(moduleName="other"),s.copy(sourceSet="test"))) {
            try {response(other,selector);fail("Expected isolation")}catch(e:PageAnalysisFailure){assertEquals("STALE_CONTEXT_SELECTION",e.code)}
        }
    }
    fun testSelectedActionRetainsTheWholeResponseCap() {
        WicketRecordActionFixture.install(myFixture);val s=capture();val action=s.navigationContexts!!.contexts.first {it.report.actionTemplate!=null}
        val large=action.copy(report=action.report.copy(warnings=listOf(Diagnostic(DiagnosticCode.PARTIAL_ANALYSIS,"界".repeat(200000)))))
        val big=s.copy(navigationContexts=s.navigationContexts.copy(contexts=listOf(large)))
        try {response(big,id(big));fail("Expected whole response bound")}catch(e:PageAnalysisFailure){assertEquals("RESPONSE_TOO_LARGE",e.code)}
    }
    fun testActionTargetMarkupDoesNotAcquireRenameAuthority() {
        WicketRecordActionFixture.install(myFixture);val s=capture()
        s.navigationContexts!!.contexts.filter {it.report.actionTemplate!=null}.forEach {context ->
            assertTrue(context.report.nodes.all {it.template && !it.idRenameSafe})
        }
    }
}
