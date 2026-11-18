package dev.wicketlens

import junit.framework.TestCase
import java.io.File
import java.nio.file.Files
import java.util.concurrent.TimeUnit

/** Independent Wicket oracle: structure, converted input, validation and model updates are separate. */
class WicketFormInputRuntimeTest : TestCase() {
    fun testLazyCompositeInputsRequiredValidationConversionAndModelUpdate() {
        val directory=Files.createTempDirectory("wicketlens-form-input-runtime").toFile()
        try {
            val example=File(directory,"example").apply { mkdirs() }
            File(example,"FieldBase.java").writeText(WicketFormInputFixture.base)
            File(example,"Field.java").writeText(WicketFormInputFixture.field)
            File(example,"Field.html").writeText(WicketFormInputFixture.markup.replace("<wicket:panel>","<wicket:panel xmlns:wicket='http://wicket.apache.org'>"))
            File(example,"Field\$Input.html").writeText(WicketFormInputFixture.inputMarkup.replace("<wicket:panel>","<wicket:panel xmlns:wicket='http://wicket.apache.org'>"))
            File(example,"ExamplePage.java").writeText("""
                package example;
                import org.apache.wicket.*;
                import org.apache.wicket.markup.html.*;
                import org.apache.wicket.markup.html.form.*;
                import org.apache.wicket.model.*;
                public class ExamplePage extends WebPage {
                    final IModel<String> value=Model.of("unchanged");
                    final Field range=new Field("range",value);
                    final Field.Input early=range.getInput();
                    int submitted;
                    public ExamplePage() {
                        Form<Void> form=new Form<>("form");add(form);form.add(range);
                        form.add(new Button("save") { @Override public void onSubmit() { submitted++; } });
                        early.setRequired(true);
                    }
                    static void require(boolean condition,String message) { if(!condition) throw new AssertionError(message); }
                    public static void main(String[] args) {
                        org.apache.wicket.util.tester.WicketTester tester=new org.apache.wicket.util.tester.WicketTester();
                        try {
                            ExamplePage page=new ExamplePage();tester.startPage(page);
                            require(page.early==page.range.getInput() && page.get("form:range:input")==page.early,"Lazy access and initialization must reuse one input");
                            tester.assertComponent("form:range:input:begin",TextField.class);
                            tester.assertComponent("form:range:input:end",TextField.class);
                            tester.newFormTester("form").submit("save");
                            require(page.early.hasErrorMessage() && page.value.getObject().equals("unchanged") && page.submitted==0,"Required failure must not update model or submit");
                            var valid=tester.newFormTester("form");valid.setValue("range:input:begin","alpha");valid.setValue("range:input:end","beta");valid.submit("save");
                            require(page.value.getObject().equals("alpha:beta") && page.submitted==1,"Valid conversion updates one composite model");
                            var invalid=tester.newFormTester("form");invalid.setValue("range:input:begin","bad");invalid.setValue("range:input:end","value");invalid.submit("save");
                            require(page.early.hasErrorMessage() && page.value.getObject().equals("alpha:beta") && page.submitted==1,"Validator failure must not update the composite model");
                            require(page.range.getInput()==page.early,"Later processing must retain cached identity");
                        } finally { tester.destroy(); }
                    }
                }
            """)
            File(example,"ExamplePage.html").writeText("<html xmlns:wicket='http://wicket.apache.org'><body><form wicket:id='form'><div wicket:id='range'></div><button wicket:id='save'>Save</button></form></body></html>")
            val classpath=listOf("libraries","springTestLibraries").flatMap { System.getProperty("wicketlens.test.$it").split(File.pathSeparator) }.distinct().joinToString(File.pathSeparator)
            val compiler=System.getProperty("wicketlens.test.javac")
            fun run(command:List<String>,name:String) {
                val log=File(directory,name);val process=ProcessBuilder(command).directory(directory).redirectErrorStream(true).redirectOutput(log).start()
                if(!process.waitFor(45,TimeUnit.SECONDS)) { process.destroyForcibly();fail("Form-input runtime check timed out: ${log.readText()}") }
                assertEquals(log.readText(),0,process.exitValue())
            }
            run(listOf(compiler,"--release","21","-cp",classpath,"-d",directory.path)+example.listFiles()!!.filter { it.extension=="java" }.map { it.path },"compile.log")
            run(listOf(File(File(compiler).parentFile,"java").path,"-cp",directory.path+File.pathSeparator+classpath,"example.ExamplePage"),"runtime.log")
        } finally { directory.deleteRecursively() }
    }
}
