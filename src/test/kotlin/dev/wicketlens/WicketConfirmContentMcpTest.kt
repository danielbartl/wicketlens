package dev.wicketlens

import com.google.gson.JsonParser
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.psi.*
import dev.wicketlens.export.WicketContextExporter
import dev.wicketlens.mcp.*

class WicketConfirmContentMcpTest : WicketProjectPsiTestCase() {
    fun testAllThreeViewsShareConstructorContentProvenanceAndUnknownEffectsReadOnly() {
        val java=WicketConfirmContentFixture.install(myFixture);val before=java.text to java.modificationStamp
        val query=WicketPageAnalysisRequest();val snapshot=query.capture(project,"example.ConfirmPage")
        val structure=JsonParser.parseString(query.render(snapshot)).asJsonObject
        assertEquals(JsonParser.parseString(WicketContextExporter().export(snapshot.report)),structure["analysis"])
        val setup=structure["analysis"].asJsonObject["initialContentSetup"].asJsonObject
        assertEquals("INITIAL_DECLARATIONS",setup["phase"].asString)
        assertEquals("unknown",setup["constructorEffects"].asString)
        assertEquals("not_evaluated",setup["callbackExecution"].asString)
        assertEquals(1,setup["hosts"].asJsonArray.single().asJsonObject["placementLocations"].asJsonArray.size())
        val validation=JsonParser.parseString(query.render(snapshot,WicketMcpReport.VALIDATION)).asJsonObject["validation"].asJsonObject
        assertEquals(setup,validation["initialContentSetup"])
        val context=JsonParser.parseString(query.render(snapshot,WicketMcpReport.CONTEXT,"confirmBox:dialog:form:content")).asJsonObject["context"].asJsonObject
        assertEquals(setup,context["initialContentSetup"])
        assertEquals("org.apache.wicket.markup.html.basic.Label",context["component"].asJsonObject["componentType"].asString)
        assertEquals("INFERRED",context["findings"].asJsonArray.single().asJsonObject["confidence"].asString)
        assertTrue(snapshot.report.javaSourceFiles.any { it.endsWith("/ConfirmBox.java") })
        assertTrue(snapshot.report.javaSourceFiles.any { it.endsWith("/DialogBox.java") })
        query.requireCurrent(project,snapshot);assertEquals(before,java.text to java.modificationStamp)
    }
    private fun stale(name:String,before:String,after:String,commit:Boolean) {
        WicketConfirmContentFixture.install(myFixture)
        val query=WicketPageAnalysisRequest();val snapshot=query.capture(project,"example.ConfirmPage")
        val file=PsiManager.getInstance(project).findFile(myFixture.findFileInTempDir("src/main/java/example/$name.java"))!!
        WriteCommandAction.runWriteCommandAction(project) { PsiDocumentManager.getInstance(project).getDocument(file)!!.setText(file.text.replace(before,after)) }
        if(commit) PsiDocumentManager.getInstance(project).commitAllDocuments()
        try {query.requireCurrent(project,snapshot);fail("Expected stale constructor content")}
        catch(e:PageAnalysisFailure){assertEquals("STALE_SNAPSHOT",e.code)}
    }
    fun testUncommittedConstructorEditsInvalidateTheSnapshot()=stale("ConfirmBox","new Label(CONTENT_ID","new Label(\"wrong\"",false)
    fun testDeferredCallbackEditsInvalidateTheSnapshot()=stale("ConfirmBox","var pending=action;","add(new Label(\"later\"));var pending=action;",true)
    fun testInheritedSetterEditsInvalidateTheSnapshot()=stale("DialogBox","if(!CONTENT_ID.equals","if(CONTENT_ID.equals",true)
    fun testWholeResponseBoundRetainsItsErrorInsteadOfTruncatingNewMetadata() {
        WicketConfirmContentFixture.install(myFixture)
        val query=WicketPageAnalysisRequest();val snapshot=query.capture(project,"example.ConfirmPage")
        val report=snapshot.report.copy(warnings=snapshot.report.warnings+dev.wicketlens.model.Diagnostic(dev.wicketlens.model.DiagnosticCode.PARTIAL_ANALYSIS,"x".repeat(600000)))
        try {query.render(snapshot.copy(report=report));fail("Expected response bound")}
        catch(e:PageAnalysisFailure){assertEquals("RESPONSE_TOO_LARGE",e.code)}
    }
}
