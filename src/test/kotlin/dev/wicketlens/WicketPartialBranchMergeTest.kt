package dev.wicketlens

import com.google.gson.JsonParser
import com.intellij.codeInspection.InspectionManager
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.psi.*
import dev.wicketlens.analysis.WicketPageAnalyzer
import dev.wicketlens.editor.*
import dev.wicketlens.export.WicketContextExporter
import dev.wicketlens.inspection.WicketUnpairedIdInspection
import dev.wicketlens.mcp.*
import dev.wicketlens.model.*
import dev.wicketlens.ui.ReportHierarchyBuilder
import java.nio.file.Files
import java.nio.file.Path

class WicketPartialBranchMergeTest : WicketProjectPsiTestCase() {
    fun testCommonTitleMatchesWhileDetailsAndItsMarkupStayConditional() {
        val report = analyze(page("""
            if (enabled) add(new Label("title"));
            else add(new Label("title"), new Label("details"));
        """), "<span wicket:id='title'></span><span wicket:id='details'></span>")
        assertMatched(report, "title")
        assertConditional(report, "details")
        assertEquals(2, findings(report).count { it.code == DiagnosticCode.UNRESOLVED_BRANCH })
        assertTrue(findings(report).none { it.code in MISSING })
        assertEquals(2, report.children().single { it.wicketId == "title" }.let { listOfNotNull(it.idLocation) + it.alternativeIdLocations }.size)
    }

    fun testAnOptionalNamedSiblingDoesNotMaskUnrelatedMissingMarkupSetup() {
        val report = analyze(page("add(new Label(\"title\")); if (enabled) add(new Label(\"details\"));"),
            "<span wicket:id='title'></span><span wicket:id='details'></span><span wicket:id='absent'></span>")
        assertMatched(report, "title")
        val missing = findings(report).single { it.code == DiagnosticCode.MARKUP_ONLY }
        assertEquals("absent", report.markup!!.elements.single { it.nodeId == missing.markupNodeId }.wicketId)
        assertTrue(missing.causes.isEmpty())
        assertConditional(report, "details")
    }

    fun testSetupBeforeAndAfterAConditionalAdditionKeepsItsBindings() {
        val report = analyze(page("add(new Label(\"before\")); if (enabled) add(new Label(\"details\")); add(new Label(\"after\"));"),
            "<span wicket:id='before'></span><span wicket:id='after'></span>")
        assertMatched(report, "before", "after")
        assertConditional(report, "details")
        assertTrue(findings(report).none { it.code in MISSING })
    }

    fun testCommonChildUnderCommonAlternativeParentsCanMatch() {
        val report = analyze(page("""
            if (enabled) {
                WebMarkupContainer box = new WebMarkupContainer("box");
                add(box); box.add(new Label("title"));
            } else {
                WebMarkupContainer box = new WebMarkupContainer("box");
                add(box); box.add(new Label("title"), new Label("details"));
            }
        """), "<div wicket:id='box'><span wicket:id='title'></span><span wicket:id='details'></span></div>")
        assertMatched(report, "box", "title")
        assertConditional(report, "details")
    }

    fun testAnOptionalParentKeepsEveryDescendantConditional() {
        val report = analyze(page("""
            add(new Label("title"));
            if (enabled) {
                WebMarkupContainer box = new WebMarkupContainer("box"); add(box);
                box.add(new Label("child"));
            }
        """), "<span wicket:id='title'></span><div wicket:id='box'><span wicket:id='child'></span></div>")
        assertMatched(report, "title")
        assertConditional(report, "box", "child")
        assertTrue(findings(report).none { it.code in MISSING })
    }

    fun testTypeAlternativesStaySeparateAndDoNotEraseAnIndependentBinding() {
        val report = analyze(page("""
            add(new Label("title"));
            if (enabled) add(new Label("choice")); else add(new WebMarkupContainer("choice"));
        """), "<span wicket:id='title'></span><div wicket:id='choice'></div>")
        assertMatched(report, "title")
        assertConditional(report, "choice")
        assertEquals(setOf("org.apache.wicket.markup.html.basic.Label", "org.apache.wicket.markup.html.WebMarkupContainer"),
            report.children().filter { it.wicketId == "choice" }.map { it.componentType }.toSet())
        assertEquals(report.nodes.size, report.nodes.map { it.nodeId }.distinct().size)
        assertTrue(findings(report).none { it.code in MISSING })
    }

    fun testChildrenDoNotMergeAcrossParentsWithDifferentTypes() {
        val report = analyze(page("""
            add(new Label("title"));
            if (enabled) {
                WebMarkupContainer box = new WebMarkupContainer("box"); add(box); box.add(new Label("child"));
            } else {
                org.apache.wicket.markup.html.form.Form<Void> box = new org.apache.wicket.markup.html.form.Form<>("box");
                add(box); box.add(new Label("child"));
            }
        """), "<span wicket:id='title'></span><div wicket:id='box'><span wicket:id='child'></span></div>")
        assertMatched(report, "title")
        assertConditional(report, "box", "child")
        assertEquals(2, report.children().count { it.wicketId == "child" })
        assertEquals(2, report.children().filter { it.wicketId == "child" }.map { it.parentNodeId }.distinct().size)
    }

    fun testOneAllocationMovingBetweenParentsGetsDistinctSnapshotIdentities() {
        val report = analyze(page("""
            WebMarkupContainer left = new WebMarkupContainer("left");
            WebMarkupContainer right = new WebMarkupContainer("right"); add(left, right);
            Label title = new Label("title");
            if (enabled) left.add(title); else right.add(title);
        """), "<div wicket:id='left'><span wicket:id='title'></span></div><div wicket:id='right'><span wicket:id='title'></span></div>")
        assertMatched(report, "left", "right")
        val alternatives = report.children().filter { it.wicketId == "title" }
        assertEquals(2, alternatives.size)
        assertTrue(alternatives.all { it.conditional })
        assertEquals(1, alternatives.map { it.javaLocation }.distinct().size)
        assertEquals(2, alternatives.map { it.nodeId }.distinct().size)
        assertEquals(2, alternatives.map { it.parentNodeId }.distinct().size)
        assertTrue(findings(report).none { it.code in MISSING })
    }

    fun testSameIdsUnderDifferentParentsDoNotShareTheirUncertainty() {
        val report = analyze(page("""
            WebMarkupContainer left = new WebMarkupContainer("left");
            WebMarkupContainer right = new WebMarkupContainer("right"); add(left, right);
            left.add(new Label("title"));
            if (enabled) right.add(new Label("title"));
        """), "<div wicket:id='left'><span wicket:id='title'></span></div><div wicket:id='right'><span wicket:id='title'></span></div>")
        assertEquals(3, findings(report).count { it.code == DiagnosticCode.MATCHED })
        val titleNodes = report.children().filter { it.wicketId == "title" }
        assertEquals(1, titleNodes.count { it.conditional })
        assertEquals(1, titleNodes.count { it.confidence == Confidence.CONFIRMED })
    }

    fun testEarlyReturnLeavesOnlyTheCommonPrefixConfirmed() {
        val report = analyze(page("add(new Label(\"title\")); if (enabled) return; add(new Label(\"details\"));"),
            "<span wicket:id='title'></span><span wicket:id='details'></span>")
        assertMatched(report, "title")
        assertConditional(report, "details")
    }

    fun testThreeChoicesKeepOnlyTheirOptionalChildrenUncertain() {
        val report = analyze(page("""
            add(new Label("title"));
            if (a) add(new Label("a"));
            if (b) add(new Label("b"));
            if (enabled) add(new Label("c"));
        """, parameters = "boolean a, boolean b, boolean enabled"))
        assertMatched(report, "title")
        assertConditional(report, "a", "b", "c")
    }

    fun testOneUnsupportedBranchStillPreventsPromotingTheCommonPrefix() {
        val report = analyze(page("add(new Label(\"title\")); if (enabled) add(new Label(\"details\")); else removeAll();"))
        assertTrue(findings(report).none { it.code in MISSING || it.code == DiagnosticCode.MATCHED })
        assertFalse(report.children().any { it.branchMerged })
    }

    fun testUnknownIdOnOnePathCannotBeTreatedAsANamedOptionalSibling() {
        val report = analyze(page("add(new Label(\"title\")); if (enabled) add(new Label(id));", parameters = "boolean enabled, String id"))
        assertTrue(findings(report).none { it.code in MISSING || it.code == DiagnosticCode.MATCHED })
    }

    fun testDuplicateSiblingIdsOnOnePathStillRejectTheWholeUpgrade() {
        val report = analyze(page("""
            add(new Label("title"));
            if (enabled) { add(new Label("details")); add(new WebMarkupContainer("details")); }
        """))
        assertFalse(report.children().any { it.branchMerged })
        assertTrue(findings(report).none { it.code == DiagnosticCode.MATCHED })
    }

    fun testTernaryInAnOptionalBranchKeepsTheWholeVariantConditional() {
        val report = analyze(page("""
            add(new Label("title"));
            if (enabled) add(new Label(other ? "details" : "details"));
        """, parameters = "boolean enabled, boolean other"))
        assertMatched(report, "title")
        assertConditional(report, "details")
    }

    fun testUnionOfAlternativesHasItsOwnComponentBound() {
        val left = (1..130).joinToString(" ") { "add(new Label(\"left$it\"));" }
        val right = (1..130).joinToString(" ") { "add(new Label(\"right$it\"));" }
        val report = analyze(page("if (enabled) { $left } else { $right }"))
        assertFalse(report.children().any { it.branchMerged })
        assertTrue(findings(report).none { it.code in MISSING || it.code == DiagnosticCode.MATCHED })
    }

    fun testOnlyDefiniteMissingBindingsProduceEditorProblems() {
        val java = page("""
            if (enabled) add(new Label("missing"));
            else add(new Label("missing"), new Label("details"));
        """)
        val report = analyze(java, "<span wicket:id='details'></span>")
        val holder = problems(java)
        assertEquals(2, holder.results.size)
        assertEquals(WicketIdPsi.javaIdElements(project, report.children().single { it.wicketId == "missing" }).map { it.textRange }.toSet(),
            holder.results.map { it.psiElement.textRange }.toSet())
        val html = WicketIdPsi.source(project, report.markupPath!!)!!
        assertTrue(problems(html).results.isEmpty())
    }

    fun testConditionalOriginsDoNotAcquirePairsRenameOrCompletion() {
        val java = page("add(new Label(\"title\")); if (enabled) add(new Label(\"details\"));")
        val report = analyze(java, "<span wicket:id='title'></span><span wicket:id='details'></span>")
        for (node in report.children()) {
            val literal = WicketIdPsi.javaIdElements(project, node).single()
            assertNull(WicketIdRenameTarget.at(java, literal.textOffset + 1))
            assertTrue(WicketIdCompletionContributor().candidates(literal).isEmpty())
            if (node.conditional) {
                assertNull(WicketIdPsi.pair(literal))
                assertNotNull(WicketIdPsi.javaId(literal))
            } else assertNotNull(WicketIdPsi.pair(literal))
        }
        val html = WicketIdPsi.source(project, report.markupPath!!)!!
        assertTrue(problems(java).results.isEmpty())
        assertTrue(problems(html).results.isEmpty())
    }

    fun testSidebarUsesTheSharedConditionalFinding() {
        val report = analyze(page("add(new Label(\"title\")); if (enabled) add(new Label(\"details\"));"))
        val entries = ReportHierarchyBuilder().build(report)
        assertEquals(DiagnosticCode.MATCHED, entries.single { it.name == "title" }.status)
        val entry = entries.single { it.name == "details" }
        assertEquals(DiagnosticCode.UNRESOLVED_BRANCH, entry.status)
        assertTrue(entry.details.contains("conditional"))
    }

    fun testMcpStructureContextAndValidationSharePartialGraphFacts() {
        val java = page("add(new Label(\"title\")); if (enabled) add(new Label(\"details\"));")
        analyze(java, "<span wicket:id='title'></span><span wicket:id='details'></span>")
        val before = java.text
        val query = WicketPageAnalysisRequest()
        val snapshot = query.capture(project, "example.ExamplePage", module.name)
        val structure = JsonParser.parseString(query.render(snapshot)).asJsonObject
        val analysis = structure.getAsJsonObject("analysis")
        assertEquals(JsonParser.parseString(WicketContextExporter().export(snapshot.report)), analysis)
        val context = JsonParser.parseString(query.render(snapshot, WicketMcpReport.CONTEXT, componentPath = "details")).asJsonObject
        val node = context.getAsJsonObject("context").getAsJsonObject("component")
        assertTrue(node["conditional"].asBoolean)
        assertTrue(node["branchMerged"].asBoolean)
        assertFalse(node["idRenameSafe"].asBoolean)
        assertEquals("UNRESOLVED", node["confidence"].asString)
        assertEquals("UNRESOLVED_BRANCH", node["status"].asString)
        assertEquals(1, node.getAsJsonArray("javaLocations").size())
        assertTrue(context["context"].asJsonObject["markupElements"].asJsonArray.isEmpty)
        val validation = JsonParser.parseString(query.render(snapshot, WicketMcpReport.VALIDATION)).asJsonObject["validation"].asJsonObject
        assertEquals(analysis["matching"].asJsonObject["findings"], validation["findings"])
        assertEquals(1, validation["summary"].asJsonObject["findingsByCode"].asJsonObject["MATCHED"].asInt)
        assertEquals(module.name, context["target"].asJsonObject["moduleName"].asString)
        assertEquals(before, java.text)
        assertEquals(query.render(snapshot), query.render(query.capture(project, "example.ExamplePage", module.name)))
    }

    fun testAmbiguousTypeAlternativesRequireAMcpNodeId() {
        val java = page("if (enabled) add(new Label(\"title\")); else add(new WebMarkupContainer(\"title\"));")
        analyze(java)
        val query = WicketPageAnalysisRequest()
        val snapshot = query.capture(project, "example.ExamplePage")
        try { query.render(snapshot, WicketMcpReport.CONTEXT, componentPath = "title"); fail("Expected ambiguous alternatives") }
        catch (failure: PageAnalysisFailure) { assertEquals("AMBIGUOUS_COMPONENT", failure.code) }
        for (node in snapshot.report.children()) {
            val result = JsonParser.parseString(query.render(snapshot, WicketMcpReport.CONTEXT, nodeId = node.nodeId)).asJsonObject
            assertTrue(result["context"].asJsonObject["component"].asJsonObject["conditional"].asBoolean)
        }
    }

    fun testAlternativeSourceEditInvalidatesThePartialSnapshot() {
        val helper = myFixture.addFileToProject("src/main/java/example/Extras.java", """
            package example; public final class Extras {
                public static org.apache.wicket.markup.html.basic.Label details() {
                    return new org.apache.wicket.markup.html.basic.Label("details");
                }
            }
        """.trimIndent())
        val java = page("add(new Label(\"title\")); if (enabled) add(Extras.details());")
        analyze(java)
        val query = WicketPageAnalysisRequest()
        val old = query.capture(project, "example.ExamplePage")
        assertTrue(old.report.javaSourceFiles.contains(helper.virtualFile.path))
        WriteCommandAction.runWriteCommandAction(project) {
            PsiDocumentManager.getInstance(project).getDocument(helper)!!.setText(helper.text.replace("\"details\"", "\"changed\""))
        }
        try { query.capture(project, "example.ExamplePage"); fail("Expected uncommitted document rejection") }
        catch (failure: PageAnalysisFailure) { assertEquals("UNCOMMITTED_DOCUMENTS", failure.code) }
        try { query.requireCurrent(project, old); fail("Expected stale snapshot") }
        catch (failure: PageAnalysisFailure) { assertEquals("STALE_SNAPSHOT", failure.code) }
        PsiDocumentManager.getInstance(project).commitAllDocuments()
        val fresh = query.capture(project, "example.ExamplePage")
        assertMatched(fresh.report, "title")
        assertConditional(fresh.report, "changed")
        assertFalse(fresh.report.children().any { it.wicketId == "details" })
    }

    fun testMcpResponseBoundIncludesConditionalOrigins() {
        val java = page("if (enabled) add(new Label(\"title\"));")
        analyze(java)
        val query = WicketPageAnalysisRequest()
        val snapshot = query.capture(project, "example.ExamplePage")
        val oversized = snapshot.copy(report = snapshot.report.copy(nodes = snapshot.report.nodes.map {
            if (it.conditional) it.copy(alternativeJavaLocations = listOf(SourceLocation("界".repeat(180_000), 0))) else it
        }))
        try { query.render(oversized); fail("Expected size bound") }
        catch (failure: PageAnalysisFailure) { assertEquals("RESPONSE_TOO_LARGE", failure.code) }
    }

    fun testPortableDemoHasOneDefiniteBindingAndConditionalDetails() {
        val root = Path.of(System.getProperty("wicketlens.test.demo"))
        val source = myFixture.addFileToProject("src/main/java/example/PartialBranchPage.java",
            Files.readString(root.resolve("main/java/example/PartialBranchPage.java"))) as PsiJavaFile
        companion("PartialBranchPage", Files.readString(root.resolve("main/resources/example/PartialBranchPage.html")))
        val report = WicketPageAnalyzer().analyze(source.classes.single())!!
        assertMatched(report, "title")
        assertConditional(report, "details")
        assertTrue(findings(report).none { it.code in MISSING })
    }

    private fun page(body: String, parameters: String = "boolean enabled") =
        myFixture.addFileToProject("src/main/java/example/ExamplePage.java", """
            package example;
            import org.apache.wicket.markup.html.basic.Label;
            import org.apache.wicket.markup.html.WebMarkupContainer;
            public class ExamplePage extends org.apache.wicket.markup.html.WebPage {
                public ExamplePage($parameters) { $body }
            }
        """.trimIndent()) as PsiJavaFile
    private fun analyze(java: PsiJavaFile, html: String = "<span wicket:id='title'></span>"): PageReport {
        companion(html = html)
        return WicketPageAnalyzer().analyze(java.classes.single())!!
    }
    private fun findings(report: PageReport) = report.matching!!.findings
    private fun assertMatched(report: PageReport, vararg ids: String) {
        assertEquals(ids.toSet(), findings(report).filter { it.code == DiagnosticCode.MATCHED }
            .map { finding -> report.nodes.single { it.nodeId == finding.javaNodeId }.wicketId }.toSet())
    }
    private fun assertConditional(report: PageReport, vararg ids: String) {
        val nodes = report.children().filter { it.wicketId in ids }
        assertEquals(ids.toSet(), nodes.map { it.wicketId }.toSet())
        assertTrue(nodes.all { it.conditional && it.branchMerged && it.confidence == Confidence.UNRESOLVED && !it.idRenameSafe })
        assertTrue(nodes.all { node -> findings(report).single { it.javaNodeId == node.nodeId }.code == DiagnosticCode.UNRESOLVED_BRANCH })
    }
    private fun problems(file: PsiFile) = ProblemsHolder(InspectionManager.getInstance(project), file, false).also {
        WicketUnpairedIdInspection().buildVisitor(it, false).visitFile(file)
    }
    companion object { private val MISSING = setOf(DiagnosticCode.JAVA_ONLY, DiagnosticCode.MARKUP_ONLY) }
}
