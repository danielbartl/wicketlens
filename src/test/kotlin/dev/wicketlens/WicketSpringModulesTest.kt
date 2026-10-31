package dev.wicketlens

import com.intellij.openapi.module.JavaModuleType
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.psi.PsiJavaFile
import com.intellij.testFramework.PsiTestUtil
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase
import dev.wicketlens.spring.WicketSpringSetup
import org.jetbrains.jps.model.java.JavaSourceRootType
import java.io.File

/** A real project fixture verifies that dependency-module applications cannot select local proxy policy. */
class WicketSpringModulesTest : JavaCodeInsightFixtureTestCase() {
    override fun toAddSourceRoot() = false

    fun testDependencyApplicationDoesNotActivateLocalSetupOrMakeItAmbiguous() {
        val other = setupModules()
        ModuleRootModificationUtil.addDependency(module, other)
        val appText = """
            package example;
            public class App extends org.apache.wicket.protocol.http.WebApplication {
                protected void init() {
                    super.init();
                    getComponentInstantiationListeners().add(new org.apache.wicket.spring.injection.annot.SpringComponentInjector(this));
                }
                public Class<? extends org.apache.wicket.Page> getHomePage() { return org.apache.wicket.markup.html.WebPage.class; }
            }
        """.trimIndent()
        myFixture.addFileToProject("other/java/example/App.java", appText)
        val page = myFixture.addFileToProject("own/java/example/ExamplePage.java", "package example; public class ExamplePage extends org.apache.wicket.markup.html.WebPage {}") as PsiJavaFile
        assertFalse(WicketSpringSetup.forFile(page).registered)
        myFixture.addFileToProject("own/java/example/OwnApp.java", appText.replace("class App", "class OwnApp"))
        assertTrue(WicketSpringSetup.forFile(page).registered)
        assertEquals("example.OwnApp", WicketSpringSetup.forFile(page).application)
    }

    fun testAnotherModulesCallerCannotHideMissingLocalInjection() {
        val other = setupModules()
        ModuleRootModificationUtil.addDependency(other, module)
        val helper = myFixture.addFileToProject("own/java/example/Helper.java", "package example; public class Helper { @org.apache.wicket.spring.injection.annot.SpringBean Object service; }")
        myFixture.addFileToProject("other/java/example/Factory.java", "package example; public class Factory { void wire(Helper target) { org.apache.wicket.injection.Injector.get().inject(target); } }")
        val holder = com.intellij.codeInspection.ProblemsHolder(com.intellij.codeInspection.InspectionManager.getInstance(project), helper, false)
        dev.wicketlens.spring.WicketSpringAnnotationInspection().buildVisitor(holder, false).visitFile(helper)
        assertEquals(1, holder.results.size)
    }

    private fun setupModules(): com.intellij.openapi.module.Module {
        fun dir(path: String) = myFixture.tempDirFixture.findOrCreateDir(path)
        PsiTestUtil.addContentRoot(module, dir("own"))
        PsiTestUtil.addSourceRoot(module, dir("own/java"), JavaSourceRootType.SOURCE)
        val other = PsiTestUtil.addModule(project, JavaModuleType.getModuleType(), "other", dir("other"))
        val sdk = com.intellij.openapi.projectRoots.JavaSdk.getInstance().createJdk("WicketLens modules test JDK", System.getProperty("java.home"), false)
        com.intellij.openapi.application.ApplicationManager.getApplication().runWriteAction {
            com.intellij.openapi.projectRoots.ProjectJdkTable.getInstance().addJdk(sdk, testRootDisposable)
        }
        ModuleRootModificationUtil.setModuleSdk(module, sdk)
        ModuleRootModificationUtil.setModuleSdk(other, sdk)
        PsiTestUtil.addSourceRoot(other, dir("other/java"), JavaSourceRootType.SOURCE)
        for (target in listOf(module, other)) {
            val paths = listOf("wicketlens.test.libraries", "wicketlens.test.springLibraries")
                .flatMap { System.getProperty(it).split(File.pathSeparator) }.distinct()
            paths.forEachIndexed { i, path ->
                val jar = File(path)
                PsiTestUtil.addLibrary(testRootDisposable, target, "Fixture $i", jar.parent, jar.name)
            }
        }
        return other
    }

    fun testDependencyModuleBeansDoNotLeakIntoTheLocalCatalog() {
        val other = setupModules()
        ModuleRootModificationUtil.addDependency(module, other)
        myFixture.addFileToProject("other/java/example/Config.java", "package example; public class Config { @org.springframework.context.annotation.Bean String foreign() { return \"value\"; } }")
        val java = myFixture.addFileToProject("own/java/example/Page.java", "package example; public class Page extends org.apache.wicket.markup.html.WebPage { @org.apache.wicket.spring.injection.annot.SpringBean(name=\"foreign\") String value; }")
        assertTrue(dev.wicketlens.spring.WicketSpringBeanNames.catalog(java).beans.isEmpty())
    }

    fun testInheritedDependencyFieldsUseTheAnalyzedPagesBeanCatalog() {
        val other = setupModules()
        ModuleRootModificationUtil.addDependency(module, other)
        val base = myFixture.addFileToProject("other/java/example/BasePage.java", "package example; public class BasePage extends org.apache.wicket.markup.html.WebPage { @org.apache.wicket.spring.injection.annot.SpringBean protected String shared; }")
        myFixture.addFileToProject("other/java/example/ForeignConfig.java", "package example; public class ForeignConfig { @org.springframework.context.annotation.Bean String foreign() { return \"foreign\"; } }")
        val config = myFixture.addFileToProject("own/java/example/LocalConfig.java", "package example; public class LocalConfig { @org.springframework.context.annotation.Bean String local() { return \"local\"; } }")
        val page = myFixture.addFileToProject("own/java/example/ExamplePage.java", "package example; public class ExamplePage extends BasePage {}") as PsiJavaFile
        val report = dev.wicketlens.analysis.WicketPageAnalyzer().analyze(page.classes.single())!!
        val result = dev.wicketlens.spring.WicketDependencyAnalyzer().analyze(project, report)
        val field = result.owners.single().fields.single()
        assertEquals(base.virtualFile.path, field.location.file)
        assertEquals(listOf("local"), field.candidates.single().names)
        assertEquals(config.virtualFile.path, field.candidates.single().location.file)
    }
}
