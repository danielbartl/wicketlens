package dev.wicketlens.editor

import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.psi.*
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.xml.XmlAttribute
import com.intellij.psi.xml.XmlAttributeValue
import dev.wicketlens.analysis.WicketIdConstructors
import dev.wicketlens.model.*

internal data class WicketIdPair(val page: EditorPage, val javaNodeId: String, val markupNodeId: String,
    val literal: PsiLiteralExpression, val attribute: XmlAttributeValue)

internal object WicketIdPsi {
    fun valueAt(file: PsiFile, offset: Int): PsiElement? {
        if (file.textLength == 0) return null
        val leaf = file.findElementAt(offset.coerceIn(0, file.textLength - 1)) ?: return null
        return PsiTreeUtil.getParentOfType(leaf, PsiLiteralExpression::class.java, false)
            ?: PsiTreeUtil.getParentOfType(leaf, XmlAttributeValue::class.java, false)
    }

    fun javaId(element: PsiElement): PsiLiteralExpression? {
        val literal = element as? PsiLiteralExpression ?: return null
        if (literal.value !is String || DumbService.isDumb(literal.project)) return null
        val arguments = literal.parent as? PsiExpressionList
        val creation = arguments?.parent as? PsiNewExpression
        if (creation != null && WicketIdConstructors.trace(creation)?.expression == literal) return literal
        // Proven origins may also be selected ternary operands or immutable field
        // initializers, rather than immediate constructor/call arguments.
        return literal.takeIf { WicketEditorAnalysis.pages(literal.containingFile).any { page ->
            page.report.nodes.any { node -> !node.computedId && node.idLocation == SourceLocation(literal.containingFile.virtualFile.path, literal.textOffset) && node.wicketId == literal.value }
        } }
    }

    fun htmlId(element: PsiElement): XmlAttributeValue? {
        val value = element as? XmlAttributeValue ?: return null
        val attribute = value.parent as? XmlAttribute ?: return null
        return value.takeIf { attribute.name == "wicket:id" && attribute.parent.attributes.count { it.name == "wicket:id" } == 1 }
    }

    fun javaLiteral(project: Project, node: ComponentNode): PsiLiteralExpression? {
        if (node.computedId) return null
        node.idLocation?.let { location ->
            val file = source(project, location.file) ?: return null
            val literal = PsiTreeUtil.getParentOfType(file.findElementAt(location.offset), PsiLiteralExpression::class.java, false)
            return literal?.takeIf { it.value == node.wicketId }
        }
        val file = source(project, node.javaLocation.file) ?: return null
        val creation = PsiTreeUtil.getParentOfType(file.findElementAt(node.javaLocation.offset), PsiNewExpression::class.java, false)
            ?: return null
        val literal = creation.argumentList?.expressions?.firstOrNull() as? PsiLiteralExpression ?: return null
        return literal.takeIf { it.value == node.wicketId && javaId(it) != null }
    }

    fun javaIdElement(project: Project, node: ComponentNode): PsiExpression? {
        if (!node.computedId) return javaLiteral(project, node)
        val location = node.idLocation ?: return null
        val file = source(project, location.file) ?: return null
        val leaf = file.findElementAt(location.offset) ?: return null
        return generateSequence(leaf) { it.parent }.filterIsInstance<PsiExpression>()
            .filter { it.textOffset == location.offset }.lastOrNull()
    }

    fun markupValue(project: Project, node: MarkupElement): XmlAttributeValue? {
        val location = node.wicketIdLocation ?: return null
        val file = source(project, location.file) ?: return null
        val attribute = PsiTreeUtil.getParentOfType(file.findElementAt(location.offset), XmlAttribute::class.java, false)
        return attribute?.valueElement?.let(::htmlId)
    }

    fun pair(element: PsiElement): WicketIdPair? {
        if (!(element is PsiLiteralExpression && element.value is String) && htmlId(element) == null) return null
        val project = element.project
        val pairs = WicketEditorAnalysis.pages(element.containingFile).flatMap { page ->
            page.report.matching?.findings.orEmpty().filter { it.code == DiagnosticCode.MATCHED }.mapNotNull { finding ->
                val java = page.report.nodes.singleOrNull { it.nodeId == finding.javaNodeId } ?: return@mapNotNull null
                val html = page.report.markup?.elements?.singleOrNull { it.nodeId == finding.markupNodeId } ?: return@mapNotNull null
                val literal = javaLiteral(project, java) ?: return@mapNotNull null
                val attribute = markupValue(project, html) ?: return@mapNotNull null
                if (element != literal && element != attribute) return@mapNotNull null
                WicketIdPair(page, java.nodeId, html.nodeId, literal, attribute)
            }
        }
        return pairs.singleOrNull()
    }

    fun source(project: Project, path: String): PsiFile? {
        val file = dev.wicketlens.analysis.WicketSourceFiles.find(project, path)
        return file?.let { PsiManager.getInstance(project).findFile(it) }
    }

    fun javaPath(report: PageReport, nodeId: String?): List<String>? {
        if (nodeId == report.rootNodeId) return emptyList()
        val path = mutableListOf<String>()
        val seen = mutableSetOf<String>()
        var current = nodeId
        while (current != report.rootNodeId) {
            if (current == null || !seen.add(current)) return null
            val node = report.nodes.singleOrNull { it.nodeId == current } ?: return null
            if (node.templateItem) {
                if (node.confidence != Confidence.CONFIRMED) return null
                current = node.parentNodeId
                continue
            }
            val id = node.wicketId ?: return null
            if (node.confidence != Confidence.CONFIRMED || report.nodes.count { !it.templateItem && it.parentNodeId == node.parentNodeId && it.wicketId == id } != 1) return null
            path.add(0, id)
            current = node.parentNodeId
        }
        return path
    }

    fun markupPath(report: PageReport, nodeId: String?): List<String>? {
        val path = mutableListOf<String>()
        val seen = mutableSetOf<String>()
        var current = nodeId
        val nodes = report.markup?.elements.orEmpty().filter { it.participatesInComponentTree && it.hasWicketId }
        while (current != null) {
            if (!seen.add(current)) return null
            val node = nodes.singleOrNull { it.nodeId == current } ?: return null
            val id = node.wicketId ?: return null
            if (node.confidence != Confidence.CONFIRMED || nodes.count { it.parentWicketElementId == node.parentWicketElementId && it.wicketId == id } != 1) return null
            path.add(0, id)
            current = node.parentWicketElementId
        }
        return path
    }
}
