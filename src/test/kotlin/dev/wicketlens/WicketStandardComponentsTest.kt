package dev.wicketlens

import com.google.gson.JsonParser
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiNewExpression
import com.intellij.psi.util.PsiTreeUtil
import dev.wicketlens.analysis.WicketPageSelection
import dev.wicketlens.export.WicketContextExporter
import dev.wicketlens.model.Confidence
import dev.wicketlens.model.DiagnosticCode
import dev.wicketlens.model.PageReport
import dev.wicketlens.model.Severity
import dev.wicketlens.ui.WicketReportFormatter
import java.nio.file.Files
import java.nio.file.Path

/** Exact Wicket 10.11.0 classes and all 33 public constructor overloads added in this slice. */
class WicketStandardComponentsTest : WicketProjectPsiTestCase() {
    private var sequence = 0

    fun testButtonOverloads() = supported(BUTTON)
    fun testSubmitLinkOverloads() = supported(SUBMIT)
    fun testLinkOverloadsWithAnonymousCallbacks() {
        supported(LINK)
        val report = report("add(new $LINK<Void>(\"control\") { public void onClick() { add(new Label(\"child\")); } });",
            "<a wicket:id=\"control\"><span wicket:id=\"child\"></span></a>")
        assertEquals(1, report.children().size)
        val findings = requireNotNull(report.matching).findings
        assertEquals(1, findings.count { it.code == DiagnosticCode.MATCHED })
        assertEquals(1, findings.count { it.code == DiagnosticCode.UNRESOLVED_PARENT })
        assertTrue(findings.none { it.code == DiagnosticCode.JAVA_ONLY || it.code == DiagnosticCode.MARKUP_ONLY })
    }
    fun testBookmarkablePageLinkOverloads() = supported(BOOKMARK)
    fun testExternalLinkOverloads() = supported(EXTERNAL)
    fun testAjaxLinkOverloadsWithAnonymousCallbacks() = supported(AJAX_LINK)
    fun testAjaxButtonOverloadsWithAnonymousBodies() = supported(AJAX_BUTTON)
    fun testCheckBoxOverloads() = supported(CHECK)
    fun testDropDownChoiceOverloads() = supported(CHOICE)
    fun testFeedbackPanelOverloadsDoNotExpandInternalChildren() = supported(FEEDBACK)

    fun testDynamicIdsStayUnresolvedForEveryAddedType() {
        for (component in COMPONENTS) {
            val expression = component.expression(component.arguments.first().replace("\"control\"", "System.getProperty(\"componentId\")"))
            val report = report("add($expression);", component.html)
            val node = report.children().single()
            assertNull(node.wicketId)
            assertEquals(Confidence.UNRESOLVED, node.confidence)
            assertTrue(node.diagnostics.any { it.code == DiagnosticCode.UNRESOLVED_DYNAMIC_ID })
            assertUnresolved(report)
        }
    }

    fun testInvalidLiteralIdsKeepTheirOwnUncertaintyReason() {
        for (id in listOf("", "a:b", "a~b")) {
            val report = report("add(new $BUTTON(\"$id\"));", "<div>No IDs</div>")
            assertEquals(id, report.children().single().wicketId)
            assertEquals(DiagnosticCode.UNRESOLVED_ID, requireNotNull(report.matching).findings.single().code)
            assertUnresolved(report)
        }
    }

    fun testSourceSubclassesTraceTheirReorderedConstructorArguments() {
        for ((name, base, superArguments) in listOf(
            Triple("SwappedButton", BUTTON, "id"),
            Triple("SwappedLink", EXTERNAL, "id, text"),
        )) {
            myFixture.addFileToProject("src/main/java/example/$name.java", """
                package example;
                public class $name extends $base {
                    public $name(String text, String id) { super($superArguments); }
                }
            """.trimIndent())
            for (body in listOf("", " {}")) {
                val report = report("add(new example.$name(\"control\", \"actualId\")$body);", "<span wicket:id=\"actualId\"></span>")
                assertEquals("example.$name", report.children().single().componentType)
                assertEquals("actualId", report.children().single().wicketId)
                assertEquals(DiagnosticCode.MATCHED, report.matching!!.findings.single().code)
            }
        }
    }

    fun testSameSimpleClassNameIsNotEnoughToAuthorizeAnId() {
        myFixture.addFileToProject("src/main/java/example/Button.java", """
            package example;
            public class Button extends org.apache.wicket.markup.html.basic.Label {
                public Button(String label, String id) { super(id.toUpperCase(), label); }
            }
        """.trimIndent())
        val report = report("add(new example.Button(\"control\", \"actualId\"));", "<span wicket:id=\"control\"></span>")
        assertNull(report.children().single().wicketId)
        assertUnresolved(report)
    }

    fun testAmbiguousAndInapplicableOverloadsStayUnresolved() {
        for (expression in listOf("new $SUBMIT(\"control\", null)", "new $BUTTON(\"control\", 42)", "new $CHECK(\"control\", 42)")) {
            val report = report("add($expression);", "<span wicket:id=\"control\"></span>")
            assertTrue(report.children().single().diagnostics.any { it.code == DiagnosticCode.UNRESOLVED_CONSTRUCTOR })
            assertUnresolved(report)
        }
    }

    fun testNewControlsKeepTheirNestedFormParentsAndRepeatedIds() {
        val report = report("""
            org.apache.wicket.markup.html.form.Form<?> left = new org.apache.wicket.markup.html.form.Form<>("left");
            org.apache.wicket.markup.html.form.Form<?> right = new org.apache.wicket.markup.html.form.Form<>("right");
            left.add(new $BUTTON("submit"), new $CHECK("enabled"));
            right.add(new $BUTTON("submit"), new $CHECK("enabled"));
            add(left, right);
        """.trimIndent(), """
            <form wicket:id="right"><div><input wicket:id="enabled" type="checkbox"/><button wicket:id="submit">Go</button></div></form>
            <form wicket:id="left"><button wicket:id="submit">Go</button><input wicket:id="enabled" type="checkbox"/></form>
        """.trimIndent())
        val findings = requireNotNull(report.matching).findings
        assertEquals(6, findings.size)
        assertTrue(findings.all { it.code == DiagnosticCode.MATCHED })
        val nodes = report.nodes.associateBy { it.nodeId }
        val elements = requireNotNull(report.markup).elements.associateBy { it.nodeId }
        findings.filter { nodes[it.javaNodeId]?.wicketId in setOf("submit", "enabled") }.forEach { finding ->
            val parent = nodes[nodes.getValue(finding.javaNodeId!!).parentNodeId]
            val markupParent = elements[elements.getValue(finding.markupNodeId!!).parentWicketElementId]
            assertEquals(parent?.wicketId, markupParent?.wicketId)
        }
    }

    fun testAnonymousCallbackRootEffectsStillInvalidateMatches() {
        val report = report("""
            add(new $AJAX_LINK<Void>("control") {
                public void onClick(org.apache.wicket.ajax.AjaxRequestTarget target) { StandardPage0.this.removeAll(); }
            });
        """.trimIndent(), "<a wicket:id=\"control\">Go</a>")
        assertTrue(report.warnings.any { it.code == DiagnosticCode.UNRESOLVED_SCOPE && it.location != null })
        assertUnresolved(report)
    }

    fun testControlsAlsoMatchInsideAnAnalyzedPanel() {
        val name = "ControlsPanel"
        val java = myFixture.addFileToProject("src/main/java/example/$name.java", """
            package example;
            public class $name extends org.apache.wicket.markup.html.panel.Panel {
                public $name() { super("root"); add(new $BUTTON("submit"), new $CHECK("enabled"), new $FEEDBACK("feedback")); }
            }
        """.trimIndent()) as PsiJavaFile
        companion(name, """
            <html><body><wicket:panel><button wicket:id="submit">Go</button><input wicket:id="enabled" type="checkbox"/><div wicket:id="feedback"></div></wicket:panel></body></html>
        """.trimIndent())
        val report = WicketPageSelection().analyze(project, java.virtualFile.url, -1)
        assertEquals(3, report.children().size)
        assertTrue(requireNotNull(report.matching).findings.all { it.code == DiagnosticCode.MATCHED })
    }

    fun testUnmatchedNewControlsRemainInformational() {
        val report = report("add(new $BUTTON(\"submit\"));", "<input wicket:id=\"enabled\" type=\"checkbox\"/>")
        val findings = requireNotNull(report.matching).findings
        assertEquals(setOf(DiagnosticCode.JAVA_ONLY, DiagnosticCode.MARKUP_ONLY), findings.map { it.code }.toSet())
        assertTrue(findings.all { it.severity == Severity.INFO && it.confidence == Confidence.INFERRED })
    }

    fun testDemoPageMatchesAllTenAddedTypesThroughTheFullPipeline() {
        val source = Path.of(requireNotNull(System.getProperty("wicketlens.test.demo")))
        myFixture.addFileToProject("src/main/java/example/CustomerPage.java",
            Files.readString(source.resolve("main/java/example/CustomerPage.java")))
        val java = myFixture.addFileToProject("src/main/java/example/StandardControlsPage.java",
            Files.readString(source.resolve("main/java/example/StandardControlsPage.java")))
        companion("StandardControlsPage", Files.readString(source.resolve("main/resources/example/StandardControlsPage.html")))
        val report = WicketPageSelection().analyze(project, java.virtualFile.url, -1)
        assertEquals(12, report.children().size)
        assertEquals(COMPONENTS.map { it.type }.toSet() + setOf("org.apache.wicket.markup.html.form.Form", "org.apache.wicket.markup.html.basic.Label"),
            report.children().map { it.componentType }.toSet())
        assertEquals(12, requireNotNull(report.matching).findings.size)
        assertTrue(report.matching.findings.all { it.code == DiagnosticCode.MATCHED && it.severity == Severity.INFO })
        assertTrue(report.warnings.any { it.code == DiagnosticCode.UNRESOLVED_SCOPE && it.message.contains("Anonymous") })
    }

    private fun supported(type: String) {
        val component = COMPONENTS.single { it.type == type }
        component.arguments.forEach { arguments ->
            val expression = component.expression(arguments)
            val report = report("add($expression);", component.html, expectedConstructor = type)
            val node = report.children().single()
            val finding = requireNotNull(report.matching).findings.single()
            assertEquals("control", node.wicketId)
            assertEquals(type, node.componentType)
            assertEquals(report.rootNodeId, node.parentNodeId)
            assertEquals(Confidence.CONFIRMED, node.confidence)
            assertTrue(node.diagnostics.isEmpty())
            assertEquals(DiagnosticCode.MATCHED, finding.code)
            assertEquals(node.javaLocation, finding.javaLocation)
            assertEquals(node.markupLocation, finding.markupLocation)
            assertNotNull(node.markupLocation)
            val json = JsonParser.parseString(WicketContextExporter().export(report)).asJsonObject
            val exported = json.getAsJsonArray("components").single { it.asJsonObject["nodeId"].asString == node.nodeId }.asJsonObject
            assertEquals(type, exported["componentType"].asString)
            assertEquals("control", exported["wicketId"].asString)
            assertEquals("MATCHED", exported["status"].asString)
            assertTrue(WicketReportFormatter().format(report).contains("control : ${type.substringAfterLast('.')}  [MATCHED]"))
        }
    }

    private fun report(body: String, html: String, expectedConstructor: String? = null): PageReport {
        val name = "StandardPage${sequence++}"
        val java = pageFile(name, body)
        val markup = companion(name, "<html><body>$html</body></html>")
        val before = java.text to markup.text
        val stamps = java.modificationStamp to markup.modificationStamp
        if (expectedConstructor != null) {
            val creation = PsiTreeUtil.findChildrenOfType(java, PsiNewExpression::class.java)
                .first { ((it.classReference?.resolve() as? PsiClass) ?: it.anonymousClass?.baseClassType?.resolve())?.qualifiedName == expectedConstructor }
            assertEquals("Constructor must resolve: ${creation.text}", expectedConstructor, creation.resolveConstructor()?.containingClass?.qualifiedName)
            assertTrue("Constructor overload must be valid: ${creation.text}", creation.resolveMethodGenerics().isValidResult)
        }
        val selection = WicketPageSelection()
        val report = selection.analyze(project, java.virtualFile.url, -1)
        assertEquals(markup.virtualFile.path, report.markupPath)
        assertFalse(report.scopeComplete)
        assertTrue(report.warnings.any { it.code == DiagnosticCode.PARTIAL_ANALYSIS })
        assertTrue(requireNotNull(report.matching).findings.all { it.severity == Severity.INFO })
        assertEquals(WicketContextExporter().export(report), WicketContextExporter().export(selection.analyze(project, java.virtualFile.url, -1)))
        assertEquals(before, java.text to markup.text)
        assertEquals(stamps, java.modificationStamp to markup.modificationStamp)
        return report
    }

    private fun assertUnresolved(report: PageReport) {
        assertTrue(requireNotNull(report.matching).findings.none {
            it.code in setOf(DiagnosticCode.MATCHED, DiagnosticCode.JAVA_ONLY, DiagnosticCode.MARKUP_ONLY)
        })
        assertTrue(report.children().all { it.markupLocation == null })
    }

    private data class Component(val type: String, val arguments: List<String>, val html: String,
        val generic: String = "", val callback: String = "") {
        fun expression(args: String) = "new $type$generic($args)$callback"
    }

    companion object {
        private const val BUTTON = "org.apache.wicket.markup.html.form.Button"
        private const val SUBMIT = "org.apache.wicket.markup.html.form.SubmitLink"
        private const val LINK = "org.apache.wicket.markup.html.link.Link"
        private const val BOOKMARK = "org.apache.wicket.markup.html.link.BookmarkablePageLink"
        private const val EXTERNAL = "org.apache.wicket.markup.html.link.ExternalLink"
        private const val AJAX_LINK = "org.apache.wicket.ajax.markup.html.AjaxLink"
        private const val AJAX_BUTTON = "org.apache.wicket.ajax.markup.html.form.AjaxButton"
        private const val CHECK = "org.apache.wicket.markup.html.form.CheckBox"
        private const val CHOICE = "org.apache.wicket.markup.html.form.DropDownChoice"
        private const val FEEDBACK = "org.apache.wicket.markup.html.panel.FeedbackPanel"
        private const val ID = "\"control\""
        private const val MODEL = "org.apache.wicket.model.Model.of(\"one\")"
        private const val FORM = "new org.apache.wicket.markup.html.form.Form<Void>(\"otherForm\")"
        private const val CHOICES = "java.util.List.of(\"one\", \"two\")"
        private const val CHOICE_MODEL = "new org.apache.wicket.model.util.ListModel<String>($CHOICES)"
        private const val RENDERER = "new org.apache.wicket.markup.html.form.ChoiceRenderer<String>()"
        private const val ANCHOR = "<a wicket:id=\"control\">Go</a>"
        private val COMPONENTS = listOf(
            Component(BUTTON, listOf(ID, "$ID, $MODEL"), "<button wicket:id=\"control\">Go</button>"),
            Component(SUBMIT, listOf(ID, "$ID, $FORM", "$ID, $MODEL", "$ID, $MODEL, $FORM"), ANCHOR),
            Component(LINK, listOf(ID, "$ID, $MODEL"), ANCHOR, "<String>", " { public void onClick() {} }"),
            Component(BOOKMARK, listOf("$ID, org.apache.wicket.markup.html.WebPage.class",
                "$ID, org.apache.wicket.markup.html.WebPage.class, new org.apache.wicket.request.mapper.parameter.PageParameters()"), ANCHOR, "<Void>"),
            Component(EXTERNAL, listOf("$ID, \"https://example.test\"", "$ID, \"https://example.test\", \"Go\"",
                "$ID, org.apache.wicket.model.Model.of(\"https://example.test\")",
                "$ID, org.apache.wicket.model.Model.of(\"https://example.test\"), $MODEL"), ANCHOR),
            Component(AJAX_LINK, listOf(ID, "$ID, $MODEL"), ANCHOR, "<String>",
                " { public void onClick(org.apache.wicket.ajax.AjaxRequestTarget target) {} }"),
            Component(AJAX_BUTTON, listOf(ID, "$ID, $MODEL", "$ID, $FORM", "$ID, $MODEL, $FORM"),
                "<button wicket:id=\"control\">Go</button>", callback = " {}"),
            Component(CHECK, listOf(ID, "$ID, org.apache.wicket.model.Model.of(true)"), "<input wicket:id=\"control\" type=\"checkbox\"/>"),
            Component(CHOICE, listOf(ID, "$ID, $CHOICES", "$ID, $CHOICES, $RENDERER", "$ID, $MODEL, $CHOICES",
                "$ID, $MODEL, $CHOICES, $RENDERER", "$ID, $CHOICE_MODEL", "$ID, $MODEL, $CHOICE_MODEL",
                "$ID, $CHOICE_MODEL, $RENDERER", "$ID, $MODEL, $CHOICE_MODEL, $RENDERER"),
                "<select wicket:id=\"control\"><option>One</option></select>", "<String>"),
            Component(FEEDBACK, listOf(ID, "$ID, message -> true"), "<div wicket:id=\"control\"></div>"),
        )
    }
}
