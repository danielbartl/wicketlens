# Owned form-field attachments

This fifth unreleased Wicket Oat milestone confirms the six ordinary bindings in
`ProductsPage.ProductFields`: **name, category, price, stock, description and
suppliers**. Java and `ProductsPage$ProductFields.html` navigate in both
directions. The application's resource layout is unchanged.

## Supported contract

An owned source component can retain its attachment when bounded source analysis
proves that its construction, initialization and fluent configuration isolate
their effects within that component and its newly created inputs. This extends
the existing source-layout/factory analyzer; it does not add an Oat class list.
The advanced pass can publish after a proved form summary as well as after an
actual factory invocation or symbolic population.

An empty anonymous `MetaDataKey<T>` is a token, not storage for a `T`. Its actual
initialization and uses are still audited. Overrides, initializer effects and
real static collections containing graph references cannot acquire this proof.

The reviewed lazy cache is a private, nonstatic form-component field, initially
null, with one assignment. Its getter checks that same instance's field for
null, assigns a freshly constructed input as the first direct statement of the
guard, configures/adds it, then returns the same field. The assignment and return
must refer to the owner. Repeated getter calls reuse that proved identity.
Borrowed or statically shared inputs, foreign receivers, unsupported aliases
and other cache shapes remain unresolved.

Private final instance fields may carry unbound source constructor references
for inputs through reviewed constructor delegation and `Objects.requireNonNull`.
Only an exact functional-interface invocation can create a source form component
declared as a static member or top-level class. Arguments remain opaque data; constructors,
initialization and source callbacks are audited. A factory that escapes or
mutates a parent scope blocks the proof. This narrow contract does not support
arbitrary page/editor placement. The subsequent
[replacement-editor milestone](replacement-editors.md) supports a separate bounded
source-declared context for `ProductFields::new`.

Source initializer blocks, implicit superclass constructors, source model/data
callbacks and known data-receiver overrides are checked. Available source effects
cannot be hidden behind array initializers, field qualifiers or object callbacks.
Unknown component results remain borrowed. Initial model-value writes require a
separate setter-ownership contract and prevent promotion. Later callbacks are
audited for escaping graph effects without claiming a concrete post-event tree.

## Framework contracts and uncertainty

The exact Wicket **10.11.0** and **11.0.0** source artifacts were reviewed for
metadata tokens; text, numeric, textarea and autocomplete construction; model
access; feedback; tag hooks; child visitors; and Ajax form-update binding.
Number formatting, converter/settings construction and rendering requests have
bounded data/configuration contracts. Source overrides still participate in
effect analysis. These operations are inspected, never executed by the analyzer.

A successful wrapper retains an explicit `UNRESOLVED_SCOPE` diagnostic:
internal inputs, conversion, validation and runtime lifecycle remain unexpanded.
It does not gain `sourceSetupExpanded`, composed input markup, or invented
`field:input` paths. Required flags, values, visibility, conversion results and
validation outcomes are not exported as inferred facts. Direct custom
`FormComponent`/`FormComponentPanel` contracts remain guarded. The subsequent
[origin-use audit](id-origin-audit.md) retains paired-rename protection for Oat
field IDs because they also supply resource keys and fallback text.

Effects retain the shared 128-source-call/12-active-level limits, with at most
128 deferred callbacks per drain. The advanced session also retains 20,000 steps,
96 source paths, 256 tracked allocations and 16 active source calls. Exceeding a
bound prevents partial confirmed attachments. Cancellation propagates.

## MCP decision

Extend the existing structure, validation and component-context **facts** through
the shared analyzer, `PageReport` and immutable value snapshots. No new tool,
request parameter, response field or schema version is needed. Optional
navigation discovery reuses these same confirmed wrapper bindings. Tool
descriptions and client documentation describe the opaque internal-input limit.

Source dependencies participate in existing freshness checks, including unsaved
factory edits. Project/module/source-set selection, read-only requests,
cancellation, the 30-second request budget and the 512-KiB response rejection
remain unchanged. A successful wrapper context has no private input children;
selecting an unobserved internal path fails normally.

## Verification and remaining scope

`WicketOwnedFormTest` covers token/cache/factory positives, borrowed inputs,
parent mutations, implicit construction, source data/model effects, subtype
dispatch, initializer effects and effect-depth exhaustion. `WicketOwnedFormMcpTest`
checks exact native/export/structure/validation/context agreement, bidirectional
references, read-only behavior, unsaved freshness and cancellation. The module
fixture checks conflicting classes and caches in separate modules. Existing
project-isolation and response-limit suites continue to cover the shared request
pipeline.

A separate Wicket 11 WicketTester process verifies that repeated lazy access
creates one runtime input and that a private constructor-reference input remains
inside its composite field. That independent fixture observes runtime children;
the static analyzer continues to leave them unexpanded.

The opt-in read-only corpus checks the six ProductFields bindings with both
Wicket versions and preserves previous Home/Badge/Card/Mobile/Inbox acceptance.
All 36 example page sources still pair with markup. ProductsPage itself remains
an unproved `CANDIDATE` with no matched graph bindings; general CRUD/dialog,
wizard and tree lifecycle protocols remain follow-up work. See the
[evidence summary](wicket-oat-form-evidence.json) for final verification and ZIP
checksums. No live IDE installation, publication or full application accuracy
and performance study is implied.

The later [lazy composite input milestone](form-inputs.md) adds separate inferred
wrapper-relative declaration contexts for supported source composites. It shares
cache recognition and source declarations while retaining this stricter effect
proof and its opaque-internal limit in initial reports.
