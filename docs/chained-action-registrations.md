# Fluent source action registration chains

Standalone fluent registration chains now preserve a plain page's inferred table
attachment and independent source action contexts. A chain starts at the same
unchanged fresh constructor local used by the existing storage-group protocol.
Each link must resolve to a reviewed method on that exact source class, assign
its unchanged BiConsumer parameter to its own private callback slot, and return
this. Analysis neither invokes registrations nor executes callbacks.

The complete use group is audited before publishing or caching any member. A
registrar local can have multiple standalone chains and individual registration
statements. Every use must ultimately form a direct statement in the selected
page's sole constructor after the separate allocation. Links are followed only
through direct method-call qualifiers; casts, aliases, indirect factories and
arbitrary expression receivers do not gain identity. Returning null or another
object, invoking a callback, inserting an unreviewed bridge/query/dispatch,
escaping a chain result as an argument or alias, or capturing the registrar in a
callback withholds the group. Unsupported business bodies remain separate from
safe storage declarations and retain the existing action-context checks.

The 64-site bound counts every registration link across all uses of a local,
not just the number of times its name appears. Existing work/source/cancellation
and per-action discovery bounds remain unchanged. Failed or canceled chain
audits cannot publish or cache a prefix. Only complete groups enter the existing
session-local cache; no PSI references enter reports or MCP results.

Each accepted link retains the unchanged storedActionRegistrations shape.
Shared allocation/slot locations identify the same source registrar. For a chain,
registrationLocation points to the individual method-name token so nested call
expressions with the same start offset remain distinguishable. Callback locations
also remain distinct. Source action contexts reuse these exact registration
locations from the primary value snapshot. Earlier standalone registration
locations and contracts remain unchanged.

All matching bindings are INFERRED with no rename authority. Receiver identity
is a source construction fact, not an active UI callback/dispatch claim. Active
registration, callback reachability, visibility, model/data values, later tree
state and business outcomes remain unknown. The runtime fixture demonstrates
that a specific single-slot implementation dispatches only its stored callback,
and reversing chain order changes its outcome; analysis describes both source
callbacks without choosing an active one or inventing a UI trigger.

MCP decision: extend existing structure/component facts and separate source action
contexts through the shared storage analyzer and primary value snapshots. Exact
per-link locations and inferred attachment targets let an agent trace fluent
source registrations without running application code. Reuse existing consumer,
attachment and callback analyzers, exact exporters, freshness, project/module
selection, cancellation, response limits and read-only host tools. No new tool,
argument, schema version or response member is required. Tool descriptions and
client docs describe chains and token locations. Editing a link, return contract,
registrar use or target markup invalidates the snapshot and its context selectors;
rejected source declarations remain freshness dependencies. The 512-KiB cap
continues to cover initial metadata and selected action responses.

Registrar-local initializer chains are covered by the
[follow-up contract](initialized-action-registrations.md). Bare allocation
statements without a local, field initialization and arbitrary factory receivers
remain unresolved.


## Chained-registration verification

**745 focused checks**, **47 actual MCP host tests**, both read-only
36-page Wicket corpora and plugin configuration/structure/binary compatibility
checks passed with zero failures/errors/skips. The added fluent fixture retains two
distinct method-token storage sites, one inferred table binding and two independent
inferred action contexts on Wicket 10.11.0 and 11.0.0. All previous corpus facts,
36 initial reports, nine saved text reports and the original 318 application
inputs are preserved. Runtime fixture dispatch changes when chain order
is reversed, while analysis keeps the active callback and outcomes unknown.
The exact 64/65-link boundary, canceled chain audit, stale link/return selectors,
project/module isolation, response caps and read-only behavior are covered.
Compatibility against IU-262.10968.63: `Compatible. 7 usages of deprecated API. 6 usages of experimental API`.
Source, host and artifact receipts match the development ZIP. The full normal
suite was not rerun; no full-suite or release-readiness claim is made. See the
[milestone evidence](wicket-oat-chained-action-evidence.json). No installation,
signing or publication was performed.
