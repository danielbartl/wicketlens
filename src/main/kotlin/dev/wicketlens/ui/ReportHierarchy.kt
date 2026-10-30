package dev.wicketlens.ui

import com.intellij.openapi.progress.ProgressManager
import dev.wicketlens.model.ComponentNode
import dev.wicketlens.model.Diagnostic
import dev.wicketlens.model.DiagnosticCode
import dev.wicketlens.model.PageReport
import dev.wicketlens.model.SourceLocation

/** Flat immutable tree description prepared in the background; contains no Swing or PSI objects. */
enum class ReportEntryKind { PAGE, CONTAINER, COMPONENT, MARKUP, GROUP, DIAGNOSTIC }

data class ReportTreeEntry(
    val id: String,
    val parentId: String?,
    val label: String,
    val details: String,
    val javaLocation: SourceLocation? = null,
    val markupLocation: SourceLocation? = null,
    val kind: ReportEntryKind = ReportEntryKind.GROUP,
    val name: String = label,
    val typeName: String? = null,
    val status: DiagnosticCode? = null,
    val qualifiedTypeName: String? = typeName,
    val contextOnly: Boolean = false,
    // State identity uses raw IDs; escaped display names are not unique addresses.
    val stateName: String? = name,
    val templateItem: Boolean = false,
) {
    override fun toString() = label
}

class ReportHierarchyBuilder {
    fun build(report: PageReport): List<ReportTreeEntry> {
        ProgressManager.checkCanceled()
        val entries = mutableListOf<ReportTreeEntry>()
        val findings = report.matching?.findings.orEmpty()
        val javaFindings = findings.filter { it.javaNodeId != null }.associateBy { it.javaNodeId }
        val nodes = report.nodes.associateBy { it.nodeId }
        val root = nodes[report.rootNodeId]
        val summary = findings.groupingBy { it.code }.eachCount().entries.sortedBy { it.key.name }
            .joinToString { "${it.value} ${it.key}" }.ifEmpty { "No component findings" }
        entries += ReportTreeEntry("report", null, clean(report.qualifiedClassName.substringAfterLast('.')),
            "${report.qualifiedClassName}\nScope: ${if (report.scopeComplete) "complete" else "partial"} static snapshot\n$summary\n" +
                "Markup sources:\n${report.markup?.sourceFiles?.joinToString("\n") ?: "unresolved"}", root?.javaLocation,
            markupLocation = report.companionLocation(),
            kind = ReportEntryKind.PAGE)
        val children = report.nodes.filter { it.nodeId != report.rootNodeId }.groupBy { it.parentNodeId }
        val seen = mutableSetOf(report.rootNodeId)
        fun append(start: List<ComponentNode>, parent: String) {
            val pending = ArrayDeque<Pair<ComponentNode, String>>()
            start.asReversed().forEach { pending.addLast(it to parent) }
            while (pending.isNotEmpty()) {
                ProgressManager.checkCanceled()
                val (node, parentId) = pending.removeLast()
                if (!seen.add(node.nodeId)) continue
                val finding = javaFindings[node.nodeId]
                val status = if (node.templateItem) "TEMPLATE" else finding?.code?.name ?: "UNRESOLVED"
                val name = if (node.templateItem) "item [template]" else node.wicketId ?: "<unresolved ID>"
                val id = "component:${node.nodeId}"
                val details = buildString {
                    appendLine("ID: ${node.wicketId ?: "unresolved"}")
                    appendLine("Type: ${node.componentType}")
                    appendLine("Status: $status")
                    appendLine("Confidence: ${node.confidence}")
                    if (node.template) appendLine("Static item template; runtime item IDs and row counts are not inferred.")
                    node.queuedInNodeId?.let { appendLine("Queued in: ${nodes[it]?.wicketId ?: nodes[it]?.componentType}") }
                    appendLine("Parent: ${nodes[node.parentNodeId]?.let { it.wicketId ?: it.componentType } ?: "unresolved"}")
                    appendLine("Java: ${source(node.javaLocation)}")
                    appendLine("Markup: ${node.markupLocation?.let(::source) ?: "unresolved"}")
                    finding?.let { appendLine(it.message) }
                    (node.diagnostics + finding?.causes.orEmpty()).distinct().forEach { appendLine(diagnostic(it)) }
                }
                entries += ReportTreeEntry(id, parentId,
                    "${clean(name)} : ${clean(node.componentType.substringAfterLast('.'))}  [$status]",
                    details, node.javaLocation, node.markupLocation,
                    kind = if (node.templateItem || !children[node.nodeId].isNullOrEmpty()) ReportEntryKind.CONTAINER else ReportEntryKind.COMPONENT,
                    name = clean(name), typeName = clean(node.componentType.substringAfterLast('.')),
                    status = if (node.templateItem) null else finding?.code ?: DiagnosticCode.UNRESOLVED_SCOPE,
                    qualifiedTypeName = node.componentType,
                    stateName = if (node.templateItem) "item" else node.wicketId, templateItem = node.templateItem)
                children[node.nodeId].orEmpty().asReversed().forEach { pending.addLast(it to id) }
            }
        }
        append(children[report.rootNodeId].orEmpty(), "report")
        val remaining = report.nodes.filter { it.nodeId !in seen }
        if (remaining.isNotEmpty()) {
            entries += ReportTreeEntry("unattached", "report", "Unattached or unresolved hierarchy", "Root attachment has not been established.")
            val ids = remaining.map { it.nodeId }.toSet()
            append(remaining.filter { it.parentNodeId !in ids }, "unattached")
            remaining.filter { it.nodeId !in seen }.forEach { append(listOf(it), "unattached") }
        }
        val markupFindings = findings.filter { it.javaNodeId == null && it.markupNodeId != null }
        if (markupFindings.isNotEmpty()) {
            val elements = report.markup?.elements.orEmpty().associateBy { it.nodeId }
            val ids = markupFindings.map { it.markupNodeId }.toSet()
            val pairedParents = findings.filter { it.code == DiagnosticCode.MATCHED && it.javaNodeId != null && it.markupNodeId != null }
                .associate { it.markupNodeId to "component:${it.javaNodeId}" }
            val markupEntries = mutableListOf<ReportTreeEntry>()
            markupFindings.forEach { finding ->
                ProgressManager.checkCanceled()
                val element = elements[finding.markupNodeId]
                val parent = element?.parentWicketElementId
                val parentId = if (parent in ids) "markup:$parent" else pairedParents[parent] ?: "markup"
                markupEntries += ReportTreeEntry("markup:${finding.markupNodeId}", parentId,
                    "${clean(element?.wicketId ?: "<unresolved ID>")} : <${clean(element?.tagName ?: "unknown")}>  [${finding.code}]",
                    "${finding.message}\nMarkup: ${finding.markupLocation?.let(::source) ?: "unresolved"}\n" +
                        finding.causes.joinToString("\n", transform = ::diagnostic), markupLocation = finding.markupLocation,
                    kind = ReportEntryKind.MARKUP, name = clean(element?.wicketId ?: "<unresolved ID>"),
                    typeName = "<${clean(element?.tagName ?: "unknown")}>", status = finding.code, stateName = element?.wicketId)
            }
            if (markupEntries.any { it.parentId == "markup" }) {
                entries += ReportTreeEntry("markup", "report", "Markup findings", "Observed markup with no confirmed Java counterpart.")
            }
            entries += markupEntries
        }
        val diagnostics = (report.warnings + report.nodes.flatMap { it.diagnostics } +
            report.markup?.diagnostics.orEmpty() + findings.flatMap { it.causes }).distinct()
        if (diagnostics.isNotEmpty()) {
            entries += ReportTreeEntry("diagnostics", "report", "Diagnostics (${diagnostics.size})", "Scope limitations and uncertainty causes.")
            val markupSources = report.markup?.sourceFiles.orEmpty().toSet()
            diagnostics.forEachIndexed { index, diagnostic ->
                ProgressManager.checkCanceled()
                val location = diagnostic.location
                entries += ReportTreeEntry("diagnostic:$index", "diagnostics", "[${diagnostic.code}] ${clean(diagnostic.message)}",
                    diagnostic(diagnostic), location?.takeIf { it.file !in markupSources }, location?.takeIf { it.file in markupSources },
                    kind = ReportEntryKind.DIAGNOSTIC, status = diagnostic.code, stateName = "${diagnostic.code}:${diagnostic.message}")
            }
        }
        val parents = entries.mapNotNull { it.parentId }.toSet()
        return entries.map { if (it.kind == ReportEntryKind.COMPONENT && it.id in parents) it.copy(kind = ReportEntryKind.CONTAINER) else it }
    }

    private fun clean(value: String) = value.replace("\r", "\\r").replace("\n", "\\n").replace("\t", "\\t")
    private fun source(location: SourceLocation) = "${location.file} @ ${location.offset}"
    private fun diagnostic(value: Diagnostic) = "[${value.severity}] ${value.code}: ${value.message}" +
        (value.location?.let { "\n${source(it)}" } ?: "")
}
