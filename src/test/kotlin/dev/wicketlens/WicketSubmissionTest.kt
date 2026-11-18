package dev.wicketlens

import dev.wicketlens.analysis.*
import dev.wicketlens.export.WicketContextExporter
import dev.wicketlens.model.*

class WicketSubmissionTest : WicketProjectPsiTestCase() {
    fun testCallbacksHaveSeparateSourceTargetsWithoutChangingTheInitialGraph() {
        val java=WicketSubmissionFixture.install(myFixture);val page=java.classes.single();val initial=WicketPageAnalyzer().analyze(page)!!
        val before=WicketContextExporter().export(initial)
        val result=WicketSubmissionContexts().analyze(page,initial)
        assertTrue(result.complete);assertEquals(result.diagnostics.toString(),2,result.reports.size)
        val save=result.reports.single { it.submissionTemplate!!.hostComponentPath=="save" }
        assertEquals("save:dialog:form:confirm",save.submissionTemplate!!.triggerComponentPath)
        assertEquals(listOf("FEEDBACK","AJAX_REFRESH"),save.submissionTemplate.effects.map { it.kind })
        assertEquals("people",save.submissionTemplate.effects.last().targetComponentPath)
        assertEquals(5,save.matching!!.findings.size)
        val delete=result.reports.single { it.submissionTemplate!!.hostComponentPath=="delete" }
        assertEquals(4,delete.matching!!.findings.size)
        assertTrue(result.reports.all { it.matching!!.findings.all { f->f.code==DiagnosticCode.MATCHED && f.confidence==Confidence.INFERRED } })
        assertEquals(before,WicketContextExporter().export(WicketPageAnalyzer().analyze(page)!!))
    }

    fun testFreshInlineLabelReplacementIsASeparateSixBindingDeclaration() {
        val java=WicketSubmissionFixture.install(myFixture,body=WicketSubmissionFixture.page.replace("success(\"Saved\");target.add(people);","setContent(new Label(DialogBox.CONTENT_ID,\"Submitted\"));target.add(people);"))
        val primary=WicketPageAnalyzer().analyze(java.classes.single())!!
        val result=WicketSubmissionContexts().analyze(java.classes.single(),primary)
        val context=result.reports.single { it.submissionTemplate!!.hostComponentPath=="save" }
        assertEquals(result.diagnostics.toString(),6,context.matching!!.findings.size)
        assertTrue(context.matching.findings.all { it.code==DiagnosticCode.MATCHED && it.confidence==Confidence.INFERRED })
        assertEquals(listOf("CONTENT_REPLACEMENT","AJAX_REFRESH"),context.submissionTemplate!!.effects.map { it.kind })
        val root=context.nodes.single { it.nodeId==context.submissionTemplate.contentNodeId }
        assertTrue(root.componentType.endsWith("Label"));assertEquals("content",root.wicketId)
        assertEquals("WebMarkupContainer",primary.nodes.single { it.nodeId==primary.initialContentSetup!!.hosts.single { it.hostNodeId==context.submissionTemplate.hostNodeId }.contentNodeId }.componentType.substringAfterLast('.'))
    }
    fun testFreshFragmentReplacementReusesTheProviderAndChildGraph() {
        val java=WicketSubmissionFixture.install(myFixture,body=WicketSubmissionFixture.page.replace("success(\"Saved\");target.add(people);", "Fragment content=new Fragment(DialogBox.CONTENT_ID,\"submitted\",SubmitPage.this);content.add(new Label(\"message\",\"Submitted\"));setContent(content);target.add(people);"))
        val primary=WicketPageAnalyzer().analyze(java.classes.single())!!
        val result=WicketSubmissionContexts().analyze(java.classes.single(),primary)
        val context=result.reports.single { it.submissionTemplate!!.hostComponentPath=="save" }
        assertEquals(result.diagnostics.toString(),7,context.matching!!.findings.size)
        assertTrue(context.matching.findings.all { it.code==DiagnosticCode.MATCHED && it.confidence==Confidence.INFERRED })
        assertEquals("submitted",context.nodes.single { it.nodeId==context.submissionTemplate!!.contentNodeId }.fragment!!.definitionId)
        assertTrue(primary.fragmentUses.single { it.definitionId=="submitted" }.instanceNodeIds.isEmpty())
    }

    private fun withheld(body:String=WicketSubmissionFixture.page,host:String=WicketSubmissionFixture.host) {
        val java=WicketSubmissionFixture.install(myFixture,body=body,hostSource=host)
        val initial=WicketPageAnalyzer().analyze(java.classes.single())!!
        val result=WicketSubmissionContexts().analyze(java.classes.single(),initial)
        assertTrue(result.reports.toString(),result.reports.none { it.submissionTemplate!!.hostComponentPath=="save" })
    }
    fun testConditionalCallbackIsWithheld()=withheld(body=WicketSubmissionFixture.page.replace("success(\"Saved\");target.add(people);","if(target!=null) target.add(people);"))
    fun testUnknownCallbackCallIsWithheld()=withheld(body=WicketSubmissionFixture.page.replace("success(\"Saved\");","System.gc();"))
    fun testAnotherTargetReceiverIsNotTheCallbackTarget()=withheld(body=WicketSubmissionFixture.page.replace("target.add(people);","((AjaxRequestTarget)null).add(people);"))
    fun testOpaqueFeedbackCannotAcquireAComponent()=withheld(body=WicketSubmissionFixture.page.replace("success(\"Saved\");","success(SubmitPage.this.get(\"people\"));"))
    fun testCallbackTargetCannotEscapeToFeedbackData()=withheld(body=WicketSubmissionFixture.page.replace("success(\"Saved\");","success(target);"))
    fun testWrongContentIdDoesNotPublishAReplacement()=withheld(body=WicketSubmissionFixture.page.replace("success(\"Saved\");","setContent(new Label(\"wrong\",\"Submitted\"));"))
    fun testBorrowedContentDoesNotPublishAReplacement()=withheld(body=WicketSubmissionFixture.page.replace("success(\"Saved\");","setContent(people);"))
    fun testCompetingContentPlacementsAreWithheld()=withheld(body=WicketSubmissionFixture.page.replace("success(\"Saved\");","setContent(new Label(DialogBox.CONTENT_ID));setContent(new Label(DialogBox.CONTENT_ID));"))
    fun testReassignedCapturedTargetHasNoDeclaredIdentity()=withheld(body=WicketSubmissionFixture.page.replace("people.setOutputMarkupId(true);","people=new WebMarkupContainer(\"other\");people.setOutputMarkupId(true);"))
    fun testCapturedAliasesAreNotInventedTargets()=withheld(body=WicketSubmissionFixture.page.replace("people.setOutputMarkupId(true);","WebMarkupContainer alias=people;people.setOutputMarkupId(true);").replace("target.add(people);","target.add(alias);"))
    fun testConditionalBridgeDispatchIsWithheld()=withheld(host=WicketSubmissionFixture.host.replace("{ onConfirm(target); }","{ if(target!=null) onConfirm(target); }"))
    fun testReassignedBridgeTargetIsWithheld()=withheld(host=WicketSubmissionFixture.host.replace("{ onConfirm(target); }","{ target=null;onConfirm(target); }"))
    fun testStructuralEffectsBeforeTheHookAreNotIgnored()=withheld(host=WicketSubmissionFixture.host.replace("{ onConfirm(target); }","{ form.removeAll();onConfirm(target); }"))
    fun testUnreviewedButtonOverrideIsWithheld()=withheld(host=WicketSubmissionFixture.host.replace("@Override protected void onSubmit", "@Override public boolean getDefaultFormProcessing() { return false; } @Override protected void onSubmit"))
    fun testExplicitOwnedFormIsTheSameGuardedForm() {
        val java=WicketSubmissionFixture.install(myFixture,hostSource=WicketSubmissionFixture.host.replace("new AjaxButton(\"confirm\")", "new AjaxButton(\"confirm\",form)"))
        val primary=WicketPageAnalyzer().analyze(java.classes.single())!!
        val result=WicketSubmissionContexts().analyze(java.classes.single(),primary)
        assertEquals(result.diagnostics.toString(),2,result.reports.size)
    }
    fun testExplicitForeignFormIsWithheld()=withheld(host=WicketSubmissionFixture.host.replace("new AjaxButton(\"confirm\")", "new AjaxButton(\"confirm\",new Form<Void>(\"foreign\"))"))
    fun testPostDispatchEffectsAreRecordedWithoutPredictingState() {
        val java=WicketSubmissionFixture.install(myFixture,hostSource=WicketSubmissionFixture.host.replace("{ onConfirm(target); }","{ open=true;onConfirm(target);if(open) { target.add(form); } }"))
        val primary=WicketPageAnalyzer().analyze(java.classes.single())!!
        val result=WicketSubmissionContexts().analyze(java.classes.single(),primary)
        assertEquals(result.diagnostics.toString(),2,result.reports.size)
        result.reports.forEach { context ->
            assertEquals(1,context.submissionTemplate!!.preDispatchLocations.size)
            assertEquals(1,context.submissionTemplate.postDispatchLocations.size)
            assertTrue(context.submissionTemplate.effects.none { it.targetComponentPath.endsWith(":form") })
        }
    }
    fun testUnknownNonSubmitHookDoesNotBecomeASubmission() {
        val java=WicketSubmissionFixture.install(myFixture,
            hostSource=WicketSubmissionFixture.host.replace("protected void onConfirm(AjaxRequestTarget target) {}","protected void onConfirm(AjaxRequestTarget target) {} protected void onClose(AjaxRequestTarget target) {}"),
            body=WicketSubmissionFixture.page.replace("info(\"Deleted\"); }", "info(\"Deleted\"); } @Override protected void onClose(AjaxRequestTarget target) { info(\"Closed\"); }"))
        val primary=WicketPageAnalyzer().analyze(java.classes.single())!!
        val result=WicketSubmissionContexts().analyze(java.classes.single(),primary)
        assertEquals(2,result.reports.size)
        assertTrue(result.reports.all { it.submissionTemplate!!.callbackName=="onConfirm" })
    }
    fun testSubmissionBudgetsWithholdTheWholeDiscovery() {
        val java=WicketSubmissionFixture.install(myFixture);val primary=WicketPageAnalyzer().analyze(java.classes.single())!!
        for(limit in listOf(WicketSubmissionContexts.Limits(steps=0),WicketSubmissionContexts.Limits(sources=0),WicketSubmissionContexts.Limits(contexts=0),
            WicketSubmissionContexts.Limits(nodes=3),WicketSubmissionContexts.Limits(depth=1),WicketSubmissionContexts.Limits(effects=0))) {
            val result=WicketSubmissionContexts(limit).analyze(java.classes.single(),primary)
            assertFalse(result.toString(),result.complete);assertTrue(result.reports.isEmpty());assertTrue(result.diagnostics.isNotEmpty())
        }
    }
    fun testCancellationPropagates() {
        val java=WicketSubmissionFixture.install(myFixture);val primary=WicketPageAnalyzer().analyze(java.classes.single())!!
        val indicator=com.intellij.openapi.progress.EmptyProgressIndicator()
        assertThrows(com.intellij.openapi.progress.ProcessCanceledException::class.java) {
            com.intellij.openapi.progress.ProgressManager.getInstance().runProcess(Runnable { indicator.cancel();WicketSubmissionContexts().analyze(java.classes.single(),primary) },indicator)
        }
    }
}
