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

class WicketCompoundBooleanTest : WicketProjectPsiTestCase() {
    fun testAndAndItsNegationEstablishExactlyOneBinding() {
        val report = report("if (a && b) add(new Label(\"title\")); if (!(a && b)) add(new Label(\"title\"));")
        matched(report, "title")
        assertEquals(3, report.branchPathCount)
        assertEquals(2, report.node("title").idLocations().size)
        assertFalse(report.node("title").conditional)
    }

    fun testDeMorganComplementSharesOriginalSymbols() = matched(report("""
        if (a && b) add(new Label("title"));
        if (!a || !b) add(new Label("title"));
    """), "title")

    fun testOrAndItsDeMorganComplementShareOriginalSymbols() = matched(report("""
        if (a || b) add(new Label("title"));
        if (!a && !b) add(new Label("title"));
    """), "title")

    fun testReorderedPureInputsStillUseTheSameFacts() = matched(report("""
        if (a && b) add(new Label("title"));
        if (!(b && a)) add(new Label("title"));
    """), "title")

    fun testNestedThreeInputPredicateAndComplementFitThePathBudget() {
        val report = report("if (a && (b || c)) add(new Label(\"title\")); if (!a || (!b && !c)) add(new Label(\"title\"));",
            parameters = "boolean a, boolean b, boolean c")
        matched(report, "title")
        assertTrue(report.branchPathCount in 4..8)
    }

    fun testCompoundSnapshotBeforeFirstDecisionPreservesInputRelations() = matched(report("""
        boolean both = a && b;
        if (both) add(new Label("title")); if (!a || !b) add(new Label("title"));
    """), "title")

    fun testNegatedCompoundAliasPreservesInputRelations() = matched(report("""
        boolean neither = !(a || b);
        if (!neither) add(new Label("title")); if (!a && !b) add(new Label("title"));
    """), "title")

    fun testCompoundAliasUsesLaterFactsWithoutChangingItsSnapshotValue() = matched(report("""
        boolean both = a && b;
        if (a) { if (b) { if (both) add(new Label("title")); } else add(new Label("title")); }
        else add(new Label("title"));
    """), "title")

    fun testReviewedAccessorCanReturnACompoundValue() = matched(report("""
        if (both(a, b)) add(new Label("title")); if (!a || !b) add(new Label("title"));
    """, members = "private boolean both(boolean first, boolean second) { return first && second; }"), "title")

    fun testReviewedAccessorCanReturnANegatedCompoundValue() = matched(report("""
        if (neither(a, b)) add(new Label("title")); if (a || b) add(new Label("title"));
    """, members = "private boolean neither(boolean first, boolean second) { return !(first || second); }"), "title")

    fun testCompoundParametersKeepOriginalSymbolsAcrossHelperCalls() = matched(report("""
        attach(a && b); attach(!a || !b);
    """, members = "private void attach(boolean value) { if (value) add(new Label(\"title\")); }"), "title")

    fun testPrivateFinalPageStoragePreservesCompoundSnapshotAcrossPhases() = matched(report("""
        both = a && b; if (both) add(new Label("title"));
    """, members = """
        private final boolean both;
        @Override protected void onInitialize() {
            super.onInitialize(); if (!both) add(new Label("title"));
        }
    """), "title")

    fun testImmutableProviderCanStoreAndReturnACompoundValue() {
        source("Flags", """
            public final class Flags {
                private final boolean value;
                public Flags(boolean value) { this.value = value; }
                public boolean enabled() { return value; }
            }
        """)
        matched(report("Flags flags = new Flags(a && b); if (flags.enabled()) add(new Label(\"title\")); if (!a || !b) add(new Label(\"title\"));"), "title")
    }

    fun testTernaryUsesTheSameCompoundFactsAsIf() = matched(report("""
        if (a && b) add(new Label("ti" + "tle"));
        if (!a || !b) add((a && b) ? new Label("unreachable") : new Label("title"));
    """), "title")

    fun testOptionalCompoundChildKeepsUncertaintyScopedToItsOwnPath() {
        val report = report("if (a && b) add(new Label(\"title\")); if (!a || !b) add(new Label(\"title\")); if (a || b) add(new Label(\"details\"));",
            "<span wicket:id='title'></span><span wicket:id='details'></span><span wicket:id='outside'></span>")
        matched(report, "title")
        val details = report.node("details")
        assertTrue(details.conditional)
        assertEquals(DiagnosticCode.UNRESOLVED_BRANCH, findings(report).single { it.javaNodeId == details.nodeId }.code)
        assertEquals(setOf("outside"), findings(report).filter { it.code == DiagnosticCode.MARKUP_ONLY }
            .map { f -> report.markup!!.elements.single { it.nodeId == f.markupNodeId }.wicketId }.toSet())
    }

    fun testFalseAndDoesNotForceBothInputsFalse() {
        val report = report("if (a && b) add(new Label(\"title\")); if (!a && !b) add(new Label(\"title\"));")
        assertTrue(report.node("title").conditional)
        assertEquals(DiagnosticCode.UNRESOLVED_BRANCH, findings(report).single { it.javaNodeId == report.node("title").nodeId }.code)
        assertTrue(findings(report).none { it.code == DiagnosticCode.MATCHED })
    }

    fun testTrueOrDoesNotForceBothInputsTrue() {
        val report = report("if (a || b) { if (a && b) add(new Label(\"title\")); }")
        assertTrue(report.node("title").conditional)
        assertEquals(DiagnosticCode.UNRESOLVED_BRANCH, findings(report).single { it.javaNodeId == report.node("title").nodeId }.code)
        assertTrue(findings(report).none { it.code == DiagnosticCode.MATCHED })
    }

    fun testCompoundQueueChecksReuseExistingPerPathPlacement() {
        val report = report("""
            queue(new org.apache.wicket.markup.html.WebMarkupContainer("box"));
            if (a && b) queue(new Label("title")); if (!a || !b) queue(new Label("title"));
        """, "<div wicket:id='box'><span wicket:id='title'></span></div>")
        matched(report, "box", "title")
        assertEquals(report.node("box").nodeId, report.node("title").parentNodeId)
        assertEquals(report.rootNodeId, report.node("title").queuedInNodeId)
    }

    fun testCompoundRepeaterChecksRetainInferredTemplateConfidence() {
        val report = report("""
            add(new org.apache.wicket.markup.html.list.ListView<String>("rows", java.util.List.of("A")) {
                @Override protected void populateItem(org.apache.wicket.markup.html.list.ListItem<String> item) {
                    if (a && b) item.add(new Label("title")); if (!a || !b) item.add(new Label("title"));
                }
            });
        """, "<div wicket:id='rows'><span wicket:id='title'></span></div>")
        matched(report, "rows", "title")
        assertEquals(Confidence.INFERRED, findings(report).single { it.javaNodeId == report.node("title").nodeId }.confidence)
    }

    fun testKnownIndependentLogicalResultsConsumeNoNewDecisions() {
        val report = report("""
            if ((a || true) && (b || true) && (c || true) && (d || true)) add(new Label("title"));
        """, parameters = "boolean a, boolean b, boolean c, boolean d")
        matched(report, "title")
        assertEquals(1, report.branchPathCount)
        assertFalse(report.node("title").branchMerged)
    }

    fun testKnownShortCircuitStillSkipsAnUnreviewedOperand() = matched(report("""
        if (true || effect()) {
            if (a && b) add(new Label("title")); if (!a || !b) add(new Label("title"));
        }
    """, members = EFFECT), "title")

    fun testKnownPathFactStillSkipsADeadOperandDuringCompoundRead() = matched(report("""
        if (a) {
            if (a || effect()) add(new Label("title"));
        } else {
            if (b && !a) add(new Label("title")); if (!b || a) add(new Label("title"));
        }
    """, members = EFFECT), "title")

    fun testPossibleRightOperandEffectsStillRejectTheEntireUpgrade() = noUpgrade(report("""
        if ((a && b) && effect()) add(new Label("title")); else add(new Label("title"));
    """, members = EFFECT))

    fun testEagerBooleanOperatorsStillEvaluateTheirEffectfulOperands() = noUpgrade(report("""
        if (a && b) add(new Label("title")); if (true | effect()) add(new Label("other"));
    """, members = EFFECT))

    fun testStableIntegralComparisonsParticipateInCompoundGroups() = matched(report("""
        if (count > 0 && a) add(new Label("title")); if (!(count > 0 && a)) add(new Label("title"));
    """, parameters = "boolean a, int count"), "title")

    fun testOnceStoredComparisonCanSupplyAStableCompoundInput() = matched(report("""
        boolean positive = count > 0;
        if (positive && a) add(new Label("title")); if (!positive || !a) add(new Label("title"));
    """, parameters = "boolean a, int count"), "title")

    fun testIndependentInputsAreNotCorrelatedByPredicateTextOrParameterNames() = noUpgrade(report("""
        if (both(a, b)) add(new Label("title")); if (!both(c, b)) add(new Label("title"));
    """, members = "private boolean both(boolean a, boolean b) { return a && b; }",
        parameters = "boolean a, boolean b, boolean c"))

    fun testRepeatedSamePolarityCanStillProduceDuplicateAdditions() = noUpgrade(report("""
        if (a && b) add(new Label("title")); if (a && b) add(new Label("title"));
    """))

    fun testAssigningAnInputInvalidatesTheWholeReviewedSetup() = noUpgrade(report("""
        boolean both = a && b; a = !a;
        if (both) add(new Label("title")); if (!a || !b) add(new Label("title"));
    """))

    fun testMutableFieldCannotEnterACompoundProof() = noUpgrade(report("""
        if (a && mutable) add(new Label("title")); if (!a || !mutable) add(new Label("title"));
    """, members = "private boolean mutable = true;"))

    fun testBoxedConditionCannotEnterACompoundProof() = noUpgrade(report("""
        if (a && b) add(new Label("title")); if (!a || !b) add(new Label("title"));
    """, parameters = "boolean a, Boolean b"))

    fun testFourthRequiredInputStillExceedsTheExplorationBudget() = noUpgrade(report("""
        if (a && b && c && d) add(new Label("title")); if (!a || !b || !c || !d) add(new Label("title"));
    """, parameters = "boolean a, boolean b, boolean c, boolean d"))

    fun testDeepStoredCompoundValuesRetainABoundedProofDepth() {
        val locals = (1..15).joinToString(" ") { "boolean value$it = ${if (it == 1) "a" else "value${it - 1}"} && b;" }
        noUpgrade(report("$locals if (value15) add(new Label(\"title\")); if (!value15) add(new Label(\"title\"));"))
    }

    fun testCompoundOriginsNavigateAndKeepMergedEditingGuards() {
        val file = page("if (a && b) add(new Label(\"title\")); if (!a || !b) add(new Label(\"title\"));")
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

    fun testMcpProjectionsKeepCommonAndConditionalBindingsWithoutExportingProofState() {
        val file = page("if (a && b) add(new Label(\"title\")); if (!a || !b) add(new Label(\"title\")); if (a) add(new Label(\"details\"));")
        analyze(file, "<span wicket:id='title'></span><span wicket:id='details'></span>")
        val before = file.text
        val query = WicketPageAnalysisRequest()
        val snapshot = query.capture(project, "example.ExamplePage", module.name)
        val structure = JsonParser.parseString(query.render(snapshot)).asJsonObject
        assertEquals(JsonParser.parseString(WicketContextExporter().export(snapshot.report)), structure["analysis"])
        val context = JsonParser.parseString(query.render(snapshot, WicketMcpReport.CONTEXT, componentPath = "title")).asJsonObject
        assertEquals("MATCHED", context["context"].asJsonObject["component"].asJsonObject["status"].asString)
        assertEquals(2, context["context"].asJsonObject["component"].asJsonObject["idLocations"].asJsonArray.size())
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

    fun testExternalPredicateEditInvalidatesTheSharedSnapshot() {
        val helper = source("Flags", "public final class Flags { public static boolean both(boolean a, boolean b) { return a && b; } }")
        val file = page("if (Flags.both(a, b)) add(new Label(\"title\")); if (!a || !b) add(new Label(\"title\"));")
        matched(analyze(file), "title")
        val query = WicketPageAnalysisRequest()
        val old = query.capture(project, "example.ExamplePage")
        WriteCommandAction.runWriteCommandAction(project) {
            PsiDocumentManager.getInstance(project).getDocument(helper)!!.setText(helper.text.replace("a && b", "a || b"))
        }
        try { query.requireCurrent(project, old); fail("Expected stale snapshot") }
        catch (failure: PageAnalysisFailure) { assertEquals("STALE_SNAPSHOT", failure.code) }
        PsiDocumentManager.getInstance(project).commitAllDocuments()
        noUpgrade(query.capture(project, "example.ExamplePage").report)
    }

    fun testMcpByteLimitStillIncludesAllCompoundOrigins() {
        val file = page("if (a && b) add(new Label(\"title\")); if (!a || !b) add(new Label(\"title\"));")
        analyze(file)
        val query = WicketPageAnalysisRequest()
        val snapshot = query.capture(project, "example.ExamplePage")
        val oversized = snapshot.copy(report = snapshot.report.copy(nodes = snapshot.report.nodes.map {
            if (it.wicketId == "title") it.copy(alternativeJavaLocations = listOf(SourceLocation("界".repeat(180_000), 0))) else it
        }))
        try { query.render(oversized); fail("Expected byte limit") }
        catch (failure: PageAnalysisFailure) { assertEquals("RESPONSE_TOO_LARGE", failure.code) }
    }

    fun testCancellationDoesNotPublishAPartialCompoundProof() {
        val file = page("if (a && b) add(new Label(\"title\")); if (!a || !b) add(new Label(\"title\"));")
        val indicator = EmptyProgressIndicator()
        assertThrows(ProcessCanceledException::class.java) {
            ProgressManager.getInstance().runProcess(Runnable {
                indicator.cancel(); WicketPageAnalyzer().analyze(file.classes.single())
            }, indicator)
        }
    }

    fun testPortableDemoHasACommonBindingAndConditionalDetails() {
        val root = Path.of(System.getProperty("wicketlens.test.demo"))
        val file = myFixture.addFileToProject("src/main/java/example/CompoundSetupPage.java",
            Files.readString(root.resolve("main/java/example/CompoundSetupPage.java"))) as PsiJavaFile
        companion("CompoundSetupPage", Files.readString(root.resolve("main/resources/example/CompoundSetupPage.html")))
        val report = WicketPageAnalyzer().analyze(file.classes.single())!!
        matched(report, "title")
        assertTrue(report.node("title").computedId)
        assertTrue(report.node("details").conditional)
        assertTrue(findings(report).none { it.code in setOf(DiagnosticCode.JAVA_ONLY, DiagnosticCode.MARKUP_ONLY) })
    }

    private fun source(name: String, declaration: String) = myFixture.addFileToProject("src/main/java/example/$name.java", "package example; $declaration")
    private fun page(body: String, members: String = "", parameters: String = "boolean a, boolean b") = source("ExamplePage", """
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
    private fun report(body: String, html: String = "<span wicket:id='title'></span>", members: String = "", parameters: String = "boolean a, boolean b") =
        analyze(page(body, members, parameters), html)
    private fun PageReport.node(id: String) = children().single { it.wicketId == id }
    private fun findings(report: PageReport) = report.matching!!.findings
    private fun matched(report: PageReport, vararg ids: String) = assertEquals(report.matching.toString(), ids.toSet(),
        findings(report).filter { it.code == DiagnosticCode.MATCHED }.map { f -> report.nodes.single { it.nodeId == f.javaNodeId }.wicketId }.toSet())
    private fun noUpgrade(report: PageReport) = assertFalse(report.children().any { it.branchMerged })
    companion object { private const val EFFECT = "private boolean effect() { removeAll(); return true; }" }
}
