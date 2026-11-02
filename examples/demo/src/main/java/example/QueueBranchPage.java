package example;

import org.apache.wicket.markup.html.WebMarkupContainer;
import org.apache.wicket.markup.html.WebPage;
import org.apache.wicket.markup.html.basic.Label;

/** Queue placement is proved separately on each feasible setup path. */
public class QueueBranchPage extends WebPage {
    public QueueBranchPage(boolean compact) {
        queue(new WebMarkupContainer("content"));
        if (compact) {
            queue(new Label("title", "Short"));
        } else {
            queue(new Label("ti" + "tle", "Long heading"));
        }
        if (!compact) {
            queue(new Label("details", "More information"));
        }
    }
}
