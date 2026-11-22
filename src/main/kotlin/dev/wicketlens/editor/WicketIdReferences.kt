package dev.wicketlens.editor

import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler
import com.intellij.icons.AllIcons
import com.intellij.openapi.editor.Editor
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.*
import com.intellij.psi.impl.FakePsiElement
import com.intellij.psi.xml.XmlAttributeValue
import com.intellij.util.ProcessingContext
import javax.swing.Icon

/** Soft references add navigation without declaring unresolved IDs to be language errors. */
class WicketIdReferenceContributor : PsiReferenceContributor() {
    override fun registerReferenceProviders(registrar: PsiReferenceRegistrar) {
        val provider = object : PsiReferenceProvider() {
            override fun getReferencesByElement(element: PsiElement, context: ProcessingContext): Array<PsiReference> {
                if (!WicketFragmentNavigation.isDefinition(element) && WicketIdPsi.javaNavigationId(element) == null && WicketIdPsi.htmlId(element) == null) return PsiReference.EMPTY_ARRAY
                return arrayOf(WicketIdReference(element))
            }
        }
        registrar.registerReferenceProvider(PlatformPatterns.psiElement(PsiLiteralExpression::class.java), provider)
        registrar.registerReferenceProvider(PlatformPatterns.psiElement(XmlAttributeValue::class.java), provider)
    }
}

internal class WicketIdReference(element: PsiElement) : PsiPolyVariantReferenceBase<PsiElement>(element, ElementManipulators.getValueTextRange(element), true) {
    override fun multiResolve(incompleteCode: Boolean): Array<ResolveResult> {
        val targets = (WicketIdPsi.baseLayoutTargets(element) + WicketFragmentNavigation.targets(element) + WicketReplacementNavigation.targets(element) + WicketTreeNavigation.targets(element) + WicketFormNavigation.targets(element) + WicketSubmissionNavigation.targets(element) +
            WicketIdPsi.pairs(element).map { if (element is PsiLiteralExpression) it.attribute else it.literal }).distinct()
        return targets.map { PsiElementResolveResult(it) }.toTypedArray()
    }

    override fun getVariants(): Array<Any> = emptyArray()

    override fun isReferenceTo(target: PsiElement): Boolean =
        if (target is WicketIdRenameTarget) target.literal == WicketIdPsi.pair(element)?.literal
        else super.isReferenceTo(target)
}

/** Possible targets participate only in Go to Declaration, outside reference resolution and refactoring. */
class WicketCompanionDeclarationHandler : GotoDeclarationHandler {
    override fun getGotoDeclarationTargets(sourceElement: PsiElement?, offset: Int, editor: Editor): Array<PsiElement>? {
        val file = sourceElement?.containingFile ?: return null
        val value = WicketIdPsi.valueAt(file, offset) ?: return null
        if (!ElementManipulators.getValueTextRange(value).shiftRight(value.textRange.startOffset).containsOffset(offset)) return null
        val candidates = WicketCompanionIds.targets(value)
        if (candidates.isEmpty() || WicketIdReference(value).multiResolve(false).isNotEmpty()) return null
        if (candidates.size == 1) return candidates.toTypedArray()
        return candidates.map { candidate ->
            val (preview, scope) = WicketCompanionIds.presentation(candidate) ?: return@map candidate
            val document = PsiDocumentManager.getInstance(candidate.project).getDocument(candidate.containingFile)
            val line = document?.getLineNumber(candidate.textOffset)?.plus(1)
            val location = if (line == null) scope else "$scope · line $line"
            WicketSourceNavigationItem(candidate, preview, location)
        }.toTypedArray()
    }
}

/** The native chooser reads the returned item's presentation and navigates to its backing PSI. */
internal class WicketSourceNavigationItem(
    target: PsiElement,
    private val preview: String,
    private val location: String,
    private val icon: Icon = AllIcons.Nodes.Tag,
) : FakePsiElement() {
    private val pointer = SmartPointerManager.createPointer(target)

    override fun getName() = preview
    override fun getLocationString() = location
    override fun getIcon(unused: Boolean) = icon
    override fun getProject() = pointer.project
    override fun getParent() = pointer.element
    override fun getContainingFile() = pointer.containingFile
    override fun getTextRange() = pointer.element?.textRange
    override fun getTextOffset() = pointer.element?.textOffset ?: 0
    override fun getNavigationElement(): PsiElement = pointer.element ?: this
    override fun isWritable() = false
}
