package dev.wicketlens.localization

import com.intellij.lang.properties.psi.PropertiesFile
import com.intellij.openapi.util.TextRange
import com.intellij.psi.ElementManipulators
import com.intellij.psi.PsiAnonymousClass
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiExpressionList
import com.intellij.psi.PsiLiteralExpression
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiMethodCallExpression
import com.intellij.psi.PsiNewExpression
import com.intellij.psi.PsiSuperExpression
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.xml.XmlAttribute
import com.intellij.psi.xml.XmlAttributeValue
import dev.wicketlens.analysis.AnalysisUnavailable
import dev.wicketlens.analysis.WicketEditorTargetResolver
import dev.wicketlens.analysis.WicketPageSelection
import dev.wicketlens.analysis.WicketSourceClassNames
import dev.wicketlens.analysis.WicketSourceResources
import dev.wicketlens.editor.WicketEditorAnalysis

/** Read-only source lookup. The active Wicket loader, component path and locale are runtime inputs. */
internal object WicketMessageSources {
    private const val WICKET_COMPONENT = "org.apache.wicket.Component"
    private const val RESOURCE_MODEL = "org.apache.wicket.model.ResourceModel"
    private const val STRING_RESOURCE_MODEL = "org.apache.wicket.model.StringResourceModel"
    const val MAX_BUNDLES = 32
    const val MAX_BUNDLE_BYTES = 512 * 1024L

    data class Use(val element: PsiElement, val owner: PsiClass, val key: String, val range: TextRange = ElementManipulators.getValueTextRange(element).shiftRight(element.textRange.startOffset))

    fun use(element: PsiElement, offset: Int): Use? {
        if (!WicketEditorAnalysis.ready(element.containingFile)) return null
        return when (element) {
            is PsiLiteralExpression -> javaUse(element)
            is XmlAttributeValue -> markupUse(element, offset)
            else -> null
        }
    }

    private fun javaUse(element: PsiLiteralExpression): Use? {
        val key = element.value as? String ?: return null
        if (element.text.startsWith("\"\"\"")) return null
        val arguments = element.parent as? PsiExpressionList ?: return null
        if (arguments.expressions.firstOrNull() != element) return null
        val call = arguments.parent as? PsiMethodCallExpression
        if (call != null) return getStringOwner(call)?.let { Use(element, it, key) }
        val creation = when (val parent = arguments.parent) {
            is PsiNewExpression -> parent
            is PsiAnonymousClass -> parent.parent as? PsiNewExpression
            else -> null
        } ?: return null
        val constructor = creation.resolveConstructor() ?: return null
        val model = constructor.containingClass?.qualifiedName
        if (model != RESOURCE_MODEL && model != STRING_RESOURCE_MODEL) return null

        val componentParameter = model == STRING_RESOURCE_MODEL &&
            constructor.parameterList.parameters.getOrNull(1)?.type?.equalsToText(WICKET_COMPONENT) == true
        val component = arguments.expressions.getOrNull(1)
        val owner = if (componentParameter && component?.text != "null") {
            val type = (component?.type as? PsiClassType)?.resolve() ?: return null
            namedComponent(type)
        } else {
            generateSequence(element.parent) { it.parent }.filterIsInstance<PsiClass>().firstNotNullOfOrNull(::namedComponent)
        } ?: return null
        return Use(element, owner, key)
    }

    private fun getStringOwner(call: PsiMethodCallExpression): PsiClass? {
        val reference = call.methodExpression
        if (reference.referenceName != "getString") return null
        val resolved = call.resolveMethodGenerics()
        if (!resolved.isValidResult || (resolved.element as? PsiMethod)?.containingClass?.qualifiedName != WICKET_COMPONENT) return null

        val receiver = reference.qualifierExpression
        if (receiver is PsiSuperExpression && receiver.qualifier != null) {
            return (receiver.qualifier?.resolve() as? PsiClass)?.let(::namedComponent)
        }
        if (receiver != null && receiver !is PsiSuperExpression) {
            return (receiver.type as? PsiClassType)?.resolve()?.let(::namedComponent)
        }
        // An anonymous instance does not yet supply 'this' inside its own constructor arguments.
        val enclosing = generateSequence(call.parent) { it.parent }.filterIsInstance<PsiClass>().firstOrNull {
            WicketSourceResources.isComponent(it) &&
                !(it is PsiAnonymousClass && PsiTreeUtil.isAncestor(it.argumentList, call, false))
        }
        return enclosing?.let(::namedComponent)
    }

    private fun namedComponent(type: PsiClass): PsiClass? =
        type.takeIf { WicketSourceClassNames.binaryBasename(it) != null && WicketSourceResources.isComponent(it) }

    private fun markupUse(element: XmlAttributeValue, offset: Int): Use? {
        val attribute = element.parent as? XmlAttribute ?: return null
        val tag = attribute.parent
        if (tag.attributes.count { it.name == attribute.name } != 1) return null
        if (tag.name == "wicket:message" && attribute.name == "key") {
            return Use(element, markupOwner(element.containingFile) ?: return null, attribute.displayValue ?: return null)
        }
        if (attribute.name != "wicket:message") return null
        val value = attribute.displayValue ?: return null
        val start = element.textRange.startOffset + ElementManipulators.getValueTextRange(element).startOffset
        val relative = attribute.physicalToDisplay(offset - start)
        if (relative !in 0..value.length) return null
        val from = if (relative == 0) 0 else value.lastIndexOf(',', relative - 1) + 1
        val to = value.indexOf(',', relative).takeIf { it >= 0 } ?: value.length
        val separator = value.lastIndexOf(':', to - 1)
        if (separator <= from || relative <= separator) return null
        // Wicket trims the whole list, preserving whitespace inside individual mappings.
        val keyEnd = if (to == value.length) value.trimEnd().length.coerceAtLeast(separator + 1) else to
        if (relative > keyEnd) return null
        val range = TextRange(start + attribute.displayToPhysical(separator + 1), start + attribute.displayToPhysical(keyEnd))
        val owner = markupOwner(element.containingFile) ?: return null
        return Use(element, owner, value.substring(separator + 1, keyEnd), range)
    }

    fun markupOwner(file: PsiFile): PsiClass? = try {
        val project = file.project
        val target = WicketEditorTargetResolver().resolve(project, file.virtualFile.url, 0)
        WicketPageSelection().resolve(project, target.sourceUrl, target.caretOffset, target.qualifiedClassName)
    } catch (_: AnalysisUnavailable) {
        null
    }

    fun bundles(owner: PsiClass, requireComplete: Boolean = false): List<PropertiesFile> {
        val basename = WicketSourceClassNames.binaryBasename(owner) ?: return emptyList()
        val resources = WicketSourceResources.forOwner(owner) ?: return emptyList()
        val maxBytes = if (requireComplete) Long.MAX_VALUE else MAX_BUNDLE_BYTES
        val files = resources.files(basename, setOf("properties"), MAX_BUNDLES, maxBytes) ?: return emptyList()
        if (requireComplete && (resources.hasCustomMapping || files.any { it.length > MAX_BUNDLE_BYTES })) return emptyList()
        val manager = PsiManager.getInstance(owner.project)
        val bundles = files.mapNotNull { manager.findFile(it) as? PropertiesFile }
        return if (requireComplete && bundles.size != files.size) emptyList() else bundles
    }
}
