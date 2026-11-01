# More complex Spring test setup

`OrderDetailsPage` deliberately needs application-specific setup:

- A constructor argument selecting the order.
- Named `orders` and `prices` service mocks, stubbed before constructing the page.
- A `categories` bean with declared type `List<String>`. The planner retains a
  manual-binding guard rather than guessing a generic mock or production bean.
- An `OrderDetailsSession`. A default WebSession is insufficient; the test's
  isolated WebApplication overrides `newSession`, and the session prefix is
  configured before subject construction.

The existing Spring plan returns `MANUAL_PAGE`, the two supported mock suggestions,
the manual categories binding and a throwing `createSubject()` hook. Nothing in
static context establishes active profiles, production bean choices, session
configuration or runtime validity. The test uses explicit isolated choices.

`OrderDetailsPageSpringTest` shows the reviewed adaptation: register the categories
list, supply the order ID, install the custom session and retain Spring context/
WicketTester cleanup. Its two cases assert title, price and category for different
orders/prefixes, including the constructor ID passed to `priceFor`.
The original `OrdersPageSpringTest` and its six cases remain intact.

## Reproduce with an agent

Import this demo's POM, connect the Preview 2 MCP tools and supply the actual
IDE project/module names. For a fresh adaptation, choose a new test filename or
use a disposable copy. Do not overwrite existing tests without an explicit task.

```text
Retrieve wicket_get_spring_test_plan and wicket_get_page_structure for
example.OrderDetailsPage, and wicket_get_component_context for its price path,
using the current projectPath, moduleName and sourceSet main.

Read the page, session, service interfaces, POM and existing tests. Starting from
the scaffold, implement the manual constructor and categories binding explicitly.
Configure an isolated WebApplication that creates OrderDetailsSession; stub the
two named mocks before constructing subjects. Keep the context/listener/cleanup.
Do not infer production beans or profiles, or remove guards without reviewing
their setup requirements. Keep all production code and dependencies unchanged.

Add behavior assertions for two order IDs with different prices and session
prefixes, including category text and the argument passed to PricingService.
Review existing coverage instead of duplicating tests. Run mvn -B test and fix
actual failures without weakening assertions or changing production behavior.
Then run python3 verify_agent_workflow.py --scenario spring-details.
Report actual baseline, expected regression and restored results.
```

From this directory:

```sh
mvn -B test
python3 verify_agent_workflow.py --scenario spring-details
```

The verifier substitutes a hard-coded price only in a disposable copy. It requires
a failure on that price assertion, matching case counts, passing restoration and
unchanged authored checkout inputs. Add `--offline` for a cached dependency setup.
The original title regression remains available with `--scenario spring-orders`.
This is a portable scenario, not proof that arbitrary application constructors,
authentication, sessions or bean graphs can be generated automatically.
