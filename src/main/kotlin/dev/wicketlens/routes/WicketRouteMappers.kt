package dev.wicketlens.routes

import com.intellij.psi.*
import com.intellij.psi.search.LocalSearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.PsiUtil
import com.intellij.util.Processor

/** Resolves bounded source mapper declarations. No constructors or mapper methods are executed. */
internal class WicketRouteMappers(
    private val hierarchy: List<PsiClass>,
    private val source: (PsiElement) -> Unit,
    private val tick: () -> Unit,
) {
    enum class Kind { PAGE, PACKAGE, RESOURCE }
    data class Input(val expression: PsiExpression?, val arguments: Map<PsiParameter, Input> = emptyMap())
    data class Mapper(
        val kind: Kind,
        val inputs: List<Input>,
        val notes: List<String> = emptyList(),
        val uncertain: Boolean = false,
    )

    private val unchanged = mutableMapOf<PsiVariable, Boolean>()
    val reviewedCalls = mutableSetOf<PsiMethodCallExpression>()

    fun bindings(method: PsiMethod, call: PsiMethodCallExpression, caller: Map<PsiParameter, Input>): Map<PsiParameter, Input>? {
        val parameters = method.parameterList.parameters
        val expressions = call.argumentList.expressions
        if (method.isVarArgs || parameters.size != expressions.size || parameters.any { !unwritten(it, method) }) return null
        return parameters.zip(expressions).associate { (parameter, expression) -> parameter to Input(expression, caller) }
    }

    fun reference(input: Input, local: Boolean = false): PsiExpression? = dereference(input, local, 0).expression

    private fun dereference(input: Input, local: Boolean, depth: Int): Input {
        tick()
        if (depth >= MAX_DEPTH) return Input(null)
        val expression = PsiUtil.skipParenthesizedExprDown(input.expression)
        val variable = (expression as? PsiReferenceExpression)?.resolve()
        val replacement = when (variable) {
            is PsiParameter -> input.arguments[variable]
            is PsiLocalVariable -> variable.initializer?.takeIf {
                local && variable.textOffset < expression.textOffset &&
                    PsiTreeUtil.getParentOfType(variable, PsiMethod::class.java)?.let { method ->
                        variable.parent.parent == method.body && unwritten(variable, method)
                    } == true
            }?.let { Input(it, input.arguments) }
            else -> null
        }
        return if (replacement != null) dereference(replacement, local, depth + 1) else input.copy(expression = expression)
    }

    private fun unwritten(variable: PsiVariable, method: PsiMethod): Boolean = unchanged.getOrPut(variable) {
        val body = method.body ?: return@getOrPut false
        ReferencesSearch.search(variable, LocalSearchScope(body)).forEach(Processor { reference ->
            tick()
            val use = reference.element as? PsiExpression
            use != null && !PsiUtil.isAccessedForWriting(use)
        })
    }

    fun wrapsCurrentRoot(input: Input): String? {
        val (expression, arguments) = dereference(input, local = false, depth = 0)
        val allocation = expression as? PsiNewExpression ?: return null
        val type = allocation.classReference?.resolve() as? PsiClass ?: return null
        val wrapper = type.qualifiedName?.takeIf { it in WRAPPERS } ?: return null
        if (allocation.anonymousClass != null || !allocation.resolveMethodGenerics().isValidResult) return null
        val delegate = reference(Input(allocation.argumentList?.expressions?.firstOrNull(), arguments))
            as? PsiMethodCallExpression ?: return null
        val method = delegate.resolveMethod() ?: return null
        val receiver = delegate.methodExpression.qualifierExpression
        if (method.containingClass?.qualifiedName != APPLICATION || method.name != "getRootRequestMapper" ||
            receiver != null && !(receiver is PsiThisExpression && receiver.qualifier == null) &&
            !(receiver is PsiSuperExpression && receiver.qualifier == null)) return null
        reviewedCalls += delegate
        return wrapper
    }

    fun extract(input: Input, depth: Int = 0, compound: Boolean = false): List<Mapper>? {
        if (depth >= MAX_DEPTH) return null
        if (compound) {
            val parameterResolved = dereference(input, local = false, depth = 0)
            val use = parameterResolved.expression as? PsiReferenceExpression
            val variable = use?.resolve() as? PsiLocalVariable
            if (variable != null && (variable.type as? PsiClassType)?.resolve()?.qualifiedName == COMPOUND)
                return localCompound(variable, use, parameterResolved.arguments, depth)
        }
        val (expression, arguments) = dereference(input, local = true, depth = 0)
        return when (expression) {
            is PsiNewExpression -> constructor(expression, arguments, depth, compound)
            is PsiMethodCallExpression -> {
                val method = expression.resolveMethod()?.takeIf { expression.resolveMethodGenerics().isValidResult } ?: return null
                if (compound && method.name == "add" && method.containingClass?.qualifiedName == COMPOUND) {
                    val earlier = extract(Input(expression.methodExpression.qualifierExpression, arguments), depth + 1, true) ?: return null
                    val added = extract(Input(expression.argumentList.expressions.singleOrNull(), arguments), depth + 1, true) ?: return null
                    reviewedCalls += expression
                    earlier + added
                } else {
                    val owner = method.containingClass ?: return null
                    val qualifier = expression.methodExpression.qualifierExpression
                    val self = qualifier == null || qualifier is PsiThisExpression && qualifier.qualifier == null ||
                        qualifier is PsiSuperExpression && qualifier.qualifier == null
                    val static = method.hasModifierProperty(PsiModifier.STATIC)
                    val resolvedQualifier = (qualifier as? PsiReferenceExpression)?.resolve()
                    if (!closed(method, qualifier is PsiSuperExpression) ||
                        (if (static) qualifier != null && resolvedQualifier != owner
                        else !self || owner !in hierarchy)) return null
                    // Static factory classes outside the application may initialize only through constants.
                    if (static && owner !in hierarchy &&
                        (owner.superClass?.qualifiedName != "java.lang.Object" || owner.interfaces.isNotEmpty() ||
                            owner.initializers.isNotEmpty() || owner.fields.any { it.hasInitializer() && it.computeConstantValue() == null })) return null
                    val result = (method.body?.statements?.singleOrNull() as? PsiReturnStatement)?.returnValue ?: return null
                    val bound = bindings(method, expression, arguments) ?: return null
                    source(method)
                    reviewedCalls += expression
                    extract(Input(result, bound), depth + 1, compound)?.map {
                        it.copy(notes = it.notes + "Mapper returned by source factory ${method.name}.")
                    }
                }
            }
            else -> null
        }
    }

    private fun localCompound(variable: PsiLocalVariable, use: PsiReferenceExpression, arguments: Map<PsiParameter, Input>,
        depth: Int): List<Mapper>? {
        val method = PsiTreeUtil.getParentOfType(variable, PsiMethod::class.java) ?: return null
        val body = method.body ?: return null
        val declaration = variable.parent as? PsiDeclarationStatement ?: return null
        val initializer = variable.initializer ?: return null
        if (declaration.parent != body || use.textOffset <= initializer.textOffset || !unwritten(variable, method)) return null
        val additions = mutableListOf<PsiMethodCallExpression>()
        val complete = ReferencesSearch.search(variable, LocalSearchScope(body)).forEach(Processor { reference ->
            tick()
            val element = reference.element
            if (element == use) return@Processor true
            val member = element.parent as? PsiReferenceExpression ?: return@Processor false
            val call = member.parent as? PsiMethodCallExpression ?: return@Processor false
            val target = call.resolveMethod() ?: return@Processor false
            if (member.qualifierExpression != element || member.referenceName != "add" ||
                target.containingClass?.qualifiedName != COMPOUND || call.parent !is PsiExpressionStatement ||
                call.parent.parent != body || call.textOffset >= use.textOffset) return@Processor false
            additions += call
            additions.size <= MAX_DEPTH
        })
        if (!complete) return null
        val mounts = extract(Input(initializer, arguments), depth + 1, compound = true)?.toMutableList() ?: return null
        for (addition in additions.sortedBy { it.textOffset }) {
            mounts += extract(Input(addition.argumentList.expressions.singleOrNull(), arguments), depth + 1, compound = true)
                ?: return null
        }
        return mounts
    }

    private fun constructor(allocation: PsiNewExpression, bindings: Map<PsiParameter, Input>, depth: Int, compound: Boolean): List<Mapper>? {
        val type = allocation.anonymousClass?.baseClassType?.resolve() ?: allocation.classReference?.resolve() as? PsiClass ?: return null
        val actual = allocation.resolveMethodGenerics()
        val method = actual.element as? PsiMethod
        val arguments = allocation.argumentList?.expressions.orEmpty().map { Input(it, bindings) }
        val name = type.qualifiedName
        val kind = kind(name)
        if (kind != null) {
            val notes = buildList {
                add("Source ${type.name} registration.")
                if (allocation.anonymousClass != null) add("Anonymous mapper overrides or initialization are unreviewed.")
                if (arguments.size == 3) add(EXPLICIT_MAPPER_CONFIGURATION)
                if (!actual.isValidResult || method?.containingClass != type) add("The mapper constructor is unresolved.")
            }
            return listOf(Mapper(kind, arguments, notes,
                allocation.anonymousClass != null || !actual.isValidResult || method?.containingClass != type))
        }
        if (allocation.anonymousClass != null || method == null || !actual.isValidResult || method.containingClass != type) return null
        if (compound && name == COMPOUND && arguments.isEmpty()) return emptyList()
        if (name in WRAPPERS) {
            val nested = extract(arguments.firstOrNull() ?: return null, depth + 1, compound) ?: return null
            return nested.map { it.copy(notes = it.notes + "${type.name} wraps this declaration. Runtime URL handling is unreviewed.",
                uncertain = it.uncertain || name == CRYPTO) }
        }
        if (type is PsiCompiledElement || type.interfaces.isNotEmpty() || type.initializers.isNotEmpty() ||
            type.fields.isNotEmpty() || type.methods.any { !it.isConstructor }) return null
        val base = type.superClass ?: return null
        val baseKind = kind(base.qualifiedName) ?: return null
        val delegation = (method.body?.statements?.singleOrNull() as? PsiExpressionStatement)?.expression as? PsiMethodCallExpression
            ?: return null
        if (delegation.methodExpression.referenceName != "super" || !delegation.resolveMethodGenerics().isValidResult ||
            delegation.resolveMethod()?.containingClass != base || method.isVarArgs) return null
        source(type)
        val env = method.parameterList.parameters.zip(arguments).toMap()
        val delegated = delegation.argumentList.expressions
        val notes = buildList {
            add("Source mapper subclass ${type.qualifiedName} directly forwards its constructor to ${base.name}.")
            if (delegated.size == 3) add(EXPLICIT_MAPPER_CONFIGURATION)
        }
        return listOf(Mapper(baseKind, delegated.map { Input(it, env) }, notes))
    }

    companion object {
        const val EXPLICIT_MAPPER_CONFIGURATION = "The explicit mapper encoder or configuration is unreviewed."
        const val APPLICATION = "org.apache.wicket.Application"
        const val COMPOUND = "org.apache.wicket.request.mapper.CompoundRequestMapper"
        const val COMPOUND_INTERFACE = "org.apache.wicket.request.mapper.ICompoundRequestMapper"
        const val CRYPTO = "org.apache.wicket.core.request.mapper.CryptoMapper"
        private val WRAPPERS = setOf(CRYPTO, "org.apache.wicket.protocol.https.HttpsMapper")
        private const val MAX_DEPTH = 8

        fun closed(method: PsiMethod, superCall: Boolean) = method !is PsiCompiledElement &&
            (superCall || method.hasModifierProperty(PsiModifier.PRIVATE) || method.hasModifierProperty(PsiModifier.FINAL) ||
                method.hasModifierProperty(PsiModifier.STATIC) || method.containingClass?.hasModifierProperty(PsiModifier.FINAL) == true)

        private fun kind(name: String?): Kind? = when (name) {
            WicketRouteAnalyzer.MOUNTED_MAPPER -> Kind.PAGE
            "org.apache.wicket.core.request.mapper.PackageMapper" -> Kind.PACKAGE
            "org.apache.wicket.core.request.mapper.ResourceMapper" -> Kind.RESOURCE
            else -> null
        }
    }
}
