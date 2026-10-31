# MCP page validation

`wicket_validate_page` returns a focused view of the **existing** Java/markup
findings. It shares the structure tool's request pipeline and does not introduce
a new checker, change severity/confidence, apply fixes, or judge runtime validity.

## Request and shared behavior

```json
{
  "projectPath": "/absolute/path/to/open/project",
  "className": "example.ValidationPage",
  "moduleName": "mcp-fixture",
  "sourceSet": "main"
}
```

The parameters and defaults match [the structure tool](mcp-page-structure.md):
`projectPath` is selected by JetBrains' host; `className` is required;
`moduleName` is an optional exact name; `sourceSet` is `main` (default), `test`,
or `any`. Only configured Java source roots in that project are eligible.
Ambiguous declarations and unsupported/library-only targets are rejected.
Conventional default markup is used rather than an editor-selected variant.

Both tools use `WicketPageAnalysisRequest` for scoped selection, asynchronous
source/dependency refresh, smart/committed PSI, core analysis, version capture,
post-serialization freshness checks, cancellation, and error handling. The
structure request's former Kotlin source names remain aliases to the shared
implementation. No second analysis engine or transport was added.

The cooperative budget remains **30 seconds**. Success is bounded to **512 KiB
of UTF-8 JSON**, with the shared 5,000-component/10,000-markup-element caps and
the core's existing budgets. Oversized responses are errors, not truncated
graphs. The normal editor, sidebar, clipboard, inspections, JSON schema 1 export,
and Spring/scaffolding flows are unchanged.

## Response and interpretation

Success is one MCP text-content block containing an envelope with
`schemaVersion: 1`, `tool: wicket_validate_page`, `status: ok`, `truncated: false`,
and the same `target` and `snapshot` metadata as the structure tool. Its payload
is `validation`, containing:

| Field | Meaning |
| --- | --- |
| `analysisSchemaVersion` | Version **1** of the existing analysis/export model. |
| `qualifiedClassName` | Resolved source class. |
| `matchingAvailable`, `markupAvailable` | Explicit availability flags. Empty arrays alone do not imply a complete or successful pairing analysis. |
| `scopeComplete` | Separate original `java`, `markup`, and `matching` flags. Missing markup/matching produces `null` for that flag. These describe static coverage, not runtime completeness. |
| `summary` | Finding count and counts by original code, severity, and confidence; counts for each diagnostic channel. Only present categories occur in the count maps; absent categories mean zero. |
| `findings` | Exact existing matching findings, including `MATCHED`, messages, confidence, severity, Java/markup node IDs and locations, and causes. |
| `warnings` | Exact existing report warning array. |
| `componentDiagnostics` | Nonempty component diagnostic arrays, with node ID, Wicket ID, type, and Java location. |
| `markupDiagnostics` | Existing markup-report diagnostics. |
| `markupElementDiagnostics` | Nonempty element diagnostic arrays, with element/node identity, tag, Wicket ID, and locations. |
| `documentDiagnostics` | Existing document-level syntax/PSI diagnostics. |

The projection uses the existing `WicketContextExporter`; it does not reinterpret
the findings. Diagnostic channels can overlap, as in the core report, so their
counts are **not a deduplicated incident total**. Full component and markup trees
are omitted; use `wicket_get_page_structure` when that context is needed.

`status: ok` means the request completed. There is deliberately no `valid` or
`hasErrors` verdict. In particular:

- `JAVA_ONLY` and `MARKUP_ONLY` keep their original informational severity.
- `INFERRED` and `UNRESOLVED` confidence and uncertainty causes stay visible.
- Missing markup or unsupported setup remains unresolved rather than a clean
  report or a definite runtime error.
- Java/markup binding analysis does not run the whole IntelliJ Problems system
  or the separate Spring field/serialization inspections. It does not verify
  actual runtime component trees, model values, or every lifecycle path.

For the disposable mismatch fixture, the real response has one `JAVA_ONLY` and
one `MARKUP_ONLY`, both `INFO` and `INFERRED`, plus a `PARTIAL_ANALYSIS` warning.
Codex reported those qualifications from the actual tool response. The complete
path-redacted [evidence](mcp-validation-evidence.json) includes matched,
mismatched, and unresolved cases.

Tool errors use the same versioned error envelope and codes as the structure
tool, with `tool: wicket_validate_page` and MCP `isError: true`. `STALE_SNAPSHOT`
requires another call; lookup failures do not select a different class.
Host project-selection/decoding and cancellation failures remain host errors.

## Verification and reproduction

The focused run passes **55 cases**: all 32 existing structure/request fixtures
and 23 new validation cases. Projection cases check exact export equality,
informational mismatches, partial/unavailable scope, every diagnostic channel,
source locations, independent summary dimensions, zero counts, structure-wire
preservation, errors, response bounds, stale edits, deterministic/no-write
behavior, and an asynchronous call without sidebar publication.

The full suite passes **1,609 tests**, with no failures, errors, or skipped tests.
Packaging and project/structure checks pass. Binary verification against IDEA
262.10968.63 is **Compatible**, with the existing four deprecated and six
experimental API notices. Shared MCP classes and the optional descriptor match
the ZIP; no additional framework/transport libraries are bundled.

Use the existing isolated fixture harness:

```sh
./gradlew runMcpSpike -PmcpStructureFixture=true \
  -PmcpSpikeTools=wicketlens_mcp_probe,wicket_get_page_structure,wicket_validate_page
python3 tools/check_mcp_validation.py
python3 tools/check_mcp_structure.py
```

Wait for the host's server-started message before the Python checks. The fixture
contains `McpPage`, `ValidationPage`, and `UnresolvedPage` in `mcp-fixture` and
the existing duplicate-class case in `mcp-other`. The validation client compares
its output to the live structure output, verifies errors/uncertainty, temporarily
corrects a fixture ID on disk, confirms `MATCHED`, then restores the original
file. Source checksums remain unchanged after the checks. The original structure
and probe checks also pass with validation present.

Codex CLI 0.166.0 successfully invoked only the validation tool with ephemeral
settings, no file/command tools, and an empty client workspace. Its summary
preserved the informational findings, inferred confidence, partial-analysis
warning, and runtime limitation. Normal IDE/client configuration was unchanged;
the disposable host was stopped after acceptance. For client setup, use
[the existing MCP guide](mcp-integration.md), selecting `wicket_validate_page` and
the request above. Relevant source paths/IDs in responses may be sent to an
agent's model provider according to that client's configuration.

## Remaining work

[Component context](mcp-component-context.md) now selects from the same captured graph.
Broader Phase 3 reliability/client/IDE matrices and the separately deferred
real-application accuracy/performance study remain open.
