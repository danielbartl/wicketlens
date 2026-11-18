package dev.wicketlens.editor

import com.intellij.psi.*
import com.intellij.psi.xml.XmlAttribute
import com.intellij.psi.xml.XmlAttributeValue
import dev.wicketlens.analysis.WicketFragmentAnalyzer
import dev.wicketlens.model.SourceLocation

/** Definition navigation uses the same value snapshot as matching; it never establishes attachments. */
internal object WicketFragmentNavigation {
    fun isDefinition(element: PsiElement): Boolean {
        if (com.intellij.openapi.project.DumbService.isDumb(element.project)) return false
        return when (element) {
        is PsiLiteralExpression -> {
            val args = element.parent as? PsiExpressionList
            val creation = args?.parent as? PsiNewExpression
            element.value is String && (args?.expressions?.getOrNull(1) == element &&
                creation?.anonymousClass == null && creation?.resolveConstructor()?.containingClass?.qualifiedName == WicketFragmentAnalyzer.TYPE ||
                WicketEditorAnalysis.navigationPages(element.containingFile).any { page -> page.report.fragmentUses.any {
                    it.definitionIdOrigin == SourceLocation(element.containingFile.virtualFile.path, element.textOffset) && it.definitionId == element.value
                } })
        }
        is XmlAttributeValue -> (element.parent as? XmlAttribute)?.let {
            it.name == "wicket:id" && it.parent.name == "wicket:fragment"
        } == true
        else -> false
        }
    }

    fun targets(element: PsiElement): List<PsiElement> {
        val origin = SourceLocation(element.containingFile.virtualFile.path,
            if (element is XmlAttributeValue) element.parent.textOffset else element.textOffset)
        return WicketEditorAnalysis.navigationPages(element.containingFile).flatMap { page ->
            page.report.fragmentUses.mapNotNull { use ->
                val target = when {
                    element is PsiLiteralExpression && use.definitionIdOrigin == origin -> use.definitionIdLocation
                    element is XmlAttributeValue && use.definitionIdLocation == origin -> use.definitionIdOrigin
                    else -> null
                } ?: return@mapNotNull null
                val file = WicketIdPsi.source(element.project, target.file) ?: return@mapNotNull null
                val leaf = file.findElementAt(target.offset) ?: return@mapNotNull null
                generateSequence(leaf) { it.parent }.firstOrNull {
                    if (element is PsiLiteralExpression) it is XmlAttribute else it is PsiLiteralExpression
                }?.let { if (it is XmlAttribute) it.valueElement else it }
            }
        }.distinct()
    }
}
