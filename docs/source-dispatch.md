# Proven source dispatch

WicketLens can expand source setup when the runtime method target is proven by
the analyzed page's finality or by a fresh helper's concrete allocation type.
This extends the [bounded source interpreter](source-setup.md); it does not run
Java code or search for an arbitrary likely implementation.

## Inherited setup

```java
class BasePage extends WebPage {
    BasePage() { setup(); }
    protected void setup() { add(new Label("baseOnly")); }
}
final class ExamplePage extends BasePage {
    @Override protected void setup() { add(new Label("title")); }
}
```

The final page selects its override, so only `title` is observed. The overridden
base body is a dependency rather than an additional execution. A final override
also establishes a fixed target on a nonfinal page. Final pages may use inherited
nonfinal implementations. An open override on a nonfinal page stays unresolved.

PSI overload resolution first selects the declaration; PSI super-method
relationships identify actual overrides. A same-named overload or private method
does not become an override. Explicit `super` selects the base body, while virtual
calls made inside that body still use the proven runtime target. The interpreter
retains constructor ordering: an override called by a base constructor cannot
read a subclass field before its initialization. Unsupported lifecycle effects
and dispatch outside this contract continue to invalidate expansion.

These rules follow [JLS 25 §15.12.4.4](https://docs.oracle.com/javase/specs/jls/se25/html/jls-15.html#jls-15.12.4.4)
and the construction ordering already recorded in the source-setup contract.

## Known instance helpers

```java
interface Parts { Label label(String id); }
class DefaultParts implements Parts {
    public Label label(String id) { return new Label(id); }
}

Parts parts = new DefaultParts();
add(parts.label("title"));
```

The allocation establishes the concrete class even though the variable has an
interface type. Source superclass implementations, overriding methods, private
helper chains, explicit superclass calls, and ordinary generic interface overrides
are supported. Generic dispatch also checks argument compatibility with the
selected implementation to avoid treating an unchecked bridge cast as success.
The [default-method extension](interface-defaults.md) adds source interface
defaults, class precedence, most-specific selection, private interface helpers,
and explicit interface-super calls, with initialization and ambiguity guards.

Helpers may be direct allocations, stable local bindings, private final fields
initialized at declaration or in the constructor, or known helper parameters
passed through traced source calls. They can return fresh components or populate
component parameters. Repeated factory calls create separate component nodes.
Supported queue/repeater setup can also call these helpers within its existing
scope restrictions.

Closed source factories can also return fresh providers while preserving their
concrete allocation and configuration; see [the factory contract](provider-factories.md).
Read-only source accessors can return existing known providers and simple scalar
configuration through the same dispatch rules; see [the accessor contract](source-accessors.md).

The object contract now includes the bounded
[immutable provider-state extension](provider-state.md):

- Allocations may supply supported configuration or known providers. Source
  constructors assign private final fields and may forward values through a
  leading `this(...)` or unqualified `super(...)` chain.
- Private final scalar/provider state is kept separately for each allocation.
  Cached components, mutable state, and initializer blocks are rejected. Static
  fields may have literal or reviewed [source-constant](static-constants.md)
  initializers; other static initialization is rejected.
- Implemented source interfaces and their default initialization are audited too.
  Unavailable binary contracts, anonymous/local classes, and nonstatic inner helpers are
  outside this contract.
- Reassignment, method-reference escapes of tracked helper fields, arbitrary
  callbacks, unknown branches, recursion, unknown calls, and cached component returns
  prevent expansion. Helpers are not accepted as opaque component/model data.

The [conditional extension](conditional-setup.md) permits proven boolean choices
inside reviewed setup/factory methods while preserving these state and dispatch guards.

Unknown injected providers and results of unknown provider factories do not
acquire a concrete runtime class from their declared interface. Mutable state,
constructor effects beyond the reviewed assignments/delegation, unavailable or
ambiguous defaults, and conditional receiver selection remain unresolved.

## Editor behavior and limits

Navigation retains each component's real construction location. ID references,
completion, native paired rename, and Problems integration use the originating
literal, including an individual caller argument. Existing shared-literal,
variant, inheritor, collision, and uncertainty guards still apply. A shared
implementation literal is not offered as a per-call-site rename.

Consulted interfaces, implementations, and class ancestors join the snapshot's
source dependencies. Editing them invalidates navigation/copying; reanalysis
recomputes dispatch and drops unused implementation dependencies. Caches and
exported reports retain values rather than PSI or helper environments. Helper
objects do not appear as Wicket component nodes, and the JSON schema is unchanged.

The existing limits remain: 12 hierarchy/call levels, 128 invocation frames,
24 source files, and 5,000 interpreter steps. Helper allocations and component
instances share the 256-instance budget. Failure or exhaustion retains the
direct analyzer's conservative observations and diagnostics.

`WicketSourceDispatchTest` covers positive and adversarial dispatch cases;
`WicketSourceSetupEditorTest` adds native editing and freshness checks.
`example.DispatchPage` combines a final page override with interface factories
and expects **4 MATCHED** bindings. Accuracy/performance evaluation on real
applications remains deferred.
