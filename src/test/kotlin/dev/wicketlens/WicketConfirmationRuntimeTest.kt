package dev.wicketlens

import junit.framework.TestCase
import java.io.File
import java.nio.file.Files
import java.util.concurrent.TimeUnit

/** A fixture oracle only: static contexts do not predict which registration is current. */
class WicketConfirmationRuntimeTest : TestCase() {
    fun testConstructionAskReplacementAndCleanupAreDifferentFromConsumerExecution() {
        val directory=Files.createTempDirectory("wicketlens-confirmation-runtime").toFile()
        try {
            val example=File(directory,"example").apply {mkdirs()}
            File(example,"DialogBox.java").writeText(WicketConfirmationFixture.base.replace("super(id);","super(id);setOutputMarkupId(true);"))
            File(example,"ConfirmBox.java").writeText(WicketConfirmationFixture.host)
            File(example,"Actions.java").writeText(WicketConfirmationFixture.actions)
            File(example,"ConfirmationPage.java").writeText(WicketConfirmationFixture.page
                .replace("public ConfirmationPage()", "public static int deletes;public static int replacement;public ConfirmationPage()")
                .replace("            }\n            private void delete", """
                    add(new org.apache.wicket.ajax.markup.html.AjaxLink<Void>("ask") {
                        @Override public void onClick(AjaxRequestTarget target) { actions.click(target,new Row("r")); }
                    });
                    add(new org.apache.wicket.ajax.markup.html.AjaxLink<Void>("replace") {
                        @Override public void onClick(AjaxRequestTarget target) {
                            actions.click(target,new Row("r"));
                            confirm.ask(target,"Replacement","",t->{replacement++;t.add(table);});
                        }
                    });
                }
                private void delete""")
                .replace("records.removeAll(rows);", "deletes++;records.removeAll(rows);"))
            File(example,"DialogBox.html").writeText(WicketInitialContentFixture.hostMarkup)
            File(example,"ConfirmationPage.html").writeText("<html xmlns:wicket='http://wicket.apache.org'><body>"+WicketConfirmationFixture.markup+"<a wicket:id='ask'>ask</a><a wicket:id='replace'>replace</a></body></html>")
            File(example,"Oracle.java").writeText("""
                package example;public class Oracle {
                    public static void main(String[] args) {
                        var tester=new org.apache.wicket.util.tester.WicketTester();
                        try {
                            var page=new ConfirmationPage();tester.startPage(page);
                            if(ConfirmationPage.deletes!=0) throw new AssertionError("Construction/rendering cannot execute a pending consumer");
                            tester.executeAjaxEvent("ask","click");
                            if(ConfirmationPage.deletes!=0) throw new AssertionError("Ask stores but does not execute");
                            tester.executeAjaxEvent("confirm:dialog:form:confirm","click");
                            if(ConfirmationPage.deletes!=1) throw new AssertionError("The submit hook must reach the stored consumer");
                            tester.assertComponentOnAjaxResponse("rows");
                            tester.executeAjaxEvent("confirm:dialog:form:confirm","click");
                            if(ConfirmationPage.deletes!=1) throw new AssertionError("The fixture's consumed slot was cleared");
                            tester.executeAjaxEvent("replace","click");tester.executeAjaxEvent("confirm:dialog:form:confirm","click");
                            if(ConfirmationPage.deletes!=1 || ConfirmationPage.replacement!=1) throw new AssertionError("The latest runtime registration can replace the row-delete consumer");
                            tester.executeAjaxEvent("ask","click");((ConfirmBox)page.get("confirm")).close(null);
                            tester.startPage(page);tester.executeAjaxEvent("confirm:dialog:form:confirm","click");
                            if(ConfirmationPage.deletes!=1) throw new AssertionError("The fixture cleanup forgets the pending consumer");
                        } finally {tester.destroy();}
                    }
                }
            """)
            val classpath=listOf("libraries","springTestLibraries").flatMap {System.getProperty("wicketlens.test.$it").split(File.pathSeparator)}.distinct().joinToString(File.pathSeparator)
            fun run(command:List<String>,name:String) {
                val log=File(directory,name);val process=ProcessBuilder(command).directory(directory).redirectErrorStream(true).redirectOutput(log).start()
                if(!process.waitFor(45,TimeUnit.SECONDS)) {process.destroyForcibly();fail("Confirmation oracle timed out: ${log.readText()}")}
                assertEquals(log.readText(),0,process.exitValue())
            }
            val compiler=System.getProperty("wicketlens.test.javac")
            run(listOf(compiler,"--release","21","-cp",classpath,"-d",directory.path)+listOf("DialogBox.java","ConfirmBox.java","Actions.java","ConfirmationPage.java","Oracle.java").map {File(example,it).path},"compile.log")
            run(listOf(File(File(compiler).parentFile,"java").path,"-cp",directory.path+File.pathSeparator+classpath,"example.Oracle"),"runtime.log")
        } finally {directory.deleteRecursively()}
    }
}
