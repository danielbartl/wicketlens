package dev.wicketlens

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.progress.*
import com.intellij.psi.*
import com.intellij.psi.util.PsiTreeUtil
import dev.wicketlens.analysis.*
import dev.wicketlens.editor.*
import dev.wicketlens.model.*
import dev.wicketlens.ui.WicketReportFormatter

class WicketTableToolbarTest : WicketProjectPsiTestCase() {
    private lateinit var java: PsiJavaFile
    override fun setUp() { super.setUp(); java=WicketTableToolbarFixture.install(myFixture) }
    private fun change(from: String,to: String,name: String="TableHost") {
        val file=PsiManager.getInstance(project).findFile(myFixture.findFileInTempDir("src/main/java/example/$name.java"))!!
        assertTrue(file.text,from in file.text)
        WriteCommandAction.runWriteCommandAction(project) { PsiDocumentManager.getInstance(project).getDocument(file)!!.setText(file.text.replace(from,to)) }
        PsiDocumentManager.getInstance(project).commitAllDocuments()
    }
    private fun contexts(limits: WicketTableToolbarContexts.Limits=WicketTableToolbarContexts.Limits()): WicketTableToolbarContexts.Result {
        val page=java.classes.single();return WicketTableToolbarContexts(limits).analyze(page,WicketPageAnalyzer().analyze(page)!!)
    }
    private fun withheld() { val result=contexts();assertTrue(result.toString(),result.reports.isEmpty());assertTrue(result.toString(),result.diagnostics.isNotEmpty()) }
    fun testInlineEmptyStateFactoryHasFourToolbarRelativeBindings() {
        val primary=WicketPageAnalyzer().analyze(java.classes.single())!!;val result=contexts()
        assertEquals(result.diagnostics.toString(),1,result.reports.size)
        val report=result.reports.single();val toolbar=report.toolbarTemplate!!
        assertEquals("empty",toolbar.hostComponentPath)
        assertEquals("example.TableHost.EmptyToolbar",toolbar.toolbarClassName)
        assertEquals("example.EmptyView",toolbar.contentClassName)
        assertNull(report.nodes.single { it.nodeId==report.rootNodeId }.wicketId)
        assertEquals(setOf("cell","content","title","message"),report.nodes.mapNotNull { it.wicketId }.toSet())
        assertEquals(4,report.matching!!.findings.size)
        assertTrue(report.matching.findings.toString(),report.matching.findings.all { it.code==DiagnosticCode.MATCHED && it.confidence==Confidence.INFERRED })
        assertTrue(report.nodes.all { it.template && !it.idRenameSafe })
        assertEquals(2,toolbar.callbackLocations.size)
        assertTrue(toolbar.configurationLocations.isNotEmpty())
        assertNull(primary.toolbarTemplate);assertTrue(primary.nodes.none { it.wicketId=="title" })
        val text=WicketReportFormatter().formatWithLinks(report)
        assertTrue(text.text,"toolbar-relative source content template for empty" in text.text)
        assertEquals(4,Regex("MATCHED / INFERRED").findAll(text.text).count())
        assertTrue(text.links.any { it.markupLocation?.file?.endsWith("TableHost\$EmptyToolbar.html")==true })
    }
    fun testFactoryArrowAndCellTitleMessageIdsNavigateWithoutEditingAuthority() {
        val lambda=PsiTreeUtil.findChildrenOfType(java,PsiLambdaExpression::class.java).single()
        val targets=WicketReplacementNavigation.targets(lambda)
        assertEquals(1,targets.size);assertEquals("TableHost\$EmptyToolbar.html",targets.single().containingFile.name)
        assertTrue(targets.single().references.filterIsInstance<PsiPolyVariantReference>().flatMap { it.multiResolve(false).toList() }.any { it.element==lambda })
        assertNull(WicketIdRenameTarget.at(targets.single().containingFile,targets.single().textOffset+1))
        for ((name,id) in listOf("TableHost" to "cell","EmptyView" to "title","EmptyView" to "message")) {
            val file=PsiManager.getInstance(project).findFile(myFixture.findFileInTempDir("src/main/java/example/$name.java"))!!
            val literal=PsiTreeUtil.findChildrenOfType(file,PsiLiteralExpression::class.java).single { it.value==id }
            val uses=literal.references.filterIsInstance<PsiPolyVariantReference>().flatMap { it.multiResolve(false).toList() }
            assertEquals(id,1,uses.size)
            assertTrue(id,uses.single().element!!.references.filterIsInstance<PsiPolyVariantReference>().flatMap { it.multiResolve(false).toList() }.any { it.element==literal })
        }
    }
    fun testASeparateLocalHostHasTheSameTemplate() {
        change("add(new TableHost(\"empty\").setEmptyState(id -> new EmptyView(id,\"No results\",\"Try again\")));","TableHost table=new TableHost(\"empty\"); add(table); table.setEmptyState(id -> new EmptyView(id,\"No results\",\"Try again\"));","EmptyPage")
        assertEquals(1,contexts().reports.size)
    }
    fun testWrongFactoryIdIsNotForwarded() { change("new EmptyView(id,","new EmptyView(\"content\",","EmptyPage");withheld() }
    fun testGuardRejectingAValidResultWithholdsTheTemplate() { change("content==null || !CONTENT.equals(content.getId())","true");withheld() }
    fun testGuardCannotExecuteUnknownCode() { change("!CONTENT.equals(content.getId())","Boolean.getBoolean(\"flag\")");withheld() }
    fun testWrongTableAttachmentUsesNoProvedRoute() { change("table.addBottomToolbar(emptyToolbar);","getPage().add(emptyToolbar);");withheld() }
    fun testCacheMustBeNullGuarded() { change("emptyToolbar==null","emptyToolbar!=null");withheld() }
    fun testCacheCannotBeOverwrittenElsewhere() { change("private int columns()", "private void reset() { emptyToolbar=null; } private int columns()");withheld() }
    fun testToolbarCellCannotBorrowAComponent() { change("cell=new WebMarkupContainer(\"cell\")","cell=(WebMarkupContainer)get(\"borrowed\")");withheld() }
    fun testCacheInitializerCannotContainAdditionalEffects() { change("table.addBottomToolbar(emptyToolbar);","table.addBottomToolbar(emptyToolbar); table.removeAll();");withheld() }
    fun testProducedContentCannotEscape() { change("emptyToolbar.cell.addOrReplace(content);","System.out.println(content); emptyToolbar.cell.addOrReplace(content);");withheld() }
    fun testOwnedTableMustBePrivateFinal() { change("private final DataTable<String,String> table;","private DataTable<String,String> table;");withheld() }
    fun testToolbarMustForwardTheOwnedTable() { change("super(table);","super((DataTable)null);");withheld() }
    fun testTableCannotBeReattachedToTwoParents() { change("add(table);","add(table); add(table);");withheld() }
    fun testKnownTableConstructorMutationsWithholdTheRoute() { change("add(table);","add(table); table.removeAll();");withheld() }
    fun testOverriddenFrameworkToolbarAttachmentIsNotAssumed() { change("RowsTable(String id,", "@Override public void addBottomToolbar(AbstractToolbar toolbar) {} RowsTable(String id,");withheld() }
    fun testForeignCacheCellIsNotAnOwnedPlacement() { change("private int columns()", "private TableHost other; private int columns()");change("emptyToolbar.cell.addOrReplace(content)","other.emptyToolbar.cell.addOrReplace(content)");withheld() }
    fun testAnonymousHostIsNotDeclaredDispatch() { change("new TableHost(\"empty\")", "new TableHost(\"empty\") {}", "EmptyPage");withheld() }
    fun testHostIdsMustBeUniqueAmongSourceAllocations() { change("public EmptyPage() {", "public EmptyPage() { add(new TableHost(\"empty\"));", "EmptyPage");withheld() }
    fun testContentComponentFieldsNeedASeparateContract() { change("public class EmptyView extends org.apache.wicket.markup.html.panel.Panel {", "public class EmptyView extends org.apache.wicket.markup.html.panel.Panel { private final org.apache.wicket.markup.html.basic.Label saved=new org.apache.wicket.markup.html.basic.Label(\"saved\");", "EmptyView");withheld() }
    fun testContentConstructorAliasesAreNotFresh() { change("message.setVisible(messageModel!=null && messageModel.getObject()!=null);", "org.apache.wicket.markup.html.basic.Label alias=message; message.setVisible(messageModel!=null && messageModel.getObject()!=null);", "EmptyView");withheld() }
    fun testAnonymousToolbarInitializersAreNotReviewed() { change("new EmptyToolbar(table)","new EmptyToolbar(table) {} ");assertTrue(contexts().reports.isEmpty()) }
    fun testUnreachableGuardCannotUseTheWrongExpectedId() { change("!CONTENT.equals(content.getId())", "!\"wrong\".equals(content.getId())");withheld() }
    fun testNodeBudgetClearsTheEntireTemplate() {
        val additions=(1..130).joinToString(" ") { "add(new org.apache.wicket.markup.html.basic.Label(\"extra$it\"));" }
        change("add(message);", "add(message); $additions", "EmptyView")
        val result=contexts(WicketTableToolbarContexts.Limits(steps=40000));assertFalse(result.complete);assertTrue(result.reports.isEmpty())
    }
    fun testStepsAndSourceAndContextBudgetsClearTheTemplates() {
        for (limits in listOf(WicketTableToolbarContexts.Limits(steps=1),WicketTableToolbarContexts.Limits(sources=1),WicketTableToolbarContexts.Limits(contexts=0))) {
            val result=contexts(limits);assertFalse(result.complete);assertTrue(result.reports.isEmpty());assertTrue(result.diagnostics.any { it.code==DiagnosticCode.PARTIAL_ANALYSIS })
        }
    }
    fun testCancellationPropagates() {
        val page=java.classes.single();val report=WicketPageAnalyzer().analyze(page)!!;val indicator=EmptyProgressIndicator()
        assertThrows(ProcessCanceledException::class.java) { ProgressManager.getInstance().runProcess(Runnable { indicator.cancel();WicketTableToolbarContexts().analyze(page,report) },indicator) }
    }
}
