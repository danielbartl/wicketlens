package dev.wicketlens

import com.google.gson.JsonParser
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.psi.*
import dev.wicketlens.editor.WicketEditorAnalysis
import dev.wicketlens.export.WicketContextExporter
import dev.wicketlens.mcp.*
import dev.wicketlens.model.*

class WicketContentFactoryMcpTest : WicketProjectPsiTestCase() {
    private fun file(path: String) = PsiManager.getInstance(project).findFile(myFixture.findFileInTempDir(path))!!
    fun testOptionalFactoryContextsReuseNativeValuesAndPreserveDefaultResponses() {
        val java = WicketContentFactoryFixture.install(myFixture)
        val files = listOf(java, file("src/main/java/example/TableHost.java"), file("src/main/java/example/SearchField.java"),
            file("src/main/resources/example/TableHost.html"), file("src/main/resources/example/FactoryPage.html"))
        val before = files.map { it.text to it.modificationStamp }
        val query = WicketPageAnalysisRequest()
        val plain = JsonParser.parseString(query.render(query.capture(project, "example.FactoryPage"))).asJsonObject
        val snapshot = query.capture(project, "example.FactoryPage", includeNavigationContexts=true)
        val structure = JsonParser.parseString(query.render(snapshot)).asJsonObject
        assertEquals(plain["analysis"], structure["analysis"]); assertFalse(plain.has("navigationContexts"))
        val native = WicketEditorAnalysis.navigationPages(java).filter { it.report.lifecycle?.contentFactory != null }
        val contexts = snapshot.navigationContexts!!.contexts.filter { it.relationship == "FACTORY_TEMPLATE" }
        assertEquals(1, contexts.size)
        assertEquals(native.map { WicketContextExporter().export(it.report) }, contexts.map { WicketContextExporter().export(it.report) })
        val exported = structure["navigationContexts"].asJsonObject["contexts"].asJsonArray.single { it.asJsonObject["relationship"].asString == "FACTORY_TEMPLATE" }.asJsonObject
        val lifecycle = exported["lifecycle"].asJsonObject
        assertEquals("SOURCE_DECLARED_CONTENT_FACTORY", lifecycle["kind"].asString)
        assertEquals("REGISTRATION_TEMPLATE", lifecycle["phase"].asString)
        assertEquals("unknown", lifecycle["runtimePresence"].asString)
        val factory = lifecycle["contentFactory"].asJsonObject
        assertEquals("source_immediate", factory["invocation"].asString)
        assertEquals("not_evaluated", factory["factoryExecution"].asString)
        assertEquals("not_evaluated", factory["callbackExecution"].asString)
        for (key in listOf("configurationEffects", "modelValues", "visibility", "laterTreeState", "slotPresence")) assertEquals("unknown", factory[key].asString)
        assertEquals("source_declared_placeholder", factory["slotRequirement"].asString)
        assertTrue(factory.has("placeholderLocation"))
        assertEquals(1, factory["callbackLocations"].asJsonArray.size())
        assertFalse(exported["scopeComplete"].asBoolean)
        assertEquals(4, exported["bindings"].asJsonArray.size())
        assertTrue(exported["bindings"].asJsonArray.all { it.asJsonObject.has("ancestors") && it.asJsonObject.has("children") &&
            it.asJsonObject["findings"].asJsonArray.single().asJsonObject["confidence"].asString == "INFERRED" })
        val validation = JsonParser.parseString(query.render(snapshot, WicketMcpReport.VALIDATION)).asJsonObject
        assertFalse(validation.has("navigationContexts"))
        assertEquals(JsonParser.parseString(query.render(query.capture(project, "example.FactoryPage"), WicketMcpReport.VALIDATION)).asJsonObject["validation"], validation["validation"])
        val rootContext = JsonParser.parseString(query.render(snapshot, WicketMcpReport.CONTEXT, "")).asJsonObject
        assertFalse(rootContext.has("navigationContexts"))
        try { query.render(snapshot, WicketMcpReport.CONTEXT, "table:toolbar:searchForm:search"); fail("Factory paths must not select the primary hierarchy") }
        catch (e: PageAnalysisFailure) { assertTrue(e.code, e.code in setOf("COMPONENT_NOT_FOUND", "UNRESOLVED_COMPONENT_PATH")) }
        query.requireCurrent(project, snapshot)
        assertEquals(before, files.map { it.text to it.modificationStamp })
    }
    fun testUnsavedSetterChangeInvalidatesSnapshot() = freshness("src/main/java/example/TableHost.java", "replace(content);", "addOrReplace(content);")
    fun testUnsavedFluentSourceChangeInvalidatesSnapshot() = freshness("src/main/java/example/SearchField.java", "return self();", "return new SearchField(\"other\");")
    fun testUnsavedSlotMarkupChangeInvalidatesSnapshot() = freshness("src/main/resources/example/TableHost.html", "wicket:id='toolbar'", "wicket:id='other'")
    fun testUnsavedFragmentMarkupChangeInvalidatesSnapshot() = freshness("src/main/resources/example/FactoryPage.html", "wicket:id='search'", "wicket:id='other'")
    private fun freshness(path: String, before: String, after: String) {
        WicketContentFactoryFixture.install(myFixture)
        val query = WicketPageAnalysisRequest(); val snapshot = query.capture(project, "example.FactoryPage", includeNavigationContexts=true)
        val file = file(path)
        assertTrue(snapshot.version.sources.toString(), snapshot.version.sources.any { it.path == file.virtualFile.path })
        WriteCommandAction.runWriteCommandAction(project) { PsiDocumentManager.getInstance(project).getDocument(file)!!.setText(file.text.replace(before, after)) }
        try { query.requireCurrent(project, snapshot); fail("Expected stale source factory snapshot") }
        catch (e: PageAnalysisFailure) { assertEquals("STALE_SNAPSHOT", e.code) }
        PsiDocumentManager.getInstance(project).commitAllDocuments()
        val next = query.capture(project, "example.FactoryPage", includeNavigationContexts=true)
        assertTrue(next.version != snapshot.version)
        val templates = next.navigationContexts!!.contexts.filter { it.relationship == "FACTORY_TEMPLATE" }
        if (path.endsWith(".java")) assertTrue(next.navigationContexts.lifecycleDiagnostics.toString(), templates.isEmpty())
        else assertTrue(templates.isEmpty() || templates.single().report.matching!!.findings.any { it.code != DiagnosticCode.MATCHED })
    }
    fun testWholeFactoryResponseRemainsBounded() {
        WicketContentFactoryFixture.install(myFixture)
        val query = WicketPageAnalysisRequest(); val snapshot = query.capture(project, "example.FactoryPage", includeNavigationContexts=true)
        val navigation = snapshot.navigationContexts!!; val context = navigation.contexts.single { it.relationship == "FACTORY_TEMPLATE" }
        val large = context.copy(report=context.report.copy(warnings=listOf(Diagnostic(DiagnosticCode.PARTIAL_ANALYSIS, "x".repeat(600_000)))))
        try { query.render(snapshot.copy(navigationContexts=navigation.copy(contexts=listOf(large)))); fail("Expected size limit") }
        catch (e: PageAnalysisFailure) { assertEquals("RESPONSE_TOO_LARGE", e.code) }
    }
    fun testDiscoveryExhaustionRemainsExplicit() {
        val java = WicketContentFactoryFixture.install(myFixture)
        val query = WicketPageAnalysisRequest()
        val snapshot = query.capture(project, "example.FactoryPage", includeNavigationContexts=true)
        val navigation = dev.wicketlens.analysis.WicketNavigationContextsAnalyzer(dev.wicketlens.analysis.WicketNavigationContextsAnalyzer.Limits(bindings=1)).analyze(java.classes.single(), snapshot.report)
        assertFalse(navigation.complete)
        assertTrue(navigation.diagnostics.isNotEmpty())
        assertTrue(java.isValid)
    }
}
