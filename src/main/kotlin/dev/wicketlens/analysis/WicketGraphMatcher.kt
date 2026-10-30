package dev.wicketlens.analysis

import com.intellij.openapi.progress.ProgressManager
import dev.wicketlens.model.ComponentNode
import dev.wicketlens.model.Confidence
import dev.wicketlens.model.Diagnostic
import dev.wicketlens.model.DiagnosticCode
import dev.wicketlens.model.MarkupElement
import dev.wicketlens.model.MatchFinding
import dev.wicketlens.model.MatchReport
import dev.wicketlens.model.PageReport

/** Matches immutable snapshots without PSI access, mutation, or persistent state. */
class WicketGraphMatcher {
    fun match(report: PageReport): PageReport {
        ProgressManager.checkCanceled()
        val java = report.nodes.filter { it.nodeId != report.rootNodeId && !it.templateItem }
        val markup = report.markup
        val markupComponents = markup?.elements.orEmpty()
            .filter { it.participatesInComponentTree && it.hasWicketId }
        val findings = mutableListOf<MatchFinding>()
        val javaScopeIssues = report.warnings.filter { it.code == DiagnosticCode.UNRESOLVED_SCOPE }
        val javaById = report.nodes.groupBy { it.nodeId }.filterValues { it.size == 1 }.mapValues { it.value.single() }
        fun javaCauses(node: ComponentNode): List<Diagnostic> {
            val causes = mutableListOf<Diagnostic>()
            val seen = mutableSetOf<String>()
            var current: ComponentNode? = node
            while (current != null && seen.add(current.nodeId)) {
                ProgressManager.checkCanceled()
                causes += current.diagnostics
                current = javaById[current.parentNodeId]
            }
            return (causes + javaScopeIssues).distinct()
        }
        if (markup == null || report.markupPath != markup.file) {
            val pairingIssues = report.warnings.filter { it.code == DiagnosticCode.UNRESOLVED_MARKUP }
            java.forEach { node ->
                ProgressManager.checkCanceled()
                findings += javaFinding(node,
                    if (node.confidence == Confidence.UNRESOLVED) javaUncertainty(node) else DiagnosticCode.UNRESOLVED_MARKUP,
                    "No uniquely paired, extracted markup is available for matching.", javaCauses(node) + pairingIssues)
            }
            markupComponents.forEach { element ->
                ProgressManager.checkCanceled()
                findings += markupFinding(element, DiagnosticCode.UNRESOLVED_MARKUP,
                    "The markup snapshot does not belong to the selected companion file.", element.diagnostics)
            }
            return finish(report, findings, false)
        }

        val root = report.nodes.filter { it.nodeId == report.rootNodeId }.singleOrNull()
        val validRoot = root != null && root.parentNodeId == null && root.confidence == Confidence.CONFIRMED
        val javaPaths = if (validRoot) paths(report.nodes.map {
            PathNode(it.nodeId, it.wicketId, it.parentNodeId, it.confidence, it.templateItem)
        }, report.rootNodeId) else emptyMap()
        val markupPaths = paths(markupComponents.map {
            PathNode(it.nodeId, it.wicketId, it.parentWicketElementId, it.confidence)
        }, null)
        val markupByPath = markupPaths.entries.associate { (id, path) -> path to id }
        val markupById = markupComponents.associateBy { it.nodeId }
        val pairedMarkup = mutableSetOf<String>()
        val unknownJavaScopes = java.filter { it.nodeId !in javaPaths }.map { node ->
            val scope = if (node.parentNodeId == report.rootNodeId) emptyList() else javaPaths[node.parentNodeId]
            ScopeUncertainty(scope, javaCauses(node))
        } + report.nodes.filter { node -> node.diagnostics.any { it.code == DiagnosticCode.UNRESOLVED_SCOPE } }
            .map { ScopeUncertainty(javaPaths[it.nodeId], it.diagnostics) }
        val javaUncertain = !validRoot || javaScopeIssues.isNotEmpty() || unknownJavaScopes.isNotEmpty()
        val markupUncertain = !markup.scopeComplete || markupPaths.size != markupComponents.size
        val allMarkupById = markup.elements.associateBy { it.nodeId }
        fun markupCauses(element: MarkupElement): List<Diagnostic> {
            val causes = markup.documentDiagnostics.toMutableList()
            val seen = mutableSetOf<String>()
            var current: MarkupElement? = element
            while (current != null && seen.add(current.nodeId)) {
                ProgressManager.checkCanceled()
                causes += current.diagnostics
                current = allMarkupById[current.parentElementId]
            }
            return causes.distinct()
        }

        java.forEach { node ->
            ProgressManager.checkCanceled()
            val path = javaPaths[node.nodeId]
            val markupId = path?.let { markupByPath[it] }
            val element = markupId?.let { markupById[it] }
            val causes = javaCauses(node).filter { it.code == DiagnosticCode.UNRESOLVED_SCOPE }
            findings += when {
                path == null -> javaFinding(node,
                    javaUncertainty(node),
                    "A unique literal component path under the Java root is not established.", javaCauses(node))
                element != null -> {
                    pairedMarkup += element.nodeId
                    MatchFinding(
                        code = DiagnosticCode.MATCHED,
                        message = if (node.template) "Item-template binding matches: ${path.joinToString(":")}. Runtime item IDs and row counts are not inferred."
                            else "Static Java and markup component paths match: ${path.joinToString(":")}.",
                        confidence = if (node.template) Confidence.INFERRED else Confidence.CONFIRMED,
                        javaNodeId = node.nodeId,
                        markupNodeId = element.nodeId,
                        javaLocation = node.javaLocation,
                        markupLocation = element.location,
                        causes = causes,
                    )
                }
                causes.isNotEmpty() -> javaFinding(node, DiagnosticCode.UNRESOLVED_SCOPE,
                    "Unsupported Java setup prevents establishing an unmatched component path.", causes)
                markupUncertain -> javaFinding(node, DiagnosticCode.UNRESOLVED_MARKUP,
                    "Markup uncertainty prevents establishing whether ${path.joinToString(":")} has a counterpart.", markup.diagnostics)
                else -> javaFinding(node, DiagnosticCode.JAVA_ONLY,
                    "No markup counterpart was observed at ${path.joinToString(":")}. This is an informational static finding.")
            }
        }

        markupComponents.filter { it.nodeId !in pairedMarkup }.forEach { element ->
            ProgressManager.checkCanceled()
            val path = markupPaths[element.nodeId]
            val blockers = unknownJavaScopes.filter { scope ->
                scope.path == null || (path != null && path.take(scope.path.size) == scope.path)
            }
            findings += when {
                path == null -> markupFinding(element, DiagnosticCode.UNRESOLVED_MARKUP,
                    "A unique supported markup component path is not established.", markupCauses(element))
                !validRoot || javaScopeIssues.isNotEmpty() || blockers.isNotEmpty() -> markupFinding(element, DiagnosticCode.UNRESOLVED_PARENT,
                    "Java hierarchy or ID uncertainty prevents establishing a counterpart for ${path.joinToString(":")}.",
                    (javaScopeIssues + blockers.flatMap { it.causes }).distinct())
                else -> markupFinding(element, DiagnosticCode.MARKUP_ONLY,
                    "No Java counterpart was statically discovered at ${path.joinToString(":")}. Java analysis may be incomplete.")
            }
        }
        return finish(report, findings, report.scopeComplete && markup.scopeComplete && !javaUncertain && !markupUncertain)
    }

    /**
     * Paths use literal segments rather than delimiter-joined strings. Reject
     * missing/cyclic parents and duplicate paths, including all their descendants.
     */
    private fun paths(nodes: List<PathNode>, rootId: String?): Map<String, List<String>> {
        val unique = nodes.groupBy { it.nodeId }.filterValues { it.size == 1 }.mapValues { it.value.single() }
        val raw = linkedMapOf<String, List<String>>()
        nodes.filter { it.nodeId != rootId }.forEach { start ->
            ProgressManager.checkCanceled()
            val seen = mutableSetOf<String>()
            val segments = mutableListOf<String>()
            var current: String? = start.nodeId
            while (current != rootId) {
                ProgressManager.checkCanceled()
                if (current == null || !seen.add(current)) break
                val node = unique[current] ?: break
                if (node.confidence != Confidence.CONFIRMED || !node.transparent && node.wicketId.isNullOrEmpty()) break
                if (!node.transparent) segments += node.wicketId!!
                current = node.parentId
            }
            if (current == rootId) raw[start.nodeId] = segments.reversed()
        }
        val ambiguous = raw.filterKeys { unique[it]?.transparent != true }.values.groupingBy { it }.eachCount().filterValues { it > 1 }.keys
        return raw.filterValues { path ->
            ProgressManager.checkCanceled()
            (1..path.size).none { path.take(it) in ambiguous }
        }
    }

    private fun javaUncertainty(node: ComponentNode): DiagnosticCode = node.diagnostics.firstOrNull {
        it.code in setOf(DiagnosticCode.UNRESOLVED_DYNAMIC_ID, DiagnosticCode.UNRESOLVED_ID,
            DiagnosticCode.UNRESOLVED_CONSTRUCTOR, DiagnosticCode.UNRESOLVED_VARIABLE, DiagnosticCode.UNRESOLVED_QUEUE)
    }?.code ?: if (node.diagnostics.any { it.code == DiagnosticCode.UNRESOLVED_SCOPE }) DiagnosticCode.UNRESOLVED_SCOPE
        else if (node.wicketId.isNullOrEmpty()) DiagnosticCode.UNRESOLVED_DYNAMIC_ID else DiagnosticCode.UNRESOLVED_PARENT

    private fun javaFinding(node: ComponentNode, code: DiagnosticCode, message: String, causes: List<Diagnostic> = emptyList()) = MatchFinding(
        code = code, message = message,
        confidence = if (code == DiagnosticCode.JAVA_ONLY) Confidence.INFERRED else Confidence.UNRESOLVED,
        javaNodeId = node.nodeId, javaLocation = node.javaLocation,
        causes = causes.distinct(),
    )

    private fun markupFinding(element: MarkupElement, code: DiagnosticCode, message: String, causes: List<Diagnostic> = emptyList()) = MatchFinding(
        code = code, message = message,
        confidence = if (code == DiagnosticCode.MARKUP_ONLY) Confidence.INFERRED else Confidence.UNRESOLVED,
        markupNodeId = element.nodeId, markupLocation = element.location,
        causes = causes.distinct(),
    )

    private fun finish(report: PageReport, findings: List<MatchFinding>, complete: Boolean): PageReport {
        val locations = findings.filter { it.code == DiagnosticCode.MATCHED }
            .associate { it.javaNodeId to it.markupLocation }
        return report.copy(
            nodes = report.nodes.map {
                ProgressManager.checkCanceled()
                it.copy(markupLocation = locations[it.nodeId] ?: if (it.templateItem) locations[it.parentNodeId] else null)
            },
            matching = MatchReport(findings.toList(), complete),
        )
    }

    private data class PathNode(
        val nodeId: String,
        val wicketId: String?,
        val parentId: String?,
        val confidence: Confidence,
        val transparent: Boolean = false,
    )

    private data class ScopeUncertainty(val path: List<String>?, val causes: List<Diagnostic>)
}
