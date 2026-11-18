package dev.wicketlens

import com.google.gson.JsonParser
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.psi.*
import dev.wicketlens.export.WicketContextExporter
import dev.wicketlens.mcp.*
import dev.wicketlens.model.*

class WicketStoredActionMcpTest : WicketProjectPsiTestCase() {
    private val query=WicketPageAnalysisRequest()
    private fun capture()=query.capture(project,"example.ConfirmationPage",view=WicketMcpReport.NAVIGATION)
    private fun file(path:String)=PsiManager.getInstance(project).findFile(myFixture.findFileInTempDir(path))!!
    private fun response(s:PageAnalysisSnapshot,id:String?=null)=JsonParser.parseString(query.render(s,WicketMcpReport.NAVIGATION,contextId=id)).asJsonObject
    private fun id(s:PageAnalysisSnapshot)=response(s)["navigationContexts"].asJsonObject["contexts"].asJsonArray.single {it.asJsonObject["relationship"].asString=="ACTION_TEMPLATE"}.asJsonObject["contextId"].asString
    fun testInitialStorageAndSeparateActionAreSharedByNativeAndMcpWithoutWrites() {
        val java=WicketRecordActionFixture.install(myFixture,WicketRecordActionFixture.plainPage)
        val files=listOf(java,file("src/main/java/example/Actions.java"),file("src/main/resources/example/ConfirmationPage.html"))
        val before=files.map {it.text to it.modificationStamp}
        val s=capture();val primary=query.capture(project,"example.ConfirmationPage")
        assertEquals(primary.report,s.report)
        val native=JsonParser.parseString(WicketContextExporter().export(primary.report)).asJsonObject
        val structure=JsonParser.parseString(query.render(primary)).asJsonObject["analysis"].asJsonObject
        assertEquals(native,structure)
        val proof=structure["storedActionRegistrations"].asJsonObject
        assertEquals("SOURCE_PROVED_CALLBACK_STORAGE",proof["kind"].asString)
        assertEquals("not_evaluated",proof["callbackExecution"].asString)
        for(key in listOf("activeRegistration","dispatch","visibility","laterTreeState","businessOutcome")) assertEquals(key,"unknown",proof[key].asString)
        assertEquals(1,proof["registrations"].asJsonArray.size())
        val component=JsonParser.parseString(query.render(primary,WicketMcpReport.CONTEXT,"rows")).asJsonObject["context"].asJsonObject
        assertEquals(proof,component["storedActionRegistrations"])
        assertEquals("INFERRED",component["findings"].asJsonArray.single().asJsonObject["confidence"].asString)
        val selected=response(s,id(s))["navigationContexts"].asJsonObject["contexts"].asJsonArray.single().asJsonObject
        selected.remove("contextId")
        assertEquals(WicketNavigationExport.context(s.navigationContexts!!.contexts.single {it.report.actionTemplate!=null}),selected)
        assertEquals(1,selected["bindings"].asJsonArray.size())
        assertEquals(before,files.map {it.text to it.modificationStamp});query.requireCurrent(project,s)
    }
    private fun stale(path:String,from:String,to:String,withheld:Boolean=false) {
        WicketRecordActionFixture.install(myFixture,WicketRecordActionFixture.plainPage)
        val source=file(path)
        if(withheld) {
            WriteCommandAction.runWriteCommandAction(project) {PsiDocumentManager.getInstance(project).getDocument(source)!!.setText(source.text.replace("action=handler;","handler.accept(null,null);action=handler;"))}
            PsiDocumentManager.getInstance(project).commitAllDocuments()
        }
        val s=capture();val selector=if(withheld) null else id(s)
        assertTrue(s.version.sources.toString(),s.version.sources.any {it.path==source.virtualFile.path})
        assertTrue(source.text.contains(from))
        WriteCommandAction.runWriteCommandAction(project) {PsiDocumentManager.getInstance(project).getDocument(source)!!.setText(source.text.replace(from,to))}
        try {query.requireCurrent(project,s);fail("Expected stale snapshot")}catch(e:PageAnalysisFailure){assertEquals("STALE_SNAPSHOT",e.code)}
        PsiDocumentManager.getInstance(project).commitAllDocuments()
        if(selector!=null) try {response(capture(),selector);fail("Expected stale context")}catch(e:PageAnalysisFailure){assertEquals("STALE_CONTEXT_SELECTION",e.code)}
    }
    fun testUnsavedStorageBodyInvalidatesInitialFactsAndActionSelectors()=stale("src/main/java/example/Actions.java","action=handler;","handler.accept(null,null);action=handler;")
    fun testUnsavedRegistrarConstructionInvalidatesFacts()=stale("src/main/java/example/Actions.java","public Actions() {}","public Actions() {System.gc();}")
    fun testUnsavedTargetMarkupInvalidatesFacts()=stale("src/main/resources/example/ConfirmationPage.html","'rows'","'changed'")
    fun testRejectedEagerRegistrarRemainsAFreshnessDependency()=stale("src/main/java/example/Actions.java","handler.accept(null,null);","",true)
    fun testSourceSelectorsStayIsolated() {
        WicketRecordActionFixture.install(myFixture,WicketRecordActionFixture.plainPage);val s=capture();val selector=id(s)
        for(other in listOf(s.copy(navigationProjectScope="another-project"),s.copy(moduleName="another-module"),s.copy(sourceSet="test"))) {
            try {response(other,selector);fail("Expected isolation")}catch(e:PageAnalysisFailure){assertEquals("STALE_CONTEXT_SELECTION",e.code)}
        }
    }
    fun testStorageMetadataUsesTheWholeResponseLimit() {
        WicketRecordActionFixture.install(myFixture,WicketRecordActionFixture.plainPage);val s=capture()
        val registration=s.report.storedActionRegistrations.single()
        val huge=s.copy(report=s.report.copy(storedActionRegistrations=listOf(registration.copy(slotLocation=SourceLocation("界".repeat(200000),0)))))
        try {query.render(huge);fail("Expected response limit")}catch(e:PageAnalysisFailure){assertEquals("RESPONSE_TOO_LARGE",e.code)}
    }
}
