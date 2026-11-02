package example;

import org.apache.wicket.markup.html.WebPage;
import org.apache.wicket.markup.html.basic.Label;

/** Complementary compound checks share immutable inputs; their runtime values stay unknown. */
public class CompoundSetupPage extends WebPage {
    public CompoundSetupPage(boolean authenticated, boolean expanded) {
        if (authenticated && expanded) {
            add(new Label("title", "Expanded"));
        }
        if (!authenticated || !expanded) {
            add(new Label("ti" + "tle", "Compact"));
        }
        if (authenticated || expanded) {
            add(new Label("details", "More information"));
        }
    }
}
