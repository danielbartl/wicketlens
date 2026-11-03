# Mounted URLs

RC 1 adds **View → Tool Windows → WicketLens → Mounted URLs**.
This project-wide view is independent of the Components view's editor following.
It does not require Spring or IntelliJ's Spring integration.

Refresh discovers concrete Java `WebApplication` subclasses in configured Java
source roots. Rows show each `mountPage(path, Page.class)` or inline
`mount(new MountedMapper(path, Page.class))` declaration reached
through reviewed source `init()` setup: path, page, application, module,
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
| `DECLARED` | Resolved registration reached through reviewed setup, with a constant String path and page class literal. |
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

## Inherited setup and helpers

The selected application's effective source `init()` is the entry point. An
inherited implementation is followed when the leaf has no override. An override
contributes superclass registrations only where it explicitly calls `super.init()`;
parent bodies are not automatically combined. Each application keeps its own
group, even when several applications share the same source implementation.

Within that setup, private/final no-argument, void instance helpers on the same
application are expanded. Explicit unqualified `super` calls can also select a
source superclass helper without virtual dispatch. Helpers can call other
eligible helpers; each call contributes its own observations, in source traversal
order. The registration link points to the actual `mountPage` or `mount` in the helper/base
source. Row notes show the reached method path. Observation IDs are distinct per
call and snapshot, not persistent runtime route identities.

Conditional/non-straight-line callers propagate `CONDITIONAL` into reached mounts;
conditions inside a helper are also retained. No condition is evaluated to assert
runtime activation. Source errors in a reached method/caller make those mounts
unresolved. Every traversed source ancestor and constant dependency participates
in snapshot freshness.

Open virtual helpers, parameterized/static/value-returning helpers, interface
defaults, compiled or external application ancestry, other receivers, callbacks,
package/resource mounts, mapper variables/factories/wrappers/named subclasses and
root mapper mutations are not expanded.
Nested/anonymous classes, lambdas and method references retain deferred coverage
notes. Recursive calls stop at the cycle with a coverage note. These boundaries
preserve known observations without claiming a complete runtime route table.

## Inline MountedMapper registrations

An inline `mount(new MountedMapper(path, Page.class))` on the current application
uses the same constant evaluator, page/HTML resolution, setup-path notes and
conditional status as `mountPage`. The outer `mount` must resolve to Wicket's
`WebApplication.mount(IRequestMapper)`; an application override is not assumed to
have framework semantics. Constructing a mapper without mounting it does not add
a row. Parenthesized arguments and self receivers are accepted.

Both the two-argument constructor and the three-argument constructor with an
explicit `IPageParametersEncoder` are recognized. Explicit encoders receive a
note that parameter encoding is not analyzed; the row describes the declared
path and target, not the complete URL behavior.

Page-supplier constructors retain a known path but leave the target unresolved;
suppliers and factory methods are not executed. Inline anonymous subclasses
retain known constructor path/page facts and navigation, but are always
`UNRESOLVED` because their overrides and initialization are not reviewed. Nested
registrations inside anonymous bodies are not attributed to the application.
Invalid constructor/call resolution also makes the observation unresolved.

Mapper variables, fields, factories, wrappers, named subclasses and
`getRootRequestMapperAsCompound().add(...)` are outside this slice, with coverage
notes for unexpanded calls. This avoids inferring routing semantics from arbitrary
custom mapper code. Package and resource registrations remain future work.

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
20,000 traversal/evaluation steps, and 4,096 inheritor candidates. Per application,
source ancestry is limited to 12 levels, the setup stack to eight active methods,
and expansion to 128 invocations (including the entry point). Reaching a
limit preserves bounded observations with an explicit truncation indication.
Select a narrower application/module when necessary. The sidebar does not impose
the separate MCP JSON byte limit.

Try `example.RoutesApplication` in [the portable demo](../examples/demo/pom.xml).
It mounts `CustomerPage` at `/customers` and `/customers/${id}`. The corresponding
read-only agent interface is [wicket_get_routes](mcp-routes.md).
`InheritedRoutesApplication` additionally demonstrates an inherited `init()` and
private helper mounting the same page at `/inherited-customers`.
`MountedRoutesApplication` demonstrates inline mapper registration at
`/mapped-customers/${id}` and an explicit encoder at `/encoded-customers`.

Later slices can expand helper arguments/dispatch, other mapper registration
forms and package/resource mounts, then consider scoped duplicate/path
conflict inspections. They should extend this analyzer rather than build a
parallel route index.
