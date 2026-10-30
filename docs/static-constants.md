# Source constant IDs and conditions

WicketLens resolves a bounded subset of source `static final` constants without
running application code. Constants can supply component IDs, immutable provider
configuration, calculations, and known setup conditions.

```java
public final class Ids {
    public static final String TITLE = "title";
    public static final int COUNT = 2;
    public static final boolean SHOW_FORM = COUNT > 0;
}

add(new Label(Ids.TITLE));
if (Ids.SHOW_FORM) {
    add(new Label("item" + (Ids.COUNT + 1)));
}
```

This extends [scalar expressions](scalar-expressions.md) and
[proven choices](conditional-setup.md). Unknown-branch merging and mutable state
remain separate follow-ups.

## Values and resolution

Supported fields are source-visible, `static final`, and typed as String, boolean,
byte, short, char, int, or long. Interface constants have their normal implicit
modifiers. Names resolve through Java PSI, including qualified class names,
static imports, inherited declarations, and source interface constants. Inaccessible
or ambiguous references cannot establish a value.
Custom component constructor tracing retains its separate forwarding-only
contract; constants embedded inside a custom `super(...)`/`this(...)` chain are
not added by this extension.

Initializers use literals, other reviewed constants, parentheses, supported integral
casts/arithmetic/comparisons, integral shifts/bitwise operations, boolean equality/logical operations, String
concatenation, and conditional expressions. Both conditional arms and all logical
operands must qualify as constants, including those skipped at runtime. String
reference equality, floating point, null, boxed values,
allocations, calls, assignment, and updates remain unsupported. A final field
initialized by a getter or static block is not treated as a constant variable.

Java promotion, narrowing, overflow, and legal constant assignments are retained.
Illegal simple-name forward references, cycles, and invalid narrowing remain
unresolved. Ordinary source values can combine with these constants using the
existing scalar-expression contract.
Shifts, masks, complement, and eager boolean operators follow the
[bitwise contract](bitwise-expressions.md).
Division/remainder by zero remains unresolved even in an unselected constant arm;
this deliberately bounded evaluator does not cover every Java constant expression.

This follows the reviewed subset of
[JLS 25 §4.12.4](https://docs.oracle.com/javase/specs/jls/se25/html/jls-4.html#jls-4.12.4),
[§15.29](https://docs.oracle.com/javase/specs/jls/se25/html/jls-15.html#jls-15.29), and
[§12.4.1](https://docs.oracle.com/javase/specs/jls/se25/html/jls-12.html#jls-12.4.1).

## Initialization and source safety

A constant name has no runtime instance qualifier. Calls such as `holder().TITLE`
and instance-qualified forms such as `this.TITLE` are outside this subset; the
interpreter cannot silently discard receiver evaluation.

Reading an external compile-time constant does not initialize its declaring class.
It can therefore be resolved even when that uninstantiated holder has unrelated
static initialization. Constructing a provider or calling a static factory still
requires the existing initialization audit. That audit also admits reviewed
constant expressions, and the analyzed page's own static initialization cannot
be ignored. Unknown initializers retain conservative fallback findings.

Compiled fields remain unsupported, including otherwise familiar JDK/library
constants. The new resolver caches values only for one analysis invocation.
Constant declarations and intermediate source lookup types join snapshot
dependencies, including inherited static-import targets. Unsaved edits invalidate
navigation/copying; reanalysis resolves the current declarations and values.

## Editing and limits

Constant-based IDs retain the whole use expression as their provenance, for
example `Ids.TITLE`. Java-only inspection ranges cover that reference, and normal
report navigation still leads to the component construction. Java's native
declaration navigation is retained.

Shared constants do not acquire per-pair rename, literal references, fragment
completion, fragment highlighting, or fragment-rewriting quick fixes. An ordinary
literal ID under a constant condition retains its existing editor features.
HTML IDs keep their ordinary highlighting and supported inspection behavior.

Source constants use the shared 24-file and 5,000-step budgets. Constant-reference
chains, source lookup ancestry, and initializer expression nesting are each
bounded to 12 levels. Strings remain limited to 4,096 characters, with the existing
16,384-character bound on sanitized folding expressions. Bounds and cancellation
fall back to uncertainty; reports and exports retain JSON schema 1.

## Tests and demo

`WicketStaticConstantTest` covers resolution, inherited/imported dependencies,
constant calculations, initialization, unsupported/effectful inputs, cycles,
bounds, and deterministic read-only reports. `WicketStaticConstantEditorTest`
covers provenance, problems, editing guards, native Java references, and freshness.

Open `example.ConstantSetupPage` in the included demo for **5 MATCHED** bindings:
`title`, `item3`, `form`, its `name` child, and `status`. Two Java files and the
HTML companion participate in the snapshot. Real application accuracy and
performance evaluation remains deferred.
