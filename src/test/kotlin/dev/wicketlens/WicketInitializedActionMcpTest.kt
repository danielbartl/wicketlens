package dev.wicketlens

import com.google.gson.JsonParser
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.psi.*
import dev.wicketlens.export.WicketContextExporter
import dev.wicketlens.mcp.*
import dev.wicketlens.model.*

class WicketInitializedActionMcpTest : WicketProjectPsiTestCase() {
    private val query=WicketPageAnalysisRequest()
    private fun capture()=query.capture(project,"example.ConfirmationPage",view=WicketMcpReport.NAVIGATION)
    private fun response(s:PageAnalysisSnapshot,id:String?=null)=JsonParser.parseString(query.render(s,WicketMcpReport.NAVIGATION,contextId=id)).asJsonObject
    private fun file(path:String)=PsiManager.getInstance(project).findFile(myFixture.findFileInTempDir(path))!!
    private fun ids(s:PageAnalysisSnapshot)=response(s)["navigationContexts"].asJsonObject["contexts"].asJsonArray.filter {it.asJsonObject["relationship"].asString=="ACTION_TEMPLATE"}.map {it.asJsonObject["contextId"].asString}
    fun testInitializedStorageAndIndependentActionsUseTheSameNativeAndMcpFactsWithoutWrites() {
        val java=WicketRecordActionFixture.install(myFixture,WicketRecordActionFixture.initializedPage)
        val files=listOf(java,file("src/main/java/example/Actions.java"),file("src/main/resources/example/ConfirmationPage.html"));val before=files.map {it.text to it.modificationStamp}
        val s=capture();val primary=query.capture(project,"example.ConfirmationPage")
        assertEquals(primary.report,s.report)
        val native=JsonParser.parseString(WicketContextExporter().export(primary.report)).asJsonObject
        assertEquals(native,JsonParser.parseString(query.render(primary)).asJsonObject["analysis"])
        val stored=native["storedActionRegistrations"].asJsonObject
        assertEquals(2,stored["registrations"].asJsonArray.size())
        val sites=stored["registrations"].asJsonArray.map {it.asJsonObject["registrationLocation"].asJsonObject["offset"].asInt}
        assertEquals(2,sites.distinct().size)
        assertTrue(sites.all {java.text.substring(it).startsWith("addAction")})
        for(key in listOf("activeRegistration","dispatch","visibility","laterTreeState","businessOutcome")) assertEquals(key,"unknown",stored[key].asString)
        val component=JsonParser.parseString(query.render(primary,WicketMcpReport.CONTEXT,"rows")).asJsonObject["context"].asJsonObject
        assertEquals(stored,component["storedActionRegistrations"])
        assertEquals("INFERRED",component["findings"].asJsonArray.single().asJsonObject["confidence"].asString)
        val actions=s.navigationContexts!!.contexts.filter {it.report.actionTemplate!=null}
        assertEquals(2,actions.size);assertEquals(2,ids(s).distinct().size)
        for((index,id) in ids(s).withIndex()) {
            val selected=response(s,id)["navigationContexts"].asJsonObject["contexts"].asJsonArray.single().asJsonObject
            selected.remove("contextId");assertEquals(WicketNavigationExport.context(actions[index]),selected)
            val metadata=actions[index].report.actionTemplate!!
            assertTrue(primary.report.storedActionRegistrations.any {it.callbackLocation==metadata.callbackLocation && it.registrationLocation==metadata.registrationLocation})
            assertEquals(1,selected["bindings"].asJsonArray.size())
            assertEquals("unknown",selected["actionTemplate"].asJsonObject["callbackStorage"].asString)
        }
        query.requireCurrent(project,s);assertEquals(before,files.map {it.text to it.modificationStamp})
    }
    private fun stale(path:String,from:String,to:String,withheld:Boolean=false) {
        val body=if(withheld) WicketRecordActionFixture.initializedPage.replace("}).addAction","});actions.click(null,null);actions.addAction") else WicketRecordActionFixture.initializedPage
        WicketRecordActionFixture.install(myFixture,body)
        val s=capture();val selectors=ids(s)
        assertEquals(if(withheld) 0 else 2,selectors.size)
        val source=file(path);assertTrue(s.version.sources.any {it.path==source.virtualFile.path});assertTrue(source.text.contains(from))
        WriteCommandAction.runWriteCommandAction(project) {PsiDocumentManager.getInstance(project).getDocument(source)!!.setText(source.text.replace(from,to))}
        try {query.requireCurrent(project,s);fail("Expected stale group snapshot")}catch(e:PageAnalysisFailure){assertEquals("STALE_SNAPSHOT",e.code)}
        PsiDocumentManager.getInstance(project).commitAllDocuments()
        val fresh=capture()
        for(selector in selectors) try {response(fresh,selector);fail("Expected stale sibling selector")}catch(e:PageAnalysisFailure){assertEquals("STALE_CONTEXT_SELECTION",e.code)}
    }
    fun testChangingOneRegistrationInvalidatesBothActionSelectors()=stale("src/main/java/example/ConfirmationPage.java","\"First\"","\"Changed\"")
    fun testLaterEagerUseInvalidatesTheWholeSnapshotAndBothSelectors()=stale("src/main/java/example/ConfirmationPage.java","}).addAction","});actions.click(null,null);actions.addAction")
    fun testAllocationEditInvalidatesBothSelectors()=stale("src/main/java/example/ConfirmationPage.java","new Actions<Row>()","new Actions<Row>() {}")
    fun testReceiverReturnEditInvalidatesBothSelectors()=stale("src/main/java/example/Actions.java","return this;","return new Actions<>();")
    fun testSharedStorageMethodEditInvalidatesBothSelectors()=stale("src/main/java/example/Actions.java","action=handler;","handler.accept(null,null);action=handler;")
    fun testTargetMarkupEditInvalidatesBothSelectors()=stale("src/main/resources/example/ConfirmationPage.html","'rows'","'changed'")
    fun testRejectedGroupRetainsRegistrarSourceFreshness()=stale("src/main/java/example/Actions.java","action=handler;","this.action=handler;",true)
    fun testEachInitializedActionSelectorStaysProjectModuleAndSourceSetIsolated() {
        WicketRecordActionFixture.install(myFixture,WicketRecordActionFixture.initializedPage);val s=capture()
        for(selector in ids(s)) for(other in listOf(s.copy(navigationProjectScope="other-project"),s.copy(moduleName="other-module"),s.copy(sourceSet="test"))) {
            try {response(other,selector);fail("Expected isolation")}catch(e:PageAnalysisFailure){assertEquals("STALE_CONTEXT_SELECTION",e.code)}
        }
    }
    fun testInitializedMetadataAndSelectedActionKeepTheWholeResponseCap() {
        WicketRecordActionFixture.install(myFixture,WicketRecordActionFixture.initializedPage);val s=capture()
        val last=s.report.storedActionRegistrations.last()
        val huge=s.copy(report=s.report.copy(storedActionRegistrations=s.report.storedActionRegistrations.dropLast(1)+last.copy(slotLocation=SourceLocation("界".repeat(200000),0))))
        try {query.render(huge);fail("Expected metadata response bound")}catch(e:PageAnalysisFailure){assertEquals("RESPONSE_TOO_LARGE",e.code)}
        val action=s.navigationContexts!!.contexts.first {it.report.actionTemplate!=null}
        val large=action.copy(report=action.report.copy(warnings=listOf(Diagnostic(DiagnosticCode.PARTIAL_ANALYSIS,"界".repeat(200000)))))
        val big=s.copy(navigationContexts=s.navigationContexts.copy(contexts=listOf(large)))
        try {response(big,ids(big).single());fail("Expected selected response bound")}catch(e:PageAnalysisFailure){assertEquals("RESPONSE_TOO_LARGE",e.code)}
    }
}
