package dev.wicketlens.editor

import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.psi.*
import com.intellij.psi.util.*
import com.intellij.psi.xml.XmlFile
import dev.wicketlens.analysis.*
import dev.wicketlens.inspection.WicketProblemRefreshService
import dev.wicketlens.model.PageReport

internal data class EditorPage(val report: PageReport, val sourceUrl: String, val classOffset: Int)

/** A shared, per-file value snapshot. PSI is resolved only within the caller's read action. */
internal object WicketEditorAnalysis {
    private val KEY = Key.create<CachedValue<List<EditorPage>>>("wicketlens.editor.pages")

    fun supports(file: PsiFile) = file.isPhysical && file.virtualFile != null &&
        (file is PsiJavaFile || file is XmlFile && file.virtualFile.extension == "html") &&
        ProjectFileIndex.getInstance(file.project).isInContent(file.virtualFile)

    fun pages(file: PsiFile): List<EditorPage> {
        if (!supports(file) || DumbService.isDumb(file.project) ||
            PsiDocumentManager.getInstance(file.project).uncommittedDocuments.isNotEmpty()) return emptyList()
        file.project.service<WicketProblemRefreshService>()
        return CachedValuesManager.getCachedValue(file, KEY) {
            CachedValueProvider.Result.create(compute(file), PsiModificationTracker.MODIFICATION_COUNT,
                ProjectRootManager.getInstance(file.project), VirtualFileManager.VFS_STRUCTURE_MODIFICATIONS)
        }
    }

    private fun compute(file: PsiFile): List<EditorPage> {
        if (file is PsiJavaFile) return file.classes.mapNotNull { page ->
            WicketPageAnalyzer().analyze(page)?.let { EditorPage(it, file.virtualFile.url, page.textOffset) }
        }
        return try {
            val target = WicketEditorTargetResolver().resolve(file.project, file.virtualFile.url, -1)
            val report = WicketPageSelection().analyze(file.project, target.sourceUrl, target.caretOffset, target.qualifiedClassName, target.markupSuffix)
            listOf(EditorPage(report, target.sourceUrl, report.nodes.single { it.nodeId == report.rootNodeId }.javaLocation.offset))
        } catch (_: AnalysisUnavailable) { emptyList() }
    }
}
