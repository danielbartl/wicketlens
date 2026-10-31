package dev.wicketlens.mcp

import com.google.gson.JsonArray
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.intellij.openapi.progress.ProgressManager

/** A selection/projection of the existing export, never a second binding or model analyzer. */
internal class WicketComponentContext(private val analysis: JsonObject) {
    private val nodes = analysis.getAsJsonArray("components").map { it.asJsonObject }
    private val byId = nodes.associateBy { it.text("nodeId")!! }
    private val children = nodes.groupBy { it.text("parentNodeId") }
    private val root = analysis["rootNodeId"].asString

    fun select(componentPath: String?, nodeId: String?): JsonObject {
        val selected = if (nodeId != null) byId[nodeId]
            ?: fail("COMPONENT_NOT_FOUND", "No component has this nodeId in the current snapshot. Fetch the page structure again.")
        else resolve(componentPath!!)
        val id = selected.text("nodeId")!!
        val ancestry = ancestors(selected)
        val matching = analysis["matching"].takeUnless { it.isJsonNull }?.asJsonObject
        val markup = analysis["markup"].takeUnless { it.isJsonNull }?.asJsonObject
        val findings = (matching?.getAsJsonArray("findings") ?: JsonArray()).filter { it.asJsonObject.text("javaNodeId") == id }
        val markupIds = findings.mapNotNull { it.asJsonObject.text("markupNodeId") }.toSet()
        return JsonObject().apply {
            add("analysisSchemaVersion", analysis["schemaVersion"])
            addProperty("componentPath", path(selected, ancestry))
            add("component", selected)
            add("parent", byId[selected.text("parentNodeId")] ?: JsonNull.INSTANCE)
            add("ancestors", array(ancestry))
            add("children", array(children[id].orEmpty()))
            addProperty("hierarchyResolved", ancestry.firstOrNull()?.text("nodeId") == root || id == root)
            add("findings", array(findings))
            // Only identities already associated by the matcher; equal ID strings are not a binding.
            add("markupElements", array((markup?.getAsJsonArray("elements") ?: JsonArray()).filter { it.asJsonObject.text("nodeId") in markupIds }))
            addProperty("matchingAvailable", matching != null)
            addProperty("markupAvailable", markup != null)
            add("markupPath", analysis["markupPath"])
            add("scopeComplete", JsonObject().apply {
                add("java", analysis["scopeComplete"])
                add("markup", markup?.get("scopeComplete") ?: JsonNull.INSTANCE)
                add("matching", matching?.get("scopeComplete") ?: JsonNull.INSTANCE)
            })
            add("warnings", analysis["warnings"])
            add("markupDiagnostics", markup?.get("diagnostics") ?: JsonArray())
            add("documentDiagnostics", markup?.get("documentDiagnostics") ?: JsonArray())
        }
    }

    private fun resolve(path: String): JsonObject {
        var current = byId[root] ?: fail("COMPONENT_NOT_FOUND", "The current graph has no root component.")
        if (path.isEmpty()) return current
        for (segment in path.split(':')) {
            ProgressManager.checkCanceled()
            val level = logicalChildren(current.text("nodeId")!!)
            val matches = level.filter { it.text("wicketId") == segment }
            if (matches.size > 1) fail("AMBIGUOUS_COMPONENT", "Multiple observed components share a path segment. Select a nodeId from wicket_get_page_structure.")
            if (level.any { it.text("wicketId").isNullOrEmpty() })
                fail("UNRESOLVED_COMPONENT_PATH", "A sibling has an unknown ID, so this path cannot select a unique component. Select a nodeId from wicket_get_page_structure.")
            current = matches.singleOrNull()
                ?: fail("COMPONENT_NOT_FOUND", "No observed component matches this path in the current graph. Use wicket_get_page_structure to inspect unresolved or markup-only entries.")
        }
        return current
    }

    /** Item templates consume no path segment; runtime item IDs are never invented. */
    private fun logicalChildren(id: String): List<JsonObject> {
        val result = mutableListOf<JsonObject>()
        val pending = ArrayDeque(children[id].orEmpty())
        val visited = mutableSetOf(id)
        while (pending.isNotEmpty()) {
            ProgressManager.checkCanceled()
            val node = pending.removeFirst()
            if (!visited.add(node.text("nodeId")!!)) continue
            if (node.isItem()) pending.addAll(children[node.text("nodeId")].orEmpty()) else result.add(node)
        }
        return result
    }

    private fun ancestors(node: JsonObject): List<JsonObject> {
        val result = mutableListOf<JsonObject>()
        val visited = mutableSetOf(node.text("nodeId"))
        var parent = node.text("parentNodeId")
        while (parent != null && visited.add(parent)) {
            ProgressManager.checkCanceled()
            val ancestor = byId[parent] ?: break
            result.add(ancestor)
            parent = ancestor.text("parentNodeId")
        }
        return result.asReversed()
    }

    private fun path(node: JsonObject, ancestors: List<JsonObject>): String? {
        if (node.text("nodeId") == root) return ""
        if (node.isItem() || ancestors.firstOrNull()?.text("nodeId") != root) return null
        val segments = (ancestors.drop(1) + node).filterNot { it.isItem() }.map {
            val id = it.text("wicketId") ?: return null
            if (id.isEmpty() || ':' in id) return null
            id
        }
        val path = segments.joinToString(":")
        return try { path.takeIf { resolve(it).text("nodeId") == node.text("nodeId") } }
        catch (_: PageAnalysisFailure) { null }
    }

    companion object {
        fun validate(componentPath: String?, nodeId: String?) {
            if ((componentPath == null) == (nodeId == null) ||
                componentPath != null && (componentPath.length > 4096 || componentPath.isNotEmpty() && componentPath.split(':').any { it.isEmpty() }) ||
                nodeId != null && (nodeId.isBlank() || nodeId.length > 4096))
                fail("INVALID_REQUEST", "Supply exactly one of componentPath (colon-separated IDs, empty for the root) or nodeId (from the current page structure), at most 4096 characters.")
        }
        private fun fail(code: String, message: String): Nothing = throw PageAnalysisFailure(code, message)
        private fun JsonObject.text(key: String) = get(key)?.takeUnless { it.isJsonNull }?.asString
        private fun JsonObject.isItem() = get("templateItem")?.asBoolean == true
        private fun array(values: Iterable<com.google.gson.JsonElement>) = JsonArray().apply { values.forEach(::add) }
    }
}
