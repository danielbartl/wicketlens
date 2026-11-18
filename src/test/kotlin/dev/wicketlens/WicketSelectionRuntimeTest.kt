package dev.wicketlens

import junit.framework.TestCase
import java.io.File
import java.nio.file.Files
import java.util.concurrent.TimeUnit

/** Independent Wicket oracle: selection and form outcomes differ from source registration order. */
class WicketSelectionRuntimeTest : TestCase() {
    fun testInitializationNextBackValidationFailureAndFinish() {
        val directory = Files.createTempDirectory("wicketlens-selection-runtime").toFile()
        try {
            val example = File(directory, "example").apply { mkdirs() }
            File(example, "ExamplePage.java").writeText("""
                package example;
                import org.apache.wicket.*;
                import org.apache.wicket.markup.html.*;
                import org.apache.wicket.markup.html.panel.*;
                import org.apache.wicket.markup.html.form.*;
                import org.apache.wicket.model.*;
                public class ExamplePage extends WebPage {
                    interface Maker extends java.util.function.Function<String,Component>, java.io.Serializable {}
                    final Flow flow; int finished;
                    public ExamplePage() {
                        flow=new Flow("flow"); add(flow);
                        flow.add("First",id -> { Fragment step=new Fragment(id,"first",this); step.add(new RequiredTextField<String>("name",Model.of(""))); return step; });
                        flow.add("Second",id -> { Fragment step=new Fragment(id,"second",this); step.add(new RequiredTextField<String>("name",Model.of(""))); return step; });
                    }
                    public class Flow extends Panel {
                        private record Entry(String title,Maker factory) implements java.io.Serializable {}
                        private final java.util.List<Entry> entries=new java.util.ArrayList<>();
                        private final Form<Void> form;
                        private int selected;
                        Flow(String id) {
                            super(id); form=new Form<>("form"); add(form); form.add(new WebMarkupContainer("body"));
                            form.add(new Button("next") { @Override public void onSubmit() { selected=1; show(); } });
                            form.add(new Button("back") { @Override public void onSubmit() { selected=0; show(); } }.setDefaultFormProcessing(false));
                            form.add(new Button("finish") { @Override public void onSubmit() { finished++; } });
                        }
                        Flow add(String title,Maker factory) { entries.add(new Entry(title,factory)); return this; }
                        private void show() { Component body=entries.get(selected).factory().apply("body"); form.addOrReplace(body); }
                        @Override protected void onInitialize() { super.onInitialize(); show(); }
                    }
                    public static void main(String[] args) {
                        org.apache.wicket.util.tester.WicketTester tester=new org.apache.wicket.util.tester.WicketTester();
                        try {
                            ExamplePage page=new ExamplePage(); tester.startPage(page);
                            String path="flow:form:body"; Component first=page.get(path);
                            if(page.flow.selected!=0 || !(first instanceof Fragment)) throw new AssertionError("First step initializes");
                            tester.newFormTester("flow:form").submit("next");
                            if(page.flow.selected!=0 || page.get(path)!=first || !page.flow.form.hasError()) throw new AssertionError("Invalid input must stay on first step");
                            var next=tester.newFormTester("flow:form"); next.setValue("body:name","Customer"); next.submit("next");
                            if(page.flow.selected!=1 || page.get(path)==first || first.getParent()!=null) throw new AssertionError("Valid next replaces the step");
                            Component second=page.get(path);
                            tester.newFormTester("flow:form").submit("back");
                            if(page.flow.selected!=0 || page.get(path)==first || page.get(path)==second || second.getParent()!=null) throw new AssertionError("Back creates a new first step without validation");
                            tester.newFormTester("flow:form").submit("finish");
                            if(page.finished!=0) throw new AssertionError("Invalid finish must not invoke callback");
                            var finish=tester.newFormTester("flow:form"); finish.setValue("body:name","Ready"); finish.submit("finish");
                            if(page.finished!=1 || page.flow.selected!=0) throw new AssertionError("Valid finish invokes callback; index is controller policy");
                        } finally { tester.destroy(); }
                    }
                }
            """)
            File(example,"ExamplePage.html").writeText("<html xmlns:wicket='http://wicket.apache.org'><body><div wicket:id='flow'></div><wicket:fragment wicket:id='first'><input wicket:id='name'/></wicket:fragment><wicket:fragment wicket:id='second'><input wicket:id='name'/></wicket:fragment></body></html>")
            File(example,"ExamplePage\$Flow.html").writeText("<wicket:panel xmlns:wicket='http://wicket.apache.org'><form wicket:id='form'><div wicket:id='body'></div><button wicket:id='next'>Next</button><button wicket:id='back'>Back</button><button wicket:id='finish'>Finish</button></form></wicket:panel>")
            val classpath=listOf("libraries","springTestLibraries").flatMap { System.getProperty("wicketlens.test.$it").split(File.pathSeparator) }.distinct().joinToString(File.pathSeparator)
            fun run(command: List<String>, name: String) {
                val log=File(directory,name)
                val process=ProcessBuilder(command).directory(directory).redirectErrorStream(true).redirectOutput(log).start()
                if(!process.waitFor(45,TimeUnit.SECONDS)) { process.destroyForcibly(); fail("Selection runtime check timed out: ${log.readText()}") }
                assertEquals(log.readText(),0,process.exitValue())
            }
            val compiler=System.getProperty("wicketlens.test.javac")
            run(listOf(compiler,"--release","21","-cp",classpath,"-d",directory.path,File(example,"ExamplePage.java").path),"compile.log")
            run(listOf(File(File(compiler).parentFile,"java").path,"-cp",directory.path+File.pathSeparator+classpath,"example.ExamplePage"),"runtime.log")
        } finally { directory.deleteRecursively() }
    }
}
