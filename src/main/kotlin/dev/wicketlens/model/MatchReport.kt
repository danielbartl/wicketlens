package dev.wicketlens.model

/** One finding per Java component; unpaired active markup components get their own findings. */
data class MatchFinding(
    val code: DiagnosticCode,
    val message: String,
    val confidence: Confidence,
    val javaNodeId: String? = null,
    val markupNodeId: String? = null,
    val javaLocation: SourceLocation? = null,
    val markupLocation: SourceLocation? = null,
    val severity: Severity = Severity.INFO,
    val causes: List<Diagnostic> = emptyList(),
)

data class MatchReport(
    val findings: List<MatchFinding>,
    val scopeComplete: Boolean = false,
)
