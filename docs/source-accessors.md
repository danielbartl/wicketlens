# Read-only source accessors and scalar getters

WicketLens can read an existing, fully constructed provider through a closed
source accessor. It can also follow a simple String or primitive getter without
losing the literal that supplied its value. Application code is never executed.

```java
class Settings {
    private final String id;
    Settings(String id) { this.id = id; }
    public String id() { return id; }
}
class Holder {
    private final Settings settings;
    Holder(Settings settings) { this.settings = settings; }
    public Settings settings() { return settings; }
}
Holder holder = new Holder(new Settings("title"));
add(new Label(holder.settings().id()));
```

This extends [immutable provider state](provider-state.md) and
[source provider factories](provider-factories.md). A fresh provider factory
still proves that its result was allocated in that invocation or a nested call.
An accessor instead proves a read of a provider whose exact allocation and
immutable configuration are already known.

## Supported reads

- A read-only method contains one return statement, with optional empty
  statements before it. The expression may read a literal, parameter, private
  final instance field, completed provider receiver, or another reviewed accessor.
  The [conditional extension](conditional-setup.md) additionally permits proven
  boolean operations and ternary selections within that single return.
  The [scalar-expression extension](scalar-expressions.md) also permits reviewed
  integral arithmetic/comparisons and String construction/content equality.
- Provider fields retain their existing per-allocation environments. Known
  provider parameters may be forwarded, including through generic source methods.
  Returning a provider does not allocate a new instance or reset its configuration.
- String/primitive getters retain literal origins or unknown scalar parameters.
  Results can supply IDs, component data, locals, provider constructor arguments,
  and private final page fields, including constructor-assigned scalar fields.
- The usual proven dispatch rules apply to static/private/final methods, final
  pages, known provider instances, source ancestors, and interface defaults.
  Generic substitutions and return compatibility are checked against PSI types.
  Return assignment follows
  [JLS 25 §14.17](https://docs.oracle.com/javase/specs/jls/se25/html/jls-14.html#jls-14.17).

The read audit includes nested method bodies, receivers, and arguments evaluated
inside the accessor. Setup before a return, allocation hidden in a nested call,
and mutable/nonprivate scalar field reads cannot masquerade as a pure read.
Receiver and arguments at the caller may construct known providers normally;
the accessor must then read those completed values without further setup.

## Conservative boundaries

Unknown injected providers and receivers remain unknown; neither an accessor nor
a declared interface selects an implementation. Unfinished providers and
uninitialized fields cannot be exposed. Unknown String values remain dynamic and
cannot establish missing counterparts.

Repeater model-data calls retain the existing opaque-data warning contract. A
getter on a runtime model object does not acquire its declared source class's
literal value, even when known provider getters are used in the same template.

Unknown branches, recursion, unreviewed calculations, setters, mutable provider state, null returns,
arbitrary Object/boxed-value getters, unavailable source, and opaque calls remain
outside this contract. Scalar getters with local declarations or other statements
are also excluded. Cached component returns remain excluded: only provider
accessors relax the fresh-result requirement. Existing static initialization,
effectful qualifier, lifecycle, escape, and budget guards still apply.

## Editor behavior and freshness

Caller ID literals preserve references, completion, coloring, inspection ranges,
and paired rename through provider and scalar accessors. Existing origin/context
guards still apply. Shared implementation literals, scalar field constants, and
one literal supplying multiple paths do not gain per-pair rename. Ignored
arguments do not become IDs merely because a getter exists.

Consulted accessor, provider, interface, superclass, and constructor sources join
the snapshot dependencies. Unsaved source edits block stale navigation/copying;
reanalysis rebuilds the values. Reports retain value snapshots rather than PSI
environments. Providers are not component nodes, and the JSON schema is unchanged.

Bounds remain 12 call/hierarchy levels, 128 frames, 256 combined provider/component
instances, 24 source files, and 5,000 steps. Getter/accessor calls consume those
budgets and preserve cancellation checks.

`WicketAccessorTest` covers supported reads and adversarial effects/types/state.
`WicketAccessorEditorTest` covers native references, rename, completion, coloring
recognition, problems, shared-origin guards, and source freshness. Real application
accuracy/performance evaluation remains deferred.

Open `example.ProviderAccessorPage` for **4 MATCHED** bindings: `title`, `subtitle`,
`form`, and its `name` child. Page accessors read two separate configured providers;
each provider's dependency and String getters preserve the caller's IDs. Four
Java files and the HTML companion participate in freshness checks.
