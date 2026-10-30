# Reviewed scalar expressions and computed IDs

WicketLens can calculate a bounded set of scalar expressions over reviewed
source values. This proves more conditions and resolves IDs assembled from
immutable configuration without executing application code.

```java
private String componentId(String prefix, int index) {
    return prefix + (index + 1);
}
if (2 * 3 >= 6) {
    add(new Label(componentId("item", 2))); // item3
}
```

This extends [conditional setup](conditional-setup.md),
[source accessors](source-accessors.md), and [immutable providers](provider-state.md).

## Supported values and operations

| Operation | Supported subset |
| --- | --- |
| Arithmetic | Primitive byte, short, char, int, and long; unary `+`/`-`, binary `+`, `-`, `*`, `/`, `%` |
| Integral shifts and bitwise operations | `<<`, `>>`, `>>>`, `&`, `\|`, `^`, and unary `~` |
| Numeric comparisons | `<`, `<=`, `>`, `>=`, `==`, `!=` on reviewed integral values |
| Boolean equality | `==` and `!=`, alongside the existing `!`, `&&`, `||` |
| Eager booleans | Primitive `&`, `\|`, and `^`, after auditing every operand |
| Integral casts | Explicit casts among supported integral types |
| String construction | `+` over known String, integral, char, and boolean values, in Java evaluation order |
| String content equality | Resolved `java.lang.String.equals`, with a known nonnull String receiver and a reviewed scalar argument |

Inputs come from literals, stable parameters/locals, private final instance state,
and reviewed single-return getters. Results can flow through the same source
helpers, factories, constructor configuration, and selected branches as ordinary
scalar values. Fresh provider/component returns retain their existing contracts.
Custom component constructors still need the existing forwarding contract;
transformations inside those constructors are not added by this extension.

Each operand is validated before folding. The interpreter supplies normalized,
typed values to IntelliJ's constant-expression helper through a temporary expression
containing only escaped scalar constants. It does not ask that helper to resolve
application fields or execute methods. Java promotion, integral overflow,
narrowing casts, division/remainder signs, and concatenation order are preserved.
Assignments and scalar returns must remain type-compatible, including legal
constant narrowing; invalid narrowing is not silently treated as a cast.

The semantics follow [JLS 25 §5.6](https://docs.oracle.com/javase/specs/jls/se25/html/jls-5.html#jls-5.6),
[§15.17–15.21](https://docs.oracle.com/javase/specs/jls/se25/html/jls-15.html#jls-15.17),
and [String.equals](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/lang/String.html#equals(java.lang.Object)).

## Unknown values and effects

Unknown primitive arithmetic or comparisons remain unknown. A later boolean
operation can still prove an independent result, such as `unknown + 1 > 0 || true`,
when the inspected expressions cannot introduce unsupported effects or exceptions.
Division/remainder require a known nonzero divisor. Unknown or zero divisors keep
uncertainty even when a later boolean operand would otherwise force a result.

String content does not establish reference identity: String `==`/`!=` remain
unsupported. An unknown `.equals()` receiver cannot be assumed nonnull. Primitive
unboxing through source arguments, scalar returns, or selected ternary arms is
not assumed safe. Null comparisons/conversion, boxed arithmetic, floating point,
implicit Object `toString()`, other library calls,
compiled constant fields, mutable/nonprivate state, and general branch merging
remain outside this contract. Existing constructor, initialization, dispatch,
escape, recursion, and opaque repeater-data guards still apply.
The [source-constant extension](static-constants.md) now resolves reviewed source
`static final` String/integral/boolean fields, with separate initialization and
shared-editing guards.
The [bitwise extension](bitwise-expressions.md) adds reviewed shifts, masks,
complement, and eager primitive boolean operators, including their evaluation guards.

## Computed IDs and editing

A known computed String can establish a Java/HTML match, a missing counterpart,
and the usual report navigation. Its origin is the whole source computation,
not one chosen fragment. Java-only inspections highlight that expression when
its origin belongs to the inspected page file; HTML
inspections retain their normal attribute ranges.

Computed IDs do not offer literal-based paired rename, fragment completion,
fragment coloring, or literal-pair references. This includes `"title" + ""`,
where a fragment happens to equal the full ID. Computed-expression problems do
not offer fixes that rewrite a fragment.

An ordinary literal ID also loses paired rename and ID completion if that same
literal value feeds a comparison or another calculation. Otherwise an edit could
switch its branch or change another computed ID without updating its markup.
This guard also applies when the comparison occurs after component construction.
Independent literal IDs retain their existing native editor features and guards.

Computed-origin and editing-dependency flags are value-only snapshot metadata.
Consulted source files participate in freshness checks; reanalysis updates results
after configuration/getter edits. Temporary PSI does not modify project sources
or invalidate an otherwise current snapshot. JSON schema 1 is unchanged.

## Bounds, evidence, and demo

The existing 12-level guards, 128 frames, 256 combined instances, 24 source files,
and 5,000 steps remain. Computed Strings are limited to 4,096 characters; temporary
fold expressions are limited to 16,384 characters. Unsupported or exhausted
calculations fall back to conservative observations.

`WicketScalarExpressionTest` checks Java arithmetic, promotion, overflow, casts,
comparisons, configuration, unknown/nullable values, effects, bounds, provenance,
and deterministic read-only exports. `WicketScalarExpressionEditorTest` checks
whole-expression problems, edit guards, ordinary editing, and source freshness.

Open `example.ExpressionSetupPage` for **5 MATCHED** bindings: `headingTitle`,
`item3`, `status`, `form`, and its `name` child. Two IDs are computed; the others
are ordinary literals. Two Java files and the HTML companion participate in
freshness checks. Real application accuracy/performance evaluation remains deferred.
