package dev.wicketlens.editor

import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.progress.ProgressManager
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiCompiledElement
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiLiteralExpression
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.ClassInheritorsSearch
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.xml.XmlAttributeValue
import com.intellij.util.Processor
import dev.wicketlens.analysis.WicketPageAnalyzer
import dev.wicketlens.analysis.WicketPageResolver
import dev.wicketlens.analysis.WicketSourceClassNames
import dev.wicketlens.analysis.WicketSourceResources
import dev.wicketlens.model.DiagnosticCode
import dev.wicketlens.model.MarkupResolutionStatus

internal data class WicketIdRenamePlan(
    val literal: PsiLiteralExpression,
    val pairs: List<WicketIdPair>,
    val properties: List<WicketIdPropertyEdit>,
) {
    val contexts get() = pairs.map { it.page.sourceUrl to it.page.report.markupSuffix }.toSet()
    val attributes: List<XmlAttributeValue> get() = pairs.map { it.attribute }.distinct()
    fun writable() = literal.isWritable && attributes.all { it.isWritable } && properties.all { it.element.isWritable }
}

internal object WicketIdRenameScopes {
    private const val MAX_CONTEXTS = 24

    /** Collect all conventional markup contexts using the same Java literal before allowing any writes. */
    fun collect(pair: WicketIdPair, owner: PsiClass): WicketIdRenamePlan? {
        val origin = PsiTreeUtil.getParentOfType(pair.literal, PsiClass::class.java) ?: return null
        if (origin.containingClass != null && origin != owner) return null
        val originResources = WicketSourceResources.forOwner(origin)?.takeUnless { it.hasCustomMapping } ?: return null
        val types = linkedSetOf(origin)
        val complete = ClassInheritorsSearch.search(origin, GlobalSearchScope.projectScope(origin.project), true).forEach(Processor { child ->
            ProgressManager.checkCanceled()
            if (child is PsiCompiledElement || types.size >= MAX_CONTEXTS) false else {
                types += child
                true
            }
        })
        if (!complete || owner !in types) return null
        if (types.size > 1 && origin.containingClass != null) return null

        val byType = linkedMapOf<PsiClass, List<String>>()
        val ownedMarkup = mutableSetOf<String>()
        val allSuffixes = linkedSetOf("")
        for (type in types) {
            ProgressManager.checkCanceled()
            val resources = WicketSourceResources.forOwner(type)?.takeUnless { it.hasCustomMapping } ?: return null
            // A consumer in another module or source set can have different classpath overrides.
            if (!resources.sameScope(originResources)) return null
            val basename = WicketSourceClassNames.binaryBasename(type) ?: return null
            val (resolution, variants) = WicketPageResolver().resolveWithVariants(type)
            if (resolution.status in setOf(MarkupResolutionStatus.AMBIGUOUS, MarkupResolutionStatus.UNSUPPORTED)) return null
            resolution.markupPath?.let { ownedMarkup += it }
            ownedMarkup += variants.map { it.path }
            if (variants.size != resolution.relatedVariantPaths.size) return null
            val suffixes = variants.map { it.nameWithoutExtension.removePrefix(basename) }
            if (suffixes.toSet().size != suffixes.size) return null
            byType[type] = suffixes
            allSuffixes += suffixes
            if (types.size * allSuffixes.size > MAX_CONTEXTS) return null
            for (variant in variants) {
                val owners = resources.owners(variant.nameWithoutExtension, markup = true) ?: return null
                if (owners != listOf(type)) return null
            }
        }

        val pairs = mutableListOf<WicketIdPair>()
        val propertyPaths = linkedMapOf<PsiClass, MutableSet<List<String>>>()
        for ((type, suffixes) in byType) for (suffix in allSuffixes) {
            ProgressManager.checkCanceled()
            // A runtime fallback to the default is already represented by its own analysis.
            val resolution = WicketPageResolver().resolve(type, suffix)
            if (suffix.isNotEmpty() && resolution.markupPath == null && suffix !in suffixes &&
                resolution.candidatePaths.isEmpty()) continue
            val report = WicketPageAnalyzer().analyze(type, suffix) ?: return null
            val location = dev.wicketlens.model.SourceLocation(pair.literal.containingFile.virtualFile.path, pair.literal.textOffset)
            val node = report.nodes.singleOrNull { location in it.idLocations() && it.wicketId == pair.literal.value }
                ?: return null
            val finding = report.matching?.findings?.singleOrNull { it.code == DiagnosticCode.MATCHED && it.javaNodeId == node.nodeId }
                ?: return null
            val markup = report.markup?.elements?.singleOrNull { it.nodeId == finding.markupNodeId } ?: return null
            val attribute = WicketIdPsi.markupValue(owner.project, markup) ?: return null
            if (attribute.containingFile.virtualFile.path !in ownedMarkup) return null
            val current = WicketIdPair(EditorPage(report, type.containingFile.virtualFile.url, type.textOffset),
                node.nodeId, markup.nodeId, pair.literal, attribute)
            if (!WicketIdRenameTarget.safeBinding(current, requireOwnedLiteral = type == origin)) return null
            val path = WicketIdPsi.javaPath(report, node.nodeId) ?: return null
            propertyPaths.getOrPut(type) { linkedSetOf() } += path
            pairs += current
        }
        if (pairs.none { it.attribute == pair.attribute }) return null
        val propertiesEnabled = PluginManagerCore.getPlugin(PluginId.getId("com.intellij.properties"))?.isEnabled == true
        val bundles = linkedMapOf<PsiClass, List<com.intellij.openapi.vfs.VirtualFile>>()
        for (type in propertyPaths.keys) {
            val basename = WicketSourceClassNames.binaryBasename(type) ?: return null
            val resources = WicketSourceResources.forOwner(type) ?: return null
            val files = resources.files(basename, setOf("properties"), 32) ?: return null
            if (files.isNotEmpty()) bundles[type] = files
        }
        val properties = if (bundles.isEmpty()) emptyList() else {
            if (!propertiesEnabled) return null
            WicketIdPropertyEdits.collect(propertyPaths, bundles) ?: return null
        }
        return WicketIdRenamePlan(pair.literal, pairs, properties)
    }
}

/** Kept independent of Properties PSI so Wicket ID rename works without that plugin. */
internal interface WicketIdPropertyEdit {
    val element: PsiElement
    fun conflict(newId: String): String?
    fun rename(newId: String)
}
