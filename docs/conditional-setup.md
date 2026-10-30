# Proven boolean choices in source setup

WicketLens follows one setup path when a boolean condition can be proved from
reviewed source values. Unknown conditions keep conservative observations and
uncertainty; branches are not merged or selected from their IDs. Application
code is never executed.

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

## Conservative boundaries

Unknown branches remain unresolved even when both contain the same textual ID.
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

## Editor behavior, freshness, and bounds

Selected ID literals support native references, completion, coloring recognition,
inspection ranges, and guarded paired rename, including ternary ID operands.
Metadata also recognizes proven immutable scalar field origins for coloring;
shared scalar constants retain their existing per-pair rename restriction.
Skipped factory arguments do not acquire pairs or ID origins. A binding proved
absent can produce an informational mismatch and the usual weak editor warning.
Unknown conditions cannot establish a missing counterpart.

Consulted condition/getter/provider files join snapshot dependencies. Unsaved
condition-source edits invalidate navigation and copying; reanalysis may select
a different branch. Reports retain snapshots rather than PSI state, and the JSON
schema is unchanged.

The shared limits remain 12 call/hierarchy levels, 128 frames, 256 combined
provider/component instances, 24 source files, and 5,000 steps. Nested statement
scopes and nested boolean/ternary expressions are each limited to 12 levels.
Budget exhaustion falls back to uncertainty with normal cancellation checks.

## Evidence and demo

`WicketConditionalSetupTest` covers selected branches, short circuiting, unknown
and mutable inputs, factories/accessors, return propagation, fields, Page/Panel
roots, repeaters, bounds, and deterministic read-only reports.
`WicketConditionalEditorTest` covers native editing, problem ranges, selected
origins, and condition-source freshness. `WicketUncertaintyTest` also checks
unexpanded root helper effects.

Open `example.ConditionalSetupPage` for **4 MATCHED** bindings: `title`, `subtitle`,
`form`, and its `name` child. It combines immutable provider flags, short circuiting,
an early factory return, and an initialization guard. Four Java files and the HTML
companion participate in freshness checks. Real application accuracy/performance
evaluation remains deferred.
