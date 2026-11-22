# Tree node and content templates

The ninth development milestone adds source-declared tree contexts for
`AccountsPage`'s `folders` (OatTree) and `accounts` (OatTableTree). Each host has two
contexts, conditional on the content constructor's selection callback being null
or non-null. These are preconditions for possible templates, not observations of
runtime state or predictions from the page's configuration calls.

Each context has **eight MATCHED / INFERRED bindings**: junction, content, link,
plain, and an icon/label pair beneath each label container. Four contexts give
32 bindings across separate uses of the same source. The active label declaration
moves between `link` and `plain`; its placeholder moves to the other parent.
Both parent choices remain available for navigation.

The icon factory preserves its ID across two constructor types. Such a node
exports the factory's declared `componentType` plus `possibleComponentTypes`.
It does not select a concrete type using unknown model values. Constructor,
configuration, behavior and callback effects remain unexpanded.

## Node-relative scope

These graphs describe **one logical node**, with no concrete ID at the graph root.
Paths such as `content:plain:label` are relative to that node. The host's separate
`hostComponentPath` identifies its source-declared page attachment (`folders` or
`accounts`). It is not a prefix that can be joined to a node-relative path to
construct a runtime path.

Wicket owns the intervening subtree/branch items or table rows/cells, assigns their
IDs, and populates descendants according to providers and expansion state. The
analysis neither enumerates recursive nodes nor invents those IDs, depth, counts,
expansion state, selection, visibility, model values or table-column contents.
The initial report remains unchanged. The templates have incomplete scope and
provide no editing authority.

## Reviewed source contract

The shared tree analyzer requires a directly constructed source NestedTree or
TableTree subtype, a known forwarded String host ID and one direct page-constructor
attachment. It rejects ambiguous host IDs, conditional attachments, reassignment,
foreign/fluent receiver changes and known structural mutations. Other page effects
and later execution remain explicitly unknown.

The source `newNodeComponent(String, IModel)` override must return a fresh Node
subtype and preserve the supplied ID, owning tree and model. A source `createContent` bridge forwards its
ID/model directly to the owning tree's `newContentComponent`. Direct static source
forwarding may lead to a fresh markup-owning content Panel. Returned component
escapes, ID reassignment, structural factory effects, custom Node initialization,
unsupported dispatch and custom markup sourcing are withheld.

The junction factory must preserve its supplied ID. Wicket Node initialization
provides the junction/content attachments; its conventional resource must contain
exactly the reviewed top-level slots. The exact Wicket **10.11.0** and **11.0.0**
AbstractTree, NestedTree, Subtree, TableTree, TreeColumn, Node and Node markup
contracts were inspected locally. Those source files are identical between these
versions; their hashes are recorded in the evidence summary.

Constructor identity tracing reuses the existing analyzer with scoped reviewed
NestedTree/TableTree/Node constructor contracts. The scoped trace also admits a
fresh non-component data argument with literal arguments and no anonymous receiver,
needed for the source tree's fresh model delegation. Default identity tracing is
unchanged; this is ID provenance, not constructor-effect or model-value proof.

Content constructors use direct fresh component locals and additions. A component
alias may choose two known locals by a stable constructor-parameter null comparison.
Repeated predicates share that parameter's identity. Each bounded nullness
combination gets its own graph. Duplicate IDs, unknown parents, component escapes,
reassignment and known structural changes are rejected. Child source factories may
return bounded fresh alternatives only when every alternative preserves one known
ID. Private inputs, arbitrary runtime control flow and nested owned component
internals need other contracts.

The implementation reuses component identity, source constants, conventional
source/dependency markup extraction, graph matching, immutable PageReport values,
indexed navigation discovery, component-context projections and freshness snapshots.
No Oat class-name allowlist or MCP-specific binding analyzer is added.

## Native navigation

Ordinary content IDs navigate both ways between their declarations and applicable
markup slots. A shared declaration can have targets under both conditional parents.
Factory method names navigate to the node resource or its content/junction slot.
Class-name navigation keeps its Java behavior.

The dependency Node markup can navigate back to source content/junction factories.
This uses bounded source Node inheritors and the existing indexed source-context
discovery, including one additional source-tree host hop. It is scoped to the
resource's actual dependency archive and visible source resources. Incomplete or
ambiguous discovery supplies no extra native targets. Cached graphs invalidate
on PSI, source-root or VFS changes; indexing and uncommitted documents are guarded.

Extra contexts supply navigation only. Initial sidebar, inspections, completion,
quick fixes and paired rename retain their existing snapshots and guards.

## MCP decision and response contract

Extend the existing optional `wicket_get_page_structure` output with
`includeNavigationContexts: true`. No new tool, request parameter or schema version
is needed. Default structure, validation and ordinary component-context selection
keep the initial graph; template-only node paths do not select it.

`navigationContexts.contexts[]` can contain `relationship: TREE_TEMPLATE` and an
additive `treeTemplate` object:

| Member | Meaning |
| --- | --- |
| `kind`, `phase` | `SOURCE_DECLARED_TREE_POPULATION`, `POPULATION_TEMPLATE` |
| `pathScope` | `NODE_RELATIVE`; context paths start at a logical node, not the page |
| `contextId` | Snapshot-local host/precondition identity |
| `frameworkKind` | `NESTED_TREE` or `TABLE_TREE` |
| `hostClassName`, `hostComponentPath`, `hostLocation` | Source host and its independently declared page attachment |
| `nodeClassName`, `contentClassName` | Source node and content types |
| `nodeFactoryLocation`, `junctionFactoryLocation`, `contentFactoryLocation` | Source factory declarations |
| `conditions[]` | Parameter name, declaration location and required `NULL`/`NON_NULL` value for this template |
| `runtimePresence`, `runtimeNodeIds`, `nodeCount`, `expansionState`, `selectionState` | Always `unknown` |

Scopes/bindings contain parent, ancestors and children inline through the same
component-context projection. `hierarchyResolved` describes a node-relative source
graph, with no page-to-runtime-node path guarantee. Component
`possibleComponentTypes` is optional and appears only for a reviewed source factory
with multiple concrete constructor types and one preserved ID.

All tools remain read-only. Semantic withholding uses existing
`lifecycleDiagnostics`; discovery completeness remains separate from semantic
certainty. Shared version snapshots include source helpers and dependency archives;
unsaved source/resource edits invalidate old results. The existing 30-second
request deadline and whole-response **512 KiB** limit apply. Tree analysis permits
12,000 source/call steps, 64 sources, 8 contexts, 12 forwarding/ancestry levels and
64 nodes per context. Bound exhaustion publishes no partial tree discovery.
Indexed navigation retains its shared reference/candidate/binding bounds.

## Verification and remaining scope

The read-only Oat corpus asserts all four contexts, eight inferred bindings each,
parent preconditions, possible icon types, native navigation, MCP output and
preserved initial reports against Wicket 10.11.0 and 11.0.0. Previous Inbox,
Products, wizard and layout milestones remain covered.

An independent Wicket 11 WicketTester process renders nested/table populations,
checks Node initialization and both label/icon alternatives, and expands/collapses
a root using provider-backed nodes without assuming any concrete generated ID.
Those fixture results do not predict arbitrary application state.

PSI/MCP tests cover IDs, factory/parent uncertainty, escapes and mutations,
initialization overrides, conditional attachment, literal values that must not impersonate forwarded IDs, module isolation, source/resource
freshness, cancellation, context/node/response bounds, native factory/dependency
navigation and read-only/default-output behavior. The actual bundled MCP host also
serves the optional tree contexts over HTTP. See the
[evidence summary](wicket-oat-tree-evidence.json) for final counts and artifact hashes.

Arbitrary providers, recursive populations at concrete depths, customized framework
population/lifecycle methods, table columns/toolbars, selection/expand callbacks,
CRUD behavior and model/property-aware refactoring remain later work. No live IDE
installation, graphical release-readiness evaluation or publication is implied.


[Context-aware completion](context-completion.md) now reuses supported graphs from
this milestone for parent-scoped Java/HTML ID suggestions. Its separate editor
probe does not change the normal inspection, fix, paired-rename or MCP contracts.
Inferred candidates remain labelled with their source context and uncertainty.

## Recursive NestedTree declaration

A reviewed `NestedTree` using the framework's inherited `newSubtree` method
can expose `childSubtree` metadata. It links the recursive framework
subtree to the same source node and content factories used for the logical
node template. A custom subtree override leaves this relation absent.
Runtime node IDs, expansion, depth and counts remain unknown to source analysis.
