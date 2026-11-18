package dev.wicketlens

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.progress.*
import com.intellij.psi.*
import com.intellij.psi.util.PsiTreeUtil
import dev.wicketlens.analysis.*
import dev.wicketlens.model.DiagnosticCode

class WicketNavigationToolbarTest:WicketProjectPsiTestCase() {
    private fun change(before:String,after:String) {
        val file=PsiManager.getInstance(project).findFile(myFixture.findFileInTempDir("src/main/java/example/TableShell.java"))!!
        assertTrue(before in file.text)
        WriteCommandAction.runWriteCommandAction(project) {PsiDocumentManager.getInstance(project).getDocument(file)!!.setText(file.text.replace(before,after))}
        PsiDocumentManager.getInstance(project).commitAllDocuments()
    }
    private fun withheld(before:String,after:String) {
        val java=WicketNavigationToolbarFixture.install(myFixture);change(before,after)
        val result=WicketLayoutSetupAnalyzer(factories=true).analyze(java.classes.single(),"root")
        assertNull(result.diagnostics.toString(),result.expansion)
        assertTrue(result.diagnostics.toString(),result.diagnostics.any {it.message.startsWith("Source layout setup stopped:")})
    }
    private fun matched(java:PsiJavaFile) {
        val report=WicketPageAnalyzer().analyze(java.classes.single())!!
        assertEquals(report.warnings.toString(),1,report.warnings.count {"Reviewed fresh-table navigation-toolbar" in it.message})
        assertEquals(report.warnings.toString(),2,report.warnings.count {"Reviewed owned DataTable toolbar registration" in it.message})
        assertEquals(report.warnings.toString(),2,report.matching!!.findings.count {it.code==DiagnosticCode.MATCHED})
        assertEquals(setOf("shell","title"),report.nodes.mapNotNull {it.wicketId}.toSet())
    }
    fun testEagerNavigatorConstructionKeepsLaterLinkInitializationUnknown() {
        val java=WicketNavigationToolbarFixture.install(myFixture)
        matched(java)
        assertTrue(WicketPageAnalyzer().analyze(java.classes.single())!!.warnings.any {"paging-link initialization" in it.message && "provider data" in it.message})
    }
    fun testCompiledAjaxPagerCanBeReturnedByTheEagerFactory() {
        val java=WicketNavigationToolbarFixture.install(myFixture)
        change("new Pager(id,table)","new org.apache.wicket.ajax.markup.html.navigation.paging.AjaxPagingNavigator(id,table)")
        matched(java)
    }
    fun testCompiledDefaultPagerCanBeReturnedByTheEagerFactory() {
        val java=WicketNavigationToolbarFixture.install(myFixture)
        change("new Pager(id,table){@Override protected void onAjaxEvent(org.apache.wicket.ajax.AjaxRequestTarget target){target.add(table);}}","new org.apache.wicket.markup.html.navigation.paging.PagingNavigator(id,table)")
        matched(java)
    }
    fun testLaterPagerFactoryBodiesAreNotExecuted() {
        val java=WicketNavigationToolbarFixture.install(myFixture)
        change("return super.newNavigation(id,pageable,labels);","TableShell.this.add(new org.apache.wicket.markup.html.basic.Label(\"later\"));return super.newNavigation(id,pageable,labels);")
        matched(java)
    }
    fun testUnusedPureThreeInputPagerConstructorIsAudited() {
        val java=WicketNavigationToolbarFixture.install(myFixture)
        change("Pager(String id,", "Pager(String id,org.apache.wicket.markup.html.navigation.paging.IPageable pageable,org.apache.wicket.markup.html.navigation.paging.IPagingLabelProvider labels){super(id,pageable,labels);}Pager(String id,")
        matched(java)
    }
    fun testUnusedPagerConstructorCannotHideEagerEffects()=withheld("Pager(String id,", "Pager(String id,org.apache.wicket.markup.html.navigation.paging.IPageable pageable,org.apache.wicket.markup.html.navigation.paging.IPagingLabelProvider labels){super(id,pageable,labels);TableShell.this.removeAll();}Pager(String id,")
    fun testInheritedEagerFactoryUsesItsDeclaredSourceImplementation() {
        val java=WicketNavigationToolbarFixture.install(myFixture)
        change("private final class Nav extends NavigationToolbar", "private class BaseNav extends NavigationToolbar")
        change("Nav(DataTable<?,?> table)", "BaseNav(DataTable<?,?> table)")
        change("public TableShell(String id)", "private final class Nav extends BaseNav {Nav(DataTable<?,?> table){super(table);}}public TableShell(String id)")
        matched(java)
    }
    fun testConcreteEagerFactoryDoesNotInvokeUnusedBaseOverride() {
        val java=WicketNavigationToolbarFixture.install(myFixture)
        change("private final class Nav extends NavigationToolbar", "private class BaseNav extends NavigationToolbar")
        change("Nav(DataTable<?,?> table)", "BaseNav(DataTable<?,?> table)")
        change("return new Pager(id,table){@Override protected void onAjaxEvent(org.apache.wicket.ajax.AjaxRequestTarget target){target.add(table);}};", "TableShell.this.getPage();return super.newPagingNavigator(id,table);")
        change("public TableShell(String id)", "private final class Nav extends BaseNav {Nav(DataTable<?,?> table){super(table);}@Override protected org.apache.wicket.markup.html.navigation.paging.PagingNavigator newPagingNavigator(String id,DataTable<?,?> table){return new Pager(id,table);}}public TableShell(String id)")
        matched(java)
    }
    fun testQualifiedEagerNavigatorAllocationIsUnreviewed()=withheld("new Pager(id,table)","TableShell.this.new Pager(id,table)")
    fun testNavigationSerializationConstantsAreAllowed() {
        val java=WicketNavigationToolbarFixture.install(myFixture)
        change("Nav(DataTable<?,?> table)","private static final long serialVersionUID=1L;Nav(DataTable<?,?> table)")
        change("Pager(String id,","private static final long serialVersionUID=1L;Pager(String id,")
        matched(java)
    }
    fun testCustomNavigatorLabelFactoryIsUnreviewed()=withheld("Nav(DataTable<?,?> table)","protected org.apache.wicket.Component newNavigatorLabel(String id,DataTable<?,?> table){return new org.apache.wicket.markup.html.basic.Label(id); }Nav(DataTable<?,?> table)")
    fun testNavigationInitializeOverrideIsUnreviewed()=withheld("Nav(DataTable<?,?> table)","protected void onInitialize(){super.onInitialize();}Nav(DataTable<?,?> table)")
    fun testNavigationInstanceStateIsUnreviewed()=withheld("Nav(DataTable<?,?> table)","private int state;Nav(DataTable<?,?> table)")
    fun testNavigationInitializerIsUnreviewed()=withheld("Nav(DataTable<?,?> table)","{TableShell.this.removeAll();}Nav(DataTable<?,?> table)")
    fun testNavigationConstructorExtraEffectsAreUnreviewed()=withheld("super(table);table.setOutputMarkupId(true);","super(table);table.setOutputMarkupId(true);TableShell.this.removeAll();")
    fun testNavigationConstructorComputedOutputFlagIsUnreviewed()=withheld("table.setOutputMarkupId(true);}","table.setOutputMarkupId(table.isVisible());}")
    fun testNavigationConstructorCannotMutateItsOuterTable()=withheld("super(table);table.setOutputMarkupId(true);","super(table);TableShell.this.table.setOutputMarkupId(true);")
    fun testEagerFactoryExtraStatementsAreUnreviewed()=withheld("return new Pager(id,table)","TableShell.this.removeAll();return new Pager(id,table)")
    fun testEagerFactoryComputedIdIsUnreviewed()=withheld("new Pager(id,table)","new Pager(id+\"other\",table)")
    fun testEagerFactoryBorrowedPageableIsUnreviewed()=withheld("new Pager(id,table)","new Pager(id,TableShell.this.table)")
    fun testEagerFactoryAliasIsUnreviewed()=withheld("return new Pager(id,table)","String alias=id;return new Pager(alias,table)")
    fun testEagerFactorySuperDelegationIsUnreviewed()=withheld("return new Pager(id,table){@Override protected void onAjaxEvent(org.apache.wicket.ajax.AjaxRequestTarget target){target.add(table);}};","return super.newPagingNavigator(id,table);")
    fun testSynchronizedEagerFactoryIsUnreviewed()=withheld("protected org.apache.wicket.markup.html.navigation.paging.PagingNavigator newPagingNavigator","protected synchronized org.apache.wicket.markup.html.navigation.paging.PagingNavigator newPagingNavigator")
    fun testPagerInstanceStateIsUnreviewed()=withheld("Pager(String id,","private int state;Pager(String id,")
    fun testPagerStaticInitializerIsUnreviewed()=withheld("Pager(String id,","static {System.setProperty(\"pager\",\"effect\");}Pager(String id,")
    fun testPagerConstructorEffectsAreUnreviewed()=withheld("super(id,pageable);","super(id,pageable);TableShell.this.removeAll();")
    fun testPagerConstructorComputedInputIsUnreviewed()=withheld("super(id,pageable);","super(id+\"other\",pageable);")
    fun testPagerInitializeOverrideIsUnreviewed()=withheld("Pager(String id,","protected void onInitialize(){super.onInitialize();}Pager(String id,")
    fun testPagerObjectHooksAreUnreviewed()=withheld("Pager(String id,","public String toString(){return \"pager\";}Pager(String id,")
    fun testNavigationInterfaceInitializationIsAudited() {
        val java=WicketNavigationToolbarFixture.install(myFixture)
        myFixture.addFileToProject("src/main/java/example/NavMarker.java","package example;interface NavMarker {int TOKEN=seed();static int seed(){System.setProperty(\"nav\",\"effect\");return 1;}default String tag(){return \"tag\";}}")
        change("Nav extends NavigationToolbar {","Nav extends NavigationToolbar implements NavMarker {")
        assertNull(WicketLayoutSetupAnalyzer(factories=true).analyze(java.classes.single(),"root").expansion)
    }
    fun testPagerInterfaceInitializationIsAudited() {
        val java=WicketNavigationToolbarFixture.install(myFixture)
        myFixture.addFileToProject("src/main/java/example/PagerMarker.java","package example;interface PagerMarker {int TOKEN=seed();static int seed(){System.setProperty(\"pager\",\"effect\");return 1;}default String tag(){return \"tag\";}}")
        change("extends org.apache.wicket.ajax.markup.html.navigation.paging.AjaxPagingNavigator {","extends org.apache.wicket.ajax.markup.html.navigation.paging.AjaxPagingNavigator implements PagerMarker {")
        assertNull(WicketLayoutSetupAnalyzer(factories=true).analyze(java.classes.single(),"root").expansion)
    }
    fun testCompletedNavigationNotesSurviveTheNextIndependentStop() {
        val java=WicketNavigationToolbarFixture.install(myFixture)
        change("add(table);","add(table);getPage();")
        val result=WicketLayoutSetupAnalyzer(factories=true).analyze(java.classes.single(),"root")
        assertNull(result.expansion)
        assertEquals(result.diagnostics.toString(),1,result.diagnostics.count {"Reviewed fresh-table navigation-toolbar" in it.message})
    }
    private fun pagerDepth(count:Int):WicketDataTableToolbars.Proof {
        WicketNavigationToolbarFixture.install(myFixture)
        for(i in 0 until count) myFixture.addFileToProject("src/main/java/example/PagerBase$i.java","package example;public class PagerBase$i extends "+
            (if(i+1==count) "org.apache.wicket.ajax.markup.html.navigation.paging.AjaxPagingNavigator" else "PagerBase${i+1}")+
            " {public PagerBase$i(String id,org.apache.wicket.markup.html.navigation.paging.IPageable pageable){super(id,pageable);}}")
        change("extends org.apache.wicket.ajax.markup.html.navigation.paging.AjaxPagingNavigator {","extends PagerBase0 {")
        return proof()
    }
    private fun proof(source:(PsiElement)->Unit={}):WicketDataTableToolbars.Proof {
        change("table.addTopToolbar(new Header(table,null));","")
        val type=JavaPsiFacade.getInstance(project).findClass("example.TableShell",com.intellij.psi.search.GlobalSearchScope.allScope(project))!!
        val call=PsiTreeUtil.findChildrenOfType(type,PsiMethodCallExpression::class.java).first {it.methodExpression.referenceName=="addBottomToolbar"}
        return WicketDataTableToolbars({ProgressManager.checkCanceled()},source,{null},{},{},{JavaPsiFacade.getInstance(project).findClass(it,type.resolveScope)},
            {_,message->throw IllegalStateException(message)}).registration(call)
    }
    fun testTwelveSourcePagerLevelsRetainTheConstructionProof()=assertTrue(pagerDepth(10).navigation)
    fun testThirteenSourcePagerLevelsRemainUnreviewed() {assertThrows(IllegalStateException::class.java) {pagerDepth(11)}}
    fun testCancellationDuringNavigationDependencyRecordingPropagates() {
        WicketNavigationToolbarFixture.install(myFixture)
        val indicator=EmptyProgressIndicator();var reached=false
        assertThrows(ProcessCanceledException::class.java) {ProgressManager.getInstance().runProcess(Runnable {
            proof {at ->if(at is PsiClass && at.qualifiedName=="org.apache.wicket.extensions.markup.html.repeater.data.table.NavigatorLabel"){reached=true;indicator.cancel()};ProgressManager.checkCanceled()}
        },indicator)}
        assertTrue(reached)
    }
}
