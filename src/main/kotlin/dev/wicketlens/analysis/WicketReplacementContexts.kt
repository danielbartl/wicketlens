package dev.wicketlens.analysis

import com.intellij.openapi.progress.ProgressManager
import com.intellij.psi.*
import com.intellij.psi.util.InheritanceUtil
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.PsiUtil
import dev.wicketlens.model.*

/** Source-declared replacement and step-selection templates, never initial/current runtime trees. */
internal class WicketReplacementContexts(private val limits: Limits = Limits()) {
    data class Limits(val steps: Int = 12000, val sources: Int = 64, val contexts: Int = 8, val depth: Int = 12)
    data class Result(val reports: List<PageReport>, val sources: List<String>, val diagnostics: List<Diagnostic>, val complete: Boolean)
    private class Unproved(val at: PsiElement, override val message: String) : RuntimeException(null, null, false, false)
    private class Bound(val at: PsiElement) : RuntimeException(null, null, false, false)
    private class Slot(val type: PsiClass, val creation: PsiNewExpression?, val id: String?, val origin: PsiExpression?,
        val variables: MutableMap<PsiVariable, Slot> = linkedMapOf(), val parents: MutableMap<Slot, MutableList<Slot>> = linkedMapOf(),
        val mutations: MutableList<Pair<Slot, PsiElement>> = mutableListOf())

    private data class WizardStep(val delta: Int, val method: PsiMethod, val guard: PsiElement, val write: PsiReferenceExpression)

    private data class Registration(val list: PsiField, val record: PsiClass, val factory: PsiRecordComponent, val storage: PsiMethodCallExpression, val methods: Set<PsiMethod>)

    companion object {
        /** One indexed host hop is enough to connect a replacement resource to its registrations. */
        fun hasRegisteredFactory(type: PsiClass): Boolean = type !is PsiCompiledElement && (type.methods.any(WicketImmediateFactoryContract::candidate) || type.fields.any { field ->
            if (!field.hasModifierProperty(PsiModifier.PRIVATE) || field.hasModifierProperty(PsiModifier.FINAL) || field.hasModifierProperty(PsiModifier.STATIC)) false
            else {
                val sam = LambdaUtil.getFunctionalInterfaceMethod(field.type)
                val result = LambdaUtil.getFunctionalInterfaceReturnType(field.type)
                val output = (if (result is PsiWildcardType) result.extendsBound else result) as? PsiClassType
                sam?.parameterList?.parametersCount == 2 && output?.resolve()?.let {
                    it.qualifiedName == "org.apache.wicket.Component" || InheritanceUtil.isInheritor(it, "org.apache.wicket.Component")
                } == true && type.methods.any { setter ->
                    val statements = setter.body?.statements.orEmpty()
                    statements.size == 2 && (statements.last() as? PsiReturnStatement)?.returnValue is PsiThisExpression &&
                        ((statements.first() as? PsiExpressionStatement)?.expression as? PsiAssignmentExpression)?.let {
                            (it.lExpression as? PsiReferenceExpression)?.resolve() == field &&
                                (it.rExpression as? PsiReferenceExpression)?.resolve() == setter.parameterList.parameters.singleOrNull()
                        } == true
                }
            }
        })
    }

    fun analyze(page: PsiClass, primary: PageReport): Result {
        val session = Session(page, primary)
        return session.run()
    }
    private inner class Session(val page: PsiClass, val primary: PageReport) {
        val sources = linkedSetOf<String>()
        val reports = mutableListOf<PageReport>()
        val issues = mutableListOf<Diagnostic>()
        var steps = 0
        val elementCache = mutableMapOf<PsiElement, List<PsiElement>>()
        val visitedElements = mutableSetOf<PsiElement>()
        val constants = WicketSourceConstants({ source(it) }, { tick(page) })
        fun tick(at: PsiElement) {
            ProgressManager.checkCanceled()
            if (++steps > limits.steps || sources.size > limits.sources) throw Bound(at)
        }
        fun fail(at: PsiElement, message: String): Nothing = throw Unproved(at, message)
        fun source(at: PsiElement) {
            tick(at)
            val file = at.containingFile ?: fail(at, "Physical source is unavailable.")
            val path = file.virtualFile?.path?.substringBefore("!/") ?: fail(at, "Physical source is unavailable.")
            sources += path
            if (sources.size > limits.sources) throw Bound(at)
            if (at !is PsiCompiledElement && PsiTreeUtil.findChildOfType(file, PsiErrorElement::class.java) != null)
                fail(at, "Source contains incomplete Java syntax.")
        }
        fun run(): Result {
            if (primary.markupSuffix.isNotEmpty()) return Result(emptyList(), emptyList(), emptyList(), true)
            var complete = true
            try {
                val refs = descendants(page, PsiMethodReferenceExpression::class.java).filter { reference ->
                    val ctor = WicketComponentIdentity.unboundConstructor(reference)
                    reference.text.endsWith("::new") && ctor != null && ctor.parameterList.parametersCount == 2 &&
                        ctor.parameterList.parameters.first().type.canonicalText == "java.lang.String" &&
                        ctor.containingClass?.let(WicketComponentTypes::ownsMarkup) == true
                }
                refs.forEach { reference ->
                    tick(reference)
                    val call = (reference.parent as? PsiExpressionList)?.parent as? PsiMethodCallExpression ?: return@forEach
                    val setter = call.resolveMethod() ?: return@forEach
                    val field = registration(setter, reference) ?: return@forEach
                    try {
                        if (reports.size >= limits.contexts) throw Bound(reference)
                        source(page); source(setter)
                        reports += build(reference, call, setter, field)
                    } catch (unknown: Unproved) {
                        issues += Diagnostic(DiagnosticCode.UNRESOLVED_SCOPE, "Replacement-site template withheld: ${unknown.message}", location(unknown.at))
                    }
                }
                registeredSelections()
                immediateFactories()
            } catch (bound: Bound) {
                complete = false
                reports.clear()
                issues += Diagnostic(DiagnosticCode.PARTIAL_ANALYSIS,
                    "Replacement-site discovery exceeded its ${limits.steps}-step/${limits.sources}-source/${limits.contexts}-context/${limits.depth}-level bound; no lifecycle template was published.", location(bound.at))
            }
            return Result(reports, sources.sorted(), issues, complete)
        }
        fun <T : PsiElement> descendants(at: PsiElement, type: Class<T>): List<T> {
            val all = elementCache.getOrPut(at) {
                val ancestor = generateSequence(at.parent) { it.parent }.firstOrNull { it in elementCache }
                if (ancestor != null) return@getOrPut elementCache.getValue(ancestor).filter { PsiTreeUtil.isAncestor(at, it, false) }
                val result = linkedSetOf<PsiElement>()
                at.accept(object : JavaRecursiveElementWalkingVisitor() {
                    override fun visitElement(element: PsiElement) {
                        if (visitedElements.add(element)) tick(element)
                        result += element; super.visitElement(element)
                    }
                })
                result.toList()
            }
            tick(at)
            return all.filter { type.isInstance(it) }.map(type::cast)
        }
        fun registration(setter: PsiMethod, reference: PsiMethodReferenceExpression): PsiField? {
            if (setter is PsiCompiledElement || setter.hasModifierProperty(PsiModifier.STATIC) || setter.parameterList.parametersCount != 1) return null
            val sam = LambdaUtil.getFunctionalInterfaceMethod(setter.parameterList.parameters.single().type) ?: return null
            if (sam.parameterList.parametersCount != 2) return null
            val body = setter.body?.statements ?: return null
            if (body.size != 2 || (body.last() as? PsiReturnStatement)?.returnValue !is PsiThisExpression) return null
            val assignment = (body.first() as? PsiExpressionStatement)?.expression as? PsiAssignmentExpression ?: return null
            if (!self((assignment.lExpression as? PsiReferenceExpression)?.qualifierExpression)) return null
            if (!self((body.last() as PsiReturnStatement).returnValue)) return null
            val field = (assignment.lExpression as? PsiReferenceExpression)?.resolve() as? PsiField ?: return null
            if (field.hasModifierProperty(PsiModifier.STATIC) || !field.hasModifierProperty(PsiModifier.PRIVATE) ||
                (assignment.rExpression as? PsiReferenceExpression)?.resolve() != setter.parameterList.parameters.single()) return null
            return field.takeIf { componentFactory(it.type) && WicketComponentIdentity.unboundConstructor(reference) != null }
        }
        fun componentFactory(type: PsiType): Boolean {
            val result = LambdaUtil.getFunctionalInterfaceReturnType(type)
            val actual = if (result is PsiWildcardType) result.extendsBound else result
            return component(actual)
        }
        fun component(type: PsiType?): Boolean = (type as? PsiClassType)?.resolve()?.let {
            it.qualifiedName == "org.apache.wicket.Component" || InheritanceUtil.isInheritor(it, "org.apache.wicket.Component")
        } == true
        fun self(expression: PsiExpression?): Boolean = expression == null || expression is PsiThisExpression &&
            (expression.qualifier == null || expression.qualifier?.resolve() == PsiTreeUtil.getParentOfType(expression, PsiMethod::class.java)?.containingClass)
        fun unwrap(expression: PsiExpression): PsiExpression = if (expression is PsiParenthesizedExpression) expression.expression?.let(::unwrap) ?: expression else expression
        fun string(expression: PsiExpression?, values: Map<PsiParameter, String?> = emptyMap()): Pair<String?, PsiExpression?> {
            if (expression == null) return null to null
            tick(expression)
            return when (val value = unwrap(expression)) {
                is PsiLiteralExpression -> (value.value as? String) to value
                is PsiReferenceExpression -> when (val variable = value.resolve()) {
                    is PsiParameter -> values[variable] to value
                    is PsiField -> (constants.resolve(value) as? String) to value
                    else -> null to value
                }
                else -> null to value
            }
        }
        fun allocation(expression: PsiExpression?, seen: Set<PsiVariable> = emptySet()): PsiNewExpression? = when (val value = expression?.let(::unwrap)) {
            is PsiNewExpression -> value
            is PsiMethodCallExpression -> {
                val target = value.resolveMethod()
                val returns = target?.body?.statements?.lastOrNull() as? PsiReturnStatement
                if (target == null || target is PsiCompiledElement || target.hasModifierProperty(PsiModifier.STATIC) || returns?.returnValue !is PsiThisExpression || !self(returns.returnValue) ||
                    descendants(target.body!!, PsiReturnStatement::class.java).any { PsiTreeUtil.getParentOfType(it, PsiMethod::class.java) == target && it != returns }) null
                else allocation(value.methodExpression.qualifierExpression, seen)
            }
            is PsiReferenceExpression -> (value.resolve() as? PsiVariable)?.takeIf { it !in seen && seen.size < limits.depth }?.let { variable ->
                allocation(variable.initializer, seen + variable)
            }
            else -> null
        }
        fun direct(element: PsiElement, method: PsiMethod): Boolean {
            val statement = PsiTreeUtil.getParentOfType(element, PsiStatement::class.java) ?: return false
            return statement.parent == method.body && PsiTreeUtil.getParentOfType(element, PsiMethod::class.java) == method &&
                generateSequence(element.parent) { it.parent }.takeWhile { it != method }.none { it is PsiLambdaExpression }
        }
        fun hierarchy(type: PsiClass): List<PsiClass> {
            val result = mutableListOf<PsiClass>()
            var current: PsiClass? = type
            while (current != null && current !is PsiCompiledElement) {
                if (result.size >= limits.depth || current in result) throw Bound(type)
                source(current); result += current; current = current.superClass
            }
            return result
        }
        fun slot(creation: PsiNewExpression, values: Map<PsiParameter, String?>): Slot {
            tick(creation)
            val identity = WicketComponentIdentity.trace(creation) ?: fail(creation, "Constructor ID forwarding is unproved.")
            sources += identity.sources
            val (id, origin) = string(identity.expression, values)
            if (id.isNullOrEmpty() || id.any { it == ':' || it == '~' }) fail(creation, "The placement ID is not a known literal String.")
            val type = creation.anonymousClass?.baseClassType?.resolve() ?: creation.classReference?.resolve() as? PsiClass
                ?: fail(creation, "The constructed component type is unavailable.")
            return Slot(type, creation, id, origin)
        }
        fun resolve(expression: PsiExpression?, root: Slot): Slot? {
            tick(expression ?: page)
            return when (val value = expression?.let(::unwrap)) {
                null -> root
                is PsiThisExpression -> root.takeIf { self(value) }
                is PsiSuperExpression -> root.takeIf { value.qualifier == null }
                is PsiReferenceExpression -> if (self(value.qualifierExpression)) root.variables[value.resolve()] else null
                else -> null
            }
        }
        fun skeleton(root: Slot, method: PsiMethod, arguments: List<PsiExpression>, callerValues: Map<PsiParameter, String?> = emptyMap(), active: Set<PsiMethod> = emptySet()) {
            tick(method); source(method)
            if (method in active || active.size >= limits.depth) throw Bound(method)
            if (method is PsiCompiledElement) return
            val values = method.parameterList.parameters.mapIndexed { index, parameter -> parameter to string(arguments.getOrNull(index), callerValues).first }.toMap()
            val statements = method.body?.statements ?: fail(method, "Constructor source is unavailable.")
            val delegation = (statements.firstOrNull() as? PsiExpressionStatement)?.expression as? PsiMethodCallExpression
            if (delegation?.methodExpression?.referenceName in setOf("this", "super")) {
                val target = delegation!!.resolveMethod() ?: fail(delegation, "Constructor delegation does not resolve.")
                if (target !is PsiCompiledElement) skeleton(root, target, delegation.argumentList.expressions.toList(), values, active + method)
            }
            hierarchy(method.containingClass!!).flatMap { it.fields.toList() }.filter { component(it.type) && it.initializer != null }.forEach { field ->
                val creation = allocation(field.initializer) ?: return@forEach
                if (!field.hasModifierProperty(PsiModifier.PRIVATE) || !field.hasModifierProperty(PsiModifier.FINAL) || field.hasModifierProperty(PsiModifier.STATIC))
                    fail(field, "Placement fields must be private final instance fields.")
                try { root.variables.putIfAbsent(field, slot(creation, values)) } catch (_: Unproved) { /* Unselected opaque siblings do not define this route. */ }
            }
            descendants(method.body!!, PsiNewExpression::class.java).filter { component(it.type) && direct(it, method) }.forEach { creation ->
                val parent = creation.parent
                val variable = when (parent) {
                    is PsiLocalVariable -> parent
                    is PsiAssignmentExpression -> if (parent.rExpression == creation) (parent.lExpression as? PsiReferenceExpression)?.resolve() as? PsiVariable else null
                    else -> null
                } ?: return@forEach
                if (variable is PsiField && (!variable.hasModifierProperty(PsiModifier.PRIVATE) || !variable.hasModifierProperty(PsiModifier.FINAL) || variable.hasModifierProperty(PsiModifier.STATIC)))
                    fail(variable, "Placement fields must be private final instance fields.")
                if (root.variables.containsKey(variable)) fail(variable, "A placement variable has multiple initializations.")
                try { root.variables[variable] = slot(creation, values) } catch (_: Unproved) { /* A selected missing slot fails at resolution. */ }
            }
            descendants(method.body!!, PsiMethodCallExpression::class.java).filter { direct(it, method) }.forEach { call ->
                val target = call.resolveMethod() ?: return@forEach
                if (target.name in setOf("remove", "removeAll", "replace", "replaceWith", "addOrReplace", "queue") && target.containingClass?.let { component(JavaPsiFacade.getElementFactory(page.project).createType(it)) } == true)
                    resolve(call.methodExpression.qualifierExpression, root)?.let { root.mutations += it to call }
                if (target.containingClass?.qualifiedName != "org.apache.wicket.MarkupContainer" || target.name != "add" ||
                    (target.parameterList.parameters.singleOrNull()?.type as? PsiEllipsisType)?.componentType?.canonicalText != "org.apache.wicket.Component") return@forEach
                val parent = resolve(call.methodExpression.qualifierExpression, root) ?: return@forEach
                call.argumentList.expressions.forEach { child -> resolve(child, root)?.let { root.parents.getOrPut(it) { mutableListOf() } += parent } }
            }
        }
        fun path(root: Slot, end: Slot): List<Slot> {
            val result = mutableListOf<Slot>()
            var current = end
            while (current !== root) {
                tick(page)
                if (result.size >= limits.depth || current in result) throw Bound(page)
                result.add(0, current)
                current = root.parents[current]?.singleOrNull() ?: fail(current.creation ?: page, "A unique constructor attachment parent is not established.")
            }
            if (root.mutations.any { it.first === root || it.first in result }) fail(root.mutations.first().second, "Known constructor mutations invalidate the placement ancestry.")
            return result
        }
        fun build(reference: PsiMethodReferenceExpression, call: PsiMethodCallExpression, setter: PsiMethod, factoryField: PsiField): PageReport {
            val constructor = WicketComponentIdentity.unboundConstructor(reference) ?: fail(reference, "An unbound source constructor is required.")
            val fields = constructor.containingClass ?: fail(reference, "The editor class is unavailable.")
            source(fields)
            if (JavaPsiFacade.getInstance(page.project).findClasses(fields.qualifiedName!!, page.resolveScope).singleOrNull() != fields)
                fail(reference, "The editor declaration is ambiguous in this source context.")
            val hostCreation = allocation(call.methodExpression.qualifierExpression) ?: fail(call, "The configured host has no unique direct allocation.")
            if (hostCreation.anonymousClass != null) fail(hostCreation, "Anonymous host configuration dispatch is not reviewed.")
            val host = slot(hostCreation, emptyMap())
            if (host.type !in hierarchy(setter.containingClass!!)) fail(call, "Registration dispatch is not established.")
            val pageMethod = PsiTreeUtil.getParentOfType(hostCreation, PsiMethod::class.java) ?: fail(hostCreation, "Host construction is not in a source constructor.")
            if (!pageMethod.isConstructor || pageMethod.containingClass != page || !direct(hostCreation, pageMethod)) fail(hostCreation, "Host construction requires direct page constructor setup.")
            val hostVariable = generateSequence(call as PsiElement) { it.parent }.takeWhile { it != pageMethod }.filterIsInstance<PsiLocalVariable>().firstOrNull()
                ?: (call.methodExpression.qualifierExpression as? PsiReferenceExpression)?.resolve() as? PsiLocalVariable
                ?: fail(call, "The registered host needs a local construction identity.")
            if (allocation(hostVariable.initializer) != hostCreation) fail(hostVariable, "The registered host alias is unproved.")
            val writes = descendants(pageMethod.body!!, PsiAssignmentExpression::class.java).filter { (it.lExpression as? PsiReferenceExpression)?.resolve() == hostVariable }
            if (writes.isNotEmpty()) fail(writes.first(), "The registered host is reassigned.")
            val adds = descendants(pageMethod.body!!, PsiMethodCallExpression::class.java).filter { added ->
                direct(added, pageMethod) && added.resolveMethod()?.let { it.containingClass?.qualifiedName == "org.apache.wicket.MarkupContainer" && it.name == "add" } == true &&
                    (added.methodExpression.qualifierExpression == null || added.methodExpression.qualifierExpression is PsiThisExpression) &&
                    added.argumentList.expressions.any { (it as? PsiReferenceExpression)?.resolve() == hostVariable }
            }
            if (adds.size != 1) fail(call, "The host has no unique direct page attachment.")
            val hostHierarchy = hierarchy(host.type)
            val hostMethods = hostHierarchy.flatMap { it.methods.toList() }
            val fieldWrites = hostHierarchy.flatMap { descendants(it, PsiAssignmentExpression::class.java) }.distinct().filter { (it.lExpression as? PsiReferenceExpression)?.resolve() == factoryField }
            if (fieldWrites.size != 1 || !PsiTreeUtil.isAncestor(setter, fieldWrites.single(), true)) fail(factoryField, "The registered factory can be overwritten outside its setter.")
            val registrations = descendants(pageMethod.body!!, PsiMethodCallExpression::class.java).filter { registered ->
                registered.resolveMethod() == setter && allocation(registered.methodExpression.qualifierExpression) == hostCreation
            }
            if (registrations.size != 1) fail(call, "The host has multiple registrations for this factory.")
            val structural = setOf("remove", "removeAll", "replace", "replaceWith", "addOrReplace", "queue")
            descendants(pageMethod.body!!, PsiMethodCallExpression::class.java).filter { direct(it, pageMethod) }.forEach { operation ->
                val target = operation.resolveMethod()
                val receiver = operation.methodExpression.qualifierExpression
                if (target?.name in structural && target?.containingClass?.let { component(JavaPsiFacade.getElementFactory(page.project).createType(it)) } == true &&
                    (receiver == null || receiver is PsiThisExpression || (receiver as? PsiReferenceExpression)?.resolve() == hostVariable))
                    fail(operation, "Known structural page/host mutations invalidate the declared placement route.")
                if (operation.argumentList.expressions.any { it is PsiThisExpression } && target?.containingClass?.qualifiedName?.startsWith("org.apache.wicket.") != true)
                    fail(operation, "The page escapes to an unreviewed direct call.")
            }
            val invocations = hostMethods.flatMap { method -> method.body?.let { descendants(it, PsiMethodCallExpression::class.java) }.orEmpty() }.filter { invocation ->
                (invocation.methodExpression.qualifierExpression as? PsiReferenceExpression)?.let { it.resolve() == factoryField && self(it.qualifierExpression) } == true && invocation.methodExpression.referenceName == "apply"
            }
            val invocation = invocations.singleOrNull() ?: fail(factoryField, "A unique source factory invocation is required.")
            if (invocation.resolveMethod() != LambdaUtil.getFunctionalInterfaceMethod(factoryField.type)) fail(invocation, "The factory invocation does not call its functional-interface method.")
            val event = PsiTreeUtil.getParentOfType(invocation, PsiMethod::class.java) ?: fail(invocation, "The event method is unavailable.")
            if (!direct(invocation, event)) fail(invocation, "The factory invocation is conditional or nested.")
            val produced = invocation.parent as? PsiLocalVariable ?: fail(invocation, "The factory result must be a direct local.")
            val (contentId, contentOrigin) = string(invocation.argumentList.expressions.firstOrNull())
            if (contentId.isNullOrEmpty() || contentId.any { it == ':' || it == '~' }) fail(invocation, "The factory receives an unknown or reserved ID.")
            val synthetic = JavaPsiFacade.getElementFactory(page.project).createExpressionFromText("new ${fields.qualifiedName}(\"${contentId.replace("\\", "\\\\").replace("\"", "\\\"")}\", null)", reference) as? PsiNewExpression
                ?: fail(reference, "The constructor invocation cannot be resolved.")
            val identity = WicketComponentIdentity.trace(synthetic) ?: fail(reference, "The editor constructor ID is unproved.")
            sources += identity.sources
            if ((identity.expression as? PsiLiteralExpression)?.value != contentId) fail(reference, "The editor constructor does not preserve the supplied ID.")
            skeleton(host, hostCreation.resolveConstructor() ?: fail(hostCreation, "Host constructor is unavailable."), hostCreation.argumentList?.expressions.orEmpty().toList())
            val consumers = descendants(event.body!!, PsiMethodCallExpression::class.java).filter { consumer -> direct(consumer, event) && consumer.argumentList.expressions.any { (it as? PsiReferenceExpression)?.resolve() == produced } }
            val consumer = consumers.singleOrNull() ?: fail(invocation, "The factory result has no unique direct source consumer.")
            val receiver = resolve(consumer.methodExpression.qualifierExpression, host) ?: fail(consumer, "The event receiver is not an owned construction field.")
            val receiverCreation = receiver.creation ?: fail(consumer, "The event receiver has no source allocation.")
            receiverCreation.anonymousClass?.let { anonymous ->
                if (anonymous.initializers.isNotEmpty() || anonymous.fields.any { it.initializer != null }) fail(anonymous, "Anonymous receiver initialization effects are not reviewed.")
            }
            val runtime = receiverCreation.anonymousClass ?: receiver.type
            val route = path(host, receiver).toMutableList()
            val target = consumer.resolveMethod() ?: fail(consumer, "The event consumer does not resolve.")
            val parameterIndex = consumer.argumentList.expressions.indexOfFirst { (it as? PsiReferenceExpression)?.resolve() == produced }
            val receiverRoot = Slot(receiver.type, receiver.creation, receiver.id, receiver.origin)
            skeleton(receiverRoot, receiverCreation.resolveConstructor() ?: fail(consumer, "Consumer construction is unavailable."), receiverCreation.argumentList?.expressions.orEmpty().toList())
            val replacement = consume(target, target.parameterList.parameters.getOrNull(parameterIndex) ?: fail(consumer, "The consumer parameter is unavailable."), receiverRoot, runtime, emptySet())
            route += path(receiverRoot, replacement.first)
            val ownerReport = WicketPageAnalyzer().analyze(fields) ?: fail(reference, "The source editor cannot be analyzed.")
            sources += ownerReport.javaSourceFiles; sources += ownerReport.markup?.sourceFiles.orEmpty()
            if (ownerReport.fragmentUses.isNotEmpty() || ownerReport.nodes.any { it.template || it.branchMerged || it.conditional || it.queuedInNodeId != null || it.sourceSetupExpanded })
                fail(reference, "Composite, generated or conditional editor-owned scopes require a separate template contract.")
            if (ownerReport.warnings.any { it.code == DiagnosticCode.UNRESOLVED_SCOPE }) fail(reference, "Editor construction has unresolved root effects.")
            if (ownerReport.matching?.findings.orEmpty().none { it.code == DiagnosticCode.MATCHED }) fail(reference, "No editor-owned bindings are established.")
            return project(reference, invocation, event, replacement.second, host, route, contentId, contentOrigin, ownerReport)
        }
        fun unaryFactory(type: PsiType?): Boolean {
            if (type == null || LambdaUtil.getFunctionalInterfaceMethod(type)?.parameterList?.parametersCount != 1) return false
            val result = LambdaUtil.getFunctionalInterfaceReturnType(type)
            return component(if (result is PsiWildcardType) result.extendsBound else result)
        }
        fun stepStorage(method: PsiMethod, parameter: PsiParameter, active: Set<PsiMethod> = emptySet()): Registration? {
            tick(method)
            if (method is PsiCompiledElement || method.hasModifierProperty(PsiModifier.STATIC) || method in active || active.size >= limits.depth) return null
            source(method)
            val statements = method.body?.statements ?: return null
            if (statements.size == 1) {
                val forwarding = (statements.single() as? PsiReturnStatement)?.returnValue as? PsiMethodCallExpression ?: return null
                if (!self(forwarding.methodExpression.qualifierExpression)) return null
                val slot = forwarding.argumentList.expressions.indexOfFirst { (it as? PsiReferenceExpression)?.resolve() == parameter }
                val target = forwarding.resolveMethod() ?: return null
                return target.parameterList.parameters.getOrNull(slot)?.let { stepStorage(target, it, active + method) }?.let { it.copy(methods = it.methods + method) }
            }
            if (statements.size != 2 || (statements.last() as? PsiReturnStatement)?.returnValue !is PsiThisExpression ||
                !self((statements.last() as PsiReturnStatement).returnValue)) return null
            val append = (statements.first() as? PsiExpressionStatement)?.expression as? PsiMethodCallExpression ?: return null
            if (append.resolveMethod()?.let { it.name == "add" && it.containingClass?.qualifiedName in setOf("java.util.List", "java.util.ArrayList") } != true) return null
            val receiver = append.methodExpression.qualifierExpression as? PsiReferenceExpression ?: return null
            val list = receiver.resolve() as? PsiField ?: return null
            if (!self(receiver.qualifierExpression) || !list.hasModifierProperty(PsiModifier.PRIVATE) || !list.hasModifierProperty(PsiModifier.FINAL) || list.hasModifierProperty(PsiModifier.STATIC)) return null
            val init = list.initializer as? PsiNewExpression ?: return null
            if ((init.classReference?.resolve() as? PsiClass)?.qualifiedName != "java.util.ArrayList" || init.argumentList?.expressionCount != 0) return null
            val value = append.argumentList.expressions.singleOrNull() as? PsiNewExpression ?: return null
            val record = value.classReference?.resolve() as? PsiClass ?: return null
            if (!record.isRecord || record.constructors.any { it.isPhysical && it.body != null }) return null
            val index = value.argumentList?.expressions?.indexOfFirst { (it as? PsiReferenceExpression)?.resolve() == parameter } ?: return null
            val factory = record.recordComponents.getOrNull(index) ?: return null
            if (!unaryFactory(factory.type) || record.findMethodsByName(factory.name, false).any { it.isPhysical && it.body != null }) return null
            source(record)
            return Registration(list, record, factory, append, setOf(method))
        }
        fun registeredSelections() {
            val lambdas = descendants(page, PsiLambdaExpression::class.java).filter { it.parameterList.parametersCount == 1 && unaryFactory(LambdaUtil.getFunctionalInterfaceType(it, true)) }
            val grouped = linkedMapOf<PsiNewExpression, MutableList<Pair<PsiLambdaExpression, PsiMethodCallExpression>>>()
            lambdas.forEach { lambda ->
                val call = (lambda.parent as? PsiExpressionList)?.parent as? PsiMethodCallExpression ?: return@forEach
                val method = call.resolveMethod() ?: return@forEach
                val parameter = method.parameterList.parameters.getOrNull(call.argumentList.expressions.indexOf(lambda)) ?: return@forEach
                if (stepStorage(method, parameter) == null) return@forEach
                val host = allocation(call.methodExpression.qualifierExpression) ?: return@forEach
                grouped.getOrPut(host) { mutableListOf() } += lambda to call
            }
            grouped.forEach { (creation, registrations) ->
                try {
                    source(page)
                    val host = slot(creation, emptyMap())
                    val pageMethod = PsiTreeUtil.getParentOfType(creation, PsiMethod::class.java) ?: fail(creation, "Selection host construction is unavailable.")
                    if (!pageMethod.isConstructor || pageMethod.containingClass != page || !direct(creation, pageMethod)) fail(creation, "Selection host needs direct page constructor setup.")
                    val variable = creation.parent as? PsiLocalVariable ?: fail(creation, "Selection host needs one direct local allocation.")
                    if (descendants(pageMethod.body!!, PsiAssignmentExpression::class.java).any { (it.lExpression as? PsiReferenceExpression)?.resolve() == variable }) fail(variable, "Selection host is reassigned.")
                    val adds = descendants(pageMethod.body!!, PsiMethodCallExpression::class.java).filter { call -> direct(call, pageMethod) && self(call.methodExpression.qualifierExpression) &&
                        call.resolveMethod()?.let { it.name == "add" && it.containingClass?.qualifiedName == "org.apache.wicket.MarkupContainer" } == true &&
                        call.argumentList.expressions.any { (it as? PsiReferenceExpression)?.resolve() == variable } }
                    if (adds.size != 1) fail(creation, "Selection host needs one direct page attachment.")
                    creation.anonymousClass?.let { if (it.initializers.isNotEmpty() || it.fields.any { f -> f.initializer != null }) fail(it, "Selection host anonymous initialization is unreviewed.") }
                    val sorted = registrations.sortedBy { it.second.textOffset }
                    if (sorted.any { !direct(it.second, pageMethod) }) fail(creation, "Conditional or nested step registration is unresolved.")
                    val declaration = sorted.first().second.resolveMethod()!!
                    val parameter = declaration.parameterList.parameters[sorted.first().second.argumentList.expressions.indexOf(sorted.first().first)]
                    val storage = stepStorage(declaration, parameter) ?: fail(declaration, "Step storage is unavailable.")
                    val allRegistrations = descendants(pageMethod.body!!, PsiMethodCallExpression::class.java).filter { candidate ->
                        allocation(candidate.methodExpression.qualifierExpression) == creation && candidate.resolveMethod()?.let { method ->
                            method.parameterList.parameters.firstOrNull { unaryFactory(it.type) }?.let { stepStorage(method, it)?.list == storage.list }
                        } == true
                    }
                    if (allRegistrations.size != sorted.size || allRegistrations.any { candidate -> sorted.none { it.second == candidate } })
                        fail(creation, "Unknown registrations prevent establishing source step order.")
                    val hostHierarchy = hierarchy(host.type)
                    val methods = hostHierarchy.flatMap { it.methods.toList() }
                    val uses = hostHierarchy.flatMap { descendants(it, PsiReferenceExpression::class.java) }.distinct().filter { it.resolve() == storage.list }
                    uses.forEach { use ->
                        if (!self(use.qualifierExpression)) fail(use, "The registration list belongs to a foreign receiver.")
                        val member = use.parent as? PsiReferenceExpression
                        val operation = member?.parent as? PsiMethodCallExpression
                        if (member?.qualifierExpression != use || operation == null || member.referenceName !in setOf("add", "get", "size", "isEmpty", "stream", "forEach") ||
                            member.referenceName == "add" && operation != storage.storage) fail(use, "The step list escapes or is changed outside append registration.")
                    }
                    val invocations = methods.flatMap { it.body?.let { body -> descendants(body, PsiMethodCallExpression::class.java) }.orEmpty() }.filter { invocation ->
                        val accessor = invocation.methodExpression.qualifierExpression as? PsiMethodCallExpression
                        val get = accessor?.methodExpression?.qualifierExpression as? PsiMethodCallExpression
                        invocation.methodExpression.referenceName == "apply" && accessor?.resolveMethod()?.let { it.containingClass == storage.record && it.name == storage.factory.name } == true &&
                            get?.methodExpression?.referenceName == "get" && (get.methodExpression.qualifierExpression as? PsiReferenceExpression)?.resolve() == storage.list
                    }
                    val invocation = invocations.singleOrNull() ?: fail(declaration, "A unique registered-step selector is required.")
                    if (invocation.resolveMethod() != LambdaUtil.getFunctionalInterfaceMethod(storage.factory.type)) fail(invocation, "Selection does not invoke the registered SAM.")
                    val accessor = invocation.methodExpression.qualifierExpression as PsiMethodCallExpression
                    val get = accessor.methodExpression.qualifierExpression as PsiMethodCallExpression
                    if (uses.any { use -> (use.parent as? PsiReferenceExpression)?.referenceName == "get" && use.parent.parent != get })
                        fail(declaration, "Other registration-record lookups escape the reviewed selector.")
                    val index = get.argumentList.expressions.singleOrNull() as? PsiReferenceExpression ?: fail(get, "The selection index must be an opaque private field.")
                    val indexField = index.resolve() as? PsiField ?: fail(index, "The selection index is unresolved.")
                    if (!self(index.qualifierExpression) || !indexField.hasModifierProperty(PsiModifier.PRIVATE) || indexField.hasModifierProperty(PsiModifier.STATIC) || indexField.type != PsiTypes.intType()) fail(index, "The selection index must be a private int field.")
                    val event = PsiTreeUtil.getParentOfType(invocation, PsiMethod::class.java) ?: fail(invocation, "Selection method is unavailable.")
                    if (!event.hasModifierProperty(PsiModifier.PRIVATE) || !direct(invocation, event)) fail(invocation, "Selection dispatch is not a direct private source method.")
                    val produced = invocation.parent as? PsiLocalVariable ?: fail(invocation, "Selected content must be a direct local.")
                    val (id, origin) = string(invocation.argumentList.expressions.singleOrNull())
                    if (id.isNullOrEmpty() || id.any { it == ':' || it == '~' }) fail(invocation, "Selection ID is not a known String.")
                    val runtime = creation.anonymousClass ?: host.type
                    sorted.forEach { (lambda, registration) ->
                        val declared = registration.resolveMethod()!!
                        val factoryParameter = declared.parameterList.parameters[registration.argumentList.expressions.indexOf(lambda)]
                        val dispatch = stepStorage(declared, factoryParameter) ?: fail(registration, "Registration forwarding is unavailable.")
                        if (dispatch.methods.any { method -> runtime.findMethodBySignature(method, true)?.let { it != method } == true })
                            fail(registration, "Step registration dispatch is overridden.")
                    }
                    descendants(pageMethod.body!!, PsiMethodCallExpression::class.java).filter { direct(it, pageMethod) }.forEach { call ->
                        if (call.resolveMethod()?.name in setOf("remove", "removeAll", "replace", "replaceWith", "addOrReplace", "queue") &&
                            (self(call.methodExpression.qualifierExpression) || (call.methodExpression.qualifierExpression as? PsiReferenceExpression)?.resolve() == variable))
                            fail(call, "Known mutations invalidate the selection host route.")
                    }
                    skeleton(host, creation.resolveConstructor() ?: fail(creation, "Selection host constructor is unavailable."), creation.argumentList?.expressions.orEmpty().toList())
                    val replacement = consume(event, produced, host, runtime, emptySet())
                    val route = path(host, replacement.first)
                    val initialized = methods.filter { it.name == "onInitialize" }.flatMap { it.body?.let { body -> descendants(body, PsiMethodCallExpression::class.java) }.orEmpty() }.singleOrNull { it.resolveMethod() == event }
                    val transitions = wizardTransitions(hostHierarchy, runtime, event, indexField, storage.list, sorted.size)
                    sorted.forEachIndexed { ordinal, (lambda, registration) ->
                        if (reports.size >= limits.contexts) throw Bound(lambda)
                        val parsed = fragmentFactory(lambda, id)
                        val title = (registration.argumentList.expressions.firstOrNull() as? PsiLiteralExpression)?.value as? String
                        val edges = transitions.mapNotNull { step ->
                            val target = ordinal + step.delta
                            if (target !in sorted.indices) null else WizardTransition(
                                if (step.delta > 0) "NEXT" else "BACK", target, location(step.method), location(step.guard))
                        }
                        val selection = RegisteredSelection(ordinal, sorted.size, title, parsed.second.definitionId!!,
                            location(index), initialized?.let(::location), edges)
                        reports += project(lambda, invocation, event, replacement.second, host, route, id, origin, parsed.first, parsed.second, selection)
                    }
                } catch (unknown: Unproved) {
                    issues += Diagnostic(DiagnosticCode.UNRESOLVED_SCOPE, "Registered-step selection withheld: ${unknown.message}", location(unknown.at))
                }
            }
        }
        private fun wizardTransitions(owners: List<PsiClass>, runtime: PsiClass, selector: PsiMethod,
            index: PsiField, steps: PsiField, count: Int): List<WizardStep> {
            val initial = index.initializer?.let { (it as? PsiLiteralExpression)?.value as? Int ?: return emptyList() } ?: 0
            if (initial !in 0 until count) return emptyList()
            fun reference(expression: PsiExpression?, field: PsiField): PsiReferenceExpression? =
                (PsiUtil.skipParenthesizedExprDown(expression) as? PsiReferenceExpression)?.takeIf {
                    it.resolve() == field && self(it.qualifierExpression)
                }
            fun literal(expression: PsiExpression?, value: Int) = (expression as? PsiLiteralExpression)?.value == value
            fun size(expression: PsiExpression?): Boolean {
                val call = expression as? PsiMethodCallExpression ?: return false
                val method = call.resolveMethod() ?: return false
                return reference(call.methodExpression.qualifierExpression, steps) != null && call.argumentList.expressionCount == 0 &&
                    method.name == "size" && method.containingClass?.qualifiedName in setOf("java.util.List", "java.util.ArrayList", "java.util.Collection")
            }
            fun binary(expression: PsiExpression?) = PsiUtil.skipParenthesizedExprDown(expression) as? PsiBinaryExpression
            val possible = owners.filterNot { it is PsiCompiledElement }.flatMap { owner ->
                owner.methods.mapNotNull { handler ->
                    tick(handler)
                    val guarded = handler.body?.statements?.singleOrNull() as? PsiIfStatement ?: return@mapNotNull null
                    if (handler.hasModifierProperty(PsiModifier.STATIC) || guarded.elseBranch != null ||
                        runtime.findMethodBySignature(handler, true) != handler) return@mapNotNull null
                    val guard = binary(guarded.condition) ?: return@mapNotNull null
                    val body = (guarded.thenBranch as? PsiBlockStatement)?.codeBlock?.statements ?: return@mapNotNull null
                    if (body.size != 2) return@mapNotNull null
                    val mutation = (body[0] as? PsiExpressionStatement)?.expression as? PsiPostfixExpression ?: return@mapNotNull null
                    val write = reference(mutation.operand, index) ?: return@mapNotNull null
                    val call = (body[1] as? PsiExpressionStatement)?.expression as? PsiMethodCallExpression ?: return@mapNotNull null
                    if (call.resolveMethod() != selector || call.argumentList.expressionCount != 0 ||
                        !self(call.methodExpression.qualifierExpression) || !call.resolveMethodGenerics().isValidResult) return@mapNotNull null
                    val left = binary(guard.lOperand)
                    val right = binary(guard.rOperand)
                    val delta = when {
                        mutation.operationTokenType == JavaTokenType.MINUSMINUS && guard.operationTokenType == JavaTokenType.GT &&
                            reference(guard.lOperand, index) != null && literal(guard.rOperand, 0) -> -1
                        mutation.operationTokenType == JavaTokenType.PLUSPLUS && guard.operationTokenType == JavaTokenType.LT &&
                            (reference(guard.lOperand, index) != null && right?.operationTokenType == JavaTokenType.MINUS &&
                                size(right.lOperand) && literal(right.rOperand, 1) ||
                             left?.operationTokenType == JavaTokenType.PLUS && reference(left.lOperand, index) != null &&
                                literal(left.rOperand, 1) && size(guard.rOperand)) -> 1
                        else -> return@mapNotNull null
                    }
                    WizardStep(delta, handler, guard, write)
                }
            }
            val writes = owners.filterNot { it is PsiCompiledElement }.flatMap { owner ->
                descendants(owner, PsiReferenceExpression::class.java).filter { it.resolve() == index && PsiUtil.isAccessedForWriting(it) }
            }.toSet()
            if (writes != possible.map { it.write }.toSet()) return emptyList()
            possible.forEach { source(it.method) }
            return possible
        }

        fun fluent(method: PsiMethod, runtime: PsiClass, active: Set<PsiMethod> = emptySet()): Boolean {
            tick(method)
            if (method in active || active.size >= limits.depth) return false
            val actual = runtime.findMethodBySignature(method, true) ?: method
            if (actual != method) return fluent(actual, runtime, active + method)
            if (method is PsiCompiledElement) return method.containingClass?.qualifiedName in setOf("org.apache.wicket.Component", "org.apache.wicket.markup.html.form.FormComponent") &&
                method.name in setOf("setRequired", "setType", "setLabel", "setOutputMarkupId", "setVisible", "setEnabled", "setDefaultModel")
            source(method)
            val returns = descendants(method.body ?: return false, PsiReturnStatement::class.java).filter { statement ->
                PsiTreeUtil.getParentOfType(statement, PsiMethod::class.java) == method && generateSequence(statement.parent) { it.parent }.takeWhile { it != method }.none { it is PsiLambdaExpression }
            }
            val terminal = method.body!!.statements.lastOrNull() as? PsiReturnStatement ?: return false
            if (returns != listOf(terminal)) return false
            fun receiver(expression: PsiExpression?): Boolean = when (val value = expression?.let(::unwrap)) {
                is PsiThisExpression -> self(value)
                is PsiTypeCastExpression -> receiver(value.operand)
                is PsiMethodCallExpression -> self(value.methodExpression.qualifierExpression) && value.resolveMethod()?.let { !it.hasModifierProperty(PsiModifier.STATIC) && fluent(it, runtime, active + method) } == true
                else -> false
            }
            return receiver(terminal.returnValue)
        }
        fun fieldCreation(expression: PsiExpression, depth: Int = 0): PsiNewExpression? {
            tick(expression)
            if (depth >= limits.depth) throw Bound(expression)
            return when (val value = unwrap(expression)) {
                is PsiNewExpression -> value.takeIf { it.anonymousClass == null }
                is PsiMethodCallExpression -> {
                    val creation = value.methodExpression.qualifierExpression?.let { fieldCreation(it, depth + 1) } ?: return null
                    val runtime = creation.classReference?.resolve() as? PsiClass ?: return null
                    val method = value.resolveMethod() ?: return null
                    creation.takeIf { fluent(method, runtime) }
                }
                else -> null
            }
        }
        /** Behavior attachment has a reviewed framework receiver return; behavior execution stays opaque. */
        fun contentCreation(expression: PsiExpression, depth: Int = 0): PsiNewExpression? {
            tick(expression)
            if (depth >= limits.depth) throw Bound(expression)
            val value = unwrap(expression)
            if (value is PsiNewExpression) return value.takeIf { it.anonymousClass == null }
            val call = value as? PsiMethodCallExpression ?: return null
            val receiver = call.methodExpression.qualifierExpression ?: return null
            val allocation = contentCreation(receiver, depth + 1) ?: return null
            val runtime = allocation.classReference?.resolve() as? PsiClass ?: return null
            val method = call.resolveMethod() ?: return null
            val behaviorAdd = method.name == "add" && method.containingClass?.qualifiedName in setOf("org.apache.wicket.Component", "org.apache.wicket.MarkupContainer") &&
                call.argumentList.expressions.isNotEmpty() && call.argumentList.expressions.all { argument ->
                    (argument.type as? PsiClassType)?.resolve()?.let { InheritanceUtil.isInheritor(it, "org.apache.wicket.behavior.Behavior") } == true
                }
            return allocation.takeIf { behaviorAdd || fluent(method, runtime) }
        }
        fun immediateFactories() {
            descendants(page, PsiLambdaExpression::class.java).forEach { lambda ->
                val call = (lambda.parent as? PsiExpressionList)?.parent as? PsiMethodCallExpression ?: return@forEach
                val setter = call.resolveMethod()?.takeIf(WicketImmediateFactoryContract::candidate) ?: return@forEach
                if (WicketTableToolbarContexts.candidate(setter)) return@forEach
                try {
                    if (reports.size >= limits.contexts) throw Bound(lambda)
                    source(page)
                    reports += immediateFactory(lambda, call, setter)
                } catch (unknown: Unproved) {
                    issues += Diagnostic(DiagnosticCode.UNRESOLVED_SCOPE, "Immediate content-factory template withheld: ${unknown.message}", location(unknown.at))
                }
            }
        }
        /** replace requires a prior component, not just an HTML ID. Other constructor effects stay unknown. */
        fun constructorPlaceholders(constructor: PsiMethod, arguments: List<PsiExpression>, contentId: String,
            callerValues: Map<PsiParameter, String?> = emptyMap(), active: Set<PsiMethod> = emptySet()): List<PsiNewExpression> {
            source(constructor)
            if (constructor in active || active.size >= limits.depth) throw Bound(constructor)
            if (constructor is PsiCompiledElement) return emptyList()
            val values = constructor.parameterList.parameters.mapIndexed { index, parameter -> parameter to string(arguments.getOrNull(index), callerValues).first }.toMap()
            val body = constructor.body ?: fail(constructor, "The host constructor body is unavailable.")
            val result = mutableListOf<PsiNewExpression>()
            val delegation = (body.statements.firstOrNull() as? PsiExpressionStatement)?.expression as? PsiMethodCallExpression
            if (delegation?.methodExpression?.referenceName in setOf("this", "super")) {
                val target = delegation!!.resolveMethod() ?: fail(delegation, "Host constructor delegation does not resolve.")
                result += constructorPlaceholders(target, delegation.argumentList.expressions.toList(), contentId, values, active + constructor)
            }
            descendants(body, PsiMethodCallExpression::class.java).filter { direct(it, constructor) && self(it.methodExpression.qualifierExpression) }.forEach { call ->
                val target = call.resolveMethod() ?: return@forEach
                if (target.name in setOf("remove", "removeAll", "replace", "replaceWith", "addOrReplace", "queue") &&
                    target.containingClass?.let { component(JavaPsiFacade.getElementFactory(page.project).createType(it)) } == true)
                    fail(call, "Known host constructor mutations invalidate the declared replacement placeholder.")
                if (target.name == "add" && target.containingClass?.qualifiedName == "org.apache.wicket.MarkupContainer") {
                    call.argumentList.expressions.forEach argumentLoop@ { argument ->
                        val allocation = fieldCreation(argument) ?: return@argumentLoop
                        if ((allocation.classReference?.resolve() as? PsiClass)?.qualifiedName != "org.apache.wicket.markup.html.WebMarkupContainer") return@argumentLoop
                        val placeholder = slot(allocation, values)
                        if (placeholder.id == contentId) result += allocation
                    }
                }
            }
            return result
        }
        fun immediateFactory(lambda: PsiLambdaExpression, call: PsiMethodCallExpression, setter: PsiMethod): PageReport {
            val pageMethod = PsiTreeUtil.getParentOfType(call, PsiMethod::class.java) ?: fail(call, "The registration has no source constructor.")
            if (!pageMethod.isConstructor || pageMethod.containingClass != page || !direct(call, pageMethod) || call.argumentList.expressions.singleOrNull() != lambda)
                fail(call, "The factory must be registered by a direct page constructor statement.")
            val hostReference = call.methodExpression.qualifierExpression as? PsiReferenceExpression ?: fail(call, "The registered host requires a direct local identity.")
            val hostVariable = hostReference.resolve() as? PsiLocalVariable ?: fail(call, "The registered host requires a local allocation.")
            if (hostVariable.parent.parent != pageMethod.body) fail(hostVariable, "The host allocation is conditional or nested.")
            val hostCreation = hostVariable.initializer as? PsiNewExpression ?: fail(hostVariable, "The host must have one direct fresh allocation.")
            if (hostCreation.anonymousClass != null) fail(hostCreation, "Anonymous host dispatch is unreviewed.")
            val host = slot(hostCreation, emptyMap())
            source(host.type)
            if (host.type.findMethodBySignature(setter, true) != setter) fail(call, "The source setter has unreviewed runtime dispatch.")
            val hostName = host.type.qualifiedName ?: fail(hostCreation, "The host source name is unavailable.")
            if (JavaPsiFacade.getInstance(page.project).findClasses(hostName, page.resolveScope).singleOrNull() != host.type)
                fail(hostCreation, "The host declaration is ambiguous in the selected source context.")
            val assignments = descendants(pageMethod.body!!, PsiAssignmentExpression::class.java).filter { (it.lExpression as? PsiReferenceExpression)?.resolve() == hostVariable }
            if (assignments.isNotEmpty()) fail(assignments.first(), "The registered host is reassigned.")
            val calls = descendants(pageMethod.body!!, PsiMethodCallExpression::class.java)
            val attachments = calls.filter { added ->
                direct(added, pageMethod) && added.resolveMethod()?.let { it.containingClass?.qualifiedName == "org.apache.wicket.MarkupContainer" && it.name == "add" } == true &&
                    self(added.methodExpression.qualifierExpression) && added.argumentList.expressions.any { (it as? PsiReferenceExpression)?.resolve() == hostVariable }
            }
            if (attachments.size != 1) fail(call, "The host has no unique direct page attachment.")
            if (calls.count { it.resolveMethod() == setter && (it.methodExpression.qualifierExpression as? PsiReferenceExpression)?.resolve() == hostVariable } != 1)
                fail(call, "The host has multiple registrations for this factory.")
            calls.filter { direct(it, pageMethod) }.forEach { operation ->
                val target = operation.resolveMethod()
                val receiver = operation.methodExpression.qualifierExpression
                if (target?.name in setOf("remove", "removeAll", "replace", "replaceWith", "addOrReplace", "queue") &&
                    target?.containingClass?.let { component(JavaPsiFacade.getElementFactory(page.project).createType(it)) } == true &&
                    (self(receiver) || (receiver as? PsiReferenceExpression)?.resolve() == hostVariable))
                    fail(operation, "Known page/host mutations invalidate the declared placement route.")
                if (operation != attachments.single() && operation.argumentList.expressions.any { (it as? PsiReferenceExpression)?.resolve() == hostVariable })
                    fail(operation, "The host escapes its direct attachment and receiver calls.")
            }
            val site = WicketImmediateFactoryContract(::source, { descendants(it, PsiElement::class.java) }, { string(it).first }, limits.depth, ::fail).inspect(setter)
            val (contentId, contentOrigin) = string(site.invocation.argumentList.expressions.single())
            if (contentId.isNullOrEmpty() || contentId.any { it == ':' || it == '~' } || contentOrigin == null)
                fail(site.invocation, "The factory receives an unknown or reserved ID.")
            val placeholder = constructorPlaceholders(hostCreation.resolveConstructor() ?: fail(hostCreation, "The host constructor does not resolve."),
                hostCreation.argumentList?.expressions.orEmpty().toList(), contentId).singleOrNull()
                ?: fail(hostCreation, "A unique direct framework container placeholder must be declared by the selected host constructor chain.")
            val factory = WicketContentFactoryDeclarations(page, primary.rootNodeId, 128, ::tick, ::source, sources,
                { descendants(it, PsiElement::class.java) }, { contentCreation(it) }, ::fail, { throw Bound(it) }).analyze(lambda)
            val registration = ContentFactoryRegistration(location(call), location(hostCreation), location(contentOrigin), location(placeholder), factory.configurationLocations, factory.callbackLocations)
            return project(lambda, site.invocation, setter, site.replacement, host, emptyList(), contentId, contentOrigin, factory.owner, factory.fragment, contentFactory = registration)
        }
        fun fragmentFactory(lambda: PsiLambdaExpression, contentId: String): Pair<PageReport, FragmentReference> {
            val block = lambda.body as? PsiCodeBlock ?: fail(lambda, "Step factory requires a direct source block.")
            val returns = descendants(block, PsiReturnStatement::class.java).filter { PsiTreeUtil.getParentOfType(it, PsiLambdaExpression::class.java) == lambda }
            val returned = (block.statements.lastOrNull() as? PsiReturnStatement)?.returnValue as? PsiReferenceExpression ?: fail(lambda, "Step factory must return a direct fragment local.")
            if (returns.size != 1) fail(lambda, "Step factory has conditional returns.")
            val variable = returned.resolve() as? PsiLocalVariable ?: fail(returned, "Step fragment identity is unresolved.")
            val creation = variable.initializer as? PsiNewExpression ?: fail(variable, "Step root is not a fresh fragment.")
            if ((creation.classReference?.resolve() as? PsiClass)?.qualifiedName != WicketFragmentAnalyzer.TYPE || creation.anonymousClass != null || variable.parent?.parent != block)
                fail(creation, "Only a direct framework Fragment root is supported.")
            if (descendants(block, PsiAssignmentExpression::class.java).any { (it.lExpression as? PsiReferenceExpression)?.resolve() in listOf(variable, lambda.parameterList.parameters.single()) })
                fail(lambda, "The fragment identity or supplied ID is reassigned.")
            val uses = descendants(block, PsiReferenceExpression::class.java).filter { it.resolve() == variable }
            if (uses.any { use -> use != returned && !((use.parent as? PsiReferenceExpression)?.let { it.qualifierExpression == use && it.referenceName == "add" } == true) })
                fail(lambda, "The selected fragment escapes its direct additions and return.")
            val args = creation.argumentList?.expressions.orEmpty()
            val forwarded = (args.firstOrNull() as? PsiReferenceExpression)?.resolve() == lambda.parameterList.parameters.single()
            if (!forwarded) fail(creation, "The fragment must forward the supplied step ID.")
            val definition = args.getOrNull(1) as? PsiLiteralExpression ?: fail(creation, "The fragment definition must be literal.")
            val provider = args.getOrNull(2) as? PsiThisExpression ?: fail(creation, "The fragment provider must be the page.")
            if (provider.qualifier != null && provider.qualifier?.resolve() != page) fail(provider, "Foreign fragment providers are unsupported.")
            val root = "step-factory:${lambda.textOffset}"
            val nodes = mutableListOf(ComponentNode(root, null, WicketFragmentAnalyzer.TYPE, null, location(creation), confidence = Confidence.CONFIRMED, idRenameSafe = false))
            val calls = descendants(block, PsiMethodCallExpression::class.java).filter { PsiTreeUtil.getParentOfType(it, PsiLambdaExpression::class.java) == lambda }
            calls.forEach { call ->
                val target = call.resolveMethod() ?: fail(call, "A step call does not resolve.")
                if (target.name in setOf("add", "remove", "removeAll", "replace", "replaceWith", "addOrReplace", "queue") &&
                    target.containingClass?.let { component(JavaPsiFacade.getElementFactory(page.project).createType(it)) } == true) {
                    if (target.name != "add" || (call.methodExpression.qualifierExpression as? PsiReferenceExpression)?.resolve() != variable ||
                        PsiTreeUtil.getParentOfType(call, PsiStatement::class.java)?.parent != block) fail(call, "Step structural effects escape the direct fragment scope.")
                    call.argumentList.expressions.forEach { child ->
                        val allocation = fieldCreation(child) ?: fail(child, "Step children must be fresh components with proved fluent receiver returns.")
                        val identity = WicketComponentIdentity.trace(allocation) ?: fail(allocation, "Step child ID forwarding is unproved.")
                        sources += identity.sources
                        val literal = identity.expression as? PsiLiteralExpression ?: fail(allocation, "Step child IDs must be literal.")
                        val id = literal.value as? String ?: fail(literal, "Step child ID is not a String.")
                        if (id.isEmpty() && !WicketCompletionAnalysis.allowsEmpty(literal) || id.any { it == ':' || it == '~' }) fail(literal, "Step child ID is reserved.")
                        val type = allocation.classReference?.resolve() as? PsiClass ?: fail(allocation, "Step child type is unresolved.")
                        nodes += ComponentNode("$root:${allocation.textOffset}", id, type.qualifiedName!!, root, location(allocation), confidence = Confidence.CONFIRMED,
                            idLocation = location(literal), idRenameSafe = false, diagnostics = listOf(Diagnostic(DiagnosticCode.UNRESOLVED_SCOPE,
                                "Step child attachment is source-declared; constructor/configuration effects, owned internals, model values and conversion remain unexpanded.", location(allocation))))
                    }
                }
            }
            if (nodes.size == 1) fail(lambda, "No direct step child declarations are available.")
            if (nodes.drop(1).groupingBy { it.wicketId }.eachCount().any { it.value > 1 }) fail(lambda, "Step child IDs are ambiguous.")
            val use = FragmentReference(definition.value as? String ?: fail(definition, "Definition ID is not a String."), primary.rootNodeId,
                location(creation), location(definition))
            return PageReport(WicketFragmentAnalyzer.TYPE, root, nodes, javaSourceFiles = sources.sorted()) to use
        }
        fun consume(declaration: PsiMethod, parameter: PsiVariable, root: Slot, runtime: PsiClass, active: Set<PsiMethod>): Pair<Slot, PsiMethodCallExpression> {
            tick(declaration); source(declaration)
            if (declaration in active || active.size >= limits.depth) throw Bound(declaration)
            val actual = runtime.findMethodBySignature(declaration, true) ?: declaration
            if (actual != declaration && actual !is PsiCompiledElement) fail(actual, "The replacement consumer has an unreviewed runtime override.")
            if (declaration is PsiCompiledElement) fail(declaration, "The replacement consumer source is unavailable.")
            val body = declaration.body ?: fail(declaration, "The replacement consumer body is unavailable.")
            val uses = descendants(body, PsiReferenceExpression::class.java).filter { it.resolve() == parameter }
            val transfers = uses.mapNotNull { use ->
                val args = use.parent as? PsiExpressionList ?: return@mapNotNull null
                val call = args.parent as? PsiMethodCallExpression ?: return@mapNotNull null
                if (!direct(call, declaration)) fail(call, "Replacement transfer is conditional or nested.")
                val target = call.resolveMethod() ?: fail(call, "Replacement transfer does not resolve.")
                val destination = resolve(call.methodExpression.qualifierExpression, root) ?: fail(call, "Replacement destination is not an owned field.")
                if (target.containingClass?.qualifiedName == "org.apache.wicket.MarkupContainer" && target.name == "addOrReplace") destination to call
                else {
                    if (destination !== root) fail(call, "Cross-object consumer forwarding is not reviewed.")
                    val index = args.expressions.indexOf(use)
                    consume(target, target.parameterList.parameters.getOrNull(index) ?: fail(call, "Replacement parameter is unavailable."), root, runtime, active + declaration)
                }
            }
            fun throwingGuard(use: PsiElement): Boolean {
                val guard = PsiTreeUtil.getParentOfType(use, PsiIfStatement::class.java) ?: return false
                val block = guard.thenBranch as? PsiBlockStatement ?: return false
                return guard.elseBranch == null && block.codeBlock.statements.singleOrNull() is PsiThrowStatement
            }
            uses.filter { it.parent !is PsiExpressionList }.forEach { use ->
                val query = use.parent as? PsiReferenceExpression
                val call = query?.parent as? PsiMethodCallExpression
                if ((query?.referenceName != "getId" || call == null || use != query.qualifierExpression) && !throwingGuard(use))
                    fail(use, "The replacement component escapes its parameter flow.")
            }
            return transfers.singleOrNull() ?: fail(declaration, "A unique unconditional replacement site is required.")
        }
        fun project(reference: PsiExpression, invocation: PsiMethodCallExpression, event: PsiMethod, replacement: PsiMethodCallExpression,
            host: Slot, route: List<Slot>, contentId: String, contentOrigin: PsiExpression?, owner: PageReport,
            fragment: FragmentReference? = null, selection: RegisteredSelection? = null, contentFactory: ContentFactoryRegistration? = null): PageReport {
            val key = "replacement:${location(reference).file}:${reference.textOffset}"
            val rootId = "$key:root"
            val nodes = mutableListOf(ComponentNode(rootId, null, page.qualifiedName!!, null, location(page), confidence = Confidence.CONFIRMED, idRenameSafe = false))
            val elements = mutableListOf<MarkupElement>()
            val markupSources = linkedSetOf<String>()
            var parentNode = rootId
            var parentMarkup: String? = null
            var provider = page
            var localPath = mutableListOf<String>()
            val scopes = listOf(host) + route
            fun element(type: PsiClass, path: List<String>): MarkupElement {
                source(type)
                if (hierarchy(type).any { cls -> cls.methods.any { it.name in WicketFragmentAnalyzer.PROVIDER_METHODS && it.findDeepestSuperMethods().isNotEmpty() } })
                    fail(type, "Custom markup sourcing is not a replacement-template contract.")
                val resolution = WicketPageResolver().resolve(type)
                val markup = resolution.markupPath?.let { path -> WicketSourceFiles.find(page.project, path)?.let { PsiManager.getInstance(page.project).findFile(it) } } ?: fail(type, "Conventional source markup is unavailable.")
                sources += markup.virtualFile.path; markupSources += markup.virtualFile.path
                val raw = WicketMarkupAnalyzer().analyzeInheritanceSource(markup, WicketComponentTypes.kind(type))
                if (raw.documentDiagnostics.isNotEmpty()) fail(markup, "Markup syntax or document context is unresolved.")
                val byId = raw.elements.associateBy { it.nodeId }
                val selected = raw.elements.filter { candidate -> candidate.hasWicketId && generateSequence(candidate) { byId[it.parentWicketElementId] }.mapNotNull { it.wicketId }.toList().asReversed() == path }
                val selectedElement = selected.singleOrNull() ?: fail(markup, "A unique resource-local placement path is not established.")
                if (selectedElement.confidence != Confidence.CONFIRMED || selectedElement.diagnostics.isNotEmpty()) fail(markup, "The resource-local placement context is unresolved.")
                return selectedElement
            }
            scopes.forEachIndexed { index, slot ->
                tick(reference)
                localPath += slot.id!!
                val selected = element(provider, localPath)
                val id = "$key:route:$index"
                nodes += ComponentNode(id, slot.id, slot.type.qualifiedName!!, parentNode, location(slot.creation!!), confidence = Confidence.CONFIRMED,
                    idLocation = slot.origin?.let(::location), idRenameSafe = false, template = true)
                val markupId = "$key:markup:route:$index"
                elements += selected.copy(nodeId = markupId, parentElementId = parentMarkup, parentWicketElementId = parentMarkup,
                    confidence = Confidence.CONFIRMED, diagnostics = emptyList(), participatesInComponentTree = true)
                parentNode = id; parentMarkup = markupId
                if (WicketComponentTypes.ownsMarkup(slot.type)) { provider = slot.type; localPath = mutableListOf() }
            }
            localPath += contentId
            val selected = element(provider, localPath)
            val contentNode = "$key:content"
            val contentMarkup = "$key:markup:content"
            nodes += ComponentNode(contentNode, contentId, owner.qualifiedClassName, parentNode, location(reference), confidence = Confidence.CONFIRMED,
                idLocation = contentOrigin?.let(::location), idRenameSafe = false, computedId = true, template = true, sourceSetupExpanded = fragment == null, ownedMarkupPath = owner.markupPath,
                fragment = fragment?.copy(providerNodeId = rootId))
            elements += selected.copy(nodeId = contentMarkup, parentElementId = parentMarkup, parentWicketElementId = parentMarkup,
                confidence = Confidence.CONFIRMED, diagnostics = emptyList(), participatesInComponentTree = true)
            val ownerNodes = owner.nodes.filter { it.nodeId != owner.rootNodeId }
            val ids = ownerNodes.associate { it.nodeId to "$key:owned:${it.nodeId}" }
            ownerNodes.forEach { node -> nodes += node.copy(nodeId = ids.getValue(node.nodeId), parentNodeId = if (node.parentNodeId == owner.rootNodeId) contentNode else ids[node.parentNodeId],
                idRenameSafe = false, template = true) }
            if (fragment == null) {
                val matchedElements = owner.matching!!.findings.filter { it.code == DiagnosticCode.MATCHED }.mapNotNull { it.markupNodeId }.toSet()
                val ownerElements = owner.markup!!.elements.filter { it.nodeId in matchedElements }
                val markupIds = ownerElements.associate { it.nodeId to "$key:owned-markup:${it.nodeId}" }
                ownerElements.forEach { el -> elements += el.copy(nodeId = markupIds.getValue(el.nodeId), parentElementId = markupIds[el.parentWicketElementId] ?: contentMarkup,
                    parentWicketElementId = markupIds[el.parentWicketElementId] ?: contentMarkup, markupProviderNodeId = contentNode) }
                markupSources += owner.markup.sourceFiles
            } else {
                val raw = primary.markup ?: fail(reference, "Fragment provider markup is unavailable.")
                val definitions = linkedSetOf<String>()
                raw.elements.forEach { el -> if (el.tagName == "wicket:fragment" || el.parentElementId in definitions) definitions += el.nodeId }
                val ids = definitions.associateWith { "$key:definition:$it" }
                raw.elements.filter { it.nodeId in definitions }.forEach { el -> elements += el.copy(nodeId = ids.getValue(el.nodeId),
                    parentElementId = ids[el.parentElementId], parentWicketElementId = null, participatesInComponentTree = false, markupProviderNodeId = null) }
                markupSources += raw.sourceFiles
            }
            val warning = Diagnostic(DiagnosticCode.PARTIAL_ANALYSIS,
                (when { contentFactory != null -> "Source-declared immediate content-factory template; factory/configuration/callback execution, presence"
                    selection == null -> "Source-declared replacement-site event template; presence"
                    else -> "Source-declared registered-step selection template; current step" }) +
                    ", invocation, other page/lifecycle effects, record values, validation and subsequent tree state remain unknown. This is not a current runtime hierarchy.", location(invocation))
            val path = (scopes.map { it.id!! } + contentId).joinToString(":")
            val result = PageReport(page.qualifiedName!!, rootId, nodes, primary.markupPath,
                warnings = listOf(warning), scopeComplete = false,
                markup = MarkupReport(primary.markupPath ?: fail(page, "Page markup is unavailable."), WicketComponentTypes.kind(page), elements,
                    false, emptyList(), emptyList(), markupSources.toList()), javaSourceFiles = sources.sorted(),
                lifecycle = ReplacementTemplate(key, path, contentNode, location(reference), location(invocation), location(event), location(replacement), owner.qualifiedClassName, selection, contentFactory))
            return WicketGraphMatcher().match(if (fragment == null) result else WicketFragmentAnalyzer().analyze(page, result))
        }
    }
}

private fun location(element: PsiElement) = SourceLocation(element.containingFile.virtualFile.path, element.textOffset)
