package example;

import org.apache.wicket.markup.html.form.FormComponent;

/** A leaf input: its ID belongs to the caller's markup. */
public class CustomInput extends FormComponent<String> {
    public CustomInput(String id) {
        super(id);
        setRequired(true);
    }
}
