package dev.wicketlens

import dev.wicketlens.analysis.*
import dev.wicketlens.model.*

class WicketSelectionClearTest : WicketProjectPsiTestCase() {
    fun testBulkConfirmationHasSourceModelClearAndSameTableRefreshDeclarations() {
        val java=WicketSelectionClearFixture.install(myFixture);val page=java.classes.single()
        val primary=WicketPageAnalyzer().analyze(page)!!
        val result=WicketConfirmationContexts().analyze(page,primary)
        assertEquals(primary.warnings.toString()+result.diagnostics,2,result.reports.size)
        val bulk=result.reports.single {it.submissionTemplate!!.confirmation!!.selectionClears.isNotEmpty()}
        val metadata=bulk.submissionTemplate!!;val operation=metadata.confirmation!!.selectionClears.single()
        assertEquals(listOf("SOURCE_DATA_CALL","FEEDBACK","SOURCE_SELECTION_CALL","AJAX_REFRESH"),metadata.effects.map {it.kind})
        assertEquals("rows",operation.targetComponentPath)
        assertEquals("rows",metadata.effects.last().targetComponentPath)
        assertEquals(5,bulk.matching!!.findings.size)
        assertTrue(bulk.matching.findings.all {it.code==DiagnosticCode.MATCHED && it.confidence==Confidence.INFERRED})
    }
    private fun withheld(table:String=WicketSelectionClearFixture.table,column:String=WicketSelectionClearFixture.column,body:String=WicketSelectionClearFixture.page,independent:Boolean=true) {
        val java=WicketSelectionClearFixture.install(myFixture,body,table,column);val primary=WicketPageAnalyzer().analyze(java.classes.single())!!
        val result=WicketConfirmationContexts().analyze(java.classes.single(),primary)
        assertTrue(result.diagnostics.toString(),result.reports.none {it.submissionTemplate!!.confirmation!!.selectionClears.isNotEmpty()})
        if(independent) assertTrue("The independent row-delete context must remain available: ${result.diagnostics}",result.reports.any {it.submissionTemplate!!.effects.map {e->e.kind}==listOf("SOURCE_DATA_CALL","FEEDBACK","AJAX_REFRESH")})
        assertTrue(primary.warnings.toString()+result.diagnostics,primary.initialContentSetup==null || result.diagnostics.isNotEmpty())
    }
    fun testWrongNullGuardIsWithheld()=withheld(table=WicketSelectionClearFixture.table.replace("selectionColumn!=null","selectionColumn==null"))
    fun testElseBranchIsWithheld()=withheld(table=WicketSelectionClearFixture.table.replace("target.add(this);", "target.add(this);").replace(".getObject().clear();}",".getObject().clear();} else {System.gc();}"))
    fun testAdditionalGuardBodyEffectsAreWithheld()=withheld(table=WicketSelectionClearFixture.table.replace(".clear();}",".clear();System.gc();}"))
    fun testAdditionalHelperStatementsAreWithheld()=withheld(table=WicketSelectionClearFixture.table.replace("target.add(this);", "System.gc();target.add(this);"))
    fun testMissingRefreshIsWithheld()=withheld(table=WicketSelectionClearFixture.table.replace("target.add(this);", ""))
    fun testForeignRefreshReceiverIsWithheld()=withheld(table=WicketSelectionClearFixture.table.replace("target.add(this);", "((AjaxRequestTarget)null).add(this);"))
    fun testForeignRefreshedComponentIsWithheld()=withheld(table=WicketSelectionClearFixture.table.replace("target.add(this);", "target.add(new WebMarkupContainer(\"foreign\"));"))
    fun testAdditionalRefreshedComponentIsWithheld()=withheld(table=WicketSelectionClearFixture.table.replace("target.add(this);", "target.add(this,this);"))
    fun testReassignedHelperTargetIsWithheld()=withheld(table=WicketSelectionClearFixture.table.replace("target.add(this);", "target=null;target.add(this);"))
    fun testHelperTargetEscapeIsWithheld()=withheld(table=WicketSelectionClearFixture.table.replace("target.add(this);", "info(target);target.add(this);"))
    fun testForeignCallbackTargetIsWithheld()=withheld(body=WicketSelectionClearFixture.page.replace("clearSelection(t)","clearSelection(null)"))
    fun testCallbackTargetAliasIsWithheld()=withheld(body=WicketSelectionClearFixture.page.replace("table.clearSelection(t);", "AjaxRequestTarget alias=t;table.clearSelection(alias);"))
    fun testUnstableColumnFieldIsWithheld()=withheld(table=WicketSelectionClearFixture.table.replace("private final SelectionBox", "private SelectionBox"))
    fun testPublicColumnFieldIsWithheld()=withheld(table=WicketSelectionClearFixture.table.replace("private final SelectionBox", "public final SelectionBox"))
    fun testAnotherColumnIsNotTheGuardedOne()=withheld(table=WicketSelectionClearFixture.table.replace("selectionColumn.getSelection()", "new SelectionBox(null).getSelection()"))
    fun testImpureGetterIsWithheld()=withheld(column=WicketSelectionClearFixture.column.replace("return selection;", "System.gc();return selection;"))
    fun testStaticGetterIsWithheld()=withheld(column=WicketSelectionClearFixture.column.replace("public IModel", "public static IModel"))
    fun testNonFinalModelStorageIsWithheld()=withheld(column=WicketSelectionClearFixture.column.replace("private final IModel", "private IModel"))
    fun testPublicModelStorageIsWithheld()=withheld(column=WicketSelectionClearFixture.column.replace("private final IModel", "public final IModel"))
    fun testGetterReturningBorrowedStorageIsWithheld()=withheld(column=WicketSelectionClearFixture.column.replace("return selection;", "return new SelectionBox(null).selection;"))
    fun testAliasedModelReadIsWithheld()=withheld(table=WicketSelectionClearFixture.table.replace("selectionColumn.getSelection().getObject().clear();", "var model=selectionColumn.getSelection();model.getObject().clear();"))
    fun testDifferentCollectionOperationIsWithheld()=withheld(table=WicketSelectionClearFixture.table.replace(".getObject().clear()", ".getObject().isEmpty()"))
    fun testDifferentModelOperationIsWithheld()=withheld(table=WicketSelectionClearFixture.table.replace("selectionColumn.getSelection().getObject().clear();", "selectionColumn.getSelection().detach();"))
    fun testFreshAnonymousReceiverOverrideIsWithheld()=withheld(body=WicketSelectionClearFixture.page.replace("new TableBox(\"rows\")", "new TableBox(\"rows\") { @Override public TableBox clearSelection(AjaxRequestTarget target) {System.gc();return this;} }"),independent=false)
    fun testActualNamedReceiverOverrideCannotBorrowTheBaseBody() {
        myFixture.addFileToProject("src/main/java/example/OtherTable.java", """
            package example;public class OtherTable extends TableBox {
                public OtherTable(String id) {super(id);}
                @Override public TableBox clearSelection(org.apache.wicket.ajax.AjaxRequestTarget target) {System.gc();return this;}
            }
        """)
        withheld(body=WicketSelectionClearFixture.page.replace("new TableBox(\"rows\")", "new OtherTable(\"rows\")"))
    }
    fun testUnbracedAndReversedNullGuardRetainsTheSameSourceFacts() {
        val java=WicketSelectionClearFixture.install(myFixture,tableSource=WicketSelectionClearFixture.table.replace("if(selectionColumn!=null) {selectionColumn.getSelection().getObject().clear();}", "if(null!=selectionColumn) selectionColumn.getSelection().getObject().clear();"))
        val result=WicketConfirmationContexts().analyze(java.classes.single(),WicketPageAnalyzer().analyze(java.classes.single())!!)
        assertEquals(result.diagnostics.toString(),2,result.reports.size)
    }
    fun testInheritedHelperWithoutItsOwnBodyIsOutsideTheDirectSourceContract() {
        myFixture.addFileToProject("src/main/java/example/OtherTable.java", "package example;public class OtherTable extends TableBox {public OtherTable(String id) {super(id);}}")
        withheld(body=WicketSelectionClearFixture.page.replace("new TableBox(\"rows\")", "new OtherTable(\"rows\")"))
    }
    fun testInheritedGetterWithoutItsOwnBodyIsOutsideTheDirectSourceContract() {
        myFixture.addFileToProject("src/main/java/example/OtherSelection.java", "package example;public class OtherSelection extends SelectionBox {public OtherSelection() {super(null);}}")
        withheld(table=WicketSelectionClearFixture.table.replace("private final SelectionBox", "private final OtherSelection"))
    }
    fun testSourceCollectionOverrideCannotBorrowTheJdkClearDeclaration() {
        myFixture.addFileToProject("src/main/java/example/CustomSelection.java", "package example;public class CustomSelection extends java.util.ArrayList<Object> {@Override public void clear() {super.clear();}}")
        withheld(column=WicketSelectionClearFixture.column.replace("Collection<?>","CustomSelection"))
    }
    fun testEffectAndContextBudgetsWithholdAllConfirmationGraphs() {
        val java=WicketSelectionClearFixture.install(myFixture);val primary=WicketPageAnalyzer().analyze(java.classes.single())!!
        for(limit in listOf(WicketSubmissionContexts.Limits(effects=3),WicketSubmissionContexts.Limits(contexts=1),WicketSubmissionContexts.Limits(sources=0),WicketSubmissionContexts.Limits(steps=0))) {
            val result=WicketConfirmationContexts(limit).analyze(java.classes.single(),primary)
            assertFalse(result.toString(),result.complete);assertTrue(result.reports.isEmpty())
        }
    }
}
