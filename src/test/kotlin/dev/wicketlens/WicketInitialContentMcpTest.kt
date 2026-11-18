package dev.wicketlens

import com.google.gson.JsonParser
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.psi.*
import dev.wicketlens.mcp.*
import dev.wicketlens.export.WicketContextExporter

class WicketInitialContentMcpTest : WicketProjectPsiTestCase() {
    private fun file(name:String)=PsiManager.getInstance(project).findFile(myFixture.findFileInTempDir("src/main/java/example/$name.java"))!!
    fun testInitialDeclarationsShareAllThreeViewsReadOnly() {
        val java=WicketInitialContentFixture.install(myFixture);val files=listOf(java,file("DialogBox"),file("DrawerBox"));val before=files.map { it.text to it.modificationStamp }
        val query=WicketPageAnalysisRequest();val snapshot=query.capture(project,"example.ContentPage",includeNavigationContexts=true)
        val structure=JsonParser.parseString(query.render(snapshot)).asJsonObject["analysis"].asJsonObject
        assertEquals(JsonParser.parseString(WicketContextExporter().export(snapshot.report)),structure)
        val setup=structure["initialContentSetup"].asJsonObject
        assertEquals("SOURCE_DECLARED_INITIAL_CONTENT",setup["kind"].asString);assertEquals("INITIAL_DECLARATIONS",setup["phase"].asString)
        assertEquals("unknown",setup["constructorEffects"].asString);assertEquals("unknown",setup["laterTreeState"].asString);assertEquals("not_evaluated",setup["callbackExecution"].asString)
        assertEquals(2,setup["hosts"].asJsonArray.size())
        val ids=snapshot.report.nodes.map { it.nodeId }.toSet()
        setup["hosts"].asJsonArray.forEach { host -> for(key in listOf("hostNodeId","slotParentNodeId","contentNodeId")) assertTrue(host.toString(),host.asJsonObject[key].asString in ids) }
        val validation=JsonParser.parseString(query.render(snapshot,WicketMcpReport.VALIDATION)).asJsonObject["validation"].asJsonObject
        val context=JsonParser.parseString(query.render(snapshot,WicketMcpReport.CONTEXT,"drawer:dialog:form:content:name")).asJsonObject["context"].asJsonObject
        assertEquals(setup,validation["initialContentSetup"]);assertEquals(setup,context["initialContentSetup"])
        assertEquals("INFERRED",context["findings"].asJsonArray.single().asJsonObject["confidence"].asString)
        query.requireCurrent(project,snapshot);assertEquals(before,files.map { it.text to it.modificationStamp })
    }
    private fun stale(path:String,before:String,after:String) {
        WicketInitialContentFixture.install(myFixture);val query=WicketPageAnalysisRequest();val snapshot=query.capture(project,"example.ContentPage")
        val source=PsiManager.getInstance(project).findFile(myFixture.findFileInTempDir(path))!!
        assertTrue(source.text,before in source.text)
        assertTrue(snapshot.version.sources.any { it.path==source.virtualFile.path })
        WriteCommandAction.runWriteCommandAction(project) { PsiDocumentManager.getInstance(project).getDocument(source)!!.setText(source.text.replace(before,after)) }
        try { query.requireCurrent(project,snapshot);fail("Expected stale initial content snapshot") } catch(e:PageAnalysisFailure) { assertEquals("STALE_SNAPSHOT",e.code) }
    }
    fun testUnsavedContentGuardInvalidatesSnapshot()=stale("src/main/java/example/DialogBox.java","!CONTENT_ID.equals","CONTENT_ID.equals")
    fun testUnsavedDrawerConstructorInvalidatesSnapshot()=stale("src/main/java/example/DrawerBox.java","setFooterVisible(false);","setFooterVisible(true);")
    fun testUnsavedEventHookInvalidatesSnapshot()=stale("src/main/java/example/ContentPage.java","success(\"Saved\")","success(\"Changed\")")
    fun testUnsavedOwnedMarkupInvalidatesSnapshot()=stale("src/main/resources/example/DialogBox.html","wicket:id='header'","wicket:id='renamed'")
    fun testWholeInitialContentResponseIsBounded() {
        WicketInitialContentFixture.install(myFixture);val query=WicketPageAnalysisRequest();val snapshot=query.capture(project,"example.ContentPage")
        val report=snapshot.report.copy(warnings=snapshot.report.warnings+dev.wicketlens.model.Diagnostic(dev.wicketlens.model.DiagnosticCode.PARTIAL_ANALYSIS,"x".repeat(600000)))
        try { query.render(snapshot.copy(report=report));fail("Expected response bound") } catch(e:PageAnalysisFailure) { assertEquals("RESPONSE_TOO_LARGE",e.code) }
    }
}
