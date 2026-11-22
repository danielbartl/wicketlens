package dev.wicketlens.analysis

import com.intellij.openapi.progress.ProgressManager
import com.intellij.psi.*
import com.intellij.psi.util.InheritanceUtil
import com.intellij.psi.util.PsiTreeUtil
import dev.wicketlens.model.*

/**
 * A second, bounded construction pass for source-decorated components. ID proof,
 * attachment and opaque component-owned contents are independent. The original
 * path/value interpreter remains authoritative for its supported subset.
 */
internal class WicketLayoutSetupAnalyzer(private val factories: Boolean = false, private val initialContentDeclarations: Boolean = false) {
    data class Result(val expansion: WicketSourceSetupAnalyzer.Expansion?, val diagnostics: List<Diagnostic>, val sources: List<String>)
    private class Stop(val at: PsiElement, override val message: String) : RuntimeException(null, null, false, false)
    private sealed interface Value
    private data class Scalar(val value: Any?, val origin: PsiExpression, val computed: Boolean = false) : Value
    private data class Ref(val id: String) : Value
    private data class ChildId(val view: Ref, val origin: PsiExpression) : Value
    private data class Factory(val expression: PsiExpression, val frame: Frame, val identity: Int) : Value
    private data class IteratorData(val type: PsiClass?) : Value
    private data class Data(val type: PsiClass? = null, val constructorArgument: Boolean = false, val callbackScopeUnknown: Boolean = false) : Value
    private data class Behavior(val type: PsiClass) : Value
    private data class EffectRef(val type: PsiClass, val known: Ref? = null, val fresh: Boolean = false, val borrowed: Boolean = false,
        val columns:PsiMethodCallExpression? = null)
    private data class EffectFrame(val owner: PsiClass, val receiver: EffectRef, val base: Frame,
        val locals: MutableMap<PsiVariable, EffectRef?> = mutableMapOf(), val enclosing: EffectFrame? = null, val construction: Boolean = false,
        val inputFactories: Map<PsiVariable, PsiMethodReferenceExpression> = emptyMap())
    private data object Unknown : Value
    private data object ReturnedVoid : Value
    private data class Frame(val receiver: Ref, val owner: PsiClass, val method: PsiMethod?, val values: MutableMap<PsiVariable, Value>)
    private data class Instance(val type: PsiClass, var node: ComponentNode?, val fields: MutableMap<PsiField, Value> = linkedMapOf(),
        val initialized: MutableSet<PsiClass> = linkedSetOf(), val enclosing: Ref? = null, val generatedBy: Ref? = null, val parents: MutableList<String> = mutableListOf(),
        val allocation:PsiNewExpression? = null)

    internal data class ConstructorEntry(val constructor: PsiMethod?, val tick: (Collection<String>) -> Unit)

    fun analyze(page: PsiClass, rootId: String, entry: ConstructorEntry? = null): Result {
        val session = Session(page, rootId, entry)
        return try {
            val expansion = session.run()
            Result(expansion, emptyList(), if (session.extended || entry != null) session.sources.toList() else emptyList())
        }
        catch (failure: Stop) {
            if (!session.extended && entry == null) Result(null, emptyList(), emptyList()) else
                Result(null, session.dataLoopDiagnostics + session.constructorContentDiagnostics + session.columnConstructionDiagnostics + Diagnostic(DiagnosticCode.UNRESOLVED_SCOPE,
                    "Source layout setup stopped: ${failure.message}", location(failure.at)), session.sources.toList())
        }
    }

    private inner class Session(private val page: PsiClass, private val rootId: String, private val entry: ConstructorEntry?) {
        val sources = linkedSetOf<String>()
        private val instances = linkedMapOf(rootId to Instance(page, null))
        private val stack = linkedSetOf<PsiMethod>()
        private val warnings = mutableListOf<Diagnostic>()
        private var steps = 0
        private var sequence = 0
        private var feature = false
        private var factoryCalls = 0
        private var factorySequence = 0
        private var templateDepth = 0
        private var symbolicLoops = 0
        val dataLoopDiagnostics = mutableListOf<Diagnostic>()
        val constructorContentDiagnostics = mutableListOf<Diagnostic>()
        val columnConstructionDiagnostics = mutableListOf<Diagnostic>()
        private val factoryGuards = mutableListOf<Set<String>>()
        private val templateGuards = mutableListOf<Set<String>>()
        private val activeFactories = mutableSetOf<PsiExpression>()
        private val validatedFactories = mutableSetOf<Int>()
        private val registeredItemCallbacks = mutableSetOf<PsiLambdaExpression>()
        private var isolatedForms = 0
        private val formEffects = mutableMapOf<String, Effects>()
        private val deferred = mutableListOf<Pair<PsiElement, Frame>>()
        private val contentHosts = linkedMapOf<String, WicketInitialContentDeclarations.Host>()
        private val contentSnapshots = linkedMapOf<String, InitialContentHost>()
        private val initialCallbacks = linkedSetOf<SourceLocation>()
        private val storedActions = mutableListOf<StoredActionRegistration>()
        private val storedActionDeclarations by lazy { WicketStoredActionDeclarations(page, ::tick, ::source, ::stop) { feature = true } }
        private val declarationFields = mutableSetOf<String>()
        private val contentDeclarations by lazy { WicketInitialContentDeclarations(page, ::tick, ::source, sources, ::stop) }
        val extended get() = feature
        private val audited = mutableSetOf<PsiClass>()
        private val initializedTypes = mutableSetOf<PsiClass>()
        private val auditedFields = mutableSetOf<PsiClass>()
        private var boundaryType: PsiClass? = null
        private val constants = WicketSourceConstants(::source) { tick(page) }
        private val columnConstruction by lazy { WicketColumnConstruction(::tick,::source,constants::expression,{graphType(it)},
            {element,owner -> auditDeferred(element,Frame(Ref(rootId),owner,null,mutableMapOf()))},::stop,
            {name -> JavaPsiFacade.getInstance(page.project).findClass(name,page.resolveScope)},
            {reference ->
                val diagnostic=Diagnostic(DiagnosticCode.PARTIAL_ANALYSIS,
                    "Reviewed JDK FormatStyle enum storage; formatting, locale, time-zone and model values remain unknown.",location(reference))
                if(diagnostic !in columnConstructionDiagnostics) {columnConstructionDiagnostics+=diagnostic;warnings+=diagnostic}
            }) }

        private val providerConstruction by lazy {WicketProviderConstruction(::tick,::source,constants::expression,columnConstruction::inspectInterface,
            {name -> JavaPsiFacade.getInstance(page.project).findClass(name,page.resolveScope)},::stop)}
        private val tableConstruction by lazy {WicketDataTableConstruction(::tick,::source,constants::expression,columnConstruction::inspectInterface,
            {name -> JavaPsiFacade.getInstance(page.project).findClass(name,page.resolveScope)},::stop)}
        private val tableToolbars by lazy {WicketDataTableToolbars(::tick,::source,constants::expression,columnConstruction::inspectInterface,::inspectTable,
            {name -> JavaPsiFacade.getInstance(page.project).findClass(name,page.resolveScope)},::stop)}
        private val toolbarProofs=mutableListOf<WicketDataTableToolbars.Proof>()
        private val ownedAccessors by lazy {WicketOwnedComponentAccessor(::tick,::source,columnConstruction::inspectInterface,::stop)}
        private val fluentConfigurations by lazy {WicketFluentComponentConfiguration(::tick,::source,constants::expression,::graphType,columnConstruction::inspectInterface,::stop)}
        private val ownedTableForwarding by lazy {WicketOwnedTableForwarding(::tick,::source,constants::expression,::inspectTable,
            columnConstruction::inspectInterface,tableToolbars::reviewedReference,tableToolbars::constructorReferences,tableToolbars::constructorAssignments,
            {name -> JavaPsiFacade.getInstance(page.project).findClass(name,page.resolveScope)},::stop)}
        private val tableColumnsRead by lazy {WicketTableColumnsRead(::tick,::source,tableToolbars::constructorAssignments,::stop)}
        private val toolbarCacheContract by lazy {WicketToolbarCacheContract(::tick,::source,tableToolbars::inspectAttachment,::stop)}

        private fun toolbarNote(at:PsiElement,constructor:Boolean) {
            val diagnostic=Diagnostic(DiagnosticCode.PARTIAL_ANALYSIS,if(constructor)
                "Reviewed fresh-table toolbar construction; header/sort/render callbacks, model values, generated toolbar IDs and private contents remain unknown."
                else "Reviewed owned DataTable toolbar registration; runtime toolbar IDs, visibility, rendering, sorting and provider data remain unknown.",location(at))
            if(diagnostic !in columnConstructionDiagnostics) {columnConstructionDiagnostics+=diagnostic;warnings+=diagnostic}
        }
        private fun navigationNote(at:PsiElement) {
            val diagnostic=Diagnostic(DiagnosticCode.PARTIAL_ANALYSIS,
                "Reviewed fresh-table navigation-toolbar construction and eager navigator factory; paging-link initialization, Ajax/render callbacks, resource/model evaluation, provider data, generated IDs and private contents remain unknown.",location(at))
            if(diagnostic !in columnConstructionDiagnostics) {columnConstructionDiagnostics+=diagnostic;warnings+=diagnostic}
        }

        private fun inspectTable(type:PsiClass) {
            if(factories && WicketDataTableConstruction.candidate(type)) {feature=true;tableConstruction.inspect(type)}
        }
        private fun tableConstructorNote(at:PsiElement) {
            val diagnostic=Diagnostic(DiagnosticCode.PARTIAL_ANALYSIS,
                "Reviewed DataTable constructor with framework caption/body/grid factories; provider queries, column/header/row/cell callbacks, runtime data and owned contents remain unknown.",location(at))
            if(diagnostic !in columnConstructionDiagnostics) {columnConstructionDiagnostics+=diagnostic;warnings+=diagnostic}
        }

        private fun stop(at: PsiElement, message: String): Nothing = throw Stop(at, message)
        private fun tick(at: PsiElement) {
            ProgressManager.checkCanceled()
            entry?.tick?.invoke(sources)
            if (++steps > (if (factories) 20000 else 5000) || sources.size > (if (factories) 96 else 48) || instances.size > 256 || stack.size > 16) stop(at, "The bounded source layout budget was exceeded." + if (factories) " ($steps steps, ${sources.size} sources, ${instances.size} instances, ${stack.size} calls)" else "")
        }
        private fun source(at: PsiElement) {
            tick(at)
            if (at is PsiCompiledElement) {
                at.containingFile?.virtualFile?.path?.let { path ->
                    val archive = path.substringBefore("!/")
                    sources += if (archive.endsWith(".jar")) archive else path
                }
                return
            }
            val file = at.containingFile ?: stop(at, "Source is unavailable.")
            if (sources.add(file.virtualFile?.path ?: stop(at, "Physical source is required.")) &&
                PsiTreeUtil.findChildOfType(file, PsiErrorElement::class.java) != null) stop(at, "Source contains incomplete Java syntax.")
        }
        fun run(): WicketSourceSetupAnalyzer.Expansion? {
            if (entry == null && page.constructors.size > 1) return null
            val constructor = entry?.constructor ?: page.constructors.singleOrNull()
            val arguments = if (entry == null) emptyList() else constructor?.parameterList?.parameters.orEmpty().map { Data(classType(it.type), constructorArgument = true) }
            construct(Ref(rootId), page, constructor, arguments)
            val hook = hierarchy(page).firstNotNullOfOrNull { owner -> owner.methods.singleOrNull { isHook(it) } }
            hook?.let { invokeSource(it, Ref(rootId), emptyList(), explicit = true) }
            val initializedHooks = mutableSetOf(rootId)
            while (true) {
                val next = instances.entries.firstOrNull { (id, instance) -> id !in initializedHooks &&
                    !WicketComponentTypes.ownsMarkup(instance.type) } ?: break
                initializedHooks += next.key
                hierarchy(next.value.type).firstNotNullOfOrNull { owner -> owner.methods.singleOrNull(::isHook) }
                    ?.let { stop(it, "Nested component initialization order is outside this source layout contract.") }
            }
            hierarchy(page).forEach { owner -> owner.methods.filter { !it.isConstructor &&
                it.findDeepestSuperMethods().any { method -> method.containingClass?.qualifiedName?.startsWith("org.apache.wicket.") == true } && !isHook(it) }
                .forEach { auditDeferred(it, Frame(Ref(rootId), owner, it, mutableMapOf())) } }
            if (initialContentDeclarations && contentHosts.isEmpty()) return null
            if (!feature && entry == null || factories && factoryCalls == 0 && symbolicLoops == 0 && isolatedForms == 0 && contentHosts.isEmpty() && columnConstructionDiagnostics.isEmpty() && storedActions.isEmpty()) return null
            if (factories && !initialContentDeclarations) {
                val effects = Effects()
                deferred.toList().forEach { (element, frame) -> effects.deferred(element, frame) }
            }
            val retained = instances.filter { it.key != rootId && (it.value.parents.isNotEmpty() || instances.values.any { parent -> parent.node?.parentNodeId == it.key }) }
            val nodes = retained.map { (id, instance) ->
                val node = instance.node ?: stop(page, "A component has no identity snapshot.")
                if (instance.parents.size != 1) stop(page, "A component has repeated or unavailable attachments ($id).")
                if (instance.generatedBy != null && instance.parents.single() != instance.generatedBy.id) stop(page, "A generated item is attached outside its generating repeater.")
                node.copy(parentNodeId = instance.parents.single(), template = node.template || generateSequence(instance.parents.singleOrNull()) { parent ->
                    instances[parent]?.parents?.singleOrNull() }.take(instances.size).any { instances[it]?.node?.templateItem == true })
            }
            val byId = nodes.associateBy { it.nodeId }
            nodes.forEach { node ->
                val seen = mutableSetOf<String>()
                var id: String? = node.nodeId
                while (id != rootId) {
                    tick(page)
                    if (id == null || !seen.add(id)) stop(page, "A component attachment is cyclic or outside the selected page.")
                    id = byId[id]?.parentNodeId
                }
            }
            val declarationsOnly = initialContentDeclarations || storedActions.isNotEmpty()
            val declaredNodes = if (declarationsOnly) nodes.map { it.copy(template = true, idRenameSafe = false) } else nodes
            val auditedIds = WicketIdOriginAudit().analyze(page, declaredNodes, promote = !declarationsOnly)
            sources += auditedIds.sources
            val initial = if (initialContentDeclarations) InitialContentSetup(contentSnapshots.values.toList(), initialCallbacks.toList()) else null
            if (initial != null) warnings += Diagnostic(DiagnosticCode.UNRESOLVED_SCOPE, "Source-declared initial content setup only: constructor/configuration effects, model values, event execution, visibility and later open/close/replacement state remain unknown. All bindings are inferred declarations.", location(page))
            storedActions.forEach { registration -> warnings += Diagnostic(DiagnosticCode.PARTIAL_ANALYSIS,
                "Source registrar construction only stores its callback. Initial attachments are inferred declarations; later dispatch, callback effects, visibility and runtime tree state remain unknown. No callback is evaluated.", registration.registrationLocation) }
            return WicketSourceSetupAnalyzer.Expansion(auditedIds.nodes, sources.toList().sorted(), (warnings + auditedIds.diagnostics).distinct(), initialContentSetup = initial,
                storedActionRegistrations = storedActions.toList())
        }

        private fun hierarchy(type: PsiClass): List<PsiClass> {
            val result = mutableListOf<PsiClass>()
            var current: PsiClass? = type
            while (current != null && current !is PsiCompiledElement) {
                source(current)
                if (result.size >= 12 || current in result) stop(current, "The source hierarchy is cyclic or exceeds its bound.")
                result += current
                current = current.superClass
            }
            return result
        }

        private fun construct(receiver: Ref, owner: PsiClass, method: PsiMethod?, args: List<Value>) {
            tick(owner)
            if (owner is PsiCompiledElement) {
                if (receiver.id == rootId && owner.qualifiedName !in WicketComponentTypes.markupRoots)
                    stop(owner, "Compiled custom page setup is unavailable to the source layout pass.")
                source(owner)
                return
            }
            source(owner)
            initializeType(owner, Frame(receiver, owner, method, mutableMapOf()))
            if (method != null && !stack.add(method)) stop(method, "Recursive constructor delegation is unsupported.")
            try {
                val context = Frame(receiver, owner, method, method?.parameterList?.parameters.orEmpty().mapIndexed { i, p -> p to args.getOrElse(i) { Unknown } }.toMap().toMutableMap())
                val statements = method?.body?.statements?.filterNot { it is PsiEmptyStatement }.orEmpty()
                val delegation = ((statements.firstOrNull() as? PsiExpressionStatement)?.expression as? PsiMethodCallExpression)
                    ?.takeIf { it.methodExpression.referenceName in setOf("this", "super") }
                if (delegation != null) {
                    val target = delegation.resolveMethod() ?: stop(delegation, "The constructor delegation does not resolve.")
                    val values = delegation.argumentList.expressions.map { evaluate(it, context) }
                    if (delegation.methodExpression.referenceName == "this") construct(receiver, owner, target, values)
                    else owner.superClass?.let { construct(receiver, it, target, values) }
                } else owner.superClass?.let { parent ->
                    val target = parent.constructors.singleOrNull { it.parameterList.parametersCount == 0 }
                    if (target == null && parent !is PsiCompiledElement && parent.constructors.isNotEmpty()) stop(owner, "No unique source superclass constructor exists.")
                    construct(receiver, parent, target, emptyList())
                }
                val instance = instances.getValue(receiver.id)
                if (instance.initialized.add(owner)) {
                    owner.initializers.forEach { stop(it, "Instance/static initializer blocks require an effect contract.") }
                    owner.fields.forEach { field ->
                        field.initializer?.let { expression ->
                            if (!field.hasModifierProperty(PsiModifier.STATIC)) instance.fields[field] = evaluate(expression, context)
                        }
                    }
                }
                execute(statements.drop(if (delegation == null) 0 else 1), context)
            } finally { method?.let(stack::remove) }
        }

        private fun execute(statements: List<PsiStatement>, frame: Frame): Value {
            for (statement in statements) {
                tick(statement)
                when (statement) {
                    is PsiEmptyStatement -> Unit
                    is PsiDeclarationStatement -> statement.declaredElements.forEach { element ->
                        val variable = element as? PsiLocalVariable ?: stop(element, "Only local variable declarations are supported.")
                        frame.values[variable] = variable.initializer?.let { evaluate(it, frame) } ?: Unknown
                    }
                    is PsiExpressionStatement -> evaluate(statement.expression, frame)
                    is PsiReturnStatement -> return statement.returnValue?.let { evaluate(it, frame) } ?: ReturnedVoid
                    is PsiBlockStatement -> {
                        val result = execute(statement.codeBlock.statements.toList(), frame)
                        if (result !== Unknown) return result
                    }
                    is PsiIfStatement -> {
                        if (!factories) stop(statement, "Control flow is outside the straight-line layout contract.")
                        val condition = statement.condition?.let { evaluate(it, frame) } as? Scalar
                            ?: stop(statement, "A factory setup branch has an unknown condition.")
                        val selected = when (condition.value) { true -> statement.thenBranch; false -> statement.elseBranch
                            else -> stop(statement, "A factory branch condition is not Boolean.") }
                        val result = selected?.let { execute(listOf(it), frame) } ?: Unknown
                        if (result !== Unknown) return result
                    }
                    is PsiWhileStatement -> symbolicWhile(statement, frame)
                    is PsiForStatement -> constructorDataLoop(statement, frame)
                    is PsiThrowStatement -> stop(statement, "Factory setup reaches a throwing branch.")
                    else -> stop(statement, "Control flow is outside the straight-line layout contract.")
                }
            }
            return Unknown
        }

        private fun constructorDataLoop(loop: PsiForStatement, frame: Frame) {
            feature = true
            if (frame.receiver.id != rootId || frame.method?.isConstructor != true)
                stop(loop, "Data initialization loops are reviewed only in root constructors.")
            if (dataLoopDiagnostics.size >= 16) stop(loop, "Constructor data loops exceed the shared 16-loop discovery bound.")
            WicketConstructorDataLoop(frame.method, ::tick, ::source, constants::expression, { variable ->
                val value = if (variable is PsiField) instances[frame.receiver.id]?.fields?.get(variable) else frame.values[variable]
                value is Ref || value is Factory || value is Behavior || value is ChildId || value is IteratorData
            }, { type -> initializeType(type, frame) }, { at, reason ->
                stop(loop, "Constructor data loop is not isolated: $reason (${at.containingFile.name}:${at.containingFile.text.take(at.textOffset).count { it == '\n' } + 1}).")
            }).audit(loop)
            val diagnostic = Diagnostic(DiagnosticCode.PARTIAL_ANALYSIS,
                "Constructor data loop audited for component isolation only; loop execution, data values and collection contents remain unknown.", location(loop))
            dataLoopDiagnostics += diagnostic
            warnings += diagnostic
        }

        private fun evaluate(expression: PsiExpression, frame: Frame): Value {
            tick(expression)
            val value = unwrap(expression)
            return when (value) {
                is PsiLiteralExpression -> Scalar(value.value, value)
                is PsiClassObjectAccessExpression -> Data()
                is PsiThisExpression -> if (factories && value.qualifier != null) lexical(frame.receiver, value.qualifier!!.resolve() as? PsiClass, value)
                    else { if (value.qualifier != null && value.qualifier!!.resolve() != frame.owner) stop(value, "An enclosing receiver escapes its construction context."); frame.receiver }
                is PsiSuperExpression -> frame.receiver
                is PsiTypeCastExpression -> {
                    val evaluated=evaluate(value.operand ?: stop(value,"Incomplete cast."),frame)
                    val target=value.castType?.type
                    if(evaluated is Data && target is PsiIntersectionType && LambdaUtil.getFunctionalInterfaceMethod(target)!=null)
                        evaluated.copy(callbackScopeUnknown=true) else evaluated
                }
                is PsiLambdaExpression, is PsiMethodReferenceExpression -> {
                    if (factories && componentFactory(LambdaUtil.getFunctionalInterfaceType(value, true))) {
                        source(value)
                        Factory(value, frame.copy(values = frame.values.toMutableMap()), ++factorySequence)
                    } else {
                        auditDeferred(value, frame)
                        val callback=LambdaUtil.getFunctionalInterfaceType(value,true)
                        Data(classType(callback) ?: classType(value.type),callbackScopeUnknown=callback !is PsiClassType || callback.resolve()==null)
                    }
                }
                is PsiReferenceExpression -> {
                    when (val target = value.resolve()) {
                        is PsiEnumConstant -> {
                            target.containingClass?.let { initializeType(it, frame) }
                            Data(target.containingClass)
                        }
                        is PsiField -> {
                            if (target.hasModifierProperty(PsiModifier.STATIC)) {
                                source(target)
                                if (component(target.type)) stop(value, "A static component reference is outside the known graph.")
                                val constant = constants.resolve(value)
                                if (constant != null) Scalar(constant, value, computed = true) else {
                                    target.containingClass?.let { initializeType(it, frame) }
                                    Data(classType(target.type))
                                }
                            } else {
                                val receiver = value.qualifierExpression?.let { evaluate(it, frame) } ?: if (factories) lexical(frame.receiver, target.containingClass, value) else frame.receiver
                                val known = (receiver as? Ref)?.let { instances[it.id]?.fields?.get(target) }
                                if (known != null) known else {
                                    if (component(target.type)) stop(value, "A component field has no known construction identity.")
                                    Data(classType(target.type))
                                }
                            }
                        }
                        is PsiVariable -> frame.values[target] ?: if (component(target.type)) stop(value, "A component variable has no known construction identity.") else Data(classType(target.type))
                        is PsiClass -> Data(target)
                        else -> stop(value, "A source value does not resolve.")
                    }
                }
                is PsiAssignmentExpression -> {
                    if (value.operationTokenType != JavaTokenType.EQ) {
                        if (!factories) return auditData(value, frame)
                        val target = (value.lExpression as? PsiReferenceExpression)?.resolve() as? PsiVariable ?: stop(value, "Unknown update target.")
                        if (graphType(target.type)) stop(value, "A compound update changes component state.")
                        evaluate(value.rExpression ?: stop(value, "Incomplete update."), frame)
                        val updated = Data(classType(value.type))
                        if (target is PsiField) {
                            val qualifier = (value.lExpression as PsiReferenceExpression).qualifierExpression
                            if (qualifier != null && qualifier !is PsiReferenceExpression && qualifier !is PsiThisExpression)
                                stop(value, "A compound update has an effectful receiver.")
                            val receiver = qualifier?.let { evaluate(it, frame) } ?: lexical(frame.receiver, target.containingClass, value)
                            if (receiver !is Ref || target.hasModifierProperty(PsiModifier.STATIC) || factoryGuards.any { receiver.id in it } || templateGuards.any { receiver.id in it })
                                stop(value, "A compound update escapes the factory scope.")
                            instances.getValue(receiver.id).fields[target] = updated
                        } else frame.values[target] = updated
                        return updated
                    }
                    val target = (value.lExpression as? PsiReferenceExpression)?.resolve() as? PsiVariable ?: stop(value, "The assignment target is not a variable.")
                    val right = evaluate(value.rExpression ?: stop(value, "Incomplete assignment."), frame)
                    if (target is PsiField) {
                        if (target.hasModifierProperty(PsiModifier.STATIC)) stop(value, "A static assignment can escape the construction scope.")
                        val receiver = (value.lExpression as PsiReferenceExpression).qualifierExpression?.let { evaluate(it, frame) } ?: frame.receiver
                        if (receiver !is Ref || target.containingClass !in hierarchy(instances.getValue(receiver.id).type)) stop(value, "A field assignment escapes the known receiver.")
                        if (factories && (factoryGuards.any { receiver.id in it } || templateGuards.any { receiver.id in it }))
                            stop(value, "A factory changes a captured component field.")
                        instances.getValue(receiver.id).fields[target] = right
                    } else frame.values[target] = right
                    right
                }
                is PsiNewExpression -> if (component(value.type)) create(value, frame) else auditData(value, frame)
                is PsiMethodCallExpression -> call(value, frame)
                is PsiBinaryExpression -> if (factories) scalarBinary(value, frame) else auditData(value, frame)
                is PsiPolyadicExpression -> if (factories) scalarPolyadic(value, frame) else auditData(value, frame)
                is PsiPrefixExpression -> if (factories && value.operationTokenType == JavaTokenType.EXCL) {
                    val result = evaluate(value.operand ?: stop(value, "Incomplete condition."), frame) as? Scalar
                    Scalar(!(result?.value as? Boolean ?: stop(value, "Unknown factory condition.")), value, true)
                } else auditData(value, frame)
                is PsiPostfixExpression -> if (factories && !component(value.type)) auditData(value, frame) else stop(value, "Unknown component update.")
                else -> auditData(value, frame)
            }
        }

        private fun lexical(start: Ref, owner: PsiClass?, at: PsiElement): Ref {
            var current: Ref? = start
            val seen = mutableSetOf<String>()
            while (current != null && seen.add(current.id)) {
                tick(at)
                if (owner == null || owner in hierarchy(instances.getValue(current.id).type) ||
                    owner is PsiCompiledElement && (instances.getValue(current.id).type == owner || InheritanceUtil.isInheritor(instances.getValue(current.id).type, owner.qualifiedName ?: ""))) return current
                current = instances.getValue(current.id).enclosing
            }
            stop(at, "An enclosing receiver has no known construction identity.")
        }

        private fun invokeFactory(factory: Factory, arguments: List<Value>, at: PsiElement): Value {
            tick(at)
            if (arguments.firstOrNull().let { it !is ChildId && (it !is Scalar || it.value !is String) } ||
                arguments.drop(1).any { it is Ref || it is Factory }) stop(at, "A factory requires a known String/generated ID and opaque data arguments.")
            if (++factoryCalls > 64 || !activeFactories.add(factory.expression)) stop(at, "Factory invocation is recursive or exceeds the 64-invocation bound.")
            feature = true
            val captured = instances.keys.toSet()
            factoryGuards += captured
            try {
                val expression = factory.expression
                val result = when (expression) {
                    is PsiLambdaExpression -> {
                        val parameters = expression.parameterList.parameters
                        if (parameters.size != arguments.size) stop(at, "Factory lambda arity is unresolved.")
                        val frame = factory.frame.copy(values = factory.frame.values.toMutableMap().apply { putAll(parameters.zip(arguments)) })
                        when (val body = expression.body) {
                            is PsiExpression -> evaluate(body, frame)
                            is PsiCodeBlock -> execute(body.statements.toList(), frame)
                            else -> stop(at, "Factory lambda body is unavailable.")
                        }
                    }
                    is PsiMethodReferenceExpression -> {
                        val method = expression.resolve() as? PsiMethod ?: stop(at, "Factory method reference does not resolve.")
                        if (method is PsiCompiledElement || method.isConstructor) stop(at, "Compiled and constructor factory references remain unresolved.")
                        val receiver = if (method.hasModifierProperty(PsiModifier.STATIC)) factory.frame.receiver
                            else expression.qualifierExpression?.let { evaluate(it, factory.frame) } as? Ref
                                ?: stop(at, "An unbound factory receiver is unresolved.")
                        invokeSource(method, receiver, arguments, method.hasModifierProperty(PsiModifier.STATIC))
                    }
                    else -> stop(at, "Unknown component factory.")
                }
                val returned = result as? Ref ?: stop(at, "A component factory did not return a known component.")
                if (templateDepth > 0 && returned.id in captured) stop(at, "A repeated factory returns a captured component; repeated attachment is unsafe.")
                val generated = arguments.firstOrNull() as? ChildId
                if (generated != null && instances.getValue(returned.id).generatedBy != generated.view)
                    stop(at, "A repeated factory does not forward its generated item ID.")
                validatedFactories += factory.identity
                return returned
            } finally { factoryGuards.removeAt(factoryGuards.lastIndex); activeFactories.remove(factory.expression) }
        }

        private fun symbolicWhile(statement: PsiWhileStatement, frame: Frame) {
            if (!factories) stop(statement, "Control flow is outside the straight-line layout contract.")
            val condition = statement.condition as? PsiMethodCallExpression ?: stop(statement, "Only reviewed iterator loops produce templates.")
            val method = condition.resolveMethod()
            val iterator = condition.methodExpression.qualifierExpression?.let { evaluate(it, frame) }
            if (method?.containingClass?.qualifiedName != "java.util.Iterator" || method.name != "hasNext" || iterator !is IteratorData ||
                condition.argumentList.expressionCount != 0 || ++symbolicLoops > 16) stop(statement, "Iterator template scope or the 16-loop bound is unresolved.")
            val body = (statement.body as? PsiBlockStatement)?.codeBlock ?: stop(statement, "A template loop requires a block.")
            if (PsiTreeUtil.findChildrenOfType(body, PsiMethodCallExpression::class.java).none {
                it.resolveMethod()?.let { method -> method.containingClass?.qualifiedName == WicketRepeaterContract.REPEATING && method.name == "newChildId" } == true })
                stop(statement, "An iterator loop has no reviewed generated item scope.")
            templateDepth++
            templateGuards += instances.keys.toSet()
            try {
                if (execute(body.statements.toList(), frame.copy(values = frame.values.toMutableMap())) !== Unknown)
                    stop(statement, "A symbolic loop has an early exit.")
            }
            finally { templateDepth--; templateGuards.removeAt(templateGuards.lastIndex) }
            feature = true
            warnings += Diagnostic(DiagnosticCode.PARTIAL_ANALYSIS, "Iterator population is represented once as a symbolic template; runtime counts, IDs and data values are unknown.", location(statement))
        }

        private fun scalarBinary(expression: PsiBinaryExpression, frame: Frame): Value {
            val left = evaluate(expression.lOperand, frame)
            if (expression.operationTokenType == JavaTokenType.ANDAND && (left as? Scalar)?.value == false) return Scalar(false, expression, true)
            if (expression.operationTokenType == JavaTokenType.OROR && (left as? Scalar)?.value == true) return Scalar(true, expression, true)
            val right = evaluate(expression.rOperand ?: stop(expression, "Incomplete expression."), frame)
            return fold(expression, expression.operationTokenType, listOf(left, right))
        }

        private fun scalarPolyadic(expression: PsiPolyadicExpression, frame: Frame): Value {
            val values = expression.operands.map { evaluate(it, frame) }
            return fold(expression, expression.operationTokenType, values)
        }

        private fun fold(expression: PsiExpression, token: com.intellij.psi.tree.IElementType, values: List<Value>): Value {
            if (values.any { it is Ref || it is Factory || it is ChildId }) stop(expression, "Component or factory arithmetic is unresolved.")
            if (values.any { it !is Scalar || it.value == null }) return Data(classType(expression.type))
            val operator = when (token) { JavaTokenType.OROR -> "||"; JavaTokenType.ANDAND -> "&&"
                else -> WicketScalarExpressions.operator(token) ?: return Data(classType(expression.type)) }
            val result = WicketScalarExpressions.fold(expression, values.map { (it as Scalar).value!! }, operator)
                ?: return Data(classType(expression.type))
            return Scalar(result, expression, true)
        }

        private fun create(expression: PsiNewExpression, frame: Frame): Value {
            val type = expression.anonymousClass ?: expression.classReference?.resolve() as? PsiClass ?: stop(expression, "Component type is unavailable.")
            val originalContract = WicketIdConstructors.trace(expression)
            if (!initialContentDeclarations && originalContract == null && (WicketComponentTypes.isFormComponent(type) || InheritanceUtil.isInheritor(type, WicketComponentTypes.FORM_PANEL)))
                stop(expression, "Custom form components retain their reviewed conversion and isolation contract.")
            WicketRepeaterContract.kind(type)?.let { kind ->
                if (type !is PsiCompiledElement && !WicketRepeaterContract.accepts(type, kind))
                    stop(expression, "Custom repeater factories retain their reviewed item contract.")
            }
            val identity = WicketComponentIdentity.trace(expression, conditionalData = initialContentDeclarations) ?: stop(expression,
                "Component constructor ID provenance is not established for ${expression.resolveConstructor()?.containingClass?.qualifiedName ?: type.name ?: "the anonymous component"}.")
            if (originalContract == null) feature = true
            sources += identity.sources
            val args = expression.argumentList?.expressions.orEmpty().mapIndexed { index, argument ->
                // Fragment's provider is a reviewed read-only markup reference, not an arbitrary root escape.
                val result = evaluate(argument, frame)
                val constructorOwner = expression.resolveConstructor()?.containingClass?.qualifiedName
                val buttonForm = constructorOwner == "org.apache.wicket.ajax.markup.html.form.AjaxButton" && index > 0 &&
                    classType(expression.resolveConstructor()?.parameterList?.parameters?.getOrNull(index)?.type)?.qualifiedName == "org.apache.wicket.markup.html.form.Form"
                if (result is Ref && !(constructorOwner == FRAGMENT && index == 2) && !buttonForm) {
                    stop(argument, "A component reference escapes into an unreviewed constructor.")
                }
                result
            }
            val originIndex = expression.argumentList?.expressions.orEmpty().indexOfFirst { unwrap(it) == identity.expression }
            val idValueOrigin = if (originIndex >= 0) args[originIndex] else evaluate(identity.expression, frame)
            val generated = idValueOrigin as? ChildId
            val id = idValueOrigin as? Scalar
            val text = if (generated != null) null else (id?.value as? String ?: stop(expression, "The source component ID is not a known String."))
            if (text != null && (text.isEmpty() && !WicketCompletionAnalysis.allowsEmpty(id?.origin) || text.any { it == ':' || it == '~' })) stop(expression, "The source component ID is empty or reserved.")
            val original = expression.anonymousClass?.baseClassType?.resolve() ?: type
            // Audited ordinary ListView attachments also enable source population discovery.
            if (expression.anonymousClass != null && original.qualifiedName == "org.apache.wicket.markup.html.list.ListView") feature = true
            val name = original.qualifiedName ?: stop(expression, "A named component base is required.")
            val idValue = "java:layout:${location(expression).file}:${expression.textOffset}:${++sequence}"
            val fragment = if (type.qualifiedName == FRAGMENT) FragmentReference(
                (args.getOrNull(1) as? Scalar)?.value as? String, (args.getOrNull(2) as? Ref)?.id,
                location(expression), (args.getOrNull(1) as? Scalar)?.origin?.let(::location)
                    ?: expression.argumentList?.expressions?.getOrNull(1)?.let(::location)) else null
            val node = ComponentNode(idValue, text, name, null, location(expression), confidence = Confidence.CONFIRMED,
                idLocation = id?.origin?.let(::location), idRenameSafe = false, computedId = id?.computed == true, fragment = fragment,
                templateItem = generated != null, template = templateDepth > 0 || generated != null,
                repetition = WicketRepeaterContract.kind(original))
            val ref = Ref(idValue)
            instances[idValue] = Instance(type, node, generatedBy = generated?.view, enclosing = if (factories && (expression.anonymousClass != null || original.containingClass != null && !original.hasModifierProperty(PsiModifier.STATIC))) frame.receiver else null,allocation=expression)
            if (initialContentDeclarations && WicketInitialContentDeclarations.candidate(original)) {
                val host = contentDeclarations.inspect(expression, idValue)
                contentHosts[idValue] = host; feature = true
                initialCallbacks += host.callbacks
                instances.getValue(idValue).node = node.copy(sourceSetupExpanded = true, template = true)
                host.nodes.forEach { child ->
                    val childType = JavaPsiFacade.getInstance(page.project).findClasses(child.componentType, page.resolveScope).singleOrNull() ?: stop(expression, "Initial child type is ambiguous.")
                    instances[child.nodeId] = Instance(childType, child.copy(parentNodeId = null), parents = mutableListOf(child.parentNodeId!!), enclosing = ref)
                }
                host.fields.forEach { (field, child) -> instances.getValue(idValue).fields[field] = Ref(child) }
                val slot = host.nodes.single { it.nodeId == host.slot }
                contentSnapshots[idValue] = InitialContentHost(idValue, name, location(expression), host.constructors.map(::location), location(host.setter), host.contentId, slot.parentNodeId!!, slot.nodeId, host.placements)
                if (host.placements.isNotEmpty()) {
                    val diagnostic = Diagnostic(DiagnosticCode.PARTIAL_ANALYSIS,
                        "Constructor-owned initial content declared for $name; constructor/configuration/model effects, visibility and callbacks remain unknown.", location(expression))
                    constructorContentDiagnostics += diagnostic
                    warnings += diagnostic
                }
                expression.anonymousClass?.methods?.filterNot { it.isConstructor }?.forEach { initialCallbacks += location(it); source(it) }
                return ref
            }
            expression.anonymousClass?.let { anonymous ->
                if (anonymous.initializers.isNotEmpty() || anonymous.fields.any { it.initializer != null }) stop(anonymous, "Anonymous initialization effects are not established.")
                anonymous.methods.filterNot { it.isConstructor }.forEach { method ->
                    if (method.findDeepestSuperMethods().none { it.containingClass?.qualifiedName?.startsWith("org.apache.wicket.") == true }) stop(method, "An unreviewed anonymous method can escape its receiver.")
                    auditDeferred(method, if (factories) Frame(ref, anonymous, method, frame.values.toMutableMap()) else frame)
                }
            }
            val formCaches = if (factories && original !is PsiCompiledElement && WicketComponentTypes.ownsMarkup(original)) lazyFormCaches(original) else emptyMap()
            val expand = factories && original !is PsiCompiledElement && WicketComponentTypes.ownsMarkup(original) && args.any { it is Factory }
            if (initialContentDeclarations && (WicketComponentTypes.isFormComponent(original) || formCaches.isNotEmpty()) && original !is PsiCompiledElement) {
                declarationFields += ref.id
                unknownContents(ref, expression, "Source-declared field attachment only; constructor, conversion, validation and model effects remain unknown.")
            } else if (formCaches.isNotEmpty()) {
                val effects = Effects(ref, formCaches)
                effects.constructForm(expression.resolveConstructor() ?: stop(expression, "The form constructor is unavailable."), args, frame)
                formEffects[ref.id] = effects
                isolatedForms++; feature = true
                unknownContents(ref, expression, "Owned form construction and configuration are isolated; internal inputs, conversion, validation and runtime lifecycle remain unexpanded.")
            } else if (expand) {
                if (hierarchy(original).any { owner -> owner.methods.any { isHook(it) || it.name in WicketFragmentAnalyzer.PROVIDER_METHODS && it.findDeepestSuperMethods().isNotEmpty() } })
                    stop(expression, "Factory-owned markup initialization or sourcing is not reviewed.")
                instances.getValue(ref.id).node = node.copy(sourceSetupExpanded = true)
                construct(ref, original, expression.resolveConstructor(), args)
            } else if (WicketComponentTypes.ownsMarkup(original) || WicketFragmentAnalyzer.isFragment(original) || WicketRepeaterContract.kind(original) != null) {
                auditBoundary(original)
                if (fragment == null) unknownContents(ref, expression, "Component-owned or repeated contents are outside the source layout snapshot.")
            } else if (original !is PsiCompiledElement) {
                construct(ref, original, expression.resolveConstructor(), args)
            }
            return ref
        }

        private fun unknownContents(receiver: Ref, at: PsiElement, message: String) {
            val instance = instances.getValue(receiver.id)
            instance.node = instance.node?.let { it.copy(diagnostics = it.diagnostics + Diagnostic(DiagnosticCode.UNRESOLVED_SCOPE, message, location(at))) }
        }

        private fun call(call: PsiMethodCallExpression, frame: Frame): Value {
            val method = call.resolveMethod() ?: stop(call, "The called method does not resolve.")
            if (!call.resolveMethodGenerics().isValidResult) stop(call, "The called method's generic arguments do not resolve.")
            if (factories && !initialContentDeclarations) storedActionDeclarations.inspectBare(call)?.let {
                storedActions.addAll(it)
                return Data(classType(method.returnType))
            }
            val static = method.hasModifierProperty(PsiModifier.STATIC)
            val qualifier = call.methodExpression.qualifierExpression
            val receiver = if (static) null else qualifier?.let { evaluate(it, frame) } ?: if (factories) lexical(frame.receiver, method.containingClass, call) else frame.receiver
            if (static && qualifier != null && (qualifier as? PsiReferenceExpression)?.resolve() !is PsiClass) stop(qualifier, "A static call has an effectful receiver.")
            val owner = method.containingClass?.qualifiedName
            if (factories && WicketRegisteredListFactory.candidate(call)) return registeredList(call, frame)
            if (factories && receiver is Factory) {
                if (method != LambdaUtil.getFunctionalInterfaceMethod(LambdaUtil.getFunctionalInterfaceType(receiver.expression, true))) stop(call, "Factory invocation does not use its reviewed SAM method.")
                return invokeFactory(receiver, call.argumentList.expressions.map { evaluate(it, frame) }, call)
            }
            if (factories && static && owner == "java.util.Objects" && method.name == "requireNonNull") {
                val value = evaluate(call.argumentList.expressions.firstOrNull() ?: stop(call, "Missing required value."), frame)
                if (value is Scalar && value.value == null) stop(call, "A required value is null.")
                call.argumentList.expressions.drop(1).forEach { evaluate(it, frame) }
                return value
            }
            if (factories && static && method !is PsiCompiledElement && component(method.returnType) && method.containingClass in hierarchy(frame.owner)) {
                return invokeSource(method, frame.receiver, call.argumentList.expressions.map { evaluate(it, frame) }, true)
            }
            if (factories && receiver is Scalar && owner == "java.lang.String" && method.name == "equals" && call.argumentList.expressionCount == 1) {
                val other = evaluate(call.argumentList.expressions.single(), frame)
                if (other !is Scalar) stop(call, "Unknown identity comparison.")
                return Scalar(receiver.value == other.value, call, true)
            }
            if (method.name == "onInitialize" && (call.methodExpression.qualifierExpression !is PsiSuperExpression || frame.method?.let(::isHook) != true))
                stop(call, "Initialization may only delegate once from a reviewed onInitialize override.")
            if (receiver is Ref) {
                contentHosts[receiver.id]?.let { host ->
                    val selected = contentDeclarations.selected(call, host, knownReceiver = true)
                    if (contentDeclarations.isSetter(selected, host)) {
                        val child = call.argumentList.expressions.singleOrNull()?.let { evaluate(it, frame) } as? Ref ?: stop(call, "Initial content requires one fresh known component.")
                        val childInstance = instances.getValue(child.id)
                        if (childInstance.node?.wicketId != host.contentId || childInstance.parents.isNotEmpty()) stop(call, "Initial content has a wrong ID or an existing parent.")
                        val previous = contentSnapshots.getValue(receiver.id)
                        if (previous.placementLocations.isNotEmpty()) stop(call, "Competing initial content placements are unsupported.")
                        instances.getValue(previous.contentNodeId).parents.clear()
                        childInstance.parents += previous.slotParentNodeId
                        contentSnapshots[receiver.id] = previous.copy(contentNodeId = child.id, placementLocations = previous.placementLocations + location(call))
                        return receiver
                    }
                    contentDeclarations.validateConfiguration(selected, host)
                    call.argumentList.expressions.forEach { if (evaluate(it, frame) is Ref) stop(it, "Initial host configuration exposes a component.") }
                    return receiver
                }
                if (factories && owner == WicketRepeaterContract.REPEATING && method.name == "newChildId") return ChildId(receiver, call)
                if (factories && owner == COMPONENT && method.name == "getId") {
                    val node = instances.getValue(receiver.id).node ?: stop(call, "The root has no component ID.")
                    return node.wicketId?.let { Scalar(it, call, true) } ?: stop(call, "Generated runtime IDs cannot be compared as literals.")
                }
                // A superclass-typed alias still dispatches to the constructed source type.
                // Resolve this before using any framework attachment/configuration summary.
                if (qualifier !is PsiSuperExpression && !method.hasModifierProperty(PsiModifier.PRIVATE) &&
                    !method.hasModifierProperty(PsiModifier.FINAL)) {
                    val actual = hierarchy(instances.getValue(receiver.id).type)
                        .firstNotNullOfOrNull { it.findMethodBySignature(method, false) }
                    if (actual != null && actual != method) return invokeSource(method, receiver,
                        call.argumentList.expressions.map { evaluate(it, frame) })
                }
                if (method.name in setOf("add", "queue") && owner == "org.apache.wicket.MarkupContainer" &&
                    (method.parameterList.parameters.singleOrNull()?.type as? PsiEllipsisType)?.componentType?.canonicalText == "org.apache.wicket.Component") {
                    if (method.name == "queue") stop(call, "Queue placement remains with the path interpreter.")
                    call.argumentList.expressions.forEach { argument ->
                        val child = evaluate(argument, frame) as? Ref ?: stop(argument, "The attached component identity is unknown.")
                        if (factoryGuards.any { receiver.id in it }) stop(call, "A factory mutates a captured component scope.")
                        if (templateGuards.any { receiver.id in it } && instances.getValue(child.id).generatedBy != receiver)
                            stop(call, "A repeated body mutates a scope outside its generated item.")
                        instances.getValue(child.id).parents += receiver.id
                    }
                    return receiver
                }
                if (method.name == "add" && owner == COMPONENT &&
                    (method.parameterList.parameters.singleOrNull()?.type as? PsiEllipsisType)?.componentType?.canonicalText == BEHAVIOR) {
                    call.argumentList.expressions.forEach { argument ->
                        val behavior = evaluate(argument, frame) as? Behavior ?: stop(argument, "Behavior binding effects are not established.")
                        auditBehavior(behavior.type, receiver, argument)
                    }
                    return receiver
                }
                if (owner in FRAMEWORK_CONFIG.keys && method.name in FRAMEWORK_CONFIG.getValue(owner!!)) {
                    call.argumentList.expressions.forEach { argument ->
                        if (evaluate(argument, frame) is Ref) stop(argument, "Configuration unexpectedly receives a component.")
                    }
                    return receiver
                }
                if (method.name in setOf("addOrReplace", "replace", "removeAll") &&
                    receiver.id != rootId && WicketComponentTypes.ownsMarkup(instances.getValue(receiver.id).type)) {
                    call.argumentList.expressions.forEach { argument ->
                        val child = evaluate(argument, frame)
                        if (child is Ref) instances.getValue(child.id).parents += receiver.id
                    }
                    unknownContents(receiver, call, "Structural changes are confined to the component's separate markup scope.")
                    return receiver
                }
                if (method.name in STRUCTURAL && method.containingClass?.let { InheritanceUtil.isInheritor(it, COMPONENT) } == true) stop(call, "An unsupported structural call '${method.name}' can change known attachments.")
                val values = call.argumentList.expressions.map { evaluate(it, frame) }
                if (initialContentDeclarations && receiver.id in declarationFields && WicketComponentTypes.ownsMarkup(instances.getValue(receiver.id).type) &&
                    instances.getValue(receiver.id).node?.sourceSetupExpanded != true && method !is PsiCompiledElement) {
                    if (values.any { it is Ref || it is Factory }) stop(call, "A declaration-only component configuration receives a component or factory.")
                    if (!contentDeclarations.returnsReceiver(method)) stop(call, "A declaration-only configuration does not return its own receiver.")
                    source(method)
                    return receiver
                }
                if (factories && receiver.id != rootId && WicketComponentTypes.ownsMarkup(instances.getValue(receiver.id).type) &&
                    instances.getValue(receiver.id).node?.sourceSetupExpanded != true && method !is PsiCompiledElement) {
                    val result = (formEffects[receiver.id] ?: Effects()).sourceCall(method, receiver, values, frame)
                    if (component(method.returnType)) {
                        if (result?.known != receiver) stop(call, "Opaque component configuration does not prove a fluent receiver return.")
                        return receiver
                    }
                    if (result != null) stop(call, "An opaque method erases a component reference into data.")
                    return Data(classType(method.returnType))
                }
                return invokeSource(method, receiver, values, qualifier is PsiSuperExpression)
            }
            return auditData(call, frame)
        }

        private fun registeredList(call: PsiMethodCallExpression, frame: Frame): Ref {
            val registration = WicketRegisteredListFactory(::tick, ::source, sources, ::stop).resolve(call)
            call.argumentList.expressions.filter { it != registration.callback }.forEach { argument ->
                val value = evaluate(argument, frame)
                if (value is Ref || value is Factory || value is ChildId) stop(argument, "Registered list data arguments cannot receive captured component/factory identities.")
            }
            val id = evaluate(registration.id, frame) as? Scalar ?: stop(call, "The registered list ID is unknown.")
            val text = id.value as? String ?: stop(call, "The registered list ID is not a String.")
            if (text.isEmpty() && !WicketCompletionAnalysis.allowsEmpty(id.origin) || text.any { it == ':' || it == '~' }) stop(call, "The registered list ID is empty or reserved.")
            if (++factoryCalls > 64) stop(call, "Registered list factories exceed the shared 64-invocation bound.")
            val ref = Ref("java:layout:${location(call).file}:${call.textOffset}:${++sequence}")
            instances[ref.id] = Instance(registration.type, ComponentNode(ref.id, text, registration.original.qualifiedName!!, null, location(call),
                confidence = Confidence.CONFIRMED, idLocation = location(id.origin), idRenameSafe = false, computedId = id.computed,
                repetition = Repetition.LIST_VIEW, template = true, registeredListPopulation = registration.metadata), enclosing = frame.receiver)
            feature = true
            auditBoundary(registration.original)
            registration.behaviors.forEach { auditBehavior(it, ref, call) }
            registeredItemCallbacks += registration.callback
            auditDeferred(registration.callback, frame)
            unknownContents(ref, call, "Registered item/data population is a separate source template; runtime rows, behavior effects and visibility remain unknown.")
            return ref
        }

        private fun invokeSource(declaration: PsiMethod, receiver: Ref, args: List<Value>, explicit: Boolean = false): Value {
            val instance = instances.getValue(receiver.id)
            val method = if (explicit || declaration.hasModifierProperty(PsiModifier.PRIVATE) || declaration.hasModifierProperty(PsiModifier.STATIC)) declaration
                else hierarchy(instance.type).firstNotNullOfOrNull { it.findMethodBySignature(declaration, false) } ?: declaration
            if (method is PsiCompiledElement) {
                if (method.name == "onInitialize" && method.containingClass?.qualifiedName in WicketComponentTypes.markupRoots +
                    setOf(COMPONENT, "org.apache.wicket.MarkupContainer", "org.apache.wicket.Page", "org.apache.wicket.markup.html.WebMarkupContainer")) return Unknown
                stop(declaration, "The effects of compiled call '${method.name}' are not reviewed.")
            }
            source(method)
            if (!stack.add(method)) stop(method, "Recursive source helper invocation is unsupported.")
            try {
                val owner = method.containingClass ?: stop(method, "The helper owner is unavailable.")
                val parameters = method.parameterList.parameters
                if (parameters.size != args.size || method.isVarArgs || method.hasModifierProperty(PsiModifier.SYNCHRONIZED)) stop(method, "Helper parameters or execution are unsupported.")
                if (method != declaration && receiver.id == rootId) warnings += Diagnostic(DiagnosticCode.PARTIAL_ANALYSIS,
                    "Source layout dispatch uses the selected concrete class ${page.qualifiedName}; runtime subclasses are not inferred.", location(method))
                val frame = Frame(receiver, owner, method, parameters.zip(args).toMap().toMutableMap())
                if (isHook(method)) {
                    val first = (method.body?.statements?.firstOrNull { it !is PsiEmptyStatement } as? PsiExpressionStatement)?.expression as? PsiMethodCallExpression
                    if (first?.methodExpression?.qualifierExpression !is PsiSuperExpression || first.methodExpression.referenceName != "onInitialize")
                        stop(method, "Initialization must begin with super.onInitialize().")
                }
                return execute(method.body?.statements?.toList() ?: stop(method,
                    "The source helper body is unavailable (${method.containingClass?.qualifiedName}.${method.name})."), frame)
            } finally { stack.remove(method) }
        }

        /** Audit component-owned construction without flattening its private markup. */
        private fun auditBoundary(type: PsiClass) {
            if (type is PsiCompiledElement || !audited.add(type)) return
            inspectTable(type)
            val previous = boundaryType
            boundaryType = type
            try {
                hierarchy(type).forEach { owner ->
                    initializeType(owner, Frame(Ref(rootId), owner, null, mutableMapOf()))
                    auditFields(owner)
                    owner.constructors.forEach { method -> auditLocal(method, mutableSetOf()) }
                    owner.methods.filter(::isHook).forEach { auditLocal(it, mutableSetOf()) }
                }
            } finally { boundaryType = previous }
        }

        private fun lazyFormCaches(type: PsiClass): Map<PsiMethod,PsiField> = WicketFormCacheContract.find(hierarchy(type),::tick)

        private fun auditLocal(method: PsiMethod, visiting: MutableSet<PsiMethod>, dataOnly: Boolean = false) {
            tick(method)
            if (method is PsiCompiledElement || !visiting.add(method)) return
            source(method)
            if (visiting.size > 12) stop(method, "Component isolation exceeds its source-call bound.")
            if (method.body == null && !(method.containingClass?.isRecord == true &&
                    (method.isConstructor || method.parameterList.parametersCount == 0 && method.containingClass?.recordComponents?.any { it.name == method.name } == true)))
                stop(method, "An abstract or unavailable source effect cannot be proved isolated.")
            try { method.body?.let { auditLocalBody(it, visiting, dataOnly) } }
            finally { visiting.remove(method) }
        }

        private fun auditFields(type: PsiClass) {
            if (!auditedFields.add(type)) return
            type.initializers.filterNot { it.hasModifierProperty(PsiModifier.STATIC) }.forEach { auditLocalBody(it.body, mutableSetOf(), false) }
            type.fields.filterNot { it.hasModifierProperty(PsiModifier.STATIC) }.forEach { field ->
                field.initializer?.let { initializer ->
                    auditLocalBody(initializer, mutableSetOf(), !graphType(field.type))
                    PsiTreeUtil.findChildrenOfType(initializer, PsiLambdaExpression::class.java).forEach {
                        auditDeferred(it, Frame(Ref(rootId), type, null, mutableMapOf()))
                    }
                    PsiTreeUtil.findChildrenOfType(initializer, PsiMethodReferenceExpression::class.java).forEach {
                        auditDeferred(it, Frame(Ref(rootId), type, null, mutableMapOf()))
                    }
                    if (initializer is PsiLambdaExpression || initializer is PsiMethodReferenceExpression)
                        auditDeferred(initializer, Frame(Ref(rootId), type, null, mutableMapOf()))
                }
            }
        }

        private fun auditLocalBody(body: PsiElement, visiting: MutableSet<PsiMethod>, dataOnly: Boolean) {
            body.accept(object : JavaRecursiveElementWalkingVisitor() {
                override fun visitClass(aClass: PsiClass) { /* Deferred nested component scope. */ }
                override fun visitLambdaExpression(expression: PsiLambdaExpression) { /* Captures audited at their caller. */ }
                override fun visitThisExpression(expression: PsiThisExpression) {
                    if (dataOnly && component(expression.type)) stop(expression, "Opaque data captures a component receiver.")
                    super.visitThisExpression(expression)
                }
                override fun visitMethodCallExpression(expression: PsiMethodCallExpression) {
                    tick(expression)
                    val declared = expression.resolveMethod() ?: stop(expression, "Component construction calls an unresolved method.")
                    if(factories && !dataOnly && WicketFluentComponentConfiguration.candidate(expression)) {
                        feature=true
                        val proof=fluentConfigurations.inspect(expression)
                        proof.allocation.argumentList?.expressions.orEmpty().forEach {auditLocalBody(it,visiting,false)}
                        val previous=boundaryType
                        boundaryType=proof.owners.first()
                        try {
                            proof.behaviors.forEach {auditBehavior(it,Ref(rootId),expression)}
                            proof.owners.forEach {owner -> initializeType(owner,Frame(Ref(rootId),owner,null,mutableMapOf()));auditFields(owner)}
                            // Constructor delegation is recursively audited once. Setter bodies already
                            // have the stricter forward/store/return proof; callbacks stay deferred.
                            proof.constructors.firstOrNull()?.let {auditLocal(it,visiting,false)}
                        } finally {boundaryType=previous}
                        val diagnostic=Diagnostic(DiagnosticCode.PARTIAL_ANALYSIS,
                            "Reviewed fresh inline component fluent configuration through private behavior and exact Model storage; enum initialization outcomes, model values, rendering, Ajax callbacks, visibility and later state remain unknown. Private contents are not expanded.",location(expression))
                        if(diagnostic !in columnConstructionDiagnostics) {columnConstructionDiagnostics+=diagnostic;warnings+=diagnostic}
                        return
                    }
                    if(factories && !dataOnly && WicketOwnedComponentAccessor.candidate(expression)) {
                        feature=true
                        val proof=ownedAccessors.inspect(expression)
                        val previous=boundaryType
                        boundaryType=proof.owners.first()
                        try {
                            proof.owners.forEach {owner -> initializeType(owner,Frame(Ref(rootId),owner,null,mutableMapOf()));auditFields(owner)}
                            proof.constructors.forEach {auditLocal(it,visiting,false)}
                            proof.owners.forEach {owner -> owner.methods.filter(::isHook).forEach {auditLocal(it,visiting,false)}}
                        } finally {boundaryType=previous}
                        ownedAccessors.complete(expression)
                        val diagnostic=Diagnostic(DiagnosticCode.PARTIAL_ANALYSIS,
                            "Reviewed owned component accessor behavior decoration; the selected pure getter returns a fresh private-final framework child. Behavior rendering, model values, visibility and later state remain unknown; private contents are not expanded.",location(expression))
                        if(diagnostic !in columnConstructionDiagnostics) {columnConstructionDiagnostics+=diagnostic;warnings+=diagnostic}
                        return
                    }
                    if(factories && !dataOnly && WicketDataTableToolbars.registrationMethod(declared)) {
                        feature=true
                        val proof=tableToolbars.registration(expression)
                        toolbarProofs+=proof
                        try {
                            proof.eagerAllocations.forEach {auditLocalBody(it,visiting,false)}
                            auditLocalBody(proof.allocation,visiting,false)
                        } finally {toolbarProofs.removeAt(toolbarProofs.lastIndex)}
                        tableToolbars.complete(proof)
                        if(proof.navigation) navigationNote(proof.allocation) else toolbarNote(proof.allocation,true)
                        toolbarNote(expression,false)
                        return
                    }
                    if(factories && !dataOnly && WicketDataTableToolbars.constructor(declared)) {
                        if(toolbarProofs.lastOrNull()?.owners?.contains(methodOwner(body))!=true)
                            stop(expression,"Toolbar construction requires a reviewed fresh-table registration context.")
                        super.visitMethodCallExpression(expression)
                        return
                    }
                    if(factories && !dataOnly && WicketDataTableConstruction.constructor(declared)) {
                        inspectTable(methodOwner(body))
                        tableConstructorNote(expression)
                        super.visitMethodCallExpression(expression)
                        return
                    }
                    if(factories && dataOnly && WicketColumnConstruction.constructor(declared)) {
                        val boundary=columnConstruction.reviewConstructor(declared)
                        feature=true
                        val diagnostic=Diagnostic(DiagnosticCode.PARTIAL_ANALYSIS,
                            "Reviewed $boundary constructor storage; header/cell/detach callbacks, model values and sort state remain unknown.",location(expression))
                        if(diagnostic !in columnConstructionDiagnostics) {columnConstructionDiagnostics+=diagnostic;warnings+=diagnostic}
                        super.visitMethodCallExpression(expression)
                        return
                    }
                    val target = if (declared !is PsiCompiledElement && !declared.isConstructor && !declared.hasModifierProperty(PsiModifier.STATIC) &&
                        !declared.hasModifierProperty(PsiModifier.PRIVATE) && !declared.hasModifierProperty(PsiModifier.FINAL) &&
                        declared.containingClass?.let { it.qualifiedName == COMPONENT || InheritanceUtil.isInheritor(it, COMPONENT) } == true && expression.methodExpression.qualifierExpression !is PsiSuperExpression) {
                        val runtime = boundaryType ?: stop(expression, "Open component dispatch has no construction context.")
                        if (expression.methodExpression.qualifierExpression != null && expression.methodExpression.qualifierExpression !is PsiThisExpression)
                            stop(expression, "An unknown component receiver has open source dispatch.")
                        hierarchy(runtime).firstNotNullOfOrNull { it.findMethodBySignature(declared, false) } ?: declared
                    } else declared
                    if (dataOnly && !target.hasModifierProperty(PsiModifier.STATIC) && target.containingClass?.let { it.qualifiedName == COMPONENT || InheritanceUtil.isInheritor(it, COMPONENT) } == true)
                        stop(expression, "Opaque data invokes a component receiver.")
                    if (target.name in setOf("getParent", "getPage", "findParent", "remove", "replaceWith")) stop(expression, "Component construction can access or mutate an enclosing component.")
                    if (target.name in STRUCTURAL && target.containingClass?.let { it.qualifiedName == COMPONENT || InheritanceUtil.isInheritor(it, COMPONENT) } == true) {
                        val qualifier = expression.methodExpression.qualifierExpression
                        val variable = (qualifier as? PsiReferenceExpression)?.resolve()
                        val local = variable is PsiLocalVariable && freshLocal(variable.initializer,
                            Frame(Ref(rootId), boundaryType ?: methodOwner(body), null, mutableMapOf()), emptySet()) ||
                            variable is PsiField && ownedField(variable, qualifier as PsiReferenceExpression)
                        if (qualifier != null && qualifier !is PsiThisExpression && !local)
                            stop(expression, "Owned construction cannot prove the receiver of a structural call is local.")
                    }
                    if (dataOnly && graphType(expression.type)) stop(expression, "Opaque data evaluation can acquire a component reference.")
                    if (target !is PsiCompiledElement) auditLocal(target, visiting, dataOnly)
                    else if (!WicketComponentIdentity.frameworkConstructor(target) && !localFramework(target) && !dataMethod(target)) stop(expression, "An opaque compiled effect is outside the owned construction contract (${target.containingClass?.qualifiedName}.${target.name}).")
                    super.visitMethodCallExpression(expression)
                }
                override fun visitNewExpression(expression: PsiNewExpression) {
                    tick(expression)
                    val type = expression.classReference?.resolve() as? PsiClass
                    if (dataOnly && graphType(expression.type)) stop(expression, "Opaque data construction creates a component.")
                    (expression.anonymousClass ?: type)?.let(::inspectTable)
                    if(factories && type is PsiCompiledElement && type.qualifiedName==WicketDataTableConstruction.TABLE) {
                        if(expression.resolveConstructor()?.let(WicketDataTableConstruction::constructor)!=true)
                            stop(expression,"DataTable allocation does not resolve to its reviewed constructor.")
                        tableConstructorNote(expression)
                    }
                    if (type != null && type !is PsiCompiledElement) {
                        if(factories && dataOnly && WicketColumnConstruction.sourceColumn(type)) {feature=true;columnConstruction.inspect(type)}
                        initializeType(type, Frame(Ref(rootId), type, null, mutableMapOf()))
                        auditFields(type)
                        expression.resolveConstructor()?.let { auditLocal(it, visiting, dataOnly) }
                        type.methods.filter { isHook(it) || it.name == "bind" && InheritanceUtil.isInheritor(type, BEHAVIOR) }
                            .forEach { auditLocal(it, visiting, dataOnly) }
                    }
                    expression.argumentList?.expressions.orEmpty().forEach { argument ->
                        if (argument is PsiThisExpression && type is PsiCompiledElement &&
                            type.qualifiedName != "org.apache.wicket.model.StringResourceModel") stop(argument, "The component escapes into unreviewed compiled construction.")
                    }
                    expression.anonymousClass?.let { anonymous ->
                        anonymous.initializers.forEach { auditLocalBody(it.body, visiting, dataOnly) }
                        anonymous.fields.forEach { field -> field.initializer?.let { auditLocalBody(it, visiting, dataOnly) } }
                        anonymous.methods.filter(::isHook).forEach { auditLocal(it, visiting, dataOnly) }
                    }
                    super.visitNewExpression(expression)
                }
                override fun visitReferenceExpression(expression: PsiReferenceExpression) {
                    if (dataOnly && expression.resolve() !is PsiClass && expression.resolve() !is PsiMethod && graphType(expression.type)) stop(expression, "Opaque data evaluation references component state.")
                    val field = expression.resolve() as? PsiField
                    if (field?.hasModifierProperty(PsiModifier.STATIC) == true && component(field.type)) stop(expression, "A static component reference escapes the owned markup scope.")
                    super.visitReferenceExpression(expression)
                }
            })
        }

        private fun initializeType(type: PsiClass, frame: Frame) {
            tick(type)
            if (type is PsiCompiledElement || !initializedTypes.add(type)) return
            source(type)
            type.superClass?.let { initializeType(it, frame) }
            type.initializers.filter { it.hasModifierProperty(PsiModifier.STATIC) }.forEach { stop(it, "Static initializer blocks require a separate effect contract.") }
            type.fields.filter { it.hasModifierProperty(PsiModifier.STATIC) && it !is PsiEnumConstant }.forEach { field ->
                if (graphType(field.type)) stop(field, "Static component storage is outside the source layout effect contract.")
                field.initializer?.let { auditData(it, frame) }
            }
        }

        private fun methodOwner(element: PsiElement): PsiClass = PsiTreeUtil.getParentOfType(element, PsiClass::class.java)
            ?: stop(element, "A source owner is unavailable.")

        private fun ownedField(field: PsiField, reference: PsiReferenceExpression): Boolean {
            if (!field.hasModifierProperty(PsiModifier.PRIVATE) || !field.hasModifierProperty(PsiModifier.FINAL) || field.hasModifierProperty(PsiModifier.STATIC) ||
                reference.qualifierExpression != null && reference.qualifierExpression !is PsiThisExpression) return false
            source(field)
            val owner = field.containingClass ?: return false
            val values = listOfNotNull(field.initializer) + PsiTreeUtil.findChildrenOfType(owner, PsiAssignmentExpression::class.java)
                .filter { (it.lExpression as? PsiReferenceExpression)?.resolve() == field }.mapNotNull { it.rExpression }
            return values.isNotEmpty() && values.all { freshLocal(it, Frame(Ref(rootId), owner, null, mutableMapOf()), emptySet()) }
        }

        private fun auditBehavior(type: PsiClass, receiver: Ref, at: PsiElement) {
            hierarchy(type).forEach { owner ->
                owner.methods.filter { it.name == "bind" || factories && it.name in setOf("onBind", "getStatelessHint", "isTemporary") || isHook(it) }.forEach { method ->
                    source(method)
                    if (method.body?.statements?.isNotEmpty() == true) stop(at, "A source behavior overrides synchronous binding/initialization effects (${owner.qualifiedName}).")
                }
            }
            if (type.qualifiedName != BEHAVIOR && !InheritanceUtil.isInheritor(type, BEHAVIOR)) stop(at, "The attached value is not a reviewed Behavior.")
            // Framework AttributeModifier/AttributeAppender bind using Behavior's empty implementation.
            var owner: PsiClass? = type
            while (owner != null && owner.qualifiedName != BEHAVIOR) {
                tick(at)
                if (owner is PsiCompiledElement && owner.qualifiedName !in (setOf("org.apache.wicket.AttributeModifier", "org.apache.wicket.behavior.AttributeAppender") +
                    if (factories) setOf("org.apache.wicket.behavior.AbstractAjaxBehavior", "org.apache.wicket.ajax.AbstractDefaultAjaxBehavior") else emptySet())) stop(at, "Compiled behavior binding is not reviewed.")
                owner = owner.superClass
            }
        }

        /** Data is opaque, but evaluation/capture effects may not reach the component graph. */
        private fun auditData(expression: PsiExpression, frame: Frame): Value {
            tick(expression)
            val type = (expression as? PsiNewExpression)?.anonymousClass?.baseClassType?.resolve() ?: classType(expression.type)
            if (expression is PsiLambdaExpression || expression is PsiMethodReferenceExpression) {
                auditDeferred(expression, frame)
                val callback=LambdaUtil.getFunctionalInterfaceType(expression,true)
                return Data(classType(callback) ?: type,callbackScopeUnknown=callback !is PsiClassType || callback.resolve()==null)
            }
            if (expression is PsiNewExpression) {
                if(factories) {
                    val candidate=expression.anonymousClass ?: expression.classReference?.resolve() as? PsiClass
                    if(candidate?.let(WicketProviderConstruction::sourceProvider)==true) {
                        feature=true
                        val provider=providerConstruction.inspect(expression)!!
                        hierarchy(provider).forEach {owner -> owner.methods.filter {sourceDataSam(it) && !WicketProviderConstruction.callback(it)}
                            .forEach {auditLocal(it,mutableSetOf(),dataOnly=true)} }
                        val diagnostic=Diagnostic(DiagnosticCode.PARTIAL_ANALYSIS,
                            "Reviewed source data-provider construction; iterator/size/model/detach callbacks, page queries, row counts, sort state and model values remain unknown.",location(expression))
                        if(diagnostic !in columnConstructionDiagnostics) {columnConstructionDiagnostics+=diagnostic;warnings+=diagnostic}
                        return Data(provider)
                    }
                }
                type?.let { initializeType(it, frame) }
                val sourceColumn=factories && type?.let(WicketColumnConstruction::sourceColumn)==true
                if(sourceColumn) feature=true
                expression.argumentList?.expressions.orEmpty().forEach { argument ->
                    val value = evaluate(argument, frame)
                    if(sourceColumn && value is Data) {
                        if(value.callbackScopeUnknown) stop(argument,"Column callback interface initialization is unresolved or uses an unreviewed intersection target.")
                        columnConstruction.inspectInput(value.type)
                    }
                    if (value.let { it is Ref || it is Factory } && type?.qualifiedName != "org.apache.wicket.model.StringResourceModel") stop(argument, "A component escapes into opaque data construction.")
                    if (value is Data && value.constructorArgument) stop(argument, "An opaque constructor input escapes into data construction.")
                }
                if (type != null && (type.qualifiedName == BEHAVIOR || InheritanceUtil.isInheritor(type, BEHAVIOR))) {
                    hierarchy(type).forEach { owner -> owner.constructors.forEach { auditLocal(it, mutableSetOf(), dataOnly = true) } }
                    return Behavior(type)
                }
                if (type != null && type !is PsiCompiledElement) hierarchy(type).forEach { owner ->
                    if(factories && WicketColumnConstruction.sourceColumn(owner)) {feature=true;columnConstruction.inspect(owner)}
                    owner.constructors.forEach { auditLocal(it, mutableSetOf(), dataOnly = true) }
                    if (factories) {
                        owner.fields.filterNot { it.hasModifierProperty(PsiModifier.STATIC) }.forEach { it.initializer?.let { initializer -> auditLocalBody(initializer, mutableSetOf(), true) } }
                        owner.initializers.filterNot { it.hasModifierProperty(PsiModifier.STATIC) }.forEach { auditLocalBody(it.body, mutableSetOf(), true) }
                        if (listOf("java.util.Iterator", "org.apache.wicket.model.IModel", "org.apache.wicket.markup.repeater.data.IDataProvider").any { InheritanceUtil.isInheritor(type, it) })
                            owner.methods.filterNot { it.isConstructor || it.hasModifierProperty(PsiModifier.STATIC) }.forEach { auditLocal(it, mutableSetOf(), true) }
                        owner.methods.filter { sourceDataSam(it) && !(WicketColumnConstruction.sourceColumn(owner) && WicketColumnConstruction.deferredCallback(it)) }.forEach { auditLocal(it, mutableSetOf(), true) }
                        owner.methods.filter { it.name in setOf("toString", "hashCode", "equals", "compareTo") && !it.hasModifierProperty(PsiModifier.STATIC) }.forEach { auditLocal(it, mutableSetOf(), true) }
                    }
                }
                if (type is PsiCompiledElement && !dataLibrary(type.qualifiedName) && type.qualifiedName !in OPAQUE_CONSTRUCTORS) stop(expression, "Opaque library construction is not reviewed (${type.qualifiedName}).")
                expression.arrayInitializer?.initializers.orEmpty().forEach { argument ->
                    val value = evaluate(argument, frame)
                    if (value is Ref) stop(argument, "A component escapes into an opaque array.")
                    if (value is Data && value.constructorArgument) stop(argument, "An opaque constructor input escapes into an array.")
                }
                expression.arrayDimensions.forEach { evaluate(it, frame) }
                expression.anonymousClass?.let { anonymous ->
                    if (type?.qualifiedName != "org.apache.wicket.MetaDataKey" || anonymous.initializers.isNotEmpty() || anonymous.fields.isNotEmpty() || anonymous.methods.isNotEmpty())
                        stop(expression, "Opaque anonymous data initialization is unsupported.")
                }
                return Data(type)
            }
            if (expression is PsiMethodCallExpression) {
                if (factories && !initialContentDeclarations) storedActionDeclarations.inspect(expression)?.let { registration ->
                    feature = true
                    // The proof checks the actual fresh receiver and the complete registrar body.
                    // Do not audit or execute the stored callback as an initialization effect.
                    storedActions.addAll(registration)
                    return Data(type)
                }
                val method = expression.resolveMethod() ?: stop(expression, "An opaque-data call does not resolve.")
                val qualifier = expression.methodExpression.qualifierExpression
                val static = method.hasModifierProperty(PsiModifier.STATIC)
                if (static) method.containingClass?.let { initializeType(it, frame) }
                val receiver = if (static) null else qualifier?.let { evaluate(it, frame) }
                if (receiver is Ref) stop(expression, "An opaque-data call receives a component receiver.")
                if (receiver is Data && receiver.constructorArgument) stop(expression, "Opaque constructor input callbacks are not evaluated by a base-layout declaration.")
                if(factories && receiver is Data && receiver.type?.let(WicketProviderConstruction::sourceProvider)==true && WicketProviderConstruction.callback(method))
                    stop(expression,"Data-provider callback execution is outside the construction-only contract.")
                if (factories && receiver is Data && receiver.type != null && receiver.type !is PsiCompiledElement && method is PsiCompiledElement) {
                    hierarchy(receiver.type).firstNotNullOfOrNull { it.findMethodBySignature(method, false) }?.let { actual ->
                        auditLocal(actual, mutableSetOf(), dataOnly = true)
                    }
                }
                expression.argumentList.expressions.forEach { argument ->
                    val value = evaluate(argument, frame)
                    if (value is Ref || value is Factory) stop(argument, "A component or factory escapes into an opaque-data call.")
                    if (value is Data && value.constructorArgument) stop(argument, "An opaque constructor input escapes into a data call.")
                }
                if (method !is PsiCompiledElement) auditLocal(method, mutableSetOf(), dataOnly = true)
                else if (!dataMethod(method) && !(factories && method.containingClass?.qualifiedName in setOf("org.apache.wicket.markup.repeater.data.IDataProvider", "org.apache.wicket.markup.repeater.data.ListDataProvider") && method.name in setOf("size", "iterator", "model", "detach"))) stop(expression, "Opaque library data effects are not reviewed (${method.containingClass?.qualifiedName}.${method.name}).")
                if (type != null && (type.qualifiedName == BEHAVIOR || InheritanceUtil.isInheritor(type, BEHAVIOR))) return Behavior(type)
                if (factories && classType(expression.type)?.qualifiedName == "java.util.Iterator") return IteratorData(type)
                return Data(type)
            }
            expression.accept(object : JavaRecursiveElementWalkingVisitor() {
                override fun visitLambdaExpression(expression: PsiLambdaExpression) { auditDeferred(expression, frame) }
                override fun visitMethodReferenceExpression(expression: PsiMethodReferenceExpression) { auditDeferred(expression, frame) }
                override fun visitThisExpression(expression: PsiThisExpression) { stop(expression, "The receiver escapes into opaque data.") }
                override fun visitMethodCallExpression(expression: PsiMethodCallExpression) { evaluate(expression, frame) }
                override fun visitNewExpression(expression: PsiNewExpression) { evaluate(expression, frame) }
                override fun visitReferenceExpression(expression: PsiReferenceExpression) {
                    tick(expression)
                    if (component(expression.type)) stop(expression, "An opaque expression references component state.")
                    super.visitReferenceExpression(expression)
                }
            })
            return Data(type)
        }

        private fun auditDeferred(element: PsiElement, frame: Frame) {
            source(element)
            if (initialContentDeclarations) { initialCallbacks += location(element); return }
            if (factories) {
                val bodies = mutableListOf(element)
                if (element is PsiMethodReferenceExpression) (element.resolve() as? PsiMethod)?.takeUnless { it is PsiCompiledElement }?.body?.let(bodies::add)
                val seen = mutableSetOf<PsiMethod>()
                fun scan(body: PsiElement, depth: Int) {
                    if (depth > 12) stop(body, "Captured scalar effects exceed their source-call bound.")
                    body.accept(object : JavaRecursiveElementWalkingVisitor() {
                        override fun visitElement(element: PsiElement) { tick(element); super.visitElement(element) }
                        override fun visitMethodCallExpression(expression: PsiMethodCallExpression) {
                            val target = expression.resolveMethod()
                            if (target != null && target !is PsiCompiledElement && seen.add(target)) {
                                if (seen.size > 128) stop(expression, "Captured scalar effects exceed their source-call bound.")
                                source(target)
                                target.body?.let { scan(it, depth + 1) }
                            }
                            super.visitMethodCallExpression(expression)
                        }
                        private fun invalidate(expression: PsiExpression) {
                            val target = (expression as? PsiReferenceExpression)?.resolve() as? PsiVariable ?: return
                            if (target is PsiField) instances.values.forEach { instance ->
                                if (instance.fields[target] is Scalar) instance.fields[target] = Data(classType(target.type))
                            } else if (frame.values[target] is Scalar) frame.values[target] = Data(classType(target.type))
                        }
                        override fun visitAssignmentExpression(expression: PsiAssignmentExpression) { invalidate(expression.lExpression); super.visitAssignmentExpression(expression) }
                        override fun visitPostfixExpression(expression: PsiPostfixExpression) { invalidate(expression.operand); super.visitPostfixExpression(expression) }
                        override fun visitPrefixExpression(expression: PsiPrefixExpression) { if (expression.operationTokenType in setOf(JavaTokenType.PLUSPLUS, JavaTokenType.MINUSMINUS)) expression.operand?.let(::invalidate); super.visitPrefixExpression(expression) }
                    })
                }
                bodies.forEach { scan(it, 0) }
                deferred += element to frame.copy(values = frame.values.toMutableMap()); return
            }
            if (element is PsiLambdaExpression && element.body is PsiExpression && graphType((element.body as PsiExpression).type) ||
                element is PsiMethodReferenceExpression && (element.resolve() as? PsiMethod)?.let { graphType(it.returnType) } == true)
                stop(element, "A deferred value can expose a component rather than opaque model data.")
            element.accept(object : JavaRecursiveElementWalkingVisitor() {
                override fun visitNewExpression(expression: PsiNewExpression) {
                    tick(expression)
                    val constructor = expression.resolveConstructor()
                    expression.argumentList?.expressions.orEmpty().forEachIndexed { index, argument ->
                        val buttonForm = constructor?.containingClass?.qualifiedName == "org.apache.wicket.ajax.markup.html.form.AjaxButton" && index > 0 &&
                            classType(constructor.parameterList.parameters.getOrNull(index)?.type)?.qualifiedName == "org.apache.wicket.markup.html.form.Form"
                        if (graphType(argument.type) && !buttonForm) stop(argument, "A deferred constructor captures an existing component.")
                    }
                    (expression.classReference?.resolve() as? PsiClass)?.let { auditBoundary(it) }
                    super.visitNewExpression(expression)
                }
                override fun visitMethodCallExpression(expression: PsiMethodCallExpression) {
                    tick(expression)
                    val method = expression.resolveMethod() ?: stop(expression, "A deferred callback has an unresolved effect.")
                    val owner = method.containingClass?.qualifiedName
                    if (method.name in STRUCTURAL && !renderRequest(method) &&
                        method.containingClass?.let { it.qualifiedName == COMPONENT || InheritanceUtil.isInheritor(it, COMPONENT) } == true) {
                        // The layout's ListView callbacks may modify only their own item/local allocations.
                        val qualifier = expression.methodExpression.qualifierExpression
                        val reference = (qualifier as? PsiReferenceExpression)?.resolve()
                        if (reference !is PsiLocalVariable && reference !is PsiParameter ||
                            !PsiTreeUtil.isAncestor(element, reference, false)) stop(expression, "A deferred callback can mutate the captured component hierarchy.")
                        if (reference is PsiLocalVariable && frame.values[reference] is Ref || reference is PsiParameter && frame.values[reference] is Ref) stop(expression, "A deferred callback can mutate a known component attachment.")
                        if (reference is PsiLocalVariable && !freshLocal(reference.initializer, frame, emptySet())) stop(expression, "A deferred callback's local component may alias an enclosing scope.")
                    }
                    expression.argumentList.expressions.forEach { argument ->
                        if (argument is PsiThisExpression || graphType(argument.type)) {
                            val variable = (argument as? PsiReferenceExpression)?.resolve() as? PsiVariable
                            val readOnly = method !is PsiCompiledElement && graphReadOnly(method, emptySet()) || graphQuery(method)
                            if (!renderRequest(method) && !readOnly && (argument is PsiThisExpression ||
                                variable != null && (frame.values[variable] is Ref || !PsiTreeUtil.isAncestor(element, variable, false)) ||
                                variable == null && !freshLocal(argument, frame, emptySet())))
                                stop(argument, "A callback can reattach or expose a captured component.")
                            val localHelper = method !is PsiCompiledElement &&
                                method.containingClass in hierarchy(frame.owner) &&
                                (variable is PsiLocalVariable || variable is PsiParameter) && frame.values[variable] !is Ref &&
                                PsiTreeUtil.isAncestor(element, variable, false)
                            if (!renderRequest(method) && !readOnly && !localFramework(method) && !localHelper) stop(argument, "A callback escapes a component into unreviewed code.")
                        }
                    }
                    if (method !is PsiCompiledElement && method.containingClass?.let { it in hierarchy(frame.owner) } == true) auditDeferredMethod(method, frame)
                    super.visitMethodCallExpression(expression)
                }
                override fun visitMethodReferenceExpression(expression: PsiMethodReferenceExpression) {
                    tick(expression)
                    val method = expression.resolve() as? PsiMethod ?: stop(expression, "The deferred method reference does not resolve.")
                    if (method.name in STRUCTURAL) stop(expression, "A structural method reference escapes the layout scope.")
                    if (method !is PsiCompiledElement) auditDeferredMethod(method, frame)
                    super.visitMethodReferenceExpression(expression)
                }
                override fun visitAssignmentExpression(expression: PsiAssignmentExpression) {
                    val target = (expression.lExpression as? PsiReferenceExpression)?.resolve() as? PsiVariable
                    if (target != null && component(target.type)) stop(expression, "A callback changes a component reference outside construction.")
                    super.visitAssignmentExpression(expression)
                }
            })
        }

        private inner class Effects(private val formOwner: Ref? = null, private val formCaches: Map<PsiMethod, PsiField> = emptyMap()) {
            private inner class TableForward(val proof:WicketOwnedTableForwarding.Proof,val table:EffectRef,val toolbar:EffectRef,val owner:Ref)
            private inner class CachedToolbar(val proof:WicketToolbarCacheContract.Proof,val construction:TableForward,var registered:Boolean=false)
            private val tableForwards=mutableListOf<TableForward>()
            private val completedToolbars=mutableMapOf<PsiNewExpression,TableForward>()
            private val toolbarCaches=mutableMapOf<PsiField,CachedToolbar>()
            private var deferredDepth=0
            private val active = mutableSetOf<PsiMethod>()
            private val auditedMethods = mutableSetOf<PsiMethod>()
            private var calls = 0
            private var configurationOwner: Ref? = null
            private val cachedInputs = mutableMapOf<PsiField, EffectRef>()
            private val pendingCallbacks = mutableListOf<Pair<PsiElement, EffectFrame>>()
            private var drainingCallbacks = false
            private val visitorParameters = mutableSetOf<PsiParameter>()
            private val inputFactoryFields = mutableMapOf<PsiField, PsiMethodReferenceExpression>()

            fun constructForm(constructor: PsiMethod, values: List<Value>, caller: Frame) {
                val receiver = formOwner ?: stop(constructor, "Missing isolated form owner.")
                configurationOwner = receiver
                val type = instances.getValue(receiver.id).type
                hierarchy(type).forEach { owner ->
                    initializeType(owner, caller)
                    if (owner.initializers.isNotEmpty()) stop(owner, "Form initializer blocks are not reviewed.")
                }
                val ctx = EffectFrame(type, EffectRef(type, receiver), caller,
                    constructor.parameterList.parameters.zip(values.map(::token)).toMap().toMutableMap(), inputFactories =
                    constructor.parameterList.parameters.zip(values).mapNotNull { (parameter, value) ->
                        ((value as? Factory)?.expression as? PsiMethodReferenceExpression)?.let { expression ->
                            parameter to (inputFactory(expression, null) ?: stop(expression, "The owned input factory is not a constructor reference."))
                        }
                    }.toMap())
                hierarchy(type).forEach { owner -> owner.fields.filterNot { it.hasModifierProperty(PsiModifier.STATIC) }.forEach { field ->
                    field.initializer?.let { value(it, ctx.copy(owner = owner)) }
                } }
                method(constructor, ctx.copy(owner = constructor.containingClass!!))
                hierarchy(type).firstNotNullOfOrNull { it.methods.singleOrNull(::isHook) }?.let { method(it, ctx.copy(owner = it.containingClass!!)) }
                drainCallbacks()
            }

            private fun drainCallbacks() {
                if (drainingCallbacks) return
                drainingCallbacks = true
                try {
                    var index = 0
                    while (index < pendingCallbacks.size) {
                        if (index >= 128) stop(pendingCallbacks[index].first, "Owned form callbacks exceed their 128-callback bound.")
                        val (element, ctx) = pendingCallbacks[index++]
                        checkDeferredNow(element, ctx)
                    }
                    pendingCallbacks.clear()
                } finally { drainingCallbacks = false }
            }

            private fun inputFactory(expression: PsiExpression, ctx: EffectFrame?): PsiMethodReferenceExpression? = when (val value = unwrap(expression)) {
                is PsiMethodReferenceExpression -> {
                    val constructor = WicketComponentIdentity.unboundConstructor(value)
                    if (constructor == null) null else {
                        val type = constructor.containingClass ?: stop(value, "Input factory type is unavailable.")
                        if (type is PsiCompiledElement || type.containingClass != null && !type.hasModifierProperty(PsiModifier.STATIC) ||
                            !WicketComponentTypes.isFormComponent(type)) stop(value, "An owned input factory requires a source static/top-level form constructor.")
                        source(constructor)
                        value
                    }
                }
                is PsiReferenceExpression -> when (val variable = value.resolve()) {
                    is PsiField -> if (componentFactory(variable.type) && ctx != null && enclosing(ctx, variable.containingClass, value).known == formOwner) inputFactoryFields[variable] else null
                    is PsiVariable -> ctx?.inputFactories?.get(variable)
                    else -> null
                }
                is PsiMethodCallExpression -> if (value.resolveMethod()?.let { it.containingClass?.qualifiedName == "java.util.Objects" && it.name == "requireNonNull" } == true)
                    value.argumentList.expressions.firstOrNull()?.let { inputFactory(it, ctx) } else null
                else -> null
            }

            private fun createInput(factory: PsiMethodReferenceExpression, arguments: List<EffectRef?>, ctx: EffectFrame, at: PsiElement): EffectRef {
                val constructor = factory.resolve() as? PsiMethod ?: stop(factory, "The input constructor is unavailable.")
                if (constructor.parameterList.parametersCount != arguments.size || arguments.any { it != null }) stop(at, "Input constructor arguments must be isolated opaque data.")
                val type = constructor.containingClass!!
                initializeType(type, ctx.base)
                val local = EffectRef(type, fresh = true)
                val context = EffectFrame(type, local, ctx.base, constructor.parameterList.parameters.zip(arguments).toMap().toMutableMap(), ctx)
                hierarchy(type).forEach { owner -> owner.fields.filterNot { it.hasModifierProperty(PsiModifier.STATIC) }.forEach { field ->
                    field.initializer?.let { value(it, context.copy(owner = owner)) }
                }; owner.initializers.filterNot { it.hasModifierProperty(PsiModifier.STATIC) }.forEach { walk(it.body, context.copy(owner = owner, construction = true)) } }
                method(constructor, context)
                hierarchy(type).firstNotNullOfOrNull { it.methods.singleOrNull(::isHook) }?.let { method(it, context.copy(owner = it.containingClass!!)) }
                return local
            }

            private fun implicitSuper(type: PsiClass, ctx: EffectFrame, at: PsiElement) {
                val parent = type.superClass ?: return
                val constructor = parent.constructors.singleOrNull { it.parameterList.parametersCount == 0 }
                if (constructor != null) invoke(constructor, ctx.receiver, emptyList(), ctx, at)
                else if (parent !is PsiCompiledElement && parent.constructors.isEmpty()) { source(parent); implicitSuper(parent, ctx.copy(owner = parent), at) }
                else stop(at, "The implicit superclass constructor is unavailable or ambiguous.")
            }

            /** Recover only an unchanged local's allocation type; never guess a runtime data subtype. */
            private fun allocatedData(expression: PsiExpression, seen: Set<PsiElement> = emptySet()): PsiClass? {
                if (seen.size >= 12 || expression in seen) return null
                tick(expression)
                return when (val e = unwrap(expression)) {
                    is PsiNewExpression -> e.anonymousClass ?: e.classReference?.resolve() as? PsiClass
                    is PsiTypeCastExpression -> e.operand?.let { allocatedData(it, seen + e) }
                    is PsiReferenceExpression -> (e.resolve() as? PsiLocalVariable)?.let { local ->
                        val scope = PsiTreeUtil.getParentOfType(local, PsiMethod::class.java) ?: return@let null
                        if (PsiTreeUtil.findChildrenOfType(scope, PsiAssignmentExpression::class.java).any {
                                (it.lExpression as? PsiReferenceExpression)?.resolve() == local }) null
                        else local.initializer?.let { allocatedData(it, seen + e) }
                    }
                    else -> null
                }
            }
            private fun token(value: Value?): EffectRef? = (value as? Ref)?.let { EffectRef(instances.getValue(it.id).type, it) }
            private fun context(frame: Frame) = EffectFrame(frame.owner, EffectRef(instances.getValue(frame.receiver.id).type, frame.receiver), frame,
                frame.values.mapValues { token(it.value) }.toMutableMap())

            fun sourceCall(method: PsiMethod, receiver: Ref, values: List<Value>, caller: Frame): EffectRef? {
                configurationOwner = receiver
                val ctx = EffectFrame(method.containingClass!!, EffectRef(instances.getValue(receiver.id).type, receiver), caller,
                    method.parameterList.parameters.zip(values.map(::token)).toMap().toMutableMap())
                val returned = method(method, ctx)
                drainCallbacks()
                return returned
            }

            fun deferred(element: PsiElement, frame: Frame) {
                var ctx = context(frame)
                if (element is PsiLambdaExpression && element in registeredItemCallbacks) {
                    val item = element.parameterList.parameters.first()
                    ctx.locals[item] = EffectRef(classType(item.type)!!, fresh = true)
                }
                val kind = WicketRepeaterContract.kind(instances.getValue(frame.receiver.id).type)
                if (element is PsiMethod && kind != null && WicketRepeaterContract.callback(element, kind)) {
                    element.parameterList.parameters.forEach { p -> if (component(p.type)) ctx.locals[p] = EffectRef(classType(p.type)!!, fresh = true) }
                    if (kind == Repetition.REPEATING_VIEW) ctx = ctx.copy(receiver = ctx.receiver.copy(fresh = true))
                }
                checkDeferred(element, ctx)
            }

            private fun enclosing(ctx: EffectFrame, owner: PsiClass?, at: PsiElement): EffectRef {
                var current: EffectFrame? = ctx
                while (current != null) {
                    if (owner == null || owner in hierarchy(current.receiver.type) || owner is PsiCompiledElement &&
                        (current.receiver.type == owner || InheritanceUtil.isInheritor(current.receiver.type, owner.qualifiedName ?: ""))) return current.receiver
                    current = current.enclosing
                }
                return token(lexical(ctx.base.receiver, owner, at)) ?: stop(at, "Unknown effect receiver.")
            }

            private fun checkDeferred(element: PsiElement, ctx: EffectFrame) {
                if (formOwner != null && !drainingCallbacks) { pendingCallbacks += element to ctx; return }
                checkDeferredNow(element, ctx)
            }

            private fun checkDeferredNow(element: PsiElement, ctx: EffectFrame) {
                deferredDepth++
                try {when (element) {
                    is PsiLambdaExpression -> {
                        val body = element.body
                        if (body is PsiExpression) {
                            val result = value(body, ctx)
                            val returnsVoid = LambdaUtil.getFunctionalInterfaceReturnType(LambdaUtil.getFunctionalInterfaceType(element, true))?.canonicalText == "void"
                            if (result != null && !returnsVoid) stop(body, "A deferred value exposes a component.")
                        } else body?.let {
                            val results = mutableListOf<EffectRef?>()
                            walk(it, ctx, results)
                            val returnsVoid = LambdaUtil.getFunctionalInterfaceReturnType(LambdaUtil.getFunctionalInterfaceType(element, true))?.canonicalText == "void"
                            if (!returnsVoid && results.any { result -> result != null }) stop(element, "A deferred block exposes a component.")
                        }
                    }
                    is PsiMethodReferenceExpression -> {
                        val method = element.resolve() as? PsiMethod ?: stop(element, "Deferred method reference is unresolved.")
                        val receiver = if (method.hasModifierProperty(PsiModifier.STATIC)) null else element.qualifierExpression?.let {
                            value(it, ctx) ?: if (formOwner != null) allocatedData(it)?.let { type -> EffectRef(type, fresh = true) }
                                ?: EffectRef(method.containingClass!!, borrowed = true) else null
                        } ?: ctx.receiver
                        val result = invoke(method, receiver, emptyList(), ctx, element)
                        val returnsVoid = LambdaUtil.getFunctionalInterfaceReturnType(LambdaUtil.getFunctionalInterfaceType(element, true))?.canonicalText == "void"
                        if (result != null && !returnsVoid) stop(element, "A deferred method reference exposes a component.")
                    }
                    is PsiMethod -> {
                        val result = method(element, ctx)
                        if (result != null && element.returnType?.canonicalText != "void") stop(element, "A deferred method exposes a component.")
                    }
                    else -> walk(element, ctx)
                }} finally {deferredDepth--}
            }

            private fun insideExpanded(ref: Ref): Boolean {
                var current: String? = ref.id
                val seen = mutableSetOf<String>()
                while (current != null && seen.add(current)) {
                    if (instances[current]?.node?.sourceSetupExpanded == true) return true
                    current = instances[current]?.parents?.singleOrNull()
                }
                return false
            }

            private fun field(field: PsiField, qualifier: PsiExpression?, ctx: EffectFrame): EffectRef? {
                val qualified = qualifier?.let { value(it, ctx) }
                if(qualified?.columns!=null) stop(field,"An opaque columns list cannot expose instance fields.")
                if (!component(field.type)) return null
                if (field.hasModifierProperty(PsiModifier.STATIC)) stop(field, "Static component storage escapes the factory scope.")
                val receiver = qualified ?: if (field.containingClass in hierarchy(ctx.receiver.type)) ctx.receiver
                    else enclosing(ctx, field.containingClass, field)
                val known = receiver?.known?.let { instances[it.id]?.fields?.get(field) }
                token(known)?.let { return it }
                if (formOwner != null && receiver?.known == formOwner) cachedInputs[field]?.let { return it }
                if (field.hasModifierProperty(PsiModifier.PRIVATE) && field.hasModifierProperty(PsiModifier.FINAL)) {
                    val owner = field.containingClass!!
                    val initializers = listOfNotNull(field.initializer) + PsiTreeUtil.findChildrenOfType(owner, PsiAssignmentExpression::class.java)
                        .filter { (it.lExpression as? PsiReferenceExpression)?.resolve() == field }.mapNotNull { it.rExpression }
                    if (initializers.isNotEmpty() && initializers.all { freshLocal(it, ctx.base, emptySet()) })
                        return EffectRef(classType(field.type)!!, fresh = true)
                }
                return EffectRef(classType(field.type)!!, borrowed = true)
            }

            private fun value(expression: PsiExpression, ctx: EffectFrame): EffectRef? {
                tick(expression)
                return when (val value = unwrap(expression)) {
                    is PsiTypeCastExpression -> value.operand?.let { value(it, ctx) }
                        ?: if (component(value.type)) EffectRef(classType(value.type)!!, borrowed = true) else null
                    is PsiThisExpression -> (if (value.qualifier == null || value.qualifier!!.resolve() == ctx.owner) ctx.receiver
                        else enclosing(ctx, value.qualifier!!.resolve() as? PsiClass, value)).takeIf {
                            it.known != null || it.type.qualifiedName == COMPONENT || InheritanceUtil.isInheritor(it.type, COMPONENT) }
                    is PsiSuperExpression -> ctx.receiver
                    is PsiLambdaExpression, is PsiMethodReferenceExpression -> {
                        if (formOwner == null || value !is PsiMethodReferenceExpression || inputFactory(value, ctx) == null) checkDeferred(value, ctx)
                        null
                    }
                    is PsiReferenceExpression -> when (val target = value.resolve()) {
                        is PsiField -> {
                            if (target.hasModifierProperty(PsiModifier.STATIC)) target.containingClass?.let { initializeType(it, ctx.base) }
                            toolbarCaches[target]?.let {cached ->
                                if(deferredDepth!=0 || enclosing(ctx,target.containingClass,value).known!=cached.construction.owner)
                                    stop(value,"The cached toolbar reference leaves its source configuration scope.")
                                return cached.construction.toolbar
                            }
                            field(target, value.qualifierExpression, ctx)
                        }
                        is PsiVariable -> if (ctx.locals.containsKey(target)) ctx.locals[target] else token(ctx.base.values[target])
                            ?: if (component(target.type)) EffectRef(classType(target.type)!!, borrowed = true) else null
                        else -> null
                    }
                    is PsiNewExpression -> {
                        if (value.type is PsiArrayType) {
                            value.arrayDimensions.forEach { value(it, ctx) }
                            value.arrayInitializer?.initializers.orEmpty().forEach { if (value(it, ctx) != null) stop(it, "A component escapes into an opaque array.") }
                            return null
                        }
                        val type = value.anonymousClass ?: value.classReference?.resolve() as? PsiClass ?: return null
                        initializeType(type, ctx.base)
                        if (!value.resolveMethodGenerics().isValidResult) stop(value, "Deferred constructor resolution is incomplete.")
                        val parameters = value.argumentList?.expressions.orEmpty().map { value(it, ctx) }
                        if(parameters.any {it?.columns!=null}) stop(value,"An opaque columns list escapes into construction.")
                        val forwarding=if(factories && formOwner==null && parameters.singleOrNull()!=null && WicketOwnedTableForwarding.candidate(value)) {
                            val owner=ctx.receiver.known
                            if(owner==null || owner!=configurationOwner || instances.getValue(owner.id).parents.isNotEmpty() || ctx.receiver.type!=ctx.owner ||
                                parameters.single()!!.borrowed || !parameters.single()!!.fresh)
                                stop(value,"Owned table forwarding requires a fresh unattached configuration owner and its own table.")
                            ownedTableForwarding.inspect(value,ctx.owner)
                        } else null
                        parameters.forEachIndexed { index, ref -> if (ref != null && !(type.qualifiedName == FRAGMENT && index == 2) &&
                            type.qualifiedName != "org.apache.wicket.model.StringResourceModel" && forwarding==null) stop(value, "A component escapes into deferred construction.") }
                        val local = EffectRef(type, fresh = true)
                        if (type is PsiCompiledElement && value.resolveConstructor()?.let { constructor ->
                            !WicketComponentIdentity.frameworkConstructor(constructor) && !(formOwner != null && formConstructor(constructor)) && !dataLibrary(type.qualifiedName) && type.qualifiedName !in OPAQUE_CONSTRUCTORS &&
                                type.qualifiedName !in setOf(BEHAVIOR, "org.apache.wicket.behavior.AbstractAjaxBehavior", "org.apache.wicket.ajax.AbstractDefaultAjaxBehavior", "jakarta.servlet.http.Cookie")
                        } == true) stop(value, "A deferred constructor's compiled effects are not reviewed.")
                        if (type !is PsiCompiledElement) {
                            val constructor = value.resolveConstructor()
                            if (formOwner != null && constructor == null) {
                                if (type.constructors.isNotEmpty()) stop(value, "Source data construction does not resolve uniquely.")
                                implicitSuper(type, ctx.copy(owner = type, receiver = local, enclosing = ctx), value)
                            }
                            constructor?.let {
                                if (it is PsiCompiledElement) {
                                    val owner = it.containingClass?.qualifiedName
                                    if (!WicketComponentIdentity.frameworkConstructor(it) && !(formOwner != null && formConstructor(it)) && !dataLibrary(owner) && owner !in OPAQUE_CONSTRUCTORS &&
                                        owner !in setOf(BEHAVIOR, "org.apache.wicket.behavior.AbstractAjaxBehavior", "org.apache.wicket.ajax.AbstractDefaultAjaxBehavior"))
                                        stop(value, "A deferred constructor's compiled effects are not reviewed.")
                                } else {
                                    if(forwarding!=null) tableForwards+=TableForward(forwarding,parameters.single()!!,local,ctx.receiver.known!!)
                                    try {method(it, EffectFrame(type, local, ctx.base, it.parameterList.parameters.zip(parameters).toMap().toMutableMap(), ctx))}
                                    finally {if(forwarding!=null) tableForwards.removeAt(tableForwards.lastIndex)}
                                }
                            }
                            hierarchy(type).forEach { owner ->
                                owner.fields.forEach { it.initializer?.let { expression -> value(expression, ctx.copy(owner = owner, receiver = local)) } }
                                owner.methods.filter { isHook(it) || it.name in setOf("bind", "onBind", "getStatelessHint") && InheritanceUtil.isInheritor(type, BEHAVIOR) }
                                    .forEach { method(it, ctx.copy(owner = owner, receiver = local)) }
                                if (formOwner != null) owner.initializers.filterNot { it.hasModifierProperty(PsiModifier.STATIC) }
                                    .forEach { walk(it.body, ctx.copy(owner = owner, receiver = local, enclosing = ctx, construction = true)) }
                                if (formOwner != null && !component(value.type)) owner.methods.filter { it.name in setOf("toString", "hashCode", "equals", "compareTo") &&
                                    !it.isConstructor && !it.hasModifierProperty(PsiModifier.STATIC) || sourceDataSam(it) ||
                                    InheritanceUtil.isInheritor(type, "org.apache.wicket.model.IModel") && it.name in setOf("getObject", "getObjectClass") }.forEach { callback ->
                                    checkDeferred(callback, ctx.copy(owner = owner, receiver = local, enclosing = ctx))
                                }
                            }
                        }
                        value.anonymousClass?.methods?.filterNot { it.isConstructor }?.forEach { callback ->
                            val localContext = ctx.copy(owner = type, receiver = local, locals = ctx.locals.toMutableMap(), enclosing = ctx)
                            val itemCallback = WicketRepeaterContract.kind(type)?.let { WicketRepeaterContract.callback(callback, it) } == true
                            if (itemCallback) callback.parameterList.parameters.forEach { p -> if (component(p.type)) localContext.locals[p] = EffectRef(classType(p.type)!!, fresh = true) }
                            if (!itemCallback && callback.parameterList.parameters.any { component(it.type) } &&
                                callback.findDeepestSuperMethods().none { it.containingClass?.qualifiedName?.startsWith("org.apache.wicket.") == true }) {
                                if (callback !in auditedMethods) stop(callback, "Custom callback component ownership is not established.")
                            } else checkDeferred(callback, localContext)
                        }
                        if(forwarding!=null) completedToolbars[value]=TableForward(forwarding,parameters.single()!!,local,ctx.receiver.known!!)
                        local.takeIf { component(value.type) }
                    }
                    is PsiMethodCallExpression -> {
                        val declared = value.resolveMethod() ?: stop(value, "A callback call is unresolved.")
                        val qualifier = value.methodExpression.qualifierExpression
                        val receiver = if (declared.hasModifierProperty(PsiModifier.STATIC)) null else if (declared.isConstructor) ctx.receiver else if (qualifier != null)
                            value(qualifier, ctx) ?: if (formOwner != null) allocatedData(qualifier)?.let { EffectRef(it, fresh = true) } else null
                            else if (declared.containingClass?.let { it.qualifiedName == COMPONENT || InheritanceUtil.isInheritor(it, COMPONENT) } == true)
                                enclosing(ctx, declared.containingClass, value) else null
                        if (formOwner != null && declared.containingClass?.qualifiedName == "org.apache.wicket.MarkupContainer" && declared.name == "visitChildren" &&
                            receiver != null && (receiver.fresh || receiver.known == formOwner)) {
                            if (value.argumentList.expressions.lastOrNull() !is PsiLambdaExpression)
                                stop(value, "Owned form traversal requires an inline reviewed visitor.")
                            value.argumentList.expressions.filterIsInstance<PsiLambdaExpression>().forEach { lambda ->
                                lambda.parameterList.parameters.filter { classType(it.type)?.qualifiedName == "org.apache.wicket.util.visit.IVisit" }.forEach(visitorParameters::add)
                            }
                        }
                        if (formOwner != null && !declared.isConstructor && !(declared.containingClass?.qualifiedName == "java.util.Objects" && declared.name == "requireNonNull") &&
                            value.argumentList.expressions.any { inputFactory(it, ctx) != null }) stop(value, "An input factory escapes its reviewed constructor/field forwarding.")
                        val arguments = value.argumentList.expressions.map { value(it, ctx) }
                        val factoryField = (qualifier as? PsiReferenceExpression)?.resolve() as? PsiField
                        if (factoryField != null && componentFactory(factoryField.type)) {
                            if (declared != LambdaUtil.getFunctionalInterfaceMethod(factoryField.type)) stop(value, "Input/component factories may only invoke their reviewed SAM method.")
                            if (formOwner != null && factoryField in inputFactoryFields) return createInput(inputFactoryFields.getValue(factoryField), arguments, ctx, value)
                            val owner = enclosing(ctx, factoryField.containingClass, value).known
                                ?: stop(value, "A deferred factory has no known owning instance.")
                            val factory = instances[owner.id]?.fields?.get(factoryField) as? Factory
                            if (factory == null || factory.identity !in validatedFactories) stop(value, "An unreviewed component factory escapes to deferred code.")
                            return EffectRef(classType(declared.returnType) ?: JavaPsiFacade.getInstance(page.project).findClass(COMPONENT, page.resolveScope)!!, fresh = true)
                        }
                        invoke(declared, receiver, arguments, ctx, value)
                    }
                    is PsiConditionalExpression -> {
                        value.condition?.let { value(it, ctx) }
                        val first = value.thenExpression?.let { value(it, ctx) }
                        val second = value.elseExpression?.let { value(it, ctx) }
                        if(first!=second && (first?.columns!=null || second?.columns!=null)) stop(value,"Conditional columns list identity is not reconciled.")
                        if (first == second) first else if (first?.fresh == true && second?.fresh == true) first
                        else if (first != null || second != null) EffectRef(first?.type ?: second!!.type, borrowed = true) else null
                    }
                    is PsiArrayAccessExpression -> {
                        if(value(value.arrayExpression, ctx)?.columns!=null) stop(value,"An opaque columns list cannot become array storage.")
                        value.indexExpression?.let { value(it, ctx) }
                        if (component(value.type)) EffectRef(classType(value.type)!!, borrowed = true) else null
                    }
                    is PsiBinaryExpression -> {
                        val first = value(value.lOperand, ctx); val second = value.rOperand?.let { value(it, ctx) }
                        if (value.operationTokenType !in setOf(JavaTokenType.EQEQ, JavaTokenType.NE) && (first != null || second != null)) stop(value, "An opaque calculation consumes a component.")
                        null
                    }
                    is PsiPolyadicExpression -> {
                        val parts = value.operands.map { value(it, ctx) }
                        if (parts.any { it != null }) stop(value, "An opaque calculation consumes a component.")
                        null
                    }
                    is PsiAssignmentExpression -> {
                        val result = value.rExpression?.let { value(it, ctx) }
                        val target = (value.lExpression as? PsiReferenceExpression)?.resolve() as? PsiVariable
                        val constructed=(value.rExpression as? PsiNewExpression)?.let {completedToolbars[it]}
                        if(formOwner==null && deferredDepth==0 && !ctx.construction && constructed!=null && target is PsiField) {
                            if(ctx.receiver.known!=configurationOwner || ctx.receiver.known!=constructed.owner || result!=constructed.toolbar)
                                stop(value,"The cache assignment does not belong to its reviewed source configuration owner.")
                            val proof=toolbarCacheContract.inspect(value,constructed.proof)
                            if(toolbarCaches.putIfAbsent(target,CachedToolbar(proof,constructed))!=null)
                                stop(value,"The cached toolbar is assigned more than once in this source configuration.")
                            return null
                        }
                        if (formOwner != null && target is PsiField && componentFactory(target.type)) {
                            val factory = value.rExpression?.let { inputFactory(it, ctx) }
                            val qualifier = (value.lExpression as PsiReferenceExpression).qualifierExpression
                            if (!ctx.construction || ctx.receiver.known != formOwner || !target.hasModifierProperty(PsiModifier.PRIVATE) ||
                                !target.hasModifierProperty(PsiModifier.FINAL) || target.hasModifierProperty(PsiModifier.STATIC) ||
                                target.containingClass !in hierarchy(ctx.receiver.type) || qualifier != null && qualifier !is PsiThisExpression ||
                                factory == null) stop(value, "An input factory field is not a private final constructor-owned value.")
                            inputFactoryFields[target] = factory
                        }
                        if (target is PsiLocalVariable) {
                            val previous = ctx.locals[target]
                            if(previous?.columns!=null || result?.columns!=null) stop(value,"Columns list reassignment is outside its read-only local declaration contract.")
                            ctx.locals[target] = if (previous == result || previous == null && result == null) result
                                else if (previous?.fresh == true && result?.fresh == true) result
                                else EffectRef(classType(target.type)?.takeIf { it.qualifiedName == COMPONENT || InheritanceUtil.isInheritor(it, COMPONENT) }
                                    ?: JavaPsiFacade.getInstance(page.project).findClass(COMPONENT, page.resolveScope)!!, borrowed = true)
                        }
                        else if (target != null && (component(target.type) || result != null)) {
                            val field = target as? PsiField
                            val qualifier = (value.lExpression as? PsiReferenceExpression)?.qualifierExpression
                            val ownedCache = formOwner != null && ctx.receiver.known == formOwner && field in formCaches.values && result?.fresh == true
                            val localInitialization = (ctx.construction && (ctx.receiver.fresh || formOwner != null && ctx.receiver.known == formOwner) && field != null &&
                                field.hasModifierProperty(PsiModifier.FINAL) || ownedCache) && field != null && !field.hasModifierProperty(PsiModifier.STATIC) &&
                                field.containingClass in hierarchy(ctx.receiver.type) && (qualifier == null || qualifier is PsiThisExpression) && result?.fresh == true
                            if (!localInitialization) stop(value, "A callback changes or exposes a component reference.")
                            if (ownedCache) cachedInputs[field!!] = result!!
                        }
                        null
                    }
                    else -> { walk(value, ctx); null }
                }
            }

            private fun superMethods(method: PsiMethod): Set<PsiMethod> {
                val result = mutableSetOf<PsiMethod>()
                val pending = ArrayDeque(method.findSuperMethods().toList())
                while (pending.isNotEmpty()) {
                    tick(method)
                    val next = pending.removeFirst()
                    if (result.add(next)) { if (result.size > 32) stop(method, "Effect dispatch exceeds its bound."); pending.addAll(next.findSuperMethods()) }
                }
                return result
            }

            private fun invoke(declared: PsiMethod, receiver: EffectRef?, arguments: List<EffectRef?>, ctx: EffectFrame, at: PsiElement): EffectRef? {
                tick(at)
                if(arguments.any {it?.columns!=null}) stop(at,"An opaque columns list escapes its size-only contract.")
                if(receiver?.columns!=null) {
                    if(deferredDepth==0 || tableForwards.none {it.proof.tableField==(receiver.columns.methodExpression.qualifierExpression as? PsiReferenceExpression)?.resolve()} ||
                        at !is PsiMethodCallExpression || !at.resolveMethodGenerics().isValidResult || at.argumentList.expressionCount!=0 || !tableColumnsRead.size(declared))
                        stop(at,"An opaque columns list operation is outside its size-only contract (${declared.containingClass?.qualifiedName}.${declared.name}).")
                    source(declared);return null
                }
                val target = if (receiver != null && !declared.isConstructor && !declared.hasModifierProperty(PsiModifier.STATIC) && !declared.hasModifierProperty(PsiModifier.PRIVATE) &&
                    !(at is PsiMethodCallExpression && at.methodExpression.qualifierExpression is PsiSuperExpression))
                    hierarchy(receiver.type).firstNotNullOfOrNull { owner -> owner.methods.firstOrNull { candidate ->
                        candidate == declared || !candidate.isConstructor && superMethods(candidate).contains(declared)
                    } } ?: declared else declared
                if (target !is PsiCompiledElement) {
                    if (target.hasModifierProperty(PsiModifier.STATIC)) target.containingClass?.let { initializeType(it, ctx.base) }
                    if (LambdaUtil.getFunctionalInterfaceMethod(target.containingClass?.let { JavaPsiFacade.getElementFactory(page.project).createType(it) }) == target &&
                        target.returnType?.canonicalText == "void" && arguments.none { it != null }) return null
                    if (formOwner != null && target.containingClass?.isInterface == true &&
                        LambdaUtil.getFunctionalInterfaceMethod(JavaPsiFacade.getElementFactory(page.project).createType(target.containingClass!!)) == target &&
                        !graphType(target.returnType) && !component((at as? PsiExpression)?.type) && arguments.none { it != null }) return null
                    if (formOwner != null && !target.isConstructor && !target.hasModifierProperty(PsiModifier.STATIC) &&
                        target.containingClass?.let { it.qualifiedName != COMPONENT && !InheritanceUtil.isInheritor(it, COMPONENT) && !it.isInterface &&
                            !it.hasModifierProperty(PsiModifier.FINAL) && !it.isRecord } == true && !target.hasModifierProperty(PsiModifier.PRIVATE) && !target.hasModifierProperty(PsiModifier.FINAL) &&
                        (receiver == null || receiver.borrowed)) stop(at, "An owned form data receiver has open source dispatch.")
                    val self = receiver ?: if (target.containingClass?.let { it.qualifiedName == COMPONENT || InheritanceUtil.isInheritor(it, COMPONENT) } == true) ctx.receiver
                        else EffectRef(target.containingClass!!)
                    return method(target, EffectFrame(target.containingClass!!, self, ctx.base,
                        target.parameterList.parameters.zip(arguments).toMap().toMutableMap(), ctx, inputFactories = if (formOwner != null && at is PsiMethodCallExpression)
                            target.parameterList.parameters.zip(at.argumentList.expressions).mapNotNull { (parameter, argument) -> inputFactory(argument, ctx)?.let { parameter to it } }.toMap()
                        else emptyMap()))
                }
                val owner = target.containingClass?.qualifiedName
                val cached=toolbarCaches.values.singleOrNull {it.proof.registration==at}
                if(cached!=null && WicketDataTableToolbars.registrationMethod(target)) {
                    if(cached.registered || deferredDepth!=0 || ctx.receiver.known!=cached.construction.owner ||
                        receiver!=cached.construction.table || arguments!=listOf(cached.construction.toolbar))
                        stop(at,"The cached toolbar registration is outside its same-table initialization scope.")
                    source(target);cached.registered=true
                    val diagnostic=Diagnostic(DiagnosticCode.PARTIAL_ANALYSIS,
                        "Reviewed source null-guarded owned toolbar cache assignment and same-table registration; cache presence/reuse, toolbar IDs, private contents, models, rendering and later state remain unknown. Source branches and callbacks are not executed.",location(cached.proof.assignment))
                    if(diagnostic !in columnConstructionDiagnostics) {columnConstructionDiagnostics+=diagnostic;warnings+=diagnostic}
                    return null
                }
                val forwarded=tableForwards.lastOrNull()
                if(forwarded!=null && deferredDepth>0 && at is PsiMethodCallExpression && WicketTableColumnsRead.query(target)) {
                    if(enclosing(ctx,forwarded.proof.tableField.containingClass,at).known!=forwarded.owner)
                        stop(at,"The columns query has no matching source configuration owner.")
                    tableColumnsRead.inspect(at,forwarded.proof,instances.getValue(forwarded.owner.id).allocation)
                    val diagnostic=Diagnostic(DiagnosticCode.PARTIAL_ANALYSIS,
                        "Reviewed owned DataTable columns getter with exact JDK List.of provenance; only guarded size reads are permitted. Column count/elements, model values, rendering, callback execution and later state remain unknown.",location(at))
                    if(diagnostic !in columnConstructionDiagnostics) {columnConstructionDiagnostics+=diagnostic;warnings+=diagnostic}
                    return EffectRef(classType(target.returnType)!!,borrowed=true,columns=at)
                }
                if(forwarded!=null && at==forwarded.proof.delegation && ctx.construction && receiver==forwarded.toolbar && arguments==listOf(forwarded.table) &&
                    target==forwarded.proof.delegation.resolveMethod() && target.isConstructor && owner==WicketDataTableToolbars.TOOLBAR) {
                    source(target)
                    val diagnostic=Diagnostic(DiagnosticCode.PARTIAL_ANALYSIS,
                        "Reviewed owned DataTable reference forwarding into AbstractToolbar construction; toolbar IDs, private contents, cache/attachment state, model values, rendering and later callbacks remain unknown. Remaining source constructor effects are independently audited.",location(forwarded.proof.allocation))
                    if(diagnostic !in columnConstructionDiagnostics) {columnConstructionDiagnostics+=diagnostic;warnings+=diagnostic}
                    return null
                }
                if (formOwner != null && !drainingCallbacks && owner in setOf(COMPONENT, "org.apache.wicket.IGenericComponent", "org.apache.wicket.model.IModel") &&
                    target.name in setOf("setDefaultModelObject", "setModelObject", "setObject"))
                    stop(at, "Model value updates during owned form setup require a model-callback ownership contract.")
                if (formOwner != null && owner == "org.apache.wicket.util.visit.IVisit" && target.name == "stop" && arguments.all { it == null } &&
                    ((at as? PsiMethodCallExpression)?.methodExpression?.qualifierExpression as? PsiReferenceExpression)?.resolve() in visitorParameters) return null
                if (target.name in STRUCTURAL && owner in setOf(COMPONENT, "org.apache.wicket.MarkupContainer")) {
                    if (target.name in setOf("remove", "replaceWith", "setId") || receiver == null || receiver.borrowed ||
                        !receiver.fresh && (receiver.known == null || receiver.known.id == rootId || !insideExpanded(receiver.known) && receiver.known != configurationOwner))
                        stop(at, "A callback can mutate an enclosing or captured component hierarchy.")
                    if (arguments.any { it != null && !it.fresh }) stop(at, "A callback can reattach a captured component.")
                    return receiver
                }
                if (owner == COMPONENT && target.name in setOf("getParent", "getPage", "findParent"))
                    return if (formOwner != null && target.name == "findParent" && receiver?.fresh == true && at is PsiMethodCallExpression &&
                        at.argumentList.expressions.singleOrNull()?.let { (it as? PsiClassObjectAccessExpression)?.operand?.type }?.let { requested ->
                            classType(requested)?.let { instances.getValue(formOwner.id).type == it || instances.getValue(formOwner.id).type.isInheritor(it, true) }
                        } == true) token(formOwner) else EffectRef(classType(target.returnType) ?: receiver!!.type, borrowed = true)
                if (renderRequest(target) || formOwner != null && owner in setOf("org.apache.wicket.ajax.AjaxRequestTarget", "org.apache.wicket.core.request.handler.IPartialPageRequestHandler") && target.name == "focusComponent" || graphQuery(target) ||
                    owner == BEHAVIOR && target.name in setOf("onComponentTag", "renderHead", "detach", "onConfigure")) return null
                if (arguments.any { it != null } && !(owner == "org.apache.wicket.model.StringResourceModel" && target.isConstructor))
                    stop(at, "A callback exposes a component into opaque code.")
                if (localFramework(target) || dataMethod(target) || WicketComponentIdentity.frameworkConstructor(target) || formOwner != null && (formConstructor(target) ||
                    target.isConstructor && owner in setOf(BEHAVIOR, "org.apache.wicket.behavior.AbstractAjaxBehavior", "org.apache.wicket.ajax.AbstractDefaultAjaxBehavior")) ||
                    formOwner != null && (owner == COMPONENT && target.name in setOf("hasErrorMessage", "getFeedbackMessages", "onAfterRender") ||
                        owner == WicketComponentTypes.FORM_COMPONENT && target.name in setOf("isRequired", "getLabel") ||
                        owner == "org.apache.wicket.MarkupContainer" && target.name == "visitChildren" ||
                        owner in setOf(COMPONENT, WicketComponentTypes.FORM_COMPONENT, "org.apache.wicket.markup.html.form.TextField", "org.apache.wicket.markup.html.form.NumberTextField", "org.apache.wicket.markup.html.form.TextArea", "org.apache.wicket.markup.html.form.DropDownChoice", "org.apache.wicket.extensions.ajax.markup.html.autocomplete.AutoCompleteTextField") && target.name == "onComponentTag" ||
                        owner == "org.apache.wicket.feedback.FeedbackMessages" && target.name == "first" ||
                        owner == "org.apache.wicket.feedback.FeedbackMessage" && target.name in setOf("markRendered", "getMessage")) ||
                    formOwner != null && (owner == "java.text.NumberFormat" && target.name in setOf("getCurrencyInstance", "getNumberInstance", "setCurrency", "setMinimumFractionDigits", "setMaximumFractionDigits", "setRoundingMode") ||
                        owner == "java.text.DecimalFormat" && target.name == "getPositiveSuffix" ||
                        owner == "org.apache.wicket.util.convert.converter.BigDecimalConverter" && target.isConstructor ||
                        owner == COMPONENT && target.name == "createConverter") ||
                    formOwner != null && (owner == "org.apache.wicket.extensions.ajax.markup.html.autocomplete.AutoCompleteSettings" && target.name in setOf("setCssClassName", "setThrottleDelay", "setMinInputLength", "setPreselect", "setAdjustInputWidth", "setMaxHeightInPx") ||
                        owner == "org.apache.wicket.util.string.Strings" && target.name == "isEmpty" ||
                        owner == "org.apache.wicket.markup.html.list.LoopItem" && target.name == "getIndex") ||
                    owner == COMPONENT && target.name in setOf("getClass", "getResponse", "getRequest", "setResponsePage", "success", "info", "warn", "error", "debug", "fatal", "getMetaData", "setMetaData") ||
                    owner == "org.apache.wicket.Page" && target.name == "getPageParameters" ||
                    owner in setOf("org.apache.wicket.markup.repeater.data.IDataProvider", "org.apache.wicket.markup.repeater.data.ListDataProvider") && target.name in setOf("size", "iterator", "model", "detach") ||
                    owner == "org.apache.wicket.ajax.AjaxEventBehavior" && target.name == "onEvent" ||
                    owner == "org.apache.wicket.request.http.WebResponse" && target.name == "clearCookie" ||
                    owner == WicketRepeaterContract.REPEATING && target.name == "newChildId" ||
                    owner?.startsWith("org.apache.wicket.markup.head.") == true ||
                    owner == "org.apache.wicket.util.string.Strings" && target.name == "escapeMarkup" ||
                    owner == "jakarta.servlet.http.Cookie" && target.name == "setPath" ||
                    owner in setOf("org.apache.wicket.markup.ComponentTag", "org.apache.wicket.markup.parser.XmlTag") ||
                    owner == "org.apache.wicket.IGenericComponent" && target.name in setOf("getModel", "getModelObject", "setModel", "setModelObject") ||
                    owner == COMPONENT && target.name in setOf("getDefaultModelObjectAsString", "getApplication", "isEnabled", "isVisible", "isEnabledInHierarchy", "isVisibleInHierarchy") ||
                    owner == "org.apache.wicket.Application" && target.name == "getSecuritySettings" ||
                    owner == "org.apache.wicket.settings.SecuritySettings" && target.name == "getAuthorizationStrategy" ||
                    owner == "org.apache.wicket.authorization.IAuthorizationStrategy" && target.name == "isInstantiationAuthorized") {
                    if (at is PsiExpression && component(at.type)) {
                        if (owner in FRAMEWORK_CONFIG && target.name in FRAMEWORK_CONFIG.getValue(owner!!)) return receiver
                        return EffectRef(classType(at.type)!!, borrowed = true)
                    }
                    return null
                }
                stop(at, "Deferred compiled effects are not reviewed ($owner.${target.name}).")
            }

            private fun method(method: PsiMethod, ctx: EffectFrame): EffectRef? {
                tick(method); source(method)
                if (formOwner != null && ctx.receiver.known == formOwner) formCaches[method]?.let { cachedInputs[it]?.let { cached -> return cached } }
                if (++calls > 128 || active.size >= 12 || !active.add(method)) stop(method, "Deferred source effects are recursive or exceed their 128-call/12-level bound.")
                try {
                    if (method.body == null && method.isConstructor && method.containingClass?.isRecord == true &&
                        method.containingClass!!.recordComponents.none { graphType(it.type) }) return null
                    val body = method.body ?: stop(method, "Deferred source effects are unavailable (${method.containingClass?.qualifiedName}.${method.name}).")
                    val bodyContext = ctx.copy(construction = method.isConstructor)
                    if (formOwner != null && method.isConstructor) {
                        val call = (body.statements.firstOrNull { it !is PsiEmptyStatement } as? PsiExpressionStatement)?.expression as? PsiMethodCallExpression
                        if (call?.methodExpression?.referenceName !in setOf("this", "super")) implicitSuper(method.containingClass!!, bodyContext, method)
                    }
                    val results = mutableListOf<EffectRef?>()
                    walk(body, bodyContext, results)
                    val first = results.firstOrNull()
                    if (results.any { it != null } && results.any { it != first }) stop(method, "Source component return alternatives are unresolved.")
                    auditedMethods += method
                    return first
                } finally { active.remove(method) }
            }

            private fun walk(element: PsiElement, ctx: EffectFrame, returns: MutableList<EffectRef?>? = null) {
                element.accept(object : JavaRecursiveElementWalkingVisitor() {
                    override fun visitElement(element: PsiElement) {
                        if(element is PsiWhiteSpace || element is PsiJavaToken || element is PsiIdentifier || element is PsiComment) return
                        tick(element);super.visitElement(element)
                    }
                    override fun visitClass(aClass: PsiClass) { }
                    override fun visitReturnStatement(statement: PsiReturnStatement) {
                        if (formOwner != null && statement.returnValue?.let { inputFactory(it, ctx) } != null) stop(statement, "An owned input factory escapes through a return value.")
                        returns?.add(statement.returnValue?.let { value(it, ctx) }) ?: statement.returnValue?.let { value(it, ctx) }
                    }
                    override fun visitMethodCallExpression(expression: PsiMethodCallExpression) {
                        if(value(expression,ctx)?.columns!=null && (expression.parent !is PsiExpressionStatement || expression.parent.parent !is PsiCodeBlock))
                            stop(expression,"An opaque columns list query occurs in an unreviewed expression.")
                    }
                    override fun visitNewExpression(expression: PsiNewExpression) { value(expression, ctx) }
                    override fun visitLambdaExpression(expression: PsiLambdaExpression) { checkDeferred(expression, ctx) }
                    override fun visitMethodReferenceExpression(expression: PsiMethodReferenceExpression) { checkDeferred(expression, ctx) }
                    override fun visitAssignmentExpression(expression: PsiAssignmentExpression) { value(expression, ctx) }
                    override fun visitLocalVariable(variable: PsiLocalVariable) { ctx.locals[variable] = variable.initializer?.let { value(it, ctx) } }
                    override fun visitReferenceExpression(expression:PsiReferenceExpression) {
                        val variable=expression.resolve() as? PsiVariable
                        if(variable!=null && ctx.locals[variable]?.columns!=null) stop(expression,"An opaque columns list occurs in an unreviewed expression.")
                        super.visitReferenceExpression(expression)
                    }
                })
            }
        }

        private fun freshLocal(expression: PsiExpression?, frame: Frame, seen: Set<PsiElement>): Boolean {
            if (expression == null || seen.size >= 12 || expression in seen) return false
            tick(expression)
            return when (val value = unwrap(expression)) {
                is PsiNewExpression -> true
                is PsiReferenceExpression -> (value.resolve() as? PsiLocalVariable)?.let { freshLocal(it.initializer, frame, seen + value) } == true
                is PsiMethodCallExpression -> {
                    val declared = value.resolveMethod() ?: return false
                    if (declared.containingClass?.qualifiedName in FRAMEWORK_CONFIG &&
                        declared.name in FRAMEWORK_CONFIG.getValue(declared.containingClass!!.qualifiedName!!))
                        return freshLocal(value.methodExpression.qualifierExpression, frame, seen + value)
                    val method = if (declared.containingClass in hierarchy(frame.owner))
                        hierarchy(instances.getValue(frame.receiver.id).type).firstNotNullOfOrNull { it.findMethodBySignature(declared, false) } ?: declared else declared
                    source(method)
                    val returns = method.body?.let { PsiTreeUtil.findChildrenOfType(it, PsiReturnStatement::class.java) }.orEmpty()
                    returns.size == 1 && freshLocal(returns.single().returnValue, frame, seen + value)
                }
                else -> false
            }
        }

        private val graphReaders = mutableSetOf<PsiMethod>()
        private fun graphReadOnly(method: PsiMethod, seen: Set<PsiMethod>): Boolean {
            tick(method)
            if (method in graphReaders) return true
            if (method is PsiCompiledElement || method in seen || seen.size >= 12 || graphType(method.returnType) || method.body == null ||
                !(method.hasModifierProperty(PsiModifier.STATIC) || method.hasModifierProperty(PsiModifier.PRIVATE) || method.hasModifierProperty(PsiModifier.FINAL) ||
                    method.containingClass?.hasModifierProperty(PsiModifier.FINAL) == true)) return false
            source(method)
            method.containingClass?.let { initializeType(it, Frame(Ref(rootId), it, method, mutableMapOf())) }
            var safe = true
            method.body!!.accept(object : JavaRecursiveElementWalkingVisitor() {
                override fun visitElement(element: PsiElement) { tick(element); if (safe) super.visitElement(element) }
                override fun visitClass(aClass: PsiClass) { safe = false }
                override fun visitLambdaExpression(expression: PsiLambdaExpression) { safe = false }
                override fun visitMethodReferenceExpression(expression: PsiMethodReferenceExpression) { safe = false }
                override fun visitAssignmentExpression(expression: PsiAssignmentExpression) {
                    if ((expression.lExpression as? PsiReferenceExpression)?.resolve() !is PsiLocalVariable) safe = false
                    super.visitAssignmentExpression(expression)
                }
                override fun visitNewExpression(expression: PsiNewExpression) {
                    if (graphType(expression.type) || expression.argumentList?.expressions.orEmpty().any { graphType(it.type) }) safe = false
                    val type = classType(expression.type)
                    if (safe && type != null) {
                        initializeType(type, Frame(Ref(rootId), type, null, mutableMapOf()))
                        if (type is PsiCompiledElement && !dataLibrary(type.qualifiedName) && type.qualifiedName !in OPAQUE_CONSTRUCTORS) safe = false
                        else expression.resolveConstructor()?.takeUnless { it is PsiCompiledElement }?.let { auditLocal(it, mutableSetOf(), dataOnly = true) }
                    }
                    super.visitNewExpression(expression)
                }
                override fun visitMethodCallExpression(expression: PsiMethodCallExpression) {
                    val target = expression.resolveMethod()
                    if (target == null || if (target is PsiCompiledElement) {
                        !(dataMethod(target) || target.containingClass?.qualifiedName == "org.apache.wicket.Page" && target.name == "getPageParameters" ||
                            target.containingClass?.qualifiedName == COMPONENT && target.name in setOf("getId", "getDefaultModelObject", "getLocale")) ||
                            expression.argumentList.expressions.any { graphType(it.type) }
                    } else !graphReadOnly(target, seen + method)) safe = false
                    super.visitMethodCallExpression(expression)
                }
            })
            if (safe) graphReaders += method
            return safe
        }

        private val deferredMethods = mutableSetOf<Pair<PsiMethod, String>>()
        private fun auditDeferredMethod(method: PsiMethod, frame: Frame) {
            if (!deferredMethods.add(method to frame.receiver.id)) return
            if (method.body == null) stop(method, "Deferred source is unavailable.")
            auditDeferred(method, frame)
        }
    }

    private companion object {
        const val COMPONENT = "org.apache.wicket.Component"
        const val BEHAVIOR = "org.apache.wicket.behavior.Behavior"
        const val FRAGMENT = "org.apache.wicket.markup.html.panel.Fragment"
        val STRUCTURAL = setOf("add", "queue", "remove", "removeAll", "replace", "replaceWith", "addOrReplace", "setId")
        val FRAMEWORK_CONFIG = mapOf(
            COMPONENT to setOf("setOutputMarkupId", "setOutputMarkupPlaceholderTag", "setVisible", "setEnabled", "setDefaultModel", "setDefaultModelObject", "setEscapeModelStrings", "setMarkupId", "setRenderBodyOnly"),
            "org.apache.wicket.markup.html.form.FormComponent" to setOf("setRequired", "setType", "setLabel"),
            "org.apache.wicket.markup.html.link.AbstractLink" to setOf("setBody"),
        )
        val OPAQUE_CONSTRUCTORS = setOf("org.apache.wicket.markup.repeater.data.ListDataProvider", "org.apache.wicket.request.resource.CssResourceReference", "org.apache.wicket.request.resource.JavaScriptResourceReference",
            "org.apache.wicket.protocol.http.FetchMetadataResourceIsolationPolicy", "org.apache.wicket.MetaDataKey")
        fun formConstructor(method: PsiMethod) = method.isConstructor && method.containingClass?.qualifiedName in setOf(
            "org.apache.wicket.markup.html.form.NumberTextField", "org.apache.wicket.markup.html.form.TextArea",
            "org.apache.wicket.extensions.ajax.markup.html.autocomplete.AutoCompleteTextField",
            "org.apache.wicket.extensions.ajax.markup.html.autocomplete.AutoCompleteSettings",
            "org.apache.wicket.extensions.ajax.markup.html.autocomplete.AbstractAutoCompleteTextRenderer",
            "org.apache.wicket.ajax.form.AjaxFormComponentUpdatingBehavior",
            "org.apache.wicket.util.convert.converter.BigDecimalConverter")
        fun renderRequest(method: PsiMethod) = method.containingClass?.qualifiedName in setOf("org.apache.wicket.ajax.AjaxRequestTarget",
            "org.apache.wicket.core.request.handler.IPartialPageRequestHandler") && method.name in setOf("add", "appendJavaScript", "prependJavaScript")
        fun graphQuery(method: PsiMethod) = method.containingClass?.qualifiedName == "java.lang.Class" && method.name == "isInstance"
        fun dataMethod(method: PsiMethod) = (dataLibrary(method.containingClass?.qualifiedName) ||
            method.containingClass?.qualifiedName == "org.apache.wicket.Session" && method.name in setOf("get", "getMetaData", "setMetaData")) &&
            method.name !in setOf("forName", "newInstance", "invoke", "invokeExact", "getDeclaredMethod", "getDeclaredField", "getMethod", "getField", "loadClass")
        fun localFramework(method: PsiMethod): Boolean {
            val owner = method.containingClass?.qualifiedName
            return owner in FRAMEWORK_CONFIG && method.name in FRAMEWORK_CONFIG.getValue(owner!!) ||
                owner == COMPONENT && method.name in setOf("add", "getDefaultModel", "getDefaultModelObject", "getId", "getMarkupId", "getLocale", "getString", "onInitialize", "onConfigure", "renderHead") ||
                owner == "org.apache.wicket.MarkupContainer" && method.name in setOf("add", "addOrReplace", "replace", "removeAll", "onInitialize") ||
                owner in WicketComponentTypes.markupRoots && method.name == "onInitialize"
        }
        fun unwrap(expression: PsiExpression): PsiExpression = if (expression is PsiParenthesizedExpression) expression.expression?.let(::unwrap) ?: expression else expression
        fun componentFactory(type: PsiType?): Boolean {
            if (type == null) return false
            val sam = LambdaUtil.getFunctionalInterfaceMethod(type) ?: return false
            if (sam.parameterList.parametersCount !in 1..2) return false
            val result = LambdaUtil.getFunctionalInterfaceReturnType(type)
            return component(if (result is PsiWildcardType) result.extendsBound else result)
        }
        fun classType(type: PsiType?): PsiClass? = (type as? PsiClassType)?.resolve()
        fun component(type: PsiType?) = classType(type)?.let { it.qualifiedName == COMPONENT || InheritanceUtil.isInheritor(it, COMPONENT) } == true
        fun sourceDataSam(method: PsiMethod) = !method.isConstructor && !method.hasModifierProperty(PsiModifier.STATIC) &&
            method.findDeepestSuperMethods().any { parent -> parent.containingClass?.takeIf { it.isInterface }?.let { owner ->
                LambdaUtil.getFunctionalInterfaceMethod(JavaPsiFacade.getElementFactory(owner.project).createType(owner)) == parent
            } == true }
        fun graphType(type: PsiType?, depth: Int = 0): Boolean = depth < 6 && (component(type) || when (type) {
            is PsiArrayType -> graphType(type.componentType, depth + 1)
            // MetaDataKey<T> is a token, not storage for T. Its actual construction and uses
            // still pass the data/effect audit; component-valued metadata is not inferred.
            is PsiClassType -> type.resolve()?.qualifiedName !in setOf("java.lang.Class", "org.apache.wicket.MetaDataKey") && LambdaUtil.getFunctionalInterfaceMethod(type) == null &&
                type.parameters.any { graphType(it, depth + 1) }
            else -> false
        })
        fun isHook(method: PsiMethod) = method.name == "onInitialize" && method.parameterList.parametersCount == 0 &&
            !method.hasModifierProperty(PsiModifier.STATIC) && method.findDeepestSuperMethods().any { it.containingClass?.qualifiedName == COMPONENT }
        fun dataLibrary(name: String?) = name?.let { it.startsWith("java.lang.") && !it.startsWith("java.lang.reflect.") && !it.startsWith("java.lang.invoke.") &&
            it !in setOf("java.lang.Thread", "java.lang.Runtime", "java.lang.ProcessBuilder", "java.lang.ClassLoader") || it.startsWith("java.time.") || it.startsWith("java.util.") &&
            !it.startsWith("java.util.concurrent.") && it !in setOf("java.util.ServiceLoader", "java.util.Timer") ||
            it in setOf("java.util.concurrent.ConcurrentHashMap", "java.util.concurrent.atomic.AtomicLong", "java.io.InputStream", "java.io.IOException", "java.io.UncheckedIOException") ||
            it.startsWith("org.apache.wicket.model.") || it in setOf("org.apache.wicket.AttributeModifier", "org.apache.wicket.behavior.AttributeAppender", "org.apache.wicket.request.mapper.parameter.PageParameters") } == true
    }
}

private fun location(element: PsiElement) = SourceLocation(element.containingFile.virtualFile?.path ?: element.containingFile.name, element.textOffset)
