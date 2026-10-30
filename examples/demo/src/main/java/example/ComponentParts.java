package example;

import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.form.Form;

public interface ComponentParts {
    Label label(String id);
    Form<Void> form(String id);
}
