package dev.wicketlens

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.progress.*
import com.intellij.psi.*
import dev.wicketlens.analysis.*
import dev.wicketlens.model.*

class WicketFormInputContextsTest : WicketProjectPsiTestCase() {
    private fun setup()=WicketFormInputFixture.install(myFixture)
    private fun result(java:PsiJavaFile,limits:WicketFormContexts.Limits=WicketFormContexts.Limits())=WicketFormContexts(limits).analyze(java.classes.single(),WicketPageAnalyzer().analyze(java.classes.single())!!)
    private fun change(path:String,before:String,after:String) {
        val file=PsiManager.getInstance(project).findFile(myFixture.findFileInTempDir(path))!!
        WriteCommandAction.runWriteCommandAction(project) { PsiDocumentManager.getInstance(project).getDocument(file)!!.setText(file.text.replace(before,after)) }
        PsiDocumentManager.getInstance(project).commitAllDocuments()
    }
    fun testCompositeInputHasSeparateInferredStructureAndUnknownConversion() {
        val java=setup();val primary=WicketPageAnalyzer().analyze(java.classes.single())!!;val r=result(java)
        assertTrue(r.diagnostics.toString(),r.complete);assertEquals(r.diagnostics.toString(),1,r.reports.size)
        val report=r.reports.single();assertEquals(6,report.matching!!.findings.size)
        assertTrue(report.matching.findings.all { it.code==DiagnosticCode.MATCHED && it.confidence==Confidence.INFERRED })
        assertTrue(report.nodes.all { it.template && !it.idRenameSafe });assertFalse(report.scopeComplete)
        val form=report.formTemplate!!;assertEquals("input",form.inputComponentPath);assertNotNull(form.initializationLocation)
        assertEquals(setOf("CONVERSION","REQUIRED_CHECK","VALIDATOR_DECLARATION"),form.hooks.map { it.kind }.toSet())
        assertEquals(setOf("begin","end"),report.nodes.filter { it.parentNodeId==form.inputNodeId }.mapNotNull { it.wicketId }.toSet())
        assertEquals(primary,WicketPageAnalyzer().analyze(java.classes.single()))
    }
    fun testExplicitNullCacheInitializerKeepsTheSameDeclaration() { val java=setup();change("src/main/java/example/FieldBase.java","private C input;","private C input=null;");assertEquals(1,result(java).reports.size) }
    fun testDeferredGetterCallIsNotAnInitializationCall() { val java=setup();change("src/main/java/example/FieldBase.java","super.onInitialize(); getInput();","super.onInitialize(); Runnable later=() -> getInput();");assertNull(result(java).reports.single().formTemplate!!.initializationLocation) }
    fun testEqualLiteralIsNotFactoryForwarding() { val java=setup();change("src/main/java/example/Field.java","new Input(id,model)","new Input(\"input\",model)");assertTrue(result(java).reports.isEmpty()) }
    fun testInputIdReassignmentIsNotForwarding() { val java=setup();change("src/main/java/example/Field.java","return new Input(id,model)","id=\"other\"; return new Input(id,model)");assertTrue(result(java).reports.isEmpty()) }
    fun testUnknownCacheGuardIsNotAProvedCache() { val java=setup();change("src/main/java/example/FieldBase.java","if(input==null)","if(System.nanoTime()>0)");assertTrue(result(java).reports.isEmpty()) }
    fun testAdditionalCacheWritePreventsTemplate() { val java=setup();change("src/main/java/example/FieldBase.java","return input;","input=null; return input;");assertTrue(result(java).reports.isEmpty()) }
    fun testDuplicateCacheAttachmentIsWithheld() { val java=setup();change("src/main/java/example/FieldBase.java","add(input);","add(input); add(input);");assertTrue(result(java).reports.isEmpty()) }
    fun testCacheRootMutationIsWithheld() { val java=setup();change("src/main/java/example/FieldBase.java","add(input);","add(input); removeAll();");assertTrue(result(java).reports.isEmpty()) }
    fun testDeferredModelCannotExposeTheCachedComponent() { val java=setup();change("src/main/java/example/FieldBase.java","add(input);","input.setDefaultModel(new org.apache.wicket.model.IModel<org.apache.wicket.Component>() { public org.apache.wicket.Component getObject() { return input; } }); add(input);");assertTrue(result(java).reports.isEmpty()) }
    fun testCacheCannotMutateAnEnclosingContainer() { val java=setup();change("src/main/java/example/FieldBase.java","add(input);","input.getParent().add(new org.apache.wicket.markup.html.basic.Label(\"other\")); add(input);");assertTrue(result(java).reports.isEmpty()) }
    fun testAdditionalCacheChildrenNeedTheirOwnDeclarationContract() { val java=setup();change("src/main/java/example/FieldBase.java","add(input);","input.add(new org.apache.wicket.markup.html.basic.Label(\"other\")); add(input);");assertTrue(result(java).reports.isEmpty()) }
    fun testCacheCannotBeAddedToAForeignParent() { val java=setup();change("src/main/java/example/FieldBase.java","add(input);","new org.apache.wicket.markup.html.WebMarkupContainer(\"foreign\").add(input); add(input);");assertTrue(result(java).reports.isEmpty()) }
    fun testCacheCannotEscapeIntoOpaqueCode() { val java=setup();change("src/main/java/example/FieldBase.java","add(input);","java.util.Objects.requireNonNull(input); add(input);");assertTrue(result(java).reports.isEmpty()) }
    fun testAccessorOverrideDoesNotUseTheBaseCache() { val java=setup();change("src/main/java/example/Field.java","public final class Input", "@Override public Input getInput() { return new Input(\"different\",null); } public final class Input");assertTrue(result(java).reports.isEmpty()) }
    fun testCustomAttachmentDoesNotUseFrameworkAdd() { val java=setup();change("src/main/java/example/Field.java","public final class Input","@Override public org.apache.wicket.MarkupContainer add(org.apache.wicket.Component... values) { return this; } public final class Input");assertTrue(result(java).reports.isEmpty()) }
    fun testPrivateHelperCannotReturnBorrowedInput() { val java=setup();change("src/main/java/example/Field.java","value.add(org.apache.wicket.AttributeModifier.replace(\"aria-label\",id)); return value;","value.add(org.apache.wicket.AttributeModifier.replace(\"aria-label\",id)); return begin;");assertTrue(result(java).reports.isEmpty()) }
    fun testRepeatedHelperLiteralCannotInventDistinctChildren() { val java=setup();change("src/main/java/example/Field.java","TextField<>(id,model)","TextField<>(\"begin\",model)");assertTrue(result(java).reports.isEmpty()) }
    fun testNonFinalInputFieldCannotSupplyUniqueOwnership() { val java=setup();change("src/main/java/example/Field.java","private final org.apache.wicket.markup.html.form.TextField<String> begin","private org.apache.wicket.markup.html.form.TextField<String> begin");assertTrue(result(java).reports.isEmpty()) }
    fun testConversionSourceIsRecordedWithoutExecutingOrCertifyingIt() { val java=setup();change("src/main/java/example/Field.java","setConvertedInput(begin.getConvertedInput()", "getPage().removeAll(); setConvertedInput(begin.getConvertedInput()");val r=result(java).reports.single();assertFalse(r.scopeComplete);assertTrue(r.warnings.any { "conversion" in it.message || "converters" in it.message });assertTrue(r.formTemplate!!.hooks.any { it.kind=="CONVERSION" }) }
    fun testInitializationOverrideDoesNotClaimGuaranteedPresence() { val java=setup();change("src/main/java/example/Field.java","public final class Input","@Override protected void onInitialize() {} public final class Input");assertNull(result(java).reports.single().formTemplate!!.initializationLocation) }
    fun testContextBoundPublishesNoPartialFormGraph() { val java=setup();val r=result(java,WicketFormContexts.Limits(contexts=0));assertFalse(r.complete);assertTrue(r.reports.isEmpty()) }
    fun testNodeBoundPublishesNoPartialFormGraph() { val java=setup();val r=result(java,WicketFormContexts.Limits(nodes=3));assertFalse(r.complete);assertTrue(r.reports.isEmpty()) }
    fun testCancellationPropagates() { val java=setup();val primary=WicketPageAnalyzer().analyze(java.classes.single())!!;val indicator=EmptyProgressIndicator()
        assertThrows(ProcessCanceledException::class.java) { ProgressManager.getInstance().runProcess(Runnable { indicator.cancel();WicketFormContexts().analyze(java.classes.single(),primary) },indicator) } }
}
