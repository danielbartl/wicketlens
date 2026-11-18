package dev.wicketlens.analysis

import com.intellij.psi.*
import com.intellij.openapi.progress.ProgressManager
import com.intellij.psi.util.InheritanceUtil
import com.intellij.psi.util.PsiTreeUtil
import dev.wicketlens.model.*

/** Initial source declarations only. Event bodies and model/configuration effects are never executed. */
internal class WicketInitialContentDeclarations(
    private val page: PsiClass,
    private val tick: (PsiElement) -> Unit,
    private val source: (PsiElement) -> Unit,
    private val sources: MutableSet<String>,
    private val fail: (PsiElement, String) -> Nothing,
) {
    data class Host(val nodes: List<ComponentNode>, val fields: Map<PsiField, String>, val setter: PsiMethod,
        val slot: String, val contentId: String, val constructors: List<PsiMethod>, val type: PsiClass, val callbacks: List<SourceLocation>,
        val placements: List<SourceLocation> = emptyList())
    companion object {
        fun applicable(page: PsiClass): Boolean {
            if (page.constructors.size != 1) return false
            var steps = 0; var allocations = 0; var found = false
            page.constructors.single().accept(object : JavaRecursiveElementWalkingVisitor() {
                override fun visitElement(element: PsiElement) { ProgressManager.checkCanceled(); if (!found && ++steps <= 20000) super.visitElement(element) }
                override fun visitClass(aClass: PsiClass) {}
                override fun visitLambdaExpression(expression: PsiLambdaExpression) {}
                override fun visitNewExpression(expression: PsiNewExpression) {
                    ProgressManager.checkCanceled()
                    if (found || steps > 20000) return
                    if (++allocations > 256) return
                    found = (expression.anonymousClass?.baseClassType?.resolve() ?: expression.classReference?.resolve() as? PsiClass)?.let(::candidate) == true
                    if (!found) super.visitNewExpression(expression)
                }
            })
            return found
        }
        fun candidate(type: PsiClass): Boolean {
            ProgressManager.checkCanceled()
            if (type is PsiCompiledElement || !InheritanceUtil.isInheritor(type, "org.apache.wicket.markup.html.panel.Panel")) return false
            var steps=0;var found=false
            for(owner in generateSequence(type) { it.superClass }.take(12).takeWhile { it !is PsiCompiledElement }) {
                for(setter in owner.methods.take(128).filter { it.parameterList.parameters.singleOrNull()?.type?.canonicalText == "org.apache.wicket.Component" }) {
                    setter.body?.accept(object : JavaRecursiveElementWalkingVisitor() {
                        override fun visitElement(element:PsiElement) { ProgressManager.checkCanceled();if(!found && ++steps<=20000) super.visitElement(element) }
                        override fun visitMethodCallExpression(expression:PsiMethodCallExpression) {
                            if(found || steps>20000) return
                            found=expression.resolveMethod()?.let { it.name=="addOrReplace" && it.containingClass?.qualifiedName=="org.apache.wicket.MarkupContainer" }==true
                            if(!found) super.visitMethodCallExpression(expression)
                        }
                    })
                    if(found) return true
                }
            }
            return false
        }
    }
    private val structure = WicketSourceDescendants(tick)
    private fun descendants(at: PsiElement) = structure.descendants(at)
    private fun call(s: PsiStatement?) = (s as? PsiExpressionStatement)?.expression as? PsiMethodCallExpression
    private fun own(e: PsiExpression?) = e == null || e is PsiThisExpression && e.qualifier == null || e is PsiSuperExpression
    private fun ref(e: PsiExpression?, target: PsiElement) = e is PsiReferenceExpression && e.resolve() == target && own(e.qualifierExpression)
    private fun hierarchy(type: PsiClass) = generateSequence(type) { it.superClass }.take(12).takeWhile { it !is PsiCompiledElement }.toList().also { list ->
        list.forEach(source)
        if (list.lastOrNull()?.superClass !is PsiCompiledElement) fail(type, "Initial content source ancestry exceeds 12 levels.")
    }

    fun inspect(creation: PsiNewExpression, root: String): Host {
        val actual = creation.anonymousClass ?: creation.classReference?.resolve() as? PsiClass ?: fail(creation, "Content host type is unavailable.")
        val type = creation.anonymousClass?.baseClassType?.resolve() ?: actual
        val owners = hierarchy(type)
        if (owners.any { owner -> owner.initializers.isNotEmpty() || owner.methods.any { method ->
                method.name in (WicketFragmentAnalyzer.PROVIDER_METHODS + setOf("onInitialize", "resolve")) && method.findDeepestSuperMethods().isNotEmpty()
            } }) fail(type, "Initial content hosts cannot override initialization or markup sourcing.")
        creation.anonymousClass?.let { anonymous ->
            if (anonymous.fields.isNotEmpty() || anonymous.initializers.isNotEmpty()) fail(anonymous, "Anonymous host initialization is unreviewed.")
            anonymous.methods.filterNot { it.isConstructor }.forEach { method ->
                if (method.returnType != PsiTypes.voidType() || method.parameterList.parameters.singleOrNull()?.type?.canonicalText != "org.apache.wicket.ajax.AjaxRequestTarget" ||
                    method.findDeepestSuperMethods().none { base -> base !is PsiCompiledElement && base.hasModifierProperty(PsiModifier.PROTECTED) && base.body?.statements?.isEmpty() == true })
                    fail(method, "Only source-declared void Ajax hooks with empty base implementations are supported on initial content hosts.")
                source(method)
            }
        }
        val setters = owners.flatMap { it.methods.toList() }.filter { setter ->
            setter.parameterList.parameters.singleOrNull()?.type?.canonicalText == "org.apache.wicket.Component" &&
                setter.body?.let { body -> descendants(body).filterIsInstance<PsiMethodCallExpression>().any { c ->
                    c.resolveMethod()?.let { it.name == "addOrReplace" && it.containingClass?.qualifiedName == "org.apache.wicket.MarkupContainer" } == true
                } } == true
        }
        val setter = setters.singleOrNull() ?: fail(type, "A unique direct source content setter is required.")
        val parameter = setter.parameterList.parameters.single()
        val statements = setter.body!!.statements.toList()
        val replacement = statements.mapNotNull(::call).singleOrNull { it.resolveMethod()?.let { m -> m.name == "addOrReplace" && m.containingClass?.qualifiedName == "org.apache.wicket.MarkupContainer" } == true }
            ?: fail(setter, "Content requires one direct owned replacement statement.")
        val field = (replacement.methodExpression.qualifierExpression as? PsiReferenceExpression)?.takeIf { own(it.qualifierExpression) }?.resolve() as? PsiField
            ?: fail(replacement, "The content parent must be an owned private final field.")
        if (!ref(replacement.argumentList.expressions.singleOrNull(), parameter) || field.containingClass !in owners ||
            !field.hasModifierProperty(PsiModifier.PRIVATE) || !field.hasModifierProperty(PsiModifier.FINAL) || field.hasModifierProperty(PsiModifier.STATIC))
            fail(replacement, "The content parameter must be replaced into its own private final parent.")
        if ((statements.lastOrNull() as? PsiReturnStatement)?.returnValue?.let { it is PsiThisExpression && it.qualifier == null } != true)
            fail(setter, "Content setters must return their own receiver.")
        val guards = statements.filterIsInstance<PsiIfStatement>()
        if (guards.size != 1 || statements.indexOf(guards.single()) >= statements.indexOfFirst { call(it) == replacement }) fail(setter, "One direct throwing ID guard must precede content replacement.")
        statements.dropLast(1).filter { it != guards.single() && call(it) != replacement }.forEach { statement ->
            val assignment = (statement as? PsiExpressionStatement)?.expression as? PsiAssignmentExpression
            val flag = (assignment?.lExpression as? PsiReferenceExpression)?.resolve() as? PsiField
            if (assignment?.operationTokenType != JavaTokenType.EQ || flag?.type != PsiTypes.booleanType() || flag.containingClass !in owners ||
                flag.hasModifierProperty(PsiModifier.STATIC) || !own((assignment.lExpression as PsiReferenceExpression).qualifierExpression) || assignment.rExpression !is PsiLiteralExpression)
                fail(statement, "Only owned boolean state declarations may accompany initial content placement.")
        }
        descendants(setter.body!!).filterIsInstance<PsiReferenceExpression>().filter { it.resolve() == parameter }.forEach { use ->
            if (use != replacement.argumentList.expressions.single() && !PsiTreeUtil.isAncestor(guards.single(), use, true)) fail(use, "The initial content parameter escapes its guard and replacement.")
        }
        val chain = mutableListOf<PsiMethod>()
        fun constructors(method: PsiMethod) {
            source(method)
            if (method is PsiCompiledElement) return
            if (method in chain || chain.size >= 12) fail(method, "Initial content constructor delegation is recursive or exceeds 12 levels.")
            chain += method
            val delegation = call(method.body?.statements?.firstOrNull())
            if (delegation?.methodExpression?.referenceName !in setOf("this", "super")) fail(method, "Initial content constructors require direct delegation.")
            constructors(delegation!!.resolveMethod() ?: fail(delegation, "Content host constructor delegation is unresolved."))
        }
        constructors(creation.resolveConstructor() ?: fail(creation, "Content host constructor is unresolved."))
        val structural = chain.singleOrNull { ctor -> ctor.body?.statements?.any { statement ->
            val assignment = (statement as? PsiExpressionStatement)?.expression as? PsiAssignmentExpression
            (assignment?.lExpression as? PsiReferenceExpression)?.resolve() == field
        } == true } ?: fail(creation, "One structural constructor must declare the owned content ancestry.")
        val declarations = WicketSourceDeclarations(page, 12, tick, source, sources, fail, { fail(it, "Initial content declaration budget exceeded.") }, ::descendants)
        val nodes = declarations.constructorNodes(structural, root, "$root:initial", 128, structural.containingClass!!, fields = true, ownedFormConfiguration = true)
        val fields = nodes.mapNotNull { node ->
            val at = descendants(structural).filterIsInstance<PsiNewExpression>().singleOrNull { location(it) == node.javaLocation }
            val assignment = at?.parent as? PsiAssignmentExpression
            ((assignment?.lExpression as? PsiReferenceExpression)?.resolve() as? PsiField)?.let { it to node.nodeId }
        }.toMap()
        val parent = fields[field] ?: fail(field, "The content parent has no selected constructor declaration.")
        val constants = WicketSourceConstants(source) { tick(page) }
        fun string(e: PsiExpression): String? = when(e) { is PsiLiteralExpression -> e.value as? String; is PsiReferenceExpression -> constants.resolve(e) as? String; else -> null }
        val expectedId = descendants(guards.single()).filterIsInstance<PsiMethodCallExpression>().singleOrNull { c ->
            c.resolveMethod()?.let { it.name == "equals" && it.containingClass?.qualifiedName == "java.lang.String" } == true
        }?.methodExpression?.qualifierExpression?.let(::string) ?: fail(setter, "The content guard needs a known String ID.")
        val placeholder = nodes.singleOrNull { it.parentNodeId == parent && it.wicketId == expectedId && it.componentType == "org.apache.wicket.markup.html.WebMarkupContainer" }
            ?: fail(field, "The content parent must declare one plain placeholder slot.")
        WicketImmediateFactoryContract(source, ::descendants, ::string, 12, fail)
            .inspectIdGuard(guards.single(), parameter, placeholder.wicketId!!)
        val callbacks = chain.flatMap(::descendants).filterIsInstance<PsiMethod>().filterNot { it.isConstructor }.map(::location).distinct()
        val initial = Host(nodes, fields, setter, placeholder.nodeId, placeholder.wicketId, chain.toList(), type, callbacks)
        val (host, installed) = constructorContent(initial, structural, declarations)
        // Declarations do not execute opaque configuration. Known component escapes and
        // calls into event hooks during construction still invalidate the source graph.
        chain.forEach { constructor ->
            constructor.body!!.statements.forEach { statement ->
                tick(statement)
                if (constructor != structural && statement !in installed) {
                    val c = call(statement) ?: fail(statement, "Only delegation and owned configuration may follow host delegation.")
                    if (c.methodExpression.referenceName !in setOf("super", "this")) validateConfiguration(selected(c, host), host)
                }
                descendants(statement).filterIsInstance<PsiMethodCallExpression>().filter { c ->
                    PsiTreeUtil.getParentOfType(c, PsiMethod::class.java) == constructor &&
                        generateSequence(c.parent) { it.parent }.takeWhile { it != statement.parent }.none { it is PsiLambdaExpression }
                }.forEach calls@ { c ->
                    if (statement in installed && c == call(statement)) return@calls
                    val target = selected(c, host)
                    if (target.containingClass in owners && !target.isConstructor) validateConfiguration(target, host)
                }
            }
        }
        return host
    }

    fun selected(call: PsiMethodCallExpression, host: Host, knownReceiver: Boolean = own(call.methodExpression.qualifierExpression)): PsiMethod {
        val method = call.resolveMethod() ?: fail(call, "Host configuration is unresolved.")
        return if (!knownReceiver || call.methodExpression.qualifierExpression is PsiSuperExpression || method.hasModifierProperty(PsiModifier.PRIVATE) ||
            method.hasModifierProperty(PsiModifier.STATIC) || method.hasModifierProperty(PsiModifier.FINAL)) method
        else host.type.findMethodBySignature(method, true) ?: method
    }

    /** Selected wrapper constructors may install one fresh private-final message into the inherited slot. */
    private fun constructorContent(host: Host, structural: PsiMethod, declarations: WicketSourceDeclarations): Pair<Host, Set<PsiStatement>> {
        val created = linkedMapOf<PsiField, ComponentNode>()
        val installed = mutableSetOf<PsiStatement>()
        var content: ComponentNode? = null
        var placement: SourceLocation? = null
        for (constructor in host.constructors.asReversed().filter { it != structural }) {
            for (statement in constructor.body!!.statements) {
                tick(statement)
                val assignment = (statement as? PsiExpressionStatement)?.expression as? PsiAssignmentExpression
                if (assignment != null && (declarations.component(assignment.lExpression.type) || declarations.component(assignment.rExpression?.type))) {
                    val target = assignment.lExpression as? PsiReferenceExpression ?: fail(assignment, "Constructor content requires an owned field.")
                    val field = target.resolve() as? PsiField ?: fail(target, "Constructor content cannot reassign an alias.")
                    source(field)
                    if (assignment.operationTokenType != JavaTokenType.EQ || !own(target.qualifierExpression) || field.containingClass != constructor.containingClass ||
                        !field.hasModifierProperty(PsiModifier.PRIVATE) || !field.hasModifierProperty(PsiModifier.FINAL) || field.hasModifierProperty(PsiModifier.STATIC) || field.initializer != null ||
                        descendants(field.containingFile).filterIsInstance<PsiAssignmentExpression>().count { (it.lExpression as? PsiReferenceExpression)?.resolve() == field } != 1)
                        fail(assignment, "Constructor content requires one direct private-final initialization.")
                    if (host.nodes.size + created.size >= 128) fail(assignment, "Initial content declaration budget exceeded.")
                    val expression = assignment.rExpression as? PsiNewExpression ?: fail(assignment, "Constructor content must be a direct fresh allocation.")
                    val value = declarations.declared(expression, emptyMap())
                    if (value.type !is PsiCompiledElement || value.type.qualifiedName !in setOf("org.apache.wicket.markup.html.basic.Label", "org.apache.wicket.markup.html.WebMarkupContainer") || value.id.value != host.contentId)
                        fail(expression, "Constructor content requires a framework Label/container with the guarded ID.")
                    created[field] = ComponentNode("${host.slot}:constructor:${expression.textOffset}", value.id.value, value.type.qualifiedName!!, null, location(expression),
                        confidence = Confidence.CONFIRMED, idLocation = value.id.origin?.let(::location), idRenameSafe = false, template = true)
                    installed += statement
                    continue
                }
                val call = call(statement) ?: continue
                if (call.methodExpression.referenceName in setOf("this", "super") || !own(call.methodExpression.qualifierExpression)) continue
                if (!isSetter(selected(call, host), host)) continue
                if (placement != null) fail(call, "Competing constructor content placements are unsupported.")
                val argument = call.argumentList.expressions.singleOrNull() as? PsiReferenceExpression
                    ?: fail(call, "Constructor content requires a direct owned field reference.")
                val field = argument.resolve() as? PsiField
                val child = created[field] ?: fail(call, "Constructor content must use its freshly initialized owned field.")
                if (!own(argument.qualifierExpression)) fail(argument, "Constructor content cannot borrow another receiver's field.")
                val slot = host.nodes.single { it.nodeId == host.slot }
                if (host.nodes.any { it.parentNodeId == slot.nodeId }) fail(call, "Constructor content cannot replace a populated placeholder.")
                content = child.copy(parentNodeId = slot.parentNodeId)
                placement = location(call)
                installed += statement
            }
        }
        if (created.isEmpty()) return host to emptySet()
        if (created.size != 1 || content == null || placement == null) fail(structural, "Constructor content fields require one unique initial placement.")
        val callbacks = hierarchy(host.type).flatMap { it.methods.toList() }.filter { method ->
            !method.isConstructor && method.returnType == PsiTypes.voidType() &&
                method.parameterList.parameters.singleOrNull()?.type?.canonicalText == "org.apache.wicket.ajax.AjaxRequestTarget" &&
                method.findDeepestSuperMethods().any { it !is PsiCompiledElement && it.hasModifierProperty(PsiModifier.PROTECTED) && it.body?.statements?.isEmpty() == true }
        }.onEach(source).map(::location)
        return host.copy(nodes = host.nodes.filter { it.nodeId != host.slot } + content,
            fields = host.fields.filterValues { it != host.slot } + created.mapValues { it.value.nodeId },
            slot = content.nodeId, callbacks = (host.callbacks + callbacks).distinct(), placements = listOf(placement)) to installed
    }

    fun isSetter(method: PsiMethod, host: Host): Boolean {
        source(method)
        if (method == host.setter) return true
        if (method.parameterList.parametersCount != 1 || method.parameterList.parameters.single().type.canonicalText != "org.apache.wicket.Component") return false
        val statements = method.body?.statements ?: return false
        val forwarded = call(statements.firstOrNull()) ?: return false
        return statements.size == 2 && forwarded.methodExpression.qualifierExpression is PsiSuperExpression &&
            forwarded.resolveMethod()?.let { isSetter(it, host) } == true && ref(forwarded.argumentList.expressions.singleOrNull(), method.parameterList.parameters.single()) &&
            (statements.last() as? PsiReturnStatement)?.returnValue?.let { it is PsiThisExpression && it.qualifier == null } == true
    }

    fun returnsReceiver(method: PsiMethod, active: Set<PsiMethod> = emptySet()): Boolean {
        source(method)
        if (method in active || active.size >= 12 || method.hasModifierProperty(PsiModifier.STATIC)) return false
        val body = method.body ?: return false
        val returned = body.statements.lastOrNull() as? PsiReturnStatement ?: return false
        if (descendants(body).filterIsInstance<PsiReturnStatement>().filter { PsiTreeUtil.getParentOfType(it, PsiMethod::class.java) == method } != listOf(returned)) return false
        fun self(e: PsiExpression?): Boolean = when(e) {
            is PsiThisExpression -> e.qualifier == null
            is PsiTypeCastExpression -> self(e.operand)
            is PsiParenthesizedExpression -> self(e.expression)
            is PsiMethodCallExpression -> own(e.methodExpression.qualifierExpression) && e.argumentList.expressionCount == 0 && e.resolveMethod()?.let { target ->
                (target.hasModifierProperty(PsiModifier.PRIVATE) || target.hasModifierProperty(PsiModifier.FINAL)) && returnsReceiver(target, active + method)
            } == true
            else -> false
        }
        return self(returned.returnValue)
    }

    fun validateConfiguration(method: PsiMethod, host: Host, active: Set<PsiMethod> = emptySet()) {
        source(method)
        if (method in active || active.size >= 12) fail(method, "Initial host configuration is recursive or exceeds 12 levels.")
        if (method is PsiCompiledElement) return
        val returned = (method.body?.statements?.singleOrNull() as? PsiReturnStatement)?.returnValue as? PsiReferenceExpression
        val returnedField = returned?.resolve() as? PsiField
        if (method.hasModifierProperty(PsiModifier.FINAL) && method.parameterList.parametersCount == 0 && returnedField in host.fields && own(returned?.qualifierExpression)) return
        if (method.parameterList.parameters.any { (it.type as? PsiClassType)?.resolve()?.let { t -> t.qualifiedName == "org.apache.wicket.Component" || InheritanceUtil.isInheritor(t, "org.apache.wicket.Component") } == true })
            fail(method, "Initial host configuration cannot receive components.")
        if ((method.body?.statements?.lastOrNull() as? PsiReturnStatement)?.returnValue?.let { it is PsiThisExpression && it.qualifier == null } != true)
            fail(method, "Only fluent source host configuration is supported; event hooks cannot run during setup.")
        descendants(method.body!!).forEach { e ->
            if (e is PsiMethodCallExpression) {
                val target = e.resolveMethod() ?: fail(e, "Host configuration is unresolved.")
                if (target.containingClass in hierarchy(host.type)) validateConfiguration(selected(e, host), host, active + method)
                if (target.name in setOf("remove", "removeAll", "replace", "replaceWith", "addOrReplace", "queue") || target.name == "add" &&
                    e.argumentList.expressions.any { (it.type as? PsiClassType)?.resolve()?.let { t -> t.qualifiedName == "org.apache.wicket.Component" || InheritanceUtil.isInheritor(t, "org.apache.wicket.Component") } == true }) fail(e, "Host configuration has structural component effects.")
                e.argumentList.expressions.forEach { a -> if (a is PsiThisExpression || (a.type as? PsiClassType)?.resolve()?.let { t -> t.qualifiedName == "org.apache.wicket.Component" || InheritanceUtil.isInheritor(t, "org.apache.wicket.Component") } == true) fail(a, "Host configuration exposes a component.") }
            }
            if (e is PsiAssignmentExpression && (e.lExpression.type as? PsiClassType)?.resolve()?.let { it.qualifiedName == "org.apache.wicket.Component" || InheritanceUtil.isInheritor(it, "org.apache.wicket.Component") } == true)
                fail(e, "Host configuration changes component storage.")
        }
    }
}
private fun location(e: PsiElement) = SourceLocation(e.containingFile.virtualFile.path, e.textOffset)
