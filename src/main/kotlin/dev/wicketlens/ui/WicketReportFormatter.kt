package dev.wicketlens.ui

import com.intellij.openapi.progress.ProgressManager
import dev.wicketlens.model.ComponentNode
import dev.wicketlens.model.Diagnostic
import dev.wicketlens.model.DiagnosticCode
import dev.wicketlens.model.PageReport
import dev.wicketlens.model.SourceLocation

/** A plain-text report: source text is never interpreted as HTML. */
class WicketReportFormatter {
    fun format(report: PageReport): String = formatWithLinks(report).text

    fun formatWithLinks(report: PageReport): FormattedReport {
        val links = mutableListOf<ReportLink>()
        val text = buildString {
            fun linkedLine(value: String, java: SourceLocation?, markup: SourceLocation?, preferred: SourceSide = SourceSide.JAVA) {
                val start = length
                appendLine(value)
                links += ReportLink(start, length, java, markup, preferred)
            }
            ProgressManager.checkCanceled()
            val findings = report.matching?.findings.orEmpty()
            val javaFindings = findings.filter { it.javaNodeId != null }.associateBy { it.javaNodeId }
            appendLine("WicketLens — ${line(report.qualifiedClassName)}")
            appendLine("Markup: ${report.markupPath?.let(::line) ?: "unresolved"}")
            report.markup?.sourceFiles?.takeIf { it.size > 1 }?.let {
                appendLine("Markup sources: " + it.joinToString(transform = ::line))
            }
            appendLine("Scope: ${if (report.scopeComplete) "complete static snapshot" else "partial static snapshot"}; re-run after source changes.")
            appendLine("Summary: " + findings.groupingBy { it.code }.eachCount().entries.sortedBy { it.key.name }
                .joinToString { "${it.value} ${it.key.name}" }.ifEmpty { "no component findings" })
            appendLine()
            appendLine("Components:")
            linkedLine(line(report.qualifiedClassName.substringAfterLast('.')),
                report.nodes.singleOrNull { it.nodeId == report.rootNodeId }?.javaLocation, report.companionLocation())

            val children = report.nodes.filter { it.nodeId != report.rootNodeId }.groupBy { it.parentNodeId }
            val seen = mutableSetOf(report.rootNodeId)
            fun render(nodes: List<ComponentNode>) {
                val stack = ArrayDeque<Row>()
                nodes.asReversed().forEachIndexed { index, node -> stack.addLast(Row(node, "", index == 0)) }
                while (stack.isNotEmpty()) {
                    ProgressManager.checkCanceled()
                    val (node, prefix, last) = stack.removeLast()
                    if (!seen.add(node.nodeId)) continue
                    val finding = javaFindings[node.nodeId]
                    linkedLine(prefix + (if (last) "└── " else "├── ") +
                        "${if (node.templateItem) "item [template]" else node.wicketId?.let(::line) ?: "<unresolved ID>"} : ${line(node.componentType.substringAfterLast('.'))}  " +
                        "[${if (node.templateItem) "TEMPLATE" else finding?.code?.name ?: "UNRESOLVED"}]", node.javaLocation, node.markupLocation)
                    val continuation = prefix + if (last) "    " else "│   "
                    linkedLine("${continuation}Java: ${source(node.javaLocation)}", node.javaLocation, node.markupLocation)
                    node.markupLocation?.let {
                        linkedLine("${continuation}Markup: ${source(it)}", node.javaLocation, it, SourceSide.MARKUP)
                    }
                    children[node.nodeId].orEmpty().asReversed().forEachIndexed { index, child ->
                        stack.addLast(Row(child, continuation, index == 0))
                    }
                }
            }
            render(children[report.rootNodeId].orEmpty())
            report.nodes.filter { it.nodeId !in seen }.forEach { node ->
                if (node.nodeId !in seen) {
                    appendLine()
                    appendLine("Unattached or unresolved hierarchy:")
                    render(listOf(node))
                }
            }

            val markupFindings = findings.filter { it.javaNodeId == null && it.markupNodeId != null }
            if (markupFindings.isNotEmpty()) {
                appendLine()
                appendLine("Markup findings:")
                val elements = report.markup?.elements.orEmpty().associateBy { it.nodeId }
                markupFindings.forEach { finding ->
                    ProgressManager.checkCanceled()
                    val element = elements[finding.markupNodeId]
                    linkedLine("- ${element?.wicketId?.let(::line) ?: "<unresolved ID>"} : <${line(element?.tagName ?: "unknown")}>  [${finding.code}]",
                        null, finding.markupLocation, SourceSide.MARKUP)
                    finding.markupLocation?.let { linkedLine("  Markup: ${source(it)}", null, it, SourceSide.MARKUP) }
                }
            }
            appendLine()
            appendLine("Diagnostics:")
            val diagnostics = (report.warnings + report.nodes.flatMap { it.diagnostics } + report.markup?.diagnostics.orEmpty() +
                findings.filter { it.code != DiagnosticCode.MATCHED }.flatMap {
                    listOf(Diagnostic(it.code, it.message, it.javaLocation ?: it.markupLocation, it.severity)) + it.causes
                }).distinct()
            diagnostics.forEach {
                ProgressManager.checkCanceled()
                appendLine("- [${it.severity}] ${it.code}: ${line(it.message)}")
                it.location?.let { location -> appendLine("  ${source(location)}") }
            }
            if (diagnostics.isEmpty()) appendLine("- None")
        }
        return FormattedReport(text, links.toList())
    }

    private fun source(location: SourceLocation) = "${line(location.file)} @ ${location.offset}"
    private fun line(value: String) = value.replace("\r", "\\r").replace("\n", "\\n").replace("\t", "\\t")
    private data class Row(val node: ComponentNode, val prefix: String, val last: Boolean)
}
