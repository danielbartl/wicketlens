# ID origin-use audit and paired rename

This sixth development milestone adds an origin-use proof for source-layout IDs
and independently selected named members. Navigation still has a wider supported
scope than editing. A matched pair alone does not authorize a rename.

## Literal-use contract

The shared semantic analyzer follows a literal declared in the selected class's
constructor, reviewed `onInitialize`, or private final instance component-field
initializer. It checks every direct use through local String aliases and resolved
source parameters. Constructors and exact private/static/final or explicit-super
calls can forward the ID. All uses must reach exactly one reviewed framework
constructor's ID argument. It does not search for equal string text or change
unrelated occurrences. The proof covers explicit literal/alias/parameter uses;
arbitrary runtime lookups and implicit property-model semantics are not inferred.

Model values, resource keys, display text, selectors, computations, field storage,
method receivers, additional component identities, shared implementation literals,
scalar-return helpers and unsupported forwarding paths withhold the proof.
Mutable alias assignments cannot bypass the audit. Conditional, computed,
branch-merged, generated and repeated-template origins remain guarded. Each
source constructor/helper examined joins snapshot freshness dependencies.

The audit permits 8,192 visited steps, 256 references per origin, 12 forwarding
levels and 48 source dependencies per report. Cancellation propagates. Exhaustion
withholds all promotions for that report and adds an explicit informational
partial-analysis diagnostic; it does not remove existing navigation bindings.

## Editor contract

The existing native preview-first two-file operation retains its further gates:
one Java origin and one pair, complete conventional own markup, unambiguous
findings without unresolved scope causes, no locale/style variants, no project
inheritors and writable counterparts. The target recalculates analysis and
eligibility before either write, then checks sibling collisions and ID syntax.
Pending document edits prevent mixed snapshots. Native commands own undo.

An isolated named member can edit its own binary-name companion. Named-class
enumeration must be complete. Any indexed source use outside that declaration
withholds editing: caller/inheritor discovery remains navigation-only and cannot
authorize a multi-context write. The member-reference check stops at 256 uses.
Shared Fragment definitions, projected caller origins and repeated templates do
not acquire a shared rename operation. Additional lookup strings, property models
and localization resources are not automatically rewritten.

## Wicket Oat acceptance

The six `ProductFields` wrappers retain confirmed navigation bindings. Their
constructor IDs also reach `BaseOatField`'s `StringResourceModel` key and default
label, so the origin audit withholds paired rename. The application's implicit
property-model and localization contracts need a separate refactoring design;
changing two ID tokens would not establish that those uses remain correct.

Isolated decorated-component literals and private forwarding helpers can gain
paired rename when the editor gates also hold. Existing shared/inherited markup
and opaque-content guards remain in force. No application files are changed by
the corpus or audit, and no broader CRUD/wizard/tree lifecycle support is added.

## MCP decision

Reuse existing `components[].idRenameSafe` facts in structure and component
context, plus existing diagnostics and source-version snapshots. This flag
records origin eligibility (true may be omitted from JSON); native editor guards still determine whether an edit
is available. It is not a writable capability or a runtime-validity assertion.
Validation findings and graph confidence remain unchanged. No new tool, request
parameter, response field or schema version is needed; all MCP tools remain
read-only. Tool descriptions and client documentation explain this distinction.

Focused tests cover identity-only positives, non-ID uses, shared origins,
independent nested selection, caller refusal, native preview/undo, read-only
counterparts, stale helpers, variants, inheritors, collisions, cancellation and
bounds. MCP tests compare native/exported flags and bindings, check unsaved
freshness and read-only behavior, and isolate conflicting source classes between
modules. Existing project-isolation and response-limit tests cover the unchanged
request pipeline. See the [evidence summary](wicket-oat-origin-evidence.json) for
final verification results and development ZIP details.
