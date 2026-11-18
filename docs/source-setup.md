# Bounded source setup

WicketLens expands bounded Java setup through source helpers, factories,
superclasses, custom forwarding constructors, and proven boolean choices. It never executes application
code. If a reachable statement, dispatch target, initialization effect, or budget
is unsupported, the direct analyzer supplies its existing observations and
uncertainty guards instead. Reports remain partial and informational.

The development [source-layout pass](source-layouts.md) follows this interpreter
when additional decorated-component construction contracts are needed. It adds
audited layout construction, fields and transparent markup while retaining the
existing form-conversion and repeater-item guards. It publishes navigation
bindings with paired rename withheld.

The advanced pass also supports [owned form-field summaries](owned-form-fields.md)
for isolated metadata/lazy-input construction. These prove wrapper attachments
while retaining explicit uncertainty about private inputs and runtime behavior.

## Helpers and factories

```java
public ExamplePage() {
    add(label("title"));
    Form<?> form = buildForm("form");
    add(form);
}
private Label label(String id) { return new Label(id, "Heading"); }
private Form<?> buildForm(String id) {
    Form<?> form = new Form<>(id);
    populate(form);
    return form;
}
private void populate(Form<?> form) { form.add(new TextField<String>("name")); }
```

- Calls must resolve to source methods with a closed dispatch target: private,
  final, static, declared on a final class, explicitly selected with `super`, or
  proven by the [source-dispatch extension](source-dispatch.md). Instance helpers
  execute on the analyzed root or a known freshly allocated provider with bounded
  immutable configuration;
  factories/helpers may reside in other source files. Unknown receivers and open
  virtual targets are not resolved by name or assumed to call a leaf override.
- Each invocation has separate parameter/local bindings. Literal values preserve
  their source literal; component arguments preserve instance identity. Repeated
  factories produce separate nodes even at the same `new` expression. Only
  reached helpers are expanded.
- Supported bodies contain declarations, supported calls, constructor assignments
  to private final component/provider/scalar fields, and a component/provider return on a selected factory path.
  A factory returns a fresh instance created in that invocation or a nested
  factory, rather than a parameter/cached field. Additions must resolve to Wicket
  `MarkupContainer.add(Component...)` or supported `queue(Component...)`; its
  returned receiver is preserved. Queue placement is resolved against markup.
- The [provider-factory extension](provider-factories.md) preserves known provider
  allocations, immutable configuration, and ID origins through closed source
  factory calls, including nested and generic factories. Cached/parameter returns
  remain outside that fresh-factory contract.
- The [accessor extension](source-accessors.md) separately supports single-return
  reads of existing known providers and literal-backed String/primitive getters.
  Nested reads preserve immutable state and ID origins; cached component returns
  remain unsupported.
- The [repeater extension](queueing-repeaters.md) additionally interprets bounded
  item callbacks and symbolic RepeatingView foreach bodies, with opaque model data.
- The [conditional extension](conditional-setup.md) follows proven primitive
  boolean choices, short circuiting, ternaries, and guard returns. Bounded unknown
  `if` and ternary branches can also confirm common known-ID subgraphs, keeping optional
  and conflicting type/parent variants conditional.
  Stable primitive boolean parameters and unchanged local aliases/snapshots retain
  identity through negation and reviewed reads, so repeated checks reuse path facts.
  Reviewed compound `&&`/`||` snapshots preserve stable input relationships through
  aliases/accessors/storage. Stable integral inputs compared with reviewed constants
  now share canonical threshold facts; broader numeric relations stay conservative.
  Per-input intervals and excluded equality values connect those thresholds,
  preserving feasible paths without supplying runtime numbers or arithmetic values.
  Primitive boolean equality and eager `&`/`|`/`^` operations now preserve those
  relationships too, after auditing every required eager operand.
  Single-return accessor purity and factory freshness
  remain separate contracts.
  Reviewed computed ID values participate in merging with typed literal/expression
  origins and conservative fragment-editing guards.
  Queue declarations participate too: the existing markup resolver checks each
  feasible path, confirming only agreeing placements and retaining optional presence.
  Reviewed repeater templates merge too, retaining transparent item boundaries,
  inferred common children and conditional variants without runtime row IDs/counts.
- The [scalar-expression extension](scalar-expressions.md) calculates reviewed
  integral and String expressions, preserving Java promotion and conservative
  editing guards for computed IDs and literals used in calculations.
  The [bitwise extension](bitwise-expressions.md) adds reviewed integral shifts,
  masks, complement, and eager primitive boolean operations.
- The [source-constant extension](static-constants.md) resolves reviewed static
  final String/integral/boolean fields through source names and static imports.
  Shared constant IDs retain whole-use provenance and conservative editing guards.
- Unsupported component aliases, escapes, setters, mutations, unreviewed branch conditions, other loops/callbacks, synchronization,
  varargs helpers, recursion, unknown calls, and recovered syntax prevent expansion.
  Static initialization in a factory's source ancestry is checked too. All uses
  of tracked fields in their source files must belong to the traced setup.

Literal-origin values can flow through parameters and stable locals. Empty or
reserved IDs remain `UNRESOLVED_ID`; a forwarded value without a known String value
remains `UNRESOLVED_DYNAMIC_ID`. Proven attachment does not manufacture an ID or
establish a missing counterpart in an uncertain scope.

## Source superclass setup

Source constructors are followed up to the reviewed `WebPage`/`Panel` boundary,
with explicit `super(...)` arguments and implicit no-argument constructors.
Superclass construction precedes each subclass's field initialization and body.
Private final fields remain owned by their declaring source class.

Initialization selects the applicable source `onInitialize()` override and follows
each leading `super.onInitialize()` exactly once to a reviewed framework method.
Missing/late delegation, unavailable source, alternatives at the analyzed entry
constructor, `this(...)` setup delegation, other lifecycle effects, and unsafe
virtual calls remain unresolved. Known empty source ancestors no longer create a
blanket scope gap. Java and markup inheritance are analyzed independently.
The [interface-default extension](interface-defaults.md) also supports proven
source default factories, including explicit interface-super calls and audited
default-interface initialization.
Final pages and final overrides now permit the proven inherited dispatch described
in [the dispatch contract](source-dispatch.md), with constructor ordering preserved.

The ordering/dispatch boundary follows
[JLS 25 §12.5](https://docs.oracle.com/javase/specs/jls/se25/html/jls-12.html#jls-12.5)
and [§15.12.4.4](https://docs.oracle.com/javase/specs/jls/se25/html/jls-15.html#jls-15.12.4.4).
The pinned Wicket sources were checked for `Component`'s final ID storage and
`MarkupContainer.add`'s returned receiver. The queue/repeater extension also audits
dequeue boundaries and item-template semantics; runtime rendering is not simulated.

## Custom component ID contracts

Source constructor delegation can be traced to a reviewed framework constructor:

```java
class Heading extends Label {
    Heading(String text, String id) { super(id, text); }
}
add(new Heading("Heading", "title")); // ID comes from argument two.
```

Parameter identity supplies the ID argument, including reordered arguments and
pure `this(...)` chains. Every delegation argument must be a literal, class literal,
or reference to that constructor's parameter, optionally parenthesized. Assignments,
updates, unknown calls, and other expressions are rejected even in non-ID positions
and before propagating a fixed target literal. Repeater wrappers additionally allow
the list/model factories reviewed by their separate constructor contract.
Fixed constructor literals are recognized too. Wrappers
normally contain only forwarding constructor bodies, no instance methods, initializer
blocks, or nonliteral field initializers. Custom inputs and Panels additionally
support the bounded isolated setup and value conversion described in
[the form-component contract](form-components.md). ID transformations, side effects,
custom `getId()`, and unknown/binary custom ancestors remain unresolved. Custom
component internals/private markup are not flattened into the caller's markup.

## Editor behavior and freshness

Construction locations remain the actual `new` expressions in helpers or source
ancestors. Separate snapshot metadata records literal ID origins for references,
coloring, completion, and inspection ranges. Empty factory-call IDs can receive
completion under a proven parent. Native local inspections only attach problems
to the analyzed file; context-dependent findings with origins in another page's
source remain available in the page report.

Rename requires one editable literal for one matched component path, owned by the
analyzed class's constructor/initialization setup or its own private final
component/provider field declaration. Individual factory/super-call
arguments may qualify. Shared helper constants, inherited implementation literals,
fixed constructor IDs, or one argument supplying several paths do not offer a
per-pair rename. Classes with known project inheritors also do not offer paired
rename: descendant companions may use the same base implementation. A future
refactoring would need to account for all those contexts. The selected HTML ID must
also belong to the analyzed class's own conventional companion: inherited templates
can be shared with sibling pages. This check uses the actual attribute resource,
so an own `wicket:extend` does not make base-template IDs safe to rename. Unique
caller literals paired with IDs in the subclass's own companion remain eligible.
Native preview,
collision checks, and undo remain in use for unique leaf contexts.

Consulted Java files join markup files in snapshot version checks. Unsaved edits
to external factories, superclass setup, or constructor contracts block stale
navigation/copying. Caches retain values, not PSI environments. JSON schema 1
retains construction locations. Dependency metadata is snapshot-only; agreeing
branch components additionally export their complete construction/ID origin lists.
Navigation recreates discarded clean documents in a background read before
rechecking the snapshot. Delayed/duplicate editor-selection events keep the
derived report context after opening an inherited source; selecting a different
file clears that context and resumes normal following.

## Bounds and evidence

Limits: 12 hierarchy/call levels, 128 invocations, 256 component/helper instances, 24 source files,
and 5,000 interpreter steps. Constructor ID tracing has a 12-level/cycle guard.
Cancellation checks run during traversal. Exhaustion falls back to uncertainty.

`WicketSourceSetupTest` covers the four source features and adversarial cases.
`WicketSourceSetupEditorTest` checks native editor integration and freshness.
The `SourceSetupPage` demo combines all four features with inherited markup and
expects **7 MATCHED** findings. Real application accuracy/performance evaluation
remains deferred.

Registered wizard factories have a separate [selection-template contract](wizard-selections.md).
It projects direct Fragment declarations at a reviewed source replacement slot;
child constructor/configuration effects, private inputs and validation remain
unexpanded. These optional inferred contexts do not widen initial setup proof.

The twenty-fourth milestone adds a bounded storage-only construction contract
for source columns whose first compiled superclass is AbstractColumn. The shared
analyzer audits source private data storage and callback-interface initialization.
Header creation, cell population, detach, model values and sort state remain
unknown and callbacks are not evaluated. These column methods supply no generated
children to the page graph. Existing diagnostics retain the reviewed constructor
fact and any later blocking source statement; the full layout expansion is withheld
when remaining setup fails. The existing tools, selectors, JSON members and schema
versions are unchanged. Source wrappers, callback interfaces and the compiled
wicket-extensions dependency participate in freshness; cancellation, source/call
bounds, read-only behavior and whole-response limits remain in force.
See [the column contract](column-construction.md).

The twenty-fifth milestone extends storage-only source columns to the two exact
compiled LambdaColumn constructors. Bound row-function references, receiver
evaluation and callback-interface initialization use the shared source audit;
callback types are retained across opaque aliases. Header/cell/detach/data-model
callbacks and row/model/sort/formatting values remain unknown and are not evaluated.
Existing diagnostics preserve the constructor fact and any later blocking source
statement. The layout expansion is withheld when remaining setup fails. Binary
constructor/guard/function dependencies participate in freshness. Existing tools,
selectors, response members, schemas, cancellation, bounds and read-only policy
are unchanged. See [the LambdaColumn contract](lambda-columns.md).

The twenty-sixth milestone permits direct storage of the four reviewed JDK
java.base FormatStyle enum constants in source columns. Type/static-import
qualification and compiled JRT identity are required. Both FormatStyle and Enum
SDK dependencies participate in freshness, and retained diagnostics preserve each
accepted physical use and any later source blocker. Source/other enums, receiver
expressions, enum/formatter methods and locale/time-zone lookups receive no new
contract. Formatting, row/model values and rendering remain unknown and callbacks
are not evaluated. Existing tools, parameters, selectors, schemas, cancellation,
bounds and read-only policy stay unchanged. See
[the date-column enum contract](date-column-enums.md).

The twenty-seventh milestone distinguishes zero-state source SortableDataProvider
allocation from later iterator/size/model/detach callbacks and page queries.
Reviewed no-argument provider/sort-state construction supplies no row/model/sort
values or generated children. State, initialization, constructor inputs, qualified
super receivers and eager callbacks remain unreviewed. Consumer/table construction
retains its own source-effect checks. Shared diagnostics preserve the allocation
fact and later blocker; source and protocol/sort-state archives join freshness.
Existing tools, selectors, response members, schemas, cancellation, bounds and
read-only behavior remain unchanged. See
[the provider construction contract](provider-construction.md).


The twenty-eighth milestone reviews the compiled four-argument DataTable
constructor inside owned source setup. Source wrappers must retain framework
eager caption/body/grid and inherited methods; only deferred row/cell factory
overrides and constant static storage are supported. Anonymous wrappers, source
ancestors and interface initialization share the same guards. Source
wrapper constructors must consist of one direct super call forwarding their four
parameters in order. Extra statements and computed delegation inputs are
unreviewed; allocation inputs retain their existing effect audit. Provider queries,
column/header/row/cell callbacks, private table children and runtime values remain
unknown. Other table methods, toolbar registration and lifecycle effects require
separate contracts. Existing diagnostics preserve the constructor fact and later
blocker; table/grid archives join snapshot freshness. Existing tools, parameters,
selectors, response fields, schemas, cancellation, bounds and read-only policy
remain unchanged. See [the DataTable contract](datatable-construction.md).


The twenty-ninth milestone reviews fresh owned DataTable toolbar registration and
AbstractToolbar/HeadersToolbar/AjaxFallbackHeadersToolbar construction. Direct
constructor locals or private-final allocations must remain unescaped and
unattached, with only reviewed output-flag setup or prior registrations. Each
inline toolbar receives that same table; locator data is null or a direct
constructor parameter. Source/anonymous wrappers only forward constructor inputs
and declare reviewed deferred hooks and constant storage. Interface initialization,
source ancestors, budgets and cancellation stay audited. Existing diagnostics
record construction and registration after argument review; runtime toolbar IDs,
private contents, sorting, rendering, visibility and provider/model values remain
unknown. Other toolbar/lifecycle protocols require separate contracts. Existing
tools and snapshots supply these same facts with unchanged parameters, selectors,
response fields and schemas. See [the registration contract](toolbar-registration.md).


The thirtieth milestone reviews NavigationToolbar construction within the same
fresh owned table registration proof. Its paging factory is eager: a selected
source override must directly return one fresh PagingNavigator/AjaxPagingNavigator
wrapper forwarding its ID/table inputs. Source label factories and source pager
initialization overrides remain unreviewed. Constructors, interface initialization
and source storage retain effect guards; paging-link initialization, Ajax/render
callbacks, resource/model evaluation, provider data, generated IDs and private
contents stay unknown. Existing diagnostics and value snapshots share these facts
without changing tools, parameters, selectors, response fields or schemas. See
[the navigation-toolbar contract](navigation-toolbar-construction.md).


The thirty-first milestone reviews owned component accessor decoration. A pure
selected source getter on a fresh unescaped local owner can return its own
uniquely allocated private-final framework WebMarkupContainer child for one
literal AttributeModifier.replace behavior. Source dispatch, selected constructor
paths/effects, interfaces, field uses and prior owner uses are guarded. This
provides no private child IDs or editing authority; rendering, model values,
visibility and later state remain unknown. Existing diagnostics and snapshots
share the fact and exact later blocker with unchanged tools, parameters,
selectors, response members and schemas. See [the contract](owned-component-accessors.md).


The thirty-second milestone reviews fresh inline source component fluent chains.
Each unary source enum setter forwards through private-final behavior storage
into an unchanged exact framework Model and returns the same receiver. Concrete/
anonymous dispatch, source constructors, enum initialization declarations, binding
hooks and field uses are guarded. Local/borrowed receivers, computed arguments,
custom models, aliases, extra eager effects and unknown binding remain unreviewed.
Models, rendering, Ajax outcomes, visibility, private contents and later state stay
unknown. Existing diagnostics and snapshots share these facts and the exact later
blocker with unchanged tools, parameters, selectors, fields and schemas. See
[the contract](fluent-component-configuration.md).

[Owned table-reference forwarding](owned-table-forwarding.md) now reviews the
single unchanged table argument from a fresh unattached source owner into the
exact AbstractToolbar constructor. Existing constructor snapshots and effect
checks are reused. Structure, validation and component-context diagnostics retain
that finding and the next independent blocker. Private nodes, runtime toolbar IDs,
cache/attachment state, deferred models and later callbacks remain unknown;
selectors and schemas are unchanged.

[Deferred table-columns reads](table-columns-read.md) now permit the exact final
DataTable getter and size-only use when the reviewed owned table retains exact
JDK List.of provenance. Internal borrowed-reference tracking preserves the guard
through locals; custom collections, element queries, mutation and escapes remain
unreviewed. Shared native/MCP diagnostics retain the finding and later blocker.
Column count/elements, models, rendering, cache/attachment state and callbacks stay
unknown. No selectors, schemas or private nodes are added.

[Null-guarded owned toolbar caches](owned-toolbar-cache.md) now permit one private
source cache assignment and exact same-table registration after the existing
constructor/ownership audits. Scoped semantic reference search rejects aliases,
competing writes and escapes; private-cell replacement retains its own effect
audit. Shared native/MCP diagnostics expose the completed fact and later blockers.
Runtime cache presence/reuse, IDs, private contents, models and callbacks remain
unknown. No nodes, selectors, response fields or schemas are added.

Declaration-scan reuse now lets the original DataTablePage complete initial source
setup within the unchanged work budget: 26 inferred matches and 9 branch findings.
Default native/MCP views share the result. The optional combined page/navigation
response retains its 512-KiB bound and currently returns RESPONSE_TOO_LARGE for this
page. See [the declaration-work contract](declaration-work.md).
