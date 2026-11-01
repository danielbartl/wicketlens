package example;

import org.apache.wicket.protocol.http.WebSession;
import org.apache.wicket.request.Request;

public class OrderDetailsSession extends WebSession {
    private String titlePrefix = "Orders: ";

    public OrderDetailsSession(Request request) {
        super(request);
    }

    public String titlePrefix() { return titlePrefix; }
    public void setTitlePrefix(String prefix) { titlePrefix = prefix; }
}
