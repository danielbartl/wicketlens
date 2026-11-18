# Constructor-owned initial dialog content

The twenty-third milestone extends source-declared initial content to a selected
source dialog subclass that installs its own private-final message component.
It addresses OatConfirmDialog without interpreting its later ask/confirm actions.

## Identity and selected construction

The opt-in declaration identity trace now permits an unqualified, non-anonymous,
zero-argument allocation of the compiled Wicket Model in a non-ID delegation
argument. The model constructor and generic resolution must be available. The
String ID must still forward unchanged through the selected constructor chain.
Anonymous models, constructor arguments, arbitrary data constructors, conditional
IDs and transformed IDs retain their previous restrictions. This extends ID
provenance only; strict constructor isolation and editing authority are unchanged.

A unique selected structural constructor declares the inherited owned form and
its plain content placeholder. Wrapper constructors may then initialize one
private final instance field directly with a fresh framework Label or plain
WebMarkupContainer carrying the guarded content ID, and pass that exact field to
the guarded source setter. The source field has one initialization across its
source file, no declaration initializer, and no competing placement. Its allocation
must be directly visible in the selected wrapper constructor.

The installed declaration replaces the placeholder in the inferred graph.
Constructor overload/delegation sources, the message allocation, the inherited
setter and the constructor placement retain their physical source locations.
Existing placementLocations metadata records the constructor call. There is no
new response member or phase. Inherited markup is composed by the existing
companion/fragment analyzers.

Setter calls are resolved against the selected source host, including virtual
calls made during superclass construction and calls through a base-typed alias.
A source override must preserve the existing direct super forwarding and return
its receiver. Foreign receivers do not acquire host dispatch. Wrong IDs, mutable
or public storage, borrowed/aliased content, custom component allocations,
anonymous component initialization, populated placeholders, unsupported constructor
statements and competing constructor/page placements withhold the entire graph.

## Phase and uncertainty

The result remains SOURCE_DECLARED_INITIAL_CONTENT / INITIAL_DECLARATIONS. Every
binding is inferred and protected from paired rename. Constructors, configuration,
models and callbacks are not executed. Source callback locations include the
message's onConfigure and the subclass's source Ajax hook overrides. Visibility,
model values, runtime presence, later open/close/replacement state, pending actions
and save/delete/business outcomes remain unknown.

Known direct setup calls into event hooks still withhold declarations. Recognizing
a message assignment still checks its direct model-argument source calls; recovering
the component declaration does not skip those eager effects. Detached placeholder
fields do not retain identities pointing to removed graph nodes. A retained
PARTIAL_ANALYSIS diagnostic identifies constructor-owned content when later page
setup fails. This is a source declaration note, not a partial initial graph or a
new usable component selector. The page's complete declaration graph is withheld
until its remaining setup also meets the existing contracts.

For the pinned Oat checkout, DataTablePage now passes its OatConfirmDialog at
Java offset 3767. Its next stop is the AbstractColumn constructor delegation in
OatSelectionColumn.java at offset 5192. The primary DataTable hierarchy remains
unresolved. Both previously available toolbar contexts retain four inferred
bindings each. A fixture-only page using the actual OatConfirmDialog source has
24 inferred matches, with a Label at confirmation:dialog:form:content and one
constructor placement. It does not change the application checkout or its 36-page
corpus.

## MCP decision and acceptance

Extend the existing page-structure, validation and component-context behavior
through the shared native analyzer and value snapshots. Agents can inspect the
same initial-content metadata, source locations, inferred bindings and retained
blocking diagnostics. No tool, parameter, selector, response field or schema
version is added. Tool descriptions and client semantic documentation describe
the additional declaration pattern.

The existing 12-level constructor/configuration/identity bounds, 128-child host
bound, shared 20,000-step/96-source/256-instance/16-call setup limits and 512-KiB
whole-response limit apply. Field scans and dispatch checks participate in source
freshness, cancellation and discovery budgets. Bound exhaustion withholds the graph.
All PSI state is invocation-local and tools remain read-only.

Full-suite verification exposed a document-readiness gap in the shared asynchronous
request path: requests could reach capture with editor documents still pending.
An explicit check inside the constrained read now keeps these requests waiting
under the existing deadline and cancellation/project-lifetime handling. It does
not commit or save documents. Synchronous capture still rejects pending edits;
post-serialization freshness checks and all response contracts are unchanged.
The existing multi-view document-wait, timeout, cancellation and route-lifecycle
tests cover this correction without weakening their assertions.

Acceptance covers constructor content, source setter forwarding and virtual
dispatch, nonfirst IDs, unknown callback effects, existing editing guards,
rejection cases, project/module isolation, constructor/callback/setter snapshot
freshness, mid-inspection cancellation and response bounds. An independent real
WicketTester process observes the constructor-installed Label, deferred rendering
and an action consumed once after an Ajax confirmation. Those runtime observations
supply no action, model or visibility values to static reports. Both pinned Oat
versions exercise the actual source component through native and MCP views.

Final milestone verification: **3,117** normal tests,
**27** actual MCP host tests and **22** release-checker unit tests passed
with zero failures/errors/skips. The 249 focused regression tests passed.
Both read-only Oat corpora (Wicket 10.11.0 and 11.0.0) exercise the actual
OatConfirmDialog through a fixture-only probe: 24 inferred bindings, a constructor
message Label and one constructor placement, shared by native and all three MCP
views. DataTablePage and CustomerPage advance to the next explicit stops while
retaining their unresolved initial hierarchies. All 36 primary finding/binding
summaries, previous template/completion groups, six graphical Project tree markers,
four saved text reports and 318 application input hashes are preserved.
Configuration, structure and binary compatibility checks passed against
IU-262.10968.63: `Compatible. 7 usages of deprecated API. 6 usages of experimental API`. The full run took 25m 21s.
Current source/test/artifact receipts match the ZIP. See the
[milestone evidence](wicket-oat-confirmation-content-evidence.json) for counts and hashes.
The ZIP retains 0.1.0 metadata; no installation or publication was performed.
