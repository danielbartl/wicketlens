# WicketLens

**See how your Apache Wicket Java components connect to their HTML markup.**

WicketLens is an IntelliJ IDEA plugin that puts a Wicket component hierarchy beside
your editor. Follow a component from Java to `wicket:id` and back, spot missing
pairs, and explore how a page is assembled without running the application.

## Features

- **A Wicket-aware sidebar.** Follows the active Java or HTML file and shows nested
  components, types, matching status, and explanations for uncertain results.
- **Java ↔ HTML navigation.** Jump between paired IDs from the editor or hierarchy.
  Completion suggests unused IDs under the matching parent.
- **Editor feedback.** Possible missing pairs appear in inspections, the Problems
  view, and the gutter. Configurable Wicket-orange highlighting makes IDs easier to spot.
- **Safer ID edits.** Preview paired Java/HTML renames and use quick fixes to correct
  IDs or create basic counterparts, with native undo and conservative scope checks.
- **Explore larger pages.** Search IDs, types, and paths; filter findings; focus a
  subtree; step through findings with F2 / Shift+F2. Keep your place after reanalysis.
- **Beyond direct additions.** Analyze supported fields, helpers, factories,
  inheritance, custom inputs, queueing, and repeater item templates.
- **Copy context.** Export the complete analyzed page as structured JSON for debugging,
  documentation, or use with your own tools. Analysis runs locally.

## Screenshots

**Explore components beside their Java source.** Fields and initialization setup
appear in one hierarchy, with form children grouped under their parent.

![WicketLens showing matched form fields beside FieldComponentsPage.java](docs/images/component-hierarchy.png)

**Find missing pairs.** The Unpaired filter keeps parent context while showing
Java-only and markup-only IDs. The HTML editor highlights IDs and marks the finding in the gutter.

![Unpaired Java and HTML IDs in the sidebar, with a warning beside the HTML source](docs/images/unpaired-ids.png)

**Inspect queues and repeaters.** See queued placement and logical item templates
for supported ListView and RepeatingView patterns.

![Queue placement and logical repeater item templates beside their Java source](docs/images/queue-and-repeaters.png)

## Installation

WicketLens is not on JetBrains Marketplace yet. Install a plugin ZIP built from
this repository, or use a prebuilt ZIP if one is supplied with a release.

### Build the ZIP

Download or clone this repository, select a **Java 25 JDK**, and run from the
repository root:

```bash
./gradlew buildPlugin
```

On Windows, use `gradlew.bat buildPlugin`. The wrapper downloads Gradle and the
required build dependencies on the first run.

The current archive is `build/distributions/wicketlens-0.1.0-SNAPSHOT.zip`.

### Install in IntelliJ IDEA

1. Open **Settings → Plugins**.
2. Open the settings menu (gear icon) and choose **Install Plugin from Disk…**.
3. Select the plugin ZIP without extracting it.
4. Apply the changes and restart IntelliJ IDEA if prompted.

See JetBrains' [installation instructions](https://www.jetbrains.com/help/idea/managing-plugins.html#install_plugin_from_disk).
To update a local installation, build or obtain a newer ZIP and install it the same way.

**Verified compatibility:** IntelliJ IDEA **2026.2.3** (build **262.10968.63**) with
Wicket **10.11.0**. Other IDE/Wicket combinations have not been verified.

## Getting started

1. Open a Wicket project with its dependencies resolved.
2. Open **View → Tool Windows → WicketLens**, then select a Java `WebPage`, `Panel`,
   or `FormComponentPanel`, or its uniquely paired HTML companion.
3. Keep **Follow editor** enabled to follow the current file, or use
   **Tools → Analyze Wicket Page** explicitly.
4. Select a component to navigate to Java or HTML. After edits, use **Re-analyze**
   to refresh the sidebar snapshot.

To try the included examples, open [examples/demo/pom.xml](examples/demo/pom.xml)
as a Maven project. The demo contains forms, custom inputs, inherited markup,
provider factories, queues, and repeaters.

## Scope and documentation

WicketLens is an early-stage static analysis plugin. It reports supported bindings
and explains uncertainty when it cannot prove a relationship. Dynamic application
code and runtime behavior can remain unresolved; real application accuracy and
performance evaluation is still pending.

- [Developer guide](docs/developer-guide.md) — build, sandbox, usage, supported patterns, and troubleshooting.
- [Hierarchy exploration](docs/hierarchy-exploration.md) — search, filters, focus, and finding navigation.
- [Analysis and verification record](docs/acceptance.md) — test evidence and compatibility limits.
- [Project specification](spec.md) — scope and future possibilities.
