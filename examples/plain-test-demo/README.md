# Plain Wicket test planning demo

Run `mvn -B test` or import `pom.xml` into IntelliJ IDEA. Use Java 17 or newer.
This project has Wicket, its tester, the Servlet API and JUnit Jupiter; it has
no Spring or Mockito dependency and needs no server or database.

`HelloPage` requires PageParameters. Its generated scaffold uses an empty
parameter set; the adapted tests assert the default and a requested name.
`GreetingPanel` accepts a String ID and uses `startComponentInPage`; its test
asserts the nested label under `subject:message`.

Use WicketLens Preview 2 or newer and **Tools → Generate Wicket Test…**, or
call [`wicket_get_test_plan`](../../docs/wicket-test-planning.md) through the IDE's
MCP server with the actual project/module and `example.HelloPage` or
`example.GreetingPanel`. Choose a different output name if creating another test;
the example already includes adapted tests. Preview 1 lacks
this planner. Spring scaffolding remains available separately.

To reproduce the workflow with an agent, ask it to obtain a plan and page/component
context, read the source, then adapt the scaffold through its normal editing tools.
Keep the POM and production files intact, review constructor/application TODOs,
and add behavior assertions before running Maven. The MCP tool itself never
writes or executes tests; static context does not establish runtime correctness.

From the repository root, prove that a deliberate greeting regression is caught:

```sh
python3 examples/spring-test-demo/verify_agent_workflow.py \
  --project examples/plain-test-demo --scenario plain
```

Add `--offline` after dependencies are cached. This reuses the verifier from the
Spring demo; it mutates and restores only a disposable copy. All phases must run
the same number of tests, the mutated greeting must produce assertion failures
rather than build/setup errors, and the checkout's authored inputs must remain
unchanged. Logs/results are in `target/agent-workflow/`.
