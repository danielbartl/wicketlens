# MCP mounted routes

RC 1 adds **`wicket_get_routes`**, the ninth WicketLens tool.
Released Preview 2 still has eight tools. The new tool reuses the
[Mounted URLs analyzer and value snapshot](mounted-urls.md); it does not inspect
or change sidebar selection and does not execute the application.

## Request

```json
{
  "projectPath": "<exact-open-project-root>",
  "moduleName": "<optional-exact-module>",
  "applicationClass": "example.RoutesApplication",
  "sourceSet": "main"
}
```

Only `projectPath` is required by the host. `moduleName` and `applicationClass`
are optional exact filters. `sourceSet` is `main`, `test`, or `any`, defaulting to
`main`. Unlike page tools, this tool does not require a `className`. Discovery
targets concrete Java `WebApplication` subclasses and anonymous
`WebApplication` instances directly returned by resolved Spring `@Bean`
methods in the selected project's Java source roots. For a bean,
`applicationClass` accepts the source selector
`fully.qualified.Configuration#method`, also returned in the application's
`className` field. The selector matches all reviewed overloads in scope.
Library classes are outside application discovery.

## Response

Envelope schema **1** and independent `routes` schema **1** include:

- `tool`, `status`, exact `target` filters and `truncated`.
- `snapshot.kind: committed_static_analysis` and freshness checked after serialization.
- `routes.runtimeRoutesComplete: false`, `applicationRelativePaths: true`, and coverage notes.
- `applications`: class or bean selector, module, source set, Java location, mounts and application coverage notes.
- Each mount: observation `id`, nullable `path` / `pageClass`, `DECLARED` /
  `CONDITIONAL` / `UNRESOLVED` status, registration location, nullable Java/HTML
  locations, `analyzablePage`, and uncertainty/navigation notes.

Locations use existing file paths and UTF-16 source offsets. Observation IDs are
snapshot locations, not persistent runtime route identities. Known facts survive
unresolved arguments; an empty result is successful discovery with stated coverage,
not proof that the application has no runtime routes. Application-relative Wicket
placeholders are preserved literally.

Java `init()` discovery uses the same bounds and status meanings as the native
view. Source inheritance, explicit `super` calls and closed instance helpers with
unchanged arguments are expanded. Local mapper initializers, closed source
factories, forwarding subclasses, selected wrappers and reviewed compound-root
changes contribute mount observations. Package and resource rows have null page
locations and identify the target kind in notes. Possible fixed-path conflicts
also appear in the existing notes. Setup-path notes and registration locations
identify the reached methods and declarations.

Open dispatch, deferred callbacks, dynamic paths and runtime route precedence
remain unknown. A later removal or root replacement can make a previously
observed declaration unresolved. [Mounted URLs coverage](mounted-urls.md)

For a bean application, the Java location points to its declaration method.
The same source `init()` rules apply. Runtime bean selection, profiles and
activation remain unknown. See the
[bean declaration contract](mounted-urls.md#anonymous-applications-returned-by-spring-beans).

Inline `mount(new MountedMapper(...))` uses the same mount fields and registration
locations. Two- and three-argument constructors are recognized; explicit parameter
encoders receive coverage notes. Known constructor path/page facts survive
uncertainty. Page suppliers remain unresolved and are never invoked. Anonymous
mapper overrides/initialization are not reviewed, making those observations
`UNRESOLVED`; nested anonymous registrations are not expanded. Only the resolved
framework `mount` operation is attributed to the application. See the
[inline mapper contract](mounted-urls.md#inline-mountedmapper-registrations).

## Lifecycle and bounds

The tool shares page analysis's project lifetime, external refresh, smart and
committed reads, 30-second deadline, cancellation propagation and final freshness
validation. It also records project-wide structural changes, including a new
application after an empty discovery. No editor commits/saves, navigation,
clipboard writes, file edits or generated files are performed. Existing tools
and their response contracts remain unchanged.

The VFS structure counter is IDE-wide: unrelated file creation/deletion can
conservatively invalidate a request. Discovery data remains project-scoped.

Discovery caps: 64 applications, 1,000 mounts, 512 source/dependency files,
20,000 traversal/evaluation steps, and 4,096 inheritor candidates. Per application,
source ancestry has 12 levels, active setup depth eight methods, and setup
invocations 128 including the entry point. Cycles stop with coverage notes.
Reaching a resource cap
sets `truncated` with coverage notes. JSON is separately limited to **512 KiB
UTF-8**; an oversized response is rejected whole as `RESPONSE_TOO_LARGE`. Narrow
the module/application filter rather than parsing partial JSON.

Structured error JSON has `schemaVersion`, `tool`, `status: error`, and
`error.code` / `error.message`. Errors include `INVALID_REQUEST`,
`MODULE_NOT_FOUND`, `APPLICATION_NOT_FOUND`, `PROJECT_UNAVAILABLE`, `TIMED_OUT`,
`STALE_SNAPSHOT`, and `RESPONSE_TOO_LARGE`. Synchronous internal capture also
rejects indexing and uncommitted documents; normal calls wait within the deadline.
Caller cancellation is propagated rather than serialized as successful partial
discovery. The existing bundled host's wire cancellation limitation still applies;
see [client setup](mcp-client-setup.md).

Example agent request: “Call `wicket_get_routes` for this open project, list the
observed page mounts by application and module, and explain unresolved paths and
discovery limits. Use page structure/context tools for a selected resolved page.”
