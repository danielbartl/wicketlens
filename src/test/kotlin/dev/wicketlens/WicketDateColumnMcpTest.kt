package dev.wicketlens

import com.google.gson.JsonParser
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.psi.*
import dev.wicketlens.export.WicketContextExporter
import dev.wicketlens.mcp.*

class WicketDateColumnMcpTest : WicketProjectPsiTestCase() {
    fun testAllThreeViewsShareEnumStorageNotesAndSdkDependenciesReadOnly() {
        val java=WicketDateColumnFixture.install(myFixture);val before=java.text to java.modificationStamp
        val query=WicketPageAnalysisRequest();val snapshot=query.capture(project,"example.DatePage")
        val structure=JsonParser.parseString(query.render(snapshot)).asJsonObject["analysis"].asJsonObject
        assertEquals(JsonParser.parseString(WicketContextExporter().export(snapshot.report)),structure)
        assertEquals(2,structure["warnings"].asJsonArray.count {"Reviewed JDK FormatStyle enum storage" in it.asJsonObject["message"].asString})
        assertTrue(query.render(snapshot,WicketMcpReport.VALIDATION).contains("Reviewed JDK FormatStyle enum storage"))
        val context=JsonParser.parseString(query.render(snapshot,WicketMcpReport.CONTEXT,"title")).asJsonObject["context"].asJsonObject
        assertEquals(structure["warnings"],context["warnings"])
        assertEquals(listOf("title"),snapshot.report.nodes.mapNotNull {it.wicketId})
        for(path in listOf("/java/time/format/FormatStyle.class","/java/lang/Enum.class")) assertTrue(path,snapshot.version.sources.any {it.path.endsWith(path)})
        query.requireCurrent(project,snapshot);assertEquals(before,java.text to java.modificationStamp)
    }
    private fun stale(before:String,after:String,commit:Boolean) {
        WicketDateColumnFixture.install(myFixture)
        val query=WicketPageAnalysisRequest();val snapshot=query.capture(project,"example.DatePage")
        val file=PsiManager.getInstance(project).findFile(myFixture.findFileInTempDir("src/main/java/example/DateColumn.java"))!!
        assertTrue(before in file.text)
        WriteCommandAction.runWriteCommandAction(project) {PsiDocumentManager.getInstance(project).getDocument(file)!!.setText(file.text.replace(before,after))}
        if(commit) PsiDocumentManager.getInstance(project).commitAllDocuments()
        try {query.requireCurrent(project,snapshot);fail("Expected stale enum storage")}
        catch(e:PageAnalysisFailure){assertEquals("STALE_SNAPSHOT",e.code)}
    }
    fun testUncommittedStyleDefaultEditsInvalidateSnapshots()=stale("FormatStyle.MEDIUM","FormatStyle.LONG",false)
    fun testCommittedEnumImportEditsInvalidateSnapshots()=stale("import java.time.format.FormatStyle;","import java.time.format.FormatStyle;import static java.time.format.FormatStyle.SHORT;",true)
    fun testDeferredRowFunctionEditsInvalidateSnapshots()=stale("value->value","value->value.plusDays(1)",true)
    fun testAllThreeViewsKeepTheWholeResponseBound() {
        WicketDateColumnFixture.install(myFixture)
        val query=WicketPageAnalysisRequest();val snapshot=query.capture(project,"example.DatePage")
        val report=snapshot.report.copy(warnings=snapshot.report.warnings+dev.wicketlens.model.Diagnostic(dev.wicketlens.model.DiagnosticCode.PARTIAL_ANALYSIS,"x".repeat(600000)))
        for(view in listOf(WicketMcpReport.STRUCTURE,WicketMcpReport.VALIDATION,WicketMcpReport.CONTEXT)) {
            try {query.render(snapshot.copy(report=report),view,if(view==WicketMcpReport.CONTEXT) "title" else null);fail("Expected response bound")}
            catch(e:PageAnalysisFailure){assertEquals("RESPONSE_TOO_LARGE",e.code)}
        }
    }
}
