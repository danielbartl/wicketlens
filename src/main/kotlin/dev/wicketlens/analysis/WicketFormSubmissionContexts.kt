package dev.wicketlens.analysis

import com.intellij.openapi.progress.ProgressManager
import com.intellij.psi.*
import com.intellij.psi.util.InheritanceUtil
import com.intellij.psi.util.PsiTreeUtil
import dev.wicketlens.model.*

/** Explicit source additions and button callback declarations; never an initial/runtime hierarchy upgrade. */
internal class WicketFormSubmissionContexts(private val limits: Limits = Limits()) {
    data class Limits(val steps: Int = 40000, val sources: Int = 96, val contexts: Int = 8, val nodes: Int = 128, val depth: Int = 12, val effects: Int = 128)
    data class Result(val reports: List<PageReport>, val sources: List<String>, val diagnostics: List<Diagnostic>, val complete: Boolean)
    private class Unknown(val at: PsiElement, override val message: String) : RuntimeException(null, null, false, false)
    private class Bound(val at: PsiElement) : RuntimeException(null, null, false, false)
    private data class Attachment(val parent: PsiNewExpression?, val call: PsiMethodCallExpression)
    fun analyze(page: PsiClass, primary: PageReport): Result = Session(page, primary).run()

    private inner class Session(val page: PsiClass, val primary: PageReport) {
        val sources = linkedSetOf<String>()
        val reports = mutableListOf<PageReport>()
        val diagnostics = mutableListOf<Diagnostic>()
        var steps = 0
        val cache = mutableMapOf<PsiElement, List<PsiElement>>()
        val locals = linkedMapOf<PsiLocalVariable, PsiNewExpression>()
        val attachments = linkedMapOf<PsiNewExpression, MutableList<Attachment>>()
        val nodes = linkedMapOf<PsiNewExpression, ComponentNode>()
        val declarations = WicketSourceDeclarations(page, limits.depth, ::tick, ::source, sources, ::fail, { throw Bound(it) }, ::descendants)
        fun tick(at: PsiElement) { ProgressManager.checkCanceled(); if (++steps > limits.steps || sources.size > limits.sources) throw Bound(at) }
        fun fail(at: PsiElement, message: String): Nothing = throw Unknown(at, message)
        fun source(at: PsiElement) {
            tick(at); sources += at.containingFile?.virtualFile?.path?.substringBefore("!/") ?: fail(at, "Physical source is unavailable.")
            if (sources.size > limits.sources) throw Bound(at)
            if (at !is PsiCompiledElement && PsiTreeUtil.findChildOfType(at.containingFile, PsiErrorElement::class.java) != null) fail(at, "Source syntax is incomplete.")
        }
        fun descendants(at: PsiElement): List<PsiElement> {
            tick(at)
            return cache.getOrPut(at) { val result = mutableListOf<PsiElement>()
                at.accept(object : JavaRecursiveElementWalkingVisitor() {
                    override fun visitElement(element: PsiElement) { tick(element); result += element; super.visitElement(element) }
                }); result }
        }
        fun own(e: PsiExpression?) = e == null || e is PsiThisExpression && (e.qualifier == null || e.qualifier?.resolve() == page)
        fun creation(e: PsiExpression?): PsiNewExpression? = when (e) {
            is PsiNewExpression -> e
            is PsiReferenceExpression -> if (e.qualifierExpression == null) locals[e.resolve()] else null
            else -> null
        }
        fun type(e: PsiNewExpression) = e.anonymousClass?.baseClassType?.resolve() ?: e.classReference?.resolve() as? PsiClass ?: fail(e, "The declared component type is unavailable.")
        fun isForm(e: PsiNewExpression) = type(e).let { it.qualifiedName == FORM || InheritanceUtil.isInheritor(it, FORM) }
        fun run(): Result {
            if (primary.markupPath == null || primary.markup == null || primary.markupSuffix.isNotEmpty()) return Result(emptyList(), emptyList(), emptyList(), true)
            try {
                source(page)
                val constructor = page.constructors.singleOrNull() ?: return Result(emptyList(), sources.toList(), emptyList(), true)
                val body = constructor.body ?: return Result(emptyList(), sources.toList(), emptyList(), true)
                body.statements.filterIsInstance<PsiDeclarationStatement>().flatMap { it.declaredElements.toList() }.filterIsInstance<PsiLocalVariable>().forEach { local ->
                    (local.initializer as? PsiNewExpression)?.takeIf { declarations.component(it.type) }?.let { locals[local] = it }
                }
                body.statements.filterIsInstance<PsiExpressionStatement>().mapNotNull { it.expression as? PsiMethodCallExpression }.forEach { call ->
                    tick(call)
                    val method = call.resolveMethod()
                    if (method !is PsiCompiledElement || method.containingClass?.qualifiedName != "org.apache.wicket.MarkupContainer" || method.name != "add" || !call.resolveMethodGenerics().isValidResult) return@forEach
                    val qualifier = call.methodExpression.qualifierExpression
                    val parent = if (own(qualifier)) null else creation(qualifier) ?: return@forEach
                    call.argumentList.expressions.forEach { argument -> tick(argument); creation(argument)?.let { child -> attachments.getOrPut(child) { mutableListOf() } += Attachment(parent, call) } }
                }
                for (button in attachments.keys) {
                    tick(button)
                    val anonymous = button.anonymousClass ?: continue
                    if (!InheritanceUtil.isInheritor(anonymous, BUTTON)) continue
                    for (callback in anonymous.methods.filter { it.name in setOf("onSubmit", "onError") }) {
                        if (reports.size >= limits.contexts) throw Bound(callback)
                        try { project(button, callback)?.let(reports::add) }
                        catch (unknown: Unknown) { diagnostics += Diagnostic(DiagnosticCode.UNRESOLVED_SCOPE, "Form submission template withheld: ${unknown.message}", location(unknown.at)) }
                    }
                }
            } catch (unknown: Unknown) {
                return Result(emptyList(), sources.sorted(), listOf(Diagnostic(DiagnosticCode.UNRESOLVED_SCOPE,
                    "Form submission discovery withheld: ${unknown.message}", location(unknown.at))), true)
            } catch (bound: Bound) {
                return Result(emptyList(), sources.sorted(), listOf(Diagnostic(DiagnosticCode.PARTIAL_ANALYSIS,
                    "Form submission discovery exceeded its bounded source contract; no ordinary form submission graphs published.", location(bound.at))), false)
            }
            return Result(reports.toList(), sources.sorted(), diagnostics.toList(), true)
        }
        fun attachment(e: PsiNewExpression) = attachments[e]?.singleOrNull() ?: fail(e, "A component requires one direct source addition.")
        fun identity(e: PsiNewExpression): WicketSourceDeclarations.Id {
            val identity = WicketComponentIdentity.trace(e) ?: fail(e, "Constructor ID provenance is unavailable.")
            sources += identity.sources; tick(e)
            return declarations.id(identity.expression, emptyMap()) ?: fail(e, "A nonempty literal source ID is required.")
        }
        fun node(e: PsiNewExpression, active: Set<PsiNewExpression> = emptySet()): ComponentNode {
            nodes[e]?.let { return it }
            if (e in active || active.size >= limits.depth) throw Bound(e)
            if (nodes.size >= limits.nodes) throw Bound(e)
            val at = attachment(e); val id = identity(e)
            val parent = at.parent?.let { node(it, active + e).nodeId } ?: primary.rootNodeId
            for ((other, additions) in attachments) {
                tick(other)
                if (other != e && additions.any { it.parent == at.parent }) {
                    val otherId = try { identity(other).value } catch (_: Unknown) { null }
                    if (otherId == id.value) fail(other, "Duplicate declared sibling IDs make the source path ambiguous.")
                }
            }
            return ComponentNode("form-submission:${e.textOffset}", id.value, type(e).qualifiedName!!, parent, location(e),
                confidence = Confidence.CONFIRMED, template = true, idLocation = id.origin?.let(::location), idRenameSafe = false).also { nodes[e] = it }
        }
        fun ancestry(e: PsiNewExpression): List<PsiNewExpression> {
            val result = mutableListOf<PsiNewExpression>(); var next: PsiNewExpression? = e
            while (next != null) { tick(next); if (next in result || result.size >= limits.depth) throw Bound(next); result += next; next = attachment(next).parent }
            return result.asReversed()
        }
        fun path(e: PsiNewExpression) = ancestry(e).joinToString(":") { node(it).wicketId!! }
        fun validateUses(selected: Set<PsiNewExpression>, button: PsiNewExpression) {
            val variables = locals.filterValues { it in selected }.keys
            descendants(page).filterIsInstance<PsiAssignmentExpression>().firstOrNull { (it.lExpression as? PsiReferenceExpression)?.resolve() in variables }?.let {
                fail(it, "A selected component local is reassigned.")
            }
            descendants(page).filterIsInstance<PsiReferenceExpression>().filter { it.resolve() in variables }.forEach { use ->
                tick(use)
                if (PsiTreeUtil.getParentOfType(use, PsiMethod::class.java)?.isConstructor != true ||
                    generateSequence(use.parent) { it.parent }.takeWhile { it != page }.any { it is PsiLambdaExpression }) return@forEach
                val member = use.parent as? PsiReferenceExpression
                val call = member?.parent as? PsiMethodCallExpression
                val resolved = call?.resolveMethod()
                if (member?.qualifierExpression == use && resolved is PsiCompiledElement &&
                    (resolved.containingClass?.qualifiedName == "org.apache.wicket.MarkupContainer" && resolved.name == "add" ||
                        resolved.containingClass?.qualifiedName == "org.apache.wicket.Component" && resolved.name in setOf("setOutputMarkupId", "setOutputMarkupPlaceholderTag", "setVisible", "setEnabled", "setRenderBodyOnly")) &&
                    (resolved.name == "add" || call.argumentList.expressions.all { it is PsiLiteralExpression })) return@forEach
                val argumentList = use.parent as? PsiExpressionList
                val addition = argumentList?.parent as? PsiMethodCallExpression
                if (addition != null && attachments.values.flatten().any { it.call == addition }) return@forEach
                if (argumentList == button.argumentList) return@forEach
                fail(use, "A selected constructor local is aliased, reassigned, structurally mutated or passed to unreviewed setup.")
            }
        }
        fun association(button: PsiNewExpression): Pair<PsiExpression?, List<SourceLocation>> {
            val locations = mutableListOf<SourceLocation>()
            var constructor = button.resolveConstructor() ?: fail(button, "The button constructor is unresolved.")
            var args = button.argumentList?.expressions.orEmpty().toList()
            val visited = mutableSetOf<PsiMethod>()
            while (constructor !is PsiCompiledElement) {
                if (!visited.add(constructor) || visited.size > limits.depth) throw Bound(constructor)
                source(constructor); locations += location(constructor)
                val delegation = (constructor.body?.statements?.firstOrNull() as? PsiExpressionStatement)?.expression as? PsiMethodCallExpression
                    ?: fail(constructor, "The button requires direct constructor delegation.")
                if (delegation.methodExpression.referenceName !in setOf("this", "super") || !delegation.resolveMethodGenerics().isValidResult) fail(delegation, "Button constructor delegation is unresolved.")
                val env = constructor.parameterList.parameters.zip(args).toMap()
                args = delegation.argumentList.expressions.map { e -> if (e is PsiReferenceExpression && e.qualifierExpression == null) env[e.resolve()] ?: e else e }
                constructor = delegation.resolveMethod() ?: fail(delegation, "The framework button constructor is unavailable.")
            }
            source(constructor)
            if (constructor.containingClass?.qualifiedName != BUTTON) fail(constructor, "A direct standard AjaxButton constructor route is required.")
            val index = constructor.parameterList.parameters.indexOfFirst { (it.type as? PsiClassType)?.resolve()?.qualifiedName == FORM }
            return (if (index >= 0) args.getOrNull(index) else null) to locations
        }
        fun project(button: PsiNewExpression, callback: PsiMethod): PageReport? {
            source(callback); nodes.clear()
            val anonymous = button.anonymousClass!!
            if (anonymous.fields.isNotEmpty() || anonymous.initializers.isNotEmpty() || anonymous.methods.any { it.name !in setOf("onSubmit", "onError") || it.isConstructor }) fail(anonymous, "Unreviewed anonymous button initialization or overrides are unsupported.")
            if (callback.hasModifierProperty(PsiModifier.STATIC) || callback.returnType != PsiTypes.voidType() || callback.parameterList.parameters.singleOrNull()?.type?.canonicalText != TARGET ||
                callback.findDeepestSuperMethods().none { it is PsiCompiledElement && it.containingClass?.qualifiedName == BUTTON && it.name == callback.name }) fail(callback, "A standard void AjaxButton callback is required.")
            var base: PsiClass? = type(button); val seen = mutableSetOf<PsiClass>()
            while (base != null && base !is PsiCompiledElement) {
                if (!seen.add(base) || seen.size > limits.depth) throw Bound(base)
                source(base)
                if (base.methods.any { !it.isConstructor && !it.hasModifierProperty(PsiModifier.STATIC) && it.findSuperMethods().isNotEmpty() }) fail(base, "A source button class overrides the framework route.")
                base = base.superClass
            }
            if (base?.qualifiedName != BUTTON) fail(button, "The source button must directly reach framework AjaxButton.")
            if (callback.body == null) fail(callback, "The source callback body is unavailable.")
            val chain = ancestry(button)
            val form = chain.dropLast(1).lastOrNull { isForm(it) } ?: fail(button, "The button requires a directly declared enclosing form.")
            if (type(form).qualifiedName != FORM || form.anonymousClass?.methods.orEmpty().any { it.name != "getLocale" || it.returnType?.canonicalText != "java.util.Locale" || it.parameterList.parametersCount != 0 } ||
                form.anonymousClass?.let { it.fields.isNotEmpty() || it.initializers.isNotEmpty() } == true)
                fail(form, "Only standard Form declarations with optional opaque getLocale are reviewed.")
            val (explicit, constructors) = association(button)
            val noExplicit = explicit == null || explicit is PsiLiteralExpression && explicit.value == null ||
                explicit is PsiTypeCastExpression && explicit.operand is PsiLiteralExpression && (explicit.operand as PsiLiteralExpression).value == null
            if (!noExplicit && creation(explicit) != form) fail(button, "The explicit form differs from the enclosing declared form or is unproved.")
            val selected = linkedSetOf<PsiNewExpression>()
            fun include(e: PsiNewExpression): String { selected += ancestry(e); return node(e).nodeId }
            val triggerId = include(button); val formId = include(form)
            fun component(e: PsiExpression): String {
                if (e is PsiThisExpression) {
                    if (e.qualifier == null) return triggerId
                    if (e.qualifier?.resolve() == page) return primary.rootNodeId
                }
                val declared = creation(e) ?: fail(e, "Callback targets need unchanged direct constructor locals.")
                return include(declared)
            }
            fun componentPath(id: String) = if (id == primary.rootNodeId) "" else path(selected.single { node(it).nodeId == id })
            val shared = WicketSubmissionEffects(callback, triggerId, ::componentPath, ::component, declarations::component, ::descendants, ::fail)
            val effects = callback.body?.statements.orEmpty().flatMap { statement ->
                tick(statement)
                val call = (statement as? PsiExpressionStatement)?.expression as? PsiMethodCallExpression ?: fail(statement, "Callback control flow or local data setup is outside the straight-line declaration contract.")
                if (!call.resolveMethodGenerics().isValidResult) fail(call, "A callback call is unresolved or inaccessible.")
                shared.declarations(call, call.resolveMethod() ?: fail(call, "The callback call is unresolved."))
                    ?: fail(call, "Only source feedback and direct Ajax refresh declarations are reviewed.")
            }
            if (effects.size > limits.effects) throw Bound(callback)
            shared.checkTargetUses(); validateUses(selected, button)
            val markupFile = WicketSourceFiles.find(page.project, primary.markupPath!!) ?: fail(page, "The own companion source is unavailable.")
            val psi = PsiManager.getInstance(page.project).findFile(markupFile) ?: fail(page, "The own companion PSI is unavailable.")
            source(psi)
            val raw = WicketMarkupAnalyzer().analyzeInheritanceSource(psi, primary.markup!!.kind)
            if (raw.documentDiagnostics.isNotEmpty()) fail(psi, "Own companion document scope is incomplete.")
            val byId = raw.elements.associateBy { it.nodeId }
            fun markupPath(element: MarkupElement, active: Set<String> = emptySet()): String? {
                tick(psi); if (element.nodeId in active || active.size >= limits.depth) throw Bound(psi)
                if (!element.participatesInComponentTree || element.confidence != Confidence.CONFIRMED || element.diagnostics.isNotEmpty()) return null
                val prefix = element.parentWicketElementId?.let { byId[it]?.let { parent -> markupPath(parent, active + element.nodeId) } ?: return null }
                return listOfNotNull(prefix, element.wicketId ?: return null).joinToString(":")
            }
            val paths = selected.map(::path).toSet()
            val slots = raw.elements.filter { it.hasWicketId && markupPath(it) in paths }.map { it.nodeId }.toSet()
            val markup = raw.copy(elements = raw.elements.map { it.copy(participatesInComponentTree = it.nodeId in slots) }, scopeComplete = false)
            val metadata = SubmissionTemplate("form-submission:${page.qualifiedName}:${callback.textOffset}", formId, path(form), location(form), formId, path(form), triggerId, path(button),
                callback.name, location(callback), null, null, emptyList(), emptyList(), effects,
                formCallback = FormCallbackRoute("$BUTTON.${callback.name}", if (noExplicit) "ENCLOSING_DECLARED_FORM" else "EXPLICIT_DECLARED_FORM",
                    selected.map { location(attachment(it).call) }.distinct().sortedBy { it.offset }, constructors, explicit?.let(::location)))
            val warning = Diagnostic(DiagnosticCode.UNRESOLVED_SCOPE, "Source form callback declarations only. Own-companion paths reflect explicit additions, not a proved initial/runtime hierarchy. Inherited markup placement, initialization/configuration/behavior effects, default form processing, validation, models, feedback/business outcomes, reachability and later state remain unknown. Callbacks are not evaluated.", location(callback))
            if (selected.size + 1 > limits.nodes) throw Bound(callback)
            val root = primary.nodes.single { it.nodeId == primary.rootNodeId }.copy(diagnostics = emptyList(), confidence = Confidence.CONFIRMED)
            val report = PageReport(primary.qualifiedClassName, primary.rootNodeId, listOf(root) + selected.map { node(it) }, primary.markupPath,
                warnings = listOf(warning), markup = markup, javaSourceFiles = sources.sorted(), submissionTemplate = metadata)
            return WicketGraphMatcher().match(report).also { matched ->
                if (matched.matching!!.findings.any { it.code != DiagnosticCode.MATCHED } || matched.matching.findings.size != selected.size)
                    fail(callback, "The declared callback targets do not have unique own-companion markup counterparts.")
            }
        }
    }
}
private const val BUTTON = "org.apache.wicket.ajax.markup.html.form.AjaxButton"
private const val TARGET = "org.apache.wicket.ajax.AjaxRequestTarget"
private const val FORM = "org.apache.wicket.markup.html.form.Form"
private fun location(e: PsiElement) = SourceLocation(e.containingFile.virtualFile.path, e.textOffset)
