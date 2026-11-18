package dev.wicketlens.editor

import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler
import com.intellij.openapi.editor.Editor
import com.intellij.psi.*
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.xml.XmlAttributeValue
import dev.wicketlens.model.SourceLocation

/** Factory/slot navigation over the shared form graph, without changing ordinary Java class navigation. */
internal object WicketFormNavigation {
    fun targets(element: PsiElement): List<PsiElement> {
        if (element !is PsiMethod && element !is PsiMethodReferenceExpression && element !is XmlAttributeValue) return emptyList()
        return WicketEditorAnalysis.navigationPages(element.containingFile).flatMap { context ->
            val form = context.report.formTemplate ?: return@flatMap emptyList()
            val binding = context.report.matching?.findings?.singleOrNull { it.javaNodeId == form.inputNodeId }
                ?: return@flatMap emptyList()
            val markup = context.report.markup?.elements?.singleOrNull { it.nodeId == binding.markupNodeId }
                ?: return@flatMap emptyList()
            val slot = WicketIdPsi.markupValue(element.project, markup) ?: return@flatMap emptyList()
            when {
                element is PsiMethod && form.factoryLocation == SourceLocation(element.containingFile.virtualFile.path, element.textOffset) -> listOf(slot)
                element is PsiMethodReferenceExpression && form.registration?.constructorReferenceLocation == SourceLocation(element.containingFile.virtualFile.path, element.textOffset) -> listOf(slot)
                element is XmlAttributeValue && element == slot -> {
                    val source = WicketIdPsi.source(element.project, form.factoryLocation.file) ?: return@flatMap emptyList()
                    val factory = source.findElementAt(form.factoryLocation.offset)?.let { leaf ->
                        generateSequence(leaf) { it.parent }.filterIsInstance<PsiMethod>().firstOrNull()?.nameIdentifier
                    }
                    val registration = form.registration?.constructorReferenceLocation?.let { at ->
                        WicketIdPsi.source(element.project, at.file)?.findElementAt(at.offset)?.let { leaf ->
                            generateSequence(leaf) { it.parent }.filterIsInstance<PsiMethodReferenceExpression>().firstOrNull()
                        }
                    }
                    listOfNotNull(factory, registration)
                }
                else -> emptyList()
            }
        }.distinct()
    }
}
class WicketFormDeclarationHandler : GotoDeclarationHandler {
    override fun getGotoDeclarationTargets(sourceElement: PsiElement?, offset: Int, editor: Editor): Array<PsiElement>? {
        val reference = PsiTreeUtil.getParentOfType(sourceElement, PsiMethodReferenceExpression::class.java, false)
        if (reference != null) {
            if (!reference.text.endsWith("::new") || offset < reference.textRange.endOffset - 3 || !WicketEditorAnalysis.supports(reference.containingFile)) return null
            return WicketFormNavigation.targets(reference).takeIf { it.isNotEmpty() }?.toTypedArray()
        }
        val method = PsiTreeUtil.getParentOfType(sourceElement, PsiMethod::class.java, false) ?: return null
        if (method.nameIdentifier?.textRange?.containsOffset(offset) != true || !WicketEditorAnalysis.supports(method.containingFile)) return null
        return WicketFormNavigation.targets(method).takeIf { it.isNotEmpty() }?.toTypedArray()
    }
}
