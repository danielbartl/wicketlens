# Conditional confirmation consumers

A confirmation context follows one source ask-site lambda through private consumer
storage and a reviewed dialog submit hook. It is a separate
`CONFIRMATION_TEMPLATE`, with inferred bindings and no rename authority. The
initial page graph remains unchanged.

The route has explicit preconditions: the initiating callback reached this ask
site, this registration supplied the current non-null slot, and the standard
submit route reached the source hook. Initiating UI dispatch, callback
registration effects, current registration, slot presence, validation, models,
rows, runtime subclasses, open/close state and later hierarchy remain unknown.
No source callback or application code is executed during analysis.

The bounded protocol requires a direct stable constructor-local host, a source
initiating BiConsumer argument and one inline Consumer argument to the ask call.
Source marker interfaces may only extend unchanged JDK Consumer/BiConsumer and
Serializable interfaces. The compiled SerializableConsumer/SerializableBiConsumer
wrappers may retain their unused unary `andThen` method; it is not invoked or
evaluated by this protocol. An optional source overload forwards the unchanged
Ajax target and consumer, wrapping the two display inputs through exact compiled
Model.of calls. The selected setter first stores the consumer in its own private,
default-null instance field. One selected source hook captures that field,
clears it and invokes the captured consumer under its direct non-null guard,
forwarding the same Ajax target. Scoped reference search rejects competing slot
uses. A separate source cleanup override clears the slot before its empty base
hook; its reachability from Cancel/close is explicitly unknown.

The shared submission analyzer proves the owned private-final AjaxButton's
onSubmit-to-hook route, its Form identity and initial trigger ancestry. It does
not execute form processing or infer successful validation. Existing pre-dispatch
state restrictions apply. Post-dispatch statements remain opaque.

This slice accepts one private selected-page data helper whose single statement
calls exact compiled List/Collection.removeAll on its own private-final,
ArrayList-backed reviewed data field. The input is the initiating symbolic data
parameter, directly or wrapped once through exact JDK List.of. Data types reuse
the constructor data-loop guard. Shared submission checks describe feedback
receivers and stable Ajax refresh targets. These are source-call declarations:
collection contents, equality effects, deletion success, feedback/business
outcomes and exceptions remain unknown. This is not general mutation permission.
Other helpers, callback control flow and target escapes withhold that context,
while independent ask sites can still contribute. A separate
[selection-helper contract](selection-clear-declarations.md) now covers the
reviewed null-guarded model-collection clear and same-owner Ajax refresh.

Discovery retains sources for supported and withheld sites, checks cancellation,
and defaults to 40,000 steps, 96 sources, eight contexts, 128 nodes/effects and
12 ancestry levels. An exhausted discovery budget publishes no confirmation
graphs. Aggregate navigation bounds and the existing 512-KiB response cap still
apply.

MCP decision: extend the existing optional `wicket_get_page_structure` navigation
contexts and `wicket_get_navigation_contexts` retrieval. No new tool is needed.
Both use the same native analyzer, report snapshots and exports. Only new
confirmation contexts add `submissionTemplate.confirmation` metadata, including
source registration, setter, slot, capture/clear/guard/dispatch, cleanup and data
helper/storage locations. Existing submission metadata and default responses
retain their contracts. Agents can follow the source flow without treating it
as an executed delete or an active runtime callback.

The [selection-helper extension](selection-clear-declarations.md) covers
DataTablePage's bulk callback at `table.clearSelection(t)`. Direct “Mark as paid”
callbacks and their `replaceAll` data helper, recursive trees, broader save/delete
protocols, form internals and shared-resource refactoring remain later work.


Native trigger navigation can resolve to the consumer lambda, and hook-name
navigation resolves to its submit trigger. A private Consumer declaration is
only a discovery hint for focused native submission navigation, which reuses
that host's existing cached, bounded source query. At most eight hosts, 256 nodes
and 512 sources contribute extra confirmation results; exhaustion supplies no
extra targets. General caller discovery is unchanged. The hint cannot publish
bindings without the full route proof. Completion and paired
rename continue to use their existing guards and gain no callback editing scope.


Application acceptance currently traces the row-delete registration at
DataTablePage source offset 5749. It reports the bulk registration's unsupported
`clearSelection` call at offset 6552. These offsets are source-snapshot locations,
not stable runtime identities; rerun after edits.


## Confirmation consumer milestone verification

**238 focused checks**, **42 actual MCP host tests**, both read-only 36-page
Wicket corpora and plugin configuration/structure/binary compatibility checks
passed with zero failures/errors/skips. Each original DataTablePage gains one
separate five-binding inferred row-delete confirmation context. All eight
navigation contexts were retrieved after fresh captures and exactly match native
projections. Wicket 10.11.0: index 11,876 bytes, largest selected context 98,436 bytes; Wicket 11.0.0: index 11,870 bytes, largest selected context 98,430 bytes. Each response stays below 524,288 bytes.
All 36 initial page reports, earlier context/completion/probe facts, five saved
reports and the original 318 application inputs are preserved. Bulk deletion is
explicitly withheld at `table.clearSelection(t)`, the next contract boundary.
Compatibility against IU-262.10968.63: `Compatible. 7 usages of deprecated API. 6 usages of experimental API`.
Current source/host/artifact receipts match the development ZIP. The full normal
suite was not rerun; no current full-suite or release-readiness claim is made.
See the [milestone evidence](wicket-oat-confirmation-route-evidence.json).
No installation, signing or publication was performed.
