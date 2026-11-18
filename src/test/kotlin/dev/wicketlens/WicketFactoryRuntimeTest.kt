package dev.wicketlens

import junit.framework.TestCase
import java.io.File
import java.nio.file.Files
import java.util.concurrent.TimeUnit

/** Independent framework oracle: observed runtime IDs are never guessed by the static analyzer. */
class WicketFactoryRuntimeTest : TestCase() {
    fun testCapturedPanesAndNestedGeneratedItemsUseTheirOwnMarkupScopes() {
        val directory = Files.createTempDirectory("wicketlens-factory-runtime").toFile()
        try {
            val example = File(directory, "example").apply { mkdirs() }
            File(example, "Split.java").writeText("""
                package example;
                public class Split extends org.apache.wicket.markup.html.panel.Panel {
                    public Split(String id, java.util.function.Function<String,org.apache.wicket.Component> first,
                        java.util.function.Function<String,org.apache.wicket.Component> second) {
                        super(id);
                        org.apache.wicket.markup.html.WebMarkupContainer pane = new org.apache.wicket.markup.html.WebMarkupContainer("firstPane");
                        pane.add(first.apply("first")); add(pane); add(second.apply("second"));
                    }
                }
            """)
            File(example, "Rows.java").writeText("""
                package example;
                public class Rows extends org.apache.wicket.markup.html.panel.Panel {
                    public Rows(String id, java.util.function.BiFunction<String,Object,org.apache.wicket.Component> factory) {
                        super(id);
                        org.apache.wicket.markup.repeater.RepeatingView batches = new org.apache.wicket.markup.repeater.RepeatingView("batch");
                        add(batches); batches.add(new Batch(batches.newChildId(), factory));
                    }
                    private class Batch extends org.apache.wicket.markup.html.WebMarkupContainer {
                        Batch(String id, java.util.function.BiFunction<String,Object,org.apache.wicket.Component> factory) {
                            super(id);
                            org.apache.wicket.markup.repeater.RepeatingView items = new org.apache.wicket.markup.repeater.RepeatingView("item"); add(items);
                            java.util.Iterator<?> rows = java.util.List.of("One", "Two").iterator();
                            while(rows.hasNext()) items.add(factory.apply(items.newChildId(), rows.next()));
                        }
                    }
                }
            """)
            File(example, "ExamplePage.java").writeText("""
                package example;
                import org.apache.wicket.markup.html.panel.Fragment;
                import org.apache.wicket.markup.html.basic.Label;
                public class ExamplePage extends org.apache.wicket.markup.html.WebPage {
                    public ExamplePage() {
                        Rows first = new Rows("first", this::row);
                        Fragment second = new Fragment("second", "detail", this); second.add(new Label("subject", "Detail"));
                        add(new Split("inbox", id -> first, id -> second));
                    }
                    private Fragment row(String id, Object data) {
                        Fragment f = new Fragment(id, "row", this); f.add(new Label("subject", data.toString())); return f;
                    }
                    public static void main(String[] args) {
                        org.apache.wicket.util.tester.WicketTester tester = new org.apache.wicket.util.tester.WicketTester();
                        try {
                            tester.startPage(new ExamplePage());
                            tester.assertComponent("inbox:firstPane:first", Rows.class);
                            tester.assertLabel("inbox:second:subject", "Detail");
                            org.apache.wicket.MarkupContainer batches = (org.apache.wicket.MarkupContainer) tester.getLastRenderedPage().get("inbox:firstPane:first:batch");
                            if (batches.size() != 1) throw new AssertionError("Expected one batch in this fixture");
                            org.apache.wicket.MarkupContainer batch = (org.apache.wicket.MarkupContainer)batches.iterator().next();
                            org.apache.wicket.MarkupContainer items = (org.apache.wicket.MarkupContainer)batch.get("item");
                            if(items.size() != 2) throw new AssertionError("Expected two fixture rows");
                            java.util.List<String> actual = new java.util.ArrayList<>();
                            for(org.apache.wicket.Component item: items) actual.add(((org.apache.wicket.MarkupContainer)item).get("subject").getDefaultModelObjectAsString());
                            if(!actual.equals(java.util.List.of("One", "Two"))) throw new AssertionError(actual.toString());
                        } finally { tester.destroy(); }
                    }
                }
            """)
            File(example, "Split.html").writeText("""<wicket:panel xmlns:wicket="http://wicket.apache.org"><div wicket:id="firstPane"><div wicket:id="first"></div></div><div wicket:id="second"></div></wicket:panel>""")
            File(example, "Rows.html").writeText("""<wicket:panel xmlns:wicket="http://wicket.apache.org"><div wicket:id="batch"><div wicket:id="item"></div></div></wicket:panel>""")
            File(example, "ExamplePage.html").writeText("""<html xmlns:wicket="http://wicket.apache.org"><body><div wicket:id="inbox"></div><wicket:fragment wicket:id="detail"><h2 wicket:id="subject"></h2></wicket:fragment><wicket:fragment wicket:id="row"><strong wicket:id="subject"></strong></wicket:fragment></body></html>""")
            val classpath = listOf("libraries", "springTestLibraries").flatMap { System.getProperty("wicketlens.test.$it").split(File.pathSeparator) }.distinct().joinToString(File.pathSeparator)
            fun run(command: List<String>, name: String) {
                val log = File(directory, name)
                val process = ProcessBuilder(command).directory(directory).redirectErrorStream(true).redirectOutput(log).start()
                if (!process.waitFor(45, TimeUnit.SECONDS)) { process.destroyForcibly(); fail("Factory runtime check timed out: ${log.readText()}") }
                assertEquals(log.readText(), 0, process.exitValue())
            }
            val compiler = System.getProperty("wicketlens.test.javac")
            run(listOf(compiler, "--release", "21", "-cp", classpath, "-d", directory.path) + example.listFiles()!!.filter { it.extension == "java" }.map { it.path }, "compile.log")
            run(listOf(File(File(compiler).parentFile, "java").path, "-cp", directory.path + File.pathSeparator + classpath, "example.ExamplePage"), "runtime.log")
        } finally { directory.deleteRecursively() }
    }
}
