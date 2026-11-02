package dev.wicketlens.analysis

import com.intellij.openapi.progress.ProgressManager
import com.intellij.psi.*
import com.intellij.psi.util.InheritanceUtil
import com.intellij.psi.util.MethodSignatureUtil
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.TypeConversionUtil
import dev.wicketlens.model.*

/**
 * A bounded interpreter for closed source setup and selected or agreeing boolean paths. It publishes an
 * upgrade only after validating the entire reachable setup; otherwise the existing
 * conservative analyzer keeps its observations and uncertainty diagnostics.
 * All PSI identities and environments are invocation-local.
 */
internal class WicketSourceSetupAnalyzer {
    data class Expansion(val nodes: List<ComponentNode>, val sources: List<String>, val warnings: List<Diagnostic> = emptyList(),
        val branchPathCount: Int = 1)

    fun analyze(page: PsiClass, rootId: String): Expansion? = try {
        val budget = Budget()
        fun explore(choices: List<Boolean>): List<Expansion> = try {
            listOf(Session(page, rootId, choices, budget).run() ?: unsupported())
        } catch (_: BranchRequired) {
            // At most eight complete paths. Replay uses the same interpreter and one
            // shared step budget; no branch gets a weaker validation contract.
            if (choices.size >= 3) unsupported()
            explore(choices + true) + explore(choices + false)
        }
        val paths = explore(emptyList())
        if (paths.size == 1) paths.single() else merge(paths, rootId, budget)
    } catch (_: UnsupportedSetup) { null }

    private class UnsupportedSetup : RuntimeException(null, null, false, false)
    private class BranchRequired : RuntimeException(null, null, false, false)
    private fun unsupported(): Nothing = throw UnsupportedSetup()

    private inner class Budget {
        var steps = 0
        fun tick() {
            ProgressManager.checkCanceled()
            if (++steps > 5000) unsupported()
        }
    }

    private data class BranchSegment(val id: String?, val type: String, val queued: Boolean,
        val repetition: Repetition?, val templateItem: Boolean, val template: Boolean)

    private fun merge(paths: List<Expansion>, rootId: String, budget: Budget): Expansion {
        fun indexed(expansion: Expansion): Map<List<BranchSegment>, ComponentNode> {
            val byId = expansion.nodes.associateBy { it.nodeId }
            val knownPaths = mutableMapOf<String, List<BranchSegment>>()
            fun path(node: ComponentNode, seen: Set<String> = emptySet()): List<BranchSegment> {
                budget.tick()
                knownPaths[node.nodeId]?.let { return it }
                val queued = node.queuedInNodeId != null
                val pendingQueue = queued && node.confidence == Confidence.UNRESOLVED &&
                    node.diagnostics.isNotEmpty() && node.diagnostics.all { it.code == DiagnosticCode.UNRESOLVED_QUEUE }
                if (node.nodeId in seen || seen.size >= 256 || !node.templateItem && node.wicketId.isNullOrEmpty() ||
                    !pendingQueue && (node.confidence != Confidence.CONFIRMED || node.diagnostics.isNotEmpty())) unsupported()
                val parentId = node.queuedInNodeId ?: node.parentNodeId
                if (node.templateItem && (node.wicketId != null || !node.template || queued || byId[parentId]?.repetition == null)) unsupported()
                val parent = if (parentId == rootId) emptyList()
                    else path(byId[parentId] ?: unsupported(), seen + node.nodeId)
                return (parent + BranchSegment(node.wicketId, node.componentType, queued, node.repetition,
                    node.templateItem, node.template)).also { knownPaths[node.nodeId] = it }
            }
            // These informational warnings describe symbolic item populations;
            // any unsupported scope/effect warning still rejects the whole merge.
            if (expansion.warnings.any { it.code != DiagnosticCode.PARTIAL_ANALYSIS }) unsupported()
            return expansion.nodes.associateBy { path(it) }.also { graph ->
                // Duplicate names on a single concrete path are still unsupported,
                // even if the duplicate components happen to have different types.
                if (graph.keys.map { key -> key.map { it.id to it.templateItem } }.distinct().size != expansion.nodes.size) unsupported()
            }
        }
        val graphs = paths.map(::indexed)
        val keys = graphs.flatMap { it.keys }.distinct()
        if (keys.size > 256) unsupported()
        // An allocation may move between parents on different paths, so its source
        // node ID cannot identify both merged alternatives. IDs remain snapshot-local.
        val identities = keys.mapIndexed { index, key -> key to "java:branch:${index + 1}" }.toMap()
        val nodes = keys.map { path ->
            budget.tick()
            val alternatives = graphs.mapNotNull { it[path] }
            val node = alternatives.first()
            val conditional = alternatives.size != graphs.size
            val queued = node.queuedInNodeId != null
            val scope = if (path.size == 1) rootId else identities.getValue(path.dropLast(1))
            node.copy(nodeId = identities.getValue(path),
                parentNodeId = if (queued) null else scope,
                queuedInNodeId = if (queued) scope else null,
                branchMerged = true, conditional = conditional, idRenameSafe = false,
                branchPathMask = graphs.indices.fold(0) { mask, index ->
                    if (path in graphs[index]) mask or (1 shl index) else mask
                },
                computedId = alternatives.any { it.computedId },
                computedIdLocations = alternatives.flatMap { alternative ->
                    alternative.idLocations().filter(alternative::isComputedIdOrigin)
                }.distinct(),
                confidence = if (conditional || queued) Confidence.UNRESOLVED else Confidence.CONFIRMED,
                diagnostics = node.diagnostics + if (conditional) listOf(Diagnostic(DiagnosticCode.UNRESOLVED_BRANCH,
                    "This component type and parent path occur on only some reviewed setup paths; runtime presence is conditional.",
                    node.javaLocation)) else emptyList(),
                alternativeJavaLocations = alternatives.map { it.javaLocation }.distinct().filter { it != node.javaLocation },
                alternativeIdLocations = alternatives.mapNotNull { it.idLocation }.distinct().filter { it != node.idLocation },
                idUsedInComputation = alternatives.any { it.idUsedInComputation })
        }
        val sources = paths.flatMap { it.sources }.distinct().sorted()
        if (sources.size > 24) unsupported()
        return Expansion(nodes, sources, paths.flatMap { it.warnings }.distinct(), branchPathCount = paths.size)
    }

    private sealed interface Value
    private data class Literal(val value: Any?, val origin: PsiLiteralExpression) : Value
    // Computed booleans never acquire an ID literal origin.
    private data class Truth(val value: Boolean) : Value
    // Identity follows a stable entry boolean through aliases and reviewed reads;
    // symbols and path facts never leave this invocation or become ID origins.
    private data class BooleanSymbol(val id: Int, val negated: Boolean = false) : Value
    private enum class BooleanOperator { AND, OR, XOR }
    // Reviewed boolean expressions over immutable flag values. These are
    // invocation-local snapshots, never identities derived from expression text.
    private data class BooleanGroup(val operator: BooleanOperator, val operands: List<Value>, val negated: Boolean = false) : Value
    // Immutable integral snapshots retain identity, never a guessed runtime value.
    private data class IntegralSymbol(val id: Int, val minimum: Long, val maximum: Long) : Value
    private data class ComparisonKey(val input: Int, val equality: Boolean, val threshold: Long)
    private data class IntegralRange(val minimum: Long, val maximum: Long, val excluded: Set<Long> = emptySet())
    private data class Computed(val value: Any, val origin: PsiExpression) : Value
    private data class Instance(val id: String) : Value
    // Allocation type and immutable field environment belong to this invocation only.
    private data class Helper(val type: PsiClass, val allocation: Int) : Value
    private data class HelperState(val framePath: List<Int>, var allocationType: PsiType,
        val fields: MutableMap<PsiField, Value> = mutableMapOf(),
        val initialized: MutableSet<PsiClass> = mutableSetOf())
    private enum class ReturnKind { COMPONENT, PROVIDER, SCALAR }
    private data class ReturnContract(val kind: ReturnKind, val type: PsiType, val substitutor: PsiSubstitutor,
        val readEffects: Int)
    private data object Root : Value
    private data object Unknown : Value
    private data object UnitValue : Value
    private data object Data : Value
    private data class ChildId(val view: String) : Value
    private data class Observed(var node: ComponentNode, val framePath: List<Int>, val parents: MutableList<String> = mutableListOf(), var used: Boolean = false,
        val queues: MutableList<String> = mutableListOf())
    private data class Frame(val owner: PsiClass, val method: PsiMethod?, val rootReceiver: Boolean,
        val values: MutableMap<PsiVariable, Value>, val path: List<Int>, val initialization: Boolean = false,
        val receiver: String? = null, val templateRoot: String? = null, val templatePath: List<Int> = emptyList(),
        val helperReceiver: Helper? = null, val allowReturn: Boolean = true)

    private inner class Session(private val page: PsiClass, private val rootId: String,
        private val choices: List<Boolean>, private val budget: Budget) {
        private var choiceIndex = 0
        private var booleanSequence = 0
        private val booleanFacts = mutableMapOf<Int, Boolean>()
        private var integralSequence = 0
        private val comparisonSymbols = mutableMapOf<ComparisonKey, Int>()
        private val comparisonKeys = mutableMapOf<Int, ComparisonKey>()
        private val integralRanges = mutableMapOf<Int, IntegralRange>()
        private val hierarchy = mutableListOf<PsiClass>()
        private val sources = linkedSetOf<String>()
        private val checkedFiles = mutableSetOf<PsiFile>()
        private val observed = linkedMapOf<String, Observed>()
        private val fields = mutableMapOf<PsiField, Value>()
        private val fieldReads = mutableSetOf<PsiReferenceExpression>()
        private val skippedRanges = mutableSetOf<PsiElement>()
        private val computedInputs = mutableSetOf<SourceLocation>()
        private val methods = mutableSetOf<PsiMethod>()
        private val shadowedMethods = mutableSetOf<PsiMethod>()
        private val helperTypes = mutableSetOf<PsiClass>()
        private val helperStates = mutableMapOf<Helper, HelperState>()
        private var helperAllocations = 0
        private val stack = mutableSetOf<PsiMethod>()
        private val initialized = mutableSetOf<PsiClass>()
        private var sequence = 0
        private var invocations = 0
        // A monotonic audit of operations outside a single-return read. Nested calls
        // participate too; an accessor cannot hide allocation, setup, or mutable reads.
        private var readEffects = 0
        private var featureUsed = InheritanceUtil.isInheritor(page, WicketComponentTypes.FORM_PANEL)
        private val constants = WicketSourceConstants(::source, ::tick)
        private val warnings = mutableListOf<Diagnostic>()
        private val generatedItems = mutableMapOf<String, String>()

        private fun tick() {
            budget.tick()
        }

        fun run(): Expansion? {
            var owner: PsiClass? = page
            while (owner?.qualifiedName !in ROOTS) {
                tick()
                if (owner == null || owner is PsiCompiledElement || hierarchy.size >= 12 || owner in hierarchy) unsupported()
                hierarchy += owner
                source(owner)
                owner = owner.superClass
            }
            if (hierarchy.size > 1) featureUsed = true
            hierarchy.forEach { type ->
                if (PsiTreeUtil.findChildOfType(type.containingFile, PsiErrorElement::class.java) != null ||
                    type.initializers.isNotEmpty() || type.fields.any { it.hasModifierProperty(PsiModifier.STATIC) && !safeStaticInitializer(it) }) unsupported()
                type.interfaces.filter { hasDefaults(it, mutableSetOf(), 0) }.forEach { validateHelper(it, mutableSetOf(), 0) }
            }
            if (page.constructors.size > 1) unsupported()
            constructor(page, page.constructors.singleOrNull(), emptyList(), emptyList())
            val hook = hierarchy.firstNotNullOfOrNull { type -> type.methods.singleOrNull(::isHook) }
            if (hook != null) initialize(hook, emptyList())
            validateUnexecutedMembers()
            validateFieldReferences()
            if (!featureUsed) return null

            val retained = observed.values.filter { it.used || it.parents.isNotEmpty() || it.queues.isNotEmpty() }
            fun resolved(id: String, seen: MutableSet<String>): Boolean {
                if (id == rootId) return true
                if (!seen.add(id)) return false
                val value = observed[id] ?: return false
                return (value.parents + value.queues).singleOrNull()?.let { resolved(it, seen) } == true
            }
            // Receiver-only instances, repeated additions, and cycles cannot become confirmed paths.
            if (retained.any { !resolved(it.node.nodeId, mutableSetOf()) }) unsupported()
            val ordinary = retained.map { value ->
                if (value.queues.isEmpty()) value.node.copy(parentNodeId = value.parents.single())
                else value.node.copy(parentNodeId = null, queuedInNodeId = value.queues.single(), confidence = Confidence.UNRESOLVED,
                    diagnostics = value.node.diagnostics + Diagnostic(DiagnosticCode.UNRESOLVED_QUEUE,
                        "Queue placement requires one eligible markup location in the declared queue scope.", value.node.javaLocation))
            }
            generatedItems.forEach { (item, view) -> if (ordinary.singleOrNull { it.nodeId == item }?.parentNodeId != view) unsupported() }
            if (generatedItems.values.groupingBy { it }.eachCount().values.any { it > 1 }) unsupported()
            val byId = ordinary.associateBy { it.nodeId }
            ordinary.filter { it.repetition != null }.forEach { view ->
                if (ordinary.any { it.parentNodeId == view.nodeId && !it.templateItem }) unsupported()
            }
            return Expansion(ordinary.map { node ->
                val template = node.template || generateSequence(node) { byId[it.parentNodeId] }.any { it.templateItem }
                val dependent = node.idLocation in computedInputs
                node.copy(template = template, idRenameSafe = node.idRenameSafe && !dependent, idUsedInComputation = dependent)
            }, sources.toList().sorted(), warnings.distinct())
        }

        private fun source(element: PsiElement) {
            val file = element.containingFile
            sources += file.virtualFile?.path ?: file.name
            if (sources.size > 24) unsupported()
            if (checkedFiles.add(file) && element !is PsiCompiledElement &&
                PsiTreeUtil.findChildOfType(file, PsiErrorElement::class.java) != null) unsupported()
        }

        private fun frame(owner: PsiClass, method: PsiMethod?, args: List<Value>, path: List<Int>, root: Boolean = true,
            initialization: Boolean = false): Frame {
            tick()
            if (++invocations > 128 || stack.size >= 12 || path.size >= 12) unsupported()
            val parameters = method?.parameterList?.parameters.orEmpty()
            if (method != null && (method.isVarArgs || method.hasModifierProperty(PsiModifier.SYNCHRONIZED))) unsupported()
            val entry = method?.isConstructor == true && owner == page && path.isEmpty()
            if (!entry && parameters.size != args.size) unsupported()
            return Frame(owner, method, root, parameters.mapIndexed { index, parameter ->
                parameter to stablePrimitive(parameter.type, args.getOrElse(index) { Unknown })
            }.toMap().toMutableMap(), path + invocations, initialization)
        }

        private fun stablePrimitive(type: PsiType, value: Value): Value {
            if (value != Unknown) return value
            return when (type.canonicalText) {
                "boolean" -> BooleanSymbol(++booleanSequence)
                "byte" -> IntegralSymbol(++integralSequence, Byte.MIN_VALUE.toLong(), Byte.MAX_VALUE.toLong())
                "short" -> IntegralSymbol(++integralSequence, Short.MIN_VALUE.toLong(), Short.MAX_VALUE.toLong())
                "char" -> IntegralSymbol(++integralSequence, Char.MIN_VALUE.code.toLong(), Char.MAX_VALUE.code.toLong())
                "int" -> IntegralSymbol(++integralSequence, Int.MIN_VALUE.toLong(), Int.MAX_VALUE.toLong())
                "long" -> IntegralSymbol(++integralSequence, Long.MIN_VALUE, Long.MAX_VALUE)
                else -> value
            }
        }

        private fun constructor(owner: PsiClass, constructorMethod: PsiMethod?, args: List<Value>, path: List<Int>) {
            tick()
            if (owner.qualifiedName in ROOTS) return
            val method = constructorMethod.takeUnless { owner.constructors.isEmpty() }
            if (owner !in hierarchy || owner in initialized || method != null && !stack.add(method)) unsupported()
            try {
                method?.let { methods += it }
                val context = frame(owner, method, args, path)
                val statements = method?.body?.statements?.filterNot { it is PsiEmptyStatement }.orEmpty()
                if (method != null && method.body == null) unsupported()
                val firstCall = (statements.firstOrNull() as? PsiExpressionStatement)?.expression as? PsiMethodCallExpression
                if (firstCall?.methodExpression?.referenceName == "this") unsupported()
                val delegation = firstCall?.takeIf { it.methodExpression.referenceName == "super" }
                val parent = owner.superClass ?: unsupported()
                if (delegation != null) {
                    val implicit = parent.constructors.isEmpty() && delegation.argumentList.expressionCount == 0 && parent !is PsiCompiledElement
                    if (!implicit && !delegation.resolveMethodGenerics().isValidResult) unsupported()
                    val target = delegation.resolveMethod()
                    noUnboxing(target, delegation.argumentList.expressions)
                    if (target?.containingClass != parent && !implicit) unsupported()
                    val values = delegation.argumentList.expressions.map { argument(it, context) }
                    if (parent.qualifiedName !in ROOTS) constructor(parent, target, values, context.path)
                } else if (parent.qualifiedName !in ROOTS) {
                    val target = parent.constructors.singleOrNull { it.parameterList.parametersCount == 0 }
                    if (target == null && parent.constructors.isNotEmpty()) unsupported()
                    constructor(parent, target, emptyList(), context.path)
                }
                initialized += owner
                owner.fields.filterNot { it.hasModifierProperty(PsiModifier.STATIC) }.forEach { field ->
                    tick()
                    val expression = field.initializer ?: return@forEach
                    scalarAssignment(field.type, expression)
                    if (component(field.type)) {
                        if (!field.hasModifierProperty(PsiModifier.PRIVATE) || !field.hasModifierProperty(PsiModifier.FINAL)) unsupported()
                        fields[field] = fresh(expression, context)
                    } else {
                        if (unwrap(expression) is PsiLiteralExpression) fields[field] = evaluate(expression, context)
                        else {
                            if (!field.hasModifierProperty(PsiModifier.PRIVATE) || !field.hasModifierProperty(PsiModifier.FINAL)) unsupported()
                            fields[field] = setupValue(expression, context)
                        }
                    }
                }
                execute(statements.drop(if (delegation == null) 0 else 1), context)
            } finally { method?.let(stack::remove) }
        }

        private fun isHook(method: PsiMethod) = method.name == "onInitialize" && method.parameterList.parametersCount == 0 &&
            method.returnType?.canonicalText == "void" && !method.hasModifierProperty(PsiModifier.STATIC) &&
            (method.hasModifierProperty(PsiModifier.PUBLIC) || method.hasModifierProperty(PsiModifier.PROTECTED)) &&
            method.findDeepestSuperMethods().any { it.containingClass?.qualifiedName == COMPONENT }

        private fun initialize(method: PsiMethod, path: List<Int>) {
            if (method.containingClass?.qualifiedName in FRAMEWORK_INITIALIZERS) return
            tick()
            val owner = method.containingClass ?: unsupported()
            if (owner !in hierarchy || !stack.add(method)) unsupported()
            try {
                methods += method
                source(method)
                val context = frame(owner, method, emptyList(), path, initialization = true)
                val statements = method.body?.statements?.filterNot { it is PsiEmptyStatement } ?: unsupported()
                val call = (statements.firstOrNull() as? PsiExpressionStatement)?.expression as? PsiMethodCallExpression ?: unsupported()
                val qualifier = call.methodExpression.qualifierExpression as? PsiSuperExpression ?: unsupported()
                if (qualifier.qualifier != null || call.methodExpression.referenceName != "onInitialize" ||
                    call.argumentList.expressionCount != 0 || !call.resolveMethodGenerics().isValidResult) unsupported()
                val parent = call.resolveMethod() ?: unsupported()
                if (parent.containingClass?.qualifiedName !in FRAMEWORK_INITIALIZERS) {
                    if (!isHook(parent)) unsupported()
                    featureUsed = true
                    initialize(parent, context.path)
                }
                execute(statements.drop(1), context)
            } finally { stack.remove(method) }
        }

        private fun execute(statements: List<PsiStatement>, context: Frame, factory: ReturnContract? = null): Value {
            if (statements.filterNot { it is PsiEmptyStatement }.singleOrNull() !is PsiReturnStatement) readEffects++
            val returned = executeStatements(statements, context, factory, 0)
            if (factory != null && returned == null) unsupported()
            return returned ?: UnitValue
        }

        // null means normal completion; UnitValue means a bare return from this method.
        private fun executeStatements(statements: List<PsiStatement>, context: Frame, factory: ReturnContract?, depth: Int): Value? {
            if (depth >= 12) unsupported()
            statements.forEachIndexed { index, statement ->
                tick()
                val returned = when (statement) {
                    is PsiEmptyStatement -> null
                    is PsiDeclarationStatement -> {
                        statement.declaredElements.forEach { element ->
                            val variable = element as? PsiLocalVariable ?: unsupported()
                            val initializer = variable.initializer ?: unsupported()
                            scalarAssignment(variable.type, initializer)
                            val value = if (component(variable.type)) fresh(initializer, context) else {
                                if (unwrap(initializer) is PsiLiteralExpression) evaluate(initializer, context)
                                else setupValue(initializer, context)
                            }
                            context.values[variable] = stablePrimitive(variable.type, value)
                        }
                        null
                    }
                    is PsiExpressionStatement -> {
                        val expression = statement.expression
                        if (expression is PsiAssignmentExpression) assign(expression, context)
                        else if (expression is PsiMethodCallExpression) invoke(expression, context)
                        else unsupported()
                        null
                    }
                    is PsiReturnStatement -> {
                        if (!context.allowReturn) unsupported()
                        featureUsed = true
                        returned(statement, context, factory)
                    }
                    is PsiBlockStatement -> {
                        featureUsed = true
                        executeStatements(statement.codeBlock.statements.toList(), context.copy(values = context.values.toMutableMap()), factory, depth + 1)
                    }
                    is PsiIfStatement -> {
                        featureUsed = true
                        val selected = branchCondition(statement.condition ?: unsupported(), context)
                        skip(if (selected) statement.elseBranch else statement.thenBranch)
                        val branch = if (selected) statement.thenBranch else statement.elseBranch
                        branch?.let {
                            val body = if (it is PsiBlockStatement) it.codeBlock.statements.toList() else listOf(it)
                            executeStatements(body, context.copy(values = context.values.toMutableMap()), factory, depth + 1)
                        }
                    }
                    is PsiForeachStatement -> {
                        foreach(statement, context)
                        null
                    }
                    else -> unsupported()
                }
                if (returned != null) {
                    statements.drop(index + 1).forEach(::skip)
                    return returned
                }
            }
            return null
        }

        private fun returned(statement: PsiReturnStatement, context: Frame, factory: ReturnContract?): Value {
            if (factory == null) {
                if (statement.returnValue != null) unsupported()
                return UnitValue
            }
            val value = statement.returnValue?.let { evaluate(it, context) } ?: unsupported()
            when (factory.kind) {
                ReturnKind.COMPONENT -> {
                    val instance = value as? Instance ?: unsupported()
                    val created = observed[instance.id] ?: unsupported()
                    if (created.framePath.take(context.path.size) != context.path) unsupported()
                }
                ReturnKind.PROVIDER -> {
                    val provider = value as? Helper ?: unsupported()
                    val state = helperStates[provider] ?: unsupported()
                    val fresh = state.framePath.take(context.path.size) == context.path
                    if ((!fresh && readEffects != factory.readEffects) || provider.type !in state.initialized) unsupported()
                    val actualType = factory.substitutor.substitute(state.allocationType) ?: unsupported()
                    if (!TypeConversionUtil.isAssignable(factory.type, actualType)) unsupported()
                    state.allocationType = actualType
                }
                ReturnKind.SCALAR -> {
                    if (readEffects != factory.readEffects || value !is Literal && value !is Truth && value !is BooleanSymbol && value !is BooleanGroup && value !is IntegralSymbol && value !is Computed && value != Unknown) unsupported()
                    if ((value is Truth || value is BooleanSymbol || value is BooleanGroup) && factory.type.canonicalText != "boolean") unsupported()
                    if (value is IntegralSymbol && !WicketScalarExpressions.integral(factory.type)) unsupported()
                    if (value is Literal && (value.value == null || !immutableOrigin(value))) unsupported()
                    val actualType = factory.substitutor.substitute(statement.returnValue?.type) ?: unsupported()
                    if (!scalarType(actualType) || !TypeConversionUtil.isAssignable(factory.type, actualType) &&
                        !TypeConversionUtil.areTypesAssignmentCompatible(factory.type, statement.returnValue!!)) unsupported()
                }
            }
            return value
        }

        private fun skip(element: PsiElement?) { element?.let(skippedRanges::add) }

        private fun branchCondition(expression: PsiExpression, context: Frame): Boolean =
            decideBoolean(booleanRead(expression, context))

        private fun decideBoolean(value: Value, depth: Int = 0): Boolean {
            knownBoolean(value)?.let { return it }
            if (value is BooleanGroup) {
                if (depth >= 12) unsupported()
                val and = value.operator == BooleanOperator.AND
                var result = and
                for (operand in value.operands) {
                    tick()
                    val next = decideBoolean(operand, depth + 1)
                    result = if (value.operator == BooleanOperator.XOR) result xor next else next
                    if (value.operator != BooleanOperator.XOR && result != and) break
                }
                return if (value.negated) !result else result
            }
            val selected = choices.getOrNull(choiceIndex++) ?: throw BranchRequired()
            if (value is BooleanSymbol) {
                val fact = selected != value.negated
                comparisonKeys[value.id]?.let { refineRange(it, fact) }
                booleanFacts[value.id] = fact
            }
            return selected
        }

        private fun knownBoolean(value: Value, depth: Int = 0): Boolean? = when (value) {
            is Literal -> value.value as? Boolean
            is Truth -> value.value
            is BooleanSymbol -> (booleanFacts[value.id] ?: comparisonKeys[value.id]?.let(::rangeFact))
                ?.let { if (value.negated) !it else it }
            is BooleanGroup -> {
                if (depth >= 12) unsupported()
                val and = value.operator == BooleanOperator.AND
                var result: Boolean? = and
                for (operand in value.operands) {
                    tick()
                    if (value.operator != BooleanOperator.XOR && result == !and) break
                    val next = knownBoolean(operand, depth + 1)
                    result = when (value.operator) {
                        BooleanOperator.AND -> if (result == false || next == false) false else if (result == null || next == null) null else true
                        BooleanOperator.OR -> if (result == true || next == true) true else if (result == null || next == null) null else false
                        BooleanOperator.XOR -> if (result == null || next == null) null else result xor next
                    }
                }
                result?.let { if (value.negated) !it else it }
            }
            else -> null
        }

        private fun booleanRead(expression: PsiExpression, context: Frame): Value {
            if (expression.type?.canonicalText != "boolean") unsupported()
            val before = readEffects
            val value = evaluate(expression, context)
            if (before != readEffects) unsupported()
            when (value) {
                is Literal -> {
                    if (!immutableOrigin(value) || value.value !is Boolean) unsupported()
                }
                is Truth, is BooleanSymbol, is BooleanGroup, Unknown, Data -> Unit
                else -> unsupported()
            }
            return value
        }

        private fun choice(expression: PsiConditionalExpression, context: Frame): PsiExpression {
            choiceDepth(expression)
            featureUsed = true
            val selected = branchCondition(expression.condition, context)
            skip(if (selected) expression.elseExpression else expression.thenExpression)
            val branch = (if (selected) expression.thenExpression else expression.elseExpression) ?: unsupported()
            if (expression.type is PsiPrimitiveType && branch.type !is PsiPrimitiveType) unsupported()
            if (!TypeConversionUtil.areTypesAssignmentCompatible(expression.type ?: unsupported(), branch)) unsupported()
            return branch
        }

        private fun logical(expression: PsiPolyadicExpression, context: Frame): Value {
            choiceDepth(expression)
            val and = when (expression.operationTokenType) {
                JavaTokenType.ANDAND -> true
                JavaTokenType.OROR -> false
                else -> unsupported()
            }
            featureUsed = true
            var result: Boolean? = and
            val operands = expression.operands
            val values = mutableListOf<Value>()
            for ((index, operand) in operands.withIndex()) {
                if (result == !and) {
                    operands.drop(index).forEach(::skip)
                    break
                }
                val value = booleanRead(operand, context)
                values += value
                val next = knownBoolean(value)
                result = when {
                    next == !and -> !and
                    result == null || next == null -> null
                    else -> and
                }
            }
            return result?.let(::Truth) ?: if (values.all { it is BooleanSymbol || it is BooleanGroup || knownBoolean(it) != null })
                BooleanGroup(if (and) BooleanOperator.AND else BooleanOperator.OR, values) else Unknown
        }

        private fun eagerBoolean(expression: PsiPolyadicExpression, context: Frame): Value {
            val token = expression.operationTokenType
            val operator = when (token) {
                JavaTokenType.AND -> BooleanOperator.AND
                JavaTokenType.OR -> BooleanOperator.OR
                JavaTokenType.XOR, JavaTokenType.EQEQ, JavaTokenType.NE -> BooleanOperator.XOR
                else -> unsupported()
            }
            // Audit every source operand, left to right, before inspecting any
            // proof group. A decisive value never skips an eager operand's effects.
            val values = expression.operands.map { operand ->
                booleanRead(operand, context).also { if (it is Literal) computedInputs += location(it.origin) }
            }
            // Boolean equality is XNOR. A same-token chain is left associative:
            // each equality flips parity once, unlike != and ^.
            val group = BooleanGroup(operator, values, negated = token == JavaTokenType.EQEQ && values.size % 2 == 0)
            return knownBoolean(group)?.let(::Truth) ?: if (values.all {
                    it is BooleanSymbol || it is BooleanGroup || knownBoolean(it) != null
                }) group else Unknown
        }

        private fun scalar(expression: PsiExpression, context: Frame): Any? {
            if (!WicketScalarExpressions.supported(expression.type)) unsupported()
            val before = readEffects
            val value = evaluate(expression, context)
            if (before != readEffects) unsupported()
            val raw = when (value) {
                is Literal -> {
                    if (!immutableOrigin(value)) unsupported()
                    computedInputs += location(value.origin)
                    value.value ?: unsupported()
                }
                is Computed -> value.value
                is Truth -> value.value
                is BooleanSymbol, is BooleanGroup -> knownBoolean(value) ?: return null
                is IntegralSymbol, Unknown, Data -> return null
                else -> unsupported()
            }
            return WicketScalarExpressions.normalize(raw, expression.type) ?: unsupported()
        }

        private fun calculation(expression: PsiPolyadicExpression, context: Frame): Value {
            choiceDepth(expression)
            featureUsed = true
            val token = expression.operationTokenType
            val operator = WicketScalarExpressions.operator(token) ?: unsupported()
            val operands = expression.operands
            if (!WicketScalarExpressions.binaryOperandsSupported(token, operands.map { it.type })) unsupported()
            if (operands.all { it.type?.canonicalText == "boolean" }) return eagerBoolean(expression, context)
            if (operands.size == 2 && operands.all { WicketScalarExpressions.integral(it.type) } &&
                operator in setOf("<", "<=", ">", ">=", "==", "!=")) return integralComparison(expression, context, operator)
            val values = operands.map { scalar(it, context) }
            if (token in setOf(JavaTokenType.DIV, JavaTokenType.PERC) && values.drop(1).any { it == null || (it as? Number)?.toLong() == 0L || it == '\u0000' }) unsupported()
            if (values.any { it == null }) return Unknown
            val result = WicketScalarExpressions.fold(expression, values.filterNotNull(), operator) ?: unsupported()
            return if (result is Boolean) Truth(result) else Computed(result, expression)
        }

        private fun integralComparison(expression: PsiPolyadicExpression, context: Frame, operator: String): Value {
            val values = expression.operands.map { operand ->
                val before = readEffects
                val value = evaluate(operand, context)
                if (before != readEffects) unsupported()
                val raw = when (value) {
                    is Literal -> {
                        if (!immutableOrigin(value)) unsupported()
                        computedInputs += location(value.origin)
                        value.value ?: unsupported()
                    }
                    is Computed -> value.value
                    is IntegralSymbol, Unknown, Data -> null
                    else -> unsupported()
                }
                value to raw?.let { WicketScalarExpressions.normalize(it, operand.type) ?: unsupported() }
            }
            if (values.all { it.second != null }) return Truth(WicketScalarExpressions.fold(expression,
                values.map { it.second!! }, operator) as? Boolean ?: unsupported())
            val left = values[0].first as? IntegralSymbol
            val right = values[1].first as? IntegralSymbol
            if (left != null && right != null && left.id == right.id)
                return Truth(operator in setOf("<=", ">=", "=="))
            fun number(value: Any?): Long? = when (value) {
                is Number -> value.toLong()
                is Char -> value.code.toLong()
                else -> null
            }
            if (left != null) number(values[1].second)?.let { return comparison(left, operator, it) }
            if (right != null) number(values[0].second)?.let { constant ->
                val reversed = when (operator) { "<" -> ">"; "<=" -> ">="; ">" -> "<"; ">=" -> "<="; else -> operator }
                return comparison(right, reversed, constant)
            }
            return Unknown
        }

        private fun comparison(input: IntegralSymbol, operator: String, constant: Long): Value {
            tick()
            // Integer <= c and > c use the cut at c + 1. Guard the long endpoint
            // before incrementing; primitive type bounds are facts about all values.
            if (constant == Long.MAX_VALUE && operator in setOf("<=", ">")) return Truth(operator == "<=")
            val equality = operator in setOf("==", "!=")
            val threshold = if (operator in setOf("<=", ">")) constant + 1 else constant
            val negated = operator in setOf(">", ">=", "!=")
            val known = if (equality) {
                if (threshold < input.minimum || threshold > input.maximum) false else null
            } else when {
                threshold <= input.minimum -> false
                threshold > input.maximum -> true
                else -> null
            }
            if (known != null) return Truth(known != negated)
            val id = comparisonSymbols.getOrPut(ComparisonKey(input.id, equality, threshold)) { ++booleanSequence }
            comparisonKeys[id] = ComparisonKey(input.id, equality, threshold)
            integralRanges.putIfAbsent(input.id, IntegralRange(input.minimum, input.maximum))
            return BooleanSymbol(id, negated)
        }

        private fun rangeFact(key: ComparisonKey): Boolean? {
            tick()
            val range = integralRanges.getValue(key.input)
            return if (key.equality) when {
                key.threshold < range.minimum || key.threshold > range.maximum || key.threshold in range.excluded -> false
                range.minimum == range.maximum -> true
                else -> null
            } else when {
                range.maximum < key.threshold -> true
                range.minimum >= key.threshold -> false
                else -> null
            }
        }

        private fun refineRange(key: ComparisonKey, fact: Boolean) {
            tick()
            val previous = integralRanges.getValue(key.input)
            val refined = if (key.equality) {
                if (fact) IntegralRange(key.threshold, key.threshold)
                else previous.copy(excluded = previous.excluded + key.threshold)
            } else if (fact) previous.copy(maximum = minOf(previous.maximum, key.threshold - 1))
                else previous.copy(minimum = maxOf(previous.minimum, key.threshold))
            var minimum = refined.minimum
            var maximum = refined.maximum
            if (minimum > maximum) unsupported()
            // Remove excluded endpoints without enumerating a primitive domain.
            // Each hole came from an actual bounded choice; singleton endpoints
            // are guarded before stepping, including Long.MIN/MAX_VALUE.
            while (minimum in refined.excluded) {
                tick()
                if (minimum == maximum) unsupported()
                minimum++
            }
            while (maximum in refined.excluded) {
                tick()
                if (minimum == maximum) unsupported()
                maximum--
            }
            integralRanges[key.input] = IntegralRange(minimum, maximum,
                refined.excluded.filter { it > minimum && it < maximum }.toSet())
        }

        private fun numericPrefix(expression: PsiPrefixExpression, context: Frame): Value {
            if (expression.operationTokenType !in setOf(JavaTokenType.PLUS, JavaTokenType.MINUS, JavaTokenType.TILDE) || !WicketScalarExpressions.integral(expression.type)) unsupported()
            featureUsed = true
            val operand = expression.operand ?: unsupported()
            val literal = unwrap(operand) as? PsiLiteralExpression
            if (literal != null) {
                if (!WicketScalarExpressions.validIntegralLiteral(literal,
                        expression.operationTokenType == JavaTokenType.MINUS && operand === literal)) unsupported()
                val literalResult = JavaPsiFacade.getInstance(page.project).constantEvaluationHelper.computeConstantExpression(expression, false)
                    ?: unsupported()
                return Computed(literalResult, expression)
            }
            val value = scalar(expression.operand ?: unsupported(), context) ?: return Unknown
            val result = WicketScalarExpressions.fold(expression, listOf(value), WicketScalarExpressions.operator(expression.operationTokenType)!!, unary = true) ?: unsupported()
            return Computed(result, expression)
        }

        private fun cast(expression: PsiTypeCastExpression, context: Frame): Value {
            choiceDepth(expression)
            if (!WicketScalarExpressions.integral(expression.castType?.type) || !WicketScalarExpressions.integral(expression.operand?.type)) unsupported()
            featureUsed = true
            val value = scalar(expression.operand ?: unsupported(), context) ?: return Unknown
            return Computed(WicketScalarExpressions.normalize(value, expression.castType?.type) ?: unsupported(), expression)
        }

        private fun choiceDepth(expression: PsiExpression) {
            tick()
            val depth = generateSequence<PsiElement>(expression) { it.parent }
                .takeWhile { it !is PsiMethod && it !is PsiField }
                .count { it is PsiConditionalExpression || it is PsiPrefixExpression || it is PsiPolyadicExpression || it is PsiTypeCastExpression }
            if (depth > 12) unsupported()
        }

        private fun fresh(expression: PsiExpression, context: Frame): Value {
            val value = unwrap(expression)
            if (value is PsiConditionalExpression) return fresh(choice(value, context), context)
            if (value !is PsiNewExpression && value !is PsiMethodCallExpression) unsupported()
            return evaluate(value, context).takeIf { it is Instance } ?: unsupported()
        }

        private fun setupValue(expression: PsiExpression, context: Frame): Value = when (val value = unwrap(expression)) {
            is PsiConditionalExpression -> setupValue(choice(value, context), context)
            is PsiPrefixExpression, is PsiPolyadicExpression -> evaluate(value, context)
            is PsiTypeCastExpression -> if (WicketScalarExpressions.integral(value.type)) evaluate(value, context) else data(value, context)
            is PsiNewExpression -> if ((value.classReference?.resolve() as? PsiClass)?.qualifiedName in
                setOf("org.apache.wicket.model.Model", "org.apache.wicket.model.ListModel")) data(value, context)
                else helper(value, context)
            is PsiReferenceExpression -> evaluate(value, context).let { if (it is Helper) it else data(value, context) }
            is PsiMethodCallExpression -> when {
                providerType(value.type) -> freshHelper(value, context)
                scalarType(value.type) -> evaluate(value, context)
                else -> data(value, context)
            }
            else -> data(value, context)
        }

        private fun freshHelper(expression: PsiExpression, context: Frame): Helper {
            val value = unwrap(expression)
            if (value !is PsiNewExpression && value !is PsiMethodCallExpression && value !is PsiConditionalExpression) unsupported()
            return evaluate(value, context) as? Helper ?: unsupported()
        }

        private fun providerType(type: PsiType?): Boolean {
            val owner = (type as? PsiClassType)?.resolve() ?: return false
            return owner !is PsiCompiledElement && owner.qualifiedName != null && !component(type)
        }

        private fun scalarType(type: PsiType?): Boolean = type?.equalsToText("java.lang.String") == true ||
            type is PsiPrimitiveType && type.canonicalText != "void"

        private fun immutableOrigin(value: Literal): Boolean {
            val field = PsiTreeUtil.getParentOfType(value.origin, PsiField::class.java) ?: return true
            return field.hasModifierProperty(PsiModifier.PRIVATE) && field.hasModifierProperty(PsiModifier.FINAL) &&
                !field.hasModifierProperty(PsiModifier.STATIC)
        }

        private fun scalarAssignment(type: PsiType, expression: PsiExpression) {
            if (scalarType(type) && !TypeConversionUtil.areTypesAssignmentCompatible(type, expression)) unsupported()
        }

        private fun noUnboxing(method: PsiMethod?, expressions: Array<out PsiExpression>) {
            if (method?.parameterList?.parameters.orEmpty().zip(expressions).any { (parameter, expression) ->
                    parameter.type is PsiPrimitiveType && expression.type !is PsiPrimitiveType
                }) unsupported()
        }

        private fun assign(expression: PsiAssignmentExpression, context: Frame) {
            if (expression.operationTokenType != JavaTokenType.EQ || context.method?.isConstructor != true) unsupported()
            val reference = expression.lExpression as? PsiReferenceExpression ?: unsupported()
            val field = reference.resolve() as? PsiField ?: unsupported()
            scalarAssignment(field.type, expression.rExpression ?: unsupported())
            if (context.helperReceiver != null) {
                val state = helperStates[context.helperReceiver] ?: unsupported()
                if (field.containingClass != context.owner || !helperReference(reference) ||
                    !field.hasModifierProperty(PsiModifier.PRIVATE) || !field.hasModifierProperty(PsiModifier.FINAL) ||
                    field.hasModifierProperty(PsiModifier.STATIC) || field.initializer != null || field in state.fields || component(field.type)) unsupported()
                state.fields[field] = helperValue(expression.rExpression ?: unsupported(), context)
                return
            }
            if (field.containingClass != context.owner || !rootReference(reference, context) ||
                !field.hasModifierProperty(PsiModifier.PRIVATE) || !field.hasModifierProperty(PsiModifier.FINAL) ||
                field.hasModifierProperty(PsiModifier.STATIC) || field.initializer != null || field in fields) unsupported()
            val value = expression.rExpression ?: unsupported()
            fields[field] = if (component(field.type)) fresh(value, context)
                else if (scalarType(field.type)) helperValue(value, context)
                else freshHelper(value, context)
            fieldReads += reference
        }

        private fun argument(expression: PsiExpression, context: Frame): Value = evaluate(expression, context).also {
            if (it == Root || it == UnitValue) unsupported()
        }

        private fun evaluate(expression: PsiExpression, context: Frame): Value {
            tick()
            return when (val value = unwrap(expression)) {
                is PsiConditionalExpression -> evaluate(choice(value, context), context)
                is PsiPrefixExpression -> {
                    choiceDepth(value)
                    if (value.operationTokenType == JavaTokenType.EXCL) {
                        featureUsed = true
                        val operand = booleanRead(value.operand ?: unsupported(), context)
                        if (operand is BooleanSymbol) operand.copy(negated = !operand.negated)
                        else if (operand is BooleanGroup) operand.copy(negated = !operand.negated)
                        else knownBoolean(operand)?.let { Truth(!it) } ?: Unknown
                    } else numericPrefix(value, context)
                }
                is PsiPolyadicExpression -> if (value.operationTokenType in setOf(JavaTokenType.ANDAND, JavaTokenType.OROR)) logical(value, context) else calculation(value, context)
                is PsiTypeCastExpression -> cast(value, context)
                is PsiLiteralExpression -> {
                    if (!WicketScalarExpressions.validIntegralLiteral(value)) unsupported()
                    Literal(value.value, value)
                }
                is PsiReferenceExpression -> {
                    val variable = value.resolve() as? PsiVariable ?: unsupported()
                    if (variable is PsiField) {
                        if (variable.hasModifierProperty(PsiModifier.STATIC)) {
                            val constant = constants.resolve(value) ?: unsupported()
                            featureUsed = true
                            return if (constant is Boolean) Truth(constant) else Computed(constant, value)
                        }
                        if (!variable.hasModifierProperty(PsiModifier.PRIVATE) || !variable.hasModifierProperty(PsiModifier.FINAL) ||
                            variable.hasModifierProperty(PsiModifier.STATIC)) readEffects++
                        if (context.helperReceiver != null && variable.containingClass == context.owner && helperReference(value) &&
                            !variable.hasModifierProperty(PsiModifier.STATIC)) {
                            return helperStates[context.helperReceiver]?.fields?.get(variable) ?: unsupported()
                        }
                        if (fields[variable] == Data && !component(variable.type) && variable.hasModifierProperty(PsiModifier.FINAL) &&
                            (value.qualifierExpression == null || value.qualifierExpression is PsiThisExpression)) return Data
                        if (variable.containingClass !in hierarchy || !rootReference(value, context)) unsupported()
                        if ((component(variable.type) || fields[variable] is Helper) && (!variable.hasModifierProperty(PsiModifier.PRIVATE) ||
                                !variable.hasModifierProperty(PsiModifier.FINAL) || variable.hasModifierProperty(PsiModifier.STATIC))) unsupported()
                        fieldReads += value
                        fields[variable] ?: unsupported()
                    } else context.values[variable] ?: unsupported()
                }
                is PsiNewExpression -> {
                    readEffects++
                    if (component(value.type)) create(value, context) else helper(value, context)
                }
                is PsiMethodCallExpression -> {
                    val method = value.resolveMethod()
                    if (context.templateRoot != null && method?.name in setOf("getModel", "getModelObject", "getDefaultModel", "getDefaultModelObject") &&
                        method?.containingClass?.qualifiedName in setOf(COMPONENT, "org.apache.wicket.IGenericComponent")) data(value, context)
                    else invoke(value, context)
                }
                is PsiThisExpression -> {
                    if (context.helperReceiver != null && value.qualifier == null) return context.helperReceiver
                    if (context.receiver != null && value.qualifier == null) return Instance(context.receiver)
                    if (!context.rootReceiver || value.qualifier?.resolve()?.let { it != context.owner } == true) unsupported()
                    Root
                }
                is PsiSuperExpression -> {
                    if (value.qualifier != null && !interfaceSuper(value, context)) unsupported()
                    if (context.helperReceiver != null) return context.helperReceiver
                    if (!context.rootReceiver) unsupported()
                    Root
                }
                else -> unsupported()
            }
        }

        private fun rootReference(reference: PsiReferenceExpression, context: Frame): Boolean =
            context.rootReceiver && (reference.qualifierExpression == null ||
                reference.qualifierExpression?.let { evaluate(it, context) } == Root)

        private fun helperReference(reference: PsiReferenceExpression): Boolean = reference.qualifierExpression?.let {
            val qualifier = unwrap(it)
            qualifier is PsiThisExpression && qualifier.qualifier == null
        } ?: true

        private fun data(expression: PsiExpression, context: Frame): Value {
            tick()
            return when (val value = unwrap(expression)) {
                is PsiConditionalExpression -> data(choice(value, context), context)
                is PsiPrefixExpression, is PsiPolyadicExpression -> evaluate(value, context)
                is PsiLiteralExpression -> Literal(value.value, value)
                is PsiReferenceExpression -> evaluate(value, context).let {
                    if (it is Instance || it is Helper || it == Root || it == UnitValue) unsupported()
                    if (it == Unknown) Data else it
                }
                is PsiTypeCastExpression -> {
                    if (WicketScalarExpressions.integral(value.type)) return cast(value, context)
                    if (component(value.castType?.type)) unsupported()
                    data(value.operand ?: unsupported(), context)
                }
                is PsiNewExpression -> {
                    if (value.anonymousClass != null || !value.resolveMethodGenerics().isValidResult ||
                        (value.classReference?.resolve() as? PsiClass)?.qualifiedName !in setOf("org.apache.wicket.model.Model", "org.apache.wicket.model.ListModel")) unsupported()
                    value.argumentList?.expressions.orEmpty().forEach { data(it, context) }
                    Data
                }
                is PsiMethodCallExpression -> {
                    if (!value.resolveMethodGenerics().isValidResult) unsupported()
                    val method = value.resolveMethod() ?: unsupported()
                    val owner = method.containingClass?.qualifiedName
                    val qualifier = value.methodExpression.qualifierExpression
                    if (method !is PsiCompiledElement && scalarType(value.type) || owner == "java.lang.String" && method.name == "equals") return invoke(value, context)
                    if (method.hasModifierProperty(PsiModifier.STATIC)) {
                        if (qualifier != null && (qualifier as? PsiReferenceExpression)?.resolve() !is PsiClass) unsupported()
                        val allowed = when (owner) {
                            "java.util.List" -> method.name == "of"
                            "java.util.Arrays" -> method.name == "asList"
                            "java.util.Collections" -> method.name in setOf("emptyList", "singletonList")
                            "org.apache.wicket.model.Model" -> method.name in setOf("of", "ofList")
                            else -> false
                        }
                        if (!allowed) unsupported()
                        value.argumentList.expressions.forEach { data(it, context) }
                        return Data
                    }
                    if (method.name == "newChildId" && owner == WicketRepeaterContract.REPEATING) return invoke(value, context)
                    val getter = method.name in setOf("getModel", "getModelObject", "getDefaultModel", "getDefaultModelObject") &&
                        owner in setOf(COMPONENT, "org.apache.wicket.IGenericComponent", "org.apache.wicket.model.IModel") ||
                        method.name == "getObject" && owner == "org.apache.wicket.model.IModel"
                    if (getter && value.argumentList.expressionCount == 0) {
                        if (qualifier != null) {
                            val receiver = if ((qualifier as? PsiReferenceExpression)?.resolve() is PsiVariable &&
                                component((qualifier.resolve() as PsiVariable).type)) evaluate(qualifier, context)
                                else if (qualifier is PsiThisExpression) evaluate(qualifier, context) else data(qualifier, context)
                            if (receiver == UnitValue || receiver is ChildId) unsupported()
                        } else if (!context.rootReceiver && context.receiver == null) unsupported()
                        return Data
                    }
                    if (context.templateRoot == null || qualifier == null || component(method.returnType)) unsupported()
                    val receiver = data(qualifier, context)
                    if (receiver is ChildId) unsupported()
                    itemData(value, context)
                }
                else -> unsupported()
            }
        }

        private fun itemData(call: PsiMethodCallExpression, context: Frame): Value {
            readEffects++
            call.argumentList.expressions.forEach { data(it, context) }
            warnings += Diagnostic(DiagnosticCode.UNRESOLVED_SCOPE,
                "Unreviewed item-data call '${call.methodExpression.referenceName}': template bindings are observed, but model/runtime effects are not expanded.", location(call))
            return Data
        }

        private fun populate(type: PsiClass, repetition: Repetition, view: String, expression: PsiNewExpression, parent: Frame) {
            var owner: PsiClass? = type
            var callback: PsiMethod? = null
            val seen = mutableSetOf<PsiClass>()
            val boundary = if (repetition == Repetition.LIST_VIEW) WicketRepeaterContract.LIST else WicketRepeaterContract.REPEATING
            while (owner != null && owner.qualifiedName != boundary) {
                tick()
                if (!seen.add(owner) || seen.size > 12 || owner is PsiCompiledElement || !WicketRepeaterContract.accepts(owner, repetition)) unsupported()
                source(owner)
                if (callback == null) callback = owner.methods.singleOrNull { WicketRepeaterContract.callback(it, repetition) }
                owner = owner.superClass
            }
            if (callback == null) {
                if (repetition == Repetition.LIST_VIEW) unsupported()
                return
            }
            val method = callback
            if (method.body == null || !stack.add(method) || observed.size + helperAllocations >= 256) unsupported()
            try {
                source(method)
                val item = if (repetition == Repetition.LIST_VIEW) "java:template:$view:item" else view
                if (repetition == Repetition.LIST_VIEW) {
                    observed[item] = Observed(ComponentNode(item, null, WicketRepeaterContract.ITEM, view,
                        location(method.parameterList.parameters.single().nameIdentifier ?: method), confidence = Confidence.CONFIRMED,
                        idRenameSafe = false, templateItem = true, template = true), parent.path,
                        parents = mutableListOf(view))
                }
                val context = frame(method.containingClass!!, method,
                    if (repetition == Repetition.LIST_VIEW) listOf(Instance(item)) else emptyList(), parent.path, root = false)
                val values = parent.values.toMutableMap().apply { putAll(context.values) }
                execute(method.body!!.statements.toList(), context.copy(values = values, receiver = view,
                    templateRoot = item, templatePath = context.path))
                warnings += Diagnostic(DiagnosticCode.PARTIAL_ANALYSIS,
                    "Repeater items are static templates; runtime item IDs and row counts are not inferred.", location(expression))
            } finally { stack.remove(method) }
        }

        private fun foreach(statement: PsiForeachStatement, context: Frame) {
            tick()
            val body = (statement.body as? PsiBlockStatement)?.codeBlock ?: unsupported()
            val calls = PsiTreeUtil.findChildrenOfType(body, PsiMethodCallExpression::class.java).filter {
                it.methodExpression.referenceName == "newChildId" &&
                    PsiTreeUtil.getParentOfType(it, PsiClass::class.java) == context.owner &&
                    it.resolveMethod()?.containingClass?.qualifiedName == WicketRepeaterContract.REPEATING
            }
            if (calls.isEmpty()) unsupported()
            val views = calls.map { call ->
                val qualifier = call.methodExpression.qualifierExpression
                val receiver = if (qualifier == null) context.receiver?.let(::Instance) else {
                    if (qualifier !is PsiReferenceExpression && qualifier !is PsiThisExpression) unsupported()
                    evaluate(qualifier, context)
                }
                (receiver as? Instance)?.id ?: unsupported()
            }.distinct()
            if (views.size != 1 || ++invocations > 128) unsupported()
            val view = views.single()
            if (context.templateRoot != null && view != context.templateRoot &&
                observed[view]?.framePath?.take(context.templatePath.size) != context.templatePath) unsupported()
            data(statement.iteratedValue ?: unsupported(), context)
            val values = context.values.toMutableMap().apply { put(statement.iterationParameter, Data) }
            val path = context.path + invocations
            execute(body.statements.toList(), context.copy(values = values, path = path, templateRoot = view, templatePath = path, allowReturn = false))
            featureUsed = true
            warnings += Diagnostic(DiagnosticCode.PARTIAL_ANALYSIS,
                "A foreach repeater body is analyzed once as an item template; iteration count is not inferred.", location(statement))
        }

        private fun create(expression: PsiNewExpression, context: Frame): Value {
            val resolvedType = expression.anonymousClass ?: expression.classReference?.resolve() as? PsiClass ?: unsupported()
            val repetition = WicketRepeaterContract.kind(resolvedType)
            if (expression.anonymousClass != null && (repetition == null || !WicketRepeaterContract.accepts(resolvedType, repetition)) || observed.size + helperAllocations >= 256) unsupported()
            val trace = WicketIdConstructors.trace(expression) ?: unsupported()
            if (trace.inlineChildren) unsupported()
            sources += trace.sources
            if (sources.size > 24) unsupported()
            val args = expression.argumentList?.expressions.orEmpty()
            noUnboxing(expression.resolveMethod(), args)
            val values = args.mapIndexed { index, argument ->
                if (index != 0 && (repetition != null || context.templateRoot != null)) data(argument, context)
                else argument(argument, context).also { if (it is Instance || it is Helper) unsupported() }
            }
            // Constructor arguments run once, left to right. Tracing a forwarded
            // ID must reuse its argument value, including its selected ternary arm.
            val originIndex = args.indexOfFirst { unwrap(it) == trace.expression }
            val value = if (originIndex >= 0) values[originIndex] else evaluate(trace.expression, context)
            val id = value as? Literal
            val computed = value as? Computed
            if (id == null && computed == null && value != Unknown && value != Data && value !is ChildId) unsupported()
            val text = id?.value as? String ?: computed?.value as? String
            val origin = id?.origin ?: computed?.origin
            val generated = value as? ChildId
            if (generated != null && (expression.classReference?.resolve() as? PsiClass)?.qualifiedName != "org.apache.wicket.markup.html.WebMarkupContainer") unsupported()
            val diagnostics = when {
                generated != null -> emptyList()
                text == null -> listOf(Diagnostic(DiagnosticCode.UNRESOLVED_DYNAMIC_ID,
                    "The source setup forwards an ID without a known literal value.", location(trace.expression)))
                text.isEmpty() || text.any { it == ':' || it == '~' } -> listOf(Diagnostic(DiagnosticCode.UNRESOLVED_ID,
                    "The component ID is empty or contains reserved path characters.", origin?.let(::location) ?: location(trace.expression)))
                else -> emptyList()
            }
            val type = (expression.anonymousClass?.baseClassType?.resolve() ?: resolvedType).qualifiedName ?: unsupported()
            if (!WicketIdConstructors.isReviewed(expression)) featureUsed = true
            val nodeId = "java:source:${location(expression).file}:${expression.textOffset}:${++sequence}"
            val originMethod = id?.origin?.let { PsiTreeUtil.getParentOfType(it, PsiMethod::class.java) }
            val originField = id?.origin?.let { PsiTreeUtil.getParentOfType(it, PsiField::class.java) }
            val ownSetup = originMethod?.containingClass == page && (originMethod.isConstructor || isHook(originMethod))
            val ownField = originField?.containingClass == page && (component(originField.type) || fields[originField] is Helper) &&
                originField.hasModifierProperty(PsiModifier.PRIVATE) && originField.hasModifierProperty(PsiModifier.FINAL) &&
                !originField.hasModifierProperty(PsiModifier.STATIC)
            val ownTemplate = context.templateRoot != null && id?.origin?.let { PsiTreeUtil.isAncestor(page, it, true) } == true
            val renameSafe = generated == null && diagnostics.isEmpty() && !trace.shared && (ownSetup || ownField || ownTemplate)
            observed[nodeId] = Observed(ComponentNode(nodeId, text, type, null, location(expression),
                confidence = if (diagnostics.isEmpty()) Confidence.CONFIRMED else Confidence.UNRESOLVED,
                diagnostics = diagnostics, idLocation = origin?.let(::location), idRenameSafe = renameSafe, computedId = computed != null,
                repetition = repetition, templateItem = generated != null, template = context.templateRoot != null || generated != null), context.path)
            if (generated != null) generatedItems[nodeId] = generated.view
            if (repetition != null) {
                featureUsed = true
                populate(resolvedType, repetition, nodeId, expression, context)
            }
            return Instance(nodeId)
        }

        private fun invoke(call: PsiMethodCallExpression, context: Frame): Value {
            tick()
            if (!call.resolveMethodGenerics().isValidResult) unsupported()
            val declaration = call.resolveMethod() ?: unsupported()
            val name = call.methodExpression.referenceName
            if (name in setOf("super", "this", "onInitialize")) unsupported()
            val qualifier = call.methodExpression.qualifierExpression
            val static = declaration.hasModifierProperty(PsiModifier.STATIC)
            noUnboxing(declaration, call.argumentList.expressions)
            val receiver = if (static) {
                // A class qualifier has no runtime receiver. Do not ignore side effects in an expression qualifier.
                if (qualifier != null && (qualifier as? PsiReferenceExpression)?.resolve() !is PsiClass) unsupported()
                Unknown
            } else qualifier?.let { evaluate(it, context) } ?: context.helperReceiver ?: context.receiver?.let(::Instance) ?: if (context.rootReceiver) Root else unsupported()
            if (receiver is Helper && receiver.type !in (helperStates[receiver]?.initialized ?: unsupported())) unsupported()
            // Runtime item data has no proven implementation, even when source is
            // available for its declared type. Preserve the opaque template contract.
            if (!static && context.templateRoot != null && qualifier != null && (receiver == Data || receiver == Unknown) &&
                !component(call.type) && call.type?.canonicalText != "void") return itemData(call, context)
            if (!static && declaration.containingClass?.qualifiedName == "java.lang.String" && declaration.name == "equals" && call.argumentList.expressionCount == 1) {
                featureUsed = true
                val text = when (receiver) {
                    is Literal -> {
                        if (!immutableOrigin(receiver)) unsupported()
                        computedInputs += location(receiver.origin)
                        receiver.value as? String ?: unsupported()
                    }
                    is Computed -> receiver.value as? String ?: unsupported()
                    else -> unsupported() // An unknown receiver could be null.
                }
                val other = scalar(call.argumentList.expressions.single(), context) ?: return Unknown
                return Truth(text == other)
            }
            if (declaration.name == "newChildId" && declaration.containingClass?.qualifiedName == WicketRepeaterContract.REPEATING &&
                call.argumentList.expressionCount == 0 && receiver is Instance && observed[receiver.id]?.node?.repetition == Repetition.REPEATING_VIEW) return ChildId(receiver.id)
            if (WicketFormComponentContract.configuration(declaration) &&
                (receiver == Root || receiver is Instance) && call.argumentList.expressions.all {
                    it is PsiLiteralExpression || it is PsiClassObjectAccessExpression
                }) {
                readEffects++
                return receiver
            }
            val args = call.argumentList.expressions.map { argument(it, context) }
            if (wicketAdd(declaration) || wicketQueue(declaration)) {
                readEffects++
                val parent = when (receiver) {
                    Root -> rootId
                    is Instance -> receiver.id.also { observed[it]?.used = true }
                    else -> unsupported()
                }
                if (context.templateRoot != null && parent != context.templateRoot &&
                    observed[parent]?.framePath?.take(context.templatePath.size) != context.templatePath) unsupported()
                args.forEach { argument ->
                    val child = argument as? Instance ?: unsupported()
                    val observedChild = observed[child.id] ?: unsupported()
                    if (wicketQueue(declaration)) {
                        observedChild.queues += parent
                        featureUsed = true
                    } else observedChild.parents += parent
                }
                return receiver
            }
            val superCall = qualifier is PsiSuperExpression && (qualifier.qualifier == null || interfaceSuper(qualifier, context))
            if (declaration is PsiCompiledElement) unsupported()
            source(declaration)
            val method = if (static || superCall || declaration.hasModifierProperty(PsiModifier.PRIVATE) || declaration.hasModifierProperty(PsiModifier.FINAL)) declaration
                else when (receiver) {
                    Root -> virtualTarget(declaration, page)
                    is Helper -> virtualTarget(declaration, receiver.type)
                    else -> declaration
                }
            val owner = method.containingClass ?: unsupported()
            val closed = static || method.hasModifierProperty(PsiModifier.PRIVATE) || method.hasModifierProperty(PsiModifier.FINAL) ||
                owner.hasModifierProperty(PsiModifier.FINAL) || superCall || receiver is Helper ||
                receiver == Root && page.hasModifierProperty(PsiModifier.FINAL)
            val ownReceiver = receiver == Root && (owner in hierarchy || owner.isInterface && owner.qualifiedName?.let { InheritanceUtil.isInheritor(page, it) } == true) ||
                receiver is Instance && receiver.id == context.receiver && owner == context.owner ||
                receiver is Helper && (owner == receiver.type || owner.qualifiedName?.let { InheritanceUtil.isInheritor(receiver.type, it) } == true)
            if (!closed || method.body == null || method is PsiCompiledElement || !static && !ownReceiver ||
                method.isConstructor || method.hasModifierProperty(PsiModifier.ABSTRACT) || !stack.add(method)) unsupported()
            try {
                // Guard bridge casts when generic dispatch changes the parameter signature.
                if (method != declaration) {
                    val runtime = when (receiver) { Root -> page; is Helper -> receiver.type; else -> null }
                    val substitutor = runtime?.let { TypeConversionUtil.getClassSubstitutor(owner, it, PsiSubstitutor.EMPTY) } ?: PsiSubstitutor.EMPTY
                    if (method.parameterList.parametersCount != call.argumentList.expressionCount ||
                        method.parameterList.parameters.zip(call.argumentList.expressions).any { (parameter, expression) ->
                            expression.type?.let { !TypeConversionUtil.isAssignable(substitutor.substitute(parameter.type) ?: unsupported(), it) } != false
                        }) unsupported()
                    if (runtime != null && declaration.containingClass?.isInterface == true) {
                        val expected = TypeConversionUtil.getClassSubstitutor(declaration.containingClass!!, runtime, PsiSubstitutor.EMPTY)
                            ?.substitute(declaration.returnType) ?: unsupported()
                        val actual = substitutor.substitute(method.returnType) ?: unsupported()
                        if (expected != actual && !TypeConversionUtil.isAssignable(expected, actual)) unsupported()
                    }
                }
                if (!static && !superCall && !declaration.hasModifierProperty(PsiModifier.PRIVATE)) shadowedMethods += superDeclarations(method)
                source(method)
                if (static) validateStaticInitialization(owner)
                methods += method
                featureUsed = true
                val invoked = frame(owner, method, args, context.path, !static && receiver == Root, context.initialization).copy(
                    receiver = if (!static) (receiver as? Instance)?.id else null,
                    templateRoot = context.templateRoot, templatePath = context.templatePath,
                    helperReceiver = if (!static) receiver as? Helper else null)
                val runtime = when (receiver) { Root -> page; is Helper -> receiver.type; else -> null }
                val substitution = if (method == declaration) call.resolveMethodGenerics().substitutor
                    else runtime?.let { TypeConversionUtil.getClassSubstitutor(owner, it, PsiSubstitutor.EMPTY) } ?: PsiSubstitutor.EMPTY
                val returnedType = substitution.substitute(method.returnType) ?: unsupported()
                val factory = when {
                    returnedType.canonicalText == "void" -> null
                    component(returnedType) -> ReturnContract(ReturnKind.COMPONENT, returnedType, substitution, readEffects)
                    providerType(returnedType) -> ReturnContract(ReturnKind.PROVIDER, returnedType, substitution, readEffects)
                    scalarType(returnedType) -> ReturnContract(ReturnKind.SCALAR, returnedType, substitution, readEffects)
                    else -> unsupported()
                }
                return execute(method.body!!.statements.toList(), invoked, factory)
            } finally { stack.remove(method) }
        }

        private fun helper(expression: PsiNewExpression, context: Frame): Helper {
            tick()
            val type = expression.classReference?.resolve() as? PsiClass ?: unsupported()
            val expressions = expression.argumentList?.expressions ?: unsupported()
            noUnboxing(expression.resolveMethod(), expressions)
            if (expression.anonymousClass != null ||
                type.isInterface || type.isEnum || type.isRecord || type.hasModifierProperty(PsiModifier.ABSTRACT) ||
                type.qualifiedName == null || type.containingClass != null && !type.hasModifierProperty(PsiModifier.STATIC) ||
                type.constructors.isNotEmpty() && !expression.resolveMethodGenerics().isValidResult ||
                ++helperAllocations + observed.size > 256) unsupported()
            validateHelper(type, mutableSetOf(), 0)
            val receiver = Helper(type, helperAllocations)
            val arguments = expressions.map { helperValue(it, context) }
            helperStates[receiver] = HelperState(context.path, expression.type ?: unsupported())
            helperConstructor(receiver, type, expression.resolveMethod(), arguments, context.path)
            featureUsed = true
            return receiver
        }

        private fun helperValue(expression: PsiExpression, context: Frame): Value {
            val value = evaluate(expression, context)
            if (value is Literal) {
                if (!immutableOrigin(value)) unsupported()
                return value
            }
            if ((value is Truth || value is BooleanSymbol || value is BooleanGroup) && expression.type?.canonicalText == "boolean") return value
            if (value is IntegralSymbol && WicketScalarExpressions.integral(expression.type)) return value
            if (value is Computed) return value
            if (value is Helper || value == Unknown &&
                (expression.type is PsiPrimitiveType || expression.type?.equalsToText("java.lang.String") == true)) return value
            unsupported()
        }

        private fun helperConstructor(receiver: Helper, owner: PsiClass, selected: PsiMethod?, arguments: List<Value>, path: List<Int>) {
            tick()
            if (owner.qualifiedName == "java.lang.Object") {
                if (arguments.isNotEmpty()) unsupported()
                return
            }
            val state = helperStates[receiver] ?: unsupported()
            val method = selected.takeUnless { owner.constructors.isEmpty() }
            if (owner in state.initialized || method == null && owner.constructors.isNotEmpty() ||
                method != null && (method.containingClass != owner || !stack.add(method))) unsupported()
            try {
                val context = frame(owner, method, arguments, path, root = false).copy(helperReceiver = receiver)
                val statements = method?.body?.statements?.filterNot { it is PsiEmptyStatement }.orEmpty()
                if (method != null && method.body == null) unsupported()
                val first = (statements.firstOrNull() as? PsiExpressionStatement)?.expression as? PsiMethodCallExpression
                val delegation = first?.takeIf { it.methodExpression.referenceName in setOf("this", "super") }
                if (delegation?.methodExpression?.qualifierExpression != null) unsupported()
                // Delegation arguments may use parameters, but not the unfinished receiver.
                val beforeDelegation = context.copy(helperReceiver = null)
                if (delegation?.methodExpression?.referenceName == "this") {
                    if (!delegation.resolveMethodGenerics().isValidResult) unsupported()
                    noUnboxing(delegation.resolveMethod(), delegation.argumentList.expressions)
                    val values = delegation.argumentList.expressions.map { helperValue(it, beforeDelegation) }
                    if (values.any { it == receiver }) unsupported()
                    helperConstructor(receiver, owner, delegation.resolveMethod(), values, context.path)
                } else {
                    val parent = owner.superClass ?: unsupported()
                    val values = delegation?.argumentList?.expressions.orEmpty().map { helperValue(it, beforeDelegation) }
                    if (values.any { it == receiver }) unsupported()
                    val implicit = parent.constructors.isEmpty() && values.isEmpty() && parent !is PsiCompiledElement
                    val target = if (delegation != null) {
                        if (!implicit && !delegation.resolveMethodGenerics().isValidResult) unsupported()
                        delegation.resolveMethod().also { if (it?.containingClass != parent && !implicit) unsupported() }
                    } else parent.constructors.singleOrNull { it.parameterList.parametersCount == 0 }.also {
                        if (it == null && parent.constructors.isNotEmpty()) unsupported()
                    }
                    noUnboxing(target, delegation?.argumentList?.expressions.orEmpty())
                    helperConstructor(receiver, parent, target, values, context.path)
                    owner.fields.filterNot { it.hasModifierProperty(PsiModifier.STATIC) }.forEach { field ->
                        tick()
                        field.initializer?.let {
                            scalarAssignment(field.type, it)
                            state.fields[field] = helperValue(it, context)
                        }
                    }
                }
                statements.drop(if (delegation == null) 0 else 1).forEach { statement ->
                    tick()
                    val assignment = (statement as? PsiExpressionStatement)?.expression as? PsiAssignmentExpression ?: unsupported()
                    assign(assignment, context)
                }
                if (owner.fields.any { !it.hasModifierProperty(PsiModifier.STATIC) && it !in state.fields }) unsupported()
                state.initialized += owner
            } finally { method?.let(stack::remove) }
        }

        private fun validateHelper(type: PsiClass, visiting: MutableSet<PsiClass>, depth: Int) {
            tick()
            if (type.qualifiedName == "java.lang.Object" || type in helperTypes) return
            if (depth >= 12 || !visiting.add(type) || type is PsiCompiledElement) unsupported()
            source(type)
            if (type.initializers.isNotEmpty() || type.fields.any {
                    if (it.hasModifierProperty(PsiModifier.STATIC)) !safeStaticInitializer(it)
                    else !it.hasModifierProperty(PsiModifier.PRIVATE) || !it.hasModifierProperty(PsiModifier.FINAL) || component(it.type)
                }) unsupported()
            type.interfaces.forEach { validateHelper(it, visiting, depth + 1) }
            if (!type.isInterface) {
                validateHelper(type.superClass ?: unsupported(), visiting, depth + 1)
            }
            visiting.remove(type)
            helperTypes += type
        }

        private fun superDeclarations(method: PsiMethod): Set<PsiMethod> {
            val seen = mutableSetOf<PsiMethod>()
            fun visit(current: PsiMethod, depth: Int) {
                tick()
                if (depth >= 12) unsupported()
                current.findSuperMethods().forEach { parent ->
                    if (parent is PsiCompiledElement) unsupported()
                    source(parent)
                    if (seen.add(parent)) visit(parent, depth + 1)
                }
            }
            visit(method, 0)
            return seen
        }

        private fun virtualTarget(declaration: PsiMethod, runtime: PsiClass): PsiMethod {
            var type: PsiClass? = runtime
            val seen = mutableSetOf<PsiClass>()
            while (type != null && type.qualifiedName !in ROOTS + "java.lang.Object") {
                tick()
                if (!seen.add(type) || seen.size > 12 || type is PsiCompiledElement) unsupported()
                source(type)
                val candidates = type.methods.filter { candidate -> !candidate.hasModifierProperty(PsiModifier.STATIC) &&
                    !candidate.hasModifierProperty(PsiModifier.PRIVATE) && candidate.name == declaration.name &&
                    (candidate == declaration || declaration in superDeclarations(candidate) ||
                        declaration.containingClass?.isInterface == true && sameSignature(declaration, candidate, runtime)) }
                if (candidates.size > 1) unsupported()
                candidates.singleOrNull()?.let {
                    if (declaration.containingClass?.isInterface == true && !it.hasModifierProperty(PsiModifier.PUBLIC)) unsupported()
                    return it
                }
                type = type.superClass
            }
            val candidates = sourceInterfaces(runtime).flatMap { it.methods.toList() }.filter {
                !it.hasModifierProperty(PsiModifier.STATIC) && !it.hasModifierProperty(PsiModifier.PRIVATE) &&
                    it.name == declaration.name && sameSignature(declaration, it, runtime)
            }.distinct()
            val maximal = candidates.filter { parent -> candidates.none { child ->
                child != parent && child.containingClass != parent.containingClass &&
                    parent.containingClass?.qualifiedName?.let { InheritanceUtil.isInheritor(child.containingClass!!, it) } == true
            } }
            val selected = maximal.singleOrNull() ?: unsupported()
            if (!selected.hasModifierProperty(PsiModifier.DEFAULT) || selected.body == null) unsupported()
            return selected
        }

        private fun sameSignature(declaration: PsiMethod, candidate: PsiMethod, runtime: PsiClass): Boolean {
            tick()
            val original = declaration.containingClass ?: unsupported()
            val owner = candidate.containingClass ?: unsupported()
            val originalSubstitution = TypeConversionUtil.getClassSubstitutor(original, runtime, PsiSubstitutor.EMPTY) ?: return false
            val candidateSubstitution = TypeConversionUtil.getClassSubstitutor(owner, runtime, PsiSubstitutor.EMPTY) ?: return false
            return MethodSignatureUtil.isSubsignature(declaration.getSignature(originalSubstitution), candidate.getSignature(candidateSubstitution))
        }

        private fun sourceInterfaces(runtime: PsiClass): Set<PsiClass> {
            val interfaces = linkedSetOf<PsiClass>()
            fun visit(type: PsiClass, depth: Int) {
                tick()
                if (depth >= 12 || type is PsiCompiledElement) unsupported()
                if (!interfaces.add(type)) return
                validateHelper(type, mutableSetOf(), 0)
                type.interfaces.forEach { visit(it, depth + 1) }
            }
            var type: PsiClass? = runtime
            val classes = mutableSetOf<PsiClass>()
            while (type != null && type.qualifiedName !in ROOTS + "java.lang.Object") {
                tick()
                if (!classes.add(type) || classes.size > 12 || type is PsiCompiledElement) unsupported()
                source(type)
                type.interfaces.forEach { visit(it, classes.size) }
                type = type.superClass
            }
            return interfaces
        }

        private fun hasDefaults(type: PsiClass, seen: MutableSet<PsiClass>, depth: Int): Boolean {
            tick()
            if (depth >= 12) unsupported()
            if (!seen.add(type)) return false
            return type.methods.any { it.hasModifierProperty(PsiModifier.DEFAULT) } || type.interfaces.any { hasDefaults(it, seen, depth + 1) }
        }

        private fun interfaceSuper(expression: PsiSuperExpression, context: Frame): Boolean {
            val selected = expression.qualifier?.resolve() as? PsiClass ?: return false
            if (!selected.isInterface || selected !in context.owner.interfaces ||
                context.owner.interfaces.any { it != selected && selected.qualifiedName?.let { name -> InheritanceUtil.isInheritor(it, name) } == true } ||
                context.owner.superClass?.let { parent -> selected.qualifiedName?.let { InheritanceUtil.isInheritor(parent, it) } } == true) return false
            validateHelper(selected, mutableSetOf(), 0)
            return true
        }

        private fun safeStaticInitializer(field: PsiField): Boolean {
            val initializer = field.initializer ?: return true
            if (unwrap(initializer) is PsiLiteralExpression) return !scalarType(field.type) ||
                TypeConversionUtil.areTypesAssignmentCompatible(field.type, initializer)
            return constants.value(field) != null
        }

        private fun validateStaticInitialization(type: PsiClass) {
            if (type.isInterface) {
                validateHelper(type, mutableSetOf(), 0)
                return
            }
            val seen = mutableSetOf<PsiClass>()
            var owner: PsiClass? = type
            while (owner?.qualifiedName !in ROOTS + "java.lang.Object") {
                tick()
                if (owner == null || owner is PsiCompiledElement || !seen.add(owner) || seen.size > 12) unsupported()
                source(owner)
                owner.interfaces.forEach { validateHelper(it, mutableSetOf(), 0) }
                if (owner.initializers.any { it.hasModifierProperty(PsiModifier.STATIC) } || owner.fields.any {
                        it.hasModifierProperty(PsiModifier.STATIC) && !safeStaticInitializer(it)
                    }) unsupported()
                owner = owner.superClass
            }
        }

        private fun validateUnexecutedMembers() {
            hierarchy.forEach { owner ->
                owner.methods.filterNot { it.isConstructor }.forEach methodLoop@ { method ->
                    tick()
                    if (method.name == "onInitialize" && method.parameterList.parametersCount == 0 && !isHook(method)) unsupported()
                    if (method in methods || method in shadowedMethods || method.hasModifierProperty(PsiModifier.PRIVATE) || method.hasModifierProperty(PsiModifier.STATIC)) return@methodLoop
                    if (WicketComponentTypes.isFormComponent(page) && WicketFormComponentContract.valueCallback(method)) return@methodLoop
                    // Public/protected callbacks and lifecycle effects outside the traced setup stay unresolved.
                    if (method.body?.statements?.isNotEmpty() != false) unsupported()
                }
            }
        }

        private fun validateFieldReferences() {
            val tracked = fields.keys.filter { component(it.type) || fields[it] is Helper }.toSet()
            hierarchy.map { it.containingFile }.distinct().forEach { file ->
                file.accept(object : JavaRecursiveElementWalkingVisitor() {
                    override fun visitReferenceExpression(expression: PsiReferenceExpression) {
                        tick()
                        if (expression.resolve() in tracked && expression !in fieldReads &&
                            generateSequence<PsiElement>(expression) { it.parent }.none { it in skippedRanges }) {
                            val method = PsiTreeUtil.getParentOfType(expression, PsiMethod::class.java)
                            if (!WicketComponentTypes.isFormComponent(page) || method == null ||
                                !WicketFormComponentContract.valueCallback(method)) unsupported()
                        }
                        super.visitReferenceExpression(expression)
                    }
                })
            }
        }
    }

    private fun component(type: PsiType?): Boolean = (type as? PsiClassType)?.resolve()?.let {
        it.qualifiedName == COMPONENT || InheritanceUtil.isInheritor(it, COMPONENT)
    } == true

    private fun wicketAdd(method: PsiMethod): Boolean {
        if (method.name != "add" || method.containingClass?.qualifiedName != "org.apache.wicket.MarkupContainer") return false
        val type = method.parameterList.parameters.singleOrNull()?.type as? PsiEllipsisType ?: return false
        return (type.componentType as? PsiClassType)?.resolve()?.qualifiedName == COMPONENT
    }

    private fun wicketQueue(method: PsiMethod): Boolean {
        if (method.name != "queue" || method.containingClass?.qualifiedName != "org.apache.wicket.MarkupContainer") return false
        return ((method.parameterList.parameters.singleOrNull()?.type as? PsiEllipsisType)?.componentType as? PsiClassType)
            ?.resolve()?.qualifiedName == COMPONENT
    }

    private fun unwrap(expression: PsiExpression): PsiExpression {
        var value = expression
        while (value is PsiParenthesizedExpression) value = value.expression ?: return value
        return value
    }

    private fun location(element: PsiElement) = SourceLocation(element.containingFile.virtualFile?.path ?: element.containingFile.name, element.textOffset)

    private companion object {
        const val COMPONENT = "org.apache.wicket.Component"
        val ROOTS = WicketComponentTypes.markupRoots
        val FRAMEWORK_INITIALIZERS = ROOTS + setOf(COMPONENT, "org.apache.wicket.MarkupContainer", "org.apache.wicket.Page", "org.apache.wicket.markup.html.WebMarkupContainer")
    }
}
