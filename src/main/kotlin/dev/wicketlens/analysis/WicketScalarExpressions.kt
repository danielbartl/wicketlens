package dev.wicketlens.analysis

import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.*
import com.intellij.psi.tree.IElementType
import com.intellij.psi.util.TypeConversionUtil

/** Folds only supplied, already audited scalar values; never resolves application state. */
internal object WicketScalarExpressions {
    fun integral(type: PsiType?) = type?.canonicalText in setOf("byte", "short", "char", "int", "long")
    fun supported(type: PsiType?) = integral(type) || type?.canonicalText == "boolean" || type?.equalsToText("java.lang.String") == true

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
        JavaTokenType.LT -> "<"
        JavaTokenType.LE -> "<="
        JavaTokenType.GT -> ">"
        JavaTokenType.GE -> ">="
        JavaTokenType.EQEQ -> "=="
        JavaTokenType.NE -> "!="
        else -> null
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
