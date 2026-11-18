package dev.wicketlens

import dev.wicketlens.analysis.*
import dev.wicketlens.model.*

class WicketRecordActionTest : WicketProjectPsiTestCase() {
    fun testRowAndBulkActionsHaveSeparateSourceRecordUpdateContexts() {
        val java=WicketRecordActionFixture.install(myFixture);val primary=WicketPageAnalyzer().analyze(java.classes.single())!!
        val result=WicketActionContexts().analyze(java.classes.single(),primary)
        assertEquals(primary.warnings.toString()+result.diagnostics,2,result.reports.size)
        val row=result.reports.single {it.actionTemplate!!.selectionClears.isEmpty()}
        val bulk=result.reports.single {it.actionTemplate!!.selectionClears.isNotEmpty()}
        assertEquals(listOf("SOURCE_RECORD_UPDATE_CALL","FEEDBACK","AJAX_REFRESH"),row.actionTemplate!!.effects.map {it.kind})
        assertEquals(listOf("SOURCE_RECORD_UPDATE_CALL","FEEDBACK","SOURCE_SELECTION_CALL","AJAX_REFRESH"),bulk.actionTemplate!!.effects.map {it.kind})
        for(report in result.reports) {
            val update=report.actionTemplate!!.updates.single()
            assertEquals("example.ConfirmationPage.Row",update.recordClassName)
            assertEquals(listOf("PRESERVED_COMPONENT","SOURCE_ENUM_CONSTANT"),update.componentArguments.map {it.kind})
            assertEquals(listOf("id","state"),update.componentArguments.map {it.componentName})
            assertEquals(1,report.matching!!.findings.size)
            assertTrue(report.matching.findings.all {it.code==DiagnosticCode.MATCHED && it.confidence==Confidence.INFERRED})
            assertTrue(report.nodes.all {it.template && !it.idRenameSafe})
            assertNull(report.submissionTemplate)
        }
        assertNull(primary.actionTemplate);assertTrue(result.complete)
    }
    fun testPlainPageHasInitialDeclarationsAndAnIndependentActionContext() {
        val java=WicketRecordActionFixture.install(myFixture,body=WicketRecordActionFixture.plainPage)
        val initial=WicketPageAnalyzer().analyze(java.classes.single())!!
        assertNull(initial.initialContentSetup)
        assertEquals(1,initial.storedActionRegistrations.size)
        val result=WicketActionContexts().analyze(java.classes.single(),initial)
        assertEquals(result.diagnostics.toString(),1,result.reports.size)
        assertTrue(result.reports.single().matching!!.findings.all {it.code==DiagnosticCode.MATCHED && it.confidence==Confidence.INFERRED})
    }
    fun testIncompleteSourceWithholdsDiscoveryWithoutThrowing() {
        val java=WicketRecordActionFixture.install(myFixture,WicketRecordActionFixture.page+"\n{")
        val initial=WicketPageAnalyzer().analyze(java.classes.single())!!
        val result=WicketActionContexts().analyze(java.classes.single(),initial)
        assertFalse(result.complete);assertTrue(result.reports.isEmpty());assertTrue(result.diagnostics.isNotEmpty())
    }
    fun testUnrelatedIncompleteSourceDoesNotPoisonActionDiscovery() {
        val body=WicketRecordActionFixture.page.replace("records.replaceAll(current->current.equals(row)?current.paid():current)","records.removeAll(rows)")+"\n{"
        val java=WicketRecordActionFixture.install(myFixture,body);val primary=WicketPageAnalyzer().analyze(java.classes.single())!!
        val result=WicketActionContexts().analyze(java.classes.single(),primary)
        assertTrue(result.complete);assertTrue(result.reports.isEmpty());assertTrue(result.diagnostics.isEmpty())
    }
    private fun withheld(body:String=WicketRecordActionFixture.page) {
        val java=WicketRecordActionFixture.install(myFixture,body);val initial=WicketPageAnalyzer().analyze(java.classes.single())!!
        val result=WicketActionContexts().analyze(java.classes.single(),initial)
        assertTrue(result.diagnostics.toString(),result.reports.isEmpty())
        assertTrue(initial.warnings.toString()+result.diagnostics,initial.initialContentSetup==null || result.diagnostics.isNotEmpty())
    }
    fun testPublicUpdateHelperIsWithheld()=withheld(WicketRecordActionFixture.page.replace("private void markPaid","public void markPaid"))
    fun testStaticUpdateHelperIsWithheld()=withheld(WicketRecordActionFixture.page.replace("private void markPaid","private static void markPaid"))
    fun testExtraHelperStatementIsWithheld()=withheld(WicketRecordActionFixture.page.replace("{for(Row row:rows)","{System.gc();for(Row row:rows)"))
    fun testChangedLoopInputIsWithheld()=withheld(WicketRecordActionFixture.page.replace("for(Row row:rows)","for(Row row:records)"))
    fun testExtraLoopStatementIsWithheld()=withheld(WicketRecordActionFixture.page.replace("{records.replaceAll", "{System.gc();records.replaceAll"))
    fun testLoopControlFlowIsWithheld()=withheld(WicketRecordActionFixture.page.replace("records.replaceAll", "if(row!=null) records.replaceAll"))
    fun testPublicStorageIsWithheld()=withheld(WicketRecordActionFixture.page.replace("private final List<Row>","public final List<Row>"))
    fun testNonFinalStorageIsWithheld()=withheld(WicketRecordActionFixture.page.replace("private final List<Row>","private List<Row>"))
    fun testWrongStorageGenericTypeIsWithheld()=withheld(WicketRecordActionFixture.page.replace("new ArrayList<>()","new ArrayList<String>()"))
    fun testBorrowedStorageIsWithheld()=withheld(WicketRecordActionFixture.page.replace("new ArrayList<>()","Collections.emptyList()"))
    fun testForeignStorageReceiverIsWithheld()=withheld(WicketRecordActionFixture.page.replace("records.replaceAll", "new ConfirmationPage().records.replaceAll"))
    fun testBlockUpdaterIsWithheld()=withheld(WicketRecordActionFixture.page.replace("current->current.equals(row)?current.paid():current", "current->{return current.equals(row)?current.paid():current;}"))
    fun testChangedPredicateIsWithheld()=withheld(WicketRecordActionFixture.page.replace("current.equals(row)?", "true?"))
    fun testReversedEqualityDirectionIsWithheld()=withheld(WicketRecordActionFixture.page.replace("current.equals(row)", "row.equals(current)"))
    fun testWrongEqualityArgumentIsWithheld()=withheld(WicketRecordActionFixture.page.replace("current.equals(row)", "current.equals(current)"))
    fun testSpecificEqualsOverloadCannotBorrowObjectEquals()=withheld(WicketRecordActionFixture.page.replace("Row paid()", "boolean equals(Row other) {return true;} Row paid()"))
    fun testChangedFallbackRecordIsWithheld()=withheld(WicketRecordActionFixture.page.replace("?current.paid():current", "?current.paid():row"))
    fun testChangedTransformReceiverIsWithheld()=withheld(WicketRecordActionFixture.page.replace("?current.paid():current", "?row.paid():current"))
    fun testExtraTransformStatementIsWithheld()=withheld(WicketRecordActionFixture.page.replace("{return new Row", "{System.gc();return new Row"))
    fun testComputedUnchangedComponentIsWithheld()=withheld(WicketRecordActionFixture.page.replace("new Row(id,State.PAID)","new Row(id+\"changed\",State.PAID)"))
    fun testWrongUnchangedComponentIsWithheld()=withheld(WicketRecordActionFixture.page.replace("new Row(id,State.PAID)","new Row(\"changed\",State.PAID)"))
    fun testNoEnumSubstitutionIsWithheld()=withheld(WicketRecordActionFixture.page.replace("new Row(id,State.PAID)","new Row(id,state)"))
    fun testExplicitCanonicalConstructorIsWithheld()=withheld(WicketRecordActionFixture.page.replace("Row paid()", "public Row {if(id==null) throw new IllegalArgumentException();} Row paid()"))
    fun testEnumInitializationWithEffectsIsWithheld()=withheld(WicketRecordActionFixture.page.replace("{OPEN,PAID}", "{OPEN,PAID;static {System.gc();}}"))
    fun testRecordWithComponentDataIsWithheld()=withheld(WicketRecordActionFixture.page.replace("record Row(String id,State state)", "record Row(org.apache.wicket.Component id,State state)"))
    fun testAliasedHelperArgumentIsWithheld()=withheld(WicketRecordActionFixture.page.replace("markPaid(List.of(row));", "var alias=row;markPaid(List.of(alias));").replace("markPaid(rows);", "var alias=rows;markPaid(alias);"))
    fun testReassignedAjaxTargetIsWithheld()=withheld(WicketRecordActionFixture.page.replace("target.add(table);", "target=null;target.add(table);").replace("table.clearSelection(target);", "target=null;table.clearSelection(target);"))
    fun testAjaxTargetCannotEscapeThroughFeedback()=withheld(WicketRecordActionFixture.page.replace("success(\"Paid\");", "success(target);").replace("success(\"Paid rows\");", "success(target);"))
    fun testAdditionalUnreviewedCallbackEffectsAreWithheld()=withheld(WicketRecordActionFixture.page.replace("markPaid(List.of(row));", "markPaid(List.of(row));System.gc();").replace("markPaid(rows);", "markPaid(rows);System.gc();"))
    fun testPredicateAndUpdateResultsAreNotHardcodedToPaid() {
        val java=WicketRecordActionFixture.install(myFixture,WicketRecordActionFixture.page.replace("new Row(id,State.PAID)","new Row(id,State.OPEN)"))
        val result=WicketActionContexts().analyze(java.classes.single(),WicketPageAnalyzer().analyze(java.classes.single())!!)
        assertEquals(result.diagnostics.toString(),2,result.reports.size)
        assertTrue(result.reports.all {it.actionTemplate!!.updates.single().componentArguments.last().declarationName.endsWith("State.OPEN")})
    }
    fun testGeneratedRecordEqualsRetainsItsUnevaluatedDeclaration() {
        val java=WicketRecordActionFixture.install(myFixture,WicketRecordActionFixture.page.replace("@Override public boolean equals(Object other) {return other instanceof Row r && r.id.equals(id);}",""))
        val result=WicketActionContexts().analyze(java.classes.single(),WicketPageAnalyzer().analyze(java.classes.single())!!)
        assertEquals(result.diagnostics.toString(),2,result.reports.size)
    }
    fun testUnsupportedBulkTargetDoesNotHideTheIndependentRowContext() {
        val java=WicketRecordActionFixture.install(myFixture,WicketRecordActionFixture.page.replace("table.clearSelection(target);","table.clearSelection(null);"))
        val result=WicketActionContexts().analyze(java.classes.single(),WicketPageAnalyzer().analyze(java.classes.single())!!)
        assertEquals(1,result.reports.size);assertTrue(result.reports.single().actionTemplate!!.selectionClears.isEmpty())
        assertTrue(result.diagnostics.isNotEmpty())
    }
    fun testIndependentCallbacksRetainDifferentContextIds() {
        val java=WicketRecordActionFixture.install(myFixture)
        val result=WicketActionContexts().analyze(java.classes.single(),WicketPageAnalyzer().analyze(java.classes.single())!!)
        assertEquals(2,result.reports.map {it.actionTemplate!!.contextId}.toSet().size)
        assertEquals(1,result.reports.map {it.actionTemplate!!.updates.single().methodLocation}.toSet().size)
    }
    fun testExhaustedBudgetsWithholdAllActionGraphs() {
        val java=WicketRecordActionFixture.install(myFixture);val primary=WicketPageAnalyzer().analyze(java.classes.single())!!
        for(limit in listOf(WicketSubmissionContexts.Limits(steps=0),WicketSubmissionContexts.Limits(sources=0),WicketSubmissionContexts.Limits(contexts=1),WicketSubmissionContexts.Limits(nodes=1),WicketSubmissionContexts.Limits(effects=2))) {
            val result=WicketActionContexts(limit).analyze(java.classes.single(),primary)
            assertFalse(result.toString(),result.complete);assertTrue(result.reports.isEmpty())
        }
    }
    fun testCancellationPropagates() {
        val java=WicketRecordActionFixture.install(myFixture);val primary=WicketPageAnalyzer().analyze(java.classes.single())!!
        val indicator=com.intellij.openapi.progress.EmptyProgressIndicator()
        assertThrows(com.intellij.openapi.progress.ProcessCanceledException::class.java) {
            com.intellij.openapi.progress.ProgressManager.getInstance().runProcess(Runnable {indicator.cancel();WicketActionContexts().analyze(java.classes.single(),primary)},indicator)
        }
    }
}
