package dev.wicketlens.analysis

import com.intellij.openapi.progress.ProgressManager
import com.intellij.psi.*
import com.intellij.psi.util.InheritanceUtil
import com.intellij.psi.util.PsiTreeUtil

/** ID provenance only. This is deliberately not a proof of constructor/setup isolation. */
internal object WicketComponentIdentity {
    data class Identity(val expression: PsiExpression, val sources: List<String>, val shared: Boolean)
    private data class Origin(val parameter: Int? = null, val literal: PsiLiteralExpression? = null)
    fun frameworkConstructor(method: PsiMethod) = method.isConstructor &&
        (method.containingClass?.qualifiedName in FRAMEWORK || WicketIdConstructors.reviewedConstructor(method))

    fun trace(creation: PsiNewExpression, additionalFrameworkConstructors: Set<String> = emptySet(), conditionalData: Boolean = false): Identity? {
        if (!creation.resolveMethodGenerics().isValidResult) return null
        val constructor = creation.resolveConstructor() ?: return null
        val type = creation.anonymousClass ?: constructor.containingClass ?: return null
        if (!InheritanceUtil.isInheritor(type, "org.apache.wicket.Component")) return null
        val sources = linkedSetOf<String>()
        var owner: PsiClass? = type
        var depth = 0
        while (owner != null && owner !is PsiCompiledElement) {
            ProgressManager.checkCanceled()
            if (++depth > 12 || owner.methods.any { !it.isConstructor && it.name == "getId" && it.parameterList.parametersCount == 0 }) return null
            sources += owner.containingFile.virtualFile?.path ?: return null
            owner = owner.superClass
        }
        WicketIdConstructors.trace(creation)?.let { trace ->
            sources += trace.sources
            constructor.containingClass?.containingFile?.virtualFile?.path?.takeIf { "!/" in it }?.substringBefore("!/")?.let(sources::add)
            return Identity(trace.expression, sources.toList(), trace.shared)
        }
        fun origin(method: PsiMethod, visiting: Set<PsiMethod>): Origin? {
            ProgressManager.checkCanceled()
            if (visiting.size >= 12 || method in visiting) return null
            val declaring = method.containingClass ?: return null
            if (method is PsiCompiledElement) {
                val valid = (frameworkConstructor(method) || method.containingClass?.qualifiedName in additionalFrameworkConstructors) && method.parameterList.parameters.firstOrNull()?.type?.canonicalText == "java.lang.String"
                if (valid) declaring.containingFile.virtualFile?.path?.takeIf { "!/" in it }?.substringBefore("!/")?.let(sources::add)
                return Origin(parameter = 0).takeIf { valid }
            }
            sources += declaring.containingFile.virtualFile?.path ?: return null
            if (sources.size > 24 || PsiTreeUtil.findChildOfType(declaring.containingFile, PsiErrorElement::class.java) != null) return null
            val first = method.body?.statements?.firstOrNull { it !is PsiEmptyStatement } as? PsiExpressionStatement ?: return null
            val call = first.expression as? PsiMethodCallExpression ?: return null
            if (call.methodExpression.referenceName !in setOf("this", "super") || !call.resolveMethodGenerics().isValidResult ||
                !call.argumentList.expressions.all { argument -> stableDelegation(argument, method, conditionalData) ||
                    additionalFrameworkConstructors.isNotEmpty() && argument is PsiNewExpression && argument.anonymousClass == null && argument.qualifier == null &&
                        argument.argumentList?.expressions?.all { it is PsiLiteralExpression } == true &&
                        (argument.type as? PsiClassType)?.resolve()?.let { !InheritanceUtil.isInheritor(it, "org.apache.wicket.Component") } == true
                }) return null
            val target = call.resolveMethod() ?: return null
            val forwarded = origin(target, visiting + method) ?: return null
            forwarded.literal?.let { return forwarded }
            val argument = unwrap(call.argumentList.expressions.getOrNull(forwarded.parameter ?: return null) ?: return null)
            if (argument is PsiLiteralExpression && argument.value is String) return Origin(literal = argument)
            val parameter = (argument as? PsiReferenceExpression)?.resolve() as? PsiParameter ?: return null
            val index = method.parameterList.parameters.indexOf(parameter)
            return Origin(parameter = index).takeIf { index >= 0 && parameter.type.canonicalText == "java.lang.String" }
        }
        val result = origin(constructor, emptySet()) ?: return null
        val expression = result.literal ?: creation.argumentList?.expressions?.getOrNull(result.parameter ?: return null) ?: return null
        return Identity(unwrap(expression), sources.toList(), result.literal != null)
    }

    private fun stableDelegation(expression: PsiExpression, constructor: PsiMethod, conditionalData: Boolean = false, depth: Int = 0): Boolean {
        ProgressManager.checkCanceled()
        if (depth >= 12) return false
        return when (val value = unwrap(expression)) {
        is PsiLiteralExpression, is PsiClassObjectAccessExpression -> true
        is PsiMethodReferenceExpression -> unboundConstructor(value) != null
        is PsiReferenceExpression -> when (val target = value.resolve()) {
            is PsiParameter -> value.qualifierExpression == null && target.declarationScope == constructor
            is PsiEnumConstant -> true
            else -> false
        }
        is PsiTypeCastExpression -> unwrap(value.operand ?: return false) is PsiLiteralExpression &&
            (unwrap(value.operand!!) as PsiLiteralExpression).value == null
        is PsiNewExpression -> conditionalData && value.anonymousClass == null && value.qualifier == null &&
            value.argumentList?.expressionCount == 0 && value.resolveMethodGenerics().isValidResult &&
            value.resolveConstructor()?.let { it is PsiCompiledElement && it.containingClass?.qualifiedName == "org.apache.wicket.model.Model" } == true
        is PsiMethodCallExpression -> {
            val method = value.resolveMethod()
            method != null && method.hasModifierProperty(PsiModifier.STATIC) &&
                method.containingClass?.qualifiedName in setOf("org.apache.wicket.model.Model", "java.util.List") &&
                method.name in setOf("of", "ofList") &&
                (value.methodExpression.qualifierExpression as? PsiReferenceExpression)?.resolve() is PsiClass &&
                value.argumentList.expressions.all { stableDelegation(it, constructor, conditionalData, depth + 1) }
        }
        is PsiConditionalExpression -> conditionalData && value.condition.let { condition ->
            val binary = condition as? PsiBinaryExpression
            val polyadic = condition as? PsiPolyadicExpression
            val operands = binary?.let { listOfNotNull(it.lOperand, it.rOperand) } ?: polyadic?.operands?.toList().orEmpty()
            val operation = binary?.operationTokenType ?: polyadic?.operationTokenType
            operation in setOf(JavaTokenType.EQEQ, JavaTokenType.NE) && operands.size == 2 && operands.count { it is PsiLiteralExpression && it.value == null } == 1 &&
                operands.filterNot { it is PsiLiteralExpression && it.value == null }.all { it is PsiReferenceExpression && (it.resolve() as? PsiParameter)?.declarationScope == constructor && it.qualifierExpression == null }
        } && listOfNotNull(value.thenExpression, value.elseExpression).let { it.size == 2 && it.all { e -> stableDelegation(e, constructor, true, depth + 1) } }
        else -> false
        }
    }

    private fun unwrap(expression: PsiExpression): PsiExpression =
        if (expression is PsiParenthesizedExpression) expression.expression?.let(::unwrap) ?: expression else expression

    /** Creating a static/top-level constructor reference evaluates no application receiver. */
    fun unboundConstructor(reference: PsiMethodReferenceExpression): PsiMethod? {
        val method = reference.resolve() as? PsiMethod ?: return null
        val type = method.containingClass ?: return null
        return method.takeIf { it.isConstructor && (reference.qualifierType != null ||
            (reference.qualifierExpression as? PsiReferenceExpression)?.resolve() is PsiClass) &&
            (type.containingClass == null || type.hasModifierProperty(PsiModifier.STATIC)) }
    }

    private val FRAMEWORK = setOf(
        "org.apache.wicket.Component", "org.apache.wicket.MarkupContainer", "org.apache.wicket.markup.html.WebComponent",
        "org.apache.wicket.markup.html.WebMarkupContainer", "org.apache.wicket.markup.html.TransparentWebMarkupContainer",
        "org.apache.wicket.markup.html.basic.Label", "org.apache.wicket.markup.html.form.Form",
        "org.apache.wicket.markup.html.form.TextField", "org.apache.wicket.markup.html.link.Link",
        "org.apache.wicket.markup.html.link.BookmarkablePageLink", "org.apache.wicket.ajax.markup.html.form.AjaxButton",
        "org.apache.wicket.ajax.markup.html.AjaxLink", "org.apache.wicket.markup.html.panel.Panel",
        "org.apache.wicket.markup.html.panel.GenericPanel", "org.apache.wicket.markup.html.panel.Fragment",
        "org.apache.wicket.markup.html.list.ListView", "org.apache.wicket.markup.repeater.RepeatingView",
    )
}
