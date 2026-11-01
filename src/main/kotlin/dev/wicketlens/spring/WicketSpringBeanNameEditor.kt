package dev.wicketlens.spring

import com.intellij.codeInsight.completion.*
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.icons.AllIcons
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.text.StringUtil
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.*
import com.intellij.util.ProcessingContext

/** Soft, polyvariant source navigation; it does not assert that a declaration is active in a runtime context. */
class WicketSpringBeanReferenceContributor : PsiReferenceContributor() {
    override fun registerReferenceProviders(registrar: PsiReferenceRegistrar) {
        registrar.registerReferenceProvider(PlatformPatterns.psiElement(PsiLiteralExpression::class.java), object : PsiReferenceProvider() {
            override fun getReferencesByElement(element: PsiElement, context: ProcessingContext): Array<PsiReference> =
                if (WicketSpringBeanNames.use(element) == null) PsiReference.EMPTY_ARRAY else arrayOf(WicketSpringBeanReference(element as PsiLiteralExpression))
        })
    }
}

internal class WicketSpringBeanReference(element: PsiLiteralExpression) :
    PsiPolyVariantReferenceBase<PsiLiteralExpression>(element, ElementManipulators.getValueTextRange(element), true) {
    override fun multiResolve(incompleteCode: Boolean): Array<ResolveResult> {
        val use = WicketSpringBeanNames.use(element) ?: return ResolveResult.EMPTY_ARRAY
        val name = use.literal.value as? String ?: return ResolveResult.EMPTY_ARRAY
        if (name.isEmpty()) return ResolveResult.EMPTY_ARRAY
        return WicketSpringBeanNames.catalog(element.containingFile).beans.filter { it.name == name }
            .mapNotNull { WicketSpringBeanNames.target(it, element.containingFile) }.distinct().map { PsiElementResolveResult(it) }.toTypedArray()
    }
    override fun getVariants(): Array<Any> = emptyArray()
    override fun isReferenceTo(element: PsiElement): Boolean = false
    // Java declaration rename must not silently rewrite aliases or explicit Spring bean names.
    override fun handleElementRename(newElementName: String): PsiElement = element
    override fun bindToElement(element: PsiElement): PsiElement = this.element
}

class WicketSpringBeanCompletionContributor : CompletionContributor() {
    override fun fillCompletionVariants(parameters: CompletionParameters, result: CompletionResultSet) {
        if (parameters.completionType != CompletionType.BASIC) return
        val file = parameters.originalFile
        val use = WicketSpringBeanNames.at(file, parameters.offset) ?: return
        val range = ElementManipulators.getValueTextRange(use.literal).shiftRight(use.literal.textRange.startOffset)
        if (parameters.offset !in range.startOffset..range.endOffset) return
        val prefix = StringUtil.unescapeStringCharacters(file.text.substring(range.startOffset, parameters.offset))
        val remaining = range.endOffset - parameters.offset
        val catalog = WicketSpringBeanNames.catalog(file)
        catalog.beans.filter { WicketSpringBeanNames.compatible(it, use.field) }.groupBy { it.name }.toSortedMap().forEach { (name, beans) ->
            val owners = beans.map { it.owner }.distinct().joinToString(", ")
            val metadata = beans.map(WicketSpringBeanNames::description).filter(String::isNotEmpty).distinct().joinToString("; ")
            val lookup = LookupElementBuilder.create(name).withIcon(AllIcons.Nodes.Method).withTypeText("Spring bean source")
                .withTailText(" ($owners; ${if (metadata.isEmpty()) "" else "$metadata; "}context not verified)", true).withInsertHandler { context, _ ->
                    val encoded = StringUtil.escapeStringCharacters(name)
                    context.document.replaceString(range.startOffset, context.tailOffset + remaining, encoded)
                    context.tailOffset = range.startOffset + encoded.length
                    context.editor.caretModel.moveToOffset(context.tailOffset)
                }
            lookup.putUserData(CANDIDATE, true)
            result.withPrefixMatcher(prefix).addElement(lookup)
        }
    }
    companion object { internal val CANDIDATE = Key.create<Boolean>("wicketlens.spring.bean.completion") }
}
