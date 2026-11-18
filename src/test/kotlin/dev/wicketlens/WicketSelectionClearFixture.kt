package dev.wicketlens

import com.intellij.psi.PsiJavaFile
import com.intellij.testFramework.fixtures.CodeInsightTestFixture

internal object WicketSelectionClearFixture {
    val column="""
        package example;
        import java.util.Collection;
        import org.apache.wicket.model.IModel;
        public class SelectionBox {
            private final IModel<? extends Collection<?>> selection;
            public SelectionBox(IModel<? extends Collection<?>> selection) {this.selection=selection;}
            public IModel<? extends Collection<?>> getSelection() {return selection;}
        }
    """
    val table="""
        package example;
        import java.util.List;
        import org.apache.wicket.ajax.AjaxRequestTarget;
        import org.apache.wicket.markup.html.WebMarkupContainer;
        import org.danekja.java.util.function.serializable.SerializableBiConsumer;
        public class TableBox extends WebMarkupContainer {
            private final SelectionBox selectionColumn;
            private SerializableBiConsumer<AjaxRequestTarget,List<ConfirmationPage.Row>> action;
            public TableBox(String id) {super(id);selectionColumn=null;setOutputMarkupId(true);}
            public TableBox addBulkAction(String title,SerializableBiConsumer<AjaxRequestTarget,List<ConfirmationPage.Row>> action) {this.action=action;return this;}
            public TableBox clearSelection(AjaxRequestTarget target) {
                if(selectionColumn!=null) {selectionColumn.getSelection().getObject().clear();}
                target.add(this);return this;
            }
        }
    """
    val page=WicketConfirmationFixture.page.replace("WebMarkupContainer table=new WebMarkupContainer(\"rows\");", "TableBox table=new TableBox(\"rows\");")
        .replace("            }\n            private void delete", """
                table.addBulkAction("Bulk delete",(target,rows)->confirm.ask(target,"Delete rows?","Cannot undo",t->{delete(rows);success("Deleted rows");table.clearSelection(t);}));
            }
            private void delete""")
    fun install(fixture:CodeInsightTestFixture,body:String=page,tableSource:String=table,columnSource:String=column):PsiJavaFile {
        fixture.addFileToProject("src/main/java/example/SelectionBox.java",columnSource)
        fixture.addFileToProject("src/main/java/example/TableBox.java",tableSource)
        return WicketConfirmationFixture.install(fixture,body)
    }
}
