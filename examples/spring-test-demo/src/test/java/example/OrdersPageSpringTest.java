package example;

import org.apache.wicket.Page;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.protocol.http.WebApplication;
import org.apache.wicket.spring.injection.annot.SpringComponentInjector;
import org.apache.wicket.util.tester.WicketTester;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.support.StaticApplicationContext;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

// Adapted from WicketLens's scaffold: configure the mock and assert the rendered title.
// Plan caveats: static discovery is partial and does not prove runtime wiring or select
// production beans/profiles. Review routes, sessions, resources and mock behavior.
// Required dependencies (availability unchecked by MCP): wicket-tester, wicket-spring,
// spring-context, jakarta.servlet-api, junit-jupiter and mockito-core; use project versions.
class OrdersPageSpringTest {
    private StaticApplicationContext springContext;
    private WicketTester tester;
    private OrderService dependency1;

    @BeforeEach
    void setUp() {
        springContext = new StaticApplicationContext();
        dependency1 = mock(OrderService.class);
        springContext.getBeanFactory().registerSingleton("orders", dependency1);
        configureMocks();
        springContext.refresh();
        tester = new WicketTester(new WebApplication() {
            @Override
            public Class<? extends Page> getHomePage() {
                return OrdersPage.class;
            }

            @Override
            protected void init() {
                super.init();
                getComponentInstantiationListeners().add(new SpringComponentInjector(this, springContext));
            }
        });
    }

    private void configureMocks() {
        when(dependency1.title()).thenReturn("Orders from the test context");
    }

    @AfterEach
    void tearDown() {
        try {
            if (tester != null) tester.destroy();
        } finally {
            if (springContext != null) springContext.close();
        }
    }

    @Test
    void rendersSubject() {
        tester.startPage(new OrdersPage());
        tester.assertRenderedPage(OrdersPage.class);
        tester.assertLabel("title", "Orders from the test context");
    }

    @ParameterizedTest
    @ValueSource(strings = {"Pending orders", "Completed orders", "", "注文 — café 🏏"})
    void rendersServiceSuppliedTitle(String title) {
        when(dependency1.title()).thenReturn(title);

        tester.startPage(new OrdersPage());

        assertTitle(title, title);
        verify(dependency1).title();
    }

    @Test
    void escapesHtmlLookingServiceTitle() {
        String title = "<strong>Orders & returns</strong>";
        when(dependency1.title()).thenReturn(title);

        tester.startPage(new OrdersPage());

        assertTitle(title, "&lt;strong&gt;Orders &amp; returns&lt;/strong&gt;");
        assertFalse(tester.getLastResponseAsString().contains("<strong>"));
        verify(dependency1).title();
    }

    private void assertTitle(String modelText, String renderedText) {
        tester.assertComponent("title", Label.class);
        assertTrue(tester.getLastResponseAsString().contains(
                "<h1 wicket:id=\"title\">" + renderedText + "</h1>"),
                () -> "Unexpected rendered title: " + tester.getLastResponseAsString());
        Label label = (Label) tester.getComponentFromLastRenderedPage("title");
        assertEquals(modelText, label.getDefaultModelObject());
    }
}
