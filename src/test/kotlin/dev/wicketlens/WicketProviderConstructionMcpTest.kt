package dev.wicketlens

import com.google.gson.JsonParser
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.psi.*
import dev.wicketlens.export.WicketContextExporter
import dev.wicketlens.mcp.*

class WicketProviderConstructionMcpTest : WicketProjectPsiTestCase() {
    fun testThreeViewsShareConstructionOnlyDiagnosticsAndBinaryDependenciesReadOnly() {
        val java=WicketProviderFixture.install(myFixture);val before=java.text to java.modificationStamp
        val query=WicketPageAnalysisRequest();val snapshot=query.capture(project,"example.ProviderPage")
        val structure=JsonParser.parseString(query.render(snapshot)).asJsonObject["analysis"].asJsonObject
        assertEquals(JsonParser.parseString(WicketContextExporter().export(snapshot.report)),structure)
        assertTrue(structure["warnings"].asJsonArray.any {"Reviewed source data-provider construction" in it.asJsonObject["message"].asString})
        assertTrue(query.render(snapshot,WicketMcpReport.VALIDATION).contains("Reviewed source data-provider construction"))
        val context=JsonParser.parseString(query.render(snapshot,WicketMcpReport.CONTEXT,"title")).asJsonObject["context"].asJsonObject
        assertEquals(structure["warnings"],context["warnings"])
        assertEquals(listOf("title"),snapshot.report.nodes.mapNotNull {it.wicketId})
        for(name in listOf("wicket-extensions-11.0.0.jar","wicket-core-11.0.0.jar","wicket-util-11.0.0.jar")) assertTrue(name,snapshot.version.sources.any {it.path.endsWith(name)})
        query.requireCurrent(project,snapshot);assertEquals(before,java.text to java.modificationStamp)
    }
    private fun stale(before:String,after:String,commit:Boolean) {
        val java=WicketProviderFixture.install(myFixture)
        val query=WicketPageAnalysisRequest();val snapshot=query.capture(project,"example.ProviderPage")
        assertTrue(before in java.text)
        WriteCommandAction.runWriteCommandAction(project) {PsiDocumentManager.getInstance(project).getDocument(java)!!.setText(java.text.replace(before,after))}
        if(commit) PsiDocumentManager.getInstance(project).commitAllDocuments()
        try {query.requireCurrent(project,snapshot);fail("Expected stale provider source")}
        catch(e:PageAnalysisFailure){assertEquals("STALE_SNAPSHOT",e.code)}
    }
    fun testUncommittedConstructorChangesInvalidateSnapshots()=stale("@Override public java.util.Iterator","Rows(){super();}@Override public java.util.Iterator",false)
    fun testDeferredIteratorChangesInvalidateSnapshots()=stale("return filtered().iterator();","return java.util.List.<String>of().iterator();",true)
    fun testPageQueryChangesInvalidateSnapshots()=stale("java.util.List.of(\"row\")","java.util.List.of(\"changed\")",true)
    fun testThreeViewsKeepTheWholeResponseBound() {
        WicketProviderFixture.install(myFixture)
        val query=WicketPageAnalysisRequest();val snapshot=query.capture(project,"example.ProviderPage")
        val report=snapshot.report.copy(warnings=snapshot.report.warnings+dev.wicketlens.model.Diagnostic(dev.wicketlens.model.DiagnosticCode.PARTIAL_ANALYSIS,"x".repeat(600000)))
        for(view in listOf(WicketMcpReport.STRUCTURE,WicketMcpReport.VALIDATION,WicketMcpReport.CONTEXT)) {
            try {query.render(snapshot.copy(report=report),view,if(view==WicketMcpReport.CONTEXT) "title" else null);fail("Expected response bound")}
            catch(e:PageAnalysisFailure){assertEquals("RESPONSE_TOO_LARGE",e.code)}
        }
    }
}
