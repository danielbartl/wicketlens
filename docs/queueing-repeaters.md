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
extra instance state, branching callbacks, and mutations of captured page or
container instances prevent expansion.

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
classic loops, branches, and other runtime population strategies remain unresolved.
An outer queue is conservatively excluded from a repeater's item scope.

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
