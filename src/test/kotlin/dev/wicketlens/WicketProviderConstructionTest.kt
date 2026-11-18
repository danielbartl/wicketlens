package dev.wicketlens

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.progress.*
import com.intellij.psi.*
import com.intellij.psi.search.GlobalSearchScope
import dev.wicketlens.analysis.*
import dev.wicketlens.model.DiagnosticCode
import dev.wicketlens.ui.WicketReportFormatter

class WicketProviderConstructionTest : WicketProjectPsiTestCase() {
    private fun change(before:String,after:String) {
        val file=PsiManager.getInstance(project).findFile(myFixture.findFileInTempDir("src/main/java/example/ProviderPage.java"))!!
        assertTrue(before in file.text)
        WriteCommandAction.runWriteCommandAction(project) {PsiDocumentManager.getInstance(project).getDocument(file)!!.setText(file.text.replace(before,after))}
        PsiDocumentManager.getInstance(project).commitAllDocuments()
    }
    private fun withheld(before:String,after:String) {
        val java=WicketProviderFixture.install(myFixture);change(before,after)
        val result=WicketLayoutSetupAnalyzer(factories=true).analyze(java.classes.single(),"root")
        assertNull(result.diagnostics.toString(),result.expansion)
    }
    private fun matched(report:dev.wicketlens.model.PageReport) {
        assertEquals(WicketReportFormatter().format(report),1,report.matching!!.findings.count {it.code==DiagnosticCode.MATCHED})
        assertEquals(listOf("title"),report.nodes.mapNotNull {it.wicketId})
        assertTrue(report.warnings.toString(),report.warnings.any {"Reviewed source data-provider construction" in it.message})
    }
    fun testInnerProviderConstructionDoesNotInvokeItsPageQueries() {
        val java=WicketProviderFixture.install(myFixture);val before=java.text to java.modificationStamp
        val report=WicketPageAnalyzer().analyze(java.classes.single())!!;matched(report)
        for(name in listOf("wicket-extensions-11.0.0.jar","wicket-core-11.0.0.jar","wicket-util-11.0.0.jar")) assertTrue(name,report.javaSourceFiles.any {it.endsWith(name)})
        assertEquals(before,java.text to java.modificationStamp)
    }
    fun testExplicitEmptyAndDirectSuperConstructorsHaveNoCallbacks() {
        val java=WicketProviderFixture.install(myFixture);change("@Override public java.util.Iterator","Rows(){super();}@Override public java.util.Iterator")
        matched(WicketPageAnalyzer().analyze(java.classes.single())!!)
    }
    fun testSourceWrapperWithImplicitDelegationKeepsDeferredCallbacks() {
        val java=WicketProviderFixture.install(myFixture)
        myFixture.addFileToProject("src/main/java/example/ProviderBase.java","package example;public abstract class ProviderBase extends org.apache.wicket.extensions.markup.html.repeater.util.SortableDataProvider<String,String> {public ProviderBase(){super();}}")
        change("class Rows extends SortableDataProvider<String,String>","class Rows extends ProviderBase")
        matched(WicketPageAnalyzer().analyze(java.classes.single())!!)
    }
    fun testAnonymousProviderConstructionIsSeparateFromItsCallbackBodies() {
        val java=WicketProviderFixture.install(myFixture)
        change("Rows rows=new Rows();","var rows=new SortableDataProvider<String,String>() {public java.util.Iterator<String> iterator(long first,long count){return filtered().iterator();}public long size(){return filtered().size();}public IModel<String> model(String row){return Model.of(row);}};")
        matched(WicketPageAnalyzer().analyze(java.classes.single())!!)
    }
    fun testLaterCallbackComponentAdditionsDoNotBecomeInitialChildren() {
        val java=WicketProviderFixture.install(myFixture)
        change("return filtered().iterator();","ProviderPage.this.add(new org.apache.wicket.markup.html.basic.Label(\"later\"));return filtered().iterator();")
        matched(WicketPageAnalyzer().analyze(java.classes.single())!!)
    }
    fun testStaticSerializationConstantsAreReviewedWithoutInstanceState() {
        val java=WicketProviderFixture.install(myFixture)
        change("@Override public java.util.Iterator","private static final long serialVersionUID=1L;@Override public java.util.Iterator")
        matched(WicketPageAnalyzer().analyze(java.classes.single())!!)
    }
    private fun depth(count:Int):PsiJavaFile {
        val java=WicketProviderFixture.install(myFixture)
        for(i in 0 until count) myFixture.addFileToProject("src/main/java/example/ProviderBase$i.java","package example;public abstract class ProviderBase$i extends "+(if(i+1==count) WicketProviderConstruction.BASE+"<String,String>" else "ProviderBase${i+1}")+" {}")
        change("class Rows extends SortableDataProvider<String,String>","class Rows extends ProviderBase0")
        return java
    }
    fun testTwelveSourceLevelsKeepTheConstructionOnlyBoundary() {
        val java=depth(11);matched(WicketPageAnalyzer().analyze(java.classes.single())!!)
    }
    fun testThirteenSourceLevelsRemainOutsideTheContract() {
        val java=depth(12);val rows=java.classes.single().innerClasses.single()
        assertFalse(WicketProviderConstruction.sourceProvider(rows))
        assertTrue(WicketPageAnalyzer().analyze(java.classes.single())!!.warnings.none {"Reviewed source data-provider construction" in it.message})
    }
    fun testAConstructorQueryIsNotADeferredCallback()=withheld("@Override public java.util.Iterator","Rows(){size();}@Override public java.util.Iterator")
    fun testConstructorDelegationCannotBeRecursive()=withheld("@Override public java.util.Iterator","Rows(){this();}@Override public java.util.Iterator")
    fun testQualifiedSuperCannotEvaluateAnUnreviewedOuterInstance() {
        val java=WicketProviderFixture.install(myFixture)
        myFixture.addFileToProject("src/main/java/example/OtherOwner.java","package example;public class OtherOwner {public OtherOwner(){System.setProperty(\"provider\",\"effect\");}public abstract class Base extends org.apache.wicket.extensions.markup.html.repeater.util.SortableDataProvider<String,String> {}}")
        change("class Rows extends SortableDataProvider<String,String>","class Rows extends OtherOwner.Base")
        change("@Override public java.util.Iterator","Rows(){new OtherOwner().super();}@Override public java.util.Iterator")
        assertNull(WicketLayoutSetupAnalyzer(factories=true).analyze(java.classes.single(),"root").expansion)
    }
    fun testInstanceInitializersAreNotCallbacks()=withheld("@Override public java.util.Iterator","{filtered();}@Override public java.util.Iterator")
    fun testStaticInitializersStayOutsideTheContract()=withheld("@Override public java.util.Iterator","static {System.setProperty(\"provider\",\"effect\");}@Override public java.util.Iterator")
    fun testInstanceStateIsNotSilentlyTreatedAsPureStorage()=withheld("@Override public java.util.Iterator","private Object state;@Override public java.util.Iterator")
    fun testFieldInitializersCannotInvokeThePage()=withheld("@Override public java.util.Iterator","private final Object rows=filtered();@Override public java.util.Iterator")
    fun testConstructorInputsRemainUnreviewed() {
        val java=WicketProviderFixture.install(myFixture)
        change("@Override public java.util.Iterator","Rows(Object ignored){super();}@Override public java.util.Iterator")
        change("Rows rows=new Rows();","Rows rows=new Rows(null);")
        assertNull(WicketLayoutSetupAnalyzer(factories=true).analyze(java.classes.single(),"root").expansion)
    }
    fun testFinalizersAndObjectHooksAreNotDeferredProviderSlots()=withheld("@Override public java.util.Iterator","@Override public String toString(){filtered();return \"rows\";}@Override public java.util.Iterator")
    fun testDirectIteratorCallsRemainUnreviewed()=withheld("Rows rows=new Rows();","Rows rows=new Rows();rows.iterator(0,1);")
    fun testDirectSizeCallsRemainUnreviewed()=withheld("Rows rows=new Rows();","Rows rows=new Rows();rows.size();")
    fun testDirectModelCallsRemainUnreviewed()=withheld("Rows rows=new Rows();","Rows rows=new Rows();rows.model(\"row\");")
    fun testDirectDetachCallsRemainUnreviewed()=withheld("Rows rows=new Rows();","Rows rows=new Rows();rows.detach();")
    fun testAConsumerCannotHideAnEagerProviderCallback() {
        val java=WicketProviderFixture.install(myFixture)
        change("private java.util.List<String> filtered()","private void consume(org.apache.wicket.markup.repeater.data.IDataProvider<String> provider){provider.size();}private java.util.List<String> filtered()")
        change("Rows rows=new Rows();","Rows rows=new Rows();consume(rows);")
        assertNull(WicketLayoutSetupAnalyzer(factories=true).analyze(java.classes.single(),"root").expansion)
    }
    fun testInterfaceInitializationIsAuditedBeforeConstruction() {
        val java=WicketProviderFixture.install(myFixture)
        myFixture.addFileToProject("src/main/java/example/InitMarker.java","package example;interface InitMarker {int TOKEN=seed();static int seed(){System.setProperty(\"provider\",\"effect\");return 1;}default String tag(){return \"tag\";}}")
        change("class Rows extends SortableDataProvider<String,String>","class Rows extends SortableDataProvider<String,String> implements InitMarker")
        assertNull(WicketLayoutSetupAnalyzer(factories=true).analyze(java.classes.single(),"root").expansion)
    }
    fun testAdditionalFunctionalInterfacesKeepTheirEagerEffectAudit() {
        val java=WicketProviderFixture.install(myFixture)
        change("class Rows extends SortableDataProvider<String,String>","class Rows extends SortableDataProvider<String,String> implements java.util.function.Supplier<String>")
        change("@Override public java.util.Iterator","public String get(){return filtered().get(0);}@Override public java.util.Iterator")
        assertNull(WicketLayoutSetupAnalyzer(factories=true).analyze(java.classes.single(),"root").expansion)
    }
    fun testLaterStopsRetainTheProviderConstructionNote() {
        val java=WicketProviderFixture.install(myFixture);change("Rows rows=new Rows();","Rows rows=new Rows();removeAll();")
        val report=WicketPageAnalyzer().analyze(java.classes.single())!!
        assertTrue(report.matching!!.findings.none {it.code==DiagnosticCode.MATCHED})
        assertTrue(report.warnings.any {"Reviewed source data-provider construction" in it.message})
    }
    fun testCancellationDuringSortStateDependencyRecordingPropagates() {
        val java=WicketProviderFixture.install(myFixture)
        val creation=com.intellij.psi.util.PsiTreeUtil.findChildrenOfType(java,PsiNewExpression::class.java).single {it.classReference?.referenceName=="Rows"}
        val indicator=EmptyProgressIndicator();var reached=false
        val tick:(PsiElement)->Unit={at->if(at is PsiClass && at.qualifiedName=="org.apache.wicket.extensions.markup.html.repeater.util.SingleSortState"){reached=true;indicator.cancel()};ProgressManager.checkCanceled()}
        assertThrows(ProcessCanceledException::class.java) {ProgressManager.getInstance().runProcess(Runnable {
            WicketProviderConstruction(tick,tick,{null},{},{JavaPsiFacade.getInstance(project).findClass(it,GlobalSearchScope.allScope(project))},{_,message->throw AssertionError(message)}).inspect(creation)
        },indicator)}
        assertTrue(reached)
    }
}
