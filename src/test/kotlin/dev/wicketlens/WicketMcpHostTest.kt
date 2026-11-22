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
        WicketTreeFixture.initializeDefaultProjectAnnotations()
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
        fixture.enableInspections(dev.wicketlens.spring.WicketSpringInjectionInspection(),
            dev.wicketlens.spring.WicketSpringSerializationInspection(),dev.wicketlens.spring.WicketSpringAnnotationInspection(),
            dev.wicketlens.spring.WicketSpringBeanTypeInspection(),dev.wicketlens.spring.WicketSpringBootstrapInspection())
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
        (System.getProperty("wicketlens.test.libraries").split(File.pathSeparator) +
            System.getProperty("wicketlens.test.springLibraries").split(File.pathSeparator)).distinct().forEachIndexed { n, path ->
            val jar = File(path); PsiTestUtil.addLibrary(fixture.testRootDisposable, target, "MCP host library $n", jar.parent, jar.name)
        }
        fixture.addFileToProject("src/main/java/example/McpPage.java", """
            package example; public class McpPage extends org.apache.wicket.markup.html.WebPage {
                @org.apache.wicket.spring.injection.annot.SpringBean(name="$id") transient Service dependency$id = null;
                @org.apache.wicket.spring.injection.annot.SpringBean(name="&$id") org.springframework.beans.factory.FactoryBean<Service> factory;
                public McpPage() { add(new org.apache.wicket.markup.html.basic.Label("$id")); }
            }
        """.trimIndent())
        fixture.addFileToProject("src/main/resources/example/McpPage.html", "<html><body><span wicket:id='$id'></span></body></html>")
        fixture.addFileToProject("src/main/java/example/Service.java", "package example; public class Service {}")
        fixture.addFileToProject("src/main/java/example/Config.java", """
            package example; @org.springframework.context.annotation.Configuration public class Config {
                @org.springframework.context.annotation.Bean("$id") @org.springframework.context.annotation.Profile({"prod & eu", "staging"})
                org.springframework.beans.factory.FactoryBean<Service> service() { return null; }
            }
        """.trimIndent())
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
        fun start(view: WicketMcpReport, path: String = project.basePath!!, id: String = "first",
            className: String? = "example.McpPage", includeNavigationContexts: Boolean = false, contextId: String? = null,
            routePath: String? = null, applicationClass: String? = null, mountedResource: Boolean = false,
            expectedContentType: String? = null, routeParameters: Map<String, String>? = null,
            expectedBodySha256: String? = null): Pair<Int, CompletableFuture<HttpResponse<String>>> {
            val requestId = nextId++
            val args = JsonObject().apply {
                addProperty("projectPath", path); addProperty("moduleName", "shared")
                className?.let { addProperty("className", it) }
                if (view == WicketMcpReport.CONTEXT) addProperty("componentPath", id)
                if (contextId != null) addProperty("contextId", contextId)
                if (includeNavigationContexts) addProperty("includeNavigationContexts", true)
                routePath?.let { addProperty("routePath", it) }
                applicationClass?.let { addProperty("applicationClass", it) }
                if (mountedResource) addProperty("mountedResource", true)
                expectedContentType?.let { addProperty("expectedContentType", it) }
                expectedBodySha256?.let { addProperty("expectedBodySha256", it) }
                routeParameters?.let { values -> add("routeParameters", JsonObject().apply { values.forEach(::addProperty) }) }
            }
            val request = JsonObject().apply {
                addProperty("jsonrpc", "2.0"); addProperty("id", requestId); addProperty("method", "tools/call")
                add("params", JsonObject().apply { addProperty("name", view.tool); add("arguments", args) })
            }
            return requestId to post(request.toString())
        }
        fun call(view: WicketMcpReport, path: String = project.basePath!!, id: String = "first",
            className: String? = "example.McpPage", includeNavigationContexts: Boolean = false, contextId: String? = null,
            routePath: String? = null, applicationClass: String? = null, mountedResource: Boolean = false,
            expectedContentType: String? = null, routeParameters: Map<String, String>? = null, expectedBodySha256: String? = null): JsonObject {
            val (requestId, pending) = start(view, path, id, className, includeNavigationContexts, contextId, routePath, applicationClass,
                mountedResource, expectedContentType, routeParameters, expectedBodySha256)
            val rpc = parse(await(pending))
            assertEquals(requestId, rpc["id"].asInt)
            val result = rpc["result"].asJsonObject
            assertFalse(result.toString(), result["isError"]?.asBoolean == true)
            return JsonParser.parseString(result["content"].asJsonArray.single().asJsonObject["text"].asString).asJsonObject
        }
        fun cancel(id: Int) {
            val response = await(post("""{"jsonrpc":"2.0","method":"notifications/cancelled","params":{"requestId":$id,"reason":"WicketLens cancellation test"}}"""))
            assertEquals("Cancellation notification: ${response.body()}", 202, response.statusCode())
        }
        fun routes(path: String = project.basePath!!, applicationClass: String? = null): JsonObject {
            val request = JsonObject().apply {
                addProperty("jsonrpc", "2.0"); addProperty("id", nextId++); addProperty("method", "tools/call")
                add("params", JsonObject().apply {
                    addProperty("name", "wicket_get_routes")
                    add("arguments", JsonObject().apply {
                        addProperty("projectPath", path)
                        addProperty("moduleName", "shared")
                        applicationClass?.let { addProperty("applicationClass", it) }
                    })
                })
            }
            val result = parse(await(post(request.toString())))["result"].asJsonObject
            assertFalse(result.toString(), result["isError"]?.asBoolean == true)
            return JsonParser.parseString(result["content"].asJsonArray.single().asJsonObject["text"].asString).asJsonObject
        }
        override fun close() { http.shutdownNow() }
    }
    private fun parse(response: HttpResponse<String>): JsonObject {
        assertEquals(response.body(), 200, response.statusCode())
        val body = response.body()
        return JsonParser.parseString(if (body.trimStart().startsWith("{")) body else body.lineSequence().first { it.startsWith("data:") }.removePrefix("data:").trim()).asJsonObject
    }
    private fun mountedApplication(fixture: CodeInsightTestFixture, path: String) {
        fixture.addFileToProject("src/main/java/example/BaseRoutesApplication.java", """
            package example; public abstract class BaseRoutesApplication extends org.apache.wicket.protocol.http.WebApplication {
                protected void init() { super.init(); registerPages(); }
                private void registerPages() { mount(new org.apache.wicket.core.request.mapper.MountedMapper("$path", McpPage.class)); }
            }
        """.trimIndent())
        fixture.addFileToProject("src/main/java/example/App.java", """
            package example; public class App extends BaseRoutesApplication {
                public Class<? extends org.apache.wicket.Page> getHomePage() { return McpPage.class; }
            }
        """.trimIndent())
    }
    private fun beanMountedApplication(fixture: CodeInsightTestFixture, path: String) {
        fixture.addFileToProject("src/main/java/example/BeanRoutes.java", """
            package example;
            import org.apache.wicket.protocol.http.WebApplication;
            public class BeanRoutes {
                @org.springframework.context.annotation.Bean
                WebApplication application() {
                    return new WebApplication() {
                        public Class<? extends org.apache.wicket.Page> getHomePage() { return McpPage.class; }
                        protected void init() { super.init(); mountPage("$path", McpPage.class); }
                    };
                }
            }
        """.trimIndent())
    }
    fun testMountedRoutesAreRegisteredReadOnlyAndDiscoverableThroughTheActualHost() {
        mountedApplication(myFixture, "/first")
        Client().use { client ->
            val listing = parse(await(client.post("""{"jsonrpc":"2.0","id":900,"method":"tools/list","params":{}}""")))["result"].asJsonObject["tools"].asJsonArray
            val tool = listing.single { it.asJsonObject["name"].asString == "wicket_get_routes" }.asJsonObject
            assertTrue(tool["annotations"].asJsonObject["readOnlyHint"].asBoolean)
            assertFalse(tool["annotations"].asJsonObject["destructiveHint"].asBoolean)
            val schema = tool["inputSchema"].asJsonObject["properties"].asJsonObject
            assertTrue(schema.has("projectPath") && schema.has("moduleName") && schema.has("applicationClass") && schema.has("sourceSet"))
            assertFalse(schema.has("className"))
            val response = client.routes()
            assertEquals("ok", response["status"].asString)
            val routes = response["routes"].asJsonObject
            assertFalse(routes["runtimeRoutesComplete"].asBoolean)
            val app = routes["applications"].asJsonArray.single().asJsonObject
            assertEquals("example.App", app["className"].asString)
            val mount = app["mounts"].asJsonArray.single().asJsonObject
            assertEquals("/first", mount["path"].asString)
            assertEquals("example.McpPage", mount["pageClass"].asString)
            assertEquals("DECLARED", mount["status"].asString)
            assertTrue(mount["notes"].asJsonArray.any { it.asString.contains("Inline MountedMapper") })
            assertTrue(mount["registration"].asJsonObject["file"].asString.endsWith("BaseRoutesApplication.java"))
            assertTrue(mount["notes"].asJsonArray.any { it.asString.contains("BaseRoutesApplication.registerPages") })
            assertEquals(response, client.routes())
        }
    }

    fun testNavigationIndexAndSelectionUseTheSharedProjectionThroughTheActualHost() {
        val java=WicketContentFactoryFixture.install(myFixture);val before=java.text to java.modificationStamp
        Client().use { client ->
            val listing=parse(await(client.post("""{"jsonrpc":"2.0","id":905,"method":"tools/list","params":{}}""")))["result"].asJsonObject["tools"].asJsonArray
            val descriptor=listing.single {it.asJsonObject["name"].asString==WicketMcpReport.NAVIGATION.tool}.asJsonObject
            assertTrue(descriptor["annotations"].asJsonObject["readOnlyHint"].asBoolean)
            assertFalse(descriptor["annotations"].asJsonObject["destructiveHint"].asBoolean)
            assertTrue(descriptor["inputSchema"].asJsonObject["properties"].asJsonObject.has("contextId"))
            assertTrue(descriptor["description"].asString.contains("512-KiB"))
            val index=client.call(WicketMcpReport.NAVIGATION,className="example.FactoryPage")
            assertFalse(index.has("analysis"))
            val item=index["navigationContexts"].asJsonObject["contexts"].asJsonArray.single {it.asJsonObject["relationship"].asString=="FACTORY_TEMPLATE"}.asJsonObject
            val selected=client.call(WicketMcpReport.NAVIGATION,className="example.FactoryPage",contextId=item["contextId"].asString)
            val context=selected["navigationContexts"].asJsonObject["contexts"].asJsonArray.single().asJsonObject
            context.remove("contextId")
            val native=dev.wicketlens.mcp.WicketPageAnalysisRequest().capture(project,"example.FactoryPage",view=WicketMcpReport.NAVIGATION).navigationContexts!!.contexts.single {it.relationship=="FACTORY_TEMPLATE"}
            assertEquals(dev.wicketlens.mcp.WicketNavigationExport.context(native),context)
            assertEquals(before,java.text to java.modificationStamp)
        }
    }
    fun testStoredConfirmationRouteIsSharedReadOnlyThroughTheActualHost() {
        ModuleRootModificationUtil.updateModel(module) {model ->model.getModuleExtension(com.intellij.openapi.roots.LanguageLevelModuleExtension::class.java).languageLevel=com.intellij.pom.java.LanguageLevel.JDK_25}
        val java=WicketConfirmationFixture.install(myFixture);val before=java.text to java.modificationStamp
        Client().use {client ->
            val index=client.call(WicketMcpReport.NAVIGATION,className="example.ConfirmationPage")
            val descriptor=index["navigationContexts"].asJsonObject["contexts"].asJsonArray.single {it.asJsonObject["relationship"].asString=="CONFIRMATION_TEMPLATE"}.asJsonObject
            val selected=client.call(WicketMcpReport.NAVIGATION,className="example.ConfirmationPage",contextId=descriptor["contextId"].asString)
            val context=selected["navigationContexts"].asJsonObject["contexts"].asJsonArray.single().asJsonObject
            context.remove("contextId")
            val native=dev.wicketlens.mcp.WicketPageAnalysisRequest().capture(project,"example.ConfirmationPage",view=WicketMcpReport.NAVIGATION).navigationContexts!!.contexts.single {it.relationship=="CONFIRMATION_TEMPLATE"}
            assertEquals(dev.wicketlens.mcp.WicketNavigationExport.context(native),context)
            val metadata=context["submissionTemplate"].asJsonObject
            assertEquals("SOURCE_DECLARED_CONFIRMATION",metadata["kind"].asString)
            assertEquals("unknown",metadata["confirmation"].asJsonObject["currentRegistration"].asString)
            assertEquals("not_evaluated",metadata["callbackExecution"].asString)
            assertEquals(before,java.text to java.modificationStamp)
        }
    }
    fun testBulkSelectionClearIsSharedAndUnevaluatedThroughTheActualHost() {
        ModuleRootModificationUtil.updateModel(module) {model ->model.getModuleExtension(com.intellij.openapi.roots.LanguageLevelModuleExtension::class.java).languageLevel=com.intellij.pom.java.LanguageLevel.JDK_25}
        val java=WicketSelectionClearFixture.install(myFixture);val before=java.text to java.modificationStamp
        Client().use {client ->
            val native=dev.wicketlens.mcp.WicketPageAnalysisRequest().capture(project,"example.ConfirmationPage",view=WicketMcpReport.NAVIGATION)
            val position=native.navigationContexts!!.contexts.indexOfFirst {it.report.submissionTemplate?.confirmation?.selectionClears?.isNotEmpty()==true}
            assertTrue(position>=0)
            val index=client.call(WicketMcpReport.NAVIGATION,className="example.ConfirmationPage")
            val id=index["navigationContexts"].asJsonObject["contexts"].asJsonArray[position].asJsonObject["contextId"].asString
            val selected=client.call(WicketMcpReport.NAVIGATION,className="example.ConfirmationPage",contextId=id)
            val context=selected["navigationContexts"].asJsonObject["contexts"].asJsonArray.single().asJsonObject;context.remove("contextId")
            assertEquals(dev.wicketlens.mcp.WicketNavigationExport.context(native.navigationContexts.contexts[position]),context)
            val operation=context["submissionTemplate"].asJsonObject["confirmation"].asJsonObject["selectionClears"].asJsonArray.single().asJsonObject
            assertEquals("not_evaluated",operation["execution"].asString)
            assertEquals("unknown",operation["clearOutcome"].asString);assertEquals("unknown",operation["columnDispatch"].asString)
            assertEquals("rows",operation["targetComponentPath"].asString)
            assertEquals(before,java.text to java.modificationStamp)
        }
    }
    fun testRecordActionDeclarationsAreSharedReadOnlyThroughTheActualHost() {
        ModuleRootModificationUtil.updateModel(module) {model ->model.getModuleExtension(com.intellij.openapi.roots.LanguageLevelModuleExtension::class.java).languageLevel=com.intellij.pom.java.LanguageLevel.JDK_25}
        val java=WicketRecordActionFixture.install(myFixture);val before=java.text to java.modificationStamp
        Client().use {client ->
            val native=dev.wicketlens.mcp.WicketPageAnalysisRequest().capture(project,"example.ConfirmationPage",view=WicketMcpReport.NAVIGATION)
            val index=client.call(WicketMcpReport.NAVIGATION,className="example.ConfirmationPage")["navigationContexts"].asJsonObject["contexts"].asJsonArray
            native.navigationContexts!!.contexts.withIndex().filter {it.value.report.actionTemplate!=null}.forEach {(position,value)->
                val selected=client.call(WicketMcpReport.NAVIGATION,className="example.ConfirmationPage",contextId=index[position].asJsonObject["contextId"].asString)
                val context=selected["navigationContexts"].asJsonObject["contexts"].asJsonArray.single().asJsonObject;context.remove("contextId")
                assertEquals(dev.wicketlens.mcp.WicketNavigationExport.context(value),context)
                val action=context["actionTemplate"].asJsonObject
                assertEquals("not_reconstructed",action["uiTrigger"].asString);assertEquals("not_evaluated",action["callbackExecution"].asString)
                assertEquals("unknown",action["businessOutcome"].asString)
                assertEquals("java.util.List.replaceAll",action["updates"].asJsonArray.single().asJsonObject["collectionOperation"].asString)
                assertFalse(action.has("triggerNodeId"));assertFalse(context.has("submissionTemplate"))
            }
            assertEquals(2,native.navigationContexts.contexts.count {it.report.actionTemplate!=null})
            assertEquals(before,java.text to java.modificationStamp)
        }
    }
    fun testStoredActionInitialDeclarationsAreSharedThroughTheActualHost() {
        ModuleRootModificationUtil.updateModel(module) {model ->model.getModuleExtension(com.intellij.openapi.roots.LanguageLevelModuleExtension::class.java).languageLevel=com.intellij.pom.java.LanguageLevel.JDK_25}
        val java=WicketRecordActionFixture.install(myFixture,WicketRecordActionFixture.plainPage);val before=java.text to java.modificationStamp
        Client().use {client ->
            val native=dev.wicketlens.mcp.WicketPageAnalysisRequest().capture(project,"example.ConfirmationPage")
            val expected=com.google.gson.JsonParser.parseString(dev.wicketlens.export.WicketContextExporter().export(native.report)).asJsonObject
            val structure=client.call(WicketMcpReport.STRUCTURE,className="example.ConfirmationPage")["analysis"].asJsonObject
            assertEquals(expected,structure)
            val proof=structure["storedActionRegistrations"].asJsonObject
            assertEquals("not_evaluated",proof["callbackExecution"].asString);assertEquals("unknown",proof["laterTreeState"].asString)
            val contexts=client.call(WicketMcpReport.NAVIGATION,className="example.ConfirmationPage")["navigationContexts"].asJsonObject["contexts"].asJsonArray
            val action=contexts.single {it.asJsonObject["relationship"].asString=="ACTION_TEMPLATE"}.asJsonObject
            val selected=client.call(WicketMcpReport.NAVIGATION,className="example.ConfirmationPage",contextId=action["contextId"].asString)
            val value=selected["navigationContexts"].asJsonObject["contexts"].asJsonArray.single().asJsonObject
            assertEquals(1,value["bindings"].asJsonArray.size());assertEquals("unknown",value["actionTemplate"].asJsonObject["businessOutcome"].asString)
            assertEquals(before,java.text to java.modificationStamp)
        }
    }
    fun testRepeatedRegistrarActionsStaySeparateThroughTheActualHost() {
        ModuleRootModificationUtil.updateModel(module) {model ->model.getModuleExtension(com.intellij.openapi.roots.LanguageLevelModuleExtension::class.java).languageLevel=com.intellij.pom.java.LanguageLevel.JDK_25}
        val java=WicketRecordActionFixture.install(myFixture,WicketRecordActionFixture.repeatedPage);val before=java.text to java.modificationStamp
        Client().use {client ->
            val query=dev.wicketlens.mcp.WicketPageAnalysisRequest()
            val native=query.capture(project,"example.ConfirmationPage",view=WicketMcpReport.NAVIGATION)
            val structure=client.call(WicketMcpReport.STRUCTURE,className="example.ConfirmationPage")["analysis"].asJsonObject
            assertEquals(com.google.gson.JsonParser.parseString(dev.wicketlens.export.WicketContextExporter().export(native.report)),structure)
            val stored=structure["storedActionRegistrations"].asJsonObject
            assertEquals(2,stored["registrations"].asJsonArray.size());assertEquals("unknown",stored["activeRegistration"].asString)
            val contexts=client.call(WicketMcpReport.NAVIGATION,className="example.ConfirmationPage")["navigationContexts"].asJsonObject["contexts"].asJsonArray
                .filter {it.asJsonObject["relationship"].asString=="ACTION_TEMPLATE"}
            assertEquals(2,contexts.size)
            for((index,descriptor) in contexts.withIndex()) {
                val selected=client.call(WicketMcpReport.NAVIGATION,className="example.ConfirmationPage",contextId=descriptor.asJsonObject["contextId"].asString)
                val value=selected["navigationContexts"].asJsonObject["contexts"].asJsonArray.single().asJsonObject;value.remove("contextId")
                assertEquals(dev.wicketlens.mcp.WicketNavigationExport.context(native.navigationContexts!!.contexts.filter {it.report.actionTemplate!=null}[index]),value)
                assertEquals(1,value["bindings"].asJsonArray.size());assertEquals("unknown",value["actionTemplate"].asJsonObject["callbackStorage"].asString)
            }
            assertEquals(before,java.text to java.modificationStamp)
        }
    }
    fun testFluentRegistrarLinksStayDistinctThroughTheActualHost() {
        ModuleRootModificationUtil.updateModel(module) {model ->model.getModuleExtension(com.intellij.openapi.roots.LanguageLevelModuleExtension::class.java).languageLevel=com.intellij.pom.java.LanguageLevel.JDK_25}
        val java=WicketRecordActionFixture.install(myFixture,WicketRecordActionFixture.chainedPage);val before=java.text to java.modificationStamp
        Client().use {client ->
            val query=dev.wicketlens.mcp.WicketPageAnalysisRequest()
            val native=query.capture(project,"example.ConfirmationPage",view=WicketMcpReport.NAVIGATION)
            val structure=client.call(WicketMcpReport.STRUCTURE,className="example.ConfirmationPage")["analysis"].asJsonObject
            assertEquals(com.google.gson.JsonParser.parseString(dev.wicketlens.export.WicketContextExporter().export(native.report)),structure)
            val stored=structure["storedActionRegistrations"].asJsonObject
            val offsets=stored["registrations"].asJsonArray.map {it.asJsonObject["registrationLocation"].asJsonObject["offset"].asInt}
            assertEquals(2,offsets.distinct().size);assertTrue(offsets.all {java.text.substring(it).startsWith("addAction")})
            assertEquals("unknown",stored["activeRegistration"].asString)
            val contexts=client.call(WicketMcpReport.NAVIGATION,className="example.ConfirmationPage")["navigationContexts"].asJsonObject["contexts"].asJsonArray
                .filter {it.asJsonObject["relationship"].asString=="ACTION_TEMPLATE"}
            assertEquals(2,contexts.size)
            for((index,descriptor) in contexts.withIndex()) {
                val selected=client.call(WicketMcpReport.NAVIGATION,className="example.ConfirmationPage",contextId=descriptor.asJsonObject["contextId"].asString)
                val value=selected["navigationContexts"].asJsonObject["contexts"].asJsonArray.single().asJsonObject;value.remove("contextId")
                assertEquals(dev.wicketlens.mcp.WicketNavigationExport.context(native.navigationContexts!!.contexts.filter {it.report.actionTemplate!=null}[index]),value)
                assertEquals(1,value["bindings"].asJsonArray.size());assertEquals("unknown",value["actionTemplate"].asJsonObject["callbackStorage"].asString)
            }
            assertEquals(before,java.text to java.modificationStamp)
        }
    }
    fun testInitializedRegistrarLinksUseOneAllocationThroughTheActualHost() {
        ModuleRootModificationUtil.updateModel(module) {model ->model.getModuleExtension(com.intellij.openapi.roots.LanguageLevelModuleExtension::class.java).languageLevel=com.intellij.pom.java.LanguageLevel.JDK_25}
        val java=WicketRecordActionFixture.install(myFixture,WicketRecordActionFixture.initializedPage);val before=java.text to java.modificationStamp
        Client().use {client ->
            val query=dev.wicketlens.mcp.WicketPageAnalysisRequest()
            val native=query.capture(project,"example.ConfirmationPage",view=WicketMcpReport.NAVIGATION)
            val structure=client.call(WicketMcpReport.STRUCTURE,className="example.ConfirmationPage")["analysis"].asJsonObject
            assertEquals(com.google.gson.JsonParser.parseString(dev.wicketlens.export.WicketContextExporter().export(native.report)),structure)
            val stored=structure["storedActionRegistrations"].asJsonObject
            val offsets=stored["registrations"].asJsonArray.map {it.asJsonObject["registrationLocation"].asJsonObject["offset"].asInt}
            assertEquals(2,offsets.distinct().size);assertTrue(offsets.all {java.text.substring(it).startsWith("addAction")})
            assertEquals(1,native.report.storedActionRegistrations.map {it.allocationLocation}.distinct().size)
            assertEquals("unknown",stored["activeRegistration"].asString)
            val contexts=client.call(WicketMcpReport.NAVIGATION,className="example.ConfirmationPage")["navigationContexts"].asJsonObject["contexts"].asJsonArray
                .filter {it.asJsonObject["relationship"].asString=="ACTION_TEMPLATE"}
            assertEquals(2,contexts.size)
            for((index,descriptor) in contexts.withIndex()) {
                val selected=client.call(WicketMcpReport.NAVIGATION,className="example.ConfirmationPage",contextId=descriptor.asJsonObject["contextId"].asString)
                val value=selected["navigationContexts"].asJsonObject["contexts"].asJsonArray.single().asJsonObject;value.remove("contextId")
                assertEquals(dev.wicketlens.mcp.WicketNavigationExport.context(native.navigationContexts!!.contexts.filter {it.report.actionTemplate!=null}[index]),value)
                assertEquals(1,value["bindings"].asJsonArray.size());assertEquals("unknown",value["actionTemplate"].asJsonObject["callbackStorage"].asString)
            }
            assertEquals(before,java.text to java.modificationStamp)
        }
    }
    fun testOldNavigationSelectorFailsAfterAnEditThroughTheActualHost() {
        val java=WicketContentFactoryFixture.install(myFixture)
        Client().use { client ->
            val index=client.call(WicketMcpReport.NAVIGATION,className="example.FactoryPage")
            val id=index["navigationContexts"].asJsonObject["contexts"].asJsonArray.first().asJsonObject["contextId"].asString
            WriteCommandAction.runWriteCommandAction(project) {PsiDocumentManager.getInstance(project).getDocument(java)!!.setText(java.text+"\n// edited")}
            PsiDocumentManager.getInstance(project).commitAllDocuments()
            val (_,pending)=client.start(WicketMcpReport.NAVIGATION,className="example.FactoryPage",contextId=id)
            val result=parse(await(pending))["result"].asJsonObject
            assertTrue(result["isError"].asBoolean)
            val failure=JsonParser.parseString(result["content"].asJsonArray.single().asJsonObject["text"].asString).asJsonObject
            assertEquals("STALE_CONTEXT_SELECTION",failure["error"].asJsonObject["code"].asString)
            val fresh=client.call(WicketMcpReport.NAVIGATION,className="example.FactoryPage")
            val next=fresh["navigationContexts"].asJsonObject["contexts"].asJsonArray.first().asJsonObject["contextId"].asString
            client.call(WicketMcpReport.NAVIGATION,className="example.FactoryPage",contextId=next)
        }
    }
    fun testOptionalNestedNavigationContextsAreExposedByTheActualHost() {
        myFixture.addFileToProject("src/main/java/example/Outer.java", """
            package example; public class Outer {
                public static final class Fields extends org.apache.wicket.markup.html.panel.Panel {
                    public Fields(String id) { super(id); add(new org.apache.wicket.markup.html.basic.Label("child")); }
                }
            }
        """)
        myFixture.addFileToProject("src/main/resources/example/Outer\$Fields.html", "<wicket:panel><b wicket:id='child'></b></wicket:panel>")
        Client().use { client ->
            val listing = parse(await(client.post("""{"jsonrpc":"2.0","id":902,"method":"tools/list","params":{}}""")))["result"].asJsonObject["tools"].asJsonArray
            val tool = listing.single { it.asJsonObject["name"].asString == "wicket_get_page_structure" }.asJsonObject
            assertTrue(tool["annotations"].asJsonObject["readOnlyHint"].asBoolean)
            assertFalse(tool["annotations"].asJsonObject["destructiveHint"].asBoolean)
            assertTrue(tool["inputSchema"].asJsonObject["properties"].asJsonObject.has("includeNavigationContexts"))
            val ordinary = client.call(WicketMcpReport.STRUCTURE, className = "example.Outer.Fields")
            assertFalse(ordinary.has("navigationContexts"))
            val response = client.call(WicketMcpReport.STRUCTURE, className = "example.Outer.Fields", includeNavigationContexts = true)
            assertEquals(ordinary["analysis"], response["analysis"])
            assertTrue(response["navigationContexts"].asJsonObject["discoveryComplete"].asBoolean)
            val own = response["navigationContexts"].asJsonObject["contexts"].asJsonArray.single().asJsonObject
            assertEquals("OWN", own["relationship"].asString)
            assertEquals("child", own["bindings"].asJsonArray.single().asJsonObject["componentPath"].asString)
        }
    }
    fun testConditionalPopulationTemplatesAreReadOnlyThroughTheActualHost() {
        val java=WicketPopulationFixture.install(myFixture);val before=java.text to java.modificationStamp
        Client().use { client ->
            val response=client.call(WicketMcpReport.STRUCTURE,className="example.MenuPage")
            assertTrue(response.toString(),response["analysis"].asJsonObject.has("populationTemplates"))
            val contexts=response["analysis"].asJsonObject["populationTemplates"].asJsonArray
            assertEquals(2,contexts.size());assertEquals(listOf(false,true),contexts.map { it.asJsonObject["populationTemplate"].asJsonObject["conditions"].asJsonArray.single().asJsonObject["requiredResult"].asBoolean })
            val validation=client.call(WicketMcpReport.VALIDATION,className="example.MenuPage")["validation"].asJsonObject
            assertEquals(listOf(4,7),validation["populationTemplates"].asJsonArray.map { it.asJsonObject["summary"].asJsonObject["findingsByCode"].asJsonObject["MATCHED"].asInt })
            val context=client.call(WicketMcpReport.CONTEXT,className="example.MenuPage",id="sidebar:entries")["context"].asJsonObject
            assertEquals(contexts,context["populationTemplates"])
        }
        assertEquals(before,java.text to java.modificationStamp)
    }

    fun testBaseLayoutTemplateIsExplicitInAllThreeActualHostViews() {
        val java=WicketBaseLayoutFixture.install(myFixture);val before=java.text to java.modificationStamp
        Client().use { client ->
            val response=client.call(WicketMcpReport.STRUCTURE,className="example.Shell")
            val metadata=response["analysis"].asJsonObject["baseLayoutTemplate"].asJsonObject
            assertEquals("DECLARED_IMPLEMENTATIONS",metadata["dispatch"].asString);assertEquals(3,metadata["constructors"].asJsonArray.size())
            val validation=client.call(WicketMcpReport.VALIDATION,className="example.Shell")["validation"].asJsonObject
            assertEquals(metadata,validation["baseLayoutTemplate"])
            val context=client.call(WicketMcpReport.CONTEXT,className="example.Shell",id="nav:name")["context"].asJsonObject
            assertEquals(metadata,context["baseLayoutTemplate"]);assertEquals("INFERRED",context["findings"].asJsonArray.single().asJsonObject["confidence"].asString)
        }
        assertEquals(before,java.text to java.modificationStamp)
    }

    fun testLazyCompositeInputContextsAreReadOnlyThroughTheActualHost() {
        val java=WicketFormInputFixture.install(myFixture);val before=java.text to java.modificationStamp
        Client().use { client ->
            val plain=client.call(WicketMcpReport.STRUCTURE,className="example.Field")
            val response=client.call(WicketMcpReport.STRUCTURE,className="example.Field",includeNavigationContexts=true)
            assertEquals(plain["analysis"],response["analysis"])
            val form=response["navigationContexts"].asJsonObject["contexts"].asJsonArray.single { it.asJsonObject["relationship"].asString=="FORM_TEMPLATE" }.asJsonObject
            assertEquals(6,form["bindings"].asJsonArray.size())
            val metadata=form["formTemplate"].asJsonObject
            assertEquals("WRAPPER_RELATIVE",metadata["pathScope"].asString);assertEquals("unknown",metadata["validationResult"].asString)
            assertTrue(metadata["hooks"].asJsonArray.all { it.asJsonObject["execution"].asString=="not_evaluated" })
        }
        assertEquals(before,java.text to java.modificationStamp)
    }

    fun testRegisteredCompositeInputContextsAreReadOnlyThroughTheActualHost() {
        val java=WicketRegisteredFormFixture.install(myFixture);val before=java.text to java.modificationStamp
        Client().use { client ->
            val plain=client.call(WicketMcpReport.STRUCTURE,className="example.ContactPage")
            val response=client.call(WicketMcpReport.STRUCTURE,className="example.ContactPage",includeNavigationContexts=true)
            assertEquals(plain["analysis"],response["analysis"])
            val form=response["navigationContexts"].asJsonObject["contexts"].asJsonArray.single { it.asJsonObject["relationship"].asString=="FORM_TEMPLATE" }.asJsonObject
            assertEquals(6,form["bindings"].asJsonArray.size())
            val metadata=form["formTemplate"].asJsonObject
            assertEquals("example.ContactPage",metadata["registration"].asJsonObject["ownerClassName"].asString)
            assertEquals("WRAPPER_RELATIVE",metadata["pathScope"].asString);assertEquals("unknown",metadata["conversionResult"].asString)
        }
        assertEquals(before,java.text to java.modificationStamp)
    }

    fun testTreeTemplatesAreNodeRelativeAndReadOnlyThroughTheActualHost() {
        val java=WicketTreeFixture.install(myFixture);val before=java.text to java.modificationStamp
        Client().use { client ->
            val plain=client.call(WicketMcpReport.STRUCTURE,className="example.ExamplePage")
            val response=client.call(WicketMcpReport.STRUCTURE,className="example.ExamplePage",includeNavigationContexts=true)
            assertEquals(plain["analysis"],response["analysis"])
            val contexts=response["navigationContexts"].asJsonObject["contexts"].asJsonArray.filter { it.asJsonObject["relationship"].asString=="TREE_TEMPLATE" }
            assertEquals(4,contexts.size)
            contexts.forEach { context -> val tree=context.asJsonObject["treeTemplate"].asJsonObject
                assertEquals("NODE_RELATIVE",tree["pathScope"].asString);assertEquals("unknown",tree["runtimeNodeIds"].asString)
                assertEquals(8,context.asJsonObject["bindings"].asJsonArray.size())
            }
        }
        assertEquals(before,java.text to java.modificationStamp)
    }

    fun testNativeCompletionProbeDoesNotChangeTheActualHostGraph() {
        val java=WicketEventContentFixture.install(myFixture,pageSource=WicketEventContentFixture.page.replace("DetailList(\"list\")","DetailList(\"\")"))
        val before=java.text to java.modificationStamp
        Client().use { client ->
            val first=client.call(WicketMcpReport.STRUCTURE,className="example.EventPage",includeNavigationContexts=true)
            val hole=com.intellij.psi.util.PsiTreeUtil.findChildrenOfType(java,com.intellij.psi.PsiLiteralExpression::class.java).single { it.value=="" }
            assertEquals(listOf("list"),dev.wicketlens.editor.WicketIdCompletionContributor().suggestions(hole).map { it.id })
            assertFalse(dev.wicketlens.analysis.WicketCompletionAnalysis.active)
            val after=client.call(WicketMcpReport.STRUCTURE,className="example.EventPage",includeNavigationContexts=true)
            assertEquals(first["analysis"],after["analysis"]);assertEquals(first["navigationContexts"],after["navigationContexts"])
            assertEquals(before,java.text to java.modificationStamp)
        }
    }

    fun testTransparentCompletionLookupHypothesesStayOutsideActualHostResponses() {
        myFixture.addFileToProject("src/main/java/example/Html.java","package example;public class Html extends org.apache.wicket.markup.html.TransparentWebMarkupContainer { public Html(String id) { super(id); } }")
        val java=myFixture.addFileToProject("src/main/java/example/ExamplePage.java","package example;public class ExamplePage extends org.apache.wicket.markup.html.WebPage { public ExamplePage() { add(new Html(\"shell\"),new org.apache.wicket.markup.html.basic.Label(\"\")); } }")
        val html=myFixture.addFileToProject("src/main/resources/example/ExamplePage.html","<div wicket:id='shell'><b wicket:id='title'></b></div>")
        val before=listOf(java,html).map { it.text to it.modificationStamp }
        Client().use { client ->
            val plain=client.call(WicketMcpReport.STRUCTURE,className="example.ExamplePage",includeNavigationContexts=true)
            val literal=com.intellij.psi.util.PsiTreeUtil.findChildrenOfType(java,com.intellij.psi.PsiLiteralExpression::class.java).single { it.value=="" }
            assertEquals(listOf("title"),dev.wicketlens.editor.WicketIdCompletionContributor().suggestions(literal).map { it.id })
            val fresh=client.call(WicketMcpReport.STRUCTURE,className="example.ExamplePage",includeNavigationContexts=true)
            assertEquals(plain["analysis"],fresh["analysis"]);assertEquals(plain["navigationContexts"],fresh["navigationContexts"])
            assertFalse(fresh.toString().contains("completionLookupScopes"))
            assertFalse(dev.wicketlens.analysis.WicketCompletionAnalysis.active)
        }
        assertEquals(before,listOf(java,html).map { it.text to it.modificationStamp })
    }

    fun testConstructorDataLoopIsolationIsSharedAndReadOnlyThroughTheActualHost() {
        ModuleRootModificationUtil.updateModel(module) { model ->
            model.getModuleExtension(com.intellij.openapi.roots.LanguageLevelModuleExtension::class.java).languageLevel=com.intellij.pom.java.LanguageLevel.JDK_25
        }
        val java=WicketDataLoopFixture.install(myFixture);val before=java.text to java.modificationStamp
        Client().use { client ->
            val structure=client.call(WicketMcpReport.STRUCTURE,className="example.ExamplePage")["analysis"].asJsonObject
            assertEquals(structure.toString(),1,structure["matching"].asJsonObject["findings"].asJsonArray.count { it.asJsonObject["code"].asString=="MATCHED" })
            assertTrue(structure.toString().contains("collection contents remain unknown"))
            assertTrue(client.call(WicketMcpReport.VALIDATION,className="example.ExamplePage").toString().contains("collection contents remain unknown"))
            assertTrue(client.call(WicketMcpReport.CONTEXT,id="title",className="example.ExamplePage")["context"].asJsonObject["hierarchyResolved"].asBoolean)
            assertEquals(before,java.text to java.modificationStamp)
            WriteCommandAction.runWriteCommandAction(project) { PsiDocumentManager.getInstance(project).getDocument(java)!!.setText(java.text.replace("rows.add(new Row(i, String.format(\"Row %d\",i)));","removeAll();")) }
            PsiDocumentManager.getInstance(project).commitAllDocuments()
            val changed=client.call(WicketMcpReport.STRUCTURE,className="example.ExamplePage")["analysis"].asJsonObject
            assertTrue(changed.toString().contains("Constructor data loop is not isolated"))
            assertTrue(changed["matching"].asJsonObject["findings"].asJsonArray.none { it.asJsonObject["code"].asString=="MATCHED" })
        }
    }

    fun testEventContentTemplateIsSeparateAndReadOnlyThroughTheActualHost() {
        val java=WicketEventContentFixture.install(myFixture);val before=java.text to java.modificationStamp
        Client().use { client ->
            val plain=client.call(WicketMcpReport.STRUCTURE,className="example.EventPage")
            val optional=client.call(WicketMcpReport.STRUCTURE,className="example.EventPage",includeNavigationContexts=true)
            assertEquals(plain["analysis"],optional["analysis"])
            val event=optional["navigationContexts"].asJsonObject["contexts"].asJsonArray.single { it.asJsonObject["lifecycle"]?.asJsonObject?.get("directContent")!=null }.asJsonObject
            assertEquals("EVENT_TEMPLATE",event["relationship"].asString)
            assertEquals("SOURCE_DECLARED_EVENT_CONTENT",event["lifecycle"].asJsonObject["kind"].asString)
            assertEquals("rows:details",event["lifecycle"].asJsonObject["directContent"].asJsonObject["triggerComponentPath"].asString)
            assertEquals(5,event["bindings"].asJsonArray.size())
            assertEquals("unknown",event["lifecycle"].asJsonObject["directContent"].asJsonObject["openCloseState"].asString)
            val initial=client.call(WicketMcpReport.CONTEXT,id="detailsDrawer:dialog:form:content",className="example.EventPage")["context"].asJsonObject
            assertTrue(initial["component"].asJsonObject["componentType"].asString.endsWith("WebMarkupContainer"))
        }
        assertEquals(before,java.text to java.modificationStamp)
    }

    fun testNullGuardedToolbarCacheProtocolIsSharedReadOnlyThroughTheActualHost() {
        ModuleRootModificationUtil.updateModel(module) {model ->model.getModuleExtension(com.intellij.openapi.roots.LanguageLevelModuleExtension::class.java).languageLevel=com.intellij.pom.java.LanguageLevel.JDK_25}
        val java=WicketToolbarCacheFixture.install(myFixture);val before=java.text to java.modificationStamp
        Client().use {client ->
            val listing=parse(await(client.post("""{"jsonrpc":"2.0","id":903,"method":"tools/list","params":{}}""")))["result"].asJsonObject["tools"].asJsonArray
            for(name in listOf("wicket_get_page_structure","wicket_validate_page","wicket_get_component_context")) {
                val description=listing.single {it.asJsonObject["name"].asString==name}.asJsonObject["description"].asString
                assertTrue(description.contains("Source null-guarded owned toolbar caches"))
                assertTrue(description.contains("same-table registration"))
                assertFalse(description.contains("navigation toolbar construction remain unreviewed"))
            }
            val structure=client.call(WicketMcpReport.STRUCTURE,className="example.TablePage")["analysis"].asJsonObject
            assertTrue(structure["warnings"].asJsonArray.any {"Reviewed source null-guarded owned toolbar cache" in it.asJsonObject["message"].asString})
            assertTrue(client.call(WicketMcpReport.VALIDATION,className="example.TablePage").toString().contains("Reviewed source null-guarded owned toolbar cache"))
            val context=client.call(WicketMcpReport.CONTEXT,id="shell",className="example.TablePage")["context"].asJsonObject
            assertEquals(structure["warnings"],context["warnings"])
            assertEquals("shell",context["component"].asJsonObject["wicketId"].asString)
            assertEquals(before,java.text to java.modificationStamp)
        }
    }
    fun testColumnsGetterAndSizeContractAreSharedReadOnlyThroughTheActualHost() {
        ModuleRootModificationUtil.updateModel(module) {model ->model.getModuleExtension(com.intellij.openapi.roots.LanguageLevelModuleExtension::class.java).languageLevel=com.intellij.pom.java.LanguageLevel.JDK_25}
        val java=WicketTableColumnsReadFixture.install(myFixture);val before=java.text to java.modificationStamp
        Client().use {client ->
            val listing=parse(await(client.post("""{"jsonrpc":"2.0","id":903,"method":"tools/list","params":{}}""")))["result"].asJsonObject["tools"].asJsonArray
            for(name in listOf("wicket_get_page_structure","wicket_validate_page","wicket_get_component_context")) {
                val description=listing.single {it.asJsonObject["name"].asString==name}.asJsonObject["description"].asString
                assertTrue(description.contains("Owned DataTable columns reads"))
                assertTrue(description.contains("exact JDK List.of provenance"))
                assertFalse(description.contains("navigation toolbar construction remain unreviewed"))
            }
            val structure=client.call(WicketMcpReport.STRUCTURE,className="example.TablePage")["analysis"].asJsonObject
            assertTrue(structure["warnings"].asJsonArray.any {"Reviewed owned DataTable columns getter" in it.asJsonObject["message"].asString})
            assertTrue(client.call(WicketMcpReport.VALIDATION,className="example.TablePage").toString().contains("Reviewed owned DataTable columns getter"))
            val context=client.call(WicketMcpReport.CONTEXT,id="shell",className="example.TablePage")["context"].asJsonObject
            assertEquals(structure["warnings"],context["warnings"])
            assertEquals("shell",context["component"].asJsonObject["wicketId"].asString)
            assertEquals(before,java.text to java.modificationStamp)
        }
    }
    fun testOwnedTableForwardingAndDescriptionsAreSharedReadOnlyThroughTheActualHost() {
        ModuleRootModificationUtil.updateModel(module) {model ->model.getModuleExtension(com.intellij.openapi.roots.LanguageLevelModuleExtension::class.java).languageLevel=com.intellij.pom.java.LanguageLevel.JDK_25}
        val java=WicketOwnedTableForwardingFixture.install(myFixture);val before=java.text to java.modificationStamp
        Client().use {client ->
            val listing=parse(await(client.post("""{"jsonrpc":"2.0","id":903,"method":"tools/list","params":{}}""")))["result"].asJsonObject["tools"].asJsonArray
            for(name in listOf("wicket_get_page_structure","wicket_validate_page","wicket_get_component_context")) {
                val description=listing.single {it.asJsonObject["name"].asString==name}.asJsonObject["description"].asString
                assertTrue(description.contains("Owned DataTable reference forwarding"))
                assertTrue(description.contains("Remaining source constructor effects are independently audited"))
                assertFalse(description.contains("navigation toolbar construction remain unreviewed"))
            }
            val structure=client.call(WicketMcpReport.STRUCTURE,className="example.TablePage")["analysis"].asJsonObject
            assertTrue(structure["warnings"].asJsonArray.any {"Reviewed owned DataTable reference forwarding" in it.asJsonObject["message"].asString})
            assertTrue(client.call(WicketMcpReport.VALIDATION,className="example.TablePage").toString().contains("Reviewed owned DataTable reference forwarding"))
            val context=client.call(WicketMcpReport.CONTEXT,id="shell",className="example.TablePage")["context"].asJsonObject
            assertEquals(structure["warnings"],context["warnings"])
            assertEquals("shell",context["component"].asJsonObject["wicketId"].asString)
            assertEquals(before,java.text to java.modificationStamp)
        }
    }
    fun testFluentStorageFactAndDescriptionsAreSharedReadOnlyThroughTheActualHost() {
        ModuleRootModificationUtil.updateModel(module) {model ->model.getModuleExtension(com.intellij.openapi.roots.LanguageLevelModuleExtension::class.java).languageLevel=com.intellij.pom.java.LanguageLevel.JDK_25}
        val java=WicketFluentConfigurationFixture.install(myFixture);val before=java.text to java.modificationStamp
        Client().use {client ->
            val listing=parse(await(client.post("""{"jsonrpc":"2.0","id":903,"method":"tools/list","params":{}}""")))["result"].asJsonObject["tools"].asJsonArray
            for(name in listOf("wicket_get_page_structure","wicket_validate_page","wicket_get_component_context")) {
                val description=listing.single {it.asJsonObject["name"].asString==name}.asJsonObject["description"].asString
                assertTrue(description.contains("Fresh inline source component fluent configuration"))
                assertTrue(description.contains("exact private Model storage"))
                assertFalse(description.contains("navigation toolbar construction remain unreviewed"))
            }
            val structure=client.call(WicketMcpReport.STRUCTURE,className="example.TablePage")["analysis"].asJsonObject
            assertTrue(structure["warnings"].asJsonArray.any {"Reviewed fresh inline component fluent configuration" in it.asJsonObject["message"].asString})
            assertTrue(client.call(WicketMcpReport.VALIDATION,className="example.TablePage").toString().contains("Reviewed fresh inline component fluent configuration"))
            val context=client.call(WicketMcpReport.CONTEXT,id="shell",className="example.TablePage")["context"].asJsonObject
            assertEquals(structure["warnings"],context["warnings"])
            assertEquals("shell",context["component"].asJsonObject["wicketId"].asString)
            assertEquals(before,java.text to java.modificationStamp)
        }
    }
    fun testOwnedAccessorFactAndDescriptionsAreSharedReadOnlyThroughTheActualHost() {
        ModuleRootModificationUtil.updateModel(module) {model ->model.getModuleExtension(com.intellij.openapi.roots.LanguageLevelModuleExtension::class.java).languageLevel=com.intellij.pom.java.LanguageLevel.JDK_25}
        val java=WicketOwnedAccessorFixture.install(myFixture);val before=java.text to java.modificationStamp
        Client().use {client ->
            val listing=parse(await(client.post("""{"jsonrpc":"2.0","id":903,"method":"tools/list","params":{}}""")))["result"].asJsonObject["tools"].asJsonArray
            for(name in listOf("wicket_get_page_structure","wicket_validate_page","wicket_get_component_context")) {
                val description=listing.single {it.asJsonObject["name"].asString==name}.asJsonObject["description"].asString
                assertTrue(description.contains("Owned component accessor decoration"))
                assertTrue(description.contains("private-final framework child"))
                assertFalse(description.contains("navigation toolbar construction remain unreviewed"))
            }
            val structure=client.call(WicketMcpReport.STRUCTURE,className="example.TablePage")["analysis"].asJsonObject
            assertTrue(structure["warnings"].asJsonArray.any {"Reviewed owned component accessor behavior decoration" in it.asJsonObject["message"].asString})
            assertTrue(client.call(WicketMcpReport.VALIDATION,className="example.TablePage").toString().contains("Reviewed owned component accessor behavior decoration"))
            val context=client.call(WicketMcpReport.CONTEXT,id="shell",className="example.TablePage")["context"].asJsonObject
            assertEquals(structure["warnings"],context["warnings"])
            assertEquals("shell",context["component"].asJsonObject["wicketId"].asString)
            assertEquals(before,java.text to java.modificationStamp)
        }
    }
    fun testNavigationFactoryIsSharedReadOnlyThroughTheActualHost() {
        ModuleRootModificationUtil.updateModel(module) {model ->model.getModuleExtension(com.intellij.openapi.roots.LanguageLevelModuleExtension::class.java).languageLevel=com.intellij.pom.java.LanguageLevel.JDK_25}
        val java=WicketNavigationToolbarFixture.install(myFixture);val before=java.text to java.modificationStamp
        Client().use {client ->
            val listing=parse(await(client.post("""{"jsonrpc":"2.0","id":903,"method":"tools/list","params":{}}""")))["result"].asJsonObject["tools"].asJsonArray
            for(name in listOf("wicket_get_page_structure","wicket_validate_page","wicket_get_component_context")) {
                val description=listing.single {it.asJsonObject["name"].asString==name}.asJsonObject["description"].asString
                assertTrue(description.contains("NavigationToolbar construction also audits its eager newPagingNavigator factory"))
                assertTrue(description.contains("Navigation wrappers may additionally set a literal output flag"))
                assertFalse(description.contains("navigation toolbar construction remain unreviewed"))
            }
            val structure=client.call(WicketMcpReport.STRUCTURE,className="example.TablePage")["analysis"].asJsonObject
            assertTrue(structure["warnings"].asJsonArray.any {"Reviewed fresh-table navigation-toolbar construction" in it.asJsonObject["message"].asString})
            assertTrue(client.call(WicketMcpReport.VALIDATION,className="example.TablePage").toString().contains("Reviewed fresh-table navigation-toolbar construction"))
            val context=client.call(WicketMcpReport.CONTEXT,id="shell",className="example.TablePage")["context"].asJsonObject
            assertEquals(structure["warnings"],context["warnings"])
            assertEquals("shell",context["component"].asJsonObject["wicketId"].asString)
            assertEquals(before,java.text to java.modificationStamp)
        }
    }
    fun testToolbarRegistrationIsSharedReadOnlyThroughTheActualHost() {
        ModuleRootModificationUtil.updateModel(module) {model ->model.getModuleExtension(com.intellij.openapi.roots.LanguageLevelModuleExtension::class.java).languageLevel=com.intellij.pom.java.LanguageLevel.JDK_25}
        val java=WicketToolbarRegistrationFixture.install(myFixture);val before=java.text to java.modificationStamp
        Client().use {client ->
            val structure=client.call(WicketMcpReport.STRUCTURE,className="example.TablePage")["analysis"].asJsonObject
            assertTrue(structure["warnings"].asJsonArray.any {"Reviewed owned DataTable toolbar registration" in it.asJsonObject["message"].asString})
            assertTrue(client.call(WicketMcpReport.VALIDATION,className="example.TablePage").toString().contains("Reviewed owned DataTable toolbar registration"))
            val context=client.call(WicketMcpReport.CONTEXT,id="shell",className="example.TablePage")["context"].asJsonObject
            assertEquals(structure["warnings"],context["warnings"])
            assertEquals("shell",context["component"].asJsonObject["wicketId"].asString)
            assertEquals(before,java.text to java.modificationStamp)
        }
    }
    fun testDataTableConstructionIsSharedReadOnlyThroughTheActualHost() {
        ModuleRootModificationUtil.updateModel(module) {model ->model.getModuleExtension(com.intellij.openapi.roots.LanguageLevelModuleExtension::class.java).languageLevel=com.intellij.pom.java.LanguageLevel.JDK_25}
        val java=WicketDataTableFixture.install(myFixture);val before=java.text to java.modificationStamp
        Client().use {client ->
            val structure=client.call(WicketMcpReport.STRUCTURE,className="example.TablePage")["analysis"].asJsonObject
            assertTrue(structure["warnings"].asJsonArray.any {"Reviewed DataTable constructor" in it.asJsonObject["message"].asString})
            assertTrue(client.call(WicketMcpReport.VALIDATION,className="example.TablePage").toString().contains("Reviewed DataTable constructor"))
            val context=client.call(WicketMcpReport.CONTEXT,id="shell",className="example.TablePage")["context"].asJsonObject
            assertEquals(structure["warnings"],context["warnings"])
            assertEquals("shell",context["component"].asJsonObject["wicketId"].asString)
            assertEquals(before,java.text to java.modificationStamp)
        }
    }
    fun testProviderConstructionIsSharedReadOnlyThroughTheActualHost() {
        ModuleRootModificationUtil.updateModel(module) {model ->model.getModuleExtension(com.intellij.openapi.roots.LanguageLevelModuleExtension::class.java).languageLevel=com.intellij.pom.java.LanguageLevel.JDK_25}
        val java=WicketProviderFixture.install(myFixture);val before=java.text to java.modificationStamp
        Client().use {client ->
            val structure=client.call(WicketMcpReport.STRUCTURE,className="example.ProviderPage")["analysis"].asJsonObject
            assertTrue(structure["warnings"].asJsonArray.any {"Reviewed source data-provider construction" in it.asJsonObject["message"].asString})
            assertTrue(client.call(WicketMcpReport.VALIDATION,className="example.ProviderPage").toString().contains("Reviewed source data-provider construction"))
            val context=client.call(WicketMcpReport.CONTEXT,id="title",className="example.ProviderPage")["context"].asJsonObject
            assertEquals(structure["warnings"],context["warnings"])
            assertEquals("title",context["component"].asJsonObject["wicketId"].asString)
            assertEquals(before,java.text to java.modificationStamp)
        }
    }

    fun testDateColumnEnumStorageIsSharedReadOnlyThroughTheActualHost() {
        ModuleRootModificationUtil.updateModel(module) {model ->model.getModuleExtension(com.intellij.openapi.roots.LanguageLevelModuleExtension::class.java).languageLevel=com.intellij.pom.java.LanguageLevel.JDK_25}
        val java=WicketDateColumnFixture.install(myFixture);val before=java.text to java.modificationStamp
        Client().use {client ->
            val structure=client.call(WicketMcpReport.STRUCTURE,className="example.DatePage")["analysis"].asJsonObject
            assertEquals(2,structure["warnings"].asJsonArray.count {"Reviewed JDK FormatStyle enum storage" in it.asJsonObject["message"].asString})
            assertTrue(client.call(WicketMcpReport.VALIDATION,className="example.DatePage").toString().contains("Reviewed JDK FormatStyle enum storage"))
            val context=client.call(WicketMcpReport.CONTEXT,id="title",className="example.DatePage")["context"].asJsonObject
            assertEquals(structure["warnings"],context["warnings"])
            assertEquals("title",context["component"].asJsonObject["wicketId"].asString)
            assertEquals(before,java.text to java.modificationStamp)
        }
    }

    fun testLambdaColumnStorageIsSharedReadOnlyThroughTheActualHost() {
        ModuleRootModificationUtil.updateModel(module) {model ->model.getModuleExtension(com.intellij.openapi.roots.LanguageLevelModuleExtension::class.java).languageLevel=com.intellij.pom.java.LanguageLevel.JDK_25}
        val java=WicketLambdaColumnFixture.install(myFixture);val before=java.text to java.modificationStamp
        Client().use {client ->
            val structure=client.call(WicketMcpReport.STRUCTURE,className="example.LambdaPage")["analysis"].asJsonObject
            assertTrue(structure["warnings"].asJsonArray.any {"Reviewed LambdaColumn constructor storage" in it.asJsonObject["message"].asString})
            assertTrue(client.call(WicketMcpReport.VALIDATION,className="example.LambdaPage").toString().contains("Reviewed LambdaColumn constructor storage"))
            val context=client.call(WicketMcpReport.CONTEXT,id="title",className="example.LambdaPage")["context"].asJsonObject
            assertEquals(structure["warnings"],context["warnings"])
            assertEquals("title",context["component"].asJsonObject["wicketId"].asString)
            assertEquals(before,java.text to java.modificationStamp)
        }
    }

    fun testColumnConstructorStorageAndDeferredCallbacksAreSharedThroughTheActualHost() {
        ModuleRootModificationUtil.updateModel(module) {model ->model.getModuleExtension(com.intellij.openapi.roots.LanguageLevelModuleExtension::class.java).languageLevel=com.intellij.pom.java.LanguageLevel.JDK_25}
        val java=WicketColumnConstructionFixture.install(myFixture);val before=java.text to java.modificationStamp
        Client().use {client ->
            val structure=client.call(WicketMcpReport.STRUCTURE,className="example.ColumnPage")["analysis"].asJsonObject
            assertTrue(structure["warnings"].asJsonArray.any {"Reviewed AbstractColumn constructor storage" in it.asJsonObject["message"].asString})
            assertTrue(client.call(WicketMcpReport.VALIDATION,className="example.ColumnPage").toString().contains("Reviewed AbstractColumn constructor storage"))
            val context=client.call(WicketMcpReport.CONTEXT,id="title",className="example.ColumnPage")["context"].asJsonObject
            assertEquals(structure["warnings"],context["warnings"])
            assertEquals("title",context["component"].asJsonObject["wicketId"].asString)
            assertEquals(before,java.text to java.modificationStamp)
        }
    }

    fun testConstructorOwnedConfirmationContentIsSharedThroughTheActualHost() {
        ModuleRootModificationUtil.updateModel(module) { model ->
            model.getModuleExtension(com.intellij.openapi.roots.LanguageLevelModuleExtension::class.java).languageLevel=com.intellij.pom.java.LanguageLevel.JDK_25
        }
        val java=WicketConfirmContentFixture.install(myFixture);val before=java.text to java.modificationStamp
        Client().use {client ->
            val structure=client.call(WicketMcpReport.STRUCTURE,className="example.ConfirmPage")["analysis"].asJsonObject
            val setup=structure["initialContentSetup"].asJsonObject
            assertEquals("INITIAL_DECLARATIONS",setup["phase"].asString)
            assertEquals(1,setup["hosts"].asJsonArray.single().asJsonObject["placementLocations"].asJsonArray.size())
            assertEquals(setup,client.call(WicketMcpReport.VALIDATION,className="example.ConfirmPage")["validation"].asJsonObject["initialContentSetup"])
            val context=client.call(WicketMcpReport.CONTEXT,id="confirmBox:dialog:form:content",className="example.ConfirmPage")["context"].asJsonObject
            assertEquals(setup,context["initialContentSetup"])
            assertEquals("org.apache.wicket.markup.html.basic.Label",context["component"].asJsonObject["componentType"].asString)
            assertEquals("INFERRED",context["findings"].asJsonArray.single().asJsonObject["confidence"].asString)
            assertEquals(before,java.text to java.modificationStamp)
        }
    }

    fun testInitialContentDeclarationsAreSharedAndReadOnlyThroughTheActualHost() {
        val java=WicketInitialContentFixture.install(myFixture);val before=java.text to java.modificationStamp
        Client().use { client ->
            val structure=client.call(WicketMcpReport.STRUCTURE,className="example.ContentPage")["analysis"].asJsonObject
            val setup=structure["initialContentSetup"].asJsonObject
            assertEquals("INITIAL_DECLARATIONS",setup["phase"].asString)
            assertEquals("unknown",setup["constructorEffects"].asString)
            assertEquals(setup,client.call(WicketMcpReport.VALIDATION,className="example.ContentPage")["validation"].asJsonObject["initialContentSetup"])
            val context=client.call(WicketMcpReport.CONTEXT,id="drawer:dialog:form:content:name",className="example.ContentPage")["context"].asJsonObject
            assertEquals(setup,context["initialContentSetup"])
            assertEquals("INFERRED",context["findings"].asJsonArray.single().asJsonObject["confidence"].asString)
        }
        assertEquals(before,java.text to java.modificationStamp)
    }

    fun testSubmissionDeclarationsAreSeparateAndReadOnlyThroughTheActualHost() {
        val java=WicketSubmissionFixture.install(myFixture);val before=java.text to java.modificationStamp
        Client().use { client ->
            val plain=client.call(WicketMcpReport.STRUCTURE,className="example.SubmitPage")
            val optional=client.call(WicketMcpReport.STRUCTURE,className="example.SubmitPage",includeNavigationContexts=true)
            assertEquals(plain["analysis"],optional["analysis"])
            val submissions=optional["navigationContexts"].asJsonObject["contexts"].asJsonArray.filter { it.asJsonObject["relationship"].asString=="SUBMISSION_TEMPLATE" }
            assertEquals(2,submissions.size)
            val save=submissions.single { it.asJsonObject["submissionTemplate"].asJsonObject["hostComponentPath"].asString=="save" }.asJsonObject
            val metadata=save["submissionTemplate"].asJsonObject
            assertEquals("save:dialog:form:confirm",metadata["triggerComponentPath"].asString)
            assertEquals("onConfirm",metadata["callbackName"].asString)
            for(key in listOf("validationResult","defaultFormProcessing","feedbackOutcome","businessOutcome","laterTreeState")) assertEquals(key,"unknown",metadata[key].asString)
            assertEquals("people",metadata["effects"].asJsonArray.single { it.asJsonObject["kind"].asString=="AJAX_REFRESH" }.asJsonObject["targetComponentPath"].asString)
            assertEquals(5,save["bindings"].asJsonArray.size())
            assertTrue(save["bindings"].asJsonArray.all { it.asJsonObject["findings"].asJsonArray.single().asJsonObject["confidence"].asString=="INFERRED" })
            assertFalse(client.call(WicketMcpReport.VALIDATION,className="example.SubmitPage")["validation"].asJsonObject.has("submissionTemplate"))
        }
        assertEquals(before,java.text to java.modificationStamp)
    }

    fun testOrdinaryFormSubmitAndErrorDeclarationsAreSeparateThroughTheActualHost() {
        val java=WicketFormSubmissionFixture.install(myFixture);val before=java.text to java.modificationStamp
        Client().use { client ->
            val plain=client.call(WicketMcpReport.STRUCTURE,className="example.FormPage")
            val optional=client.call(WicketMcpReport.STRUCTURE,className="example.FormPage",includeNavigationContexts=true)
            assertEquals(plain["analysis"],optional["analysis"])
            val contexts=optional["navigationContexts"].asJsonObject["contexts"].asJsonArray.filter { it.asJsonObject["submissionTemplate"]?.asJsonObject?.has("formCallback")==true }
            assertEquals(2,contexts.size)
            assertEquals(setOf("SOURCE_ON_SUBMIT_ROUTE","SOURCE_ON_ERROR_ROUTE"),contexts.map { it.asJsonObject["submissionTemplate"].asJsonObject["dispatchCondition"].asString }.toSet())
            contexts.forEach { entry -> val metadata=entry.asJsonObject["submissionTemplate"].asJsonObject
                assertEquals("SOURCE_DECLARED_FORM_CALLBACK",metadata["kind"].asString)
                assertEquals("OWN_COMPANION_DECLARATIONS",metadata["pathScope"].asString)
                assertEquals("invoiceForm:submit",metadata["triggerComponentPath"].asString)
                assertEquals("unknown",metadata["formCallback"].asJsonObject["initialHierarchy"].asString)
                assertEquals("unknown",metadata["validationResult"].asString);assertEquals("unknown",metadata["businessOutcome"].asString)
                assertEquals("not_evaluated",metadata["callbackExecution"].asString)
                assertTrue(metadata["bridgeLocation"].isJsonNull)
            }
            assertFalse(client.call(WicketMcpReport.VALIDATION,className="example.FormPage")["validation"].asJsonObject.has("submissionTemplate"))
        }
        assertEquals(before,java.text to java.modificationStamp)
    }

    fun testRegisteredItemCallbacksAreSharedAndReadOnlyThroughTheActualHost() {
        val java=WicketRegisteredListFixture.install(myFixture);val before=java.text to java.modificationStamp
        Client().use { client ->
            val structure=client.call(WicketMcpReport.STRUCTURE,className="example.CallbackPage")
            val populations=structure["analysis"].asJsonObject["populationTemplates"].asJsonArray
            assertEquals(1,populations.size())
            val registration=populations.single().asJsonObject["populationTemplate"].asJsonObject["registration"].asJsonObject
            assertEquals("SOURCE_DECLARED_LIST_CALLBACK",registration["kind"].asString)
            assertEquals("not_evaluated",registration["callbackExecution"].asString)
            val validation=client.call(WicketMcpReport.VALIDATION,className="example.CallbackPage")
            assertEquals(2,validation["validation"].asJsonObject["populationTemplates"].asJsonArray.single().asJsonObject["summary"].asJsonObject["findingsByCode"].asJsonObject["MATCHED"].asInt)
            assertEquals(populations,client.call(WicketMcpReport.CONTEXT,id="sections",className="example.CallbackPage")["context"].asJsonObject["populationTemplates"])
        }
        assertEquals(before,java.text to java.modificationStamp)
    }

    fun testLazyTableToolbarIsReadOnlyAndRelativeThroughTheActualHost() {
        val java=WicketTableToolbarFixture.install(myFixture);val before=java.text to java.modificationStamp
        Client().use { client ->
            val plain=client.call(WicketMcpReport.STRUCTURE,className="example.EmptyPage")
            val response=client.call(WicketMcpReport.STRUCTURE,className="example.EmptyPage",includeNavigationContexts=true)
            assertEquals(plain["analysis"],response["analysis"])
            val template=response["navigationContexts"].asJsonObject["contexts"].asJsonArray.single { it.asJsonObject["relationship"].asString=="TABLE_TOOLBAR_TEMPLATE" }.asJsonObject
            val toolbar=template["toolbarTemplate"].asJsonObject
            assertEquals("TOOLBAR_RELATIVE",toolbar["pathScope"].asString)
            assertEquals("unknown",toolbar["runtimeToolbarId"].asString)
            assertEquals("unknown",toolbar["visibility"].asString)
            assertEquals("not_evaluated",toolbar["factoryExecution"].asString)
            assertEquals(4,template["bindings"].asJsonArray.size())
            assertFalse(client.call(WicketMcpReport.VALIDATION,className="example.EmptyPage").has("navigationContexts"))
            assertFalse(client.call(WicketMcpReport.CONTEXT,id="",className="example.EmptyPage").has("navigationContexts"))
        }
        assertEquals(before,java.text to java.modificationStamp)
    }

    fun testImmediateFactoryTemplateRemainsSeparateAndReadOnlyThroughTheActualHost() {
        val java=WicketContentFactoryFixture.install(myFixture); val before=java.text to java.modificationStamp
        Client().use { client ->
            val plain=client.call(WicketMcpReport.STRUCTURE,className="example.FactoryPage")
            val response=client.call(WicketMcpReport.STRUCTURE,className="example.FactoryPage",includeNavigationContexts=true)
            assertEquals(plain["analysis"],response["analysis"])
            val template=response["navigationContexts"].asJsonObject["contexts"].asJsonArray.single { it.asJsonObject["relationship"].asString=="FACTORY_TEMPLATE" }.asJsonObject
            val lifecycle=template["lifecycle"].asJsonObject
            assertEquals("SOURCE_DECLARED_CONTENT_FACTORY",lifecycle["kind"].asString)
            assertEquals("REGISTRATION_TEMPLATE",lifecycle["phase"].asString)
            assertEquals("not_evaluated",lifecycle["contentFactory"].asJsonObject["factoryExecution"].asString)
            assertEquals("not_evaluated",lifecycle["contentFactory"].asJsonObject["callbackExecution"].asString)
            assertEquals(4,template["bindings"].asJsonArray.size())
            assertFalse(plain.has("navigationContexts"))
            assertFalse(client.call(WicketMcpReport.VALIDATION,className="example.FactoryPage").has("navigationContexts"))
            assertFalse(client.call(WicketMcpReport.CONTEXT,id="",className="example.FactoryPage").has("navigationContexts"))
        }
        assertEquals(before,java.text to java.modificationStamp)
    }

    fun testSelectionTemplatesRemainSeparateThroughTheActualHost() {
        val java=WicketSelectionFixture.install(myFixture); val before=java.text to java.modificationStamp
        Client().use { client ->
            val plain=client.call(WicketMcpReport.STRUCTURE,className="example.ExamplePage")
            val response=client.call(WicketMcpReport.STRUCTURE,className="example.ExamplePage",includeNavigationContexts=true)
            assertEquals(plain["analysis"],response["analysis"])
            val contexts=response["navigationContexts"].asJsonObject["contexts"].asJsonArray.filter { it.asJsonObject["relationship"].asString=="SELECTION_TEMPLATE" }
            assertEquals(2,contexts.size)
            assertEquals(listOf("first","second"),contexts.map { it.asJsonObject["lifecycle"].asJsonObject["selection"].asJsonObject["definitionId"].asString })
            assertTrue(contexts.all { it.asJsonObject["lifecycle"].asJsonObject["runtimePresence"].asString=="unknown" })
        }
        assertEquals(before,java.text to java.modificationStamp)
    }

    fun testReplacementEventTemplatesAreReadOnlyAndSeparateThroughTheActualHost() {
        myFixture.addFileToProject("src/main/java/example/EventPage.java", """
            package example; public class EventPage extends org.apache.wicket.markup.html.WebPage {
                public EventPage() { Host host=new Host("host").setFields(Fields::new); add(host); }
                public static final class Fields extends org.apache.wicket.markup.html.panel.Panel {
                    public Fields(String id, org.apache.wicket.model.IModel<String> model) { super(id,model); add(new org.apache.wicket.markup.html.basic.Label("name")); }
                }
                public static class Host extends org.apache.wicket.markup.html.panel.Panel {
                    private final org.apache.wicket.markup.html.form.Form<Void> form;
                    private java.util.function.BiFunction<String,org.apache.wicket.model.IModel<String>,? extends org.apache.wicket.Component> factory;
                    public Host(String id) { super(id); form=new org.apache.wicket.markup.html.form.Form<>("form"); add(form); form.add(new org.apache.wicket.markup.html.WebMarkupContainer("body")); }
                    public Host setFields(java.util.function.BiFunction<String,org.apache.wicket.model.IModel<String>,? extends org.apache.wicket.Component> factory) { this.factory=factory; return this; }
                    public void edit(org.apache.wicket.model.IModel<String> model) { org.apache.wicket.Component content=factory.apply("body",model); open(content); }
                    private void open(org.apache.wicket.Component content) { form.addOrReplace(content); }
                }
            }
        """)
        myFixture.addFileToProject("src/main/resources/example/EventPage.html", "<div wicket:id='host'></div>")
        myFixture.addFileToProject("src/main/resources/example/EventPage\$Host.html", "<wicket:panel><form wicket:id='form'><div wicket:id='body'></div></form></wicket:panel>")
        myFixture.addFileToProject("src/main/resources/example/EventPage\$Fields.html", "<wicket:panel><b wicket:id='name'></b></wicket:panel>")
        Client().use { client ->
            val plain = client.call(WicketMcpReport.STRUCTURE, className = "example.EventPage")
            val response = client.call(WicketMcpReport.STRUCTURE, className = "example.EventPage", includeNavigationContexts = true)
            assertEquals(plain["analysis"], response["analysis"])
            val event = response["navigationContexts"].asJsonObject["contexts"].asJsonArray.single { it.asJsonObject["relationship"].asString == "EVENT_TEMPLATE" }.asJsonObject
            assertEquals("host:form:body", event["lifecycle"].asJsonObject["componentPath"].asString)
            assertEquals("unknown", event["lifecycle"].asJsonObject["runtimePresence"].asString)
            assertTrue(event["bindings"].asJsonArray.all { it.asJsonObject["findings"].asJsonArray.single().asJsonObject["confidence"].asString == "INFERRED" })
        }
    }

    fun testWireCancellationIsAcceptedButHostWorkNeedsItsDeadlineAndAReconnection() {
        Client().use { client ->
            val token = DumbModeTestUtils.startEternalDumbModeTask(project)
            try {
                val requests = WicketMcpReport.entries.map { it to client.start(it) }
                val control = client.start(WicketMcpReport.STRUCTURE)
                waitFor("Semantic requests entered indexing wait") { activeRequests() == WicketMcpReport.entries.size + 1 }
                requests.forEach { (_, pending) -> assertFalse(pending.second.isDone); client.cancel(pending.first) }
                val observed = CompletableFuture<Unit>()
                CompletableFuture.delayedExecutor(1, java.util.concurrent.TimeUnit.SECONDS).execute { observed.complete(Unit) }
                await(observed)
                assertEquals("Cancellation notifications have not stopped the tool jobs", WicketMcpReport.entries.size + 1, activeRequests())
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
                    assertFalse(result.toString(), result["isError"]?.asBoolean == true)
                    val value = JsonParser.parseString(result["content"].asJsonArray.single().asJsonObject["text"].asString).asJsonObject
                    val data = when (view) {
                        WicketMcpReport.NAVIGATION -> if(value["navigationContexts"].asJsonObject["contexts"].asJsonArray.first().asJsonObject["javaLocation"].asJsonObject["file"].asString.startsWith(other.tempDirPath)) "second" else "first"
                        WicketMcpReport.STRUCTURE -> value["analysis"].asJsonObject["components"].asJsonArray.filter { !it.asJsonObject["wicketId"].isJsonNull }.single().asJsonObject["wicketId"].asString
                        WicketMcpReport.CONTEXT -> value["context"].asJsonObject["componentPath"].asString
                        WicketMcpReport.VALIDATION -> if (value["validation"].asJsonObject["findings"].asJsonArray.single().asJsonObject["javaLocation"].asJsonObject["file"].asString.startsWith(other.tempDirPath)) "second" else "first"
                        WicketMcpReport.DEPENDENCIES -> value["dependencies"].asJsonObject["owners"].asJsonArray.single().asJsonObject["fields"].asJsonArray.first().asJsonObject["beanName"].asString
                        WicketMcpReport.TEST_PLAN -> if (value["testPlan"].asJsonObject["manualBindings"].toString().contains("dependencysecond")) "second" else "first"
                        WicketMcpReport.SPRING_TEST_PLAN -> if (value["springTestPlan"].asJsonObject["manualBindings"].toString().contains("dependencysecond")) "second" else "first"
                        WicketMcpReport.SPRING_INSPECTIONS -> if (value["springInspections"].asJsonObject["findings"].asJsonArray.first().asJsonObject["location"].asJsonObject["file"].asString.startsWith(other.tempDirPath)) "second" else "first"
                    }
                    assertEquals(expected, data)
                }
                val ownIndex=client.call(WicketMcpReport.NAVIGATION)
                val ownId=ownIndex["navigationContexts"].asJsonObject["contexts"].asJsonArray.first().asJsonObject["contextId"].asString
                val (_,foreignPending)=client.start(WicketMcpReport.NAVIGATION,path=other.project.basePath!!,contextId=ownId)
                val foreignResult=parse(await(foreignPending))["result"].asJsonObject
                assertTrue(foreignResult["isError"].asBoolean)
                assertEquals("STALE_CONTEXT_SELECTION",JsonParser.parseString(foreignResult["content"].asJsonArray.single().asJsonObject["text"].asString).asJsonObject["error"].asJsonObject["code"].asString)
                for ((path, expected) in listOf(project.basePath!! to "first", other.project.basePath!! to "second")) {
                    val contexts = client.call(WicketMcpReport.STRUCTURE, path, includeNavigationContexts = true)["navigationContexts"].asJsonObject
                    assertEquals(expected, contexts["contexts"].asJsonArray.single().asJsonObject["bindings"].asJsonArray.single().asJsonObject["componentPath"].asString)
                    val inventory = client.call(WicketMcpReport.DEPENDENCIES, path, className = null)["dependencies"].asJsonObject
                    assertEquals(setOf(expected, "&$expected"), inventory["fields"].asJsonArray.map { it.asJsonObject["beanName"].asString }.toSet())
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
                mountedApplication(myFixture, "/first"); mountedApplication(other, "/second")
                for ((path, expected) in listOf(project.basePath!! to "/first", other.project.basePath!! to "/second")) {
                    val routes = client.routes(path)["routes"].asJsonObject["applications"].asJsonArray
                    assertEquals(1, routes.size())
                    assertEquals(expected, routes.single().asJsonObject["mounts"].asJsonArray.single().asJsonObject["path"].asString)
                }
                beanMountedApplication(myFixture, "/bean-first")
                beanMountedApplication(other, "/bean-second")
                val selector = "example.BeanRoutes#application"
                for ((path, expected) in listOf(project.basePath!! to "/bean-first", other.project.basePath!! to "/bean-second")) {
                    val routes = client.routes(path, selector)["routes"].asJsonObject
                    assertFalse(routes["runtimeRoutesComplete"].asBoolean)
                    val app = routes["applications"].asJsonArray.single().asJsonObject
                    assertEquals(selector, app["className"].asString)
                    assertEquals(expected, app["mounts"].asJsonArray.single().asJsonObject["path"].asString)
                }
            }
        } finally { other.tearDown() }
    }
    fun testDependencyToolIsRegisteredReadOnlyAndReturnsSpringFactsOverHttp() {
        Client().use { client ->
            val listing = parse(await(client.post("""{"jsonrpc":"2.0","id":900,"method":"tools/list","params":{}}""")))["result"].asJsonObject["tools"].asJsonArray
            val tool = listing.single { it.asJsonObject["name"].asString == WicketMcpReport.DEPENDENCIES.tool }.asJsonObject
            assertTrue(tool["annotations"].asJsonObject["readOnlyHint"].asBoolean)
            assertFalse(tool["annotations"].asJsonObject["destructiveHint"].asBoolean)
            val schema = tool["inputSchema"].asJsonObject
            val properties = schema["properties"].asJsonObject
            assertTrue(properties.has("projectPath") && properties.has("className") && properties.has("moduleName") && properties.has("sourceSet"))
            assertFalse(schema["required"]?.asJsonArray?.any { it.asString == "className" } == true)
            val response = client.call(WicketMcpReport.DEPENDENCIES)
            val dependencies = response["dependencies"].asJsonObject
            assertFalse(dependencies["runtimeBindingsVerified"].asBoolean)
            val fields = dependencies["owners"].asJsonArray.single().asJsonObject["fields"].asJsonArray
            val product = fields.first().asJsonObject["candidates"].asJsonArray.single().asJsonObject
            assertEquals("example.Service", product["type"].asString)
            assertEquals("((prod & eu) OR (staging))", product["profiles"].asJsonArray.single().asString)
            assertFalse(product["factoryLookup"].asBoolean)
            assertTrue(fields.last().asJsonObject["candidates"].asJsonArray.single().asJsonObject["factoryLookup"].asBoolean)
            assertEquals(response, client.call(WicketMcpReport.DEPENDENCIES))
            val inventory = client.call(WicketMcpReport.DEPENDENCIES, className = null)["dependencies"].asJsonObject
            assertEquals("project", inventory["scope"].asString)
            assertEquals(fields.map { it.asJsonObject["location"] }, inventory["fields"].asJsonArray.map { it.asJsonObject["location"] })
        }
    }
    fun testGuiceRegistrationIsPublishedReadOnlyThroughTheActualHost() {
        System.getProperty("wicketlens.test.guiceLibraries").split(File.pathSeparator).forEachIndexed { index, path ->
            val jar = File(path)
            PsiTestUtil.addLibrary(testRootDisposable, module, "MCP Guice fixture $index", jar.parent, jar.name)
        }
        val page = myFixture.addFileToProject("src/main/java/example/GuicePage.java", """
            package example;
            public class GuicePage extends org.apache.wicket.markup.html.WebPage {
                @com.google.inject.Inject private Service service;
            }
        """.trimIndent())
        val application = myFixture.addFileToProject("src/main/java/example/GuiceApplication.java", """
            package example;
            public class GuiceApplication extends org.apache.wicket.protocol.http.WebApplication {
                @Override protected void init() {
                    super.init();
                    getComponentInstantiationListeners().add(new org.apache.wicket.guice.GuiceComponentInjector(this));
                }
                @Override public Class<? extends org.apache.wicket.Page> getHomePage() { return GuicePage.class; }
            }
        """.trimIndent())
        val files = listOf(page, application)
        val before = files.map { it.text to it.modificationStamp }
        waitFor("Guice fixture smart mode") { !com.intellij.openapi.project.DumbService.isDumb(project) }
        Client().use { client ->
            val response = client.call(WicketMcpReport.DEPENDENCIES, className = "example.GuicePage")
            assertEquals(1, response["schemaVersion"].asInt)
            val dependencies = response["dependencies"].asJsonObject
            assertFalse(dependencies["runtimeBindingsVerified"].asBoolean)
            val field = dependencies["owners"].asJsonArray.single().asJsonObject["fields"].asJsonArray.single().asJsonObject
            assertEquals("service", field["name"].asString)
            assertEquals("GUICE", field["lookup"].asString)
            assertTrue(field["candidates"].asJsonArray.isEmpty)
            val registration = field["injectorRegistration"].asJsonObject
            assertTrue(registration["file"].asString.endsWith("/example/GuiceApplication.java"))
            assertEquals(application.text.indexOf(".add(") + 1, registration["offset"].asInt)
            val inventory = client.call(WicketMcpReport.DEPENDENCIES, className = null)["dependencies"].asJsonObject
            val projectField = inventory["fields"].asJsonArray.map { it.asJsonObject }.single { it["lookup"].asString == "GUICE" }
            assertEquals(registration, projectField["injectorRegistration"])
            assertEquals(field["location"], projectField["location"])
            val plan = client.call(WicketMcpReport.TEST_PLAN, className = "example.GuicePage")["testPlan"].asJsonObject
            val binding = plan["dependencyGraph"].asJsonObject["owners"].asJsonArray.single().asJsonObject
                .getAsJsonArray("fields").single().asJsonObject
            assertEquals("MANUAL", binding["testBinding"].asString)
            assertEquals(registration, binding["injectorRegistration"])
        }
        assertEquals(before, files.map { it.text to it.modificationStamp })
    }

    fun testSpringInspectionToolReturnsTheNativeFindingsThroughTheActualHost() {
        Client().use { client ->
            val response = client.call(WicketMcpReport.SPRING_INSPECTIONS)
            val data = response["springInspections"].asJsonObject
            assertFalse(data["runtimeValidityVerified"].asBoolean)
            val findings = data["findings"].asJsonArray
            assertEquals(setOf("WicketSpringInjection","WicketSpringSerialization"),findings.map { it.asJsonObject["inspectionId"].asString }.toSet())
            assertTrue(findings.all { it.asJsonObject["nodeIds"].asJsonArray.single().asString == "page" })
            assertTrue(findings.any { it.asJsonObject["message"].asString.startsWith("This initializer may overwrite") })
            assertEquals(response,client.call(WicketMcpReport.SPRING_INSPECTIONS))
        }
    }
    fun testSpringTestPlanIsRegisteredReadOnlyAndReturnsTheExistingScaffoldOverHttp() {
        Client().use { client ->
            val listing = parse(await(client.post("""{"jsonrpc":"2.0","id":901,"method":"tools/list","params":{}}""")))["result"].asJsonObject["tools"].asJsonArray
            val tool = listing.single { it.asJsonObject["name"].asString == WicketMcpReport.SPRING_TEST_PLAN.tool }.asJsonObject
            val hints = tool["annotations"].asJsonObject
            assertTrue(hints["readOnlyHint"].asBoolean)
            assertFalse(hints["destructiveHint"].asBoolean)
            assertTrue(hints["idempotentHint"].asBoolean)
            assertFalse(hints["openWorldHint"].asBoolean)
            val properties = tool["inputSchema"].asJsonObject["properties"].asJsonObject
            assertTrue(listOf("projectPath","className","moduleName","sourceSet").all { properties.has(it) })
            val response = client.call(WicketMcpReport.SPRING_TEST_PLAN)
            val plan = response["springTestPlan"].asJsonObject
            assertFalse(plan["runtimeValidityVerified"].asBoolean)
            assertFalse(plan["dependencyAvailabilityChecked"].asBoolean)
            assertEquals("PAGE", plan["constructorStrategy"].asString)
            assertEquals(2, plan["manualBindings"].asJsonArray.size())
            val fields = plan["dependencyGraph"].asJsonObject["owners"].asJsonArray.single().asJsonObject.getAsJsonArray("fields")
            assertEquals(2, fields.size())
            assertTrue(fields.all { it.asJsonObject["testBinding"].asString == "MANUAL" })
            assertTrue(plan["source"].asString.contains("new SpringComponentInjector"))
            assertEquals(response,client.call(WicketMcpReport.SPRING_TEST_PLAN))
        }
    }

    fun testMountedPageTestPlanIsReadOnlyThroughTheActualHost() {
        val route = "/orders/\${orderId}/#{view}"
        val page = myFixture.addFileToProject("src/main/java/example/OrderPage.java", """
            package example;
            public class OrderPage extends org.apache.wicket.markup.html.WebPage {
                public OrderPage(org.apache.wicket.request.mapper.parameter.PageParameters parameters) { super(parameters); }
            }
        """.trimIndent())
        val session = myFixture.addFileToProject("src/main/java/example/OrdersSession.java", """
            package example;
            public class OrdersSession extends org.apache.wicket.protocol.http.WebSession {
                public OrdersSession(org.apache.wicket.request.Request request) { super(request); }
            }
        """.trimIndent())
        val app = myFixture.addFileToProject("src/main/java/example/OrdersApplication.java", """
            package example;
            public class OrdersApplication extends org.apache.wicket.protocol.http.WebApplication {
                public Class<? extends org.apache.wicket.Page> getHomePage() { return OrderPage.class; }
                protected void init() { super.init(); mountPage("$route", OrderPage.class); }
                public org.apache.wicket.Session newSession(org.apache.wicket.request.Request request,
                    org.apache.wicket.request.Response response) { return new OrdersSession(request); }
            }
        """.trimIndent())
        val files = listOf(page, app, session)
        val before = files.map { it.text to it.modificationStamp }
        val view = WicketMcpReport.TEST_PLAN
        Client().use { client ->
            val listing = parse(await(client.post("""{"jsonrpc":"2.0","id":918,"method":"tools/list","params":{}}""")))["result"]
                .asJsonObject["tools"].asJsonArray
            val tool = listing.single { it.asJsonObject["name"].asString == view.tool }.asJsonObject
            assertTrue(tool["annotations"].asJsonObject["readOnlyHint"].asBoolean)
            val inputs = tool["inputSchema"].asJsonObject["properties"].asJsonObject
            assertTrue(inputs.has("routePath") && inputs.has("applicationClass") && inputs.has("routeParameters"))
            val result = client.call(view, className = "example.OrderPage", routePath = route, applicationClass = "example.OrdersApplication")
            val plan = result["testPlan"].asJsonObject
            assertEquals("mounted_wicket_tester_scaffold", plan["kind"].asString)
            val mount = plan["mountedRoute"].asJsonObject
            val declaredSession = mount["session"].asJsonObject
            assertEquals("example.OrdersSession", declaredSession["className"].asString)
            assertTrue(declaredSession["declaration"].asJsonObject["file"].asString.endsWith("/example/OrdersSession.java"))
            assertEquals("orders/42/43", mount["requestPath"].asString)
            val omission = mount["withoutOptionalParameters"].asJsonObject
            assertEquals("orders/42", omission["requestPath"].asString)
            assertEquals("view", omission["absentParameters"].asJsonArray.single().asString)
            assertTrue(plan["source"].asString.contains("new example.OrdersApplication()"))
            val custom = client.call(view, className = "example.OrderPage", routePath = route,
                routeParameters = mapOf("orderId" to "order-A1", "view" to "summary"))["testPlan"].asJsonObject
            assertEquals("orders/order-A1/summary", custom["mountedRoute"].asJsonObject["requestPath"].asString)
            assertEquals("orders/order-A1", custom["mountedRoute"].asJsonObject["withoutOptionalParameters"].asJsonObject["requestPath"].asString)
            assertEquals(before, files.map { it.text to it.modificationStamp })
        }
    }

    fun testMountedResourcePlanIsReadOnlyThroughTheActualHost() {
        val app = myFixture.addFileToProject("src/main/java/example/AssetsApplication.java", """
            package example;
            public class AssetsApplication extends org.apache.wicket.protocol.http.WebApplication {
                public Class<? extends org.apache.wicket.Page> getHomePage() { return McpPage.class; }
                protected void init() {
                    super.init();
                    mountResource("/logo.svg", new org.apache.wicket.request.resource.PackageResourceReference(AssetsApplication.class, "logo.svg"));
                }
            }
        """.trimIndent())
        val before = app.text
        Client().use { client ->
            val view = WicketMcpReport.TEST_PLAN
            val listing = parse(await(client.post("""{"jsonrpc":"2.0","id":919,"method":"tools/list","params":{}}""")))["result"]
                .asJsonObject["tools"].asJsonArray
            val tool = listing.single { it.asJsonObject["name"].asString == view.tool }.asJsonObject
            assertTrue(tool["annotations"].asJsonObject["readOnlyHint"].asBoolean)
            val inputs = tool["inputSchema"].asJsonObject["properties"].asJsonObject
            assertTrue(inputs.has("mountedResource") && inputs.has("expectedContentType") && inputs.has("expectedBodySha256"))
            val digest = "aB".repeat(32)
            val result = client.call(view, className = "example.AssetsApplication", routePath = "/logo.svg",
                mountedResource = true, expectedContentType = "image/svg+xml", expectedBodySha256 = digest)
            val plan = result["testPlan"].asJsonObject
            assertEquals("mounted_resource_wicket_tester_scaffold", plan["kind"].asString)
            val route = plan["mountedRoute"].asJsonObject
            assertEquals("logo.svg", route["requestPath"].asString)
            assertEquals("image/svg+xml", route["expectedContentType"].asString)
            assertEquals(digest.lowercase(), route["expectedBodySha256"].asString)
            assertEquals(app.virtualFile.path, route["registration"].asJsonObject["file"].asString)
            assertEquals(before, app.text)
        }
    }

    fun testGeneralTestPlanUsesTheSameSpringRecipeWhenInjectionIntentIsPresent() {
        Client().use { client ->
            val general = client.call(WicketMcpReport.TEST_PLAN)
            val spring = client.call(WicketMcpReport.SPRING_TEST_PLAN)
            assertEquals(spring["springTestPlan"],general["testPlan"])
            assertEquals("wicket_get_test_plan",general["tool"].asString)
        }
    }

}
