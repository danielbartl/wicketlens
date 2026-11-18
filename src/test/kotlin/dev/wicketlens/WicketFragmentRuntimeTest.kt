package dev.wicketlens

import junit.framework.TestCase
import java.io.File
import java.nio.file.Files
import java.util.concurrent.TimeUnit

/** An independent WicketTester oracle for the fragment scope used by the PSI fixtures. */
class WicketFragmentRuntimeTest : TestCase() {
    fun testInheritedDefinitionCanServeTwoInstancesWithIndependentChildren() {
        val directory = Files.createTempDirectory("wicketlens-fragment-runtime").toFile()
        try {
            val example = File(directory, "example").apply { mkdirs() }
            File(example, "Base.java").writeText("""
                package example;
                public class Base extends org.apache.wicket.markup.html.WebPage { }
            """.trimIndent())
            File(example, "ExamplePage.java").writeText("""
                package example;
                import org.apache.wicket.markup.html.panel.Fragment;
                import org.apache.wicket.markup.html.basic.Label;
                public class ExamplePage extends Base {
                    public ExamplePage() {
                        Fragment a = new Fragment("first", "shared", this); a.add(new Label("title", "One")); add(a);
                        Fragment b = new Fragment("second", "shared", this); b.add(new Label("title", "Two")); add(b);
                    }
                    public static void main(String[] args) {
                        org.apache.wicket.util.tester.WicketTester tester = new org.apache.wicket.util.tester.WicketTester();
                        try {
                            tester.startPage(new ExamplePage());
                            tester.assertLabel("first:title", "One"); tester.assertLabel("second:title", "Two");
                            if (tester.getLastRenderedPage().get("shared") != null) throw new AssertionError("Definition is not a child");
                            if (tester.getLastResponseAsString().contains("preview")) throw new AssertionError("Preview was rendered");
                        } finally { tester.destroy(); }
                    }
                }
            """.trimIndent())
            File(example, "Base.html").writeText("""<html xmlns:wicket="http://wicket.apache.org"><body><wicket:child/><wicket:fragment wicket:id="shared"><b wicket:id="title"></b></wicket:fragment></body></html>""")
            File(example, "ExamplePage.html").writeText("""<wicket:extend xmlns:wicket="http://wicket.apache.org"><div wicket:id="first">preview</div><div wicket:id="second">preview</div></wicket:extend>""")
            val classpath = listOf("libraries", "springTestLibraries").flatMap {
                System.getProperty("wicketlens.test.$it").split(File.pathSeparator)
            }.distinct().joinToString(File.pathSeparator)
            fun run(command: List<String>, name: String) {
                val log = File(directory, name)
                val process = ProcessBuilder(command).directory(directory).redirectErrorStream(true).redirectOutput(log).start()
                if (!process.waitFor(45, TimeUnit.SECONDS)) { process.destroyForcibly(); fail("Runtime fragment check timed out: ${log.readText()}") }
                assertEquals(log.readText(), 0, process.exitValue())
            }
            val compiler = System.getProperty("wicketlens.test.javac")
            run(listOf(compiler, "--release", "21", "-cp", classpath, "-d", directory.path) +
                example.listFiles()!!.filter { it.extension == "java" }.map { it.path }, "compile.log")
            run(listOf(File(File(compiler).parentFile, "java").path, "-cp", directory.path + File.pathSeparator + classpath, "example.ExamplePage"), "runtime.log")
        } finally { directory.deleteRecursively() }
    }
}
