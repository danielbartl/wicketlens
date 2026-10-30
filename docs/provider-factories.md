# Fresh source provider factories

WicketLens expands closed source methods that return freshly constructed,
reviewed providers. The concrete allocation, immutable configuration, and literal
ID origins survive factory calls rather than being inferred from a declared type.

```java
class PartsFactory {
    static Parts create(String id) { return new ConfiguredParts(id); }
}
Parts parts = PartsFactory.create("title");
add(parts.build());
```

This uses the [immutable provider contract](provider-state.md),
[proven dispatch](source-dispatch.md), and [source defaults](interface-defaults.md).
It adds provider returns to the existing interpreter; it does not execute code.

## Supported calls and returns

- Static source factories, closed page helpers, and methods on known builder
  instances can return providers. Proven interface default methods can do so too.
- A factory may return a direct allocation, a stable local alias to that allocation,
  or a provider returned by a nested fresh factory. Repeated calls retain distinct
  allocation state, even when they use the same constructor/factory expression.
- Results may initialize locals or private final fields, be assigned to a private
  final page field in its constructor, or immediately receive component-factory
  calls. Provider constructor arguments and field initializers may call reviewed
  source factories to assemble known dependencies.
- The selected return must refer to a provider constructed within that invocation or
  a nested factory, with construction complete. Provider parameters and cached
  fields do not become fresh factory results. They can now be read through the
  separate [read-only accessor contract](source-accessors.md).
- PSI substitutions check return compatibility, including nested generic factories.
  Incompatible or unresolved types remain unresolved rather than acquiring an
  implementation by name. Return assignment follows
  [JLS 25 §14.17](https://docs.oracle.com/javase/specs/jls/se25/html/jls-14.html#jls-14.17).

Bodies support bounded setup and [proven boolean choices](conditional-setup.md),
including early factory returns. Unknown branches, recursion,
unknown calls, effectful static qualifiers/initialization, null results, mutable
provider state, opaque/binary factories, and unknown injected implementations keep
conservative diagnostics. A fresh return is required even when a cached provider
could otherwise be inspected; [source accessors](source-accessors.md) provide a
separate read-only contract for cached/parameter providers and simple String or
primitive getters. Arbitrary `Object` returns remain unsupported.

## Editor behavior, freshness, and bounds

Individual page constructor/initialization or private final provider-field
literals can supply IDs through factories. Their origins support native
references, paired rename, completion, orange coloring, and possible-unpaired-ID
inspections. Default literals in shared implementations, repeated origins, variant
contexts, inheritors, collisions, and uncertainty retain existing edit guards.
Unknown String IDs stay dynamic and cannot establish missing counterparts.

Factory implementations and their consulted ancestors/interfaces join provider,
constructor, and markup sources in freshness checks. Unsaved edits invalidate
navigation/copying; reanalysis reconstructs the provider graph. Reports retain
value snapshots rather than provider/PSI environments. Providers do not appear as
Wicket components, and the JSON schema is unchanged.

The shared limits remain 12 call/hierarchy levels, 128 frames, 256 combined
provider/component instances, 24 source files, and 5,000 steps. Factory calls,
constructor chains, and allocations all consume those budgets. Failure or
exhaustion falls back to conservative observations and diagnostics.

## Evidence and demo

`WicketProviderFactoryTest` covers static/instance/default factories, chains,
allocation separation, generics, constructor composition, provenance, and
adversarial returns. Its editor suite covers references, rename, completion,
coloring recognition, problem ranges, shared origins, and source freshness.

Open `example.ProviderFactoryPage` for **5 MATCHED** findings: `title`, `subtitle`,
`footer`, `form`, and its `name` child. Two private final fields use a static factory
with a nested factory call. A constructor-assigned field uses a known instance
builder. Five consulted Java files and the companion HTML participate in freshness
checks. Real application accuracy/performance evaluation remains deferred.
