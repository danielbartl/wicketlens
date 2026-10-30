package dev.wicketlens.ui

import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.VirtualFileWithId
import com.intellij.psi.util.PsiModificationTracker
import dev.wicketlens.analysis.AnalysisUnavailable
import dev.wicketlens.model.PageReport

/** Captured with the PSI snapshot; never retains documents, virtual files, or PSI. */
data class ReportVersion(
    val sources: List<SourceVersion>,
    val psiStamp: Long,
    val rootsStamp: Long,
    val changeStamp: Long,
) {
    fun isCurrent(project: Project, changes: Long): Boolean =
        changes == changeStamp && PsiModificationTracker.getInstance(project).modificationCount == psiStamp &&
            ProjectRootManager.getInstance(project).modificationCount == rootsStamp && sources.all { it.isCurrent() }

    companion object {
        fun capture(project: Project, report: PageReport, sourceUrl: String, changes: Long): ReportVersion {
            val primary = VirtualFileManager.getInstance().findFileByUrl(sourceUrl)
                ?: throw AnalysisUnavailable("The Java source is no longer available. Run Analyze Wicket Page again.")
            val paths = (report.markup?.sourceFiles ?: listOfNotNull(report.markupPath)) + report.javaSourceFiles
            val files = listOf(primary) + paths.map {
                dev.wicketlens.analysis.WicketSourceFiles.find(project, it)
                    ?: throw AnalysisUnavailable("An analyzed Java/markup source is no longer available. Run Analyze Wicket Page again.")
            }
            return ReportVersion(files.distinctBy { it.url }.map(SourceVersion::capture),
                PsiModificationTracker.getInstance(project).modificationCount,
                ProjectRootManager.getInstance(project).modificationCount, changes)
        }
    }
}

data class SourceVersion(val url: String, val path: String, val fileId: Int?, val fileStamp: Long, val documentStamp: Long) {
    fun isCurrent(): Boolean {
        val file = VirtualFileManager.getInstance().findFileByUrl(url) ?: return false
        if (!file.isValid || file.path != path || (file as? VirtualFileWithId)?.id != fileId) return false
        // A collected clean document can be recreated later from this same unchanged VFS version.
        val document = FileDocumentManager.getInstance().getCachedDocument(file)
        // Saving the analyzed text can update the VFS stamp without changing the document.
        // External VFS changes also invalidate the service's change stamp.
        return document?.modificationStamp?.let { it == documentStamp }
            ?: (file.modificationStamp == fileStamp && documentStamp == fileStamp)
    }

    companion object {
        fun capture(file: VirtualFile): SourceVersion {
            val document = FileDocumentManager.getInstance().getDocument(file)
                ?: throw AnalysisUnavailable("The source cannot be opened as text: ${file.path}")
            return SourceVersion(file.url, file.path, (file as? VirtualFileWithId)?.id, file.modificationStamp, document.modificationStamp)
        }
    }
}
