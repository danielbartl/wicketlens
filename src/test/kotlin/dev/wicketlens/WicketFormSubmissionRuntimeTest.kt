package dev.wicketlens

import junit.framework.TestCase
import java.io.File
import java.nio.file.Files
import java.util.concurrent.TimeUnit

/** Independent framework route oracle; counters are instrumentation, not static analyzer input. */
class WicketFormSubmissionRuntimeTest : TestCase() {
    fun testDefaultValidationSelectsErrorOrSubmitAndBothDeclareTheFormRefresh() {
        val directory=Files.createTempDirectory("wicketlens-form-submission-runtime").toFile()
        try {
            val example=File(directory,"example").apply { mkdirs() }
            File(example,"SubmitButton.java").writeText(WicketFormSubmissionFixture.button)
            File(example,"FormPage.java").writeText(WicketFormSubmissionFixture.page
                .replace("public FormPage()", "public static int submits,errors;public FormPage()")
                .replace("form.add(new SubmitButton", "form.add(new org.apache.wicket.markup.html.form.TextField<String>(\"name\",org.apache.wicket.model.Model.of(\"\")).setRequired(true));form.add(new SubmitButton")
                .replace("success(\"Saved\");", "submits++;success(\"Saved\");")
                .replace("error(\"Please fix the form\");", "errors++;error(\"Please fix the form\");"))
            File(example,"FormPage.html").writeText(WicketFormSubmissionFixture.markup.replace("<button", "<input type='text' wicket:id='name'/><button"))
            File(example,"Oracle.java").writeText("""
                package example;public class Oracle {
                    public static void main(String[] args) {
                        var tester=new org.apache.wicket.util.tester.WicketTester();
                        try {
                            var page=new FormPage();tester.startPage(page);
                            if(FormPage.submits!=0 || FormPage.errors!=0) throw new AssertionError("Rendering must not execute either callback");
                            tester.executeAjaxEvent("invoiceForm:submit","click");
                            if(FormPage.submits!=0 || FormPage.errors!=1) throw new AssertionError("Failed default validation must dispatch onError only");
                            tester.assertComponentOnAjaxResponse("invoiceForm");
                            tester.newFormTester("invoiceForm").setValue("name","Ada");
                            tester.executeAjaxEvent("invoiceForm:submit","click");
                            if(FormPage.submits!=1 || FormPage.errors!=1) throw new AssertionError("Successful default validation must dispatch onSubmit only");
                            tester.assertComponentOnAjaxResponse("invoiceForm");
                            var bypass=new FormPage();
                            ((SubmitButton)bypass.get("invoiceForm:submit")).setDefaultFormProcessing(false);
                            tester.startPage(bypass);tester.executeAjaxEvent("invoiceForm:submit","click");
                            if(FormPage.submits!=2 || FormPage.errors!=1) throw new AssertionError("Disabled default processing may submit without validating required fields");
                            tester.startPage(bypass);
                            if(FormPage.submits!=2 || FormPage.errors!=1) throw new AssertionError("A later render must not repeat callback execution");
                        } finally { tester.destroy(); }
                    }
                }
            """)
            val classpath=listOf("libraries","springTestLibraries").flatMap { System.getProperty("wicketlens.test.$it").split(File.pathSeparator) }.distinct().joinToString(File.pathSeparator)
            fun run(command:List<String>,name:String) {
                val log=File(directory,name);val process=ProcessBuilder(command).directory(directory).redirectErrorStream(true).redirectOutput(log).start()
                if(!process.waitFor(45,TimeUnit.SECONDS)) { process.destroyForcibly();fail("Form submission runtime check timed out: ${log.readText()}") }
                assertEquals(log.readText(),0,process.exitValue())
            }
            val compiler=System.getProperty("wicketlens.test.javac")
            run(listOf(compiler,"--release","21","-cp",classpath,"-d",directory.path)+listOf("SubmitButton.java","FormPage.java","Oracle.java").map { File(example,it).path },"compile.log")
            run(listOf(File(File(compiler).parentFile,"java").path,"-cp",directory.path+File.pathSeparator+classpath,"example.Oracle"),"runtime.log")
        } finally { directory.deleteRecursively() }
    }
}
