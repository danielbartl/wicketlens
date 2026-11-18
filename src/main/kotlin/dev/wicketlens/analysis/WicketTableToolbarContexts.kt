package dev.wicketlens.analysis

import com.intellij.openapi.progress.ProgressManager
import com.intellij.psi.*
import com.intellij.psi.util.InheritanceUtil
import com.intellij.psi.util.PsiTreeUtil
import dev.wicketlens.analysis.WicketSourceDeclarations.Id
import dev.wicketlens.model.*

/** Source-owned lazy DataTable toolbar content. Runtime toolbar IDs and visibility are never inferred. */
internal class WicketTableToolbarContexts(private val limits: Limits = Limits()) {
    data class Limits(val steps: Int = 20000, val sources: Int = 96, val contexts: Int = 4, val depth: Int = 12, val nodes: Int = 128)
    data class Result(val reports: List<PageReport>, val sources: List<String>, val diagnostics: List<Diagnostic>, val complete: Boolean)
    private class Unknown(val at: PsiElement, override val message: String) : RuntimeException(null, null, false, false)
    private class Bound(val at: PsiElement) : RuntimeException(null, null, false, false)

    companion object {
        const val TABLE = "org.apache.wicket.extensions.markup.html.repeater.data.table.DataTable"
        const val TOOLBAR = "org.apache.wicket.extensions.markup.html.repeater.data.table.AbstractToolbar"
        private const val CONTAINER = "org.apache.wicket.markup.html.WebMarkupContainer"
        /** Cheap discovery hint only. Full source, placement and ownership checks follow. */
        fun candidate(method: PsiMethod): Boolean = WicketImmediateFactoryContract.candidate(method) &&
            method.body!!.statements.filterIsInstance<PsiIfStatement>().any { guard ->
                val assignment = ((guard.thenBranch as? PsiBlockStatement)?.codeBlock?.statements?.firstOrNull() as? PsiExpressionStatement)?.expression as? PsiAssignmentExpression
                val type = ((assignment?.rExpression as? PsiNewExpression)?.classReference?.resolve() as? PsiClass)
                type?.let { InheritanceUtil.isInheritor(it, TOOLBAR) } == true
            }
    }

    fun analyze(page: PsiClass, primary: PageReport): Result = Session(page, primary).run()

    private inner class Session(val page: PsiClass, val primary: PageReport) {
        val sources = linkedSetOf<String>()
        val reports = mutableListOf<PageReport>()
        val diagnostics = mutableListOf<Diagnostic>()
        val cache = mutableMapOf<PsiElement, List<PsiElement>>()
        val visited = mutableSetOf<PsiElement>()
        var steps = 0
        val declarations = WicketSourceDeclarations(page, limits.depth, ::tick, ::source, sources, ::fail, { throw Bound(it) }, ::descendants)
        fun tick(at: PsiElement) { ProgressManager.checkCanceled(); if (++steps > limits.steps || sources.size > limits.sources) throw Bound(at) }
        fun fail(at: PsiElement, message: String): Nothing = throw Unknown(at, message)
        fun source(at: PsiElement) {
            tick(at)
            val file = at.containingFile ?: fail(at, "Source is unavailable.")
            sources += file.virtualFile?.path?.substringBefore("!/") ?: fail(at, "Physical source is unavailable.")
            if (sources.size > limits.sources) throw Bound(at)
            if (at !is PsiCompiledElement && PsiTreeUtil.findChildOfType(file, PsiErrorElement::class.java) != null) fail(at, "Java syntax is incomplete.")
        }
        fun descendants(at: PsiElement): List<PsiElement> {
            val all = cache.getOrPut(at) {
                val ancestor = generateSequence(at.parent) { it.parent }.firstOrNull { it in cache }
                if (ancestor != null) return@getOrPut cache.getValue(ancestor).filter { PsiTreeUtil.isAncestor(at, it, false) }
                val result = linkedSetOf<PsiElement>()
                at.accept(object : JavaRecursiveElementWalkingVisitor() {
                    override fun visitElement(element: PsiElement) { if (visited.add(element)) tick(element); result += element; super.visitElement(element) }
                })
                result.toList()
            }
            tick(at); return all
        }
        fun direct(at: PsiElement, method: PsiMethod) = PsiTreeUtil.getParentOfType(at, PsiStatement::class.java)?.parent == method.body &&
            PsiTreeUtil.getParentOfType(at, PsiMethod::class.java) == method && generateSequence(at.parent) { it.parent }.takeWhile { it != method }.none { it is PsiLambdaExpression }
        fun self(e: PsiExpression?, owner: PsiClass) = e == null || e is PsiThisExpression && (e.qualifier == null || e.qualifier?.resolve() == owner)
        fun reference(e: PsiExpression?, variable: PsiVariable, owner: PsiClass) = e is PsiReferenceExpression && e.resolve() == variable && self(e.qualifierExpression, owner)
        fun field(e: PsiExpression?, owner: PsiClass): PsiField? = (e as? PsiReferenceExpression)?.takeIf { self(it.qualifierExpression, owner) }?.resolve() as? PsiField
        fun call(statement: PsiStatement?) = (statement as? PsiExpressionStatement)?.expression as? PsiMethodCallExpression
        fun component(method: PsiMethod?) = method?.containingClass?.let { declarations.component(JavaPsiFacade.getElementFactory(page.project).createType(it)) } == true
        fun hierarchy(type: PsiClass): List<PsiClass> {
            val result = mutableListOf<PsiClass>(); var current: PsiClass? = type
            while (current != null && current !is PsiCompiledElement) {
                if (result.size >= limits.depth || current in result) throw Bound(current)
                source(current); result += current; current = current.superClass
            }
            return result
        }
        fun run(): Result {
            if (primary.markupSuffix.isNotEmpty()) return Result(emptyList(), emptyList(), emptyList(), true)
            var complete = true
            try {
                descendants(page).filterIsInstance<PsiLambdaExpression>().forEach { lambda ->
                    val call = (lambda.parent as? PsiExpressionList)?.parent as? PsiMethodCallExpression ?: return@forEach
                    val setter = call.resolveMethod()?.takeIf(WicketImmediateFactoryContract::candidate) ?: return@forEach
                    try {
                        // Eligibility reads source too: edits can turn a withheld registration into a template.
                        source(setter)
                        if (!candidate(setter)) return@forEach
                        if (reports.size >= limits.contexts) throw Bound(lambda)
                        reports += build(lambda, call, setter)
                    } catch (unknown: Unknown) { diagnostics += Diagnostic(DiagnosticCode.UNRESOLVED_SCOPE, "Lazy table toolbar template withheld: ${unknown.message}", location(unknown.at)) }
                }
            } catch (bound: Bound) {
                complete = false; reports.clear()
                diagnostics += Diagnostic(DiagnosticCode.PARTIAL_ANALYSIS, "Table toolbar discovery exceeded its ${limits.steps}-step/${limits.sources}-source/${limits.contexts}-context/${limits.depth}-level/${limits.nodes}-node bound; no toolbar template was published.", location(bound.at))
            }
            return Result(reports, sources.sorted(), diagnostics, complete)
        }
        fun constructors(constructor: PsiMethod, active: Set<PsiMethod> = emptySet()): List<PsiMethod> {
            if (constructor is PsiCompiledElement) return emptyList()
            source(constructor)
            if (constructor in active || active.size >= limits.depth) throw Bound(constructor)
            val delegation = call(constructor.body?.statements?.firstOrNull())
            val previous = if (delegation?.methodExpression?.referenceName in setOf("super", "this")) constructors(delegation!!.resolveMethod() ?: fail(delegation, "Constructor delegation is unresolved."), active + constructor) else emptyList()
            return previous + constructor
        }
        fun immutable(field: PsiField, owner: PsiClass) {
            source(field)
            if (field.containingClass != owner || !field.hasModifierProperty(PsiModifier.PRIVATE) || !field.hasModifierProperty(PsiModifier.FINAL) || field.hasModifierProperty(PsiModifier.STATIC))
                fail(field, "Owned placement fields must be private final instance fields.")
        }
        fun nullGuard(guard: PsiIfStatement, variable: PsiField, owner: PsiClass) {
            if(!WicketToolbarCacheContract.nullGuard(guard,variable,owner)) fail(guard,"Toolbar initialization must have a direct field == null guard without else.")
        }
        fun host(call: PsiMethodCallExpression, setter: PsiMethod): Pair<PsiNewExpression, String> {
            val constructor = PsiTreeUtil.getParentOfType(call, PsiMethod::class.java) ?: fail(call, "The registration has no source constructor.")
            if (!constructor.isConstructor || constructor.containingClass != page || !direct(call, constructor)) fail(call, "Registration requires direct page constructor setup.")
            val receiver = call.methodExpression.qualifierExpression
            val local = (receiver as? PsiReferenceExpression)?.resolve() as? PsiLocalVariable
            val creation = (if (local != null) local.initializer else receiver) as? PsiNewExpression ?: fail(call, "The host needs a direct fresh local or inline allocation.")
            if (local != null && local.parent.parent != constructor.body || creation.anonymousClass != null || creation.qualifier != null) fail(creation, "Conditional, anonymous or foreign host construction is unsupported.")
            val runtime = creation.classReference?.resolve() as? PsiClass ?: fail(creation, "The host type is unavailable.")
            source(runtime)
            if (runtime != setter.containingClass || runtime.findMethodBySignature(setter, true) != setter) fail(call, "Inherited or anonymous setter dispatch is outside this contract.")
            if (JavaPsiFacade.getInstance(page.project).findClasses(runtime.qualifiedName!!, page.resolveScope).singleOrNull() != runtime) fail(runtime, "The host declaration is ambiguous in this source context.")
            val identity = WicketComponentIdentity.trace(creation) ?: fail(creation, "Host ID forwarding is unproved."); sources += identity.sources
            val id = declarations.id(identity.expression, emptyMap()) ?: fail(creation, "The host ID is unknown.")
            val all = descendants(constructor.body!!)
            val sameIds = all.filterIsInstance<PsiNewExpression>().filter { direct(it, constructor) && declarations.component(it.type) }.count { allocation ->
                WicketComponentIdentity.trace(allocation)?.let { known -> sources += known.sources; declarations.id(known.expression, emptyMap())?.value == id.value } == true
            }
            if (sameIds != 1) fail(creation, "Host IDs are ambiguous among direct constructor allocations.")
            if (local != null && all.filterIsInstance<PsiAssignmentExpression>().any { (it.lExpression as? PsiReferenceExpression)?.resolve() == local }) fail(local, "The host is reassigned.")
            val calls = all.filterIsInstance<PsiMethodCallExpression>()
            val additions = calls.filter { added -> direct(added, constructor) && self(added.methodExpression.qualifierExpression, page) &&
                added.resolveMethod()?.let { it.name == "add" && it.containingClass?.qualifiedName == "org.apache.wicket.MarkupContainer" } == true &&
                added.argumentList.expressions.any { if (local == null) it == call else reference(it, local, page) } }
            if (additions.size != 1) fail(call, "A unique direct page attachment is required.")
            if (calls.count { it.resolveMethod() == setter && (it.methodExpression.qualifierExpression == creation || local != null && reference(it.methodExpression.qualifierExpression, local, page)) } != 1)
                fail(call, "Multiple registrations for one host are not reconciled.")
            calls.filter { direct(it, constructor) }.forEach { operation ->
                val receiverIsHost = operation.methodExpression.qualifierExpression == creation || local != null && reference(operation.methodExpression.qualifierExpression, local, page)
                if (operation.resolveMethod()?.name in MUTATIONS && component(operation.resolveMethod()) && (self(operation.methodExpression.qualifierExpression, page) || receiverIsHost)) fail(operation, "Known page/host mutations invalidate the declared placement.")
                if (local != null && operation != additions.single() && operation.argumentList.expressions.any { reference(it, local, page) }) fail(operation, "The host escapes its direct attachment and receiver calls.")
            }
            resource(page, id.value)
            return creation to id.value
        }
        fun resource(type: PsiClass, id: String) {
            if (hierarchy(type).any { cls -> cls.methods.any { it.name in WicketFragmentAnalyzer.PROVIDER_METHODS && it.findDeepestSuperMethods().isNotEmpty() } })
                fail(type, "Custom markup sourcing is outside the toolbar placement contract.")
            val markupPath = WicketPageResolver().resolve(type).markupPath ?: fail(type, "Conventional source markup is unavailable.")
            val file = WicketSourceFiles.find(page.project, markupPath)?.let { PsiManager.getInstance(page.project).findFile(it) } ?: fail(type, "Conventional source markup is unavailable.")
            val raw = WicketMarkupAnalyzer().analyzeInheritanceSource(file, WicketComponentTypes.kind(type)); sources += raw.sourceFiles
            val byId = raw.elements.associateBy { it.nodeId }
            val placements = raw.elements.filter { e -> e.hasWicketId && e.participatesInComponentTree && generateSequence(e) { byId[it.parentWicketElementId] }.mapNotNull { it.wicketId }.toList().asReversed() == listOf(id) }
            if (raw.documentDiagnostics.isNotEmpty() || placements.singleOrNull()?.let { it.confidence == Confidence.CONFIRMED && it.diagnostics.isEmpty() } != true) fail(file, "A unique resource-local host placement is not established.")
        }
        fun ownedTable(field: PsiField, owner: PsiClass, host: PsiNewExpression, attachment: PsiMethodCallExpression) {
            immutable(field, owner)
            if ((field.type as? PsiClassType)?.resolve()?.let { it.qualifiedName == TABLE || InheritanceUtil.isInheritor(it, TABLE) } != true) fail(field, "The toolbar must belong to a DataTable field.")
            val chain = constructors(host.resolveConstructor() ?: fail(host, "The host constructor is unavailable."))
            val writes = descendants(owner).filterIsInstance<PsiAssignmentExpression>().filter { field(it.lExpression, owner) == field }
            val creation = if (field.initializer != null && writes.isEmpty()) field.initializer as? PsiNewExpression else writes.singleOrNull()?.takeIf { write -> chain.any { direct(write, it) } }?.rExpression as? PsiNewExpression
                ?: fail(field, "The owned DataTable needs one direct selected-constructor allocation.")
            if (creation == null || creation.anonymousClass != null || creation.qualifier != null) fail(field, "The owned table allocation is anonymous, aliased or unavailable.")
            val runtime = creation.classReference?.resolve() as? PsiClass ?: fail(creation, "The table type is unavailable.")
            val target = attachment.resolveMethod() ?: fail(attachment, "The framework toolbar attachment does not resolve.")
            if (target.containingClass?.qualifiedName != TABLE || target.name != "addBottomToolbar" || runtime.findMethodBySignature(target, true) != target) fail(attachment, "The DataTable toolbar attachment has unreviewed runtime dispatch.")
            val identity = WicketComponentIdentity.trace(creation, setOf(TABLE)) ?: fail(creation, "The table ID forwarding is unproved."); sources += identity.sources
            val tableId = declarations.id(identity.expression, emptyMap()) ?: fail(creation, "The owned table ID is unknown.")
            resource(owner, tableId.value)
            val additions = chain.flatMap { ctor -> descendants(ctor).filterIsInstance<PsiMethodCallExpression>().filter { added -> direct(added, ctor) && self(added.methodExpression.qualifierExpression, owner) &&
                added.resolveMethod()?.let { it.name == "add" && it.containingClass?.qualifiedName == "org.apache.wicket.MarkupContainer" } == true && added.argumentList.expressions.any { reference(it, field, owner) } } }
            if (additions.size != 1) fail(field, "The owned table lacks a unique direct constructor attachment.")
            chain.forEach { ctor -> descendants(ctor).filterIsInstance<PsiMethodCallExpression>().filter { direct(it, ctor) }.forEach { operation ->
                if (operation.resolveMethod()?.name in MUTATIONS && component(operation.resolveMethod()) &&
                    (self(operation.methodExpression.qualifierExpression, owner) || reference(operation.methodExpression.qualifierExpression, field, owner))) fail(operation, "Known constructor mutations invalidate the owned DataTable.")
            } }
        }
        fun build(lambda: PsiLambdaExpression, call: PsiMethodCallExpression, setter: PsiMethod): PageReport {
            source(page); source(setter)
            if (call.argumentList.expressions.singleOrNull() != lambda) fail(call, "A direct unary source factory registration is required.")
            val (hostCreation, hostId) = host(call, setter)
            val owner = setter.containingClass!!
            val statements = setter.body!!.statements
            if (statements.size !in 4..5 || (statements.last() as? PsiReturnStatement)?.returnValue?.let { it is PsiThisExpression && it.qualifier == null } != true) fail(setter, "The lazy setter has additional effects or an unproved receiver return.")
            val produced = (statements[0] as PsiDeclarationStatement).declaredElements.single() as PsiLocalVariable
            val invocation = produced.initializer as PsiMethodCallExpression
            val parameter = setter.parameterList.parameters.single()
            if (invocation.resolveMethod() != LambdaUtil.getFunctionalInterfaceMethod(parameter.type) || invocation.argumentList.expressionCount != 1) fail(invocation, "The unary factory invocation is unproved.")
            val id = declarations.id(invocation.argumentList.expressions.single(), emptyMap()) ?: fail(invocation, "The supplied content ID is unknown.")
            if (descendants(setter.body!!).filterIsInstance<PsiReferenceExpression>().filter { it.resolve() == parameter } != listOf(invocation.methodExpression.qualifierExpression)) fail(parameter, "The factory parameter escapes its immediate invocation.")
            val identityGuard = if (statements.size == 5) statements[1] as? PsiIfStatement ?: fail(statements[1], "A throwing identity guard is required here.") else null
            identityGuard?.let { WicketImmediateFactoryContract(::source, ::descendants, { e -> declarations.id(e, emptyMap())?.value }, limits.depth, ::fail).inspectGuard(it, produced, invocation) }
            val initialization = statements[statements.lastIndex - 2] as? PsiIfStatement ?: fail(setter, "A null-guarded toolbar cache is required.")
            val init = (initialization.thenBranch as? PsiBlockStatement)?.codeBlock?.statements ?: fail(initialization, "Toolbar initialization needs a direct source block.")
            if (init.size != 2) fail(initialization, "Toolbar initialization must only allocate and attach.")
            val assignment = (init[0] as? PsiExpressionStatement)?.expression as? PsiAssignmentExpression ?: fail(init[0], "The toolbar cache needs one direct assignment.")
            val cacheField = field(assignment.lExpression, owner) ?: fail(assignment, "The toolbar cache uses a foreign receiver.")
            source(cacheField)
            if (cacheField.containingClass != owner || !cacheField.hasModifierProperty(PsiModifier.PRIVATE) || cacheField.hasModifierProperty(PsiModifier.STATIC) || cacheField.hasModifierProperty(PsiModifier.FINAL) || cacheField.initializer != null || assignment.operationTokenType != JavaTokenType.EQ)
                fail(cacheField, "The toolbar cache must be a private instance field with default-null initialization.")
            nullGuard(initialization, cacheField, owner)
            if (descendants(owner).filterIsInstance<PsiAssignmentExpression>().filter { field(it.lExpression, owner) == cacheField } != listOf(assignment)) fail(cacheField, "The toolbar cache is overwritten outside its initializer.")
            val toolbarCreation = assignment.rExpression as? PsiNewExpression ?: fail(assignment, "The toolbar cache must be freshly allocated.")
            val toolbarType = toolbarCreation.classReference?.resolve() as? PsiClass ?: fail(toolbarCreation, "The toolbar type is unavailable.")
            source(toolbarType)
            if (toolbarCreation.anonymousClass != null || toolbarCreation.qualifier != null || toolbarType.containingClass != owner || !toolbarType.hasModifierProperty(PsiModifier.FINAL) || toolbarType.superClass?.qualifiedName != TOOLBAR ||
                (cacheField.type as? PsiClassType)?.resolve() != toolbarType) fail(toolbarCreation, "A final source-owned direct AbstractToolbar subtype is required.")
            val attachment = call(init[1]) ?: fail(init[1], "The toolbar needs a direct DataTable attachment.")
            val tableField = field(attachment.methodExpression.qualifierExpression, owner) ?: fail(attachment, "The toolbar uses a foreign table receiver.")
            if (attachment.argumentList.expressions.singleOrNull()?.let { reference(it, cacheField, owner) } != true || toolbarCreation.argumentList?.expressions?.singleOrNull()?.let { reference(it, tableField, owner) } != true)
                fail(attachment, "Toolbar construction and attachment must use the same owned DataTable.")
            ownedTable(tableField, owner, hostCreation, attachment)
            val replacement = call(statements[statements.lastIndex - 1]) ?: fail(setter, "The content needs one direct addOrReplace.")
            val cellAccess = replacement.methodExpression.qualifierExpression as? PsiReferenceExpression ?: fail(replacement, "The replacement cell is unavailable.")
            val cell = cellAccess.resolve() as? PsiField ?: fail(cellAccess, "The replacement receiver must be a toolbar field.")
            immutable(cell, toolbarType)
            if (!reference(cellAccess.qualifierExpression, cacheField, owner) || replacement.resolveMethod()?.let { it.name == "addOrReplace" && it.containingClass?.qualifiedName == "org.apache.wicket.MarkupContainer" } != true ||
                (replacement.argumentList.expressions.singleOrNull() as? PsiReferenceExpression)?.resolve() != produced) fail(replacement, "The factory result must be added to the cached toolbar's own cell.")
            descendants(setter.body!!).filterIsInstance<PsiReferenceExpression>().filter { it.resolve() == produced }.forEach { use ->
                if (use != replacement.argumentList.expressions.single() && (identityGuard == null || !PsiTreeUtil.isAncestor(identityGuard, use, true))) fail(use, "The produced content escapes its identity guard and placement.")
            }
            val formal = lambda.parameterList.parameters.singleOrNull() ?: fail(lambda, "A unary source lambda is required.")
            val contentCreation = lambda.body as? PsiNewExpression ?: fail(lambda, "The content factory must be a direct fresh constructor expression.")
            val contentType = contentCreation.classReference?.resolve() as? PsiClass ?: fail(contentCreation, "The content type is unavailable.")
            if (formal.type.canonicalText != "java.lang.String" || contentCreation.anonymousClass != null || contentCreation.qualifier != null || !WicketComponentTypes.ownsMarkup(contentType)) fail(contentCreation, "Conventional source-owned content with a supplied String ID is required.")
            source(contentType)
            val identity = WicketComponentIdentity.trace(contentCreation) ?: fail(contentCreation, "Content constructor ID forwarding is unproved."); sources += identity.sources
            if (declarations.id(identity.expression, mapOf(formal to Id(id.value, null)))?.let { it.value == id.value && it.origin == null } != true ||
                descendants(lambda).filterIsInstance<PsiReferenceExpression>().filter { it.resolve() == formal } != listOf(identity.expression)) fail(contentCreation, "Content must forward its supplied ID exactly once, rather than an equal literal.")
            return project(lambda, call, hostCreation, hostId, invocation, id, cacheField, initialization, toolbarCreation, attachment, replacement, toolbarType, cell, contentCreation, contentType)
        }
        fun project(lambda: PsiLambdaExpression, registration: PsiMethodCallExpression, host: PsiNewExpression, hostId: String, invocation: PsiMethodCallExpression, id: Id,
            cacheField: PsiField, initialization: PsiIfStatement, toolbarCreation: PsiNewExpression, attachment: PsiMethodCallExpression, replacement: PsiMethodCallExpression,
            toolbarType: PsiClass, cell: PsiField, contentCreation: PsiNewExpression, contentType: PsiClass): PageReport {
            val key = "table-toolbar:${page.qualifiedName}:${lambda.textOffset}"; val root = "$key:root"; val content = "$key:content"
            val toolbarCtor = toolbarType.constructors.singleOrNull()?.takeIf { it == toolbarCreation.resolveConstructor() } ?: fail(toolbarType, "The toolbar needs one selected structural constructor.")
            val superCall = call(toolbarCtor.body?.statements?.firstOrNull())
            if (toolbarCtor.parameterList.parametersCount != 1 || superCall?.resolveMethod()?.containingClass?.qualifiedName != TOOLBAR ||
                (superCall.argumentList.expressions.singleOrNull() as? PsiReferenceExpression)?.resolve() != toolbarCtor.parameterList.parameters.single()) fail(toolbarCtor, "The toolbar must forward its supplied table to AbstractToolbar.")
            if (toolbarType.initializers.isNotEmpty() || toolbarType.fields.any { it.initializer != null }) fail(toolbarType, "Toolbar initializer effects are unsupported.")
            val nodes = mutableListOf(ComponentNode(root, null, toolbarType.qualifiedName!!, null, location(toolbarCreation), confidence=Confidence.CONFIRMED, template=true, idRenameSafe=false))
            nodes += declarations.constructorNodes(toolbarCtor, root, "$key:toolbar", limits.nodes - nodes.size, toolbarType, fields=true)
            val cellAllocation = descendants(toolbarCtor).filterIsInstance<PsiAssignmentExpression>().singleOrNull { field(it.lExpression, toolbarType) == cell }?.rExpression as? PsiNewExpression
                ?: fail(cell, "The replacement cell must have a direct constructor allocation.")
            if (cellAllocation.anonymousClass != null || (cellAllocation.classReference?.resolve() as? PsiClass)?.qualifiedName != CONTAINER) fail(cellAllocation, "The replacement cell must be a plain framework WebMarkupContainer.")
            val cellNode = nodes.singleOrNull { it.javaLocation == location(cellAllocation) } ?: fail(cellAllocation, "The cell declaration is not reconstructed.")
            nodes += ComponentNode(content, id.value, contentType.qualifiedName!!, cellNode.nodeId, location(contentCreation), confidence=Confidence.CONFIRMED,
                idLocation=id.origin?.let(::location), computedId=true, template=true, idRenameSafe=false, sourceSetupExpanded=true)
            val contentCtor = contentCreation.resolveConstructor() ?: fail(contentCreation, "The content constructor is unavailable.")
            val contentOwners = hierarchy(contentType)
            if (contentOwners.any { it.initializers.isNotEmpty() || it.fields.any { field -> declarations.component(field.type) } }) fail(contentType, "Content component fields and initializers require a separate contract.")
            constructors(contentCtor).forEach { ctor -> nodes += declarations.constructorNodes(ctor, content, "$key:content:${ctor.textOffset}", limits.nodes - nodes.size, contentType) }
            if (nodes.size > limits.nodes) throw Bound(contentCreation)
            if (nodes.filter { it.nodeId != root }.groupingBy { it.parentNodeId to it.wicketId }.eachCount().any { it.value > 1 }) fail(contentCtor, "Constructor declarations have duplicate child paths.")
            (contentOwners + toolbarType).forEach { type -> if (type.methods.any { it.name in WicketFragmentAnalyzer.PROVIDER_METHODS && it.findDeepestSuperMethods().isNotEmpty() }) fail(type, "Custom markup sourcing is outside the toolbar template contract.") }
            val extractor = WicketMarkupInheritanceAnalyzer()
            val outer = extractor.analyze(toolbarType, WicketComponentTypes.kind(toolbarType)).markup ?: fail(toolbarType, "Toolbar companion markup is unavailable.")
            val inner = extractor.analyze(contentType, WicketComponentTypes.kind(contentType)).markup ?: fail(contentType, "Content companion markup is unavailable.")
            if ((outer.documentDiagnostics + inner.documentDiagnostics).isNotEmpty()) fail(toolbarType, "Toolbar/content markup has document uncertainty.")
            sources += (outer.sourceFiles + inner.sourceFiles).map { it.substringBefore("!/") }; tick(toolbarType)
            val byId = outer.elements.associateBy { it.nodeId }
            val slot = outer.elements.singleOrNull { element -> element.hasWicketId && generateSequence(element) { byId[it.parentWicketElementId] }.mapNotNull { it.wicketId }.toList().asReversed() == listOf(cellNode.wicketId, id.value) }
                ?: fail(toolbarType, "A unique toolbar-relative content slot is unavailable.")
            val ids = inner.elements.associate { it.nodeId to "$key:markup:${it.nodeId}" }
            val elements = outer.elements + inner.elements.map { element -> element.copy(nodeId=ids.getValue(element.nodeId), parentElementId=ids[element.parentElementId] ?: slot.nodeId,
                parentWicketElementId=ids[element.parentWicketElementId] ?: slot.nodeId, markupProviderNodeId=content) }
            val methods = (listOf(toolbarType) + contentOwners).flatMap { it.methods.toList() }.filter { !it.isConstructor && it.name in setOf("onInitialize", "onConfigure", "onBeforeRender", "onAfterRender", "onDetach", "onRemove") && it.findDeepestSuperMethods().isNotEmpty() }
            val setup = (listOf(toolbarCtor) + constructors(contentCtor)).flatMap(::descendants).distinct()
            val configurations = setup.filterIsInstance<PsiMethodCallExpression>().filter { methodCall -> (PsiTreeUtil.getParentOfType(methodCall, PsiMethod::class.java)?.let { direct(methodCall, it) } == true) && methodCall.methodExpression.referenceName !in setOf("super", "this") }.map(::location)
            val callbacks = (methods.map(::location) + setup.filter { it is PsiMethodReferenceExpression || it is PsiLambdaExpression }.map(::location)).distinct()
            val warning = Diagnostic(DiagnosticCode.UNRESOLVED_SCOPE, "Source-declared toolbar-relative content template. Wicket assigns the enclosing toolbar ID; actual toolbar instances, row count, visibility, colspan, models, constructor/configuration effects, callbacks and later lifecycle state remain unknown. No source factory or callback was executed.", location(registration))
            return WicketGraphMatcher().match(PageReport(page.qualifiedName!!, root, nodes.map { if (it.nodeId == content) it.copy(ownedMarkupPath=inner.file) else it }, outer.file,
                warnings=listOf(warning), scopeComplete=false, markup=outer.copy(elements=elements, scopeComplete=false, sourceFiles=(outer.sourceFiles + inner.sourceFiles).distinct()), javaSourceFiles=sources.sorted(),
                toolbarTemplate=TableToolbarTemplate(key, (host.classReference!!.resolve() as PsiClass).qualifiedName!!, hostId, toolbarType.qualifiedName!!, contentType.qualifiedName!!, content,
                    location(registration), location(host), location(lambda), location(invocation), location(id.origin ?: invocation.argumentList.expressions.single()), location(cacheField), location(initialization),
                    location(toolbarCreation), location(attachment), location(replacement), configurations, callbacks)))
        }
    }
}

private fun location(at: PsiElement) = SourceLocation(at.containingFile.virtualFile.path, at.textOffset)
private val MUTATIONS = setOf("remove", "removeAll", "replace", "replaceWith", "addOrReplace", "queue")
