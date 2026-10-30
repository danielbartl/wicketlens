package dev.wicketlens.spring

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.progress.ProgressManager
import com.intellij.psi.*

/** Positive source-type evidence only. Unknown and absent bean names never produce missing-bean diagnostics. */
class WicketSpringBeanTypeInspection : LocalInspectionTool() {
    override fun runForWholeFile() = true
    override fun isAvailableForFile(file: PsiFile) = WicketSpringSupport.supports(file)
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor = object : PsiElementVisitor() {
        override fun visitFile(file: PsiFile) {
            if (!WicketSpringSupport.supports(file)) return
            file.accept(object : JavaRecursiveElementWalkingVisitor() {
                override fun visitElement(element: PsiElement) { ProgressManager.checkCanceled(); super.visitElement(element) }
                override fun visitLiteralExpression(expression: PsiLiteralExpression) {
                    super.visitLiteralExpression(expression)
                    val use = WicketSpringBeanNames.use(expression) ?: return
                    val message = WicketSpringBeanNames.mismatch(use) ?: return
                    if (!isSuppressedFor(expression)) holder.registerProblem(expression, message, ProblemHighlightType.GENERIC_ERROR_OR_WARNING)
                }
            })
        }
    }
}
