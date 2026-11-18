package dev.wicketlens

import com.intellij.psi.PsiJavaFile
import dev.wicketlens.analysis.WicketPageAnalyzer
import dev.wicketlens.editor.WicketIdPsi
import dev.wicketlens.model.*

class WicketTransparentMarkupTest : WicketProjectPsiTestCase() {
    private fun htmlClass(extra: String = "") = myFixture.addFileToProject("src/main/java/example/Html.java", """
        package example;
        public class Html extends org.apache.wicket.markup.html.TransparentWebMarkupContainer {
            public Html(String id) { super(id); add(new org.apache.wicket.behavior.Behavior()); }
            $extra
        }
    """.trimIndent())
    private fun analyze(body: String, html: String): PageReport {
        val java = pageFile(body = body)
        companion(html = html)
        return WicketPageAnalyzer().analyze(java.classes.single())!!
    }
    private fun matches(report: PageReport) = report.matching!!.findings.filter { it.code == DiagnosticCode.MATCHED }

    fun testTransparentHtmlKeepsItsBindingWhileHeadAndBodyResolveAgainstThePage() {
        htmlClass()
        val report = analyze("add(new Html(\"html\"), new Label(\"title\"), new Label(\"body\"));", """
            <html wicket:id="html"><head><title wicket:id="title"></title></head><body><span wicket:id="body"></span></body></html>
        """)
        assertEquals(report.warnings.toString(), 3, matches(report).size)
        assertNull(report.markup!!.elements.single { it.wicketId == "title" }.parentWicketElementId)
        assertNull(report.markup.elements.single { it.wicketId == "body" }.parentWicketElementId)
        assertTrue(report.markup.diagnostics.none { "HTML head" in it.message })
    }

    fun testOrdinaryChildrenWinBeforeTransparentParentFallback() {
        htmlClass()
        val report = analyze("""
            Html shell = new Html("shell"); shell.add(new Label("title")); add(shell, new Label("title"));
        """, "<div wicket:id='shell'><span wicket:id='title'></span></div><span wicket:id='title'></span>")
        assertEquals(report.warnings.toString(), 3, matches(report).size)
        val shell = report.markup!!.elements.single { it.wicketId == "shell" }
        assertEquals(setOf(null, shell.nodeId), report.markup.elements.filter { it.wicketId == "title" }.map { it.parentWicketElementId }.toSet())
    }

    fun testNestedTransparentContainersResolveThroughBothFallbackLevels() {
        htmlClass()
        val report = analyze("""
            Html outer = new Html("outer"); outer.add(new Html("inner")); add(outer, new Label("title"));
        """, "<html wicket:id='outer'><div wicket:id='inner'><span wicket:id='title'></span></div></html>")
        assertEquals(report.warnings.toString(), 3, matches(report).size)
        assertNull(report.markup!!.elements.single { it.wicketId == "title" }.parentWicketElementId)
    }

    fun testOrdinaryContainerInsideTransparentMarkupRemainsARealBoundary() {
        htmlClass()
        val report = analyze("""
            Html shell = new Html("shell"); shell.add(new org.apache.wicket.markup.html.WebMarkupContainer("box")); add(shell, new Label("title"));
        """, "<div wicket:id='shell'><section wicket:id='box'><span wicket:id='title'></span></section></div>")
        assertEquals(setOf("shell", "box"), matches(report).map { finding -> report.nodes.single { it.nodeId == finding.javaNodeId }.wicketId }.toSet())
        assertNull(report.nodes.single { it.wicketId == "title" }.markupLocation)
    }

    fun testRepeatedFallbackLocationsDoNotChooseOneByTraversalOrder() {
        htmlClass()
        val report = analyze("add(new Html(\"first\"), new Html(\"second\"), new Label(\"title\"));", """
            <div wicket:id='first'><span wicket:id='title'></span></div>
            <div wicket:id='second'><span wicket:id='title'></span></div>
        """)
        assertNull(report.nodes.single { it.wicketId == "title" }.markupLocation)
        assertTrue(report.markup!!.elements.filter { it.wicketId == "title" }.all { it.confidence == Confidence.UNRESOLVED })
    }

    fun testCustomResolverOverrideDoesNotAcquireTheStandardTransparentContract() {
        htmlClass("""
            @Override public org.apache.wicket.Component resolve(org.apache.wicket.MarkupContainer parent,
                org.apache.wicket.markup.MarkupStream stream, org.apache.wicket.markup.ComponentTag tag) { return null; }
        """)
        val report = analyze("add(new Html(\"html\"), new Label(\"title\"));", "<html wicket:id='html'><head><title wicket:id='title'></title></head></html>")
        assertTrue(matches(report).isEmpty())
        assertTrue(report.markup!!.diagnostics.any { "HTML head" in it.message })
    }

    fun testSimpleMessagesAndAttributeLocalizationDoNotInventComponentScopes() {
        val report = analyze("add(new Label(\"title\"));", """
            <html><body><wicket:message key='skip'>Skip</wicket:message>
            <button wicket:message='aria-label:toggle'>Toggle</button><span wicket:id='title'></span></body></html>
        """)
        assertEquals(report.warnings.toString(), 1, matches(report).size)
        assertTrue(report.markup!!.diagnostics.isEmpty())
        assertEquals(listOf("title"), report.markup.elements.filter { it.hasWicketId }.map { it.wicketId })
    }

    fun testUnsupportedMessageBodyStaysLocalToItsSubtree() {
        val report = analyze("add(new Label(\"title\"), new Label(\"embedded\"));", """
            <div><wicket:message key='text'><span wicket:id='embedded'></span></wicket:message><span wicket:id='title'></span></div>
        """)
        assertEquals(1, matches(report).size)
        assertEquals("title", report.nodes.single { it.markupLocation != null && it.nodeId != report.rootNodeId }.wicketId)
        assertTrue(report.markup!!.elements.single { it.wicketId == "embedded" }.diagnostics.isNotEmpty())
    }

    fun testTransparentBindingsNavigateInBothDirections() {
        htmlClass()
        val java = pageFile(body = "add(new Html(\"html\"), new Label(\"title\"));")
        val html = companion(html = "<html wicket:id=\"html\"><head><title wicket:id=\"title\"></title></head></html>")
        assertEquals(html.idValue("title"), WicketIdPsi.pair(java.idValue("title"))?.attribute)
        assertEquals(java.idValue("title"), WicketIdPsi.pair(html.idValue("title"))?.literal)
    }

    fun testNamespaceDeclarationsOnAnExtensionAreNotComponentAttributes() {
        val base = myFixture.addFileToProject("src/main/java/example/Base.java", "package example; public class Base extends org.apache.wicket.markup.html.WebPage {}") as PsiJavaFile
        companion("Base", "<html><body><wicket:child/></body></html>")
        val java = myFixture.addFileToProject("src/main/java/example/ExamplePage.java", "package example; public class ExamplePage extends Base { public ExamplePage() { add(new org.apache.wicket.markup.html.basic.Label(\"title\")); } }") as PsiJavaFile
        companion(html = "<wicket:extend xmlns:wicket='http://wicket.apache.org'><span wicket:id='title'></span></wicket:extend>")
        assertEquals(1, matches(WicketPageAnalyzer().analyze(java.classes.single())!!).size)
        assertTrue(base.isValid)
    }

    fun testAnAmbiguousTransparentParentDoesNotPromoteItsChildAtThePageRoot() {
        htmlClass()
        val report = analyze("""
            Html shell = new Html("shell"); shell.add(new Label("title")); add(shell, new Label("title"));
        """, "<div wicket:id='shell'><span wicket:id='title'></span></div><div wicket:id='shell'></div>")
        assertTrue(matches(report).isEmpty())
    }

    fun testTransparentDiscoveryDepthIsBoundedAndExplicit() {
        htmlClass()
        val nested = "<div>".repeat(140) + "<span wicket:id='title'></span>" + "</div>".repeat(140)
        val report = analyze("add(new Html(\"shell\"), new Label(\"title\"));", "<div wicket:id='shell'>$nested</div>")
        assertTrue(matches(report).isEmpty())
        assertTrue(report.markup!!.diagnostics.any { "128-level" in it.message })
    }

    fun testCustomMarkupProvidersDoNotMatchCallerChildrenAgainstTheirPreviewBody() {
        myFixture.addFileToProject("src/main/java/example/Box.java", """
            package example; public class Box extends org.apache.wicket.markup.html.WebMarkupContainer {
                public Box(String id) { super(id); add(new org.apache.wicket.behavior.Behavior()); }
                @Override public String getMarkupType() { return "other"; }
            }
        """.trimIndent())
        val report = analyze("var box = new Box(\"box\"); box.add(new Label(\"title\")); add(box);", "<div wicket:id='box'><span wicket:id='title'></span></div>")
        assertNull(report.nodes.single { it.wicketId == "title" }.markupLocation)
        assertTrue(report.nodes.single { it.wicketId == "box" }.diagnostics.any { it.code == DiagnosticCode.UNRESOLVED_SCOPE })
    }
}
