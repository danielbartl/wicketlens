# Mounted URLs

RC 1 adds **View → Tool Windows → WicketLens → Mounted URLs**.
This project-wide view is independent of the Components view's editor following.
It does not require Spring or IntelliJ's Spring integration.

Refresh discovers concrete Java `WebApplication` subclasses and anonymous
applications directly returned by Spring `@Bean` methods in configured Java
source roots. Rows show page, package and resource mount declarations reached through
reviewed source `init()` setup. The table shows the mounted path and target.
When several applications are visible, the target label includes its
application. Selection details include the application, module, source set and
status. Conditional, unresolved and possibly conflicting paths carry icons.
Repeated declarations remain separate. Applications without observed mounts
still contribute coverage notes.

Search paths, pages, applications, modules or statuses. Narrow the module,
application, or source set (`main`, `test`, `any`; default `main`) and refresh to
scan that scope. Filters also narrow the current snapshot locally; switching to
a scope that was not scanned requires refreshing.

Click an underlined mounted path to open the Java page. The pointer changes to a
hand over the path. Enter and double-click also open the selected page. The toolbar offers **Go to HTML**,
**Go to registration**, and **Analyze page**. HTML navigation requires a uniquely
resolved conventional default companion. Paths whose target has no project
source appear as plain text.

Choose **Generate Wicket Mounted Test…** on a supported page or resource mount
to preview a WicketTester test that requests the path through the source application.
[Mounted test planning](wicket-test-planning.md#mounted-page-tests)

An orange **W** in the gutter marks the Java page declaration and the opening
tag of its conventional HTML companion. Hover to see the declared
application-relative paths, including aliases and conditional status. The
pointer changes to a hand. Click the badge to open **Mounted URLs** filtered to
that page across applications and source sets. **Clear page filter** restores
your previous filters. The tooltip shows up to 24 distinct paths.

MCP needs no change for these editor and browser features. The clickable paths,
details, gutter tooltip and page filter use the existing route declarations and source
locations from the shared analyzer.

## Declaration status and coverage

| Status | Meaning |
| --- | --- |
| `DECLARED` | Resolved registration reached through reviewed setup, with a constant String path and a reviewed registration target. |
| `CONDITIONAL` | Known path and target, but registration has conditional or non-straight-line reachability. |
| `UNRESOLVED` | The path, target, call resolution, or source is uncertain; known facts and the registration location remain available. |

These statuses describe source declarations. Later unmounting, custom mappers,
deployment and application initialization can change active runtime routes.
Mounted URLs and the **Possible Wicket mounted route conflict** Java inspection
flag repeated, fixed paths reached unconditionally within the same application.
The check omits pattern paths, package prefixes and routes affected by unreviewed
root changes. Conflict notes appear in the shared route snapshot.

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

Within that setup, WicketLens expands closed void instance helpers on the same
application. A helper may be private, final, declared on a final source class, or
selected by an explicit `super` call. Directly forwarded unchanged arguments
can supply paths, page class literals and mappers. Each call contributes its own
observations. The registration links point to the reached source and the row
notes show the method path.

Conditional/non-straight-line callers propagate `CONDITIONAL` into reached mounts;
conditions inside a helper are also retained. No condition is evaluated to assert
runtime activation. Source errors in a reached method/caller make those mounts
unresolved. Every traversed source ancestor and constant dependency participates
in snapshot freshness.

Open dispatch, callbacks and ancestry outside source retain coverage notes.
Recursive setup stops at the cycle. Source discovery is bounded and represents
known observations with the uncertainty from their callers.

## Anonymous applications returned by Spring beans

A source method with a resolved Spring `@Bean` annotation and a
`WebApplication` return type (or subtype) can supply the application. The
method body must directly return one compatible anonymous application allocation.
Parentheses around the allocation are supported. The anonymous application's
source `init()` uses the same route, inheritance and helper analysis as a named
class. Local variables, helper-returned allocations, and conditional factories
are outside this coverage. Spring profiles, bean aliases, factory arguments,
activation and runtime routing remain unknown.

The application details and MCP `className` field use
`fully.qualified.Configuration#method`. The application's source link points
to that method. An exact filter selects all reviewed overloads of the method,
each with its own source location. Identically named declarations in separate
modules keep their own route groups and can be narrowed with the module filter.
Registration links still point to the actual mount statements.

This extends the existing `wicket_get_routes` tool because agents can discover
and filter the same bean-defined mounts as the Mounted URLs browser. Existing
named-class selectors, response fields, mount
statuses, bounds and read-only lifecycle apply to both application forms.

## Mapper and root registrations

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

The analyzer also follows unreassigned method-local mapper initializers, bounded
closed single-return source factories, and named subclasses whose only
initialization is forwarding to a reviewed mapper constructor. It recognizes
`HttpsMapper` and `CryptoMapper` wrapping a reviewed declaration or the current
root. Crypto-wrapped paths carry unresolved runtime URL handling. Page suppliers,
custom overrides, reassigned locals and arbitrary mapper implementations retain
uncertainty.

Direct `mountPackage` and `mountResource` calls and reviewed package/resource
mapper constructors contribute application-relative paths. A package mount
does not enumerate member pages. A resource mount does not evaluate the
reference. Both can be opened at the registration source.

Supported root changes include direct compound-root additions, fresh compound
root replacement, direct reviewed wrappers, unmount and direct compound removal.
A removal or replacement can make an earlier observation unresolved while
preserving its location. Unreviewed root operations retain coverage notes.

## Snapshot safeguards

Discovery waits for indexing and committed documents, refreshes source/class
roots, and retains value snapshots without cached PSI. Edits, source/root changes
and new files invalidate the snapshot. Clicking an out-of-date path refreshes
the previously scanned scope and opens the Java page if the same application,
path and target are still present. Toolbar navigation and page analysis require
a current snapshot. The browser rechecks the destination in a background read
before opening it. A click that becomes stale while queued also refreshes and
rechecks the mount, with a bounded retry if the source changes during refresh.
An unsuccessful refresh keeps the previous paths available for another click.
This changes sidebar navigation only. MCP route discovery and response fields
stay the same. Cancellation and a 30-second request deadline use the
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
