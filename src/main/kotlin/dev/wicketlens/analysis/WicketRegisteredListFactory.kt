package dev.wicketlens.analysis

import com.intellij.psi.*
import com.intellij.psi.util.InheritanceUtil
import com.intellij.psi.util.PsiTreeUtil
import dev.wicketlens.model.*

/** A source protocol, not arbitrary static factory execution or a general custom repeater contract. */
internal class WicketRegisteredListFactory(
    private val tick: (PsiElement) -> Unit,
    private val source: (PsiElement) -> Unit,
    private val sources: MutableSet<String>,
    private val fail: (PsiElement, String) -> Nothing,
) {
    data class Result(val type: PsiClass, val original: PsiClass, val id: PsiExpression, val callback: PsiLambdaExpression,
        val behaviors: List<PsiClass>, val metadata: RegisteredListPopulation)

    companion object {
        fun candidate(call: PsiMethodCallExpression): Boolean {
            val method = call.resolveMethod() ?: return false
            val output = (method.returnType as? PsiClassType)?.resolve() ?: return false
            return method !is PsiCompiledElement && method.hasModifierProperty(PsiModifier.STATIC) && InheritanceUtil.isInheritor(output, WicketRepeaterContract.LIST) &&
                call.argumentList.expressions.filterIsInstance<PsiLambdaExpression>().any { lambda ->
                    lambda.parameterList.parameters.size == 2 && (lambda.parameterList.parameters[0].type as? PsiClassType)?.resolve()?.qualifiedName == WicketRepeaterContract.ITEM &&
                        LambdaUtil.getFunctionalInterfaceReturnType(LambdaUtil.getFunctionalInterfaceType(lambda, true)) == PsiTypes.voidType()
                }
        }
    }

    private fun <T : PsiElement> descendants(at: PsiElement, type: Class<T>): List<T> {
        val result = linkedSetOf<T>()
        at.accept(object : JavaRecursiveElementWalkingVisitor() {
            override fun visitElement(element: PsiElement) { tick(element); if (type.isInstance(element)) result += type.cast(element); super.visitElement(element) }
        })
        return result.toList()
    }
    private fun own(e: PsiExpression?) = e == null || e is PsiThisExpression && e.qualifier == null
    private fun ref(e: PsiExpression?, target: PsiElement) = e is PsiReferenceExpression && e.resolve() == target && own(e.qualifierExpression)
    private fun call(statement: PsiStatement?) = (statement as? PsiExpressionStatement)?.expression as? PsiMethodCallExpression
    private fun actual(e: PsiExpression, values: Map<PsiParameter, PsiExpression>): PsiExpression =
        (e as? PsiReferenceExpression)?.let { values[it.resolve()] } ?: e

    fun resolve(call: PsiMethodCallExpression): Result {
        val entry = call.resolveMethod() ?: fail(call, "The registered list factory does not resolve.")
        if (!call.resolveMethodGenerics().isValidResult) fail(call, "The registered list factory's generic arguments are unresolved.")
        val owner = entry.containingClass ?: fail(call, "The source factory class is unavailable.")
        source(owner)
        if (owner.qualifiedName == null || owner.containingClass != null && !owner.hasModifierProperty(PsiModifier.STATIC))
            fail(owner, "Registered list factories need a named top-level or static member source class.")
        if (owner.superClass?.qualifiedName != WicketRepeaterContract.LIST) fail(owner, "Only a direct source ListView subtype is reviewed for item callbacks.")
        val factorySources = mutableListOf<SourceLocation>()
        val active = mutableSetOf<PsiMethod>()
        fun walk(method: PsiMethod, arguments: List<PsiExpression>): Triple<PsiNewExpression, PsiParameter, PsiLambdaExpression> {
            source(method)
            if (method in active || active.size >= 12) fail(method, "Registered factory forwarding is recursive or exceeds 12 source levels.")
            if (method.containingClass != owner || !method.hasModifierProperty(PsiModifier.STATIC) || method.isVarArgs || method.hasModifierProperty(PsiModifier.SYNCHRONIZED) ||
                method.parameterList.parametersCount != arguments.size) fail(method, "Factory forwarding must stay in the same source class with direct parameters.")
            active += method; factorySources += location(method)
            try {
                val values = method.parameterList.parameters.zip(arguments).toMap()
                val statements = method.body?.statements ?: fail(method, "The source factory body is unavailable.")
                val returned = (statements.lastOrNull() as? PsiReturnStatement)?.returnValue ?: fail(method, "The factory needs a direct final return.")
                if (returned is PsiMethodCallExpression) {
                    val qualifier = returned.methodExpression.qualifierExpression
                    if (statements.size != 1 || qualifier != null && (qualifier !is PsiReferenceExpression || qualifier.resolve() != owner) ||
                        !returned.resolveMethodGenerics().isValidResult) fail(returned, "Only direct source factory forwarding is reviewed.")
                    val target = returned.resolveMethod() ?: fail(returned, "The forwarding target is unavailable.")
                    val forwarded = returned.argumentList.expressions.map { e ->
                        if (e !is PsiLiteralExpression && (e !is PsiReferenceExpression || e.resolve() !in values)) fail(e, "Forwarded factory arguments must be parameters or literals.")
                        actual(e, values)
                    }
                    return walk(target, forwarded)
                }
                val creation = returned as? PsiNewExpression ?: fail(returned, "The factory must return one fresh anonymous ListView subtype.")
                val anonymous = creation.anonymousClass ?: fail(creation, "One source population bridge override is required.")
                if (creation.qualifier != null || anonymous.baseClassType.resolve() != owner || anonymous.fields.isNotEmpty() || anonymous.initializers.isNotEmpty() ||
                    anonymous.methods.count { !it.isConstructor } != 1) fail(anonymous, "The source bridge has unreviewed anonymous state or overrides.")
                if (creation.argumentList?.expressions.orEmpty().any { it !is PsiLiteralExpression && (it !is PsiReferenceExpression || it.resolve() !in values || it.qualifierExpression != null) })
                    fail(creation, "Registered list construction must use direct factory parameters or literals.")
                val bridge = anonymous.methods.single { !it.isConstructor }
                if (!WicketRepeaterContract.callback(bridge, Repetition.LIST_VIEW)) fail(bridge, "The source bridge must override ListView.populateItem.")
                val forwarding = call(bridge.body?.statements?.singleOrNull()) ?: fail(bridge, "The population bridge must make one direct callback invocation.")
                val callbackParameter = (forwarding.methodExpression.qualifierExpression as? PsiReferenceExpression)?.resolve() as? PsiParameter
                    ?: fail(forwarding, "The bridge must invoke its captured factory parameter.")
                if (callbackParameter.declarationScope != method || forwarding.resolveMethod() != LambdaUtil.getFunctionalInterfaceMethod(callbackParameter.type) ||
                    LambdaUtil.getFunctionalInterfaceReturnType(callbackParameter.type) != PsiTypes.voidType()) fail(forwarding, "The captured item callback SAM is unproved.")
                val item = bridge.parameterList.parameters.single()
                val args = forwarding.argumentList.expressions
                val model = args.getOrNull(1) as? PsiMethodCallExpression
                if (args.size != 2 || !ref(args[0], item) || model?.argumentList?.expressionCount != 0 || !ref(model.methodExpression.qualifierExpression, item) ||
                    model.resolveMethod()?.let { it.name == "getModelObject" && it.containingClass?.qualifiedName in setOf(WicketRepeaterContract.ITEM, "org.apache.wicket.IGenericComponent") } != true)
                    fail(forwarding, "The bridge must forward its item and that item's model object unchanged.")
                val callback = values[callbackParameter] as? PsiLambdaExpression ?: fail(callbackParameter, "A direct source item/data lambda is required.")
                if (callback.body !is PsiCodeBlock) fail(callback, "Registered item/data callbacks require a direct source block.")
                if (callback.parameterList.parameters.size != 2 || (callback.parameterList.parameters[0].type as? PsiClassType)?.resolve()?.qualifiedName != WicketRepeaterContract.ITEM)
                    fail(callback, "The callback item parameter must be a framework ListItem.")
                val guards = statements.dropLast(1)
                if (guards.size > 1 || guards.any { statement -> call(statement)?.let { guard ->
                    guard.resolveMethod()?.let { it.containingClass?.qualifiedName == "java.util.Objects" && it.name == "requireNonNull" } != true ||
                        !guard.resolveMethodGenerics().isValidResult || guard.methodExpression.qualifierExpression?.let { it !is PsiReferenceExpression || it.resolve() !is PsiClass } == true ||
                        guard.argumentList.expressions.singleOrNull()?.let { ref(it, callbackParameter) } != true
                } != false }) fail(method, "Only an optional direct callback requireNonNull may precede allocation.")
                val allowed = listOfNotNull(forwarding.methodExpression.qualifierExpression) + guards.mapNotNull { call(it)?.argumentList?.expressions?.singleOrNull() }
                val uses = descendants(method, PsiReferenceExpression::class.java).filter { it.resolve() == callbackParameter }
                if (uses.size != allowed.size || uses.any { it !in allowed }) fail(callbackParameter, "The item callback escapes its one population bridge.")
                val id = WicketComponentIdentity.trace(creation) ?: fail(creation, "The constructed ListView ID forwarding is unproved.")
                sources += id.sources
                val formal = (id.expression as? PsiReferenceExpression)?.resolve() as? PsiParameter ?: fail(creation, "The source factory must forward its String ID parameter.")
                if (formal.declarationScope != method || formal.type.canonicalText != "java.lang.String") fail(formal, "The source ID parameter is unavailable.")
                return Triple(creation, formal, callback)
            } finally { active.remove(method) }
        }
        val supplied = entry.parameterList.parameters.zip(call.argumentList.expressions.toList()).toMap()
        val (creation, idFormal, callback) = walk(entry, call.argumentList.expressions.toList())
        // Recover the original call's ID expression through the same bounded source forwarding route.
        fun idArgument(method: PsiMethod, args: List<PsiExpression>, depth: Int = 0): PsiExpression {
            if (depth >= 12) fail(method, "Factory ID forwarding exceeds its source bound.")
            val values = method.parameterList.parameters.zip(args).toMap()
            val returned = (method.body!!.statements.last() as PsiReturnStatement).returnValue!!
            return if (returned is PsiNewExpression) values[idFormal] ?: fail(returned, "The supplied source ID is unavailable.") else {
                val forwarded = returned as PsiMethodCallExpression
                idArgument(forwarded.resolveMethod()!!, forwarded.argumentList.expressions.map { actual(it, values) }, depth + 1)
            }
        }
        if (supplied.values.count { it == callback } != 1) fail(callback, "The registered lambda has no unique source argument.")
        val behaviorFields = owner.fields.toList()
        val behaviors = behaviorFields.map { field ->
            source(field)
            val type = (field.type as? PsiClassType)?.resolve() ?: fail(field, "A source repeater field type is unavailable.")
            if (!field.hasModifierProperty(PsiModifier.PRIVATE) || !field.hasModifierProperty(PsiModifier.FINAL) || field.hasModifierProperty(PsiModifier.STATIC) || !InheritanceUtil.isInheritor(type, "org.apache.wicket.behavior.Behavior"))
                fail(field, "Only private final Behavior fields are reviewed on registered lists.")
            val writes = descendants(owner, PsiAssignmentExpression::class.java).filter { (it.lExpression as? PsiReferenceExpression)?.resolve() == field }
            if (writes.any { write -> !own((write.lExpression as PsiReferenceExpression).qualifierExpression) || PsiTreeUtil.getParentOfType(write, PsiMethod::class.java)?.let { it.isConstructor && it.containingClass == owner } != true })
                fail(field, "Behavior fields may only be initialized on their own constructed list.")
            val allocations = listOfNotNull(field.initializer) + writes.mapNotNull { it.rExpression }
            if (allocations.isEmpty() || allocations.any { it !is PsiNewExpression || it.anonymousClass != null || (it.classReference?.resolve() as? PsiClass) != type })
                fail(field, "Registered list behaviors must have fresh direct construction.")
            type
        }
        if (owner.initializers.isNotEmpty()) fail(owner, "Registered list initialization blocks are unreviewed.")
        owner.constructors.forEach { constructor -> constructor(constructor, owner, behaviorFields) }
        val itemFactories = owner.methods.filter { it.name == "newItem" && !it.hasModifierProperty(PsiModifier.STATIC) }
        if (itemFactories.size > 1) fail(owner, "Multiple source item factories are unreviewed.")
        val itemFactory = itemFactories.singleOrNull()
        if (itemFactory != null) itemFactory(itemFactory, behaviorFields)
        owner.methods.filter { !it.isConstructor && !it.hasModifierProperty(PsiModifier.STATIC) && it.findDeepestSuperMethods().isNotEmpty() && it != itemFactory }
            .forEach { fail(it, "Other repeater lifecycle/factory overrides remain outside the registered callback contract.") }
        val bridge = creation.anonymousClass!!.methods.single { !it.isConstructor }
        return Result(creation.anonymousClass!!, owner, idArgument(entry, call.argumentList.expressions.toList()), callback, behaviors,
            RegisteredListPopulation(owner.qualifiedName!!, location(call), factorySources, location(callback), location(bridge), itemFactories.map(::location), behaviors.map(::location)))
    }

    private fun constructor(method: PsiMethod, owner: PsiClass, behaviors: List<PsiField>) {
        source(method)
        val statements = method.body?.statements ?: fail(method, "The registered list constructor source is unavailable.")
        val delegation = call(statements.firstOrNull()) ?: fail(method, "Registered lists must directly delegate to this or ListView.")
        val target = delegation.resolveMethod() ?: fail(delegation, "The registered list constructor delegation is unavailable.")
        val parameters = method.parameterList.parameters.toSet()
        if (delegation.methodExpression.text !in setOf("super", "this") || !target.isConstructor ||
            target.containingClass != owner && target.containingClass?.qualifiedName != WicketRepeaterContract.LIST ||
            delegation.argumentList.expressions.any { it !is PsiLiteralExpression && (it !is PsiReferenceExpression || it.qualifierExpression != null || it.resolve() !in parameters) })
            fail(delegation, "Registered list constructor delegation must forward only parameters or literals.")
        for (statement in statements.drop(1)) {
            tick(statement)
            val assignment = (statement as? PsiExpressionStatement)?.expression as? PsiAssignmentExpression
            if (assignment != null && assignment.operationTokenType == JavaTokenType.EQ && behaviors.any { ref(assignment.lExpression, it) } && assignment.rExpression is PsiNewExpression) continue
            val addition = call(statement) ?: fail(statement, "Only owned Behavior construction and attachment may follow registered list delegation.")
            if (!own(addition.methodExpression.qualifierExpression) || addition.resolveMethod()?.let { it.name == "add" && it.containingClass?.qualifiedName == "org.apache.wicket.Component" } != true ||
                addition.argumentList.expressions.isEmpty() || addition.argumentList.expressions.any { value -> behaviors.none { ref(value, it) } })
                fail(addition, "Only owned Behavior fields may be attached by registered list constructors.")
        }
    }

    private fun itemFactory(method: PsiMethod, behaviors: List<PsiField>) {
        source(method)
        val parameters = method.parameterList.parameters
        if (parameters.size != 2 || parameters[0].type != PsiTypes.intType() || (parameters[1].type as? PsiClassType)?.resolve()?.qualifiedName != "org.apache.wicket.model.IModel" ||
            (method.returnType as? PsiClassType)?.resolve()?.qualifiedName != WicketRepeaterContract.ITEM || method.findDeepestSuperMethods().none { it.containingClass?.qualifiedName == WicketRepeaterContract.LIST })
            fail(method, "The custom item method must override ListView.newItem(int,IModel).")
        val statements = method.body?.statements ?: fail(method, "The item factory source is unavailable.")
        val local = (statements.firstOrNull() as? PsiDeclarationStatement)?.declaredElements?.singleOrNull() as? PsiLocalVariable ?: fail(method, "The item wrapper must start with super.newItem.")
        val origin = local.initializer as? PsiMethodCallExpression ?: fail(local, "The item is not created by the default factory.")
        if ((local.type as? PsiClassType)?.resolve()?.qualifiedName != WicketRepeaterContract.ITEM || !origin.resolveMethodGenerics().isValidResult || origin.methodExpression.qualifierExpression !is PsiSuperExpression || origin.resolveMethod()?.let { it.name == "newItem" && it.containingClass?.qualifiedName == WicketRepeaterContract.LIST } != true ||
            origin.argumentList.expressions.size != 2 || origin.argumentList.expressions.zip(parameters).any { !ref(it.first, it.second) }) fail(origin, "The default item index and model must be forwarded unchanged.")
        if ((statements.lastOrNull() as? PsiReturnStatement)?.returnValue?.let { ref(it, local) } != true) fail(method, "The wrapper must return its default item unchanged.")
        val uses = mutableSetOf<PsiReferenceExpression>()
        uses += (statements.last() as PsiReturnStatement).returnValue as PsiReferenceExpression
        for (statement in statements.drop(1).dropLast(1)) {
            val call = call(statement) ?: fail(statement, "Only behavior additions may decorate the default item.")
            val receiver = call.methodExpression.qualifierExpression as? PsiReferenceExpression ?: fail(call, "The item behavior receiver is unknown.")
            if (!ref(receiver, local) || call.resolveMethod()?.let { it.name == "add" && it.containingClass?.qualifiedName == "org.apache.wicket.Component" } != true ||
                call.argumentList.expressions.isEmpty() || call.argumentList.expressions.any { argument -> behaviors.none { ref(argument, it) } }) fail(call, "Only owned Behavior fields may be added to the default item.")
            uses += receiver
        }
        if (descendants(method, PsiReferenceExpression::class.java).filter { it.resolve() == local }.any { it !in uses }) fail(method, "The default item escapes its behavior-only wrapper.")
    }
}

private fun location(element: PsiElement) = SourceLocation(element.containingFile.virtualFile.path, element.textOffset)
