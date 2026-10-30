package dev.wicketlens.export

import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.intellij.openapi.progress.ProgressManager
import dev.wicketlens.model.Diagnostic
import dev.wicketlens.model.MarkupReport
import dev.wicketlens.model.MatchFinding
import dev.wicketlens.model.PageReport
import dev.wicketlens.model.SourceLocation

/** Explicit schema and ordering; no reflection, timestamps, PSI, or external requests. */
class WicketContextExporter {
    fun export(report: PageReport): String {
        ProgressManager.checkCanceled()
        val findings = report.matching?.findings.orEmpty().associateBy { it.javaNodeId }
        val json = JsonObject().apply {
            addProperty("schemaVersion", report.schemaVersion)
            addProperty("qualifiedClassName", report.qualifiedClassName)
            addProperty("rootNodeId", report.rootNodeId)
            addProperty("markupPath", report.markupPath)
            addProperty("scopeComplete", report.scopeComplete)
            add("warnings", diagnostics(report.warnings))
            add("components", array(report.nodes.sortedWith(compareBy({ it.javaLocation.file }, { it.javaLocation.offset }, { it.nodeId }))) { node ->
                JsonObject().apply {
                    val finding = findings[node.nodeId]
                    addProperty("nodeId", node.nodeId)
                    addProperty("wicketId", node.wicketId)
                    addProperty("componentType", node.componentType)
                    addProperty("parentNodeId", node.parentNodeId)
                    node.queuedInNodeId?.let { addProperty("queuedInNodeId", it) }
                    node.repetition?.let { addProperty("repetition", it.name) }
                    if (node.template) addProperty("template", true)
                    if (node.templateItem) addProperty("templateItem", true)
                    add("javaLocation", location(node.javaLocation))
                    add("markupLocation", location(node.markupLocation))
                    addProperty("confidence", node.confidence.name)
                    addProperty("status", finding?.code?.name)
                    addProperty("matchingConfidence", finding?.confidence?.name)
                    addProperty("markupNodeId", finding?.markupNodeId)
                    add("diagnostics", diagnostics(node.diagnostics))
                }
            })
            add("markup", report.markup?.let(::markup) ?: JsonNull.INSTANCE)
            add("matching", report.matching?.let { matching ->
                JsonObject().apply {
                    addProperty("scopeComplete", matching.scopeComplete)
                    add("findings", array(matching.findings.sortedWith(compareBy(
                        { it.javaNodeId == null }, { it.javaLocation?.file ?: it.markupLocation?.file.orEmpty() },
                        { it.javaLocation?.offset ?: it.markupLocation?.offset ?: -1 },
                        { it.javaNodeId.orEmpty() }, { it.markupNodeId.orEmpty() }, { it.code.name },
                    )), ::finding))
                }
            } ?: JsonNull.INSTANCE)
        }
        return GsonBuilder().serializeNulls().disableHtmlEscaping().setPrettyPrinting().create().toJson(json) + "\n"
    }

    private fun markup(report: MarkupReport) = JsonObject().apply {
        addProperty("file", report.file)
        addProperty("kind", report.kind.name)
        addProperty("scopeComplete", report.scopeComplete)
        add("diagnostics", diagnostics(report.diagnostics))
        add("documentDiagnostics", diagnostics(report.documentDiagnostics))
        add("elements", array(report.elements.sortedWith(compareBy({ it.location.file }, { it.location.offset }, { it.nodeId }))) { element ->
            JsonObject().apply {
                addProperty("nodeId", element.nodeId)
                addProperty("tagName", element.tagName)
                addProperty("parentElementId", element.parentElementId)
                addProperty("hasWicketId", element.hasWicketId)
                addProperty("wicketId", element.wicketId)
                addProperty("parentWicketElementId", element.parentWicketElementId)
                add("location", location(element.location))
                add("wicketIdLocation", location(element.wicketIdLocation))
                addProperty("participatesInComponentTree", element.participatesInComponentTree)
                addProperty("confidence", element.confidence.name)
                add("diagnostics", diagnostics(element.diagnostics))
            }
        })
    }

    private fun finding(finding: MatchFinding) = JsonObject().apply {
        addProperty("code", finding.code.name)
        addProperty("message", finding.message)
        addProperty("confidence", finding.confidence.name)
        addProperty("severity", finding.severity.name)
        addProperty("javaNodeId", finding.javaNodeId)
        addProperty("markupNodeId", finding.markupNodeId)
        add("javaLocation", location(finding.javaLocation))
        add("markupLocation", location(finding.markupLocation))
        add("causes", diagnostics(finding.causes))
    }

    private fun location(location: SourceLocation?): JsonElement = location?.let {
        JsonObject().apply {
            addProperty("file", it.file)
            addProperty("offset", it.offset)
        }
    } ?: JsonNull.INSTANCE

    private fun diagnostics(values: List<Diagnostic>) = array(values.sortedWith(compareBy(
        { it.location?.file.orEmpty() }, { it.location?.offset ?: -1 }, { it.code.name }, { it.message }, { it.severity.name },
    ))) { diagnostic ->
        JsonObject().apply {
            addProperty("code", diagnostic.code.name)
            addProperty("message", diagnostic.message)
            addProperty("severity", diagnostic.severity.name)
            add("location", location(diagnostic.location))
        }
    }

    private fun <T> array(values: Iterable<T>, convert: (T) -> JsonElement) = JsonArray().apply {
        values.forEach {
            ProgressManager.checkCanceled()
            add(convert(it))
        }
    }
}
