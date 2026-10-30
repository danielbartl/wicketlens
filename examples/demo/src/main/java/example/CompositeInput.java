package example;

import org.apache.wicket.markup.html.form.FormComponentPanel;
import org.apache.wicket.markup.html.form.TextField;

/** Open this class or its companion to analyze the internal input separately. */
public class CompositeInput extends FormComponentPanel<String> {
    private final TextField<String> value = new TextField<>("value");

    public CompositeInput(String id) {
        super(id);
        add(value);
    }

    @Override
    public void convertInput() {
        setConvertedInput(value.getConvertedInput());
    }
}
