package dev.wicketlens

import com.intellij.psi.PsiJavaFile
import dev.wicketlens.analysis.*
import dev.wicketlens.model.*

class WicketFormComponentsTest : WicketProjectPsiTestCase() {
    fun testDirectFormComponentMapsTheForwardedIdRatherThanTheFirstString() {
        input(parameters = "String caption, String id", delegation = "super(id);",
            members = "@Override public void convertInput() { setConvertedInput(\"value\"); }")
        companion()
        val report = analyze(pageFile(body = "add(new Input(\"Caption\", \"title\"));"))
        matched(report, "title")
        assertEquals("\"title\"", report.nodes.single { it.wicketId == "title" }.idLocation!!.let {
            reportSource().substring(it.offset, it.offset + 7)
        })
    }

    fun testFormComponentPanelUsesItsOwnPanelMarkup() {
        val java = composite()
        companion("Input", "<wicket:panel><input wicket:id=\"value\"/></wicket:panel>")
        val report = analyze(java)
        matched(report, "value")
        assertEquals(MarkupKind.PANEL, report.markup!!.kind)
        assertEquals(report.rootNodeId, report.nodes.single { it.wicketId == "value" }.parentNodeId)
    }

    fun testOuterAndInnerComponentsHaveSeparateScopes() {
        val input = composite()
        val inner = companion("Input", "<wicket:panel><input wicket:id=\"value\"/></wicket:panel>")
        val outer = companion(html = "<div wicket:id=\"title\"><input wicket:id=\"preview\"/></div><span wicket:id=\"other\"></span>")
        val page = pageFile(body = "add(new Input(\"title\"), new Label(\"other\"));")
        val report = analyze(page)
        matched(report, "title", "other")
        assertEquals(outer.virtualFile.path, report.nodes.single { it.wicketId == "title" }.markupLocation!!.file)
        assertTrue(report.markup!!.elements.single { it.wicketId == "preview" }.let { !it.participatesInComponentTree })
        assertEquals(inner.virtualFile.path, analyze(input).nodes.single { it.wicketId == "value" }.markupLocation!!.file)
        assertTrue(input.virtualFile.path in report.javaSourceFiles)
    }

    fun testPanelEmbeddingAFormComponentAlsoKeepsItsOwnScope() {
        input()
        val panel = file("InputPanel", """
            public class InputPanel extends org.apache.wicket.markup.html.panel.Panel {
                public InputPanel(String id) { super(id); add(new Input("value")); }
            }
        """)
        companion("InputPanel", "<wicket:panel><input wicket:id=\"value\"/></wicket:panel>")
        companion()
        matched(analyze(pageFile(body = "add(new InputPanel(\"title\"));")), "title")
        matched(analyze(panel), "value")
    }

    fun testPrivateFinalInputAndValueConversionAreSupported() {
        val java = composite(fields = "private final TextField<String> value = new TextField<>(\"value\");",
            body = "add(value);", members = "@Override public void convertInput() { setConvertedInput(value.getConvertedInput()); }")
        companion("Input", "<wicket:panel><input wicket:id=\"value\"/></wicket:panel>")
        matched(analyze(java), "value")
        companion()
        matched(analyze(pageFile(body = "add(new Input(\"title\"));")), "title")
    }

    fun testConstructorAssignedPrivateFinalField() {
        val java = composite(fields = "private final TextField<String> value;", body = "value = new TextField<>(\"value\"); add(value);")
        companion("Input", "<wicket:panel><input wicket:id=\"value\"/></wicket:panel>")
        matched(analyze(java), "value")
        companion()
        matched(analyze(pageFile(body = "add(new Input(\"title\"));")), "title")
    }

    fun testInitializeHookAndRequiredConfiguration() {
        val java = composite(body = "setRequired(true); setType(String.class);", members = """
            @Override protected void onInitialize() { super.onInitialize(); add(new TextField<String>("value")); }
        """)
        companion("Input", "<wicket:panel><input wicket:id=\"value\"/></wicket:panel>")
        matched(analyze(java), "value")
        companion()
        matched(analyze(pageFile(body = "add(new Input(\"title\"));")), "title")
    }

    fun testFormPanelInheritsSourceMarkupAndJavaSetup() {
        composite(name = "BaseInput")
        val java = file("Input", "public class Input extends BaseInput { public Input(String id) { super(id); } }")
        val html = companion("BaseInput", "<wicket:panel><input wicket:id=\"value\"/></wicket:panel>")
        val report = analyze(java)
        matched(report, "value")
        assertEquals(html.virtualFile.path, report.markupPath)
    }

    fun testHtmlAndJavaEditorTargetSelectionAcceptFormPanels() {
        val java = composite()
        val html = companion("Input", "<wicket:panel><input wicket:id=\"value\"/></wicket:panel>")
        val target = WicketEditorTargetResolver().resolve(project, html.virtualFile.url, 0)
        assertEquals(java.virtualFile.url, target.sourceUrl)
        matched(WicketPageSelection().analyze(project, target.sourceUrl, target.caretOffset, target.qualifiedClassName), "value")
        matched(WicketPageSelection().analyze(project, java.virtualFile.url, java.text.indexOf("add(")), "value")
    }

    fun testPlainFormComponentDoesNotInventAnOwnedMarkupScope() {
        val java = input()
        val html = companion("Input", "<wicket:panel><input wicket:id=\"value\"/></wicket:panel>")
        assertNull(WicketPageAnalyzer().analyze(java.classes.single()))
        try {
            WicketEditorTargetResolver().resolve(project, html.virtualFile.url, 0)
            fail("A plain FormComponent uses its owner's markup")
        } catch (_: AnalysisUnavailable) { }
    }

    fun testInlineChildrenOnPlainFormComponentStayUnresolved() {
        input(body = "add(new TextField<String>(\"value\"));")
        companion(html = "<div wicket:id=\"title\"><input wicket:id=\"value\"/></div>")
        val report = analyze(pageFile(body = "add(new Input(\"title\"));"))
        assertTrue(report.warnings.any { it.code == DiagnosticCode.UNRESOLVED_SCOPE })
        assertTrue(report.matching!!.findings.none { it.code == DiagnosticCode.MARKUP_ONLY })
    }

    fun testUnknownConstructorEffectCannotEstablishMissingPairs() {
        input(body = "Registry.touch();")
        file("Registry", "public class Registry { static void touch() {} }")
        companion()
        val report = analyze(pageFile(body = "add(new Input(\"title\"));"))
        assertTrue(report.matching!!.findings.none { it.code in setOf(DiagnosticCode.MATCHED, DiagnosticCode.MARKUP_ONLY) })
        assertTrue(report.nodes.any { it.diagnostics.any { issue -> issue.code == DiagnosticCode.UNRESOLVED_CONSTRUCTOR } })
    }

    fun testGetIdOverrideIsNotTrusted() {
        input(members = "@Override public String getId() { return \"different\"; }")
        companion()
        assertTrue(analyze(pageFile(body = "add(new Input(\"title\"));")).matching!!.findings.none { it.code == DiagnosticCode.MATCHED })
    }

    fun testTransformedConstructorIdIsNotGuessed() {
        input(delegation = "super(id + \"suffix\");")
        companion()
        assertTrue(analyze(pageFile(body = "add(new Input(\"title\"));")).matching!!.findings.none { it.code == DiagnosticCode.MATCHED })
    }

    fun testStructuralConversionCallbackIsUncertain() {
        val java = composite(members = "@Override public void convertInput() { removeAll(); }")
        companion("Input", "<wicket:panel><input wicket:id=\"missing\"/></wicket:panel>")
        val report = analyze(java)
        assertTrue(report.matching!!.findings.none { it.code == DiagnosticCode.MARKUP_ONLY })
        companion()
        assertTrue(analyze(pageFile(body = "add(new Input(\"title\"));")).matching!!.findings.none { it.code == DiagnosticCode.MATCHED })
    }

    fun testMalformedPreviewIsNotHiddenByCompositeBoundary() {
        composite()
        companion(html = "<div wicket:id=\"title\"><broken wicket:id=\"preview\"></div>")
        val report = analyze(pageFile(body = "add(new Input(\"title\"));"))
        assertFalse(report.markup!!.scopeComplete)
        assertTrue(report.markup.documentDiagnostics.isNotEmpty())
    }

    fun testUnrelatedUnmatchedIdStillReportsAProblemBesideACompositePreview() {
        composite()
        companion(html = "<div wicket:id=\"title\"><input wicket:id=\"preview\"/></div><input wicket:id=\"missing\"/>")
        val report = analyze(pageFile(body = "add(new Input(\"title\"));"))
        assertEquals(1, report.matching!!.findings.count { it.code == DiagnosticCode.MARKUP_ONLY })
        assertTrue(report.markup!!.scopeComplete)
    }

    fun testCallerAddedCompositeChildIsNotMatchedAgainstPreviewMarkup() {
        composite(body = "")
        companion(html = "<div wicket:id=\"title\"><input wicket:id=\"value\"/></div>")
        val report = analyze(pageFile(body = """
            Input input = new Input("title");
            input.add(new org.apache.wicket.markup.html.form.TextField<String>("value"));
            add(input);
        """.trimIndent()))
        assertEquals(1, report.matching!!.findings.count { it.code == DiagnosticCode.MATCHED })
        assertTrue(report.matching.findings.none { it.code in setOf(DiagnosticCode.JAVA_ONLY, DiagnosticCode.MARKUP_ONLY) })
        assertEquals(Confidence.UNRESOLVED, report.nodes.single { it.wicketId == "value" }.confidence)
    }

    fun testConstructorModelSideEffectIsNotIgnored() {
        file("Input", """
            public class Input extends org.apache.wicket.markup.html.form.FormComponent<String> {
                public Input(String id) { super(id, model()); }
                private static org.apache.wicket.model.IModel<String> model() { return null; }
            }
        """)
        companion()
        assertTrue(analyze(pageFile(body = "add(new Input(\"title\"));")).matching!!.findings.none { it.code == DiagnosticCode.MATCHED })
    }

    fun testEscapedFinalLocalContainerDoesNotQualifyAsIsolatedSetup() {
        composite(body = """
            final org.apache.wicket.markup.html.WebMarkupContainer container = new org.apache.wicket.markup.html.WebMarkupContainer("value");
            Registry.consume(container); add(container);
        """.trimIndent())
        file("Registry", "public class Registry { public static void consume(org.apache.wicket.Component value) {} }")
        companion()
        assertTrue(analyze(pageFile(body = "add(new Input(\"title\"));")).matching!!.findings.none { it.code == DiagnosticCode.MATCHED })
    }

    fun testAContainerSuppliedByAParameterIsNotAnOwnedReceiver() {
        file("Input", """
            public class Input extends org.apache.wicket.markup.html.form.FormComponentPanel<String> {
                public Input(String id, org.apache.wicket.MarkupContainer external) {
                    super(id); external.add(new TextField<String>("value"));
                }
            }
        """)
        companion()
        assertTrue(analyze(pageFile(body = "add(new Input(\"title\", this));")).matching!!.findings.none { it.code == DiagnosticCode.MATCHED })
    }

    fun testDemoShowsThreeParentPairsAndOneSeparateInternalPair() {
        val root = java.nio.file.Path.of(System.getProperty("wicketlens.test.demo"))
        for (name in listOf("CustomInput", "CompositeInput", "FormComponentsPage")) {
            myFixture.addFileToProject("src/main/java/example/$name.java", java.nio.file.Files.readString(root.resolve("main/java/example/$name.java")))
        }
        for (name in listOf("CompositeInput", "FormComponentsPage")) {
            companion(name, java.nio.file.Files.readString(root.resolve("main/resources/example/$name.html")))
        }
        fun java(name: String) = com.intellij.psi.PsiManager.getInstance(project)
            .findFile(myFixture.findFileInTempDir("src/main/java/example/$name.java")) as PsiJavaFile
        matched(analyze(java("FormComponentsPage")), "form", "name", "editor")
        matched(analyze(java("CompositeInput")), "value")
    }

    private fun input(parameters: String = "String id", delegation: String = "super(id);", body: String = "", members: String = "") =
        file("Input", "public class Input extends org.apache.wicket.markup.html.form.FormComponent<String> { public Input($parameters) { $delegation $body } $members }")

    private fun composite(name: String = "Input", fields: String = "", body: String = "add(new TextField<String>(\"value\"));", members: String = "") =
        file(name, "public class $name extends org.apache.wicket.markup.html.form.FormComponentPanel<String> { $fields public $name(String id) { super(id); $body } $members }")

    private fun file(name: String, text: String) = myFixture.addFileToProject("src/main/java/example/$name.java",
        "package example; import org.apache.wicket.markup.html.form.TextField; $text") as PsiJavaFile
    private fun analyze(java: PsiJavaFile) = WicketPageAnalyzer().analyze(java.classes.single())!!
    private fun matched(report: PageReport, vararg ids: String) {
        assertEquals(report.toString(), ids.toSet(), report.nodes.filter { it.nodeId != report.rootNodeId }.map { it.wicketId }.toSet())
        assertEquals(report.toString(), ids.size, report.matching!!.findings.count { it.code == DiagnosticCode.MATCHED })
        assertTrue(report.toString(), report.warnings.none { it.code == DiagnosticCode.UNRESOLVED_SCOPE })
    }
    private fun reportSource() = myFixture.findFileInTempDir("src/main/java/example/ExamplePage.java").let {
        com.intellij.psi.PsiManager.getInstance(project).findFile(it)!!.text
    }
}
