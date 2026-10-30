package example;

import org.apache.wicket.injection.Injector;
import org.apache.wicket.model.LoadableDetachableModel;
import org.apache.wicket.spring.injection.annot.SpringBean;

public class InjectedOrderModel extends LoadableDetachableModel<String> {
    @SpringBean
    private OrderService orders;

    public InjectedOrderModel() {
        Injector.get().inject(this);
    }

    @Override
    protected String load() {
        return orders.summary();
    }
}
