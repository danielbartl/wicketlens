package example;

import org.apache.wicket.Page;
import org.apache.wicket.protocol.http.WebApplication;
import org.apache.wicket.spring.injection.annot.SpringComponentInjector;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

public class SpringDemoApplication extends WebApplication {
    @Override
    protected void init() {
        super.init();
        getComponentInstantiationListeners().add(new SpringComponentInjector(
            this, new AnnotationConfigApplicationContext(DemoSpringConfig.class)));
    }

    @Override
    public Class<? extends Page> getHomePage() {
        return SpringDemoPage.class;
    }
}
