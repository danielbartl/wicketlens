package dev.wicketlens

import com.intellij.psi.PsiJavaFile
import com.intellij.testFramework.fixtures.CodeInsightTestFixture

internal object WicketDataTableFixture {
    val shell="""
        package example;
        import java.util.List;
        import org.apache.wicket.extensions.markup.html.repeater.data.table.*;
        import org.apache.wicket.markup.repeater.data.*;
        import org.apache.wicket.markup.repeater.Item;
        import org.apache.wicket.model.IModel;
        public class TableShell extends org.apache.wicket.markup.html.panel.Panel {
            private final class RowsTable extends DataTable<String,String> {
                RowsTable(String id,List<? extends IColumn<String,String>> columns,IDataProvider<String> provider,long count){super(id,columns,provider,count);}
                @Override protected Item<String> newRowItem(String id,int index,IModel<String> model){return super.newRowItem(id,index,model);}
                @Override protected Item<IColumn<String,String>> newCellItem(String id,int index,IModel<IColumn<String,String>> model){return super.newCellItem(id,index,model);}
            }
            public TableShell(String id){super(id);add(new RowsTable("table",List.of(),new ListDataProvider<String>(List.of("row")),10));}
        }
    """.trimIndent()
    const val page="package example;public class TablePage extends org.apache.wicket.markup.html.WebPage {public TablePage(){add(new TableShell(\"shell\"));add(new org.apache.wicket.markup.html.basic.Label(\"title\",\"Ready\"));}}"
    const val markup="<div wicket:id='shell'></div><b wicket:id='title'></b>"
    fun install(fixture:CodeInsightTestFixture):PsiJavaFile {
        fixture.addFileToProject("src/main/java/example/TableShell.java",shell)
        fixture.addFileToProject("src/main/resources/example/TableShell.html","<wicket:panel><table wicket:id='table'></table></wicket:panel>")
        fixture.addFileToProject("src/main/resources/example/TablePage.html",markup)
        return fixture.addFileToProject("src/main/java/example/TablePage.java",page) as PsiJavaFile
    }
}
