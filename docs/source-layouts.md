# Source component layouts

This development feature after 0.1.0 adds a bounded construction pass for
decorated source components and inherited layouts. It establishes ordinary
Java-to-HTML bindings on HomePage, BadgePage, CardPage, and the mobile layout
without moving resources or executing application code. The following
[fragment milestone](fragments.md) adds definition lookup and proved instance
contents. The [factory milestone](factories.md) now adds bounded source callbacks, owned
Panel scopes and symbolic Inbox populations. Custom lifecycle protocols remain
partial.

## Construction facts and opaque contents

The existing path/value interpreter runs first. When it cannot expand setup,
`WicketLayoutSetupAnalyzer` can prove additional source-component construction
patterns. The pass keeps three facts separate: the constructor's ID origin, the
instance's attachment, and uncertainty about its owned contents.

A custom Label or container may forward its ID, add styling behaviors, and expose
fluent methods. Constructor forwarding through parameters, literal IDs, reviewed
`Model.of`/`ofList` and `List.of` calls, enum arguments, and null casts is supported.
Delegation arguments that assign or update an ID parameter, call an unreviewed
function, or require unknown String computation do not acquire an identity proof.
Source overrides of `getId()` also prevent that proof. Existing constructor
contracts are reused; `WebComponent`, `TransparentWebMarkupContainer`, and
`Fragment` have additional reviewed identity contracts.

Custom form components retain their existing conversion and isolation contracts.
Custom repeater item factories also retain their reviewed contract; this milestone
does not weaken those guards. Static IDs reuse the existing constant analyzer,
source dependencies, and computed-origin editing guards.

ID proof is not permission to ignore constructor effects. The layout pass audits
source construction and helpers, configuration, data expressions, and behavior
binding. An unknown component escape, structural root effect, nonlocal component
access, unavailable effect body, or unsupported control flow prevents promotion.
An actionable diagnostic identifies the rejected source operation. A rejected
extended layout cannot silently regain confirmed bindings through the direct
fallback.

A matched Panel can still have unresolved internal contents. Its companion scope
is kept separate from its caller's markup. Fragment and repeater attachments can
likewise be known while their contents stay unresolved. Those local gaps do not
erase an independently proved sibling binding. An unknown root effect continues
to block all attachments it could invalidate.

## Constructor and initialization order

Source `this(...)` and `super(...)` delegation share one allocation and initialize
fields once. The pass supports straight-line locals, aliases, field assignments,
and helper calls through constructor and root `onInitialize()` setup. Initialization
must begin with a reviewed `super.onInitialize()` delegation. Recursive delegation,
manual initialization calls, alternatives at the analyzed entry constructor, and
unsupported nested initialization order remain unresolved.

The selected concrete page supplies inherited template-hook overrides. Reports
explicitly state this class context; possible runtime subclasses are not inferred.
Source component isolation also checks actual overrides in its known construction
context. Custom compiled ancestor setup is not reconstructed by this pass; the
existing fallback retains its supported observations and uncertainty.

Calls through superclass-typed aliases also resolve source overrides before a
framework configuration or attachment summary is applied. A setter name alone
does not prove the actual call is nonstructural.

Opaque models, ordinary data objects, and static resource references can be
audited for graph effects without evaluating their values. Compiled library
operations need reviewed contracts. Source behavior overrides of synchronous
binding/initialization effects are conservative. Known callbacks that mutate,
reattach, or expose captured components are rejected. Read-only source helpers
may inspect components after a separate graph-effect proof.

The snapshot describes static construction and initialization. It does not execute
rendering, events, Spring, model getters, or application services, or establish
runtime visibility and validity. In particular, an unresolved owned scope is not
an empty component tree.

## Transparent markup and localization

`WicketTransparentMarkup` resolves markup against already-proved Java allocations.
Ordinary children take precedence inside a transparent container. Parent lookup
is considered only when an ordinary child is unavailable and unknown contents do
not hide that child. Ordinary containers remain boundaries, including when nested
inside transparent markup.

The container itself remains a real component. Only its descendants' logical
markup parents change when the resolution proof requires fallback. Physical
`parentElementId` values and original source locations are preserved. Repeated
locations, ambiguous parents, custom resolver overrides, and incomplete header
contexts do not acquire a guessed binding.

The Page analyzer uses a bounded re-extraction to accept an HTML head beneath
proved transparent ancestors. It does not remove the head guard for arbitrary
components. Unsupported fragments and other Wicket markup retain local scope
diagnostics. Namespace declarations on inheritance markers are not mistaken for
component attributes.

Simple `wicket:message` elements containing text/ordinary markup and literal
attribute localization such as
`wicket:message="aria-label:OatAppLayout.toggleMenu"` preserve component scope.
Embedded component interpolation and unreviewed localization forms remain
unresolved. Localized text is not evaluated.

The ID storage, transparent child precedence/parent fallback, and automatic
transparent attribute-localization wrapper were audited in the Wicket source
archives for both supported fixture versions:

| Version | Source archive SHA-256 |
| --- | --- |
| [10.11.0](https://repo.maven.apache.org/maven2/org/apache/wicket/wicket-core/10.11.0/wicket-core-10.11.0-sources.jar) | `4b72cbf44463b7ce2ca39e1cb3c8c43095d0b3d23ef29e221fa48332a453be34` |
| [11.0.0](https://repo.maven.apache.org/maven2/org/apache/wicket/wicket-core/11.0.0/wicket-core-11.0.0-sources.jar) | `485a75789d32fc4be2a83e1fc755d5b8ade8b72c2e26ce160e1e865f55ef08b0` |

## Editor behavior and snapshots

References and navigation consume the existing matched bindings. ID origins keep
their actual Java locations, including forwarded caller literals. Bindings
established by the construction pass now use the subsequent
[origin-use audit](id-origin-audit.md). Identity-only literals can gain paired
rename when all existing editor guards also hold; shared, data and unsupported
uses remain guarded. Editing on the original analysis path keeps its behavior. Recognizing an
attachment does not make its internal template eligible for editing.

Consulted source classes, helpers, behaviors, constructors, markup, and the
framework constructor archives join the existing snapshot dependencies. Unsaved
changes invalidate native and MCP snapshots. No PSI, documents, or module state
are retained by the construction pass between requests.

The pass has limits of 5,000 analysis steps, 48 dependency paths, 256 instances,
16 active calls, and 12 hierarchy/delegation or source-effect levels. Transparent
discovery is limited to 128 markup levels. Cancellation propagates; exceeded
bounds produce unresolved results rather than partial promoted bindings.

## MCP implementation decision

Extend existing facts through `wicket_get_page_structure`, `wicket_validate_page`,
and `wicket_get_component_context`. They consume the same immutable graph and
matching decisions as native navigation. No tool, request parameter, response
field, or schema version is added. Tool descriptions now explain source-layout
and transparent bindings and retained owned-scope uncertainty.

`WicketLayoutMcpTest` checks exact export reuse, native-reference agreement,
read-only behavior, and unsaved behavior/resolver freshness. Module-selection
coverage uses different constructor facts for identical class names in unrelated
modules. Existing request lifecycle, project disposal, cancellation, response
limits, and host isolation safeguards remain shared.

## Verification and application corpus

`WicketLayoutSetupTest` and `WicketTransparentMarkupTest` cover the construction
and markup contracts, including unsafe binding, captured aliases, root escapes,
ID getter overrides, parameter mutation, source override dispatch, constructor
cycles, ambiguous transparent parents, cancellation, and discovery depth.

The optional corpus runner reads an external checkout and analyzes disposable
fixture copies. It checks required IDs and bidirectional native references on
the milestone pages, compares structure/validation/context MCP results with the
native report, and verifies input hashes after analysis. Default tests exclude
this opt-in class; they do not depend on a local application checkout.

```bash
./gradlew test --tests dev.wicketlens.WicketOatCorpusTest \
  -PwicketOatProject=/path/to/wicket-oat

./gradlew test --tests dev.wicketlens.WicketOatCorpusTest \
  -PwicketOatProject=/path/to/wicket-oat -PwicketOatVersion=11.0.0
```

Results are written to `build/reports/wicket-oat/<version>/corpus.json`.
The default release checker excludes the opt-in corpus from required portable
test classes; any corpus result present must still have no failures or skips.
The runner uses the supplied application source roots in one fixture module;
separate module-isolation tests cover colliding declarations. The Wicket 11 run
is a PSI compatibility check, not an application framework upgrade or a server
execution test. The complete real-application accuracy/performance study remains
open.

The first milestone's [evidence summary](wicket-oat-milestone-evidence.json)
records both application corpus runs, the full regression results and development
ZIP checksum. The ZIP retains the repository's 0.1.0 version metadata; it is an
unreleased development build, separate from the published 0.1.0 artifact.


[Direct abstract base-layout analysis](base-layouts.md) now reconciles agreeing
constructor entries under declared implementations and preserves an open child
slot as a local boundary. This improves eligible primary reports without changing
ordinary concrete-page dispatch or constructor selection. Nonagreeing/unsafe
entries remain unresolved; runtime subclasses are not inferred.
