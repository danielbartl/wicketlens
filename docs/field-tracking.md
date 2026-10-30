# Conservative component field tracking

The rules below describe direct field analysis, which remains the fallback.
The [conditional extension](conditional-setup.md) additionally follows proven
constructor branches and omits field uses in statements proved not to execute.
The later [bounded source setup pass](source-setup.md) additionally supports source
factories, private final fields owned by source superclasses, and closed helpers
when the entire reachable setup and field references are validated.

The Java analyzer binds supported private final instance fields to their original
constructions. A field can connect constructor and `onInitialize()` setup without
guessing IDs from field names or expanding arbitrary methods. The existing fifteen
reviewed constructors, exact Wicket add-method resolution, literal-ID rules, graph
matching, and partial-scope contract are unchanged.

```java
private final Form<?> form = new Form<>("form");
private final Label title;

public ExamplePage() {
    title = new Label("title");
    add(form);
}

@Override
protected void onInitialize() {
    super.onInitialize();
    form.add(title);
}
```

## Binding and availability

- The field must be declared on the analyzed Page/Panel class, private, final, and
  nonstatic. Mutable, non-private, static, and inherited fields remain unresolved.
- An inline initializer must be a direct `new` expression with no additional
  writes. A blank final field must have one direct `=` assignment to `new` in the
  class's sole supported constructor. Local aliases, factories, deferred/conditional
  assignments, and alternative/delegating constructors are not reconciled.
- Declaration resolution supplies identity. Unqualified, `this.field`, and
  same-instance qualified `Page.this.field` reads share the construction node.
  Local shadowing resolves to its own declaration. Another instance's identically
  named field is not treated as this instance's field.
- Reads in a constructor must follow the accepted field initialization and any
  explicit superclass invocation. A supported hook can read constructor-initialized
  fields even when its method appears earlier in the source. Inline field
  declarations can appear after constructor declarations without changing their
  phase.

The ordering boundary follows the conventional constructor path in
[JLS 25 §12.5](https://docs.oracle.com/javase/specs/jls/se25/html/jls-12.html#jls-12.5):
superclass construction precedes instance initializers and the constructor
epilogue. Java 25 constructor preambles are outside this first subset. If an
explicit `super(...)` is present, it must be the first nonempty constructor
statement for field attachments to be confirmed. This analyzer is not a general
Java compilation or runtime execution check.

## Uses and effects

All references to a tracked field in the Java source file are checked, including
enclosing/nested code. Except for its accepted constructor write, each reference
must be a component argument or receiver of a resolved Wicket
`MarkupContainer.add(Component...)` within the analyzed constructor or supported
`onInitialize()` override. Parenthesized arguments are supported. Fields passed
to collection `add` methods are escapes, not component attachments.

Aliases, getters/returns, helper calls, casts to aliases, setters, unsupported
mutations, nested/lambda captures, method references, extra writes, and reads
through other instances invalidate the binding. Even an apparently benign setter
is outside the reviewed use contract. Reads in other field initializers or in
other lifecycle methods are not promoted to supported field data flow.

A final reference alone does not establish safe component effects. Field
attachments also require supported constructor control flow and initialization
effects. Opaque root helpers/escapes, root-bound method references, enclosing-root
effects in field initializers, and instance initializer blocks keep those
attachments unresolved. The existing lifecycle guards still apply to both phases.
Where construction identity remains known, literal IDs and local container edges
survive with unresolved confidence. Unsupported bindings retain
`UNRESOLVED_VARIABLE`, with source-located scope causes explaining the field limit.

Each supported field instance has one graph node at its original `new` expression,
shared across argument and receiver uses. Children can be added before or after
their container is attached. Unused constructions are omitted; receiver-only
containers retain unresolved root attachment. Multiple additions of one instance
remain ambiguous. Existing duplicate-path and cycle guards apply unchanged.

## Evidence and retained limits

`WicketFieldTrackingTest` uses actual Wicket libraries and Java/HTML PSI. It covers
Page and Panel roots, both initialization forms, phase/source ordering, container
edges, repeated IDs, shadowing, concrete types, observed locations, hierarchy/JSON
identity, read-only/deterministic analysis, and the unsupported cases above.

The demo `FieldComponentsPage` mixes inline fields and a constructor-assigned
field, attaches its Form in the constructor, and adds three Form children and a
status Label during initialization. Its full-pipeline fixture expects
**6 MATCHED** findings. Maven compilation validates the source against Wicket
10.11.0 without executing a Wicket application.

No new runtime dependencies, persistent indexes, source edits, UI controls, or
JSON schema changes are introduced. PSI-keyed reference maps are local to one
analysis. All reports remain partial and informational. Inherited Java setup,
arbitrary interprocedural/lifecycle effects, reflection, runtime mutation, and
real application accuracy/performance remain outside this evidence.
