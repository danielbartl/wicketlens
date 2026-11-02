package dev.wicketlens

import com.google.gson.JsonParser
import com.intellij.codeInspection.InspectionManager
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressManager
import com.intellij.psi.*
import dev.wicketlens.analysis.*
import dev.wicketlens.editor.*
import dev.wicketlens.export.WicketContextExporter
import dev.wicketlens.inspection.WicketUnpairedIdInspection
import dev.wicketlens.mcp.*
import dev.wicketlens.model.*
import java.nio.file.Files
import java.nio.file.Path

class WicketQueueBranchMergeTest : WicketProjectPsiTestCase() {
    fun testBothBranchesCanQueueTheSameBindingWithBothOrigins() {
        val report = report("if (flag) queue(new Label(\"title\")); else queue(new Label(\"title\"));")
        matched(report, "title")
        val node = report.children().single()
        assertEquals(report.rootNodeId, node.queuedInNodeId)
        assertEquals(report.rootNodeId, node.parentNodeId)
        assertFalse(node.conditional)
        assertEquals(1, node.alternativeIdLocations.size)
        assertTrue(node.diagnostics.isEmpty())
    }

    fun testQueueParentComesFromMarkupRatherThanItsDeclaringScope() {
        val report = report("""
            queue(new WebMarkupContainer("form"));
            if (flag) queue(new Label("title")); else queue(new Label("title"));
        """, "<div wicket:id='form'><span wicket:id='title'></span></div>")
        matched(report, "form", "title")
        val title = report.node("title")
        assertEquals(report.rootNodeId, title.queuedInNodeId)
        assertEquals(report.node("form").nodeId, title.parentNodeId)
    }

    fun testOptionalQueuedSiblingKeepsItsPresenceUnresolvedAfterPlacement() {
        val report = report("queue(new Label(\"title\")); if (flag) queue(new Label(\"details\"));",
            "<span wicket:id='title'></span><span wicket:id='details'></span>")
        matched(report, "title")
        conditional(report, "details")
        assertEquals(report.rootNodeId, report.node("details").parentNodeId)
        assertTrue(report.node("details").diagnostics.none { it.code == DiagnosticCode.UNRESOLVED_QUEUE })
        assertTrue(findings(report).none { it.code in MISSING })
    }

    fun testOptionalDirectSiblingDoesNotPreventCommonQueuePlacement() =
        matched(report("queue(new Label(\"title\")); if (flag) add(new Label(\"details\"));",
            "<span wicket:id='title'></span><span wicket:id='details'></span>"), "title")

    fun testComplementaryChecksShareOneQueuedBinding() =
        matched(report("if (flag) queue(new Label(\"title\")); if (!flag) queue(new Label(\"title\"));"), "title")

    fun testTernaryQueueArgumentRetainsMixedComputedAndLiteralOrigins() {
        val report = report("queue(flag ? new Label(\"ti\" + \"tle\") : new Label(\"title\"));")
        matched(report, "title")
        val node = report.node("title")
        assertTrue(node.computedId)
        assertEquals(1, node.computedIdLocations.size)
        assertEquals(2, node.idLocations().size)
    }

    fun testConditionalDirectShadowCannotHideAnotherQueueCandidate() {
        val report = report("""
            WebMarkupContainer box = new WebMarkupContainer("box"); add(box);
            queue(new Label("name")); if (flag) box.add(new Label("name"));
        """, "<span wicket:id='name'></span><div wicket:id='box'><span wicket:id='name'></span></div>")
        unresolvedQueue(report, report.children().single { it.wicketId == "name" && it.queuedInNodeId == report.rootNodeId })
        matched(report, "box")
    }

    fun testConditionalNearerQueueCannotHideAnotherRootQueueCandidate() {
        val report = report("""
            WebMarkupContainer box = new WebMarkupContainer("box"); add(box);
            queue(new Label("name")); if (flag) box.queue(new Label("name"));
        """, "<span wicket:id='name'></span><div wicket:id='box'><span wicket:id='name'></span></div>")
        unresolvedQueue(report, report.children().single { it.wicketId == "name" && it.queuedInNodeId == report.rootNodeId })
        val inner = report.children().single { it.wicketId == "name" && it.queuedInNodeId != report.rootNodeId }
        assertTrue(inner.conditional)
        assertEquals(report.node("box").nodeId, inner.parentNodeId)
        assertEquals(Confidence.UNRESOLVED, inner.confidence)
    }

    fun testConditionalContainerTypesCannotHideCompetingDescendantPlacements() {
        val report = report("""
            if (flag) add(new WebMarkupContainer("box"));
            else add(new org.apache.wicket.markup.html.form.Form<Void>("box"));
            queue(new Label("name"));
        """, "<span wicket:id='name'></span><div wicket:id='box'><span wicket:id='name'></span></div>")
        unresolvedQueue(report, report.node("name"))
        assertTrue(findings(report).none { it.code == DiagnosticCode.MATCHED })
    }

    fun testDifferentParentsAcrossFeasiblePathsDoNotBecomeOneConfirmedPlacement() {
        val report = report("""
            if (flag) add(new WebMarkupContainer("left")); else add(new WebMarkupContainer("right"));
            queue(new Label("name")); add(new Label("footer"));
        """, "<div wicket:id='left'><span wicket:id='name'></span></div><div wicket:id='right'><span wicket:id='name'></span></div><span wicket:id='footer'></span>")
        matched(report, "footer")
        unresolvedQueue(report, report.node("name"))
        assertNull(report.node("name").parentNodeId)
    }

    fun testPlacementExistingOnOnlyOnePathCannotConfirmAMandatoryQueue() {
        val report = report("if (flag) add(new WebMarkupContainer(\"box\")); queue(new Label(\"name\"));",
            "<div wicket:id='box'><span wicket:id='name'></span></div>")
        unresolvedQueue(report, report.node("name"))
        assertTrue(findings(report).none { it.code == DiagnosticCode.MATCHED })
    }

    fun testOptionalQueuedParentKeepsAllDescendantsConditional() {
        val report = report("""
            if (flag) {
                WebMarkupContainer box = new WebMarkupContainer("box");
                box.queue(new Label("name")); queue(box);
            }
        """, "<div wicket:id='box'><span wicket:id='name'></span></div>")
        conditional(report, "box", "name")
        assertEquals(report.rootNodeId, report.node("box").parentNodeId)
        assertEquals(report.node("box").nodeId, report.node("name").parentNodeId)
        assertTrue(findings(report).none { it.code in MISSING || it.code == DiagnosticCode.MATCHED })
    }

    fun testCommonQueuedParentCanContainAnOptionalQueue() {
        val report = report("""
            WebMarkupContainer box = new WebMarkupContainer("box"); queue(box);
            if (flag) box.queue(new Label("name"));
        """, "<div wicket:id='box'><span wicket:id='name'></span></div>")
        matched(report, "box")
        conditional(report, "name")
        assertEquals(report.node("box").nodeId, report.node("name").parentNodeId)
        assertEquals(report.node("box").nodeId, report.node("name").queuedInNodeId)
    }

    fun testDirectChildrenOfAQueuedContainerKeepTheirActualParentChain() {
        val report = report("""
            add(new WebMarkupContainer("outer"));
            WebMarkupContainer box = new WebMarkupContainer("box"); box.add(new Label("name"));
            if (flag) queue(box); else queue(box);
        """, "<div wicket:id='outer'><div wicket:id='box'><span wicket:id='name'></span></div></div>")
        matched(report, "outer", "box", "name")
        assertEquals(report.node("outer").nodeId, report.node("box").parentNodeId)
        assertEquals(report.node("box").nodeId, report.node("name").parentNodeId)
    }

    fun testRepeatedIdsInSeparateQueueScopesRemainDistinct() {
        val report = report("""
            WebMarkupContainer left = new WebMarkupContainer("left"), right = new WebMarkupContainer("right");
            left.queue(flag ? new Label("name") : new Label("name"));
            right.queue(new Label("name")); queue(left, right);
        """, "<div wicket:id='left'><span wicket:id='name'></span></div><div wicket:id='right'><span wicket:id='name'></span></div>")
        assertEquals(4, findings(report).count { it.code == DiagnosticCode.MATCHED })
        assertEquals(2, report.children().filter { it.wicketId == "name" }.map { it.parentNodeId }.distinct().size)
    }

    fun testChangingTheQueueReceiverKeepsEachScopeAlternativeConditional() {
        val report = report("""
            WebMarkupContainer left = new WebMarkupContainer("left"), right = new WebMarkupContainer("right"); add(left, right);
            Label label = new Label("name"); (flag ? left : right).queue(label);
        """, "<div wicket:id='left'><span wicket:id='name'></span></div><div wicket:id='right'><span wicket:id='name'></span></div>")
        matched(report, "left", "right")
        conditional(report, "name")
        val names = report.children().filter { it.wicketId == "name" }
        assertEquals(2, names.size)
        assertEquals(2, names.map { it.queuedInNodeId }.distinct().size)
        assertTrue(names.all { it.parentNodeId == it.queuedInNodeId })
    }

    fun testExclusiveAddAndQueueAlternativesRemainSeparateAndConditional() {
        val report = report("Label title = new Label(\"title\"); if (flag) add(title); else queue(title);")
        assertEquals(2, report.children().size)
        conditional(report, "title")
        assertTrue(report.children().all { it.parentNodeId == report.rootNodeId })
        assertEquals(1, report.children().count { it.queuedInNodeId != null })
        assertTrue(findings(report).none { it.code in MISSING || it.code == DiagnosticCode.MATCHED })
    }

    fun testNoMarkupCandidateStaysUnresolvedQueueRatherThanJavaOnly() {
        val report = report("if (flag) queue(new Label(\"missing\")); else queue(new Label(\"missing\"));", "<div></div>")
        unresolvedQueue(report, report.node("missing"))
        assertTrue(findings(report).none { it.code in MISSING })
    }

    fun testAmbiguousMarkupCandidatesStayUnresolvedOnEveryPath() {
        val report = report("""
            add(new WebMarkupContainer("left"), new WebMarkupContainer("right"));
            queue(flag ? new Label("name") : new Label("name"));
        """, "<div wicket:id='left'><span wicket:id='name'></span></div><div wicket:id='right'><span wicket:id='name'></span></div>")
        matched(report, "left", "right")
        unresolvedQueue(report, report.node("name"))
        assertTrue(findings(report).none { it.code in MISSING })
    }

    fun testQueueCannotUseAHeaderOccurrenceAsAPlacement() {
        val report = report("queue(flag ? new Label(\"title\") : new Label(\"title\"));",
            "<html><head><title wicket:id='title'></title></head><body></body></html>")
        unresolvedQueue(report, report.node("title"))
        assertTrue(findings(report).none { it.code == DiagnosticCode.MATCHED })
    }

    fun testBranchQueueDoesNotCrossPanelMarkupBoundaries() {
        val report = report("""
            queue(new org.apache.wicket.markup.html.panel.Panel("panel"));
            if (flag) queue(new Label("name")); else queue(new Label("name"));
        """, "<div wicket:id='panel'><span wicket:id='name'></span></div>")
        matched(report, "panel")
        unresolvedQueue(report, report.node("name"))
        assertFalse(report.markup!!.elements.single { it.wicketId == "name" }.participatesInComponentTree)
    }

    fun testBranchQueueDoesNotCrossACustomQueueRegion() {
        source("Region", """
            public class Region extends org.apache.wicket.markup.html.WebMarkupContainer implements org.apache.wicket.IQueueRegion {
                public Region(String id) { super(id); }
            }
        """)
        val report = report("add(new Region(\"region\")); queue(flag ? new Label(\"name\") : new Label(\"name\"));",
            "<div wicket:id='region'><span wicket:id='name'></span></div>")
        matched(report, "region")
        unresolvedQueue(report, report.node("name"))
    }

    fun testIncompleteMarkupDoesNotConfirmAQueuePlacement() {
        val report = report("queue(flag ? new Label(\"title\") : new Label(\"title\"));", "<div wicket:id='title'")
        unresolvedQueue(report, report.node("title"))
        assertTrue(findings(report).none { it.code in MISSING || it.code == DiagnosticCode.MATCHED })
    }

    fun testJavaOnlyBranchDiscoveryNeverClaimsQueueAttachment() {
        val file = page("if (flag) queue(new Label(\"title\")); else queue(new Label(\"title\"));")
        val report = WicketJavaAnalyzer().analyze(file.classes.single())!!
        val title = report.node("title")
        assertEquals(2, report.branchPathCount)
        assertNull(title.parentNodeId)
        assertEquals(Confidence.UNRESOLVED, title.confidence)
        assertEquals(report.rootNodeId, title.queuedInNodeId)
    }

    fun testUnknownIdsStillRejectTheEntireBranchUpgrade() =
        noUpgrade(report("queue(flag ? new Label(id) : new Label(\"title\"));", parameters = "boolean flag, String id"))

    fun testPossibleRootEffectsStillRejectTheEntireBranchUpgrade() =
        noUpgrade(report("if (flag) queue(new Label(\"title\")); else { removeAll(); queue(new Label(\"title\")); }"))

    fun testRepeatedQueueingInOneFeasiblePathRemainsUnsupported() =
        noUpgrade(report("Label title = new Label(\"title\"); if (flag) { queue(title); queue(title); } else queue(title);"))

    fun testMixedAddQueueInOneFeasiblePathRemainsUnsupported() =
        noUpgrade(report("Label title = new Label(\"title\"); if (flag) { add(title); queue(title); } else queue(title);"))

    fun testFourthIndependentChoiceStillRejectsTheBranchUpgrade() {
        val body = (1..4).joinToString(" ") { "queue(flag$it ? new Label(\"id$it\") : new Label(\"id$it\"));" }
        noUpgrade(report(body, parameters = "boolean flag1, boolean flag2, boolean flag3, boolean flag4"))
    }

    fun testOptionalQueuedRepeaterAndItsTemplateRemainConditional() {
        val report = report("""
            if (flag) queue(new org.apache.wicket.markup.html.list.ListView<String>("rows", java.util.List.of("A")) {
                @Override protected void populateItem(org.apache.wicket.markup.html.list.ListItem<String> item) { item.add(new Label("name")); }
            });
        """, "<div wicket:id='rows'><span wicket:id='name'></span></div>")
        conditional(report, "rows", "name")
        assertTrue(report.nodes.single { it.templateItem }.conditional)
        assertTrue(findings(report).none { it.code in MISSING || it.code == DiagnosticCode.MATCHED })
    }

    fun testClosedHelpersAndFieldsKeepQueueScopeAndLiteralOrigins() {
        val report = report("setup(flag);", "<div wicket:id='box'><span wicket:id='name'></span></div>", """
            private final WebMarkupContainer box = new WebMarkupContainer("box");
            private void setup(boolean enabled) {
                queue(box);
                if (enabled) box.queue(new Label("name")); else box.queue(new Label("name"));
            }
        """)
        matched(report, "box", "name")
        assertEquals(report.node("box").nodeId, report.node("name").queuedInNodeId)
    }

    fun testUnplacedQueueUncertaintyStaysInsideItsKnownDeclaringScope() {
        val report = report("""
            WebMarkupContainer form = new WebMarkupContainer("form");
            form.add(new WebMarkupContainer("left"), new WebMarkupContainer("right")); add(form);
            form.queue(flag ? new Label("name") : new Label("name"));
        """, "<div wicket:id='form'><div wicket:id='left'><span wicket:id='name'></span></div><div wicket:id='right'><span wicket:id='name'></span></div></div><span wicket:id='outside'></span>")
        unresolvedQueue(report, report.node("name"))
        val outside = report.markup!!.elements.single { it.wicketId == "outside" }
        assertEquals(DiagnosticCode.MARKUP_ONLY, findings(report).single { it.markupNodeId == outside.nodeId }.code)
    }

    fun testDescendantsOfAnUnplacedQueuedContainerKeepDeclaringScopeUncertainty() {
        val report = report("""
            WebMarkupContainer form = new WebMarkupContainer("form"), box = new WebMarkupContainer("box");
            box.add(new Label("name")); add(form); form.queue(box);
            if (flag) {} else {}
        """, "<div wicket:id='form'><span wicket:id='maybe'></span></div><span wicket:id='outside'></span>")
        val outside = report.markup!!.elements.single { it.wicketId == "outside" }
        assertEquals(DiagnosticCode.MARKUP_ONLY, findings(report).single { it.markupNodeId == outside.nodeId }.code)
        assertTrue(findings(report).filter { it.javaNodeId == report.node("name").nodeId }.all { it.code != DiagnosticCode.JAVA_ONLY })
    }

    fun testCommonQueueOriginsNavigateButMergedRenameAndCompletionStayGuarded() {
        val file = page("queue(flag ? new Label(\"title\") : new Label(\"title\"));")
        val report = analyze(file)
        for (literal in WicketIdPsi.javaIdElements(project, report.node("title"))) {
            assertNotNull(WicketIdPsi.javaId(literal))
            val pair = WicketIdPsi.pair(literal)!!
            assertNull(WicketIdRenameTarget.at(file, literal.textOffset + 1))
            assertNull(WicketIdRenameTarget.at(pair.attribute.containingFile, pair.attribute.textOffset + 1))
            assertTrue(WicketIdCompletionContributor().candidates(literal).isEmpty())
        }
        assertTrue(problems(file).results.isEmpty())
    }

    fun testOptionalQueuesNeverProduceNativeMissingPairProblemsOrPairs() {
        val file = page("queue(new Label(\"title\")); if (flag) queue(new Label(\"details\"));")
        val report = analyze(file, "<span wicket:id='title'></span><span wicket:id='details'></span>")
        val details = report.node("details")
        assertNull(WicketIdPsi.pair(WicketIdPsi.javaIdElements(project, details).single()))
        assertTrue(problems(file).results.isEmpty())
        assertTrue(problems(WicketIdPsi.source(project, report.markupPath!!)!!).results.isEmpty())
    }

    fun testMcpViewsUsePlacedParentsAndPreserveQueueDeclarationScope() {
        val file = page("""
            queue(new WebMarkupContainer("box"));
            queue(flag ? new Label("title") : new Label("title"));
            if (flag) queue(new Label("details"));
        """)
        analyze(file, "<div wicket:id='box'><span wicket:id='title'></span><span wicket:id='details'></span></div>")
        val before = file.text
        val query = WicketPageAnalysisRequest()
        val snapshot = query.capture(project, "example.ExamplePage", module.name)
        val structure = JsonParser.parseString(query.render(snapshot)).asJsonObject
        assertEquals(JsonParser.parseString(WicketContextExporter().export(snapshot.report)), structure["analysis"])
        val context = JsonParser.parseString(query.render(snapshot, WicketMcpReport.CONTEXT, componentPath = "box:title")).asJsonObject
        val title = context["context"].asJsonObject["component"].asJsonObject
        assertEquals("MATCHED", title["status"].asString)
        assertEquals(snapshot.report.rootNodeId, title["queuedInNodeId"].asString)
        assertEquals(snapshot.report.node("box").nodeId, title["parentNodeId"].asString)
        assertEquals(2, title["idLocations"].asJsonArray.size())
        val optional = JsonParser.parseString(query.render(snapshot, WicketMcpReport.CONTEXT, componentPath = "box:details")).asJsonObject
        assertTrue(optional["context"].asJsonObject["component"].asJsonObject["conditional"].asBoolean)
        assertEquals("UNRESOLVED", optional["context"].asJsonObject["component"].asJsonObject["confidence"].asString)
        assertTrue(optional["context"].asJsonObject["markupElements"].asJsonArray.isEmpty)
        val validation = JsonParser.parseString(query.render(snapshot, WicketMcpReport.VALIDATION)).asJsonObject
        assertEquals(structure["analysis"].asJsonObject["matching"].asJsonObject["findings"], validation["validation"].asJsonObject["findings"])
        assertFalse(structure.toString().contains("branchPathMask"))
        assertFalse(structure.toString().contains("branchPathCount"))
        assertEquals(module.name, context["target"].asJsonObject["moduleName"].asString)
        assertEquals(before, file.text)
        assertEquals(query.render(snapshot), query.render(query.capture(project, "example.ExamplePage", module.name)))
    }

    fun testMarkupEditInvalidatesAndReparentsEveryFeasibleQueuePath() {
        val file = page("""
            add(new WebMarkupContainer("left"), new WebMarkupContainer("right"));
            queue(flag ? new Label("name") : new Label("name"));
        """)
        val report = analyze(file, "<div wicket:id='left'><span wicket:id='name'></span></div><div wicket:id='right'></div>")
        val html = WicketIdPsi.source(project, report.markupPath!!)!!
        val query = WicketPageAnalysisRequest()
        val old = query.capture(project, "example.ExamplePage")
        assertEquals(old.report.node("left").nodeId, old.report.node("name").parentNodeId)
        WriteCommandAction.runWriteCommandAction(project) {
            PsiDocumentManager.getInstance(project).getDocument(html)!!.setText(
                "<div wicket:id='left'></div><div wicket:id='right'><span wicket:id='name'></span></div>")
        }
        try { query.requireCurrent(project, old); fail("Expected stale snapshot") }
        catch (failure: PageAnalysisFailure) { assertEquals("STALE_SNAPSHOT", failure.code) }
        PsiDocumentManager.getInstance(project).commitAllDocuments()
        val fresh = query.capture(project, "example.ExamplePage")
        assertEquals(fresh.report.node("right").nodeId, fresh.report.node("name").parentNodeId)
        matched(fresh.report, "left", "right", "name")
    }

    fun testExternalConditionEditInvalidatesTheFeasiblePathSnapshot() {
        val helper = source("Flags", "public final class Flags { public static boolean opposite(boolean flag) { return !flag; } }")
        val file = page("if (flag) queue(new Label(\"title\")); if (Flags.opposite(flag)) queue(new Label(\"title\"));")
        matched(analyze(file), "title")
        val query = WicketPageAnalysisRequest()
        val old = query.capture(project, "example.ExamplePage")
        assertTrue(helper.virtualFile.path in old.report.javaSourceFiles)
        WriteCommandAction.runWriteCommandAction(project) {
            PsiDocumentManager.getInstance(project).getDocument(helper)!!.setText(helper.text.replace("return !flag;", "return flag;"))
        }
        try { query.requireCurrent(project, old); fail("Expected stale snapshot") }
        catch (failure: PageAnalysisFailure) { assertEquals("STALE_SNAPSHOT", failure.code) }
        PsiDocumentManager.getInstance(project).commitAllDocuments()
        noUpgrade(query.capture(project, "example.ExamplePage").report)
    }

    fun testQueueTraversalBudgetIsSharedAcrossAllFeasiblePaths() {
        val report = traversalFixture()
        val containers = report.children().filter { it.wicketId != "title" }.map { it.nodeId }.toSet()
        val single = WicketQueueResolver.resolve(report.copy(branchPathCount = 1), containers, emptySet())
        assertEquals(Confidence.CONFIRMED, single.node("title").confidence)
        val allPaths = WicketQueueResolver.resolve(report, containers, emptySet())
        assertEquals(Confidence.UNRESOLVED, allPaths.node("title").confidence)
        assertNull(allPaths.node("title").parentNodeId)
    }

    fun testCancellationIsNotConvertedIntoAQueuePlacement() {
        val report = traversalFixture()
        val indicator = EmptyProgressIndicator()
        assertThrows(ProcessCanceledException::class.java) {
            ProgressManager.getInstance().runProcess(Runnable {
                indicator.cancel(); WicketQueueResolver.resolve(report, emptySet(), emptySet())
            }, indicator)
        }
    }

    fun testPortableDemoHasTwoMatchedQueueBindingsAndConditionalDetails() {
        val root = Path.of(System.getProperty("wicketlens.test.demo"))
        val file = myFixture.addFileToProject("src/main/java/example/QueueBranchPage.java",
            Files.readString(root.resolve("main/java/example/QueueBranchPage.java"))) as PsiJavaFile
        companion("QueueBranchPage", Files.readString(root.resolve("main/resources/example/QueueBranchPage.html")))
        val report = WicketPageAnalyzer().analyze(file.classes.single())!!
        matched(report, "content", "title")
        conditional(report, "details")
        assertEquals(report.node("content").nodeId, report.node("title").parentNodeId)
        assertEquals(report.rootNodeId, report.node("title").queuedInNodeId)
        assertTrue(report.node("title").computedId)
        assertTrue(findings(report).none { it.code in MISSING })
    }

    private fun traversalFixture(): PageReport {
        val javaLocation = SourceLocation("/fixture/Page.java", 0)
        val markupLocation = SourceLocation("/fixture/Page.html", 0)
        val root = ComponentNode("page", null, "example.Page", null, javaLocation, confidence = Confidence.CONFIRMED)
        val containers = (1..600).map {
            ComponentNode("box$it", "box$it", "org.apache.wicket.markup.html.WebMarkupContainer", "page", javaLocation,
                confidence = Confidence.CONFIRMED, branchMerged = true, branchPathMask = 255)
        }
        val title = ComponentNode("title", "title", "org.apache.wicket.markup.html.basic.Label", null, javaLocation,
            confidence = Confidence.UNRESOLVED, queuedInNodeId = "page", branchMerged = true, branchPathMask = 255,
            diagnostics = listOf(Diagnostic(DiagnosticCode.UNRESOLVED_QUEUE, "Unplaced queue.")))
        val elements = (listOf("title") + containers.map { it.wicketId!! }).mapIndexed { index, id ->
            MarkupElement("markup$index", "div", null, true, id, null, markupLocation.copy(offset = index),
                markupLocation.copy(offset = index), true, Confidence.CONFIRMED)
        }
        return PageReport("example.Page", "page", listOf(root) + containers + title, markupPath = markupLocation.file,
            markup = MarkupReport(markupLocation.file, MarkupKind.PAGE, elements, true), branchPathCount = 8)
    }

    private fun source(name: String, declaration: String) = myFixture.addFileToProject("src/main/java/example/$name.java",
        "package example; import org.apache.wicket.markup.html.basic.Label; $declaration")
    private fun page(body: String, members: String = "", parameters: String = "boolean flag", name: String = "ExamplePage") =
        source(name, """
            import org.apache.wicket.markup.html.WebMarkupContainer;
            public class $name extends org.apache.wicket.markup.html.WebPage {
                public $name($parameters) { $body }
                $members
            }
        """.trimIndent()) as PsiJavaFile
    private fun analyze(file: PsiJavaFile, html: String = "<span wicket:id='title'></span>"): PageReport {
        companion(file.classes.single().name!!, html)
        return WicketPageAnalyzer().analyze(file.classes.single())!!
    }
    private fun report(body: String, html: String = "<span wicket:id='title'></span>", members: String = "",
        parameters: String = "boolean flag", name: String = "ExamplePage") = analyze(page(body, members, parameters, name), html)
    private fun PageReport.node(id: String) = children().single { it.wicketId == id }
    private fun findings(report: PageReport) = report.matching!!.findings
    private fun matched(report: PageReport, vararg ids: String) =
        assertEquals(report.matching.toString(), ids.toSet(), findings(report).filter { it.code == DiagnosticCode.MATCHED }
            .map { finding -> report.nodes.single { it.nodeId == finding.javaNodeId }.wicketId }.toSet())
    private fun conditional(report: PageReport, vararg ids: String) {
        val nodes = report.children().filter { it.wicketId in ids }
        assertEquals(ids.toSet(), nodes.map { it.wicketId }.toSet())
        assertTrue(nodes.all { it.conditional && it.confidence == Confidence.UNRESOLVED && !it.idRenameSafe })
        assertTrue(nodes.all { node -> findings(report).single { it.javaNodeId == node.nodeId }.code == DiagnosticCode.UNRESOLVED_BRANCH })
    }
    private fun unresolvedQueue(report: PageReport, node: ComponentNode) {
        assertEquals(Confidence.UNRESOLVED, node.confidence)
        assertTrue(node.diagnostics.any { it.code == DiagnosticCode.UNRESOLVED_QUEUE })
        assertEquals(DiagnosticCode.UNRESOLVED_QUEUE, findings(report).single { it.javaNodeId == node.nodeId }.code)
    }
    private fun noUpgrade(report: PageReport) = assertFalse(report.children().any { it.branchMerged })
    private fun problems(file: PsiFile) = ProblemsHolder(InspectionManager.getInstance(project), file, false).also {
        WicketUnpairedIdInspection().buildVisitor(it, false).visitFile(file)
    }
    companion object { private val MISSING = setOf(DiagnosticCode.JAVA_ONLY, DiagnosticCode.MARKUP_ONLY) }
}
