package dev.wicketlens

import com.intellij.psi.PsiJavaFile
import com.intellij.testFramework.fixtures.CodeInsightTestFixture

internal object WicketBaseLayoutFixture {
    const val layout = """
        package example;
        import org.apache.wicket.markup.html.*;
        import org.apache.wicket.markup.html.basic.Label;
        import org.apache.wicket.model.*;
        import org.apache.wicket.request.mapper.parameter.PageParameters;
        public abstract class Shell extends WebPage {
            private WebMarkupContainer nav;
            public Shell() { this((PageParameters)null); }
            public Shell(PageParameters parameters) { super(parameters); add(new Html("html")); }
            public Shell(IModel<?> model) { super(model); add(new Html("html")); }
            @Override protected void onInitialize() { super.onInitialize(); add(new Label("title",title())); setup(); }
            private void setup() { nav=new WebMarkupContainer("nav");add(nav);nav.add(new Label("name","App"));nav.add(extra("extra")); }
            protected IModel<String> title() { return Model.of("App"); }
            protected org.apache.wicket.Component extra(String id) { return new WebMarkupContainer(id).setVisible(false); }
        }
    """
    const val htmlClass = """
        package example; public class Html extends org.apache.wicket.markup.html.TransparentWebMarkupContainer {
            public Html(String id) { super(id); add(org.apache.wicket.AttributeModifier.replace("lang","en")); }
        }
    """
    const val markup = "<html xmlns:wicket='http://wicket.apache.org' wicket:id='html'><head><title wicket:id='title'></title></head><body><header wicket:id='nav'><b wicket:id='name'></b><div wicket:id='extra'></div></header><main><wicket:child/></main></body></html>"
    fun install(fixture: CodeInsightTestFixture): PsiJavaFile {
        fixture.addFileToProject("src/main/java/example/Html.java",htmlClass)
        fixture.addFileToProject("src/main/resources/example/Shell.html",markup)
        return fixture.addFileToProject("src/main/java/example/Shell.java",layout) as PsiJavaFile
    }
}
