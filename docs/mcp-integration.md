# Optional MCP integration: implementation and verification

The [integration concept](../mcp.md) is incorporated into the existing
[project roadmap](../spec.md#12-follow-up-features). WicketLens contributes domain
tools to JetBrains' bundled MCP server. Agents provide reasoning and code
generation. The integration adds no separate server, parser, chat UI, LLM SDK,
or runtime transport dependency.

**Phase 1** and the three semantic **Phase 2** slices are implemented. The static
`wicketlens_mcp_probe` verifies connectivity; `wicket_get_page_structure` now
delegates project-scoped requests to the existing analyzer and exporter. See
[the structure tool contract and evidence](mcp-page-structure.md). The
[validation tool](mcp-validation.md) projects existing findings over the shared
request pipeline. [Component context](mcp-component-context.md) selects paths or
node IDs from the same exported graph.
The [injected-dependency tool](mcp-injected-dependencies.md) now adds source
injection fields, bean candidates, profile conditions, and FactoryBean products
by reusing the existing dependency snapshot through the same request pipeline.
The [Spring inspection tool](mcp-spring-inspections.md) reuses registered native
inspections and current profile settings, preserving severity and suppression.
Phase 3 has started with [lifecycle and concurrent-request reliability](mcp-reliability.md).
The second slice adds [open-project isolation, larger fixtures and transport
verification](mcp-isolation-transport.md), including a documented bundled-host
cancellation/session-expiry limitation.
The third slice adds [graphical/client setup](mcp-client-setup.md), successful
Codex and Claude Code calls through the graphical server, and a
[local-preview check tied to current sources](release-checklist.md).

## Implementation inspected and reusable

| Existing implementation | Reuse in the integration |
| --- | --- |
| `WicketPageSelection`, `WicketEditorTargetResolver` | Select a supported Page/Panel/FormComponentPanel and resolve a Java/HTML source identity. Class-name requests use a small, project/module-aware source lookup adapter. |
| `WicketPageAnalyzer`, Java/source setup analyzers, markup inheritance, matcher | Reuse the current semantic engine unchanged. It already handles IDs, hierarchy, matching, source locations, and explicit uncertainty. |
| Value-only `PageReport`, `ComponentNode`, matching findings and diagnostics | Use the same observed graph and confidence/status meanings for all three semantic tools. |
| `WicketContextExporter` | Reuse deterministic analysis JSON schema **1**. Any MCP envelope must version its own contract without changing the working export. |
| `ReportVersion`, `SourceVersion`, `WicketSourceFiles` | Reuse source/binary/JAR freshness tracking where snapshots are retained. |
| Smart/committed reads, cancellation, and disposal patterns in `WicketLensService` | Reuse the threading pattern in a UI-independent request path. The current service owns sidebar state, editor following, and clipboard/navigation actions; MCP must not drive those UI actions to obtain results. |
| Existing editor inspections and quick fixes | Preserve them. Validation should derive from existing findings and retain their conservative severity, rather than invent a second checker or copy the concept's illustrative `ERROR` examples literally. |
| `WicketDependencyAnalyzer` and dependency snapshots | Reused directly by `wicket_get_injected_dependencies`; graph/export schemas and earlier tools remain unchanged. |
| `WicketSpringTestGenerator` and its preview/create workflow | Keep the existing deterministic test scaffold. Agent-generated tests can supplement it; MCP does not replace it. |

Structured Wicket model/property expressions are not currently part of
`ComponentNode`. The component-context response omits such data until there is
actual analysis evidence. No model details are inferred by these tools.

## Phase 1 result: PASS

The exact targeted SDK, IntelliJ IDEA **2026.2.3 / IU-262.10968.63**, bundles
`com.intellij.mcpServer` version **262.10968.63**. Its descriptor publishes
`com.intellij.mcpServer.mcpToolset`; its API provides `McpToolset`, `McpTool`,
`McpDescription`, and `McpToolHints`. JetBrains documents contributing toolsets
through these surfaces in its
[authoring guide](https://github.com/JetBrains/intellij-community/blob/master/plugins/mcp-server/README.md)
and [plugin tutorial](https://www.jetbrains.com/help/mps/writing-mps-mcp-tools-plugin.html).

The plugin declares an **optional** dependency with a separate
`META-INF/wicketlens-mcp.xml`. MCP bridge classes are not loaded when that plugin
is disabled. Gradle uses the bundled plugin only as an IDE-provided compile
dependency; its JARs are not shipped inside WicketLens.

The probe returns this fixed JSON as one MCP text-content block:

```json
{"schemaVersion":1,"plugin":"WicketLens","tool":"wicketlens_mcp_probe","phase":1,"status":"ok","semanticAnalysis":false}
```

It has no analysis parameters or project/file access and declares read-only,
non-destructive, idempotent, closed-world hints. The JetBrains host nevertheless
requires its own `projectPath` argument to select invocation context. That path
must identify an open project; the successful test uses a disposable empty
project under `build/`.

Verification performed:

1. Started the **actual IDEA distribution** using its built-in headless MCP
   starter, in an isolated Gradle sandbox and with only the probe allowlisted.
2. Performed MCP `initialize`, `tools/list`, and `tools/call` over loopback
   Streamable HTTP; checked the advertised hints and exact JSON response.
3. Ran installed **Codex CLI 0.166.0** with ephemeral per-process MCP settings,
   an empty temporary client workspace, and only the probe enabled for that
   client. Codex emitted a completed `mcp_tool_call` with no error and returned
   the exact tool response. No command or file-edit tools were used.
4. Stopped the disposable host. The test does not change the user's saved Codex
   configuration or normal IDE MCP settings.

The portable, path-redacted [evidence record](mcp-phase1-evidence.json) contains
the actual protocol result and completed Codex tool-call event. Its response is
text containing JSON, not MCP `structuredContent`; choosing the semantic response
envelope is Phase 2 work. The built-in `execute_tool` router also appears in
discovery; Codex's allowlist exposed only `wicketlens_mcp_probe`.

The final full suite passes **1,554 tests**, with no failures, errors, or skips,
including the new MCP-disabled foundation case. Packaging and project/structure
checks pass. Binary verification is **Compatible** against the targeted build,
with the existing four deprecated and six experimental API notices. The ZIP
contains the current probe and optional descriptor, one WicketLens JAR, and no
bundled MCP SDK, Ktor, or other framework libraries.

## Compatibility and API boundaries

- The plugin's minimum build stays **262.10968.63**. Only the exact IDEA and
  bundled MCP versions above are verified. JetBrains documents built-in MCP
  availability from 2025.2 in its
  [server setup guide](https://www.jetbrains.com/help/idea/mcp-server.html);
  that does not establish WicketLens compatibility with older IDE builds.
- The production bridge uses the public toolset interface and annotations.
  Inspection of this SDK found no `ApiStatus.Internal` or
  `ApiStatus.Experimental` annotation on the bridge API it uses, and compilation
  requires no API opt-in. The MCP API can still evolve; keep the adapter small
  and verify against each future target IDE.
- `isExperimental() = false` makes the bounded read-only tools discoverable
  under normal tool filtering. It is a tool-discovery category, not an assertion
  that the entire MCP integration is production-ready.
- The **development harness** uses JetBrains' internal headless application
  starter. WicketLens production code does not reference that starter. Its CLI
  is version-sensitive: this SDK requires `--project=...`; positional project
  arguments in current upstream examples are ignored here.
- This SDK's whitelist matches the qualified Kotlin method identity, while a
  tool annotation can override the wire name. Keeping the Kotlin and wire names
  identical avoids that filtering mismatch.
- The successful automated host run proves built-in runtime extension loading
  and Codex interoperability. It does not claim a graphical Settings-dialog
  walkthrough, older-IDE support, or semantic-tool readiness.

## Reproducing the spike

Use the existing build JDK (Java 25) and start the isolated host:

```sh
./gradlew runMcpSpike
```

The Gradle testing extension creates a separate sandbox. The default project is
`build/mcp-spike/project` and the loopback port is `64343`. Override with
`-PmcpSpikeProject=/absolute/path/to/disposable/project` and
`-PmcpSpikePort=64344` if necessary. Wait for the server-started message, then:

```sh
python3 tools/check_mcp_spike.py
```

For overrides, pass `--url http://127.0.0.1:64344/stream` and
`--project-path /absolute/path/to/disposable/project` to the checker. It uses
only Python's standard library and is an MCP **test client**, not another server.
Stop the host with Ctrl+C when finished. The server and project initialization
can write sandbox/index files; the probe itself performs no project operations.

For a Codex acceptance call, supply temporary CLI overrides for a server named
`wicketlens_spike` with URL `http://127.0.0.1:64343/stream`,
`enabled_tools=["wicketlens_mcp_probe"]`, and `required=true`. Ask Codex to call
the tool once with `projectPath` set to the absolute disposable project path.
Use an empty client workspace, `--ephemeral`, `--sandbox read-only`, and
`--ignore-user-config` to keep this test separate from normal configuration.
The CLI's `-c` overrides were verified locally; normal client setup is described
in the [official OpenAI MCP documentation](https://developers.openai.com/codex/mcp/).

For example, replace both placeholder paths below and run from a terminal with
the isolated host still running:

```sh
codex exec --ignore-user-config --ephemeral --skip-git-repo-check \
  --sandbox read-only --cd /absolute/path/to/empty/client-workspace \
  -c 'mcp_servers.wicketlens_spike.url="http://127.0.0.1:64343/stream"' \
  -c 'mcp_servers.wicketlens_spike.enabled_tools=["wicketlens_mcp_probe"]' \
  -c 'mcp_servers.wicketlens_spike.required=true' \
  'Call wicketlens_mcp_probe once with projectPath="/absolute/path/to/wicketlens/build/mcp-spike/project". Use no other tools. Return the exact tool response; report any error without inventing a result.'
```

For a normal graphical IDE, enable MCP using JetBrains' Settings workflow and
copy the IDE-generated HTTP connection settings. WicketLens does not enable the
server, change host authentication/permissions, or configure clients itself.
The connected agent may send tool responses to its model provider; this spike
returns only the public fixed payload above.

## Phase 2 progress and next implementation steps

**Phase 2 — semantic integration**

1. **Implemented:** `wicket_get_page_structure` resolves source classes within
   the host-selected project, with exact module and source-set choices. It
   rejects ambiguity, refreshes external disk changes, runs smart/committed
   analysis, and returns the unchanged export in a bounded, versioned envelope.
   Snapshot/error/limit behavior and live Codex verification are documented in
   [the structure contract](mcp-page-structure.md).
2. **Implemented:** `wicket_validate_page` is a view over the same findings and diagnostics.
   Preserve `MATCHED`, informational `JAVA_ONLY`/`MARKUP_ONLY`, and unresolved
   statuses; do not turn incomplete analysis into definite runtime errors.
3. **Implemented:** `wicket_get_component_context` selects an unambiguous component
   path/node from the existing graph, with source/markup locations, ancestry,
   direct children and exact findings. Logical repeater items remain explicit;
   unavailable model information is omitted. See [the context contract](mcp-component-context.md).
4. The first-slice envelope/error versions, bounds, module/source-set ambiguity,
   cancellation, source-root-only target policy, external refresh, and freshness
   behavior are shared by all three tools using the existing request/core
   services. No broad engine refactor was required.

**Phase 3 — reliability and release readiness**

1. **Implemented:** [lifecycle and concurrency](mcp-reliability.md): indexing and
   document waits, cancellation/timeout recovery, prompt project-disposal
   handling, independent concurrent requests and live sessions across modules.
   Existing semantic protocol and no-write assertions remain in use.
2. **Implemented:** [multiple open projects, larger bounded fixtures and
   transport verification](mcp-isolation-transport.md). The actual host routes
   colliding class/module names correctly and recovers from dropped connections.
   In this SDK, wire cancellation does not promptly stop work, and long blocked
   calls expire their session with HTTP 404; the tool's deadline bounds work and
   a fresh session recovers. Immediate wire cancellation remains an upstream
   limitation to retest on SDK upgrades. Existing response bounds are reused.
3. **Implemented:** [graphical/client setup and compatibility](mcp-client-setup.md)
   with native settings screenshots, a fresh Codex call and a second-client
   Claude Code call. [Release checks](release-checklist.md) retain separate
   normal/host results tied to source checksums, validate the exact ZIP/verifier receipt,
   package the license and check current graphical/client evidence.

Real-application correctness/performance evaluation
remains deferred until separately scheduled; this phase does not silently
authorize that study. Runtime inspection and agent-assisted generation remain
separate roadmap items.

The three planned Phase 3 implementation slices are complete for the current
local preview. Publication remains a separate, explicit workflow; native-host
cancellation/session expiry remains an upstream issue to retest. The original
estimate excluded the still-deferred real-application study.
