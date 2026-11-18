# Source-declared initial dialog and drawer content

The seventeenth development milestone follows DialogPage's initial owned content.
The page has 72 inferred matches and 13 primary branch findings. The latter have
counterparts in three separate item templates: sidebar leaf/group rows and the
four-binding people row. Runtime rows and current dialog state remain unknown.

| Source host | Initial content at `dialog:form:content` |
| --- | --- |
| deleteDialog | Label |
| editDialog | editFields Fragment with name and email wrappers |
| filtersDrawer | filterFields Fragment with status and overdueOnly wrappers |
| detailsDrawer | Plain initial placeholder |

The personDetails fragment is created by a later row callback. It retains
definition navigation without acquiring an initial component instance or path.

## Phase and confidence

The existing strict interpreters remain the first choice. When they cannot
establish setup, a bounded source protocol can supply an explicit
INITIAL_DECLARATIONS fallback graph. It describes declarations selected from
source, not the current runtime hierarchy or a proof of constructor/configuration
effects. All bindings in this fallback are INFERRED, scope remains partial, and
all origins are protected from paired rename. Existing ordinary reports retain
their own confidence and editing rules.

Constructors, configuration, models and event callbacks are not executed.
Constructor/configuration/model effects, validation, visibility, runtime presence
and later open/close/replacement state remain unknown. Confirm/save/delete effects
and content supplied later to open are not projected into the initial tree.

## Source protocol and reused analysis

One selected page constructor must directly construct a source Panel subtype with
a source content setter. The setter accepts one Component, checks its ID with a
direct throwing String-equality guard, adds/replaces it into an owned private final
component field, optionally assigns owned boolean flags and returns this. The
selected constructor chain must declare that parent and one plain placeholder
with the guarded ID. Source setter overrides may directly forward to super and
return this. Wrong IDs, borrowed contents, competing initial placements, parameter
escapes, ambiguous parents and unsupported setter bodies withhold the graph.

Owned constructor nodes reuse the shared source declaration interpreter. Selected
component fields require one direct private final initialization and unique
attachments. Delegation is bounded and preserves component identity. An opt-in
ID-only proof permits null-guarded Model data expressions in non-ID constructor
arguments; the ID itself must still forward unchanged. Other data expressions
retain the existing conservative identity contract.

Anonymous hosts may override source-defined protected void Ajax hooks whose base
implementations are empty. Their bodies remain deferred and supply source
locations. Direct constructor calls into these hooks are rejected, including calls
through unsupported source configuration helpers. Host initialization and custom
markup overrides, anonymous state/initializers and structural configuration remain
unsupported. Fluent source configuration must retain its receiver. Callback and
opaque configuration effects stay unknown rather than becoming initial children.

Initial content must be fresh and unattached. Framework Fragment provider lookup,
definition projection, companion inheritance, matching, and ID provenance are
shared with existing analyzers. Owned transparent containers retain their own
binding while children use ordinary-child precedence and parent fallback. A
framework Form may designate its own declared child as its default button.
Read-only deferred getMarkupId references preserve declared component ownership.
Behavior-only fluent Component.add is a framework receiver summary for a direct
fresh allocation with the concrete method resolved. Factory/alias receivers do
not acquire that summary; behavior effects are not evaluated. Private field inputs, conversion and validation remain
opaque unless a separate existing form context supplies their declarations.

## Native navigation and MCP decision

Host, slot and initial fragment-field IDs navigate through the same value graphs
used by the report and MCP. Reused source companions retain separate markup
providers. Navigation supplies no paired-rename authority for these declarations.

Extend the existing structure, validation and component-context tools with
optional initialContentSetup metadata. No tool, parameter or schema version is
added. The exporter and freshness snapshots reuse the native semantic graph.

| Member | Meaning |
| --- | --- |
| kind, phase | SOURCE_DECLARED_INITIAL_CONTENT, INITIAL_DECLARATIONS |
| hosts[].hostNodeId, hostClassName | Declared source host |
| hosts[].slotParentNodeId, contentNodeId, contentId | Owned parent and initial content declaration |
| hosts[].constructionLocation, constructorLocations[] | Call site and selected source delegation chain |
| hosts[].setterLocation, placementLocations[] | Guarded setter and initial content calls |
| callbackLocations[] | Deferred source declarations |
| constructorExecution, configurationExecution, callbackExecution | not_evaluated |
| runtimePresence, constructorEffects, configurationEffects, modelValues, visibility, laterTreeState | unknown |

Node IDs are snapshot-local and identify observed declarations. Component paths
and hierarchyResolved refer to this source graph, without establishing runtime
presence. Population templates remain separate logical-item scopes and omit this
page-wide host metadata. Event-only component paths do not select the initial graph.

Discovery gates inspect at most 256 constructor allocations and 20,000 PSI elements,
with 128 methods per source owner and 12 source ancestry levels. The setup session
retains its 20,000-step, 96-source, 256-instance and 16-call-stack limits. Each host
allows 128 declared children and 12 constructor/configuration/identity levels.
Exhaustion withholds the entire fallback rather than publishing a partial graph.
Existing fragment expansion, population discovery, source/module/project isolation,
cancellation, the request deadline and the 512-KiB whole-response bound remain in
force. All tools remain read-only.

## Acceptance and remaining work

Acceptance includes both pinned Oat corpus versions, initial content and wrapper
references in both directions, all three MCP views and actual host requests.
Negative cases cover content ID guards, private-final ancestry, borrowed or
competing placements, changed returns, eager hooks, structural configuration,
foreign accessors, anonymous initialization, source initialization overrides,
conditional IDs, unsupported delegation data and declaration budgets. Unsaved
setter, constructor, callback and owned-markup edits invalidate snapshots.

An independent WicketTester fixture observes content installed in the owned form
during construction, deferred Ajax confirmation, initial fragment labels, later
replacement with detachment of the previous content and wrong-ID rejection.
Those runtime observations do not supply values or guarantees to static analysis.

Row-triggered detail content is now covered by the separate
[event-content milestone](event-dialog-content.md). General open/close protocols,
validation/save/delete outcomes, arbitrary constructor/configuration effects and
shared/property-aware refactoring remain later work. This development ZIP is not
installed or published automatically.

Final verification: **2,879** normal tests, **20** actual MCP host tests and **22**
release-checker unit tests passed with zero failures/errors/skips. Both read-only
Oat corpus versions (Wicket 10.11.0 and 11.0.0) passed. DialogPage has 72 inferred
matches, four owned hosts, three initial placements and three item templates
covering 13 primary branch slots. Other 35 ordinary page summaries and all earlier
milestone template summaries match the previous build exactly. All 36 resources
pair and all 318 application input hashes remain unchanged. Plugin configuration,
structure and binary compatibility checks passed against IU-262.10968.63:
`Compatible. 5 usages of deprecated API. 6 usages of experimental API`. The full build/verification run took 23m 10s.
Current source/test/artifact receipts match the ZIP. See the
[evidence summary](wicket-oat-initial-content-evidence.json) for counts and hashes.
The ZIP retains 0.1.0 metadata; no live IDE installation or publication was performed.

Constructor-owned message content is also covered by the
[constructor-owned dialog contract](constructor-owned-dialog-content.md). Selected
source wrappers can install one private-final Label/container into the inherited
guarded slot. Existing initialContentSetup placementLocations identify the
constructor placement; source Ajax hooks and message configuration remain deferred.
All bindings remain inferred, constructor/model/visibility/action effects remain
unknown, and unsupported later setup still withholds the entire initial graph.
The shared native analyzer supplies these facts with unchanged parameters,
selectors, schema, snapshot freshness, cancellation, bounds and read-only behavior.
