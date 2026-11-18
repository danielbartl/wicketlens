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
    private val NAVIGATION_KEY = Key.create<CachedValue<List<EditorPage>>>("wicketlens.editor.navigation.pages")
    private val COMPLETION_KEY = Key.create<CachedValue<MutableMap<dev.wicketlens.model.SourceLocation, List<EditorPage>>>>("wicketlens.editor.completion.pages")
    private val discovering = ThreadLocal<Boolean>()

    fun supports(file: PsiFile) = file.isPhysical && file.virtualFile != null &&
        (file is PsiJavaFile || file is XmlFile && file.virtualFile.extension == "html") &&
        ProjectFileIndex.getInstance(file.project).isInContent(file.virtualFile)

    fun ready(file: PsiFile) = supports(file) && !DumbService.isDumb(file.project) &&
        PsiDocumentManager.getInstance(file.project).uncommittedDocuments.isEmpty()

    fun pages(file: PsiFile): List<EditorPage> {
        if (!ready(file)) return emptyList()
        file.project.service<WicketProblemRefreshService>()
        return CachedValuesManager.getCachedValue(file, KEY) {
            CachedValueProvider.Result.create(compute(file), PsiModificationTracker.MODIFICATION_COUNT,
                ProjectRootManager.getInstance(file.project), VirtualFileManager.VFS_STRUCTURE_MODIFICATIONS)
        }
    }

    /** Completion has its own value cache; an empty editing site never enters ordinary report caches. */
    fun completionPages(file: PsiFile, target: dev.wicketlens.model.SourceLocation): List<EditorPage> {
        if (!ready(file)) return emptyList()
        val cache = CachedValuesManager.getCachedValue(file, COMPLETION_KEY) {
            CachedValueProvider.Result.create(mutableMapOf<dev.wicketlens.model.SourceLocation, List<EditorPage>>(),
                PsiModificationTracker.MODIFICATION_COUNT, ProjectRootManager.getInstance(file.project), VirtualFileManager.VFS_STRUCTURE_MODIFICATIONS)
        }
        synchronized(cache) { cache[target] }?.let { return it }
        val attribute = WicketIdPsi.valueAt(file, target.offset)?.let(WicketIdPsi::htmlId)?.parent
            ?.let { dev.wicketlens.model.SourceLocation(file.virtualFile.path, it.textOffset) }
        val computed = WicketCompletionAnalysis.at(target, attribute) {
            val local = compute(file)
            val owned = local.flatMap { entry -> entry.report.populationContexts.map { EditorPage(it, entry.sourceUrl, entry.classOffset) } }
            val seeds = local.mapNotNull { entry ->
                val source = WicketSourceFiles.find(file.project, entry.report.nodes.single { it.nodeId == entry.report.rootNodeId }.javaLocation.file)
                    ?.let { PsiManager.getInstance(file.project).findFile(it) as? PsiJavaFile } ?: return@mapNotNull null
                WicketSourceClassNames.members(source).classes.singleOrNull { it.qualifiedName == entry.report.qualifiedClassName }?.let { it to entry.report }
            }
            val discovered = if (seeds.isEmpty() || seeds.size > 16) null else WicketNavigationContextsAnalyzer().analyze(seeds)
            val extra = if (discovered?.complete != true) emptyList() else discovered.contexts.filter { it.report.submissionTemplate == null }.map { context ->
                val at = context.report.nodes.single { it.nodeId == context.report.rootNodeId }.javaLocation
                EditorPage(context.report, entrySourceUrl(file, at.file), at.offset)
            }
            (local + owned + extra).distinctBy { listOf(it.sourceUrl, it.report.qualifiedClassName, it.report.markupSuffix,
                it.report.lifecycle?.contextId, it.report.populationTemplate?.contextId, it.report.treeTemplate?.contextId,
                it.report.formTemplate?.contextId, it.report.toolbarTemplate?.contextId, it.report.submissionTemplate?.contextId, it.report.actionTemplate?.contextId) }
        }
        synchronized(cache) { if (cache.size >= 4) cache.clear(); cache[target] = computed }
        return computed
    }

    private fun entrySourceUrl(file: PsiFile, path: String) = WicketSourceFiles.find(file.project, path)?.url ?: file.virtualFile.url

    /** Extra contexts supply navigation; inspections and paired edits keep the local snapshot. */
    fun navigationPages(file: PsiFile): List<EditorPage> {
        val local = pages(file)
        val owned = local.flatMap { entry -> entry.report.populationContexts.map { context -> EditorPage(context, entry.sourceUrl, entry.classOffset) } }
        if (local.isEmpty() || discovering.get() == true) return local + owned
        return CachedValuesManager.getCachedValue(file, NAVIGATION_KEY) {
            discovering.set(true)
            try {
                val seeds = local.mapNotNull { entry ->
                    val source = VirtualFileManager.getInstance().findFileByUrl(entry.sourceUrl)
                        ?.let { PsiManager.getInstance(file.project).findFile(it) as? PsiJavaFile } ?: return@mapNotNull null
                    val type = WicketSourceClassNames.members(source).classes.singleOrNull { it.qualifiedName == entry.report.qualifiedClassName }
                        ?: return@mapNotNull null
                    type to entry.report
                }
                val result = if (seeds.size > 16) null else WicketNavigationContextsAnalyzer().analyze(seeds)
                val extra = if (result?.complete != true) emptyList() else result.contexts.filter { it.relationship in setOf("CALLER", "INHERITED", "EVENT_TEMPLATE", "SELECTION_TEMPLATE", "FACTORY_TEMPLATE", "TREE_TEMPLATE", "FORM_TEMPLATE", "REPEATER_TEMPLATE", "TABLE_TOOLBAR_TEMPLATE", "SUBMISSION_TEMPLATE", "CONFIRMATION_TEMPLATE", "ACTION_TEMPLATE") }.map { context ->
                    val root = context.report.nodes.single { it.nodeId == context.report.rootNodeId }
                    val source = WicketSourceFiles.find(file.project, root.javaLocation.file)!!
                    EditorPage(context.report, source.url, root.javaLocation.offset)
                }
                CachedValueProvider.Result.create((local + owned + extra).distinctBy { listOf(it.sourceUrl, it.report.qualifiedClassName, it.report.markupSuffix, it.report.lifecycle?.contextId, it.report.treeTemplate?.contextId, it.report.formTemplate?.contextId, it.report.populationTemplate?.contextId, it.report.toolbarTemplate?.contextId, it.report.submissionTemplate?.contextId, it.report.actionTemplate?.contextId) },
                    PsiModificationTracker.MODIFICATION_COUNT, ProjectRootManager.getInstance(file.project), VirtualFileManager.VFS_STRUCTURE_MODIFICATIONS)
            } finally { discovering.remove() }
        }
    }

    private fun compute(file: PsiFile): List<EditorPage> {
        if (file is PsiJavaFile) return WicketSourceClassNames.members(file).classes.mapNotNull { page ->
            WicketPageAnalyzer().analyze(page)?.takeIf { page.containingClass == null || it.markupPath != null }
                ?.let { EditorPage(it, file.virtualFile.url, page.textOffset) }
        }
        return try {
            val target = WicketEditorTargetResolver().resolve(file.project, file.virtualFile.url, -1)
            val report = WicketPageSelection().analyze(file.project, target.sourceUrl, target.caretOffset, target.qualifiedClassName, target.markupSuffix)
            listOf(EditorPage(report, target.sourceUrl, report.nodes.single { it.nodeId == report.rootNodeId }.javaLocation.offset))
        } catch (_: AnalysisUnavailable) { emptyList() }
    }
}
