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

class WicketRepeaterBranchMergeTest : WicketProjectPsiTestCase() {
    fun testAlternativeListViewAllocationsMergeTheirLogicalItemAndOrigins() {
        val report = report("if (flag) ${list("item.add(new Label(\"name\", \"Short\"));")} else ${list("item.add(new Label(\"name\", \"Long\"));")}")
        matched(report, "rows", "name")
        val item = report.nodes.single { it.templateItem }
        assertNull(item.wicketId)
        assertTrue(item.branchMerged)
        assertEquals(1, item.alternativeJavaLocations.size)
        assertEquals(item.nodeId, report.node("name").parentNodeId)
        assertEquals(2, report.node("name").idLocations().size)
        inferred(report, "name")
    }

    fun testUnknownCallbackBranchesConfirmOnlyTheirCommonTemplateBinding() {
        val report = report(list("if (flag) item.add(new Label(\"name\")); else item.add(new Label(\"name\"));"))
        matched(report, "rows", "name")
        inferred(report, "name")
        assertTrue(report.warnings.any { it.code == DiagnosticCode.PARTIAL_ANALYSIS && it.message.contains("row counts") })
    }

    fun testOptionalCallbackChildDoesNotSuppressAnUnrelatedMarkupFinding() {
        val report = report(list("item.add(new Label(\"name\")); if (flag) item.add(new Label(\"details\"));"),
            "<div wicket:id='rows'><span wicket:id='name'></span><span wicket:id='details'></span><span wicket:id='missing'></span></div><span wicket:id='outside'></span>")
        matched(report, "rows", "name")
        conditional(report, "details")
        assertEquals(setOf("missing", "outside"), findings(report).filter { it.code == DiagnosticCode.MARKUP_ONLY }
            .map { f -> report.markup!!.elements.single { it.nodeId == f.markupNodeId }.wicketId }.toSet())
        assertEquals(DiagnosticCode.UNRESOLVED_BRANCH, markupFinding(report, "details").code)
    }

    fun testOptionalRepeaterAndAllItsChildrenRemainConditional() {
        val report = report("if (flag) ${list("item.add(new Label(\"name\"));")}",
            "$MARKUP<span wicket:id='outside'></span>")
        conditional(report, "rows", "name")
        assertTrue(report.nodes.single { it.templateItem }.conditional)
        assertEquals(DiagnosticCode.MARKUP_ONLY, markupFinding(report, "outside").code)
        assertTrue(findings(report).none { it.code == DiagnosticCode.MATCHED })
    }

    fun testComputedAndLiteralItemIdOriginsRemainDistinct() {
        val report = report(list("item.add(flag ? new Label(\"na\" + \"me\") : new Label(\"name\"));"))
        matched(report, "rows", "name")
        inferred(report, "name")
        val name = report.node("name")
        assertTrue(name.computedId)
        assertEquals(2, name.idLocations().size)
        assertEquals(1, name.computedIdLocations.size)
    }

    fun testComplementaryCallbackChecksReuseTheCapturedStableFlag() {
        val report = report(list("if (flag) item.add(new Label(\"name\")); if (!flag) item.add(new Label(\"name\"));"))
        matched(report, "rows", "name")
        assertEquals(2, report.branchPathCount)
        inferred(report, "name")
    }

    fun testCallbacksShareTheSameCapturedStableIntegralInput() {
        val report = report(list("if (number > 0) item.add(new Label(\"name\"));", "first") +
            list("if (number > 0) item.add(new Label(\"name\"));", "second"),
            "<div wicket:id='first'><span wicket:id='name'></span></div><div wicket:id='second'><span wicket:id='name'></span></div>",
            parameters = "int number")
        matched(report, "first", "second")
        conditional(report, "name")
        assertEquals(2, report.branchPathCount)
    }

    fun testNestedListViewsKeepBothTransparentItemBoundaries() {
        val nested = """
            item.add(new ListView<String>("inner", java.util.List.of("B")) {
                @Override protected void populateItem(ListItem<String> nested) {
                    nested.add(flag ? new Label("name") : new Label("name"));
                }
            });
        """
        val report = report(list(nested), "<div wicket:id='rows'><div wicket:id='inner'><span wicket:id='name'></span></div></div>")
        matched(report, "rows", "inner", "name")
        assertEquals(2, report.nodes.count { it.templateItem })
        inferred(report, "inner", "name")
    }

    fun testOptionalNestedTemplateUncertaintyIsConfinedToThatSubtree() {
        val report = report(list("""
            if (flag) item.add(new ListView<String>("inner", java.util.List.of("B")) {
                @Override protected void populateItem(ListItem<String> nested) { nested.add(new Label("name")); }
            });
        """), "<div wicket:id='rows'><div wicket:id='inner'><span wicket:id='name'></span></div><span wicket:id='outside'></span></div>")
        matched(report, "rows")
        conditional(report, "inner", "name")
        assertEquals(DiagnosticCode.MARKUP_ONLY, markupFinding(report, "outside").code)
    }

    fun testRepeaterAndOrdinaryContainerAlternativesAreNotUnified() {
        val report = report("if (flag) ${list("item.add(new Label(\"name\"));")} else add(new WebMarkupContainer(\"rows\"));")
        conditional(report, "rows", "name")
        assertEquals(2, report.children().count { it.wicketId == "rows" })
        assertTrue(findings(report).none { it.code == DiagnosticCode.MATCHED || it.code in MISSING })
    }

    fun testListViewAndRepeatingViewKeepSeparateConditionalKinds() {
        val report = report("if (flag) ${list("item.add(new Label(\"name\"));")} else { ${repeating("item.add(new Label(\"name\"));")} }")
        conditional(report, "rows", "name")
        assertEquals(setOf(Repetition.LIST_VIEW, Repetition.REPEATING_VIEW), report.children().filter { it.wicketId == "rows" }.map { it.repetition }.toSet())
        assertEquals(2, report.nodes.count { it.templateItem && it.conditional })
        assertTrue(findings(report).none { it.code == DiagnosticCode.MATCHED || it.code in MISSING })
    }

    fun testRepeatingViewAlternativeItemBodiesMergeWithoutInventingAnId() {
        val report = report(repeating("if (flag) item.add(new Label(\"name\")); else item.add(new Label(\"name\"));"))
        matched(report, "rows", "name")
        inferred(report, "name")
        assertNull(report.nodes.single { it.templateItem }.wicketId)
        assertEquals(2, report.node("name").idLocations().size)
    }

    fun testRepeatingViewForeachRemainsOneSymbolicItemAcrossBranches() {
        val report = report("""
            RepeatingView rows = new RepeatingView("rows"); add(rows);
            for (String value : java.util.List.of("A", "B")) {
                WebMarkupContainer item = new WebMarkupContainer(rows.newChildId());
                item.add(flag ? new Label("name", value) : new Label("name", value)); rows.add(item);
            }
        """)
        matched(report, "rows", "name")
        inferred(report, "name")
        assertEquals(1, report.nodes.count { it.templateItem })
        assertTrue(report.warnings.any { it.message.contains("iteration count") })
    }

    fun testAlternativeRepeatingViewItemAllocationsShareOneLogicalTemplate() {
        val report = report("""
            RepeatingView rows = new RepeatingView("rows"); add(rows);
            if (flag) {
                WebMarkupContainer shortItem = new WebMarkupContainer(rows.newChildId());
                shortItem.add(new Label("name")); rows.add(shortItem);
            } else {
                WebMarkupContainer longItem = new WebMarkupContainer(rows.newChildId());
                longItem.add(new Label("name")); rows.add(longItem);
            }
        """)
        matched(report, "rows", "name")
        inferred(report, "name")
        assertEquals(1, report.nodes.count { it.templateItem })
        assertEquals(1, report.nodes.single { it.templateItem }.alternativeJavaLocations.size)
    }

    fun testEmptyAlternativeCallbacksStillExposeMissingTemplateChildren() {
        val report = report("if (flag) ${list("")} else ${list("")}")
        matched(report, "rows")
        assertEquals(DiagnosticCode.MARKUP_ONLY, markupFinding(report, "name").code)
        assertTrue(report.warnings.any { it.message.contains("row counts") })
    }

    fun testConflictingChildTypesRemainConditionalAtTheSameLogicalPath() {
        val report = report(list("if (flag) item.add(new Label(\"name\")); else item.add(new WebMarkupContainer(\"name\"));"))
        matched(report, "rows")
        conditional(report, "name")
        assertEquals(2, report.children().count { it.wicketId == "name" })
        assertEquals(DiagnosticCode.UNRESOLVED_BRANCH, markupFinding(report, "name").code)
    }

    fun testRepeatedChildIdsInSeparateTemplatesRemainIndependentMatches() {
        val report = report(list("item.add(flag ? new Label(\"name\") : new Label(\"name\"));", "first") +
            list("item.add(new Label(\"name\"));", "second"),
            "<div wicket:id='first'><span wicket:id='name'></span></div><div wicket:id='second'><span wicket:id='name'></span></div>")
        assertEquals(4, findings(report).count { it.code == DiagnosticCode.MATCHED })
        assertEquals(2, findings(report).count { it.code == DiagnosticCode.MATCHED && it.confidence == Confidence.INFERRED })
        assertEquals(2, report.children().filter { it.wicketId == "name" }.map { it.parentNodeId }.distinct().size)
    }

    fun testRepeatingViewOnPopulateUsesItsOwnReceiverInEachBranch() {
        val report = report("""
            add(new RepeatingView("rows") {
                @Override protected void onPopulate() {
                    WebMarkupContainer item = new WebMarkupContainer(newChildId());
                    if (flag) item.add(new Label("name")); else item.add(new Label("name")); add(item);
                }
            });
        """)
        matched(report, "rows", "name")
        inferred(report, "name")
    }

    fun testOptionalRepeatingViewItemKeepsItsChildrenConditional() {
        val report = report("""
            RepeatingView rows = new RepeatingView("rows"); add(rows);
            if (flag) {
                WebMarkupContainer item = new WebMarkupContainer(rows.newChildId());
                item.add(new Label("name")); rows.add(item);
            }
        """)
        matched(report, "rows")
        conditional(report, "name")
        assertTrue(report.nodes.single { it.templateItem }.conditional)
        assertEquals(DiagnosticCode.UNRESOLVED_BRANCH, markupFinding(report, "name").code)
    }

    fun testPrivateFinalRepeaterFieldAndCallbackHelperUseTheSameMerge() {
        val report = report("add(rows); if (flag) {} else {}", members = """
            private final ListView<String> rows = new ListView<String>("rows", java.util.List.of("A")) {
                @Override protected void populateItem(ListItem<String> item) { attach(item, flagValue()); }
                private void attach(ListItem<String> item, boolean selected) {
                    item.add(selected ? new Label("name") : new Label("name"));
                }
                private boolean flagValue() { return 1 > 0; }
            };
        """, parameters = "boolean flag")
        // A separate unknown setup choice still merges the field's item template.
        assertTrue(report.node("name").template)
        matched(report, "rows", "name")
    }

    fun testItemLocalQueueBranchesUseTheTransparentItemScope() {
        val report = report(list("item.queue(flag ? new Label(\"name\") : new Label(\"name\"));"))
        matched(report, "rows", "name")
        inferred(report, "name")
        val item = report.nodes.single { it.templateItem }
        assertEquals(item.nodeId, report.node("name").queuedInNodeId)
        assertEquals(item.nodeId, report.node("name").parentNodeId)
    }

    fun testConditionalDirectItemChildCannotHideAnotherQueuePlacement() {
        val report = report(list("""
            WebMarkupContainer box = new WebMarkupContainer("box"); item.add(box);
            item.queue(new Label("name")); if (flag) box.add(new Label("name"));
        """), "<div wicket:id='rows'><span wicket:id='name'></span><div wicket:id='box'><span wicket:id='name'></span></div></div>")
        val queued = report.children().single { it.wicketId == "name" && it.queuedInNodeId != null }
        assertTrue(queued.diagnostics.any { it.code == DiagnosticCode.UNRESOLVED_QUEUE })
        assertNull(queued.parentNodeId)
        matched(report, "rows", "box")
    }

    fun testOptionalItemQueueRetainsConditionalPresenceAfterPlacement() {
        val report = report(list("if (flag) item.queue(new Label(\"name\"));"))
        matched(report, "rows")
        conditional(report, "name")
        assertEquals(report.nodes.single { it.templateItem }.nodeId, report.node("name").parentNodeId)
        assertTrue(report.node("name").diagnostics.none { it.code == DiagnosticCode.UNRESOLVED_QUEUE })
        assertEquals(DiagnosticCode.UNRESOLVED_BRANCH, markupFinding(report, "name").code)
    }

    fun testRootQueueStillCannotCrossIntoABranchMergedItemTemplate() {
        val report = report("queue(new Label(\"name\")); " + list("if (flag) {} else {}"))
        matched(report, "rows")
        assertTrue(report.node("name").diagnostics.any { it.code == DiagnosticCode.UNRESOLVED_QUEUE })
        assertNull(report.node("name").parentNodeId)
    }

    fun testUnplacedItemQueueDoesNotSuppressUnrelatedPageMarkup() {
        val report = report(list("""
            item.add(new WebMarkupContainer("left"), new WebMarkupContainer("right"));
            item.queue(flag ? new Label("name") : new Label("name"));
        """), "<div wicket:id='rows'><div wicket:id='left'><span wicket:id='name'></span></div><div wicket:id='right'><span wicket:id='name'></span></div></div><span wicket:id='outside'></span>")
        assertTrue(report.node("name").diagnostics.any { it.code == DiagnosticCode.UNRESOLVED_QUEUE })
        assertEquals(DiagnosticCode.MARKUP_ONLY, markupFinding(report, "outside").code)
        matched(report, "rows", "left", "right")
    }

    fun testDuplicateIdsInOneFeasibleItemPathStillRejectTheUpgrade() =
        noUpgrade(report(list("if (flag) item.add(new Label(\"name\"), new Label(\"name\")); else item.add(new Label(\"name\"));")))

    fun testMultipleItemsInOneFeasiblePathStillRejectTheUpgrade() = noUpgrade(report("""
        ${repeating("item.add(new Label(\"name\"));")}
        if (flag) { WebMarkupContainer extra = new WebMarkupContainer(rows.newChildId()); rows.add(extra); }
    """))

    fun testGeneratedItemCannotMoveToAnotherViewInOneBranch() = noUpgrade(report("""
        RepeatingView rows = new RepeatingView("rows"), other = new RepeatingView("other"); add(rows, other);
        WebMarkupContainer item = new WebMarkupContainer(rows.newChildId());
        item.add(new Label("name")); if (flag) rows.add(item); else other.add(item);
    """))

    fun testPossibleEnclosingPageEffectsStillRejectTheEntireUpgrade() =
        noUpgrade(report(list("if (flag) ExamplePage.this.removeAll(); item.add(new Label(\"name\"));")))

    fun testCapturedContainerMutationStillRejectsTheEntireUpgrade() = noUpgrade(report("""
        WebMarkupContainer captured = new WebMarkupContainer("captured"); add(captured);
        ${list("if (flag) captured.add(new Label(\"outside\")); item.add(new Label(\"name\"));")}
    """))

    fun testUnknownModelCallDoesNotBecomePureDuringBranchMerging() {
        source("Person", "public class Person { public String getName() { return \"A\"; } }")
        val report = report("""
            add(new ListView<Person>("rows", people) {
                @Override protected void populateItem(ListItem<Person> item) {
                    if (flag) item.add(new Label("name", item.getModelObject().getName()));
                    else item.add(new Label("name"));
                }
            });
        """, parameters = "boolean flag, java.util.List<Person> people")
        noUpgrade(report)
        assertTrue(report.warnings.any { it.code == DiagnosticCode.UNRESOLVED_SCOPE })
        assertTrue(findings(report).none { it.code == DiagnosticCode.MARKUP_ONLY })
    }

    fun testCustomItemFactoryRemainsOutsideTheReviewedContract() = noUpgrade(report(list(
        "if (flag) item.add(new Label(\"name\")); else item.add(new Label(\"name\"));", extra = """
            @Override protected ListItem<String> newItem(int index, org.apache.wicket.model.IModel<String> model) {
                return super.newItem(index, model);
            }
        """)))

    fun testFourthIndependentItemChoiceRetainsTheExplorationBound() = noUpgrade(report(list(
        (1..4).joinToString(" ") { "if (flag$it) item.add(new Label(\"id$it\"));" }),
        parameters = "boolean flag1, boolean flag2, boolean flag3, boolean flag4"))

    fun testCommonTemplateOriginsNavigateWhileRenameAndCompletionStayGuarded() {
        val file = page(list("item.add(flag ? new Label(\"name\") : new Label(\"name\"));"))
        val report = analyze(file)
        val origins = WicketIdPsi.javaIdElements(project, report.node("name"))
        assertEquals(2, origins.size)
        for (origin in origins) {
            assertNotNull(WicketIdPsi.pair(origin))
            assertNull(WicketIdRenameTarget.at(file, origin.textOffset + 1))
            assertTrue(WicketIdCompletionContributor().candidates(origin).isEmpty())
        }
        assertTrue(problems(file).results.isEmpty())
    }

    fun testOptionalTemplateChildProducesNoNativeMissingPairProblems() {
        val file = page(list("if (flag) item.add(new Label(\"name\"));"))
        val report = analyze(file)
        assertTrue(problems(file).results.isEmpty())
        assertTrue(problems(WicketIdPsi.source(project, report.markupPath!!)!!).results.isEmpty())
        assertNull(WicketIdPsi.pair(WicketIdPsi.javaIdElements(project, report.node("name")).single()))
    }

    fun testMcpContextUsesLogicalPathsAndPreservesInferredAndConditionalFindings() {
        val file = page(list("item.add(flag ? new Label(\"name\") : new Label(\"name\")); if (flag) item.add(new Label(\"details\"));"))
        analyze(file, "<div wicket:id='rows'><span wicket:id='name'></span><span wicket:id='details'></span></div>")
        val before = file.text
        val query = WicketPageAnalysisRequest()
        val snapshot = query.capture(project, "example.ExamplePage", module.name)
        val structure = JsonParser.parseString(query.render(snapshot)).asJsonObject
        assertEquals(JsonParser.parseString(WicketContextExporter().export(snapshot.report)), structure["analysis"])
        val context = JsonParser.parseString(query.render(snapshot, WicketMcpReport.CONTEXT, componentPath = "rows:name")).asJsonObject
        val component = context["context"].asJsonObject["component"].asJsonObject
        assertEquals("INFERRED", component["matchingConfidence"].asString)
        assertTrue(context["context"].asJsonObject["parent"].asJsonObject["templateItem"].asBoolean)
        assertEquals(2, component["idLocations"].asJsonArray.size())
        val optional = JsonParser.parseString(query.render(snapshot, WicketMcpReport.CONTEXT, componentPath = "rows:details")).asJsonObject["context"].asJsonObject
        assertTrue(optional["component"].asJsonObject["conditional"].asBoolean)
        assertTrue(optional["markupElements"].asJsonArray.isEmpty)
        val validation = JsonParser.parseString(query.render(snapshot, WicketMcpReport.VALIDATION)).asJsonObject
        assertEquals(structure["analysis"].asJsonObject["matching"].asJsonObject["findings"], validation["validation"].asJsonObject["findings"])
        assertEquals(module.name, context["target"].asJsonObject["moduleName"].asString)
        assertFalse(structure.toString().contains("branchPathMask"))
        assertEquals(before, file.text)
        assertEquals(query.render(snapshot), query.render(query.capture(project, "example.ExamplePage", module.name)))
    }

    fun testMcpRepeaterAlternativesRequireNodeIdsWhenTheirPathsOverlap() {
        val file = page("if (flag) ${list("item.add(new Label(\"name\"));")} else { ${repeating("item.add(new Label(\"name\"));")} }")
        analyze(file)
        val query = WicketPageAnalysisRequest()
        val snapshot = query.capture(project, "example.ExamplePage")
        try { query.render(snapshot, WicketMcpReport.CONTEXT, componentPath = "rows:name"); fail("Expected ambiguous path") }
        catch (failure: PageAnalysisFailure) { assertEquals("AMBIGUOUS_COMPONENT", failure.code) }
        val child = snapshot.report.children().first { it.wicketId == "name" }
        val context = JsonParser.parseString(query.render(snapshot, WicketMcpReport.CONTEXT, nodeId = child.nodeId)).asJsonObject["context"].asJsonObject
        assertTrue(context["component"].asJsonObject["conditional"].asBoolean)
        assertTrue(context["markupElements"].asJsonArray.isEmpty)
    }

    fun testExternalCallbackEditInvalidatesTheSharedSnapshot() {
        val helper = source("Names", """
            public class Names extends org.apache.wicket.markup.html.list.ListView<String> {
                public Names(String id) { super(id, java.util.List.of("A")); }
                @Override protected void populateItem(org.apache.wicket.markup.html.list.ListItem<String> item) {
                    item.add(new org.apache.wicket.markup.html.basic.Label("name"));
                }
            }
        """)
        val file = page("if (flag) add(new Names(\"rows\")); else add(new Names(\"rows\"));")
        matched(analyze(file), "rows", "name")
        val query = WicketPageAnalysisRequest()
        val old = query.capture(project, "example.ExamplePage")
        WriteCommandAction.runWriteCommandAction(project) {
            PsiDocumentManager.getInstance(project).getDocument(helper)!!.setText(helper.text.replace("\"name\"", "\"changed\""))
        }
        try { query.requireCurrent(project, old); fail("Expected stale snapshot") }
        catch (failure: PageAnalysisFailure) { assertEquals("STALE_SNAPSHOT", failure.code) }
        PsiDocumentManager.getInstance(project).commitAllDocuments()
        val fresh = query.capture(project, "example.ExamplePage")
        assertTrue(fresh.report.nodes.any { it.wicketId == "changed" })
        assertFalse(fresh.report.nodes.any { it.wicketId == "name" })
    }

    fun testMcpResponseBoundIncludesMergedTemplateOrigins() {
        val file = page(list("item.add(flag ? new Label(\"name\") : new Label(\"name\"));"))
        analyze(file)
        val query = WicketPageAnalysisRequest()
        val snapshot = query.capture(project, "example.ExamplePage")
        val oversized = snapshot.copy(report = snapshot.report.copy(nodes = snapshot.report.nodes.map {
            if (it.templateItem) it.copy(alternativeJavaLocations = listOf(SourceLocation("界".repeat(180_000), 0))) else it
        }))
        try { query.render(oversized); fail("Expected response bound") }
        catch (failure: PageAnalysisFailure) { assertEquals("RESPONSE_TOO_LARGE", failure.code) }
    }

    fun testCancellationNeverPublishesAPartialTemplateMerge() {
        val file = page(list("item.add(flag ? new Label(\"name\") : new Label(\"name\"));"))
        val indicator = EmptyProgressIndicator()
        assertThrows(ProcessCanceledException::class.java) {
            ProgressManager.getInstance().runProcess(Runnable {
                indicator.cancel(); WicketPageAnalyzer().analyze(file.classes.single())
            }, indicator)
        }
    }

    fun testPortableDemoHasCommonInferredChildrenAndOptionalDetails() {
        val root = Path.of(System.getProperty("wicketlens.test.demo"))
        val file = myFixture.addFileToProject("src/main/java/example/RepeaterBranchPage.java",
            Files.readString(root.resolve("main/java/example/RepeaterBranchPage.java"))) as PsiJavaFile
        companion("RepeaterBranchPage", Files.readString(root.resolve("main/resources/example/RepeaterBranchPage.html")))
        val report = WicketPageAnalyzer().analyze(file.classes.single())!!
        matched(report, "people", "name", "messages", "text")
        inferred(report, "name", "text")
        conditional(report, "details")
        assertEquals(2, report.nodes.count { it.templateItem })
        assertTrue(findings(report).none { it.code in MISSING })
    }

    private fun list(body: String, id: String = "rows", extra: String = "") = """
        add(new ListView<String>("$id", java.util.List.of("A")) {
            @Override protected void populateItem(ListItem<String> item) { $body }
            $extra
        });
    """.trimIndent()
    private fun repeating(body: String) = """
        RepeatingView rows = new RepeatingView("rows"); add(rows);
        WebMarkupContainer item = new WebMarkupContainer(rows.newChildId()); $body rows.add(item);
    """.trimIndent()
    private fun source(name: String, declaration: String) = myFixture.addFileToProject("src/main/java/example/$name.java", "package example; $declaration")
    private fun page(body: String, members: String = "", parameters: String = "boolean flag") = source("ExamplePage", """
        import org.apache.wicket.markup.html.WebMarkupContainer;
        import org.apache.wicket.markup.html.basic.Label;
        import org.apache.wicket.markup.html.list.*;
        import org.apache.wicket.markup.repeater.RepeatingView;
        public class ExamplePage extends org.apache.wicket.markup.html.WebPage {
            public ExamplePage($parameters) { $body }
            $members
        }
    """.trimIndent()) as PsiJavaFile
    private fun analyze(file: PsiJavaFile, html: String = MARKUP): PageReport {
        companion(file.classes.single().name!!, html)
        return WicketPageAnalyzer().analyze(file.classes.single())!!
    }
    private fun report(body: String, html: String = MARKUP, members: String = "", parameters: String = "boolean flag") =
        analyze(page(body, members, parameters), html)
    private fun PageReport.node(id: String) = children().single { it.wicketId == id }
    private fun findings(report: PageReport) = report.matching!!.findings
    private fun matched(report: PageReport, vararg ids: String) = assertEquals(report.matching.toString(), ids.toSet(),
        findings(report).filter { it.code == DiagnosticCode.MATCHED }.map { f -> report.nodes.single { it.nodeId == f.javaNodeId }.wicketId }.toSet())
    private fun inferred(report: PageReport, vararg ids: String) {
        ids.forEach { id ->
            val finding = findings(report).single { it.javaNodeId == report.node(id).nodeId }
            assertEquals(DiagnosticCode.MATCHED, finding.code)
            assertEquals(Confidence.INFERRED, finding.confidence)
        }
    }
    private fun conditional(report: PageReport, vararg ids: String) {
        val nodes = report.children().filter { it.wicketId in ids }
        assertEquals(ids.toSet(), nodes.map { it.wicketId }.toSet())
        assertTrue(nodes.all { it.branchMerged && it.conditional && it.confidence == Confidence.UNRESOLVED && !it.idRenameSafe })
        assertTrue(nodes.all { node -> findings(report).single { it.javaNodeId == node.nodeId }.code == DiagnosticCode.UNRESOLVED_BRANCH })
    }
    private fun markupFinding(report: PageReport, id: String) = findings(report).single {
        it.markupNodeId == report.markup!!.elements.single { e -> e.wicketId == id }.nodeId
    }
    private fun noUpgrade(report: PageReport) = assertFalse(report.children().any { it.branchMerged })
    private fun problems(file: PsiFile) = ProblemsHolder(InspectionManager.getInstance(project), file, false).also {
        WicketUnpairedIdInspection().buildVisitor(it, false).visitFile(file)
    }
    companion object {
        private const val MARKUP = "<div wicket:id='rows'><span wicket:id='name'></span></div>"
        private val MISSING = setOf(DiagnosticCode.JAVA_ONLY, DiagnosticCode.MARKUP_ONLY)
    }
}
