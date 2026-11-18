package dev.wicketlens

import dev.wicketlens.analysis.WicketPageAnalyzer
import dev.wicketlens.model.DiagnosticCode
import dev.wicketlens.ui.WicketReportFormatter
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.progress.*
import com.intellij.psi.*
import dev.wicketlens.analysis.WicketLayoutSetupAnalyzer
import dev.wicketlens.analysis.WicketColumnConstruction

class WicketColumnConstructionTest : WicketProjectPsiTestCase() {
    private fun change(before:String,after:String,name:String="StorageColumn") {
        val file=PsiManager.getInstance(project).findFile(myFixture.findFileInTempDir("src/main/java/example/$name.java"))!!
        assertTrue(file.text,before in file.text)
        WriteCommandAction.runWriteCommandAction(project) {PsiDocumentManager.getInstance(project).getDocument(file)!!.setText(file.text.replace(before,after))}
        PsiDocumentManager.getInstance(project).commitAllDocuments()
    }
    private fun analyze(before:String,after:String,name:String="StorageColumn") = WicketColumnConstructionFixture.install(myFixture).let { java ->
        change(before,after,name);WicketPageAnalyzer().analyze(java.classes.single())!!
    }
    private fun withheld(before:String,after:String,name:String="StorageColumn") {
        val java=WicketColumnConstructionFixture.install(myFixture)
        change(before,after,name)
        val result=WicketLayoutSetupAnalyzer(factories=true).analyze(java.classes.single(),"root")
        assertNull(result.diagnostics.toString(),result.expansion)
    }
    fun testSourceColumnStorageDoesNotExecuteHeaderOrCellCallbacks() {
        val java=WicketColumnConstructionFixture.install(myFixture)
        val report=WicketPageAnalyzer().analyze(java.classes.single())!!
        assertEquals(WicketReportFormatter().format(report),1,report.matching!!.findings.count {it.code==DiagnosticCode.MATCHED})
        assertEquals(listOf("title"),report.nodes.mapNotNull {it.wicketId})
        assertTrue(report.warnings.toString(),report.warnings.any {"column" in it.message.lowercase() && "constructor" in it.message.lowercase()})
        assertTrue(report.javaSourceFiles.any {it.endsWith("wicket-extensions-11.0.0.jar")})
    }
    fun testBothReviewedConstructorSignaturesRemainStorageOnly() {
        val report=analyze("super(Model.of(\"\"));","super(model,\"name\");")
        assertEquals(report.warnings.toString(),1,report.matching!!.findings.count {it.code==DiagnosticCode.MATCHED})
        val type=JavaPsiFacade.getInstance(project).findClass(WicketColumnConstruction.ABSTRACT,com.intellij.psi.search.GlobalSearchScope.allScope(project))!!
        assertEquals(2,type.constructors.count {WicketColumnConstruction.constructor(it)})
        assertTrue(type.methods.filterNot {it.isConstructor}.none {WicketColumnConstruction.constructor(it)})
    }
    fun testEagerDetachIsNotDeferred()=withheld("this.key=key;","this.key=key;detach();")
    fun testEagerPopulationIsNotDeferred()=withheld("this.key=key;","this.key=key;populateItem(null,\"cell\",model);")
    fun testEagerHeaderCreationIsNotDeferred()=withheld("this.key=key;","this.key=key;getHeader(\"header\");")
    fun testEagerCallbackInvocationIsNotStorage()=withheld("this.key=key;","this.key=key;key.apply(\"row\");")
    fun testModelGettersCannotRunDuringColumnConstruction()=withheld("this.key=key;","this.key=key;model.getObject();")
    fun testSourceHelpersCannotHideConstructorEffects() {
        val java=WicketColumnConstructionFixture.install(myFixture)
        change("public StorageColumn(IModel<String> model)","private static IModel<String> read(IModel<String> model){model.getObject();return model;}public StorageColumn(IModel<String> model)")
        change("super(Model.of(\"\"));","super(read(model));")
        val result=WicketLayoutSetupAnalyzer(factories=true).analyze(java.classes.single(),"root")
        assertNull(result.expansion)
        assertTrue(result.diagnostics.toString(),result.diagnostics.any {"outside storage-only column construction" in it.message})
    }
    fun testComponentFieldsCannotBeInitializedAsColumnData()=withheld("private final IModel<String> model;","private final org.apache.wicket.Component hidden=new Label(\"hidden\");private final IModel<String> model;")
    fun testInitializerBlocksRemainUnreviewed()=withheld("private final IModel<String> model;","{System.setProperty(\"column\",\"effect\");}private final IModel<String> model;")
    fun testStaticInitializationCannotHideGraphEffects()=withheld("private final IModel<String> model;","static {System.setProperty(\"column\",\"effect\");}private final IModel<String> model;")
    fun testFinalizersDoNotAcquireAStorageContract()=withheld("private final IModel<String> model;","@Override protected void finalize(){System.setProperty(\"column\",\"effect\");}private final IModel<String> model;")
    fun testForeignStorageWritesRemainUnresolved() {
        val java=WicketColumnConstructionFixture.install(myFixture)
        change("private final IModel<String> model;","private static Object escaped;private final IModel<String> model;")
        change("this.key=key;","this.key=key;escaped=key;")
        val result=WicketLayoutSetupAnalyzer(factories=true).analyze(java.classes.single(),"root")
        assertNull(result.expansion)
        assertTrue(result.diagnostics.toString(),result.diagnostics.any {"private instance data storage" in it.message})
    }
    fun testSourceControlFlowIsNotAssumedToReachLaterBindings()=withheld("this.key=key;","this.key=key;if(model==null)return;")
    fun testAnonymousDataConstructionIsNotAStorageSummary()=withheld("super(Model.of(\"\"));","super(new Model<String>() {{System.setProperty(\"column\",\"effect\");}});")
    fun testColumnDataExpressionDepthIsBounded()=withheld("super(Model.of(\"\"));","super("+"(".repeat(13)+"model"+")".repeat(13)+");")
    fun testCallbackInterfaceAncestryIsBounded() {
        val java=WicketColumnConstructionFixture.install(myFixture)
        change("java.util.function.Function<String,String>","Deep0")
        for(i in 0..12) myFixture.addFileToProject("src/main/java/example/Deep$i.java","package example;interface Deep$i extends "+(if(i==12) "java.util.function.Function<String,String>" else "Deep${i+1}")+" {}")
        val result=WicketLayoutSetupAnalyzer(factories=true).analyze(java.classes.single(),"root")
        assertNull(result.expansion)
        assertTrue(result.diagnostics.toString(),result.diagnostics.any {"interface initialization exceeds" in it.message})
    }
    fun testAStoredCallbackMayReferenceAnUnusedFutureCellId() {
        val report=analyze("item.add(new Label(id,key.apply(row.getObject())));","item.add(new Label(\"future\",key.apply(row.getObject())));")
        assertEquals(report.warnings.toString(),1,report.matching!!.findings.count {it.code==DiagnosticCode.MATCHED})
        assertTrue(report.nodes.none {it.wicketId=="future"})
    }
    fun testSourceCallbackInterfacesWithOnlyMarkersHaveNoEagerInitialization() {
        val java=WicketColumnConstructionFixture.install(myFixture)
        change("java.util.function.Function<String,String>","PlainFunction")
        myFixture.addFileToProject("src/main/java/example/PlainFunction.java","package example;interface PlainFunction extends java.util.function.Function<String,String>,java.io.Serializable {}")
        val report=WicketPageAnalyzer().analyze(java.classes.single())!!
        assertEquals(report.warnings.toString(),1,report.matching!!.findings.count {it.code==DiagnosticCode.MATCHED})
    }
    fun testCallbackInterfaceInitializationCannotRunAtLambdaCreation() {
        val java=WicketColumnConstructionFixture.install(myFixture)
        change("java.util.function.Function<String,String>","InitFunction")
        myFixture.addFileToProject("src/main/java/example/InitFunction.java","package example;interface InitFunction extends java.util.function.Function<String,String> {int TOKEN=seed();static int seed(){System.setProperty(\"column\",\"effect\");return 1;}default String tag(){return \"tag\";}}")
        val result=WicketLayoutSetupAnalyzer(factories=true).analyze(java.classes.single(),"root")
        assertNull(result.expansion)
        assertTrue(result.diagnostics.toString(),result.diagnostics.any {"interface initialization" in it.message})
    }
    fun testErasedComponentInputsCannotEscapeIntoAColumn() {
        val java=WicketColumnConstructionFixture.install(myFixture)
        change("public class ColumnPage extends org.apache.wicket.markup.html.WebPage {public ColumnPage(){","public class ColumnPage extends org.apache.wicket.markup.html.WebPage implements org.apache.wicket.model.IModel<String> {public String getObject(){return \"root\";}public ColumnPage(){Object erased=this;","ColumnPage")
        change("org.apache.wicket.model.Model.of(\"Header\")","(org.apache.wicket.model.IModel<String>)erased","ColumnPage")
        assertNull(WicketLayoutSetupAnalyzer(factories=true).analyze(java.classes.single(),"root").expansion)
    }
    private fun depth(count:Int):dev.wicketlens.model.PageReport {
        val java=WicketColumnConstructionFixture.install(myFixture)
        for(i in 0 until count) myFixture.addFileToProject("src/main/java/example/Base$i.java","package example;public abstract class Base$i extends "+(if(i+1==count) "org.apache.wicket.extensions.markup.html.repeater.data.table.AbstractColumn<String,String>" else "Base${i+1}")+" {public Base$i(org.apache.wicket.model.IModel<String> model){super(model);}}")
        change("extends AbstractColumn<String,String>","extends Base0")
        return WicketPageAnalyzer().analyze(java.classes.single())!!
    }
    fun testBoundedSourceSuperclassStorage() {
        val report=depth(5)
        assertEquals(report.warnings.toString(),1,report.matching!!.findings.count {it.code==DiagnosticCode.MATCHED})
    }
    fun testTwelveSourceLevelsDoNotRaiseTheExistingSourceCallLimit() {
        val report=depth(11)
        val type=JavaPsiFacade.getInstance(project).findClass("example.StorageColumn",com.intellij.psi.search.GlobalSearchScope.projectScope(project))!!
        assertTrue(WicketColumnConstruction.sourceColumn(type))
        assertTrue(report.warnings.toString(),report.warnings.any {"source-call bound" in it.message})
    }
    fun testThirteenSourceLevelsRemainOutsideTheBound() {
        val report=depth(12)
        val type=JavaPsiFacade.getInstance(project).findClass("example.StorageColumn",com.intellij.psi.search.GlobalSearchScope.projectScope(project))!!
        assertFalse(WicketColumnConstruction.sourceColumn(type))
        assertTrue(report.warnings.none {"Reviewed AbstractColumn constructor storage" in it.message})
    }
    fun testLaterFailuresRetainTheReviewedConstructorNoteWithoutAPartialGraph() {
        val report=analyze("add(new org.apache.wicket.markup.html.basic.Label(\"title\",\"Ready\"));","removeAll();add(new org.apache.wicket.markup.html.basic.Label(\"title\",\"Ready\"));","ColumnPage")
        assertTrue(report.matching!!.findings.none {it.code==DiagnosticCode.MATCHED})
        assertTrue(report.warnings.any {"Reviewed AbstractColumn constructor storage" in it.message})
    }
    fun testCancellationDuringColumnFieldInspectionPropagates() {
        WicketColumnConstructionFixture.install(myFixture)
        val type=JavaPsiFacade.getInstance(project).findClass("example.StorageColumn",com.intellij.psi.search.GlobalSearchScope.projectScope(project))!!
        val indicator=EmptyProgressIndicator();var reached=false
        val tick:(PsiElement)->Unit={at -> if(at is PsiField && at.name=="model"){reached=true;indicator.cancel()};ProgressManager.checkCanceled()}
        assertThrows(ProcessCanceledException::class.java) {ProgressManager.getInstance().runProcess(Runnable {
            WicketColumnConstruction(tick,tick,{null},{false},{_,_->},{_,message->throw AssertionError(message)}).inspect(type)
        },indicator)}
        assertTrue(reached)
    }
    fun testAnalysisIsReadOnlyAndCancellationPropagates() {
        val java=WicketColumnConstructionFixture.install(myFixture);val before=java.text to java.modificationStamp
        assertEquals(1,WicketPageAnalyzer().analyze(java.classes.single())!!.matching!!.findings.count {it.code==DiagnosticCode.MATCHED})
        assertEquals(before,java.text to java.modificationStamp)
        val indicator=EmptyProgressIndicator()
        assertThrows(ProcessCanceledException::class.java) {ProgressManager.getInstance().runProcess(Runnable {indicator.cancel();WicketPageAnalyzer().analyze(java.classes.single())},indicator)}
    }
}
