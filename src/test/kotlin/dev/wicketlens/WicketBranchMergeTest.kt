package dev.wicketlens

import com.google.gson.JsonParser
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.psi.*
import dev.wicketlens.analysis.WicketPageAnalyzer
import dev.wicketlens.editor.WicketIdPsi
import dev.wicketlens.editor.WicketIdRenameTarget
import dev.wicketlens.export.WicketContextExporter
import dev.wicketlens.inspection.WicketUnpairedIdInspection
import com.intellij.codeInspection.InspectionManager
import com.intellij.codeInspection.ProblemsHolder
import dev.wicketlens.mcp.*
import dev.wicketlens.model.*
import java.nio.file.Files
import java.nio.file.Path

class WicketBranchMergeTest : WicketProjectPsiTestCase() {
    fun testIdenticalAlternativeAllocationsProduceOneMatchedNodeWithBothOrigins() {
        val java = page("if (enabled) add(new Label(\"title\")); else add(new Label(\"title\"));")
        val report = analyze(java)
        matched(report)
        val node = report.children().single()
        assertTrue(node.branchMerged)
        assertFalse(node.idRenameSafe)
        assertEquals(1, node.alternativeJavaLocations.size)
        assertEquals(1, node.alternativeIdLocations.size)
        assertEquals(2, WicketIdPsi.javaIdElements(project, node).size)
        for (literal in WicketIdPsi.javaIdElements(project, node)) {
            assertNotNull(WicketIdPsi.javaId(literal))
            assertNotNull(WicketIdPsi.pair(literal))
            assertNull(WicketIdRenameTarget.at(java, literal.textOffset + 1))
        }
        val html = WicketIdPsi.pair(WicketIdPsi.javaIdElements(project, node).first())!!.attribute
        assertNull(WicketIdRenameTarget.at(html.containingFile, html.textOffset + 1))
    }

    fun testRuntimeCaptionsDoNotNeedToAgree() = matched(analyze(page(
        "if (enabled) add(new Label(\"title\", \"First\")); else add(new Label(\"title\", \"Second\"));")))

    fun testSameInstanceAttachedOnBothPathsRetainsOneOrigin() {
        val report = analyze(page("Label title = new Label(\"title\"); if (enabled) add(title); else add(title);"))
        matched(report)
        assertTrue(report.children().single().alternativeIdLocations.isEmpty())
    }

    fun testAlternativeFactoryReturnsKeepTheirFreshnessContract() = matched(analyze(page(
        "add(label(enabled));",
        "private Label label(boolean selected) { if (selected) return new Label(\"title\"); return new Label(\"title\"); }")))

    fun testAlternativePrivateFinalFieldAssignmentsCanAgree() = matched(analyze(page(
        "if (enabled) title = new Label(\"title\"); else title = new Label(\"title\"); add(title);",
        "private final Label title;")))

    fun testChildOrderCanDifferWhenTheFullPathsAgree() {
        val report = analyze(page("""
            if (enabled) { add(new Label("title")); add(new Label("subtitle")); }
            else { add(new Label("subtitle")); add(new Label("title")); }
        """), "<span wicket:id='title'></span><span wicket:id='subtitle'></span>")
        matched(report, 2)
    }

    fun testNestedParentsAreComparedByTheirWholePath() {
        val report = analyze(page("""
            if (enabled) {
                WebMarkupContainer left = new WebMarkupContainer("box");
                add(left); left.add(new Label("title"));
            } else {
                WebMarkupContainer right = new WebMarkupContainer("box");
                right.add(new Label("title")); add(right);
            }
        """), "<div wicket:id='box'><span wicket:id='title'></span></div>")
        matched(report, 2)
        val box = report.children().single { it.wicketId == "box" }
        assertEquals(box.nodeId, report.children().single { it.wicketId == "title" }.parentNodeId)
    }

    fun testThreeIndependentChoicesFitTheEightPathLimit() {
        val setup = (1..3).joinToString("\n") { "if (flag$it) add(new Label(\"id$it\")); else add(new Label(\"id$it\"));" }
        matched(analyze(page(setup, parameters = "boolean flag1, boolean flag2, boolean flag3"),
            (1..3).joinToString("") { "<span wicket:id='id$it'></span>" }), 3)
    }

    fun testFourthIndependentChoiceFallsBackWithoutPartialConfirmation() {
        val setup = (1..4).joinToString("\n") { "if (flag$it) add(new Label(\"id$it\")); else add(new Label(\"id$it\"));" }
        uncertain(analyze(page(setup, parameters = "boolean flag1, boolean flag2, boolean flag3, boolean flag4")))
    }

    fun testOptionalBindingRemainsUnresolved() = uncertain(analyze(page("if (enabled) add(new Label(\"title\"));")))
    fun testConflictingIdsRemainUnresolved() = uncertain(analyze(page("if (enabled) add(new Label(\"title\")); else add(new Label(\"other\"));")))
    fun testConflictingTypesRemainUnresolved() = uncertain(analyze(page("if (enabled) add(new Label(\"title\")); else add(new WebMarkupContainer(\"title\"));")))
    fun testOneEffectfulBranchInvalidatesTheWholeExpansion() = uncertain(analyze(page(
        "add(new Label(\"title\")); if (enabled) {} else removeAll();")))
    fun testAnEffectfulConditionCannotBeForked() = uncertain(analyze(page(
        "if (effect()) add(new Label(\"title\")); else add(new Label(\"title\"));",
        "private boolean effect() { removeAll(); return true; }")))
    fun testMutableConditionCannotBeTreatedAsAReviewedUnknown() = uncertain(analyze(page(
        "if (flag) add(new Label(\"title\")); else add(new Label(\"title\"));", "private boolean flag;")))
    fun testDuplicateSiblingIdsDoNotMerge() = uncertain(analyze(page(
        "if (enabled) { add(new Label(\"title\")); add(new Label(\"title\")); } else { add(new Label(\"title\")); add(new Label(\"title\")); }")))

    fun testConflictingParentsCannotMergeEqualIds() {
        val report = analyze(page("""
        WebMarkupContainer box = new WebMarkupContainer("box"); add(box);
        if (enabled) box.add(new Label("title")); else add(new Label("title"));
        """), "<div wicket:id='box'><span wicket:id='title'></span></div>")
        assertTrue(report.children().filter { it.wicketId == "title" }.all { it.conditional })
        assertEquals(1, report.matching!!.findings.count { it.code == DiagnosticCode.MATCHED })
        assertTrue(report.matching.findings.filter { it.code != DiagnosticCode.MATCHED }.all { it.code == DiagnosticCode.UNRESOLVED_BRANCH })
    }

    fun testEarlyReturnCannotHideAnOptionalTail() = uncertain(analyze(page(
        "if (enabled) return; add(new Label(\"title\"));")))

    fun testUnknownTernaryWithAgreeingIdsRetainsBothOrigins() {
        val report = analyze(page("add(new Label(enabled ? \"title\" : \"title\"));"))
        matched(report)
        assertEquals(1, report.children().single().alternativeIdLocations.size)
        assertFalse(report.children().single().idRenameSafe)
    }

    fun testSharedBudgetRejectsLargeAlternativeProofs() {
        val branch = (1..180).joinToString(" ") { "add(new Label(\"id$it\"));" }
        uncertain(analyze(page("if (a) {} else {} if (b) {} else {} if (enabled) { $branch } else { $branch }",
            parameters = "boolean a, boolean b, boolean enabled")))
    }

    fun testJavaOnlyInspectionHighlightsBothAlternativeLiterals() {
        val java = page("if (enabled) add(new Label(\"missing\")); else add(new Label(\"missing\"));")
        val report = analyze(java)
        val holder = ProblemsHolder(InspectionManager.getInstance(project), java, false)
        WicketUnpairedIdInspection().buildVisitor(holder, false).visitFile(java)
        assertEquals(2, holder.results.size)
        assertEquals(2, holder.results.map { it.psiElement.textOffset }.distinct().size)
        assertEquals(WicketIdPsi.javaIdElements(project, report.children().single()).map { it.textRange }.toSet(),
            holder.results.map { it.psiElement.textRange }.toSet())
    }

    fun testCancellationPropagatesInsteadOfPublishingAFallbackGraph() {
        val java = page("if (enabled) add(new Label(\"title\")); else add(new Label(\"title\"));")
        val indicator = com.intellij.openapi.progress.EmptyProgressIndicator()
        try {
            com.intellij.openapi.progress.ProgressManager.getInstance().runProcess(
                Runnable { indicator.cancel(); WicketPageAnalyzer().analyze(java.classes.single()) }, indicator)
            fail("Expected cancellation")
        } catch (_: com.intellij.openapi.progress.ProcessCanceledException) { }
    }

    fun testExportAndMcpContextPreserveBothOriginsWithoutChangingToolOrSchema() {
        val java = page("if (enabled) add(new Label(\"title\")); else add(new Label(\"title\"));")
        analyze(java)
        val query = WicketPageAnalysisRequest()
        val snapshot = query.capture(project, "example.ExamplePage", module.name)
        val structure = JsonParser.parseString(query.render(snapshot)).asJsonObject.getAsJsonObject("analysis")
        assertEquals(JsonParser.parseString(WicketContextExporter().export(snapshot.report)), structure)
        val node = structure.getAsJsonArray("components").single { it.asJsonObject["wicketId"].let { id -> !id.isJsonNull && id.asString == "title" } }.asJsonObject
        assertTrue(node["branchMerged"].asBoolean)
        assertFalse(node["idRenameSafe"].asBoolean)
        assertEquals(2, node.getAsJsonArray("javaLocations").size())
        assertEquals(2, node.getAsJsonArray("idLocations").size())
        val context = JsonParser.parseString(query.render(snapshot, WicketMcpReport.CONTEXT, componentPath = "title")).asJsonObject
        assertEquals(node, context.getAsJsonObject("context")["component"])
        val validation = JsonParser.parseString(query.render(snapshot, WicketMcpReport.VALIDATION)).asJsonObject
        assertEquals(structure["matching"].asJsonObject["findings"], validation["validation"].asJsonObject["findings"])
        assertEquals(1, structure["schemaVersion"].asInt)
        assertEquals(module.name, context["target"].asJsonObject["moduleName"].asString)
    }

    fun testAlternativeSourceIsVersionedAndAnEditRevokesTheMerge() {
        val helper = myFixture.addFileToProject("src/main/java/example/Other.java", """
            package example; public final class Other {
                public static org.apache.wicket.markup.html.basic.Label label() {
                    return new org.apache.wicket.markup.html.basic.Label("title");
                }
            }
        """.trimIndent())
        val java = page("if (enabled) add(new Label(\"title\")); else add(Other.label());")
        matched(analyze(java))
        val before = java.text to helper.text
        val query = WicketPageAnalysisRequest()
        val first = query.capture(project, "example.ExamplePage")
        assertTrue(first.report.javaSourceFiles.contains(helper.virtualFile.path))
        assertEquals(query.render(first), query.render(query.capture(project, "example.ExamplePage")))
        assertEquals(before, java.text to helper.text)
        WriteCommandAction.runWriteCommandAction(project) {
            PsiDocumentManager.getInstance(project).getDocument(helper)!!.setText(helper.text.replace("\"title\"", "\"other\""))
        }
        PsiDocumentManager.getInstance(project).commitAllDocuments()
        try { query.requireCurrent(project, first); fail("Expected stale snapshot") }
        catch (failure: PageAnalysisFailure) { assertEquals("STALE_SNAPSHOT", failure.code) }
        uncertain(query.capture(project, "example.ExamplePage").report)
    }

    fun testPortableDemoHasThreeBindingsAndRetainsAlternativeParentsAndIds() {
        val root = Path.of(System.getProperty("wicketlens.test.demo"))
        val java = myFixture.addFileToProject("src/main/java/example/BranchMergePage.java",
            Files.readString(root.resolve("main/java/example/BranchMergePage.java"))) as PsiJavaFile
        companion("BranchMergePage", Files.readString(root.resolve("main/resources/example/BranchMergePage.html")))
        val report = WicketPageAnalyzer().analyze(java.classes.single())!!
        matched(report, 3)
        assertEquals(2, report.children().count { it.alternativeJavaLocations.isNotEmpty() })
    }

    private fun page(body: String, members: String = "", parameters: String = "boolean enabled") =
        myFixture.addFileToProject("src/main/java/example/ExamplePage.java", """
            package example;
            import org.apache.wicket.markup.html.basic.Label;
            import org.apache.wicket.markup.html.WebMarkupContainer;
            public class ExamplePage extends org.apache.wicket.markup.html.WebPage {
                public ExamplePage($parameters) { $body }
                $members
            }
        """.trimIndent()) as PsiJavaFile

    private fun analyze(java: PsiJavaFile, html: String = "<span wicket:id='title'></span>"): PageReport {
        companion(html = html)
        return WicketPageAnalyzer().analyze(java.classes.single())!!
    }
    private fun matched(report: PageReport, count: Int = 1) {
        assertEquals(report.matching.toString(), count, report.matching!!.findings.count { it.code == DiagnosticCode.MATCHED })
        assertTrue(report.warnings.toString(), report.warnings.none { it.code == DiagnosticCode.UNRESOLVED_SCOPE })
    }
    private fun uncertain(report: PageReport) = assertTrue(report.matching.toString(),
        report.matching!!.findings.none { it.code in setOf(DiagnosticCode.MATCHED, DiagnosticCode.JAVA_ONLY, DiagnosticCode.MARKUP_ONLY) })
}
