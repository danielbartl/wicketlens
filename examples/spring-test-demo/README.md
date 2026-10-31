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
