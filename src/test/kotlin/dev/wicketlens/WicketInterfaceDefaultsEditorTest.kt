package dev.wicketlens

import com.intellij.codeInsight.CodeInsightSettings
import com.intellij.codeInspection.InspectionManager
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiJavaFile
import com.intellij.refactoring.rename.RenameProcessor
import com.intellij.testFramework.PlatformTestUtil
import dev.wicketlens.editor.*
import dev.wicketlens.inspection.WicketUnpairedIdInspection
import dev.wicketlens.model.DiagnosticCode
import dev.wicketlens.ui.WicketLensService

class WicketInterfaceDefaultsEditorTest : WicketProjectPsiTestCase() {
    private val service get() = project.service<WicketLensService>()
    private var autoComplete = true
    override fun setUp() {
        super.setUp()
        service.setEditorFollowing(false)
        autoComplete = CodeInsightSettings.getInstance().AUTOCOMPLETE_ON_CODE_COMPLETION
        CodeInsightSettings.getInstance().AUTOCOMPLETE_ON_CODE_COMPLETION = false
    }
    override fun tearDown() {
        try {
            service.cancel()
            CodeInsightSettings.getInstance().AUTOCOMPLETE_ON_CODE_COMPLETION = autoComplete
            FileEditorManager.getInstance(project).openFiles.forEach { FileEditorManager.getInstance(project).closeFile(it) }
        } finally { super.tearDown() }
    }

    fun testDefaultFactoryReferencesLinkTheCallersLiteralInBothDirections() {
        defaults()
        val java = page("add(new DefaultParts().label(\"title\"));")
        val html = companion()
        assertEquals(html.idValue("title"), WicketIdPsi.pair(java.idValue("title"))!!.attribute)
        assertEquals(java.idValue("title"), WicketIdPsi.pair(html.idValue("title"))!!.literal)
    }

    fun testNativeRenameUpdatesTheCallerAndMarkupWithoutChangingTheDefaultBody() {
        val api = defaults()
        val before = api.text
        val java = page("add(new DefaultParts().label(\"title\"));")
        val html = companion()
        rename(java, "title", "heading")
        assertTrue(java.text.contains("label(\"heading\")"))
        assertTrue(html.text.contains("wicket:id=\"heading\""))
        assertEquals(before, api.text)
    }

    fun testNativeCompletionUsesTheDefaultsProvenComponentParent() {
        defaults()
        val java = page("add(new DefaultParts().label(\"\"));")
        companion()
        myFixture.configureFromExistingVirtualFile(java.virtualFile)
        myFixture.editor.caretModel.moveToOffset(java.text.indexOf("\"\"") + 1)
        val candidates = myFixture.completeBasic().orEmpty().filter { it.getUserData(WicketIdCompletionContributor.CANDIDATE) == true }
        assertEquals(listOf("title"), candidates.map { it.lookupString })
        myFixture.lookup.currentItem = candidates.single(); myFixture.finishLookup('\n')
        PsiDocumentManager.getInstance(project).commitAllDocuments()
        assertEquals(DiagnosticCode.MATCHED, WicketEditorAnalysis.pages(java).single().report.matching!!.findings.single().code)
    }

    fun testUnpairedDefaultBindingsHaveNativeProblemsAtTheirOrigin() {
        defaults()
        val java = page("add(new DefaultParts().label(\"javaOnly\"));")
        val html = companion(html = "<span wicket:id=\"htmlOnly\"></span>")
        for ((file, id) in listOf(java to "javaOnly", html to "htmlOnly")) {
            val holder = problems(file)
            assertEquals(1, holder.results.size)
            assertEquals(file.idValue(id).textRange, holder.results.single().psiElement.textRange)
        }
    }

    fun testDefaultInterfaceEditsInvalidateTheSnapshotAndReanalysisUsesTheNewBody() {
        val api = defaults()
        val java = page("add(new DefaultParts().label(\"title\"));")
        companion()
        service.analyze(java.virtualFile.url, -1); await()
        val prepared = service.state.prepared!!
        assertEquals(4, prepared.version.sources.size)
        assertEquals(api.virtualFile.path, prepared.report.children().single().javaLocation.file)
        edit(api, api.text.replace("Label(id)", "Label(\"changed\")"), commit = false)
        assertTrue(service.state.stale)
        assertFalse(service.copyContext())
        assertFalse(service.navigate(prepared.report.children().single().javaLocation))
        PsiDocumentManager.getInstance(project).commitAllDocuments()
        service.reanalyze(); await()
        assertEquals("changed", service.state.prepared!!.report.children().single().wicketId)
    }

    fun testParentAndMostSpecificInterfaceContractsBothParticipateInFreshness() {
        val parent = defaults(child = true)
        val java = page("Parts parts = new DefaultParts(); add(parts.label(\"title\"));")
        companion()
        service.analyze(java.virtualFile.url, -1); await()
        val prepared = service.state.prepared!!
        assertEquals(5, prepared.version.sources.size)
        assertTrue(prepared.report.children().single().javaLocation.file.endsWith("/ChildParts.java"))
        edit(parent, parent.text.replace("Label(id)", "Label(\"unusedParent\")"), commit = false)
        assertTrue(service.state.stale)
        PsiDocumentManager.getInstance(project).commitAllDocuments()
    }

    fun testAnUnknownReceiverGetsNeitherPairedRenameNorFalseInspectionProblems() {
        defaults()
        val java = page("add(parts.label(\"title\"));", parameters = "Parts parts")
        val html = companion()
        assertTrue(problems(java).results.isEmpty())
        assertTrue(problems(html).results.isEmpty())
        assertNull(WicketIdRenameTarget.at(java, java.text.indexOf("\"title\"") + 1))
    }

    fun testASharedDefaultLiteralIsNotRenamedAsAnIndividualCallSite() {
        defaults("return new org.apache.wicket.markup.html.basic.Label(\"title\");")
        val java = page("add(new DefaultParts().label(\"unused\"));")
        val html = companion()
        assertEquals(DiagnosticCode.MATCHED, WicketEditorAnalysis.pages(java).single().report.matching!!.findings.single().code)
        assertNull(WicketIdRenameTarget.at(html, html.text.indexOf("\"title\"") + 1))
    }

    fun testExplicitInterfaceSuperCallSupportsNativeRenameOnANonfinalPage() {
        defaults()
        val java = page("add(Parts.super.label(\"title\"));", interfaces = "implements Parts")
        val html = companion()
        rename(java, "title", "heading")
        assertTrue(java.text.contains("Parts.super.label(\"heading\")"))
        assertTrue(html.text.contains("wicket:id=\"heading\""))
    }

    private fun defaults(body: String = "return new org.apache.wicket.markup.html.basic.Label(id);", child: Boolean = false): PsiFile {
        val api = myFixture.addFileToProject("src/main/java/example/Parts.java", """
            package example; public interface Parts {
                default org.apache.wicket.markup.html.basic.Label label(String id) { $body }
            }
        """.trimIndent())
        if (child) myFixture.addFileToProject("src/main/java/example/ChildParts.java", """
            package example; public interface ChildParts extends Parts {
                @Override default org.apache.wicket.markup.html.basic.Label label(String id) { return new org.apache.wicket.markup.html.basic.Label(id); }
            }
        """.trimIndent())
        myFixture.addFileToProject("src/main/java/example/DefaultParts.java", "package example; public class DefaultParts implements ${if (child) "ChildParts" else "Parts"} {}")
        return api
    }
    private fun page(body: String, parameters: String = "", interfaces: String = "") = myFixture.addFileToProject("src/main/java/example/ExamplePage.java", """
        package example; public class ExamplePage extends org.apache.wicket.markup.html.WebPage $interfaces {
            public ExamplePage($parameters) { $body }
        }
    """.trimIndent()) as PsiJavaFile
    private fun rename(file: PsiFile, id: String, replacement: String) {
        val target = WicketIdRenameTarget.at(file, file.text.indexOf("\"$id\"") + 1)!!
        RenameProcessor(project, target, replacement, false, false).run()
        PsiDocumentManager.getInstance(project).commitAllDocuments()
    }
    private fun edit(file: PsiFile, text: String, commit: Boolean = true) {
        WriteCommandAction.runWriteCommandAction(project) { PsiDocumentManager.getInstance(project).getDocument(file)!!.setText(text) }
        if (commit) PsiDocumentManager.getInstance(project).commitAllDocuments()
    }
    private fun await() {
        PlatformTestUtil.waitWithEventsDispatching("Default method report", { !service.state.running }, 15)
        assertNotNull(service.state.toString(), service.state.prepared)
    }
    private fun problems(file: PsiFile) = ProblemsHolder(InspectionManager.getInstance(project), file, false).also {
        WicketUnpairedIdInspection().buildVisitor(it, false).visitFile(file)
    }
}
