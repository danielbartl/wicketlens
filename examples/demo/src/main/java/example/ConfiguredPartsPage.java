package example;

import org.apache.wicket.markup.html.WebPage;

public class ConfiguredPartsPage extends WebPage {
    private final ComponentParts first = new ConfiguredParts(new DispatchParts(), "title");
    private final ComponentParts second = new ConfiguredParts(new DispatchParts(), "subtitle");

    public ConfiguredPartsPage() {
        add(first.label("unused"));
        add(second.label("unused"));
        add(first.form("form"));
    }
}
