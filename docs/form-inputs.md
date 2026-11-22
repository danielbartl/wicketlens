# Lazy composite form input structure

The tenth development milestone exposes source-declared internals for
`OatDateRangeField`. Its optional form context has **six MATCHED / INFERRED
bindings**:

| Wrapper-relative path | Source component |
| --- | --- |
| `container` | Transparent fieldset wrapper |
| `label` | Field label |
| `feedback` | Feedback/hint label |
| `input` | `OatDateRangeField.RangeInput` |
| `input:from` | First date input |
| `input:to` | Second date input |

The wrapper fieldset remains a real component. Existing transparent lookup gives
label/input/feedback their short paths, rather than adding `container` to them.
The input's binary-name companion supplies its own two child slots.

These paths describe a **source declaration template relative to the wrapper**.
They do not identify a particular page field or establish current runtime presence.
The initial report and existing confirmed wrapper attachments remain unchanged.
All new bindings are inferred, scopes remain incomplete, and template origins gain
no editing authority.

## Structure and runtime semantics

The context records conversion, required-check and validator declaration source
locations. Their `execution` is `not_evaluated`; conversion and validation results,
required state and model values are always unknown. It does not execute converters,
validators, model getters/setters, behavior binding, resource lookup or callbacks.

A model update is distinct from converted input, and a markup match does not prove
that validation succeeds. The exact Wicket 10.11.0 and 11.0.0 source contracts were
reviewed for FormComponentPanel markup, transparent resolution and FormComponent
conversion/validation/model-update methods. The source files and their version-
specific hashes are recorded in the evidence summary. These existing framework
methods do not turn arbitrary application hooks into statically known outcomes.

## Shared declaration contract

The existing owned-form effect proof and the new context analyzer share the same
private null-guarded cache recognizer. The field is a private nonstatic form input,
initially null, with one assignment inside the guard and a return of the same field.
This recognizer supplies shape only: the existing effect analyzer retains all its
construction, callback, model-write and escape checks. No initial-analysis proof is
relaxed by adding a template.

The optional declaration context additionally requires one direct cache attachment
to its owner, a known String ID, no accessor/attachment override, no known structural
cache mutation and no escape of the cached value outside that declaration flow.
The concrete source factory must return a fresh source FormComponentPanel and
preserve its supplied ID parameter. An equal hardcoded literal is not forwarding.
Anonymous/foreign composite construction remains unsupported.

The shared structural wrapper has one constructor and directly extends a framework
markup owner. Intermediate source wrapper constructors may only delegate. The
input directly extends FormComponentPanel. Component initializer blocks, unsupported
source-base construction and extra subclass setup are withheld.

Both wrapper and input declarations reuse the bounded source machinery extracted
from tree support. It tracks fresh component locals, private final fields assigned
once by direct constructor statements, direct additions and known parents. A private
instance helper on the same input may return a fresh component/local with a forwarded
literal ID; source helper effects and deferred arguments are not interpreted as
runtime results. Static source factory alternatives retain their existing same-ID
rules and possible constructor types. Arbitrary parent aliases, reassignments,
escapes, structural mutations and duplicate paths are rejected.

The graph reuses existing constructor identity, source constants, companion lookup,
transparent markup planning, owned-markup extraction and graph matching. Input
preview descendants in wrapper markup are inactive; syntax/document uncertainty is
retained. No Oat class-name allowlist or separate MCP binding implementation exists.

## Native navigation

The `from`/`to` declarations navigate to their actual nested companion and back.
Go to Declaration on the concrete input factory's method name opens the wrapper's
`input` slot; the slot navigates back to that factory. Class-name navigation keeps
its ordinary Java behavior.

Source and resource changes invalidate the same native snapshots as other contexts.
Extra graphs serve navigation only. The initial sidebar, inspections, completion,
quick fixes and guarded paired rename keep their existing snapshots and eligibility.

## MCP decision and response contract

Extend existing `wicket_get_page_structure` output when
`includeNavigationContexts: true`. No new tool, request parameter or schema version
is needed. Native navigation and MCP reuse the same semantic value graphs and
freshness pipeline.

`navigationContexts.contexts[]` may contain `relationship: FORM_TEMPLATE` with an
additive `formTemplate` object:

| Member | Meaning |
| --- | --- |
| `kind`, `phase` | `SOURCE_DECLARED_LAZY_FORM_INPUT`, `LAZY_INPUT_TEMPLATE` |
| `pathScope` | `WRAPPER_RELATIVE`; this context starts at the wrapper, with no enclosing page prefix |
| `contextId` | Snapshot-local source context identity |
| `wrapperClassName`, `inputClassName` | Source wrapper and concrete composite input |
| `inputNodeId`, `inputComponentPath` | Input selection inside this graph and its declared wrapper-relative path |
| `cacheLocation`, `getterLocation`, `factoryLocation` | Cache field, getter and concrete factory source |
| `initializationLocation` | Optional non-deferred source call from the selected initialization method; no successful execution guarantee |
| `hooks[]` | `CONVERSION`, `REQUIRED_CHECK`, `VALIDATION` or `VALIDATOR_DECLARATION` provenance, with `execution: not_evaluated` |
| `runtimePresence`, `conversionResult`, `validationResult`, `modelValues`, `requiredState` | Always `unknown` |

Scope/binding projections include parent, ancestors and children inline.
`hierarchyResolved` describes the declared wrapper-relative graph, not current
runtime presence or an enclosing page path. A request for a nested input's own
class/companion can discover the corresponding wrapper context through the existing
bounded source-reference machinery.

Default structure, validation and ordinary component-context selectors retain the
initial graph. A template-only `input:from` path cannot select that graph. All tools
remain read-only. Existing lifecycle diagnostics explain withheld form templates;
discovery completeness remains separate from semantic certainty.

Unsaved wrapper/cache/helper/markup changes invalidate old snapshots. Existing
project/module/source-set gates, cancellation, the 30-second request deadline and
512-KiB whole-response rejection remain. Form discovery permits 12,000 source/call
steps, 64 sources, 4 contexts, 12 forwarding/hierarchy levels and 64 nodes per graph.
Exhaustion publishes no partial form discovery. Tree declaration/refactoring
regressions preserve its previous bounds, contexts and output facts.

## Verification and remaining scope

The read-only Oat corpus verifies the six bindings, transparent paths, nested
companion navigation, factory/slot navigation, shared MCP output and unchanged
initial reports with Wicket 10.11.0 and 11.0.0. Prior layout, Inbox, ProductFields,
editor, wizard and tree acceptance remains covered.

A separate Wicket 11 WicketTester process renders the same generic cache/private-
helper pattern. It checks cache identity, composite child paths, required failure,
valid conversion/model update and validator failure retaining the prior model.
Those runtime fixture outcomes are not exported as guarantees for Oat or arbitrary
applications.

PSI/MCP tests cover null caches, parameter provenance, accessor/attachment overrides,
private helper ownership, borrowed/reassigned inputs, duplicate fields/attachments,
source mutation, deferred initialization, model/validation uncertainty, module
isolation, freshness, cancellation, bounded contexts/nodes/responses and read-only
behavior. Actual bundled MCP-host HTTP tests cover the optional form projection.
See the [evidence summary](wicket-oat-input-evidence.json) for final verification and
artifact hashes.

The [eleventh milestone](registered-form-inputs.md) adds bounded registered custom
input constructor references, reusing this graph and its uncertainty contract.

This slice does not expand every scalar field, arbitrary private input factories,
source-base/conditional setup, converter internals, runtime model/validation effects,
page embedding or shared/property-aware rename. No live IDE installation, graphical
release-readiness evaluation or publication is implied.


[Context-aware completion](context-completion.md) now reuses supported graphs from
this milestone for parent-scoped Java/HTML ID suggestions. Its separate editor
probe does not change the normal inspection, fix, paired-rename or MCP contracts.
Inferred candidates remain labelled with their source context and uncertainty.

## Declared child configuration and conversion

The owned child graph records direct required settings and reviewed numeric
`RangeValidator` bounds on known child receivers. Component metadata
`formConfiguration` identifies the declaration and, when proved, its boolean
setting or bounds. Other resolved validator declarations can carry their
registration location with unknown constraints.

A direct `convertInput` override can link one proved child field to the
composite input's `setConvertedInput` call. The optional `conversions` array
refers to the child and target nodes in the same inferred context. Runtime
input, conversion and validation values are unknown.
