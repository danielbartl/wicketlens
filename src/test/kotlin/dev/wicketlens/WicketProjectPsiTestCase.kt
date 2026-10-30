package dev.wicketlens

import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.psi.PsiJavaFile
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.PsiTestUtil
import org.jetbrains.jps.model.java.JavaResourceRootType
import org.jetbrains.jps.model.java.JavaSourceRootType

abstract class WicketProjectPsiTestCase : WicketJavaPsiTestCase() {
    override fun getProjectDescriptor(): LightProjectDescriptor = DESCRIPTOR

    override fun setUp() {
        super.setUp()
        ModuleRootModificationUtil.updateModel(module) { model ->
            model.contentEntries.forEach { entry ->
                entry.sourceFolders.forEach { entry.removeSourceFolder(it) }
                entry.excludeFolders.forEach { entry.removeExcludeFolder(it) }
            }
        }
        PsiTestUtil.addSourceRoot(module, myFixture.tempDirFixture.findOrCreateDir("src/main/java"), JavaSourceRootType.SOURCE)
        PsiTestUtil.addSourceRoot(module, myFixture.tempDirFixture.findOrCreateDir("src/main/resources"), JavaResourceRootType.RESOURCE)
    }

    protected fun pageFile(name: String = "ExamplePage", body: String = "add(new Label(\"title\"));"): PsiJavaFile =
        myFixture.addFileToProject("src/main/java/example/$name.java", """
            package example;
            import org.apache.wicket.markup.html.WebPage;
            import org.apache.wicket.markup.html.basic.Label;
            public class $name extends WebPage { public $name() { $body } }
        """.trimIndent()) as PsiJavaFile

    protected fun companion(name: String = "ExamplePage", html: String = "<html><body><span wicket:id=\"title\"></span></body></html>") =
        myFixture.addFileToProject("src/main/resources/example/$name.html", html)

    companion object {
        private val DESCRIPTOR = object : LightProjectDescriptor() {
            override fun getSdk() = Java25ProjectDescriptor.getSdk()
        }
    }
}
