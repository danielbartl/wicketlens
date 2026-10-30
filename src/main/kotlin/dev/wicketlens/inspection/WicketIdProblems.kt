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
        return report.matching?.findings.orEmpty().mapNotNull { finding ->
            if (finding.causes.isNotEmpty()) return@mapNotNull null
            val (element, id, missing) = when (finding.code) {
                DiagnosticCode.JAVA_ONLY -> {
                    val node = report.nodes.singleOrNull { it.nodeId == finding.javaNodeId } ?: return@mapNotNull null
                    val location = node.idLocation ?: node.javaLocation
                    if (file !is PsiJavaFile || location.file != file.virtualFile.path) return@mapNotNull null
                    val origin = dev.wicketlens.editor.WicketIdPsi.javaIdElement(file.project, node) ?: return@mapNotNull null
                    if (!node.computedId && (origin as? com.intellij.psi.PsiLiteralExpression)?.value != node.wicketId) return@mapNotNull null
                    Triple(origin, node.wicketId ?: return@mapNotNull null, "HTML markup")
                }
                DiagnosticCode.MARKUP_ONLY -> {
                    val node = report.markup?.elements?.singleOrNull { it.nodeId == finding.markupNodeId } ?: return@mapNotNull null
                    val location = node.wicketIdLocation ?: return@mapNotNull null
                    if (file !is XmlFile || location.file != file.virtualFile.path) return@mapNotNull null
                    val attribute = PsiTreeUtil.getParentOfType(file.findElementAt(location.offset), XmlAttribute::class.java, false)
                        ?: return@mapNotNull null
                    val value = attribute.valueElement ?: return@mapNotNull null
                    Triple(value, node.wicketId ?: return@mapNotNull null, "Java component setup")
                }
                else -> return@mapNotNull null
            }
            val range = element.textRange
            WicketIdProblem(range.startOffset, range.endOffset, document.getLineNumber(range.startOffset),
                "Possible unpaired Wicket ID '$id': no matching $missing at this component path. Analysis is partial.", url, classOffset, report.markupSuffix)
        }
    }
}
