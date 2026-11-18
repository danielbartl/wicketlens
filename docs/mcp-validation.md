# MCP page validation

`wicket_validate_page` returns a focused view of the **existing** Java/markup
findings. It shares the structure tool's request pipeline and does not introduce
a new checker, change severity/confidence, apply fixes, or judge runtime validity.

Development [source-layout bindings](source-layouts.md) and rejected-operation
locations flow through these same findings and diagnostic channels. A matched
attachment with unresolved owned contents does not establish a complete runtime
tree. Request parameters and response schema 1 remain unchanged.

Development [fragment support](fragments.md) adds optional definition-reference
provenance and per-instance markup projections through the same graph. Uncalled
factories can expose a definition reference without an attached component. The
existing tools and schema 1 are retained; see the linked additive-field contract.

Development [factory support](factories.md) follows proved source callbacks,
composes owned markup and preserves generated populations as **INFERRED**
templates. Optional `sourceSetupExpanded`, `ownedMarkupPath` and markup
`markupProviderNodeId` metadata reuse the shared schema-1 export. Runtime IDs,
counts and post-event hierarchy remain unknown; existing requests remain compatible.

[Named member targets](nested-navigation.md) pair binary-name companions while
retaining their own binding uncertainty. Optional navigation-context discovery is
provided by the structure tool; validate the indicated caller's class/module/source
set independently. This validation request and its findings are unchanged.

Development [owned form summaries](owned-form-fields.md) can confirm wrapper
attachments through metadata tokens, private lazy inputs and private input
constructor references. Internal inputs, conversion and validation remain opaque
with explicit scope diagnostics. No private input paths or model values are
invented. These facts reuse the same snapshots, tools and schema 1; request and
response contracts are unchanged.

The [ID origin-use audit](id-origin-audit.md) reuses existing origin eligibility:
`idRenameSafe: false` is explicit; a true value may be omitted. Passing this audit
alone does not authorize a native edit: current scope, sharing, variants,
inheritors, collisions and writability still apply. Shared/projected and data
origins stay protected. MCP remains read-only, with unchanged tools and schema 1.

[Replacement editor contexts](replacement-editors.md) are available through the
structure tool's existing `includeNavigationContexts: true` option. Entries with
`relationship: EVENT_TEMPLATE` carry explicit lifecycle provenance, inferred
bindings, and inline parent/ancestor/child context. They describe a declared
replacement site with unknown runtime presence. The primary graph and ordinary
validation/component-context requests keep their initial-phase meaning; selecting
an event-only path there fails normally. No new tool, parameter or schema version
is introduced. Read-only, freshness, discovery and response bounds are shared.

## Request and shared behavior

```json
{
  "projectPath": "/absolute/path/to/open/project",
  "className": "example.ValidationPage",
  "moduleName": "mcp-fixture",
  "sourceSet": "main"
}
```

The parameters and defaults match [the structure tool](mcp-page-structure.md):
`projectPath` is selected by JetBrains' host; `className` is required;
`moduleName` is an optional exact name; `sourceSet` is `main` (default), `test`,
or `any`. Only configured Java source roots in that project are eligible.
Ambiguous declarations and unsupported/library-only targets are rejected.
Conventional default markup is used rather than an editor-selected variant.

Both tools use `WicketPageAnalysisRequest` for scoped selection, asynchronous
source/dependency refresh, smart/committed PSI, core analysis, version capture,
post-serialization freshness checks, cancellation, and error handling. The
structure request's former Kotlin source names remain aliases to the shared
implementation. No second analysis engine or transport was added.

The cooperative budget remains **30 seconds**. Success is bounded to **512 KiB
of UTF-8 JSON**, with the shared 5,000-component/10,000-markup-element caps and
the core's existing budgets. Oversized responses are errors, not truncated
graphs. The normal editor, sidebar, clipboard, inspections, JSON schema 1 export,
and Spring/scaffolding flows are unchanged.

## Response and interpretation

Success is one MCP text-content block containing an envelope with
`schemaVersion: 1`, `tool: wicket_validate_page`, `status: ok`, `truncated: false`,
and the same `target` and `snapshot` metadata as the structure tool. Its payload
is `validation`, containing:

| Field | Meaning |
| --- | --- |
| `analysisSchemaVersion` | Version **1** of the existing analysis/export model. |
| `qualifiedClassName` | Resolved source class. |
| `matchingAvailable`, `markupAvailable` | Explicit availability flags. Empty arrays alone do not imply a complete or successful pairing analysis. |
| `scopeComplete` | Separate original `java`, `markup`, and `matching` flags. Missing markup/matching produces `null` for that flag. These describe static coverage, not runtime completeness. |
| `summary` | Finding count and counts by original code, severity, and confidence; counts for each diagnostic channel. Only present categories occur in the count maps; absent categories mean zero. |
| `findings` | Exact existing matching findings, including `MATCHED`, messages, confidence, severity, Java/markup node IDs and locations, and causes. |
| `warnings` | Exact existing report warning array. |
| `componentDiagnostics` | Nonempty component diagnostic arrays, with node ID, Wicket ID, type, and Java location. |
| `markupDiagnostics` | Existing markup-report diagnostics. |
| `markupElementDiagnostics` | Nonempty element diagnostic arrays, with element/node identity, tag, Wicket ID, and locations. |
| `documentDiagnostics` | Existing document-level syntax/PSI diagnostics. |

The projection uses the existing `WicketContextExporter`; it does not reinterpret
the findings. Diagnostic channels can overlap, as in the core report, so their
counts are **not a deduplicated incident total**. Full component and markup trees
are omitted; use `wicket_get_page_structure` when that context is needed.

`status: ok` means the request completed. There is deliberately no `valid` or
`hasErrors` verdict. In particular:

- `JAVA_ONLY` and `MARKUP_ONLY` keep their original informational severity.
- `INFERRED` and `UNRESOLVED` confidence and uncertainty causes stay visible.
- Development builds retain optional/conflicting branch variants as
  `UNRESOLVED_BRANCH`, including the causes on corresponding markup paths.
  Common bindings can still be `MATCHED`; conditional variants never become
  definite `JAVA_ONLY`/`MARKUP_ONLY` findings. See [branch analysis](conditional-setup.md).
- Queue placement is checked on every feasible setup path. Optional queues keep
  unresolved branch findings even after placement; ambiguous/changing placements
  keep `UNRESOLVED_QUEUE`. Unplaced queues suppress absence claims within their
  known declaring scope rather than across unrelated page branches.
- Common reviewed repeater children retain `MATCHED` with `INFERRED` confidence
  across branches. Optional/conflicting item structure retains `UNRESOLVED_BRANCH`;
  neither result establishes row existence, runtime IDs/counts or model values.
- Reviewed compound `&&`/`||` predicates over stable flags share input facts
  through the existing core engine. Optional components retain unresolved presence;
  no runtime flag values, proof groups or numeric constraints are exported.
- Stable primitive boolean equality and eager `&`/`|`/`^` operators share those
  facts after the operand audit. Optional structure stays unresolved; boxed/reference
  equality, possible effects and numeric comparison uncertainty remain guarded.
- Stable integral threshold comparisons share canonical predicate facts while
  retaining existing confidence, conditional findings and causes. Type bounds
  do not establish runtime numeric values. Per-input intervals/exclusions now
  connect thresholds, settling implied tests and avoiding impossible paths.
  Broader numeric relations remain
  conservative, and no numeric proof state is exported.
- Missing markup or unsupported setup remains unresolved rather than a clean
  report or a definite runtime error.
- Java/markup binding analysis does not run the whole IntelliJ Problems system
  or the separate Spring field/serialization inspections. It does not verify
  actual runtime component trees, model values, or every lifecycle path.

For the disposable mismatch fixture, the real response has one `JAVA_ONLY` and
one `MARKUP_ONLY`, both `INFO` and `INFERRED`, plus a `PARTIAL_ANALYSIS` warning.
Codex reported those qualifications from the actual tool response. The complete
path-redacted [evidence](mcp-validation-evidence.json) includes matched,
mismatched, and unresolved cases.

Tool errors use the same versioned error envelope and codes as the structure
tool, with `tool: wicket_validate_page` and MCP `isError: true`. `STALE_SNAPSHOT`
requires another call; lookup failures do not select a different class.
Host project-selection/decoding and cancellation failures remain host errors.

## Verification and reproduction

The focused run passes **55 cases**: all 32 existing structure/request fixtures
and 23 new validation cases. Projection cases check exact export equality,
informational mismatches, partial/unavailable scope, every diagnostic channel,
source locations, independent summary dimensions, zero counts, structure-wire
preservation, errors, response bounds, stale edits, deterministic/no-write
behavior, and an asynchronous call without sidebar publication.

The full suite passes **1,609 tests**, with no failures, errors, or skipped tests.
Packaging and project/structure checks pass. Binary verification against IDEA
262.10968.63 is **Compatible**, with the existing four deprecated and six
experimental API notices. Shared MCP classes and the optional descriptor match
the ZIP; no additional framework/transport libraries are bundled.

Use the existing isolated fixture harness:

```sh
./gradlew runMcpSpike -PmcpStructureFixture=true \
  -PmcpSpikeTools=wicketlens_mcp_probe,wicket_get_page_structure,wicket_validate_page
python3 tools/check_mcp_validation.py
python3 tools/check_mcp_structure.py
```

Wait for the host's server-started message before the Python checks. The fixture
contains `McpPage`, `ValidationPage`, and `UnresolvedPage` in `mcp-fixture` and
the existing duplicate-class case in `mcp-other`. The validation client compares
its output to the live structure output, verifies errors/uncertainty, temporarily
corrects a fixture ID on disk, confirms `MATCHED`, then restores the original
file. Source checksums remain unchanged after the checks. The original structure
and probe checks also pass with validation present.

Codex CLI 0.166.0 successfully invoked only the validation tool with ephemeral
settings, no file/command tools, and an empty client workspace. Its summary
preserved the informational findings, inferred confidence, partial-analysis
warning, and runtime limitation. Normal IDE/client configuration was unchanged;
the disposable host was stopped after acceptance. For client setup, use
[the existing MCP guide](mcp-integration.md), selecting `wicket_validate_page` and
the request above. Relevant source paths/IDs in responses may be sent to an
agent's model provider according to that client's configuration.

## Remaining work

[Component context](mcp-component-context.md) now selects from the same captured graph.
Broader Phase 3 reliability/client/IDE matrices and the separately deferred
real-application accuracy/performance study remain open.

[Registered wizard selections](wizard-selections.md) extend the same optional
structure projection with `relationship: SELECTION_TEMPLATE`, lifecycle phase/kind
and additive `selection` provenance (source registration index/count, title,
fragment definition and index/initialization source locations). Steps sharing a
body path remain separate contexts. All bindings are inferred templates with
unknown runtime presence; no current index or validation result is supplied.
Inline parent/ancestor/child projections refer to that source template. Default
structure, validation and ordinary component-context paths retain the initial
graph. No tool/request parameter/schema version changes or write operations.

[Tree population templates](tree-populations.md) extend optional structure discovery
with `relationship: TREE_TEMPLATE` and additive `treeTemplate` provenance.
`pathScope: NODE_RELATIVE` distinguishes paths inside one logical node from the
separate `hostComponentPath`; joining them does not establish runtime ancestry.
Nullness conditions describe preconditions, not current state. Runtime IDs/count,
expansion and selection remain unknown. Optional component `possibleComponentTypes`
retains source factory constructor alternatives without selecting a concrete type.
Inline parent/ancestor/child projections share native facts. Default structure,
validation and ordinary component-context selection retain the initial graph.
No new tool, parameter, schema version or writable operation is introduced.

[Lazy composite form input templates](form-inputs.md) extend optional structure
output with `relationship: FORM_TEMPLATE` and additive `formTemplate` provenance.
`WRAPPER_RELATIVE` paths describe declared input/child structure inside a source
wrapper. Conversion, required-check and validator hooks have source locations and
`execution: not_evaluated`; runtime presence, required state, model values and
conversion/validation outcomes remain unknown. Parent/ancestor/child projections
reuse native graphs. Default structure, ID-binding validation and ordinary component
selection retain the initial graph. No new tool, parameter, schema version or
writable operation is added.


[Registered custom composite inputs](registered-form-inputs.md) extend the same
optional FORM_TEMPLATE projection with additive `formTemplate.registration` source
provenance: `kind: CONSTRUCTOR_REFERENCE`, `ownerClassName`,
`wrapperConstructionLocation`, `constructorReferenceLocation`,
`inputConstructorLocation` and `factoryFieldLocation`. Separate registrations retain
separate context IDs. Paths stay WRAPPER_RELATIVE; no concrete page attachment or
runtime conversion/validation result is claimed. Native navigation uses the same
graphs. Default selectors, read-only behavior, isolation, freshness, cancellation
and response limits remain unchanged. No new tool, parameter or schema version.

For a registered form template, context `className`/`moduleName` identify the
registering source owner; `formTemplate.wrapperClassName` identifies the virtual
graph's root component type. A wrapper in a dependency module retains that source
provenance without relabeling its caller as a class of the dependency module.


[Direct abstract base layouts](base-layouts.md) improve the eligible primary graph
and add `baseLayoutTemplate` to structure `analysis`, `validation` and selected
`context`. `kind: SOURCE_DECLARED_BASE_LAYOUT`, `phase: BASE_LAYOUT_TEMPLATE`,
`pathScope: BASE_RELATIVE`, `constructorAgreement: ALL_REVIEWED_ENTRIES` and
`dispatch: DECLARED_IMPLEMENTATIONS` describe the source assumptions. Constructor
signatures/locations and extension-slot locations retain provenance. Matches are
INFERRED; runtime presence/constructor, subclass overrides, extension contents and
model values stay unknown. A resolved path describes that declaration graph.
Concrete-page analysis, tools, parameters and schema version remain unchanged.
Shared freshness/isolation/cancellation/response bounds and read-only behavior
apply; no new editing authority is supplied.


[Conditional ListView populations](list-populations.md) add primary
`populationTemplates[]` source graphs to structure `analysis`, validation and
component-context output. Each graph has `populationTemplate` provenance for its
host, symbolic items, predicate preconditions, helper/configuration sources and
projected placeholder bodies. Required predicate results apply to one source item
at that call; leaf and group rows may coexist. All item bindings are INFERRED,
with runtime IDs/counts, model/predicate state, authorization, visibility and runtime
subclasses unknown, and configuration/callback execution not_evaluated. Primary
markup covered conditionally is UNRESOLVED_BRANCH; template-only children do not
select the primary graph. Optional navigation discovery projects the same values
as REPEATER_TEMPLATE contexts. No new tool, parameter or schema version. Exhaustion
withholds all population graphs, sets populationDiscoveryComplete=false and marks
structure/validation/context truncated. Shared freshness/isolation/cancellation,
read-only behavior and whole-response bounds remain unchanged.


[Registered list item/data callbacks](registered-list-callbacks.md) reuse these
`populationTemplates[]` graphs. A reviewed source static factory forwards its ID
and direct lambda into an exact `populateItem` bridge; an optional default
`newItem` wrapper may add only reviewed owned behaviors. Component
`registeredListPopulation` and `populationTemplate.registration` expose
`SOURCE_DECLARED_LIST_CALLBACK`, factory/registration/callback/bridge/item-wrapper/
behavior locations, `callbackExecution: not_evaluated` and
`behaviorEffects: unknown`. Hosts and item bindings are inferred and protected
from paired rename. Model values, runtime rows/count/IDs and visibility remain
unknown. Template-only child paths still do not select the primary hierarchy.
Optional navigation discovery reuses the same graph as `REPEATER_TEMPLATE`.
Existing source/module isolation, freshness, cancellation, discovery/response
bounds and read-only guarantees apply; no new tool, parameter or schema version.


[Initial dialog/drawer content](initial-dialog-content.md) can add
`initialContentSetup` to the existing primary graph, validation and
component-context output. `kind: SOURCE_DECLARED_INITIAL_CONTENT` and
`phase: INITIAL_DECLARATIONS` identify a bounded fallback used after strict setup
analysis cannot establish a graph. Every binding in this fallback is INFERRED and
protected from paired rename. `hierarchyResolved` describes source declarations;
it does not establish the current runtime hierarchy.

`hosts[]` records host/slot/content node IDs, selected constructor locations,
the guarded setter, supplied content ID and initial placement locations.
`callbackLocations[]` retain deferred source declarations. Constructor,
configuration and callback execution are `not_evaluated`; runtime presence,
constructor/configuration/model effects, validation, visibility and later
open/close/replacement state remain unknown. Initial Fragment instances reuse the
normal provider/definition graphs. Event-only fragments retain definition lookup
without an initial attachment. Existing module/project isolation, source freshness,
cancellation, request and response bounds, and read-only behavior apply. No new
tool, parameter or schema version is added.


[Row-triggered event content](event-dialog-content.md) is available separately in
`wicket_get_page_structure` with `includeNavigationContexts=true`. Its
`EVENT_TEMPLATE` / `SOURCE_DECLARED_EVENT_CONTENT` bindings and `directContent`
provenance describe a source content placement and a separate symbolic row trigger.
The initial validation and component-context responses remain unchanged. Event-only
paths cannot select the initial hierarchy. Row IDs/count, models, validation,
visibility and later open/close state remain unknown; callbacks are not evaluated.


[Dialog submission declarations](submission-templates.md) are separate optional
`SUBMISSION_TEMPLATE` contexts in `wicket_get_page_structure`. This tool continues
to select the initial graph; submission-only content is not an initial selector.
Source feedback and Ajax target declarations establish no validation, model,
save/delete, runtime presence or later tree/open/close outcome. Callbacks are not
evaluated, and existing freshness/bounds/read-only contracts apply.


[Ordinary form submit/error declarations](form-submission-templates.md) are optional
SUBMISSION_TEMPLATE structure contexts with OWN_COMPANION_DECLARATIONS scope.
Their source additions do not upgrade an unresolved initial path in this tool.
Callback route names establish no validation, model, business or runtime outcome.
Default selectors, snapshot freshness, bounds and read-only behavior are unchanged.

## Constructor data-loop isolation

The existing shared source-layout analyzer can audit finite counted root-constructor
loops that initialize reviewed data only. No application loop is executed or
unrolled. Data/model contents and runtime row counts remain unknown. Complete
construction proof can improve the same graph; an unsupported loop or later effect
still withholds expansion and retains the exact blocking source diagnostic.
Request parameters, selectors, schema 1, severity/confidence, freshness, cancellation,
read-only behavior and whole-response bounds are unchanged. See
[the native/MCP contract](constructor-data-loops.md).

Constructor-owned message content is also covered by the
[constructor-owned dialog contract](constructor-owned-dialog-content.md). Selected
source wrappers can install one private-final Label/container into the inherited
guarded slot. Existing initialContentSetup placementLocations identify the
constructor placement; source Ajax hooks and message configuration remain deferred.
All bindings remain inferred, constructor/model/visibility/action effects remain
unknown, and unsupported later setup still withholds the entire initial graph.
The shared native analyzer supplies these facts with unchanged parameters,
selectors, schema, snapshot freshness, cancellation, bounds and read-only behavior.

The twenty-fourth milestone adds a bounded storage-only construction contract
for source columns whose first compiled superclass is AbstractColumn. The shared
analyzer audits source private data storage and callback-interface initialization.
Header creation, cell population, detach, model values and sort state remain
unknown and callbacks are not evaluated. These column methods supply no generated
children to the page graph. Existing diagnostics retain the reviewed constructor
fact and any later blocking source statement; the full layout expansion is withheld
when remaining setup fails. The existing tools, selectors, JSON members and schema
versions are unchanged. Source wrappers, callback interfaces and the compiled
wicket-extensions dependency participate in freshness; cancellation, source/call
bounds, read-only behavior and whole-response limits remain in force.
See [the column contract](column-construction.md).

The twenty-fifth milestone extends storage-only source columns to the two exact
compiled LambdaColumn constructors. Bound row-function references, receiver
evaluation and callback-interface initialization use the shared source audit;
callback types are retained across opaque aliases. Header/cell/detach/data-model
callbacks and row/model/sort/formatting values remain unknown and are not evaluated.
Existing diagnostics preserve the constructor fact and any later blocking source
statement. The layout expansion is withheld when remaining setup fails. Binary
constructor/guard/function dependencies participate in freshness. Existing tools,
selectors, response members, schemas, cancellation, bounds and read-only policy
are unchanged. See [the LambdaColumn contract](lambda-columns.md).

The twenty-sixth milestone permits direct storage of the four reviewed JDK
java.base FormatStyle enum constants in source columns. Type/static-import
qualification and compiled JRT identity are required. Both FormatStyle and Enum
SDK dependencies participate in freshness, and retained diagnostics preserve each
accepted physical use and any later source blocker. Source/other enums, receiver
expressions, enum/formatter methods and locale/time-zone lookups receive no new
contract. Formatting, row/model values and rendering remain unknown and callbacks
are not evaluated. Existing tools, parameters, selectors, schemas, cancellation,
bounds and read-only policy stay unchanged. See
[the date-column enum contract](date-column-enums.md).

The twenty-seventh milestone distinguishes zero-state source SortableDataProvider
allocation from later iterator/size/model/detach callbacks and page queries.
Reviewed no-argument provider/sort-state construction supplies no row/model/sort
values or generated children. State, initialization, constructor inputs, qualified
super receivers and eager callbacks remain unreviewed. Consumer/table construction
retains its own source-effect checks. Shared diagnostics preserve the allocation
fact and later blocker; source and protocol/sort-state archives join freshness.
Existing tools, selectors, response members, schemas, cancellation, bounds and
read-only behavior remain unchanged. See
[the provider construction contract](provider-construction.md).


The twenty-eighth milestone reviews the compiled four-argument DataTable
constructor inside owned source setup. Source wrappers must retain framework
eager caption/body/grid and inherited methods; only deferred row/cell factory
overrides and constant static storage are supported. Anonymous wrappers, source
ancestors and interface initialization share the same guards. Source
wrapper constructors must consist of one direct super call forwarding their four
parameters in order. Extra statements and computed delegation inputs are
unreviewed; allocation inputs retain their existing effect audit. Provider queries,
column/header/row/cell callbacks, private table children and runtime values remain
unknown. Other table methods, toolbar registration and lifecycle effects require
separate contracts. Existing diagnostics preserve the constructor fact and later
blocker; table/grid archives join snapshot freshness. Existing tools, parameters,
selectors, response fields, schemas, cancellation, bounds and read-only policy
remain unchanged. See [the DataTable contract](datatable-construction.md).


The twenty-ninth milestone reviews fresh owned DataTable toolbar registration and
AbstractToolbar/HeadersToolbar/AjaxFallbackHeadersToolbar construction. Direct
constructor locals or private-final allocations must remain unescaped and
unattached, with only reviewed output-flag setup or prior registrations. Each
inline toolbar receives that same table; locator data is null or a direct
constructor parameter. Source/anonymous wrappers only forward constructor inputs
and declare reviewed deferred hooks and constant storage. Interface initialization,
source ancestors, budgets and cancellation stay audited. Existing diagnostics
record construction and registration after argument review; runtime toolbar IDs,
private contents, sorting, rendering, visibility and provider/model values remain
unknown. Other toolbar/lifecycle protocols require separate contracts. Existing
tools and snapshots supply these same facts with unchanged parameters, selectors,
response fields and schemas. See [the registration contract](toolbar-registration.md).


The thirtieth milestone reviews NavigationToolbar construction within the same
fresh owned table registration proof. Its paging factory is eager: a selected
source override must directly return one fresh PagingNavigator/AjaxPagingNavigator
wrapper forwarding its ID/table inputs. Source label factories and source pager
initialization overrides remain unreviewed. Constructors, interface initialization
and source storage retain effect guards; paging-link initialization, Ajax/render
callbacks, resource/model evaluation, provider data, generated IDs and private
contents stay unknown. Existing diagnostics and value snapshots share these facts
without changing tools, parameters, selectors, response fields or schemas. See
[the navigation-toolbar contract](navigation-toolbar-construction.md).


The thirty-first milestone reviews owned component accessor decoration. A pure
selected source getter on a fresh unescaped local owner can return its own
uniquely allocated private-final framework WebMarkupContainer child for one
literal AttributeModifier.replace behavior. Source dispatch, selected constructor
paths/effects, interfaces, field uses and prior owner uses are guarded. This
provides no private child IDs or editing authority; rendering, model values,
visibility and later state remain unknown. Existing diagnostics and snapshots
share the fact and exact later blocker with unchanged tools, parameters,
selectors, response members and schemas. See [the contract](owned-component-accessors.md).


The thirty-second milestone reviews fresh inline source component fluent chains.
Each unary source enum setter forwards through private-final behavior storage
into an unchanged exact framework Model and returns the same receiver. Concrete/
anonymous dispatch, source constructors, enum initialization declarations, binding
hooks and field uses are guarded. Local/borrowed receivers, computed arguments,
custom models, aliases, extra eager effects and unknown binding remain unreviewed.
Models, rendering, Ajax outcomes, visibility, private contents and later state stay
unknown. Existing diagnostics and snapshots share these facts and the exact later
blocker with unchanged tools, parameters, selectors, fields and schemas. See
[the contract](fluent-component-configuration.md).

[Owned table-reference forwarding](owned-table-forwarding.md) now reviews the
single unchanged table argument from a fresh unattached source owner into the
exact AbstractToolbar constructor. Existing constructor snapshots and effect
checks are reused. Structure, validation and component-context diagnostics retain
that finding and the next independent blocker. Private nodes, runtime toolbar IDs,
cache/attachment state, deferred models and later callbacks remain unknown;
selectors and schemas are unchanged.

[Deferred table-columns reads](table-columns-read.md) now permit the exact final
DataTable getter and size-only use when the reviewed owned table retains exact
JDK List.of provenance. Internal borrowed-reference tracking preserves the guard
through locals; custom collections, element queries, mutation and escapes remain
unreviewed. Shared native/MCP diagnostics retain the finding and later blocker.
Column count/elements, models, rendering, cache/attachment state and callbacks stay
unknown. No selectors, schemas or private nodes are added.

[Null-guarded owned toolbar caches](owned-toolbar-cache.md) now permit one private
source cache assignment and exact same-table registration after the existing
constructor/ownership audits. Scoped semantic reference search rejects aliases,
competing writes and escapes; private-cell replacement retains its own effect
audit. Shared native/MCP diagnostics expose the completed fact and later blockers.
Runtime cache presence/reuse, IDs, private contents, models and callbacks remain
unknown. No nodes, selectors, response fields or schemas are added.

Declaration-scan reuse now lets the original DataTablePage complete initial source
setup within the unchanged work budget: 26 inferred matches and 9 branch findings.
Default native/MCP views share the result. The optional combined page/navigation
response retains its 512-KiB bound and currently returns RESPONSE_TOO_LARGE for this
page. See [the declaration-work contract](declaration-work.md).

The thirty-seventh development milestone adds `wicket_get_navigation_contexts`:
list a compact index, then retrieve one exact existing navigation projection by
its snapshot-bound contextId. This makes all seven DataTablePage contexts
available within the unchanged 512-KiB cap. Existing tool defaults, schemas,
semantic uncertainty and native discovery bounds are preserved. See
[the retrieval contract](mcp-navigation-contexts.md).
