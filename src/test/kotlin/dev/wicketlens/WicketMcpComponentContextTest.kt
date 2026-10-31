package dev.wicketlens

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.components.service
import com.intellij.psi.PsiDocumentManager
import com.intellij.testFramework.PlatformTestUtil
import dev.wicketlens.export.WicketContextExporter
import dev.wicketlens.mcp.*
import dev.wicketlens.model.*
import dev.wicketlens.ui.WicketLensService
import kotlinx.coroutines.*

class WicketMcpComponentContextTest : WicketProjectPsiTestCase() {
    private val query = WicketPageAnalysisRequest()
    private fun capture() = query.capture(project, "example.ExamplePage")
    private fun envelope(path: String? = "title", snapshot: PageAnalysisSnapshot = capture(), nodeId: String? = null) =
        JsonParser.parseString(query.render(snapshot, WicketMcpReport.CONTEXT, path, nodeId)).asJsonObject
    private fun context(path: String? = "title", snapshot: PageAnalysisSnapshot = capture(), nodeId: String? = null) =
        envelope(path, snapshot, nodeId).getAsJsonObject("context")
    private fun exported(snapshot: PageAnalysisSnapshot) = JsonParser.parseString(WicketContextExporter().export(snapshot.report)).asJsonObject
    private fun assertFailure(code: String, action: () -> Unit) {
        try { action(); fail("Expected $code") } catch (failure: PageAnalysisFailure) { assertEquals(code, failure.code) }
    }
    private fun nested() {
        pageFile(body = """
            var form = new org.apache.wicket.markup.html.form.Form<Void>("form");
            form.add(new org.apache.wicket.markup.html.form.TextField<String>("name"));
            add(form); add(new Label("name"));
        """.trimIndent())
        companion(html = "<form wicket:id='form'><input wicket:id='name'/></form><span wicket:id='name'></span>")
    }

    fun testEnvelopeHasContextToolAndNoWholePageTrees() {
        pageFile(); companion()
        val response = envelope()
        assertEquals("wicket_get_component_context", response["tool"].asString)
        assertEquals(1, response["schemaVersion"].asInt)
        assertEquals("ok", response["status"].asString)
        assertFalse(response["truncated"].asBoolean)
        assertFalse(response.has("analysis")); assertFalse(response.has("validation"))
        assertFalse(response["context"].asJsonObject.has("model"))
    }
    fun testNestedPathUsesParentsRatherThanGlobalIdLookup() {
        nested()
        val result = context("form:name")
        assertEquals("form:name", result["componentPath"].asString)
        assertEquals("form", result["parent"].asJsonObject["wicketId"].asString)
        assertEquals("org.apache.wicket.markup.html.form.TextField", result["component"].asJsonObject["componentType"].asString)
        assertEquals("org.apache.wicket.markup.html.basic.Label", context("name")["component"].asJsonObject["componentType"].asString)
        assertEquals(2, result["ancestors"].asJsonArray.size())
    }
    fun testSelectedComponentAndChildrenAreExactExportObjects() {
        nested()
        val snapshot = capture()
        val components = exported(snapshot)["components"].asJsonArray.map { it.asJsonObject }
        val form = components.single { it["wicketId"].asStringOrNull() == "form" }
        val result = context("form", snapshot)
        assertEquals(form, result["component"])
        assertEquals(components.filter { it["parentNodeId"] == form["nodeId"] }, result["children"].asJsonArray.toList())
        assertTrue(result["hierarchyResolved"].asBoolean)
    }
    fun testFindingsAndAssociatedMarkupUseMatcherIdentities() {
        nested()
        val snapshot = capture()
        val export = exported(snapshot)
        val result = context("form:name", snapshot)
        val id = result["component"].asJsonObject["nodeId"]
        val findings = export["matching"].asJsonObject["findings"].asJsonArray.filter { it.asJsonObject["javaNodeId"] == id }
        assertEquals(findings, result["findings"].asJsonArray.toList())
        val markupId = findings.single().asJsonObject["markupNodeId"]
        assertEquals(export["markup"].asJsonObject["elements"].asJsonArray.single { it.asJsonObject["nodeId"] == markupId }, result["markupElements"].asJsonArray.single())
    }
    fun testEmptyPathSelectsRootAndItsDirectChildren() {
        nested()
        val snapshot = capture()
        val result = context("", snapshot)
        assertEquals(snapshot.report.rootNodeId, result["component"].asJsonObject["nodeId"].asString)
        assertEquals("", result["componentPath"].asString)
        assertTrue(result["parent"].isJsonNull)
        assertTrue(result["ancestors"].asJsonArray.isEmpty)
        assertEquals(2, result["children"].asJsonArray.size())
        assertTrue(result["markupElements"].asJsonArray.isEmpty)
        assertFalse(result["markupPath"].isJsonNull)
    }
    fun testNodeIdSelectionAgreesWithPathSelection() {
        nested()
        val snapshot = capture()
        val result = context("form:name", snapshot)
        assertEquals(result, context(null, snapshot, result["component"].asJsonObject["nodeId"].asString))
    }
    fun testUnknownPathAndUnknownNodeAreExplicitErrors() {
        pageFile(); companion()
        assertFailure("COMPONENT_NOT_FOUND") { context("missing") }
        assertFailure("COMPONENT_NOT_FOUND") { context(null, nodeId = "obsolete-node") }
        assertFailure("COMPONENT_NOT_FOUND") { context("title:missing") }
    }
    fun testSelectorValidationRejectsMissingBothAndMalformedPaths() {
        pageFile()
        for (path in listOf(null, ":title", "title:", "form::title", "x".repeat(4097)))
            assertFailure("INVALID_REQUEST") { context(path) }
        assertFailure("INVALID_REQUEST") { context("title", nodeId = "node") }
        assertFailure("INVALID_REQUEST") { context(null, nodeId = " ") }
        assertFailure("INVALID_REQUEST") { context(null, nodeId = "x".repeat(4097)) }
    }
    fun testDuplicateSiblingPathsRequireNodeIdEvenWhenOnlyOneHasDesiredChild() {
        nested()
        val old = capture()
        val form = old.report.nodes.single { it.wicketId == "form" }
        val snapshot = old.copy(report = old.report.copy(nodes = old.report.nodes + form.copy(nodeId = "duplicate-form")))
        assertFailure("AMBIGUOUS_COMPONENT") { context("form:name", snapshot) }
        val result = context(null, snapshot, form.nodeId)
        assertTrue(result["componentPath"].isJsonNull)
        assertEquals(form.nodeId, result["component"].asJsonObject["nodeId"].asString)
    }
    fun testUnknownSiblingIdPreventsUniquePathClaim() {
        pageFile(); companion()
        val old = capture()
        val child = old.report.nodes.single { it.wicketId == "title" }
        val snapshot = old.copy(report = old.report.copy(nodes = old.report.nodes + child.copy(nodeId = "unknown", wicketId = null)))
        assertFailure("UNRESOLVED_COMPONENT_PATH") { context("title", snapshot) }
        assertTrue(context(null, snapshot, child.nodeId)["componentPath"].isJsonNull)
    }
    fun testDetachedNodeRetainsUnresolvedParentAndHasNoPath() {
        pageFile(); companion()
        val old = capture()
        val child = old.report.nodes.single { it.wicketId == "title" }.copy(parentNodeId = null)
        val snapshot = old.copy(report = old.report.copy(nodes = old.report.nodes.map { if (it.nodeId == child.nodeId) child else it }))
        val result = context(null, snapshot, child.nodeId)
        assertTrue(result["componentPath"].isJsonNull)
        assertTrue(result["parent"].isJsonNull)
        assertFalse(result["hierarchyResolved"].asBoolean)
        assertFailure("COMPONENT_NOT_FOUND") { context("title", snapshot) }
    }
    fun testColonIdCanStillBeSelectedByNodeId() {
        pageFile(body = "add(new Label(\"a:b\"));"); companion(html = "<span wicket:id='a:b'></span>")
        val snapshot = capture()
        val result = context(null, snapshot, snapshot.report.nodes.single { it.wicketId == "a:b" }.nodeId)
        assertTrue(result["componentPath"].isJsonNull)
        assertEquals("a:b", result["component"].asJsonObject["wicketId"].asString)
    }
    fun testMissingMarkupIsUnavailableWithOriginalWarning() {
        pageFile()
        val result = context()
        assertFalse(result["markupAvailable"].asBoolean)
        assertTrue(result["scopeComplete"].asJsonObject["markup"].isJsonNull)
        assertTrue(result["markupElements"].asJsonArray.isEmpty)
        assertFalse(result["warnings"].asJsonArray.isEmpty)
        assertEquals("UNRESOLVED_MARKUP", result["findings"].asJsonArray.single().asJsonObject["code"].asString)
    }
    fun testMarkupOnlyEntryDoesNotBecomeAJavaComponent() {
        pageFile(); companion(html = "<span wicket:id='htmlOnly'></span>")
        assertFailure("COMPONENT_NOT_FOUND") { context("htmlOnly") }
        val result = context()
        assertTrue(result["markupElements"].asJsonArray.isEmpty)
        assertEquals("JAVA_ONLY", result["findings"].asJsonArray.single().asJsonObject["code"].asString)
    }
    fun testUncertainScopeKeepsFindingsCausesAndPageWarnings() {
        pageFile(body = "if (System.getProperty(\"enabled\") != null) add(new Label(\"title\"));"); companion()
        val snapshot = capture()
        val node = snapshot.report.nodes.single { it.wicketId == "title" }
        val result = context(null, snapshot, node.nodeId)
        assertEquals(exported(snapshot)["warnings"], result["warnings"])
        assertFalse(result["warnings"].asJsonArray.isEmpty)
        assertTrue(result["findings"].asJsonArray.any { !it.asJsonObject["causes"].asJsonArray.isEmpty })
        assertTrue(result["findings"].asJsonArray.none { it.asJsonObject["code"].asString == "MATCHED" })
    }
    fun testAllScopeAndDiagnosticChannelsArePreserved() {
        pageFile(); companion()
        val old = capture()
        val diagnostic = Diagnostic(DiagnosticCode.PARTIAL_ANALYSIS, "Scope detail", severity = Severity.WARNING)
        val snapshot = old.copy(report = old.report.copy(warnings = listOf(diagnostic), markup = old.report.markup!!.copy(
            diagnostics = listOf(diagnostic), documentDiagnostics = listOf(diagnostic))))
        val result = context(snapshot = snapshot)
        val export = exported(snapshot)
        assertEquals(export["warnings"], result["warnings"])
        assertEquals(export["markup"].asJsonObject["diagnostics"], result["markupDiagnostics"])
        assertEquals(export["markup"].asJsonObject["documentDiagnostics"], result["documentDiagnostics"])
        assertEquals(snapshot.report.scopeComplete, result["scopeComplete"].asJsonObject["java"].asBoolean)
        assertEquals(snapshot.report.matching!!.scopeComplete, result["scopeComplete"].asJsonObject["matching"].asBoolean)
    }
    fun testRepeaterPathSkipsLogicalItemButAncestryPreservesIt() {
        pageFile(body = """
            add(new org.apache.wicket.markup.html.list.ListView<String>("rows", java.util.List.of("Ada")) {
                protected void populateItem(org.apache.wicket.markup.html.list.ListItem<String> item) {
                    item.add(new Label("name", item.getModelObject()));
                }
            });
        """.trimIndent())
        companion(html = "<div wicket:id='rows'><span wicket:id='name'></span></div>")
        val result = context("rows:name")
        assertEquals("rows:name", result["componentPath"].asString)
        assertTrue(result["parent"].asJsonObject["templateItem"].asBoolean)
        assertTrue(result["component"].asJsonObject["template"].asBoolean)
        assertEquals("INFERRED", result["findings"].asJsonArray.single().asJsonObject["confidence"].asString)
        val item = context(null, nodeId = result["parent"].asJsonObject["nodeId"].asString)
        assertTrue(item["componentPath"].isJsonNull)
        assertEquals(1, item["children"].asJsonArray.size())
        assertFailure("COMPONENT_NOT_FOUND") { context("rows:0:name") }
    }
    fun testCyclicUnattachedGraphCannotLoopOrProduceRootedPath() {
        pageFile(); companion()
        val old = capture()
        val node = old.report.nodes.single { it.wicketId == "title" }
        val snapshot = old.copy(report = old.report.copy(nodes = old.report.nodes.map { if (it.nodeId == node.nodeId) node.copy(parentNodeId = node.nodeId) else it }))
        val result = context(null, snapshot, node.nodeId)
        assertTrue(result["componentPath"].isJsonNull)
        assertFalse(result["hierarchyResolved"].asBoolean)
    }
    fun testContextUsesSharedGraphAndUtf8ResponseLimits() {
        pageFile(); companion()
        val old = capture()
        assertFailure("RESPONSE_TOO_LARGE") { context(snapshot = old.copy(report = old.report.copy(nodes = List(5_001) { old.report.nodes.first() }))) }
        val large = old.copy(report = old.report.copy(warnings = listOf(Diagnostic(DiagnosticCode.PARTIAL_ANALYSIS, "界".repeat(180_000)))))
        assertFailure("RESPONSE_TOO_LARGE") { context(snapshot = large) }
    }
    fun testContextErrorsIdentifyTheCorrectTool() {
        val error = JsonParser.parseString(WicketPageAnalysisRequest.errorJson("COMPONENT_NOT_FOUND", "No component", WicketMcpReport.CONTEXT)).asJsonObject
        assertEquals("wicket_get_component_context", error["tool"].asString)
        assertEquals("COMPONENT_NOT_FOUND", error["error"].asJsonObject["code"].asString)
    }
    fun testContextIsDeterministicReadOnlyAndRejectsStaleSnapshot() {
        val java = pageFile(); val html = companion()
        val before = java.text to html.text
        assertEquals(envelope(), envelope())
        assertEquals(before, java.text to html.text)
        val snapshot = capture()
        WriteCommandAction.runWriteCommandAction(project) { PsiDocumentManager.getInstance(project).getDocument(java)!!.setText(java.text + "\n// edit") }
        assertFailure("STALE_SNAPSHOT") { query.requireCurrent(project, snapshot) }
    }
    fun testAsyncContextUsesCommittedPipelineWithoutPublishingSidebarState() {
        pageFile(); companion()
        val service = project.service<WicketLensService>()
        val before = service.state
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val result = scope.async { query.execute(project, "example.ExamplePage", view = WicketMcpReport.CONTEXT, componentPath = "title") }
            PlatformTestUtil.waitWithEventsDispatching("MCP context", { result.isCompleted }, 15)
            val response = JsonParser.parseString(runBlocking { result.await() }).asJsonObject
            assertEquals("title", response["context"].asJsonObject["component"].asJsonObject["wicketId"].asString)
            assertSame(before, service.state)
        } finally { scope.cancel() }
    }
    private fun com.google.gson.JsonElement.asStringOrNull() = takeUnless { it.isJsonNull }?.asString
}
