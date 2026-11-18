package dev.wicketlens.analysis

import com.intellij.openapi.progress.ProgressManager
import dev.wicketlens.model.*

/** Resolves transparent lookup against already-proven Java allocations, not HTML tag names. */
internal object WicketTransparentMarkup {
    data class Plan(val transparentLocations: Set<SourceLocation>, val parents: Map<SourceLocation, SourceLocation?>,
        val ambiguous: Set<SourceLocation>, val limited: Boolean = false,
        val completionLookupScopes: Map<SourceLocation, List<String>> = emptyMap())

    fun plan(java: PageReport, markup: MarkupReport?, transparentNodes: Set<String>): Plan {
        if (markup == null || transparentNodes.isEmpty()) return Plan(emptySet(), emptyMap(), emptySet())
        val javaByParent = java.nodes.filter { it.nodeId != java.rootNodeId }.groupBy { it.parentNodeId }
        val javaById = java.nodes.associateBy { it.nodeId }
        val byParent = markup.elements.groupBy { it.parentElementId }
        val bindings = linkedMapOf<SourceLocation, String>()
        val transparent = linkedSetOf<SourceLocation>()
        val completionScopes = linkedMapOf<SourceLocation, List<String>>()
        val editedParents = if (!WicketCompletionAnalysis.active) emptySet() else java.nodes
            .filter { node -> node.idLocations().any(WicketCompletionAnalysis::editingOrigin) }.mapNotNull { it.parentNodeId }.toSet()
        val stack = mutableSetOf<String>()
        var limited = false
        fun visit(element: MarkupElement, scopes: List<String>) {
            ProgressManager.checkCanceled()
            if (stack.size >= 128) { limited = true; return }
            if (!stack.add(element.nodeId)) return
            var children = scopes
            if (element.participatesInComponentTree && element.hasWicketId) {
                var selected: ComponentNode? = null
                val visitedScopes = mutableListOf<String>()
                for (scope in scopes) {
                    visitedScopes += scope
                    val options = javaByParent[scope].orEmpty().filter { it.wicketId == element.wicketId }
                    if (options.isNotEmpty()) {
                        selected = options.singleOrNull()?.takeIf { it.confidence == Confidence.CONFIRMED }
                        break
                    }
                    // Unknown contents could hide an ordinary child that wins over fallback.
                    if (scope in transparentNodes && javaById[scope]?.diagnostics?.any { it.code == DiagnosticCode.UNRESOLVED_SCOPE } == true) break
                }
                if (selected == null && visitedScopes.any { it in transparentNodes } && element.wicketIdLocation != null &&
                    (WicketCompletionAnalysis.editingAttribute(element.wicketIdLocation) || visitedScopes.any { it in editedParents })) {
                    // Retain the lookup path for a completion hypothesis, without selecting an ID
                    // or descending through an unbound component. Ordinary child precedence and
                    // the unknown-transparent-content barrier above still constrain the scopes.
                    completionScopes[element.wicketIdLocation] = visitedScopes.toList()
                }
                children = if (selected == null) emptyList() else {
                    bindings[element.location] = selected.nodeId
                    if (selected.nodeId in transparentNodes) {
                        transparent += element.location
                        listOf(selected.nodeId) + scopes
                    } else listOf(selected.nodeId)
                }
            }
            byParent[element.nodeId].orEmpty().forEach { visit(it, children) }
            stack.remove(element.nodeId)
        }
        byParent[null].orEmpty().forEach { visit(it, listOf(java.rootNodeId)) }
        val locations = bindings.entries.groupBy({ it.value }, { it.key })
        val ambiguous = locations.values.filter { it.size != 1 }.flatten().toMutableSet()
        bindings.forEach { (location, id) ->
            var parent = javaById[id]?.parentNodeId
            val seen = mutableSetOf<String>()
            while (parent != null && parent != java.rootNodeId && seen.add(parent)) {
                ProgressManager.checkCanceled()
                if (locations[parent]?.size != 1) { ambiguous += location; break }
                parent = javaById[parent]?.parentNodeId
            }
        }
        val parents = bindings.mapValues { (_, id) ->
            javaById[id]?.parentNodeId?.let { locations[it]?.singleOrNull() }
        }
        return Plan(transparent - ambiguous, parents, ambiguous, limited, completionScopes)
    }

    fun apply(markup: MarkupReport?, plan: Plan): MarkupReport? {
        if (markup == null || plan.parents.isEmpty() && !plan.limited && plan.completionLookupScopes.isEmpty()) return markup
        if (plan.limited) {
            val issue = Diagnostic(DiagnosticCode.UNRESOLVED_MARKUP, "Transparent markup resolution exceeds the 128-level discovery bound.", SourceLocation(markup.file, 0))
            return markup.copy(elements = markup.elements.map { it.copy(confidence = Confidence.UNRESOLVED) }, scopeComplete = false,
                documentDiagnostics = markup.documentDiagnostics + issue, diagnostics = markup.diagnostics + issue)
        }
        val byLocation = markup.elements.associateBy { it.location }
        val elements = markup.elements.map { element ->
            ProgressManager.checkCanceled()
            val parent = if (element.location in plan.parents) plan.parents[element.location]?.let { byLocation[it]?.nodeId } else element.parentWicketElementId
            val ambiguous = element.location in plan.ambiguous
            element.copy(parentWicketElementId = parent,
                confidence = if (ambiguous) Confidence.UNRESOLVED else element.confidence,
                diagnostics = element.diagnostics + if (!ambiguous) emptyList() else listOf(Diagnostic(DiagnosticCode.UNRESOLVED_MARKUP,
                    "Transparent markup lookup has multiple locations for one component; no binding is selected.", element.wicketIdLocation)))
        }
        val byId = elements.associateBy { it.nodeId }
        val completionScopes = plan.completionLookupScopes.filterKeys { attribute ->
            val element = elements.singleOrNull { it.wicketIdLocation == attribute } ?: return@filterKeys false
            val seen = mutableSetOf<String>(); var parent = element.parentElementId
            while (parent != null) {
                ProgressManager.checkCanceled()
                if (!seen.add(parent) || seen.size >= 128) return@filterKeys false
                val owner = byId[parent] ?: return@filterKeys false
                if (owner.participatesInComponentTree && owner.confidence == Confidence.UNRESOLVED) return@filterKeys false
                parent = owner.parentElementId
            }
            true
        }
        return markup.copy(elements = elements, scopeComplete = markup.scopeComplete && plan.ambiguous.isEmpty(),
            completionLookupScopes = markup.completionLookupScopes + completionScopes,
            diagnostics = (markup.documentDiagnostics + elements.flatMap { it.diagnostics }).distinct())
    }
}
