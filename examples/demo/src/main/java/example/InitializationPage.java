package example;

import org.apache.wicket.markup.html.WebPage;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.form.Button;
import org.apache.wicket.markup.html.form.CheckBox;
import org.apache.wicket.markup.html.form.Form;
import org.apache.wicket.markup.html.form.TextField;

/** Static fixture combining constructor setup with the supported initialization hook. */
public class InitializationPage extends WebPage {
    public InitializationPage() {
        add(new Label("title", "Initialized components"));
    }

    @Override
    protected void onInitialize() {
        super.onInitialize();

        Form<Void> form = new Form<>("form");
        form.add(new TextField<String>("name"), new CheckBox("enabled"), new Button("save"));
        Label status = new Label("status", "Ready");
        add(form, status);
    }
}
