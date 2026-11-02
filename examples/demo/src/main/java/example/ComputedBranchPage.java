package example;

import org.apache.wicket.markup.html.WebPage;
import org.apache.wicket.markup.html.basic.Label;

/** Known computed values can agree across paths while optional IDs stay conditional. */
public class ComputedBranchPage extends WebPage {
    public ComputedBranchPage(boolean compact) {
        String prefix = "user";
        add(new Label(prefix + "Name"));
        add(new Label(compact ? "item" + (1 << 2) : "item4"));
        if (compact) {
            add(new Label("hint"));
        }
    }
}
