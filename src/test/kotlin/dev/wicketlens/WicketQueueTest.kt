package dev.wicketlens

import com.intellij.psi.PsiJavaFile
import dev.wicketlens.analysis.WicketJavaAnalyzer
import dev.wicketlens.analysis.WicketPageAnalyzer
import dev.wicketlens.model.*

class WicketQueueTest : WicketProjectPsiTestCase() {
    fun testQueuePlacementUsesMarkupRatherThanTheQueueReceiver() {
        val java = page("queue(new Label(\"name\"), new WebMarkupContainer(\"form\"));")
        companion(html = "<div wicket:id=\"form\"><span wicket:id=\"name\"></span></div>")
        val report = analyze(java)
        matched(report, 2)
        val name = report.nodes.single { it.wicketId == "name" }
        assertEquals(report.rootNodeId, name.queuedInNodeId)
        assertEquals("form", report.nodes.single { it.nodeId == name.parentNodeId }.wicketId)
    }

    fun testQueueCanFillAnExplicitlyAddedContainer() {
        val java = page("add(new WebMarkupContainer(\"form\")); queue(new Label(\"name\"));")
        companion(html = "<div wicket:id=\"form\"><span wicket:id=\"name\"></span></div>")
        matched(analyze(java), 2)
    }

    fun testQueueScopeUsesTheNearestDeclaredReceiver() {
        val java = page("""
            WebMarkupContainer left = new WebMarkupContainer("left");
            WebMarkupContainer right = new WebMarkupContainer("right");
            left.queue(new Label("name")); right.queue(new Label("name")); queue(left, right);
        """.trimIndent())
        companion(html = "<div><div wicket:id=\"left\"><span wicket:id=\"name\"></span></div><div wicket:id=\"right\"><span wicket:id=\"name\"></span></div></div>")
        matched(analyze(java), 4)
    }

    fun testAmbiguousRootQueueTargetsRemainUnresolved() {
        val java = page("queue(new WebMarkupContainer(\"left\"), new WebMarkupContainer(\"right\"), new Label(\"name\"));")
        companion(html = "<div><div wicket:id=\"left\"><span wicket:id=\"name\"></span></div><div wicket:id=\"right\"><span wicket:id=\"name\"></span></div></div>")
        val report = analyze(java)
        assertEquals(2, report.matching!!.findings.count { it.code == DiagnosticCode.MATCHED })
        assertTrue(report.matching.findings.any { it.code == DiagnosticCode.UNRESOLVED_QUEUE })
        assertTrue(report.matching.findings.none { it.code == DiagnosticCode.MARKUP_ONLY })
    }

    fun testMissingQueuedIdIsNotReportedAsJavaOnly() {
        val java = page("queue(new Label(\"missing\"));")
        companion(html = "<html><body></body></html>")
        val report = analyze(java)
        assertEquals(DiagnosticCode.UNRESOLVED_QUEUE, report.matching!!.findings.single().code)
    }

    fun testQueueCannotInventAnUnknownMarkupContainer() {
        val java = page("queue(new Label(\"name\"));")
        companion(html = "<div wicket:id=\"unknown\"><span wicket:id=\"name\"></span></div>")
        val report = analyze(java)
        assertTrue(report.matching!!.findings.none { it.code in setOf(DiagnosticCode.MATCHED, DiagnosticCode.MARKUP_ONLY) })
    }

    fun testQueuedComponentsDoNotCrossPanelMarkupBoundaries() {
        val java = page("queue(new org.apache.wicket.markup.html.panel.Panel(\"panel\"), new Label(\"name\"));")
        companion(html = "<div wicket:id=\"panel\"><span wicket:id=\"name\"></span></div>")
        val report = analyze(java)
        assertEquals(1, report.matching!!.findings.count { it.code == DiagnosticCode.MATCHED })
        assertTrue(report.matching.findings.none { it.code == DiagnosticCode.MARKUP_ONLY })
        assertFalse(report.markup!!.elements.single { it.wicketId == "name" }.participatesInComponentTree)
    }

    fun testQueueDoesNotBindHeaderComponents() {
        val java = page("queue(new Label(\"title\"));")
        companion(html = "<html><head><title wicket:id=\"title\"></title></head><body></body></html>")
        assertTrue(analyze(java).matching!!.findings.none { it.code == DiagnosticCode.MATCHED })
    }

    fun testConditionalQueueingRetainsTheExistingScopeGuard() {
        val java = page("if (System.nanoTime() > 0) queue(new Label(\"name\"));")
        companion(html = "<span wicket:id=\"name\"></span>")
        assertTrue(analyze(java).matching!!.findings.none { it.code in setOf(DiagnosticCode.MATCHED, DiagnosticCode.MARKUP_ONLY) })
    }

    fun testQueuedFieldsAndPrivateHelpersUseTheSameProof() {
        val java = page("setup();", """
            private final WebMarkupContainer form = new WebMarkupContainer("form");
            private void setup() { queue(form, new Label("name")); }
        """)
        companion(html = "<div wicket:id=\"form\"><span wicket:id=\"name\"></span></div>")
        matched(analyze(java), 2)
    }

    fun testRepeatedQueueingAndMixedAddQueueAreNotUpgraded() {
        for ((index, body) in listOf("queue(label); queue(label);", "add(label); queue(label);").withIndex()) {
            val java = page("Label label = new Label(\"name\"); $body", name = "ExamplePage$index")
            companion("ExamplePage$index", "<span wicket:id=\"name\"></span>")
            assertTrue(analyze(java).warnings.any { it.code == DiagnosticCode.UNRESOLVED_SCOPE })
        }
    }

    fun testJavaOnlyDiscoveryDoesNotClaimQueueAttachment() {
        val java = page("queue(new Label(\"name\"));")
        val report = WicketJavaAnalyzer().analyze(java.classes.single())!!
        val node = report.nodes.single { it.wicketId == "name" }
        assertEquals(Confidence.UNRESOLVED, node.confidence)
        assertNull(node.parentNodeId)
        assertEquals(report.rootNodeId, node.queuedInNodeId)
    }

    private fun page(body: String, members: String = "", name: String = "ExamplePage") = myFixture.addFileToProject("src/main/java/example/$name.java", """
        package example; import org.apache.wicket.markup.html.WebPage;
        import org.apache.wicket.markup.html.WebMarkupContainer;
        import org.apache.wicket.markup.html.basic.Label;
        public class $name extends WebPage { public $name() { $body } $members }
    """.trimIndent()) as PsiJavaFile
    private fun analyze(java: PsiJavaFile) = WicketPageAnalyzer().analyze(java.classes.single())!!
    private fun matched(report: PageReport, count: Int) = assertEquals(report.toString(), count,
        report.matching!!.findings.count { it.code == DiagnosticCode.MATCHED })
}
