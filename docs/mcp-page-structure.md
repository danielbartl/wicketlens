# MCP page structure: Phase 2 first slice

`wicket_get_page_structure` analyzes a source Wicket `WebPage`, `Panel`, or
`FormComponentPanel` using the existing analysis engine and JSON exporter. It
does not require an active editor or a sidebar report. The optional JetBrains
MCP integration and minimum IDE build are unchanged from
[Phase 1](mcp-integration.md).

Development [fragment support](fragments.md) adds optional definition-reference
provenance and per-instance markup projections through the same graph. Uncalled
factories can expose a definition reference without an attached component. The
existing tools and schema 1 are retained; see the linked additive-field contract.

Development [factory support](factories.md) follows proved source callbacks,
composes owned markup and preserves generated populations as **INFERRED**
templates. Optional `sourceSetupExpanded`, `ownedMarkupPath` and markup
`markupProviderNodeId` metadata reuse the shared schema-1 export. Runtime IDs,
counts and post-event hierarchy remain unknown; existing requests remain compatible.

Development [nested companion and navigation support](nested-navigation.md)
accepts named member source targets such as `example.Outer.Fields`, pairing
`Outer$Fields.html`. Optional `includeNavigationContexts: true` adds a sibling
`navigationContexts` object with bounded own, caller, inherited and unproved
candidate contexts. It reuses exact graph/context projections, source isolation,
freshness and response limits. Default false keeps the existing response shape.
`discoveryComplete=false` and `truncated=true` expose incomplete context discovery;
the primary analysis graph remains intact. See the linked contract for bounds,
source-set selection, snapshot-local IDs and limitations.

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

## Request

```json
{
  "projectPath": "/absolute/path/to/open/project",
  "className": "example.McpPage",
  "moduleName": "mcp-fixture",
  "sourceSet": "main"
}
```

| Parameter | Contract |
| --- | --- |
| `projectPath` | JetBrains' host-provided project selector. Use the exact open project path. The tool obtains that project's coroutine context; it does not select an unrelated active project. |
| `className` | Required Java qualified class name, at most 512 characters. Use dots for nested source classes. File paths and library-only targets are rejected. |
| `moduleName` | Optional exact IntelliJ module name, at most 256 characters. It restricts source lookup to that module, excluding dependency-module declarations. |
| `sourceSet` | `main` (default), `test`, or `any`. Test sources are never an implicit fallback for `main`. |

The target must belong to a configured Java source root in the selected project.
Attached library sources, compiled classes, resource-only roots, and arbitrary
filesystem paths are not targets. Multiple matching declarations produce an
ambiguity error. A module/source-set choice resolves normal duplicate-class
layouts; duplicates that remain within that choice are still rejected.

The first slice uses conventional default markup. An editor-selected locale or
style variant is not implicitly used. Markup inheritance, queues, repeaters,
custom inputs, compiled constants, and other existing supported analysis flow
through the same engine; unsupported patterns retain the same uncertainty.

The [source-layout pass](source-layouts.md) additionally recognizes audited
decorated components, constructor delegation, initialization fields, concrete-page
hooks and transparent-container bindings. An attachment can match while its
component-owned, fragment, or repeated contents remain unresolved. These facts
reuse the existing graph, findings, source dependencies and schema 1.

## Response

Success is one MCP text-content block containing JSON with:

| Field | Meaning |
| --- | --- |
| `schemaVersion` | MCP envelope version **1**. |
| `tool` | `wicket_get_page_structure`. |
| `status` | `ok`: the static analysis completed, including any partial/unresolved findings. |
| `truncated` | Always `false` on success. Oversized reports produce an error instead of a partial graph. |
| `target` | The resolved `className`, `moduleName`, and actual `sourceSet`. |
| `snapshot` | `kind: committed_static_analysis`, `freshness: checked_after_serialization`. |
| `analysis` | The existing deterministic analysis JSON **schema 1**, unchanged. |

`analysis.components` is the existing flat graph, linked by `nodeId` and
`parentNodeId`, with IDs, component types, confidence, and Java/markup locations.
The normal markup tree, matching findings, causes, severities, and warnings are
included. Component paths can be reconstructed from parent links. No model
properties, runtime visibility, or runtime component values are invented.

The [agreeing-branch analysis](conditional-setup.md) reuses this tool and
`wicket_get_component_context`; no additional tool is needed. Merged components
add `branchMerged: true`, `idRenameSafe: false`, and `javaLocations`/`idLocations`
arrays containing every proven alternative origin, including the primary.
Existing singular locations remain representative navigation targets.
Partially agreeing graphs keep common bindings confirmed and export optional or
conflicting typed parent-path variants with `conditional: true`, unresolved
confidence and `UNRESOLVED_BRANCH` findings. Different types or parent chains
remain separate nodes; use a current node ID if a path selects multiple variants.
Unknown ternaries share this exact branch engine, export and exploration budget;
alternative source origins and conditional flags need no new MCP fields.
Stable primitive boolean parameters, unchanged aliases/snapshots and negation now
reuse path facts, including through reviewed reads. This can confirm complementary
checks without exposing runtime flag values or changing the graph schema. Local
proof state is discarded after each analysis; independent values stay distinct.
Reviewed compound `&&`/`||` predicates now preserve those stable input
relationships, including aliases, negation and reviewed accessors. Complementary
predicates can confirm common bindings under the existing bounded replay.
Distinct-input numeric relations and untracked comparisons remain outside this slice.
Groups and input facts are not exported; the existing merged origins,
conditional flags, confidence and causes describe the result without new fields.
Primitive boolean `==`/`!=` and eager `&`/`|`/`^` now reuse those same proof groups
after all required source operands pass their audit. Shared origins, confidence
and unresolved variants describe the result. Boxed/reference equality and
unsupported numeric comparisons retain their existing limits. No tool,
response field or schema change is needed; internal facts remain local.
Stable integral inputs compared with known reviewed constants now share repeated,
reversed, complementary and adjacent-threshold facts. Primitive bounds and long
endpoints are respected without exporting runtime numbers. Direct aliases,
implicit widening and reviewed accessors/storage preserve identity; independent
unknown arithmetic and different inputs stay distinct. Per-input intervals and
excluded equality values now connect thresholds, removing impossible paths and
settling implied tests within the existing budget. No runtime numeric value is
supplied to scalar evaluation, even for a singleton range. Existing origins,
confidence and conditional/uncertainty fields
express the results; numeric identities and threshold keys are not exported.
Intervals and exclusions are also internal. Distinct unknown inputs and symbolic
arithmetic remain conservative; the tool set and schemas stay unchanged.
Known computed ID values now participate in this same merge. Any
computed-containing component (merged or ordinary) adds `computedId: true`,
`idRenameSafe: false` and an `idOrigins` array. Each origin has a `location`
(`file`, `offset`) and `kind: LITERAL | COMPUTED`; COMPUTED locations identify
whole calculations or constant-use expressions, never editable fragments.
Mixed origins retain their individual kinds regardless of primary-path order.
Existing `idLocations` on merged components remain unchanged, literal-only
exports keep their shape, and schema versions remain 1.
Queue placement is checked on each feasible setup path using the shared resolver.
Existing `queuedInNodeId` records the declaring scope and `parentNodeId` records
the actual parent when proven consistently. Optional queues retain `conditional`
and unresolved branch findings even when their parent is known. Ambiguous or
changing placements retain `UNRESOLVED_QUEUE`. Internal path membership/counts
are not exported; the response schema and tool set are unchanged.
Reviewed repeater templates merge through this same engine. Existing `repetition`,
`templateItem` and `template` fields retain logical items without runtime IDs.
Common item bindings have `MATCHED` findings and `INFERRED` matching confidence;
optional item structure retains conditional flags and unresolved branch findings.
Neither a common shape nor an optional shape establishes row existence/counts.
Logical items consume no path segment, and overlapping alternatives require node
IDs for context selection. No new tool, schema field or runtime data is added.
`wicket_validate_page` projects the same shared findings. An agreed graph does
not prove which branch runs or what caption/model values it supplies.
All branch source files participate in the existing freshness check, and the
normal response bounds and read-only/project selection rules remain in force.

`status: ok` does not mean that every component is paired or that the runtime
tree is complete. Preserve the distinctions between `MATCHED`, informational
`JAVA_ONLY`/`MARKUP_ONLY`, and unresolved findings. A real, path-redacted response
is included in [the evidence record](mcp-structure-evidence.json).

As with Copy Wicket Context, locations can include absolute source/markup paths
and relevant classpath resource locations. Internal dependency/version lists
are not added to the export. An agent may send returned metadata to its model
provider according to its own configuration.

## Freshness, bounds, and side effects

Each request asynchronously refreshes the selected project's local source roots
and dependency class roots/archives before class lookup. SDK roots are not
recursively refreshed. This makes immediate agent disk edits and new source
files visible to the IDE's VFS/index. It does not save documents or change source
text. IntelliJ handles conflicts with unsaved editor buffers using its normal
document synchronization policy.

The existing engine then runs under smart-mode and committed-document read
constraints. Only values leave the read action. Serialization reuses
`WicketContextExporter`; the captured `ReportVersion` is checked afterward,
including pending editor changes and source/binary/JAR/root versions. A changed
project produces `STALE_SNAPSHOT`, not a successful stale response. This is a
snapshot at the final check, not a live view; invoke the tool again after edits.

The request has a cooperative **30-second budget**, including refresh and waits.
Caller cancellation propagates. Success is limited to **512 KiB of UTF-8 JSON**,
5,000 component nodes, and 10,000 markup elements, in addition to the core's
existing analysis budgets. Large responses are rejected explicitly; graph JSON
is never cut in the middle. These limits do not turn partial core analysis into
a complete runtime claim.

The tool does not replace the sidebar report, navigate, copy to the clipboard,
run configurations, generate code, or modify project sources. Normal IDE VFS
events can invalidate an already displayed sidebar snapshot when external files
actually changed. The analyzer, model, matcher, exporter, editor features, and
Spring workflows remain unchanged.

## Errors

Tool-owned errors below are distinct from transport failures. The targeted
bundled host can expire long blocked sessions with HTTP 404 before an error
envelope is delivered; wire cancellation is not prompt in this SDK. See
[the verified behavior and reconnection guidance](mcp-isolation-transport.md#wire-cancellation-observed-host-limitation).

Tool-owned failures are MCP results with `isError: true` and JSON envelope
`schemaVersion: 1`, `tool`, `status: error`, and `error: {code, message}`.
Messages do not expose raw exception details or unrelated file paths.

| Code | Meaning/action |
| --- | --- |
| `INVALID_REQUEST` | Invalid class name, module name, or source-set value. |
| `MODULE_NOT_FOUND` | The requested exact module does not exist in this project. |
| `CLASS_NOT_FOUND` | No eligible Java source matches the chosen scope. |
| `AMBIGUOUS_CLASS` | Narrow the module/source-set choice or resolve remaining duplicate declarations. |
| `UNSUPPORTED_TARGET` | The selected declaration is not a supported Wicket root, or its Wicket types cannot resolve. |
| `ANALYSIS_UNAVAILABLE` | Source/dependency data became unavailable or could not be analyzed. |
| `STALE_SNAPSHOT` | Files, documents, PSI, or roots changed during the request; retry. |
| `RESPONSE_TOO_LARGE` | Analyze a smaller source component separately. No graph was truncated. |
| `TIMED_OUT` | Refresh, indexing, document commits, or analysis exceeded the cooperative budget. |
| `PROJECT_UNAVAILABLE` | The selected project is unavailable or closed. |
| `ANALYSIS_FAILED` | Unexpected failure; consult the IDE log. |

The internal capture API also guards `INDEXING` and `UNCOMMITTED_DOCUMENTS`;
normal MCP calls wait for those conditions rather than reading unsafe PSI.
JetBrains' own project-selection, argument-decoding, transport, and cancellation
errors remain host errors; they are not rewritten into WicketLens envelopes.

## Verification and reproduction

The 32 adapter fixtures cover equality with the existing export, Page/Panel/
FormComponentPanel roots, uncertainty, missing/unsupported/library/out-of-root
targets, request validation, exact module and source-set selection, ambiguity,
stale Java/HTML/roots, pending editor commits, cancellation, deterministic/no-write
behavior, unchanged sidebar state, and response byte/graph limits. These fixtures
run with the optional MCP Server plugin disabled.

The full regression run passes **1,586 tests**, with no failures, errors, or skips.
Packaging and project/structure checks pass. Binary verification against IDEA
262.10968.63 is **Compatible**, with the existing four deprecated and six
experimental API notices. Current MCP classes and the optional descriptor match
the ZIP, which contains one plugin JAR and no added framework libraries.

Start the existing isolated harness with a disposable, configured two-module
Wicket project, real Java 25 SDK, and existing fixture libraries:

```sh
./gradlew runMcpSpike -PmcpStructureFixture=true \
  -PmcpSpikeTools=wicketlens_mcp_probe,wicket_get_page_structure
python3 tools/check_mcp_structure.py
```

Wait for the host's server-started message before the Python call. The generated
project is `build/mcp-structure/project`; its modules are `mcp-fixture` and
`mcp-other`, both declaring `example.McpPage`. The Python client reuses the Phase
1 HTTP client. It checks module selection, ambiguity and other errors, three
matched bindings and their parent link, repeatability, the original probe,
immediate external Java/HTML edits, and a newly created source pair. Temporary
fixture edits are restored and source checksums are verified. The fixture
preparer refuses to overwrite an unmarked, nonempty project.

Codex CLI 0.166.0 also successfully invoked the final tool once, with ephemeral
settings and only this semantic tool enabled, then reported `title`, `form`, and
`form:name`, all MATCHED, with the response's partial-static-analysis caveat.
No file or command tools were used. See [the evidence](mcp-structure-evidence.json).
Client configuration is the same as [Phase 1](mcp-integration.md), substituting
the semantic tool name, the fixture project path, and these class/module inputs.
Stop the test host with Ctrl+C. Normal IDE/client settings are unchanged.

## Remaining MCP work

The [validation tool](mcp-validation.md) now shares this request pipeline and
projects existing findings without changing this structure response.
[Component context](mcp-component-context.md) now selects from the same graph.
Phase 3 now verifies [lifecycle and concurrent requests](mcp-reliability.md) and
[multiple projects, larger fixtures and transport behavior](mcp-isolation-transport.md).
The [setup guide](mcp-client-setup.md) and [release checklist](release-checklist.md)
now cover the pinned graphical/client combination. Broader SDK/client matrices
and retesting the upstream transport limitation remain open. Real-application
accuracy/performance evaluation remains deferred.

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


[Immediate content factories](content-factories.md) extend only the existing
optional `includeNavigationContexts` projection. `relationship: FACTORY_TEMPLATE`
contains native source graph bindings with ancestry/children. Lifecycle
`kind: SOURCE_DECLARED_CONTENT_FACTORY`, `phase: REGISTRATION_TEMPLATE` and an
additive `contentFactory` object identify the reviewed immediate unary invocation,
registration/host/ID/placeholder sources and opaque configuration/callback declarations.
`invocation: source_immediate` describes the source setter; `factoryExecution` and
`callbackExecution` stay `not_evaluated`. Runtime presence, configuration effects,
model values, visibility, slot presence and later tree state remain unknown. A
source constructor placeholder is required; HTML alone cannot establish the
replacement slot. Primary selectors,
default structure, validation/component-context views, existing event/selection
contracts and schema version remain unchanged. No new tool or parameter. Shared
project/module/source-set isolation, freshness, cancellation, bounds and read-only
behavior apply. Discovery exhaustion marks optional navigation incomplete.


[Lazy table toolbar content](table-toolbars.md) adds optional
`TABLE_TOOLBAR_TEMPLATE` navigation contexts over the native source graph.
`toolbarTemplate` records `SOURCE_DECLARED_TABLE_TOOLBAR_CONTENT`,
`TABLE_TOOLBAR_TEMPLATE` and `TOOLBAR_RELATIVE`, with immediate source factory
invocation and null-guarded toolbar initialization. Cache/table/cell/factory and
configuration/callback locations retain provenance. Root toolbar IDs are null;
no runtime toolbar index or complete page-to-toolbar path is inferred. Row count,
visibility, colspan, model values, actual instances and later state stay unknown;
factory/callback execution is not_evaluated. All new bindings are inferred and
protected from paired rename. Default views and primary selectors are unchanged.
Shared isolation/freshness/cancellation, indexed discovery and whole-response
bounds and read-only behavior remain. No new tool, parameter or schema version.


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


[Row-triggered dialog/drawer content](event-dialog-content.md) extends optional
`navigationContexts` with relationship `EVENT_TEMPLATE`,
`lifecycle.kind: SOURCE_DECLARED_EVENT_CONTENT` and `phase: EVENT_TEMPLATE`.
`directContent` records host construction, fresh content, guarded setter and
source transfer locations. `triggerComponentPath`, `triggerPopulationContextId`
and `triggerHostComponentPath` identify the separate symbolic row trigger;
`triggerPathScope: SYMBOLIC_ITEMS` never supplies runtime row IDs. The target uses
`pathScope: PAGE_DECLARED_ATTACHMENT`. `rowPreconditions[]` retain required source
predicates, not current results. Runtime rows/count, models, visibility,
validation, open/close state, other event effects and later tree state remain
unknown; callback/configuration execution is not_evaluated. The inferred event
bindings and required ancestry reuse native semantic graphs and are protected
from paired rename. Defaults and initial selectors are unchanged; event-only
paths cannot select the initial hierarchy. Source/module isolation, freshness,
cancellation, discovery and whole-response bounds apply. No new tool, parameter
or schema version is added.


[Native context completion](context-completion.md) reuses these existing facts.
Its caret-specific incomplete-ID probe and insertion behavior remain editor-only;
no completion hypothesis or new field enters this tool's normal snapshots. MCP
schemas, selectors, discovery/freshness/response limits and read-only behavior
remain unchanged.


[Dialog submission templates](submission-templates.md) add optional
`relationship: SUBMISSION_TEMPLATE` contexts with `submissionTemplate` metadata.
`kind: SOURCE_DECLARED_SUBMISSION`, `phase: SUBMISSION_TEMPLATE` and
`pathScope: PAGE_DECLARED_ATTACHMENT` identify source declarations. Host/form/trigger
paths, callback and bridge/dispatch locations, and effects retain source provenance.
Effects identify FEEDBACK, AJAX_REFRESH or fresh CONTENT_REPLACEMENT declarations,
with method, receiver/target path, argument locations and `execution: not_evaluated`.
Their target attachment phase distinguishes initial declarations from newly declared
submission content. All bindings are inferred; ancestors and children are inline.

`dispatchCondition: SOURCE_ON_SUBMIT_ROUTE` does not imply successful validation:
default form processing can be disabled. Validation, default processing, models,
feedback/business outcomes, runtime reachability, bridge/post-callback effects and
later tree/open/close state remain unknown. No callback or business action is
executed. Default responses and selectors are unchanged. The shared native analyzer
and snapshot preserve project/module isolation, freshness, cancellation, discovery
and response bounds, and read-only behavior. This extends the existing optional
structure context contract without a new tool, parameter or schema version.


[Ordinary form callbacks](form-submission-templates.md) reuse SUBMISSION_TEMPLATE
with submissionTemplate.kind=SOURCE_DECLARED_FORM_CALLBACK and
pathScope=OWN_COMPANION_DECLARATIONS. frameworkCallback and dispatchCondition
identify separate SOURCE_ON_SUBMIT_ROUTE / SOURCE_ON_ERROR_ROUTE declarations.
bridgeLocation and dispatchLocation are null for these direct framework hooks.
formCallback records source attachment and constructor locations, the enclosing
or explicit declared form association, and unknown initial hierarchy, inherited
placement, initialization and form-processing effects. Effect targets use
SOURCE_DECLARED_ADDITIONS as their attachment phase. All bindings are inferred.
Default views/selectors and earlier dialog metadata remain unchanged. Route names
establish no validation, business or runtime outcome; callbacks are not evaluated.
The existing project/module, freshness, cancellation, response/discovery-bound
and read-only contracts apply. No new tool, parameter or schema version is added.

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


## Conditional confirmation flow

Navigation discovery can add a separate `CONFIRMATION_TEMPLATE` for one reviewed
source ask-site consumer. Its optional `submissionTemplate.confirmation` records
ask/slot/capture/clear/guard/accept and cleanup locations, plus source data-helper
calls. Initiating dispatch, current registration, slot presence, cleanup
reachability and mutation/business outcomes stay unknown; callbacks are not
executed. Retrieve it with the existing navigation index and selected-context
operation. Default primary views and existing submission metadata retain their
contracts. See [the bounded protocol](confirmation-templates.md).


Confirmation metadata can also include `selectionClears` for the reviewed source
selection helper. It records its helper/guard/getter/model-read/collection-clear
and same-owner Ajax-refresh locations. Column dispatch, model implementation and
value, collection identity/mutability, clear/refresh outcomes and later selection
remain unknown. No helper, model or callback is executed. The member is omitted
from existing row-confirmation contexts. See
[the source declaration contract](selection-clear-declarations.md).


Source constructor callbacks can add `ACTION_TEMPLATE` contexts with a separate
`actionTemplate` object. A reviewed List.replaceAll loop, equality declaration,
canonical record enum-component substitution and shared feedback/Ajax/selection
helper retain their source locations. Registration/storage, visibility, UI
dispatch, row values, loop counts, equality/enum effects and business outcomes
stay unknown. No callback, helper, predicate or constructor is executed;
`uiTrigger="not_reconstructed"` supplies no runtime button/row ID or Form link.
Existing initial, submission and confirmation views keep their contracts. See
[the record-action declaration contract](record-action-callbacks.md).


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
