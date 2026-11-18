package dev.wicketlens

import com.intellij.psi.PsiJavaFile
import com.intellij.testFramework.fixtures.CodeInsightTestFixture

internal object WicketColumnConstructionFixture {
    val column="""
        package example;
        import org.apache.wicket.*;
        import org.apache.wicket.extensions.markup.html.repeater.data.table.AbstractColumn;
        import org.apache.wicket.extensions.markup.html.repeater.data.grid.ICellPopulator;
        import org.apache.wicket.markup.repeater.Item;
        import org.apache.wicket.model.*;
        import org.apache.wicket.markup.html.basic.Label;
        public class StorageColumn extends AbstractColumn<String,String> {
            private final IModel<String> model;
            private final java.util.function.Function<String,String> key;
            public StorageColumn(IModel<String> model){this(model,row->row);}
            private StorageColumn(IModel<String> model,java.util.function.Function<String,String> key){
                super(Model.of(""));this.model=java.util.Objects.requireNonNull(model);this.key=key;
            }
            @Override public void detach(){super.detach();model.detach();}
            @Override public Component getHeader(String id){return new Label(id,model);}
            @Override public void populateItem(Item<ICellPopulator<String>> item,String id,IModel<String> row){item.add(new Label(id,key.apply(row.getObject())));}
        }
    """.trimIndent()
    const val page="package example;public class ColumnPage extends org.apache.wicket.markup.html.WebPage {public ColumnPage(){java.util.List<StorageColumn> columns=new java.util.ArrayList<>();columns.add(new StorageColumn(org.apache.wicket.model.Model.of(\"Header\")));add(new org.apache.wicket.markup.html.basic.Label(\"title\",\"Ready\"));}}"
    const val markup="<b wicket:id='title'></b>"
    fun install(fixture:CodeInsightTestFixture):PsiJavaFile {
        fixture.addFileToProject("src/main/java/example/StorageColumn.java",column)
        fixture.addFileToProject("src/main/resources/example/ColumnPage.html",markup)
        return fixture.addFileToProject("src/main/java/example/ColumnPage.java",page) as PsiJavaFile
    }
}
