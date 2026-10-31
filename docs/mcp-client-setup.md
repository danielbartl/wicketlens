# Connect an agent to WicketLens

WicketLens contributes four read-only tools to IntelliJ IDEA's bundled MCP
server. Install the plugin using [the ZIP instructions](../README.md#installation)
and open a Wicket project with Java/resource roots and resolved dependencies.
No separate WicketLens server, AI subscription inside the IDE, or Spring plugin
is required. Your chosen agent has its own account/model configuration.

## Enable the graphical server

1. Check **Settings → Plugins → Installed**: WicketLens and **MCP Server** must
   be enabled. Restart after installing/updating the ZIP if prompted. When using
   the development sandbox, an already running IDE can retain an older plugin
   even after Gradle copies a new JAR; exit and relaunch that sandbox.
2. Open **Settings → Tools → MCP Server**, enable the server, review the IDE's
   access dialog, and apply the change.
3. Open **Exposed Tools**, search for `wicket`, and verify the four enabled
   entries below. For direct tool calls with the client allowlists in this
   guide, leave **Enable router-only mode** off in the verified build.
4. Copy the **HTTP Stream** configuration, or use the `/stream` address shown
   beside the enable checkbox. Use your IDE's displayed URL; the graphical
   sandbox here used port `64342`, while the headless development harness uses
   `64343`. Restart/reconnect the client after changing its configuration.

This flow was exercised on the pinned IDE. JetBrains also documents
[server enablement, configuration copying and tool exposure](https://www.jetbrains.com/help/idea/mcp-server.html).
The command-execution “brave mode” setting is not needed for these read-only
WicketLens tools and remained off during verification.

![MCP Server enabled in the isolated WicketLens demo IDE](images/mcp-server-setup.png)

![The four WicketLens tools in the graphical Exposed Tools settings](images/mcp-exposed-tools.png)

| Tool | Purpose |
| --- | --- |
| `wicketlens_mcp_probe` | Check connectivity; fixed JSON, no semantic analysis. |
| `wicket_get_page_structure` | Existing component graph, matching, locations and uncertainty. |
| `wicket_validate_page` | Existing findings/diagnostics and summary counts. |
| `wicket_get_component_context` | Select a path/node; inspect parent, children and associated markup. |

The IDE exposes other tools independently of WicketLens. The Codex allowlist
below exposes only these four to that client. Router-only tool workflows are
managed by the host; this guide verifies direct discovery and invocation.

## Codex

Add the following to your selected Codex configuration, replacing the URL with
the address copied from your IDE. Global `~/.codex/config.toml` and supported
project-scoped configuration use the same MCP table. Preserve existing entries.

```toml
[mcp_servers.wicketlens]
url = "http://127.0.0.1:64342/stream"
enabled_tools = [
  "wicketlens_mcp_probe",
  "wicket_get_page_structure",
  "wicket_validate_page",
  "wicket_get_component_context",
]
startup_timeout_sec = 20
tool_timeout_sec = 60
```

Restart the client and inspect `/mcp` in its TUI. The 60-second client timeout
allows the plugin's shorter deadline to finish; it does not extend that deadline
or fix the native host's session-expiry behavior. OpenAI documents these fields
and configuration scopes in its [official MCP guide](https://developers.openai.com/codex/mcp/).

For a temporary verification instead of saving configuration, see the
[ephemeral CLI example](mcp-integration.md#reproducing-the-spike), substituting
the graphical URL and desired semantic tool. The recorded walkthrough used
per-process overrides, an empty client folder, and no command/file tools.

## Claude Code

Claude Code can use the IDE's copied HTTP configuration. For example, merge this
entry into the chosen project's `.mcp.json`, or use its own MCP configuration UI:

```json
{
  "mcpServers": {
    "wicketlens": {
      "type": "http",
      "url": "http://127.0.0.1:64342/stream"
    }
  }
}
```

Use `/mcp` to inspect the connection and follow the client's project approval
flow. The `type` field is required for this HTTP entry. Anthropic documents
[HTTP configuration and scopes](https://code.claude.com/docs/en/mcp).
The acceptance call used temporary `--mcp-config` settings, only the context
tool permitted, no built-in tools, and no saved conversation or client settings.

## First requests

Start with the probe and supply `projectPath` as the exact open project root.
The host requires it even for the parameterless probe. Then ask the agent:

> Call `wicket_get_page_structure` with `projectPath` set to my open project,
> `className` set to `example.CustomerPage`, `moduleName` set to the exact IDE
> module, and `sourceSet` set to `main`. Explain findings and uncertainty.

Replace the class/module with your application. Supported roots are source
`WebPage`, `Panel`, and `FormComponentPanel` subclasses. Module selection is
optional when unambiguous. Sources in libraries are not root targets.

For the disposable development fixture, `example.McpPage`, module `mcp-fixture`,
and component path `form:name` select a matched TextField. Use that path with
`wicket_get_component_context`. Use a fresh graph `nodeId` for ambiguous IDs or
logical item templates. The tool contracts are linked in
[the integration guide](mcp-integration.md).

`status: ok` means the request completed. Findings remain static observations;
confidence, scope flags and causes still matter. Runtime model/property values
are unavailable. Responses contain source paths and findings that the connected
agent may send to its model provider according to that client's configuration.

## Troubleshooting

| Symptom | Check / recovery |
| --- | --- |
| MCP settings absent | Enable the bundled MCP Server plugin; confirm the exact supported IDE build. WicketLens remains usable without MCP. |
| IDE tools appear but WicketLens tools are missing | Confirm the plugin version, restart the IDE after replacing the ZIP, inspect Exposed Tools and direct/router settings, then reconnect. A stale sandbox process reproduced this symptom. |
| Connection refused or wrong server | Copy the address from the running IDE. `64343` belongs to the isolated headless harness, not the observed graphical server. |
| `AMBIGUOUS_CLASS` | Supply exact `moduleName` and `sourceSet`; also supply the intended open `projectPath`. |
| `CLASS_NOT_FOUND` / `UNSUPPORTED_TARGET` | Check the qualified Java class, Java roots, source set, Wicket dependency resolution and supported base type. |
| `COMPONENT_NOT_FOUND` / ambiguous path | Fetch structure again; use a current node ID instead of guessing a duplicate/dynamic path. |
| `STALE_SNAPSHOT` | Wait for edits/indexing to settle and invoke again. No outdated graph is returned. |
| `RESPONSE_TOO_LARGE` | Select a smaller component/root. Responses are rejected rather than truncated. |
| HTTP 404 after a long wait or cancellation | Reconnect the client and retry after the IDE is ready. This SDK can expire the session and suppress the tool's JSON error envelope. An absent response is not an empty or clean report. |
| Cancel button does not stop work immediately | Native wire cancellation is not prompt in the targeted SDK. Work remains bounded by the plugin's 30-second deadline/project disposal. See [the measured behavior](mcp-isolation-transport.md). |

## Verified compatibility

| Item | Evidence / limit |
| --- | --- |
| IntelliJ IDEA 2026.2.3, IU-262.10968.63 | Build/verifier, MCP-disabled tests, actual host tests and graphical settings walkthrough. |
| Bundled MCP Server 262.10968.63 | Direct discovery/calls; known cancellation/session-expiry limitation retained. |
| Wicket 10.11.0 | Real configured PSI fixtures; no application runtime execution. |
| Codex CLI 0.166.0 | Previous semantic acceptance calls and a fresh context call through the graphical server. |
| Claude Code 2.1.314 | A fresh context call through the same graphical server. |
| Other IDE/client/framework versions | Not verified; rerun the relevant checks before claiming support. |

Both agent calls returned the real TextField/Form/HTML association and retained
the partial-analysis caveat. Portable [setup evidence](mcp-setup-evidence.json)
contains the actual calls with fixture paths redacted. The sandbox was closed
normally and its original disabled MCP state restored. No normal client
configuration was written. See [release verification](release-checklist.md) for
the reproducible artifact checks and remaining publication work.
