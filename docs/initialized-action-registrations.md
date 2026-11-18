# Registrar initializer chains

A constructor local can now be initialized by a direct fresh allocation followed
by reviewed storage-only registration calls, for example:

    Actions<Row> actions = new Actions<Row>().addAction(...).addAction(...);

The initial graph retains inferred attachment declarations, and supported source
callbacks retain independent ACTION_TEMPLATE contexts. An initializer-only local
needs no later references. A single registration in an initializer also works.
Explicitly typed and var locals use the actual allocation and method contracts;
analysis does not infer arbitrary factory/alias receiver identities.

The existing storage analyzer locates the complete initializer, its direct new
allocation and its named local in the selected page's sole constructor. Every
initializer link must assign the unchanged reviewed BiConsumer to the actual
owner's private callback slot and return this. The existing Object ancestry,
empty zero-input constructor, slot and literal-label guards remain in force.
All later references to the local join that same audit. Later statements are
allowed only after the initializer completes and must themselves be reviewed
registrations. An alias, reassignment, dispatch or capture invalidates the group,
including earlier initializer links. Distinct locals keep distinct allocations.

An initializer cannot reference its own incomplete local, be wrapped in an opaque
call, use a borrowed/factory/cast receiver, or initialize a field under this local
protocol. Anonymous allocations, extra constructor effects, eager callbacks and
links returning another object remain withheld. Bare allocation chains without
a named local remain outside this slice. Callback business bodies retain their
separate source-action checks and do not run during initialization analysis.

The shared 64-site limit counts initializer links and later registrations
together. Existing work/source bounds and cancellation checks still apply. Only
a complete group enters the session-local cache; cancellation or a failed later
use cannot publish or cache an initializer prefix. Initial bindings remain
INFERRED with no rename authority. No constructor, registration, helper, model
or callback is executed by analysis.

Existing storedActionRegistrations values retain their unchanged shape. Each
initializer entry points to its method-name token, including a single-link
initializer; allocationLocation points to the direct new expression. Callback,
slot and method locations retain their existing meanings. Source action contexts
reuse the exact registration locations from the primary snapshot. Earlier direct
and standalone-chain locations and contracts remain unchanged. These declarations
never establish the active callback, UI dispatch, visibility, model/data values,
later tree state or business outcomes.

MCP decision: extend existing structure/component and separate action-context
facts through the same semantic storage analyzer and value snapshots. Allocation
and per-link source locations help an agent trace a fluent initializer and its
later uses without executing it or manufacturing runtime UI state. No new tool,
argument, schema version or response member is needed. Reuse the consumer and
attachment protocols, exact export, freshness, cancellation, project/module
selection, response bounds and read-only host. Tool descriptions and client docs
explain initializer support. Editing the allocation, a link, the return/storage
contract, a later use or target markup invalidates snapshots and selectors;
rejected source dependencies remain tracked. The 512-KiB response cap remains.

Support for a bare allocation chain used directly as a constructor statement
is deferred. The current audit relies on a named local to identify the allocation
and its later uses. Field initialization, arbitrary factories and broader storage
protocols also remain unresolved. The next planned work is
[page-local Java/HTML navigation and completion](../spec.md#page-local-navigation-and-completion).


## Initializer-registration verification

**778 focused checks**, **48 actual MCP host tests**, both read-only
36-page Wicket corpora and plugin configuration/structure/binary compatibility
checks passed with zero failures/errors/skips. The added initializer fixture retains two
distinct method-token storage sites, one fresh allocation, one inferred table binding and two independent
inferred action contexts on Wicket 10.11.0 and 11.0.0. All previous corpus facts,
36 initial reports, nine saved text reports and the original 318 application
inputs are preserved. Runtime fixture dispatch changes when initializer order
is reversed, while analysis keeps the active callback and outcomes unknown.
The combined 64/65-site boundary, canceled initializer audit, stale allocation/link/return selectors,
project/module isolation, response caps and read-only behavior are covered.
Compatibility against IU-262.10968.63: `Compatible. 7 usages of deprecated API. 6 usages of experimental API`.
Source, host and artifact receipts match the development ZIP. The full normal
suite was not rerun; no full-suite or release-readiness claim is made. See the
[milestone evidence](wicket-oat-initialized-action-evidence.json). No installation,
signing or publication was performed.
