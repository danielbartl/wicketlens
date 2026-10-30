package dev.wicketlens

import com.intellij.psi.PsiJavaFile
import dev.wicketlens.analysis.WicketPageAnalyzer
import dev.wicketlens.export.WicketContextExporter
import dev.wicketlens.model.*

class WicketAccessorTest : WicketProjectPsiTestCase() {
    fun testConstructorInjectedCachedProviderRetainsTheCallerIdOrigin() {
        configured(); holder()
        val java = page("Holder holder = new Holder(new ConfiguredParts(\"title\")); add(holder.parts().build());")
        val report = analyze(java)
        matched(report)
        assertEquals(java.text.indexOf("\"title\""), report.children().single().idLocation!!.offset)
        assertTrue(report.children().single().idRenameSafe)
    }

    fun testPageAccessorReadsAPrivateFinalProviderField() {
        configured()
        matched(analyze(page("add(parts().build());", """
            private final Parts parts = new ConfiguredParts("title");
            private Parts parts() { return this.parts; }
        """)))
    }

    fun testFinalPageCanUseAVirtualAccessor() {
        configured()
        matched(analyze(page("add(parts().build());", """
            private final Parts parts = new ConfiguredParts("title");
            public Parts parts() { return parts; }
        """, final = true)))
    }

    fun testOpenPageAccessorRemainsUnresolved() {
        configured()
        uncertain(analyze(page("add(parts().build());", """
            private final Parts parts = new ConfiguredParts("title");
            public Parts parts() { return parts; }
        """)))
    }

    fun testProviderAccessorResultsCanInitializeOtherProviderFields() {
        configured(); holder()
        matched(analyze(page("add(parts.build());", """
            private final Holder holder = new Holder(new ConfiguredParts("title"));
            private final Parts parts = holder.parts();
        """)))
    }

    fun testAnAccessorCanReturnTheCompletedProviderReceiver() {
        configured(extra = "public Parts self() { return this; }")
        matched(analyze(page("add(new ConfiguredParts(\"title\").self().build());")))
    }

    fun testAReadOnlyStaticForwarderPreservesKnownProviderIdentity() {
        configured()
        source("Access", "public class Access { public static <T extends Parts> T identity(T value) { return value; } }")
        matched(analyze(page("add(Access.identity(new ConfiguredParts(\"title\")).build());")))
    }

    fun testSourceDefaultAccessorsDispatchOnTheKnownProvider() {
        configured()
        source("Selection", "public interface Selection { Parts parts(); default Parts selected() { return parts(); } }")
        holder("implements Selection")
        matched(analyze(page("Selection selection = new Holder(new ConfiguredParts(\"title\")); add(selection.selected().build());")))
    }

    fun testGenericProviderAccessorRetainsConcreteAllocationType() {
        configured()
        source("Box", """
            public class Box<T extends Parts> {
                private final T value;
                public Box(T value) { this.value = value; }
                public T get() { return value; }
            }
        """)
        matched(analyze(page("Box<ConfiguredParts> box = new Box<ConfiguredParts>(new ConfiguredParts(\"title\")); add(box.get().build());")))
    }

    fun testScalarGetterForwardsAConstructorConfiguredId() {
        configured(body = "return new Label(id());", extra = "public String id() { return id; }")
        val java = page("add(new ConfiguredParts(\"title\").build());")
        val report = analyze(java)
        matched(report)
        assertEquals(java.text.indexOf("\"title\""), report.children().single().idLocation!!.offset)
    }

    fun testInheritedScalarGetterUsesTheSuperclassPrivateField() {
        source("BaseSettings", """
            public class BaseSettings {
                private final String id;
                public BaseSettings(String id) { this.id = id; }
                public String id() { return id; }
            }
        """)
        source("Settings", "public class Settings extends BaseSettings { public Settings(String id) { super(id); } }")
        matched(analyze(page("add(new Label(new Settings(\"title\").id()));")))
    }

    fun testAGetterOverrideUsesTheKnownConcreteImplementation() {
        source("Ids", "public interface Ids { String id(); }")
        settings("implements Ids")
        matched(analyze(page("Ids ids = new Settings(\"title\"); add(new Label(ids.id()));")))
    }

    fun testScalarGettersCanBeNestedAcrossProvidersAndLocals() {
        configured(extra = "public String id() { return this.id; }")
        holder(extra = "public String id() { return parts().id(); }")
        matched(analyze(page("Holder holder = new Holder(new ConfiguredParts(\"title\")); String id = holder.id(); add(new Label(id));")))
    }

    fun testScalarAccessorCanInitializeAPrivateFinalPageField() {
        settings()
        matched(analyze(page("add(new Label(id));", """
            private final Settings settings = new Settings("title");
            private final String id = settings.id();
        """)))
    }

    fun testScalarAccessorCanSupplyAConstructorAssignedPageField() {
        settings()
        matched(analyze(page("id = new Settings(\"title\").id(); add(new Label(id));", "private final String id;")))
    }

    fun testPageScalarGetterReadsAPrivateFinalField() {
        matched(analyze(page("add(new Label(id()));", "private final String id = \"title\"; private String id() { return id; }")))
    }

    fun testSourceDefaultScalarGetterPreservesTheInjectedLiteral() {
        source("Ids", "public interface Ids { String stored(); default String id() { return stored(); } }")
        settings("implements Ids", "public String stored() { return id; }")
        matched(analyze(page("Ids ids = new Settings(\"title\"); add(new Label(ids.id()));")))
    }

    fun testGenericScalarAccessorUsesTheCallSiteSubstitution() {
        source("Settings", """
            public class Settings<T> {
                private final T id;
                public Settings(T id) { this.id = id; }
                public T id() { return id; }
            }
        """)
        matched(analyze(page("Settings<String> settings = new Settings<String>(\"title\"); add(new Label(settings.id()));")))
    }

    fun testPrimitiveAndStaticLiteralGettersCanSupplyComponentData() {
        source("Values", "public class Values { public static String id() { return \"title\"; } public int count() { return 3; } }")
        val report = analyze(page("add(new Label(Values.id(), new Values().count()));"))
        matched(report)
        assertFalse(report.children().single().idRenameSafe)
    }

    fun testUnknownStringGetterResultsRemainDynamic() {
        settings()
        val report = analyze(page("Settings settings = new Settings(supplied); add(new Label(settings.id()));", parameters = "String supplied"))
        assertTrue(report.matching!!.findings.any { it.code == DiagnosticCode.UNRESOLVED_DYNAMIC_ID })
        uncertain(report)
    }

    fun testUnknownInjectedProviderCannotBeResolvedThroughAnAccessor() {
        configured(); holder()
        uncertain(analyze(page("Holder holder = new Holder(injected); add(holder.parts().build());", parameters = "ConfiguredParts injected")))
    }

    fun testAnUnknownReceiverCannotAcquireAConcreteGetter() {
        settings()
        uncertain(analyze(page("add(new Label(injected.id()));", parameters = "Settings injected")))
    }

    fun testAccessorsCannotExposeAnUnfinishedProvider() {
        configured()
        source("Holder", """
            public class Holder {
                private final Parts value = parts();
                public Parts parts() { return new ConfiguredParts("title"); }
                public Label build() { return value.build(); }
            }
        """)
        uncertain(analyze(page("add(new Holder().build());")))
    }

    fun testScalarGettersCannotReadAFieldBeforeItIsInitialized() {
        source("Settings", """
            public class Settings {
                private final String first = id();
                private final String id = "title";
                public String id() { return id; }
            }
        """)
        uncertain(analyze(page("add(new Label(new Settings().id()));")))
    }

    fun testMutablePageFieldsCannotBecomeStableIdsThroughGetters() {
        uncertain(analyze(page("add(new Label(id()));", "private String id = \"title\"; private String id() { return id; } private void change() { id = \"changed\"; }")))
    }

    fun testMutableLiteralOriginsCannotHideBehindScalarParameters() {
        uncertain(analyze(page("add(new Label(identity(id)));", "private String id = \"title\"; private String identity(String value) { return value; }")))
    }

    fun testNonprivatePageFieldsCannotBecomeStableIdsThroughGetters() {
        uncertain(analyze(page("add(new Label(id()));", "public final String id = \"title\"; private String id() { return id; }")))
    }

    fun testProviderAccessorsCannotHideSetupBeforeTheReturn() {
        configured()
        uncertain(analyze(page("add(parts().build());", """
            private final Parts parts = new ConfiguredParts("title");
            private Parts parts() { add(new Label("other")); return parts; }
        """)))
    }

    fun testNestedCallsCannotHideSetupInsideAnAccessorReturn() {
        configured()
        uncertain(analyze(page("add(parts().build());", """
            private final Parts parts = new ConfiguredParts("title");
            private Parts parts() { return nested(); }
            private Parts nested() { add(new Label("other")); return parts; }
        """)))
    }

    fun testAccessorsCannotHideAllocationInAnUnusedArgument() {
        configured()
        uncertain(analyze(page("add(parts().build());", """
            private final Parts parts = new ConfiguredParts("title");
            private Parts parts() { return identity(new ConfiguredParts("unused")); }
            private Parts identity(Parts ignored) { return parts; }
        """)))
    }

    fun testScalarAccessorsMustBeSingleReturnReads() {
        uncertain(analyze(page("add(new Label(id()));", "private String id() { String value = \"title\"; return value; }")))
    }

    fun testRecursiveGettersRemainBounded() {
        uncertain(analyze(page("add(new Label(id()));", "private String id() { return id(); }")))
    }

    fun testComputedScalarGetterIdsAreMatchedWithoutLiteralRename() {
        val report = analyze(page("add(new Label(id()));", "private String id() { return \"ti\" + \"tle\"; }"))
        matched(report)
        assertTrue(report.children().single().computedId)
        assertFalse(report.children().single().idRenameSafe)
    }

    fun testConditionalGetterDoesNotChooseOneLiteral() {
        uncertain(analyze(page("add(new Label(id()));", "private String id() { if (System.nanoTime() > 0) return \"title\"; return \"other\"; }")))
    }

    fun testAnObjectReturningGetterIsNotTreatedAsAScalarGetter() {
        uncertain(analyze(page("add(new Label(\"title\", caption()));", "private Object caption() { return \"Caption\"; }")))
    }

    fun testStaticInitializationCannotHideBehindAScalarGetter() {
        source("Ids", "public class Ids { static { System.setProperty(\"effect\", \"yes\"); } public static String id() { return \"title\"; } }")
        uncertain(analyze(page("add(new Label(Ids.id()));")))
    }

    fun testAnEffectfulQualifierCannotHideBehindAStaticGetter() {
        source("Ids", "public class Ids { public static String id() { return \"title\"; } }")
        uncertain(analyze(page("add(new Label(ids().id()));", "private Ids ids() { removeAll(); return null; }")))
    }

    fun testIncompatibleScalarReturnTypesRemainUnresolved() {
        uncertain(analyze(page("add(new Label(id()));", "private String id() { return 1; }")))
    }

    fun testNullGettersRemainUnresolved() {
        uncertain(analyze(page("add(new Label(id()));", "private String id() { return null; }")))
    }

    fun testProviderAccessorsStillCheckDeclaredReturnCompatibility() {
        configured()
        source("Other", "public interface Other { Label build(); }")
        uncertain(analyze(page("add(parts().build());", """
            private final Parts parts = new ConfiguredParts("title");
            private Other parts() { return parts; }
        """)))
    }

    fun testCachedComponentReturnsRemainUnsupported() {
        uncertain(analyze(page("add(label());", "private final Label label = new Label(\"title\"); private Label label() { return label; }")))
    }

    fun testKnownGetterIdsAndOpaqueRuntimeGettersCoexistInRepeaterTemplates() {
        settings()
        source("Person", "public class Person { public String name() { return \"Ada\"; } }")
        val java = page("""
            Settings settings = new Settings("name");
            add(new org.apache.wicket.markup.html.list.ListView<Person>("rows", people) {
                @Override protected void populateItem(org.apache.wicket.markup.html.list.ListItem<Person> item) {
                    String id = settings.id();
                    String caption = item.getModelObject().name();
                    item.add(new Label(id, caption));
                }
            });
        """, parameters = "java.util.List<Person> people")
        companion(html = "<div wicket:id=\"rows\"><span wicket:id=\"name\"></span><span wicket:id=\"other\"></span></div>")
        val report = WicketPageAnalyzer().analyze(java.classes.single())!!
        assertEquals(2, report.matching!!.findings.count { it.code == DiagnosticCode.MATCHED })
        assertTrue(report.warnings.any { it.code == DiagnosticCode.UNRESOLVED_SCOPE })
        assertFalse(report.matching.findings.any { it.code == DiagnosticCode.MARKUP_ONLY })
    }

    fun testNestedRuntimeDataGettersNeverSupplyADeclaredImplementationId() {
        source("Person", "public class Person { public Person child() { return this; } public String id() { return \"name\"; } }")
        val java = page("""
            add(new org.apache.wicket.markup.html.list.ListView<Person>("rows", people) {
                @Override protected void populateItem(org.apache.wicket.markup.html.list.ListItem<Person> item) {
                    item.add(new Label(item.getModelObject().child().id()));
                }
            });
        """, parameters = "java.util.List<Person> people")
        companion(html = "<div wicket:id=\"rows\"><span wicket:id=\"name\"></span></div>")
        val report = WicketPageAnalyzer().analyze(java.classes.single())!!
        assertTrue(report.matching!!.findings.any { it.code == DiagnosticCode.UNRESOLVED_DYNAMIC_ID })
        assertTrue(report.children().none { it.wicketId == "name" })
        assertFalse(report.matching.findings.any { it.code == DiagnosticCode.MARKUP_ONLY })
    }

    fun testReadOnlyAccessorAnalysisIsDeterministicAndDoesNotEditSource() {
        val provider = configured(body = "return new Label(id());", extra = "public String id() { return id; }")
        val container = holder()
        val java = page("Holder holder = new Holder(new ConfiguredParts(\"title\")); add(holder.parts().build());")
        val before = listOf(provider.text, container.text, java.text)
        val first = analyze(java)
        val second = WicketPageAnalyzer().analyze(java.classes.single())!!
        matched(first)
        assertEquals(first, second)
        assertEquals(WicketContextExporter().export(first), WicketContextExporter().export(second))
        assertEquals(before, listOf(provider.text, container.text, java.text))
    }

    fun testDemoCombinesPageProviderAndScalarAccessors() {
        val root = java.nio.file.Path.of(System.getProperty("wicketlens.test.demo"))
        for (name in listOf("ComponentParts", "DispatchParts", "AccessorParts", "ProviderAccessorPage")) {
            myFixture.addFileToProject("src/main/java/example/$name.java", java.nio.file.Files.readString(root.resolve("main/java/example/$name.java")))
        }
        companion("ProviderAccessorPage", java.nio.file.Files.readString(root.resolve("main/resources/example/ProviderAccessorPage.html")))
        val java = com.intellij.psi.PsiManager.getInstance(project).findFile(myFixture.findFileInTempDir("src/main/java/example/ProviderAccessorPage.java")) as PsiJavaFile
        val report = WicketPageAnalyzer().analyze(java.classes.single())!!
        assertEquals(4, report.matching!!.findings.count { it.code == DiagnosticCode.MATCHED })
        assertEquals(setOf("title", "subtitle", "form", "name"), report.children().map { it.wicketId }.toSet())
        assertEquals(4, report.javaSourceFiles.size)
        assertTrue(report.children().filter { it.wicketId in setOf("title", "subtitle", "form") }.all { it.idRenameSafe })
    }

    private fun configured(body: String = "return new Label(id);", extra: String = ""): com.intellij.psi.PsiFile {
        source("Parts", "public interface Parts { Label build(); String id(); }")
        return source("ConfiguredParts", """
        public class ConfiguredParts implements Parts {
            private final String id;
            public ConfiguredParts(String id) { this.id = id; }
            ${if (extra.contains("String id()")) "" else "public String id() { return id; }"}
            public Label build() { $body }
            $extra
        }
        """)
    }
    private fun holder(interfaces: String = "", extra: String = "") = source("Holder", """
        public class Holder $interfaces {
            private final ConfiguredParts parts;
            public Holder(ConfiguredParts parts) { this.parts = parts; }
            public ConfiguredParts parts() { return parts; }
            $extra
        }
    """)
    private fun settings(interfaces: String = "", extra: String = "") = source("Settings", """
        public class Settings $interfaces {
            private final String id;
            public Settings(String id) { this.id = id; }
            public String id() { return id; }
            $extra
        }
    """)
    private fun source(name: String, declaration: String) = myFixture.addFileToProject("src/main/java/example/$name.java", "package example; import org.apache.wicket.markup.html.basic.Label; $declaration")
    private fun page(body: String, members: String = "", parameters: String = "", final: Boolean = false) = source("ExamplePage", """
        public ${if (final) "final " else ""}class ExamplePage extends org.apache.wicket.markup.html.WebPage {
            public ExamplePage($parameters) { $body } $members
        }
    """) as PsiJavaFile
    private fun analyze(java: PsiJavaFile): PageReport {
        companion()
        return WicketPageAnalyzer().analyze(java.classes.single())!!
    }
    private fun matched(report: PageReport) {
        assertEquals(report.matching!!.findings.map { it.code }.toString(), listOf(DiagnosticCode.MATCHED), report.matching.findings.map { it.code })
        assertTrue(report.warnings.toString(), report.warnings.none { it.code == DiagnosticCode.UNRESOLVED_SCOPE })
    }
    private fun uncertain(report: PageReport) = assertTrue(report.matching!!.findings.map { it.code }.toString(),
        report.matching.findings.none { it.code in setOf(DiagnosticCode.MATCHED, DiagnosticCode.JAVA_ONLY, DiagnosticCode.MARKUP_ONLY) })
}
