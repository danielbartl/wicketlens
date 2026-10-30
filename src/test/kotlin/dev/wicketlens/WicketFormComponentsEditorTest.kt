package dev.wicketlens

import com.intellij.codeInspection.InspectionManager
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.components.service
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiJavaFile
import com.intellij.refactoring.rename.RenameProcessor
import com.intellij.testFramework.PlatformTestUtil
import dev.wicketlens.editor.*
import dev.wicketlens.inspection.WicketUnpairedIdInspection
import dev.wicketlens.model.DiagnosticCode
import dev.wicketlens.ui.WicketLensService

class WicketFormComponentsEditorTest : WicketProjectPsiTestCase() {
    override fun tearDown() {
        try { project.service<WicketLensService>().cancel() } finally { super.tearDown() }
    }

    fun testOuterAndInnerReferencesResolveToTheirOwnMarkup() {
        val component = input()
        val inner = companion("Input", "<wicket:panel><input wicket:id=\"value\"/></wicket:panel>")
        val page = pageFile(body = "add(new Input(\"title\"));")
        val outer = companion(html = "<div wicket:id=\"title\"><input wicket:id=\"preview\"/></div>")
        assertEquals(outer.idValue("title"), page.idValue("title").references.filterIsInstance<WicketIdReference>().single().resolve())
        assertEquals(inner.idValue("value"), component.idValue("value").references.filterIsInstance<WicketIdReference>().single().resolve())
        assertEquals(component.idValue("value"), inner.idValue("value").references.filterIsInstance<WicketIdReference>().single().resolve())
        assertNull(WicketIdPsi.pair(outer.idValue("preview")))
    }

    fun testNativeInnerRenameChangesOnlyTheCompositePair() {
        val component = input()
        val inner = companion("Input", "<wicket:panel><input wicket:id=\"value\"/></wicket:panel>")
        val page = pageFile(body = "add(new Input(\"title\"));")
        val outer = companion()
        val target = WicketIdRenameTarget.at(component, component.text.indexOf("\"value\"") + 1)!!
        RenameProcessor(project, target, "inputValue", false, false).run()
        PsiDocumentManager.getInstance(project).commitAllDocuments()
        assertTrue(component.text.contains("\"inputValue\""))
        assertTrue(inner.text.contains("wicket:id=\"inputValue\""))
        assertTrue(page.text.contains("\"title\""))
        assertTrue(outer.text.contains("wicket:id=\"title\""))
    }

    fun testNativeOuterRenameDoesNotChangeCompositeInternals() {
        val component = input()
        val inner = companion("Input", "<wicket:panel><input wicket:id=\"value\"/></wicket:panel>")
        val page = pageFile(body = "add(new Input(\"title\"));")
        val outer = companion()
        val target = WicketIdRenameTarget.at(page, page.text.indexOf("\"title\"") + 1)!!
        RenameProcessor(project, target, "editor", false, false).run()
        PsiDocumentManager.getInstance(project).commitAllDocuments()
        assertTrue(page.text.contains("\"editor\""))
        assertTrue(outer.text.contains("wicket:id=\"editor\""))
        assertTrue(component.text.contains("\"value\""))
        assertTrue(inner.text.contains("wicket:id=\"value\""))
    }

    fun testInspectionsIgnorePreviewIdsButReportMissingInternalIds() {
        val component = input()
        val inner = companion("Input", "<wicket:panel><input wicket:id=\"missing\"/></wicket:panel>")
        val outer = companion(html = "<div wicket:id=\"title\"><input wicket:id=\"preview\"/></div>")
        pageFile(body = "add(new Input(\"title\"));")
        fun problems(file: PsiFile): ProblemsHolder {
            val holder = ProblemsHolder(InspectionManager.getInstance(project), file, false)
            WicketUnpairedIdInspection().buildVisitor(holder, false).visitFile(file)
            return holder
        }
        assertEmpty(problems(outer).results)
        assertEquals(1, problems(component).results.size)
        assertEquals(1, problems(inner).results.size)
    }

    fun testCompletionKeepsParentAndInternalCandidatesSeparate() {
        val component = input(id = "")
        companion("Input", "<wicket:panel><input wicket:id=\"value\"/></wicket:panel>")
        val page = pageFile(body = "add(new Input(\"\"));")
        companion(html = "<div wicket:id=\"title\"><input wicket:id=\"preview\"/></div>")
        assertEquals(listOf("value"), WicketIdCompletionContributor().candidates(component.idValue("")))
        assertEquals(listOf("title"), WicketIdCompletionContributor().candidates(page.idValue("")))
    }

    fun testEditingCompositeContractInvalidatesTheParentSnapshot() {
        val component = input()
        val page = pageFile(body = "add(new Input(\"title\"));")
        companion()
        val service = project.service<WicketLensService>()
        service.setEditorFollowing(false)
        service.analyze(page.virtualFile.url, -1)
        PlatformTestUtil.waitWithEventsDispatching("Composite report", { !service.state.running }, 15)
        assertNotNull(service.state.toString(), service.state.prepared)
        assertTrue(service.state.prepared!!.version.sources.any { it.path == component.virtualFile.path })
        WriteCommandAction.runWriteCommandAction(project) {
            PsiDocumentManager.getInstance(project).getDocument(component)!!.setText(component.text.replace("super(id)", "super(id + \"x\")"))
        }
        assertTrue(service.state.stale)
        PsiDocumentManager.getInstance(project).commitAllDocuments()
        val report = WicketEditorAnalysis.pages(page).single().report
        assertTrue(report.matching!!.findings.none { it.code == DiagnosticCode.MATCHED })
    }

    private fun input(id: String = "value") = myFixture.addFileToProject("src/main/java/example/Input.java", """
        package example;
        public class Input extends org.apache.wicket.markup.html.form.FormComponentPanel<String> {
            public Input(String id) { super(id); add(new org.apache.wicket.markup.html.form.TextField<String>("$id")); }
        }
    """.trimIndent()) as PsiJavaFile
}
