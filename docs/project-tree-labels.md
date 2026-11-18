> Superseded: the current UI uses a graphical orange dot on the class icon,
> without text labels. See [Project tree markers](project-tree-markers.md).
> The label implementation and verification below are a historical development record.

# Project tree labels

Java source class/file nodes in IntelliJ's Project tree show an orange role label:

| Resolved inheritance | Label |
| --- | --- |
| `org.apache.wicket.Page` (including WebPage) | Wicket Page |
| Panel or FormComponentPanel | Wicket Panel |
| Other `org.apache.wicket.Component` subclasses | Wicket Component |

Custom base classes, abstract classes and named nested classes are included.
For a Java file node, the class matching its filename supplies the role; otherwise
only a single top-level class is accepted. A helper class cannot label an unrelated
primary class. Expanded class nodes receive their own roles. This applies to Java
source content, including tests. HTML files, dependency classes, directories and
ordinary Java classes keep their normal presentation. Names alone are insufficient.

The label sits beside the normal class name. Existing icons, text fragments,
location comments and tooltips are preserved. The orange has separate light/dark
colors. No report must be opened or analyzed first. A class such as OatMobileLayout
can be identified as a Wicket Page even when its component setup is unresolved.

The registered `projectViewNodeDecorator` uses the same resolved PSI inheritance
mechanism and framework type boundaries as the analyzers, without running a page
analysis or looking for companion markup. It retains no PSI or role cache. Native
tree refreshes read the current inheritance; labels are withheld while indexing
or if indexes become unavailable. Cancellation propagates. The operation changes
only the node presentation and never writes source files.

MCP decision: no change. This is presentation-only classification of existing
class inheritance, not a new binding, runtime claim, analysis operation or agent
response field. No analyzer, report selector, schema, tool or MCP description changes.

Acceptance uses the registered extension through native ClassTreeNode/PsiFileNode
updates, not a mock classification function. Checks cover custom bases, raw Page,
Panel/FormComponentPanel and ordinary components, primary versus helper classes,
nested classes, unrelated names/HTML/library nodes, preserved presentation and
source stamps, repeated decoration, superclass edits, indexing and cancellation.
The native renderer is also checked and painted into an offscreen fixture preview
at `build/reports/project-view/preview.png`. That preview is not a live installation
or graphical release-readiness result. Real Oat fixture copies check pages,
OatMobileLayout, OatDialog and OatMeter on both pinned Wicket versions.

Verification: **75 targeted tests** and **22 release-checker unit tests** passed,
with zero failures/errors/skips. Both read-only Oat corpora (Wicket 10.11.0 and
11.0.0) passed the six real Project tree role checks and the mobile/DataTable
report reproductions. All 36 prior page summaries, source-template results,
completion probes and four saved text reports remain unchanged. All 318
application inputs retain their hashes. The native renderer fixture preview was
visually inspected; no live installation or release-readiness claim is made.
Configuration, structure and compatibility checks passed against IU-262.10968.63:
`Compatible. 7 usages of deprecated API. 6 usages of experimental API`. The targeted test/build/verification run took 1m 34s.
Source, test and artifact receipts match the development ZIP (0.1.0 metadata).
The full suite and actual MCP host were not repeated for this presentation-only
change; their preceding baseline is recorded separately. See
[the verification evidence](wicket-oat-project-view-evidence.json) for hashes.
