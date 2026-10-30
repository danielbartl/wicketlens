package example;

import org.apache.wicket.markup.html.basic.Label;

/** Closed root dispatch plus a known instance factory behind an interface. */
public final class DispatchPage extends DispatchBasePage {
    private final ComponentParts parts = new DispatchParts();

    public DispatchPage() {
        add(parts.label("title"));
        add(parts.form("form"));
    }

    @Override
    protected void setup() {
        // This runs during superclass construction, before parts is initialized.
        add(new Label("dispatched", "From the final page override"));
    }
}
