package example;

import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.form.Form;

/** Immutable configuration plus a dependency with a known concrete allocation. */
public class ConfiguredParts implements ComponentParts {
    private final ComponentParts delegate;
    private final String headingId;

    public ConfiguredParts(ComponentParts delegate, String headingId) {
        this.delegate = delegate;
        this.headingId = headingId;
    }

    @Override
    public Label label(String ignored) {
        return delegate.label(headingId);
    }

    @Override
    public Form<Void> form(String id) {
        return delegate.form(id);
    }
}
