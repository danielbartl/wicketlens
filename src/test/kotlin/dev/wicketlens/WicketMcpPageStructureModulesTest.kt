package dev.wicketlens

import com.intellij.openapi.module.JavaModuleType
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.testFramework.PsiTestUtil
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase
import dev.wicketlens.mcp.*
import java.io.File

class WicketMcpPageStructureModulesTest : JavaCodeInsightFixtureTestCase() {
    override fun toAddSourceRoot() = false
    private fun modules() {
        val own = myFixture.tempDirFixture.findOrCreateDir("own")
        PsiTestUtil.addContentRoot(module, own)
        PsiTestUtil.addSourceRoot(module, myFixture.tempDirFixture.findOrCreateDir("own/java"))
        val other = PsiTestUtil.addModule(project, JavaModuleType.getModuleType(), "other", myFixture.tempDirFixture.findOrCreateDir("other"))
        PsiTestUtil.addSourceRoot(other, myFixture.tempDirFixture.findOrCreateDir("other/java"))
        val sdk = com.intellij.openapi.projectRoots.JavaSdk.getInstance().createJdk("MCP fixture SDK", System.getProperty("java.home"), false)
        com.intellij.openapi.application.ApplicationManager.getApplication().runWriteAction {
            com.intellij.openapi.projectRoots.ProjectJdkTable.getInstance().addJdk(sdk, testRootDisposable)
        }
        for (target in listOf(module, other)) {
            ModuleRootModificationUtil.setModuleSdk(target, sdk)
            (System.getProperty("wicketlens.test.libraries").split(File.pathSeparator) +
                System.getProperty("wicketlens.test.springLibraries").split(File.pathSeparator)).distinct().forEachIndexed { i, path ->
                val jar = File(path)
                PsiTestUtil.addLibrary(testRootDisposable, target, "MCP fixture $i", jar.parent, jar.name)
            }
        }
        for (directory in listOf("own", "other")) myFixture.addFileToProject("$directory/java/example/ExamplePage.java", """
            package example; public class ExamplePage extends org.apache.wicket.markup.html.WebPage {
              @org.apache.wicket.spring.injection.annot.SpringBean(name="$directory") Object dependency = null;
              public ExamplePage() { add(new org.apache.wicket.markup.html.basic.Label("$directory")); }
            }
        """.trimIndent())
        for (directory in listOf("own", "other")) myFixture.addFileToProject("$directory/java/example/Config.java", """
            package example; @org.springframework.context.annotation.Configuration public class Config {
              @org.springframework.context.annotation.Bean("$directory") String service(){return "$directory";}
            }
        """.trimIndent())
    }
    fun testDuplicateClassesAcrossModulesAreRejected() {
        modules()
        try { WicketPageStructureRequest().capture(project, "example.ExamplePage"); fail("Expected ambiguity") }
        catch (failure: PageStructureFailure) { assertEquals("AMBIGUOUS_CLASS", failure.code) }
    }
    fun testExactModuleSelectsItsOwnDeclaration() {
        modules()
        val result = WicketPageStructureRequest().capture(project, "example.ExamplePage", "other")
        assertEquals("other", result.moduleName)
        assertEquals("other", result.report.nodes.single { it.wicketId != null }.wicketId)
    }
    fun testDependencyToolSelectsOnlyItsOwnModulesBeanCandidates() {
        modules()
        val snapshot = WicketPageAnalysisRequest().capture(project, "example.ExamplePage", "other", view=WicketMcpReport.DEPENDENCIES)
        assertEquals(listOf("other"), snapshot.dependencies!!.owners.single().fields.single().candidates.single().names)
    }
    fun testDependencyToolRejectsAmbiguousModuleSelection() {
        modules()
        try { WicketPageAnalysisRequest().capture(project,"example.ExamplePage",view=WicketMcpReport.DEPENDENCIES); fail("Expected ambiguity") }
        catch (failure: PageAnalysisFailure) { assertEquals("AMBIGUOUS_CLASS", failure.code) }
    }
    fun testSpringInspectionScopeDoesNotIncludeTheOtherModulesFindings() {
        modules()
        myFixture.enableInspections(dev.wicketlens.spring.WicketSpringInjectionInspection())
        val snapshot = WicketPageAnalysisRequest().capture(project,"example.ExamplePage","other",view=WicketMcpReport.SPRING_INSPECTIONS)
        val result = snapshot.springInspections!!
        assertEquals(result.toString(),1,result.findings.size)
        assertTrue(result.findings.single().location.file.contains("/other/java/"))
        assertTrue(result.sourceFiles.all { it.contains("/other/java/") })
    }
}
