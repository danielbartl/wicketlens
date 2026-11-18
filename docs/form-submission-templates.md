# Ordinary form submit and error declarations

Milestone 21 adds separate source contexts for ordinary AjaxButton and source
subclass onSubmit/onError overrides. InvoicePage's OatSubmitButton supplies two
contexts. Each has two inferred bindings, invoiceForm and invoiceForm:submit,
plus the source feedback receiver and an Ajax refresh of invoiceForm.

The initial InvoicePage hierarchy remains unresolved. These contexts describe
explicit Java additions paired with the selected class's own companion markup.
They establish no initial/runtime tree, successful validation, saved invoice or
executed callback. Existing primary reports, selectors, inspections, completion
and edit eligibility retain their existing rules.

## Source contract

Discovery visits one selected source constructor. It records direct component
locals initialized by fresh constructors and direct compiled MarkupContainer.add
statements. The selected button, its enclosing form, ancestors and effect targets
must have one declared attachment, reviewed constructor ID provenance, known IDs,
unique siblings and unique own-companion markup paths. Conditional additions,
reparenting, aliases, reassigned locals, direct structural mutations and escapes
through unreviewed constructor setup withhold the affected callback context.
Unknown effects elsewhere remain explicit; a source addition can be described
without proving execution, continued attachment or reachability.

The form is a standard Form, optionally with an opaque anonymous getLocale
implementation, as used by InvoicePage. The button can be a standard AjaxButton
or a source subclass with reviewed ID forwarding and constructor delegation.
Source superclass overrides of framework routing and unreviewed anonymous button
initialization/overrides are excluded. Constructor, configuration and attached
behavior effects remain unknown. Static button factories and registered lambdas
are outside this milestone.

Handlers are direct anonymous overrides at the selected construction site. Named
callback implementations and static factory registrations remain deferred.

The source constructor chain is followed to the framework AjaxButton overload.
A null/absent explicit Form uses the nearest enclosing declared form. An explicit
Form must refer to that same constructor local. External or computed form
associations are withheld. This does not evaluate AjaxButton.getForm or runtime
parent lookup. The standard framework submit/error declarations supply the route;
there is no source dialog bridge for these ordinary callbacks.

Callbacks must override protected void AjaxButton.onSubmit(AjaxRequestTarget) or
onError(AjaxRequestTarget). Each straight-line body can declare Component feedback
and direct add(Component...) calls on its own callback target. The effect reader
is shared with milestone 20. Feedback receivers can be the button, qualified page
or a stable declared component local. Ajax refresh targets use the same declared
identities. Component acquisition in opaque feedback, target escape, model/local
data setup, arbitrary helpers, business operations and callback conditionals are
unsupported. Independent supported callbacks can still contribute.

The own companion is extracted with the existing markup analyzer, retaining its
syntax, parent, duplicate, preview and inheritance-source checks. The shared
matcher pairs the selected declarations. Inherited placement remains unknown;
paths are relative to declarations in the own companion. No inherited runtime
prefix or complete caller hierarchy is claimed. All bindings are INFERRED and
have idRenameSafe=false in the template. Independently eligible initial bindings
keep their original edit authority.

## Native navigation and reports

Go to Declaration on either callback name opens its button slot. The slot's soft
references retain both callback names and the Java ID counterpart. Existing ID
references navigate in both directions using the new declaration graphs.

Text reports identify the own-companion declaration scope, trigger, callback and
declared feedback/Ajax effects. Validation, model values, business/feedback
outcomes, initialization, runtime reachability and later tree state remain
unknown. Feedback text is not serialized and callbacks are never invoked.

## MCP implementation decision

Extend the existing optional structure contexts. Agents benefit from inspecting
where an ordinary form declares its submit/error handlers and which components
those handlers request to refresh. Reuse the native analyzer, shared effect
reader, graph matcher and immutable value snapshots. No new tool, parameter or
schema version is required.

`wicket_get_page_structure` with `includeNavigationContexts=true` adds
`relationship: SUBMISSION_TEMPLATE` entries. Their submissionTemplate metadata has:

- `kind: SOURCE_DECLARED_FORM_CALLBACK`, `phase: SUBMISSION_TEMPLATE`,
  `pathScope: OWN_COMPANION_DECLARATIONS`.
- `frameworkCallback`: the standard AjaxButton onSubmit or onError declaration;
  `dispatchCondition: SOURCE_ON_SUBMIT_ROUTE` or SOURCE_ON_ERROR_ROUTE.
- Host/form IDs and paths (the host is the declared form), button trigger and
  callback location. bridgeLocation and dispatchLocation are null because this
  route uses the framework callback directly. Pre/post-dispatch arrays are empty.
- `formCallback`: `attachmentEvidence: SOURCE_DECLARED_ADDITIONS`,
  `formAssociation: ENCLOSING_DECLARED_FORM` or EXPLICIT_DECLARED_FORM,
  source attachment/constructor locations and optional explicit Form location.
  initialHierarchy, inheritedMarkupPlacement, initializationEffects and
  formProcessingEffects are unknown.
- Existing effect records, with `targetPathScope: OWN_COMPANION_DECLARATIONS` and
  `targetAttachmentPhase: SOURCE_DECLARED_ADDITIONS`. Their execution is
  not_evaluated. Bindings include ancestors and children inline.

Callback execution is not_evaluated. Runtime presence, subclasses, reachability,
other routes, default form processing, validation results, models, feedback and
business outcomes, constructor/configuration/behavior/opaque-data effects and
later tree/open/close state stay unknown. A route name describes a source hook;
onSubmit does not establish successful validation, since default processing can
be disabled. onError does not establish that a validation failure occurred.

Existing dialog metadata and graphs remain unchanged. Default structure,
validation and component-context tools continue to select the initial graph;
optional declarations cannot select an otherwise unresolved initial path. The
completion probe excludes submission discovery. All operations are read-only.

Discovery allows 40,000 steps, 96 sources, eight contexts, 128 graph nodes,
12 source/ancestry levels and 128 effect records. Exhaustion withholds all ordinary
form callback graphs and marks discovery incomplete. The existing indexed source
and aggregate 256-binding limits and 512-KiB whole-response bound still apply.
Unsaved source, constructor and markup edits invalidate snapshots. Module/project
isolation, cancellation and freshness use the shared request pipeline.

## Acceptance

Fixtures exercise standard and forwarded source buttons, enclosing/explicit form
association, separate submit/error routes, feedback receivers, Ajax targets,
unknown initial effects, conditional additions, structural mutations, aliasing,
reassignment, changed routing, markup ambiguity, native navigation and protected
editing. Direct and actual-host MCP checks cover shared values, unchanged initial
views, freshness, module isolation, response bounds and read-only behavior.

An independent WicketTester oracle confirms rendering without callback execution,
onError after required-field failure, onSubmit after successful default processing,
form refresh on either route, and dispatch with disabled default processing.

The pinned Oat corpus verifies InvoicePage's two contexts against Wicket 10.11.0
and 11.0.0. Earlier page summaries, templates, completion probes and dialog/event
text reports are regression checked. All application files remain read-only.

Final verification: **3,002** normal tests, **24** actual MCP host tests and **22**
release-checker unit tests passed with zero failures/errors/skips. Both read-only
Oat corpora (Wicket 10.11.0 and 11.0.0) passed InvoicePage's two submit/error contexts.
All 36 ordinary page summaries, earlier template groups, six completion probes
and initial/event/dialog-submission text reports match milestone 20. All 318
application input hashes remain unchanged. Configuration, structure and binary
compatibility checks passed against IU-262.10968.63: `Compatible. 5 usages of deprecated API. 6 usages of experimental API`.
The full build/verification run took 23m 57s. Current source/test/artifact
receipts match the ZIP. See the [evidence summary](wicket-oat-form-submission-evidence.json)
for counts, framework source contracts and hashes. The ZIP retains 0.1.0 metadata.
