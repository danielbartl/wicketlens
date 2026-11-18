package dev.wicketlens

import junit.framework.TestCase
import java.io.File
import java.nio.file.Files
import java.util.concurrent.TimeUnit

class WicketFluentConfigurationRuntimeTest:TestCase() {
    fun testFluentConfigurationStoresEnumsAndDefersRenderingAndAjaxCallbacks() {
        val directory=Files.createTempDirectory("wicketlens-fluent-runtime").toFile()
        try {
            val example=File(directory,"example").apply {mkdirs()}
            File(example,"ToolbarRuntimePage.html").writeText("<html><body><button wicket:id='clear'>Clear</button></body></html>")
            File(example,"ToolbarRuntimePage.java").writeText("""
                package example;
                import org.apache.wicket.*;
                import org.apache.wicket.model.*;
                public class ToolbarRuntimePage extends org.apache.wicket.markup.html.WebPage {
                    static int tags,clicks,initializations;
                    public static class Appearance extends org.apache.wicket.behavior.Behavior {
                        enum Size {DEFAULT(null),SMALL("small");private final String css;Size(String css){this.css=css;}}
                        enum Style {DEFAULT(null),GHOST("ghost");private final String css;Style(String css){this.css=css;}}
                        private IModel<Size> size=Model.of(Size.DEFAULT);
                        private IModel<Style> style=Model.of(Style.DEFAULT);
                        public Appearance setSize(Size value){size.setObject(value);return this;}
                        public Appearance setStyle(Style value){style.setObject(value);return this;}
                        public void onComponentTag(Component component,org.apache.wicket.markup.ComponentTag tag){super.onComponentTag(component,tag);tags++;tag.put("data-size",size.getObject().css);tag.put("data-style",style.getObject().css);}
                    }
                    public static abstract class Button extends org.apache.wicket.ajax.markup.html.AjaxLink<Void> {
                        private final Appearance appearance;
                        public Button(String id){super(id);appearance=new Appearance();add(appearance);}
                        public Button setSize(Appearance.Size size){appearance.setSize(size);return this;}
                        public Button setStyle(Appearance.Style style){appearance.setStyle(style);return this;}
                        protected void onInitialize(){super.onInitialize();initializations++;}
                    }
                    final Button button;
                    public ToolbarRuntimePage(){
                        Button fresh=new Button("clear"){public void onClick(org.apache.wicket.ajax.AjaxRequestTarget target){clicks++;}};
                        button=fresh.setSize(Appearance.Size.SMALL).setStyle(Appearance.Style.GHOST);
                        if(button!=fresh || button.appearance.size.getObject()!=Appearance.Size.SMALL || button.appearance.style.getObject()!=Appearance.Style.GHOST)throw new AssertionError("Fluent configuration lost its receiver or private model storage");
                        add(button);
                    }
                    public static void main(String[] args){
                        var tester=new org.apache.wicket.util.tester.WicketTester();
                        try {
                            var page=new ToolbarRuntimePage();
                            if(tags+clicks+initializations!=0)throw new AssertionError("Configuration ran initialization, rendering or Ajax callbacks");
                            tester.startPage(page);tester.assertRenderedPage(ToolbarRuntimePage.class);
                            tester.assertContains("data-size=\\\"small\\\"");tester.assertContains("data-style=\\\"ghost\\\"");
                            if(tags==0 || initializations!=1 || clicks!=0)throw new AssertionError("Rendering was not deferred");
                            tester.executeAjaxEvent("clear","click");if(clicks!=1)throw new AssertionError("The Ajax callback did not remain separate");
                        }finally {tester.destroy();}
                    }
                }
            """.trimIndent())
            val classpath=listOf("libraries","springTestLibraries").flatMap {System.getProperty("wicketlens.test.$it").split(File.pathSeparator)}.distinct().joinToString(File.pathSeparator)
            val compiler=System.getProperty("wicketlens.test.javac")
            fun run(command:List<String>,name:String) {
                val log=File(directory,name);val process=ProcessBuilder(command).directory(directory).redirectErrorStream(true).redirectOutput(log).start()
                if(!process.waitFor(45,TimeUnit.SECONDS)){process.destroyForcibly();fail("DataTable oracle timed out: ${log.readText()}")}
                assertEquals(log.readText(),0,process.exitValue())
            }
            run(listOf(compiler,"--release","21","-cp",classpath,"-d",directory.path,File(example,"ToolbarRuntimePage.java").path),"compile.log")
            run(listOf(File(File(compiler).parentFile,"java").path,"-cp",directory.path+File.pathSeparator+classpath,"example.ToolbarRuntimePage"),"runtime.log")
        } finally {directory.deleteRecursively()}
    }
}
