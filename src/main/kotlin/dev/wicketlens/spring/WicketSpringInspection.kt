package dev.wicketlens.spring

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.progress.ProgressManager
import com.intellij.psi.*
import com.intellij.psi.util.InheritanceUtil

/** Native local/batch inspections with evidence gates, independent of the sidebar and IntelliJ Spring plugin. */
abstract class WicketSpringInspection(private val serialization: Boolean) : LocalInspectionTool() {
    override fun runForWholeFile() = true
    override fun isAvailableForFile(file: PsiFile) = WicketSpringSupport.supports(file)

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor = object : PsiElementVisitor() {
        override fun visitFile(file: PsiFile) {
            if (!WicketSpringSupport.supports(file)) return
            fun report(anchor: PsiElement, message: String) {
                if (!isSuppressedFor(anchor)) holder.registerProblem(anchor, message, ProblemHighlightType.GENERIC_ERROR_OR_WARNING)
            }
            file.accept(object : JavaRecursiveElementWalkingVisitor() {
                override fun visitElement(element: PsiElement) {
                    ProgressManager.checkCanceled()
                    super.visitElement(element)
                }

                override fun visitField(field: PsiField) {
                    super.visitField(field)
                    if (!WicketSpringSupport.injected(field, file)) return
                    if (!serialization) {
                        field.initializer?.takeUnless { WicketSpringSupport.repairedInitializer(field) }?.let {
                            report(it, "This initializer may overwrite a value supplied by Wicket's Spring construction listener. Keep injected fields uninitialized or verify explicit later injection.")
                        }
                        return
                    }
                    val owner = field.containingClass ?: return
                    val custom = WicketSpringSupport.customSerialization(owner)
                    if (field.hasModifierProperty(PsiModifier.TRANSIENT)) {
                        if (!custom) report(field.nameIdentifier ?: field,
                            "A transient Spring injection field may be null after page deserialization. Wicket's default Spring proxy is serializable; verify explicit reinjection.")
                    }
                    val type = (field.type as? PsiClassType)?.resolve() ?: return
                    if (type.isEnum || type.qualifiedName in DIRECT_TYPES) return
                    when (WicketSpringSetup.forFile(file).proxies) {
                        WicketSpringSetup.Proxies.WRAPPED -> if (!type.isInterface && type.hasModifierProperty(PsiModifier.FINAL)) {
                            report(field.typeElement ?: field, "The recognized Wicket Spring injector wraps dependencies in proxies, but this final concrete type cannot be subclass-proxied. Prefer a suitable interface type.")
                        }
                        WicketSpringSetup.Proxies.RAW -> if (!custom && !field.hasModifierProperty(PsiModifier.TRANSIENT) &&
                            !type.isInterface && type.hasModifierProperty(PsiModifier.FINAL) && !InheritanceUtil.isInheritor(type, "java.io.Serializable")) {
                            report(field.typeElement ?: field, "The recognized Wicket Spring injector disables proxy wrapping. A non-null instance of this final non-Serializable type cannot be serialized with the component.")
                        }
                        WicketSpringSetup.Proxies.UNKNOWN -> Unit
                    }
                }

                override fun visitAssignmentExpression(expression: PsiAssignmentExpression) {
                    super.visitAssignmentExpression(expression)
                    if (serialization) return
                    val field = WicketSpringSupport.constructionWrite(expression) ?: return
                    if (WicketSpringSupport.injected(field, file) && !WicketSpringSupport.repairedAssignment(expression)) {
                        report(expression.lExpression, "This construction-time assignment may overwrite a value supplied by Wicket's Spring listener. Verify that replacing the injected dependency is intentional.")
                    }
                }
            })
        }
    }

    private companion object {
        val DIRECT_TYPES = setOf("java.lang.String", "java.lang.Byte", "java.lang.Short", "java.lang.Integer", "java.lang.Long",
            "java.lang.Float", "java.lang.Double", "java.lang.Character", "java.lang.Boolean")
    }
}

class WicketSpringInjectionInspection : WicketSpringInspection(false)
class WicketSpringSerializationInspection : WicketSpringInspection(true)
