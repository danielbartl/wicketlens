# Wicket 11 dependency baseline

The source checkout now uses Apache Wicket **11.0.0** for its Java PSI fixtures,
disposable MCP projects and all four Maven demos. Wicket remains a fixture/project
dependency: it is not bundled with WicketLens or added to the plugin runtime.
The published **0.1.0-rc.1** artifact and its Wicket 10.11.0 evidence are unchanged.

## Dependency changes

| Dependency | Previous baseline | Source baseline |
| --- | --- | --- |
| Wicket core, Spring and tester | 10.11.0 | 11.0.0 |
| Spring Web and transitive Spring modules | 6.2.12 | 7.0.9 |
| Jakarta Servlet API for tester execution | 6.0.0 | 6.1.0 |
| JUnit Jupiter / standalone console for generated tests and demos | 5.13.4 / 1.13.4 | 6.1.3 |
| Maven demo Java release | 17 | 21 |

The versions follow the released
[Wicket parent POM](https://repo.maven.apache.org/maven2/org/apache/wicket/wicket-parent/11.0.0/wicket-parent-11.0.0.pom).
The [Wicket 11 requirements](https://wicket.apache.org/versions/wicket-11.x/)
raise the application baseline to Java 21, Servlet 6.1 and Spring 7. WicketLens's
own Gradle/IDE build continues to use Java 25 and IDEA 2026.2.3.
The disposable MCP project uses Java 21 language settings; generated Spring test
execution compiles with `--release 21`.

Wicket tester 11.0.0 has compile dependencies on JUnit Jupiter engine/params
6.1.3. Keeping the old explicit JUnit 5 dependency caused mixed engine/API/platform
versions and failed test discovery. Both runnable Maven demos and the standalone
console used to execute generated tests now use the matching JUnit 6.1.3 release.
The IDE plugin's JUnit 4 fixture runner is separate and stays unchanged.

## Framework contract review

The released 10.11.0 and 11.0.0 source archives were compared for the framework
contracts used by analysis. Wicket 11 archive SHA-256 values:

| Archive | SHA-256 |
| --- | --- |
| [wicket-core sources](https://repo.maven.apache.org/maven2/org/apache/wicket/wicket-core/11.0.0/wicket-core-11.0.0-sources.jar) | `485a75789d32fc4be2a83e1fc755d5b8ade8b72c2e26ce160e1e865f55ef08b0` |
| [wicket-spring sources](https://repo.maven.apache.org/maven2/org/apache/wicket/wicket-spring/11.0.0/wicket-spring-11.0.0-sources.jar) | `393afacab64e7ad774d00fdd2fb6003b9fa48230d1d9cd2e3c68e87288f72421` |
| [wicket-ioc sources](https://repo.maven.apache.org/maven2/org/apache/wicket/wicket-ioc/11.0.0/wicket-ioc-11.0.0-sources.jar) | `dde11d42b75d1c58f736d7da149ecbcc1382ce9889462634038e9612b4bfb0b1` |

- The nineteen reviewed component classes retain their constructor signatures
  and ID forwarding. `Component` still invokes instantiation listeners during its
  constructor, before subclass field initialization. Button's changed default
  model-string escaping does not change its component ID or attachment contract.
- `Component.onInitialize()` retains the superclass-delegation flag.
  `MarkupContainer` and `Page` sources are unchanged, including component
  additions, queue/dequeue and superclass initialization behavior.
- `ListView`, `ListItem`, `RepeatingView`, queue support classes and
  `MountedMapper` retain the reviewed static behavior. Markup merge/header and
  companion-resource strategies retain the existing supported contracts.
  Resource-name iterators add input validation; WicketLens still analyzes only
  explicit conventional companions/variants and does not infer runtime fallback.
- Wicket Spring's Java sources are unchanged. IOC's ByteBuddy proxy factory now
  uses the dependency type's loader and a private method-handle lookup for
  non-JDK classes. The null-only injection, listener registration, serializable
  proxy and final-type restrictions behind existing inspections remain intact.
  Module/package openness and arbitrary runtime proxy creation remain unverified;
  no new proxy-success claim or inspection is introduced.

Component state storage, Ajax implementations, cryptography and authentication
changes in Wicket 11 do not expand this analyzer's static subset. Existing
uncertainty, editing restrictions and discovery bounds still apply. Earlier
feature audit documents and published release records retain their original
10.11.0 evidence; this record describes the subsequent source baseline review.

## MCP impact

No tool or response-schema change is needed. This upgrade adds no analysis fact
or operation: all nine tools continue to reuse the same semantic analyzers and
value snapshots. Existing project isolation, freshness, cancellation, response
bounds and read-only checks run against the Wicket 11 fixture libraries, including
the separate actual-host suite. Client configuration and tool descriptions remain
valid. Published graphical/client evidence continues to describe RC 1; it is not
new Wicket 11 graphical evidence.

## Verification

The complete **2,317-test** plugin suite, **eight-test** actual MCP host suite,
**20** release-checker tests and all **11** runnable demo tests pass. All four
demos compile, all three disposable behavior regression/restoration checks pass,
and packaging/structure checks and the local release-readiness checker pass.
IntelliJ binary verification is **Compatible**, retaining the existing five
deprecated and six experimental API notices.

During the dependency upgrade, the rebuilt RC 1 ZIP and production classes were
byte-identical to the published RC 1 artifact. The upgrade validated its analysis
against the new project libraries without changing the shipped runtime.
Final [0.1.0](releases/0.1.0.md) changes release metadata and retains those
production classes. Commands and results are recorded in the
[acceptance record](acceptance.md#wicket-11-source-baseline-2026-11-10).
The full production-application accuracy/performance study remains deferred.
This dependency upgrade does not publish a new plugin release or website.
