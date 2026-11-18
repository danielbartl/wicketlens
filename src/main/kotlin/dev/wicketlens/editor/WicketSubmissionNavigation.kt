package dev.wicketlens.editor

import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.progress.ProgressManager
import dev.wicketlens.analysis.WicketConfirmationContexts
import com.intellij.psi.*
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.xml.XmlAttributeValue
import dev.wicketlens.model.SourceLocation

/** Hook-name/trigger navigation only; ordinary Java variables and framework methods keep their targets. */
internal object WicketSubmissionNavigation {
    fun targets(element: PsiElement): List<PsiElement> {
        if (element !is PsiMethod && element !is XmlAttributeValue) return emptyList()
        val local = WicketEditorAnalysis.navigationPages(element.containingFile)
        // A button in an inherited resource may be below a stored-consumer host. Use that
        // host's independently bounded cached query, without enlarging general caller discovery.
        val confirmations = mutableListOf<EditorPage>()
        if (element is XmlAttributeValue) {
            val hosts = local.distinctBy { it.report.qualifiedClassName }.mapNotNull { entry ->
                ProgressManager.checkCanceled()
                val source = WicketIdPsi.source(element.project, entry.report.nodes.single { it.nodeId == entry.report.rootNodeId }.javaLocation.file) as? PsiJavaFile
                    ?: return@mapNotNull null
                val type = dev.wicketlens.analysis.WicketSourceClassNames.members(source).classes.singleOrNull { it.qualifiedName == entry.report.qualifiedClassName }
                    ?: return@mapNotNull null
                source.takeIf { WicketConfirmationContexts.hasConsumerDeclaration(type) }
            }.distinct()
            if (hosts.size <= 8) hosts.forEach { source ->
                ProgressManager.checkCanceled()
                confirmations += WicketEditorAnalysis.navigationPages(source).filter { it.report.submissionTemplate?.confirmation != null }
            }
            val distinct = confirmations.distinctBy { it.report.submissionTemplate!!.contextId }
            if (distinct.sumOf { it.report.nodes.size } > 256 || distinct.flatMap { it.report.javaSourceFiles + it.report.markup?.sourceFiles.orEmpty() }.distinct().size > 512)
                confirmations.clear()
        }
        return (local + confirmations).distinctBy { it.report.qualifiedClassName to it.report.submissionTemplate?.contextId }.flatMap { context ->
            val submission = context.report.submissionTemplate ?: return@flatMap emptyList()
            val finding = context.report.matching!!.findings.single { it.javaNodeId == submission.triggerNodeId }
            val markup = context.report.markup!!.elements.single { it.nodeId == finding.markupNodeId }
            val trigger = WicketIdPsi.markupValue(element.project, markup) ?: return@flatMap emptyList()
            when {
                element is PsiMethod && (submission.confirmation?.hookLocation ?: submission.callbackLocation) == SourceLocation(element.containingFile.virtualFile.path, element.textOffset) -> listOf(trigger)
                element == trigger -> {
                    val source = WicketIdPsi.source(element.project, submission.callbackLocation.file) ?: return@flatMap emptyList()
                    listOfNotNull(source.findElementAt(submission.callbackLocation.offset)?.let { leaf ->
                        if (submission.confirmation != null) PsiTreeUtil.getParentOfType(leaf, PsiLambdaExpression::class.java, false)?.parameterList
                        else PsiTreeUtil.getParentOfType(leaf, PsiMethod::class.java, false)?.nameIdentifier
                    })
                }
                else -> emptyList()
            }
        }.distinct()
    }
}

class WicketSubmissionDeclarationHandler : GotoDeclarationHandler {
    override fun getGotoDeclarationTargets(sourceElement: PsiElement?, offset: Int, editor: Editor): Array<PsiElement>? {
        val method = PsiTreeUtil.getParentOfType(sourceElement, PsiMethod::class.java, false) ?: return null
        if (method.nameIdentifier?.textRange?.containsOffset(offset) != true || !WicketEditorAnalysis.supports(method.containingFile)) return null
        return WicketSubmissionNavigation.targets(method).takeIf { it.isNotEmpty() }?.toTypedArray()
    }
}
