# MCP isolation, bounds and transport verification

This is Phase 3's second reliability slice. It extends the existing request
pipeline's evidence with two open projects, larger real Java/HTML fixtures,
overlapping calls in one MCP session, wire cancellation and dropped connections.
The semantic engine, production adapter, tool schemas and dependencies are
unchanged. No second server or cancellation protocol is introduced.

## Two open projects

The dedicated host fixture opens two real IntelliJ projects. Both contain a
module named `shared` and a source class named `example.McpPage`, with different
component IDs and paired markup. Six overlapping requests in one HTTP session
exercise structure, validation and context in both projects. Each response must
contain the selected project's data, rather than the active editor's or another
project's declarations.

The fixture also holds the first project in dumb mode. A request to that project
remains pending while a request to the other project succeeds. Releasing
indexing cleans up the first request. These checks pass through the actual
bundled MCP HTTP server and optional WicketLens extension.

## Wire cancellation: observed host limitation

On IDEA **2026.2.3 / IU-262.10968.63**, the server accepts
`notifications/cancelled` with HTTP **202**, but does **not** promptly cancel the
WicketLens tool coroutine. Three blocked semantic requests and an uncancelled
control remain bounded by the existing **30-second** WicketLens deadline. Their
request-lifetime registrations are removed by that deadline.

The same run observes empty HTTP **404** responses for those long blocked calls.
The original session then returns HTTP 404 with `Streamable HTTP session not
found`. The uncancelled control behaves the same way, so session expiry is not
attributed solely to sending the cancellation notification. A fresh session
successfully invokes all three tools afterward.

This is a bundled-host/transport limitation, not a new WicketLens JSON error
code. A host-level HTTP failure can prevent delivery of the tool's normal
versioned error envelope. Clients must treat it as a transport/session failure,
reconnect, and retry once the IDE is ready. Do not interpret a missing response
as successful validation or an empty component graph. Prompt cancellation is
verified when cancellation reaches the adapter's coroutine by the earlier
[lifecycle tests](mcp-reliability.md); it is **not promised over this SDK's wire**.

The integration deliberately retains JetBrains' transport. Fixing this at the
plugin layer would require intercepting host sessions or introducing another
protocol, contrary to the integration's boundary. Retest this behavior when
upgrading the target SDK; immediate wire cancellation remains an upstream
compatibility issue.

## Connection loss and recovery

For each semantic tool, the fixture drops the client's HTTP request while the
server is blocked by indexing. After indexing is released, the request's
disposal registration disappears and a fresh client succeeds. This verifies
bounded recovery and absence of a retained WicketLens request. It does not claim
that merely closing an HTTP connection immediately cancels server-side work;
the existing deadline and project-disposal cancellation remain the safeguards.

## Larger real fixtures

The normal PSI suite adds these cases using actual Java source and companion
HTML, rather than fabricated oversized reports:

- A page with 200 bindings preserves every component and finding. Validation
  equals structure's findings; root context includes all 200 direct children.
- A page with 100 distinct long Unicode IDs exceeds the 512-KiB UTF-8 budget
  in structure, validation and root context. Each fails with
  `RESPONSE_TOO_LARGE`; none truncates the graph.
- Selecting one component from that same Unicode page returns its original
  matched graph context within the budget. A large full report does not prevent
  a small selection from succeeding when the existing graph limits are met.

This is bounded correctness/limit verification, not a throughput benchmark or
the separately deferred real-application accuracy/performance study.

## Running the checks

Normal tests keep MCP Server disabled, including the foundation test proving
that the plugin works without it. All **90 focused MCP tests** pass, including
the three new larger-fixture cases:

```sh
./gradlew test --tests 'dev.wicketlens.WicketMcp*Test'
```

The separate mode enables the bundled server and runs **only** the three host
integration cases:

```sh
./gradlew test -PmcpHostTests=true
```

All **three host integration tests** pass, including the explicit assertions
for the observed SDK limitation and successful reconnection. These cases run
separately rather than being skipped inside the normal suite.

The complete normal suite passes **1,644 tests**, with no failures, errors or
skips. Packaging and project/structure checks pass. All 19 MCP classes and the
optional descriptor match the current ZIP, which contains one WicketLens JAR
and no test helpers or bundled framework/transport libraries. The unchanged
production artifact retains its **Compatible** verifier result against
262.10968.63; that task is up to date, with the existing four deprecated and six
experimental API notices.

The test application starts the real `McpServerService` on a disposable port;
Java's standard HTTP client performs MCP initialization, calls and cancellation
notifications over loopback. The client timeout exceeds the tool's deadline.
Tests observe existing disposal registrations to distinguish acceptance of a
notification from actual termination of a request. They restore server/settings
state and dispose their projects/SDKs. Nothing is added to the shipped plugin.

The host fixture uses SDK internals only in test sources, including a small
Java accessor for JVM-public settings that are Kotlin-internal. This harness
is specific to the pinned SDK and is not part of the production API boundary.
Normal graphical IDE/client settings are untouched. Graphical setup, another
client and release checks are now documented in the
[setup guide](mcp-client-setup.md) and [release checklist](release-checklist.md).
Broader version coverage and the native host limitation remain open.

The portable [evidence record](mcp-isolation-transport-evidence.json) includes
the three passing host cases, raw HTTP status observations and explicit
limitations. Its acceptance means the tested isolation, bounds and recovery
behavior is verified **with the documented host limitation**.
