package dev.wicketlens.routes

import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiNameHelper
import dev.wicketlens.mcp.PageAnalysisFailure
import dev.wicketlens.mcp.WicketPageAnalysisRequest
import dev.wicketlens.ui.ReportVersion

data class WicketRoutesSnapshot(val routes: WicketRoutes, val version: ReportVersion, val structureStamp: Long,
    val moduleName: String?, val applicationClass: String?, val sourceSet: String)

/** The sidebar and MCP share one analyzer, value snapshot, freshness and JSON projection. */
class WicketRoutesRequest internal constructor(private val timeoutMillis: Long) {
    constructor() : this(30_000)
    suspend fun execute(project: Project, moduleName: String? = null, applicationClass: String? = null, sourceSet: String = "main"): String =
        request(project, moduleName, applicationClass, sourceSet, serialize = true).second

    suspend fun prepare(project: Project, moduleName: String? = null, applicationClass: String? = null, sourceSet: String = "main"): WicketRoutesSnapshot =
        request(project, moduleName, applicationClass, sourceSet, serialize = false).first

    private suspend fun request(project: Project, moduleName: String?, applicationClass: String?, sourceSet: String, serialize: Boolean): Pair<WicketRoutesSnapshot, String> {
        var captured: WicketRoutesSnapshot? = null
        val json = WicketPageAnalysisRequest(timeoutMillis).readSnapshot(project, { validate(project, moduleName, applicationClass, sourceSet) },
            { capture(project, moduleName, applicationClass, sourceSet).also { captured = it } },
            { if (serialize) render(it) else "" }, { requireCurrent(project, it) })
        return requireNotNull(captured) to json
    }

    fun capture(project: Project, moduleName: String? = null, applicationClass: String? = null, sourceSet: String = "main"): WicketRoutesSnapshot {
        ProgressManager.checkCanceled()
        validate(project, moduleName, applicationClass, sourceSet)
        if (DumbService.isDumb(project)) fail("INDEXING", "Wait for indexing before discovering mounted URLs.")
        if (PsiDocumentManager.getInstance(project).uncommittedDocuments.isNotEmpty())
            fail("UNCOMMITTED_DOCUMENTS", "Commit editor documents before taking the route snapshot.")
        val routes = WicketRouteAnalyzer().analyze(project, moduleName, applicationClass, sourceSet)
        if (applicationClass != null && routes.applications.isEmpty() && !routes.truncated)
            fail("APPLICATION_NOT_FOUND", "No concrete Java WebApplication matches the selected project/module/source set.")
        val version = ReportVersion.captureSources(project, routes.sourceFiles)
        return WicketRoutesSnapshot(routes, version, VirtualFileManager.VFS_STRUCTURE_MODIFICATIONS.modificationCount,
            moduleName, applicationClass, sourceSet)
    }

    fun requireCurrent(project: Project, snapshot: WicketRoutesSnapshot) {
        if (project.isDisposed) fail("PROJECT_UNAVAILABLE", "The selected project closed during route discovery.")
        if (PsiDocumentManager.getInstance(project).uncommittedDocuments.isNotEmpty() || !snapshot.version.isCurrent(project, 0) ||
            VirtualFileManager.VFS_STRUCTURE_MODIFICATIONS.modificationCount != snapshot.structureStamp)
            fail("STALE_SNAPSHOT", "The project changed during route discovery. Refresh mounted URLs.")
    }

    fun render(snapshot: WicketRoutesSnapshot): String {
        ProgressManager.checkCanceled()
        if (snapshot.routes.applications.size > WicketRouteAnalyzer.MAX_APPLICATIONS || snapshot.routes.applications.sumOf { it.mounts.size } > WicketRouteAnalyzer.MAX_MOUNTS)
            fail("RESPONSE_TOO_LARGE", "The route snapshot exceeds application/mount limits. Select a narrower scope.")
        val json = JsonObject().apply {
            addProperty("schemaVersion", 1); addProperty("tool", TOOL); addProperty("status", "ok")
            addProperty("truncated", snapshot.routes.truncated)
            add("target", GSON.toJsonTree(mapOf("moduleName" to snapshot.moduleName, "applicationClass" to snapshot.applicationClass, "sourceSet" to snapshot.sourceSet)))
            add("snapshot", GSON.toJsonTree(mapOf("kind" to "committed_static_analysis", "freshness" to "checked_after_serialization")))
            add("routes", JsonObject().apply {
                addProperty("schemaVersion", 1)
                addProperty("runtimeRoutesComplete", false)
                addProperty("applicationRelativePaths", true)
                add("notes", GSON.toJsonTree(snapshot.routes.notes))
                add("applications", GSON.toJsonTree(snapshot.routes.applications))
            })
        }.let(GSON::toJson)
        if (json.toByteArray(Charsets.UTF_8).size > WicketPageAnalysisRequest.MAX_RESPONSE_BYTES)
            fail("RESPONSE_TOO_LARGE", "The route response exceeds 512 KiB. Select an application or module; no JSON was truncated.")
        return json
    }

    private fun validate(project: Project, moduleName: String?, applicationClass: String?, sourceSet: String) {
        if (project.isDisposed || project.isDefault) fail("PROJECT_UNAVAILABLE", "The selected project is unavailable.")
        if (moduleName != null && (moduleName.isBlank() || moduleName.length > 256) ||
            applicationClass != null && (applicationClass.length !in 1..512 || !PsiNameHelper.getInstance(project).isQualifiedName(applicationClass)) ||
            sourceSet !in setOf("main", "test", "any")) fail("INVALID_REQUEST", "Supply optional exact moduleName/applicationClass and sourceSet main, test, or any.")
        if (moduleName != null && ModuleManager.getInstance(project).modules.none { it.name == moduleName })
            fail("MODULE_NOT_FOUND", "No open module matches moduleName in the selected project.")
    }

    companion object {
        const val TOOL = "wicket_get_routes"
        private val GSON = GsonBuilder().serializeNulls().disableHtmlEscaping().create()
        fun errorJson(code: String, message: String): String = GSON.toJson(mapOf("schemaVersion" to 1, "tool" to TOOL,
            "status" to "error", "error" to mapOf("code" to code, "message" to message)))
        private fun fail(code: String, message: String): Nothing = throw PageAnalysisFailure(code, message)
    }
}
