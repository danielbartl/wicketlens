package dev.wicketlens

import com.intellij.psi.PsiJavaFile
import dev.wicketlens.analysis.WicketPageSelection
import dev.wicketlens.export.WicketContextExporter
import dev.wicketlens.model.Confidence
import dev.wicketlens.model.DiagnosticCode
import dev.wicketlens.model.PageReport
import dev.wicketlens.model.Severity
import java.nio.file.Files
import java.nio.file.Path

class WicketInitializationTest : WicketProjectPsiTestCase() {
    private var sequence = 0

    fun testDirectPageInitializationAddsMatchWithSourceLocations() {
        val report = report(initialization = "super.onInitialize(); add(new Label(\"title\"));")
        assertMatched(report, 1)
        val child = report.children().single()
        assertEquals(report.rootNodeId, child.parentNodeId)
        assertNotNull(child.markupLocation)
        assertTrue(child.javaLocation.offset > report.nodes.first().javaLocation.offset)
    }

    fun testConstructorAndInitializationAreCombined() {
        val report = report("add(new Label(\"early\"));", "super.onInitialize(); this.add(new Label(\"title\"));",
            html = "<span wicket:id=\"early\"></span><span wicket:id=\"title\"></span>")
        assertMatched(report, 2)
    }

    fun testInitializationLocalsRetainNestedParentsAndRepeatedIds() {
        val report = report(initialization = """
            super.onInitialize();
            Form<?> left = new Form<>("left");
            Form<?> right = new Form<>("right");
            Label title = new Label("title");
            left.add(title);
            add(left, right);
            right.add(new Label("title"));
        """.trimIndent(), html = "<form wicket:id=\"right\"><span wicket:id=\"title\"></span></form>" +
            "<form wicket:id=\"left\"><span wicket:id=\"title\"></span></form>")
        assertMatched(report, 4)
        val byId = report.nodes.associateBy { it.nodeId }
        assertEquals(setOf("left", "right"), report.children().filter { it.wicketId == "title" }
            .map { byId[it.parentNodeId]?.wicketId }.toSet())
    }

    fun testSameLocalNameInEachPhaseKeepsSeparateIdentity() {
        val report = report("Label label = new Label(\"early\"); add(label);",
            "super.onInitialize(); Label label = new Label(\"title\"); add(label);",
            html = "<span wicket:id=\"early\"></span><span wicket:id=\"title\"></span>")
        assertMatched(report, 2)
        assertEquals(2, report.children().map { it.nodeId }.distinct().size)
    }

    fun testPanelInitializationWithSuperAdd() {
        val report = report(initialization = "super.onInitialize(); super.add(new Label(\"title\"));",
            base = "Panel", constructorPrefix = "super(\"root\");",
            html = "<wicket:panel><span wicket:id=\"title\"></span></wicket:panel>")
        assertMatched(report, 1)
    }

    fun testImplicitConstructorAndNoOverrideAnnotationAreSupported() {
        val report = report(initialization = null, omitConstructor = true, members = """
            public void onInitialize() { ; /* before delegation */ super.onInitialize(); add(new Label("title")); }
        """.trimIndent())
        assertMatched(report, 1)
    }

    fun testKnownUnmatchedIdsRemainInformational() {
        val report = report(initialization = "super.onInitialize(); add(new Label(\"javaOnly\"));",
            html = "<span wicket:id=\"markupOnly\"></span>")
        assertEquals(setOf(DiagnosticCode.JAVA_ONLY, DiagnosticCode.MARKUP_ONLY), findings(report).map { it.code }.toSet())
        assertTrue(report.warnings.none { it.code == DiagnosticCode.UNRESOLVED_SCOPE })
    }

    fun testEmptySupportedHookPreservesConstructorMatching() {
        assertMatched(report("add(new Label(\"title\"));", "super.onInitialize();"), 1)
    }

    fun testUncertainInitializationRetainsKnownLocalEdges() {
        val report = report(initialization = """
            super.onInitialize();
            Form<?> form = new Form<>("form");
            form.add(new Label("title"));
            add(form);
            removeAll();
        """.trimIndent(), html = "<form wicket:id=\"form\"><span wicket:id=\"title\"></span></form>")
        val form = report.children().single { it.wicketId == "form" }
        assertNull(form.parentNodeId)
        assertEquals(form.nodeId, report.children().single { it.wicketId == "title" }.parentNodeId)
        assertUncertain(report)
    }

    fun testIncompleteInitializationDoesNotLeaveConfirmedMatches() {
        val report = report("add(new Label(\"title\"));", "super.onInitialize(); int broken = ;")
        assertTrue(report.warnings.any { it.message.contains("Java syntax") })
        assertUncertain(report)
    }

    fun testCustomAddOverrideIsNotAssumedToAttachDuringInitialization() {
        val report = report(initialization = "super.onInitialize(); add(new Label(\"title\"));", members = """
            public org.apache.wicket.MarkupContainer add(org.apache.wicket.Component... children) { return this; }
        """.trimIndent())
        assertTrue(report.children().isEmpty())
        assertUncertain(report)
    }

    fun testMissingLateConditionalAndRepeatedSuperCallsAreUnresolved() {
        for (body in listOf(
            "add(new Label(\"title\"));",
            "add(new Label(\"title\")); super.onInitialize();",
            "if (true) super.onInitialize(); add(new Label(\"title\"));",
            "super.onInitialize(); super.onInitialize(); add(new Label(\"title\"));",
        )) assertUncertain(report("add(new Label(\"early\"));", body))
    }

    fun testInitializationControlFlowInvalidatesBothPhases() {
        for (flow in listOf("if (System.nanoTime() > 0) return;", "for (int i=0; i<1; i++) {}", "try {} finally {}")) {
            assertUncertain(report("add(new Label(\"early\"));", "super.onInitialize(); $flow add(new Label(\"title\"));"))
        }
    }

    fun testInitializationRootMutationsInvalidateEarlierConstructorMatches() {
        for (mutation in listOf("removeAll();", "remove(\"title\");", "replace(new Label(\"title\"));",
            "addOrReplace(new Label(\"title\"));")) {
            assertUncertain(report("add(new Label(\"title\"));", "super.onInitialize(); $mutation"))
        }
    }

    fun testInitializationQueuePreservesEarlierMatchesAndReportsUnplacedChildren() {
        val report = report("add(new Label(\"title\"));", "super.onInitialize(); queue(new Label(\"other\"));")
        assertEquals(DiagnosticCode.MATCHED, findings(report).single { it.javaNodeId == report.children().single { it.wicketId == "title" }.nodeId }.code)
        assertEquals(DiagnosticCode.UNRESOLVED_QUEUE, findings(report).single { it.javaNodeId == report.children().single { it.wicketId == "other" }.nodeId }.code)
        assertTrue(findings(report).none { it.code in setOf(DiagnosticCode.JAVA_ONLY, DiagnosticCode.MARKUP_ONLY) })
    }

    fun testInitializationHelperEffectsInvalidateBothPhases() {
        assertUncertain(report("add(new Label(\"title\"));", "super.onInitialize(); setup();",
            members = "void setup() { removeAll(); }"))
    }

    fun testManualInitializationFromConstructorIsNotTreatedAsOnceOnly() {
        for (invocation in listOf("onInitialize();", "this.onInitialize();", "super.onInitialize();")) {
            assertUncertain(report(invocation, "super.onInitialize(); add(new Label(\"title\"));"))
        }
    }

    fun testConstructorUncertaintyAlsoInvalidatesInitialization() {
        for (body in listOf("if (System.nanoTime() > 0) return;", "removeAll();", "setup();")) {
            assertUncertain(report(body, "super.onInitialize(); add(new Label(\"title\"));",
                members = "void setup() {}"))
        }
    }

    fun testAlternativeConstructorsAreNotReconciledWithInitialization() {
        assertUncertain(report(initialization = "super.onInitialize(); add(new Label(\"title\"));",
            members = "public INITIALIZATION_PAGE(int ignored) {}"))
    }

    fun testRootMethodReferencesAndNestedEffectsInvalidateBothPhases() {
        for (effect in listOf(
            "java.util.function.Consumer<org.apache.wicket.Component> attach = this::add;",
            "Runnable change = super::removeAll;",
            "new Object() {{ INITIALIZATION_PAGE.this.removeAll(); }};",
            "new Object() {{ removeAll(); }};",
        )) assertUncertain(report("add(new Label(\"title\"));", "super.onInitialize(); $effect"))
    }

    fun testPassingTheRootToExternalCodeInvalidatesBothPhases() {
        assertUncertain(report("add(new Label(\"title\"));", "super.onInitialize(); consume(this);",
            members = "static void consume(Object root) {}"))
    }

    fun testDeferredAdditionsDoNotPreserveEarlierMatches() {
        assertUncertain(report("add(new Label(\"title\"));",
            "super.onInitialize(); Runnable later = () -> add(new Label(\"later\"));"))
    }

    fun testOverloadsAndOtherLifecycleMethodsAreNotInitializationScopes() {
        for (method in listOf("void onInitialize(String ignored)", "protected void onConfigure()")) {
            val report = report(initialization = null, members = "$method { add(new Label(\"title\")); }")
            assertUncertain(report)
        }
    }

    fun testInheritedInitializationIsNotExpandedOrAssumedHarmless() {
        myFixture.addFileToProject("src/main/java/example/BaseInitializationPage.java", """
            package example;
            public class BaseInitializationPage extends org.apache.wicket.markup.html.WebPage {
                protected void onInitialize() { super.onInitialize(); removeAll(); }
            }
        """.trimIndent())
        for (initialization in listOf(null, "super.onInitialize(); add(new Label(\"later\"));")) {
            assertUncertain(report("add(new Label(\"title\"));", initialization, base = "BaseInitializationPage"))
        }
    }

    fun testSourceInheritedSetupWorksWithLocalInitialization() {
        myFixture.addFileToProject("src/main/java/example/PlainBase.java",
            "package example; public class PlainBase extends org.apache.wicket.markup.html.WebPage {}")
        assertMatched(report(initialization = "super.onInitialize(); add(new Label(\"title\"));", base = "PlainBase"), 1)
    }

    fun testFieldsAndAliasesDoNotBecomeCrossPhaseDataFlow() {
        val fields = report("label = new Label(\"title\");", "super.onInitialize(); add(label);", members = "Label label;")
        assertNull(fields.children().single().wicketId)
        assertTrue(fields.children().single().diagnostics.any { it.code == DiagnosticCode.UNRESOLVED_VARIABLE })
        assertUncertain(fields)
        val aliases = report(initialization = "super.onInitialize(); Label a = new Label(\"title\"); Label b = a; add(b);")
        assertNull(aliases.children().single().wicketId)
        assertUncertain(aliases)
    }

    fun testReassignedLocalsAndEscapingContainersRemainUnresolved() {
        for (body in listOf(
            "Label a = new Label(\"title\"); add(a); a = new Label(\"other\");",
            "Form<?> form = new Form<>(\"form\"); form.add(new Label(\"title\")); add(form); consume(form);",
        )) assertUncertain(report(initialization = "super.onInitialize(); $body", members = "static Object escaped; static void consume(Object value) { escaped = value; }"))
    }

    fun testDynamicIdsAndBranchingFactoriesKeepTheirCauses() {
        for ((expression, code) in listOf(
            "new Label(System.getProperty(\"id\"))" to DiagnosticCode.UNRESOLVED_DYNAMIC_ID,
            "makeLabel()" to DiagnosticCode.UNRESOLVED_CONSTRUCTOR,
        )) {
            val report = report(initialization = "super.onInitialize(); add($expression);",
                members = "static Label makeLabel() { if (System.nanoTime() > 0) return new Label(\"title\"); return new Label(\"other\"); }")
            assertTrue(report.children().single().diagnostics.any { it.code == code })
            assertUncertain(report)
        }
    }

    fun testDuplicateIdsAcrossPhasesDoNotChooseOneMatch() {
        val report = report("add(new Label(\"title\"));", "super.onInitialize(); add(new Label(\"title\"));")
        assertEquals(2, report.children().size)
        assertTrue(findings(report).none { it.code in CLAIMS })
    }

    fun testUnrelatedMethodReferencesAndCollectionAddsRemainHarmless() {
        val report = report(initialization = """
            super.onInitialize();
            java.util.List<String> values = new java.util.ArrayList<>();
            values.add("value");
            Runnable later = values::clear;
            add(new Label("title"));
        """.trimIndent())
        assertMatched(report, 1)
    }

    fun testDemoCombinesBothPhasesThroughTheFullPipeline() {
        val source = Path.of(requireNotNull(System.getProperty("wicketlens.test.demo")))
        val java = myFixture.addFileToProject("src/main/java/example/InitializationPage.java",
            Files.readString(source.resolve("main/java/example/InitializationPage.java")))
        companion("InitializationPage", Files.readString(source.resolve("main/resources/example/InitializationPage.html")))
        val report = WicketPageSelection().analyze(project, java.virtualFile.url, -1)
        assertMatched(report, 6)
        val form = report.children().single { it.wicketId == "form" }
        assertEquals(setOf("name", "enabled", "save"), report.children().filter { it.parentNodeId == form.nodeId }
            .map { it.wicketId }.toSet())
    }

    private fun report(
        constructor: String = "",
        initialization: String? = null,
        members: String = "",
        html: String = "<span wicket:id=\"title\"></span>",
        base: String = "WebPage",
        constructorPrefix: String = "",
        omitConstructor: Boolean = false,
    ): PageReport {
        val name = "InitializationPage${sequence++}"
        val hook = initialization?.let { "@Override protected void onInitialize() { $it }" }.orEmpty()
        val ctor = if (omitConstructor) "" else "public $name() { $constructorPrefix $constructor }"
        val java = myFixture.addFileToProject("src/main/java/example/$name.java", """
            package example;
            import org.apache.wicket.markup.html.WebPage;
            import org.apache.wicket.markup.html.panel.Panel;
            import org.apache.wicket.markup.html.basic.Label;
            import org.apache.wicket.markup.html.form.Form;
            public class $name extends $base { $ctor $hook $members }
        """.trimIndent().replace("INITIALIZATION_PAGE", name)) as PsiJavaFile
        val markup = companion(name, "<html xmlns:wicket=\"http://wicket.apache.org\"><body>$html</body></html>")
        val before = java.text to markup.text
        val stamps = java.modificationStamp to markup.modificationStamp
        val selection = WicketPageSelection()
        val report = selection.analyze(project, java.virtualFile.url, -1)
        assertEquals(markup.virtualFile.path, report.markupPath)
        assertFalse(report.scopeComplete)
        assertTrue(report.warnings.any { it.code == DiagnosticCode.PARTIAL_ANALYSIS })
        assertTrue(findings(report).all { it.severity == Severity.INFO })
        report.children().filter { it.confidence == Confidence.CONFIRMED }.forEach { node ->
            assertEquals(java.virtualFile.path, node.javaLocation.file)
            assertTrue(java.text.substring(node.javaLocation.offset).startsWith("new "))
        }
        assertEquals(WicketContextExporter().export(report), WicketContextExporter().export(selection.analyze(project, java.virtualFile.url, -1)))
        assertEquals(before, java.text to markup.text)
        assertEquals(stamps, java.modificationStamp to markup.modificationStamp)
        return report
    }

    private fun findings(report: PageReport) = requireNotNull(report.matching).findings

    private fun assertMatched(report: PageReport, count: Int) {
        assertEquals(count, report.children().size)
        assertEquals(findings(report).toString(), count, findings(report).size)
        assertTrue(findings(report).toString(), findings(report).all { it.code == DiagnosticCode.MATCHED })
        assertTrue(report.children().all { it.confidence == Confidence.CONFIRMED })
        assertTrue(report.warnings.toString(), report.warnings.none { it.code == DiagnosticCode.UNRESOLVED_SCOPE })
    }

    private fun assertUncertain(report: PageReport) {
        assertTrue(findings(report).toString(), findings(report).none { it.code in CLAIMS })
        assertTrue(report.children().all { it.confidence == Confidence.UNRESOLVED && it.markupLocation == null })
        assertTrue(findings(report).all { it.causes.isNotEmpty() })
    }

    companion object {
        private val CLAIMS = setOf(DiagnosticCode.MATCHED, DiagnosticCode.JAVA_ONLY, DiagnosticCode.MARKUP_ONLY)
    }
}
