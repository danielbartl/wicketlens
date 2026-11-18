package dev.wicketlens

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.progress.*
import com.intellij.psi.*
import dev.wicketlens.analysis.*
import dev.wicketlens.model.*

class WicketRegisteredFormTest : WicketProjectPsiTestCase() {
    private fun setup()=WicketRegisteredFormFixture.install(myFixture)
    private fun result(java:PsiJavaFile, limits:WicketFormContexts.Limits=WicketFormContexts.Limits())=WicketFormContexts(limits).analyze(java.classes.single(),WicketPageAnalyzer().analyze(java.classes.single())!!)
    private fun change(path:String,before:String,after:String) {
        val file=PsiManager.getInstance(project).findFile(myFixture.findFileInTempDir("src/main/java/example/$path.java"))!!
        assertTrue(before,file.text.contains(before))
        WriteCommandAction.runWriteCommandAction(project) { PsiDocumentManager.getInstance(project).getDocument(file)!!.setText(file.text.replace(before,after));PsiDocumentManager.getInstance(project).commitAllDocuments() }
    }
    private fun rejects(path:String,before:String,after:String) { val java=setup();change(path,before,after);val r=result(java);assertTrue(r.toString(),r.reports.isEmpty()) }
    fun testRegisteredCompositeInputHasSixInferredWrapperRelativeBindings() {
        val java=setup();val initial=WicketPageAnalyzer().analyze(java.classes.single())!!;val result=result(java)
        assertTrue(result.complete);assertEquals(result.diagnostics.toString(),1,result.reports.size)
        val report=result.reports.single();assertEquals("example.ContactPage",report.qualifiedClassName);assertEquals(6,report.matching!!.findings.size)
        assertTrue(report.matching.findings.all { it.code==DiagnosticCode.MATCHED && it.confidence==Confidence.INFERRED })
        assertTrue(report.nodes.all { it.template && !it.idRenameSafe });assertFalse(report.scopeComplete)
        val form=report.formTemplate!!;assertEquals("example.PhoneInput",form.inputClassName);assertEquals("input",form.inputComponentPath)
        assertEquals("example.ContactPage",form.registration!!.ownerClassName);assertEquals(setOf("code","number"),report.nodes.filter { it.parentNodeId==form.inputNodeId }.mapNotNull { it.wicketId }.toSet())
        assertEquals(listOf("CONVERSION"),form.hooks.map { it.kind });assertEquals(initial,WicketPageAnalyzer().analyze(java.classes.single()))
    }
    fun testDirectReturnFactoryIsSupported() { val java=setup();change("CustomField","org.apache.wicket.markup.html.form.FormComponent<String> component=inputFactory.apply(id,model);\n                if(component==null || !id.equals(component.getId())) { throw new IllegalStateException(\"Invalid component ID\"); }\n                return component;","return inputFactory.apply(id,model);");assertEquals(1,result(java).reports.size) }
    fun testRejectionMessageCanFormatOnlyTheSuppliedAndReturnedIds() { val java=setup();change("CustomField","new IllegalStateException(\"Invalid component ID\")","new IllegalArgumentException(\"Wrong input \"+id+\": \"+(component==null?\"null\":\"[\"+component.getId()+\"]\"))");assertEquals(1,result(java).reports.size) }
    fun testRejectionMessageCannotRunApplicationCode()=rejects("CustomField","new IllegalStateException(\"Invalid component ID\")","new IllegalArgumentException(getPage().toString())")
    fun testTwoRegistrationsRetainSeparateProvenance() { val java=setup();change("ContactPage","add(new CustomField(\"phone\",PhoneInput::new));","add(new CustomField(\"phone\",PhoneInput::new)); add(new CustomField(\"other\",PhoneInput::new));");val reports=result(java).reports;assertEquals(2,reports.size);assertEquals(2,reports.map { it.formTemplate!!.contextId }.distinct().size) }
    fun testEqualHardcodedConstructorIdIsNotForwarding()=rejects("PhoneInput","super(id,model)","super(\"__wicketlens_form_id__\",model)")
    fun testFactoryMustForwardItsIdParameter()=rejects("CustomField","inputFactory.apply(id,model)","inputFactory.apply(\"input\",model)")
    fun testFactoryMustForwardItsModelParameter()=rejects("CustomField","inputFactory.apply(id,model)","inputFactory.apply(id,null)")
    fun testFactoryCannotBeStoredInMutableField()=rejects("CustomField","private final java.util.function","private java.util.function")
    fun testFactoryCannotBeStoredInStaticField()=rejects("CustomField","private final java.util.function","private static final java.util.function")
    fun testFactoryCannotEscapeThroughGetter()=rejects("CustomField","@Override protected", "public Object leak() { return inputFactory; } @Override protected")
    fun testConstructorCannotInvokeOpaqueFactoryConsumer()=rejects("CustomField","super(id,model); this.inputFactory", "super(id,model); java.util.Objects.requireNonNull(factory); this.inputFactory")
    fun testConstructorCannotAddExtraComponents()=rejects("CustomField","super(id,model); this.inputFactory", "super(id,model); add(new org.apache.wicket.markup.html.basic.Label(\"extra\")); this.inputFactory")
    fun testFactoryResultCannotBeReassigned()=rejects("CustomField","return component;","component=new PhoneInput(id,model); return component;")
    fun testFactoryResultCannotEscape()=rejects("CustomField","return component;","java.util.Objects.requireNonNull(component); return component;")
    fun testGuardCannotRunArbitraryApplicationCode()=rejects("CustomField","throw new IllegalStateException(\"Invalid component ID\");","getPage().removeAll(); throw new IllegalStateException(\"Invalid component ID\");")
    fun testGuardMustBeAnIdRejection()=rejects("CustomField","!id.equals(component.getId())","System.nanoTime()>0")
    fun testLambdaRegistrationIsOutsideThisSlice()=rejects("ContactPage","PhoneInput::new","(id,model)->new PhoneInput(id,model)")
    fun testAnonymousWrapperDoesNotReuseBaseContract()=rejects("ContactPage","new CustomField(\"phone\",PhoneInput::new)","new CustomField(\"phone\",PhoneInput::new) {}")
    fun testContextBoundRejectsAllRegisteredGraphs() { val java=setup();val r=result(java,WicketFormContexts.Limits(contexts=0));assertFalse(r.complete);assertTrue(r.reports.isEmpty()) }
    fun testNodeBoundRejectsAllRegisteredGraphs() { val java=setup();val r=result(java,WicketFormContexts.Limits(nodes=3));assertFalse(r.complete);assertTrue(r.reports.isEmpty()) }
    fun testStepBoundRejectsAllRegisteredGraphs() { val java=setup();val r=result(java,WicketFormContexts.Limits(steps=3));assertFalse(r.complete);assertTrue(r.reports.isEmpty()) }
    fun testSourceBoundRejectsAllRegisteredGraphs() { val java=setup();val r=result(java,WicketFormContexts.Limits(sources=1));assertFalse(r.complete);assertTrue(r.reports.isEmpty()) }
    fun testConstructorDepthBoundRejectsAllRegisteredGraphs() { val java=setup();val r=result(java,WicketFormContexts.Limits(depth=1));assertFalse(r.complete);assertTrue(r.reports.isEmpty()) }
    fun testIncompatibleConstructorReferenceIsWithheld()=rejects("PhoneInput","IModel<String> model","Integer model")
    fun testNestedStorageHelpersAreOutsideTheForwardingContract()=rejects("CustomField","java.util.Objects.requireNonNull(factory)","java.util.Objects.requireNonNull(java.util.Objects.requireNonNull(factory))")
    fun testCacheMutationStillWithholdsRegisteredInput()=rejects("FieldBase","add(input);","add(input); removeAll();")
    fun testFactoryCannotInvokeTheStoredReferenceElsewhere()=rejects("CustomField","@Override protected","public void extra() { inputFactory.apply(\"extra\",null); } @Override protected")
    fun testCancellationPropagates() { val java=setup();val initial=WicketPageAnalyzer().analyze(java.classes.single())!!;val indicator=EmptyProgressIndicator();assertThrows(ProcessCanceledException::class.java) { ProgressManager.getInstance().runProcess(Runnable { indicator.cancel();WicketFormContexts().analyze(java.classes.single(),initial) },indicator) } }
}
