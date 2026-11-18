package dev.wicketlens

import com.intellij.psi.PsiJavaFile
import com.intellij.testFramework.fixtures.CodeInsightTestFixture

internal object WicketDateColumnFixture {
    val column="""
        package example;
        import org.apache.wicket.extensions.markup.html.repeater.data.table.LambdaColumn;
        import org.apache.wicket.model.IModel;
        import java.time.LocalDate;
        import java.time.format.FormatStyle;
        public class DateColumn extends LambdaColumn<LocalDate,String> {
            private FormatStyle dateStyle=FormatStyle.MEDIUM;
            private FormatStyle timeStyle=FormatStyle.SHORT;
            public DateColumn(IModel<String> model){super(model,"date",value->value);}
        }
    """.trimIndent()
    const val page="package example;public class DatePage extends org.apache.wicket.markup.html.WebPage {public DatePage(){java.util.List<DateColumn> columns=new java.util.ArrayList<>();columns.add(new DateColumn(org.apache.wicket.model.Model.of(\"Date\")));add(new org.apache.wicket.markup.html.basic.Label(\"title\",\"Ready\"));}}"
    const val markup="<b wicket:id='title'></b>"
    fun install(fixture:CodeInsightTestFixture):PsiJavaFile {
        fixture.addFileToProject("src/main/java/example/DateColumn.java",column)
        fixture.addFileToProject("src/main/resources/example/DatePage.html",markup)
        return fixture.addFileToProject("src/main/java/example/DatePage.java",page) as PsiJavaFile
    }
}
