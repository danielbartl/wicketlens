package example;

import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.form.Form;
import org.apache.wicket.markup.html.form.TextField;

/** Helpers, factories, source inheritance, and a forwarding-only custom constructor. */
public class SourceSetupPage extends SourceSetupBasePage {
    public SourceSetupPage() {
        add(new SourceHeading("Source-aware components", "title"));
        add(buildForm("form"));
        add(label("footer"));
    }

    private Form<Void> buildForm(String id) {
        Form<Void> form = new Form<>(id);
        populate(form);
        return form;
    }

    private void populate(Form<?> form) {
        form.add(new TextField<String>("name"));
    }

    private Label label(String id) {
        return new Label(id, "Created by a source factory");
    }

    @Override
    protected void onInitialize() {
        super.onInitialize();
        addStatus();
    }

    private void addStatus() {
        add(new Label("status", "Added by a private helper"));
    }
}
