package example;

import org.apache.wicket.markup.html.WebMarkupContainer;
import org.apache.wicket.markup.html.WebPage;
import org.apache.wicket.markup.html.basic.Label;

/** Both runtime choices establish the same structure, with distinct source origins. */
public class BranchMergePage extends WebPage {
    public BranchMergePage(boolean compact) {
        if (compact) {
            WebMarkupContainer content = new WebMarkupContainer("content");
            add(content);
            content.add(new Label("title", "Short"));
        } else {
            WebMarkupContainer content = new WebMarkupContainer("content");
            content.add(new Label("title", "Long heading"));
            add(content);
        }
        add(new Label("footer", "WicketLens branch analysis"));
    }
}
