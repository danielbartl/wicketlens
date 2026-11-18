package dev.wicketlens

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.progress.*
import com.intellij.psi.*
import com.intellij.psi.search.GlobalSearchScope
import dev.wicketlens.analysis.*
import dev.wicketlens.model.DiagnosticCode
import dev.wicketlens.ui.WicketReportFormatter

class WicketLambdaColumnTest : WicketProjectPsiTestCase() {
    private fun change(before:String,after:String,name:String="BadgeColumn") {
        val file=PsiManager.getInstance(project).findFile(myFixture.findFileInTempDir("src/main/java/example/$name.java"))!!
        assertTrue(before in file.text)
        WriteCommandAction.runWriteCommandAction(project) {PsiDocumentManager.getInstance(project).getDocument(file)!!.setText(file.text.replace(before,after))}
        PsiDocumentManager.getInstance(project).commitAllDocuments()
    }
    private fun withheld(before:String,after:String,name:String="BadgeColumn") {
        val java=WicketLambdaColumnFixture.install(myFixture);change(before,after,name)
        val result=WicketLayoutSetupAnalyzer(factories=true).analyze(java.classes.single(),"root")
        assertNull(result.diagnostics.toString(),result.expansion)
    }
    private fun matched(report:dev.wicketlens.model.PageReport) {
        assertEquals(WicketReportFormatter().format(report),1,report.matching!!.findings.count {it.code==DiagnosticCode.MATCHED})
        assertEquals(listOf("title"),report.nodes.mapNotNull {it.wicketId})
        assertTrue(report.warnings.toString(),report.warnings.any {"Reviewed LambdaColumn constructor storage" in it.message})
    }
    fun testBoundSourceFunctionIsStoredWithoutCreatingCellOrDataModelBindings() {
        val java=WicketLambdaColumnFixture.install(myFixture)
        val report=WicketPageAnalyzer().analyze(java.classes.single())!!;matched(report)
        for(name in listOf("wicket-extensions-11.0.0.jar","wicket-util-11.0.0.jar","jdk-serializable-functional-1.9.0.jar","/RowFunction.java")) assertTrue(name,report.javaSourceFiles.any {it.endsWith(name)})
    }
    fun testBothCompiledConstructorDescriptorsExcludeNonconstructorMethods() {
        WicketLambdaColumnFixture.install(myFixture)
        val type=JavaPsiFacade.getInstance(project).findClass(WicketColumnConstruction.LAMBDA,GlobalSearchScope.allScope(project))!!
        assertEquals(2,type.constructors.count(WicketColumnConstruction::constructor))
        assertTrue(type.methods.filterNot {it.isConstructor}.none(WicketColumnConstruction::constructor))
        val other=JavaPsiFacade.getInstance(project).findClass("org.apache.wicket.extensions.markup.html.repeater.data.table.PropertyColumn",GlobalSearchScope.allScope(project))!!
        assertTrue(other.constructors.none(WicketColumnConstruction::constructor))
    }
    fun testUnsortableConstructorDelegationRetainsTheStorageContract() {
        val java=WicketLambdaColumnFixture.install(myFixture);change("super(model,sort,value::apply);","super(model,value::apply);")
        matched(WicketPageAnalyzer().analyze(java.classes.single())!!)
    }
    fun testStoredLambdaDoesNotInvokeTheRowFunction() {
        val java=WicketLambdaColumnFixture.install(myFixture);change("value::apply","row->value.apply(row)")
        matched(WicketPageAnalyzer().analyze(java.classes.single())!!)
    }
    fun testStoredUnboundSourceMethodReferenceDoesNotRunItsBody() {
        val java=WicketLambdaColumnFixture.install(myFixture)
        change("super(model,sort,value::apply);","super(model,sort,BadgeColumn::later);")
        change("private final RowFunction<String,String> style;","private static String later(String row){System.setProperty(\"later\",row);return row;}private final RowFunction<String,String> style;")
        matched(WicketPageAnalyzer().analyze(java.classes.single())!!)
    }
    fun testSignedConstantFieldDefaultsRemainOpaqueData() {
        val java=WicketLambdaColumnFixture.install(myFixture)
        change("private final RowFunction<String,String> style;","private int min=-1;private int max=+3;private boolean disabled=!true;private int mask=~0;private final RowFunction<String,String> style;")
        matched(WicketPageAnalyzer().analyze(java.classes.single())!!)
    }
    fun testUnaryMutationIsNotAConstantDefault()=withheld("private final RowFunction<String,String> style;","private int mutable=1;private int unknown=--mutable;private final RowFunction<String,String> style;")
    fun testUnaryCallsAreNotEvaluatedAsConstants()=withheld("private final RowFunction<String,String> style;","private int unknown=-Integer.parseInt(\"1\");private final RowFunction<String,String> style;")
    fun testEagerDataModelCreationIsNotConstructorStorage()=withheld("this.style=style;","this.style=style;getDataModel(Model.of(\"row\"));")
    fun testEagerRowFunctionCallsRemainUnreviewed()=withheld("this.style=style;","this.style=style;value.apply(\"row\");")
    fun testEagerDefaultFunctionCompositionIsNotStorage()=withheld("value::apply","value.andThen(value)::apply")
    fun testEagerBoundReceiverEvaluationCannotHideASourceCall() {
        val java=WicketLambdaColumnFixture.install(myFixture)
        change("private final RowFunction<String,String> style;","private static RowFunction<String,String> read(RowFunction<String,String> value){value.apply(\"row\");return value;}private final RowFunction<String,String> style;")
        change("value::apply","read(value)::apply")
        assertNull(WicketLayoutSetupAnalyzer(factories=true).analyze(java.classes.single(),"root").expansion)
    }
    fun testCallerCallbackInterfaceInitializationIsAudited()=withheld(" {}"," {int TOKEN=seed();static int seed(){System.setProperty(\"lambda\",\"effect\");return 1;}default String tag(){return \"tag\";}}","RowFunction")
    fun testCallerCallbackSubtypeCannotHideBehindAStableAlias() {
        val java=WicketLambdaColumnFixture.install(myFixture)
        myFixture.addFileToProject("src/main/java/example/EffectFunction.java","package example;interface EffectFunction extends RowFunction<String,String> {int TOKEN=seed();static int seed(){System.setProperty(\"lambda\",\"effect\");return 1;}default String tag(){return \"tag\";}}")
        change("java.util.List<BadgeColumn>","EffectFunction function=row->row;RowFunction<String,String> alias=function;java.util.List<BadgeColumn>","LambdaPage")
        change("value->value,value->\"accent\"","alias,value->\"accent\"","LambdaPage")
        assertNull(WicketLayoutSetupAnalyzer(factories=true).analyze(java.classes.single(),"root").expansion)
    }
    fun testIntersectionCallbackInitializationCannotDisappearAcrossAnAlias() {
        val java=WicketLambdaColumnFixture.install(myFixture)
        myFixture.addFileToProject("src/main/java/example/EffectMarker.java","package example;interface EffectMarker {int TOKEN=seed();static int seed(){System.setProperty(\"lambda\",\"effect\");return 1;}default String tag(){return \"tag\";}}")
        change("java.util.List<BadgeColumn>","RowFunction<String,String> alias=(RowFunction<String,String> & EffectMarker)(row->row);java.util.List<BadgeColumn>","LambdaPage")
        change("value->value,value->\"accent\"","alias,value->\"accent\"","LambdaPage")
        assertNull(WicketLayoutSetupAnalyzer(factories=true).analyze(java.classes.single(),"root").expansion)
    }
    fun testDeferredDataModelOverridesRemainOutsideTheInitialGraph() {
        val java=WicketLambdaColumnFixture.install(myFixture)
        change("@Override public void populateItem","@Override public IModel<?> getDataModel(IModel<String> row){return Model.of(row.getObject());}@Override public void populateItem")
        matched(WicketPageAnalyzer().analyze(java.classes.single())!!)
    }
    fun testACompiledGuardCannotBeReplacedByAnUnreviewedSourceImplementation() {
        WicketLambdaColumnFixture.install(myFixture)
        val unreviewed=(myFixture.addFileToProject("src/main/java/example/SourceArgs.java","package example;public class SourceArgs {public static <T>T notNull(T value,String name){System.setProperty(name,\"effect\");return value;}}") as PsiJavaFile).classes.single()
        val facade=JavaPsiFacade.getInstance(project);val scope=GlobalSearchScope.allScope(project)
        val constructor=facade.findClass(WicketColumnConstruction.LAMBDA,scope)!!.constructors.last()
        val contract=WicketColumnConstruction({},{},{null},{false},{_,_->},{_,message->throw IllegalStateException(message)},
            {if(it=="org.apache.wicket.util.lang.Args") unreviewed else facade.findClass(it,scope)})
        try {contract.reviewConstructor(constructor);fail("Expected the compiled guard requirement")}
        catch(failure:IllegalStateException){assertTrue(failure.message!!.contains("LambdaColumn null guard is unavailable or unreviewed"))}
    }
    fun testLaterStopsRetainTheReviewedConstructorNote() {
        val java=WicketLambdaColumnFixture.install(myFixture)
        change("add(new org.apache.wicket.markup.html.basic.Label","removeAll();add(new org.apache.wicket.markup.html.basic.Label","LambdaPage")
        val report=WicketPageAnalyzer().analyze(java.classes.single())!!
        assertTrue(report.matching!!.findings.none {it.code==DiagnosticCode.MATCHED})
        assertTrue(report.warnings.any {"Reviewed LambdaColumn constructor storage" in it.message})
    }
    fun testCancellationDuringCompiledGuardDependencyInspectionPropagates() {
        WicketLambdaColumnFixture.install(myFixture)
        val scope=GlobalSearchScope.allScope(project);val facade=JavaPsiFacade.getInstance(project)
        val constructor=facade.findClass(WicketColumnConstruction.LAMBDA,scope)!!.constructors.last()
        val indicator=EmptyProgressIndicator();var reached=false
        val tick:(PsiElement)->Unit={at -> if(at is PsiMethod && at.name=="notNull"){reached=true;indicator.cancel()};ProgressManager.checkCanceled()}
        assertThrows(ProcessCanceledException::class.java) {ProgressManager.getInstance().runProcess(Runnable {
            WicketColumnConstruction(tick,tick,{null},{false},{_,_->},{_,message->throw AssertionError(message)},{facade.findClass(it,scope)}).reviewConstructor(constructor)
        },indicator)}
        assertTrue(reached)
    }
}
