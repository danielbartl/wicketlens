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

class WicketBooleanOperatorsTest : WicketProjectPsiTestCase() {
    fun testPrimitiveEqualityAndInequalityAreComplementary() {
        val report = report("if (a == b) add(new Label(\"title\")); if (a != b) add(new Label(\"title\"));")
        matched(report, "title")
        assertEquals(4, report.branchPathCount)
        assertEquals(2, report.node("title").idLocations().size)
    }

    fun testEqualityToTruePreservesTheFlagRelationship() = matched(report(
        "if (a == true) add(new Label(\"title\")); if (!a) add(new Label(\"title\"));"), "title")

    fun testEqualityToFalseAndInequalityToFalseAreComplementary() = matched(report(
        "if (false == a) add(new Label(\"title\")); if (a != false) add(new Label(\"title\"));"), "title")

    fun testEagerAndSharesFactsWithItsDeMorganComplement() = matched(report(
        "if (a & b) add(new Label(\"title\")); if (!a || !b) add(new Label(\"title\"));"), "title")

    fun testEagerOrSharesFactsWithItsDeMorganComplement() = matched(report(
        "if (a | b) add(new Label(\"title\")); if (!a && !b) add(new Label(\"title\"));"), "title")

    fun testEagerAndAndShortCircuitAndShareOnlyTheirReviewedValues() = matched(report(
        "if (a & b) add(new Label(\"title\")); if (!(a && b)) add(new Label(\"title\"));"), "title")

    fun testXorAndEqualityAreComplementary() = matched(report(
        "if (a ^ b) add(new Label(\"title\")); if (a == b) add(new Label(\"title\"));"), "title")

    fun testXorWithTruePreservesNegationOfTheOriginalFlag() = matched(report(
        "if (a ^ true) add(new Label(\"title\")); if (a) add(new Label(\"title\"));"), "title")

    fun testThreeInputXorRetainsEightConsistentPaths() {
        val report = report("if (a ^ b ^ c) add(new Label(\"title\")); if (!(a != b != c)) add(new Label(\"title\"));",
            parameters = "boolean a, boolean b, boolean c")
        matched(report, "title")
        assertEquals(8, report.branchPathCount)
    }

    fun testThreeInputEqualityPreservesLeftAssociativeParity() = matched(report(
        "if (a == b == c) add(new Label(\"title\")); if (!(a ^ b ^ c)) add(new Label(\"title\"));",
        parameters = "boolean a, boolean b, boolean c"), "title")

    fun testFourEqualityOperandsFlipParityThreeTimes() = matched(report(
        "if (a == b == a == b) add(new Label(\"title\")); else removeAll();"), "title")

    fun testMixedEqualityTokensKeepTheirParsedGrouping() = matched(report(
        "if ((a == b) != c) add(new Label(\"title\")); if (a ^ b ^ c) add(new Label(\"title\"));",
        parameters = "boolean a, boolean b, boolean c"), "title")

    fun testUnparenthesizedMixedEqualityThenInequalityKeepGrouping() = matched(report(
        "if (a == b != c) add(new Label(\"title\")); if (a ^ b ^ c) add(new Label(\"title\"));",
        parameters = "boolean a, boolean b, boolean c"), "title")

    fun testUnparenthesizedMixedInequalityThenEqualityKeepGrouping() = matched(report(
        "if (a != b == c) add(new Label(\"title\")); if (a ^ b ^ c) add(new Label(\"title\"));",
        parameters = "boolean a, boolean b, boolean c"), "title")

    fun testSelfEqualityDoesNotInventDifferentValuesForOneStableInput() = matched(report(
        "if (a == a) add(new Label(\"title\")); else removeAll(); if (a ^ a) removeAll();"), "title")

    fun testKnownTruthTablesAgreeForAllFiveOperators() {
        val operators = listOf("&", "|", "^", "==", "!=")
        var index = 0
        for (a in listOf(false, true)) for (b in listOf(false, true)) for (operator in operators) {
            val expected = when (operator) {
                "&" -> a && b
                "|" -> a || b
                "^", "!=" -> a != b
                else -> a == b
            }
            val body = "if (($a $operator $b) == $expected) add(new Label(\"title\")); else removeAll();"
            matched(report(body, name = "TruthCase${index++}"), "title")
        }
    }

    fun testStoredEqualityAndNegatedAliasPreserveOriginalInputs() = matched(report("""
        boolean same = a == b; boolean different = !same;
        if (different) add(new Label("title")); if (!(a ^ b)) add(new Label("title"));
    """), "title")

    fun testReviewedAccessorCanReturnEquality() = matched(report("""
        if (same(a, b)) add(new Label("title")); if (a ^ b) add(new Label("title"));
    """, members = "private boolean same(boolean first, boolean second) { return first == second; }"), "title")

    fun testPrivateFinalStorageRetainsParityAcrossInitialization() = matched(report("""
        different = a ^ b; if (different) add(new Label("title"));
    """, members = """
        private final boolean different;
        @Override protected void onInitialize() { super.onInitialize(); if (!different) add(new Label("title")); }
    """), "title")

    fun testImmutableProviderCanStoreAnEagerBooleanValue() {
        source("Flags", """
            public final class Flags {
                private final boolean value;
                public Flags(boolean value) { this.value = value; }
                public boolean enabled() { return value; }
            }
        """)
        matched(report("Flags flags = new Flags(a & b); if (flags.enabled()) add(new Label(\"title\")); if (!a || !b) add(new Label(\"title\"));"), "title")
    }

    fun testEqualityControlsTernariesWithMixedLiteralAndComputedOrigins() {
        val report = report("if (a == b) add(new Label(\"ti\" + \"tle\")); if (a != b) add((a ^ b) ? new Label(\"title\") : new Label(\"unreachable\"));")
        matched(report, "title")
        assertTrue(report.node("title").computedId)
        assertEquals(2, report.node("title").idLocations().size)
    }

    fun testEqualityDoesNotForceBothInputsTrue() {
        val report = report("if (a == b) { if (a & b) add(new Label(\"title\")); }")
        assertTrue(report.node("title").conditional)
        assertTrue(findings(report).none { it.code == DiagnosticCode.MATCHED })
    }

    fun testOptionalEqualityChildKeepsUncertaintyScoped() {
        val report = report("if (a == b) add(new Label(\"title\")); if (a != b) add(new Label(\"title\")); if (a | b) add(new Label(\"details\"));",
            "<span wicket:id='title'></span><span wicket:id='details'></span><span wicket:id='outside'></span>")
        matched(report, "title")
        assertTrue(report.node("details").conditional)
        assertEquals(setOf("outside"), findings(report).filter { it.code == DiagnosticCode.MARKUP_ONLY }
            .map { f -> report.markup!!.elements.single { it.nodeId == f.markupNodeId }.wicketId }.toSet())
    }

    fun testQueuePlacementUsesTheSameEqualityPaths() {
        val report = report("""
            queue(new org.apache.wicket.markup.html.WebMarkupContainer("box"));
            if (a == b) queue(new Label("title")); if (a ^ b) queue(new Label("title"));
        """, "<div wicket:id='box'><span wicket:id='title'></span></div>")
        matched(report, "box", "title")
        assertEquals(report.node("box").nodeId, report.node("title").parentNodeId)
        assertEquals(report.rootNodeId, report.node("title").queuedInNodeId)
    }

    fun testRepeaterEqualityPathsRetainInferredConfidence() {
        val report = report("""
            add(new org.apache.wicket.markup.html.list.ListView<String>("rows", java.util.List.of("A")) {
                @Override protected void populateItem(org.apache.wicket.markup.html.list.ListItem<String> item) {
                    if (a == b) item.add(new Label("title")); if (a ^ b) item.add(new Label("title"));
                }
            });
        """, "<div wicket:id='rows'><span wicket:id='title'></span></div>")
        matched(report, "rows", "title")
        assertEquals(Confidence.INFERRED, findings(report).single { it.javaNodeId == report.node("title").nodeId }.confidence)
    }

    fun testKnownEagerResultsRequireNoExtraInputDecisions() {
        val report = report("if ((a & false) | (b & false) | (c & false) | (d & false)) removeAll(); else add(new Label(\"title\"));",
            parameters = "boolean a, boolean b, boolean c, boolean d")
        matched(report, "title")
        assertEquals(1, report.branchPathCount)
    }

    fun testFalseAndStillAuditsTheRightOperand() = noUpgrade(report(
        "add(new Label(\"title\")); if (false & effect()) removeAll();", members = EFFECT))

    fun testTrueOrStillAuditsTheRightOperand() = noUpgrade(report(
        "add(new Label(\"title\")); if (true | effect()) removeAll();", members = EFFECT))

    fun testEqualityDoesNotSkipAnEffectfulOperand() = noUpgrade(report(
        "if (a == effect()) add(new Label(\"title\")); else add(new Label(\"title\"));", members = EFFECT))

    fun testXorDoesNotSkipAnEffectfulOperand() = noUpgrade(report(
        "if (a ^ effect()) add(new Label(\"title\")); else add(new Label(\"title\"));", members = EFFECT))

    fun testEagerConstantDoesNotHideADivisionRisk() = noUpgrade(report(
        "if (true | (1 / divisor > 0)) add(new Label(\"title\"));", parameters = "int divisor"))

    fun testBoxedBooleanUnboxingRemainsUnsupported() = noUpgrade(report(
        "if (a == b) add(new Label(\"title\")); if (a != b) add(new Label(\"title\"));", parameters = "boolean a, Boolean b"))

    fun testTwoBoxedBooleansDoNotBecomePrimitiveEquality() = noUpgrade(report(
        "if (a == b) add(new Label(\"title\")); if (a != b) add(new Label(\"title\"));", parameters = "Boolean a, Boolean b"))

    fun testReferenceEqualityIsNotTreatedAsBooleanEquality() = noUpgrade(report(
        "if (first == second) add(new Label(\"title\")); if (first != second) add(new Label(\"title\"));", parameters = "String first, String second"))

    fun testStableIntegralComparisonsParticipateInBooleanEquality() = matched(report(
        "if ((count > 0) == a) add(new Label(\"title\")); if ((count > 0) != a) add(new Label(\"title\"));", parameters = "boolean a, int count"), "title")

    fun testStoredComparisonCanParticipateAsAStableBooleanInput() = matched(report("""
        boolean positive = count > 0;
        if (positive == a) add(new Label("title")); if (positive ^ a) add(new Label("title"));
    """, parameters = "boolean a, int count"), "title")

    fun testDifferentInputsAreNotCorrelatedThroughAnAccessorName() = noUpgrade(report("""
        if (same(a, b)) add(new Label("title")); if (!same(c, b)) add(new Label("title"));
    """, parameters = "boolean a, boolean b, boolean c",
        members = "private boolean same(boolean a, boolean b) { return a == b; }"))

    fun testMutatingAStableInputStillRejectsTheUpgrade() = noUpgrade(report(
        "boolean same = a == b; a = !a; if (same) add(new Label(\"title\")); if (a != b) add(new Label(\"title\"));"))

    fun testMutableFieldsCannotBecomeEagerProofInputs() = noUpgrade(report(
        "if (a & mutable) add(new Label(\"title\")); if (!a | !mutable) add(new Label(\"title\"));", members = "private boolean mutable = true;"))

    fun testFourthIndependentParityInputRetainsTheExplorationBound() = noUpgrade(report(
        "if (a ^ b ^ c ^ d) add(new Label(\"title\")); if (!(a ^ b ^ c ^ d)) add(new Label(\"title\"));",
        parameters = "boolean a, boolean b, boolean c, boolean d"))

    fun testDeepStoredParityGroupsRetainTheValueDepthBound() {
        val locals = (1..15).joinToString(" ") { "boolean value$it = ${if (it == 1) "a" else "value${it - 1}"} ^ b;" }
        noUpgrade(report("$locals if (value15) add(new Label(\"title\")); if (!value15) add(new Label(\"title\"));"))
    }

    fun testMergedOperatorOriginsNavigateButRetainEditingGuards() {
        val file = page("if (a == b) add(new Label(\"title\")); if (a ^ b) add(new Label(\"title\"));")
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

    fun testMcpReusesTheExactGraphAndRetainsConditionalUncertainty() {
        val file = page("if (a == b) add(new Label(\"title\")); if (a ^ b) add(new Label(\"title\")); if (a & b) add(new Label(\"details\"));")
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
        assertFalse(structure.toString().contains("BooleanGroup"))
        assertFalse(structure.toString().contains("booleanFacts"))
        assertEquals(before, file.text)
        assertEquals(query.render(snapshot), query.render(query.capture(project, "example.ExamplePage", module.name)))
    }

    fun testExternalEqualityAccessorEditInvalidatesTheSnapshot() {
        val helper = source("Flags", "public final class Flags { public static boolean same(boolean a, boolean b) { return a == b; } }")
        val file = page("if (Flags.same(a, b)) add(new Label(\"title\")); if (a ^ b) add(new Label(\"title\"));")
        matched(analyze(file), "title")
        val query = WicketPageAnalysisRequest()
        val old = query.capture(project, "example.ExamplePage")
        WriteCommandAction.runWriteCommandAction(project) {
            PsiDocumentManager.getInstance(project).getDocument(helper)!!.setText(helper.text.replace("a == b", "a != b"))
        }
        try { query.requireCurrent(project, old); fail("Expected stale snapshot") }
        catch (failure: PageAnalysisFailure) { assertEquals("STALE_SNAPSHOT", failure.code) }
        PsiDocumentManager.getInstance(project).commitAllDocuments()
        noUpgrade(query.capture(project, "example.ExamplePage").report)
    }

    fun testMcpUtf8LimitStillBoundsAllMergedOrigins() {
        val file = page("if (a == b) add(new Label(\"title\")); if (a ^ b) add(new Label(\"title\"));")
        analyze(file)
        val query = WicketPageAnalysisRequest()
        val snapshot = query.capture(project, "example.ExamplePage")
        val oversized = snapshot.copy(report = snapshot.report.copy(nodes = snapshot.report.nodes.map {
            if (it.wicketId == "title") it.copy(alternativeJavaLocations = listOf(SourceLocation("界".repeat(180_000), 0))) else it
        }))
        try { query.render(oversized); fail("Expected byte limit") }
        catch (failure: PageAnalysisFailure) { assertEquals("RESPONSE_TOO_LARGE", failure.code) }
    }

    fun testCancellationNeverPublishesAPartialParityProof() {
        val file = page("if (a == b) add(new Label(\"title\")); if (a ^ b) add(new Label(\"title\"));")
        val indicator = EmptyProgressIndicator()
        assertThrows(ProcessCanceledException::class.java) {
            ProgressManager.getInstance().runProcess(Runnable {
                indicator.cancel(); WicketPageAnalyzer().analyze(file.classes.single())
            }, indicator)
        }
    }

    fun testPortableDemoHasOneCommonBindingAndConditionalDetails() {
        val root = Path.of(System.getProperty("wicketlens.test.demo"))
        val file = myFixture.addFileToProject("src/main/java/example/BooleanOperatorsPage.java",
            Files.readString(root.resolve("main/java/example/BooleanOperatorsPage.java"))) as PsiJavaFile
        companion("BooleanOperatorsPage", Files.readString(root.resolve("main/resources/example/BooleanOperatorsPage.html")))
        val report = WicketPageAnalyzer().analyze(file.classes.single())!!
        matched(report, "title")
        assertTrue(report.node("title").computedId)
        assertTrue(report.node("details").conditional)
        assertTrue(findings(report).none { it.code in setOf(DiagnosticCode.JAVA_ONLY, DiagnosticCode.MARKUP_ONLY) })
    }

    private fun source(name: String, declaration: String) = myFixture.addFileToProject("src/main/java/example/$name.java", "package example; $declaration")
    private fun page(body: String, members: String = "", parameters: String = "boolean a, boolean b", name: String = "ExamplePage") = source(name, """
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
    private fun report(body: String, html: String = "<span wicket:id='title'></span>", members: String = "", parameters: String = "boolean a, boolean b", name: String = "ExamplePage") =
        analyze(page(body, members, parameters, name), html)
    private fun PageReport.node(id: String) = children().single { it.wicketId == id }
    private fun findings(report: PageReport) = report.matching!!.findings
    private fun matched(report: PageReport, vararg ids: String) = assertEquals(report.matching.toString(), ids.toSet(),
        findings(report).filter { it.code == DiagnosticCode.MATCHED }.map { f -> report.nodes.single { it.nodeId == f.javaNodeId }.wicketId }.toSet())
    private fun noUpgrade(report: PageReport) = assertFalse(report.children().any { it.branchMerged })
    companion object { private const val EFFECT = "private boolean effect() { removeAll(); return true; }" }
}
