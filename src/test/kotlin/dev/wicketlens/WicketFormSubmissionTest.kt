package dev.wicketlens

import dev.wicketlens.analysis.*
import dev.wicketlens.export.WicketContextExporter
import dev.wicketlens.model.*

class WicketFormSubmissionTest : WicketProjectPsiTestCase() {
    fun testSubmitAndErrorAreSeparateDeclarationsAndLeaveInitialAnalysisUnchanged() {
        val java=WicketFormSubmissionFixture.install(myFixture);val page=java.classes.single()
        val primary=WicketPageAnalyzer().analyze(page)!!;val before=WicketContextExporter().export(primary)
        val result=WicketFormSubmissionContexts().analyze(page,primary)
        assertEquals(result.diagnostics.toString(),2,result.reports.size);assertTrue(result.complete)
        assertEquals(setOf("onSubmit","onError"),result.reports.map { it.submissionTemplate!!.callbackName }.toSet())
        result.reports.forEach { report ->
            assertEquals(2,report.matching!!.findings.size)
            assertTrue(report.matching.findings.all { it.code==DiagnosticCode.MATCHED && it.confidence==Confidence.INFERRED })
            assertEquals("invoiceForm:submit",report.submissionTemplate!!.triggerComponentPath)
            assertEquals("ENCLOSING_DECLARED_FORM",report.submissionTemplate.formCallback!!.formAssociation)
            assertEquals(listOf("invoiceForm:submit","invoiceForm"),report.submissionTemplate.effects.map { it.targetComponentPath })
            assertNull(report.submissionTemplate.bridgeLocation);assertNull(report.submissionTemplate.dispatchLocation)
        }
        assertEquals(before,WicketContextExporter().export(WicketPageAnalyzer().analyze(page)!!))
    }
    fun testExplicitFormConstructorForwardingPreservesAssociation() {
        val java=WicketFormSubmissionFixture.install(myFixture,body=WicketFormSubmissionFixture.page.replace("SubmitButton(\"submit\")","SubmitButton(\"submit\",null,form)"))
        val result=WicketFormSubmissionContexts().analyze(java.classes.single(),WicketPageAnalyzer().analyze(java.classes.single())!!)
        assertEquals(result.diagnostics.toString(),2,result.reports.size)
        assertTrue(result.reports.all { it.submissionTemplate!!.formCallback!!.formAssociation=="EXPLICIT_DECLARED_FORM" })
    }
    private fun result(body:String=WicketFormSubmissionFixture.page,button:String=WicketFormSubmissionFixture.button,html:String=WicketFormSubmissionFixture.markup):WicketFormSubmissionContexts.Result {
        val java=WicketFormSubmissionFixture.install(myFixture,body=body,buttonSource=button,html=html)
        return WicketFormSubmissionContexts().analyze(java.classes.single(),WicketPageAnalyzer().analyze(java.classes.single())!!)
    }
    private fun withheld(body:String=WicketFormSubmissionFixture.page,button:String=WicketFormSubmissionFixture.button,html:String=WicketFormSubmissionFixture.markup) {
        val result=result(body,button,html);assertTrue(result.toString(),result.reports.isEmpty());assertTrue(result.diagnostics.toString(),result.diagnostics.isNotEmpty())
    }
    fun testDirectFrameworkButtonAndQualifiedPageFeedback() {
        val result=result(body=WicketFormSubmissionFixture.page.replace("new SubmitButton(\"submit\")","new org.apache.wicket.ajax.markup.html.form.AjaxButton(\"submit\")").replace("success(\"Saved\")","FormPage.this.success(\"Saved\")"))
        assertEquals(result.diagnostics.toString(),2,result.reports.size)
        val submit=result.reports.single { it.submissionTemplate!!.callbackName=="onSubmit" }
        assertEquals("",submit.submissionTemplate!!.effects.first().targetComponentPath)
    }
    fun testUnknownInitialEffectsDoNotBecomeAnInitialHierarchyUpgrade() {
        val java=WicketFormSubmissionFixture.install(myFixture,body=WicketFormSubmissionFixture.page.replace("public FormPage()", "private void configure() { removeAll(); } public FormPage()").replace(";add(form);",";add(form);configure();"))
        val primary=WicketPageAnalyzer().analyze(java.classes.single())!!;val before=WicketContextExporter().export(primary)
        val result=WicketFormSubmissionContexts().analyze(java.classes.single(),primary)
        assertEquals(result.diagnostics.toString(),2,result.reports.size)
        assertTrue(primary.matching!!.findings.any { it.code!=DiagnosticCode.MATCHED })
        assertTrue(result.reports.all { it.warnings.single().message.contains("not a proved initial/runtime hierarchy") })
        assertEquals(before,WicketContextExporter().export(WicketPageAnalyzer().analyze(java.classes.single())!!))
    }
    fun testConditionalSubmitWithholdsOnlyThatCallback() {
        val result=result(body=WicketFormSubmissionFixture.page.replace("success(\"Saved\");target.add(form);","if(target!=null) target.add(form);"))
        assertEquals(listOf("onError"),result.reports.map { it.submissionTemplate!!.callbackName })
        assertTrue(result.diagnostics.isNotEmpty())
    }
    fun testTargetEscapeWithholdsOnlyThatCallback() {
        val result=result(body=WicketFormSubmissionFixture.page.replace("success(\"Saved\");","success(target);"))
        assertEquals(listOf("onError"),result.reports.map { it.submissionTemplate!!.callbackName })
    }
    fun testComponentAcquisitionInFeedbackIsWithheld() {
        val result=result(body=WicketFormSubmissionFixture.page.replace("success(\"Saved\");","success(FormPage.this.get(\"invoiceForm\"));"))
        assertEquals(listOf("onError"),result.reports.map { it.submissionTemplate!!.callbackName })
    }
    fun testCapturedAliasesAreNotRefreshTargets() {
        val result=result(body=WicketFormSubmissionFixture.page.replace("form.add(new SubmitButton", "Form<Void> alias=form;form.add(new SubmitButton").replace("target.add(form);","target.add(alias);"))
        assertTrue(result.reports.isEmpty())
    }
    fun testReassignedFormHasNoStableDeclaredTarget()=withheld(body=WicketFormSubmissionFixture.page.replace("form.setOutputMarkupId(true);","form=new Form<>(\"changed\");form.setOutputMarkupId(true);"))
    fun testDirectStructuralMutationIsWithheld()=withheld(body=WicketFormSubmissionFixture.page.replace("form.setOutputMarkupId(true);","form.removeAll();form.setOutputMarkupId(true);"))
    fun testButtonCannotAcquireTwoParents()=withheld(body=WicketFormSubmissionFixture.page.replace("form.add(new SubmitButton(\"submit\")", "SubmitButton b=new SubmitButton(\"submit\")").replace("        });", "        };form.add(b);add(b);"))
    fun testConditionalFormAttachmentIsNotAnUnconditionalDeclaration()=withheld(body=WicketFormSubmissionFixture.page.replace(";add(form);",";if(System.nanoTime()>0) add(form);"))
    fun testExplicitForeignFormIsWithheld()=withheld(body=WicketFormSubmissionFixture.page.replace("SubmitButton(\"submit\")","SubmitButton(\"submit\",null,new Form<Void>(\"foreign\"))"))
    fun testCastForeignFormIsNotAnImplicitNullForm()=withheld(body=WicketFormSubmissionFixture.page.replace("SubmitButton(\"submit\")","SubmitButton(\"submit\",null,(Form<?>)new Form<Void>(\"foreign\"))"))
    fun testIncompleteSyntaxWithholdsDiscoveryWithADiagnostic() {
        val result=result(body=WicketFormSubmissionFixture.page.replace("success(\"Saved\");","success(\"Saved\"); ???"))
        assertTrue(result.reports.isEmpty());assertTrue(result.diagnostics.any { it.message.contains("syntax is incomplete") })
    }
    fun testOverriddenFrameworkRoutingIsWithheld()=withheld(button=WicketFormSubmissionFixture.button.replace("public SubmitButton(String id)", "@Override public org.apache.wicket.markup.html.form.Form<?> getForm() { return null; } public SubmitButton(String id)"))
    fun testAnonymousDefaultProcessingOverrideIsWithheld()=withheld(body=WicketFormSubmissionFixture.page.replace("@Override protected void onSubmit", "@Override public boolean getDefaultFormProcessing() { return false; } @Override protected void onSubmit"))
    fun testDuplicateMarkupIsNotAUniqueCounterpart()=withheld(html=WicketFormSubmissionFixture.markup.replace("</form>","<button wicket:id='submit'>Again</button></form>"))
    fun testMarkupParentMismatchDoesNotInventAPath()=withheld(html=WicketFormSubmissionFixture.markup.replace("<button wicket:id='submit'>Save</button></form>","</form><button wicket:id='submit'>Save</button>"))
    fun testMissingOrVariantCompanionsSupplyNoOrdinaryDeclarationGraph() {
        val java=WicketFormSubmissionFixture.install(myFixture);val primary=WicketPageAnalyzer().analyze(java.classes.single())!!
        for(report in listOf(primary.copy(markup=null),primary.copy(markupPath=null),primary.copy(markupSuffix="_de"))) {
            val result=WicketFormSubmissionContexts().analyze(java.classes.single(),report)
            assertTrue(result.reports.isEmpty());assertTrue(result.sources.isEmpty())
        }
    }
    fun testOrdinaryContextBudgetsWithholdAllOrdinaryGraphs() {
        val java=WicketFormSubmissionFixture.install(myFixture);val primary=WicketPageAnalyzer().analyze(java.classes.single())!!
        for(limit in listOf(WicketFormSubmissionContexts.Limits(steps=0),WicketFormSubmissionContexts.Limits(sources=0),WicketFormSubmissionContexts.Limits(contexts=1),
            WicketFormSubmissionContexts.Limits(nodes=2),WicketFormSubmissionContexts.Limits(depth=1),WicketFormSubmissionContexts.Limits(effects=1))) {
            val result=WicketFormSubmissionContexts(limit).analyze(java.classes.single(),primary)
            assertFalse(result.toString(),result.complete);assertTrue(result.reports.isEmpty())
        }
    }
    fun testCancellationPropagates() {
        val java=WicketFormSubmissionFixture.install(myFixture);val primary=WicketPageAnalyzer().analyze(java.classes.single())!!
        val indicator=com.intellij.openapi.progress.EmptyProgressIndicator()
        assertThrows(com.intellij.openapi.progress.ProcessCanceledException::class.java) {
            com.intellij.openapi.progress.ProgressManager.getInstance().runProcess(Runnable { indicator.cancel();WicketFormSubmissionContexts().analyze(java.classes.single(),primary) },indicator)
        }
    }

}
