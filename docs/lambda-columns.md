# Lambda column constructor storage

The twenty-fifth milestone extends the source column construction contract to
Wicket LambdaColumn. OatBadgeColumn can forward a bound row function through its
compiled superclass constructor without being mistaken for eager row evaluation.

## Reviewed framework boundary

The two compiled constructors are LambdaColumn(IModel, SerializableFunction)
and LambdaColumn(IModel, Object, SerializableFunction), with the exact function
type org.danekja.java.util.function.serializable.SerializableFunction. The first
delegates to the second, which delegates to AbstractColumn, checks its function
with Args.notNull(Object, String), and stores it. No row function, model getter,
header creation or cell population runs in these constructors. The pinned
wicket-extensions 10.11.0 and 11.0.0 sources have the same implementation.

Only these resolved compiled constructors acquire this contract. Source wrappers
retain the existing explicit-delegation/private-data-storage audit. The compiled
AbstractColumn parent, exact compiled null guard and reviewed function interface
are checked and recorded as dependencies. The guard may reject null at runtime;
analysis does not decide whether constructor inputs are null or whether the page
initializes successfully. The guard is not added as a generally callable helper.

The compiled function interface extends JDK Function and Serializable. Its default
composition methods run only when called; this milestone does not authorize those
calls during source construction. Source callback interfaces and their ancestry
retain the initialization/constant-field audit. Callback target types survive
opaque aliases and casts so an interface subtype cannot disappear before the
column-input check. Unreviewed interface initialization withholds the expansion.
Unreviewed intersection callback casts retain an unresolved marker across aliases;
normalizing their functional type does not authorize extra interface initialization.

## Method references and data defaults

Method references are classified before ordinary reference expressions because
IntelliJ represents them as both. Bound receiver evaluation is audited separately
from the deferred callback. A constructor parameter receiver such as value::apply
is accepted. A source helper call producing that receiver remains unreviewed.
Lambdas, unbound references and source marker interfaces retain the existing
capture/effect checks. No function result or data-model value is evaluated.

Private scalar defaults also accept unary +, -, ! and ~ when the existing constant
analyzer proves a constant and the operand passes the same bounded data check.
This covers the number column's -1 defaults. Increment/decrement, method calls and
unknown unary values remain rejected. These constants are construction data and
are not exported as row, model or formatting values.

Header/cell/detach callbacks, getDataModel, model getObject and source rendering
hooks remain deferred. Calling them eagerly during construction still withholds
the storage expansion. No generated header, cell, row count, selection, sort,
formatting or model value is added to the page graph. The retained LambdaColumn
constructor diagnostic remains available if later setup fails. It supplies no
partial component selector or complete initial graph.

For the pinned Oat source, the badge column now passes its LambdaColumn delegation
at offset 1700. Number-column signed defaults also pass. DataTablePage next stops
at OatDateColumn's compiled FormatStyle enum defaults (offset 1884, line 43), which have no initialization
contract in this milestone. Its initial hierarchy remains unresolved and its
separate toolbar contexts remain available.

## MCP decision and acceptance

Extend existing page-structure, validation and component-context behavior through
the shared analyzer and value snapshots. Agents see the same constructor storage
diagnostic, unknown callback/model state, source provenance and later blocker.
No tool, parameter, selector, response member or schema version is added. Tool
descriptions and client semantic documentation describe the additional boundary.

Source wrappers, callback interfaces and the wicket-extensions, wicket-util and
jdk-serializable-functional archives participate in freshness. Analysis stays
read-only and invocation-local. Existing 12-level ancestry/data/interface and
source-isolation bounds, 20,000 setup steps, 96 sources, 256 instances, 16 source
calls, cancellation, 30-second MCP deadline and 512-KiB whole-response limit apply.

Acceptance covers both constructor signatures, bound/unbound/lambda callbacks,
eager receiver/function/model calls, source interface and subtype initialization,
compiled guard identity, signed constant versus effectful defaults, retained
blocking notes, module isolation, source/interface freshness, binary dependency
snapshots, cancellation, response bounds and read-only native/MCP results.
An independent WicketTester oracle checks constructor storage, the null guard,
lazy data-model creation, subsequent row-function/model access and later rendering.
Those runtime observations do not become static values in reports.

Final milestone verification: **3,177** normal tests,
**29** actual MCP host tests and **22** release-checker unit tests passed
with zero failures/errors/skips. The 304 focused regression tests passed.
Both read-only Oat corpora (Wicket 10.11.0 and 11.0.0) construct the actual badge
and number columns in a fixture-only probe: 11 page bindings, two constructor
storage notes, no column-generated children and shared native/MCP diagnostics.
DataTablePage advances to the date column's FormatStyle enum default while its
initial hierarchy remains unresolved. All 36 primary finding/binding summaries,
previous template/completion groups, earlier constructor probes, six graphical
Project tree markers, four saved text reports and 318 application input hashes
are preserved. Configuration, structure and binary compatibility checks passed
against IU-262.10968.63: `Compatible. 7 usages of deprecated API. 6 usages of experimental API`. The full run took 24m 41s.
Current source/test/artifact receipts match the ZIP. See the
[milestone evidence](wicket-oat-lambda-column-evidence.json) for counts and hashes.
The ZIP retains 0.1.0 metadata; no installation or publication was performed.

The [next milestone](date-column-enums.md) adds the narrow JDK FormatStyle
constant-storage contract and advances the real page to its provider callback.
