package dev.wicketlens

import com.intellij.psi.*
import com.intellij.openapi.command.WriteCommandAction
import dev.wicketlens.analysis.*
import dev.wicketlens.model.DiagnosticCode

class WicketTableColumnsReadTest:WicketProjectPsiTestCase() {
    private fun change(before:String,after:String) {
        val file=PsiManager.getInstance(project).findFile(myFixture.findFileInTempDir("src/main/java/example/TableShell.java"))!!
        assertTrue(before in file.text)
        WriteCommandAction.runWriteCommandAction(project) {PsiDocumentManager.getInstance(project).getDocument(file)!!.setText(file.text.replace(before,after))}
        PsiDocumentManager.getInstance(project).commitAllDocuments()
    }
    fun testDeferredImmutableColumnsSizeKeepsValuesAndPrivateContentsUnknown() {
        val java=WicketTableColumnsReadFixture.install(myFixture)
        val report=WicketPageAnalyzer().analyze(java.classes.single())!!
        assertEquals(report.warnings.toString(),1,report.warnings.count {"Reviewed owned DataTable columns getter" in it.message})
        assertEquals(report.warnings.toString(),2,report.matching!!.findings.count {it.code==DiagnosticCode.MATCHED})
        assertEquals(setOf("shell","title"),report.nodes.mapNotNull {it.wicketId}.toSet())
    }

    private fun withheld(before:String,after:String,completed:Boolean=true) {
        val java=WicketTableColumnsReadFixture.install(myFixture);change(before,after)
        val result=WicketLayoutSetupAnalyzer(factories=true).analyze(java.classes.single(),"root")
        assertNull(result.diagnostics.toString(),result.expansion)
        assertEquals(result.diagnostics.toString(),completed,result.diagnostics.any {"Reviewed owned DataTable columns getter" in it.message})
    }
    private fun matched(java:PsiJavaFile) {
        val report=WicketPageAnalyzer().analyze(java.classes.single())!!
        assertEquals(report.warnings.toString(),1,report.warnings.count {"Reviewed owned DataTable columns getter" in it.message})
        assertEquals(report.warnings.toString(),2,report.matching!!.findings.count {it.code==DiagnosticCode.MATCHED})
    }
    fun testDirectSizeReadNeedsNoLocal() {
        val java=WicketTableColumnsReadFixture.install(myFixture)
        change("java.util.List<?> columns=table.getColumns();return columns.size();","return table.getColumns().size();");matched(java)
    }
    fun testLocalAliasPreservesSizeOnlyCapability() {
        val java=WicketTableColumnsReadFixture.install(myFixture)
        change("return columns.size();","java.util.List<?> alias=columns;return alias.size();");matched(java)
    }
    fun testIdenticalConditionalReferencesPreserveCapability() {
        val java=WicketTableColumnsReadFixture.install(myFixture)
        change("return columns.size();","return (true?columns:columns).size();");matched(java)
    }
    fun testClearIsNotAReadOnlySizeOperation()=withheld("return columns.size();","columns.clear();return 0;")
    fun testElementQueriesRemainUnreviewed()=withheld("return columns.size();","return columns.get(0).hashCode();")
    fun testIteratorQueriesRemainUnreviewed()=withheld("return columns.size();","return columns.iterator().hasNext()?1:0;")
    fun testStreamQueriesRemainUnreviewed()=withheld("return columns.size();","return (int)columns.stream().count();")
    fun testEqualityCanInvokeElementCodeAndRemainsUnreviewed()=withheld("return columns.size();","return columns.equals(java.util.List.of())?1:0;")
    fun testToStringCanInvokeElementCodeAndRemainsUnreviewed()=withheld("return columns.size();","return columns.toString().length();")
    fun testHashCodeCanInvokeElementCodeAndRemainsUnreviewed()=withheld("return columns.size();","return columns.hashCode();")
    fun testCollectionCastDoesNotBroadenTheListMethodContract()=withheld("return columns.size();","return ((java.util.Collection<?>)columns).size();")
    fun testObjectAliasDoesNotEraseRestrictedIdentity()=withheld("return columns.size();","Object alias=columns;return alias.hashCode();")
    fun testOpaqueCompiledConsumerCannotReceiveTheReference()=withheld("return columns.size();","System.out.println(columns);return 0;")
    fun testOpaqueSourceConsumerCannotReceiveTheReference()=withheld("return columns.size();","consume(columns);return 0;}private void consume(Object value){")
    fun testReturningTheColumnsReferenceIsNotAValueQuery() {
        val java=WicketTableColumnsReadFixture.install(myFixture)
        change("(org.apache.wicket.model.IModel<Integer>)","(org.apache.wicket.model.IModel<Object>)")
        change("private int columns(){java.util.List<?> columns=table.getColumns();return columns.size();}","private Object columns(){return table.getColumns();}")
        val result=WicketLayoutSetupAnalyzer(factories=true).analyze(java.classes.single(),"root")
        assertNull(result.expansion);assertTrue(result.diagnostics.any {"Reviewed owned DataTable columns getter" in it.message})
    }
    fun testLocalReassignmentCannotEraseTheCapability()=withheld("return columns.size();","columns=null;return 0;")
    fun testUncertainConditionalCannotEraseTheCapability()=withheld("return columns.size();","java.util.List<?> alias=System.currentTimeMillis()>0?columns:java.util.List.of();alias.clear();return 0;")
    fun testArrayLiteralCannotEraseTheCapability()=withheld("return columns.size();","java.util.List<?>[] aliases={columns};aliases[0].clear();return 0;")
    fun testExplicitArrayCannotEraseTheCapability()=withheld("return columns.size();","Object[] aliases=new Object[]{columns};return aliases.length;")
    fun testSwitchYieldCannotEraseTheCapability()=withheld("return columns.size();","java.util.List<?> alias=switch(1){case 1 -> columns;default -> java.util.List.of();};alias.clear();return 0;")
    fun testMonitorUseIsNotAReadOnlySizeOperation()=withheld("return columns.size();","synchronized(columns){return 0;}")
    fun testModelConstructionCannotCaptureTheReference()=withheld("return columns.size();","new org.apache.wicket.model.Model<java.io.Serializable>((java.io.Serializable)columns);return 0;")
    fun testFieldStorageCannotCaptureTheReference()=withheld("private int columns(){java.util.List<?> columns=table.getColumns();return columns.size();}","private Object leak;private int columns(){leak=table.getColumns();return 0;}")
    fun testEagerCallDoesNotBorrowTheDeferredQueryContract()=withheld("(org.apache.wicket.model.IModel<Integer>)TableShell.this::columns","org.apache.wicket.model.Model.of(TableShell.this.columns())",false)
    fun testLocalTableAliasDoesNotBorrowTheOwnedReceiverProof()=withheld("java.util.List<?> columns=table.getColumns();","DataTable<String,String> alias=table;java.util.List<?> columns=alias.getColumns();",false)
    fun testMutableJdkListDoesNotAcquireImmutableListIdentity()=withheld("new RowsTable(\"table\",List.of(),","new RowsTable(\"table\",new java.util.ArrayList<>(),",false)
    fun testOtherImmutableFactoriesRemainUnreviewed()=withheld("new RowsTable(\"table\",List.of(),","new RowsTable(\"table\",java.util.Collections.emptyList(),",false)
    fun testSourceListFactoryDoesNotAcquireAnExactJdkIdentity() {
        val java=WicketTableColumnsReadFixture.install(myFixture)
        change("new RowsTable(\"table\",List.of(),","new RowsTable(\"table\",columnsFactory(),")
        change("public TableShell configure()","private java.util.List<IColumn<String,String>> columnsFactory(){return java.util.List.of();}public TableShell configure()")
        val result=WicketLayoutSetupAnalyzer(factories=true).analyze(java.classes.single(),"root")
        assertNull(result.expansion);assertFalse(result.diagnostics.any {"Reviewed owned DataTable columns getter" in it.message})
    }
    private fun forwarded():PsiJavaFile {
        val java=WicketTableColumnsReadFixture.install(myFixture)
        change("public TableShell(String id)","public TableShell(String id,java.util.List<? extends IColumn<String,String>> input)")
        change("new RowsTable(\"table\",List.of(),","new RowsTable(\"table\",input,")
        WriteCommandAction.runWriteCommandAction(project) {PsiDocumentManager.getInstance(project).getDocument(java)!!.setText(java.text.replace("new TableShell(\"shell\")","new TableShell(\"shell\",java.util.List.of())"))}
        PsiDocumentManager.getInstance(project).commitAllDocuments();return java
    }
    fun testSelectedOwnerParameterPreservesTheListFactoryProvenance()=matched(forwarded())
    fun testOwnerParameterReassignmentWithholdsTheProvenance() {
        val java=forwarded();change("super(id);table=","super(id);input=new java.util.ArrayList<>();table=")
        val result=WicketLayoutSetupAnalyzer(factories=true).analyze(java.classes.single(),"root")
        assertNull(result.expansion);assertFalse(result.diagnostics.any {"Reviewed owned DataTable columns getter" in it.message})
    }
    fun testSourceMethodsDoNotAcquireJdkSizeAuthority() {
        val file=myFixture.addFileToProject("src/main/java/example/FakeList.java","package example;class FakeList {public int size(){return 1;}}") as PsiJavaFile
        assertFalse(WicketTableColumnsRead({},{},{emptyList()},{_,message->throw IllegalStateException(message)}).size(file.classes.single().methods.single()))
    }
    fun testCacheAssignmentRetainsItsGuardAfterCompletedColumnQuery() {
        val java=WicketTableColumnsReadFixture.install(myFixture)
        change("public TableShell configure(){new EmptyToolbar(table);","private EmptyToolbar cache;public TableShell configure(){cache=new EmptyToolbar(table);")
        val result=WicketLayoutSetupAnalyzer(factories=true).analyze(java.classes.single(),"root")
        assertNull(result.expansion)
        assertEquals(1,result.diagnostics.count {"Reviewed owned DataTable columns getter" in it.message})
        assertTrue(result.diagnostics.toString(),result.diagnostics.any {"toolbar cache requires a direct null-guarded source block" in it.message})
    }

    fun testSwitchQueryCannotEraseTheCapability()=withheld("java.util.List<?> columns=table.getColumns();return columns.size();","java.util.List<?> columns=switch(1){case 1 -> table.getColumns();default -> java.util.List.of();};columns.clear();return 0;")
    fun testUnusedGetterDoesNotExposeItsList() {
        val java=WicketTableColumnsReadFixture.install(myFixture)
        change("java.util.List<?> columns=table.getColumns();return columns.size();","table.getColumns();return 0;");matched(java)
    }
    fun testQueryOutsideOwnedToolbarConstructionHasNoNewAuthority()=withheld("new EmptyToolbar(table);return this;","columns();return this;",false)
    fun testOversizedDeferredBodyWithholdsTheExpansionAndRetainsCompletedQuery() {
        val java=WicketTableColumnsReadFixture.install(myFixture)
        change("return columns.size();","int count=0;"+"count++;".repeat(6000)+"return columns.size();")
        val result=WicketLayoutSetupAnalyzer(factories=true).analyze(java.classes.single(),"root")
        assertNull(result.expansion)
        assertTrue(result.diagnostics.toString(),result.diagnostics.any {"budget was exceeded" in it.message})
        assertTrue(result.diagnostics.toString(),result.diagnostics.any {"Reviewed owned DataTable columns getter" in it.message})
    }
    fun testCancellationDuringListFactoryProvenancePropagates() {
        WicketTableColumnsReadFixture.install(myFixture)
        val shell=PsiManager.getInstance(project).findFile(myFixture.findFileInTempDir("src/main/java/example/TableShell.java")) as PsiJavaFile
        val owner=shell.classes.single();val toolbar=owner.innerClasses.single {it.name=="EmptyToolbar"}
        val constructor=toolbar.constructors.single()
        val all=com.intellij.psi.util.PsiTreeUtil.findChildrenOfType(shell,PsiNewExpression::class.java)
        val allocation=all.single {it.classReference?.referenceName=="EmptyToolbar"}
        val proof=WicketOwnedTableForwarding.Proof(allocation,constructor,(constructor.body!!.statements.first() as PsiExpressionStatement).expression as PsiMethodCallExpression,
            owner.fields.single {it.name=="table"},all.single {it.classReference?.referenceName=="RowsTable"},owner.constructors.single())
        val query=com.intellij.psi.util.PsiTreeUtil.findChildrenOfType(shell,PsiMethodCallExpression::class.java).single {it.methodExpression.referenceName=="getColumns"}
        val helper=WicketTableColumnsRead({},{if(it is PsiMethod && it.name=="of") throw com.intellij.openapi.progress.ProcessCanceledException()},{emptyList()},{_,message->throw IllegalStateException(message)})
        assertThrows(com.intellij.openapi.progress.ProcessCanceledException::class.java) {helper.inspect(query,proof,null)}
    }

    fun testRestrictedReferenceVisitorKeepsAuditingOtherQualifierEffects() {
        val java=WicketTableColumnsReadFixture.install(myFixture)
        change("return columns.size();","if(effect().flag)return columns.size();return 0;")
        change("public TableShell configure()","private static class Holder {boolean flag;}private Holder effect(){getPage().removeAll();return new Holder();}public TableShell configure()")
        val result=WicketLayoutSetupAnalyzer(factories=true).analyze(java.classes.single(),"root")
        assertNull(result.expansion);assertTrue(result.diagnostics.any {"Reviewed owned DataTable columns getter" in it.message})
        assertTrue(result.diagnostics.toString(),result.diagnostics.any {"enclosing or captured component hierarchy" in it.message})
    }
}
