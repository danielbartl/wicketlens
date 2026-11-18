package dev.wicketlens

import com.intellij.psi.PsiJavaFile
import com.intellij.testFramework.fixtures.CodeInsightTestFixture

internal object WicketInitialContentFixture {
    val host = """
        package example;
        import org.apache.wicket.*;
        import org.apache.wicket.ajax.*;
        import org.apache.wicket.ajax.markup.html.form.AjaxButton;
        import org.apache.wicket.markup.html.*;
        import org.apache.wicket.markup.html.form.*;
        import org.apache.wicket.markup.html.basic.Label;
        import org.apache.wicket.markup.html.panel.Panel;
        public class DialogBox extends Panel {
            public static final String CONTENT_ID="content";
            private final WebMarkupContainer dialog;
            private final Form<Void> form;
            private final Label header;
            private final AjaxButton confirm;
            private boolean open;
            public DialogBox(String id,String title) {
                super(id);
                dialog=new WebMarkupContainer("dialog");add(dialog);
                form=new Form<Void>("form");dialog.add(form);
                header=new Label("header",title);form.add(header);
                form.add(new WebMarkupContainer(CONTENT_ID));
                confirm=new AjaxButton("confirm") {
                    @Override protected void onSubmit(AjaxRequestTarget target) { onConfirm(target); }
                };
                form.add(confirm);form.setDefaultButton(confirm);
            }
            protected void onConfirm(AjaxRequestTarget target) {}
            protected final WebMarkupContainer element() { return dialog; }
            public DialogBox setContent(Component content) {
                if(!CONTENT_ID.equals(content.getId())) { throw new IllegalArgumentException("Wrong content ID: "+content.getId()); }
                form.addOrReplace(content);open=false;return this;
            }
            public DialogBox setFooterVisible(boolean visible) { confirm.setVisible(visible);return this; }
            public DialogBox open(AjaxRequestTarget target) { open=true;target.add(this);return this; }
        }
    """
    val drawer = """
        package example;import org.apache.wicket.*;
        public class DrawerBox extends DialogBox {
            public DrawerBox(String id,String title) { super(id,title);element().add(AttributeModifier.append("class","drawer"));setFooterVisible(false); }
            @Override public DrawerBox setContent(Component content) { super.setContent(content);return this; }
        }
    """
    val page = """
        package example;
        import org.apache.wicket.ajax.*;
        import org.apache.wicket.markup.html.*;
        import org.apache.wicket.markup.html.basic.Label;
        import org.apache.wicket.markup.html.panel.Fragment;
        public class ContentPage extends WebPage {
            public ContentPage() {
                add(new Label("heading","Dialogs"));
                add(new DialogBox("dialogBox","Confirm") {
                    @Override protected void onConfirm(AjaxRequestTarget target) { success("Saved"); }
                }.setContent(new Label(DialogBox.CONTENT_ID,"Initial")));
                DrawerBox drawer=new DrawerBox("drawer","Edit");
                Fragment fields=new Fragment(DialogBox.CONTENT_ID,"fields",this);
                fields.add(new Label("name","Ada"));fields.add(new Label("email","ada@example.test"));
                drawer.setContent(fields).setFooterVisible(false);add(drawer);
            }
        }
    """
    const val hostMarkup = "<wicket:panel><div wicket:id='dialog'><form wicket:id='form'><h2 wicket:id='header'></h2><div wicket:id='content'></div><button wicket:id='confirm'></button></form></div></wicket:panel>"
    const val markup = "<h1 wicket:id='heading'></h1><div wicket:id='dialogBox'></div><div wicket:id='drawer'></div><wicket:fragment wicket:id='fields'><b wicket:id='name'></b><small wicket:id='email'></small></wicket:fragment>"
    fun install(fixture: CodeInsightTestFixture): PsiJavaFile {
        fixture.addFileToProject("src/main/java/example/DialogBox.java", host)
        fixture.addFileToProject("src/main/java/example/DrawerBox.java", drawer)
        fixture.addFileToProject("src/main/resources/example/DialogBox.html", hostMarkup)
        fixture.addFileToProject("src/main/resources/example/ContentPage.html", markup)
        return fixture.addFileToProject("src/main/java/example/ContentPage.java", page) as PsiJavaFile
    }
}
