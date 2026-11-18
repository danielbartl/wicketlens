# Owned table reference forwarding

The thirty-third milestone reviews the table argument in
`new EmptyStateToolbar(table)` during `OatDataTable.setEmptyState`. The general
configuration-effects pass previously rejected every component argument here.
A scoped proof now permits this one owned reference to reach the exact compiled
`AbstractToolbar(DataTable)` constructor. Application code is inspected, not run.

The configuration receiver must be a fresh, still unattached known source owner.
Its table is a private final instance field with one ordinary direct allocation
in its single source constructor or field initializer, and one direct attachment
to that same owner. Constructor references may additionally use a literal output
flag or an earlier toolbar registration already reviewed in this analysis session.
Borrowed tables, aliases, another field initializer consuming the table,
reassignment, extra attachments and other eager consumers withhold the proof.
Already attached owners receive no new configuration authority.

The toolbar is a final, nonstatic nested source class directly extending the
compiled AbstractToolbar. One selected source constructor accepts DataTable and
forwards that parameter unchanged, exactly once, in its first direct super call.
Other parameter uses, computed arguments and anonymous/foreign construction remain
unreviewed. Source interfaces retain initialization audits. Initializer blocks,
unknown static effects, eager framework overrides and instance storage other than
private final framework container fields established by the constructor are
outside this contract. Only the standard deferred configure/render/detach hooks
are allowed as additional source methods.

The reference exception is scoped to this allocation and its exact super call.
The remaining source constructor body retains the existing effects audit. A later
unsafe operation still withholds expansion and retains the completed forwarding
finding. Cache writes, toolbar registration, content replacement and deferred
model queries acquire no additional permission. Private toolbar/cell nodes are
not added to the primary graph and no runtime toolbar ID or rename authority is
inferred. Earlier toolbar-relative declaration templates remain separate.

## Framework and bounds

On both pinned Wicket 10.11.0 and 11.0.0 versions, AbstractToolbar's one-table
constructor delegates to its model/table overload. That overload obtains an ID
through DataTable.newToolbarId and retains the table reference. Construction
alone does not register the toolbar. Existing DataTable construction checks keep
the compiled ID allocator and eager factories authoritative; source table methods
cannot replace them under this contract. The compiled archives join freshness.
The WicketTester oracle separately checks stored identity, distinct IDs,
unregistered parents, later explicit registration and later rendering callbacks.
Those observations are fixture outcomes, not runtime values in analysis output.

The ownership proof reuses the existing toolbar analyzer's constructor reference
and assignment snapshots instead of traversing the same constructor twice.
Additional source scans cache semantic references and assignments and check
cancellation. The shared 20,000-step/96-source layout budget, existing source-call
limits, MCP deadline and 512-KiB response limit stay unchanged. Oversized input
withholds expansion instead of publishing partial graph authority.

## MCP decision

Extend the existing structure, validation and component-context **diagnostics**
through the shared semantic analyzer and value snapshots. Agents can distinguish
reviewed table forwarding from the next source blocker. No tool, argument,
selector, response field or schema is added. Tool descriptions and client notes
record this boundary. Native/MCP equality, read-only behavior, unsaved source and
markup freshness, withheld-interface freshness, module isolation, cancellation,
source/response bounds and actual-host discovery are covered by acceptance.

## Application result

The original DataTablePage now passes the table argument at OatDataTable.java
offset 8234, line 165, within the existing budget. It next stops at
`DataTable.getColumns()` in `visibleColumnCount`, offset 8600, line 176. That
method is referenced by the toolbar cell's deferred colspan model. Its read-only
query/callback contract is a separate roadmap item. Cache state, attachment,
model values, rendering, visibility and later callbacks remain unknown.

The original complete OatDataTable constructor, setEmptyState and private toolbar
sources are used unchanged by a minimal page probe. It verifies the retained
forwarding finding and the same later blocker through native and all three MCP
views. The real 36-page corpus separately checks the page summaries, previous
context/completion groups and earlier constructor probes. Original application
sources are never modified.

Final milestone verification: **3,476** normal tests,
**37** actual MCP host tests and **22** release-checker unit tests passed
with zero failures/errors/skips. All 311 focused tests passed.
Both read-only Oat corpora (Wicket 10.11.0 and 11.0.0) verify original-source
EmptyStateToolbar forwarding and the same independent later query blocker through
native and MCP reports. DataTablePage next stops at `DataTable.getColumns()` in
visibleColumnCount, OatDataTable.java offset 8600, line 176. Its initial hierarchy
remains unresolved. All 36 primary finding/binding/source-dependency summaries,
earlier groups/probes, six markers, four reports and 318 application input hashes
are preserved. Configuration, structure and binary compatibility checks passed
against IU-262.10968.63: `Compatible. 7 usages of deprecated API. 6 usages of experimental API`. The full run took 30m 14s.
Current source/test/artifact receipts match the ZIP. See the
[milestone evidence](wicket-oat-table-forwarding-evidence.json).
No installation or publication was performed.
