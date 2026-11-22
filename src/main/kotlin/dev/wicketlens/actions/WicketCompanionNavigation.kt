package dev.wicketlens.actions

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import dev.wicketlens.analysis.AnalysisUnavailable
import dev.wicketlens.analysis.WicketEditorTargetResolver
import dev.wicketlens.analysis.WicketPageResolver
import dev.wicketlens.analysis.WicketPageSelection
import dev.wicketlens.model.MarkupResolutionStatus

internal object WicketCompanionNavigation {
    data class Destination(val file: VirtualFile, val offset: Int = 0) {
        override fun toString() = file.path
    }

    fun destinations(project: Project, source: VirtualFile, offset: Int): List<Destination> {
        val target = WicketEditorTargetResolver().resolve(project, source.url, offset)
        val owner = WicketPageSelection().resolve(project, target.sourceUrl, target.caretOffset, target.qualifiedClassName)
        if (source.extension == "html") return listOf(Destination(owner.containingFile.virtualFile, owner.textOffset))

        val (resolution, variants) = WicketPageResolver().resolveWithVariants(owner)
        if (resolution.status == MarkupResolutionStatus.AMBIGUOUS || resolution.status == MarkupResolutionStatus.UNSUPPORTED) {
            throw AnalysisUnavailable(resolution.diagnostics.first().message)
        }
        val base = resolution.markupUrl?.let { VirtualFileManager.getInstance().findFileByUrl(it) }
        val namedVariants = variants.groupBy { it.name }.values.mapNotNull { it.singleOrNull() }
        val files = listOfNotNull(base) + namedVariants
        if (files.isEmpty()) {
            val message = if (variants.isEmpty()) resolution.diagnostics.firstOrNull()?.message
                else "Multiple files share each markup variant name. Open the intended variant from the Project view."
            throw AnalysisUnavailable(message ?: "The companion markup is no longer available.")
        }
        return files.map { Destination(it) }
    }
}
