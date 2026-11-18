package dev.wicketlens

import junit.framework.TestCase
import java.io.File
import java.nio.file.Files
import java.util.concurrent.TimeUnit

/** Independent framework oracle: real data seeding runs, while mutated IDs differ after a loop. */
class WicketDataLoopRuntimeTest : TestCase() {
    fun testRealFrameworkRendersSeededDataAndTheActualPostLoopId() {
        val directory=Files.createTempDirectory("wicketlens-data-loop-runtime").toFile()
        try {
            val example=File(directory,"example").apply { mkdirs() }
            File(example,"SeedPage.java").writeText("package example; public class SeedPage extends org.apache.wicket.markup.html.WebPage { ${WicketDataLoopFixture.members} public SeedPage() { ${WicketDataLoopFixture.body} add(new org.apache.wicket.markup.html.basic.Label(\"title\",rows.size())); } }")
            File(example,"SeedPage.html").writeText("<html><body><b wicket:id='title'></b></body></html>")
            File(example,"MutatedPage.java").writeText("package example;public class MutatedPage extends org.apache.wicket.markup.html.WebPage { public MutatedPage() { String id=\"title\";for(int i=0;i<2;i++){id=\"other\";}add(new org.apache.wicket.markup.html.basic.Label(id,\"Changed\")); } }")
            File(example,"MutatedPage.html").writeText("<html><body><b wicket:id='other'></b></body></html>")
            File(example,"InitializationData.java").writeText("""
                package example;
                interface InitializationHook {
                    int VALUE=initialize();
                    static int initialize(){System.setProperty("wicketlens.loop.interface","called");return 1;}
                    default int value(){return VALUE;}
                }
                record InitializedData(int id) implements InitializationHook {}
                record FinalizedData(int id) {
                    @Override protected void finalize(){System.setProperty("wicketlens.loop.finalizer","called");}
                }
                record TextData(int id) {
                    public String toString(){System.setProperty("wicketlens.loop.text","called");return "data";}
                }
                record AccessorData(int id) {
                    public int id(){System.setProperty("wicketlens.loop.accessor","called");return id;}
                }
                enum InitializedEnum {
                    VALUE;
                    final int data=initialize();
                    static int initialize(){System.setProperty("wicketlens.loop.enum","called");return 1;}
                }
            """.trimIndent())
            File(example,"Check.java").writeText("""
                package example;public class Check {
                    public static void main(String[] args) {
                        new InitializedData(1);
                        if(!"called".equals(System.getProperty("wicketlens.loop.interface"))) throw new AssertionError("A record initializes its default-method interface");
                        if(InitializedEnum.VALUE.data!=1 || !"called".equals(System.getProperty("wicketlens.loop.enum"))) throw new AssertionError("An implicit enum constructor runs its field initializer");
                        String text="Value "+new TextData(1);text+=new TextData(2);
                        if(!"Value datadata".equals(text) || !"called".equals(System.getProperty("wicketlens.loop.text"))) throw new AssertionError("Concatenation invokes custom toString");
                        if((Object)new AccessorData(1) instanceof AccessorData(int id)) {
                            if(id!=1 || !"called".equals(System.getProperty("wicketlens.loop.accessor"))) throw new AssertionError("Record patterns invoke custom accessors");
                        } else throw new AssertionError("The record pattern must match");
                        var tester=new org.apache.wicket.util.tester.WicketTester();
                        try {
                            tester.startPage(new SeedPage());tester.assertLabel("title","23");
                            tester.startPage(new MutatedPage());tester.assertLabel("other","Changed");
                            if(tester.getLastRenderedPage().get("title")!=null) throw new AssertionError("A pre-loop ID must not survive a runtime write");
                        } finally { tester.destroy(); }
                    }
                }
            """.trimIndent())
            val classpath=listOf("libraries","springTestLibraries").flatMap { System.getProperty("wicketlens.test.$it").split(File.pathSeparator) }.distinct().joinToString(File.pathSeparator)
            val compiler=System.getProperty("wicketlens.test.javac")
            fun run(command:List<String>,name:String) {
                val log=File(directory,name);val process=ProcessBuilder(command).directory(directory).redirectErrorStream(true).redirectOutput(log).start()
                if(!process.waitFor(45,TimeUnit.SECONDS)) {process.destroyForcibly();fail("Data-loop oracle timed out: ${log.readText()}")}
                assertEquals(log.readText(),0,process.exitValue())
            }
            run(listOf(compiler,"--release","21","-cp",classpath,"-d",directory.path)+example.listFiles()!!.filter { it.extension=="java" }.map { it.path },"compile.log")
            run(listOf(File(File(compiler).parentFile,"java").path,"-cp",directory.path+File.pathSeparator+classpath,"example.Check"),"runtime.log")
        } finally {directory.deleteRecursively()}
    }
}
