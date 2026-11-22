# Retrieve Wicket navigation contexts

The thirty-seventh development milestone adds the read-only
`wicket_get_navigation_contexts` tool. It uses the same bounded native navigation
analyzer and value snapshots as `wicket_get_page_structure` with
`includeNavigationContexts=true`. It omits the primary page graph and retrieves
one existing context projection per selected request. Published 0.1.0 has nine
tools; this development build adds a tenth tool. Existing tool defaults and
response schemas are unchanged.

## Requests and responses

First request an index:

```json
{"className":"dev.jbaby.wicket.oat.app.DataTablePage"}
```

The host supplies the same `projectPath` argument as for the other Wicket tools.
`moduleName` selects an exact module when necessary; `sourceSet` is `main` by
default, with `test` and `any` also supported. The response retains the version-1
MCP envelope and has `tool="wicket_get_navigation_contexts"`. Its
`navigationContexts` member contains:

- `mode="index"`, an opaque `snapshotId`, `contextCount`, and all descriptors from
  the bounded native discovery;
- each descriptor's `contextId`, class/module/source set, relationship, Java
  location, markup path/suffix, scope completeness, and scope/binding counts;
- the existing discovery completeness, diagnostics, lifecycle diagnostics, and
  source dependencies.

The index contains no primary graph, scope projections or binding projections.
A relationship such as `CANDIDATE` remains a discovery hint, not a confirmed
attachment. Counts describe source scopes and observed/inferred bindings, never
runtime rows or component counts.

Pass one returned ID with the same target selection:

```json
{
  "className":"dev.jbaby.wicket.oat.app.DataTablePage",
  "contextId":"nav:<64 lowercase hexadecimal characters from the current index>:<ordinal>"
}
```

Copy the actual ID; the example is a placeholder. A selected response has
`mode="context"`, the same `snapshotId` and total `contextCount`, and one item in
`contexts`. That item is the **exact existing native/MCP navigation projection**
with `contextId` added: warnings, source/lifecycle/template provenance, scopes,
bindings, findings, markup locations, and any existing ancestor/child projections
are retained. The selected projection's scope is explicit; deliberate selection
is not reported as truncation.

Index and selected responses retain the **512-KiB whole-response byte limit**.
An individually oversized context or oversized index metadata returns
`RESPONSE_TOO_LARGE`; no graph is silently shortened. There is no server-side
snapshot cache, cursor, runtime component lookup or new editing authority.

## Freshness and uncertainty

Every call uses the existing refresh, smart/committed-read, deadline,
cancellation, project-lifetime and post-serialization freshness checks. It
recomputes native discovery. IDs are bound to the selected project, class,
module/source set, source/binary/SDK/root/PSI version and discovered context
identities. They are opaque selectors, not authorization tokens or durable IDs.

After edits, indexing/root changes, SDK/archive changes, another target selection,
or reopening/changing projects, refresh the index. A valid ID for another
snapshot/target produces `STALE_CONTEXT_SELECTION`. Malformed IDs produce
`INVALID_REQUEST`; a valid current token with a nonexistent ordinal produces
`CONTEXT_NOT_FOUND`. Ordinary existing errors such as `STALE_SNAPSHOT`,
`UNCOMMITTED_DOCUMENTS`, `INDEXING`, `TIMED_OUT`, and `PROJECT_UNAVAILABLE` retain
the shared request pipeline's behavior. Execute waits for committed documents and
smart mode under the same deadline; direct fixture capture requires them already.

`discoveryComplete` and diagnostics preserve native discovery limits. The
version-1 envelope's `truncated` remains true when discovery/population coverage
exhausts its bounds, including on a selected response. Retrieving every descriptor
cannot turn partial source discovery into complete runtime coverage. Model values,
callback execution, visibility, validation/business outcomes, runtime IDs,
private contents and later state remain unknown as in the underlying reports.

## Rank 11 response fields

These fields appear when the source analysis establishes the corresponding
declaration. The request arguments and schema version remain the same.

| Tool response | Field | Evidence |
| --- | --- | --- |
| Page structure or component context | `analysis.components[].attachmentOrigins` or `context.component.attachmentOrigins` | Equivalent `ADD` and `QUEUE` attachment sites, with the receiver node ID and source location. |
| Page structure | `analysis.components[].componentType` | A reviewed custom `ListView` item uses its concrete source type in the existing item template. Constructor-local scalar assignments and comparisons affect matches and findings through the existing graph. |
| Selected `FORM_TEMPLATE` context | `bindings[].component.formConfiguration`, `formTemplate.conversions` | Declared required settings and validator registrations, plus child-to-composite conversion links. |
| Selected `ACTION_TEMPLATE` context | `actionTemplate.recordChanges` | Source `ADD_RECORD` or `REMOVE_RECORD` operation, with the helper, storage and collection call locations. |
| Selected `SELECTION_TEMPLATE` context | `lifecycle.selection.transitions` | Possible `NEXT` or `BACK` edge, with the target registration index and handler and guard locations. |
| Selected `TREE_TEMPLATE` context | `treeTemplate.childSubtree` | Framework subtree class and source node/content factory locations for reviewed `NestedTree` declarations. |
| Injected dependencies | `dependencies.owners[].fields[]` or project-wide `dependencies.fields[]` | `lookup: "GUICE"` and `injectorRegistration` for a reviewed explicit registration. A client that enumerates lookup values should accept `GUICE`. |

A component context can include the following attachment evidence.

```json
{"context":{"component":{"wicketId":"title","attachmentOrigins":[
  {"kind":"ADD","receiverNodeId":"java:root","location":{"file":"/project/src/main/java/example/Page.java","offset":132}},
  {"kind":"QUEUE","receiverNodeId":"java:root","location":{"file":"/project/src/main/java/example/Page.java","offset":156}}
]}}}
```

Fetch a `SELECTION_TEMPLATE` descriptor from the index, then pass its
`contextId` to get source edges such as this one.

```json
{"navigationContexts":{"mode":"context","contexts":[{
  "relationship":"SELECTION_TEMPLATE",
  "lifecycle":{"selection":{"registrationIndex":0,"transitions":[{
    "direction":"NEXT","targetRegistrationIndex":1,
    "handlerLocation":{"file":"/project/src/main/java/example/Flow.java","offset":254},
    "guardLocation":{"file":"/project/src/main/java/example/Flow.java","offset":279}
  }]}}
}]}}
```

Paths and node IDs above are illustrative, and the examples show excerpts.
The same selected context metadata is available through
`wicket_get_page_structure` with `includeNavigationContexts=true`. Source
edges, conversion and validation declarations, and Guice registration leave
runtime state and outcomes unknown. See the [Guice response example](mcp-injected-dependencies.md#explicit-guice-registration).

## Application result and MCP decision

The original DataTablePage's combined page-plus-all-navigation structure response
still exceeds the cap. The new index lists **seven contexts**; each can now be
retrieved separately. On Wicket 10.11.0 the index is about 11 KiB and the largest
selected response about 96 KiB. Acceptance checks exact byte counts on both pinned
Wicket versions and compares every selected item with the unchanged native
projection after a fresh capture. It also verifies that all primary page reports,
earlier contexts/completion/probes, markers, saved reports and original application
source hashes are preserved.

MCP decision: add a focused tool because agents need bounded access to already
available separate scopes without receiving an oversized primary-plus-context
bundle. Reuse `WicketNavigationContextsAnalyzer`, the shared request lifecycle,
`ReportVersion`, and the existing context exporter. There is no new semantic
analyzer or alternate binding rule. Existing structure, validation and component
context contracts remain unchanged. Tool descriptions, client allowlists and
setup documentation describe the development-only tool.

Coverage includes native/projection equality, legacy responses, all context kinds
in the application corpus, project/module/source-set/target selection, source and
binary/root freshness, hot cancellation and shared project lifecycle, Unicode byte
limits, oversized individual contexts, read-only output and actual-host discovery,
selection and stale-selector errors.

Final milestone verification: **99 focused tests**, **41 actual MCP host tests**,
both read-only 36-page Wicket corpora and plugin configuration/structure/binary
compatibility checks passed with zero failures/errors/skips. All seven original
DataTablePage contexts were retrieved after fresh captures and exactly matched
native projections. Wicket 10.11.0: index 10,788 bytes, largest selected context 97,880 bytes; Wicket 11.0.0: index 10,782 bytes, largest selected context 97,874 bytes. Every response is below 524,288 bytes.
All 36 primary reports, earlier context/completion/probe facts, six markers, five
saved reports and the original 318 application inputs are preserved. Compatibility
against IU-262.10968.63: `Compatible. 7 usages of deprecated API. 6 usages of experimental API`. Current source/host/artifact
receipts match the development ZIP. The full normal suite was not rerun; no current
full-suite or release-readiness claim is made.
See the [milestone evidence](wicket-oat-navigation-retrieval-evidence.json).
No installation, signing or publication was performed.


## Conditional confirmation flow

Navigation discovery can add a separate `CONFIRMATION_TEMPLATE` for one reviewed
source ask-site consumer. Its optional `submissionTemplate.confirmation` records
ask/slot/capture/clear/guard/accept and cleanup locations, plus source data-helper
calls. Initiating dispatch, current registration, slot presence, cleanup
reachability and mutation/business outcomes stay unknown; callbacks are not
executed. Retrieve it with the existing navigation index and selected-context
operation. Default primary views and existing submission metadata retain their
contracts. See [the bounded protocol](confirmation-templates.md).


Confirmation metadata can also include `selectionClears` for the reviewed source
selection helper. It records its helper/guard/getter/model-read/collection-clear
and same-owner Ajax-refresh locations. Column dispatch, model implementation and
value, collection identity/mutability, clear/refresh outcomes and later selection
remain unknown. No helper, model or callback is executed. The member is omitted
from existing row-confirmation contexts. See
[the source declaration contract](selection-clear-declarations.md).


Source constructor callbacks can add `ACTION_TEMPLATE` contexts with a separate
`actionTemplate` object. A reviewed List.replaceAll loop, equality declaration,
canonical record enum-component substitution and shared feedback/Ajax/selection
helper retain their source locations. Registration/storage, visibility, UI
dispatch, row values, loop counts, equality/enum effects and business outcomes
stay unknown. No callback, helper, predicate or constructor is executed;
`uiTrigger="not_reconstructed"` supplies no runtime button/row ID or Form link.
Existing initial, submission and confirmation views keep their contracts. See
[the record-action declaration contract](record-action-callbacks.md).


Plain-page initial attachments beside a reviewed fresh source action registrar
can expose optional `storedActionRegistrations` metadata: allocation, registration,
method, callback and private slot locations. Only direct storage without invocation
is proved. These bindings remain `INFERRED`, grant no rename authority, and keep
active registration, dispatch, visibility, later tree state and business outcomes
unknown. The existing separate `ACTION_TEMPLATE` protocol is unchanged. See the
[storage and attachment contract](stored-action-attachments.md).


The same `storedActionRegistrations` shape now supports up to 64 direct storage
registrations per fresh local after every local use passes a complete group
audit. Shared allocation/slot locations identify source sites; they do not imply
concurrently active callbacks or reveal the currently stored callback. A later
eager call, alias or escape withholds the group. Sibling edits invalidate the
snapshot and its action selectors. Existing per-context limits and the 512-KiB
response cap still apply. See [the repeated-registration contract](repeated-action-registrations.md).


Standalone fluent chains now use the same complete registrar-group audit. Every
link must store the callback and return its exact receiver. The 64-site limit
counts links across all uses. Each chain entry's `registrationLocation` points to
its method-name token; action contexts reuse that location from the primary
snapshot. Other metadata shapes and standalone-call locations are unchanged.
Active callback/dispatch state and outcomes remain unknown; bindings remain
inferred with no rename authority. See [the chain contract](chained-action-registrations.md).


A constructor local may now be initialized by `new Registrar().addAction(...)`
under the same storage/receiver-return protocol. Initializer links and every
later use share one complete audit and the 64-site bound. Initializer entries
point to individual method-name tokens, including single-link initializers;
allocation locations retain the direct new expression. Existing value shapes,
confidence, freshness, response caps and unknown active callback/dispatch state
are unchanged. See [the initializer contract](initialized-action-registrations.md).
