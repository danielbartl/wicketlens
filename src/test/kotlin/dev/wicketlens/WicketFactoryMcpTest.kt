package dev.wicketlens

import com.google.gson.JsonParser
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.psi.*
import dev.wicketlens.analysis.WicketPageAnalyzer
import dev.wicketlens.export.WicketContextExporter
import dev.wicketlens.mcp.*
import dev.wicketlens.model.*

class WicketFactoryMcpTest : WicketProjectPsiTestCase() {
    private fun fixture(): List<PsiFile> {
        val type = myFixture.addFileToProject("src/main/java/example/Box.java", """
            package example; public class Box extends org.apache.wicket.markup.html.panel.Panel {
                public Box(String id, java.util.function.Function<String,org.apache.wicket.Component> factory) {
                    super(id); add(factory.apply("child"));
                }
            }
        """)
        val own = myFixture.addFileToProject("src/main/resources/example/Box.html", "<wicket:panel><span wicket:id=\"child\"></span></wicket:panel>")
        val page = pageFile(body = "add(new Box(\"box\", id -> new Label(id))); ")
        val html = companion(html = "<div wicket:id=\"box\"></div>")
        return listOf(type, own, page, html)
    }

    fun testExistingMcpViewsReuseOwnedMarkupBindingsAndRemainReadOnly() {
        val files = fixture()
        val text = files.map { it.text }; val stamps = files.map { it.modificationStamp }
        val query = WicketPageAnalysisRequest()
        val snapshot = query.capture(project, "example.ExamplePage")
        val native = WicketPageAnalyzer().analyze((files[2] as PsiJavaFile).classes.single())!!
        val structure = JsonParser.parseString(query.render(snapshot, WicketMcpReport.STRUCTURE)).asJsonObject
        assertEquals(JsonParser.parseString(WicketContextExporter().export(native)), structure["analysis"])
        val context = JsonParser.parseString(query.render(snapshot, WicketMcpReport.CONTEXT, "box:child")).asJsonObject["context"].asJsonObject
        assertTrue(context["hierarchyResolved"].asBoolean)
        assertEquals("MATCHED", context["findings"].asJsonArray.single().asJsonObject["code"].asString)
        assertEquals(files[1].virtualFile.path, context["parent"].asJsonObject["ownedMarkupPath"].asString)
        val validation = JsonParser.parseString(query.render(snapshot, WicketMcpReport.VALIDATION)).asJsonObject["validation"].asJsonObject
        assertEquals(2, validation["summary"].asJsonObject["findingsByCode"].asJsonObject["MATCHED"].asInt)
        assertTrue(snapshot.report.markup!!.sourceFiles.contains(files[1].virtualFile.path))
        query.requireCurrent(project, snapshot)
        assertEquals(text, files.map { it.text }); assertEquals(stamps, files.map { it.modificationStamp })
    }

    fun testUnsavedOwnedMarkupEditInvalidatesTheSharedSnapshot() {
        val files = fixture(); val query = WicketPageAnalysisRequest()
        val old = query.capture(project, "example.ExamplePage")
        val manager = PsiDocumentManager.getInstance(project)
        WriteCommandAction.runWriteCommandAction(project) { manager.getDocument(files[1])!!.setText(files[1].text.replace("child", "changed")) }
        try { query.requireCurrent(project, old); fail("Expected stale snapshot") }
        catch (e: PageAnalysisFailure) { assertEquals("STALE_SNAPSHOT", e.code) }
        manager.commitAllDocuments()
        val fresh = query.capture(project, "example.ExamplePage")
        assertTrue(fresh.report.matching!!.findings.none { it.code == DiagnosticCode.MATCHED && it.javaNodeId == fresh.report.nodes.single { n -> n.wicketId == "child" }.nodeId })
    }

    fun testUnsavedFactoryEditCannotReuseEarlierAttachments() {
        val files = fixture(); val query = WicketPageAnalysisRequest()
        val old = query.capture(project, "example.ExamplePage")
        val manager = PsiDocumentManager.getInstance(project)
        WriteCommandAction.runWriteCommandAction(project) { manager.getDocument(files[2])!!.setText(files[2].text.replace("id -> new Label(id)", "id -> { removeAll(); return new Label(id); }")) }
        manager.commitAllDocuments()
        try { query.requireCurrent(project, old); fail("Expected stale snapshot") }
        catch (e: PageAnalysisFailure) { assertEquals("STALE_SNAPSHOT", e.code) }
        assertTrue(query.capture(project, "example.ExamplePage").report.matching!!.findings.none { it.code == DiagnosticCode.MATCHED })
    }

    fun testOwnedMarkupMetadataRetainsTheSharedResponseSizeLimit() {
        fixture()
        val query = WicketPageAnalysisRequest()
        val snapshot = query.capture(project, "example.ExamplePage")
        val oversized = snapshot.copy(report = snapshot.report.copy(nodes = snapshot.report.nodes.map {
            if (it.sourceSetupExpanded) it.copy(ownedMarkupPath = "界".repeat(180000)) else it
        }))
        try { query.render(oversized, WicketMcpReport.STRUCTURE); fail("Expected response bound") }
        catch (e: PageAnalysisFailure) { assertEquals("RESPONSE_TOO_LARGE", e.code) }
    }
}
