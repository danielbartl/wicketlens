package dev.wicketlens.analysis

import com.intellij.openapi.progress.ProgressManager
import com.intellij.psi.*
import com.intellij.psi.util.InheritanceUtil
import com.intellij.psi.util.PsiTreeUtil

/** Reviewed framework contracts plus bounded, source-visible, forwarding-only wrappers. */
internal object WicketIdConstructors {
    data class Trace(val expression: PsiExpression, val sources: List<String>, val shared: Boolean, val inlineChildren: Boolean = false)
    private data class Origin(val parameter: Int? = null, val literal: PsiLiteralExpression? = null)

    fun accepts(creation: PsiNewExpression) = trace(creation) != null
    fun isReviewed(creation: PsiNewExpression) = creation.resolveConstructor()?.containingClass?.qualifiedName in REVIEWED

    fun trace(creation: PsiNewExpression): Trace? {
        if (!creation.resolveMethodGenerics().isValidResult) return null
        val constructor = creation.resolveConstructor() ?: return null
        creation.anonymousClass?.let { anonymous ->
            WicketRepeaterContract.kind(anonymous)?.let { if (!WicketRepeaterContract.accepts(anonymous, it)) return null }
        }
        val sources = linkedSetOf<String>()
        val origin = origin(constructor, linkedSetOf(), sources, intArrayOf(256)) ?: return null
        val expression = origin.literal ?: creation.argumentList?.expressions?.getOrNull(origin.parameter ?: return null) ?: return null
        val owner = constructor.containingClass ?: return null
        val inlineChildren = WicketComponentTypes.isFormComponent(owner) && !WicketComponentTypes.ownsMarkup(owner) &&
            sources.any { path ->
                // The full source ancestry has been checked by origin().
                generateSequence(owner) { it.superClass }.take(12).filter { it.containingFile.virtualFile?.path == path }
                    .any { PsiTreeUtil.findChildrenOfType(it, PsiNewExpression::class.java).any { creation ->
                        (creation.classReference?.resolve() as? PsiClass)?.let { type ->
                            InheritanceUtil.isInheritor(type, "org.apache.wicket.Component")
                        } == true
                    } }
            }
        return Trace(unwrap(expression), sources.toList(), origin.literal != null, inlineChildren)
    }

    private fun origin(method: PsiMethod, visiting: MutableSet<PsiMethod>, sources: MutableSet<String>, budget: IntArray): Origin? {
        ProgressManager.checkCanceled()
        if (--budget[0] < 0 || visiting.size >= 12 || !visiting.add(method)) return null
        try {
            val owner = method.containingClass ?: return null
            if (owner.qualifiedName in REVIEWED) {
                return Origin(parameter = 0).takeIf { method.parameterList.parameters.firstOrNull()?.type?.canonicalText == "java.lang.String" }
            }
            if (method is PsiCompiledElement || !InheritanceUtil.isInheritor(owner, "org.apache.wicket.Component")) return null
            sources += owner.containingFile.virtualFile?.path ?: owner.containingFile.name
            if (sources.size > 24) return null
            if (PsiTreeUtil.findChildOfType(owner.containingFile, PsiErrorElement::class.java) != null) return null
            val repetition = WicketRepeaterContract.kind(owner)
            val isolated = WicketComponentTypes.isFormComponent(owner) || InheritanceUtil.isInheritor(owner, WicketComponentTypes.PANEL)
            if (isolated) {
                if (!WicketFormComponentContract.accepts(owner) { creation ->
                        creation.anonymousClass == null && creation.resolveMethodGenerics().isValidResult &&
                            creation.argumentList?.expressions.orEmpty().all { it is PsiLiteralExpression } &&
                            creation.resolveConstructor()?.let { origin(it, visiting, sources, budget) } != null
                    }) return null
            } else if (repetition != null) {
                if (!WicketRepeaterContract.accepts(owner, repetition)) return null
            } else if (owner.methods.any { !it.isConstructor && !it.hasModifierProperty(PsiModifier.STATIC) } || owner.initializers.isNotEmpty() ||
                owner.fields.any { it.initializer != null && unwrap(it.initializer!!) !is PsiLiteralExpression }) return null
            val statements = method.body?.statements?.filterNot { it is PsiEmptyStatement } ?: return null
            val statement = (if (isolated) statements.firstOrNull() else statements.singleOrNull()) as? PsiExpressionStatement ?: return null
            val call = statement.expression as? PsiMethodCallExpression ?: return null
            if (call.methodExpression.referenceName !in setOf("super", "this") || !call.resolveMethodGenerics().isValidResult) return null
            // All arguments run left to right before the target constructor. A non-ID argument
            // can change the parameter later used as the ID, so validate before following any origin.
            // Repeater contracts already check every argument, including their reviewed list/model factories.
            if (repetition == null && !call.argumentList.expressions.all { forwardingArgument(it, method) }) return null
            val target = call.resolveMethod() ?: return null
            val forwarded = origin(target, visiting, sources, budget) ?: return null
            if (forwarded.literal != null) return forwarded
            val argument = call.argumentList.expressions.getOrNull(forwarded.parameter ?: return null)?.let(::unwrap) ?: return null
            if (argument is PsiLiteralExpression && argument.value is String) return Origin(literal = argument)
            val parameter = (argument as? PsiReferenceExpression)?.resolve() as? PsiParameter ?: return null
            val index = method.parameterList.parameters.indexOf(parameter)
            return Origin(parameter = index).takeIf { index >= 0 && parameter.type.canonicalText == "java.lang.String" }
        } finally { visiting.remove(method) }
    }

    private fun forwardingArgument(expression: PsiExpression, constructor: PsiMethod): Boolean = when (val value = unwrap(expression)) {
        is PsiLiteralExpression, is PsiClassObjectAccessExpression -> true
        is PsiReferenceExpression -> value.qualifierExpression == null &&
            (value.resolve() as? PsiParameter)?.declarationScope == constructor
        else -> false
    }

    private fun unwrap(expression: PsiExpression): PsiExpression {
        var current = expression
        while (current is PsiParenthesizedExpression) current = current.expression ?: return current
        return current
    }

    // Reviewed against Wicket 10.11.0; see docs/constructor-coverage.md.
    private val REVIEWED = setOf(
        "org.apache.wicket.markup.html.basic.Label", "org.apache.wicket.markup.html.form.Form",
        "org.apache.wicket.markup.html.form.TextField", "org.apache.wicket.markup.html.form.Button",
        "org.apache.wicket.markup.html.form.SubmitLink", "org.apache.wicket.markup.html.form.CheckBox",
        "org.apache.wicket.markup.html.form.DropDownChoice", "org.apache.wicket.markup.html.link.Link",
        "org.apache.wicket.markup.html.link.BookmarkablePageLink", "org.apache.wicket.markup.html.link.ExternalLink",
        "org.apache.wicket.ajax.markup.html.AjaxLink", "org.apache.wicket.ajax.markup.html.form.AjaxButton",
        "org.apache.wicket.markup.html.panel.FeedbackPanel", "org.apache.wicket.markup.html.WebMarkupContainer",
        "org.apache.wicket.markup.html.panel.Panel",
        WicketComponentTypes.FORM_COMPONENT, WicketComponentTypes.FORM_PANEL,
        WicketRepeaterContract.LIST, WicketRepeaterContract.REPEATING,
    )
}
