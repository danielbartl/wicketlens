# Conditional ListView population templates

The thirteenth development milestone reconstructs `OatAppLayout`'s ordinary-link
and grouped-menu rows, including shared link/label/badge helpers and nested
`groupItems`. The primary report retains **8 MATCHED / INFERRED** shell bindings.
The nine sidebar slots now have **UNRESOLVED_BRANCH**, with counterparts in
separate source item contexts rather than an invented unconditional row graph.
The text report appends both contexts:

| Row precondition | New item bindings | Declared contents |
| --- | --- | --- |
| `mi.isGroup() = false` | 4 MATCHED / INFERRED | `link`, `link:label`, `link:menuBadge`, hidden `menuGroup` placeholder |
| `mi.isGroup() = true` | 7 MATCHED / INFERRED | hidden `link` placeholder, `menuGroup`, its label and `groupItems`, then a nested link with label/badge |

Together the contexts cover all **nine distinct sidebar markup locations**. They
have eleven matches because the outer `link` and `menuGroup` slots have different
roles in the two row shapes. A real list can contain both leaf and group rows at
once. A predicate precondition applies to one symbolic item at that source call;
it is not a global page choice or the current model's value.

## Shared source contract

Population discovery runs after ordinary pairing, inheritance, transparent lookup,
fragment composition and matching. It requires a matched source anonymous ListView
attachment whose item scope is not already reconstructed. Existing ordinary
repeater/factory templates keep their previous analysis. Audited source anonymous
ListView attachments can now enable the shared layout setup pass without requiring
an unrelated decorated component. Unsafe root setup, escapes and captured
structural mutations retain that pass's existing guards.

The population analyzer adds bounded declaration rules for direct callback locals,
fresh constructor allocations, direct component additions to their own item/fresh
parents, private source setup helpers and source component factories on the
selected page hierarchy. Component IDs reuse the shared source declarations,
constants and constructor identity tracer. Factories must return fresh unattached
components and preserve their supplied String ID origin; an equal hardcoded
literal is not forwarding. Borrowed fields, foreign receivers, reassignment,
repeated attachments, recursive helpers, unknown parent aliases, unsupported loops
and exception flow withhold that host's contexts.

`item.getModelObject()` can supply an opaque local data identity. A structural
branch may test a direct zero-argument primitive-boolean predicate on that data.
The analyzer enumerates bounded source branches without invoking the predicate or
reading a model. Predicate source/declaration locations and required results are
retained as preconditions for the corresponding symbolic item. Source override
dispatch uses the selected concrete page; abstract base-layout contexts keep their
explicit declared-implementation assumptions.

Nested anonymous ListViews recursively declare one symbolic ListItem each. Item
nodes have `templateItem: true` and a null ID; logical paths omit the unknown
runtime index. For example, a nested badge uses
`sidebar:menuItems:menuGroup:groupItems:link:menuBadge`. This does not identify a
particular runtime row or establish its count or ancestry through concrete IDs.

Configuration-only branches preserve source locations without selecting or
executing their conditions. Authorization, selection styling, badge visibility,
models, behavior binding and later callbacks are not evaluated. Known structural
calls in those branches are not treated as configuration. An empty, plain framework
WebMarkupContainer declared with unconditional `setVisible(false)` can serve as a
placeholder: that source branch projects its HTML preview body inactive. This
rule does not suppress a hidden row's declared children, anonymous container hooks,
unknown visibility changes or containers with attached children. Component-owned
markup remains a separate scope.

Each context reuses the existing graph matcher. It retains exact item findings,
necessary Java ancestors and relevant markup/physical ancestors, instead of
repeating unrelated page bindings. Projection does not invent new matches or
remove source uncertainty. All context bindings are INFERRED and template origins
remain protected from paired rename. The primary ordinary bindings retain their
confidence; only template-covered unresolved markup changes to UNRESOLVED_BRANCH.

## Native reports and navigation

The text report shows population count, item preconditions, inferred rows, nested
symbolic item scopes and source links. Its ordinary summary remains separate from
the conditional contexts. Missing/unproved slots remain explicit.

ID references use the same value graphs. The shared `menuBadge` and link-label
helper declarations navigate to both outer and nested markup uses; each use
navigates back to the corresponding source. Private helper call ID origins and
concrete source factory overrides retain their actual provenance. Owned population
contexts remain available for navigation even when bounded indexed caller or
inheritor discovery is exhausted. They supply no editing authority, counterpart
fixes or runtime-state guarantees.

## MCP decision and response contract

Extend the existing structure, validation and component-context tools over the
shared primary PageReport. No new tool, request parameter or schema version.
All tools remain read-only.

Structure `analysis.populationTemplates[]` contains the exact scoped graph exports.
Validation `populationTemplates[]` reuses those graphs' findings and counts.
Component-context output includes the same source graphs alongside the ordinary
selected component. Template-only children do not become selectable through the
primary graph's path selector. Optional `includeNavigationContexts` can project the
same rows as `relationship: REPEATER_TEMPLATE`, with inline ancestry/children and
only the new item binding IDs selected.

Each context has an additive `populationTemplate` object:

| Member | Meaning |
| --- | --- |
| `kind`, `phase` | `SOURCE_DECLARED_LIST_POPULATION`, `ITEM_POPULATION_TEMPLATE` |
| `pathScope` | `SYMBOLIC_ITEMS`; class-relative logical paths omit item IDs |
| `dispatch` | `DECLARED_IMPLEMENTATIONS` for a base-layout template, otherwise `SELECTED_SOURCE_CLASS` |
| `contextId`, `hostNodeId`, `hostComponentPath`, `itemNodeId`, `ownedNodeIds[]` | Snapshot-local graph identities and host attachment |
| `hostLocation`, `callbackLocation`, `callbackLocations[]` | Source allocation and outer/nested population callbacks |
| `conditions[]` | Source expression/location, declared predicate method, variable/declaration, symbolic item and `requiredResult` |
| `configurationLocations[]` | Opaque source configuration declarations |
| `inactiveBodyLocations[]` | Placeholder/component-owned preview bodies projected in this source context |
| `runtimePresence`, `runtimeItemIds`, `rowCount`, `modelValues`, `predicateResults`, `authorization`, `visibility`, `runtimeSubclass` | Always `unknown` |
| `configurationExecution`, `callbackExecution` | Always `not_evaluated` |

Discovery permits **20,000 shared steps, 96 sources, 4 contexts, 128 nodes per full
context, 12 helper/statement levels, 3 nested repeaters and two branching decisions**.
Contexts from one host are withheld together if any source branch is unsupported.
Budget exhaustion publishes no population graphs, sets
`populationDiscoveryComplete: false`, records a PARTIAL_ANALYSIS diagnostic and
marks structure/validation/context responses `truncated`. Completeness describes
eligible bounded source discovery, not full semantic or runtime knowledge.

The existing project/module/source-set gates, source dependency snapshots,
unsaved-edit invalidation, cancellation, 30-second request deadline and 512-KiB
whole-response rejection remain. Source predicates, helpers, selected overrides,
callbacks and markup join freshness tracking. Unrelated page bindings are kept out
of each item projection so ordinary report requests remain within response bounds.

## Acceptance and remaining work

The read-only Oat corpus checks both contexts, 4/7 inferred bindings, nine covered
locations, nested scopes, native helper references, text report sections, compact
MCP output and preserved previous ordinary matches against Wicket 10.11.0 and
11.0.0. Application inputs remain unchanged. An independent Wicket 11 WicketTester
process renders a mixed list with leaf and group rows simultaneously, including
nested labels/badges and empty hidden placeholders. These fixture outcomes do not
become runtime guarantees in analysis output.

Regression tests cover frame ownership, source ID forwarding, branch all-or-nothing
publication, concrete overrides, configuration/visibility distinctions, aliases,
escapes, recursive/unsupported flow, normal repeater preservation, bounds,
cancellation, freshness, module isolation, indexed discovery exhaustion, response
size, protected rename and all three views through the actual bundled MCP host.
See [the evidence summary](wicket-oat-population-evidence.json).

Named/custom compiled repeater factories, arbitrary predicates/compound conditions,
loops and exception protocols, shared mutable component storage, unrestricted
helper hierarchies, runtime data/authorization/visibility and shared/property-aware
rename remain outside this slice. The build is not installed or published
automatically.
