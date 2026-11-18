# Source column construction

The twenty-fourth milestone recognizes storage-only source columns whose first
compiled superclass is Wicket AbstractColumn. It addresses OatSelectionColumn's
constructor delegation without evaluating header creation, cell population or
row/model values.

## Reviewed boundary

The compiled AbstractColumn(IModel) constructor delegates to
AbstractColumn(IModel, Object), which only assigns its private display-model and
sort-property fields. The contract covers those two resolved constructors;
getHeader, populateItem, detach, model access and other compiled methods receive
no execution contract. Both pinned wicket-extensions source archives, versions
10.11.0 and 11.0.0, contain this same implementation. Their SHA-256 digests are
`e04b8a602528bb6fc8204d3d07962eb9bfd8a6d8e1baf6db20542d54be2426fe` and
`1bacfca351001bbf48d8e9c4c590584e8d19be0692241297379f508c1a5cef8b`.

Source wrappers require explicit constructor delegation followed only by writes
to their own private instance data fields. Initializer blocks, finalizers,
component fields, foreign writes, control flow and arbitrary helper evaluation
remain outside this contract. Declaration initializers use the same data proof;
static initialization requires known constants. Supported expressions include
literal/constructor-parameter data, private data-field references, stored callbacks,
reviewed Model/ArrayList allocation and Model.of/ofList or the non-supplier
Objects.requireNonNull overloads. Call arguments and method-reference receivers
are audited; erasing a component to Object does not authorize its escape.

Source callback interfaces have their own bounded ancestry and static-field
initialization audit. Marker interfaces such as SerializableFunction can extend
reviewed JDK Function/Serializable interfaces. Unknown initialization effects and
unreviewed compiled interface ancestry withhold this construction expansion.

Column header/cell methods remain deferred. In particular, the inherited
ICellPopulator.populateItem and IDetachable.detach overrides are no longer mistaken
for eager constructor effects. Calling them during construction still fails the
storage-only check. No header, cell, row, selection, sort or model value is added
to the page graph. Unrelated existing analyzer behavior and editing guards are
preserved. A failed layout expansion supplies no partial layout graph; older
independent Java discovery may still describe its own supported declarations.

The retained diagnostic names the reviewed constructor storage and its unknown
callback/model/sort state, including when later page setup stops. It is a source
analysis note, not a component selector or proof that the full page initializes.

For the pinned application, DataTablePage passes the selection column's
constructor at OatSelectionColumn.java offset 5192. It next stops at
OatBadgeColumn.java offset 1700 (line 43), whose first compiled superclass is
LambdaColumn. That constructor has no contract in this milestone. The primary
DataTable hierarchy remains unresolved; both separate toolbar contexts retain
four inferred bindings each. A fixture-only page constructs the actual
OatSelectionColumn and then adds one ordinary title Label, verifying the source
column independently of the remaining table setup.

## MCP decision and limits

Extend the existing page-structure, validation and component-context behavior
through the same native semantic analyzer and value snapshots. The retained
constructor diagnostic and subsequent exact failure location help agents explain
remaining blockers. No tool, parameter, selector, response field or schema version
is added. Client documentation and existing tool descriptions describe the scope.

Analysis remains read-only and invocation-local. Source wrappers, source callback
interfaces and the compiled wicket-extensions dependency participate in snapshot
freshness. The 12-level source ancestry/data-expression/interface bounds and
existing source-call, 20,000-step, 96-source and 256-instance limits apply;
recognizing ancestry does not raise the existing 12-call isolation limit.
Cancellation propagates. The existing 30-second request deadline and 512-KiB
whole-response limit remain unchanged.

Acceptance includes both constructor signatures, constructor/helper/model and
initialization rejection cases, deferred populations, source interfaces, source
ancestry/call limits, module isolation, snapshot freshness, cancellation,
response bounds and read-only native/MCP behavior. An independent WicketTester
oracle observes constructor storage and explicitly invoked later callbacks;
those runtime observations are not exported as static facts.

Final milestone verification: **3,151** normal tests,
**28** actual MCP host tests and **22** release-checker unit tests passed
with zero failures/errors/skips. The 269 focused regression tests passed.
Both read-only Oat corpora (Wicket 10.11.0 and 11.0.0) exercise the actual
OatSelectionColumn through a fixture-only probe: 11 page bindings, no column-generated
children, and the same constructor diagnostic in native/MCP views. DataTablePage
advances to OatBadgeColumn's LambdaColumn delegation while retaining its unresolved
initial hierarchy. All 36 primary finding/binding summaries, previous template and
completion groups, confirmation probe, six graphical Project tree markers, four
saved text reports and 318 application input hashes are preserved.
Configuration, structure and binary compatibility checks passed against
IU-262.10968.63: `Compatible. 7 usages of deprecated API. 6 usages of experimental API`. The full run took 25m 17s.
Current source/test/artifact receipts match the ZIP. See the
[milestone evidence](wicket-oat-column-construction-evidence.json) for counts and hashes.
The ZIP retains 0.1.0 metadata; no installation or publication was performed.

The [next milestone](lambda-columns.md) extends the same storage audit to the
compiled LambdaColumn constructors and bound row-function references.
