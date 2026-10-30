package dev.wicketlens

import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressManager
import com.intellij.psi.PsiJavaFile
import dev.wicketlens.analysis.WicketJavaAnalyzer
import dev.wicketlens.model.Confidence
import dev.wicketlens.model.DiagnosticCode
import dev.wicketlens.model.Severity

class WicketJavaAnalyzerTest : WicketJavaPsiTestCase() {

    fun testSimplePageDiscoversLiteralLabelAndLocations() {
        val report = analyze("add(new Label(\"title\", \"Hello\"));")
        val root = report.nodes.single { it.nodeId == report.rootNodeId }
        val label = report.children().single()

        assertEquals("example.ExamplePage", report.qualifiedClassName)
        assertEquals(1, report.schemaVersion)
        assertEquals("example.ExamplePage", root.componentType)
        assertNull(root.parentNodeId)
        assertEquals("title", label.wicketId)
        assertEquals("org.apache.wicket.markup.html.basic.Label", label.componentType)
        assertEquals(root.nodeId, label.parentNodeId)
        assertEquals(Confidence.CONFIRMED, label.confidence)
        assertTrue(label.diagnostics.isEmpty())
        assertEquals(myFixture.file.virtualFile.path, label.javaLocation.file)
        assertEquals(myFixture.file.text.indexOf("new Label"), label.javaLocation.offset)
        assertNull(label.markupLocation)
        assertNull(report.markupPath)
        assertFalse(report.scopeComplete)
        assertEquals(DiagnosticCode.PARTIAL_ANALYSIS, report.warnings.single().code)
    }

    fun testThisSuperVarargsAndReviewedConstructors() {
        val report = analyze(
            """
            this.add(new Label("first"), new Label("second", "Text"));
            super.add(new Form<Void>("form"));
            add(new TextField<String>("name", String.class));
            add(new WebMarkupContainer("container"));
            add(new Panel("panel"));
            """.trimIndent(),
        )
        assertEquals(listOf("first", "second", "form", "name", "container", "panel"),
            report.children().map { it.wicketId })
        assertTrue(report.children().all { it.confidence == Confidence.CONFIRMED })
        assertTrue(report.children().all { it.parentNodeId == report.rootNodeId })
    }

    fun testPanelAndIndirectPageInheritance() {
        assertEquals("title", analyze("add(new Label(\"title\"));", "Panel", "super(\"root\");")
            .children().single().wicketId)
        myFixture.addClass("package example; public class BasePage extends org.apache.wicket.markup.html.WebPage {}")
        assertEquals("title", analyze("add(new Label(\"title\"));", "BasePage").children().single().wicketId)
    }

    fun testNonWicketClassIsNotAnalyzed() {
        val file = myFixture.configureByText("Plain.java", "class Plain { void add(Object value) {} }") as PsiJavaFile
        assertNull(WicketJavaAnalyzer().analyze(file.classes.single()))
    }

    fun testUnrelatedAddOverloadAndBehaviorAddAreIgnored() {
        val report = analyze(
            """
            new java.util.ArrayList<Object>().add(new Label("listItem"));
            add("not a component");
            add(new org.apache.wicket.behavior.Behavior() {});
            new Form<Void>("form").add(new org.apache.wicket.markup.html.form.validation.AbstractFormValidator() {
                public org.apache.wicket.markup.html.form.FormComponent<?>[] getDependentFormComponents() { return null; }
                public void validate(Form<?> form) {}
            });
            add(new Label("actual"));
            """.trimIndent(),
            members = "void add(String text) {}",
        )
        assertEquals(listOf("actual"), report.children().map { it.wicketId })
    }

    fun testCustomAddOverridingWicketIsNotAssumedToAttachChildren() {
        val report = analyze(
            "add(new Label(\"notProven\"));",
            members = "public org.apache.wicket.MarkupContainer add(org.apache.wicket.Component... children) { return this; }",
        )
        assertTrue(report.children().isEmpty())
        assertFalse(report.scopeComplete)
    }

    fun testCustomConstructorDoesNotAssumeFirstStringIsId() {
        myFixture.addClass(
            """
            package example;
            public class SwappedLabel extends org.apache.wicket.markup.html.basic.Label {
                public SwappedLabel(String text, String id) { super(id, text); }
            }
            """.trimIndent(),
        )
        val node = analyze("add(new SwappedLabel(\"text\", \"actualId\"));").children().single()
        assertEquals("example.SwappedLabel", node.componentType)
        assertEquals("actualId", node.wicketId)
        assertEquals(Confidence.CONFIRMED, node.confidence)
        assertTrue(node.diagnostics.isEmpty())
    }

    fun testDynamicIdsAndConstantsRemainUnresolved() {
        val report = analyze(
            """
            add(new Label(dynamicId()));
            add(new Label(ID));
            add(new Label("prefix" + "suffix"));
            """.trimIndent(),
            members = "static final String ID = \"constant\"; String dynamicId() { return \"dynamic\"; }",
        )
        assertEquals(3, report.children().size)
        report.children().forEach {
            assertNull(it.wicketId)
            assertEquals(Confidence.UNRESOLVED, it.confidence)
            assertEquals(DiagnosticCode.UNRESOLVED_DYNAMIC_ID, it.diagnostics.single().code)
        }
    }

    fun testFactoryRemainsUnresolvedWhileLocalVariableIsRecognized() {
        val report = analyze(
            "Label heading = new Label(\"heading\"); add(heading); add(createHeader(\"header\"));",
            members = "Label createHeader(String id) { return new Label(id); }",
        )
        assertEquals(2, report.children().size)
        val heading = report.children().first()
        assertEquals("heading", heading.wicketId)
        assertEquals(Confidence.CONFIRMED, heading.confidence)
        val factory = report.children().last()
        assertNull(factory.wicketId)
        assertEquals(Confidence.UNRESOLVED, factory.confidence)
        assertEquals(DiagnosticCode.UNRESOLVED_CONSTRUCTOR, factory.diagnostics.single().code)
    }

    fun testOtherContainerHasUnresolvedParent() {
        val report = analyze("new WebMarkupContainer(\"other\").add(new Label(\"child\"));")
        val child = report.children().single()
        assertEquals("child", child.wicketId)
        assertNull(child.parentNodeId)
        assertEquals(Confidence.UNRESOLVED, child.confidence)
        assertEquals(DiagnosticCode.UNRESOLVED_PARENT, child.diagnostics.single().code)
    }

    fun testConditionalLambdaAndHelperScopesAreNotConfirmed() {
        val report = analyze(
            """
            if (enabled()) add(new Label("conditional"));
            Runnable deferred = () -> add(new Label("deferred"));
            """.trimIndent(),
            members = "boolean enabled() { return true; } void setup() { add(new Label(\"helper\")); }",
        )
        assertEquals(listOf("conditional", "deferred", "helper"), report.children().map { it.wicketId })
        report.children().forEach {
            assertEquals(Confidence.UNRESOLVED, it.confidence)
            assertNull(it.parentNodeId)
            assertTrue(it.diagnostics.any { d -> d.code == DiagnosticCode.UNRESOLVED_SCOPE })
            assertTrue(it.diagnostics.all { d -> d.severity == Severity.INFO })
        }
    }

    fun testNestedClassBodiesAndUnattachedConstructionsDoNotLeakIntoPage() {
        val report = analyze(
            """
            Label unattached = new Label("unused");
            class Local { void setup() { add(new Label("local")); } }
            add(new Label("outer") {
                void setup() { ExamplePage.this.add(new Label("anonymous")); }
            });
            """.trimIndent(),
        )
        assertEquals(1, report.children().size)
        assertEquals("outer", report.children().single().wicketId)
    }

    fun testReportIsDeterministicReadOnlyAndHasUniqueNodeIds() {
        val report = analyze("add(new Label(\"repeat\"), new Label(\"repeat\"));")
        val file = myFixture.file as PsiJavaFile
        val original = file.text
        assertEquals(report, WicketJavaAnalyzer().analyze(file.classes.single()))
        assertEquals(original, file.text)
        assertEquals(report.nodes.size, report.nodes.map { it.nodeId }.distinct().size)
        assertTrue(report.nodes.map { it.javaLocation.offset }.zipWithNext().all { (a, b) -> a <= b })
    }

    fun testAnalysisHonorsCancellation() {
        analyze("add(new Label(\"title\"));")
        val page = (myFixture.file as PsiJavaFile).classes.single()
        val indicator = EmptyProgressIndicator()
        assertThrows(ProcessCanceledException::class.java) {
            ProgressManager.getInstance().runProcess(Runnable {
                indicator.cancel()
                WicketJavaAnalyzer().analyze(page)
            }, indicator)
        }
    }

}
