# MCP component context

`wicket_get_component_context` selects a component from the same current graph
used by [page structure](mcp-page-structure.md) and [validation](mcp-validation.md).
It adds a projection of the existing export, with no new parser, binding rules,
runtime dependencies, UI actions, or model evaluation.

Development source-layout support also uses this projection: transparent markup
bindings follow the proved Java hierarchy, preserving original HTML locations.
A matched custom component may still expose unresolved owned contents. Read its
findings and scope flags independently of successful path selection. See the
[source-layout contract](source-layouts.md); the response schema remains **1**.

Development [fragment support](fragments.md) adds optional definition-reference
provenance and per-instance markup projections through the same graph. Uncalled
factories can expose a definition reference without an attached component. The
existing tools and schema 1 are retained; see the linked additive-field contract.

Development [factory support](factories.md) follows proved source callbacks,
composes owned markup and preserves generated populations as **INFERRED**
templates. Optional `sourceSetupExpanded`, `ownedMarkupPath` and markup
`markupProviderNodeId` metadata reuse the shared schema-1 export. Runtime IDs,
counts and post-event hierarchy remain unknown; existing requests remain compatible.

[Named nested companions and navigation discovery](nested-navigation.md) support
independent member targets such as `example.Outer.Fields`. To find bounded caller
contexts, request `includeNavigationContexts: true` on the structure tool, then
fetch the indicated caller's fresh structure before selecting its node/path here.
Discovery completion is independent of binding/runtime completeness; unproved
candidate uses have no invented attachment. This tool's request is unchanged.

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

## Request and selection

```json
{
  "projectPath": "<open-project>",
  "className": "example.CustomerPage",
  "moduleName": "web",
  "sourceSet": "main",
  "componentPath": "customerForm:email"
}
```

`projectPath` is injected and validated by JetBrains' host. Class/module/source-set
selection follows the existing tools: only Java source Page, Panel, or
FormComponentPanel subclasses in the selected project; sourceSet defaults to
`main`, with `test` and `any` also supported. Exact module selection disambiguates
classes; ambiguous classes are rejected.

Supply **exactly one** selector:

- `componentPath`: colon-separated IDs relative to the graph root. An empty
  string selects the root. Each segment must have a unique observed child;
  duplicate prefixes are rejected even if later segments would disambiguate.
  An unknown sibling ID prevents unique path selection. Paths are case-sensitive
  and are not trimmed or decoded. Empty interior segments are invalid.
- `nodeId`: exact ID from a freshly fetched page structure. This supports
  duplicate IDs, IDs containing colons, unknown IDs, detached components, and
  logical template nodes. IDs are snapshot-local graph identities, **not durable
  handles**; after edits fetch the structure again. No ID pinning across requests
  is promised. A node that is no longer present produces `COMPONENT_NOT_FOUND`.

Selectors are limited to 4,096 characters. Component paths select observed Java
nodes, not HTML-only findings. Use validation or structure for HTML-only entries.
Selecting a path does not assert that the runtime hierarchy is complete or that
an observed node will execute; confidence, findings, and scope flags still apply.

Repeater item templates consume no path segment: `rows:name` selects the template
child, following the existing [repeater convention](queueing-repeaters.md).
The response preserves the logical item in ancestry and parent links. Select
the item itself by node ID. Runtime item IDs, row counts, and paths such as
`rows:0:name` are not inferred.

## Response

Success is one MCP text block containing JSON with the shared schema **1**
envelope (`tool`, `status`, `truncated`, `target`, `snapshot`) and a `context`
object. `status: ok` means selection completed, not runtime validity.

| Field | Meaning |
| --- | --- |
| `analysisSchemaVersion` | Existing export schema, currently **1**. |
| `component` | Exact exported component, including type, ID, confidence, source locations, parent identity, diagnostics, matcher status and template/queue metadata where available. |
| `componentPath` | Unique observed logical path; empty for root, null for unknown, ambiguous, detached or item-template addresses. |
| `parent` | Exact exported direct parent, or null when unavailable/root. |
| `ancestors` | Exported ancestors in root-to-parent order, retaining item templates. An unresolved chain can be partial. |
| `children` | Exact exported direct graph children, including logical item templates. No recursive subtree expansion. |
| `hierarchyResolved` | Whether the observed ancestor chain reaches the graph root. This is separate from confidence/runtime attachment. |
| `findings` | Exact existing findings whose `javaNodeId` equals the selection; causes, severities and locations unchanged. |
| `markupElements` | Exact markup elements referenced by those findings' `markupNodeId`. These are matcher associations, not a new binding claim. Equal ID strings alone do not create a link. |
| `markupPath` | Existing resolved companion resource, including for the root, which has no corresponding element. |
| `matchingAvailable`, `markupAvailable` | Explicit availability flags. |
| `scopeComplete` | Unchanged `java`, `markup`, `matching` flags; unavailable markup/matching is null. |
| `warnings`, `markupDiagnostics`, `documentDiagnostics` | Existing page-wide diagnostic channels, retained because they can affect the selection. Component/ancestor and associated-element diagnostics remain inside their exported objects. |

Structured Wicket models and property expressions are absent from the current
graph. The response omits model information; it does not inspect values or infer
`PropertyModel` bindings. Missing markup and partial analysis remain explicit.

## Freshness, bounds and errors

The shared request pipeline refreshes source roots and physical dependency
archives, waits for smart/committed PSI, captures a value-only report, and checks
freshness after serialization. Unsaved committed editor text is analyzed without
saving. Requests have the same 30-second budget, cancellation/disposal behavior,
5,000-component/10,000-markup-element limits and 512-KiB UTF-8 response limit.
Oversized responses fail rather than truncate. Sidebar and clipboard state are
untouched. No cross-request graph cache is introduced.

In addition to the [shared errors](mcp-page-structure.md#errors), selection can
return `COMPONENT_NOT_FOUND`, `AMBIGUOUS_COMPONENT`, or
`UNRESOLVED_COMPONENT_PATH`. Invalid selectors return `INVALID_REQUEST`.

Development branch merging retains optional/conflicting typed parent-path variants
with `conditional: true`, unresolved confidence and `UNRESOLVED_BRANCH` findings.
A unique observed path can select such a node for inspection without confirming a
binding; its `markupElements` remains empty. Type/parent alternatives with the
same path require a current node ID. Existing source locations include all
alternative origins, and no runtime branch or model value is inferred.
Reviewed compound `&&`/`||` checks over stable flags reuse input facts through
the same core engine. Existing inferred/confirmed findings and conditional
metadata describe the result; proof groups and runtime flag values are not
exported. Stable integral threshold comparisons now share those predicate facts;
broader numeric relations remain outside the subset.
Stable primitive boolean equality and eager operators use those same facts after
the required operand audit. Context paths, inferred template confidence, optional
presence and origin/editing guards are unchanged. No new tool or response field
is added, and boxed/reference equality remains unsupported.
Integral threshold facts can confirm common bindings or retain optional ones
through the same core graph. Known primitive bounds do not supply runtime numbers
or computed IDs. Per-input intervals/exclusions now combine thresholds and remove
impossible setup paths. Relations between distinct unknown values remain
conservative, and no numeric proof state is added to the response.
Computed-containing components add `computedId: true`, `idRenameSafe: false`
and typed `idOrigins` (location plus LITERAL/COMPUTED kind). These are exact
shared exporter values. Mixed alternatives distinguish whole literals from
whole computations; no substring edit or runtime expression execution is implied.
Queued branch components use the same context selection and exported fields.
`queuedInNodeId` is the declaring scope; a proven `parentNodeId` is the actual
markup parent. A known parent does not confirm an optional component's presence:
its conditional flag, unresolved confidence and branch findings remain. Ambiguous
or changing placements retain `UNRESOLVED_QUEUE`, and internal path membership is
not exported. Request a current node ID for ambiguous structural alternatives.
Reviewed repeater branches preserve logical paths such as `rows:name`, including
nested transparent items. Common children retain `INFERRED` matching confidence;
optional children/items stay conditional and have no confirmed markup association.
Use node IDs for overlapping repeater kinds or item alternatives. Logical items
have no independent binding finding or runtime ID; counts/model data stay unknown.
Tool-owned failures use MCP `isError: true` and the shared versioned error
envelope, naming `wicket_get_component_context`. Caller cancellation propagates.

## Verification and reproduction

The focused run passes **77 tests**, including **22 new context cases** covering
nested paths, sibling ambiguity, unknown IDs, detached/cyclic graphs, node-ID
selection, root context, exact export/markup identities, missing markup,
uncertainty, repeater templates, UTF-8/graph limits, stale edits, deterministic
read-only responses and the shared async pipeline without sidebar publication.

The complete suite passes **1,631 tests**, with no failures, errors or skips.
The installable ZIP contains one WicketLens JAR; all 16 MCP class files and the
optional descriptor match the current build. No MCP/transport/Wicket/Spring
libraries are bundled.
Packaging and project/structure checks pass. Binary verification against the
target IDEA build is **Compatible**, with the existing four deprecated and
six experimental API notices unchanged.

Use the existing isolated host and standard-library client:

```sh
./gradlew runMcpSpike -PmcpStructureFixture=true \
  -PmcpSpikeTools=wicketlens_mcp_probe,wicket_get_page_structure,wicket_validate_page,wicket_get_component_context
python3 tools/check_mcp_context.py
python3 tools/check_mcp_structure.py
python3 tools/check_mcp_validation.py
```

The fixture includes two modules, nested fields, a repeater template, duplicate
IDs and unresolved setup. The context checker compares objects with live
structure, checks selection errors, temporarily edits/restores a Java/HTML pair,
and verifies immediate external-edit freshness and original source checksums.
These checks pass against IDEA **2026.2.3 / IU-262.10968.63**, alongside the
existing structure/probe and validation protocol checks. Installed Codex CLI
**0.166.0** successfully invokes only the context tool, with no command or
file-edit tools, and reports the real type, parent, markup and uncertainty.
The portable [evidence record](mcp-context-evidence.json) contains the actual
protocol responses and completed Codex call with fixture paths redacted.
The isolated host is stopped; normal IDE/client settings are unchanged.
For an actual Codex call, follow [the integration guide](mcp-integration.md)
with only `wicket_get_component_context` enabled and request `example.McpPage`,
module `mcp-fixture`, path `form:name` in the disposable fixture project.

Phase 2's three semantic tools are implemented. Phase 3 now verifies
[lifecycle/concurrent requests](mcp-reliability.md) and
[multiple projects, larger fixtures and transport behavior](mcp-isolation-transport.md).
[Graphical/client setup](mcp-client-setup.md) and [preview release checks](release-checklist.md)
are now implemented for the pinned versions. Broader compatibility and retesting
the documented upstream transport limitation remain open.
Real-application accuracy/performance evaluation remains separately deferred.

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


Plain-page initial attachments beside a reviewed fresh source action registrar
can expose optional `storedActionRegistrations` metadata: allocation, registration,
method, callback and private slot locations. Only direct storage without invocation
is proved. These bindings remain `INFERRED`, grant no rename authority, and keep
active registration, dispatch, visibility, later tree state and business outcomes
unknown. The existing separate `ACTION_TEMPLATE` protocol is unchanged. See the
[storage and attachment contract](stored-action-attachments.md).


The same `storedActionRegistrations` shape now supports up to 64 direct storage
registrations per fresh local after every local use passes a complete group
audit. Shared allocation/slot locations identify source sites; they do not imply
concurrently active callbacks or reveal the currently stored callback. A later
eager call, alias or escape withholds the group. Sibling edits invalidate the
snapshot and its action selectors. Existing per-context limits and the 512-KiB
response cap still apply. See [the repeated-registration contract](repeated-action-registrations.md).


Standalone fluent chains now use the same complete registrar-group audit. Every
link must store the callback and return its exact receiver. The 64-site limit
counts links across all uses. Each chain entry's `registrationLocation` points to
its method-name token; action contexts reuse that location from the primary
snapshot. Other metadata shapes and standalone-call locations are unchanged.
Active callback/dispatch state and outcomes remain unknown; bindings remain
inferred with no rename authority. See [the chain contract](chained-action-registrations.md).


A constructor local may now be initialized by `new Registrar().addAction(...)`
under the same storage/receiver-return protocol. Initializer links and every
later use share one complete audit and the 64-site bound. Initializer entries
point to individual method-name tokens, including single-link initializers;
allocation locations retain the direct new expression. Existing value shapes,
confidence, freshness, response caps and unknown active callback/dispatch state
are unchanged. See [the initializer contract](initialized-action-registrations.md).
