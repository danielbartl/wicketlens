# Guarded selection clear declarations

Bulk confirmation callbacks can describe a bounded source table helper whose
body declares a model-collection clear under a null guard, an Ajax refresh of
that same table, and a receiver return. This completes the original
DataTablePage bulk-delete confirmation context without querying a model or
executing a callback.

The receiver must be an unchanged direct constructor local with a unique initial
markup binding. Source dispatch uses its actual named allocation type, including
selected overrides; the reviewed helper must be declared directly on that type. Anonymous receiver overrides remain outside the contract.
The selected helper accepts one unchanged callback Ajax target and returns its
own source receiver type. Its body must contain exactly three statements:

1. A direct non-null guard on its own private-final column field. The branch
   contains only the direct `column.getter().getObject().clear()` chain, with no
   else branch. Reversed null comparisons and unbraced branches are accepted.
2. Exact compiled AjaxRequestTarget/partial-page-handler `add(this)`, using the
   helper's unchanged parameter, with no other targets or target uses.
3. `return this`.

The column getter must be declared directly on its field's declared source type,
and must have one source return of its own private-final
IModel field. Exact compiled `IModel.getObject` and java.base
`Collection.clear` declarations are checked. Extra statements, aliases, target
escapes, other collection/model operations, different fields, impure getters,
unknown/inherited helper or getter dispatch and declared source collection overrides withhold that context.
Independent confirmation contexts retain their own source proofs.

These facts describe source declarations. The column field's actual value and
runtime getter dispatch are unknown. The model implementation, effects and value,
collection identity/implementation/mutability, clear success, selection contents
and later state are also unknown. The compiled collection method is an interface
declaration; its runtime implementation and effects are not established. Clearing is conditional on the source non-null guard and the
declared getter dispatch. The later refresh requires prior helper statements to
return; a model failure can prevent it. No model, getter, collection operation,
constructor or application callback is executed during analysis.

The new bulk context keeps the existing `CONFIRMATION_TEMPLATE` relationship,
with five inferred initial-target/ancestor bindings and no rename authority.
`SOURCE_SELECTION_CALL` identifies the source helper invocation; the separate
`AJAX_REFRESH` declaration uses its source refresh location and the proved outer
table identity. No private column/model nodes or runtime row IDs are invented.
The primary initial graph and initial selectors are unchanged.

MCP decision: extend the existing optional structure navigation contexts and
`wicket_get_navigation_contexts`. Reuse the native confirmation/submission
analyzers, attachment/effect checks, report snapshots and exact context exporter.
No new tool is needed. `submissionTemplate.confirmation.selectionClears` appears
only when this new operation is present. It records helper, guard, column,
getter/model-storage, compiled model read, collection clear, refresh and return
locations, with explicit unknown state and `execution="not_evaluated"`.
Existing row-confirmation metadata remains unchanged when this member is absent.
Tool descriptions and client documentation describe the additive metadata.

Existing per-analysis work/source/context/node/effect/depth bounds, cancellation,
aggregate discovery limits, freshness and the 512-KiB whole-response cap apply.
Supported and withheld helper/getter sources participate in freshness. Native
navigation and MCP have the same facts; neither gains editing authority.

The [record-action extension](record-action-callbacks.md) covers DataTablePage's
direct “Mark as paid” row/bulk source callbacks and their `replaceAll` record
update helper. This selection helper still grants no general model or data-mutation
authority. Plain-page initial target discovery remains a separate boundary.


## Selection helper milestone verification

**282 focused checks**, **43 actual MCP host tests**, both read-only 36-page
Wicket corpora and plugin configuration/structure/binary compatibility checks
passed with zero failures/errors/skips. DataTablePage's bulk confirmation now has
its own five-binding inferred context, alongside the preserved row-delete
context. All nine contexts were retrieved after fresh captures and exactly
matched native projections. Wicket 10.11.0: index 12,138 bytes, largest selected context 98,166 bytes; Wicket 11.0.0: index 12,132 bytes, largest selected context 98,160 bytes. Each response stays below 524,288 bytes.
All 36 initial reports, earlier corpus facts, six saved reports and the original
318 application inputs are preserved; the prior bulk-helper diagnostic is
resolved. Selection/model effects and outcomes remain unknown. Compatibility
against IU-262.10968.63: `Compatible. 7 usages of deprecated API. 6 usages of experimental API`. Source, host and artifact
receipts match the development ZIP. The full normal suite was not rerun; no
full-suite or release-readiness claim is made.
See the [milestone evidence](wicket-oat-selection-clear-evidence.json).
No installation, signing or publication was performed.
