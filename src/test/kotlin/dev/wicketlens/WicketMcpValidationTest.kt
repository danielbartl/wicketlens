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

class WicketMcpValidationTest : WicketProjectPsiTestCase() {
    private val query = WicketPageAnalysisRequest()
    private fun capture() = query.capture(project, "example.ExamplePage")
    private fun envelope(snapshot: PageAnalysisSnapshot = capture()) = JsonParser.parseString(query.render(snapshot, WicketMcpReport.VALIDATION)).asJsonObject
    private fun validation(snapshot: PageAnalysisSnapshot = capture()) = envelope(snapshot).getAsJsonObject("validation")
    private fun exported(snapshot: PageAnalysisSnapshot) = JsonParser.parseString(WicketContextExporter().export(snapshot.report)).asJsonObject
    private fun assertFailure(code: String, action: () -> Unit) {
        try { action(); fail("Expected $code") } catch (failure: PageAnalysisFailure) { assertEquals(code, failure.code) }
    }
    private fun execute(): JsonObject {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val result = scope.async { query.execute(project, "example.ExamplePage", view = WicketMcpReport.VALIDATION) }
            PlatformTestUtil.waitWithEventsDispatching("MCP validation", { result.isCompleted }, 15)
            return JsonParser.parseString(runBlocking { result.await() }).asJsonObject
        } finally { scope.cancel() }
    }

    fun testValidationEnvelopeIsVersionedAndDistinctFromStructure() {
        pageFile(); companion()
        val response = envelope()
        assertEquals(1, response["schemaVersion"].asInt)
        assertEquals("wicket_validate_page", response["tool"].asString)
        assertEquals("ok", response["status"].asString)
        assertFalse(response["truncated"].asBoolean)
        assertFalse(response.has("analysis"))
        assertEquals("example.ExamplePage", response["target"].asJsonObject["className"].asString)
        assertEquals(1, response["validation"].asJsonObject["analysisSchemaVersion"].asInt)
    }
    fun testFindingsAreExactlyTheExistingExporterFindings() {
        pageFile(); companion()
        val snapshot = capture()
        assertEquals(exported(snapshot)["matching"].asJsonObject["findings"], validation(snapshot)["findings"])
    }
    fun testMatchedBindingsRemainInfoAndDoNotProduceARuntimeValidityVerdict() {
        pageFile(); companion()
        val result = validation()
        assertEquals(1, result["summary"].asJsonObject["findingsByCode"].asJsonObject["MATCHED"].asInt)
        assertEquals("INFO", result["findings"].asJsonArray.single().asJsonObject["severity"].asString)
        assertFalse(result.has("valid"))
        assertFalse(result.has("hasErrors"))
    }
    fun testJavaOnlyAndMarkupOnlyStayInformational() {
        pageFile(); companion(html = "<span wicket:id='htmlOnly'></span>")
        val findings = validation()["findings"].asJsonArray.map { it.asJsonObject }
        assertEquals(setOf("JAVA_ONLY", "MARKUP_ONLY"), findings.map { it["code"].asString }.toSet())
        assertTrue(findings.all { it["severity"].asString == "INFO" })
    }
    fun testMismatchedFindingsKeepBothSourceLocations() {
        pageFile(); companion(html = "<span wicket:id='htmlOnly'></span>")
        val result = validation()["findings"].asJsonArray.map { it.asJsonObject }
        assertFalse(result.single { it["code"].asString == "JAVA_ONLY" }["javaLocation"].isJsonNull)
        assertFalse(result.single { it["code"].asString == "MARKUP_ONLY" }["markupLocation"].isJsonNull)
    }
    fun testUnknownSetupKeepsUncertaintyInsteadOfMissingPairClaims() {
        pageFile(body = "if (System.getProperty(\"enabled\") != null) add(new Label(\"title\"));")
        companion()
        val result = validation()
        assertFalse(result["warnings"].asJsonArray.isEmpty)
        assertTrue(result["findings"].asJsonArray.none { it.asJsonObject["code"].asString in setOf("MATCHED", "JAVA_ONLY", "MARKUP_ONLY") })
        assertTrue(result["findings"].asJsonArray.any { !it.asJsonObject["causes"].asJsonArray.isEmpty })
    }
    fun testMissingMarkupIsUnavailableNotACleanResult() {
        pageFile()
        val result = validation()
        assertFalse(result["markupAvailable"].asBoolean)
        assertTrue(result["scopeComplete"].asJsonObject["markup"].isJsonNull)
        assertFalse(result["warnings"].asJsonArray.isEmpty)
        assertEquals("UNRESOLVED_MARKUP", result["findings"].asJsonArray.single().asJsonObject["code"].asString)
    }
    fun testAllScopeFlagsRetainTheCoreValues() {
        pageFile(); companion()
        val snapshot = capture()
        val scopes = validation(snapshot)["scopeComplete"].asJsonObject
        assertEquals(snapshot.report.scopeComplete, scopes["java"].asBoolean)
        assertEquals(snapshot.report.markup!!.scopeComplete, scopes["markup"].asBoolean)
        assertEquals(snapshot.report.matching!!.scopeComplete, scopes["matching"].asBoolean)
    }
    fun testAbsentMatchingIsExplicitlyUnavailable() {
        pageFile(); companion()
        val snapshot = capture()
        val result = validation(snapshot.copy(report = snapshot.report.copy(matching = null)))
        assertFalse(result["matchingAvailable"].asBoolean)
        assertTrue(result["scopeComplete"].asJsonObject["matching"].isJsonNull)
        assertTrue(result["findings"].asJsonArray.isEmpty)
    }
    fun testWarningArraysAreNotReclassifiedOrDropped() {
        pageFile(); companion()
        val old = capture()
        val diagnostic = Diagnostic(DiagnosticCode.PARTIAL_ANALYSIS, "Review source evidence", severity = Severity.WARNING)
        val snapshot = old.copy(report = old.report.copy(warnings = listOf(diagnostic)))
        assertEquals(exported(snapshot)["warnings"], validation(snapshot)["warnings"])
        assertEquals(1, validation(snapshot)["summary"].asJsonObject["warningCount"].asInt)
    }
    fun testMarkupAndDocumentDiagnosticsHaveSeparateChannels() {
        pageFile(); companion()
        val old = capture()
        val markup = old.report.markup!!.copy(
            diagnostics = listOf(Diagnostic(DiagnosticCode.UNRESOLVED_MARKUP, "Markup scope", severity = Severity.WARNING)),
            documentDiagnostics = listOf(Diagnostic(DiagnosticCode.PARTIAL_ANALYSIS, "Document syntax", severity = Severity.WARNING)))
        val snapshot = old.copy(report = old.report.copy(markup = markup))
        val original = exported(snapshot)["markup"].asJsonObject
        val result = validation(snapshot)
        assertEquals(original["diagnostics"], result["markupDiagnostics"])
        assertEquals(original["documentDiagnostics"], result["documentDiagnostics"])
        assertEquals(1, result["summary"].asJsonObject["markupDiagnosticCount"].asInt)
        assertEquals(1, result["summary"].asJsonObject["documentDiagnosticCount"].asInt)
    }
    fun testComponentDiagnosticsIncludeTheirIdentity() {
        pageFile(); companion()
        val old = capture()
        val child = old.report.nodes.single { it.wicketId == "title" }
        val node = child.copy(diagnostics = listOf(Diagnostic(DiagnosticCode.UNRESOLVED_SCOPE, "Component scope", child.javaLocation)))
        val snapshot = old.copy(report = old.report.copy(nodes = old.report.nodes.map { if (it.nodeId == node.nodeId) node else it }))
        val result = validation(snapshot)
        val entry = result["componentDiagnostics"].asJsonArray.single().asJsonObject
        assertEquals(node.nodeId, entry["nodeId"].asString)
        assertEquals("title", entry["wicketId"].asString)
        assertEquals(1, result["summary"].asJsonObject["componentDiagnosticCount"].asInt)
        assertEquals(exported(snapshot)["components"].asJsonArray.single { it.asJsonObject["nodeId"].asString == node.nodeId }.asJsonObject["diagnostics"], entry["diagnostics"])
    }
    fun testMarkupElementDiagnosticsKeepTheirElementContext() {
        pageFile(); companion()
        val old = capture()
        val markup = old.report.markup!!
        val element = markup.elements.first { it.wicketId == "title" }
        val annotated = element.copy(diagnostics = listOf(Diagnostic(DiagnosticCode.UNRESOLVED_MARKUP, "Element issue", element.location)))
        val snapshot = old.copy(report = old.report.copy(markup = markup.copy(elements = markup.elements.map { if (it.nodeId == element.nodeId) annotated else it })))
        val result = validation(snapshot)
        val entry = result["markupElementDiagnostics"].asJsonArray.single().asJsonObject
        assertEquals(element.nodeId, entry["nodeId"].asString)
        assertEquals("title", entry["wicketId"].asString)
        assertEquals(1, result["summary"].asJsonObject["markupElementDiagnosticCount"].asInt)
    }
    fun testSummaryCountsRespectCodeSeverityAndConfidenceIndependently() {
        pageFile(); companion()
        val old = capture()
        val findings = listOf(
            MatchFinding(DiagnosticCode.JAVA_ONLY, "Unpaired", Confidence.CONFIRMED),
            MatchFinding(DiagnosticCode.MARKUP_ONLY, "Other", Confidence.CONFIRMED),
            MatchFinding(DiagnosticCode.UNRESOLVED_SCOPE, "Review", Confidence.INFERRED, severity = Severity.WARNING))
        val snapshot = old.copy(report = old.report.copy(matching = MatchReport(findings)))
        val result = validation(snapshot)
        val summary = result["summary"].asJsonObject
        assertEquals(3, summary["findingCount"].asInt)
        assertEquals(2, summary["findingsBySeverity"].asJsonObject["INFO"].asInt)
        assertEquals(1, summary["findingsBySeverity"].asJsonObject["WARNING"].asInt)
        assertFalse(summary["findingsBySeverity"].asJsonObject.has("ERROR"))
        assertEquals(2, summary["findingsByConfidence"].asJsonObject["CONFIRMED"].asInt)
        assertEquals(1, summary["findingsByConfidence"].asJsonObject["INFERRED"].asInt)
    }
    fun testEmptyFindingsProduceEmptyMapsAndZeroCounts() {
        pageFile(); companion()
        val old = capture()
        val result = validation(old.copy(report = old.report.copy(matching = MatchReport(emptyList()))))
        assertEquals(0, result["summary"].asJsonObject["findingCount"].asInt)
        assertEquals(0, result["summary"].asJsonObject["findingsByCode"].asJsonObject.size())
    }
    fun testStructureOutputRemainsTheSameReportAfterPipelineSharing() {
        pageFile(); companion()
        val snapshot = capture()
        val structure = JsonParser.parseString(query.render(snapshot)).asJsonObject
        assertEquals("wicket_get_page_structure", structure["tool"].asString)
        assertEquals(exported(snapshot), structure["analysis"])
        assertFalse(structure.has("validation"))
    }
    fun testValidationOmitsTheFullComponentAndMarkupTrees() {
        pageFile(); companion()
        val result = validation()
        assertFalse(result.has("components"))
        assertFalse(result.has("markup"))
        assertFalse(result.has("model"))
    }
    fun testValidationErrorsIdentifyTheCorrectTool() {
        val error = JsonParser.parseString(WicketPageAnalysisRequest.errorJson("CLASS_NOT_FOUND", "No source", WicketMcpReport.VALIDATION)).asJsonObject
        assertEquals("wicket_validate_page", error["tool"].asString)
        assertEquals("error", error["status"].asString)
        assertEquals("CLASS_NOT_FOUND", error["error"].asJsonObject["code"].asString)
    }
    fun testValidationByteLimitStillRejectsOversizedDiagnostics() {
        pageFile()
        val old = capture()
        val snapshot = old.copy(report = old.report.copy(warnings = listOf(Diagnostic(DiagnosticCode.PARTIAL_ANALYSIS, "界".repeat(180_000)))))
        assertFailure("RESPONSE_TOO_LARGE") { envelope(snapshot) }
    }
    fun testValidationUsesTheSharedGraphLimits() {
        pageFile(); val old = capture()
        assertFailure("RESPONSE_TOO_LARGE") { envelope(old.copy(report = old.report.copy(nodes = List(5_001) { old.report.nodes.first() }))) }
    }
    fun testValidationSnapshotStillRejectsUncommittedEdits() {
        val java = pageFile(); val old = capture()
        WriteCommandAction.runWriteCommandAction(project) { PsiDocumentManager.getInstance(project).getDocument(java)!!.setText(java.text + "\n// edit") }
        assertFailure("STALE_SNAPSHOT") { query.requireCurrent(project, old) }
    }
    fun testValidationCallsAreDeterministicAndReadOnly() {
        val java = pageFile(); val html = companion()
        val before = java.text to html.text
        assertEquals(envelope(), envelope())
        assertEquals(before, java.text to html.text)
    }
    fun testAsyncValidationUsesTheSamePipelineWithoutPublishingSidebarState() {
        pageFile(); companion(html = "<span wicket:id='htmlOnly'></span>")
        val service = project.service<WicketLensService>()
        val before = service.state
        val response = execute()
        assertEquals("wicket_validate_page", response["tool"].asString)
        assertEquals(2, response["validation"].asJsonObject["summary"].asJsonObject["findingCount"].asInt)
        assertSame(before, service.state)
    }
}
