# Bounded source setup

WicketLens expands bounded Java setup through source helpers, factories,
superclasses, custom forwarding constructors, and proven boolean choices. It never executes application
code. If a reachable statement, dispatch target, initialization effect, or budget
is unsupported, the direct analyzer supplies its existing observations and
uncertainty guards instead. Reports remain partial and informational.

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
  boolean choices, short circuiting, ternaries, and guard returns. Unknown
  branches are not merged. Single-return accessor purity and factory freshness
  remain separate contracts.
- The [scalar-expression extension](scalar-expressions.md) calculates reviewed
  integral and String expressions, preserving Java promotion and conservative
  editing guards for computed IDs and literals used in calculations.
  The [bitwise extension](bitwise-expressions.md) adds reviewed integral shifts,
  masks, complement, and eager primitive boolean operations.
- The [source-constant extension](static-constants.md) resolves reviewed static
  final String/integral/boolean fields through source names and static imports.
  Shared constant IDs retain whole-use provenance and conservative editing guards.
- Unsupported component aliases, escapes, setters, mutations, unknown branches, other loops/callbacks, synchronization,
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
retains construction locations; dependency/literal-origin metadata is snapshot-only.
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
