package dev.wicketlens.ui

import com.intellij.openapi.progress.ProgressManager

data class HierarchySegment(val role: String, val name: String, val type: String)
data class HierarchyRowKey(val path: List<HierarchySegment>)

/** Logical addresses deliberately exclude source offsets, status, and display-only context flags. */
class HierarchyStateIndex(entries: List<ReportTreeEntry>) {
    private val keys: Map<String, HierarchyRowKey>
    private val ids: Map<HierarchyRowKey, String>

    init {
        val byId = entries.associateBy { it.id }
        val raw = mutableMapOf<String, HierarchyRowKey>()
        for (entry in entries) {
            ProgressManager.checkCanceled()
            val pending = mutableListOf<ReportTreeEntry>()
            val seen = mutableSetOf<String>()
            var current: ReportTreeEntry? = entry
            while (current != null && current.id !in raw && seen.add(current.id)) {
                pending += current
                current = byId[current.parentId]
            }
            if (current != null && current.id !in raw) continue // Invalid cyclic ancestry is not a state address.
            var path = current?.let { raw[it.id]?.path }.orEmpty()
            for (row in pending.asReversed()) {
                val segment = when (row.kind) {
                    ReportEntryKind.PAGE -> HierarchySegment("page", "report", "")
                    ReportEntryKind.GROUP -> HierarchySegment("group", row.id, "")
                    ReportEntryKind.DIAGNOSTIC -> HierarchySegment("diagnostic", row.stateName ?: break,
                        row.javaLocation?.file ?: row.markupLocation?.file.orEmpty())
                    ReportEntryKind.MARKUP -> HierarchySegment("markup", row.stateName ?: break, row.qualifiedTypeName.orEmpty())
                    ReportEntryKind.COMPONENT, ReportEntryKind.CONTAINER -> HierarchySegment(if (row.templateItem) "template" else "component",
                        row.stateName ?: break, row.qualifiedTypeName.orEmpty())
                }
                path = path + segment
                raw[row.id] = HierarchyRowKey(path)
            }
        }
        val ambiguous = raw.values.groupingBy { it }.eachCount().filterValues { it > 1 }.keys
        val excluded = raw.filterValues { it in ambiguous }.keys.toMutableSet()
        val children = entries.groupBy { it.parentId }
        val pending = ArrayDeque(excluded)
        while (pending.isNotEmpty()) {
            ProgressManager.checkCanceled()
            children[pending.removeFirst()].orEmpty().forEach { if (excluded.add(it.id)) pending.addLast(it.id) }
        }
        keys = raw.filterKeys { it !in excluded }
        ids = keys.entries.associate { it.value to it.key }
    }

    fun keyFor(id: String?): HierarchyRowKey? = keys[id]
    fun idFor(key: HierarchyRowKey?): String? = ids[key]
}

data class HierarchyViewState(
    val expanded: Set<HierarchyRowKey>,
    val unfilteredExpanded: Set<HierarchyRowKey>,
    val selected: HierarchyRowKey?,
    val focused: HierarchyRowKey?,
    val query: String,
    val status: HierarchyStatusFilter,
)
