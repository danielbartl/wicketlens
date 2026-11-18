package dev.wicketlens

import com.google.gson.JsonParser
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.psi.*
import dev.wicketlens.export.WicketContextExporter
import dev.wicketlens.mcp.*

class WicketColumnConstructionMcpTest : WicketProjectPsiTestCase() {
    fun testAllThreeViewsShareTheNativeConstructorNoteWithoutColumnChildrenReadOnly() {
        val java=WicketColumnConstructionFixture.install(myFixture);val before=java.text to java.modificationStamp
        val query=WicketPageAnalysisRequest();val snapshot=query.capture(project,"example.ColumnPage")
        val structure=JsonParser.parseString(query.render(snapshot)).asJsonObject["analysis"].asJsonObject
        assertEquals(JsonParser.parseString(WicketContextExporter().export(snapshot.report)),structure)
        assertTrue(structure["warnings"].asJsonArray.any {"Reviewed AbstractColumn constructor storage" in it.asJsonObject["message"].asString})
        val validation=query.render(snapshot,WicketMcpReport.VALIDATION)
        assertTrue(validation.contains("Reviewed AbstractColumn constructor storage"))
        val context=JsonParser.parseString(query.render(snapshot,WicketMcpReport.CONTEXT,"title")).asJsonObject["context"].asJsonObject
        assertEquals(structure["warnings"],context["warnings"])
        assertEquals("title",context["component"].asJsonObject["wicketId"].asString)
        assertEquals(listOf("title"),snapshot.report.nodes.mapNotNull {it.wicketId})
        assertTrue(snapshot.report.javaSourceFiles.any {it.endsWith("wicket-extensions-11.0.0.jar")})
        query.requireCurrent(project,snapshot);assertEquals(before,java.text to java.modificationStamp)
    }
    private fun stale(before:String,after:String,commit:Boolean,interfaceEdit:Boolean=false) {
        WicketColumnConstructionFixture.install(myFixture)
        if(interfaceEdit) {
            val file=PsiManager.getInstance(project).findFile(myFixture.findFileInTempDir("src/main/java/example/StorageColumn.java"))!!
            WriteCommandAction.runWriteCommandAction(project) {PsiDocumentManager.getInstance(project).getDocument(file)!!.setText(file.text.replace("java.util.function.Function<String,String>","PlainFunction"))}
            PsiDocumentManager.getInstance(project).commitAllDocuments()
            myFixture.addFileToProject("src/main/java/example/PlainFunction.java","package example;interface PlainFunction extends java.util.function.Function<String,String>,java.io.Serializable {}")
        }
        val query=WicketPageAnalysisRequest();val snapshot=query.capture(project,"example.ColumnPage")
        assertTrue(snapshot.report.warnings.any {"Reviewed AbstractColumn constructor storage" in it.message})
        val name=if(interfaceEdit) "PlainFunction" else "StorageColumn"
        val file=PsiManager.getInstance(project).findFile(myFixture.findFileInTempDir("src/main/java/example/$name.java"))!!
        assertTrue(before in file.text)
        WriteCommandAction.runWriteCommandAction(project) {PsiDocumentManager.getInstance(project).getDocument(file)!!.setText(file.text.replace(before,after))}
        if(commit) PsiDocumentManager.getInstance(project).commitAllDocuments()
        try {query.requireCurrent(project,snapshot);fail("Expected stale column source")}
        catch(e:PageAnalysisFailure){assertEquals("STALE_SNAPSHOT",e.code)}
    }
    fun testPendingColumnConstructorEditsInvalidateSnapshots()=stale("this.key=key;","this.key=key;getHeader(\"eager\");",false)
    fun testDeferredPopulationEditsInvalidateSnapshots()=stale("key.apply(row.getObject())","\"later\"",true)
    fun testSourceFunctionalInterfaceEditsInvalidateSnapshots()=stale(" {}"," {int TOKEN=System.identityHashCode(new Object());}",true,true)
    fun testWholeResponseBoundsStillRejectRatherThanTruncateConstructorNotes() {
        WicketColumnConstructionFixture.install(myFixture)
        val query=WicketPageAnalysisRequest();val snapshot=query.capture(project,"example.ColumnPage")
        val report=snapshot.report.copy(warnings=snapshot.report.warnings+dev.wicketlens.model.Diagnostic(dev.wicketlens.model.DiagnosticCode.PARTIAL_ANALYSIS,"x".repeat(600000)))
        for(view in listOf(WicketMcpReport.STRUCTURE,WicketMcpReport.VALIDATION,WicketMcpReport.CONTEXT)) {
            try {query.render(snapshot.copy(report=report),view,if(view==WicketMcpReport.CONTEXT) "title" else null);fail("Expected response bound")}
            catch(e:PageAnalysisFailure){assertEquals("RESPONSE_TOO_LARGE",e.code)}
        }
    }
}
