# Spring test scaffolding

Open a concrete Java Wicket `WebPage`, `Panel`, or `FormComponentPanel` with
Wicket–Spring injection intent, or its uniquely paired HTML companion. Choose
**Tools → Generate Wicket Spring Test…** to review a deterministic Java test.
Analysis waits for indexing and committed documents and can be cancelled.
No AI service or IntelliJ Spring plugin is used.

The preview lets you change the test class name and copy the scaffold. If the
module has an existing writable Java test source root, **Create Test** writes it
there with normal undo. Generated roots are excluded and package prefixes are
respected. Existing files are never overwritten. Edits to the analyzed sources
invalidate the preview; generate again before copying or creating the test.
Build files and source-root configuration are not changed.

## What the recipe contains

- JUnit Jupiter setup/cleanup and one WicketTester rendering smoke test.
- A separate test `WebApplication` with a `SpringComponentInjector` installed
  before constructing the subject, using default proxy wrapping.
- An isolated `StaticApplicationContext` with Mockito mocks for supported source
  fields. Explicit literal names use that exact name; type lookups use the field
  name so Wicket can disambiguate multiple bindings of the same declared type.
  Repeated requests for the same name/type share one mock. Source bean candidates
  never select production bindings for the test.
- A `configureMocks()` hook before rendering; stub return values and behavior
  needed by the subject's constructor and lifecycle.
- Cleanup that destroys WicketTester and closes the Spring context, including
  when initialization or rendering fails.

Public interfaces and accessible non-final, non-generic classes with a usable
zero-argument constructor can receive mocks. Unknown names/types, generic fields,
final or sealed types, conflicting named types, explicit field initialization, unsupported
annotations, and incomplete dependency discovery produce manual binding TODOs.
The generated `registerAdditionalBeans()` then throws until those TODOs have
been reviewed and replaced. This keeps an incomplete recipe from looking like
a successful wiring test. Mockito's ability to mock a final class does not make
that class proxyable by Wicket's default lazy injector.

Pages use an accessible no-argument or `PageParameters` constructor; components
use an accessible `String` constructor with the ID `subject`. Other constructors
receive a `createSubject()` hook that throws until arguments are supplied.
Abstract, nested, and generic subjects are not supported by this first generator.

## Dependencies and customization

Use the project's Wicket/Spring versions. The recipe requires `wicket-tester`,
`wicket-spring`, `spring-context`, the matching Servlet API, JUnit Jupiter, and
Mockito as test dependencies, and a JUnit Jupiter test runner. On modern JDKs,
configure Mockito as a test JVM agent following its documentation. No test
library is bundled into WicketLens.

This is an isolated test application. Adapt sessions, authentication, routes,
resources, locale/style variants, and any other application-specific setup.
Source discovery does not enumerate every component constructed at runtime.
The initial rendering assertion is a smoke test; add meaningful assertions for
the behavior you want to protect. A runnable, portable example is provided in
[the Spring test demo](../examples/spring-test-demo/pom.xml).

The lifecycle follows Apache Wicket's
[Spring integration guide](https://wicket.apache.org/docs/spring.html) and
[WicketTester guide](https://wicket.apache.org/docs/wicket-tester.html), using a
test-only Spring context. Preview 2 also exposes the same plan and source
through the read-only [`wicket_get_spring_test_plan`](mcp-spring-test-plan.md) tool
for an agent to adapt. The [agent-assisted demo workflow](../examples/spring-test-demo/AGENT_WORKFLOW.md)
now exercises adaptation and normal Maven execution, including an expected
regression failure and a passing restoration in a disposable copy. MCP itself
does not write or run tests. The published first preview contains the original
IDE generator.

The [general planner](wicket-test-planning.md), available in development builds
through **Tools → Generate Wicket Test…** and `wicket_get_test_plan`, uses plain
Wicket/JUnit without Spring or Mockito unless Spring intent is recognized. It
shares the constructor/renderer/preview/create machinery with this recipe.
The [complex Spring demo](../examples/spring-test-demo/COMPLEX_WORKFLOW.md) exercises
manual arguments, multiple mocks, a generic binding and a custom session, with
passing behavior tests and a controlled failing-regression/restoration check.
