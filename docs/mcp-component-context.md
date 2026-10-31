# MCP component context

`wicket_get_component_context` selects a component from the same current graph
used by [page structure](mcp-page-structure.md) and [validation](mcp-validation.md).
It adds a projection of the existing export, with no new parser, binding rules,
runtime dependencies, UI actions, or model evaluation.

## Request and selection

```json
{
  "projectPath": "<open-project>",
  "className": "example.CustomerPage",
  "moduleName": "web",
  "sourceSet": "main",
  "componentPath": "customerForm:email"
}
```

`projectPath` is injected and validated by JetBrains' host. Class/module/source-set
selection follows the existing tools: only Java source Page, Panel, or
FormComponentPanel subclasses in the selected project; sourceSet defaults to
`main`, with `test` and `any` also supported. Exact module selection disambiguates
classes; ambiguous classes are rejected.

Supply **exactly one** selector:

- `componentPath`: colon-separated IDs relative to the graph root. An empty
  string selects the root. Each segment must have a unique observed child;
  duplicate prefixes are rejected even if later segments would disambiguate.
  An unknown sibling ID prevents unique path selection. Paths are case-sensitive
  and are not trimmed or decoded. Empty interior segments are invalid.
- `nodeId`: exact ID from a freshly fetched page structure. This supports
  duplicate IDs, IDs containing colons, unknown IDs, detached components, and
  logical template nodes. IDs are snapshot-local graph identities, **not durable
  handles**; after edits fetch the structure again. No ID pinning across requests
  is promised. A node that is no longer present produces `COMPONENT_NOT_FOUND`.

Selectors are limited to 4,096 characters. Component paths select observed Java
nodes, not HTML-only findings. Use validation or structure for HTML-only entries.
Selecting a path does not assert that the runtime hierarchy is complete or that
an observed node will execute; confidence, findings, and scope flags still apply.

Repeater item templates consume no path segment: `rows:name` selects the template
child, following the existing [repeater convention](queueing-repeaters.md).
The response preserves the logical item in ancestry and parent links. Select
the item itself by node ID. Runtime item IDs, row counts, and paths such as
`rows:0:name` are not inferred.

## Response

Success is one MCP text block containing JSON with the shared schema **1**
envelope (`tool`, `status`, `truncated`, `target`, `snapshot`) and a `context`
object. `status: ok` means selection completed, not runtime validity.

| Field | Meaning |
| --- | --- |
| `analysisSchemaVersion` | Existing export schema, currently **1**. |
| `component` | Exact exported component, including type, ID, confidence, source locations, parent identity, diagnostics, matcher status and template/queue metadata where available. |
| `componentPath` | Unique observed logical path; empty for root, null for unknown, ambiguous, detached or item-template addresses. |
| `parent` | Exact exported direct parent, or null when unavailable/root. |
| `ancestors` | Exported ancestors in root-to-parent order, retaining item templates. An unresolved chain can be partial. |
| `children` | Exact exported direct graph children, including logical item templates. No recursive subtree expansion. |
| `hierarchyResolved` | Whether the observed ancestor chain reaches the graph root. This is separate from confidence/runtime attachment. |
| `findings` | Exact existing findings whose `javaNodeId` equals the selection; causes, severities and locations unchanged. |
| `markupElements` | Exact markup elements referenced by those findings' `markupNodeId`. These are matcher associations, not a new binding claim. Equal ID strings alone do not create a link. |
| `markupPath` | Existing resolved companion resource, including for the root, which has no corresponding element. |
| `matchingAvailable`, `markupAvailable` | Explicit availability flags. |
| `scopeComplete` | Unchanged `java`, `markup`, `matching` flags; unavailable markup/matching is null. |
| `warnings`, `markupDiagnostics`, `documentDiagnostics` | Existing page-wide diagnostic channels, retained because they can affect the selection. Component/ancestor and associated-element diagnostics remain inside their exported objects. |

Structured Wicket models and property expressions are absent from the current
graph. The response omits model information; it does not inspect values or infer
`PropertyModel` bindings. Missing markup and partial analysis remain explicit.

## Freshness, bounds and errors

The shared request pipeline refreshes source roots and physical dependency
archives, waits for smart/committed PSI, captures a value-only report, and checks
freshness after serialization. Unsaved committed editor text is analyzed without
saving. Requests have the same 30-second budget, cancellation/disposal behavior,
5,000-component/10,000-markup-element limits and 512-KiB UTF-8 response limit.
Oversized responses fail rather than truncate. Sidebar and clipboard state are
untouched. No cross-request graph cache is introduced.

In addition to the [shared errors](mcp-page-structure.md#errors), selection can
return `COMPONENT_NOT_FOUND`, `AMBIGUOUS_COMPONENT`, or
`UNRESOLVED_COMPONENT_PATH`. Invalid selectors return `INVALID_REQUEST`.
Tool-owned failures use MCP `isError: true` and the shared versioned error
envelope, naming `wicket_get_component_context`. Caller cancellation propagates.

## Verification and reproduction

The focused run passes **77 tests**, including **22 new context cases** covering
nested paths, sibling ambiguity, unknown IDs, detached/cyclic graphs, node-ID
selection, root context, exact export/markup identities, missing markup,
uncertainty, repeater templates, UTF-8/graph limits, stale edits, deterministic
read-only responses and the shared async pipeline without sidebar publication.

The complete suite passes **1,631 tests**, with no failures, errors or skips.
The installable ZIP contains one WicketLens JAR; all 16 MCP class files and the
optional descriptor match the current build. No MCP/transport/Wicket/Spring
libraries are bundled.
Packaging and project/structure checks pass. Binary verification against the
target IDEA build is **Compatible**, with the existing four deprecated and
six experimental API notices unchanged.

Use the existing isolated host and standard-library client:

```sh
./gradlew runMcpSpike -PmcpStructureFixture=true \
  -PmcpSpikeTools=wicketlens_mcp_probe,wicket_get_page_structure,wicket_validate_page,wicket_get_component_context
python3 tools/check_mcp_context.py
python3 tools/check_mcp_structure.py
python3 tools/check_mcp_validation.py
```

The fixture includes two modules, nested fields, a repeater template, duplicate
IDs and unresolved setup. The context checker compares objects with live
structure, checks selection errors, temporarily edits/restores a Java/HTML pair,
and verifies immediate external-edit freshness and original source checksums.
These checks pass against IDEA **2026.2.3 / IU-262.10968.63**, alongside the
existing structure/probe and validation protocol checks. Installed Codex CLI
**0.166.0** successfully invokes only the context tool, with no command or
file-edit tools, and reports the real type, parent, markup and uncertainty.
The portable [evidence record](mcp-context-evidence.json) contains the actual
protocol responses and completed Codex call with fixture paths redacted.
The isolated host is stopped; normal IDE/client settings are unchanged.
For an actual Codex call, follow [the integration guide](mcp-integration.md)
with only `wicket_get_component_context` enabled and request `example.McpPage`,
module `mcp-fixture`, path `form:name` in the disposable fixture project.

Phase 2's three semantic tools are implemented. Phase 3 now verifies
[lifecycle/concurrent requests](mcp-reliability.md) and
[multiple projects, larger fixtures and transport behavior](mcp-isolation-transport.md).
[Graphical/client setup](mcp-client-setup.md) and [preview release checks](release-checklist.md)
are now implemented for the pinned versions. Broader compatibility and retesting
the documented upstream transport limitation remain open.
Real-application accuracy/performance evaluation remains separately deferred.
