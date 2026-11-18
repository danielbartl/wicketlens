package dev.wicketlens

import com.intellij.psi.PsiJavaFile
import com.intellij.testFramework.fixtures.CodeInsightTestFixture

internal object WicketOwnedAccessorFixture {
    val popover="""
        package example;
        import org.apache.wicket.*;
        import org.apache.wicket.markup.html.*;
        import org.apache.wicket.markup.html.basic.Label;
        import org.apache.wicket.model.IModel;
        public class Popover extends org.apache.wicket.markup.html.panel.Panel {
            private final WebMarkupContainer trigger;
            public Popover(String id,java.util.function.Function<String,Component> content){
                super(id);
                trigger=new WebMarkupContainer("trigger");
                trigger.add(new Label("caption","Open"));
                add(trigger);
                add(content.apply("content"));
            }
            public WebMarkupContainer getTrigger(){return trigger;}
        }
    """.trimIndent()
    val shell="""
        package example;
        import org.apache.wicket.*;
        import org.apache.wicket.markup.html.*;
        import org.apache.wicket.markup.html.basic.Label;
        public class TableShell extends org.apache.wicket.markup.html.panel.Panel {
            public TableShell(String id){super(id);add(newTools("tools"));}
            private Component newTools(String id){
                WebMarkupContainer tools=new WebMarkupContainer(id);
                Popover chooser=new Popover("chooser",contentId -> new Label(contentId,"Content")) {
                    @Override protected void onConfigure(){super.onConfigure();setVisible(true);}
                };
                chooser.getTrigger().add(AttributeModifier.replace("class","outline small"));
                tools.add(chooser);
                return tools;
            }
        }
    """.trimIndent()
    fun install(fixture:CodeInsightTestFixture):PsiJavaFile {
        fixture.addFileToProject("src/main/java/example/Popover.java",popover)
        fixture.addFileToProject("src/main/resources/example/Popover.html","<wicket:panel><button wicket:id='trigger'><span wicket:id='caption'></span></button><b wicket:id='content'></b></wicket:panel>")
        fixture.addFileToProject("src/main/java/example/TableShell.java",shell)
        fixture.addFileToProject("src/main/resources/example/TableShell.html","<wicket:panel><div wicket:id='tools'></div></wicket:panel>")
        fixture.addFileToProject("src/main/resources/example/TablePage.html",WicketDataTableFixture.markup)
        return fixture.addFileToProject("src/main/java/example/TablePage.java",WicketDataTableFixture.page) as PsiJavaFile
    }
}
