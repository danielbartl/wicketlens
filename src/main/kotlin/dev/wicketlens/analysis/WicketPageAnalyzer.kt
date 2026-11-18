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
        val initial = WicketJavaAnalyzer().analyze(page) ?: return null
        val discovered = WicketBaseLayoutAnalyzer().analyze(page, initial, markupSuffix)
        val customScopes = discovered.nodes.filter { it.nodeId != discovered.rootNodeId }.filter { node ->
            JavaPsiFacade.getInstance(page.project).findClasses(node.componentType, page.resolveScope).singleOrNull()?.let { type ->
                generateSequence(type) { it.superClass }.take(12).filter { it !is com.intellij.psi.PsiCompiledElement }.any { owner ->
                    owner.methods.any { it.name in setOf("getMarkup", "getMarkupType", "getMarkupResourceStream", "getMarkupSourcingStrategy", "resolve", "findChildComponent", "newMarkupSourcingStrategy", "chooseMarkup") &&
                        it.findDeepestSuperMethods().any { parent -> parent.containingClass?.qualifiedName?.startsWith("org.apache.wicket.") == true } }
                }
            } == true
        }.map { it.nodeId }.toSet()
        val owners = customScopes + discovered.nodes.filter { it.nodeId != discovered.rootNodeId }.filter { node ->
            JavaPsiFacade.getInstance(page.project).findClasses(node.componentType, page.resolveScope).singleOrNull()
                ?.let { !node.sourceSetupExpanded && WicketComponentTypes.ownsMarkup(it) || node.fragment == null && WicketFragmentAnalyzer.isFragment(it) } == true
        }.map { it.nodeId }.toSet()
        val byId = discovered.nodes.associateBy { it.nodeId }
        val java = discovered.copy(nodes = discovered.nodes.map { node ->
            val ancestors = generateSequence(node.parentNodeId) { byId[it]?.parentNodeId }.take(discovered.nodes.size)
            val described = if (node.nodeId in customScopes) node.copy(diagnostics = node.diagnostics + Diagnostic(DiagnosticCode.UNRESOLVED_SCOPE,
                "Custom component markup resolution remains unexpanded.", node.javaLocation)) else node
            if (ancestors.none { it in owners }) described else described.copy(confidence = Confidence.UNRESOLVED,
                diagnostics = described.diagnostics + Diagnostic(DiagnosticCode.UNRESOLVED_SCOPE,
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
        val transparent = discovered.nodes.filter { node -> JavaPsiFacade.getInstance(page.project)
            .findClasses(node.componentType, page.resolveScope).singleOrNull()?.let { type ->
                (type.qualifiedName == "org.apache.wicket.markup.html.TransparentWebMarkupContainer" ||
                    InheritanceUtil.isInheritor(type, "org.apache.wicket.markup.html.TransparentWebMarkupContainer")) &&
                    generateSequence(type) { it.superClass }.takeWhile { it.qualifiedName != "org.apache.wicket.markup.html.TransparentWebMarkupContainer" }
                        .none { owner -> owner.methods.any { it.name in setOf("resolve", "findChildComponent") } }
            } == true }.map { it.nodeId }.toSet()
        fun extract(boundaries: Set<dev.wicketlens.model.SourceLocation> = emptySet()): WicketMarkupInheritanceAnalyzer.Result {
            val raw = inheritance.analyze(page, kind, boundaries, markupSuffix, allowOpenExtension = java.baseLayoutTemplate != null)
            val plan = WicketTransparentMarkup.plan(java, raw.markup, transparent)
            val result = if (plan.transparentLocations.isEmpty()) raw else
                inheritance.analyze(page, kind, boundaries, markupSuffix, plan.transparentLocations, allowOpenExtension = java.baseLayoutTemplate != null)
            return result.copy(markup = WicketTransparentMarkup.apply(result.markup, plan))
        }
        fun match(result: WicketMarkupInheritanceAnalyzer.Result) = matcher.match(WicketQueueResolver.resolve(java.copy(
            markupPath = result.resolution.markupPath, markup = result.markup,
            warnings = java.warnings + result.resolution.diagnostics + result.markup?.diagnostics.orEmpty(), markupSuffix = markupSuffix), containers, queueRegions))
        val first = match(extract())
        // A matched Panel/FormComponentPanel replaces its tag's preview body with its own markup.
        // Re-extraction preserves syntax/document diagnostics and original offsets.
        val boundaries = first.nodes.filter { (it.nodeId in owners || it.fragment != null || it.sourceSetupExpanded) && it.markupLocation != null }
            .mapNotNull { it.markupLocation }.toSet()
        val result = if (boundaries.isEmpty()) first else match(extract(boundaries))
        val fragments = matcher.match(WicketFragmentAnalyzer().analyze(page, result))
        if (fragments.fragmentUses.isEmpty() && fragments.nodes.none { it.sourceSetupExpanded }) return WicketPopulationAnalyzer().analyze(page, fragments)
        val previewParents = fragments.matching!!.findings.filter { it.code == DiagnosticCode.MATCHED && it.javaNodeId in owners }
            .mapNotNull { it.markupNodeId }.toMutableSet()
        val fragmentMarkup = fragments.markup ?: return WicketPopulationAnalyzer().analyze(page, fragments)
        val hidden = mutableSetOf<String>()
        val elements = fragmentMarkup.elements.map { element ->
            if (element.parentElementId in previewParents || element.parentElementId in hidden) {
                hidden += element.nodeId
                element.copy(participatesInComponentTree = false, diagnostics = emptyList())
            } else element
        }
        val diagnostics = (fragmentMarkup.documentDiagnostics + elements.flatMap { it.diagnostics }).distinct()
        return WicketPopulationAnalyzer().analyze(page, matcher.match(fragments.copy(markup = fragmentMarkup.copy(elements = elements, diagnostics = diagnostics,
            scopeComplete = fragmentMarkup.scopeComplete && diagnostics.isEmpty()))))
    }
}
