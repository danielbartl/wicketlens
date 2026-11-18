Current follow-up: the thirty-seventh milestone exposes DataTablePage's seven
navigation contexts through a compact index and separate complete MCP responses.
The combined legacy response keeps its size error. Native graphs, primary
reports and source uncertainty remain unchanged. See
[the retrieval contract](mcp-navigation-contexts.md). Earlier reviews below retain
their historical scope.

Current follow-up: the thirty-sixth milestone removes the inherited-menu work
stop by reusing complete semantic declaration subtrees. DataTablePage now exposes
26 inferred matches and 9 branch findings with 49 sources. Its optional combined
navigation response is the next 512-KiB blocker; default views fit. See
[the declaration-work contract](declaration-work.md). Earlier reviews below retain
their historical scope.

Current follow-up: the [thirty-fifth milestone](owned-toolbar-cache.md) passes
the cache write and same-table registration. The isolated original-table probe
completes; the full page next reaches its existing inherited-menu work budget.
Earlier reviews below retain their original milestone context.

Current follow-up: the [thirty-fourth milestone](table-columns-read.md) passes
getColumns and guarded size reads in the deferred colspan callback. The next stop
is the EmptyStateToolbar cache assignment at line 165. Earlier reviews below
retain their original milestone context.

Current follow-up: the [thirty-third milestone](owned-table-forwarding.md)
passes EmptyStateToolbar's owned table argument at line 165. The next source stop
is DataTable.getColumns in the deferred colspan method reference at line 176.
Earlier report reviews below retain their original milestone context.

# Mobile and DataTable report review

These findings explain the supplied reports and the later source-contract milestones.
The current DataTablePage boundary is described in the
[fresh fluent configuration contract](fluent-component-configuration.md); the earlier stops
below record how that diagnosis advanced. The application checkout is read-only;
reproductions and checks use fixture copies.

| Target | Interpretation |
| --- | --- |
| MobileCapacityPage | Four primary matches. The three primary branch entries (`day`, `count`, `meter`) match in its separate `days` population template. These are logical item declarations, not runtime rows. |
| OatMobileLayout | Direct base-layout reconciliation is withheld because initialization invokes an abstract model hook with no declared implementation. |
| InboxPage | 31 primary matches and nine primary branch entries, covered by the two sidebar item templates (four and seven matches). These remain separate symbolic scopes. |
| DataTablePage | Data seeding, constructor-owned confirmation content, column/provider construction and RowsTable's compiled DataTable constructor now pass. Header/navigation-toolbar construction and registration, and literal decoration through OatPopover.getTrigger(), now pass. The clear button’s fresh fluent configuration now passes. Initial reconstruction next stops at `new EmptyStateToolbar(table)` in setEmptyState (line 165), where the table reference reaches the deferred-construction escape guard. The initial parent scope remains unresolved. |

## OatMobileLayout

`onInitialize()` calls `bottomNavItemsModel()` as an argument of `newBottomNav()`
(line 60). The method is abstract at line 74. Direct base-layout analysis reconciles
constructor entries under declared base implementations; this hook has no body to
review. The diagnostic `The source helper body is unavailable` describes that
failure. OatAppLayout's successful declaration template does not establish this
missing implementation.

Selecting MobileCapacityPage gives the source interpreter MobileBasePage's actual
override, so `html`, `appTitle`, `bottomNav` and `days` can resolve. This difference
is expected. A method returning IModel can still have component side effects;
ignoring an unavailable implementation would exceed the current proof contract.
A regression check reproduces the same distinction with an abstract model hook
and a selected concrete override.

The separate markup diagnostic at MobileBasePage.html offset 1267 points to
`<wicket:link>` on line 20, around the All demos link. Automatic link reconstruction
is outside the supported markup contract. It does not mean the three row IDs are
missing. The real corpus checks identify that exact source tag and verify all
three row-template matches.

## DataTablePage

The current stop is the compiled `NavigationToolbar` constructor at
OatNavigationToolbar.java offset 898, line 19. DataTablePage retains reviewed
RowsTable construction and top-header construction/registration notes. Paging
and navigation-toolbar constructors still need separate contracts; the initial
hierarchy remains unresolved.

The original first stop was the `for` loop at offset 3304, line 79, seeding
23 Invoice records. The constructor data-loop milestone now audits that code for
component isolation without executing the loop or exporting its data. Its accepted
PARTIAL_ANALYSIS note remains visible even though later construction is withheld.

The constructor-owned content milestone now also passes
`new OatConfirmDialog("confirm")` at offset 3767, line 86. Its private-final message
Label replaces the inherited placeholder under a source declaration contract.
At that milestone, the next stop was `super(Model.of(""))` in OatSelectionColumn.java
at offset 5192, line 98: the compiled AbstractColumn constructor was not yet reviewed.
The primary report therefore retains three unresolved variables, two unresolved
constructors and 20 unresolved markup entries. These are not 20 proved missing IDs.
Text reports put the first source-layout stop ahead of cascading diagnostics;
the stop names the first effect whose proof is unavailable.

Later ask/confirm action protocols and table/CRUD callbacks remain separate work.
The new declarations do not execute constructors or select current row state,
model values, visibility or business outcomes. A fixture-only page using the real
OatConfirmDialog source has 24 inferred matches and one constructor placement,
shared by all three MCP views. CustomerPage also advances past OatConfirmDialog;
its next stop is OatPageHeader constructor-ID provenance.

Useful declarations are already available separately: the `dataTable:toolbar`
registration has four inferred matches through the
[immediate content factory contract](content-factories.md), and emptyDataTable's
empty-state toolbar has four inferred matches through the
[table toolbar contract](table-toolbars.md). Optional navigation contexts expose
these in the existing MCP structure response. They do not prove the initial
hierarchy, CRUD effects, runtime rows or current visibility.

MCP decision: existing tools now reuse the shared constructor content declarations and
precise stop diagnostics with unchanged request/response schemas. The Project
tree independently marks these classes by inheritance. See the
[constructor-owned content contract](constructor-owned-dialog-content.md) for the scope and its limits.

The twenty-third milestone's read-only Wicket 10.11.0/11.0.0 corpus runs reproduced these findings.
All 36 primary binding/finding summaries and prior source-template values are
unchanged. DataTablePage and CustomerPage retain constructor-owned content notes
and identified their later stops. See that milestone's
[verification record](wicket-oat-confirmation-content-evidence.json).

The twenty-fourth milestone advances DataTablePage through the OatSelectionColumn
AbstractColumn constructor delegation at offset 5192. The next stop is the
LambdaColumn constructor used by OatBadgeColumn.java at offset 1700. Header/cell
population and detach are deferred callbacks; this change does not supply a
complete DataTable initial graph or execute business actions. See the
[column construction contract](column-construction.md).

The twenty-fifth milestone passes OatBadgeColumn's LambdaColumn delegation and
OatNumberColumn's signed constant defaults. DataTablePage next stops at the
FormatStyle.MEDIUM default in OatDateColumn.java, offset 1884 (line 43).
The enum's compiled initialization has no contract in this slice. Its primary
hierarchy stays unresolved; both separate toolbar contexts retain four inferred
bindings. See [the LambdaColumn contract](lambda-columns.md).

The twenty-sixth milestone passes OatDateColumn's JDK FormatStyle defaults and
then the boolean/actions columns. DataTablePage next stops at its own offset 9841
(line 205), where InvoiceProvider.iterator() calls filtered() on the page. The
initial hierarchy remains unresolved and both existing toolbar contexts retain
four inferred bindings. See [the enum-storage contract](date-column-enums.md).

The twenty-seventh milestone passes InvoiceProvider allocation at DataTablePage
source offset 5255 without evaluating its filtered() queries. The next stop is
the compiled DataTable constructor in OatDataTable.java, offset 19041 (line 435).
The primary hierarchy remains unresolved; both existing toolbar contexts retain
four inferred matches. See [the provider contract](provider-construction.md).

The twenty-eighth milestone passes the compiled DataTable constructor used by
RowsTable at OatDataTable.java offset 19041 (line 435). The next stop is
DataTable.addTopToolbar at offset 4624 (line 96). Source wrappers retain the
framework's eager methods; only deferred row/cell overrides are reviewed. No
framework-owned children, provider data or runtime callbacks are inferred.
See [the DataTable construction contract](datatable-construction.md).

The twenty-ninth milestone passes fresh top-header construction and registration.
The next stop is NavigationToolbar construction at OatNavigationToolbar.java
offset 898 (line 19). Finding/binding summaries and older optional contexts are
preserved; DataTablePage adds the header/navigation wrapper files to its source
closure for freshness (35 to 37 dependencies). See
[the registration contract](toolbar-registration.md).
