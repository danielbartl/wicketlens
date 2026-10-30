package dev.wicketlens

import com.intellij.psi.PsiJavaFile
import dev.wicketlens.analysis.WicketGraphMatcher
import dev.wicketlens.analysis.WicketJavaAnalyzer
import dev.wicketlens.analysis.WicketMarkupAnalyzer
import dev.wicketlens.model.Confidence
import dev.wicketlens.model.DiagnosticCode
import dev.wicketlens.model.PageReport
import dev.wicketlens.model.Severity

class WicketUncertaintyTest : WicketJavaPsiTestCase() {
    fun testEmptyAndReservedLiteralIdsAreNotMisclassifiedAsDynamic() {
        val report = match("add(new Label(\"\"), new Label(\"a:b\"), new Label(\"a~b\"));", "<div>No IDs</div>")
        assertEquals(listOf("", "a:b", "a~b"), report.children().map { it.wicketId })
        assertTrue(report.children().all { it.confidence == Confidence.UNRESOLVED })
        assertTrue(report.findings().all { it.code == DiagnosticCode.UNRESOLVED_ID })
        assertTrue(report.findings().all { it.causes.any { cause -> cause.code == DiagnosticCode.UNRESOLVED_ID } })
        assertNoUnmatchedClaims(report)
    }

    fun testControlFlowMakesEvenTopLevelAdditionsUnresolved() {
        for (prefix in listOf("if (enabled()) return;", "for (int i=0; i<1; i++) {}", "try {} finally {}")) {
            val report = match("$prefix add(new Label(\"title\"));", "<span wicket:id=\"title\"></span>",
                members = "boolean enabled() { return true; }")
            assertEquals("title", report.children().single().wicketId)
            assertEquals(Confidence.UNRESOLVED, report.children().single().confidence)
            assertEquals(DiagnosticCode.UNRESOLVED_SCOPE, report.findings().first().code)
            assertTrue(report.findings().first().causes.any { it.code == DiagnosticCode.UNRESOLVED_SCOPE })
            assertNoPositiveOrUnmatchedClaims(report)
        }
    }

    fun testRootRemovalAndReplacementDoNotLeaveConfirmedMatches() {
        for (mutation in listOf("removeAll();", "remove(\"title\");", "addOrReplace(new Label(\"title\"));", "replace(new Label(\"title\"));")) {
            val report = match("add(new Label(\"title\")); $mutation", "<span wicket:id=\"title\"></span>")
            assertEquals(DiagnosticCode.UNRESOLVED_SCOPE, report.findings().first().code)
            assertNoPositiveOrUnmatchedClaims(report)
        }
    }

    fun testParenthesizedAndCastRootMutationsCannotBypassScopeGuards() {
        for (receiver in listOf("(this)", "((org.apache.wicket.MarkupContainer) this)")) {
            val report = match("add(new Label(\"title\")); $receiver.removeAll();", "<span wicket:id=\"title\"></span>")
            assertNoPositiveOrUnmatchedClaims(report)
        }
    }

    fun testRecoveredJavaSyntaxDoesNotLeaveConfirmedMatches() {
        val report = match("add(new Label(\"title\")); int broken = ;", "<span wicket:id=\"title\"></span>")
        assertTrue(report.warnings.any { it.message.contains("Java syntax") })
        assertNoPositiveOrUnmatchedClaims(report)
        assertTrue(report.findings().first().causes.any { it.message.contains("Java syntax") })
    }

    fun testQueueWithoutPlacementPassRetainsAnExplicitGapInsteadOfMarkupOnly() {
        val report = match("queue(new Label(\"title\"));", "<span wicket:id=\"title\"></span>")
        val child = report.children().single()
        assertEquals(report.rootNodeId, child.queuedInNodeId)
        assertNull(child.parentNodeId)
        val finding = report.findings().single { it.javaNodeId == child.nodeId }
        assertEquals(DiagnosticCode.UNRESOLVED_QUEUE, finding.code)
        assertTrue(finding.causes.any { it.code == DiagnosticCode.UNRESOLVED_QUEUE && it.location != null })
        assertNoUnmatchedClaims(report)
    }

    fun testCustomAddOverrideRecordsItsUnexpandedScope() {
        val report = match("add(new Label(\"title\"));", "<span wicket:id=\"title\"></span>", members = """
            public org.apache.wicket.MarkupContainer add(org.apache.wicket.Component... children) { return this; }
        """.trimIndent())
        assertTrue(report.children().isEmpty())
        assertTrue(report.findings().single().causes.any { it.code == DiagnosticCode.UNRESOLVED_SCOPE })
        assertNoUnmatchedClaims(report)
    }

    fun testConditionalInheritedSetupDoesNotLookLikeAbsentJava() {
        myFixture.addClass("""
            package example;
            public class BasePage extends org.apache.wicket.markup.html.WebPage {
                public BasePage() { if (System.nanoTime() > 0) add(new org.apache.wicket.markup.html.basic.Label("inherited")); }
            }
        """.trimIndent())
        val java = analyze("", base = "BasePage")
        val report = matchJava(java, "<span wicket:id=\"inherited\"></span>")
        assertTrue(java.children().isEmpty())
        assertTrue(report.findings().single().causes.any { it.message.contains("inherited") })
        assertNoUnmatchedClaims(report)
    }

    fun testInheritedScopeGapAlsoPreventsJavaOnlyClaims() {
        myFixture.addClass("package example; public class BasePage extends org.apache.wicket.markup.html.WebPage { public BasePage() { setup(); } protected void setup() {} }")
        val report = matchJava(analyze("add(new Label(\"title\"));", base = "BasePage"), "<div>No IDs</div>")
        assertEquals(DiagnosticCode.UNRESOLVED_SCOPE, report.findings().single().code)
        assertTrue(report.findings().single().causes.any { it.message.contains("inherited") })
        assertNoUnmatchedClaims(report)
    }

    fun testOpaqueRootHelpersAndRootEscapesAreVisibleInFindings() {
        for (body in listOf("configure();", "new Configurer().configure(this);")) {
            val report = match(body, "<span wicket:id=\"unknown\"></span>", members = """
                void configure() {}
                static class Configurer { void configure(ExamplePage page) {} }
            """.trimIndent())
            assertEquals(DiagnosticCode.UNRESOLVED_PARENT, report.findings().single().code)
            assertTrue(report.findings().single().causes.any { it.code == DiagnosticCode.UNRESOLVED_SCOPE })
            assertNoUnmatchedClaims(report)
        }
    }

    fun testUnresolvedRootCallIsAReportedGap() {
        val report = match("unknownSetup();", "<span wicket:id=\"unknown\"></span>")
        assertTrue(report.findings().single().causes.any { it.message.contains("unknownSetup") })
        assertNoUnmatchedClaims(report)
    }

    fun testUnexpandedRootHelperEffectsInvalidateEarlierDirectBindings() {
        val report = match("add(new Label(\"title\")); setup();", "<span wicket:id=\"title\"></span>",
            members = "private void setup() { removeAll(); }")
        assertNoPositiveOrUnmatchedClaims(report)
        assertTrue(report.findings().any { finding -> finding.causes.any { it.code == DiagnosticCode.UNRESOLVED_SCOPE } })
    }

    fun testAnonymousComponentChildrenAreNotAssumedAbsent() {
        val report = match("""
            add(new Form<Void>("form") {{ add(new Label("inner")); }});
        """.trimIndent(), "<form wicket:id=\"form\"><span wicket:id=\"inner\"></span></form>")
        assertEquals(1, report.children().size)
        val unpaired = report.findings().filter { it.code != DiagnosticCode.MATCHED }
        assertEquals(1, unpaired.size)
        assertEquals(DiagnosticCode.UNRESOLVED_PARENT, unpaired.single().code)
        assertTrue(unpaired.single().causes.any { it.message.contains("Anonymous") })
        assertNoUnmatchedClaims(report)
    }

    fun testNonComponentAnonymousInitializerRootMutationInvalidatesAttachments() {
        for (mutation in listOf("ExamplePage.this.removeAll();", "removeAll();",
            "((org.apache.wicket.MarkupContainer) ExamplePage.this).removeAll();")) {
            val report = match("""
                add(new Label("title"));
                new Object() {{ $mutation }};
            """.trimIndent(), "<span wicket:id=\"title\"></span>")
            assertEquals(1, report.children().size)
            assertEquals(Confidence.UNRESOLVED, report.children().single().confidence)
            assertTrue(report.findings().all { it.causes.any { cause ->
                cause.code == DiagnosticCode.UNRESOLVED_SCOPE && cause.location != null
            } })
            assertNoPositiveOrUnmatchedClaims(report)
        }
    }

    fun testAnonymousInitializerRootAdditionDoesNotInventNestedComponents() {
        val report = match("""
            new Object() {{ ExamplePage.this.add(new Label("hidden")); }};
        """.trimIndent(), "<span wicket:id=\"hidden\"></span>")
        assertTrue(report.children().isEmpty())
        assertEquals(DiagnosticCode.UNRESOLVED_PARENT, report.findings().single().code)
        assertTrue(report.findings().single().causes.any { it.code == DiagnosticCode.UNRESOLVED_SCOPE })
        assertNoPositiveOrUnmatchedClaims(report)
    }

    fun testAnonymousRootEscapeAndImplicitHelperAffectEnclosingAttachments() {
        for (effect in listOf("configure();", "new Configurer().configure(ExamplePage.this);")) {
            val report = match("""
                add(new Label("title"));
                new Object() {{ $effect }};
            """.trimIndent(), "<span wicket:id=\"title\"></span>", members = """
                void configure() { removeAll(); }
                static class Configurer { void configure(ExamplePage page) { page.removeAll(); } }
            """.trimIndent())
            assertTrue(report.warnings.any { it.code == DiagnosticCode.UNRESOLVED_SCOPE && it.location != null })
            assertNoPositiveOrUnmatchedClaims(report)
        }
    }

    fun testUnrelatedAnonymousReceiverDoesNotInvalidatePageMatches() {
        val report = match("""
            add(new Label("title"));
            new Object() {
                { this.removeAll(); removeAll(); }
                void removeAll() {}
            };
        """.trimIndent(), "<span wicket:id=\"title\"></span>")
        assertEquals(DiagnosticCode.MATCHED, report.findings().single().code)
        assertTrue(report.warnings.none { it.code == DiagnosticCode.UNRESOLVED_SCOPE })
    }

    fun testRootBoundAddMethodReferencesAreScopeEscapes() {
        for (receiver in listOf("this", "ExamplePage.this", "super", "(this)")) {
            val report = match("""
                java.util.function.Consumer<org.apache.wicket.Component> attach = $receiver::add;
                attach.accept(new Label("title"));
            """.trimIndent(), "<span wicket:id=\"title\"></span>")
            assertTrue(report.children().isEmpty())
            assertEquals(DiagnosticCode.UNRESOLVED_PARENT, report.findings().single().code)
            assertTrue(report.findings().single().causes.any { it.code == DiagnosticCode.UNRESOLVED_SCOPE && it.location != null })
            assertNoPositiveOrUnmatchedClaims(report)
        }
    }

    fun testRootBoundMutationMethodReferencesInvalidateExistingMatches() {
        for (receiver in listOf("this", "super", "ExamplePage.this")) {
            val report = match("""
                add(new Label("title"));
                Runnable clear = $receiver::removeAll;
                clear.run();
            """.trimIndent(), "<span wicket:id=\"title\"></span>")
            assertEquals(DiagnosticCode.UNRESOLVED_SCOPE, report.findings().first().code)
            assertNoPositiveOrUnmatchedClaims(report)
        }
    }

    fun testRootMethodReferenceInsideAnonymousBodyIsNotPrunedSilently() {
        val report = match("""
            new Object() {{
                java.util.function.Consumer<org.apache.wicket.Component> attach = ExamplePage.this::add;
                attach.accept(new Label("title"));
            }};
        """.trimIndent(), "<span wicket:id=\"title\"></span>")
        assertTrue(report.children().isEmpty())
        assertTrue(report.findings().single().causes.any { it.code == DiagnosticCode.UNRESOLVED_SCOPE })
        assertNoPositiveOrUnmatchedClaims(report)
    }

    fun testOtherReceiversMethodReferencesDoNotInvalidatePageMatches() {
        val report = match("""
            add(new Label("title"));
            java.util.List<String> values = new java.util.ArrayList<>();
            java.util.function.Consumer<String> append = values::add;
            append.accept("value");
            new Object() {{ Runnable clear = super::toString; }};
        """.trimIndent(), "<span wicket:id=\"title\"></span>")
        assertEquals(DiagnosticCode.MATCHED, report.findings().single().code)
        assertTrue(report.warnings.none { it.code == DiagnosticCode.UNRESOLVED_SCOPE })
    }

    fun testAlternativeAndDelegatingConstructorsExposeScopeLimitations() {
        val report = match("this(1);", "<span wicket:id=\"unknown\"></span>",
            members = "public ExamplePage(int mode) {}")
        val causes = report.findings().single().causes
        assertTrue(causes.any { it.message.contains("Alternative") })
        assertTrue(causes.any { it.message.contains("delegation") })
        assertNoUnmatchedClaims(report)
    }

    fun testUnknownChildIdOnlyBlocksItsKnownContainerScope() {
        val report = match("""
            Form<?> form = new Form<>("form");
            form.add(new Label(configuredId));
            add(form);
        """.trimIndent(), """
            <form wicket:id="form"><span wicket:id="maybe"></span></form>
            <span wicket:id="outside"></span>
        """.trimIndent(), members = "String configuredId;")
        val markup = requireNotNull(report.markup).elements.associateBy { it.nodeId }
        val outside = report.findings().single { markup[it.markupNodeId]?.wicketId == "outside" }
        val maybe = report.findings().single { markup[it.markupNodeId]?.wicketId == "maybe" }
        assertEquals(DiagnosticCode.MARKUP_ONLY, outside.code)
        assertEquals(DiagnosticCode.UNRESOLVED_PARENT, maybe.code)
        assertTrue(maybe.causes.any { it.code == DiagnosticCode.UNRESOLVED_DYNAMIC_ID })
        assertEquals(1, report.findings().count { it.code == DiagnosticCode.MATCHED })
    }

    fun testUnknownRootIdStillBlocksEveryPossibleRootScope() {
        val report = match("add(new Label(configuredId));", "<span wicket:id=\"one\"></span><span wicket:id=\"two\"></span>",
            members = "String configuredId;")
        assertNoPositiveOrUnmatchedClaims(report)
        assertTrue(report.findings().filter { it.markupNodeId != null }
            .all { it.causes.any { cause -> cause.code == DiagnosticCode.UNRESOLVED_DYNAMIC_ID } })
    }

    fun testAncestorIdUncertaintyIsIncludedInChildFindingCauses() {
        val report = matchJava(analyze("""
            Form<?> form = new Form<>(configuredId);
            form.add(new Label("child")); add(form);
        """.trimIndent(), members = "String configuredId;"), "<div>No IDs</div>")
        val child = report.children().single { it.wicketId == "child" }
        val finding = report.findings().single { it.javaNodeId == child.nodeId }
        assertEquals(DiagnosticCode.UNRESOLVED_PARENT, finding.code)
        assertTrue(finding.causes.any { it.code == DiagnosticCode.UNRESOLVED_DYNAMIC_ID })
        assertTrue(finding.causes.all { it.location?.file == child.javaLocation.file })
    }

    fun testDynamicIdKeepsItsPrimaryReasonWhenMarkupIsMissing() {
        val java = analyze("add(new Label(configuredId));", members = "String configuredId;")
        val report = WicketGraphMatcher().match(java)
        assertEquals(DiagnosticCode.UNRESOLVED_DYNAMIC_ID, report.findings().single().code)
        assertTrue(report.findings().single().causes.any { it.code == DiagnosticCode.UNRESOLVED_DYNAMIC_ID })
    }

    fun testRecoveredUnclosedHtmlDoesNotProduceConfirmedMatches() {
        for (html in listOf("<span wicket:id=\"title\">", "<div><span wicket:id=\"title\"></span>", "<span wicket:id=\"title\"")) {
            val report = match("add(new Label(\"title\"));", html)
            assertFalse(requireNotNull(report.markup).scopeComplete)
            assertTrue(report.markup.documentDiagnostics.isNotEmpty())
            assertNoPositiveOrUnmatchedClaims(report)
            assertTrue(report.findings().any { it.causes.any { cause -> cause.code == DiagnosticCode.UNRESOLVED_MARKUP } })
        }
    }

    fun testHtmlVoidAndOptionalEndTagsRemainSupported() {
        val report = match("add(new Label(\"title\"));", "<html><body><p wicket:id=\"title\">Hello<br><img src=\"x\">")
        assertTrue(requireNotNull(report.markup).scopeComplete)
        assertEquals(DiagnosticCode.MATCHED, report.findings().single().code)
    }

    fun testMalformedMarkupFindingsRetainTheActualDocumentReason() {
        val report = match("add(new Label(\"title\"));", "<wicket:extend><span wicket:id=\"title\"></span></wicket:extend>")
        assertTrue(report.findings().all { it.causes.any { cause -> cause.message.contains("inheritance") } })
        assertNoPositiveOrUnmatchedClaims(report)
    }

    fun testQualifiedOuterThisIsNotMistakenForTheAnalyzedInnerPage() {
        val file = myFixture.configureByText("Outer.java", """
            package example;
            public class Outer extends org.apache.wicket.markup.html.WebPage {
                public class Inner extends org.apache.wicket.markup.html.WebPage {
                    public Inner() { Outer.this.add(new org.apache.wicket.markup.html.basic.Label("title")); }
                }
            }
        """.trimIndent()) as PsiJavaFile
        val java = requireNotNull(WicketJavaAnalyzer().analyze(file.classes.single().innerClasses.single()))
        assertNull(java.children().single().parentNodeId)
        assertEquals(Confidence.UNRESOLVED, java.children().single().confidence)
        assertNoPositiveOrUnmatchedClaims(matchJava(java, "<span wicket:id=\"title\"></span>"))
    }

    fun testCausesAreDeterministicAndNotDuplicatedOnRematching() {
        val report = match("configure();", "<span wicket:id=\"title\"></span>", members = "void configure() {}")
        val again = WicketGraphMatcher().match(report)
        assertEquals(report, again)
        assertTrue(again.findings().all { it.causes.distinct() == it.causes })
        assertTrue(again.findings().all { it.causes.all { cause -> cause.severity == Severity.INFO } })
    }

    private fun match(body: String, html: String, members: String = ""): PageReport =
        matchJava(analyze(body, members = members), html)

    private fun matchJava(java: PageReport, html: String): PageReport {
        val markup = WicketMarkupAnalyzer().analyze(myFixture.configureByText("ExamplePage.html", html))
        return WicketGraphMatcher().match(java.copy(markup = markup, markupPath = markup.file))
    }

    private fun PageReport.findings() = requireNotNull(matching).findings

    private fun assertNoUnmatchedClaims(report: PageReport) {
        assertTrue(report.findings().none { it.code == DiagnosticCode.JAVA_ONLY || it.code == DiagnosticCode.MARKUP_ONLY })
        assertTrue(report.findings().all { it.severity == Severity.INFO })
    }

    private fun assertNoPositiveOrUnmatchedClaims(report: PageReport) {
        assertNoUnmatchedClaims(report)
        assertTrue(report.findings().none { it.code == DiagnosticCode.MATCHED })
        assertTrue(report.children().all { it.markupLocation == null })
    }
}
