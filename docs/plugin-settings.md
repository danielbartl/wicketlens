# Plugin settings

Open **Settings → Tools → WicketLens** and use **Show text report (debugging)**
to enable or disable the Text report tab in the Components tool window.
It is disabled by default. Hierarchy and Dependencies remain available.

The preference is application-wide, saved by the IDE in the WicketLens component
of wicketlens.xml, and applies to open panels immediately after Apply. No restart
or reanalysis is required. New panels use the saved preference. Reset discards
unapplied checkbox changes; closing settings without applying does not change
report visibility. The page and checkbox are included in IDE settings search.

Hiding a selected Text report returns to Hierarchy. A selected Dependencies tab
stays selected when the text tab is inserted or removed. Settings notifications
are delivered to Swing on the UI thread, and disposed panels ignore queued updates.
Hierarchy selection/filter state, component details, completed analysis, JSON
copy/export and source navigation remain available. Existing text-navigation
checks explicitly enable the debugging view; normal-user views use the default.

This is a presentation preference. Report formatting, semantic analyzers,
matching, uncertainty, source snapshots, cancellation and JSON/MCP response
contracts are unchanged. MCP decision: no tool or response change is needed;
an agent does not gain new semantic facts or operations from a hidden debugging
tab. Existing read-only MCP queries remain independent of the UI preference.

Validation covers the registered native Settings page, default value,
Apply/Reset/disposal lifecycle, persistent-state XML round trips, updates to
existing and newly created panels, preserved dependency selection, active-text
fallback, background state loads, disposal of pending listeners and unchanged
analysis/export snapshots. Existing hierarchy, navigation, editor-following,
dependency and service checks cover both normal and explicitly enabled debug
views. Packaging includes searchable options and plugin structure/compatibility
verification. This development change does not install or publish the plugin.


Settings-search metadata is packaged in the IDE-generated searchable-options JAR.
The release evidence gate now accepts only that versioned, metadata-only index
beside the production code JAR: the manifest is restricted, index bytes must match
the generated output, and entries must be valid text-index JSON. Extra libraries,
classes, nested archives and unrelated resources remain rejected. Production
class/resource identity and artifact/verifier receipt checks remain in force.


Verification: **135 focused UI/navigation/service checks** and **30 packaging-gate
checks** passed with zero failures/errors/skips. Native settings registration,
persistence and live updates are covered. The packaged settings search index
contains the checkbox. Plugin configuration, structure and compatibility checks
passed against IU-262.10968.63. The full normal suite and semantic corpora were not
rerun for this presentation change. Source, artifact and verifier receipts agree.
See [the evidence](plugin-settings-evidence.json).
