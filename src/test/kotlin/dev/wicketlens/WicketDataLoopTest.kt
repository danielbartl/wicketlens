package dev.wicketlens

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressManager
import com.intellij.psi.*
import dev.wicketlens.analysis.WicketPageAnalyzer
import dev.wicketlens.export.WicketContextExporter
import dev.wicketlens.model.DiagnosticCode
import dev.wicketlens.model.PageReport
import dev.wicketlens.ui.WicketReportFormatter

class WicketDataLoopTest : WicketProjectPsiTestCase() {
    private fun analyze(loop: String = WicketDataLoopFixture.body, members: String = WicketDataLoopFixture.members, after: String = "add(new org.apache.wicket.markup.html.basic.Label(\"title\",\"Ready\"));"): PageReport =
        WicketPageAnalyzer().analyze(WicketDataLoopFixture.install(myFixture, loop, members, after).classes.single())!!
    private fun matched(report: PageReport) {
        assertEquals(report.warnings.toString(), 1, report.matching!!.findings.count { it.code == DiagnosticCode.MATCHED })
        assertTrue(report.warnings.toString(), report.warnings.any { "data loop audited" in it.message })
        assertEquals(listOf("title"), report.nodes.mapNotNull { it.wicketId })
    }
    private fun withheld(report: PageReport) {
        assertTrue(report.toString(), report.matching!!.findings.none { it.code == DiagnosticCode.MATCHED })
        assertTrue(report.warnings.toString(), report.warnings.any { "data loop" in it.message || "Data initialization loops" in it.message })
    }

    fun testCanonicalRecordSeedingDoesNotProduceDataRowsOrModelValues() {
        val report=analyze();matched(report)
        val json=WicketContextExporter().export(report)
        assertFalse(json.contains("Row 23"));assertFalse(json.contains("Row 1"))
        assertTrue(json.contains("collection contents remain unknown"))
    }
    fun testFiniteEmptyLoopDoesNotInvalidateLaterDeclaredBindings() = matched(analyze("for(int i=0;i<1;i++) {}"))
    fun testInvoiceStyleEnumsArraysDecimalAndDatesAreAuditedWithoutExecuting() {
        matched(analyze("""
            String[] names={"ACME","Other"}; java.util.Currency[] currencies={java.util.Currency.getInstance("EUR")};
            for(int i=1;i<=23;i++) {
                Status status=i%2==0?Status.OPEN:Status.PAID;
                rows.add(new Row(i,String.format("INV-%04d",i),status,java.math.BigDecimal.valueOf(13750L*i,2),
                    currencies[i%currencies.length],java.time.LocalDate.of(2026,10,1).plusDays(i*3L),names[i%names.length]));
            }
        """.trimIndent(), """
            private final java.util.List<Row> rows=new java.util.ArrayList<>();
            public enum Status { OPEN, PAID }
            public record Row(long id,String name,Status status,java.math.BigDecimal total,java.util.Currency currency,java.time.LocalDate date,String customer) {}
        """.trimIndent()))
    }
    fun testDescendingFiniteLoopAndExplicitThisDataFieldAreReviewed() = matched(analyze("for(long i=3L;i>=0L;--i) { this.rows.add(new Row(i,\"Row\")); }"))
    fun testVeryLargeFiniteRangeIsAuditedOnceRatherThanUnrolled() = matched(analyze("for(long i=0L;i<Long.MAX_VALUE;i++) { rows.add(new Row(i,\"Row\")); }"))
    fun testZeroTripLoopStillAuditsItsBodyConservatively() = withheld(analyze("for(int i=1;i<0;i++) { add(new org.apache.wicket.markup.html.basic.Label(\"bad\")); }"))
    fun testComponentConstructionAndAttachmentCannotHideInADataLoop() = withheld(analyze("for(int i=0;i<3;i++) { rows.add(new Row(i,\"Row\")); add(new org.apache.wicket.markup.html.basic.Label(\"bad\")); }"))
    fun testExternalScalarWritesCannotLeaveAKnownIdSnapshot() = withheld(analyze("String id=\"title\"; for(int i=0;i<3;i++) { id=\"other\"; }", after="add(new org.apache.wicket.markup.html.basic.Label(id));"))
    fun testExternalArrayWritesRemainOutsideTheLoopContract() = withheld(analyze("String[] ids={\"title\"};for(int i=0;i<3;i++) { ids[0]=\"other\"; }"))
    fun testCounterMutationInTheBodyDoesNotProveTermination() = withheld(analyze("for(int i=0;i<3;i++) { i=0; }"))
    fun testOverflowingIntCounterIsNotAccepted() = withheld(analyze("for(int i=0;i<=Integer.MAX_VALUE;i++) { rows.add(new Row(i,\"Row\")); }"))
    fun testOverflowingLongCounterIsNotAccepted() = withheld(analyze("for(long i=0L;i<=Long.MAX_VALUE;i++) { rows.add(new Row(i,\"Row\")); }"))
    fun testWrongCounterDirectionIsNotAccepted() = withheld(analyze("for(int i=0;i<3;i--) { rows.add(new Row(i,\"Row\")); }"))
    fun testUnknownBoundsAreNotAssumedFinite() = withheld(analyze("int limit=rows.size();for(int i=0;i<limit;i++) { rows.add(new Row(i,\"Row\")); }"))
    fun testCallbacksCannotBeCapturedByLoopData() = withheld(analyze("for(int i=0;i<3;i++) { Runnable task=()->removeAll(); }"))
    fun testMethodReferencesCannotEscapeTheLoopAudit() = withheld(analyze("for(int i=0;i<3;i++) { Runnable task=this::removeAll; }"))
    fun testEarlyReturnsCannotCreateUnconditionalLaterBindings() = withheld(analyze("for(int i=0;i<3;i++) { return; }"))
    fun testNestedLoopsRemainExplicitlyUnsupported() = withheld(analyze("for(int i=0;i<3;i++) { for(int j=0;j<3;j++) {} }"))
    fun testUnreviewedHelpersNameTheFirstBlockingCall() {
        val report=analyze("for(int i=0;i<3;i++) { seed(i); }", WicketDataLoopFixture.members+"private void seed(int n) { removeAll(); }")
        withheld(report)
        assertTrue(report.warnings.toString(),report.warnings.any { "example.ExamplePage.seed" in it.message })
        val text=WicketReportFormatter().format(report)
        val first=text.substringAfter("Diagnostics:\n").lineSequence().first()
        assertTrue(first,first.contains("Source layout setup stopped:"))
        assertTrue(first,first.contains("data loop is not isolated"))
    }
    fun testCanonicalRecordWithAComponentFieldIsNotData() = withheld(analyze("for(int i=0;i<3;i++){rows.add(new Row(i,new org.apache.wicket.markup.html.basic.Label(\"bad\")));}",members="private final java.util.List<Row> rows=new java.util.ArrayList<>();public record Row(long id,org.apache.wicket.Component component) {}"))
    fun testCustomRecordConstructorsAreNotAssumedIsolated() = withheld(analyze(members=WicketDataLoopFixture.members.replace("public record Row(long id,String name) {}", "public record Row(long id,String name) { public Row { if(id<0) throw new IllegalArgumentException(); } }")))
    fun testACollectionAliasCannotMasqueradeAsFreshArrayListStorage() = withheld(analyze("java.util.List<Row> data=new java.util.ArrayList<>();java.util.List<Row> alias=data;for(int i=0;i<3;i++) { alias.add(new Row(i,\"Row\")); }"))
    fun testReassignedCollectionReceiversRemainUnresolved() = withheld(analyze("java.util.List<Row> data=new java.util.ArrayList<>();data=java.util.List.of();for(int i=0;i<3;i++) { data.add(new Row(i,\"Row\")); }"))
    fun testErasingThePageToObjectDoesNotAllowItToEnterData() = withheld(analyze("Object escaped=this;for(int i=0;i<3;i++) { rows.add(new Row(i,String.valueOf(escaped))); }"))
    fun testUserToStringIsNotInvokedByFormattingLoopData() = withheld(analyze("for(int i=0;i<3;i++){ Row row=new Row(i,\"Row\");String text=String.format(\"%s\",row); }"))
    fun testRecordConcatenationCannotInvokeAnImplicitUserToString() = withheld(analyze("for(int i=0;i<3;i++){String text=\"Row \"+new Row(i,\"Row\");}",WicketDataLoopFixture.members.replace("public record Row(long id,String name) {}","public record Row(long id,String name) {public String toString(){System.setProperty(\"loop\",\"effect\");return name;}}")))
    fun testCompoundConcatenationCannotInvokeAnImplicitUserToString() = withheld(analyze("for(int i=0;i<3;i++){String text=\"Row \";text+=new Row(i,\"Row\");}",WicketDataLoopFixture.members.replace("public record Row(long id,String name) {}","public record Row(long id,String name) {public String toString(){System.setProperty(\"loop\",\"effect\");return name;}}")))
    fun testScalarConcatenationAndCompoundAssignmentRemainReviewed() = matched(analyze("for(int i=0;i<3;i++){String text=\"Row \"+i+\" \"+true;text+=i;rows.add(new Row(i,text));}"))
    fun testRecordStaticInitializationCannotReachTheComponentGraph() = withheld(analyze(members=WicketDataLoopFixture.members.replace("public record Row(long id,String name) {}", "public record Row(long id,String name) {static org.apache.wicket.MarkupContainer parent;static {if(parent!=null)parent.removeAll();}}")))
    fun testCustomEnumConstructionIsNotAssumedIsolated() = withheld(analyze("for(int i=0;i<3;i++){ Status status=Status.OPEN;rows.add(new Row(i,\"Row\")); }",WicketDataLoopFixture.members+"enum Status { OPEN;Status(){System.setProperty(\"loop\",\"effect\");} }"))
    fun testImplicitEnumConstructorFieldInitializersAreNotAssumedIsolated() = withheld(analyze("for(int i=0;i<3;i++){ Status status=Status.OPEN;rows.add(new Row(i,\"Row\")); }",WicketDataLoopFixture.members+"enum Status { OPEN;final String value=seed();private static String seed(){System.setProperty(\"loop\",\"effect\");return \"value\";} }"))
    fun testRecordInterfacesCannotHideInitializationEffects() = withheld(analyze(members=WicketDataLoopFixture.members.replace("public record Row(long id,String name) {}", "public record Row(long id,String name) implements Hook {} interface Hook { String VALUE=seed();static String seed(){System.setProperty(\"loop\",\"effect\");return \"value\";}default String value(){return VALUE;} }")))
    fun testRecordFinalizersAreNotAssumedInertData() = withheld(analyze(members=WicketDataLoopFixture.members.replace("public record Row(long id,String name) {}", "public record Row(long id,String name) { @Override protected void finalize(){System.setProperty(\"loop\",\"effect\");} }")))
    fun testRecordPatternsCannotInvokeImplicitCustomAccessors() = withheld(analyze("for(int i=0;i<3;i++){if((Object)new Row(i,\"Row\") instanceof Row(long id,String name)){rows.add(new Row(id,name));}}",WicketDataLoopFixture.members.replace("public record Row(long id,String name) {}","public record Row(long id,String name) {public String name(){System.setProperty(\"loop\",\"effect\");return name;}}")))
    fun testArbitraryCollectionImplementationsCannotReceiveLoopMutations() = withheld(analyze("java.util.List<Row> data=java.util.List.of();for(int i=0;i<3;i++){data.add(new Row(i,\"Row\"));}"))
    fun testCounterBoundsCanUseImportedConstants() {
        myFixture.addFileToProject("src/main/java/example/Limits.java","package example;public final class Limits { public static final int COUNT=23;}")
        matched(analyze(WicketDataLoopFixture.body.replace("i<=23","i<=Limits.COUNT")))
    }
    fun testSharedDataTypeDepthIsBounded() = withheld(analyze("for(int i=0;i<3;i++){rows.add(new Row(new L1(new L2(new L3(new L4(new L5(new L6(\"deep\"))))))));}","private final java.util.List<Row> rows=new java.util.ArrayList<>();record Row(L1 value){}record L1(L2 value){}record L2(L3 value){}record L3(L4 value){}record L4(L5 value){}record L5(L6 value){}record L6(String value){}"))
    fun testCompletedLoopProofIsRetainedWhenALaterEffectStopsTheGraph() {
        val report=analyze(after="removeAll(); add(new org.apache.wicket.markup.html.basic.Label(\"title\"));")
        assertTrue(report.matching!!.findings.none { it.code==DiagnosticCode.MATCHED })
        assertTrue(report.warnings.any { "data loop audited" in it.message })
        assertTrue(report.warnings.any { "Source layout setup stopped:" in it.message && "data loop is not isolated" !in it.message })
    }
    fun testSharedLoopCountBoundWithholdsTheWholeExpansion() = withheld(analyze((0..16).joinToString("\n") { "for(int i=0;i<1;i++){ rows.add(new Row(i,\"Row\")); }" }))
    fun testLoopProofDoesNotPermitRenamingAnIdAlsoUsedAsData() {
        val java=WicketDataLoopFixture.install(myFixture,"String id=\"title\";for(int i=0;i<3;i++){rows.add(new Row(i,id));}",after="add(new org.apache.wicket.markup.html.basic.Label(id));")
        val report=WicketPageAnalyzer().analyze(java.classes.single())!!;matched(report)
        assertFalse(report.nodes.single { it.wicketId=="title" }.idRenameSafe)
        assertNull(dev.wicketlens.editor.WicketIdRenameTarget.at(java,java.text.indexOf("\"title\"")))
    }
    fun testDataLoopsInOrdinaryHelpersRemainOutsideTheConstructorSlice() = withheld(analyze("seed();",WicketDataLoopFixture.members+"private void seed(){for(int i=0;i<3;i++){rows.add(new Row(i,\"Row\"));}}"))
    fun testSourceStepBudgetDoesNotPublishAPartialGraph() {
        val report=analyze("for(int i=0;i<1;i++){"+(0..5500).joinToString(";") { "int value$it=i" }+";}")
        assertTrue(report.matching!!.findings.none { it.code==DiagnosticCode.MATCHED })
        assertTrue(report.warnings.toString(),report.warnings.any { "budget" in it.message })
    }
    fun testCancellationInsideTheLoopAuditPropagates() {
        val java=WicketDataLoopFixture.install(myFixture)
        val loop=com.intellij.psi.util.PsiTreeUtil.findChildOfType(java,PsiForStatement::class.java)!!
        val indicator=EmptyProgressIndicator();var checks=0
        val tick: (PsiElement)->Unit = { if(++checks>30) indicator.cancel();ProgressManager.checkCanceled() }
        val constants=dev.wicketlens.analysis.WicketSourceConstants({}, { tick(loop) })
        assertThrows(ProcessCanceledException::class.java) { ProgressManager.getInstance().runProcess(Runnable {
            dev.wicketlens.analysis.WicketConstructorDataLoop(java.classes.single().constructors.single(),tick,{},constants::expression,{false},{},{_,reason->throw AssertionError(reason)}).audit(loop)
        },indicator) }
        assertTrue(checks>30)
    }
    fun testSourceEditsRevokeAFormerlySafeLoopProof() {
        val java=WicketDataLoopFixture.install(myFixture)
        matched(WicketPageAnalyzer().analyze(java.classes.single())!!)
        WriteCommandAction.runWriteCommandAction(project) { PsiDocumentManager.getInstance(project).getDocument(java)!!.setText(java.text.replace("rows.add(new Row(i, String.format(\"Row %d\",i)));", "removeAll();")) }
        PsiDocumentManager.getInstance(project).commitAllDocuments()
        withheld(WicketPageAnalyzer().analyze(java.classes.single())!!)
    }
    fun testAnalysisIsReadOnlyAndCancellationPropagates() {
        val java=WicketDataLoopFixture.install(myFixture)
        val before=java.text to java.modificationStamp
        matched(WicketPageAnalyzer().analyze(java.classes.single())!!)
        assertEquals(before,java.text to java.modificationStamp)
        val indicator=EmptyProgressIndicator()
        assertThrows(ProcessCanceledException::class.java) { ProgressManager.getInstance().runProcess(Runnable {
            indicator.cancel();WicketPageAnalyzer().analyze(java.classes.single())
        },indicator) }
    }
}
