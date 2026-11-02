package example;

import org.apache.wicket.markup.html.WebPage;
import org.apache.wicket.markup.html.basic.Label;

/** A definite title and optional details share one statically reviewed setup. */
public class PartialBranchPage extends WebPage {
    public PartialBranchPage(boolean compact) {
        if (compact) {
            add(new Label("title", "Short"));
        } else {
            add(new Label("title", "Long heading"), new Label("details", "More information"));
        }
    }
}
