package dev.wicketlens.mcp

import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.intellij.openapi.progress.ProgressManager
import java.security.MessageDigest

/** Value-only retrieval of existing navigation contexts; never an alternate binding analyzer. */
internal object WicketNavigationResponse {
    private val gson = GsonBuilder().serializeNulls().disableHtmlEscaping().create()
    private val selector = Regex("nav:([0-9a-f]{64}):(0|[1-9][0-9]{0,5})")

    fun validate(id: String?) {
        if (id != null && !selector.matches(id)) fail("INVALID_REQUEST", "contextId must be an opaque nav ID from this tool's current index; omit it to list contexts.")
    }

    fun render(snapshot: PageAnalysisSnapshot, id: String?): JsonObject {
        validate(id)
        ProgressManager.checkCanceled()
        val value = snapshot.navigationContexts ?: fail("ANALYSIS_UNAVAILABLE", "Navigation contexts were not captured. Invoke the navigation-context tool again.")
        val scope = snapshot.navigationProjectScope ?: fail("ANALYSIS_UNAVAILABLE", "The navigation project scope was not captured. Invoke the navigation-context tool again.")
        val identity = listOf(scope, snapshot.moduleName, snapshot.sourceSet, snapshot.report.qualifiedClassName,
            snapshot.report.rootNodeId, snapshot.version, value.contexts.map {
                ProgressManager.checkCanceled()
                listOf(it.moduleName, it.sourceSet, it.relationship, it.report.qualifiedClassName, it.report.rootNodeId, it.scopeNodeIds, it.bindingNodeIds)
            })
        val token = MessageDigest.getInstance("SHA-256").digest(gson.toJson(identity).toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        val selected = id?.let {
            val match = selector.matchEntire(it)!!
            if (match.groupValues[1] != token) fail("STALE_CONTEXT_SELECTION", "This contextId belongs to another target or an older snapshot. Omit contextId to refresh the index.")
            match.groupValues[2].toInt().also { index ->
                if (index !in value.contexts.indices) fail("CONTEXT_NOT_FOUND", "No context has this ID in the current discovery. Refresh the index.")
            }
        }
        // Reuse discovery metadata without serializing any context graphs for the index.
        return WicketNavigationExport.render(value.copy(contexts = emptyList())).apply {
            addProperty("mode", if (selected == null) "index" else "context")
            addProperty("snapshotId", token)
            addProperty("contextCount", value.contexts.size)
            add("contexts", JsonArray().apply {
                val indices = selected?.let(::listOf) ?: value.contexts.indices.toList()
                indices.forEach { index ->
                    ProgressManager.checkCanceled()
                    val context = value.contexts[index]
                    val item = if (selected != null) {
                        if (context.report.nodes.size > 5_000 || context.report.markup?.elements?.size?.let { it > 10_000 } == true)
                            fail("RESPONSE_TOO_LARGE", "The selected navigation context exceeds the component/markup limits. No context was truncated.")
                        WicketNavigationExport.context(context)
                    } else JsonObject().apply {
                        addProperty("className", context.report.qualifiedClassName)
                        addProperty("moduleName", context.moduleName)
                        addProperty("sourceSet", context.sourceSet)
                        addProperty("relationship", context.relationship)
                        val root = context.report.nodes.single { it.nodeId == context.report.rootNodeId }
                        add("javaLocation", gson.toJsonTree(root.javaLocation))
                        addProperty("markupPath", context.report.markupPath)
                        addProperty("markupSuffix", context.report.markupSuffix)
                        addProperty("scopeComplete", context.report.scopeComplete)
                        addProperty("scopeCount", context.scopeNodeIds.size)
                        addProperty("bindingCount", context.bindingNodeIds.size)
                    }
                    item.addProperty("contextId", "nav:$token:$index")
                    add(item)
                }
            })
        }
    }

    private fun fail(code: String, message: String): Nothing = throw PageAnalysisFailure(code, message)
}
