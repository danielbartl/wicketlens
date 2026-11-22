package dev.wicketlens

import junit.framework.TestCase
import java.io.File
import java.nio.file.Files
import java.util.concurrent.TimeUnit

class WicketCustomItemRuntimeTest : TestCase() {
    fun testTwoRowsUseTheSameCustomItemDeclaration() {
        val directory = Files.createTempDirectory("wicketlens-custom-item").toFile()
        try {
            val example = File(directory, "example").apply { mkdirs() }
            val source = File(example, "ExamplePage.java")
            source.writeText("""
                package example;
                import org.apache.wicket.markup.html.WebPage;
                import org.apache.wicket.markup.html.basic.Label;
                import org.apache.wicket.markup.html.list.*;
                import org.apache.wicket.model.IModel;

                public class ExamplePage extends WebPage {
                    static final class RowItem extends ListItem<String> {
                        RowItem(int index, IModel<String> model) { super(index, model); }
                    }
                    public ExamplePage() {
                        add(new ListView<String>("rows", java.util.List.of("First", "Second")) {
                            protected ListItem<String> newItem(int index, IModel<String> model) { return new RowItem(index, model); }
                            protected void populateItem(ListItem<String> item) { item.add(new Label("name", item.getModel())); }
                        });
                    }
                    public static void main(String[] args) {
                        var tester = new org.apache.wicket.util.tester.WicketTester();
                        try {
                            tester.startPage(new ExamplePage());
                            for (int i = 0; i < 2; i++) {
                                tester.assertComponent("rows:" + i, RowItem.class);
                                tester.assertComponent("rows:" + i + ":name", Label.class);
                            }
                        } finally { tester.destroy(); }
                    }
                }
            """)
            File(example, "ExamplePage.html").writeText("<html><body><div wicket:id='rows'><span wicket:id='name'></span></div></body></html>")
            val compiler = System.getProperty("wicketlens.test.javac")
            val java = File(File(compiler).parentFile, "java").path
            val libraries = listOf("libraries", "springTestLibraries")
                .flatMap { System.getProperty("wicketlens.test.$it").split(File.pathSeparator) }.distinct()
            val classpath = (listOf(directory.path) + libraries).joinToString(File.pathSeparator)
            fun run(command: List<String>) {
                val log = File(directory, "run.log")
                val process = ProcessBuilder(command).directory(directory).redirectErrorStream(true).redirectOutput(log).start()
                if (!process.waitFor(45, TimeUnit.SECONDS)) {
                    process.destroyForcibly()
                    fail("Custom item test timed out. " + log.readText())
                }
                assertEquals(log.readText(), 0, process.exitValue())
            }
            run(listOf(compiler, "--release", "21", "-cp", classpath, "-d", directory.path, source.path))
            run(listOf(java, "-cp", classpath, "example.ExamplePage"))
        } finally {
            directory.deleteRecursively()
        }
    }
}
