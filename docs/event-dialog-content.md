# Row-triggered dialog and drawer content

Milestone 18 adds a separate source event template for DialogPage's details link.
Its five bindings describe `detailsDrawer:dialog:form:content` as a fresh
personDetails Fragment containing an OatDescriptionList named list, plus the
three required host ancestors. All bindings are INFERRED and protected from
paired rename. The primary report remains 72 matches and 13 branch findings;
the initial details drawer retains its plain placeholder.

The trigger path `people:rows:details` belongs to a symbolic row. Runtime row IDs,
count, model values, visibility, validation, open/close state and subsequent
replacements remain unknown. A click handler is inspected as source; it is never
executed. The presence of this template does not establish that a click occurred.

## Reviewed source contract

A matched standard AjaxLink.onClick handler must declare a fresh framework
Fragment as its first statement, add fresh child declarations, optionally configure
the unchanged captured host, and pass the content and its own AjaxRequestTarget
to a final source transfer call. The provider must be the qualified selected page.
The content ID must equal the host's guarded slot ID. The host must be one direct,
unchanged local constructed and attached by the selected page constructor.

The existing initial-content protocol proves the private-final parent, placeholder,
throwing ID guard and inherited setter route. The event transfer must forward the
supplied Component directly to that setter, optionally assign owned boolean flags,
and forward the same target to its own receiver-returning open helper. Setter,
configuration and helper dispatch account for the constructed source subtype.
Pure super forwarding is supported. The helper permits reviewed form input reset,
markup-tag queries, boolean state declarations and Ajax rendering. It does not
supply a general event interpreter.

The shared content-factory declaration analyzer supplies the fresh graph. The
existing source constructor identity proof, provider-aware fragment projection,
owned markup matching and component-context exporter supply its bindings.
Fluent child declarations may forward through bounded source helpers that return
the same allocated receiver. Their constructor, configuration, model and callback
effects remain unknown. Private list internals and property-expression values are
outside this event graph.

Wrong IDs, borrowed or escaped content, ambiguous triggers, aliases, reassigned
hosts, conditional transfers, unsupported event handlers, changed fluent identity,
structural host configuration, unsafe setter overrides and missing or ambiguous
markup counterparts withhold the template. Bounds withhold all event graphs and
mark optional discovery incomplete. Other eligible but unsupported source sites
retain a diagnostic and their consulted dependencies for freshness.

## Native navigation and MCP decision

Extend the existing optional structure navigation contexts. No new tool, parameter,
or schema version is added. Request `wicket_get_page_structure` with
`includeNavigationContexts=true`, then choose relationship EVENT_TEMPLATE and
lifecycle.kind SOURCE_DECLARED_EVENT_CONTENT. Native constructor `new` navigation
opens the guarded content slot; list IDs and fragment definitions navigate in both
directions through the same semantic graphs. These declarations grant no editing
authority. Initial validation and component selectors retain their existing output;
an event-only list path cannot select the initial hierarchy.

| Metadata | Meaning |
| --- | --- |
| lifecycle.componentPath | Target source attachment, relative to the selected page |
| lifecycle.factoryLocation, invocationLocation, eventLocation, replacementLocation | Fresh Fragment, final transfer, handler and guarded source placement |
| directContent.pathScope | PAGE_DECLARED_ATTACHMENT |
| triggerPathScope | SYMBOLIC_ITEMS for a population trigger; otherwise PAGE_DECLARED_ATTACHMENT |
| triggerComponentPath, triggerPopulationContextId, triggerHostComponentPath | Trigger declaration and its separate symbolic row context |
| rowPreconditions[] | Required source row predicates; never current runtime results |
| hostNodeId, hostConstructionLocation, contentLocation, setterLocation, transferLocations[] | Source ownership and placement provenance |
| configurationLocations[], callbackLocations[] | Unevaluated declaration locations |
| callbackExecution, configurationExecution | not_evaluated |
| runtimeRowIds, rowCount, modelValues, visibility, validation, openCloseState, otherEventEffects, laterTreeState | unknown |

Node IDs are local to one snapshot. A symbolic trigger path is not a runtime row
path. Paths in the event graph describe conditional source attachments only.
Discovery is limited to 20,000 PSI steps, 96 sources, four contexts, 128 graph nodes
and 12 source forwarding levels. Existing module/project/source-set isolation,
request cancellation/deadline, freshness and the 512-KiB whole-response bound remain
in force. Tools remain read-only.

Acceptance covers the two pinned Oat corpora, default-graph preservation, native
navigation and rename guards, optional MCP values and actual host calls, module
isolation, unsaved source/markup changes, cancellation and discovery/response
bounds. An independent WicketTester fixture checks the initial placeholder,
row-click replacement, rendered fragment content and detachment on a later click.
Those runtime observations do not add runtime values to static reports.

General open/close protocols, save/delete outcomes, validation execution, arbitrary
event control flow, list property values and shared refactoring remain later work.

Final verification: **2,910** normal tests, **21** actual MCP host tests and **22**
release-checker unit tests passed with zero failures/errors/skips. Both read-only
Oat corpus versions (Wicket 10.11.0 and 11.0.0) passed. DialogPage has a separate
five-binding inferred event template; its initial 72-match/13-branch report is
unchanged. All 36 ordinary page summaries and earlier milestone template
summaries match the previous build exactly. All 318 application input hashes
remain unchanged. Configuration, structure and binary compatibility checks passed
against IU-262.10968.63: `Compatible. 5 usages of deprecated API. 6 usages of experimental API`. The full build/verification run
took 22m 35s. Current source/test/artifact receipts match the ZIP. See the
[evidence summary](wicket-oat-event-content-evidence.json) for counts and hashes.
The ZIP retains 0.1.0 metadata.


[Context-aware completion](context-completion.md) now reuses supported graphs from
this milestone for parent-scoped Java/HTML ID suggestions. Its separate editor
probe does not change the normal inspection, fix, paired-rename or MCP contracts.
Inferred candidates remain labelled with their source context and uncertainty.
