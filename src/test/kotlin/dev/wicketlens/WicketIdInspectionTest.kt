package dev.wicketlens

import com.intellij.codeInspection.InspectionManager
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.profile.codeInspection.InspectionProjectProfileManager
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.psi.impl.PsiDocumentManagerEx
import dev.wicketlens.inspection.WicketIdLineMarkerProvider
import dev.wicketlens.inspection.WicketIdProblems
import dev.wicketlens.inspection.WicketUnpairedIdInspection

class WicketIdInspectionTest : WicketProjectPsiTestCase() {
    private val inspection = WicketUnpairedIdInspection()

    override fun setUp() {
        super.setUp()
        myFixture.enableInspections(inspection)
    }

    override fun tearDown() {
        try {
            FileEditorManager.getInstance(project).openFiles.forEach { FileEditorManager.getInstance(project).closeFile(it) }
        } finally { super.tearDown() }
    }

    fun testNativeJavaInspectionHighlightsOnlyTheUnpairedLiteralAsAWeakWarning() {
        val java = pageFile(body = "add(new Label(\"title\"), new Label(\"orphan\"));")
        companion()
        val problems = highlights(java)
        assertEquals(1, problems.size)
        assertEquals(HighlightSeverity.WEAK_WARNING, problems.single().severity)
        assertEquals("\"orphan\"", java.text.substring(problems.single().startOffset, problems.single().endOffset))
        assertTrue(problems.single().description!!.contains("HTML markup"))
        assertTrue(problems.single().description!!.contains("Analysis is partial"))
    }

    fun testNativeHtmlInspectionHighlightsTheAttributeValueOnAMultilineTag() {
        pageFile()
        val html = companion(html = "<html><body><span wicket:id=\"title\"></span>\n<div\n class='other'\n wicket:id='orphan'></div></body></html>")
        val problem = highlights(html).single()
        assertEquals(HighlightSeverity.WEAK_WARNING, problem.severity)
        assertEquals("'orphan'", html.text.substring(problem.startOffset, problem.endOffset))
        assertTrue(problem.description!!.contains("Java component setup"))
        val marker = marker(html, problem.startOffset)
        assertNotNull(marker)
        assertEquals(3, PsiDocumentManager.getInstance(project).getDocument(html)!!.getLineNumber(marker!!.startOffset))
        assertNull(marker.element!!.firstChild)
    }

    fun testBatchInspectionUsesTheSameFindingsWithoutTheSidebar() {
        val java = pageFile(body = "add(new Label(\"javaOnly\"));")
        val html = companion(html = "<span wicket:id=\"htmlOnly\"></span>")
        for (file in listOf(java, html)) {
            val holder = ProblemsHolder(InspectionManager.getInstance(project), file, false)
            inspection.buildVisitor(holder, false).visitFile(file)
            assertEquals(1, holder.results.size)
        }
    }

    fun testMatchedIdsHaveNeitherProblemsNorGutterIcons() {
        val java = pageFile()
        val html = companion()
        assertEmpty(highlights(java))
        assertEmpty(highlights(html))
        assertNull(marker(java, java.text.indexOf("\"title\"")))
        assertNull(marker(html, html.text.indexOf("\"title\"")))
    }

    fun testDynamicIdsDoNotTurnUncertainMarkupIntoProblems() {
        val java = pageFile(body = "add(new Label(System.getProperty(\"id\")));")
        val html = companion()
        assertEmpty(highlights(java))
        assertEmpty(highlights(html))
    }

    fun testUnsupportedRootEffectsSuppressBothUnpairedDirections() {
        val java = pageFile(body = "add(new Label(\"javaOnly\")); removeAll();")
        val html = companion(html = "<span wicket:id=\"htmlOnly\"></span>")
        assertEmpty(highlights(java))
        assertEmpty(highlights(html))
    }

    fun testMissingOrAmbiguousCompanionsAreExcluded() {
        val java = pageFile()
        assertEmpty(WicketIdProblems.forFile(java))
        val html = companion(html = "<div></div>")
        assertEquals(1, WicketIdProblems.forFile(java).size)
        myFixture.addFileToProject("src/main/java/example/ExamplePage.html", "<div></div>")
        assertEmpty(WicketIdProblems.forFile(java))
        assertEmpty(WicketIdProblems.forFile(html))
    }

    fun testNonWicketFilesAndUnpairedHtmlAreIgnored() {
        val java = myFixture.addFileToProject("src/main/java/example/Ordinary.java", "package example; public class Ordinary { String id = \"title\"; }")
        val html = companion("Ordinary")
        assertEmpty(highlights(java))
        assertEmpty(highlights(html))
    }

    fun testMatchingUsesFullParentPaths() {
        val java = pageFile(body = """
            org.apache.wicket.markup.html.form.Form<?> form = new org.apache.wicket.markup.html.form.Form<>("form");
            form.add(new Label("title")); add(form);
        """.trimIndent())
        val html = companion(html = "<form wicket:id=\"form\"></form><span wicket:id=\"title\"></span>")
        assertEquals(1, highlights(java).size)
        assertEquals(1, highlights(html).size)
    }

    fun testTrackedFieldsHighlightTheConstructionInsteadOfTheAttachment() {
        val java = myFixture.addFileToProject("src/main/java/example/ExamplePage.java", """
            package example;
            public class ExamplePage extends org.apache.wicket.markup.html.WebPage {
                private final org.apache.wicket.markup.html.basic.Label title = new org.apache.wicket.markup.html.basic.Label("title");
                public ExamplePage() { add(title); }
            }
        """.trimIndent())
        companion(html = "<div></div>")
        val problem = highlights(java).single()
        assertEquals(java.text.indexOf("\"title\""), problem.startOffset)
    }

    fun testEditingOppositeFileInvalidatesCacheAndClearsThenCreatesProblems() {
        val java = pageFile()
        val html = companion(html = "<div></div>")
        val before = WicketIdProblems.forFile(java)
        assertEquals(1, before.size)
        assertSame(before, WicketIdProblems.forFile(java))
        edit(html, "<span wicket:id=\"title\"></span>")
        assertEmpty(WicketIdProblems.forFile(java))
        assertEmpty(WicketIdProblems.forFile(html))
        edit(java, java.text.replace("\"title\"", "\"newTitle\""))
        assertTrue(WicketIdProblems.forFile(java).single().message.contains("'newTitle'"))
        assertTrue(WicketIdProblems.forFile(html).single().message.contains("'title'"))
    }

    fun testUncommittedOppositeFileCannotYieldStaleProblems() {
        val java = pageFile()
        val html = companion(html = "<div></div>")
        assertEquals(1, WicketIdProblems.forFile(java).size)
        val manager = PsiDocumentManager.getInstance(project)
        (manager as PsiDocumentManagerEx).disableBackgroundCommit(testRootDisposable)
        val document = manager.getDocument(html)!!
        WriteCommandAction.runWriteCommandAction(project) { document.setText("<span wicket:id=\"title\"></span>") }
        try {
            assertFalse(manager.isCommitted(document))
            assertEmpty(WicketIdProblems.forFile(java))
        } finally { manager.commitAllDocuments() }
        assertEmpty(WicketIdProblems.forFile(java))
    }

    fun testGutterUsesOneLeafIconPerLineWithBothMessagesAndEscapedIds() {
        val java = pageFile(body = "add(new Label(\"<first>\"), new Label(\"second\"));")
        companion(html = "<div></div>")
        val problems = WicketIdProblems.forFile(java)
        assertEquals(2, problems.size)
        val first = marker(java, problems.first().start)!!
        assertNull(first.element!!.firstChild)
        assertTrue(first.lineMarkerTooltip!!.contains("&lt;first&gt;"))
        assertTrue(first.lineMarkerTooltip!!.contains("second"))
        assertNull(marker(java, problems.last().start))
    }

    fun testJavaSuppressionRemovesInspectionAndGutter() {
        val java = pageFile(body = "\n//noinspection WicketUnpairedId\nadd(new Label(\"orphan\"));")
        companion(html = "<div></div>")
        assertEmpty(highlights(java))
        assertNull(marker(java, java.text.indexOf("\"orphan\"")))
    }

    fun testDisabledInspectionAlsoHidesItsGutterIcons() {
        val java = pageFile()
        companion(html = "<div></div>")
        val profile = InspectionProjectProfileManager.getInstance(project).currentProfile
        profile.setToolEnabled(WicketUnpairedIdInspection.SHORT_NAME, false, project)
        try {
            assertNull(marker(java, java.text.indexOf("\"title\"")))
            assertEmpty(highlights(java))
        } finally { profile.setToolEnabled(WicketUnpairedIdInspection.SHORT_NAME, true, project) }
    }

    fun testPanelInspectionIgnoresPreviewMarkup() {
        val java = myFixture.addFileToProject("src/main/java/example/ExamplePanel.java", """
            package example;
            public class ExamplePanel extends org.apache.wicket.markup.html.panel.Panel {
                public ExamplePanel(String id) { super(id); add(new org.apache.wicket.markup.html.basic.Label("javaOnly")); }
            }
        """.trimIndent())
        val html = companion("ExamplePanel", "<span wicket:id=\"preview\"></span><wicket:panel><span wicket:id=\"htmlOnly\"></span></wicket:panel>")
        assertEquals(1, highlights(java).size)
        val problem = highlights(html).single()
        assertTrue(problem.description!!.contains("htmlOnly"))
        assertFalse(problem.description!!.contains("preview"))
    }

    fun testIncompleteMarkupAndDuplicatePathsDoNotProduceMissingPairWarnings() {
        val java = pageFile()
        val html = companion(html = "<div wicket:id=\"unknown\"")
        assertEmpty(highlights(java))
        assertEmpty(highlights(html))
        edit(java, java.text.replace("add(new Label(\"title\"));", "add(new Label(\"title\"), new Label(\"title\"));"))
        edit(html, "<span wicket:id=\"title\"></span>")
        assertEmpty(highlights(java))
        assertEmpty(highlights(html))
    }

    fun testDeletingAndRecreatingCompanionInvalidatesExistingDiagnostics() {
        val java = pageFile()
        val html = companion(html = "<div></div>")
        assertEquals(1, WicketIdProblems.forFile(java).size)
        WriteCommandAction.runWriteCommandAction(project) { html.delete() }
        assertEmpty(WicketIdProblems.forFile(java))
        companion()
        assertEmpty(WicketIdProblems.forFile(java))
    }

    fun testSourceInheritedSetupReportsOnlyTheCurrentFilesUnpairedIds() {
        myFixture.addFileToProject("src/main/java/example/BasePage.java", """
            package example; public class BasePage extends org.apache.wicket.markup.html.WebPage {}
        """.trimIndent())
        companion("BasePage", "<div wicket:id=\"baseOnly\"></div><wicket:child/>")
        val java = myFixture.addFileToProject("src/main/java/example/ChildPage.java", """
            package example;
            public class ChildPage extends BasePage {
                public ChildPage() { add(new org.apache.wicket.markup.html.basic.Label("javaOnly")); }
            }
        """.trimIndent())
        val html = companion("ChildPage", "<wicket:extend><span wicket:id=\"childOnly\"></span></wicket:extend>")
        assertEquals(1, highlights(java).size)
        val problem = highlights(html).single()
        assertTrue(problem.description!!.contains("childOnly"))
        assertFalse(problem.description!!.contains("baseOnly"))
    }

    private fun highlights(file: PsiFile): List<com.intellij.codeInsight.daemon.impl.HighlightInfo> {
        myFixture.configureFromExistingVirtualFile(file.virtualFile)
        return myFixture.doHighlighting().filter { it.description?.startsWith("Possible unpaired Wicket ID") == true }
    }

    private fun marker(file: PsiFile, offset: Int) = WicketIdLineMarkerProvider().getLineMarkerInfo(file.findElementAt(offset)!!)

    private fun edit(file: PsiFile, text: String) {
        WriteCommandAction.runWriteCommandAction(project) { PsiDocumentManager.getInstance(project).getDocument(file)!!.setText(text) }
        PsiDocumentManager.getInstance(project).commitAllDocuments()
    }
}
