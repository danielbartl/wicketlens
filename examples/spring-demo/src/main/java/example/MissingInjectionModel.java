package example;

import org.apache.wicket.model.LoadableDetachableModel;
import org.apache.wicket.spring.injection.annot.SpringBean;

// Intentionally missing explicit injection: models are not constructed by the component listener.
public class MissingInjectionModel extends LoadableDetachableModel<String> {
    @SpringBean
    private OrderService orders;

    @Override
    protected String load() {
        return orders.summary();
    }
}
