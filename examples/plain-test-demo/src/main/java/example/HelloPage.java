package example;

import org.apache.wicket.markup.html.WebPage;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.request.mapper.parameter.PageParameters;

public class HelloPage extends WebPage {
    public HelloPage(PageParameters parameters) {
        super(parameters);
        add(new Label("greeting", "Hello, " + parameters.get("name").toString("Guest")));
    }
}
