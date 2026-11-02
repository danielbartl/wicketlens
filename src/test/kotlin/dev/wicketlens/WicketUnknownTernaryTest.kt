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

class WicketUnknownTernaryTest : WicketProjectPsiTestCase() {
    fun testAlternativeComponentsShareOneBindingAndRetainBothOrigins() {
        val report = report("add(enabled ? new Label(\"title\", \"Short\") : new Label(\"title\", \"Long\"));")
        matched(report, "title")
        val node = report.children().single()
        assertTrue(node.branchMerged)
        assertFalse(node.conditional)
        assertFalse(node.idRenameSafe)
        assertEquals(1, node.alternativeJavaLocations.size)
        assertEquals(1, node.alternativeIdLocations.size)
    }

    fun testOneAllocationWithAlternativeIdLiteralsRetainsBothIdOrigins() {
        val report = report("add(new Label(enabled ? \"title\" : \"title\"));")
        matched(report, "title")
        val node = report.children().single()
        assertTrue(node.alternativeJavaLocations.isEmpty())
        assertEquals(1, node.alternativeIdLocations.size)
    }

    fun testDifferentIdsRemainConditionalWithoutMissingPairClaims() {
        val report = report("add(new Label(enabled ? \"title\" : \"details\"));",
            html = "<span wicket:id='title'></span><span wicket:id='details'></span>")
        conditional(report, "title", "details")
        assertTrue(report.matching!!.findings.all { it.code == DiagnosticCode.UNRESOLVED_BRANCH })
    }

    fun testDifferentTypesRemainSeparateConditionalVariants() {
        val report = report("add(enabled ? new Label(\"title\") : new WebMarkupContainer(\"title\"));")
        conditional(report, "title")
        assertEquals(2, report.children().size)
        assertEquals(2, report.children().map { it.componentType }.distinct().size)
        assertTrue(report.matching!!.findings.none { it.code in PAIRS })
    }

    fun testCaptionsCanDifferWithoutInventingTheirRuntimeValue() {
        val report = report("add(new Label(\"title\", enabled ? \"Short\" : \"Long\"));")
        matched(report, "title")
        val json = WicketContextExporter().export(report)
        assertFalse(json.contains("Short"))
        assertFalse(json.contains("Long"))
        assertEquals(1, WicketIdPsi.javaIdElements(project, report.children().single()).size)
    }

    fun testOpaqueModelParametersRemainOpaqueWhenSelected() {
        matched(report("add(new Label(\"title\", enabled ? first : second));",
            parameters = "boolean enabled, org.apache.wicket.model.IModel<String> first, org.apache.wicket.model.IModel<String> second"), "title")
    }

    fun testTernaryLocalKeepsItsSelectedIdOriginsAcrossLaterReads() {
        val report = report("String id = enabled ? \"title\" : \"title\"; add(new Label(id));")
        matched(report, "title")
        assertEquals(1, report.children().single().alternativeIdLocations.size)
    }

    fun testTernaryComponentLocalStillRequiresFreshAllocations() {
        matched(report("Label title = enabled ? new Label(\"title\") : new Label(\"title\"); add(title);"), "title")
    }

    fun testUnknownTernaryScalarAccessorKeepsTheReadOnlyContract() {
        matched(report("add(new Label(id(enabled)));",
            "private String id(boolean choice) { return choice ? \"title\" : \"title\"; }"), "title")
    }

    fun testUnknownTernaryComponentFactoryKeepsFreshnessAndCallerOrigins() {
        val report = report("add(label(enabled, \"title\"));", """
            private Label label(boolean choice, String id) { return choice ? new Label(id) : new Label(id); }
        """)
        matched(report, "title")
        assertEquals(1, report.children().single().alternativeJavaLocations.size)
        assertTrue(report.children().single().alternativeIdLocations.isEmpty())
    }

    fun testCachedComponentCannotHideInOneTernaryFactoryArm() {
        unsupported(report("add(label(enabled));", """
            private final Label stored = new Label("title");
            private Label label(boolean choice) { return choice ? stored : new Label("title"); }
        """))
    }

    fun testFreshProviderAlternativesPreserveTheirImmutableConfiguration() {
        parts()
        matched(report("Parts parts = enabled ? new Parts(\"title\") : new Parts(\"title\"); add(parts.label());"), "title")
    }

    fun testFreshProviderFactoryCanReturnAnUnknownTernary() {
        parts()
        matched(report("add(parts(enabled).label());",
            "private Parts parts(boolean choice) { return choice ? new Parts(\"title\") : new Parts(\"title\"); }"), "title")
    }

    fun testReadOnlyProviderAccessorCanSelectExistingKnownProviders() {
        parts()
        matched(report("""
            Parts first = new Parts("title"); Parts second = new Parts("title");
            add(select(enabled, first, second).label());
        """, "private Parts select(boolean choice, Parts first, Parts second) { return choice ? first : second; }"), "title")
    }

    fun testConditionalReceiversPreserveDistinctParentAlternatives() {
        val report = report("""
            WebMarkupContainer left = new WebMarkupContainer("left"), right = new WebMarkupContainer("right");
            add(left, right);
            (enabled ? left : right).add(new Label("title"));
        """, html = "<div wicket:id='left'><span wicket:id='title'></span></div><div wicket:id='right'><span wicket:id='title'></span></div>")
        matched(report, "left", "right")
        conditional(report, "title")
        assertEquals(2, report.children().count { it.wicketId == "title" })
    }

    fun testThreeIdTernariesUseThreeChoicesRatherThanEvaluatingIdsTwice() {
        val body = (1..3).joinToString(" ") { "add(new Label(flag$it ? \"id$it\" : \"id$it\"));" }
        matched(report(body, parameters = "boolean flag1, boolean flag2, boolean flag3",
            html = (1..3).joinToString("") { "<span wicket:id='id$it'></span>" }), "id1", "id2", "id3")
    }

    fun testForwardedNonfirstConstructorIdsAreEvaluatedOnceToo() {
        source("Wrapped", """
            public class Wrapped extends Label {
                public Wrapped(String caption, String id) { super(id, caption); }
            }
        """)
        val body = (1..3).joinToString(" ") { "add(new Wrapped(\"Caption\", flag$it ? \"id$it\" : \"id$it\"));" }
        matched(report(body, parameters = "boolean flag1, boolean flag2, boolean flag3",
            html = (1..3).joinToString("") { "<span wicket:id='id$it'></span>" }), "id1", "id2", "id3")
    }

    fun testThreeScalarAccessorTernariesAlsoStayWithinTheChoiceLimit() {
        val body = (1..3).joinToString(" ") { "add(new Label(id(flag$it, \"id$it\")));" }
        matched(report(body, "private String id(boolean choice, String id) { return choice ? id : id; }",
            "boolean flag1, boolean flag2, boolean flag3", (1..3).joinToString("") { "<span wicket:id='id$it'></span>" }),
            "id1", "id2", "id3")
    }

    fun testIfAndTernaryChoicesShareOneExplorationBudget() {
        matched(report("""
            if (a) add(new Label("first")); else add(new Label("first"));
            add(b ? new Label("second") : new Label("second"));
            add(new Label(enabled ? "third" : "third"));
        """, parameters = "boolean a, boolean b, boolean enabled",
            html = "<span wicket:id='first'></span><span wicket:id='second'></span><span wicket:id='third'></span>"),
            "first", "second", "third")
    }

    fun testFourthUnknownTernaryRejectsTheWholeUpgrade() {
        val body = (1..4).joinToString(" ") { "add(new Label(flag$it ? \"id$it\" : \"id$it\"));" }
        unsupported(report(body, parameters = "boolean flag1, boolean flag2, boolean flag3, boolean flag4"))
    }

    fun testNestedTernariesKeepEveryLiteralOrigin() {
        val report = report("add(new Label(a ? (b ? \"title\" : \"title\") : (enabled ? \"title\" : \"title\")));",
            parameters = "boolean a, boolean b, boolean enabled")
        matched(report, "title")
        assertEquals(3, report.children().single().alternativeIdLocations.size)
    }

    fun testUnknownBooleanTernaryCanControlAReviewedIf() {
        val report = report("if (enabled ? true : false) add(new Label(\"title\"));")
        conditional(report, "title")
        assertTrue(report.matching!!.findings.none { it.code in PAIRS })
    }

    fun testNumericPromotionStillUsesTheConditionalExpressionType() {
        matched(report("if ((enabled ? 2147483647 : 2147483647L) + 1 > 2147483647) add(new Label(\"title\"));"), "title")
    }

    fun testNarrowNumericConditionalConversionIsPreserved() {
        matched(report("if ((enabled ? (char) 65 : 65) == 'A') add(new Label(\"title\"));"), "title")
    }

    fun testNullableNumericUnboxingInEitherArmRemainsUnsupported() {
        unsupported(report("if ((enabled ? value : 1) > 0 || true) add(new Label(\"title\"));",
            parameters = "boolean enabled, Integer value"))
    }

    fun testNullableBooleanUnboxingInEitherArmRemainsUnsupported() {
        unsupported(report("if (enabled ? value : true) add(new Label(\"title\"));",
            parameters = "boolean enabled, Boolean value"))
    }

    fun testBoxedConditionsDoNotAcquireAProvenPrimitiveValue() {
        unsupported(report("add(enabled ? new Label(\"title\") : new Label(\"title\"));", parameters = "Boolean enabled"))
    }

    fun testUnknownIdOnOneArmRejectsTheUpgradeWithoutInventingItsBinding() {
        val report = report("add(new Label(\"title\")); add(new Label(enabled ? \"details\" : id));",
            parameters = "boolean enabled, String id")
        assertFalse(report.children().any { it.branchMerged })
        assertTrue(report.matching!!.findings.any { it.code == DiagnosticCode.UNRESOLVED_DYNAMIC_ID })
        assertTrue(report.matching.findings.filter { it.code == DiagnosticCode.MATCHED }.all { finding ->
            report.nodes.single { it.nodeId == finding.javaNodeId }.wicketId == "title"
        })
    }

    fun testNullComponentArmCannotBecomeAMissingPairClaim() {
        unsupported(report("add(enabled ? new Label(\"title\") : null);"))
    }

    fun testUnknownEffectfulConditionCannotBeForked() {
        unsupported(report("add(effect() ? new Label(\"title\") : new Label(\"title\"));",
            "private boolean effect() { removeAll(); return true; }"))
    }

    fun testAnEffectfulPossibleArmPreventsPromotingTheCommonPrefix() {
        unsupported(report("add(new Label(\"title\")); add(new Label(\"details\", enabled ? \"Caption\" : caption()));",
            "private String caption() { removeAll(); return \"Other\"; }"))
    }

    fun testNestedRootCallOutsideATernaryAlsoInvalidatesEarlierFallbackAttachments() {
        unsupported(report("add(new Label(\"title\")); add(new Label(\"details\", caption()));",
            "private String caption() { removeAll(); return \"Other\"; }"))
    }

    fun testQualifiedRootCallInAPossibleArmCannotLeaveAFallbackMatch() {
        unsupported(report("add(new Label(\"title\")); add(new Label(\"details\", enabled ? \"Text\" : this.caption()));",
            "private String caption() { removeAll(); return \"Other\"; }"))
    }

    fun testAKnownConditionStillSkipsAnUnsupportedDeadArm() {
        matched(report("add(true ? new Label(\"title\") : label());",
            "private Label label() { removeAll(); return new Label(\"unused\"); }"), "title")
    }

    fun testMutableConditionReadsRemainUnsupported() {
        unsupported(report("add(flag ? new Label(\"title\") : new Label(\"title\"));", "private boolean flag;"))
    }

    fun testDeepKnownTernaryNestingStillExhaustsTheDepthBound() {
        unsupported(report("add(new Label(" + "true ? (".repeat(15) + "\"title\"" + ") : \"title\"".repeat(15) + "));"))
    }

    fun testComputedAndLiteralTernaryIdsCanAgreeWithoutLosingTheirOriginKinds() {
        val report = report("add(new Label(enabled ? \"ti\" + \"tle\" : \"title\"));")
        matched(report, "title")
        val node = report.children().single()
        assertTrue(node.computedId)
        assertEquals(1, node.computedIdLocations.size)
        assertEquals(2, node.idLocations().size)
    }

    fun testBothJavaOriginsNavigateButCannotRenameOrCompleteOneArm() {
        val java = page("add(enabled ? new Label(\"title\") : new Label(\"title\"));")
        val report = analyze(java)
        for (origin in WicketIdPsi.javaIdElements(project, report.children().single())) {
            assertNotNull(WicketIdPsi.javaId(origin))
            assertNotNull(WicketIdPsi.pair(origin))
            assertNull(WicketIdRenameTarget.at(java, origin.textOffset + 1))
            assertTrue(WicketIdCompletionContributor().candidates(origin).isEmpty())
        }
        assertTrue(problems(java).results.isEmpty())
        val html = WicketIdPsi.pair(WicketIdPsi.javaIdElements(project, report.children().single()).first())!!.attribute
        assertNull(WicketIdRenameTarget.at(html.containingFile, html.textOffset + 1))
    }

    fun testOnlyDefiniteMissingOriginsProduceEditorProblems() {
        val java = page("add(enabled ? new Label(\"missing\") : new Label(\"missing\"));")
        val report = analyze(java)
        val ranges = WicketIdPsi.javaIdElements(project, report.children().single()).map { it.textRange }.toSet()
        assertEquals(ranges, problems(java).results.map { it.psiElement.textRange }.toSet())
    }

    fun testConditionalTernaryIdsHaveNoNativeMissingPairProblems() {
        val java = page("add(new Label(enabled ? \"title\" : \"details\"));")
        val report = analyze(java, "<span wicket:id='title'></span><span wicket:id='details'></span>")
        assertTrue(problems(java).results.isEmpty())
        assertTrue(problems(WicketIdPsi.source(project, report.markupPath!!)!!).results.isEmpty())
        for (node in report.children()) assertNull(WicketIdPsi.pair(WicketIdPsi.javaIdElements(project, node).single()))
    }

    fun testMcpViewsReuseTheExportAndRetainConditionalAlternatives() {
        val java = page("add(new Label(\"title\")); add(new Label(enabled ? \"first\" : \"second\"));")
        analyze(java, "<span wicket:id='title'></span><span wicket:id='first'></span><span wicket:id='second'></span>")
        val before = java.text
        val query = WicketPageAnalysisRequest()
        val snapshot = query.capture(project, "example.ExamplePage", module.name)
        val structure = JsonParser.parseString(query.render(snapshot)).asJsonObject
        assertEquals(JsonParser.parseString(WicketContextExporter().export(snapshot.report)), structure["analysis"])
        val context = JsonParser.parseString(query.render(snapshot, WicketMcpReport.CONTEXT, componentPath = "first")).asJsonObject
        assertTrue(context["context"].asJsonObject["component"].asJsonObject["conditional"].asBoolean)
        assertTrue(context["context"].asJsonObject["markupElements"].asJsonArray.isEmpty)
        assertEquals(module.name, context["target"].asJsonObject["moduleName"].asString)
        val validation = JsonParser.parseString(query.render(snapshot, WicketMcpReport.VALIDATION)).asJsonObject
        assertEquals(structure["analysis"].asJsonObject["matching"].asJsonObject["findings"], validation["validation"].asJsonObject["findings"])
        assertEquals(query.render(snapshot), query.render(query.capture(project, "example.ExamplePage", module.name)))
        assertEquals(before, java.text)
    }

    fun testEditingAnAlternativeSourceInvalidatesTheTernarySnapshot() {
        val extra = source("Extra", "public final class Extra { public static Label label() { return new Label(\"title\"); } }")
        val java = page("add(enabled ? new Label(\"title\") : Extra.label());")
        matched(analyze(java), "title")
        val query = WicketPageAnalysisRequest()
        val old = query.capture(project, "example.ExamplePage")
        assertTrue(old.report.javaSourceFiles.contains(extra.virtualFile.path))
        WriteCommandAction.runWriteCommandAction(project) {
            PsiDocumentManager.getInstance(project).getDocument(extra)!!.setText(extra.text.replace("\"title\"", "\"details\""))
        }
        try { query.requireCurrent(project, old); fail("Expected stale snapshot") }
        catch (failure: PageAnalysisFailure) { assertEquals("STALE_SNAPSHOT", failure.code) }
        PsiDocumentManager.getInstance(project).commitAllDocuments()
        conditional(query.capture(project, "example.ExamplePage").report, "title", "details")
    }

    fun testCancellationIsNotTurnedIntoAFallbackReport() {
        val java = page("add(enabled ? new Label(\"title\") : new Label(\"title\"));")
        val indicator = EmptyProgressIndicator()
        assertThrows(ProcessCanceledException::class.java) {
            ProgressManager.getInstance().runProcess(Runnable {
                indicator.cancel(); WicketPageAnalyzer().analyze(java.classes.single())
            }, indicator)
        }
    }

    fun testPortableDemoKeepsOneCommonBindingAndTwoConditionalIds() {
        val root = Path.of(System.getProperty("wicketlens.test.demo"))
        val file = myFixture.addFileToProject("src/main/java/example/TernarySetupPage.java",
            Files.readString(root.resolve("main/java/example/TernarySetupPage.java"))) as PsiJavaFile
        companion("TernarySetupPage", Files.readString(root.resolve("main/resources/example/TernarySetupPage.html")))
        val report = WicketPageAnalyzer().analyze(file.classes.single())!!
        matched(report, "title")
        conditional(report, "summary", "details")
        assertTrue(report.matching!!.findings.none { it.code == DiagnosticCode.JAVA_ONLY || it.code == DiagnosticCode.MARKUP_ONLY })
    }

    private fun parts() = source("Parts", """
        public final class Parts {
            private final String id;
            public Parts(String id) { this.id = id; }
            public Label label() { return new Label(id); }
        }
    """)
    private fun source(name: String, declaration: String) = myFixture.addFileToProject("src/main/java/example/$name.java",
        "package example; import org.apache.wicket.markup.html.basic.Label; import org.apache.wicket.markup.html.WebMarkupContainer; $declaration")
    private fun page(body: String, members: String = "", parameters: String = "boolean enabled") =
        source("ExamplePage", """
            public class ExamplePage extends org.apache.wicket.markup.html.WebPage {
                public ExamplePage($parameters) { $body }
                $members
            }
        """.trimIndent()) as PsiJavaFile
    private fun analyze(java: PsiJavaFile, html: String = "<span wicket:id='title'></span>"): PageReport {
        companion(html = html)
        return WicketPageAnalyzer().analyze(java.classes.single())!!
    }
    private fun report(body: String, members: String = "", parameters: String = "boolean enabled",
        html: String = "<span wicket:id='title'></span>") = analyze(page(body, members, parameters), html)
    private fun matched(report: PageReport, vararg ids: String) {
        assertEquals(ids.toSet(), report.matching!!.findings.filter { it.code == DiagnosticCode.MATCHED }
            .map { finding -> report.nodes.single { it.nodeId == finding.javaNodeId }.wicketId }.toSet())
    }
    private fun conditional(report: PageReport, vararg ids: String) {
        val nodes = report.children().filter { it.conditional }
        assertEquals(ids.toSet(), nodes.map { it.wicketId }.toSet())
        assertTrue(nodes.all { it.confidence == Confidence.UNRESOLVED && !it.idRenameSafe && it.branchMerged })
        assertTrue(nodes.all { node -> report.matching!!.findings.single { it.javaNodeId == node.nodeId }.code == DiagnosticCode.UNRESOLVED_BRANCH })
    }
    private fun unsupported(report: PageReport) {
        assertFalse(report.children().any { it.branchMerged })
        assertTrue(report.matching!!.findings.toString(), report.matching.findings.none { it.code in PAIRS })
    }
    private fun problems(file: PsiFile) = ProblemsHolder(InspectionManager.getInstance(project), file, false).also {
        WicketUnpairedIdInspection().buildVisitor(it, false).visitFile(file)
    }
    companion object { private val PAIRS = setOf(DiagnosticCode.MATCHED, DiagnosticCode.JAVA_ONLY, DiagnosticCode.MARKUP_ONLY) }
}
