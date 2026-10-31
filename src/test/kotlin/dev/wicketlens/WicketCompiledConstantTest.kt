package dev.wicketlens

import com.intellij.openapi.roots.DependencyScope
import com.intellij.openapi.roots.LibraryOrderEntry
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.psi.*
import dev.wicketlens.analysis.WicketPageAnalyzer
import dev.wicketlens.export.WicketContextExporter
import dev.wicketlens.model.DiagnosticCode

class WicketCompiledConstantTest : WicketCompiledConstantTestCase() {
    fun testBinaryStringKeepsTheUseExpressionAndClassDependency() {
        val report = report("add(new Label(lib.Ids.TITLE));")
        matched(report)
        val node = report.children().single()
        assertTrue(node.computedId)
        assertFalse(node.idRenameSafe)
        assertTrue(report.javaSourceFiles.any { it.endsWith("constants.jar!/lib/Ids.class") })
    }
    fun testSingleStaticImport() { matched(report("add(new Label(TITLE));", imports = "import static lib.Ids.TITLE;")) }
    fun testWildcardStaticImport() { matched(report("add(new Label(TITLE));", imports = "import static lib.Ids.*;")) }
    fun testBinaryInterfaceConstant() { matched(report("add(new Label(lib.Constants.TITLE));")) }
    fun testNestedBinaryClass() { matched(report("add(new Label(lib.Ids.Nested.TITLE));")) }
    fun testBinaryAliasTracksLookupAndDeclaringClasses() {
        val report = report("add(new Label(lib.Alias.TITLE));")
        matched(report)
        assertTrue(report.javaSourceFiles.any { it.endsWith("!/lib/Alias.class") })
        assertTrue(report.javaSourceFiles.any { it.endsWith("!/lib/Ids.class") })
    }
    fun testSourceAliasRetainsBothKindsOfDependencies() {
        val alias = myFixture.addFileToProject("src/main/java/example/Alias.java", "package example; public class Alias extends lib.Ids {}")
        val report = report("add(new Label(Alias.TITLE));")
        matched(report)
        assertTrue(alias.virtualFile.path in report.javaSourceFiles)
        assertTrue(report.javaSourceFiles.any { it.endsWith("!/lib/Ids.class") })
    }
    fun testBinaryFlagsAndArithmeticSelectAProvenBranch() {
        matched(report("if (lib.Ids.ENABLED && lib.Ids.COUNT == 2) add(new Label(\"title\")); else removeAll();"))
    }
    fun testFalseFlagProvesMarkupOnly() {
        val report = report("if (lib.Ids.DISABLED) add(new Label(\"title\"));")
        assertEquals(DiagnosticCode.MARKUP_ONLY, report.matching!!.findings.single().code)
        assertTrue(report.warnings.none { it.code == DiagnosticCode.UNRESOLVED_SCOPE })
    }
    fun testAllSupportedIntegralMetadataTypes() {
        matched(report("add(new Label(\"\" + lib.Ids.BYTE + lib.Ids.SHORT + lib.Ids.LETTER + lib.Ids.COUNT + lib.Ids.LARGE));", "-23t24294967296"), "-23t24294967296")
    }
    fun testJdkConstantsCanSupplyBitwiseAndOverflowCalculations() {
        matched(report("if ((Integer.MAX_VALUE + 1) == Integer.MIN_VALUE && (Long.SIZE >>> 3) == 8) add(new Label(\"title\"));"))
    }
    fun testJdkStringConstants() { matched(report("add(new Label(javax.xml.XMLConstants.XML_NS_PREFIX));", "xml"), "xml") }
    fun testSourceConstantCanCombineBinaryConstants() {
        matched(report("add(new Label(TITLE));", members = "private static final String TITLE = \"item\" + (lib.Ids.COUNT + 1);", id = "item3"), "item3")
    }
    fun testBinaryConstantCanConfigureAClosedHelper() {
        matched(report("add(label(lib.Ids.TITLE));", members = "private Label label(String id) { return new Label(id); }"))
    }
    fun testRuntimeFinalStringHasNoConstantValue() { uncertain(report("add(new Label(lib.Ids.RUNTIME));")) }
    fun testStaticBlockAssignmentHasNoConstantValue() { uncertain(report("add(new Label(lib.Ids.BLOCK));")) }
    fun testMutableBinaryFieldIsUnresolved() { uncertain(report("add(new Label(lib.Ids.MUTABLE));")) }
    fun testBinaryMethodCallsStayUnresolved() { uncertain(report("add(new Label(lib.Ids.title()));")) }
    fun testBoxedBinaryFieldStaysUnresolved() { uncertain(report("if (lib.Ids.BOXED == 2) add(new Label(\"title\"));")) }
    fun testFloatingPointStaysUnresolved() { uncertain(report("if (lib.Ids.FLOAT > 1) add(new Label(\"title\"));")) }
    fun testPrivateFieldStaysUnresolved() { uncertain(report("add(new Label(lib.Ids.PRIVATE));")) }
    fun testPackagePrivateFieldStaysUnresolved() { uncertain(report("add(new Label(lib.Ids.PACKAGE));")) }
    fun testInstanceQualifiedConstantDoesNotDiscardReceiverEffects() {
        uncertain(report("add(new Label(holder().TITLE));", members = "private lib.Ids holder() { removeAll(); return new lib.Ids(); }"))
    }
    fun testOversizedBinaryStringRetainsUncertainty() { uncertain(report("add(new Label(lib.Ids.TOO_LONG));")) }
    fun testEscapedBinaryStringRetainsItsActualValue() { matched(report("add(new Label(lib.Ids.ESCAPED));", "ti\"tle"), "ti\"tle") }
    fun testConstructingABinaryHolderStillRequiresAnInitializationContract() {
        uncertain(report("new lib.Ids(); add(new Label(lib.Ids.TITLE));"))
    }
    fun testAmbiguousImportsCannotChooseAConstant() {
        uncertain(report("add(new Label(TITLE));", imports = "import static lib.Constants.*; import static lib.Other.*;"))
    }
    fun testAttachedSourceDoesNotOverrideClassMetadata() { attachWrongSource(); matched(report("add(new Label(lib.Ids.TITLE));")) }
    fun testBinaryClassIsNeverLoadedOrInitialized() {
        val before = System.getProperty("wicketlens.fixture.must.not.load")
        matched(report("add(new Label(lib.Ids.TITLE));"))
        assertEquals(before, System.getProperty("wicketlens.fixture.must.not.load"))
    }
    fun testTestOnlyLibraryCannotSupplyProductionConstants() {
        ModuleRootModificationUtil.updateModel(module) { model ->
            model.orderEntries.filterIsInstance<LibraryOrderEntry>().single { it.libraryName == libraryName }.scope = DependencyScope.TEST
        }
        uncertain(report("add(new Label(lib.Ids.TITLE));"))
    }
    fun testReportsAndExportsAreDeterministicAndSchemaUnchanged() {
        val java = page("add(new Label(lib.Ids.TITLE));")
        companion()
        val before = java.text
        val first = WicketPageAnalyzer().analyze(java.classes.single())!!
        val second = WicketPageAnalyzer().analyze(java.classes.single())!!
        matched(first)
        assertEquals(first, second)
        assertEquals(before, java.text)
        assertEquals(WicketContextExporter().export(first), WicketContextExporter().export(second))
        assertEquals(1, first.schemaVersion)
    }
    fun testPortableDemoUsesThreeJdkConstants() {
        val root = java.nio.file.Path.of(System.getProperty("wicketlens.test.demo"))
        val file = myFixture.addFileToProject("src/main/java/example/BinaryConstantSetupPage.java",
            java.nio.file.Files.readString(root.resolve("main/java/example/BinaryConstantSetupPage.java"))) as PsiJavaFile
        companion("BinaryConstantSetupPage", java.nio.file.Files.readString(root.resolve("main/resources/example/BinaryConstantSetupPage.html")))
        val report = WicketPageAnalyzer().analyze(file.classes.single())!!
        assertEquals(setOf("xmlTitle", "item8", "status"), report.children().map { it.wicketId }.toSet())
        assertEquals(3, report.matching!!.findings.count { it.code == DiagnosticCode.MATCHED })
        assertEquals(3, report.javaSourceFiles.count { it.endsWith(".class") })
    }
}
