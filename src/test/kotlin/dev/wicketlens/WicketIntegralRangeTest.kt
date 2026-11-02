package dev.wicketlens

import com.google.gson.JsonParser
import com.intellij.codeInspection.InspectionManager
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressManager
import com.intellij.psi.*
import dev.wicketlens.analysis.WicketPageAnalyzer
import dev.wicketlens.editor.*
import dev.wicketlens.export.WicketContextExporter
import dev.wicketlens.inspection.WicketUnpairedIdInspection
import dev.wicketlens.mcp.*
import dev.wicketlens.model.*
import java.nio.file.Files
import java.nio.file.Path

class WicketIntegralRangeTest : WicketProjectPsiTestCase() {
    fun testOrderingAndEqualityPartitionOneInputWithoutImpossiblePaths() {
        val report = report("if (count < 0) add(new Label(\"title\")); if (count == 0) add(new Label(\"title\")); if (count > 0) add(new Label(\"title\"));")
        matched(report, "title")
        assertEquals(3, report.branchPathCount)
        assertEquals(3, report.node("title").idLocations().size)
    }

    fun testEqualityFirstStillLeavesBothNegativeAndPositiveAlternatives() {
        val report = report("if (count == 0) add(new Label(\"title\")); else { if (count < 0) add(new Label(\"title\")); if (count > 0) add(new Label(\"title\")); }")
        matched(report, "title")
        assertEquals(3, report.branchPathCount)
    }

    fun testStrongerLowerBoundSettlesAWeakerLowerBound() = matched(report(
        "if (count > 5) { if (count > 0) add(new Label(\"title\")); else removeAll(); } else add(new Label(\"title\"));"), "title")

    fun testStrongerUpperBoundSettlesAWeakerUpperBound() = matched(report(
        "if (count < 5) { if (count < 10) add(new Label(\"title\")); else removeAll(); } else add(new Label(\"title\"));"), "title")

    fun testFalseLessThanEstablishesItsInclusiveLowerBound() = matched(report(
        "if (!(count < 5)) { if (count >= 0) add(new Label(\"title\")); else removeAll(); } else add(new Label(\"title\"));"), "title")

    fun testFalseGreaterThanEstablishesItsInclusiveUpperBound() = matched(report(
        "if (!(count > 5)) { if (count <= 10) add(new Label(\"title\")); else removeAll(); } else add(new Label(\"title\"));"), "title")

    fun testEqualitySettlesBothOrderingDirections() = matched(report(
        "if (count == 10) { if (count > 5 && count < 20) add(new Label(\"title\")); else removeAll(); } else add(new Label(\"title\"));"), "title")

    fun testExcludingAnInteriorValueSettlesLaterEquality() = matched(report(
        "if (count != 5) { if (count == 5) removeAll(); add(new Label(\"title\")); } else add(new Label(\"title\"));"), "title")

    fun testExcludedCutEndpointRefinesTheRemainingBound() = matched(report("""
        if (count != 0) {
            if (count <= 0) { if (count < 0) add(new Label("title")); else removeAll(); }
            else add(new Label("title"));
        } else add(new Label("title"));
    """), "title")

    fun testMultipleExcludedValuesRemainDistinctWithoutExtraDecisions() = matched(report("""
        if (count != 5 && count != 10) {
            if (count == 5 || count == 10) removeAll(); add(new Label("title"));
        } else add(new Label("title"));
    """), "title")

    fun testTwoBoundsDescribeAClosedInterval() = matched(report("""
        if (count >= 10 && count <= 20) {
            if (count >= 0 && count < 100) add(new Label("title")); else removeAll();
        } else add(new Label("title"));
    """), "title")

    fun testNarrowWindowAndExclusionCanLeaveOneValue() = matched(report("""
        if (count >= 0 && count <= 1) {
            if (count != 0) { if (count == 1) add(new Label("title")); else removeAll(); }
            else add(new Label("title"));
        } else add(new Label("title"));
    """, parameters = "long count"), "title")

    fun testTinyCharDomainPartitionFitsTheThreeChoiceBudget() {
        val report = report("""
            if (count <= 2) {
                if (count == 0) add(new Label("title"));
                else if (count == 1) add(new Label("title"));
                else if (count == 2) add(new Label("title")); else removeAll();
            } else add(new Label("title"));
        """, parameters = "char count")
        matched(report, "title")
        assertEquals(4, report.branchPathCount)
    }

    fun testExcludingLongMinimumDoesNotOverflowTheEndpoint() = matched(report("""
        if (count != Long.MIN_VALUE) {
            if (count <= Long.MIN_VALUE) removeAll(); add(new Label("title"));
        } else add(new Label("title"));
    """, parameters = "long count"), "title")

    fun testExcludingLongMaximumDoesNotOverflowTheEndpoint() = matched(report("""
        if (count != Long.MAX_VALUE) {
            if (count >= Long.MAX_VALUE) removeAll(); add(new Label("title"));
        } else add(new Label("title"));
    """, parameters = "long count"), "title")

    fun testExcludingBothLongEndpointsLeavesTheExactInterior() = matched(report("""
        if (count != Long.MIN_VALUE && count != Long.MAX_VALUE) {
            if (count > Long.MIN_VALUE && count < Long.MAX_VALUE) add(new Label("title")); else removeAll();
        } else add(new Label("title"));
    """, parameters = "long count"), "title")

    fun testLongMaximumSingletonSatisfiesItsAdjacentOrderingCut() = matched(report("""
        if (count == Long.MAX_VALUE) {
            if (count > 9223372036854775806L) add(new Label("title")); else removeAll();
        } else add(new Label("title"));
    """, parameters = "long count"), "title")

    fun testNumericAliasAndImplicitWideningShareRefinements() = matched(report("""
        long alias = count;
        if (count > 10) { if (alias >= 1L) add(new Label("title")); else removeAll(); }
        else add(new Label("title"));
    """), "title")

    fun testReviewedAccessorUsesTheSameRefinedInput() = matched(report("""
        if (count > 10) { if (read(count) > 0) add(new Label("title")); else removeAll(); }
        else add(new Label("title"));
    """, members = "private long read(int value) { return value; }"), "title")

    fun testBooleanSnapshotCreatedBeforeRefinementKeepsItsMeaning() = matched(report("""
        boolean positive = count > 0;
        if (count > 10) { if (positive) add(new Label("title")); else removeAll(); }
        else add(new Label("title"));
    """), "title")

    fun testPrivateFinalStorageSharesRangeFactsAcrossPhases() = matched(report("""
        saved = count; if (count > 5) add(new Label("title"));
    """, members = """
        private final int saved;
        @Override protected void onInitialize() {
            super.onInitialize(); if (saved <= 5) { if (saved < 10) add(new Label("title")); }
        }
    """), "title")

    fun testContradictoryNestedRangeNeverExecutesItsUnsupportedBody() = matched(report("""
        if (count > 5) { if (count < 0) removeAll(); add(new Label("title")); }
        else add(new Label("title"));
    """), "title")

    fun testRefinedRangeCanSkipADeadShortCircuitOperand() = matched(report("""
        if (count > 5) { if (count > 0 || effect()) add(new Label("title")); }
        else add(new Label("title"));
    """, members = EFFECT), "title")

    fun testRefinedRangeCannotSkipAnEagerOperandAudit() = noUpgrade(report("""
        if (count > 5) { if ((count > 0) | effect()) add(new Label("title")); }
        else add(new Label("title"));
    """, members = EFFECT))

    fun testLaterRangeReasoningDoesNotWaiveTheInitialOperandAudit() = noUpgrade(report(
        "if (count > 5 && count < 0 && effect()) add(new Label(\"title\")); else add(new Label(\"title\"));", members = EFFECT))

    fun testSingletonRangeDoesNotSupplyAGuessedNumericId() {
        val report = report("if (count == 1) add(new Label(\"item\" + count)); else add(new Label(\"item\" + count));")
        noUpgrade(report)
        assertTrue(findings(report).none { it.code == DiagnosticCode.MATCHED })
    }

    fun testNonzeroRangeDoesNotWaiveTheExistingDivisionGuard() = noUpgrade(report(
        "if (count != 0) add(new Label(\"title\", 1 / count)); else add(new Label(\"title\"));"))

    fun testWeakerLowerBoundDoesNotProveAStrongerOne() {
        val report = report("if (count > 5) { if (count > 10) add(new Label(\"title\")); }")
        conditional(report, "title")
    }

    fun testWeakerUpperBoundDoesNotProveAStrongerOne() {
        val report = report("if (count < 5) { if (count < 0) add(new Label(\"title\")); }")
        conditional(report, "title")
    }

    fun testInequalityDoesNotChooseOneSideOfTheExcludedPoint() {
        val report = report("if (count != 5) { if (count > 5) add(new Label(\"title\")); }")
        conditional(report, "title")
    }

    fun testTrueOrDoesNotApplyBothOperandRangesToItsBody() {
        val report = report("if (count > 5 || flag) { if (count > 0) add(new Label(\"title\")); } else add(new Label(\"title\"));",
            parameters = "int count, boolean flag")
        conditional(report, "title")
    }

    fun testFalseAndDoesNotApplyBothOperandComplementsToItsBody() {
        val report = report("if (!(count > 5 && flag)) { if (count <= 5) add(new Label(\"title\")); } else add(new Label(\"title\"));",
            parameters = "int count, boolean flag")
        conditional(report, "title")
    }

    fun testBooleanEqualityOfOverlappingCutsPreservesItsThreeFeasibleRanges() {
        val report = report("""
            if ((count < 0) == (count <= 0)) {
                if (count != 0) add(new Label("title")); else removeAll();
            } else {
                if (count == 0) add(new Label("title")); else removeAll();
            }
        """)
        matched(report, "title")
        assertEquals(3, report.branchPathCount)
    }

    fun testArithmeticOverflowIsNotReplacedWithMathematicalRangeArithmetic() {
        val report = report("if (count > 0) { if (count + 1 > 0) add(new Label(\"title\")); } else add(new Label(\"title\"));")
        conditional(report, "title")
    }

    fun testDifferentInputsNeverShareTheirRefinedBounds() {
        val report = report("""
            if (count > 5) { if (other > 0) add(new Label("title")); }
            else add(new Label("title"));
        """, parameters = "int count, int other")
        conditional(report, "title")
    }

    fun testDistinctUnknownInputsRemainOutsideRelationalReasoning() = noUpgrade(report(
        "if (count < other) add(new Label(\"title\")); if (count >= other) add(new Label(\"title\"));", parameters = "int count, int other"))

    fun testMutableInputCannotKeepEarlierRangeFacts() = noUpgrade(report(
        "if (count > 5) add(new Label(\"title\")); count = 0; if (count <= 5) add(new Label(\"title\"));"))

    fun testBoxedInputCannotAcquirePrimitiveRangeFacts() = noUpgrade(report(
        "if (count > 5) { if (count > 0) add(new Label(\"title\")); } else add(new Label(\"title\"));", parameters = "Integer count"))

    fun testFloatingPointInputDoesNotAcquireIntegralRanges() = noUpgrade(report(
        "if (count > 5) { if (count > 0) add(new Label(\"title\")); } else add(new Label(\"title\"));", parameters = "double count"))

    fun testConditionalRangeChildKeepsUncertaintyScoped() {
        val report = report("add(new Label(\"title\")); if (count >= 10 && count <= 20) add(new Label(\"details\"));",
            "<span wicket:id='title'></span><span wicket:id='details'></span><span wicket:id='outside'></span>")
        matched(report, "title")
        conditional(report, "details")
        assertEquals(setOf("outside"), findings(report).filter { it.code == DiagnosticCode.MARKUP_ONLY }
            .map { f -> report.markup!!.elements.single { it.nodeId == f.markupNodeId }.wicketId }.toSet())
    }

    fun testRangePartitionUsesExistingQueuePlacement() {
        val report = report("""
            queue(new org.apache.wicket.markup.html.WebMarkupContainer("box"));
            if (count < 0) queue(new Label("title")); if (count == 0) queue(new Label("title")); if (count > 0) queue(new Label("title"));
        """, "<div wicket:id='box'><span wicket:id='title'></span></div>")
        matched(report, "box", "title")
        assertEquals(report.node("box").nodeId, report.node("title").parentNodeId)
        assertEquals(report.rootNodeId, report.node("title").queuedInNodeId)
    }

    fun testRangePartitionKeepsRepeaterChildrenInferred() {
        val report = report("""
            add(new org.apache.wicket.markup.html.list.ListView<String>("rows", java.util.List.of("A")) {
                @Override protected void populateItem(org.apache.wicket.markup.html.list.ListItem<String> item) {
                    if (count < 0) item.add(new Label("title")); if (count == 0) item.add(new Label("title")); if (count > 0) item.add(new Label("title"));
                }
            });
        """, "<div wicket:id='rows'><span wicket:id='title'></span></div>")
        matched(report, "rows", "title")
        assertEquals(Confidence.INFERRED, findings(report).single { it.javaNodeId == report.node("title").nodeId }.confidence)
    }

    fun testThreeIndependentInputsRetainTheirEightFeasibleChoices() {
        val body = (1..3).joinToString(" ") { "if (count$it > 0) add(new Label(\"id$it\")); if (count$it <= 0) add(new Label(\"id$it\"));" }
        val report = report(body, (1..3).joinToString("") { "<span wicket:id='id$it'></span>" }, parameters = "int count1, int count2, int count3")
        matched(report, "id1", "id2", "id3")
        assertEquals(8, report.branchPathCount)
    }

    fun testFourthNecessaryDecisionStillExhaustsTheExplorationBound() {
        val body = (0..3).joinToString(" ") { "if (count < $it) add(new Label(\"id$it\"));" }
        noUpgrade(report(body))
    }

    fun testRepeatedImpliedBoundsConsumeNoExtraDecisions() {
        val checks = (1..5).joinToString(" ") { "if (count > $it) {} else removeAll();" }
        val report = report("if (count > 10) { $checks add(new Label(\"title\")); } else add(new Label(\"title\"));")
        matched(report, "title")
        assertEquals(2, report.branchPathCount)
    }

    fun testMergedRangeOriginsNavigateAndRetainEditingGuards() {
        val file = page(PARTITION)
        val report = analyze(file)
        val origins = WicketIdPsi.javaIdElements(project, report.node("title"))
        assertEquals(3, origins.size)
        for (origin in origins) {
            assertNotNull(WicketIdPsi.pair(origin))
            assertNull(WicketIdRenameTarget.at(file, origin.textOffset + 1))
            assertTrue(WicketIdCompletionContributor().candidates(origin).isEmpty())
        }
        val holder = ProblemsHolder(InspectionManager.getInstance(project), file, false)
        WicketUnpairedIdInspection().buildVisitor(holder, false).visitFile(file)
        assertTrue(holder.results.isEmpty())
    }

    fun testMcpUsesTheSameRangeGraphWithoutExportingIntervalsOrValues() {
        val file = page("$PARTITION if (count >= 10) add(new Label(\"details\"));")
        analyze(file, "<span wicket:id='title'></span><span wicket:id='details'></span>")
        val before = file.text
        val query = WicketPageAnalysisRequest()
        val snapshot = query.capture(project, "example.ExamplePage", module.name)
        val structure = JsonParser.parseString(query.render(snapshot)).asJsonObject
        assertEquals(JsonParser.parseString(WicketContextExporter().export(snapshot.report)), structure["analysis"])
        val context = JsonParser.parseString(query.render(snapshot, WicketMcpReport.CONTEXT, componentPath = "title")).asJsonObject
        assertEquals("MATCHED", context["context"].asJsonObject["component"].asJsonObject["status"].asString)
        assertEquals(3, context["context"].asJsonObject["component"].asJsonObject["idLocations"].asJsonArray.size())
        val optional = JsonParser.parseString(query.render(snapshot, WicketMcpReport.CONTEXT, componentPath = "details")).asJsonObject["context"].asJsonObject
        assertTrue(optional["component"].asJsonObject["conditional"].asBoolean)
        assertTrue(optional["markupElements"].asJsonArray.isEmpty)
        val validation = JsonParser.parseString(query.render(snapshot, WicketMcpReport.VALIDATION)).asJsonObject
        assertEquals(structure["analysis"].asJsonObject["matching"].asJsonObject["findings"], validation["validation"].asJsonObject["findings"])
        assertEquals(module.name, context["target"].asJsonObject["moduleName"].asString)
        assertFalse(structure.toString().contains("IntegralRange"))
        assertFalse(structure.toString().contains("integralRanges"))
        assertEquals(before, file.text)
        assertEquals(query.render(snapshot), query.render(query.capture(project, "example.ExamplePage", module.name)))
    }

    fun testExternalRangeBoundaryEditInvalidatesAndChangesTheProof() {
        val constants = source("Limits", "public final class Limits { public static final int HIGH = 10; }")
        val file = page("if (count > 5) { if (count < 6) removeAll(); add(new Label(\"title\")); } else { if (count < Limits.HIGH) add(new Label(\"title\")); }")
        matched(analyze(file), "title")
        val query = WicketPageAnalysisRequest()
        val old = query.capture(project, "example.ExamplePage")
        WriteCommandAction.runWriteCommandAction(project) {
            PsiDocumentManager.getInstance(project).getDocument(constants)!!.setText(constants.text.replace("HIGH = 10", "HIGH = 5"))
        }
        try { query.requireCurrent(project, old); fail("Expected stale snapshot") }
        catch (failure: PageAnalysisFailure) { assertEquals("STALE_SNAPSHOT", failure.code) }
        PsiDocumentManager.getInstance(project).commitAllDocuments()
        val fresh = query.capture(project, "example.ExamplePage")
        conditional(fresh.report, "title")
    }

    fun testMcpUtf8LimitStillBoundsMergedRangeOrigins() {
        val file = page(PARTITION)
        analyze(file)
        val query = WicketPageAnalysisRequest()
        val snapshot = query.capture(project, "example.ExamplePage")
        val oversized = snapshot.copy(report = snapshot.report.copy(nodes = snapshot.report.nodes.map {
            if (it.wicketId == "title") it.copy(alternativeJavaLocations = listOf(SourceLocation("界".repeat(180_000), 0))) else it
        }))
        try { query.render(oversized); fail("Expected byte limit") }
        catch (failure: PageAnalysisFailure) { assertEquals("RESPONSE_TOO_LARGE", failure.code) }
    }

    fun testCancellationDoesNotPublishAPartialRangeProof() {
        val file = page(PARTITION)
        val indicator = EmptyProgressIndicator()
        assertThrows(ProcessCanceledException::class.java) {
            ProgressManager.getInstance().runProcess(Runnable {
                indicator.cancel(); WicketPageAnalyzer().analyze(file.classes.single())
            }, indicator)
        }
    }

    fun testPortableDemoHasAThreeWayCommonBindingAndOptionalDetails() {
        val root = Path.of(System.getProperty("wicketlens.test.demo"))
        val file = myFixture.addFileToProject("src/main/java/example/IntegralRangePage.java",
            Files.readString(root.resolve("main/java/example/IntegralRangePage.java"))) as PsiJavaFile
        companion("IntegralRangePage", Files.readString(root.resolve("main/resources/example/IntegralRangePage.html")))
        val report = WicketPageAnalyzer().analyze(file.classes.single())!!
        matched(report, "title")
        assertTrue(report.node("title").computedId)
        conditional(report, "details")
        assertEquals(3, report.node("title").idLocations().size)
        assertTrue(findings(report).none { it.code in setOf(DiagnosticCode.JAVA_ONLY, DiagnosticCode.MARKUP_ONLY) })
    }

    private fun source(name: String, declaration: String) = myFixture.addFileToProject("src/main/java/example/$name.java", "package example; $declaration")
    private fun page(body: String, members: String = "", parameters: String = "int count") = source("ExamplePage", """
        import org.apache.wicket.markup.html.basic.Label;
        public class ExamplePage extends org.apache.wicket.markup.html.WebPage {
            public ExamplePage($parameters) { $body }
            $members
        }
    """.trimIndent()) as PsiJavaFile
    private fun analyze(file: PsiJavaFile, html: String = "<span wicket:id='title'></span>"): PageReport {
        companion(file.classes.single().name!!, html)
        return WicketPageAnalyzer().analyze(file.classes.single())!!
    }
    private fun report(body: String, html: String = "<span wicket:id='title'></span>", members: String = "", parameters: String = "int count") =
        analyze(page(body, members, parameters), html)
    private fun PageReport.node(id: String) = children().single { it.wicketId == id }
    private fun findings(report: PageReport) = report.matching!!.findings
    private fun matched(report: PageReport, vararg ids: String) = assertEquals(report.matching.toString(), ids.toSet(),
        findings(report).filter { it.code == DiagnosticCode.MATCHED }.map { f -> report.nodes.single { it.nodeId == f.javaNodeId }.wicketId }.toSet())
    private fun conditional(report: PageReport, id: String) {
        val node = report.node(id)
        assertTrue(node.branchMerged && node.conditional && node.confidence == Confidence.UNRESOLVED && !node.idRenameSafe)
        assertEquals(DiagnosticCode.UNRESOLVED_BRANCH, findings(report).single { it.javaNodeId == node.nodeId }.code)
    }
    private fun noUpgrade(report: PageReport) = assertFalse(report.children().any { it.branchMerged })
    companion object {
        private const val PARTITION = "if (count < 0) add(new Label(\"title\")); if (count == 0) add(new Label(\"title\")); if (count > 0) add(new Label(\"title\"));"
        private const val EFFECT = "private boolean effect() { removeAll(); return true; }"
    }
}
