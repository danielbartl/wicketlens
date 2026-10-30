package dev.wicketlens

import com.intellij.psi.PsiJavaFile
import dev.wicketlens.analysis.WicketPageAnalyzer
import dev.wicketlens.export.WicketContextExporter
import dev.wicketlens.model.*

class WicketInterfaceDefaultsTest : WicketProjectPsiTestCase() {
    fun testDefaultFactoryUsesTheKnownReceiverAndRecordsItsSource() {
        val api = parts()
        implementation()
        val report = analyze(page("Parts parts = new DefaultParts(); add(parts.label(\"title\"));"))
        matched(report, 1)
        assertEquals(api.virtualFile.path, report.children().single().javaLocation.file)
        assertTrue(api.virtualFile.path in report.javaSourceFiles)
        assertTrue(report.children().single().idRenameSafe)
    }

    fun testAChildInterfaceCanInheritItsParentsDefault() {
        parts(); source("ChildParts", "public interface ChildParts extends Parts {}")
        implementation("implements ChildParts")
        matched(analyze(page("ChildParts parts = new DefaultParts(); add(parts.label(\"title\"));")), 1)
    }

    fun testAMoreSpecificDefaultWinsEvenWhenTheVariableUsesTheParentInterface() {
        parts("return new Label(\"wrong\");")
        source("ChildParts", "public interface ChildParts extends Parts { @Override default Label label(String id) { return new Label(id); } }")
        implementation("implements ChildParts")
        val report = analyze(page("Parts parts = new DefaultParts(); add(parts.label(\"title\"));"))
        matched(report, 1)
        assertEquals("title", report.children().single().wicketId)
    }

    fun testADiamondSharesOneInheritedDefault() {
        parts()
        source("Left", "public interface Left extends Parts {}")
        source("Right", "public interface Right extends Parts {}")
        implementation("implements Left, Right")
        matched(analyze(page("Parts parts = new DefaultParts(); add(parts.label(\"title\"));")), 1)
    }

    fun testAClassImplementationWinsOverADefault() {
        parts("return new Label(\"wrong\");")
        implementation(members = "@Override public Label label(String id) { return new Label(id); }")
        matched(analyze(page("Parts parts = new DefaultParts(); add(parts.label(\"title\"));")), 1)
    }

    fun testAnInheritedClassMethodWinsWithoutDeclaringTheInterfaceOnItsOwner() {
        parts("return new Label(\"wrong\");")
        source("BaseParts", "public class BaseParts { public Label label(String id) { return new Label(id); } }")
        implementation("extends BaseParts implements Parts")
        val report = analyze(page("Parts parts = new DefaultParts(); add(parts.label(\"title\"));"))
        matched(report, 1)
        assertTrue(report.children().single().javaLocation.file.endsWith("/BaseParts.java"))
    }

    fun testInheritedDefaultsOnASourceSuperclassAreVisible() {
        parts()
        source("BaseParts", "public class BaseParts implements Parts {}")
        implementation("extends BaseParts")
        matched(analyze(page("Parts parts = new DefaultParts(); add(parts.label(\"title\"));")), 1)
    }

    fun testPrivateSuperclassMethodsDoNotHideAnInterfaceDefault() {
        parts()
        source("BaseParts", "public class BaseParts { private Label label(String id) { return new Label(\"wrong\"); } }")
        implementation("extends BaseParts implements Parts")
        matched(analyze(page("Parts parts = new DefaultParts(); add(parts.label(\"title\"));")), 1)
    }

    fun testAnOverloadDoesNotReplaceTheDefaultSignature() {
        parts()
        implementation(members = "public Label label(Object ignored) { return new Label(\"wrong\"); }")
        matched(analyze(page("Parts parts = new DefaultParts(); add(parts.label(\"title\"));")), 1)
    }

    fun testAPrivateInterfaceHelperKeepsTheReceiver() {
        parts("return build(id);", "private Label build(String id) { return new Label(id); }")
        implementation()
        matched(analyze(page("add(new DefaultParts().label(\"title\"));")), 1)
    }

    fun testAPrivateStaticInterfaceHelperHasAuditedInitialization() {
        parts("return build(id);", "private static Label build(String id) { return new Label(id); }")
        implementation()
        matched(analyze(page("add(new DefaultParts().label(\"title\"));")), 1)
    }

    fun testADirectStaticSourceInterfaceFactoryIsAlsoAudited() {
        source("Parts", "public interface Parts { static Label label(String id) { return new Label(id); } }")
        matched(analyze(page("add(Parts.label(\"title\"));")), 1)
    }

    fun testADefaultCanDispatchToAnAbstractMethodImplementedByAConfiguredProvider() {
        source("Parts", "public interface Parts { default Label label(String ignored) { return build(); } Label build(); }")
        implementation(members = """
            private final String id;
            public DefaultParts(String id) { this.id = id; }
            @Override public Label build() { return new Label(id); }
        """)
        matched(analyze(page("Parts parts = new DefaultParts(\"title\"); add(parts.label(\"unused\"));")), 1)
    }

    fun testAVoidDefaultCanPopulateAComponentParameter() {
        source("Parts", "public interface Parts { default void populate(Form<?> form) { form.add(new Label(\"title\")); } }")
        implementation()
        matched(analyze(page("Form<?> form = new Form<>(\"form\"); new DefaultParts().populate(form); add(form);"),
            "<form wicket:id=\"form\"><span wicket:id=\"title\"></span></form>"), 2)
    }

    fun testExplicitInterfaceSuperSelectsTheChosenDefaultBody() {
        parts()
        implementation(members = "@Override public Label label(String id) { return Parts.super.label(id); }")
        matched(analyze(page("add(new DefaultParts().label(\"title\"));")), 1)
    }

    fun testAChildDefaultCanExplicitlyCallItsParentDefault() {
        parts()
        source("ChildParts", "public interface ChildParts extends Parts { @Override default Label label(String id) { return Parts.super.label(id); } }")
        implementation("implements ChildParts")
        matched(analyze(page("add(new DefaultParts().label(\"title\"));")), 1)
    }

    fun testAFinalPageCanUseItsInheritedInterfaceDefault() {
        parts()
        matched(analyze(page("add(label(\"title\"));", final = true, interfaces = "implements Parts")), 1)
    }

    fun testExplicitInterfaceSuperClosesATargetOnANonfinalPage() {
        parts()
        matched(analyze(page("add(Parts.super.label(\"title\"));", interfaces = "implements Parts")), 1)
    }

    fun testNonfinalPageDefaultCallsRemainOpenDispatch() {
        parts()
        uncertain(analyze(page("add(label(\"title\"));", interfaces = "implements Parts")))
    }

    fun testAnUnknownInjectedReceiverDoesNotAcquireTheDefaultImplementation() {
        parts()
        uncertain(analyze(page("add(parts.label(\"title\"));", parameters = "Parts parts")))
    }

    fun testConflictingUnrelatedDefaultsRemainUnresolved() {
        parts()
        source("Other", "public interface Other { default Label label(String id) { return new Label(\"wrong\"); } }")
        implementation("implements Parts, Other")
        uncertain(analyze(page("Parts parts = new DefaultParts(); add(parts.label(\"title\"));")))
    }

    fun testAbstractInterfaceRedeclarationsSuppressTheParentDefault() {
        parts()
        source("ChildParts", "public interface ChildParts extends Parts { @Override Label label(String id); }")
        implementation("implements ChildParts")
        uncertain(analyze(page("Parts parts = new DefaultParts(); add(parts.label(\"title\"));")))
    }

    fun testAnAbstractClassMethodDoesNotFallThroughToADefault() {
        parts()
        source("BaseParts", "public abstract class BaseParts { public abstract Label label(String id); }")
        implementation("extends BaseParts implements Parts")
        uncertain(analyze(page("Parts parts = new DefaultParts(); add(parts.label(\"title\"));")))
    }

    fun testInterfaceInitializationIsNotIgnoredWhenAClassOverridesTheDefault() {
        parts(extra = "String STATE = System.getProperty(\"state\");")
        implementation(members = "@Override public Label label(String id) { return new Label(id); }")
        uncertain(analyze(page("Parts parts = new DefaultParts(); add(parts.label(\"title\"));")))
    }

    fun testFinalPageDefaultInterfaceInitializationIsAudited() {
        parts(extra = "String STATE = System.getProperty(\"state\");")
        uncertain(analyze(page("add(label(\"title\"));", "@Override public Label label(String id) { return new Label(id); }", final = true, interfaces = "implements Parts")))
    }

    fun testClassStaticFactoriesAuditTheirDefaultInterfaces() {
        parts(extra = "String STATE = System.getProperty(\"state\");")
        implementation(members = "public static Label create(String id) { return new Label(id); }")
        uncertain(analyze(page("add(DefaultParts.create(\"title\"));")))
    }

    fun testBranchesInDefaultsDoNotProduceAbsenceClaims() {
        parts("if (System.nanoTime() > 0) return new Label(id); return new Label(\"other\");")
        implementation()
        uncertain(analyze(page("add(new DefaultParts().label(\"title\"));")))
    }

    fun testRecursiveDefaultCallsAreBounded() {
        parts("return label(id);")
        implementation()
        uncertain(analyze(page("add(new DefaultParts().label(\"title\"));")))
    }

    fun testGenericDefaultParametersKeepTheirResolvedValues() {
        source("Parts", "public interface Parts<T extends java.io.Serializable> { default Label label(String id, T value) { return new Label(id, value); } }")
        implementation("implements Parts<String>")
        matched(analyze(page("Parts<String> parts = new DefaultParts(); add(parts.label(\"title\", \"Caption\"));")), 1)
    }

    fun testGenericInheritedClassImplementationWinsOverTheDefault() {
        source("Parts", "public interface Parts<T extends java.io.Serializable> { default Label label(T value) { return new Label(\"wrong\", value); } }")
        source("BaseParts", "public class BaseParts<T extends java.io.Serializable> { public Label label(T value) { return new Label(\"title\", value); } }")
        implementation("extends BaseParts<String> implements Parts<String>")
        matched(analyze(page("Parts<String> parts = new DefaultParts(); add(parts.label(\"Caption\"));")), 1)
    }

    fun testUnavailableBinaryDefaultContractsStayUnresolved() {
        implementation("implements java.util.function.Function<String, Label>", "public Label apply(String id) { return new Label(id); }")
        uncertain(analyze(page("add(new DefaultParts().apply(\"title\"));")))
    }

    fun testDefaultExpansionIsReadOnlyAndDeterministic() {
        val api = parts(); val impl = implementation()
        val java = page("add(new DefaultParts().label(\"title\"));")
        val before = listOf(api.text, impl.text, java.text)
        val first = analyze(java)
        val second = WicketPageAnalyzer().analyze(java.classes.single())!!
        assertEquals(first, second)
        assertEquals(WicketContextExporter().export(first), WicketContextExporter().export(second))
        assertEquals(before, listOf(api.text, impl.text, java.text))
    }

    fun testDemoCombinesProviderPageAndExplicitDefaults() {
        val root = java.nio.file.Path.of(System.getProperty("wicketlens.test.demo"))
        for (name in listOf("ComponentParts", "DefaultComponentParts", "DefaultMethodParts", "DefaultMethodsPage")) {
            myFixture.addFileToProject("src/main/java/example/$name.java", java.nio.file.Files.readString(root.resolve("main/java/example/$name.java")))
        }
        companion("DefaultMethodsPage", java.nio.file.Files.readString(root.resolve("main/resources/example/DefaultMethodsPage.html")))
        val java = com.intellij.psi.PsiManager.getInstance(project).findFile(myFixture.findFileInTempDir("src/main/java/example/DefaultMethodsPage.java")) as PsiJavaFile
        val report = WicketPageAnalyzer().analyze(java.classes.single())!!
        matched(report, 6)
        assertEquals(4, report.javaSourceFiles.size)
        assertEquals(3, report.children().count { it.wicketId == "name" })
    }

    private fun parts(body: String = "return new Label(id);", extra: String = "") = source("Parts", "public interface Parts { default Label label(String id) { $body } $extra }")
    private fun implementation(parents: String = "implements Parts", members: String = "") = source("DefaultParts", "public class DefaultParts $parents { $members }")
    private fun source(name: String, declaration: String) = myFixture.addFileToProject("src/main/java/example/$name.java", """
        package example; import org.apache.wicket.markup.html.basic.Label; import org.apache.wicket.markup.html.form.Form;
        $declaration
    """.trimIndent())
    private fun page(body: String, members: String = "", final: Boolean = false, interfaces: String = "", parameters: String = "") =
        source("ExamplePage", "public ${if (final) "final " else ""}class ExamplePage extends org.apache.wicket.markup.html.WebPage $interfaces { public ExamplePage($parameters) { $body } $members }") as PsiJavaFile
    private fun analyze(java: PsiJavaFile, html: String = "<span wicket:id=\"title\"></span>"): PageReport {
        companion(html = html)
        return WicketPageAnalyzer().analyze(java.classes.single())!!
    }
    private fun matched(report: PageReport, count: Int) {
        assertEquals(report.matching!!.findings.toString(), count, report.matching.findings.count { it.code == DiagnosticCode.MATCHED })
        assertTrue(report.warnings.toString(), report.warnings.none { it.code == DiagnosticCode.UNRESOLVED_SCOPE })
    }
    private fun uncertain(report: PageReport) = assertTrue(report.matching!!.findings.toString(),
        report.matching.findings.none { it.code in setOf(DiagnosticCode.MATCHED, DiagnosticCode.JAVA_ONLY, DiagnosticCode.MARKUP_ONLY) })
}
