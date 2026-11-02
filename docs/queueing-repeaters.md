# Component queueing and repeater templates

WicketLens reconstructs bounded `queue(Component...)` setup and static item
templates for `ListView` and `RepeatingView`. Analysis uses Java/HTML PSI and does
not execute application code, models, or Wicket rendering.

## Queue placement

```java
queue(new WebMarkupContainer("details"));
queue(new Label("name", "Ada"));
```

```html
<section wicket:id="details"><span wicket:id="name"></span></section>
```

The selected markup establishes `details → name`, although both components were
queued on the page. Existing Java children take precedence, followed by the
nearest eligible queue scope. Containers may themselves be queued. Supported
locals, private final fields, closed helpers, and initialization setup can supply
queued components using the existing source contracts.

Placement requires a unique eligible markup occurrence and a proven Java
container chain. WicketLens does not invent missing containers. Panel,
FormComponentPanel, and other `IQueueRegion` boundaries prevent an outside queue
from supplying internal components; header contributions do not dequeue body
components. Repeated IDs are supported in separate queue scopes. Competing
placements, repeated queueing, mixed add/queue uses, incomplete markup, dynamic
IDs, or unsupported setup retain unresolved findings. An unplaced queue component
has `UNRESOLVED_QUEUE`, rather than an unsupported assertion that it is Java-only.

## Queues across unknown branches

Development builds also merge bounded unknown `if/else` and ternary setup that
uses queues. The [branch interpreter](conditional-setup.md) retains each feasible
path's declarations. The existing queue resolver checks the selected markup
separately on each path, including direct-child precedence, nearest queue scopes,
and region boundaries. Resolving the union would be unsafe: a component present
on one path could hide a competing placement on another.

A queued binding gains a parent only when placement is uniquely proven with the
same parent on every path where that component occurs. A mandatory component
cannot be confirmed from placement on just one path. Differing parents, absent
containers, ambiguous candidates and incomplete markup retain `UNRESOLVED_QUEUE`.
Unplaced queue uncertainty covers its known declaring scope, including descendants,
without suppressing unrelated missing-pair findings outside that scope.

An optional queue may have a proven parent, but retains `conditional: true`,
unresolved confidence and `UNRESOLVED_BRANCH`. Placement does not prove presence.
Changing declaring scopes or using mutually exclusive `add` versus `queue`
operations retains separate conditional variants. Repeated queueing or mixed
operations on one feasible path remain unsupported. Reviewed repeater item
templates also participate in branch merging as described below.

All paths share the existing 4,096-container-visit queue budget per resolver pass.
Exhaustion discards partial placement proofs. Internal path membership is local
to the value snapshot and is not exported as runtime branch information. Existing
`queuedInNodeId`, `parentNodeId`, confidence, conditional flags and diagnostics
describe the result in sidebar, editor and MCP tools without new JSON fields.
Merged origins retain their navigation and conservative rename/completion guards.

## ListView

```java
add(new ListView<String>("rows", java.util.List.of("Ada", "Grace")) {
    @Override protected void populateItem(ListItem<String> item) {
        item.add(new Label("name", item.getModelObject()));
    }
});
```

```html
<div wicket:id="rows"><span wicket:id="name"></span></div>
```

The hierarchy contains `rows → item [template] → name`. The logical item has no
runtime ID and consumes no additional HTML tag. The child path is `rows:name`;
its positive match has `INFERRED` confidence. The report does not assert that any
rows exist or predict their count. Nested containers, nested ListViews,
item-local queueing, and closed component helpers are supported when their entire
reachable setup passes the bounded source interpreter.

Anonymous implementations and source subclasses may implement `populateItem`.
Source constructors must forward the ID without transformation and have no
additional construction effects. Custom item factories, lifecycle overrides,
extra instance state, unsupported callback branches, and mutations of captured
page or container instances prevent expansion.

## RepeatingView

```java
RepeatingView rows = new RepeatingView("rows");
add(rows);
for (String name : java.util.List.of("Ada", "Grace")) {
    WebMarkupContainer item = new WebMarkupContainer(rows.newChildId());
    item.add(new Label("name", name));
    rows.add(item);
}
```

WicketLens treats `newChildId()` as an opaque item-ID token belonging to that
RepeatingView. It never runs the counter or invents a numeric ID. One straight-line
item construction, a supported `foreach` body, or a supported `onPopulate()`
override supplies one logical item template. Its descendants match the markup
inside the repeater tag, as with ListView.

The initial contract requires an exact `WebMarkupContainer` item and one item
construction shape per view. Multiple explicit item constructions, custom item
classes, literal runtime item IDs, generated IDs attached to another view,
classic loops, unsupported branches, and other runtime population strategies remain unresolved.
An outer queue is conservatively excluded from a repeater's item scope.

## Repeater templates across unknown branches

Development builds merge reviewed `ListView` and `RepeatingView` templates across
bounded unknown `if/else` and ternary choices. This includes alternative repeater
allocations, callback children, nested repeaters, one symbolic `foreach` body,
and reviewed helpers/fields. The existing interpreter validates every feasible
path; the existing three-choice/eight-path/5,000-step limits remain shared with
ordinary and queue setup. No callback or loop runs in the application.

Typed merge paths include repeater kind, template status and a distinct logical
item segment. Logical items have no runtime ID and consume no HTML path segment.
`rows:name` still selects a template child. Different repeater types/kinds,
ordinary containers and differing parent paths remain separate conditional
variants. Duplicate IDs or multiple item shapes on one concrete path reject the
upgrade; alternative single-item allocations across paths may share one template.

Common item children have `MATCHED` findings with `INFERRED` confidence. This
proves a reviewed template shape, without proving that any rows exist, their
count, runtime IDs, model contents, or which branch an individual row takes.
Optional/conflicting children, items or repeaters retain `conditional: true`,
unresolved confidence and `UNRESOLVED_BRANCH`. Conditional child uncertainty is
limited to its logical path and descendants, preserving unrelated missing-pair
findings. Optional item structures do not become native missing-pair problems.

Item-local queues reuse per-path placement and its shared traversal budget.
Proven placement does not confirm optional presence; an outer queue still cannot
cross an item boundary. Unsupported model calls or scope/effect warnings reject
the entire merge. Informational template/count warnings are retained across all
paths. Existing custom-item, lifecycle, capture, generated-ID ownership and
population guards remain in force.

Merged source origins retain read-only navigation and computed/literal kinds.
Paired rename and Java ID completion retain their merged-origin guards. Existing
MCP structure/context/validation tools expose the same shared templates, findings
and conditional metadata; no new tool or schema field is required. Ambiguous
alternatives require a current node ID, and internal path membership stays local.

## Models, editing, and reports

Model/list parameters and stable data references are opaque. Reviewed data
constructors/factories include Wicket `Model`/`ListModel`, `Model.of/ofList`,
`List.of`, `Arrays.asList`, and `Collections.emptyList/singletonList`. Reviewed
model getters supply opaque item data. Unreviewed instance calls on item data may
retain observed bindings with an explicit scope warning; they suppress absence
claims and paired rename. They are not assumed to be side-effect free.

Proven queue and closed-template bindings support native navigation, parent-aware
completion, paired rename, orange ID coloring, gutter markers, and inspections.
Existing literal-origin, collision, variant, inheritor, and freshness guards still
apply. Queue rename checks the whole declaring queue namespace, even when markup
places its components under different containers. Quick fixes can create missing
HTML for a proven template child; generating Java inside a repeater template is
withheld because it requires choosing a population strategy.

JSON schema 1 adds `queuedInNodeId`, `repetition`, `templateItem`, and `template`
only to components using those features. Logical items have no independent
matching finding. Ordinary report exports retain their existing shape.

The source interpreter retains its limits: 12 call/hierarchy levels, 128 frames,
256 instances, 24 source files, and 5,000 steps. Queue traversal is limited to
4,096 container visits. Failure or exhaustion keeps conservative diagnostics.

## Evidence

The framework audit uses Wicket 10.11.0's published sources, including
`MarkupContainer.queue/dequeue`, `DequeueContext`, `IQueueRegion`,
`AbstractRepeater`, `ListView`, `ListItem`, and `RepeatingView`. See the
[official queueing guide](https://nightlies.apache.org/wicket/guide/10.x/single.html#_component_queueing)
and pinned [source artifact](https://repo.maven.apache.org/maven2/org/apache/wicket/wicket-core/10.11.0/wicket-core-10.11.0-sources.jar).
The constructor audit records the artifact checksum.

`WicketQueueTest`, `WicketRepeaterTest`, and `WicketQueueRepeaterEditorTest` cover
positive bindings, uncertainty boundaries, and native editor behavior.
`example.QueueRepeaterPage` combines all three patterns in the demo. Real
application reconstruction accuracy and performance evaluation remain deferred.

`WicketQueueBranchMergeTest` covers common and optional queues, conditional
shadowing and container types, scope/parent changes, region boundaries, malformed
markup, source guards, scoped uncertainty, native editing, shared MCP projections,
freshness, cancellation and the shared traversal budget. `example.QueueBranchPage`
has **2 MATCHED** bindings (`content` and its `title`) and conditional `details`.
It combines complementary boolean checks with mixed literal/computed ID origins.

`WicketRepeaterBranchMergeTest` covers common/optional and nested item structure,
repeater-kind alternatives, symbolic populations, item-local queue placement,
source/effect/ownership guards, scoped uncertainty, editor navigation and editing
guards, shared read-only MCP projections, bounds, freshness and cancellation.
`example.RepeaterBranchPage` combines both repeater kinds: `people:name` and
`messages:text` are inferred template matches, while `people:details` is conditional.
