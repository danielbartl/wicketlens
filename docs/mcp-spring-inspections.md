# MCP Spring inspection findings

`wicket_get_spring_inspections` runs the five **registered native WicketLens Spring
inspections** in the current IDE inspection profile. It projects their existing
problem descriptors; it does not implement another Spring checker or apply fixes.
The MCP impact decision for this slice is to add a focused read-only tool while
preserving all earlier tool responses and component/export schemas.

## Request and scope

Supply host `projectPath`, qualified source `className`, optional exact
`moduleName`, and `sourceSet` (`main`, `test`, or `any`, default `main`). Roots are
source WebPage, Panel, or FormComponentPanel subclasses, as in the other tools.

```json
{
  "projectPath": "<open-project>",
  "className": "example.McpPage",
  "moduleName": "mcp-spring"
}
```

Inspection scope includes the selected root, uniquely resolved discovered source
component types, their source ancestors, and the reviewed Wicket application and
its source ancestors. Only Java sources in the selected module and production/test
source set are inspected. Unobserved models, sessions, helpers, sibling/nested
classes, compiled custom ancestry, and other modules are outside this slice.
Source files shared by several components are inspected once; a finding can carry
multiple graph node IDs. Application findings have an empty node-ID list.

The existing inspections retain their own injection/bootstrap/bean-source evidence
gates. Unknown application setup or profiles never become guessed missing-injector
or missing-bean errors. Bootstrap findings come from the reviewed application's
file, including the existing zero/multiple-registration checks.

## Response and IDE settings

Envelope schema **1** has an independent `springInspections` schema **1**, with:

- Current `profileName`, `reviewedApplication`, inspected files and scope notes.
- Per-file inspection `checks`: short ID, enabled state, configured severity.
- `findings`: inspection ID/display name, exact native message template,
  configured severity, original highlight type, source start/end offsets,
  declaring class and associated graph node IDs.
- Explicit `truncated` and coverage `limitations`.
- `runtimeValidityVerified: false`; successful execution is not a validity verdict.

Disabled inspections and native `@SuppressWarnings` / supported `//noinspection`
contexts stay excluded. Severity names are the IDE's actual strings, including
`WEAK WARNING` and user-selected/custom levels. All current Spring descriptors
use the native generic highlight type, so the inspection profile supplies severity.
No severity is promoted merely because a finding is returned to an agent.

The tool reads the IDE-loaded profile; it does not edit settings or read raw
profile XML. Enabled/severity state and profile identity are rechecked after
serialization alongside source/root freshness. Changes require a fresh request
and return `STALE_SNAPSHOT`. The five current inspections have no additional
options requiring configuration fingerprints.

The same smart/committed reads, external source/binary refresh, project isolation,
30-second deadline, lifecycle/cancellation handling and 512-KiB UTF-8 response
budget apply. Native host [wire-cancellation/session limits](mcp-isolation-transport.md)
remain unchanged. Source scope is bounded to **64 types**, **12 types per source
hierarchy (including its root)**, and **256 findings**. Reached bounds set envelope/payload `truncated`
and limitations; missing/compiled source remains explicit. Oversized responses
fail with `RESPONSE_TOO_LARGE`, without cutting JSON to fit.

An empty result may reflect disabled/suppressed checks, insufficient evidence,
limited coverage, or no findings. It does not prove correct runtime wiring,
serialization, bean availability, or profile activation. Dependency candidates
remain available separately through
[`wicket_get_injected_dependencies`](mcp-injected-dependencies.md).

## Verification

The full normal suite passes **1,721 tests**, alongside **five actual-host tests**
and **20 release-checker unit tests**. Binary verification reports **Compatible**
on the pinned IDE. The live protocol checker and a single ephemeral **Codex CLI
0.166.0** call pass. [Portable evidence](mcp-spring-inspections-evidence.json)
retains the returned findings and exact artifact checksum. No fresh graphical
setup/client-matrix walkthrough or plugin publication is claimed.

Tests compare native descriptors directly, including messages, source ranges and
severity. They cover disabled checks, annotation/comment suppression, profile
freshness, bootstrap/proxy/annotation/type checks, repeated components, ancestry,
scope isolation, source freshness, discovery/byte bounds, Spring-free projects,
determinism, and no sidebar or file changes. The existing lifecycle matrices
include the new tool; separate tests invoke the real bundled MCP host.

The live checker verifies configured severity, exact source ranges, external
suppression edits/restoration and a quiet Spring-free module. Reproduce it with
the isolated host:

```sh
./gradlew runMcpSpike -PmcpStructureFixture=true \
  -PmcpSpikeTools=wicketlens_mcp_probe,wicket_get_page_structure,wicket_validate_page,wicket_get_component_context,wicket_get_injected_dependencies,wicket_get_spring_inspections
```

In a second terminal:

```sh
python3 tools/check_mcp_spring_inspections.py
```

Only the disposable `mcp-spring` module has Spring libraries. The checker restores
authored fixture inputs after its suppression edit. Stop the host afterward.
