# Bounded declaration scan reuse

The thirty-sixth milestone lets the complete original DataTablePage finish its
initial source declaration analysis within the existing 20,000-step budget.
The earlier stop occurred in inherited menu setup, but a temporary profile found
12,249 of the first 20,000 steps in initial-content declaration scans. Overlapping
source regions and syntax trivia consumed work before menu construction finished.
No application source or analysis limit is changed.

## Source structure, separately from effects

`WicketInitialContentDeclarations` uses a session-local `WicketSourceDescendants`
index. A complete preorder traversal records semantic elements and subtree ranges.
The walking visitor's `elementFinished` callback establishes each range after its
children have been visited. Nested classes, lambdas, assignments, references,
allocations, guards and callbacks remain in the snapshot. Whitespace, punctuation,
identifier tokens and comments are excluded; the expressions containing identifiers
and operators remain available to the existing semantic checks.

A later request for a recorded subtree returns its existing list view. When a
larger enclosing scope is requested after a smaller scope, the completed child
snapshot is reused in the new preorder list. Elements are unique, as in the old
linked-set traversal. Partial or cancelled scans publish no snapshots. Queries,
including cache hits, call the existing work/deadline/cancellation hook. A new
semantic traversal charges that same hook for its elements. Oversized semantic
bodies still exceed the existing bound.

Only immutable declaration structure is reused, inside one analyzer session under
its existing committed read. Receiver identity, constructor execution, field state,
configuration effects, callbacks, method results and model values are never cached.
Repeated hosts still receive distinct component scopes. Existing source tracking,
constructor ownership, nested-write, guard, initialization and escape checks remain
in force. New analyses build fresh indexes, so source edits and different projects
or modules cannot reuse earlier PSI snapshots.

The 20,000-step/96-source, call/depth/instance, MCP deadline and 512-KiB response
limits remain unchanged. This adds no runtime evaluation or broader component
mutation permission.

## Application and MCP results

The unmodified original DataTablePage now has **26 MATCHED / INFERRED** components
and **9 UNRESOLVED_BRANCH** findings. Its initial declaration setup completes with
one confirmation-dialog content host and 49 tracked source dependencies. The
sidebar's leaf/group population contexts remain separate. Runtime rows, callback
execution, models, visibility, toolbar IDs/private contents and later state remain
unknown. The existing protection against paired rename of inferred declarations
is retained. This is a partial static declaration graph, not a running-page state.

MCP decision: the existing structure, validation and component-context tools benefit
from the more complete shared report automatically. Reuse their semantic analyzer,
value snapshots, existing schemas and descriptions; no new tool, parameter,
selector or response member is needed for a performance improvement. Native and
all three default MCP views agree and retain source freshness and read-only checks.
The implementation does not add a new semantic protocol to tool descriptions.

The optional combined response for DataTablePage **with all navigation contexts**
now exceeds the existing 512-KiB cap. It returns `RESPONSE_TOO_LARGE`, without
truncating a graph or changing the cap. Native navigation contexts and their shared
value projection remain intact; smaller existing MCP fixtures still cover those
contexts. Default structure, validation and component-context requests for the
page fit. Bounded retrieval of these larger navigation results is the next blocker.
Acceptance records that limitation explicitly rather than treating an internal
projection as a successful wire response.

Dedicated tests cover semantic traversal completeness, parent/child reuse,
failed-scan publication, hot/cold cancellation and budgets, large documentation,
large semantic bodies, separate same-named PSI classes, distinct repeated hosts,
nested competing writes, native/MCP equality, unsaved-source invalidation and
read-only output. Existing module, runtime, response-bound and actual-host suites
remain applicable.

Verification in the main workspace: **199 focused tests**, **39 actual MCP host
tests**, both read-only 36-page Wicket corpora and plugin configuration/structure/
binary compatibility checks passed. The 14 new tests cover declaration reuse and
integration guards. The original 318 application files remain unchanged. Binary
verification against IU-262.10968.63: `Compatible. 7 usages of deprecated API. 6 usages of experimental API`.
The full normal suite was not rerun; no current full-suite or release-readiness
claim is made. See the [milestone evidence](wicket-oat-declaration-work-evidence.json).
The change is integrated and verified in the main workspace. No installation or
publication was performed.
