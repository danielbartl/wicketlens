package dev.wicketlens.analysis

import com.intellij.openapi.progress.ProgressManager
import com.intellij.psi.*
import com.intellij.psi.util.InheritanceUtil
import com.intellij.psi.util.PsiTreeUtil
import dev.wicketlens.analysis.WicketSourceDeclarations.Id
import dev.wicketlens.model.*

/** Bounded ListView source declarations. Predicates, models, configuration and callbacks are never executed. */
internal class WicketPopulationAnalyzer(private val limits: Limits = Limits()) {
    data class Limits(val steps: Int = 20000, val sources: Int = 96, val contexts: Int = 4, val nodes: Int = 128, val depth: Int = 12, val repeaters: Int = 3)
    private class Unknown(val at: PsiElement, override val message: String) : RuntimeException(null, null, false, false)
    private class Bound(val at: PsiElement) : RuntimeException(null, null, false, false)
    private class Branch(val at: PsiElement) : RuntimeException(null, null, false, false)
    private sealed interface Value
    private data class Ref(val id: String) : Value
    private data class Data(val row: String? = null, val variable: PsiVariable? = null) : Value
    private data class Identifier(val id: Id) : Value
    private data class Frame(val method: PsiMethod, val receiver: Ref, val variables: MutableMap<PsiVariable, Value>)

    fun analyze(page: PsiClass, primary: PageReport): PageReport = Discovery(page, primary).run()

    private inner class Discovery(val page: PsiClass, val primary: PageReport) {
        val sources = linkedSetOf<String>()
        val contexts = mutableListOf<PageReport>()
        val issues = mutableListOf<Diagnostic>()
        val cache = mutableMapOf<PsiElement, List<PsiElement>>()
        val visited = mutableSetOf<PsiElement>()
        val owners = generateSequence(page) { it.superClass }.take(limits.depth).takeWhile { it !is PsiCompiledElement }.toList()
        var steps = 0
        var complete = true
        fun tick(at: PsiElement) { ProgressManager.checkCanceled(); if (++steps > limits.steps || sources.size > limits.sources) throw Bound(at) }
        fun fail(at: PsiElement, message: String): Nothing = throw Unknown(at, message)
        fun source(at: PsiElement) {
            tick(at)
            sources += at.containingFile?.virtualFile?.path?.substringBefore("!/") ?: fail(at, "Physical source is unavailable.")
            if (sources.size > limits.sources) throw Bound(at)
            if (at !is PsiCompiledElement && PsiTreeUtil.findChildOfType(at.containingFile, PsiErrorElement::class.java) != null) fail(at, "Java syntax is incomplete.")
        }
        fun elements(at: PsiElement): List<PsiElement> = cache.getOrPut(at) {
            val ancestor = generateSequence(at.parent) { it.parent }.firstOrNull { it in cache }
            if (ancestor != null) return@getOrPut cache.getValue(ancestor).filter { PsiTreeUtil.isAncestor(at, it, false) }
            val result = mutableListOf<PsiElement>()
            at.accept(object : JavaRecursiveElementWalkingVisitor() {
                override fun visitElement(element: PsiElement) { if (visited.add(element)) tick(element); result += element; super.visitElement(element) }
            })
            result
        }
        val declarations = WicketSourceDeclarations(page, limits.depth, ::tick, ::source, sources, ::fail, { throw Bound(it) }, ::elements)
        fun component(type: PsiType?) = declarations.component(type)
        fun method(call: PsiMethodCallExpression): PsiMethod {
            if (!call.resolveMethodGenerics().isValidResult) fail(call, "The source call is unresolved or incompatible.")
            val declared = call.resolveMethod() ?: fail(call, "The source call is unavailable.")
            return if (declared.hasModifierProperty(PsiModifier.PRIVATE) || declared.hasModifierProperty(PsiModifier.STATIC)) declared
                else owners.firstNotNullOfOrNull { it.findMethodBySignature(declared, false) } ?: declared
        }
        fun creation(node: ComponentNode): PsiNewExpression? {
            val file = WicketSourceFiles.find(page.project, node.javaLocation.file)?.let { PsiManager.getInstance(page.project).findFile(it) } ?: return null
            return generateSequence(file.findElementAt(node.javaLocation.offset)) { it.parent }.filterIsInstance<PsiNewExpression>().firstOrNull { it.textOffset == node.javaLocation.offset }
        }
        fun callback(creation: PsiNewExpression): PsiMethod? {
            val type = creation.anonymousClass ?: return null
            if(!WicketRepeaterContract.accepts(type, Repetition.LIST_VIEW)) return null
            return type.methods.singleOrNull { WicketRepeaterContract.callback(it, Repetition.LIST_VIEW) }
        }
        fun registeredCallback(host: ComponentNode): PsiLambdaExpression? {
            val at = host.registeredListPopulation?.callbackLocation ?: return null
            val file = WicketSourceFiles.find(page.project, at.file)?.let { PsiManager.getInstance(page.project).findFile(it) } ?: return null
            return generateSequence(file.findElementAt(at.offset)) { it.parent }.filterIsInstance<PsiLambdaExpression>().firstOrNull { it.textOffset == at.offset }
        }
        fun registrationCall(host: ComponentNode): PsiMethodCallExpression? {
            val at = host.registeredListPopulation?.registrationLocation ?: return null
            val file = WicketSourceFiles.find(page.project, at.file)?.let { PsiManager.getInstance(page.project).findFile(it) } ?: return null
            return generateSequence(file.findElementAt(at.offset)) { it.parent }.filterIsInstance<PsiMethodCallExpression>().firstOrNull { it.textOffset == at.offset }
        }

        fun run(): PageReport {
            if (primary.populationTemplate != null || primary.markup == null) return primary
            val matched = primary.matching?.findings.orEmpty().filter { it.code == DiagnosticCode.MATCHED }.mapNotNull { it.javaNodeId }.toSet()
            try {
                val populated = primary.nodes.filter { it.templateItem }.map { it.parentNodeId }.toSet()
                for (host in primary.nodes.filter { it.repetition == Repetition.LIST_VIEW && it.nodeId in matched && it.nodeId !in populated }) {
                    tick(page)
                    val allocation: PsiExpression = if (host.registeredListPopulation != null) registrationCall(host) ?: continue else creation(host) ?: continue
                    val population: PsiElement = if (host.registeredListPopulation != null) registeredCallback(host) ?: continue else callback(allocation as PsiNewExpression) ?: continue
                    fun explore(choices: List<Boolean>) {
                        tick(population)
                        try {
                            val context = Population(host, allocation, population, choices).build()
                            if (contexts.size >= limits.contexts) throw Bound(population)
                            contexts += context
                        } catch (branch: Branch) {
                            if (choices.size >= 2) throw Bound(branch.at)
                            explore(choices + false); explore(choices + true)
                        }
                    }
                    val start = contexts.size
                    try { explore(emptyList()) }
                    catch (unknown: Unknown) {
                        while (contexts.size > start) contexts.removeLast()
                        issues += Diagnostic(DiagnosticCode.UNRESOLVED_SCOPE, "Population templates withheld: ${unknown.message}", location(unknown.at))
                    }
                }
            } catch (bound: Bound) {
                contexts.clear(); complete = false
                issues += Diagnostic(DiagnosticCode.PARTIAL_ANALYSIS, "Population discovery exceeded its ${limits.steps}-step/${limits.sources}-source/${limits.contexts}-context/${limits.nodes}-node/${limits.depth}-level/${limits.repeaters}-repeater bound; no population template was published.", location(bound.at))
            }
            if (contexts.isEmpty()) return primary.copy(populationContexts = emptyList(), populationDiscoveryComplete = complete, javaSourceFiles = (primary.javaSourceFiles + sources).distinct().sorted(), warnings = primary.warnings + issues)
            val coverage = contexts.flatMap { context -> context.matching!!.findings.filter { it.code == DiagnosticCode.MATCHED && it.javaNodeId in context.populationTemplate!!.ownedNodeIds }.mapNotNull { it.markupLocation } }.toSet()
            val findings = primary.matching!!.findings.map { finding ->
                if (finding.javaNodeId == null && finding.markupLocation in coverage && finding.code in setOf(DiagnosticCode.UNRESOLVED_PARENT, DiagnosticCode.UNRESOLVED_MARKUP))
                    finding.copy(code = DiagnosticCode.UNRESOLVED_BRANCH, message = "A source population template supplies a conditional counterpart. Select its item context; runtime rows and branch state remain unknown.")
                else finding
            }
            return primary.copy(populationContexts = contexts.toList(), populationDiscoveryComplete = complete, matching = primary.matching.copy(findings = findings),
                javaSourceFiles = (primary.javaSourceFiles + sources).distinct().sorted(), warnings = primary.warnings + issues)
        }

        private inner class Population(val host: ComponentNode, val allocation: PsiExpression, val population: PsiElement, val choices: List<Boolean>) {
            val key = "population:${primary.qualifiedClassName}:${host.nodeId}:${choices.joinToString("") { if (it) "T" else "F" }}"
            val nodes = linkedMapOf<String, ComponentNode>()
            val hidden = mutableSetOf<String>()
            val placeholders = mutableSetOf<String>()
            val separateMarkup = mutableSetOf<String>()
            val conditions = mutableListOf<SourcePredicateCondition>()
            val callbacks = mutableListOf<SourceLocation>()
            val configuration = linkedSetOf<SourceLocation>()
            val active = mutableSetOf<PsiMethod>()
            var branchIndex = 0
            var depth = 0
            var sequence = 0
            var configurationDepth = 0
            var statementDepth = 0
            lateinit var item: Ref
            fun frameIds(frame: Frame) = frame.variables.mapNotNull { (variable, value) -> (value as? Identifier)?.let { variable to it.id } }.toMap()
            fun own(ref: Ref, at: PsiElement) { if (ref.id !in nodes) fail(at, "Population setup reaches a captured component outside its own items/fresh allocations.") }
            fun fresh(node: ComponentNode): Ref {
                if (nodes.size + primary.nodes.size >= limits.nodes) throw Bound(population)
                nodes[node.nodeId] = node
                return Ref(node.nodeId)
            }
            fun attach(parent: Ref, child: Ref, at: PsiElement) {
                own(parent, at); own(child, at)
                val node = nodes.getValue(child.id)
                if (node.parentNodeId != null) fail(at, "A component is reused or attached more than once.")
                nodes[child.id] = node.copy(parentNodeId = parent.id)
            }
            fun data(expression: PsiExpression, frame: Frame): Data {
                val e = declarations.unwrap(expression)
                if (e is PsiReferenceExpression) (frame.variables[e.resolve()] as? Data)?.let { return it }
                if (e is PsiMethodCallExpression && e.argumentList.expressions.isEmpty() && e.resolveMethod()?.let {
                        it.name == "getModelObject" && it.containingClass?.qualifiedName in setOf("org.apache.wicket.IGenericComponent", "org.apache.wicket.markup.html.list.ListItem")
                    } == true) {
                    val receiver = value(e.methodExpression.qualifierExpression ?: fail(e, "The model receiver is unavailable."), frame) as? Ref
                    if (receiver != null && nodes[receiver.id]?.templateItem == true) return Data(receiver.id)
                }
                // Deferred data is opaque; passing a tracked component into it is not a declaration.
                elements(e).filterIsInstance<PsiReferenceExpression>().forEach { use ->
                    if (frame.variables[use.resolve()] is Ref) fail(use, "A population component escapes into opaque data.")
                }
                return Data()
            }
            fun value(expression: PsiExpression, frame: Frame): Value {
                tick(expression)
                val e = declarations.unwrap(expression)
                if (e is PsiReferenceExpression) {
                    frame.variables[e.resolve()]?.let { return it }
                    if (component(e.type)) fail(e, "Borrowed component fields/aliases are unsupported.")
                }
                declarations.id(e, frameIds(frame))?.let { return Identifier(it) }
                if (e is PsiNewExpression && component(e.type)) {
                    val declared = declarations.declared(e, frameIds(frame))
                    val id = "$key:node:${++sequence}"
                    val ref = fresh(ComponentNode(id, declared.id.value, declared.type.qualifiedName!!, null, location(e),
                        confidence = Confidence.CONFIRMED, idLocation = declared.id.origin?.let(::location), idRenameSafe = false, template = true,
                        repetition = if (InheritanceUtil.isInheritor(declared.type, LIST_VIEW) || declared.type.qualifiedName == LIST_VIEW) Repetition.LIST_VIEW else null))
                    if (e.anonymousClass == null && declared.type.qualifiedName == "org.apache.wicket.markup.html.WebMarkupContainer") placeholders += id
                    if (WicketComponentTypes.ownsMarkup(declared.type)) separateMarkup += id
                    if (nodes.getValue(id).repetition == Repetition.LIST_VIEW) populate(e, ref)
                    return ref
                }
                if (e is PsiMethodCallExpression && component(e.type)) {
                    val target = e.resolveMethod() ?: fail(e, "A component call is unresolved.")
                    val behaviorDecoration = target.name == "add" && e.methodExpression.qualifierExpression?.let { declarations.unwrap(it) } is PsiNewExpression &&
                        (target.parameterList.parameters.singleOrNull()?.type as? PsiEllipsisType)?.componentType?.canonicalText == "org.apache.wicket.behavior.Behavior" &&
                        e.argumentList.expressions.all { argument -> (argument.type as? PsiClassType)?.resolve()?.let { InheritanceUtil.isInheritor(it,"org.apache.wicket.behavior.Behavior") } == true }
                    if (target is PsiCompiledElement && target.containingClass?.qualifiedName == COMPONENT && (target.name in CONFIG || behaviorDecoration)) {
                        val receiver = value(e.methodExpression.qualifierExpression ?: fail(e, "A configuration receiver is required."), frame) as? Ref ?: fail(e, "The receiver is not a component.")
                        configure(e, receiver, frame)
                        return receiver
                    }
                    return invoke(e, frame) as? Ref ?: fail(e, "The source factory does not return a fresh component.")
                }
                return data(e, frame)
            }
            fun configurationOnly(statement: PsiStatement): Boolean {
                tick(statement)
                if(++configurationDepth > limits.depth) throw Bound(statement)
                try { return when (statement) {
                    is PsiBlockStatement -> statement.codeBlock.statements.all(::configurationOnly)
                    is PsiIfStatement -> statement.thenBranch?.let(::configurationOnly) == true && (statement.elseBranch?.let(::configurationOnly) != false)
                    is PsiExpressionStatement -> (statement.expression as? PsiMethodCallExpression)?.let { call ->
                        val target = call.resolveMethod()
                        target is PsiCompiledElement && target.containingClass?.qualifiedName == COMPONENT &&
                            (target.name in CONFIG || target.name == "add" && call.argumentList.expressions.all { argument ->
                                (argument.type as? PsiClassType)?.resolve()?.let { InheritanceUtil.isInheritor(it, "org.apache.wicket.behavior.Behavior") } == true
                            })
                    } == true
                    is PsiEmptyStatement -> true
                    else -> false
                } } finally { configurationDepth-- }
            }
            fun configure(call: PsiMethodCallExpression, receiver: Ref, frame: Frame, conditional: Boolean = false) {
                own(receiver, call)
                call.argumentList.expressions.forEach { data(it, frame) }
                configuration += location(call)
                if (call.resolveMethod()?.name == "setVisible") {
                    hidden.remove(receiver.id)
                    if (!conditional && (call.argumentList.expressions.singleOrNull() as? PsiLiteralExpression)?.value == false) hidden += receiver.id
                }
            }
            fun configStatement(statement: PsiStatement, frame: Frame) {
                when (statement) {
                    is PsiBlockStatement -> statement.codeBlock.statements.forEach { configStatement(it, frame) }
                    is PsiIfStatement -> { configuration += location(statement); statement.thenBranch?.let { configStatement(it, frame) }; statement.elseBranch?.let { configStatement(it, frame) } }
                    is PsiExpressionStatement -> {
                        val call = statement.expression as PsiMethodCallExpression
                        val receiver = value(call.methodExpression.qualifierExpression ?: fail(call, "Captured implicit configuration is unsupported."), frame) as? Ref ?: fail(call, "Unknown configuration receiver.")
                        configure(call, receiver, frame, conditional = true)
                    }
                }
            }
            fun statements(setup: Array<PsiStatement>, frame: Frame): Value? {
                if(++statementDepth > limits.depth) throw Bound(frame.method)
                try { for (statement in setup) {
                    tick(statement)
                    when (statement) {
                        is PsiEmptyStatement -> Unit
                        is PsiBlockStatement -> statements(statement.codeBlock.statements, frame)?.let { return it }
                        is PsiDeclarationStatement -> {
                            val variable = statement.declaredElements.singleOrNull() as? PsiLocalVariable ?: fail(statement, "One direct local declaration is required.")
                            val expression = variable.initializer ?: fail(variable, "Uninitialized population locals are unsupported.")
                            val result = value(expression, frame)
                            frame.variables[variable] = if (result is Data) result.copy(variable = variable) else result
                        }
                        is PsiReturnStatement -> return statement.returnValue?.let { value(it, frame) } ?: Data()
                        is PsiExpressionStatement -> {
                            val call = statement.expression as? PsiMethodCallExpression ?: fail(statement, "Population assignment or update is unsupported.")
                            val target = call.resolveMethod() ?: fail(call, "The population call is unavailable.")
                            if (target.name == "add" && target.containingClass?.qualifiedName == "org.apache.wicket.MarkupContainer") {
                                val parent = value(call.methodExpression.qualifierExpression ?: fail(call, "A direct item/fresh parent is required."), frame) as? Ref ?: fail(call, "Unknown addition parent.")
                                call.argumentList.expressions.forEach { argument ->
                                    if (!component(argument.type)) fail(argument, "A component addition needs direct fresh components/locals.")
                                    attach(parent, value(argument, frame) as? Ref ?: fail(argument, "Unknown child identity."), argument)
                                }
                            } else if (target is PsiCompiledElement && target.containingClass?.qualifiedName == COMPONENT && (target.name in CONFIG || target.name == "add")) {
                                val parent = value(call.methodExpression.qualifierExpression ?: fail(call, "Implicit component configuration is unsupported."), frame) as? Ref ?: fail(call, "Unknown configuration receiver.")
                                configure(call, parent, frame)
                            } else invoke(call, frame)
                        }
                        is PsiIfStatement -> {
                            if (statement.thenBranch?.let(::configurationOnly) == true && statement.elseBranch?.let(::configurationOnly) != false) {
                                configStatement(statement, frame); continue
                            }
                            val predicate = statement.condition as? PsiMethodCallExpression ?: fail(statement, "A structural branch needs one direct data predicate.")
                            val receiver = (predicate.methodExpression.qualifierExpression as? PsiReferenceExpression)?.let { frame.variables[it.resolve()] as? Data }
                                ?: fail(predicate, "The predicate must be bound to the current item model local.")
                            val target = predicate.resolveMethod() ?: fail(predicate, "The predicate source is unavailable.")
                            if (receiver.row == null || receiver.variable == null || target.hasModifierProperty(PsiModifier.STATIC) || target.returnType != PsiTypes.booleanType() || predicate.argumentList.expressions.isNotEmpty())
                                fail(predicate, "Only a zero-argument boolean item-model predicate is supported.")
                            source(target)
                            val choice = choices.getOrNull(branchIndex++) ?: throw Branch(predicate)
                            conditions += SourcePredicateCondition(predicate.text, location(predicate), location(target), (predicate.methodExpression.qualifierExpression as PsiReferenceExpression).referenceName!!, location((predicate.methodExpression.qualifierExpression as PsiReferenceExpression).resolve()!!), receiver.row, choice)
                            (if (choice) statement.thenBranch else statement.elseBranch)?.let { branch ->
                                val selected = if (branch is PsiBlockStatement) branch.codeBlock.statements else arrayOf(branch)
                                statements(selected, frame)?.let { return it }
                            }
                        }
                        else -> fail(statement, "Population loops, exception flow and unsupported setup are not reconstructed.")
                    }
                }
                return null
                } finally { statementDepth-- }
            }
            fun invoke(call: PsiMethodCallExpression, caller: Frame): Value? {
                val target = method(call)
                val qualifier = call.methodExpression.qualifierExpression
                if (target is PsiCompiledElement || target.containingClass !in owners || target.hasModifierProperty(PsiModifier.STATIC) ||
                    qualifier != null && (qualifier !is PsiThisExpression || qualifier.qualifier?.resolve()?.let { it !in owners } == true) ||
                    !target.hasModifierProperty(PsiModifier.PRIVATE) && !component(target.returnType)) fail(call, "Only source private setup helpers and source component factories on the selected owner are supported.")
                source(target)
                if (active.size >= limits.depth) throw Bound(call)
                if (!active.add(target)) fail(call, "Recursive population helpers are unsupported.")
                try {
                    val parameters = target.parameterList.parameters
                    if (target.isVarArgs || parameters.size != call.argumentList.expressions.size) fail(call, "Helper arguments are not direct fixed-arity source arguments.")
                    val arguments = call.argumentList.expressions.map { value(it, caller) }
                    val before = nodes.keys.toSet()
                    val result = statements(target.body?.statements ?: fail(target, "Helper source body is unavailable."), Frame(target, Ref(primary.rootNodeId), parameters.zip(arguments).toMap().toMutableMap()))
                    if (component(target.returnType)) {
                        val returned = result as? Ref ?: fail(target, "A component factory needs one fresh result.")
                        if (returned.id in before || returned.id !in nodes || nodes.getValue(returned.id).parentNodeId != null) fail(call, "A source factory returns a borrowed or attached component.")
                        if (parameters.firstOrNull()?.type?.canonicalText == "java.lang.String") {
                            val supplied = arguments.firstOrNull() as? Identifier ?: fail(call, "The factory ID is unknown.")
                            val node = nodes.getValue(returned.id)
                            if (node.wicketId != supplied.id.value || node.idLocation != supplied.id.origin?.let(::location)) fail(call, "The source factory must forward its supplied ID, not an equal literal.")
                        }
                    }
                    return result
                } finally { active.remove(target) }
            }
            fun populate(creation: PsiNewExpression, view: Ref): Ref {
                if (++depth > limits.repeaters) throw Bound(creation)
                try {
                    val method = callback(creation) ?: fail(creation, "A reviewed ListView with one source populateItem override is required.")
                    source(method);callbacks += location(method)
                    val customItem = method.containingClass?.methods?.firstNotNullOfOrNull(WicketRepeaterContract::customItem)
                    customItem?.let(::source)
                    val root = fresh(ComponentNode("$key:item:${++sequence}", null, customItem?.qualifiedName ?: WicketRepeaterContract.ITEM, view.id,
                        location(method.parameterList.parameters.single()), confidence = Confidence.CONFIRMED, idRenameSafe = false, template = true, templateItem = true))
                    statements(method.body?.statements ?: fail(method, "Population source is unavailable."), Frame(method, view, mutableMapOf(method.parameterList.parameters.single() to root)))
                    return root
                } finally { depth-- }
            }
            fun populateRegistered(lambda: PsiLambdaExpression, view: Ref): Ref {
                source(lambda)
                val registration = host.registeredListPopulation ?: fail(lambda, "The registered source bridge is unavailable.")
                callbacks += registration.bridgeLocation
                callbacks += registration.itemFactoryLocations
                callbacks += location(lambda)
                val parameters = lambda.parameterList.parameters
                if (parameters.size != 2) fail(lambda, "The registered item/data callback arity is unresolved.")
                val root = fresh(ComponentNode("$key:item:${++sequence}", null, "org.apache.wicket.markup.html.list.ListItem", view.id,
                    location(parameters[0]), confidence=Confidence.CONFIRMED, idRenameSafe=false, template=true, templateItem=true))
                val method = PsiTreeUtil.getParentOfType(lambda, PsiMethod::class.java) ?: fail(lambda, "The source callback owner is unavailable.")
                val body = lambda.body as? PsiCodeBlock ?: fail(lambda, "A registered population source block is required.")
                val variables = mutableMapOf<PsiVariable, Value>(parameters[0] to root, parameters[1] to Data(root.id, parameters[1]))
                if (statements(body.statements, Frame(method, Ref(primary.rootNodeId), variables)) != null) fail(lambda, "Registered item callbacks may not return or expose a value.")
                return root
            }
            fun hostPath(): String? {
                val byId = primary.nodes.associateBy { it.nodeId }
                val path = mutableListOf<String>();val seen = mutableSetOf<String>()
                var current: ComponentNode? = host
                while(current?.nodeId != primary.rootNodeId) {
                    val part = current ?: return null
                    if(!seen.add(part.nodeId) || part.wicketId.isNullOrEmpty()) return null
                    path += part.wicketId!!;current=byId[part.parentNodeId]
                }
                return path.asReversed().joinToString(":")
            }
            fun build(): PageReport {
                source(allocation)
                item = if (population is PsiLambdaExpression) populateRegistered(population, Ref(host.nodeId)) else populate(allocation as PsiNewExpression, Ref(host.nodeId))
                if (branchIndex != choices.size) fail(population, "Branch choices do not describe one source path.")
                if (nodes.values.any { it.parentNodeId == null }) fail(population, "A fresh declared component is unattached.")
                val all = primary.nodes + nodes.values
                val byId = all.associateBy { it.nodeId }
                nodes.values.forEach { node ->
                    val chain = generateSequence(node) { byId[it.parentNodeId] }.take(all.size + 1).toList()
                    if (chain.last().nodeId != primary.rootNodeId || all.count { it.parentNodeId == node.parentNodeId && it.wicketId == node.wicketId } > 1)
                        fail(population, "A population path is cyclic or ambiguous.")
                }
                val raw = primary.markup!!
                val java = primary.copy(nodes = all.map { it.copy(template = true, idRenameSafe = false) }, matching = null, populationContexts = emptyList(), initialContentSetup = null)
                val first = WicketGraphMatcher().match(java)
                val inactive = (hidden.filter { it in placeholders && nodes.values.none { child -> child.parentNodeId == it } } + separateMarkup).mapNotNull { id -> first.nodes.single { it.nodeId == id }.markupLocation }.toSet()
                val suppressed = mutableSetOf<String>()
                val markupById = raw.elements.associateBy { it.nodeId }
                val elements = raw.elements.map { element ->
                    tick(population)
                    if (element.parentElementId in suppressed || markupById[element.parentElementId]?.location in inactive) {
                        suppressed += element.nodeId; element.copy(participatesInComponentTree = false, diagnostics = emptyList())
                    } else element
                }
                val hostPath = hostPath() ?: fail(population, "The source host path is unavailable.")
                val metadata = RepeaterPopulationTemplate(key, host.nodeId, hostPath, item.id, nodes.keys.toList(), location(allocation), location(population), conditions.toList(), callbacks.distinct(), configuration.toList(), inactive.toList(), host.registeredListPopulation)
                val warning = Diagnostic(DiagnosticCode.UNRESOLVED_SCOPE, "Source-declared item population under explicit row preconditions only. Runtime item IDs/count, model values, predicate results, authorization, visibility, configuration effects and later callbacks remain unknown.", location(population))
                val matched = WicketGraphMatcher().match(java.copy(populationTemplate = metadata, scopeComplete = false,
                    warnings = primary.warnings + warning, javaSourceFiles = (primary.javaSourceFiles + sources).distinct().sorted(),
                    markup = raw.copy(elements = elements, scopeComplete = false, diagnostics = (raw.documentDiagnostics + elements.flatMap { it.diagnostics }).distinct())))
                // Keep the exact item findings and their ancestors; unrelated page bindings are already in the primary report.
                val retainedNodes = nodes.keys.toMutableSet()
                for(node in nodes.values) {
                    var parent = node.parentNodeId
                    while(parent != null && retainedNodes.add(parent)) { tick(population); parent = byId[parent]?.parentNodeId }
                }
                val hostMarkup = primary.matching!!.findings.single { it.javaNodeId == host.nodeId }.markupNodeId!!
                val rowMarkup = elements.filter { e -> e.participatesInComponentTree && e.hasWicketId &&
                    generateSequence(markupById[e.parentElementId]) { markupById[it.parentElementId] }.any { tick(population); it.nodeId == hostMarkup } }.map { it.nodeId }.toSet()
                val retainedMarkup = rowMarkup.toMutableSet()
                for(id in rowMarkup) {
                    var parent = markupById[id]?.parentElementId
                    while(parent != null && retainedMarkup.add(parent)) { tick(population);parent=markupById[parent]?.parentElementId }
                }
                return matched.copy(nodes=matched.nodes.filter { it.nodeId in retainedNodes }, matching=matched.matching!!.copy(findings=matched.matching.findings.filter { it.javaNodeId in nodes || it.javaNodeId==null && it.markupNodeId in rowMarkup }),
                    markup=matched.markup!!.copy(elements=matched.markup.elements.filter { it.nodeId in retainedMarkup }))
            }
        }
    }
    companion object {
        private const val LIST_VIEW = "org.apache.wicket.markup.html.list.ListView"
        private const val COMPONENT = "org.apache.wicket.Component"
        private val CONFIG = setOf("setVisible", "setEnabled", "setOutputMarkupId", "setEscapeModelStrings", "setRenderBodyOnly", "setDefaultModel")
    }
}
private fun location(at: PsiElement) = SourceLocation(at.containingFile.virtualFile.path, at.textOffset)
