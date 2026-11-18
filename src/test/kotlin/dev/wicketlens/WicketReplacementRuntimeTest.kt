package dev.wicketlens

import junit.framework.TestCase
import java.io.File
import java.nio.file.Files
import java.util.concurrent.TimeUnit

/** Independent Wicket oracle: initial placeholder, successive edits and removal are different states. */
class WicketReplacementRuntimeTest : TestCase() {
    fun testConstructorReferenceContentUsesTheDialogFormScopeAndIsReplacedOnEachEdit() {
        val directory = Files.createTempDirectory("wicketlens-replacement-runtime").toFile()
        try {
            val example = File(directory, "example").apply { mkdirs() }
            File(example, "ExamplePage.java").writeText("""
                package example;
                import org.apache.wicket.*;
                import org.apache.wicket.markup.html.*;
                import org.apache.wicket.markup.html.panel.Panel;
                import org.apache.wicket.markup.html.form.Form;
                import org.apache.wicket.markup.html.basic.Label;
                import org.apache.wicket.model.*;
                public class ExamplePage extends WebPage {
                    interface Maker extends java.util.function.BiFunction<String,IModel<String>,Component>, java.io.Serializable {}
                    public static class Fields extends Panel {
                        public Fields(String id, IModel<String> model) { super(id, model); add(new Label("name", model)); }
                    }
                    public static class Dialog extends Panel {
                        static final String CONTENT="content";
                        private final WebMarkupContainer window;
                        private final Form<Void> form;
                        Dialog(String id) { super(id); window=new WebMarkupContainer("window"); form=new Form<>("form"); window.add(form); add(window); form.add(new WebMarkupContainer(CONTENT)); }
                        void open(Component content) { form.addOrReplace(content); }
                        void close() { form.replace(new WebMarkupContainer(CONTENT)); }
                    }
                    public static class Host extends Panel {
                        private final Dialog editor;
                        private Maker factory;
                        Host(String id) { super(id); editor=new Dialog("editor"); add(editor); }
                        Host setFields(Maker factory) { this.factory=factory; return this; }
                        void edit(IModel<String> model) { Component fields=factory.apply(Dialog.CONTENT, model); editor.open(fields); }
                    }
                    final Host host;
                    public ExamplePage() { host=new Host("host").setFields(Fields::new); add(host); }
                    public static void main(String[] args) {
                        org.apache.wicket.util.tester.WicketTester tester=new org.apache.wicket.util.tester.WicketTester();
                        try {
                            ExamplePage page=new ExamplePage(); tester.startPage(page);
                            String path="host:editor:window:form:content";
                            if(page.get(path) instanceof Fields) throw new AssertionError("Editor must not exist initially");
                            page.host.edit(Model.of("first")); tester.startPage(page);
                            tester.assertComponent(path, Fields.class); tester.assertLabel(path+":name", "first");
                            Component first=page.get(path);
                            page.host.edit(Model.of("second")); tester.startPage(page);
                            tester.assertLabel(path+":name", "second");
                            if(page.get(path)==first || first.getParent()!=null) throw new AssertionError("An edit must replace the prior fields");
                            page.host.editor.close(); tester.startPage(page);
                            if(page.get(path) instanceof Fields || ((MarkupContainer)page.get(path)).get("name")!=null) throw new AssertionError("Close must remove fields");
                        } finally { tester.destroy(); }
                    }
                }
            """)
            File(example,"ExamplePage.html").writeText("<html xmlns:wicket='http://wicket.apache.org'><body><div wicket:id='host'></div></body></html>")
            File(example,"ExamplePage\$Host.html").writeText("<wicket:panel xmlns:wicket='http://wicket.apache.org'><div wicket:id='editor'></div></wicket:panel>")
            File(example,"ExamplePage\$Dialog.html").writeText("<wicket:panel xmlns:wicket='http://wicket.apache.org'><div wicket:id='window'><form wicket:id='form'><div wicket:id='content'></div></form></div></wicket:panel>")
            File(example,"ExamplePage\$Fields.html").writeText("<wicket:panel xmlns:wicket='http://wicket.apache.org'><span wicket:id='name'></span></wicket:panel>")
            val classpath=listOf("libraries","springTestLibraries").flatMap { System.getProperty("wicketlens.test.$it").split(File.pathSeparator) }.distinct().joinToString(File.pathSeparator)
            fun run(command: List<String>, name: String) {
                val log=File(directory,name)
                val process=ProcessBuilder(command).directory(directory).redirectErrorStream(true).redirectOutput(log).start()
                if(!process.waitFor(45,TimeUnit.SECONDS)) { process.destroyForcibly(); fail("Replacement runtime check timed out: ${log.readText()}") }
                assertEquals(log.readText(),0,process.exitValue())
            }
            val compiler=System.getProperty("wicketlens.test.javac")
            run(listOf(compiler,"--release","21","-cp",classpath,"-d",directory.path,File(example,"ExamplePage.java").path),"compile.log")
            run(listOf(File(File(compiler).parentFile,"java").path,"-cp",directory.path+File.pathSeparator+classpath,"example.ExamplePage"),"runtime.log")
        } finally { directory.deleteRecursively() }
    }
}
