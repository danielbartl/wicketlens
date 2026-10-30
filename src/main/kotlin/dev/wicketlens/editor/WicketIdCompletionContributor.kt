package dev.wicketlens.editor

import com.intellij.codeInsight.completion.*
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.icons.AllIcons
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.util.Key
import com.intellij.psi.*
import com.intellij.psi.util.InheritanceUtil
import dev.wicketlens.model.*

class WicketIdCompletionContributor : CompletionContributor() {
    override fun fillCompletionVariants(parameters: CompletionParameters, result: CompletionResultSet) {
        if (parameters.completionType != CompletionType.BASIC) return
        val file = parameters.originalFile
        val value = WicketIdPsi.valueAt(file, parameters.offset) ?: return
        val java = WicketIdPsi.javaId(value)
        val html = WicketIdPsi.htmlId(value)
        if (java == null && html == null) return
        // Completion uses the committed original file, never the synthetic file containing the IDE dummy identifier.
        val range = ElementManipulators.getValueTextRange(value).shiftRight(value.textRange.startOffset)
        if (parameters.offset !in range.startOffset..range.endOffset || value.text.startsWith("\"\"\"")) return
        val rawPrefix = file.text.substring(range.startOffset, parameters.offset)
        val prefix = if (java != null) StringUtil.unescapeStringCharacters(rawPrefix) else StringUtil.unescapeXmlEntities(rawPrefix)
        val remaining = range.endOffset - parameters.offset
        val matcher = result.withPrefixMatcher(prefix)
        candidates(value).forEach { id ->
            val lookup = LookupElementBuilder.create(id).withIcon(AllIcons.Nodes.Tag).withTypeText("Wicket ID")
                .withInsertHandler { context, _ ->
                    val encoded = if (java != null) StringUtil.escapeStringCharacters(id) else escapeXml(id)
                    context.document.replaceString(range.startOffset, context.tailOffset + remaining, encoded)
                    context.tailOffset = range.startOffset + encoded.length
                    context.editor.caretModel.moveToOffset(context.tailOffset)
                }
            lookup.putUserData(CANDIDATE, true)
            matcher.addElement(lookup)
        }
    }

    internal fun candidates(value: PsiElement): List<String> {
        val java = WicketIdPsi.javaId(value)
        val html = WicketIdPsi.htmlId(value)
        if (java == null && html == null) return emptyList()
        return WicketEditorAnalysis.pages(value.containingFile).flatMap { page ->
            val report = page.report
            if (report.markup == null || report.warnings.any { it.code == DiagnosticCode.UNRESOLVED_SCOPE } ||
                report.markup.documentDiagnostics.isNotEmpty()) return@flatMap emptyList()
            val markup = report.markup.elements.filter { it.participatesInComponentTree && it.hasWicketId }
            if (java != null) {
                val origin = SourceLocation(value.containingFile.virtualFile.path, java.textOffset)
                val current = report.nodes.singleOrNull { it.idLocation == origin } ?: return@flatMap emptyList()
                if (current.idUsedInComputation) return@flatMap emptyList()
                if (current.diagnostics.any { it.code !in setOf(DiagnosticCode.UNRESOLVED_ID, DiagnosticCode.UNRESOLVED_QUEUE, DiagnosticCode.PARTIAL_ANALYSIS) }) return@flatMap emptyList()
                if (current.queuedInNodeId != null) return@flatMap queueCandidates(value, report, current)
                val parent = WicketIdPsi.javaPath(report, current.parentNodeId) ?: return@flatMap emptyList()
                val used = report.nodes.filter { it.nodeId != current.nodeId && WicketIdPsi.javaPath(report, it.parentNodeId) == parent }.mapNotNull { it.wicketId }.toSet()
                markup.filter { WicketIdPsi.markupPath(report, it.parentWicketElementId) == parent && it.confidence == Confidence.CONFIRMED }
                    .filter { it.wicketId !in used && WicketIdPsi.markupPath(report, it.nodeId) != null }.mapNotNull { it.wicketId }
            } else {
                val current = markup.singleOrNull { WicketIdPsi.markupValue(value.project, it) == html } ?: return@flatMap emptyList()
                val parent = WicketIdPsi.markupPath(report, current.parentWicketElementId) ?: return@flatMap emptyList()
                val used = markup.filter { it.nodeId != current.nodeId && WicketIdPsi.markupPath(report, it.parentWicketElementId) == parent }.mapNotNull { it.wicketId }.toSet()
                report.nodes.filter { it.nodeId != report.rootNodeId && WicketIdPsi.javaPath(report, it.parentNodeId) == parent &&
                    WicketIdPsi.javaPath(report, it.nodeId) != null && it.wicketId !in used }.mapNotNull { it.wicketId }
            }
        }.distinct().sorted()
    }

    private fun queueCandidates(value: PsiElement, report: PageReport, current: ComponentNode): List<String> {
        val markup = report.markup ?: return emptyList()
        if (!markup.scopeComplete) return emptyList()
        val nodes = report.nodes.associateBy { it.nodeId }
        val elements = markup.elements.associateBy { it.nodeId }
        val paths = report.nodes.filter { it.nodeId != current.nodeId && it.confidence == Confidence.CONFIRMED }
            .groupBy { WicketIdPsi.javaPath(report, it.nodeId) }
        fun type(node: ComponentNode) = JavaPsiFacade.getInstance(value.project).findClasses(node.componentType, value.resolveScope).singleOrNull()
        fun eligible(parent: ComponentNode): Boolean {
            if (parent.nodeId != report.rootNodeId && type(parent)?.let { InheritanceUtil.isInheritor(it, "org.apache.wicket.MarkupContainer") } != true) return false
            val seen = mutableSetOf<String>()
            var scope: ComponentNode? = parent
            while (scope != null && seen.add(scope.nodeId)) {
                if (scope.nodeId == current.queuedInNodeId) return true
                if (scope.templateItem || scope.repetition != null || type(scope)?.let { InheritanceUtil.isInheritor(it, "org.apache.wicket.IQueueRegion") } == true) return false
                scope = nodes[scope.parentNodeId]
            }
            return false
        }
        val occupied = report.matching?.findings.orEmpty().filter { it.code == DiagnosticCode.MATCHED && it.javaNodeId != current.nodeId }
            .mapNotNull { it.markupNodeId }.toSet()
        val namespace = report.nodes.filter { it.nodeId != current.nodeId && it.queuedInNodeId == current.queuedInNodeId }.mapNotNull { it.wicketId }.toSet()
        return markup.elements.filter { element ->
            if (!element.participatesInComponentTree || !element.hasWicketId || element.wicketId in namespace || element.nodeId in occupied ||
                element.confidence != Confidence.CONFIRMED || generateSequence(element) { elements[it.parentElementId] }.any { it.tagName in setOf("head", "wicket:head") }) return@filter false
            val parentPath = WicketIdPsi.markupPath(report, element.parentWicketElementId) ?: return@filter false
            val parents = paths[parentPath].orEmpty()
            val parent = parents.singleOrNull { it.templateItem } ?: parents.singleOrNull() ?: return@filter false
            eligible(parent)
        }.groupBy { it.wicketId }.filterValues { it.size == 1 }.keys.filterNotNull().sorted()
    }

    private fun escapeXml(value: String) = value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
        .replace("\"", "&quot;").replace("'", "&#39;")

    companion object {
        internal val CANDIDATE = Key.create<Boolean>("wicketlens.id.completion")
    }
}
