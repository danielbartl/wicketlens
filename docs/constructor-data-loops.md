# Constructor data initialization loops

Finite counted `for` loops can now be audited for component isolation during the
bounded source-layout pass. This removes DataTablePage's first setup blocker
without executing the application, unrolling rows, or evaluating model contents.

## Supported slice

The loop declares one int/long counter with byte/short/int/long constant start and bound,
compares that counter with `<`, `<=`, `>` or `>=`, and updates it with `++` or `--`.
The direction must prove termination without overflow, including the final update.
Existing source/binary constant snapshots supply ranges and freshness dependencies.
A huge finite range costs one source audit, not one pass per iteration.

The body supports declarations, expressions, blocks, if/else and ternary data
expressions. It may construct implicit-canonical source records containing
reviewed primitive/boxed/String, simple source-enum, array and immutable JDK data.
The reviewed JDK values include Currency, LocalDate and BigDecimal.valueOf.
Record/enum executable constructors and initializers remain outside this slice,
including enum instance-field initializers run by an implicit constructor.
Additional interfaces are confined to the compiled JDK Serializable marker;
unreviewed interfaces can trigger initialization through default methods.
Source finalizers are excluded, including on otherwise canonical records.

Collection operations are confined to directly constructed, zero-argument
ArrayList storage, referenced by an unreassigned local or a private final field
of the current constructor owner. Raw collections, aliases, arbitrary
implementations and constructor-input collections do not establish this proof.
Only add/get/size/isEmpty are reviewed. String formatting and concatenation accept
scalar inputs; they cannot invoke a user's record/object toString through this
contract. Instanceof/pattern matching is excluded because record patterns can
invoke custom accessors implicitly.

The body cannot change the counter, write outer locals/fields/arrays, acquire or
expose components (including erased aliases), capture callbacks/method references,
construct arbitrary objects, invoke arbitrary source helpers, or use nested loops,
early exits, exceptional/synchronized control flow. Unreviewed library calls are
withheld by qualified method name. Even zero-trip loops audit their bodies.
Only root constructors participate; onInitialize and ordinary helper loops retain
their prior restrictions. Existing iterator population templates are separate.

## Meaning and diagnostics

An accepted loop contributes an INFO/PARTIAL_ANALYSIS source diagnostic:
`Constructor data loop audited for component isolation only; loop execution, data values and collection contents remain unknown.`
It supplies no row IDs/counts, collection contents, computed model values, or
post-loop scalar facts. Loop locals never enter the construction environment.
External scalar writes are rejected rather than leaving stale known IDs behind.

An unsafe loop reports `Constructor data loop is not isolated: ...` at the loop,
with the offending source file/line in its reason. Later setup failures retain
the accepted-loop note. The entire expanded graph is still withheld unless all
remaining construction and initialization effects pass the existing contracts.
Ordinary ID-origin/editing guards remain authoritative, including IDs also used
as loop data. No new editing authority is granted by an isolation note.

Text reports with no matched bindings show the first source-layout stop before
cascading fallback diagnostics. Unavailable helper diagnostics name the class and
method, and unavailable constructor-ID proof names the component class.
Severity, confidence, source locations and structured diagnostic codes are retained.

At milestone 22, the pinned Oat corpus's DataTablePage invoice-seeding loop at Java offset
3304 (line 79) passes. The next stop is OatConfirmDialog construction at offset
3767 (line 86), whose ID provenance is not established. The primary report remains
three unresolved variables, two unresolved constructors and 20 unresolved markup
entries. The search and empty-state toolbar source templates retain four inferred
matches each. Constructor content protocols and CRUD callbacks are separate work.
The later [constructor-owned content milestone](constructor-owned-dialog-content.md)
now passes OatConfirmDialog and advances the next stop to OatSelectionColumn's
unreviewed AbstractColumn construction.

## Shared native/MCP contract

MCP decision: extend the behavior of the existing page-structure, validation and
component-context tools through their shared native analyzer, value snapshots and
existing diagnostics. Agents benefit from the improved graph where the entire
setup is proved, and from the precise stop where it is not. No new tool, parameter,
selector, response field, schema version, runtime count or model-value field is
added. Tool descriptions and semantic client documentation describe this scope.

All state is invocation-local. Source/bytecode dependencies, committed snapshot
freshness, module/project isolation, cancellation, read-only behavior and the
512-KiB whole-response limit are unchanged. A session admits at most 16 reviewed
loops and a six-level data-type proof, sharing the source pass's 5,000/20,000-step,
48/96-source, 16-call and 256-instance bounds. Bound exhaustion withholds the graph.

Acceptance includes safe invoice-style records/enums/arrays/decimal/date seeding,
large/descending/zero-trip ranges, overflow and direction checks, component/data
escape and mutation guards, callback/helper restrictions, ID edit protection,
module isolation, stale/uncommitted dependencies, mid-audit cancellation and
bounds. An independent WicketTester process renders a real 23-record seed and
verifies that a runtime loop write changes an ID rather than preserving its old
literal. Native reports and all three MCP views share the evidence, with an
actual-host check. The independent Java process also demonstrates enum field and
default-method interface initialization, implicit toString calls and record-pattern
accessor dispatch; regression tests withhold these effects. Application sources
are read only through fixture copies.

Final milestone verification: **3,085** normal tests,
**26** actual MCP host tests and **22** release-checker unit tests passed
with zero failures/errors/skips. The 182 focused regression tests also passed.
Both read-only Oat corpora (Wicket 10.11.0 and 11.0.0) audit DataTablePage's seed
loop and identify the next OatConfirmDialog constructor stop. All 36 primary
binding/finding summaries, earlier template/completion groups, six graphical
Project tree marker checks and four saved text reports are preserved. Six other
unavailable-constructor diagnostics now name the component class; their source
locations, codes and findings are unchanged. All 318 application inputs are
unchanged. Configuration, structure and binary compatibility checks passed against
IU-262.10968.63: `Compatible. 7 usages of deprecated API. 6 usages of experimental API`. The full run took 24m 12s.
Current source/test/artifact receipts match the ZIP. See the
[milestone evidence](wicket-oat-data-loop-evidence.json) for counts and hashes.
The ZIP retains 0.1.0 metadata; no installation or publication was performed.
