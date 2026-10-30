package example;

import org.apache.wicket.markup.html.form.Form;
import org.apache.wicket.markup.html.basic.Label;

/** Reads immutable provider/configuration state through source accessors. */
public class AccessorParts implements ComponentParts {
    private final ComponentParts delegate;
    private final String headingId;

    public AccessorParts(ComponentParts delegate, String headingId) {
        this.delegate = delegate;
        this.headingId = headingId;
    }

    public ComponentParts delegate() {
        return delegate;
    }

    public String headingId() {
        return headingId;
    }

    @Override
    public Label label(String ignored) {
        return delegate().label(headingId());
    }

    @Override
    public Form<Void> form(String id) {
        return delegate().form(id);
    }
}
