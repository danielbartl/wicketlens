package dev.wicketlens.analysis

import com.intellij.psi.PsiClass
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.util.InheritanceUtil
import dev.wicketlens.model.PageReport
import dev.wicketlens.model.Confidence
import dev.wicketlens.model.Diagnostic
import dev.wicketlens.model.DiagnosticCode

/** Composes discovery, pairing, extraction, and matching under a smart read action. */
class WicketPageAnalyzer {
    fun analyze(page: PsiClass, markupSuffix: String = ""): PageReport? {
        val discovered = WicketJavaAnalyzer().analyze(page) ?: return null
        val owners = discovered.nodes.filter { it.nodeId != discovered.rootNodeId }.filter { node ->
            JavaPsiFacade.getInstance(page.project).findClasses(node.componentType, page.resolveScope).singleOrNull()
                ?.let(WicketComponentTypes::ownsMarkup) == true
        }.map { it.nodeId }.toSet()
        val byId = discovered.nodes.associateBy { it.nodeId }
        val java = discovered.copy(nodes = discovered.nodes.map { node ->
            val ancestors = generateSequence(node.parentNodeId) { byId[it]?.parentNodeId }.take(discovered.nodes.size)
            if (ancestors.none { it in owners }) node else node.copy(confidence = Confidence.UNRESOLVED,
                diagnostics = node.diagnostics + Diagnostic(DiagnosticCode.UNRESOLVED_SCOPE,
                    "This child belongs to a component's separate markup scope; analyze the owning component's source.", node.javaLocation))
        })
        val matcher = WicketGraphMatcher()
        val containers = discovered.nodes.filter { node -> JavaPsiFacade.getInstance(page.project)
            .findClasses(node.componentType, page.resolveScope).singleOrNull()?.let {
                InheritanceUtil.isInheritor(it, "org.apache.wicket.MarkupContainer")
            } == true }.map { it.nodeId }.toSet()
        val queueRegions = owners + discovered.nodes.filter { node -> JavaPsiFacade.getInstance(page.project)
            .findClasses(node.componentType, page.resolveScope).singleOrNull()?.let {
                InheritanceUtil.isInheritor(it, "org.apache.wicket.IQueueRegion")
            } == true }.map { it.nodeId }
        val kind = WicketComponentTypes.kind(page)
        val inheritance = WicketMarkupInheritanceAnalyzer()
        fun match(result: WicketMarkupInheritanceAnalyzer.Result) = matcher.match(WicketQueueResolver.resolve(java.copy(
            markupPath = result.resolution.markupPath, markup = result.markup,
            warnings = java.warnings + result.resolution.diagnostics + result.markup?.diagnostics.orEmpty(), markupSuffix = markupSuffix), containers, queueRegions))
        val first = match(inheritance.analyze(page, kind, markupSuffix = markupSuffix))
        // A matched Panel/FormComponentPanel replaces its tag's preview body with its own markup.
        // Re-extraction preserves syntax/document diagnostics and original offsets.
        val boundaries = first.nodes.filter { it.nodeId in owners && it.markupLocation != null }
            .mapNotNull { it.markupLocation }.toSet()
        return if (boundaries.isEmpty()) first else match(inheritance.analyze(page, kind, boundaries, markupSuffix))
    }
}
