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

class WicketAccessorEditorTest : WicketProjectPsiTestCase() {
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

    fun testCachedProviderAndScalarGetterPreserveBidirectionalReferences() {
        accessors()
        val java = page("title")
        val html = companion()
        assertEquals(html.idValue("title"), WicketIdPsi.pair(java.idValue("title"))!!.attribute)
        assertEquals(java.idValue("title"), WicketIdPsi.pair(html.idValue("title"))!!.literal)
    }

    fun testPairedRenameChangesTheCallerLiteralAndMarkup() {
        val getter = accessors()
        val before = getter.text
        val java = page("title")
        val html = companion()
        val target = WicketIdRenameTarget.at(java, java.text.indexOf("\"title\"") + 1)!!
        RenameProcessor(project, target, "heading", false, false).run()
        PsiDocumentManager.getInstance(project).commitAllDocuments()
        assertTrue(java.text.contains("new Settings(\"heading\")"))
        assertTrue(html.text.contains("wicket:id=\"heading\""))
        assertEquals(before, getter.text)
    }

    fun testCompletionFollowsEmptyIdsThroughBothKindsOfAccessor() {
        accessors()
        val java = page("")
        companion()
        myFixture.configureFromExistingVirtualFile(java.virtualFile)
        myFixture.editor.caretModel.moveToOffset(java.text.indexOf("\"\"") + 1)
        val candidates = myFixture.completeBasic().orEmpty().filter { it.getUserData(WicketIdCompletionContributor.CANDIDATE) == true }
        assertEquals(listOf("title"), candidates.map { it.lookupString })
        myFixture.lookup.currentItem = candidates.single(); myFixture.finishLookup('\n')
        PsiDocumentManager.getInstance(project).commitAllDocuments()
        assertEquals(DiagnosticCode.MATCHED, WicketEditorAnalysis.pages(java).single().report.matching!!.findings.single().code)
    }

    fun testAccessorOriginsDriveColoringAndPreciseProblemRanges() {
        accessors()
        val java = page("javaOnly")
        val html = companion(html = "<span wicket:id=\"htmlOnly\"></span>")
        assertNotNull(WicketIdPsi.javaId(java.idValue("javaOnly")))
        for ((file, id) in listOf(java to "javaOnly", html to "htmlOnly")) {
            val holder = problems(file)
            assertEquals(1, holder.results.size)
            assertEquals(file.idValue(id).textRange, holder.results.single().psiElement.textRange)
        }
    }

    fun testGetterSourceEditsInvalidateSnapshotsAndReanalysis() {
        val getter = accessors()
        val java = page("title")
        companion()
        service.analyze(java.virtualFile.url, -1); await()
        val prepared = service.state.prepared!!
        assertEquals(5, prepared.version.sources.size)
        assertTrue(prepared.version.sources.any { it.path == getter.virtualFile.path })
        edit(getter, getter.text.replace("return id;", "return \"changed\";"), commit = false)
        assertTrue(service.state.stale)
        assertFalse(service.copyContext())
        assertFalse(service.navigate(prepared.report.children().single().javaLocation))
        PsiDocumentManager.getInstance(project).commitAllDocuments()
        service.reanalyze(); await()
        assertEquals("changed", service.state.prepared!!.report.children().single().wicketId)
    }

    fun testSharedGetterLiteralDoesNotAcquireAPerPairRename() {
        accessors("return \"title\";")
        val java = page("unused")
        val html = companion()
        assertEquals(DiagnosticCode.MATCHED, WicketEditorAnalysis.pages(java).single().report.matching!!.findings.single().code)
        assertNull(WicketIdPsi.javaId(java.idValue("unused")))
        assertNull(WicketIdRenameTarget.at(html, html.text.indexOf("\"title\"") + 1))
    }

    fun testOneAccessorOriginFeedingSeveralPathsCannotBeRenamedPerPair() {
        accessors()
        val java = myFixture.addFileToProject("src/main/java/example/ExamplePage.java", """
            package example; public class ExamplePage extends org.apache.wicket.markup.html.WebPage {
                public ExamplePage() {
                    Holder holder = new Holder(new Settings("name"));
                    org.apache.wicket.markup.html.WebMarkupContainer left = new org.apache.wicket.markup.html.WebMarkupContainer("left");
                    org.apache.wicket.markup.html.WebMarkupContainer right = new org.apache.wicket.markup.html.WebMarkupContainer("right");
                    left.add(holder.settings().build()); right.add(holder.settings().build()); add(left, right);
                }
            }
        """.trimIndent())
        val html = companion(html = "<div><div wicket:id=\"left\"><span wicket:id=\"name\"></span></div><div wicket:id=\"right\"><span wicket:id=\"name\"></span></div></div>")
        assertEquals(4, WicketEditorAnalysis.pages(java).single().report.matching!!.findings.count { it.code == DiagnosticCode.MATCHED })
        assertNull(WicketIdRenameTarget.at(java, java.text.indexOf("\"name\"") + 1))
        assertNull(WicketIdRenameTarget.at(html, html.text.indexOf("\"name\"") + 1))
    }

    fun testMutableGetterDoesNotProduceFalseProblemsOrRename() {
        accessors()
        val java = myFixture.addFileToProject("src/main/java/example/ExamplePage.java", """
            package example; public class ExamplePage extends org.apache.wicket.markup.html.WebPage {
                private String id = "title";
                private String id() { return id; }
                public ExamplePage() { add(new org.apache.wicket.markup.html.basic.Label(id())); }
            }
        """.trimIndent())
        val html = companion()
        assertTrue(problems(java).results.isEmpty())
        assertTrue(problems(html).results.isEmpty())
        assertNull(WicketIdRenameTarget.at(html, html.text.indexOf("\"title\"") + 1))
    }

    private fun accessors(body: String = "return id;"): PsiFile {
        myFixture.addFileToProject("src/main/java/example/Holder.java", """
            package example; public class Holder {
                private final Settings settings;
                public Holder(Settings settings) { this.settings = settings; }
                public Settings settings() { return settings; }
            }
        """.trimIndent())
        val file = myFixture.addFileToProject("src/main/java/example/Settings.java", """
            package example; public class Settings {
                private final String id;
                public Settings(String id) { this.id = id; }
                public String id() { $body }
                public org.apache.wicket.markup.html.basic.Label build() { return new org.apache.wicket.markup.html.basic.Label(id()); }
            }
        """.trimIndent())
        // A separate source caller ensures getter dependencies participate in freshness.
        myFixture.addFileToProject("src/main/java/example/Access.java", "package example; public class Access { public static Settings settings(Holder holder) { return holder.settings(); } }")
        return file
    }
    private fun page(id: String) = myFixture.addFileToProject("src/main/java/example/ExamplePage.java", """
        package example; public class ExamplePage extends org.apache.wicket.markup.html.WebPage {
            private final Holder holder = new Holder(new Settings("$id"));
            public ExamplePage() { add(Access.settings(holder).build()); }
        }
    """.trimIndent()) as PsiJavaFile
    private fun edit(file: PsiFile, text: String, commit: Boolean = true) {
        WriteCommandAction.runWriteCommandAction(project) { PsiDocumentManager.getInstance(project).getDocument(file)!!.setText(text) }
        if (commit) PsiDocumentManager.getInstance(project).commitAllDocuments()
    }
    private fun await() {
        PlatformTestUtil.waitWithEventsDispatching("Accessor report", { !service.state.running }, 15)
        assertNotNull(service.state.toString(), service.state.prepared)
    }
    private fun problems(file: PsiFile) = ProblemsHolder(InspectionManager.getInstance(project), file, false).also {
        WicketUnpairedIdInspection().buildVisitor(it, false).visitFile(file)
    }
}
