package example;

import org.apache.wicket.markup.html.WebPage;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.spring.injection.annot.SpringBean;

// Inspection fixture: try Go to Declaration on "orders" and completion inside the empty name.
public class BeanNamesPage extends WebPage {
    @SpringBean(name = "orders")
    private OrderService namedOrders;

    // Intentionally incompatible: the source configuration declares a String bean.
    @SpringBean(name = "message")
    private OrderService incompatible;

    @SpringBean(name = "")
    private OrderService byType;

    public BeanNamesPage() {
        add(new Label("title", "Named bean editing examples"));
    }
}
