package dev.wicketlens

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.intellij.mcpserver.impl.McpServerService
import com.intellij.mcpserver.settings.McpServerSettings
import com.intellij.mcpserver.McpSessionInvocationMode
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.projectRoots.JavaSdk
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.util.Disposer
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.impl.PsiDocumentManagerBase
import com.intellij.testFramework.DumbModeTestUtils
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.PsiTestUtil
import com.intellij.testFramework.builders.JavaModuleFixtureBuilder
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase
import dev.wicketlens.mcp.WicketMcpReport
import org.jetbrains.jps.model.java.JavaSourceRootType
import org.jetbrains.jps.model.java.JavaResourceRootType
import java.io.File
import java.net.URI
import java.net.ServerSocket
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.concurrent.CompletableFuture

/** Run separately with -PmcpHostTests=true: actual bundled host, no replacement server or test toolset. */
class WicketMcpHostTest : JavaCodeInsightFixtureTestCase() {
    private lateinit var server: McpServerService
    private lateinit var endpoint: String
    private var oldEnabled = false
    private var oldBrave = false
    private var oldPort = 0
    private var oldMode = McpSessionInvocationMode.VIA_ROUTER

    override fun setUp() {
        super.setUp()
        configure(myFixture, "first")
        val settings = McpServerSettings.getInstance().state
        oldEnabled = settings.enableMcpServer; oldBrave = settings.enableBraveMode; oldPort = settings.mcpServerPort
        oldMode = McpHostInvocationSettings.get()
        settings.enableMcpServer = true; settings.enableBraveMode = true
        settings.mcpServerPort = ServerSocket(0).use { it.localPort }
        McpHostInvocationSettings.set(McpSessionInvocationMode.DIRECT)
        server = McpServerService.getInstance()
        server.start()
        waitFor("MCP server startup") { server.serverPortFlow.value != null }
        endpoint = "http://127.0.0.1:${server.serverPortFlow.value}/stream"
    }
    override fun tearDown() {
        try {
            if (::server.isInitialized) {
                server.stop()
                waitFor("MCP server shutdown") { server.serverPortFlow.value == null }
                val settings = McpServerSettings.getInstance().state
                settings.enableMcpServer = oldEnabled; settings.enableBraveMode = oldBrave; settings.mcpServerPort = oldPort
                McpHostInvocationSettings.set(oldMode)
            }
        } finally { super.tearDown() }
    }
    private fun waitFor(label: String, seconds: Int = 15, condition: () -> Boolean) = PlatformTestUtil.waitWithEventsDispatching(label, condition, seconds)
    private fun <T> await(future: CompletableFuture<T>): T { waitFor("MCP HTTP response") { future.isDone }; return future.join() }
    private fun activeRequests(): Int {
        var count = 0
        Disposer.getTree().assertNoReferenceKeptInTree { value ->
            if (value.javaClass.name.startsWith("dev.wicketlens.mcp.WicketPageAnalysisRequest")) count++
            false
        }
        return count
    }
    private fun configure(fixture: CodeInsightTestFixture, id: String) {
        val target = fixture.module
        ApplicationManager.getApplication().runWriteAction {
            val model = ModuleManager.getInstance(fixture.project).getModifiableModel()
            model.renameModule(target, "shared"); model.commit()
        }
        ModuleRootModificationUtil.updateModel(target) { model -> model.contentEntries.forEach { entry -> entry.sourceFolders.forEach { entry.removeSourceFolder(it) } } }
        val root = fixture.tempDirFixture.findOrCreateDir("")
        PsiTestUtil.addContentRoot(target, root)
        PsiTestUtil.addSourceRoot(target, fixture.tempDirFixture.findOrCreateDir("src/main/java"), JavaSourceRootType.SOURCE)
        PsiTestUtil.addSourceRoot(target, fixture.tempDirFixture.findOrCreateDir("src/main/resources"), JavaResourceRootType.RESOURCE)
        val sdk = JavaSdk.getInstance().createJdk("MCP host SDK $id", System.getProperty("java.home"), false)
        ApplicationManager.getApplication().runWriteAction { ProjectJdkTable.getInstance().addJdk(sdk, fixture.testRootDisposable) }
        ModuleRootModificationUtil.setModuleSdk(target, sdk)
        System.getProperty("wicketlens.test.libraries").split(File.pathSeparator).forEachIndexed { n, path ->
            val jar = File(path); PsiTestUtil.addLibrary(fixture.testRootDisposable, target, "MCP host library $n", jar.parent, jar.name)
        }
        fixture.addFileToProject("src/main/java/example/McpPage.java", """
            package example; public class McpPage extends org.apache.wicket.markup.html.WebPage {
                public McpPage() { add(new org.apache.wicket.markup.html.basic.Label("$id")); }
            }
        """.trimIndent())
        fixture.addFileToProject("src/main/resources/example/McpPage.html", "<html><body><span wicket:id='$id'></span></body></html>")
        waitFor("Fixture smart mode") { !com.intellij.openapi.project.DumbService.isDumb(fixture.project) }
    }
    private inner class Client : AutoCloseable {
        private val http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build()
        private var session: String? = null
        private var nextId = 2
        init {
            val initialized = await(post("""{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-06-18","capabilities":{},"clientInfo":{"name":"wicketlens-host-test","version":"1"}}}"""))
            session = initialized.headers().firstValue("Mcp-Session-Id").orElse(null)
            assertNotNull(parse(initialized)["result"])
            await(post("""{"jsonrpc":"2.0","method":"notifications/initialized"}"""))
        }
        fun post(json: String): CompletableFuture<HttpResponse<String>> {
            val builder = HttpRequest.newBuilder(URI(endpoint)).timeout(Duration.ofSeconds(40))
                .header("Content-Type", "application/json").header("Accept", "application/json, text/event-stream")
                .header("MCP-Protocol-Version", "2025-06-18")
            session?.let { builder.header("Mcp-Session-Id", it) }
            return http.sendAsync(builder.POST(HttpRequest.BodyPublishers.ofString(json)).build(), HttpResponse.BodyHandlers.ofString())
        }
        fun start(view: WicketMcpReport, path: String = project.basePath!!, id: String = "first"): Pair<Int, CompletableFuture<HttpResponse<String>>> {
            val requestId = nextId++
            val args = JsonObject().apply {
                addProperty("projectPath", path); addProperty("className", "example.McpPage"); addProperty("moduleName", "shared")
                if (view == WicketMcpReport.CONTEXT) addProperty("componentPath", id)
            }
            val request = JsonObject().apply {
                addProperty("jsonrpc", "2.0"); addProperty("id", requestId); addProperty("method", "tools/call")
                add("params", JsonObject().apply { addProperty("name", view.tool); add("arguments", args) })
            }
            return requestId to post(request.toString())
        }
        fun call(view: WicketMcpReport, path: String = project.basePath!!, id: String = "first"): JsonObject {
            val (requestId, pending) = start(view, path, id)
            val rpc = parse(await(pending))
            assertEquals(requestId, rpc["id"].asInt)
            val result = rpc["result"].asJsonObject
            assertFalse(result["isError"]?.asBoolean == true)
            return JsonParser.parseString(result["content"].asJsonArray.single().asJsonObject["text"].asString).asJsonObject
        }
        fun cancel(id: Int) {
            val response = await(post("""{"jsonrpc":"2.0","method":"notifications/cancelled","params":{"requestId":$id,"reason":"WicketLens cancellation test"}}"""))
            assertEquals("Cancellation notification: ${response.body()}", 202, response.statusCode())
        }
        override fun close() { http.shutdownNow() }
    }
    private fun parse(response: HttpResponse<String>): JsonObject {
        assertEquals(response.body(), 200, response.statusCode())
        val body = response.body()
        return JsonParser.parseString(if (body.trimStart().startsWith("{")) body else body.lineSequence().first { it.startsWith("data:") }.removePrefix("data:").trim()).asJsonObject
    }
    fun testWireCancellationIsAcceptedButHostWorkNeedsItsDeadlineAndAReconnection() {
        Client().use { client ->
            val token = DumbModeTestUtils.startEternalDumbModeTask(project)
            try {
                val requests = WicketMcpReport.entries.map { it to client.start(it) }
                val control = client.start(WicketMcpReport.STRUCTURE)
                waitFor("Semantic requests entered indexing wait") { activeRequests() == 4 }
                requests.forEach { (_, pending) -> assertFalse(pending.second.isDone); client.cancel(pending.first) }
                val observed = CompletableFuture<Unit>()
                CompletableFuture.delayedExecutor(1, java.util.concurrent.TimeUnit.SECONDS).execute { observed.complete(Unit) }
                await(observed)
                assertEquals("Cancellation notifications have not stopped the three tool jobs", 4, activeRequests())
                // This SDK accepts notifications/cancelled without cancelling its incoming tool jobs.
                // Verify the real deadline bounds abandoned work; do not add a parallel cancellation protocol.
                waitFor("Host requests are released by the tool deadline", 40) { activeRequests() == 0 }
                for ((view, pending) in requests + (WicketMcpReport.STRUCTURE to control)) {
                    val raw = await(pending.second)
                    println("WIRE ${if (pending == control) "CONTROL" else view} ${raw.statusCode()} ${raw.body().take(150)}")
                    if (raw.statusCode() == 404) {
                        // The bundled HTTP transport can end a timed-out stream with an empty 404.
                        assertTrue(raw.body().isEmpty())
                        continue
                    }
                    val result = parse(raw)["result"].asJsonObject
                    assertTrue(result["isError"].asBoolean)
                    val error = JsonParser.parseString(result["content"].asJsonArray.single().asJsonObject["text"].asString).asJsonObject
                    assertEquals(view.tool, error["tool"].asString)
                    assertEquals("TIMED_OUT", error["error"].asJsonObject["code"].asString)
                }
            } finally { DumbModeTestUtils.endEternalDumbModeTaskAndWaitForSmartMode(project, token) }
            // Long blocked calls also expire this SDK's HTTP session, including the uncancelled control.
            val expired = await(client.start(WicketMcpReport.STRUCTURE).second)
            assertEquals(404, expired.statusCode())
            assertTrue(expired.body().contains("session not found"))
        }
        Client().use { recovered -> WicketMcpReport.entries.forEach { assertEquals("ok", recovered.call(it)["status"].asString) } }
    }
    fun testDisconnectDoesNotPoisonOtherRequestsAndWorkIsReleasedAfterIndexing() {
        for (view in WicketMcpReport.entries) {
            val token = DumbModeTestUtils.startEternalDumbModeTask(project)
            val client = Client()
            try {
                val response = client.start(view).second
                waitFor("Request entered indexing wait") { activeRequests() == 1 }
                response.cancel(true)
                client.close()
            } finally { DumbModeTestUtils.endEternalDumbModeTaskAndWaitForSmartMode(project, token) }
            waitFor("Disconnected request released its registration") { activeRequests() == 0 }
            Client().use { assertEquals("ok", it.call(view)["status"].asString) }
        }
    }
    fun testHostRoutesCollidingClassAndModuleNamesToTheSelectedOpenProject() {
        // Initialize primary-project analysis services before the sibling's listener-leak baseline.
        Client().use { it.call(WicketMcpReport.STRUCTURE) }
        val factory = IdeaTestFixtureFactory.getFixtureFactory()
        val builder = factory.createFixtureBuilder("second-wicketlens-host-project")
        builder.addModule(JavaModuleFixtureBuilder::class.java)
        val other = factory.createCodeInsightFixture(builder.fixture)
        other.setUp()
        try {
            configure(other, "second")
            assertTrue(ProjectManager.getInstance().openProjects.toList().containsAll(listOf(project, other.project)))
            Client().use { client ->
                val requests = WicketMcpReport.entries.flatMap { view -> listOf(
                    Triple(view, "first", client.start(view)),
                    Triple(view, "second", client.start(view, other.project.basePath!!, "second"))) }
                for ((view, expected, pending) in requests) {
                    val rpc = parse(await(pending.second))
                    assertEquals(pending.first, rpc["id"].asInt)
                    val result = rpc["result"].asJsonObject
                    assertFalse(result["isError"]?.asBoolean == true)
                    val value = JsonParser.parseString(result["content"].asJsonArray.single().asJsonObject["text"].asString).asJsonObject
                    val data = when (view) {
                        WicketMcpReport.STRUCTURE -> value["analysis"].asJsonObject["components"].asJsonArray.filter { !it.asJsonObject["wicketId"].isJsonNull }.single().asJsonObject["wicketId"].asString
                        WicketMcpReport.CONTEXT -> value["context"].asJsonObject["componentPath"].asString
                        WicketMcpReport.VALIDATION -> if (value["validation"].asJsonObject["findings"].asJsonArray.single().asJsonObject["javaLocation"].asJsonObject["file"].asString.startsWith(other.tempDirPath)) "second" else "first"
                    }
                    assertEquals(expected, data)
                }
                val token = DumbModeTestUtils.startEternalDumbModeTask(project)
                try {
                    val blocked = client.start(WicketMcpReport.STRUCTURE)
                    waitFor("First project is waiting for indexing") { activeRequests() == 1 }
                    val second = client.call(WicketMcpReport.CONTEXT, other.project.basePath!!, "second")
                    assertEquals("second", second["context"].asJsonObject["componentPath"].asString)
                    assertFalse(blocked.second.isDone)
                    blocked.second.cancel(true)
                } finally { DumbModeTestUtils.endEternalDumbModeTaskAndWaitForSmartMode(project, token) }
                waitFor("Released first-project request") { activeRequests() == 0 }
            }
        } finally { other.tearDown() }
    }
}
