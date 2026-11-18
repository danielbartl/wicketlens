# Navigation-toolbar construction

The bounded owned setup pass now reviews compiled `NavigationToolbar(DataTable)`
construction during fresh DataTable toolbar registration. It reuses the existing
same-table, inline allocation, source scope, freshness and escape guards. This
extends construction declarations; it does not reconstruct the toolbar's private
component tree or establish its eventual visibility.

NavigationToolbar calls `newPagingNavigator` and `newNavigatorLabel` **inside its
constructor**. Those calls cannot be treated like header rendering callbacks.
The framework default paging factory is reviewed. A selected source paging factory
may instead return one fresh PagingNavigator/AjaxPagingNavigator wrapper directly,
forwarding its ID and table parameters in order without evaluation. The selected
factory's allocation and constructor chain are audited while its registration
proof is active. Additional statements, aliases, computed/borrowed inputs,
synchronized factories, delegation through helpers and source label factories
remain unreviewed. An inherited source factory is selected by the concrete source
chain; base implementations are not implicitly invoked.

Source navigation-toolbar constructors directly forward their table input through
one super call. They may then set one literal boolean output-markup flag on that
same input, matching OatNavigationToolbar's constructor. Source pager constructors
only forward ID/pageable and an optional label provider in a single super call;
the eager source factory currently supplies the two-input constructor. Both wrapper chains prohibit
instance state and initializer blocks, allow constant static-final storage, and
audit source interface initialization. Unknown compiled subclasses and arbitrary
source hooks are rejected.

PagingNavigator's link/navigation factories run during **later initialization**,
not its constructor. Reviewed source overrides of `newNavigation`,
`newPagingNavigationLink`, `newPagingNavigationIncrementLink`, Ajax `onAjaxEvent`
and the usual render/configure/detach hooks retain unknown bodies and execution.
Source `onInitialize`, object/model hooks and other overrides remain unreviewed.
A fresh unparented DataTable and its private toolbar attachment do not trigger that
later initialization. Attaching or escaping the table before registration still
withholds the existing freshness proof.

## Framework audit

The reviewed Wicket 10.11.0 and 11.0.0 extension sources for NavigationToolbar and
NavigatorLabel are identical. NavigationToolbar creates its span, stores the
colspan model and invokes the two eager factories. PagingNavigator constructors
store pageable/label-provider inputs; AjaxPagingNavigator additionally sets its
output flag. The default NavigatorLabel binds a StringResourceModel over a Model
of LabelModelObject. The Model stores that object; assignment wrapping stores a
component. Provider-size/property/resource evaluation happens later, not during
this construction. AttributeModifier stores its model and inherits an empty
Behavior.bind implementation; it does not evaluate colspan on attachment.

The core sources are **not all identical** across these releases. Wicket 10 stores
models in Component's inline data slots and delegates behavior attachment to
Behaviors. Wicket 11 uses ComponentState for model/behavior storage. Each path was
reviewed separately: model storage/wrapping does not load the fresh label model,
and binding the exact framework AttributeModifier does not call its value model.
StringResourceModel also differs in later detach hooks, outside this construction
contract. Evidence records per-version source hashes, including the version-only
Behaviors/ComponentState implementations, rather than claiming whole-class
identity. A runtime oracle checks eager factory invocation, zero construction-time
provider/link callbacks and their later invocation under WicketTester.

## Native and MCP behavior

A retained diagnostic records completed navigation construction and eager factory
review, separately from the registration diagnostic. It explicitly leaves later
paging-link initialization, Ajax/render callbacks, resource/model evaluation,
provider data, generated IDs and private contents unknown. A later unsupported
operation retains these completed notes while withholding the overall expansion.
No navigator/span/link IDs are exported into the enclosing page graph.

MCP decision: extend the existing page-structure, validation and component-context
diagnostics through the shared semantic analyzer and value snapshots. These facts
help an agent distinguish a reviewed eager factory from the next unsupported
operation. No new tool, parameter, selector, response field or schema is needed.
Tool descriptions and client documents describe the same boundaries. Source
factories/wrappers/interfaces and framework archives join snapshot freshness,
including failed eligibility proofs. Project/module isolation, cancellation,
12-level source ancestry, the shared 20,000-step/96-source setup budget, 30-second
MCP deadline, 512-KiB whole-response bound and read-only behavior are preserved.
Earlier independent declaration analyzers retain their existing policies.

## Application scope and MCP

DataTablePage now passes OatNavigationToolbar construction, its eager
OatAjaxPagingNavigator factory and bottom registration. The next stop is
`chooser.getTrigger().add(AttributeModifier.replace(...))` inside
OatDataTable.newTools: offset 15146, line 316. The returned trigger does not yet
have a reviewed ownership proof for structural mutation. The page's initial
hierarchy therefore remains unresolved. This milestone does not claim the
remaining table-tools/selection/bulk-action setup is supported.

The corpus isolates the original constructor prefix through both toolbar
registrations in a fixture copy, retaining RowsTable and the original source
navigation/pager wrappers. It assigns null to the remaining selection/bulk fields
and attaches the table, keeping the page's provider, seeding and confirmation
setup. Native/MCP views describe the same 26 inferred bindings and nine separate
branch counterparts. No application inputs are edited.

MCP decision: extend the existing structure, validation and component-context
**diagnostics** through shared semantic analyzers and value snapshots. Agents need
the reviewed construction fact, its uncertainty and the exact next blocker.
There are no new tools, parameters, selectors, response members or schema versions.
Descriptions and client documentation cover the new eager-factory contract.
Source constructors, selected/deferred factories, interfaces, markup and framework
archives participate in freshness, including failed eligibility proofs. Project
and module scope remain invocation-local. Cancellation, the existing 12-level
source/interface ancestry limits, 20,000-step/96-source setup budget, 30-second MCP
deadline, 512-KiB response bound and read-only behavior remain in force. Earlier
independent analyzers retain their existing declaration policies.


Actual-host acceptance also checks the three tools through `tools/list`, so their
descriptions state the eager-factory and output-flag contract without retaining
an outdated claim that navigation-toolbar construction is unsupported.

Final milestone verification: **3,346** normal tests,
**34** actual MCP host tests and **22** release-checker unit tests passed
with zero failures/errors/skips. All 151 focused tests passed.
Both read-only Oat corpora (Wicket 10.11.0 and 11.0.0) verify the original pair of
toolbar registrations and eager source navigator factory in an isolated fixture
with 26 inferred matches and nine branch counterparts. Native/MCP facts agree.
DataTablePage next stops at `chooser.getTrigger().add(...)` in OatDataTable.newTools,
offset 15146, line 316. All 36 primary finding/binding summaries, prior groups and
probes, six markers, four saved reports and 318 application input hashes are
preserved. One extra source dependency increases DataTablePage's count from 37 to
38. Configuration, structure and binary compatibility checks passed against
IU-262.10968.63: `Compatible. 7 usages of deprecated API. 6 usages of experimental API`. The full run took 27m 26s.
Current source/test/artifact receipts match the ZIP. See the
[milestone evidence](wicket-oat-navigation-toolbar-evidence.json).
No installation or publication was performed.
