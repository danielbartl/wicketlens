# Component factories and repeated source templates

This unreleased development milestone follows [source layouts](source-layouts.md)
and [fragments](fragments.md). Inbox now has **31 bindings: 26 confirmed ordinary
bindings and 5 inferred template bindings**. The original resource layout is
unchanged. All 36 example page sources still pair with their conventional markup.

## Supported source contracts

The existing path/value interpreter and layout pass retain their precedence.
A further invocation of the same layout analyzer enables bounded factories when
those passes cannot expand setup. It publishes an upgrade only after an actual
factory invocation or reviewed symbolic population is proved. The later
[owned form-field milestone](owned-form-fields.md) also permits a proved isolated
form summary to publish wrapper attachments. Failure cannot
silently regain confirmed bindings through direct fallback.

A supported factory is a component-returning lambda or bound source method
reference invoked with a known String ID or a generated repeater ID and optional
opaque model/data argument. Parameters, captured locals, lexical receivers and
component fields retain their identities. An immediate factory can return an
already-created unattached component, as Inbox's split-pane factories do. A
repeated factory must create a fresh component and forward the generated ID.
Reusing a captured component, changing a captured component scope, returning
through an unknown alias, escaping a factory into opaque data, and recursive or
unbound invocation remain unresolved. General constructor-reference placement
remains unsupported; the later form milestone supports private input factories
inside opaque owned fields. The later
[replacement-editor milestone](replacement-editors.md) also follows registered
constructor references into separate event-site contexts.

Source component constructors with these factories can expose their own child
setup and conventional Panel markup. This is proved per allocation; an unrelated
component of the same type does not inherit another instance's proof. Source
initialization/markup-sourcing overrides outside the reviewed contract prevent
expansion. Opaque sibling components retain their attachment facts and explicit
uncertainty about internals.

The source pass folds reviewed scalar conditions without evaluating models or
application services. Unknown branches and unsupported control flow prevent
promotion. Void returns terminate their source helper; early exits in symbolic
population are rejected. Captured scalar writes invalidate affected selectors,
including writes reached through bounded source helpers.

## Symbolic population

A reviewed `Iterator.hasNext()` block containing default `RepeatingView.newChildId()`
is analyzed once as a population template. Nested generated containers preserve
their generating repeater and intervening scopes. A generated child must attach
under its own generator. A repeated body cannot mutate its enclosing setup scope.
Iteration count, actual IDs, provider contents and model values are not evaluated.

Inbox's observed scopes are:

| Logical path | Meaning |
| --- | --- |
| `inbox:firstPane:first` | Captured load-more component in the split panel's first pane. |
| `inbox:second` | Captured detail Fragment instance in the second pane. |
| `inbox:second:message:subject` | Ordinary detail binding; runtime visibility/model values are unknown. |
| `inbox:firstPane:first:list:batch:item:open:subject` | Inferred mail-row template binding. Both generated batch and row identities are omitted from path segments. |

The graph retains both logical generated items as `templateItem=true`,
`template=true`, with no `wicketId`. Their descendants retain template flags and
matched findings use **INFERRED** confidence. A template item's own context is
selected by node ID. No path such as `batch:0:item:0` is invented.

## Effects and framework boundaries

Source constructors, data initialization, configuration and callbacks remain
bounded and read-only. An isolated component's configuration can be summarized
without flattening its internals. Framework Ajax rendering/feedback, model/data
access and tag/header operations have reviewed contracts; they are not executed.
Source helper effects are followed with actual receiver and argument ownership,
including generic overrides. Unknown component-valued results remain borrowed.
Aliases through Object, branches, casts and arrays cannot become fresh components.

Known page mutations, reattachment of captured components, graph-reference
storage, unreviewed compiled construction, custom synchronous behavior binding,
and unavailable/recursive effects block the affected expansion. Available source
data initializers, model/provider/iterator implementations and implicit object
callbacks are audited for graph effects. Reviewed Ajax behavior binding stores its
host and sets its markup-ID flag. Wicket 10 also queries the behavior's stateless
hint during binding; source `bind`, `onBind` and `getStatelessHint` overrides remain
guarded. The exact Wicket 10.11.0 and 11.0.0 sources were reviewed for these
contracts.

Later actions such as resetting owned list contents do not establish a concrete
post-event tree. The report describes bounded initial source setup and symbolic
populations, with runtime subclasses, services, model values and later event
execution still partial. It is not a runtime-validity or visibility guarantee.
The subsequent [origin-use audit](id-origin-audit.md) permits isolated identity-only
attachments while keeping projected children and shared/template origins guarded.

## Markup scopes, snapshots and MCP decision

Owned source markup and Fragment definitions are composed in bounded passes.
Original tag/attribute locations remain intact, while each instance receives
separate markup node identities. Provider identities keep identical definition
names in different owned scopes separate. Placeholder previews are suppressed.
Missing/ambiguous/unsupported companion markup and exhausted projection budgets
leave the corresponding descendants unresolved.

MCP decision: extend existing structure, validation and component-context facts
through the shared semantic analyzers and value snapshots. No new tool or request
parameter is needed. Schema **1** and existing fields remain, with optional additive
metadata:

| Member | Meaning |
| --- | --- |
| `components[].sourceSetupExpanded` | The allocation's bounded source setup was expanded. This alone does not prove complete markup or runtime behavior. |
| `components[].ownedMarkupPath` | Conventional owned resource successfully composed for this instance. |
| `markup.elements[].markupProviderNodeId` | Provider scope for projected elements, separating repeated source resource instances. Root elements retain the existing implicit root provider. |

Existing fragment provenance, repetition/template flags, findings and source
locations are reused. Component-context paths skip logical items as before.
All consulted factory/source contracts and composed resources join snapshot
freshness checks. Unsaved changes invalidate native/MCP snapshots without saving.
No application-specific Oat type list or second MCP parser is introduced.

The factory-enabled pass permits 20,000 analysis steps, 96 dependency paths,
256 allocations, 16 active source calls, 64 factory invocations and 16 symbolic
loops. Captured scalar/helper and deferred-effect discovery have separate bounds
of 128 source calls and 12 levels. Identity tracing and source hierarchy limits
remain in force. Combined owned/fragment markup projection permits 64 scopes,
16 rounds and 4,096 generated elements. Cancellation propagates, and the existing
512-KiB response limit still applies. The preceding layout pass keeps its own
smaller bounds.

## Verification and remaining scope

Focused PSI and MCP tests cover immediate captures, method references, nested
scopes, generated-ID forwarding, unsafe captures, Object/array/conditional aliases,
initialization and implicit data effects, synchronous Ajax binding, scalar
selector invalidation, early returns, recursion, unsaved edits, module isolation,
read-only behavior and existing editor/form/repeater guards.

A separate Wicket 11 WicketTester process verifies captured split panes and two
nested generated levels with two different row values. It observes the runtime
children without assuming generated IDs. The read-only Oat corpus verifies the
logical Inbox paths, native page-owned/definition references, MCP agreement,
confidence and resource dependencies against both Wicket 10.11.0 and 11.0.0.
Commands and corpus outputs follow [the source-layout runner](source-layouts.md#verification-and-application-corpus).

Representative follow-up inspection remains explicit:

- ProductsPage uses `ProductFields::new`, dialog/editor lifecycle callbacks and a
  named nested companion. Constructor-reference and nested editor contexts are
  not established by this milestone; its graph remains unresolved.
- OrderWizardPage registers step factories and source lifecycle hooks with
  selection/history behavior. Those custom callbacks remain outside this bounded
  constructor/population subset; no complete wizard graph is claimed.
- AccountsPage relies on stateful tree expansion/provider protocols and custom
  content identity. No arbitrary tree row placement is inferred.

Those pages remain at zero matched bindings in this corpus. The following
[navigation milestone](nested-navigation.md) now pairs named nested companions
and adds bounded caller contexts. Broader wizard/tree lifecycle support remains
follow-up work; reverse discovery does not enumerate all callers. No running Oat
server, live IDEA 2026.3 installation or full application accuracy/performance
study is implied. See [the evidence summary](wicket-oat-factory-evidence.json).
