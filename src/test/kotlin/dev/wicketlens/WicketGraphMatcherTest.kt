package dev.wicketlens

import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressManager
import dev.wicketlens.analysis.WicketGraphMatcher
import dev.wicketlens.analysis.WicketMarkupAnalyzer
import dev.wicketlens.model.Confidence
import dev.wicketlens.model.DiagnosticCode
import dev.wicketlens.model.MarkupKind
import dev.wicketlens.model.PageReport
import dev.wicketlens.model.Severity

class WicketGraphMatcherTest : WicketJavaPsiTestCase() {
    fun testSimplePageMatchesWithBothSourceLocations() {
        val report = match("add(new Label(\"title\", \"Hello\"));", "<span wicket:id=\"title\">Hello</span>")
        val node = report.children().single()
        val element = requireNotNull(report.markup).elements.single()
        val finding = report.findings().single()
        assertEquals(DiagnosticCode.MATCHED, finding.code)
        assertEquals(Confidence.CONFIRMED, finding.confidence)
        assertEquals(node.nodeId, finding.javaNodeId)
        assertEquals(element.nodeId, finding.markupNodeId)
        assertEquals(node.javaLocation, finding.javaLocation)
        assertEquals(element.location, finding.markupLocation)
        assertEquals(element.location, node.markupLocation)
        assertNull(report.nodes.single { it.nodeId == report.rootNodeId }.markupLocation)
        assertFalse(requireNotNull(report.matching).scopeComplete)
    }

    fun testNestedFormMatchesThroughOrdinaryHtmlWrappers() {
        val report = match("""
            Form<?> form = new Form<>("form");
            form.add(new TextField<>("name"));
            add(form);
        """.trimIndent(), "<form wicket:id=\"form\"><div><section><input wicket:id=\"name\"/></section></div></form>")
        assertEquals(2, report.findings().size)
        assertTrue(report.findings().all { it.code == DiagnosticCode.MATCHED })
        assertTrue(report.children().all { it.markupLocation != null })
    }

    fun testVariablePageMatchesConstructionToMarkup() {
        val report = match("Label heading = new Label(\"heading\"); add(heading);", "<h1 wicket:id=\"heading\"></h1>")
        assertEquals(DiagnosticCode.MATCHED, report.findings().single().code)
        assertEquals("org.apache.wicket.markup.html.basic.Label", report.children().single().componentType)
    }

    fun testJavaIdMissingFromSupportedMarkupIsInformational() {
        val report = match("add(new Label(\"title\"));", "<html><body><div>No IDs</div></body></html>")
        val finding = report.findings().single()
        assertEquals(DiagnosticCode.JAVA_ONLY, finding.code)
        assertEquals(Confidence.INFERRED, finding.confidence)
        assertNull(finding.markupNodeId)
        assertNull(finding.markupLocation)
        assertNull(report.children().single().markupLocation)
        assertInformational(report)
    }

    fun testMarkupWithoutADiscoveredJavaCounterpartIsInformational() {
        val report = match("", "<span wicket:id=\"extra\"></span>")
        val finding = report.findings().single()
        assertEquals(DiagnosticCode.MARKUP_ONLY, finding.code)
        assertEquals(Confidence.INFERRED, finding.confidence)
        assertNull(finding.javaNodeId)
        assertNull(finding.javaLocation)
        assertNotNull(finding.markupLocation)
        assertFalse(report.scopeComplete)
        assertInformational(report)
    }

    fun testRepeatedIdsUnderDifferentParentsMatchIndependentlyOfSourceOrder() {
        val report = match("""
            Form<?> left = new Form<>("left"), right = new Form<>("right");
            left.add(new Label("name")); right.add(new Label("name")); add(left, right);
        """.trimIndent(), """
            <form wicket:id="right"><span wicket:id="name"></span></form>
            <form wicket:id="left"><span wicket:id="name"></span></form>
        """.trimIndent())
        assertEquals(4, report.findings().size)
        assertTrue(report.findings().all { it.code == DiagnosticCode.MATCHED })
        val javaById = report.nodes.associateBy { it.nodeId }
        val markupById = requireNotNull(report.markup).elements.associateBy { it.nodeId }
        report.findings().filter { javaById[it.javaNodeId]?.wicketId == "name" }.forEach {
            val javaParent = javaById[javaById.getValue(it.javaNodeId!!).parentNodeId]
            val markupParent = markupById[markupById.getValue(it.markupNodeId!!).parentWicketElementId]
            assertEquals(javaParent?.wicketId, markupParent?.wicketId)
        }
        assertEquals(4, report.findings().map { it.markupNodeId }.distinct().size)
    }

    fun testAnIdUnderTheWrongParentDoesNotMatchGlobally() {
        val report = match("""
            Form<?> left = new Form<>("left"), right = new Form<>("right");
            left.add(new Label("name")); add(left, right);
        """.trimIndent(), """
            <form wicket:id="left"></form><form wicket:id="right"><span wicket:id="name"></span></form>
        """.trimIndent())
        assertEquals(2, report.findings().count { it.code == DiagnosticCode.MATCHED })
        assertEquals(1, report.findings().count { it.code == DiagnosticCode.JAVA_ONLY })
        assertEquals(1, report.findings().count { it.code == DiagnosticCode.MARKUP_ONLY })
        assertNull(report.children().single { it.wicketId == "name" }.markupLocation)
        assertInformational(report)
    }

    fun testUnmatchedContainerDescendantsRetainTheirFullPaths() {
        val report = match("Form<?> form = new Form<>(\"form\"); form.add(new Label(\"name\")); add(form);",
            "<div wicket:id=\"other\"><span wicket:id=\"name\"></span></div>")
        assertEquals(2, report.findings().count { it.code == DiagnosticCode.JAVA_ONLY })
        assertEquals(2, report.findings().count { it.code == DiagnosticCode.MARKUP_ONLY })
        assertTrue(report.findings().none { it.code == DiagnosticCode.MATCHED })
    }

    fun testUnrelatedAddDoesNotCreateAJavaMatch() {
        val report = match("new java.util.ArrayList<Object>().add(new Label(\"title\"));", "<span wicket:id=\"title\"></span>")
        assertTrue(report.children().isEmpty())
        assertEquals(DiagnosticCode.MARKUP_ONLY, report.findings().single().code)
    }

    fun testDynamicIdDoesNotProduceDefinitiveUnmatchedFindings() {
        val report = match("add(new Label(dynamicId()));", "<span wicket:id=\"title\"></span>",
            members = "String dynamicId() { return \"title\"; }")
        assertEquals(DiagnosticCode.UNRESOLVED_DYNAMIC_ID, report.findings().first().code)
        assertEquals(DiagnosticCode.UNRESOLVED_PARENT, report.findings().last().code)
        assertUnresolved(report)
    }

    fun testFactoryIdKeepsItsOriginalUncertaintyClassification() {
        val report = match("add(createHeader(\"title\"));", "<span wicket:id=\"title\"></span>",
            members = "Label createHeader(String id) { return new Label(id); }")
        assertEquals(DiagnosticCode.UNRESOLVED_CONSTRUCTOR, report.findings().first().code)
        assertUnresolved(report)
    }

    fun testUnknownJavaParentDoesNotMatchAConvenientMarkupId() {
        val report = match("new WebMarkupContainer(\"form\").add(new Label(\"name\"));",
            "<form wicket:id=\"form\"><span wicket:id=\"name\"></span></form>")
        assertUnresolved(report)
        assertEquals(DiagnosticCode.UNRESOLVED_PARENT, report.findings().first().code)
    }

    fun testMarkupInheritanceDoesNotProduceMatchesOrUnmatchedClaims() {
        val report = match("add(new Label(\"title\"));", "<wicket:extend><span wicket:id=\"title\"></span></wicket:extend>")
        assertUnresolved(report)
        assertTrue(report.findings().all { it.code == DiagnosticCode.UNRESOLVED_MARKUP })
    }

    fun testSupportedBranchCanMatchWhileAnotherMarkupBranchIsUnresolved() {
        val report = match("add(new Label(\"title\"), new Label(\"other\"));", """
            <span wicket:id="title"></span><wicket:enclosure child="other"><span wicket:id="other"></span></wicket:enclosure>
        """.trimIndent())
        assertEquals(1, report.findings().count { it.code == DiagnosticCode.MATCHED })
        assertEquals(2, report.findings().count { it.code == DiagnosticCode.UNRESOLVED_MARKUP })
        assertTrue(report.findings().none { it.code == DiagnosticCode.JAVA_ONLY || it.code == DiagnosticCode.MARKUP_ONLY })
    }

    fun testKnownStaticPairCanMatchAlongsideADynamicJavaComponent() {
        val report = match("add(new Label(\"title\"), new Label(dynamicId()));", "<span wicket:id=\"title\"></span>",
            members = "String dynamicId() { return \"dynamic\"; }")
        assertEquals(1, report.findings().count { it.code == DiagnosticCode.MATCHED })
        assertEquals(1, report.findings().count { it.code == DiagnosticCode.UNRESOLVED_DYNAMIC_ID })
        assertFalse(requireNotNull(report.matching).scopeComplete)
    }

    fun testDuplicateJavaIdsDoNotChooseAnArbitraryInstance() {
        val report = match("add(new Label(\"title\"), new Label(\"title\"));", "<span wicket:id=\"title\"></span>")
        assertEquals(3, report.findings().size)
        assertTrue(report.findings().all { it.code == DiagnosticCode.UNRESOLVED_PARENT })
        assertUnresolved(report)
    }

    fun testDuplicateJavaAncestorBlocksEvenUniqueDescendantIds() {
        val report = match("""
            Form<?> first = new Form<>("same"), second = new Form<>("same");
            first.add(new Label("one")); second.add(new Label("two")); add(first, second);
        """.trimIndent(), "<form wicket:id=\"same\"><span wicket:id=\"one\"></span><b wicket:id=\"two\"></b></form>")
        assertUnresolved(report)
        assertTrue(report.children().all { it.markupLocation == null })
    }

    fun testDuplicateMarkupIdsDoNotChooseAnArbitraryElement() {
        val report = match("add(new Label(\"title\"));", "<span wicket:id=\"title\"></span><b wicket:id=\"title\"></b>")
        assertEquals(3, report.findings().size)
        assertUnresolved(report)
        assertTrue(report.findings().all { it.code == DiagnosticCode.UNRESOLVED_MARKUP })
    }

    fun testDuplicateMarkupAncestorBlocksEvenUniqueDescendantIds() {
        val report = match("Form<?> form = new Form<>(\"same\"); form.add(new Label(\"one\")); add(form);", """
            <form wicket:id="same"><span wicket:id="one"></span></form>
            <form wicket:id="same"><span wicket:id="two"></span></form>
        """.trimIndent())
        assertUnresolved(report)
    }

    fun testMissingMarkupLeavesAllJavaFindingsUnresolved() {
        val report = WicketGraphMatcher().match(analyze("add(new Label(\"title\"));"))
        assertEquals(DiagnosticCode.UNRESOLVED_MARKUP, report.findings().single().code)
        assertUnresolved(report)
    }

    fun testMismatchedMarkupSnapshotCannotBeMatchedToTheSelectedFile() {
        val input = pair("add(new Label(\"title\"));", "<span wicket:id=\"title\"></span>")
        val report = WicketGraphMatcher().match(input.copy(markupPath = "/different/Page.html"))
        assertUnresolved(report)
        assertTrue(report.findings().all { it.code == DiagnosticCode.UNRESOLVED_MARKUP })
    }

    fun testMissingOrCyclicJavaParentsAreRejectedEvenIfConfidenceWasConfirmed() {
        val input = pair("add(new Label(\"title\"));", "<span wicket:id=\"title\"></span>")
        val root = input.nodes.single { it.nodeId == input.rootNodeId }
        val child = input.children().single()
        for (parent in listOf("missing", child.nodeId)) {
            val report = WicketGraphMatcher().match(input.copy(nodes = listOf(root, child.copy(parentNodeId = parent))))
            assertUnresolved(report)
            assertEquals(DiagnosticCode.UNRESOLVED_PARENT, report.findings().first().code)
        }
    }

    fun testCyclicMarkupParentDoesNotProduceAFalseJavaOnlyFinding() {
        val input = pair("add(new Label(\"title\"));", "<span wicket:id=\"title\"></span>")
        val markup = requireNotNull(input.markup)
        val element = markup.elements.single()
        val report = WicketGraphMatcher().match(input.copy(markup = markup.copy(
            elements = listOf(element.copy(parentWicketElementId = element.nodeId)),
        )))
        assertUnresolved(report)
    }

    fun testPanelPreviewIdsAreNotMatchedOrReportedAsMarkupOnly() {
        val report = match("add(new Label(\"title\"));", """
            <html><body><span wicket:id="title"></span><span wicket:id="preview"></span>
              <wicket:panel><span wicket:id="title"></span></wicket:panel>
            </body></html>
        """.trimIndent(), kind = MarkupKind.PANEL)
        assertEquals(DiagnosticCode.MATCHED, report.findings().single().code)
    }

    fun testLiteralIdsAreCaseSensitiveAndEntitiesAreDecodedBeforeMatching() {
        val report = match("add(new Label(\"a&b\"), new Label(\"Title\"));", "<span wicket:id=\"a&amp;b\"></span><b wicket:id=\"title\"></b>")
        assertEquals(1, report.findings().count { it.code == DiagnosticCode.MATCHED })
        assertEquals(1, report.findings().count { it.code == DiagnosticCode.JAVA_ONLY })
        assertEquals(1, report.findings().count { it.code == DiagnosticCode.MARKUP_ONLY })
    }

    fun testUnmatchedFindingsRemainInformationalEvenForExplicitlyCompleteSnapshots() {
        val input = pair("add(new Label(\"java\"));", "<span wicket:id=\"markup\"></span>").copy(scopeComplete = true)
        val report = WicketGraphMatcher().match(input)
        assertTrue(requireNotNull(report.matching).scopeComplete)
        assertInformational(report)
    }

    fun testMatchingIsDeterministicIdempotentAndPreservesDiscoveryDiagnostics() {
        val input = pair("add(new Label(\"title\"));", "<span wicket:id=\"title\"></span>")
        val matcher = WicketGraphMatcher()
        val first = matcher.match(input)
        assertEquals(first, matcher.match(input))
        assertEquals(first, matcher.match(first))
        assertNull(input.children().single().markupLocation)
        assertNull(input.matching)
        assertEquals(input.children().single().diagnostics, first.children().single().diagnostics)
        assertEquals(input.warnings, first.warnings)
        assertEquals(input.markup, first.markup)
    }

    fun testRematchingClearsStaleSourceLinksAndFindings() {
        val input = pair("add(new Label(\"title\"));", "<span wicket:id=\"title\"></span>")
        val matcher = WicketGraphMatcher()
        val matched = matcher.match(input)
        val markup = WicketMarkupAnalyzer().analyze(myFixture.configureByText("ExamplePage.html", "<div>No IDs</div>"))
        val result = matcher.match(matched.copy(markup = markup, markupPath = markup.file))
        assertNull(result.children().single().markupLocation)
        assertEquals(DiagnosticCode.JAVA_ONLY, result.findings().single().code)
    }

    fun testEmptyGraphsDoNotMatchTheSyntheticPageRoot() {
        val report = match("", "<html><body></body></html>")
        assertTrue(report.findings().isEmpty())
        assertNull(report.nodes.single().markupLocation)
    }

    fun testMatchingHonorsCancellation() {
        val input = pair("add(new Label(\"title\"));", "<span wicket:id=\"title\"></span>")
        val indicator = EmptyProgressIndicator()
        assertThrows(ProcessCanceledException::class.java) {
            ProgressManager.getInstance().runProcess(Runnable {
                indicator.cancel()
                WicketGraphMatcher().match(input)
            }, indicator)
        }
    }

    private fun match(body: String, html: String, members: String = "", kind: MarkupKind = MarkupKind.PAGE) =
        WicketGraphMatcher().match(pair(body, html, members, kind))

    private fun pair(body: String, html: String, members: String = "", kind: MarkupKind = MarkupKind.PAGE): PageReport {
        val java = analyze(body, base = if (kind == MarkupKind.PANEL) "Panel" else "WebPage",
            constructorPrefix = if (kind == MarkupKind.PANEL) "super(\"root\");" else "", members = members)
        val markup = WicketMarkupAnalyzer().analyze(myFixture.configureByText("ExamplePage.html", html), kind)
        return java.copy(markupPath = markup.file, markup = markup)
    }

    private fun PageReport.findings() = requireNotNull(matching).findings

    private fun assertInformational(report: PageReport) {
        assertTrue(report.findings().all { it.severity == Severity.INFO })
    }

    private fun assertUnresolved(report: PageReport) {
        assertTrue(report.findings().all { it.confidence == Confidence.UNRESOLVED })
        assertTrue(report.findings().none { it.code in setOf(DiagnosticCode.MATCHED, DiagnosticCode.JAVA_ONLY, DiagnosticCode.MARKUP_ONLY) })
        assertTrue(report.children().all { it.markupLocation == null })
        assertInformational(report)
    }
}
