package example;

import org.apache.wicket.markup.html.WebPage;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.form.Button;
import org.apache.wicket.markup.html.form.CheckBox;
import org.apache.wicket.markup.html.form.Form;
import org.apache.wicket.markup.html.form.TextField;

/** Static fixture with private final fields shared by constructor and initialization setup. */
public class FieldComponentsPage extends WebPage {
    private final Form<Void> form = new Form<>("form");
    private final Label title;
    private final Label status = new Label("status", "Ready");

    public FieldComponentsPage() {
        title = new Label("title", "Field components");
        add(title, form);
    }

    @Override
    protected void onInitialize() {
        super.onInitialize();

        TextField<String> name = new TextField<>("name");
        form.add(name, new CheckBox("enabled"), new Button("save"));
        add(status);
    }
}
