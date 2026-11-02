package dev.wicketlens.analysis

import com.intellij.openapi.progress.ProgressManager
import dev.wicketlens.model.*

/** Static queue candidates, constrained by actual Java containers and the selected markup region. */
internal object WicketQueueResolver {
    fun resolve(report: PageReport, containers: Set<String>, boundaries: Set<String>): PageReport {
        val budget = TraversalBudget()
        if (report.branchPathCount == 1) return resolvePath(report, containers, boundaries, budget)
        if (report.branchPathCount !in 2..8 || report.nodes.none { it.queuedInNodeId != null }) return report
        val byPath = (0 until report.branchPathCount).map { index ->
            ProgressManager.checkCanceled()
            val nodes = report.nodes.filter { it.branchPathMask == 0 || it.branchPathMask and (1 shl index) != 0 }.map { node ->
                val diagnostics = node.diagnostics.filterNot { it.code == DiagnosticCode.UNRESOLVED_BRANCH }
                node.copy(conditional = false, diagnostics = diagnostics,
                    confidence = if (node.conditional && diagnostics.isEmpty()) Confidence.CONFIRMED else node.confidence)
            }
            resolvePath(report.copy(nodes = nodes, branchPathCount = 1), containers, boundaries, budget).nodes.associateBy { it.nodeId }
        }
        if (budget.exhausted) return report
        return report.copy(nodes = report.nodes.map { node ->
            ProgressManager.checkCanceled()
            if (node.queuedInNodeId == null) return@map node
            val alternatives = byPath.mapNotNull { it[node.nodeId] }
            val parents = alternatives.map { it.parentNodeId }.distinct()
            if (alternatives.isEmpty() || alternatives.any { candidate ->
                    candidate.confidence != Confidence.CONFIRMED || candidate.diagnostics.any { it.code == DiagnosticCode.UNRESOLVED_QUEUE }
                } || parents.size != 1 || parents.single() == null) node
            else node.copy(parentNodeId = parents.single(),
                confidence = if (node.conditional) Confidence.UNRESOLVED else Confidence.CONFIRMED,
                diagnostics = node.diagnostics.filterNot { it.code == DiagnosticCode.UNRESOLVED_QUEUE })
        })
    }

    /** Every feasible path shares one traversal budget; a partial proof never upgrades the union. */
    private class TraversalBudget {
        private var visits = 0
        var exhausted = false
            private set
        fun visit(): Boolean {
            ProgressManager.checkCanceled()
            if (++visits > 4096) exhausted = true
            return !exhausted
        }
    }

    private fun resolvePath(report: PageReport, containers: Set<String>, boundaries: Set<String>, budget: TraversalBudget): PageReport {
        val queued = report.nodes.filter { it.queuedInNodeId != null }
        if (queued.isEmpty()) return report
        val markup = report.markup
        if (markup?.scopeComplete != true) return report
        val byId = markup.elements.associateBy { it.nodeId }
        fun inHeader(element: MarkupElement) = generateSequence(element) { byId[it.parentElementId] }
            .any { it.tagName in setOf("head", "wicket:head") }
        val elements = markup.elements.filter { it.participatesInComponentTree && it.hasWicketId && !inHeader(it) }
        val children = elements.groupBy { it.parentWicketElementId }
        val direct = report.nodes.filter { it.queuedInNodeId == null && !it.templateItem }.groupBy { it.parentNodeId }
        val items = report.nodes.filter { it.templateItem }.groupBy { it.parentNodeId }
        val queues = queued.filter { !it.wicketId.isNullOrEmpty() && it.diagnostics.all { d -> d.code == DiagnosticCode.UNRESOLVED_QUEUE } }
            .groupBy { it.queuedInNodeId }
        val candidates = mutableMapOf<String, MutableSet<Pair<String, String>>>()
        fun walk(markupParent: String?, parent: String, scopes: List<String>, ancestry: Set<String>) {
            if (!budget.visit() || parent in ancestry) return
            children[markupParent].orEmpty().forEach { element ->
                val explicit = direct[parent].orEmpty().filter { it.wicketId == element.wicketId }
                val available = if (explicit.isNotEmpty()) explicit else scopes.firstNotNullOfOrNull { scope ->
                    queues[scope].orEmpty().filter { it.wicketId == element.wicketId }.takeIf { it.isNotEmpty() }
                }.orEmpty()
                if (available.size != 1) return@forEach
                val node = available.single()
                if (node.queuedInNodeId != null) candidates.getOrPut(node.nodeId) { linkedSetOf() } += parent to element.nodeId
                if (node.repetition != null) {
                    items[node.nodeId].orEmpty().singleOrNull()?.let { item ->
                        walk(element.nodeId, item.nodeId, listOf(item.nodeId), ancestry + parent + node.nodeId)
                    }
                } else if (node.nodeId in containers && node.nodeId !in boundaries) {
                    walk(element.nodeId, node.nodeId, listOf(node.nodeId) + scopes, ancestry + parent)
                }
            }
        }
        walk(null, report.rootNodeId, listOf(report.rootNodeId), emptySet())
        if (budget.exhausted) return report
        val placements = candidates.filterValues { it.size == 1 }.mapValues { it.value.single().first }
        val nodes = report.nodes.map { node ->
            val parent = placements[node.nodeId]
            if (node.queuedInNodeId == null || parent == null) node else node.copy(parentNodeId = parent,
                confidence = Confidence.CONFIRMED, diagnostics = node.diagnostics.filterNot { it.code == DiagnosticCode.UNRESOLVED_QUEUE })
        }
        return report.copy(nodes = nodes)
    }
}
