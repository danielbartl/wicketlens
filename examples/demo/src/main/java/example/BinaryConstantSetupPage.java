package example;

import javax.xml.XMLConstants;
import org.apache.wicket.markup.html.WebPage;
import org.apache.wicket.markup.html.basic.Label;

/** Library constants are read from class metadata; their classes are not initialized by analysis. */
public class BinaryConstantSetupPage extends WebPage {
    public BinaryConstantSetupPage() {
        add(new Label(XMLConstants.XML_NS_PREFIX + "Title"));
        add(new Label("item" + Long.BYTES));
        if (Integer.MAX_VALUE > 0) {
            add(new Label("status"));
        }
    }
}
