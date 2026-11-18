package dev.wicketlens

import junit.framework.TestCase
import java.io.File
import java.nio.file.Files
import java.util.concurrent.TimeUnit

/** Independent framework oracle: a row event replaces the placeholder only when clicked. */
class WicketEventContentRuntimeTest : TestCase() {
    fun testClickCreatesTheFragmentAndALaterClickDetachesIt() {
        val directory=Files.createTempDirectory("wicketlens-event-content-runtime").toFile()
        try {
            val example=File(directory,"example").apply { mkdirs() }
            File(example,"DialogBox.java").writeText(WicketEventContentFixture.host)
            File(example,"DrawerBox.java").writeText(WicketInitialContentFixture.drawer)
            File(example,"EventPage.java").writeText(WicketEventContentFixture.page)
            File(example,"DetailList.java").writeText(WicketEventContentFixture.list)
            File(example,"DetailList.html").writeText("<wicket:panel>Details</wicket:panel>")
            File(example,"DialogBox.html").writeText(WicketInitialContentFixture.hostMarkup)
            File(example,"EventPage.html").writeText("<html xmlns:wicket='http://wicket.apache.org'><body>"+WicketEventContentFixture.markup+"</body></html>")
            File(example,"Oracle.java").writeText("""
                package example;public class Oracle {
                    public static void main(String[] args) {
                        var tester=new org.apache.wicket.util.tester.WicketTester();
                        try {
                            var page=new EventPage();
                            var drawer=(DrawerBox)page.get("detailsDrawer");drawer.setOutputMarkupId(true);
                            var initial=drawer.get("dialog:form:content");
                            if(!(initial instanceof org.apache.wicket.markup.html.WebMarkupContainer) || initial instanceof org.apache.wicket.markup.html.panel.Fragment) throw new AssertionError("Initial placeholder");
                            tester.startPage(page);
                            tester.clickLink("rows:0:details",true);
                            var first=drawer.get("dialog:form:content");
                            if(!(first instanceof org.apache.wicket.markup.html.panel.Fragment) || first.getParent()!=drawer.get("dialog:form") || initial.getParent()!=null) throw new AssertionError("Click installs fresh fragment");
                            if(!(first.get("list") instanceof DetailList)) throw new AssertionError("Fragment owns the declared list");
                            tester.assertContains("Details");
                            tester.clickLink("rows:0:details",true);
                            var next=drawer.get("dialog:form:content");
                            if(next==first || first.getParent()!=null || next.getParent()!=drawer.get("dialog:form")) throw new AssertionError("Each click replaces and detaches the previous fragment");
                        } finally { tester.destroy(); }
                    }
                }
            """)
            val classpath=listOf("libraries","springTestLibraries").flatMap { System.getProperty("wicketlens.test.$it").split(File.pathSeparator) }.distinct().joinToString(File.pathSeparator)
            fun run(command:List<String>,name:String) {
                val log=File(directory,name);val process=ProcessBuilder(command).directory(directory).redirectErrorStream(true).redirectOutput(log).start()
                if(!process.waitFor(45,TimeUnit.SECONDS)) { process.destroyForcibly();fail("Event content runtime check timed out: ${log.readText()}") }
                assertEquals(log.readText(),0,process.exitValue())
            }
            val compiler=System.getProperty("wicketlens.test.javac")
            run(listOf(compiler,"--release","21","-cp",classpath,"-d",directory.path)+listOf("DialogBox.java","DrawerBox.java","EventPage.java","DetailList.java","Oracle.java").map { File(example,it).path },"compile.log")
            run(listOf(File(File(compiler).parentFile,"java").path,"-cp",directory.path+File.pathSeparator+classpath,"example.Oracle"),"runtime.log")
        } finally { directory.deleteRecursively() }
    }
}
