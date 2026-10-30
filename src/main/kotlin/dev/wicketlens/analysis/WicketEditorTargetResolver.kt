package dev.wicketlens.analysis

import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.PsiManager
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope

data class EditorPageTarget(val sourceUrl: String, val caretOffset: Int, val qualifiedClassName: String? = null, val markupSuffix: String = "")

/** Resolve editor identity inside the same smart, committed read as the page snapshot. */
class WicketEditorTargetResolver {
    fun resolve(project: Project, editorUrl: String, caretOffset: Int): EditorPageTarget {
        ProgressManager.checkCanceled()
        val file = VirtualFileManager.getInstance().findFileByUrl(editorUrl)
            ?: throw AnalysisUnavailable("The selected editor file is no longer available.")
        if (file.extension == "java") return EditorPageTarget(editorUrl, caretOffset)
        if (file.extension != "html") throw AnalysisUnavailable("Select a Wicket Java Page/Panel/FormComponentPanel or its companion HTML file.")
        val module = ProjectFileIndex.getInstance(project).getModuleForFile(file)
            ?: throw AnalysisUnavailable("The HTML file has no configured project module.")
        val basename = file.nameWithoutExtension
        val names = (listOf(basename) + basename.indices.filter { basename[it] == '_' }.map { basename.take(it) }).distinct()
        if (names.size > 16) throw AnalysisUnavailable("Too many possible Java names in this markup variant filename.")
        val candidates = names.flatMap { FilenameIndex.getVirtualFilesByName("$it.java", GlobalSearchScope.moduleScope(module)) }.distinct()
        val targets = candidates.mapNotNull { candidate ->
            ProgressManager.checkCanceled()
            val java = PsiManager.getInstance(project).findFile(candidate) as? PsiJavaFile ?: return@mapNotNull null
            val page = java.classes.singleOrNull { it.name == candidate.nameWithoutExtension &&
                WicketComponentTypes.ownsMarkup(it) } ?: return@mapNotNull null
            val suffix = basename.removePrefix(page.name!!)
            if (WicketPageResolver().resolve(page, suffix).markupUrl != file.url) return@mapNotNull null
            EditorPageTarget(candidate.url, -1, page.qualifiedName, suffix)
        }.distinct()
        return targets.singleOrNull()
            ?: throw AnalysisUnavailable("No unique conventional Wicket Java source matches the selected HTML file.")
    }
}
