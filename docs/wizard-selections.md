# Registered wizard selections

The eighth development milestone adds separate source-declared selection templates
for `OrderWizardPage`. All three use **wizard:form:body**, with different fragment
definitions and independent child scopes:

| Registration index | Title | Fragment definition | Direct fields | Inferred bindings |
| --- | --- | --- | --- | --- |
| 0 | Customer | customerStep | customer, notify, delivery | 6 |
| 1 | Items | itemsStep | quantity, unitPrice | 5 |
| 2 | Review | reviewStep | review | 4 |

Each count includes the wizard, form and body placement components. The primary
initial report remains unchanged. These are **SELECTION_TEMPLATE** contexts with
unknown runtime presence; registration index does not identify the selected step.
The initialization call is recorded as source provenance, without claiming that
initialization succeeds or that any particular body currently exists.

## Source contract

The shared replacement-site analyzer recognizes a directly allocated and attached
source component with registrations in the page constructor. A source registration
method appends a fresh record to a private final list initialized with an empty
ArrayList and returns its own receiver. A same-receiver overload may forward the
factory parameter to that method. The record's ordinary canonical constructor and
factory accessor must preserve the one-argument component factory. Explicit custom
record constructors/accessors are withheld. Runtime registration overrides,
conditional registrations, unknown factory registrations, list escapes, reordering,
and competing body destinations withhold the declaration pattern.

A unique direct private source selector retrieves a record using an opaque private
integer field and invokes its registered functional-interface method with a known
String ID. The produced local must flow to a unique owned `addOrReplace` site.
Constructor skeletons establish the literal placement ancestry. A source
`onInitialize` call to the selector is recorded when uniquely available. Neither
the index's runtime value nor next/back/finish conditions are evaluated.

Each supported factory is a block lambda returning a fresh framework Fragment
local. It forwards the supplied attachment ID, uses a literal definition ID and
the page as markup provider, and directly adds fresh children with literal IDs.
Source fluent configuration must return the same receiver; a small existing
framework configuration contract is also recognized. Fragment/ID reassignment,
escapes, conditional additions, duplicate child IDs and known structural effects
outside the fragment are rejected.

This is a source declaration projection. It deliberately does not expand child
constructors/configuration effects, private form inputs, conversion, models,
validation, arbitrary helper/service/behavior effects, or subsequent tree changes.
Every projected binding is an editing-protected template and every matched binding
is **INFERRED**. Explicit node diagnostics and incomplete scope retain those limits.
No application class-name allowlist is used.

The implementation reuses constructor identity tracing, source constants, placement
skeletons, replacement flow, companion lookup, the Fragment analyzer and the graph
matcher. It does not add a separate matcher for steps. Existing Products editor,
Inbox, owned form-field and ordinary layout behavior remains covered.

## Navigation and MCP decision

Go to Declaration on a registered factory's **lambda arrow** opens the core body
markup slot. That slot can navigate back to all discovered registered factories.
Ordinary child IDs navigate to their own fragment definition. Existing discovery,
project/module/source-set selection and freshness checks apply. These extra graphs
supply navigation; initial sidebar, inspections, completion and paired rename keep
their existing snapshots and eligibility rules.

Extend the existing optional `wicket_get_page_structure` projection with
`includeNavigationContexts: true`. No new tool, request parameter or schema version
is needed. Native navigation and MCP consume the same semantic value graphs.
Selection contexts use `relationship: SELECTION_TEMPLATE` and lifecycle metadata:

| Member | Meaning |
| --- | --- |
| `kind` | `SOURCE_DECLARED_STEP_SELECTION` |
| `phase`, `runtimePresence` | `SELECTION_TEMPLATE`, `unknown` |
| `contextId`, `componentPath`, `factoryNodeId` | Snapshot-local identity, declared body route and selected fragment node |
| `factoryLocation` | Registered block lambda |
| `invocationLocation`, `eventLocation`, `replacementLocation` | Factory invocation, selector method and attachment call |
| `componentClassName` | Framework Fragment for this supported pattern |
| `selection.registrationIndex`, `registrationCount` | Zero-based source registration order and known registration count |
| `selection.title`, `definitionId` | Literal title when available, and fragment definition ID |
| `selection.indexLocation` | Source reference to the opaque controller index |
| `selection.initializationLocation` | Optional source initialization call to the selector |

There is no current-index field. Scope/binding projections include parent,
ancestors and direct children inline. `hierarchyResolved` describes the template's
source route, with no initial/runtime-presence guarantee. Semantic withholding is
reported through `lifecycleDiagnostics`; bounded discovery completeness remains
separate from semantic certainty.

Default structure output, validation and ordinary component-context selection
retain the initial graph. A selection-only path cannot select that graph. All
tools remain read-only. Unsaved source/resource edits invalidate snapshots. The
existing 30-second request deadline and 512-KiB whole-response limit apply, along
with the shared replacement analyzer's 12,000-step, 64-source, 8-context and 12-level
limits. Context-limit exhaustion publishes no partial registration set.

## Verification and remaining scope

Read-only corpus checks against Wicket 10.11.0 and 11.0.0 assert the three distinct
Oat scopes and all 15 inferred bindings, bidirectional native navigation and shared
MCP output, while preserving previous milestones and all 36 page companions.
An independent Wicket 11 WicketTester process checks initial selection, validation
failure, successful advancement, revisiting with a fresh fragment and finish-hook
outcomes. Those runtime assertions validate the fixture; analysis does not predict
the same outcomes for arbitrary applications.

Regression tests cover source order, unknown/conditional registrations, runtime
overrides, escapes/reassignment, competing destinations, resources, cancellation,
bounds, module isolation, unsaved freshness, unchanged primary responses, read-only
use and actual bundled MCP-host transport. See the
[evidence summary](wicket-oat-wizard-evidence.json) for final verification and ZIP
hashes. Tree populations, broader wizard protocols, CRUD callbacks, form internals
and shared/property-aware refactoring remain later work. This milestone does not
install the development plugin in the running IDE or publish it.


[Context-aware completion](context-completion.md) now reuses supported graphs from
this milestone for parent-scoped Java/HTML ID suggestions. Its separate editor
probe does not change the normal inspection, fix, paired-rename or MCP contracts.
Inferred candidates remain labelled with their source context and uncertainty.

## Guarded next and back edges

A reviewed selector can now carry `NEXT` and `BACK` transitions in its selection
metadata. Each edge records the target registration index and the handler and
guard source locations. The initial index must be in bounds, and writes
must come from reviewed guarded updates that call the same selector. An
unreviewed write withholds the transition edges while retaining the independent
selection templates. Current step and runtime action execution remain unknown.
