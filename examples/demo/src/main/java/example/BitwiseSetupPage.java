package example;

import org.apache.wicket.markup.html.WebPage;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.form.Form;
import org.apache.wicket.markup.html.form.TextField;

/** Flag masks, eager booleans, computed IDs, and initialization in one graph. */
public class BitwiseSetupPage extends WebPage {
    public BitwiseSetupPage() {
        BitwiseFlags config = new BitwiseFlags(BitwiseFlags.DEFAULT);
        if (config.canSave() & (true ^ false)) {
            add(new Label("title", "Bitwise setup"));
        }
        add(new Label("item" + (config.itemIndex() + 1), "Calculated ID"));
        Form<Void> form = new Form<>("form");
        if ((BitwiseFlags.DEFAULT & (BitwiseFlags.EDIT | BitwiseFlags.SAVE)) == BitwiseFlags.DEFAULT) {
            form.add(new TextField<String>("name"));
        }
        add(form);
    }

    @Override
    protected void onInitialize() {
        super.onInitialize();
        if ((~BitwiseFlags.DEFAULT & 4) != 0) {
            add(new Label("status", "Ready"));
        }
    }
}
