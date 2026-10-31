# MCP page structure: Phase 2 first slice

`wicket_get_page_structure` analyzes a source Wicket `WebPage`, `Panel`, or
`FormComponentPanel` using the existing analysis engine and JSON exporter. It
does not require an active editor or a sidebar report. The optional JetBrains
MCP integration and minimum IDE build are unchanged from
[Phase 1](mcp-integration.md).

## Request

```json
{
  "projectPath": "/absolute/path/to/open/project",
  "className": "example.McpPage",
  "moduleName": "mcp-fixture",
  "sourceSet": "main"
}
```

| Parameter | Contract |
| --- | --- |
| `projectPath` | JetBrains' host-provided project selector. Use the exact open project path. The tool obtains that project's coroutine context; it does not select an unrelated active project. |
| `className` | Required Java qualified class name, at most 512 characters. Use dots for nested source classes. File paths and library-only targets are rejected. |
| `moduleName` | Optional exact IntelliJ module name, at most 256 characters. It restricts source lookup to that module, excluding dependency-module declarations. |
| `sourceSet` | `main` (default), `test`, or `any`. Test sources are never an implicit fallback for `main`. |

The target must belong to a configured Java source root in the selected project.
Attached library sources, compiled classes, resource-only roots, and arbitrary
filesystem paths are not targets. Multiple matching declarations produce an
ambiguity error. A module/source-set choice resolves normal duplicate-class
layouts; duplicates that remain within that choice are still rejected.

The first slice uses conventional default markup. An editor-selected locale or
style variant is not implicitly used. Markup inheritance, queues, repeaters,
custom inputs, compiled constants, and other existing supported analysis flow
through the same engine; unsupported patterns retain the same uncertainty.

## Response

Success is one MCP text-content block containing JSON with:

| Field | Meaning |
| --- | --- |
| `schemaVersion` | MCP envelope version **1**. |
| `tool` | `wicket_get_page_structure`. |
| `status` | `ok`: the static analysis completed, including any partial/unresolved findings. |
| `truncated` | Always `false` on success. Oversized reports produce an error instead of a partial graph. |
| `target` | The resolved `className`, `moduleName`, and actual `sourceSet`. |
| `snapshot` | `kind: committed_static_analysis`, `freshness: checked_after_serialization`. |
| `analysis` | The existing deterministic analysis JSON **schema 1**, unchanged. |

`analysis.components` is the existing flat graph, linked by `nodeId` and
`parentNodeId`, with IDs, component types, confidence, and Java/markup locations.
The normal markup tree, matching findings, causes, severities, and warnings are
included. Component paths can be reconstructed from parent links. No model
properties, runtime visibility, or runtime component values are invented.

`status: ok` does not mean that every component is paired or that the runtime
tree is complete. Preserve the distinctions between `MATCHED`, informational
`JAVA_ONLY`/`MARKUP_ONLY`, and unresolved findings. A real, path-redacted response
is included in [the evidence record](mcp-structure-evidence.json).

As with Copy Wicket Context, locations can include absolute source/markup paths
and relevant classpath resource locations. Internal dependency/version lists
are not added to the export. An agent may send returned metadata to its model
provider according to its own configuration.

## Freshness, bounds, and side effects

Each request asynchronously refreshes the selected project's local source roots
and dependency class roots/archives before class lookup. SDK roots are not
recursively refreshed. This makes immediate agent disk edits and new source
files visible to the IDE's VFS/index. It does not save documents or change source
text. IntelliJ handles conflicts with unsaved editor buffers using its normal
document synchronization policy.

The existing engine then runs under smart-mode and committed-document read
constraints. Only values leave the read action. Serialization reuses
`WicketContextExporter`; the captured `ReportVersion` is checked afterward,
including pending editor changes and source/binary/JAR/root versions. A changed
project produces `STALE_SNAPSHOT`, not a successful stale response. This is a
snapshot at the final check, not a live view; invoke the tool again after edits.

The request has a cooperative **30-second budget**, including refresh and waits.
Caller cancellation propagates. Success is limited to **512 KiB of UTF-8 JSON**,
5,000 component nodes, and 10,000 markup elements, in addition to the core's
existing analysis budgets. Large responses are rejected explicitly; graph JSON
is never cut in the middle. These limits do not turn partial core analysis into
a complete runtime claim.

The tool does not replace the sidebar report, navigate, copy to the clipboard,
run configurations, generate code, or modify project sources. Normal IDE VFS
events can invalidate an already displayed sidebar snapshot when external files
actually changed. The analyzer, model, matcher, exporter, editor features, and
Spring workflows remain unchanged.

## Errors

Tool-owned errors below are distinct from transport failures. The targeted
bundled host can expire long blocked sessions with HTTP 404 before an error
envelope is delivered; wire cancellation is not prompt in this SDK. See
[the verified behavior and reconnection guidance](mcp-isolation-transport.md#wire-cancellation-observed-host-limitation).

Tool-owned failures are MCP results with `isError: true` and JSON envelope
`schemaVersion: 1`, `tool`, `status: error`, and `error: {code, message}`.
Messages do not expose raw exception details or unrelated file paths.

| Code | Meaning/action |
| --- | --- |
| `INVALID_REQUEST` | Invalid class name, module name, or source-set value. |
| `MODULE_NOT_FOUND` | The requested exact module does not exist in this project. |
| `CLASS_NOT_FOUND` | No eligible Java source matches the chosen scope. |
| `AMBIGUOUS_CLASS` | Narrow the module/source-set choice or resolve remaining duplicate declarations. |
| `UNSUPPORTED_TARGET` | The selected declaration is not a supported Wicket root, or its Wicket types cannot resolve. |
| `ANALYSIS_UNAVAILABLE` | Source/dependency data became unavailable or could not be analyzed. |
| `STALE_SNAPSHOT` | Files, documents, PSI, or roots changed during the request; retry. |
| `RESPONSE_TOO_LARGE` | Analyze a smaller source component separately. No graph was truncated. |
| `TIMED_OUT` | Refresh, indexing, document commits, or analysis exceeded the cooperative budget. |
| `PROJECT_UNAVAILABLE` | The selected project is unavailable or closed. |
| `ANALYSIS_FAILED` | Unexpected failure; consult the IDE log. |

The internal capture API also guards `INDEXING` and `UNCOMMITTED_DOCUMENTS`;
normal MCP calls wait for those conditions rather than reading unsafe PSI.
JetBrains' own project-selection, argument-decoding, transport, and cancellation
errors remain host errors; they are not rewritten into WicketLens envelopes.

## Verification and reproduction

The 32 adapter fixtures cover equality with the existing export, Page/Panel/
FormComponentPanel roots, uncertainty, missing/unsupported/library/out-of-root
targets, request validation, exact module and source-set selection, ambiguity,
stale Java/HTML/roots, pending editor commits, cancellation, deterministic/no-write
behavior, unchanged sidebar state, and response byte/graph limits. These fixtures
run with the optional MCP Server plugin disabled.

The full regression run passes **1,586 tests**, with no failures, errors, or skips.
Packaging and project/structure checks pass. Binary verification against IDEA
262.10968.63 is **Compatible**, with the existing four deprecated and six
experimental API notices. Current MCP classes and the optional descriptor match
the ZIP, which contains one plugin JAR and no added framework libraries.

Start the existing isolated harness with a disposable, configured two-module
Wicket project, real Java 25 SDK, and existing fixture libraries:

```sh
./gradlew runMcpSpike -PmcpStructureFixture=true \
  -PmcpSpikeTools=wicketlens_mcp_probe,wicket_get_page_structure
python3 tools/check_mcp_structure.py
```

Wait for the host's server-started message before the Python call. The generated
project is `build/mcp-structure/project`; its modules are `mcp-fixture` and
`mcp-other`, both declaring `example.McpPage`. The Python client reuses the Phase
1 HTTP client. It checks module selection, ambiguity and other errors, three
matched bindings and their parent link, repeatability, the original probe,
immediate external Java/HTML edits, and a newly created source pair. Temporary
fixture edits are restored and source checksums are verified. The fixture
preparer refuses to overwrite an unmarked, nonempty project.

Codex CLI 0.166.0 also successfully invoked the final tool once, with ephemeral
settings and only this semantic tool enabled, then reported `title`, `form`, and
`form:name`, all MATCHED, with the response's partial-static-analysis caveat.
No file or command tools were used. See [the evidence](mcp-structure-evidence.json).
Client configuration is the same as [Phase 1](mcp-integration.md), substituting
the semantic tool name, the fixture project path, and these class/module inputs.
Stop the test host with Ctrl+C. Normal IDE/client settings are unchanged.

## Remaining MCP work

The [validation tool](mcp-validation.md) now shares this request pipeline and
projects existing findings without changing this structure response.
[Component context](mcp-component-context.md) now selects from the same graph.
Phase 3 now verifies [lifecycle and concurrent requests](mcp-reliability.md) and
[multiple projects, larger fixtures and transport behavior](mcp-isolation-transport.md).
The [setup guide](mcp-client-setup.md) and [release checklist](release-checklist.md)
now cover the pinned graphical/client combination. Broader SDK/client matrices
and retesting the upstream transport limitation remain open. Real-application
accuracy/performance evaluation remains deferred.
