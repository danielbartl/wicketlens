package example;

import org.apache.wicket.markup.html.WebPage;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.spring.injection.annot.SpringBean;

public class OrdersPage extends WebPage {
    @SpringBean(name = "orders")
    private OrderService orders;

    public OrdersPage() {
        add(new Label("title", orders.title()));
    }
}
