# Wicket–Spring test example

Import `pom.xml` into IntelliJ IDEA or run `mvn test` in this directory.
This example uses Java 17 or newer; Mockito runs as a test JVM agent.

Open `OrdersPage.java`, then use **Tools → Generate Wicket Spring Test…**.
Choose a different test name to keep the existing example. The preview can create
the file in the existing `src/test/java` root after Maven import.

`OrdersPageSpringTest` shows how to adapt the recipe: stub the injected service
before constructing the page and assert the actual rendered label. The Spring
context and Wicket application are isolated in the test; no production service
configuration, server, or database is needed. The other `spring-demo` contains
intentional inspection findings and is separate from this runnable example.

For the agent-assisted version, follow [the reproducible MCP workflow](AGENT_WORKFLOW.md).
It reuses the existing test, adds service-driven title and escaping assertions,
and uses normal editing and Maven commands. After adaptation, run
`python3 verify_agent_workflow.py` to prove a hard-coded-title regression is caught
in a disposable copy and that restoring it passes. The checkout's production
source is never mutated; logs and results are under `target/agent-workflow/`.

[OrderDetailsPage](COMPLEX_WORKFLOW.md) adds manual constructor arguments, two
named mocks, an explicit generic binding and a custom session. Its two tests
keep the existing six cases intact. Run the same verifier with
`--scenario spring-details` for the price regression, or
`--scenario spring-orders` for the original title regression.
