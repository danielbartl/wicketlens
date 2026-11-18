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
            report.initialContentSetup?.let { setup -> appendLine("Analysis: source-declared initial content setup; ${setup.hosts.size} owned hosts; all bindings inferred. Constructor/configuration effects, model values, visibility and later open/close/replacement state remain unknown; callbacks not evaluated.") }
            if (report.storedActionRegistrations.isNotEmpty()) appendLine("Analysis: initial attachment declarations with ${report.storedActionRegistrations.size} source-proved callback storage site(s); all bindings inferred. Later dispatch, callback effects, visibility and runtime tree state remain unknown; callbacks not evaluated.")
            report.lifecycle?.takeIf { it.contentFactory != null }?.let { factory ->
                appendLine("Analysis: source content-factory template at ${line(factory.componentPath)}; declared immediate invocation, factory and callbacks not evaluated, runtime presence and later state unknown.")
            }
            report.lifecycle?.takeIf { it.directContent!=null }?.let { event ->
                appendLine("Analysis: source event content template at ${line(event.componentPath)}; trigger ${line(event.directContent!!.triggerComponentPath)}; callback not evaluated, runtime presence, rows, models and later open/close state unknown.")
            }
            report.actionTemplate?.let { action ->
                appendLine("Analysis: conditional source record-action callback; registration/storage, UI dispatch, rows and mutation outcomes unknown; callback, equality and record constructor not evaluated.")
                linkedLine("Source callback registration (UI trigger not reconstructed)", action.registrationLocation, null)
                action.updates.forEach { update ->
                    linkedLine("Source record replacement loop (counts/outcomes unknown)", update.methodLocation, null)
                    linkedLine("Source record transform: ${line(update.recordClassName)}", update.transformMethodLocation, null)
                    update.componentArguments.filter { it.kind=="SOURCE_ENUM_CONSTANT" }.forEach { argument ->
                        linkedLine("Declared enum argument: ${line(argument.componentName)} -> ${line(argument.declarationName)}", argument.declarationLocation, null)
                    }
                }
                action.selectionClears.forEach { operation -> linkedLine("Source selection helper (model/clear outcomes unknown)", operation.helperLocation, null) }
                action.effects.forEach { effect -> linkedLine("Declared ${line(effect.kind)}: ${line(effect.targetComponentPath.ifEmpty { "<page>" })} (${line(effect.methodName)})", effect.location, null) }
            }
            report.submissionTemplate?.let { submission ->
                if (submission.confirmation != null) appendLine("Analysis: conditional stored confirmation consumer at ${line(submission.hostComponentPath)}; trigger ${line(submission.triggerComponentPath)}. Initiating dispatch, current registration, validation and data outcomes unknown; callback not evaluated.")
                else if (submission.formCallback != null) appendLine("Analysis: own-companion form callback declarations at ${line(submission.triggerComponentPath)}; ${line(submission.callbackName)} not evaluated. Explicit additions only; initial hierarchy, inherited placement, validation, models and business/feedback outcomes unknown.")
                else appendLine("Analysis: source submission template at ${line(submission.hostComponentPath)}; trigger ${line(submission.triggerComponentPath)}; callback ${line(submission.callbackName)} not evaluated. Validation, models, business/feedback outcomes and later state unknown.")
                submission.confirmation?.let { route ->
                    linkedLine("Source ask registration (conditional)", route.registrationLocation, null)
                    linkedLine("Source capture / clear / guarded dispatch", route.hookLocation, null)
                    route.dataCalls.forEach { call -> linkedLine("Source data helper (${line(call.mutationMethod)}; outcome unknown)", call.methodLocation, null) }
                    route.selectionClears.forEach { operation ->
                        linkedLine("Source selection helper at ${line(operation.targetComponentPath)} (model/clear outcomes unknown)", operation.helperLocation, null)
                        linkedLine("Declared model getter (runtime dispatch unknown)", operation.getterLocation, null)
                    }
                }
                submission.effects.forEach { effect -> linkedLine("Declared ${line(effect.kind)}: ${line(effect.targetComponentPath)} (${line(effect.methodName)})", effect.location, null) }
            }
            report.toolbarTemplate?.let { toolbar ->
                appendLine("Analysis: toolbar-relative source content template for ${line(toolbar.hostComponentPath)}; runtime toolbar ID, row count, visibility and colspan unknown; factory and callbacks not evaluated.")
            }
            report.populationTemplate?.let { population ->
                appendLine("Analysis: source item population template at ${line(population.hostComponentPath)}; runtime item IDs/count, model values, authorization and visibility remain unknown.")
                appendLine("Row preconditions: " + population.conditions.joinToString { "${line(it.expression)} = ${it.result}" }.ifEmpty { "source-declared item setup" })
                population.registration?.let { appendLine("Population source: registered item/data callback from ${line(it.factoryClassName)}; default items retain their identity through reviewed behavior decoration; callback and behavior effects remain unevaluated.") }
            }
            if(report.populationContexts.isNotEmpty()) appendLine("Population templates: ${report.populationContexts.size}; branch counterparts are separate symbolic item scopes.")
            report.baseLayoutTemplate?.let { layout ->
                appendLine("Analysis: base-layout declaration template; ${layout.constructors.size} constructor entries agree under declared implementations.")
                appendLine("Extension slots: ${layout.extensionSlots.size}; subclass contents, overrides and runtime constructor selection remain unknown.")
            }
            appendLine("Summary: " + findings.groupingBy { it.code }.eachCount().entries.sortedBy { it.key.name }
                .joinToString { "${it.value} ${it.key.name}" }.ifEmpty { "no component findings" })
            appendLine()
            appendLine("Components:")
            linkedLine(line((report.toolbarTemplate?.toolbarClassName ?: report.qualifiedClassName).substringAfterLast('.')),
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
                        "[${if (node.templateItem) "TEMPLATE" else finding?.let { if ((report.storedActionRegistrations.isNotEmpty() || report.actionTemplate != null || report.submissionTemplate != null || report.initialContentSetup != null || report.baseLayoutTemplate != null || report.populationTemplate != null || report.lifecycle?.contentFactory != null || report.lifecycle?.directContent != null || report.toolbarTemplate != null || node.registeredListPopulation != null) && it.code == DiagnosticCode.MATCHED) "${it.code} / ${it.confidence}" else it.code.name } ?: "UNRESOLVED"}]", node.javaLocation, node.markupLocation)
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
            val firstStop = if (findings.none { it.code == DiagnosticCode.MATCHED }) report.warnings.firstOrNull {
                it.code == DiagnosticCode.UNRESOLVED_SCOPE && it.message.startsWith("Source layout setup stopped:")
            } else null
            val diagnostics = (listOfNotNull(firstStop) + report.warnings + report.nodes.flatMap { it.diagnostics } + report.markup?.diagnostics.orEmpty() +
                findings.filter { it.code != DiagnosticCode.MATCHED }.flatMap {
                    listOf(Diagnostic(it.code, it.message, it.javaLocation ?: it.markupLocation, it.severity)) + it.causes
                }).distinct()
            diagnostics.forEach {
                ProgressManager.checkCanceled()
                appendLine("- [${it.severity}] ${it.code}: ${line(it.message)}")
                it.location?.let { location -> appendLine("  ${source(location)}") }
            }
            if (diagnostics.isEmpty()) appendLine("- None")
            report.populationContexts.forEachIndexed { index, context ->
                val population = context.populationTemplate!!
                val owned = population.ownedNodeIds.toSet()
                val focused = context.copy(rootNodeId = population.itemNodeId, nodes = context.nodes.filter { it.nodeId in owned }.map { node ->
                    if(node.nodeId==population.itemNodeId) node.copy(parentNodeId=null) else node
                }, matching = context.matching!!.copy(findings=context.matching.findings.filter { it.javaNodeId in owned }), baseLayoutTemplate=null, populationContexts=emptyList())
                appendLine();appendLine("Population template ${index+1}:")
                val rendered = formatWithLinks(focused)
                val start = length
                append(rendered.text)
                links += rendered.links.map { it.copy(start=it.start+start,end=it.end+start) }
            }
        }
        return FormattedReport(text, links.toList())
    }

    private fun source(location: SourceLocation) = "${line(location.file)} @ ${location.offset}"
    private fun line(value: String) = value.replace("\r", "\\r").replace("\n", "\\n").replace("\t", "\\t")
    private data class Row(val node: ComponentNode, val prefix: String, val last: Boolean)
}
