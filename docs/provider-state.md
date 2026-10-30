# Immutable provider configuration and constructor injection

Known providers can hold private final configuration and references to other known
providers. This extends [proven source dispatch](source-dispatch.md) without
executing application code or guessing implementations of injected types.

```java
interface Parts { Label build(); }
class ConfiguredParts implements Parts {
    private final String id;
    ConfiguredParts(String id) { this.id = id; }
    public Label build() { return new Label(id); }
}

Parts first = new ConfiguredParts("title");
Parts second = new ConfiguredParts("subtitle");
add(first.build(), second.build());
```

Each allocation has a separate field environment. Fields are identified by PSI
declaration, so private superclass/subclass fields with the same name stay distinct.
Reports contain resulting components; provider environments are not exported.

## Construction and dependencies

Supported values are literal-origin scalars, opaque primitive/String parameters,
and known provider references. All instance fields must be private, final, and
initialized exactly once. Field initializers run in source order. Constructors
may assign those fields and forward supported values through a leading, resolved
`this(...)` or unqualified `super(...)` delegation. Superclass construction precedes
subclass field initialization. Delegation cannot expose the unfinished receiver;
reads before initialization stay unresolved rather than acquiring default values.

This follows the supported subset of [JLS 25 §12.5](https://docs.oracle.com/javase/specs/jls/se25/html/jls-12.html#jls-12.5).
Constructor prologues before explicit delegation, qualified superclass calls,
branches, arbitrary constructor method calls, initializer blocks, unreviewed ID
transformations, and unavailable source remain outside it. Static initialization
retains the source-dispatch contract's literal-only audit.

Known constructor injection preserves both the concrete type and its configuration:

```java
class Decorator implements Parts {
    private final Parts delegate;
    Decorator(Parts delegate) { this.delegate = delegate; }
    public Label build() { return delegate.build(); }
}
add(new Decorator(new ConfiguredParts("title")).build());
```

Source interfaces/superclasses use the existing proven dispatch rules. An unknown
framework-injected provider does not acquire an implementation from its declared
type, including when passed to another constructor. An unknown String ID can flow
through configuration but remains `UNRESOLVED_DYNAMIC_ID` and cannot establish a
missing counterpart.

Mutable or nonprivate state, cached components, mutable model/collection objects,
and escaped page/component arguments prevent expansion. Mutable page fields cannot
become stable configuration through aliases. Recursive construction/calls fall back
to uncertainty. Closed source factories can now return fresh known providers;
see [the factory contract](provider-factories.md). Read-only accessors can now
return existing known providers and scalar configuration; see
[the accessor contract](source-accessors.md). Opaque factories remain unresolved.
Source interface defaults have their own
[bounded dispatch contract](interface-defaults.md).
Reviewed setup/factory methods can now select branches using stored boolean
configuration; see [proven conditional setup](conditional-setup.md). Provider
constructors themselves retain the assignment-only contract above.
Reviewed scalar expressions can assemble immutable configuration and IDs or prove
numeric/String conditions; see [scalar expressions](scalar-expressions.md).

## Editing, freshness, and bounds

ID origins follow values through fields and delegated factories. A constructor
argument proven to supply an ID supports orange coloring, references, completion,
paired rename, and possible-unpaired-ID problems. Display strings keep their colors.

Individual literals in the page's constructor/initialization setup or its own
private final provider field can qualify for rename. One stored literal feeding
several paths, shared scalar constants, and implementation default literals retain
the existing per-pair restrictions. Variant, inheritor, collision, and uncertainty
guards also remain in force.

Consulted provider, constructor, interface, and superclass files join snapshot
freshness checks. Edits invalidate old navigation/copying; reanalysis reconstructs
independent allocation state. Caches retain reports rather than PSI environments.
The JSON schema is unchanged, and analysis remains deterministic and read-only.

Bounds remain 12 call/hierarchy levels, 128 invocation frames, 256 combined
provider/component instances, 24 source files, and 5,000 steps. Implicit/delegating
constructors count toward those limits. Unsupported setup or exhaustion retains
conservative diagnostics.

## Evidence and demo

`WicketProviderStateTest` covers configuration, allocation separation, constructor
ordering/delegation, injection, ID origins, and adversarial cases.
`WicketSourceSetupEditorTest` covers native editing, coloring recognition, problem
ranges, shared-origin guards, and freshness.

Open `example.ConfiguredPartsPage` for **4 MATCHED** bindings: `title`, `subtitle`,
`form`, and its `name` child. Two configured providers wrap separate known
`DispatchParts` instances. Heading IDs come from constructor configuration; the
`label("unused")` arguments deliberately do not supply them. Four Java files and
the companion HTML participate in freshness checks. Real application accuracy and
performance evaluation remains deferred.
