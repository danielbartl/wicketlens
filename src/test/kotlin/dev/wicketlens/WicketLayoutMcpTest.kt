package dev.wicketlens

import com.google.gson.JsonParser
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import dev.wicketlens.editor.WicketIdPsi
import dev.wicketlens.export.WicketContextExporter
import dev.wicketlens.mcp.*
import dev.wicketlens.model.DiagnosticCode

class WicketLayoutMcpTest : WicketProjectPsiTestCase() {
    private fun fixture(): List<PsiFile> {
        val behavior = myFixture.addFileToProject("src/main/java/example/Decoration.java", "package example; public class Decoration extends org.apache.wicket.behavior.Behavior {}")
        val htmlType = myFixture.addFileToProject("src/main/java/example/Html.java", """
            package example; public class Html extends org.apache.wicket.markup.html.TransparentWebMarkupContainer {
                public Html(String id) { super(id); add(new Decoration()); }
            }
        """.trimIndent())
        val java = pageFile(body = "add(new Html(\"html\"), new Label(\"title\"));")
        val html = companion(html = "<html wicket:id=\"html\"><head><title wicket:id=\"title\"></title></head></html>")
        return listOf(behavior, htmlType, java, html)
    }

    fun testExistingToolsAndNativeReferencesConsumeTheSameNewBindingsWithoutWrites() {
        val files = fixture()
        val before = files.map { it.text }
        val stamps = files.map { it.modificationStamp }
        val query = WicketPageAnalysisRequest()
        val snapshot = query.capture(project, "example.ExamplePage")
        assertEquals(2, snapshot.report.matching!!.findings.count { it.code == DiagnosticCode.MATCHED })
        assertTrue(snapshot.report.javaSourceFiles.containsAll(files.take(3).map { it.virtualFile.path }))
        assertTrue(snapshot.report.javaSourceFiles.any { it.endsWith(".jar") })
        val structure = JsonParser.parseString(query.render(snapshot, WicketMcpReport.STRUCTURE)).asJsonObject
        assertEquals(1, structure["schemaVersion"].asInt)
        assertEquals(JsonParser.parseString(WicketContextExporter().export(snapshot.report)), structure["analysis"])
        val validation = JsonParser.parseString(query.render(snapshot, WicketMcpReport.VALIDATION)).asJsonObject["validation"].asJsonObject
        assertEquals(2, validation["summary"].asJsonObject["findingsByCode"].asJsonObject["MATCHED"].asInt)
        val context = JsonParser.parseString(query.render(snapshot, WicketMcpReport.CONTEXT, "title")).asJsonObject["context"].asJsonObject
        assertEquals("title", context["componentPath"].asString)
        assertEquals("MATCHED", context["findings"].asJsonArray.single().asJsonObject["code"].asString)
        assertTrue(context["hierarchyResolved"].asBoolean)
        assertEquals(files.last().idValue("title"), WicketIdPsi.pair(files[2].idValue("title"))?.attribute)
        assertEquals(before, files.map { it.text })
        assertEquals(stamps, files.map { it.modificationStamp })
        query.requireCurrent(project, snapshot)
    }

    fun testUnsavedBehaviorBindingEditInvalidatesTheMcpSnapshotAndPreventsPromotion() {
        val files = fixture()
        val query = WicketPageAnalysisRequest()
        val snapshot = query.capture(project, "example.ExamplePage")
        val manager = PsiDocumentManager.getInstance(project)
        WriteCommandAction.runWriteCommandAction(project) {
            manager.getDocument(files[0])!!.setText("""
                package example; public class Decoration extends org.apache.wicket.behavior.Behavior {
                    @Override public void bind(org.apache.wicket.Component component) { ((org.apache.wicket.MarkupContainer)component).removeAll(); }
                }
            """.trimIndent())
        }
        try { query.requireCurrent(project, snapshot); fail("Expected stale snapshot") }
        catch (failure: PageAnalysisFailure) { assertEquals("STALE_SNAPSHOT", failure.code) }
        manager.commitAllDocuments()
        assertTrue(query.capture(project, "example.ExamplePage").report.matching!!.findings.none { it.code == DiagnosticCode.MATCHED })
    }

    fun testUnsavedTransparentResolverEditInvalidatesItsMarkupContract() {
        val files = fixture()
        val query = WicketPageAnalysisRequest()
        val snapshot = query.capture(project, "example.ExamplePage")
        val manager = PsiDocumentManager.getInstance(project)
        WriteCommandAction.runWriteCommandAction(project) {
            val document = manager.getDocument(files[1])!!
            document.setText(document.text.replace("public Html", """
                @Override public org.apache.wicket.Component resolve(org.apache.wicket.MarkupContainer parent,
                    org.apache.wicket.markup.MarkupStream stream, org.apache.wicket.markup.ComponentTag tag) { return null; }
                public Html
            """.trimIndent()))
        }
        manager.commitAllDocuments()
        try { query.requireCurrent(project, snapshot); fail("Expected stale snapshot") }
        catch (failure: PageAnalysisFailure) { assertEquals("STALE_SNAPSHOT", failure.code) }
        val fresh = query.capture(project, "example.ExamplePage").report
        assertTrue(fresh.matching!!.findings.none { it.code == DiagnosticCode.MATCHED })
    }
}
