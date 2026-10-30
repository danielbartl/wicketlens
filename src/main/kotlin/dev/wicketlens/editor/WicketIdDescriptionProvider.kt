package dev.wicketlens.editor

import com.intellij.psi.ElementDescriptionLocation
import com.intellij.psi.ElementDescriptionProvider
import com.intellij.psi.PsiElement
import com.intellij.usageView.UsageViewTypeLocation

class WicketIdDescriptionProvider : ElementDescriptionProvider {
    override fun getElementDescription(element: PsiElement, location: ElementDescriptionLocation): String? {
        if (element !is WicketIdRenameTarget) return null
        return if (location is UsageViewTypeLocation) "Wicket ID" else element.name
    }
}
