package dev.wicketlens

import junit.framework.TestCase
import java.io.File
import java.nio.file.Files
import java.util.concurrent.TimeUnit

/** Independent framework oracle: installation is immediate, Ajax and later replacements occur afterwards. */
class WicketContentFactoryRuntimeTest : TestCase() {
    fun testImmediateToolbarInstallationAjaxCallbackAndLaterReplacement() {
        val directory = Files.createTempDirectory("wicketlens-content-factory-runtime").toFile()
        try {
            val example = File(directory, "example").apply { mkdirs() }
            File(example, "FactoryPage.java").writeText("""
                package example;
                import org.apache.wicket.*;
                import org.apache.wicket.markup.html.*;
                import org.apache.wicket.markup.html.panel.*;
                import org.apache.wicket.markup.html.form.*;
                import org.apache.wicket.model.*;
                public class FactoryPage extends WebPage {
                    interface Maker extends java.util.function.Function<String,Component>, java.io.Serializable {}
                    private final TableHost table; private int updates;
                    private final IModel<String> query=Model.of("");
                    public FactoryPage() {
                        table=new TableHost("table"); add(table);
                        table.setToolbar(id -> {
                            Fragment toolbar=new Fragment(id,"toolbarFragment",this);
                            Form<Void> form=new Form<>("searchForm"); toolbar.add(form);
                            form.add(new TextField<>("search",query).add(new org.apache.wicket.ajax.form.AjaxFormComponentUpdatingBehavior("input") {
                                @Override protected void onUpdate(org.apache.wicket.ajax.AjaxRequestTarget target) { updates++; }
                            }));
                            return toolbar;
                        });
                    }
                    public static class TableHost extends Panel {
                        private static final String TOOLBAR="toolbar";
                        public TableHost(String id) { super(id); add(new WebMarkupContainer(TOOLBAR).setVisible(false)); }
                        public TableHost setToolbar(Maker factory) {
                            Component content=factory.apply(TOOLBAR);
                            if (content==null || !TOOLBAR.equals(content.getId())) { throw new IllegalArgumentException(); }
                            replace(content); return this;
                        }
                    }
                    public static void main(String[] args) {
                        var tester=new org.apache.wicket.util.tester.WicketTester();
                        try {
                            FactoryPage page=new FactoryPage();
                            String root="table:toolbar", input=root+":searchForm:search";
                            Component first=page.get(root);
                            if (!(first instanceof Fragment) || page.get(input)==null || page.updates!=0 || !page.query.getObject().isEmpty()) throw new AssertionError("Factory installs nested structure immediately, callback stays deferred");
                            tester.startPage(page); tester.assertComponent(input,TextField.class);
                            var field=(FormComponent<?>)page.get(input);
                            tester.getRequest().getPostParameters().setParameterValue(field.getInputName(),"Invoice");
                            tester.executeAjaxEvent(field,"input");
                            if(page.updates!=1 || !"Invoice".equals(page.query.getObject())) throw new AssertionError("Ajax callback/model update occur only on event: updates="+page.updates+", query="+page.query.getObject());
                            page.table.setToolbar(id -> new WebMarkupContainer(id).setVisible(false));
                            if (page.get(root)==first || first.getParent()!=null || page.get(input)!=null) throw new AssertionError("Later registration replaces former contents");
                            try { page.table.setToolbar(id -> new WebMarkupContainer("wrong")); throw new AssertionError("Wrong ID accepted"); }
                            catch (IllegalArgumentException expected) {}
                        } finally { tester.destroy(); }
                    }
                }
            """)
            File(example, "FactoryPage.html").writeText("<html xmlns:wicket='http://wicket.apache.org'><body><div wicket:id='table'></div><wicket:fragment wicket:id='toolbarFragment'><form wicket:id='searchForm'><input wicket:id='search'/></form></wicket:fragment></body></html>")
            File(example, "FactoryPage\$TableHost.html").writeText("<wicket:panel xmlns:wicket='http://wicket.apache.org'><div wicket:id='toolbar'></div></wicket:panel>")
            val classpath = listOf("libraries", "springTestLibraries").flatMap { System.getProperty("wicketlens.test.$it").split(File.pathSeparator) }.distinct().joinToString(File.pathSeparator)
            fun run(command: List<String>, name: String) {
                val log = File(directory, name)
                val process = ProcessBuilder(command).directory(directory).redirectErrorStream(true).redirectOutput(log).start()
                if (!process.waitFor(45, TimeUnit.SECONDS)) { process.destroyForcibly(); fail("Content factory runtime check timed out: ${log.readText()}") }
                assertEquals(log.readText(), 0, process.exitValue())
            }
            val compiler = System.getProperty("wicketlens.test.javac")
            run(listOf(compiler, "--release", "21", "-cp", classpath, "-d", directory.path, File(example,"FactoryPage.java").path), "compile.log")
            run(listOf(File(File(compiler).parentFile,"java").path, "-cp", directory.path+File.pathSeparator+classpath, "example.FactoryPage"), "runtime.log")
        } finally { directory.deleteRecursively() }
    }
}
