package dev.wicketlens

import junit.framework.TestCase
import java.io.File
import java.nio.file.Files
import java.util.concurrent.TimeUnit

/** Independent framework oracle: leaf and group rows coexist; source templates do not choose runtime rows. */
class WicketPopulationRuntimeTest : TestCase() {
    fun testMixedRowsRenderSharedHelpersAndNestedPopulation() {
        val directory=Files.createTempDirectory("wicketlens-population-runtime").toFile()
        try {
            val example=File(directory,"example").apply { mkdirs() }
            File(example,"Entry.java").writeText(WicketPopulationFixture.model)
            File(example,"MenuPage.java").writeText(WicketPopulationFixture.page.replace("java.util.List.<Entry>of()","java.util.List.of(new Entry(\"Leaf\",java.util.List.of()),new Entry(\"Group\",java.util.List.of(new Entry(\"Nested\",java.util.List.of()))))"))
            File(example,"MenuPage.html").writeText(WicketPopulationFixture.markup)
            File(example,"Check.java").writeText("""
                package example; public class Check {
                    public static void main(String[] args) {
                        var tester=new org.apache.wicket.util.tester.WicketTester();
                        try {
                            MenuPage page=new MenuPage();tester.startPage(page);
                            tester.assertLabel("sidebar:entries:0:link:label","Leaf");
                            tester.assertLabel("sidebar:entries:0:link:badge","1");
                            tester.assertLabel("sidebar:entries:1:group:label","Group");
                            tester.assertLabel("sidebar:entries:1:group:children:0:link:label","Nested");
                            tester.assertLabel("sidebar:entries:1:group:children:0:link:badge","1");
                            var hiddenGroup=(org.apache.wicket.MarkupContainer)page.get("sidebar:entries:0:group");
                            var hiddenLink=(org.apache.wicket.MarkupContainer)page.get("sidebar:entries:1:link");
                            if(hiddenGroup.isVisible() || hiddenLink.isVisible() || hiddenGroup.size()!=0 || hiddenLink.size()!=0) throw new AssertionError("Each source branch has an empty hidden placeholder");
                            tester.assertContains("Leaf");tester.assertContains("Group");tester.assertContains("Nested");
                        } finally { tester.destroy(); }
                    }
                }
            """)
            val classpath=listOf("libraries","springTestLibraries").flatMap { System.getProperty("wicketlens.test.$it").split(File.pathSeparator) }.distinct().joinToString(File.pathSeparator)
            val compiler=System.getProperty("wicketlens.test.javac")
            fun run(command:List<String>,name:String) {
                val log=File(directory,name);val process=ProcessBuilder(command).directory(directory).redirectErrorStream(true).redirectOutput(log).start()
                if(!process.waitFor(45,TimeUnit.SECONDS)) { process.destroyForcibly();fail("Population runtime oracle timed out: ${log.readText()}") }
                assertEquals(log.readText(),0,process.exitValue())
            }
            run(listOf(compiler,"--release","21","-cp",classpath,"-d",directory.path)+example.listFiles()!!.filter { it.extension=="java" }.map { it.path },"compile.log")
            run(listOf(File(File(compiler).parentFile,"java").path,"-cp",directory.path+File.pathSeparator+classpath,"example.Check"),"runtime.log")
        } finally { directory.deleteRecursively() }
    }
}
