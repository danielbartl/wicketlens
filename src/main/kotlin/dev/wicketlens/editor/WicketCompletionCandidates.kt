package dev.wicketlens.editor

import com.intellij.openapi.progress.ProgressManager
import dev.wicketlens.model.*

/** Value-only projections of the same graphs used by reports and navigation. No new bindings. */
internal data class WicketIdCandidate(val id: String, val componentTypes: List<String>, val contexts: List<String>,
    val confidence: Confidence, val counterparts: List<SourceLocation>, val possible: Boolean = false)

internal class WicketCompletionCandidates(private val limits: Limits = Limits()) {
    data class Limits(val steps: Int = 30000, val scopes: Int = 64, val candidates: Int = 128, val depth: Int = 32)
    data class Result(val candidates: List<WicketIdCandidate>, val complete: Boolean, val hasScope: Boolean = false)
    private class Bound : RuntimeException(null, null, false, false)

    fun project(reports: List<PageReport>, javaOrigin: SourceLocation? = null, htmlAttribute: SourceLocation? = null): Result {
        require((javaOrigin == null) != (htmlAttribute == null))
        var steps = 0
        fun tick() { ProgressManager.checkCanceled(); if (++steps > limits.steps) throw Bound() }
        val scopes = mutableListOf<List<WicketIdCandidate>>()
        fun add(scope: List<WicketIdCandidate>) { if (scopes.size >= limits.scopes) throw Bound(); scopes += scope }
        return try {
            for (report in reports) {
                tick()
                val markup = report.markup ?: continue
                val declaration = report.lifecycle != null || report.populationTemplate != null || report.treeTemplate != null ||
                    report.formTemplate != null || report.toolbarTemplate != null || report.baseLayoutTemplate != null || report.initialContentSetup != null
                if (markup.documentDiagnostics.isNotEmpty() || !declaration && report.warnings.any { it.code == DiagnosticCode.UNRESOLVED_SCOPE }) continue
                val nodes = report.nodes.associateBy { tick(); it.nodeId }
                val elements = markup.elements.filter { tick(); it.participatesInComponentTree && it.hasWicketId }
                val byMarkup = elements.associateBy { it.nodeId }
                val javaSiblings = report.nodes.groupBy { tick(); it.parentNodeId }
                val javaCounts = report.nodes.filterNot { it.templateItem }.groupingBy { it.parentNodeId to it.wicketId }.eachCount()
                val markupCounts = elements.groupingBy { it.parentWicketElementId to it.wicketId }.eachCount()
                fun javaPath(nodeId: String?): List<String>? {
                    val path = mutableListOf<String>(); val seen = mutableSetOf<String>(); var current = nodeId
                    while (current != report.rootNodeId) {
                        tick(); if (current == null || !seen.add(current)) return null
                        if (seen.size > limits.depth) throw Bound()
                        val node = nodes[current] ?: return null
                        if (node.confidence == Confidence.UNRESOLVED || node.conditional || node.branchMerged) return null
                        if (!node.templateItem) {
                            val id = node.wicketId?.takeIf { it.isNotEmpty() } ?: return null
                            if (javaCounts[node.parentNodeId to id] != 1) return null
                            path += id
                        }
                        current = node.parentNodeId
                    }
                    return path.asReversed()
                }
                fun markupPath(nodeId: String?): List<String>? {
                    val path = mutableListOf<String>(); val seen = mutableSetOf<String>(); var current = nodeId
                    while (current != null) {
                        tick(); if (!seen.add(current)) return null
                        if (seen.size > limits.depth) throw Bound()
                        val node = byMarkup[current] ?: return null
                        val id = node.wicketId?.takeIf { it.isNotEmpty() } ?: return null
                        if (node.confidence == Confidence.UNRESOLVED || markupCounts[node.parentWicketElementId to id] != 1) return null
                        path += id; current = node.parentWicketElementId
                    }
                    return path.asReversed()
                }
                val javaPaths = report.nodes.associate { tick(); it.nodeId to javaPath(it.nodeId) }
                val javaParents = report.nodes.associate { tick(); it.nodeId to javaPath(it.parentNodeId) }
                val htmlPaths = elements.associate { tick(); it.nodeId to markupPath(it.nodeId) }
                val htmlParents = elements.associate { tick(); it.nodeId to markupPath(it.parentWicketElementId) }
                fun lookupScopes(element: MarkupElement) = markup.completionLookupScopes[element.wicketIdLocation].orEmpty().filter { tick(); it in nodes }
                fun editable(node: ComponentNode) = !node.computedId && !node.idUsedInComputation && !node.branchMerged && !node.conditional
                fun context(path: List<String>): String {
                    val kind = when {
                        report.populationTemplate != null -> "row ${report.populationTemplate.hostComponentPath}" + (report.populationTemplate.conditions.takeIf { it.isNotEmpty() }?.joinToString(", ", " [", "]") { "${it.expression}=${it.result}" }.orEmpty())
                        report.lifecycle?.directContent != null -> "event ${report.lifecycle.componentPath}"
                        report.lifecycle?.contentFactory != null -> "factory ${report.lifecycle.componentPath}"
                        report.lifecycle?.selection != null -> "step ${report.lifecycle.selection.registrationIndex}"
                        report.lifecycle != null -> "replacement ${report.lifecycle.componentPath}"
                        report.treeTemplate != null -> "tree node"
                        report.formTemplate != null -> "input ${report.formTemplate.inputComponentPath}"
                        report.toolbarTemplate != null -> "toolbar ${report.toolbarTemplate.hostComponentPath}"
                        report.baseLayoutTemplate != null -> "base layout"
                        report.initialContentSetup != null -> "initial content"
                        else -> path.joinToString(":").ifEmpty { "root" }
                    }
                    return "${report.qualifiedClassName.substringAfterLast('.')}: $kind".take(256)
                }
                fun candidate(id: String, type: String, path: List<String>, inferred: Boolean, at: SourceLocation) = WicketIdCandidate(
                    id, listOf(type), listOf(context(path)), if (inferred || declaration) Confidence.INFERRED else Confidence.CONFIRMED, listOf(at))
                if (javaOrigin != null) {
                    val currentNodes = report.nodes.filter { javaOrigin in it.idLocations() }
                    for (current in currentNodes) {
                        tick()
                        if (!editable(current) || current.queuedInNodeId != null || current.templateItem) continue
                        if (current.diagnostics.any { it.code !in setOf(DiagnosticCode.UNRESOLVED_ID, DiagnosticCode.UNRESOLVED_QUEUE, DiagnosticCode.PARTIAL_ANALYSIS, DiagnosticCode.UNRESOLVED_SCOPE) }) continue
                        val parent = javaParents[current.nodeId] ?: continue
                        val used = report.nodes.filter { it.nodeId != current.nodeId && javaParents[it.nodeId] == parent }.mapNotNull { it.wicketId }.toSet()
                        val choices = elements.filter { tick(); (htmlParents[it.nodeId] == parent || current.parentNodeId in lookupScopes(it)) && htmlPaths[it.nodeId] != null &&
                            it.confidence != Confidence.UNRESOLVED && it.wicketId !in used }.mapNotNull { element ->
                            val id = element.wicketId?.takeIf { it.isNotEmpty() && it.length <= 256 } ?: return@mapNotNull null
                            candidate(id, current.componentType, parent, current.template || current.confidence == Confidence.INFERRED,
                                element.wicketIdLocation ?: element.location)
                        }
                        add(choices)
                    }
                } else {
                    val currentElements = elements.filter { it.wicketIdLocation == htmlAttribute }
                    for (current in currentElements) {
                        tick()
                        val lookup = lookupScopes(current)
                        val parent = htmlParents[current.nodeId]
                        if (parent == null && lookup.isEmpty()) continue
                        var owners = if (lookup.isEmpty()) report.nodes.filter { javaPaths[it.nodeId] == parent }
                            else lookup.mapNotNull { nodes[it] }.filter { javaPaths[it.nodeId] != null }
                        if (owners.any { it.templateItem }) owners = owners.filter { it.templateItem }
                        // Unexpanded repeaters have separate row graphs; their page shell is not an empty row.
                        owners = owners.filter { it.repetition == null || javaSiblings[it.nodeId].orEmpty().any { child -> child.templateItem } }
                        if (owners.isEmpty() || lookup.isEmpty() && owners.size != 1) continue
                        val ownerIds = owners.map { it.nodeId }.toSet()
                        val usedByParent = elements.filter { tick(); it.nodeId != current.nodeId }.groupBy { htmlParents[it.nodeId] }
                            .mapValues { (_, siblings) -> siblings.mapNotNull { it.wicketId }.toSet() }
                        val choices = report.nodes.filter { tick(); it.nodeId != report.rootNodeId && !it.templateItem && editable(it) &&
                            (if (lookup.isEmpty()) javaParents[it.nodeId] == parent else it.parentNodeId in ownerIds) && javaPaths[it.nodeId] != null &&
                            (lookup.isEmpty() || lookup.firstOrNull { scope -> tick(); javaCounts[scope to it.wicketId] != null } == it.parentNodeId) &&
                            it.wicketId !in usedByParent[javaParents[it.nodeId]].orEmpty() }.mapNotNull { node ->
                            val id = node.wicketId?.takeIf { it.isNotEmpty() && it.length <= 256 } ?: return@mapNotNull null
                            candidate(id, node.componentType, javaParents[node.nodeId]!!, node.template || node.confidence == Confidence.INFERRED,
                                node.idLocation ?: node.javaLocation)
                        }
                        add(choices)
                    }
                }
            }
            if (scopes.isEmpty()) return Result(emptyList(), true)
            // One literal/attribute edits all its observed uses: offer only IDs valid in each proved scope.
            val shared = scopes.map { it.map { candidate -> candidate.id }.toSet() }.reduce { left, right -> left intersect right }
            if (shared.size > limits.candidates) throw Bound()
            val candidates = shared.sorted().map { id ->
                tick(); val uses = scopes.flatten().filter { it.id == id }
                WicketIdCandidate(id, uses.flatMap { it.componentTypes }.distinct().sorted(), uses.flatMap { it.contexts }.distinct().sorted(),
                    if (uses.any { it.confidence == Confidence.INFERRED }) Confidence.INFERRED else Confidence.CONFIRMED,
                    uses.flatMap { it.counterparts }.distinct())
            }
            Result(candidates, true, hasScope = true)
        } catch (_: Bound) { Result(emptyList(), false) }
    }
}
