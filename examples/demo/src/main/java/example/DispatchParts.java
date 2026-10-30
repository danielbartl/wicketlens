package example;

import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.form.Form;
import org.apache.wicket.markup.html.form.TextField;

/** A stateless instance factory whose concrete allocation type is known. */
public class DispatchParts implements ComponentParts {
    @Override
    public Label label(String id) {
        return new Label(id, "From an interface factory");
    }

    @Override
    public Form<Void> form(String id) {
        Form<Void> form = new Form<>(id);
        populate(form);
        return form;
    }

    private void populate(Form<Void> form) {
        form.add(new TextField<String>("name"));
    }
}
