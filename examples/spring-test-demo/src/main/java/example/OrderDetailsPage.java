package example;

import java.util.List;
import org.apache.wicket.markup.html.WebPage;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.spring.injection.annot.SpringBean;

public class OrderDetailsPage extends WebPage {
    @SpringBean(name = "orders") private OrderService orders;
    @SpringBean(name = "prices") private PricingService prices;
    @SpringBean(name = "categories") private List<String> categories;

    public OrderDetailsPage(String orderId) {
        OrderDetailsSession session = (OrderDetailsSession) getSession();
        add(new Label("title", session.titlePrefix() + orders.title() + " / " + orderId));
        add(new Label("price", prices.priceFor(orderId)));
        add(new Label("category", categories.get(0)));
    }
}
