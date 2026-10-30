package example;

import org.apache.wicket.markup.html.WebPage;
import org.apache.wicket.markup.html.basic.Label;

public class DispatchBasePage extends WebPage {
    public DispatchBasePage() {
        setup();
    }

    protected void setup() {
        add(new Label("baseOnly", "Overridden in the final page"));
    }
}
