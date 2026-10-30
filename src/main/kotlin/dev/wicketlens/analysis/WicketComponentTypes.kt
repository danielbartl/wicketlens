package dev.wicketlens.analysis

import com.intellij.psi.PsiClass
import com.intellij.psi.util.InheritanceUtil
import dev.wicketlens.model.MarkupKind

/** Framework boundaries differ: FormComponentPanel does not extend Panel. */
internal object WicketComponentTypes {
    const val PAGE = "org.apache.wicket.markup.html.WebPage"
    const val PANEL = "org.apache.wicket.markup.html.panel.Panel"
    const val FORM_COMPONENT = "org.apache.wicket.markup.html.form.FormComponent"
    const val FORM_PANEL = "org.apache.wicket.markup.html.form.FormComponentPanel"
    val markupRoots = setOf(PAGE, PANEL, FORM_PANEL)
    fun ownsMarkup(type: PsiClass) = markupRoots.any { InheritanceUtil.isInheritor(type, it) }
    fun isFormComponent(type: PsiClass) = InheritanceUtil.isInheritor(type, FORM_COMPONENT)
    fun kind(type: PsiClass) = if (InheritanceUtil.isInheritor(type, PAGE)) MarkupKind.PAGE else MarkupKind.PANEL
}
