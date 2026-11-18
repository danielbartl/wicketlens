package dev.wicketlens.analysis

import com.intellij.psi.*
import com.intellij.psi.util.PsiTreeUtil

/** Constructor-reference provenance for optional form declarations, not a runtime effect proof. */
internal class WicketFormRegistration(
    private val depth: Int,
    private val tick: (PsiElement) -> Unit,
    private val source: (PsiElement) -> Unit,
    private val fail: (PsiElement, String) -> Nothing,
    private val bound: (PsiElement) -> Nothing,
    private val descend: (PsiElement) -> List<PsiElement>,
) {
    data class Registration(val creation: PsiNewExpression, val reference: PsiMethodReferenceExpression,
        val wrapper: PsiClass, val inputConstructor: PsiMethod, val field: PsiField, val constructors: Set<PsiMethod>)

    companion object {
        fun hasFactory(type: PsiClass): Boolean = type !is PsiCompiledElement && type.fields.any { field ->
            field.hasModifierProperty(PsiModifier.PRIVATE) && field.hasModifierProperty(PsiModifier.FINAL) &&
                !field.hasModifierProperty(PsiModifier.STATIC) && factory(field.type)
        }
        private fun factory(type: PsiType): Boolean {
            val result = LambdaUtil.getFunctionalInterfaceReturnType(type)
            val actual = if (result is PsiWildcardType) result.extendsBound else result
            return LambdaUtil.getFunctionalInterfaceMethod(type)?.parameterList?.parametersCount == 2 &&
                (actual as? PsiClassType)?.resolve()?.let(WicketComponentTypes::isFormComponent) == true
        }
    }
    private fun <T : PsiElement> all(at: PsiElement, kind: Class<T>) = descend(at).filter(kind::isInstance).map(kind::cast)
    private fun self(e: PsiExpression?) = e == null || e is PsiThisExpression && e.qualifier == null
    private fun variable(e: PsiExpression?) = (e as? PsiReferenceExpression)?.takeIf { self(it.qualifierExpression) }?.resolve()
    private fun forwarded(e: PsiExpression?, values: Set<PsiParameter>): Boolean = when (e) {
        is PsiReferenceExpression -> variable(e) in values
        is PsiMethodCallExpression -> e.resolveMethod()?.let { it.name == "requireNonNull" && it.containingClass?.qualifiedName == "java.util.Objects" } == true &&
            e.argumentList.expressions.size == 1 && variable(e.argumentList.expressions.single()) in values
        else -> false
    }

    fun discover(creation: PsiNewExpression): Registration? {
        val args = creation.argumentList?.expressions ?: return null
        val refs = args.filterIsInstance<PsiMethodReferenceExpression>()
        if (refs.isEmpty()) return null
        val wrapper = creation.classReference?.resolve() as? PsiClass ?: return null
        if (!hasFactory(wrapper)) return null
        source(creation)
        if (creation.anonymousClass != null || creation.qualifier != null || refs.size != 1)
            fail(creation, "One direct unbound input constructor registration is required.")
        val reference = refs.single()
        if (reference.multiResolve(false).singleOrNull()?.isValidResult != true)
            fail(reference, "The constructor reference is incompatible or ambiguous.")
        val input = WicketComponentIdentity.unboundConstructor(reference) ?: fail(reference, "A static/top-level source input constructor is required.")
        if (input is PsiCompiledElement || input.parameterList.parametersCount != 2 || input.parameterList.parameters[0].type.canonicalText != "java.lang.String" ||
            input.containingClass?.let { WicketComponentTypes.isFormComponent(it) && WicketComponentTypes.ownsMarkup(it) } != true)
            fail(reference, "A source String/model composite input constructor is required.")
        source(input)
        val initial = creation.resolveConstructor() ?: fail(creation, "The registered wrapper constructor is unavailable.")
        val param = initial.parameterList.parameters.getOrNull(args.indexOf(reference)) ?: fail(reference, "The registration parameter is unavailable.")
        if (!factory(param.type)) fail(param, "The registration needs a form-input functional interface.")
        val chain = linkedSetOf<PsiMethod>()
        var stored: PsiField? = null
        fun visit(ctor: PsiMethod, values: Set<PsiParameter>) {
            tick(ctor)
            if (chain.size >= depth) bound(ctor)
            if (!chain.add(ctor) || ctor is PsiCompiledElement) fail(ctor, "Constructor forwarding is recursive or unavailable.")
            source(ctor)
            val statements = ctor.body?.statements ?: fail(ctor, "Constructor forwarding is unavailable.")
            statements.forEachIndexed { index, statement ->
                val expression = (statement as? PsiExpressionStatement)?.expression
                val delegation = expression as? PsiMethodCallExpression
                if (index == 0 && delegation?.methodExpression?.referenceName in setOf("this", "super")) {
                    val target = delegation!!.resolveMethod() ?: fail(delegation, "Constructor delegation is unavailable.")
                    val parameters = target.parameterList.parameters.zip(delegation.argumentList.expressions).filter { forwarded(it.second, values) }.map { it.first }.toSet()
                    if (parameters.size > 1) fail(delegation, "The input factory is forwarded more than once.")
                    if (parameters.isNotEmpty()) visit(target, parameters)
                } else {
                    val assignment = expression as? PsiAssignmentExpression ?: fail(statement, "Registered wrapper constructors may only delegate and store the input factory.")
                    val field = variable(assignment.lExpression) as? PsiField ?: fail(assignment, "The factory storage is unavailable.")
                    if (assignment.operationTokenType != JavaTokenType.EQ || !forwarded(assignment.rExpression, values) || stored != null ||
                        field.containingClass != ctor.containingClass || !hasFactory(ctor.containingClass!!) || !factory(field.type) ||
                        !field.hasModifierProperty(PsiModifier.PRIVATE) || !field.hasModifierProperty(PsiModifier.FINAL) || field.hasModifierProperty(PsiModifier.STATIC) || field.initializer != null)
                        fail(assignment, "The input factory needs one private final instance assignment.")
                    stored = field
                }
            }
            all(ctor, PsiReferenceExpression::class.java).filter { it.resolve() in values }.forEach { use ->
                // References are allowed only as whole arguments to delegation/requireNonNull or the direct storage RHS.
                var value: PsiElement = use
                if ((use.parent as? PsiExpressionList)?.parent is PsiMethodCallExpression) {
                    val call = use.parent.parent as PsiMethodCallExpression
                    if (call.resolveMethod()?.let { it.name == "requireNonNull" && it.containingClass?.qualifiedName == "java.util.Objects" } == true) value = call
                }
                val assignment = value.parent as? PsiAssignmentExpression
                val call = (value.parent as? PsiExpressionList)?.parent as? PsiMethodCallExpression
                if (!(assignment?.rExpression == value && variable(assignment.lExpression) == stored) &&
                    !(call?.methodExpression?.referenceName in setOf("this", "super") && call?.parent?.parent == ctor.body))
                    fail(use, "The registered factory escapes constructor forwarding.")
            }
        }
        visit(initial, setOf(param))
        return Registration(creation, reference, wrapper, input, stored ?: fail(creation, "No unique input-factory storage was found."), chain)
    }

    fun invocation(registration: Registration, factory: PsiMethod): PsiMethodCallExpression {
        source(factory)
        val statements = factory.body?.statements ?: fail(factory, "The registered input factory is unavailable.")
        val returned = statements.lastOrNull() as? PsiReturnStatement ?: fail(factory, "The factory needs a final return.")
        val returnedVariable = variable(returned.returnValue) as? PsiLocalVariable
        val call = if (statements.size == 1) returned.returnValue as? PsiMethodCallExpression else {
            if (statements.size !in 2..3 || returnedVariable?.parent != statements.first()) fail(factory, "Only a direct input-factory result is supported.")
            if ((statements.first() as? PsiDeclarationStatement)?.declaredElements?.size != 1) fail(factory, "The factory result needs one local declaration.")
            if (statements.size == 3 && !idGuard(statements[1], returnedVariable, factory.parameterList.parameters[0]))
                fail(statements[1], "Only the null/ID rejection guard is supported.")
            returnedVariable.initializer as? PsiMethodCallExpression
        } ?: fail(factory, "The input must be returned from the registered functional interface.")
        val fieldRef = call.methodExpression.qualifierExpression as? PsiReferenceExpression
        if (variable(fieldRef) != registration.field || call.resolveMethod() != LambdaUtil.getFunctionalInterfaceMethod(registration.field.type) ||
            call.argumentList.expressions.map(::variable) != factory.parameterList.parameters.toList())
            fail(call, "The registered factory must receive the unchanged ID/model parameters.")
        val owner = registration.field.containingClass!!
        source(owner)
        all(owner, PsiReferenceExpression::class.java).filter { it.resolve() == registration.field }.forEach { use ->
            val assignment = use.parent as? PsiAssignmentExpression
            if (use != fieldRef && !(assignment?.lExpression == use && PsiTreeUtil.getParentOfType(assignment, PsiMethod::class.java) in registration.constructors))
                fail(use, "The stored input factory escapes its constructor/invocation contract.")
        }
        return call
    }

    private fun idGuard(statement: PsiStatement, value: PsiLocalVariable, id: PsiParameter): Boolean {
        val guard = statement as? PsiIfStatement ?: return false
        if (guard.elseBranch != null) return false
        val condition = guard.condition as? PsiPolyadicExpression ?: return false
        if (condition.operationTokenType != JavaTokenType.OROR || condition.operands.size != 2) return false
        fun nullCheck(e: PsiExpression?): Boolean = e is PsiBinaryExpression && e.operationTokenType == JavaTokenType.EQEQ &&
            variable(e.lOperand) == value && e.rOperand is PsiLiteralExpression && (e.rOperand as PsiLiteralExpression).value == null
        if (!nullCheck(condition.operands[0])) return false
        val negation = condition.operands[1] as? PsiPrefixExpression ?: return false
        if (negation.operationTokenType != JavaTokenType.EXCL) return false
        val equals = negation.operand as? PsiMethodCallExpression ?: return false
        if (equals.resolveMethod()?.let { it.name == "equals" && it.containingClass?.qualifiedName == "java.lang.String" } != true ||
            variable(equals.methodExpression.qualifierExpression) != id) return false
        val getId = equals.argumentList.expressions.singleOrNull() as? PsiMethodCallExpression ?: return false
        if (getId.resolveMethod()?.let { it.name == "getId" && it.containingClass?.qualifiedName == "org.apache.wicket.Component" } != true ||
            variable(getId.methodExpression.qualifierExpression) != value || getId.argumentList.expressions.isNotEmpty()) return false
        val thrown = when (val branch = guard.thenBranch) {
            is PsiThrowStatement -> branch
            is PsiBlockStatement -> branch.codeBlock.statements.singleOrNull() as? PsiThrowStatement
            else -> null
        } ?: return false
        val exception = thrown.exception as? PsiNewExpression ?: return false
        fun message(e: PsiExpression, level: Int = 0): Boolean {
            tick(e)
            if (level >= depth) bound(e)
            return when (e) {
                is PsiLiteralExpression -> e.value is String
                is PsiReferenceExpression -> variable(e) == id
                is PsiParenthesizedExpression -> e.expression?.let { message(it, level + 1) } == true
                is PsiPolyadicExpression -> e.operationTokenType == JavaTokenType.PLUS && e.operands.all { message(it, level + 1) }
                is PsiConditionalExpression -> nullCheck(e.condition) && e.thenExpression?.let { message(it, level + 1) } == true &&
                    e.elseExpression?.let { message(it, level + 1) } == true
                is PsiMethodCallExpression -> e.resolveMethod()?.let { it.name == "getId" && it.containingClass?.qualifiedName == "org.apache.wicket.Component" } == true &&
                    variable(e.methodExpression.qualifierExpression) == value && e.argumentList.expressions.isEmpty()
                else -> false
            }
        }
        return exception.classReference?.qualifiedName in setOf("java.lang.IllegalStateException", "java.lang.IllegalArgumentException") &&
            exception.anonymousClass == null && exception.qualifier == null &&
            exception.argumentList?.expressions?.let { it.size <= 1 && it.all { argument -> message(argument) } } == true
    }
}
