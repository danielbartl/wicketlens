package dev.wicketlens

import junit.framework.TestCase
import java.io.File
import java.nio.file.Files
import java.util.concurrent.TimeUnit

/** Independent Wicket resource lookup oracle for named member binary filenames. */
class WicketNestedRuntimeTest : TestCase() {
    fun testWicketLoadsStaticAndDeepNamedMemberResources() {
        val directory = Files.createTempDirectory("wicketlens-nested-runtime").toFile()
        try {
            val example = File(directory, "example").apply { mkdirs() }
            File(example, "Outer.java").writeText("""
                package example;
                public class Outer {
                    public static final class Fields extends org.apache.wicket.markup.html.panel.Panel {
                        public Fields(String id) { super(id); add(new org.apache.wicket.markup.html.basic.Label("field", "Member")); }
                    }
                    public static class Group {
                        public static final class Deep extends org.apache.wicket.markup.html.panel.Panel {
                            public Deep(String id) { super(id); add(new org.apache.wicket.markup.html.basic.Label("field", "Deep")); }
                        }
                    }
                    public static void main(String[] args) {
                        org.apache.wicket.util.tester.WicketTester tester = new org.apache.wicket.util.tester.WicketTester();
                        try {
                            tester.startComponentInPage(new Fields("panel")); tester.assertLabel("panel:field", "Member");
                            tester.startComponentInPage(new Group.Deep("panel")); tester.assertLabel("panel:field", "Deep");
                        } finally { tester.destroy(); }
                    }
                }
            """)
            for (name in listOf("Outer\$Fields", "Outer\$Group\$Deep")) {
                File(example, "$name.html").writeText("<wicket:panel xmlns:wicket='http://wicket.apache.org'><b wicket:id='field'></b></wicket:panel>")
            }
            val classpath = listOf("libraries", "springTestLibraries").flatMap { System.getProperty("wicketlens.test.$it").split(File.pathSeparator) }.distinct().joinToString(File.pathSeparator)
            fun run(command: List<String>, name: String) {
                val log = File(directory, name)
                val process = ProcessBuilder(command).directory(directory).redirectErrorStream(true).redirectOutput(log).start()
                if (!process.waitFor(45, TimeUnit.SECONDS)) { process.destroyForcibly(); fail("Nested runtime check timed out: ${log.readText()}") }
                assertEquals(log.readText(), 0, process.exitValue())
            }
            val compiler = System.getProperty("wicketlens.test.javac")
            run(listOf(compiler, "--release", "21", "-cp", classpath, "-d", directory.path, File(example, "Outer.java").path), "compile.log")
            run(listOf(File(File(compiler).parentFile, "java").path, "-cp", directory.path + File.pathSeparator + classpath, "example.Outer"), "runtime.log")
        } finally { directory.deleteRecursively() }
    }
}
