package dev.wicketlens.editor

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.fileTypes.PlainSyntaxHighlighter
import com.intellij.openapi.options.colors.AttributesDescriptor
import com.intellij.openapi.options.colors.ColorDescriptor
import com.intellij.openapi.options.colors.ColorSettingsPage
import com.intellij.psi.ElementManipulators
import com.intellij.psi.PsiElement

class WicketIdAnnotator : Annotator {
    override fun annotate(element: PsiElement, holder: AnnotationHolder) {
        if (WicketIdPsi.htmlId(element) == null && WicketIdPsi.javaId(element) == null) return
        val range = ElementManipulators.getValueTextRange(element).shiftRight(element.textRange.startOffset)
        if (!range.isEmpty) holder.newSilentAnnotation(HighlightSeverity.INFORMATION).range(range).textAttributes(ID).create()
    }

    companion object {
        // Wicket 10.11.0 DefaultButtonImageResource uses RGB E9/60/1A as its default orange.
        @JvmField val ID = TextAttributesKey.createTextAttributesKey("WICKETLENS_COMPONENT_ID", DefaultLanguageHighlighterColors.STRING)
    }
}

class WicketIdColorSettingsPage : ColorSettingsPage {
    override fun getDisplayName() = "WicketLens"
    override fun getIcon(): javax.swing.Icon? = null
    override fun getHighlighter() = PlainSyntaxHighlighter()
    override fun getDemoText() = """
        Java: add(new Label("<id>title</id>", "Page title"));
        HTML: <span wicket:id="<id>title</id>">Page title</span>
    """.trimIndent()
    override fun getAdditionalHighlightingTagToDescriptorMap() = mapOf("id" to WicketIdAnnotator.ID)
    override fun getAttributeDescriptors() = arrayOf(AttributesDescriptor("Wicket component ID", WicketIdAnnotator.ID))
    override fun getColorDescriptors(): Array<ColorDescriptor> = ColorDescriptor.EMPTY_ARRAY
}
