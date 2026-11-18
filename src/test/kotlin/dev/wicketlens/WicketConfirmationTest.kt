package dev.wicketlens

import com.intellij.openapi.progress.*
import dev.wicketlens.analysis.*
import dev.wicketlens.export.WicketContextExporter
import dev.wicketlens.model.*

class WicketConfirmationTest : WicketProjectPsiTestCase() {
    private fun inspectConfirmation(body:String=WicketConfirmationFixture.page,host:String=WicketConfirmationFixture.host,base:String=WicketConfirmationFixture.base,
        limits:WicketSubmissionContexts.Limits=WicketSubmissionContexts.Limits()):Pair<PageReport,WicketConfirmationContexts.Result> {
        val java=WicketConfirmationFixture.install(myFixture,body,host,base)
        val initial=WicketPageAnalyzer().analyze(java.classes.single())!!
        return initial to WicketConfirmationContexts(limits).analyze(java.classes.single(),initial)
    }
    fun testStoredConsumerHasASeparateConditionalRouteAndSourceDataDeclaration() {
        val (initial,result)=inspectConfirmation()
        assertEquals(result.diagnostics.toString(),1,result.reports.size)
        val report=result.reports.single();val submission=report.submissionTemplate!!;val route=submission.confirmation!!
        assertEquals("confirm:dialog:form:confirm",submission.triggerComponentPath)
        assertEquals(listOf("SOURCE_DATA_CALL","FEEDBACK","AJAX_REFRESH"),submission.effects.map {it.kind})
        assertEquals("rows",submission.effects.last().targetComponentPath)
        assertEquals("removeAll",route.dataCalls.single().mutationMethod)
        assertEquals(2,route.setterLocations.size)
        assertEquals(5,report.matching!!.findings.size)
        assertTrue(report.matching.findings.all {it.code==DiagnosticCode.MATCHED && it.confidence==Confidence.INFERRED})
        assertTrue(report.nodes.all {it.template && !it.idRenameSafe})
        assertNull(initial.submissionTemplate)
        assertTrue(result.complete)
        assertTrue(result.sources.any {it.endsWith("Actions.java")})
        val primary=WicketContextExporter().export(initial)
        assertFalse(primary.contains("SOURCE_DATA_CALL"))
    }
    private fun withheld(body:String=WicketConfirmationFixture.page,host:String=WicketConfirmationFixture.host,base:String=WicketConfirmationFixture.base) {
        val (_,result)=inspectConfirmation(body,host,base)
        assertTrue(result.diagnostics.toString(),result.reports.isEmpty())
    }
    fun testWrongSlotGuardIsWithheld()=withheld(host=WicketConfirmationFixture.host.replace("pending!=null","pending==null"))
    fun testMissingCaptureClearIsWithheld()=withheld(host=WicketConfirmationFixture.host.replace("var pending=action;action=null;","var pending=action;"))
    fun testAdditionalSlotReadsAreWithheld()=withheld(host=WicketConfirmationFixture.host.replace("@Override protected void onClose", "public Object peek() {return action;} @Override protected void onClose"))
    fun testAdditionalSlotWritesAreWithheld()=withheld(host=WicketConfirmationFixture.host.replace("@Override protected void onClose", "public void forget() {action=null;} @Override protected void onClose"))
    fun testPublicSlotIsWithheld()=withheld(host=WicketConfirmationFixture.host.replace("private org.danekja", "public org.danekja"))
    fun testExplicitSlotInitializerIsWithheld()=withheld(host=WicketConfirmationFixture.host.replace("action;", "action=null;"))
    fun testWrongDispatchTargetIsWithheld()=withheld(host=WicketConfirmationFixture.host.replace("pending.accept(target)","pending.accept(null)"))
    fun testConditionalBridgeIsWithheld()=withheld(base=WicketConfirmationFixture.base.replace("{ onConfirm(target); }","{ if(target!=null) onConfirm(target); }"))
    fun testPreDispatchMutationIsWithheld()=withheld(base=WicketConfirmationFixture.base.replace("{ onConfirm(target); }","{ form.removeAll();onConfirm(target); }"))
    fun testEagerConsumerHookCallIsWithheld()=withheld(host=WicketConfirmationFixture.host.replace("this.action=pending;", "this.action=pending;onConfirm(target);"))
    fun testUnreviewedHelperEffectsAreWithheld()=withheld(body=WicketConfirmationFixture.page.replace("records.removeAll(rows);","records.removeAll(rows);System.gc();"))
    fun testNonPrivateDataHelperIsWithheld()=withheld(body=WicketConfirmationFixture.page.replace("private void delete", "public void delete"))
    fun testBorrowedStorageIsWithheld()=withheld(body=WicketConfirmationFixture.page.replace("new ArrayList<>()", "Collections.emptyList()"))
    fun testCapturedDataAliasIsWithheld()=withheld(body=WicketConfirmationFixture.page.replace("delete(List.of(row));", "var alias=row;delete(List.of(alias));"))
    fun testComponentDataIsWithheld()=withheld(body=WicketConfirmationFixture.page.replace("record Row(String id)","record Row(org.apache.wicket.Component id)"))
    fun testAnotherPageStorageIsWithheld()=withheld(body=WicketConfirmationFixture.page.replace("records.removeAll(rows);", "new ConfirmationPage().records.removeAll(rows);"))
    fun testConditionalCallbackIsWithheld()=withheld(body=WicketConfirmationFixture.page.replace("delete(List.of(row));", "if(row!=null) delete(List.of(row));"))
    fun testCallbackTargetEscapesAreWithheld()=withheld(body=WicketConfirmationFixture.page.replace("success(\"Deleted\");", "success(t);"))
    fun testOpaqueBulkSelectionCallDoesNotAcquirePermission()=withheld(body=WicketConfirmationFixture.page.replace("t.add(table);", "table.removeAll();t.add(table);"))
    fun testIndependentRegistrationsStaySeparateAndDoNotPredictCurrentSlot() {
        val (_,result)=inspectConfirmation(body=WicketConfirmationFixture.page.replace("private void delete", "private void delete").replace("            }\n            private void delete", """
                actions.addAction("Delete again",(target,row)->confirm.ask(target,"Again?","Cannot undo",t->{delete(List.of(row));t.add(table);}));
            }
            private void delete"""))
        assertEquals(result.diagnostics.toString(),2,result.reports.size)
        assertEquals(2,result.reports.map {it.submissionTemplate!!.contextId}.toSet().size)
        assertEquals(1,result.reports.map {it.submissionTemplate!!.confirmation!!.slotLocation}.toSet().size)
    }
    fun testUnsupportedIndependentRegistrationDoesNotHideTheSupportedRoute() {
        val (_,result)=inspectConfirmation(body=WicketConfirmationFixture.page.replace("            }\n            private void delete", """
                actions.addAction("Unsupported",(target,row)->confirm.ask(target,"Again?","",t->{delete(List.of(row));table.removeAll();}));
            }
            private void delete"""))
        assertEquals(result.diagnostics.toString(),1,result.reports.size)
        assertTrue(result.diagnostics.any {"unreviewed component/helper" in it.message})
    }
    fun testCleanupCannotInvokeAnotherOperation()=withheld(host=WicketConfirmationFixture.host.replace("action=null;super.onClose(target);","action=null;System.gc();super.onClose(target);"))
    fun testConditionalCleanupIsWithheld()=withheld(host=WicketConfirmationFixture.host.replace("action=null;super.onClose(target);","if(target!=null) action=null;super.onClose(target);"))
    fun testWrongAskTargetIsWithheld()=withheld(body=WicketConfirmationFixture.page.replace("confirm.ask(target,", "confirm.ask(null,"))
    fun testDataHelperCannotInvokeAStaticMutationWrapper()=withheld(body=WicketConfirmationFixture.page.replace("records.removeAll(rows);", "Collections.fill(records,null);"))
    fun testPostSubmitStateIsKeptOpaque() {
        val (_,result)=inspectConfirmation(base=WicketConfirmationFixture.base.replace("{ onConfirm(target); }", "{ open=true;onConfirm(target);if(open) form.removeAll(); }"))
        assertEquals(result.diagnostics.toString(),1,result.reports.size)
        assertEquals(1,result.reports.single().submissionTemplate!!.preDispatchLocations.size)
        assertEquals(1,result.reports.single().submissionTemplate!!.postDispatchLocations.size)
        assertTrue(result.reports.single().submissionTemplate!!.effects.none {it.methodName=="removeAll"})
    }
    private fun marker(body:String):WicketConfirmationContexts.Result {
        myFixture.addFileToProject("src/main/java/example/ConsumerMarker.java","package example; public interface ConsumerMarker<T> extends java.util.function.Consumer<T>,java.io.Serializable { $body }")
        return inspectConfirmation(host=WicketConfirmationFixture.host.replace("org.danekja.java.util.function.serializable.SerializableConsumer","example.ConsumerMarker")).second
    }
    fun testPureSourceConsumerMarkerUsesTheInheritedJdkOperation() {
        val result=marker("");assertEquals(result.diagnostics.toString(),1,result.reports.size)
        assertTrue(result.sources.any {it.endsWith("ConsumerMarker.java")})
    }
    fun testSourceConsumerOverrideCannotBorrowTheCompiledOperation() {assertTrue(marker("@Override default void accept(T value) {System.gc();}").reports.isEmpty())}
    fun testSourceConsumerInitializerIsWithheld() {assertTrue(marker("Object STATE=new Object();").reports.isEmpty())}
    fun testSourceConsumerExtraDefaultIsWithheld() {assertTrue(marker("default void extra() {System.gc();}").reports.isEmpty())}
    fun testBudgetsWithholdTheWholeDiscovery() {
        val java=WicketConfirmationFixture.install(myFixture);val initial=WicketPageAnalyzer().analyze(java.classes.single())!!
        for(limit in listOf(WicketSubmissionContexts.Limits(steps=0),WicketSubmissionContexts.Limits(sources=0),WicketSubmissionContexts.Limits(contexts=0),WicketSubmissionContexts.Limits(nodes=3),WicketSubmissionContexts.Limits(effects=0))) {
            val result=WicketConfirmationContexts(limit).analyze(java.classes.single(),initial)
            assertFalse(result.toString(),result.complete);assertTrue(result.reports.isEmpty())
        }
    }
    fun testCancellationPropagates() {
        val java=WicketConfirmationFixture.install(myFixture);val initial=WicketPageAnalyzer().analyze(java.classes.single())!!
        val indicator=EmptyProgressIndicator()
        try {ProgressManager.getInstance().runProcess({indicator.cancel();WicketConfirmationContexts().analyze(java.classes.single(),initial)},indicator);fail("Expected cancellation")}
        catch(_:ProcessCanceledException) {}
    }
}
