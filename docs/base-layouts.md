# Direct abstract base-layout analysis

The twelfth development milestone makes opening `OatAppLayout.java` or its HTML
companion useful without selecting a concrete application page. The primary text
report now contains **8 MATCHED / INFERRED** shell bindings and **9
UNRESOLVED_PARENT** sidebar entries:

| Base-relative path | Declared component |
| --- | --- |
| `html` | OatHtml transparent container |
| `appTitle` | Label |
| `topNav` | WebMarkupContainer |
| `topNav:appName` | Label |
| `topNav:topNavExtra` | Default hidden WebMarkupContainer |
| `sidebar` | WebMarkupContainer |
| `sidebar:menuItems` | ListView attachment; item contents remain opaque |
| `sidebar:footer` | Default hidden WebMarkupContainer |

The text header explicitly identifies a **base-layout declaration template** and
reports three agreeing constructor entries and one open extension slot. Match rows
include `MATCHED / INFERRED`. The former constructor/scope/head cascade is replaced
by the declared shell and localized uncertainty about the extension and menu.

## Constructor and dispatch contract

This is a declaration graph under the selected abstract base class's own source
implementations. It is not an instantiated abstract page or a prediction of its
runtime subclasses. All MATCHED findings are INFERRED; nodes have `template: true`
and `idRenameSafe: false`. Runtime presence, constructor selection, subclass
overrides, model values and extension contents remain unknown.

Eligibility requires a source abstract WebPage and exactly one conventional,
active, empty `wicket:child` slot outside a header. The resource may use the selected
markup suffix. Ordinary concrete page analysis retains its previous constructor
selection and actual source override dispatch.

Every declared constructor entry is analyzed separately with the existing audited
source layout engine. An implicit default constructor counts as one entry. The
reviewed entry arguments are absent arguments, PageParameters and IModel, treated
as opaque data. Source `this`/`super` delegation and exactly-once field setup retain
their existing contracts. Dereferencing an opaque entry input or passing it to
data helpers is withheld; its runtime callbacks are not executed or certified.
The existing engine continues to audit helpers, model/resource construction,
source behaviors, known mutations, component escapes and deferred captures.

A template is published only when every entry yields the same unique component
paths, component types and scope contracts. Configuration/model values are not
claimed equal. Different graphs, unavailable abstract hooks, unknown setup effects,
unsafe constructors, duplicate paths and unsupported forwarding withhold the whole
template. This slice does not select the first overload, union incompatible trees,
or publish partial constructor results. Constructor-specific contexts for
nonagreeing graphs remain future work.

Reconciliation preserves every distinct ID/allocation origin. For `html`, the
no-argument constructor delegates to the PageParameters route, while the IModel
route has a second allocation. Both literals navigate to the same markup slot;
the slot navigates back to both. No new origin gains paired rename authority.
The text tree uses a representative allocation; JSON includes alternative source
locations and constructor-entry provenance.

The engine is reused rather than duplicating Java parsing or effect summaries.
Default calls to that engine keep their existing behavior. Reconciliation permits
at most **4 constructors, 20,000 shared steps, 64 consulted sources and 128 nodes
per entry graph**. The engine's existing per-entry limits still apply. Cancellation
propagates and exhaustion publishes no base-layout template. Consulted sources,
including failed constructor/helper attempts and markup, join freshness tracking.

## Open inheritance slots and transparent markup

A validated open child slot is an explicit extension boundary. Its diagnostic stays
on the slot, rather than making every ancestor and sibling unresolved. The markup
and overall report remain incomplete. Multiple slots, slot content/attributes,
header slots, malformed syntax and ambiguous resources retain conservative
handling. The default inheritance analyzer still rejects unfilled slots outside
this explicit mode.

Transparent HTML/head resolution uses the existing allocation-driven planner and
re-extraction. Header guards are not removed for arbitrary components or custom
resolvers. Known shell bindings can match while sidebar item scopes and extension
contents remain unknown. Missing counterparts in this incomplete graph stay
unresolved, without automatic counterpart fixes. Concrete subclass markup continues
to fill the slot through ordinary inheritance analysis.

## MCP decision

Extend the existing structure, validation and component-context tools using the
same primary PageReport and immutable snapshots as the sidebar and native
references. No new tool, request parameter or schema version is needed. Unlike the
optional lifecycle contexts in previous milestones, this feature improves the
primary report for eligible abstract layouts.

The additive `baseLayoutTemplate` object appears in `analysis` for structure,
`validation` for validation, and `context` for component selection. Optional
navigation-context projections preserve it too:

| Member | Meaning |
| --- | --- |
| `kind`, `phase` | `SOURCE_DECLARED_BASE_LAYOUT`, `BASE_LAYOUT_TEMPLATE` |
| `pathScope` | `BASE_RELATIVE` |
| `constructorAgreement` | `ALL_REVIEWED_ENTRIES`; attachment/type/scope graph agreement |
| `dispatch` | `DECLARED_IMPLEMENTATIONS`; runtime subclasses are not selected |
| `constructors[]` | Parameter signature and source location; implicit entry uses its class declaration |
| `extensionSlots[]` | Open extension locations |
| `runtimePresence`, `runtimeConstructor`, `subclassOverrides`, `extensionContents`, `modelValues` | Always `unknown` |

Base-relative selectors such as `topNav:appName` select the declared shell.
`hierarchyResolved` describes that graph, not runtime presence or subclass
behavior. Validation reports the same inferred findings and uncertainty. Ordinary
concrete-page responses retain their meaning. The tools remain read-only and use
existing project/module/source-set isolation, smart committed PSI, freshness,
cancellation, 30-second deadlines and 512-KiB whole-response rejection.

## Acceptance and remaining work

The read-only Oat corpus checks the eight shell matches, nine unresolved menu
entries, three constructor entries, local extension boundary, transparent head,
source-origin navigation and shared MCP facts against Wicket 10.11.0 and 11.0.0.
Previous concrete-page, fragment, form, editor, wizard and tree acceptance remains
covered. All 318 application Java/HTML inputs retain their hashes.

A separate Wicket 11 WicketTester process renders concrete extensions through all
three constructor routes. It checks the common shell, extension contents and the
distinction between a hidden component's tree presence and rendered visibility.
These fixture outcomes are not exported as runtime guarantees for Oat.

Tests cover constructor disagreement/unsafe effects, opaque model callbacks,
unavailable hooks, malformed slots, unchanged concrete overrides, bounds,
cancellation, module isolation, unsaved constructor/helper/markup edits, protected
rename, incomplete-graph fixes, all three MCP views and the actual bundled host.
See the [evidence summary](wicket-oat-base-layout-evidence.json) for final receipts.

Conditional sidebar group/link populations, concrete base classes with open slots,
nonagreeing constructor projections, broader input parameter contracts, runtime
subclass/model interpretation and shared/property-aware rename remain later work.
This build does not imply live IDE installation or publication.


The [thirteenth milestone](list-populations.md) adds conditional leaf/group source
contexts for the previously unresolved sidebar. The shell still has eight inferred
matches; the nine sidebar slots become UNRESOLVED_BRANCH with separate 4/7-binding
item projections. This does not select actual runtime rows or branch state.
