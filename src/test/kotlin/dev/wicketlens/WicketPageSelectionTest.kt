package dev.wicketlens

import com.intellij.psi.PsiJavaFile
import dev.wicketlens.analysis.AnalysisUnavailable
import dev.wicketlens.analysis.WicketPageSelection

class WicketPageSelectionTest : WicketProjectPsiTestCase() {
    fun testSingleEligiblePageIsSelectedWithoutAnEditorCaret() {
        val file = pageFile()
        companion()
        val report = WicketPageSelection().analyze(project, file.virtualFile.url, -1)
        assertEquals("example.ExamplePage", report.qualifiedClassName)
        assertEquals("MATCHED", report.matching?.findings?.single()?.code?.name)
    }

    fun testCaretSelectsTheCorrectClassWhenAFileHasMultiplePages() {
        val file = myFixture.addFileToProject("src/main/java/example/First.java", """
            package example;
            class First extends org.apache.wicket.markup.html.WebPage {}
            class Second extends org.apache.wicket.markup.html.WebPage {}
        """.trimIndent()) as PsiJavaFile
        val selection = WicketPageSelection()
        assertEquals("example.Second", selection.analyze(project, file.virtualFile.url, file.text.indexOf("Second")).qualifiedClassName)
        assertThrows(AnalysisUnavailable::class.java) { selection.analyze(project, file.virtualFile.url, -1) }
    }

    fun testCaretInsideAnonymousLabelStillSelectsItsEnclosingPage() {
        val file = pageFile(body = "add(new Label(\"title\") { public String toString() { return \"inside\"; } });")
        companion()
        val report = WicketPageSelection().analyze(project, file.virtualFile.url, file.text.indexOf("inside"))
        assertEquals("example.ExamplePage", report.qualifiedClassName)
    }

    fun testUnavailableOrNonWicketSourcesProduceActionableMessages() {
        val file = myFixture.addFileToProject("src/main/java/example/Plain.java", "package example; class Plain {}")
        assertThrows(AnalysisUnavailable::class.java) { WicketPageSelection().analyze(project, file.virtualFile.url, -1) }
        assertThrows(AnalysisUnavailable::class.java) { WicketPageSelection().analyze(project, "file:///missing/WicketLensPage.java", -1) }
    }
}
