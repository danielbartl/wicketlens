package example;

import org.apache.wicket.Page;
import org.apache.wicket.markup.html.WebPage;
import org.apache.wicket.protocol.http.WebApplication;
import org.apache.wicket.util.tester.WicketTester;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

// Isolated smoke test: adapt sessions, routes, resources and subject construction before running.
class HelloPageTest {
    private WicketTester tester;

    @BeforeEach
    void setUp() {
        tester = new WicketTester(new WebApplication() {
            @Override
            public Class<? extends Page> getHomePage() {
                return example.HelloPage.class;
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
        tester.startPage(new example.HelloPage(new org.apache.wicket.request.mapper.parameter.PageParameters()));
        tester.assertRenderedPage(example.HelloPage.class);
        tester.assertLabel("greeting", "Hello, Guest");
    }
    @Test
    void rendersTheRequestedName() {
        tester.startPage(new example.HelloPage(new org.apache.wicket.request.mapper.parameter.PageParameters().add("name", "Ada")));
        tester.assertRenderedPage(example.HelloPage.class);
        tester.assertLabel("greeting", "Hello, Ada");
    }

}
