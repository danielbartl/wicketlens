package dev.wicketlens

import com.intellij.testFramework.PsiTestUtil
import java.io.File

/** Spring integration is attached only to Spring fixtures; ordinary Wicket fixtures stay Spring-free. */
abstract class WicketSpringPsiTestCase : WicketProjectPsiTestCase() {
    override fun setUp() {
        super.setUp()
        val wicket = System.getProperty("wicketlens.test.libraries").split(File.pathSeparator).toSet()
        System.getProperty("wicketlens.test.springLibraries").split(File.pathSeparator).filterNot { it in wicket }
            .forEachIndexed { index, path ->
                val jar = File(path)
                PsiTestUtil.addLibrary(testRootDisposable, module, "Spring fixture $index", jar.parent, jar.name)
            }
    }
}
