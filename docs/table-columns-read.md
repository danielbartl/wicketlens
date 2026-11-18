# Deferred owned-table columns reads

The thirty-fourth milestone reviews `DataTable.getColumns()` in the source
callback used by Oat's deferred colspan model. The getter returns the original
constructor-supplied list. It does not evaluate a model or query a provider, but a
later call on an arbitrary list could invoke application code. The proof therefore
checks the list's origin and tracks its restricted use independently of ordinary
opaque data.

## Source and framework contract

The query is available only during the deferred-callback audit inside a toolbar
constructor already covered by the [owned table forwarding contract](owned-table-forwarding.md).
Its receiver must directly name that proof's private final table field on the same
known source owner. The selected method must be the final, zero-input compiled
DataTable.getColumns returning List. Eager calls, foreign/local table aliases and
queries outside this owned constructor scope acquire no new authority.

The table's columns argument must be an exact static JRT/java.base List.of call,
either directly in the owned table allocation or forwarded through an unchanged
parameter of the selected owner constructor. In the latter case the concrete owner
allocation must supply that factory directly. Constructor reference/assignment
snapshots are reused to reject parameter reassignment. Local/field inputs, custom
source factories, mutable lists and other collection factories remain unreviewed.
Factory arguments retain their earlier evaluation/effect audits. No application
factory or JDK method is executed by the analysis.

The returned list is an internal borrowed reference with a restricted identity.
Only the exact zero-input JRT/java.base List.size operation is allowed. Local
declarations, aliases and identical conditional references preserve that identity;
casts cannot broaden its operations. Reassignment and uncertain conditional identity
are rejected. Element/iterator/stream queries, equality, hashing, formatting,
mutation, monitor use, method/constructor arguments, field storage, array and switch
escapes, and returning the list from the deferred model remain outside the contract.
A direct unused getter statement may discard its reference. No list, column or
private component nodes are added to the graph, and no size value is inferred.

The pinned Wicket 10.11.0/11.0.0 getter bodies retain the list reference unchanged.
The reviewed JDK List.of implementations supply immutable framework storage whose
size reads do not invoke element methods. The WicketTester oracle checks stored
identity and size without provider, model or element callbacks, contrasts a custom
list's size dispatch, then separately observes later model evaluation during
rendering. These observations are fixture outcomes, not runtime facts exported by
analysis. Column count/elements, models, visibility, rendering and later state
remain unknown.

## MCP and limits

MCP decision: extend the existing structure, validation and component-context
**diagnostics** through the shared analyzer and value snapshots. Agents can see the
completed getter/provenance fact and the next independent blocker. No tool,
parameter, selector, response member or schema is added. Descriptions and client
notes explain the same restricted identity and uncertainty.

Source, core/extensions archives and SDK List metadata participate in freshness.
Native/MCP equality, uncommitted callback edits, factory/owner/markup/interface
freshness, module isolation, read-only actual-host output, cancellation, source
budget and whole-response bounds are covered by acceptance. The shared 20,000-step,
96-source and existing call/depth limits, MCP deadline and 512-KiB response limit
remain unchanged. Returned list capabilities are session-local and never serialized.

## Original application result

DataTablePage now passes getColumns at OatDataTable.java offset 8600, line 176,
and both size reads in visibleColumnCount. Its next source stop is assigning the
fresh EmptyStateToolbar into the private cache, offset 8214, line 165:
`A callback changes or exposes a component reference`. Source order returns to the
allocation statement after finishing its constructor audit. Null-guarded cache
state, later registration/replacement, constructor outcomes and runtime contents
remain unknown. The initial primary hierarchy remains unresolved.

The unchanged original OatDataTable constructor, setEmptyState and private toolbar
are exercised by the minimal TableForwardingProbe and by the real 36-page corpus.
Its earlier forwarding finding remains; the new getter finding precedes the same
cache-assignment blocker in native and all three MCP views. Earlier template,
completion, navigation and constructor cases remain separately checked.

Final milestone verification: **3,525** normal tests,
**38** actual MCP host tests and **22** release-checker unit tests passed
with zero failures/errors/skips. All 360 focused regressions passed.
Both read-only Oat corpora (Wicket 10.11.0 and 11.0.0) verify original-source
columns getter/size reads and the next cache-assignment blocker through native and
MCP reports. DataTablePage next stops at the private toolbar cache assignment,
OatDataTable.java offset 8214, line 165. Its initial hierarchy remains unresolved.
All 36 primary binding/source-dependency summaries, earlier groups/probes, six
markers, four reports and 318 application input hashes are preserved; the existing
forwarding probe records its reviewed progress. Configuration, structure and binary
compatibility checks passed against IU-262.10968.63: `Compatible. 7 usages of deprecated API. 6 usages of experimental API`.
The full run took 30m 36s. Current source/test/artifact receipts match the ZIP.
See the [milestone evidence](wicket-oat-columns-read-evidence.json).
No installation or publication was performed.
