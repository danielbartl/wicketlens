# Reviewed date-column enum defaults

The twenty-sixth milestone lets source column constructors store the four JDK
FormatStyle enum constants as opaque data. OatDateColumn's MEDIUM and SHORT
private defaults no longer stop its storage-only construction audit.

## Exact boundary

The resolved field must be a compiled PsiEnumConstant owned by the enum
java.time.format.FormatStyle in the SDK's JRT java.base module. The enum must
have exactly FULL, LONG, MEDIUM and SHORT as its enum constants. Its compiled
java.lang.Enum parent must come from the same JRT java.base class location.
Source declarations, similarly named enums, other JDK enums and library copies
outside JRT remain unreviewed.

The installed JDK 21 and 25 FormatStyle sources are identical: four constants,
no custom fields, constructors, methods or initializer blocks. Their implicit
Enum constructor stores the constant's name and ordinal. The reviewed source
entry SHA-256 is
`0e9bddd2f931ce40533f63539c729f3d2e063e0526fb35392af12f846992167b`.
The contract relies on that specific JDK class; it is not a general enum
initialization interpreter or an execution contract for Enum methods.

Only type-qualified and statically imported constants are supported. An
instance expression, borrowed receiver or helper call cannot qualify the static
constant. Source aliases, valueOf/values/name calls, formatter creation and
locale/time-zone lookups receive no new contract. The existing private instance
storage, constructor delegation, source initialization, capture and discovery
guards remain in force.

Both FormatStyle and Enum class dependencies enter the shared source snapshot.
Each physical accepted use retains a PARTIAL_ANALYSIS diagnostic with its source
location, including if later page setup fails. No enum object, formatting result,
locale, time-zone, row/model value or generated header/cell is exported. Rendering,
conversion, row functions and model reads stay deferred and unknown. A failure
still withholds the layout expansion rather than supplying partial selectors.

## Application and MCP decision

DataTablePage now passes OatDateColumn, followed by the boolean and actions
columns. Its next stop is DataTablePage.java offset 9841 (line 205), the filtered()
call in InvoiceProvider.iterator(). That provider/page interaction has no eager
construction contract in this milestone. The initial hierarchy remains unresolved;
its previously available toolbar contexts retain four inferred bindings each.
A fixture-only page using the actual OatDateColumn independently exercises the
accepted storage declarations without changing application sources.

Extend the existing page-structure, validation and component-context behavior
through shared semantic analyzers and value snapshots. Agents can inspect the
same enum-storage provenance and exact later blocker. No new tool, parameter,
selector, response member or schema version is added. Tool descriptions and client
documentation describe the narrow JDK boundary and remaining unknown values.

Analysis remains invocation-local and read-only. Source/default/callback edits,
SDK source dependencies, module isolation and request freshness remain covered.
Enum checks and dependency recording honor cancellation and the existing source
budget; no discovery bounds increase. The 12-level data/ancestry/interface bounds,
source-isolation/call limits, 20,000 steps, 96 sources, 256 instances, 30-second MCP
deadline and 512-KiB whole-response limit remain unchanged.

Acceptance covers all four constants, static imports, source/name/other-enum
rejections, receiver evaluation, eager enum/formatter/locale calls, later-stop
provenance, cancellation, read-only behavior, SDK dependency capture, source
freshness, module isolation, response limits and the actual MCP host. An independent
WicketTester oracle observes stored enum identities, unchanged locale/time-zone
defaults, deferred models and explicit later rendering. These observations do not
become runtime facts in static reports.

Final milestone verification: **3,199** normal tests,
**30** actual MCP host tests and **22** release-checker unit tests passed
with zero failures/errors/skips. The 326 focused regression tests passed.
Both read-only Oat corpora (Wicket 10.11.0 and 11.0.0) exercise the actual date
column in a fixture-only probe: 11 page bindings, two enum-storage notes, no
generated cells and shared native/MCP diagnostics. DataTablePage passes the date,
boolean and actions columns, then stops at its provider's filtered() call.
All 36 primary finding/binding summaries, prior template/completion groups,
earlier constructor probes, six Project tree markers, four saved reports and
318 application input hashes are preserved. Configuration, structure and binary
compatibility checks passed against IU-262.10968.63: `Compatible. 7 usages of deprecated API. 6 usages of experimental API`.
The full run took 26m 11s. Current source/test/artifact receipts match the ZIP.
See the [milestone evidence](wicket-oat-date-enum-evidence.json) for counts and hashes.
The ZIP retains 0.1.0 metadata; no installation or publication was performed.

The [next milestone](provider-construction.md) separates provider allocation from
its data callbacks and advances DataTablePage to RowsTable construction.
