package dev.wicketlens

import com.google.gson.JsonParser
import com.intellij.psi.PsiJavaFile
import com.intellij.testFramework.PsiTestUtil
import dev.wicketlens.mcp.WicketMcpReport
import dev.wicketlens.mcp.WicketPageAnalysisRequest
import dev.wicketlens.spring.DependencyLookup
import dev.wicketlens.spring.WicketGuiceSetup
import dev.wicketlens.spring.WicketProjectDependencyAnalyzer
import java.io.File

class WicketGuiceDependenciesTest : WicketProjectPsiTestCase() {
    private lateinit var page: PsiJavaFile

    override fun setUp() {
        super.setUp()
        System.getProperty("wicketlens.test.guiceLibraries").split(File.pathSeparator).forEachIndexed { index, path ->
            val jar = File(path)
            PsiTestUtil.addLibrary(testRootDisposable, module, "Guice fixture $index", jar.parent, jar.name)
        }
        myFixture.addFileToProject("src/main/java/example/Service.java", "package example; public interface Service {}")
        page = myFixture.addFileToProject("src/main/java/example/ExamplePage.java", """
            package example;
            public class ExamplePage extends org.apache.wicket.markup.html.WebPage {
                @com.google.inject.Inject private Service services;
            }
        """.trimIndent()) as PsiJavaFile
    }

    private fun app(statement: String) = myFixture.addFileToProject("src/main/java/example/App.java", """
        package example;
        public class App extends org.apache.wicket.protocol.http.WebApplication {
            protected void init() { super.init(); $statement }
            public Class<? extends org.apache.wicket.Page> getHomePage() { return ExamplePage.class; }
        }
    """.trimIndent())

    private fun capture() = WicketPageAnalysisRequest().capture(project, "example.ExamplePage", view = WicketMcpReport.DEPENDENCIES)

    fun testExplicitGuiceRegistrationAddsBootstrapEvidenceToBothScopes() {
        val registration = app("getComponentInstantiationListeners().add(new org.apache.wicket.guice.GuiceComponentInjector(this));")
        assertEquals(registration.virtualFile.path, WicketGuiceSetup.forFile(page)?.file)
        val captured = capture()
        val dependencies = captured.dependencies!!
        val field = dependencies.owners.single().fields.single()
        assertEquals("services", field.name)
        assertEquals(DependencyLookup.GUICE, field.lookup)
        assertEquals(registration.virtualFile.path, field.injectorLocation?.file)
        assertTrue(captured.version.sources.any { it.path == registration.virtualFile.path })
        val response = JsonParser.parseString(WicketPageAnalysisRequest().render(captured, WicketMcpReport.DEPENDENCIES)).asJsonObject
        val exported = response["dependencies"].asJsonObject["owners"].asJsonArray.single().asJsonObject["fields"].asJsonArray.single().asJsonObject
        assertEquals("GUICE", exported["lookup"].asString)
        assertEquals(registration.virtualFile.path, exported["injectorRegistration"].asJsonObject["file"].asString)
        val projectWide = WicketProjectDependencyAnalyzer.analyze(project, module.name, "main")
        assertEquals(field.location, projectWide.fields.single().field.location)
        assertTrue(registration.virtualFile.path in projectWide.sourceFiles)
        assertEquals(0, dependencies.springFieldCount)
        val query = WicketPageAnalysisRequest()
        val planning = query.capture(project, "example.ExamplePage", view = WicketMcpReport.TEST_PLAN)
        val plan = JsonParser.parseString(query.render(planning, WicketMcpReport.TEST_PLAN)).asJsonObject["testPlan"].asJsonObject
        val dependency = plan["dependencyGraph"].asJsonObject["owners"].asJsonArray.single().asJsonObject
            .getAsJsonArray("fields").single().asJsonObject
        assertEquals("MANUAL", dependency["testBinding"].asString)
        assertEquals("GUICE", dependency["lookup"].asString)
        assertEquals(exported["injectorRegistration"], dependency["injectorRegistration"])
        assertTrue(plan["source"].asString.contains("configureAdditionalDependencies()"))
        assertTrue(planning.version.sources.any { it.path == registration.virtualFile.path })
    }

    fun testClasspathAndAnnotationAloneDoNotEstablishAnInjector() {
        assertEquals(0, capture().dependencies!!.fieldCount)
    }

    fun testConditionalBootstrapIsWithheld() {
        app("if (Boolean.getBoolean(\"inject\")) getComponentInstantiationListeners().add(new org.apache.wicket.guice.GuiceComponentInjector(this));")
        assertEquals(0, capture().dependencies!!.fieldCount)
    }
}
