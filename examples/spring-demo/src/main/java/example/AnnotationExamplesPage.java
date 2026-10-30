package example;

import org.apache.wicket.markup.html.WebPage;
import org.apache.wicket.markup.html.basic.Label;
import org.springframework.beans.factory.annotation.Autowired;

// Wicket's construction listener does not process @Autowired.
public class AnnotationExamplesPage extends WebPage {
    @Autowired
    private OrderService orders;

    public AnnotationExamplesPage() {
        add(new Label("title", "Annotation and manual injection examples"));
    }
}
