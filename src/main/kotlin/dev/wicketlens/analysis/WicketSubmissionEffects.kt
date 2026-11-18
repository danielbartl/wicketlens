package dev.wicketlens.analysis

import com.intellij.psi.*
import dev.wicketlens.model.*

/** Shared source effect declarations. Feedback text, data expressions and callbacks are never evaluated. */
internal class WicketSubmissionEffects(
    private val callback: PsiElement,
    private val defaultReceiver: String,
    private val path: (String) -> String,
    private val component: (PsiExpression) -> String,
    private val componentType: (PsiType?) -> Boolean,
    private val descendants: (PsiElement) -> List<PsiElement>,
    private val fail: (PsiElement, String) -> Nothing,
    private val target: PsiParameter = (callback as PsiMethod).parameterList.parameters.single(),
) {
    private val allowed = mutableSetOf<PsiReferenceExpression>()
    private fun own(e: PsiExpression?) = e == null || e is PsiThisExpression && e.qualifier == null
    fun declarations(call: PsiMethodCallExpression, method: PsiMethod): List<SubmissionEffect>? {
        val args = call.argumentList.expressions
        val qualifier = call.methodExpression.qualifierExpression
        if (method is PsiCompiledElement && method.name == "add" && method.containingClass?.qualifiedName in
            setOf("org.apache.wicket.ajax.AjaxRequestTarget", "org.apache.wicket.core.request.handler.IPartialPageRequestHandler") &&
            (method.parameterList.parameters.singleOrNull()?.type as? PsiEllipsisType)?.componentType?.canonicalText == "org.apache.wicket.Component" &&
            qualifier is PsiReferenceExpression && qualifier.resolve() == target && own(qualifier.qualifierExpression)) {
            if (args.isEmpty()) fail(call, "An Ajax refresh needs a declared component target.")
            allowed += qualifier
            return args.map { argument -> val id = component(argument)
                SubmissionEffect("AJAX_REFRESH", method.name, location(call), id, path(id), listOf(location(argument))) }
        }
        if (method is PsiCompiledElement && method.containingClass?.qualifiedName == "org.apache.wicket.Component" &&
            method.name in setOf("success", "info", "warn", "error", "debug", "fatal") && args.size == 1) {
            val id = if (own(qualifier)) defaultReceiver else component(qualifier!!)
            descendants(args.single()).firstOrNull { it is PsiThisExpression || it is PsiExpression && componentType(it.type) }?.let {
                fail(it, "An opaque feedback value exposes a component reference.")
            }
            return listOf(SubmissionEffect("FEEDBACK", method.name, location(call), id, path(id), args.map(::location)))
        }
        return null
    }
    /** Only called after a separate helper contract proves unchanged forwarding into its direct Ajax refresh. */
    fun acceptTargetForwarding(argument: PsiExpression) {
        val reference=argument as? PsiReferenceExpression ?: fail(argument,"A reviewed helper must receive the callback target unchanged.")
        if(reference.resolve()!=target || !own(reference.qualifierExpression)) fail(argument,"A reviewed helper must receive the callback target unchanged.")
        allowed+=reference
    }
    fun checkTargetUses() {
        descendants((callback as? PsiMethod)?.body ?: (callback as PsiLambdaExpression).body!!).filterIsInstance<PsiReferenceExpression>().firstOrNull { it.resolve() == target && it !in allowed }?.let {
            fail(it, "The callback Ajax target escapes its direct refresh declarations.")
        }
    }
}
private fun location(e: PsiElement) = SourceLocation(e.containingFile.virtualFile.path, e.textOffset)
