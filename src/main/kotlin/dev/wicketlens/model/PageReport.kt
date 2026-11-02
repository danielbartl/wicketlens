package dev.wicketlens.model

/** Serializable values only: reports must not retain PSI or project objects. */
data class SourceLocation(val file: String, val offset: Int)

enum class Confidence { CONFIRMED, INFERRED, UNRESOLVED }

enum class DiagnosticCode {
    MATCHED,
    JAVA_ONLY,
    MARKUP_ONLY,
    UNRESOLVED_DYNAMIC_ID,
    UNRESOLVED_ID,
    UNRESOLVED_PARENT,
    UNRESOLVED_MARKUP,
    UNRESOLVED_CONSTRUCTOR,
    UNRESOLVED_VARIABLE,
    UNRESOLVED_SCOPE,
    UNRESOLVED_QUEUE,
    UNRESOLVED_BRANCH,
    PARTIAL_ANALYSIS,
}

enum class Severity { INFO, WARNING, ERROR }

data class Diagnostic(
    val code: DiagnosticCode,
    val message: String,
    val location: SourceLocation? = null,
    val severity: Severity = Severity.INFO,
)

data class ComponentNode(
    val nodeId: String,
    val wicketId: String?,
    val componentType: String,
    val parentNodeId: String?,
    val javaLocation: SourceLocation,
    val markupLocation: SourceLocation? = null,
    val confidence: Confidence,
    val diagnostics: List<Diagnostic> = emptyList(),
    // Snapshot provenance for source expansion; the exported construction location stays unchanged.
    val idLocation: SourceLocation? = null,
    val idRenameSafe: Boolean = true,
    val computedId: Boolean = false,
    val idUsedInComputation: Boolean = false,
    val queuedInNodeId: String? = null,
    val repetition: Repetition? = null,
    val templateItem: Boolean = false,
    val template: Boolean = false,
    // Alternative allocations/origins grouped by ID/type/parent path across
    // bounded setup paths. The primary locations remain stable navigation targets.
    val branchMerged: Boolean = false,
    val conditional: Boolean = false,
    val alternativeJavaLocations: List<SourceLocation> = emptyList(),
    val alternativeIdLocations: List<SourceLocation> = emptyList(),
    // Per-origin kind is required when a merged path has both literal and computed
    // alternatives. An empty list retains legacy single-kind snapshot behavior.
    val computedIdLocations: List<SourceLocation> = emptyList(),
    // Invocation-local feasible-path membership for queue placement. Zero means
    // shared/no branch expansion. Never exported as runtime branch information.
    val branchPathMask: Int = 0,
) {
    fun idLocations(): List<SourceLocation> = (listOfNotNull(idLocation) + alternativeIdLocations).distinct()
    fun isComputedIdOrigin(location: SourceLocation): Boolean =
        computedId && (computedIdLocations.isEmpty() || location in computedIdLocations)
}

enum class Repetition { LIST_VIEW, REPEATING_VIEW }

data class PageReport(
    val qualifiedClassName: String,
    val rootNodeId: String,
    val nodes: List<ComponentNode>,
    val markupPath: String? = null,
    val schemaVersion: Int = 1,
    val warnings: List<Diagnostic> = emptyList(),
    // Discovery alone cannot establish that all runtime children have been found.
    val scopeComplete: Boolean = false,
    val markup: MarkupReport? = null,
    val matching: MatchReport? = null,
    // Additional source dependencies, including superclass/helper/constructor contracts; not exported.
    val javaSourceFiles: List<String> = emptyList(),
    // Explicit filename suffix selected by opening HTML; Java analysis defaults to unsuffixed markup.
    val markupSuffix: String = "",
    val branchPathCount: Int = 1,
)
