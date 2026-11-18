package dev.wicketlens

import com.intellij.psi.PsiJavaFile
import com.intellij.testFramework.fixtures.CodeInsightTestFixture

internal object WicketDataLoopFixture {
    const val body = "for (int i=1; i<=23; i++) { rows.add(new Row(i, String.format(\"Row %d\",i))); }"
    const val members = "private final java.util.List<Row> rows=new java.util.ArrayList<>(); public record Row(long id,String name) {}"
    fun install(fixture: CodeInsightTestFixture, loop: String = body, declarations: String = members, after: String = "add(new org.apache.wicket.markup.html.basic.Label(\"title\",\"Ready\"));"): PsiJavaFile {
        fixture.addFileToProject("src/main/resources/example/ExamplePage.html", "<b wicket:id='title'></b>")
        return fixture.addFileToProject("src/main/java/example/ExamplePage.java", """
            package example; public class ExamplePage extends org.apache.wicket.markup.html.WebPage {
                $declarations
                public ExamplePage() { $loop $after }
            }
        """.trimIndent()) as PsiJavaFile
    }
}
