package dev.wicketlens

import com.google.gson.JsonParser
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.psi.*
import dev.wicketlens.export.WicketContextExporter
import dev.wicketlens.mcp.*

class WicketLambdaColumnMcpTest : WicketProjectPsiTestCase() {
    fun testAllThreeViewsShareNativeStorageDiagnosticsWithoutRowsOrModelValuesReadOnly() {
        val java=WicketLambdaColumnFixture.install(myFixture);val before=java.text to java.modificationStamp
        val query=WicketPageAnalysisRequest();val snapshot=query.capture(project,"example.LambdaPage")
        val structure=JsonParser.parseString(query.render(snapshot)).asJsonObject["analysis"].asJsonObject
        assertEquals(JsonParser.parseString(WicketContextExporter().export(snapshot.report)),structure)
        assertTrue(structure["warnings"].asJsonArray.any {"Reviewed LambdaColumn constructor storage" in it.asJsonObject["message"].asString})
        assertTrue(query.render(snapshot,WicketMcpReport.VALIDATION).contains("Reviewed LambdaColumn constructor storage"))
        val context=JsonParser.parseString(query.render(snapshot,WicketMcpReport.CONTEXT,"title")).asJsonObject["context"].asJsonObject
        assertEquals(structure["warnings"],context["warnings"])
        assertEquals(listOf("title"),snapshot.report.nodes.mapNotNull {it.wicketId})
        for(name in listOf("wicket-extensions-11.0.0.jar","wicket-util-11.0.0.jar","jdk-serializable-functional-1.9.0.jar")) assertTrue(name,snapshot.version.sources.any {it.path.endsWith(name)})
        assertTrue(snapshot.report.javaSourceFiles.any {it.endsWith("/RowFunction.java")})
        query.requireCurrent(project,snapshot);assertEquals(before,java.text to java.modificationStamp)
    }
    private fun stale(name:String,before:String,after:String,commit:Boolean) {
        WicketLambdaColumnFixture.install(myFixture)
        val query=WicketPageAnalysisRequest();val snapshot=query.capture(project,"example.LambdaPage")
        assertTrue(snapshot.report.warnings.any {"Reviewed LambdaColumn constructor storage" in it.message})
        val file=PsiManager.getInstance(project).findFile(myFixture.findFileInTempDir("src/main/java/example/$name.java"))!!
        assertTrue(before in file.text)
        WriteCommandAction.runWriteCommandAction(project) {PsiDocumentManager.getInstance(project).getDocument(file)!!.setText(file.text.replace(before,after))}
        if(commit) PsiDocumentManager.getInstance(project).commitAllDocuments()
        try {query.requireCurrent(project,snapshot);fail("Expected stale lambda-column source")}
        catch(e:PageAnalysisFailure){assertEquals("STALE_SNAPSHOT",e.code)}
    }
    fun testUncommittedBoundFunctionEditsInvalidateTheSnapshot()=stale("BadgeColumn","value::apply","row->value.apply(row)",false)
    fun testSourceCallbackInterfaceEditsInvalidateTheSnapshot()=stale("RowFunction"," {}"," {int TOKEN=seed();static int seed(){return 1;}default String tag(){return \"tag\";}}",true)
    fun testDeferredDataModelEditsInvalidateTheSnapshot()=stale("BadgeColumn","getDataModel(row)","Model.of(\"later\")",true)
    fun testAllThreeViewsKeepTheWholeResponseBound() {
        WicketLambdaColumnFixture.install(myFixture)
        val query=WicketPageAnalysisRequest();val snapshot=query.capture(project,"example.LambdaPage")
        val report=snapshot.report.copy(warnings=snapshot.report.warnings+dev.wicketlens.model.Diagnostic(dev.wicketlens.model.DiagnosticCode.PARTIAL_ANALYSIS,"x".repeat(600000)))
        for(view in listOf(WicketMcpReport.STRUCTURE,WicketMcpReport.VALIDATION,WicketMcpReport.CONTEXT)) {
            try {query.render(snapshot.copy(report=report),view,if(view==WicketMcpReport.CONTEXT) "title" else null);fail("Expected response bound")}
            catch(e:PageAnalysisFailure){assertEquals("RESPONSE_TOO_LARGE",e.code)}
        }
    }
}
