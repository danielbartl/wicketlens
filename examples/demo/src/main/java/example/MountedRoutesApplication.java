package example;

import org.apache.wicket.Page;
import org.apache.wicket.core.request.mapper.MountedMapper;
import org.apache.wicket.protocol.http.WebApplication;
import org.apache.wicket.request.mapper.parameter.PageParametersEncoder;

/** Portable inline mapper registrations for WicketLens's Mounted URLs view. */
public class MountedRoutesApplication extends WebApplication {
    @Override
    public Class<? extends Page> getHomePage() {
        return CustomerPage.class;
    }

    @Override
    protected void init() {
        super.init();
        mount(new MountedMapper("/mapped-customers/${id}", CustomerPage.class));
        mount(new MountedMapper("/encoded-customers", CustomerPage.class, new PageParametersEncoder()));
    }
}
