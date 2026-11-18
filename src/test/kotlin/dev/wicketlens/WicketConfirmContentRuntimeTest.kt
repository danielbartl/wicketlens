package dev.wicketlens

import junit.framework.TestCase
import java.io.File
import java.nio.file.Files
import java.util.concurrent.TimeUnit

class WicketConfirmContentRuntimeTest : TestCase() {
    fun testRealFrameworkInstallsTheConstructorLabelAndDefersTheAction() {
        val directory=Files.createTempDirectory("wicketlens-confirm-content-runtime").toFile()
        try {
            val example=File(directory,"example").apply {mkdirs()}
            File(example,"DialogBox.java").writeText(WicketConfirmContentFixture.base)
            File(example,"ConfirmBox.java").writeText(WicketConfirmContentFixture.confirmation)
            File(example,"ConfirmPage.java").writeText(WicketConfirmContentFixture.page)
            File(example,"DialogBox.html").writeText(WicketInitialContentFixture.hostMarkup)
            File(example,"ConfirmPage.html").writeText("<html><body>"+WicketConfirmContentFixture.markup+"</body></html>")
            File(example,"Oracle.java").writeText("""
                package example;public class Oracle {
                    public static void main(String[] args) {
                        var tester=new org.apache.wicket.util.tester.WicketTester();
                        try {
                            var page=new ConfirmPage();var box=(ConfirmBox)page.get("confirmBox");
                            var message=box.get("dialog:form:content");
                            if(!(message instanceof org.apache.wicket.markup.html.basic.Label) || message.getParent()!=box.get("dialog:form")) throw new AssertionError("The constructor replaces the inherited placeholder");
                            int[] actions={0};box.ask("Delete invoice?",target->actions[0]++);
                            tester.startPage(page);tester.assertLabel("confirmBox:dialog:form:content","Delete invoice?");
                            if(actions[0]!=0) throw new AssertionError("Construction and rendering do not confirm");
                            tester.executeAjaxEvent("confirmBox:dialog:form:confirm","click");
                            if(actions[0]!=1) throw new AssertionError("Confirmation executes the registered action");
                            tester.executeAjaxEvent("confirmBox:dialog:form:confirm","click");
                            if(actions[0]!=1) throw new AssertionError("The action is consumed once");
                        } finally {tester.destroy();}
                    }
                }
            """.trimIndent())
            val classpath=listOf("libraries","springTestLibraries").flatMap {System.getProperty("wicketlens.test.$it").split(File.pathSeparator)}.distinct().joinToString(File.pathSeparator)
            val compiler=System.getProperty("wicketlens.test.javac")
            fun run(command:List<String>,name:String) {
                val log=File(directory,name);val process=ProcessBuilder(command).directory(directory).redirectErrorStream(true).redirectOutput(log).start()
                if(!process.waitFor(45,TimeUnit.SECONDS)){process.destroyForcibly();fail("Confirmation oracle timed out: ${log.readText()}")}
                assertEquals(log.readText(),0,process.exitValue())
            }
            run(listOf(compiler,"--release","21","-cp",classpath,"-d",directory.path)+example.listFiles()!!.filter {it.extension=="java"}.map {it.path},"compile.log")
            run(listOf(File(File(compiler).parentFile,"java").path,"-cp",directory.path+File.pathSeparator+classpath,"example.Oracle"),"runtime.log")
        } finally {directory.deleteRecursively()}
    }
}
