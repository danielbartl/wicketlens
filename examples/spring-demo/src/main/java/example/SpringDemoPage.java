package example;

import org.apache.wicket.markup.html.WebPage;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.spring.injection.annot.SpringBean;

// Intentionally problematic inspection fixture; not a recommended wiring pattern.
public class SpringDemoPage extends WebPage {
    @SpringBean
    private OrderService orders = null;

    @SpringBean
    private transient OrderService archivedOrders;

    @SpringBean
    private FinalAuditService audit;

    @SpringBean
    private OrderService untouchedOrders;

    public SpringDemoPage() {
        orders = null;
        add(new Label("title", "Wicket and Spring inspection examples"));
    }
}
