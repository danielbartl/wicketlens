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

class WicketConditionalEditorTest : WicketProjectPsiTestCase() {
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

    fun testSelectedBranchOriginsResolveInBothDirections() {
        flags()
        val java = page()
        val html = companion()
        assertEquals(html.idValue("title"), WicketIdPsi.pair(java.idValue("title"))!!.attribute)
        assertEquals(java.idValue("title"), WicketIdPsi.pair(html.idValue("title"))!!.literal)
        assertNull(WicketIdPsi.pair(java.idValue("inactive")))
    }

    fun testNativeRenameChangesOnlyTheSelectedCallerIdAndMarkup() {
        val flags = flags()
        val before = flags.text
        val java = page()
        val html = companion()
        val target = WicketIdRenameTarget.at(java, java.text.indexOf("\"title\"") + 1)!!
        RenameProcessor(project, target, "heading", false, false).run()
        PsiDocumentManager.getInstance(project).commitAllDocuments()
        assertTrue(java.text.contains("label(flags, \"heading\", \"inactive\")"))
        assertTrue(html.text.contains("wicket:id=\"heading\""))
        assertEquals(before, flags.text)
    }

    fun testCompletionFollowsAnEmptyIdIntoTheSelectedFactoryBranch() {
        flags()
        val java = page("")
        companion()
        complete(java)
    }

    fun testCompletionWorksForTheSelectedTernaryIdLiteral() {
        val java = simple("add(new org.apache.wicket.markup.html.basic.Label(true ? \"\" : \"inactive\"));")
        companion()
        complete(java)
    }

    fun testTernaryIdSupportsNativePairedRename() {
        val java = simple("add(new org.apache.wicket.markup.html.basic.Label(true ? \"title\" : \"inactive\"));")
        val html = companion()
        val target = WicketIdRenameTarget.at(java, java.text.indexOf("\"title\"") + 1)!!
        RenameProcessor(project, target, "heading", false, false).run()
        PsiDocumentManager.getInstance(project).commitAllDocuments()
        assertTrue(java.text.contains("true ? \"heading\" : \"inactive\""))
        assertTrue(html.text.contains("wicket:id=\"heading\""))
    }

    fun testProvenImmutableScalarFieldOriginsAreRecognizedForColoring() {
        val java = myFixture.addFileToProject("src/main/java/example/ExamplePage.java", """
            package example; public class ExamplePage extends org.apache.wicket.markup.html.WebPage {
                private final String id = "title";
                private final String caption = "Caption";
                private String id() { return id; }
                public ExamplePage() { if (true) add(new org.apache.wicket.markup.html.basic.Label(id(), caption)); }
            }
        """.trimIndent())
        companion()
        assertNotNull(WicketIdPsi.javaId(java.idValue("title")))
        assertNull(WicketIdPsi.javaId(java.idValue("Caption")))
        assertNull(WicketIdRenameTarget.at(java, java.text.indexOf("\"title\"") + 1))
    }

    fun testSelectedOriginsDriveColoringAndPreciseProblemRanges() {
        flags()
        val java = page("javaOnly")
        val html = companion(html = "<span wicket:id=\"htmlOnly\"></span>")
        assertNotNull(WicketIdPsi.javaId(java.idValue("javaOnly")))
        assertNull(WicketIdPsi.javaId(java.idValue("inactive")))
        for ((file, id) in listOf(java to "javaOnly", html to "htmlOnly")) {
            val holder = problems(file)
            assertEquals(1, holder.results.size)
            assertEquals(file.idValue(id).textRange, holder.results.single().psiElement.textRange)
        }
    }

    fun testConditionSourceEditsInvalidateTheSnapshotAndSwitchTheSelectedBranch() {
        val flags = flags()
        val java = page()
        companion()
        service.analyze(java.virtualFile.url, -1); await()
        val prepared = service.state.prepared!!
        assertEquals(3, prepared.version.sources.size)
        assertTrue(prepared.version.sources.any { it.path == flags.virtualFile.path })
        edit(flags, flags.text.replace("return enabled;", "return !enabled;"), commit = false)
        assertTrue(service.state.stale)
        assertFalse(service.copyContext())
        assertFalse(service.navigate(prepared.report.children().single().javaLocation))
        PsiDocumentManager.getInstance(project).commitAllDocuments()
        service.reanalyze(); await()
        assertEquals("inactive", service.state.prepared!!.report.children().single().wicketId)
    }

    fun testAProvenMissingJavaBindingProducesAMarkupProblem() {
        simple("if (false) add(new org.apache.wicket.markup.html.basic.Label(\"title\"));")
        val html = companion()
        assertEquals(DiagnosticCode.MARKUP_ONLY, WicketEditorAnalysis.pages(html).single().report.matching!!.findings.single().code)
        val holder = problems(html)
        assertEquals(1, holder.results.size)
        assertEquals(html.idValue("title").textRange, holder.results.single().psiElement.textRange)
        assertNull(WicketIdRenameTarget.at(html, html.text.indexOf("\"title\"") + 1))
    }

    fun testUnknownBranchesDoNotProduceDefinitiveProblemsOrPairedRename() {
        val java = simple("if (enabled) add(new org.apache.wicket.markup.html.basic.Label(\"title\"));", "boolean enabled")
        val html = companion()
        assertTrue(problems(java).results.isEmpty())
        assertTrue(problems(html).results.isEmpty())
        assertNull(WicketIdRenameTarget.at(java, java.text.indexOf("\"title\"") + 1))
        assertNull(WicketIdRenameTarget.at(html, html.text.indexOf("\"title\"") + 1))
    }

    fun testASelectedSharedImplementationLiteralDoesNotAcquireACallerRename() {
        val java = myFixture.addFileToProject("src/main/java/example/ExamplePage.java", """
            package example; public class ExamplePage extends org.apache.wicket.markup.html.WebPage {
                public ExamplePage() { add(label(true, "unused")); }
                private org.apache.wicket.markup.html.basic.Label label(boolean enabled, String ignored) {
                    if (enabled) return new org.apache.wicket.markup.html.basic.Label("title");
                    return new org.apache.wicket.markup.html.basic.Label("other");
                }
            }
        """.trimIndent())
        val html = companion()
        assertEquals(DiagnosticCode.MATCHED, WicketEditorAnalysis.pages(java).single().report.matching!!.findings.single().code)
        assertNull(WicketIdPsi.javaId(java.idValue("unused")))
        assertNull(WicketIdRenameTarget.at(html, html.text.indexOf("\"title\"") + 1))
    }

    fun testUnknownBranchesWithIdenticalIdsDoNotChooseASingleRenameOrigin() {
        val java = simple("if (enabled) add(new org.apache.wicket.markup.html.basic.Label(\"title\")); else add(new org.apache.wicket.markup.html.basic.Label(\"title\"));", "boolean enabled")
        val html = companion()
        assertTrue(problems(java).results.isEmpty())
        assertTrue(problems(html).results.isEmpty())
        assertNull(WicketIdRenameTarget.at(html, html.text.indexOf("\"title\"") + 1))
    }

    private fun complete(java: PsiFile) {
        myFixture.configureFromExistingVirtualFile(java.virtualFile)
        myFixture.editor.caretModel.moveToOffset(java.text.indexOf("\"\"") + 1)
        val candidates = myFixture.completeBasic().orEmpty().filter { it.getUserData(WicketIdCompletionContributor.CANDIDATE) == true }
        assertEquals(listOf("title"), candidates.map { it.lookupString })
        myFixture.lookup.currentItem = candidates.single(); myFixture.finishLookup('\n')
        PsiDocumentManager.getInstance(project).commitAllDocuments()
        assertEquals(DiagnosticCode.MATCHED, WicketEditorAnalysis.pages(java).single().report.matching!!.findings.single().code)
    }
    private fun flags() = myFixture.addFileToProject("src/main/java/example/Flags.java", """
        package example; public class Flags {
            private final boolean enabled;
            public Flags(boolean enabled) { this.enabled = enabled; }
            public boolean enabled() { return enabled; }
        }
    """.trimIndent())
    private fun page(id: String = "title") = myFixture.addFileToProject("src/main/java/example/ExamplePage.java", """
        package example; public class ExamplePage extends org.apache.wicket.markup.html.WebPage {
            public ExamplePage() { Flags flags = new Flags(true); add(label(flags, "$id", "inactive")); }
            private org.apache.wicket.markup.html.basic.Label label(Flags flags, String primary, String other) {
                if (flags.enabled()) return new org.apache.wicket.markup.html.basic.Label(primary);
                return new org.apache.wicket.markup.html.basic.Label(other);
            }
        }
    """.trimIndent()) as PsiJavaFile
    private fun simple(body: String, parameters: String = "") = myFixture.addFileToProject("src/main/java/example/ExamplePage.java", """
        package example; public class ExamplePage extends org.apache.wicket.markup.html.WebPage {
            public ExamplePage($parameters) { $body }
        }
    """.trimIndent()) as PsiJavaFile
    private fun edit(file: PsiFile, text: String, commit: Boolean = true) {
        WriteCommandAction.runWriteCommandAction(project) { PsiDocumentManager.getInstance(project).getDocument(file)!!.setText(text) }
        if (commit) PsiDocumentManager.getInstance(project).commitAllDocuments()
    }
    private fun await() {
        PlatformTestUtil.waitWithEventsDispatching("Conditional report", { !service.state.running }, 15)
        assertNotNull(service.state.toString(), service.state.prepared)
    }
    private fun problems(file: PsiFile) = ProblemsHolder(InspectionManager.getInstance(project), file, false).also {
        WicketUnpairedIdInspection().buildVisitor(it, false).visitFile(file)
    }
}
