package dev.wicketlens

import com.google.gson.JsonParser
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.psi.*
import dev.wicketlens.editor.WicketEditorAnalysis
import dev.wicketlens.export.WicketContextExporter
import dev.wicketlens.mcp.*
import dev.wicketlens.model.*

class WicketSelectionMcpTest : WicketProjectPsiTestCase() {
    private fun file(path: String) = PsiManager.getInstance(project).findFile(myFixture.findFileInTempDir(path))!!
    fun testReadOnlySelectionContextsShareNativeGraphsAndKeepInitialSelectors() {
        val java=WicketSelectionFixture.install(myFixture)
        val files=listOf(java,file("src/main/java/example/Flow.java"),file("src/main/resources/example/Flow.html"),file("src/main/resources/example/ExamplePage.html"))
        val before=files.map { it.text to it.modificationStamp }; val query=WicketPageAnalysisRequest()
        val plain=JsonParser.parseString(query.render(query.capture(project,"example.ExamplePage"))).asJsonObject
        val snapshot=query.capture(project,"example.ExamplePage",includeNavigationContexts=true)
        val json=JsonParser.parseString(query.render(snapshot)).asJsonObject
        assertEquals(plain["analysis"],json["analysis"]); assertFalse(plain.has("navigationContexts"))
        val native=WicketEditorAnalysis.navigationPages(java).filter { it.report.lifecycle?.selection!=null }.map { WicketContextExporter().export(it.report) }.toSet()
        val selections=snapshot.navigationContexts!!.contexts.filter { it.relationship=="SELECTION_TEMPLATE" }
        assertEquals(2,selections.size); assertEquals(native,selections.map { WicketContextExporter().export(it.report) }.toSet())
        val exported=json["navigationContexts"].asJsonObject["contexts"].asJsonArray.filter { it.asJsonObject["relationship"].asString=="SELECTION_TEMPLATE" }.map { it.asJsonObject }
        assertEquals(listOf(0,1),exported.map { it["lifecycle"].asJsonObject["selection"].asJsonObject["registrationIndex"].asInt })
        exported.forEach { context ->
            val lifecycle=context["lifecycle"].asJsonObject
            assertEquals("SELECTION_TEMPLATE",lifecycle["phase"].asString); assertEquals("SOURCE_DECLARED_STEP_SELECTION",lifecycle["kind"].asString)
            assertEquals("unknown",lifecycle["runtimePresence"].asString); assertFalse(context["scopeComplete"].asBoolean)
            assertFalse(lifecycle["selection"].asJsonObject.has("currentIndex"))
            assertTrue(context["bindings"].asJsonArray.all { it.asJsonObject.has("ancestors") && it.asJsonObject.has("children") && it.asJsonObject["findings"].asJsonArray.single().asJsonObject["confidence"].asString=="INFERRED" })
        }
        try { query.render(snapshot,WicketMcpReport.CONTEXT,"flow:form:body:name"); fail("Selection must not become the initial graph") }
        catch(e:PageAnalysisFailure) { assertTrue(e.code,e.code in setOf("COMPONENT_NOT_FOUND","UNRESOLVED_COMPONENT_PATH")) }
        query.requireCurrent(project,snapshot); assertEquals(before,files.map { it.text to it.modificationStamp })
    }
    fun testUnsavedControllerChangeInvalidatesSelectionSnapshot() = freshness("src/main/java/example/Flow.java","form.addOrReplace(content);","form.addOrReplace(content); addOrReplace(content);")
    fun testUnsavedDefinitionChangeInvalidatesSelectionSnapshot() = freshness("src/main/resources/example/ExamplePage.html","wicket:id='first'","wicket:id='unknown'")
    private fun freshness(path:String,before:String,after:String) {
        WicketSelectionFixture.install(myFixture); val query=WicketPageAnalysisRequest(); val snapshot=query.capture(project,"example.ExamplePage",includeNavigationContexts=true)
        val file=file(path); assertTrue(snapshot.version.sources.any { it.path==file.virtualFile.path })
        WriteCommandAction.runWriteCommandAction(project) { PsiDocumentManager.getInstance(project).getDocument(file)!!.setText(file.text.replace(before,after)) }
        try { query.requireCurrent(project,snapshot); fail("Expected stale selection snapshot") } catch(e:PageAnalysisFailure) { assertEquals("STALE_SNAPSHOT",e.code) }
        PsiDocumentManager.getInstance(project).commitAllDocuments()
        val next=query.capture(project,"example.ExamplePage",includeNavigationContexts=true)
        assertTrue(snapshot.version!=next.version)
        if(path.endsWith("Flow.java")) { assertTrue(next.navigationContexts!!.contexts.none { it.relationship=="SELECTION_TEMPLATE" }); assertTrue(next.navigationContexts.lifecycleDiagnostics.isNotEmpty()) }
        else assertTrue(next.navigationContexts!!.contexts.filter { it.relationship=="SELECTION_TEMPLATE" }.all { it.report.lifecycle!!.selection!!.definitionId!="first" || it.report.matching!!.findings.any { it.code!=DiagnosticCode.MATCHED } })
    }
    fun testWholeSelectionResponseRemainsBounded() {
        WicketSelectionFixture.install(myFixture); val query=WicketPageAnalysisRequest(); val snapshot=query.capture(project,"example.ExamplePage",includeNavigationContexts=true)
        val navigation=snapshot.navigationContexts!!; val context=navigation.contexts.first { it.relationship=="SELECTION_TEMPLATE" }
        val large=context.copy(report=context.report.copy(warnings=listOf(Diagnostic(DiagnosticCode.PARTIAL_ANALYSIS,"x".repeat(600_000)))))
        try { query.render(snapshot.copy(navigationContexts=navigation.copy(contexts=listOf(large)))); fail("Expected size limit") }
        catch(e:PageAnalysisFailure) { assertEquals("RESPONSE_TOO_LARGE",e.code) }
    }
}
