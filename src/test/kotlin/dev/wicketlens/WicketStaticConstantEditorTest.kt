package dev.wicketlens

import com.intellij.codeInsight.CodeInsightSettings
import com.intellij.codeInspection.InspectionManager
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.psi.*
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.refactoring.rename.RenameProcessor
import com.intellij.testFramework.PlatformTestUtil
import dev.wicketlens.editor.*
import dev.wicketlens.inspection.WicketUnpairedIdInspection
import dev.wicketlens.model.DiagnosticCode
import dev.wicketlens.ui.WicketLensService

class WicketStaticConstantEditorTest : WicketProjectPsiTestCase() {
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

    fun testResolvedConstantKeepsTheWholeUseExpressionAsItsEditorAnchor() {
        val constants = ids()
        val java = pageFile(body = "add(new Label(Ids.TITLE));")
        companion()
        val report = WicketEditorAnalysis.pages(java).single().report
        val node = report.children().single()
        assertEquals(DiagnosticCode.MATCHED, report.matching!!.findings.single().code)
        assertEquals("Ids.TITLE", WicketIdPsi.javaIdElement(project, node)!!.text)
        assertNull(WicketIdPsi.javaLiteral(project, node))
        assertNull(WicketIdPsi.javaId(constants.idValue("title")))
    }

    fun testSharedConstantCannotBePairedRenamedFromHtmlOrItsDeclaration() {
        val constants = ids()
        pageFile(body = "add(new Label(Ids.TITLE));")
        val html = companion()
        assertNull(WicketIdPsi.pair(html.idValue("title")))
        assertNull(WicketIdRenameTarget.at(html, html.text.indexOf("\"title\"") + 1))
        assertNull(WicketIdRenameTarget.at(constants, constants.text.indexOf("\"title\"") + 1))
    }

    fun testConstantProblemsHighlightTheReferenceAndOfferNoFragmentRewrite() {
        ids()
        val java = pageFile(body = "add(new Label(Ids.TITLE));")
        val html = companion(html = "<span wicket:id=\"htmlOnly\"></span>")
        val result = problems(java).results.single()
        assertEquals("Ids.TITLE", result.psiElement.text)
        assertTrue(result.fixes.orEmpty().isEmpty())
        assertEquals(1, problems(html).results.size)
    }

    fun testOrdinaryLiteralRenameStillWorksUnderAConstantCondition() {
        ids("public static final boolean ENABLED = true;")
        val java = pageFile(body = "if (Ids.ENABLED) add(new Label(\"title\"));")
        val html = companion()
        val target = WicketIdRenameTarget.at(java, java.text.indexOf("\"title\"") + 1)!!
        RenameProcessor(project, target, "heading", false, false).run()
        PsiDocumentManager.getInstance(project).commitAllDocuments()
        assertTrue(java.text.contains("Label(\"heading\")"))
        assertTrue(html.text.contains("wicket:id=\"heading\""))
    }

    fun testEmptyLiteralCompletionStillWorksUnderAConstantCondition() {
        ids("public static final boolean ENABLED = true;")
        val java = pageFile(body = "if (Ids.ENABLED) add(new Label(\"\"));")
        companion()
        myFixture.configureFromExistingVirtualFile(java.virtualFile)
        myFixture.editor.caretModel.moveToOffset(java.text.indexOf("\"\"") + 1)
        val candidates = myFixture.completeBasic().orEmpty().filter { it.getUserData(WicketIdCompletionContributor.CANDIDATE) == true }
        assertEquals(listOf("title"), candidates.map { it.lookupString })
        myFixture.lookup.currentItem = candidates.single()
        myFixture.finishLookup('\n')
        assertTrue(java.text.contains("new Label(\"title\")"))
    }

    fun testConstantReferenceRetainsNativeJavaDeclarationResolution() {
        val constants = ids() as PsiJavaFile
        val java = pageFile(body = "add(new Label(Ids.TITLE));")
        companion()
        WicketEditorAnalysis.pages(java)
        val reference = PsiTreeUtil.findChildrenOfType(java, PsiReferenceExpression::class.java).single { it.text == "Ids.TITLE" }
        assertEquals(constants.classes.single().fields.single(), reference.resolve())
        assertTrue(reference.references.none { it is WicketIdReference })
    }

    fun testUnsavedConstantEditInvalidatesTheSnapshotAndReanalysisUpdatesTheId() {
        val constants = ids()
        val java = pageFile(body = "add(new Label(Ids.TITLE));")
        companion()
        analyze(java)
        val prepared = service.state.prepared!!
        assertTrue(prepared.version.sources.any { it.path == constants.virtualFile.path })
        edit(constants, constants.text.replace("\"title\"", "\"changed\""), commit = false)
        assertTrue(service.state.stale)
        assertFalse(service.copyContext())
        assertFalse(service.navigate(prepared.report.children().single().javaLocation))
        PsiDocumentManager.getInstance(project).commitAllDocuments()
        service.reanalyze(); await()
        assertEquals("changed", service.state.prepared!!.report.children().single().wicketId)
    }

    fun testIntermediateAliasSourceParticipatesInFreshness() {
        ids()
        val alias = myFixture.addFileToProject("src/main/java/example/AliasIds.java", "package example; public class AliasIds extends Ids {}")
        myFixture.addFileToProject("src/main/java/example/OtherIds.java", "package example; public class OtherIds { public static final String TITLE = \"changed\"; }")
        val java = pageFile(body = "add(new Label(AliasIds.TITLE));")
        companion()
        analyze(java)
        assertTrue(service.state.prepared!!.version.sources.any { it.path == alias.virtualFile.path })
        edit(alias, alias.text.replace("extends Ids", "extends OtherIds"), commit = false)
        assertFalse(service.refreshFreshness())
        PsiDocumentManager.getInstance(project).commitAllDocuments()
        service.reanalyze(); await()
        assertEquals("changed", service.state.prepared!!.report.children().single().wicketId)
    }

    fun testDeclarationLiteralThatEqualsAComputedIdDoesNotAcquireAPair() {
        val constants = ids("public static final String TITLE = \"title\" + \"\";")
        pageFile(body = "add(new Label(Ids.TITLE));")
        val html = companion()
        assertNull(WicketIdPsi.javaId(constants.idValue("title")))
        assertNull(WicketIdPsi.pair(html.idValue("title")))
    }

    fun testUnknownStaticValuesDoNotProduceFalseUnpairedProblems() {
        ids("public static final String TITLE = System.getProperty(\"id\");")
        val java = pageFile(body = "add(new Label(Ids.TITLE));")
        val html = companion()
        assertTrue(problems(java).results.isEmpty())
        assertTrue(problems(html).results.isEmpty())
    }

    private fun ids(members: String = "public static final String TITLE = \"title\";") =
        myFixture.addFileToProject("src/main/java/example/Ids.java", "package example; public class Ids { $members }")
    private fun analyze(java: PsiJavaFile) { service.analyze(java.virtualFile.url, -1); await() }
    private fun await() {
        PlatformTestUtil.waitWithEventsDispatching("Static constant report", { !service.state.running }, 15)
        assertNotNull(service.state.toString(), service.state.prepared)
    }
    private fun edit(file: PsiFile, text: String, commit: Boolean = true) {
        WriteCommandAction.runWriteCommandAction(project) { PsiDocumentManager.getInstance(project).getDocument(file)!!.setText(text) }
        if (commit) PsiDocumentManager.getInstance(project).commitAllDocuments()
    }
    private fun problems(file: PsiFile) = ProblemsHolder(InspectionManager.getInstance(project), file, false).also {
        WicketUnpairedIdInspection().buildVisitor(it, false).visitFile(file)
    }
}
