package dev.wicketlens

import com.intellij.psi.PsiJavaFile
import dev.wicketlens.analysis.WicketPageAnalyzer
import dev.wicketlens.export.WicketContextExporter
import dev.wicketlens.model.*
import dev.wicketlens.ui.ReportHierarchyBuilder
import java.nio.file.Files
import java.nio.file.Path

class WicketRepeaterTest : WicketProjectPsiTestCase() {
    fun testListViewProducesOneLogicalItemTemplateWithNoRuntimeIdOrCount() {
        val report = listView("item.add(new Label(\"name\", item.getModelObject()));")
        matched(report, 2)
        val item = report.nodes.single { it.templateItem }
        assertNull(item.wicketId)
        assertTrue(item.template)
        assertEquals(Repetition.LIST_VIEW, report.nodes.single { it.wicketId == "rows" }.repetition)
        val name = report.nodes.single { it.wicketId == "name" }
        assertEquals(item.nodeId, name.parentNodeId)
        assertEquals(Confidence.INFERRED, report.matching!!.findings.single { it.javaNodeId == name.nodeId }.confidence)
        assertTrue(report.nodes.none { it.wicketId in setOf("0", "1") })
        assertTrue(WicketContextExporter().export(report).contains("\"templateItem\": true"))
        assertTrue(ReportHierarchyBuilder().build(report).any { it.name == "item [template]" && it.label.contains("TEMPLATE") })
    }

    fun testListItemContainerChildrenKeepTheirWicketParent() {
        val report = listView("""
            WebMarkupContainer details = new WebMarkupContainer("details");
            details.add(new Label("name", item.getModelObject())); item.add(details);
        """, "<div wicket:id=\"rows\"><section wicket:id=\"details\"><span wicket:id=\"name\"></span></section></div>")
        matched(report, 3)
    }

    fun testListItemQueueUsesTheItemScope() {
        val report = listView("item.queue(new Label(\"name\", item.getModelObject()));")
        matched(report, 2)
        assertEquals(report.nodes.single { it.templateItem }.nodeId, report.nodes.single { it.wicketId == "name" }.queuedInNodeId)
    }

    fun testNestedListViewsProduceNestedTemplates() {
        val report = listView("""
            item.add(new ListView<String>("inner", java.util.List.of("nested")) {
                @Override protected void populateItem(ListItem<String> nested) { nested.add(new Label("name", nested.getModelObject())); }
            });
        """, "<div wicket:id=\"rows\"><div wicket:id=\"inner\"><span wicket:id=\"name\"></span></div></div>")
        matched(report, 3)
        assertEquals(2, report.nodes.count { it.templateItem })
    }

    fun testPrivateFinalListViewFieldsCanOwnTheirTemplates() {
        val java = page("add(rows);", """
            private final ListView<String> rows = new ListView<String>("rows", java.util.List.of("Ada")) {
                @Override protected void populateItem(ListItem<String> item) { item.add(new Label("name", item.getModelObject())); }
            };
        """)
        companion(html = markup)
        matched(analyze(java), 2)
    }

    fun testReorderedListViewConstructorCanUseAReviewedListFactory() {
        myFixture.addFileToProject("src/main/java/example/Names.java", """
            package example; import org.apache.wicket.markup.html.list.*; import org.apache.wicket.markup.html.basic.Label;
            public class Names extends ListView<String> {
                public Names(String caption, String id) { super(id, java.util.List.of(caption)); }
                @Override protected void populateItem(ListItem<String> item) { item.add(new Label("name", item.getModelObject())); }
            }
        """.trimIndent())
        val java = page("add(new Names(\"Ada\", \"rows\"));")
        companion(html = markup)
        matched(analyze(java), 2)
    }

    fun testForwardingSourceListViewSubclassIsSupported() {
        myFixture.addFileToProject("src/main/java/example/Names.java", """
            package example; import org.apache.wicket.markup.html.list.*; import org.apache.wicket.markup.html.basic.Label;
            public class Names extends ListView<String> {
                public Names(String id, java.util.List<String> names) { super(id, names); }
                @Override protected void populateItem(ListItem<String> item) { item.add(new Label("name", item.getModelObject())); }
            }
        """.trimIndent())
        val java = page("add(new Names(\"rows\", java.util.List.of(\"Ada\")));")
        companion(html = markup)
        matched(analyze(java), 2)
    }

    fun testRepeatingViewUsesGeneratedIdsAsTemplateMarkers() {
        val java = page("""
            RepeatingView rows = new RepeatingView("rows");
            WebMarkupContainer item = new WebMarkupContainer(rows.newChildId());
            item.add(new Label("name", "Ada")); rows.add(item); add(rows);
        """)
        companion(html = markup)
        val report = analyze(java)
        matched(report, 2)
        assertEquals(1, report.nodes.count { it.templateItem })
        assertNull(report.nodes.single { it.templateItem }.wicketId)
    }

    fun testForeachRepeatingViewIsAnalyzedOnceAsATemplate() {
        val java = page("""
            RepeatingView rows = new RepeatingView("rows"); add(rows);
            for (String name : java.util.List.of("Ada", "Grace")) {
                WebMarkupContainer item = new WebMarkupContainer(rows.newChildId());
                item.add(new Label("name", name)); rows.add(item);
            }
        """)
        companion(html = markup)
        val report = analyze(java)
        matched(report, 2)
        assertEquals(1, report.nodes.count { it.templateItem })
    }

    fun testRepeatingViewOnPopulateHasItsOwnThisReceiver() {
        val java = page("""
            add(new RepeatingView("rows") {
                @Override protected void onPopulate() {
                    for (String name : java.util.List.of("Ada")) {
                        WebMarkupContainer item = new WebMarkupContainer(newChildId());
                        item.add(new Label("name", name)); add(item);
                    }
                }
            });
        """)
        companion(html = markup)
        matched(analyze(java), 2)
    }

    fun testRootQueueCannotFlowIntoRepeaterTemplates() {
        val java = page("""
            queue(new Label("name"));
            add(new ListView<String>("rows", java.util.List.of("Ada")) {
                @Override protected void populateItem(ListItem<String> item) {}
            });
        """)
        companion(html = markup)
        val report = analyze(java)
        assertEquals(1, report.matching!!.findings.count { it.code == DiagnosticCode.MATCHED })
        assertTrue(report.matching.findings.any { it.code == DiagnosticCode.UNRESOLVED_QUEUE })
    }

    fun testEmptyCallbackCanReportAnUnpairedTemplateId() {
        val report = listView("")
        assertEquals(1, report.matching!!.findings.count { it.code == DiagnosticCode.MARKUP_ONLY })
    }

    fun testDuplicateIdsInOneItemRemainUnresolved() {
        val report = listView("item.add(new Label(\"name\"), new Label(\"name\"));")
        assertTrue(report.matching!!.findings.none { it.javaNodeId == report.nodes.first { it.wicketId == "name" }.nodeId && it.code == DiagnosticCode.MATCHED })
        assertTrue(report.matching.findings.none { it.code == DiagnosticCode.MARKUP_ONLY })
    }

    fun testBranchingCallbackDoesNotClaimMissingJavaChildren() {
        val report = listView("if (System.nanoTime() > 0) item.add(new Label(\"name\"));")
        assertTrue(report.warnings.any { it.code == DiagnosticCode.UNRESOLVED_SCOPE })
        assertTrue(report.matching!!.findings.none { it.code == DiagnosticCode.MARKUP_ONLY })
    }

    fun testListCallbackCannotMutateTheEnclosingPage() {
        val report = listView("ExamplePage.this.removeAll(); item.add(new Label(\"name\"));")
        assertTrue(report.warnings.any { it.code == DiagnosticCode.UNRESOLVED_SCOPE })
        assertTrue(report.matching!!.findings.none { it.code in setOf(DiagnosticCode.MATCHED, DiagnosticCode.MARKUP_ONLY) })
    }

    fun testCustomNewItemFactoryIsNotTreatedAsTheDefaultListItem() {
        val report = listView("item.add(new Label(\"name\"));", members = """
            @Override protected ListItem<String> newItem(int index, org.apache.wicket.model.IModel<String> model) { return super.newItem(index, model); }
        """)
        assertTrue(report.warnings.any { it.code == DiagnosticCode.UNRESOLVED_SCOPE })
        assertTrue(report.matching!!.findings.none { it.code == DiagnosticCode.MARKUP_ONLY })
    }

    fun testGetIdOverrideDoesNotEstablishAFalseRepeaterPair() {
        val report = listView("item.add(new Label(\"name\"));", members = "@Override public String getId() { return \"different\"; }")
        assertTrue(report.matching!!.findings.none { it.code == DiagnosticCode.MATCHED })
    }

    fun testUnknownModelGetterRetainsBindingsButBlocksAbsenceClaims() {
        myFixture.addFileToProject("src/main/java/example/Person.java", "package example; public class Person { public String getName() { return \"Ada\"; } }")
        val java = page("""
            add(new ListView<Person>("rows", names) {
                @Override protected void populateItem(ListItem<Person> item) { item.add(new Label("name", item.getModelObject().getName())); }
            });
        """, parameters = "java.util.List<Person> names")
        companion(html = "<div wicket:id=\"rows\"><span wicket:id=\"name\"></span><span wicket:id=\"other\"></span></div>")
        val report = analyze(java)
        matched(report, 2)
        assertTrue(report.warnings.any { it.code == DiagnosticCode.UNRESOLVED_SCOPE })
        assertTrue(report.matching!!.findings.none { it.code == DiagnosticCode.MARKUP_ONLY })
        assertTrue(report.matching.findings.single { it.javaNodeId == report.nodes.single { it.wicketId == "name" }.nodeId }.causes.isNotEmpty())
    }

    fun testGeneratedItemCannotBeAttachedToAnotherRepeater() {
        val java = page("""
            RepeatingView first = new RepeatingView("first"); RepeatingView rows = new RepeatingView("rows");
            WebMarkupContainer item = new WebMarkupContainer(first.newChildId());
            rows.add(item); add(first, rows);
        """)
        companion(html = markup)
        assertTrue(analyze(java).warnings.any { it.code == DiagnosticCode.UNRESOLVED_SCOPE })
    }

    fun testMultipleExplicitRepeatingItemsDoNotInventOneUnambiguousPrototype() {
        val java = page("""
            RepeatingView rows = new RepeatingView("rows");
            WebMarkupContainer first = new WebMarkupContainer(rows.newChildId()); first.add(new Label("name"));
            WebMarkupContainer second = new WebMarkupContainer(rows.newChildId()); second.add(new Label("name"));
            rows.add(first, second); add(rows);
        """)
        companion(html = markup)
        assertTrue(analyze(java).warnings.any { it.code == DiagnosticCode.UNRESOLVED_SCOPE })
    }

    fun testPrivateComponentFactoryInAListCallbackPreservesTheItemScope() {
        val report = listView("item.add(label(item));", members = """
            private Label label(ListItem<String> item) { return new Label("name", item.getModelObject()); }
        """)
        matched(report, 2)
        assertTrue(report.nodes.single { it.wicketId == "name" }.template)
    }

    fun testCallbackCannotAttachChildrenToACapturedContainer() {
        val java = page("""
            WebMarkupContainer captured = new WebMarkupContainer("captured"); add(captured);
            add(new ListView<String>("rows", java.util.List.of("Ada")) {
                @Override protected void populateItem(ListItem<String> item) {
                    captured.add(new Label("outside")); item.add(new Label("name"));
                }
            });
        """)
        companion(html = "<div><div wicket:id=\"captured\"></div>$markup</div>")
        val report = analyze(java)
        assertTrue(report.warnings.any { it.code == DiagnosticCode.UNRESOLVED_SCOPE })
        assertTrue(report.matching!!.findings.none { it.code == DiagnosticCode.MARKUP_ONLY })
        assertTrue(report.matching.findings.none { it.code == DiagnosticCode.MATCHED && report.nodes.single { n -> n.nodeId == it.javaNodeId }.wicketId == "name" })
    }

    fun testLiteralRuntimeItemIdsAreNotMatchedAsOrdinaryMarkupChildren() {
        val java = page("""
            RepeatingView rows = new RepeatingView("rows");
            WebMarkupContainer item = new WebMarkupContainer("0");
            item.add(new Label("name")); rows.add(item); add(rows);
        """)
        companion(html = "<div wicket:id=\"rows\"><div wicket:id=\"0\"><span wicket:id=\"name\"></span></div></div>")
        val report = analyze(java)
        assertTrue(report.warnings.any { it.code == DiagnosticCode.UNRESOLVED_SCOPE })
        assertTrue(report.matching!!.findings.none { it.code == DiagnosticCode.MATCHED && report.nodes.single { n -> n.nodeId == it.javaNodeId }.wicketId in setOf("0", "name") })
        assertTrue(report.matching.findings.none { it.code == DiagnosticCode.MARKUP_ONLY })
    }

    fun testStaticModelFactoryDoesNotHideAnEffectfulExpressionQualifier() {
        val java = page("""
            add(new ListView<String>("rows", unsafeModel().ofList(java.util.List.of("Ada"))) {
                @Override protected void populateItem(ListItem<String> item) { item.add(new Label("name")); }
            });
        """, "private org.apache.wicket.model.Model<String> unsafeModel() { removeAll(); return null; }")
        companion(html = markup)
        val report = analyze(java)
        assertTrue(report.warnings.any { it.code == DiagnosticCode.UNRESOLVED_SCOPE })
        assertTrue(report.matching!!.findings.none { it.code == DiagnosticCode.MARKUP_ONLY })
    }

    fun testDemoQueueAndBothRepeaterTemplatesMatchThroughTheFullPipeline() {
        val root = Path.of(System.getProperty("wicketlens.test.demo"))
        val java = myFixture.addFileToProject("src/main/java/example/QueueRepeaterPage.java",
            Files.readString(root.resolve("main/java/example/QueueRepeaterPage.java"))) as PsiJavaFile
        companion("QueueRepeaterPage", Files.readString(root.resolve("main/resources/example/QueueRepeaterPage.html")))
        val report = analyze(java)
        matched(report, 6)
        assertEquals(2, report.nodes.count { it.templateItem })
        assertEquals(2, report.nodes.count { it.repetition != null })
        assertEquals(2, report.matching!!.findings.count { it.code == DiagnosticCode.MATCHED && it.confidence == Confidence.INFERRED })
        assertTrue(report.matching.findings.all { it.code == DiagnosticCode.MATCHED })
    }

    private val markup = "<div wicket:id=\"rows\"><span wicket:id=\"name\"></span></div>"
    private fun listView(body: String, html: String = markup, members: String = ""): PageReport {
        val java = page("""
            add(new ListView<String>("rows", java.util.List.of("Ada", "Grace")) {
                @Override protected void populateItem(ListItem<String> item) { $body }
                $members
            });
        """)
        companion(html = html)
        return analyze(java)
    }
    private fun page(body: String, members: String = "", parameters: String = "") = myFixture.addFileToProject("src/main/java/example/ExamplePage.java", """
        package example; import org.apache.wicket.markup.html.WebPage;
        import org.apache.wicket.markup.html.WebMarkupContainer;
        import org.apache.wicket.markup.html.basic.Label;
        import org.apache.wicket.markup.html.list.*;
        import org.apache.wicket.markup.repeater.RepeatingView;
        public class ExamplePage extends WebPage { public ExamplePage($parameters) { $body } $members }
    """.trimIndent()) as PsiJavaFile
    private fun analyze(java: PsiJavaFile) = WicketPageAnalyzer().analyze(java.classes.single())!!
    private fun matched(report: PageReport, count: Int) = assertEquals(report.toString(), count,
        report.matching!!.findings.count { it.code == DiagnosticCode.MATCHED })
}
