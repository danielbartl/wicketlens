# Registered custom composite form inputs

The eleventh development milestone supports the source registration
`new OatCustomField<String>("phone", PhoneInput::new)` in `InvoicePage`.
Its optional **FORM_TEMPLATE** context contains six **MATCHED / INFERRED** bindings:
`container`, `label`, `feedback`, `input`, `input:code` and `input:number`.

Paths remain **WRAPPER_RELATIVE**. Registration provenance connects this declaration
to InvoicePage without asserting a concrete enclosing page path, attachment,
successful initialization or current runtime presence. Separate registrations have
separate context IDs even when they select the same input class. The initial graph,
inspections, completion, fixes and guarded paired rename retain their previous
meaning. No new context supplies editing authority.

## Source contract

The implementation extends the existing [lazy form input analyzer](form-inputs.md).
It reuses cache recognition, constructor identity, bounded source declarations,
transparent wrapper planning, owned markup extraction, graph matching and immutable
navigation snapshots. There is no Oat class-name allowlist.

A source wrapper construction must pass one direct unbound constructor reference
to a two-argument form-input functional interface. The input is a source top-level
or static member FormComponentPanel with a String/model constructor. Selected
wrapper constructor delegation may forward the functional argument, including a
single-argument `Objects.requireNonNull` storage check, to one private final
instance field. These constructors may only delegate and store that factory.
Parameters and the stored factory may not escape this flow. The selected
constructor chain is checked; unrelated overloads do not establish a registration.

The selected source input factory invokes that exact field's SAM with its original
ID/model parameters. It returns the result directly or through one local. An
optional rejection guard may throw IllegalStateException or IllegalArgumentException
for a null result or mismatched ID. Its diagnostic text may format only String
literals, the supplied ID, the returned component's framework getId and a nullness
conditional; application message helpers are withheld. Arbitrary conditional setup, additional statements, changed arguments,
component escapes and factory uses elsewhere are withheld. Anonymous wrappers,
lambdas, bound constructor references and borrowed factories are outside this slice.

The existing constructor identity tracer checks the registered overload using
scratch PSI that is never published as application source. The result must originate
at its supplied ID argument; an equal hardcoded literal does not satisfy this rule.
The input directly extends FormComponentPanel. Its constructor children follow the
same private-final/direct-addition rules as milestone ten. The shared cache and
wrapper structural checks remain in force. Initial owned-form effect proof stays
unchanged; template discovery makes no claim that configuration or callbacks are
side-effect free.

Conversion and other hook locations remain source declarations with
`execution: not_evaluated`. Model values, required state, conversion/validation
results and runtime presence are unknown. OatCustomField's render-time child visitor
and PhoneInput's model splitting are not executed or summarized as runtime results.

## Native navigation and MCP decision

Input child IDs navigate to PhoneInput.html and back. Go to Declaration on the
registered constructor reference's `new` token opens OatCustomField.html's input
slot. That slot can navigate back to both the source input factory and the
registration. Java class-qualifier navigation retains its ordinary meaning.
Source-reference discovery can find the registration from the input or wrapper
resource/class, using the existing bounded indexed candidate machinery.

Extend the existing optional `wicket_get_page_structure` projection with additive
`formTemplate.registration` provenance. No tool, request parameter or schema version
is added. Native and MCP consumers use the same analyzed graph and snapshots.
The optional registration object contains:

| Member | Meaning |
| --- | --- |
| `kind` | `CONSTRUCTOR_REFERENCE` |
| `ownerClassName` | Source class containing the registration |
| `wrapperConstructionLocation` | Source wrapper allocation |
| `constructorReferenceLocation` | Registered `Input::new` expression |
| `inputConstructorLocation` | Resolved source input constructor |
| `factoryFieldLocation` | Private final storage field |

For a registered form template, context `className`/`moduleName` identify the
registering source owner; `formTemplate.wrapperClassName` identifies the virtual
graph's root component type. A wrapper in a dependency module retains that source
provenance without relabeling its caller as a class of the dependency module.

The existing factory/cache/getter locations still identify the shared wrapper
implementation. Bindings include parent/ancestor/child projections. Default
structure, validation and component-context selectors retain the initial graph;
these template-only paths do not select it. All tools remain read-only.

The form discovery limits remain 12,000 steps, 64 sources, 4 contexts, 12 forwarding
levels and 64 nodes per graph. Registrations share these invocation-local limits
with direct form discovery. Exhaustion publishes no partial form graphs. Existing
source-set/module gates, cancellation, freshness, 30-second request deadline and
512-KiB whole-response rejection apply. Every source used for forwarding, factory,
cache, input and markup contributes to freshness dependencies.

## Acceptance and remaining scope

The read-only Oat corpus checks InvoicePage's six inferred bindings, concrete
PhoneInput children, shared native/MCP graphs, both navigation directions, unchanged
initial analysis and unchanged application inputs against Wicket 10.11.0 and 11.0.0.
A separate Wicket 11 WicketTester process checks constructor registration, lazy input
identity, child rendering, composite conversion/model update and ID mismatch rejection.
These fixture outcomes do not become runtime guarantees in exported reports.

Regression tests cover constructor/ID/model forwarding, private-final ownership,
factory escapes, unsupported guards/setup, separate registrations, module isolation,
unsaved edits, cancellation, bounds and read-only MCP behavior, including the actual
bundled host. Final receipts are recorded in
[the evidence summary](wicket-oat-registered-input-evidence.json).

Page embedding, arbitrary constructor factories/lambdas, source-base input
construction, scalar input expansion, general conversion/validation/model analysis,
CRUD save/delete protocols and shared/property-aware rename remain follow-up work.
This development build does not imply installation into the live IDE or publication.


[Context-aware completion](context-completion.md) now reuses supported graphs from
this milestone for parent-scoped Java/HTML ID suggestions. Its separate editor
probe does not change the normal inspection, fix, paired-rename or MCP contracts.
Inferred candidates remain labelled with their source context and uncertainty.
