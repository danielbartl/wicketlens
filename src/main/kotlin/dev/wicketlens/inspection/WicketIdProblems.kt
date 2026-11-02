package dev.wicketlens.inspection

import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.psi.*
import com.intellij.psi.util.CachedValue
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.xml.XmlAttribute
import com.intellij.psi.xml.XmlFile
import dev.wicketlens.editor.WicketEditorAnalysis
import dev.wicketlens.model.DiagnosticCode
import dev.wicketlens.model.PageReport

/** Cached diagnostics contain only values; neither a PSI tree nor a report is retained. */
internal data class WicketIdProblem(
    val start: Int,
    val end: Int,
    val line: Int,
    val message: String,
    val sourceUrl: String,
    val classOffset: Int,
    val markupSuffix: String = "",
)

internal object WicketIdProblems {
    private val KEY = Key.create<CachedValue<List<WicketIdProblem>>>("wicketlens.unpaired.ids")

    fun supports(file: PsiFile): Boolean = file.isPhysical && file.virtualFile != null &&
        (file is PsiJavaFile || file is XmlFile && file.virtualFile.extension == "html") &&
        ProjectFileIndex.getInstance(file.project).isInContent(file.virtualFile)

    /** The daemon normally supplies committed PSI. Never cache a mixed Java/HTML snapshot. */
    fun forFile(file: PsiFile): List<WicketIdProblem> {
        if (!supports(file) || DumbService.isDumb(file.project) ||
            PsiDocumentManager.getInstance(file.project).uncommittedDocuments.isNotEmpty()) return emptyList()
        file.project.service<WicketProblemRefreshService>()
        return CachedValuesManager.getCachedValue(file, KEY) {
            CachedValueProvider.Result.create(compute(file), PsiModificationTracker.MODIFICATION_COUNT,
                ProjectRootManager.getInstance(file.project), VirtualFileManager.VFS_STRUCTURE_MODIFICATIONS)
        }
    }

    private fun compute(file: PsiFile): List<WicketIdProblem> {
        val reports = WicketEditorAnalysis.pages(file)
        return reports.flatMap { (report, url, offset) -> problems(file, report, url, offset) }
            .distinct().sortedBy { it.start }
    }

    private fun problems(file: PsiFile, report: PageReport, url: String, classOffset: Int): List<WicketIdProblem> {
        val document = PsiDocumentManager.getInstance(file.project).getDocument(file) ?: return emptyList()
        return report.matching?.findings.orEmpty().flatMap { finding ->
            if (finding.causes.isNotEmpty()) return@flatMap emptyList()
            val (elements, id, missing) = when (finding.code) {
                DiagnosticCode.JAVA_ONLY -> {
                    val node = report.nodes.singleOrNull { it.nodeId == finding.javaNodeId } ?: return@flatMap emptyList()
                    if (file !is PsiJavaFile) return@flatMap emptyList()
                    val origins = dev.wicketlens.editor.WicketIdPsi.javaIdElements(file.project, node).filter {
                        it.containingFile == file && (node.computedId || (it as? PsiLiteralExpression)?.value == node.wicketId)
                    }
                    Triple(origins, node.wicketId ?: return@flatMap emptyList(), "HTML markup")
                }
                DiagnosticCode.MARKUP_ONLY -> {
                    val node = report.markup?.elements?.singleOrNull { it.nodeId == finding.markupNodeId } ?: return@flatMap emptyList()
                    val location = node.wicketIdLocation ?: return@flatMap emptyList()
                    if (file !is XmlFile || location.file != file.virtualFile.path) return@flatMap emptyList()
                    val attribute = PsiTreeUtil.getParentOfType(file.findElementAt(location.offset), XmlAttribute::class.java, false)
                        ?: return@flatMap emptyList()
                    val value = attribute.valueElement ?: return@flatMap emptyList()
                    Triple(listOf(value), node.wicketId ?: return@flatMap emptyList(), "Java component setup")
                }
                else -> return@flatMap emptyList()
            }
            elements.map { element ->
                val range = element.textRange
                WicketIdProblem(range.startOffset, range.endOffset, document.getLineNumber(range.startOffset),
                    "Possible unpaired Wicket ID '$id': no matching $missing at this component path. Analysis is partial.", url, classOffset, report.markupSuffix)
            }
        }
    }
}
