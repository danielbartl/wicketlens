# Owned component accessor decoration

The thirty-first milestone extends the bounded owned construction audit for
`chooser.getTrigger().add(AttributeModifier.replace(...))`. The selected source
getter must return its own private-final, freshly allocated framework child. The
owner must be an earlier direct local allocation in the same method, with no
reassignment, prior attachment, aliases or unreviewed uses. Earlier successfully
reviewed decorations may use that same owner again.

The motivating call in OatDataTable.newTools uses **OatPopover.getTrigger()**.
OatDropdown declares a similar accessor; the earlier roadmap used that name.
This proof does not depend on either application class name.

## Identity, construction and binding guards

Source dispatch follows the allocated concrete/anonymous type and records the
selected getter and its source ancestors. The getter takes no inputs, is not
synchronized or static, and consists of one direct field return. The field must
belong to that declaration, be private/final/nonstatic and have one direct fresh
WebMarkupContainer(String) allocation with a literal ID. Source child subclasses,
anonymous children, borrowed values, conditional assignments and alternative
field writes remain unreviewed.

The selected source constructor path must establish the field, including bounded
this/super delegation or a field initializer. The owner hierarchy's initialization,
fields, selected constructor bodies and initialization hooks also pass the shared
construction-effect audit in the allocated owner's context. Source interface
initialization uses the existing shared audit. Pure field-return syntax alone
does not approve constructor effects.

The private child can be used by its selected getter, its direct assignment and
reviewed constructor additions: direct framework Labels, stored AttributeModifier
factories and attachment to its own owner. Additional getters, aliases, consumers
and other child uses withhold this proof. Constructor effects and behavior/model
inputs remain independently audited; later rendering and model values are unknown.

The new external operation is deliberately limited to one exact compiled
Component.add(Behavior...) call with AttributeModifier.replace using literal
attribute and replacement strings. It grants no authority to add components,
replace/detach children, use an arbitrary Behavior or configure a borrowed owner.
Unrelated fluent-call and factory analysis retains its earlier rules. No private
child node, component path or paired-rename authority is added to the page graph.

The proof reuses the pinned Wicket 10.11.0/11.0.0 behavior-binding audit. The
AttributeModifier scalar factory stores its value model; its inherited Behavior
binding does not evaluate the replacement model. Wicket 10's Behaviors/inline
Component storage and Wicket 11's ComponentState implementation were reviewed
separately. A WicketTester oracle checks getter identity, owner attachment, eager
content-factory invocation, deferred initialization/model loading and the rendered
attributes.

## Native and MCP behavior

A retained diagnostic describes completed accessor decoration, selected source
identity and unknown rendering/model/visibility/later state. It survives a later
independent setup failure while the overall layout remains unresolved. Source
wrappers, getters, constructor paths, interface eligibility and framework archives
participate in snapshot freshness. Invocation-local scans check cancellation at
AST nodes and reuse the existing 12-level ancestry/call bounds and shared
20,000-step/96-source budget. The MCP deadline and 512-KiB response limit remain
unchanged.

MCP decision: extend existing page-structure, validation and component-context
**diagnostics** through the same semantic analyzer and value snapshots. The
ownership fact and precise later blocker help an agent diagnose partial reports.
No new tool, parameter, selector, response member or schema is needed. Descriptions
and client documents describe the guarded scope. Native/MCP equality, module
isolation, unsaved getter edits, constructor/owner/interface/markup freshness,
cancellation, bounds and read-only actual-host behavior are covered by acceptance.

## Application scope

DataTablePage now passes the original `chooser.getTrigger().add(...)` call at
OatDataTable.java offset 15146, line 316. Its next stop is a source fluent call
on the freshly allocated anonymous bulk-action clear button: offset 6999, line
144, with `An unknown component receiver has open source dispatch`. The overall
initial hierarchy remains unresolved.

A corpus fixture preserves the original constructor prefix through `newTools`,
the complete original `newTools` and RowsTable sources except the enclosing type
name, and the original OatPopover getter/constructor. It assigns null to the
remaining bulk-action field and keeps page seeding/provider/confirmation setup.
Native/MCP views share its 26 inferred matches and nine separate branch entries.
The original application checkout remains unchanged.

Final milestone verification: **3,389** normal tests,
**35** actual MCP host tests and **22** release-checker unit tests passed
with zero failures/errors/skips. All 224 focused tests passed.
Both read-only Oat corpora (Wicket 10.11.0 and 11.0.0) verify the original
newTools accessor decoration in an isolated fixture with 26 inferred matches and
nine branch counterparts. Native/MCP facts agree. DataTablePage next stops at
the bulk-action clear button's fluent source call, OatDataTable.java offset 6999,
line 144. All 36 primary finding/binding summaries, earlier groups/probes, six
markers, four reports and 318 application input hashes are preserved. Its source
dependency count increases from 38 to 40. Configuration, structure and binary
compatibility checks passed against IU-262.10968.63: `Compatible. 7 usages of deprecated API. 6 usages of experimental API`. The full run took 28m 44s.
Current source/test/artifact receipts match the ZIP. See the
[milestone evidence](wicket-oat-owned-accessor-evidence.json).
No installation or publication was performed.
