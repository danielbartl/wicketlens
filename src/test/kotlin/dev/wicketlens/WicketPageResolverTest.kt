package dev.wicketlens

import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiJavaFile
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.PsiTestUtil
import dev.wicketlens.analysis.WicketJavaAnalyzer
import dev.wicketlens.analysis.WicketPageAnalyzer
import dev.wicketlens.analysis.WicketPageResolver
import dev.wicketlens.model.DiagnosticCode
import dev.wicketlens.model.MarkupResolution
import dev.wicketlens.model.MarkupResolutionStatus
import dev.wicketlens.model.MarkupKind
import dev.wicketlens.model.Severity
import org.jetbrains.jps.model.java.JavaResourceRootProperties
import org.jetbrains.jps.model.java.JavaResourceRootType
import org.jetbrains.jps.model.java.JavaSourceRootType

class WicketPageResolverTest : WicketJavaPsiTestCase() {
    // Isolate source-root changes from the earlier analyzer fixture classes.
    override fun getProjectDescriptor(): LightProjectDescriptor = DESCRIPTOR

    override fun setUp() {
        super.setUp()
        ModuleRootModificationUtil.updateModel(module) { model ->
            model.contentEntries.forEach { entry ->
                entry.sourceFolders.forEach { entry.removeSourceFolder(it) }
                entry.excludeFolders.forEach { entry.removeExcludeFolder(it) }
            }
        }
        PsiTestUtil.addSourceRoot(module, dir("src/main/java"), JavaSourceRootType.SOURCE)
        PsiTestUtil.addSourceRoot(module, dir("src/main/resources"), JavaResourceRootType.RESOURCE)
        PsiTestUtil.addSourceRoot(module, dir("src/test/java"), JavaSourceRootType.TEST_SOURCE)
        PsiTestUtil.addSourceRoot(module, dir("src/test/resources"), JavaResourceRootType.TEST_RESOURCE)
        ModuleRootModificationUtil.updateModel(module) { model ->
            model.contentEntries.flatMap { it.sourceFolders.toList() }
                .filter { it.rootType == JavaSourceRootType.SOURCE || it.rootType == JavaSourceRootType.TEST_SOURCE }
                .forEach { it.packagePrefix = "" }
        }
    }

    fun testPairsHtmlBesideJavaSource() {
        val page = page()
        val markup = html("src/main/java/example/ExamplePage.html")
        val result = WicketPageResolver().resolve(page)
        assertEquals(MarkupResolutionStatus.PAIRED, result.status)
        assertEquals(markup.virtualFile.path, result.markupPath)
        assertEquals(markup.virtualFile.url, result.markupUrl)
        assertEquals(listOf(result.markupPath), result.candidatePaths)
        assertTrue(result.diagnostics.isEmpty())
    }

    fun testPairsMatchingResourcePackageForPage() {
        assertResourcePairing("org.apache.wicket.markup.html.WebPage")
    }

    fun testPairsMatchingResourcePackageForPanel() {
        assertResourcePairing("org.apache.wicket.markup.html.panel.Panel")
    }

    private fun assertResourcePairing(base: String) {
        val page = page(base = base)
        val markup = html("src/main/resources/example/ExamplePage.html")
        val result = WicketPageResolver().resolve(page)
        assertEquals(result.toString(), markup.virtualFile.path, result.markupPath)
    }

    fun testMissingMarkupIsInformationalAndDoesNotSelectAPath() {
        val page = page()
        val result = WicketPageResolver().resolve(page)
        assertUnresolved(result, MarkupResolutionStatus.MISSING)
        assertTrue(result.candidatePaths.isEmpty())
        assertEquals(page.containingFile.virtualFile.path, result.diagnostics.single().location?.file)
    }

    fun testBothSupportedLayoutsAreAmbiguousRatherThanPrioritized() {
        val page = page()
        val adjacent = html("src/main/java/example/ExamplePage.html")
        val resource = html("src/main/resources/example/ExamplePage.html")
        val result = WicketPageResolver().resolve(page)
        assertUnresolved(result, MarkupResolutionStatus.AMBIGUOUS)
        assertEquals(listOf(adjacent.virtualFile.path, resource.virtualFile.path).sorted(), result.candidatePaths)
        assertTrue(result.candidatePaths.all { result.diagnostics.single().message.contains(it) })
    }

    fun testMultipleConfiguredResourceRootsAreAmbiguous() {
        PsiTestUtil.addSourceRoot(module, dir("extra-resources"), JavaResourceRootType.RESOURCE)
        val page = page()
        html("src/main/resources/example/ExamplePage.html")
        html("extra-resources/example/ExamplePage.html")
        val result = WicketPageResolver().resolve(page)
        assertUnresolved(result, MarkupResolutionStatus.AMBIGUOUS)
        assertEquals(2, result.candidatePaths.size)
    }

    fun testConfiguredResourceRootNeedNotUseTheMavenDirectoryName() {
        PsiTestUtil.addSourceRoot(module, dir("assets"), JavaResourceRootType.RESOURCE)
        val page = page()
        val markup = html("assets/example/ExamplePage.html")
        assertEquals(markup.virtualFile.path, WicketPageResolver().resolve(page).markupPath)
    }

    fun testWrongPackageBasenameAndUnconfiguredDirectoriesAreIgnored() {
        val page = page()
        html("src/main/resources/other/ExamplePage.html")
        html("src/main/resources/example/DifferentPage.html")
        html("unconfigured/example/ExamplePage.html")
        assertUnresolved(WicketPageResolver().resolve(page), MarkupResolutionStatus.MISSING)
    }

    fun testLocalizedVariantsAndOtherMarkupExtensionsAreNotGuessed() {
        val page = page()
        html("src/main/resources/example/ExamplePage_en.html")
        html("src/main/resources/example/ExamplePage_mobile.html")
        myFixture.addFileToProject("src/main/resources/example/ExamplePage.xhtml", "<html/>")
        assertUnresolved(WicketPageResolver().resolve(page), MarkupResolutionStatus.MISSING)
    }

    fun testProductionPageIgnoresTestResources() {
        val page = page()
        html("src/test/resources/example/ExamplePage.html")
        assertUnresolved(WicketPageResolver().resolve(page), MarkupResolutionStatus.MISSING)
        val production = html("src/main/resources/example/ExamplePage.html")
        assertEquals(production.virtualFile.path, WicketPageResolver().resolve(page).markupPath)
    }

    fun testTestPageUsesTestResourcesWithoutProductionAmbiguity() {
        val page = page(path = "src/test/java/example/ExamplePage.java")
        html("src/main/resources/example/ExamplePage.html")
        val testMarkup = html("src/test/resources/example/ExamplePage.html")
        assertEquals(testMarkup.virtualFile.path, WicketPageResolver().resolve(page).markupPath)
    }

    fun testTestPageCanPairBesideTestJava() {
        val page = page(path = "src/test/java/example/ExamplePage.java")
        val markup = html("src/test/java/example/ExamplePage.html")
        assertEquals(markup.virtualFile.path, WicketPageResolver().resolve(page).markupPath)
    }

    fun testExcludedMarkupDoesNotBecomeACandidate() {
        val page = page()
        html("src/main/resources/example/ExamplePage.html")
        PsiTestUtil.addExcludedRoot(module, dir("src/main/resources/example"))
        assertUnresolved(WicketPageResolver().resolve(page), MarkupResolutionStatus.MISSING)
    }

    fun testHtmlInAnotherJavaSourceRootIsNotACompanion() {
        PsiTestUtil.addSourceRoot(module, dir("extra-java"), JavaSourceRootType.SOURCE)
        val page = page()
        html("extra-java/example/ExamplePage.html")
        assertUnresolved(WicketPageResolver().resolve(page), MarkupResolutionStatus.MISSING)
    }

    fun testDirectoryNamedHtmlIsNotACompanionFile() {
        val page = page()
        dir("src/main/java/example/ExamplePage.html")
        assertUnresolved(WicketPageResolver().resolve(page), MarkupResolutionStatus.MISSING)
    }

    fun testDefaultPackageResourcePairing() {
        val page = page(path = "src/main/java/ExamplePage.java", packageName = "")
        val markup = html("src/main/resources/ExamplePage.html")
        val result = WicketPageResolver().resolve(page)
        assertEquals(result.toString(), markup.virtualFile.path, result.markupPath)
    }

    fun testJavaSourcePackagePrefixUsesTheLogicalPackageForResources() {
        ModuleRootModificationUtil.updateModel(module) { model ->
            model.contentEntries.flatMap { it.sourceFolders.toList() }
                .single { it.file == dir("src/main/java") }.packagePrefix = "example"
        }
        val page = page(path = "src/main/java/ExamplePage.java")
        val markup = html("src/main/resources/example/ExamplePage.html")
        val result = WicketPageResolver().resolve(page)
        assertEquals(result.toString(), markup.virtualFile.path, result.markupPath)
    }

    fun testPackageMismatchDoesNotPairAnAdjacentFile() {
        val page = page(path = "src/main/java/wrong/ExamplePage.java")
        html("src/main/java/wrong/ExamplePage.html")
        assertUnresolved(WicketPageResolver().resolve(page), MarkupResolutionStatus.UNSUPPORTED)
    }

    fun testSourceOutsideAConfiguredJavaRootIsUnsupported() {
        val page = page(path = "unconfigured/example/ExamplePage.java")
        html("unconfigured/example/ExamplePage.html")
        assertUnresolved(WicketPageResolver().resolve(page), MarkupResolutionStatus.UNSUPPORTED)
    }

    fun testNonphysicalJavaFileIsUnsupported() {
        val file = myFixture.configureByText("ExamplePage.java", "class ExamplePage {}") as PsiJavaFile
        assertUnresolved(WicketPageResolver().resolve(file.classes.single()), MarkupResolutionStatus.UNSUPPORTED)
    }

    fun testNestedAndSecondaryClassesDoNotBorrowOuterFileMarkup() {
        val page = page(members = "class Inner {}")
        html("src/main/java/example/ExamplePage.html")
        assertUnresolved(WicketPageResolver().resolve(page.innerClasses.single()), MarkupResolutionStatus.UNSUPPORTED)
        val file = myFixture.addFileToProject(
            "src/main/java/example/Container.java",
            "package example; class Container {} class Secondary {}",
        ) as PsiJavaFile
        html("src/main/java/example/Container.html")
        assertUnresolved(WicketPageResolver().resolve(file.classes.last()), MarkupResolutionStatus.UNSUPPORTED)
    }

    fun testCustomResourceOutputMappingPreventsFalseUniquePairing() {
        PsiTestUtil.addSourceRoot(
            module, dir("mapped-resources"), JavaResourceRootType.RESOURCE,
            JavaResourceRootProperties("custom", false),
        )
        val page = page()
        val adjacent = html("src/main/java/example/ExamplePage.html")
        val result = WicketPageResolver().resolve(page)
        assertUnresolved(result, MarkupResolutionStatus.UNSUPPORTED)
        assertEquals(listOf(adjacent.virtualFile.path), result.candidatePaths)
    }

    fun testComposedReportPreservesJavaGraphAndAddsMarkupPath() {
        val page = page(body = "add(new org.apache.wicket.markup.html.basic.Label(\"title\"));")
        val markup = html("src/main/resources/example/ExamplePage.html")
        val java = requireNotNull(WicketJavaAnalyzer().analyze(page))
        val report = requireNotNull(WicketPageAnalyzer().analyze(page))
        assertEquals(markup.virtualFile.path, report.markupPath)
        assertEquals(java.nodes, report.nodes.map { it.copy(markupLocation = null) })
        assertEquals(java.warnings, report.warnings)
        assertFalse(report.scopeComplete)
        assertEquals(DiagnosticCode.MATCHED, report.matching?.findings?.single()?.code)
        assertNotNull(report.children().single().markupLocation)
        assertEquals(MarkupKind.PAGE, report.markup?.kind)
        assertEquals(listOf("title"), report.markup?.elements?.filter { it.hasWicketId }?.map { it.wicketId })
    }

    fun testComposedReportRetainsJavaFindingsWhenMarkupIsUnresolved() {
        val page = page(body = "add(new org.apache.wicket.markup.html.basic.Label(\"title\"));")
        val report = requireNotNull(WicketPageAnalyzer().analyze(page))
        assertNull(report.markupPath)
        assertNull(report.markup)
        assertEquals("title", report.children().single().wicketId)
        assertTrue(report.warnings.any { it.code == DiagnosticCode.UNRESOLVED_MARKUP })
        assertTrue(report.warnings.any { it.code == DiagnosticCode.PARTIAL_ANALYSIS })
    }

    fun testComposedAnalyzerIgnoresNonWicketClasses() {
        val file = myFixture.addFileToProject("src/main/java/example/Plain.java", "package example; class Plain {}") as PsiJavaFile
        assertNull(WicketPageAnalyzer().analyze(file.classes.single()))
    }

    fun testComposedPanelReportExtractsOnlyThePanelComponentScope() {
        val page = page(base = "org.apache.wicket.markup.html.panel.Panel")
        myFixture.addFileToProject("src/main/resources/example/ExamplePage.html", """
            <html><body><span wicket:id="preview"></span>
              <wicket:panel><span wicket:id="title"></span></wicket:panel>
            </body></html>
        """.trimIndent())
        val report = requireNotNull(WicketPageAnalyzer().analyze(page))
        val markup = requireNotNull(report.markup)
        assertEquals(MarkupKind.PANEL, markup.kind)
        assertEquals(listOf("title"), markup.elements.filter { it.hasWicketId && it.participatesInComponentTree }.map { it.wicketId })
        assertFalse(report.scopeComplete)
    }

    fun testComposedReportIncludesMarkupUncertaintyWithoutChangingJavaNodes() {
        val page = page(body = "add(new org.apache.wicket.markup.html.basic.Label(\"title\"));")
        val java = requireNotNull(WicketJavaAnalyzer().analyze(page))
        myFixture.addFileToProject("src/main/resources/example/ExamplePage.html", """
            <html><body><wicket:extend><span wicket:id="title"></span></wicket:extend></body></html>
        """.trimIndent())
        val report = requireNotNull(WicketPageAnalyzer().analyze(page))
        assertEquals(java.nodes, report.nodes)
        assertFalse(requireNotNull(report.markup).scopeComplete)
        assertTrue(report.warnings.any { it.code == DiagnosticCode.UNRESOLVED_MARKUP })
        assertTrue(report.nodes.all { it.markupLocation == null })
    }

    fun testAmbiguousPairingDoesNotExtractAnArbitraryMarkupFile() {
        val page = page()
        html("src/main/java/example/ExamplePage.html")
        html("src/main/resources/example/ExamplePage.html")
        val report = requireNotNull(WicketPageAnalyzer().analyze(page))
        assertNull(report.markupPath)
        assertNull(report.markup)
        assertTrue(report.warnings.any { it.code == DiagnosticCode.UNRESOLVED_MARKUP })
    }

    fun testMissingPairingReasonIsIncludedInMatchingCauses() {
        val page = page(body = "add(new org.apache.wicket.markup.html.basic.Label(\"title\"));")
        val report = requireNotNull(WicketPageAnalyzer().analyze(page))
        val finding = requireNotNull(report.matching).findings.single()
        assertEquals(DiagnosticCode.UNRESOLVED_MARKUP, finding.code)
        assertTrue(finding.causes.any { it.code == DiagnosticCode.UNRESOLVED_MARKUP && it.message.contains("No ExamplePage.html") })
    }

    fun testAmbiguousPairingCausesPreserveBothCandidates() {
        val page = page(body = "add(new org.apache.wicket.markup.html.basic.Label(\"title\"));")
        val adjacent = html("src/main/java/example/ExamplePage.html")
        val resource = html("src/main/resources/example/ExamplePage.html")
        val report = requireNotNull(WicketPageAnalyzer().analyze(page))
        val causes = requireNotNull(report.matching).findings.single().causes
        assertTrue(causes.any { it.message.contains(adjacent.virtualFile.path) && it.message.contains(resource.virtualFile.path) })
        assertNull(report.children().single().markupLocation)
    }

    fun testResolutionIsDeterministicReadOnlyAndDoesNotCacheMissingResults() {
        val page = page()
        val original = page.containingFile.text
        val resolver = WicketPageResolver()
        assertUnresolved(resolver.resolve(page), MarkupResolutionStatus.MISSING)
        val markup = html("src/main/resources/example/ExamplePage.html")
        val paired = resolver.resolve(page)
        assertEquals(markup.virtualFile.path, paired.markupPath)
        assertEquals(paired, resolver.resolve(page))
        assertEquals(original, page.containingFile.text)
        html("src/main/java/example/ExamplePage.html")
        assertUnresolved(resolver.resolve(page), MarkupResolutionStatus.AMBIGUOUS)
    }

    fun testResolverHonorsCancellation() {
        val page = page()
        val indicator = EmptyProgressIndicator()
        assertThrows(ProcessCanceledException::class.java) {
            ProgressManager.getInstance().runProcess(Runnable {
                indicator.cancel()
                WicketPageResolver().resolve(page)
            }, indicator)
        }
    }

    private fun dir(path: String) = myFixture.tempDirFixture.findOrCreateDir(path)

    private fun html(path: String) = myFixture.addFileToProject(path, "<html><body><span wicket:id=\"title\">Hi</span></body></html>")

    private fun page(
        path: String = "src/main/java/example/ExamplePage.java",
        packageName: String = "example",
        base: String = "org.apache.wicket.markup.html.WebPage",
        body: String = "",
        members: String = "",
    ): PsiClass {
        val prefix = if (packageName.isEmpty()) "" else "package $packageName;"
        val constructor = if (base.endsWith("Panel")) "super(\"root\");" else ""
        val file = myFixture.addFileToProject(
            path,
            "$prefix public class ExamplePage extends $base { public ExamplePage() { $constructor $body } $members }",
        ) as PsiJavaFile
        return file.classes.single()
    }

    private fun assertUnresolved(result: MarkupResolution, status: MarkupResolutionStatus) {
        assertEquals(status, result.status)
        assertNull(result.markupPath)
        assertEquals(DiagnosticCode.UNRESOLVED_MARKUP, result.diagnostics.single().code)
        assertEquals(Severity.INFO, result.diagnostics.single().severity)
    }

    companion object {
        private val DESCRIPTOR = object : LightProjectDescriptor() {
            override fun getSdk() = Java25ProjectDescriptor.getSdk()
        }
    }
}
