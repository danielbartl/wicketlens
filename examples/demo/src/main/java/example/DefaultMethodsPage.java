package example;

import org.apache.wicket.markup.html.WebPage;
import org.apache.wicket.markup.html.basic.Label;

/** Provider defaults, inherited page defaults, and explicit interface super calls. */
public final class DefaultMethodsPage extends WebPage implements DefaultComponentParts {
    private final ComponentParts parts = new DefaultMethodParts("From the provider");

    public DefaultMethodsPage() {
        add(parts.form("provided"));
        add(form("inherited"));
        add(DefaultComponentParts.super.form("explicit"));
    }

    @Override
    public Label label(String id) {
        return new Label(id, "From the final page");
    }
}
