# WicketLens — Technical specification v0.1

## 1. Objective

Determine whether IntelliJ PSI provides a practical foundation for Wicket-aware static analysis. This is a technical spike, not a production-grade plugin.

## 2. Supported environment

- IntelliJ IDEA
- IntelliJ Platform Gradle Plugin 2.x
- Kotlin plugin implementation
- Java Apache Wicket project analysis
- Classes extending WebPage or Panel
- Conventional same-package/same-basename Java and HTML pairing
- No dependency on WicketForge

## 3. Data model

Each component contains:

- nodeId: stable within one report
- wicketId: literal ID or null
- componentType: Java type
- parentNodeId: parent or null
- javaLocation: file and offset
- markupLocation: file and offset, if matched
- confidence: CONFIRMED, INFERRED, UNRESOLVED
- diagnostics: codes and messages

Page metadata includes schema version, qualified class name, markup path, warnings, and component nodes.

## 4. Java PSI discovery

Required patterns:

```java
add(new Label("title", "Hello"));

Form<?> form = new Form<>("form");
form.add(new TextField<>("name"));
add(form);

Label heading = new Label("heading", model);
add(heading);
```

Expected nesting:

```text
Page
├── title : Label
├── form : Form
│   └── name : TextField
└── heading : Label
```

Unresolved when the called code or ID provenance cannot be established:

```java
add(factory.create("header"));
add(new Label(config.getId(), model));
add(createHeader("header"));
```

Treat these as unresolved, not errors.

The later [bounded source setup pass](docs/source-setup.md) can now expand closed
source helpers/factories, source superclass constructor/initialization chains,
and custom constructors whose ID forwarding is provable. It preserves literal
origins, distinct factory instances, source dependencies, and conservative editor
rename guards. Unknown branches, recursion, unsafe dispatch, and unavailable source remain
unresolved; this is not arbitrary Java execution.
The [dispatch extension](docs/source-dispatch.md) additionally resolves final-page/
final-method targets and known instance factories, including source interface
calls, while preserving construction order and dependency snapshots. The
[provider-state extension](docs/provider-state.md) adds per-allocation private final
configuration and known constructor-injected dependencies with literal ID origins.
Source interface defaults are now supported through audited class/default
precedence, most-specific selection, and explicit interface-super calls; see
[the default-method contract](docs/interface-defaults.md).
Closed source provider factories now preserve fresh allocations, configuration,
and ID origins, with generic return checks and source-version guards; see
[the factory contract](docs/provider-factories.md).
Read-only source accessors can also return existing known providers and simple
String/primitive getter values. Nested reads retain immutable configuration and
literal ID origins, with separate purity and initialization checks; see
[the accessor contract](docs/source-accessors.md).
Proven primitive boolean conditions now select bounded `if`/`else` and ternary
paths, short-circuit operands, and guard returns. Mutable or unknown conditions
remain unresolved; source accessor purity and factory freshness checks still
apply. See [the conditional contract](docs/conditional-setup.md).
Reviewed integral arithmetic/comparisons, String concatenation, and known String
content equality now resolve more values and conditions. Computed IDs retain
whole-expression provenance; shared calculation inputs have conservative editing
guards. See [the scalar-expression contract](docs/scalar-expressions.md).

Post-MVP constructor coverage includes fifteen reviewed Wicket component classes.
See the [constructor audit](docs/constructor-coverage.md) for the exact class set,
the 33 overloads added in the standard-component slice, and retained uncertainty
rules. This constructor slice does not add field, helper/factory, or callback execution
analysis.

A further post-MVP slice supports straight-line additions and method-local
components in actual `onInitialize()` overrides on the analyzed Page or Panel.
The method must begin with a reviewed `super.onInitialize()` delegation. Both
phases share the graph, while unsupported initialization effects make combined
attachments unresolved. Unsupported fields, inherited setup, arbitrary helper effects, and
other lifecycle execution remain unexpanded. See the
[initialization contract](docs/initialization-coverage.md).

The field-tracking follow-up binds private final instance fields declared on the
selected class to direct `new` initializers at declaration or in one supported
constructor. Fields can connect constructor and `onInitialize()` additions while
retaining original construction identity, source, and container edges. All reads
in the source file must stay in supported additions on the analyzed instance.
Aliases, escapes, setters, captures, inherited/mutable/static/non-private fields,
alternative constructors, and unsupported initialization effects retain
uncertainty. See the [field contract](docs/field-tracking.md).

Important:
- Resolve Wicket add methods rather than matching every Java add call.
- Confirm component constructor semantics before interpreting string arguments as IDs.
- IDs are local to their parent container, not globally unique.
- Do not report definitive mismatches when the component hierarchy is uncertain.

## 5. HTML analysis

Use IntelliJ HTML/XML PSI to extract wicket:id attributes, owning elements, nesting, and source locations. Preserve ordinary HTML containers when reconstructing nesting. Treat unsupported inheritance and Wicket-specific markup as unresolved.

Post-MVP markup inheritance supports conventional source-based Page/Panel
ancestor fallback and a single `wicket:extend` → empty `wicket:child` slot per
level. Preserve locations in every contributing source and recompute merged
parentage/uniqueness. Inherited Java setup remains unexpanded; ambiguous, missing,
malformed, and unsupported chains stay unresolved.

## 6. Matching diagnostics

Implement:

- MATCHED
- JAVA_ONLY
- MARKUP_ONLY
- UNRESOLVED_DYNAMIC_ID
- UNRESOLVED_PARENT
- UNRESOLVED_MARKUP

Report Java-only and markup-only findings as informational unless the analyzed scope is demonstrably complete. Avoid false definitive errors.

## 7. User interface

Provide an Analyze Wicket Page action showing results like:

```text
CustomerPage
├── title : Label             MATCHED
└── form : Form               MATCHED
    └── name : TextField      MATCHED

Diagnostics:
- [INFO] dynamicHeader: unresolved factory component
```

Include source locations and markup pairing.

Provide Copy Wicket Context, exporting deterministic structured JSON for future AI integration. No external network requests.

Post-MVP follow-up: report component/source rows support Java/HTML navigation.
Stale snapshots remain visible but cannot navigate or copy outdated context.
Re-analyze refreshes the same selected Java class; analysis does not automatically
run after edits. A later follow-up adds an expandable hierarchy, component detail
pane, Expand/Collapse controls, and source navigation from tree rows. The text
report remains available. Stale trees remain readable with navigation/copy blocked.

The sidebar presentation uses a left-docked WicketLens tool window with native
IntelliJ tree icons, muted type/status fragments, an icon action toolbar,
type-ahead search through collapsed branches, and an optional details pane.
It remains independently accessible alongside the IDE's Java Structure view.
Existing saved window positions are respected. Follow editor is enabled by
default after the view is opened, follows active Wicket Java/uniquely paired HTML
files, and clears unrelated snapshots. It can be disabled to pin the current
report. File switches cancel obsolete analysis; edits alone mark snapshots stale
until re-analysis. Source navigation preserves the selected page context.
The first filtering slice adds a persistent ID/type/path search field, combined
finding-status selection, and retained ancestor context. Markup-only children use
their matched containing hierarchy. Controls survive reanalysis and page switches;
filtering applies to the visible tree while text/JSON retain the complete report.
See [the hierarchy-filtering contract](docs/hierarchy-filtering.md).
The second hierarchy slice adds subtree focus, next/previous component-finding
selection, and expansion/selection/focus restoration across reanalysis. Unique
raw ID/type/parent paths identify rows; dynamic or ambiguous targets are not
guessed. Per-page contexts isolate files/classes/markup variants. See
[the exploration contract](docs/hierarchy-exploration.md).

## 8. Suggested architecture

- analysis/WicketJavaAnalyzer
- analysis/WicketMarkupAnalyzer
- analysis/WicketPageResolver
- analysis/WicketGraphMatcher
- model/
- actions/
- ui/
- export/

Avoid unnecessary persistent indexes and application-wide state.

## 9. Test fixtures

1. SimplePage: static Label and matching HTML.
2. NestedFormPage: nested Form and TextField.
3. VariablePage: component variable then add(variable).
4. MissingMarkupIdPage: Java ID missing from complete markup scope.
5. MarkupOnlyPage: HTML ID without Java counterpart in complete scope.
6. DynamicIdPage: dynamic constructor ID reported unresolved.
7. NonWicketAddPage: unrelated add method ignored.
8. RepeatedIdsPage: same ID under different parents allowed.

Use actual PSI fixture tests where possible. Assert graph structure, diagnostics, and deterministic JSON.

## 10. Acceptance criteria

- [x] Build succeeds.
- [x] Sandbox IDE launches.
- [x] Static Java patterns are recognized.
- [x] Nested HTML wicket:id values are found.
- [x] Matching is parent-aware.
- [x] Dynamic cases do not produce definitive false errors.
- [x] Analyze Wicket Page is available in the IDE.
- [x] JSON context can be copied.
- [x] Eight fixture scenarios are tested.
- [x] README documents run commands and limitations.

Verified for the supported prototype subset on 2026-10-28. See the
[acceptance record](docs/acceptance.md) for test mappings, commands, results,
compatibility limits, and remaining exclusions.

## 11. Feasibility gate

Evaluate 5–10 representative pages from a real Wicket application. Target at least 90% correct reconstruction within the explicitly supported subset, without false definitive error diagnostics.

Status: **deferred by agreement; not evaluated**. The completed fixture acceptance
does not establish this real application accuracy target.

## 12. Follow-up features

Implemented after the MVP: native unpaired-ID inspections and gutter icons,
paired-ID rename with preview/undo, direct editor navigation, parent-aware ID
completion, and configurable Wicket-orange editor coloring. See the
[editing contract](docs/developer-guide.md#editing-wicket-ids) for their conservative limits.
The bounded source follow-up additionally supports closed helpers/factories,
source superclass setup, and provable custom constructor ID forwarding; see
[the source contract](docs/source-setup.md).
Custom form components and `FormComponentPanel` have bounded ID/setup support
with separate composite markup scopes. Inspection quick fixes now correct
compatible unpaired IDs and create supported Java/HTML counterparts, with native
preview and undo; see [the quick-fix contract](docs/inspection-quick-fixes.md).
Broader markup support adds static header contributions, explicitly opened
filename variants, and read-only dependency layouts, with conservative fallback
and editing guards; see [the markup contract](docs/markup-inheritance.md).
Component queueing and ListView/RepeatingView item templates now have bounded
static support, with explicit uncertainty for placement and population outside
the reviewed subset; see [the queue/repeater contract](docs/queueing-repeaters.md).

Reviewed source `static final` String/integral/boolean constants now supply IDs,
immutable configuration, and conditions, with source dependencies and conservative
editing/initialization guards; see [the constant contract](docs/static-constants.md).

Remaining possibilities (not scheduled):

- Further Java analysis: unknown-branch flow merging, mutable state, broader scalar/compiled-constant support, and broader injection models
- MCP adapter
- AI-assisted WicketTester generation
- AI-assisted component generation
- Runtime component inspection
