# README screenshots

`mcp-server-setup.png` and `mcp-exposed-tools.png` additionally document the
native MCP settings in the isolated IDEA 2026.2.3 sandbox. Only the portable
demo name and loopback addresses are visible. Their hashes and the graphical/
client walkthrough are recorded in [setup evidence](../mcp-setup-evidence.json).

These images show WicketLens in isolated IntelliJ IDEA projects. The captures
use IntelliJ IDEA 2026.2.3 and the bundled demos unless the table gives a
different version or project. Each image is cropped to the tool window and
editor area.

| Image | Demo / state |
| --- | --- |
| `component-hierarchy.png` | `FieldComponentsPage.java`, All filter, expanded form children |
| `unpaired-ids.png` | `QuickFixPage.html`, Unpaired filter, Java-only `name` and markup-only `note` |
| `queue-and-repeaters.png` | `QueueRepeaterPage.java`, All filter, expanded queue and item-template branches |
| `spring-inspections.png` | Spring demo's `SpringDemoPage.java`, four Spring findings and matched `title` binding |
| `spring-bean-completion.png` | Spring demo's `BeanNamesPage.java`, live Basic Completion after typing `or`, with the `orderService` source candidate selected |
| `mounted-url-navigation.png` | IntelliJ IDEA 2026.3 with a disposable copy of the Wicket Oat examples, after opening `BadgePage.java` from `/badge` |
| `spring-bean-inventory.png` | IntelliJ IDEA 2026.3 with the Spring demo, after opening `DemoSpringConfig.orderService()` from the `namedOrders` injection |
| `mcp-server-setup.png` | Native MCP Server settings enabled for the portable WicketLens demo; client configuration controls visible |
| `mcp-exposed-tools.png` | Native Exposed Tools settings filtered to the eight enabled WicketLens tools in Preview 2 |

To refresh them, run `./gradlew runIde`, open `examples/demo` as a Maven project,
and open the corresponding files with the WicketLens sidebar visible. Expand
the component branches, collapse Diagnostics, and reanalyze after any edits.
Hide author annotations before capturing. Keep screenshots limited to the demo
UI, with no local source paths or unrelated desktop content.

For the Spring images, open `examples/spring-demo` as the Maven project. The
completion screenshot shows real source bean candidates alongside IntelliJ's
ordinary Java/string suggestions. The temporary typed prefix was restored without
accepting a completion; the demo file's original checksum was verified afterward.
Neither the temporary typing edits nor the capture agents are included in the repository.
The original three captures remain unchanged.

The temporary capture helper is not part of the repository or plugin archive.
