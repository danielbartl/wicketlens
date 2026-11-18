package dev.wicketlens

import com.intellij.psi.PsiJavaFile
import com.intellij.testFramework.fixtures.CodeInsightTestFixture

internal object WicketConfirmContentFixture {
    val base = WicketInitialContentFixture.host.replace("String title", "org.apache.wicket.model.IModel<String> title")
    val confirmation = """
        package example;
        import org.apache.wicket.ajax.AjaxRequestTarget;
        import org.apache.wicket.markup.html.basic.Label;
        import org.apache.wicket.model.Model;
        public class ConfirmBox extends DialogBox {
            private final Label message;
            private org.danekja.java.util.function.serializable.SerializableConsumer<AjaxRequestTarget> action;
            public ConfirmBox(String id) {
                super(id,new Model<>());
                message=new Label(CONTENT_ID,new Model<String>()) {
                    @Override protected void onConfigure() {
                        super.onConfigure();setVisible(getDefaultModelObject()!=null);
                    }
                };
                setContent(message);
                setFooterVisible(true);
            }
            public ConfirmBox ask(String text,org.danekja.java.util.function.serializable.SerializableConsumer<AjaxRequestTarget> pending) {
                message.setDefaultModel(Model.of(text));action=pending;return this;
            }
            @Override protected void onConfirm(AjaxRequestTarget target) {
                var pending=action;action=null;if(pending!=null) pending.accept(target);
            }
        }
    """.trimIndent()
    const val page = "package example;public class ConfirmPage extends org.apache.wicket.markup.html.WebPage {public ConfirmPage(){add(new ConfirmBox(\"confirmBox\"));}}"
    const val markup = "<div wicket:id='confirmBox'></div>"
    fun install(fixture:CodeInsightTestFixture):PsiJavaFile {
        fixture.addFileToProject("src/main/java/example/DialogBox.java",base)
        fixture.addFileToProject("src/main/resources/example/DialogBox.html",WicketInitialContentFixture.hostMarkup)
        fixture.addFileToProject("src/main/java/example/ConfirmBox.java",confirmation)
        fixture.addFileToProject("src/main/resources/example/ConfirmPage.html",markup)
        return fixture.addFileToProject("src/main/java/example/ConfirmPage.java",page) as PsiJavaFile
    }
}
