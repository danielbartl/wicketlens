# Integral shifts, bitwise expressions, and eager booleans

WicketLens can calculate reviewed flag masks and shift expressions in setup,
source getters/providers, and source constants. This extends
[scalar expressions](scalar-expressions.md) and [source constants](static-constants.md)
without executing application code.

```java
private static final int EDIT = 1 << 0;
private static final int SAVE = 1 << 1;
private static final int DEFAULT = EDIT | SAVE;

int flags = DEFAULT;
if ((flags & SAVE) != 0) {
    add(new Label("title"));
}
add(new Label("item" + ((flags >>> 1) ^ 3))); // item2
```

## Supported operations

- Integral shifts: `<<`, signed `>>`, and unsigned `>>>`.
- Integral bitwise AND, OR, XOR, and complement: `&`, `|`, `^`, and `~`.
- Eager primitive boolean AND, OR, and XOR: `&`, `|`, and `^`.

Integral values use the existing byte, short, char, int, and long subset. Shift
operands are promoted independently: a long distance does not make an int left
operand long. Int distances use five low bits; long distances use six. Negative
and oversized distances therefore follow Java masking. Narrow left values and
complement operands promote to int; integral bitwise binary operations use normal
binary promotion. Signed/unsigned right shifts, overflow, precedence, and left
associativity are preserved.
Out-of-range decimal integer tokens are rejected before folding. Java's special
minimum-value decimal tokens are admitted only directly under unary minus;
valid negative hexadecimal/octal/binary bit patterns retain their usual meaning.

The interpreter folds sanitized, typed scalar values through IntelliJ's constant
expression helper. It does not substitute guessed runtime values or execute
application methods. The reviewed rules follow
[JLS 25 §15.19](https://docs.oracle.com/javase/specs/jls/se25/html/jls-15.html#jls-15.19),
[§15.22](https://docs.oracle.com/javase/specs/jls/se25/html/jls-15.html#jls-15.22), and
[§15.15.5](https://docs.oracle.com/javase/specs/jls/se25/html/jls-15.html#jls-15.15.5).
Literal validity follows [§3.10.1](https://docs.oracle.com/javase/specs/jls/se25/html/jls-3.html#jls-3.10.1).

## Eager evaluation and uncertainty

Every eager boolean operand is inspected before folding or proving an independent
result. `unknownBoolean | true` can be true and `unknownBoolean & false` can be
false when all required reads are safe. Eager XOR with unknown input remains
unknown. `false & effect()` and `true | effect()` cannot skip their right operand;
unsupported mutations, opaque calls, possibly null unboxing, and division risks
retain uncertainty even when a constant operand would determine the boolean value.

Numeric bitwise/shift expressions with unknown inputs remain unknown, including
an unknown integer masked with zero. A later short-circuit expression can still
prove an independent result after required operands pass their safety checks.
The existing `&&`/`||` short-circuit behavior is unchanged.

Source constant initializers require every operand to be a reviewed constant
expression. An eager constant expression cannot acquire a value from mutable or
opaque state merely because its other operand determines the result.

Boxed, floating-point, String, and mixed boolean/integral bitwise operands are
outside the subset. Compound assignments (`&=`, `|=`, `^=`, `<<=`, `>>=`, `>>>=`),
increments/decrements, mutable state, and general branch merging remain unsupported.
Custom component constructor tracing retains its existing forwarding contract.

## Editor behavior and limits

Computed String IDs retain whole-expression provenance. Their Java-only problems
highlight the computation, and fragment rename/completion/rewriting remains
withheld. Independent literal IDs under proved masks or eager boolean conditions
retain ordinary references, completion, and guarded paired rename. Literal values
used in a predicate retain their existing computation-dependency guard.

Consulted configuration and constant sources join snapshot dependencies. Unsaved
edits invalidate navigation/copying, and reanalysis can select a different branch.
Reports remain read-only value snapshots with JSON schema 1. Existing recursion,
expression, step, file, String, and sanitized-fold bounds remain in effect.

## Evidence and demo

`WicketBitwiseExpressionTest` covers promotion, width and distance masks, negative
distances, signed/unsigned shifts, complement, truth tables, eager effects and
exceptions, invalid/boxed/mutable inputs, source constants, bounds, provenance,
and deterministic read-only reports. `WicketBitwiseExpressionEditorTest` covers
problem ranges, edit guards, literal editing/completion, and source freshness.

Open `example.BitwiseSetupPage` for **5 MATCHED** bindings: `title`, `item3`,
`form`, its `name` child, and `status`. It combines constant flags, immutable
provider getters, eager booleans, a computed ID, and initialization. Two Java
files and its HTML companion form the snapshot. Real application accuracy and
performance evaluation remains deferred.
