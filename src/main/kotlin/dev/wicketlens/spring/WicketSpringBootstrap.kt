package dev.wicketlens.spring

import com.intellij.openapi.progress.ProgressManager
import com.intellij.psi.*
import com.intellij.psi.util.PsiTreeUtil

/** Bounded, same-instance setup interpreter. Unsupported statements leave the whole setup unknown. */
internal class WicketSpringBootstrap(private val hierarchy: List<PsiClass>) {
    data class Injection(val proxies: WicketSpringSetup.Proxies, val configurations: List<String>)
    data class Review(val injections: List<Injection>, val constructed: Int)
    private sealed interface Value {
        data class Context(val creation: PsiNewExpression) : Value
        data class Injector(val id: Int, val injection: Injection) : Value
        data class Flag(val value: Boolean) : Value
    }
    private val creations = mutableSetOf<PsiNewExpression>()
    private val injections = mutableListOf<Injection>()
    private val added = mutableSetOf<Int>()
    private val stack = mutableSetOf<PsiMethod>()
    private var allocations = 0
    private var statements = 0

    fun review(): Review? {
        for (type in hierarchy.asReversed()) {
            ProgressManager.checkCanceled()
            val methods = type.methods.filter { it.name == "init" && it.parameterList.parametersCount == 0 && !it.hasModifierProperty(PsiModifier.STATIC) }
            if (methods.size > 1) return null
            val method = methods.singleOrNull() ?: continue
            val body = method.body?.statements?.filterNot { it is PsiEmptyStatement } ?: return null
            val first = (body.firstOrNull() as? PsiExpressionStatement)?.expression as? PsiMethodCallExpression ?: return null
            if (!WicketSpringSetup.superInit(first) || !execute(method, body.drop(1))) return null
        }
        if (hierarchy.any { type -> PsiTreeUtil.findChildrenOfType(type, PsiNewExpression::class.java).any {
                ProgressManager.checkCanceled()
                it.resolveConstructor()?.containingClass?.qualifiedName == WicketSpringSupport.INJECTOR && it !in creations
            } }) return null
        return Review(injections.toList(), allocations)
    }

    private fun execute(method: PsiMethod, body: List<PsiStatement>): Boolean {
        ProgressManager.checkCanceled()
        if (stack.size >= 8 || !stack.add(method)) return false
        try {
            val values = mutableMapOf<PsiLocalVariable, Value>()
            for (statement in body) {
                ProgressManager.checkCanceled()
                if (++statements > 128) return false
                when (statement) {
                    is PsiEmptyStatement -> Unit
                    is PsiDeclarationStatement -> {
                        val variable = statement.declaredElements.singleOrNull() as? PsiLocalVariable ?: return false
                        values[variable] = variable.initializer?.let { value(it, values) } ?: return false
                    }
                    is PsiExpressionStatement -> {
                        val expression = WicketSpringSupport.unwrap(statement.expression)
                        if (expression is PsiNewExpression) {
                            if (value(expression, values) !is Value.Injector) return false
                            continue
                        }
                        val call = expression as? PsiMethodCallExpression ?: return false
                        if (!call.resolveMethodGenerics().isValidResult) return false
                        val target = call.resolveMethod() ?: return false
                        val qualifier = call.methodExpression.qualifierExpression
                        if (target.name == "add" && target.containingClass?.qualifiedName == "org.apache.wicket.util.listener.ListenerCollection") {
                            val getter = qualifier?.let(WicketSpringSupport::unwrap) as? PsiMethodCallExpression ?: return false
                            if (!getter.resolveMethodGenerics().isValidResult || getter.resolveMethod()?.name != "getComponentInstantiationListeners" ||
                                getter.resolveMethod()?.containingClass?.qualifiedName != "org.apache.wicket.Application" || getter.argumentList.expressionCount != 0 ||
                                !self(getter.methodExpression.qualifierExpression)) return false
                            val injector = call.argumentList.expressions.singleOrNull()?.let { value(it, values) } as? Value.Injector ?: return false
                            if (!added.add(injector.id)) return false
                            injections += injector.injection
                        } else if (target.name == "mountPage" && target.containingClass?.qualifiedName == "org.apache.wicket.protocol.http.WebApplication" && self(qualifier)) {
                            val args = call.argumentList.expressions
                            if (args.size != 2 || (WicketSpringSupport.unwrap(args[0]) as? PsiLiteralExpression)?.value !is String ||
                                WicketSpringSupport.unwrap(args[1]) !is PsiClassObjectAccessExpression) return false
                        } else if (target.name == "refresh" && call.argumentList.expressionCount == 0 &&
                            target.containingClass?.qualifiedName == "org.springframework.context.support.AbstractApplicationContext" &&
                            qualifier?.let { value(it, values) } is Value.Context) {
                            // Standard local context refresh; no application receiver or custom dispatch.
                        } else {
                            if (target.containingClass !in hierarchy || target is PsiCompiledElement || target.hasModifierProperty(PsiModifier.STATIC) ||
                                target.parameterList.parametersCount != 0 || call.argumentList.expressionCount != 0 || target.returnType != PsiTypes.voidType() ||
                                !(target.hasModifierProperty(PsiModifier.PRIVATE) || target.hasModifierProperty(PsiModifier.FINAL)) || !self(qualifier)) return false
                            val statements = target.body?.statements?.toList() ?: return false
                            if (!execute(target, statements)) return false
                        }
                    }
                    else -> return false
                }
            }
            return true
        } finally { stack.remove(method) }
    }

    private fun value(expression: PsiExpression, values: Map<PsiLocalVariable, Value>): Value? {
        ProgressManager.checkCanceled()
        return when (val expr = WicketSpringSupport.unwrap(expression)) {
            is PsiLiteralExpression -> (expr.value as? Boolean)?.let(Value::Flag)
            is PsiReferenceExpression -> when (val target = expr.resolve()) {
                is PsiLocalVariable -> values[target]
                is PsiField -> if (target.hasModifierProperty(PsiModifier.FINAL) && target.type == PsiTypes.booleanType())
                    (target.initializer?.let(WicketSpringSupport::unwrap) as? PsiLiteralExpression)?.value?.let { (it as? Boolean)?.let(Value::Flag) } else null
                else -> null
            }
            is PsiNewExpression -> {
                if (expr.anonymousClass != null || !expr.resolveMethodGenerics().isValidResult) return null
                val owner = expr.resolveConstructor()?.containingClass?.qualifiedName ?: return null
                val args = expr.argumentList?.expressions ?: return null
                if (owner in setOf("org.springframework.context.support.GenericApplicationContext", "org.springframework.context.support.StaticApplicationContext",
                        "org.springframework.context.annotation.AnnotationConfigApplicationContext")) {
                    if (args.any { WicketSpringSupport.unwrap(it) !is PsiClassObjectAccessExpression && WicketSpringSupport.unwrap(it) !is PsiLiteralExpression }) return null
                    Value.Context(expr)
                } else if (owner == WicketSpringSupport.INJECTOR) {
                    val receiver = args.firstOrNull()?.let(WicketSpringSupport::unwrap) as? PsiThisExpression ?: return null
                    if (receiver.qualifier != null || args.size !in 1..3 || allocations >= 16) return null
                    val context = args.getOrNull(1)?.let { argument ->
                        val unwrapped = WicketSpringSupport.unwrap(argument)
                        if (unwrapped is PsiReferenceExpression && unwrapped.resolve() is PsiLocalVariable)
                            (values[unwrapped.resolve()] as? Value.Context)?.creation ?: return null
                        else unwrapped
                    }
                    val mode = if (args.size < 3) WicketSpringSetup.Proxies.WRAPPED else when ((value(args[2], values) as? Value.Flag)?.value) {
                        true -> WicketSpringSetup.Proxies.WRAPPED
                        false -> WicketSpringSetup.Proxies.RAW
                        null -> WicketSpringSetup.Proxies.UNKNOWN
                    }
                    creations += expr
                    Value.Injector(++allocations, Injection(mode, context?.let(WicketSpringSetup::configurations).orEmpty()))
                } else null
            }
            else -> null
        }
    }

    private fun self(expression: PsiExpression?): Boolean = when (val expr = expression?.let(WicketSpringSupport::unwrap)) {
        null -> true
        is PsiThisExpression -> expr.qualifier == null
        is PsiSuperExpression -> expr.qualifier == null
        else -> false
    }
}
