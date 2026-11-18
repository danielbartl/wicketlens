# WicketLens — Technical specification v0.1

## 1. Objective

The original feasibility spike evaluated IntelliJ PSI for Wicket-aware static analysis.
The editor helps developers find and complete a Wicket ID in the companion Java
or HTML file while editing a page or panel. See the
[editor milestone](#page-local-navigation-and-completion).

## 2. Supported environment

- IntelliJ IDEA
- IntelliJ Platform Gradle Plugin 2.x
- Kotlin plugin implementation
- Java Apache Wicket project analysis
- Source fixture/demo baseline: Wicket 11.0.0, Java 21 demos, Servlet 6.1 and Spring 7;
  see [the upgrade review](docs/wicket-11-upgrade.md). Published RC 1 evidence
  retains its Wicket 10.11.0 baseline.
- Classes extending WebPage or Panel
- Conventional same-package/same-basename Java and HTML pairing
- No dependency on WicketForge

## 3. Data model

Each component contains:

- nodeId: stable within one report
- wicketId: literal ID or null
- componentType: Java type
- parentNodeId: parent or null
- javaLocation: file and offset
- markupLocation: file and offset, if matched
- confidence: CONFIRMED, INFERRED, UNRESOLVED
- diagnostics: codes and messages

Page metadata includes schema version, qualified class name, markup path, warnings, and component nodes.

## 4. Java PSI discovery

Required patterns:

```java
add(new Label("title", "Hello"));

Form<?> form = new Form<>("form");
form.add(new TextField<>("name"));
add(form);

Label heading = new Label("heading", model);
add(heading);
```

Expected nesting:

```text
Page
├── title : Label
├── form : Form
│   └── name : TextField
└── heading : Label
```

Unresolved when the called code or ID provenance cannot be established:

```java
add(factory.create("header"));
add(new Label(config.getId(), model));
add(createHeader("header"));
```

Treat these as unresolved, not errors.

The later [bounded source setup pass](docs/source-setup.md) can now expand closed
source helpers/factories, source superclass constructor/initialization chains,
and custom constructors whose ID forwarding is provable. It preserves literal
origins, distinct factory instances, source dependencies, and conservative editor
rename guards. Conflicting or optional branches, recursion, unsafe dispatch, and unavailable source remain
unresolved; this is not arbitrary Java execution.
The [dispatch extension](docs/source-dispatch.md) additionally resolves final-page/
final-method targets and known instance factories, including source interface
calls, while preserving construction order and dependency snapshots. The
[provider-state extension](docs/provider-state.md) adds per-allocation private final
configuration and known constructor-injected dependencies with literal ID origins.
Source interface defaults are now supported through audited class/default
precedence, most-specific selection, and explicit interface-super calls; see
[the default-method contract](docs/interface-defaults.md).
Closed source provider factories now preserve fresh allocations, configuration,
and ID origins, with generic return checks and source-version guards; see
[the factory contract](docs/provider-factories.md).
Read-only source accessors can also return existing known providers and simple
String/primitive getter values. Nested reads retain immutable configuration and
literal ID origins, with separate purity and initialization checks; see
[the accessor contract](docs/source-accessors.md).
Proven primitive boolean conditions now select bounded `if`/`else` and ternary
paths, short-circuit operands, and guard returns. Bounded unknown `if` and ternary paths can
also confirm common known-ID subgraphs, retaining conditional type/parent
alternatives and all origins while disabling single-origin editing. Mutable
conditions and unsupported paths remain unresolved; source accessor purity and factory freshness checks still
apply. See [the conditional contract](docs/conditional-setup.md).
Stable primitive boolean parameters, unchanged local snapshots/aliases and
negation now reuse invocation-local path facts across checks and reviewed reads.
Reviewed compound `&&`/`||` predicates over those stable values retain input
relationships through aliases, accessors and audited storage, within existing
exploration limits and a bounded value depth. Primitive boolean equality and
eager `&`/`|`/`^` operations now preserve these relationships after a complete
operand audit. Stable integral inputs compared with reviewed known constants now
share repeated, reversed, complementary and adjacent-threshold facts, preserving
primitive bounds without guessing numeric values. Per-input closed intervals and
excluded equality values now connect different thresholds and remove impossible
paths. Relations between different unknown inputs and symbolic arithmetic remain
follow-up work.
Independent values and unsupported mutations/opaque conditions remain distinct;
this is bounded correlation rather than general boolean constraint solving.
Reviewed integral arithmetic/comparisons, String concatenation, and known String
content equality now resolve more values and conditions. Computed IDs retain
whole-expression provenance; shared calculation inputs have conservative editing
guards. See [the scalar-expression contract](docs/scalar-expressions.md).
Branch merging now accepts these known computed IDs, comparing their evaluated
values and preserving each literal/calculation origin. Computed-containing
components export typed read-only origin metadata; fragment editing stays guarded.

Post-MVP constructor coverage includes fifteen reviewed Wicket component classes.
See the [constructor audit](docs/constructor-coverage.md) for the exact class set,
the 33 overloads added in the standard-component slice, and retained uncertainty
rules. This constructor slice does not add field, helper/factory, or callback execution
analysis.

A further post-MVP slice supports straight-line additions and method-local
components in actual `onInitialize()` overrides on the analyzed Page or Panel.
The method must begin with a reviewed `super.onInitialize()` delegation. Both
phases share the graph, while unsupported initialization effects make combined
attachments unresolved. Unsupported fields, inherited setup, arbitrary helper effects, and
other lifecycle execution remain unexpanded. See the
[initialization contract](docs/initialization-coverage.md).

The field-tracking follow-up binds private final instance fields declared on the
selected class to direct `new` initializers at declaration or in one supported
constructor. Fields can connect constructor and `onInitialize()` additions while
retaining original construction identity, source, and container edges. All reads
in the source file must stay in supported additions on the analyzed instance.
Aliases, escapes, setters, captures, inherited/mutable/static/non-private fields,
alternative constructors, and unsupported initialization effects retain
uncertainty. See the [field contract](docs/field-tracking.md).

Important:
- Resolve Wicket add methods rather than matching every Java add call.
- Confirm component constructor semantics before interpreting string arguments as IDs.
- IDs are local to their parent container, not globally unique.
- Do not report definitive mismatches when the component hierarchy is uncertain.

## 5. HTML analysis

Use IntelliJ HTML/XML PSI to extract wicket:id attributes, owning elements, nesting, and source locations. Preserve ordinary HTML containers when reconstructing nesting. Treat unsupported inheritance and Wicket-specific markup as unresolved.

Post-MVP markup inheritance supports conventional source-based Page/Panel
ancestor fallback and a single `wicket:extend` → empty `wicket:child` slot per
level. Preserve locations in every contributing source and recompute merged
parentage/uniqueness. Inherited Java setup remains unexpanded; ambiguous, missing,
malformed, and unsupported chains stay unresolved.

## 6. Matching diagnostics

Implement:

- MATCHED
- JAVA_ONLY
- MARKUP_ONLY
- UNRESOLVED_DYNAMIC_ID
- UNRESOLVED_PARENT
- UNRESOLVED_MARKUP
- UNRESOLVED_BRANCH (reviewed conditional type/parent-path variant)

Report Java-only and markup-only findings as informational unless the analyzed scope is demonstrably complete. Avoid false definitive errors.

## 7. User interface

Provide an Analyze Wicket Page action showing results like:

```text
CustomerPage
├── title : Label             MATCHED
└── form : Form               MATCHED
    └── name : TextField      MATCHED

Diagnostics:
- [INFO] dynamicHeader: unresolved factory component
```

Include source locations and markup pairing.

Provide Copy Wicket Context, exporting deterministic structured JSON for future AI integration. No external network requests.

Post-MVP follow-up: report component/source rows support Java/HTML navigation.
Stale snapshots remain visible but cannot navigate or copy outdated context.
Re-analyze refreshes the same selected Java class; analysis does not automatically
run after edits. A later follow-up adds an expandable hierarchy, component detail
pane, Expand/Collapse controls, and source navigation from tree rows. The text
report is available through **Settings → Tools → WicketLens → Show text report
(debugging)**, disabled by default. The application-wide preference is persisted
and updates existing panels on Apply. Hierarchy, Dependencies, analysis and
JSON/MCP export remain available. This presentation-only setting requires no MCP
tool change; see [the settings notes](docs/plugin-settings.md). Stale trees remain
readable with navigation/copy blocked.

The sidebar presentation uses a left-docked WicketLens tool window with native
IntelliJ tree icons, muted type/status fragments, an icon action toolbar,
type-ahead search through collapsed branches, and an optional details pane.
It remains independently accessible alongside the IDE's Java Structure view.
The native Project tree also marks Wicket Java source class/file nodes with a
small orange dot on their existing icon, based on resolved Component inheritance.
Filenames and text fragments retain their native presentation. The badge does not
depend on a successful component report or change semantic/MCP facts. See
[the Project tree marker contract](docs/project-tree-markers.md).
Existing saved window positions are respected. Follow editor is enabled by
default after the view is opened, follows active Wicket Java/uniquely paired HTML
files, and clears unrelated snapshots. It can be disabled to pin the current
report. File switches cancel obsolete analysis; edits alone mark snapshots stale
until re-analysis. Source navigation preserves the selected page context.
The first filtering slice adds a persistent ID/type/path search field, combined
finding-status selection, and retained ancestor context. Markup-only children use
their matched containing hierarchy. Controls survive reanalysis and page switches;
filtering applies to the visible tree while text/JSON retain the complete report.
See [the hierarchy-filtering contract](docs/hierarchy-filtering.md).
The second hierarchy slice adds subtree focus, next/previous component-finding
selection, and expansion/selection/focus restoration across reanalysis. Unique
raw ID/type/parent paths identify rows; dynamic or ambiguous targets are not
guessed. Per-page contexts isolate files/classes/markup variants. See
[the exploration contract](docs/hierarchy-exploration.md).

## 8. Suggested architecture

- analysis/WicketJavaAnalyzer
- analysis/WicketMarkupAnalyzer
- analysis/WicketPageResolver
- analysis/WicketGraphMatcher
- model/
- actions/
- ui/
- export/

Avoid unnecessary persistent indexes and application-wide state.

## 9. Test fixtures

1. SimplePage: static Label and matching HTML.
2. NestedFormPage: nested Form and TextField.
3. VariablePage: component variable then add(variable).
4. MissingMarkupIdPage: Java ID missing from complete markup scope.
5. MarkupOnlyPage: HTML ID without Java counterpart in complete scope.
6. DynamicIdPage: dynamic constructor ID reported unresolved.
7. NonWicketAddPage: unrelated add method ignored.
8. RepeatedIdsPage: same ID under different parents allowed.

Use actual PSI fixture tests where possible. Assert graph structure, diagnostics, and deterministic JSON.

## 10. Acceptance criteria

- [x] Build succeeds.
- [x] Sandbox IDE launches.
- [x] Static Java patterns are recognized.
- [x] Nested HTML wicket:id values are found.
- [x] Matching is parent-aware.
- [x] Dynamic cases do not produce definitive false errors.
- [x] Analyze Wicket Page is available in the IDE.
- [x] JSON context can be copied.
- [x] Eight fixture scenarios are tested.
- [x] README documents run commands and limitations.

Verified for the supported prototype subset on 2026-10-28. See the
[acceptance record](docs/acceptance.md) for test mappings, commands, results,
compatibility limits, and remaining exclusions.

## 11. Feasibility gate

Evaluate 5–10 representative pages from a real Wicket application. Target at least 90% correct reconstruction within the explicitly supported subset, without false definitive error diagnostics.

Status: **deferred by agreement; not evaluated**. The completed fixture acceptance
does not establish this real application accuracy target.

## 12. Follow-up features

### Page-local navigation and completion

**Implemented.** Ctrl-click and Ctrl+Space work from either side of a Java/HTML
pair even when WicketLens can reconstruct only part of the page.

1. Reuse the existing companion resolver and parent-aware matches. When the
   analyzer lacks a binding, look for literal IDs in the active page or panel’s
   resolved companion in the same module and source set. Use recognized Java
   component-ID arguments and HTML `wicket:id` values. If the companion itself
   is ambiguous, keep the ambiguity explicit and skip the fallback.
2. For Ctrl-click, open the established counterpart first. If there is no binding,
   open a single plausible same-ID declaration. For multiple candidates, let the
   developer choose using the enclosing container or fragment and a source preview.
3. For Ctrl+Space, support empty and partial IDs in both editors. Rank unused IDs
   under the known parent first. When scope is incomplete, label broader
   companion suggestions **possible**. Exclude IDs already occupied by a known
   sibling and keep unrelated pages and ordinary Java strings out of the results.

**Acceptance on Wicket Oat’s `DialogPage`**

- [x] Clear the row’s HTML `email` value in a fixture copy. Ctrl+Space offers
  `email` from Java. Clear or partially type the row `Label`’s ID in Java and
  verify completion from HTML as well.
- [x] Restore the ID and Ctrl-click in both directions. A resolved row leads to
  its `Label("email", …)` and `<td wicket:id="email">`. With parent scope
  unresolved, verify that the chooser distinguishes the row from the
  `editFields` fragment’s `OatEmailField("email")` and its markup. The
  `addProperty("email")` call is excluded.
- [x] Check the single-candidate jump with incomplete scope and repeat with an
  unrelated page containing the same ID. Only the active companion contributes.
  Possible candidates confer no authority for paired rename, quick fixes or
  missing-ID diagnostics.

The [editor contract](docs/context-completion.md#companion-file-suggestions-and-navigation)
records the fallback behavior, limits and MCP decision. Further callback and
runtime reconstruction can be reconsidered if a concrete editor case needs it.

### Implemented features and unscheduled backlog

Implemented after the MVP: native unpaired-ID inspections and gutter icons,
paired-ID rename with preview/undo, direct editor navigation, parent-aware ID
completion, and configurable Wicket-orange editor coloring. See the
[editing contract](docs/developer-guide.md#editing-wicket-ids) for their conservative limits.
The bounded source follow-up additionally supports closed helpers/factories,
source superclass setup, and provable custom constructor ID forwarding; see
[the source contract](docs/source-setup.md).
Custom form components and `FormComponentPanel` have bounded ID/setup support
with separate composite markup scopes. Inspection quick fixes now correct
compatible unpaired IDs and create supported Java/HTML counterparts, with native
preview and undo; see [the quick-fix contract](docs/inspection-quick-fixes.md).
Broader markup support adds static header contributions, explicitly opened
filename variants, and read-only dependency layouts, with conservative fallback
and editing guards; see [the markup contract](docs/markup-inheritance.md).
Component queueing and ListView/RepeatingView item templates now have bounded
static support, with explicit uncertainty for placement and population outside
the reviewed subset; see [the queue/repeater contract](docs/queueing-repeaters.md).
Development branch merging now also checks queue placement separately on each
feasible setup path. Common placements can be confirmed, while optional queues
retain unresolved presence and ambiguous or changing placements stay unresolved.
Reviewed ListView/RepeatingView item templates now also merge across bounded
setup/callback choices. Common children remain inferred template matches;
optional/conflicting item structure stays unresolved without inventing row IDs/counts.

Reviewed source `static final` String/integral/boolean constants now supply IDs,
immutable configuration, and conditions, with source dependencies and conservative
editing/initialization guards; see [the constant contract](docs/static-constants.md).
Reviewed integral shifts, bit masks/complement, and eager primitive boolean
operations now extend source expressions and constant initializers; see
[the bitwise contract](docs/bitwise-expressions.md).

Compiled dependency and JDK String/integral/boolean constants now supply IDs,
conditions, and calculations through compiled PSI metadata. Whole-expression
editing guards remain; binary class/JAR versions join snapshot freshness checks.
See [the binary constant contract](docs/compiled-constants.md).

Remaining possibilities (not scheduled):

- Mounted URLs browser (development):
  - [x] Slice 1: project-wide native view and shared bounded Java `init()` / `mountPage` discovery, module/application/source-set filters, explicit uncertainty, page/HTML/registration navigation, existing Components analysis, and read-only `wicket_get_routes` over the same snapshots/lifecycle; see [the route contract](docs/mounted-urls.md)
  - [x] Reviewed source init inheritance, explicit super calls and private/final no-argument instance helper registration expansion, with call-path notes, conditional propagation, bounded traversal and shared MCP facts
  - [x] Inline `mount(new MountedMapper(...))` declarations, class-literal navigation, supplier/anonymous uncertainty and encoder notes shared with MCP
  - [x] Anonymous WebApplications directly returned by resolved Spring `@Bean` methods, shared by Mounted URLs and MCP with source-method selectors
  - [x] Two-column path browser with single-click source navigation and refresh on stale path clicks, an orange W gutter badge that opens the selected page’s mounts, and background freshness checks for lock-free editor callbacks
  - [ ] Mapper variables/factories/wrappers/named subclasses, root mapper mutations, package and resource registrations
  - [ ] Scoped path/duplicate conflict inspections once registration coverage is sufficient
  - [ ] Broader helper arguments, open dispatch and deferred setup contracts
- Further Java analysis: numeric relationships between distinct unknown inputs and symbolic arithmetic, normalization across differing queue scopes or add/queue alternatives, broader repeater population/custom-item contracts, mutable state, broader scalar operations and nonconstant library APIs, and broader injection models
- Wicket–Spring integration (optional, evidence-gated):
  - [x] Slice 1: possible injected-field overwrites during Component/Behavior construction
  - [x] Slice 2: transient-storage and bounded serialization/proxy inspections
  - [x] Slice 3: annotation and manual-injection checks, including models/sessions/helpers
  - [x] Slice 4: source bean-name completion/navigation and conservative declared-type validation
  - [x] Slice 5: broader application bootstrap and WicketTester setup/mock scaffolding
  - [x] Slice 6: injected-dependency view for analyzed pages and discovered source components
  - [x] Project-wide Spring Beans inventory and field-to-bean gutter navigation, shared with `wicket_get_injected_dependencies` when `className` is omitted
  - [x] Source profile conditions and generic FactoryBean products in completion/navigation and the dependency view (including `&name` factory lookups)
  - [ ] Broader discovery through optional IntelliJ Spring integration (XML, runtime profile selection, composed stereotypes, dynamic FactoryBean product types)
- Optional JetBrains MCP integration, following [the integration concept](mcp.md)
  and [the implementation roadmap and feasibility findings](docs/mcp-integration.md):
  - [x] Phase 1: register one static read-only WicketLens tool in JetBrains' built-in server and invoke it successfully from Codex
  - [x] Phase 2: project-scoped page structure, validation, and component context tools over the existing semantic engine and report model
    - [x] Project-scoped `wicket_get_page_structure`, existing export reuse, ambiguity/error/response limits, fresh disk/editor snapshots, and live Codex verification
    - [x] `wicket_validate_page` over existing findings and uncertainty, with unchanged severity/confidence and live Codex verification
    - [x] `wicket_get_component_context` over the existing graph, with unambiguous paths/node IDs, repeater templates and live Codex verification
  - [ ] Phase 3: protocol/reliability tests, bounded responses, lifecycle/cancellation handling, client documentation, and application validation when scheduled
    - [x] Slice 1: indexing/document waits, cancellation and timeout recovery, prompt project-disposal handling, concurrent request/session isolation and no-write assertions
    - [x] Slice 2: multiple open projects, larger bounded fixtures, transport cancellation/disconnection verification with documented host limitation
    - [x] Slice 3: graphical host setup, Codex/Claude compatibility and troubleshooting, preview artifact/evidence checks and release checklist
    - [ ] Retest upstream wire-cancellation/session-expiry behavior when the target SDK fixes it; current work remains bounded by WicketLens's deadline
- AI-assisted WicketTester generation:
  - [x] Slice 1: read-only `wicket_get_spring_test_plan` over the existing Spring plan/scaffold, with dependency requirements, constructor/mock/manual setup and shared lifecycle safeguards
  - [x] Slice 2: reproducible agent adaptation on the existing Spring test demo using plan/structure/context tools, meaningful title/model/HTML assertions, normal Maven execution, and a disposable failing-regression/restoration check
  - [x] Plain-Wicket planning: shared constructor/renderer/preview/create flow and wicket_get_test_plan, with Spring reuse only for recognized intent; Spring-free page/panel demo
  - [x] More complex Spring demo: manual constructor, named mocks, explicit generic binding and custom WebSession; agent adaptation and regression/restoration checks
  - [ ] Broader application-specific authentication, routing, resources and dependency graphs; built-in orchestration remains outside the read-only tools
- [x] MCP injected dependencies: existing analyzer/snapshot reuse, source profiles
  and FactoryBean candidates, with read-only/project/freshness safeguards
- [x] MCP Spring inspection findings over the existing native inspections,
  preserving configured severity, suppression, source ranges and uncertainty
- AI-assisted component generation
- Runtime component inspection

The MCP Phase 1 spike is verified on IDEA 2026.2.3 / build 262.10968.63 with
Codex CLI 0.166.0. It adds only `wicketlens_mcp_probe`, an optional tool returning
static versioned JSON. The first Phase 2 slice now adds `wicket_get_page_structure`;
validation now shares its request pipeline and projects existing findings.
Component context now selects paths/node IDs over the same graph. See the
[structure](docs/mcp-page-structure.md), [validation](docs/mcp-validation.md),
and [component-context](docs/mcp-component-context.md) contracts.
The planned reliability/setup slices are implemented for a downloadable GitHub preview; see
[client setup](docs/mcp-client-setup.md) and the [release checklist](docs/release-checklist.md).
Known host behavior and the deferred real-application evaluation are still open;
no publication is implied by completing these implementation slices.
Existing
analysis, inspections, navigation, JSON export, and Spring test scaffolding are
reused as applicable and remain available independently of MCP. Agent-assisted
generation can consume future MCP analysis; no separate LLM/chat implementation
or replacement for working generation features is planned in this integration.

For every core feature extension, assess the MCP impact: extend an existing tool,
add a focused tool over shared semantic values, or record why MCP needs no change.
Presentation-only changes do not require tool changes. See `AGENTS.md` and
[the dependency-tool contract](docs/mcp-injected-dependencies.md).

The first four Spring slices use resolved injection intent and conservative
module/source-set application evidence. Plain Wicket projects remain unaffected;
generic `@Inject` and classpath presence alone do not identify Spring. See
[the Spring inspection contract](docs/spring-integration.md) for exact gates,
supported bootstrap patterns, and remaining limitations.
The dependency view (slice 6) was implemented next, ahead of slice 5. It shows
source injection fields and candidate declarations with the existing snapshot
freshness/navigation guards; see [the view contract](docs/injected-dependencies.md).
Slice 5 now recognizes bounded local/bootstrap helpers and offers a preview-first
isolated Spring WicketTester recipe, with explicit manual binding/constructor
hooks and creation only in existing test roots. See [Spring testing](docs/spring-testing.md).


## Development after 0.1.0: source component layouts

- [x] First Wicket Oat navigation milestone: bounded source constructor identity
  and setup effects, inherited desktop/mobile layouts, transparent markup and
  simple localization. HomePage, BadgePage, CardPage and MobileCapacityPage have
  confirmed ordinary bindings in read-only application fixtures against Wicket
  10.11.0 and 11.0.0. See the [contract](docs/source-layouts.md) and
  [investigation and acceptance scope](docs/wicket-oat-support.md).
- [x] Second navigation milestone: fragment definition/instance projection,
  inherited definitions, repeated-use contexts and native definition navigation.
  See [the fragment contract](docs/fragments.md).
- [x] Third navigation milestone: bounded source factories, owned Panel markup
  and symbolic repeated-item templates for Inbox split panes and rows.
  Representative CRUD/wizard/tree cases were inspected; custom lifecycle and
  constructor-reference support remain explicit follow-up limitations.
  See [the factory contract](docs/factories.md).
- [x] Fourth navigation milestone: named member companions, independent nested
  selection and bounded caller/inheritor navigation contexts. ProductFields
  pairs with its binary-name resource; form setup was deferred to the fifth milestone.
  Native references and optional MCP structure discovery reuse analyzed graphs.
  See [the navigation contract](docs/nested-navigation.md).
- [x] Fifth milestone: owned form summaries, metadata tokens, lazy-input caches
  and private input constructor-reference forwarding. ProductFields has six
  confirmed ordinary bindings; internal inputs/conversion/validation stay opaque.
  Existing MCP tools reuse the shared facts without contract changes.
  See [the owned form-field contract](docs/owned-form-fields.md).
- [x] Sixth milestone: bounded origin-use audit for new layout IDs and isolated
  named members. Identity-only literals can use native paired rename when all
  editor guards hold; resource/model/data uses, shared Fragment/projected children
  and external member contexts remain protected. Existing MCP facts expose the
  same origin eligibility. See [the audit contract](docs/id-origin-audit.md).
- [x] Seventh milestone: source constructor-reference editors and separate
  replacement-site event contexts. ProductFields' registered placement is
  `products:editor:dialog:form:content`, with 11 inferred template bindings and
  unchanged initial analysis. Native navigation and optional MCP contexts share
  the same source graph. See [the contract](docs/replacement-editors.md).
- [x] Eighth milestone: registered wizard factories and separate source-declared
  step-selection contexts. OrderWizardPage has Customer/Items/Review projections
  at `wizard:form:body`, with 6/5/4 inferred bindings, source registration metadata
  and unknown current selection. Native navigation and optional MCP contexts share
  the same graphs. See [the contract](docs/wizard-selections.md).
- [x] Ninth milestone: node-relative tree factory/content templates. AccountsPage's
  nested and table trees each expose two explicit nullness-precondition contexts,
  with eight inferred bindings per context and unknown runtime ancestry/state.
  Native navigation and optional MCP contexts share the same source graphs.
  See [the contract](docs/tree-populations.md).
- [x] Tenth milestone: lazy composite form input declarations. OatDateRangeField
  has six wrapper-relative inferred bindings, including `input:from`/`input:to`.
  Source conversion/required/validator provenance remains separate from unknown
  runtime results. Shared tree declarations/cache recognition serve native and MCP
  contexts without relaxing initial form-effect proof. See [the contract](docs/form-inputs.md).
- [x] Eleventh milestone: registered custom composite form inputs. InvoicePage's
  `OatCustomField("phone", PhoneInput::new)` has six wrapper-relative inferred
  bindings, including `input:code`/`input:number`. Private final factory provenance
  and selected constructor forwarding share the existing form graph, native
  navigation and optional MCP output; runtime results remain unknown.
  See [the contract](docs/registered-form-inputs.md).
- [x] Twelfth milestone: direct abstract base-layout shell analysis. OatAppLayout
  now has eight primary inferred bindings after all three constructor entries
  agree under declared implementations. The child slot stays an explicit open
  boundary; nine sidebar-internal entries remain unresolved. Native and existing
  MCP views reuse the primary graph and explicit constructor/extension provenance.
  See [the contract](docs/base-layouts.md).
- [x] Thirteenth milestone: conditional ListView population templates. OatAppLayout
  has separate leaf/group item contexts with 4/7 inferred bindings, shared
  label/badge helpers and nested groupItems. All nine sidebar slots have conditional
  counterparts; primary bindings remain separate. Native reports/references and
  existing MCP views share source preconditions and unknown runtime state.
  See [the contract](docs/list-populations.md).
- [x] Fourteenth milestone: immediate table content factories. Follow the source
  unary setter invocation and direct replacement for DataTablePage's toolbar,
  including nested searchForm/search declarations. Native factory/ID navigation
  and optional MCP FACTORY_TEMPLATE contexts share the graph and explicit source
  provenance; factory/configuration/callback execution and later runtime state
  remain unknown. See [the contract](docs/content-factories.md).
- [x] Fifteenth milestone: lazy empty-state table toolbar content. Recover the
  owned null-guarded toolbar/cell route and selected content constructor chain for
  DataTablePage. Native and optional MCP TABLE_TOOLBAR_TEMPLATE graphs provide
  four toolbar-relative inferred bindings; framework-generated toolbar IDs, row
  count, visibility, colspan and callback execution remain unknown or not evaluated.
  See [the contract](docs/table-toolbars.md).
- [x] Sixteenth milestone: source static ListView factories registering item/data
  callbacks. AccordionPage has 11 primary matches and a separate two-binding
  inferred item template. Factory forwarding, its exact populateItem bridge and
  behavior-only default newItem decoration are reviewed; runtime rows, models and
  behavior effects stay unknown. Native references and existing MCP structure,
  validation and component-context views reuse the same graphs and registration
  provenance. See [the contract](docs/registered-list-callbacks.md).
- [x] Seventeenth milestone: source-declared initial dialog/drawer content. A
  bounded fallback follows selected owned constructor declarations and guarded
  content setters, composes initial fragments and transparent owned markup, and
  retains source-defined Ajax hooks as deferred declarations. DialogPage has 72
  inferred matches and 13 primary branch findings covered by three item templates.
  Constructor/configuration/model effects and later open/close/replacement state
  stay unknown. Native navigation and existing MCP structure, validation and
  component-context views share the graph and initialContentSetup provenance.
  See [the contract](docs/initial-dialog-content.md).
- [x] Eighteenth milestone: row-triggered drawer content as a separate source event
  template. DialogPage's details link supplies five inferred bindings at the
  guarded content slot, including the personDetails Fragment and its list. The
  primary initial graph remains 72 matches and 13 branch findings. Native
  navigation and optional MCP EVENT_TEMPLATE contexts share directContent
  provenance and separate symbolic trigger paths. Callbacks are not executed;
  runtime rows, models, validation and later open/close state remain unknown.
  See [the contract](docs/event-dialog-content.md).
- [x] Nineteenth milestone: context-aware ID completion in Java and HTML. Existing
  source/template graphs supply parent-scoped unused counterpart IDs, component
  types, context labels and inferred confidence. One focused empty literal can be
  retained in an isolated editor probe; reports, inspections, fixes, rename and
  MCP keep their original policy. Shared uses intersect candidate IDs. Existing
  insertion/escaping, scope guards, freshness and cancellation remain in force.
  MCP needs no new surface: the popup projects existing context facts and its
  incomplete editing-site hypotheses are not exported as observed bindings.
  Popup regression checks now include all contributors; recognized ID sites
  exclude generic string fallback. Editor-only transparent lookup hints recover
  incomplete host IDs without changing normal bindings or MCP responses.
  See [the contract](docs/context-completion.md).
- [x] Twentieth milestone: separate dialog submission source templates. Follow
  the owned AjaxButton.onSubmit bridge to source onConfirm hooks, with declared
  feedback, stable Ajax targets and guarded fresh content replacements. Native
  navigation and optional MCP SUBMISSION_TEMPLATE contexts share inferred graphs
  and bridge/effect provenance. DialogPage has three contexts (4/5/4 bindings).
  Initial reports/selectors stay unchanged; validation, models, business outcomes,
  callback execution and later state remain unknown.
  See [the contract](docs/submission-templates.md).
- [x] Twenty-first milestone: ordinary form submit/error declaration contexts.
  Standard AjaxButton and source OatSubmitButton overrides expose separate
  onSubmit/onError routes with declared feedback and Ajax refresh targets.
  InvoicePage supplies two contexts with two inferred bindings each, relative to
  its own companion's explicit additions. Its initial hierarchy stays unresolved.
  Native navigation and existing optional MCP SUBMISSION_TEMPLATE contexts share
  provenance, unknown initialization/validation/business outcomes and read-only
  snapshots. See [the contract](docs/form-submission-templates.md).
- [x] Twenty-second milestone: finite counted constructor data loops. Audit
  isolated canonical-record/scalar data seeding without unrolling or evaluating
  data/model values. Counter overflow, outer writes, component/callback escape
  and unreviewed calls withhold the expansion. DataTablePage's line-79 seed now
  passes; its next stop names OatConfirmDialog's unavailable constructor-ID proof.
  Native and existing MCP tools share graph/diagnostic facts with unchanged
  contracts. First-stop text diagnostics are shown ahead of cascading findings.
  See [the contract](docs/constructor-data-loops.md).
- [x] Twenty-third milestone: constructor-owned initial dialog content. The
  declaration-only ID trace accepts the compiled empty Model delegation, and
  selected source wrappers can place one private-final message Label into an
  inherited guarded slot. Virtual setter dispatch, constructor placement and
  deferred hook locations share the existing native/MCP initial-content metadata.
  OatConfirmDialog is supported; DataTablePage next stops at OatSelectionColumn's
  unreviewed AbstractColumn construction. Runtime actions/models remain unknown.
  See [the contract](docs/constructor-owned-dialog-content.md).
- [x] Twenty-fourth milestone: storage-only source columns at the reviewed
  compiled AbstractColumn constructor boundary. Private data storage and callback
  interface initialization are audited; header/cell/detach callbacks stay deferred,
  while eager calls and unknown initialization withhold the expansion. DataTablePage
  now passes OatSelectionColumn and next stops at OatBadgeColumn's unreviewed
  LambdaColumn delegation. Existing native/MCP diagnostics share the construction
  fact and exact later stop without column-generated children or runtime values.
  See [the contract](docs/column-construction.md).
- [x] Twenty-fifth milestone: LambdaColumn constructor storage and bound row
  functions. Both compiled constructor signatures delegate to the reviewed
  AbstractColumn storage and compiled null guard without invoking row/model
  callbacks. Source storage, receiver evaluation, callback-interface initialization
  and opaque alias provenance stay audited; signed constant defaults are supported.
  DataTablePage passes OatBadgeColumn and the number defaults, then stops at
  OatDateColumn's unreviewed FormatStyle enum defaults. Existing native/MCP
  diagnostics share these facts without generated cells or runtime values.
  See [the contract](docs/lambda-columns.md).
- [x] Twenty-sixth milestone: reviewed JDK FormatStyle enum defaults in source
  columns. Exact compiled JRT/java.base constants can be stored under type/static
  import qualification, with SDK dependencies and retained source diagnostics.
  Enum methods, general/source enums, formatting and locale/time-zone lookups
  remain unreviewed; model/row/rendering values stay unknown. DataTablePage passes
  the date, boolean and actions columns, then stops at the provider iterator's
  filtered() call into its page. Existing native/MCP views share this boundary.
  See [the contract](docs/date-column-enums.md).
- [x] Twenty-seventh milestone: construction-only source data providers. Zero-state
  source SortableDataProvider wrappers can allocate reviewed sort-state storage
  while page-query, row, model and detach callbacks remain deferred and unknown.
  State, initializers, constructor inputs, qualified delegation and eager calls
  withhold the expansion; consumer/table effects still need their own contracts.
  DataTablePage now passes InvoiceProvider allocation and next stops at the compiled
  DataTable constructor in OatDataTable.RowsTable. Existing native/MCP views share
  the same retained source diagnostic and binary dependencies.
  See [the contract](docs/provider-construction.md).
- [x] Twenty-eighth milestone: the compiled DataTable construction boundary.
  Source wrappers retain framework eager caption/body/grid and inherited methods;
  only deferred row/cell overrides and constant static storage are reviewed.
  Source constructors only forward four inputs in one direct super call;
  allocation inputs, interfaces and anonymous wrappers retain their effect guards. No provider queries, generated table children or runtime
  data are inferred. DataTablePage passes RowsTable construction and next stops
  at DataTable.addTopToolbar in OatDataTable. Existing native/MCP diagnostics and
  binary freshness capture the same construction-only fact and later blocker.
  See [the contract](docs/datatable-construction.md).
- [x] Twenty-ninth milestone: fresh owned DataTable toolbar registration and
  abstract/header/Ajax header construction. Source fields/locals, matching inline
  toolbar inputs, source forwarding, interface initialization and prior table uses
  are guarded. Header/render/sort/provider callbacks and runtime toolbar IDs stay
  unknown, with no private children added to the graph. DataTablePage passes top
  header registration and next stops at NavigationToolbar construction in
  OatNavigationToolbar. Existing native/MCP diagnostics and fresh snapshots share
  the same facts and uncertainty. See [the contract](docs/toolbar-registration.md).
- [x] Thirtieth milestone: compiled NavigationToolbar construction and its eager
  source paging factory under fresh same-table registration. Source pager wrappers
  only forward ID/pageable; later link initialization, Ajax/render callbacks,
  provider/resource/model evaluation, generated IDs and private contents stay
  unknown. DataTablePage passes both registrations and next stops at the structural
  mutation through chooser.getTrigger() in OatDataTable.newTools. Existing native/MCP
  diagnostics and snapshots share the same guarded facts.
  See [the contract](docs/navigation-toolbar-construction.md).
- [x] Thirty-first milestone: guarded owned-component accessor decoration.
  The selected pure getter of a fresh unescaped local owner can return its own
  uniquely allocated private-final framework child for one literal attribute
  behavior. Source dispatch, constructor paths/effects, interfaces and child uses
  are audited. OatPopover.getTrigger() now passes in OatDataTable.newTools;
  private children and runtime/model/rendering state stay unknown. Existing
  native/MCP diagnostics and snapshots share the same fact and later blocker.
  See [the contract](docs/owned-component-accessors.md).
- [x] Thirty-second milestone: guarded fresh inline source fluent configuration.
  Unary source enum setters preserve the selected concrete/anonymous receiver and
  forward through private behavior into exact Model storage. Source constructors,
  enum initialization, binding hooks and aliases retain their guards; runtime
  models, rendering, Ajax callbacks and private contents remain unknown. The clear
  button now passes within the shared budget. Native/MCP diagnostics and snapshots
  share the same facts. See [the contract](docs/fluent-component-configuration.md).
- [x] Thirty-third milestone: owned table-reference forwarding into the exact
  AbstractToolbar constructor during fresh unattached-owner configuration. Private
  table allocation/attachment, selected nested source constructors, interfaces and
  unchanged parameter forwarding are checked with reused constructor snapshots.
  Remaining constructor effects stay independently audited; cache/attachment,
  private contents, runtime IDs and deferred models remain unknown. Native/MCP
  diagnostics share the completed fact and next blocker. See
  [the contract](docs/owned-table-forwarding.md).
- [x] Thirty-fourth milestone: deferred owned-table columns getter and guarded
  immutable-list size reads. Exact JDK List.of provenance, unchanged constructor
  forwarding and same-owner table identity are checked. Borrowed-reference tracking
  preserves size-only authority through locals and rejects mutation, element queries
  and escapes. Native/MCP diagnostics retain the getter fact and later blocker;
  column count/elements, models, private contents and callback execution remain
  unknown. See [the contract](docs/table-columns-read.md).
- [x] Thirty-fifth milestone: null-guarded owned toolbar cache assignment and
  same-table registration. Shared guard/attachment proofs and scoped semantic
  reference search require private default-null storage, a unique owned allocation
  and no aliases/escapes. One plain private-cell replacement retains its effect
  audit. The original isolated table probe now completes with two root matches;
  runtime cache state, IDs, private contents and callbacks stay unknown. Native/MCP
  diagnostics share the fact. See [the contract](docs/owned-toolbar-cache.md).
- [x] Thirty-sixth milestone: bounded declaration scan reuse for complete
  DataTablePage setup. Session-local semantic subtree snapshots avoid repeated
  traversal and syntax-trivia work while retaining nested writes, cancellation,
  source freshness and the existing limits. The original page now has 26 inferred
  matches and 9 branch findings through native and default MCP views. Runtime
  state stays unknown. See [the contract](docs/declaration-work.md).
- [x] Thirty-seventh milestone: bounded navigation-context retrieval through
  wicket_get_navigation_contexts. A compact index supplies snapshot-bound IDs;
  selected calls return one exact existing context projection. Project/target and
  source freshness, native discovery limits and the 512-KiB cap remain enforced.
  All seven original DataTablePage contexts can be retrieved without a combined
  oversized page response. Existing tools and semantics are unchanged. See
  [the contract](docs/mcp-navigation-contexts.md).
- [x] Thirty-eighth milestone: conditional stored confirmation consumers. One
  source ask site flows through private callback storage, capture/clear/non-null
  guard and unchanged Consumer.accept under the shared owned AjaxButton submit
  bridge. Source data-helper, feedback and Ajax-refresh declarations have inferred
  target bindings; initiating dispatch, current slot, cleanup reachability and
  mutation/business outcomes remain unknown. Native navigation and existing MCP
  context tools share the report. DataTablePage gains one row-delete context;
  bulk clearSelection remains the next selection/model helper boundary. See
  [the contract](docs/confirmation-templates.md).
- [x] Thirty-ninth milestone: guarded selection-helper declarations within stored
  confirmation callbacks. A direct selected source helper declares one private-final
  column null guard, a pure source IModel getter/read/Collection.clear chain and
  same-owner Ajax refresh. Column dispatch, models, collection mutability, clear
  and refresh outcomes remain unknown; no helper/model is executed. Existing
  native/MCP contexts share source locations and five inferred bindings without
  initial graph or rename changes. DataTablePage's bulk confirmation is resolved;
  direct Mark as paid callbacks and their replaceAll/record-update loop are next.
  See [the contract](docs/selection-clear-declarations.md).
- [x] Fortieth milestone: direct source record-update action callbacks. Separate
  ACTION_TEMPLATE contexts describe source constructor BiConsumer arguments,
  private input loops with exact List.replaceAll, conditional canonical record
  enum-component substitution and shared feedback/Ajax/selection-helper targets.
  Registration/storage, UI dispatch, visibility, rows, equality/enum effects,
  construction and business outcomes stay unknown; no callback/helper/predicate
  is executed and no runtime trigger ID is invented. Native/MCP share value
  snapshots and inferred targets. DataTablePage's row/bulk Mark as paid source
  contexts complete. Plain-page initial attachment discovery is covered by the
  forty-first milestone below. See [the contract](docs/record-action-callbacks.md).
- [x] Forty-first milestone: plain-page initial attachments beside source-proved
  callback storage. A fresh single-use constructor-local registrar with an empty
  constructor and exact own-slot assignment/receiver return retains inferred
  initial bindings without executing or initialization-auditing its stored
  callback. Aliases, eager invocation, extra effects and unrelated unsafe setup
  remain withheld. Native/MCP share additive storedActionRegistrations locations;
  later active registration, dispatch, visibility, tree state and business
  outcomes remain unknown, and no rename authority is granted. Repeated action
  registrations are covered by the forty-second milestone below. See
  [the contract](docs/stored-action-attachments.md).
- [x] Forty-second milestone: repeated direct source action registrations. Every
  reference to a fresh registrar local is audited as one bounded group before
  any registration is published or cached. Multiple storage-only methods retain
  distinct callback/registration provenance and separate inferred action contexts;
  active callback, dispatch and later state remain unknown. Eager calls, aliases,
  captures and unsupported sibling uses withhold the group. Existing MCP value
  shapes and tool schemas are unchanged. Fluent chained registration is covered
  by the forty-third milestone below. See [the contract](docs/repeated-action-registrations.md).
- [x] Forty-third milestone: standalone fluent source registration chains. Every
  link must store its unchanged callback and return the exact source receiver;
  every use and chain result is audited before publishing or caching the group.
  Unsafe links, bridges, aliases and escaping results remain withheld. Distinct
  method-token locations identify each link and are reused by separate inferred
  action contexts in native/MCP views. Active callback/dispatch and outcomes stay
  unknown; schemas and existing standalone locations are unchanged. Chained
  local initializers are covered by the forty-fourth milestone below. See
  [the contract](docs/chained-action-registrations.md).
- [x] Forty-fourth milestone: constructor-local registrar initializer chains. A
  direct new allocation may initialize a named local through reviewed storage-only
  receiver-returning links. Initializer-only locals and later registrations share
  one complete bounded audit; unsafe later uses withhold the initializer too.
  Allocation and per-link locations reuse existing native/MCP value shapes and
  independent inferred action contexts. Active UI registration/dispatch, later
  state and outcomes remain unknown, with no rename authority.
  See [the contract](docs/initialized-action-registrations.md).

### Unscheduled analyzer and refactoring work

- [ ] Fluent action-registration chains starting from a bare allocation without a named local.
- [ ] Broader CRUD/save/delete/table callbacks, recursive tree populations and wizard protocols,
  expanded form internals/conversion/validation, with representative acceptance.
- [ ] Multi-context/shared-resource rename and property/localization-aware ID
  refactoring beyond the guarded two-file operation.

This is unreleased development work. Unknown effects, owned component contents,
and unsupported callbacks remain explicit; the full real-application accuracy
and performance study is still deferred. MCP decision: extend the existing
structure, validation and component-context facts through the shared analyzers
and snapshots. Navigation discovery adds an optional structure-tool flag, with
unchanged default responses, tools and schemas. Native and MCP acceptance covers
navigation, project/module isolation, freshness, cancellation, bounded discovery
and read-only behavior.

## 0.1.0 release stabilization

Status: **published on 2026-11-10** as [0.1.0](https://github.com/danielbartl/wicketlens/releases/tag/v0.1.0),
with the updated landing page deployed and verified. See the
[publication and deployment record](docs/acceptance.md#final-010-publication-2026-11-10).

Final **0.1.0** retains the feature scope frozen at **0.1.0-rc.1**: the implemented component/editor,
optional Spring/dependency/test-planning, Mounted URLs and nine-tool MCP features.
The fixture/demo baseline has moved to Wicket 11.0.0; see the
[upgrade review](docs/wicket-11-upgrade.md) and [final release notes](docs/releases/0.1.0.md).
The [editor milestone](#page-local-navigation-and-completion) is included in
post-0.1.0 development. Open feature proposals are unscheduled.
Use [the release checklist](docs/release-checklist.md) for the versioned artifact,
clean installation and Preview 2 upgrade, native editor/UI checks, graphical MCP
client evidence, limited real-project smoke checks and public documentation.
The full real-application accuracy/performance study remains deferred by agreement.
MCP decision: release stabilization adds no semantic facts or operations; retain
the existing tools and schemas and refresh their version-specific client evidence.
