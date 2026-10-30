package example;

import org.apache.wicket.markup.html.WebPage;
import org.apache.wicket.markup.html.basic.Label;

public class ConditionalSetupPage extends WebPage {
    private final ConditionalParts parts = new ConditionalParts(new DispatchParts(), true);

    public ConditionalSetupPage() {
        if (parts.enabled() && !false) {
            add(parts.label("title"));
        } else {
            add(new Label("inactive"));
        }
        add(label(false, "unused", "subtitle"));
    }

    private Label label(boolean first, String primary, String other) {
        if (first) {
            return new Label(primary);
        }
        return new Label(other);
    }

    @Override
    protected void onInitialize() {
        super.onInitialize();
        if (!parts.enabled()) {
            return;
        }
        add(parts.form("form"));
    }
}
