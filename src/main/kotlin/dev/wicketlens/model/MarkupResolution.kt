package dev.wicketlens.model

enum class MarkupResolutionStatus { PAIRED, MISSING, AMBIGUOUS, UNSUPPORTED }

/** File pairing only; PAIRED makes no claim about markup contents or ID matches. */
data class MarkupResolution(
    val status: MarkupResolutionStatus,
    val markupPath: String? = null,
    val candidatePaths: List<String> = emptyList(),
    val diagnostics: List<Diagnostic> = emptyList(),
    val markupUrl: String? = null,
    // Other conventional variants, used to guard edits to Java shared by multiple templates.
    val relatedVariantPaths: List<String> = emptyList(),
)
