package dev.wicketlens.ui

import com.intellij.openapi.progress.ProgressManager
import dev.wicketlens.model.DiagnosticCode
import java.util.Locale

enum class HierarchyStatusFilter(private val title: String) {
    ALL("All"), UNPAIRED("Unpaired"), JAVA_ONLY("Java only"), MARKUP_ONLY("Markup only"),
    UNRESOLVED("Unresolved"), MATCHED("Matched");

    override fun toString() = title

    fun accepts(entry: ReportTreeEntry): Boolean = when (this) {
        ALL -> true
        UNPAIRED -> entry.status in setOf(DiagnosticCode.JAVA_ONLY, DiagnosticCode.MARKUP_ONLY)
        JAVA_ONLY -> entry.status == DiagnosticCode.JAVA_ONLY
        MARKUP_ONLY -> entry.status == DiagnosticCode.MARKUP_ONLY
        UNRESOLVED -> entry.status?.name?.startsWith("UNRESOLVED_") == true
        MATCHED -> entry.status == DiagnosticCode.MATCHED
    }
}

data class FilteredHierarchy(val entries: List<ReportTreeEntry>, val matchCount: Int, val active: Boolean)

/** A presentation-only projection; source locations, findings, and the full report are unchanged. */
class ReportHierarchyFilter {
    fun apply(entries: List<ReportTreeEntry>, query: String, status: HierarchyStatusFilter, focusId: String? = null): FilteredHierarchy {
        val terms = query.trim().lowercase(Locale.ROOT).split(Regex("\\s+")).filter { it.isNotEmpty() }
        val byId = entries.associateBy { it.id }
        val focus = focusId?.takeIf { it != "report" && it in byId }
        val active = terms.isNotEmpty() || status != HierarchyStatusFilter.ALL || focus != null
        if (!active) return FilteredHierarchy(entries, entries.count(::searchable), false)
        val scope = focus?.let {
            val children = entries.groupBy { it.parentId }
            val pending = ArrayDeque<String>().apply { add(it) }
            val included = mutableSetOf<String>()
            while (pending.isNotEmpty()) {
                ProgressManager.checkCanceled()
                val id = pending.removeFirst()
                if (included.add(id)) children[id].orEmpty().forEach { child -> pending.addLast(child.id) }
            }
            included
        }
        val paths = mutableMapOf<String, String>()
        fun path(entry: ReportTreeEntry): String {
            val pending = mutableListOf<ReportTreeEntry>()
            val seen = mutableSetOf<String>()
            var current: ReportTreeEntry? = entry
            while (current != null && current.id !in paths && seen.add(current.id)) {
                ProgressManager.checkCanceled()
                pending += current
                current = byId[current.parentId]
            }
            var prefix = current?.let { paths[it.id] }.orEmpty()
            for (row in pending.asReversed()) {
                if (row.kind != ReportEntryKind.GROUP && row.kind != ReportEntryKind.DIAGNOSTIC) {
                    prefix = listOf(prefix, row.name.lowercase(Locale.ROOT)).filter { it.isNotEmpty() }.joinToString(":")
                }
                paths[row.id] = prefix
            }
            return paths[entry.id].orEmpty()
        }
        val matched = entries.filter { entry ->
            ProgressManager.checkCanceled()
            (scope == null || entry.id in scope) && searchable(entry) && status.accepts(entry) && (terms.isEmpty() || run {
                val text = listOfNotNull(entry.name, entry.qualifiedTypeName, path(entry)).joinToString(" ").lowercase(Locale.ROOT)
                terms.all { it in text }
            })
        }.map { it.id }.toSet()
        val retained = matched.toMutableSet()
        for (id in matched + listOfNotNull(focus)) {
            retained += id
            var parent = byId[id]?.parentId
            while (parent != null && retained.add(parent)) {
                ProgressManager.checkCanceled()
                parent = byId[parent]?.parentId
            }
        }
        // Always retain the page root, including when no findings match.
        if ("report" in byId) retained += "report"
        return FilteredHierarchy(entries.filter { it.id in retained }.map { it.copy(contextOnly = it.id !in matched) }, matched.size, true)
    }

    private fun searchable(entry: ReportTreeEntry) = entry.kind !in setOf(ReportEntryKind.PAGE, ReportEntryKind.GROUP)
}
