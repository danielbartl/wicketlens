package example;

import static example.ConstantIds.TITLE;

import org.apache.wicket.markup.html.WebPage;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.form.Form;
import org.apache.wicket.markup.html.form.TextField;

/** Source constants, a static import, and initialization share one analyzed graph. */
public class ConstantSetupPage extends WebPage {
    private static final String STATUS = "sta" + "tus";

    public ConstantSetupPage() {
        add(new Label(TITLE, "Constant IDs"));
        add(new Label(ConstantIds.PREFIX + (ConstantIds.INDEX + 1), "Calculated ID"));
        if (ConstantIds.SHOW_FORM) {
            Form<Void> form = new Form<>(ConstantIds.FORM);
            form.add(new TextField<String>(ConstantIds.NAME));
            add(form);
        }
    }

    @Override
    protected void onInitialize() {
        super.onInitialize();
        add(new Label(STATUS, "Ready"));
    }
}
