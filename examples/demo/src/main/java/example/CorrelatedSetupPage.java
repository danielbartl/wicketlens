package example;

import org.apache.wicket.markup.html.WebPage;
import org.apache.wicket.markup.html.basic.Label;

/** Complementary checks share a stable value, including its local negated alias. */
public class CorrelatedSetupPage extends WebPage {
    public CorrelatedSetupPage(boolean compact) {
        boolean expanded = !compact;
        if (compact) {
            add(new Label("title", "Short"));
        }
        if (expanded) {
            add(new Label("title", "Long heading"));
        }
        if (compact) {
            add(new Label("details", "Compact mode details"));
        }
    }
}
