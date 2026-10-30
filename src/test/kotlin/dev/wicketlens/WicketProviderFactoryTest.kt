package dev.wicketlens

import com.intellij.psi.PsiJavaFile
import dev.wicketlens.analysis.WicketPageAnalyzer
import dev.wicketlens.export.WicketContextExporter
import dev.wicketlens.model.*

class WicketProviderFactoryTest : WicketProjectPsiTestCase() {
    fun testStaticSourceFactoryReturnsAKnownConfiguredProvider() {
        configured(); factory()
        val java = page("Parts parts = Factory.create(\"title\"); add(parts.build());")
        val report = analyze(java)
        matched(report, 1)
        assertEquals(java.text.indexOf("\"title\""), report.children().single().idLocation!!.offset)
        assertTrue(report.children().single().idRenameSafe)
        assertEquals(4, report.javaSourceFiles.size)
    }

    fun testAnInlineFactoryResultCanImmediatelySupplyAComponent() {
        configured(); factory()
        matched(analyze(page("add(Factory.create(\"title\").build());")), 1)
    }

    fun testFactoryInvocationsKeepSeparateAllocationState() {
        configured(); factory()
        val report = analyze(page("Parts first = Factory.create(\"first\"); Parts second = Factory.create(\"second\"); add(first.build(), second.build());"),
            "<div><span wicket:id=\"first\"></span><span wicket:id=\"second\"></span></div>")
        matched(report, 2)
        assertEquals(setOf("first", "second"), report.children().map { it.wicketId }.toSet())
        assertEquals(2, report.children().map { it.idLocation }.distinct().size)
    }

    fun testAPrivateFinalProviderFieldCanUseAFactoryInitializer() {
        configured(); factory()
        val report = analyze(page("add(parts.build());", "private final Parts parts = Factory.create(\"title\");"))
        matched(report, 1)
        assertTrue(report.children().single().idRenameSafe)
    }

    fun testAConstructorAssignedProviderFieldCanUseAFactory() {
        configured(); factory()
        matched(analyze(page("parts = Factory.create(\"title\"); add(parts.build());", "private final Parts parts;")), 1)
    }

    fun testFactoryFieldsCanSupplyProvidersInInitialization() {
        configured(); factory()
        matched(analyze(page("", """
            private final Parts parts = Factory.create("title");
            @Override protected void onInitialize() { super.onInitialize(); add(parts.build()); }
        """)), 1)
    }

    fun testPrivatePageFactoriesCanReturnProviders() {
        configured()
        matched(analyze(page("add(parts(\"title\").build());", "private Parts parts(String id) { return new ConfiguredParts(id); }")), 1)
    }

    fun testKnownBuilderInstancesCanReturnProviders() {
        configured()
        source("Builder", "public class Builder { public Parts create(String id) { return new ConfiguredParts(id); } }")
        matched(analyze(page("Builder builder = new Builder(); add(builder.create(\"title\").build());")), 1)
    }

    fun testSourceInterfaceDefaultsCanProduceFreshProviders() {
        configured()
        source("Builder", "public interface Builder { default Parts create(String id) { return new ConfiguredParts(id); } }")
        source("ConcreteBuilder", "public class ConcreteBuilder implements Builder {}")
        matched(analyze(page("Builder builder = new ConcreteBuilder(); add(builder.create(\"title\").build());")), 1)
    }

    fun testProviderFactoriesCanCallNestedFreshFactories() {
        configured()
        factory("return nested(id);", "private static Parts nested(String id) { return new ConfiguredParts(id); }")
        matched(analyze(page("add(Factory.create(\"title\").build());")), 1)
    }

    fun testAStableLocalAliasDoesNotLoseFreshFactoryProvenance() {
        configured()
        factory("Parts first = new ConfiguredParts(id); Parts alias = first; return alias;")
        matched(analyze(page("add(Factory.create(\"title\").build());")), 1)
    }

    fun testFactoriesCanComposeKnownConstructorInjectedProviders() {
        configured()
        source("Decorator", """
            public class Decorator implements Parts {
                private final Parts delegate;
                public Decorator(Parts delegate) { this.delegate = delegate; }
                public Label build() { return delegate.build(); }
            }
        """)
        factory("return new Decorator(new ConfiguredParts(id));")
        matched(analyze(page("add(Factory.create(\"title\").build());")), 1)
    }

    fun testProviderFieldInitializersCanUseSourceFactories() {
        configured(); factory()
        source("Decorator", """
            public class Decorator implements Parts {
                private final Parts delegate = Factory.create("title");
                public Label build() { return delegate.build(); }
            }
        """)
        val report = analyze(page("add(new Decorator().build());"))
        matched(report, 1)
        assertFalse(report.children().single().idRenameSafe)
    }

    fun testGenericFactoryReturnTypesPreserveConfiguration() {
        genericParts()
        source("Factory", "public class Factory { public static <T extends java.io.Serializable> Parts<T> create(String id, T value) { return new ConfiguredParts<T>(id, value); } }")
        matched(analyze(page("Parts<String> parts = Factory.create(\"title\", \"Caption\"); add(parts.build());")), 1)
    }

    fun testNestedGenericFactoriesRefineTheAllocationTypeAcrossCalls() {
        genericParts()
        source("Factory", """
            public class Factory {
                public static <T extends java.io.Serializable> Parts<T> create(String id, T value) { return nested(id, value); }
                private static <U extends java.io.Serializable> Parts<U> nested(String id, U value) { return new ConfiguredParts<U>(id, value); }
            }
        """)
        matched(analyze(page("add(Factory.create(\"title\", \"Caption\").build());")), 1)
    }

    fun testUnknownStringIdsRemainDynamicAfterAProviderFactory() {
        configured(); factory()
        val report = analyze(page("add(Factory.create(id).build());", parameters = "String id"))
        assertTrue(report.matching!!.findings.any { it.code == DiagnosticCode.UNRESOLVED_DYNAMIC_ID })
        uncertain(report)
    }

    fun testReturningACachedProviderUsesTheReadOnlyAccessorContract() {
        configured()
        source("Builder", """
            public class Builder {
                private final Parts cached = new ConfiguredParts("title");
                public Parts create() { return cached; }
            }
        """)
        matched(analyze(page("add(new Builder().create().build());")), 1)
    }

    fun testReturningAKnownProviderParameterUsesTheReadOnlyAccessorContract() {
        configured()
        source("Factory", "public class Factory { public static Parts identity(Parts supplied) { return supplied; } }")
        matched(analyze(page("add(Factory.identity(new ConfiguredParts(\"title\")).build());")), 1)
    }

    fun testAnUnknownInjectedProviderCannotBePromotedByAnIdentityFactory() {
        configured()
        source("Factory", "public class Factory { public static Parts identity(Parts supplied) { return supplied; } }")
        uncertain(analyze(page("add(Factory.identity(injected).build());", parameters = "Parts injected")))
    }

    fun testNullProviderResultsRemainUnresolved() {
        configured(); factory("return null;")
        uncertain(analyze(page("add(Factory.create(\"title\").build());")))
    }

    fun testBranchingFactoriesDoNotChooseOneImplementation() {
        configured(); factory("if (System.nanoTime() > 0) return new ConfiguredParts(id); return new ConfiguredParts(\"other\");")
        uncertain(analyze(page("add(Factory.create(\"title\").build());")))
    }

    fun testRecursiveFactoriesRemainBounded() {
        configured(); factory("return create(id);")
        uncertain(analyze(page("add(Factory.create(\"title\").build());")))
    }

    fun testStaticInitializationCannotHideBehindAProviderFactory() {
        configured(); factory(extra = "static { System.setProperty(\"effect\", \"yes\"); }")
        uncertain(analyze(page("add(Factory.create(\"title\").build());")))
    }

    fun testAProviderFactoryCannotIgnoreAnEffectfulStaticQualifier() {
        configured(); factory()
        uncertain(analyze(page("add(qualifier().create(\"title\").build());", "private Factory qualifier() { removeAll(); return null; }")))
    }

    fun testFactoryResultsStillRejectMutableProviderState() {
        configured(field = "private String id;"); factory()
        uncertain(analyze(page("add(Factory.create(\"title\").build());")))
    }

    fun testIncompatibleDeclaredProviderReturnTypesAreNotInferredByName() {
        configured()
        source("Other", "public interface Other { Label build(); }")
        source("Factory", "public class Factory { public static Other create(String id) { return new ConfiguredParts(id); } }")
        uncertain(analyze(page("add(Factory.create(\"title\").build());")))
    }

    fun testIncompatibleGenericProviderReturnsStayUnresolved() {
        genericParts()
        source("Factory", "public class Factory { public static Parts<Integer> create(String id) { return new ConfiguredParts<String>(id, \"Caption\"); } }")
        uncertain(analyze(page("add(Factory.create(\"title\").build());")))
    }

    fun testProviderFactoryAnalysisIsReadOnlyAndDeterministic() {
        val provider = configured(); val producer = factory()
        val java = page("add(Factory.create(\"title\").build());")
        val before = listOf(provider.text, producer.text, java.text)
        val first = analyze(java)
        val second = WicketPageAnalyzer().analyze(java.classes.single())!!
        assertEquals(first, second)
        assertEquals(WicketContextExporter().export(first), WicketContextExporter().export(second))
        assertEquals(before, listOf(provider.text, producer.text, java.text))
        assertTrue(first.nodes.none { it.componentType in setOf("example.Factory", "example.ConfiguredParts") })
    }

    fun testDemoCombinesStaticNestedAndInstanceProviderFactories() {
        val root = java.nio.file.Path.of(System.getProperty("wicketlens.test.demo"))
        for (name in listOf("ComponentParts", "DispatchParts", "ConfiguredParts", "ProviderFactories", "ProviderFactoryPage")) {
            myFixture.addFileToProject("src/main/java/example/$name.java", java.nio.file.Files.readString(root.resolve("main/java/example/$name.java")))
        }
        companion("ProviderFactoryPage", java.nio.file.Files.readString(root.resolve("main/resources/example/ProviderFactoryPage.html")))
        val java = com.intellij.psi.PsiManager.getInstance(project).findFile(myFixture.findFileInTempDir("src/main/java/example/ProviderFactoryPage.java")) as PsiJavaFile
        val report = WicketPageAnalyzer().analyze(java.classes.single())!!
        matched(report, 5)
        assertEquals(setOf("title", "subtitle", "form", "name", "footer"), report.children().map { it.wicketId }.toSet())
        assertEquals(5, report.javaSourceFiles.size)
    }

    private fun configured(field: String = "private final String id;"): com.intellij.psi.PsiFile {
        source("Parts", "public interface Parts { Label build(); }")
        return source("ConfiguredParts", "public class ConfiguredParts implements Parts { $field public ConfiguredParts(String id) { this.id = id; } public Label build() { return new Label(id); } }")
    }
    private fun genericParts() {
        source("Parts", "public interface Parts<T extends java.io.Serializable> { Label build(); }")
        source("ConfiguredParts", """
            public class ConfiguredParts<T extends java.io.Serializable> implements Parts<T> {
                private final String id; private final T value;
                public ConfiguredParts(String id, T value) { this.id = id; this.value = value; }
                public Label build() { return new Label(id, value); }
            }
        """)
    }
    private fun factory(body: String = "return new ConfiguredParts(id);", extra: String = "") = source("Factory", "public class Factory { public static Parts create(String id) { $body } $extra }")
    private fun source(name: String, declaration: String) = myFixture.addFileToProject("src/main/java/example/$name.java", """
        package example; import org.apache.wicket.markup.html.basic.Label; $declaration
    """.trimIndent())
    private fun page(body: String, members: String = "", parameters: String = "") = source("ExamplePage", "public class ExamplePage extends org.apache.wicket.markup.html.WebPage { public ExamplePage($parameters) { $body } $members }") as PsiJavaFile
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
