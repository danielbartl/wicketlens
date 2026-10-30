package example;

import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.form.Form;
import org.apache.wicket.markup.html.form.TextField;

/** Content inserted into BaseLayoutPage's wicket:child slot. */
public class InheritedLayoutPage extends BaseLayoutPage {
    public InheritedLayoutPage() {
        add(new Label("title", "Inherited layout"));
        Form<Void> form = new Form<>("form");
        form.add(new TextField<String>("name"));
        add(form);
    }
}
