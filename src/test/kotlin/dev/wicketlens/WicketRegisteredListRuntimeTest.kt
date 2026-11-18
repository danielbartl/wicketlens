package dev.wicketlens

import junit.framework.TestCase
import java.io.File
import java.nio.file.Files
import java.util.concurrent.TimeUnit

/** Independent Wicket oracle for the reviewed bridge and behavior-only default item wrapper. */
class WicketRegisteredListRuntimeTest : TestCase() {
    fun testRegisteredCallbackPopulatesDefaultItemsWithoutPredictingRowsOrBehaviorState() {
        val directory = Files.createTempDirectory("wicketlens-registered-list-runtime").toFile()
        try {
            val example = File(directory, "example").apply { mkdirs() }
            // Wicket serializes the page: use a serializable SAM for this runtime fixture.
            val list = WicketRegisteredListFixture.list
                .replace("java.util.function.BiConsumer<ListItem<T>,T>", "Populator<ListItem<T>,T>")
                .replace("private final Decoration behavior;", """
                    public interface Populator<A,B> extends java.util.function.BiConsumer<A,B>, java.io.Serializable {}
                    private final Decoration behavior;
                """)
            File(example, "DecoratedList.java").writeText(list)
            File(example, "Decoration.java").writeText(WicketRegisteredListFixture.behavior)
            File(example, "OraclePage.java").writeText("""
                package example;
                import org.apache.wicket.markup.html.basic.Label;
                import org.apache.wicket.markup.html.list.*;
                import org.apache.wicket.model.Model;
                public class OraclePage extends org.apache.wicket.markup.html.WebPage {
                    record Row(String title,String body) implements java.io.Serializable {}
                    final java.util.List<Row> rows=new java.util.ArrayList<>();
                    public OraclePage() {
                        rows.add(new Row("First","One")); rows.add(new Row("Second","Two"));
                        add(DecoratedList.of("sections",Model.ofList(rows),(item,data) -> {
                            item.add(new Label("title",data.title()));item.add(new Label("body",data.body()));
                        }));
                    }
                    public static void main(String[] args) {
                        var tester=new org.apache.wicket.util.tester.WicketTester();
                        try {
                            var page=new OraclePage();
                            if(page.get("sections").get("0")!=null) throw new AssertionError("Factory registration does not populate runtime rows");
                            tester.startPage(page);
                            tester.assertLabel("sections:0:title","First");tester.assertLabel("sections:1:body","Two");
                            var view=(ListView<?>)page.get("sections");
                            if(view.size()!=2) throw new AssertionError("Runtime model determines the row count");
                            var first=(ListItem<?>)view.get("0");var second=(ListItem<?>)view.get("1");
                            if(first.getIndex()!=0 || second.getIndex()!=1 || first.getModelObject()!=page.rows.get(0)) throw new AssertionError("The wrapper preserves default item index and model identity");
                            var behavior=view.getBehaviors(Decoration.class).get(0);
                            if(first.getBehaviors(Decoration.class).get(0)!=behavior || second.getBehaviors(Decoration.class).get(0)!=behavior) throw new AssertionError("The same owned behavior decorates default items");
                            if(!tester.getLastResponseAsString().contains("data-group=\"one\"")) throw new AssertionError("Behavior runs during rendering, separately from source population");
                            page.rows.clear();tester.startPage(page);
                            if(view.size()!=0 || tester.getLastResponseAsString().contains("First")) throw new AssertionError("Later runtime models can remove all rows");
                        } finally { tester.destroy(); }
                    }
                }
            """)
            File(example, "OraclePage.html").writeText("<html xmlns:wicket='http://wicket.apache.org'><body><details wicket:id='sections'><summary wicket:id='title'></summary><div wicket:id='body'></div></details></body></html>")
            val classpath = listOf("libraries", "springTestLibraries")
                .flatMap { System.getProperty("wicketlens.test.$it").split(File.pathSeparator) }
                .distinct().joinToString(File.pathSeparator)
            fun run(command: List<String>, name: String) {
                val log = File(directory, name)
                val process = ProcessBuilder(command).directory(directory).redirectErrorStream(true).redirectOutput(log).start()
                if (!process.waitFor(45, TimeUnit.SECONDS)) {
                    process.destroyForcibly()
                    fail("Registered list runtime check timed out: ${log.readText()}")
                }
                assertEquals(log.readText(), 0, process.exitValue())
            }
            val compiler = System.getProperty("wicketlens.test.javac")
            run(listOf(compiler, "--release", "21", "-cp", classpath, "-d", directory.path) +
                listOf("DecoratedList.java", "Decoration.java", "OraclePage.java").map { File(example, it).path }, "compile.log")
            run(listOf(File(File(compiler).parentFile, "java").path, "-cp", directory.path + File.pathSeparator + classpath, "example.OraclePage"), "runtime.log")
        } finally { directory.deleteRecursively() }
    }
}
