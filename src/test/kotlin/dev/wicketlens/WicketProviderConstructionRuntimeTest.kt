package dev.wicketlens

import junit.framework.TestCase
import java.io.File
import java.nio.file.Files
import java.util.concurrent.TimeUnit

class WicketProviderConstructionRuntimeTest : TestCase() {
    fun testRealFrameworkProviderAllocationStoresSortStateWithoutPageQueries() {
        val directory=Files.createTempDirectory("wicketlens-provider-runtime").toFile()
        try {
            val example=File(directory,"example").apply {mkdirs()}
            File(example,"ProviderPage.java").writeText(WicketProviderFixture.page.replace("filtered(){return","filtered(){Oracle.queries++;return"))
            File(example,"ProviderPage.html").writeText("<html><body>"+WicketProviderFixture.markup+"</body></html>")
            File(example,"Oracle.java").writeText("""
                package example;public class Oracle {
                    static int queries;
                    public static void main(String[] args)throws Exception {
                        var tester=new org.apache.wicket.util.tester.WicketTester();
                        try {
                            var page=new ProviderPage();
                            var type=Class.forName("example.ProviderPage"+'$'+"Rows");
                            var constructor=type.getDeclaredConstructor(ProviderPage.class);constructor.setAccessible(true);
                            @SuppressWarnings("unchecked") var provider=(org.apache.wicket.extensions.markup.html.repeater.util.SortableDataProvider<String,String>)constructor.newInstance(page);
                            if(queries!=0)throw new AssertionError("Page and inner provider construction do not query page data");
                            if(provider.getSortState()==null || provider.getSort()!=null)throw new AssertionError("The framework creates an empty sort-state object");
                            if(queries!=0)throw new AssertionError("Sort-state storage does not query rows");
                            if(provider.size()!=1 || queries!=1)throw new AssertionError("An explicit size callback queries the page");
                            if(!provider.iterator(0,1).next().equals("row") || queries!=2)throw new AssertionError("An explicit iterator callback queries the page");
                            var model=provider.model("row");
                            if(queries!=2)throw new AssertionError("Creating the detachable model remains lazy");
                            if(!"row".equals(model.getObject()) || queries!=3)throw new AssertionError("Loading the later model queries page data");
                            provider.detach();
                            if(queries!=3)throw new AssertionError("The inherited empty detach adds no query");
                            tester.startPage(page);tester.assertLabel("title","Ready");
                            if(queries!=3)throw new AssertionError("Rendering the page without a provider consumer does not query rows");
                        } finally {tester.destroy();}
                    }
                }
            """.trimIndent())
            val classpath=listOf("libraries","springTestLibraries").flatMap {System.getProperty("wicketlens.test.$it").split(File.pathSeparator)}.distinct().joinToString(File.pathSeparator)
            val compiler=System.getProperty("wicketlens.test.javac")
            fun run(command:List<String>,name:String) {
                val log=File(directory,name);val process=ProcessBuilder(command).directory(directory).redirectErrorStream(true).redirectOutput(log).start()
                if(!process.waitFor(45,TimeUnit.SECONDS)){process.destroyForcibly();fail("Provider oracle timed out: ${log.readText()}")}
                assertEquals(log.readText(),0,process.exitValue())
            }
            run(listOf(compiler,"--release","21","-cp",classpath,"-d",directory.path)+example.listFiles()!!.filter {it.extension=="java"}.map {it.path},"compile.log")
            run(listOf(File(File(compiler).parentFile,"java").path,"-cp",directory.path+File.pathSeparator+classpath,"example.Oracle"),"runtime.log")
        } finally {directory.deleteRecursively()}
    }
}
