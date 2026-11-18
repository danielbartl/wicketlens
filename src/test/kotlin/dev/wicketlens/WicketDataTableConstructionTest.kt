package dev.wicketlens

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.progress.*
import com.intellij.psi.*
import dev.wicketlens.analysis.*
import dev.wicketlens.model.DiagnosticCode

class WicketDataTableConstructionTest:WicketProjectPsiTestCase() {
    private fun change(before:String,after:String) {
        val file=PsiManager.getInstance(project).findFile(myFixture.findFileInTempDir("src/main/java/example/TableShell.java"))!!
        assertTrue(before in file.text)
        WriteCommandAction.runWriteCommandAction(project) {PsiDocumentManager.getInstance(project).getDocument(file)!!.setText(file.text.replace(before,after))}
        PsiDocumentManager.getInstance(project).commitAllDocuments()
    }
    private fun withheld(member:String) {
        val java=WicketDataTableFixture.install(myFixture)
        change("RowsTable(String id",member+"RowsTable(String id")
        val result=WicketLayoutSetupAnalyzer(factories=true).analyze(java.classes.single(),"root")
        assertNull(result.diagnostics.toString(),result.expansion)
        assertTrue(result.diagnostics.toString(),result.diagnostics.any {it.message.startsWith("Source layout setup stopped:")})
    }
    private fun matched(java:PsiJavaFile) {
        val result=WicketLayoutSetupAnalyzer(factories=true).analyze(java.classes.single(),"root")
        assertNotNull(result.diagnostics.toString(),result.expansion)
        assertTrue(result.expansion!!.warnings.toString(),result.expansion.warnings.any {"Reviewed DataTable constructor" in it.message})
        val report=WicketPageAnalyzer().analyze(java.classes.single())!!
        assertEquals(report.warnings.toString(),2,report.matching!!.findings.count {it.code==DiagnosticCode.MATCHED})
        assertEquals(setOf("shell","title"),report.nodes.mapNotNull {it.wicketId}.toSet())
    }
    fun testRowsTableConstructionRetainsOpaqueContentsAndDeferredRows() {
        val java=WicketDataTableFixture.install(myFixture)
        val report=WicketPageAnalyzer().analyze(java.classes.single())!!
        assertTrue(report.warnings.toString(),report.warnings.any {"Reviewed DataTable constructor" in it.message})
        assertEquals(report.warnings.toString(),2,report.matching!!.findings.count {it.code==DiagnosticCode.MATCHED})
        assertEquals(setOf("shell","title"),report.nodes.mapNotNull {it.wicketId}.toSet())
        assertFalse(report.nodes.single {it.wicketId=="shell"}.sourceSetupExpanded)
    }
    fun testCaptionFactoryOverrideRemainsUnreviewed()=withheld("@Override protected IModel<String> getCaptionModel(){return null;}")
    fun testBodyFactoryOverrideRemainsUnreviewed()=withheld("@Override protected org.apache.wicket.markup.html.WebMarkupContainer newBodyContainer(String id){return super.newBodyContainer(id);}")
    fun testGridFactoryOverrideRemainsUnreviewed()=withheld("@Override protected org.apache.wicket.extensions.markup.html.repeater.data.grid.DataGridView<String> newDataGridView(String id,List<? extends IColumn<String,String>> columns,IDataProvider<String> provider){return super.newDataGridView(id,columns,provider);}")
    fun testInheritedAddOverrideCannotBeMistakenForFrameworkStorage()=withheld("@Override public org.apache.wicket.MarkupContainer add(org.apache.wicket.Component... children){return super.add(children);}")
    fun testObjectHooksRemainUnreviewed()=withheld("@Override public String toString(){return \"table\";}")
    fun testExtraPrivateMethodsDoNotAcquireAConstructorContract()=withheld("private String query(){return \"later\";}")
    fun testInstanceStateRemainsOutsideTheWrapperContract()=withheld("private String value;")
    fun testInstanceInitializersRemainUnreviewed()=withheld("{System.setProperty(\"table\",\"effect\");}")
    fun testStaticInitializersRemainUnreviewed()=withheld("static {System.setProperty(\"table\",\"effect\");}")
    fun testUnknownStaticStorageRemainsUnreviewed()=withheld("private static final String VALUE=System.getProperty(\"table\");")
    fun testSerializationConstantsArePermitted() {
        val java=WicketDataTableFixture.install(myFixture);change("RowsTable(String id","private static final long serialVersionUID=1L;RowsTable(String id");matched(java)
    }
    fun testDirectFrameworkTableKeepsTheSameOwnedBoundary() {
        val java=WicketDataTableFixture.install(myFixture)
        change("new RowsTable(\"table\"","new DataTable<String,String>(\"table\"")
        matched(java)
    }
    fun testAnonymousDeferredRowsKeepTheSameGuard() {
        val java=WicketDataTableFixture.install(myFixture)
        change("private final class RowsTable","private class RowsTable")
        change("List.of(\"row\")),10));","List.of(\"row\")),10){protected Item<String> newRowItem(String id,int index,IModel<String> model){return super.newRowItem(id,index,model);}});")
        val shell=PsiManager.getInstance(project).findFile(myFixture.findFileInTempDir("src/main/java/example/TableShell.java"))!!
        review(com.intellij.psi.util.PsiTreeUtil.findChildrenOfType(shell,PsiAnonymousClass::class.java).single())
        assertEquals(2,WicketPageAnalyzer().analyze(java.classes.single())!!.matching!!.findings.count {it.code==DiagnosticCode.MATCHED})
    }
    fun testDeferredRowsDoNotAddInitialPageChildren() {
        val java=WicketDataTableFixture.install(myFixture)
        change("return super.newRowItem(id,index,model);","TableShell.this.add(new org.apache.wicket.markup.html.basic.Label(\"later\"));return super.newRowItem(id,index,model);")
        matched(java)
    }
    fun testAnonymousCaptionOverrideIsNotHiddenByItsBaseConstructor() {
        val java=WicketDataTableFixture.install(myFixture)
        change("private final class RowsTable","private class RowsTable")
        change("List.of(\"row\")),10));","List.of(\"row\")),10){protected IModel<String> getCaptionModel(){return null;}});")
        val shell=PsiManager.getInstance(project).findFile(myFixture.findFileInTempDir("src/main/java/example/TableShell.java"))!!
        assertThrows(IllegalStateException::class.java) {review(com.intellij.psi.util.PsiTreeUtil.findChildrenOfType(shell,PsiAnonymousClass::class.java).single())}
        assertNull(WicketLayoutSetupAnalyzer(factories=true).analyze(java.classes.single(),"root").expansion)
    }
    fun testSourceInterfaceInitializationIsAudited() {
        val java=WicketDataTableFixture.install(myFixture)
        myFixture.addFileToProject("src/main/java/example/TableMarker.java","package example;interface TableMarker {int TOKEN=seed();static int seed(){System.setProperty(\"table\",\"effect\");return 1;}default String tag(){return \"tag\";}}")
        change("extends DataTable<String,String>","extends DataTable<String,String> implements TableMarker")
        assertNull(WicketLayoutSetupAnalyzer(factories=true).analyze(java.classes.single(),"root").expansion)
    }
    fun testProviderCallsInSourceConstructorStillWithholdExpansion() {
        val java=WicketDataTableFixture.install(myFixture)
        change("super(id,columns,provider,count);","super(id,columns,provider,count);provider.size();")
        val result=WicketLayoutSetupAnalyzer(factories=true).analyze(java.classes.single(),"root")
        assertNull(result.diagnostics.toString(),result.expansion)
        assertTrue(result.diagnostics.toString(),result.diagnostics.any {"only forwards four inputs" in it.message})
    }
    fun testUnknownTableMethodsDoNotGetTheConstructorContract() {
        val java=WicketDataTableFixture.install(myFixture)
        change("super(id,columns,provider,count);","super(id,columns,provider,count);getRowCount();")
        assertNull(WicketLayoutSetupAnalyzer(factories=true).analyze(java.classes.single(),"root").expansion)
    }
    fun testSourceBaseOverridesAreGuarded() {
        val java=WicketDataTableFixture.install(myFixture)
        myFixture.addFileToProject("src/main/java/example/TableBase.java","package example;public class TableBase extends org.apache.wicket.extensions.markup.html.repeater.data.table.DataTable<String,String> {public TableBase(String id,java.util.List<? extends org.apache.wicket.extensions.markup.html.repeater.data.table.IColumn<String,String>> columns,org.apache.wicket.markup.repeater.data.IDataProvider<String> provider,long count){super(id,columns,provider,count);}protected org.apache.wicket.model.IModel<String> getCaptionModel(){return null;}}")
        change("extends DataTable<String,String>","extends TableBase")
        assertNull(WicketLayoutSetupAnalyzer(factories=true).analyze(java.classes.single(),"root").expansion)
    }
    private fun ancestry(count:Int):PsiClass {
        WicketDataTableFixture.install(myFixture)
        for(i in 0 until count) myFixture.addFileToProject("src/main/java/example/TableBase$i.java","package example;public class TableBase$i extends "+(if(i+1==count) "org.apache.wicket.extensions.markup.html.repeater.data.table.DataTable<String,String>" else "TableBase${i+1}")+" {public TableBase$i(String id,java.util.List<? extends org.apache.wicket.extensions.markup.html.repeater.data.table.IColumn<String,String>> columns,org.apache.wicket.markup.repeater.data.IDataProvider<String> provider,long count){super(id,columns,provider,count);}}")
        change("extends DataTable<String,String>","extends TableBase0")
        return JavaPsiFacade.getInstance(project).findClass("example.TableShell",com.intellij.psi.search.GlobalSearchScope.allScope(project))!!.innerClasses.single()
    }
    fun testTwelveSourceLevelsRetainTheBoundedConstructionProof() {
        val type=ancestry(11);assertTrue(WicketDataTableConstruction.candidate(type))
        var recorded=false
        WicketDataTableConstruction({},{if(it is PsiClass && it.qualifiedName==WicketDataTableConstruction.TABLE)recorded=true},{null},{},
            {JavaPsiFacade.getInstance(project).findClass(it,type.resolveScope)},{_,message->throw AssertionError(message)}).inspect(type)
        assertTrue(recorded)
    }
    fun testSourceWrapperBaseKeepsTheSameEndToEndProof() {
        ancestry(1)
        matched(PsiManager.getInstance(project).findFile(myFixture.findFileInTempDir("src/main/java/example/TablePage.java")) as PsiJavaFile)
    }
    fun testThirteenSourceLevelsDoNotGetTheConstructorContract()=assertFalse(WicketDataTableConstruction.candidate(ancestry(12)))
    private fun review(type:PsiClass) {
        WicketDataTableConstruction({},{},{null},{},{JavaPsiFacade.getInstance(project).findClass(it,type.resolveScope)},
            {_,message->throw IllegalStateException(message)}).inspect(type)
    }
    fun testRecursiveThisDelegationRemainsUnreviewed() {
        val java=WicketDataTableFixture.install(myFixture)
        change("super(id,columns,provider,count);","this(id,columns,provider,count);")
        assertNull(WicketLayoutSetupAnalyzer(factories=true).analyze(java.classes.single(),"root").expansion)
    }
    fun testConstructorArgumentQueriesDoNotBecomeForwardedData() {
        val java=WicketDataTableFixture.install(myFixture)
        change("super(id,columns,provider,count);","super(id,columns,provider,provider.size());")
        val result=WicketLayoutSetupAnalyzer(factories=true).analyze(java.classes.single(),"root")
        assertNull(result.diagnostics.toString(),result.expansion)
        assertTrue(result.diagnostics.toString(),result.diagnostics.any {"without evaluating additional expressions" in it.message})
    }
    fun testConstructorCannotHideMutationOfTheEnclosingPageAfterSuper() {
        val java=WicketDataTableFixture.install(myFixture)
        val header=WicketDataTableFixture.shell.substringBefore("public class TableShell")
        val nested=WicketDataTableFixture.shell.substringAfter("public class TableShell")
            .replace("super(id,columns,provider,count);","super(id,columns,provider,count);TablePage.this.removeAll();")
        val page=header+"public class TablePage extends org.apache.wicket.markup.html.WebPage {private class TableShell"+nested+
            "public TablePage(){add(new org.apache.wicket.markup.html.basic.Label(\"title\",\"Ready\"));add(new TableShell(\"shell\"));}}"
        WriteCommandAction.runWriteCommandAction(project) {PsiDocumentManager.getInstance(project).getDocument(java)!!.setText(page)}
        PsiDocumentManager.getInstance(project).commitAllDocuments()
        myFixture.addFileToProject("src/main/resources/example/TablePage${'$'}TableShell.html","<wicket:panel><table wicket:id='table'></table></wicket:panel>")
        val result=WicketLayoutSetupAnalyzer(factories=true).analyze(java.classes.single(),"root")
        assertNull(result.diagnostics.toString(),result.expansion)
        assertTrue(result.diagnostics.toString(),result.diagnostics.any {"only forwards four inputs" in it.message})
    }
    fun testCancellationDuringCompiledGridDependencyRecordingPropagates() {
        WicketDataTableFixture.install(myFixture)
        val type=JavaPsiFacade.getInstance(project).findClass("example.TableShell",com.intellij.psi.search.GlobalSearchScope.allScope(project))!!.innerClasses.single()
        val indicator=EmptyProgressIndicator();var reached=false
        val tick:(PsiElement)->Unit={at ->if(at is PsiClass && at.qualifiedName=="org.apache.wicket.extensions.markup.html.repeater.data.grid.DataGridView"){reached=true;indicator.cancel()};ProgressManager.checkCanceled()}
        assertThrows(ProcessCanceledException::class.java) {ProgressManager.getInstance().runProcess(Runnable {
            WicketDataTableConstruction(tick,tick,{null},{},{JavaPsiFacade.getInstance(project).findClass(it,type.resolveScope)},{_,message->throw AssertionError(message)}).inspect(type)
        },indicator)}
        assertTrue(reached)
    }
}
