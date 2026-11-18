package dev.wicketlens

import com.intellij.psi.PsiJavaFile
import com.intellij.testFramework.fixtures.CodeInsightTestFixture

internal object WicketLambdaColumnFixture {
    const val function="package example;public interface RowFunction<T,R> extends java.util.function.Function<T,R>,java.io.Serializable {}"
    val column="""
        package example;
        import org.apache.wicket.*;
        import org.apache.wicket.extensions.markup.html.repeater.data.table.LambdaColumn;
        import org.apache.wicket.extensions.markup.html.repeater.data.grid.ICellPopulator;
        import org.apache.wicket.markup.repeater.Item;
        import org.apache.wicket.model.*;
        import org.apache.wicket.markup.html.basic.Label;
        public class BadgeColumn extends LambdaColumn<String,String> {
            private final RowFunction<String,String> style;
            public BadgeColumn(IModel<String> model,RowFunction<String,String> value,RowFunction<String,String> style){this(model,null,value,style);}
            public BadgeColumn(IModel<String> model,String sort,RowFunction<String,String> value,RowFunction<String,String> style){
                super(model,sort,value::apply);this.style=style;
            }
            @Override public void populateItem(Item<ICellPopulator<String>> item,String id,IModel<String> row){item.add(new Label(id,getDataModel(row)));}
        }
    """.trimIndent()
    const val page="package example;public class LambdaPage extends org.apache.wicket.markup.html.WebPage {public LambdaPage(){java.util.List<BadgeColumn> columns=new java.util.ArrayList<>();columns.add(new BadgeColumn(org.apache.wicket.model.Model.of(\"Header\"),\"name\",value->value,value->\"accent\"));add(new org.apache.wicket.markup.html.basic.Label(\"title\",\"Ready\"));}}"
    const val markup="<b wicket:id='title'></b>"
    fun install(fixture:CodeInsightTestFixture):PsiJavaFile {
        fixture.addFileToProject("src/main/java/example/RowFunction.java",function)
        fixture.addFileToProject("src/main/java/example/BadgeColumn.java",column)
        fixture.addFileToProject("src/main/resources/example/LambdaPage.html",markup)
        return fixture.addFileToProject("src/main/java/example/LambdaPage.java",page) as PsiJavaFile
    }
}
