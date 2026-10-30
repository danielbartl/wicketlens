# Conventional markup inheritance

The composed page analyzer resolves and merges Page/Panel/FormComponentPanel markup
without constructing synthetic PSI or changing source files. The Java graph can
also include constructor/initialization setup from source ancestors through the
[bounded source setup pass](source-setup.md). Unknown inherited effects remain
unexpanded; markup composition alone does not establish the Java hierarchy.

## Framework contract

The [official Wicket inheritance documentation](https://wicket.apache.org/docs/markup-inheritance.html)
describes ancestor fallback, replacement by an own companion, and insertion of
`wicket:extend` content into a parent `wicket:child` slot. `MergedMarkup` was also
reviewed in the pinned Wicket **10.11.0**
[source archive](https://repo.maven.apache.org/maven2/org/apache/wicket/wicket-core/10.11.0/wicket-core-10.11.0-sources.jar),
SHA-256 `4b72cbf44463b7ce2ca39e1cb3c8c43095d0b3d23ef29e221fa48332a453be34`.
The broader slice also audits `HtmlHeaderSectionHandler`, `HtmlHeaderResolver`,
`HeaderPartContainer`, `AssociatedMarkupSourcingStrategy`,
`DefaultMarkupResourceStreamProvider`, and the resource-name iterators in that
same artifact. It supports static header contributions, explicit filename
variants, and read-only layouts from compiled dependency ancestors.

- Search the selected class and then source ancestors for conventional companions,
  using each class's configured module roots. Skip missing companions, including
  intermediate classes; stop at ambiguous/unsupported lookup results. Compiled
  ancestors use the requesting source module's visible classpath resources.
- An own companion without `wicket:extend` replaces the ancestor layout. An
  extension must have one exact `wicket:extend` section, matched to one active,
  empty `wicket:child` slot in the resolved ancestor markup. Structural markers
  with attributes or slot content are unresolved. Multi-level chains can leave a
  new child slot for the next level.
- Panel base markup must provide its usual single `wicket:panel` region. Derived
  Panel extensions use the same insertion rules as Pages. Preview elements outside
  a derived extension or the active base Panel remain inactive observations.
- Recompute nearest Wicket parents and duplicate IDs after insertion. Preserve
  ordinary HTML wrappers, original source paths/offsets, and distinct node IDs
  across files. Cross-file duplicates and uncertain ancestor contexts propagate
  uncertainty into descendants.

Unfilled slots, missing/multiple slots or extensions, malformed syntax, custom
Wicket prefixes, unavailable ancestors, and unsupported resource layouts do not
produce confirmed or definitive unmatched claims in the affected merged scope.
All reports remain partial and informational. Successful markup composition does
not establish inherited Java setup. Unsupported/unavailable inherited Java
effects retain their scope cause instead of producing `MARKUP_ONLY` claims;
fully traced source setup can establish ordinary unmatched observations.

`MarkupReport.file` and `PageReport.markupPath` identify the selected companion.
Element locations identify their actual source files. Snapshot-only `sourceFiles`
records every contributing markup dependency, including empty extensions, so
navigation and copying cannot use a report after any contributing file changes.
The JSON schema stays at version 1; source provenance is expressed by its existing
locations and IDs. No live PSI or file objects are retained in prepared reports.

## Header contributions

An ordinary Page `head` is transparent to logical component parentage: a title
Label remains a root child. An explicit `wicket:head` outside and before the
Panel/extension region contributes its contents in the owning component's scope.
Derived contributions are merged into the ancestor head when present. A missing
head does not create invented source elements or offsets. Plain HTML head content
outside a derived extension or Panel is preview markup, rather than a contribution.

Multi-level contributions preserve original source locations and are checked for
duplicate component paths across header/body files. Static CSS/JavaScript/meta
tags do not invent Java components. Multiple, misplaced, attributed/scoped
contributions, custom component-owned head tags, header child slots, and malformed
boundaries remain unresolved. Runtime HeaderItems, Java `renderHead()` execution,
resource deduplication, and custom header placement are not simulated.

## Explicit markup variants

Opening `DetailPage_fr.html` selects suffix `_fr`; opening an exact styled filename
such as `DetailPage_mobile_dark_fr_CA.html` selects that whole suffix. The selected
file and exact matching ancestor variants are analyzed together. An ancestor with
no variants can supply its ordinary companion. If an exact ancestor variant is
missing but other named variants exist, fallback remains unresolved: a filename
alone does not identify the runtime locale/style/variation values or their full
fallback order. Java analysis continues to use unsuffixed markup.

Follow editor, re-analysis, source navigation, inspections, gutter report actions,
completion, and references preserve the selected HTML context. The sidebar status
names the variant. Ambiguous class/variant names, duplicate resource locations,
and unsupported suffixes are rejected. Underscores in Java class names are allowed.
Reverse lookup is bounded to 16 possible Java basenames; inheritance lookup is
bounded to 32 classes. No runtime session settings are inferred or changed.

Java shared by several templates needs a broader refactoring: paired rename and
Java-changing inspection fixes are withheld when variants exist. Supported fixes
that change only the selected HTML remain available. Literal coloring is unchanged.

## Dependency layouts

Compiled top-level ancestors may provide conventional HTML in JARs or library
class directories visible from the requesting source module. Production lookup
excludes test-only libraries. Visible configured module resources participate in
duplicate detection; application overrides and duplicate classpath resources are
reported as ambiguous rather than silently choosing a winner. Custom output
mappings and custom Wicket markup providers/types/strategies remain unresolved.

Dependency markup participates in inheritance, headers, exact variant lookup,
source navigation, and freshness checks. Archive sources are read-only. Their
archive paths are watched too. Binary Java constructors/lifecycle methods are
not reconstructed: existing uncertainty guards suppress misleading missing-Java
claims. Source-owned ordinary markup can still replace a dependency layout.
This uses the IDE's configured classpath, without executing application classes,
searching arbitrary archives, or downloading resources.

## Verification and demo

`WicketMarkupInheritanceTest` exercises fallback, replacement, Pages/Panels,
multiple levels, slot parentage, preview exclusion, cross-file uniqueness,
uncertainty guards, original offsets, read-only analysis, deterministic JSON, and
the demo. `WicketHierarchyViewTest` additionally opens inherited HTML destinations
and checks that ancestor edits and empty-extension edits invalidate snapshots.

The demo `InheritedLayoutPage` inserts its content into `BaseLayoutPage.html`.
Its fixture expects **3 MATCHED** findings (Label, Form, TextField). The base
declares no Java components; the bounded source pass verifies its empty setup.
Maven compilation checks both Java classes against Wicket 10.11.0.

The `HeaderDetailPage` demo adds a source superclass title, a contributed stylesheet,
and a body title. Both its default Java view and explicitly opened
`HeaderDetailPage_fr.html` expect **3 MATCHED** findings across two markup files.
The French view uses `HeaderBasePage_fr.html`. Fixtures additionally compile small
dependency JARs with the configured Java 25 compiler and check resource ambiguity,
read-only navigation, snapshot provenance, and production/test isolation. The
quick-fix walkthrough resets only fixture copies, preserving interactive demo edits.

The full suite passes **559 tests**, with 26 broader-markup/editor tests and nine
compiled-library resource tests. Plugin packaging, structure/configuration, and
binary verification pass with the preceding four deprecated and six experimental
API notices. All fifteen demo Java classes compile against the pinned Wicket.
Real application accuracy/performance evaluation remains deferred.
Graphical checks confirm three matches in both layouts, inherited header
Java/French-HTML navigation, and the final manual switch from a variant back to
Java/default markup. The running sandbox matches the verified distribution.
