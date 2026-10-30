package dev.wicketlens

import com.intellij.psi.PsiJavaFile
import dev.wicketlens.analysis.WicketPageAnalyzer
import dev.wicketlens.export.WicketContextExporter
import dev.wicketlens.model.*

class WicketSourceSetupTest : WicketProjectPsiTestCase() {
    fun testPrivateHelperAddsComponentsOnlyWhenInvoked() {
        val report = report("setup();", """
            private void setup() { add(new Label("title")); }
            private void unused() { add(new Label("unused")); }
        """.trimIndent())
        assertIds(report, "title")
        assertMatched(report, 1)
    }

    fun testComponentParametersKeepTheirContainerIdentityAcrossHelpers() {
        val report = report("Form<?> form = new Form<>(\"form\"); populate(form); add(form);", """
            private void populate(Form<?> container) { install(container, "title"); }
            private void install(Form<?> container, String id) { container.add(new Label(id)); }
        """.trimIndent(), "<form wicket:id=\"form\"><span wicket:id=\"title\"></span></form>")
        assertMatched(report, 2)
        val form = report.children().single { it.wicketId == "form" }
        assertEquals(form.nodeId, report.children().single { it.wicketId == "title" }.parentNodeId)
    }

    fun testFactoryInvocationsHaveDistinctInstancesAndIdOrigins() {
        val report = report("add(label(\"first\"), label(\"second\"));", "private Label label(String id) { return new Label(id); }",
            "<span wicket:id=\"first\"></span><span wicket:id=\"second\"></span>")
        assertMatched(report, 2)
        assertEquals(2, report.children().map { it.nodeId }.distinct().size)
        assertEquals(1, report.children().map { it.javaLocation }.distinct().size)
        assertEquals(2, report.children().map { it.idLocation }.distinct().size)
        assertTrue(report.children().all { it.idRenameSafe })
    }

    fun testFactoryCanBuildAContainerAndItsChildrenBeforeReturningIt() {
        val report = report("add(build(\"form\"));", """
            private Form<?> build(String id) {
                Form<?> form = new Form<>(id);
                form.add(new Label("title"));
                return form;
            }
        """.trimIndent(), "<form wicket:id=\"form\"><span wicket:id=\"title\"></span></form>")
        assertMatched(report, 2)
        assertFalse(report.children().single { it.wicketId == "title" }.idRenameSafe)
    }

    fun testStaticSourceFactoryCanLiveInAnotherFile() {
        val factory = myFixture.addFileToProject("src/main/java/example/Components.java", """
            package example; public class Components {
                public static org.apache.wicket.markup.html.basic.Label label(String id) {
                    return new org.apache.wicket.markup.html.basic.Label(id);
                }
            }
        """.trimIndent())
        val report = report("add(Components.label(\"title\"));")
        assertMatched(report, 1)
        assertEquals(factory.virtualFile.path, report.children().single().javaLocation.file)
        assertTrue(factory.virtualFile.path in report.javaSourceFiles)
    }

    fun testFinalHelperHasAClosedDispatchTarget() {
        val report = report("setup();", "protected final void setup() { add(new Label(\"title\")); }")
        assertMatched(report, 1)
    }

    fun testHelpersCanRunInTheReviewedInitializationPhase() {
        val report = report("", """
            @Override protected void onInitialize() { super.onInitialize(); setup(); }
            private void setup() { add(new Label("title")); }
        """.trimIndent())
        assertMatched(report, 1)
    }

    fun testPrivateFinalFieldsCanBePassedToTracedHelpers() {
        val report = report("add(form); populate(form);", """
            private final Form<?> form = new Form<>("form");
            private void populate(Form<?> target) { target.add(new Label("title")); }
        """.trimIndent(), "<form wicket:id=\"form\"><span wicket:id=\"title\"></span></form>")
        assertMatched(report, 2)
    }

    fun testSourceSuperclassConstructorsContributeOriginalLocations() {
        val base = base("BasePage", "add(new Label(\"base\"));")
        val java = page("add(new Label(\"title\"));", base = "BasePage")
        val report = analyze(java, "<span wicket:id=\"base\"></span><span wicket:id=\"title\"></span>")
        assertMatched(report, 2)
        assertEquals(base.virtualFile.path, report.children().single { it.wicketId == "base" }.javaLocation.file)
        assertTrue(base.virtualFile.path in report.javaSourceFiles)
        assertFalse(report.children().single { it.wicketId == "base" }.idRenameSafe)
    }

    fun testLiteralConstructorArgumentsBindIntoSuperclassSetup() {
        base("BasePage", "add(new Label(id));", parameters = "String id")
        val java = page("super(\"title\");", base = "BasePage")
        val report = analyze(java)
        assertMatched(report, 1)
        assertEquals(java.virtualFile.path, report.children().single().idLocation!!.file)
        assertEquals(java.text.indexOf("\"title\""), report.children().single().idLocation!!.offset)
    }

    fun testInheritedInitializationAndPrivateFieldsUseOneRoot() {
        base("BasePage", "add(form);", """
            private final Form<?> form = new Form<>("form");
            @Override protected void onInitialize() { super.onInitialize(); form.add(new Label("name")); }
        """.trimIndent())
        val java = page("", "@Override protected void onInitialize() { super.onInitialize(); add(new Label(\"title\")); }", "BasePage")
        val report = analyze(java, "<form wicket:id=\"form\"><span wicket:id=\"name\"></span></form><span wicket:id=\"title\"></span>")
        assertMatched(report, 3)
    }

    fun testImplicitConstructorsAndMultipleSourceAncestorsAreSupported() {
        myFixture.addFileToProject("src/main/java/example/BasePage.java", "package example; public class BasePage extends org.apache.wicket.markup.html.WebPage {}")
        base("MiddlePage", "add(new Label(\"base\"));", parent = "BasePage")
        val report = analyze(page("add(new Label(\"title\"));", base = "MiddlePage"), "<span wicket:id=\"base\"></span><span wicket:id=\"title\"></span>")
        assertMatched(report, 2)
        assertEquals(3, report.javaSourceFiles.size)
    }

    fun testAnEmptySourceAncestorNoLongerMakesKnownUnmatchedPathsUnresolved() {
        base("BasePage", "")
        val report = analyze(page("add(new Label(\"javaOnly\"));", base = "BasePage"), "<span wicket:id=\"htmlOnly\"></span>")
        assertEquals(setOf(DiagnosticCode.JAVA_ONLY, DiagnosticCode.MARKUP_ONLY), report.matching!!.findings.map { it.code }.toSet())
    }

    fun testExplicitSuperCallCanSelectAnImplicitSourceConstructor() {
        myFixture.addFileToProject("src/main/java/example/BasePage.java", "package example; public class BasePage extends org.apache.wicket.markup.html.WebPage {}")
        assertMatched(analyze(page("super(); add(new Label(\"title\"));", base = "BasePage")), 1)
    }

    fun testCustomConstructorCanForwardAnIdFromAnotherArgumentPosition() {
        custom("CustomLabel", "public CustomLabel(String text, String id) { super(id, text); }")
        val java = page("add(new CustomLabel(\"display\", \"title\"));")
        val report = analyze(java)
        assertMatched(report, 1)
        assertEquals(java.text.indexOf("\"title\""), report.children().single().idLocation!!.offset)
    }

    fun testCustomConstructorDelegationAndFixedIdsAreTracedWithoutGuessing() {
        custom("CustomLabel", """
            public CustomLabel(String text, String id) { this(id); }
            public CustomLabel(String id) { super(id); }
        """.trimIndent())
        assertMatched(report("add(new CustomLabel(\"display\", \"title\"));"), 1)
    }

    fun testAConstantInACustomConstructorIsNotSafeToRenamePerInstance() {
        val wrapper = custom("CustomLabel", "public CustomLabel(String ignored) { super(\"title\"); }")
        val report = report("add(new CustomLabel(\"different\"));")
        assertMatched(report, 1)
        assertEquals(wrapper.virtualFile.path, report.children().single().idLocation!!.file)
        assertFalse(report.children().single().idRenameSafe)
    }

    fun testTransformedIdsAndExtraConstructorEffectsStayUnresolved() {
        custom("CustomLabel", "public CustomLabel(String id) { super(id + \"suffix\"); }")
        val report = report("add(new CustomLabel(\"title\"));")
        assertNoUnmatched(report)
        assertTrue(report.children().single().diagnostics.any { it.code == DiagnosticCode.UNRESOLVED_CONSTRUCTOR })
    }

    fun testOverridableHelpersRemainUnresolved() {
        val report = report("setup();", "protected void setup() { add(new Label(\"title\")); }")
        assertNoUnmatched(report)
        assertTrue(report.children().all { it.confidence == Confidence.UNRESOLVED })
    }

    fun testRecursiveHelpersAndFactoriesDoNotOverflowOrConfirmPaths() {
        val report = report("add(label(\"title\"));", "private Label label(String id) { return label(id); }")
        assertNoUnmatched(report)
        assertTrue(report.children().all { it.confidence == Confidence.UNRESOLVED })
    }

    fun testControlFlowInAHelperCannotProduceAnAbsenceClaim() {
        val report = report("setup();", "private void setup() { if (System.getProperty(\"enabled\") != null) add(new Label(\"title\")); }")
        assertNoUnmatched(report)
    }

    fun testAFactoryCannotReturnAnExistingFieldAsAFreshInstance() {
        val report = report("add(label());", """
            private final Label title = new Label("title");
            private Label label() { return title; }
        """.trimIndent())
        assertNoUnmatched(report)
    }

    fun testDynamicFactoryIdsAndEscapedRootsStayUnresolved() {
        val report = report("add(label(System.getProperty(\"id\")));", "private Label label(String id) { return new Label(id); }")
        assertNoUnmatched(report)
    }

    fun testUnexpandedFieldGetterPreventsPromotingHelperSetup() {
        val report = report("setup();", """
            private final Label title = new Label("title");
            private void setup() { add(title); }
            public Label title() { return title; }
        """.trimIndent())
        assertNoUnmatched(report)
    }

    fun testInheritedVirtualCallsAreNotResolvedToTheLeafOverrideByName() {
        base("BasePage", "setup();", "protected void setup() { add(new Label(\"base\")); }")
        val report = analyze(page("", "@Override protected void setup() { add(new Label(\"title\")); }", "BasePage"))
        assertNoUnmatched(report)
    }

    fun testUnsupportedInheritedLifecycleEffectsKeepTheWholeSetupUnresolved() {
        base("BasePage", "add(new Label(\"base\"));", "@Override protected void onBeforeRender() { super.onBeforeRender(); removeAll(); }")
        assertNoUnmatched(analyze(page("add(new Label(\"title\"));", base = "BasePage")))
    }

    fun testFiniteButDeepCallChainsRespectTheExpansionBudget() {
        val members = (0..16).joinToString("\n") { index ->
            "private void step$index() { ${if (index == 16) "add(new Label(\"title\"));" else "step${index + 1}();"} }"
        }
        assertNoUnmatched(report("step0();", members))
    }

    fun testConstructorContractSourcesAlsoCountTowardTheSourceBudget() {
        val additions = (0..24).joinToString("\n") { index ->
            custom("Heading$index", "public Heading$index(String id) { super(id); }")
            "add(new Heading$index(\"id$index\"));"
        }
        assertNoUnmatched(report("setup();", "private void setup() { $additions }"))
    }

    fun testSourceExpansionIsDeterministicAndDoesNotExportSnapshotOnlyMetadata() {
        val java = page("add(label(\"title\"));", "private Label label(String id) { return new Label(id); }")
        companion()
        val before = java.text
        val analyzer = WicketPageAnalyzer()
        val first = analyzer.analyze(java.classes.single())!!
        val second = analyzer.analyze(java.classes.single())!!
        assertEquals(first, second)
        assertEquals(before, java.text)
        val json = WicketContextExporter().export(first)
        assertEquals(json, WicketContextExporter().export(second))
        assertFalse(json.contains("javaSourceFiles"))
        assertFalse(json.contains("idRenameSafe"))
        assertFalse(first.scopeComplete)
    }

    fun testOpaqueHelperEscapesKeepEarlierComponentsUnresolved() {
        val report = report("add(new Label(\"title\")); consume(this);", "private void consume(Object root) {}")
        assertNoUnmatched(report)
    }

    fun testUnknownAncestorClassInitializationCannotHideBehindAStaticFactory() {
        myFixture.addFileToProject("src/main/java/example/PartsBase.java", """
            package example; public class PartsBase { static { System.setProperty("setup", "changed"); } }
        """.trimIndent())
        myFixture.addFileToProject("src/main/java/example/Parts.java", """
            package example; public class Parts extends PartsBase {
                public static org.apache.wicket.markup.html.basic.Label label(String id) { return new org.apache.wicket.markup.html.basic.Label(id); }
            }
        """.trimIndent())
        assertNoUnmatched(report("add(Parts.label(\"title\"));"))
    }

    fun testFactoryCannotReplaceOrDetachAnAlreadyAddedContainer() {
        val report = report("add(build(\"form\"));", """
            private Form<?> build(String id) {
                Form<?> form = new Form<>(id); form.add(new Label("title")); form.removeAll(); return form;
            }
        """.trimIndent(), "<form wicket:id=\"form\"><span wicket:id=\"title\"></span></form>")
        assertNoUnmatched(report)
    }

    fun testSourceConstructorsWithAdditionalEffectsAreNotForwardingContracts() {
        custom("CustomLabel", "public CustomLabel(String id) { super(id); setVisible(true); }")
        assertNoUnmatched(report("add(new CustomLabel(\"title\"));"))
    }

    fun testRepeatedHelperAttachmentsOfOneInstanceDoNotBecomeUniqueParents() {
        val report = report("Label title = new Label(\"title\"); attach(title); attach(title);", "private void attach(Label child) { add(child); }")
        assertNoUnmatched(report)
    }

    fun testForwardedCustomContainersKeepTheirChildrenUnderTheCorrectId() {
        myFixture.addFileToProject("src/main/java/example/CustomForm.java", """
            package example; public class CustomForm extends org.apache.wicket.markup.html.form.Form<Void> {
                public CustomForm(String text, String id) { super(id); }
            }
        """.trimIndent())
        val report = report("CustomForm form = new CustomForm(\"display\", \"form\"); form.add(new Label(\"title\")); add(form);",
            html = "<form wicket:id=\"form\"><span wicket:id=\"title\"></span></form>")
        assertMatched(report, 2)
    }

    fun testDemoCombinesAllFourSourceFeaturesWithInheritedMarkup() {
        val root = java.nio.file.Path.of(System.getProperty("wicketlens.test.demo"))
        for (name in listOf("SourceSetupBasePage", "SourceHeading", "SourceSetupPage")) {
            myFixture.addFileToProject("src/main/java/example/$name.java", java.nio.file.Files.readString(root.resolve("main/java/example/$name.java")))
        }
        for (name in listOf("SourceSetupBasePage", "SourceSetupPage")) {
            companion(name, java.nio.file.Files.readString(root.resolve("main/resources/example/$name.html")))
        }
        val file = myFixture.findFileInTempDir("src/main/java/example/SourceSetupPage.java")
        val page = (com.intellij.psi.PsiManager.getInstance(project).findFile(file) as PsiJavaFile).classes.single()
        val report = WicketPageAnalyzer().analyze(page)!!
        assertMatched(report, 7)
        assertEquals(3, report.javaSourceFiles.size)
        assertEquals(2, report.markup!!.sourceFiles.size)
    }

    private fun report(body: String, members: String = "", html: String = "<span wicket:id=\"title\"></span>") = analyze(page(body, members), html)

    private fun analyze(java: PsiJavaFile, html: String = "<span wicket:id=\"title\"></span>"): PageReport {
        companion(java.classes.single().name!!, html)
        return WicketPageAnalyzer().analyze(java.classes.single())!!
    }

    private fun page(body: String, members: String = "", base: String = "org.apache.wicket.markup.html.WebPage") =
        myFixture.addFileToProject("src/main/java/example/ExamplePage.java", """
            package example;
            import org.apache.wicket.markup.html.basic.Label;
            import org.apache.wicket.markup.html.form.Form;
            public class ExamplePage extends $base {
                public ExamplePage() { $body }
                $members
            }
        """.trimIndent()) as PsiJavaFile

    private fun base(name: String, body: String, members: String = "", parameters: String = "", parent: String = "org.apache.wicket.markup.html.WebPage") =
        myFixture.addFileToProject("src/main/java/example/$name.java", """
            package example; import org.apache.wicket.markup.html.basic.Label;
            import org.apache.wicket.markup.html.form.Form;
            public class $name extends $parent { public $name($parameters) { $body } $members }
        """.trimIndent())

    private fun custom(name: String, constructors: String) = myFixture.addFileToProject("src/main/java/example/$name.java", """
        package example; public class $name extends org.apache.wicket.markup.html.basic.Label { $constructors }
    """.trimIndent())

    private fun assertIds(report: PageReport, vararg ids: String) = assertEquals(ids.toSet(), report.children().map { it.wicketId }.toSet())
    private fun assertMatched(report: PageReport, count: Int) {
        assertEquals(report.toString(), count, report.matching!!.findings.count { it.code == DiagnosticCode.MATCHED })
        assertTrue(report.toString(), report.children().all { it.confidence == Confidence.CONFIRMED })
        assertTrue(report.warnings.none { it.code == DiagnosticCode.UNRESOLVED_SCOPE })
    }
    private fun assertNoUnmatched(report: PageReport) = assertTrue(report.toString(), report.matching!!.findings.none { it.code in setOf(DiagnosticCode.JAVA_ONLY, DiagnosticCode.MARKUP_ONLY) })
}
