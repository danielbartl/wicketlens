package dev.wicketlens.analysis

import com.intellij.openapi.progress.ProgressManager
import dev.wicketlens.model.*

/** Static queue candidates, constrained by actual Java containers and the selected markup region. */
internal object WicketQueueResolver {
    fun resolve(report: PageReport, containers: Set<String>, boundaries: Set<String>): PageReport {
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
        var visits = 0
        fun walk(markupParent: String?, parent: String, scopes: List<String>, ancestry: Set<String>) {
            ProgressManager.checkCanceled()
            if (++visits > 4096 || parent in ancestry) return
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
        if (visits > 4096) return report
        val placements = candidates.filterValues { it.size == 1 }.mapValues { it.value.single().first }
        val nodes = report.nodes.map { node ->
            val parent = placements[node.nodeId]
            if (node.queuedInNodeId == null || parent == null) node else node.copy(parentNodeId = parent,
                confidence = Confidence.CONFIRMED, diagnostics = node.diagnostics.filterNot { it.code == DiagnosticCode.UNRESOLVED_QUEUE })
        }
        return report.copy(nodes = nodes)
    }
}
