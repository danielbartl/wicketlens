# Null-guarded owned toolbar caches

The thirty-fifth milestone reviews the EmptyStateToolbar cache write and its
same-table registration during initial source configuration. The generic effects
pass previously rejected this component-field assignment. A scoped ownership proof
now permits the declared protocol while keeping runtime cache state unknown.

## Shared source contract

The receiver retains the [owned-table forwarding contract](owned-table-forwarding.md):
a fresh unattached known source owner, its private final directly constructed table,
and a reviewed final nested toolbar constructor. Constructor fields, interfaces,
source overrides, deferred columns reads and remaining effects retain their audits.
The cache exception is considered only after that complete constructor audit.

The cache must be a private, nonstatic, nonfinal instance field with implicit null
initialization and the exact final toolbar type. Its only write is the reviewed
fresh allocation. A direct standalone `cache == null` or `null == cache` guard has
no else branch and contains exactly allocation and registration. The registration
must use the exact compiled DataTable toolbar method on the same directly owned
table with that cache as its sole argument. Aliased/foreign receivers, another
allocation, extra statements, competing writes, initialized fields, cache getters
and other escapes withhold this proof.

The null-guard predicate and compiled attachment checks are shared with the
existing toolbar-template and constructor-registration analyzers. Semantic
ReferencesSearch is confined to the declaring source owner, including nested code.
It charges the caller's work budget for each reference and propagates cancellation;
no project-wide or cross-module search supplies ownership. Documentation references
are ignored as nonexecuting declarations. Cache aliases and escapes are rejected.

At most one later direct addOrReplace on its own private final plain framework
container cell is permitted to retain the existing isolated replacement audit.
The cell must have one direct exact framework allocation in the reviewed
constructor. A named/source/anonymous container subclass cannot borrow that dispatch
proof. Replacement arguments and other effects still pass the generic audit; this
adds no general component-field mutation permission.

The accepted cache reference remains internal to that source configuration. Its
registration must match the proved source operation, owner, table and allocation.
Neither predicates nor application/framework callbacks are executed by analysis.
Cache presence/reuse, toolbar instance count and IDs, model values, rendering,
visibility and later state remain unknown. No private cache/cell/content nodes,
new selectors or editing authority are exported. Earlier toolbar-relative content
templates continue to describe their separate inferred scopes.

## Bounds and framework verification

The shared 20,000-step/96-source, existing source-call/depth/instance limits, MCP
deadline and 512-KiB response bound are unchanged. The effects walker and existing
constructor-reference collector now skip whitespace, punctuation, identifiers and
comments while retaining all semantic operations and cancellation checks. Oversized
semantic bodies still withhold expansion. No method-effect or hierarchy memoization
has been added.

The exact compiled addTopToolbar/addBottomToolbar and private addToolbar boundaries
retain the earlier Wicket 10.11.0/11.0.0 review. The independent WicketTester oracle
now begins cache setup with an unattached owner, then observes dynamic toolbar IDs,
one registration, later cache reuse/content replacement and model/provider-dependent
visibility. Those are fixture observations; later attached-owner configuration is
not granted authority by this initial-source contract.

## MCP decision and application result

Extend existing structure, validation and component-context **diagnostics** through
shared semantic analysis and value snapshots. Agents can distinguish a reviewed
cache protocol from the exact later blocker. No tool, argument, selector, response
member or schema is added. Tool descriptions and client notes cover the same proof
and uncertainty. Native/MCP equality, source/interface/guard/registration/markup
freshness, module isolation, read-only actual-host output, cancellation and bounds
are covered by acceptance.

The original complete OatDataTable constructor, setEmptyState and private toolbar
sources remain unchanged. Their minimal TableForwardingProbe now completes initial
source setup with two root matches and the cache-registration finding; private
contents remain unexpanded. Native and all three MCP views agree.

The full DataTablePage passes the cache write at OatDataTable.java offset 8214,
line 165. It then reaches the unchanged 20,000-step budget in inherited menu setup,
at MenuItem.java offset 2752. Its initial hierarchy still remains unresolved. This
is a separate bounded-work issue, not a new cache permission or a larger limit.
Its source dependencies increase from 42 to 48 as discovery proceeds. All 36
primary finding/binding summaries, earlier context/completion groups and earlier
constructor probes remain separately checked. Application sources are never
modified by corpus acceptance.

Final milestone verification: **3,568** normal tests,
**39** actual MCP host tests and **22** release-checker unit tests passed
with zero failures/errors/skips. All 440 focused regressions passed.
Both read-only Oat corpora (Wicket 10.11.0 and 11.0.0) verify the original-source
cache protocol through native and MCP reports. The isolated complete table probe
now completes initial source setup with two root matches. Full DataTablePage next
reaches the unchanged 20,000-step budget at MenuItem.java offset 2752, line 69;
its initial hierarchy remains unresolved and tracked sources increase from 42 to 48.
All 36 primary finding/binding summaries, earlier groups/probes except the completed
forwarding probe, six markers, four reports and 318 application input hashes are
preserved. Configuration, structure and binary compatibility checks passed against
IU-262.10968.63: `Compatible. 7 usages of deprecated API. 6 usages of experimental API`. The full run took 28m 44s.
Current source/test/artifact receipts match the ZIP.
See the [milestone evidence](wicket-oat-toolbar-cache-evidence.json).
No installation or publication was performed.
