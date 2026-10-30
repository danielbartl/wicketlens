package example;

import org.apache.wicket.markup.html.WebPage;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.form.Form;
import org.apache.wicket.markup.html.form.TextField;

/** Small static-analysis fixture; this is not a deployed Wicket application. */
public class CustomerPage extends WebPage {
    public CustomerPage() {
        add(new Label("title", "Customers"));
        Form<?> form = new Form<>("form");
        form.add(new TextField<String>("name"));
        add(form);
        add(new Label("what jk,m /javaOnly", "Informational finding"));
    }
}
