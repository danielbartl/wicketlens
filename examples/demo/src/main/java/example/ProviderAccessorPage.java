package example;

import org.apache.wicket.markup.html.WebPage;

public class ProviderAccessorPage extends WebPage {
    private final ComponentParts first = new AccessorParts(new DispatchParts(), "title");
    private final ComponentParts second = new AccessorParts(new DispatchParts(), "subtitle");

    public ProviderAccessorPage() {
        ComponentParts parts = first();
        add(parts.label("unused"));
        add(second().label("unused"));
        add(first().form("form"));
    }

    private ComponentParts first() {
        return first;
    }

    private ComponentParts second() {
        return second;
    }
}
