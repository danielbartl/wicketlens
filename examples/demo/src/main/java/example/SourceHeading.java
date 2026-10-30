package example;

import org.apache.wicket.markup.html.basic.Label;

/** The ID is the second argument; its forwarding contract is visible in source. */
public class SourceHeading extends Label {
    public SourceHeading(String text, String id) {
        super(id, text);
    }
}
