# Context-aware Wicket ID completion

Milestone 19 extends Basic Completion in Java ID literals and HTML wicket:id
values to the source contexts added by the Oat milestones. Press Ctrl+Space
inside a quoted empty or partial value. Suggestions show the component type,
source context and an inferred label when they come from a declaration/template.

Completion first reuses the existing page, fragment, owned markup, population,
factory, event, base-layout, tree, input and toolbar analyzers. In a known scope,
it suggests unused IDs under the current parent. If one literal or attribute has
several proved uses, an ID must be available in every such scope. Queue completion
uses its own region and occupancy checks.

## Companion-file suggestions and navigation

When completion cannot establish a parent scope, it can show IDs from the active
page or panel’s conventional companion. These entries carry a **possible** label
and require explicit selection. The search uses literal Java component constructor
ID arguments and HTML `wicket:id` values, including IDs inside markup fragments.
It excludes known siblings, panel preview markup, fragment definition IDs and
ordinary strings such as `addProperty("email")`. A known parent with no unused
IDs keeps an empty list. Java origins known to be branch-merged or involved in
ID computation also withhold companion suggestions.

Ctrl-click uses an established binding when available. With only one possible
same-ID target, IntelliJ opens it. With more than one, IntelliJ's chooser shows
the source text, enclosing container or fragment, and line number. Each entry
provides its own IntelliJ navigation presentation and points to the original
source element.

The fallback reads committed PSI from one uniquely resolved companion pair in
its own module and source set. The limits are 524,288 characters per file,
30,000 visited PSI elements per scan and 128 distinct suggestions or navigation
targets. An ambiguous companion pair or a limit violation withholds the fallback.

MCP needs no change for this editor feature. Possible targets are local editor
hints. Committed analysis still supplies the bindings used by reports,
inspections, paired rename, quick fixes and MCP.

The Wicket Oat corpus checks completion of the table row’s `email` in
`DialogPage` in both directions. It checks that the known row binding wins,
then verifies both chooser targets with the parent scope unresolved. The
corpus only edits fixture copies. A separate editor regression checks the
presentations and destinations that Go to Declaration passes to the chooser.

Accepting a suggestion replaces the whole edited value, including a suffix after
the caret. Java/XML escaping and the original quotes are preserved. Completion
changes the selected value only. It does not rename the counterpart or grant
paired-rename/quick-fix authority. Runtime row IDs, counts, model values, callback
execution, validation, visibility and later event state remain unknown.

## Incomplete editing sites and cache isolation

Newer source declaration analyzers require a nonempty component ID. An internal,
completion-only probe can retain one empty literal at the exact physical source
location currently being edited. Constructor identity, ownership, parentage,
provider selection, source dispatch and all other source effect checks still run.
The empty ID is never inferred to be a valid runtime ID. Other empty/reserved IDs
retain their usual checks. Multiple incomplete declarations can withhold a graph.

Event templates may retain mismatched ID findings during this editor probe so
that a typed prefix can receive scoped suggestions. The semantic projection still
requires a known, unique source parent and a usable counterpart. The scoped probe is restored
on success, failure and cancellation, including when IntelliJ reuses a worker.

Completion reads committed physical sources, never IntelliJ's synthetic completion
copy. Each physical file has a separate value cache, holding at most four editing
sites per PSI/root/VFS generation. Source and counterpart edits invalidate it;
uncommitted documents withhold suggestions rather than mixing snapshots. Cache
values contain reports and locations, not retained PSI/project objects. Analysis
runs outside the cache monitor. All discovery is read-only; accepting a lookup
item is the explicit editor operation that writes the selected value.

The value projection allows 30,000 steps, 64 observed scopes, 128 distinct IDs,
32 parent levels and IDs up to 256 characters. Exhausting a projection budget
withholds its whole candidate set. Existing source/interpreter/indexed discovery
bounds and cancellation checks remain in force. Exhausted extra-context discovery
supplies no extra graphs; independently proved local scopes can still contribute.
Unknown global root effects remain excluded outside explicit declaration views.

## MCP decision

No MCP tool, parameter, schema or response change is needed for this native editor
extension. Candidate IDs, component types, parents, source locations, context
provenance and confidence are existing facts exposed by structure and optional
navigation contexts. The new operation projects those facts for a caret and an
incomplete quoted value, and provides popup labels and insertion behavior. Its
editing-site recovery is not an observed component binding and is deliberately
kept out of the normal snapshots consumed by agents. Existing MCP selectors and
all 512-KiB/freshness/cancellation/read-only contracts remain unchanged.

Tests check that a native probe can offer an event child while the same default
MCP snapshot continues withholding that incomplete event graph. Both direct MCP
capture and actual host HTTP calls retain identical analysis/navigation values
after the probe. No completion hypotheses are exported as confirmed components.

## Acceptance

Native fixture checks cover empty and partial Java/HTML values in initial dialog
fragments, row events, immediate factories, registered item callbacks and abstract
base layouts. They cover shared nested helper uses and incompatible shared
fragment uses, occupied siblings, source context/type/confidence labels, insertion
and escaping, protected rename, module isolation, cache freshness, uncommitted
sources, synthetic copies, computed expressions, unsafe parents/transfers,
cancellation and projection bounds.

The real Oat corpus uses temporary fixture-copy edits and restores them after each
probe. Expected inferred candidates are:

| Source | Direction | Editing site | Suggestion |
| --- | --- | --- | --- |
| DialogPage | Java and HTML | personDetails list | list |
| DialogPage | Java | initial editFields input | name |
| AccordionPage | Java and HTML | registered item title | title |
| OatAppLayout | Java | shared outer/nested row helper | menuBadge |

The application checkout is read-only. Normal page/template summaries, inspections,
MCP output and runtime fixtures are regression checked separately. Automatic popup
triggering follows IntelliJ's completion settings; Ctrl+Space invokes the feature
explicitly. Arbitrary invalid syntax, unresolved ownership and general property or
model-expression completion remain outside this milestone.

Final verification: **2,936** normal tests, **22** actual MCP host tests and **22**
release-checker unit tests passed with zero failures/errors/skips. Both read-only
Oat corpus versions (Wicket 10.11.0 and 11.0.0) passed all six completion probes.
All 36 ordinary page summaries, earlier template summaries and DialogPage's
initial/event text reports match milestone 18 exactly. All 318 application input
hashes remain unchanged. Configuration, structure and binary compatibility checks
passed against IU-262.10968.63: `Compatible. 5 usages of deprecated API. 6 usages of experimental API`. The full build/verification
run took 23m 30s. Current source/test/artifact receipts match the ZIP. See the
[evidence summary](wicket-oat-completion-evidence.json) for counts and hashes.
The ZIP retains 0.1.0 metadata.

## Popup scoping and transparent-layout correction

The user-visible popup now contains only Wicket counterpart candidates at a
recognized Java component-ID site or exact HTML wicket:id value. Wicket completion
runs before generic contributors and stops their fallback at those sites. Generic
string completion previously mixed display text and IDs from unrelated scopes into
the list. Ordinary model/display strings and other attributes retain their normal
completion behavior. Tests inspect every popup item, including untagged items.

A second failure affected DialogPage's root components under OatHtml. Transparent
markup resolution had no binding for an empty or partially typed ID, so that
unmatched tag retained its physical HTML parent. The completion projection then
looked in the wrong Java scope and returned no candidates.

The existing transparent resolver now retains bounded lookup scopes during the
focused editor probe. These scopes preserve ordinary-child precedence, nested
transparent fallback, the unknown-content barrier and ordinary container
boundaries. Ambiguous/unresolved surrounding markup supplies no hint. An editing
scope describes where candidate IDs may be looked up; it does not bind an unknown
ID or descend through an unbound component. Used siblings and shadowed fallback
IDs remain excluded. An entirely populated parent can therefore have no unused
counterpart IDs for an additional tag.

The lookup scopes are value-only data in the separate completion snapshot. They
are never exported, placed in normal report caches or used for inspections,
paired rename, quick fixes or MCP selection. Freshness, cancellation, module
isolation and existing discovery/projection bounds remain in force. Nested probe
failure restores both the Java and HTML editing sites.

MCP decision: no tool, parameter, schema or response change. The correction
projects existing source parent/transparent lookup facts for an incomplete editing
site and controls native popup contributors. These are editor hypotheses, not
new observed component bindings. Direct and actual-host checks preserve identical
normal analysis/navigation values after a probe, with no lookup-scope data in
responses.

Real DialogPage popup checks cover empty and partial deleteDialog host IDs, the
initial name field and the event-created list in both Java and HTML. All eight
checks inspect every contributor, not just WicketLens-tagged entries. Earlier
report/template/completion corpus results and application input hashes remain
unchanged.

Final correction verification: **3,024** normal tests,
**25** actual MCP host tests and **22** release-checker unit tests passed
with zero failures/errors/skips. The 186 focused tests passed. Both read-only Oat
corpora (Wicket 10.11.0 and 11.0.0) passed all eight complete-popup checks for
DialogPage: empty and partial host IDs, initial fragment fields and event content,
in Java and HTML. All 36 ordinary page summaries, prior template/completion
results and four saved text reports match milestone 21. All 318 application input
hashes remain unchanged. Configuration, structure and binary compatibility checks
passed against IU-262.10968.63: `Compatible. 5 usages of deprecated API. 6 usages of experimental API`. The full run took 23m 49s.
Current source/test/artifact receipts match the ZIP. See the
[correction evidence](wicket-oat-popup-scoping-evidence.json) for counts and hashes.
The ZIP retains 0.1.0 metadata.
