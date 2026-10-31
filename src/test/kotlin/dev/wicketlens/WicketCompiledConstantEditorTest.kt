package dev.wicketlens

import com.intellij.codeInspection.InspectionManager
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.roots.LibraryOrderEntry
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.psi.*
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.refactoring.rename.RenameProcessor
import com.intellij.testFramework.PlatformTestUtil
import dev.wicketlens.analysis.WicketPageAnalyzer
import dev.wicketlens.analysis.WicketSourceFiles
import dev.wicketlens.editor.*
import dev.wicketlens.inspection.WicketUnpairedIdInspection
import dev.wicketlens.ui.*

class WicketCompiledConstantEditorTest : WicketCompiledConstantTestCase() {
    private val service get() = project.service<WicketLensService>()
    override fun setUp() { super.setUp(); service.setEditorFollowing(false) }
    override fun tearDown() {
        try {
            service.cancel()
            FileEditorManager.getInstance(project).openFiles.forEach { FileEditorManager.getInstance(project).closeFile(it) }
        } finally { super.tearDown() }
    }

    fun testWholeReferenceIsTheIdAnchorWithNativeJavaDeclarationNavigation() {
        val java = page("add(new Label(lib.Ids.TITLE));")
        companion()
        val report = WicketEditorAnalysis.pages(java).single().report
        matched(report)
        val node = report.children().single()
        val anchor = WicketIdPsi.javaIdElement(project, node)!!
        assertEquals("lib.Ids.TITLE", anchor.text)
        assertNull(WicketIdPsi.javaLiteral(project, node))
        assertTrue((anchor as PsiReferenceExpression).resolve() is PsiCompiledElement)
        assertNull(WicketIdRenameTarget.at(java, anchor.textOffset))
    }
    fun testMarkupPairedRenameIsUnavailableForABinaryId() {
        page("add(new Label(lib.Ids.TITLE));")
        val html = companion()
        assertNull(WicketIdRenameTarget.at(html, html.text.indexOf("title")))
    }
    fun testProblemsUseTheWholeReferenceWithoutFragmentFixes() {
        val java = page("add(new Label(lib.Ids.TITLE));")
        companion(html = "<span wicket:id='other'></span>")
        val holder = ProblemsHolder(InspectionManager.getInstance(project), java, false)
        WicketUnpairedIdInspection().buildVisitor(holder, false).visitFile(java)
        assertEquals("lib.Ids.TITLE", holder.results.single().psiElement.text)
        assertTrue(holder.results.single().fixes.orEmpty().isEmpty())
    }
    fun testBinaryAndArchiveVersionsAreCapturedWithoutTextDocuments() {
        val java = page("add(new Label(lib.Ids.TITLE));")
        companion()
        val report = WicketPageAnalyzer().analyze(java.classes.single())!!
        val version = ReportVersion.capture(project, report, java.virtualFile.url, 0)
        assertTrue(version.isCurrent(project, 0))
        assertTrue(version.sources.any { it.path.endsWith("!/lib/Ids.class") && it.binary })
        assertTrue(version.sources.any { it.path == jar.toString() && it.binary })
    }
    fun testJdkRuntimeImageConstantsHaveResolvableBinaryVersions() {
        val java = page("if (Integer.MAX_VALUE > 0) add(new Label(\"title\"));")
        companion()
        val report = WicketPageAnalyzer().analyze(java.classes.single())!!
        matched(report)
        val version = ReportVersion.capture(project, report, java.virtualFile.url, 0)
        assertTrue(version.isCurrent(project, 0))
        assertTrue(version.sources.any { it.url.startsWith("jrt://") && it.path.endsWith("Integer.class") && it.binary })
    }
    fun testArchiveChangeInvalidatesEvenBeforeEntryRefresh() {
        val java = page("add(new Label(lib.Ids.TITLE));")
        companion()
        val report = WicketPageAnalyzer().analyze(java.classes.single())!!
        val version = ReportVersion.capture(project, report, java.virtualFile.url, 0)
        val archive = LocalFileSystem.getInstance().findFileByNioFile(jar)!!
        val binaryVersion = version.sources.single { it.path == jar.toString() }
        WriteCommandAction.runWriteCommandAction(project) { archive.setBinaryContent(archive.contentsToByteArray() + byteArrayOf(0)) }
        assertFalse(binaryVersion.isCurrent())
        assertFalse(version.isCurrent(project, 0))
    }
    fun testLibraryRemovalInvalidatesTheCapturedVersion() {
        val java = page("add(new Label(lib.Ids.TITLE));")
        companion()
        val report = WicketPageAnalyzer().analyze(java.classes.single())!!
        val version = ReportVersion.capture(project, report, java.virtualFile.url, 0)
        ModuleRootModificationUtil.updateModel(module) { model ->
            model.removeOrderEntry(model.orderEntries.filterIsInstance<LibraryOrderEntry>().single { it.libraryName == libraryName })
        }
        assertFalse(version.isCurrent(project, 0))
    }
    fun testOrdinaryLiteralEditingStillWorksUnderABinaryFlag() {
        val java = page("if (lib.Ids.ENABLED) add(new Label(\"title\"));")
        val html = companion()
        val target = WicketIdRenameTarget.at(java, java.text.indexOf("\"title\"") + 1)!!
        RenameProcessor(project, target, "heading", false, false).run()
        PsiDocumentManager.getInstance(project).commitAllDocuments()
        assertTrue(java.text.contains("Label(\"heading\")"))
        assertTrue(html.text.contains("wicket:id=\"heading\""))
    }
    fun testServicePreparesACurrentBinaryDependencySnapshot() {
        val java = page("add(new Label(lib.Ids.TITLE));")
        companion()
        service.analyze(java.virtualFile.url, -1)
        PlatformTestUtil.waitWithEventsDispatching("Binary constant report", { !service.state.running }, 15)
        val prepared = service.state.prepared ?: error(service.state.message)
        assertFalse(service.state.stale)
        matched(prepared.report)
        assertTrue(prepared.version.sources.any { it.path == jar.toString() })
        assertTrue(service.copyContext())
    }
}
