package dev.wicketlens

import com.google.gson.JsonParser
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.psi.PsiDocumentManager
import com.intellij.testFramework.PsiTestUtil
import dev.wicketlens.mcp.PageAnalysisFailure
import dev.wicketlens.mcp.WicketMcpReport
import dev.wicketlens.mcp.WicketPageAnalysisRequest
import dev.wicketlens.spring.WicketSpringTestFiles
import dev.wicketlens.testing.WicketMountedTestSelection
import dev.wicketlens.testing.WicketMountedResource
import dev.wicketlens.testing.WicketMountedTests
import dev.wicketlens.testing.WicketTestGenerator
import dev.wicketlens.testing.WicketTestAuthentication
import org.jetbrains.jps.model.java.JavaSourceRootType
import java.io.File
import java.nio.file.Files
import java.util.concurrent.TimeUnit

class WicketMountedTestPlanningTest : WicketProjectPsiTestCase() {
    private val query = WicketPageAnalysisRequest()
    private val view = WicketMcpReport.TEST_PLAN
    private val orderRoute = "/orders/\${orderId}"

    private fun orderPage() = myFixture.addFileToProject("src/main/java/example/OrderPage.java", """
        package example;
        import org.apache.wicket.markup.html.WebPage;
        import org.apache.wicket.request.mapper.parameter.PageParameters;
        public class OrderPage extends WebPage {
            public OrderPage(PageParameters parameters) {
                super(parameters);
            }
        }
    """.trimIndent())

    private fun application(name: String = "OrdersApplication", init: String = """mountPage("$orderRoute", OrderPage.class);""",
        constructor: String = "") = myFixture.addFileToProject("src/main/java/example/$name.java", """
        package example;
        import org.apache.wicket.Page;
        import org.apache.wicket.markup.html.WebPage;
        import org.apache.wicket.protocol.http.WebApplication;
        public class $name extends WebApplication {
            $constructor
            @Override
            public Class<? extends Page> getHomePage() { return WebPage.class; }
            @Override
            protected void init() {
                super.init();
                $init
            }
        }
    """.trimIndent())

    private fun capture(path: String = orderRoute, application: String? = null) =
        query.capture(project, "example.OrderPage", view = view, mounted = WicketMountedTestSelection(path, application))

    private fun fails(block: () -> Unit): PageAnalysisFailure {
        try {
            block()
        } catch (failure: PageAnalysisFailure) {
            return failure
        }
        error("Expected a test planning failure")
    }

    private fun rejects(path: String = orderRoute, application: String? = null, message: String) {
        val failure = fails { capture(path, application) }
        assertEquals("TEST_PLAN_UNAVAILABLE", failure.code)
        assertTrue(failure.message.orEmpty(), failure.message.orEmpty().contains(message))
    }

    fun testMountedPlanUsesTheSelectedApplicationAndSourcesThroughBothEntryPoints() {
        val page = orderPage()
        val app = application()
        application("OtherApplication")
        val root = myFixture.tempDirFixture.findOrCreateDir("src/test/java")
        PsiTestUtil.addSourceRoot(module, root, JavaSourceRootType.TEST_SOURCE)
        rejects(message = "ambiguous")
        val snapshot = capture(application = "example.OrdersApplication")
        val prepared = snapshot.testPlan!!
        val native = WicketTestGenerator().prepare(project, page.virtualFile.url, -1,
            WicketMountedTestSelection(orderRoute, "example.OrdersApplication"))
        assertEquals(native.plan, prepared.plan)
        assertTrue(prepared.version.sources.any { it.path == app.virtualFile.path })
        val plan = JsonParser.parseString(query.render(snapshot, view)).asJsonObject["testPlan"].asJsonObject
        assertEquals("mounted_wicket_tester_scaffold", plan["kind"].asString)
        assertEquals("MOUNTED_ROUTE", plan["constructorStrategy"].asString)
        val route = plan["mountedRoute"].asJsonObject
        assertEquals("orders/42", route["requestPath"].asString)
        assertEquals("42", route["parameters"].asJsonObject["orderId"].asString)
        assertEquals(app.virtualFile.path, route["registration"].asJsonObject["file"].asString)
        val created = WicketSpringTestFiles.create(project, prepared, prepared.roots.single(), prepared.plan.testName)
        assertTrue(created.text.contains("""tester.executeUrl("orders/42")"""))
        assertTrue(created.text.contains("""getPageParameters().get("orderId")"""))
        val allocations = com.intellij.psi.util.PsiTreeUtil.findChildrenOfType(created, com.intellij.psi.PsiNewExpression::class.java)
        assertTrue(allocations.any { it.resolveConstructor()?.containingClass?.qualifiedName == "example.OrdersApplication" })
        assertEquals("OrderPageMountedTest.java", created.name)
    }

    fun testConditionalAmbiguousAndInaccessibleApplicationMountsAreWithheld() {
        orderPage()
        application(init = """if (Boolean.getBoolean("feature")) mountPage("$orderRoute", OrderPage.class);""")
        application("OptionalApplication", """mountPage("/optional/#{id}/#{id}", OrderPage.class);""")
        application("PrivateApplication", """mountPage("/private", OrderPage.class);""", "private PrivateApplication() {}")
        rejects(message = "unconditional")
        rejects("/optional/#{id}/#{id}", message = "supported parameter segments")
        rejects("/private", message = "accessible no-argument constructor")
        rejects("/missing", message = "No matching page mount")
    }

    fun testRouteEditsInvalidateThePlan() {
        orderPage()
        val app = application()
        val snapshot = capture()
        val documents = PsiDocumentManager.getInstance(project)
        WriteCommandAction.runWriteCommandAction(project) {
            documents.getDocument(app)!!.setText(app.text.replace("/orders/", "/purchases/"))
        }
        documents.commitAllDocuments()
        val stale = fails { query.requireCurrent(project, snapshot) }
        assertEquals("STALE_SNAPSHOT", stale.code)
        rejects(message = "No matching page mount")
    }

    fun testRouteExamplesAreAppliedToBothRequestsAndRejectedOutsideTheirMount() {
        val page = orderPage()
        application(init = """mountPage("$optionalRoute", OrderPage.class);""")
        val selection = WicketMountedTestSelection(optionalRoute, routeParameters = mapOf("locale" to "en-US", "itemId" to "A-15"))
        val prepared = query.capture(project, "example.OrderPage", view = view, mounted = selection).testPlan!!
        val native = WicketTestGenerator().prepare(project, page.virtualFile.url, -1, selection)
        assertEquals(native.plan, prepared.plan)
        val mount = prepared.plan.mounted!!
        assertEquals("catalog/en-US/A-15/detail/42", mount.requestPath)
        assertEquals("catalog/A-15/detail", mount.withoutOptionalParameters!!.requestPath)
        assertEquals(mapOf("itemId" to "A-15"), mount.withoutOptionalParameters.parameters)
        fun reject(values: Map<String, String>, code: String) = assertEquals(code, fails {
            query.capture(project, "example.OrderPage", view = view, mounted = selection.copy(routeParameters = values))
        }.code)
        reject(mapOf("itemId" to "../escaped"), "INVALID_REQUEST")
        reject(mapOf("view" to "empty"), "TEST_PLAN_UNAVAILABLE")
        reject(mapOf("locale" to "detail"), "TEST_PLAN_UNAVAILABLE")
    }

    private val sessionFactory = """
        @Override public org.apache.wicket.Session newSession(org.apache.wicket.request.Request request,
            org.apache.wicket.request.Response response) { return new OrdersSession(request); }
    """.trimIndent()

    private fun ordersSession() = myFixture.addFileToProject("src/main/java/example/OrdersSession.java", """
            package example;
            public class OrdersSession extends org.apache.wicket.protocol.http.WebSession {
                public OrdersSession(org.apache.wicket.request.Request request) { super(request); }
            }
        """.trimIndent())

    fun testCustomWebSessionSourceBecomesPartOfTheMountedPlan() {
        orderPage()
        val session = ordersSession()
        val app = application(constructor = sessionFactory)
        val snapshot = capture()
        val plan = dev.wicketlens.mcp.WicketTestPlanExport.render(snapshot.testPlan!!)
        val declared = plan["mountedRoute"].asJsonObject["session"].asJsonObject
        assertEquals("example.OrdersSession", declared["className"].asString)
        assertFalse(declared.has("authentication"))
        assertTrue(plan["source"].asString.contains("tester.getSession().getClass().getName()"))
        assertTrue(snapshot.version.sources.any { it.path == session.virtualFile.path })
        val documents = PsiDocumentManager.getInstance(project)
        WriteCommandAction.runWriteCommandAction(project) { documents.getDocument(app)!!.setText(app.text.replace("new OrdersSession(request)", "super.newSession(request, response)")) }
        documents.commitAllDocuments()
        assertNull(capture().testPlan!!.plan.mounted!!.session)
    }

    private val optionalRoute = "/catalog/#{locale}/\${itemId}/detail/#{layout}"
    private val fixedResource = "/logo.txt"
    private val resourceDigest = java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(byteArrayOf(65, 66)))

    private fun resourceApplication() = application("ResourceApplication", """
        mountResource("$fixedResource", new org.apache.wicket.request.resource.ResourceReference("logo") {
            @Override
            public org.apache.wicket.request.resource.IResource getResource() {
                return new org.apache.wicket.request.resource.ByteArrayResource("text/plain", new byte[] {65, 66});
            }
        });
    """.trimIndent())

    fun testResourcePlanUsesTheApplicationAndValidatesTheMediaType() {
        val app = resourceApplication()
        val root = myFixture.tempDirFixture.findOrCreateDir("src/test/java")
        PsiTestUtil.addSourceRoot(module, root, JavaSourceRootType.TEST_SOURCE)
        val task = WicketMountedTests.prepareResource(project, "example.ResourceApplication", fixedResource, module.name, "main",
            WicketMountedResource("Text/Plain", resourceDigest.uppercase()))
        assertEquals(app.virtualFile.url, task.test.sourceUrl)
        val plan = dev.wicketlens.mcp.WicketTestPlanExport.render(task.test)
        assertEquals("mounted_resource_wicket_tester_scaffold", plan["kind"].asString)
        assertEquals("Text/Plain", plan["mountedRoute"].asJsonObject["expectedContentType"].asString)
        assertEquals(resourceDigest, plan["mountedRoute"].asJsonObject["expectedBodySha256"].asString)
        val source = plan["source"].asString
        assertTrue(source.contains("""assertEquals("text/plain", contentType.split"""))
        assertTrue(source.contains("tester.getLastResponse().getBinaryContent()"))
        assertFalse(source.contains("assertRenderedPage"))
        val created = WicketSpringTestFiles.create(project, task.test, task.test.roots.single(), task.test.plan.testName)
        assertEquals("ResourceApplicationMountedResourceTest.java", created.name)
        assertThrows(dev.wicketlens.analysis.AnalysisUnavailable::class.java) {
            WicketMountedTests.prepareResource(project, "example.ResourceApplication", fixedResource, module.name, "main",
                WicketMountedResource("text/plain; charset=UTF-8"))
        }
        assertThrows(dev.wicketlens.analysis.AnalysisUnavailable::class.java) {
            WicketMountedTests.prepareResource(project, "example.ResourceApplication", fixedResource, module.name, "main",
                WicketMountedResource(expectedBodySha256 = "0000"))
        }
    }

    fun testGeneratedTestsRunAgainstWicketAndDetectChangedMountsParametersAndResourceContent() {
        val page = orderPage()
        val app = application()
        val session = ordersSession()
        val fixed = application("FixedApplication", """mountPage("/recent-orders", OrderPage.class);""", sessionFactory)
        val optional = application("OptionalApplication", """mountPage("$optionalRoute", OrderPage.class);""")
        val resource = resourceApplication()
        val markup = companion("OrderPage", "<html><body><h1>Order</h1></body></html>")
        fun generated(path: String, application: String, name: String): String {
            val plan = capture(path, application).testPlan!!.plan
            return dev.wicketlens.spring.WicketSpringTestGenerator().render(plan, name)
        }
        val parameterized = generated(orderRoute, "example.OrdersApplication", "OrderPageMountedTest")
        val fixedTest = generated("/recent-orders", "example.FixedApplication", "OrderPageFixedTest")
        val optionalPlan = query.capture(project, "example.OrderPage", view = view, mounted = WicketMountedTestSelection(
            optionalRoute, "example.OptionalApplication", mapOf("itemId" to "A-15"))).testPlan!!
        val omission = dev.wicketlens.mcp.WicketTestPlanExport.render(optionalPlan)["mountedRoute"].asJsonObject["withoutOptionalParameters"].asJsonObject
        assertEquals("catalog/A-15/detail", omission["requestPath"].asString)
        assertEquals(listOf("locale", "layout"), omission["absentParameters"].asJsonArray.map { it.asString })
        val optionalTest = dev.wicketlens.spring.WicketSpringTestGenerator().render(optionalPlan.plan, "OrderPageOptionalTest")
        val resourcePlan = WicketMountedTests.prepareResource(project, "example.ResourceApplication", fixedResource, module.name,
            "main", WicketMountedResource("text/plain", resourceDigest)).test.plan
        val resourceTest = dev.wicketlens.spring.WicketSpringTestGenerator().render(resourcePlan, "MountedResourceTest")
        val directory = Files.createTempDirectory("wicketlens-mounted-test").toFile()
        try {
            val example = File(directory, "example").apply { mkdirs() }
            File(example, "OrderPage.java").writeText(page.text)
            File(example, "OrderPage.html").writeText(markup.text)
            File(example, session.name).writeText(session.text)
            File(example, "OrderPageMountedTest.java").writeText(parameterized)
            File(example, "OrderPageFixedTest.java").writeText(fixedTest)
            File(example, "OrderPageOptionalTest.java").writeText(optionalTest)
            File(example, "MountedResourceTest.java").writeText(resourceTest)
            val appFile = File(example, "OrdersApplication.java")
            val fixedFile = File(example, "FixedApplication.java")
            val optionalFile = File(example, "OptionalApplication.java")
            val resourceFile = File(example, "ResourceApplication.java")
            val jars = listOf("libraries", "springTestLibraries").flatMap {
                System.getProperty("wicketlens.test.$it").split(File.pathSeparator)
            }.distinct()
            val classpath = (listOf(directory.path) + jars).joinToString(File.pathSeparator)
            val javac = System.getProperty("wicketlens.test.javac")
            val java = File(File(javac).parentFile, "java").path
            fun checkApplication(expectedErrors: Int, modifiedFile: File? = null, original: String = "", changed: String = "") {
                appFile.writeText(app.text)
                fixedFile.writeText(fixed.text)
                optionalFile.writeText(optional.text)
                resourceFile.writeText(resource.text)
                if (modifiedFile != null) modifiedFile.writeText(modifiedFile.readText().replace(original, changed))
                val compile = run(directory, listOf(javac, "--release", "21", "-cp", classpath, "-d", directory.path) +
                    example.listFiles()!!.filter { it.extension == "java" }.map { it.path })
                assertEquals(compile.second, 0, compile.first)
                val result = run(directory, listOf(java, "-cp", classpath, "org.junit.platform.console.ConsoleLauncher", "execute",
                    "--select-class", "example.OrderPageMountedTest", "--select-class", "example.OrderPageFixedTest",
                    "--select-class", "example.OrderPageOptionalTest", "--select-class", "example.MountedResourceTest",
                    "--fail-if-no-tests", "--disable-banner"))
                assertEquals(result.second, expectedErrors, result.first)
                assertTrue(result.second, result.second.contains("$expectedErrors tests failed"))
                assertTrue(result.second, result.second.contains("${5 - expectedErrors} tests successful"))
            }
            checkApplication(0)
            checkApplication(1, appFile, "/orders/", "/purchases/")
            checkApplication(1, appFile, "orderId", "id")
            checkApplication(1, fixedFile, "new OrdersSession(request)", "super.newSession(request, response)")
            checkApplication(1, optionalFile, "#{layout}", "\${layout}")
            checkApplication(1, resourceFile, "text/plain", "text/xml")
            checkApplication(1, resourceFile, "65, 66", "65, 67")
            checkApplication(1, resourceFile, "/logo.txt", "/logo-moved.txt")
            checkApplication(0)
        } finally {
            directory.deleteRecursively()
        }
    }

    fun testAuthenticatedMountedScaffoldRunsTheApplicationSignInAndDetectsLostProtection() {
        val authJars = System.getProperty("wicketlens.test.authRolesLibraries").split(File.pathSeparator)
        authJars.forEachIndexed { index, path ->
            val jar = File(path)
            PsiTestUtil.addLibrary(testRootDisposable, module, "Wicket auth roles $index", jar.parent, jar.name)
        }
        val route = "/protected/\${orderId}"
        val application = myFixture.addFileToProject("src/main/java/example/ProtectedApplication.java", """
            package example;
            public class ProtectedApplication extends org.apache.wicket.authroles.authentication.AuthenticatedWebApplication {
                @Override protected void init() { super.init(); mountPage("$route", ProtectedPage.class); }
                @Override public Class<? extends org.apache.wicket.Page> getHomePage() { return LoginPage.class; }
                @Override protected Class<? extends org.apache.wicket.authroles.authentication.AbstractAuthenticatedWebSession> getWebSessionClass() { return MemberSession.class; }
                @Override protected Class<? extends org.apache.wicket.markup.html.WebPage> getSignInPageClass() { return LoginPage.class; }
            }
        """.trimIndent())
        val protectedPage = myFixture.addFileToProject("src/main/java/example/ProtectedPage.java", """
            package example;
            @org.apache.wicket.authroles.authorization.strategies.role.annotations.AuthorizeInstantiation("MEMBER")
            public class ProtectedPage extends org.apache.wicket.markup.html.WebPage {
                public ProtectedPage(org.apache.wicket.request.mapper.parameter.PageParameters parameters) { super(parameters); }
            }
        """.trimIndent())
        val session = myFixture.addFileToProject("src/main/java/example/MemberSession.java", """
            package example;
            import org.apache.wicket.authroles.authorization.strategies.role.Roles;
            public class MemberSession extends org.apache.wicket.authroles.authentication.AuthenticatedWebSession {
                public MemberSession(org.apache.wicket.request.Request request) { super(request); }
                @Override protected boolean authenticate(String username, String password) {
                    return "fixture-user".equals(username) && "fixture-password".equals(password);
                }
                @Override public Roles getRoles() { return isSignedIn() ? new Roles("MEMBER") : new Roles(); }
            }
        """.trimIndent())
        val login = myFixture.addFileToProject("src/main/java/example/LoginPage.java", """
            package example; public class LoginPage extends org.apache.wicket.markup.html.WebPage {}
        """.trimIndent())
        val snapshot = query.capture(project, "example.ProtectedPage", view = view, mounted = WicketMountedTestSelection(route))
        val prepared = snapshot.testPlan!!
        val native = WicketTestGenerator().prepare(project, protectedPage.virtualFile.url, -1, WicketMountedTestSelection(route))
        assertEquals(native.plan, prepared.plan)
        val plan = dev.wicketlens.mcp.WicketTestPlanExport.render(prepared)
        val metadata = plan["mountedRoute"].asJsonObject["session"].asJsonObject
        assertEquals("example.MemberSession", metadata["className"].asString)
        val auth = metadata["authentication"].asJsonObject
        assertEquals("example.LoginPage", auth["signInPage"].asString)
        assertEquals(listOf("MEMBER"), auth["declaredRoles"].asJsonArray.map { it.asString })
        assertEquals(WicketTestAuthentication.USERNAME_ENV, auth["usernameEnvironmentVariable"].asString)
        assertTrue(plan["requiredDependencies"].asJsonArray.any { it.asString == "org.apache.wicket:wicket-auth-roles" })
        assertTrue(listOf(application, protectedPage, session, login).all { file -> snapshot.version.sources.any { it.path == file.virtualFile.path } })
        val generated = plan["source"].asString
        assertFalse(generated.contains("fixture-password"))
        val directory = Files.createTempDirectory("wicketlens-authenticated-test").toFile()
        try {
            val example = File(directory, "example").apply { mkdirs() }
            listOf(application, protectedPage, session, login).forEach { file -> File(example, file.name).writeText(file.text) }
            val pageSource = File(example, protectedPage.name)
            for (name in listOf("LoginPage", "ProtectedPage")) File(example, "$name.html").writeText("<html><body>$name</body></html>")
            File(example, "${prepared.plan.testName}.java").writeText(generated)
            val jars = (authJars + listOf("libraries", "springTestLibraries").flatMap {
                System.getProperty("wicketlens.test.$it").split(File.pathSeparator)
            }).distinct()
            val classpath = (listOf(directory.path) + jars).joinToString(File.pathSeparator)
            val javac = System.getProperty("wicketlens.test.javac")
            val java = File(File(javac).parentFile, "java").path
            fun check(expectedFailures: Int, password: String = "fixture-password") {
                val compile = run(directory, listOf(javac, "--release", "21", "-cp", classpath, "-d", directory.path) +
                    example.listFiles()!!.filter { it.extension == "java" }.map { it.path })
                assertEquals(compile.second, 0, compile.first)
                val result = run(directory, listOf(java, "-cp", classpath, "org.junit.platform.console.ConsoleLauncher", "execute",
                    "--select-class", "example.${prepared.plan.testName}", "--fail-if-no-tests", "--disable-banner"),
                    mapOf(WicketTestAuthentication.USERNAME_ENV to "fixture-user", WicketTestAuthentication.PASSWORD_ENV to password))
                assertEquals(result.second, expectedFailures, result.first)
                assertTrue(result.second, result.second.contains("$expectedFailures tests failed"))
                assertTrue(result.second, result.second.contains("${2 - expectedFailures} tests successful"))
            }
            check(0)
            check(1, "incorrect-fixture-password")
            pageSource.writeText(protectedPage.text.lines().filterNot { "@org.apache.wicket" in it }.joinToString("\n"))
            check(1)
        } finally {
            directory.deleteRecursively()
        }
        val documents = PsiDocumentManager.getInstance(project)
        WriteCommandAction.runWriteCommandAction(project) { documents.getDocument(login)!!.insertString(0, "// changed\n") }
        documents.commitAllDocuments()
        assertEquals("STALE_SNAPSHOT", fails { query.requireCurrent(project, snapshot) }.code)
        WriteCommandAction.runWriteCommandAction(project) {
            val document = documents.getDocument(protectedPage)!!
            document.setText(protectedPage.text.replace("AuthorizeInstantiation(\"MEMBER\")", "AuthorizeInstantiation(ProtectedPage.ROLE)")
                .replace("public class ProtectedPage extends org.apache.wicket.markup.html.WebPage {",
                    "public class ProtectedPage extends org.apache.wicket.markup.html.WebPage { public static final String ROLE = \"MEMBER\";"))
        }
        documents.commitAllDocuments()
        val dynamicRole = query.capture(project, "example.ProtectedPage", view = view, mounted = WicketMountedTestSelection(route))
        assertNull(dynamicRole.testPlan!!.plan.mounted!!.session!!.authentication)
    }

    private fun run(directory: File, command: List<String>, environment: Map<String, String> = emptyMap()): Pair<Int, String> {
        val log = File(directory, "process.log")
        val builder = ProcessBuilder(command).directory(directory).redirectErrorStream(true).redirectOutput(log)
        builder.environment().putAll(environment)
        val process = builder.start()
        if (!process.waitFor(60, TimeUnit.SECONDS)) {
            process.destroyForcibly()
            error("Generated test process timed out: ${log.readText()}")
        }
        return process.exitValue() to log.readText()
    }
}
