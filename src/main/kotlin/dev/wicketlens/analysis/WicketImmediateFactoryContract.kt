package dev.wicketlens.analysis

import com.intellij.psi.*
import com.intellij.psi.util.InheritanceUtil
import com.intellij.psi.util.PsiTreeUtil

/** A deliberately small source protocol: apply a unary factory, optionally guard, replace, return this. */
internal class WicketImmediateFactoryContract(
    private val source: (PsiElement) -> Unit,
    private val descendants: (PsiElement) -> List<PsiElement>,
    private val string: (PsiExpression) -> String?,
    private val depthLimit: Int,
    private val fail: (PsiElement, String) -> Nothing,
) {
    data class Site(val invocation: PsiMethodCallExpression, val replacement: PsiMethodCallExpression)

    companion object {
        fun candidate(method: PsiMethod): Boolean {
            if (method is PsiCompiledElement || method.hasModifierProperty(PsiModifier.STATIC)) return false
            val parameter = method.parameterList.parameters.singleOrNull() ?: return false
            val sam = LambdaUtil.getFunctionalInterfaceMethod(parameter.type) ?: return false
            if (sam.parameterList.parametersCount != 1) return false
            val output = LambdaUtil.getFunctionalInterfaceReturnType(parameter.type)
            val type = (if (output is PsiWildcardType) output.extendsBound else output) as? PsiClassType
            if (type?.resolve()?.let { it.qualifiedName == "org.apache.wicket.Component" || InheritanceUtil.isInheritor(it, "org.apache.wicket.Component") } != true) return false
            val declaration = method.body?.statements?.firstOrNull() as? PsiDeclarationStatement ?: return false
            val local = declaration.declaredElements.singleOrNull() as? PsiLocalVariable ?: return false
            val call = local.initializer as? PsiMethodCallExpression ?: return false
            return (call.methodExpression.qualifierExpression as? PsiReferenceExpression)?.resolve() == parameter
        }
    }

    fun inspect(method: PsiMethod): Site {
        source(method)
        if (!candidate(method)) fail(method, "The source setter is not a unary immediate component factory.")
        val parameter = method.parameterList.parameters.single()
        val statements = method.body!!.statements
        if (statements.size !in 3..4) fail(method, "The factory setter has additional source effects.")
        val local = (statements.first() as PsiDeclarationStatement).declaredElements.single() as PsiLocalVariable
        val invocation = local.initializer as PsiMethodCallExpression
        if (invocation.resolveMethod() != LambdaUtil.getFunctionalInterfaceMethod(parameter.type) || invocation.argumentList.expressionCount != 1)
            fail(invocation, "The factory must invoke its unary functional-interface method directly.")
        val parameterUses = descendants(method.body!!).filterIsInstance<PsiReferenceExpression>().filter { it.resolve() == parameter }
        if (parameterUses != listOf(invocation.methodExpression.qualifierExpression)) fail(parameter, "The factory parameter escapes its single immediate invocation.")
        val returned = (statements.last() as? PsiReturnStatement)?.returnValue as? PsiThisExpression
        if (returned == null || returned.qualifier != null) fail(method, "The factory setter must return its own receiver.")
        val replacement = (statements[statements.lastIndex - 1] as? PsiExpressionStatement)?.expression as? PsiMethodCallExpression
            ?: fail(method, "The factory result requires a direct replacement statement.")
        val receiver = replacement.methodExpression.qualifierExpression
        val target = replacement.resolveMethod()
        if (target?.containingClass?.qualifiedName != "org.apache.wicket.MarkupContainer" || target.name != "replace" ||
            receiver != null && (receiver !is PsiThisExpression || receiver.qualifier != null) ||
            (replacement.argumentList.expressions.singleOrNull() as? PsiReferenceExpression)?.resolve() != local)
            fail(replacement, "The factory result must replace one slot on the setter's own receiver.")
        val guard = if (statements.size == 4) statements[1] as? PsiIfStatement
            ?: fail(statements[1], "Only an optional throwing identity guard may precede replacement.") else null
        guard?.let { inspectGuard(it, local, invocation) }
        descendants(method.body!!).filterIsInstance<PsiReferenceExpression>().filter { it.resolve() == local }.forEach { use ->
            if (use != replacement.argumentList.expressions.single() && (guard == null || !PsiTreeUtil.isAncestor(guard, use, true)))
                fail(use, "The factory result escapes its replacement and identity guard.")
        }
        return Site(invocation, replacement)
    }

    /** Shared source guard proof for fresh results whose String ID is forwarded. */
    fun inspectGuard(guard: PsiIfStatement, local: PsiLocalVariable, invocation: PsiMethodCallExpression) {
        inspectIdGuard(guard, local, string(invocation.argumentList.expressions.single()) ?: fail(invocation, "The supplied factory ID is unknown."))
    }

    fun inspectIdGuard(guard: PsiIfStatement, local: PsiVariable, suppliedId: String) {
        val throwing = (guard.thenBranch as? PsiBlockStatement)?.codeBlock?.statements?.singleOrNull() as? PsiThrowStatement
        val exception = throwing?.exception as? PsiNewExpression
        if (guard.elseBranch != null || throwing == null || exception?.resolveConstructor()?.containingClass?.qualifiedName != "java.lang.IllegalArgumentException")
            fail(guard, "The factory guard must only throw IllegalArgumentException.")
        descendants(guard).forEach { element ->
            when (element) {
                is PsiAssignmentExpression, is PsiLambdaExpression, is PsiPostfixExpression -> fail(element, "The factory guard has unreviewed effects.")
                is PsiPrefixExpression -> if (element.operationTokenType != JavaTokenType.EXCL) fail(element, "The factory guard has unreviewed effects.")
                is PsiNewExpression -> if (element != throwing.exception) fail(element, "The factory guard allocates unreviewed values.")
                is PsiMethodCallExpression -> {
                    val called = element.resolveMethod()
                    val qualifier = element.methodExpression.qualifierExpression as? PsiReferenceExpression
                    val idQuery = called?.name == "getId" && called.containingClass?.qualifiedName == "org.apache.wicket.Component" && qualifier?.resolve() == local && element.argumentList.expressionCount == 0
                    val equality = called?.name == "equals" && called.containingClass?.qualifiedName == "java.lang.String"
                    if (!idQuery && !equality) fail(element, "Only String equality and component ID queries are reviewed in factory guards.")
                }
                is PsiReferenceExpression -> {
                    val resolved = element.resolve()
                    if (resolved is PsiVariable && resolved != local && !(resolved is PsiField && resolved.hasModifierProperty(PsiModifier.STATIC) && resolved.hasModifierProperty(PsiModifier.FINAL) && resolved.type.canonicalText == "java.lang.String"))
                        fail(element, "The factory guard reads unreviewed mutable state.")
                    if (resolved == local) {
                        val member = element.parent as? PsiReferenceExpression
                        val comparison = element.parent as? PsiBinaryExpression
                        val other = if (comparison?.lOperand == element) comparison.rOperand else comparison?.lOperand
                        val nullComparison = comparison?.operationTokenType in setOf(JavaTokenType.EQEQ, JavaTokenType.NE) && other is PsiLiteralExpression && other.value == null
                        if (!(member?.qualifierExpression == element && member.referenceName == "getId") && !nullComparison)
                            fail(element, "Factory guards may only query the result's ID or compare it to null.")
                    }
                }
            }
        }
        // This is a source identity check, not evaluation of a component, callback or model.
        fun result(e: PsiExpression?) = (e as? PsiReferenceExpression)?.resolve() == local
        fun nullValue(e: PsiExpression?) = e is PsiLiteralExpression && e.value == null
        fun idQuery(e: PsiExpression?) = e is PsiMethodCallExpression && e.methodExpression.referenceName == "getId" && result(e.methodExpression.qualifierExpression)
        fun nullComparison(left: PsiExpression?, right: PsiExpression?, unequal: Boolean, at: PsiElement): Boolean {
            if (!(result(left) && nullValue(right) || nullValue(left) && result(right))) fail(at, "Only result/null comparisons are reviewed in identity guards.")
            return unequal // A fresh allocation is never null.
        }
        fun condition(e: PsiExpression?, depth: Int = 0): Boolean {
            if (e == null || depth >= depthLimit) fail(guard, "The identity guard exceeds the reviewed source expression contract.")
            return when (e) {
                is PsiParenthesizedExpression -> condition(e.expression, depth + 1)
                is PsiLiteralExpression -> e.value as? Boolean ?: fail(e, "The identity guard is not a known Boolean expression.")
                is PsiPrefixExpression -> if (e.operationTokenType == JavaTokenType.EXCL) !condition(e.operand, depth + 1) else fail(e, "Unsupported identity guard operator.")
                is PsiPolyadicExpression -> {
                    when (e.operationTokenType) {
                        JavaTokenType.OROR -> e.operands.map { condition(it, depth + 1) }.any { it }
                        JavaTokenType.ANDAND -> e.operands.map { condition(it, depth + 1) }.all { it }
                        JavaTokenType.EQEQ, JavaTokenType.NE -> {
                            if (e.operands.size != 2) fail(e, "Only a single result/null comparison is reviewed.")
                            nullComparison(e.operands[0], e.operands[1], e.operationTokenType == JavaTokenType.NE, e)
                        }
                        else -> fail(e, "Unsupported identity guard operator.")
                    }
                }
                is PsiBinaryExpression -> {
                    when (e.operationTokenType) {
                        JavaTokenType.EQEQ, JavaTokenType.NE -> nullComparison(e.lOperand, e.rOperand, e.operationTokenType == JavaTokenType.NE, e)
                        else -> fail(e, "Unsupported identity guard comparison.")
                    }
                }
                is PsiMethodCallExpression -> {
                    val receiver = e.methodExpression.qualifierExpression
                    val argument = e.argumentList.expressions.singleOrNull()
                    when {
                        idQuery(argument) && receiver != null -> (string(receiver) ?: fail(receiver, "The guard's expected ID is unknown.")) == suppliedId
                        idQuery(receiver) && argument != null -> suppliedId == (string(argument) ?: fail(argument, "The guard's expected ID is unknown."))
                        else -> fail(e, "Only source ID equality is reviewed in identity guards.")
                    }
                }
                else -> fail(e, "The identity guard cannot be established for a fresh forwarded-ID result.")
            }
        }
        if (condition(guard.condition)) fail(guard, "The identity guard rejects the fresh correctly forwarded factory result.")
    }
}
