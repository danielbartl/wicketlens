package dev.wicketlens

import com.google.gson.JsonParser
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.psi.*
import dev.wicketlens.editor.*
import dev.wicketlens.export.WicketContextExporter
import dev.wicketlens.mcp.*
import dev.wicketlens.model.DiagnosticCode

class WicketFragmentEditorMcpTest : WicketProjectPsiTestCase() {
    private fun fixture(): Pair<PsiFile, PsiFile> {
        val java = pageFile(body = """
            org.apache.wicket.markup.html.panel.Fragment a = new org.apache.wicket.markup.html.panel.Fragment("first", "shared", this);
            a.add(new Label("title")); add(a);
            org.apache.wicket.markup.html.panel.Fragment b = new org.apache.wicket.markup.html.panel.Fragment("second", "shared", this);
            b.add(new Label("title")); add(b);
        """)
        val html = companion(html = """<div wicket:id="first"></div><div wicket:id="second"></div><wicket:fragment wicket:id="shared"><b wicket:id="title"></b></wicket:fragment>""")
        return java to html
    }

    fun testNativeDefinitionAndSharedChildNavigationHasAllTargetsWithoutPairedRename() {
        val (java, html) = fixture()
        val strings = com.intellij.psi.util.PsiTreeUtil.findChildrenOfType(java, PsiLiteralExpression::class.java)
        val uses = strings.filter { it.value == "shared" }
        val definition = html.idValue("shared")
        for (use in uses) assertEquals(definition, use.references.filterIsInstance<WicketIdReference>().single().resolve())
        val reverse = definition.references.filterIsInstance<PsiPolyVariantReference>().single()
        assertEquals(uses.toSet(), reverse.multiResolve(false).map { it.element }.toSet())
        val title = html.idValue("title")
        val targets = title.references.filterIsInstance<PsiPolyVariantReference>().single().multiResolve(false).map { it.element }
        assertEquals(strings.filter { it.value == "title" }.toSet(), targets.toSet())
        assertNull(WicketIdRenameTarget.at(html, definition.textOffset))
        assertNull(WicketIdRenameTarget.at(html, title.textOffset))
    }

    fun testMcpKeepsDefinitionAndTwoInstanceContextsSeparateAndReadOnly() {
        val (java, html) = fixture()
        val before = listOf(java.text, html.text)
        val query = WicketPageAnalysisRequest()
        val snapshot = query.capture(project, "example.ExamplePage")
        val structure = JsonParser.parseString(query.render(snapshot, WicketMcpReport.STRUCTURE)).asJsonObject
        assertEquals(JsonParser.parseString(WicketContextExporter().export(snapshot.report)), structure["analysis"])
        assertEquals(2, structure["analysis"].asJsonObject["fragmentUses"].asJsonArray.size())
        for (path in listOf("first", "second", "first:title", "second:title")) {
            val context = JsonParser.parseString(query.render(snapshot, WicketMcpReport.CONTEXT, path)).asJsonObject["context"].asJsonObject
            assertTrue(context["hierarchyResolved"].asBoolean)
            assertEquals("MATCHED", context["findings"].asJsonArray.single().asJsonObject["code"].asString)
            if (!path.contains(':')) assertEquals("shared", context["component"].asJsonObject["fragment"].asJsonObject["definitionId"].asString)
        }
        val validation = JsonParser.parseString(query.render(snapshot, WicketMcpReport.VALIDATION)).asJsonObject["validation"].asJsonObject
        assertEquals(4, validation["summary"].asJsonObject["findingsByCode"].asJsonObject["MATCHED"].asInt)
        query.requireCurrent(project, snapshot)
        assertEquals(before, listOf(java.text, html.text))
    }

    fun testUnsavedDefinitionEditInvalidatesNativeAndMcpBindings() {
        val (java, html) = fixture()
        val query = WicketPageAnalysisRequest()
        val snapshot = query.capture(project, "example.ExamplePage")
        val manager = PsiDocumentManager.getInstance(project)
        WriteCommandAction.runWriteCommandAction(project) {
            manager.getDocument(html)!!.setText(html.text.replace("wicket:id=\"shared\"", "wicket:id=\"changed\""))
        }
        try { query.requireCurrent(project, snapshot); fail("Expected stale snapshot") }
        catch (e: PageAnalysisFailure) { assertEquals("STALE_SNAPSHOT", e.code) }
        manager.commitAllDocuments()
        val fresh = query.capture(project, "example.ExamplePage")
        assertEquals(2, fresh.report.matching!!.findings.count { it.code == DiagnosticCode.MATCHED })
        assertTrue(fresh.report.fragmentUses.all { it.definitionLocation == null })
        val use = com.intellij.psi.util.PsiTreeUtil.findChildrenOfType(java, PsiLiteralExpression::class.java).first { it.value == "shared" }
        assertNull(use.references.filterIsInstance<WicketIdReference>().single().resolve())
    }
    fun testFragmentProvenanceRetainsTheSharedResponseSizeLimit() {
        fixture()
        val query = WicketPageAnalysisRequest()
        val snapshot = query.capture(project, "example.ExamplePage")
        val use = snapshot.report.fragmentUses.first()
        val oversized = snapshot.copy(report = snapshot.report.copy(fragmentUses = listOf(use.copy(definitionId = "界".repeat(180000)))))
        try { query.render(oversized, WicketMcpReport.STRUCTURE); fail("Expected response bound") }
        catch (e: PageAnalysisFailure) { assertEquals("RESPONSE_TOO_LARGE", e.code) }
    }

}
