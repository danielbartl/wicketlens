package example;

import org.apache.wicket.markup.html.basic.Label;

/** Open either companion HTML file to select the default or French layout. */
public class HeaderDetailPage extends HeaderBasePage {
    public HeaderDetailPage() {
        add(new Label("theme", "body { color: black; }"));
        add(new Label("title", "Header contributions and markup variants"));
    }
}
