package dev.wicketlens

import com.intellij.psi.PsiJavaFile
import com.intellij.openapi.roots.LanguageLevelModuleExtension
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.pom.java.LanguageLevel
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.PsiTestUtil
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import dev.wicketlens.analysis.WicketJavaAnalyzer
import dev.wicketlens.model.PageReport
import java.io.File

abstract class WicketJavaPsiTestCase : LightJavaCodeInsightFixtureTestCase() {
    override fun getProjectDescriptor(): LightProjectDescriptor = Java25ProjectDescriptor

    override fun setUp() {
        super.setUp()
        ModuleRootModificationUtil.updateModel(module) { model ->
            model.getModuleExtension(LanguageLevelModuleExtension::class.java).languageLevel = LanguageLevel.JDK_25
        }
        // Actual Maven libraries are attached as PSI roots, not loaded as test JVM classes.
        val libraries = requireNotNull(System.getProperty("wicketlens.test.libraries"))
        libraries.split(File.pathSeparator).forEachIndexed { index, path ->
            val jar = File(path)
            PsiTestUtil.addLibrary(testRootDisposable, module, "Wicket fixture $index", jar.parent, jar.name)
        }
    }

    protected fun PageReport.children() = nodes.filter { it.nodeId != rootNodeId }

    protected fun analyze(
        body: String,
        base: String = "WebPage",
        constructorPrefix: String = "",
        members: String = "",
    ): PageReport {
        val file = myFixture.configureByText(
            "ExamplePage.java",
            """
            package example;
            import org.apache.wicket.markup.html.WebPage;
            import org.apache.wicket.markup.html.WebMarkupContainer;
            import org.apache.wicket.markup.html.basic.Label;
            import org.apache.wicket.markup.html.form.Form;
            import org.apache.wicket.markup.html.form.TextField;
            import org.apache.wicket.markup.html.panel.Panel;
            public class ExamplePage extends $base {
                public ExamplePage() {
                    $constructorPrefix
                    $body
                }
                $members
            }
            """.trimIndent(),
        ) as PsiJavaFile
        return requireNotNull(WicketJavaAnalyzer().analyze(file.classes.single()))
    }
}
