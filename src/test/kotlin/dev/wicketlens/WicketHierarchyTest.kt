package dev.wicketlens

import com.intellij.psi.PsiJavaFile
import dev.wicketlens.analysis.WicketJavaAnalyzer
import dev.wicketlens.model.Confidence
import dev.wicketlens.model.DiagnosticCode
import dev.wicketlens.model.PageReport

class WicketHierarchyTest : WicketJavaPsiTestCase() {
    fun testVariableComponentUsesConstructionLocationAndConcreteType() {
        val report = analyze("org.apache.wicket.Component heading = new Label(\"heading\"); add(heading);")
        val heading = report.children().single()
        assertEquals("heading", heading.wicketId)
        assertEquals("org.apache.wicket.markup.html.basic.Label", heading.componentType)
        assertEquals(myFixture.file.text.indexOf("new Label"), heading.javaLocation.offset)
        assertEquals(report.rootNodeId, heading.parentNodeId)
        assertEquals(Confidence.CONFIRMED, heading.confidence)
    }

    fun testNestedFormCanBeAttachedAfterItsChildren() {
        val report = analyze(
            """
            Form<?> form = new Form<>("form");
            form.add(new TextField<>("name"));
            add(form);
            """.trimIndent(),
        )
        assertEquals(2, report.children().size)
        assertEquals(report.rootNodeId, report.node("form").parentNodeId)
        assertEquals(report.node("form").nodeId, report.node("name").parentNodeId)
        assertConfirmed(report)
    }

    fun testChildVariableAndParentCanBeAddedInEitherOrder() {
        for (additions in listOf("form.add(name); add(form);", "add(form); form.add(name);")) {
            val report = analyze(
                "Form<?> form = new Form<>(\"form\"); TextField<String> name = new TextField<>(\"name\"); $additions",
            )
            assertEquals(2, report.children().size)
            assertEquals(report.node("form").nodeId, report.node("name").parentNodeId)
            assertConfirmed(report)
        }
    }

    fun testDeepHierarchyAllowsTheSameIdUnderDifferentParents() {
        val report = analyze(
            """
            WebMarkupContainer outer = new WebMarkupContainer("outer");
            Form<?> left = new Form<>("left"), right = new Form<>("right");
            left.add(new Label("title"));
            right.add(new Label("title"));
            outer.add(left, right);
            add(outer);
            """.trimIndent(),
        )
        assertEquals(5, report.children().size)
        assertEquals(report.node("outer").nodeId, report.node("left").parentNodeId)
        assertEquals(report.node("outer").nodeId, report.node("right").parentNodeId)
        assertEquals(setOf(report.node("left").nodeId, report.node("right").nodeId),
            report.children().filter { it.wicketId == "title" }.map { it.parentNodeId }.toSet())
        assertConfirmed(report)
    }

    fun testSameVariableNameInDifferentConstructorsDoesNotMergeInstances() {
        val report = analyze(
            "Form<?> form = new Form<>(\"first\"); form.add(new Label(\"title\")); add(form);",
            members = """
                public ExamplePage(String value) {
                    Form<?> form = new Form<>("second");
                    form.add(new Label("title"));
                    add(form);
                }
            """.trimIndent(),
        )
        assertEquals(4, report.children().size)
        assertEquals(2, report.children().count { it.wicketId == "title" })
        assertEquals(setOf(report.node("first").nodeId, report.node("second").nodeId),
            report.children().filter { it.wicketId == "title" }.map { it.parentNodeId }.toSet())
        assertFalse(report.scopeComplete) // Alternative constructors are not runtime-merged.
    }

    fun testLocalShadowingAFieldResolvesToTheLocalDeclaration() {
        val report = analyze(
            "Label heading = new Label(\"local\"); add(heading);",
            members = "Label heading = new Label(\"field\");",
        )
        assertEquals(listOf("local"), report.children().map { it.wicketId })
        assertConfirmed(report)
    }

    fun testReassignedChildDoesNotUseItsStaleInitializer() {
        val report = analyze("Label heading = new Label(\"old\"); heading = new Label(\"new\"); add(heading);")
        val child = report.children().single()
        assertNull(child.wicketId)
        assertEquals(Confidence.UNRESOLVED, child.confidence)
        assertEquals(DiagnosticCode.UNRESOLVED_VARIABLE, child.diagnostics.single().code)
    }

    fun testConditionalWriteAlsoInvalidatesEarlierVariableUse() {
        val report = analyze(
            "Label heading = new Label(\"old\"); add(heading); if (enabled()) heading = new Label(\"new\");",
            members = "boolean enabled() { return true; }",
        )
        assertNull(report.children().single().wicketId)
        assertEquals(Confidence.UNRESOLVED, report.children().single().confidence)
    }

    fun testReassignedReceiverDoesNotAttachChildrenToTheWrongInstance() {
        val report = analyze(
            """
            Form<?> form = new Form<>("old");
            form.add(new Label("child"));
            form = new Form<>("new");
            add(form);
            """.trimIndent(),
        )
        assertNull(report.node("child").parentNodeId)
        assertTrue(report.children().all { it.confidence == Confidence.UNRESOLVED })
        assertTrue(report.children().none { it.wicketId == "old" || it.wicketId == "new" })
    }

    fun testFieldsDeferredAssignmentsAndAliasesRemainUnresolved() {
        val report = analyze(
            """
            add(this.field);
            Label deferred;
            deferred = new Label("deferred");
            add(deferred);
            Label original = new Label("original");
            Label alias = original;
            add(original, alias);
            """.trimIndent(),
            members = "Label field = new Label(\"field\");",
        )
        assertEquals(4, report.children().size)
        assertTrue(report.children().all { it.wicketId == null && it.confidence == Confidence.UNRESOLVED })
        assertTrue(report.children().all { it.diagnostics.single().code == DiagnosticCode.UNRESOLVED_VARIABLE })
    }

    fun testParenthesizedLocalReferencesPreserveTheirIdentity() {
        val report = analyze(
            "Form<?> form = new Form<>(\"form\"); Label child = new Label(\"child\"); (form).add((child)); add((form));",
        )
        assertEquals(2, report.children().size)
        assertEquals(report.node("form").nodeId, report.node("child").parentNodeId)
        assertConfirmed(report)
    }

    fun testCastAliasAndHelperEscapeDoNotPreserveAConfirmedIdentity() {
        val report = analyze(
            """
            Label original = new Label("original");
            Label alias = (Label) original;
            add(original, alias);
            Form<?> form = new Form<>("form");
            configure(form);
            form.add(new Label("child"));
            add(form);
            """.trimIndent(),
            members = "void configure(Form<?> form) {}",
        )
        assertEquals(4, report.children().size)
        assertTrue(report.children().all { it.confidence == Confidence.UNRESOLVED })
        assertNull(report.node("child").parentNodeId)
        assertTrue(report.children().none { it.wicketId == "original" || it.wicketId == "form" })
    }

    fun testOtherReceiverMethodsAndNestedCapturesRemainUnresolved() {
        val report = analyze(
            """
            Form<?> configured = new Form<>("configured");
            configured.setOutputMarkupId(true);
            add(configured);
            Form<?> captured = new Form<>("captured");
            class Local { void setup() { captured.add(new Label("nested")); } }
            add(captured);
            """.trimIndent(),
        )
        assertEquals(2, report.children().size)
        assertTrue(report.children().all { it.wicketId == null && it.confidence == Confidence.UNRESOLVED })
    }

    fun testUnknownParentInstanceAndItsDescendantsStayUnresolved() {
        myFixture.addClass(
            """
            package example;
            public class CustomForm extends org.apache.wicket.markup.html.form.Form<Void> {
                public CustomForm(String text, String id) { super(id + "suffix"); }
            }
            """.trimIndent(),
        )
        val report = analyze("CustomForm form = new CustomForm(\"text\", \"id\"); form.add(new Label(\"child\")); add(form);")
        val form = report.children().single { it.wicketId == null }
        assertEquals(report.rootNodeId, form.parentNodeId)
        assertEquals(form.nodeId, report.node("child").parentNodeId)
        assertTrue(report.children().all { it.confidence == Confidence.UNRESOLVED })
    }

    fun testMultipleAdditionsProduceOneUnresolvedInstance() {
        val report = analyze("Label heading = new Label(\"heading\"); add(heading); add(heading);")
        val child = report.children().single()
        assertEquals("heading", child.wicketId)
        assertNull(child.parentNodeId)
        assertEquals(Confidence.UNRESOLVED, child.confidence)
        assertTrue(child.diagnostics.single().message.contains("multiple additions"))
    }

    fun testConflictingParentsAreNotResolvedByLastWriteWins() {
        val report = analyze(
            """
            Form<?> left = new Form<>("left"), right = new Form<>("right");
            Label child = new Label("child");
            left.add(child);
            right.add(child);
            add(left, right);
            """.trimIndent(),
        )
        assertEquals(3, report.children().size)
        assertNull(report.node("child").parentNodeId)
        assertEquals(Confidence.UNRESOLVED, report.node("child").confidence)
    }

    fun testUnattachedContainerPreservesLocalEdgeButLeavesRootPathUnresolved() {
        val report = analyze("Form<?> form = new Form<>(\"form\"); form.add(new Label(\"child\"));")
        assertEquals(2, report.children().size)
        assertNull(report.node("form").parentNodeId)
        assertEquals(report.node("form").nodeId, report.node("child").parentNodeId)
        assertTrue(report.children().all { it.confidence == Confidence.UNRESOLVED })
        assertTrue(report.children().all { it.diagnostics.any { d -> d.code == DiagnosticCode.UNRESOLVED_PARENT } })
    }

    fun testUnknownAncestorIdPropagatesUncertaintyToDescendants() {
        val report = analyze(
            "Form<?> form = new Form<>(dynamicId()); form.add(new Label(\"child\")); add(form);",
            members = "String dynamicId() { return \"form\"; }",
        )
        val form = report.children().single { it.wicketId == null }
        assertEquals(report.rootNodeId, form.parentNodeId)
        assertEquals(form.nodeId, report.node("child").parentNodeId)
        assertEquals(Confidence.UNRESOLVED, report.node("child").confidence)
        assertEquals(DiagnosticCode.UNRESOLVED_PARENT, report.node("child").diagnostics.single().code)
    }

    fun testConditionalContainerAttachmentDoesNotConfirmDescendants() {
        val report = analyze(
            "Form<?> form = new Form<>(\"form\"); if (enabled()) add(form); form.add(new Label(\"child\"));",
            members = "boolean enabled() { return true; }",
        )
        assertEquals(report.node("form").nodeId, report.node("child").parentNodeId)
        assertTrue(report.children().all { it.confidence == Confidence.UNRESOLVED })
        assertNull(report.node("form").parentNodeId)
    }

    fun testCyclesAreBrokenAndTheirDescendantsRemainUnresolved() {
        val report = analyze(
            """
            WebMarkupContainer a = new WebMarkupContainer("a"), b = new WebMarkupContainer("b");
            a.add(b);
            b.add(a);
            b.add(new Label("child"));
            """.trimIndent(),
        )
        assertEquals(3, report.children().size)
        assertNull(report.node("a").parentNodeId)
        assertNull(report.node("b").parentNodeId)
        assertEquals(report.node("b").nodeId, report.node("child").parentNodeId)
        assertTrue(report.children().all { it.confidence == Confidence.UNRESOLVED })
        assertTrue(report.node("a").diagnostics.single().message.contains("cycle"))
        assertTrue(report.node("b").diagnostics.single().message.contains("cycle"))
    }

    fun testSelfAttachmentIsUnresolvedInsteadOfPublishingASelfEdge() {
        val report = analyze("Form<?> form = new Form<>(\"form\"); form.add(form);")
        assertNull(report.node("form").parentNodeId)
        assertEquals(Confidence.UNRESOLVED, report.node("form").confidence)
    }

    fun testNestedGraphIsDeterministicAndReferencesOnlyReportedNodes() {
        val report = analyze(
            "Form<?> form = new Form<>(\"form\"); Label child = new Label(\"child\"); form.add(child); add(form);",
        )
        val file = myFixture.file as PsiJavaFile
        assertEquals(report, WicketJavaAnalyzer().analyze(file.classes.single()))
        val ids = report.nodes.map { it.nodeId }.toSet()
        assertEquals(report.nodes.size, ids.size)
        assertTrue(report.nodes.all { it.parentNodeId == null || it.parentNodeId in ids })
    }

    private fun PageReport.node(id: String) = children().single { it.wicketId == id }

    private fun assertConfirmed(report: PageReport) {
        assertTrue(report.children().all { it.confidence == Confidence.CONFIRMED && it.diagnostics.isEmpty() })
    }
}
