package dev.wicketlens

import com.intellij.codeInspection.InspectionManager
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.search.GlobalSearchScope
import dev.wicketlens.spring.WicketSpringInjectionInspection
import dev.wicketlens.spring.WicketSpringSerializationInspection
import dev.wicketlens.spring.WicketSpringAnnotationInspection

class WicketSpringAbsentTest : WicketProjectPsiTestCase() {
    fun testOrdinaryWicketProjectDoesNotAcquireASpringDependency() {
        assertNull(JavaPsiFacade.getInstance(project).findClass("org.apache.wicket.spring.injection.annot.SpringBean", GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(module)))
        assertSilent(pageFile())
    }

    fun testUnresolvedSpringAnnotationDoesNotEnableInspections() {
        assertSilent(pageFile(body = "").also { file ->
            com.intellij.openapi.command.WriteCommandAction.runWriteCommandAction(project) {
                val replacement = JavaPsiFacade.getElementFactory(project).createFieldFromText(
                    "@org.apache.wicket.spring.injection.annot.SpringBean private transient Object orders = null;", file)
                file.classes.single().add(replacement)
            }
        })
    }

    fun testDependencyDiscoveryRequiresNoSpringRuntimeOrIdePlugin() {
        val java = pageFile()
        val report = dev.wicketlens.analysis.WicketPageAnalyzer().analyze(java.classes.single())!!
        val snapshot = dev.wicketlens.spring.WicketDependencyAnalyzer().analyze(project, report)
        assertTrue(snapshot.owners.isEmpty())
        assertTrue(snapshot.sourceFiles.isEmpty())
    }

    fun testScaffoldGeneratorExplainsAbsentSpringWithoutLoadingIt() {
        val java = pageFile()
        assertThrows(dev.wicketlens.analysis.AnalysisUnavailable::class.java) {
            dev.wicketlens.spring.WicketSpringTestGenerator().prepare(project, java.virtualFile.url, -1)
        }
    }

    private fun assertSilent(file: com.intellij.psi.PsiFile) {
        listOf(WicketSpringInjectionInspection(), WicketSpringSerializationInspection(), WicketSpringAnnotationInspection(), dev.wicketlens.spring.WicketSpringBeanTypeInspection(), dev.wicketlens.spring.WicketSpringBootstrapInspection()).forEach { tool ->
            val holder = ProblemsHolder(InspectionManager.getInstance(project), file, false)
            tool.buildVisitor(holder, false).visitFile(file)
            assertTrue(holder.results.isEmpty())
        }
    }
}
