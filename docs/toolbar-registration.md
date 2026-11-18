# Owned DataTable header-toolbar construction and registration

The twenty-ninth milestone reviews fresh table/header-toolbar setup inside the
bounded source-owned construction pass. DataTablePage passes its top header
registration. Its next stop is NavigationToolbar construction through
OatNavigationToolbar.java offset 898 (line 22); the initial hierarchy is still
unresolved.

## Reviewed operations

The exact compiled addTopToolbar/addBottomToolbar methods delegate to DataTable's
private attachment helper. That helper null-checks the toolbar and adds it to the
appropriate private RepeatingView. It does not render, configure, query provider
rows or ask columns for headers. The table and inline toolbar must have proven
fresh, matching ownership before this construction contract applies.

The reviewed AbstractToolbar(DataTable) constructor delegates with a null model,
allocates an opaque toolbar ID from that table and retains its table reference.
HeadersToolbar(DataTable,ISortStateLocator) creates a lazy header RefreshingView;
AjaxFallbackHeadersToolbar additionally sets the table's output-markup flag.
Column iteration, header factories, sort-state access and render/sort callbacks
are deferred. The relevant extension sources match on pinned Wicket 10.11.0 and
11.0.0. Common component/repeater construction keeps its existing contracts.

## Ownership, source and input guards

Registration must be a direct statement in an owned source constructor. Its table
must be a local allocation or a uniquely assigned private-final instance field
allocated directly in that same constructor. Factory-returned tables, constructor
inputs, foreign receivers and aliases do not receive this proof. Before a
registration, only a literal-boolean setOutputMarkupId call and previous reviewed
registrations may use the table. Attachment, escape and other setup withhold the
expansion. The bounded source scan includes constructor/initializer writes and
uses the existing cancellation and step budget.

Each registration takes a fresh inline toolbar constructed for that same table.
Locator inputs are null or direct constructor parameters, with no evaluation.
For the abstract/header contract, source and anonymous wrappers may have only constant static-final storage, no
initializer blocks, and a single direct super call forwarding all constructor
inputs in order. Only the reviewed deferred render/configure/detach and sortable
header hooks are allowed. Eager extra statements, model-binding overloads, object
hooks, source initialization and arbitrary compiled toolbar subclasses remain
unreviewed. Source interface initialization retains the shared audit.

The argument allocation is audited before recording a completed registration.
Constructor diagnostics identify each source allocation; registration diagnostics
identify each source call. Runtime toolbar IDs/counts, private header children,
visibility, sorting, model/provider values and constructor/lifecycle outcomes stay
unknown. These facts do not add private table components to the page graph.

## Native, MCP and application scope

MCP decision: extend existing page-structure, validation and component-context
diagnostics through the same semantic analyzer and value snapshots. Agents need
the guarded registration fact and precise later blocker. No tool, parameter,
selector, response field or schema version is added. Tool descriptions and client
documents describe the same ownership and uncertainty rules. Source wrappers,
interfaces and framework archives participate in freshness. Invocation-local
state, selected project/module scope, cancellation, the existing 12-level source
and interface limits, 20,000-step/96-source setup budget, 30-second MCP deadline,
512-KiB response limit and read-only behavior remain in place.

The corpus also copies the original OatDataTable constructor prefix through its
top registration into a fixture, preserving RowsTable and the registration's
anonymous render callback except for the enclosing type name. It stops before
navigation setup, assigns null to the otherwise unassigned selection/bulk fields,
and attaches the table. The page copy keeps its provider/seed/confirmation setup.
Its 26 inferred matches and separate branch counterparts describe declarations,
with shared native/MCP constructor and registration notes. Application sources
remain untouched. Earlier independent analyzers keep their existing declaration
policies; this contract applies in the bounded owned factory/setup pass.

Final milestone verification: **3,302** normal tests,
**33** actual MCP host tests and **22** release-checker unit tests passed
with zero failures/errors/skips. The 107 focused tests passed.
Both read-only Oat corpora (Wicket 10.11.0 and 11.0.0) verify the original header
registration/anonymous render hook in an isolated fixture with 26 inferred matches.
Native/MCP construction and registration notes agree. DataTablePage next stops at
OatNavigationToolbar's compiled NavigationToolbar constructor (offset 898, line 19).
All 36 finding/binding summaries, prior groups/probes, six markers, four saved
reports and 318 input hashes are preserved. Two additional wrapper files increase
DataTablePage's source dependencies from 35 to 37. Configuration, structure and
binary compatibility checks passed against IU-262.10968.63: `Compatible. 7 usages of deprecated API. 6 usages of experimental API`.
The full run took 27m 2s. Current source/test/artifact receipts match the ZIP.
See the [milestone evidence](wicket-oat-toolbar-registration-evidence.json).
No installation or publication was performed.
