package dev.wicketlens

import junit.framework.TestCase
import java.io.File
import java.nio.file.Files
import java.util.concurrent.TimeUnit

class WicketOwnedAccessorRuntimeTest:TestCase() {
    fun testPureGetterDecorationDefersModelsUntilRendering() {
        val directory=Files.createTempDirectory("wicketlens-owned-accessor-runtime").toFile()
        try {
            val example=File(directory,"example").apply {mkdirs()}
            File(example,"ToolbarRuntimePage.html").writeText("<html><body><div wicket:id='popover'></div></body></html>")
            File(example,"ToolbarRuntimePage.java").writeText("""
                package example;
                import org.apache.wicket.*;
                import org.apache.wicket.markup.html.*;
                import org.apache.wicket.markup.html.basic.Label;
                import org.apache.wicket.model.IModel;
                public class ToolbarRuntimePage extends org.apache.wicket.markup.html.WebPage {
                    static int factories,modelReads,initializations;
                    public static class Popover extends org.apache.wicket.markup.html.panel.Panel {
                        private final WebMarkupContainer trigger;
                        public Popover(String id,java.util.function.Function<String,Component> content){
                            super(id);trigger=new WebMarkupContainer("trigger");
                            trigger.add(new Label("caption","Open"));
                            trigger.add(AttributeModifier.replace("data-value",(IModel<String>)this::value));
                            add(trigger);add(content.apply("content"));
                        }
                        private String value(){modelReads++;return "stored";}
                        public WebMarkupContainer getTrigger(){return trigger;}
                        protected void onInitialize(){super.onInitialize();initializations++;}
                    }
                    final Popover popover;
                    public ToolbarRuntimePage(){
                        Popover chooser=new Popover("popover",id -> {factories++;return new Label(id,"Content");});
                        chooser.getTrigger().add(AttributeModifier.replace("class","outline small"));
                        popover=chooser;add(chooser);
                    }
                    public static void main(String[] args){
                        var tester=new org.apache.wicket.util.tester.WicketTester();
                        try {
                            var page=new ToolbarRuntimePage();
                            if(factories!=1 || modelReads!=0 || initializations!=0)throw new AssertionError("Decoration evaluated a model or initialized the owner");
                            if(page.popover.getTrigger()!=page.popover.trigger || page.popover.trigger.getParent()!=page.popover)throw new AssertionError("Accessor did not retain the owned child");
                            tester.startPage(page);tester.assertRenderedPage(ToolbarRuntimePage.class);
                            tester.assertContains("class=\\\"outline small\\\"");
                            tester.assertContains("data-value=\\\"stored\\\"");
                            if(modelReads==0 || initializations!=1 || factories!=1)throw new AssertionError("Later rendering did not evaluate the stored behavior model");
                        }finally {tester.destroy();}
                    }
                }
            """.trimIndent())
            File(example,"ToolbarRuntimePage\$Popover.html").writeText("<wicket:panel><button wicket:id='trigger'><span wicket:id='caption'></span></button><b wicket:id='content'></b></wicket:panel>")
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
