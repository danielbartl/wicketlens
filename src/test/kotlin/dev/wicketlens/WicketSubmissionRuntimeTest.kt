package dev.wicketlens

import junit.framework.TestCase
import java.io.File
import java.nio.file.Files
import java.util.concurrent.TimeUnit

/** Framework oracle: rendering, validation, submit dispatch and content replacement are distinct. */
class WicketSubmissionRuntimeTest : TestCase() {
    fun testValidationGatesTheDefaultSubmitRouteButCanBeDisabledAtRuntime() {
        val directory=Files.createTempDirectory("wicketlens-submission-runtime").toFile()
        try {
            val example=File(directory,"example").apply { mkdirs() }
            File(example,"DialogBox.java").writeText(WicketSubmissionFixture.host)
            File(example,"SubmitPage.java").writeText(WicketSubmissionFixture.page
                .replace("public SubmitPage()", "public static int confirms;public SubmitPage()")
                .replace("success(\"Saved\");", "confirms++;setContent(new Label(DialogBox.CONTENT_ID,\"Submitted\"));success(\"Saved\");"))
            File(example,"DialogBox.html").writeText(WicketInitialContentFixture.hostMarkup)
            File(example,"SubmitPage.html").writeText("<html xmlns:wicket='http://wicket.apache.org'><body>"+WicketSubmissionFixture.markup+
                "<wicket:fragment wicket:id='validation'><input type='text' wicket:id='name'/></wicket:fragment></body></html>")
            File(example,"Oracle.java").writeText("""
                package example;public class Oracle {
                    static org.apache.wicket.markup.html.panel.Fragment validation(SubmitPage page) {
                        var fragment=new org.apache.wicket.markup.html.panel.Fragment(DialogBox.CONTENT_ID,"validation",page);
                        var name=new org.apache.wicket.markup.html.form.TextField<String>("name",org.apache.wicket.model.Model.of(""));
                        name.setRequired(true);fragment.add(name);
                        ((DialogBox)page.get("save")).setContent(fragment);return fragment;
                    }
                    public static void main(String[] args) {
                        var tester=new org.apache.wicket.util.tester.WicketTester();
                        try {
                            var page=new SubmitPage();var first=validation(page);
                            tester.startPage(page);
                            if(SubmitPage.confirms!=0) throw new AssertionError("Construction/rendering must not submit");
                            tester.executeAjaxEvent("save:dialog:form:confirm","click");
                            if(SubmitPage.confirms!=0 || first.getParent()==null) throw new AssertionError("Required-field validation must prevent default submit dispatch");
                            tester.newFormTester("save:dialog:form").setValue("content:name","Ada");
                            tester.executeAjaxEvent("save:dialog:form:confirm","click");
                            if(SubmitPage.confirms!=1 || first.getParent()!=null) throw new AssertionError("Successful submit installs fresh content and detaches the old fragment");
                            tester.assertLabel("save:dialog:form:content","Submitted");
                            tester.startPage(page);
                            if(SubmitPage.confirms!=1) throw new AssertionError("A later render must not repeat submission");
                            var bypass=new SubmitPage();validation(bypass);
                            ((org.apache.wicket.ajax.markup.html.form.AjaxButton)bypass.get("save:dialog:form:confirm")).setDefaultFormProcessing(false);
                            tester.startPage(bypass);tester.executeAjaxEvent("save:dialog:form:confirm","click");
                            if(SubmitPage.confirms!=2) throw new AssertionError("Disabled default processing can dispatch without successful required-field validation");
                        } finally { tester.destroy(); }
                    }
                }
            """)
            val classpath=listOf("libraries","springTestLibraries").flatMap { System.getProperty("wicketlens.test.$it").split(File.pathSeparator) }.distinct().joinToString(File.pathSeparator)
            fun run(command:List<String>,name:String) {
                val log=File(directory,name);val process=ProcessBuilder(command).directory(directory).redirectErrorStream(true).redirectOutput(log).start()
                if(!process.waitFor(45,TimeUnit.SECONDS)) { process.destroyForcibly();fail("Submission runtime check timed out: ${log.readText()}") }
                assertEquals(log.readText(),0,process.exitValue())
            }
            val compiler=System.getProperty("wicketlens.test.javac")
            run(listOf(compiler,"--release","21","-cp",classpath,"-d",directory.path)+listOf("DialogBox.java","SubmitPage.java","Oracle.java").map { File(example,it).path },"compile.log")
            run(listOf(File(File(compiler).parentFile,"java").path,"-cp",directory.path+File.pathSeparator+classpath,"example.Oracle"),"runtime.log")
        } finally { directory.deleteRecursively() }
    }
}
