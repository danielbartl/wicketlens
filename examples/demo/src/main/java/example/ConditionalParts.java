package example;

import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.form.Form;

/** Immutable flags can select reviewed setup without guessing runtime configuration. */
public class ConditionalParts implements ComponentParts {
    private final ComponentParts delegate;
    private final boolean enabled;

    public ConditionalParts(ComponentParts delegate, boolean enabled) {
        this.delegate = delegate;
        this.enabled = enabled;
    }

    public boolean enabled() {
        return enabled;
    }

    @Override
    public Label label(String id) {
        if (enabled()) {
            return delegate.label(id);
        }
        return delegate.label("inactive");
    }

    @Override
    public Form<Void> form(String id) {
        return delegate.form(id);
    }
}
