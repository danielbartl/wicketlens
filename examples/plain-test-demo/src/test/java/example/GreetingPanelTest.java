package example;

import org.apache.wicket.Page;
import org.apache.wicket.markup.html.WebPage;
import org.apache.wicket.protocol.http.WebApplication;
import org.apache.wicket.util.tester.WicketTester;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

// Isolated smoke test: adapt sessions, routes, resources and subject construction before running.
class GreetingPanelTest {
    private WicketTester tester;

    @BeforeEach
    void setUp() {
        tester = new WicketTester(new WebApplication() {
            @Override
            public Class<? extends Page> getHomePage() {
                return WebPage.class;
            }

            @Override
            protected void init() {
                super.init();
                // Customize sessions/routes/resources and any application-specific setup here.
            }
        });
    }

    @AfterEach
    void tearDown() {
        if (tester != null) tester.destroy();
    }

    @Test
    void rendersSubject() throws Throwable {
        tester.startComponentInPage(new example.GreetingPanel("subject"));
        tester.assertComponent("subject", example.GreetingPanel.class);
        tester.assertLabel("subject:message", "Welcome to plain Wicket");
    }
}
