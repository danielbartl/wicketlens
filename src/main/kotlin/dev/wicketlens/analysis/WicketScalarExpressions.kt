package dev.wicketlens.analysis

import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.*
import com.intellij.psi.tree.IElementType
import com.intellij.psi.util.TypeConversionUtil

/** Folds only supplied, already audited scalar values; never resolves application state. */
internal object WicketScalarExpressions {
    fun integral(type: PsiType?) = type?.canonicalText in setOf("byte", "short", "char", "int", "long")
    fun supported(type: PsiType?) = integral(type) || type?.canonicalText == "boolean" || type?.equalsToText("java.lang.String") == true

    fun validIntegralLiteral(literal: PsiLiteralExpression, allowDecimalMinimum: Boolean = false): Boolean {
        val value = literal.value
        if (value !is Int && value !is Long || (value as Number).toLong() >= 0) return true
        val digits = literal.text.replace("_", "").removeSuffix("L").removeSuffix("l")
        if (digits.startsWith("0")) return true // Hex/octal/binary bit patterns can be negative.
        // PSI represents these exceptional decimal tokens as MIN_VALUE even outside a legal unary minus.
        return allowDecimalMinimum && (value is Int && digits == "2147483648" || value is Long && digits == "9223372036854775808")
    }

    fun normalize(value: Any, type: PsiType?): Any? = when {
        integral(type) -> if (value is Number || value is Char) TypeConversionUtil.computeCastTo(value, type!!) else null
        type?.canonicalText == "boolean" -> value as? Boolean
        type?.equalsToText("java.lang.String") == true -> (value as? String)?.takeIf { it.length <= 4096 }
        else -> null
    }

    fun operator(token: IElementType): String? = when (token) {
        JavaTokenType.PLUS -> "+"
        JavaTokenType.MINUS -> "-"
        JavaTokenType.ASTERISK -> "*"
        JavaTokenType.DIV -> "/"
        JavaTokenType.PERC -> "%"
        JavaTokenType.LTLT -> "<<"
        JavaTokenType.GTGT -> ">>"
        JavaTokenType.GTGTGT -> ">>>"
        JavaTokenType.AND -> "&"
        JavaTokenType.OR -> "|"
        JavaTokenType.XOR -> "^"
        JavaTokenType.TILDE -> "~"
        JavaTokenType.LT -> "<"
        JavaTokenType.LE -> "<="
        JavaTokenType.GT -> ">"
        JavaTokenType.GE -> ">="
        JavaTokenType.EQEQ -> "=="
        JavaTokenType.NE -> "!="
        else -> null
    }

    fun binaryOperandsSupported(token: IElementType, types: List<PsiType?>): Boolean = when (token) {
        JavaTokenType.EQEQ, JavaTokenType.NE, JavaTokenType.AND, JavaTokenType.OR, JavaTokenType.XOR ->
            types.all(::integral) || types.all { it?.canonicalText == "boolean" }
        JavaTokenType.PLUS -> types.all(::supported)
        else -> operator(token) != null && types.all(::integral)
    }

    fun fold(context: PsiExpression, values: List<Any>, operator: String, unary: Boolean = false): Any? {
        val parts = mutableListOf<String>()
        var length = 0
        for (value in values) {
            val part = when (value) {
                is String -> "\"${StringUtil.escapeStringCharacters(value)}\""
                is Char -> "((char)${value.code})"
                is Byte -> "((byte)$value)"
                is Short -> "((short)$value)"
                is Int -> "($value)"
                is Long -> "(${value}L)"
                is Boolean -> value.toString()
                else -> return null
            }
            length += part.length + operator.length
            if (length > 16384) return null
            parts += part
        }
        val text = if (unary) operator + parts.single() else parts.joinToString(operator)
        val expression = JavaPsiFacade.getElementFactory(context.project).createExpressionFromText(text, context)
        val result = JavaPsiFacade.getInstance(context.project).constantEvaluationHelper.computeConstantExpression(expression, false)
        return result?.takeUnless { it is String && it.length > 4096 }
    }
}
