package example;

import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.panel.Panel;

public class GreetingPanel extends Panel {
    public GreetingPanel(String id) {
        super(id);
        add(new Label("message", "Welcome to plain Wicket"));
    }
}
