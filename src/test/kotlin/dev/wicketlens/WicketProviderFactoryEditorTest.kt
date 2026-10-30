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

class WicketProviderFactoryEditorTest : WicketProjectPsiTestCase() {
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

    fun testProviderFactoryIdReferencesResolveInBothDirections() {
        factory()
        val java = page("add(Factory.create(\"title\").build());")
        val html = companion()
        assertEquals(html.idValue("title"), WicketIdPsi.pair(java.idValue("title"))!!.attribute)
        assertEquals(java.idValue("title"), WicketIdPsi.pair(html.idValue("title"))!!.literal)
    }

    fun testNativeRenameChangesTheProviderFactoryCallAndMarkup() {
        val producer = factory()
        val before = producer.text
        val java = page("add(parts.build());", "private final Parts parts = Factory.create(\"title\");")
        val html = companion()
        val target = WicketIdRenameTarget.at(java, java.text.indexOf("\"title\"") + 1)!!
        RenameProcessor(project, target, "heading", false, false).run()
        PsiDocumentManager.getInstance(project).commitAllDocuments()
        assertTrue(java.text.contains("Factory.create(\"heading\")"))
        assertTrue(html.text.contains("wicket:id=\"heading\""))
        assertEquals(before, producer.text)
    }

    fun testNativeCompletionFollowsAnEmptyIdThroughAProviderFactory() {
        factory()
        val java = page("add(Factory.create(\"\").build());")
        companion()
        myFixture.configureFromExistingVirtualFile(java.virtualFile)
        myFixture.editor.caretModel.moveToOffset(java.text.indexOf("\"\"") + 1)
        val candidates = myFixture.completeBasic().orEmpty().filter { it.getUserData(WicketIdCompletionContributor.CANDIDATE) == true }
        assertEquals(listOf("title"), candidates.map { it.lookupString })
        myFixture.lookup.currentItem = candidates.single(); myFixture.finishLookup('\n')
        PsiDocumentManager.getInstance(project).commitAllDocuments()
        assertEquals(DiagnosticCode.MATCHED, WicketEditorAnalysis.pages(java).single().report.matching!!.findings.single().code)
    }

    fun testProviderFactoryOriginsDriveColoringAndNativeProblemRanges() {
        factory()
        val java = page("add(Factory.create(\"javaOnly\").build());")
        val html = companion(html = "<span wicket:id=\"htmlOnly\"></span>")
        assertNotNull(WicketIdPsi.javaId(java.idValue("javaOnly")))
        for ((file, id) in listOf(java to "javaOnly", html to "htmlOnly")) {
            val holder = problems(file)
            assertEquals(1, holder.results.size)
            assertEquals(file.idValue(id).textRange, holder.results.single().psiElement.textRange)
        }
    }

    fun testProviderFactorySourceEditsInvalidateTheSnapshotAndReanalysis() {
        val producer = factory()
        val java = page("add(Factory.create(\"title\").build());")
        companion()
        service.analyze(java.virtualFile.url, -1); await()
        val prepared = service.state.prepared!!
        assertEquals(5, prepared.version.sources.size)
        assertTrue(prepared.version.sources.any { it.path == producer.virtualFile.path })
        edit(producer, producer.text.replace("new ConfiguredParts(id)", "new ConfiguredParts(\"changed\")"), commit = false)
        assertTrue(service.state.stale)
        assertFalse(service.copyContext())
        assertFalse(service.navigate(prepared.report.children().single().javaLocation))
        PsiDocumentManager.getInstance(project).commitAllDocuments()
        service.reanalyze(); await()
        assertEquals("changed", service.state.prepared!!.report.children().single().wicketId)
    }

    fun testAFactoryArgumentFeedingTwoPathsDoesNotOfferPerPairRename() {
        factory()
        val java = page("""
            Parts parts = Factory.create("name");
            org.apache.wicket.markup.html.WebMarkupContainer left = new org.apache.wicket.markup.html.WebMarkupContainer("left");
            org.apache.wicket.markup.html.WebMarkupContainer right = new org.apache.wicket.markup.html.WebMarkupContainer("right");
            left.add(parts.build()); right.add(parts.build()); add(left, right);
        """)
        val html = companion(html = "<div><div wicket:id=\"left\"><span wicket:id=\"name\"></span></div><div wicket:id=\"right\"><span wicket:id=\"name\"></span></div></div>")
        assertEquals(4, WicketEditorAnalysis.pages(java).single().report.matching!!.findings.count { it.code == DiagnosticCode.MATCHED })
        assertNull(WicketIdRenameTarget.at(java, java.text.indexOf("\"name\"") + 1))
        assertNull(WicketIdRenameTarget.at(html, html.text.indexOf("\"name\"") + 1))
    }

    fun testAFactoryImplementationLiteralDoesNotBecomeACallSiteRename() {
        factory("return new ConfiguredParts(\"title\");")
        val java = page("add(Factory.create(\"unused\").build());")
        val html = companion()
        assertEquals(DiagnosticCode.MATCHED, WicketEditorAnalysis.pages(java).single().report.matching!!.findings.single().code)
        assertNull(WicketIdRenameTarget.at(html, html.text.indexOf("\"title\"") + 1))
        assertNull(WicketIdPsi.javaId(java.idValue("unused")))
    }

    fun testACachedProviderWithASharedImplementationOriginDoesNotOfferPerPairEdits() {
        factory()
        myFixture.addFileToProject("src/main/java/example/Builder.java", """
            package example; public class Builder {
                private final Parts cached = Factory.create("title");
                public Parts create() { return cached; }
            }
        """.trimIndent())
        val java = page("add(new Builder().create().build());")
        val html = companion()
        assertEquals(DiagnosticCode.MATCHED, WicketEditorAnalysis.pages(java).single().report.matching!!.findings.single().code)
        assertTrue(problems(java).results.isEmpty())
        assertTrue(problems(html).results.isEmpty())
        assertNull(WicketIdRenameTarget.at(html, html.text.indexOf("\"title\"") + 1))
    }

    private fun factory(body: String = "return new ConfiguredParts(id);"): PsiFile {
        myFixture.addFileToProject("src/main/java/example/Parts.java", "package example; public interface Parts { org.apache.wicket.markup.html.basic.Label build(); }")
        myFixture.addFileToProject("src/main/java/example/ConfiguredParts.java", """
            package example; public class ConfiguredParts implements Parts {
                private final String id;
                public ConfiguredParts(String id) { this.id = id; }
                public org.apache.wicket.markup.html.basic.Label build() { return new org.apache.wicket.markup.html.basic.Label(id); }
            }
        """.trimIndent())
        return myFixture.addFileToProject("src/main/java/example/Factory.java", "package example; public class Factory { public static Parts create(String id) { $body } }")
    }
    private fun page(body: String, members: String = "") = myFixture.addFileToProject("src/main/java/example/ExamplePage.java", """
        package example; public class ExamplePage extends org.apache.wicket.markup.html.WebPage {
            public ExamplePage() { $body } $members
        }
    """.trimIndent()) as PsiJavaFile
    private fun edit(file: PsiFile, text: String, commit: Boolean = true) {
        WriteCommandAction.runWriteCommandAction(project) { PsiDocumentManager.getInstance(project).getDocument(file)!!.setText(text) }
        if (commit) PsiDocumentManager.getInstance(project).commitAllDocuments()
    }
    private fun await() {
        PlatformTestUtil.waitWithEventsDispatching("Provider factory report", { !service.state.running }, 15)
        assertNotNull(service.state.toString(), service.state.prepared)
    }
    private fun problems(file: PsiFile) = ProblemsHolder(InspectionManager.getInstance(project), file, false).also {
        WicketUnpairedIdInspection().buildVisitor(it, false).visitFile(file)
    }
}
