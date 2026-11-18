# Dialog submission source templates

Milestone 20 follows an owned dialog's source AjaxButton.onSubmit bridge to a
selected onConfirm override. Each callback gets a separate SUBMISSION_TEMPLATE
graph. DialogPage supplies three contexts: deleteDialog (four inferred bindings),
editDialog (five, including the people refresh target), and filtersDrawer (four).
Feedback declarations record their method and receiver. A message such as
"Saved" or "Item deleted" establishes no business outcome.

The initial DialogPage report remains 72 matches and 13 branch findings. Its
initial content, row population contexts and separate details-link event template
retain their existing meaning. Submission templates are source declarations;
WicketLens never invokes a callback, form processing, validation or a model.

## Reviewed source contract

The existing initial-content analyzer proves the selected host constructor chain,
private-final owned form, content placeholder, throwing ID guard and setter route.
Submission discovery reuses those facts and matched markup counterparts. It
requires a private-final standard AjaxButton attached directly to that same form,
with one direct outer-host hook call receiving the unchanged AjaxRequestTarget.
An explicit button form must be the guarded owned form. Unreviewed button
overrides, conditional dispatch and target reassignment withhold the context.

Pre-dispatch statements can assign literal booleans to owned instance fields.
Their locations are retained without evaluating state. Post-dispatch statements
are recorded as locations, with unknown effects. OatDialog's later error checks,
form refresh and close logic do not become callback outcomes or extra refresh
declarations. Other possible runtime dispatch routes remain unknown.

The selected source hook has a bounded straight-line body. Supported declarations
are Component feedback calls, its own AjaxRequestTarget.add(Component...) calls,
and one guarded placement of fresh content. Refresh receivers must be that exact
callback parameter. Targets can be the selected host, qualified selected page, or
unchanged direct constructor locals with unique initial attachment counterparts.
Borrowed references, captured aliases, reassignments and arbitrary helper/domain
calls withhold the callback graph. Opaque feedback data cannot expose a component
or let the Ajax target escape. Model and other opaque data effects stay unknown.

A replacement can be a fresh component with reviewed ID provenance, or a fresh
framework Fragment with a qualified selected-page provider and reviewed child
declarations. A direct content local must be the callback's first statement and
have one placement. The slot ID must equal the guarded ID. Fragment projection
reuses the existing declaration builder and markup matcher; competing placements,
wrong IDs, borrowed/factory content and escaped values remain unsupported.
Constructor, configuration and model effects are unknown. The conditional fresh
content graph never replaces the initial graph in analysis.

## Navigation and reports

Go to Declaration on a supported source hook name navigates to the owned confirm
slot. The slot's references include the applicable source overrides. Supported
replacement constructors navigate to their content slot; fragment child IDs use
the existing Java/HTML references in both directions. Physical shared slots may
have several source counterparts. All submission bindings are INFERRED and grant
no paired-rename or quick-fix authority. Completion keeps its existing contexts;
it does not use submission hypotheses to invent an initial attachment.

Text reports identify the trigger, callback and declared effects. Paths refer to
source attachments under the selected page. A refresh target path describes its
initial declared identity, with no guarantee of its attachment during an event.
Source locations for the framework bridge, dispatch, callback, pre/post statements
and effect arguments remain available. Feedback values are not serialized.

## MCP decision

Extend the existing `wicket_get_page_structure` optional navigation contexts.
These callback routes, content declarations and Ajax targets help an agent answer
where a dialog submits and what its source declares. Reuse the native analyzer
and value snapshot; no new tool, parameter or schema version is needed.

With `includeNavigationContexts=true`, each new context has
`relationship: SUBMISSION_TEMPLATE` and optional `submissionTemplate` metadata:

- `kind: SOURCE_DECLARED_SUBMISSION`, `phase: SUBMISSION_TEMPLATE`,
  `pathScope: PAGE_DECLARED_ATTACHMENT`, `dispatchCondition: SOURCE_ON_SUBMIT_ROUTE`.
- Host/form/trigger IDs and paths; callback name and location; bridge/dispatch
  locations; pre/post-dispatch source locations.
- `effects[]`: kind (FEEDBACK, AJAX_REFRESH or CONTENT_REPLACEMENT), method,
  location, target ID/path and argument locations. Each has
  `execution: not_evaluated`, `targetPathScope: PAGE_DECLARED_ATTACHMENT`, and
  `targetAttachmentPhase: INITIAL_DECLARATIONS` or SUBMISSION_DECLARATION for
  freshly declared content.
- Optional replacement content ID, construction and guarded setter locations;
  inferred bindings with ancestors and children inline.

Callback execution is `not_evaluated`. Runtime presence/subclasses/reachability,
other dispatch routes, default form processing, validation results, model values,
feedback/business outcomes, behavior/opaque data/constructor/configuration
effects, bridge/post-callback effects, visibility and later tree/open/close state
are `unknown`. The framework route can dispatch with disabled default processing;
it does not establish successful validation.

Default structure, validation and component-context responses are unchanged.
Initial selectors cannot select submission-only children. Project/module
isolation, committed snapshot freshness, cancellation, source dependency tracking
and the 512-KiB whole-response bound apply. Discovery permits 40,000 steps,
96 sources, eight contexts, 128 nodes per graph, 12 ancestry/source levels and
128 effect records per callback. Budget exhaustion withholds all submission
graphs and marks discovery incomplete; unsupported individual callbacks retain
diagnostics while independent callbacks can contribute. Indexed navigation has
its existing aggregate source and 256-binding bounds. All discovery is read-only.

## Acceptance

Fixtures cover feedback and stable Ajax targets, fresh label/fragment replacement,
source bridge guards, withheld unsupported callbacks, bidirectional navigation,
protected rename, unchanged initial selectors, source/module isolation, unsaved
hook/bridge/setter/markup freshness, cancellation and response/discovery bounds.
An actual MCP host check exercises the optional context contract over HTTP.

An independent WicketTester oracle checks rendering without submission, blocked
default submission after required-field failure, successful dispatch and fresh
content replacement, and dispatch with disabled default form processing. Source
review uses Wicket 10.11.0 and 11.0.0 AjaxButton, AjaxFormSubmitBehavior and Form;
the local member hashes and real-application results are recorded in the
[evidence summary](wicket-oat-submission-evidence.json).

This milestone does not yet analyze general Form.onSubmit/onError protocols,
arbitrary business save/delete operations, callback conditionals, helper chains,
model/validation results or a sequence of runtime tree states.

Final verification: **2,971** normal tests, **23** actual MCP host tests and **22**
release-checker unit tests passed with zero failures/errors/skips. Both read-only
Oat corpora (Wicket 10.11.0 and 11.0.0) passed all three submission contexts.
All 36 ordinary page summaries, earlier template groups, six completion probes
and DialogPage's initial/event text reports match milestone 19. All 318
application input hashes remain unchanged. Configuration, structure and binary
compatibility checks passed against IU-262.10968.63: `Compatible. 5 usages of deprecated API. 6 usages of experimental API`.
The full build/verification run took 23m 37s. Current source/test/artifact
receipts match the ZIP. See the [evidence summary](wicket-oat-submission-evidence.json)
for counts, framework source contracts and hashes. The ZIP retains 0.1.0 metadata.


Milestone 21 adds [ordinary form submit/error declarations](form-submission-templates.md)
in the same optional context collection. Their separate own-companion scope and
formCallback provenance retain uncertainty about initial setup. The milestone 20
dialog metadata and behavior described above remain unchanged.
