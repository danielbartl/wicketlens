# MCP Spring test plan

`wicket_get_spring_test_plan` exposes the existing deterministic
[Spring WicketTester generator](spring-testing.md) through JetBrains' bundled
MCP server. It returns a plan and Java scaffold for an agent to adapt using its
normal editing tools. It introduces no AI SDK, separate generator, or write tool.

## Request

Use the host's exact open `projectPath`, qualified source `className`, optional
exact `moduleName`, and `sourceSet` (`main`, `test`, or `any`; default `main`).
Duplicate declarations are rejected. Only concrete, top-level, non-generic
WebPage, Panel, and FormComponentPanel subjects supported by the existing Spring
generator are eligible. Wicket–Spring injection intent or recognized application
registration is required; a Spring JAR alone does not enable scaffolding.

```json
{
  "projectPath": "<open-project>",
  "className": "example.McpPage",
  "moduleName": "mcp-spring",
  "sourceSet": "main"
}
```

An unsupported Spring recipe returns versioned `TEST_PLAN_UNAVAILABLE` JSON with
the generator's explanation. This includes plain Wicket, abstract, nested or
generic subjects and invalid Java syntax. Standard request/target errors still
apply before generator selection. No eligible test root is required to obtain
the plan: `testRoots` can be empty.

## Response

The existing envelope schema **1** contains the selected target and committed
static snapshot. The new `springTestPlan` object has schema **1**:

- `kind: "isolated_spring_wicket_tester_scaffold"` and
  `runtimeValidityVerified: false`.
- `packageName`, `targetClass`, `suggestedTestName`, `suggestedFileName`, and
  `constructorStrategy`: PAGE, PARAMETERS, COMPONENT, MANUAL_PAGE or MANUAL_COMPONENT.
- `mocks`: suggested isolated-context `beanName` and declared `type`, directly
  from `SpringTestPlan`. Production source bean candidates, FactoryBeans and
  declared profiles do not determine bindings for this isolated context.
- `manualBindings` and `notes`: the generator's exact review requirements.
  Incomplete dependency discovery retains its manual TODO and throwing guard.
- `requiredDependencies`: unversioned test dependency coordinates from the shared
  generator, with `dependencyAvailabilityChecked: false`. These are requirements,
  not missing-dependency findings. Use the project's framework versions, a JUnit
  Jupiter runner, and its appropriate Mockito JVM-agent setup.
- `testRoots`: existing eligible writable, non-generated Java test root paths
  and their `packagePrefix`, restricted to the selected module. These are
  suggestions, not reserved paths; check collisions, package mapping and current
  writability before creating files separately.
- `language: "java"`, `source`: the exact default scaffold from
  `WicketSpringTestGenerator.render`, and explicit `scopeNotes`.

The source keeps its TODOs and throwing guards for manual bean bindings and
constructors. `status: ok` means preparation completed, not that the test runs or
production wiring is valid. Adapt mock stubbing, subject construction, sessions,
routes, resources and application-specific behavior; add meaningful assertions.

## Shared lifecycle and limits

The request pipeline selects the exact source declaration, refreshes external
files, waits for smart/committed PSI, captures values in one read, serializes
outside that read, and checks freshness afterward. Dependency declarations and
types consulted for mock preparation participate in version tracking, as do the
project roots. A source change invalidates the plan; request it again.

The existing 30-second deadline, project-disposal/cancellation behavior,
module/source-set isolation, 5,000-component/10,000-markup guard and 512-KiB UTF-8
response limit apply. All plan fields and the complete Java source count toward
the byte limit. Oversized responses fail with `RESPONSE_TOO_LARGE`; JSON is never
cut to fit. Dependency discovery retains the shared 64-owner, 256-field and
12-ancestor boundaries in its manual review notes. The scaffold itself is
complete (`truncated: false`); its discovery may still be partial.

Requests do not save/commit documents, create files or directories, modify build
files or source roots, open dialogs, publish sidebar state, change the clipboard,
or execute tests, an application or Spring. The native host's
[wire cancellation/session limitation](mcp-isolation-transport.md) still applies.

## Suggested agent workflow

1. Obtain this plan and relevant component/binding context through the existing
   read-only tools.
2. Review required dependencies and all manual setup instructions against the
   user's project. Choose an appropriate existing test location and avoid
   overwriting files.
3. Use the agent's normal editing tools, within the user's authorized scope, to
   adapt the scaffold and add behavior-specific assertions.
4. Run the project's test command separately and iterate on actual failures.

The first tool slice ends at read-only preparation. The next
[portable agent workflow](../examples/spring-test-demo/AGENT_WORKFLOW.md) uses the
plan, page structure and component context to adapt the existing demo test with
normal editing/Maven tools. It verifies behavior and an expected failing control;
this is not a new write/execute operation in MCP. Broader application scenarios,
plain-Wicket scaffolding and built-in orchestration remain separate work.
The [workflow evidence](agent-test-workflow-evidence.json) records the actual
three-tool Codex run, its assertion correction, six passing cases, six expected
regression failures and six passing restored cases.

## Live fixture verification

The initial tool slice passed **1,739 normal tests**, **six actual-host tests**, and
**20 release-checker unit tests**, with no failures, errors or skips. Packaging,
project configuration and structure checks pass. Binary verification is
**Compatible**, with the existing four deprecated and six experimental API
notices. [Portable evidence](mcp-spring-test-plan-evidence.json) records the exact
artifact, live protocol/concurrency checks and ephemeral Codex call.

Preview 2 includes both test-planning tools and refreshed eight-tool graphical/
client checks. [Current release evidence](mcp-release-evidence.json) identifies
its exact packaged version and full release verification. The earlier tool
record above remains a dated implementation check. Representative application
evaluation remains deferred.

Start the existing isolated host with all eight WicketLens tools allowed:

```sh
./gradlew runMcpSpike -PmcpStructureFixture=true \
  -PmcpSpikeTools=wicketlens_mcp_probe,wicket_get_page_structure,wicket_validate_page,wicket_get_component_context,wicket_get_injected_dependencies,wicket_get_spring_inspections,wicket_get_spring_test_plan,wicket_get_test_plan
```

Then run `python3 tools/check_mcp_spring_test_plan.py`. It checks discovery and
read-only metadata, explicit manual guards, Spring-free/ambiguous rejection,
external binding edits and restoration, deterministic responses and unchanged
authored fixture inputs. The concurrency checker includes the Spring plan in
its own module while retaining the original Spring-free modules.
