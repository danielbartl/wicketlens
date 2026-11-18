# Wicket Oat support investigation

WicketLens needs broader component identity, markup scope, and setup-effect
analysis to support wicket-oat. Conventional resource lookup already works.
Changing the application's resource layout would not address the failures.

Twenty-three milestones are implemented on the development branch: source layouts,
fragment projection, factories and repeated rows, named nested companions,
owned form summaries, ID origin auditing, registered editors, wizard selections,
node-relative tree templates, lazy composite form internals, registered custom
composite inputs, direct abstract base-layout shells and conditional list
populations, immediate table toolbar factories and lazy empty-state toolbar
content, registered list item/data callbacks, and source-declared initial
dialog/drawer content, row-triggered event content, context-aware ID completion
and separate dialog and ordinary form submission source templates, followed by
finite constructor data-loop isolation and constructor-owned initial dialog content. Broader CRUD, wizard, tree and
form lifecycle support remains partial.

The contracts describe [source layouts](source-layouts.md),
[fragments](fragments.md), [factories](factories.md),
[nested navigation](nested-navigation.md), [owned form fields](owned-form-fields.md),
[ID origin auditing](id-origin-audit.md), [replacement editors](replacement-editors.md),
[wizard selections](wizard-selections.md), [tree populations](tree-populations.md),
[lazy form inputs](form-inputs.md) and
[registered custom inputs](registered-form-inputs.md) and
[direct base layouts](base-layouts.md) and
[conditional list populations](list-populations.md) and
[immediate content factories](content-factories.md) and
[lazy table toolbars](table-toolbars.md) and
[registered list callbacks](registered-list-callbacks.md) and
[initial dialog content](initial-dialog-content.md) and
[row-triggered drawer content](event-dialog-content.md) and
[context-aware completion](context-completion.md) and
[dialog submission templates](submission-templates.md) and
[ordinary form submissions](form-submission-templates.md).

## Twenty-third milestone results

OatConfirmDialog's empty Model delegation and constructor-installed message Label
now participate in the existing initial-content declaration phase. A fixture-only
page using its real source supplies 24 inferred matches and one constructor
placement, shared by native and MCP views. Constructor/model/visibility/action
effects stay unknown. DataTablePage advances to OatSelectionColumn's unreviewed
AbstractColumn construction; CustomerPage advances to OatPageHeader identity.
The 36 primary binding/finding summaries, earlier templates, completion and orange
Project tree markers remain unchanged. See
[the constructor-owned content contract](constructor-owned-dialog-content.md).

## Twenty-second milestone results

DataTablePage's counted invoice-seeding loop is audited without execution, row
unrolling or known collection/model values. The first blocker moves from the
loop at offset 3304 to OatConfirmDialog construction at offset 3767. The original
unresolved primary findings and both four-binding toolbar templates are retained.
The passed-loop note and qualified constructor diagnostic reuse native/MCP
snapshots; complete new graphs still require every later setup effect to pass.
See [the constructor data-loop contract](constructor-data-loops.md).

## Twenty-first milestone results

InvoicePage has two source callback contexts for its OatSubmitButton onSubmit and
onError handlers. Each supplies two inferred own-companion bindings and declared
feedback plus an invoiceForm Ajax refresh. Its initial hierarchy remains
unresolved. The declared paths establish no runtime placement or business outcome.

Native callback/ID navigation and optional MCP SUBMISSION_TEMPLATE contexts share
the graph and formCallback attachment/constructor provenance. Source scope is
OWN_COMPANION_DECLARATIONS; validation/default processing, initialization,
reachability and later state remain unknown. See
[the contract](form-submission-templates.md) and
[twenty-first milestone evidence](wicket-oat-form-submission-evidence.json).

## Twentieth milestone results

DialogPage supplies separate onConfirm source contexts for deleteDialog (four
inferred bindings), editDialog (five, including the people Ajax refresh target)
and filtersDrawer (four). Source feedback calls do not establish save/delete
outcomes. Supported fresh label/fragment replacements are separate declaration
graphs; initial selectors and the 72-match/13-branch report stay unchanged.

Native hook/trigger and content navigation share optional MCP SUBMISSION_TEMPLATE
contexts, bridge provenance and declared effects. No callback is invoked.
Validation, default form processing, models, runtime reachability and later
open/close/tree state remain unknown. See [the contract](submission-templates.md)
and [twentieth milestone evidence](wicket-oat-submission-evidence.json).

## Nineteenth milestone results

Basic Completion now suggests counterpart IDs inside empty or partial Java/HTML
values using supported source/template contexts. The popup shows component types,
source context and inferred confidence. Shared physical uses intersect candidates,
and already-used siblings and unrelated scopes stay excluded. A separate editor
probe retains the focused empty ID without changing normal analysis or editing
authority.

Real Oat fixture-copy probes cover DialogPage's details list in both directions,
its initial name input in Java, AccordionPage's title in both directions and the
shared outer/nested sidebar menuBadge helper. Application inputs and existing
page/template summaries remain unchanged. No MCP surface change is needed:
existing tools expose the underlying facts, while editor recovery hypotheses stay
out of their snapshots. See [the contract](context-completion.md) and
[nineteenth milestone evidence](wicket-oat-completion-evidence.json).

## Eighteenth milestone results

DialogPage has a separate five-binding EVENT_TEMPLATE for the details link at
`people:rows:details`. It places a fresh personDetails Fragment and its list at
`detailsDrawer:dialog:form:content`, retaining required host ancestry. The primary
72-match/13-branch report and plain initial details placeholder remain unchanged.

Native references and optional MCP structure navigation contexts share the graph.
`lifecycle.kind=SOURCE_DECLARED_EVENT_CONTENT` and `directContent` preserve the
source transfer, guarded setter and separate symbolic row trigger. Callback and
configuration execution remain not evaluated; runtime row IDs/count, model values,
validation, visibility and later open/close state remain unknown. See
[the contract](event-dialog-content.md) and
[eighteenth milestone evidence](wicket-oat-event-content-evidence.json).

## Seventeenth milestone results

DialogPage now has 72 inferred matches, including four owned dialog/drawer hosts,
three initial content placements, editFields/name/email and
filterFields/status/overdueOnly. Thirteen primary branch findings retain separate
counterparts in the two sidebar templates and the four-binding people-row template.

The bounded fallback is explicitly INITIAL_DECLARATIONS. Constructor/configuration
and model effects, validation, visibility and later open/close/replacement state
remain unknown; callbacks are not executed. The personDetails fragment remains a
definition without an initial instance. All new bindings are protected from paired
rename. Native references and the existing MCP views share initialContentSetup
provenance. See [the contract](initial-dialog-content.md) and
[seventeenth milestone evidence](wicket-oat-initial-content-evidence.json).

## Sixteenth milestone results

AccordionPage now has 11 primary matches and a separate two-binding inferred
item template for title and content. Its source static factory forwards the ID
and callback through an overload into an exact populateItem bridge. A reviewed
newItem wrapper preserves the default item/index/model and adds only owned
behaviors. The previous opaque-acquisition failure is gone.

The primary 11 UNRESOLVED_BRANCH findings refer to nine sidebar slots and the
two accordion item slots. Their counterparts are available in three separate
population templates; they do not become unconditional page children. Native
host/child IDs and callback-arrow navigation share those graphs with existing
MCP structure, validation and component-context output. Callback execution,
behavior effects, runtime row count/IDs and model values remain unknown or not
evaluated. See [the contract](registered-list-callbacks.md) and
[sixteenth milestone evidence](wicket-oat-registered-list-evidence.json).

## Fifteenth milestone results

DataTablePage's empty-state registration now has four toolbar-relative inferred
bindings: cell, content, content:title and content:message. The source setter
invokes its factory immediately, allocates its private toolbar under a null guard
and adds/replaces content in the toolbar's owned cell. Native factory/cell/label
navigation and optional MCP TABLE_TOOLBAR_TEMPLATE contexts share that graph.

Wicket assigns the toolbar's runtime ID. Source configuration, model references
and lifecycle hooks retain locations; row count, visibility, colspan, actual
instances, model values and later state stay unknown. Factories and callbacks are
not evaluated. Primary reports/selectors and earlier templates retain their
existing behavior. See [the contract](table-toolbars.md) and
[fifteenth milestone evidence](wicket-oat-toolbar-evidence.json).

## Fourteenth milestone results

DataTablePage's search toolbar now has a separate four-binding source template:
dataTable, toolbar, searchForm and search. The reviewed setter invokes its unary
factory immediately and replaces the toolbar slot. Native factory/fragment/field
navigation and optional MCP FACTORY_TEMPLATE contexts reuse the same graph and
registration, ID, configuration and callback source locations.

All four bindings are inferred. The factory and Ajax callbacks are not evaluated;
configuration effects, runtime presence, model values, visibility and later tree
state remain unknown. Primary graphs and selectors, default MCP responses and
existing event/selection contexts remain unchanged. The lazy empty-state toolbar
route and table column/cell populations remain later work. See
[the contract](content-factories.md) and
[fourteenth milestone evidence](wicket-oat-content-factory-evidence.json).

## Thirteenth milestone results

OatAppLayout now exposes two source item contexts: leaf rows have four inferred
bindings and grouped rows seven, including nested groupItems and shared label/badge
helpers. They cover all nine sidebar markup slots. The primary report retains its
eight inferred shell matches and identifies the nine conditional counterparts as
UNRESOLVED_BRANCH. The text report appends both item contexts and preconditions.

Native references resolve the shared helper IDs to outer and nested markup uses.
The existing MCP structure/validation/component-context views expose the same
scoped populationTemplates; optional navigation discovery projects REPEATER_TEMPLATE
contexts. Runtime rows/IDs/counts, predicate results, authorization and visibility
remain unknown. See [the contract](list-populations.md) and
[thirteenth milestone evidence](wicket-oat-population-evidence.json).

## Twelfth milestone results

Opening OatAppLayout directly now yields **8 MATCHED / INFERRED** shell bindings
and **9 UNRESOLVED_PARENT** sidebar entries. Its three constructor entries agree
under declared base implementations; the unfilled child slot stays a local
extension boundary. The text report labels the template and its inference level.
Both constructor allocations of `html` navigate to their common slot and back.

The primary native and MCP structure/validation/component-context views share the
same graph and additive `baseLayoutTemplate` constructor/extension provenance.
Runtime constructor selection, overrides, extension contents and model values stay
unknown. Concrete pages retain their existing analysis. See
[the contract](base-layouts.md) and
[twelfth milestone evidence](wicket-oat-base-layout-evidence.json).

## Eleventh milestone results

InvoicePage's `OatCustomField("phone", PhoneInput::new)` now exposes six inferred
wrapper-relative bindings, including `input:code` and `input:number`. Constructor
registration and private final factory storage have source provenance. Input IDs
and the registration's `new` token navigate through the same shared graphs as
optional MCP FORM_TEMPLATE contexts. Initial analysis remains unchanged; current
presence, enclosing page paths and runtime conversion/validation results remain
unknown. See [the contract](registered-form-inputs.md) and
[eleventh milestone evidence](wicket-oat-registered-input-evidence.json).

## Tenth milestone results

OatDateRangeField now has **six inferred wrapper-relative bindings**: container,
label, feedback, input, input:from and input:to. Private final inputs created through
source helpers pair with their nested companion. Native field/factory navigation
and optional MCP **FORM_TEMPLATE** contexts share the same source graphs.

Conversion, required-check and validator source locations are recorded; runtime
presence, required state, model values and conversion/validation results remain
unknown. Initial analysis and the stricter existing owned-form effect proof are
unchanged. See [the contract](form-inputs.md) and
[tenth milestone evidence](wicket-oat-input-evidence.json).

## Ninth milestone results

AccountsPage's folders and accounts trees now have **four node-relative template
contexts**, with **eight inferred bindings each**. Null/non-null callback
preconditions keep the link/plain parent alternatives separate; icon factory
constructor choices remain explicit. Runtime node IDs, full page ancestry,
expansion, selection and model values remain unknown. Initial reports are unchanged.

Native ID and source factory navigation, including reverse dependency-slot
navigation, consume the same graphs as optional MCP `TREE_TEMPLATE` contexts.
No tool or request parameter is added. See [the contract](tree-populations.md) and
[ninth milestone evidence](wicket-oat-tree-evidence.json).

## Eighth milestone results

OrderWizardPage now has three separate source-declared selection contexts at
**wizard:form:body**: Customer (6 inferred bindings), Items (5), and Review (4).
Each preserves its registration index, title and fragment definition. The current
runtime step and validation outcomes remain unknown; primary initial analysis is
unchanged. Factory lambda arrows and the shared body slot navigate both ways.
The existing optional MCP structure projection exposes the same graphs with
`relationship: SELECTION_TEMPLATE` and additive `lifecycle.selection` provenance.
See [the contract](wizard-selections.md) and
[eighth milestone evidence](wicket-oat-wizard-evidence.json).

## Seventh milestone results

ProductsPage's `ProductFields::new` registration now has a separate source-declared
replacement-site context at **products:editor:dialog:form:content**. It contains
**11 inferred template bindings**, including the six fields. Their independent
ProductFields report retains six confirmed bindings; the primary ProductsPage
initial report is unchanged. The event's occurrence, model values, validation and
subsequent tree remain unknown.

Go to Declaration on the constructor reference's `new` token opens the core
OatDialog content slot; that slot navigates back through bounded source discovery.
The existing MCP structure tool exposes the same event graph with
`includeNavigationContexts: true`. Default structure, validation and component
context retain their initial-phase meaning. No writable tool is added.
See [the contract](replacement-editors.md) and
[seventh milestone evidence](wicket-oat-editor-evidence.json).

## Sixth milestone results

A bounded origin-use audit now permits paired rename for isolated identity-only
layout literals and isolated named-member companions when the existing editor
guards also hold. Every direct alias/parameter use must reach exactly one
reviewed Wicket ID slot. Data, resource-key, model and computation uses,
shared Fragment/projected children, and external member contexts remain guarded.
Native preview/undo and pre-write revalidation are retained. MCP reuses existing
origin flags; no writable tool or schema change is added.

The audit confirms a concrete Oat restriction: `BaseOatField` also uses the
constructor ID for a `StringResourceModel` key and fallback label. All six
ProductFields bindings remain confirmed and navigable, while paired rename stays
withheld. Full property/localization-aware refactoring needs its own contract.
See the [audit notes](id-origin-audit.md) and
[sixth milestone evidence](wicket-oat-origin-evidence.json).

## Fifth milestone results

`ProductsPage.ProductFields` now has **six confirmed bindings**: name, category,
price, stock, description and suppliers. Empty metadata tokens, bounded private
lazy-input caches and private input constructor references no longer invalidate
these wrapper attachments. Native references and existing MCP views agree.
Internal inputs, conversion, validation and runtime behavior remain explicitly
unexpanded. ProductsPage itself remains an unproved discovery candidate;
arbitrary CRUD/editor constructor-reference placement is not inferred.

Both Wicket 10.11.0 and 11.0.0 corpus checks preserve all previous target results,
including Inbox's 26 confirmed and 5 inferred bindings. All 36 page sources still
pair with resources. A separate WicketTester fixture checks lazy identity and
composite-input ownership. Tests cover source effects, module isolation, unsaved
freshness, cancellation, bounds and read-only use. See the
[form contract](owned-form-fields.md) and
[fifth milestone evidence](wicket-oat-form-evidence.json).

## Fourth milestone results

`ProductsPage.ProductFields` pairs with `ProductsPage$ProductFields.html` and
can be selected independently through Java, HTML and MCP. At this milestone its
six field bindings remained unresolved at the `BaseOatField` static metadata/
lazy-input boundary; the fifth milestone establishes them. Pairing does not
expand the Products CRUD graph or its constructor reference. ProductsPage remains an explicit unproved discovery candidate.

Native references now consult bounded caller/inheritor reports using the same
binding analyzer. The core `OatSplitLayout.html` resource's `second` ID can
navigate to Inbox's actual captured Fragment origin. The existing structure MCP
tool can expose the same contexts with optional `includeNavigationContexts: true`;
default responses remain unchanged. Distinct source origins in shared nested and
inherited Fragment resources get multiple native navigation targets.

All previous target counts are preserved, including Inbox's 26 confirmed and 5
inferred bindings. Both Wicket corpus versions still pair all 36 page sources.
The independent WicketTester oracle verifies binary resource names for static
and deep members; focused tests cover module/source-set isolation, variants,
freshness, cancellation, bounds, read-only use and editing guards. See the
[navigation contract](nested-navigation.md) and
[fourth milestone evidence](wicket-oat-navigation-evidence.json).

## Third milestone results

Inbox has **31 matched bindings: 26 confirmed and 5 inferred template bindings**
with both Wicket 10.11.0 and 11.0.0 fixture libraries. Captured split-pane
factories preserve `inbox:firstPane:first` and `inbox:second`; the detail Fragment
and source-owned split/list resources compose into their actual instance scopes.
Its row method reference produces a symbolic Fragment under both generated
repeater levels. The graph preserves those logical scopes without concrete row
IDs, counts or model values. Nine inherited sidebar findings remain unresolved.

All 36 page sources still pair with their conventional resources. Previous target
counts remain HomePage 14, BadgePage 17, CardPage 16 and MobileCapacityPage 4.
Read-only corpus assertions cover logical paths, confidence, resource dependencies,
native page-owned/definition references and existing MCP views. A separate Wicket
11 WicketTester oracle checks captured panes and nested generated rows.

ProductsPage, OrderWizardPage and AccountsPage were inspected and remain at zero
matched bindings: constructor references, nested companions and custom lifecycle
protocols need further work. Dialog and Tabs retain definition navigation from
the second milestone; their graph placement remains unresolved. This milestone
does not claim complete project coverage. See the [factory contract](factories.md)
and [third milestone evidence](wicket-oat-factory-evidence.json).

## Second milestone results

Fragment definitions are separate from attachment IDs and instance contexts.
BasePage's `topBar` now projects its `theme` and `user` children into the proved
`topNav:topNavExtra` instance. Confirmed bindings including inherited layout rise
to **HomePage 14, BadgePage 17, CardPage 16**; MobileCapacityPage remains **4**.
The same assertions pass on Wicket 10.11.0 and 11.0.0 fixture libraries.

Inbox's `detailFragment` and `rowFragment`, Dialog's `editFields`, `filterFields`
and `personDetails`, and Tabs' `overviewTab`, `activityTab` and `settingsTab` all
resolve to their definition declarations with bidirectional native navigation.
At this milestone these three pages had **zero confirmed component bindings**;
the following factory milestone establishes Inbox placement. No callback or
runtime row was invented to make a definition reference appear attached.

Repeated definitions get distinct projected identities and original source
locations. Shared helper constructions retain each evaluated definition ID and
caller origin. A separate WicketTester oracle confirms inherited definition reuse
with independent child values and discarded previews. See the
[fragment contract](fragments.md) and
[second milestone evidence](wicket-oat-fragment-evidence.json).

## First milestone results

The opt-in read-only application corpus verifies required page IDs, bidirectional
native references, and agreement with the existing structure, validation and
component-context MCP tools. The same assertions pass with Wicket 10.11.0 and
11.0.0 libraries on the pinned IDEA 2026.2.3 fixture platform.

| Target | Confirmed bindings, including inherited layout |
| --- | ---: |
| HomePage | 12 |
| BadgePage | 15 |
| CardPage | 14 |
| MobileCapacityPage | 4 |

All 36 page sources still pair with their conventional resource markup; six have
at least one confirmed binding. Shared desktop fragments and sidebar item scopes
remain unresolved even on target pages. Constructor identity, attachment and
owned-content completeness are separate facts. The new pass enables navigation;
paired rename is withheld pending an origin-use audit.

The portable runner and commands are documented in
[source layouts](source-layouts.md#verification-and-application-corpus). It hashes
the original Java/HTML before and after analysis and uses disposable copies in
one fixture module. Separate module-isolation tests cover conflicting classes.
The [milestone evidence summary](wicket-oat-milestone-evidence.json) records input
and corpus report hashes, findings and verification results. These results do
not establish full project support, a running-server test, a
Wicket upgrade, or live IDEA 2026.3 installation. The original baseline below is
retained for comparison.

## Measured baseline

The investigation on 2026-11-11 used application commit
`76562314a52eb37b517321632cfb6ef2f5187781`, Wicket **10.11.0**, and WicketLens
0.1.0 source. Production Java and HTML from the core and examples modules were
copied into disposable IntelliJ PSI fixtures. The original application was only
read. [The evidence](wicket-oat-support-evidence.json) records per-page counts,
constructor types, diagnostic categories, and the WicketLens commit.

| Observation | Result |
| --- | --- |
| Example page source files analyzed | 36, including the two shared base classes |
| Conventional markup pairing | 36 of 36 |
| Pages with any `MATCHED` finding | 0 |
| Pages affected by the component-owned HTML head diagnostic | 36 |
| Resolved custom component constructor calls in those Java files | 166 across 83 types |
| Custom constructor calls with a traced ID | 0 |

The Inbox fixture reproduced the supplied report's summary exactly:
4 `UNRESOLVED_CONSTRUCTOR`, 35 `UNRESOLVED_MARKUP`, 7 `UNRESOLVED_SCOPE`,
and 2 `UNRESOLVED_VARIABLE`. A separate source inventory found custom Oat
component construction in 26 page files, fragment construction in 11, and lambdas
in 25. Example markup contains 21 fragment definitions and a nested-class
companion, `ProductsPage$ProductFields.html`.

The probe passed on the repository's IDEA 2026.2.3 test platform using locally
available Wicket 10.11.0 libraries. Both application source-root sets shared a
fixture module. This establishes semantic coverage failures, rather than exact
Maven import fidelity or equivalence with the running IDEA 2026.3 instance. It
does not measure runtime accuracy or editor latency. The disposable harness and
full output remain in `/tmp/WicketOatInvestigationTest.kt`,
`/tmp/wicketlens-oat-investigation.json`, and
`/tmp/wicketlens-oat-investigation-run.log` for this session; the harness was
removed from the repository after the probe.

## Why the baseline analysis failed

### Transparent markup and document uncertainty

`OatAppLayout` and `OatMobileLayout` add `OatHtml("html")` for an
`<html wicket:id="html">` element. `OatHtml` extends
`TransparentWebMarkupContainer`: children in that markup can resolve against
the container's parent. The Wicket 10.11.0 implementation first checks ordinary
children and falls back to parent resolution. Transparent markup therefore
needs a resolution contract; deleting every transparent segment from every
path would also be incorrect. The
[framework API](https://nightlies.apache.org/wicket/apidocs/10.x/org.apache.wicket.core/org/apache/wicket/markup/html/TransparentWebMarkupContainer.html)
identifies this component as a resolver.

`WicketMarkupAnalyzer` currently rejects a Page head beneath any `wicket:id`
ancestor. It puts that diagnostic in `documentDiagnostics`; extraction and
`WicketMarkupInheritanceAnalyzer.normalize` then mark the entire merged document
uncertain. Both layouts trigger this check. Simply removing the head check
would leave the ordinary nearest-ID parent calculation wrong for `OatHtml`.

`wicket:message`, its attribute form, fragments, and `wicket:link` also encounter
generic unsupported-tag handling. The plain message and attribute-localization
uses in the layouts need reviewed handling. Messages with embedded components
need explicit parent resolution, as illustrated by the
[Wicket message contract](https://nightlies.apache.org/wicket/apidocs/10.x/org/apache/wicket/markup/resolver/WicketMessageResolver.html).

### Constructor identity depends on too much unrelated code

`WicketIdConstructors.origin` accepts a small framework constructor set and
restricted wrappers. Ordinary wrappers are rejected for instance methods or
additional constructor statements; Panels pass a restrictive whole-class setup
audit. Delegation arguments are generally limited to literals and parameter
references.

That excludes ordinary cases such as `OatCard`, whose constructor forwards
`id` and adds a behavior, and `OatBadge`, which delegates through `Model.of`
and exposes fluent configuration. `OatIcon` additionally reaches `WebComponent`,
which is outside the reviewed constructor set. `Fragment` and
`TransparentWebMarkupContainer` also need explicit framework contracts.

The ID can be provable while a component's child graph remains incomplete.
Those proofs need to be independent. Argument evaluation order and parameter
mutation checks must still protect ID provenance.

### Source setup expansion has several independent rejection points

`WicketSourceSetupAnalyzer.analyze` catches an unsupported operation and returns
`null`, discarding its whole expansion. `WicketJavaAnalyzer` then falls back to
direct discovery and reports generic inherited-setup gaps. The actual rejected
source operation is lost.

The shared layouts and pages exercise these restrictions:

- Static resource objects such as `BasePage.EXAMPLES_CSS`, and data arrays such
  as `InboxPage.SENDERS`, fail the static-initialization preflight.
- The no-argument layout constructors use `this((PageParameters) null)`;
  source setup currently rejects `this(...)` delegation.
- Layout fields such as `sidebar`, `topNav`, and `bottomNav` are mutable
  references assigned during initialization, outside the private-final contract.
- Overridable hooks such as `newTopNavExtra`, `newSidebarFooter`, and
  `newBottomNav` need dispatch in the selected concrete-page context.
- Behavior additions, most configuration methods, fluent returns, opaque model
  data, and ordinary anonymous component callbacks are outside the source
  interpreter's current contracts.
- Unexecuted public/protected methods and field reads from deferred callbacks
  can reject the expansion even when they do not change initial attachments.

The fallback then reports calls such as `success`, callback captures, and markup
provider references as possible root effects. A model getter and a hierarchy
mutation need different effect summaries. Arbitrary root escapes must continue
to invalidate every path they could change.

### Fragments and callback templates need explicit identities

Inbox creates `Fragment("second", "detailFragment", this)`. Here `second`
is the attachment ID; `detailFragment` names a markup definition; `this` is
the provider. They are three different roles. Fragment definitions do not form
ordinary page children at their declaration positions. See the
[official fragment example](https://nightlies.apache.org/wicket/guide/10.x/single.html#_working_with_markup_fragments).

`OatSplitLayout` invokes factories with `first` and `second`, placing the first
inside `firstPane`. Inbox's `this::row` reaches `OatLoadMoreList`, whose batch
and item wrappers use generated repeater IDs. Supporting those children requires
tracking callback parameters, captured component identity, returned components,
and the intervening component scopes. It does not require predicting mail data
or row counts.

## Implementation order and remaining work

| Slice | Implementation | Initial acceptance cases |
| --- | --- | --- |
| 1. Explain failures and retain independent facts | Preserve the rejected operation, source, reason, and affected receiver/phase. Separate ID provenance, attachment, child-scope completeness, and effect uncertainty internally. Retain facts only when unknown effects cannot invalidate them. | A rejected nested component exposes its cause; a proved nonstructural operation does not erase a sibling match; an unknown root mutation still blocks affected page paths. |
| 2. Broaden component identity and configuration contracts | Trace source constructor delegation independently of full child reconstruction. Add reviewed `WebComponent`, transparent-container, and Fragment ID semantics. Summarize fluent receiver returns, attribute/model configuration, and behavior attachment, including synchronous `bind` effects. | `OatCard("simpleCard")`, every `OatBadge` overload used by BadgePage, `OatIcon`, and `OatCookieConsent` retain their IDs; malicious or ambiguous ID forwarding stays unresolved. |
| 3. Resolve layout markup scopes | Resolve transparent containers against Java type/instance facts, with ordinary-child precedence, parent fallback, ambiguity checks, and header support. Model the layout's message forms and localize unsupported markup uncertainty. Use bounded resolution passes where markup and Java facts depend on each other. | `OatHtml` remains a real component while page siblings resolve beneath its tag; ordinary containers remain boundaries; nested or ambiguous transparent resolvers are conservative; localization does not invalidate sibling IDs. |
| 4. Expand ordinary page setup | Follow `this(...)` chains with exactly-once field initialization. Track bounded component aliases and fields through constructor/onInitialize phases. Summarize opaque data/static-resource initialization effects without computing application values. Resolve reviewed template hooks in the selected concrete-page context, making context assumptions explicit. | Desktop and mobile layout setup, HomePage, BadgePage, and CardPage obtain useful confirmed bindings. Reassignment, unsafe dispatch, structural behaviors, and relevant callback mutations retain uncertainty. |
| 5. Bind fragment definitions to instances | Record definition ID, provider, attachment ID, instance, and original source locations separately. Project definition children into the chosen instance scope. Support inherited definitions and repeated use without inventing a Java component for the definition itself. | BasePage's `topBar`, Inbox's `detailFragment` and `rowFragment`, and representative Dialog/Tabs fragments navigate to their actual definitions. Reused definitions produce multiple contexts and guarded editing. |
| 6. Follow bounded component factories and repeated templates | Interpret reviewed lambdas/method references when a source call site invokes them with known IDs or models. Preserve captured identities and component returns. Extend symbolic repeater templates to custom source wrappers and distinguish immediate factories from later event handlers. | Inbox split-pane factories resolve `inbox:firstPane:first` and `inbox:second`; its row fragment gets a symbolic template context without concrete batch/item IDs. Inspect a representative CRUD editor, wizard step, and tree factory before claiming broad support. |
| 7. Complete nested component and editor contexts | Pair named nested markup-owning classes with binary-name resources, and reverse-resolve their HTML. Support selecting a component's own companion and the same component as embedded by a caller. Provide multiple navigation targets where one fragment/template definition serves multiple instances. | `ProductsPage.ProductFields` pairs with `ProductsPage$ProductFields.html`; inherited/fragment/template navigation preserves the originating context; ambiguous or shared definitions do not acquire unsafe paired rename. |

The supported subset of slices 1 through 4 forms the implemented first navigation
milestone, bounded by the [source-layout contract](source-layouts.md). This does
not claim every constructor or static expression in those slices. Slice 5 now
supports the shared desktop top bar. The bounded subset of slice 6 establishes
Inbox composition and symbolic rows; broader CRUD, wizard and tree protocols
remain follow-up work. Slice 7 now supports named member pairing and bounded
context discovery; complete caller enumeration and the rename audit remain
outside this milestone. These
dependencies mean no single markup or constructor patch can unlock the entire
project.

Use shared source contracts and framework summaries so all Oat components can
benefit. Adding the observed 83 custom types to a fixed constructor list would
leave fluent methods, scopes, and callbacks unresolved.

## Validation and release gates

Keep a representative corpus covering Home, Badge, Card, Inbox, an ordinary form,
one mobile page, Products, a dialog/tab fragment, a wizard, and a tree. Include
the relevant shared layouts and source component contracts. Use pinned fixture
copies or an opt-in read-only corpus runner; ordinary CI must not depend on a
developer's checkout path. Exercise the application's Wicket 10.11 baseline
alongside the repository's Wicket 11 baseline.

Measure resolved IDs, confirmed attachments, matched markup bindings, unknown
regions, and navigation targets separately. A constructor-ID increase alone
does not establish usable navigation. Review expected paths against Wicket
semantics, with focused WicketTester checks where transparent resolution,
fragment projection, or custom wrapper placement needs runtime confirmation.

Each slice needs positive and adversarial PSI tests. Important negatives include
non-ID argument side effects, changed forwarding parameters, constructor cycles,
overridden behavior binding, root capture that mutates hierarchy, callback-time
replacement, conflicting transparent lookup, duplicate fragment definitions,
and shared-template rename. Callback registration is not proof that the callback
is harmless or that it executes during construction.

Keep the existing project/module/source-set isolation, cancellation, traversal
bounds, and read-only guarantees. Track every consulted constructor, method,
behavior, fragment provider, markup file, and binary contract as a snapshot
dependency. Unsaved edits and VFS changes must invalidate dependent results.
Test bounded response size and editor responsiveness as the corpus grows.

## MCP implementation decision

All proposed semantic work belongs in the existing analyzers and immutable
snapshots. Extend the facts consumed by `wicket_get_page_structure`,
`wicket_validate_page`, and `wicket_get_component_context`; no additional tool
is required for these slices. Existing test-planning tools benefit from improved
graphs through their current shared pipeline.

Preserve current response contracts when improvements can use existing nodes,
locations, findings, and template metadata. If fragment-definition/instance
provenance or scoped effect details need public representation, design additive
bounded metadata in the existing tools, update their descriptions and client
documentation, and add compatibility tests before publishing it. Do not expose
a definition ID as an ordinary runtime component path.

Cover project isolation, unsaved/disk freshness, cancellation, response bounds,
and no-write behavior through the same MCP request lifecycle as existing facts.
Native navigation, reports, and MCP must consume the same binding decisions.

## Implementation entry points

- `analysis/WicketIdConstructors.kt`: constructor ID provenance and framework contracts.
- `analysis/WicketComponentIdentity.kt` and `WicketLayoutSetupAnalyzer.kt`: the
  implemented bounded source-layout identity, setup and effect contracts.
- `analysis/WicketTransparentMarkup.kt`: Java-guided transparent lookup and
  logical markup parents.
- `analysis/WicketSourceSetupAnalyzer.kt`: values, effects, constructor chains, hooks, callbacks, and partial expansion.
- `analysis/WicketJavaAnalyzer.kt`: fallback integration and useful failure diagnostics.
- `analysis/WicketMarkupAnalyzer.kt` and `WicketMarkupInheritanceAnalyzer.kt`: extraction, transparent resolution, fragment definitions, and scoped uncertainty.
- `analysis/WicketPageAnalyzer.kt` and `WicketGraphMatcher.kt`: component-owned markup contexts and binding composition.
- `model/PageReport.kt` and `MarkupReport.kt`: separate semantic facts and source provenance.
- `analysis/WicketPageResolver.kt`, `WicketEditorTargetResolver.kt`, and `editor/WicketIdPsi.kt`: nested companions, contextual navigation, and editing guards.

Completion popup correction: scoped Java/HTML suggestions and transparent layout
lookup are verified with all contributors on real DialogPage fixtures. See
[the correction contract](context-completion.md#popup-scoping-and-transparent-layout-correction)
and [verification evidence](wicket-oat-popup-scoping-evidence.json).

Constructor data-loop verification: the isolated seed is audited without executing
application data, and DataTablePage now names its next OatConfirmDialog stop.
Existing template/completion/graphical marker behavior remains covered. See the
[contract](constructor-data-loops.md) and [evidence](wicket-oat-data-loop-evidence.json).

Constructor-owned confirmation content is covered by the
[twenty-third milestone](constructor-owned-dialog-content.md). OatConfirmDialog's
message Label replaces its inherited placeholder in a source declaration graph;
DataTablePage next stops at OatSelectionColumn's AbstractColumn construction.

Constructor-owned confirmation verification is recorded in the
[twenty-third milestone evidence](wicket-oat-confirmation-content-evidence.json),
including current source/test/ZIP receipts and both read-only Oat corpus runs.

Storage-only column construction verification is recorded in the
[twenty-fourth milestone evidence](wicket-oat-column-construction-evidence.json),
including current source/test/ZIP receipts and both read-only Oat corpus runs.

LambdaColumn constructor storage verification is recorded in the
[twenty-fifth milestone evidence](wicket-oat-lambda-column-evidence.json),
including current source/test/ZIP receipts and both read-only Oat corpus runs.

Reviewed date-column enum verification is recorded in the
[twenty-sixth milestone evidence](wicket-oat-date-enum-evidence.json), including
current source/test/ZIP receipts and both read-only Oat corpus runs.

Source provider construction verification is recorded in the
[twenty-seventh milestone evidence](wicket-oat-provider-construction-evidence.json),
including current source/test/ZIP receipts and both read-only corpus runs.

Compiled DataTable construction verification is recorded in the
[twenty-eighth milestone evidence](wicket-oat-datatable-construction-evidence.json),
including current source/test/ZIP receipts and both read-only corpus runs.

Fresh header-toolbar registration verification is recorded in the
[twenty-ninth milestone evidence](wicket-oat-toolbar-registration-evidence.json),
including current source/test/ZIP receipts and both read-only corpus runs.

Navigation-toolbar construction verification is recorded in the
[thirtieth milestone evidence](wicket-oat-navigation-toolbar-evidence.json),
including current source/test/ZIP receipts and both read-only corpus runs.

Owned-component accessor verification is recorded in the
[thirty-first milestone evidence](wicket-oat-owned-accessor-evidence.json),
including current source/test/ZIP receipts and both read-only corpus runs.

Fresh inline fluent-configuration verification is recorded in the
[thirty-second milestone evidence](wicket-oat-fluent-configuration-evidence.json),
including current source/test/ZIP receipts and both read-only corpus runs.

Owned table-reference forwarding verification is recorded in the
[thirty-third milestone evidence](wicket-oat-table-forwarding-evidence.json),
including current source/test/ZIP receipts and both read-only corpus runs.

Deferred table-columns read verification is recorded in the
[thirty-fourth milestone evidence](wicket-oat-columns-read-evidence.json),
including current source/test/ZIP receipts and both read-only corpus runs.

Null-guarded owned toolbar cache verification is recorded in the
[thirty-fifth milestone evidence](wicket-oat-toolbar-cache-evidence.json),
including current source/test/ZIP receipts and both read-only corpus runs.

Bounded declaration-scan reuse and its integrated verification are recorded in the
[thirty-sixth milestone evidence](wicket-oat-declaration-work-evidence.json).

Bounded navigation retrieval and its new development MCP tool are recorded in the
[thirty-seventh milestone evidence](wicket-oat-navigation-retrieval-evidence.json).

Conditional stored confirmation consumers and the remaining bulk selection boundary
are recorded in the [thirty-eighth milestone evidence](wicket-oat-confirmation-route-evidence.json).

Guarded selection-helper declarations resolve the bulk-confirmation blocker, as
recorded in the [thirty-ninth milestone evidence](wicket-oat-selection-clear-evidence.json).

Direct record-action callback declarations and the remaining plain-page initial
scope boundary are recorded in the [fortieth milestone evidence](wicket-oat-record-action-evidence.json).

Plain-page attachment declarations beside a fresh storage-only action registrar
are covered by [the forty-first milestone contract](stored-action-attachments.md).
Original application reports remain subject to the saved corpus comparisons.

Repeated direct registrations through one fresh source registrar are covered by
the [forty-second milestone contract](repeated-action-registrations.md), with
independent inferred action contexts and unknown active-callback state.

Standalone fluent chains are covered by the [forty-third milestone contract](chained-action-registrations.md),
with distinct method-token locations and unchanged earlier source protocols.

Constructor-local initializer chains are covered by the
[forty-fourth milestone contract](initialized-action-registrations.md), with
inferred targets and unchanged earlier source protocols and MCP value shapes.
