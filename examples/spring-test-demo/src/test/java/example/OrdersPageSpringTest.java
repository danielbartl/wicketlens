package example;

import org.apache.wicket.Page;
import org.apache.wicket.protocol.http.WebApplication;
import org.apache.wicket.spring.injection.annot.SpringComponentInjector;
import org.apache.wicket.util.tester.WicketTester;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.StaticApplicationContext;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

// Adapted from WicketLens's scaffold: configure the mock and assert the rendered title.
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
}
