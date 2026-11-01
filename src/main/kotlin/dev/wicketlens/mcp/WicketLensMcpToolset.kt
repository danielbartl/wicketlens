package dev.wicketlens.mcp

import com.intellij.mcpserver.McpToolset
import com.intellij.mcpserver.McpExpectedError
import com.intellij.mcpserver.project
import com.intellij.mcpserver.annotations.McpDescription
import com.intellij.mcpserver.annotations.McpTool
import com.intellij.mcpserver.annotations.McpToolHintValue
import com.intellij.mcpserver.annotations.McpToolHints
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.ProcessCanceledException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext

/** Optional read-only MCP tools; semantic work delegates to the existing analysis services. */
class WicketLensMcpToolset : McpToolset {
    // Expose the bounded read-only tools with the host's default experimental filter off.
    override fun isExperimental() = false

    @Suppress("FunctionName")
    @McpTool(title = "WicketLens MCP connectivity probe")
    @McpDescription("Verify that WicketLens is reachable through the JetBrains MCP server. Returns static, versioned JSON; does not read or modify project files and does not perform semantic analysis.")
    @McpToolHints(readOnlyHint = McpToolHintValue.TRUE, destructiveHint = McpToolHintValue.FALSE,
        idempotentHint = McpToolHintValue.TRUE, openWorldHint = McpToolHintValue.FALSE)
    suspend fun wicketlens_mcp_probe(): String =
        """{"schemaVersion":1,"plugin":"WicketLens","tool":"wicketlens_mcp_probe","phase":1,"status":"ok","semanticAnalysis":false}"""

    @Suppress("FunctionName")
    @McpTool(title = "Get Wicket page structure")
    @McpDescription("Analyze a source Wicket WebPage, Panel, or FormComponentPanel in the host-selected project. Returns MCP envelope schema 1 containing the existing analysis schema 1, with component hierarchy, Java/markup locations, findings, and uncertainty. Uses conventional default markup, not an editor-selected locale/style variant. Requires projectPath supplied by the host. Waits up to 30 seconds for smart/committed PSI and analysis, checks freshness after serialization, and rejects responses over 512 KiB rather than truncating. Does not save files, change sidebar/clipboard state, or provide runtime/model-value guarantees. Reinvoke after edits. Tool errors contain versioned JSON codes.")
    @McpToolHints(readOnlyHint = McpToolHintValue.TRUE, destructiveHint = McpToolHintValue.FALSE,
        idempotentHint = McpToolHintValue.TRUE, openWorldHint = McpToolHintValue.FALSE)
    suspend fun wicket_get_page_structure(
        @McpDescription("Java qualified source class name, using dots for nested classes. No file paths or library-only targets.") className: String,
        @McpDescription("Exact IntelliJ module name to disambiguate duplicate classes. Default: no module restriction within the selected project.") moduleName: String? = null,
        @McpDescription("Source-set selection: main, test, or any. Default: main. Ambiguous matches are rejected.") sourceSet: String = "main",
    ): String = analyze(WicketMcpReport.STRUCTURE, className, moduleName, sourceSet)

    @Suppress("FunctionName")
    @McpTool(title = "Validate Wicket page bindings")
    @McpDescription("Return existing static Java/markup findings for a source Wicket WebPage, Panel, or FormComponentPanel in the host-selected project. Requires host projectPath. Uses default markup and the same module/source-set selection, external-file refresh, smart/committed reads, 30-second budget, freshness check, and 512-KiB response limit as wicket_get_page_structure. Returns envelope schema 1 with exact existing findings, causes, locations, severities, diagnostic channels, scope flags, and summary counts. status ok means the request completed; it does not mean runtime validity. JAVA_ONLY and MARKUP_ONLY remain informational; unresolved evidence is preserved. Read-only, no fixes or sidebar/clipboard actions. Reinvoke after edits. Tool errors contain versioned JSON codes.")
    @McpToolHints(readOnlyHint = McpToolHintValue.TRUE, destructiveHint = McpToolHintValue.FALSE,
        idempotentHint = McpToolHintValue.TRUE, openWorldHint = McpToolHintValue.FALSE)
    suspend fun wicket_validate_page(
        @McpDescription("Java qualified source class name, using dots for nested classes. No file paths or library-only targets.") className: String,
        @McpDescription("Exact IntelliJ module name. Default: no module restriction within the selected project; duplicates are rejected.") moduleName: String? = null,
        @McpDescription("Source-set selection: main, test, or any. Default: main.") sourceSet: String = "main",
    ): String = analyze(WicketMcpReport.VALIDATION, className, moduleName, sourceSet)

    @Suppress("FunctionName")
    @McpTool(title = "Get Wicket component context")
    @McpDescription("Select an observed Java component from the existing Wicket graph for a source WebPage, Panel, or FormComponentPanel. Supply exactly one componentPath or nodeId, plus host projectPath. Returns envelope schema 1 with the exported component, parent, ancestors, direct children, exact findings, associated markup elements, scope flags and diagnostics. Paths use colon-separated IDs relative to the root, empty for the root; logical repeater items consume no segment (rows:name), and runtime row IDs are unavailable. Duplicate/unknown path segments are rejected; use nodeId from a fresh wicket_get_page_structure result. Node IDs are snapshot-local, not durable handles. No runtime model values or property-expression analysis. Uses default markup, shared module/source-set selection, external refresh, smart/committed reads, 30-second budget, freshness check and 512-KiB limit. Read-only, no sidebar or clipboard changes. Reinvoke after edits; errors contain versioned JSON codes.")
    @McpToolHints(readOnlyHint = McpToolHintValue.TRUE, destructiveHint = McpToolHintValue.FALSE,
        idempotentHint = McpToolHintValue.TRUE, openWorldHint = McpToolHintValue.FALSE)
    suspend fun wicket_get_component_context(
        @McpDescription("Java qualified source class name. No file paths or library-only targets.") className: String,
        @McpDescription("Colon-separated observed component IDs, for example form:name. Empty selects the root. Omit when supplying nodeId.") componentPath: String? = null,
        @McpDescription("Exact nodeId from the current page structure, for ambiguous, dynamic or template nodes. Omit when supplying componentPath.") nodeId: String? = null,
        @McpDescription("Exact IntelliJ module name. Default: no module restriction; duplicate classes are rejected.") moduleName: String? = null,
        @McpDescription("Source-set selection: main, test, or any. Default: main.") sourceSet: String = "main",
    ): String = analyze(WicketMcpReport.CONTEXT, className, moduleName, sourceSet, componentPath, nodeId)

    @Suppress("FunctionName")
    @McpTool(title = "Get Wicket injected dependencies")
    @McpDescription("Inspect injection fields on a source Wicket page/panel and its discovered source components using the existing Dependencies analyzer. Requires host projectPath and a Java source className. Returns envelope schema 1 and dependencies schema 1 with owner node IDs/paths, field lookup intent, required policy, source candidates/aliases, locations, declared profile conditions and uncertainty, and generic FactoryBean products versus &name factory instances. Empty candidates are not missing-bean findings. No active-profile evaluation, runtime bindings, model values or Spring inspection findings. Same module/source-set selection, external refresh, smart/committed reads, 30-second deadline, freshness check and 512-KiB response limit as other Wicket tools. Existing discovery limits are explicit: truncated/partial/candidatesTruncated remain visible. Read-only; no sidebar, clipboard, file or configuration changes. Reinvoke after edits; errors use versioned JSON codes.")
    @McpToolHints(readOnlyHint = McpToolHintValue.TRUE, destructiveHint = McpToolHintValue.FALSE,
        idempotentHint = McpToolHintValue.TRUE, openWorldHint = McpToolHintValue.FALSE)
    suspend fun wicket_get_injected_dependencies(
        @McpDescription("Java qualified source WebPage, Panel, or FormComponentPanel class. No file paths or library targets.") className: String,
        @McpDescription("Exact IntelliJ module name to disambiguate duplicate classes. Default: no module restriction.") moduleName: String? = null,
        @McpDescription("Source-set selection: main, test, or any. Default: main; ambiguous matches are rejected.") sourceSet: String = "main",
    ): String = analyze(WicketMcpReport.DEPENDENCIES, className, moduleName, sourceSet)

    @Suppress("FunctionName")
    @McpTool(title = "Get Wicket Spring inspection findings")
    @McpDescription("Run the existing native WicketLens Spring inspections for a source Wicket page/panel, discovered source component types, bounded source ancestors, and reviewed Wicket application in the same module/source set. Requires host projectPath. Returns envelope schema 1 and springInspections schema 1 with inspection IDs/names, exact native messages, configured severity, highlight type, source ranges and graph node IDs; disabled checks and native suppressions are respected. Includes inspection-profile settings, coverage limitations and truncation. Empty findings are not proof of valid injection, serialization, proxying or runtime wiring. No missing-bean inference, fixes, runtime values, profile changes or sidebar/clipboard changes. Reuses smart/committed reads, disk refresh, 30-second deadline, project isolation, freshness checks (including enabled/severity profile state) and 512-KiB limit. Scope is limited to 64 source types, 12 ancestors per type and 256 findings. Reinvoke after edits/profile changes; errors use versioned JSON codes.")
    @McpToolHints(readOnlyHint = McpToolHintValue.TRUE, destructiveHint = McpToolHintValue.FALSE,
        idempotentHint = McpToolHintValue.TRUE, openWorldHint = McpToolHintValue.FALSE)
    suspend fun wicket_get_spring_inspections(
        @McpDescription("Qualified Java source WebPage, Panel or FormComponentPanel class. No file paths or library-only roots.") className: String,
        @McpDescription("Exact IntelliJ module name. Default: no module restriction; ambiguous targets are rejected.") moduleName: String? = null,
        @McpDescription("Source-set selection: main, test or any. Default: main.") sourceSet: String = "main",
    ): String = analyze(WicketMcpReport.SPRING_INSPECTIONS, className, moduleName, sourceSet)

    private suspend fun analyze(view: WicketMcpReport, className: String, moduleName: String?, sourceSet: String,
        componentPath: String? = null, nodeId: String? = null): String {
        try {
            return WicketPageAnalysisRequest().execute(currentCoroutineContext().project, className, moduleName, sourceSet, view, componentPath, nodeId)
        } catch (failure: PageAnalysisFailure) {
            throw McpExpectedError(WicketPageAnalysisRequest.errorJson(failure.code, failure.message!!, view))
        } catch (cancelled: CancellationException) { throw cancelled
        } catch (cancelled: ProcessCanceledException) { throw cancelled
        } catch (hostError: McpExpectedError) { throw hostError
        } catch (failure: Exception) {
            logger<WicketLensMcpToolset>().warn("Wicket MCP analysis failed for ${view.tool}", failure)
            throw McpExpectedError(WicketPageAnalysisRequest.errorJson("ANALYSIS_FAILED", "Analysis failed. See the IDE log for details.", view))
        }
    }
}
