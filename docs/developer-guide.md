# WicketLens development and analysis reference

For a quick introduction and installation, see the [project README](../README.md).

An IntelliJ IDEA plugin feasibility prototype for reconstructing Apache Wicket
component hierarchies through Java and HTML PSI. See [spec.md](../spec.md) for the
full prototype scope.

## Current scope: MVP complete (slices 1–9)

The Kotlin plugin includes conservative Java component discovery, companion-file
pairing, HTML/XML PSI extraction, and parent-aware Java/markup matching. Reports
contain matched, informational unmatched, and unresolved findings with source
locations and explicit uncertainty causes. Unsupported setup and recovered syntax
are reported conservatively. Tests use real Wicket libraries and configured module
roots. **Analyze Wicket Page** runs the analysis in the background and displays a
hierarchy, matching statuses, source locations, and diagnostics in the WicketLens
tool window. **Copy Wicket Context** exports the completed snapshot as JSON.
The first follow-up slice adds Java/HTML source navigation, stale-report detection,
and a **Re-analyze** control.
The next coverage slice adds ten standard component types; the complete reviewed
set now contains nineteen classes, including the later ListView/RepeatingView
contracts. See [the constructor audit](constructor-coverage.md).
The initialization slice also recognizes conservative `onInitialize()` setup on
the analyzed Page or Panel and combines it with constructor observations.
Further slices add conventional Page/Panel markup inheritance and an expandable
hierarchy with component details, diagnostics, and source navigation.
Private final component fields can now connect constructor and initialization
setup within the reviewed subset. See [the field contract](field-tracking.md).
The tool window uses a native IntelliJ sidebar layout, compact icon toolbar,
themed tree icons, type-ahead search, and optional component details.
**Follow editor** is enabled by default: an opened sidebar follows active Wicket
Java files and uniquely paired HTML companions. Turn it off to pin a snapshot.
Native inspections now show possible unpaired IDs as configurable weak warnings
in Java and HTML, with editor gutter icons and Problems integration. They refresh
after edits independently of the sidebar snapshot.
Native inspection quick fixes can correct compatible unpaired IDs or create a
basic Java/HTML counterpart. They use normal diff preview and undo; see
[the quick-fix contract](inspection-quick-fixes.md).
Markup inheritance also merges supported header contributions, follows the HTML
variant you open, and reads conventional layouts from compiled dependency ancestors.
See [the markup contract](markup-inheritance.md) for context and fallback limits.
Component queueing now follows unique eligible markup placement. ListView and
RepeatingView show logical item templates with inferred child bindings, without
inventing runtime IDs or row counts. See [the queue/repeater contract](queueing-repeaters.md).
Editor references link matched Java/HTML IDs directly. Native Rename previews a
paired-ID change, and completion suggests unused IDs under the corresponding
parent. Wicket IDs use configurable Wicket-orange coloring in both editors.
Bounded source analysis also expands closed helpers/factories, source superclass
constructor/initialization setup, and custom constructors with provable ID
forwarding. See [the source setup contract](source-setup.md).
Proven source dispatch also follows final-page/final-method overrides and known
provider factories, including calls through interfaces. Providers can retain
bounded immutable configuration and constructor-injected known dependencies. See
[the dispatch contract](source-dispatch.md).
Source interface defaults now participate in proven dispatch, including inherited
defaults, class precedence, private helpers, and explicit interface-super calls.
See [the default-method contract](interface-defaults.md).
Closed source factories now preserve known providers and their configuration,
including static/instance builders and nested generic factories. See
[the provider-factory contract](provider-factories.md).
Read-only source accessors also preserve existing known providers and simple
String/primitive getter values, including literal ID origins. See
[the accessor contract](source-accessors.md).
Proven primitive boolean conditions now select `if`/`else` and ternary paths,
including short circuiting and guard returns. Unknown branches retain uncertainty.
See [the conditional setup contract](conditional-setup.md).
Reviewed integral arithmetic/comparisons and String construction/content equality
now resolve more conditions and computed IDs. Composite IDs retain whole-expression
provenance and conservative editing guards. See
[the scalar-expression contract](scalar-expressions.md).
Reviewed source `static final` constants now supply IDs, immutable configuration,
and conditions through qualified names, static imports, and inherited declarations.
Shared constants retain whole-use provenance and conservative editing guards;
see [the source-constant contract](static-constants.md).
Custom `FormComponent` inputs and markup-owning `FormComponentPanel` composites
are supported within a bounded constructor/conversion subset. Parent and
composite markup remain separate; see [the input contract](form-components.md).
The prototype implementation and acceptance checks are complete. See
[the acceptance record](acceptance.md) for the specification checklist,
fixture evidence, and verified compatibility limits. Real application evaluation
remains deferred; the 90% reconstruction target has not been measured.

## Java discovery

`WicketJavaAnalyzer.analyze(PsiClass)` returns a `PageReport` for classes extending
Wicket `WebPage`, `Panel`, or `FormComponentPanel`, including indirect subclasses. Other classes return
`null`. Call the analyzer under a smart read action, off the UI thread for real
projects. Discovery is read-only, checks cancellation, and returns value objects
without retaining PSI or project references.

Supported examples in a constructor:

```java
add(new Label("title", "Hello"));
this.add(new Label("subtitle"));
super.add(new Form<Void>("search"));
add(new Label("first"), new Label("second"));

Label heading = new Label("heading", "Hello");
add(heading);

Form<?> form = new Form<>("form");
form.add(new TextField<>("name"));
add(form);
```

The same additions and method-local constructions are supported in an actual
Wicket `onInitialize()` override beginning with `super.onInitialize()`:

```java
@Override
protected void onInitialize() {
    super.onInitialize();
    Form<?> form = new Form<>("form");
    form.add(new TextField<>("name"));
    add(form);
}
```

Constructor and initialization observations share one graph. Each phase keeps
its own local declarations; supported private final fields connect the phases.
Missing, late, or
repeated superclass delegation, unknown conditional setup, mutations, root escapes,
opaque root helpers, constructor alternatives, and unsupported inherited setup keep
the combined attachment scope unresolved. Duplicate paths across phases remain
ambiguous. See [the initialization contract](initialization-coverage.md) for
the framework source audit and precise limits.

- Attachments must resolve to Wicket's `MarkupContainer.add(Component...)` or
  supported `queue(Component...)` placement. Collection
  methods, application overloads/overrides, behavior additions, and form-validator
  additions are not interpreted as component attachments.
- Constructor ID semantics are explicitly reviewed for Wicket 10.11.0 `Label`,
  `Form`, `TextField`, `WebMarkupContainer`, `Panel`, `Button`, `SubmitLink`,
  `Link`, `BookmarkablePageLink`, `ExternalLink`, `AjaxLink`, `AjaxButton`,
  `CheckBox`, `DropDownChoice`, `FeedbackPanel`, `FormComponent`, and
  `FormComponentPanel`, `ListView`, and `RepeatingView`. Overload resolution must be
  valid and accept a first `String` argument; only literal strings are extracted.
  The [constructor audit](constructor-coverage.md) covers all 33 public
  overloads of the ten additional classes and their ID forwarding chains.
  Source forwarding-only custom constructors can be traced to these contracts,
  including reordered arguments, pure delegation, and fixed literal IDs. Unknown
  custom semantics and ID transformations remain `UNRESOLVED_CONSTRUCTOR`.
- Abstract link/Ajax components can use anonymous callback bodies with reviewed
  constructors. Their declared base class is used for the reported type. Callback
  effects and framework-owned descendants remain unexpanded; existing scope
  warnings and enclosing-root guards still apply.
- A direct `add(new ...)`, `this.add(new ...)`, or `super.add(new ...)` in a
  straight-line constructor or supported `onInitialize()` statement receives the
  report root as its parent.
  Local variable receivers such as `form.add(child)` establish container edges.
  Children may be added before or after their container is attached to the root;
  the graph is assembled after collecting additions. IDs remain local to their
  containers, so repeated IDs under different parents remain separate nodes.
- Locals use direct `new` declarations, or supported fresh source factories in
  a fully validated source setup. References resolve to Java declarations,
  not names, and point back to the
  construction's type and source location. Each supported instance has one node,
  shared across its uses as an argument or receiver. Unused constructions are
  omitted; a container used as a receiver is included even if it is never attached.
- Private final instance fields declared on the analyzed class can be initialized
  directly with `new` at declaration or in one direct constructor assignment.
  Supported reads use the field as a component argument or container receiver in
  constructor/`onInitialize()` additions. Unqualified and `this.field` references
  share one node at the original construction. Field-declaration positions and
  hook/constructor source order do not determine runtime phase order. The scan
  checks all field uses in the Java source file; getters, opaque helpers, setters,
  aliases, escapes, captures, foreign-instance access, and extra writes prevent
  tracking. The source pass additionally binds factories and private final fields
  of source superclasses. Instance initializer blocks, constructor preambles, and unknown root
  initialization effects keep field attachments unresolved. See the
  [full field contract](field-tracking.md).
- Unsupported fields, parameters, deferred local assignments, aliases, reassigned locals, and locals
  used outside supported Wicket additions remain `UNRESOLVED_VARIABLE`. This is
  deliberately conservative across the entire method, including writes after
  an addition. Passing a local to an opaque helper, capturing it in a nested class, casting
  it to an alias, or calling another method on it (even a benign setter) prevents
  tracking for now. Arbitrary helper effects and setter semantics are not analyzed.
- Closed source helpers/factories receive their own parameter/local bindings;
  literal origins and component identities are preserved across calls. Source
  superclass constructor and `onInitialize()` chains can share the root graph.
  Unknown branches, recursion, unsafe dispatch, unknown calls, and budget exhaustion fall
  back to the existing uncertainty guards. Only reached helpers are expanded.
  Final-page/final-method overrides and known instance providers also
  establish proven targets, including source interface calls. Their constructors,
  implementation hierarchy, and interface contracts are audited; unknown injected
  receivers and mutable provider state remain unresolved. Private final
  configuration and known constructor-injected dependencies are supported;
  see [the provider-state contract](provider-state.md).
- Unknown receivers remain `UNRESOLVED_PARENT`. Calls in unsupported helpers, conditionals,
  lambdas, loops, or other lifecycle methods remain `UNRESOLVED_SCOPE`; their execution
  and root attachment are not assumed. Nested class bodies are excluded from
  component discovery.
- Unsupported constructor control flow and root tree mutations make that
  constructor's attachment scope unresolved, including direct additions before or
  after the operation. Known local container edges are retained with unresolved
  confidence. Recovered Java syntax also prevents confirmed attachments. Qualified
  `Outer.this` is not mistaken for the root of an analyzed inner Page.
  When a local `onInitialize()` override is present, uncertainty in either phase
  prevents confirmed root attachments in both phases. Unexpanded inherited
  initialization also prevents constructor attachments from remaining confirmed.
- Nested and anonymous class bodies are scanned for explicit or implicit access
  to the analyzed root before being excluded from component discovery. Enclosing
  root effects and root-bound method references such as `this::add` or
  `super::removeAll` make their containing phase's attachments unresolved. They retain
  source-located scope causes without inventing nested components or treating
  another object's methods as page effects.
- Known local edges are retained for unattached containers, but the affected nodes
  remain unresolved. Unknown ancestor IDs and uncertain ancestor attachments
  propagate to descendants. Multiple additions of one instance do not create
  duplicate nodes or choose a last parent. Cycle edges are removed and reported
  as unresolved. None of these cases produces a definitive error.
- Dynamic IDs and constants/expressions outside the reviewed scalar and
  [source-constant](static-constants.md) contracts remain unresolved;
  literal-origin values can be forwarded by the bounded source pass.
  Empty literal IDs and literals containing reserved `:` or `~` characters remain
  available as observations but receive `UNRESOLVED_ID`, not a dynamic-ID label.
  Unknown factory arguments, unsupported chained/inline receiver contexts, and
  explicitly constructed component arrays are not reconstructed.
- Reports contain an explicit root node (`rootNodeId`), source file paths and
  offsets, stable node IDs within the report, confidence, and informational
  diagnostics. `CONFIRMED` describes a proven literal ID and source attachment,
  not a complete runtime hierarchy or a Java/HTML match.
- Reports always carry `PARTIAL_ANALYSIS` and `scopeComplete = false`. Constructor
  setup delegation/alternatives, unknown inherited effects, and runtime execution
  remain unresolved. Java-only discovery emits no markup mismatch diagnostics.
  Additional source-located `UNRESOLVED_SCOPE` warnings identify custom inherited
  setup, alternative/delegating constructors, unexpanded root calls/overrides,
  root escapes, anonymous component bodies, and unsupported tree operations such
  as unsupported queueing, removal, and replacement. Unrelated collection calls remain ignored.

## Companion markup pairing

`WicketPageAnalyzer.analyze(PsiClass)` composes Java discovery, file pairing,
HTML extraction, and matching.
It returns `null` for non-Wicket classes, preserves the discovered Java graph, and
fills `PageReport.markupPath` only when one conventional companion is found.
Unresolved pairing adds informational `UNRESOLVED_MARKUP` report warnings; Java
findings remain available. Like Java discovery, call it under a smart read action,
off the UI thread for real projects.

For `com.example.CustomerPage` in
`src/main/java/com/example/CustomerPage.java`, the supported layouts are:

| Layout | Companion |
| --- | --- |
| Beside Java | `src/main/java/com/example/CustomerPage.html` |
| Matching resource package | `src/main/resources/com/example/CustomerPage.html` |

The resolver uses the owning module's configured Java source and resource roots,
including custom directory names. Java source package prefixes are respected;
resource lookup uses the declared Java package. Source packages must agree with
the source-root mapping. Default-package classes are supported as well.

- Production Java uses production resources; test Java uses test resources.
  Source companions remain module-owned. Compiled ancestors can supply scoped
  dependency resources as described below. Excluded directories,
  unconfigured resource directories, and HTML in unrelated Java roots are ignored.
- One unique file yields `PAIRED`. No file yields `MISSING`. Multiple candidates,
  including a file in each supported layout, yield `AMBIGUOUS`; no location takes
  precedence. Candidate paths are deduplicated and sorted deterministically.
- Nested classes, secondary classes with a different source-file basename,
  nonphysical files, Java files outside configured Java roots, package mismatches,
  and custom resource output mappings yield `UNSUPPORTED`. A partial lookup does
  not select an otherwise plausible file.
- Java starts with the exact `<ClassName>.html` convention. This resolver checks
  one class; the composed analyzer adds ancestor fallback and inheritance below.
  Opening a named HTML variant explicitly selects its filename suffix. Compiled
  ancestors may supply classpath/JAR HTML. Custom locators, ambiguous variant
  fallback, and `.xhtml` remain unsupported.

`WicketPageResolver.resolve(PsiClass)` exposes the structural lookup separately as
a `MarkupResolution` value containing status, selected path and VFS URL, candidate
paths, and diagnostics; Wicket eligibility is the composed analyzer's responsibility. Results
retain no PSI, virtual files, or project objects. Lookup is read-only, cancellable,
does not refresh the filesystem, and caches no results across calls.

The selected VFS URL lets the composed analyzer obtain HTML/XML PSI without
refreshing the filesystem. Ambiguous/unsupported lookup skips extraction; missing
companions can fall back to source ancestors as described below.

### Markup inheritance

`WicketPageAnalyzer` supports conventional source-based Page and Panel markup
chains. If a class has no companion, the nearest ancestor companion is used.
An own companion replaces ancestor markup unless it contains `wicket:extend`.
Exactly one extension section is inserted into exactly one empty, active
`wicket:child` slot in the ancestor layout; multiple levels are supported.
Preview markup outside a derived extension remains inactive. Parent-local Wicket
paths and original source locations are preserved across files, and duplicate
IDs are checked again after merging.

Missing, ambiguous, malformed, or unsupported ancestor layouts retain unresolved
findings. Supported static headers and dependency layouts are composed; custom
locators/prefixes and unavailable inherited Java setup remain unresolved. Every contributing
markup file participates in stale-report checks, including empty extensions.
See the [inheritance contract](markup-inheritance.md) for the source audit,
supported boundaries, and fixture evidence.

## HTML/XML extraction

`WicketMarkupAnalyzer.analyze(PsiFile, MarkupKind)` extracts a `MarkupReport` using
HTML/XML PSI. The default kind is `PAGE`; the composed analyzer selects `PANEL`
for Panel and FormComponentPanel subclasses and stores the result in `PageReport.markup`. The Java
component graph is preserved, and markup diagnostics are included in report warnings.

Every tag is retained as a `MarkupElement`, including ordinary HTML wrappers.
`parentElementId` describes the full element tree; `parentWicketElementId` refers
to the nearest participating element with a Wicket ID, skipping ordinary wrappers.
This keeps an input under its Form even when ordinary `div` or `section` elements
intervene. Node IDs and output order are deterministic within a report.

- `location` points to the opening tag's `<`, and `wicketIdLocation` points to the
  ID attribute's name, using PSI character offsets and source file paths. Literal
  values preserve case and whitespace; predefined and numeric entities are decoded
  through PSI. Comments, script/style text, plain `id`, and foreign namespace IDs
  do not become Wicket components. Both HTML and XML PSI are supported.
- `hasWicketId` preserves an ID boundary even when its value is missing or
  ambiguous. Empty/valueless IDs, multiple ID attributes, custom entities,
  reserved component-path characters, and nonstandard ID attribute spelling
  remain unresolved. Repeated IDs under different Wicket parents are allowed;
  repeated IDs under one parent are ambiguous and make descendants unresolved.
- In Panel mode, exactly one `wicket:panel` section defines the component scope.
  The wrapper is transparent to Wicket parentage. Preview elements outside it
  remain in the full tree with `participatesInComponentTree = false`; they are
  excluded from component ID uniqueness checks. Missing/multiple sections do
  not select an arbitrary active scope.
- Standalone extraction treats inheritance (`wicket:extend`/`wicket:child`) as
  unresolved; the composed analyzer handles the validated chains described above.
  Custom Wicket namespace prefixes,
  PSI syntax errors, and incomplete or mismatched tag boundaries make the document
  context unresolved, including boundaries silently recovered by PSI. Standard
  HTML void tags and optional closing tags remain supported. Other Wicket tags,
  Wicket attributes that can introduce auto components, and unsupported header
  layouts leave their own branches unresolved. Uncertainty propagates
  through ordinary wrappers; independent supported branches remain available.
  `MarkupReport.documentDiagnostics` records document-wide causes separately from
  element diagnostics; `diagnostics` still contains the combined diagnostic list.
- Diagnostics are informational. Extraction records observations without executing
  Wicket, resolving arbitrary/custom entities, fetching schemas, modifying files,
  or treating the PSI parser as a full Wicket runtime validator. Unsupported PSI
  and empty markup produce unresolved reports. Analysis is read-only, cancellable,
  and returns value objects without retaining PSI, virtual files, or project state.

`MarkupReport.scopeComplete` describes extraction within the supported static
markup subset, not runtime reconstruction. The overall `PageReport` remains partial
with `scopeComplete = false`. Extraction itself emits no matching findings; the
composed analyzer applies the matcher to its output.

## Parent-aware matching

`WicketGraphMatcher.match(PageReport)` consumes the Java and markup snapshots and
returns a new report with `PageReport.matching`. It reads no PSI and retains no
project state. The composed analyzer runs this step even when pairing fails, so
Java components can receive unresolved findings when markup is unavailable.

Matching uses the full sequence of literal IDs from the analyzed root. For
example, `left:name` and `right:name` are separate paths. Ordinary HTML wrappers
are skipped using the extracted Wicket parent relationship. A matching ID under
another parent never supplies a counterpart. IDs remain case-sensitive and are
compared as segments, with no delimiter-based key construction.

| Finding | Meaning |
| --- | --- |
| `MATCHED` | One supported Java node and one markup element share a unique path. |
| `JAVA_ONLY` | No counterpart was observed in supported, fully extracted markup. |
| `MARKUP_ONLY` | No Java counterpart was statically discovered, with no explicit uncertainty affecting this path. |
| `UNRESOLVED_*` | ID, hierarchy, uniqueness, or markup uncertainty prevents classification. |

Each `MatchFinding` contains its code, message, confidence, severity, relevant node
IDs, source locations, and a deduplicated `causes` list. Causes retain their original
codes, messages, severity, and source locations from discovery, ancestor context,
file pairing, or markup extraction. A positive match references both graphs and fills the
Java node's `markupLocation` with the owning markup tag's location. Root metadata,
ordinary HTML elements, and inactive Panel preview IDs are excluded from findings.
Discovery diagnostics, graph relationships, and discovery confidence are preserved.

- Positive matches have `CONFIRMED` finding confidence within the supported static
  subset. They make no claim about runtime execution or whole-project completeness.
  Known static pairs can still match alongside unresolved components.
- `JAVA_ONLY` and `MARKUP_ONLY` findings have `INFERRED` confidence and `INFO`
  severity. They describe static observations, including descendants of unmatched
  containers, and never assert definitive runtime errors.
- Duplicate paths on either side block matching, including otherwise unique
  descendants of ambiguous parents. Missing/cyclic parent links are rejected.
  Unsupported IDs, factories, unknown parents, inheritance, and missing/ambiguous
  companions remain unresolved rather than becoming convenient global ID matches.
- Negative findings are deliberately conservative: incomplete/uncertain markup
  suppresses Java-only classification. Unexpanded Java root setup also prevents
  unmatched claims. An unknown Java child ID blocks markup-only classification
  within its known parent subtree; unrelated paths can still receive informational
  findings. An unknown root ID or unknown parent can affect every root path.
- Source uncertainty keeps its primary classification even when markup is missing:
  a dynamic ID remains `UNRESOLVED_DYNAMIC_ID`, with the missing/ambiguous pairing
  reason retained in `causes`. Constructor, variable, invalid-literal, and scope
  causes remain distinguishable. Inherited/anonymous setup and opaque root helpers
  cannot masquerade as absent Java counterparts.
- Finding order is deterministic: Java components in report order, followed by
  unpaired active markup components in markup order. Reapplying matching replaces
  its findings and clears stale markup links without changing discovery data.
  Matching checks cancellation throughout traversal.

`MatchReport.scopeComplete` requires both input scopes to be complete and their
paths to be resolved. Current composed reports remain partial because Java discovery
does not reconstruct all runtime behavior. All matching findings are informational;
discovery confidence and matching confidence describe separate evidence.
Explicitly supported static pairs can remain matched alongside scope warnings;
unexpanded helper effects and runtime mutations are not simulated. Unsupported
constructor control flow, proven root tree mutations, and broken syntax do not
retain confirmed source links.

## Toolchain

- IntelliJ IDEA **2026.2.3**, build **262.10968.63** (sandbox target).
- Java **25** JDK for Gradle and compilation.
- Kotlin **2.4.21**, compiling against IDEA's bundled stdlib **2.4.0**.
- IntelliJ Platform Gradle Plugin **2.19.0**.
- Gradle **9.7.0**, pinned by the included wrapper with SHA-256 verification.
  This is within Kotlin 2.4.21's fully supported Gradle range.
- Wicket fixture baseline: **10.11.0**, with transitive libraries resolved from
  Maven Central and attached to fixture Java projects only. No Wicket runtime is
  bundled in the plugin or added to its production classpath.

Versions were checked on 2026-10-28 against the
[IDEA release listing](https://data.services.jetbrains.com/products/releases?code=IIU&latest=true&type=release),
[Gradle plugin releases](https://github.com/JetBrains/intellij-platform-gradle-plugin/releases),
[Kotlin compatibility table](https://kotlinlang.org/docs/gradle-configure-project.html),
and [Wicket releases](https://wicket.apache.org/versions/).

Use an installed Java 25 JDK. With SDKMAN, select your installed candidate, for example:

```bash
source "$HOME/.sdkman/bin/sdkman-init.sh"
sdk use java 25.0.4+1.1-librca
java -version
```

The selection applies to the current shell. No system-wide Java change is required.

## Build and test

```bash
./gradlew test buildPlugin verifyPluginProjectConfiguration verifyPluginStructure
```

The first run downloads Gradle, IDEA (including its sandbox runtime), and build/test
dependencies. Allow several GB of disk space and an internet connection. Subsequent
runs reuse Gradle's cache. A separate Gradle installation is unnecessary.

- Plugin archive: `build/distributions/wicketlens-0.1.0-SNAPSHOT.zip`
- Test report: `build/reports/tests/test/index.html`
- Optional binary compatibility verification against the pinned IDEA:

```bash
./gradlew verifyPlugin
```

To install the built prototype in the verified IDEA version, open **Settings >
Plugins**, select **Install Plugin from Disk** from the settings menu, and choose
the ZIP above. Restart the IDE if requested. This is a local snapshot build;
there is no published Marketplace release.

The verified combination is IDEA **2026.2.3** and Wicket **10.11.0**.
`WebPage`, `Panel`, and `FormComponentPanel` are covered by PSI fixtures. Older Wicket versions, other
IDE products, and newer IDEA builds have not been verified. The plugin metadata's
open-ended upper build range is not a compatibility guarantee.

## Launch the sandbox

From a graphical desktop session:

```bash
./gradlew runIde
```

This starts a separate development IDE with WicketLens installed. Its configuration,
plugins, and logs are isolated under `build/idea-sandbox/`; your normal IDE's settings
are not modified. Complete any first-launch dialogs, then open a Java project with
resolved Wicket dependencies and configured Java/resource roots. With a Java file
active, select **Tools > Analyze Wicket Page**, or use **Find Action**. Alternatively,
open **WicketLens** with **Follow editor** enabled to analyze the current Java file
or its unique conventional HTML companion automatically. The class at
the caret is selected if it extends Wicket `WebPage`, `Panel`, or `FormComponentPanel`; otherwise the file
must contain exactly one eligible top-level class. The nearest eligible enclosing
class is used when the caret is inside an anonymous child component.

Analysis waits for indexing and committed documents, runs under a constrained
read action in the background, and opens the **WicketLens** sidebar. New layouts
dock it on the left. Existing projects retain their saved placement; use the tool
window's **Move to** menu to choose the left edge if needed.
Its sidebar button uses a dedicated **W** icon with light/dark and regular/compact
variants, distinguishing WicketLens from IntelliJ's Structure window.
The **Hierarchy** tab uses native class, field, markup, and informational icons.
Component IDs appear in normal text, with types and matching status in muted text.
The compact toolbar provides **Re-analyze**, **Cancel**, Java/HTML navigation,
**Copy Wicket Context**, **Expand all**, and **Collapse all** through standard IDE
icon actions and tooltips. Type in the tree to find IDs or types, including inside
collapsed branches. Use **Show component details** to reveal the selected row's
confidence, parent, original sources, and uncertainty causes; it is hidden by
default to leave room for the tree.
The search field above the tree filters IDs, types, and colon-separated paths;
space-separated terms must all match. Combine it with **All**, **Unpaired**,
**Java only**, **Markup only**, **Unresolved**, or **Matched**. Ancestors remain
visible in muted text for context, including matched containers around markup-only
children. Search and status stay applied across page switches and reanalysis.
The text report and copied context still cover the full page. See
[hierarchy filtering](hierarchy-filtering.md) for behavior and limits.
Use **Focus subtree** on a branch and **Show full hierarchy** to return. **Next
finding**/**Previous finding** (F2/Shift+F2 while the tree is focused) select
unpaired or unresolved components within the current view, revealing collapsed
branches. Reanalysis restores expansion, selection, and focus through unique
logical addresses, using refreshed source links. See
[hierarchy exploration](hierarchy-exploration.md) for restoration guards.
The **Text report** tab retains the full textual snapshot and source links. Missing or
ambiguous markup still leaves the Java report available. Unsupported input shows
an explanation in the tool window.

**Follow editor** tracks file selection once the sidebar has been opened. Switching
to another Wicket Java file or its uniquely paired HTML replaces the report;
unrelated files clear the previous page instead of leaving its snapshot visible.
Disabling it pins the report while browsing other files. Rapid switches cancel
obsolete requests. Opening a source through WicketLens's navigation keeps the
current page context, including inherited markup; selecting that file manually
can follow its own Java class. The setting is remembered per project.

Pending Java and HTML editor edits must be committed to PSI before the snapshot
is read. This does not require saving files to disk. Re-running analysis after an
edit therefore reads the updated IDs and source offsets; it does not reuse stale
PSI from the previous commit.

Select a component in the tree or its source line in the text report, then use **Go to Java** or
**Go to HTML**. Double-clicking or pressing **Enter** opens the selected row's
default destination: Java for a component row, HTML for a markup source line or
markup-only finding. Java navigation opens the construction (or the page class
for the root); component HTML navigation opens the owning tag. On the class row,
**Go to HTML** opens the beginning of its resolved companion file, including an
inherited companion when that is the selected markup. Missing or ambiguous
companions leave the class HTML action disabled. Component counterpart actions
remain disabled when no supported match was established; each observed source itself
remains navigable. Repeated IDs
under different parents retain their own destinations.

Use **Cancel** or the IDE's background progress cancellation to stop analysis. A
new request supersedes the previous one. Each project retains one completed value
snapshot; closing the project cancels its work. Editor-file changes can trigger
analysis through Follow editor; text edits alone do not automatically re-analyze.
Source edits, relevant VFS changes, project PSI changes, and
root/dependency configuration changes mark a report **out of date**. The old
report stays visible, but source navigation and copying are blocked. This check
is conservative: another Java/resource edit can affect resolution and invalidate
the report. Saving already analyzed text does not itself invalidate it.

Use **Re-analyze** to refresh the same Java class, even while its HTML is active
or source offsets have shifted. If that class was renamed or removed, run
**Analyze Wicket Page** again from the desired class. Once a snapshot becomes
stale it stays stale until re-analysis, including after an undo. External changes
are detected when IDEA observes them through its VFS; the plugin does not force
a filesystem refresh. Starting a new analysis clears
the previous snapshot, and cancelled/failed requests cannot leave stale copyable
results. Analysis modifies no source files.
Stale trees remain expandable and readable; re-analysis replaces the tree and
clears selection so old node offsets cannot carry over to a new snapshot.

Once analysis completes, use the sidebar's **Copy Wicket Context** toolbar action or
the identically named Tools/Find Action command. It copies the prepared JSON from
that project's last completed report without running analysis again. Copy is
disabled while analysis is running, the report is stale, or no completed report
exists. Navigation also validates snapshot versions immediately before opening
an editor, so it cannot use a known outdated source offset.

The plugin requires IDEA's bundled Java and XML modules. No WicketForge dependency,
AI integration, telemetry, or plugin-initiated network requests are included.

## Editor problems and gutter icons

**Possible unpaired Wicket ID** is enabled by default under **Settings > Editor >
Inspections > WicketLens**, with configurable **Weak Warning** severity. Supported
literal Java IDs without markup and active HTML `wicket:id` values without Java
setup are highlighted at their values. Matching uses the complete component path,
so an identically named ID under another parent does not count as its pair.

Findings appear in **Problems > Current File** during normal editor analysis.
Use **Code > Inspect Code** with project scope for project-wide inspection results.
This uses IntelliJ's native local inspection system; it does not supply the
Ultimate-specific live **Project Errors** analysis. See the platform documentation
for [Problems](https://www.jetbrains.com/help/idea/problems-tool-window.html) and
[file/project analysis](https://www.jetbrains.com/help/idea/file-and-project-wide-analysis.html).

A warning icon appears beside each affected line. Its tooltip lists the possible
unpaired IDs on that line; clicking opens their page's WicketLens report. Disable
icons separately under **Settings > Editor > General > Gutter Icons > Possible
unpaired Wicket IDs**. Disabling or suppressing the inspection also hides its
icons. Native suppression actions are available where supported by the language.

These are possible mismatches in partial static analysis, not proven runtime
errors. Only `JAVA_ONLY` and `MARKUP_ONLY` observations without uncertainty causes
become inspection problems. Dynamic IDs, ambiguous/missing companions, duplicate
paths, unsupported setup, and other unresolved cases stay in the report. Core
report findings and exported JSON retain their informational severity.

Editor inspection results refresh after committed PSI changes, including edits to
the paired file, file creation/deletion, and root changes. Saving is not required.
The gutter and inspection share a per-file cache of value-only diagnostics;
indexing or uncommitted documents prevent a mixed Java/HTML snapshot. The sidebar
remains a separately refreshed snapshot with its existing stale-report controls.

## Editing Wicket IDs

Use **Go to Declaration** (Ctrl+B with the default keymap), or Ctrl-click an ID
value, to open its confirmed counterpart in Java or HTML. Java navigation lands
on the original construction, including tracked fields. Missing, ambiguous, and
unresolved matches do not create navigation targets. Inherited markup can be a
destination from Java; reverse navigation from an ancestor's HTML uses that
ancestor's own page context, without guessing a descendant.

Place the caret in a matched ID and choose **Refactor > Rename** (Shift+F6 with
the default keymap). WicketLens uses the native rename dialog, usage preview, and
undo command to change the Java literal and HTML attribute together. It checks
both sibling scopes for name collisions, accepts IDs such as `page-heading`,
escapes Java/XML characters, and leaves display strings and comments untouched.
Rename requires a unique pair with supported attachment scope. Dynamic IDs,
duplicate paths, shared helper/inherited implementation literals, IDs from inherited
markup resources, classes with known project inheritors, and other unresolved
contexts remain excluded. The selected HTML attribute must belong to the analyzed
class's own conventional companion, including when an own extension includes base markup.
Both sources must be writable. The pair and collision checks run again
before applying the preview, so changed sources cannot silently rename a stale pair.

Use **Basic Completion** (Ctrl+Space with the default keymap) inside a supported
Java constructor ID argument, a traced helper/factory-call ID literal, or an HTML
`wicket:id` value. WicketLens suggestions come from the paired file under the
corresponding parent, excluding IDs already
used by siblings. Empty values and partially typed IDs are supported. Choosing a
suggestion replaces the whole value, including any suffix after the caret, while
preserving its quotes and escaping the inserted ID. Panel preview markup and
unsupported/ambiguous scopes do not contribute suggestions. Java completion is
based on the same constructor/source-provenance contracts as analysis.

ID values are orange by default; quotes, display strings, and unrelated attributes
keep their normal syntax colors. Java coloring recognizes reviewed/forwarded
constructor IDs and traced literal ID origins; HTML coloring recognizes exact `wicket:id` values even when
their counterpart is not available. Coloring does not imply a confirmed match.
Configure or remove the foreground color under **Settings > Editor > Color Scheme
> WicketLens > Wicket component ID**. The Default and Darcula schemes start with
`#E9601A`, the orange used by Wicket's `DefaultButtonImageResource` in the reviewed
[10.11.0 sources](https://repo.maven.apache.org/maven2/org/apache/wicket/wicket-core/10.11.0/wicket-core-10.11.0-sources.jar).
Diagnostic underlines and gutter warnings remain available alongside the color.

References, completion, and inspections share committed analysis snapshots cached
as values on each physical PSI file. PSI/root/VFS changes invalidate the cache;
completion analyzes the original file rather than the IDE's synthetic completion
copy. Analysis remains read-only. Rename and accepting completion are explicit
editor operations that modify source through IntelliJ's refactoring/editor APIs.

## JSON context

The explicit schema has `schemaVersion = 1`, with the class name, root node ID,
paired markup path, overall scope completeness, and report warnings. `components`
includes the root and each Java node's ID, Wicket ID, type, parent, source
locations, discovery confidence, matching status/confidence, linked markup node,
and diagnostics. Queue and repeater nodes conditionally add queue provenance,
repetition kind, and template flags; logical items have no runtime ID or separate
matching finding. The synthetic root has no matching status. Unavailable values
are explicit JSON `null` values.

`markup` preserves the complete HTML element tree, nearest Wicket parent links,
active/preview participation, confidence, source locations, and diagnostics.
`matching.findings` preserves matched, unmatched, and unresolved statuses with
both graph references, locations, severity, confidence, and uncertainty causes.
Every location contains `file` and a zero-based PSI character `offset`.

Output is pretty-printed with stable field ordering, canonically sorted arrays,
and a trailing newline. It has no timestamps or transient IDE objects. Repeated
exports of the same snapshot are byte-for-byte identical; paths and offsets mean
it is not intended to be identical across project relocations or edits. Overall
reports remain partial, and discovery confidence is separate from matching
confidence. Export performs no network requests.

## Demo walkthrough

The small Maven project in [examples/demo](../examples/demo) is a controlled fixture
for checking the plugin UI. Import its `pom.xml` in the sandbox, resolve its Wicket
10.11.0 dependency, and open `example.CustomerPage` in `src/main/java`.

1. Run **Analyze Wicket Page**.
2. Check that the report shows **3 MATCHED**, **1 JAVA_ONLY**, and **1 MARKUP_ONLY**.
   The `form` node contains `name`, even though an ordinary HTML `div` wraps the
   input. `javaOnly` and `markupOnly` remain informational findings.
3. Run **Copy Wicket Context** and inspect the exported hierarchy and source
   locations. Edit and re-run to obtain a fresh snapshot.
4. Select `title` in the report and use **Go to Java**, then **Go to HTML**.
   Check the constructor expression and opening `h1` tag respectively.
5. Edit the Java or HTML. Check that the status says the report is out of date
   and navigation/copying are disabled. Use **Re-analyze**, then select a component
   again to navigate using the refreshed locations.

The demo can also be compiled with `mvn -f examples/demo/pom.xml compile` using
Java 17 or later. This fixture walkthrough is separate from the deferred real
application evaluation.

Open `example.StandardControlsPage` to try the ten additional reviewed component
classes. Its companion markup includes a nested Form and all ten control types.
Expect **12 MATCHED** findings, plus informational warnings for the anonymous
callback bodies that are not expanded. The full-pipeline fixture test checks this
same Java/HTML pair. The original `CustomerPage` walkthrough above is unchanged.

Open `example.InitializationPage` to try constructor setup followed by
`onInitialize()` setup. Its full-pipeline fixture expects **6 MATCHED** findings,
including a Form with three children and a hook-local Label. This is a static
fixture, without running a Wicket application.

Open `example.InheritedLayoutPage` to try a two-file inherited layout. Expect
**3 MATCHED** findings, including Form → TextField. The source pass verifies the
empty base setup. Expand the Form branch, select `name`, inspect
its details, and navigate to Java/HTML. Both layout HTML files appear in the root
details and participate in freshness checks.

Open `example.FieldComponentsPage` to try private final fields initialized both
at declaration and in the constructor, with Form children added in
`onInitialize()`. Its fixture expects **6 MATCHED** findings. Select `form` to
navigate to the field's original `new Form<>("form")` construction.

Open `example.SourceSetupPage` to combine all four source-analysis features.
Expect **7 MATCHED** components: two from source superclass constructor/lifecycle
setup, a custom `SourceHeading` whose ID is argument two, a factory-built Form
with a helper-added TextField, a factory Label, and a helper-added status Label.
Construction navigation opens the original `new` expression. Direct ID navigation
uses the originating literal, including individual factory-call arguments. The
three consulted Java files and both inherited markup files participate in
freshness checks. See [the bounded source contract](source-setup.md).

Open `example.FormComponentsPage` for **3 MATCHED** parent components, including
a custom input and a composite input. Open `example.CompositeInput.java` or its
HTML for **1 MATCHED** internal field. Preview IDs in the page's composite tag
are inactive. See [the custom input contract](form-components.md).

Open `example.HeaderDetailPage` for **3 MATCHED** bindings spanning the base title,
derived header stylesheet, and body title. Open `HeaderDetailPage_fr.html` to
select the French layout and its exact suffixed base. Java returns to the default
layout. See [header/variant/dependency coverage](markup-inheritance.md).

Open `example.QueueRepeaterPage` for **6 MATCHED** bindings spanning a queued
container/Label, a ListView item, and a RepeatingView item. Expand each repeater's
`item [template]` group: its child binding is inferred, and runtime row IDs/counts
remain unknown. See [queueing and repeaters](queueing-repeaters.md).

Open `example.DispatchPage` for **4 MATCHED** bindings: the final page override
selected during base construction, an interface-factory Label, and a factory-built
Form with a TextField child. All four consulted Java files participate in freshness
checks. See [proven source dispatch](source-dispatch.md).

Open `example.ConfiguredPartsPage` for **4 MATCHED** bindings from two independently
configured providers and a delegated Form factory. The heading IDs come from the
`ConfiguredParts` constructor arguments; the `label("unused")` arguments do not
supply those IDs. Constructor configuration participates in native ID editing and
coloring. See [provider configuration and injection](provider-state.md).

Open `example.DefaultMethodsPage` for **6 MATCHED** bindings: three Forms with
separate `name` children. It combines a configured provider default, an inherited
final-page default, and an explicit interface-super call. See
[source interface defaults](interface-defaults.md).

Open `example.ProviderFactoryPage` for **5 MATCHED** bindings from static/nested
provider factories and an instance builder. Separate configured providers supply
`title`, `subtitle`, and `footer`, while the first supplies a Form with its `name`
child. See [fresh provider factories](provider-factories.md).

Open `example.ProviderAccessorPage` for **4 MATCHED** bindings: `title`, `subtitle`,
and a Form with its `name` child. Private page accessors return known cached
providers; provider dependency and String getters preserve the configured heading
IDs. The ignored `label("unused")` arguments do not become IDs.
See [read-only source accessors](source-accessors.md).

Open `example.ConditionalSetupPage` for **4 MATCHED** bindings: `title`, `subtitle`,
and a Form with its `name` child. It combines a configured boolean getter,
short circuiting, a selected factory return, and an initialization guard. See
[proven conditional setup](conditional-setup.md).

Open `example.ExpressionSetupPage` for **5 MATCHED** bindings: computed `headingTitle`
and `item3`, plus literal `status`, `form`, and its `name` child. It combines immutable
configuration, arithmetic/comparisons, String content equality, and initialization
setup. See [reviewed scalar expressions](scalar-expressions.md).

Open `example.ConstantSetupPage` for **5 MATCHED** bindings: `title`, `item3`,
`form`, its `name` child, and `status`. It combines source constants, a static
import, a known flag, and initialization setup. Two Java files and the HTML
companion participate in freshness checks. See [source constants](static-constants.md).

## Troubleshooting and limits

- **Analyze action is unavailable:** activate a Java source file in an open
  project. The explicit Analyze command starts from Java. Follow editor can also
  start from HTML when one conventional Wicket Java counterpart is established.
- **No eligible class:** resolve the project's Wicket dependencies in IDEA. Put
  the caret inside the desired `WebPage`, `Panel`, or `FormComponentPanel` when a file has multiple
  eligible classes. A standalone file without module roots cannot establish
  conventional resource pairing.
- **Markup is unresolved:** use the exact class basename and `.html` extension,
  beside the Java source or in the same package under a configured resource root
  in that module. Production/test roots are kept separate. Multiple candidates
  remain ambiguous; diagnostics list the candidate paths.
- **A component is unresolved:** inspect its diagnostic and uncertainty causes.
  Reviewed/forwarded constructors and bounded source setup with proven boolean choices are
  reconstructed. Unsupported fields, aliases, opaque helper/factory results,
  other lifecycle setup, unsupported queue/repeater patterns and markup/resource contexts,
  and runtime behavior remain outside the subset. Custom components with
  unverified constructor effects remain unresolved as children; custom Panels
  can still be analyzed separately as roots.
- **Report is out of date:** use **Re-analyze** after edits. Copy uses
  the last completed current snapshot in the project; it does not re-analyze.
  Starting another request, cancelling it, or failing analysis clears that
  project's copyable report. Clipboard contents already copied remain until the
  clipboard is replaced.

Findings describe a partial static snapshot. `MATCHED` confirms an observed unique
path in the supported subset, and `JAVA_ONLY`/`MARKUP_ONLY` remain informational.
The prototype does not prove runtime correctness. Editor inspections surface
possible unpaired IDs as weak warnings. Inspection quick fixes are available
for supported scopes as described in
[the quick-fix contract](inspection-quick-fixes.md). A real application feasibility
assessment and performance evaluation have not been performed.

## Source constants verification (2026-10-30)

The full suite passes **1,095 tests**, with no failures, errors, or skipped tests.
The extension adds 65 cases: 54 constant-analysis/demo tests, 10 native editor
tests, and one mutable-static-boolean guard. Earlier integral/boolean constant
expectations now verify the supported behavior. The affected 240-test run also
passes.

Coverage includes source names/static imports, inherited/interface constants and
lookup dependencies, constant calculations, initialization and effect guards,
invalid assignments, inaccessible/ambiguous names, cycles and bounds, whole-use
provenance, conservative editing, native Java references, stale snapshots, and
deterministic read-only reports/exports. See [source constants](static-constants.md).

All **35 demo Java files** compile against Wicket 10.11.0. `ConstantSetupPage`
has **5 MATCHED** bindings, combining constants, a static import, calculations,
a known branch, and initialization. Two Java files and its HTML form the snapshot.

Packaging and configuration/structure checks pass. Binary compatibility remains
**Compatible**, with four deprecated and six experimental API notices. The final
run reuses unchanged production packaging/verifier results; the resolver and
interpreter in the distribution match current compiled classes. JSON schema 1
is unchanged. This slice adds no graphical sandbox verification claim, and real
application accuracy/performance evaluation remains deferred.

## Rename and delegation review verification (2026-10-30)

Both review findings were reproduced and fixed. Paired rename requires the selected
HTML attribute to belong to the analyzed class's own conventional companion.
Shared inherited templates, including base IDs composed into an own extension,
cannot be renamed as a single derived pair. Unique caller literals paired with
the subclass's own markup remain eligible.

Constructor ID tracing now validates every delegation argument before following
the target ID origin, including fixed target literals. Assignments, updates, and
unreviewed argument expressions remain unresolved. Pure reordered forwarding,
parenthesized parameters, and the reviewed repeater factories remain supported.

The full suite passes **1,030 tests**, with no failures, errors, or skipped tests.
The 12 new cases cover shared markup and safe own-template rename, argument
evaluation effects, nested/conditional writes, fixed-literal targets, unknown
calls, updates, and pure forwarding. Packaging, plugin configuration/structure,
and binary compatibility checks pass: **Compatible**, with the existing four
deprecated and six experimental API notices. See [the source contract](source-setup.md).
Real application evaluation remains deferred; this review adds no graphical
sandbox verification claim.

## Hierarchy exploration verification (2026-10-30)

The full suite passes **1,018 tests**, with no failures, errors, or skipped tests.
This extension adds 35 cases: four focus-projection tests, 11 logical state-key
tests, and 20 native view tests. Coverage includes subtree focus with filters,
next/previous traversal and keyboard shortcuts, expansion and selection across
reanalysis, fresh Java/HTML source links, page isolation, ambiguous/dynamic IDs,
literal UI text, stale snapshots, and complete exports.

Packaging, plugin configuration/structure, and binary compatibility verification
pass. The verdict is **Compatible** with the existing four deprecated and six
experimental API notices. See [the hierarchy-exploration contract](hierarchy-exploration.md).
Both hierarchy slices are implemented; real application accuracy/performance
evaluation remains deferred. This slice adds no graphical sandbox verification.

## Hierarchy filtering verification (2026-10-30)

The full suite passes **983 tests**, with no failures, errors, or skipped tests.
The extension adds 13 filter-projection tests and 14 native view tests. Coverage
includes status/query composition, qualified types and paths, retained parent
context, markup-only placement, repeated IDs, empty results, selection and source
actions, full-report exports, reanalysis/page switches, stale snapshots, and cancellation.

Packaging, plugin configuration/structure, and binary compatibility verification
pass with the existing four deprecated and six experimental API notices. See
[the hierarchy-filtering contract](hierarchy-filtering.md). Filtering is
presentation-only; analysis and JSON schema 1 retain the full report. Real
application accuracy/performance evaluation remains deferred.

## Scalar expression verification (2026-10-30)

The full suite passes **956 tests**, with no failures, errors, or skipped tests.
The extension adds 56 scalar-expression/demo tests and 11 editor tests. Coverage
includes integral promotion/overflow/casts, arithmetic and comparisons, String
concatenation/content equality, unknown values and divisors, nullable unboxing,
assignment compatibility, configuration, computed-ID provenance, whole-expression
problems, editing dependencies, and source/snapshot freshness.

Packaging, plugin configuration/structure, and binary compatibility verification
pass with the existing four deprecated and six experimental API notices. All
33 demo Java files compile against Wicket 10.11.0. `ExpressionSetupPage` has
**5 MATCHED** bindings, including two computed IDs, and tracks two Java files.
See [the scalar-expression contract](scalar-expressions.md). JSON schema 1
is unchanged. Real application accuracy/performance evaluation remains deferred.

## Proven conditional setup verification (2026-10-30)

The full suite passes **889 tests**, with no failures, errors, or skipped tests.
The extension adds 57 conditional/demo tests, 12 native editor tests, and a direct
fallback regression for unexpanded root helper effects. Coverage includes proven
branches, boolean short circuiting, ternary choices, early returns, private final
fields, factory/accessor boundaries, Page/Panel roots, repeaters, uncertainty,
budgets, native editing, and condition-source freshness.

Packaging, plugin configuration/structure, and binary compatibility verification
pass with the existing four deprecated and six experimental API notices. All
31 demo Java files compile against Wicket 10.11.0. `ConditionalSetupPage` has
**4 MATCHED** bindings and tracks four consulted Java files. See
[the conditional setup contract](conditional-setup.md). Real application
accuracy/performance evaluation remains deferred.

## Source accessor verification (2026-10-30)

The full suite passes **819 tests**, with no failures, errors, or skipped tests.
The extension adds 45 accessor/demo tests and eight native editor tests. Coverage
includes cached provider reads, scalar getters, nested/default/generic dispatch,
private final page fields, initialization and purity guards, return compatibility,
unknown injection, opaque repeater model data, native editing/inspections, source
freshness, and deterministic read-only reports.

Packaging, plugin configuration/structure, and binary compatibility verification
pass with the existing four deprecated and six experimental API notices. All
29 demo Java files compile against Wicket 10.11.0. `ProviderAccessorPage` has
**4 MATCHED** bindings and tracks four consulted Java files. See
[the accessor contract](source-accessors.md). Real application accuracy and
performance evaluation remains deferred.

## Source provider factories verification (2026-10-29)

The full suite passes **766 tests**, with no failures, errors, or skipped tests.
The extension adds 29 provider-factory/demo cases and eight native editor cases.
Coverage includes static/instance/default factories, nested and generic returns,
allocation separation, constructor composition, ID provenance, type compatibility,
cached/parameter/null returns, unknown injection, recursion, initialization,
read-only exports, native editing, problem ranges, and source freshness.

Packaging, plugin configuration/structure, and binary compatibility verification
pass with the existing four deprecated and six experimental API notices. All
27 demo Java files compile against Wicket 10.11.0. `ProviderFactoryPage` has
**5 MATCHED** bindings and records five consulted Java files. See
[the provider-factory contract](provider-factories.md). Real application
accuracy/performance evaluation remains deferred.

The refreshed graphical sandbox confirms all five bindings, including independent
provider IDs and the delegated Form child. All five Java dependencies and the HTML
companion are current, and its loaded JAR matches the verified distribution.
Temporary developer Swing checks make no source edits and are not distributed.

## Source interface defaults verification (2026-10-29)

The full suite passes **729 tests**, with no failures, errors, or skipped tests.
The extension adds 33 default-method/demo tests and nine native editor tests.
Coverage includes class/default precedence, inherited class implementations,
most-specific defaults, diamonds, abstract suppression, explicit interface-super
calls, private/static helpers, generics, interface initialization, unknown receivers,
and source freshness. Fixtures now explicitly use Java 25 source language level
with the Java 25 SDK; the full regression suite passes under that configuration.

Packaging, plugin configuration/structure, and binary compatibility verification
pass with the existing four deprecated and six experimental API notices. All
25 demo Java files compile against Wicket 10.11.0. `DefaultMethodsPage` has
**6 MATCHED** bindings and records all four consulted Java files. See
[the interface-default contract](interface-defaults.md). Real application
accuracy/performance evaluation remains deferred.

The refreshed graphical sandbox confirms all three Form scopes and their six
bindings, with all four Java dependencies and the HTML companion current. The
loaded JAR matches the verified distribution. Temporary developer Swing checks
make no source edits and are not distributed.

## Immutable provider-state verification (2026-10-29)

The full suite passes **687 tests**, with no failures, errors, or skipped tests.
The extension adds 33 provider-state/demo tests and eight editor tests. Coverage
includes independent allocation state, constructor assignment/delegation and
ordering, known injected dependencies, inherited/shadowed fields, literal origins,
dynamic IDs, mutable-state/escape/initialization guards, and recursive bounds.
Native checks cover constructor-argument references, rename, completion, coloring
recognition, possible-unpaired-ID ranges, shared origins, and reanalysis/freshness.

Packaging, plugin configuration/structure, and binary compatibility verification
pass with the existing four deprecated and six experimental API notices. All
22 demo Java files compile against Wicket 10.11.0. `ConfiguredPartsPage` has
**4 MATCHED** bindings and records four consulted Java files. See
[the provider-state contract](provider-state.md). Real application accuracy
and performance evaluation remains deferred.

The graphical sandbox confirms independent heading IDs and the delegated Form's
child, with all five Java/markup source versions current. The loaded plugin JAR
matches the verified distribution; temporary Swing checks make no source edits
and are not distributed.

## Proven source dispatch verification (2026-10-29)

The full suite passes **646 tests**, with no failures, errors, or skipped tests.
The extension adds 29 dispatch/demo cases and eight native editor/freshness cases.
Coverage includes final page/final method targets, inherited implementations,
private methods versus overrides, explicit superclass calls, constructor ordering,
known interface factories, generic bridge guards, source dependencies, and rejection
of unknown receivers, stateful helpers, unsafe initialization, and escaped fields.

Packaging, plugin configuration/structure, and binary compatibility verification
pass with the existing four deprecated and six experimental API notices. All
twenty demo Java files compile against Wicket 10.11.0. `DispatchPage` has
**4 MATCHED** bindings and records all four consulted Java files. See
[the source-dispatch contract](source-dispatch.md). Real application
accuracy/performance evaluation remains deferred.

The graphical sandbox confirms `DispatchPage`'s four bindings, including the Form
child and the selected override, with all five Java/markup source versions current.
The loaded sandbox JAR matches the verified distribution. Temporary developer
Swing checks make no source edits and are not included in the plugin.

## Queue/repeater verification (2026-10-29)

The full suite passes **609 tests**, with no failures, errors, or skipped tests.
The extension adds 50 cases across queue placement, logical item templates,
initialization, the demo, and native editor behavior. Coverage includes scoped
duplicate IDs, missing containers, Panel boundaries, nested ListViews, generated
RepeatingView IDs, symbolic foreach bodies, captured-container guards, custom
item-factory rejection, and unreviewed item-data uncertainty.

Packaging, plugin configuration/structure, and binary compatibility verification
pass with the existing four deprecated and six experimental API notices. All
sixteen demo classes compile against Wicket 10.11.0. `QueueRepeaterPage` has
**6 MATCHED** findings, including two inferred template-child bindings, and two
logical item groups with no invented runtime IDs/counts. See
[the supported queue/repeater contract](queueing-repeaters.md).
Real application accuracy/performance evaluation remains deferred.

The refreshed graphical sandbox confirms the queued container hierarchy, both
expanded item-template groups, and template-child HTML navigation. A repeated
navigation check retains a fresh `QueueRepeaterPage` snapshot. Temporary Swing
checks make no source edits and are not included in the plugin.

## Broader markup verification (2026-10-29)

The full suite passes **559 tests**, including 26 broader-markup/editor cases
and nine compiled-library resource cases. Coverage includes static Page/Panel/
FormComponentPanel headers, multi-level contributions, explicit variants,
conservative fallback, shared-Java editing guards, gutter/re-analysis context,
read-only JAR navigation, classpath ambiguity, and production/test isolation.
The quick-fix walkthrough resets mismatches in its fixture copies so using the
interactive demo does not break regression runs or discard saved demo edits.

Packaging, plugin configuration/structure, and binary compatibility verification
pass with the existing four deprecated and six experimental API notices. All
fifteen demo classes compile against Wicket 10.11.0. The `HeaderDetailPage` default
and explicitly opened French layouts each have **3 MATCHED** bindings across two
markup sources. See [the resource contract](markup-inheritance.md). Real
application accuracy/performance evaluation remains deferred.
The graphical sandbox confirms default/French selection, inherited-header
navigation, and restoration of default markup on a manual Java switch.

## Inspection quick-fix verification (2026-10-29)

The full suite passes **524 tests**, including 41 quick-fix tests and two further
custom-component isolation guards. Coverage includes compatible ID corrections,
root/nested counterpart creation, generated containers, repeated parent-local
IDs, composite scope preservation, source/type collisions, escaping, read-only
targets, stale actions, cross-file diff previews, native intention discovery,
and undo. The `QuickFixPage` fixture is repaired through all three action types
to produce **4 MATCHED** findings with no unpaired-ID inspection results.

Plugin packaging, configuration, structure, and IntelliJ binary verification
pass. The verifier retains the same four deprecated and six experimental API
usages. All thirteen demo Java classes compile against Wicket 10.11.0. See
[the quick-fix contract](inspection-quick-fixes.md). The repository demo
keeps its deliberate mismatches for manual exploration; real application
evaluation remains deferred.
The graphical sandbox confirms both directions of inspection fixes and eight
native diff previews, without applying edits to the demonstration sources.

## Custom input extension verification (2026-10-29)

The extended source-analysis slice passes **481 tests**, including 26 new custom
input/composite analysis and editor cases. The suite covers direct and reordered
input IDs, composite scope separation, preview exclusion, private final fields,
conversion and initialization, source inheritance, Java/HTML selection,
references, native rename, completion, inspections, and stale snapshots. Unknown
constructor/model effects, custom IDs, structural conversion, malformed markup,
and inline/caller-added children retain uncertainty.

Plugin packaging, project configuration, structure, and IntelliJ compatibility
verification pass. The verifier reports the same four deprecated and six
experimental API usages as the preceding build. The demo's twelve Java classes
compile against Wicket 10.11.0. See [coverage and limits](form-components.md).
The graphical sandbox confirms separate three-pair/one-pair reports, editor
following, outer-tag navigation, and class-level composite HTML navigation.
Real application evaluation remains deferred.

## Bounded source setup verification (2026-10-29)

The full regression suite passes **455 tests**, including 47 new source-analysis
and editor integration cases. Coverage includes helpers, repeated factories,
component/string parameters, source superclass constructors and initialization,
implicit constructor selection, forwarding/reordered/fixed custom IDs, inherited
private final fields, recursion/control-flow/dispatch/escape guards, source-file
limits, and deterministic read-only reports.
Two additional navigation regressions cover discarded clean documents and delayed
editor-selection events when opening inherited sources.

Editor checks cover native factory-call navigation, completion at empty call-site
IDs, paired rename of unique caller/field literals, shared-literal and base-class
rename guards, correct inspection ranges, and stale-report rejection after unsaved
edits to additional source dependencies. The combined `SourceSetupPage` fixture
has **7 MATCHED** findings; Maven compilation passes for all nine demo classes.
The graphical sandbox confirms the same seven matches, five tracked source files,
and Java/HTML navigation into inherited sources while retaining the derived page
context. The helper-added TextField opens at its original construction.

Packaging, project configuration, and plugin structure checks pass. Binary
verification remains **Compatible** with IDEA 262.10968.63, with the same four
deprecated/six experimental API usages from the existing tool-window factory.
Real application evaluation remains deferred.

## ID editing and color verification (2026-10-29)

The editor follow-up adds 30 real-PSI tests for native paired-ID rename, usage
preview and two-file undo, Go to Declaration in both directions, field-backed IDs,
repeated paths, sibling conflicts, escaped values, stale-pair rejection, native
completion/insertion, and configurable Java/HTML coloring. Text-search options
cannot expand paired rename into unrelated comments or display strings. Uncertain
scope and constructor semantics retain their existing conservative limits.
The full regression suite passes **406 tests**, with no failures, errors, or skips.

The graphical sandbox confirmed Wicket-orange ID values in Java and HTML,
Go to Declaration opening the HTML counterpart, and the native Rename dialog
accepting `page-heading.2` and opening a usage preview without applying it.
With a temporary empty HTML ID, Basic Completion suggested the unused Java ID
`javaOnly`; the Java popup offered the unused markup ID `markupOnly` alongside
IntelliJ's standard Java string suggestions. Temporary fixture edits and
completion settings were restored after the checks.

Packaging, project configuration, and plugin structure checks pass. Binary
verification against IDEA 262.10968.63 remains **Compatible**, with the same four
deprecated/six experimental API usages from the existing tool-window factory.

## Editor problem verification (2026-10-29)

The full regression suite passes **376 tests**, including 19 new inspection/gutter
fixtures. Coverage includes native Java/HTML weak-warning highlights, batch
inspection registration, path-sensitive matching, tracked-field construction
locations, multiline attribute anchors, suppression and profile disabling,
one icon per line, escaped tooltips, committed-document guards, paired-file cache
invalidation, companion deletion/recreation, and conservative unresolved cases.

Packaging, project configuration, plugin structure, and binary verification pass.
The verifier reports **Compatible** with IDEA 262.10968.63 and the same four
deprecated/six experimental API usages as before this slice.

The graphical demo confirmed `javaOnly` and `markupOnly` weak warnings in the
native Problems file tab, warning icons on their editor lines, and gutter-click
navigation to the page report. Temporarily pairing the IDs in HTML cleared the
Java warning and icon automatically; restoring the fixture restored the finding.
The fixture was restored and saved after the check. The temporary smoke agents
are outside the repository and are not included in the plugin.

## Class-level HTML navigation (2026-10-29)

The class row now offers **Go to HTML** for its resolved companion in both the
tree and text report. It opens the file at its beginning, including derived
extension files and inherited fallback markup. Missing/ambiguous pairing stays
disabled, and stale reports retain their navigation guard. Component matching
and exported root metadata are unchanged.

The full suite passes **357 tests**, with packaging, configuration/structure,
and binary compatibility verification passing.

## Native sidebar and editor-following slices (2026-10-29)

WicketLens now uses a left-hand sidebar with native toolbar/tree presentation,
semantic icons, type-ahead search, and optional details. Follow editor is enabled
by default after opening the view; Java and uniquely paired HTML editor switches
update the page automatically. Toggle it off to pin a snapshot. Text edits retain
stale-report guards until re-analysis.

The complete suite passes **352 tests**. Packaging, configuration/structure checks,
and binary verification pass with the recorded API warnings. The live sandbox
confirms automatic Java/HTML following, pinning/resuming, native controls, details,
search, and original source destinations. See the
[sidebar contract](sidebar-presentation.md) and
[acceptance record](acceptance.md). The updated sandbox remains running.

## Field-tracking slice (2026-10-28)

Added private final component fields initialized directly with `new` at declaration
or in one constructor, including container/child use across `onInitialize()`.
Fields share their original construction identity and source location; unsupported
uses and initialization effects retain explicit uncertainty.

The complete suite passes **323 tests**, including 35 field fixtures. Packaging,
configuration/structure checks, binary verification, and the six-class Maven demo
compilation pass. The verifier remains **Compatible** with the recorded API
warnings. The `FieldComponentsPage` fixture produces **6 MATCHED** findings.
See the [field contract](field-tracking.md) and
[acceptance record](acceptance.md). Relaunch through Gradle `runIde` to prepare
and load the updated sandbox plugin; this backend slice did not relaunch its UI.

## Markup inheritance and hierarchy slices (2026-10-28)

Added conventional Page/Panel markup inheritance, ancestor fallback, source-aware
merged parentage, and an expandable hierarchy with component details and source
navigation. The text report and deterministic JSON remain available. All
contributing markup files participate in stale-report checks.

The complete suite passes **288 tests**, including 16 inheritance and 13 hierarchy
UI fixtures. Packaging, configuration/structure checks, binary verification, and
the five-class Maven demo compilation pass. The verifier remains **Compatible**
with the recorded API warnings. The graphical sandbox confirms the new demo's
three matches, Expand/Collapse, selected-component details, and Java/HTML caret
destinations. See the [inheritance contract](markup-inheritance.md) and
[acceptance record](acceptance.md). Inherited Java setup remains unexpanded.

## Initialization coverage slice (2026-10-28)

Added conservative `onInitialize()` support for analyzed Page and Panel roots.
Constructor and hook setup share one graph; unsupported effects in either phase
keep the combined attachment scope unresolved. Thirty new real-PSI tests cover
the supported patterns, uncertainty guards, source locations, deterministic JSON,
and the six-match demo. The complete suite passes **259 tests**.

Packaging, configuration/structure checks, binary verification, and Maven demo
compilation pass. The verifier remains **Compatible** with the recorded API
warnings. See the [initialization contract](initialization-coverage.md) and
[acceptance record](acceptance.md). This backend slice did not relaunch the
graphical sandbox.

## Standard-component coverage slice (2026-10-28)

Added ten reviewed component classes, bringing constructor coverage to fifteen
classes. Twenty new fixture tests cover all 33 public overloads of the additional
types, constructor validity, anonymous class types, uncertainty, parent-aware
matching, source links, JSON, and the new demo. The full suite passes **229 tests**.

`StandardControlsPage` compiles and has **12 MATCHED** findings in its full-pipeline
test. Build, configuration, structure, and binary verification pass with the same
API warnings. The [constructor audit](constructor-coverage.md) records the
source review and limits. Restart the sandbox with the rebuilt plugin to try
the new page; this backend coverage slice did not relaunch the sandbox UI.

## Source-navigation slice (2026-10-28)

Added component/source-row selection, Java/HTML navigation, double-click/Enter
navigation, an out-of-date indicator, and **Re-analyze**. Stale offsets and JSON
copying are blocked. Re-analysis preserves the chosen class after offset changes;
another class is never silently selected after a rename.

The full suite now includes **209 tests**, including 19 navigation/staleness
fixtures. Plugin build, configuration, structure, and binary checks pass against
IDEA 262.10968.63 with the existing API warnings. See the
[acceptance record](acceptance.md#source-navigation-follow-up) for the details.
The final sandbox walkthrough confirmed both source destinations, stale controls
after a normal editor change, and recovery through re-analysis.

## Core review verification (2026-10-28)

The three review findings are fixed: analysis waits for committed Java/HTML
documents, nested/anonymous code cannot hide enclosing-root effects, and root-bound
method references are reported as unsupported scope effects. Affected constructor
attachments become unresolved, with source-located causes.

The rebuilt plugin passes **190 tests**, configuration/structure checks, and
binary verification against IDEA 262.10968.63 (**Compatible**, with the same 4
deprecated and 6 experimental API warnings). Twelve new regression/control tests
cover these fixes, unrelated receivers, and cancellation while waiting for a
document commit. See [the acceptance record](acceptance.md#core-review-follow-up)
for reproduction evidence. Restart the development sandbox to load the rebuilt
plugin; the graphical walkthrough below records the earlier slice 8/9 build.

## Slice 9 acceptance (2026-10-28)

The [acceptance record](acceptance.md) maps every specification criterion to
its evidence. Eight dedicated acceptance tests now run the required scenarios
through source selection, conventional pairing, composed Java/HTML analysis,
report formatting, and JSON export. They also check unchanged source text and
modification stamps, informational severity, matched source locations, and
byte-for-byte export stability across repeated analyses.

The final full suite passed **178 tests with no failures, errors, or skipped
tests**. Plugin build, configuration, and structure checks passed; Gradle reused
the compatible binary-verifier result for the unchanged production JAR. The
demo compilation passed, and the existing sandbox's report and copied JSON were
checked again. The installed sandbox JAR matches the packaged JAR.

Earlier slice results below are historical records of the functionality available
at that point; their references to a status dialog or future slices do not
describe the completed MVP.

## Slice 8 verification (2026-10-28)

- Full regression suite: **170 tests passed**, including 8 presentation/export,
  4 caret/file selection, and 6 background service/UI/clipboard tests.
- Export coverage checks schema fields, parent relationships, source locations,
  explicit nulls, uncertainty causes, escaping, cancellation, and canonical
  ordering when snapshot arrays are reordered. Presentation tests check readable
  hierarchy/status output and plain-text handling of source content.
- Service tests cover prepared snapshots, registered copy action, panel controls,
  superseding requests, cancellation, unsupported input without stale results,
  and listener disposal.
- `buildPlugin`, `verifyPluginProjectConfiguration`, and `verifyPluginStructure`:
  passed. The distribution contains only the WicketLens plugin JAR.
- `verifyPlugin`: **Compatible** with IDEA build 262.10968.63. It reports 4
  deprecated and 6 experimental API usages in generated forwarding methods for
  IntelliJ's `ToolWindowFactory` defaults; there are no binary compatibility
  errors. These warnings do not extend the verified target to future IDE builds.
- Demo Maven compilation passed. In the graphical sandbox, **Analyze Wicket
  Page** opened the report with **3 MATCHED**, **1 JAVA_ONLY**, and
  **1 MARKUP_ONLY**. The registered **Copy Wicket Context** action copied valid
  JSON; clipboard inspection confirmed the class, finding counts, `form` → `name`
  parent relationship, and matched Java/HTML source locations.
- The final sandbox check also confirmed that the status message stays visible
  on its own row in a narrow tool window.

The report is a static snapshot and must be re-run after edits. Rich source
navigation, automatic refresh, and real application evaluation remain deferred.

## Slice 7 verification (2026-10-28)

- Full regression suite: **152 tests passed**, including 22 uncertainty fixtures
  and 2 additional end-to-end pairing-cause tests, alongside the previous 128 tests.
- Coverage includes dynamic/factory/invalid IDs, source-located ancestor causes,
  missing and ambiguous markup, custom inherited setup, constructor delegation,
  alternative constructors, opaque root helpers, root escapes, anonymous bodies,
  queueing, overrides, control flow, removal/replacement, and recovered Java syntax.
- Additional cases cover parenthesized/cast root mutations, qualified `Outer.this`,
  recovered HTML boundaries, allowed HTML void/optional end tags, document-wide
  markup reasons, scoped unknown child IDs, and deterministic cause deduplication.
- Known local edges remain available when root attachment is uncertain; no tested
  unsupported scope produces definitive unmatched errors or stale source links.
- `buildPlugin`, `verifyPluginProjectConfiguration`, and `verifyPluginStructure`:
  passed.
- `verifyPlugin`: **Compatible** with IDEA build 262.10968.63.
- Distribution inspection confirms only the WicketLens plugin JAR is bundled.

All findings remain informational. Full interprocedural/runtime analysis and
markup inheritance resolution remain outside the prototype scope. The Tools
action is still a status dialog, and the sandbox UI was not relaunched.

## Slice 6 verification (2026-10-28)

- Full regression suite: **128 tests passed**, including 30 new matcher tests
  using actual Java and HTML/XML PSI snapshots with real Wicket fixture libraries.
- Coverage includes the eight specified matching scenarios: simple components,
  nested Forms, variables, Java-only IDs, markup-only IDs, dynamic IDs, unrelated
  `add` methods, and repeated IDs under different parents.
- Additional coverage checks wrong-parent IDs, unmatched subtrees, ambiguous
  ancestors and descendants, missing/cyclic parents, missing or mismatched markup,
  unsupported markup contexts, Panel preview exclusion, case/entity handling,
  source links, informational severity, cancellation, determinism, idempotence,
  and clearing stale links on rematching.
- `buildPlugin`, `verifyPluginProjectConfiguration`, and `verifyPluginStructure`:
  passed. The final prototype status text and plugin description reflect matching.
- `verifyPlugin`: **Compatible** with IDEA build 262.10968.63, including the final
  packaged status/description update.
- Distribution inspection confirms only the WicketLens plugin JAR is bundled.

The composed analyzer now produces matching findings and positive source links.
The Tools action remains a status dialog; the report UI and JSON export are still
planned for slice 8. The sandbox UI was not relaunched for this slice.

## Slice 5 verification (2026-10-28)

- Full regression suite: **98 tests passed**, including 28 new HTML/XML PSI
  extraction tests and 3 additional composed-report tests.
- Coverage includes owning elements and offsets, ordinary wrappers, nearest
  Wicket parents, repeated IDs in separate/same containers, quote styles, entity
  decoding, malformed/duplicate attributes, custom entities, comments and raw
  script/style content, Wicket constructs, headers, namespace limitations, Panel
  sections/preview markup, syntax errors, deterministic/read-only results, and
  cancellation.
- Composed-report tests verify extraction through the selected VFS URL, Panel
  context selection, diagnostic propagation, preserved Java nodes, and skipped
  extraction for missing or ambiguous companions.
- `buildPlugin`, `verifyPluginProjectConfiguration`, and `verifyPluginStructure`:
  passed.
- `verifyPlugin`: **Compatible** with IDEA build 262.10968.63.
- Distribution inspection confirms only the WicketLens plugin JAR is bundled.

No Java/markup matching is performed yet. The Tools action remains a status dialog,
and the sandbox UI was not relaunched for this core analysis change.

## Slice 4 verification (2026-10-28)

- Full regression suite: **67 tests passed**, including 27 pairing/composition
  tests and a separate two-module project fixture, alongside the previous 39 tests.
- Pairing coverage includes both supported layouts, WebPage/Panel classes,
  missing and ambiguous companions, multiple resource roots, production/test
  separation, dependency-module isolation, exclusions, source package prefixes,
  default packages, unsupported contexts, deterministic/read-only results, and
  cancellation. A repeated lookup observes newly created files without a stale
  cached result.
- `buildPlugin`, `verifyPluginProjectConfiguration`, and `verifyPluginStructure`:
  passed.
- `verifyPlugin`: **Compatible** with IDEA build 262.10968.63.
- Distribution inspection confirms only the WicketLens plugin JAR is bundled.

The composed analyzer populates the markup path without altering component nodes
or claiming ID matches. The Tools action is still a status dialog; the sandbox UI
was not relaunched for this core analysis change.

## Slice 3 verification (2026-10-28)

- `test`: **39 passed**: 3 foundation tests, 14 Java discovery tests, and 22
  hierarchy tests, all using the same real Wicket fixture libraries.
- Hierarchy coverage includes variables, both container attachment orders, deep
  nesting, repeated IDs under different parents, declaration identity across
  scopes, construction locations, deterministic node identity, reassignment,
  aliases/escapes, conflicting attachments, unattached containers, uncertain
  ancestors, and cycle removal.
- `buildPlugin`, `verifyPluginProjectConfiguration`, and `verifyPluginStructure`:
  passed.
- `verifyPlugin`: **Compatible** with IDEA build 262.10968.63.
- Distribution inspection confirms only the WicketLens plugin JAR is bundled.

The existing read-only and cancellation tests also pass. Interactive analysis and
markup pairing remain deferred to their planned slices; the sandbox UI was not
relaunched for this analyzer change.

## Slice 2 verification (2026-10-28)

- `test`: **17 passed** (3 foundation tests and 14 Java analyzer tests), using
  actual Wicket 10.11.0 libraries. Coverage includes pages/panels, indirect
  inheritance, literal IDs, reviewed constructors, varargs, method overloads,
  behavior/validator exclusion, unresolved expressions and scopes, source
  locations, deterministic/read-only results, and cancellation.
- `buildPlugin`, `verifyPluginProjectConfiguration`, and `verifyPluginStructure`:
  passed.
- `verifyPlugin`: **Compatible** with IDEA build 262.10968.63. Detailed reports
  are under `build/reports/pluginVerifier/`.
- Inspected the distribution ZIP: it contains only the WicketLens plugin JAR,
  with no Wicket or fixture dependencies bundled.

These checks verify the analyzer and packaging. The Tools action remains a status
dialog; interactive analysis, HTML matching, the full eight end-to-end fixture
scenarios, and real application evaluation are still pending their planned slices.

## Slice 1 verification (2026-10-28)

Verified locally using the installed SDKMAN Java 25 JDK:

- `test`: all **3 tests passed** (Java type resolution, HTML PSI, action registration).
- `buildPlugin`: plugin ZIP produced successfully.
- `verifyPluginProjectConfiguration` and `verifyPluginStructure`: passed.
- `verifyPlugin`: **Compatible** with IDEA build 262.10968.63.
- `runIde`: graphical welcome window appeared; the sandbox log confirms
  `Loaded custom plugins: WicketLens (0.1.0-SNAPSHOT)`.

The fixtures use the test process's actual JDK because the standalone framework
lacks the IDE source tree's mock JDK. Tests load WicketLens, Java, and their required
dependencies only: loading all bundled plugins exposed an unrelated Ultimate
startup extension failure. No platform errors are suppressed by the tests.

The verifier and sandbox emit warnings about unrelated bundled modules/plugins;
the verifier still completes successfully and the sandbox loads WicketLens.
The action's registration is tested automatically; interactive invocation of its
status dialog remains a manual check using the steps above.

## Further work

All nine implementation slices and the documented editor follow-ups are complete.
Real application evaluation remains deferred. Further possibilities are listed in
[spec.md](../spec.md#12-follow-up-features); they have not been scheduled.
