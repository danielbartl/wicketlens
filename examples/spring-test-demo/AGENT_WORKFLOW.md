# Adapt a WicketLens scaffold with an agent

This is a portable example of an agent using WicketLens's read-only MCP context
and its normal editing/testing tools. WicketLens does not write or run the test.
Use Preview 2 or newer with `wicket_get_spring_test_plan`; Preview 1 has six
tools and does not include that addition.

Import this demo's `pom.xml` into IntelliJ IDEA, let Maven resolve its dependencies,
and connect your agent through the [MCP setup guide](../../docs/mcp-client-setup.md).
Use the IDE's exact project path and module name; these depend on how the demo
was imported. Keep the existing framework versions and test JVM-agent setup.

## Reproducible prompt

Replace `<open-project>` and `<module>` before using this prompt. Run it from the
demo directory, with permission to edit its existing test and run Maven/Python.

```text
Extend the existing src/test/java/example/OrdersPageSpringTest.java in this demo.
Preserve its current rendersSubject test, isolated Spring context, mock registration,
injection listener, and cleanup. Do not change production Java/HTML or pom.xml.
If the requested cases already exist, review and run them instead of duplicating them.

First call these read-only MCP tools once each, using projectPath <open-project>,
className example.OrdersPage, moduleName <module>, sourceSet main:
- wicket_get_spring_test_plan
- wicket_get_page_structure
- wicket_get_component_context with componentPath title

Read the demo source and existing test through your normal file tools. Use the
returned plan's Java scaffold as the lifecycle reference and keep its dependency
and uncertainty notes. Review every manual TODO before changing any guard.
Do not infer runtime values or production bean choices from static context.

Adapt the existing test to cover several service-supplied titles, including an
empty value and Unicode. Add a check that HTML-looking service text is rendered
as escaped text rather than markup. Assert the Label type, its model text, and
the rendered HTML where relevant; verify the service supplies the value.
Use the already configured JUnit Jupiter/Mockito dependencies, with mock stubbing
before page construction. Avoid duplicating the existing smoke test in a new class.

Run mvn -B test, inspect failures and fix the test if necessary. Then run
python3 verify_agent_workflow.py (add --offline when dependencies are cached).
This verifier introduces a hard-coded title only in a disposable copy, requires
a rendered-title assertion failure, restores that copy and requires passing tests.
Do not weaken assertions to make the negative control pass. Report the actual
MCP facts, changes, passing tests and expected failing control. Keep the work
limited to this demo; no commits, publishing, build changes or new dependencies.
```

## Run the checks again

Use Java 17 or newer; the recorded walkthrough uses the installed Java 25 JDK.

```sh
mvn -B test
python3 verify_agent_workflow.py
```

The verifier runs a fresh baseline, then mutates and restores only a temporary
copy of `pom.xml` and `src/`. It fails if there are no test reports, a build/setup
error, skipped tests, no assertion failure on the substituted title, a failed
restoration, or changed authored demo inputs. The checkout's page stays intact.
Logs and the result are under `target/agent-workflow/`; add `--offline` after the
first dependency resolution if desired. This script verifies adapted tests; it
does not call an AI service or generate them.

The example has a supported page constructor and one named mock, so it needs no
manual constructor or binding replacement. Its `configureMocks()` stub remains
essential. Other subjects may retain throwing TODO guards that need separate
review. Empty `manualBindings` does not guarantee that arbitrary application
setup is complete. This exercise covers the demo only; representative application
evaluation and broader plain-Wicket planning remain separate work.
