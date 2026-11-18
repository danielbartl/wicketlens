package dev.wicketlens

import com.google.gson.JsonParser
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.psi.PsiDocumentManager
import com.intellij.testFramework.PsiTestUtil
import dev.wicketlens.mcp.PageAnalysisFailure
import dev.wicketlens.routes.MountStatus
import dev.wicketlens.routes.WicketRoutesRequest
import org.jetbrains.jps.model.java.JavaSourceRootType

class WicketBeanRoutesTest : WicketSpringPsiTestCase() {
    private val request = WicketRoutesRequest()
    private val selector = "example.Config#application"

    private fun config(methods: String, sourceSet: String = "main") =
        myFixture.addFileToProject("src/$sourceSet/java/example/Config.java", """
            package example;
            import org.apache.wicket.Page;
            import org.apache.wicket.protocol.http.WebApplication;
            public class Config {
                $methods
            }
        """.trimIndent())

    private fun application(path: String, init: String = "") = """
        new WebApplication() {
            @Override public Class<? extends Page> getHomePage() { return ExamplePage.class; }
            @Override protected void init() { super.init(); mountPage("$path", ExamplePage.class); $init }
            private void additionalRoutes() {
                if (System.getProperty("feature") != null) mountPage("/conditional", ExamplePage.class);
            }
        }
    """.trimIndent()

    fun testDirectSpringBeanReturnsTheSameSourceFactsToMcp() {
        val page = pageFile()
        val html = companion()
        val java = config("""
            @org.springframework.context.annotation.Bean("customBeanName")
            @org.springframework.context.annotation.Profile("demo")
            WebApplication application(String injected) { return (${application("/bean", "additionalRoutes();")}); }
        """.trimIndent())
        val contents = java.text
        val stamp = java.virtualFile.modificationStamp
        val requestSnapshot = request.capture(project, applicationClass = selector)
        val app = requestSnapshot.routes.applications.single()
        assertEquals(selector, app.className)
        assertEquals("application", java.findElementAt(app.source.offset)?.text)
        assertEquals(java.virtualFile.path, app.source.file)
        assertEquals(listOf("/bean", "/conditional"), app.mounts.map { it.path })
        assertEquals(listOf(MountStatus.DECLARED, MountStatus.CONDITIONAL), app.mounts.map { it.status })
        assertTrue(app.notes.any { it.contains("activation are unknown") })
        val mount = app.mounts.first()
        assertEquals(java.virtualFile.path, mount.registration.file)
        assertEquals("mountPage", java.findElementAt(mount.registration.offset)?.text)
        assertEquals(page.virtualFile.path, mount.pageSource?.file)
        assertEquals(html.virtualFile.path, mount.markupSource?.file)
        assertTrue(app.mounts.last().notes.any { "$selector.init" in it && "$selector.additionalRoutes" in it })

        val json = request.render(requestSnapshot)
        val response = JsonParser.parseString(json).asJsonObject
        assertEquals(1, response["schemaVersion"].asInt)
        val routes = response["routes"].asJsonObject
        assertEquals(1, routes["schemaVersion"].asInt)
        assertFalse(routes["runtimeRoutesComplete"].asBoolean)
        val serializedApp = routes["applications"].asJsonArray.single().asJsonObject
        assertEquals(selector, serializedApp["className"].asString)
        assertEquals(listOf("/bean", "/conditional"), serializedApp["mounts"].asJsonArray.map { it.asJsonObject["path"].asString })
        assertEquals(json, request.render(request.capture(project, applicationClass = selector)))
        request.requireCurrent(project, requestSnapshot)
        assertEquals(contents, java.text)
        assertEquals(stamp, java.virtualFile.modificationStamp)
        assertTrue(PsiDocumentManager.getInstance(project).uncommittedDocuments.isEmpty())
    }

    fun testLookalikeAnnotationsAndIndirectOrConditionalFactoriesAreOutsideDiscovery() {
        pageFile()
        config("""
            @interface Bean {}
            @Bean WebApplication lookalike() { return ${application("/pretend")}; }
            WebApplication unannotated() { return ${application("/plain")}; }
            @org.springframework.context.annotation.Bean
            WebApplication local() { WebApplication app = ${application("/local")}; return app; }
            @org.springframework.context.annotation.Bean
            WebApplication conditional(boolean active) { if (active) return ${application("/branch")}; return null; }
            @org.springframework.context.annotation.Bean
            Object unrelatedReturnType() { return ${application("/object")}; }
        """.trimIndent())
        assertTrue(request.capture(project).routes.applications.isEmpty())
    }

    fun testOverloadedBeansHaveSeparateSourceDeclarationsAndObserveTheSourceSetFilter() {
        pageFile()
        val testRoot = myFixture.tempDirFixture.findOrCreateDir("src/test/java")
        PsiTestUtil.addSourceRoot(module, testRoot, JavaSourceRootType.TEST_SOURCE)
        config("""
            @org.springframework.context.annotation.Bean
            WebApplication application() { return ${application("/test-a")}; }
            @org.springframework.context.annotation.Bean
            WebApplication application(String input) { return ${application("/test-b")}; }
        """.trimIndent(), "test")

        assertTrue(request.capture(project).routes.applications.isEmpty())
        val apps = request.capture(project, applicationClass = selector, sourceSet = "test").routes.applications
        assertEquals(listOf("/test-a", "/test-b"), apps.map { it.mounts.single().path })
        assertEquals(2, apps.map { it.source }.distinct().size)
        assertEquals(2, apps.map { it.mounts.single().id }.distinct().size)
    }

    fun testRemovingTheBeanAnnotationMakesPriorRoutesStaleAndRefreshDropsThem() {
        pageFile()
        val config = config("""
            @org.springframework.context.annotation.Bean
            WebApplication application() { return ${application("/old")}; }
        """.trimIndent())
        val snapshot = request.capture(project, applicationClass = selector)
        val documents = PsiDocumentManager.getInstance(project)
        val document = requireNotNull(documents.getDocument(config))
        WriteCommandAction.runWriteCommandAction(project) {
            document.setText(document.text.replace("@org.springframework.context.annotation.Bean", ""))
        }
        try {
            request.requireCurrent(project, snapshot)
            fail("Expected the removed bean annotation to make the snapshot stale")
        } catch (failure: PageAnalysisFailure) {
            assertEquals("STALE_SNAPSHOT", failure.code)
        }
        documents.commitAllDocuments()
        assertTrue(request.capture(project).routes.applications.isEmpty())
        try {
            request.capture(project, applicationClass = "example.Config#application#other")
            fail("Expected malformed bean selector to fail validation")
        } catch (failure: PageAnalysisFailure) {
            assertEquals("INVALID_REQUEST", failure.code)
        }
    }
}
