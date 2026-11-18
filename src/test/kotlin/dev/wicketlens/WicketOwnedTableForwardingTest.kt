package dev.wicketlens

import com.intellij.psi.*
import com.intellij.openapi.command.WriteCommandAction
import dev.wicketlens.analysis.*
import dev.wicketlens.model.DiagnosticCode

class WicketOwnedTableForwardingTest:WicketProjectPsiTestCase() {
    private fun change(before:String,after:String) {
        val file=PsiManager.getInstance(project).findFile(myFixture.findFileInTempDir("src/main/java/example/TableShell.java"))!!
        assertTrue(before in file.text)
        WriteCommandAction.runWriteCommandAction(project) {PsiDocumentManager.getInstance(project).getDocument(file)!!.setText(file.text.replace(before,after))}
        PsiDocumentManager.getInstance(project).commitAllDocuments()
    }
    fun testOwnedReferenceForwardingDoesNotExposePrivateToolbarOrRuntimeState() {
        val java=WicketOwnedTableForwardingFixture.install(myFixture)
        val report=WicketPageAnalyzer().analyze(java.classes.single())!!
        assertEquals(report.warnings.toString(),1,report.warnings.count {"Reviewed owned DataTable reference forwarding" in it.message})
        assertEquals(report.warnings.toString(),2,report.matching!!.findings.count {it.code==DiagnosticCode.MATCHED})
        assertEquals(setOf("shell","title"),report.nodes.mapNotNull {it.wicketId}.toSet())
    }
    fun testCompletedForwardingFactSurvivesUnreviewedLaterConstructorEffects() {
        val java=WicketOwnedTableForwardingFixture.install(myFixture)
        change("super(table);cell=","super(table);getPage().removeAll();cell=")
        val result=WicketLayoutSetupAnalyzer(factories=true).analyze(java.classes.single(),"root")
        assertNull(result.expansion)
        assertEquals(result.diagnostics.toString(),1,result.diagnostics.count {"Reviewed owned DataTable reference forwarding" in it.message})
    }

    private fun withheld(before:String,after:String) {
        val java=WicketOwnedTableForwardingFixture.install(myFixture);change(before,after)
        val result=WicketLayoutSetupAnalyzer(factories=true).analyze(java.classes.single(),"root")
        assertNull(result.diagnostics.toString(),result.expansion)
        assertFalse(result.diagnostics.toString(),result.diagnostics.any {"Reviewed owned DataTable reference forwarding" in it.message})
    }
    fun testMutableTableStorageDoesNotAcquireForwarding()=withheld("private final DataTable<String,String> table;","private DataTable<String,String> table;")
    fun testForeignReceiverDoesNotAcquireForwarding()=withheld("new EmptyToolbar(table);","TableShell other=this;new EmptyToolbar(other.table);")
    fun testBorrowedLocalTableDoesNotAcquireForwarding()=withheld("new EmptyToolbar(table);","DataTable<String,String> other=table;new EmptyToolbar(other);")
    fun testTableReassignmentIsUnreviewed()=withheld("add(table);}","add(table);table=new RowsTable(\"other\",List.of(),new ListDataProvider<String>(List.of()),10);}")
    fun testExtraOwnerConstructorDoesNotBorrowOneAllocationProof()=withheld("public TableShell(String id)","public TableShell(){this(\"other\");}public TableShell(String id)")
    fun testMissingOwnTableAttachmentIsUnreviewed()=withheld("add(table);}","}")
    fun testDuplicateOwnTableAttachmentsAreUnreviewed()=withheld("add(table);}","add(table);add(table);}")
    fun testPriorTableConsumerIsUnreviewed()=withheld("add(table);}","add(table);consume(table);}private void consume(DataTable<String,String> value){}")
    fun testOwnerInitializerEffectsAreUnreviewed()=withheld("private final DataTable<String,String> table;","{getPage().removeAll();}private final DataTable<String,String> table;")
    fun testSourceTableMethodsCannotReplaceCompiledIdAllocation()=withheld("RowsTable(String id","public String newToolbarId(){return \"mine\";}RowsTable(String id")
    fun testUnreviewedToolbarParentDoesNotAcquireForwarding()=withheld("extends AbstractToolbar","extends org.apache.wicket.extensions.markup.html.repeater.data.table.NoRecordsToolbar")
    fun testNonFinalToolbarDoesNotAcquireForwarding()=withheld("private final class EmptyToolbar","private class EmptyToolbar")
    fun testStaticToolbarDoesNotAcquireOwnerForwarding()=withheld("private final class EmptyToolbar","private static final class EmptyToolbar")
    fun testToolbarInitializerBlocksAreUnreviewed()=withheld("EmptyToolbar(DataTable<String,String> table)","{getPage().removeAll();}EmptyToolbar(DataTable<String,String> table)")
    fun testToolbarInitializationOverridesAreUnreviewed()=withheld("EmptyToolbar(DataTable<String,String> table)","protected void onInitialize(){super.onInitialize();getPage().removeAll();}EmptyToolbar(DataTable<String,String> table)")
    fun testUnknownToolbarStaticInitializationIsUnreviewed()=withheld("EmptyToolbar(DataTable<String,String> table)","private static final String VALUE=System.getProperty(\"unknown\");EmptyToolbar(DataTable<String,String> table)")
    fun testMutableToolbarContainerStorageIsUnreviewed()=withheld("private final org.apache.wicket.markup.html.WebMarkupContainer cell;","private org.apache.wicket.markup.html.WebMarkupContainer cell;")
    fun testUnusedToolbarModelFieldsDoNotAcquireAContract()=withheld("EmptyToolbar(DataTable<String,String> table)","private final org.apache.wicket.model.IModel<String> model=org.apache.wicket.model.Model.of(\"value\");EmptyToolbar(DataTable<String,String> table)")
    fun testChangedForwardingArgumentIsUnreviewed()=withheld("super(table);cell=","super(null);cell=")
    fun testParameterAliasCannotBorrowTheCompiledBoundary()=withheld("super(table);cell=","super(table);DataTable<String,String> alias=table;cell=")
    fun testParameterMutationIsNotJustConstructorForwarding()=withheld("super(table);cell=","super(table);table.removeAll();cell=")
    fun testToolbarInterfaceInitializationIsAudited() {
        WicketOwnedTableForwardingFixture.install(myFixture)
        myFixture.addFileToProject("src/main/java/example/ToolbarMarker.java","package example;interface ToolbarMarker {int VALUE=seed();static int seed(){System.setProperty(\"interface\",\"effect\");return 1;}default String marker(){return \"marker\";}}")
        change("extends AbstractToolbar","extends AbstractToolbar implements ToolbarMarker")
        val java=PsiManager.getInstance(project).findFile(myFixture.findFileInTempDir("src/main/java/example/TablePage.java")) as PsiJavaFile
        val result=WicketLayoutSetupAnalyzer(factories=true).analyze(java.classes.single(),"root")
        assertNull(result.expansion);assertFalse(result.diagnostics.any {"Reviewed owned DataTable reference forwarding" in it.message})
    }
    fun testAlreadyAttachedOwnersDoNotAcquireConfigurationForwarding() {
        val java=WicketOwnedTableForwardingFixture.install(myFixture)
        WriteCommandAction.runWriteCommandAction(project) {PsiDocumentManager.getInstance(project).getDocument(java)!!.setText(java.text.replace("add(new TableShell(\"shell\").configure());","TableShell shell=new TableShell(\"shell\");add(shell);shell.configure();"))}
        PsiDocumentManager.getInstance(project).commitAllDocuments()
        assertNull(WicketLayoutSetupAnalyzer(factories=true).analyze(java.classes.single(),"root").expansion)
    }
    fun testCacheAssignmentKeepsItsIndependentGuardAfterForwarding() {
        val java=WicketOwnedTableForwardingFixture.install(myFixture)
        change("public TableShell configure(){new EmptyToolbar(table);","private EmptyToolbar cache;public TableShell configure(){cache=new EmptyToolbar(table);")
        val result=WicketLayoutSetupAnalyzer(factories=true).analyze(java.classes.single(),"root")
        assertNull(result.expansion)
        assertEquals(1,result.diagnostics.count {"Reviewed owned DataTable reference forwarding" in it.message})
        assertTrue(result.diagnostics.toString(),result.diagnostics.any {"toolbar cache requires a direct null-guarded source block" in it.message})
    }
    fun testDeferredColumnElementsRemainOutsideTheSizeOnlyQueryContract() {
        val java=WicketOwnedTableForwardingFixture.install(myFixture)
        change("add(cell);}","cell.add(org.apache.wicket.AttributeModifier.replace(\"colspan\",(org.apache.wicket.model.IModel<Integer>)TableShell.this::columns));add(cell);}")
        change("public TableShell configure()","private int columns(){return table.getColumns().get(0).hashCode();}public TableShell configure()")
        val result=WicketLayoutSetupAnalyzer(factories=true).analyze(java.classes.single(),"root")
        assertNull(result.expansion)
        assertEquals(1,result.diagnostics.count {"Reviewed owned DataTable reference forwarding" in it.message})
        assertTrue(result.diagnostics.toString(),result.diagnostics.any {"columns list operation" in it.message})
    }
    fun testOversizedConstructorSnapshotWithholdsTheForwardingProof() {
        val java=WicketOwnedTableForwardingFixture.install(myFixture)
        change("public TableShell(String id){super(id);","public TableShell(String id){super(id);int count=0;"+"count++;".repeat(6000))
        val result=WicketLayoutSetupAnalyzer(factories=true).analyze(java.classes.single(),"root")
        assertNull(result.expansion)
        assertTrue(result.diagnostics.toString(),result.diagnostics.any {"budget was exceeded" in it.message})
        assertFalse(result.diagnostics.any {"Reviewed owned DataTable reference forwarding" in it.message})
    }
    fun testCancellationDuringOwnedTableReferenceReviewPropagates() {
        WicketOwnedTableForwardingFixture.install(myFixture)
        val shell=PsiManager.getInstance(project).findFile(myFixture.findFileInTempDir("src/main/java/example/TableShell.java")) as PsiJavaFile
        val allocation=com.intellij.psi.util.PsiTreeUtil.findChildrenOfType(shell,PsiNewExpression::class.java).single {it.classReference?.referenceName=="EmptyToolbar"}
        val count={at:PsiElement -> if(at is PsiReferenceExpression) throw com.intellij.openapi.progress.ProcessCanceledException() else Unit}
        val toolbar=WicketDataTableToolbars(count,count,{(it as? PsiLiteralExpression)?.value},{},{},{null},{_,msg->throw IllegalStateException(msg)})
        val proof=WicketOwnedTableForwarding(count,count,{(it as? PsiLiteralExpression)?.value},{},{},toolbar::reviewedReference,toolbar::constructorReferences,toolbar::constructorAssignments,
            {name->JavaPsiFacade.getInstance(project).findClass(name,shell.resolveScope)},{_,msg->throw IllegalStateException(msg)})
        assertThrows(com.intellij.openapi.progress.ProcessCanceledException::class.java) {proof.inspect(allocation,shell.classes.single())}
    }

    fun testOwnedTableFieldInitializerUsesTheSameBoundary() {
        val java=WicketOwnedTableForwardingFixture.install(myFixture)
        change("private final DataTable<String,String> table;","private final DataTable<String,String> table=new RowsTable(\"table\",List.of(),new ListDataProvider<String>(List.of(\"row\")),10);")
        change("super(id);table=new RowsTable(\"table\",List.of(),new ListDataProvider<String>(List.of(\"row\")),10);add(table);","super(id);add(table);")
        val report=WicketPageAnalyzer().analyze(java.classes.single())!!
        assertEquals(report.warnings.toString(),1,report.warnings.count {"Reviewed owned DataTable reference forwarding" in it.message})
        assertEquals(2,report.matching!!.findings.count {it.code==DiagnosticCode.MATCHED})
    }
    fun testOwnedTableCannotEscapeIntoAnotherFieldInitializer() {
        val java=WicketOwnedTableForwardingFixture.install(myFixture)
        change("private final DataTable<String,String> table;","private final DataTable<String,String> table=new RowsTable(\"table\",List.of(),new ListDataProvider<String>(List.of(\"row\")),10);private final Object alias=table;")
        change("super(id);table=new RowsTable(\"table\",List.of(),new ListDataProvider<String>(List.of(\"row\")),10);add(table);","super(id);add(table);")
        val result=WicketLayoutSetupAnalyzer(factories=true).analyze(java.classes.single(),"root")
        assertNull(result.expansion);assertFalse(result.diagnostics.any {"Reviewed owned DataTable reference forwarding" in it.message})
    }
}
