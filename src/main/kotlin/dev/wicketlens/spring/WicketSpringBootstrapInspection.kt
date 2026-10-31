package dev.wicketlens.spring

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.psi.*
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.AnnotatedElementsSearch
import com.intellij.util.Processor

/** Reviewed zero/multiple registrations only; unknown bootstrap never becomes a missing-injector finding. */
class WicketSpringBootstrapInspection : LocalInspectionTool() {
    override fun runForWholeFile() = true
    override fun isAvailableForFile(file: PsiFile) = WicketSpringSupport.supports(file)
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor = object : PsiElementVisitor() {
        override fun visitFile(file: PsiFile) {
            if (!WicketSpringSupport.supports(file)) return
            val setup = WicketSpringSetup.forFile(file)
            val count = setup.registrationCount ?: return
            if (count == 1) return
            val app = (file as? PsiJavaFile)?.classes?.singleOrNull { it.qualifiedName == setup.reviewedApplication } ?: return
            if (count == 0 && setup.constructedInjectors == 0 && !hasInjectionIntent(file)) return
            val anchor = app.nameIdentifier ?: return
            if (!isSuppressedFor(anchor)) holder.registerProblem(anchor, if (count == 0)
                "The reviewed application setup has no Spring component-listener registration. Verify external wiring before adding SpringComponentInjector to getComponentInstantiationListeners(); constructing it alone only installs behavior injection."
            else "The reviewed application setup registers multiple Spring component injectors. Verify that these contexts/listeners are intentional; no proxy policy is inferred.", ProblemHighlightType.GENERIC_ERROR_OR_WARNING)
        }
    }

    private fun hasInjectionIntent(file: PsiFile): Boolean {
        val index = ProjectFileIndex.getInstance(file.project)
        val module = index.getModuleForFile(file.virtualFile) ?: return false
        val annotation = JavaPsiFacade.getInstance(file.project).findClass(WicketSpringSupport.BEAN, file.resolveScope) ?: return false
        val test = index.isInTestSourceContent(file.virtualFile)
        var found = false
        var count = 0
        AnnotatedElementsSearch.searchPsiFields(annotation, GlobalSearchScope.moduleScope(module)).forEach(Processor { field ->
            ProgressManager.checkCanceled()
            val virtual = field.containingFile?.virtualFile
            if (virtual != null && index.isInTestSourceContent(virtual) == test && WicketSpringSupport.automatic(field.containingClass)) found = true
            ++count < 256 && !found
        })
        return found
    }
}
