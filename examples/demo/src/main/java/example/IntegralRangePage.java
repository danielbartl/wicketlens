package example;

import org.apache.wicket.markup.html.WebPage;
import org.apache.wicket.markup.html.basic.Label;

/** Range facts prove a three-way binding without exposing a runtime numeric value. */
public class IntegralRangePage extends WebPage {
    public IntegralRangePage(int count) {
        if (count < 0) {
            add(new Label("title", "Negative"));
        }
        if (count == 0) {
            add(new Label("ti" + "tle", "Zero"));
        }
        if (count > 0) {
            add(new Label("title", "Positive"));
        }
        if (count >= 10) {
            add(new Label("details", "At least ten"));
        }
    }
}
