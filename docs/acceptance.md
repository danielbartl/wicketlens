# WicketLens MVP acceptance record

Latest verification on **2026-10-30** for the local **0.1.0-SNAPSHOT** build. All nine
implementation slices are complete. This record closes the prototype's
implementation acceptance criteria; the real application feasibility gate remains
deferred by agreement.

## Verified environment

| Item | Version / scope |
| --- | --- |
| IntelliJ IDEA | 2026.2.3, build 262.10968.63 |
| Wicket fixture and demo libraries | 10.11.0, actual Maven artifacts |
| Gradle / IntelliJ Platform Gradle Plugin | 9.7.0 / 2.19.0 |
| Kotlin | Compiler 2.4.21; IDEA's bundled stdlib 2.4.0 |
| Build JDK | SDKMAN Java 25.0.4+1.1-librca |
| Analyzed roots | Java `WebPage`, `Panel`, and `FormComponentPanel` subclasses |

Only this IDE/Wicket combination has been verified. Other Wicket versions, IDE
products, and future IDEA builds are unverified. An absent upper build bound in
plugin metadata does not extend the evidence to those environments.

## Source constant IDs and conditions follow-up

The complete suite passes **1,095 tests**, with no failures, errors, or skipped
tests. The extension adds 54 constant-analysis/demo tests, 10 native editor tests,
and one explicit mutable-static-boolean guard. Two earlier static-constant
expectations now verify supported integral/boolean values. The affected
240-test run and complete suite both pass.

Coverage includes qualified names, static imports, interfaces, inherited lookup
dependencies, source constant expressions, narrowing/promotion/overflow,
initialization effects, invalid/inaccessible/ambiguous references, recursive/file/
expression bounds, whole-use problems, native Java declaration references,
shared editing guards, stale snapshots, and deterministic read-only exports.
The analyzed page's static initializers and instantiated/invoked source owners
remain audited; reading an otherwise unused external constant holder does not
initialize it.

All **35 demo Java files** compile against Wicket 10.11.0. `ConstantSetupPage`
has **5 MATCHED** bindings and records two Java source dependencies plus HTML.
See [the source-constant contract](static-constants.md).

Packaging, configuration/structure checks, and binary verification pass. The
final run reuses unchanged production packaging/verifier outputs; both the new
resolver and interpreter in the ZIP match current compiler output. The verdict
is Compatible with four deprecated and six experimental API notices. JSON schema
1 is unchanged. No new graphical sandbox verification or real application
accuracy/performance evaluation is claimed.

## Paired rename and constructor delegation review

The complete suite passes **1,030 tests**, with no failures, errors, or skipped
tests. Twelve cases were added: three native editor cases for inherited/shared
markup and safe own-companion rename, and nine constructor-tracing cases.
The original inherited-template and delegation-assignment failures reproduced
before the fixes; the focused 137-test run and complete suite then passed.

Paired rename checks the actual selected attribute resource against the analyzed
class's own conventional companion. A derived caller literal cannot rename an
inherited template shared with siblings. An own extension also cannot rename
base-template IDs, while its own eligible IDs retain paired rename.

Constructor tracing checks every delegation argument before propagating parameter
or fixed-literal origins. Nested/conditional assignments, updates, and unknown
calls cannot produce a confirmed incorrect ID. Tests also retain pure reordered
forwarding, parenthesized parameters, and reviewed repeater factory behavior.
See [source setup](source-setup.md) and [constructor coverage](constructor-coverage.md).

Packaging, configuration/structure checks, and binary verification pass. The
verdict is Compatible with four deprecated and six experimental API notices.
Demo sources are unchanged by this review. No new graphical sandbox or real
application evaluation is claimed.

## Hierarchy exploration follow-up

The complete suite passes **1,018 tests**, with no failures, errors, or skipped
tests. The extension adds four focus-projection tests, 11 logical state-key tests,
and 20 native view tests. Focus subtree and Show full hierarchy combine with
search/status filters. Next/Previous finding and F2/Shift+F2 reveal unpaired or
unresolved component rows in tree order, including collapsed branches, without
automatically opening an editor.

Expansion, selection, and focus survive reanalysis through unique raw ID/type/path
keys. Tests verify fresh Java/HTML source locations, collapsed branches, removed
or ambiguous targets, dynamic IDs, logical templates, page isolation, literal
labels, stale snapshot guards, and complete exports. Up to 16 page contexts are
remembered for the open view's lifetime; current search/status controls persist.

Packaging, configuration/structure checks, and binary verification pass. The
verdict is Compatible with four deprecated and six experimental API notices.
See [the hierarchy-exploration contract](hierarchy-exploration.md). Both hierarchy
slices are implemented. Real application evaluation remains deferred, and this
slice adds no graphical sandbox verification claim.

## Hierarchy filtering follow-up

The complete suite passes **983 tests**, with no failures, errors, or skipped
tests. The extension adds 13 filter-projection tests and 14 native view tests.
Persistent ID/type/path search combines with All, Unpaired, Java only, Markup
only, Unresolved, and Matched selection. Nonmatching ancestors remain muted for
context, and markup-only descendants use their matched container hierarchy.

Tests verify query/status composition, qualified types, paths, repeated IDs,
context retention, actual markup parents, empty results, selection/details/source
actions, expansion restoration when clearing filters, complete text/JSON exports,
reanalysis, page switches, stale navigation/copy guards, and cancellation. Controls
retain values for the open view's lifetime. Subtree focus, finding traversal, and
tree state across reanalysis are implemented in the later exploration follow-up
recorded above.

Packaging, configuration/structure checks, and binary verification pass. The
verdict remains Compatible with four deprecated and six experimental API notices.
See [the hierarchy-filtering contract](hierarchy-filtering.md). Analysis and JSON
schema 1 retain the full page; real application evaluation remains deferred.

## Reviewed scalar expressions follow-up

The complete suite passes **956 tests**, with no failures, errors, or skipped
tests. The extension adds 56 scalar-expression/demo cases and 11 editor cases.
One earlier unsupported-concatenation expectation now verifies a computed getter
ID. Coverage includes integral promotion, overflow, narrowing, comparisons,
String concatenation/content equality, immutable configuration, unknown and
nullable values, exception/effect guards, bounds, source freshness, and
deterministic read-only exports.

Computed IDs have whole-expression provenance and do not acquire fragment-based
rename/completion or literal-pair references. Java-only inspections anchor to the
whole computation in the inspected page file. Literal IDs used by comparisons
or other calculations also withhold independent rename/completion; ordinary
independent literals retain native editing. Temporary constant-folding PSI leaves
physical sources and current snapshot versions unchanged. The JSON schema is
unchanged; new provenance/editing flags remain snapshot metadata.

Packaging, configuration/structure checks, and binary verification pass. The
verdict remains Compatible with four deprecated and six experimental API notices.
All 33 demo Java files compile against Wicket 10.11.0. `ExpressionSetupPage` has
five matched bindings, including two computed IDs, tracking two Java files and
its HTML companion. See [the scalar-expression contract](scalar-expressions.md).
Real application accuracy/performance evaluation remains deferred.

## Proven conditional setup follow-up

The complete suite passes **889 tests**, with no failures, errors, or skipped
tests. The extension adds 57 conditional/demo cases, 12 editor cases, and one
fallback regression. Previously unsupported literal guards now have positive
coverage; uncertainty fixtures continue checking unknown guards. The suite covers
`if`/`else`, short circuiting, ternary origins, return propagation, private final
fields, factory freshness/accessor purity, Page/Panel roots, repeater boundaries,
budgets, deterministic read-only reports, native editing, and source freshness.

Standalone unexpanded root setup calls now invalidate direct root attachment
confidence even without a local initialization hook. Selected ternary literals
support native ID editing; proven immutable scalar field origins are recognized
for coloring while keeping the existing rename restrictions.

Packaging, configuration/structure checks, and binary verification pass. The
verdict remains Compatible with four deprecated and six experimental API notices.
All 31 demo Java files compile against Wicket 10.11.0. `ConditionalSetupPage` has
four matched bindings from provider flags, a selected factory return, and an
initialization guard, tracking four Java files and its HTML companion. See
[the conditional contract](conditional-setup.md). Real application evaluation
remains deferred.

## Read-only source accessors follow-up

The complete suite passes **819 tests**, with no failures, errors, or skipped
tests. The extension adds 45 accessor/demo cases and eight editor cases. Existing
cached-provider/known-parameter factory expectations now verify the separate
read-only accessor contract. Coverage includes provider identity, String/primitive
getters, nested/default/generic dispatch, immutable origins, private final page
fields, initialization, purity and type guards, unknown injection, opaque repeater
model data, native editing/inspections, source freshness, and read-only exports.

Packaging, configuration/structure checks, and binary verification pass. The
verdict remains Compatible with four deprecated and six experimental API notices.
All 29 demo Java files compile against Wicket 10.11.0. `ProviderAccessorPage` has
four matched bindings through page/provider/scalar accessors, tracking four Java
files and its HTML companion. See [the accessor contract](source-accessors.md).
Real application accuracy/performance evaluation remains deferred.

## Source provider factories follow-up

The complete suite passes **766 tests**, with no failures, errors, or skipped
tests. The extension adds 29 provider-factory/demo tests and eight editor tests.
Factories can return fully constructed providers allocated in that invocation or
a nested factory, retaining concrete types, immutable configuration, and ID
origins. Tests cover static/instance/default dispatch, generic substitution,
allocation separation, constructor composition, return compatibility, cached/
parameter/null rejection, unknown injection, initialization, recursion, native
editing/inspections, freshness, and deterministic read-only reports.

Packaging, configuration/structure checks, and binary verification pass. The
verdict remains Compatible with four deprecated and six experimental API notices.
All 27 demo Java files compile against Wicket 10.11.0. `ProviderFactoryPage` has
five matched bindings from static/nested factories and a known instance builder,
with five consulted Java files tracked. See
[the provider-factory contract](provider-factories.md) for exact bounds. Real
application evaluation remains deferred.

The isolated sandbox was refreshed after confirming its sole demo project had no
unsaved documents. Its loaded plugin JAR matches the verified distribution. The
native tree confirms all five bindings, with distinct provider IDs and the
TextField under its Form; all five Java dependencies and the HTML companion are
current. Temporary developer Swing checks made no source edits and are not distributed.

## Source interface defaults follow-up

The complete suite passes **729 tests**, with no failures, errors, or skipped
tests. The extension adds 33 default-method/demo cases and nine editor cases;
one previous unsupported-default expectation now verifies the reviewed subset.
Class precedence, inherited implementations, most-specific defaults, diamonds,
abstract suppression, explicit interface-super calls, private/static interface
helpers, generic signatures, initialization guards, unknown receivers, read-only
exports, native editing, and source freshness are covered.

Fixtures now explicitly set Java 25 source language level in addition to their
Java 25 SDK. This corrects the previous implicit Java 6 language level and enables
real qualified interface-super resolution; all existing tests pass with the correction.
Packaging, configuration/structure checks, and binary verification pass, retaining
the Compatible verdict and four deprecated/six experimental API notices. All
25 demo Java files compile against Wicket 10.11.0. `DefaultMethodsPage` has six
matched bindings from provider, inherited-page, and explicit interface defaults,
with four Java dependencies. See [the default-method contract](interface-defaults.md).
Real application evaluation remains deferred.

The isolated sandbox was refreshed after confirming its sole demo project had no
unsaved documents. Its loaded plugin JAR matches the verified distribution. The
native tree shows all six bindings with a separate `name` Label under each Form;
all four Java sources and the HTML companion are current. Temporary developer
Swing checks made no source edits and are not distributed.

## Immutable provider-state follow-up

The complete suite passes **687 tests**, with no failures, errors, or skipped
tests. The extension adds 33 provider-state/demo tests and eight editor tests.
Known providers retain separate private final configuration and known dependencies
supplied through constructors. Constructor ordering/delegation, inherited/shadowed fields,
ID provenance, unknown scalar IDs, and conservative state/escape/effect guards are
covered, including implicit recursive construction and unfinished-receiver escape.
One previous stateless-only expectation now checks that mutable state remains
unresolved. Native editing, coloring recognition, problem ranges, shared-origin
restrictions, and source/configuration freshness are verified too.

Packaging, configuration/structure checks, and binary verification pass. The
verdict remains Compatible with four deprecated and six experimental API notices.
All 22 demo Java files compile against Wicket 10.11.0. `ConfiguredPartsPage` has
four matched bindings from independent configured providers and a delegated Form
factory, with all four consulted Java files tracked. See
[the provider-state contract](provider-state.md) for exact limits. Real application
evaluation remains deferred.

The native sandbox confirms four matched bindings for `ConfiguredPartsPage`,
including distinct heading IDs and the TextField under the delegated Form.
All four Java dependencies and the HTML source are current. Its loaded plugin
JAR matches the verified distribution. Temporary developer Swing checks made no
source edits and are not distributed.

## Proven source dispatch follow-up

The complete suite passes **646 tests**, with no failures, errors, or skipped
tests. The extension adds 29 dispatch/demo tests and eight editor tests. Final
pages/final overrides establish closed root targets; fresh stateless helper
allocations establish instance targets through source interfaces. Tests cover
overload/override distinctions, constructor ordering, explicit superclass calls,
generic bridge checks, conservative fallbacks, shared budgets, deterministic
exports, native navigation/rename/completion/inspections, dependency freshness,
and reanalysis after switching an implementation.

Packaging, configuration/structure checks, and binary verification pass. The
verdict remains Compatible with four deprecated and six experimental API notices.
All 20 demo Java files compile against Wicket 10.11.0. `DispatchPage` has four
matched bindings spanning its final-page override and interface factories; all
four consulted Java files are snapshot dependencies. See
[the dispatch contract](source-dispatch.md) for the supported subset. Real
application evaluation remains deferred.

The graphical sandbox confirms four matched bindings for `DispatchPage`, with
the TextField under its factory-built Form and the final-page override present
instead of the overridden base addition. All four Java dependencies and the HTML
source are current. The loaded JAR matches the verified distribution; temporary
developer Swing checks made no source edits and are not distributed.

## Queue/repeater follow-up

The complete suite passes **609 tests**, with no failures, errors, or skipped
tests. The new coverage comprises 12 queue tests, 25 repeater/demo tests,
12 editor integration tests, and one initialization test. Two earlier uncertainty
expectations now distinguish supported queueing from destructive tree mutations.

Queue placement follows unique eligible markup paths and preserves queue-scope
provenance. ListView and RepeatingView expose logical item groups whose child
matches have inferred confidence. Unsupported population, custom item factories,
captured-container mutations, and ambiguous placements retain uncertainty.
Navigation, rename, completion, Problems integration, quick-fix availability,
freshness, hierarchy presentation, and additive export metadata are covered.
See [the queue/repeater contract](queueing-repeaters.md) for exact bounds.

Packaging, configuration/structure validation, and binary verification pass.
The verdict remains Compatible with four deprecated and six experimental API
notices. All 16 demo classes compile against Wicket 10.11.0. The new
`QueueRepeaterPage` fixture has six matched bindings, two inferred template-child
matches, and two logical item groups. No runtime IDs or item counts are asserted.
The real application evaluation remains deferred.

The isolated sandbox was refreshed after confirming that its sole demo project
had no unsaved documents. Its loaded plugin JAR matches the verified distribution.
The native tree shows the queued container's child and two expanded logical item
groups, with six matched bindings. Go to HTML opens the template child's companion;
a repeated check retains the same page context and current source/PSI stamps.
Temporary developer Swing checks made no source edits and are not distributed.

## Specification acceptance criteria

| Criterion from [spec.md](../spec.md#10-acceptance-criteria) | Result | Evidence |
| --- | --- | --- |
| Build succeeds | Pass | `buildPlugin`; ZIP at `build/distributions/wicketlens-0.1.0-SNAPSHOT.zip`. |
| Sandbox IDE launches | Pass | Slice 8 graphical launch and demo import; the same installed plugin JAR was confirmed identical to the packaged JAR during slice 9. |
| Static Java patterns are recognized | Pass | `WicketAcceptanceTest` simple, variable, and nested Form cases; Java discovery/hierarchy regression suites. |
| Nested HTML Wicket IDs are found | Pass | Acceptance Form case retains both the ordinary `div` parent and the nearest Wicket parent. HTML/XML extraction suite covers Panels and preview markup. |
| Matching is parent-aware | Pass | Acceptance repeated-ID case reverses markup source order and confirms both `name` nodes match under their own parents; matcher suite covers wrong-parent and duplicate-path cases. |
| Dynamic cases do not produce definitive false errors | Pass within tested scope | Acceptance dynamic-ID case exports unresolved findings and causes with `INFO` severity; uncertainty suite covers unsupported setup and syntax recovery. |
| Analyze Wicket Page is available in the IDE | Pass | Action registration fixture and graphical demo report in the WicketLens tool window. |
| JSON context can be copied | Pass | Registered copy action / clipboard fixture and sandbox clipboard JSON inspection. |
| Eight fixture scenarios are tested | Pass | Eight dedicated full-pipeline acceptance tests, mapped below. |
| README documents run commands and limitations | Pass | Introduction and installation in [README.md](../README.md); sandbox, demo, JSON, troubleshooting, supported subset, and compatibility limits in the [developer guide](developer-guide.md). |

## Eight full-pipeline scenarios

Each test in [WicketAcceptanceTest](../src/test/kotlin/dev/wicketlens/WicketAcceptanceTest.kt)
creates physical Java and companion HTML PSI files under configured module roots,
with actual Wicket libraries attached to the fixture project. It runs the same
source selection and composed analysis used by the background service, formats
the report, and parses the exported JSON. The separate service suite tests
background execution, panel controls, supersession, cancellation, and copying.

| Specification scenario / test method | Acceptance observation |
| --- | --- |
| `SimplePage` / `testSimplePage` | Literal Label ID, concrete type, page parent, `MATCHED`, Java and markup locations. |
| `NestedFormPage` / `testNestedFormPage` | Form → TextField relationship in Java and JSON; ordinary HTML wrapper preserved without changing Wicket parentage. |
| `VariablePage` / `testVariablePage` | One Label construction represented once and matched after `add(variable)`. |
| `MissingMarkupIdPage` / `testMissingMarkupIdPage` | `JAVA_ONLY` with inferred matching confidence and null counterpart references. |
| `MarkupOnlyPage` / `testMarkupOnlyPage` | `MARKUP_ONLY` with a markup source location and null Java counterpart. |
| `DynamicIdPage` / `testDynamicIdPage` | `UNRESOLVED_DYNAMIC_ID` plus unresolved markup counterpart, with causes; no unmatched or positive match claim. |
| `NonWicketAddPage` / `testNonWicketAddPage` | Collection `add` does not discover a Wicket child or create a Java match. |
| `RepeatedIdsPage` / `testRepeatedIdsPage` | Four matches, with the two `name` IDs retaining separate parent and markup identities despite reversed source order. |

Every scenario additionally checks informational severity, partial-scope metadata,
valid Java offsets, selected companion path, matched markup offsets, unchanged
Java/HTML text and modification stamps, and identical JSON across two analyses
of the same sources. The exported finding counts must agree with the expected
scenario, independently of the text formatter.

## Final verification results

After the class-level HTML navigation follow-up, the complete suite passed:
**357 tests, 0 failures, 0 errors, 0 skipped**.

| Suite | Tests |
| --- | ---: |
| Plugin foundation | 3 |
| Full-pipeline acceptance | 8 |
| Java discovery | 14 |
| Java hierarchy | 22 |
| File pairing and composed reports | 32 |
| Cross-module pairing isolation | 1 |
| HTML/XML extraction | 28 |
| Parent-aware matching | 30 |
| Uncertainty handling | 30 |
| Presentation and JSON export | 8 |
| Caret/file selection | 4 |
| Background service, panel, and clipboard | 10 |
| Source navigation, stale reports, and re-analysis | 23 |
| Standard component constructors and demo | 20 |
| Initialization scopes and demo | 30 |
| Markup inheritance and demo | 16 |
| Expandable hierarchy, native sidebar, and source navigation | 19 |
| Component fields and demo | 35 |
| Editor Java/HTML target resolution | 9 |
| Editor following and pinning | 15 |

The final commands completed successfully using Java 25. To reproduce them,
set `JAVA_HOME` to a Java 25 JDK and run:

```bash
./gradlew "-Dorg.gradle.java.home=$JAVA_HOME" \
  test buildPlugin verifyPluginProjectConfiguration verifyPluginStructure verifyPlugin
mvn -f examples/demo/pom.xml -B -ntp compile
```

Slice 9 originally passed 178 tests and reused unchanged build/verifier outputs.
The core review follow-up changed production analysis, added 12 regression/control
tests, re-ran the complete suite, rebuilt the plugin, and ran binary verification
again. The source-navigation follow-up adds 19 further tests and repeats the full
suite, packaging, and verification for the rebuilt plugin. The standard-component
slice adds 20 tests covering 33 reviewed public overloads and repeats these checks;
the expanded Maven demo also compiles successfully. The initialization slice adds
30 tests and again passes the full suite, packaging, configuration/structure
checks, binary verification, and Maven demo compilation. The inheritance and
hierarchy slices add 29 tests and again pass those checks for the rebuilt plugin
and expanded five-class demo. The field slice adds 35 tests and repeats the full
suite, packaging, configuration/structure checks, binary verification, and Maven
compilation for the six-class demo. The native-sidebar and editor-following slices
add 29 tests, pass packaging/configuration/structure checks and binary verification,
and finish with a complete 352-test regression run. The class-navigation fix adds
five tests and repeats packaging/configuration/structure and binary verification
with all 357 tests passing. The demo Java sources are
unchanged by these UI slices. The rebuilt binary verdict is
**Compatible** with IDEA 262.10968.63, with **4 deprecated** and **6 experimental**
API usages in generated forwarding methods for IntelliJ's `ToolWindowFactory`
defaults. These warnings remain recorded; they are not compatibility errors.

The distribution contains only the WicketLens plugin JAR, with no Wicket fixture
libraries or extra Kotlin runtime bundled. At slice 9 acceptance, the JAR inside
the ZIP and the one loaded by the sandbox had the same SHA-256 digest. The core
review follow-up produced a new plugin JAR, followed by the source-navigation
build. Graphical verification of source navigation is recorded below. The later
constructor-coverage and initialization builds were verified through PSI fixtures,
Maven compilation, and the binary verifier; those slices did not relaunch the
sandbox UI.

In the graphical demo, the report shows **3 MATCHED**, **1 JAVA_ONLY**, and
**1 MARKUP_ONLY**. Clipboard inspection after invoking **Copy Wicket Context**
confirmed valid JSON for `example.CustomerPage`, the expected finding counts,
`form` → `name`, and matched HTML source locations. Slice 8 also checked the
visible source locations, background report flow, and status row in a narrow
window. The demo is a controlled fixture rather than a real application study.

## Core review follow-up

All three review findings are addressed:

- The background read action now combines `inSmartMode` and
  `withDocumentsCommitted` constraints. Tests disable automatic background
  commits, edit Java, HTML, or both, and verify that no report is published until
  the pending documents are committed. The completed JSON contains the edited
  IDs and current Java source offsets. A further test cancels a request while
  it waits and checks that committing later cannot publish a copyable report.
- Before pruning nested/anonymous class bodies from component discovery, a
  separate PSI scan detects access to the enclosing root. This includes qualified
  `ExamplePage.this`, implicit root member calls, and escaped root references.
  Affected constructor attachments become unresolved, with source-located
  `UNRESOLVED_SCOPE` causes; nested components are not invented.
- Method references bound to the analyzed root, including `this::add`,
  `ExamplePage.this::add`, and `super::removeAll`, are recorded as unexpanded
  scope effects. They cannot silently turn into markup-only claims or preserve
  confirmed matches in the affected constructor. Control cases keep other
  objects' methods and nested classes' own receivers separate from the page.

The tests were first run against the previous implementation: three stale-PSI
tests and six scope-effect tests failed, reproducing the review. After the fixes,
the focused suites and the complete regression suite passed. No dependencies or
new user-facing features were introduced. The graphical smoke results above
remain the slice 8/9 baseline; these fixes were verified through real PSI/service
fixtures and the rebuilt plugin verifier.

## Source-navigation follow-up

The report now maps component rows and their source lines to value-only Java and
markup destinations. **Go to Java**, **Go to HTML**, double-click, and **Enter**
open observed sources at their snapshot offsets. Root, matched, Java-only,
markup-only, and unresolved rows offer only their available destinations.
Repeated IDs under different parents retain separate links. JSON schema and
deterministic export remain unchanged; navigation/version metadata is not exported.

Source document stamps, file identity, project PSI and root versions, and relevant
editor/VFS events detect stale snapshots. Navigation and copying validate freshness
again when invoked. Stale reports stay visible with an out-of-date status and
disabled navigation/copy controls. Editor opening runs on the public EDT coroutine
API without holding a read lock across editor creation. The source offset is also
guarded when the IDE requests it, since opening an editor may process queued events.

**Re-analyze** uses the original Java source and qualified class identity. It
works while HTML is active and after source offsets shift. A renamed or removed
class requires a new selection through **Analyze Wicket Page**. Analysis is still
manual and waits for indexing and committed documents.

The 19 navigation fixtures exercise real editor opening/caret offsets, available
counterparts, repeated IDs, Enter navigation, uncommitted Java/HTML edits, external
VFS content changes, rename/deletion, newly created companions, project root and
other source changes, stale-state persistence, saving unchanged analyzed text,
re-analysis, and cancellation of obsolete queued navigation. Existing core-review
and acceptance fixtures remain in the complete regression run.

The final graphical sandbox was exercised through its real Swing controls with a
temporary developer UI harness. The registered Analyze action produced the demo
report. Selecting `title` and using **Go to HTML** opened `CustomerPage.html` at
the `h1` tag (offset 74); **Go to Java** opened `CustomerPage.java` at the Label
construction (offset 381). A temporary leading newline immediately disabled
navigation and copying and showed the out-of-date status. Restoring the original
text kept the report stale; **Re-analyze** restored a fresh report and working
navigation/copy controls. The demo source was restored, and the installed sandbox
JAR was confirmed identical to the verified distribution JAR. The temporary
harness is not part of the plugin or distribution.

The edit/undo flow was also repeated through normal keyboard input, with no new
IDE errors. Direct document edits from the scratch harness had emitted a
transaction-context diagnostic; that diagnostic came from the harness, not the
plugin's navigation path. The final sandbox was restarted without the harness.

## Standard-component coverage follow-up

Ten exact Wicket classes were added to the reviewed constructor set: `Button`,
`SubmitLink`, `Link`, `BookmarkablePageLink`, `ExternalLink`, `AjaxLink`,
`AjaxButton`, `CheckBox`, `DropDownChoice`, and `FeedbackPanel`. There are now
fifteen reviewed classes in total. The [constructor audit](constructor-coverage.md)
records the pinned source artifact, ID forwarding, all 33 additional public
overloads, and the retained limits.

Overload resolution must be valid before ID semantics can be confirmed. Anonymous
instances report the declared base class name, consistently with ordinary
instances, so generic links have correct component labels and exported types.
User subclasses, same-name classes, dynamic/invalid IDs, ambiguous/inapplicable
calls, callback scope effects, and unexpanded framework children remain
conservative. No runtime/callback or lifecycle execution is inferred.

Twenty new fixture tests cover the overloads, discovery through matching and
export, parent-aware Form nesting, repeated IDs, Panel roots, anonymous callbacks,
uncertainty controls, read-only/deterministic behavior, and the new demo sources.
`StandardControlsPage` compiles against Wicket 10.11.0 and produces **12 MATCHED**
findings in its full-pipeline fixture, with the existing partial-scope warnings.
Demo sources are declared test inputs so fixture edits invalidate cached results.
No dependencies or runtime libraries were added to the plugin.

## Initialization coverage follow-up

Actual `Component.onInitialize()` overrides declared on the analyzed Page or
Panel now support straight-line additions and directly initialized method-local
components/containers. The method must begin with one valid, reviewed
`super.onInitialize()` delegation. PSI override resolution keeps overloads and
other lifecycle hooks outside this phase. The
[initialization contract](initialization-coverage.md) records the reviewed Wicket
10.11.0 implementations, including `MarkupContainer`'s dequeue handling.

Constructor and hook observations share the graph while locals retain separate
declaration identities. Validation precedes discovery: unsupported hook effects
invalidate earlier constructor attachments, and uncertain constructor execution
also invalidates hook attachments. Conditional setup, mutations, root helpers,
manual/repeated initialization, root escapes, nested effects, custom inherited
setup, fields, aliases, factories, and duplicate paths retain uncertainty. Known
local container edges survive with unresolved confidence. Reports stay partial
and informational, with unchanged JSON schema and value-only snapshots.

Thirty real-PSI tests cover the supported setup, uncertainty boundaries, read-only
behavior, deterministic JSON, source locations, and a new demo pair.
`InitializationPage` produces **6 MATCHED** findings: a constructor Label, an
initialization Form with three children, and a hook-local Label. The demo compiles
with Maven against the pinned Wicket libraries. This backend slice does not add
UI controls or claim new graphical smoke-test results; restart the sandbox with
the rebuilt plugin to exercise the new fixture interactively.

## Markup inheritance and expandable hierarchy follow-ups

Conventional source-based Page and Panel markup now supports ancestor fallback,
replacement by an own ordinary companion, and one `wicket:extend` section inserted
into one empty active `wicket:child` slot per level. Merged paths preserve original
source locations and distinct identities across files; parentage, duplicate IDs,
and uncertainty are recomputed. Invalid or unavailable chains remain unresolved.
The [inheritance contract](markup-inheritance.md) records the source review and
boundaries. Inherited Java component setup, header merging, dependency resources,
custom locators, and resource variants remain unexpanded.

The default **Hierarchy** tab contains expandable component branches, unattached
components, markup findings, and diagnostics. A detail pane shows type, status,
confidence, parent, sources, and causes. Expand/Collapse, Java/HTML buttons,
double-click, and Enter support exploration and navigation. The **Text report**
tab remains available. Prepared hierarchy entries are immutable values built in
the background; Swing models stay on the EDT. Labels/details are plain text.
Stale trees remain readable while navigation/copying is blocked; re-analysis
rebuilds the tree without carrying old selection offsets into the new report.

Sixteen inheritance fixtures and thirteen hierarchy/service fixtures cover these
behaviors, including ancestor edits, empty-extension dependencies, original
navigation offsets, repeated IDs, literal labels, and cancellation. Existing
navigation, service, initialization, acceptance, and uncertainty suites remain in
the complete 288-test run. JSON schema stays at version 1, and the distribution
continues to contain only the plugin JAR.

The rebuilt sandbox was launched with `InheritedLayoutPage`. A temporary developer
Swing harness invoked the registered Analyze action and the real panel controls.
The report showed **3 MATCHED** findings and both contributing HTML source files.
Expand all displayed seven rows; Collapse all reduced the view to four root-level
rows. Selecting `name : TextField` displayed `CONFIRMED`, parent `form`, and both
source locations. **Go to Java** opened `InheritedLayoutPage.java` at offset 444;
**Go to HTML** opened `InheritedLayoutPage.html` at offset 225. Visual inspection
confirmed the hierarchy, selected row, details pane, and tabs in the narrow IDE
window. The harness is temporary, performs no source edits, and is not distributed.
Freshness/edit/re-analysis coverage for the new tree comes from the PSI/service
fixtures rather than new graphical source-edit checks. This is controlled demo
verification; real application evaluation remains deferred.

## Field-tracking follow-up

Private final instance fields declared on the analyzed class can now bind to
direct `new` initializers at declaration or in one supported constructor.
Constructor and `onInitialize()` reads share the original construction node,
preserving concrete type, literal ID, source offset, and container parentage.
Declaration resolution keeps fields separate from shadowing locals and foreign
instances. Field declarations after constructor methods and hooks before
constructor methods retain the correct phase interpretation.

All field uses in the source file are checked. Unsupported visibility/mutability,
static or inherited fields, extra writes, alternative/delegating constructors,
aliases, getters/helpers, setters, escapes, casts, captures, method references,
and unavailable/conditional initialization remain unresolved. Field attachments
also retain uncertainty for opaque root effects, field-initializer root captures,
instance initializer blocks, constructor preambles, and the existing lifecycle
guards. Known construction observations and local edges survive when only their
attachment scope is uncertain. The [field contract](field-tracking.md) documents
the precise boundary and Java initialization reference.

Thirty-five new PSI fixtures cover those cases, Page/Panel roots, source/phase
ordering, locals, repeated IDs, receiver-only containers, multiple additions,
unchanged constructor/ID semantics, tree/JSON identity, read-only analysis, and
deterministic exports. They were first run against the earlier implementation:
16 of the initial 30 tests failed, reproducing absent field bindings. The final
focused runs and the complete 323-test suite pass. No dependencies, persistent
indexes, UI controls, or JSON schema changes were added.

`FieldComponentsPage` uses both inline fields and a constructor-assigned field,
attaches a Form during construction, then adds three Form children and a status
Label in `onInitialize()`. Its full-pipeline fixture produces **6 MATCHED**
findings and one shared Form node. All six demo Java classes compile with Maven
against Wicket 10.11.0. This backend slice was verified through PSI fixtures,
packaging, and binary verification; it adds no new graphical smoke-test claim.
Relaunch through Gradle `runIde` to prepare the updated sandbox plugin. The
earlier hierarchy sandbox evidence above remains the graphical baseline.

## Native sidebar and editor-following follow-ups

WicketLens now defaults to a left-hand tool window with native platform toolbar
actions, semantic class/field/tag/information icons, muted type/status fragments,
type-ahead search through collapsed branches, a native splitter, and optional
component details. The text report remains available. Source labels are literal
text, and value-only row metadata drives rendering without parsing report strings.
Existing saved window placement is respected. The
[sidebar contract](sidebar-presentation.md) records behavior and platform references.

Follow editor is enabled by default once the view is opened and remembered per
project. Switching Wicket Java files or uniquely paired HTML companions replaces
the report; unrelated files and closing editors clear it. Disabling following
pins the current snapshot, and enabling it resumes on the active file. Rapid
switches cancel obsolete work. Mapping HTML back to Java uses module-scoped
filename lookup verified through the existing forward pairing contract, including
package and production/test isolation. WicketLens source navigation preserves the
current page context and avoids follow/re-analysis loops for inherited markup.
Edits alone retain stale-state guards until re-analysis. No automatic analysis
runs before the sidebar has been opened or after its view has been disposed.

Six native UI tests, nine editor-target fixtures, and fourteen editor-following
fixtures join the existing suite. They cover native action presentations/icons,
literal rendered fragments, details, collapsed-branch search, text-view actions,
stale guards, startup activation, Java/HTML/Page/Panel targets, pinning, ambiguous
or unrelated sources, closing editors, pending commits/rapid switches, cancellation,
disposal, explicit class selection, and inherited-source navigation. The existing
manual-snapshot navigation/service tests run with following disabled. All 352
tests pass, with unchanged JSON schema and compatibility warnings.

The development IDE was restarted after confirming it held only the demo project
and no unsaved documents. Its saved bottom placement was moved to the left using
the public tool-window API. Graphical inspection confirmed the new sidebar styling
beside the built-in Structure view. Native Expand/Collapse and details controls
worked; selecting the field-backed Form opened Java at offset 501 and HTML at
offset 114. Native speed search revealed the collapsed `name` child.

After the editor-following fix, the sandbox was reloaded again. Merely showing the
sidebar, without invoking Analyze, produced `FieldComponentsPage` with six matches.
Switching the actual editor to `CustomerPage.java` changed the report to
`example.CustomerPage`. Selecting `FieldComponentsPage.html` changed it back to
`example.FieldComponentsPage` through reverse companion mapping. Turning following
off pinned that report while CustomerPage was active; turning it on resumed
CustomerPage automatically. Temporary developer Swing harnesses exercised real
toolbar controls and public editor APIs under the IDE's write-intent context.
They made no source edits and are not distributed. The sandbox remains running
with Follow editor enabled. Real application evaluation remains deferred.

## Class-level HTML navigation follow-up

The class row now links to the beginning of the resolved companion file in both
the hierarchy and text report. It uses the selected markup path and captured
source dependencies, rather than guessing a filename or requiring a component
match. Derived markup opens its own extension file; inherited fallback markup
opens the resolved ancestor file. Missing or ambiguous pairing remains disabled.
Uncertain markup content still permits navigating to a known file, while stale
reports retain the existing navigation block. The exported root component is
unchanged and is not given an invented markup match.

Four navigation tests and one following test were added, with existing root-row
expectations updated. Cases include Page/Panel roots, tree/text actions, Java-only
children, derived/fallback companions, missing/ambiguous lookup, malformed markup,
staleness, and preservation of the followed snapshot. All 357 tests pass, along
with packaging, configuration/structure checks, and binary verification. The
compatibility verdict remains Compatible with the recorded API warnings.

After checking that the demo had no unsaved documents, the sandbox was reloaded
while restoring its existing editor tabs. Selecting the `CustomerPage` class row
enabled the real Go to HTML toolbar action. Invoking it opened `CustomerPage.html`
at offset 0, retained `example.CustomerPage` as the report, and kept Follow editor
enabled. This temporary graphical check made no source edits.

Generated evidence remains under `build/` and can be regenerated:

- Test results: `build/test-results/test/`; HTML report: `build/reports/tests/test/index.html`.
- Binary verdict: `build/reports/pluginVerifier/IU-262.10968.63/plugins/dev.wicketlens/0.1.0-SNAPSHOT/verification-verdict.txt`.
- Verifier detail: `build/reports/pluginVerifier/IU-262.10968.63/report.html`.
- Sandbox log: `build/idea-sandbox/wicketlens/IU-2026.2.3/log_runIde/idea.log`.

## Boundaries of acceptance

The implementation reconstructs the explicitly supported static subset. All
reports remain partial, unmatched findings are informational, and unsupported IDs,
constructors, scope, parentage, or markup retain uncertainty. The detailed
[supported patterns and limitations](developer-guide.md#java-discovery) are part of
this acceptance boundary.

Analysis uses PSI and configured module roots, runs in a cancellable read action
constrained to smart mode and committed documents through a project-scoped
service, and retains value snapshots. Formatting
and JSON preparation run in the background; UI updates and clipboard access run
on the EDT. Analysis performs no source writes. Subsequent editor features add
explicit native paired-ID rename and completion insertion; these are user-driven
source edits with standard editor/refactoring undo. There are no plugin-initiated
network requests, runtime instrumentation, persistent project index, or AI integration.

Reports require re-running after edits. Native editor inspections refresh
independently, and matched IDs support navigation, rename, and completion; see
[the editing contract](developer-guide.md#editing-wicket-ids). Source setup now also
supports closed helpers/factories, source superclass phases, and forwarding-only
custom constructors, with additional source-version and shared-rename guards;
see [the source contract](source-setup.md). The extended slice also supports
custom input ID contracts, isolated composite setup, and separate
`FormComponentPanel` markup; see [the input contract](form-components.md).
Bounded inspection quick fixes now support compatible ID corrections and basic
Java/HTML counterpart creation with native preview and undo; see
[the quick-fix contract](inspection-quick-fixes.md). Supported static headers,
explicit HTML variants, and read-only dependency layouts are also implemented;
see [the markup contract](markup-inheritance.md). Bounded component queueing and
ListView/RepeatingView templates are also supported; see
[their contract](queueing-repeaters.md). Proven final-page/final-method dispatch
and known instance helpers are also supported, including source
interface calls; see [the dispatch contract](source-dispatch.md).
Providers may now retain private final configuration and known constructor-injected
dependencies, with independent allocation state and traced ID origins; see
[the provider-state contract](provider-state.md).
Source interface defaults, private interface helpers, and explicit interface-super
calls now have bounded support with class/most-specific precedence and initialization
guards; see [the default-method contract](interface-defaults.md).
Closed source provider factories now preserve fresh known allocations and
configuration across nested/generic calls; see [their contract](provider-factories.md).
Custom resource contexts, arbitrary helper/factory
analysis, unsupported fields/aliases, unreviewed queue/repeater patterns, and runtime reconstruction remain
outside the supported subset.

The feasibility gate in [spec.md §11](../spec.md#11-feasibility-gate) is **not yet
evaluated**: no 5–10-page real application study, 90% reconstruction measurement,
or representative performance assessment has been performed. Passing fixture
acceptance does not establish those results. The deferred evaluation and any
additional features are separate follow-up decisions.
