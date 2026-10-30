package dev.wicketlens

import com.google.gson.JsonParser
import com.intellij.psi.PsiJavaFile
import dev.wicketlens.analysis.WicketPageSelection
import dev.wicketlens.export.WicketContextExporter
import dev.wicketlens.model.Confidence
import dev.wicketlens.model.DiagnosticCode
import dev.wicketlens.model.PageReport
import dev.wicketlens.model.Severity
import dev.wicketlens.ui.ReportHierarchyBuilder
import java.nio.file.Files
import java.nio.file.Path

class WicketFieldTrackingTest : WicketProjectPsiTestCase() {
    private var sequence = 0

    fun testDeclarationInitializedFieldMatchesAtItsConstruction() {
        assertMatches(report("private final Label title = new Label(\"title\");", "add(title);"), 1)
    }

    fun testConstructorInitializedFieldMatchesWithThisQualification() {
        assertMatches(report("private final Label title;", "this.title = new Label(\"title\"); add(this.title);"), 1)
    }

    fun testDeclarationAfterConstructorStillInitializesBeforeItsBody() {
        assertMatches(report("private final Label title = new Label(\"title\");", "add(title);", fieldsAfterConstructor = true), 1)
    }

    fun testExplicitSuperAndParenthesizedRootFieldReads() {
        for (reference in listOf("title", "(this.title)", "FIELD_PAGE.this.title")) {
            assertMatches(report("private final Label title = new Label(\"title\");", "super(); add($reference);"), 1)
        }
    }

    fun testHookTextBeforeConstructorUsesConstructorInitializedField() {
        assertMatches(report("private final Label title;", "title = new Label(\"title\");",
            "super.onInitialize(); add(this.title);", hookFirst = true), 1)
    }

    fun testInlineContainerAttachedInConstructorReceivesHookChildren() {
        val report = report("private final Form<?> form = new Form<>(\"form\");", "add(form);", """
            super.onInitialize();
            Label title = new Label("title");
            this.form.add(title, new TextField<>("name"));
        """.trimIndent(), html = FORM_HTML)
        assertMatches(report, 3)
        val form = report.children().single { it.wicketId == "form" }
        assertEquals(2, report.children().count { it.parentNodeId == form.nodeId })
    }

    fun testConstructorAssignedContainerAndChildFieldsCrossPhases() {
        assertMatches(report("private final Form<?> form; private final Label title;", """
            this.form = new Form<>("form");
            title = new Label("title");
            add(this.form);
        """.trimIndent(), "super.onInitialize(); form.add(this.title, new TextField<>(\"name\"));", html = FORM_HTML), 3)
    }

    fun testContainerChildrenCanPrecedeHookAttachment() {
        assertMatches(report("private final Form<?> form = new Form<>(\"form\");",
            "form.add(new Label(\"title\"), new TextField<>(\"name\"));", "super.onInitialize(); add(form);", html = FORM_HTML), 3)
    }

    fun testImplicitConstructorSupportsInlineFieldsInInitialization() {
        assertMatches(report("private final Label title = new Label(\"title\");", initialization = "super.onInitialize(); add(title);",
            implicitConstructor = true), 1)
    }

    fun testShadowingLocalAndFieldKeepDifferentDeclarations() {
        assertMatches(report("private final Label title = new Label(\"field\");",
            "Label title = new Label(\"local\"); add(this.title, title);",
            html = "<span wicket:id=\"field\"></span><span wicket:id=\"local\"></span>"), 2)
    }

    fun testDeclaredComponentSupertypeKeepsConcreteConstructionType() {
        val report = report("private final org.apache.wicket.Component title = new Label(\"title\");", "add(title);")
        assertMatches(report, 1)
        assertEquals("org.apache.wicket.markup.html.basic.Label", report.children().single().componentType)
    }

    fun testPanelRootSupportsFieldContainers() {
        assertMatches(report("private final Form<?> form = new Form<>(\"form\");", "add(form);",
            "super.onInitialize(); form.add(new Label(\"title\"), new TextField<>(\"name\"));", base = "Panel",
            constructorPrefix = "super(\"root\");", html = "<wicket:panel>$FORM_HTML</wicket:panel>"), 3)
    }

    fun testRepeatedChildIdsStayLocalToTheirFieldContainers() {
        assertMatches(report("private final Form<?> left = new Form<>(\"left\"), right = new Form<>(\"right\");",
            "add(left, right);", "super.onInitialize(); left.add(new Label(\"title\")); right.add(new Label(\"title\"));",
            html = "<form wicket:id=\"right\"><span wicket:id=\"title\"></span></form>" +
                "<form wicket:id=\"left\"><b wicket:id=\"title\"></b></form>"), 4)
    }

    fun testUnusedFieldConstructionsAreNotAttachments() {
        val report = report("private final Label title = new Label(\"title\");")
        assertTrue(report.children().isEmpty())
        assertEquals(DiagnosticCode.MARKUP_ONLY, report.matching!!.findings.single().code)
    }

    fun testMutableStaticAndNonPrivateFieldsRemainUnresolved() {
        for (modifiers in listOf("private", "public final", "protected final", "final", "private static final")) {
            assertUnresolved(report("$modifiers Label title = new Label(\"title\");", "add(title);"))
        }
    }

    fun testAliasesAndEscapesInvalidateEvenEarlierFieldUse() {
        for (escape in listOf("Label alias = title;", "consume(title);", "Object alias = (Object) title;",
            "java.util.List<Label> sink = new java.util.ArrayList<>(); sink.add(title);")) {
            assertUnresolved(report("private final Label title = new Label(\"title\");", "add(title); $escape",
                members = "static Object escaped; static void consume(Object value) { escaped = value; }"))
        }
    }

    fun testGettersHelpersAndOtherLifecycleUsesAreNotExpanded() {
        for (member in listOf("Label getTitle() { return title; }", "void setup() { title.setVisible(true); }",
            "protected void onConfigure() { super.onConfigure(); title.setVisible(true); }")) {
            assertUnresolved(report("private final Label title = new Label(\"title\");", "add(title);", members = member))
        }
    }

    fun testContainerSettersAndMutationsInvalidateItsTrackedChildren() {
        for (operation in listOf("form.setEnabled(true);", "form.removeAll();")) {
            assertUnresolved(report("private final Form<?> form = new Form<>(\"form\");", """
                form.add(new Label("title")); add(form); $operation
            """.trimIndent(), html = "<form wicket:id=\"form\"><span wicket:id=\"title\"></span></form>"))
        }
    }

    fun testLambdasMethodReferencesAndNestedCapturesAreUnsupportedUses() {
        for (use in listOf("Runnable later = () -> consume(title);",
            "java.util.function.Supplier<String> later = title::getId;",
            "new Object() { Label value = FIELD_PAGE.this.title; };")) {
            assertUnresolved(report("private final Label title = new Label(\"title\");", "add(title); $use",
                members = "static void consume(Object value) {}"))
        }
    }

    fun testAccessThroughAnotherInstanceIsNotTheAnalyzedFieldInstance() {
        assertUnresolved(report("private final Label title = new Label(\"title\");", "add(title);",
            members = "void attachOther(FIELD_PAGE other) { add(other.title); }"))
    }

    fun testInheritedFieldsRemainUnresolved() {
        myFixture.addFileToProject("src/main/java/example/BaseFieldsPage.java", """
            package example;
            public class BaseFieldsPage extends org.apache.wicket.markup.html.WebPage {
                protected final org.apache.wicket.markup.html.basic.Label title = new org.apache.wicket.markup.html.basic.Label("title");
            }
        """.trimIndent())
        assertUnresolved(report("", "add(title);", base = "BaseFieldsPage"))
    }

    fun testExtraWritesAndAlternativeConstructorsDoNotChooseAnInitializer() {
        assertUnresolved(report("private final Label title = new Label(\"title\");",
            "add(title); title = new Label(\"other\");"))
        assertUnresolved(report("private final Label title;", "title = new Label(\"title\"); add(title);",
            members = "public FIELD_PAGE(int ignored) { title = new Label(\"other\"); }"))
    }

    fun testConditionalOrLateAssignmentsAreUnresolved() {
        for (body in listOf(
            "if (System.nanoTime() > 0) title = new Label(\"title\"); else throw new IllegalStateException(); add(title);",
            "add(title); title = new Label(\"title\");",
            "add(title);",
        )) assertUnresolved(report("private final Label title;", body))
    }

    fun testConstructorPreamblesControlFlowAndSyntaxDoNotConfirmFields() {
        for (body in listOf("int ignored = 0; super(); add(title);", "if (System.nanoTime() > 0) return; add(title);", "int broken = ; add(title);")) {
            assertUnresolved(report("private final Label title = new Label(\"title\");", body))
        }
    }

    fun testFieldReadsInOtherInitializersAreNotAssumedAvailable() {
        assertUnresolved(report("private final Label title = new Label(\"title\"); private final Object alias = title;", "add(title);"))
    }

    fun testSourceFactoryFieldsAreSupportedButLocalAliasAssignmentsAreNot() {
        assertMatches(report("private final Label title = make();", "add(title);", members = "static Label make() { return new Label(\"title\"); }"), 1)
        assertUnresolved(report("private final Label title;", "Label local = new Label(\"title\"); title = local; add(title);"))
    }

    fun testOpaqueRootEffectsWithoutAHookDoNotPreserveFieldMatches() {
        for (effect in listOf("setup();", "consume(this);")) {
            val report = report("private final Label title = new Label(\"title\");", "add(title); $effect",
                members = "void setup() { removeAll(); } static void consume(Object value) {}")
            assertUnresolved(report)
            assertEquals("title", report.children().single().wicketId)
        }
    }

    fun testUnsupportedFieldAndInstanceInitializerEffectsAreUnresolved() {
        for (extra in listOf(
            "private final Object sideEffect = consume(this);",
            "private final Object sideEffect = new Object() {{ FIELD_PAGE.this.removeAll(); }};",
            "private final Runnable sideEffect = this::removeAll;",
            "{ removeAll(); }",
        )) {
            assertUnresolved(report("private final Label title = new Label(\"title\"); $extra", "add(title);",
                members = "static Object consume(Object value) { return value; }"))
        }
    }

    fun testUncertainHookEffectsInvalidateConstructorFieldAttachments() {
        assertUnresolved(report("private final Label title = new Label(\"title\");", "add(title);", "super.onInitialize(); removeAll();"))
    }

    fun testMultipleAdditionsShareOneFieldNodeButHaveUnresolvedParentage() {
        val report = report("private final Label title = new Label(\"title\");", "add(title);", "super.onInitialize(); add(title);")
        assertEquals(1, report.children().size)
        assertEquals("title", report.children().single().wicketId)
        assertUnresolved(report)
    }

    fun testConstructorAndIdSemanticsKeepTheirExistingCauses() {
        val dynamic = report("private final Label title = new Label(System.getProperty(\"id\"));", "add(title);")
        assertTrue(dynamic.children().single().diagnostics.any { it.code == DiagnosticCode.UNRESOLVED_DYNAMIC_ID })
        assertUnresolved(dynamic)
        myFixture.addFileToProject("src/main/java/example/CustomLabel.java", """
            package example; public class CustomLabel extends org.apache.wicket.markup.html.basic.Label {
                public CustomLabel(String id) { super(id + "suffix"); }
            }
        """.trimIndent())
        val custom = report("private final CustomLabel title = new CustomLabel(\"title\");", "add(title);")
        assertTrue(custom.children().single().diagnostics.any { it.code == DiagnosticCode.UNRESOLVED_CONSTRUCTOR })
        assertUnresolved(custom)
    }

    fun testKnownUnmatchedFieldIdsStayInformational() {
        val report = report("private final Label title = new Label(\"javaOnly\");", "add(title);", html = "<span wicket:id=\"markupOnly\"></span>")
        assertEquals(setOf(DiagnosticCode.JAVA_ONLY, DiagnosticCode.MARKUP_ONLY), report.matching!!.findings.map { it.code }.toSet())
    }

    fun testFieldContainerHasOneTreeEntryAndExportedConstruction() {
        val report = report("private final Form<?> form = new Form<>(\"form\");", "add(form);",
            "super.onInitialize(); form.add(new Label(\"title\"));", html = "<form wicket:id=\"form\"><span wicket:id=\"title\"></span></form>")
        assertMatches(report, 2)
        val tree = ReportHierarchyBuilder().build(report)
        val form = tree.single { it.label.startsWith("form : Form") }
        val title = tree.single { it.label.startsWith("title : Label") }
        assertEquals(form.id, title.parentId)
        val formNode = report.children().single { it.wicketId == "form" }
        assertEquals(formNode.javaLocation, form.javaLocation)
        assertEquals(formNode.markupLocation, form.markupLocation)
        val components = JsonParser.parseString(WicketContextExporter().export(report)).asJsonObject.getAsJsonArray("components")
        val exported = components.single { it.asJsonObject["wicketId"].isJsonPrimitive && it.asJsonObject["wicketId"].asString == "form" }.asJsonObject
        assertEquals(formNode.javaLocation.offset, exported.getAsJsonObject("javaLocation")["offset"].asInt)
    }

    fun testUnattachedFieldContainersKeepKnownChildEdges() {
        val report = report("private final Form<?> form = new Form<>(\"form\");", "form.add(new Label(\"title\"));",
            html = "<form wicket:id=\"form\"><span wicket:id=\"title\"></span></form>")
        val form = report.children().single { it.wicketId == "form" }
        assertNull(form.parentNodeId)
        assertEquals(form.nodeId, report.children().single { it.wicketId == "title" }.parentNodeId)
        assertUnresolved(report)
    }

    fun testDemoFieldsMatchAcrossBothPhases() {
        val source = Path.of(requireNotNull(System.getProperty("wicketlens.test.demo")))
        val java = myFixture.addFileToProject("src/main/java/example/FieldComponentsPage.java",
            Files.readString(source.resolve("main/java/example/FieldComponentsPage.java")))
        companion("FieldComponentsPage", Files.readString(source.resolve("main/resources/example/FieldComponentsPage.html")))
        val report = WicketPageSelection().analyze(project, java.virtualFile.url, -1)
        assertMatches(report, 6)
        val form = report.children().single { it.wicketId == "form" }
        assertEquals(setOf("name", "enabled", "save"), report.children().filter { it.parentNodeId == form.nodeId }.map { it.wicketId }.toSet())
        assertEquals(1, report.children().count { it.wicketId == "form" })
    }

    private fun report(
        fields: String,
        body: String = "",
        initialization: String? = null,
        members: String = "",
        html: String = "<span wicket:id=\"title\"></span>",
        base: String = "WebPage",
        constructorPrefix: String = "",
        fieldsAfterConstructor: Boolean = false,
        hookFirst: Boolean = false,
        implicitConstructor: Boolean = false,
    ): PageReport {
        val name = "FieldPage${sequence++}"
        val hook = initialization?.let { "@Override protected void onInitialize() { $it }" }.orEmpty()
        val constructor = if (implicitConstructor) "" else "public $name() { $constructorPrefix $body }"
        val java = myFixture.addFileToProject("src/main/java/example/$name.java", """
            package example;
            import org.apache.wicket.markup.html.WebPage;
            import org.apache.wicket.markup.html.panel.Panel;
            import org.apache.wicket.markup.html.basic.Label;
            import org.apache.wicket.markup.html.form.Form;
            import org.apache.wicket.markup.html.form.TextField;
            public class $name extends $base {
                ${if (hookFirst) hook else ""}
                ${if (fieldsAfterConstructor) "" else fields}
                $constructor
                ${if (fieldsAfterConstructor) fields else ""}
                ${if (hookFirst) "" else hook}
                $members
            }
        """.trimIndent().replace("FIELD_PAGE", name)) as PsiJavaFile
        val markup = companion(name, "<html><body>$html</body></html>")
        val before = java.text to markup.text
        val stamps = java.modificationStamp to markup.modificationStamp
        val selection = WicketPageSelection()
        val report = selection.analyze(project, java.virtualFile.url, -1)
        assertFalse(report.scopeComplete)
        assertTrue(report.warnings.any { it.code == DiagnosticCode.PARTIAL_ANALYSIS })
        assertTrue(report.matching!!.findings.all { it.severity == Severity.INFO })
        report.children().filter { it.confidence == Confidence.CONFIRMED }.forEach {
            assertEquals(java.virtualFile.path, it.javaLocation.file)
            assertTrue(java.text.substring(it.javaLocation.offset).startsWith("new "))
        }
        val json = WicketContextExporter().export(report)
        assertEquals(1, JsonParser.parseString(json).asJsonObject["schemaVersion"].asInt)
        assertEquals(json, WicketContextExporter().export(selection.analyze(project, java.virtualFile.url, -1)))
        assertEquals(before, java.text to markup.text)
        assertEquals(stamps, java.modificationStamp to markup.modificationStamp)
        return report
    }

    private fun assertMatches(report: PageReport, count: Int) {
        assertEquals(count, report.children().size)
        assertEquals(count, report.matching!!.findings.size)
        assertTrue(report.matching.toString(), report.matching.findings.all { it.code == DiagnosticCode.MATCHED })
        assertTrue(report.warnings.toString(), report.warnings.none { it.code == DiagnosticCode.UNRESOLVED_SCOPE })
    }

    private fun assertUnresolved(report: PageReport) {
        assertTrue(report.matching.toString(), report.matching!!.findings.none {
            it.code in setOf(DiagnosticCode.MATCHED, DiagnosticCode.JAVA_ONLY, DiagnosticCode.MARKUP_ONLY)
        })
        assertTrue(report.children().all { it.confidence == Confidence.UNRESOLVED && it.markupLocation == null })
        assertTrue(report.matching.findings.all { it.causes.isNotEmpty() })
    }

    companion object {
        private const val FORM_HTML = "<form wicket:id=\"form\"><span wicket:id=\"title\"></span><input wicket:id=\"name\"/></form>"
    }
}
