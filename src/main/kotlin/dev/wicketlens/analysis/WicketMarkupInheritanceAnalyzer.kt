package dev.wicketlens.analysis

import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiManager
import dev.wicketlens.model.Confidence
import dev.wicketlens.model.Diagnostic
import dev.wicketlens.model.DiagnosticCode
import dev.wicketlens.model.MarkupElement
import dev.wicketlens.model.MarkupKind
import dev.wicketlens.model.MarkupReport
import dev.wicketlens.model.MarkupResolution
import dev.wicketlens.model.MarkupResolutionStatus
import dev.wicketlens.model.SourceLocation

/** Per-request source/dependency markup composition. Never fabricates PSI or source offsets. */
internal class WicketMarkupInheritanceAnalyzer {
    data class Result(val resolution: MarkupResolution, val markup: MarkupReport?)
    private data class Source(val owner: PsiClass, val resolution: MarkupResolution)

    fun analyze(page: PsiClass, kind: MarkupKind, boundaries: Set<SourceLocation> = emptySet(), markupSuffix: String = ""): Result {
        val resolver = WicketPageResolver()
        val visited = mutableSetOf<PsiClass>()
        fun findSource(start: PsiClass?): Source? {
            var owner = start
            while (owner != null && owner.qualifiedName !in FRAMEWORK_ROOTS && visited.add(owner)) {
                ProgressManager.checkCanceled()
                if (visited.size > 32) return Source(owner, MarkupResolution(MarkupResolutionStatus.UNSUPPORTED,
                    diagnostics = listOf(Diagnostic(DiagnosticCode.UNRESOLVED_MARKUP, "Markup inheritance exceeds the 32-class lookup bound."))))
                val resolution = resolver.resolve(owner, markupSuffix, page)
                if (resolution.status != MarkupResolutionStatus.MISSING) return Source(owner, resolution)
                owner = owner.superClass
            }
            return null
        }
        val primary = findSource(page) ?: return Result(resolver.resolve(page, markupSuffix), null)
        if (primary.resolution.markupPath == null) return Result(primary.resolution, null)
        val extractor = WicketMarkupAnalyzer()
        fun read(source: Source): MarkupReport? {
            val file = source.resolution.markupUrl?.let { VirtualFileManager.getInstance().findFileByUrl(it) }
                ?.let { PsiManager.getInstance(page.project).findFile(it) } ?: return null
            return extractor.analyzeInheritanceSource(file, kind, boundaries)
        }
        val first = read(primary) ?: return Result(primary.resolution.copy(diagnostics = listOf(Diagnostic(
            DiagnosticCode.UNRESOLVED_MARKUP, "The paired markup file is no longer available to PSI.",
            SourceLocation(primary.resolution.markupPath, 0),
        ))), null)

        fun compose(source: Source, raw: MarkupReport): MarkupReport {
            ProgressManager.checkCanceled()
            val extensions = raw.elements.filter { it.tagName == "wicket:extend" }
            if (extensions.isEmpty()) return raw
            if (extensions.size != 1) return unresolved(raw, "Markup inheritance requires one extension section.")
            val parentSource = findSource(source.owner.superClass)
                ?: return unresolved(raw, "No conventional source ancestor markup was found for wicket:extend.")
            val parentRaw = read(parentSource)
                ?: return unresolved(raw, "Ancestor markup is missing, ambiguous, or unsupported.", parentSource.resolution.diagnostics)
            val parent = compose(parentSource, parentRaw)
            val slots = parent.elements.filter { it.participatesInComponentTree && it.tagName == "wicket:child" }
            if (slots.size != 1) return unresolved(raw.copy(sourceFiles = (parent.sourceFiles + raw.sourceFiles).distinct()),
                "Ancestor markup must expose exactly one active wicket:child slot.", parent.diagnostics)
            val slot = slots.single()
            if (generateSequence(parent.elements.singleOrNull { it.nodeId == slot.parentElementId }) { node ->
                    parent.elements.singleOrNull { it.nodeId == node.parentElementId }
                }.any { it.tagName in setOf("head", "wicket:head") }) {
                return unresolved(raw.copy(sourceFiles = (parent.sourceFiles + raw.sourceFiles).distinct()),
                    "A wicket:child slot inside a header is not supported.", parent.diagnostics)
            }
            if (parent.elements.any { it.parentElementId == slot.nodeId } || slot.hasWicketId || slot.diagnostics.isNotEmpty()) {
                return unresolved(raw.copy(sourceFiles = (parent.sourceFiles + raw.sourceFiles).distinct()),
                    "A wicket:child slot with content or a component ID is not supported.", parent.diagnostics)
            }
            val parentElements = qualify(parent)
            val derivedElements = qualify(raw)
            val slotId = qualified(slot)
            val slotParent = parentElements.single { it.nodeId == slotId }.parentElementId
            val extendId = qualified(extensions.single())
            val headerParent = parentElements.singleOrNull { it.participatesInComponentTree && it.tagName == "head" }?.nodeId
            val merged = parentElements.filter { it.nodeId != slotId } + derivedElements.map {
                when {
                    it.nodeId == extendId -> it.copy(parentElementId = slotParent)
                    it.participatesInComponentTree && it.tagName == "wicket:head" -> it.copy(parentElementId = headerParent)
                    else -> it
                }
            }
            return normalize(raw.copy(elements = merged, sourceFiles = (parent.sourceFiles + raw.sourceFiles).distinct(),
                documentDiagnostics = (parent.documentDiagnostics + raw.documentDiagnostics).distinct()))
        }

        var graph = compose(primary, first)
        if (graph.elements.any { it.participatesInComponentTree && it.tagName == "wicket:child" }) {
            graph = unresolved(graph, "An unfilled wicket:child slot remains in the selected markup.")
        }
        return Result(primary.resolution, graph)
    }

    private fun qualified(element: MarkupElement) = "${element.location.file}#${element.nodeId.substringAfterLast('#')}"

    private fun qualify(report: MarkupReport): List<MarkupElement> {
        val ids = report.elements.associate { it.nodeId to qualified(it) }
        return report.elements.map { it.copy(nodeId = ids.getValue(it.nodeId),
            parentElementId = ids[it.parentElementId], parentWicketElementId = ids[it.parentWicketElementId]) }
    }

    /** Recompute context after reparenting, including duplicate paths spanning different files. */
    private fun normalize(report: MarkupReport): MarkupReport {
        val byId = report.elements.associateBy { it.nodeId }
        val normalized = mutableMapOf<String, MarkupElement>()
        fun visit(element: MarkupElement): MarkupElement {
            normalized[element.nodeId]?.let { return it }
            ProgressManager.checkCanceled()
            val parent = byId[element.parentElementId]?.let(::visit)
            return element.copy(parentWicketElementId = if (!element.participatesInComponentTree) null
                else if (parent?.hasWicketId == true) parent.nodeId else parent?.parentWicketElementId).also {
                normalized[element.nodeId] = it
            }
        }
        report.elements.forEach(::visit)
        val duplicates = normalized.values.filter { it.participatesInComponentTree && it.hasWicketId }
            .groupBy { it.parentWicketElementId to it.wicketId }.values.filter { it.size > 1 }.flatten().map { it.nodeId }.toSet()
        val finished = mutableMapOf<String, MarkupElement>()
        fun finish(element: MarkupElement): MarkupElement {
            finished[element.nodeId]?.let { return it }
            ProgressManager.checkCanceled()
            val parent = normalized[element.parentElementId]?.let(::finish)
            val issues = element.diagnostics.toMutableList()
            if (element.nodeId in duplicates) issues += Diagnostic(DiagnosticCode.UNRESOLVED_MARKUP,
                "Repeated markup IDs under the merged Wicket parent cannot be matched uniquely.", element.wicketIdLocation)
            val uncertain = report.documentDiagnostics.isNotEmpty() || parent?.confidence == Confidence.UNRESOLVED || issues.isNotEmpty()
            if (uncertain && element.hasWicketId) issues += Diagnostic(DiagnosticCode.UNRESOLVED_PARENT,
                "The merged markup context is unresolved.", element.wicketIdLocation)
            return element.copy(confidence = if (uncertain) Confidence.UNRESOLVED else Confidence.CONFIRMED,
                diagnostics = issues.distinct()).also { finished[element.nodeId] = it }
        }
        val elements = report.elements.map { finish(normalized.getValue(it.nodeId)) }
        val diagnostics = (report.documentDiagnostics + elements.flatMap { it.diagnostics }).distinct()
        return report.copy(elements = elements, diagnostics = diagnostics, scopeComplete = diagnostics.isEmpty())
    }

    private fun unresolved(report: MarkupReport, message: String, causes: List<Diagnostic> = emptyList()): MarkupReport {
        val issue = Diagnostic(DiagnosticCode.UNRESOLVED_MARKUP, message, SourceLocation(report.file, 0))
        return normalize(report.copy(documentDiagnostics = (report.documentDiagnostics + causes + issue).distinct()))
    }

    private companion object {
        val FRAMEWORK_ROOTS = WicketComponentTypes.markupRoots
    }
}
