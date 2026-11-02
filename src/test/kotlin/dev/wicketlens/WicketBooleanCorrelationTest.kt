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

class WicketBooleanCorrelationTest : WicketProjectPsiTestCase() {
    fun testComplementaryParameterChecksEstablishOneBindingWithBothOrigins() {
        val report = report("if (flag) add(new Label(\"title\")); if (!flag) add(new Label(\"title\"));")
        matched(report, "title")
        val node = report.children().single()
        assertTrue(node.branchMerged)
        assertFalse(node.conditional)
        assertFalse(node.idRenameSafe)
        assertEquals(1, node.alternativeJavaLocations.size)
        assertEquals(1, node.alternativeIdLocations.size)
    }

    fun testNegationCanEstablishTheFirstPathFact() =
        matched(report("if (!flag) add(new Label(\"title\")); if (flag) add(new Label(\"title\"));"), "title")

    fun testRepeatedSamePolarityDoesNotBecomeExactlyOneAddition() {
        val report = report("if (flag) add(new Label(\"title\")); if (flag) add(new Label(\"title\"));")
        noUpgrade(report)
    }

    fun testUnchangedAliasRetainsTheOriginalParameterIdentity() =
        matched(report("boolean alias = flag; if (flag) add(new Label(\"title\")); if (!alias) add(new Label(\"title\"));"), "title")

    fun testNegatedAliasCreatedBeforeTheFirstDecisionRetainsItsIdentity() =
        matched(report("boolean inverse = !flag; if (inverse) add(new Label(\"title\")); if (flag) add(new Label(\"title\"));"), "title")

    fun testChainsOfAliasesAndParenthesizedNegationsKeepTheSameIdentity() =
        matched(report("boolean first = flag; boolean next = !first; boolean last = !next; if ((!!last)) add(new Label(\"title\")); if (!flag) add(new Label(\"title\"));"), "title")

    fun testAnAliasMadeInsideAKnownPathDoesNotLoseTheExistingFact() =
        matched(report("if (flag) { boolean alias = !flag; if (!alias) add(new Label(\"title\")); } if (!flag) add(new Label(\"title\"));"), "title")

    fun testEarlyReturnAndCallerTailUseConsistentFlagValues() =
        matched(report("if (flag) { add(new Label(\"title\")); return; } if (!flag) add(new Label(\"title\"));"), "title")

    fun testReviewedBooleanAccessorPreservesTheInputIdentity() =
        matched(report("if (read(flag)) add(new Label(\"title\")); if (!flag) add(new Label(\"title\"));",
            "private boolean read(boolean value) { return value; }"), "title")

    fun testReviewedNegatingAccessorPreservesTheInputIdentity() =
        matched(report("if (inverse(flag)) add(new Label(\"title\")); if (flag) add(new Label(\"title\"));",
            "private boolean inverse(boolean value) { return !value; }"), "title")

    fun testComplementaryCallsIntoTheSameHelperShareTheOriginalFlag() =
        matched(report("attach(flag); attach(!flag);",
            "private void attach(boolean enabled) { if (enabled) add(new Label(\"title\")); }"), "title")

    fun testAStoredReviewedComparisonGetsOneStableLocalValue() =
        matched(report("boolean positive = count > 0; if (positive) add(new Label(\"title\")); if (!positive) add(new Label(\"title\"));",
            parameters = "int count"), "title")

    fun testEachBooleanParameterEvaluationHasAStableValueWithinItsOwnCall() =
        matched(report("attach(count > 0);", """
            private void attach(boolean value) {
                if (value) add(new Label("title"));
                if (!value) add(new Label("title"));
            }
        """, "int count"), "title")

    fun testIndependentCallsWithTheSameParameterPsiDoNotShareFacts() {
        val report = report("attach(left > 0); attach(right > 0);", """
            private void attach(boolean value) {
                if (value) add(new Label("title"));
            }
        """, "int left, int right")
        noUpgrade(report)
    }

    fun testDifferentEntryParametersAreNotCorrelatedByTheirUnknownValue() {
        val report = report("if (first) add(new Label(\"title\")); if (!second) add(new Label(\"title\"));",
            parameters = "boolean first, boolean second")
        noUpgrade(report)
    }

    fun testLocalShadowingDoesNotReuseAParameterNameAsItsIdentity() {
        val report = report("if (flag) add(new Label(\"title\")); attach(other);",
            "private void attach(boolean flag) { if (!flag) add(new Label(\"title\")); }",
            "boolean flag, boolean other")
        noUpgrade(report)
    }

    fun testTwoIndependentLocalSnapshotsDoNotShareASingleUnknownIdentity() {
        val report = report("""
            boolean first = left > 0; boolean second = right > 0;
            if (first) add(new Label("title")); if (!second) add(new Label("title"));
        """, parameters = "int left, int right")
        noUpgrade(report)
    }

    fun testPrivateFinalPageStoragePreservesAnExistingFlagIdentityAcrossPhases() =
        matched(report("this.enabled = flag; if (flag) add(new Label(\"title\"));", """
            private final boolean enabled;
            @Override protected void onInitialize() { super.onInitialize(); if (!enabled) add(new Label("title")); }
        """), "title")

    fun testImmutableProviderStoragePreservesAnExistingFlagIdentity() {
        source("Flags", """
            public final class Flags {
                private final boolean value;
                public Flags(boolean value) { this.value = value; }
                public boolean enabled() { return value; }
            }
        """)
        matched(report("Flags flags = new Flags(flag); if (flags.enabled()) add(new Label(\"title\")); if (!flag) add(new Label(\"title\"));"), "title")
    }

    fun testDifferentImmutableProviderValuesDoNotBecomeOneFlag() {
        source("Flags", """
            public final class Flags {
                private final boolean value;
                public Flags(boolean value) { this.value = value; }
                public boolean enabled() { return value; }
            }
        """)
        noUpgrade(report("""
            Flags first = new Flags(left); Flags second = new Flags(right);
            if (first.enabled()) add(new Label("title")); if (!second.enabled()) add(new Label("title"));
        """, parameters = "boolean left, boolean right"))
    }

    fun testIfAndTernaryCanReuseOneStableFlag() =
        matched(report("if (flag) add(new Label(\"title\")); if (!flag) add(flag ? new Label(\"unreachable\") : new Label(\"title\"));"), "title")

    fun testKnownPathFactsCanProveShortCircuitingWithoutEvaluatingTheDeadCall() =
        matched(report("""
            if (flag) {
                if (flag || effect()) add(new Label("title"));
            } else {
                if (!flag || effect()) add(new Label("title"));
            }
        """, "private boolean effect() { removeAll(); return true; }"), "title")

    fun testPossibleEffectsOnAFeasiblePathStillRejectTheUpgrade() =
        noUpgrade(report("""
            if (flag) add(new Label("title"));
            if (!flag && effect()) add(new Label("title"));
        """, "private boolean effect() { removeAll(); return true; }"))

    fun testKnownFlagValuesRemainAvailableToExistingBooleanOperators() =
        matched(report("""
            if (flag) { if (flag & true) add(new Label("title")); }
            else { if (flag == false) add(new Label("title")); }
        """), "title")

    fun testComplementaryCompoundChecksReuseTheirStableInputs() =
        matched(report("""
            if (left && right) add(new Label("title"));
            if (!(left && right)) add(new Label("title"));
        """, parameters = "boolean left, boolean right"), "title")

    fun testRepeatedComparisonsOfOneStableIntegralInputShareFacts() =
        matched(report("if (count > 0) add(new Label(\"title\")); if (!(count > 0)) add(new Label(\"title\"));",
            parameters = "int count"), "title")

    fun testReviewedComparisonAccessorsRetainTheStableIntegralInput() =
        matched(report("if (positive(count)) add(new Label(\"title\")); if (!positive(count)) add(new Label(\"title\"));",
            "private boolean positive(int count) { return count > 0; }", "int count"), "title")

    fun testFourPairsOfChecksOfOneFlagFitTheExistingExplorationLimit() {
        val body = (1..4).joinToString(" ") { "if (flag) add(new Label(\"id$it\")); if (!flag) add(new Label(\"id$it\"));" }
        matched(report(body, html = (1..4).joinToString("") { "<span wicket:id='id$it'></span>" }), "id1", "id2", "id3", "id4")
    }

    fun testThreeIndependentFlagsStillFitEightConsistentPaths() {
        val body = (1..3).joinToString(" ") { "if (flag$it) add(new Label(\"id$it\")); if (!flag$it) add(new Label(\"id$it\"));" }
        matched(report(body, parameters = "boolean flag1, boolean flag2, boolean flag3",
            html = (1..3).joinToString("") { "<span wicket:id='id$it'></span>" }), "id1", "id2", "id3")
    }

    fun testFourthIndependentFlagStillExhaustsTheBudget() {
        val body = (1..4).joinToString(" ") { "if (flag$it) add(new Label(\"id$it\")); if (!flag$it) add(new Label(\"id$it\"));" }
        noUpgrade(report(body, parameters = "boolean flag1, boolean flag2, boolean flag3, boolean flag4"))
    }

    fun testAssigningToAParameterDoesNotReuseItsEarlierFact() =
        noUpgrade(report("if (flag) add(new Label(\"title\")); flag = !flag; if (!flag) add(new Label(\"title\"));"))

    fun testAssigningToALocalAliasDoesNotReuseItsEarlierFact() =
        noUpgrade(report("boolean alias = flag; if (alias) add(new Label(\"title\")); alias = !alias; if (!alias) add(new Label(\"title\"));"))

    fun testMutableFieldCannotAcquireAStableIdentityThroughAnAlias() =
        noUpgrade(report("boolean alias = enabled; if (alias) add(new Label(\"title\")); if (!alias) add(new Label(\"title\"));",
            "private boolean enabled = true;"))

    fun testNonprivateFieldCannotLaunderAStableIdentity() =
        noUpgrade(report("enabled = flag; if (enabled) add(new Label(\"title\")); if (!enabled) add(new Label(\"title\"));",
            "public final boolean enabled;"))

    fun testBoxedParameterCannotBypassTheUnboxingGuard() =
        noUpgrade(report("if (flag) add(new Label(\"title\")); if (!flag) add(new Label(\"title\"));", parameters = "Boolean flag"))

    fun testKnownBoundFlagCannotHideAReachedOpaqueRootCall() =
        noUpgrade(report("if (flag) add(new Label(\"title\")); if (flag && effect()) add(new Label(\"other\")); if (!flag) add(new Label(\"title\"));",
            "private boolean effect() { removeAll(); return true; }"))

    fun testBranchSpecificComponentsStayConditionalAfterCorrelation() {
        val report = report("""
            if (flag) add(new Label("title"), new Label("details"));
            if (!flag) add(new Label("title"));
        """, html = "<span wicket:id='title'></span><span wicket:id='details'></span>")
        matched(report, "title")
        val details = report.children().single { it.wicketId == "details" }
        assertTrue(details.conditional)
        assertEquals(DiagnosticCode.UNRESOLVED_BRANCH, report.matching!!.findings.single { it.javaNodeId == details.nodeId }.code)
        assertTrue(report.matching.findings.none { it.code == DiagnosticCode.JAVA_ONLY || it.code == DiagnosticCode.MARKUP_ONLY })
    }

    fun testOriginsSupportNavigationButNoSingleOriginRenameOrCompletion() {
        val file = page("if (flag) add(new Label(\"title\")); if (!flag) add(new Label(\"title\"));")
        val report = analyze(file)
        for (origin in WicketIdPsi.javaIdElements(project, report.children().single())) {
            val pair = WicketIdPsi.pair(origin)!!
            assertNotNull(WicketIdPsi.javaId(origin))
            assertNull(WicketIdRenameTarget.at(file, origin.textOffset + 1))
            assertNull(WicketIdRenameTarget.at(pair.attribute.containingFile, pair.attribute.textOffset + 1))
            assertTrue(WicketIdCompletionContributor().candidates(origin).isEmpty())
        }
        assertTrue(problems(file).results.isEmpty())
    }

    fun testDefiniteMissingBindingStillHighlightsBothAlternativeOrigins() {
        val file = page("if (flag) add(new Label(\"missing\")); if (!flag) add(new Label(\"missing\"));")
        val report = analyze(file)
        assertEquals(WicketIdPsi.javaIdElements(project, report.children().single()).map { it.textRange }.toSet(),
            problems(file).results.map { it.psiElement.textRange }.toSet())
    }

    fun testMcpViewsShareTheCorrelatedGraphWithoutExportingPathFacts() {
        val file = page("if (flag) add(new Label(\"title\")); if (!flag) add(new Label(\"title\"));")
        analyze(file)
        val before = file.text
        val query = WicketPageAnalysisRequest()
        val snapshot = query.capture(project, "example.ExamplePage", module.name)
        val structure = JsonParser.parseString(query.render(snapshot)).asJsonObject
        assertEquals(JsonParser.parseString(WicketContextExporter().export(snapshot.report)), structure["analysis"])
        val context = JsonParser.parseString(query.render(snapshot, WicketMcpReport.CONTEXT, componentPath = "title")).asJsonObject
        val component = context["context"].asJsonObject["component"].asJsonObject
        assertEquals("MATCHED", component["status"].asString)
        assertEquals(2, component["idLocations"].asJsonArray.size())
        assertFalse(component["idRenameSafe"].asBoolean)
        assertEquals(module.name, context["target"].asJsonObject["moduleName"].asString)
        assertFalse(structure.toString().contains("booleanFacts"))
        assertFalse(structure.toString().contains("BooleanSymbol"))
        val validation = JsonParser.parseString(query.render(snapshot, WicketMcpReport.VALIDATION)).asJsonObject
        assertEquals(structure["analysis"].asJsonObject["matching"].asJsonObject["findings"], validation["validation"].asJsonObject["findings"])
        assertEquals(query.render(snapshot), query.render(query.capture(project, "example.ExamplePage", module.name)))
        assertEquals(before, file.text)
    }

    fun testFactsAreNotReusedByTheNextAnalysisOfTheSameFile() {
        val file = page("if (flag) add(new Label(\"title\")); if (!flag) add(new Label(\"title\"));")
        analyze(file)
        val query = WicketPageAnalysisRequest()
        val before = query.capture(project, "example.ExamplePage")
        WriteCommandAction.runWriteCommandAction(project) {
            PsiDocumentManager.getInstance(project).getDocument(file)!!.setText(file.text.replace("if (!flag)", "if (flag)"))
        }
        try { query.requireCurrent(project, before); fail("Expected stale snapshot") }
        catch (failure: PageAnalysisFailure) { assertEquals("STALE_SNAPSHOT", failure.code) }
        PsiDocumentManager.getInstance(project).commitAllDocuments()
        noUpgrade(query.capture(project, "example.ExamplePage").report)
    }

    fun testChangingAnExternalNegatingAccessorInvalidatesTheCorrelatedSnapshot() {
        val helper = source("Flags", "public final class Flags { public static boolean opposite(boolean flag) { return !flag; } }")
        val file = page("if (flag) add(new Label(\"title\")); if (Flags.opposite(flag)) add(new Label(\"title\"));")
        matched(analyze(file), "title")
        val query = WicketPageAnalysisRequest()
        val before = query.capture(project, "example.ExamplePage")
        assertTrue(before.report.javaSourceFiles.contains(helper.virtualFile.path))
        WriteCommandAction.runWriteCommandAction(project) {
            PsiDocumentManager.getInstance(project).getDocument(helper)!!.setText(helper.text.replace("return !flag;", "return flag;"))
        }
        try { query.requireCurrent(project, before); fail("Expected stale snapshot") }
        catch (failure: PageAnalysisFailure) { assertEquals("STALE_SNAPSHOT", failure.code) }
        PsiDocumentManager.getInstance(project).commitAllDocuments()
        noUpgrade(query.capture(project, "example.ExamplePage").report)
    }

    fun testCancellationDoesNotPublishAFallbackOrRetainPathFacts() {
        val file = page("if (flag) add(new Label(\"title\")); if (!flag) add(new Label(\"title\"));")
        val indicator = EmptyProgressIndicator()
        assertThrows(ProcessCanceledException::class.java) {
            ProgressManager.getInstance().runProcess(Runnable {
                indicator.cancel(); WicketPageAnalyzer().analyze(file.classes.single())
            }, indicator)
        }
        matched(analyze(file), "title")
    }

    fun testPortableDemoHasOneCommonTitleAndConditionalDetails() {
        val root = Path.of(System.getProperty("wicketlens.test.demo"))
        val file = myFixture.addFileToProject("src/main/java/example/CorrelatedSetupPage.java",
            Files.readString(root.resolve("main/java/example/CorrelatedSetupPage.java"))) as PsiJavaFile
        companion("CorrelatedSetupPage", Files.readString(root.resolve("main/resources/example/CorrelatedSetupPage.html")))
        val report = WicketPageAnalyzer().analyze(file.classes.single())!!
        matched(report, "title")
        assertEquals(1, report.children().single { it.wicketId == "title" }.alternativeIdLocations.size)
        assertTrue(report.children().single { it.wicketId == "details" }.conditional)
        assertTrue(report.matching!!.findings.none { it.code == DiagnosticCode.JAVA_ONLY || it.code == DiagnosticCode.MARKUP_ONLY })
    }

    private fun source(name: String, declaration: String) = myFixture.addFileToProject("src/main/java/example/$name.java",
        "package example; import org.apache.wicket.markup.html.basic.Label; $declaration")
    private fun page(body: String, members: String = "", parameters: String = "boolean flag") =
        source("ExamplePage", """
            public class ExamplePage extends org.apache.wicket.markup.html.WebPage {
                public ExamplePage($parameters) { $body }
                $members
            }
        """.trimIndent()) as PsiJavaFile
    private fun analyze(file: PsiJavaFile, html: String = "<span wicket:id='title'></span>"): PageReport {
        companion(html = html)
        return WicketPageAnalyzer().analyze(file.classes.single())!!
    }
    private fun report(body: String, members: String = "", parameters: String = "boolean flag",
        html: String = "<span wicket:id='title'></span>") = analyze(page(body, members, parameters), html)
    private fun matched(report: PageReport, vararg ids: String) {
        assertEquals(report.matching.toString(), ids.toSet(), report.matching!!.findings.filter { it.code == DiagnosticCode.MATCHED }
            .map { finding -> report.nodes.single { it.nodeId == finding.javaNodeId }.wicketId }.toSet())
        assertTrue(report.warnings.none { it.code == DiagnosticCode.UNRESOLVED_SCOPE })
    }
    private fun noUpgrade(report: PageReport) = assertFalse(report.children().any { it.branchMerged })
    private fun problems(file: PsiFile) = ProblemsHolder(InspectionManager.getInstance(project), file, false).also {
        WicketUnpairedIdInspection().buildVisitor(it, false).visitFile(file)
    }
}
