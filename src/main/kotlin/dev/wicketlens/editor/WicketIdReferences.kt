package dev.wicketlens.editor

import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.*
import com.intellij.psi.xml.XmlAttributeValue
import com.intellij.util.ProcessingContext

/** Soft references add navigation without declaring unresolved IDs to be language errors. */
class WicketIdReferenceContributor : PsiReferenceContributor() {
    override fun registerReferenceProviders(registrar: PsiReferenceRegistrar) {
        val provider = object : PsiReferenceProvider() {
            override fun getReferencesByElement(element: PsiElement, context: ProcessingContext): Array<PsiReference> {
                if (WicketIdPsi.javaId(element) == null && WicketIdPsi.htmlId(element) == null) return PsiReference.EMPTY_ARRAY
                return arrayOf(WicketIdReference(element))
            }
        }
        registrar.registerReferenceProvider(PlatformPatterns.psiElement(PsiLiteralExpression::class.java), provider)
        registrar.registerReferenceProvider(PlatformPatterns.psiElement(XmlAttributeValue::class.java), provider)
    }
}

internal class WicketIdReference(element: PsiElement) : PsiReferenceBase<PsiElement>(element, ElementManipulators.getValueTextRange(element), true) {
    override fun resolve(): PsiElement? {
        val pair = WicketIdPsi.pair(element) ?: return null
        return if (element is PsiLiteralExpression) pair.attribute else pair.literal
    }

    override fun getVariants(): Array<Any> = emptyArray()

    override fun isReferenceTo(target: PsiElement): Boolean =
        if (target is WicketIdRenameTarget) target.literal == WicketIdPsi.pair(element)?.literal
        else super.isReferenceTo(target)
}
