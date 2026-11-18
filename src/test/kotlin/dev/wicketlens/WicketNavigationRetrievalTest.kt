package dev.wicketlens

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.progress.*
import com.intellij.psi.*
import dev.wicketlens.mcp.*
import dev.wicketlens.model.*

class WicketNavigationRetrievalTest : WicketProjectPsiTestCase() {
    private val query = WicketPageAnalysisRequest()
    private fun fixture():PageAnalysisSnapshot {
        WicketContentFactoryFixture.install(myFixture)
        return query.capture(project,"example.FactoryPage",view=WicketMcpReport.NAVIGATION)
    }
    private fun response(s:PageAnalysisSnapshot,id:String?=null) = JsonParser.parseString(query.render(s,WicketMcpReport.NAVIGATION,contextId=id)).asJsonObject
    private fun contexts(s:PageAnalysisSnapshot) = response(s)["navigationContexts"].asJsonObject["contexts"].asJsonArray
    private fun firstId(s:PageAnalysisSnapshot) = contexts(s).first().asJsonObject["contextId"].asString
    private fun rejected(code:String,action:()->Unit) {try {action();fail("Expected $code")}catch(f:PageAnalysisFailure){assertEquals(f.message,code,f.code)}}

    fun testIndexOmitsPrimaryAndContextGraphsButListsEveryNativeContext() {
        val s=fixture();val result=response(s);val index=result["navigationContexts"].asJsonObject
        assertFalse(result.has("analysis"));assertEquals("index",index["mode"].asString)
        assertEquals(s.navigationContexts!!.contexts.size,index["contextCount"].asInt)
        assertEquals(index["contextCount"].asInt,index["contexts"].asJsonArray.size())
        assertFalse(result["truncated"].asBoolean)
        index["contexts"].asJsonArray.zip(s.navigationContexts.contexts).forEach { (item,native) ->
            val descriptor=item.asJsonObject
            assertFalse(descriptor.has("bindings"));assertFalse(descriptor.has("scopes"))
            assertEquals(native.bindingNodeIds.size,descriptor["bindingCount"].asInt)
            assertEquals(native.relationship,descriptor["relationship"].asString)
        }
    }
    fun testEverySelectionIsTheExactLegacyNativeProjectionWithOnlyItsIdAdded() {
        val s=fixture();val descriptors=contexts(s)
        descriptors.forEachIndexed { i,item ->
            val id=item.asJsonObject["contextId"].asString
            val result=response(s,id)["navigationContexts"].asJsonObject
            assertEquals("context",result["mode"].asString);assertEquals(descriptors.size(),result["contextCount"].asInt)
            assertEquals(1,result["contexts"].asJsonArray.size())
            val selected=result["contexts"].asJsonArray.single().asJsonObject
            assertEquals(id,selected.remove("contextId").asString)
            assertEquals(WicketNavigationExport.context(s.navigationContexts!!.contexts[i]),selected)
        }
    }
    fun testFactoryContextKeepsItsInferredScopeProvenanceAndUnevaluatedCallbacks() {
        val s=fixture();val id=contexts(s).single {it.asJsonObject["relationship"].asString=="FACTORY_TEMPLATE"}.asJsonObject["contextId"].asString
        val selected=response(s,id)["navigationContexts"].asJsonObject["contexts"].asJsonArray.single().asJsonObject
        assertEquals("not_evaluated",selected["lifecycle"].asJsonObject["contentFactory"].asJsonObject["callbackExecution"].asString)
        assertTrue(selected["bindings"].asJsonArray.all {b -> b.asJsonObject["findings"].asJsonArray.all {it.asJsonObject["confidence"].asString=="INFERRED"}})
    }
    fun testUnchangedRecapturesKeepTheSameIdsWithoutAServerSnapshotCache() {
        val s=fixture();val fresh=query.capture(project,"example.FactoryPage",view=WicketMcpReport.NAVIGATION)
        assertEquals(response(s),response(fresh));response(fresh,firstId(s));query.requireCurrent(project,fresh)
    }
    fun testDefaultAndOptionalLegacyStructureRemainUnchanged() {
        val s=fixture();val plain=query.capture(project,"example.FactoryPage")
        assertFalse(JsonParser.parseString(query.render(plain)).asJsonObject.has("navigationContexts"))
        val legacy=query.capture(project,"example.FactoryPage",includeNavigationContexts=true)
        assertEquals(JsonParser.parseString(query.render(plain)).asJsonObject["analysis"],JsonParser.parseString(query.render(legacy)).asJsonObject["analysis"])
        assertEquals(WicketNavigationExport.render(s.navigationContexts!!),JsonParser.parseString(query.render(legacy)).asJsonObject["navigationContexts"])
    }
    fun testMalformedSelectorsAreExplicitRequestErrors() {
        val s=fixture()
        for(id in listOf("","0","nav:bad:0","nav:"+"a".repeat(64)+":-1","nav:"+"a".repeat(64)+":00")) rejected("INVALID_REQUEST") {response(s,id)}
    }
    fun testContextIdsCannotBeUsedOnTheLegacyViews() {
        val s=fixture();rejected("INVALID_REQUEST") {query.render(s,contextId=firstId(s))}
    }
    fun testUnknownOrdinalDoesNotSelectAnotherContext() {
        val s=fixture();val id=firstId(s).substringBeforeLast(':')+":999999"
        rejected("CONTEXT_NOT_FOUND") {response(s,id)}
    }
    fun testProjectIdentityCannotReuseAnotherProjectsSelector() {
        val s=fixture();rejected("STALE_CONTEXT_SELECTION") {response(s.copy(navigationProjectScope="different-project"),firstId(s))}
    }
    fun testDifferentTargetAndModuleCannotReuseSelectors() {
        val s=fixture();val id=firstId(s)
        rejected("STALE_CONTEXT_SELECTION") {response(s.copy(moduleName="different-module"),id)}
        rejected("STALE_CONTEXT_SELECTION") {response(s.copy(sourceSet="test"),id)}
        val other=query.capture(project,"example.TableHost",view=WicketMcpReport.NAVIGATION)
        rejected("STALE_CONTEXT_SELECTION") {response(other,id)}
    }
    fun testUnsavedSourceInvalidatesSnapshotsAndCommittedEditsInvalidateIds() {
        val s=fixture();val id=firstId(s)
        val file=PsiManager.getInstance(project).findFile(myFixture.findFileInTempDir("src/main/java/example/FactoryPage.java"))!!
        WriteCommandAction.runWriteCommandAction(project) {PsiDocumentManager.getInstance(project).getDocument(file)!!.setText(file.text+"\n// edit")}
        rejected("STALE_SNAPSHOT") {query.requireCurrent(project,s)}
        PsiDocumentManager.getInstance(project).commitAllDocuments()
        val fresh=query.capture(project,"example.FactoryPage",view=WicketMcpReport.NAVIGATION)
        rejected("STALE_CONTEXT_SELECTION") {response(fresh,id)}
        response(fresh,firstId(fresh))
    }
    fun testRootAndSdkVersionChangesInvalidateIds() {
        val s=fixture();val id=firstId(s)
        rejected("STALE_CONTEXT_SELECTION") {response(s.copy(version=s.version.copy(rootsStamp=s.version.rootsStamp+1)),id)}
        val binary=s.version.sources.indexOfFirst {it.binary};assertTrue(binary>=0)
        val sources=s.version.sources.toMutableList();sources[binary]=sources[binary].copy(fileStamp=sources[binary].fileStamp+1)
        rejected("STALE_CONTEXT_SELECTION") {response(s.copy(version=s.version.copy(sources=sources)),id)}
    }
    fun testPartialDiscoveryRemainsExplicitForIndexAndSelection() {
        val original=fixture();val s=original.copy(navigationContexts=original.navigationContexts!!.copy(complete=false,diagnostics=listOf("Discovery bound reached")))
        assertTrue(response(s)["truncated"].asBoolean)
        val selected=response(s,firstId(s));assertTrue(selected["truncated"].asBoolean)
        assertFalse(selected["navigationContexts"].asJsonObject["discoveryComplete"].asBoolean)
    }
    fun testIndexDoesNotSerializeALargePrimaryGraphOrAnUnselectedLargeContext() {
        val original=fixture();val nav=original.navigationContexts!!
        val warning=Diagnostic(DiagnosticCode.PARTIAL_ANALYSIS,"界".repeat(200000))
        val large=nav.contexts.first().copy(report=nav.contexts.first().report.copy(warnings=listOf(warning)))
        val s=original.copy(report=original.report.copy(warnings=listOf(warning)),navigationContexts=nav.copy(contexts=listOf(large)+nav.contexts.drop(1)))
        assertTrue(query.render(s,WicketMcpReport.NAVIGATION).toByteArray().size<WicketPageAnalysisRequest.MAX_RESPONSE_BYTES)
        rejected("RESPONSE_TOO_LARGE") {response(s,firstId(s))}
        val small=contexts(s).last().asJsonObject["contextId"].asString
        response(s,small)
    }
    fun testLargePrimaryGraphDoesNotPreventAnIndexOrSmallContextSelection() {
        val original=fixture();val s=original.copy(report=original.report.copy(nodes=List(5001) {original.report.nodes.first()}))
        response(s,firstId(s))
        rejected("RESPONSE_TOO_LARGE") {query.render(s)}
    }
    fun testSelectedContextComponentLimitIsCheckedWithoutBlockingTheIndex() {
        val original=fixture();val nav=original.navigationContexts!!;val context=nav.contexts.first()
        val oversized=context.copy(report=context.report.copy(nodes=List(5001) {context.report.nodes.first()}))
        // Keep exactly one root for descriptor metadata.
        val nodes=listOf(context.report.nodes.single {it.nodeId==context.report.rootNodeId})+List(5000) {context.report.nodes.first {it.nodeId!=context.report.rootNodeId}}
        val s=original.copy(navigationContexts=nav.copy(contexts=listOf(oversized.copy(report=oversized.report.copy(nodes=nodes)))))
        rejected("RESPONSE_TOO_LARGE") {response(s,firstId(s))}
    }
    fun testIndexMetadataRetainsTheWholeByteLimit() {
        val original=fixture();val s=original.copy(navigationContexts=original.navigationContexts!!.copy(diagnostics=listOf("界".repeat(200000))))
        rejected("RESPONSE_TOO_LARGE") {response(s)}
    }
    fun testSelectionRetainsTheWholeByteLimitForWarnings() {
        val original=fixture();val nav=original.navigationContexts!!
        val altered=nav.contexts.map {it.copy(report=it.report.copy(warnings=listOf(Diagnostic(DiagnosticCode.PARTIAL_ANALYSIS,"x".repeat(600000)))))}
        val s=original.copy(navigationContexts=nav.copy(contexts=altered));rejected("RESPONSE_TOO_LARGE") {response(s,firstId(s))}
    }
    fun testRenderingPropagatesCancellation() {
        val s=fixture();val indicator=EmptyProgressIndicator()
        assertThrows(ProcessCanceledException::class.java) {ProgressManager.getInstance().runProcess(Runnable {indicator.cancel();response(s)},indicator)}
    }
    fun testIndexAndSelectionLeaveAllSourcesUnchanged() {
        val s=fixture();val files=s.version.sources.filterNot {it.binary}.mapNotNull {dev.wicketlens.analysis.WicketSourceFiles.find(project,it.path)}
        val before=files.map {it.modificationStamp to it.contentsToByteArray().toList()}
        contexts(s).forEach {response(s,it.asJsonObject["contextId"].asString)}
        query.requireCurrent(project,s)
        assertEquals(before,files.map {it.modificationStamp to it.contentsToByteArray().toList()})
    }
}
