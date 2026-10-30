package dev.wicketlens.analysis

import com.intellij.psi.*
import com.intellij.psi.util.InheritanceUtil
import com.intellij.openapi.progress.ProgressManager
import dev.wicketlens.model.Repetition

/** Default item construction only; custom item factories/lifecycle hooks need separate contracts. */
internal object WicketRepeaterContract {
    const val LIST = "org.apache.wicket.markup.html.list.ListView"
    const val ITEM = "org.apache.wicket.markup.html.list.ListItem"
    const val REPEATING = "org.apache.wicket.markup.repeater.RepeatingView"
    const val ABSTRACT = "org.apache.wicket.markup.repeater.AbstractRepeater"

    fun kind(type: PsiClass): Repetition? = when {
        InheritanceUtil.isInheritor(type, LIST) -> Repetition.LIST_VIEW
        InheritanceUtil.isInheritor(type, REPEATING) -> Repetition.REPEATING_VIEW
        else -> null
    }

    fun callback(method: PsiMethod, kind: Repetition): Boolean = !method.hasModifierProperty(PsiModifier.STATIC) &&
        method.returnType?.canonicalText == "void" && when (kind) {
            Repetition.LIST_VIEW -> method.name == "populateItem" && method.parameterList.parametersCount == 1 &&
                (method.parameterList.parameters.single().type as? PsiClassType)?.resolve()?.qualifiedName == ITEM &&
                method.findDeepestSuperMethods().any { it.containingClass?.qualifiedName == LIST }
            Repetition.REPEATING_VIEW -> method.name == "onPopulate" && method.parameterList.parametersCount == 0 &&
                method.findDeepestSuperMethods().any { it.containingClass?.qualifiedName == ABSTRACT }
        }

    fun accepts(type: PsiClass, kind: Repetition): Boolean = type.fields.isEmpty() && type.initializers.isEmpty() &&
        type.methods.all { if (it.isConstructor) type is PsiAnonymousClass || forwarding(it) else it.hasModifierProperty(PsiModifier.STATIC) ||
            it.hasModifierProperty(PsiModifier.PRIVATE) && it.findDeepestSuperMethods().isEmpty() || callback(it, kind) }

    private fun forwarding(method: PsiMethod): Boolean {
        val statement = method.body?.statements?.filterNot { it is PsiEmptyStatement }?.singleOrNull() as? PsiExpressionStatement ?: return false
        val call = statement.expression as? PsiMethodCallExpression ?: return false
        return call.methodExpression.referenceName in setOf("super", "this") && call.argumentList.expressions.all { argument(it, method, 0) }
    }

    private fun argument(expression: PsiExpression, constructor: PsiMethod, depth: Int): Boolean {
        ProgressManager.checkCanceled()
        if (depth > 12) return false
        return when (expression) {
            is PsiLiteralExpression -> true
            is PsiReferenceExpression -> expression.qualifierExpression == null &&
                (expression.resolve() as? PsiParameter)?.declarationScope == constructor
            is PsiMethodCallExpression -> {
                val target = expression.resolveMethod() ?: return false
                val allowed = when (target.containingClass?.qualifiedName) {
                    "java.util.List" -> target.name == "of"
                    "java.util.Arrays" -> target.name == "asList"
                    "java.util.Collections" -> target.name in setOf("emptyList", "singletonList")
                    "org.apache.wicket.model.Model" -> target.name in setOf("of", "ofList")
                    else -> false
                }
                allowed && target.hasModifierProperty(PsiModifier.STATIC) && expression.resolveMethodGenerics().isValidResult &&
                    (expression.methodExpression.qualifierExpression?.let { (it as? PsiReferenceExpression)?.resolve() is PsiClass } != false) &&
                    expression.argumentList.expressions.all { argument(it, constructor, depth + 1) }
            }
            else -> false
        }
    }
}
