package example;

import org.apache.wicket.markup.html.WebPage;
import org.apache.wicket.markup.html.WebMarkupContainer;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.list.ListItem;
import org.apache.wicket.markup.html.list.ListView;
import org.apache.wicket.markup.repeater.RepeatingView;

/** Common item structure is inferred; optional children and row counts stay unknown. */
public class RepeaterBranchPage extends WebPage {
    public RepeaterBranchPage(boolean compact) {
        add(new ListView<String>("people", java.util.List.of("Ada", "Grace")) {
            @Override protected void populateItem(ListItem<String> item) {
                item.add(compact ? new Label("name", item.getModelObject())
                        : new Label("na" + "me", item.getModelObject()));
                if (!compact) {
                    item.queue(new Label("details", "More information"));
                }
            }
        });
        RepeatingView messages = new RepeatingView("messages");
        add(messages);
        for (String value : java.util.List.of("Welcome", "Hello")) {
            WebMarkupContainer item = new WebMarkupContainer(messages.newChildId());
            if (compact) {
                item.add(new Label("text", value));
            } else {
                item.add(new Label("text", value));
            }
            messages.add(item);
        }
    }
}
