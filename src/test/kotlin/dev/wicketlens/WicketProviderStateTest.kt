package dev.wicketlens

import com.intellij.psi.PsiJavaFile
import dev.wicketlens.analysis.WicketPageAnalyzer
import dev.wicketlens.export.WicketContextExporter
import dev.wicketlens.model.*

class WicketProviderStateTest : WicketProjectPsiTestCase() {
    fun testConstructorConfigurationSuppliesTheActualIdAndItsLiteralOrigin() {
        configured()
        val java = page("Parts parts = new ConfiguredParts(\"title\"); add(parts.build());")
        val report = analyze(java)
        matched(report, 1)
        assertEquals("title", report.children().single().wicketId)
        assertEquals(java.text.indexOf("\"title\""), report.children().single().idLocation!!.offset)
        assertTrue(report.children().single().idRenameSafe)
    }

    fun testTwoAllocationsOfOneProviderClassKeepSeparateState() {
        configured()
        val report = analyze(page("""
            Parts first = new ConfiguredParts("first"); Parts second = new ConfiguredParts("second");
            add(first.build(), second.build());
        """), "<div><span wicket:id=\"first\"></span><span wicket:id=\"second\"></span></div>")
        matched(report, 2)
        assertEquals(setOf("first", "second"), report.children().map { it.wicketId }.toSet())
        assertEquals(1, report.children().map { it.javaLocation }.distinct().size)
        assertEquals(2, report.children().map { it.idLocation }.distinct().size)
    }

    fun testKnownConstructorInjectedProviderCanSupplyAComponent() {
        configured(); decorator()
        matched(analyze(page("Parts parts = new Decorator(new ConfiguredParts(\"title\")); add(parts.build());")), 1)
    }

    fun testPrivateFinalRootProviderFieldsKeepConfigurationAcrossInitialization() {
        configured()
        val report = analyze(page("", """
            private final Parts parts = new ConfiguredParts("title");
            @Override protected void onInitialize() { super.onInitialize(); add(parts.build()); }
        """))
        matched(report, 1)
        assertTrue(report.children().single().idRenameSafe)
    }

    fun testAMutablePageFieldCannotBecomeStableConfigurationThroughALocalAlias() {
        configured()
        uncertain(analyze(page("String copy = id; add(new ConfiguredParts(copy).build());", "private String id = \"title\";")))
    }

    fun testAPrivateFinalScalarCanSupplyConfigurationWithSharedOriginGuards() {
        configured()
        val report = analyze(page("add(new ConfiguredParts(id).build());", "private final String id = \"title\";"))
        matched(report, 1)
        assertFalse(report.children().single().idRenameSafe)
    }

    fun testConstructorAssignedRootProviderFieldPreservesItsIdOrigin() {
        configured()
        val report = analyze(page("parts = new ConfiguredParts(\"title\"); add(parts.build());", "private final Parts parts;"))
        matched(report, 1)
        assertTrue(report.children().single().idRenameSafe)
    }

    fun testLiteralFieldInitializersDoNotNeedAnExplicitConstructor() {
        api()
        source("ConfiguredParts", """
            public class ConfiguredParts implements Parts {
                private final String id = "title";
                public Label build() { return new Label(this.id); }
            }
        """)
        val report = analyze(page("add(new ConfiguredParts().build());"))
        matched(report, 1)
        assertFalse(report.children().single().idRenameSafe)
    }

    fun testThisConstructorDelegationInitializesOneEnvironment() {
        configured(extra = "public ConfiguredParts() { this(\"title\"); }")
        val report = analyze(page("add(new ConfiguredParts().build());"))
        matched(report, 1)
        assertFalse(report.children().single().idRenameSafe)
    }

    fun testSuperclassConstructorCanOwnTheConfiguredField() {
        api()
        source("BaseParts", """
            public class BaseParts implements Parts {
                private final String id;
                public BaseParts(String id) { this.id = id; }
                public Label build() { return new Label(id); }
            }
        """)
        source("ConfiguredParts", "public class ConfiguredParts extends BaseParts { public ConfiguredParts(String id) { super(id); } }")
        val report = analyze(page("add(new ConfiguredParts(\"title\").build());"))
        matched(report, 1)
        assertEquals(4, report.javaSourceFiles.size)
    }

    fun testShadowedPrivateFieldsAreResolvedByDeclarationRatherThanName() {
        api()
        source("BaseParts", """
            public class BaseParts implements Parts {
                private final String id;
                public BaseParts(String id) { this.id = id; }
                public Label build() { return new Label(id); }
            }
        """)
        source("ConfiguredParts", """
            public class ConfiguredParts extends BaseParts {
                private final String id = "wrong";
                public ConfiguredParts(String id) { super(id); }
            }
        """)
        val report = analyze(page("add(new ConfiguredParts(\"title\").build());"))
        matched(report, 1)
        assertEquals("title", report.children().single().wicketId)
    }

    fun testEarlierInitializerAndAssignmentValuesCanBeForwardedToOtherFields() {
        api()
        source("ConfiguredParts", """
            public class ConfiguredParts implements Parts {
                private final String first = "title";
                private final String second = first;
                private final String id;
                public ConfiguredParts() { this.id = second; }
                public Label build() { return new Label(id); }
            }
        """)
        matched(analyze(page("add(new ConfiguredParts().build());")), 1)
    }

    fun testAProviderFieldInitializerCanConstructAnotherKnownProvider() {
        configured()
        source("Decorator", """
            public class Decorator implements Parts {
                private final Parts delegate = new ConfiguredParts("title");
                public Label build() { return delegate.build(); }
            }
        """)
        matched(analyze(page("add(new Decorator().build());")), 1)
    }

    fun testForwardedConstructorParametersDoNotNeedIdenticalNamesOrOrder() {
        api()
        source("ConfiguredParts", """
            public class ConfiguredParts implements Parts {
                private final String id;
                private final String caption;
                public ConfiguredParts(String text, String componentId) { this.caption = text; this.id = componentId; }
                public Label build() { return new Label(id, caption); }
            }
        """)
        val java = page("add(new ConfiguredParts(\"Heading\", \"title\").build());")
        val report = analyze(java)
        matched(report, 1)
        assertEquals(java.text.indexOf("\"title\""), report.children().single().idLocation!!.offset)
    }

    fun testUnknownStringConfigurationRetainsDynamicIdUncertainty() {
        configured()
        val report = analyze(page("add(new ConfiguredParts(id).build());", parameters = "String id"))
        assertTrue(report.matching!!.findings.any { it.code == DiagnosticCode.UNRESOLVED_DYNAMIC_ID })
        uncertain(report)
    }

    fun testAnUnknownInjectedProviderDoesNotBecomeAKnownImplementation() {
        api(); decorator()
        uncertain(analyze(page("add(new Decorator(injected).build());", parameters = "Parts injected")))
    }

    fun testMutableProviderStateIsNotTreatedAsConfiguration() {
        configured(field = "private String id;")
        uncertain(analyze(page("add(new ConfiguredParts(\"title\").build());")))
    }

    fun testNonprivateFinalProviderStateStaysOutsideTheContract() {
        configured(field = "public final String id;")
        uncertain(analyze(page("add(new ConfiguredParts(\"title\").build());")))
    }

    fun testCachedComponentsAreNotAcceptedAsProviderState() {
        api()
        source("ConfiguredParts", """
            public class ConfiguredParts implements Parts {
                private final Label cached = new Label("title");
                public Label build() { return cached; }
            }
        """)
        uncertain(analyze(page("add(new ConfiguredParts().build());")))
    }

    fun testComponentArgumentsCannotEscapeThroughProviderConstruction() {
        api()
        source("ConfiguredParts", """
            public class ConfiguredParts implements Parts {
                private final Object cached;
                public ConfiguredParts(Object cached) { this.cached = cached; }
                public Label build() { return new Label("title"); }
            }
        """)
        uncertain(analyze(page("add(new ConfiguredParts(new Label(\"other\")).build());")))
    }

    fun testThePageCannotEscapeIntoAProviderConstructor() {
        api()
        source("ConfiguredParts", """
            public class ConfiguredParts implements Parts {
                private final Object page;
                public ConfiguredParts(Object page) { this.page = page; }
                public Label build() { return new Label("title"); }
            }
        """)
        uncertain(analyze(page("add(new ConfiguredParts(this).build());")))
    }

    fun testConstructorMethodCallsAndBranchesDoNotBecomePureAssignments() {
        configured(body = "this.id = id; System.setProperty(\"effect\", id);")
        uncertain(analyze(page("add(new ConfiguredParts(\"title\").build());")))
    }

    fun testConditionalConstructorAssignmentsRemainUnresolved() {
        configured(body = "if (System.nanoTime() > 0) this.id = id; else this.id = \"other\";")
        uncertain(analyze(page("add(new ConfiguredParts(\"title\").build());")))
    }

    fun testFieldInitializersCannotRunAnUnknownConfigurationFactory() {
        api()
        source("ConfiguredParts", """
            public class ConfiguredParts implements Parts {
                private final String id = System.getProperty("id");
                public Label build() { return new Label(id); }
            }
        """)
        uncertain(analyze(page("add(new ConfiguredParts().build());")))
    }

    fun testAFieldReadBeforeItsInitializerDoesNotInventADefaultValue() {
        api()
        source("ConfiguredParts", """
            public class ConfiguredParts implements Parts {
                private final String id = this.later;
                private final String later = "title";
                public Label build() { return new Label(id); }
            }
        """)
        uncertain(analyze(page("add(new ConfiguredParts().build());")))
    }

    fun testRepeatedAssignmentsDoNotPublishPartialProviderState() {
        configured(body = "this.id = id; this.id = \"other\";")
        uncertain(analyze(page("add(new ConfiguredParts(\"title\").build());")))
    }

    fun testMutatingAConfiguredProviderAfterConstructionIsNotSupported() {
        configured(extra = "public void reset() { System.setProperty(\"effect\", \"yes\"); }")
        uncertain(analyze(page("ConfiguredParts parts = new ConfiguredParts(\"title\"); parts.reset(); add(parts.build());")))
    }

    fun testMissingFinalFieldAssignmentDoesNotPublishPartialProviderState() {
        configured(body = "")
        uncertain(analyze(page("add(new ConfiguredParts(\"title\").build());")))
    }

    fun testRecursiveImplicitProviderConstructionIsBounded() {
        api()
        source("ConfiguredParts", """
            public class ConfiguredParts implements Parts {
                private final Parts delegate = new ConfiguredParts();
                public Label build() { return delegate.build(); }
            }
        """)
        uncertain(analyze(page("add(new ConfiguredParts().build());")))
    }

    fun testQualifiedSuperclassConstructionIsNotTreatedAsBareDelegation() {
        api()
        source("Outer", """
            public class Outer {
                public class Base implements Parts { public Label build() { return new Label("title"); } }
            }
        """)
        source("ConfiguredParts", "public class ConfiguredParts extends Outer.Base { public ConfiguredParts(Outer outer) { outer.super(); } }")
        uncertain(analyze(page("add(new ConfiguredParts(new Outer()).build());")))
    }

    fun testUnfinishedThisCannotBeWrappedAndForwardedThroughConstructorDelegation() {
        api(); decorator()
        source("BaseParts", "public class BaseParts implements Parts { public BaseParts(Parts other) {} public Label build() { return new Label(\"title\"); } }")
        source("ConfiguredParts", "public class ConfiguredParts extends BaseParts { public ConfiguredParts() { super(new Decorator(this)); } }")
        uncertain(analyze(page("add(new ConfiguredParts().build());")))
    }

    fun testStatePreservesReadOnlyAnalysisAndDeterministicExports() {
        val impl = configured()
        val java = page("add(new ConfiguredParts(\"title\").build());")
        val before = listOf(impl.text, java.text)
        val first = analyze(java)
        val second = WicketPageAnalyzer().analyze(java.classes.single())!!
        assertEquals(first, second)
        assertEquals(WicketContextExporter().export(first), WicketContextExporter().export(second))
        assertEquals(before, listOf(impl.text, java.text))
        assertTrue(first.nodes.none { it.componentType == "example.ConfiguredParts" })
    }

    fun testDemoCombinesIndependentConfigurationAndConstructorInjection() {
        val root = java.nio.file.Path.of(System.getProperty("wicketlens.test.demo"))
        for (name in listOf("ComponentParts", "DispatchParts", "ConfiguredParts", "ConfiguredPartsPage")) {
            myFixture.addFileToProject("src/main/java/example/$name.java", java.nio.file.Files.readString(root.resolve("main/java/example/$name.java")))
        }
        companion("ConfiguredPartsPage", java.nio.file.Files.readString(root.resolve("main/resources/example/ConfiguredPartsPage.html")))
        val java = com.intellij.psi.PsiManager.getInstance(project).findFile(myFixture.findFileInTempDir("src/main/java/example/ConfiguredPartsPage.java")) as PsiJavaFile
        val report = WicketPageAnalyzer().analyze(java.classes.single())!!
        matched(report, 4)
        assertEquals(setOf("title", "subtitle", "form", "name"), report.children().map { it.wicketId }.toSet())
        assertEquals(4, report.javaSourceFiles.size)
        assertTrue(report.children().filter { it.wicketId in setOf("title", "subtitle") }.all { it.idRenameSafe })
    }

    private fun api() = source("Parts", "public interface Parts { Label build(); }")
    private fun configured(field: String = "private final String id;", body: String = "this.id = id;", extra: String = ""): com.intellij.psi.PsiFile {
        api()
        return source("ConfiguredParts", "public class ConfiguredParts implements Parts { $field public ConfiguredParts(String id) { $body } public Label build() { return new Label(this.id); } $extra }")
    }
    private fun decorator() = source("Decorator", """
        public class Decorator implements Parts {
            private final Parts delegate;
            public Decorator(Parts delegate) { this.delegate = delegate; }
            public Label build() { return delegate.build(); }
        }
    """)
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
