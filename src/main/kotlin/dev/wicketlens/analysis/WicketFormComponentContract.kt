package dev.wicketlens.analysis

import com.intellij.openapi.progress.ProgressManager
import com.intellij.psi.*
import com.intellij.psi.util.PsiTreeUtil

/** A deliberately small isolation proof, independent of the constructor's ID parameter. */
internal object WicketFormComponentContract {
    fun accepts(owner: PsiClass, child: (PsiNewExpression) -> Boolean): Boolean {
        if (owner.initializers.isNotEmpty()) return false
        if (owner.fields.any { field ->
                val value = field.initializer ?: return@any false
                if (field.hasModifierProperty(PsiModifier.STATIC)) value !is PsiLiteralExpression
                else !scalar(value, owner) && !(stableField(field, owner) && value is PsiNewExpression && child(value))
            }) return false
        return owner.methods.all { method ->
            ProgressManager.checkCanceled()
            if (method.isConstructor) {
                val statements = method.body?.statements?.filterNot { it is PsiEmptyStatement } ?: return@all false
                val delegation = (statements.firstOrNull() as? PsiExpressionStatement)?.expression as? PsiMethodCallExpression
                delegation != null && delegation.methodExpression.referenceName in setOf("super", "this") &&
                    delegation.argumentList.expressions.all { argument ->
                        scalar(argument, owner) || argument is PsiReferenceExpression &&
                            argument.qualifierExpression == null && argument.resolve() is PsiParameter
                    } && statements.drop(1).all { setup(it, owner, child) }
            } else if (method.hasModifierProperty(PsiModifier.STATIC)) {
                // Uncalled static helpers do not run during construction.
                true
            } else if (method.name == "onInitialize" && method.parameterList.parametersCount == 0) {
                val statements = method.body?.statements?.filterNot { it is PsiEmptyStatement } ?: return@all false
                val call = (statements.firstOrNull() as? PsiExpressionStatement)?.expression as? PsiMethodCallExpression
                call != null && call.methodExpression.referenceName == "onInitialize" &&
                    call.methodExpression.qualifierExpression is PsiSuperExpression &&
                    (call.methodExpression.qualifierExpression as PsiSuperExpression).qualifier == null &&
                    call.argumentList.expressionCount == 0 && call.resolveMethodGenerics().isValidResult &&
                    statements.drop(1).all { setup(it, owner, child) }
            } else valueCallback(method)
        }
    }

    /** Only value conversion, with no component escape or structural call, may be deferred. */
    fun valueCallback(method: PsiMethod): Boolean {
        if (method.name != "convertInput" || method.parameterList.parametersCount != 0 ||
            method.returnType?.canonicalText != "void" || method.hasModifierProperty(PsiModifier.STATIC) ||
            method.findDeepestSuperMethods().none { it.containingClass?.qualifiedName == WicketComponentTypes.FORM_COMPONENT }) return false
        val owner = method.containingClass ?: return false
        return method.body?.statements?.all { statement ->
            if (statement is PsiEmptyStatement) return@all true
            val call = (statement as? PsiExpressionStatement)?.expression as? PsiMethodCallExpression ?: return@all false
            val target = call.resolveMethod() ?: return@all false
            if (!call.resolveMethodGenerics().isValidResult || !ownReceiver(call.methodExpression.qualifierExpression) ||
                target.containingClass?.qualifiedName != WicketComponentTypes.FORM_COMPONENT || target.name != "setConvertedInput") return@all false
            val argument = call.argumentList.expressions.singleOrNull() ?: return@all false
            if (scalar(argument, owner)) return@all true
            val getter = argument as? PsiMethodCallExpression ?: return@all false
            val field = (getter.methodExpression.qualifierExpression as? PsiReferenceExpression)?.resolve() as? PsiField ?: return@all false
            getter.resolveMethodGenerics().isValidResult && getter.resolveMethod()?.let {
                it.name == "getConvertedInput" && it.containingClass?.qualifiedName == WicketComponentTypes.FORM_COMPONENT
            } == true && getter.argumentList.expressionCount == 0 && stableField(field, owner) &&
                ownReceiver((getter.methodExpression.qualifierExpression as PsiReferenceExpression).qualifierExpression)
        } == true
    }

    private fun setup(statement: PsiStatement, owner: PsiClass, child: (PsiNewExpression) -> Boolean): Boolean {
        ProgressManager.checkCanceled()
        if (statement is PsiDeclarationStatement) {
            val local = statement.declaredElements.singleOrNull() as? PsiLocalVariable ?: return false
            return stableLocal(local, owner, child)
        }
        val expression = (statement as? PsiExpressionStatement)?.expression ?: return false
        if (expression is PsiAssignmentExpression) {
            val reference = expression.lExpression as? PsiReferenceExpression ?: return false
            val field = reference.resolve() as? PsiField ?: return false
            val creation = expression.rExpression as? PsiNewExpression ?: return false
            return expression.operationTokenType == JavaTokenType.EQ && stableField(field, owner) &&
                ownReceiver(reference.qualifierExpression) && field.initializer == null && child(creation) &&
                PsiTreeUtil.findChildrenOfType(owner, PsiAssignmentExpression::class.java).count {
                    (it.lExpression as? PsiReferenceExpression)?.resolve() == field
                } == 1
        }
        val call = expression as? PsiMethodCallExpression ?: return false
        val receiver = call.methodExpression.qualifierExpression
        val owned = ownReceiver(receiver) || receiver is PsiReferenceExpression &&
            ownReceiver(receiver.qualifierExpression) && when (val variable = receiver.resolve()) {
                is PsiField -> stableField(variable, owner)
                is PsiLocalVariable -> stableLocal(variable, owner, child)
                else -> false
            }
        if (!call.resolveMethodGenerics().isValidResult || !owned) return false
        val target = call.resolveMethod() ?: return false
        if (target.containingClass?.qualifiedName == "org.apache.wicket.MarkupContainer" && target.name in setOf("add", "queue") &&
            (target.parameterList.parameters.singleOrNull()?.type as? PsiEllipsisType)?.componentType?.canonicalText == "org.apache.wicket.Component") {
            return call.argumentList.expressions.all { value ->
                when (value) {
                    is PsiNewExpression -> child(value)
                    is PsiReferenceExpression -> ownReceiver(value.qualifierExpression) && when (val variable = value.resolve()) {
                        is PsiField -> stableField(variable, owner)
                        is PsiLocalVariable -> stableLocal(variable, owner, child)
                        else -> false
                    }
                    else -> false
                }
            }
        }
        return configuration(target) && call.argumentList.expressions.all { scalar(it, owner) }
    }

    fun configuration(method: PsiMethod) = method.containingClass?.qualifiedName == WicketComponentTypes.FORM_COMPONENT &&
        method.name in setOf("setRequired", "setType")

    private fun ownReceiver(expression: PsiExpression?) = expression == null ||
        expression is PsiThisExpression && expression.qualifier == null

    private fun stableField(field: PsiField, owner: PsiClass) = field.containingClass == owner &&
        field.hasModifierProperty(PsiModifier.PRIVATE) && field.hasModifierProperty(PsiModifier.FINAL) &&
        !field.hasModifierProperty(PsiModifier.STATIC)

    private fun stableLocal(local: PsiLocalVariable, owner: PsiClass, child: (PsiNewExpression) -> Boolean): Boolean {
        val initializer = local.initializer as? PsiNewExpression ?: return false
        return local.hasModifierProperty(PsiModifier.FINAL) &&
            PsiTreeUtil.getParentOfType(local, PsiMethod::class.java)?.containingClass == owner && child(initializer)
    }

    private fun scalar(expression: PsiExpression, owner: PsiClass): Boolean = when (expression) {
        is PsiLiteralExpression, is PsiClassObjectAccessExpression -> true
        is PsiReferenceExpression -> ownReceiver(expression.qualifierExpression) &&
            (expression.resolve() as? PsiParameter)?.let { parameter ->
                (parameter.declarationScope as? PsiMethod)?.containingClass == owner &&
                    parameter.type.canonicalText in setOf("java.lang.String", "boolean", "int", "java.lang.Class<?>")
            } == true
        else -> false
    }
}
