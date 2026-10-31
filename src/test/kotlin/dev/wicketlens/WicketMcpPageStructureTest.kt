package dev.wicketlens

import com.google.gson.JsonParser
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.components.service
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.psi.*
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.PsiTestUtil
import dev.wicketlens.export.WicketContextExporter
import dev.wicketlens.mcp.*
import dev.wicketlens.model.*
import dev.wicketlens.ui.WicketLensService
import kotlinx.coroutines.*
import org.jetbrains.jps.model.java.JavaSourceRootType

class WicketMcpPageStructureTest : WicketProjectPsiTestCase() {
    private val query = WicketPageStructureRequest()
    private fun snapshot() = query.capture(project, "example.ExamplePage")
    private fun failure(code: String, action: () -> Unit) {
        try { action(); fail("Expected $code") } catch (failure: PageStructureFailure) { assertEquals(code, failure.code) }
    }
    private fun edit(file: PsiFile, text: String, commit: Boolean = true) {
        WriteCommandAction.runWriteCommandAction(project) { PsiDocumentManager.getInstance(project).getDocument(file)!!.setText(text) }
        if (commit) PsiDocumentManager.getInstance(project).commitAllDocuments()
    }
    private fun execute(): String {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val result = scope.async { query.execute(project, "example.ExamplePage") }
            PlatformTestUtil.waitWithEventsDispatching("MCP structure request", { result.isCompleted }, 15)
            return runBlocking { result.await() }
        } finally { scope.cancel() }
    }

    fun testEnvelopeContainsTheExistingExportWithoutChangingItsSchema() {
        pageFile(); companion()
        val snapshot = snapshot()
        val result = JsonParser.parseString(query.render(snapshot)).asJsonObject
        assertEquals(1, result["schemaVersion"].asInt)
        assertEquals("ok", result["status"].asString)
        assertFalse(result["truncated"].asBoolean)
        assertEquals(JsonParser.parseString(WicketContextExporter().export(snapshot.report)), result["analysis"])
        assertEquals("MATCHED", result["analysis"].asJsonObject["matching"].asJsonObject["findings"].asJsonArray.single().asJsonObject["code"].asString)
        assertEquals(module.name, result["target"].asJsonObject["moduleName"].asString)
    }
    fun testPanelUsesTheSameAnalysisPipeline() {
        myFixture.addFileToProject("src/main/java/example/ExamplePage.java", """
            package example; public class ExamplePage extends org.apache.wicket.markup.html.panel.Panel {
              public ExamplePage(String id) { super(id); add(new org.apache.wicket.markup.html.basic.Label("title")); }
            }
        """.trimIndent())
        companion(html = "<wicket:panel><span wicket:id='title'></span></wicket:panel>")
        assertEquals("title", snapshot().report.nodes.single { it.wicketId != null }.wicketId)
    }
    fun testFormComponentPanelIsAnEligibleRoot() {
        myFixture.addFileToProject("src/main/java/example/ExamplePage.java", """
            package example; public class ExamplePage extends org.apache.wicket.markup.html.form.FormComponentPanel<String> {
              public ExamplePage(String id) { super(id); }
            }
        """.trimIndent())
        assertEquals("example.ExamplePage", snapshot().report.qualifiedClassName)
    }
    fun testMissingMarkupRetainsTheExistingUncertainty() {
        pageFile()
        val report = snapshot().report
        assertNull(report.markup)
        assertTrue(report.warnings.isNotEmpty())
    }
    fun testUnsupportedSetupDoesNotBecomeAConfirmedBinding() {
        pageFile(body = "if (System.getProperty(\"enabled\") != null) add(new Label(\"title\"));")
        companion()
        assertTrue(snapshot().report.matching!!.findings.none { it.code == DiagnosticCode.MATCHED })
    }
    fun testPlainJavaClassIsRejected() {
        myFixture.addFileToProject("src/main/java/example/ExamplePage.java", "package example; public class ExamplePage {}")
        failure("UNSUPPORTED_TARGET") { snapshot() }
    }
    fun testUnknownClassIsNotFoundInThisProject() { failure("CLASS_NOT_FOUND") { snapshot() } }
    fun testLibraryClassIsNeverAnAnalysisTarget() {
        failure("CLASS_NOT_FOUND") { query.capture(project, "org.apache.wicket.markup.html.WebPage") }
    }
    fun testClassNameCannotBeAFilePath() {
        failure("INVALID_REQUEST") { query.capture(project, "../outside/ExamplePage.java") }
    }
    fun testOversizedClassNameIsRejectedBeforeLookup() { failure("INVALID_REQUEST") { query.capture(project, "a".repeat(513)) } }
    fun testBlankModuleNameIsRejected() { failure("INVALID_REQUEST") { query.capture(project, "example.ExamplePage", " ") } }
    fun testUnknownModuleIsRejected() { failure("MODULE_NOT_FOUND") { query.capture(project, "example.ExamplePage", "missing-module") } }
    fun testUnknownSourceSetIsRejected() { failure("INVALID_REQUEST") { query.capture(project, "example.ExamplePage", sourceSet = "production") } }
    fun testExactModuleCanSelectItsOwnSource() {
        pageFile()
        assertEquals(module.name, query.capture(project, "example.ExamplePage", module.name).moduleName)
    }
    fun testJavaOutsideSourceRootsIsNotSelected() {
        myFixture.addFileToProject("unconfigured/example/ExamplePage.java", "package example; public class ExamplePage extends org.apache.wicket.markup.html.WebPage {}")
        failure("CLASS_NOT_FOUND") { snapshot() }
    }
    private fun addTestPage() {
        PsiTestUtil.addSourceRoot(module, myFixture.tempDirFixture.findOrCreateDir("src/test/java"), JavaSourceRootType.TEST_SOURCE)
        myFixture.addFileToProject("src/test/java/example/ExamplePage.java", """
            package example; public class ExamplePage extends org.apache.wicket.markup.html.WebPage {
              public ExamplePage() { add(new org.apache.wicket.markup.html.basic.Label("testTitle")); }
            }
        """.trimIndent())
    }
    fun testMainSelectionDoesNotFallBackToTestSources() { addTestPage(); failure("CLASS_NOT_FOUND") { snapshot() } }
    fun testExplicitTestSelectionUsesTheTestDeclaration() {
        pageFile(); addTestPage()
        val selected = query.capture(project, "example.ExamplePage", sourceSet = "test")
        assertEquals("test", selected.sourceSet)
        assertEquals("testTitle", selected.report.nodes.single { it.wicketId != null }.wicketId)
    }
    fun testAnySelectionRejectsDuplicateMainAndTestDeclarations() {
        pageFile(); addTestPage()
        failure("AMBIGUOUS_CLASS") { query.capture(project, "example.ExamplePage", sourceSet = "any") }
    }
    fun testUncommittedDocumentsCannotProduceACapture() {
        val java = pageFile()
        edit(java, java.text.replace("title", "newTitle"), commit = false)
        failure("UNCOMMITTED_DOCUMENTS") { snapshot() }
    }
    fun testJavaEditInvalidatesTheCapturedSnapshot() {
        val java = pageFile(); companion()
        val old = snapshot()
        edit(java, java.text.replace("title", "newTitle"), commit = false)
        failure("STALE_SNAPSHOT") { query.requireCurrent(project, old) }
    }
    fun testMarkupEditInvalidatesTheCapturedSnapshot() {
        pageFile(); val html = companion()
        val old = snapshot()
        edit(html, "<span wicket:id='changed'></span>")
        failure("STALE_SNAPSHOT") { query.requireCurrent(project, old) }
    }
    fun testRootChangesInvalidateTheCapturedSnapshot() {
        pageFile(); val old = snapshot()
        ModuleRootModificationUtil.updateModel(module) { model -> model.addContentEntry(myFixture.tempDirFixture.findOrCreateDir("new-content")) }
        failure("STALE_SNAPSHOT") { query.requireCurrent(project, old) }
    }
    fun testUnicodeByteLimitReturnsAnErrorInsteadOfTruncating() {
        pageFile()
        val old = snapshot()
        val oversized = old.copy(report = old.report.copy(warnings = listOf(Diagnostic(DiagnosticCode.PARTIAL_ANALYSIS, "界".repeat(180_000)))))
        failure("RESPONSE_TOO_LARGE") { query.render(oversized) }
    }
    fun testComponentCountLimitIsExplicit() {
        pageFile(); val old = snapshot()
        val oversized = old.copy(report = old.report.copy(nodes = List(5_001) { old.report.nodes.first() }))
        failure("RESPONSE_TOO_LARGE") { query.render(oversized) }
    }
    fun testMarkupCountLimitIsExplicit() {
        pageFile(); companion(); val old = snapshot()
        val markup = old.report.markup!!
        val oversized = old.copy(report = old.report.copy(markup = markup.copy(elements = List(10_001) { markup.elements.first() })))
        failure("RESPONSE_TOO_LARGE") { query.render(oversized) }
    }
    fun testRepeatedRequestsAreDeterministicAndDoNotModifyDocuments() {
        val java = pageFile(); val html = companion()
        val before = java.text to html.text
        val first = query.render(snapshot())
        val second = query.render(snapshot())
        assertEquals(first, second)
        assertEquals(before, java.text to html.text)
    }
    fun testAsyncRequestUsesTheLatestCommittedText() {
        val java = pageFile(); companion()
        edit(java, java.text.replace("title", "newTitle"))
        val result = JsonParser.parseString(execute()).asJsonObject["analysis"].asJsonObject
        assertTrue(result["components"].asJsonArray.any { it.asJsonObject["wicketId"].let { id -> !id.isJsonNull && id.asString == "newTitle" } })
    }
    fun testAsyncRequestDoesNotChangeSidebarState() {
        pageFile(); companion()
        val service = project.service<WicketLensService>()
        val before = service.state
        execute()
        assertSame(before, service.state)
    }
    fun testAsyncRequestWaitsForUncommittedEditorChanges() {
        val java = pageFile(); companion()
        edit(java, java.text.replace("title", "pendingTitle"), commit = false)
        com.intellij.openapi.application.ApplicationManager.getApplication().invokeLater {
            PsiDocumentManager.getInstance(project).commitAllDocuments()
        }
        val response = JsonParser.parseString(execute()).asJsonObject["analysis"].asJsonObject
        assertTrue(response["components"].asJsonArray.any { it.asJsonObject["wicketId"].let { id -> !id.isJsonNull && id.asString == "pendingTitle" } })
    }
    fun testCallerCancellationIsNotTurnedIntoAnAnalysisError() {
        pageFile(); companion()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val result = scope.async {
                currentCoroutineContext().cancel()
                query.execute(project, "example.ExamplePage")
            }
            PlatformTestUtil.waitWithEventsDispatching("Cancelled MCP request", { result.isCompleted }, 15)
            try { runBlocking { result.await() }; fail("Expected cancellation") }
            catch (_: CancellationException) { /* No fallback/error response is returned after client cancellation. */ }
        } finally { scope.cancel() }
    }
}
