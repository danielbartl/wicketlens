package example;

import org.apache.wicket.markup.html.WebPage;

public class ProviderFactoryPage extends WebPage {
    private final ComponentParts first = ProviderFactories.create("title");
    private final ComponentParts second = ProviderFactories.create("subtitle");
    private final ComponentParts third;

    public ProviderFactoryPage() {
        ProviderFactories.Builder builder = new ProviderFactories.Builder();
        third = builder.create("footer");
        add(first.label("unused"));
        add(second.label("unused"));
        add(first.form("form"));
        add(third.label("unused"));
    }
}
