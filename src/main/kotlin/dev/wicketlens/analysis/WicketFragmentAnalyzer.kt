package dev.wicketlens.analysis

import com.intellij.openapi.progress.ProgressManager
import com.intellij.psi.*
import com.intellij.psi.util.PsiTreeUtil
import dev.wicketlens.model.*

/** Fragment definitions are reusable markup, never component allocations at their declaration site. */
internal class WicketFragmentAnalyzer {
    private data class UseKey(val allocation: SourceLocation, val definition: String?, val provider: String?, val origin: SourceLocation?)
    fun analyze(page: PsiClass, report: PageReport): PageReport {
        val markup = report.markup
        val sources = report.javaSourceFiles.toMutableSet()
        fun key(use: FragmentReference) = UseKey(use.javaLocation, use.definitionId, use.providerNodeId, use.definitionIdOrigin)
        val uses = linkedMapOf<UseKey, FragmentReference>()
        val hierarchy = generateSequence(page) { it.superClass }.take(32).takeWhile { it !is PsiCompiledElement }.toList()
        val providerCustom = hierarchy.any { owner -> owner.methods.any { method -> method.name in PROVIDER_METHODS &&
            method.findDeepestSuperMethods().any { it.containingClass?.qualifiedName?.startsWith("org.apache.wicket.") == true } } }
        var limited = hierarchy.lastOrNull()?.superClass?.let { it !is PsiCompiledElement } == true
        var count = 0
        var steps = 0
        for (owner in hierarchy) {
            ProgressManager.checkCanceled()
            val path = owner.containingFile.virtualFile?.path ?: continue
            sources += path
            owner.accept(object : JavaRecursiveElementWalkingVisitor() {
                override fun visitElement(element: PsiElement) {
                    ProgressManager.checkCanceled()
                    if (++steps > 20000) limited = true
                    if (!limited) super.visitElement(element)
                }
                override fun visitClass(aClass: PsiClass) { if (aClass == owner || aClass is PsiAnonymousClass) super.visitClass(aClass) }
                override fun visitNewExpression(expression: PsiNewExpression) {
                    ProgressManager.checkCanceled()
                    if (++count > 1024) { limited = true; return }
                    val constructor = expression.resolveConstructor()
                    if (constructor?.containingClass?.qualifiedName == TYPE && expression.anonymousClass == null &&
                        expression.resolveMethodGenerics().isValidResult) {
                        constructor.containingFile.virtualFile?.path?.substringBefore("!/")?.let(sources::add)
                        val args = expression.argumentList?.expressions.orEmpty()
                        val definition = args.getOrNull(1) as? PsiLiteralExpression
                        val provider = args.getOrNull(2) as? PsiThisExpression
                        val providerClass = provider?.qualifier?.resolve() ?: provider?.let { PsiTreeUtil.getParentOfType(it, PsiClass::class.java) }
                        val location = location(expression)
                        val use = FragmentReference(definition?.value as? String,
                            report.rootNodeId.takeIf { provider != null && providerClass in hierarchy }, location, args.getOrNull(1)?.let(::location))
                        uses[key(use)] = use
                    }
                    super.visitNewExpression(expression)
                }
            })
        }
        val captured = report.nodes.mapNotNull { it.fragment }
        val capturedLocations = captured.map { it.javaLocation }.toSet()
        uses.keys.removeAll { it.allocation in capturedLocations }
        captured.forEach { uses[key(it)] = it }
        if (uses.isEmpty() && report.nodes.none { it.sourceSetupExpanded }) return report
        if (markup == null) {
            val unresolved = uses.mapValues { (_, use) -> use.copy(
                instanceNodeIds = report.nodes.filter { it.fragment?.let { ref -> key(ref) == key(use) } == true }.map { it.nodeId },
                diagnostics = listOf(Diagnostic(DiagnosticCode.UNRESOLVED_SCOPE,
                    "No conventional provider markup is available for fragment lookup.", use.definitionIdOrigin))) }
            return report.copy(javaSourceFiles = sources.sorted(), fragmentUses = unresolved.values.toList(),
                nodes = report.nodes.map { node -> node.fragment?.let { node.copy(fragment = unresolved[key(it)]) } ?: node })
        }
        val elements = markup.elements.toMutableList()
        val markupSources = markup.sourceFiles.toMutableSet()
        val templates = mutableMapOf<String, WicketMarkupInheritanceAnalyzer.Result?>()
        val ownedPaths = mutableMapOf<String, String>()
        fun issue(use: FragmentReference, text: String) = Diagnostic(DiagnosticCode.UNRESOLVED_SCOPE, text, use.definitionIdOrigin ?: use.javaLocation)
        fun resolve(use: FragmentReference): FragmentReference {
            ProgressManager.checkCanceled()
            val provider = use.providerNodeId
            val candidates = elements.filter { it.tagName == "wicket:fragment" && it.wicketId == use.definitionId &&
                (it.markupProviderNodeId ?: report.rootNodeId) == provider }
            val definition = candidates.singleOrNull()
            val byId = elements.associateBy { it.nodeId }
            val message = when {
                limited -> "Fragment discovery exceeds the 32-class/1024-construction/20000-step bound."
                providerCustom && provider == report.rootNodeId -> "Custom provider markup sourcing prevents fragment lookup."
                provider != report.rootNodeId && provider !in ownedPaths -> "The fragment markup provider is not a proved markup scope."
                use.definitionId.isNullOrEmpty() -> "The fragment definition ID is not a known nonempty literal."
                definition == null -> "Fragment definition lookup requires exactly one definition in the provider markup."
                definition.diagnostics.any { it.code != DiagnosticCode.UNRESOLVED_PARENT } ||
                    markup.documentDiagnostics.any { it.message.startsWith("Markup syntax") || it.message.startsWith("Markup tag boundaries") } -> "The fragment definition markup is unresolved."
                generateSequence(byId[definition.parentElementId]) { byId[it.parentElementId] }.any { it.tagName == "wicket:fragment" } -> "Nested fragment definitions remain unresolved."
                else -> null
            }
            return use.copy(definitionLocation = definition?.location.takeIf { message == null },
                definitionIdLocation = definition?.wicketIdLocation.takeIf { message == null },
                instanceNodeIds = report.nodes.filter { it.fragment?.let { ref -> key(ref) == key(use) } == true }.map { it.nodeId },
                diagnostics = if (message == null) emptyList() else listOf(issue(use, message)))
        }
        val pending = report.nodes.filter { it.fragment != null || it.sourceSetupExpanded }.map { it.nodeId }.toMutableSet()
        val expanded = mutableSetOf<String>()
        val failures = mutableMapOf<String, List<Diagnostic>>()
        var generated = 0
        for (round in 0 until 16) {
            ProgressManager.checkCanceled()
            if (pending.isEmpty()) break
            val matched = WicketGraphMatcher().match(report.copy(markup = markup.copy(elements = elements.toList())))
            val bindings = matched.matching!!.findings.filter { it.code == DiagnosticCode.MATCHED }.associateBy { it.javaNodeId }
            var progress = false
            for (nodeId in pending.toList()) {
                ProgressManager.checkCanceled()
                val node = report.nodes.single { it.nodeId == nodeId }
                val attachmentId = bindings[if (node.templateItem) node.parentNodeId else nodeId]?.markupNodeId
                val attachment = elements.singleOrNull { it.nodeId == attachmentId } ?: continue
                if (expanded.size >= 64) break
                val originals: List<MarkupElement>
                val prefix: String
                if (node.sourceSetupExpanded) {
                    val result = templates.getOrPut(node.componentType) {
                        val type = JavaPsiFacade.getInstance(page.project).findClasses(node.componentType, page.resolveScope).singleOrNull()
                        type?.let { WicketMarkupInheritanceAnalyzer().analyze(it, WicketComponentTypes.kind(it)) }
                    }
                    var sourceMarkup = result?.markup
                    if (sourceMarkup != null && report.initialContentSetup != null) {
                        val transparent = report.nodes.filter { it.componentType == "org.apache.wicket.markup.html.TransparentWebMarkupContainer" }.map { it.nodeId }.toSet()
                        val owned = report.copy(rootNodeId = node.nodeId, nodes = report.nodes.filter { candidate ->
                            generateSequence(candidate) { parent -> report.nodes.singleOrNull { it.nodeId == parent.parentNodeId } }.take(report.nodes.size).any { it.nodeId == node.nodeId }
                        })
                        val plan = WicketTransparentMarkup.plan(owned.copy(nodes = owned.nodes.map { it.copy(confidence = Confidence.CONFIRMED) }), sourceMarkup, transparent)
                        sourceMarkup = WicketTransparentMarkup.apply(sourceMarkup, plan)
                    }
                    sourceMarkup?.sourceFiles?.let(markupSources::addAll)
                    if (sourceMarkup == null || sourceMarkup.documentDiagnostics.isNotEmpty()) {
                        failures[nodeId] = listOf(Diagnostic(DiagnosticCode.UNRESOLVED_SCOPE,
                            "The expanded source component has missing, ambiguous or unsupported companion markup.", node.javaLocation))
                        continue
                    }
                    originals = sourceMarkup.elements
                    prefix = "owned:$nodeId:"
                    ownedPaths[nodeId] = sourceMarkup.file
                } else {
                    val use = resolve(node.fragment!!)
                    failures[nodeId] = use.diagnostics
                    val definition = elements.singleOrNull { it.location == use.definitionLocation && it.tagName == "wicket:fragment" &&
                        (it.markupProviderNodeId ?: report.rootNodeId) == use.providerNodeId } ?: continue
                    val descendants = linkedSetOf(definition.nodeId)
                    val content = mutableListOf<MarkupElement>()
                    elements.toList().forEach { element ->
                        ProgressManager.checkCanceled()
                        if (element.parentElementId in descendants && element.tagName != "wicket:fragment") {
                            descendants += element.nodeId; content += element
                        }
                    }
                    originals = content
                    prefix = "fragment:$nodeId:"
                }
                if (generated + originals.size > 4096) { ownedPaths.remove(nodeId); continue }
                val ids = originals.associate { it.nodeId to "$prefix${it.nodeId}" }
                val projected = linkedMapOf<String, MarkupElement>()
                originals.forEach { element ->
                    ProgressManager.checkCanceled()
                    val parent = projected[element.parentElementId]
                    val diagnostics = element.diagnostics.filterNot { it.code == DiagnosticCode.UNRESOLVED_PARENT }
                    val active = if (node.sourceSetupExpanded) element.participatesInComponentTree else true
                    val uncertain = diagnostics.isNotEmpty() || active && parent?.confidence == Confidence.UNRESOLVED
                    projected[element.nodeId] = element.copy(nodeId = ids.getValue(element.nodeId),
                        parentElementId = parent?.nodeId ?: attachment.nodeId,
                        parentWicketElementId = if (!active) null else if (node.sourceSetupExpanded && report.initialContentSetup != null) element.parentWicketElementId?.let { ids[it] } ?: attachment.nodeId
                            else if (parent?.hasWicketId == true && parent.participatesInComponentTree) parent.nodeId
                            else parent?.parentWicketElementId ?: attachment.nodeId,
                        participatesInComponentTree = active, confidence = if (uncertain) Confidence.UNRESOLVED else Confidence.CONFIRMED,
                        diagnostics = diagnostics, markupProviderNodeId = if (node.sourceSetupExpanded) nodeId else element.markupProviderNodeId)
                }
                elements += projected.values
                generated += projected.size
                pending -= nodeId; expanded += nodeId; progress = true
            }
            if (!progress) break
        }
        val resolved = uses.mapValues { (_, use) -> resolve(use) }
        val byNodeId = report.nodes.associateBy { it.nodeId }
        val finalNodes = report.nodes.map { node ->
            val use = node.fragment?.let { resolved[key(it)] }
            val description = if (node.nodeId in pending) failures[node.nodeId].orEmpty().ifEmpty { listOf(Diagnostic(DiagnosticCode.UNRESOLVED_SCOPE,
                "Markup contents are not projected: attachment is unresolved or the 64-instance/16-level/4096-element bound was reached.", node.javaLocation)) } else emptyList()
            val ancestors = generateSequence(node.parentNodeId) { byNodeId[it]?.parentNodeId }.take(report.nodes.size)
            node.copy(fragment = use, ownedMarkupPath = ownedPaths[node.nodeId],
                confidence = if (ancestors.any { it in pending }) Confidence.UNRESOLVED else node.confidence,
                diagnostics = node.diagnostics + description + if (ancestors.any { it in pending }) listOf(Diagnostic(DiagnosticCode.UNRESOLVED_SCOPE,
                    "The containing markup instance is unresolved.", node.javaLocation)) else emptyList())
        }
        val diagnostics = (markup.documentDiagnostics + elements.flatMap { it.diagnostics }).distinct()
        return report.copy(javaSourceFiles = sources.sorted(), fragmentUses = resolved.values.toList(), nodes = finalNodes,
            markup = markup.copy(elements = elements, sourceFiles = markupSources.toList(), diagnostics = diagnostics,
                scopeComplete = diagnostics.isEmpty() && pending.isEmpty()))
    }

    companion object {
        const val TYPE = "org.apache.wicket.markup.html.panel.Fragment"
        fun isFragment(type: PsiClass) = type.qualifiedName == TYPE || com.intellij.psi.util.InheritanceUtil.isInheritor(type, TYPE)
        val PROVIDER_METHODS = setOf("getMarkup", "getMarkupType", "getMarkupResourceStream", "getMarkupSourcingStrategy", "newMarkupSourcingStrategy", "chooseMarkup")
        private fun location(element: PsiElement) = SourceLocation(element.containingFile.virtualFile.path, element.textOffset)
    }
}
