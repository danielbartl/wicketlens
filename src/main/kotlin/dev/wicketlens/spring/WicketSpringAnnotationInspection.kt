package dev.wicketlens.spring

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.progress.ProgressManager
import com.intellij.psi.*
import com.intellij.psi.util.InheritanceUtil

/** Bounded annotation and injection-path checks; absence of a recognized path is never a runtime error claim. */
class WicketSpringAnnotationInspection : LocalInspectionTool() {
    override fun runForWholeFile() = true
    override fun isAvailableForFile(file: PsiFile) = WicketSpringSupport.supports(file)

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor = object : PsiElementVisitor() {
        override fun visitFile(file: PsiFile) {
            if (!WicketSpringSupport.supports(file)) return
            fun report(annotation: PsiAnnotation, message: String) {
                val anchor = annotation.nameReferenceElement ?: annotation
                if (!isSuppressedFor(anchor)) holder.registerProblem(anchor, message, ProblemHighlightType.GENERIC_ERROR_OR_WARNING)
            }
            file.accept(object : JavaRecursiveElementWalkingVisitor() {
                override fun visitElement(element: PsiElement) {
                    ProgressManager.checkCanceled()
                    super.visitElement(element)
                }

                override fun visitField(field: PsiField) {
                    super.visitField(field)
                    val owner = field.containingClass ?: return
                    val bean = WicketSpringSupport.annotation(field, WicketSpringSupport.BEAN)
                    val inject = WicketSpringSupport.annotation(field, "jakarta.inject.Inject")
                    val autowired = WicketSpringSupport.annotation(field, "org.springframework.beans.factory.annotation.Autowired")
                    if (bean == null && inject == null && autowired == null) return
                    val registered by lazy { WicketSpringSetup.forFile(file).registered }
                    val paths by lazy { WicketSpringInjectionPaths.forClass(owner) }
                    val supported = bean ?: inject?.takeIf {
                        registered && !paths.unknown && !paths.managed && !paths.spring &&
                            (WicketSpringSupport.automatic(owner) || paths.wicket ||
                                InheritanceUtil.isInheritor(owner, "org.apache.wicket.model.IModel") ||
                                InheritanceUtil.isInheritor(owner, "org.apache.wicket.Session") ||
                                InheritanceUtil.isInheritor(owner, "org.apache.wicket.validation.IValidator"))
                    }
                    if (supported != null) {
                        when {
                            field.hasModifierProperty(PsiModifier.STATIC) -> report(supported,
                                "Wicket's Spring injector does not support static injection fields. Use an instance field or review explicit application wiring.")
                            field.type is PsiPrimitiveType -> report(supported,
                                "Wicket's Spring injector only fills null fields, so a primitive field cannot receive injection. Use a suitable reference type.")
                            !WicketSpringSupport.automatic(owner) -> {
                                if (!paths.wicket && !paths.unknown) report(supported,
                                    "Wicket's Spring listener only injects Components and Behaviors automatically. No manual Wicket injection path was recognized for this type; verify constructor or caller injection with Injector.get().inject(target).")
                            }
                        }
                    } else if (autowired != null && WicketSpringSupport.automatic(owner) && registered) {
                        if (!paths.spring && !paths.managed && !paths.unknown) report(autowired,
                            "Wicket's Spring listener does not process @Autowired. No Spring autowiring path was recognized for this component or behavior; use @SpringBean or verify explicit Spring management.")
                    }
                }
            })
        }
    }
}
