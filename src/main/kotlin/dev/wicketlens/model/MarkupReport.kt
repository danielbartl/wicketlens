package dev.wicketlens.model

enum class MarkupKind { PAGE, PANEL }

/** Every tag is retained, including ordinary HTML wrappers and Panel preview tags. */
data class MarkupElement(
    val nodeId: String,
    val tagName: String,
    val parentElementId: String?,
    val hasWicketId: Boolean,
    val wicketId: String?,
    val parentWicketElementId: String?,
    val location: SourceLocation,
    val wicketIdLocation: SourceLocation?,
    val participatesInComponentTree: Boolean,
    val confidence: Confidence,
    val diagnostics: List<Diagnostic> = emptyList(),
)

/** Completeness describes supported static markup, not a complete runtime tree. */
data class MarkupReport(
    val file: String,
    val kind: MarkupKind,
    val elements: List<MarkupElement>,
    val scopeComplete: Boolean,
    val diagnostics: List<Diagnostic> = emptyList(),
    val documentDiagnostics: List<Diagnostic> = emptyList(),
    // Snapshot dependencies, including empty extension files; not part of the JSON schema.
    val sourceFiles: List<String> = listOf(file),
)
