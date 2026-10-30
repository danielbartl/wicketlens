package dev.wicketlens

import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressManager
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import dev.wicketlens.analysis.WicketMarkupAnalyzer
import dev.wicketlens.model.Confidence
import dev.wicketlens.model.DiagnosticCode
import dev.wicketlens.model.MarkupKind
import dev.wicketlens.model.MarkupReport
import dev.wicketlens.model.Severity

class WicketMarkupAnalyzerTest : LightJavaCodeInsightFixtureTestCase() {
    override fun getProjectDescriptor(): LightProjectDescriptor = Java25ProjectDescriptor

    fun testSimpleIdRetainsItsOwningElementAndSourceLocations() {
        val text = "<html><body><span wicket:id=\"title\">Hello</span></body></html>"
        val report = analyze(text)
        val title = report.component("title")
        assertEquals(listOf("html", "body", "span"), report.elements.map { it.tagName })
        assertEquals("span", title.tagName)
        assertEquals(myFixture.file.virtualFile.path, title.location.file)
        assertEquals(text.indexOf("<span"), title.location.offset)
        assertEquals(text.indexOf("wicket:id"), title.wicketIdLocation?.offset)
        assertEquals(report.elements.single { it.tagName == "body" }.nodeId, title.parentElementId)
        assertNull(title.parentWicketElementId)
        assertEquals(Confidence.CONFIRMED, title.confidence)
        assertTrue(report.scopeComplete)
        assertTrue(report.diagnostics.isEmpty())
    }

    fun testOrdinaryWrappersArePreservedWithoutBecomingWicketParents() {
        val report = analyze("""
            <html><body><form wicket:id="form"><div><section>
              <input wicket:id="name"/>
            </section></div></form></body></html>
        """.trimIndent())
        val name = report.component("name")
        assertEquals(report.component("form").nodeId, name.parentWicketElementId)
        val section = report.elements.single { it.tagName == "section" }
        val div = report.elements.single { it.tagName == "div" }
        assertEquals(section.nodeId, name.parentElementId)
        assertEquals(div.nodeId, section.parentElementId)
        assertEquals(report.component("form").nodeId, div.parentElementId)
        assertTrue(report.scopeComplete)
    }

    fun testRepeatedIdsUnderDifferentParentsAreAllowed() {
        val report = analyze("""
            <div wicket:id="left"><span wicket:id="name"></span></div>
            <div wicket:id="right"><span wicket:id="name"></span></div>
        """.trimIndent())
        val names = report.components().filter { it.wicketId == "name" }
        assertEquals(2, names.size)
        assertEquals(setOf(report.component("left").nodeId, report.component("right").nodeId),
            names.map { it.parentWicketElementId }.toSet())
        assertTrue(names.all { it.confidence == Confidence.CONFIRMED })
        assertTrue(report.scopeComplete)
    }

    fun testRepeatedIdsUnderOneWicketParentAreUnresolvedThroughWrappers() {
        val report = analyze("""
            <div><span wicket:id="same"><b wicket:id="first"></b></span></div>
            <section><span wicket:id="same"><b wicket:id="second"></b></span></section>
        """.trimIndent())
        assertEquals(4, report.components().size)
        assertTrue(report.components().all { it.confidence == Confidence.UNRESOLVED })
        assertTrue(report.components().filter { it.wicketId == "same" }
            .all { it.diagnostics.any { d -> d.code == DiagnosticCode.UNRESOLVED_MARKUP } })
        assertFalse(report.scopeComplete)
        assertInformational(report)
    }

    fun testPlainIdsAndForeignNamespaceAttributesAreNotWicketIds() {
        val report = analyze("""
            <html xmlns:x="urn:other"><body>
              <div id="html" data-wicket-id="data" x:id="foreign"></div>
            </body></html>
        """.trimIndent())
        assertTrue(report.components().isEmpty())
        assertEquals(3, report.elements.size)
        assertTrue(report.scopeComplete)
    }

    fun testCommentsScriptsAndStylesDoNotCreateComponentElements() {
        val report = analyze("""
            <html><body>
              <!-- <span wicket:id="comment"></span><wicket:extend/> -->
              <script>const fake = '<span wicket:id="script"></span>';</script>
              <style>.example::before { content: '<span wicket:id="style">'; }</style>
              <span wicket:id="actual"></span>
            </body></html>
        """.trimIndent())
        assertEquals(listOf("actual"), report.components().map { it.wicketId })
        assertTrue(report.scopeComplete)
    }

    fun testXmlPsiIsSupportedAsWellAsHtmlPsi() {
        val report = analyze("<root xmlns:wicket=\"http://wicket.apache.org\"><item wicket:id=\"title\"/></root>", name = "Page.xml")
        assertEquals("item", report.component("title").tagName)
        assertTrue(report.scopeComplete)
    }

    fun testEntitiesAreDecodedWithoutNormalizingLiteralIds() {
        val report = analyze("""
            <div wicket:id="a&amp;b"></div><span wicket:id="&#x6e;ame"></span>
            <b wicket:id="  MiXeD  "></b>
        """.trimIndent())
        assertEquals(listOf("a&b", "name", "  MiXeD  "), report.components().map { it.wicketId })
        assertTrue(report.scopeComplete)
    }

    fun testQuotesAndUnquotedHtmlValuesAreReadThroughPsi() {
        val report = analyze("<div wicket:id='single'></div><span wicket:id=unquoted></span><b wicket:id=\"double\"></b>")
        assertEquals(listOf("single", "unquoted", "double"), report.components().map { it.wicketId })
        assertTrue(report.scopeComplete)
    }

    fun testCustomEntitiesAreUnresolved() {
        val report = analyze("""
            <!DOCTYPE root [<!ENTITY custom "title">]>
            <root xmlns:wicket="http://wicket.apache.org"><item wicket:id="&custom;"/></root>
        """.trimIndent(), name = "Page.xml")
        assertEquals(Confidence.UNRESOLVED, report.components().single().confidence)
        assertFalse(report.scopeComplete)
        assertTrue(report.diagnostics.any { it.message.contains("entities") })
    }

    fun testEmptyAndValuelessIdAttributesRetainAnUnresolvedBoundary() {
        for (attribute in listOf("wicket:id=\"\"", "wicket:id")) {
            val report = analyze("<div $attribute><span wicket:id=\"child\"></span></div>")
            val owner = report.elements.single { it.tagName == "div" }
            assertTrue(owner.hasWicketId)
            assertNull(owner.wicketId)
            assertEquals(owner.nodeId, report.component("child").parentWicketElementId)
            assertEquals(Confidence.UNRESOLVED, report.component("child").confidence)
            assertFalse(report.scopeComplete)
        }
    }

    fun testDuplicateIdAttributesDoNotChooseAnArbitraryValue() {
        val report = analyze("<div wicket:id=\"one\" wicket:id=\"two\"><span wicket:id=\"child\"></span></div>")
        val div = report.elements.single { it.tagName == "div" }
        assertTrue(div.hasWicketId)
        assertNull(div.wicketId)
        assertEquals(div.nodeId, report.component("child").parentWicketElementId)
        assertEquals(Confidence.UNRESOLVED, report.component("child").confidence)
        assertFalse(report.scopeComplete)
    }

    fun testReservedPathCharactersAreRetainedButNotConfirmed() {
        val report = analyze("<div wicket:id=\"a:b\"></div><span wicket:id=\"a~b\"></span>")
        assertEquals(listOf("a:b", "a~b"), report.components().map { it.wicketId })
        assertTrue(report.components().all { it.confidence == Confidence.UNRESOLVED })
        assertFalse(report.scopeComplete)
        assertInformational(report)
    }

    fun testInheritanceLeavesTheWholeDocumentContextUnresolved() {
        val report = analyze("""
            <html><body><span wicket:id="preview"></span>
              <wicket:extend><span wicket:id="inherited"></span></wicket:extend>
            </body></html>
        """.trimIndent())
        assertEquals(2, report.components().size)
        assertTrue(report.components().all { it.confidence == Confidence.UNRESOLVED })
        assertFalse(report.scopeComplete)
        assertTrue(report.diagnostics.any { it.message.contains("inheritance") })
    }

    fun testUnsupportedWicketTagsPropagateThroughOrdinaryWrappersOnlyInTheirBranch() {
        val report = analyze("""
            <html><body><wicket:enclosure child="inside"><div>
              <span wicket:id="inside"></span>
            </div></wicket:enclosure><span wicket:id="outside"></span></body></html>
        """.trimIndent())
        assertEquals(Confidence.UNRESOLVED, report.component("inside").confidence)
        assertEquals(Confidence.CONFIRMED, report.component("outside").confidence)
        assertFalse(report.scopeComplete)
    }

    fun testFragmentAndRemoveTagsAreObservedWithoutClaimingRuntimeComponents() {
        val report = analyze("""
            <wicket:fragment wicket:id="fragment"><span wicket:id="nested"></span></wicket:fragment>
            <wicket:remove><b wicket:id="preview"></b></wicket:remove>
        """.trimIndent())
        assertEquals(report.component("fragment").nodeId, report.component("nested").parentWicketElementId)
        assertTrue(report.components().all { it.confidence == Confidence.UNRESOLVED })
        assertFalse(report.scopeComplete)
    }

    fun testWicketAttributesThatCreateAutoComponentsAreUnresolved() {
        val report = analyze("""
            <div wicket:enclosure="child"><span wicket:id="child"></span></div>
            <label wicket:for="input">Name</label><input wicket:id="input"/>
        """.trimIndent())
        assertEquals(Confidence.UNRESOLVED, report.component("child").confidence)
        assertEquals(Confidence.CONFIRMED, report.component("input").confidence)
        assertFalse(report.scopeComplete)
    }

    fun testOrdinaryPageHeaderComponentsShareTheLogicalRoot() {
        val report = analyze("""
            <html><head><title wicket:id="header"></title></head>
            <body><span wicket:id="body"></span></body></html>
        """.trimIndent())
        assertEquals(Confidence.CONFIRMED, report.component("header").confidence)
        assertEquals(Confidence.CONFIRMED, report.component("body").confidence)
        assertTrue(report.scopeComplete)
    }

    fun testCustomNamespacePrefixesDoNotCreateConfirmedMatches() {
        val report = analyze("""
            <html xmlns:w="http://wicket.apache.org"><body>
              <span w:id="custom"></span><span wicket:id="default"></span>
            </body></html>
        """.trimIndent())
        assertEquals(listOf("custom", "default"), report.components().map { it.wicketId })
        assertTrue(report.components().all { it.confidence == Confidence.UNRESOLVED })
        assertFalse(report.scopeComplete)
    }

    fun testNonstandardAttributeCaseIsNotConfirmedInXml() {
        val report = analyze("<root><item WICKET:ID=\"title\"/></root>", name = "Page.xml")
        assertEquals(Confidence.UNRESOLVED, report.component("title").confidence)
        assertFalse(report.scopeComplete)
    }

    fun testStandalonePanelWrapperIsTransparentToTheComponentHierarchy() {
        val report = analyze("<wicket:panel><div><span wicket:id=\"title\"></span></div></wicket:panel>", MarkupKind.PANEL)
        assertEquals(MarkupKind.PANEL, report.kind)
        assertNull(report.component("title").parentWicketElementId)
        assertEquals(3, report.elements.size)
        assertTrue(report.scopeComplete)
        assertEquals(Confidence.CONFIRMED, report.component("title").confidence)
    }

    fun testPanelPreviewMarkupIsPreservedButExcludedFromComponentScope() {
        val report = analyze("""
            <html><body><div wicket:id="preview"><span wicket:id="title"></span>
              <wicket:panel><section><span wicket:id="title"></span></section></wicket:panel>
            </div></body></html>
        """.trimIndent(), MarkupKind.PANEL)
        assertEquals(listOf("title"), report.components().map { it.wicketId })
        assertEquals(3, report.elements.count { it.hasWicketId })
        assertNull(report.component("title").parentWicketElementId)
        assertFalse(report.elements.single { it.wicketId == "preview" }.participatesInComponentTree)
        assertTrue(report.scopeComplete)
    }

    fun testMissingOrMultiplePanelSectionsDoNotGuessAnActiveScope() {
        for (text in listOf(
            "<html><body><span wicket:id=\"title\"></span></body></html>",
            "<wicket:panel><span wicket:id=\"one\"></span></wicket:panel><wicket:panel><b wicket:id=\"two\"></b></wicket:panel>",
        )) {
            val report = analyze(text, MarkupKind.PANEL)
            assertFalse(report.scopeComplete)
            assertTrue(report.components().isEmpty())
            assertTrue(report.diagnostics.any { it.message.contains("exactly one") })
        }
    }

    fun testPanelTagInPageContextRemainsUnresolved() {
        val report = analyze("<wicket:panel><span wicket:id=\"title\"></span></wicket:panel>")
        assertEquals(Confidence.UNRESOLVED, report.component("title").confidence)
        assertFalse(report.scopeComplete)
    }

    fun testPsiSyntaxErrorsPreventConfirmedHierarchyClaims() {
        val report = analyze("<root><span wicket:id=\"title\"></root>", name = "Page.xml")
        assertFalse(report.scopeComplete)
        assertTrue(report.diagnostics.any { it.message.contains("syntax") })
        assertTrue(report.components().all { it.confidence == Confidence.UNRESOLVED })
        assertInformational(report)
    }

    fun testNonXmlAndEmptyFilesRemainUnresolved() {
        assertFalse(analyze("plain text", name = "Page.txt").scopeComplete)
        assertFalse(analyze("<!-- no tags -->").scopeComplete)
    }

    fun testExtractionIsDeterministicReadOnlyAndUsesOnlyReportedParentIds() {
        val report = analyze("<form wicket:id=\"form\"><div><input wicket:id=\"name\"/></div></form>")
        val text = myFixture.file.text
        assertEquals(report, WicketMarkupAnalyzer().analyze(myFixture.file))
        assertEquals(text, myFixture.file.text)
        val ids = report.elements.map { it.nodeId }.toSet()
        assertEquals(report.elements.size, ids.size)
        assertTrue(report.elements.all { it.parentElementId == null || it.parentElementId in ids })
        assertTrue(report.elements.all { it.parentWicketElementId == null || it.parentWicketElementId in ids })
        assertTrue(report.elements.map { it.location.offset }.zipWithNext().all { (a, b) -> a < b })
    }

    fun testExtractionHonorsCancellation() {
        analyze("<span wicket:id=\"title\"></span>")
        val indicator = EmptyProgressIndicator()
        assertThrows(ProcessCanceledException::class.java) {
            ProgressManager.getInstance().runProcess(Runnable {
                indicator.cancel()
                WicketMarkupAnalyzer().analyze(myFixture.file)
            }, indicator)
        }
    }

    private fun analyze(text: String, kind: MarkupKind = MarkupKind.PAGE, name: String = "ExamplePage.html"): MarkupReport =
        WicketMarkupAnalyzer().analyze(myFixture.configureByText(name, text), kind)

    private fun MarkupReport.components() = elements.filter { it.hasWicketId && it.participatesInComponentTree }

    private fun MarkupReport.component(id: String) = components().single { it.wicketId == id }

    private fun assertInformational(report: MarkupReport) {
        assertTrue(report.diagnostics.all { it.severity == Severity.INFO })
        assertTrue(report.diagnostics.none { it.code in setOf(DiagnosticCode.MATCHED, DiagnosticCode.JAVA_ONLY, DiagnosticCode.MARKUP_ONLY) })
    }
}
