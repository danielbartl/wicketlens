package example;

import org.apache.wicket.markup.html.form.Form;

/** Shared form construction dispatches label creation to the concrete receiver. */
public interface DefaultComponentParts extends ComponentParts {
    @Override
    default Form<Void> form(String id) {
        Form<Void> form = new Form<>(id);
        populate(form);
        return form;
    }

    private void populate(Form<Void> form) {
        form.add(label("name"));
    }
}
