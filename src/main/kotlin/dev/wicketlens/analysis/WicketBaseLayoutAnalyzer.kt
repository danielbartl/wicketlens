package dev.wicketlens.analysis

import com.intellij.openapi.progress.ProgressManager
import com.intellij.psi.*
import com.intellij.psi.util.InheritanceUtil
import dev.wicketlens.model.*

/** Reconciles audited constructor declarations; never supplies a concrete runtime subclass. */
internal class WicketBaseLayoutAnalyzer(private val limits: Limits = Limits()) {
    data class Limits(val constructors: Int = 4, val steps: Int = 20000, val sources: Int = 64, val nodes: Int = 128)
    private class Withheld(override val message: String) : RuntimeException(null, null, false, false)

    fun analyze(type: PsiClass, initial: PageReport, suffix: String = ""): PageReport {
        if (!type.hasModifierProperty(PsiModifier.ABSTRACT) || type is PsiCompiledElement ||
            !InheritanceUtil.isInheritor(type, "org.apache.wicket.markup.html.WebPage")) return initial
        val markup = WicketMarkupInheritanceAnalyzer().analyze(type, MarkupKind.PAGE, markupSuffix = suffix, allowOpenExtension = true).markup ?: return initial
        val slots = markup.elements.filter { it.participatesInComponentTree && it.tagName == "wicket:child" }
        if (slots.size != 1 || markup.documentDiagnostics.any { "unfilled wicket:child" in it.message }) return initial
        val sources = linkedSetOf<String>().apply { addAll(initial.javaSourceFiles); addAll(markup.sourceFiles) }
        var steps = 0
        fun tick() {
            ProgressManager.checkCanceled()
            if (++steps > limits.steps || sources.size > limits.sources) throw Withheld("The shared constructor/source budget was exceeded.")
        }
        try {
            val constructors: List<PsiMethod?> = type.constructors.toList().ifEmpty { listOf(null) }
            if (constructors.size > limits.constructors) throw Withheld("More than ${limits.constructors} constructor entries need reconciliation.")
            val paths = constructors.map { ctor ->
                tick()
                if (ctor?.parameterList?.parameters.orEmpty().any { parameter ->
                        val name = (parameter.type as? PsiClassType)?.resolve()?.qualifiedName
                        name !in setOf("org.apache.wicket.request.mapper.parameter.PageParameters", "org.apache.wicket.model.IModel")
                    }) throw Withheld("Only no-argument, PageParameters and IModel entry arguments are reviewed as opaque data.")
                val result = WicketLayoutSetupAnalyzer().analyze(type, initial.rootNodeId, WicketLayoutSetupAnalyzer.ConstructorEntry(ctor) { consulted -> sources += consulted; tick() })
                sources += result.sources
                val expansion = result.expansion ?: throw Withheld(result.diagnostics.firstOrNull()?.message ?: "A constructor's complete setup could not be audited.")
                sources += expansion.sources
                if (expansion.nodes.size > limits.nodes) throw Withheld("The ${limits.nodes}-node constructor graph bound was exceeded.")
                tick()
                expansion
            }
            fun index(expansion: WicketSourceSetupAnalyzer.Expansion): Map<List<String>, ComponentNode> {
                val byId = expansion.nodes.associateBy { it.nodeId }
                val result = linkedMapOf<List<String>, ComponentNode>()
                fun path(node: ComponentNode, seen: Set<String> = emptySet()): List<String> {
                    tick()
                    if (seen.size >= limits.nodes || node.nodeId in seen || node.confidence != Confidence.CONFIRMED || (node.wicketId.isNullOrEmpty() && !WicketCompletionAnalysis.allowsEmpty(node.wicketId, node.idLocation)) ||
                        node.templateItem || node.conditional || node.fragment != null || node.queuedInNodeId != null)
                        throw Withheld("A constructor has unresolved, conditional, fragment or queued attachment paths.")
                    return (if (node.parentNodeId == initial.rootNodeId) emptyList() else
                        path(byId[node.parentNodeId] ?: throw Withheld("A constructor parent is unavailable."), seen + node.nodeId)) + node.wicketId!!
                }
                expansion.nodes.forEach { node ->
                    if (result.put(path(node), node) != null) throw Withheld("A constructor has duplicate component paths.")
                }
                return result
            }
            val indexed = paths.map(::index)
            val first = indexed.first()
            if (indexed.any { it.keys != first.keys }) throw Withheld("Constructor entries declare different component paths; no common runtime scope is selected.")
            val merged = first.map { (path, node) ->
                val alternatives = indexed.map { it.getValue(path) }
                if (alternatives.any { it.componentType != node.componentType || it.repetition != node.repetition ||
                        it.sourceSetupExpanded != node.sourceSetupExpanded || it.ownedMarkupPath != node.ownedMarkupPath ||
                        it.diagnostics.map { d -> d.code to d.message }.toSet() != node.diagnostics.map { d -> d.code to d.message }.toSet() })
                    throw Withheld("Constructor entries declare different component types or scope contracts.")
                val origins = alternatives.flatMap { it.idLocations() }.distinct()
                val computed = alternatives.flatMap { candidate -> candidate.idLocations().filter(candidate::isComputedIdOrigin) }.distinct()
                node.copy(template = true, idRenameSafe = false,
                    alternativeJavaLocations = alternatives.flatMap { listOf(it.javaLocation) + it.alternativeJavaLocations }.distinct() - node.javaLocation,
                    alternativeIdLocations = origins - listOfNotNull(node.idLocation),
                    computedId = computed.isNotEmpty(), computedIdLocations = computed,
                    branchMerged = false, conditional = false)
            }
            val root = initial.nodes.single { it.nodeId == initial.rootNodeId }.copy(template = true, idRenameSafe = false)
            val warning = Diagnostic(DiagnosticCode.UNRESOLVED_SCOPE,
                "Base-layout declaration template: all ${constructors.size} constructor entries agree under the declared base implementations. Runtime constructor selection, subclass overrides, extension contents, model values/callbacks and later lifecycle effects remain unknown.", root.javaLocation)
            return initial.copy(nodes = listOf(root) + merged, warnings = listOf(warning) + paths.flatMap { it.warnings }.distinct(),
                scopeComplete = false, javaSourceFiles = sources.sorted(), baseLayoutTemplate = BaseLayoutTemplate(constructors.map { ctor ->
                    LayoutConstructorSource(ctor?.parameterList?.parameters?.joinToString(prefix = "(", postfix = ")") { it.type.canonicalText } ?: "()",
                        location(ctor ?: type))
                }, slots.map { it.location }))
        } catch (withheld: Withheld) {
            return initial.copy(javaSourceFiles = sources.sorted(), warnings = initial.warnings + Diagnostic(DiagnosticCode.UNRESOLVED_SCOPE,
                "Base-layout template withheld: ${withheld.message}", location(type)))
        }
    }
}
private fun location(at: PsiElement) = SourceLocation(at.containingFile.virtualFile.path, at.textOffset)
