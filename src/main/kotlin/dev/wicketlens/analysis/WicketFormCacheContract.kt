package dev.wicketlens.analysis

import com.intellij.psi.*
import com.intellij.psi.util.InheritanceUtil
import com.intellij.psi.util.PsiTreeUtil

/** Shared shape recognizer. Callers retain their own effects, attachment and dispatch checks. */
internal object WicketFormCacheContract {
    fun find(owners: List<PsiClass>, tick: (PsiElement) -> Unit): Map<PsiMethod,PsiField> {
            val result = linkedMapOf<PsiMethod, PsiField>()
            owners.forEach { owner -> owner.fields.filter { field ->
                field.hasModifierProperty(PsiModifier.PRIVATE) && !field.hasModifierProperty(PsiModifier.STATIC) &&
                    (field.type as? PsiClassType)?.resolve()?.let { it.qualifiedName == WicketComponentTypes.FORM_COMPONENT || InheritanceUtil.isInheritor(it, WicketComponentTypes.FORM_COMPONENT) } == true &&
                    (field.initializer == null || field.initializer is PsiLiteralExpression && (field.initializer as PsiLiteralExpression).value == null)
            }.forEach fieldLoop@ { field ->
                tick(field)
                val writes = PsiTreeUtil.findChildrenOfType(owner, PsiAssignmentExpression::class.java)
                    .filter { (it.lExpression as? PsiReferenceExpression)?.resolve() == field }
                val write = writes.singleOrNull() ?: return@fieldLoop
                if (write.operationTokenType != JavaTokenType.EQ || write.rExpression !is PsiMethodCallExpression) return@fieldLoop
                val getter = PsiTreeUtil.getParentOfType(write, PsiMethod::class.java) ?: return@fieldLoop
                if (getter.containingClass != owner || getter.isConstructor || getter.parameterList.parametersCount != 0 || getter.hasModifierProperty(PsiModifier.STATIC)) return@fieldLoop
                val statements = getter.body?.statements?.filterNot { it is PsiEmptyStatement }.orEmpty()
                val guarded = statements.firstOrNull() as? PsiIfStatement ?: return@fieldLoop
                val condition = guarded.condition as? PsiBinaryExpression ?: return@fieldLoop
                val returned = (statements.lastOrNull() as? PsiReturnStatement)?.returnValue as? PsiReferenceExpression
                val operands = listOfNotNull(condition.lOperand, condition.rOperand)
                fun self(expression: PsiExpression?) = (expression as? PsiReferenceExpression)?.let { reference ->
                    val qualifier = reference.qualifierExpression
                    reference.resolve() == field && (qualifier == null || qualifier is PsiThisExpression &&
                        (qualifier.qualifier == null || qualifier.qualifier!!.resolve() == owner))
                } == true
                val body = (guarded.thenBranch as? PsiBlockStatement)?.codeBlock
                if (statements.size == 2 && guarded.elseBranch == null && condition.operationTokenType == JavaTokenType.EQEQ &&
                    operands.any { it is PsiLiteralExpression && it.value == null } && operands.any(::self) && self(write.lExpression) && self(returned) &&
                    (body?.statements?.firstOrNull() as? PsiExpressionStatement)?.expression == write) result[getter] = field
            } }
            return result
    }
}
