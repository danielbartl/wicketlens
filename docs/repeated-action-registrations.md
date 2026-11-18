# Repeated source action registrations

A fresh constructor-local registrar can now appear in multiple direct registration
statements. Initial table attachments remain inferred declarations, and separately
reviewed record-action callbacks retain independent ACTION_TEMPLATE contexts.
Registration count never means that all callbacks are active at runtime.

The shared storage analyzer enumerates every reference to that exact local in
the selected page class. Every reference must be the receiver of a direct source
registration statement after its allocation in the same sole constructor. The
existing fresh Object-based class, empty constructor, one private uninitialized
slot, literal label, unchanged BiConsumer and exact slot assignment/return checks
apply independently to every call. Different source methods may use the same
slot if each method independently satisfies that contract. Different fresh
locals retain distinct allocation identities even when they share a class/slot.

The complete group is checked before any member is returned or cached. A later
eager call, alias, reassignment, callback capture, nested/conditional registration,
indirect callback or extra effect invalidates the group. Failed or canceled
checks publish no partial group. Only complete results enter a session-local
cache; no PSI or project objects enter the exported values. The existing shared
work/source bounds and cancellation checks apply, with a maximum of 64 storage
sites per local. Existing action-context discovery limits remain independent.

Stored callback bodies remain outside initial execution analysis. An unsupported
business body can therefore retain its storage declaration while withholding its
own action context; an independent supported sibling can still be described.
All attachment bindings remain INFERRED and idRenameSafe=false. No callback,
helper, predicate, model or constructor is executed by analysis.

Existing storedActionRegistrations entries retain their unchanged shape. Shared
allocationLocation and slotLocation identify source registration sites using the
same registrar. Callback and registration locations distinguish the sites.
Neither their array order nor a shared slot establishes the active callback,
active UI registrations, dispatch, visibility, later tree state or business
outcomes. A runtime fixture demonstrates that reversing two registrations changes
which callback its particular single-slot implementation dispatches; analysis
continues to describe both source declarations with explicit unknown outcomes.

MCP decision: extend the facts already returned by wicket_get_page_structure,
wicket_get_component_context and existing navigation projections. Repeated source
sites and their independent action contexts help an agent trace alternatives
without claiming simultaneous UI actions or executing application code. Reuse
the shared storage/consumer analyzers, initial attachment guard, value snapshots,
exact export, module/project selection, freshness, cancellation, response caps
and read-only host. No new tool, argument, schema version or response member is
needed. Update tool descriptions and client docs for the expanded semantics.
Both successful and rejected sibling source declarations remain dependencies;
editing any sibling invalidates the snapshot and its action selectors. The
512-KiB whole-response cap applies to metadata and selected action responses.

Standalone fluent chains are covered by the
[follow-up contract](chained-action-registrations.md), including exact per-link
receiver-return checks and method-token locations. Chained local initializers,
aliases, arbitrary dispatch and broader storage protocols remain unresolved.


## Repeated-registration verification

**716 focused checks**, **46 actual MCP host tests**, both read-only
36-page Wicket corpora and plugin configuration/structure/binary compatibility
checks passed with zero failures/errors/skips. The added fixture retains two
source storage sites, one inferred initial table binding and two independent
inferred action contexts on Wicket 10.11.0 and 11.0.0. All previous corpus facts,
36 initial reports, nine saved text reports and the original 318 application
inputs are preserved. Runtime fixture dispatch changes when registration order
is reversed, while analysis keeps the active callback and outcomes unknown.
The exact 64/65-site boundary, canceled sibling audit, stale sibling selectors,
project/module isolation, response caps and read-only behavior are covered.
Compatibility against IU-262.10968.63: `Compatible. 7 usages of deprecated API. 6 usages of experimental API`.
Source, host and artifact receipts match the development ZIP. The full normal
suite was not rerun; no full-suite or release-readiness claim is made. See the
[milestone evidence](wicket-oat-repeated-action-evidence.json). No installation,
signing or publication was performed.
