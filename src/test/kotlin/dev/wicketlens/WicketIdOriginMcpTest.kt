package dev.wicketlens

import com.google.gson.JsonParser
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.psi.*
import dev.wicketlens.analysis.*
import dev.wicketlens.export.WicketContextExporter
import dev.wicketlens.mcp.*
import dev.wicketlens.model.DiagnosticCode

class WicketIdOriginMcpTest : WicketProjectPsiTestCase() {
    fun testSharedOriginFactsAreReadOnlyAndFreshAcrossHelperEdits() {
        val helper = myFixture.addFileToProject("src/main/java/example/Decorated.java", """
            package example; public class Decorated extends org.apache.wicket.markup.html.WebMarkupContainer {
                public Decorated(String id) { super(id); add(new org.apache.wicket.behavior.Behavior()); }
            }
        """)
        val java = pageFile(body = "add(new Decorated(\"title\"));"); val html = companion()
        val files = listOf(java, html, helper); val before = files.map { it.text to it.modificationStamp }
        val query = WicketPageAnalysisRequest(); val snapshot = query.capture(project, "example.ExamplePage")
        val structure = JsonParser.parseString(query.render(snapshot)).asJsonObject
        assertEquals(JsonParser.parseString(WicketContextExporter().export(WicketPageAnalyzer().analyze(java.classes.single())!!)), structure["analysis"])
        val context = JsonParser.parseString(query.render(snapshot, WicketMcpReport.CONTEXT, "title")).asJsonObject["context"].asJsonObject
        assertTrue(snapshot.report.nodes.single { it.wicketId == "title" }.idRenameSafe)
        assertFalse(context["component"].asJsonObject.has("idRenameSafe"))
        assertEquals(before, files.map { it.text to it.modificationStamp })
        assertTrue(snapshot.version.sources.any { it.path == helper.virtualFile.path })
        WriteCommandAction.runWriteCommandAction(project) {
            PsiDocumentManager.getInstance(project).getDocument(helper)!!.setText(helper.text.replace("super(id);", "super(id); org.apache.wicket.model.Model.of(id);"))
        }
        try { query.requireCurrent(project, snapshot); fail("Expected stale snapshot") }
        catch (e: PageAnalysisFailure) { assertEquals("STALE_SNAPSHOT", e.code) }
        PsiDocumentManager.getInstance(project).commitAllDocuments()
        val fresh = query.capture(project, "example.ExamplePage")
        assertFalse(fresh.report.nodes.single { it.wicketId == "title" }.idRenameSafe)
        assertEquals(DiagnosticCode.MATCHED, fresh.report.matching!!.findings.single().code)
        val validation = JsonParser.parseString(query.render(fresh, WicketMcpReport.VALIDATION)).asJsonObject["validation"].asJsonObject
        assertEquals(1, validation["summary"].asJsonObject["findingsByCode"].asJsonObject["MATCHED"].asInt)
    }
}
