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

class WicketIntegralComparisonTest : WicketProjectPsiTestCase() {
    fun testRepeatedStableComparisonAndNegationEstablishOneBinding() {
        val report = report("if (count > 0) add(new Label(\"title\")); if (!(count > 0)) add(new Label(\"title\"));")
        matched(report, "title")
        assertEquals(2, report.branchPathCount)
        assertEquals(2, report.node("title").idLocations().size)
    }

    fun testAllSixOperatorsShareTheirExactComplement() {
        val pairs = listOf("<" to ">=", "<=" to ">", ">" to "<=", ">=" to "<", "==" to "!=", "!=" to "==")
        pairs.forEachIndexed { index, (first, second) ->
            matched(report("if (count $first 7) add(new Label(\"title\")); if (count $second 7) add(new Label(\"title\"));", name = "OperatorCase$index"), "title")
        }
    }

    fun testReversedConstantComparisonsShareTheSameInput() = matched(report(
        "if (0 < count) add(new Label(\"title\")); if (count <= 0) add(new Label(\"title\"));"), "title")

    fun testAllReversedOperatorsRetainTheirMeaning() {
        val pairs = listOf("<" to "<=", "<=" to "<", ">" to ">=", ">=" to ">", "==" to "!=", "!=" to "==")
        pairs.forEachIndexed { index, (first, second) ->
            matched(report("if (5 $first count) add(new Label(\"title\")); if (count $second 5) add(new Label(\"title\"));", name = "ReversedCase$index"), "title")
        }
    }

    fun testStrictAndAdjacentInclusiveThresholdsAreEquivalent() = matched(report(
        "if (count > 0) add(new Label(\"title\")); if (!(count >= 1)) add(new Label(\"title\"));"), "title")

    fun testLessThanAndAdjacentInclusiveThresholdsAreEquivalent() = matched(report(
        "if (count < 1) add(new Label(\"title\")); if (!(count <= 0)) add(new Label(\"title\"));"), "title")

    fun testEquivalentKnownConstantExpressionsShareThresholds() = matched(report(
        "if (count > (1 << 2)) add(new Label(\"title\")); if (count < 5L) add(new Label(\"title\"));"), "title")

    fun testSourceConstantThresholdParticipatesInCanonicalComparisons() {
        source("Limits", "public final class Limits { public static final int START = 7; }")
        matched(report("if (count >= Limits.START) add(new Label(\"title\")); if (count < 7L) add(new Label(\"title\"));"), "title")
    }

    fun testLongComparisonDoesNotRoundThroughDouble() = matched(report(
        "if (count > 9007199254740992L) add(new Label(\"title\")); if (count < 9007199254740993L) add(new Label(\"title\"));",
        parameters = "long count"), "title")

    fun testLongMaximumInclusiveAndStrictBoundsDoNotOverflow() {
        val report = report("if (count <= Long.MAX_VALUE) add(new Label(\"title\")); if (count > Long.MAX_VALUE) removeAll();", parameters = "long count")
        matched(report, "title")
        assertEquals(1, report.branchPathCount)
    }

    fun testLongMinimumBoundsRemainExact() = matched(report(
        "if (count >= Long.MIN_VALUE) add(new Label(\"title\")); if (count < Long.MIN_VALUE) removeAll();", parameters = "long count"), "title")

    fun testLongMaximumAdjacentCutRemainsUnknownButCorrelated() = matched(report(
        "if (count > 9223372036854775806L) add(new Label(\"title\")); if (count < Long.MAX_VALUE) add(new Label(\"title\"));", parameters = "long count"), "title")

    fun testLongMinimumAdjacentCutDoesNotOverflow() = matched(report(
        "if (count <= Long.MIN_VALUE) add(new Label(\"title\")); if (count > Long.MIN_VALUE) add(new Label(\"title\"));", parameters = "long count"), "title")

    fun testStableSelfComparisonsHaveTheirExactPrimitiveMeaning() = matched(report(
        "if (count == count) add(new Label(\"title\")); if (count < count) removeAll();"), "title")

    fun testIntBoundsComparedWithLongConstantsDoNotWrap() = matched(report(
        "if (count < 2147483648L) add(new Label(\"title\")); if (count >= 2147483648L) removeAll();"), "title")

    fun testPrimitiveNarrowBoundsAreKnownWithoutGuessingAValue() {
        val cases = listOf("byte" to "count >= -128 && count <= 127", "short" to "count >= -32768 && count <= 32767",
            "char" to "count >= 0 && count < 65536L")
        cases.forEachIndexed { index, (type, condition) ->
            val report = report("if ($condition) add(new Label(\"title\")); else removeAll();", parameters = "$type count", name = "BoundsCase$index")
            matched(report, "title")
            assertEquals(1, report.branchPathCount)
        }
    }

    fun testOutOfDomainEqualityIsFalseForEveryPrimitiveValue() = matched(report(
        "if (count != 128L) add(new Label(\"title\")); if (count == 128L) removeAll();", parameters = "byte count"), "title")

    fun testCharThresholdAndIntegralThresholdAreEquivalent() = matched(report(
        "if (count < 'A') add(new Label(\"title\")); if (count >= 65) add(new Label(\"title\"));", parameters = "char count"), "title")

    fun testUnchangedNumericAliasRetainsInputIdentity() = matched(report(
        "int alias = count; if (alias > 0) add(new Label(\"title\")); if (count <= 0) add(new Label(\"title\"));"), "title")

    fun testImplicitWideningRetainsIdentityAndOriginalBounds() = matched(report("""
        long widened = count;
        if (widened > 0L) add(new Label("title")); if (count <= 0) add(new Label("title"));
        if (widened > Integer.MAX_VALUE) removeAll();
    """), "title")

    fun testNarrowingCastDoesNotKeepTheOriginalIdentity() = noUpgrade(report("""
        int narrowed = (int) count;
        if (narrowed > 0) add(new Label("title")); if (count <= 0) add(new Label("title"));
    """, parameters = "long count"))

    fun testOnceComputedNumericSnapshotHasItsOwnStableIdentity() = matched(report("""
        int snapshot = count + 1;
        if (snapshot > 0) add(new Label("title")); if (snapshot <= 0) add(new Label("title"));
    """), "title")

    fun testSeparatelyRecomputedArithmeticIsNotCorrelated() = noUpgrade(report(
        "if (count + 1 > 0) add(new Label(\"title\")); if (count + 1 <= 0) add(new Label(\"title\"));"))

    fun testIndependentArithmeticSnapshotsDoNotShareIdentity() = noUpgrade(report("""
        int first = count + 1; int second = count + 1;
        if (first > 0) add(new Label("title")); if (second <= 0) add(new Label("title"));
    """))

    fun testReviewedNumericAccessorPreservesTheOriginalInput() = matched(report(
        "if (read(count) > 0) add(new Label(\"title\")); if (count <= 0) add(new Label(\"title\"));",
        members = "private long read(int value) { return value; }"), "title")

    fun testReviewedPredicateAccessorPreservesTheCanonicalComparison() = matched(report(
        "if (positive(count)) add(new Label(\"title\")); if (count < 1) add(new Label(\"title\"));",
        members = "private boolean positive(int value) { return value > 0; }"), "title")

    fun testPrivateFinalNumericStorageRetainsInputIdentityAcrossPhases() = matched(report(
        "saved = count; if (saved > 0) add(new Label(\"title\"));", members = """
            private final int saved;
            @Override protected void onInitialize() { super.onInitialize(); if (saved <= 0) add(new Label("title")); }
        """), "title")

    fun testImmutableProviderCanRetainTheNumericInput() {
        source("Limits", """
            public final class Limits {
                private final int value;
                public Limits(int value) { this.value = value; }
                public int count() { return value; }
            }
        """)
        matched(report("Limits limits = new Limits(count); if (limits.count() > 0) add(new Label(\"title\")); if (count <= 0) add(new Label(\"title\"));"), "title")
    }

    fun testMixedBooleanGroupAndEqualityReuseComparisonFacts() = matched(report("""
        if ((count > 0 && flag) == true) add(new Label("title"));
        if (count <= 0 || !flag) add(new Label("title"));
    """, parameters = "int count, boolean flag"), "title")

    fun testComparisonTernaryRetainsMixedIdOriginKinds() {
        val report = report("if (count > 0) add(new Label(\"ti\" + \"tle\")); if (count <= 0) add(count < 1 ? new Label(\"title\") : new Label(\"unreachable\"));")
        matched(report, "title")
        assertTrue(report.node("title").computedId)
        assertEquals(2, report.node("title").idLocations().size)
    }

    fun testQueuePlacementUsesCanonicalComparisonPaths() {
        val report = report("""
            queue(new org.apache.wicket.markup.html.WebMarkupContainer("box"));
            if (count > 0) queue(new Label("title")); if (count <= 0) queue(new Label("title"));
        """, "<div wicket:id='box'><span wicket:id='title'></span></div>")
        matched(report, "box", "title")
        assertEquals(report.node("box").nodeId, report.node("title").parentNodeId)
    }

    fun testRepeaterComparisonPathsRetainInferredTemplateConfidence() {
        val report = report("""
            add(new org.apache.wicket.markup.html.list.ListView<String>("rows", java.util.List.of("A")) {
                @Override protected void populateItem(org.apache.wicket.markup.html.list.ListItem<String> item) {
                    if (count > 0) item.add(new Label("title")); if (count <= 0) item.add(new Label("title"));
                }
            });
        """, "<div wicket:id='rows'><span wicket:id='title'></span></div>")
        matched(report, "rows", "title")
        assertEquals(Confidence.INFERRED, findings(report).single { it.javaNodeId == report.node("title").nodeId }.confidence)
    }

    fun testOptionalComparisonStructureKeepsAbsenceUncertaintyScoped() {
        val report = report("if (count > 0) add(new Label(\"title\")); if (count <= 0) add(new Label(\"title\")); if (count >= 1) add(new Label(\"details\"));",
            "<span wicket:id='title'></span><span wicket:id='details'></span><span wicket:id='outside'></span>")
        matched(report, "title")
        assertTrue(report.node("details").conditional)
        assertEquals(setOf("outside"), findings(report).filter { it.code == DiagnosticCode.MARKUP_ONLY }
            .map { f -> report.markup!!.elements.single { it.nodeId == f.markupNodeId }.wicketId }.toSet())
    }

    fun testDifferentParametersNeverShareFactsBecauseTheyHaveTheSameType() = noUpgrade(report(
        "if (count > 0) add(new Label(\"title\")); if (other <= 0) add(new Label(\"title\"));", parameters = "int count, int other"))

    fun testIndependentHelperInvocationsDoNotShareUnknownArithmeticInputs() = noUpgrade(report(
        "attach(count + 1); attach(count + 2);", members = "private void attach(int value) { if (value > 0) add(new Label(\"title\")); }"))

    fun testTwoUnknownInputsRemainOutsideThresholdCorrelation() = noUpgrade(report(
        "if (count < other) add(new Label(\"title\")); if (count >= other) add(new Label(\"title\"));", parameters = "int count, int other"))

    fun testOrderingAndEqualityPartitionOneStableInput() = matched(report("""
        if (count < 0) add(new Label("title")); if (count == 0) add(new Label("title")); if (count > 0) add(new Label("title"));
    """), "title")

    fun testPredicateFactsNeverSupplyAGuessedComputedIdValue() {
        val report = report("if (count == 1) add(new Label(\"item\" + count)); else add(new Label(\"item\" + count));")
        noUpgrade(report)
        assertTrue(findings(report).none { it.code == DiagnosticCode.MATCHED })
    }

    fun testAssignmentsToANumericInputStillRejectSetup() = noUpgrade(report(
        "if (count > 0) add(new Label(\"title\")); count = -count; if (count <= 0) add(new Label(\"title\"));"))

    fun testMutableNumericFieldsCannotAcquireStableIdentity() = noUpgrade(report(
        "if (mutable > 0) add(new Label(\"title\")); if (mutable <= 0) add(new Label(\"title\"));", members = "private int mutable;"))

    fun testBoxedNumericInputsRemainOutsideThePrimitiveContract() = noUpgrade(report(
        "if (count > 0) add(new Label(\"title\")); if (count <= 0) add(new Label(\"title\"));", parameters = "Integer count"))

    fun testFloatingPointComparisonsRemainOutsideTheIntegralContract() = noUpgrade(report(
        "if (count > 0) add(new Label(\"title\")); if (count <= 0) add(new Label(\"title\"));", parameters = "double count"))

    fun testAnEffectfulThresholdCannotBecomeAComparisonFact() = noUpgrade(report(
        "if (count > limit()) add(new Label(\"title\")); else add(new Label(\"title\"));", members = "private int limit() { removeAll(); return 0; }"))

    fun testKnownComparisonFactStillSkipsADeadShortCircuitOperand() = matched(report("""
        if (count > 0) { if (1 <= count || effect()) add(new Label("title")); }
        else if (count <= 0) add(new Label("title"));
    """, members = "private boolean effect() { removeAll(); return true; }"), "title")

    fun testKnownComparisonCannotSkipAnEagerEffectfulOperand() = noUpgrade(report(
        "if ((count > 0) | effect()) add(new Label(\"title\")); else add(new Label(\"title\"));",
        members = "private boolean effect() { removeAll(); return true; }"))

    fun testPossibleDivisionRiskCannotHideBehindAComparisonResult() = noUpgrade(report(
        "if (count > (1 / divisor)) add(new Label(\"title\")); else add(new Label(\"title\"));", parameters = "int count, int divisor"))

    fun testOrderedThresholdsRetainOnlyTheirFourFeasibleRanges() {
        val body = (0..2).joinToString(" ") { "if (count < $it) add(new Label(\"id$it\"));" }
        val report = report(body, (0..2).joinToString("") { "<span wicket:id='id$it'></span>" })
        assertEquals(4, report.branchPathCount)
        assertTrue(report.children().all { it.conditional })
    }

    fun testFourthIndependentThresholdStillExhaustsTheExplorationBudget() {
        val body = (0..3).joinToString(" ") { "if (count < $it) add(new Label(\"id$it\"));" }
        noUpgrade(report(body))
    }

    fun testEquivalentComparisonChecksReuseOneDecisionRepeatedly() {
        val body = (1..4).joinToString(" ") { "if (count > 0) add(new Label(\"id$it\")); if (count < 1) add(new Label(\"id$it\"));" }
        val report = report(body, (1..4).joinToString("") { "<span wicket:id='id$it'></span>" })
        matched(report, "id1", "id2", "id3", "id4")
        assertEquals(2, report.branchPathCount)
    }

    fun testMergedComparisonOriginsNavigateAndRetainEditingGuards() {
        val file = page("if (count > 0) add(new Label(\"title\")); if (count <= 0) add(new Label(\"title\"));")
        val report = analyze(file)
        val origins = WicketIdPsi.javaIdElements(project, report.node("title"))
        assertEquals(2, origins.size)
        for (origin in origins) {
            assertNotNull(WicketIdPsi.pair(origin))
            assertNull(WicketIdRenameTarget.at(file, origin.textOffset + 1))
            assertTrue(WicketIdCompletionContributor().candidates(origin).isEmpty())
        }
        val holder = ProblemsHolder(InspectionManager.getInstance(project), file, false)
        WicketUnpairedIdInspection().buildVisitor(holder, false).visitFile(file)
        assertTrue(holder.results.isEmpty())
    }

    fun testMcpProjectionsReuseComparisonFindingsWithoutExportingNumericState() {
        val file = page("if (count > 0) add(new Label(\"title\")); if (count <= 0) add(new Label(\"title\")); if (count >= 1) add(new Label(\"details\"));")
        analyze(file, "<span wicket:id='title'></span><span wicket:id='details'></span>")
        val before = file.text
        val query = WicketPageAnalysisRequest()
        val snapshot = query.capture(project, "example.ExamplePage", module.name)
        val structure = JsonParser.parseString(query.render(snapshot)).asJsonObject
        assertEquals(JsonParser.parseString(WicketContextExporter().export(snapshot.report)), structure["analysis"])
        val context = JsonParser.parseString(query.render(snapshot, WicketMcpReport.CONTEXT, componentPath = "title")).asJsonObject
        assertEquals("MATCHED", context["context"].asJsonObject["component"].asJsonObject["status"].asString)
        val optional = JsonParser.parseString(query.render(snapshot, WicketMcpReport.CONTEXT, componentPath = "details")).asJsonObject["context"].asJsonObject
        assertTrue(optional["component"].asJsonObject["conditional"].asBoolean)
        assertTrue(optional["markupElements"].asJsonArray.isEmpty)
        val validation = JsonParser.parseString(query.render(snapshot, WicketMcpReport.VALIDATION)).asJsonObject
        assertEquals(structure["analysis"].asJsonObject["matching"].asJsonObject["findings"], validation["validation"].asJsonObject["findings"])
        assertEquals(module.name, context["target"].asJsonObject["moduleName"].asString)
        assertFalse(structure.toString().contains("IntegralSymbol"))
        assertFalse(structure.toString().contains("comparisonSymbols"))
        assertEquals(before, file.text)
        assertEquals(query.render(snapshot), query.render(query.capture(project, "example.ExamplePage", module.name)))
    }

    fun testExternalThresholdEditInvalidatesTheSnapshotAndItsFacts() {
        val constants = source("Limits", "public final class Limits { public static final int START = 1; }")
        val file = page("if (count >= Limits.START) add(new Label(\"title\")); if (count < 1) add(new Label(\"title\"));")
        matched(analyze(file), "title")
        val query = WicketPageAnalysisRequest()
        val old = query.capture(project, "example.ExamplePage")
        WriteCommandAction.runWriteCommandAction(project) {
            PsiDocumentManager.getInstance(project).getDocument(constants)!!.setText(constants.text.replace("START = 1", "START = 0"))
        }
        try { query.requireCurrent(project, old); fail("Expected stale snapshot") }
        catch (failure: PageAnalysisFailure) { assertEquals("STALE_SNAPSHOT", failure.code) }
        PsiDocumentManager.getInstance(project).commitAllDocuments()
        noUpgrade(query.capture(project, "example.ExamplePage").report)
    }

    fun testMcpUtf8LimitStillBoundsMergedComparisonOrigins() {
        val file = page("if (count > 0) add(new Label(\"title\")); if (count <= 0) add(new Label(\"title\"));")
        analyze(file)
        val query = WicketPageAnalysisRequest()
        val snapshot = query.capture(project, "example.ExamplePage")
        val oversized = snapshot.copy(report = snapshot.report.copy(nodes = snapshot.report.nodes.map {
            if (it.wicketId == "title") it.copy(alternativeJavaLocations = listOf(SourceLocation("界".repeat(180_000), 0))) else it
        }))
        try { query.render(oversized); fail("Expected byte limit") }
        catch (failure: PageAnalysisFailure) { assertEquals("RESPONSE_TOO_LARGE", failure.code) }
    }

    fun testCancellationNeverPublishesAPartialComparisonProof() {
        val file = page("if (count > 0) add(new Label(\"title\")); if (count <= 0) add(new Label(\"title\"));")
        val indicator = EmptyProgressIndicator()
        assertThrows(ProcessCanceledException::class.java) {
            ProgressManager.getInstance().runProcess(Runnable {
                indicator.cancel(); WicketPageAnalyzer().analyze(file.classes.single())
            }, indicator)
        }
    }

    fun testPortableDemoHasACommonBindingAndConditionalDetails() {
        val root = Path.of(System.getProperty("wicketlens.test.demo"))
        val file = myFixture.addFileToProject("src/main/java/example/IntegralComparisonPage.java",
            Files.readString(root.resolve("main/java/example/IntegralComparisonPage.java"))) as PsiJavaFile
        companion("IntegralComparisonPage", Files.readString(root.resolve("main/resources/example/IntegralComparisonPage.html")))
        val report = WicketPageAnalyzer().analyze(file.classes.single())!!
        matched(report, "title")
        assertTrue(report.node("title").computedId)
        assertTrue(report.node("details").conditional)
        assertTrue(findings(report).none { it.code in setOf(DiagnosticCode.JAVA_ONLY, DiagnosticCode.MARKUP_ONLY) })
    }

    private fun source(name: String, declaration: String) = myFixture.addFileToProject("src/main/java/example/$name.java", "package example; $declaration")
    private fun page(body: String, members: String = "", parameters: String = "int count", name: String = "ExamplePage") = source(name, """
        import org.apache.wicket.markup.html.basic.Label;
        public class $name extends org.apache.wicket.markup.html.WebPage {
            public $name($parameters) { $body }
            $members
        }
    """.trimIndent()) as PsiJavaFile
    private fun analyze(file: PsiJavaFile, html: String = "<span wicket:id='title'></span>"): PageReport {
        companion(file.classes.single().name!!, html)
        return WicketPageAnalyzer().analyze(file.classes.single())!!
    }
    private fun report(body: String, html: String = "<span wicket:id='title'></span>", members: String = "", parameters: String = "int count", name: String = "ExamplePage") =
        analyze(page(body, members, parameters, name), html)
    private fun PageReport.node(id: String) = children().single { it.wicketId == id }
    private fun findings(report: PageReport) = report.matching!!.findings
    private fun matched(report: PageReport, vararg ids: String) = assertEquals(report.matching.toString(), ids.toSet(),
        findings(report).filter { it.code == DiagnosticCode.MATCHED }.map { f -> report.nodes.single { it.nodeId == f.javaNodeId }.wicketId }.toSet())
    private fun noUpgrade(report: PageReport) = assertFalse(report.children().any { it.branchMerged })
}
