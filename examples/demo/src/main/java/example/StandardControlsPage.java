package example;

import java.util.List;
import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.ajax.markup.html.AjaxLink;
import org.apache.wicket.ajax.markup.html.form.AjaxButton;
import org.apache.wicket.markup.html.WebPage;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.form.Button;
import org.apache.wicket.markup.html.form.CheckBox;
import org.apache.wicket.markup.html.form.DropDownChoice;
import org.apache.wicket.markup.html.form.Form;
import org.apache.wicket.markup.html.form.SubmitLink;
import org.apache.wicket.markup.html.link.BookmarkablePageLink;
import org.apache.wicket.markup.html.link.ExternalLink;
import org.apache.wicket.markup.html.link.Link;
import org.apache.wicket.markup.html.panel.FeedbackPanel;

/** Static-analysis fixture for the ten additional reviewed component classes. */
public class StandardControlsPage extends WebPage {
    public StandardControlsPage() {
        add(new Label("title", "Standard controls"));
        add(new FeedbackPanel("feedback"));

        Form<?> form = new Form<>("form");
        form.add(new CheckBox("enabled"));
        form.add(new DropDownChoice<String>("choice", List.of("one", "two")));
        form.add(new Button("save"));
        form.add(new SubmitLink("submitLink"));
        form.add(new AjaxButton("ajaxSave") {});
        add(form);

        add(new BookmarkablePageLink<Void>("bookmark", CustomerPage.class));
        add(new ExternalLink("external", "https://wicket.apache.org/"));
        add(new Link<Void>("link") {
            @Override public void onClick() {}
        });
        add(new AjaxLink<Void>("ajaxLink") {
            @Override public void onClick(AjaxRequestTarget target) {}
        });
    }
}
