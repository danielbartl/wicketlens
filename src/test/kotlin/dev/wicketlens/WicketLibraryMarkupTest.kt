package dev.wicketlens

import com.intellij.openapi.roots.DependencyScope
import com.intellij.openapi.roots.LibraryOrderEntry
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.JarFileSystem
import com.intellij.psi.*
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.testFramework.PsiTestUtil
import dev.wicketlens.analysis.*
import dev.wicketlens.editor.WicketIdReference
import dev.wicketlens.model.*
import dev.wicketlens.ui.ReportVersion
import java.nio.file.Files
import java.nio.file.Path
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream
import java.util.concurrent.TimeUnit

class WicketLibraryMarkupTest : WicketProjectPsiTestCase() {
    fun testSourcePageCanUseReadonlyMarkupFromACompiledAncestor() {
        library("base", mapOf("lib/BasePage.html" to "<html><head></head><body><span wicket:id=\"title\"></span></body></html>"))
        val java = page("add(new Label(\"title\"));")
        val report = analyze(java)
        assertEquals(1, report.matching!!.findings.count { it.code == DiagnosticCode.MATCHED })
        assertTrue(report.markupPath!!, "!/lib/BasePage.html" in report.markupPath)
        assertTrue(report.warnings.any { it.code == DiagnosticCode.UNRESOLVED_SCOPE })
        assertFalse(markupFile(report.markupPath!!).isWritable)
    }

    fun testSourceExtensionComposesWithBinaryBaseHeaderAndBody() {
        library("base", mapOf("lib/BasePage.html" to "<html><head><title wicket:id=\"unknownBaseTitle\"></title></head><body><wicket:child/></body></html>"))
        val java = page("add(new Label(\"title\"));")
        val html = companion("DetailPage", "<wicket:head><script src=\"detail.js\"></script></wicket:head><wicket:extend><span wicket:id=\"title\"></span></wicket:extend>")
        val report = analyze(java)
        assertEquals(1, report.matching!!.findings.count { it.code == DiagnosticCode.MATCHED })
        assertTrue(report.matching.findings.none { it.code == DiagnosticCode.MARKUP_ONLY })
        assertEquals(html.virtualFile.path, report.markupPath)
        assertEquals(2, report.markup!!.sourceFiles.size)
        assertTrue(report.markup.scopeComplete)
    }

    fun testDuplicateClasspathResourcesAreAmbiguous() {
        library("base", mapOf("lib/BasePage.html" to "<span wicket:id=\"title\"></span>"))
        library("duplicate", mapOf("lib/BasePage.html" to "<span wicket:id=\"title\"></span>"), compileClass = false)
        val java = page("add(new Label(\"title\"));")
        val result = WicketPageResolver().resolve(java.classes.single().superClass!!, consumer = java.classes.single())
        assertEquals(MarkupResolutionStatus.AMBIGUOUS, result.status)
        assertEquals(2, result.candidatePaths.size)
        assertNull(analyze(java).markup)
    }

    fun testApplicationResourcesCannotSilentlyOverrideDependencyResources() {
        library("base", mapOf("lib/BasePage.html" to "<span wicket:id=\"title\"></span>"))
        myFixture.addFileToProject("src/main/resources/lib/BasePage.html", "<span wicket:id=\"other\"></span>")
        val java = page("add(new Label(\"title\"));")
        val result = WicketPageResolver().resolve(java.classes.single().superClass!!, consumer = java.classes.single())
        assertEquals(MarkupResolutionStatus.AMBIGUOUS, result.status)
    }

    fun testSourceOwnedOrdinaryMarkupReplacesDependencyLayout() {
        library("base", mapOf("lib/BasePage.html" to "<html><body><wicket:child/></body></html>"))
        val java = page("add(new Label(\"title\"));")
        val html = companion("DetailPage", "<span wicket:id=\"title\"></span>")
        val report = analyze(java)
        assertEquals(listOf(html.virtualFile.path), report.markup!!.sourceFiles)
        assertEquals(1, report.matching!!.findings.count { it.code == DiagnosticCode.MATCHED })
    }

    fun testMissingDependencyMarkupRetainsUncertainty() {
        library("base", emptyMap())
        val java = page("add(new Label(\"title\"));")
        companion("DetailPage", "<wicket:extend><span wicket:id=\"title\"></span></wicket:extend>")
        val report = analyze(java)
        assertTrue(report.matching!!.findings.none { it.code in setOf(DiagnosticCode.MATCHED, DiagnosticCode.JAVA_ONLY, DiagnosticCode.MARKUP_ONLY) })
    }

    fun testDependencyMarkupIsPartOfSnapshotVersionsAndJavaNavigation() {
        library("base", mapOf("lib/BasePage.html" to "<span wicket:id=\"title\"></span>"))
        val java = page("add(new Label(\"title\"));")
        val report = analyze(java)
        val version = ReportVersion.capture(project, report, java.virtualFile.url, 0)
        assertTrue(version.sources.any { it.url.startsWith("jar://") })
        assertTrue(version.isCurrent(project, 0))
        val resolved = java.idValue("title").references.filterIsInstance<WicketIdReference>().single().resolve()!!
        assertEquals(report.markupPath, resolved.containingFile.virtualFile.path)
        assertFalse(resolved.isWritable)
    }

    fun testExactVariantCanInheritAnExactDependencyVariant() {
        library("base", mapOf("lib/BasePage_fr.html" to "<html><head></head><body><wicket:child/></body></html>"))
        page("add(new Label(\"title\"));")
        val html = companion("DetailPage_fr", "<wicket:extend><span wicket:id=\"title\"></span></wicket:extend>")
        val report = dev.wicketlens.editor.WicketEditorAnalysis.pages(html).single().report
        assertEquals(1, report.matching!!.findings.count { it.code == DiagnosticCode.MATCHED })
        assertTrue(report.markup!!.sourceFiles.any { it.endsWith("!/lib/BasePage_fr.html") })
    }

    fun testTestOnlyLibrariesAreNotAProductionLookupShortcut() {
        val libraryName = library("base", mapOf("lib/BasePage.html" to "<span wicket:id=\"title\"></span>"))
        val java = page("add(new Label(\"title\"));")
        val ancestor = java.classes.single().superClass!!
        ModuleRootModificationUtil.updateModel(module) { model ->
            model.orderEntries.filterIsInstance<LibraryOrderEntry>().single { it.libraryName == libraryName }.scope = DependencyScope.TEST
        }
        assertEquals(MarkupResolutionStatus.UNSUPPORTED, WicketPageResolver().resolve(ancestor, consumer = java.classes.single()).status)
    }

    private fun page(body: String) = myFixture.addFileToProject("src/main/java/example/DetailPage.java", """
        package example; import org.apache.wicket.markup.html.basic.Label;
        public class DetailPage extends lib.BasePage { public DetailPage() { $body } }
    """.trimIndent()) as PsiJavaFile

    private fun analyze(file: PsiJavaFile) = WicketPageAnalyzer().analyze(file.classes.single())!!
    private fun markupFile(path: String) = PsiManager.getInstance(project).findFile(WicketSourceFiles.find(project, path)!!)!!

    private fun library(name: String, resources: Map<String, String>, compileClass: Boolean = true): String {
        val root = Files.createTempDirectory("wicketlens-layout-$name")
        com.intellij.openapi.vfs.newvfs.impl.VfsRootAccess.allowRootAccess(testRootDisposable, root.toString())
        Disposer.register(testRootDisposable) { root.toFile().deleteRecursively() }
        val classes = Files.createDirectories(root.resolve("classes"))
        if (compileClass) {
            val source = root.resolve("BasePage.java")
            Files.writeString(source, "package lib; public class BasePage extends org.apache.wicket.markup.html.WebPage { public BasePage() {} }")
            val log = root.resolve("javac.log")
            val process = ProcessBuilder(System.getProperty("wicketlens.test.javac"),
                "-classpath", System.getProperty("wicketlens.test.libraries"), "-d", classes.toString(), source.toString())
                .redirectErrorStream(true).redirectOutput(log.toFile()).start()
            try {
                assertTrue("Fixture compiler timed out", process.waitFor(30, TimeUnit.SECONDS))
                assertEquals(Files.readString(log), 0, process.exitValue())
            } finally { if (process.isAlive) process.destroyForcibly() }
        }
        val jar = root.resolve("$name.jar")
        JarOutputStream(Files.newOutputStream(jar)).use { output ->
            if (compileClass) {
                output.putNextEntry(JarEntry("lib/BasePage.class")); output.write(Files.readAllBytes(classes.resolve("lib/BasePage.class"))); output.closeEntry()
            }
            for ((path, text) in resources) {
                output.putNextEntry(JarEntry(path)); output.write(text.toByteArray()); output.closeEntry()
            }
        }
        val libraryName = "layout-$name-${root.fileName}"
        PsiTestUtil.addLibrary(testRootDisposable, module, libraryName, root.toString(), jar.fileName.toString())
        return libraryName
    }
}
