package dev.wicketlens

import com.intellij.psi.PsiJavaFile
import dev.wicketlens.analysis.WicketPageAnalyzer
import dev.wicketlens.export.WicketContextExporter
import dev.wicketlens.model.*

class WicketSourceDispatchTest : WicketProjectPsiTestCase() {
    fun testFinalPageDispatchesABaseConstructorCallToItsOverride() {
        base("setup();", "protected void setup() { add(new Label(\"wrong\")); }")
        val java = page("", "@Override protected void setup() { add(new Label(\"title\")); }", final = true, base = "BasePage")
        val report = analyze(java)
        matched(report, 1)
        assertEquals(setOf("title"), report.children().map { it.wicketId }.toSet())
        assertEquals(java.virtualFile.path, report.children().single().javaLocation.file)
    }

    fun testFinalOverrideClosesDispatchEvenOnANonfinalPage() {
        base("setup();", "protected void setup() { add(new Label(\"wrong\")); }")
        matched(analyze(page("", "@Override protected final void setup() { add(new Label(\"title\")); }", base = "BasePage")), 1)
    }

    fun testFinalPageCanUseAnInheritedNonfinalImplementation() {
        base("", "protected void setup() { add(new Label(\"title\")); }")
        matched(analyze(page("setup();", final = true, base = "BasePage")), 1)
    }

    fun testSuperSelectsTheBaseBodyButItsVirtualCallStillUsesTheLeaf() {
        base("", "protected void setup() { add(label(\"title\")); } protected Label label(String id) { return new Label(\"wrong\"); }")
        matched(analyze(page("super.setup();", "@Override protected Label label(String id) { return new Label(id); }", final = true, base = "BasePage")), 1)
    }

    fun testInitializationDispatchUsesTheFinalOverride() {
        base("", """
            @Override protected void onInitialize() { super.onInitialize(); setup(); }
            protected void setup() { add(new Label("wrong")); }
        """)
        matched(analyze(page("", "@Override protected void setup() { add(new Label(\"title\")); }", final = true, base = "BasePage")), 1)
    }

    fun testConstructorDispatchCannotReadTheLeafsLaterFieldInitializer() {
        base("setup();", "protected void setup() {}")
        uncertain(analyze(page("", """
            private final Label title = new Label("title");
            @Override protected void setup() { add(title); }
        """, final = true, base = "BasePage")))
    }

    fun testNonfinalPageWithAnOverridableLeafImplementationStaysUnresolved() {
        base("setup();", "protected void setup() { add(new Label(\"wrong\")); }")
        uncertain(analyze(page("", "@Override protected void setup() { add(new Label(\"title\")); }", base = "BasePage")))
    }

    fun testPrivateBaseHelperIsNotReplacedByASameNamedLeafMethod() {
        base("setup();", "private void setup() { add(new Label(\"title\")); }")
        matched(analyze(page("", "private void setup() { add(new Label(\"wrong\")); }", final = true, base = "BasePage")), 1)
    }

    fun testKnownHelperDispatchesThroughAnInterfaceAndRecordsItsSources() {
        val api = api()
        val impl = parts()
        val java = page("Parts parts = new DefaultParts(); add(parts.label(\"title\"));")
        val report = analyze(java)
        matched(report, 1)
        assertEquals(impl.virtualFile.path, report.children().single().javaLocation.file)
        assertTrue(listOf(api.virtualFile.path, impl.virtualFile.path, java.virtualFile.path).all { it in report.javaSourceFiles })
        assertTrue(report.children().single().idRenameSafe)
    }

    fun testFreshHelpersCanBeCalledInlineAndRepeatedFactoriesStayDistinct() {
        parts()
        val report = analyze(page("add(new DefaultParts().label(\"first\"), new DefaultParts().label(\"second\"));"),
            "<span wicket:id=\"first\"></span><span wicket:id=\"second\"></span>")
        matched(report, 2)
        assertEquals(2, report.children().map { it.nodeId }.distinct().size)
        assertEquals(1, report.children().map { it.javaLocation }.distinct().size)
    }

    fun testAHelperCanPopulateAComponentParameterThroughAPrivateMethod() {
        parts(members = """
            public void populate(Form<?> target) { install(target); }
            private void install(Form<?> target) { target.add(new Label("title")); }
        """)
        val report = analyze(page("Form<?> form = new Form<>(\"form\"); new DefaultParts().populate(form); add(form);"),
            "<form wicket:id=\"form\"><span wicket:id=\"title\"></span></form>")
        matched(report, 2)
    }

    fun testHelperFieldsCanBeInitializedInTheConstructorAndUsedInInitialization() {
        api(); parts()
        matched(analyze(page("parts = new DefaultParts();", """
            private final Parts parts;
            @Override protected void onInitialize() { super.onInitialize(); add(parts.label("title")); }
        """)), 1)
    }

    fun testAHelperCanPassAnotherKnownHelperToASourceMethod() {
        api(); parts()
        matched(analyze(page("Parts parts = new DefaultParts(); attach(parts, \"title\");",
            "private void attach(Parts parts, String id) { add(parts.label(id)); }")), 1)
    }

    fun testInheritedHelperImplementationUsesItsPrivateBaseHelper() {
        parts(name = "BaseParts", body = "return make(id);", members = "private Label make(String id) { return new Label(id); }")
        source("DefaultParts", "public class DefaultParts extends BaseParts { public DefaultParts() { super(); } }")
        matched(analyze(page("add(new DefaultParts().label(\"title\"));")), 1)
    }

    fun testHelperSuperCallDoesNotRedispatchTheSelectedBody() {
        parts(name = "BaseParts")
        source("DefaultParts", """
            public class DefaultParts extends BaseParts {
                @Override public Label label(String id) { return super.label(id); }
            }
        """)
        matched(analyze(page("add(new DefaultParts().label(\"title\"));")), 1)
    }

    fun testGenericInterfaceDispatchUsesTheActualOverride() {
        source("Parts", "public interface Parts<T> { Label label(T id); }")
        source("DefaultParts", "public class DefaultParts implements Parts<String> { public Label label(String id) { return new Label(id); } }")
        matched(analyze(page("Parts<String> parts = new DefaultParts(); add(parts.label(\"title\"));")), 1)
    }

    fun testAnOverloadIsNotMistakenForAnOverride() {
        parts(name = "BaseParts")
        source("DefaultParts", "public class DefaultParts extends BaseParts { public Label label(Object value) { return new Label(\"wrong\"); } }")
        matched(analyze(page("BaseParts parts = new DefaultParts(); add(parts.label(\"title\"));")), 1)
    }

    fun testRawGenericArgumentsCannotBypassBridgeCasts() {
        source("Parts", "public interface Parts<T> { Label label(T id); }")
        source("DefaultParts", "public class DefaultParts implements Parts<String> { public Label label(String ignored) { return new Label(\"title\"); } }")
        uncertain(analyze(page("Parts parts = new DefaultParts(); Object id = null; add(parts.label(id));")))
    }

    fun testInjectedHelperCannotBeResolvedFromItsDeclaredInterface() {
        api(); parts()
        uncertain(analyze(page("add(parts.label(\"title\"));", parameters = "Parts parts")))
    }

    fun testMutableStatefulHelpersStayUnresolved() {
        parts(members = "private String prefix = \"stored\";")
        uncertain(analyze(page("add(new DefaultParts().label(\"title\"));")))
    }

    fun testEffectfulHelperConstructorsStayUnresolved() {
        parts(members = "public DefaultParts() { System.setProperty(\"sideEffect\", \"yes\"); }")
        uncertain(analyze(page("add(new DefaultParts().label(\"title\"));")))
    }

    fun testHelperStaticInitializationIsAuditedAcrossItsAncestry() {
        parts(name = "BaseParts", members = "static { System.setProperty(\"sideEffect\", \"yes\"); }")
        source("DefaultParts", "public class DefaultParts extends BaseParts {}")
        uncertain(analyze(page("add(new DefaultParts().label(\"title\"));")))
    }

    fun testAReviewedSourceInterfaceDefaultCanSupplyAComponent() {
        source("Parts", "public interface Parts { default Label label(String id) { return new Label(id); } }")
        source("DefaultParts", "public class DefaultParts implements Parts {}")
        matched(analyze(page("Parts parts = new DefaultParts(); add(parts.label(\"title\"));")), 1)
    }

    fun testMutableHelperFieldsDoNotBecomeStableReceivers() {
        parts()
        uncertain(analyze(page("add(parts.label(\"title\"));", "private DefaultParts parts = new DefaultParts();")))
    }

    fun testHelperFieldEscapesIntoUntracedCallbacksAreRejected() {
        parts()
        uncertain(analyze(page("add(parts.label(\"title\"));", """
            private final DefaultParts parts = new DefaultParts();
            private java.util.function.Function<String, Label> later() { return parts::label; }
        """)))
    }

    fun testRecursiveInterfaceFactoriesRemainBounded() {
        api(); parts(body = "return this.label(id);")
        uncertain(analyze(page("Parts parts = new DefaultParts(); add(parts.label(\"title\"));")))
    }

    fun testHelperAllocationsShareTheExistingInstanceBudget() {
        parts()
        val allocations = (0..256).joinToString("\n") { "DefaultParts p$it = new DefaultParts();" }
        uncertain(analyze(page("$allocations add(new DefaultParts().label(\"title\"));")))
    }

    fun testDispatchDoesNotModifySourcesAndExportsDeterministically() {
        api(); val impl = parts()
        val java = page("Parts parts = new DefaultParts(); add(parts.label(\"title\"));")
        val before = listOf(java.text, impl.text)
        val first = analyze(java)
        val second = WicketPageAnalyzer().analyze(java.classes.single())!!
        assertEquals(first, second)
        assertEquals(WicketContextExporter().export(first), WicketContextExporter().export(second))
        assertEquals(before, listOf(java.text, impl.text))
        assertTrue(first.nodes.none { it.componentType == "example.DefaultParts" })
    }

    fun testDemoCombinesRootDispatchAndInterfaceFactories() {
        val root = java.nio.file.Path.of(System.getProperty("wicketlens.test.demo"))
        for (name in listOf("ComponentParts", "DispatchParts", "DispatchBasePage", "DispatchPage")) {
            myFixture.addFileToProject("src/main/java/example/$name.java", java.nio.file.Files.readString(root.resolve("main/java/example/$name.java")))
        }
        companion("DispatchPage", java.nio.file.Files.readString(root.resolve("main/resources/example/DispatchPage.html")))
        val java = com.intellij.psi.PsiManager.getInstance(project).findFile(myFixture.findFileInTempDir("src/main/java/example/DispatchPage.java")) as PsiJavaFile
        val report = WicketPageAnalyzer().analyze(java.classes.single())!!
        matched(report, 4)
        assertEquals(setOf("title", "dispatched", "form", "name"), report.children().map { it.wicketId }.toSet())
        assertEquals(4, report.javaSourceFiles.size)
    }

    private fun api() = source("Parts", "public interface Parts { Label label(String id); }")
    private fun parts(name: String = "DefaultParts", body: String = "return new Label(id);", members: String = "") =
        source(name, "public class $name ${if (myFixture.tempDirFixture.getFile("src/main/java/example/Parts.java") != null) "implements Parts" else ""} { public Label label(String id) { $body } $members }")
    private fun source(name: String, declaration: String) = myFixture.addFileToProject("src/main/java/example/$name.java", """
        package example; import org.apache.wicket.markup.html.basic.Label; import org.apache.wicket.markup.html.form.Form;
        $declaration
    """.trimIndent())
    private fun base(body: String, members: String) = source("BasePage", "public class BasePage extends org.apache.wicket.markup.html.WebPage { public BasePage() { $body } $members }")
    private fun page(body: String, members: String = "", final: Boolean = false, base: String = "org.apache.wicket.markup.html.WebPage", parameters: String = "") =
        source("ExamplePage", "public ${if (final) "final " else ""}class ExamplePage extends $base { public ExamplePage($parameters) { $body } $members }") as PsiJavaFile
    private fun analyze(java: PsiJavaFile, html: String = "<span wicket:id=\"title\"></span>"): PageReport {
        companion(html = html)
        return WicketPageAnalyzer().analyze(java.classes.single())!!
    }
    private fun matched(report: PageReport, count: Int) {
        assertEquals(report.matching!!.findings.toString(), count, report.matching.findings.count { it.code == DiagnosticCode.MATCHED })
        assertTrue(report.warnings.toString(), report.warnings.none { it.code == DiagnosticCode.UNRESOLVED_SCOPE })
    }
    private fun uncertain(report: PageReport) {
        assertTrue(report.matching!!.findings.toString(), report.matching.findings.none { it.code in setOf(DiagnosticCode.MATCHED, DiagnosticCode.JAVA_ONLY, DiagnosticCode.MARKUP_ONLY) })
    }
}
