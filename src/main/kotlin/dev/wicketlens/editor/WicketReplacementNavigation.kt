package dev.wicketlens.editor

import com.intellij.openapi.editor.Editor
import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler
import com.intellij.psi.*
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.xml.XmlAttributeValue
import dev.wicketlens.model.SourceLocation

/** Navigation from constructor new tokens or factory arrows to replacement slots, with no editing authority. */
internal object WicketReplacementNavigation {
    fun targets(element: PsiElement): List<PsiElement> = WicketEditorAnalysis.navigationPages(element.containingFile).flatMap { page ->
        val factories = mutableListOf<Pair<SourceLocation, String>>()
        page.report.lifecycle?.let { factories += it.factoryLocation to it.factoryNodeId }
        page.report.submissionTemplate?.let { submission ->
            if (submission.contentConstructionLocation != null && submission.contentNodeId != null) factories += submission.contentConstructionLocation to submission.contentNodeId
        }
        page.report.toolbarTemplate?.let { factories += it.factoryLocation to it.contentNodeId }
        page.report.nodes.forEach { node -> node.registeredListPopulation?.let { factories += it.callbackLocation to node.nodeId } }
        factories.flatMap factoryLoop@ { (factory, contentId) ->
            val content = page.report.matching?.findings?.singleOrNull { it.javaNodeId == contentId } ?: return@factoryLoop emptyList()
            val markup = page.report.markup?.elements?.singleOrNull { it.nodeId == content.markupNodeId } ?: return@factoryLoop emptyList()
            val attribute = WicketIdPsi.markupValue(element.project, markup) ?: return@factoryLoop emptyList()
            when {
                (element is PsiMethodReferenceExpression || element is PsiLambdaExpression || element is PsiNewExpression && (page.report.lifecycle?.directContent!=null || page.report.submissionTemplate?.contentNodeId!=null)) && factory == SourceLocation(element.containingFile.virtualFile.path, element.textOffset) -> listOf(attribute)
                element is XmlAttributeValue && element == attribute -> {
                    val file = WicketIdPsi.source(element.project, factory.file) ?: return@factoryLoop emptyList()
                    listOfNotNull(generateSequence(file.findElementAt(factory.offset)) { it.parent }.firstOrNull { it is PsiMethodReferenceExpression || it is PsiLambdaExpression || it is PsiNewExpression && (page.report.lifecycle?.directContent!=null || page.report.submissionTemplate?.contentNodeId!=null) })
                }
                else -> emptyList()
            }
        }
    }.distinct()
}

class WicketReplacementDeclarationHandler : GotoDeclarationHandler {
    override fun getGotoDeclarationTargets(sourceElement: PsiElement?, offset: Int, editor: Editor): Array<PsiElement>? {
        val reference = generateSequence(sourceElement) { it.parent }.firstOrNull { it is PsiMethodReferenceExpression || it is PsiLambdaExpression || it is PsiNewExpression } as? PsiExpression ?: return null
        if (!WicketEditorAnalysis.supports(reference.containingFile)) return null
        when (reference) {
            is PsiMethodReferenceExpression -> if (!reference.text.endsWith("::new") || offset < reference.textRange.endOffset - 3) return null
            is PsiLambdaExpression -> if (reference.node.findChildByType(JavaTokenType.ARROW)?.textRange?.containsOffset(offset) != true) return null
            is PsiNewExpression -> if(reference.node.findChildByType(JavaTokenType.NEW_KEYWORD)?.textRange?.containsOffset(offset)!=true) return null
        }
        return WicketReplacementNavigation.targets(reference).takeIf { it.isNotEmpty() }?.toTypedArray()
    }
}
