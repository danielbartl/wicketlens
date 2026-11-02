package example;

import org.apache.wicket.markup.html.WebPage;
import org.apache.wicket.markup.html.basic.Label;

/** Unknown ternaries preserve common structure and retain differing ID variants. */
public class TernarySetupPage extends WebPage {
    public TernarySetupPage(boolean compact) {
        add(compact ? new Label("title", "Short") : new Label("title", "Long heading"));
        add(new Label(compact ? "summary" : "details"));
    }
}
