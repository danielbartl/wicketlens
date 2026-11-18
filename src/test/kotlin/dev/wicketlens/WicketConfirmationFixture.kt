package dev.wicketlens

import com.intellij.psi.PsiJavaFile
import com.intellij.testFramework.fixtures.CodeInsightTestFixture

internal object WicketConfirmationFixture {
    val base = WicketConfirmContentFixture.base.replace("protected void onConfirm(AjaxRequestTarget target) {}", """
        protected void onConfirm(AjaxRequestTarget target) {}
        protected void onClose(AjaxRequestTarget target) {}
        public DialogBox close(AjaxRequestTarget target) { onClose(target);return this; }
        public DialogBox setHeader(org.apache.wicket.model.IModel<String> value) { header.setDefaultModel(value);return this; }
    """)
    val host = WicketConfirmContentFixture.confirmation.substringBefore("    public ConfirmBox ask(") + """
        public ConfirmBox ask(AjaxRequestTarget target,String title,String text,org.danekja.java.util.function.serializable.SerializableConsumer<AjaxRequestTarget> pending) {
            return ask(target,Model.of(title),Model.of(text),pending);
        }
        public ConfirmBox ask(AjaxRequestTarget target,org.apache.wicket.model.IModel<String> title,org.apache.wicket.model.IModel<String> text,org.danekja.java.util.function.serializable.SerializableConsumer<AjaxRequestTarget> pending) {
            this.action=pending;setHeader(title);message.setDefaultModel(text);open(target);return this;
        }
        @Override protected void onConfirm(AjaxRequestTarget target) {
            var pending=action;action=null;if(pending!=null) { pending.accept(target); }
        }
        @Override protected void onClose(AjaxRequestTarget target) { action=null;super.onClose(target); }
    }
    """.trimIndent()
    val actions = """
        package example;
        import org.apache.wicket.ajax.AjaxRequestTarget;
        import org.danekja.java.util.function.serializable.SerializableBiConsumer;
        public class Actions<T> {
            private SerializableBiConsumer<AjaxRequestTarget,T> action;
            public Actions() {}
            public Actions<T> addAction(String title,SerializableBiConsumer<AjaxRequestTarget,T> handler) { action=handler;return this; }
            public void click(AjaxRequestTarget target,T row) { action.accept(target,row); }
        }
    """
    val page = """
        package example;
        import java.util.*;
        import org.apache.wicket.ajax.AjaxRequestTarget;
        import org.apache.wicket.markup.html.*;
        public class ConfirmationPage extends WebPage {
            public record Row(String id) implements java.io.Serializable {}
            private final List<Row> records=new ArrayList<>();
            public ConfirmationPage() {
                ConfirmBox confirm=new ConfirmBox("confirm");add(confirm);
                WebMarkupContainer table=new WebMarkupContainer("rows");table.setOutputMarkupId(true);add(table);
                Actions<Row> actions=new Actions<>();
                actions.addAction("Delete",(target,row)->confirm.ask(target,"Delete?","Cannot undo",t->{delete(List.of(row));success("Deleted");t.add(table);}));
            }
            private void delete(List<Row> rows) { records.removeAll(rows); }
        }
    """
    const val markup="<div wicket:id='confirm'></div><div wicket:id='rows'></div>"
    fun install(fixture:CodeInsightTestFixture,body:String=page,hostSource:String=host,baseSource:String=base):PsiJavaFile {
        fixture.addFileToProject("src/main/java/example/DialogBox.java",baseSource)
        fixture.addFileToProject("src/main/java/example/ConfirmBox.java",hostSource)
        fixture.addFileToProject("src/main/java/example/Actions.java",actions)
        fixture.addFileToProject("src/main/resources/example/DialogBox.html",WicketInitialContentFixture.hostMarkup)
        fixture.addFileToProject("src/main/resources/example/ConfirmationPage.html",markup)
        return fixture.addFileToProject("src/main/java/example/ConfirmationPage.java",body) as PsiJavaFile
    }
}
