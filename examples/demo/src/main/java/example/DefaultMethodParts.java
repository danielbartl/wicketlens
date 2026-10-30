package example;

import org.apache.wicket.markup.html.basic.Label;

public class DefaultMethodParts implements DefaultComponentParts {
    private final String caption;

    public DefaultMethodParts(String caption) {
        this.caption = caption;
    }

    @Override
    public Label label(String id) {
        return new Label(id, caption);
    }
}
