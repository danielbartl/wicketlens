# Native sidebar presentation

WicketLens is a dedicated tool window styled with IntelliJ's standard UI
components. Its default anchor is the left edge and its stripe uses a dedicated
outlined **W** icon. The SVG has light/dark variants and native 20-pixel regular
and 16-pixel compact New UI sizes, following the platform's
[icon conventions](https://plugins.jetbrains.com/docs/intellij/icons.html#new-ui-tool-window-icons).
It keeps its own report lifecycle and does not replace Java's
built-in Structure view. Existing projects retain saved tool-window placement;
the native Move to menu can dock it on the left.

The custom icon was checked in the graphical sandbox after restart; its **W** is
distinct from the Structure button. Packaging, project configuration, and plugin
structure checks pass with all six SVG variants and the New UI mapping bundled.

## Interaction

- Native icon toolbar: Re-analyze, Follow editor, Cancel, Go to Java, Go to HTML, Copy Wicket
  Context, Expand all, Collapse all, and Show component details. Standard tooltips
  expose action names and descriptions. Presentations and handlers check current
  report/selection state, including staleness, on the EDT.
- Class-level HTML navigation opens the selected companion file at offset zero
  in both the tree and text report. Derived classes open their own extension file
  when present or the inherited fallback companion otherwise. Missing/ambiguous
  pairing stays disabled. Content uncertainty does not prevent opening a known
  file, and the class root is not turned into a matched markup component. Stale
  reports retain the same navigation block as component rows.
- Native tree presentation: class icons for roots/containers, field icons for leaf
  components, tag icons for markup findings, folders for groups, and information
  icons for diagnostics. IDs are regular text; type and matching status are muted
  fragments. Icons/colors/fonts follow the IDE theme. No source label is rendered
  as HTML, and labels retain escaped line breaks.
- Type-ahead search: standard IntelliJ tree speed search matches component IDs and
  types, traverses collapsed branches, and reveals the selected result.
- Follow editor: enabled by default once the sidebar is opened, remembered per
  project, and switchable to pin the current report. Selecting another Wicket Java
  file or uniquely paired HTML source replaces the snapshot. Unrelated files and
  closing all editors clear the old page. The follower uses background smart,
  committed reads, coalesces rapid switches, and cancels obsolete work. No editor
  following runs before the report view has been opened or after it is disposed.
- Optional details: hidden initially for a compact sidebar, toggled from the
  toolbar, and displayed using a native splitter. Selection information includes
  status, confidence, parent, sources, and causes. The Text report tab preserves
  the existing full report and source links.
- Freshness: stale reports remain readable/searchable. Navigation and copying
  remain blocked; details and expansion stay available. Re-analysis replaces the
  tree without reusing old selected-source offsets.

The small footer reports progress, match count/partial scope, copying feedback,
and out-of-date state. Its tooltip preserves the full service message. Analysis
can start explicitly or from an editor-file switch and always waits for indexing
and committed documents. Text edits alone mark reports stale until re-analysis.

HTML selection is mapped back through module-scoped Java filename lookup and the
existing forward companion resolver, preserving package/root and production/test
separation. Missing, ambiguous, non-Wicket, unconfigured, and custom layouts do
not reuse an unrelated page's snapshot. Native WicketLens source navigation keeps
its current page context, including inherited markup, and does not trigger a
follow/re-analysis loop. Manual tab selection can follow that source's own class.

## Implementation and evidence

The presentation uses public IntelliJ APIs: `SimpleToolWindowPanel`,
`ActionToolbar`, `ColoredTreeCellRenderer`, `AllIcons`, `TreeSpeedSearch`,
`OnePixelSplitter`, and `JBTabbedPane`. Explicit value-only row metadata supplies
names, types, row kinds, and statuses; the renderer does not parse formatted
report strings. No PSI is retained in presentation rows, and the JSON schema is
unchanged. Toolbar actions replace the earlier Swing text buttons.

Platform guidance: [tool windows](https://plugins.jetbrains.com/docs/intellij/tool-windows.html),
[tool-window UI](https://plugins.jetbrains.com/docs/intellij/tool-window.html), and
[Structure extensions](https://plugins.jetbrains.com/docs/intellij/structure-view.html).
The dedicated sidebar preserves Wicket's partial value snapshots and freshness
guards while following active files independently of Java's own Structure model.

The hierarchy fixture suite additionally checks action presentations, native
icons and rendered fragments, optional details, collapsed-branch search, text-tab
actions, and stale action guards. Separate editor-target and following suites
exercise initial activation, Java/HTML switches, Panel targets, pinning/resuming,
unrelated files, closing editors, rapid switching during pending commits,
cancellation, stale edits, disposal, and inherited-source navigation. Existing navigation, service, snapshot, field,
inheritance, and acceptance tests remain in the full suite. Graphical smoke-test
results are recorded in the acceptance record after sandbox verification.
