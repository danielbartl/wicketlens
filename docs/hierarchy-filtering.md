# Hierarchy filtering and search

The Hierarchy tab has a persistent search field and a finding-status selector.
They filter the current snapshot's rows while retaining ancestor containers.

## Controls

- **All** shows every row when the search is empty.
- **Unpaired** shows Java-only and markup-only findings. **Java only** and
  **Markup only** select either side separately.
- **Unresolved** shows unresolved component/markup findings and unresolved
  diagnostic rows. The routine partial-snapshot notice and logical repeater item
  templates do not count as unresolved findings by themselves.
- **Matched** shows matched component findings.

Search is case-insensitive literal substring matching against IDs, short or
qualified component types, and colon-separated hierarchy paths. Space-separated
terms must all match. For example, `form:address:postcode` finds that path, while
`form postcode` can find the same component with two terms. A container path also
matches descendants whose paths include it. Search and status selection apply
together. The existing tree type-ahead search remains available on visible rows.

The controls retain their values for the lifetime of the open view, including
reanalysis and page switches. The hierarchy expands filtered branches so results
are visible. Clear the search with its normal clear button and choose **All** to
return to the complete tree.

## Parent context and results

Ancestor rows remain visible in muted text even when they do not match the
selected status or query. The matching-row count excludes those context rows.
Context rows remain selectable and retain their original details and source links.
An empty result shows the page root and **No matching rows**.

Markup-only children beneath a matched Java/HTML container now appear beneath
that container in the hierarchy. For example, an unpaired `postcode` can appear
under `Page → form → address`. Root-level markup findings retain their existing
markup group. Unattached and diagnostic groups remain when needed by matching rows.

A selected row stays selected while it remains visible. Hiding it clears its
details and source actions. Clearing filters restores the previous visible
expansion state for that report. Reanalysis retains the controls and builds a
fresh filtered tree. The [exploration follow-up](hierarchy-exploration.md) also
restores expansion, selection, and focus through unique logical row addresses.

Filtering is a presentation of the current snapshot. The **Text report** and
**Copy Wicket Context** continue to cover the complete page. Filter changes do
not run analysis, change findings, or modify the exported JSON schema. A stale
snapshot remains searchable, with source navigation and copying blocked by the
existing freshness guards.

## Evidence and remaining work

`WicketHierarchyFilterTest` covers status/query composition, types and paths,
ancestor retention, repeated IDs, empty results, and immutable projections.
`WicketHierarchyFilteringViewTest` covers native controls, actual markup parent
context, selection/details/navigation, full-report copying, reanalysis, stale
snapshots, page switches, and cancellation. Existing hierarchy/presentation tests
remain part of verification.

The [exploration follow-up](hierarchy-exploration.md) adds subtree focus,
next/previous finding navigation, and safe view-state restoration across reanalysis.
Real application accuracy/performance evaluation remains deferred.
