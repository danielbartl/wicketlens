package example;

import org.apache.wicket.Page;
import org.apache.wicket.protocol.http.WebApplication;

/** Direct page mounts for the project-wide Mounted URLs view. */
public class RoutesApplication extends WebApplication {
    private static final String CUSTOMERS = "/customers";

    @Override public Class<? extends Page> getHomePage() { return CustomerPage.class; }

    @Override protected void init() {
        super.init();
        mountPage(CUSTOMERS, CustomerPage.class);
        mountPage(CUSTOMERS + "/${id}", CustomerPage.class);
    }
}
