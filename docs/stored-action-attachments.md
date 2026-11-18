# Initial attachments beside stored actions

The initial graph can retain an attached table on a plain page whose constructor
registers an inline action. The construction pass proves that the particular
registration stores its callback without invoking it. The callback body remains
outside initialization analysis. Initial bindings are inferred declarations with
no rename authority; later execution and runtime tree state remain unknown.

The supported registrar is a direct new instance in an unchanged local in the
selected page's sole constructor. The local is used only as the receiver of
reviewed direct registration statements. Multiple sites now use the complete
[registrar-group audit](repeated-action-registrations.md), bounded at 64 sites. Its actual source class has Object ancestry,
no interfaces or initializers, one private uninitialized nonstatic callback slot,
and an empty zero-input constructor (or implicit constructor). The source method
accepts a literal String label and the unchanged reviewed BiConsumer operation.
Its complete body assigns that parameter to its own callback slot and returns
this. The shared consumer-interface proof checks the JDK operation and permitted
serializable wrappers; neither interface initialization nor callbacks execute.

Eager invocation, helper forwarding, additional effects, aliasing or
borrowed registrars, anonymous overrides, initialized/foreign slots, nested
registrations and unsupported constructor effects withhold this expansion.
Remaining page construction and initialization still pass the existing bounded
layout audit. Unrelated callbacks retain their existing checks. This rule does
not suppress arbitrary lambda effects or promise that stored callbacks preserve
the hierarchy later. For example a stored callback may declare removeAll(); that
cannot become an active runtime attachment or a reviewed record-action context.

The initial report contains optional storedActionRegistrations metadata with
allocation, registration, method, slot and callback source locations. The same
locations and inferred table binding appear in native reporting and existing
MCP structure/component projections. A separately reviewed ACTION_TEMPLATE can
then use the initial declaration through the existing attachment-identity guard.
Active storage/UI registration, dispatch, visibility, later tree state and
business outcomes stay unknown. No trigger ID is invented, and the action
context's existing argument-provenance contract is unchanged.

MCP decision: extend wicket_get_page_structure, wicket_get_component_context and
optional navigation context projections with this additive value snapshot.
The source storage proof and newly available initial target help an agent trace
a plain-page action without executing it. No new tool, arguments, schema version
or editing operation is needed. Reuse the semantic analyzer, exact exporter,
project/module selection, snapshot/freshness, cancellation and response limits.
Successful and withheld registrars remain source dependencies. The 512-KiB whole
response cap also covers the new metadata. Client and tool descriptions document
its declaration-only meaning.

Repeated registrations are covered by the
[follow-up contract](repeated-action-registrations.md). Fluent chaining remains
outside the direct-statement rule. The existing table-owned bulk-action fixture
continues to use its prior path. Broader protocols and runtime outcomes remain
unresolved.


## Stored-action attachment verification

**691 focused checks**, **45 actual MCP host tests**, both read-only
36-page Wicket corpora and plugin configuration/structure/binary compatibility
checks passed with zero failures/errors/skips. The added plain-page fixture has
one inferred initial table binding and one separate inferred action target on
both Wicket 10.11.0 and 11.0.0. All prior corpus facts, 36 initial reports, nine
saved text reports and the original 318 application inputs are preserved.
The runtime fixture confirms registration/render does not invoke its callback;
explicit fixture dispatch reaches the updater. Analysis executes no callback.
Compatibility against IU-262.10968.63: `Compatible. 7 usages of deprecated API. 6 usages of experimental API`.
Source, host and artifact receipts match the development ZIP. The full normal
suite was not rerun; no full-suite or release-readiness claim is made. See the
[milestone evidence](wicket-oat-stored-action-evidence.json). No installation,
signing or publication was performed.
