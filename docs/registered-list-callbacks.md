# Registered list item/data callbacks

The sixteenth development milestone supports AccordionPage's source static
`OatAccordion.of(id, model, (item, data) -> { ... })` protocol. Its primary report
has 11 matches. A separate logical item template has two inferred bindings,
`title` and `content`. The primary 11 UNRESOLVED_BRANCH findings retain nine
sidebar slots and these two accordion slots; three population templates supply
their conditional counterparts. Runtime rows are not added to the page graph.

## Reviewed source protocol

The factory belongs to a named top-level or static member source class directly
extending ListView. A bounded chain of same-class static overloads forwards
parameters or literals. Its terminal method optionally checks the callback with
Objects.requireNonNull and returns one fresh anonymous instance. The instance's
only override must be an exact populateItem bridge that calls the captured SAM
once with its unchanged item and that item's getModelObject result. Callback
storage, aliases, conditional invocation, extra effects and other overrides
withhold this proof. The caller supplies a direct two-parameter block lambda.

Component identity reuses the existing constructor-chain tracer. The factory
must forward its String ID parameter to the component; an equal hardcoded ID
does not satisfy forwarding. Factory/data arguments and captured callback effects
reuse the source-layout isolation audit. Captured page mutations, borrowed
children and callbacks escaping to unsupported code remain unresolved.

Source constructors must directly delegate to this/ListView with parameters or
literals, then only construct and attach owned private final Behavior fields.
Other fields and initialization blocks are unsupported. An optional newItem
override must start with super.newItem(index, model), add only those behaviors,
and return the same item. Altered index/model, fresh or borrowed replacement
items, children, aliases and escapes are rejected. Other source repeater
lifecycle overrides remain unsupported. Synchronous behavior binding and
initialization reuse the existing behavior audit, including synchronous
isTemporary and getStatelessHint calls during attachment. Render-time behavior effects
and later configuration remain unknown.

The registered callback runs through the existing population interpreter using
one symbolic default ListItem and opaque item data. Existing child construction,
helper/factory checks, branch preconditions and nested populations still apply.
The item has no runtime ID. Models, rows/count/IDs, visibility, authorization and
later state remain unknown; application callbacks are not executed. Host and
item matches are INFERRED and grant no paired-rename authority.

## Native and MCP behavior

Host IDs, child IDs and the registered callback arrow navigate through shared
source/markup graphs. The callback arrow points to the host markup slot, and the
slot can navigate back to the callback. The text report identifies the registered
source factory and the separate item template.

MCP decision: extend existing structure, validation and component-context facts.
No new tool, parameter or schema version. The same value snapshots provide
primary populationTemplates and optional REPEATER_TEMPLATE navigation contexts.
Component registeredListPopulation and populationTemplate.registration share:

| Member | Meaning |
| --- | --- |
| kind | SOURCE_DECLARED_LIST_CALLBACK |
| factoryClassName | Source ListView subtype |
| registrationLocation, factoryLocations[] | Caller and reviewed forwarding route |
| callbackLocation, bridgeLocation | Direct source lambda and populateItem bridge |
| itemFactoryLocations[], behaviorLocations[] | Default-item wrapper and behavior declarations |
| itemFactory | reviewed_default or reviewed_default_with_behavior_decoration |
| callbackExecution | not_evaluated |
| behaviorEffects | unknown |

These are source facts, without live PSI references or executable callbacks.
Primary selectors cannot select template-only children. Discovery shares the
existing 20,000-step, 96-source, four-context, 128-node, 12-source-level and
three-nested-repeater limits. Source factory forwarding is bounded to 12 levels,
within the shared 64 factory-invocation limit. Exhausted population budgets
withhold all item graphs and mark discovery incomplete. Existing project/module/
source-set isolation, source freshness, cancellation, request deadline and
512-KiB whole-response bound remain in force; all tools remain read-only.

## Acceptance and remaining work

Acceptance covers the real unmodified Oat source on Wicket 10.11.0 and 11.0.0,
native navigation, shared MCP views and actual host requests. Negative source
fixtures exercise wrong item/index/model, callback storage/duplication/branching,
captured page/child mutations, extra constructor/factory effects, behavior binding
and lifecycle overrides. Unsaved bridge, behavior and item-wrapper edits invalidate
snapshots; this includes the synchronous isTemporary hook. Module isolation, cancellation and discovery/response bounds are tested.

An independent WicketTester fixture observes registration before population,
default runtime index/model identity, shared behavior decoration, actual callback
children during rendering and later disappearance of all rows with an empty model.
Those observations do not supply runtime values to static analysis.

General custom item factories, callback variables/method references, arbitrary
static factories, non-default repeater protocols and runtime state remain later
work. The development ZIP is not installed or published automatically.

Verification passed **2,848 normal tests**, **19 actual MCP host tests**,
**353 focused regressions** and **22 release-checker unit tests**, with zero
failures/errors/skips. Both Wicket corpus versions passed. The other 35 ordinary
page summaries and all earlier milestone template summaries are unchanged; all
36 page resources pair and all 318 application input hashes remain unchanged.
Plugin configuration/structure and binary compatibility checks passed against
IU-262.10968.63. Current source/test/artifact receipts match the ZIP. See the
[evidence summary](wicket-oat-registered-list-evidence.json).
