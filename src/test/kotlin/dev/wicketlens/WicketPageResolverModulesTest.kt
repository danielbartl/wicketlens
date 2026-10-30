package dev.wicketlens

import com.intellij.openapi.module.JavaModuleType
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.psi.PsiJavaFile
import com.intellij.testFramework.PsiTestUtil
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase
import dev.wicketlens.analysis.WicketPageResolver
import dev.wicketlens.model.MarkupResolutionStatus
import org.jetbrains.jps.model.java.JavaResourceRootType
import org.jetbrains.jps.model.java.JavaSourceRootType

/** Multiple modules require a real project fixture; light fixtures forbid module additions. */
class WicketPageResolverModulesTest : JavaCodeInsightFixtureTestCase() {
    override fun toAddSourceRoot(): Boolean = false

    fun testDependencyModulesResourcesAreNotCompanions() {
        fun dir(path: String) = myFixture.tempDirFixture.findOrCreateDir(path)
        PsiTestUtil.addContentRoot(module, dir("own"))
        PsiTestUtil.addSourceRoot(module, dir("own/java"), JavaSourceRootType.SOURCE)
        PsiTestUtil.addSourceRoot(module, dir("own/resources"), JavaResourceRootType.RESOURCE)

        val other = PsiTestUtil.addModule(project, JavaModuleType.getModuleType(), "other", dir("other"))
        PsiTestUtil.addSourceRoot(other, dir("other/resources"), JavaResourceRootType.RESOURCE)
        ModuleRootModificationUtil.addDependency(module, other)
        val file = myFixture.addFileToProject(
            "own/java/example/ExamplePage.java", "package example; public class ExamplePage {}",
        ) as PsiJavaFile
        myFixture.addFileToProject("other/resources/example/ExamplePage.html", "<html/>")
        val resolver = WicketPageResolver()
        val missing = resolver.resolve(file.classes.single())
        assertEquals(MarkupResolutionStatus.MISSING, missing.status)
        assertTrue(missing.candidatePaths.isEmpty())

        val markup = myFixture.addFileToProject("own/resources/example/ExamplePage.html", "<html/>")
        val paired = resolver.resolve(file.classes.single())
        assertEquals(MarkupResolutionStatus.PAIRED, paired.status)
        assertEquals(listOf(markup.virtualFile.path), paired.candidatePaths)
        assertEquals(markup.virtualFile.path, paired.markupPath)
    }
}
