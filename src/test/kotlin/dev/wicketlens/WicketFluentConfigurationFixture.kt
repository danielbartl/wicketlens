package dev.wicketlens

import com.intellij.psi.PsiJavaFile
import com.intellij.testFramework.fixtures.CodeInsightTestFixture

internal object WicketFluentConfigurationFixture {
    val button="""
        package example;
        public abstract class Button extends org.apache.wicket.ajax.markup.html.AjaxLink<Void> {
            private final Appearance appearance;
            public Button(String id){super(id);appearance=new Appearance();add(appearance);}
            public Button setSize(Appearance.Size size){appearance.setSize(size);return this;}
            public Button setStyle(Appearance.Style style){appearance.setStyle(style);return this;}
        }
    """.trimIndent()
    val appearance="""
        package example;
        import org.apache.wicket.model.*;
        public class Appearance extends org.apache.wicket.behavior.Behavior {
            public enum Size {DEFAULT(null),SMALL("small");private final String css;Size(String css){this.css=css;}public String css(){return css;}}
            public enum Style {DEFAULT(null),GHOST("ghost");private final String css;Style(String css){this.css=css;}public String css(){return css;}}
            private IModel<Size> sizeModel=Model.of(Size.DEFAULT);
            private IModel<Style> styleModel=Model.of(Style.DEFAULT);
            public Appearance(){}
            public Appearance setSize(Size size){sizeModel.setObject(size);return this;}
            public Appearance setStyle(Style style){styleModel.setObject(style);return this;}
            public void onComponentTag(org.apache.wicket.Component component,org.apache.wicket.markup.ComponentTag tag){super.onComponentTag(component,tag);tag.put("data-size",sizeModel.getObject().css());tag.put("data-style",styleModel.getObject().css());}
        }
    """.trimIndent()
    val shell="""
        package example;
        public class TableShell extends org.apache.wicket.markup.html.panel.Panel {
            public TableShell(String id){super(id);add(new Button("clear"){
                public void onClick(org.apache.wicket.ajax.AjaxRequestTarget target){TableShell.this.removeAll();}
            }.setSize(Appearance.Size.SMALL).setStyle(Appearance.Style.GHOST));}
        }
    """.trimIndent()
    fun install(fixture:CodeInsightTestFixture):PsiJavaFile {
        fixture.addFileToProject("src/main/java/example/Button.java",button)
        fixture.addFileToProject("src/main/java/example/Appearance.java",appearance)
        fixture.addFileToProject("src/main/java/example/TableShell.java",shell)
        fixture.addFileToProject("src/main/resources/example/TableShell.html","<wicket:panel><a wicket:id='clear'>Clear</a></wicket:panel>")
        fixture.addFileToProject("src/main/resources/example/TablePage.html",WicketDataTableFixture.markup)
        return fixture.addFileToProject("src/main/java/example/TablePage.java",WicketDataTableFixture.page) as PsiJavaFile
    }
}
