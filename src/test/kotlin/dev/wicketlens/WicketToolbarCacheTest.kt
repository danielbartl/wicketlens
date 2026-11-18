package dev.wicketlens

import com.intellij.psi.*
import com.intellij.openapi.command.WriteCommandAction
import dev.wicketlens.analysis.*
import dev.wicketlens.model.DiagnosticCode

class WicketToolbarCacheTest:WicketProjectPsiTestCase() {
    private fun change(before:String,after:String) {
        val file=PsiManager.getInstance(project).findFile(myFixture.findFileInTempDir("src/main/java/example/TableShell.java"))!!
        assertTrue(before in file.text)
        WriteCommandAction.runWriteCommandAction(project) {PsiDocumentManager.getInstance(project).getDocument(file)!!.setText(file.text.replace(before,after))}
        PsiDocumentManager.getInstance(project).commitAllDocuments()
    }
    fun testNullGuardedOwnedCacheRegistrationKeepsRuntimeStateAndPrivateIdsUnknown() {
        val java=WicketToolbarCacheFixture.install(myFixture)
        val report=WicketPageAnalyzer().analyze(java.classes.single())!!
        assertEquals(report.warnings.toString(),1,report.warnings.count {"Reviewed source null-guarded owned toolbar cache" in it.message})
        assertEquals(report.warnings.toString(),2,report.matching!!.findings.count {it.code==DiagnosticCode.MATCHED})
        assertEquals(setOf("shell","title"),report.nodes.mapNotNull {it.wicketId}.toSet())
    }

    private fun withheld(before:String,after:String) {
        val java=WicketToolbarCacheFixture.install(myFixture);change(before,after)
        val result=WicketLayoutSetupAnalyzer(factories=true).analyze(java.classes.single(),"root")
        assertNull(result.diagnostics.toString(),result.expansion)
        assertFalse(result.diagnostics.toString(),result.diagnostics.any {"Reviewed source null-guarded owned toolbar cache" in it.message})
    }
    private fun matched(java:PsiJavaFile) {
        val report=WicketPageAnalyzer().analyze(java.classes.single())!!
        assertEquals(report.warnings.toString(),1,report.warnings.count {"Reviewed source null-guarded owned toolbar cache" in it.message})
        assertEquals(2,report.matching!!.findings.count {it.code==DiagnosticCode.MATCHED})
        assertEquals(setOf("shell","title"),report.nodes.mapNotNull {it.wicketId}.toSet())
    }
    fun testReverseNullGuardUsesTheSameProtocol() {val java=WicketToolbarCacheFixture.install(myFixture);change("cache==null","null==cache");matched(java)}
    fun testExplicitThisReferencesUseTheSameOwner() {val java=WicketToolbarCacheFixture.install(myFixture);change("cache==null","this.cache==null");change("cache=new EmptyToolbar","this.cache=new EmptyToolbar");change("addBottomToolbar(cache)","addBottomToolbar(this.cache)");matched(java)}
    fun testPrivatePlainCellReplacementKeepsItsExistingIsolatedScope() {val java=WicketToolbarCacheFixture.install(myFixture);change("return this;","cache.cell.addOrReplace(new org.apache.wicket.markup.html.basic.Label(\"content\",\"Empty\"));return this;");matched(java)}
    fun testPrivateCacheMustNotBecomePublic()=withheld("private EmptyToolbar cache;","public EmptyToolbar cache;")
    fun testStaticCacheIsNotOwnedInstanceState()=withheld("private EmptyToolbar cache;","private static EmptyToolbar cache;")
    fun testExplicitNullInitializerRemainsOutsideTheDefaultNullContract()=withheld("private EmptyToolbar cache;","private EmptyToolbar cache=null;")
    fun testBorrowedCacheInitializerIsUnreviewed()=withheld("private EmptyToolbar cache;","private EmptyToolbar cache=borrowed();private EmptyToolbar borrowed(){return null;}")
    fun testBroaderDeclaredCacheTypeDoesNotEstablishFinalToolbarIdentity()=withheld("private EmptyToolbar cache;","private AbstractToolbar cache;")
    fun testMissingNullGuardIsUnreviewed()=withheld("if(cache==null){cache=new EmptyToolbar(table);table.addBottomToolbar(cache);}","cache=new EmptyToolbar(table);table.addBottomToolbar(cache);")
    fun testNonNullGuardIsUnreviewed()=withheld("cache==null","cache!=null")
    fun testElseBranchIsNotReconciledAsInitialCacheSetup()=withheld("table.addBottomToolbar(cache);}","table.addBottomToolbar(cache);}else{table.setVisible(false);}")
    fun testCompoundGuardIsNotAStandaloneCacheProtocol()=withheld("cache==null","cache==null && isVisible()")
    fun testNestedGuardDoesNotAcquireDirectSetupAuthority()=withheld("if(cache==null)","if(isVisible())if(cache==null)")
    fun testExtraInitializationEffectsWithholdTheProtocol()=withheld("table.addBottomToolbar(cache);}","table.addBottomToolbar(cache);cache.setVisible(true);}")
    fun testMissingRegistrationIsUnreviewed()=withheld("table.addBottomToolbar(cache);","")
    fun testDuplicateRegistrationIsUnreviewed()=withheld("table.addBottomToolbar(cache);","table.addBottomToolbar(cache);table.addBottomToolbar(cache);")
    fun testRegistrationMustUseTheCachedAllocation()=withheld("addBottomToolbar(cache)","addBottomToolbar(new EmptyToolbar(table))")
    fun testRegistrationCannotUseAnAliasedReceiver()=withheld("cache=new EmptyToolbar(table);table.addBottomToolbar(cache);","cache=new EmptyToolbar(table);DataTable<String,String> alias=table;alias.addBottomToolbar(cache);")
    fun testCompetingWritesInAnotherMethodWithholdOwnership()=withheld("public TableShell configure()","private void reset(){cache=null;}public TableShell configure()")
    fun testCacheGetterDoesNotAcquireAnEscapeContract()=withheld("public TableShell configure()","public AbstractToolbar expose(){return cache;}public TableShell configure()")
    fun testLocalCacheAliasDoesNotAcquireAnEscapeContract()=withheld("return this;","EmptyToolbar alias=cache;return this;")
    fun testCacheReadBeforeInitializationIsUnreviewed()=withheld("if(cache==null)","cache.setVisible(true);if(cache==null)")
    fun testReplacementMustUseItsPrivatePlainCell()=withheld("return this;","cache.addOrReplace(new org.apache.wicket.markup.html.basic.Label(\"content\"));return this;")
    fun testMultiplePostGuardPlacementsAreUnreviewed()=withheld("return this;","cache.cell.addOrReplace(new org.apache.wicket.markup.html.basic.Label(\"a\"));cache.cell.addOrReplace(new org.apache.wicket.markup.html.basic.Label(\"b\"));return this;")
    fun testSameTypedForeignTableCannotBorrowReceiverIdentity() {
        val java=WicketToolbarCacheFixture.install(myFixture)
        change("private EmptyToolbar cache;","private final DataTable<String,String> other;private EmptyToolbar cache;")
        change("add(table);}","add(table);other=new RowsTable(\"other\",List.of(),new ListDataProvider<String>(List.of()),10);add(other);}")
        change("table.addBottomToolbar(cache)","other.addBottomToolbar(cache)")
        val result=WicketLayoutSetupAnalyzer(factories=true).analyze(java.classes.single(),"root")
        assertNull(result.expansion);assertFalse(result.diagnostics.any {"Reviewed source null-guarded owned toolbar cache" in it.message})
    }
    fun testSourceCellSubclassDoesNotBorrowPlainContainerDispatch() {
        val java=WicketToolbarCacheFixture.install(myFixture)
        change("new org.apache.wicket.markup.html.WebMarkupContainer(\"cell\")","new FancyCell(\"cell\")")
        change("public TableShell configure()","private static class FancyCell extends org.apache.wicket.markup.html.WebMarkupContainer {FancyCell(String id){super(id);}public org.apache.wicket.MarkupContainer addOrReplace(org.apache.wicket.Component... children){getPage().removeAll();return this;}}public TableShell configure()")
        change("return this;\n}","cache.cell.addOrReplace(new org.apache.wicket.markup.html.basic.Label(\"content\"));return this;\n}")
        val result=WicketLayoutSetupAnalyzer(factories=true).analyze(java.classes.single(),"root")
        assertNull(result.expansion);assertFalse(result.diagnostics.any {"Reviewed source null-guarded owned toolbar cache" in it.message})
    }
    fun testShadowedFieldsDoNotBecomeCompetingCacheWrites() {val java=WicketToolbarCacheFixture.install(myFixture);change("private EmptyToolbar cache;","private static class Shadow {Object cache;void reset(){cache=null;}}private EmptyToolbar cache;");matched(java)}
    fun testDocumentationReferencesDoNotEscapeTheCache() {val java=WicketToolbarCacheFixture.install(myFixture);change("public TableShell configure()","/** See {@link #cache}. */public TableShell configure()");matched(java)}
    fun testOversizedSemanticCallbackStillWithholdsAllExpansion() {
        val java=WicketToolbarCacheFixture.install(myFixture)
        change("return columns.size();","int count=0;"+"count++;".repeat(6000)+"return columns.size();")
        val result=WicketLayoutSetupAnalyzer(factories=true).analyze(java.classes.single(),"root")
        assertNull(result.expansion);assertTrue(result.diagnostics.toString(),result.diagnostics.any {"budget was exceeded" in it.message})
    }
    fun testSemanticTraversalDoesNotSpendTheBudgetOnCommentText() {val java=WicketToolbarCacheFixture.install(myFixture);change("return columns.size();","/*"+" comment ".repeat(10000)+"*/return columns.size();");matched(java)}
    fun testCompletedCacheFactSurvivesLaterUnreviewedSourceEffects() {
        val java=WicketToolbarCacheFixture.install(myFixture);change("return this;","getPage().removeAll();return this;")
        val result=WicketLayoutSetupAnalyzer(factories=true).analyze(java.classes.single(),"root")
        assertNull(result.expansion);assertEquals(1,result.diagnostics.count {"Reviewed source null-guarded owned toolbar cache" in it.message})
    }

    private fun inspectReferences(tick:(PsiElement)->Unit):WicketToolbarCacheContract.Proof {
        WicketToolbarCacheFixture.install(myFixture)
        val file=PsiManager.getInstance(project).findFile(myFixture.findFileInTempDir("src/main/java/example/TableShell.java")) as PsiJavaFile
        val owner=file.classes.single();val toolbar=owner.innerClasses.single {it.name=="EmptyToolbar"}
        val ctor=toolbar.constructors.single()
        val all=com.intellij.psi.util.PsiTreeUtil.findChildrenOfType(file,PsiNewExpression::class.java)
        val allocation=all.single {it.classReference?.referenceName=="EmptyToolbar"}
        val forward=WicketOwnedTableForwarding.Proof(allocation,ctor,(ctor.body!!.statements.first() as PsiExpressionStatement).expression as PsiMethodCallExpression,
            owner.fields.single {it.name=="table"},all.single {it.classReference?.referenceName=="RowsTable"},owner.constructors.single())
        val assignment=allocation.parent as PsiAssignmentExpression
        return WicketToolbarCacheContract(tick,{},{it.resolveMethod()!!},{_,message->throw IllegalStateException(message)}).inspect(assignment,forward)
    }
    fun testCancellationDuringScopedCacheReferenceSearchPropagates() {
        assertThrows(com.intellij.openapi.progress.ProcessCanceledException::class.java) {
            inspectReferences {if(it is PsiReferenceExpression) throw com.intellij.openapi.progress.ProcessCanceledException()}
        }
    }
    fun testScopedReferenceSearchUsesTheCallersWorkBudget() {
        var references=0
        try {inspectReferences {if(it is PsiReferenceExpression && ++references>1)throw IllegalStateException("reference-budget")};fail("Expected reference budget")}
        catch(e:IllegalStateException){assertEquals("reference-budget",e.message)}
        assertEquals(2,references)
    }
}
