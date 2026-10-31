package dev.wicketlens

import com.intellij.psi.PsiJavaFile
import dev.wicketlens.analysis.WicketPageAnalyzer
import dev.wicketlens.export.WicketContextExporter
import dev.wicketlens.model.*

class WicketStaticConstantTest : WicketProjectPsiTestCase() {
    fun testOwnPrivateStringConstantResolvesAtTheUseExpression() {
        val java = page("add(new Label(TITLE));", "private static final String TITLE = \"title\";")
        val report = analyze(java)
        matched(report)
        val node = report.children().single()
        assertTrue(node.computedId)
        assertFalse(node.idRenameSafe)
        assertEquals(java.text.indexOf("TITLE));"), node.idLocation!!.offset)
    }

    fun testExternalPublicConstantAddsItsSourceDependency() {
        val ids = ids()
        val report = report("add(new Label(Ids.TITLE));")
        matched(report)
        assertTrue(ids.virtualFile.path in report.javaSourceFiles)
        assertEquals(2, report.javaSourceFiles.size)
    }

    fun testFullyQualifiedConstantUsesTheResolvedDeclaration() {
        ids()
        matched(report("add(new Label(example.Ids.TITLE));"))
    }

    fun testSingleStaticImportResolvesItsConstant() {
        ids()
        matched(analyze(page("add(new Label(TITLE));", imports = "import static example.Ids.TITLE;")))
    }

    fun testWildcardStaticImportResolvesItsConstant() {
        ids()
        matched(analyze(page("add(new Label(TITLE));", imports = "import static example.Ids.*;")))
    }

    fun testInterfaceConstantsHaveImplicitStaticFinalModifiers() {
        source("Ids", "public interface Ids { String TITLE = \"title\"; }")
        matched(report("add(new Label(Ids.TITLE));"))
    }

    fun testInheritedPageConstantIsResolvedWithoutAnInstanceFieldEnvironment() {
        source("BasePage", "public class BasePage extends org.apache.wicket.markup.html.WebPage { protected static final String TITLE = \"title\"; }")
        matched(analyze(page("add(new Label(TITLE));", base = "BasePage")))
    }

    fun testQualifiedInheritedConstantIncludesIntermediateSourceTypes() {
        ids()
        val middle = source("MiddleIds", "public class MiddleIds extends Ids {}")
        val alias = source("AliasIds", "public class AliasIds extends MiddleIds {}")
        val report = report("add(new Label(AliasIds.TITLE));")
        matched(report)
        assertTrue(report.javaSourceFiles.containsAll(listOf(middle.virtualFile.path, alias.virtualFile.path)))
        assertEquals(4, report.javaSourceFiles.size)
    }

    fun testStaticImportOfInheritedConstantIncludesItsImportTarget() {
        ids()
        val alias = source("AliasIds", "public class AliasIds extends Ids {}")
        val report = analyze(page("add(new Label(TITLE));", imports = "import static example.AliasIds.TITLE;"))
        matched(report)
        assertTrue(alias.virtualFile.path in report.javaSourceFiles)
    }

    fun testUnqualifiedInterfaceLookupTracksIntermediateInterfaces() {
        source("BaseIds", "public interface BaseIds { String TITLE = \"title\"; }")
        val alias = source("Ids", "public interface Ids extends BaseIds {}")
        val report = analyze(page("add(new Label(TITLE));", base = "org.apache.wicket.markup.html.WebPage implements Ids"))
        matched(report)
        assertTrue(alias.virtualFile.path in report.javaSourceFiles)
    }

    fun testCrossFileConstantExpressionsPreserveStringAndIntegralValues() {
        source("Numbers", "public class Numbers { public static final byte INDEX = 2; }")
        ids("public static final String TITLE = \"item\" + (Numbers.INDEX + 1);")
        matched(analyze(page("add(new Label(Ids.TITLE));"), "<span wicket:id=\"item3\"></span>"), setOf("item3"))
    }

    fun testOwnComputedConstantInitializerIsAuditedBeforeUse() {
        matched(report("add(new Label(TITLE));", "private static final String PREFIX = \"ti\"; private static final String TITLE = PREFIX + \"tle\";"))
    }

    fun testBooleanAndIntegralConstantsCanSelectASetupBranch() {
        ids("public static final boolean ENABLED = !false && (2 + 3 >= 5); public static final int COUNT = 2;")
        matched(report("if (Ids.ENABLED && Ids.COUNT > 0) add(new Label(\"title\")); else removeAll();"))
    }

    fun testFalseConstantCanProveAMarkupOnlyFinding() {
        ids("public static final boolean ENABLED = false;")
        val report = report("if (Ids.ENABLED) add(new Label(\"title\"));")
        assertTrue(report.children().isEmpty())
        assertEquals(DiagnosticCode.MARKUP_ONLY, report.matching!!.findings.single().code)
    }

    fun testConditionalConstantRequiresAndSelectsKnownArms() {
        ids("public static final String TITLE = true ? \"title\" : \"unused\";")
        matched(report("add(new Label(Ids.TITLE));"))
    }

    fun testIntegralCastsPromotionAndOverflowFollowJava() {
        ids("public static final byte INDEX = (byte)257; public static final int OVERFLOW = 2147483647 + 1; public static final long SIZE = 2147483647L + 1;")
        matched(report("if (Ids.INDEX == 1 && Ids.OVERFLOW < 0 && Ids.SIZE > 2147483647) add(new Label(\"title\"));"))
    }

    fun testCharConstantsRetainTheirCharTypeInConcatenation() {
        ids("public static final char LETTER = 116; public static final String TITLE = LETTER + \"itle\";")
        matched(report("add(new Label(Ids.TITLE));"))
    }

    fun testMinimumIntegralConstantsKeepTheirLeadingMinus() {
        ids("public static final int MIN = -2147483648; public static final long LONG_MIN = -9223372036854775808L;")
        matched(report("if (Ids.MIN < 0 && Ids.LONG_MIN < 0L) add(new Label(\"title\"));"))
    }

    fun testQualifiedForwardReferenceCanNameALaterConstant() {
        ids("public static final String TITLE = Ids.LATER; public static final String LATER = \"title\";")
        matched(report("add(new Label(Ids.TITLE));"))
    }

    fun testKnownProviderConfigurationCanComeFromAStaticConstant() {
        ids()
        source("Parts", "public class Parts { private final String id; public Parts(String id) { this.id = id; } public org.apache.wicket.markup.html.basic.Label build() { return new org.apache.wicket.markup.html.basic.Label(id); } }")
        matched(report("add(new Parts(Ids.TITLE).build());"))
    }

    fun testSourceStaticFactoryCanOwnAComputedConstant() {
        source("Parts", "public class Parts { private static final String TITLE = \"ti\" + \"tle\"; public static org.apache.wicket.markup.html.basic.Label build() { return new org.apache.wicket.markup.html.basic.Label(TITLE); } }")
        matched(report("add(Parts.build());"))
    }

    fun testSourceScalarGetterCanReturnAConstant() {
        matched(report("add(new Label(id()));", "private static final String TITLE = \"title\"; private String id() { return TITLE; }"))
    }

    fun testConstantReadDoesNotInitializeAnUninstantiatedExternalHolder() {
        ids("public static final String TITLE = \"title\"; static { System.setProperty(\"wicketlens.test.constant.holder\", \"must-not-run\"); }")
        matched(report("add(new Label(Ids.TITLE));"))
        assertNull(System.getProperty("wicketlens.test.constant.holder"))
    }

    fun testAnInstantiatedProviderStillRejectsUnknownStaticInitialization() {
        source("Parts", "public class Parts { public static final String TITLE = \"title\"; static { System.setProperty(\"effect\", \"yes\"); } public org.apache.wicket.markup.html.basic.Label build() { return new org.apache.wicket.markup.html.basic.Label(TITLE); } }")
        uncertain(report("add(new Parts().build());"))
    }

    fun testRootStaticInitializerPreventsConstantBasedConfirmation() {
        uncertain(report("add(new Label(TITLE));", "private static final String TITLE = \"title\"; static { System.setProperty(\"effect\", \"yes\"); }"))
    }

    fun testRootNonconstantStaticFieldInitializerIsNotIgnored() {
        uncertain(report("add(new Label(TITLE));", "private static final String TITLE = \"title\"; private static final String OTHER = System.getProperty(\"config\");"))
    }

    fun testMutableStaticFieldDoesNotBecomeAConstant() {
        ids("public static String TITLE = \"title\";")
        uncertain(report("add(new Label(Ids.TITLE));"))
    }

    fun testFinalFieldInitializedByAMethodCallIsNotAConstantVariable() {
        ids("public static final String TITLE = id(); private static String id() { return \"title\"; }")
        uncertain(report("add(new Label(Ids.TITLE));"))
    }

    fun testFinalNewStringIsNotAConstantVariable() {
        ids("public static final String TITLE = new String(\"title\");")
        uncertain(report("add(new Label(Ids.TITLE));"))
    }

    fun testMutableInputsCannotHideBehindAFinalField() {
        ids("private static int count = 1; public static final boolean ENABLED = count > 0;")
        uncertain(report("if (Ids.ENABLED) add(new Label(\"title\"));"))
    }

    fun testStaticBlockAssignmentDoesNotBecomeAConstantVariable() {
        ids("public static final String TITLE; static { TITLE = \"title\"; }")
        uncertain(report("add(new Label(Ids.TITLE));"))
    }

    fun testBoxedStaticFinalFieldsStayUnresolved() {
        ids("public static final Boolean ENABLED = true;")
        uncertain(report("if (Ids.ENABLED) add(new Label(\"title\"));"))
    }

    fun testFloatingPointConstantsStayOutsideTheReviewedSubset() {
        ids("public static final double COUNT = 1.0;")
        uncertain(report("if (Ids.COUNT > 0) add(new Label(\"title\"));"))
    }

    fun testNullStringIsNotAConstantVariable() {
        ids("public static final String TITLE = null;")
        uncertain(report("add(new Label(Ids.TITLE));"))
    }

    fun testExpressionQualifierEffectsCannotBeSkippedForAStaticConstant() {
        ids()
        uncertain(report("add(new Label(holder().TITLE));", "private Ids holder() { removeAll(); return new Ids(); }"))
    }

    fun testInstanceQualifiedConstantStaysOutsideTheNameOnlySubset() {
        uncertain(report("add(new Label(this.TITLE));", "private static final String TITLE = \"title\";"))
    }

    fun testCompiledIntegralConstantsCanSelectAReviewedBranch() {
        matched(report("if (Integer.MAX_VALUE > 0) add(new Label(\"title\"));"))
    }

    fun testInaccessibleConstantDoesNotProduceAConfirmedBinding() {
        ids("private static final String TITLE = \"title\";")
        uncertain(report("add(new Label(Ids.TITLE));"))
    }

    fun testIllegalSimpleNameForwardReferenceIsRejected() {
        ids("public static final String TITLE = LATER; public static final String LATER = \"title\";")
        uncertain(report("add(new Label(Ids.TITLE));"))
    }

    fun testCyclicQualifiedConstantsCannotOverflowOrGuessAValue() {
        ids("public static final String TITLE = Ids.OTHER; public static final String OTHER = Ids.TITLE;")
        uncertain(report("add(new Label(Ids.TITLE));"))
    }

    fun testInvalidConstantNarrowingIsNotSilentlyWrapped() {
        ids("public static final byte COUNT = 257;")
        uncertain(report("if (Ids.COUNT == 1) add(new Label(\"title\"));"))
    }

    fun testSkippedNonconstantConditionalArmIsStillRejected() {
        ids("public static final String TITLE = true ? \"title\" : System.getProperty(\"config\");")
        uncertain(report("add(new Label(Ids.TITLE));"))
    }

    fun testShortCircuitedOpaqueOperandIsStillNotAConstantVariable() {
        ids("public static final boolean ENABLED = true || Boolean.getBoolean(\"config\");")
        uncertain(report("if (Ids.ENABLED) add(new Label(\"title\"));"))
    }

    fun testDivisionByZeroDoesNotProduceAProvenCondition() {
        ids("public static final int COUNT = 1 / 0;")
        uncertain(report("if (Ids.COUNT > 0 || true) add(new Label(\"title\"));"))
    }

    fun testDeepConstantChainsAreBounded() {
        ids((0..15).joinToString(" ") { "public static final String ${if (it == 0) "TITLE" else "ID$it"} = ${if (it == 15) "\"title\"" else "Ids.ID${it + 1}"};" })
        uncertain(report("add(new Label(Ids.TITLE));"))
    }

    fun testTwelveConstantDeclarationsRemainWithinTheChainBudget() {
        ids((0..11).joinToString(" ") { "public static final String ${if (it == 0) "TITLE" else "ID$it"} = ${if (it == 11) "\"title\"" else "Ids.ID${it + 1}"};" })
        matched(report("add(new Label(Ids.TITLE));"))
    }

    fun testDeepInitializerExpressionsAreBounded() {
        ids("public static final boolean ENABLED = ${"!".repeat(16)}true;")
        uncertain(report("if (Ids.ENABLED) add(new Label(\"title\"));"))
    }

    fun testConstantLookupCannotExceedTheSourceFileBudget() {
        for (index in 0..23) source("Part$index", "public class Part$index { public static final String VALUE = \"a\"; }")
        ids("public static final String TITLE = ${(0..23).joinToString(" + ") { "Part$it.VALUE" }};")
        uncertain(report("add(new Label(Ids.TITLE));"))
    }

    fun testAmbiguousStaticImportsCannotSelectAConstantByValue() {
        ids()
        source("OtherIds", "public class OtherIds { public static final String TITLE = \"title\"; }")
        uncertain(analyze(page("add(new Label(TITLE));", imports = "import static example.Ids.*; import static example.OtherIds.*;")))
    }

    fun testDivisionByZeroInAnUnusedConstantArmIsConservativelyRejected() {
        ids("public static final int COUNT = true ? 1 : 1 / 0;")
        uncertain(report("if (Ids.COUNT > 0) add(new Label(\"title\"));"))
    }

    fun testEmptyAndReservedConstantIdsRetainTheirInvalidIdDiagnostics() {
        ids("public static final String EMPTY = \"\"; public static final String RESERVED = \"a:b\";")
        val report = report("add(new Label(Ids.EMPTY), new Label(Ids.RESERVED));")
        assertEquals(2, report.matching!!.findings.count { it.code == DiagnosticCode.UNRESOLVED_ID })
        assertTrue(report.children().all { it.computedId && !it.idRenameSafe })
    }

    fun testOversizedConstantStringsAreBounded() {
        ids("public static final String TITLE = \"${"a".repeat(4097)}\";")
        uncertain(report("add(new Label(Ids.TITLE));"))
    }

    fun testReadOnlyAnalysisAndExportsRemainDeterministic() {
        val constants = ids()
        val java = page("add(new Label(Ids.TITLE));")
        companion()
        val before = java.text to constants.text
        val first = WicketPageAnalyzer().analyze(java.classes.single())!!
        val second = WicketPageAnalyzer().analyze(java.classes.single())!!
        matched(first)
        assertEquals(first, second)
        assertEquals(before, java.text to constants.text)
        assertEquals(WicketContextExporter().export(first), WicketContextExporter().export(second))
        assertFalse(WicketContextExporter().export(first).contains("computedId"))
    }

    fun testDemoCombinesStaticImportsConditionsAndInitialization() {
        val root = java.nio.file.Path.of(System.getProperty("wicketlens.test.demo"))
        for (name in listOf("ConstantIds", "ConstantSetupPage")) {
            myFixture.addFileToProject("src/main/java/example/$name.java", java.nio.file.Files.readString(root.resolve("main/java/example/$name.java")))
        }
        companion("ConstantSetupPage", java.nio.file.Files.readString(root.resolve("main/resources/example/ConstantSetupPage.html")))
        val file = com.intellij.psi.PsiManager.getInstance(project).findFile(myFixture.findFileInTempDir("src/main/java/example/ConstantSetupPage.java")) as PsiJavaFile
        val report = WicketPageAnalyzer().analyze(file.classes.single())!!
        matched(report, setOf("title", "item3", "form", "name", "status"))
        assertEquals(2, report.javaSourceFiles.size)
        assertTrue(report.children().all { it.computedId && !it.idRenameSafe })
    }

    private fun ids(members: String = "public static final String TITLE = \"title\";") = source("Ids", "public class Ids { $members }")
    private fun source(name: String, declaration: String) = myFixture.addFileToProject("src/main/java/example/$name.java", "package example; $declaration")
    private fun page(body: String, members: String = "", imports: String = "", base: String = "org.apache.wicket.markup.html.WebPage") =
        source("ExamplePage", "$imports import org.apache.wicket.markup.html.basic.Label; public class ExamplePage extends $base { public ExamplePage() { $body } $members }") as PsiJavaFile
    private fun analyze(java: PsiJavaFile, html: String = "<span wicket:id=\"title\"></span>"): PageReport {
        companion(html = html)
        return WicketPageAnalyzer().analyze(java.classes.single())!!
    }
    private fun report(body: String, members: String = "") = analyze(page(body, members))
    private fun matched(report: PageReport, ids: Set<String> = setOf("title")) {
        assertEquals(ids, report.children().map { it.wicketId }.toSet())
        assertEquals(report.matching!!.findings.toString(), ids.size, report.matching.findings.count { it.code == DiagnosticCode.MATCHED })
        assertTrue(report.warnings.toString(), report.warnings.none { it.code == DiagnosticCode.UNRESOLVED_SCOPE })
    }
    private fun uncertain(report: PageReport) = assertTrue(report.matching!!.findings.toString(),
        report.matching.findings.none { it.code in setOf(DiagnosticCode.MATCHED, DiagnosticCode.JAVA_ONLY, DiagnosticCode.MARKUP_ONLY) })
}
