package dev.wicketlens.analysis

import com.intellij.psi.*
import com.intellij.psi.util.PsiTreeUtil
import dev.wicketlens.model.SourceLocation
import dev.wicketlens.model.StoredActionRegistration

/** A construction-only storage proof. Later dispatch, callback effects and active UI state stay unknown. */
internal class WicketStoredActionDeclarations(
    private val page: PsiClass,
    private val tick: (PsiElement) -> Unit,
    private val source: (PsiElement) -> Unit,
    private val fail: (PsiElement, String) -> Nothing,
    private val candidate: () -> Unit,
) {
    private val descendants = WicketSourceDescendants(tick)
    private val consumers = WicketConsumerDeclarations(12, tick, source, fail)
    private val groups = mutableMapOf<PsiLocalVariable, Map<PsiMethodCallExpression, StoredActionRegistration>>()

    fun inspectBare(call: PsiMethodCallExpression): List<StoredActionRegistration>? {
        if (call.parent !is PsiExpressionStatement || callback(call) == null) return null
        candidate()
        if (firstCall(call).methodExpression.qualifierExpression !is PsiNewExpression) return null
        return inspect(call)
    }

    fun inspect(call: PsiMethodCallExpression): List<StoredActionRegistration>? {
        if (callback(call) == null) return null
        candidate()
        val method = call.resolveMethod() ?: fail(call, "The callback registrar is unresolved.")
        if (method is PsiCompiledElement) return null
        source(method)
        val owner = method.containingClass ?: fail(method, "The registrar owner is unavailable.")
        val constructor = PsiTreeUtil.getParentOfType(call, PsiMethod::class.java)
        if (page.constructors.size != 1 || constructor?.isConstructor != true || constructor.containingClass != page)
            fail(call, "Stored actions require a selected-page constructor registration statement.")
        val first = firstCall(call)
        val receiver = first.methodExpression.qualifierExpression
        val bareCalls = mutableListOf<PsiMethodCallExpression>()
        val end = if (receiver is PsiNewExpression) chain(first, bareCalls) else null
        if (receiver is PsiNewExpression && end?.parent is PsiExpressionStatement) {
            if (call != end || end.parent.parent != constructor.body)
                fail(call, "A bare registrar chain must be a direct constructor statement.")
            val slot = registrarSlot(owner, receiver)
            return bareCalls.map { registration(it, owner, slot, receiver) }
        }
        val local = when (receiver) {
            is PsiReferenceExpression -> receiver.resolve() as? PsiLocalVariable
                ?: fail(receiver, "Borrowed callback registrars are not reviewed.")
            is PsiNewExpression -> {
                val end = requireNotNull(end)
                val declaration = end.parent as? PsiLocalVariable
                    ?: fail(end, "An allocation-rooted registrar chain must initialize a direct constructor local.")
                if (declaration.initializer != end) fail(end, "The registrar chain result escapes its local initializer.")
                declaration
            }
            else -> fail(call, "The registrar chain must start at its local or a direct fresh allocation in that local's initializer.")
        }
        tick(call)
        groups[local]?.let { return listOf(it[call] ?: fail(call, "The call is outside the reviewed registrar uses.")) }
        val initializer = local.initializer ?: fail(local, "The registrar initializer is unavailable.")
        val initialCall = (initializer as? PsiMethodCallExpression)?.let(::firstCall)
        val allocation = (initialCall?.methodExpression?.qualifierExpression ?: initializer) as? PsiNewExpression
            ?: fail(local, "Aliased or factory-rooted registrar initializers are not reviewed.")
        if (local.parent.parent != constructor.body)
            fail(local, "The registrar must be a direct constructor local.")
        val slot = registrarSlot(owner, allocation)
        val references = descendants.descendants(page).filterIsInstance<PsiReferenceExpression>().filter { it.resolve() == local }
        if (references.size > MAX_REGISTRATIONS) countBound(local)
        // Audit every use before publishing any member of the group. A later eager
        // call, alias or callback capture must invalidate the first registration too.
        val calls = mutableListOf<PsiMethodCallExpression>()
        if (initialCall != null && chain(initialCall, calls) != initializer)
            fail(initializer, "The complete allocation-rooted chain must be the local initializer.")
        if (references.isEmpty() && calls.isEmpty()) fail(local, "The registrar has no reviewed registration sites.")
        for (reference in references) {
            tick(reference)
            val expression = reference.parent as? PsiReferenceExpression
            val start = expression?.parent as? PsiMethodCallExpression
                ?: fail(reference, "Every registrar use must be the receiver of a reviewed registration chain.")
            if (expression.qualifierExpression != reference || start.methodExpression != expression)
                fail(reference, "Every registrar use must be the receiver of a reviewed registration chain.")
            val use = chain(start, calls)
            if (use.parent !is PsiExpressionStatement || use.parent.parent != constructor.body ||
                use.textRange.startOffset <= initializer.textRange.endOffset)
                fail(reference, "Later registrar uses must be direct constructor statements after its completed initializer, without escaping its result.")
        }
        val reviewed = linkedMapOf<PsiMethodCallExpression, StoredActionRegistration>()
        for (use in calls) reviewed[use] = registration(use, owner, slot, allocation)
        groups[local] = reviewed // Only complete groups enter the session-local cache.
        return listOf(reviewed[call] ?: fail(call, "The registration does not belong to its registrar group."))
    }

    private fun callback(call: PsiMethodCallExpression): PsiLambdaExpression? {
        val arguments = call.argumentList.expressions
        if (arguments.size != 2) return null
        val callback = arguments[1] as? PsiLambdaExpression ?: return null
        val parameters = callback.parameterList.parameters
        return callback.takeIf {
            parameters.size == 2 && (parameters.first().type as? PsiClassType)?.resolve()?.qualifiedName == TARGET
        }
    }

    private fun registrarSlot(owner: PsiClass, allocation: PsiNewExpression): PsiField {
        if (allocation.anonymousClass != null || allocation.classReference?.resolve() != owner ||
            owner is PsiCompiledElement || owner.superClass?.qualifiedName != "java.lang.Object" ||
            owner.interfaces.isNotEmpty() || owner.initializers.isNotEmpty() || owner.isInterface ||
            owner.hasModifierProperty(PsiModifier.ABSTRACT))
            fail(allocation, "Only a direct fresh source registrar with Object ancestry is reviewed.")
        source(owner)
        if (owner.fields.size != 1) fail(owner, "The registrar must have one private callback slot without other initialization.")
        val slot = owner.fields.single()
        if (!slot.hasModifierProperty(PsiModifier.PRIVATE) || slot.hasModifierProperty(PsiModifier.STATIC) ||
            slot.hasModifierProperty(PsiModifier.FINAL) || slot.initializer != null)
            fail(slot, "The callback slot must be a private uninitialized instance field.")
        if (owner.constructors.size > 1 || allocation.argumentList?.expressionCount != 0 ||
            owner.constructors.singleOrNull()?.let { it.parameterList.parametersCount != 0 || it.body?.statements?.isNotEmpty() != false } == true)
            fail(allocation, "Registrar construction must be empty and have no callback inputs.")
        return slot
    }

    private fun firstCall(call: PsiMethodCallExpression): PsiMethodCallExpression {
        var first = call
        var depth = 0
        while (first.methodExpression.qualifierExpression is PsiMethodCallExpression) {
            tick(first)
            if (++depth >= MAX_REGISTRATIONS) countBound(call)
            first = first.methodExpression.qualifierExpression as PsiMethodCallExpression
        }
        return first
    }

    private fun chain(first: PsiMethodCallExpression, calls: MutableList<PsiMethodCallExpression>): PsiMethodCallExpression {
        var use = first
        while (true) {
            tick(use)
            if (calls.size >= MAX_REGISTRATIONS) countBound(use)
            calls += use
            val expression = use.parent as? PsiReferenceExpression
            val next = expression?.parent as? PsiMethodCallExpression
            if (expression?.qualifierExpression != use || next?.methodExpression != expression) return use
            use = next
        }
    }

    private fun registration(call: PsiMethodCallExpression, owner: PsiClass, slot: PsiField, allocation: PsiNewExpression): StoredActionRegistration {
        tick(call)
        val method = call.resolveMethod() ?: fail(call, "A sibling registrar use is unresolved.")
        source(method)
        if (method is PsiCompiledElement || method.containingClass != owner) fail(call, "Every registrar use must resolve to its actual fresh source owner.")
        val arguments = call.argumentList.expressions
        val callback = arguments.getOrNull(1) as? PsiLambdaExpression ?: fail(call, "Every registration requires a direct inline callback.")
        if (callback.parameterList.parametersCount != 2 ||
            (callback.parameterList.parameters.first().type as? PsiClassType)?.resolve()?.qualifiedName != TARGET)
            fail(callback, "Every registration requires the reviewed Ajax-target BiConsumer operation.")
        val parameters = method.parameterList.parameters
        if (parameters.size != 2 || arguments.size != 2 || parameters.first().type.canonicalText != "java.lang.String" || slot.type != parameters.last().type || method.hasModifierProperty(PsiModifier.STATIC) ||
            method.isVarArgs || (method.returnType as? PsiClassType)?.resolve() != owner || !call.resolveMethodGenerics().isValidResult ||
            (arguments.first() as? PsiLiteralExpression)?.value !is String)
            fail(method, "The registrar must accept a literal label and unchanged BiConsumer and return its receiver.")
        consumers.inspect(LambdaUtil.getFunctionalInterfaceType(callback, true), 2, callback)
        val statements = method.body?.statements ?: fail(method, "The registrar body is unavailable.")
        if (statements.size != 2) fail(method, "The registrar may only store the callback and return this.")
        val assignment = (statements.first() as? PsiExpressionStatement)?.expression as? PsiAssignmentExpression
            ?: fail(method, "The registrar does not directly store the callback.")
        val left = assignment.lExpression as? PsiReferenceExpression
        val right = assignment.rExpression as? PsiReferenceExpression
        val returned = (statements.last() as? PsiReturnStatement)?.returnValue as? PsiThisExpression
        if (assignment.operationTokenType != JavaTokenType.EQ || left?.resolve() != slot ||
            left.qualifierExpression?.let { it !is PsiThisExpression || it.qualifier != null } == true ||
            right?.resolve() != parameters.last() || right.qualifierExpression != null || returned == null || returned.qualifier != null)
            fail(method, "The registrar must store its unchanged callback in its own slot and return this.")
        val parent = call.parent as? PsiReferenceExpression
        val chained = call.methodExpression.qualifierExpression is PsiMethodCallExpression || call.methodExpression.qualifierExpression is PsiNewExpression ||
            parent?.qualifierExpression == call && (parent.parent as? PsiMethodCallExpression)?.methodExpression == parent
        val at = if (chained) call.methodExpression.referenceNameElement ?: call else call
        return StoredActionRegistration(location(at), location(method), location(callback), location(allocation), location(slot))
    }

    private fun countBound(at: PsiElement): Nothing = fail(at, "The callback registrar exceeds the bounded registration count ($MAX_REGISTRATIONS).")
    private fun location(element: PsiElement) = SourceLocation(element.containingFile.virtualFile.path, element.textOffset)
    private companion object {
        const val TARGET = "org.apache.wicket.ajax.AjaxRequestTarget"
        const val MAX_REGISTRATIONS = 64
    }
}
