package example;

import org.apache.wicket.markup.html.WebPage;
import org.apache.wicket.markup.html.basic.Label;

/** Source-visible superclass constructor and lifecycle setup. */
public class SourceSetupBasePage extends WebPage {
    private final Label baseTitle = new Label("baseTitle", "Source setup example");

    public SourceSetupBasePage() {
        add(baseTitle);
    }

    @Override
    protected void onInitialize() {
        super.onInitialize();
        addBaseStatus();
    }

    private void addBaseStatus() {
        add(new Label("baseStatus", "Added by the source superclass"));
    }
}
