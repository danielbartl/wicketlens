package dev.wicketlens.analysis

import com.intellij.psi.*
import com.intellij.psi.util.InheritanceUtil
import com.intellij.psi.util.PsiTreeUtil
import dev.wicketlens.model.*

/** Invocation-local declarations only; constructor/model/configuration effects remain unexpanded. */
internal class WicketSourceDeclarations(
    private val page: PsiClass,
    private val depthLimit: Int,
    private val tick: (PsiElement) -> Unit,
    private val source: (PsiElement) -> Unit,
    private val sources: MutableSet<String>,
    private val fail: (PsiElement, String) -> Nothing,
    private val bound: (PsiElement) -> Nothing,
    private val descend: (PsiElement) -> List<PsiElement>,
) {
    data class Id(val value: String, val origin: PsiExpression?)
    data class Declared(val id: Id, val type: PsiClass, val at: PsiExpression, val possibilities: List<String> = emptyList())
    data class Created(val creation: PsiNewExpression, val ids: Map<PsiVariable, Id>)
    private val constants = WicketSourceConstants(source) { tick(page) }
    private fun <T : PsiElement> descendants(at: PsiElement, type: Class<T>) = descend(at).filter(type::isInstance).map(type::cast)
    fun unwrap(e: PsiExpression): PsiExpression = if (e is PsiParenthesizedExpression) e.expression?.let(::unwrap) ?: e else e
    private fun self(e: PsiExpression?) = e == null || e is PsiThisExpression && e.qualifier == null
    fun component(type: PsiType?) = (type as? PsiClassType)?.resolve()?.let {
        it.qualifiedName == "org.apache.wicket.Component" || InheritanceUtil.isInheritor(it, "org.apache.wicket.Component")
    } == true
    private fun direct(e: PsiElement, method: PsiMethod) = PsiTreeUtil.getParentOfType(e, PsiStatement::class.java)?.parent == method.body &&
        PsiTreeUtil.getParentOfType(e, PsiMethod::class.java) == method && generateSequence(e.parent) { it.parent }.takeWhile { it != method }.none { it is PsiLambdaExpression }

    /** A direct fresh receiver can follow source fluent forwarding without guessing alias dispatch. */
    fun freshCreation(expression: PsiExpression): PsiNewExpression? {
        var current=unwrap(expression);val calls=mutableListOf<PsiMethodCallExpression>()
        while(current is PsiMethodCallExpression) {
            tick(current)
            if(calls.size>=depthLimit) bound(current)
            calls+=current;current=current.methodExpression.qualifierExpression?.let(::unwrap) ?: return null
        }
        val creation=current as? PsiNewExpression ?: return null
        val runtime=creation.anonymousClass ?: creation.classReference?.resolve() as? PsiClass ?: return null
        fun returnsSelf(method:PsiMethod,active:Set<PsiMethod>):Boolean {
            tick(method);source(method)
            if(active.size>=depthLimit) bound(method)
            if(method in active || method.hasModifierProperty(PsiModifier.STATIC)) return false
            if(method is PsiCompiledElement) return fluent(method)
            val body=method.body ?: return false
            val terminal=body.statements.lastOrNull() as? PsiReturnStatement ?: return false
            if(descendants(body,PsiReturnStatement::class.java).filter { PsiTreeUtil.getParentOfType(it,PsiMethod::class.java)==method && generateSequence(it.parent) { p->p.parent }.takeWhile { p->p!=method }.none { p->p is PsiLambdaExpression } }!=listOf(terminal)) return false
            return when(val value=terminal.returnValue?.let(::unwrap)) {
                is PsiThisExpression -> value.qualifier==null
                is PsiMethodCallExpression -> {
                    if(!self(value.methodExpression.qualifierExpression) && value.methodExpression.qualifierExpression !is PsiSuperExpression) return false
                    val target=value.resolveMethod() ?: return false
                    val selected=if(value.methodExpression.qualifierExpression is PsiSuperExpression || target.hasModifierProperty(PsiModifier.PRIVATE) || target.hasModifierProperty(PsiModifier.FINAL)) target else runtime.findMethodBySignature(target,true) ?: return false
                    returnsSelf(selected,active+method)
                }
                else -> false
            }
        }
        return creation.takeIf { calls.all { call ->
            if(!call.resolveMethodGenerics().isValidResult) false else call.resolveMethod()?.let { target ->
                val selected=runtime.findMethodBySignature(target,true) ?: target
                returnsSelf(selected,emptySet())
            }==true
        } }
    }
        fun id(e:PsiExpression?, env:Map<PsiVariable,Id>):Id? = when(val value=e?.let(::unwrap)) {
            is PsiLiteralExpression -> (value.value as? String)?.let { Id(it,value) }
            is PsiReferenceExpression -> env[value.resolve()] ?: (constants.resolve(value) as? String)?.let { Id(it,value) }
            else -> null
        }?.takeIf { (it.value.isNotEmpty() || WicketCompletionAnalysis.allowsEmpty(it.origin)) && it.value.none { ch -> ch==':' || ch=='~' } }
        fun fluent(method:PsiMethod,active:Set<PsiMethod> = emptySet()):Boolean {
            tick(method); if(method in active || active.size>=depthLimit || method.hasModifierProperty(PsiModifier.STATIC)) return false
            if(method is PsiCompiledElement) return method.containingClass?.qualifiedName in setOf("org.apache.wicket.Component","org.apache.wicket.markup.html.form.FormComponent") &&
                method.name in setOf("setVisible","setEnabled","setOutputMarkupId","setDefaultModel")
            source(method)
            val returned=method.body?.statements?.lastOrNull() as? PsiReturnStatement ?: return false
            val returns=descendants(method.body!!,PsiReturnStatement::class.java).filter { PsiTreeUtil.getParentOfType(it,PsiMethod::class.java)==method && generateSequence(it.parent) { p->p.parent }.takeWhile { p->p!=method }.none { p->p is PsiLambdaExpression } }
            return returns==listOf(returned) && returned.returnValue is PsiThisExpression && self(returned.returnValue)
        }
        fun returnedCreation(method:PsiMethod, ids:Map<PsiVariable,Id>,active:Set<PsiMethod> = emptySet()):Created {
            source(method); if(method is PsiCompiledElement || method in active || active.size>=depthLimit) fail(method,"Source factory forwarding is unavailable or recursive.")
            val body=method.body ?: fail(method,"Factory body is unavailable.")
            if(descendants(body,PsiAssignmentExpression::class.java).any { (it.lExpression as? PsiReferenceExpression)?.resolve() in ids }) fail(method,"A forwarded ID parameter is reassigned.")
            descendants(body,PsiMethodCallExpression::class.java).filter { direct(it,method) }.forEach { call ->
                if(call.resolveMethod()?.let { it.name in MUTATIONS && component(it.containingClass?.let { type -> JavaPsiFacade.getElementFactory(page.project).createType(type) }) }==true || call.resolveMethod()?.let { it.name=="add" && it.containingClass?.qualifiedName=="org.apache.wicket.MarkupContainer" }==true &&
                    call.argumentList.expressions.any { component(it.type) }) fail(call,"Factory setup has unreviewed structural mutations.")
            }
            val returned=body.statements.lastOrNull() as? PsiReturnStatement ?: fail(method,"Factory needs one final return.")
            if(descendants(body,PsiReturnStatement::class.java).count { PsiTreeUtil.getParentOfType(it,PsiMethod::class.java)==method && generateSequence(it.parent) { p->p.parent }.takeWhile { p->p!=method }.none { p->p is PsiLambdaExpression } }!=1) fail(method,"Conditional factory returns are unsupported.")
            var expression=returned.returnValue ?: fail(returned,"No component is returned.")
            if(expression is PsiReferenceExpression) {
                val variable=expression.resolve() as? PsiLocalVariable ?: fail(expression,"Factory return is not a fresh local.")
                if(variable.parent.parent!=body || descendants(body,PsiAssignmentExpression::class.java).any { (it.lExpression as? PsiReferenceExpression)?.resolve()==variable }) fail(variable,"Factory local is conditional or reassigned.")
                descendants(body,PsiReferenceExpression::class.java).filter { it.resolve()==variable && it!=expression }.forEach { use ->
                    val member=use.parent as? PsiReferenceExpression
                    val call=member?.parent as? PsiMethodCallExpression
                    val target=call?.resolveMethod()
                    val behaviorOnly=target?.name=="add" && target.containingClass?.qualifiedName in setOf("org.apache.wicket.MarkupContainer","org.apache.wicket.Component") && call.argumentList.expressions.all { argument ->
                        (argument.type as? PsiClassType)?.resolve()?.let { InheritanceUtil.isInheritor(it,"org.apache.wicket.behavior.Behavior") }==true
                    }
                    if(member?.qualifierExpression!=use || target==null || !behaviorOnly && !fluent(target)) fail(use,"The returned component escapes direct receiver configuration.")
                }
                expression=variable.initializer ?: fail(variable,"Factory local is uninitialized.")
            }
            when(val value=unwrap(expression)) {
                is PsiNewExpression -> { source(value); return Created(value,ids) }
                is PsiMethodCallExpression -> {
                    if(body.statements.size!=1) fail(method,"Forwarding factories need one return statement.")
                    val target=value.resolveMethod() ?: fail(value,"Factory target is unresolved.")
                    if(!target.hasModifierProperty(PsiModifier.STATIC) || (value.methodExpression.qualifierExpression as? PsiReferenceExpression)?.resolve() !is PsiClass && value.methodExpression.qualifierExpression!=null) fail(value,"Only static source factory forwarding is supported.")
                    val values:Map<PsiVariable,Id> =target.parameterList.parameters.mapIndexedNotNull { i,p -> id(value.argumentList.expressions.getOrNull(i),ids)?.let { p to it } }.toMap()
                    return returnedCreation(target,values,active+method)
                }
                else -> fail(expression,"A fresh source component is required.")
            }
        }
        fun declared(expression:PsiExpression, ids:Map<PsiVariable,Id>,active:Set<PsiMethod> = emptySet(),depth:Int=0,helperOwner:PsiClass?=null):Declared {
            tick(expression);if(depth>=depthLimit) bound(expression)
            when(val value=unwrap(expression)) {
                is PsiNewExpression -> {
                    val identity=WicketComponentIdentity.trace(value) ?: fail(value,"Child constructor ID is unproved.")
                    sources+=identity.sources
                    val known=id(identity.expression,ids) ?: fail(value,"Child ID is not a known String.")
                    val type=value.anonymousClass?.baseClassType?.resolve() ?: value.classReference?.resolve() as? PsiClass ?: fail(value,"Child type is unavailable.")
                    value.anonymousClass?.let { if(it.initializers.isNotEmpty() || it.fields.any { f->f.initializer!=null }) fail(it,"Anonymous child initialization is unreviewed.") }
                    return Declared(known,type,value)
                }
                is PsiMethodCallExpression -> {
                    val method=value.resolveMethod() ?: fail(value,"Child factory is unresolved.")
                    if(fluent(method)) return declared(value.methodExpression.qualifierExpression ?: fail(value,"A fluent receiver is required."),ids,active,depth+1,helperOwner)
                    val localHelper = helperOwner != null && method.containingClass == helperOwner && method.hasModifierProperty(PsiModifier.PRIVATE) && self(value.methodExpression.qualifierExpression)
                    if(method in active || active.size>=depthLimit || method is PsiCompiledElement || !method.hasModifierProperty(PsiModifier.STATIC) && !localHelper) fail(value,"Child factory forwarding is unreviewed.")
                    if(localHelper) {
                        val arguments: Map<PsiVariable,Id> = method.parameterList.parameters.mapIndexedNotNull { index,parameter -> id(value.argumentList.expressions.getOrNull(index),ids)?.let { parameter to it } }.toMap()
                        val fresh=returnedCreation(method,arguments,active)
                        return declared(fresh.creation,fresh.ids,active+method,depth+1,helperOwner).copy(at=value)
                    }
                    source(method)
                    val result=(method.body?.statements?.singleOrNull() as? PsiReturnStatement)?.returnValue ?: fail(method,"Child factories require one direct source return.")
                    val mapped:Map<PsiVariable,Id> =method.parameterList.parameters.mapIndexedNotNull { i,p -> id(value.argumentList.expressions.getOrNull(i),ids)?.let { p to it } }.toMap()
                    fun alternatives(e:PsiExpression,depth:Int=0):List<Declared> {
                        tick(e); if(depth>=depthLimit) bound(e)
                        return if(e is PsiConditionalExpression) {
                        alternatives(e.thenExpression ?: fail(e,"Missing alternative."),depth+1)+alternatives(e.elseExpression ?: fail(e,"Missing alternative."),depth+1)
                    } else listOf(declared(e,mapped,active+method,depth+1,helperOwner))
                    }
                    val options=alternatives(result)
                    if(options.size>4 || options.map { it.id.value }.distinct().size!=1) fail(value,"Factory alternatives do not preserve one attachment ID.")
                    val types=options.flatMap { it.possibilities.ifEmpty { listOf(it.type.qualifiedName!!) } }.distinct().sorted()
                    val output=(method.returnType as? PsiClassType)?.resolve() ?: fail(method,"The component return type is unavailable.")
                    return Declared(options.first().id,if(types.size==1) options.first().type else output,value,if(types.size>1) types else emptyList())
                }
                else -> fail(expression,"Children need fresh constructors or bounded source factories.")
            }
        }

    fun constructorNodes(
        constructor: PsiMethod,
        root: String,
        key: String,
        nodeLimit: Int,
        runtimeOwner: PsiClass,
        fields: Boolean = false,
        conditional: ((PsiConditionalExpression) -> PsiExpression?)? = null,
        ownedFormConfiguration: Boolean = false,
        recordFormConfiguration: Boolean = false,
        ownedFields: MutableMap<PsiField, String>? = null,
    ): List<ComponentNode> {
        source(constructor)
        val body = constructor.body ?: fail(constructor, "Constructor source is unavailable.")
        val nodes = mutableListOf<ComponentNode>()
        val locals = mutableMapOf<PsiVariable, String>()
        var sequence = 0
        fun add(value: Declared): String {
            if (nodes.size >= nodeLimit) bound(value.at)
            val node = "$key:${++sequence}"
            nodes += ComponentNode(node, value.id.value, value.type.qualifiedName!!, null, location(value.at),
                confidence = Confidence.CONFIRMED, idLocation = value.id.origin?.let(::location),
                idRenameSafe = false, template = true, possibleComponentTypes = value.possibilities)
            return node
        }
        fun own(value: PsiExpression?) = value == null || value is PsiThisExpression &&
            (value.qualifier == null || value.qualifier?.resolve()?.let { owner -> owner == runtimeOwner || runtimeOwner.isInheritor(owner as? PsiClass ?: return@let false, true) } == true)
        fun receiver(expression: PsiExpression?): String? = when (val value = expression?.let(::unwrap)) {
            null -> root
            is PsiThisExpression -> root.takeIf { own(value) }
            is PsiReferenceExpression -> locals[value.resolve()].takeIf { own(value.qualifierExpression) }
            else -> null
        }
        fun create(expression: PsiExpression) = declared(expression, emptyMap(), helperOwner = runtimeOwner.takeIf { fields })
        for (statement in body.statements) {
            tick(statement)
            when (statement) {
                is PsiEmptyStatement -> Unit
                is PsiDeclarationStatement -> statement.declaredElements.forEach { element ->
                    val local = element as? PsiLocalVariable ?: fail(element, "Only component locals are supported.")
                    if (component(local.type)) {
                        val init = local.initializer ?: fail(local, "A component local is uninitialized.")
                        locals[local] = if (init is PsiConditionalExpression) {
                            val branch = conditional?.invoke(init) ?: fail(init, "The component alias condition is unsupported.")
                            receiver(branch) ?: fail(init, "Conditional aliases must select known local components.")
                        } else add(create(init))
                    }
                }
                is PsiExpressionStatement -> {
                    val assignment = statement.expression as? PsiAssignmentExpression
                    if (assignment != null) {
                        val target = (assignment.lExpression as? PsiReferenceExpression)?.resolve() as? PsiVariable
                        if (component(target?.type) || component(assignment.rExpression?.type)) {
                            val field = target as? PsiField ?: fail(assignment, "A component reference is reassigned.")
                            if (!fields || assignment.operationTokenType != JavaTokenType.EQ || !field.hasModifierProperty(PsiModifier.PRIVATE) ||
                                !field.hasModifierProperty(PsiModifier.FINAL) || field.hasModifierProperty(PsiModifier.STATIC) || !own((assignment.lExpression as PsiReferenceExpression).qualifierExpression) ||
                                field.containingClass != constructor.containingClass || field.initializer != null ||
                                descendants(field.containingClass!!, PsiAssignmentExpression::class.java).count { (it.lExpression as? PsiReferenceExpression)?.resolve() == field } != 1)
                                fail(assignment, "Only one direct private-final component initialization is supported.")
                            locals[field] = add(create(assignment.rExpression ?: fail(assignment, "Missing component initializer.")))
                        }
                        continue
                    }
                    val call = statement.expression as? PsiMethodCallExpression ?: fail(statement, "Unsupported constructor effect.")
                    val method = call.resolveMethod() ?: fail(call, "A constructor call is unresolved.")
                    if (call.methodExpression.referenceName in setOf("super", "this")) continue
                    if (recordFormConfiguration) {
                        val node = receiver(call.methodExpression.qualifierExpression)
                        val index = nodes.indexOfFirst { it.nodeId == node }
                        if (index >= 0) formConfiguration(call, method)?.let { configuration ->
                            nodes[index] = nodes[index].copy(formConfiguration = nodes[index].formConfiguration + configuration)
                        }
                    }
                    if (method.name in MUTATIONS && component(method.containingClass?.let { JavaPsiFacade.getElementFactory(page.project).createType(it) }))
                        fail(call, "Structural constructor mutations are unsupported.")
                    if (method.name == "add" && method.containingClass?.qualifiedName == "org.apache.wicket.MarkupContainer") {
                        val parent = receiver(call.methodExpression.qualifierExpression) ?: fail(call, "A child uses an unknown parent.")
                        call.argumentList.expressions.forEach { argument ->
                            if (component(argument.type)) {
                                val reference = argument as? PsiReferenceExpression
                                val child = reference?.let { reference -> locals[reference.resolve()].takeIf { own(reference.qualifierExpression) } } ?: add(create(argument))
                                val index = nodes.indexOfFirst { it.nodeId == child }
                                if (nodes[index].parentNodeId != null) fail(argument, "A component has more than one attachment.")
                                nodes[index] = nodes[index].copy(parentNodeId = parent)
                            } else if ((argument.type as? PsiClassType)?.resolve()?.let { type ->
                                    InheritanceUtil.isInheritor(type, "org.apache.wicket.behavior.Behavior") || fields &&
                                        (type.qualifiedName == "org.apache.wicket.validation.IValidator" || InheritanceUtil.isInheritor(type, "org.apache.wicket.validation.IValidator"))
                                } != true) fail(argument, "An add argument is neither a component nor a reviewed behavior/validator declaration.")
                        }
                    } else {
                        if (ownedFormConfiguration && method.containingClass?.qualifiedName == "org.apache.wicket.markup.html.form.Form" && method.name == "setDefaultButton" &&
                            call.argumentList.expressions.singleOrNull()?.let { receiver(it) }?.let { child -> nodes.singleOrNull { it.nodeId == child }?.parentNodeId == receiver(call.methodExpression.qualifierExpression) } == true) continue
                        if (fields && call.argumentList.expressions.any { component(it.type) }) fail(call, "A component escapes a direct form addition.")
                        if (receiver(call.methodExpression.qualifierExpression) == null) fail(call, "Constructor configuration has a foreign receiver.")
                        if (call.argumentList.expressions.any { argument -> descendants(argument, PsiReferenceExpression::class.java).any { use ->
                                if (use.resolve() !in locals) false else if (!ownedFormConfiguration) true else {
                                    val callback = PsiTreeUtil.getParentOfType(use, PsiMethod::class.java)
                                    val deferred = callback != null && callback != constructor && callback.findDeepestSuperMethods().any { it.containingClass?.qualifiedName?.startsWith("org.apache.wicket.") == true } ||
                                        generateSequence(use.parent) { it.parent }.takeWhile { it != argument }.any { it is PsiLambdaExpression }
                                    val reference = use.parent as? PsiMethodReferenceExpression
                                    val markupId = reference?.qualifierExpression == use && (reference.resolve() as? PsiMethod)?.let { it.name == "getMarkupId" && it.parameterList.parametersCount == 0 && it.containingClass?.qualifiedName == "org.apache.wicket.Component" } == true
                                    !deferred && !markupId
                                }
                            } })
                            fail(call, "A component escapes through configuration.")
                    }
                }
                else -> fail(statement, "Constructor control flow is outside the declaration contract.")
            }
        }
        if (nodes.any { it.parentNodeId == null }) fail(constructor, "A declared component is unattached.")
        val byId = nodes.associateBy { it.nodeId }
        nodes.forEach { node ->
            val path = generateSequence(node) { byId[it.parentNodeId] }.take(nodes.size + 1).toList()
            if (path.last().parentNodeId != root || nodes.count { it.parentNodeId == node.parentNodeId && it.wicketId == node.wicketId } > 1)
                fail(constructor, "The declared component path is cyclic or ambiguous.")
        }
        ownedFields?.let { fieldsByNode ->
            locals.forEach { (variable, node) -> if (variable is PsiField) fieldsByNode[variable] = node }
        }
        return nodes
    }

    private fun formConfiguration(call: PsiMethodCallExpression, method: PsiMethod): FormFieldConfiguration? {
        if (!call.resolveMethodGenerics().isValidResult || method.containingClass?.qualifiedName != WicketComponentTypes.FORM_COMPONENT) return null
        val argument = call.argumentList.expressions.singleOrNull() ?: return null
        val at = location(call.methodExpression.referenceNameElement ?: call)
        if (method.name == "setRequired") {
            val value = (unwrap(argument) as? PsiLiteralExpression)?.value as? Boolean
            return FormFieldConfiguration("REQUIRED", at, required = value)
        }
        if (method.name != "add" || method.parameterList.parameters.singleOrNull()?.type
                ?.canonicalText?.substringBefore('<') != "org.apache.wicket.validation.IValidator") return null
        val allocation = unwrap(argument) as? PsiNewExpression
        val factory = unwrap(argument) as? PsiMethodCallExpression
        val type = "org.apache.wicket.validation.validator.RangeValidator"
        val limits = when {
            allocation != null && allocation.anonymousClass == null && allocation.resolveMethodGenerics().isValidResult &&
                allocation.resolveConstructor()?.containingClass?.qualifiedName == type -> allocation.argumentList?.expressions
            factory != null && factory.resolveMethodGenerics().isValidResult && factory.resolveMethod()?.let {
                it.name == "range" && it.hasModifierProperty(PsiModifier.STATIC) && it.containingClass?.qualifiedName == type
            } == true -> factory.argumentList.expressions
            else -> null
        }?.map { (constants.expression(it) as? Number)?.takeIf { value ->
            value is Byte || value is Short || value is Int || value is Long
        }?.toLong() }
        val (minimum, maximum) = if (limits?.size == 2) limits else listOf(null, null)
        return if (minimum != null && maximum != null && minimum <= maximum)
            FormFieldConfiguration("RANGE_VALIDATOR", at, minimum = minimum, maximum = maximum)
        else FormFieldConfiguration("VALIDATOR", at)
    }
}
private fun location(element:PsiElement)=SourceLocation(element.containingFile.virtualFile.path,element.textOffset)
private val MUTATIONS=setOf("remove","removeAll","replace","replaceWith","addOrReplace","queue")
