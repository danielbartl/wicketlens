package dev.wicketlens

import com.intellij.openapi.module.JavaModuleType
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.testFramework.PsiTestUtil
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase
import dev.wicketlens.routes.WicketRoutesRequest
import java.io.File

class WicketRoutesModulesTest : JavaCodeInsightFixtureTestCase() {
    override fun toAddSourceRoot() = false

    fun testSameClassNameInDifferentModulesKeepsItsOwnMounts() {
        fun dir(path: String) = myFixture.tempDirFixture.findOrCreateDir(path)
        PsiTestUtil.addContentRoot(module, dir("first"))
        val second = PsiTestUtil.addModule(project, JavaModuleType.getModuleType(), "second", dir("second"))
        val libraries = listOf("wicketlens.test.libraries", "wicketlens.test.springLibraries")
            .flatMap { requireNotNull(System.getProperty(it)).split(File.pathSeparator) }.distinct()
        for ((folder, owner) in listOf("first" to module, "second" to second)) {
            PsiTestUtil.addSourceRoot(owner, dir("$folder/java"))
            ModuleRootModificationUtil.setModuleSdk(owner, Java25ProjectDescriptor.getSdk())
            libraries.forEachIndexed { index, path ->
                val jar = File(path)
                PsiTestUtil.addLibrary(testRootDisposable, owner, "Wicket fixture $index", jar.parent, jar.name)
            }
            myFixture.addFileToProject("$folder/java/example/ExamplePage.java",
                "package example; public class ExamplePage extends org.apache.wicket.markup.html.WebPage {}")
            myFixture.addFileToProject("$folder/java/example/App.java", """
                package example;
                public class App extends org.apache.wicket.protocol.http.WebApplication {
                    @Override public Class<? extends org.apache.wicket.Page> getHomePage() { return ExamplePage.class; }
                    @Override protected void init() { super.init(); mountPage("/$folder", ExamplePage.class); }
                }
                class Config {
                    @org.springframework.context.annotation.Bean
                    org.apache.wicket.protocol.http.WebApplication application() {
                        return new org.apache.wicket.protocol.http.WebApplication() {
                            @Override public Class<? extends org.apache.wicket.Page> getHomePage() { return ExamplePage.class; }
                            @Override protected void init() { super.init(); mountPage("/bean-$folder", ExamplePage.class); }
                        };
                    }
                }
            """.trimIndent())
        }

        val request = WicketRoutesRequest()
        val routes = request.capture(project).routes
        assertFalse(routes.truncated)
        assertEquals(4, routes.applications.size)
        val byModule = routes.applications.groupBy { it.moduleName }
        assertEquals(setOf(module.name, second.name), byModule.keys)
        for ((owner, folder) in listOf(module.name to "first", second.name to "second")) {
            val applications = byModule.getValue(owner)
            assertEquals(mapOf("example.App" to "/$folder", "example.Config#application" to "/bean-$folder"),
                applications.associate { it.className to it.mounts.single().path })
            val page = myFixture.findFileInTempDir("$folder/java/example/ExamplePage.java")
            for (application in applications) {
                val mount = application.mounts.single()
                assertEquals("example.ExamplePage", mount.pageClass)
                assertEquals(page.path, mount.pageSource?.file)
            }
        }
        val matched = request.capture(project, second.name, "example.Config#application").routes.applications.single()
        assertEquals(second.name, matched.moduleName)
        assertEquals("/bean-second", matched.mounts.single().path)
    }
}
