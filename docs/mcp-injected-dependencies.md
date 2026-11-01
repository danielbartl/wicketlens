# MCP injected dependencies

`wicket_get_injected_dependencies` exposes the existing Dependencies analyzer as
a read-only tool in JetBrains' bundled MCP server. It reuses
`WicketDependencyAnalyzer` and its value snapshot, including source profile and
FactoryBean facts. No second Spring resolver or runtime dependency is introduced.

## Request

Use the host's exact open `projectPath`, a qualified source `className`, optional
exact `moduleName`, and `sourceSet` (`main`, `test`, or `any`; default `main`).
Supported roots are the same source WebPage, Panel, and FormComponentPanel types
as the other tools. Duplicate classes are rejected rather than guessed.

```json
{
  "projectPath": "<open-project>",
  "className": "example.McpPage",
  "moduleName": "mcp-spring",
  "sourceSet": "main"
}
```

The adapter refreshes project source/binary roots, waits for smart mode and
committed documents, captures graph/dependency values in the same read action,
and checks freshness after serialization. Dependency source files participate in
version tracking. The existing 30-second deadline, disposal, cancellation,
module/source-set isolation, 5,000-component/10,000-markup limits, 512-KiB UTF-8
response limit, and versioned errors apply. The upstream transport
[cancellation/session limitation](mcp-isolation-transport.md) still applies.

## Response

The unchanged envelope schema **1** identifies this new tool, the selected target,
and its committed static snapshot. Its `dependencies` member has independent
schema **1**, with explicit fields:

- `evidence: "source_candidates"`, `runtimeBindingsVerified: false`, and
  `profileActivationEvaluated: false`.
- `registeredApplication`: existing recognized application identity, or `null`.
- `fieldCount`, `truncated`, `discoveryLimited`, and scope notes.
- `owners`: graph `nodeId`, descriptive `displayPath`, component type/location,
  partial-ancestry flag, and injection fields.
- Fields: identity, name/type/declaring class, annotation, lookup mode, explicit
  bean name, required policy (`null` if unknown), location, original notes,
  candidate-limit flag, and candidates.
- Candidates: names/aliases, source lookup type and declaring class/location,
  nullable compatibility, conditional flag, factory/product distinction,
  declared factory type, declared profile groups, and unknown-profile flag.

`displayPath` is the sidebar label including the root class, not the relative
`componentPath` accepted by the component-context tool. `nodeId` joins the observed
graph, but remains snapshot-local; refetch after edits.

Ordinary names refer to FactoryBean products; `&name` refers to factory instances.
Raw/wildcard/unknown products retain unknown compatibility. Profile conditions are
declared source expressions, not a selected environment. Alternatives remain
separate candidates. An empty result establishes neither Spring absence nor a
missing bean. `status: ok` means completion, not that injection works. Spring
inspections, runtime values, active-profile inference, bean creation, and test
generation are outside this tool.

Core limits remain visible: 64 populated owners, 256 fields, 12 source ancestors,
32 candidates per field, and the bounded source catalog. Envelope `truncated`
is true when field/owner or candidate discovery was limited; owner `partial`
and `discoveryLimited` additionally retain incomplete ancestry. An oversized
response fails with `RESPONSE_TOO_LARGE`; JSON is never cut to fit. Existing
graph/export schemas and other tool responses are unchanged.

## Verification

The current full suite passes **1,693 normal tests**, **four actual-host tests**,
and **20 release-checker unit tests**. Binary verification is **Compatible** on
the pinned IDE. The live protocol checker and one ephemeral **Codex CLI 0.166.0**
dependency call pass. [Portable evidence](mcp-dependencies-evidence.json) records
the response and artifact checksum. No new graphical settings walkthrough or
plugin publication is claimed.

Normal tests exercise analyzer reuse, source locations/freshness, profiles and
factories, unknown/unsupported/absent cases, discovered children, module/source-set
selection, discovery/byte limits, and no sidebar/file changes. Lifecycle matrices
now include this tool. The separate host suite covers discovery, annotations and
schema, Spring facts over HTTP, project isolation, cancellation and recovery.

To reproduce the live fixture walkthrough:

```sh
./gradlew runMcpSpike -PmcpStructureFixture=true \
  -PmcpSpikeTools=wicketlens_mcp_probe,wicket_get_page_structure,wicket_validate_page,wicket_get_component_context,wicket_get_injected_dependencies
```

In another terminal:

```sh
python3 tools/check_mcp_dependencies.py
```

The task attaches real Spring libraries only to a separate disposable `mcp-spring`
module. The original two Wicket modules stay Spring-free. The checker verifies
source profile/factory facts, external profile-edit refresh/restoration,
deterministic responses, graph node identity, and empty plain-Wicket dependencies.
This is bounded framework-fixture verification, not application evaluation.
