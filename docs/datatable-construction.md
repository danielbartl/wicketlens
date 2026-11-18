# Construction-only DataTable boundary

The twenty-eighth source-layout milestone reviews the compiled four-argument
DataTable constructor used by OatDataTable.RowsTable. The contract proves an
owned construction boundary; it does not reconstruct the framework's private
table contents or execute provider and column callbacks.

## Framework construction and eager dispatch

The reviewed constructor stores the column list, creates caption, column-group,
body, grid and toolbar containers, retains the provider in the grid, and sets
the grid's page-size/current-page storage. The standard grid constructors and
paging setters do not call provider size, iterator, model or detach. Header,
row/cell construction, population and rendering are later operations.

The constructor does call getCaptionModel, newBodyContainer and newDataGridView
virtually. Its child additions can also dispatch to an overridden add method.
The source wrapper chain must therefore retain the framework implementations of
these and other methods. This first slice permits only the exact deferred
newRowItem/newCellItem overrides declared by DataTable. It permits no instance
fields, initializer blocks, object hooks or extra source methods. Static-final
constants such as serialVersionUID are allowed. Source interfaces retain the
existing initialization audit. Anonymous subclasses receive the same checks.

Source constructors must consist of a single resolved direct super call forwarding
their four parameters in order. Extra statements, computed delegation inputs,
varargs, qualified enclosing receivers and delegation through `this(...)` remain
unreviewed. This prevents eager provider queries or enclosing-page mutations from
hiding before or after the compiled boundary. Allocation inputs retain their
existing source-effect audit. Source
wrapper construction does not grant contracts to table getters, paging actions,
toolbar construction, configuration, rendering or arbitrary compiled subclasses.

The exact compiled DataTable signature, internal Caption/ToolbarsContainer/grid
classes, grid/repeater superclass chain, ColGroup and Args null guard are checked
in the selected page's resolve scope and recorded as dependencies. The relevant
Wicket 10.11.0 and 11.0.0 source entries are identical. Common framework component
construction retains its existing review policy; application component listeners
and runtime initialization are not simulated.

## Native and MCP behavior

The shared factory/setup pass retains a PARTIAL_ANALYSIS diagnostic at the
reviewed constructor call, including when a later statement stops the analysis.
Provider queries, column/header/row/cell callbacks, model values, row counts,
visibility, constructor exceptions and later lifecycle outcomes remain unknown.
Framework-owned children and runtime item IDs do not become page bindings.

MCP decision: extend the existing page-structure, validation and component-context
facts through the shared analyzer and value snapshots. Agents need the same
construction fact and precise later stop. No tool, parameter, selector, response
field or schema version is added. Tool descriptions and client documents describe
the eager-factory guards and remaining uncertainty.

Source wrappers, companions and compiled dependency archives participate in
freshness. Read-only invocation-local analysis, selected module/project isolation,
cancellation, the existing source ancestry/interface/call/step budgets, 30-second
MCP deadline and 512-KiB whole-response bound are preserved.

## Application evidence and next boundary

DataTablePage now passes RowsTable's superclass constructor at OatDataTable.java
offset 19041 (line 435). The next stop is DataTable.addTopToolbar at offset 4624
(line 96). Toolbar registration and toolbar construction require their own
contracts; the primary initial hierarchy remains unresolved.

The read-only corpus also makes a fixture-only copy of OatDataTable with its
original constructor prefix through table creation and its original RowsTable
source. Only the enclosing type name is changed in that wrapper. The shortened
fixture assigns null to the otherwise unassigned selection/bulk-bar fields and
attaches the table, stopping before toolbar setup. A page copy retains the actual
InvoiceProvider, filtered query, data seed and confirmation setup. This isolates
the newly reviewed constructor without editing application sources or assuming
the rest of the table lifecycle. Native/MCP graphs retain explicit uncertainty.
The probe has 26 inferred matches and nine unresolved branch counterparts, with
the same constructor note in structure, validation and component-context views.
All 36 primary summaries and the previously supported contexts remain unchanged.

This slice runs in the bounded factory/setup pass. Earlier independent analyzers
retain their existing supported-declaration policy; this is not a global rewrite
of DataTable discovery. The constructor proof checks anonymous wrappers directly
even when an earlier analyzer supplies their existing page bindings.

Final milestone verification: **3,266** normal tests,
**32** actual MCP host tests and **22** release-checker unit tests passed
with zero failures/errors/skips. The 103 focused table/provider/module tests passed.
Both read-only Oat corpora (Wicket 10.11.0 and 11.0.0) retain the actual RowsTable
and constructor prefix in an isolated fixture before toolbar setup. Its 26 inferred
bindings and constructor note are shared by native/MCP views. DataTablePage passes
the compiled constructor and next stops at DataTable.addTopToolbar.
All 36 primary finding/binding/source-dependency-count summaries, earlier groups
and probes, six markers, four saved reports and 318 application input hashes are
preserved. Configuration, structure and binary compatibility checks passed against
IU-262.10968.63: `Compatible. 7 usages of deprecated API. 6 usages of experimental API`. The full run took 26m 21s.
Current source/test/artifact receipts match the development ZIP.
See the [milestone evidence](wicket-oat-datatable-construction-evidence.json).
No installation or publication was performed.
