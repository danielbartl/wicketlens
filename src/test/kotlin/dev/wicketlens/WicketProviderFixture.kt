package dev.wicketlens

import com.intellij.psi.PsiJavaFile
import com.intellij.testFramework.fixtures.CodeInsightTestFixture

internal object WicketProviderFixture {
    val page="""
        package example;
        import org.apache.wicket.model.*;
        import org.apache.wicket.extensions.markup.html.repeater.util.SortableDataProvider;
        public class ProviderPage extends org.apache.wicket.markup.html.WebPage {
            private java.util.List<String> filtered(){return java.util.List.of("row");}
            private class Rows extends SortableDataProvider<String,String> {
                @Override public java.util.Iterator<? extends String> iterator(long first,long count){return filtered().iterator();}
                @Override public long size(){return filtered().size();}
                @Override public IModel<String> model(String row){return LoadableDetachableModel.of(()->filtered().get(0));}
                @Override public void detach(){super.detach();}
            }
            public ProviderPage(){Rows rows=new Rows();add(new org.apache.wicket.markup.html.basic.Label("title","Ready"));}
        }
    """.trimIndent()
    const val markup="<b wicket:id='title'></b>"
    fun install(fixture:CodeInsightTestFixture):PsiJavaFile {
        fixture.addFileToProject("src/main/resources/example/ProviderPage.html",markup)
        return fixture.addFileToProject("src/main/java/example/ProviderPage.java",page) as PsiJavaFile
    }
}
