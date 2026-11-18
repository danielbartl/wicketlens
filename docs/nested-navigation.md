# Named nested companions and navigation contexts

This unreleased development milestone follows [component factories](factories.md).
Named member Page, Panel and FormComponentPanel classes can pair with their own
binary-name resources. Native references can also use bounded, independently
analyzed caller and inheritor contexts. No new constructor, form-field or event
lifecycle execution is introduced.

## Companion identity and selection

`example.Outer.Fields` pairs with `example/Outer$Fields.html`;
`Outer.Group.Deep` uses `Outer$Group$Deep.html`. Static and nonstatic named members
have stable resource identities. Their source must be a physical Java member
chain whose outermost declaration matches its Java filename. Local and anonymous
classes, secondary top-level declarations and unavailable identities remain
unsupported. Nested classes do not borrow `Outer.html` or `Fields.html`.

The existing module, main/test source-root, package-prefix, resource-output mapping,
ambiguity and custom markup-provider guards still apply. Exact variant suffixes
use the binary basename. HTML reverse lookup checks all bounded source/member
candidates and requires a unique conventional owner. A top-level class containing
`$` can make reverse lookup ambiguous; no class is selected in that case.

Place the Java caret in the named member to analyze that component independently.
Opening its HTML selects the same canonical source class. File-level editor
snapshots include named member markup owners with available companion/inherited
markup, while the explicit analysis action can also explain missing resources.
Selecting an enclosing caller continues to analyze the caller's own graph.

Named identity is bounded to 16 class levels. Member discovery permits 256
declarations per file; reverse lookup permits 64 possible source names and 64
candidate Java files. Exhausted or ambiguous reverse discovery does not choose a
partial unique result.

## Shared navigation discovery

`WicketNavigationContextsAnalyzer` proposes source roots through indexed class
references, their lexical markup-owning classes, and source inheritors. It checks
the actual selected class's visibility from the candidate's module, keeps source
sets separate, and only examines the selected project. A source declaration with
the same qualified name in another module is not interchangeable with it.

Each candidate is analyzed with the existing `WicketPageAnalyzer`. References
never establish attachment or instantiate a factory. The result distinguishes:

| Relationship | Evidence |
| --- | --- |
| `OWN` | The independently analyzed requested class. |
| `INHERITED` | A concrete source descendant analyzed as its own root. |
| `CALLER` | An observed component scope, relevant matched binding or Fragment reference in another analyzed root. Scope findings still distinguish matched and unresolved attachments. |
| `CANDIDATE` | An indexed use with no established component scope or relevant binding. Its analysis warnings remain available. |

For a proved binding, the same graph retains its Java origin, markup attribute,
instance node identity and confidence. A shared resource may lead to distinct
Java targets; repeated use of one source location keeps distinct graph contexts
and one native PSI navigation destination. Logical repeater items continue to
omit runtime IDs, and inferred template confidence is preserved.

Native polyvariant references use these extra reports. Caller-only ID arguments
can navigate in both directions; synthetic completion copies do not supply
source-context facts. Local inspections,
completion, quick fixes and paired rename keep their existing local snapshots
and safety checks. New navigation contexts do not grant editing authority;
shared Fragment rename remains withheld. The subsequent
[origin-use audit](id-origin-audit.md) permits isolated named-member pairs; any
outside source use or incomplete member-reference search withholds that edit.

Discovery permits 256 references, 256 inheritor visits, 16 caller candidates and
256 relevant matched bindings. Probing stops after the 512-source dependency
threshold; dependencies of the last consulted graph are retained for freshness.
The existing per-graph analysis budgets remain. Exceeding a bound marks discovery
incomplete with a diagnostic. Native navigation then retains its local contexts
and withholds additional caller targets. This is a bounded indexed search, not an
enumeration of arbitrary transitive helper calls or all runtime uses.

Caller discovery uses default markup. An editor-selected locale/style variant
retains its exact own context; additional caller discovery for variants remains
unresolved. This avoids mixing default-resource bindings into a selected variant.

## MCP decision and compatibility

Extend `wicket_get_page_structure` with optional
`includeNavigationContexts: boolean = false`. Existing calls retain their exact
response shape and analysis schema **1**; no new tool is added. Nested canonical
class names use dots in `className` and binary `$` names only in resource paths.

```json
{
  "projectPath": "/path/to/project",
  "className": "example.Outer.Fields",
  "sourceSet": "main",
  "includeNavigationContexts": true
}
```

The optional sibling `navigationContexts` object contains `discoveryKind`,
`discoveryComplete`, `diagnostics`, `sourceFiles` and `contexts`. Each context
identifies its `className`, `moduleName`, `sourceSet`, `relationship`, root
`javaLocation`, markup path/suffix, scope flag and warnings. `scopes` projects
observed instances; `bindings` projects relevant **MATCHED** findings. Both reuse
the existing component-context projection's component, component path, hierarchy
flag, exact findings and associated markup elements. Fragment provenance is
retained when present. An indexed `CANDIDATE` has no invented scope or binding.

Incomplete discovery sets the envelope's `truncated` flag and its own
`discoveryComplete=false`. The primary `analysis` graph remains intact. Discovery
completion does not establish complete runtime hierarchy or model values.
Node IDs are local to these snapshots: fetch the indicated caller's structure
again before selecting it with the context tool. Paths can be null when they
cannot uniquely select a component.

All consulted Java/markup dependencies join the shared value snapshot's freshness
checks. Unsaved edits invalidate it without saving files. Project selection,
external refresh, smart/committed reads, cancellation/project disposal, the
30-second request budget and post-serialization freshness checks are reused.
Each graph retains the 5,000-component/10,000-markup limits; the entire response,
including optional contexts, retains the 512-KiB UTF-8 limit and is rejected when
oversized. No source, sidebar, clipboard or application state is changed.

## Wicket Oat acceptance and limits

`ProductsPage.ProductFields` now pairs with
`ProductsPage$ProductFields.html` and is selectable through Java, HTML and MCP.
At this milestone its six field declarations encountered the `BaseOatField`
static metadata/lazy-input effect boundary and retained unresolved findings;
the surrounding Products CRUD graph and its constructor-reference factory are
not expanded by resource pairing. Context discovery preserves ProductsPage as
an unproved candidate.

Inbox retains **31 matched bindings: 26 confirmed and 5 inferred**. Opening the
core `OatSplitLayout.html` resource can now navigate its `second` attachment to
Inbox's actual captured Fragment origin. Optional MCP discovery exposes the
same Inbox scope and bindings. The read-only corpus checks both Wicket 10.11.0
and 11.0.0 without changing the application's resource layout.

Focused tests cover nested/deep/nonstatic identities, reverse ambiguity and
exhaustion, variants, caller captures, inherited shared Fragment targets, module
and source-set isolation, unsaved freshness, cancellation, discovery/response
bounds and read-only use. A separate Wicket 11 WicketTester process confirms the
static and deep member resource names. Actual MCP host tests cover the optional
parameter, default-response compatibility and routing between open projects.
See [the evidence summary](wicket-oat-navigation-evidence.json) for final results.

The following [owned form-field milestone](owned-form-fields.md) establishes
those six bindings through bounded metadata/lazy-input summaries. The later
[replacement-editor milestone](replacement-editors.md) exposes their registered
CRUD placement as a separate inferred event context. General
constructor-reference placement, wizard/tree event protocols, broader caller
discovery remain follow-up work. The origin-use audit now permits a bounded
subset of isolated paired edits. No live IDEA 2026.3 installation or full application
accuracy/performance study is implied.


[Context-aware completion](context-completion.md) now reuses supported graphs from
this milestone for parent-scoped Java/HTML ID suggestions. Its separate editor
probe does not change the normal inspection, fix, paired-rename or MCP contracts.
Inferred candidates remain labelled with their source context and uncertainty.
