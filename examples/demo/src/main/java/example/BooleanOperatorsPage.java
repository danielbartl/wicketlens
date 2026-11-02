package example;

import org.apache.wicket.markup.html.WebPage;
import org.apache.wicket.markup.html.basic.Label;

/** Equality and XOR describe complementary structure; eager reads retain their safety audit. */
public class BooleanOperatorsPage extends WebPage {
    public BooleanOperatorsPage(boolean first, boolean second) {
        if (first == second) {
            add(new Label("title", "Same flags"));
        }
        if (first ^ second) {
            add(new Label("ti" + "tle", "Different flags"));
        }
        if (first & second) {
            add(new Label("details", "Both flags are set"));
        }
    }
}
