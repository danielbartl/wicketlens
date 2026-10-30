package example;

import java.util.List;
import org.apache.wicket.markup.html.WebMarkupContainer;
import org.apache.wicket.markup.html.WebPage;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.list.ListItem;
import org.apache.wicket.markup.html.list.ListView;
import org.apache.wicket.markup.repeater.RepeatingView;

/** Queue placement and logical item templates; no runtime row counts are inferred. */
public class QueueRepeaterPage extends WebPage {
    public QueueRepeaterPage() {
        queue(new WebMarkupContainer("queued"), new Label("queuedName", "Ada"));

        add(new ListView<String>("rows", List.of("Ada", "Grace")) {
            @Override
            protected void populateItem(ListItem<String> item) {
                item.add(new Label("name", item.getModelObject()));
            }
        });

        RepeatingView manualRows = new RepeatingView("manualRows");
        add(manualRows);
        for (String name : List.of("Ada", "Grace")) {
            WebMarkupContainer item = new WebMarkupContainer(manualRows.newChildId());
            item.add(new Label("value", name));
            manualRows.add(item);
        }
    }
}
