# README screenshots

These images show the running IntelliJ IDEA 2026.2.3 sandbox with WicketLens and
the bundled Maven demo. They were captured from the actual Swing UI and cropped
to the tool window and editor area; they are not generated mockups.

| Image | Demo / state |
| --- | --- |
| `component-hierarchy.png` | `FieldComponentsPage.java`, All filter, expanded form children |
| `unpaired-ids.png` | `QuickFixPage.html`, Unpaired filter, Java-only `name` and markup-only `note` |
| `queue-and-repeaters.png` | `QueueRepeaterPage.java`, All filter, expanded queue and item-template branches |

To refresh them, run `./gradlew runIde`, open `examples/demo` as a Maven project,
and open the corresponding files with the WicketLens sidebar visible. Expand
the component branches, collapse Diagnostics, and reanalyze after any edits.
Hide author annotations before capturing. Keep screenshots limited to the demo
UI, with no local source paths or unrelated desktop content.

The temporary capture helper is not part of the repository or plugin archive.
