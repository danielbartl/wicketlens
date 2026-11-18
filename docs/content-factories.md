# Immediate content-factory templates

The fourteenth development milestone follows the search toolbar registered by
`DataTablePage` with `OatDataTable.setToolbar`. The source setter invokes the unary
factory with the `toolbar` ID and replaces that slot directly. A separate source
template describes four inferred bindings:

| Logical path | Source component |
| --- | --- |
| `dataTable` | `OatDataTable` attachment |
| `dataTable:toolbar` | Page-provided `Fragment` |
| `dataTable:toolbar:searchForm` | `Form` |
| `dataTable:toolbar:searchForm:search` | `OatSearchField` |

This describes declarations at a reviewed source call. It does not establish the
current runtime hierarchy, execution of the factory or Ajax behavior, filtering,
model values, visibility or later component state. The primary page graph and its
selectors remain separate.

## Source contract

Discovery reuses the existing bounded replacement-context session, constructor ID
tracer, constants, conventional markup resolution, fragment composition and graph
matcher. A source unary factory setter must contain one direct factory invocation
into a fresh local, an optional throwing identity guard, a framework `replace` on
its own receiver and `return this`. The optional guard may only throw
`IllegalArgumentException` and use reviewed String equality/component ID queries.
Bounded Boolean composition must accept a fresh non-null component with the
forwarded source ID; a guard that rejects that result withholds the template.
This uses source constants and allocation facts without invoking application code.
Factory storage, forwarding, conditional invocation, additional setter effects,
foreign replacement receivers and escaping factory/results are unsupported.

The host must have a unique direct local allocation and one direct page attachment
in the selected source constructor. Registration must be direct and unique for
that host/setter. Reassignment, anonymous dispatch, ambiguous source declarations,
known structural page/host mutations and direct host escapes withhold the template.
The selected constructor chain must declare exactly one direct fresh framework
WebMarkupContainer placeholder with the supplied ID. Conditional, missing,
duplicate or wrong-ID placeholders and known constructor mutations are rejected.
A matching HTML attribute alone is insufficient for `replace`. The placeholder
allocation joins provenance and freshness. Other constructor, page and host
lifecycle effects and the slot's actual runtime presence remain unknown.

The factory must return a fresh framework Fragment local from a straight-line
block, forward the supplied ID parameter exactly once, select a literal fragment
definition and use the selected page as provider. Direct fresh component locals
and nested framework additions build the declaration graph. Children use literal
IDs whose constructor forwarding is proved by the shared identity tracer. Reviewed
fluent receiver returns and framework Behavior additions preserve construction
identity. Constructor/configuration effects and owned component internals remain
unexpanded; source configuration and deferred callback locations retain provenance.

Borrowed components, aliases, reassignment, duplicate/cyclic attachments,
conditional/early returns, loops, structural mutations, unknown parents, anonymous
child initialization and escapes of fresh graph components into configuration or
callbacks withhold the template. The analyzer never invokes source code or reads
model values. All new bindings are INFERRED and protected from paired rename.

## Native navigation and reporting

Factory arrows navigate to the host's toolbar markup slot. That slot navigates
back to its source factory. Fragment definition IDs, nested form/field IDs and
markup uses share the normal ID reference machinery and source locations. The
scoped text formatter labels the immediate source factory and inferred bindings.
This milestone does not append indexed templates to the primary text report.

## MCP decision

Extend the existing page-structure tool's optional `includeNavigationContexts`
projection. No new tool, parameter or schema version. Default structure,
validation and component-context responses and primary selectors are unchanged.
All tools remain read-only.

`relationship: FACTORY_TEMPLATE` supplies the exact native value graph with inline
binding ancestry/children. Its `lifecycle.kind` is
`SOURCE_DECLARED_CONTENT_FACTORY`, `phase` is `REGISTRATION_TEMPLATE`, and runtime
presence remains `unknown`. Existing factory, invocation, setter/event and
replacement source locations remain available. An additive `contentFactory`
object records:

| Member | Meaning |
| --- | --- |
| `invocation` | `source_immediate`; the invocation is in the source setter |
| `factoryExecution`, `callbackExecution` | `not_evaluated` |
| `configurationEffects`, `modelValues`, `visibility`, `laterTreeState`, `slotPresence` | `unknown` |
| `slotRequirement` | `source_declared_placeholder` |
| `registrationLocation`, `hostConstructionLocation`, `idLocation`, `placeholderLocation` | Source registration, host allocation, supplied ID and constructor placeholder provenance |
| `configurationLocations[]`, `callbackLocations[]` | Opaque configuration and deferred callback declarations |

Existing event/selection contexts keep their response contracts. The shared
replacement limits remain 12,000 steps, 64 source dependencies, eight contexts and
12 source levels, with at most 128 factory nodes. Exhaustion clears that discovery
session's templates and marks optional navigation discovery incomplete. Project,
module and source-set gates, unsaved-source freshness, cancellation, the 30-second
request deadline and 512-KiB whole-response bound remain in force.

## Acceptance and remaining work

Acceptance covers the read-only application corpus with Wicket 10.11.0 and 11.0.0,
native references and factory navigation, shared MCP snapshots, freshness,
isolation, cancellation, discovery/response bounds and preserved earlier graphs.
An independent WicketTester fixture checks immediate installation and later Ajax
behavior and replacement; those observed fixture outcomes are not analyzer runtime
guarantees. Verification passed **2,776 normal tests**, **17 actual MCP host tests**,
**221 focused regressions** and **22 release-checker unit tests**, with zero
failures/errors/skips. Both Oat corpus versions passed, preserving all earlier
ordinary matches and milestone summaries, pairing all 36 page resources and
leaving all 318 application input hashes unchanged. Plugin configuration,
structure and binary compatibility checks passed against **IU-262.10968.63**;
the verifier retains the previous five deprecated/six experimental API notices.
The full normal build/verification run took **21m 43s**. Source, test and artifact
receipts match the resulting ZIP. See the
[evidence summary](wicket-oat-content-factory-evidence.json) for hashes and counts.

The empty-state setter uses a separate lazy toolbar placement protocol, covered
by the [fifteenth milestone](table-toolbars.md). Table column/cell populations,
CRUD save/delete callbacks, arbitrary factories and runtime state remain later
work. No live IDE installation or publication is
performed by this milestone.


[Context-aware completion](context-completion.md) now reuses supported graphs from
this milestone for parent-scoped Java/HTML ID suggestions. Its separate editor
probe does not change the normal inspection, fix, paired-rename or MCP contracts.
Inferred candidates remain labelled with their source context and uncertainty.
