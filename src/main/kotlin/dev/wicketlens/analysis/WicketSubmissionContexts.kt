package dev.wicketlens.analysis

import com.intellij.openapi.progress.ProgressManager
import com.intellij.psi.*
import com.intellij.psi.util.InheritanceUtil
import com.intellij.psi.util.PsiTreeUtil
import dev.wicketlens.model.*

/** A bounded source route from an owned AjaxButton to a selected host hook. Nothing is executed. */
internal class WicketSubmissionContexts(private val limits: Limits = Limits()) {
    data class Limits(val steps: Int = 40000, val sources: Int = 96, val contexts: Int = 8, val nodes: Int = 128, val depth: Int = 12, val effects: Int = 128)
    data class Result(val reports: List<PageReport>, val sources: List<String>, val diagnostics: List<Diagnostic>, val complete: Boolean)
    private class Unknown(val at: PsiElement, override val message: String) : RuntimeException(null, null, false, false)
    private class Bound(val at: PsiElement) : RuntimeException(null, null, false, false)
    fun analyze(page: PsiClass, primary: PageReport): Result = Session(page, primary).run()

    private data class Content(val nodes: List<ComponentNode>, val root: String, val construction: SourceLocation,
        val fragment: FragmentReference?, val idOrigin: SourceLocation)

    private inner class Session(val page: PsiClass, val primary: PageReport) {
        val sources = linkedSetOf<String>()
        val reports = mutableListOf<PageReport>()
        val diagnostics = mutableListOf<Diagnostic>()
        val scanned = mutableMapOf<PsiElement, List<PsiElement>>()
        var steps = 0
        fun tick(at: PsiElement) { ProgressManager.checkCanceled(); if (++steps > limits.steps || sources.size > limits.sources) throw Bound(at) }
        fun fail(at: PsiElement, message: String): Nothing = throw Unknown(at, message)
        fun source(at: PsiElement) {
            tick(at)
            sources += at.containingFile?.virtualFile?.path?.substringBefore("!/") ?: fail(at, "Physical source is unavailable.")
            if (sources.size > limits.sources) throw Bound(at)
            if (at !is PsiCompiledElement && PsiTreeUtil.findChildOfType(at.containingFile, PsiErrorElement::class.java) != null) fail(at, "Java syntax is incomplete.")
        }
        fun descendants(at: PsiElement): List<PsiElement> {
            tick(at)
            return scanned.getOrPut(at) {
                val items = mutableListOf<PsiElement>()
                at.accept(object : JavaRecursiveElementWalkingVisitor() {
                    override fun visitElement(element: PsiElement) { tick(element); items += element; super.visitElement(element) }
                }); items
            }
        }
        val declarations = WicketSourceDeclarations(page, limits.depth, ::tick, ::source, sources, ::fail, { throw Bound(it) }, ::descendants)
        val hosts = WicketInitialContentDeclarations(page, ::tick, ::source, sources, ::fail)
        val initial = primary.nodes.associateBy { it.nodeId }
        fun own(e: PsiExpression?) = e == null || e is PsiThisExpression && e.qualifier == null
        fun reference(e: PsiExpression?, target: PsiElement) = e is PsiReferenceExpression && e.resolve() == target && own(e.qualifierExpression)
        fun call(s: PsiStatement?) = (s as? PsiExpressionStatement)?.expression as? PsiMethodCallExpression
        fun method(call: PsiMethodCallExpression): PsiMethod {
            if (!call.resolveMethodGenerics().isValidResult) fail(call, "A submission call is unresolved or inaccessible.")
            return call.resolveMethod() ?: fail(call, "A submission call is unresolved.")
        }
        val attachments=WicketDeclaredAttachments(page,primary,limits.depth,::tick,::descendants,::fail,{throw Bound(it)}) {currentHost}
        fun ancestry(id:String)=attachments.ancestry(id)
        fun path(id:String)=attachments.path(id)
        fun under(id: String, host: String) = ancestry(id).any { it.nodeId == host }
        fun run(): Result {
            if (primary.initialContentSetup == null || primary.markup == null) return Result(emptyList(), emptyList(), emptyList(), true)
            try {
                source(page)
                val creations = descendants(page).filterIsInstance<PsiNewExpression>()
                for (host in primary.initialContentSetup.hosts) {
                    val creation = creations.singleOrNull { location(it) == host.constructionLocation } ?: continue
                    val anonymous = creation.anonymousClass ?: continue
                    for (callback in anonymous.methods.filterNot { it.isConstructor }) {
                        if (reports.size >= limits.contexts) throw Bound(callback)
                        try { project(creation, callback, host)?.let(reports::add) }
                        catch (e: Unknown) { diagnostics += Diagnostic(DiagnosticCode.UNRESOLVED_SCOPE, "Submission template withheld: ${e.message}", location(e.at)) }
                    }
                }
            } catch (e: Bound) {
                return Result(emptyList(), sources.sorted(), listOf(Diagnostic(DiagnosticCode.PARTIAL_ANALYSIS,
                    "Submission discovery exceeded its bounded source contract; no submission graphs published.", location(e.at))), false)
            }
            return Result(reports.toList(), sources.sorted(), diagnostics.toList(), true)
        }
        val submissionBridge=WicketDialogSubmissionBridge(page,primary,limits.depth,::tick,::source,::descendants,::fail,attachments)
        fun component(e:PsiExpression)=attachments.component(e)
        var currentHost = ""
        fun content(callback: PsiMethod, placement: PsiExpressionStatement, host: InitialContentHost, ignored: Set<PsiStatement>): Content {
            val expression = call(placement)!!.argumentList.expressions.singleOrNull() ?: fail(placement, "A single fresh content value is required.")
            val variable = (expression as? PsiReferenceExpression)?.resolve() as? PsiLocalVariable
            val allocation = (variable?.initializer ?: expression) as? PsiNewExpression ?: fail(expression, "Replacement content must be fresh, not borrowed or aliased.")
            if (allocation.anonymousClass != null) fail(allocation, "Anonymous replacement content is outside the reviewed declaration scope.")
            if (variable != null && (variable.parent.parent != callback.body || callback.body!!.statements.firstOrNull() != variable.parent))
                fail(variable, "A content local must be the callback's first direct declaration.")
            if (allocation.resolveConstructor()?.containingClass?.qualifiedName == WicketFragmentAnalyzer.TYPE) {
                if (variable == null) fail(allocation, "Fragment content requires one direct local declaration.")
                val declared = WicketContentFactoryDeclarations(page, primary.rootNodeId, limits.nodes, ::tick, ::source, sources, ::descendants,
                    declarations::freshCreation, ::fail, { throw Bound(it) }).analyzeEvent(callback, variable, placement, host.contentId, ignored, allowTrailingEffects = true)
                return Content(declared.owner.nodes, declared.owner.rootNodeId, location(allocation), declared.fragment, location(allocation.argumentList!!.expressions[0]))
            }
            val identity = WicketComponentIdentity.trace(allocation, conditionalData = true) ?: fail(allocation, "The fresh replacement constructor ID is unproved.")
            sources += identity.sources
            val constants = WicketSourceConstants(::source) { tick(callback) }
            val id = when (val origin = identity.expression) { is PsiLiteralExpression -> origin.value as? String; is PsiReferenceExpression -> constants.resolve(origin) as? String; else -> null }
            if (id != host.contentId) fail(allocation, "The fresh content must use the guarded slot ID.")
            if (variable != null && descendants(callback.body!!).filterIsInstance<PsiReferenceExpression>().filter { it.resolve() == variable } != listOf(expression))
                fail(variable, "The fresh replacement local escapes its one direct placement.")
            callback.body!!.statements.filter { it != placement && it !in ignored && it != variable?.parent }.firstOrNull()?.let {
                fail(it, "Leaf replacement content cannot have unreviewed setup statements.")
            }
            val type = allocation.classReference?.resolve() as? PsiClass ?: fail(allocation, "The replacement type is unavailable.")
            val root = "submission-content:${callback.textOffset}"
            return Content(listOf(ComponentNode(root, host.contentId, type.qualifiedName!!, host.slotParentNodeId, location(allocation), confidence = Confidence.CONFIRMED,
                idLocation = location(identity.expression), idRenameSafe = false, template = true)), root, location(allocation), null, location(identity.expression))
        }
        fun project(creation: PsiNewExpression, callback: PsiMethod, host: InitialContentHost): PageReport? {
            source(callback)
            val info = hosts.inspect(creation, host.hostNodeId)
            if (callback.hasModifierProperty(PsiModifier.STATIC) || callback.returnType != PsiTypes.voidType() || callback.parameterList.parameters.singleOrNull()?.type?.canonicalText != TARGET)
                fail(callback, "A source void Ajax hook is required.")
            val bridge = submissionBridge.inspect(info, callback, host,creation.anonymousClass!!) ?: return null
            currentHost = host.hostNodeId
            val effects = mutableListOf<SubmissionEffect>()
            val ignored = linkedSetOf<PsiStatement>()
            var placement: PsiExpressionStatement? = null
            val shared = WicketSubmissionEffects(callback, host.hostNodeId, ::path, ::component, declarations::component, ::descendants, ::fail)
            for (statement in callback.body?.statements.orEmpty()) {
                tick(statement)
                if (statement is PsiDeclarationStatement) continue
                val c = call(statement) ?: fail(statement, "Submission callback control flow is outside the straight-line source contract.")
                val resolved = method(c)
                val qualifier = c.methodExpression.qualifierExpression
                val declared = shared.declarations(c, resolved)
                when {
                    declared != null -> { effects += declared; ignored += statement }
                    own(qualifier) && hosts.isSetter(creation.anonymousClass!!.findMethodBySignature(resolved, true) ?: resolved, info) -> {
                        if (placement != null) fail(c, "Competing submission content replacements are unsupported.")
                        placement = statement as PsiExpressionStatement
                    }
                    // Fresh fragment child additions are audited by the shared declaration builder.
                    resolved.containingClass?.qualifiedName == "org.apache.wicket.MarkupContainer" && resolved.name == "add" && qualifier is PsiReferenceExpression && qualifier.resolve() is PsiLocalVariable -> Unit
                    else -> fail(c, "The callback has an unreviewed call beyond feedback, direct Ajax refresh and guarded content placement.")
                }
                if (effects.size > limits.effects) throw Bound(callback)
            }
            shared.checkTargetUses()
            val replacement = placement?.let { content(callback, it, host, ignored) }
            if (replacement == null && callback.body!!.statements.any { it !in ignored }) fail(callback, "Unplaced content declarations are unsupported.")
            val selected = linkedSetOf(primary.rootNodeId)
            fun include(id: String) { selected += ancestry(id).map { it.nodeId } }
            include(bridge.trigger.nodeId)
            effects.forEach { include(it.targetNodeId) }
            if (replacement != null) {
                include(host.slotParentNodeId)
                effects += SubmissionEffect("CONTENT_REPLACEMENT", method(call(placement)!!).name, location(placement!!), replacement.root,
                    "${path(host.slotParentNodeId)}:${host.contentId}", listOf(location(call(placement)!!.argumentList.expressions.single())))
            }
            if (effects.size > limits.effects) throw Bound(callback)
            val nodes = primary.nodes.filter { it.nodeId in selected }.map { it.copy(sourceSetupExpanded = false, ownedMarkupPath = null, fragment = null, template = true, idRenameSafe = false) } +
                replacement?.nodes.orEmpty().map { node -> if (node.nodeId == replacement!!.root) node.copy(wicketId = host.contentId, parentNodeId = host.slotParentNodeId,
                    fragment = replacement.fragment, idLocation = replacement.idOrigin, template = true, idRenameSafe = false) else node.copy(template = true, idRenameSafe = false) }
            if (nodes.size > limits.nodes) throw Bound(callback)
            val slots = primary.matching!!.findings.filter { it.javaNodeId in selected }.mapNotNull { it.markupNodeId }.toSet() +
                if (replacement == null) emptySet() else setOf(primary.matching.findings.single { it.javaNodeId == host.contentNodeId }.markupNodeId!!)
            val markup = primary.markup!!.copy(elements = primary.markup.elements.map { it.copy(participatesInComponentTree = it.nodeId in slots, diagnostics = emptyList()) },
                diagnostics = primary.markup.documentDiagnostics, scopeComplete = false)
            val statements = bridge.method.body!!.statements
            val dispatchIndex = statements.indexOfFirst { call(it) == bridge.dispatch }
            val metadata = SubmissionTemplate("submission:${page.qualifiedName}:${callback.textOffset}", host.hostNodeId, path(host.hostNodeId), location(creation),
                host.slotParentNodeId, path(host.slotParentNodeId), bridge.trigger.nodeId, path(bridge.trigger.nodeId), callback.name, location(callback), location(bridge.method), location(bridge.dispatch),
                statements.take(dispatchIndex).map(::location), statements.drop(dispatchIndex + 1).map(::location), effects.sortedBy { it.location.offset },
                replacement?.root, replacement?.construction, info.setter.let(::location).takeIf { replacement != null })
            val warning = Diagnostic(DiagnosticCode.UNRESOLVED_SCOPE, "Source submission declarations only. The framework route and callback body are not executed. Default form processing, validation, models, feedback/business outcomes, runtime presence, bridge/post-callback effects and later tree/open/close state remain unknown.", location(callback))
            val report = PageReport(primary.qualifiedClassName, primary.rootNodeId, nodes, primary.markupPath, warnings = listOf(warning), markup = markup,
                javaSourceFiles = (primary.javaSourceFiles + sources).distinct().sorted(), submissionTemplate = metadata)
            val matched = WicketGraphMatcher().match(WicketFragmentAnalyzer().analyze(page, WicketGraphMatcher().match(report)))
            if (matched.matching!!.findings.any { it.code != DiagnosticCode.MATCHED }) fail(callback, "Submission targets/content do not have unique supported markup counterparts.")
            return matched
        }
    }
}
private const val TARGET = "org.apache.wicket.ajax.AjaxRequestTarget"
private const val FORM = "org.apache.wicket.markup.html.form.Form"
private const val BUTTON = "org.apache.wicket.ajax.markup.html.form.AjaxButton"
private fun location(e: PsiElement) = SourceLocation(e.containingFile.virtualFile.path, e.textOffset)
