# Mounted URLs

Development builds add **View → Tool Windows → WicketLens → Mounted URLs**.
This project-wide view is independent of the Components view's editor following.
It does not require Spring or IntelliJ's Spring integration.

Refresh discovers concrete Java `WebApplication` subclasses in configured Java
source roots. Rows show each direct `mountPage(path, Page.class)` declaration in
the application's own zero-argument `init()`: path, page, application, module,
and status. Repeated declarations remain separate. Applications with no observed
mounts still contribute coverage notes.

Search paths, pages, applications, modules or statuses. Narrow the module,
application, or source set (`main`, `test`, `any`; default `main`) and refresh to
scan that scope. Filters also narrow the current snapshot locally; switching to
a scope that was not scanned requires refreshing.

Select a row and use **Go to page**, **Go to HTML**, or **Go to registration**.
Double-click or press Enter to open the Java page. HTML navigation is available
for a uniquely resolved conventional default companion. **Analyze page** reuses
the existing Components analysis and selects that view. Dependency classes can
have a resolved page name without navigable Java source; inherited/variant HTML
is outside this initial route lookup.

## Declaration status and coverage

| Status | Meaning |
| --- | --- |
| `DECLARED` | Resolved direct registration with a reviewed constant String path and page class literal. |
| `CONDITIONAL` | Known path/page, but registration has conditional or non-straight-line reachability. |
| `UNRESOLVED` | The path, target, call resolution, or source is uncertain; known facts and the registration location remain available. |

These statuses describe source declarations. Even `DECLARED` does not guarantee
an active runtime URL: later unmounting, helpers, custom mappers, deployment and
application initialization can change routing. No duplicate-route inspection or
runtime validity claim is made. Conditions are not evaluated in this slice.

Reviewed source and compiled String constants and concatenations reuse the
existing constant evaluator. Dynamic paths or page targets stay unresolved.
Wicket patterns such as `/customers/${id}` and `#{optional}` remain literal;
paths are relative to the deployed application. Host, port and context path are
unknown. The home page is not invented as an explicit `/` mount.

Inherited `init()`, helper registrations, package/resource mounts, custom
`MountedMapper` instances, nested/anonymous classes, and deferred lambdas are not
expanded. Calls on another or unresolved receiver are not attributed to the
current application. Coverage notes explain these boundaries. The initial view
cannot enumerate every runtime route.

## Snapshot safeguards

Discovery waits for indexing and committed documents, refreshes source/class
roots, and retains value snapshots without cached PSI. Edits, source/root changes
and new files invalidate the snapshot. Stale rows cannot navigate or start page
analysis until refreshed. Cancellation and a 30-second request deadline use the
same lifecycle as MCP page analysis. Cancel or Refresh replaces pending work;
project closure cancels project-owned jobs.

Structural freshness currently uses the IDE-wide VFS structure counter, so an
unrelated file creation/deletion can also require a refresh. Discovery results
and navigation remain scoped to the selected project.

Discovery is bounded to 64 applications, 1,000 mounts, 512 source/dependency files,
20,000 traversal/evaluation steps, and 4,096 inheritor candidates. Reaching a
limit preserves bounded observations with an explicit truncation indication.
Select a narrower application/module when necessary. The sidebar does not impose
the separate MCP JSON byte limit.

Try `example.RoutesApplication` in [the portable demo](../examples/demo/pom.xml).
It mounts `CustomerPage` at `/customers` and `/customers/${id}`. The corresponding
read-only agent interface is [wicket_get_routes](mcp-routes.md).

Later slices can expand reviewed helpers/inherited setup, explicit custom mapper
registrations and package/resource mounts, then consider scoped duplicate/path
conflict inspections. They should extend this analyzer rather than build a
parallel route index.
