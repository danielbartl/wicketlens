package example;

import org.apache.wicket.protocol.http.WebSession;
import org.apache.wicket.request.Request;
import org.apache.wicket.spring.injection.annot.SpringBean;

// Intentionally missing manual injection; sessions are outside the component listener.
public class MissingInjectionSession extends WebSession {
    @SpringBean
    private OrderService orders;

    public MissingInjectionSession(Request request) {
        super(request);
    }
}
