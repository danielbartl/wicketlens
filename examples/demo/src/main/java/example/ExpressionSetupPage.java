package example;

import org.apache.wicket.markup.html.WebPage;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.form.Form;
import org.apache.wicket.markup.html.form.TextField;

public class ExpressionSetupPage extends WebPage {
    private final ExpressionParts parts = new ExpressionParts("hea" + "ding", 2);

    public ExpressionSetupPage() {
        if (parts.enabled() && "enabled".equals("en" + "abled")) {
            add(new Label(parts.id("Title")));
        } else {
            add(new Label("inactive"));
        }
        int ordinal = 1 + 2;
        add(new Label("item" + ordinal));
        if (ordinal == 3) {
            add(new Label("status"));
        }
    }

    @Override
    protected void onInitialize() {
        super.onInitialize();
        if (parts.enabled()) {
            Form<Void> form = new Form<>("form");
            form.add(new TextField<String>("name"));
            add(form);
        }
    }
}
