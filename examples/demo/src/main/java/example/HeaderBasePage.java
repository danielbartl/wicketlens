package example;

import org.apache.wicket.markup.html.WebPage;
import org.apache.wicket.markup.html.basic.Label;

public class HeaderBasePage extends WebPage {
    public HeaderBasePage() {
        add(new Label("baseTitle", "Shared layout"));
    }
}
