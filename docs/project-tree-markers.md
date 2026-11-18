# Project tree icon markers

A small orange dot on the existing class icon marks Wicket pages, panels and
components in the native Project tree. Class/file names have no added labels.
IntelliJ retains its own Java glyphs, text styling, comments and tooltips.

The badge identifies physical Java source classes whose resolved inheritance
includes `org.apache.wicket.Component`. This includes Page, Panel,
FormComponentPanel, custom components, abstract bases and named nested classes.
A Java file node uses its filename-matching primary class, or its sole top-level
class. An unrelated main class does not inherit a badge from a helper. Ordinary
Java classes, HTML files, dependency classes and directories remain unmarked.

The decorator wraps the existing icon with IntelliJ's native BadgeIcon and a
small BadgeDotProvider shape. The shape and its transparent border fit inside
the icon bounds, so the original dimensions remain unchanged. JBColor supplies
Wicket orange in light mode and a brighter orange in dark mode. Existing icon
layers remain underneath; repeated decoration does not add another badge.

No component graph or companion lookup runs for the tree decoration. It does
not depend on a complete report, so OatMobileLayout and DataTablePage are still
recognizable even when their initial layouts remain unresolved. The badge is
class inheritance information, not an analysis status or severity indicator.

The existing physical-source/project checks, indexing guard and cancellation
remain in force. No PSI or role cache is retained. Native refreshes use current
inheritance and can remove the badge after a superclass edit. Node presentation
is the only mutation; source files and semantic snapshots remain unchanged.

MCP decision: no change. This is a graphical replacement for the text labels,
not a new semantic fact, binding, agent operation or response field.

Acceptance adapts the existing native class/file tree-node and renderer checks:
custom bases, pages/panels/components, nested/primary/helper classes, unrelated
HTML/library nodes, source stamps, superclass changes, indexing and cancellation.
The original icon remains inside the native badge with the same dimensions;
text fragments, names, locations and tooltips remain identical. Real Oat fixture
copies check six classes on Wicket 10.11.0 and 11.0.0, with no Wicket label text.

The offscreen renderer preview is `build/reports/project-view/preview.png`.
Headless fixtures cache empty platform glyphs, so that preview explicitly reloads
the platform's class SVG while retaining the decorator's actual badge paint and
shape. This is a native renderer fixture preview, not a live installation or a
graphical release-readiness claim.

Verification: **75 targeted tests** and **22 release-checker unit tests** passed,
with zero failures/errors/skips. Both read-only Oat corpora (Wicket 10.11.0 and
11.0.0) passed the six real Project tree badge checks without added text and the mobile/DataTable
report reproductions. All 36 prior page summaries, source-template results,
completion probes and four saved text reports remain unchanged. All 318
application inputs retain their hashes. The native renderer fixture preview reloads the standard class SVG in headless
mode and was visually inspected; no live installation or release-readiness claim is made.
Configuration, structure and compatibility checks passed against IU-262.10968.63:
`Compatible. 7 usages of deprecated API. 6 usages of experimental API`. The targeted test/build/verification run took 1m 33s.
Source, test and artifact receipts match the development ZIP (0.1.0 metadata).
The full suite and actual MCP host were not repeated for this presentation-only
change; their preceding baseline is recorded separately. See
[the verification evidence](wicket-oat-project-marker-evidence.json) for hashes.
