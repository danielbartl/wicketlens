# MCP reliability: lifecycle and concurrent requests

This is the first Phase 3 slice. It hardens the shared request pipeline used by
all three [semantic tools](mcp-integration.md) without changing their wire
contracts, analysis, exports, response limits, or optional-plugin behavior.

## Project lifecycle

Each request now registers a temporary disposal callback with its selected
project. Closing that project cancels the request's child coroutine scope,
including an indexing, document-commit or refresh wait, and produces the existing
`PROJECT_UNAVAILABLE` error. The callback is removed on every exit path: success,
analysis failure, timeout and cancellation. It does not cancel other requests
or the caller's parent scope. A simultaneous caller cancellation retains its
cancellation semantics instead of becoming a tool-owned error.

Previously the request checked project disposal at read boundaries, but it had
no direct disposal hook while suspended. A wait could therefore last until the
30-second deadline after its project closed. The disposal registration closes
that gap. The default deadline remains **30 seconds**; an internal constructor
allows a shorter budget in deterministic timeout tests and is not an MCP option.

Existing behavior remains shared:

- Indexing and uncommitted editor documents block semantic capture.
- Both Java and HTML must be committed before a snapshot is taken. Committing
  editor text does not save it to disk.
- Caller cancellation propagates; deadline expiry returns `TIMED_OUT`.
- Requests do not publish sidebar state, save source files, copy to the
  clipboard or retain a cross-request graph cache.
- Source/module selection, external refresh, freshness after serialization,
  scope/uncertainty, and graph/UTF-8 response limits retain their existing rules.

## Focused verification

All **87 MCP tests** pass: the 77 existing semantic/request cases and **10 new
reliability cases**, with all three tools exercised in the lifecycle matrices.
These use real IntelliJ projects, PSI, editor documents and indexing constraints.

| Case | Verified behavior |
| --- | --- |
| Indexing wait and release | All views remain pending in controlled dumb mode, then equal their original exported responses. |
| Java and HTML edits | All views wait for both documents and report the latest matching IDs after explicit commits. |
| Cancellation during indexing | Each view cancels promptly; fresh requests still work after indexing ends. |
| Cancellation during a document wait | Cancelling one request leaves other views alive and able to finish. |
| Indexing deadline | All views time out with the shared error and recover on later requests. |
| Document deadline | Pending text stays uncommitted and backing-file bytes stay unchanged; explicit commit permits recovery. |
| Concurrent views and targets | Twelve requests across two source classes return deterministic results for their own target and leave source/sidebar state unchanged. |
| Concurrent analysis failure | A missing class does not cancel successful requests. |
| Already disposed project | Every view rejects it promptly. |
| Project closes during indexing | Every wait ends with `PROJECT_UNAVAILABLE` before the normal deadline; the caller's parent scope remains usable. |

Document-wait fixtures temporarily disable **automatic background commits** via
the SDK's test support. They still use real explicit commits. Otherwise the IDE
correctly commits these small files before a test can observe a blocked request.
The fixture restores normal commit behavior during teardown. Production code
does not reference that internal test API.

The complete suite passes **1,641 tests**, with no failures, errors or skips.
The installable ZIP contains one WicketLens JAR; its 19 MCP class files and
optional descriptor match the current build. No MCP, transport, Wicket or Spring
libraries are bundled.
Packaging and project/structure checks pass. Binary verification against IDEA
262.10968.63 is **Compatible**, with the existing four deprecated and six
experimental API notices unchanged.

Run the focused suite with the existing Java 25 build toolchain:

```sh
./gradlew test --tests 'dev.wicketlens.WicketMcp*Test'
```

## Live protocol verification

Use the existing isolated IDEA host and fixture:

```sh
./gradlew runMcpSpike -PmcpStructureFixture=true \
  -PmcpSpikeTools=wicketlens_mcp_probe,wicket_get_page_structure,wicket_validate_page,wicket_get_component_context
python3 tools/check_mcp_reliability.py
```

The standard-library client starts **six concurrent MCP sessions**, one per
tool/module combination. The two modules deliberately share `example.McpPage`
with different component IDs. Each session receives one expected class lookup
error and then three successful responses equal to its sequential baseline.
The original baseline session remains usable afterward. This is **36 semantic
requests** total: 30 successful and six expected errors. Each worker owns its
transport headers and JSON-RPC IDs; the test does not claim overlapping requests
within a single session.

The checker verifies the source/resource/module inputs are unchanged and compares
all returned findings and selected nodes with the existing structure view.
This is bounded fixture verification, not a throughput benchmark or the deferred
real-application evaluation. No new transport or production dependencies are
introduced.

All live checks pass on IDEA **2026.2.3 / IU-262.10968.63**, alongside the
existing structure/probe, validation and component-context protocol checks.
The portable [evidence record](mcp-reliability-evidence.json) contains the
concurrent-session results and explicitly records what remains unverified.
The disposable host is stopped; normal IDE/client settings are unchanged.
This slice does not repeat the prior Codex acceptance calls because the semantic
wire contracts are unchanged; it exercises the same host through the protocol
test client.

## Remaining Phase 3 slices

The [second slice](mcp-isolation-transport.md) now verifies multiple open projects,
larger bounded fixtures and transport behavior, with a documented native-host
cancellation/session-expiry limitation. Remaining work:

The third slice now adds [graphical/client setup](mcp-client-setup.md), two agent
clients and a [preview release checklist/checker](release-checklist.md).

1. Retest immediate wire cancellation and long-wait session behavior when the
   bundled SDK fixes the observed limitation.

The existing graph/response limits and ordinary repeated-request tests are
already implemented; later slices should extend that evidence rather than
replace them. Real-application accuracy/performance evaluation remains deferred
until separately scheduled.
