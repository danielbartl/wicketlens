# Constructor-reference editors and replacement-site contexts

This seventh development milestone connects `ProductsPage`'s `ProductFields::new`
registration to the source replacement route:

`products:editor:dialog:form:content`

The six fields beneath it are name, category, price, stock, description and
suppliers. The optional context has **11 MATCHED / INFERRED bindings**: the five
placement components and six editor fields. ProductFields' independent report
retains its six confirmed bindings.

## Phase and confidence

The fields are created when New/Edit reaches the factory invocation. They are
not present in the initial placeholder tree, and closing/reopening can replace
them. The new graph is explicitly an **EVENT_TEMPLATE** at the declared replacement
site. It does not describe the current runtime tree, guarantee that the event
runs, or predict record/model values, visibility, validation, persistence, or
subsequent page state. Other page/event effects remain unknown.

The initial analysis remains unchanged. ProductsPage's primary report is still
partial; this milestone adds a separate source context rather than turning its
event-created fields into initial children. Unrelated table rows, toolbar
callbacks and confirmation-dialog internals remain outside the projection.

## Source contract

The shared semantic analyzer follows an unbound source constructor reference for
a top-level/static member markup owner. A source fluent setter must store the
callback in a private instance field on its own receiver and return that receiver.
The callback has a two-argument component factory signature. The host has one
local allocation, one registration and one direct page-constructor attachment.
Reassignment, competing registrations and known direct structural mutations
withhold the route.

A unique direct source invocation of the registered functional-interface method
must pass a known String ID and produce a local component. Constructor identity
tracing checks that the editor preserves that ID. A single direct consumer
forwards the component through available source parameters to an owned
`MarkupContainer.addOrReplace` call. ID-query guards are supported. Parameter
escapes, foreign receivers, competing consumers, unknown IDs, conditional/nested
transfers, recursion and consumer overrides remain unresolved.

The constructor skeleton establishes the declared ancestry through direct
allocations and additions. Selected component fields must be private final
instance fields. Multiple parents and known constructor mutations invalidate the
path. Anonymous host configuration and anonymous receiver initialization remain
guarded. Opaque sibling internals do not define the selected route.

This is a bounded declaration/parameter-flow contract, not a general event-effect
interpreter. In particular, it does not certify arbitrary helper effects, behavior
callbacks, services, model getters or later changes elsewhere in the page.

The analyzer reuses the existing constructor-identity proof, source constants,
companion resolver, markup extractor, independent editor `PageReport` and graph
matcher. Each route segment must have a unique supported resource-local markup
path. Custom markup sourcing and unresolved markup contexts withhold the route.
Editor-owned fragments, generated/conditional scopes and queue placement need
separate contracts. Private form inputs stay opaque as before. No Oat class-name
allowlist or separate MCP binding implementation is introduced.

The exact Wicket 10.11.0 and 11.0.0 `MarkupContainer` sources were reviewed:
`addOrReplace` uses the component's ID to choose addition or replacement. Both
source files have SHA-256
`4b26ae9666c9d50b937cf95882bc6fab9515d7241f1b35f63344c232c0657a4b`.

## Native navigation

On the **new** token of a supported constructor reference, Go to Declaration
opens the actual replacement markup slot. Navigation on the class qualifier
keeps IntelliJ's Java behavior. The core dialog slot can navigate back to its
registered factory through the same source context. Ordinary ID references also
consume the event graph, including the page's host ID.

Reverse discovery permits one additional indexed hop through a source host with
a registered component-factory field. This connects a dialog resource to a host
such as OatCrud and then its page registrations. It does not enumerate arbitrary
transitive helpers or runtime callers. Existing source-set, module and project
selection gates still apply.

Event contexts supply navigation only. Inspections, completion, paired rename,
quick fixes and the initial sidebar report retain their existing local snapshot.
No event-template origin gains editing authority.

## MCP decision and response contract

Extend the existing `wicket_get_page_structure` optional
`includeNavigationContexts: true` projection. No new tool, request parameter or
schema version is needed. Default responses and the primary `analysis` retain
their initial-phase meaning. Existing validation and component-context requests
also continue to select that initial graph; event-only paths are not accepted as
initial component paths.

`navigationContexts.contexts[]` can now contain `relationship: EVENT_TEMPLATE`.
Its optional `lifecycle` object records:

| Member | Meaning |
| --- | --- |
| `kind` | `SOURCE_DECLARED_REPLACEMENT_SITE`. |
| `phase`, `runtimePresence` | `EVENT_TEMPLATE`, `unknown`. |
| `contextId` | Snapshot-local source context identity; fetch again after edits. |
| `componentPath`, `factoryNodeId` | Declared host route and the editor node within this graph. |
| `componentClassName` | Constructor-reference target. |
| `factoryLocation` | Registered constructor reference. |
| `invocationLocation`, `eventLocation` | Source invocation and containing method. |
| `replacementLocation` | Source attachment call. |

The context's scopes/bindings use the existing exporter and component-context
projection. Event entries additionally include parent, ancestors and direct
children inline. `hierarchyResolved` refers to the declared template graph;
it does not establish initial/runtime presence. Template flags, inferred matching
confidence, incomplete scope and phase warnings remain explicit.

Optional `navigationContexts.lifecycleDiagnostics` reports source sites whose
replacement route could not be established. Discovery completeness is separate
from semantic completeness. Exhausted discovery is explicit and publishes no
partial event template. All tools remain read-only.

Source and resource dependencies join existing version snapshots. Unsaved helper
or markup edits invalidate old responses. The 30-second request deadline,
post-serialization freshness check and whole-response 512-KiB rejection remain.
The event analyzer permits 12,000 source/call steps, 64 dependency paths,
8 contexts and 12 forwarding/ancestry levels per analyzed source. Source elements
are cached within that read only. Existing navigation discovery retains its
256-reference/256-inheritor, 16-candidate, 256-binding and 512-source bounds,
including the additional indexed host hop.

## Verification and remaining scope

The read-only Oat corpus checks all 11 inferred bindings, the actual field route,
constructor-reference/core-slot navigation in both directions, and preserved
independent ProductFields bindings against Wicket 10.11.0 and 11.0.0. Existing
Home/Badge/Card/Mobile/Inbox results and all 36 conventional page resources remain
covered.

An independent Wicket 11 WicketTester process verifies an initial placeholder,
two edits with different models, replacement of the previous panel, and removal
on close. PSI/MCP tests cover incorrect IDs, overwritten factories, foreign
receivers, host reassignment, competing parents/consumers, source mutations,
markup uncertainty, dispatch, cancellation, bounds, default response compatibility,
read-only behavior, unsaved freshness, module isolation and the response limit.
The actual bundled MCP host covers the optional event projection over HTTP.

General save/delete behavior, table/row details, wizard/tree protocols, form
conversion/validation and property/localization-aware refactoring remain later
work. See the [evidence summary](wicket-oat-editor-evidence.json) for final counts
and development ZIP hashes. No live IDE installation or publication is implied.


[Context-aware completion](context-completion.md) now reuses supported graphs from
this milestone for parent-scoped Java/HTML ID suggestions. Its separate editor
probe does not change the normal inspection, fix, paired-rename or MCP contracts.
Inferred candidates remain labelled with their source context and uncertainty.
