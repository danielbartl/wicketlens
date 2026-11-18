# Direct record-update callback declarations

The row and bulk “Mark as paid” callbacks on DataTablePage have separate
`ACTION_TEMPLATE` source contexts. They describe one inline constructor
BiConsumer argument, its private record-update helper, declared feedback/Ajax
targets, and the optional shared selection-clear helper. They do not reuse a
submission trigger or invent a runtime row/button ID or Form association.

The callback must be a direct source registration argument in the selected
class's sole constructor, outside other callbacks. The shared consumer protocol
checks the unchanged JDK BiConsumer operation and its reviewed source/library
wrappers. Registration dispatch, storage, current callback, authorization,
visibility predicates and UI dispatch remain unknown. Registration provenance
means an argument is present in source, not that an active UI action stores or
invokes it. Only block callbacks containing a candidate source replacement-loop
helper are considered in this slice; it is not an enumeration of every action.

The callback body consists of bounded straight-line declarations. Shared
submission checks identify feedback receivers, unchanged constructor-local Ajax
targets and the same source selection-clear protocol used by bulk confirmation.
One private, nonstatic selected-class void helper takes the captured data List,
or one captured record wrapped through exact JDK List.of. Data types reuse the
constructor data-loop guard.

The helper contains one enhanced for-loop over its unchanged input. The loop
body has one exact JDK List.replaceAll call on an own private-final List field
backed by a direct new ArrayList. Its inline JDK UnaryOperator declares:

- `current.equals(loopRecord)` as a predicate, with Object.equals signature;
- one direct, zero-input source method on the current canonical record for the
  true branch;
- the unchanged current record for the false branch.

The transform method only returns a new instance of that same canonical source
record. Its constructor arguments preserve their corresponding own backing
component fields, in order, apart from exactly one component supplied with a
constant of its reviewed source enum type. Extra effects, explicit constructors,
computed/reordered arguments, other predicates, aliases, target escapes and
unreviewed data operations withhold the context. No method name, action label or
specific enum constant such as PAID is hardcoded into the proof.

These are declarations, never execution results. Input/storage values and aliasing,
loop counts, collection execution, equality effects/results, record construction,
enum initialization, business/feedback outcomes and later state remain unknown.
Equality bodies are retained as source dependencies but not evaluated or claimed
pure. An empty input or false predicate can leave the records unchanged; earlier
calls or exceptions can prevent later feedback and refresh. No callback, helper,
predicate, record constructor or model is executed by analysis.

Each original action context retains one inferred initial table binding and its
root ancestry. No data-record nodes or private model/column nodes are added.
Primary reports/selectors stay unchanged, callbacks remain excluded from
completion, and these contexts grant no editing or paired-rename authority.
Source registration, loop, equality, transform, constructor-argument and effect
locations are available to native reporting/navigation and MCP from one value
snapshot.

MCP decision: extend the existing optional structure contexts and
`wicket_get_navigation_contexts`, using the shared semantic analyzers, source
snapshots, freshness/cancellation/bounding pipeline and exact context exporter.
No new tool is needed. New contexts expose `actionTemplate`, including `updates`,
`effects` and optional `selectionClears`. Older submission and confirmation
objects remain unchanged. Tool descriptions and client documentation describe the
additive relationship and metadata. `uiTrigger="not_reconstructed"`,
`callbackExecution="not_evaluated"` and explicit unknown properties prevent the
source graph from implying an executed business action.

Existing work/source/context/node/effect/depth and aggregate discovery limits,
the 512-KiB whole-response cap, project/module isolation and read-only behavior
apply. Supported and withheld source helpers, records, enums, registration
interfaces and compiled operations participate in freshness. Incomplete syntax
and exhausted budgets publish no action graphs and retain diagnostics.

Initial target discovery for the narrow fresh source storage registrar is now
covered by the [stored-action attachment contract](stored-action-attachments.md).
It retains inferred initial declarations on plain pages without treating a
registered callback as construction-time execution. Other registrar protocols,
broader actions, recursive trees, wizard/form internals and shared-resource
refactoring remain later work.


## Record-action milestone verification

**334 focused checks**, **44 actual MCP host tests**, both read-only 36-page
Wicket corpora and plugin configuration/structure/binary compatibility checks
passed with zero failures/errors/skips. DataTablePage's direct row and bulk
“Mark as paid” callbacks each have a separate inferred action context with one
table binding. All eleven contexts were retrieved after fresh captures and
exactly matched native projections. Wicket 10.11.0: index 13,192 bytes, largest selected context 98,167 bytes; Wicket 11.0.0: index 13,186 bytes, largest selected context 98,161 bytes. Each response stays below
524,288 bytes. All 36 initial reports, earlier corpus facts, seven saved reports
and the original 318 application inputs are preserved. Plain-page bindings remain
withheld where initial target discovery is unresolved. Callback/registration,
predicate, record construction and business outcomes retain their uncertainty.
Compatibility against IU-262.10968.63: `Compatible. 7 usages of deprecated API. 6 usages of experimental API`. Source, host
and artifact receipts match the development ZIP. The full normal suite was not
rerun; no full-suite or release-readiness claim is made.
See the [milestone evidence](wicket-oat-record-action-evidence.json).
No installation, signing or publication was performed.
