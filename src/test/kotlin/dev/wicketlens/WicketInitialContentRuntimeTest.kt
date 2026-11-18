package dev.wicketlens

import junit.framework.TestCase
import java.io.File
import java.nio.file.Files
import java.util.concurrent.TimeUnit

/** Independent framework oracle: initial content, later replacement and confirm are different phases. */
class WicketInitialContentRuntimeTest : TestCase() {
    fun testInitialFragmentsAndLaterReplacementUseTheOwnedFormSlot() {
        val directory=Files.createTempDirectory("wicketlens-initial-content-runtime").toFile()
        try {
            val example=File(directory,"example").apply { mkdirs() }
            File(example,"DialogBox.java").writeText(WicketInitialContentFixture.host)
            File(example,"DrawerBox.java").writeText(WicketInitialContentFixture.drawer)
            File(example,"ContentPage.java").writeText(WicketInitialContentFixture.page.replace("public ContentPage()", "public static int confirms; public ContentPage()").replace("success(\"Saved\");", "confirms++;success(\"Saved\");"))
            File(example,"DialogBox.html").writeText(WicketInitialContentFixture.hostMarkup)
            File(example,"ContentPage.html").writeText("<html xmlns:wicket='http://wicket.apache.org'><body>"+WicketInitialContentFixture.markup+"</body></html>")
            File(example,"Oracle.java").writeText("""
                package example;public class Oracle {
                    public static void main(String[] args) {
                        var tester=new org.apache.wicket.util.tester.WicketTester();
                        try {
                            var page=new ContentPage();
                            var dialog=(DialogBox)page.get("dialogBox");
                            var first=dialog.get("dialog:form:content");
                            if(ContentPage.confirms!=0 || first.getParent()!=dialog.get("dialog:form")) throw new AssertionError("Construction installs content without confirming");
                            tester.startPage(page);
                            tester.assertLabel("dialogBox:dialog:form:content","Initial");
                            tester.assertLabel("drawer:dialog:form:content:name","Ada");
                            tester.assertLabel("drawer:dialog:form:content:email","ada@example.test");
                            if(ContentPage.confirms!=0) throw new AssertionError("Rendering is not confirmation");
                            tester.executeAjaxEvent("dialogBox:dialog:form:confirm","click");
                            if(ContentPage.confirms!=1) throw new AssertionError("The callback executes only after the Ajax submit");
                            var replacement=new org.apache.wicket.markup.html.basic.Label(DialogBox.CONTENT_ID,"Later");
                            dialog.setContent(replacement);
                            if(first.getParent()!=null || replacement.getParent()!=dialog.get("dialog:form")) throw new AssertionError("Later replacement detaches previous content");
                            tester.startPage(page);tester.assertLabel("dialogBox:dialog:form:content","Later");
                            try { dialog.setContent(new org.apache.wicket.markup.html.basic.Label("wrong"));throw new AssertionError("Wrong content ID accepted"); } catch(IllegalArgumentException expected) {}
                        } finally { tester.destroy(); }
                    }
                }
            """)
            val classpath=listOf("libraries","springTestLibraries").flatMap { System.getProperty("wicketlens.test.$it").split(File.pathSeparator) }.distinct().joinToString(File.pathSeparator)
            fun run(command:List<String>,name:String) {
                val log=File(directory,name);val process=ProcessBuilder(command).directory(directory).redirectErrorStream(true).redirectOutput(log).start()
                if(!process.waitFor(45,TimeUnit.SECONDS)) { process.destroyForcibly();fail("Initial content runtime check timed out: ${log.readText()}") }
                assertEquals(log.readText(),0,process.exitValue())
            }
            val compiler=System.getProperty("wicketlens.test.javac")
            run(listOf(compiler,"--release","21","-cp",classpath,"-d",directory.path)+listOf("DialogBox.java","DrawerBox.java","ContentPage.java","Oracle.java").map { File(example,it).path },"compile.log")
            run(listOf(File(File(compiler).parentFile,"java").path,"-cp",directory.path+File.pathSeparator+classpath,"example.Oracle"),"runtime.log")
        } finally { directory.deleteRecursively() }
    }
}
