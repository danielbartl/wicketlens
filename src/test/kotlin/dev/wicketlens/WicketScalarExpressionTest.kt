package dev.wicketlens

import com.intellij.psi.PsiJavaFile
import dev.wicketlens.analysis.WicketPageAnalyzer
import dev.wicketlens.export.WicketContextExporter
import dev.wicketlens.model.*

class WicketScalarExpressionTest : WicketProjectPsiTestCase() {
    fun testStringConcatenationMatchesWithoutInventingALiteralOrigin() {
        val java = page("add(new Label(\"ti\" + \"tle\"));")
        val report = analyze(java)
        matched(report)
        val node = report.children().single()
        assertTrue(node.computedId)
        assertFalse(node.idRenameSafe)
        assertEquals(java.text.indexOf("\"ti\""), node.idLocation!!.offset)
    }

    fun testConcatenationThroughParametersAndGettersKeepsKnownConfiguration() {
        settings()
        matched(report("Settings settings = new Settings(\"ti\", 2); add(new Label(settings.id(\"tle\")));"))
    }

    fun testCalculatedIdsCanInitializePrivateFinalPageFields() {
        matched(report("add(new Label(id));", "private final String id = \"ti\" + \"tle\";"))
    }

    fun testCalculatedIdsCanInitializeConstructorAssignedFields() {
        matched(report("id = \"ti\" + \"tle\"; add(new Label(id));", "private final String id;"))
    }

    fun testStableLocalPrefixesCanSupplyComputedIds() {
        matched(report("String prefix = \"ti\"; String id = prefix + \"tle\"; add(new Label(id));"))
    }

    fun testIntegralComparisonSelectsTheKnownBranch() {
        matched(report("if (2 + 3 * 4 >= 14) add(new Label(\"title\")); else removeAll();"))
    }

    fun testAllIntegralComparisonOperatorsPreserveTheirResults() {
        matched(report("if (1 < 2 && 2 <= 2 && 3 > 2 && 3 >= 3 && 4 == 4 && 5 != 4) add(new Label(\"title\"));"))
    }

    fun testBooleanEqualityCanCombineProvenGetters() {
        matched(report("if (enabled() == true && false != true) add(new Label(\"title\"));", "private boolean enabled() { return true; }"))
    }

    fun testArithmeticUsesJavaIntOverflow() {
        matched(report("int value = 2147483647; if (value + 1 < 0) add(new Label(\"title\"));"))
    }

    fun testLongPromotionDoesNotOverflowAtTheIntBoundary() {
        matched(report("long value = 2147483647; if (value + 1 > 2147483647) add(new Label(\"title\"));"))
    }

    fun testAdjacentLongValuesAreNotComparedAsLossyDoubles() {
        matched(report("if (9007199254740993L > 9007199254740992L) add(new Label(\"title\"));"))
    }

    fun testLongOverflowWrapsLikeJava() {
        matched(report("if (9223372036854775807L + 1L < 0L) add(new Label(\"title\"));"))
    }

    fun testMinimumIntegralLiteralAndDivisionOverflowAreHandled() {
        matched(report("if (-2147483648 / -1 == -2147483648 && -9223372036854775808L < 0L) add(new Label(\"title\"));"))
    }

    fun testUnaryPromotionHandlesCharByteAndShort() {
        matched(report("char value = 'b'; byte offset = 1; short size = 2; if (-value + +offset + size == -95) add(new Label(\"title\"));"))
    }

    fun testIntegralCastsPreserveJavaNarrowing() {
        matched(report("if ((byte)257 == 1 && (char)65 == 'A' && (int)4294967297L == 1) add(new Label(\"title\"));"))
    }

    fun testLegalConstantNarrowingInScalarGettersIsPreserved() {
        matched(report("if (value() + 1 == 2) add(new Label(\"title\"));", "private byte value() { return 1; }"))
    }

    fun testInvalidImplicitNarrowingDoesNotBecomeAWrappedConstant() {
        uncertain(report("byte value = 257; if (value == 1) add(new Label(\"title\"));"))
    }

    fun testInvalidFieldNarrowingDoesNotBecomeAProvenCondition() {
        uncertain(report("if (value == 1) add(new Label(\"title\"));", "private final byte value = 257;"))
    }

    fun testInvalidProviderFieldNarrowingDoesNotBecomeAProvenCondition() {
        source("Settings", "public class Settings { private final byte value = 257; public int value() { return value; } }")
        uncertain(report("if (new Settings().value() == 1) add(new Label(\"title\"));"))
    }

    fun testNullableUnboxingThroughAScalarGetterCannotProveABranch() {
        uncertain(report("if (unbox(value) > 0 || true) add(new Label(\"title\"));", "private int unbox(Integer value) { return value; }", "Integer value"))
    }

    fun testNullableUnboxingThroughASourceArgumentCannotProveABranch() {
        uncertain(report("if (positive(value) || true) add(new Label(\"title\"));", "private boolean positive(int value) { return value > 0; }", "Integer value"))
    }

    fun testNullableUnboxingInASelectedTernaryCannotBeIgnored() {
        uncertain(report("if ((true ? value : 1) > 0 || true) add(new Label(\"title\"));", parameters = "Integer value"))
    }

    fun testIntegralDivisionAndRemainderUseJavaSigns() {
        matched(report("if (-7 / 3 == -2 && -7 % 3 == -1) add(new Label(\"title\"));"))
    }

    fun testNumericTernaryPromotionIsPreservedByLaterOperations() {
        matched(report("if ((true ? 2147483647 : 0L) + 1 > 2147483647) add(new Label(\"title\"));"))
    }

    fun testWideningGetterResultsKeepTheDeclaredNumericType() {
        matched(report("if (value() + 1 > 2147483647) add(new Label(\"title\"));", "private long value() { return 2147483647; }"))
    }

    fun testStringConversionPreservesCharAndBooleanValues() {
        val java = page("char letter = 65; boolean enabled = true; add(new Label(\"item\" + letter + enabled));")
        matched(analyze(java, "<span wicket:id=\"itemAtrue\"></span>"), setOf("itemAtrue"))
    }

    fun testStringConcatenationIsLeftAssociative() {
        val java = page("add(new Label(1 + 2 + \"item\" + 3 + 4));")
        matched(analyze(java, "<span wicket:id=\"3item34\"></span>"), setOf("3item34"))
    }

    fun testStringEscapesAreNotInterpretedAsCodeWhenFolded() {
        val java = page("String prefix = \"a\\\"b\"; add(new Label(prefix + \"&c\"));")
        matched(analyze(java, "<span wicket:id=\"a&quot;b&amp;c\"></span>"), setOf("a\"b&c"))
    }

    fun testKnownStringContentEqualityCanSelectSetup() {
        matched(report("String prefix = \"ti\"; if ((prefix + \"tle\").equals(\"title\")) add(new Label(\"title\"));"))
    }

    fun testStringContentInequalityCanSelectElse() {
        matched(report("if (\"inactive\".equals(\"title\")) removeAll(); else add(new Label(\"title\"));"))
    }

    fun testUnknownStringArgumentToANonnullEqualsReceiverStaysUnknown() {
        uncertain(report("if (\"title\".equals(id)) add(new Label(\"title\"));", parameters = "String id"))
    }

    fun testNonnullEqualsWithUnknownArgumentCanHaveAnIndependentBooleanResult() {
        matched(report("if (\"title\".equals(id) || true) add(new Label(\"title\"));", parameters = "String id"))
    }

    fun testUnknownEqualsReceiverCannotBeAssumedNonnull() {
        uncertain(report("if (id.equals(\"title\") || true) add(new Label(\"title\"));", parameters = "String id"))
    }

    fun testStringReferenceEqualityDoesNotGuessIdentityFromKnownContents() {
        uncertain(report("String value = \"title\"; if (value == \"title\") add(new Label(\"title\"));"))
    }

    fun testUnknownArithmeticRemainsDynamicWithoutInventingAnId() {
        val report = report("add(new Label(\"item\" + (value + 1)));", parameters = "int value")
        assertTrue(report.matching!!.findings.any { it.code == DiagnosticCode.UNRESOLVED_DYNAMIC_ID })
        uncertain(report)
    }

    fun testUnknownPrimitiveArithmeticCanHaveAnIndependentBooleanResult() {
        matched(report("if (value + 1 > 0 || true) add(new Label(\"title\"));", parameters = "int value"))
    }

    fun testUnknownDivisorCannotHidePossibleDivisionByZero() {
        uncertain(report("if (10 / value > 0 || true) add(new Label(\"title\"));", parameters = "int value"))
    }

    fun testUnknownNumeratorWithKnownNonzeroDivisorRemainsSafeAndUnknown() {
        matched(report("if (value / 2 > 0 || true) add(new Label(\"title\"));", parameters = "int value"))
    }

    fun testKnownDivisionByZeroFallsBackConservatively() {
        uncertain(report("if (1 / 0 == 0) add(new Label(\"title\"));"))
    }

    fun testUnknownComparisonStillInspectsAnEffectfulOtherOperand() {
        uncertain(report("if (value == effect() || true) add(new Label(\"title\"));", "private int effect() { removeAll(); return 1; }", "int value"))
    }

    fun testMutableValuesCannotAcquireStableComputedOrigins() {
        uncertain(report("if (value + 1 > 0) add(new Label(\"title\"));", "private int value = 1;"))
    }

    fun testMutableStringAliasesCannotAcquireComputedIds() {
        uncertain(report("String alias = prefix; add(new Label(alias + \"tle\"));", "private String prefix = \"ti\";"))
    }

    fun testObjectConcatenationDoesNotInvokeOrGuessToString() {
        source("Prefix", "public class Prefix { public String toString() { return \"ti\"; } }")
        uncertain(report("Prefix prefix = new Prefix(); add(new Label(prefix + \"tle\"));"))
    }

    fun testFloatingPointArithmeticStaysOutsideTheIntegralContract() {
        uncertain(report("if (0.1 + 0.2 > 0.0) add(new Label(\"title\"));"))
    }

    fun testNullComparisonsAndBoxedValuesRemainUnresolved() {
        uncertain(report("if (value != null) add(new Label(\"title\"));", parameters = "Integer value"))
    }

    fun testSourceStaticConstantsCanProveAnIntegralCondition() {
        matched(report("if (VALUE > 0) add(new Label(\"title\"));", "private static final int VALUE = 1;"))
    }

    fun testComputedEmptyAndReservedIdsKeepInvalidIdDiagnostics() {
        val report = report("add(new Label(\"\" + \"\"), new Label(\"a\" + \":b\"));")
        assertEquals(2, report.matching!!.findings.count { it.code == DiagnosticCode.UNRESOLVED_ID })
        uncertain(report)
    }

    fun testKnownAbsentCounterpartsCanBeReportedForComputedIds() {
        val java = page("add(new Label(\"java\" + \"Only\"));")
        val report = analyze(java, "<span wicket:id=\"htmlOnly\"></span>")
        assertEquals(setOf(DiagnosticCode.JAVA_ONLY, DiagnosticCode.MARKUP_ONLY), report.matching!!.findings.map { it.code }.toSet())
    }

    fun testAnIdUsedInALaterConditionCannotOfferAnIndependentRename() {
        val java = page("String id = \"title\"; Label label = new Label(id); if (id.equals(\"title\")) add(label);")
        val report = analyze(java)
        matched(report)
        assertTrue(report.children().single().idUsedInComputation)
        assertFalse(report.children().single().idRenameSafe)
    }

    fun testPlainAndComputedIdsSharingAnInputCannotBeRenamedSeparately() {
        val java = page("String id = \"title\"; add(new Label(id), new Label(id + \"Extra\"));")
        val report = analyze(java, "<span wicket:id=\"title\"></span><span wicket:id=\"titleExtra\"></span>")
        matched(report, setOf("title", "titleExtra"))
        assertTrue(report.children().single { !it.computedId }.idUsedInComputation)
        assertTrue(report.children().none { it.idRenameSafe })
    }

    fun testAComparisonNotUsingTheIdKeepsOrdinaryLiteralRename() {
        val report = report("if (2 > 1) add(new Label(\"title\"));")
        matched(report)
        assertTrue(report.children().single().idRenameSafe)
    }

    fun testComputedProviderConfigurationRetainsSeparateInstances() {
        settings()
        val java = page("Settings first = new Settings(\"ti\" + \"t\", 1); Settings second = new Settings(\"sub\", 2); add(new Label(first.id(\"le\")), new Label(second.id(\"title\")));")
        matched(analyze(java, "<span wicket:id=\"title\"></span><span wicket:id=\"subtitle\"></span>"), setOf("title", "subtitle"))
    }

    fun testConfiguredComparisonsCanRunInsideSourceDefaultFactories() {
        source("Parts", "public interface Parts { int size(); default Label build() { if (size() >= 2) return new Label(\"title\"); return new Label(\"inactive\"); } }")
        source("ConfiguredParts", "public class ConfiguredParts implements Parts { private final int size; public ConfiguredParts(int size) { this.size = size; } public int size() { return size; } }")
        matched(report("add(new ConfiguredParts(1 + 1).build());"))
    }

    fun testOversizedComputedStringsExhaustTheBoundConservatively() {
        uncertain(report("add(new Label(\"${"x".repeat(2100)}\" + \"${"y".repeat(2100)}\"));"))
    }

    fun testCalculationIsReadOnlyDeterministicAndKeepsTheExportSchema() {
        val java = page("if (2 * 3 == 6) add(new Label(\"ti\" + \"tle\"));")
        val before = java.text
        val first = analyze(java)
        val second = WicketPageAnalyzer().analyze(java.classes.single())!!
        matched(first)
        assertEquals(before, java.text)
        assertEquals(first, second)
        val exported = WicketContextExporter().export(first)
        assertEquals(exported, WicketContextExporter().export(second))
        assertFalse(exported.contains("computedId"))
        assertFalse(exported.contains("idUsedInComputation"))
    }

    fun testDemoCombinesConfiguredExpressionsAndInitialization() {
        val root = java.nio.file.Path.of(System.getProperty("wicketlens.test.demo"))
        for (name in listOf("ExpressionParts", "ExpressionSetupPage")) {
            myFixture.addFileToProject("src/main/java/example/$name.java", java.nio.file.Files.readString(root.resolve("main/java/example/$name.java")))
        }
        companion("ExpressionSetupPage", java.nio.file.Files.readString(root.resolve("main/resources/example/ExpressionSetupPage.html")))
        val java = com.intellij.psi.PsiManager.getInstance(project).findFile(myFixture.findFileInTempDir("src/main/java/example/ExpressionSetupPage.java")) as PsiJavaFile
        val report = WicketPageAnalyzer().analyze(java.classes.single())!!
        matched(report, setOf("headingTitle", "item3", "status", "form", "name"))
        assertEquals(2, report.javaSourceFiles.size)
        assertEquals(2, report.children().count { it.computedId })
    }

    private fun settings() = source("Settings", """
        public class Settings {
            private final String prefix; private final int size;
            public Settings(String prefix, int size) { this.prefix = prefix; this.size = size; }
            public String id(String suffix) { return prefix + suffix; }
            public boolean enabled() { return size >= 2; }
        }
    """)
    private fun source(name: String, declaration: String) = myFixture.addFileToProject("src/main/java/example/$name.java", "package example; import org.apache.wicket.markup.html.basic.Label; $declaration")
    private fun page(body: String, members: String = "", parameters: String = "") = source("ExamplePage", "public class ExamplePage extends org.apache.wicket.markup.html.WebPage { public ExamplePage($parameters) { $body } $members }") as PsiJavaFile
    private fun analyze(java: PsiJavaFile, html: String = "<span wicket:id=\"title\"></span>"): PageReport {
        companion(html = html)
        return WicketPageAnalyzer().analyze(java.classes.single())!!
    }
    private fun report(body: String, members: String = "", parameters: String = "") = analyze(page(body, members, parameters))
    private fun matched(report: PageReport, ids: Set<String> = setOf("title")) {
        assertEquals(ids, report.children().map { it.wicketId }.toSet())
        assertEquals(report.matching!!.findings.map { it.code }.toString(), ids.size, report.matching.findings.count { it.code == DiagnosticCode.MATCHED })
        assertTrue(report.warnings.toString(), report.warnings.none { it.code == DiagnosticCode.UNRESOLVED_SCOPE })
    }
    private fun uncertain(report: PageReport) = assertTrue(report.matching!!.findings.map { it.code }.toString(),
        report.matching.findings.none { it.code in setOf(DiagnosticCode.MATCHED, DiagnosticCode.JAVA_ONLY, DiagnosticCode.MARKUP_ONLY) })
}
