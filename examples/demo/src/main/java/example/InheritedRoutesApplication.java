package example;

import org.apache.wicket.Page;
import org.apache.wicket.protocol.http.WebApplication;

/** The inherited setup and private helper appear in the Mounted URLs view. */
public class InheritedRoutesApplication extends BaseRoutesApplication {
    @Override public Class<? extends Page> getHomePage() { return CustomerPage.class; }
}

abstract class BaseRoutesApplication extends WebApplication {
    @Override protected void init() {
        super.init();
        mountCustomers();
    }

    private void mountCustomers() {
        mountPage("/inherited-customers", CustomerPage.class);
    }
}
