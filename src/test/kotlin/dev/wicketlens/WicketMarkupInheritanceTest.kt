package dev.wicketlens

import com.intellij.psi.PsiJavaFile
import dev.wicketlens.analysis.WicketPageAnalyzer
import dev.wicketlens.export.WicketContextExporter
import dev.wicketlens.model.Confidence
import dev.wicketlens.model.DiagnosticCode
import dev.wicketlens.model.PageReport
import java.nio.file.Files
import java.nio.file.Path

class WicketMarkupInheritanceTest : WicketProjectPsiTestCase() {
    fun testExtensionMatchesInsideBaseLayoutWithOriginalOffsets() {
        page("BasePage")
        val base = companion("BasePage", "<html><body><span wicket:id=\"header\"></span><wicket:child/></body></html>")
        val java = page("DetailPage", "BasePage", "add(new Label(\"title\"));")
        val html = companion("DetailPage", "<html><body><wicket:extend><h1 wicket:id=\"title\"></h1></wicket:extend></body></html>")
        val before = listOf(java.text, base.text, html.text)
        val report = analyze(java)
        assertEquals(html.virtualFile.path, report.markupPath)
        val match = report.matching!!.findings.single { it.code == DiagnosticCode.MATCHED }
        assertEquals(html.virtualFile.path, match.markupLocation!!.file)
        assertEquals(html.text.indexOf("<h1"), match.markupLocation.offset)
        assertEquals(Confidence.CONFIRMED, report.markup!!.elements.single { it.wicketId == "header" }.confidence)
        assertEquals(listOf(base.virtualFile.path, html.virtualFile.path), report.markup.sourceFiles)
        assertEquals(base.virtualFile.path, report.matching.findings.single { it.code == DiagnosticCode.MARKUP_ONLY }.markupLocation!!.file)
        assertEquals(before, listOf(java.text, base.text, html.text))
        assertEquals(WicketContextExporter().export(report), WicketContextExporter().export(analyze(java)))
    }

    fun testSlotPreservesWicketParentAcrossFiles() {
        page("BasePage")
        val base = companion("BasePage", "<form wicket:id=\"form\"><div><wicket:child/></div></form>")
        val java = page("DetailPage", "BasePage", """
            org.apache.wicket.markup.html.form.Form<?> form = new org.apache.wicket.markup.html.form.Form<>("form");
            form.add(new Label("title")); add(form);
        """.trimIndent())
        companion("DetailPage", "<wicket:extend><section><span wicket:id=\"title\"></span></section></wicket:extend>")
        val report = analyze(java)
        assertMatches(report, 2)
        val form = report.markup!!.elements.single { it.wicketId == "form" }
        assertEquals(base.virtualFile.path, form.location.file)
        assertEquals(form.nodeId, report.markup.elements.single { it.wicketId == "title" }.parentWicketElementId)
    }

    fun testMissingOwnMarkupUsesNearestAncestor() {
        page("BasePage")
        val base = companion("BasePage", "<span wicket:id=\"title\"></span>")
        val report = analyze(page("DetailPage", "BasePage", "add(new Label(\"title\"));"))
        assertMatches(report, 1)
        assertEquals(base.virtualFile.path, report.markupPath)
    }

    fun testMissingIntermediateMarkupIsSkipped() {
        page("BasePage")
        companion("BasePage", "<wicket:child/>")
        page("MiddlePage", "BasePage")
        val java = page("DetailPage", "MiddlePage", "add(new Label(\"title\"));")
        companion("DetailPage", "<wicket:extend><span wicket:id=\"title\"></span></wicket:extend>")
        assertMatches(analyze(java), 1)
    }

    fun testMultipleLevelsComposeAndRetainDistinctIdsAtEqualOffsets() {
        page("BasePage")
        companion("BasePage", "<html><body><wicket:child/></body></html>")
        page("MiddlePage", "BasePage")
        companion("MiddlePage", "<wicket:extend><div wicket:id=\"middle\"><wicket:child/></div></wicket:extend>")
        val java = page("DetailPage", "MiddlePage", """
            org.apache.wicket.markup.html.WebMarkupContainer middle = new org.apache.wicket.markup.html.WebMarkupContainer("middle");
            middle.add(new Label("title")); add(middle);
        """.trimIndent())
        companion("DetailPage", "<wicket:extend><span wicket:id=\"title\"></span></wicket:extend>")
        val report = analyze(java)
        assertMatches(report, 2)
        assertEquals(3, report.markup!!.sourceFiles.size)
        assertEquals(report.markup.elements.size, report.markup.elements.map { it.nodeId }.distinct().size)
    }

    fun testOwnOrdinaryMarkupReplacesTheLayout() {
        page("BasePage")
        companion("BasePage", "<span wicket:id=\"ignored\"></span><wicket:child/>")
        val java = page("DetailPage", "BasePage", "add(new Label(\"title\"));")
        val html = companion("DetailPage", "<span wicket:id=\"title\"></span>")
        val report = analyze(java)
        assertMatches(report, 1)
        assertEquals(listOf(html.virtualFile.path), report.markup!!.sourceFiles)
        assertTrue(report.markup.elements.none { it.wicketId == "ignored" })
    }

    fun testDerivedPreviewIdsAreInactive() {
        page("BasePage")
        companion("BasePage", "<wicket:child/>")
        val java = page("DetailPage", "BasePage", "add(new Label(\"title\"));")
        companion("DetailPage", "<html><body><span wicket:id=\"preview\"></span>" +
            "<wicket:extend><span wicket:id=\"title\"></span></wicket:extend></body></html>")
        val report = analyze(java)
        assertMatches(report, 1)
        assertFalse(report.markup!!.elements.single { it.wicketId == "preview" }.participatesInComponentTree)
    }

    fun testPanelMarkupInheritance() {
        myFixture.addFileToProject("src/main/java/example/BasePanel.java", """
            package example;
            public class BasePanel extends org.apache.wicket.markup.html.panel.Panel { public BasePanel() { super("root"); } }
        """.trimIndent())
        companion("BasePanel", "<html><body><span wicket:id=\"preview\"></span><wicket:panel><wicket:child/></wicket:panel></body></html>")
        val java = page("DetailPanel", "BasePanel", "add(new Label(\"title\"));")
        companion("DetailPanel", "<wicket:extend><span wicket:id=\"title\"></span></wicket:extend>")
        val report = analyze(java)
        assertMatches(report, 1)
        assertEquals("PANEL", report.markup!!.kind.name)
        assertFalse(report.markup.elements.single { it.wicketId == "preview" }.participatesInComponentTree)
    }

    fun testMissingOrMultipleSlotsDoNotConfirmMatches() {
        for ((index, markup) in listOf("<div>No slot</div>", "<wicket:child/><wicket:child/>").withIndex()) {
            page("BasePage$index")
            companion("BasePage$index", markup)
            val java = page("DetailPage$index", "BasePage$index", "add(new Label(\"title\"));")
            companion("DetailPage$index", "<wicket:extend><span wicket:id=\"title\"></span></wicket:extend>")
            assertUnresolved(analyze(java))
        }
    }

    fun testMissingAncestorAndUnfilledSlotsStayUnresolved() {
        val java = page("DetailPage", body = "add(new Label(\"title\"));")
        companion("DetailPage", "<wicket:extend><span wicket:id=\"title\"></span></wicket:extend>")
        assertUnresolved(analyze(java))
        val base = page("BasePage", body = "add(new Label(\"title\"));")
        companion("BasePage", "<span wicket:id=\"title\"></span><wicket:child/>")
        assertUnresolved(analyze(base))
    }

    fun testAmbiguousAncestorIsNotSkipped() {
        page("BasePage")
        companion("BasePage", "<wicket:child/>")
        myFixture.addFileToProject("src/main/java/example/BasePage.html", "<wicket:child/>")
        val java = page("DetailPage", "BasePage", "add(new Label(\"title\"));")
        companion("DetailPage", "<wicket:extend><span wicket:id=\"title\"></span></wicket:extend>")
        val report = analyze(java)
        assertUnresolved(report)
        assertTrue(report.warnings.any { it.message.contains("Multiple companion") })
    }

    fun testDuplicateIdsAcrossFilesAreUnresolved() {
        page("BasePage")
        companion("BasePage", "<span wicket:id=\"title\"></span><wicket:child/>")
        val java = page("DetailPage", "BasePage", "add(new Label(\"title\"));")
        companion("DetailPage", "<wicket:extend><span wicket:id=\"title\"></span></wicket:extend>")
        assertUnresolved(analyze(java))
    }

    fun testMultipleExtensionsAndMalformedBaseAreUnresolved() {
        for ((index, pair) in listOf(
            "<wicket:child/>" to "<wicket:extend><span wicket:id=\"title\"></span></wicket:extend><wicket:extend/>",
            "<div><wicket:child/>" to "<wicket:extend><span wicket:id=\"title\"></span></wicket:extend>",
        ).withIndex()) {
            page("BasePage$index")
            companion("BasePage$index", pair.first)
            val java = page("DetailPage$index", "BasePage$index", "add(new Label(\"title\"));")
            companion("DetailPage$index", pair.second)
            assertUnresolved(analyze(java))
        }
    }

    fun testInheritedJavaSetupMatchesBothSourceClasses() {
        page("BasePage", body = "add(new Label(\"header\"));")
        companion("BasePage", "<span wicket:id=\"header\"></span><wicket:child/>")
        val java = page("DetailPage", "BasePage", "add(new Label(\"title\"));")
        companion("DetailPage", "<wicket:extend><span wicket:id=\"title\"></span></wicket:extend>")
        val report = analyze(java)
        assertEquals(setOf("title", "header"), report.children().map { it.wicketId }.toSet())
        assertEquals(2, report.matching!!.findings.count { it.code == DiagnosticCode.MATCHED })
        assertTrue(report.matching.findings.none { it.code == DiagnosticCode.MARKUP_ONLY })
    }

    fun testSlotAttributesAndNestedContentRetainUncertainty() {
        for ((index, slot) in listOf("<wicket:child wicket:enclosure=\"title\"/>",
            "<wicket:child><span>fallback</span></wicket:child>", "<wicket:child>fallback</wicket:child>").withIndex()) {
            page("BasePage$index")
            companion("BasePage$index", slot)
            val java = page("DetailPage$index", "BasePage$index", "add(new Label(\"title\"));")
            companion("DetailPage$index", "<wicket:extend><span wicket:id=\"title\"></span></wicket:extend>")
            assertUnresolved(analyze(java))
        }
    }

    fun testDemoLayoutMatchesThroughTheFullPipeline() {
        val source = Path.of(requireNotNull(System.getProperty("wicketlens.test.demo")))
        myFixture.addFileToProject("src/main/java/example/BaseLayoutPage.java",
            Files.readString(source.resolve("main/java/example/BaseLayoutPage.java")))
        companion("BaseLayoutPage", Files.readString(source.resolve("main/resources/example/BaseLayoutPage.html")))
        val java = myFixture.addFileToProject("src/main/java/example/InheritedLayoutPage.java",
            Files.readString(source.resolve("main/java/example/InheritedLayoutPage.java"))) as PsiJavaFile
        companion("InheritedLayoutPage", Files.readString(source.resolve("main/resources/example/InheritedLayoutPage.html")))
        val report = analyze(java)
        assertMatches(report, 3)
        assertEquals(2, report.markup!!.sourceFiles.size)
    }

    private fun page(name: String, base: String = "org.apache.wicket.markup.html.WebPage", body: String = "") =
        myFixture.addFileToProject("src/main/java/example/$name.java", """
            package example;
            import org.apache.wicket.markup.html.basic.Label;
            public class $name extends $base { public $name() { $body } }
        """.trimIndent()) as PsiJavaFile

    private fun analyze(java: PsiJavaFile) = requireNotNull(WicketPageAnalyzer().analyze(java.classes.single()))
    private fun assertMatches(report: PageReport, count: Int) {
        assertEquals(report.matching.toString(), count, report.matching!!.findings.count { it.code == DiagnosticCode.MATCHED })
        assertTrue(report.markup!!.scopeComplete)
    }
    private fun assertUnresolved(report: PageReport) {
        assertFalse(report.markup!!.scopeComplete)
        assertTrue(report.matching.toString(), report.matching!!.findings.none {
            it.code in setOf(DiagnosticCode.MATCHED, DiagnosticCode.JAVA_ONLY, DiagnosticCode.MARKUP_ONLY)
        })
        assertTrue(report.warnings.any { it.code == DiagnosticCode.UNRESOLVED_MARKUP })
    }
}
