package example;

import org.apache.wicket.markup.html.WebPage;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.form.Form;
import org.apache.wicket.markup.html.form.TextField;

/** Deliberate mismatches for the WicketLens inspection quick fixes. */
public class QuickFixPage extends WebPage {
    public QuickFixPage() {
        Form<Void> form = new Form<>("form");
        form.add(new Label("title", "Heading"));
        form.add(new TextField<String>("name"));
        add(form);
    }
}
