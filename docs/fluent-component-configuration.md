# Fresh inline component fluent configuration

The thirty-second milestone reviews source fluent chains such as the bulk-action
clear button's `new OatButton(...).setSize(SMALL).setStyle(GHOST)`. It proves a fresh
inline receiver and the selected source storage operations. Constructors, setters,
enums and models are inspected as source declarations; no application code runs.

A chain has at most twelve resolved unary source calls, rooted in one ordinary or
anonymous source component allocation. Local/field aliases, factory-returned or
borrowed receivers and computed inputs receive no new authority. Source dispatch
uses the allocated concrete/anonymous class rather than the enclosing page class.
Each selected component setter performs one direct forwarding call through its
own private-final behavior field and returns its own unqualified `this`.

The behavior field is a unique direct zero-input source allocation established by
the selected constructor path or initializer. Source initializer blocks, unknown
compiled behavior ancestors and eager default-constructor statements are rejected.
Source interfaces, field initializers, framework construction overrides and
synchronous binding hooks retain their effect guards. Source component constructor
delegation is audited once in its allocated context; allocation arguments retain
the caller's context. Deferred Ajax/rendering/configuration callbacks are not
executed or expanded.

The selected behavior setter performs one exact Model.setObject operation on a
private, directly initialized Model.of field and returns its own receiver. The
field may be declared as IModel or Model, but its source identity must remain the
exact framework Model allocation. Reassignment, aliases, custom model factories,
model getters/consumers and unreviewed uses withhold the proof. Ordinary deferred
render/detach hooks may read the field; their model values and effects stay unknown.
The component's private behavior can only participate in its direct attachment
and simple source forwarders. No private behavior/model/component nodes or editing
authority are added to the page graph.

## Source enums and framework storage

Inputs and Model defaults are type-qualified or statically imported source enum
constants. Their initialization declarations are audited across every constant:
literal arguments, no constant-specific anonymous bodies or initializer blocks,
constant static data, and constructors that only assign parameters to private-final
primitive/String fields. Source interface initialization is checked. Convenience
methods and enum property getters are not evaluated. This grants no general enum,
formatting or model-value execution contract.

The pinned Wicket 10.11.0/11.0.0 Model factories allocate an exact Model, whose
constructor/setObject store an object reference without invoking its getters,
formatting, equals or detach callbacks. IModel's open setObject contract is only
accepted after proving the actual private Model identity. Framework archives and
SDK Enum metadata join freshness. Runtime enum initialization outcomes, current
model values, rendered classes, Ajax outcomes and later state remain unknown.
An instrumented WicketTester oracle checks receiver identity and stored enum
references, then separately exercises rendering and an Ajax click.

## Bounds and MCP

The ownership scan caches semantic references and assignments, skipping tokens,
identifiers, whitespace and documentation that cannot hide additional operations.
Semantic traversal still checks cancellation and the shared 20,000-step/96-source
budget. Twelve-operation chains, twelve source-ancestry/constructor levels, the
existing MCP deadline and 512-KiB response limit remain unchanged. Oversized scans
publish no partial expansion. Setter operations have a strict forward/store/return
proof; redundant generic setter and constructor walks are avoided.

MCP decision: extend the existing page-structure, validation and component-context
**diagnostics** through shared semantic analyzers and value snapshots. Agents can
distinguish reviewed fresh configuration from the exact next blocker without a
new tool, parameter, selector, response field or schema. Descriptions and client
documents cover the storage and uncertainty rules. Native/MCP equality, module
isolation, constructor/setter/model/enum/interface/markup freshness, cancellation,
bounds, whole-response limits and read-only actual-host responses/descriptions
are covered by acceptance.

## Application scope

The original clear-button chain at OatDataTable.java offset 6999 (line 144) now
passes. DataTablePage next stops in setEmptyState at `new EmptyStateToolbar(table)`,
offset 8234 (line 165): `A component escapes into deferred construction`. The
initial hierarchy remains unresolved. Passing the table into that source toolbar
requires a separate ownership/construction contract.

A minimal page fixture uses the original complete OatDataTable constructor and
newTools method without modifying the application sources. It avoids spending
the shared budget on unrelated inherited navigation setup and omits the later
empty-state configuration. Its native/MCP findings describe declarations, not
runtime rendering. The original 36-page corpus separately verifies the real
DataTablePage and all earlier template, completion and constructor probes.

Final milestone verification: **3,435** normal tests,
**36** actual MCP host tests and **22** release-checker unit tests passed
with zero failures/errors/skips. All 270 focused tests passed.
Both read-only Oat corpora (Wicket 10.11.0 and 11.0.0) verify the original complete
OatDataTable constructor and newTools fluent configuration in a minimal fixture
with two matched declarations. Native/MCP facts agree. DataTablePage next stops
at `new EmptyStateToolbar(table)`, OatDataTable.java offset 8234, line 165.
All 36 primary finding/binding summaries, earlier groups/probes, six markers,
four reports and 318 application input hashes are preserved. Its source dependency
count increases from 40 to 42. Configuration, structure and binary compatibility
checks passed against IU-262.10968.63: `Compatible. 7 usages of deprecated API. 6 usages of experimental API`. The full run took 29m 5s.
Current source/test/artifact receipts match the ZIP. See the
[milestone evidence](wicket-oat-fluent-configuration-evidence.json).
No installation or publication was performed.
