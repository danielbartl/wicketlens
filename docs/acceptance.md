# WicketLens MVP acceptance record

For the subsequent Wicket 11 source baseline, see the
[upgrade review](wicket-11-upgrade.md) and the verification entry at the end of this record.

Original verification on **2026-11-01** for the local **0.1.0-SNAPSHOT** build. All nine
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

## Agreeing unknown Java branches

The next Java slice replays bounded unknown `if` choices through the existing
source interpreter and upgrades only identical complete component graphs. IDs,
concrete component types, and parent paths must agree; captions/model values and
the actual runtime branch remain unknown. Optional/conflicting paths, duplicate
IDs, unsafe effects, unsupported graph kinds, or exhausted limits retain the
conservative fallback. Eight paths share the 5,000-step budget.

Primary and alternative construction/ID origins remain values in the report.
Both Java literals in analyzed page files support navigation, coloring and
missing-markup problem ranges; helper files keep the existing page-discovery boundary.
Paired rename and Java completion are disabled for merged nodes. The portable
`BranchMergePage` exercises three bindings with alternative parent/child
allocations. No framework/runtime dependency is added.

MCP decision: extend the existing structure/context export with additive
`branchMerged`, `idRenameSafe`, `javaLocations`, and `idLocations` metadata;
validation consumes the same graph findings. No new tool or schema version is
needed. All consulted alternative source files join freshness checks, while
existing project/module isolation, cancellation, read-only behavior and response
bounds remain in force. See [the contract and tests](conditional-setup.md).

Verification passes **1,774 normal tests**, including **25 new branch cases**,
and **seven actual JetBrains MCP host tests**, with no failures/errors/skips.
Packaging, plugin project configuration and structure checks pass. Binary
verification against IDEA IU-262.10968.63 is **Compatible**, retaining the existing
four deprecated and six experimental API notices. The portable demo compiles
offline with Maven. No new graphical/client release walkthrough is claimed.

This implementation is separate from the published Preview 2 artifact and its
recorded graphical/client evidence. It does not claim a new release or
production-application evaluation.

## Partial Java branch graphs

The next slice keeps the bounded source interpreter and proves common typed
parent paths across all reviewed setup paths. Optional components and conflicting
type/parent alternatives are retained as separate conditional nodes. Ancestor
types participate in grouping, and allocations that move between parents receive
distinct snapshot IDs. A conditional parent cannot give its descendants confirmed
bindings. Known conditional paths suppress missing-pair claims at that path and
below, while unrelated common/missing bindings remain available.

Unsupported effects, unknown IDs, duplicate sibling IDs within a concrete path,
unsupported graph kinds, and budget exhaustion still reject the entire upgrade.
The existing eight-path/5,000-step limits remain; the merged union is bounded at
256 components. Source dependencies include all branches.

MCP decision: reuse structure/context/validation with additive `conditional: true`
metadata and `UNRESOLVED_BRANCH` findings/causes. No new tool or schema version.
Both tool descriptions and client documentation describe possible variants,
ambiguity and retained runtime uncertainty. The sidebar shows conditional status;
editor problems exclude conditional bindings and existing rename/completion/fix
guards remain conservative. `PartialBranchPage` demonstrates one common title
and conditional details. Application evaluation and a new release remain outside
this implementation slice.

Verification passes **1,798 normal tests**, including **24 new partial-graph
cases**, and **seven actual JetBrains MCP host tests**, with no failures,
errors or skipped tests. Packaging, plugin project configuration and structure
checks pass. Binary verification against IDEA IU-262.10968.63 is **Compatible**,
with the same four deprecated and six experimental API notices. The portable demo
compiles offline with Maven. These results cover the development sources; the
published Preview 2 artifact and its graphical/client evidence are unchanged.

## Unknown Java ternaries

Unknown reviewed primitive `?:` conditions now use the existing bounded branch
replay and typed parent-path graph merge. Common IDs/types/parents remain
confirmed; differing IDs/types/receivers stay conditional. Supported values
include component/provider creation, immutable configuration, reviewed read-only
accessors, captions/model parameters and locals. No runtime model/branch value
is inferred, and known conditions still skip dead operands.

Constructor argument processing now retains each evaluated value and reuses it
for ID tracing, including forwarding from a nonfirst argument. Unknown ID
ternaries and scalar accessors therefore consume one choice per actual argument
evaluation. Existing numeric promotion, assignment, nullable-unboxing, purity
and fresh-factory guards remain. Opaque root calls nested inside constructor
arguments/possible operands now invalidate fallback attachment confidence too.
Dynamic IDs still retain ordinary conservative direct observations when expansion
fails; no merged proof is published for an unsupported path.

MCP decision: reuse the existing structure/context/validation tools and their
branch origin/conditional metadata. No new tool, schema field or dependency is
needed. Tool/client documentation explains the shared `if`/ternary budget:
three choices, eight paths, 5,000 steps and a 256-component merged union.
The `TernarySetupPage` demo has one matched title and conditional summary/details.
New cases cover ordinary/forwarded ID single evaluation, factories/providers,
receivers, typing/unboxing/effects, depth/budget, editor origins, MCP projection,
read-only behavior, alternative-source freshness and cancellation.

Verification passes **1,843 normal tests**, including **44 new ternary cases**
and the additional graph-matcher fallback regression, plus **seven actual
JetBrains MCP host tests**, with no failures/errors/skips. Packaging, plugin
project configuration and structure checks pass. Binary verification against
IDEA IU-262.10968.63 is **Compatible**, retaining the four deprecated and six
experimental API notices. The portable demo compiles offline with Maven.
The published Preview 2 artifact, graphical/client evidence and deferred
application evaluation are separate from this development verification.

## Correlated stable boolean conditions

Stable unknown primitive boolean parameters and unchanged local snapshots now
retain invocation-local identities. Direct aliases, negation, reviewed reads and
existing audited private final storage preserve those identities. The first
branch decision records a path fact; complementary/repeated checks reuse it.
Independent inputs, local snapshots and helper invocations receive distinct
identities even when parameter names/PSI declarations match. Facts never leave
the analysis session or become ID literal origins.

The slice reuses the bounded interpreter and typed parent-path graph merge.
Common bindings retain both origins; optional components remain conditional.
Writes, mutable/nonprivate fields, boxed conditions and reachable opaque/effectful
calls keep their existing guards. General compound predicates, repeated unknown
comparisons and unknown result calls are not correlated by text. There is no
boolean constraint solver. The budget remains three independent decisions,
eight paths, 5,000 steps and a 256-component union; repeated bound-symbol checks
consume no extra decision.

MCP decision: reuse the existing structure/context/validation tools and branch
metadata. No new tool, schema field or dependency is needed. Descriptions and
client documentation explain stable correlation without exposing runtime flag
values or internal facts. Tests cover identity/isolation, purity/mutation guards,
budgets, editor origins and safe edits, deterministic/read-only MCP projections,
alternative-source freshness, cancellation and the portable
`CorrelatedSetupPage` demonstration.

Verification passes **1,887 normal tests**, including **44 new correlation cases**,
and **seven actual JetBrains MCP host tests**, with no failures/errors/skips.
Packaging, plugin project configuration and structure checks pass. Binary
verification against IDEA IU-262.10968.63 is **Compatible**, with the same four
deprecated and six experimental API notices. The portable demo compiles offline
with Maven. These results describe the development sources; publication,
graphical/client release evidence and application evaluation remain separate.

## Computed IDs in branch merging

Known computed values now participate in the existing typed parent-path merge.
The reviewed scalar/constant evaluator is reused; different expressions with
equal ID values can establish a common binding, while differing values/types/
parents remain conditional. Unknown/unsafe computations, invalid/duplicate paths,
unsupported effects and exhausted budgets retain the conservative fallback.
Queues/repeaters and broader expression execution are unchanged.

Per-origin computation locations distinguish mixed literal/expression alternatives
independently of primary-path order. A computed-containing node carries the
aggregate computed flag, but genuine whole literal alternatives retain read-only
references/coloring. Equal-valued expression fragments do not acquire ID behavior.
Computed-only groups retain report navigation without invented literal pairs.
Missing-markup problems cover each literal or complete expression range. All
merged IDs retain rename/Java-completion guards.

MCP decision: extend the shared exporter used by structure/context with additive
`computedId: true`, `idRenameSafe: false` and typed `idOrigins` entries containing
`location` and LITERAL/COMPUTED `kind`. This also describes ordinary computed
IDs; literal-only payloads and schema versions remain unchanged. Existing merged
`idLocations` arrays and validation findings are preserved. No new tool or
dependency is added. Tool/client documentation explains whole-expression origins.

The portable `ComputedBranchPage` combines two matched computed-containing IDs
and a conditional hint. Tests cover mixed-origin order, scopes/parents, arithmetic,
source/binary/JDK constants, factories/providers, correlation, invalid/unknown/
unsafe values, editor ranges/fragment guards, deterministic/read-only MCP
projection, response limits, source freshness and cancellation.

Verification passes **1,930 normal tests**, including **42 new computed-branch
cases** and the binary-constant branch case, plus **seven actual JetBrains MCP
host tests**, with no failures/errors/skips. Packaging, plugin project
configuration and structure checks pass. Binary verification against IDEA
IU-262.10968.63 is **Compatible**, retaining the four deprecated and six
experimental API notices. The portable demo compiles offline with Maven.
These are development-source checks; the published preview, graphical/client
release evidence and deferred application evaluation remain separate.

## Queue placement across unknown setup branches

Pending queue declarations now participate in the existing bounded typed-path
merge. Invocation-local value metadata retains feasible-path membership; the
existing markup queue resolver runs separately for each path with one shared
4,096-container-visit budget per resolver pass. This preserves direct-child and
nearest-queue precedence without allowing a conditional component to hide an
alternative placement. Exhaustion retains the original unresolved queues.

A queued binding gains a parent only if every path containing it proves the same
unique parent. Optional queues retain conditional presence, unresolved confidence
and branch causes after placement. Missing containers, changing parents,
ambiguous markup, headers and component-region boundaries preserve queue
uncertainty. Known declaring scopes constrain absence suppression, including for
descendants of unplaced queued containers. Scope changes and exclusive add/queue
alternatives retain separate conditional variants. Repeater template branch
merging, mutable setup and broader predicates remain outside this slice.

MCP decision: reuse the structure/context/validation tools and shared value
snapshots/exporter. Existing `queuedInNodeId`, `parentNodeId`, confidence,
conditional flags, origins and causes express the new results. No new tool,
response field, schema version or dependency is needed. The structure tool
description and client contracts explain per-path placement and distinguish a
known parent from proven presence. Internal path membership/counts are not
exported. Existing project selection, committed PSI, freshness, cancellation,
read-only behavior and response bounds continue through the shared pipeline.

The 41 new `WicketQueueBranchMergeTest` cases cover placement agreement and
conflict, conditional shadowing and container types, optional parents/children,
computed/literal origins, repeated IDs in separate scopes, region/header and
malformed-markup guards, unsupported effects and repeated operations, bounded
exploration, scoped uncertainty, editor navigation/rename/completion guards,
deterministic read-only MCP structure/context/validation, source/markup freshness,
cancellation and the shared traversal budget. The portable `QueueBranchPage`
has two matched bindings (`content` and `title`) plus conditional `details` and
compiles offline with Maven. The expanded focused run passes **121 tests** with
no failures or errors.

Verification on 2026-11-02 passes **1,971 normal tests** and **seven actual
JetBrains MCP host tests**, with no failures, errors or skips. `buildPlugin`,
`verifyPluginProjectConfiguration` and `verifyPluginStructure` pass. Binary
verification against IDEA IU-262.10968.63 is **Compatible**, with the existing
four deprecated and six experimental API notices. The ZIP contains only the
WicketLens JAR; all 303 Kotlin class files match the current build. No additional
dependencies are bundled. `git diff --check` passes.

Reproduction uses Java 25:

```sh
./gradlew test buildPlugin verifyPluginProjectConfiguration verifyPluginStructure
./gradlew test -PmcpHostTests=true verifyPlugin
mvn -o -q -f examples/demo/pom.xml compile
```

These checks cover the development sources and local package. Published Preview
2, graphical/client release evidence and the deferred real-application accuracy
and performance evaluation remain separate.

## Repeater templates across unknown setup branches

Reviewed ListView/RepeatingView item snapshots now participate in the existing
bounded typed-path merge. Merge identities include repeater kind, template status
and a distinct logical item segment; logical items retain null runtime IDs and
consume no markup/context path segment. The interpreter's existing callback,
source-constructor, item-ownership, purity and capture guards remain authoritative.
Informational template/count warnings are unioned across paths; scope/effect
warnings still reject the whole upgrade. No second population analyzer is added.

Common item children retain `MATCHED` findings with `INFERRED` confidence.
Optional/conflicting repeaters, items and children retain unresolved branch
presence. Conditional path calculation skips logical items, keeping uncertainty
within the affected subtree and preserving unrelated missing-pair findings.
Alternative allocations may share one item template, but duplicate IDs or
multiple item constructions on a single feasible path still reject the upgrade.
Different kinds/types and ordinary containers remain distinct conditional variants.
Row existence/counts, runtime item IDs, model values and per-row branch outcomes
are never inferred. Reviewed item-local queues reuse per-path placement, and
outer queues still cannot cross item boundaries.

MCP decision: reuse structure/context/validation tools, value snapshots and the
shared exporter. Existing repetition/template fields, merged origins,
conditional flags, matching confidence and causes express all new results.
No new tool, response field, schema version or dependency is needed. The structure
tool description and client contracts explain inferred templates, logical paths
and unresolved optional structure. Overlapping alternatives require a current
node ID. Shared project selection, freshness, cancellation, read-only behavior
and response bounds remain in force.

`WicketRepeaterBranchMergeTest` covers agreeing/partial callback graphs, alternate
repeater/item allocations, nested and optional templates, differing kinds/types,
computed/literal origins, symbolic foreach/onPopulate bodies, fields/helpers,
item-local queue ambiguity, scoped absence suppression, source/capture/model-call
and ownership guards, exploration bounds, editor navigation and editing guards,
deterministic read-only MCP structure/context/validation, overlapping selectors,
external callback freshness, UTF-8 response bounds and cancellation. The portable
`RepeaterBranchPage` combines two matched repeater IDs, two inferred common item
bindings and a conditional queued detail. The previous queue test now checks that
an optional queued repeater and its children remain conditional.

Verification on 2026-11-02 passes **2,012 normal tests**, including **41 new
repeater branch cases**, and **seven actual JetBrains MCP host tests**, with no
failures, errors or skips. The initial focused run passed 114 tests; the final
repeater/matcher/partial-branch run passed 96 tests. Packaging, plugin project
configuration and structure checks pass. Binary verification against IDEA
IU-262.10968.63 is **Compatible**, with the existing four deprecated and six
experimental API notices. The portable demo compiles offline with Maven. The ZIP
contains only the WicketLens JAR; all 303 Kotlin class files match the current
build, and no additional dependencies are bundled. `git diff --check` passes.

Reproduction uses Java 25:

```sh
./gradlew test buildPlugin verifyPluginProjectConfiguration verifyPluginStructure
./gradlew test -PmcpHostTests=true verifyPlugin
mvn -o -q -f examples/demo/pom.xml compile
```

These results cover development sources and the local package. Publication,
graphical/client release evidence and the deferred real-application accuracy and
performance evaluation remain separate.

## Compound predicates over stable boolean inputs

Reviewed `&&`/`||` expressions now retain invocation-local groups over existing
stable primitive boolean identities and known boolean values. Negation, unchanged
aliases, reviewed accessors/helper parameters and existing private final storage
preserve those groups. Bounded branch/ternary replay chooses needed input facts
in short-circuit order and reuses them across later predicates. Complementary
and De Morgan checks can therefore establish one common binding without deriving
identity from expression text or inventing runtime flag values.

Known independent logical results retain folding without new exploration choices.
The existing operand purity audit, short-circuit skips and all setup validation
remain in force. Possible effects, mutation, boxed inputs and unsupported reads
cannot become a compound proof. Separately reevaluated unknown comparisons,
untracked unknown calls, eager operators and boolean equality retain their prior
contracts. A once-stored comparison may supply a stable boolean input. This slice
does not add numeric constraints or general boolean constraint solving.

The shared three-choice/eight-path/5,000-step limits remain; choices track needed
stable input decisions. Group inspection also shares that step budget and has a
12-level value-depth bound, including groups built through aliases. Common,
conditional, computed-origin, queue-placement and inferred repeater-template
behavior continues through the existing graph merge and matcher. Merged IDs keep
their navigation and conservative rename/completion guards.

MCP decision: reuse the existing structure/context/validation tools and shared
value snapshots/exporter. Existing merged origins, conditional flags, confidence
and causes describe the new facts. No tool, response field, schema version or
dependency is added. Internal groups/input facts stay inside the analysis session.
The structure description and client contracts explain the supported stable
predicates and retained comparison/effect limits. Project selection, committed
reads, freshness, cancellation, response bounds and read-only behavior continue
through the existing request pipeline.

`WicketCompoundBooleanTest` covers complementary/nested and reordered predicates,
aliases/accessors/storage, mixed literal/computed origins, ternaries, queues,
inferred repeater children, optional scopes and invalid implication guards,
constant folding, short-circuit/eager effect guards, independent inputs and
comparisons, mutation/unboxing, exploration/value-depth bounds, native editing,
shared deterministic/read-only MCP projections, external predicate freshness,
UTF-8 response bounds and cancellation. The portable `CompoundSetupPage` has one
matched computed-containing `title` with both origins and conditional `details`.

Verification on 2026-11-02 passes **2,051 normal tests**, including **39 new
compound-predicate cases**, and **seven actual JetBrains MCP host tests**, with
no failures, errors or skips. The initial focused run passed 197 tests; the final
compound/ternary/repeater run passed 124 tests. Packaging, plugin project
configuration and structure checks pass. Binary verification against IDEA
IU-262.10968.63 is **Compatible**, retaining the existing four deprecated and six
experimental API notices. The portable demo compiles offline with Maven. The ZIP
contains only the WicketLens JAR; all 304 Kotlin class files match the current
build, with no additional bundled dependencies. `git diff --check` passes.

Reproduction uses Java 25:

```sh
./gradlew test buildPlugin verifyPluginProjectConfiguration verifyPluginStructure
./gradlew test -PmcpHostTests=true verifyPlugin
mvn -o -q -f examples/demo/pom.xml compile
```

These are development-source/local-package checks. Publication, graphical/client
release evidence and deferred real-application accuracy/performance evaluation
remain separate.

## Primitive boolean equality and eager predicate operators

Stable primitive `==`, `!=`, `&`, `|` and `^` expressions now reuse the existing
invocation-local boolean groups. AND/OR groups retain reviewed value relationships;
XOR/parity and inverted parity represent inequality/equality. Same-token equality
chains retain the parity of each operation; parsed mixed-token grouping is
preserved. Aliases, negation, reviewed accessors/helper parameters and existing
private final storage preserve input identities without using expression text.

Every required eager source operand is audited left to right before group
inspection or proof. Known independent results can still fold without decisions,
but effects, possible unboxing and division risks cannot hide behind decisive
constants. Group reasoning may avoid unnecessary input choices only after the
audit. Existing integral scalar folding and short-circuit source evaluation
remain unchanged. Boxed/reference equality, mutable inputs, untracked calls and
separately reevaluated unknown numeric comparisons retain conservative guards;
numeric range constraints remain pending. A once-stored comparison may supply a
stable boolean input. The contract links the JLS boolean equality/logical rules.

No additional interpreter, dependency or runtime execution is introduced. The
same three-choice/eight-path/5,000-step and 12-level group-depth bounds apply.
Existing graph merge, scoped conditional uncertainty, per-path queue placement,
inferred repeater templates, source-origin navigation and editing guards are
reused.

MCP decision: reuse structure/context/validation tools, shared value snapshots
and exporter. Existing merged origins, confidence, conditional flags and causes
express all new facts; internal groups and runtime input values are not exported.
No tool, response field or schema version is added. Tool/client documentation
explains primitive-only support and mandatory eager auditing. Existing project
selection, committed reads, freshness, cancellation, read-only behavior and
response bounds continue through the shared request pipeline.

`WicketBooleanOperatorsTest` covers truth tables, equality/inequality and XOR
complements, equality/parity chains and mixed-token grouping, self identity,
aliases/accessors/storage, conditional scopes, queue and inferred repeater
integration, eager effects/unboxing/division, independent inputs and numeric
comparisons, mutation, exploration/value-depth bounds, editor origins and guards,
shared deterministic/read-only MCP projections, external accessor freshness,
UTF-8 response bounds and cancellation. The portable `BooleanOperatorsPage` has
one matched computed-containing `title` with both origins and conditional `details`.

Verification on 2026-11-02 passes **2,098 normal tests**, including **47 new
boolean-operator cases**, and **seven actual JetBrains MCP host tests**, with no
failures, errors or skips. The initial focused run passed 197 tests; the final
operator/ternary/repeater run passed 132 tests. Packaging, plugin project
configuration and structure checks pass. Binary verification against IDEA
IU-262.10968.63 is **Compatible**, with the existing four deprecated and six
experimental API notices. The portable demo compiles offline with Maven. The ZIP
contains only the WicketLens JAR; all 305 Kotlin class files match the current
build, and no additional dependencies are bundled. `git diff --check` passes.

Reproduction uses Java 25:

```sh
./gradlew test buildPlugin verifyPluginProjectConfiguration verifyPluginStructure
./gradlew test -PmcpHostTests=true verifyPlugin
mvn -o -q -f examples/demo/pom.xml compile
```

These checks cover development sources and the local package. Publication,
graphical/client release evidence and deferred real-application accuracy and
performance evaluation remain separate.

## Stable integral threshold comparisons

Unknown primitive integral parameters and once-initialized local snapshots now
retain invocation-local value identities and their primitive domains. Direct
aliases, implicit widening, reviewed scalar/predicate accessors and existing
audited private final storage preserve tracked identities. Unknown arithmetic
and explicit casts do not retain relationships to their operands; storing one
such result can create a new local identity. Independent reads/calls/snapshots
never share identity merely because their source text or parameter names match.

One tracked integral input compared with a known reviewed constant uses the
existing boolean symbols and bounded group replay. All six operators support
exact complements and reversed operands. Integer cuts normalize adjacent forms
such as `> 0`, `>= 1` and their `< 1` complement. Equality/inequality use their
own facts. Same-identity self comparisons and primitive out-of-domain comparisons
have their exact known meaning. Long endpoints are guarded before incrementing
a cut; threshold keys never pass through floating-point conversion.

This slice does not combine distinct thresholds, equality versus ordering, or
two distinct unknown inputs into numeric ranges. Separately recomputed unknown
arithmetic remains uncorrelated. Comparison facts never materialize a runtime
number or computed ID, even after equality with a constant. All operand purity,
mutation, boxing, dispatch, source and division/exception guards remain. The
shared three-choice/eight-path/5,000-step and group-depth bounds are retained;
distinct predicate facts can consume separate choices for one numeric input.

Existing typed graph merging, conditional scope handling, computed/literal
origins, per-path queue placement, inferred repeater confidence and editor guards
are reused. Earlier boolean/repeater tests now assert canonical comparison
sharing when they capture the same reviewed integral input.

MCP decision: reuse structure/context/validation tools, value snapshots and the
shared exporter. Existing origins, confidence, conditional flags and causes
describe the new facts. No tool, response field, schema version or dependency is
added. Internal numeric identities, thresholds and runtime values are not
exported. Tool/client documentation explains the supported threshold identities
and retained range-solving limits. Project selection, freshness, cancellation,
read-only behavior and response bounds remain in the shared request pipeline.

`WicketIntegralComparisonTest` covers all comparison complements/reversal,
equivalent cuts/constants, primitive domains and long endpoints, aliases and
widening, independent snapshots/calls, audited storage/accessors, boolean/ternary/
queue/repeater integration, scoped uncertainty, no guessed IDs, mutation/unboxing/
effects/division guards, short-circuit versus eager auditing, bounds, native
editing, deterministic read-only MCP projections, external threshold freshness,
UTF-8 response bounds and cancellation. `IntegralComparisonPage` has a common
computed-containing `title` with both origins and conditional `details`.

Verification on 2026-11-02 passes **2,153 normal tests**, including **55 new
integral-comparison cases**, and **seven actual JetBrains MCP host tests**, with
no failures, errors or skips. The initial focused run passed 222 tests; the final
comparison/scalar/bitwise/accessor run passed 214 tests. Packaging, plugin project
configuration and structure checks pass. Binary verification against IDEA
IU-262.10968.63 is **Compatible**, retaining the existing four deprecated and six
experimental API notices. The portable demo compiles offline with Maven. The ZIP
contains only the WicketLens JAR; all 307 Kotlin class files match the current
build, with no additional bundled dependencies. `git diff --check` passes.

Reproduction uses Java 25:

```sh
./gradlew test buildPlugin verifyPluginProjectConfiguration verifyPluginStructure
./gradlew test -PmcpHostTests=true verifyPlugin
mvn -o -q -f examples/demo/pom.xml compile
```

These are development-source/local-package checks. Publication, graphical/client
release evidence and deferred real-application accuracy/performance evaluation
remain separate.

## Per-input integral ranges and excluded values

Each analysis session now associates a closed interval and excluded equality
values with each existing stable integral identity. Choosing a comparison
intersects this domain; later equality/ordering tests can be settled without
new choices. This removes impossible combinations of distinct thresholds and
equality/order facts. Independent inputs retain independent domains, and aliases,
implicit widening and reviewed accessors/storage keep their existing identities.

Excluded endpoints are trimmed with guards at both long endpoints; finite
exclusions come only from bounded choices. No primitive domain is enumerated.
Boolean groups refine only the predicates actually needed for that path, so
true OR and false AND results do not force constraints from both operands.
Negative/zero/positive partitions have three feasible paths. Necessary decisions
still obey the existing three-choice/eight-path cap; a fourth needed decision
rejects the upgrade. Queries/refinement share the 5,000-step budget and normal
cancellation checks. Existing group-depth and source limits remain in force.

Prior facts may settle a later source test and skip its dead short-circuit
operand/body. They never waive eager or initial unresolved-operand auditing.
Even singleton ranges do not supply numeric values to computed IDs, arithmetic,
casts or division checks. Mutation, boxing, floating point, opaque reads and
exception guards remain. Relations between distinct unknown inputs and symbolic
arithmetic are still pending. Existing graph/matcher behavior retains optional
presence, scoped absence suppression, inferred repeater confidence, queue scope
and placement, origin metadata and conservative editing guards.

MCP decision: reuse structure/context/validation tools, shared value snapshots
and exporter. Existing origins, confidence, conditional flags and causes express
the new facts; intervals, exclusions and runtime numbers remain internal. No
tool, response field, schema version or dependency is added. Tool/client
documentation describes the range subset and retained limits. Project selection,
freshness, cancellation, response bounds and read-only behavior stay in the shared
request pipeline.

`WicketIntegralRangeTest` covers feasible partitions, stronger/weaker bounds,
equality and interior/endpoint exclusions, tiny domains/singletons, long endpoints,
aliases/accessors/storage, boolean-group implications, short-circuit versus eager
auditing, no guessed values/arithmetic, conditional scopes, queue/repeater reuse,
exploration bounds, native editing, exact deterministic/read-only MCP projections,
external boundary freshness, UTF-8 response bounds and cancellation. The demo
`IntegralRangePage` has one common computed-containing `title` with three origins
and conditional `details`. Its optional check stays within the existing decision
budget. Two earlier threshold tests now assert the three-way partition and four
feasible ranges of three ordered cuts.

Verification on 2026-11-02 passes **2,204 normal tests**, including **51 new
integral-range cases**, and **seven actual JetBrains MCP host tests**, with no
failures, errors or skips. The corrected range/threshold/boolean run passed 189
tests; the final range/scalar/accessor/repeater run passed 193 tests. Packaging,
plugin project configuration and structure checks pass. Binary verification
against IDEA IU-262.10968.63 is **Compatible**, retaining the existing four
deprecated and six experimental API notices. The portable demo compiles offline
with Maven. The ZIP contains only the WicketLens JAR; all 308 Kotlin class files
match the current build, with no additional bundled dependencies.
`git diff --check` passes.

Reproduction uses Java 25:

```sh
./gradlew test buildPlugin verifyPluginProjectConfiguration verifyPluginStructure
./gradlew test -PmcpHostTests=true verifyPlugin
mvn -o -q -f examples/demo/pom.xml compile
```

These are development-source/local-package checks. Publication, graphical/client
release evidence and deferred real-application accuracy/performance evaluation
remain separate.


## Mounted URLs browser — direct Java registrations

A separate native WicketLens content tab now groups direct `mountPage` declarations
by concrete Java WebApplication, module and source set. Search/filtering, Java,
default HTML and registration navigation reuse value locations and snapshot
freshness guards. Analyze page delegates to the existing Components service and
selects that content without changing the editor-follow preference. The existing
Components hierarchy, Spring tools, inspections and editor flows remain in place.
The portable `RoutesApplication` mounts `CustomerPage` at two literal patterns.

Discovery covers the application's own zero-argument `init()`, reviewed constant
String paths and resolved Page class literals. Conditional/dynamic declarations
retain explicit status and known locations. Inherited setup, helpers, deferred
bodies, other receivers and custom/package/resource registrations have coverage
notes instead of fabricated routes. No application code is executed. Paths are
application-relative and no declaration is claimed to be an active runtime URL.
Traversal, application, mount, dependency and request limits remain explicit.

MCP decision: add focused read-only `wicket_get_routes`, since project-wide route
facts cannot be expressed by an existing single-page request. Both clients reuse
one analyzer and immutable value snapshots. The page request's external refresh,
smart/committed reads, deadline, cancellation/disposal and final freshness pipeline
is shared through a small generic read operation; existing per-page contracts and
schemas are unchanged. Route snapshots also detect structural changes after empty
discovery. Native navigation accepts only current snapshot locations. MCP has no
sidebar, navigation, clipboard or source-writing operation and separately rejects
JSON beyond 512 KiB UTF-8. No new plugin dependency is introduced. See
[Mounted URLs](mounted-urls.md) and [the tool contract](mcp-routes.md).

Tests cover direct/dynamic/conditional source discovery, source constants and
source-set gates, skipped/inherited setup, deterministic value projections,
application/traversal/mount/UTF-8 bounds, cancellation, pending-document freshness
and new-file invalidation. Native tests exercise filtering, literal text rendering,
Java/HTML/registration navigation, stale/tampered-location rejection and Components
handoff. Async tests cover indexing waits, cancellation of one request, deadline
recovery, committed-document waits and read-only selection behavior. The actual
bundled host test discovers/invokes the tool over HTTP and verifies read-only
metadata; colliding application/module names in two open projects remain isolated.


Verification on 2026-11-02 passes **2,253 normal tests**, including **49 new route
tests**, and **eight actual JetBrains MCP host tests**, with no failures, errors
or skips. The final focused route/page-lifecycle run passes 87 tests. Packaging,
plugin project configuration and structure checks pass. Binary verification on
IDEA IU-262.10968.63 is **Compatible**, retaining four deprecated and six
experimental API notices. The portable demo compiles offline with Maven. The
local ZIP contains only the WicketLens JAR; all 345 compiled Kotlin classes match
that package. Documentation links and `git diff --check` pass.

Reproduction uses Java 25:

```sh
./gradlew test buildPlugin verifyPluginProjectConfiguration verifyPluginStructure
./gradlew test -PmcpHostTests=true verifyPlugin
mvn -o -q -f examples/demo/pom.xml compile
```

These are development-source, native UI-test, actual-host and local-package
checks. The new route tool has not been recorded in a graphical Codex/Claude
walkthrough or published release. Preview 2 remains the existing eight-tool
release. Deferred real-application accuracy/performance evaluation is unchanged.


## Mounted URLs — inherited setup and closed helpers

Route discovery now starts from the selected concrete application's effective
source init. An override reaches parent setup only through its explicit super
call. Private/final no-argument void instance helpers and explicit superclass
helper calls expand at their actual call sites, retaining source traversal order,
per-call conditional context, actual registration locations and textual setup
paths. Repeated helper calls have distinct deterministic observation IDs, and
siblings sharing an inherited init remain separate application groups. The native
view, navigation and existing Analyze page handoff reuse these value snapshots.

This is declaration discovery, not execution or an active route table. Open
virtual calls, helper arguments, static/value-returning helpers, interface
callbacks, deferred lambda/method-reference/anonymous bodies and compiled/external
application ancestry retain coverage limits. Recursive expansion stops at the
cycle. Per-application bounds are 12 source ancestry levels, eight active setup
methods and 128 invocations including the entry. Existing project-wide source,
step, mount, application, candidate, deadline and response-byte limits remain.
Source errors in reached setup/callers make reached mounts unresolved. Every
traversed source ancestor and constant dependency joins freshness tracking.

MCP decision: extend existing wicket_get_routes facts over the same analyzer and
snapshot. Existing mount/status/location/notes fields already express the new
facts, so no new tool, field or schema is introduced. Descriptions and client
contracts now document inheritance, helper closure, call paths and expansion
bounds. The shared committed/smart-read pipeline, project isolation,
cancellation/deadline handling, final freshness and read-only behavior remain.
The actual-host route fixture now uses inherited init plus a private helper in
each isolated project. The portable InheritedRoutesApplication demonstrates the
same pattern. Landing-page source copy is updated; no publication is implied.

The focused tests cover omitted/explicit/conditional super calls, inherited and
private/final helpers, private binding versus open dispatch, deferred/other/static
receivers, dynamic paths, source errors, multiple applications and repeated calls,
cycles and hierarchy/depth/invocation limits, source constants, source-set gates,
ancestor-document waits/freshness, read-only deterministic MCP projections,
response-byte limits and cancellation. Native UI tests navigate to the inherited
helper registration and reuse the existing Components analysis.


Verification on 2026-11-02 passes **2,293 normal tests**, including **40 additional
inheritance/helper cases**, and **eight actual JetBrains MCP host tests**, with
no failures, errors or skips. The final focused route run passes 89 tests.
Packaging, plugin project configuration and structure checks pass. Binary
verification against IDEA IU-262.10968.63 is **Compatible**, retaining the same
four deprecated and six experimental API notices. The portable demo compiles
offline. All 345 compiled Kotlin classes/resources match the current local ZIP,
which contains only the WicketLens JAR. Landing-page assembly, documentation and
asset/anchor links, and git diff --check pass.

Reproduction uses Java 25:

```sh
./gradlew test buildPlugin verifyPluginProjectConfiguration verifyPluginStructure
./gradlew test -PmcpHostTests=true verifyPlugin
mvn -o -q -f examples/demo/pom.xml compile
python3 site/build.py --base-url https://danielbartl.github.io/wicketlens/
```

These are development-source, native UI-test, actual-host and local-package
checks. A new graphical Codex/Claude walkthrough, website deployment, plugin
publication and the deferred real-application evaluation remain separate work.


## Mounted URLs — inline MountedMapper registrations

The shared route analyzer now observes inline
`mount(new MountedMapper(path, Page.class))` in the already-reviewed source init,
ancestor and closed-helper setup. It reuses constant evaluation, target/companion
resolution, registration navigation, conditional propagation, observation IDs and
the same value snapshots. No additional runtime dependency or parallel index is
introduced. The native registration action now describes route registrations
rather than only mountPage calls. A portable MountedRoutesApplication demonstrates
class-literal and explicit-encoder registrations.

Two- and three-argument constructors are recognized. Explicit encoders carry a
parameter-behavior coverage note. Supplier targets remain unresolved, and inline
anonymous mapper classes preserve known constructor facts but are unresolved
because their overrides/initialization are not reviewed. Invalid constructor or
registration resolution cannot produce a declared observation. The outer call
must resolve to framework WebApplication.mount rather than an application override.
Other receivers, unattached mapper construction, variables/fields, factories,
wrappers, named subclasses and root-mapper mutations are not interpreted as inline
registrations. These limits, unexpanded callbacks and later runtime changes remain
explicit; this is not an active runtime route table or a conflict inspection.

MCP decision: extend the existing wicket_get_routes facts through the same route
analyzer and snapshot. Existing path/page/status/location/notes fields express
these registrations without changing either schema version or adding a tool.
Descriptions and client docs cover suppliers, anonymous behavior, explicit
encoders and omitted registration forms. The existing committed reads, freshness,
project isolation, cancellation/deadline, traversal/response bounds and read-only
behavior remain shared. The actual-host fixture now mounts an inline mapper from
an inherited private helper in each isolated project. Native navigation and
uncommitted-ancestor waiting tests exercise the same shape.

Focused mapper tests cover class/HTML/registration links, explicit encoders,
suppliers, anonymous overrides and initializers, invalid constructors, parenthesized
syntax, conditional helper calls, repeated observations, unused construction,
variables/factories, root mapper mutation, subclasses/overrides/other receivers,
deferred callbacks, constant-dependency freshness, deterministic read-only MCP
projection/application filters, and the portable demo. Documentation, roadmap and
local landing-page development copy are updated; publication is separate.

Verification on 2026-11-03 passes **2,315 normal tests**, including **22 new
mapper cases**, and **eight actual JetBrains MCP host tests**, with no failures,
errors or skips. The focused route run passes 111 tests. Packaging, plugin
project configuration and structure checks pass. Binary verification against
IDEA IU-262.10968.63 is **Compatible**, retaining the existing four deprecated
and six experimental API notices. Current normal/host verification receipts and
the verifier receipt match the authored inputs and packaged bytecode/resources.
The development ZIP contains the same 345 production classes and only the
WicketLens JAR, with no additional bundled dependency. The portable demo compiles
offline; landing-page assembly, local documentation/assets/anchors and
git diff --check pass. Reproduction uses the Java 25 commands in the preceding
slice's verification section. These local checks do not publish a new release
or deploy the website; graphical client walkthroughs and real-application
accuracy/performance evaluation remain separate.


## 0.1.0 Release Candidate 1 stabilization

The candidate freezes the implemented feature scope at 0.1.0-rc.1. No semantic
feature is added in this pass. MCP decision: retain the nine existing tools and
response schemas; refresh the final artifact's graphical and client evidence.
Version metadata, candidate notes, roadmap, release checklist and local site copy
are updated. Source/build fingerprints require new full normal and host receipts
for this versioned artifact; Preview 2 evidence remains historical.

Validation uses owned, isolated IDEA/client profiles and disposable project
copies. Published Preview 2 is installed first in the upgrade profile, followed
by the candidate through IntelliJ's native Install Plugin from Disk chooser and
third-party notice. A temporary UI-only Java instrumentation bridge lives outside
plugin sources/artifacts; it inspects and operates the owned IDE's native controls
and captures application windows. Normal IDE/client configuration is not used
as a test write target. Source-only external application smoke checks remain
separate from the deferred full real-application accuracy/performance study.

The graphical pass found a real Mounted URLs navigation failure: native mouse
callbacks on the target IDE do not necessarily hold read access, while the route
freshness guard read PsiDocumentManager.uncommittedDocuments without acquiring
it. A regression calling the guard without a read lock reproduced the exact
assertion before the fix. requireCurrent now owns a short ReadAction around the
freshness checks, preserving stale/project-disposal errors and read-only behavior.
Two regression cases cover fresh off-lock callers and rejection of pending edits
without committing them. All 53 focused route/editor/lifecycle tests pass after
the fix. MCP decision: the shared lifecycle guard is corrected for existing
callers; no tool, argument, response field or schema change is needed. The full
normal/host/package/client evidence must be refreshed for the changed bytecode.

A limited source-only smoke check copied five pages and supporting sources from
an external Wicket application into the owned fixture's configured Java/resource
roots. After correcting the temporary module/root setup, all five calls returned
ok, untruncated snapshots in approximately 0.12–0.85 seconds in this local run.
All five snapshots are partial and carry unresolved scope/constructor/variable/
markup findings; no confirmed bindings or accuracy percentage are established
by this sample. Unresolved custom framework setup remains an explicit release
limit. The original external checkout is unchanged and no external source or
application screenshot is included in public assets. This is not a runtime
application test, a representative benchmark, or the deferred feasibility study.

Final RC 1 verification on 2026-11-03 passes **2,317 normal tests**, **eight actual
MCP host tests**, and **20 release-checker tests**, with no failures/errors/skips.
The pre-fix regression fails with the exact read-access assertion; all 53 focused
route/editor/lifecycle tests pass with the fix. Packaging, project/structure and
full release-readiness checks pass. Binary verification against IU-262.10968.63
is Compatible with five deprecated and six experimental notices. The synchronous
freshness guard adds one ReadAction.run deprecation to the previous four; this
is recorded in the candidate notes and verifier report.
The final archive contains only the WicketLens JAR and the same 345 production
classes; its JAR exactly matches both isolated installations.

The repeated native Preview 2 upgrade uses the fixed candidate JAR and restart.
A native table double-click explicitly verifies readAccessBeforeDoubleClick=false
and successfully navigates to the source, with no severe IDE log entries after
fixed-candidate startup. Follow editor switches to ValidationPage and shows its
JAVA_ONLY/MARKUP_ONLY findings; class-root HTML navigation succeeds. Paired native
rename preview/application/multi-file Undo were exercised during stabilization;
the only subsequent production change is the separately retested freshness guard.
A fresh owned profile installs the candidate from ZIP with MCP excluded and
verifies the Spring service/FactoryBean source-candidate view and plain Wicket
scaffold preview (Copy enabled, Create disabled without a test root).

The final graphical MCP setup shows nine exposed tools with router-only mode off
and command confirmation enabled. Actual Codex CLI 0.166.0 and Claude Code 2.1.317
calls each return the inherited/direct and inline mapper routes through the
graphical server. Updated screenshots and redacted client evidence identify the
final production classes hash. Both owned IDEs close normally and the graphical
MCP profile is restored to disabled. Normal IDE/client settings are not test
write targets. Full real-application evaluation and normal user-profile
installation remain unclaimed.

Local artifacts: build/distributions/wicketlens-0.1.0-rc.1.zip and SHA256SUMS.txt;
full readiness report: build/reports/release-readiness.json. Published Preview 2
remains the existing download until the candidate is committed/published. The
local landing-page copy and release documentation describe that distinction.


## RC 1 publication

On 2026-11-03, the verified candidate was committed as 7b141947e96b1a9a6dc587d6e526b1a7e9fb4cfc
and pushed to master with the new annotated v0.1.0-rc.1 tag. The requested
[GitHub prerelease](https://github.com/danielbartl/wicketlens/releases/tag/v0.1.0-rc.1)
provides wicketlens-0.1.0-rc.1.zip and SHA256SUMS.txt. Both published assets were
downloaded again and match the verified local files byte-for-byte. The ZIP SHA-256
is a8b116e1d65a86c8913aa78f20526b17fb218e01c2b485c3112c83e59e78c83b. Previous
preview releases and tags remain available.

[Pages deployment 41620881402](https://github.com/danielbartl/wicketlens/actions/runs/41620881402)
completed successfully for that commit. The public
[landing page](https://danielbartl.github.io/wicketlens/) returns HTTP 200 and
exactly matches the assembled HTML, including the direct RC download link and
nine-tool feature description. No domain or Pages configuration was changed.
These publication actions were explicitly requested; the release checker itself
continues to report local verification only. Candidate feedback and fixes before
final 0.1.0 remain open.


## Mounted URLs landing-page screenshot

The landing page adds a fourth gallery tab and a visible route-section figure
using docs/images/mounted-urls.png. The image is an actual 1500×500 capture of
the published 0.1.0-rc.1 plugin's native floating WicketLens tool window, running
only the portable CustomerPage route examples in an isolated demo profile. It
shows five declarations across three applications, all source columns, and the
inherited setup details and static/runtime caveat. The window and splitter were
resized through native UI controls for readability; no image content was drawn,
replaced or fabricated, and no desktop/private project content is included.

The site assembler copies this existing documentation asset once, shared by the
gallery and route figure. Alt text, intrinsic dimensions, captions, full-size
links and existing keyboard tab navigation cover the new entry. MCP decision:
presentation-only documentation change; no semantic tool or schema change. The
plugin/release artifact and its prior verification remain unchanged.

Validation passes the static site assembly, local Markdown/HTML asset and anchor
checks, exact new-image bytes/dimensions and a real Chromium browser walkthrough.
The fourth tab updates the image/caption/alt/full-size links, keyboard navigation
wraps across all four tabs, and both the gallery and dedicated route figure
render at 1366px desktop and 390px mobile widths in light and dark themes without
horizontal overflow. The existing plugin release-readiness gate still passes.


## Wicket 11 source baseline (2026-11-10)

The fixture/demo baseline is now **Wicket 11.0.0**, with **Spring 7.0.9**,
**Jakarta Servlet 6.1.0** and **JUnit 6.1.3**. All four Maven demos target
**Java 21**. Gradle and Java PSI fixtures continue to use the installed Java 25
JDK and IDEA **2026.2.3 / IU-262.10968.63**. The released framework source review,
archive checksums, dependency alignment and MCP decision are in the
[upgrade record](wicket-11-upgrade.md).

Verification completed with no failures, errors or skips:

- Complete normal plugin suite: **2,317 tests**, including real-library PSI/editor
  analysis, Spring findings/dependencies, generated Page/Panel test compilation
  and execution, MCP projections, isolation, freshness, bounds and read-only checks.
- Separate actual MCP host suite: **eight tests**, including discovery, project
  selection, HTTP semantic responses, cancellation/deadline and disconnect behavior.
- Release-checker unit suite: **20 tests**.
- Main demo: **52 Java source files** compile; Spring inspection demo: **10 source
  files** compile. Both `clean verify` builds pass with Java 21.
- Plain tester demo: **three tests** pass; Spring tester demo: **eight tests** pass.
  Disposable regression/restoration checks pass for all three scenarios: `plain`,
  `spring-orders` and `spring-details`. Each negative control runs the full expected
  count and fails behavior assertions, with no setup errors or skips; restoration
  passes and authored inputs stay unchanged.
- Plugin packaging, project configuration and structure checks pass.
  Binary verification is **Compatible**, with the existing **five deprecated**
  and **six experimental** API usages.
- The local release-readiness checker passes against the current source inputs
  and complete normal/host receipts. The rebuilt ZIP SHA-256 remains
  `a8b116e1d65a86c8913aa78f20526b17fb218e01c2b485c3112c83e59e78c83b`, identical
  to the published RC 1 ZIP. Production classes also match its graphical evidence.

Reproduction (select Java 25 for Gradle; the Maven checks also pass on Java 21):

```sh
python3 -m unittest discover -s tools -p 'test_*.py'
./gradlew test
./gradlew test -PmcpHostTests=true buildPlugin verifyPluginProjectConfiguration verifyPluginStructure verifyPlugin
mvn -B -f examples/demo/pom.xml clean verify
mvn -B -f examples/spring-demo/pom.xml clean verify
mvn -B -f examples/plain-test-demo/pom.xml clean test
mvn -B -f examples/spring-test-demo/pom.xml clean test
python3 examples/spring-test-demo/verify_agent_workflow.py --project examples/plain-test-demo --scenario plain --offline
python3 examples/spring-test-demo/verify_agent_workflow.py --scenario spring-orders --offline
python3 examples/spring-test-demo/verify_agent_workflow.py --scenario spring-details --offline
python3 tools/check_release.py --output build/reports/release-readiness.json
```

MCP decision: no new semantic fact or operation, so the nine existing tools and
schemas continue to reuse the same analyzers/snapshots. The complete normal and
host suites exercise the upgraded libraries. No new graphical/client walkthrough,
plugin publication or website deployment is claimed. Historical release/framework
records retain their original Wicket 10.11.0 evidence. Full production-application
accuracy/performance evaluation and the upstream host cancellation/session-expiry
limitation remain deferred/open as previously documented.


## Final 0.1.0 artifact verification (2026-11-10)

The final **0.1.0** version passes fresh complete **2,317 normal** and **eight
actual MCP host** tests, plus the **20 release-checker tests**. No failures,
errors or skips are recorded. Packaging/project/structure checks pass, and binary
verification against **IU-262.10968.63** is **Compatible**, retaining the five
deprecated and six experimental API notices. The local release-readiness checker
passes for the exact final ZIP and current source/build inputs.

The ZIP SHA-256 is
`e594fad8ff128b2f7319d97cf1091ad8f09fc5c509087a46551e59adc48a3b28`.
Comparison with the published RC 1 ZIP shows identical production classes and
resources except the version value in `META-INF/plugin.xml` and
`META-INF/MANIFEST.MF`. All other entry bytes are unchanged. The retained RC 1
graphical/client evidence therefore identifies the same production classes;
no new final-version graphical walkthrough is claimed. Final artifact/test scope
and the package comparison are in [portable evidence](releases/0.1.0-evidence.json).

The [Wicket 11 review and demo verification](wicket-11-upgrade.md) apply to the
unchanged fixture/demo inputs: Wicket 11.0.0, Spring 7.0.9, Servlet 6.1.0,
JUnit 6.1.3 and Java 21 demos. Final release metadata does not change the semantic
analyzers, tools or schemas. Full real-application accuracy/performance evaluation
and the documented upstream cancellation/session-expiry limitation remain open.

README, final notes, MCP compatibility documentation, roadmap and landing-page
copy now identify 0.1.0. Static page assembly, local assets/anchors, final download
links and exact screenshot bytes pass. The page changes only text and links;
existing styles, scripts and screenshots are retained. The remote branch matches
local HEAD before release preparation and GitHub reports no open issues. User
authorization to publish final 0.1.0 is explicit. Publication and deployment are
recorded separately after their returned state and downloads have been verified.


## Final 0.1.0 publication (2026-11-10)

The user explicitly confirmed public final publication after reviewing the source
payload and destination. Verified source commit
`086655f464826e379892d39940fd98cdd9330929` and the annotated **v0.1.0** tag were
pushed atomically to the existing public `danielbartl/wicketlens` repository.
The [final GitHub release](https://github.com/danielbartl/wicketlens/releases/tag/v0.1.0)
is published, is not a draft or prerelease, and is returned as the latest stable
release. Its `wicketlens-0.1.0.zip` and `SHA256SUMS.txt` assets were downloaded again
and match the checked local files byte-for-byte. GitHub's asset digest and the
computed downloaded ZIP digest both equal
`e594fad8ff128b2f7319d97cf1091ad8f09fc5c509087a46551e59adc48a3b28`.
Earlier candidates/previews remain available; no existing release or tag was replaced.

The page copy includes Wicket 11.0.0 fixtures/demo compatibility, Java 21 demo
requirements, final 0.1.0 download/checksum links and the nine-tool MCP badge.
Its separate Pages deployment and live content verification are recorded after
completion. MCP decision: publication/copy only; no semantic tool or schema change.


## Final 0.1.0 landing-page deployment (2026-11-10)

The explicitly requested [Pages deployment 42845724240](https://github.com/danielbartl/wicketlens/actions/runs/42845724240)
completed successfully for source commit
`78df5b760083cbf84472943207f8fd76cfee2983`. The canonical public
[landing page](https://danielbartl.github.io/wicketlens/) was downloaded after
completion and exactly matches the assembled HTML byte-for-byte. It contains
Wicket 11.0.0 fixture/demo compatibility, Java 21 demo requirements, the final
0.1.0 ZIP/release/checksum links and the final-version nine-tool MCP badge.
No Wicket 10 text or RC 1 download/version strings remain in the served HTML.

The page was assembled through the existing manual GitHub Pages workflow.
No Pages settings, domain, DNS, styles, scripts or screenshot assets were changed.
Static local asset/anchor checks pass, and the published release ZIP/checksum
round-trip verification remains unchanged. The final source and release tag are
public; the working branch's subsequent commits only record publication and
finish presentation copy. MCP decision: documentation/presentation only; no
semantic tool or response-schema change.

## First Wicket Oat navigation milestone (development, 2026-11-12)

Implemented the supported source-layout subset in
[the contract](source-layouts.md), following the
[application investigation](wicket-oat-support.md). Confirmed bindings including
inherited layouts: HomePage 12, BadgePage 15, CardPage 14, MobileCapacityPage 4.
The read-only corpus checks required page IDs, bidirectional native references
on page-owned literals and shared structure/validation/context MCP results.
All 36 page sources pair with resource markup on Wicket 10.11.0 and 11.0.0.

Verification:

- Full default suite: **2,367 passed**, zero failures/errors/skips.
- Actual MCP host suite: **8 passed**, zero failures/errors/skips.
- Release-checker unit tests: **22 passed**.
- `buildPlugin`, `verifyPluginProjectConfiguration`, `verifyPluginStructure` and
  `verifyPlugin`: passed against IDEA **IU-262.10968.63**. Verdict: compatible,
  with 5 deprecated and 6 experimental API usages reported.
- Source/test/artifact receipts agree with the final implementation inputs.

[The evidence summary](wicket-oat-milestone-evidence.json) records input/corpus
hashes, counts and the development ZIP checksum. This build retains 0.1.0 version
metadata and has not been published or installed into the running IDE. Graphical
release readiness was not reevaluated. Fragments, component factories, custom
repeated templates and nested companions remain later milestones; paired rename
on new-pass bindings remains withheld.

MCP decision: extend facts through the existing shared analyzers and immutable
snapshots; keep all tools, parameters and response schemas unchanged. Updated
tool descriptions and client documentation; covered module isolation, unsaved
freshness, cancellation, bounded discovery, native agreement and read-only use.

## Second Wicket Oat navigation milestone: fragments (development, 2026-11-12)

Implemented [fragment definition and instance support](fragments.md), including
inherited definitions, repeated-use contexts, shared helper origins, projected
children and native definition navigation. The attachment ID, definition ID and
provider remain separate facts. No factory is executed merely to locate its
definition. Custom sourcing and Fragment subclasses remain opaque.

Both Wicket 10.11.0 and 11.0.0 read-only corpus runs pass. BasePage's top-bar
children raise confirmed bindings to HomePage **14**, BadgePage **17**, CardPage
**16**; MobileCapacityPage remains **4**. Eight Inbox/Dialog/Tabs definition
references navigate in both directions; those pages still have zero confirmed
graph bindings pending factory/template work. A Wicket 11 WicketTester oracle
confirms inherited reuse, independent child values and preview replacement.

Final checks: **2,393** normal tests, **8** actual MCP host tests and **22**
release-checker unit tests passed, with zero failures/errors/skips. Plugin build,
configuration and structure verification passed; binary verdict against
**IU-262.10968.63** is compatible, with 5 deprecated and 6 experimental API usages.
Source, test and artifact receipts match the final inputs. The
[evidence summary](wicket-oat-fragment-evidence.json) records the development ZIP
checksum. Version metadata remains 0.1.0; no installation, publication or updated
graphical release-readiness walkthrough was performed.

MCP decision: retain existing tools and request contracts, adding optional
`fragmentUses` and component `fragment` provenance in the shared schema-1 export.
Validation and component context reuse the same matching/snapshot pipeline.
Tests cover module isolation, unsaved freshness, cancellation, response and
discovery bounds, native agreement and read-only behavior. Tool descriptions and
client documentation were updated. Paired rename remains withheld.


## Third Wicket Oat navigation milestone: factories (development, 2026-11-12)

Implemented [bounded component factories and repeated source templates](factories.md).
Captured split-pane identities, source-owned Panel markup and nested generated
repeater scopes now compose with Fragment instances. No concrete generated IDs,
row counts, model values or post-event tree are inferred. Unsafe captured effects
and unsupported lifecycle/factory patterns retain uncertainty; new-pass paired
rename remains withheld.

Both Wicket 10.11.0 and 11.0.0 read-only corpus runs pass. Inbox now has **31
matched bindings: 26 confirmed and 5 inferred template bindings**, with nine
inherited sidebar findings unresolved. All 36 page sources still pair with their
resources; Home/Badge/Card/Mobile target results remain unchanged. Products,
OrderWizard and Accounts were inspected and remain unresolved for the reasons
recorded in the contract. Named nested companions/contextual editor targets are
next; no broader CRUD, wizard or tree lifecycle support is claimed.

A separate Wicket 11 WicketTester process verifies captured panes and two nested
generated levels with independent row values. The focused factory suites pass
31 tests covering source effects, synchronous Wicket 10 Ajax stateless-hint
binding, recursion, invocation bounds, cancellation, MCP response bounds,
unsaved freshness and read-only behavior. A separate fixture verifies
source/owned-resource isolation between modules with conflicting classes.

Final verification: **2,425** normal tests, **8** actual MCP host tests and **22**
release-checker unit tests passed. Both Gradle suites have zero failures, errors
or skips. Plugin build, configuration and structure checks passed; binary
verification against **IU-262.10968.63** is compatible, with 5 deprecated and 6
experimental API usages. The full build/verification run took 17m 46s.
Source/test/artifact receipts match the final inputs and ZIP. See the
[evidence summary](wicket-oat-factory-evidence.json) for hashes and counts.

The development ZIP retains 0.1.0 metadata. It was not installed into the running
IDE or published; graphical installation/release readiness was not reevaluated.

MCP decision: retain tools, request parameters and schema 1, adding optional
`sourceSetupExpanded`, `ownedMarkupPath` and `markupProviderNodeId` provenance
through shared analyzers and value snapshots. Validation/context reuse the same
findings and inferred-template confidence. Tool descriptions and client
documentation are updated.


## Fourth Wicket Oat navigation milestone: nested companions and contexts (development, 2026-11-12)

Implemented [named member companions and bounded navigation contexts](nested-navigation.md).
Canonical member source names pair with binary `$` resource basenames, including
static, nonstatic and deep members. Java caret selection, HTML reverse lookup
and MCP target selection preserve the named component's independent scope.
Ambiguous, local/anonymous or exhausted identities do not select a companion.

Native polyvariant references reuse independently analyzed caller/inheritor
snapshots. Distinct caller source origins in a shared nested Panel and inherited
Fragment get multiple targets; IDs known only through a caller can navigate in
both directions. These facts do not extend local inspections, completion, quick
fixes or paired rename. Selected variants retain their exact own contexts.

Both read-only Wicket Oat corpora verify `ProductsPage.ProductFields` pairing and
selection. Its six field bindings retain unresolved constructor/parent findings
at the existing BaseOatField metadata/lazy-input boundary; Products remains an
unproved discovery candidate. The core `OatSplitLayout.html` resource exposes
Inbox as one caller scope with three matching bindings, including native reverse
navigation from `second` to its captured Fragment origin. Inbox retains 26
confirmed and 5 inferred bindings. All 36 page sources still pair with markup.
A separate Wicket 11 WicketTester oracle verifies static/deep binary resources.

Final verification: **2,447** normal tests, **9** actual MCP host tests and **22**
release-checker unit tests passed. Both Gradle suites have zero failures, errors
or skips. Plugin build, configuration and structure checks passed; binary
verification against **IU-262.10968.63** reports:
`Compatible. 5 usages of deprecated API. 6 usages of experimental API`.
The complete normal build/verification run took 18m 25s. Source/test/artifact
receipts match the final inputs and ZIP. See the
[evidence summary](wicket-oat-navigation-evidence.json) for hashes and counts.

The development ZIP retains 0.1.0 metadata. It was not installed into the running
IDE or published; graphical installation/release readiness was not reevaluated.

MCP decision: extend the existing structure tool with optional
`includeNavigationContexts=false`. Default requests retain the same response;
no new tool or schema version is added. The optional projection reuses shared
graphs, exports and component-context selection, with explicit discovery status,
source dependencies and unproved candidate diagnostics. Tests cover source-set,
module and open-project isolation; unsaved freshness; read-only behavior;
cancellation; discovery and response bounds; default compatibility and actual
host schema/routing. Tool descriptions and client documentation are updated.


## Fifth Wicket Oat milestone: owned form fields (development, 2026-11-12)

Implemented [owned form-field summaries](owned-form-fields.md): empty metadata
keys are tokens, private lazy caches preserve fresh input identity, and private
input constructor references can forward through audited constructor chains.
Source initializer, superclass, callback and data/model effects remain bounded.
Parent mutation, borrowed inputs, unsafe source effects and exhausted budgets
withhold promotion. No Oat-specific class rules are introduced.

Both Wicket 10.11.0 and 11.0.0 read-only corpus runs now confirm **all six
ProductFields bindings**: name, category, price, stock, description and suppliers.
Native references navigate both ways; existing MCP structure, validation,
component context and optional navigation projections reuse the same facts.
All 36 page resources still pair; previous targets retain their counts, including
Inbox's 26 confirmed and 5 inferred bindings. ProductsPage itself remains an
unproved candidate with no matched graph bindings.

Internal inputs, conversion, validation and runtime lifecycle remain opaque with
explicit component-scope diagnostics. The independent Wicket 11 WicketTester
fixture verifies one lazy input identity and constructor-factory input ownership,
without turning runtime children into static graph claims. General CRUD/editor,
wizard/tree lifecycle and the rename origin-use audit remain follow-up work.

The focused run passes **140 tests** with zero failures/errors/skips. Coverage
includes input ownership, source effects and bounds, module isolation, unsaved
freshness, cancellation, native/MCP agreement and read-only behavior. Existing
shared request tests retain project isolation and response-limit coverage.

MCP decision: extend existing facts through shared analyzers and value snapshots.
No tool, parameter, response field or schema changes are required. Tool
descriptions and client documentation explain the unexpanded-input boundary.

Final verification: **2,469** normal tests, **9** actual MCP host tests and **22**
release-checker unit tests passed, with zero failures/errors/skips. Plugin build,
configuration and structure checks passed; binary verification against
**IU-262.10968.63** reports: `Compatible. 5 usages of deprecated API. 6 usages of experimental API`.
The complete normal build/verification run took 17m 47s. Source/test/artifact
receipts match the final inputs and ZIP. See the
[evidence summary](wicket-oat-form-evidence.json) for hashes and counts.

The development ZIP retains 0.1.0 metadata. It was not installed into the running
IDE or published; graphical installation/release readiness was not reevaluated.


## Sixth Wicket Oat milestone: ID origin-use audit (development, 2026-11-12)

Implemented [bounded ID origin-use auditing](id-origin-audit.md) for source-layout
origins and independently selected named members. Identity-only local literals
can gain native paired rename after all existing editor gates hold. The audit
follows explicit local aliases and exact source parameters to a single reviewed
framework ID slot. Data/model/resource-key/computation uses, stored/shared
origins, templates and unsupported flows remain guarded. Source dependencies and
exhausted traversal diagnostics join the existing value snapshots.

Shared Fragment children and projected owned-markup children retain rename
protection. A separate unshared Fragment attachment can rename without changing
its definition or other instances. Isolated named members can edit their own
binary-name companion; external source uses or reference-search exhaustion
withhold that edit. Top-level behavior retains its existing enumeration guards.
Native preview, atomic pre-write checks and undo remain in use. Tests verify
read-only targets, helper edits, new variants/inheritors, sibling conflicts,
source bounds, cancellation and module isolation.

The **190-test focused run** passed with zero failures/errors/skips. ProductFields
retains all six confirmed native/MCP bindings, but its IDs also reach
`BaseOatField` resource keys and fallback labels. Those origins stay protected;
property/localization-aware and multi-context refactoring remain separate work.
The application input manifest still matches all 318 original Java/HTML sources.

MCP decision: reuse the existing optional `idRenameSafe` eligibility flag,
diagnostics and shared snapshots. A true value can remain omitted from schema-1
JSON and is not an editor-write capability. Validation bindings/confidence stay
unchanged. No new tool, request parameter or response field is introduced;
MCP remains read-only. Descriptions and client documentation are updated.

Final verification: **2,497** normal tests, **9** actual MCP host tests and **22**
release-checker unit tests passed with zero failures/errors/skips. Both Wicket
10.11.0 and 11.0.0 corpora preserve all prior target counts, including Inbox's
26 confirmed and 5 inferred bindings and all 36 conventional page resources.
Plugin build, configuration and structure checks passed; binary verification
against **IU-262.10968.63** reports: `Compatible. 5 usages of deprecated API. 6 usages of experimental API`.
The full normal build/verification run took 18m 41s. Source/test/artifact
receipts match the final inputs and ZIP. See the
[evidence summary](wicket-oat-origin-evidence.json) for hashes and counts.

The development ZIP retains 0.1.0 metadata. It was not installed into the running
IDE or published; graphical installation/release readiness was not reevaluated.


## Seventh Wicket Oat milestone: constructor-reference editor contexts (development, 2026-11-12)

Implemented [source-declared replacement-site contexts](replacement-editors.md).
A bounded source factory registration, constructor ID proof, direct consumer
parameter flow and owned constructor ancestry connect `ProductFields::new` to
**products:editor:dialog:form:content**. Existing markup extraction, independent
editor analysis and matching supply the projected binding facts. No Oat class
allowlist is introduced.

Both Wicket 10.11.0 and 11.0.0 corpus runs expose **11 MATCHED / INFERRED event
bindings**, including the six fields, with explicit EVENT_TEMPLATE provenance and
unknown runtime presence. ProductFields' independent six bindings remain
confirmed. ProductsPage's initial report stays partial and unchanged; no event
is executed and no save/delete, model, validation or subsequent tree is inferred.
All prior corpus target counts and all 36 conventional resources are preserved.

Native Go to Declaration on the constructor reference's `new` token opens the
OatDialog content slot; reverse navigation uses one bounded indexed hop through
the configurable source host. The class qualifier keeps normal Java navigation.
Event graphs supply navigation only; existing inspections, completion, quick
fixes, paired rename and initial sidebar analysis keep their local snapshots.

The focused run passes **219 tests**. The independent Wicket 11 WicketTester
oracle observes the initial placeholder, two successive edits with different
models, replacement of the previous panel and removal on close. Tests cover
incorrect IDs, reconfiguration, foreign receivers, multiple consumers/parents,
mutations, markup uncertainty, dispatch, cancellation, bounds, native agreement,
unsaved freshness, read-only behavior, module isolation and response limits.
All 318 original application Java/HTML input hashes remain unchanged.

MCP decision: extend the existing optional structure context projection with
`relationship: EVENT_TEMPLATE`, optional `lifecycle` provenance, inline event
parent/ancestor/child selections and optional `lifecycleDiagnostics`. No new
tool, request parameter or schema version is needed. Default primary analysis,
validation and component-context selection retain their initial-phase meaning.
All tools remain read-only. Descriptions and client documentation are updated.

Final verification: **2,526** normal tests, **10** actual MCP host tests and **22**
release-checker unit tests passed with zero failures/errors/skips. Plugin build,
configuration and structure checks passed; binary verification against
**IU-262.10968.63** reports: `Compatible. 5 usages of deprecated API. 6 usages of experimental API`.
The full normal build/verification run took 18m 31s. Source/test/artifact
receipts match the final inputs and ZIP. See the
[evidence summary](wicket-oat-editor-evidence.json) for hashes and counts.

The development ZIP retains 0.1.0 metadata. It was not installed into the running
IDE or published; graphical installation/release readiness was not reevaluated.

## Eighth Wicket Oat milestone: registered wizard selections (development, 2026-11-12)

Implemented [source-declared step-selection contexts](wizard-selections.md).
OrderWizardPage has three separate contexts at **wizard:form:body**: Customer
(**6 inferred bindings**), Items (**5**) and Review (**4**), including placement.
Each retains its fragment definition and zero-based source registration index.
The private current index, validation outcomes, child internals/configuration and
other runtime effects remain unknown. Initial analysis is unchanged.

Native navigation connects registered factory lambda arrows to the shared core
body slot and back to all discovered factories. Direct field IDs retain their
fragment-definition locations. Selection graphs grant no editing authority.
The shared source analyzer follows bounded append registration, record/SAM
selection, literal constructor ancestry and replacement flow, then reuses Fragment
projection and the existing matcher. No Oat-specific class-name allowlist is used.

MCP decision: extend existing optional `includeNavigationContexts` structure
output with **SELECTION_TEMPLATE**, lifecycle kind **SOURCE_DECLARED_STEP_SELECTION**
and additive `selection` source metadata. Parent/ancestor/child facts reuse the
same component-context projections as native navigation. Default structure,
validation and ordinary component-context selection retain the initial graph.
No new tool, parameter, schema version or writable operation is introduced.

The read-only application corpus covers Wicket 10.11.0 and 11.0.0 and preserves
all previous target counts and 36 page companions. An independent Wicket 11
WicketTester checks initialization, failed validation, successful next, revisiting
and finish outcomes; those fixture outcomes are not runtime analysis guarantees.
Tests also cover registration order, unknown/conditional registrations, dispatch,
resource uncertainty, escapes/reassignment, competing parents, cancellation,
context/response bounds, module isolation, unsaved freshness, read-only behavior,
unchanged default responses and actual bundled MCP-host transport.

Final verification: **2,553** normal tests, **11** actual MCP host tests and **22**
release-checker unit tests passed with zero failures/errors/skips. Plugin build,
configuration and structure checks passed; binary verification against
**IU-262.10968.63** reports: `Compatible. 5 usages of deprecated API. 6 usages of experimental API`.
The full normal build/verification run took 19m 6s. Source/test/artifact
receipts match the final inputs and ZIP. See the
[evidence summary](wicket-oat-wizard-evidence.json) for hashes and counts.

The development ZIP retains 0.1.0 metadata. It was not installed into the running
IDE or published; graphical installation/release readiness was not reevaluated.

## Ninth Wicket Oat milestone: tree node/content templates (development, 2026-11-12)

Implemented [node-relative tree templates](tree-populations.md) for AccountsPage's
folders and accounts hosts. Each has two explicit callback-nullness preconditions,
with **eight inferred bindings per context** (four contexts, 32 bindings across
uses). Link/plain parent alternatives remain separate; the icon factory retains
its possible constructor types. Runtime node IDs, intervening page ancestry,
provider data, recursive depth/count, expansion and selection remain unknown.
Initial reports remain unchanged.

The source contract follows reviewed NestedTree/TableTree node and Node content/
junction factories. It reuses constructor identity, source constants, conventional
source/dependency markup extraction, graph matching, immutable reports, bounded
indexed discovery and version snapshots. Native ID/factory navigation, including
reverse dependency-slot targets, consumes those shared facts without adding editing
authority. Framework source contracts were checked against Wicket 10.11.0/11.0.0.

MCP decision: extend existing optional `includeNavigationContexts` output with
**TREE_TEMPLATE**, additive `treeTemplate` provenance and explicit
**NODE_RELATIVE** scope. Paths inside a logical node cannot be joined to the
hostComponentPath to invent a runtime path. Source nullness conditions are template
preconditions; optional `possibleComponentTypes` preserves factory alternatives.
Default structure, validation and ordinary component-context selection retain the
initial graph. No new tool, parameter, schema version or writable operation.

The read-only Oat corpus checks both versions, all four contexts and their
inferred bindings, native navigation and bounded MCP serialization while retaining
previous milestone results and 36 conventional page companions. An independent
Wicket 11 WicketTester verifies nested/table population, junction/content setup,
label/icon alternatives and expansion/collapse without assuming generated IDs.
Tests cover factory identity/escapes, conditional parents, initialization/mutation
guards, module isolation, unsaved source/resource freshness, cancellation,
context/node/response bounds, default-output compatibility, read-only behavior and
actual bundled MCP-host HTTP transport.

The heavy multi-module and actual-host fixtures initialize IntelliJ's application-
lived default-project annotation service before listener tracking. Leak assertions
remain enabled; the setup prevents lazy service initialization from being mistaken
for a listener retained by an individual fixture project.

Forwarding checks retain parameter provenance, including regressions where literal
node/content/junction IDs equal the analyzer's internal comparison markers. Value
equality alone does not establish forwarding of a supplied factory ID.

Final verification: **2,582** normal tests, **12** actual MCP host tests and **22**
release-checker unit tests passed with zero failures/errors/skips. Plugin build,
configuration and structure checks passed; binary verification against
**IU-262.10968.63** reports: `Compatible. 5 usages of deprecated API. 6 usages of experimental API`.
The full normal build/verification run took 19m 54s. Source/test/artifact
receipts match the final inputs and ZIP. See the
[evidence summary](wicket-oat-tree-evidence.json) for hashes and counts.

The development ZIP retains 0.1.0 metadata. It was not installed into the running
IDE or published; graphical installation/release readiness was not reevaluated.

## Tenth Wicket Oat milestone: lazy composite form internals (development, 2026-11-12)

Implemented [source-declared composite input structure](form-inputs.md).
OatDateRangeField has **six inferred wrapper-relative bindings**: container, label,
feedback, input, input:from and input:to. Transparent lookup preserves short paths;
private final input fields created through helpers navigate to their own binary-name
companion. Factory method names and the wrapper input slot navigate both ways.
Initial reports and existing confirmed wrapper attachments stay unchanged.

The existing owned-form effect proof and optional contexts share cache recognition.
Bounded constructor/factory declarations are reused by tree and form support.
The form context composes existing transparent/owned markup and graph matching;
it does not relax model-write, callback or escape checks in initial effect analysis.
Source conversion/required/validator hooks are recorded with no execution or known
result. Runtime presence, required state, model values, conversion/validation,
behavior effects and later tree changes remain unknown. Template editing is guarded.

MCP decision: extend optional `includeNavigationContexts` structure output with
**FORM_TEMPLATE** and additive `formTemplate` provenance, scoped **WRAPPER_RELATIVE**.
Bindings include existing parent/ancestor/child projections. Source hook locations
have `execution: not_evaluated`; no input value or validation outcome is supplied.
Default structure, ID-binding validation and ordinary component-context selection
retain the initial graph. No new tool, request parameter, schema version or writable
operation is introduced. Client descriptions/docs are updated.

The read-only Oat corpus checks all six bindings, transparent/private input paths,
source/factory navigation, shared MCP output and initial-report preservation with
Wicket 10.11.0 and 11.0.0. Previous layout, Inbox, ProductFields, editor, wizard and
tree results and 36 page companions remain covered. An independent Wicket 11
WicketTester verifies one lazy input identity, child structure, required failure,
valid conversion/model update and validator failure preserving the prior model.
Tests cover caches, ID provenance, override/attachment/helper ownership, escapes,
structural mutations, explicit null initialization, deferred initialization calls,
module isolation, source/resource freshness, cancellation and context/node/response
bounds, plus actual bundled MCP-host HTTP transport. Framework source contracts
and version-specific hashes are included in the evidence summary.

Final verification: **2,614** normal tests, **13** actual MCP host tests and **22**
release-checker unit tests passed with zero failures/errors/skips. Plugin build,
configuration and structure checks passed; binary verification against
**IU-262.10968.63** reports: `Compatible. 5 usages of deprecated API. 6 usages of experimental API`.
The full normal build/verification run took 20m 27s. Source/test/artifact
receipts match the final inputs and ZIP. See the
[evidence summary](wicket-oat-input-evidence.json) for hashes and counts.

The development ZIP retains 0.1.0 metadata. It was not installed into the running
IDE or published; graphical installation/release readiness was not reevaluated.


## Eleventh Wicket Oat milestone: registered custom composite inputs (development, 2026-11-12)

Implemented [registered custom inputs](registered-form-inputs.md) over the shared
lazy form declaration/cache/identity/markup machinery. InvoicePage's PhoneInput
registration supplies six wrapper-relative MATCHED/INFERRED bindings, including
input:code and input:number. Selected constructor forwarding and private final
factory storage are recorded; runtime presence, page embedding, model values and
conversion/validation outcomes remain unknown. Initial analysis and stricter owned
form effects retain their previous meaning.

MCP decision: extend existing optional FORM_TEMPLATE output with additive
formTemplate.registration provenance. No new tool, request parameter or schema
version. Native ID/new-token/reverse-slot navigation and MCP reuse the same value
graphs and freshness dependencies. All tools remain read-only. New regressions
exercise forwarding/ownership/escape rejection, separate registrations, source
freshness, module isolation, cancellation and response/discovery bounds. The
independent WicketTester oracle checks cached input identity, rendered children,
conversion/model update and ID mismatch rejection. The actual bundled MCP host
checks optional output and unchanged default analysis.

Final verification: **2,653** normal tests, **14** actual MCP host tests and **22**
release-checker unit tests passed with zero failures/errors/skips. Both read-only
Oat corpus runs (Wicket 10.11.0 and 11.0.0) passed, pairing all 36 page resources
and preserving all 318 application input hashes. Prior milestone binding counts
remain unchanged. Plugin build, configuration and structure checks passed; binary
verification against **IU-262.10968.63** reports: `Compatible. 5 usages of deprecated API. 6 usages of experimental API`.
The full normal build/verification run took 21m 25s. Source/test/artifact
receipts match the final inputs and ZIP. See the
[evidence summary](wicket-oat-registered-input-evidence.json) for counts and hashes.

The development ZIP retains 0.1.0 metadata. It was not installed into the live IDE
or published; graphical installation/release readiness was not reevaluated.


## Twelfth Wicket Oat milestone: direct base-layout shell (development, 2026-11-12)

Implemented [abstract base-layout reconciliation](base-layouts.md) for the directly
reported OatAppLayout case. The primary report now has eight MATCHED/INFERRED shell
bindings and nine unresolved sidebar-internal entries. All three constructor
entries agree on the attachment/type/scope graph under declared implementations.
The open child slot is an explicit local boundary. The text header and match rows
show the template assumptions; runtime constructor selection, subclass overrides,
extension contents and model values remain unknown.

Constructor entries reuse audited layout setup, source identity, field/helper
handling, deferred effect checks, origin auditing and shared budgets. Transparent
head planning and ordinary inheritance composition are reused. Different/unsafe
entries, opaque input callbacks and malformed slots remain conservative. Both
html allocations navigate to their common markup and back; rename stays protected.
Ordinary concrete-page analysis retains its existing behavior.

MCP decision: extend the primary structure/validation/component-context facts with
additive baseLayoutTemplate constructor/extension provenance. Native and MCP use
one PageReport and freshness snapshot; there is no new tool, request parameter or
schema version. Tests cover native report/navigation, all three MCP views,
constructor disagreement, source freshness, module isolation, cancellation,
discovery/response bounds and read-only behavior. The runtime oracle renders the
same shell through each constructor route after concrete extension markup fills
the slot, and distinguishes hidden tree components from rendered output.

Final verification: **2,681** normal tests, **15** actual MCP host tests and **22**
release-checker unit tests passed with zero failures/errors/skips. Both read-only
Oat corpus runs (Wicket 10.11.0 and 11.0.0) passed. Direct OatAppLayout reports
contain eight inferred shell bindings and nine unresolved sidebar entries, with
three constructor entries and one open slot. All 36 page resources remain paired,
all 318 application input hashes are unchanged, and prior milestone facts remain
unchanged. Plugin build, configuration and structure checks passed; binary
verification against **IU-262.10968.63** reports: `Compatible. 5 usages of deprecated API. 6 usages of experimental API`.
The full normal build/verification run took 22m 17s. Source/test/artifact
receipts match the final inputs and ZIP. See the
[evidence summary](wicket-oat-base-layout-evidence.json) for counts and hashes.

The development ZIP retains 0.1.0 metadata. It was not installed into the live IDE
or published; graphical installation/release readiness was not reevaluated.


## Thirteenth Wicket Oat milestone: conditional list populations (development, 2026-11-12)

Implemented [source ListView population templates](list-populations.md). The
OatAppLayout sidebar has separate leaf/group contexts with four/seven inferred
bindings, shared link/label/badge helpers and nested groupItems. All nine sidebar
markup slots have conditional counterparts. Primary shell matches stay separate;
these slots are UNRESOLVED_BRANCH. Text reports append item contexts with explicit
row preconditions, rather than combining incompatible component types or inventing
runtime row IDs. Concrete source factory overrides use the selected source class.

The existing audited layout pass also recognizes ordinary source ListView
attachments without requiring decoration. New source rules reuse shared constructor
identity/declarations, constants, graph matching, source snapshots and native
references. Item graphs retain their findings and necessary ancestors without
copying unrelated page bindings. Empty hidden placeholder bodies are projected per
source branch; hidden rows retain their declared children. Runtime model/predicate
state, row counts/IDs, authorization, visibility and callbacks remain unknown.

MCP decision: extend existing primary structure/validation/component-context facts
with populationTemplates and additive populationTemplate provenance. Optional
navigation discovery exposes the same graphs as REPEATER_TEMPLATE. There is no new
tool, parameter or schema version. Tests cover source ownership/ID forwarding,
unsupported branches/escapes/recursion, concrete dispatch, normal repeaters,
source freshness, module isolation, cancellation, indexed discovery exhaustion,
response/discovery bounds and protected rename. An independent WicketTester oracle
renders simultaneous leaf/group rows, nested content and empty placeholders. All
three views are covered through the actual bundled MCP host.

Final verification: **2,717** normal tests, **16** actual MCP host tests and **22**
release-checker unit tests passed with zero failures/errors/skips. Both read-only
Oat corpus runs (Wicket 10.11.0 and 11.0.0) passed. The direct layout report retains
eight inferred shell matches and has nine conditional sidebar counterparts, with
separate four/seven-binding item contexts. Shared helpers navigate to outer and
nested markup. All 36 page resources remain paired; all 318 application input
hashes are unchanged. Prior ordinary matches and other milestone templates remain
unchanged; affected parent uncertainty becomes explicit branch uncertainty.
Plugin build, configuration and structure checks passed; binary verification
against **IU-262.10968.63** reports: `Compatible. 5 usages of deprecated API. 6 usages of experimental API`.
The full normal build/verification run took 21m 33s. Source/test/artifact
receipts match the final inputs and ZIP. See the
[evidence summary](wicket-oat-population-evidence.json) for counts and hashes.

The development ZIP retains 0.1.0 metadata. It was not installed into the live IDE
or published; graphical installation/release readiness was not reevaluated.


## Fourteenth Oat milestone: immediate table content factories (2026-11-13)

The source toolbar setter invokes a unary fragment factory and replaces its own
slot. The implementation reuses the bounded replacement session, constructor ID
tracer/constants, conventional source markup, fragment composition and matcher.
The selected source constructor must declare a replacement placeholder; the pure
identity guard must accept a fresh correctly forwarded-ID result.
The separate DataTablePage source template declares dataTable, toolbar,
searchForm and search with inferred bindings. The primary hierarchy is unchanged.
Native factory arrows, fragment IDs and nested form/field IDs navigate through the
same value graph. Configuration, deferred Ajax hooks, factory execution, model
values, visibility and later state remain explicitly unknown or not evaluated.

MCP decision: extend the existing page-structure tool's optional navigation
projection with FACTORY_TEMPLATE / SOURCE_DECLARED_CONTENT_FACTORY /
REGISTRATION_TEMPLATE and additive source registration/configuration/callback
provenance. No tool, parameter or schema version change. Default structure,
validation and component-context views and existing lifecycle contracts remain
unchanged. Shared source freshness, isolation, cancellation, response/discovery
bounds and read-only behavior apply. The source template supplies no paired-rename
authority. See [the contract](content-factories.md).

Final verification: **2,776** normal tests, **17** actual MCP host tests and **22**
release-checker unit tests passed with zero failures/errors/skips. Both read-only
Oat corpus runs (Wicket 10.11.0 and 11.0.0) passed. DataTablePage's toolbar has four
inferred bindings, including searchForm/search, and two recorded Ajax hooks.
All 36 page resources remain paired; all 318 application input hashes are
unchanged. Earlier ordinary matches and all earlier milestone template summaries
remain unchanged, including OatAppLayout's 8 shell and 4/7 item bindings.
Plugin build, configuration and structure checks passed; binary verification
against **IU-262.10968.63** reports: `Compatible. 5 usages of deprecated API. 6 usages of experimental API`.
The full normal build/verification run took 21m 43s. Source/test/artifact
receipts match the final inputs and ZIP. See the
[evidence summary](wicket-oat-content-factory-evidence.json) for counts and hashes.

The development ZIP retains 0.1.0 metadata. It was not installed into the live IDE
or published; graphical installation/release readiness was not reevaluated.


## Fifteenth Oat milestone: lazy table toolbar content (2026-11-13)

The DataTablePage empty-state factory now has a separate toolbar-relative source
snapshot with four inferred bindings: cell, content, content:title and
content:message. It checks the owned DataTable and null-guarded toolbar cache,
framework bottom-toolbar attachment, private final cell, forwarded factory ID and
selected content constructor chain. The shared constructor ID/declaration,
resource/inheritance and matching analyzers supply the bindings. The existing
immediate-factory identity guard proof is reused without broadening its contract.

Native factory arrows and cell/label references share the graph with optional MCP
TABLE_TOOLBAR_TEMPLATE contexts. Wicket-generated toolbar IDs, full runtime page
paths, row count, visibility, colspan, models, actual instances and later state
remain unknown. Configuration, bound model expressions and lifecycle locations
are provenance; application factories and callbacks are not evaluated.

MCP decision: extend only the existing page-structure tool's optional navigation
projection with toolbarTemplate source provenance and TOOLBAR_RELATIVE bindings.
No new tool, parameter or schema version. Defaults, primary selectors and earlier
lifecycle contracts remain unchanged. Read-only, isolation, unsaved-source
freshness (including inspected but withheld registrations), cancellation and
response/discovery bounds are covered. New templates grant no paired-rename
authority. An independent WicketTester fixture observes immediate installation,
ID allocation in the presence of another toolbar, cache reuse, replacement and
model/row-dependent visibility. See [the contract](table-toolbars.md).

Final verification: **2,814** normal tests, **18** actual MCP host tests and **22**
release-checker unit tests passed with zero failures/errors/skips. Both read-only
Oat corpus runs (Wicket 10.11.0 and 11.0.0) passed. The empty-state toolbar has four
inferred toolbar-relative bindings and two recorded lifecycle/model declarations.
All 36 ordinary page summaries and earlier milestone template summaries match
the previous milestone exactly. All 36 page resources remain paired, and all 318
application input hashes are unchanged. Plugin build, configuration and structure
checks passed; binary verification against **IU-262.10968.63** reports:
`Compatible. 5 usages of deprecated API. 6 usages of experimental API`. The full normal build/verification run took 22m 7s.
Source/test/artifact receipts match the final inputs and ZIP. See the
[evidence summary](wicket-oat-toolbar-evidence.json) for counts and hashes.

The development ZIP retains 0.1.0 metadata. It was not installed into the live IDE
or published; graphical installation/release readiness was not reevaluated.

## Sixteenth Oat milestone: registered list callbacks (2026-11-13)

AccordionPage's source static item/data factory is now a reviewed source protocol.
The analyzer checks overload forwarding, exact callback registration and bridge,
constructor ID forwarding and behavior-only decoration of the unchanged default
ListView item. It reuses the source-layout effect audit and the existing bounded
population interpreter, matcher, value snapshots and native navigation graphs.
Unsupported factories and captured component mutations keep their uncertainty.
See [the contract](registered-list-callbacks.md).

AccordionPage has 11 primary matches and three population contexts: the unchanged
sidebar leaf/group contexts and the new two-binding inferred title/content item
template. The primary 11 UNRESOLVED_BRANCH slots retain their separate conditional
item contexts. Row counts/IDs, models, visibility and behavior effects stay unknown;
callbacks are not executed. Other ordinary page summaries and earlier milestone
template summaries retain their existing behavior.

MCP decision: extend the existing structure, validation and component-context
population facts with optional registeredListPopulation and
populationTemplate.registration source provenance. Optional navigation discovery
projects the exact same graph as REPEATER_TEMPLATE. No tool, request parameter or
schema version is added. Client docs and tool descriptions describe the additive
facts and limits. Tests cover source protocol rejection, native references and
protected rename, source/module isolation, freshness, cancellation, response and
discovery bounds, read-only views and the actual MCP host. An independent Wicket
runtime fixture verifies unchanged item/index/model identity, population during
rendering, behavior decoration and a later empty model.

Framework source review also covers Behavior.isTemporary during attachment;
source overrides with synchronous effects withhold the callback proof, and
unsaved edits to that hook invalidate existing snapshots.

Final verification: **2,848** normal tests, **19** actual MCP host tests and **22**
release-checker unit tests passed with zero failures/errors/skips. Both read-only
Oat corpus runs (Wicket 10.11.0 and 11.0.0) passed. AccordionPage now has 11 primary
matches and a separate two-binding inferred item template; all 35 other ordinary
page summaries and earlier milestone template summaries match the prior build.
All 36 page resources remain paired and all 318 application input hashes remain
unchanged. Plugin build/configuration/structure and binary compatibility checks
passed against **IU-262.10968.63**: `Compatible. 5 usages of deprecated API. 6 usages of experimental API`.
The full normal build/verification run took 22m 26s. Source/test/artifact
receipts match the final inputs and ZIP. See the
[evidence summary](wicket-oat-registered-list-evidence.json) for counts and hashes.

The ZIP retains 0.1.0 metadata. No live IDE installation or publication was
performed, and graphical release readiness was not reevaluated.

## Seventeenth Oat milestone: initial dialog/drawer content (development, 2026-11-13)

Implemented [source-declared initial content setup](initial-dialog-content.md).
The existing strict analyzers remain the first choice. A bounded fallback follows
owned constructor declarations and a guarded private-final content parent, composes
initial Fragment instances and transparent owned markup, and leaves source-defined
Ajax hooks deferred. The entire fallback graph is explicitly INITIAL_DECLARATIONS:
constructor/configuration/model effects, validation, visibility and later
open/close/replacement state remain unknown. Every binding is inferred and grants
no editing authority. Direct eager hook calls, structural configuration, wrong IDs,
foreign ancestry, competing placements, source initialization/markup overrides and
budget exhaustion withhold the declaration graph.

DialogPage has 72 inferred primary matches and 13 branch findings. Four owned
hosts have three explicit initial content placements; the details drawer retains
its initial placeholder. editFields and filterFields supply their field bindings.
The sidebar and people-row populations remain separate templates. personDetails
has definition navigation and no invented initial instance. Shared source ID tracing
adds a bounded opt-in null/model conditional for non-ID delegation data; the ID
itself must still forward unchanged. The shared population interpreter recognizes behavior-only fluent add on direct
fresh allocations, and owned transparent markup preserves
ordinary-child precedence and parent fallback.

MCP decision: extend the existing structure, validation and component-context
value snapshots with optional initialContentSetup provenance. No tool, parameter
or schema version is added. Native navigation uses the same source graph and
fragment/owned-resource matching. Descriptions and client docs state the phase and
unknowns. Tests cover guarded protocols, negative source cases, eager hooks,
protected native references, module isolation, unsaved source/resource freshness,
cancellation, declaration/response bounds, read-only views and the actual MCP host.
An independent Wicket runtime fixture distinguishes initial placement, Ajax confirm
execution, later replacement/detachment and wrong-ID rejection.

Final verification: **2,879** normal tests, **20** actual MCP host tests and **22**
release-checker unit tests passed with zero failures/errors/skips. Both read-only
Oat corpus versions (Wicket 10.11.0 and 11.0.0) passed. DialogPage has 72 inferred
matches, four owned hosts, three initial placements and three item templates
covering 13 primary branch slots. Other 35 ordinary page summaries and all earlier
milestone template summaries match the previous build exactly. All 36 resources
pair and all 318 application input hashes remain unchanged. Plugin configuration,
structure and binary compatibility checks passed against IU-262.10968.63:
`Compatible. 5 usages of deprecated API. 6 usages of experimental API`. The full build/verification run took 23m 10s.
Current source/test/artifact receipts match the ZIP. See the
[evidence summary](wicket-oat-initial-content-evidence.json) for counts and hashes.
The ZIP retains 0.1.0 metadata; no live IDE installation or publication was performed.


## Wicket Oat development milestone 18: row-triggered drawer content

Implemented [separate source event-content templates](event-dialog-content.md).
DialogPage's details link supplies a personDetails Fragment and OatDescriptionList
at the guarded drawer slot. Five inferred bindings retain target ancestry;
`people:rows:details` identifies a separate symbolic-row trigger. The initial
72-match/13-branch report and its plain details placeholder remain unchanged.
Callbacks are not executed, and runtime rows, models, validation, visibility and
later open/close state remain unknown.

MCP decision: extend the existing optional structure navigation contexts with
EVENT_TEMPLATE / SOURCE_DECLARED_EVENT_CONTENT and directContent provenance. No
new tool, parameter or schema version. Native navigation and MCP reuse the shared
constructor identity, content declarations, fragment/provider matching and value
snapshots. Initial validation and selectors retain existing contracts. Acceptance
covers read-only native/MCP/actual-host behavior, module isolation, unsaved source
and markup freshness, cancellation, source/discovery/response bounds and an
independent row-click runtime fixture. Verification results follow below.

Final verification: **2,910** normal tests, **21** actual MCP host tests and **22**
release-checker unit tests passed with zero failures/errors/skips. Both read-only
Oat corpus versions (Wicket 10.11.0 and 11.0.0) passed. DialogPage has a separate
five-binding inferred event template; its initial 72-match/13-branch report is
unchanged. All 36 ordinary page summaries and earlier milestone template
summaries match the previous build exactly. All 318 application input hashes
remain unchanged. Configuration, structure and binary compatibility checks passed
against IU-262.10968.63: `Compatible. 5 usages of deprecated API. 6 usages of experimental API`. The full build/verification run
took 22m 35s. Current source/test/artifact receipts match the ZIP. See the
[evidence summary](wicket-oat-event-content-evidence.json) for counts and hashes.
The ZIP retains 0.1.0 metadata.


## Wicket Oat development milestone 19: context-aware ID completion

Implemented [context-aware Java/HTML ID completion](context-completion.md) using
the existing semantic graphs. Parent-scoped unused counterpart IDs show component
types, source context and inferred labels. Shared physical uses intersect
candidates. A completion-only scope retains one focused empty literal and allows
ID mismatch findings in event probes; all source ownership/effect proofs remain.
Default reports, inspections, fixes, paired rename and MCP keep their prior policy.

Native tests cover insertion/prefixes/escaping, initial fragments, event content,
factories, registered rows, shared helper/fragment uses, abstract base layouts,
module isolation, source freshness, uncommitted/synthetic sources, unsafe parents
and transfers, computed IDs, cancellation and projection bounds. Real Oat probes
edit and restore fixture copies only. Default page/template summaries and the
application input manifest are compared against milestone 18.

MCP decision: no new tool, parameter, schema or response fields. Existing structure
and optional contexts already expose IDs/types/parents/locations/confidence. This
extension projects those facts for a caret and adds native insertion/presentation.
The incomplete editing-site probe is not an observed binding and stays outside
normal agent snapshots. Direct capture and actual host calls verify identical MCP
analysis/navigation before and after a read-only completion probe. Verification
results follow below.

Final verification: **2,936** normal tests, **22** actual MCP host tests and **22**
release-checker unit tests passed with zero failures/errors/skips. Both read-only
Oat corpus versions (Wicket 10.11.0 and 11.0.0) passed all six completion probes.
All 36 ordinary page summaries, earlier template summaries and DialogPage's
initial/event text reports match milestone 18 exactly. All 318 application input
hashes remain unchanged. Configuration, structure and binary compatibility checks
passed against IU-262.10968.63: `Compatible. 5 usages of deprecated API. 6 usages of experimental API`. The full build/verification
run took 23m 30s. Current source/test/artifact receipts match the ZIP. See the
[evidence summary](wicket-oat-completion-evidence.json) for counts and hashes.
The ZIP retains 0.1.0 metadata.


## Milestone 20: source dialog submissions

The bounded analyzer follows private-final owned AjaxButton.onSubmit declarations
to selected dialog onConfirm hooks. It records feedback receivers, unchanged
constructor-local Ajax refresh targets and guarded fresh content placements in
separate inferred source graphs. Framework bridge and callback locations retain
provenance; validation, models, runtime dispatch, business outcomes and later state
remain unknown. OatDialog's post-hook close/error logic is not executed or used to
invent callback effects. DialogPage has three contexts with 4/5/4 bindings; its
initial 72-match/13-branch graph and earlier template/completion results remain
unchanged.

Native hook/trigger references and fresh content/child navigation share the MCP
value graphs. Paired rename and initial selectors keep their prior rules. The MCP
decision is to extend existing optional structure navigation contexts with
SUBMISSION_TEMPLATE and submissionTemplate provenance, without a new tool,
parameter or schema version. Tool descriptions and client docs describe unknown
validation/default-processing/outcomes and source attachment phases. See
[the contract](submission-templates.md).

Tests cover module isolation, unsaved callback/bridge/setter/markup freshness,
read-only direct and actual-host MCP output, source and response bounds,
cancellation, protected rename and unchanged initial views. An independent
WicketTester oracle checks required-field failure, successful submission/content
replacement, rendering without submission and disabled default form processing.

The panel-preview inspection fixture now declares the Wicket namespace in an HTML
wrapper. This avoids IntelliJ XML/RelaxNG namespace recursion during native
highlighting while retaining the existing preview exclusion assertions; no
inspection or production behavior was changed for that fixture correction.

Final verification: **2,971** normal tests, **23** actual MCP host tests and **22**
release-checker unit tests passed with zero failures/errors/skips. Both read-only
Oat corpora (Wicket 10.11.0 and 11.0.0) passed all three submission contexts.
All 36 ordinary page summaries, earlier template groups, six completion probes
and DialogPage's initial/event text reports match milestone 19. All 318
application input hashes remain unchanged. Configuration, structure and binary
compatibility checks passed against IU-262.10968.63: `Compatible. 5 usages of deprecated API. 6 usages of experimental API`.
The full build/verification run took 23m 37s. Current source/test/artifact
receipts match the ZIP. See the [evidence summary](wicket-oat-submission-evidence.json)
for counts, framework source contracts and hashes. The ZIP retains 0.1.0 metadata.


## Milestone 21: ordinary form submit/error declarations

A bounded analyzer recovers explicit source form/button additions and separate
AjaxButton/source-subclass onSubmit/onError overrides. InvoicePage supplies two
contexts with two inferred bindings each and feedback/form-refresh declarations.
The initial hierarchy remains unresolved. The shared effect reader, constructor
ID tracing, markup analyzer and matcher supply native navigation and MCP values.

MCP decision: extend existing optional SUBMISSION_TEMPLATE contexts with
SOURCE_DECLARED_FORM_CALLBACK / OWN_COMPANION_DECLARATIONS and formCallback
provenance. Source attachment/constructor/form-association facts help agents
inspect submit/error handlers. No new tool, parameter or schema version is needed.
Existing dialog metadata, default selectors, inspections, completion and editing
retain their prior policy. Validation, business outcomes, initialization and
runtime state remain unknown; no callback or model is executed. See
[the contract](form-submission-templates.md).

Final verification: **3,002** normal tests, **24** actual MCP host tests and **22**
release-checker unit tests passed with zero failures/errors/skips. Both read-only
Oat corpora (Wicket 10.11.0 and 11.0.0) passed InvoicePage's two submit/error contexts.
All 36 ordinary page summaries, earlier template groups, six completion probes
and initial/event/dialog-submission text reports match milestone 20. All 318
application input hashes remain unchanged. Configuration, structure and binary
compatibility checks passed against IU-262.10968.63: `Compatible. 5 usages of deprecated API. 6 usages of experimental API`.
The full build/verification run took 23m 57s. Current source/test/artifact
receipts match the ZIP. See the [evidence summary](wicket-oat-form-submission-evidence.json)
for counts, framework source contracts and hashes. The ZIP retains 0.1.0 metadata.

## Wicket ID completion: popup scoping and transparent lookup

The reported unrelated suggestions came from generic string/word contributors
running alongside Wicket ID completion. The native contributor now runs first
and stops generic fallback at recognized ID sites, while keeping ordinary display
and model-string completion. Native regression tests inspect the complete popup.

DialogPage's missing host candidates were reproduced on the real Oat corpus:
clearing a Java host ID left its HTML counterpart under the transparent OatHtml
parent, so the candidate projection looked in the wrong scope. The existing
transparent resolver now supplies bounded editor-only lookup hints for the
focused unmatched site. Ordinary-child precedence, nested fallback, ordinary
container boundaries, shadowing and ambiguity checks remain intact. No new
binding is asserted, and normal report caches stay separate.

MCP decision: no surface change. These lookup hypotheses and popup operations
remain native editor behavior over existing semantic facts. Direct and actual
host responses remain unchanged; no lookup hint is exported. Tests cover source
and module scoping, freshness, cancellation/probe restoration, protected editing,
normal text completion and initial/MCP snapshot preservation. See
[the completion contract](context-completion.md#popup-scoping-and-transparent-layout-correction).

Final correction verification: **3,024** normal tests,
**25** actual MCP host tests and **22** release-checker unit tests passed
with zero failures/errors/skips. The 186 focused tests passed. Both read-only Oat
corpora (Wicket 10.11.0 and 11.0.0) passed all eight complete-popup checks for
DialogPage: empty and partial host IDs, initial fragment fields and event content,
in Java and HTML. All 36 ordinary page summaries, prior template/completion
results and four saved text reports match milestone 21. All 318 application input
hashes remain unchanged. Configuration, structure and binary compatibility checks
passed against IU-262.10968.63: `Compatible. 5 usages of deprecated API. 6 usages of experimental API`. The full run took 23m 49s.
Current source/test/artifact receipts match the ZIP. See the
[correction evidence](wicket-oat-popup-scoping-evidence.json) for counts and hashes.
The ZIP retains 0.1.0 metadata.

## Project tree labels and report review

Java source class/file nodes now display orange Wicket Page/Panel/Component labels
through the native Project tree decorator. Roles follow resolved inheritance and
include custom bases and nested classes independently of report completeness.
Existing icons/text/comments/tooltips are preserved; there is no source mutation,
companion lookup, component analysis or retained PSI/role cache.

MCP decision: presentation-only, no change. The mobile/DataTable review adds
regression assertions for the existing abstract-hook/constructor-loop withholding
policy and separate source templates, without broadening semantic claims. See
[the label contract](project-tree-labels.md) and
[the report review](wicket-oat-report-review.md).

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

## Graphical Project tree markers

The text labels are replaced by a small orange dot on the existing class icon.
The same source class/file scope is retained, with Component inheritance covering
Wicket pages, panels and custom components. Native BadgeIcon/BadgeDotProvider
preserve the original glyph and icon dimensions; no text fragment or tooltip is
added. Indexing, cancellation, source freshness and read-only rules are unchanged.

MCP decision: presentation-only, no surface change. No semantic analyzer, report,
completion or editing policy changes. See [the graphical marker contract](project-tree-markers.md).

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

## Constructor data initialization loops

The bounded source-layout pass audits finite counted root-constructor loops over
reviewed scalar/canonical-record data and fresh ArrayList storage. No data values,
model contents, runtime rows or counts are evaluated or exported. Outer writes,
component/erased-alias escape, callbacks, unreviewed calls, counter overflow and
unsupported control flow withhold the expansion. Later failures retain the passed
loop note and identify the next component/helper; first-stop text diagnostics
precede cascading findings when no primary binding is matched.

MCP decision: improve the existing structure/validation/component-context behavior
through the same native graph, diagnostics and value snapshots. No tool, parameter,
selector or schema-field change. Scope, freshness, cancellation, bounds and
read-only acceptance cover direct calls, module isolation and the actual host. See
[the implementation contract](constructor-data-loops.md).

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

## Constructor-owned initial dialog content

The twenty-third milestone recognizes empty compiled Model delegation for the
declaration-only ID proof and a selected wrapper's private-final message placement
into an inherited guarded content slot. Virtual source dispatch, inferred
confidence, constructor placement provenance and deferred callbacks reuse existing
initialContentSetup metadata. Later setup failures retain a declaration note and
withhold the complete graph. Constructor/configuration/model effects, visibility
and later confirmation actions remain unknown. See
[the contract](constructor-owned-dialog-content.md). MCP decision: extend existing
structure/validation/component-context behavior through shared analyzers and
snapshots, with unchanged tools, parameters and schemas.

Final milestone verification: **3,117** normal tests,
**27** actual MCP host tests and **22** release-checker unit tests passed
with zero failures/errors/skips. The 249 focused regression tests passed.
Both read-only Oat corpora (Wicket 10.11.0 and 11.0.0) exercise the actual
OatConfirmDialog through a fixture-only probe: 24 inferred bindings, a constructor
message Label and one constructor placement, shared by native and all three MCP
views. DataTablePage and CustomerPage advance to the next explicit stops while
retaining their unresolved initial hierarchies. All 36 primary finding/binding
summaries, previous template/completion groups, six graphical Project tree markers,
four saved text reports and 318 application input hashes are preserved.
Configuration, structure and binary compatibility checks passed against
IU-262.10968.63: `Compatible. 7 usages of deprecated API. 6 usages of experimental API`. The full run took 25m 21s.
Current source/test/artifact receipts match the ZIP. See the
[milestone evidence](wicket-oat-confirmation-content-evidence.json) for counts and hashes.
The ZIP retains 0.1.0 metadata; no installation or publication was performed.

## Twenty-fourth milestone: source column constructor storage

See [the contract](column-construction.md) for the two reviewed compiled
constructors, deferred callbacks, rejection cases and MCP decision.

Final milestone verification: **3,151** normal tests,
**28** actual MCP host tests and **22** release-checker unit tests passed
with zero failures/errors/skips. The 269 focused regression tests passed.
Both read-only Oat corpora (Wicket 10.11.0 and 11.0.0) exercise the actual
OatSelectionColumn through a fixture-only probe: 11 page bindings, no column-generated
children, and the same constructor diagnostic in native/MCP views. DataTablePage
advances to OatBadgeColumn's LambdaColumn delegation while retaining its unresolved
initial hierarchy. All 36 primary finding/binding summaries, previous template and
completion groups, confirmation probe, six graphical Project tree markers, four
saved text reports and 318 application input hashes are preserved.
Configuration, structure and binary compatibility checks passed against
IU-262.10968.63: `Compatible. 7 usages of deprecated API. 6 usages of experimental API`. The full run took 25m 17s.
Current source/test/artifact receipts match the ZIP. See the
[milestone evidence](wicket-oat-column-construction-evidence.json) for counts and hashes.
The ZIP retains 0.1.0 metadata; no installation or publication was performed.

## Twenty-fifth milestone: LambdaColumn constructor storage

See [the contract](lambda-columns.md) for storage, callback-interface and receiver
audits, the new constructor diagnostic and the MCP decision.

Final milestone verification: **3,177** normal tests,
**29** actual MCP host tests and **22** release-checker unit tests passed
with zero failures/errors/skips. The 304 focused regression tests passed.
Both read-only Oat corpora (Wicket 10.11.0 and 11.0.0) construct the actual badge
and number columns in a fixture-only probe: 11 page bindings, two constructor
storage notes, no column-generated children and shared native/MCP diagnostics.
DataTablePage advances to the date column's FormatStyle enum default while its
initial hierarchy remains unresolved. All 36 primary finding/binding summaries,
previous template/completion groups, earlier constructor probes, six graphical
Project tree markers, four saved text reports and 318 application input hashes
are preserved. Configuration, structure and binary compatibility checks passed
against IU-262.10968.63: `Compatible. 7 usages of deprecated API. 6 usages of experimental API`. The full run took 24m 41s.
Current source/test/artifact receipts match the ZIP. See the
[milestone evidence](wicket-oat-lambda-column-evidence.json) for counts and hashes.
The ZIP retains 0.1.0 metadata; no installation or publication was performed.

## Twenty-sixth milestone: reviewed date-column enum defaults

See [the contract](date-column-enums.md) for exact JDK identity, storage-only
semantics, rejection cases and the MCP decision.

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

## Twenty-seventh milestone: construction-only source providers

See [the contract](provider-construction.md) for zero-state allocation, deferred
callbacks, independent consumer checks and the MCP decision.

Final milestone verification: **3,232** normal tests,
**31** actual MCP host tests and **22** release-checker unit tests passed
with zero failures/errors/skips. The 359 focused regression tests passed.
Both read-only Oat corpora (Wicket 10.11.0 and 11.0.0) exercise an isolated copy
of the actual InvoiceProvider and filtered() source, retaining the original data
seed and confirmation setup. Its 25 inferred bindings and provider-construction
note are shared by native/MCP views; no row/query/model values are evaluated.
DataTablePage advances to the compiled DataTable constructor in RowsTable.
All 36 primary finding/binding/source-dependency-count summaries, previous groups,
earlier probes, six Project tree markers, four saved reports and 318 application
input hashes are preserved. Configuration, structure and binary compatibility
checks passed against IU-262.10968.63: `Compatible. 7 usages of deprecated API. 6 usages of experimental API`.
The full run took 25m 11s. Current source/test/artifact receipts match the ZIP.
See the [milestone evidence](wicket-oat-provider-construction-evidence.json).
The ZIP retains 0.1.0 metadata; no installation or publication was performed.


## Twenty-eighth milestone: compiled DataTable construction

See [the contract](datatable-construction.md) for reviewed table/grid construction,
source wrapper and eager-factory guards, deferred callbacks and MCP impact.
Existing page structure, validation and component contexts share the retained
constructor fact and exact later stop. No new tools, response fields or selectors
are introduced. Private framework children and runtime row/model/query values
remain outside the graph.

Final milestone verification: **3,266** normal tests,
**32** actual MCP host tests and **22** release-checker unit tests passed
with zero failures/errors/skips. The 103 focused table/provider/module tests passed.
Both read-only Oat corpora (Wicket 10.11.0 and 11.0.0) retain the actual RowsTable
and constructor prefix in an isolated fixture before toolbar setup. Its 26 inferred
bindings and constructor note are shared by native/MCP views. DataTablePage passes
the compiled constructor and next stops at DataTable.addTopToolbar.
All 36 primary finding/binding/source-dependency-count summaries, earlier groups
and probes, six markers, four saved reports and 318 application input hashes are
preserved. Configuration, structure and binary compatibility checks passed against
IU-262.10968.63: `Compatible. 7 usages of deprecated API. 6 usages of experimental API`. The full run took 26m 21s.
Current source/test/artifact receipts match the development ZIP.
See the [milestone evidence](wicket-oat-datatable-construction-evidence.json).
No installation or publication was performed.


## Twenty-ninth milestone: fresh DataTable header-toolbar registration

See [the contract](toolbar-registration.md) for ownership and source-construction
guards, deferred rendering/sorting, actual-source prefix probes and MCP impact.
Existing structure, validation and component-context diagnostics share the same
facts and next blocker. No new tool, response field or selector is introduced.

Final milestone verification: **3,302** normal tests,
**33** actual MCP host tests and **22** release-checker unit tests passed
with zero failures/errors/skips. The 107 focused tests passed.
Both read-only Oat corpora (Wicket 10.11.0 and 11.0.0) verify the original header
registration/anonymous render hook in an isolated fixture with 26 inferred matches.
Native/MCP construction and registration notes agree. DataTablePage next stops at
OatNavigationToolbar's compiled NavigationToolbar constructor (offset 898, line 19).
All 36 finding/binding summaries, prior groups/probes, six markers, four saved
reports and 318 input hashes are preserved. Two additional wrapper files increase
DataTablePage's source dependencies from 35 to 37. Configuration, structure and
binary compatibility checks passed against IU-262.10968.63: `Compatible. 7 usages of deprecated API. 6 usages of experimental API`.
The full run took 27m 2s. Current source/test/artifact receipts match the ZIP.
See the [milestone evidence](wicket-oat-toolbar-registration-evidence.json).
No installation or publication was performed.

## Navigation-toolbar construction follow-up

Acceptance covers eager navigator factory dispatch, pure source forwarding, same-table
registration, deferred link initialization and model/provider queries, interface
effects, source ancestry bounds and cancellation. Native/MCP equality, module
isolation, source/markup freshness, response bounds and read-only actual-host
checks reuse shared analyzers and value snapshots. The original Oat source inputs
remain untouched; fixture probes isolate the constructor prefix from later setup.
See [the contract](navigation-toolbar-construction.md).

Final milestone verification: **3,346** normal tests,
**34** actual MCP host tests and **22** release-checker unit tests passed
with zero failures/errors/skips. All 151 focused tests passed.
Both read-only Oat corpora (Wicket 10.11.0 and 11.0.0) verify the original pair of
toolbar registrations and eager source navigator factory in an isolated fixture
with 26 inferred matches and nine branch counterparts. Native/MCP facts agree.
DataTablePage next stops at `chooser.getTrigger().add(...)` in OatDataTable.newTools,
offset 15146, line 316. All 36 primary finding/binding summaries, prior groups and
probes, six markers, four saved reports and 318 application input hashes are
preserved. One extra source dependency increases DataTablePage's count from 37 to
38. Configuration, structure and binary compatibility checks passed against
IU-262.10968.63: `Compatible. 7 usages of deprecated API. 6 usages of experimental API`. The full run took 27m 26s.
Current source/test/artifact receipts match the ZIP. See the
[milestone evidence](wicket-oat-navigation-toolbar-evidence.json).
No installation or publication was performed.

## Owned component accessor follow-up

Acceptance covers selected pure getter dispatch, constructor-established private-final
framework children, escape/reassignment/alias rejection, interface initialization,
behavior-only additions, ancestry bounds, cancellation and deferred runtime models.
Native/MCP equality and freshness, response bounds, module isolation and actual-host
responses/descriptions share the semantic analyzers and value snapshots.
See [the contract](owned-component-accessors.md).

Final milestone verification: **3,389** normal tests,
**35** actual MCP host tests and **22** release-checker unit tests passed
with zero failures/errors/skips. All 224 focused tests passed.
Both read-only Oat corpora (Wicket 10.11.0 and 11.0.0) verify the original
newTools accessor decoration in an isolated fixture with 26 inferred matches and
nine branch counterparts. Native/MCP facts agree. DataTablePage next stops at
the bulk-action clear button's fluent source call, OatDataTable.java offset 6999,
line 144. All 36 primary finding/binding summaries, earlier groups/probes, six
markers, four reports and 318 application input hashes are preserved. Its source
dependency count increases from 38 to 40. Configuration, structure and binary
compatibility checks passed against IU-262.10968.63: `Compatible. 7 usages of deprecated API. 6 usages of experimental API`. The full run took 28m 44s.
Current source/test/artifact receipts match the ZIP. See the
[milestone evidence](wicket-oat-owned-accessor-evidence.json).
No installation or publication was performed.

## Fresh inline fluent configuration follow-up

Acceptance covers selected receiver identity, private behavior/exact Model ownership,
source enum initialization guards, constructor/binding effects, semantic scan and
chain/ancestry bounds, cancellation, preserved callbacks and runtime storage/render/Ajax
separation. Native/MCP views, module isolation, source/markup freshness and actual-host
tool descriptions share the analyzers and value snapshots.
See [the contract](fluent-component-configuration.md).

Final milestone verification: **3,435** normal tests,
**36** actual MCP host tests and **22** release-checker unit tests passed
with zero failures/errors/skips. All 270 focused tests passed.
Both read-only Oat corpora (Wicket 10.11.0 and 11.0.0) verify the original complete
OatDataTable constructor and newTools fluent configuration in a minimal fixture
with two matched declarations. Native/MCP facts agree. DataTablePage next stops
at `new EmptyStateToolbar(table)`, OatDataTable.java offset 8234, line 165.
All 36 primary finding/binding summaries, earlier groups/probes, six markers,
four reports and 318 application input hashes are preserved. Its source dependency
count increases from 40 to 42. Configuration, structure and binary compatibility
checks passed against IU-262.10968.63: `Compatible. 7 usages of deprecated API. 6 usages of experimental API`. The full run took 29m 5s.
Current source/test/artifact receipts match the ZIP. See the
[milestone evidence](wicket-oat-fluent-configuration-evidence.json).
No installation or publication was performed.

Final milestone verification: **3,476** normal tests,
**37** actual MCP host tests and **22** release-checker unit tests passed
with zero failures/errors/skips. All 311 focused tests passed.
Both read-only Oat corpora (Wicket 10.11.0 and 11.0.0) verify original-source
EmptyStateToolbar forwarding and the same independent later query blocker through
native and MCP reports. DataTablePage next stops at `DataTable.getColumns()` in
visibleColumnCount, OatDataTable.java offset 8600, line 176. Its initial hierarchy
remains unresolved. All 36 primary finding/binding/source-dependency summaries,
earlier groups/probes, six markers, four reports and 318 application input hashes
are preserved. Configuration, structure and binary compatibility checks passed
against IU-262.10968.63: `Compatible. 7 usages of deprecated API. 6 usages of experimental API`. The full run took 30m 14s.
Current source/test/artifact receipts match the ZIP. See the
[milestone evidence](wicket-oat-table-forwarding-evidence.json).
No installation or publication was performed.

Final milestone verification: **3,525** normal tests,
**38** actual MCP host tests and **22** release-checker unit tests passed
with zero failures/errors/skips. All 360 focused regressions passed.
Both read-only Oat corpora (Wicket 10.11.0 and 11.0.0) verify original-source
columns getter/size reads and the next cache-assignment blocker through native and
MCP reports. DataTablePage next stops at the private toolbar cache assignment,
OatDataTable.java offset 8214, line 165. Its initial hierarchy remains unresolved.
All 36 primary binding/source-dependency summaries, earlier groups/probes, six
markers, four reports and 318 application input hashes are preserved; the existing
forwarding probe records its reviewed progress. Configuration, structure and binary
compatibility checks passed against IU-262.10968.63: `Compatible. 7 usages of deprecated API. 6 usages of experimental API`.
The full run took 30m 36s. Current source/test/artifact receipts match the ZIP.
See the [milestone evidence](wicket-oat-columns-read-evidence.json).
No installation or publication was performed.

Final milestone verification: **3,568** normal tests,
**39** actual MCP host tests and **22** release-checker unit tests passed
with zero failures/errors/skips. All 440 focused regressions passed.
Both read-only Oat corpora (Wicket 10.11.0 and 11.0.0) verify the original-source
cache protocol through native and MCP reports. The isolated complete table probe
now completes initial source setup with two root matches. Full DataTablePage next
reaches the unchanged 20,000-step budget at MenuItem.java offset 2752, line 69;
its initial hierarchy remains unresolved and tracked sources increase from 42 to 48.
All 36 primary finding/binding summaries, earlier groups/probes except the completed
forwarding probe, six markers, four reports and 318 application input hashes are
preserved. Configuration, structure and binary compatibility checks passed against
IU-262.10968.63: `Compatible. 7 usages of deprecated API. 6 usages of experimental API`. The full run took 28m 44s.
Current source/test/artifact receipts match the ZIP.
See the [milestone evidence](wicket-oat-toolbar-cache-evidence.json).
No installation or publication was performed.

Verification in the main workspace: **199 focused tests**, **39 actual MCP host
tests**, both read-only 36-page Wicket corpora and plugin configuration/structure/
binary compatibility checks passed. The 14 new tests cover declaration reuse and
integration guards. The original 318 application files remain unchanged. Binary
verification against IU-262.10968.63: `Compatible. 7 usages of deprecated API. 6 usages of experimental API`.
The full normal suite was not rerun; no current full-suite or release-readiness
claim is made. See the [milestone evidence](wicket-oat-declaration-work-evidence.json).
The change is integrated and verified in the main workspace. No installation or
publication was performed.

Final milestone verification: **99 focused tests**, **41 actual MCP host tests**,
both read-only 36-page Wicket corpora and plugin configuration/structure/binary
compatibility checks passed with zero failures/errors/skips. All seven original
DataTablePage contexts were retrieved after fresh captures and exactly matched
native projections. Wicket 10.11.0: index 10,788 bytes, largest selected context 97,880 bytes; Wicket 11.0.0: index 10,782 bytes, largest selected context 97,874 bytes. Every response is below 524,288 bytes.
All 36 primary reports, earlier context/completion/probe facts, six markers, five
saved reports and the original 318 application inputs are preserved. Compatibility
against IU-262.10968.63: `Compatible. 7 usages of deprecated API. 6 usages of experimental API`. Current source/host/artifact
receipts match the development ZIP. The full normal suite was not rerun; no current
full-suite or release-readiness claim is made.
See the [milestone evidence](wicket-oat-navigation-retrieval-evidence.json).
No installation, signing or publication was performed.


## Confirmation consumer milestone verification

**238 focused checks**, **42 actual MCP host tests**, both read-only 36-page
Wicket corpora and plugin configuration/structure/binary compatibility checks
passed with zero failures/errors/skips. Each original DataTablePage gains one
separate five-binding inferred row-delete confirmation context. All eight
navigation contexts were retrieved after fresh captures and exactly match native
projections. Wicket 10.11.0: index 11,876 bytes, largest selected context 98,436 bytes; Wicket 11.0.0: index 11,870 bytes, largest selected context 98,430 bytes. Each response stays below 524,288 bytes.
All 36 initial page reports, earlier context/completion/probe facts, five saved
reports and the original 318 application inputs are preserved. Bulk deletion is
explicitly withheld at `table.clearSelection(t)`, the next contract boundary.
Compatibility against IU-262.10968.63: `Compatible. 7 usages of deprecated API. 6 usages of experimental API`.
Current source/host/artifact receipts match the development ZIP. The full normal
suite was not rerun; no current full-suite or release-readiness claim is made.
See the [milestone evidence](wicket-oat-confirmation-route-evidence.json).
No installation, signing or publication was performed.


## Selection helper milestone verification

**282 focused checks**, **43 actual MCP host tests**, both read-only 36-page
Wicket corpora and plugin configuration/structure/binary compatibility checks
passed with zero failures/errors/skips. DataTablePage's bulk confirmation now has
its own five-binding inferred context, alongside the preserved row-delete
context. All nine contexts were retrieved after fresh captures and exactly
matched native projections. Wicket 10.11.0: index 12,138 bytes, largest selected context 98,166 bytes; Wicket 11.0.0: index 12,132 bytes, largest selected context 98,160 bytes. Each response stays below 524,288 bytes.
All 36 initial reports, earlier corpus facts, six saved reports and the original
318 application inputs are preserved; the prior bulk-helper diagnostic is
resolved. Selection/model effects and outcomes remain unknown. Compatibility
against IU-262.10968.63: `Compatible. 7 usages of deprecated API. 6 usages of experimental API`. Source, host and artifact
receipts match the development ZIP. The full normal suite was not rerun; no
full-suite or release-readiness claim is made.
See the [milestone evidence](wicket-oat-selection-clear-evidence.json).
No installation, signing or publication was performed.


## Record-action milestone verification

**334 focused checks**, **44 actual MCP host tests**, both read-only 36-page
Wicket corpora and plugin configuration/structure/binary compatibility checks
passed with zero failures/errors/skips. DataTablePage's direct row and bulk
“Mark as paid” callbacks each have a separate inferred action context with one
table binding. All eleven contexts were retrieved after fresh captures and
exactly matched native projections. Wicket 10.11.0: index 13,192 bytes, largest selected context 98,167 bytes; Wicket 11.0.0: index 13,186 bytes, largest selected context 98,161 bytes. Each response stays below
524,288 bytes. All 36 initial reports, earlier corpus facts, seven saved reports
and the original 318 application inputs are preserved. Plain-page bindings remain
withheld where initial target discovery is unresolved. Callback/registration,
predicate, record construction and business outcomes retain their uncertainty.
Compatibility against IU-262.10968.63: `Compatible. 7 usages of deprecated API. 6 usages of experimental API`. Source, host
and artifact receipts match the development ZIP. The full normal suite was not
rerun; no full-suite or release-readiness claim is made.
See the [milestone evidence](wicket-oat-record-action-evidence.json).
No installation, signing or publication was performed.


## Stored-action attachment verification

**691 focused checks**, **45 actual MCP host tests**, both read-only
36-page Wicket corpora and plugin configuration/structure/binary compatibility
checks passed with zero failures/errors/skips. The added plain-page fixture has
one inferred initial table binding and one separate inferred action target on
both Wicket 10.11.0 and 11.0.0. All prior corpus facts, 36 initial reports, nine
saved text reports and the original 318 application inputs are preserved.
The runtime fixture confirms registration/render does not invoke its callback;
explicit fixture dispatch reaches the updater. Analysis executes no callback.
Compatibility against IU-262.10968.63: `Compatible. 7 usages of deprecated API. 6 usages of experimental API`.
Source, host and artifact receipts match the development ZIP. The full normal
suite was not rerun; no full-suite or release-readiness claim is made. See the
[milestone evidence](wicket-oat-stored-action-evidence.json). No installation,
signing or publication was performed.


## Repeated-registration verification

**716 focused checks**, **46 actual MCP host tests**, both read-only
36-page Wicket corpora and plugin configuration/structure/binary compatibility
checks passed with zero failures/errors/skips. The added fixture retains two
source storage sites, one inferred initial table binding and two independent
inferred action contexts on Wicket 10.11.0 and 11.0.0. All previous corpus facts,
36 initial reports, nine saved text reports and the original 318 application
inputs are preserved. Runtime fixture dispatch changes when registration order
is reversed, while analysis keeps the active callback and outcomes unknown.
The exact 64/65-site boundary, canceled sibling audit, stale sibling selectors,
project/module isolation, response caps and read-only behavior are covered.
Compatibility against IU-262.10968.63: `Compatible. 7 usages of deprecated API. 6 usages of experimental API`.
Source, host and artifact receipts match the development ZIP. The full normal
suite was not rerun; no full-suite or release-readiness claim is made. See the
[milestone evidence](wicket-oat-repeated-action-evidence.json). No installation,
signing or publication was performed.


## Chained-registration verification

**745 focused checks**, **47 actual MCP host tests**, both read-only
36-page Wicket corpora and plugin configuration/structure/binary compatibility
checks passed with zero failures/errors/skips. The added fluent fixture retains two
distinct method-token storage sites, one inferred table binding and two independent
inferred action contexts on Wicket 10.11.0 and 11.0.0. All previous corpus facts,
36 initial reports, nine saved text reports and the original 318 application
inputs are preserved. Runtime fixture dispatch changes when chain order
is reversed, while analysis keeps the active callback and outcomes unknown.
The exact 64/65-link boundary, canceled chain audit, stale link/return selectors,
project/module isolation, response caps and read-only behavior are covered.
Compatibility against IU-262.10968.63: `Compatible. 7 usages of deprecated API. 6 usages of experimental API`.
Source, host and artifact receipts match the development ZIP. The full normal
suite was not rerun; no full-suite or release-readiness claim is made. See the
[milestone evidence](wicket-oat-chained-action-evidence.json). No installation,
signing or publication was performed.


## Initializer-registration verification

**778 focused checks**, **48 actual MCP host tests**, both read-only
36-page Wicket corpora and plugin configuration/structure/binary compatibility
checks passed with zero failures/errors/skips. The added initializer fixture retains two
distinct method-token storage sites, one fresh allocation, one inferred table binding and two independent
inferred action contexts on Wicket 10.11.0 and 11.0.0. All previous corpus facts,
36 initial reports, nine saved text reports and the original 318 application
inputs are preserved. Runtime fixture dispatch changes when initializer order
is reversed, while analysis keeps the active callback and outcomes unknown.
The combined 64/65-site boundary, canceled initializer audit, stale allocation/link/return selectors,
project/module isolation, response caps and read-only behavior are covered.
Compatibility against IU-262.10968.63: `Compatible. 7 usages of deprecated API. 6 usages of experimental API`.
Source, host and artifact receipts match the development ZIP. The full normal
suite was not rerun; no full-suite or release-readiness claim is made. See the
[milestone evidence](wicket-oat-initialized-action-evidence.json). No installation,
signing or publication was performed.
