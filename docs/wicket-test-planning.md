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

## Mounted page tests

In **WicketLens → Mounted URLs**, select a supported page mount and choose
**Generate Wicket Mounted Test…**. Review the Java test in the usual
preview, then copy it or create it in an existing Java test root.

The test constructs the selected source application's `WebApplication` in
WicketTester. It calls `executeUrl` with an application-relative path, checks
HTTP 200 and the rendered page, and asserts path parameter values. A declaration at `/orders/${orderId}` produces
the request `orders/42` and an assertion that `orderId` equals `42`.
Example values are deterministic and editable. For supported optional
placeholders, the scaffold also requests the URL with all optional segments
omitted, asserts the remaining values, and checks that the omitted parameters
are absent.

In the preview's **Route parameters** tab, replace sample values with known
test data. Required values feed both requests. Values are one URL-safe segment
of 1–64 letters, digits, dots, underscores, tildes or hyphens. The planner
rejects `.` and `..`, names outside the mount, and values that duplicate a
fixed segment.

This slice covers plain Wicket pages and named, top-level source applications
with accessible no-argument constructors in the page's module and source set.
It accepts unconditional, nonconflicting standard page mounts with fixed
segments and distinct required or optional placeholders. The omission case
must have a nonempty request path. Explicit encoders, wrappers, Spring setup,
and routes with uncertain reachability or targets remain outside the supported
scope. Review application startup and configuration before running the scaffold.

### Application sessions and authentication

An application that directly overrides `WebApplication.newSession` and returns
a fresh source `WebSession` subclass with the supplied request gets a session
class assertion in the generated route test. The session declaration and
factory location participate in freshness checks.

The planner also recognizes a direct source `AuthenticatedWebApplication`
subclass with literal `getWebSessionClass` and `getSignInPageClass` returns,
ordinary `super.init()` followed by direct literal page mounts, and a mounted
page with literal `@AuthorizeInstantiation` roles. It generates separate checks
for the anonymous redirect to the sign-in page and a signed-in request to the
mounted page. The generated test calls Wicket's public session `signIn` method
inside a WicketTester request. Give the test environment a dedicated account
with a declared role through `WICKET_TEST_USERNAME` and `WICKET_TEST_PASSWORD`.
Only these variable names appear in the scaffold and response. The application
still determines authentication and role membership when the test runs.

This recipe covers that source-declared Wicket auth-roles flow. Custom login
forms, SSO, opaque bootstrap, inherited application overrides and credentials
provisioning require application-specific setup.

## Mounted resource tests

Select a resource in **Mounted URLs** and use the same **Generate Wicket
Mounted Test…** action. The generated test uses the selected plain Wicket
application, checks HTTP 200 and a nonempty `Content-Type` header, and confirms
that the request did not render a page. Enter an optional expected media
type such as `image/svg+xml` in the preview. The assertion compares the media
type case-insensitively, allowing response parameters such as a charset.
The **Resource assertions** tab also accepts the expected SHA-256 of the
response bytes. The generated test checks a known fixture response and detects
a body change even when HTTP status and media type still match.

Resource planning requires an unambiguous, unconditional fixed path and
a named application source with an accessible no-argument constructor.
The generated test exercises the application's resource registration.
The fixture compiles the generated source against Wicket 11 and checks that
moved routes, changed page parameters, media type and response bytes cause failures.

## Dependency graphs

When source analysis finds injection fields, the preview's **Dependencies** tab
lists each component and field with its lookup and planned test binding. A
`MOCK` row names a mock actually registered by the isolated Spring recipe.
Several fields may share that mock. `MANUAL` means the generated test requires
setup, such as conflicting Spring bean types or a source-registered Guice
injector. Guice-only plans include a throwing setup method until the developer
configures the test application's bindings. Production bean candidates remain
source evidence and do not choose isolated test bindings.

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

For a mounted plan, also send `routePath` with the exact declaration pattern
from `wicket_get_routes`. If several applications share the pattern, send
the relevant `applicationClass`. A path must identify one supported mount of
the requested page. The same preview and source rendering are used by both
entry points.

To use realistic sample values, include `routeParameters` as an object such as
`{"orderId":"order-A1","view":"summary"}`. Keys must match declared
placeholders. Omitted keys retain generated sample values.

For a mounted resource, set `mountedResource` to true, supply the owning
`WebApplication` as `className`, and use its exact mount as `routePath`. Pass
`expectedContentType` to add an exact media-type assertion. The application
class and module/source-set filters select the resource scope. Pass
`expectedBodySha256` as 64 hexadecimal characters to check response bytes.

For example, this request selects an SVG resource test.

```json
{
  "projectPath": "<open-project>",
  "className": "example.AssetsApplication",
  "routePath": "/logo.svg",
  "mountedResource": true,
  "expectedContentType": "image/svg+xml"
}
```

The envelope remains schema **1**, with a new `testPlan` member using schema **1**.
Its fields match the shared [Spring plan](mcp-spring-test-plan.md): constructor
strategy, suggested names, exact Java source, mocks/manual bindings, notes,
requirements, test-root suggestions and scope notes. `kind` distinguishes
`isolated_wicket_tester_scaffold` from `isolated_spring_wicket_tester_scaffold`.
For recognized Spring intent, the payload equals the dedicated Spring tool's
`springTestPlan`. Plain plans have no generated mocks. They include explicit
setup hooks for reviewed manual constructors and source-registered Guice fields.

`runtimeValidityVerified` and `dependencyAvailabilityChecked` are false. A plan
is a scaffold, not a missing-dependency finding, runtime-binding inference or
proof of a passing test. Test roots are suggestions rather than reserved paths;
check names, package prefixes, collisions and current writability before writing.

Mounted page plans use kind `mounted_wicket_tester_scaffold` and constructor strategy
`MOUNTED_ROUTE`. The `mountedRoute` object contains `applicationClass`,
`declarationPath`, `requestPath`, expected `parameters`, and the registration file
and offset. Optional-page plans add `withoutOptionalParameters` with the omitted
request path, remaining values and names expected to be absent. Resource plans
use kind `mounted_resource_wicket_tester_scaffold`, constructor strategy
`MOUNTED_RESOURCE`, and include `expectedContentType` and normalized
`expectedBodySha256` when supplied. Both track route source dependencies for
freshness. Reviewed page sessions add optional `mountedRoute.session` with
the class and source locations. Its optional `authentication` contains the
sign-in page and registration locations, declared roles, the authorization
location and the two credential environment variable names.

The general and dedicated Spring tools can return `dependencyGraph`. Its
`owners` link the existing component `nodeId`, `componentPath` and source location
to their `fields`. Each field includes `lookup`, `testBinding` (`MOCK` or
`MANUAL`), and an optional `mockName` referring to `mocks`. A field can also
carry `injectorRegistration`. Candidate counts and `discoveryLimited` describe
the bounded source evidence. Use `wicket_get_injected_dependencies` for the
individual source bean candidates.

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
