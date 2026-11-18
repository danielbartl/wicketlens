package dev.wicketlens

import com.google.gson.JsonParser
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.psi.*
import com.intellij.psi.util.PsiTreeUtil
import dev.wicketlens.analysis.*
import dev.wicketlens.editor.WicketIdReference
import dev.wicketlens.export.WicketContextExporter
import dev.wicketlens.mcp.*
import dev.wicketlens.model.DiagnosticCode

class WicketOwnedFormMcpTest : WicketProjectPsiTestCase() {
    private fun fixture(): List<PsiFile> {
        val field = myFixture.addFileToProject("src/main/java/example/Field.java", """
            package example; public class Field extends org.apache.wicket.markup.html.panel.Panel {
                private org.apache.wicket.markup.html.form.TextField<String> input;
                private static final org.apache.wicket.MetaDataKey<java.util.Set<Field>> KEY = new org.apache.wicket.MetaDataKey<>() {};
                public Field(String id) { super(id); }
                public org.apache.wicket.markup.html.form.TextField<String> getInput() {
                    if (input == null) { input = create(); add(input); }
                    return input;
                }
                private org.apache.wicket.markup.html.form.TextField<String> create() { return new org.apache.wicket.markup.html.form.TextField<>("input"); }
                public Field required() { getInput().setRequired(true); return this; }
                @Override protected void onInitialize() { super.onInitialize(); getInput(); }
            }
        """)
        val page = pageFile(body = "add(new Field(\"field\").required());")
        val html = companion(html = "<div wicket:id='field'></div>")
        return listOf(field, page, html)
    }

    fun testSharedNativeAndMcpFactsMatchWithoutExpandingThePrivateInput() {
        val files = fixture(); val texts = files.map { it.text }; val stamps = files.map { it.modificationStamp }
        val query = WicketPageAnalysisRequest(); val snapshot = query.capture(project, "example.ExamplePage")
        val native = WicketPageAnalyzer().analyze((files[1] as PsiJavaFile).classes.single())!!
        val structure = JsonParser.parseString(query.render(snapshot)).asJsonObject
        assertEquals(JsonParser.parseString(WicketContextExporter().export(native)), structure["analysis"])
        val context = JsonParser.parseString(query.render(snapshot, WicketMcpReport.CONTEXT, "field")).asJsonObject["context"].asJsonObject
        assertTrue(context["hierarchyResolved"].asBoolean)
        assertEquals("MATCHED", context["findings"].asJsonArray.single().asJsonObject["code"].asString)
        assertTrue(context["children"].asJsonArray.isEmpty)
        assertTrue(context["component"].asJsonObject["diagnostics"].asJsonArray.any { it.asJsonObject["message"].asString.contains("internal inputs") })
        val validation = JsonParser.parseString(query.render(snapshot, WicketMcpReport.VALIDATION)).asJsonObject["validation"].asJsonObject
        assertEquals(1, validation["summary"].asJsonObject["findingsByCode"].asJsonObject["MATCHED"].asInt)
        try { query.render(snapshot, WicketMcpReport.CONTEXT, "field:input"); fail("No internal input path is established") }
        catch (e: PageAnalysisFailure) { assertEquals("COMPONENT_NOT_FOUND", e.code) }
        val literal = PsiTreeUtil.findChildrenOfType(files[1], PsiLiteralExpression::class.java).single { it.value == "field" }
        val target = literal.references.filterIsInstance<WicketIdReference>().single().resolve()!!
        assertEquals(literal, target.references.filterIsInstance<WicketIdReference>().single().resolve())
        query.requireCurrent(project, snapshot)
        assertEquals(texts, files.map { it.text }); assertEquals(stamps, files.map { it.modificationStamp })
    }

    fun testUnsavedInputFactoryChangesInvalidateBothBindingSurfaces() {
        val files = fixture(); val query = WicketPageAnalysisRequest(); val old = query.capture(project, "example.ExamplePage")
        assertTrue(old.version.sources.any { it.path == files[0].virtualFile.path })
        val manager = PsiDocumentManager.getInstance(project)
        WriteCommandAction.runWriteCommandAction(project) { manager.getDocument(files[0])!!.setText(files[0].text.replace("return new org.apache.wicket.markup.html.form.TextField", "getPage().removeAll(); return new org.apache.wicket.markup.html.form.TextField")) }
        try { query.requireCurrent(project, old); fail("Expected stale snapshot") }
        catch (e: PageAnalysisFailure) { assertEquals("STALE_SNAPSHOT", e.code) }
        manager.commitAllDocuments()
        val fresh = query.capture(project, "example.ExamplePage")
        assertTrue(fresh.report.matching!!.findings.none { it.code == DiagnosticCode.MATCHED })
        val literal = PsiTreeUtil.findChildrenOfType(files[1], PsiLiteralExpression::class.java).single { it.value == "field" }
        assertTrue(literal.references.filterIsInstance<WicketIdReference>().all { it.multiResolve(false).isEmpty() })
    }

    fun testOwnedFormAnalysisPropagatesCancellation() {
        val files = fixture(); val type = (files[1] as PsiJavaFile).classes.single()
        assertTrue(WicketPageAnalyzer().analyze(type)!!.matching!!.findings.any { it.code == DiagnosticCode.MATCHED })
        val indicator = com.intellij.openapi.progress.EmptyProgressIndicator()
        try {
            com.intellij.openapi.progress.ProgressManager.getInstance().runProcess(Runnable {
                indicator.cancel(); WicketLayoutSetupAnalyzer(factories = true).analyze(type, "page")
            }, indicator)
            fail("Expected cancellation")
        } catch (_: com.intellij.openapi.progress.ProcessCanceledException) { }
    }
}
