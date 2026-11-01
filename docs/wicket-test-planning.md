# Wicket test planning

Preview 2 offers **Tools → Generate Wicket Test…** and the read-only MCP
tool **`wicket_get_test_plan`**. They share the existing preview, copy/create,
constructor selection, renderer, root checks, undo and snapshot freshness guards.
Preview 1 does not include these additions.

## Plain Wicket first

Select a concrete top-level Java WebPage, Panel or FormComponentPanel, or its
uniquely paired HTML companion. A plain recipe contains JUnit Jupiter and an
isolated WicketTester/WebApplication, with cleanup. It requires test dependencies
for `org.apache.wicket:wicket-tester`, `jakarta.servlet:jakarta.servlet-api` and
`org.junit.jupiter:junit-jupiter`, using the project's framework versions and
a JUnit Jupiter runner. No Spring or Mockito is needed or imported.

Supported construction is shared with the Spring recipe: accessible no-argument
pages, PageParameters pages, and String-ID components. Other constructors keep
a throwing `createSubject()` hook until the developer or agent supplies arguments.
Abstract, nested, generic and syntactically broken subjects are rejected.

Recognized Wicket–Spring field/application intent selects the existing Spring
recipe instead. Spring classpath presence alone does not select that mode.
The original **Generate Wicket Spring Test…** action and
`wicket_get_spring_test_plan` keep their Spring-specific gate and contract.
Plain recipes do not promise support for other dependency-injection systems;
review any application-specific setup before running them.

The preview can copy a scaffold without a configured test root. Creation reuses
the existing writable, non-generated Java test-root and package-prefix checks,
never overwrites an existing file, and participates in normal undo. Neither
action edits build files or configures new source roots.

## MCP contract

Supply the host's exact open `projectPath`, qualified source `className`, optional
exact `moduleName`, and `sourceSet` (`main`, `test`, `any`; default `main`).

```json
{
  "projectPath": "<open-project>",
  "className": "example.HelloPage",
  "moduleName": "<module>",
  "sourceSet": "main"
}
```

The envelope remains schema **1**, with a new `testPlan` member using schema **1**.
Its fields match the shared [Spring plan](mcp-spring-test-plan.md): constructor
strategy, suggested names, exact Java source, mocks/manual bindings, notes,
requirements, test-root suggestions and scope notes. `kind` distinguishes
`isolated_wicket_tester_scaffold` from `isolated_spring_wicket_tester_scaffold`.
For recognized Spring intent, the payload equals the dedicated Spring tool's
`springTestPlan`. Plain mocks/manual bindings are empty; a manual constructor is
still explicit in its strategy, notes and throwing source hook.

`runtimeValidityVerified` and `dependencyAvailabilityChecked` are false. A plan
is a scaffold, not a missing-dependency finding, runtime-binding inference or
proof of a passing test. Test roots are suggestions rather than reserved paths;
check names, package prefixes, collisions and current writability before writing.

The same project/module/source-set isolation, external refresh, smart/committed
read, disposal/cancellation handling, 30-second deadline, post-serialization
freshness check and 512-KiB UTF-8 response bound apply. Unsupported recipes use
`TEST_PLAN_UNAVAILABLE`; other standard target/request errors remain unchanged.
The entire source and notes count toward the byte limit; JSON is never cut to fit.
No request saves/commits files, opens a preview, writes source/build/root settings,
changes the sidebar/clipboard, executes a test, or starts Spring/an application.
The known native host cancellation/session-expiry limitation still applies.

## Runnable examples and limits

- [Plain test demo](../examples/plain-test-demo/README.md): actual MCP-generated
  PageParameters page and String-ID panel scaffolds compile/run without Spring
  or Mockito. Adapted tests assert default/requested names and panel contents.
- [Complex Spring workflow](../examples/spring-test-demo/COMPLEX_WORKFLOW.md):
  manual constructor arguments, two named mocks, an explicit generic bean, and
  custom WebSession setup. Returned manual guards remain until reviewed.

Both examples have disposable failing-regression/restoration checks using the
same standard-library verifier. These are bounded fixture demonstrations;
arbitrary application setup and production accuracy/performance remain unverified.

For the standard native host fixture, allow all eight tools and run
`python3 tools/check_mcp_test_plan.py`; it checks plain/Spring modes, exact Spring
reuse, metadata, deterministic responses and externally edited constructor
refresh/restoration. The concurrency checker includes the general planner in
both plain modules and the dedicated Spring planner in its own module.

## Verification

The complete normal suite passes **1,749 tests**, the separate actual-host suite
passes **seven**, and **20 release-checker unit tests** pass, with no failures,
errors or skips. Packaging and project/structure checks pass. Binary verification
is **Compatible**, with the existing four deprecated and six experimental API
notices. [Portable evidence](test-planning-evidence.json) identifies the exact ZIP.

The exact MCP-produced plain page/panel scaffolds compile and run as two smoke
tests with no Spring/Mockito on the demo classpath. Adapted plain tests pass
three cases; the controlled greeting mutation fails two, and restoration passes
three. The Spring demo passes eight cases; its controlled price mutation fails
the two complex cases, and restoration passes eight. Codex invokes the general
planner read-only and separately adapts the complex Spring test using existing
MCP tools plus normal editing/testing commands.

The native protocol checker verifies shared Spring output and external constructor
refresh/restoration; existing semantic regressions and 13 concurrent sessions pass.
Freshness, source-set/module/project isolation, preview/create/undo, no-write and
response-bound behavior remain tested. Temporary hosts are stopped after use.
Preview 2 includes the refreshed eight-tool graphical/client walkthrough; its
exact artifact and installation scope are in [release evidence](mcp-release-evidence.json).
Later bytecode changes require their own release verification.
