package dev.wicketlens

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.progress.*
import com.intellij.psi.*
import com.intellij.psi.util.PsiTreeUtil
import dev.wicketlens.analysis.*
import dev.wicketlens.model.DiagnosticCode

class WicketToolbarRegistrationTest:WicketProjectPsiTestCase() {
    private fun change(before:String,after:String) {
        val file=PsiManager.getInstance(project).findFile(myFixture.findFileInTempDir("src/main/java/example/TableShell.java"))!!
        assertTrue(before in file.text)
        WriteCommandAction.runWriteCommandAction(project) {PsiDocumentManager.getInstance(project).getDocument(file)!!.setText(file.text.replace(before,after))}
        PsiDocumentManager.getInstance(project).commitAllDocuments()
    }
    private fun withheld(before:String,after:String) {
        val java=WicketToolbarRegistrationFixture.install(myFixture);change(before,after)
        val result=WicketLayoutSetupAnalyzer(factories=true).analyze(java.classes.single(),"root")
        assertNull(result.diagnostics.toString(),result.expansion)
        assertTrue(result.diagnostics.toString(),result.diagnostics.any {it.message.startsWith("Source layout setup stopped:")})
    }
    private fun matched(java:PsiJavaFile) {
        val report=WicketPageAnalyzer().analyze(java.classes.single())!!
        assertEquals(report.warnings.toString(),2,report.warnings.count {"Reviewed owned DataTable toolbar registration" in it.message})
        assertEquals(report.warnings.toString(),2,report.matching!!.findings.count {it.code==DiagnosticCode.MATCHED})
        assertEquals(setOf("shell","title"),report.nodes.mapNotNull {it.wicketId}.toSet())
    }
    fun testFreshOwnedTableRegistersHeadersWithoutPublishingPrivateChildren() {
        val java=WicketToolbarRegistrationFixture.install(myFixture)
        val report=WicketPageAnalyzer().analyze(java.classes.single())!!
        assertEquals(report.warnings.toString(),2,report.warnings.count {"Reviewed owned DataTable toolbar registration" in it.message})
        assertEquals(report.warnings.toString(),2,report.warnings.count {"Reviewed fresh-table toolbar construction" in it.message})
        assertEquals(2,report.matching!!.findings.count {it.code==DiagnosticCode.MATCHED})
        assertEquals(setOf("shell","title"),report.nodes.mapNotNull {it.wicketId}.toSet())
    }
    fun testDirectConstructorLocalsKeepTheSameFreshnessProof() {
        val java=WicketToolbarRegistrationFixture.install(myFixture)
        change("private final DataTable<String,String> table;","")
        change("table=new RowsTable", "DataTable<String,String> table=new RowsTable")
        matched(java)
    }
    fun testAbstractToolbarStorageConstructorIsReviewedWithTheSameOwnedTable() {
        val java=WicketToolbarRegistrationFixture.install(myFixture)
        change("extends org.apache.wicket.extensions.ajax.markup.html.repeater.data.table.AjaxFallbackHeadersToolbar<String>","extends AbstractToolbar")
        change("Header(DataTable<String,String> table,org.apache.wicket.extensions.markup.html.repeater.data.sort.ISortStateLocator<String> locator){super(table,locator);}","Header(DataTable<String,String> table){super(table);}")
        change("new Header(table,null)","new Header(table)")
        matched(java)
    }
    fun testDirectCompiledHeaderAllocationKeepsTheRegistrationContext() {
        val java=WicketToolbarRegistrationFixture.install(myFixture)
        change("new Header(table,null)","new org.apache.wicket.extensions.ajax.markup.html.repeater.data.table.AjaxFallbackHeadersToolbar<String>(table,null)")
        matched(java)
    }
    fun testAnonymousSourceHeaderRenderingHooksStayDeferred() {
        val java=WicketToolbarRegistrationFixture.install(myFixture)
        change("private final class Header","private class Header")
        change("new Header(table,null)","new Header(table,null){protected void onBeforeRender(){super.onBeforeRender();TableShell.this.add(new org.apache.wicket.markup.html.basic.Label(\"later\"));}}")
        matched(java)
    }
    fun testTypedSortableHeaderFactoryIsDeferred() {
        val java=WicketToolbarRegistrationFixture.install(myFixture)
        change("@Override protected void onBeforeRender()","@Override protected org.apache.wicket.markup.html.WebMarkupContainer newSortableHeader(String id,String property,org.apache.wicket.extensions.markup.html.repeater.data.sort.ISortStateLocator<String> locator){TableShell.this.add(new org.apache.wicket.markup.html.basic.Label(\"later\"));return super.newSortableHeader(id,property,locator);}@Override protected void onBeforeRender()")
        matched(java)
    }
    fun testTableAliasesDoNotBorrowTheAllocationProof()=withheld("table.addTopToolbar(new Header(table,null));","DataTable<String,String> alias=table;alias.addTopToolbar(new Header(alias,null));")
    fun testAttachmentBeforeRegistrationWithholdsTheProof()=withheld("table.setOutputMarkupId(true);","table.setOutputMarkupId(true);add(table);")
    fun testSourceConsumerEscapeBeforeRegistrationWithholdsTheProof() {
        val java=WicketToolbarRegistrationFixture.install(myFixture)
        change("public TableShell(String id)","private void consume(DataTable<?,?> table){}public TableShell(String id)")
        change("table.setOutputMarkupId(true);","table.setOutputMarkupId(true);consume(table);")
        assertNull(WicketLayoutSetupAnalyzer(factories=true).analyze(java.classes.single(),"root").expansion)
    }
    fun testOtherConfigurationDoesNotAcquireTheOutputFlagContract()=withheld("table.setOutputMarkupId(true);","table.setOutputMarkupId(true);table.setVisible(false);")
    fun testToolbarAliasesAndReuseRemainUnreviewed()=withheld("table.addTopToolbar(new Header(table,null));","Header header=new Header(table,null);table.addTopToolbar(header);")
    fun testNullToolbarDoesNotBecomeARegisteredComponent()=withheld("table.addTopToolbar(new Header(table,null));","table.addTopToolbar(null);")
    fun testToolbarCannotUseAnotherFreshTable()=withheld("table.addTopToolbar(new Header(table,null));","DataTable<String,String> other=new RowsTable(\"other\",List.of(),new ListDataProvider<String>(List.of()),10);table.addTopToolbar(new Header(other,null));")
    fun testSourceToolbarInstanceStateIsUnreviewed()=withheld("Header(DataTable<String,String> table,","private String value;Header(DataTable<String,String> table,")
    fun testSourceToolbarInitializerBlocksAreUnreviewed()=withheld("Header(DataTable<String,String> table,","{System.setProperty(\"toolbar\",\"effect\");}Header(DataTable<String,String> table,")
    fun testSourceToolbarStaticInitializationIsUnreviewed()=withheld("Header(DataTable<String,String> table,","static {System.setProperty(\"toolbar\",\"effect\");}Header(DataTable<String,String> table,")
    fun testSourceToolbarObjectHooksAreUnreviewed()=withheld("Header(DataTable<String,String> table,","public String toString(){return \"toolbar\";}Header(DataTable<String,String> table,")
    fun testSourceToolbarInitializeHookIsUnreviewed()=withheld("Header(DataTable<String,String> table,","protected void onInitialize(){super.onInitialize();}Header(DataTable<String,String> table,")
    fun testSourceToolbarEagerExtraStatementsAreUnreviewed()=withheld("super(table,locator);","super(table,locator);TableShell.this.removeAll();")
    fun testSourceToolbarComputedDelegationInputsAreUnreviewed()=withheld("super(table,locator);","super(table,locator==null?null:locator);")
    fun testComputedLocatorArgumentsDoNotAcquireAConstructorContract() {
        val java=WicketToolbarRegistrationFixture.install(myFixture)
        change("public TableShell(String id)","private org.apache.wicket.extensions.markup.html.repeater.data.sort.ISortStateLocator<String> query(){removeAll();return null;}public TableShell(String id)")
        change("new Header(table,null)","new Header(table,query())")
        val result=WicketLayoutSetupAnalyzer(factories=true).analyze(java.classes.single(),"root")
        assertNull(result.diagnostics.toString(),result.expansion)
        assertTrue(result.diagnostics.toString(),result.diagnostics.any {"without evaluation" in it.message})
    }
    fun testSourceToolbarThisDelegationIsUnreviewed()=withheld("super(table,locator);","this(table,locator);")
    fun testSourceToolbarSerializationConstantsAreAllowed() {
        val java=WicketToolbarRegistrationFixture.install(myFixture)
        change("Header(DataTable<String,String> table,","private static final long serialVersionUID=1L;Header(DataTable<String,String> table,")
        matched(java)
    }
    fun testSourceToolbarInterfaceInitializationIsAudited() {
        val java=WicketToolbarRegistrationFixture.install(myFixture)
        myFixture.addFileToProject("src/main/java/example/ToolbarMarker.java","package example;interface ToolbarMarker {int TOKEN=seed();static int seed(){System.setProperty(\"toolbar\",\"effect\");return 1;}default String tag(){return \"tag\";}}")
        change("AjaxFallbackHeadersToolbar<String> {","AjaxFallbackHeadersToolbar<String> implements ToolbarMarker {")
        assertNull(WicketLayoutSetupAnalyzer(factories=true).analyze(java.classes.single(),"root").expansion)
    }
    fun testFrameworkNavigationToolbarKeepsTheFreshRegistrationContract() {
        val java=WicketToolbarRegistrationFixture.install(myFixture)
        change("new Header(table,null)","new org.apache.wicket.extensions.markup.html.repeater.data.table.NavigationToolbar(table)")
        matched(java)
        assertEquals(2,WicketPageAnalyzer().analyze(java.classes.single())!!.warnings.count {"Reviewed fresh-table navigation-toolbar construction" in it.message})
    }
    private fun headerDepth(count:Int):WicketDataTableToolbars.Proof {
        WicketToolbarRegistrationFixture.install(myFixture)
        for(i in 0 until count) myFixture.addFileToProject("src/main/java/example/HeaderBase$i.java","package example;public class HeaderBase$i extends "+
            (if(i+1==count) "org.apache.wicket.extensions.ajax.markup.html.repeater.data.table.AjaxFallbackHeadersToolbar<String>" else "HeaderBase${i+1}")+
            " {public HeaderBase$i(org.apache.wicket.extensions.markup.html.repeater.data.table.DataTable<String,String> table,org.apache.wicket.extensions.markup.html.repeater.data.sort.ISortStateLocator<String> locator){super(table,locator);}}")
        change("extends org.apache.wicket.extensions.ajax.markup.html.repeater.data.table.AjaxFallbackHeadersToolbar<String>","extends HeaderBase0")
        val type=JavaPsiFacade.getInstance(project).findClass("example.TableShell",com.intellij.psi.search.GlobalSearchScope.allScope(project))!!
        val call=PsiTreeUtil.findChildrenOfType(type,PsiMethodCallExpression::class.java).first {it.methodExpression.referenceName=="addTopToolbar"}
        return WicketDataTableToolbars({ProgressManager.checkCanceled()},{},{null},{},{},{JavaPsiFacade.getInstance(project).findClass(it,type.resolveScope)},
            {_,message->throw IllegalStateException(message)}).registration(call)
    }
    fun testTwelveSourceHeaderLevelsRetainTheConstructionProof()=assertEquals(12,headerDepth(11).owners.size)
    fun testThirteenSourceHeaderLevelsRemainUnreviewed() {assertThrows(IllegalStateException::class.java) {headerDepth(12)}}
    fun testCancellationDuringHeaderDependencyRecordingPropagates() {
        WicketToolbarRegistrationFixture.install(myFixture)
        val type=JavaPsiFacade.getInstance(project).findClass("example.TableShell",com.intellij.psi.search.GlobalSearchScope.allScope(project))!!
        val call=PsiTreeUtil.findChildrenOfType(type,PsiMethodCallExpression::class.java).first {it.methodExpression.referenceName=="addTopToolbar"}
        val indicator=EmptyProgressIndicator();var reached=false
        val tick:(PsiElement)->Unit={at ->if(at is PsiClass && at.qualifiedName==WicketDataTableToolbars.HEADERS){reached=true;indicator.cancel()};ProgressManager.checkCanceled()}
        assertThrows(ProcessCanceledException::class.java) {ProgressManager.getInstance().runProcess(Runnable {
            WicketDataTableToolbars(tick,tick,{null},{},{},{JavaPsiFacade.getInstance(project).findClass(it,type.resolveScope)},
                {_,message->throw AssertionError(message)}).registration(call)
        },indicator)}
        assertTrue(reached)
    }
}
