package dev.wicketlens

import com.intellij.psi.PsiJavaFile
import dev.wicketlens.analysis.WicketPageAnalyzer
import dev.wicketlens.export.WicketContextExporter
import dev.wicketlens.model.*

class WicketBitwiseExpressionTest : WicketProjectPsiTestCase() {
    fun testLeftShiftSelectsAProvenBranch() = matched(report("if ((1 << 3) == 8) add(new Label(\"title\")); else removeAll();"))
    fun testSignedRightShiftExtendsTheSignBit() = matched(report("if ((-8 >> 2) == -2) add(new Label(\"title\"));"))
    fun testUnsignedIntRightShiftFillsWithZeros() = matched(report("if ((-1 >>> 1) == 2147483647) add(new Label(\"title\"));"))
    fun testUnsignedLongRightShiftFillsWithZeros() = matched(report("if ((-1L >>> 1) == 9223372036854775807L) add(new Label(\"title\"));"))

    fun testIntShiftDistanceUsesOnlyFiveLowBits() = matched(report("if ((1 << 32) == 1 && (1 << 33) == 2 && (8 >> 34) == 2 && (-1 >>> 32) == -1) add(new Label(\"title\"));"))
    fun testLongShiftDistanceUsesOnlySixLowBits() = matched(report("if ((1L << 64) == 1L && (1L << 65) == 2L && (8L >> 66) == 2L && (-1L >>> 64) == -1L) add(new Label(\"title\"));"))
    fun testNegativeShiftDistancesAreMaskedRatherThanRejected() = matched(report("if ((1 << -1) == -2147483648 && (1L << -1) == -9223372036854775808L && (-1 >>> -1) == 1) add(new Label(\"title\"));"))
    fun testLongRightOperandDoesNotPromoteAnIntLeftOperand() = matched(report("if ((1 << 32L) == 1 && (1 << 31L) == -2147483648 && (-1 >>> 1L) == 2147483647) add(new Label(\"title\"));"))

    fun testNarrowLeftOperandsPromoteToIntBeforeShifting() = matched(report("byte b = -1; short s = -8; char c = 65535; if ((b >>> 1) == 2147483647 && (s >> 1) == -4 && (c << 16) == -65536) add(new Label(\"title\"));"))
    fun testNarrowRightOperandsUseTheirPromotedShiftDistance() = matched(report("char c = 33; byte b = -1; short s = 64; if ((1 << c) == 2 && (1L << b) == -9223372036854775808L && (1L << s) == 1L) add(new Label(\"title\"));"))
    fun testChainedShiftsAssociateToTheLeft() = matched(report("if ((64 >> 1 >> 2) == 8 && (1 << 2 << 3) == 32) add(new Label(\"title\"));"))

    fun testIntegralAndOrXorPreserveTheirBitPatterns() = matched(report("if ((6 & 3) == 2 && (4 | 3) == 7 && (7 ^ 3) == 4) add(new Label(\"title\"));"))
    fun testMixedLongAndIntBitwiseOperandsPromoteWithoutLosingBits() = matched(report("if ((9007199254740993L & 1) == 1L && (4294967296L | 1) == 4294967297L && (4294967297L ^ 1) == 4294967296L) add(new Label(\"title\"));"))
    fun testIntComplementUsesAllThirtyTwoBits() = matched(report("if (~0 == -1 && ~(-1) == 0 && ~2147483647 == -2147483648) add(new Label(\"title\"));"))
    fun testLongComplementUsesAllSixtyFourBits() = matched(report("if (~0L == -1L && ~9223372036854775807L == -9223372036854775808L) add(new Label(\"title\"));"))
    fun testComplementPromotesNarrowValuesBeforeInverting() = matched(report("byte b = -1; short s = 1; char c = 0; if (~b == 0 && ~s == -2 && ~c == -1) add(new Label(\"title\"));"))
    fun testHexMinimumLiteralsAreValidComplementOperands() = matched(report("if (~0x80000000 == 2147483647 && ~0x8000000000000000L == 9223372036854775807L) add(new Label(\"title\"));"))
    fun testOutOfRangeIntDecimalComplementOperandCannotProveABinding() = uncertain(report("if (~2147483648 == 2147483647) add(new Label(\"title\"));"))
    fun testOutOfRangeLongDecimalComplementOperandCannotProveABinding() = uncertain(report("if (~9223372036854775808L == 9223372036854775807L) add(new Label(\"title\"));"))
    fun testOutOfRangeDecimalShiftLeftOperandCannotProveABinding() = uncertain(report("if ((2147483648 >>> 1) == 1073741824) add(new Label(\"title\"));"))
    fun testOutOfRangeDecimalShiftDistanceCannotProveABinding() = uncertain(report("if ((1 << 2147483648) == 1) add(new Label(\"title\"));"))
    fun testParenthesizedPositiveMinimumCannotUseTheUnaryMinusException() = uncertain(report("if (-(2147483648) == -2147483648) add(new Label(\"title\"));"))
    fun testNegativeMinimumValuesRemainValidBitwiseOperands() = matched(report("if ((-2147483648 >>> 31) == 1 && (-9223372036854775808L >>> 63) == 1L) add(new Label(\"title\"));"))
    fun testArithmeticShiftAndBitwisePrecedenceFollowJava() = matched(report("if ((1 + 1 << 2) == 8 && (1 | 2 & 4) == 1 && (1 ^ 3 & 2) == 3) add(new Label(\"title\"));"))

    fun testShiftBasedComputedIdKeepsWholeExpressionProvenance() {
        val java = page("add(new Label(\"item\" + (1 << 1)));")
        val report = analyze(java, "<span wicket:id=\"item2\"></span>")
        matched(report, setOf("item2"))
        val node = report.children().single()
        assertTrue(node.computedId)
        assertFalse(node.idRenameSafe)
        assertEquals(java.text.indexOf("\"item\""), node.idLocation!!.offset)
    }

    fun testComplementAndMaskCanSupplyACalculatedId() = matched(analyze(page("add(new Label(\"item\" + (~(-4) & 7)));"), "<span wicket:id=\"item3\"></span>"), setOf("item3"))
    fun testMaskedLocalConfigurationCanSelectAComponent() = matched(report("int flags = (1 << 1) | 1; if ((flags & 2) != 0) add(new Label(\"title\"));"))
    fun testReadOnlyScalarGetterCanReturnABitwiseResult() = matched(report("if (index(6) == 3) add(new Label(\"title\"));", "private int index(int flags) { return (flags >>> 1) ^ 0; }"))

    fun testSourceConstantsCanUseShiftsMasksAndComplement() {
        constants("public static final int FIRST = 1 << 1; public static final int FLAGS = FIRST | 1; public static final int INVERSE = ~FLAGS; public static final String TITLE = \"item\" + ((FLAGS >>> 1) ^ 3);")
        matched(analyze(page("if (Flags.INVERSE < 0) add(new Label(Flags.TITLE));"), "<span wicket:id=\"item2\"></span>"), setOf("item2"))
    }

    fun testSourceBooleanConstantsCanUseEagerLogicalOperators() {
        constants("public static final boolean ENABLED = (true & true) | (false ^ false);")
        matched(report("if (Flags.ENABLED) add(new Label(\"title\"));"))
    }

    fun testOutOfRangeComplementInASourceConstantCannotProveABinding() {
        constants("public static final int MASK = ~2147483648;")
        uncertain(report("if (Flags.MASK > 0) add(new Label(\"title\"));"))
    }

    fun testEagerBooleanOperatorsHaveTheirJavaTruthTables() = matched(report("if ((true & true) && !(true & false) && !(false & true) && !(false & false) && (false | true) && (true | false) && (true | true) && !(false | false) && (true ^ false) && (false ^ true) && !(true ^ true) && !(false ^ false)) add(new Label(\"title\"));"))

    fun testEagerAndCanProveFalseAfterAuditingAnUnknownPrimitive() {
        val report = report("if (enabled & false) add(new Label(\"title\"));", parameters = "boolean enabled")
        assertTrue(report.children().isEmpty())
        assertEquals(DiagnosticCode.MARKUP_ONLY, report.matching!!.findings.single().code)
    }

    fun testEagerOrCanProveTrueAfterAuditingAnUnknownPrimitive() = matched(report("if (enabled | true) add(new Label(\"title\"));", parameters = "boolean enabled"))
    fun testEagerXorOfAnUnknownPrimitiveRemainsUnknown() = uncertain(report("if (enabled ^ true) add(new Label(\"title\"));", parameters = "boolean enabled"))
    fun testFalseEagerAndDoesNotSkipAnEffectfulRightOperand() = uncertain(report("add(new Label(\"title\")); if (false & effect()) removeAll();", effect()))
    fun testTrueEagerOrDoesNotSkipAnEffectfulRightOperand() = uncertain(report("add(new Label(\"title\")); if (true | effect()) removeAll();", effect()))
    fun testEagerPolyadicAndInspectsEveryOperand() = uncertain(report("add(new Label(\"title\")); if (false & enabled & effect()) removeAll();", effect(), "boolean enabled"))
    fun testEagerPolyadicOrInspectsEveryOperand() = uncertain(report("add(new Label(\"title\")); if (true | enabled | effect()) removeAll();", effect(), "boolean enabled"))
    fun testEagerBooleanResultCannotHideAnUnknownDivisor() = uncertain(report("if (true | (1 / divisor > 0)) add(new Label(\"title\"));", parameters = "int divisor"))
    fun testEagerBooleanResultCannotHideDivisionByZero() = uncertain(report("if (false & (1 / 0 > 0)) add(new Label(\"title\"));"))
    fun testBoxedBooleanCannotBeAssumedSafeToUnbox() = uncertain(report("if (enabled | true) add(new Label(\"title\"));", parameters = "Boolean enabled"))
    fun testBoxedIntegralBitwiseOperandsRemainUnresolved() = uncertain(report("if ((flags & 0) == 0) add(new Label(\"title\"));", parameters = "Integer flags"))
    fun testBoxedLeftShiftOperandRemainsUnresolved() = uncertain(report("if ((flags << 1) > 0 || true) add(new Label(\"title\"));", parameters = "Integer flags"))
    fun testBoxedShiftDistanceRemainsUnresolved() = uncertain(report("if ((1 << distance) > 0 || true) add(new Label(\"title\"));", parameters = "Integer distance"))
    fun testBoxedComplementOperandRemainsUnresolved() = uncertain(report("if (~flags != 0 || true) add(new Label(\"title\"));", parameters = "Integer flags"))
    fun testFloatingPointBitwiseOperandsAreRejected() = uncertain(report("if ((1.0 & 1) == 0) add(new Label(\"title\"));"))
    fun testMixedBooleanAndIntegralOperandsAreRejected() = uncertain(report("if ((enabled & 1) == 0 || true) add(new Label(\"title\"));", parameters = "boolean enabled"))
    fun testStringBitwiseOperandsAreRejected() = uncertain(report("if ((\"a\" | \"b\").equals(\"a\")) add(new Label(\"title\"));"))
    fun testCompoundShiftAssignmentStaysOutsideTheMutationContract() = uncertain(report("int flags = 1; flags <<= 1; if (flags == 2) add(new Label(\"title\"));"))
    fun testIncrementInsideAShiftDistanceRemainsUnsupported() = uncertain(report("int distance = 1; if ((1 << distance++) == 2) add(new Label(\"title\"));"))
    fun testMutableInstanceFlagsDoNotBecomeStableConfiguration() = uncertain(report("if ((flags & 2) != 0) add(new Label(\"title\"));", "private int flags = 3;"))
    fun testUnknownNumericMasksAreNotGuessedEvenForAZeroMask() = uncertain(report("if ((flags & 0) == 0) add(new Label(\"title\"));", parameters = "int flags"))
    fun testSafeUnknownShiftCanStillHaveAnIndependentLogicalResult() = matched(report("if ((flags >>> distance) > 0 || true) add(new Label(\"title\"));", parameters = "int flags, long distance"))

    fun testSourceConstantEagerOperandsMustAllBeConstantExpressions() {
        constants("public static boolean UNKNOWN = false; public static final boolean ENABLED = UNKNOWN | true;")
        uncertain(report("if (Flags.ENABLED) add(new Label(\"title\"));"))
    }

    fun testDeepComplementExpressionsRetainTheExpressionBudget() = uncertain(report("if (${"~".repeat(16)}0 == 0) add(new Label(\"title\"));"))

    fun testBitwiseAnalysisAndExportsAreReadOnlyAndDeterministic() {
        val java = page("if (((1 << 3) & 8) != 0) add(new Label(\"title\"));")
        companion()
        val before = java.text
        val first = WicketPageAnalyzer().analyze(java.classes.single())!!
        val second = WicketPageAnalyzer().analyze(java.classes.single())!!
        matched(first)
        assertEquals(before, java.text)
        assertEquals(first, second)
        assertEquals(WicketContextExporter().export(first), WicketContextExporter().export(second))
    }

    fun testDemoCombinesConstantFlagsProvidersAndInitialization() {
        val root = java.nio.file.Path.of(System.getProperty("wicketlens.test.demo"))
        for (name in listOf("BitwiseFlags", "BitwiseSetupPage")) {
            myFixture.addFileToProject("src/main/java/example/$name.java", java.nio.file.Files.readString(root.resolve("main/java/example/$name.java")))
        }
        companion("BitwiseSetupPage", java.nio.file.Files.readString(root.resolve("main/resources/example/BitwiseSetupPage.html")))
        val file = com.intellij.psi.PsiManager.getInstance(project).findFile(myFixture.findFileInTempDir("src/main/java/example/BitwiseSetupPage.java")) as PsiJavaFile
        val report = WicketPageAnalyzer().analyze(file.classes.single())!!
        matched(report, setOf("title", "item3", "form", "name", "status"))
        assertEquals(2, report.javaSourceFiles.size)
        assertEquals(1, report.children().count { it.computedId })
    }

    private fun constants(members: String) = source("Flags", "public class Flags { $members }")
    private fun effect() = "private boolean effect() { removeAll(); return true; }"
    private fun source(name: String, declaration: String) = myFixture.addFileToProject("src/main/java/example/$name.java", "package example; $declaration")
    private fun page(body: String, members: String = "", parameters: String = "") = source("ExamplePage", "import org.apache.wicket.markup.html.basic.Label; public class ExamplePage extends org.apache.wicket.markup.html.WebPage { public ExamplePage($parameters) { $body } $members }") as PsiJavaFile
    private fun analyze(java: PsiJavaFile, html: String = "<span wicket:id=\"title\"></span>"): PageReport {
        companion(html = html)
        return WicketPageAnalyzer().analyze(java.classes.single())!!
    }
    private fun report(body: String, members: String = "", parameters: String = "") = analyze(page(body, members, parameters))
    private fun matched(report: PageReport, ids: Set<String> = setOf("title")) {
        assertEquals(ids, report.children().map { it.wicketId }.toSet())
        assertEquals(report.matching!!.findings.toString(), ids.size, report.matching.findings.count { it.code == DiagnosticCode.MATCHED })
        assertTrue(report.warnings.toString(), report.warnings.none { it.code == DiagnosticCode.UNRESOLVED_SCOPE })
    }
    private fun uncertain(report: PageReport) = assertTrue(report.matching!!.findings.toString(),
        report.matching.findings.none { it.code in setOf(DiagnosticCode.MATCHED, DiagnosticCode.JAVA_ONLY, DiagnosticCode.MARKUP_ONLY) })
}
