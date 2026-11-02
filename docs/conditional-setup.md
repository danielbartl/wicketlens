# Boolean choices and agreeing setup branches

WicketLens follows one setup path when a boolean condition can be proved from
reviewed source values. For an unknown reviewed primitive condition, it can also
confirm components whose IDs, types and parent chains agree on every bounded
setup path, while retaining conditional alternatives.
Application code is never executed.

```java
private Label heading(boolean primary, String first, String second) {
    if (primary) {
        return new Label(first);
    }
    return new Label(second);
}
add(heading(false, "unused", "title"));
```

Only the `title` component is observed, with its ID origin at the caller literal.
This extends [bounded source setup](source-setup.md),
[immutable providers](provider-state.md), and [source accessors](source-accessors.md).

## Conditions and selected paths

- Conditions use primitive boolean literals, known parameters/stable locals,
  private final instance configuration, or reviewed single-return source getters.
  Dispatch, immutable origins, construction, and static initialization must still
  satisfy their existing contracts.
- `!`, `&&`, and `||` follow Java evaluation order. A short-circuited operand is
  skipped, including its effects. Unknown primitive inputs can still have an
  independent result: `unknown || true` is true, while `unknown && false` is false,
  provided every operand that must be inspected is a reviewed read.
- `if`/`else`, nested blocks, and ternary expressions select one proven branch.
  Ternaries can supply IDs, component/provider construction, scalar configuration,
  or a read-only accessor's result. Selected values retain their existing origins
  and identities; a computed boolean never acquires an ID literal origin.
- Bare returns stop their constructor, void helper, or initialization method.
  Value returns can exit a selected factory branch. The caller resumes normally,
  and a constructor return does not skip the later initialization phase. Hooks
  still require their leading direct `super.onInitialize()` call.
- A private final page field may be assigned in the selected constructor branch.
  Reads in skipped statements, short-circuited operands, and the tail after a
  proven return do not invalidate that executed setup. Other field uses retain
  their existing escape and mutation guards.

These choices follow [JLS 25 §14.9](https://docs.oracle.com/javase/specs/jls/se25/html/jls-14.html#jls-14.9),
[§14.17](https://docs.oracle.com/javase/specs/jls/se25/html/jls-14.html#jls-14.17),
and [§15.23–15.25](https://docs.oracle.com/javase/specs/jls/se25/html/jls-15.html#jls-15.23).

## Agreeing unknown branches

```java
public ExamplePage(boolean compact) {
    if (compact) {
        add(new Label("title", "Short"));
    } else {
        add(new Label("title", "Long heading"));
    }
}
```

This establishes one `title` binding without guessing `compact` or a runtime
caption. The same source interpreter validates each complete setup path,
including constructor/initialization order, helper and fresh factory calls,
field state, returns, and the statements after the branch. Confirmation requires
identical component IDs, declared concrete types, and complete parent paths on
every path. Child insertion order may differ.

### Partially agreeing graphs

```java
if (compact) {
    add(new Label("title"));
} else {
    add(new Label("title"), new Label("details"));
}
```

The `title` binding is confirmed; `details` is retained with `conditional: true`,
`UNRESOLVED` confidence and an `UNRESOLVED_BRANCH` finding. Optional components
cannot produce definite missing-pair findings in Java or HTML. Their known
possible path blocks uncertainty at that path and its descendants, allowing
unrelated common bindings and genuinely absent counterparts to remain useful.

Groups include the component type of every ancestor. Conflicting types or
parents produce separate conditional alternatives rather than selecting one
type or merging their children. An allocation moved between parents receives
distinct snapshot node identities. A component variant can be conditional even
when its ID exists on every runtime path, because its type or parent chain
differs. These are possible structural variants, not branch probabilities or
runtime model values. Node IDs remain snapshot-local.

Every complete path must still validate. Unknown IDs, duplicate sibling IDs
within a concrete path, an unsupported effect, or an exhausted proof budget
reject the entire upgrade; a seemingly common prefix cannot bypass those checks.

### Unknown ternary expressions

```java
add(compact
    ? new Label("title", "Short")
    : new Label("title", "Long heading"));
add(new Label(compact ? "summary" : "details"));
```

The same bounded replay handles `?:`. The first expression confirms `title`
and retains both construction/ID origins. The second retains conditional
`summary` and `details` variants. Supported ternaries can also select local
values, component/provider factory results, immutable provider configurations,
reviewed read-only accessor results, captions/model parameters, and receivers.
Caption/model values and which arm runs remain unavailable to clients.

Each path evaluates the condition first and only its selected operand, preserving
source purity, dispatch, fresh-factory and assignment/unboxing checks. Constructor
arguments are interpreted once in Java order; tracing an ID reuses its argument's
value even when a custom constructor forwards a nonfirst argument. This prevents
one ID ternary from consuming two choices or allocating a provider twice.
Numeric expressions keep their conditional result type in subsequent reviewed
operations. Nullable unboxing, null/unknown IDs, null component additions, and
possible unsupported effects still reject the whole upgrade. A known condition
can continue to skip an unsupported dead operand.

The current slice accepts graphs with known evaluated IDs, including pending
queues whose only diagnostic is `UNRESOLVED_QUEUE`. Queue placement is checked
separately for each feasible path after markup extraction; see
[the queue branch contract](queueing-repeaters.md#queues-across-unknown-branches).
Reviewed repeater templates also participate, retaining transparent logical items
and inferred template bindings; see [their branch contract](queueing-repeaters.md#repeater-templates-across-unknown-branches).
Other path diagnostics and scope/effect warnings stay outside merging.
Informational template/count warnings are preserved. Conditional provider
constructor assignments remain unsupported.

### Computed IDs across branches

```java
String prefix = "user";
add(new Label(prefix + "Name"));
if (compact) add(new Label("hint"));
```

The existing reviewed evaluator proves `userName` on every path while `hint`
stays conditional. Merging compares evaluated ID values, component types and
complete typed parent paths. Different expressions that yield the same ID can
share one binding; differing values/types/parents remain conditional alternatives.
The scalar/constant evaluator and its purity, type, overflow, length and source
dependency rules are reused without adding arbitrary expression execution.

Each origin retains its kind. Literal alternatives refer to whole string literals;
computed alternatives refer to whole calculations or constant-use expressions.
A merged node's `computedId` is true if any origin is computed. Mixed origins
work regardless of which path is analyzed first. In analyzed page files, genuine
whole-literal origins retain coloring and read-only references; equal-valued
fragments of computations never acquire literal-ID behavior. HTML references can
use an actual literal alternative. Computed-only groups keep their existing
report navigation without inventing literal pairs.

Missing-markup inspections cover each origin using the corresponding literal or
whole-expression range. Merged IDs still disable paired rename and Java ID
completion. Unknown/unsafe computations, invalid IDs, possible effects, duplicate
paths and exhausted limits still reject the entire upgrade.

### Correlated stable booleans

```java
boolean expanded = !compact;
if (compact) add(new Label("title"));
if (expanded) add(new Label("title"));
```

The two checks establish exactly one `title` addition. A stable unknown primitive
boolean has an identity within one analysis path. Its first branch decision
records a path fact; subsequent reads, direct local aliases, parentheses and
negation reuse that fact. An alias created before the first decision keeps its
identity. Transparent reviewed boolean accessors and parameter passing preserve
the same value, including through existing audited private final storage.

Unknown primitive boolean parameters and once-initialized local snapshots get
separate identities. A comparison stored once as `boolean positive = count > 0`
can therefore be checked repeatedly. Distinct parameters, independent snapshots,
provider values, and independent helper invocations do not share identities just
because their names or PSI declarations match. Known symbols are ordinary boolean
values for subsequent existing short-circuit and scalar operations. Comparisons
of the same tracked integral input can now reuse canonical predicate identities,
including when captured in different boolean locals; see the integral extension below.

These are invocation-local proof facts, not exposed runtime values or persistent
assumptions. Parameter/local assignments remain unsupported; mutable/nonprivate
fields, boxed conditions, opaque calls and reachable effects keep their existing
guards. Reviewed compound `&&`/`||` predicates over those stable inputs now retain
their input relationships, as described below. Reviewed integral comparisons
also retain their stable input identity. The engine never correlates opaque reads
from expression text. Per-input integral ranges now connect threshold facts;
relations between distinct unknown values and symbolic arithmetic remain outside
the subset.
Such programs may still conservatively fall back even when equivalent at runtime.

### Compound stable predicates

```java
if (authenticated && expanded) add(new Label("title"));
if (!authenticated || !expanded) add(new Label("title"));
```

The checks establish exactly one `title` binding. Reviewed `&&`/`||` expressions
retain invocation-local groups over stable primitive boolean values and known
boolean constants. Negation, unchanged local snapshots, reviewed accessors/helper
parameters and existing private final storage preserve those groups. Identity
comes from the original inputs, never expression text or parameter names.

When an unknown group controls a branch or ternary, bounded replay chooses its
needed input facts in short-circuit order. Other reads of those inputs reuse the
facts. Nested groups, reordered pure inputs and De Morgan complements can agree
without inferring the actual runtime flags. Known independent results such as
`flag || true` remain known without spending exploration choices. Ordinary and
template/queue graph merging, origin metadata and editing guards are reused.

Group construction retains the existing read-only operand audit and Java
short-circuit rules. A potentially evaluated unsupported call/effect still
rejects expansion; later proof reasoning does not waive that audit. Primitive
boolean equality and eager operators now extend those groups as described below.
Stable integral comparisons now supply shared predicate inputs under the bounded
contract below. A numeric comparison stored once in a stable boolean local can
also be an input; untracked arithmetic/comparison results remain conservative. Mixed expressions
containing an untracked unknown result retain conservative fallback.

The existing three-independent-choice/eight-path limit applies to needed input
decisions, with one shared 5,000-step budget. Stored groups additionally retain
a 12-level proof-depth bound so alias chains cannot build an unbounded value
graph. Facts/groups do not leave the analysis session or add export fields.

### Primitive boolean equality and eager operators

```java
if (first == second) add(new Label("title"));
if (first ^ second) add(new Label("title"));
```

Development builds also retain stable input relationships through primitive
boolean `==`, `!=`, `&`, `|` and `^`. Equality/inequality and parity can agree
with complementary short-circuit predicates. Aliases, negation, reviewed
accessors/helper parameters and audited private final storage reuse the same
bounded groups. Boolean equality chains retain their parsed grouping and parity;
they do not mean that every input has the same value.

Every eager source operand is audited from left to right before inspecting its
proof group. A decisive constant or a known input fact cannot hide an effect,
unsafe unboxing or division risk in another eager operand. Group reasoning may
avoid unnecessary input decisions only after that audit. Known eager independent
results still fold without new choices. Primitive boolean semantics follow
[JLS §15.21.2](https://docs.oracle.com/javase/specs/jls/se25/html/jls-15.html#jls-15.21.2)
and [§15.22.2](https://docs.oracle.com/javase/specs/jls/se25/html/jls-15.html#jls-15.22.2).

Boxed/reference equality, mutable inputs, untracked unknown calls and unsupported
numeric comparisons stay outside this correlation contract.
A once-stored comparison can supply a stable boolean input. Existing integral
comparisons and bitwise folding retain their prior scalar rules. Per-input range
reasoning below adds no dependency or export field. The same three-choice/
eight-path/5,000-step and 12-level group-depth limits apply. Common/conditional
graphs, queued placement and inferred repeater templates use the shared pipeline.

### Stable integral threshold comparisons

```java
if (count > 0) add(new Label("title"));
if (count < 1L) add(new Label("title"));
```

Development builds track invocation-local identities for unknown `byte`, `short`,
`char`, `int` and `long` parameters and once-initialized local snapshots. Direct
aliases, implicit integral widening, reviewed scalar/predicate accessors and
existing audited private final storage preserve a tracked identity. Independent
parameters, calls and arithmetic snapshots stay distinct, even if their source
text is identical. Arithmetic and explicit casts do not preserve symbolic
relationships; storing an unknown result once can create a new local identity.

Comparisons of one tracked value with one known reviewed integral constant use
the existing boolean symbols and groups. All six operators are supported:
`<`, `<=`, `>`, `>=`, `==`, `!=`. Reversed operands are normalized, as are adjacent
integer cuts: `count > 0` and `count >= 1` share a fact, with `count < 1` as its
complement. Repeated comparisons and reviewed predicate calls therefore reuse
facts instead of introducing unrelated choices. Self-comparisons of the same
tracked value have their exact primitive meaning.

Known constants retain the scalar evaluator's promotion and provenance checks.
Primitive type bounds can prove an out-of-domain comparison without choosing
a runtime value. Long endpoints are guarded before adjacent-cut arithmetic;
there is no floating-point conversion or wraparound in threshold keys. Rules
follow [JLS §15.20.1](https://docs.oracle.com/javase/specs/jls/se25/html/jls-15.html#jls-15.20.1)
and [numeric promotion](https://docs.oracle.com/javase/specs/jls/se25/html/jls-5.html#jls-5.6).

Facts for different cuts, equality and ordering now combine for each tracked
input through the bounded range extension below. Relations between two distinct
unknown values remain unsupported. Separately recomputed unknown arithmetic
remains uncorrelated. Boxed/floating-point values,
mutable reads, assignments, opaque calls, possible effects and division risks
retain their guards. Comparison facts never turn an unknown numeric value into
a computed component ID, even after an equality test against a constant.

The existing three-choice/eight-path/5,000-step and 12-level group-depth limits
remain. Distinct predicate facts can consume separate choices even when they use
one integral input. Internal numeric identities and threshold facts are not
exported. Common/conditional graphs, typed origins, scoped findings, item-template
confidence, per-path queue placement and editing guards reuse the existing pipeline.

### Per-input ranges and excluded values

```java
if (count < 0) add(new Label("title"));
if (count == 0) add(new Label("title"));
if (count > 0) add(new Label("title"));
```

These checks establish one common binding through three feasible ranges. Each
analysis session tracks a closed interval and excluded equality values for each
stable integral identity. Choosing a reviewed comparison intersects that state;
later ordering/equality predicates can be settled without another choice.
For example, `count > 5` implies `count > 0`, while `count == 10` settles both
ordering directions. A false equality excludes one value without choosing a side.

Excluded endpoints are trimmed after intersections, so a small interval can
become a singleton. Bounds remain inclusive and exact at both long endpoints.
The interpreter never enumerates a primitive domain; exclusions come only from
bounded choices. Independent inputs have independent range state. Aliases,
implicit widening, reviewed accessors/storage, boolean groups and ternaries use
the same existing identities and replay engine.

Only necessary decisions count against the existing three-choice/eight-path
budget. Three ordered thresholds on one input have four feasible ranges, while
three independent numeric inputs can still require eight paths. A fourth needed
decision still rejects the whole upgrade. Range queries/refinement share the
5,000-step budget and cancellation checks; partial proofs do not bypass limits.

Prior range facts can settle a later test and skip its dead short-circuit operand
or body. They do not waive eager operand auditing or the initial purity audit
of an unresolved compound expression. Ranges do not supply numeric values to
computed IDs, arithmetic, casts or division checks, even for a singleton. Existing
mutation, boxing, opaque-read and exception guards remain. Relationships between
distinct unknown values and symbolic arithmetic are still pending.

The existing graph/matcher/export pipeline retains conditional presence, scoped
absence suppression, inferred item-template confidence, per-path queue placement
and editing guards. MCP reuses structure/context/validation fields; no interval,
excluded-value set or runtime number is exported, and no tool/schema/dependency
is added.

Merged nodes retain their primary construction/ID locations and all alternative
locations as value snapshots. In analyzed page files, both literal origins support
coloring, navigation to markup, and missing-markup problem ranges. Helper-file
origins retain the existing editor page-discovery boundary; their locations are
available in the shared report and MCP export. HTML navigation uses the primary
representative. Paired rename and Java ID completion are disabled for merged nodes;
changing one branch would invalidate the proof. The existing shared graph feeds
the sidebar, inspections, export, and MCP.

## Conservative boundaries

Branch-specific structure remains unresolved even when common components are confirmed.
Mutable/nonprivate instance fields, nonconstant static flags, boxed booleans, opaque injected
receivers and effectful conditions
are outside this contract. A condition cannot hide mutations behind a constant
logical result if those mutations may need evaluation.
The [scalar-expression extension](scalar-expressions.md) adds reviewed integral
arithmetic/comparisons, String content equality, and computed String IDs, with
separate provenance and editing guards.
Reviewed source constant flags and counts can now select paths under the
[source-constant contract](static-constants.md).

Fresh component/provider factory returns keep their freshness and type checks.
Cached components remain unsupported. A method returning an existing provider or
scalar still needs the single-return read-only contract; an `if` statement does
not turn it into an accessor. A single-return ternary can qualify when its condition
and selected value are reviewed reads.

Provider constructors retain their assignment-only contract; conditional provider
constructor assignments are not added here. Ordinary loops, exceptions, recursion,
and other lifecycle effects remain unsupported when reached. Known conditional
item callbacks are supported, but returns from symbolic RepeatingView foreach
bodies stay unresolved: unknown iteration counts cannot justify skipping the
containing method's tail. Existing model-data calls retain their opaque warnings.

If expansion fails, direct discovery keeps its conservative findings. Standalone
unexpanded root setup calls invalidate direct root attachment confidence, including
when no local initialization hook is present. Possible mutations cannot leave an
earlier direct attachment confirmed merely because it precedes the helper call.
This guard also covers opaque root calls nested in constructor arguments or
possible ternary operands, including `this`-qualified calls. Rejection of the
source expansion retains the direct analyzer's ordinary observations and
uncertainty; it does not mark every independently observed binding unknown solely
because a different component has a dynamic ID.

## Editor behavior, freshness, and bounds

Selected ID literals support native references, completion, coloring recognition,
inspection ranges, and guarded paired rename, including ternary ID operands.
Metadata also recognizes proven immutable scalar field origins for coloring;
shared scalar constants retain their existing per-pair rename restriction.
Skipped factory arguments do not acquire pairs or ID origins. A binding proved
absent can produce an informational mismatch and the usual weak editor warning.
An optional or conflicting unknown branch cannot establish a missing counterpart.

Consulted condition/getter/provider files join snapshot dependencies. Unsaved
condition-source edits invalidate navigation and copying; reanalysis may select
a different branch. Reports retain snapshots rather than PSI state, and the JSON
schema version remains 1. Merged exported components add `branchMerged: true`,
`idRenameSafe: false`, and `javaLocations`/`idLocations` arrays including the
primary and alternative origins. Ordinary components keep their existing shape.
Conditional variants additionally export `conditional: true`; the existing
structure/context tools carry this metadata, and validation projects the shared
`UNRESOLVED_BRANCH` findings and causes. Ambiguous type/parent variants require
a node ID for context selection. There is no additional MCP tool or schema version.
Computed-containing components additionally export `computedId: true`,
`idRenameSafe: false` and typed `idOrigins` entries, each with `location` and
`kind: LITERAL | COMPUTED`. Existing merged `idLocations` arrays remain intact.
This provenance also describes ordinary computed IDs outside branch merging;
literal-only exports retain their shape.

The shared limits remain 12 call/hierarchy levels, 128 frames, 256 combined
provider/component instances, 24 source files, and 5,000 steps. Nested statement
scopes and nested boolean/ternary expressions are each limited to 12 levels.
Budget exhaustion falls back to uncertainty with normal cancellation checks.
Unknown `if` and ternary exploration together permit at most three independent choices along a path, eight
completed paths, and fifteen interpreter runs. All runs share the 5,000-step
budget. Source dependencies are the union across all successful paths, still
limited to 24 files.
The merged union is also limited to 256 components, including conditional variants.
Repeated checks of a bound stable symbol do not consume additional choices;
different symbols and uncorrelated unknown conditions still do.
Queue placement shares one 4,096-container-visit budget across all feasible paths
per resolver pass. Partial placement proofs never upgrade the merged report.
Internal path membership and path counts are not exported.

## Evidence and demo

`WicketConditionalSetupTest` covers selected branches, short circuiting, unknown
and mutable inputs, factories/accessors, return propagation, fields, Page/Panel
roots, repeaters, bounds, and deterministic read-only reports.
`WicketConditionalEditorTest` covers native editing, problem ranges, selected
origins, and condition-source freshness. `WicketUncertaintyTest` also checks
unexpanded root helper effects.
`WicketBranchMergeTest` covers agreeing allocations/factories/fields/parents,
alternative origins, conflicts/effects, exploration limits, editor guards,
determinism, source freshness, and shared structure/context/validation exports.
`WicketPartialBranchMergeTest` covers common subgraphs, conditional type/parent
variants, scoped missing-pair suppression, native editor guards, snapshot identity,
source freshness, response limits, and shared sidebar/MCP projections.
`WicketUnknownTernaryTest` covers allocations, IDs, captions, locals, factories,
providers, receivers, mixed budgets, single evaluation of forwarded IDs, numeric
conversions, null/unboxing/effect guards, editor origins, MCP projections,
source freshness, cancellation and the portable ternary demo.
`WicketBooleanCorrelationTest` covers aliases/negation, snapshots, accessors,
parameter/phase/provider identity, independent invocations and inputs, mutations,
opaque/boxed reads, mixed conditions, budgets, editor guards, MCP projections,
read-only determinism, source freshness and cancellation.
`WicketComputedBranchMergeTest` covers common/conditional computed values,
mixed-origin order, whole-expression editor ranges, fragment guards, constants,
factories/providers, correlation, bounds, MCP metadata and source freshness.
`WicketQueueBranchMergeTest` additionally covers per-path queue placement,
conditional shadowing, changing parents/scopes, region boundaries, optional
presence, scoped uncertainty, shared traversal limits, editor and MCP projections,
source freshness and cancellation.

`WicketRepeaterBranchMergeTest` covers transparent item boundaries, common and
conditional templates, nested repeaters, differing kinds, symbolic populations,
item-local queue placement, source/effect guards, scoped uncertainty, editor/MCP
projections, response bounds, source freshness and cancellation.

`WicketCompoundBooleanTest` covers complementary/nested stable predicates,
aliases/accessors/storage, constant folding and short-circuit guards, independent
inputs and comparisons, queue/repeater integration, exploration/value-depth bounds,
native editing, shared read-only MCP projections, source freshness and cancellation.

`WicketBooleanOperatorsTest` covers primitive equality and eager truth tables,
chains/parity and input relationships, aliases/accessors/storage, queue/repeater
integration, effect/unboxing/division guards, unknown comparisons, bounds, editor
origins, shared MCP projections, source freshness and cancellation.

`WicketIntegralComparisonTest` covers comparison complements/reversal, equivalent
cuts, primitive bounds and long endpoints, stable aliases/storage/accessors,
independent snapshots, guarded arithmetic/casts, boolean/queue/repeater integration,
scoped uncertainty, source/effect/unboxing guards, bounds, native editing, shared
MCP projections, freshness and cancellation.

`WicketIntegralRangeTest` covers feasible partitions, stronger/weaker bounds,
equality/exclusions and tiny intervals, long endpoints, aliases/accessors/storage,
source/eager auditing, no guessed values, conditional scopes, queue/repeater
integration, bounds, native editing, MCP projections, freshness and cancellation.

Open `example.ConditionalSetupPage` for **4 MATCHED** bindings: `title`, `subtitle`,
`form`, and its `name` child. It combines immutable provider flags, short circuiting,
an early factory return, and an initialization guard. Four Java files and the HTML
companion participate in freshness checks. Real application accuracy/performance
evaluation remains deferred.

`example.BranchMergePage` has **3 MATCHED** bindings: `content`, its `title`
child, and `footer`. Its unknown constructor flag changes captions and allocation
locations but preserves the graph. Both construction/ID alternatives are retained
for `content` and `title`; this example does not require Spring.

`example.PartialBranchPage` has one **MATCHED** `title` and conditional
`details`, including an unresolved markup finding rather than a false missing
pair. Its unknown constructor flag changes whether details are added.

`example.TernarySetupPage` has one **MATCHED** `title` with two source origins,
plus conditional `summary` and `details` variants. Stable flag reads share
path facts; this does not add a general condition-correlation solver.

`example.CorrelatedSetupPage` uses a negated local alias to establish one
**MATCHED** `title` with both origins, plus conditional `details`.

`example.ComputedBranchPage` has **2 MATCHED** bindings, `userName` and
`item4`, plus conditional `hint`. It combines concatenation, integral shifts
and a mixed computed/literal ID.

`example.QueueBranchPage` has **2 MATCHED** bindings, `content` and its queued
`title` child, plus conditional `details`. The declaring queue scope remains the
page while markup establishes the actual parent.

`example.RepeaterBranchPage` combines both repeater kinds with common inferred
item children and a conditional queued detail. Runtime row IDs/counts stay unknown.

`example.CompoundSetupPage` has one matched computed-containing `title` with
both origins and conditional `details`, using complementary stable predicates.

`example.BooleanOperatorsPage` has one matched computed-containing `title`
and conditional `details`, using complementary equality/XOR and eager AND.

`example.IntegralComparisonPage` has one matched computed-containing `title`
with both origins and conditional `details`, using equivalent integral thresholds.

`example.IntegralRangePage` has one common computed-containing `title` with
three origins and conditional `details`, using negative/zero/positive ranges.
