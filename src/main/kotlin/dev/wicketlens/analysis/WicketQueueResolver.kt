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
            resolvePath(report.copy(nodes = nodes, branchPathCount = 1), containers, boundaries, budget)
        }
        if (budget.exhausted) return report
        val pathNodes = byPath.map { path -> path.nodes.associateBy { it.nodeId } }
        val placed = report.copy(nodes = report.nodes.map { node ->
            ProgressManager.checkCanceled()
            if (node.queuedInNodeId == null) return@map node
            val alternatives = pathNodes.mapNotNull { it[node.nodeId] }
            val parents = alternatives.map { it.parentNodeId }.distinct()
            if (alternatives.isEmpty() || alternatives.any { candidate ->
                    candidate.confidence != Confidence.CONFIRMED || candidate.diagnostics.any { it.code == DiagnosticCode.UNRESOLVED_QUEUE }
                } || parents.size != 1 || parents.single() == null) node
            else node.copy(parentNodeId = parents.single(),
                confidence = if (node.conditional) Confidence.UNRESOLVED else Confidence.CONFIRMED,
                diagnostics = node.diagnostics.filterNot { it.code == DiagnosticCode.UNRESOLVED_QUEUE })
        })
        return mergeEquivalentPlacements(placed, byPath, pathNodes)
    }

    private fun mergeEquivalentPlacements(
        report: PageReport, paths: List<PageReport>, pathNodes: List<Map<String, ComponentNode>>,
    ): PageReport {
        val markup = report.markup?.takeIf { it.scopeComplete } ?: return report
        val candidateIds = report.nodes.filter { it.conditional && !it.template }.map { it.nodeId }.toSet()
        if (report.nodes.none { it.nodeId in candidateIds && it.queuedInNodeId != null }) return report
        val observations = paths.map { path ->
            WicketGraphMatcher().match(path).matching!!.findings.filter {
                it.code == DiagnosticCode.MATCHED && it.confidence == Confidence.CONFIRMED && it.causes.isEmpty() &&
                    it.javaNodeId in candidateIds && it.markupNodeId != null
            }.associateBy { it.markupNodeId }
        }
        val byMarkup = markup.elements.associateBy { it.nodeId }
        val byId = report.nodes.associateBy { it.nodeId }
        fun depth(markupId: String): Int {
            val seen = mutableSetOf<String>()
            var id: String? = markupId
            while (id != null && seen.add(id)) {
                ProgressManager.checkCanceled()
                id = byMarkup[id]?.parentWicketElementId
            }
            return seen.size
        }
        val scopeCandidates = observations.first().keys.filterNotNull().map { it to depth(it) }.sortedBy { it.second }
        val merged = mutableMapOf<String, ComponentNode>()
        val replacements = mutableMapOf<String, String>()
        for ((markupId) in scopeCandidates) {
            ProgressManager.checkCanceled()
            val alternatives = observations.mapIndexedNotNull { index, matches -> pathNodes[index][matches[markupId]?.javaNodeId] }
            if (alternatives.size != paths.size || alternatives.map { it.componentType }.distinct().size != 1 ||
                alternatives.map { it.wicketId }.distinct().size != 1 ||
                alternatives.none { it.queuedInNodeId != null }) continue
            val ids = alternatives.map { it.nodeId }.distinct()
            if (ids.size < 2 || ids.any { it in replacements }) continue
            // A provisional node that resolves elsewhere or fails to resolve on another
            // path cannot be consumed by this binding.
            if (pathNodes.indices.any { index ->
                ids.filter { it in pathNodes[index] } != listOf(alternatives[index].nodeId)
            }) continue
            val parents = alternatives.map { it.parentNodeId?.let { id -> replacements[id] ?: id } }.distinct()
            val parent = parents.singleOrNull() ?: continue
            if (parent != report.rootNodeId && byId[parent]?.conditional != false && parent !in merged) continue
            val first = alternatives.first()
            val scopes = alternatives.map { it.queuedInNodeId?.let { id -> replacements[id] ?: id } }.distinct()
            val javaLocations = alternatives.flatMap { listOf(it.javaLocation) + it.alternativeJavaLocations }.distinct()
            val idLocations = alternatives.flatMap(ComponentNode::idLocations).distinct()
            merged[first.nodeId] = first.copy(parentNodeId = parent, queuedInNodeId = scopes.singleOrNull(),
                conditional = false, confidence = Confidence.CONFIRMED, equivalentAttachments = true,
                branchPathMask = (1 shl paths.size) - 1,
                attachments = alternatives.flatMap { it.attachments }.distinct(),
                alternativeJavaLocations = javaLocations - first.javaLocation,
                alternativeIdLocations = idLocations.filterNot { it == first.idLocation },
                computedId = alternatives.any { it.computedId },
                computedIdLocations = alternatives.flatMap { node -> node.idLocations().filter(node::isComputedIdOrigin) }.distinct(),
                idUsedInComputation = alternatives.any { it.idUsedInComputation })
            ids.forEach { replacements[it] = first.nodeId }
        }
        if (merged.isEmpty()) return report
        fun replace(id: String?) = replacements[id] ?: id
        return report.copy(nodes = report.nodes.mapNotNull { original ->
            if (replace(original.nodeId) != original.nodeId) return@mapNotNull null
            val node = merged[original.nodeId] ?: original
            node.copy(parentNodeId = replace(node.parentNodeId), queuedInNodeId = replace(node.queuedInNodeId),
                attachments = node.attachments.map { it.copy(receiverNodeId = replacements[it.receiverNodeId] ?: it.receiverNodeId) }.distinct())
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
