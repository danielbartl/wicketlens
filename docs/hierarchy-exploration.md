# Subtree focus, finding navigation, and view state

The Hierarchy tab can focus a branch, step through component findings, and restore
its view after reanalysis. These controls build on [hierarchy filtering](hierarchy-filtering.md).

## Focus a branch

Select a branch and use **Focus subtree**. Its descendants and containing path
remain visible, with a **Focused:** label above the tree. Search and status filters
continue to apply within that branch, using full component paths. Even an empty
filtered result retains the focused row and its parent context.

Use **Show full hierarchy** to clear focus while keeping the search and status
selection. A nested branch can become the new focus. Branches are identified from
the full snapshot, so a container can be focused even when its children are
currently hidden by a filter. The page root already represents the full scope.

## Step through findings

**Next finding** and **Previous finding** select and reveal unpaired or unresolved
component/markup rows in displayed tree order. They respect search, status, and
focus. Collapsed branches can be revealed by navigation. Matched components,
context-only ancestors, diagnostic messages, groups, and the page root are skipped.
Traversal wraps; without a selection, Next starts at the first finding and
Previous at the last.

With the tree focused, **F2** and **Shift+F2** perform the same selection actions.
They update the selected details without automatically opening an editor. Use
Enter, double-click, or the Java/HTML actions to open the selected row's source.
Exploration actions are disabled in the Text report tab. A stale snapshot remains
explorable, while source navigation and copying retain the existing freshness guards.

## Restore the view safely

Expansion, selection, and subtree focus are remembered through reanalysis,
including the temporary empty/loading state. Up to 16 recent page contexts are
kept for the open view's lifetime, separated by Java class, source file, and markup
variant. Returning to a page can restore its view without applying another page's
focus or selection. Search and status remain the view's current controls.

Restoration uses unique raw IDs, qualified types, and containing paths, rather
than source offsets or escaped display labels. Current report rows supply all
restored source links. Status changes and container/leaf presentation changes do
not by themselves change a component's address. Logical repeater templates have
their own identity domain.

Dynamic IDs, duplicate addresses, and descendants of ambiguous/unknown parents
are not guessed. Removed or changed ID/type/path targets are left unselected;
lost focus returns to the full hierarchy. Markup rows becoming Java component
rows are also treated as a different role. Explicitly collapsed branches remain
collapsed when restoring the same controls. Changing controls reveals matching
branches normally, and clearing filters restores the saved unfiltered expansion.

These are view operations over a snapshot. Analysis findings, the Text report,
and Copy Wicket Context still cover the complete page; the JSON schema is unchanged.
State records contain value keys rather than PSI elements or old source targets.

## Evidence

`WicketHierarchyStateTest` covers logical addresses, offset/status changes, raw
versus escaped IDs, duplicate/unknown ancestry, template roles, and removed targets.
`WicketHierarchyExplorationViewTest` covers focus and filters, tree-order traversal,
keyboard selection, stale snapshots, fresh Java/HTML locations after reanalysis,
expansion, lost targets, page isolation, exports, and literal UI text. Focused
projection cases are also in `WicketHierarchyFilterTest`.

Both documented hierarchy slices are implemented. Real application accuracy and
performance evaluation remains deferred.
