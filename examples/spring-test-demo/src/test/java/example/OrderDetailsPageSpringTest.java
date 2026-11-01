package example;

import java.util.List;
import org.apache.wicket.Page;
import org.apache.wicket.Session;
import org.apache.wicket.request.Request;
import org.apache.wicket.request.Response;
import org.apache.wicket.markup.html.WebPage;
import org.apache.wicket.protocol.http.WebApplication;
import org.apache.wicket.spring.injection.annot.SpringComponentInjector;
import org.apache.wicket.util.tester.WicketTester;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.StaticApplicationContext;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.junit.jupiter.api.Assertions.assertEquals;

// Adapted from the WicketLens scaffold; bindings are explicit isolated test choices.
class OrderDetailsPageSpringTest {
    private StaticApplicationContext springContext;
    private WicketTester tester;
    private example.OrderService dependency1;
    private example.PricingService dependency2;

    private String orderId = "A-42";
    private String sessionPrefix = "Test: ";

    @BeforeEach
    void setUp() {
        springContext = new StaticApplicationContext();
        dependency1 = mock(example.OrderService.class);
        springContext.getBeanFactory().registerSingleton("orders", dependency1);
        dependency2 = mock(example.PricingService.class);
        springContext.getBeanFactory().registerSingleton("prices", dependency2);
        configureMocks();
        registerAdditionalBeans();
        springContext.refresh();
        tester = new WicketTester(new WebApplication() {
            @Override
            public Class<? extends Page> getHomePage() {
                return example.OrderDetailsPage.class;
            }

            @Override
            public Session newSession(Request request, Response response) {
                OrderDetailsSession session = new OrderDetailsSession(request);
                session.setTitlePrefix(sessionPrefix);
                return session;
            }

            @Override
            protected void init() {
                super.init();
                getComponentInstantiationListeners().add(new SpringComponentInjector(this, springContext));
            }
        });
    }

    private void configureMocks() {
        when(dependency1.title()).thenReturn("Orders");
        when(dependency2.priceFor("A-42")).thenReturn("EUR 12.50");
        when(dependency2.priceFor("B-7")).thenReturn("EUR 87.30");
    }

    private void registerAdditionalBeans() {
        List<String> categories = List.of("Priority");
        springContext.getBeanFactory().registerSingleton("categories", categories);
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
    void rendersSubject() throws Throwable {
        tester.startPage(createSubject());
        assertDetails("Test: Orders / A-42", "EUR 12.50");
        verify(dependency2).priceFor("A-42");
    }

    @Test
    void rendersAnotherOrderWithCustomSessionPrefix() throws Throwable {
        orderId = "B-7";
        sessionPrefix = "Review: ";
        // Set the active custom session before constructing the second subject.
        ((OrderDetailsSession) tester.getSession()).setTitlePrefix(sessionPrefix);

        tester.startPage(createSubject());

        assertDetails("Review: Orders / B-7", "EUR 87.30");
        verify(dependency2).priceFor("B-7");
    }

    private void assertDetails(String title, String price) {
        tester.assertRenderedPage(example.OrderDetailsPage.class);
        tester.assertLabel("title", title);
        tester.assertLabel("price", price);
        tester.assertLabel("category", "Priority");
        assertEquals(title, tester.getComponentFromLastRenderedPage("title").getDefaultModelObject());
        assertEquals(price, tester.getComponentFromLastRenderedPage("price").getDefaultModelObject());
        assertEquals("Priority", tester.getComponentFromLastRenderedPage("category").getDefaultModelObject());
    }

    private example.OrderDetailsPage createSubject() throws Throwable {
        return new OrderDetailsPage(orderId);
    }
}
