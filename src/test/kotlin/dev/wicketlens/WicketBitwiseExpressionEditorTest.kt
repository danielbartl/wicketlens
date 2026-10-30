package dev.wicketlens

import com.intellij.codeInsight.CodeInsightSettings
import com.intellij.codeInspection.InspectionManager
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.psi.*
import com.intellij.refactoring.rename.RenameProcessor
import com.intellij.testFramework.PlatformTestUtil
import dev.wicketlens.editor.*
import dev.wicketlens.inspection.WicketUnpairedIdInspection
import dev.wicketlens.model.DiagnosticCode
import dev.wicketlens.ui.WicketLensService

class WicketBitwiseExpressionEditorTest : WicketProjectPsiTestCase() {
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

    fun testComputedShiftIdProblemHighlightsTheWholeExpressionWithoutFragmentFixes() {
        val java = pageFile(body = "add(new Label(\"item\" + (1 << 1)));")
        companion(html = "<span wicket:id=\"htmlOnly\"></span>")
        val problem = problems(java).results.single()
        assertEquals("\"item\" + (1 << 1)", problem.psiElement.text)
        assertTrue(problem.fixes.orEmpty().isEmpty())
    }

    fun testBitwiseComputedIdDoesNotGiveAFragmentALiteralPair() {
        val java = pageFile(body = "add(new Label(\"item\" + (~(-3) & 7)));")
        val html = companion(html = "<span wicket:id=\"item2\"></span>")
        val report = WicketEditorAnalysis.pages(java).single().report
        assertEquals(DiagnosticCode.MATCHED, report.matching!!.findings.single().code)
        assertNull(WicketIdPsi.javaId(java.idValue("item")))
        assertNull(WicketIdPsi.pair(html.idValue("item2")))
        assertNull(WicketIdRenameTarget.at(html, html.text.indexOf("\"item2\"") + 1))
    }

    fun testIndependentLiteralRenameStillWorksUnderAMaskCondition() {
        val java = pageFile(body = "if (((1 << 2) & 4) != 0) add(new Label(\"title\"));")
        val html = companion()
        val target = WicketIdRenameTarget.at(java, java.text.indexOf("\"title\"") + 1)!!
        RenameProcessor(project, target, "heading", false, false).run()
        PsiDocumentManager.getInstance(project).commitAllDocuments()
        assertTrue(java.text.contains("((1 << 2) & 4)"))
        assertTrue(java.text.contains("Label(\"heading\")"))
        assertTrue(html.text.contains("wicket:id=\"heading\""))
    }

    fun testIndependentEmptyLiteralCompletionWorksAfterAuditedEagerOr() {
        val java = myFixture.addFileToProject("src/main/java/example/ExamplePage.java", """
            package example; import org.apache.wicket.markup.html.basic.Label;
            public class ExamplePage extends org.apache.wicket.markup.html.WebPage {
                public ExamplePage(boolean enabled) { if (enabled | true) add(new Label("")); }
            }
        """.trimIndent())
        companion()
        myFixture.configureFromExistingVirtualFile(java.virtualFile)
        myFixture.editor.caretModel.moveToOffset(java.text.indexOf("\"\"") + 1)
        val candidates = myFixture.completeBasic().orEmpty().filter { it.getUserData(WicketIdCompletionContributor.CANDIDATE) == true }
        assertEquals(listOf("title"), candidates.map { it.lookupString })
    }

    fun testUnsavedShiftConstantEditsInvalidateTheSnapshotAndChangeTheBranch() {
        val flags = myFixture.addFileToProject("src/main/java/example/Flags.java", "package example; public class Flags { public static final int MASK = 1 << 1; }")
        val java = pageFile(body = "if ((Flags.MASK & 2) != 0) add(new Label(\"title\"));")
        companion()
        service.analyze(java.virtualFile.url, -1); await()
        val prepared = service.state.prepared!!
        assertEquals(DiagnosticCode.MATCHED, prepared.report.matching!!.findings.single().code)
        assertTrue(prepared.version.sources.any { it.path == flags.virtualFile.path })
        WriteCommandAction.runWriteCommandAction(project) {
            PsiDocumentManager.getInstance(project).getDocument(flags)!!.setText(flags.text.replace("<< 1", "<< 2"))
        }
        assertTrue(service.state.stale)
        assertFalse(service.copyContext())
        assertFalse(service.navigate(prepared.report.children().single().javaLocation))
        PsiDocumentManager.getInstance(project).commitAllDocuments()
        service.reanalyze(); await()
        assertTrue(service.state.prepared!!.report.children().isEmpty())
        assertEquals(DiagnosticCode.MARKUP_ONLY, service.state.prepared!!.report.matching!!.findings.single().code)
    }

    fun testEagerBooleanEffectsCannotCreateFalseMissingPairProblems() {
        val java = myFixture.addFileToProject("src/main/java/example/ExamplePage.java", """
            package example; import org.apache.wicket.markup.html.basic.Label;
            public class ExamplePage extends org.apache.wicket.markup.html.WebPage {
                public ExamplePage() { add(new Label("title")); if (true | effect()) removeAll(); }
                private boolean effect() { removeAll(); return false; }
            }
        """.trimIndent())
        val html = companion()
        assertTrue(problems(java).results.isEmpty())
        assertTrue(problems(html).results.isEmpty())
    }

    fun testLiteralUsedByAnEagerPredicateStillKeepsItsRenameGuard() {
        val java = pageFile(body = "String id = \"title\"; Label label = new Label(id); if (id.equals(\"title\") | true) add(label);")
        val html = companion()
        val report = WicketEditorAnalysis.pages(java).single().report
        assertEquals(DiagnosticCode.MATCHED, report.matching!!.findings.single().code)
        assertTrue(report.children().single().idUsedInComputation)
        assertNotNull(WicketIdPsi.pair(java.idValue("title")))
        assertNull(WicketIdRenameTarget.at(java, java.text.indexOf("\"title\"") + 1))
        assertNull(WicketIdRenameTarget.at(html, html.text.indexOf("\"title\"") + 1))
    }

    fun testPossiblyNullEagerOperandDoesNotProduceFalseMissingPairProblems() {
        val java = myFixture.addFileToProject("src/main/java/example/ExamplePage.java", """
            package example; import org.apache.wicket.markup.html.basic.Label;
            public class ExamplePage extends org.apache.wicket.markup.html.WebPage {
                public ExamplePage(Boolean enabled) { if (enabled | true) add(new Label("javaOnly")); }
            }
        """.trimIndent())
        val html = companion(html = "<span wicket:id=\"htmlOnly\"></span>")
        assertTrue(problems(java).results.isEmpty())
        assertTrue(problems(html).results.isEmpty())
    }

    private fun await() {
        PlatformTestUtil.waitWithEventsDispatching("Bitwise expression report", { !service.state.running }, 15)
        assertNotNull(service.state.toString(), service.state.prepared)
    }
    private fun problems(file: PsiFile) = ProblemsHolder(InspectionManager.getInstance(project), file, false).also {
        WicketUnpairedIdInspection().buildVisitor(it, false).visitFile(file)
    }
}
