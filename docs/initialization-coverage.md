# Reviewed initialization scope

The direct-scope rules below remain the fallback. The later
[bounded source setup pass](source-setup.md) additionally traces closed helpers,
factories, and source superclass `onInitialize()` chains with leading superclass
delegation at every level. Unknown lifecycle/virtual effects remain unresolved.
The [conditional extension](conditional-setup.md) also supports proven boolean
branches and guard returns after the required leading superclass delegation.

This follow-up supports method-local static setup in an actual Wicket
`onInitialize()` override declared on the analyzed `WebPage` or `Panel` subclass.
It combines those observations with constructor setup without simulating runtime
execution or expanding other classes' lifecycle bodies.

## Framework source audit

Reviewed artifact: **org.apache.wicket:wicket-core:10.11.0**, from its
[published source archive](https://repo.maven.apache.org/maven2/org/apache/wicket/wicket-core/10.11.0/wicket-core-10.11.0-sources.jar).
SHA-256: `4b72cbf44463b7ce2ca39e1cb3c8c43095d0b3d23ef29e221fa48332a453be34`.

| Implementation | Reviewed behavior |
| --- | --- |
| `Component.onInitialize()` | Sets the flag verifying superclass delegation. |
| `MarkupContainer.onInitialize()` | Calls its superclass and `dequeue()`. The later queue extension models unique markup placement; framework-created containers and other runtime effects remain unexpanded. |
| `Page.onInitialize()` | Calls its superclass and touches the page manager. |
| `WebPage`, `WebMarkupContainer`, `Panel` | Inherit the implementations above. |

These exact superclass implementations form the reviewed delegation set. A
custom superclass implementation is not assumed harmless, even if its source
looks simple. Queuing calls retain scope uncertainty; markup extraction retains
its existing guards for framework-owned or unsupported markup constructs.

## Supported contract

- PSI must identify a zero-argument, nonstatic, public/protected `void`
  `onInitialize()` that overrides `Component.onInitialize()`. An annotation or
  matching name alone is insufficient. Overloads and unrelated hooks are not
  treated as this phase.
- The first nonempty statement must be a direct, valid `super.onInitialize()`
  call resolving to a reviewed implementation. Comments and empty statements
  before it are allowed. Missing, late, conditional, repeated, and custom
  superclass calls are unresolved.
- The body must contain only straight-line declarations, expression statements,
  and empty statements, with no unsupported root mutation or escape. Calls must
  resolve to the existing reviewed `MarkupContainer.add(Component...)` overload.
  The fifteen reviewed constructors and literal-ID rules are unchanged.
- Locals must be directly initialized with `new` and used solely in supported
  additions within their method. Local Forms/containers retain their child
  edges and construction locations. Equal variable names in the two phases refer
  to distinct declarations. Local aliases, reassignment, helper/factory results,
  and arbitrary cross-method data flow remain unresolved. A later
  [field-tracking slice](field-tracking.md) supports private final fields connecting
  the constructor and this hook; other fields remain unresolved.
- Constructor and hook nodes share the same parent-aware graph. Duplicate paths
  across phases remain ambiguous; the analyzer does not choose a replacement or
  simulate last-write behavior.

## Uncertainty and retained limits

Validation precedes graph discovery, so an unsupported hook cannot leave earlier
constructor attachments confirmed. Control flow, root removals/replacements or
unsupported queueing, root-bound method references, nested root effects, escaped `this`, and
opaque root helpers make the combined phase scope unresolved. An uncertain
constructor, manual initialization call, constructor delegation/alternatives, or
custom inherited setup also prevents confirmed combined attachments. A custom
inherited initializer is unexpanded even when no local override is present.
Known local container edges survive, with unresolved confidence and source causes.
The later [queue/repeater extension](queueing-repeaters.md) supports proven
queue operations in either phase without invalidating unrelated additions.

Helpers, factories, callbacks, other lifecycle methods, superclass bodies,
framework-created descendants, arbitrary Java execution, and runtime lifecycle
ordering are not reconstructed. Nested/anonymous bodies retain their existing
discovery boundary and enclosing-root guards. All reports remain partial with
informational diagnostics; a match establishes a unique observed static path,
not a complete runtime hierarchy.

## Fixtures and demo

[WicketInitializationTest](../src/test/kotlin/dev/wicketlens/WicketInitializationTest.kt)
uses actual Java/HTML PSI and the Wicket fixture libraries. It covers Page and
Panel roots, combined setup, locals, nested/repeated IDs, valid source locations,
informational unmatched findings, determinism, read-only analysis, and the
uncertainty boundaries above. Existing constructor/hierarchy, core review,
navigation, and acceptance suites remain part of the full check.

[InitializationPage.java](../examples/demo/src/main/java/example/InitializationPage.java)
and its [HTML](../examples/demo/src/main/resources/example/InitializationPage.html)
demonstrate one constructor Label plus five initialization components: a Form,
three Form children, and a local status Label. The full-pipeline fixture expects
**6 MATCHED** findings. Maven compilation checks the demo against Wicket 10.11.0;
no Wicket application is executed and no real application evaluation is claimed.
