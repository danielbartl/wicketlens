package dev.wicketlens.spring

import dev.wicketlens.model.SourceLocation

/** Sidebar values only: no PSI, project, documents, or runtime bean instances. */
data class WicketDependencySnapshot(
    val owners: List<DependencyOwner> = emptyList(),
    val sourceFiles: List<String> = emptyList(),
    val application: String? = null,
    val truncated: Boolean = false,
) {
    val fieldCount get() = owners.sumOf { it.fields.size }
    val locations get() = owners.flatMap { owner -> listOf(owner.location) + owner.fields.flatMap { field ->
        listOf(field.location) + field.candidates.map { it.location }
    } }.toSet()
}

data class DependencyOwner(val id: String, val path: String, val type: String, val location: SourceLocation,
    val fields: List<InjectedField>, val partial: Boolean = false)

enum class DependencyLookup { TYPE, NAME, UNKNOWN, UNSUPPORTED, SPRING_AUTOWIRING }

data class InjectedField(val id: String, val name: String, val type: String, val declaringClass: String,
    val annotation: String, val lookup: DependencyLookup, val beanName: String?, val required: Boolean?,
    val location: SourceLocation, val candidates: List<DependencyCandidate>, val notes: List<String> = emptyList(),
    val candidatesTruncated: Boolean = false)

data class DependencyCandidate(val names: List<String>, val type: String, val declaringClass: String,
    val location: SourceLocation, val compatible: Boolean?, val conditional: Boolean, val factory: Boolean)
