package dev.wicketlens.mcp

import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.JsonArray
import com.google.gson.JsonNull
import com.google.gson.JsonParser
import com.intellij.openapi.application.ReadConstraint
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.constrainedReadAction
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.roots.OrderEnumerator
import com.intellij.openapi.vfs.JarFileSystem
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.util.Disposer
import com.intellij.psi.*
import com.intellij.psi.search.GlobalSearchScope
import dev.wicketlens.analysis.AnalysisUnavailable
import dev.wicketlens.analysis.WicketComponentTypes
import dev.wicketlens.analysis.WicketPageSelection
import dev.wicketlens.analysis.EditorPageTarget
import dev.wicketlens.export.WicketContextExporter
import dev.wicketlens.model.PageReport
import dev.wicketlens.spring.WicketDependencyAnalyzer
import dev.wicketlens.spring.WicketDependencySnapshot
import dev.wicketlens.spring.PreparedSpringTest
import dev.wicketlens.spring.WicketSpringTestGenerator
import dev.wicketlens.testing.WicketTestGenerator
import dev.wicketlens.ui.ReportVersion
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import java.util.concurrent.atomic.AtomicBoolean
import org.jetbrains.jps.model.java.JavaSourceRootType

class PageAnalysisFailure(val code: String, message: String) : Exception(message)

enum class WicketMcpReport(val tool: String) {
    STRUCTURE("wicket_get_page_structure"), VALIDATION("wicket_validate_page"), CONTEXT("wicket_get_component_context"),
    DEPENDENCIES("wicket_get_injected_dependencies"), SPRING_INSPECTIONS("wicket_get_spring_inspections"),
    SPRING_TEST_PLAN("wicket_get_spring_test_plan"), TEST_PLAN("wicket_get_test_plan")
}

/** Values only. No MCP SDK dependency, sidebar state, cached PSI, clipboard, or navigation. */
data class PageAnalysisSnapshot(val report: PageReport, val moduleName: String, val sourceSet: String, val version: ReportVersion,
    val dependencies: WicketDependencySnapshot? = null, val springInspections: SpringInspectionSnapshot? = null,
    val springTest: PreparedSpringTest? = null, val testPlan: PreparedSpringTest? = null)

/** Thin request adapter over the existing engine and exporter; also testable without the optional host plugin. */
class WicketPageAnalysisRequest internal constructor(private val timeoutMillis: Long) {
    constructor() : this(30_000)

    private class ProjectClosed : CancellationException("The selected project closed during analysis.")

    suspend fun execute(project: Project, className: String, moduleName: String? = null, sourceSet: String = "main",
        view: WicketMcpReport = WicketMcpReport.STRUCTURE, componentPath: String? = null, nodeId: String? = null): String = try {
        coroutineScope {
            if (project.isDisposed || project.isDefault) fail("PROJECT_UNAVAILABLE", "The selected project is unavailable.")
            val requestJob = currentCoroutineContext().job
            val listening = AtomicBoolean(true)
            val onClose = Disposable {
                if (listening.getAndSet(false)) requestJob.cancel(ProjectClosed())
            }
            if (!Disposer.tryRegister(project, onClose)) fail("PROJECT_UNAVAILABLE", "The selected project is unavailable.")
            try {
                analyze(project, className, moduleName, sourceSet, view, componentPath, nodeId)
            } finally {
                // Dispose our registration without cancelling a normally completed request.
                listening.set(false)
                Disposer.dispose(onClose)
            }
        }
    } catch (_: ProjectClosed) {
        // A simultaneous client cancellation still belongs to the caller.
        currentCoroutineContext().ensureActive()
        fail("PROJECT_UNAVAILABLE", "The selected project closed during analysis.")
    }

    private suspend fun analyze(project: Project, className: String, moduleName: String?, sourceSet: String,
        view: WicketMcpReport, componentPath: String?, nodeId: String?): String =
        withTimeoutOrNull(timeoutMillis) {
            if (view == WicketMcpReport.CONTEXT) WicketComponentContext.validate(componentPath, nodeId)
            constrainedReadAction { validate(project, className, moduleName, sourceSet) }
            refreshProject(project)
            val snapshot = constrainedReadAction(ReadConstraint.inSmartMode(project), ReadConstraint.withDocumentsCommitted(project)) {
                capture(project, className, moduleName, sourceSet, view)
            }
            currentCoroutineContext().ensureActive()
            val json = render(snapshot, view, componentPath, nodeId)
            currentCoroutineContext().ensureActive()
            constrainedReadAction { requireCurrent(project, snapshot) }
            json
        } ?: throw PageAnalysisFailure("TIMED_OUT", "Analysis timed out while waiting for refresh, indexing, document commits, or analysis. Retry when the project is ready.")

    /** Refresh asynchronously outside read access: external agent writes must reach VFS/PSI before lookup. */
    private suspend fun refreshProject(project: Project) {
        val roots = constrainedReadAction {
            if (project.isDisposed) fail("PROJECT_UNAVAILABLE", "The selected project is unavailable.")
            val sources = ProjectRootManager.getInstance(project).contentSourceRoots.toList()
            val binaries = OrderEnumerator.orderEntries(project).recursively().withoutSdk().classes().roots.map {
                JarFileSystem.getInstance().getVirtualFileForJar(it) ?: it
            }
            (sources + binaries).filter { it.isValid && it.fileSystem.protocol == "file" }.distinctBy { it.url }
        }
        if (roots.isEmpty()) return
        suspendCancellableCoroutine<Unit> { continuation ->
            LocalFileSystem.getInstance().refreshFiles(roots, true, true, Runnable {
                if (continuation.isActive) continuation.resume(Unit)
            })
        }
    }

    private fun validate(project: Project, className: String, moduleName: String?, sourceSet: String) {
        if (project.isDisposed || project.isDefault) fail("PROJECT_UNAVAILABLE", "The selected project is unavailable.")
        if (className.length !in 1..512 || !PsiNameHelper.getInstance(project).isQualifiedName(className) ||
            moduleName != null && (moduleName.isBlank() || moduleName.length > 256) || sourceSet !in setOf("main", "test", "any"))
            fail("INVALID_REQUEST", "Supply a Java qualified className, an optional exact moduleName, and sourceSet main, test, or any.")
        if (moduleName != null && ModuleManager.getInstance(project).modules.none { it.name == moduleName })
            fail("MODULE_NOT_FOUND", "No open module matches moduleName in the selected project.")
    }

    /** Caller holds smart, committed read access. Only Java source roots of the selected project are eligible. */
    fun capture(project: Project, className: String, moduleName: String? = null, sourceSet: String = "main",
        view: WicketMcpReport = WicketMcpReport.STRUCTURE): PageAnalysisSnapshot {
        ProgressManager.checkCanceled()
        validate(project, className, moduleName, sourceSet)
        if (DumbService.isDumb(project)) fail("INDEXING", "Wait for indexing before analyzing a page.")
        if (PsiDocumentManager.getInstance(project).uncommittedDocuments.isNotEmpty())
            fail("UNCOMMITTED_DOCUMENTS", "Commit editor documents before taking the analysis snapshot.")
        val selectedModule = moduleName?.let { name -> ModuleManager.getInstance(project).modules.singleOrNull { it.name == name }
            ?: fail("MODULE_NOT_FOUND", "No open module matches moduleName in the selected project.") }
        val scope = selectedModule?.let(GlobalSearchScope::moduleScope) ?: GlobalSearchScope.projectScope(project)
        val index = ProjectFileIndex.getInstance(project)
        val candidates = JavaPsiFacade.getInstance(project).findClasses(className, scope).filter { type ->
            ProgressManager.checkCanceled()
            val file = type.containingFile?.virtualFile
            val owner = file?.let(index::getModuleForFile)
            val root = file?.let(index::getSourceRootForFile)
            type !is PsiCompiledElement && type.containingFile is PsiJavaFile && file != null && owner != null &&
                index.isInContent(file) && (selectedModule == null || selectedModule == owner) &&
                (sourceSet == "any" || index.isInTestSourceContent(file) == (sourceSet == "test")) &&
                (root in ModuleRootManager.getInstance(owner).getSourceRoots(JavaSourceRootType.SOURCE) ||
                    root in ModuleRootManager.getInstance(owner).getSourceRoots(JavaSourceRootType.TEST_SOURCE))
        }.distinctBy { it.containingFile.virtualFile.url to it.qualifiedName }
        if (candidates.isEmpty()) fail("CLASS_NOT_FOUND", "No Java source matches className and the module/source-set selection in this project. Library classes are not analysis targets.")
        if (candidates.size != 1) fail("AMBIGUOUS_CLASS", "Multiple source declarations match. Supply moduleName and sourceSet; no declaration was selected.")
        val type = candidates.single()
        if (!WicketComponentTypes.ownsMarkup(type)) fail("UNSUPPORTED_TARGET", "The selected source must extend Wicket WebPage, Panel, or FormComponentPanel. Check its module's Wicket dependencies.")
        val file = type.containingFile.virtualFile
        val report = try { WicketPageSelection().analyze(project, file.url, -1, className) }
            catch (_: AnalysisUnavailable) { fail("ANALYSIS_UNAVAILABLE", "The selected source could not be analyzed. Check Java/markup sources and Wicket dependencies.") }
        val dependencies = if (view == WicketMcpReport.DEPENDENCIES) WicketDependencyAnalyzer().analyze(project, report) else null
        val inspections = if (view == WicketMcpReport.SPRING_INSPECTIONS) WicketSpringInspectionReport.capture(project, report) else null
        val springTest = if (view == WicketMcpReport.SPRING_TEST_PLAN) try {
            WicketSpringTestGenerator().prepare(project, report, EditorPageTarget(file.url, -1, className))
        } catch (unavailable: AnalysisUnavailable) { fail("TEST_PLAN_UNAVAILABLE", unavailable.message ?: "The selected source is not supported by the Spring test generator.") }
        else null
        val testPlan = if (view == WicketMcpReport.TEST_PLAN) try {
            WicketTestGenerator().prepare(project, report, EditorPageTarget(file.url, -1, className))
        } catch (unavailable: AnalysisUnavailable) { fail("TEST_PLAN_UNAVAILABLE", unavailable.message ?: "The selected subject cannot be scaffolded.") }
        else null
        val version = try { springTest?.version ?: testPlan?.version ?: ReportVersion.capture(project, report, file.url, 0, dependencies?.sourceFiles.orEmpty() + inspections?.sourceFiles.orEmpty()) }
            catch (_: AnalysisUnavailable) { fail("ANALYSIS_UNAVAILABLE", "An analysis dependency became unavailable. Retry after the project refreshes.") }
        return PageAnalysisSnapshot(report, index.getModuleForFile(file)!!.name, if (index.isInTestSourceContent(file)) "test" else "main", version, dependencies, inspections, springTest, testPlan)
    }

    fun render(snapshot: PageAnalysisSnapshot, view: WicketMcpReport = WicketMcpReport.STRUCTURE,
        componentPath: String? = null, nodeId: String? = null): String {
        if (view == WicketMcpReport.CONTEXT) WicketComponentContext.validate(componentPath, nodeId)
        if (snapshot.report.nodes.size > 5_000 || snapshot.report.markup?.elements?.size?.let { it > 10_000 } == true)
            fail("RESPONSE_TOO_LARGE", "The page exceeds this tool's component/markup limits. Analyze a smaller source component separately.")
        val json = JsonObject().apply {
            addProperty("schemaVersion", 1)
            addProperty("tool", view.tool)
            addProperty("status", "ok")
            addProperty("truncated", when (view) {
                WicketMcpReport.DEPENDENCIES -> dependencies(snapshot).let { it.truncated || it.owners.any { owner -> owner.fields.any { field -> field.candidatesTruncated } } }
                WicketMcpReport.SPRING_INSPECTIONS -> inspections(snapshot).truncated
                else -> false
            })
            add("target", JsonObject().apply {
                addProperty("className", snapshot.report.qualifiedClassName)
                addProperty("moduleName", snapshot.moduleName)
                addProperty("sourceSet", snapshot.sourceSet)
            })
            add("snapshot", JsonObject().apply {
                addProperty("kind", "committed_static_analysis")
                addProperty("freshness", "checked_after_serialization")
            })
            val analysis by lazy { JsonParser.parseString(WicketContextExporter().export(snapshot.report)).asJsonObject }
            when (view) {
                WicketMcpReport.STRUCTURE -> add("analysis", analysis)
                WicketMcpReport.VALIDATION -> add("validation", validation(analysis))
                WicketMcpReport.CONTEXT -> add("context", WicketComponentContext(analysis).select(componentPath, nodeId))
                WicketMcpReport.DEPENDENCIES -> add("dependencies", WicketDependencyExport.render(dependencies(snapshot)))
                WicketMcpReport.SPRING_INSPECTIONS -> add("springInspections", WicketSpringInspectionReport.render(inspections(snapshot)))
                WicketMcpReport.SPRING_TEST_PLAN -> add("springTestPlan", WicketSpringTestPlanExport.render(snapshot.springTest
                    ?: fail("ANALYSIS_UNAVAILABLE", "The Spring test plan was not captured. Retry the test-plan tool for a fresh analysis.")))
                WicketMcpReport.TEST_PLAN -> add("testPlan", WicketTestPlanExport.render(snapshot.testPlan
                    ?: fail("ANALYSIS_UNAVAILABLE", "The test plan was not captured. Retry the test-plan tool for a fresh analysis.")))
            }
        }.let(GSON::toJson)
        if (json.toByteArray(Charsets.UTF_8).size > MAX_RESPONSE_BYTES)
            fail("RESPONSE_TOO_LARGE", "The JSON response exceeds 512 KiB. Analyze a smaller source component separately; no graph was truncated.")
        return json
    }

    private fun dependencies(snapshot: PageAnalysisSnapshot): WicketDependencySnapshot = snapshot.dependencies
        ?: fail("ANALYSIS_UNAVAILABLE", "The dependency snapshot was not captured. Retry the dependency tool for a fresh analysis.")

    private fun inspections(snapshot: PageAnalysisSnapshot): SpringInspectionSnapshot = snapshot.springInspections
        ?: fail("ANALYSIS_UNAVAILABLE", "The Spring inspection snapshot was not captured. Retry the inspection tool for a fresh analysis.")

    /** Project the existing export, preserving its exact findings, causes, locations, and severities. */
    private fun validation(analysis: JsonObject): JsonObject {
        val matching = analysis["matching"].takeUnless { it.isJsonNull }?.asJsonObject
        val markup = analysis["markup"].takeUnless { it.isJsonNull }?.asJsonObject
        val findings = matching?.getAsJsonArray("findings") ?: JsonArray()
        val componentDiagnostics = JsonArray()
        for (node in analysis.getAsJsonArray("components")) {
            ProgressManager.checkCanceled()
            val component = node.asJsonObject
            if (component.getAsJsonArray("diagnostics").isEmpty) continue
            componentDiagnostics.add(JsonObject().apply {
                add("nodeId", component["nodeId"])
                add("wicketId", component["wicketId"])
                add("componentType", component["componentType"])
                add("javaLocation", component["javaLocation"])
                add("diagnostics", component["diagnostics"])
            })
        }
        val markupElementDiagnostics = JsonArray()
        for (value in markup?.getAsJsonArray("elements") ?: JsonArray()) {
            ProgressManager.checkCanceled()
            val element = value.asJsonObject
            if (element.getAsJsonArray("diagnostics").isEmpty) continue
            markupElementDiagnostics.add(JsonObject().apply {
                for (key in listOf("nodeId", "tagName", "wicketId", "location", "wicketIdLocation", "diagnostics")) add(key, element[key])
            })
        }
        fun counts(field: String) = JsonObject().apply {
            findings.map { it.asJsonObject[field].asString }.groupingBy { it }.eachCount().toSortedMap()
                .forEach { (key, count) -> addProperty(key, count) }
        }
        val warnings = analysis.getAsJsonArray("warnings")
        val markupDiagnostics = markup?.getAsJsonArray("diagnostics") ?: JsonArray()
        val documentDiagnostics = markup?.getAsJsonArray("documentDiagnostics") ?: JsonArray()
        return JsonObject().apply {
            add("analysisSchemaVersion", analysis["schemaVersion"])
            add("qualifiedClassName", analysis["qualifiedClassName"])
            addProperty("matchingAvailable", matching != null)
            addProperty("markupAvailable", markup != null)
            add("scopeComplete", JsonObject().apply {
                add("java", analysis["scopeComplete"])
                add("markup", markup?.get("scopeComplete") ?: JsonNull.INSTANCE)
                add("matching", matching?.get("scopeComplete") ?: JsonNull.INSTANCE)
            })
            add("summary", JsonObject().apply {
                addProperty("findingCount", findings.size())
                add("findingsByCode", counts("code"))
                add("findingsBySeverity", counts("severity"))
                add("findingsByConfidence", counts("confidence"))
                addProperty("warningCount", warnings.size())
                addProperty("componentDiagnosticCount", componentDiagnostics.sumOf { it.asJsonObject.getAsJsonArray("diagnostics").size() })
                addProperty("markupDiagnosticCount", markupDiagnostics.size())
                addProperty("markupElementDiagnosticCount", markupElementDiagnostics.sumOf { it.asJsonObject.getAsJsonArray("diagnostics").size() })
                addProperty("documentDiagnosticCount", documentDiagnostics.size())
            })
            add("findings", findings)
            add("warnings", warnings)
            add("componentDiagnostics", componentDiagnostics)
            add("markupDiagnostics", markupDiagnostics)
            add("markupElementDiagnostics", markupElementDiagnostics)
            add("documentDiagnostics", documentDiagnostics)
        }
    }

    fun requireCurrent(project: Project, snapshot: PageAnalysisSnapshot) {
        if (project.isDisposed) fail("PROJECT_UNAVAILABLE", "The selected project closed during analysis.")
        if (PsiDocumentManager.getInstance(project).uncommittedDocuments.isNotEmpty() || !snapshot.version.isCurrent(project, 0))
            fail("STALE_SNAPSHOT", "The project changed during analysis. Invoke the tool again for a fresh snapshot.")
        if (snapshot.springInspections?.let { !WicketSpringInspectionReport.isCurrent(project, it) } == true)
            fail("STALE_SNAPSHOT", "The inspection profile changed during analysis. Invoke the tool again.")
    }

    companion object {
        const val MAX_RESPONSE_BYTES = 512 * 1024
        private val GSON = GsonBuilder().serializeNulls().disableHtmlEscaping().create()
        fun errorJson(code: String, message: String, view: WicketMcpReport = WicketMcpReport.STRUCTURE): String = GSON.toJson(JsonObject().apply {
            addProperty("schemaVersion", 1)
            addProperty("tool", view.tool)
            addProperty("status", "error")
            add("error", JsonObject().apply { addProperty("code", code); addProperty("message", message) })
        })
        private fun fail(code: String, message: String): Nothing = throw PageAnalysisFailure(code, message)
    }
}
