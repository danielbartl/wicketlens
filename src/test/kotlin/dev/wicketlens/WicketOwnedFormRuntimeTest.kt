package dev.wicketlens

import junit.framework.TestCase
import java.io.File
import java.nio.file.Files
import java.util.concurrent.TimeUnit

/** Independent oracle for metadata tokens, lazy input identity, configuration and constructor factories. */
class WicketOwnedFormRuntimeTest : TestCase() {
    fun testLazyInputsAreCreatedOnceAndPrivateConstructorFactoriesStayInsideTheirFields() {
        val directory = Files.createTempDirectory("wicketlens-form-runtime").toFile()
        try {
            val example = File(directory, "example").apply { mkdirs() }
            File(example, "ExamplePage.java").writeText("""
                package example;
                public class ExamplePage extends org.apache.wicket.markup.html.WebPage {
                    public static class Field extends org.apache.wicket.markup.html.panel.Panel {
                        private static final org.apache.wicket.MetaDataKey<java.util.Set<Field>> KEY = new org.apache.wicket.MetaDataKey<>() {};
                        private org.apache.wicket.markup.html.form.TextField<String> input;
                        int created;
                        public Field(String id) { super(id); }
                        public org.apache.wicket.markup.html.form.TextField<String> getInput() {
                            if (input == null) { input = new org.apache.wicket.markup.html.form.TextField<>("input", org.apache.wicket.model.Model.of("value")); created++; add(input); }
                            return input;
                        }
                        public Field required() { getInput().setRequired(true); return this; }
                        @Override protected void onInitialize() { super.onInitialize(); getInput(); }
                    }
                    public static class Composite extends org.apache.wicket.markup.html.panel.Panel {
                        private org.apache.wicket.markup.html.form.FormComponent<String> input;
                        private final java.util.function.BiFunction<String,org.apache.wicket.model.IModel<String>,? extends org.apache.wicket.markup.html.form.FormComponent<String>> factory;
                        public Composite(String id) { this(id, Input::new); }
                        private Composite(String id, java.util.function.BiFunction<String,org.apache.wicket.model.IModel<String>,? extends org.apache.wicket.markup.html.form.FormComponent<String>> factory) { super(id); this.factory = factory; }
                        public org.apache.wicket.markup.html.form.FormComponent<String> getInput() {
                            if (input == null) { input = factory.apply("input", org.apache.wicket.model.Model.of("value")); add(input); }
                            return input;
                        }
                        @Override protected void onInitialize() { super.onInitialize(); getInput(); }
                        static class Input extends org.apache.wicket.markup.html.form.FormComponentPanel<String> {
                            Input(String id, org.apache.wicket.model.IModel<String> model) { super(id, model); }
                        }
                    }
                    final Field field = new Field("field").required();
                    final Composite composite = new Composite("composite");
                    public ExamplePage() { add(field, composite); }
                    public static void main(String[] args) {
                        org.apache.wicket.util.tester.WicketTester tester = new org.apache.wicket.util.tester.WicketTester();
                        try {
                            ExamplePage page = new ExamplePage();
                            Object before = page.field.getInput(); page.field.required();
                            tester.startPage(page);
                            tester.assertComponent("field:input", org.apache.wicket.markup.html.form.TextField.class);
                            tester.assertComponent("composite:input", Composite.Input.class);
                            if(page.field.created != 1 || before != page.field.getInput() || !page.field.getInput().isRequired()) throw new AssertionError("Lazy input identity/configuration changed");
                            if(page.composite.getInput().getParent() != page.composite) throw new AssertionError("Input escaped its field");
                        } finally { tester.destroy(); }
                    }
                }
            """)
            File(example, "ExamplePage.html").writeText("<html xmlns:wicket='http://wicket.apache.org'><body><div wicket:id='field'></div><div wicket:id='composite'></div></body></html>")
            File(example, "ExamplePage\$Field.html").writeText("<wicket:panel xmlns:wicket='http://wicket.apache.org'><input type='text' wicket:id='input'></wicket:panel>")
            File(example, "ExamplePage\$Composite.html").writeText("<wicket:panel xmlns:wicket='http://wicket.apache.org'><div wicket:id='input'></div></wicket:panel>")
            File(example, "ExamplePage\$Composite\$Input.html").writeText("<wicket:panel xmlns:wicket='http://wicket.apache.org'><span>Composite input</span></wicket:panel>")
            val classpath = listOf("libraries", "springTestLibraries").flatMap { System.getProperty("wicketlens.test.$it").split(File.pathSeparator) }.distinct().joinToString(File.pathSeparator)
            fun run(command: List<String>, name: String) {
                val log = File(directory, name)
                val process = ProcessBuilder(command).directory(directory).redirectErrorStream(true).redirectOutput(log).start()
                if (!process.waitFor(45, TimeUnit.SECONDS)) { process.destroyForcibly(); fail("Form runtime check timed out: ${log.readText()}") }
                assertEquals(log.readText(), 0, process.exitValue())
            }
            val compiler = System.getProperty("wicketlens.test.javac")
            run(listOf(compiler, "--release", "21", "-cp", classpath, "-d", directory.path, File(example, "ExamplePage.java").path), "compile.log")
            run(listOf(File(File(compiler).parentFile, "java").path, "-cp", directory.path + File.pathSeparator + classpath, "example.ExamplePage"), "runtime.log")
        } finally { directory.deleteRecursively() }
    }
}
