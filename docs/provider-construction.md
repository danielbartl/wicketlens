# Construction-only source data providers

The twenty-seventh milestone separates allocation of a source SortableDataProvider
from its later data callbacks. InvoiceProvider's references to DataTablePage's
filtered() method no longer make the allocation itself look like a page query.

## Reviewed boundary

The exact compiled SortableDataProvider no-argument constructor allocates a
SingleSortState. That class's implicit no-argument constructor only initializes
ordinary object storage; its SortParam field starts null. Neither allocation
invokes iterator, size, model, detach or an overridable provider hook. The relevant
provider/sort-state/interface sources are identical in the pinned Wicket 10.11.0
and 11.0.0 archives. IDataProvider's default detach method is empty.

Only source providers whose first compiled superclass is SortableDataProvider
acquire this construction contract. All source classes in the chain must have:

- no instance fields or initializer blocks;
- only known constant static-final fields;
- implicit or empty zero-argument constructors, or one bare resolved super() call;
- no source object/finalizer hooks such as equals, hashCode or toString;
- reviewed interface initialization under the existing bounded source audit.

Anonymous source providers and source wrapper bases are supported under the same
rules. A lexically allocated inner provider may retain its implicit enclosing
page reference. Its allocation must be unqualified and take no explicit arguments.
Explicit borrowed receivers, qualified super calls, constructor inputs, state,
initializers, eager callbacks, recursive delegation and unknown compiled bases
remain outside the contract. Retaining an enclosing page does not evaluate its
queries or prove its later behavior.

The compiled SortableDataProvider, SingleSortState and provider/sort-state marker
interfaces are resolved in the selected page's scope and recorded as dependencies.
Provider construction does not grant execution contracts to their methods.

## Deferred callbacks and consumers

Provider callback bodies and their private query/model helpers are not treated
as construction statements. They may reference the page, but supply no initial
component children or row/model/query/sort values. Direct iterator/size/model/detach
calls through a known provider still withhold this construction expansion. Eager
calls made by source consumers retain the existing source-effect checks. Extra
functional-interface methods retain their existing eager-effect audit; unknown
compiled consumer and component construction remain unreviewed.

In particular, this milestone does not assume a table can never call a provider
during its own setup. A table's constructor and lifecycle require their own proof.
The new PARTIAL_ANALYSIS diagnostic records the provider allocation and unknown
callbacks/data state even when a later statement stops setup. It supplies no
partial selector or complete initial hierarchy.

This slice runs in the bounded factory/setup pass. Earlier independent analyzers
retain their existing supported-declaration policy; the new rule is not a global
rewrite of provider discovery.

## Application and MCP decision

DataTablePage passes its InvoiceProvider allocation at source offset 5255. Its
next stop is OatDataTable.java offset 19041 (line 435), the compiled DataTable
constructor used by RowsTable. The primary initial hierarchy remains unresolved;
both previously available toolbar contexts retain four inferred bindings each.

The corpus probe copies the actual page into a fixture, retains its original data
seed and confirmation-dialog setup, and isolates provider allocation before the
table. The companion retains the dialog attachment. InvoiceProvider and filtered()
source remain unchanged. This exercises the
same constructor-declaration path as the application without editing its checkout.

Extend existing page-structure, validation and component-context behavior through
the shared semantic analyzer and value snapshots. Agents receive the same
construction diagnostic and later blocker. No tool, parameter, selector, response
member or schema version is added. Tool descriptions and client documentation
explain the construction/callback distinction and remaining consumer uncertainty.

Source provider, page query, interface and binary dependencies participate in
freshness. Analysis stays read-only and invocation-local. Existing 12-level source
ancestry/interface and isolation bounds, 20,000 steps, 96 sources, 256 component
instances, 16 source calls, cancellation, 30-second MCP deadline and 512-KiB
whole-response limit remain unchanged.

Acceptance covers inner/anonymous/wrapped providers, empty and bare-super
constructors, static serialization constants, state/initialization/object-hook
rejections, direct/hidden eager callbacks, qualified-super receiver effects,
interface initialization, ancestry limits, later-stop notes, cancellation,
module isolation, constructor/callback/query freshness, binary snapshots,
response bounds and read-only native/MCP behavior. An independent WicketTester
oracle observes query-free provider/sort-state allocation and explicitly invoked
later size, iterator and lazy model callbacks. These runtime observations do not
become row/query/sort values in static reports.

Final milestone verification: **3,232** normal tests,
**31** actual MCP host tests and **22** release-checker unit tests passed
with zero failures/errors/skips. The 359 focused regression tests passed.
Both read-only Oat corpora (Wicket 10.11.0 and 11.0.0) exercise an isolated copy
of the actual InvoiceProvider and filtered() source, retaining the original data
seed and confirmation setup. Its 25 inferred bindings and provider-construction
note are shared by native/MCP views; no row/query/model values are evaluated.
DataTablePage advances to the compiled DataTable constructor in RowsTable.
All 36 primary finding/binding/source-dependency-count summaries, previous groups,
earlier probes, six Project tree markers, four saved reports and 318 application
input hashes are preserved. Configuration, structure and binary compatibility
checks passed against IU-262.10968.63: `Compatible. 7 usages of deprecated API. 6 usages of experimental API`.
The full run took 25m 11s. Current source/test/artifact receipts match the ZIP.
See the [milestone evidence](wicket-oat-provider-construction-evidence.json).
The ZIP retains 0.1.0 metadata; no installation or publication was performed.
