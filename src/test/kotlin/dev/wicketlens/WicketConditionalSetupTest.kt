package dev.wicketlens

import com.intellij.psi.PsiJavaFile
import dev.wicketlens.analysis.WicketPageAnalyzer
import dev.wicketlens.export.WicketContextExporter
import dev.wicketlens.model.*

class WicketConditionalSetupTest : WicketProjectPsiTestCase() {
    fun testTrueBranchAddsOnlyTheSelectedComponent() {
        matched(report("if (true) add(new Label(\"title\")); else add(new Label(\"inactive\"));"))
    }

    fun testFalseBranchSelectsElseAndIgnoresUnsupportedDeadEffects() {
        matched(report("if (false) { removeAll(); new Object() {{ ExamplePage.this.removeAll(); }}; } else add(new Label(\"title\"));"))
    }

    fun testKnownFalseWithoutElseHasNoInventedJavaBinding() {
        val report = report("if (false) add(new Label(\"title\"));")
        assertTrue(report.children().isEmpty())
        assertEquals(listOf(DiagnosticCode.MARKUP_ONLY), report.matching!!.findings.map { it.code })
    }

    fun testPrivateFinalBooleanFieldsAreStableConditions() {
        matched(report("if (enabled) add(new Label(\"title\"));", "private final boolean enabled = true;"))
    }

    fun testLiteralBooleanLocalsAndNestedBlocksKeepTheirScopes() {
        matched(report("boolean enabled = true; { String id = \"title\"; if (enabled) { add(new Label(id)); } }"))
    }

    fun testPrivateBooleanGetterCanSelectTheBranch() {
        matched(report("if (enabled()) add(new Label(\"title\"));", "private boolean enabled() { return true; }"))
    }

    fun testProviderConfigurationAndNegatedGettersSelectTheBranch() {
        flags()
        matched(report("Flags flags = new Flags(false); if (!flags.enabled()) add(new Label(\"title\"));"))
    }

    fun testComputedBooleanConfigurationRetainsAProvenValue() {
        flags()
        matched(report("Flags flags = new Flags(!false && true); if (flags.enabled()) add(new Label(\"title\"));"))
    }

    fun testNegationAndShortCircuitPrecedenceFollowJava() {
        matched(report("if (!false && (true || false) && true) add(new Label(\"title\")); else removeAll();"))
    }

    fun testFalseAndSkipsAnEffectfulRightOperand() {
        matched(report("add(new Label(\"title\")); if (false && effect()) removeAll();", effect()))
    }

    fun testTrueOrSkipsAnEffectfulRightOperand() {
        matched(report("if (true || effect()) add(new Label(\"title\"));", effect()))
    }

    fun testUnknownPrimitiveValuesCanHaveAProvenLogicalResult() {
        matched(report("if (enabled || true) add(new Label(\"title\"));", parameters = "boolean enabled"))
    }

    fun testUnknownAndFalseSelectsElseWithoutGuessingTheUnknownValue() {
        matched(report("if (enabled && false) removeAll(); else add(new Label(\"title\"));", parameters = "boolean enabled"))
    }

    fun testUnknownBooleanLocalAliasStillAllowsAnIndependentLogicalResult() {
        matched(report("boolean alias = enabled; if (alias || true) add(new Label(\"title\"));", parameters = "boolean enabled"))
    }

    fun testARequiredEffectfulOperandCannotHideBehindTheLogicalResult() {
        uncertain(report("add(new Label(\"title\")); if (effect() && false) removeAll();", effect()))
    }

    fun testUnknownLeftOperandDoesNotSkipAPossibleEffectfulRightOperand() {
        uncertain(report("add(new Label(\"title\")); if (enabled && effect()) removeAll();", effect(), "boolean enabled"))
    }

    fun testUnknownConditionKeepsBothBranchesAndEarlierSetupUncertain() {
        uncertain(report("add(new Label(\"title\")); if (enabled) add(new Label(\"left\")); else add(new Label(\"right\"));", parameters = "boolean enabled"))
    }

    fun testMutableConditionFieldsCannotBeProvenFromTheirInitializers() {
        uncertain(report("if (enabled) add(new Label(\"title\"));", "private boolean enabled = true; private void disable() { enabled = false; }"))
    }

    fun testMutableOriginsCannotHideBehindLocalBooleanAliases() {
        uncertain(report("boolean alias = enabled; if (alias) add(new Label(\"title\"));", "private boolean enabled = true;"))
    }

    fun testMutableOriginsCannotHideBehindDerivedBooleanAliases() {
        uncertain(report("boolean alias = !disabled; if (alias) add(new Label(\"title\"));", "private boolean disabled = false;"))
    }

    fun testNonprivateInstanceBooleanFieldsRemainOutsideTheContract() {
        uncertain(report("if (enabled) add(new Label(\"title\"));", "public final boolean enabled = true;"))
    }

    fun testMutableStaticBooleanFieldsRemainOutsideTheContract() {
        uncertain(report("if (enabled) add(new Label(\"title\"));", "private static boolean enabled = true;"))
    }

    fun testSourceStaticFinalBooleanFieldsCanSelectAProvenBranch() {
        matched(report("if (enabled) add(new Label(\"title\"));", "private static final boolean enabled = true;"))
    }

    fun testBoxedBooleanConditionsRemainUnresolved() {
        uncertain(report("if (enabled) add(new Label(\"title\"));", parameters = "Boolean enabled"))
    }

    fun testAnOpenVirtualBooleanGetterIsNotAssumedToReturnItsBodyLiteral() {
        uncertain(report("if (enabled()) add(new Label(\"title\"));", "protected boolean enabled() { return true; }"))
    }

    fun testEvaluatedConditionEffectsInvalidateEarlierBindings() {
        uncertain(report("add(new Label(\"title\")); if (effect()) add(new Label(\"other\"));", effect()))
    }

    fun testAComponentFactoryReturnsFromTheSelectedBranch() {
        matched(report("add(label(false, \"unused\", \"title\"));", """
            private Label label(boolean first, String left, String right) {
                if (first) return new Label(left);
                return new Label(right);
            }
        """))
    }

    fun testAnEarlyFactoryReturnSkipsLaterMutationsAndBindings() {
        matched(report("add(label(true, \"title\"));", """
            private Label label(boolean enabled, String id) {
                if (enabled) { return new Label(id); }
                removeAll();
                return new Label("inactive");
            }
        """))
    }

    fun testConditionalCallsHaveIndependentArgumentsAndAllocations() {
        val java = page("add(label(true, \"title\", \"unused\"), label(false, \"unused\", \"subtitle\"));", """
            private Label label(boolean first, String left, String right) {
                if (first) return new Label(left); else return new Label(right);
            }
        """)
        matched(analyze(java, "<span wicket:id=\"title\"></span><span wicket:id=\"subtitle\"></span>"), setOf("title", "subtitle"))
    }

    fun testFreshProviderFactoriesCanChooseAFreshAllocation() {
        parts()
        matched(report("add(parts(true, \"title\").build());", """
            private Parts parts(boolean enabled, String id) {
                if (enabled) return new ConfiguredParts(id);
                return new ConfiguredParts("inactive");
            }
        """))
    }

    fun testSourceInterfaceDefaultFactoriesCanUseKnownConditions() {
        source("Parts", """
            public interface Parts {
                default Label build(boolean enabled, String id) {
                    if (enabled) return new Label(id);
                    return new Label("inactive");
                }
            }
        """)
        source("DefaultParts", "public class DefaultParts implements Parts {}")
        matched(report("add(new DefaultParts().build(true, \"title\"));"))
    }

    fun testIfStatementsDoNotRelaxTheReadOnlyCachedProviderContract() {
        parts()
        uncertain(report("add(parts(true).build());", """
            private final Parts stored = new ConfiguredParts("title");
            private Parts parts(boolean enabled) { if (enabled) return stored; return stored; }
        """))
    }

    fun testCachedComponentsStillCannotBecomeFreshFactoryReturns() {
        uncertain(report("add(label(true));", """
            private final Label stored = new Label("title");
            private Label label(boolean enabled) { if (enabled) return stored; return new Label("inactive"); }
        """))
    }

    fun testKnownConstructorGuardStopsOnlyThatConstructor() {
        val report = report("if (true) return; add(new Label(\"title\"));")
        assertTrue(report.children().isEmpty())
        assertEquals(DiagnosticCode.MARKUP_ONLY, report.matching!!.findings.single().code)
    }

    fun testVoidHelperGuardDoesNotReturnFromItsCaller() {
        matched(report("setup(false); add(new Label(\"title\"));", "private void setup(boolean enabled) { if (!enabled) return; removeAll(); }"))
    }

    fun testInitializationGuardPreservesConstructorBindings() {
        matched(report("add(new Label(\"title\"));", "@Override protected void onInitialize() { super.onInitialize(); if (true) return; removeAll(); }"))
    }

    fun testConstructorReturnDoesNotSkipTheLaterInitializationPhase() {
        matched(report("if (true) return;", "@Override protected void onInitialize() { super.onInitialize(); add(new Label(\"title\")); }"))
    }

    fun testPrivateFinalFieldsCanBeAssignedInTheProvenBranch() {
        matched(report("if (true) title = new Label(\"title\"); else title = new Label(\"inactive\"); add(title);", "private final Label title;"))
    }

    fun testSkippedTrackedFieldReadsDoNotInvalidateTheSelectedSetup() {
        matched(report("if (false) add(inactive); add(new Label(\"title\"));", "private final Label inactive = new Label(\"inactive\");"))
    }

    fun testShortCircuitedTrackedFieldEscapesAreNotEvaluated() {
        matched(report("if (true || consume(stored)) add(stored);", """
            private final Label stored = new Label("title");
            private boolean consume(org.apache.wicket.Component ignored) { return true; }
        """))
    }

    fun testTrackedFieldReadsAfterAnEarlyReturnAreSkipped() {
        matched(report("setup(false); add(stored);", """
            private final Label stored = new Label("title");
            private void setup(boolean enabled) { if (!enabled) return; remove(stored); }
        """))
    }

    fun testSkippedMutationInOneInvocationDoesNotHideItInAnother() {
        uncertain(report("setup(false); add(new Label(\"title\")); setup(true);", "private void setup(boolean enabled) { if (!enabled) return; removeAll(); }"))
    }

    fun testTernaryIdRetainsTheSelectedLiteralOrigin() {
        val java = page("add(new Label(true ? \"title\" : \"inactive\"));")
        val report = analyze(java)
        matched(report)
        assertEquals(java.text.indexOf("\"title\""), report.children().single().idLocation!!.offset)
        assertTrue(report.children().single().idRenameSafe)
    }

    fun testTernaryComponentLocalsConstructOnlyTheSelectedBranch() {
        matched(report("Label title = false ? new Label(\"inactive\") : new Label(\"title\"); add(title);"))
    }

    fun testTernaryProviderFieldsKeepKnownConfiguration() {
        parts()
        matched(report("add(parts.build());", "private final Parts parts = true ? new ConfiguredParts(\"title\") : new ConfiguredParts(\"inactive\");"))
    }

    fun testTernaryAccessorsCanReadKnownProvidersWithoutAllocating() {
        parts()
        val java = page("add(parts(true).build(), parts(false).build());", """
            private final Parts first = new ConfiguredParts("title");
            private final Parts second = new ConfiguredParts("subtitle");
            private Parts parts(boolean primary) { return primary ? first : second; }
        """)
        matched(analyze(java, "<span wicket:id=\"title\"></span><span wicket:id=\"subtitle\"></span>"), setOf("title", "subtitle"))
    }

    fun testSingleReturnScalarGettersCanSelectAnIdWithATernary() {
        matched(report("add(new Label(id(true, \"title\", \"unused\")));", "private String id(boolean enabled, String primary, String other) { return enabled ? primary : other; }"))
    }

    fun testUnknownTernaryDoesNotChooseAnId() {
        uncertain(report("add(new Label(enabled ? \"title\" : \"other\"));", parameters = "boolean enabled"))
    }

    fun testDeadTernaryOperandDoesNotInvokeItsEffects() {
        matched(report("add(new Label(true ? \"title\" : badId()));", "private String badId() { removeAll(); return \"inactive\"; }"))
    }

    fun testUnknownConditionsInFreshFactoriesStayUnresolved() {
        uncertain(report("add(label(enabled));", "private Label label(boolean enabled) { if (enabled) return new Label(\"title\"); return new Label(\"other\"); }", "boolean enabled"))
    }

    fun testLoopAndExceptionControlFlowRemainUnresolvedWhenReached() {
        uncertain(report("if (true) { while (true) { add(new Label(\"title\")); break; } }"))
    }

    fun testDeepConditionalBlocksExhaustTheBoundConservatively() {
        uncertain(report("if (true) {".repeat(15) + "add(new Label(\"title\"));" + "}".repeat(15)))
    }

    fun testDeepBooleanExpressionsExhaustTheBoundConservatively() {
        uncertain(report("if (" + "!".repeat(40) + "true) add(new Label(\"title\"));"))
    }

    fun testPanelSetupUsesTheSameConditionalContract() {
        val java = source("ExamplePage", """
            public class ExamplePage extends org.apache.wicket.markup.html.panel.Panel {
                public ExamplePage(String id) { super(id); if (true) add(new Label("title")); }
            }
        """) as PsiJavaFile
        matched(analyze(java, "<wicket:panel><span wicket:id=\"title\"></span></wicket:panel>"))
    }

    fun testKnownConditionalItemSetupRetainsTheRepeaterTemplate() {
        val java = page("""
            add(new org.apache.wicket.markup.html.list.ListView<String>("rows", names) {
                @Override protected void populateItem(org.apache.wicket.markup.html.list.ListItem<String> item) {
                    if (false) return;
                    if (true) item.add(new Label("name", item.getModelObject()));
                }
            });
        """, parameters = "java.util.List<String> names")
        matched(analyze(java, "<div wicket:id=\"rows\"><span wicket:id=\"name\"></span></div>"), setOf("rows", "name"))
    }

    fun testReturnInsideASymbolicForeachCannotSkipTheCallerTailByGuessingRowCounts() {
        uncertain(report("""
            org.apache.wicket.markup.repeater.RepeatingView rows = new org.apache.wicket.markup.repeater.RepeatingView("rows");
            add(rows);
            for (String name : names) {
                org.apache.wicket.markup.html.WebMarkupContainer item = new org.apache.wicket.markup.html.WebMarkupContainer(rows.newChildId());
                rows.add(item);
                item.add(new Label("name", name));
                if (true) return;
            }
            add(new Label("title"));
        """, parameters = "java.util.List<String> names"))
    }

    fun testConditionAnalysisIsReadOnlyAndDeterministic() {
        val flags = flags()
        val java = page("Flags flags = new Flags(true); if (flags.enabled()) add(new Label(\"title\"));")
        val before = listOf(flags.text, java.text)
        val first = analyze(java)
        val second = WicketPageAnalyzer().analyze(java.classes.single())!!
        matched(first)
        assertEquals(first, second)
        assertEquals(WicketContextExporter().export(first), WicketContextExporter().export(second))
        assertEquals(before, listOf(flags.text, java.text))
    }

    fun testDemoCombinesProviderConditionsFactoriesAndInitializationGuards() {
        val root = java.nio.file.Path.of(System.getProperty("wicketlens.test.demo"))
        for (name in listOf("ComponentParts", "DispatchParts", "ConditionalParts", "ConditionalSetupPage")) {
            myFixture.addFileToProject("src/main/java/example/$name.java", java.nio.file.Files.readString(root.resolve("main/java/example/$name.java")))
        }
        companion("ConditionalSetupPage", java.nio.file.Files.readString(root.resolve("main/resources/example/ConditionalSetupPage.html")))
        val java = com.intellij.psi.PsiManager.getInstance(project).findFile(myFixture.findFileInTempDir("src/main/java/example/ConditionalSetupPage.java")) as PsiJavaFile
        val report = WicketPageAnalyzer().analyze(java.classes.single())!!
        matched(report, setOf("title", "subtitle", "form", "name"))
        assertEquals(4, report.javaSourceFiles.size)
    }

    private fun flags() = source("Flags", """
        public class Flags {
            private final boolean enabled;
            public Flags(boolean enabled) { this.enabled = enabled; }
            public boolean enabled() { return enabled; }
        }
    """)
    private fun parts() {
        source("Parts", "public interface Parts { Label build(); }")
        source("ConfiguredParts", "public class ConfiguredParts implements Parts { private final String id; public ConfiguredParts(String id) { this.id = id; } public Label build() { return new Label(id); } }")
    }
    private fun effect() = "private boolean effect() { removeAll(); return true; }"
    private fun source(name: String, declaration: String) = myFixture.addFileToProject("src/main/java/example/$name.java", "package example; import org.apache.wicket.markup.html.basic.Label; $declaration")
    private fun page(body: String, members: String = "", parameters: String = "") = source("ExamplePage", "public class ExamplePage extends org.apache.wicket.markup.html.WebPage { public ExamplePage($parameters) { $body } $members }") as PsiJavaFile
    private fun analyze(java: PsiJavaFile, html: String = "<span wicket:id=\"title\"></span>"): PageReport {
        companion(html = html)
        return WicketPageAnalyzer().analyze(java.classes.single())!!
    }
    private fun report(body: String, members: String = "", parameters: String = "") = analyze(page(body, members, parameters))
    private fun matched(report: PageReport, ids: Set<String> = setOf("title")) {
        assertEquals(ids, report.children().filterNot { it.templateItem }.map { it.wicketId }.toSet())
        assertEquals(report.matching!!.findings.map { it.code }.toString(), ids.size, report.matching.findings.count { it.code == DiagnosticCode.MATCHED })
        assertTrue(report.warnings.toString(), report.warnings.none { it.code == DiagnosticCode.UNRESOLVED_SCOPE })
    }
    private fun uncertain(report: PageReport) = assertTrue(report.matching!!.findings.map { it.code }.toString(),
        report.matching.findings.none { it.code in setOf(DiagnosticCode.MATCHED, DiagnosticCode.JAVA_ONLY, DiagnosticCode.MARKUP_ONLY) })
}
