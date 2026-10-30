package dev.wicketlens.ui

import dev.wicketlens.model.SourceLocation
import dev.wicketlens.model.PageReport

enum class SourceSide { JAVA, MARKUP }

/** The class row opens the selected companion file, independently of component matching. */
internal fun PageReport.companionLocation(): SourceLocation? {
    val snapshot = markup ?: return null
    if (snapshot.file != markupPath || snapshot.file !in snapshot.sourceFiles) return null
    return SourceLocation(snapshot.file, 0)
}

/** Character ranges in the rendered report, with value-only source destinations. */
data class ReportLink(
    val start: Int,
    val end: Int,
    val javaLocation: SourceLocation?,
    val markupLocation: SourceLocation?,
    val preferredSide: SourceSide,
) {
    fun location(side: SourceSide) = if (side == SourceSide.JAVA) javaLocation else markupLocation
}

data class FormattedReport(val text: String, val links: List<ReportLink>) {
    fun linkAt(offset: Int): ReportLink? = links.firstOrNull { offset >= it.start && offset < it.end }
}
