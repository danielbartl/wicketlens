package example;

import org.apache.wicket.markup.html.WebPage;
import org.apache.wicket.markup.html.basic.Label;

/** Stable comparisons share structural facts without supplying a runtime numeric value. */
public class IntegralComparisonPage extends WebPage {
    public IntegralComparisonPage(int count) {
        if (count > 0) {
            add(new Label("title", "Positive"));
        }
        if (count < 1L) {
            add(new Label("ti" + "tle", "Nonpositive"));
        }
        if (count >= 1) {
            add(new Label("details", "Additional details"));
        }
    }
}
