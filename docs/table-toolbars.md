# Lazy table toolbar content templates

The fifteenth development milestone follows `DataTablePage`'s empty-state
registration. The source setter immediately invokes a content factory and installs
its result in a cached `AbstractToolbar` that is allocated and attached only when
its private field is null. The shared source template has four inferred bindings:

| Toolbar-relative path | Component |
| --- | --- |
| `cell` | `WebMarkupContainer` |
| `cell:content` | `OatEmptyState` |
| `cell:content:title` | `Label` |
| `cell:content:message` | `Label` |

Wicket assigns the toolbar ID through its DataTable counter. These paths are
relative to a symbolic toolbar root, whose ID is null. `emptyDataTable` is the
source host attachment; no complete page-to-toolbar path, current ID or runtime
instance count is supplied. Row count, visibility, colspan, model values and later
state remain unknown. Neither application factories nor callbacks are executed.
The primary page graph and selectors remain separate.

## Source protocol and reused analyzers

Discovery checks a direct source unary factory setter. Its body has a factory
invocation into a local, an optional throwing identity guard, a field-null-guarded
allocation/attachment, a direct `cachedToolbar.cell.addOrReplace(result)` and
`return this`. The source identity guard proof is shared with the immediate
[toolbar factory contract](content-factories.md). It must accept a fresh non-null
result with the forwarded ID; unsupported effects or a rejecting guard withhold
the template.

The cache must be a private instance field with default-null initialization and
one source assignment. Initialization must allocate a final source-owned direct
AbstractToolbar subtype with an owned DataTable and call the framework's
`addBottomToolbar` on that same table. The table must be a private final field with
one direct selected-constructor allocation and attachment. Source overrides of
that framework attachment, borrowed/anonymous/foreign allocations, cache writes
elsewhere and known constructor mutations withhold the template.

The host may be a direct fresh local or an inline allocation passed through the
reviewed setter into a unique page addition. Host ID forwarding and a unique
resource-local placement are required. Reassignment, duplicate source host IDs,
multiple registrations, anonymous dispatch, direct host escapes and known page or
host mutations remain unsupported. Custom markup sourcing is not reconstructed.

The toolbar's selected source constructor must forward its table to AbstractToolbar
and construct a private final plain framework container cell. Component fields,
constructor additions and configuration reuse the shared source declaration
analyzer. The factory is a direct fresh source-owned constructor expression that
forwards its String ID parameter exactly once, rather than an equal hardcoded
literal. Its selected source constructor delegation chain supplies content child
declarations. Content component fields, initializers, aliases, ambiguous child
paths and unsupported control flow require a separate contract.

Conventional named nested companions, markup inheritance and owned-content
projection reuse the existing resolvers and graph matcher. The toolbar and
content retain separate markup providers. Source configuration, bound model
expressions and lifecycle hooks retain locations without evaluating them. Other
constructor/configuration/lifecycle effects remain unknown. All new bindings are
INFERRED and grant no paired-rename authority; existing ordinary analysis keeps
its own editing rules.

## Native navigation

The registration's factory arrow navigates to the private toolbar companion's
content slot, and that slot navigates back to its source factory. Cell and content
child IDs use the normal shared reference graphs in both directions. The scoped
text report identifies its toolbar-relative root, inference and runtime unknowns.
Indexed templates are not appended to the primary text report.

## MCP decision and contract

Extend only the page-structure tool's existing optional
`includeNavigationContexts` output. No new tool, parameter or schema version.
Default structure, validation and component-context views and existing lifecycle
contracts remain unchanged. Tools stay read-only.

`relationship: TABLE_TOOLBAR_TEMPLATE` projects the same native value graph with
binding ancestry/children and an additive `toolbarTemplate` object:

| Member | Meaning |
| --- | --- |
| `kind`, `phase`, `pathScope` | `SOURCE_DECLARED_TABLE_TOOLBAR_CONTENT`, `TABLE_TOOLBAR_TEMPLATE`, `TOOLBAR_RELATIVE` |
| `factoryInvocation`, `initialization` | `source_immediate`, `source_null_guarded` |
| `contextId`, `contentNodeId` | Snapshot-local graph identities |
| `hostClassName`, `hostComponentPath`, `toolbarClassName`, `contentClassName` | Registering host and source component declarations |
| `registrationLocation`, `hostConstructionLocation`, `factoryLocation`, `invocationLocation`, `idLocation` | Factory and supplied ID provenance |
| `cacheLocation`, `initializationLocation`, `toolbarConstructionLocation`, `toolbarAttachmentLocation`, `replacementLocation` | Cache, owned table attachment and cell placement provenance |
| `configurationLocations[]`, `callbackLocations[]` | Opaque configuration, bound expressions and lifecycle declarations |
| `runtimePresence`, `runtimeToolbarId`, `toolbarInstances`, `rowCount`, `visibility`, `colspan`, `modelValues`, `configurationEffects`, `laterTreeState` | `unknown` |
| `factoryExecution`, `callbackExecution` | `not_evaluated` |

Context `className` and `moduleName` identify the registering page; the graph root
component type and `toolbarClassName` identify the source-owned private toolbar.
Bindings have toolbar-relative paths. Template-only paths do not select the
primary hierarchy and contain no generated runtime toolbar index.

The analyzer bounds each discovery session at 20,000 steps, 96 source dependencies,
four contexts, 12 source levels and 128 nodes. Budget exhaustion clears its
contexts, records PARTIAL_ANALYSIS and makes optional navigation discovery
incomplete. Existing indexed caller/source-set/module gates, freshness for source
and resource dependencies, cancellation, the 30-second request deadline and
512-KiB whole-response bound remain in force.

## Acceptance and remaining work

Acceptance includes the real read-only Oat corpus on Wicket 10.11.0 and 11.0.0,
native factory/cell/content ID navigation, shared MCP snapshots, module isolation,
unsaved-source freshness, cancellation, discovery/response bounds and preserved
earlier milestone graphs. An independent WicketTester fixture observes immediate
content installation, another toolbar affecting ID allocation, cache reuse,
content replacement, model-dependent message visibility and empty/populated table
visibility. Those fixture outcomes are not runtime guarantees in analysis output.
Verification passed **2,814 normal tests**, **18 actual MCP host tests**,
**327 focused regressions** and **22 release-checker unit tests**, with zero
failures/errors/skips. Both Oat corpus versions passed; all 36 ordinary page
summaries and all earlier milestone template summaries match the previous build
exactly. All 36 page resources remain paired and all 318 application input hashes
are unchanged. Plugin configuration, structure and binary compatibility checks
passed against **IU-262.10968.63**, retaining the previous five deprecated/six
experimental API notices. The full normal build/verification run took **22m 7s**.
Source, test and artifact receipts match the resulting ZIP. See the
[evidence summary](wicket-oat-toolbar-evidence.json) for hashes and counts.

General table column/cell populations, arbitrary toolbar cache protocols, factory
blocks/method references, unrestricted component fields and helpers, CRUD
save/delete callbacks, runtime state and shared/property-aware rename remain later
work. This development build is not installed or published automatically.


[Context-aware completion](context-completion.md) now reuses supported graphs from
this milestone for parent-scoped Java/HTML ID suggestions. Its separate editor
probe does not change the normal inspection, fix, paired-rename or MCP contracts.
Inferred candidates remain labelled with their source context and uncertainty.
