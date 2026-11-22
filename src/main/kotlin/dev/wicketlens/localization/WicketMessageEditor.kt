package dev.wicketlens.localization

import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.lookup.AutoCompletionPolicy
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler
import com.intellij.icons.AllIcons
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiLiteralExpression
import com.intellij.psi.xml.XmlAttribute
import dev.wicketlens.editor.WicketIdPsi
import dev.wicketlens.editor.WicketSourceNavigationItem

class WicketMessageCompletionContributor : CompletionContributor() {
    override fun fillCompletionVariants(parameters: CompletionParameters, result: CompletionResultSet) {
        if (parameters.completionType != CompletionType.BASIC) return
        val file = parameters.originalFile
        val offset = parameters.offset
        val element = WicketIdPsi.valueAt(file, offset) ?: return
        val use = WicketMessageSources.use(element, offset) ?: return
        val range = use.range
        if (!range.containsOffset(offset)) return
        val java = element is PsiLiteralExpression
        val attributeMapping = (element.parent as? XmlAttribute)?.name == "wicket:message"
        val prefix = file.text.substring(range.startOffset, offset)
        val suffix = file.text.substring(offset, range.endOffset)
        val matcher = result.withPrefixMatcher(if (java) StringUtil.unescapeStringCharacters(prefix) else StringUtil.unescapeXmlEntities(prefix))

        val keys = linkedMapOf<String, MutableSet<String>>()
        for (bundle in WicketMessageSources.bundles(use.owner)) {
            for (property in bundle.properties) {
                ProgressManager.checkCanceled()
                val key = property.unescapedKey ?: continue
                if (key.isEmpty() || !matcher.prefixMatcher.prefixMatches(key)) continue
                if (attributeMapping && (key.last().isWhitespace() || key.any { it == ',' || it == ':' })) continue
                if (key !in keys && keys.size >= MAX_KEYS) continue
                keys.getOrPut(key) { linkedSetOf() } += bundle.name
            }
        }
        for ((key, bundles) in keys) {
            val names = bundles.take(3).joinToString(", ") + if (bundles.size > 3) ", …" else ""
            val lookup = LookupElementBuilder.create(key).withIcon(AllIcons.Nodes.ResourceBundle)
                .withTypeText("Wicket message source").withTailText("  $names", true)
                .withInsertHandler { context, _ ->
                    val encoded = if (java) StringUtil.escapeStringCharacters(key) else StringUtil.escapeXmlEntities(key)
                    // Replace any suffix the completion engine left behind inside the original value.
                    val end = context.tailOffset
                    val text = context.document.charsSequence
                    val after = if (end + suffix.length <= text.length && text.subSequence(end, end + suffix.length).toString() == suffix)
                        end + suffix.length else end
                    context.document.replaceString(range.startOffset, after, encoded)
                    context.tailOffset = range.startOffset + encoded.length
                    context.editor.caretModel.moveToOffset(context.tailOffset)
                }
            val item = AutoCompletionPolicy.NEVER_AUTOCOMPLETE.applyPolicy(lookup)
            item.putUserData(CANDIDATE, true)
            matcher.addElement(item)
        }
    }

    companion object {
        private const val MAX_KEYS = 256
        internal val CANDIDATE = Key.create<Boolean>("wicketlens.message.completion")
    }
}

/** Candidate targets participate in native navigation without establishing refactoring bindings. */
class WicketMessageDeclarationHandler : GotoDeclarationHandler {
    override fun getGotoDeclarationTargets(sourceElement: PsiElement?, offset: Int, editor: Editor): Array<PsiElement>? {
        val file = sourceElement?.containingFile ?: return null
        val element = WicketIdPsi.valueAt(file, offset) ?: return null
        val use = WicketMessageSources.use(element, offset) ?: return null
        if (!use.range.containsOffset(offset)) return null
        if (use.key.isEmpty()) return null

        val targets = declarations(use)
        if (targets.size <= 1) return targets.takeIf { it.isNotEmpty() }?.toTypedArray()
        return targets.map { target ->
            val path = target.containingFile.virtualFile.path
            val base = target.project.basePath
            val location = if (base == null) path else path.removePrefix("$base/")
            WicketSourceNavigationItem(target, use.key, location, AllIcons.Nodes.ResourceBundle)
        }.toTypedArray()
    }

    internal fun declarations(use: WicketMessageSources.Use): List<PsiElement> = buildList {
        for (bundle in WicketMessageSources.bundles(use.owner)) {
            ProgressManager.checkCanceled()
            for (property in bundle.findPropertiesByKey(use.key)) {
                ProgressManager.checkCanceled()
                if (size >= MAX_TARGETS) return emptyList()
                add(property.psiElement)
            }
        }
    }

    companion object {
        private const val MAX_TARGETS = 64
    }
}
