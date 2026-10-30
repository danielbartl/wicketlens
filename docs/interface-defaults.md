# Source interface default methods

WicketLens expands source interface defaults when the receiver and target are
proven. Defaults can create fresh components, build containers through closed
helpers, or populate a component parameter. They share the read-only interpreter
and bounds of [source dispatch](source-dispatch.md) and
[immutable providers](provider-state.md).

```java
interface Parts {
    Label label(String id);
    default Form<Void> form(String id) {
        Form<Void> form = new Form<>(id);
        form.add(label("name"));
        return form;
    }
}
class ConcreteParts implements Parts {
    public Label label(String id) { return new Label(id); }
}
Parts parts = new ConcreteParts();
add(parts.form("form"));
```

The default's call to `label` uses the concrete receiver's source implementation.
Private instance/static interface helpers are supported; source interface static
factories are audited too. Defaults can use provider configuration through
implemented component factories. The later [accessor extension](source-accessors.md)
also supports read-only defaults returning known providers or simple scalar values.
The [provider-factory extension](provider-factories.md) also allows proven default
methods to return freshly constructed providers.

## Method selection

- Class implementations win, including inherited methods whose declaring class
  does not itself implement the interface.
- Otherwise, a unique most-specific source default is selected. Inherited
  defaults and a diamond sharing one declaration do not produce duplicate executions.
- Abstract redeclarations suppress parent defaults. Abstract class methods,
  competing unrelated declarations, invalid access/return types, unavailable
  implementations, and unresolved overloads retain uncertainty.
- PSI type substitutions establish parameter signatures. Generic implementation
  arguments retain the existing bridge-cast compatibility guard.
- `Interface.super.method(...)` selects the compile-time default. Its qualifier
  must be a valid direct superinterface of the lexical owner. Redundant/distant
  qualifiers and enclosing-class `Outer.super` are not treated as that call.
  Calls inside the selected body still use the proven runtime receiver.

Final pages can invoke inherited defaults. Nonfinal pages can use explicit
interface-super calls; normal open virtual calls stay unresolved. Unknown injected
receivers do not acquire an implementation from a declared interface default.
The audit follows [JLS 25 §15.12.4.4](https://docs.oracle.com/javase/specs/jls/se25/html/jls-15.html#jls-15.12.4.4).
IDEA PSI supplies overload resolution, super-method relationships, class
substitutions, and signatures; names alone do not establish a target.

## Initialization, editing, and bounds

Default-interface contracts and source ancestry join the literal-only static
initialization audit. Overridden/unused defaults may still require initialization;
class implementations and static factories cannot bypass that guard. Unknown
initialization, initializer blocks, binary default contracts, mutable state,
unknown branches, arbitrary callbacks, and recursive calls remain unresolved.
The [conditional extension](conditional-setup.md) supports proven boolean choices
inside default factories with the same freshness and dispatch guards.

ID literals retain native references, paired rename, completion, coloring, and
possible-unpaired-ID inspections. Shared default literals and repeated origins
retain their rename restrictions. Consulted interfaces, implementations, and
ancestors join freshness checks, including overridden contracts. Reports retain
values rather than PSI environments, and the JSON schema is unchanged.

Limits remain 12 hierarchy/call levels, 128 frames, 256 component/provider
instances, 24 source files, and 5,000 steps. Unsupported setup or exhaustion keeps
conservative diagnostics.

## Evidence and demo

`WicketInterfaceDefaultsTest` covers selection, initialization, generics, guards,
exports, and the demo. Its editor suite covers native editing and freshness.
Fixtures explicitly use Java 25 source language level alongside their Java 25 SDK
so qualified interface-super resolution uses the intended language.

Open `example.DefaultMethodsPage` for **6 MATCHED** bindings: Forms `provided`,
`inherited`, and `explicit`, each with its own `name` Label. They exercise a
configured provider default, an inherited final-page default, and an explicit
interface-super call. A private interface helper builds children through the
receiver's concrete factory. Four Java files and the HTML companion participate
in freshness checks. Real application accuracy/performance evaluation remains deferred.
