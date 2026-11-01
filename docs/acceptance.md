# WicketLens MVP acceptance record

Latest verification on **2026-11-01** for the local **0.1.0-SNAPSHOT** build. All nine
implementation slices are complete. This record closes the prototype's
implementation acceptance criteria; the real application feasibility gate remains
deferred by agreement.

## Verified environment

| Item | Version / scope |
| --- | --- |
| IntelliJ IDEA | 2026.2.3, build 262.10968.63 |
| Wicket fixture and demo libraries | 10.11.0, actual Maven artifacts |
| Optional Spring fixtures and separate demo | Wicket Spring 10.11.0; Spring 6.2.12 |
| Gradle / IntelliJ Platform Gradle Plugin | 9.7.0 / 2.19.0 |
| Kotlin | Compiler 2.4.21; IDEA's bundled stdlib 2.4.0 |
| Build JDK | SDKMAN Java 25.0.4+1.1-librca |
| Analyzed roots | Java `WebPage`, `Panel`, and `FormComponentPanel` subclasses |

Only this IDE/Wicket combination has been verified. Other Wicket versions, IDE
products, and future IDEA builds are unverified. An absent upper build bound in
plugin metadata does not extend the evidence to those environments.

## MCP native Spring inspection findings

The full normal suite passes **1,721 tests**, with no failures, errors, or skips.
The separate bundled-host suite has **five passing tests**, and all **20
release-checker unit tests** pass. Packaging/project/structure checks pass.
Binary verification against **IU-262.10968.63** is **Compatible**, with the
existing four deprecated and six experimental API usages.

`wicket_get_spring_inspections` runs the registered five native WicketLens Spring
inspections in the current IDE inspection profile. Its source scope is the selected
page, discovered source components, their bounded superclass ancestry, and the
reviewed Wicket application in the same module/source set. Exact native messages,
configured severity, highlight type, source ranges, and graph node IDs are retained.
Disabled checks, supported comment/annotation suppression, unavailable inspections,
source limits and partial coverage remain explicit. Profile enabled/severity state
joins source freshness checks; no fixes or profile writes are offered.

The **28 new normal cases** cover native descriptor parity, severity/disable and
suppression, bootstrap/proxy/annotation/bean-type findings, source/module scope,
repeated component identities, source/profile changes, type/ancestry/finding/byte
limits, Spring-free projects and read-only asynchronous operation. Lifecycle
matrices now include this tool. Their deliberately dirty document fixtures finish
setup before holding commits and block unrelated IDE autosave, while still requiring
all requests to wait for explicit PSI commit and preserving deadline/no-save checks.

Actual-host tests verify native findings over HTTP, module/project isolation and
cancellation/reconnection across all semantic views. The separate live protocol
checker confirms source ranges, configured severity, external suppression-edit
refresh/restoration, a quiet Spring-free module and deterministic responses.
**Codex CLI 0.166.0** successfully makes one ephemeral read-only call with temporary
configuration and no command/file tools. The isolated host is stopped afterward.
See [the contract](mcp-spring-inspections.md) and
[portable verification evidence](mcp-spring-inspections-evidence.json).

This is a bounded static inspection export. Runtime wiring, profile activation,
unobserved types, and production-application accuracy/performance evaluation remain
unverified. Earlier tool/graph/export schemas are preserved. No new graphical
settings walkthrough, plugin publication, or complete release-gate certification
is claimed; refresh that matrix before distributing the changed artifact.

## MCP injected dependencies and ongoing feature-impact checks

The full normal suite passes **1,693 tests**, the separate actual-host suite
passes **four tests**, and all **20 release-checker unit tests** pass, with no
failures, errors, or skips. Packaging/project/structure checks pass. Binary
verification against **IU-262.10968.63** is **Compatible**, with the existing four
deprecated and six experimental API usages.

`wicket_get_injected_dependencies` captures the existing dependency analyzer's
values in the shared smart/committed request pipeline. It preserves source
locations, profile uncertainty, FactoryBean product/factory distinctions, nullable
compatibility, partial ancestry, and discovery limits. Existing graph/export and
other tool responses remain unchanged. Lifecycle matrices now include this tool;
the actual host verifies its registration/hints/schema, source facts, project
isolation, cancellation deadlines, disconnect recovery, and no-write behavior.

The isolated headless host's disposable Spring module additionally passes live
protocol checks for external profile edits/restoration, plain-Wicket isolation,
determinism, aliases, and graph node identity. **Codex CLI 0.166.0** successfully
invokes the new tool once using temporary configuration and an empty client folder,
read-only mode, no command/file tools, and no persisted conversation. The host
is stopped afterward. [Portable evidence](mcp-dependencies-evidence.json) retains
the actual response, exact ZIP checksum, and test counts.

[`AGENTS.md`](../AGENTS.md) and the developer guide now require an MCP impact
decision for every core extension. This slice's decision is to reuse dependency
snapshots via a dedicated read-only tool. Spring inspection findings remain a
separate follow-up. See [the tool contract](mcp-injected-dependencies.md).

This is not a new graphical/client-matrix release walkthrough. The earlier
graphical evidence below is historical, including its four-tool screenshot;
refresh it before claiming the complete release gate for this changed artifact.
Production-application accuracy/performance evaluation remains deferred.

## Spring profile conditions and FactoryBean products

All **380 focused Spring/dependency tests** pass with no failures, errors, or
skips, including **29 new cases**. This includes native completion and reference
navigation, dependency-tree details/search, production/test and module isolation,
Spring-absent behavior, existing inspections, and generated test compilation/run.
Packaging, project configuration, and plugin structure checks pass.

Binary verification against **IU-262.10968.63** reports **Compatible**, with the
same four deprecated and six experimental API notices as the earlier preview.
The rebuilt ZIP contains one plugin JAR and no new dependency JARs.

The shared source catalog now exposes declared class/method profile conditions,
bounded composed conditions, and concrete generic FactoryBean products (including
inherited type substitutions). `name`/aliases identify the product and `&name`/
`&alias` identify its factory. By-type candidates avoid duplicating a compatible
product with its factory fallback. Raw/wildcard/unknown products, unresolved
profiles, and runtime availability retain uncertainty. Source-type inspection
guards, the component graph, JSON/MCP schemas, and runtime dependencies are unchanged.

A separate disposable Java fixture compiled and ran against the actual Spring
**6.2.12** artifacts. Five application contexts checked class/method profile AND,
array and composed-profile OR, negation, generic FactoryBean products, aliases,
and factory-instance dereferences. This is framework-fixture verification, not
the deferred production-application evaluation.

See [the feature contract](spring-integration.md#profile-conditions-and-factorybean-source-candidates).
The full normal/host suite and graphical/client release evidence below belong
to the earlier preview. This slice does not refresh that complete release gate
or claim a graphical sandbox walkthrough or publication of the new plugin ZIP.

## MCP graphical/client setup and preview checks: Phase 3 third slice

The full normal suite passes **1,644 tests**, the separate host suite passes
**three tests**, and the release checker passes **20 tests**, with no failures,
errors or skips. The combined gate returns **verified_local_preview**.

The native Settings walkthrough verifies enabling MCP, reviewing its access
dialog, and seeing all four WicketLens entries in Exposed Tools on IDEA
2026.2.3 / IU-262.10968.63. A stale sandbox process was detected and restarted
before semantic acceptance. Codex CLI 0.166.0 and Claude Code 2.1.314 each make
one actual context call through the graphical server and report the matched
TextField/Form/HTML association with uncertainty preserved. Per-process client
settings permit only the read-only context tool; saved client settings are
unchanged. The sandbox closes normally and its original disabled MCP setting is
restored. [Screenshots and portable evidence](mcp-setup-evidence.json) are linked
from the [setup guide](mcp-client-setup.md).

Normal and host test results now use separate report directories. Gradle records
source/build/tooling fingerprints at test start/completion and retains XML
hashes/completeness. A source mutation, partial test run or changed report cannot
silently replace successful full-suite evidence. The exact ZIP is tied to the
Compatible verifier receipt; bytecode/resources, optional MCP metadata, current
graphical/client evidence and screenshot hashes are checked. The archive now
contains the project's Apache license. The checker has **20 passing tests**,
including stale, missing, altered and incomplete evidence cases.

Packaging and project/structure checks pass. All **278 production classes** and
processed resources match the checked ZIP, including 19 MCP classes. The Apache
license and optional MCP descriptor match their sources, and no test/framework
classes or extra dependency JARs are bundled. Binary verification is
**Compatible**, with the existing four deprecated and six experimental API
notices. The exact artifact checksum and retained test/client results appear in
[portable release evidence](mcp-release-evidence.json).

See [the release checklist](release-checklist.md). This prepares a local preview;
it does not sign, tag, publish, commit or push. A fresh normal-user-profile ZIP
installation is still a per-release review item, distinct from the verified
Gradle sandbox. The native transport limitation and deferred application study
remain explicitly open.

## MCP project isolation and transport: Phase 3 second slice

The complete normal suite passes **1,644 tests**, with no failures, errors or
skipped tests; the three host tests run separately.

All **90 normal focused MCP tests** and **three separate host tests** pass.
The dedicated mode uses the actual bundled MCP HTTP server and optional
WicketLens registration. Two open projects have identical class/module names;
six overlapping calls in one session select the correct project. Indexing in
one project leaves the other usable.

Larger real PSI fixtures preserve all 200 matched bindings, reject oversized
Unicode reports consistently across the three views, and allow a small context
selection from the same large graph. Existing response bounds are reused.

The host accepts cancellation notifications with HTTP 202 but does not promptly
cancel the tool coroutine. Work is released by the existing 30-second deadline.
Long blocked calls, including an uncancelled control, return empty HTTP 404 and
expire their session in this SDK. A fresh session recovers for all three tools.
Dropped HTTP requests also release their registrations after indexing resumes,
and fresh calls succeed. Immediate wire cancellation is a documented upstream
limitation, not a claimed feature.

See [the verified contract and reconnection guidance](mcp-isolation-transport.md)
and [portable evidence](mcp-isolation-transport-evidence.json). Production
analysis, adapters, schemas and dependencies are unchanged. Test servers,
projects and SDKs are stopped/disposed, with test settings restored. Normal
IDE/client settings are unchanged. Graphical/client/release coverage and the
deferred application study remain open, along with retesting upstream transport
behavior on SDK upgrades.

Packaging and project/structure checks pass. All 19 MCP classes and the optional
descriptor match the ZIP, with no bundled test helpers or extra framework/
transport libraries. The production artifact is unchanged and retains the
**Compatible** binary-verifier verdict against IDEA 262.10968.63 (task up to
date), with the existing four deprecated and six experimental API notices.

## MCP reliability: Phase 3 first slice

The full suite passes **1,641 tests**, with no failures, errors or skipped tests.

All **87 focused MCP tests** pass, including **10 new lifecycle/concurrency
cases**. The shared request adapter now cancels its own child scope when the
selected project is disposed and returns `PROJECT_UNAVAILABLE`, with temporary
disposal registrations removed on every exit. Caller cancellation still
propagates. Wire contracts, normal deadlines, analyzers, exports and dependencies
are unchanged.

Real indexing/document constraints cover blocking and release, cancellation,
timeout recovery, source bytes, concurrent successful/failed requests and
sidebar isolation. Separate real-project fixtures verify already disposed
projects and closure during indexing for all three views.

The live built-in-server checker passes **36 requests across six concurrent
sessions**: 30 successes and six expected lookup failures. Two modules share a
class name but return distinct correct data. All responses equal sequential
baselines, the original session remains usable, and source/resource/module
inputs stay unchanged. Existing structure/probe, validation and context protocol
checks pass. See [the reliability contract](mcp-reliability.md) and
[evidence](mcp-reliability-evidence.json).

The isolated host is stopped; normal settings are unchanged. Multiple open
projects, transport cancellation, larger fixtures and graphical/client/release
coverage remain open. Real-application evaluation stays separately deferred.

Packaging and project/structure checks pass. All 19 MCP class files and the
optional descriptor match the ZIP, with one WicketLens JAR and no bundled
framework/transport libraries. Binary verification against IDEA 262.10968.63
is **Compatible**, with the existing four deprecated and six experimental API
notices unchanged.

## MCP component context: Phase 2 third slice

The full suite passes **1,631 tests**, with no failures, errors or skipped tests.

The **77-case focused run** passes, including **22 new component-context cases**.
The third semantic tool reuses the shared request pipeline and selects from the
existing export. It supports root/nested paths and node IDs, rejects duplicate
or unknown path segments, preserves repeater template ancestry, and returns
exact component/parent/child objects, associated markup, findings and diagnostic
channels. Unavailable model/property-expression data is omitted.

Live built-in-server checks pass for nested paths, node-ID equivalence, root
selection, duplicate IDs, repeater templates, unresolved findings, request errors,
determinism, external Java/HTML edits and restored source checksums. Existing
structure/probe and validation protocol checks pass with the new tool present.
Codex CLI 0.166.0 invokes only `wicket_get_component_context` and reports the
actual TextField, Form parent, matched input and partial-analysis caveat.

See [the context contract](mcp-component-context.md) and path-redacted
[evidence](mcp-context-evidence.json). Normal IDE/client settings are unchanged;
the isolated host is stopped. Phase 2 is complete. Phase 3 reliability/release
readiness and the separately deferred application study remain open.

Earlier verification records below describe their respective slices.

Packaging and project/structure checks pass. All 16 MCP class files and the
optional descriptor match the ZIP, which contains one WicketLens JAR and no
bundled framework/transport libraries. Binary verification against IDEA
262.10968.63 is **Compatible**, with the existing four deprecated and six
experimental API notices unchanged.

## MCP validation: Phase 2 second slice

The full suite passes **1,609 tests**, with no failures, errors, or skipped tests.

The focused run passes **55 tests**: 32 existing structure/request cases and
**23 new validation cases**. Structure and validation now share one request
pipeline; the second view projects the existing export rather than adding a
checker. Findings retain exact codes, severities, confidence, locations, and
causes. Report, component, markup, element, and document diagnostic channels
remain visible, with original scope/availability flags and counts.

Live checks through JetBrains' built-in MCP server compare validation directly
to structure for matched, mismatched, and unresolved fixtures. Java-only and
markup-only findings remain INFO; unresolved setup retains causes. Errors use
the correct tool name. Repeated requests match, a temporary external fixture fix
is immediately reflected as MATCHED, files are restored, and checksums agree.
The existing structure/probe protocol checks also pass with validation present.

Codex CLI 0.166.0 successfully called only `wicket_validate_page` with ephemeral
settings and no file/command tools. It reported the two INFO/INFERRED unpaired
findings, partial-analysis warning, and runtime limitation. See
[the validation contract](mcp-validation.md) and path-redacted
[evidence](mcp-validation-evidence.json). The disposable host was stopped; normal
IDE/client configuration is unchanged. Component context and later Phase 3
work remain open; real-application evaluation stays deferred.

Packaging and project/structure checks pass. The shared MCP tool/request/snapshot/
error/view classes and optional descriptor match compiled output in the ZIP.
The archive contains one plugin JAR and no added framework/transport libraries.
Binary verification against IDEA 262.10968.63 is **Compatible**, retaining four
deprecated and six experimental API notices.

## MCP page structure: Phase 2 first slice

The full suite passes **1,586 tests**, with no failures, errors, or skipped tests.

All **32 adapter fixtures** pass with the MCP host plugin disabled. They cover
reuse of the existing JSON report, supported root kinds, scope/ambiguity and
invalid targets, source sets/modules, stale documents/markup/roots, pending
commits, cancellation, response limits, deterministic/no-write behavior, and
unchanged sidebar state.

Live checks use the real built-in JetBrains server, a disposable two-module
project, a real Java 25 SDK, and the existing Wicket 10.11.0 libraries. They prove
three MATCHED bindings (`title`, `form`, `form:name`), exact module selection,
ambiguity and other versioned tool errors, repeatability, original-probe
compatibility, and source checksums. Immediate external disk edits and newly
created Java/HTML pairs are refreshed and analyzed; temporary fixture edits are
restored. This check caught and fixed a stale-VFS case before final verification.

Codex CLI 0.166.0 successfully invoked the final semantic tool with no error,
using only that tool and ephemeral settings, then reported the correct hierarchy
with its partial-static-analysis qualification. See [the structure contract](mcp-page-structure.md)
and path-redacted [evidence](mcp-structure-evidence.json). Normal IDE/client
settings were unchanged and the disposable host was stopped. Validation and
component-context tools remain planned; real-application evaluation is deferred.

Packaging and project/structure checks pass. The final MCP tool, request adapter,
value snapshot/error classes, and optional descriptor match compiled output in
the ZIP. It contains one plugin JAR and no additional framework/transport
libraries. Binary verification against IDEA 262.10968.63 is **Compatible**, with
the existing four deprecated and six experimental API notices.

## MCP Phase 1 connectivity spike

The full suite passes **1,554 tests**, with no failures, errors, or skipped tests.

The optional MCP bridge compiles against IDEA's bundled server API and passes
project/structure checks. Binary verification against IDEA 262.10968.63 is
**Compatible**, retaining four deprecated and six experimental API notices.
The core foundation tests pass with the MCP Server plugin disabled, including
an explicit check that WicketLens and its existing actions remain available.

The real built-in MCP runtime discovers `wicketlens_mcp_probe`, advertises its
read-only/non-destructive/idempotent/closed-world hints, and returns the exact
versioned static JSON. Codex CLI **0.166.0** successfully invokes it over loopback
Streamable HTTP using the host's explicit project path; the recorded event has
status `completed`, no error, and an exact final response. The test client used
no command/file tools. No semantic MCP tools or analysis-engine refactor were
implemented. User client configuration and ordinary IDE settings were unchanged;
the disposable host was stopped after verification.

See [the findings and phased roadmap](mcp-integration.md) and the portable,
path-redacted [evidence](mcp-phase1-evidence.json). Older IDEs, graphical setup,
and semantic tool behavior are not covered by this spike. Real-application
evaluation remains deferred.

## Compiled dependency constants

The full suite passes **1,553 tests**, with no failures, errors, or skipped tests.

The new slice adds **41 cases**: 32 semantic/analysis and nine native editor/
version/service tests. The **104-test focused run** passes, including the
existing source constants and compiled-ancestor markup cases. Actual Java 17
class-file fixtures supply binary String/integral/boolean constants; unsupported
runtime, boxed, mutable, inaccessible, ambiguous, oversized, and effectful
inputs retain uncertainty. Mismatched attached source does not replace metadata,
and analysis never loads the temporary library classes.

Class files are captured as binary versions, with enclosing archive versions
for JAR entries. Tests verify archive changes before entry refresh, library-root
removal, JDK runtime-image lookup, native references, whole-expression problems,
paired-edit restrictions, independent literal rename, and current service
copying. Internal dependencies and JSON schema 1 retain their existing contracts.

The portable Maven demo compiles **38 Java files**. Its new
`BinaryConstantSetupPage` supplies `xmlTitle`, `item8`, and `status`, all MATCHED,
from three JDK class dependencies. See [the binary-constant contract](compiled-constants.md).
This is static fixture verification; real application evaluation stays deferred.

Packaging and project/structure checks pass. The ZIP contains current constant,
source-setup, file-resolution, and snapshot-version classes matching compiled
output, with no Wicket/Spring/JUnit/Mockito fixture libraries bundled. Binary
verification against IDEA 262.10968.63 is **Compatible**, with the existing four
deprecated and six experimental API notices.

## Bootstrap and test scaffolding (Spring slice 5)

The full suite passes **1,511 tests**, with no failures, errors, or skipped tests.
A final sealed-dependency guard and class-name validation update then pass all
**35 affected tests** (31 generator and four Spring-absent cases), including
regenerated Java compilation/execution. The project now contains **1,512 cases**,
with **53 added by this slice**: 21 bootstrap, 31 scaffold, and one Spring-absent.
Packaging and binary verification were repeated after the final guard.

The bootstrap interpreter recognizes bounded same-instance local context/injector
variables, closed setup helpers, boolean flags, standard context refresh, and
literal page mounts. Conditional, virtual, reassigned, recursive, or foreign
setup remains unknown. A separate native inspection reports reviewed zero or
multiple component-listener registrations with conditional wording and no
automatic rewrite.

The Tools action prepares a value snapshot under smart/committed read constraints
and opens a native preview with class-name validation. Creation uses an existing
non-generated Java test root, respects package prefixes, checks collisions and
freshness, and supports native undo. Plain Wicket needs no Spring libraries.
The generated test uses an isolated Spring context and application, reviewed
mock bindings, explicit manual guards, and subject constructor hooks.

Real Java 25 subprocesses compile generated Java 17 page and panel tests and run
both through JUnit Jupiter with Mockito as an agent, against Wicket 10.11.0 and
Spring 6.2.12. A manual-binding scaffold also compiles. The standalone
[Maven example](../examples/spring-test-demo/pom.xml) passes its rendering test,
including a label assertion against a stubbed dependency value. Native fixture
checks cover test-root selection, blocked paths, class-name conflicts, preview
validation, stale/uncommitted sources, collision refusal, and undo.
Interactive dialog display and a real application study remain separate manual
checks; these test recipes do not prove production Spring wiring.

Packaging and project/structure checks pass. The ZIP contains the current
bootstrap, inspection, generator, preview/service/action, file-creation, and
dependency analyzer classes, matching compiled output. No Wicket, Spring,
Mockito, or JUnit classes are bundled. Binary verification against IDEA
262.10968.63 is **Compatible**, with four deprecated and six experimental API
notices, unchanged from the preceding slice.

## Injected-dependency view (Spring slice 6)

The full suite passes **1,459 tests**, with no failures, errors, or skipped tests.
The slice adds **56 tests**: 34 dependency-analysis cases, 19 native view/service
cases, and one each for the Spring-free fixture, module boundary, and existing
Spring demo. Focused checks pass, including the final corrected module run. The
multi-module fixture now supplies a real JDK to both modules so inherited String
fields and local String bean declarations can resolve consistently.

Coverage includes local Spring intent versus classpath/generic injection alone,
literal/unknown names, annotation precedence, optional policies, static/primitive
targets, aliases, multiple/type-incompatible candidates, inherited/hidden fields,
separate custom component owners, generics/FactoryBean uncertainty, source limits,
cancellation, module scope, native actions/Enter, filtering, stale snapshots,
source deletion and refresh, editor-following preservation, plain Wicket, literal
markup characters, and unchanged component graph/JSON output.

The existing Spring demo supplies four injection fields and one source candidate
per field, with `orderService`/`orders` aliases merged for the same declaration.
No demo source changes or additional runtime libraries are required. This is
source discovery and native fixture verification, not a running Spring context
or real application accuracy/performance evaluation. See
[the dependency-view contract](injected-dependencies.md) for exact limits. This
slice adds no graphical sandbox verification claim.

Packaging and configuration/structure checks pass. The ZIP includes the current
dependency analyzer/model, sidebar panel, service, and version-tracking classes,
with no Spring/Wicket fixture libraries bundled. Binary verification against
IDEA 262.10968.63 is **Compatible**, with the existing four deprecated and six
experimental API notices.

## Wicket–Spring bean-name editing slice 4

A subsequent README capture used the real IDEA 2026.2.3 sandbox and Spring Maven
demo. The editor displayed the four Spring findings on `SpringDemoPage`; the
native completion popup on `BeanNamesPage` included both `orders` and
`orderService`. Captures are documented in [the image provenance](images/README.md).
The temporary typed prefix was restored, with the original file checksum checked.
This is a bounded UI check, not the deferred real application study.

The full suite passes **1,403 tests**, with no failures, errors, or skipped tests.
The slice adds **75 tests**: 55 catalog/validation cases, 18 editor/reference
cases, one module case, and one demo case. The 237-case Spring run and subsequent
76-case catalog/editor/demo run pass. Coverage includes default/explicit names,
aliases, standard stereotypes, acronym/blank naming, assignability, FactoryBean
product uncertainty, soft/polyvariant navigation, insertion escaping and suffix
replacement, annotation precedence, native highlighting/suppression, Java rename
guards, configuration identity, unknown profiles/scans/imports/inheritance,
conversion targets, post-processors, unresolved metadata/types, declaration
bounds, cancellation, scope isolation, edits, and read-only determinism.

The separate Spring demo compiles all **ten Java files** with Maven.
`BeanNamesPage` resolves `orders` to the `orderService` factory alias, offers an
empty name for completion, and has one intentional incompatible source-type
finding for `message`. Its `title` binding remains MATCHED. Previous demo
findings remain unchanged. No live Spring context is executed.

This uses Java source PSI without IntelliJ Spring APIs or runtime Spring classes.
Packaging and configuration/structure checks pass. The ZIP contains current
bean-name/reference/completion/setup classes and the new inspection description,
without Spring/Wicket fixture libraries. Binary verification against IDEA
262.10968.63 is **Compatible**, with the existing four deprecated and six
experimental API notices.
Candidates are not runtime bindings, and absent names never receive missing-bean
warnings. Optional IDE Spring model integration remains a follow-up. See
[the slice 4 contract](spring-integration.md#bean-name-editing-and-source-type-validation-slice-4).
Real application accuracy/performance evaluation remains deferred; no graphical
sandbox verification is claimed.

## Wicket–Spring annotation/manual-injection slice 3

The full suite passes **1,328 tests**, with no failures, errors, or skipped tests.
The slice adds **73 tests**: 67 annotation/path cases, four native editor cases,
one demo case, and one additional module case. The focused Spring run and final
69-case annotation/module run pass. Coverage includes supported/unsupported
targets, models/sessions/helpers, explicit self and caller injection, source
ancestry, Spring autowiring APIs, stereotypes/composed annotations, factory intent,
generic Jakarta ownership, unrelated API names/targets, unknown ancestry/search
bounds, source-set/module isolation, cancellation, edits, native highlighting,
suppression, disabling, and deterministic read-only findings.

The expanded separate Spring demo compiles all **nine Java files** with Maven.
`AnnotationExamplesPage`, `MissingInjectionModel`, and `MissingInjectionSession`
each have one annotation/manual-path finding. `InjectedOrderModel` has none.
The new page's `title` binding remains MATCHED; the original demo page retains
its four findings. These fixtures do not exercise a live Spring context.

The inspection depends on no IntelliJ Spring plugin or runtime Spring classes.
Packaging and configuration/structure checks pass. The ZIP contains the current
annotation/path classes and inspection description, with no Spring/Wicket
fixture libraries bundled. Binary verification against IDEA 262.10968.63 is
**Compatible**, with the existing four deprecated and six experimental API notices.
No automatic annotation or constructor rewrite is provided. See
[the slice 3 contract](spring-integration.md#possible-annotation-or-manual-injection-issue-slice-3)
for evidence gates and limits. Real application accuracy/performance evaluation
remains deferred; no graphical sandbox verification is claimed.

## Wicket–Spring inspection slices 1–2

The complete suite passes **1,255 tests**, with no failures, errors, or skipped
tests. The **94 new Spring cases** also pass independently. Ordinary fixtures
remain Spring-free; only the Spring fixtures attach the actual Wicket Spring
and Spring libraries.

Coverage includes construction-time null/non-null resets, Component/Behavior
targets, inherited fields, deferred/other-instance writes, narrow explicit
reinjection repairs, native suppression/highlighting, transient storage, custom
serialization hooks, final proxy types, raw final non-Serializable storage,
Wicket's direct-value exceptions, and conservative unknown configurations.
Application evidence is checked across source ancestry, multiple applications,
production/test roots, module dependencies, committed edits, and cache invalidation.
Generic Jakarta injection and Spring classpath presence alone stay inactive.

The separate [Spring demo](../examples/spring-demo/pom.xml) compiles all **five
Java files** with Maven against the released dependencies. Its page has two
overwrite findings, one transient finding, one final-proxy-type finding, and an
unaffected MATCHED `title` binding. This is an intentionally problematic fixture,
not a deployable application or runtime Spring test.

Packaging and configuration/structure checks pass. The distribution contains the
current Spring inspection classes and descriptions, with no Spring/Wicket
fixture JARs. Binary verification against IDEA 262.10968.63 is **Compatible**,
with the existing four deprecated and six experimental API notices.
The plugin adds no IntelliJ Spring dependency. See
[the Spring contract](spring-integration.md) for exact evidence gates, limitations,
and remaining slices. No graphical or real application evaluation is claimed.

## Shifts, bitwise expressions, and eager booleans follow-up

The complete suite passes **1,161 tests**, with no failures, errors, or skipped
tests. This extension adds 58 semantic/demo cases and eight native editor cases.
The corrected affected 234-test run and complete suite both pass.

Coverage includes signed/unsigned shifts, int/long distance masks, negative and
long distances, independent shift promotion, complement and narrow integral
promotion, masks and precedence, eager boolean truth tables, required effects and
exception risks, unknown/boxed/mutable inputs, source constants, bounds,
whole-expression provenance, native editing/completion, and source freshness.
Literal-validity regressions reject out-of-range decimal tokens before folding
while retaining legal minimum-value forms and hexadecimal bit patterns.

All **37 demo Java files** compile against Wicket 10.11.0. `BitwiseSetupPage`
has **5 MATCHED** bindings, including computed `item3`. Two Java files and its
HTML companion participate in the snapshot. See [bitwise expressions](bitwise-expressions.md).

Packaging, configuration/structure checks, and binary verification pass. The
verdict is Compatible with four deprecated and six experimental API notices.
All three modified analysis classes in the distribution match current compiler
output. JSON schema 1 is unchanged. This slice adds no graphical sandbox
verification claim; real application accuracy/performance evaluation remains deferred.

## Source constant IDs and conditions follow-up

The complete suite passes **1,095 tests**, with no failures, errors, or skipped
tests. The extension adds 54 constant-analysis/demo tests, 10 native editor tests,
and one explicit mutable-static-boolean guard. Two earlier static-constant
expectations now verify supported integral/boolean values. The affected
240-test run and complete suite both pass.

Coverage includes qualified names, static imports, interfaces, inherited lookup
dependencies, source constant expressions, narrowing/promotion/overflow,
initialization effects, invalid/inaccessible/ambiguous references, recursive/file/
expression bounds, whole-use problems, native Java declaration references,
shared editing guards, stale snapshots, and deterministic read-only exports.
The analyzed page's static initializers and instantiated/invoked source owners
remain audited; reading an otherwise unused external constant holder does not
initialize it.

All **35 demo Java files** compile against Wicket 10.11.0. `ConstantSetupPage`
has **5 MATCHED** bindings and records two Java source dependencies plus HTML.
See [the source-constant contract](static-constants.md).

Packaging, configuration/structure checks, and binary verification pass. The
final run reuses unchanged production packaging/verifier outputs; both the new
resolver and interpreter in the ZIP match current compiler output. The verdict
is Compatible with four deprecated and six experimental API notices. JSON schema
1 is unchanged. No new graphical sandbox verification or real application
accuracy/performance evaluation is claimed.

## Paired rename and constructor delegation review

The complete suite passes **1,030 tests**, with no failures, errors, or skipped
tests. Twelve cases were added: three native editor cases for inherited/shared
markup and safe own-companion rename, and nine constructor-tracing cases.
The original inherited-template and delegation-assignment failures reproduced
before the fixes; the focused 137-test run and complete suite then passed.

Paired rename checks the actual selected attribute resource against the analyzed
class's own conventional companion. A derived caller literal cannot rename an
inherited template shared with siblings. An own extension also cannot rename
base-template IDs, while its own eligible IDs retain paired rename.

Constructor tracing checks every delegation argument before propagating parameter
or fixed-literal origins. Nested/conditional assignments, updates, and unknown
calls cannot produce a confirmed incorrect ID. Tests also retain pure reordered
forwarding, parenthesized parameters, and reviewed repeater factory behavior.
See [source setup](source-setup.md) and [constructor coverage](constructor-coverage.md).

Packaging, configuration/structure checks, and binary verification pass. The
verdict is Compatible with four deprecated and six experimental API notices.
Demo sources are unchanged by this review. No new graphical sandbox or real
application evaluation is claimed.

## Hierarchy exploration follow-up

The complete suite passes **1,018 tests**, with no failures, errors, or skipped
tests. The extension adds four focus-projection tests, 11 logical state-key tests,
and 20 native view tests. Focus subtree and Show full hierarchy combine with
search/status filters. Next/Previous finding and F2/Shift+F2 reveal unpaired or
unresolved component rows in tree order, including collapsed branches, without
automatically opening an editor.

Expansion, selection, and focus survive reanalysis through unique raw ID/type/path
keys. Tests verify fresh Java/HTML source locations, collapsed branches, removed
or ambiguous targets, dynamic IDs, logical templates, page isolation, literal
labels, stale snapshot guards, and complete exports. Up to 16 page contexts are
remembered for the open view's lifetime; current search/status controls persist.

Packaging, configuration/structure checks, and binary verification pass. The
verdict is Compatible with four deprecated and six experimental API notices.
See [the hierarchy-exploration contract](hierarchy-exploration.md). Both hierarchy
slices are implemented. Real application evaluation remains deferred, and this
slice adds no graphical sandbox verification claim.

## Hierarchy filtering follow-up

The complete suite passes **983 tests**, with no failures, errors, or skipped
tests. The extension adds 13 filter-projection tests and 14 native view tests.
Persistent ID/type/path search combines with All, Unpaired, Java only, Markup
only, Unresolved, and Matched selection. Nonmatching ancestors remain muted for
context, and markup-only descendants use their matched container hierarchy.

Tests verify query/status composition, qualified types, paths, repeated IDs,
context retention, actual markup parents, empty results, selection/details/source
actions, expansion restoration when clearing filters, complete text/JSON exports,
reanalysis, page switches, stale navigation/copy guards, and cancellation. Controls
retain values for the open view's lifetime. Subtree focus, finding traversal, and
tree state across reanalysis are implemented in the later exploration follow-up
recorded above.

Packaging, configuration/structure checks, and binary verification pass. The
verdict remains Compatible with four deprecated and six experimental API notices.
See [the hierarchy-filtering contract](hierarchy-filtering.md). Analysis and JSON
schema 1 retain the full page; real application evaluation remains deferred.

## Reviewed scalar expressions follow-up

The complete suite passes **956 tests**, with no failures, errors, or skipped
tests. The extension adds 56 scalar-expression/demo cases and 11 editor cases.
One earlier unsupported-concatenation expectation now verifies a computed getter
ID. Coverage includes integral promotion, overflow, narrowing, comparisons,
String concatenation/content equality, immutable configuration, unknown and
nullable values, exception/effect guards, bounds, source freshness, and
deterministic read-only exports.

Computed IDs have whole-expression provenance and do not acquire fragment-based
rename/completion or literal-pair references. Java-only inspections anchor to the
whole computation in the inspected page file. Literal IDs used by comparisons
or other calculations also withhold independent rename/completion; ordinary
independent literals retain native editing. Temporary constant-folding PSI leaves
physical sources and current snapshot versions unchanged. The JSON schema is
unchanged; new provenance/editing flags remain snapshot metadata.

Packaging, configuration/structure checks, and binary verification pass. The
verdict remains Compatible with four deprecated and six experimental API notices.
All 33 demo Java files compile against Wicket 10.11.0. `ExpressionSetupPage` has
five matched bindings, including two computed IDs, tracking two Java files and
its HTML companion. See [the scalar-expression contract](scalar-expressions.md).
Real application accuracy/performance evaluation remains deferred.

## Proven conditional setup follow-up

The complete suite passes **889 tests**, with no failures, errors, or skipped
tests. The extension adds 57 conditional/demo cases, 12 editor cases, and one
fallback regression. Previously unsupported literal guards now have positive
coverage; uncertainty fixtures continue checking unknown guards. The suite covers
`if`/`else`, short circuiting, ternary origins, return propagation, private final
fields, factory freshness/accessor purity, Page/Panel roots, repeater boundaries,
budgets, deterministic read-only reports, native editing, and source freshness.

Standalone unexpanded root setup calls now invalidate direct root attachment
confidence even without a local initialization hook. Selected ternary literals
support native ID editing; proven immutable scalar field origins are recognized
for coloring while keeping the existing rename restrictions.

Packaging, configuration/structure checks, and binary verification pass. The
verdict remains Compatible with four deprecated and six experimental API notices.
All 31 demo Java files compile against Wicket 10.11.0. `ConditionalSetupPage` has
four matched bindings from provider flags, a selected factory return, and an
initialization guard, tracking four Java files and its HTML companion. See
[the conditional contract](conditional-setup.md). Real application evaluation
remains deferred.

## Read-only source accessors follow-up

The complete suite passes **819 tests**, with no failures, errors, or skipped
tests. The extension adds 45 accessor/demo cases and eight editor cases. Existing
cached-provider/known-parameter factory expectations now verify the separate
read-only accessor contract. Coverage includes provider identity, String/primitive
getters, nested/default/generic dispatch, immutable origins, private final page
fields, initialization, purity and type guards, unknown injection, opaque repeater
model data, native editing/inspections, source freshness, and read-only exports.

Packaging, configuration/structure checks, and binary verification pass. The
verdict remains Compatible with four deprecated and six experimental API notices.
All 29 demo Java files compile against Wicket 10.11.0. `ProviderAccessorPage` has
four matched bindings through page/provider/scalar accessors, tracking four Java
files and its HTML companion. See [the accessor contract](source-accessors.md).
Real application accuracy/performance evaluation remains deferred.

## Source provider factories follow-up

The complete suite passes **766 tests**, with no failures, errors, or skipped
tests. The extension adds 29 provider-factory/demo tests and eight editor tests.
Factories can return fully constructed providers allocated in that invocation or
a nested factory, retaining concrete types, immutable configuration, and ID
origins. Tests cover static/instance/default dispatch, generic substitution,
allocation separation, constructor composition, return compatibility, cached/
parameter/null rejection, unknown injection, initialization, recursion, native
editing/inspections, freshness, and deterministic read-only reports.

Packaging, configuration/structure checks, and binary verification pass. The
verdict remains Compatible with four deprecated and six experimental API notices.
All 27 demo Java files compile against Wicket 10.11.0. `ProviderFactoryPage` has
five matched bindings from static/nested factories and a known instance builder,
with five consulted Java files tracked. See
[the provider-factory contract](provider-factories.md) for exact bounds. Real
application evaluation remains deferred.

The isolated sandbox was refreshed after confirming its sole demo project had no
unsaved documents. Its loaded plugin JAR matches the verified distribution. The
native tree confirms all five bindings, with distinct provider IDs and the
TextField under its Form; all five Java dependencies and the HTML companion are
current. Temporary developer Swing checks made no source edits and are not distributed.

## Source interface defaults follow-up

The complete suite passes **729 tests**, with no failures, errors, or skipped
tests. The extension adds 33 default-method/demo cases and nine editor cases;
one previous unsupported-default expectation now verifies the reviewed subset.
Class precedence, inherited implementations, most-specific defaults, diamonds,
abstract suppression, explicit interface-super calls, private/static interface
helpers, generic signatures, initialization guards, unknown receivers, read-only
exports, native editing, and source freshness are covered.

Fixtures now explicitly set Java 25 source language level in addition to their
Java 25 SDK. This corrects the previous implicit Java 6 language level and enables
real qualified interface-super resolution; all existing tests pass with the correction.
Packaging, configuration/structure checks, and binary verification pass, retaining
the Compatible verdict and four deprecated/six experimental API notices. All
25 demo Java files compile against Wicket 10.11.0. `DefaultMethodsPage` has six
matched bindings from provider, inherited-page, and explicit interface defaults,
with four Java dependencies. See [the default-method contract](interface-defaults.md).
Real application evaluation remains deferred.

The isolated sandbox was refreshed after confirming its sole demo project had no
unsaved documents. Its loaded plugin JAR matches the verified distribution. The
native tree shows all six bindings with a separate `name` Label under each Form;
all four Java sources and the HTML companion are current. Temporary developer
Swing checks made no source edits and are not distributed.

## Immutable provider-state follow-up

The complete suite passes **687 tests**, with no failures, errors, or skipped
tests. The extension adds 33 provider-state/demo tests and eight editor tests.
Known providers retain separate private final configuration and known dependencies
supplied through constructors. Constructor ordering/delegation, inherited/shadowed fields,
ID provenance, unknown scalar IDs, and conservative state/escape/effect guards are
covered, including implicit recursive construction and unfinished-receiver escape.
One previous stateless-only expectation now checks that mutable state remains
unresolved. Native editing, coloring recognition, problem ranges, shared-origin
restrictions, and source/configuration freshness are verified too.

Packaging, configuration/structure checks, and binary verification pass. The
verdict remains Compatible with four deprecated and six experimental API notices.
All 22 demo Java files compile against Wicket 10.11.0. `ConfiguredPartsPage` has
four matched bindings from independent configured providers and a delegated Form
factory, with all four consulted Java files tracked. See
[the provider-state contract](provider-state.md) for exact limits. Real application
evaluation remains deferred.

The native sandbox confirms four matched bindings for `ConfiguredPartsPage`,
including distinct heading IDs and the TextField under the delegated Form.
All four Java dependencies and the HTML source are current. Its loaded plugin
JAR matches the verified distribution. Temporary developer Swing checks made no
source edits and are not distributed.

## Proven source dispatch follow-up

The complete suite passes **646 tests**, with no failures, errors, or skipped
tests. The extension adds 29 dispatch/demo tests and eight editor tests. Final
pages/final overrides establish closed root targets; fresh stateless helper
allocations establish instance targets through source interfaces. Tests cover
overload/override distinctions, constructor ordering, explicit superclass calls,
generic bridge checks, conservative fallbacks, shared budgets, deterministic
exports, native navigation/rename/completion/inspections, dependency freshness,
and reanalysis after switching an implementation.

Packaging, configuration/structure checks, and binary verification pass. The
verdict remains Compatible with four deprecated and six experimental API notices.
All 20 demo Java files compile against Wicket 10.11.0. `DispatchPage` has four
matched bindings spanning its final-page override and interface factories; all
four consulted Java files are snapshot dependencies. See
[the dispatch contract](source-dispatch.md) for the supported subset. Real
application evaluation remains deferred.

The graphical sandbox confirms four matched bindings for `DispatchPage`, with
the TextField under its factory-built Form and the final-page override present
instead of the overridden base addition. All four Java dependencies and the HTML
source are current. The loaded JAR matches the verified distribution; temporary
developer Swing checks made no source edits and are not distributed.

## Queue/repeater follow-up

The complete suite passes **609 tests**, with no failures, errors, or skipped
tests. The new coverage comprises 12 queue tests, 25 repeater/demo tests,
12 editor integration tests, and one initialization test. Two earlier uncertainty
expectations now distinguish supported queueing from destructive tree mutations.

Queue placement follows unique eligible markup paths and preserves queue-scope
provenance. ListView and RepeatingView expose logical item groups whose child
matches have inferred confidence. Unsupported population, custom item factories,
captured-container mutations, and ambiguous placements retain uncertainty.
Navigation, rename, completion, Problems integration, quick-fix availability,
freshness, hierarchy presentation, and additive export metadata are covered.
See [the queue/repeater contract](queueing-repeaters.md) for exact bounds.

Packaging, configuration/structure validation, and binary verification pass.
The verdict remains Compatible with four deprecated and six experimental API
notices. All 16 demo classes compile against Wicket 10.11.0. The new
`QueueRepeaterPage` fixture has six matched bindings, two inferred template-child
matches, and two logical item groups. No runtime IDs or item counts are asserted.
The real application evaluation remains deferred.

The isolated sandbox was refreshed after confirming that its sole demo project
had no unsaved documents. Its loaded plugin JAR matches the verified distribution.
The native tree shows the queued container's child and two expanded logical item
groups, with six matched bindings. Go to HTML opens the template child's companion;
a repeated check retains the same page context and current source/PSI stamps.
Temporary developer Swing checks made no source edits and are not distributed.

## Specification acceptance criteria

| Criterion from [spec.md](../spec.md#10-acceptance-criteria) | Result | Evidence |
| --- | --- | --- |
| Build succeeds | Pass | `buildPlugin`; ZIP at `build/distributions/wicketlens-0.1.0-SNAPSHOT.zip`. |
| Sandbox IDE launches | Pass | Slice 8 graphical launch and demo import; the same installed plugin JAR was confirmed identical to the packaged JAR during slice 9. |
| Static Java patterns are recognized | Pass | `WicketAcceptanceTest` simple, variable, and nested Form cases; Java discovery/hierarchy regression suites. |
| Nested HTML Wicket IDs are found | Pass | Acceptance Form case retains both the ordinary `div` parent and the nearest Wicket parent. HTML/XML extraction suite covers Panels and preview markup. |
| Matching is parent-aware | Pass | Acceptance repeated-ID case reverses markup source order and confirms both `name` nodes match under their own parents; matcher suite covers wrong-parent and duplicate-path cases. |
| Dynamic cases do not produce definitive false errors | Pass within tested scope | Acceptance dynamic-ID case exports unresolved findings and causes with `INFO` severity; uncertainty suite covers unsupported setup and syntax recovery. |
| Analyze Wicket Page is available in the IDE | Pass | Action registration fixture and graphical demo report in the WicketLens tool window. |
| JSON context can be copied | Pass | Registered copy action / clipboard fixture and sandbox clipboard JSON inspection. |
| Eight fixture scenarios are tested | Pass | Eight dedicated full-pipeline acceptance tests, mapped below. |
| README documents run commands and limitations | Pass | Introduction and installation in [README.md](../README.md); sandbox, demo, JSON, troubleshooting, supported subset, and compatibility limits in the [developer guide](developer-guide.md). |

## Eight full-pipeline scenarios

Each test in [WicketAcceptanceTest](../src/test/kotlin/dev/wicketlens/WicketAcceptanceTest.kt)
creates physical Java and companion HTML PSI files under configured module roots,
with actual Wicket libraries attached to the fixture project. It runs the same
source selection and composed analysis used by the background service, formats
the report, and parses the exported JSON. The separate service suite tests
background execution, panel controls, supersession, cancellation, and copying.

| Specification scenario / test method | Acceptance observation |
| --- | --- |
| `SimplePage` / `testSimplePage` | Literal Label ID, concrete type, page parent, `MATCHED`, Java and markup locations. |
| `NestedFormPage` / `testNestedFormPage` | Form → TextField relationship in Java and JSON; ordinary HTML wrapper preserved without changing Wicket parentage. |
| `VariablePage` / `testVariablePage` | One Label construction represented once and matched after `add(variable)`. |
| `MissingMarkupIdPage` / `testMissingMarkupIdPage` | `JAVA_ONLY` with inferred matching confidence and null counterpart references. |
| `MarkupOnlyPage` / `testMarkupOnlyPage` | `MARKUP_ONLY` with a markup source location and null Java counterpart. |
| `DynamicIdPage` / `testDynamicIdPage` | `UNRESOLVED_DYNAMIC_ID` plus unresolved markup counterpart, with causes; no unmatched or positive match claim. |
| `NonWicketAddPage` / `testNonWicketAddPage` | Collection `add` does not discover a Wicket child or create a Java match. |
| `RepeatedIdsPage` / `testRepeatedIdsPage` | Four matches, with the two `name` IDs retaining separate parent and markup identities despite reversed source order. |

Every scenario additionally checks informational severity, partial-scope metadata,
valid Java offsets, selected companion path, matched markup offsets, unchanged
Java/HTML text and modification stamps, and identical JSON across two analyses
of the same sources. The exported finding counts must agree with the expected
scenario, independently of the text formatter.

## Final verification results

After the class-level HTML navigation follow-up, the complete suite passed:
**357 tests, 0 failures, 0 errors, 0 skipped**.

| Suite | Tests |
| --- | ---: |
| Plugin foundation | 3 |
| Full-pipeline acceptance | 8 |
| Java discovery | 14 |
| Java hierarchy | 22 |
| File pairing and composed reports | 32 |
| Cross-module pairing isolation | 1 |
| HTML/XML extraction | 28 |
| Parent-aware matching | 30 |
| Uncertainty handling | 30 |
| Presentation and JSON export | 8 |
| Caret/file selection | 4 |
| Background service, panel, and clipboard | 10 |
| Source navigation, stale reports, and re-analysis | 23 |
| Standard component constructors and demo | 20 |
| Initialization scopes and demo | 30 |
| Markup inheritance and demo | 16 |
| Expandable hierarchy, native sidebar, and source navigation | 19 |
| Component fields and demo | 35 |
| Editor Java/HTML target resolution | 9 |
| Editor following and pinning | 15 |

The final commands completed successfully using Java 25. To reproduce them,
set `JAVA_HOME` to a Java 25 JDK and run:

```bash
./gradlew "-Dorg.gradle.java.home=$JAVA_HOME" \
  test buildPlugin verifyPluginProjectConfiguration verifyPluginStructure verifyPlugin
mvn -f examples/demo/pom.xml -B -ntp compile
```

Slice 9 originally passed 178 tests and reused unchanged build/verifier outputs.
The core review follow-up changed production analysis, added 12 regression/control
tests, re-ran the complete suite, rebuilt the plugin, and ran binary verification
again. The source-navigation follow-up adds 19 further tests and repeats the full
suite, packaging, and verification for the rebuilt plugin. The standard-component
slice adds 20 tests covering 33 reviewed public overloads and repeats these checks;
the expanded Maven demo also compiles successfully. The initialization slice adds
30 tests and again passes the full suite, packaging, configuration/structure
checks, binary verification, and Maven demo compilation. The inheritance and
hierarchy slices add 29 tests and again pass those checks for the rebuilt plugin
and expanded five-class demo. The field slice adds 35 tests and repeats the full
suite, packaging, configuration/structure checks, binary verification, and Maven
compilation for the six-class demo. The native-sidebar and editor-following slices
add 29 tests, pass packaging/configuration/structure checks and binary verification,
and finish with a complete 352-test regression run. The class-navigation fix adds
five tests and repeats packaging/configuration/structure and binary verification
with all 357 tests passing. The demo Java sources are
unchanged by these UI slices. The rebuilt binary verdict is
**Compatible** with IDEA 262.10968.63, with **4 deprecated** and **6 experimental**
API usages in generated forwarding methods for IntelliJ's `ToolWindowFactory`
defaults. These warnings remain recorded; they are not compatibility errors.

The distribution contains only the WicketLens plugin JAR, with no Wicket fixture
libraries or extra Kotlin runtime bundled. At slice 9 acceptance, the JAR inside
the ZIP and the one loaded by the sandbox had the same SHA-256 digest. The core
review follow-up produced a new plugin JAR, followed by the source-navigation
build. Graphical verification of source navigation is recorded below. The later
constructor-coverage and initialization builds were verified through PSI fixtures,
Maven compilation, and the binary verifier; those slices did not relaunch the
sandbox UI.

In the graphical demo, the report shows **3 MATCHED**, **1 JAVA_ONLY**, and
**1 MARKUP_ONLY**. Clipboard inspection after invoking **Copy Wicket Context**
confirmed valid JSON for `example.CustomerPage`, the expected finding counts,
`form` → `name`, and matched HTML source locations. Slice 8 also checked the
visible source locations, background report flow, and status row in a narrow
window. The demo is a controlled fixture rather than a real application study.

## Core review follow-up

All three review findings are addressed:

- The background read action now combines `inSmartMode` and
  `withDocumentsCommitted` constraints. Tests disable automatic background
  commits, edit Java, HTML, or both, and verify that no report is published until
  the pending documents are committed. The completed JSON contains the edited
  IDs and current Java source offsets. A further test cancels a request while
  it waits and checks that committing later cannot publish a copyable report.
- Before pruning nested/anonymous class bodies from component discovery, a
  separate PSI scan detects access to the enclosing root. This includes qualified
  `ExamplePage.this`, implicit root member calls, and escaped root references.
  Affected constructor attachments become unresolved, with source-located
  `UNRESOLVED_SCOPE` causes; nested components are not invented.
- Method references bound to the analyzed root, including `this::add`,
  `ExamplePage.this::add`, and `super::removeAll`, are recorded as unexpanded
  scope effects. They cannot silently turn into markup-only claims or preserve
  confirmed matches in the affected constructor. Control cases keep other
  objects' methods and nested classes' own receivers separate from the page.

The tests were first run against the previous implementation: three stale-PSI
tests and six scope-effect tests failed, reproducing the review. After the fixes,
the focused suites and the complete regression suite passed. No dependencies or
new user-facing features were introduced. The graphical smoke results above
remain the slice 8/9 baseline; these fixes were verified through real PSI/service
fixtures and the rebuilt plugin verifier.

## Source-navigation follow-up

The report now maps component rows and their source lines to value-only Java and
markup destinations. **Go to Java**, **Go to HTML**, double-click, and **Enter**
open observed sources at their snapshot offsets. Root, matched, Java-only,
markup-only, and unresolved rows offer only their available destinations.
Repeated IDs under different parents retain separate links. JSON schema and
deterministic export remain unchanged; navigation/version metadata is not exported.

Source document stamps, file identity, project PSI and root versions, and relevant
editor/VFS events detect stale snapshots. Navigation and copying validate freshness
again when invoked. Stale reports stay visible with an out-of-date status and
disabled navigation/copy controls. Editor opening runs on the public EDT coroutine
API without holding a read lock across editor creation. The source offset is also
guarded when the IDE requests it, since opening an editor may process queued events.

**Re-analyze** uses the original Java source and qualified class identity. It
works while HTML is active and after source offsets shift. A renamed or removed
class requires a new selection through **Analyze Wicket Page**. Analysis is still
manual and waits for indexing and committed documents.

The 19 navigation fixtures exercise real editor opening/caret offsets, available
counterparts, repeated IDs, Enter navigation, uncommitted Java/HTML edits, external
VFS content changes, rename/deletion, newly created companions, project root and
other source changes, stale-state persistence, saving unchanged analyzed text,
re-analysis, and cancellation of obsolete queued navigation. Existing core-review
and acceptance fixtures remain in the complete regression run.

The final graphical sandbox was exercised through its real Swing controls with a
temporary developer UI harness. The registered Analyze action produced the demo
report. Selecting `title` and using **Go to HTML** opened `CustomerPage.html` at
the `h1` tag (offset 74); **Go to Java** opened `CustomerPage.java` at the Label
construction (offset 381). A temporary leading newline immediately disabled
navigation and copying and showed the out-of-date status. Restoring the original
text kept the report stale; **Re-analyze** restored a fresh report and working
navigation/copy controls. The demo source was restored, and the installed sandbox
JAR was confirmed identical to the verified distribution JAR. The temporary
harness is not part of the plugin or distribution.

The edit/undo flow was also repeated through normal keyboard input, with no new
IDE errors. Direct document edits from the scratch harness had emitted a
transaction-context diagnostic; that diagnostic came from the harness, not the
plugin's navigation path. The final sandbox was restarted without the harness.

## Standard-component coverage follow-up

Ten exact Wicket classes were added to the reviewed constructor set: `Button`,
`SubmitLink`, `Link`, `BookmarkablePageLink`, `ExternalLink`, `AjaxLink`,
`AjaxButton`, `CheckBox`, `DropDownChoice`, and `FeedbackPanel`. There are now
fifteen reviewed classes in total. The [constructor audit](constructor-coverage.md)
records the pinned source artifact, ID forwarding, all 33 additional public
overloads, and the retained limits.

Overload resolution must be valid before ID semantics can be confirmed. Anonymous
instances report the declared base class name, consistently with ordinary
instances, so generic links have correct component labels and exported types.
User subclasses, same-name classes, dynamic/invalid IDs, ambiguous/inapplicable
calls, callback scope effects, and unexpanded framework children remain
conservative. No runtime/callback or lifecycle execution is inferred.

Twenty new fixture tests cover the overloads, discovery through matching and
export, parent-aware Form nesting, repeated IDs, Panel roots, anonymous callbacks,
uncertainty controls, read-only/deterministic behavior, and the new demo sources.
`StandardControlsPage` compiles against Wicket 10.11.0 and produces **12 MATCHED**
findings in its full-pipeline fixture, with the existing partial-scope warnings.
Demo sources are declared test inputs so fixture edits invalidate cached results.
No dependencies or runtime libraries were added to the plugin.

## Initialization coverage follow-up

Actual `Component.onInitialize()` overrides declared on the analyzed Page or
Panel now support straight-line additions and directly initialized method-local
components/containers. The method must begin with one valid, reviewed
`super.onInitialize()` delegation. PSI override resolution keeps overloads and
other lifecycle hooks outside this phase. The
[initialization contract](initialization-coverage.md) records the reviewed Wicket
10.11.0 implementations, including `MarkupContainer`'s dequeue handling.

Constructor and hook observations share the graph while locals retain separate
declaration identities. Validation precedes discovery: unsupported hook effects
invalidate earlier constructor attachments, and uncertain constructor execution
also invalidates hook attachments. Conditional setup, mutations, root helpers,
manual/repeated initialization, root escapes, nested effects, custom inherited
setup, fields, aliases, factories, and duplicate paths retain uncertainty. Known
local container edges survive with unresolved confidence. Reports stay partial
and informational, with unchanged JSON schema and value-only snapshots.

Thirty real-PSI tests cover the supported setup, uncertainty boundaries, read-only
behavior, deterministic JSON, source locations, and a new demo pair.
`InitializationPage` produces **6 MATCHED** findings: a constructor Label, an
initialization Form with three children, and a hook-local Label. The demo compiles
with Maven against the pinned Wicket libraries. This backend slice does not add
UI controls or claim new graphical smoke-test results; restart the sandbox with
the rebuilt plugin to exercise the new fixture interactively.

## Markup inheritance and expandable hierarchy follow-ups

Conventional source-based Page and Panel markup now supports ancestor fallback,
replacement by an own ordinary companion, and one `wicket:extend` section inserted
into one empty active `wicket:child` slot per level. Merged paths preserve original
source locations and distinct identities across files; parentage, duplicate IDs,
and uncertainty are recomputed. Invalid or unavailable chains remain unresolved.
The [inheritance contract](markup-inheritance.md) records the source review and
boundaries. Inherited Java component setup, header merging, dependency resources,
custom locators, and resource variants remain unexpanded.

The default **Hierarchy** tab contains expandable component branches, unattached
components, markup findings, and diagnostics. A detail pane shows type, status,
confidence, parent, sources, and causes. Expand/Collapse, Java/HTML buttons,
double-click, and Enter support exploration and navigation. The **Text report**
tab remains available. Prepared hierarchy entries are immutable values built in
the background; Swing models stay on the EDT. Labels/details are plain text.
Stale trees remain readable while navigation/copying is blocked; re-analysis
rebuilds the tree without carrying old selection offsets into the new report.

Sixteen inheritance fixtures and thirteen hierarchy/service fixtures cover these
behaviors, including ancestor edits, empty-extension dependencies, original
navigation offsets, repeated IDs, literal labels, and cancellation. Existing
navigation, service, initialization, acceptance, and uncertainty suites remain in
the complete 288-test run. JSON schema stays at version 1, and the distribution
continues to contain only the plugin JAR.

The rebuilt sandbox was launched with `InheritedLayoutPage`. A temporary developer
Swing harness invoked the registered Analyze action and the real panel controls.
The report showed **3 MATCHED** findings and both contributing HTML source files.
Expand all displayed seven rows; Collapse all reduced the view to four root-level
rows. Selecting `name : TextField` displayed `CONFIRMED`, parent `form`, and both
source locations. **Go to Java** opened `InheritedLayoutPage.java` at offset 444;
**Go to HTML** opened `InheritedLayoutPage.html` at offset 225. Visual inspection
confirmed the hierarchy, selected row, details pane, and tabs in the narrow IDE
window. The harness is temporary, performs no source edits, and is not distributed.
Freshness/edit/re-analysis coverage for the new tree comes from the PSI/service
fixtures rather than new graphical source-edit checks. This is controlled demo
verification; real application evaluation remains deferred.

## Field-tracking follow-up

Private final instance fields declared on the analyzed class can now bind to
direct `new` initializers at declaration or in one supported constructor.
Constructor and `onInitialize()` reads share the original construction node,
preserving concrete type, literal ID, source offset, and container parentage.
Declaration resolution keeps fields separate from shadowing locals and foreign
instances. Field declarations after constructor methods and hooks before
constructor methods retain the correct phase interpretation.

All field uses in the source file are checked. Unsupported visibility/mutability,
static or inherited fields, extra writes, alternative/delegating constructors,
aliases, getters/helpers, setters, escapes, casts, captures, method references,
and unavailable/conditional initialization remain unresolved. Field attachments
also retain uncertainty for opaque root effects, field-initializer root captures,
instance initializer blocks, constructor preambles, and the existing lifecycle
guards. Known construction observations and local edges survive when only their
attachment scope is uncertain. The [field contract](field-tracking.md) documents
the precise boundary and Java initialization reference.

Thirty-five new PSI fixtures cover those cases, Page/Panel roots, source/phase
ordering, locals, repeated IDs, receiver-only containers, multiple additions,
unchanged constructor/ID semantics, tree/JSON identity, read-only analysis, and
deterministic exports. They were first run against the earlier implementation:
16 of the initial 30 tests failed, reproducing absent field bindings. The final
focused runs and the complete 323-test suite pass. No dependencies, persistent
indexes, UI controls, or JSON schema changes were added.

`FieldComponentsPage` uses both inline fields and a constructor-assigned field,
attaches a Form during construction, then adds three Form children and a status
Label in `onInitialize()`. Its full-pipeline fixture produces **6 MATCHED**
findings and one shared Form node. All six demo Java classes compile with Maven
against Wicket 10.11.0. This backend slice was verified through PSI fixtures,
packaging, and binary verification; it adds no new graphical smoke-test claim.
Relaunch through Gradle `runIde` to prepare the updated sandbox plugin. The
earlier hierarchy sandbox evidence above remains the graphical baseline.

## Native sidebar and editor-following follow-ups

WicketLens now defaults to a left-hand tool window with native platform toolbar
actions, semantic class/field/tag/information icons, muted type/status fragments,
type-ahead search through collapsed branches, a native splitter, and optional
component details. The text report remains available. Source labels are literal
text, and value-only row metadata drives rendering without parsing report strings.
Existing saved window placement is respected. The
[sidebar contract](sidebar-presentation.md) records behavior and platform references.

Follow editor is enabled by default once the view is opened and remembered per
project. Switching Wicket Java files or uniquely paired HTML companions replaces
the report; unrelated files and closing editors clear it. Disabling following
pins the current snapshot, and enabling it resumes on the active file. Rapid
switches cancel obsolete work. Mapping HTML back to Java uses module-scoped
filename lookup verified through the existing forward pairing contract, including
package and production/test isolation. WicketLens source navigation preserves the
current page context and avoids follow/re-analysis loops for inherited markup.
Edits alone retain stale-state guards until re-analysis. No automatic analysis
runs before the sidebar has been opened or after its view has been disposed.

Six native UI tests, nine editor-target fixtures, and fourteen editor-following
fixtures join the existing suite. They cover native action presentations/icons,
literal rendered fragments, details, collapsed-branch search, text-view actions,
stale guards, startup activation, Java/HTML/Page/Panel targets, pinning, ambiguous
or unrelated sources, closing editors, pending commits/rapid switches, cancellation,
disposal, explicit class selection, and inherited-source navigation. The existing
manual-snapshot navigation/service tests run with following disabled. All 352
tests pass, with unchanged JSON schema and compatibility warnings.

The development IDE was restarted after confirming it held only the demo project
and no unsaved documents. Its saved bottom placement was moved to the left using
the public tool-window API. Graphical inspection confirmed the new sidebar styling
beside the built-in Structure view. Native Expand/Collapse and details controls
worked; selecting the field-backed Form opened Java at offset 501 and HTML at
offset 114. Native speed search revealed the collapsed `name` child.

After the editor-following fix, the sandbox was reloaded again. Merely showing the
sidebar, without invoking Analyze, produced `FieldComponentsPage` with six matches.
Switching the actual editor to `CustomerPage.java` changed the report to
`example.CustomerPage`. Selecting `FieldComponentsPage.html` changed it back to
`example.FieldComponentsPage` through reverse companion mapping. Turning following
off pinned that report while CustomerPage was active; turning it on resumed
CustomerPage automatically. Temporary developer Swing harnesses exercised real
toolbar controls and public editor APIs under the IDE's write-intent context.
They made no source edits and are not distributed. The sandbox remains running
with Follow editor enabled. Real application evaluation remains deferred.

## Class-level HTML navigation follow-up

The class row now links to the beginning of the resolved companion file in both
the hierarchy and text report. It uses the selected markup path and captured
source dependencies, rather than guessing a filename or requiring a component
match. Derived markup opens its own extension file; inherited fallback markup
opens the resolved ancestor file. Missing or ambiguous pairing remains disabled.
Uncertain markup content still permits navigating to a known file, while stale
reports retain the existing navigation block. The exported root component is
unchanged and is not given an invented markup match.

Four navigation tests and one following test were added, with existing root-row
expectations updated. Cases include Page/Panel roots, tree/text actions, Java-only
children, derived/fallback companions, missing/ambiguous lookup, malformed markup,
staleness, and preservation of the followed snapshot. All 357 tests pass, along
with packaging, configuration/structure checks, and binary verification. The
compatibility verdict remains Compatible with the recorded API warnings.

After checking that the demo had no unsaved documents, the sandbox was reloaded
while restoring its existing editor tabs. Selecting the `CustomerPage` class row
enabled the real Go to HTML toolbar action. Invoking it opened `CustomerPage.html`
at offset 0, retained `example.CustomerPage` as the report, and kept Follow editor
enabled. This temporary graphical check made no source edits.

Generated evidence remains under `build/` and can be regenerated:

- Test results: `build/test-results/test/`; HTML report: `build/reports/tests/test/index.html`.
- Binary verdict: `build/reports/pluginVerifier/IU-262.10968.63/plugins/dev.wicketlens/0.1.0-SNAPSHOT/verification-verdict.txt`.
- Verifier detail: `build/reports/pluginVerifier/IU-262.10968.63/report.html`.
- Sandbox log: `build/idea-sandbox/wicketlens/IU-2026.2.3/log_runIde/idea.log`.

## Boundaries of acceptance

The implementation reconstructs the explicitly supported static subset. All
reports remain partial, unmatched findings are informational, and unsupported IDs,
constructors, scope, parentage, or markup retain uncertainty. The detailed
[supported patterns and limitations](developer-guide.md#java-discovery) are part of
this acceptance boundary.

Analysis uses PSI and configured module roots, runs in a cancellable read action
constrained to smart mode and committed documents through a project-scoped
service, and retains value snapshots. Formatting
and JSON preparation run in the background; UI updates and clipboard access run
on the EDT. Analysis performs no source writes. Subsequent editor features add
explicit native paired-ID rename and completion insertion; these are user-driven
source edits with standard editor/refactoring undo. There are no plugin-initiated
network requests, runtime instrumentation, persistent project index, or AI integration.

Reports require re-running after edits. Native editor inspections refresh
independently, and matched IDs support navigation, rename, and completion; see
[the editing contract](developer-guide.md#editing-wicket-ids). Source setup now also
supports closed helpers/factories, source superclass phases, and forwarding-only
custom constructors, with additional source-version and shared-rename guards;
see [the source contract](source-setup.md). The extended slice also supports
custom input ID contracts, isolated composite setup, and separate
`FormComponentPanel` markup; see [the input contract](form-components.md).
Bounded inspection quick fixes now support compatible ID corrections and basic
Java/HTML counterpart creation with native preview and undo; see
[the quick-fix contract](inspection-quick-fixes.md). Supported static headers,
explicit HTML variants, and read-only dependency layouts are also implemented;
see [the markup contract](markup-inheritance.md). Bounded component queueing and
ListView/RepeatingView templates are also supported; see
[their contract](queueing-repeaters.md). Proven final-page/final-method dispatch
and known instance helpers are also supported, including source
interface calls; see [the dispatch contract](source-dispatch.md).
Providers may now retain private final configuration and known constructor-injected
dependencies, with independent allocation state and traced ID origins; see
[the provider-state contract](provider-state.md).
Source interface defaults, private interface helpers, and explicit interface-super
calls now have bounded support with class/most-specific precedence and initialization
guards; see [the default-method contract](interface-defaults.md).
Closed source provider factories now preserve fresh known allocations and
configuration across nested/generic calls; see [their contract](provider-factories.md).
Custom resource contexts, arbitrary helper/factory
analysis, unsupported fields/aliases, unreviewed queue/repeater patterns, and runtime reconstruction remain
outside the supported subset.

The feasibility gate in [spec.md §11](../spec.md#11-feasibility-gate) is **not yet
evaluated**: no 5–10-page real application study, 90% reconstruction measurement,
or representative performance assessment has been performed. Passing fixture
acceptance does not establish those results. The deferred evaluation and any
additional features are separate follow-up decisions.
## MCP read-only Spring test plan

The first agent-assisted test slice exposes the existing Spring recipe through
`wicket_get_spring_test_plan`. Constructor strategies, isolated Mockito bindings,
manual review guards, required dependency coordinates and exact scaffold source
come from the shared generator; existing test roots are advisory. No files are
created or tests executed by the tool, and dependency availability/runtime
validity remain explicitly unchecked.

The complete normal suite passes **1,739 tests**, the separate bundled-host
suite passes **six**, and release-checker unit tests pass **20**, with no failures,
errors or skips. Packaging and project/structure checks pass; binary verification
is **Compatible**, retaining four deprecated and six experimental API notices.
Live protocol, 11-session concurrency, existing context/dependency/inspection
regressions, and one ephemeral read-only Codex call pass. See
[the contract](mcp-spring-test-plan.md) and
[portable evidence](mcp-spring-test-plan-evidence.json).

The published preview and its graphical matrix remain the six-tool artifact.
The seven-tool development build requires a refreshed graphical/client release
walkthrough before distribution; the full release gate correctly detects this
bytecode difference. This slice does not claim publication, new graphical
verification, runtime validity or real-application evaluation.
## Agent-assisted Spring demo workflow

The reproducible workflow uses the existing Spring test demo and three existing
read-only MCP tools. Codex retrieves the plan/structure/component context, adapts
only the existing test, runs Maven, corrects a raw-model-versus-escaped-string
assertion and obtains **six passing cases**. The original smoke test, isolated
Spring lifecycle, production Java/HTML and POM remain intact.

The agent and final repository verifier both observe **six passing baseline
cases**, **six expected assertion failures** after substituting a hard-coded
title in a disposable copy, and **six passing restored cases**, with no errors
or skipped tests. The final verifier also enforces matching case counts and
unchanged authored inputs. See [the prompt and commands](../examples/spring-test-demo/AGENT_WORKFLOW.md)
and [portable evidence](agent-test-workflow-evidence.json).

No new MCP tool/schema, plugin production code or runtime dependency was needed.
This verifies the portable workflow, not arbitrary application test generation
or production accuracy/performance. Prior full plugin-suite evidence remains
tied to its original inputs; this example-only slice uses the Maven/regression
checks and does not certify or publish another plugin release.
## General/plain test planning and complex Spring setup

The general `wicket_get_test_plan` tool and **Generate Wicket Test…** action reuse
the existing constructor, renderer, preview/create and freshness infrastructure.
Plain recipes require no Spring/Mockito; recognized Spring intent selects the
same existing Spring recipe. Unsupported constructors retain a throwing hook.
The Spring-only action/tool remain available with their original gate/payload.

The complete suite passes **1,749 normal tests**, **seven actual-host tests** and
**20 release-checker tests**, with no failures/errors/skips. Packaging and
project/structure checks pass; binary compatibility is **Compatible**, with four
deprecated and six experimental API notices. Live metadata/schema, external
constructor refresh, exact Spring reuse, existing semantic regressions and
13-session concurrent isolation checks pass.

Actual MCP plain scaffolds compile/run as two smoke tests without Spring/Mockito.
Adapted plain behavior tests pass three cases; a disposable greeting regression
fails two, then restoration passes three. The complex Spring scenario uses a
manual constructor, two mocks, explicit generic binding and custom session.
Codex adapts only its new two-case test using existing MCP context and normal
editing/testing tools. The demo passes eight cases, fails the two expected price
assertions after a disposable mutation, then passes eight after restoration.
Authored checkout inputs stay unchanged during all regression checks.

See [the contract](wicket-test-planning.md) and [portable evidence](test-planning-evidence.json).
No production-application evaluation or new release publication is claimed;
fresh graphical/client release verification is required for the new bytecode.

## Preview 2 release readiness

Version `0.1.0-preview.2` passes the complete release gate: 1,749 normal tests,
seven actual-host tests and 20 release-checker unit tests, plus packaging and
Compatible binary verification with the existing four deprecated/six experimental
API notices. The refreshed graphical sandbox/JAR and Codex/Claude calls cover
the eight-tool build, sidebar analysis and the plain-test preview. It exits
normally with MCP disabled again. Current [setup](mcp-setup-evidence.json) and
[release evidence](mcp-release-evidence.json) identify the exact artifact.

The updated landing page includes planning and agent workflows, points to the
versioned Preview 2 download, and passes responsive light/dark and no-JavaScript
checks. Publication is explicitly requested; application evaluation remains deferred.
