package example;

import org.apache.wicket.markup.html.WebPage;
import org.apache.wicket.markup.html.form.Form;

public class FormComponentsPage extends WebPage {
    public FormComponentsPage() {
        Form<Void> form = new Form<>("form");
        form.add(new CustomInput("name"), new CompositeInput("editor"));
        add(form);
    }
}
