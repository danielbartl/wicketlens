package dev.wicketlens.spring

import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.psi.PsiDocumentManager
import dev.wicketlens.mcp.PageAnalysisFailure
import dev.wicketlens.mcp.WicketDependencyExport
import dev.wicketlens.mcp.WicketMcpReport
import dev.wicketlens.mcp.WicketPageAnalysisRequest
import dev.wicketlens.ui.ReportVersion

internal data class WicketProjectDependencySnapshot(
    val dependencies: WicketProjectDependencies,
    val version: ReportVersion,
    val structureStamp: Long,
)

internal class WicketProjectDependencyRequest(private val timeoutMillis: Long = 30_000) {
    suspend fun prepare(project: Project): WicketProjectDependencySnapshot {
        var snapshot: WicketProjectDependencySnapshot? = null
        request(project, null, "any") { snapshot = it; "" }
        return requireNotNull(snapshot)
    }

    suspend fun execute(project: Project, moduleName: String?, sourceSet: String): String =
        request(project, moduleName, sourceSet) { snapshot -> render(snapshot, moduleName, sourceSet) }

    private suspend fun request(project: Project, moduleName: String?, sourceSet: String,
        render: (WicketProjectDependencySnapshot) -> String): String =
        WicketPageAnalysisRequest(timeoutMillis).readSnapshot(project, {
            if (sourceSet !in setOf("main", "test", "any") || moduleName != null && (moduleName.isBlank() || moduleName.length > 256))
                throw PageAnalysisFailure("INVALID_REQUEST", "Supply an optional exact moduleName and sourceSet main, test, or any.")
            if (moduleName != null && ModuleManager.getInstance(project).modules.none { it.name == moduleName })
                throw PageAnalysisFailure("MODULE_NOT_FOUND", "No open module matches moduleName in the selected project.")
        }, {
            val dependencies = WicketProjectDependencyAnalyzer.analyze(project, moduleName, sourceSet)
            WicketProjectDependencySnapshot(dependencies, ReportVersion.captureSources(project, dependencies.sourceFiles.toList()),
                VirtualFileManager.VFS_STRUCTURE_MODIFICATIONS.modificationCount)
        }, render, { requireCurrent(project, it) })

    fun requireCurrent(project: Project, snapshot: WicketProjectDependencySnapshot) = runReadAction {
        if (project.isDisposed || PsiDocumentManager.getInstance(project).uncommittedDocuments.isNotEmpty() ||
            !snapshot.version.isCurrent(project, 0) ||
            snapshot.structureStamp != VirtualFileManager.VFS_STRUCTURE_MODIFICATIONS.modificationCount)
            throw PageAnalysisFailure("STALE_SNAPSHOT", "Spring bean results are out of date. Refresh to see current source.")
    }

    private fun render(snapshot: WicketProjectDependencySnapshot, moduleName: String?, sourceSet: String): String {
        val dependencies = snapshot.dependencies
        val result = JsonObject().apply {
            addProperty("schemaVersion", 1)
            addProperty("tool", WicketMcpReport.DEPENDENCIES.tool)
            addProperty("status", "ok")
            addProperty("truncated", dependencies.truncated || dependencies.fields.any { it.field.candidatesTruncated })
            add("target", JsonObject().apply {
                addProperty("className", null as String?)
                addProperty("moduleName", moduleName)
                addProperty("sourceSet", sourceSet)
            })
            add("snapshot", JsonObject().apply {
                addProperty("kind", "committed_static_analysis")
                addProperty("freshness", "checked_after_serialization")
            })
            add("dependencies", WicketDependencyExport.renderProject(dependencies))
        }
        val json = GSON.toJson(result)
        if (json.toByteArray(Charsets.UTF_8).size > WicketPageAnalysisRequest.MAX_RESPONSE_BYTES)
            throw PageAnalysisFailure("RESPONSE_TOO_LARGE", "The Spring bean inventory exceeds 512 KiB. Select a narrower module or source set.")
        return json
    }

    private companion object {
        val GSON = GsonBuilder().serializeNulls().disableHtmlEscaping().create()
    }
}
