package dev.wicketlens.analysis

import com.intellij.psi.*
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.TypeConversionUtil

/** Invocation-local source and binary constant variables; no application initialization or method execution. */
internal class WicketSourceConstants(private val source: (PsiElement) -> Unit, private val tick: () -> Unit) {
    private val values = mutableMapOf<PsiField, Any>()
    private val visiting = mutableSetOf<PsiField>()
    private val types = mutableSetOf<Pair<PsiClass, PsiClass?>>()

    fun resolve(reference: PsiReferenceExpression): Any? = readReference(reference)
    fun value(field: PsiField): Any? = readField(field)

    private fun dependencies(type: PsiClass, depth: Int, binaryTarget: PsiClass? = null): Boolean {
        tick()
        if (type is PsiCompiledElement && binaryTarget == null) {
            if (depth == 0) source(type)
            return true
        }
        val key = type to binaryTarget
        if (key in types) return true
        if (depth >= 12) return false
        types += key
        source(type)
        if (type == binaryTarget) return true
        return type.supers.all { dependencies(it, depth + 1, binaryTarget) }
    }

    private fun readReference(reference: PsiReferenceExpression): Any? {
        tick()
        val resolution = reference.advancedResolve(false)
        if (!resolution.isValidResult) return null
        val field = resolution.element as? PsiField ?: return null
        val binaryTarget = field.containingClass?.takeIf { field is PsiCompiledElement }
        val qualifier = reference.qualifierExpression
        if (qualifier != null) {
            val owner = (qualifier as? PsiReferenceExpression)?.resolve() as? PsiClass ?: return null
            if (!dependencies(owner, 0, binaryTarget)) return null
        } else {
            // Imports and intermediate source ancestors affect which declaration the name resolves to.
            (resolution.currentFileResolveScope as? PsiImportStaticStatement)?.resolveTargetClass()?.let {
                if (!dependencies(it, 0, binaryTarget)) return null
            }
            var owner = PsiTreeUtil.getParentOfType(reference, PsiClass::class.java)
            while (owner != null) {
                if (!dependencies(owner, 0, binaryTarget)) return null
                owner = owner.containingClass
            }
            val initializer = PsiTreeUtil.getParentOfType(reference, PsiField::class.java)
            if (initializer?.containingClass == field.containingClass && initializer?.hasModifierProperty(PsiModifier.STATIC) == true &&
                field.textOffset >= initializer.textOffset) return null // Illegal simple-name forward/self reference.
        }
        return readField(field)
    }

    private fun readField(field: PsiField): Any? {
        tick()
        if (!field.hasModifierProperty(PsiModifier.STATIC) ||
            !field.hasModifierProperty(PsiModifier.FINAL) || !WicketScalarExpressions.supported(field.type)) return null
        source(field)
        values[field]?.let { return it }
        if (field is PsiCompiledElement) {
            // Compiled Java PSI exposes ConstantValue metadata, not execution of <clinit> or attached source.
            val value = field.computeConstantValue()?.let { WicketScalarExpressions.normalize(it, field.type) } ?: return null
            values[field] = value
            return value
        }
        if (visiting.size >= 12 || !visiting.add(field)) return null
        try {
            val initializer = field.initializer ?: return null
            val raw = readExpression(initializer, 0) ?: return null
            if (!TypeConversionUtil.areTypesAssignmentCompatible(field.type, initializer)) return null
            val value = WicketScalarExpressions.normalize(raw, field.type) ?: return null
            values[field] = value
            return value
        } finally { visiting.remove(field) }
    }

    private fun readExpression(expression: PsiExpression, depth: Int): Any? {
        tick()
        if (depth >= 12 || !WicketScalarExpressions.supported(expression.type)) return null
        return when (expression) {
            is PsiParenthesizedExpression -> expression.expression?.let { readExpression(it, depth + 1) }
            is PsiLiteralExpression -> {
                if (!WicketScalarExpressions.validIntegralLiteral(expression)) return null
                expression.value?.let { WicketScalarExpressions.normalize(it, expression.type) }
            }
            is PsiReferenceExpression -> readReference(expression)
            is PsiPrefixExpression -> {
                val token = expression.operationTokenType
                val operand = expression.operand ?: return null
                if (token == JavaTokenType.EXCL && operand.type?.canonicalText == "boolean") {
                    !(readExpression(operand, depth + 1) as? Boolean ?: return null)
                } else {
                    if (token !in setOf(JavaTokenType.PLUS, JavaTokenType.MINUS, JavaTokenType.TILDE) || !WicketScalarExpressions.integral(operand.type)) return null
                    // Java's minimum integral literal is only valid with its leading minus.
                    if (operand is PsiLiteralExpression) {
                        if (!WicketScalarExpressions.validIntegralLiteral(operand, token == JavaTokenType.MINUS)) return null
                        val raw = JavaPsiFacade.getInstance(expression.project).constantEvaluationHelper.computeConstantExpression(expression, false) ?: return null
                        return WicketScalarExpressions.normalize(raw, expression.type)
                    }
                    WicketScalarExpressions.fold(expression, listOf(readExpression(operand, depth + 1) ?: return null),
                        WicketScalarExpressions.operator(token) ?: return null, unary = true)
                }
            }
            is PsiTypeCastExpression -> {
                val operand = expression.operand ?: return null
                if (!WicketScalarExpressions.integral(expression.type) || !WicketScalarExpressions.integral(operand.type)) return null
                WicketScalarExpressions.normalize(readExpression(operand, depth + 1) ?: return null, expression.type)
            }
            is PsiPolyadicExpression -> {
                val token = expression.operationTokenType
                val operands = expression.operands
                val logical = token in setOf(JavaTokenType.ANDAND, JavaTokenType.OROR)
                val operator = if (logical) (if (token == JavaTokenType.ANDAND) "&&" else "||")
                    else WicketScalarExpressions.operator(token) ?: return null
                if (logical && operands.any { it.type?.canonicalText != "boolean" }) return null
                if (!logical && !WicketScalarExpressions.binaryOperandsSupported(token, operands.map { it.type })) return null
                val arguments = operands.map { readExpression(it, depth + 1) ?: return null }
                if (token in setOf(JavaTokenType.DIV, JavaTokenType.PERC) && arguments.drop(1).any {
                        (it as? Number)?.toLong() == 0L || it == '\u0000'
                    }) return null
                WicketScalarExpressions.fold(expression, arguments, operator)
            }
            is PsiConditionalExpression -> {
                val condition = readExpression(expression.condition, depth + 1) as? Boolean ?: return null
                // Both arms must be constant expressions; a skipped opaque call is not a constant variable.
                val left = expression.thenExpression ?: return null
                val right = expression.elseExpression ?: return null
                val first = readExpression(left, depth + 1) ?: return null
                val second = readExpression(right, depth + 1) ?: return null
                WicketScalarExpressions.normalize(if (condition) first else second, expression.type)
            }
            else -> null
        }
    }
}
