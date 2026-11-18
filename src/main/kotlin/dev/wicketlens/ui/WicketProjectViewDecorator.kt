package dev.wicketlens.ui

import com.intellij.ide.projectView.PresentationData
import com.intellij.ide.projectView.ProjectViewNode
import com.intellij.ide.projectView.ProjectViewNodeDecorator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.IndexNotReadyException
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiCompiledElement
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.util.InheritanceUtil
import com.intellij.ui.JBColor
import com.intellij.ui.BadgeIcon
import com.intellij.ui.BadgeDotProvider
import javax.swing.Icon

/** An icon badge only: no component graph, markup lookup, or retained PSI. */
class WicketProjectViewDecorator : ProjectViewNodeDecorator, DumbAware {
    override fun decorate(node: ProjectViewNode<*>, data: PresentationData) {
        val project = node.project ?: return
        if (project.isDisposed || DumbService.isDumb(project)) return
        ProgressManager.checkCanceled()
        try {
            val type = when (val value = node.value) {
                is PsiClass -> value
                is PsiJavaFile -> value.classes.singleOrNull { it.name == value.virtualFile?.nameWithoutExtension }
                    ?: value.classes.singleOrNull()
                else -> null
            } ?: return
            if (!type.isValid || type.project != project || type is PsiCompiledElement) return
            val file = type.containingFile ?: return
            val virtual = file.virtualFile ?: return
            if (!file.isPhysical || !ProjectFileIndex.getInstance(project).isInSourceContent(virtual)) return
            if (!InheritanceUtil.isInheritor(type, "org.apache.wicket.Component")) return
            val original = data.getIcon(false) ?: return
            if (!hasMarker(original)) data.setIcon(BadgeIcon(original, MARKER_COLOR, MARKER_SHAPE))
        } catch (_: IndexNotReadyException) {
            // The tree will refresh after indexing; never infer a role from the filename.
        }
    }

    companion object {
        private val MARKER_COLOR = JBColor(0xE9601A, 0xF29B61)
        private val MARKER_SHAPE = BadgeDotProvider(0.75, 0.25, 0.16, 0.06)
        internal fun hasMarker(icon: Icon?) = icon is BadgeIcon && icon.paint === MARKER_COLOR
    }
}
