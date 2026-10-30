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

class WicketScalarExpressionEditorTest : WicketProjectPsiTestCase() {
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

    fun testCompositeIdDoesNotTreatAnEqualValuedFragmentAsTheWholeId() {
        val java = page("add(new Label(\"title\" + \"\"));")
        val html = companion()
        val node = WicketEditorAnalysis.pages(java).single().report.children().single()
        assertTrue(node.computedId)
        assertNull(WicketIdPsi.javaLiteral(project, node))
        assertNull(WicketIdPsi.javaId(java.idValue("title")))
        assertNull(WicketIdPsi.pair(html.idValue("title")))
        assertNull(WicketIdRenameTarget.at(html, html.text.indexOf("\"title\"") + 1))
        assertNull(WicketIdRenameTarget.at(java, java.text.indexOf("\"title\"") + 1))
    }

    fun testComputedIdProblemHighlightsTheWholeExpression() {
        val java = page("add(new Label(\"java\" + \"Only\"));")
        val html = companion(html = "<span wicket:id=\"htmlOnly\"></span>")
        val holder = problems(java)
        assertEquals(1, holder.results.size)
        assertEquals("\"java\" + \"Only\"", holder.results.single().psiElement.text)
        assertTrue(holder.results.single().fixes.orEmpty().isEmpty())
        assertEquals(1, problems(html).results.size)
    }

    fun testNumericConditionsPreserveOrdinaryPairedRename() {
        val java = page("if (1 + 1 >= 2) add(new Label(\"title\"));")
        val html = companion()
        val target = WicketIdRenameTarget.at(java, java.text.indexOf("\"title\"") + 1)!!
        RenameProcessor(project, target, "heading", false, false).run()
        PsiDocumentManager.getInstance(project).commitAllDocuments()
        assertTrue(java.text.contains("1 + 1 >= 2"))
        assertTrue(java.text.contains("new Label(\"heading\")"))
        assertTrue(html.text.contains("wicket:id=\"heading\""))
    }

    fun testIdUsedToChooseItsOwnBranchDoesNotOfferPairedRename() {
        val java = page("add(label(\"title\"));", "private Label label(String id) { if (id.equals(\"title\")) return new Label(id); return new Label(\"other\"); }")
        val html = companion()
        val node = WicketEditorAnalysis.pages(java).single().report.children().single()
        assertTrue(node.idUsedInComputation)
        assertNotNull(WicketIdPsi.pair(java.idValue("title")))
        assertNull(WicketIdRenameTarget.at(java, java.text.indexOf("\"title\"") + 1))
        assertNull(WicketIdRenameTarget.at(html, html.text.indexOf("\"title\"") + 1))
    }

    fun testComparisonAfterConstructionStillProtectsTheOriginalId() {
        val java = page("String id = \"title\"; Label label = new Label(id); if (id.equals(\"title\")) add(label);")
        val html = companion()
        assertEquals(DiagnosticCode.MATCHED, WicketEditorAnalysis.pages(java).single().report.matching!!.findings.single().code)
        assertNull(WicketIdRenameTarget.at(html, html.text.indexOf("\"title\"") + 1))
    }

    fun testCompletionDoesNotChangeAnIdThatControlsItsOwnBranch() {
        val java = page("String id = \"\"; if (id.equals(\"\")) add(new Label(id));")
        companion()
        assertTrue(complete(java).isEmpty())
    }

    fun testCompletionDoesNotReplaceOneFragmentOfAComputedId() {
        val java = page("add(new Label(\"\" + \"\"));")
        companion()
        assertTrue(complete(java).isEmpty())
    }

    fun testIndependentEmptyLiteralIdStillReceivesCompletion() {
        val java = page("if (1 < 2) add(new Label(\"\"));")
        companion()
        assertEquals(listOf("title"), complete(java).map { it.lookupString })
    }

    fun testAnInputSharedWithAnotherComputedIdCannotBeRenamedPerPair() {
        val java = page("String id = \"title\"; add(new Label(id), new Label(id + \"Extra\"));")
        val html = companion(html = "<span wicket:id=\"title\"></span><span wicket:id=\"titleExtra\"></span>")
        assertEquals(2, WicketEditorAnalysis.pages(java).single().report.matching!!.findings.count { it.code == DiagnosticCode.MATCHED })
        assertNull(WicketIdRenameTarget.at(html, html.text.indexOf("\"title\"") + 1))
        assertNull(WicketIdRenameTarget.at(java, java.text.indexOf("\"title\"") + 1))
    }

    fun testComputationSourceEditsInvalidateSnapshotsAndReanalysis() {
        val settings = myFixture.addFileToProject("src/main/java/example/Settings.java", """
            package example; public class Settings {
                private final String prefix;
                public Settings(String prefix) { this.prefix = prefix; }
                public String id(String suffix) { return prefix + suffix; }
            }
        """.trimIndent())
        val java = page("add(new Label(new Settings(\"ti\").id(\"tle\")));")
        companion()
        service.analyze(java.virtualFile.url, -1); await()
        val prepared = service.state.prepared!!
        assertEquals(3, prepared.version.sources.size)
        assertFalse(service.state.stale)
        WicketEditorAnalysis.pages(java)
        assertTrue(prepared.version.isCurrent(project, prepared.version.changeStamp))
        WriteCommandAction.runWriteCommandAction(project) {
            PsiDocumentManager.getInstance(project).getDocument(settings)!!.setText(settings.text.replace("prefix + suffix", "prefix + \"changed\""))
        }
        assertTrue(service.state.stale)
        assertFalse(service.copyContext())
        assertFalse(service.navigate(prepared.report.children().single().javaLocation))
        PsiDocumentManager.getInstance(project).commitAllDocuments()
        service.reanalyze(); await()
        assertEquals("tichanged", service.state.prepared!!.report.children().single().wicketId)
    }

    fun testUnknownComputedIdsDoNotProduceFalseProblems() {
        val java = page("add(new Label(\"item\" + value));", parameters = "int value")
        val html = companion()
        assertTrue(problems(java).results.isEmpty())
        assertTrue(problems(html).results.isEmpty())
        assertNull(WicketIdRenameTarget.at(html, html.text.indexOf("\"title\"") + 1))
    }

    private fun complete(java: PsiFile) = run {
        myFixture.configureFromExistingVirtualFile(java.virtualFile)
        myFixture.editor.caretModel.moveToOffset(java.text.indexOf("\"\"") + 1)
        myFixture.completeBasic().orEmpty().filter { it.getUserData(WicketIdCompletionContributor.CANDIDATE) == true }
    }
    private fun page(body: String, members: String = "", parameters: String = "") = myFixture.addFileToProject("src/main/java/example/ExamplePage.java", """
        package example; import org.apache.wicket.markup.html.basic.Label;
        public class ExamplePage extends org.apache.wicket.markup.html.WebPage { public ExamplePage($parameters) { $body } $members }
    """.trimIndent()) as PsiJavaFile
    private fun await() {
        PlatformTestUtil.waitWithEventsDispatching("Scalar expression report", { !service.state.running }, 15)
        assertNotNull(service.state.toString(), service.state.prepared)
    }
    private fun problems(file: PsiFile) = ProblemsHolder(InspectionManager.getInstance(project), file, false).also {
        WicketUnpairedIdInspection().buildVisitor(it, false).visitFile(file)
    }
}
