package dev.wicketlens.mcp

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.intellij.openapi.progress.ProgressManager
import dev.wicketlens.analysis.NavigationContexts
import dev.wicketlens.export.WicketContextExporter

/** Project exact existing component-context facts; this adapter has no binding rules. */
internal object WicketNavigationExport {
    fun render(value: NavigationContexts) = JsonObject().apply {
        addProperty("discoveryComplete", value.complete)
        addProperty("discoveryKind", "bounded_source_references_and_inheritors")
        add("diagnostics", JsonArray().apply { value.diagnostics.forEach(::add) })
        add("sourceFiles", JsonArray().apply { value.sourceFiles.forEach(::add) })
        if (value.lifecycleDiagnostics.isNotEmpty()) add("lifecycleDiagnostics", JsonArray().apply {
            value.lifecycleDiagnostics.forEach { diagnostic -> add(JsonObject().apply {
                addProperty("code", diagnostic.code.name)
                addProperty("message", diagnostic.message)
                diagnostic.location?.let { at -> add("location", JsonObject().apply { addProperty("file", at.file); addProperty("offset", at.offset) }) }
            }) }
        })
        add("contexts", JsonArray().apply { value.contexts.forEach { add(context(it)) } })
    }
    fun context(context: dev.wicketlens.analysis.NavigationContext): JsonObject {
        ProgressManager.checkCanceled()
        val analysis = JsonParser.parseString(WicketContextExporter().export(context.report)).asJsonObject
        val projection = WicketComponentContext(analysis)
        fun selected(id: String) = projection.select(null, id).let { selected -> JsonObject().apply {
            (listOf("componentPath", "hierarchyResolved", "component", "findings", "markupElements") +
                if (context.report.actionTemplate != null || context.report.submissionTemplate != null || context.report.lifecycle != null || context.report.treeTemplate != null || context.report.formTemplate != null || context.report.populationTemplate != null || context.report.toolbarTemplate != null) listOf("parent", "ancestors", "children") else emptyList()).forEach { add(it, selected[it]) }
        } }
        return JsonObject().apply {
            addProperty("className", context.report.qualifiedClassName)
            addProperty("moduleName", context.moduleName)
            addProperty("sourceSet", context.sourceSet)
            addProperty("relationship", context.relationship)
            analysis["populationTemplate"]?.let { add("populationTemplate", it) }
            analysis["baseLayoutTemplate"]?.let { add("baseLayoutTemplate", it) }
            analysis["lifecycle"]?.let { add("lifecycle", it) }
            analysis["submissionTemplate"]?.let { add("submissionTemplate", it) }
            analysis["actionTemplate"]?.let { add("actionTemplate", it) }
            analysis["storedActionRegistrations"]?.let { add("storedActionRegistrations", it) }
            analysis["treeTemplate"]?.let { add("treeTemplate", it) }
            analysis["formTemplate"]?.let { add("formTemplate", it) }
            analysis["toolbarTemplate"]?.let { add("toolbarTemplate", it) }
            add("javaLocation", analysis["components"].asJsonArray.single { it.asJsonObject["nodeId"].asString == context.report.rootNodeId }.asJsonObject["javaLocation"])
            addProperty("markupPath", context.report.markupPath)
            addProperty("markupSuffix", context.report.markupSuffix)
            addProperty("scopeComplete", context.report.scopeComplete)
            add("warnings", analysis["warnings"])
            add("scopes", JsonArray().apply { context.scopeNodeIds.forEach { add(selected(it)) } })
            add("bindings", JsonArray().apply { context.bindingNodeIds.forEach { add(selected(it)) } })
            analysis["fragmentUses"]?.let { add("fragmentUses", it) }
        }
    }
}
