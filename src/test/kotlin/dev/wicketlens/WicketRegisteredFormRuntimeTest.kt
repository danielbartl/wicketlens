package dev.wicketlens

import junit.framework.TestCase
import java.io.File
import java.nio.file.Files
import java.util.concurrent.TimeUnit

/** Independent framework oracle for constructor registration, lazy identity and composite input processing. */
class WicketRegisteredFormRuntimeTest : TestCase() {
    fun testRegisteredInputUsesSuppliedIdModelAndCachedChildren() {
        val directory=Files.createTempDirectory("wicketlens-registered-form-runtime").toFile()
        try {
            val example=File(directory,"example").apply { mkdirs() }
            for((name,text) in listOf("FieldBase" to WicketFormInputFixture.base,"CustomField" to WicketRegisteredFormFixture.wrapper,"PhoneInput" to WicketRegisteredFormFixture.input)) File(example,"$name.java").writeText(text)
            File(example,"CustomField.html").writeText(WicketFormInputFixture.markup.replace("<wicket:panel>","<wicket:panel xmlns:wicket='http://wicket.apache.org'>"))
            File(example,"PhoneInput.html").writeText(WicketRegisteredFormFixture.inputMarkup.replace("<wicket:panel>","<wicket:panel xmlns:wicket='http://wicket.apache.org'>"))
            File(example,"ExamplePage.java").writeText("""
                package example;
                import org.apache.wicket.markup.html.WebPage;
                import org.apache.wicket.markup.html.form.*;
                import org.apache.wicket.model.*;
                public class ExamplePage extends WebPage {
                    final IModel<String> value=Model.of("unchanged");
                    final CustomField phone=new CustomField("phone",value,PhoneInput::new);
                    int submitted;
                    public ExamplePage() {
                        Form<Void> form=new Form<>("form");add(form);form.add(phone);
                        form.add(new Button("save") { @Override public void onSubmit() { submitted++; } });
                    }
                    static void require(boolean condition,String message) { if(!condition) throw new AssertionError(message); }
                    public static void main(String[] args) {
                        org.apache.wicket.util.tester.WicketTester tester=new org.apache.wicket.util.tester.WicketTester();
                        try {
                            ExamplePage page=new ExamplePage();tester.startPage(page);
                            FormComponent<String> input=page.phone.getInput();
                            require(input instanceof PhoneInput && input==page.phone.getInput() && input==page.get("form:phone:input"),"Registered constructor supplies one cached input with the requested ID");
                            tester.assertComponent("form:phone:input:code",DropDownChoice.class);tester.assertComponent("form:phone:input:number",TextField.class);
                            var form=tester.newFormTester("form");form.select("phone:input:code",0);form.setValue("phone:input:number","12345");form.submit("save");
                            require(page.value.getObject().equals("+43 12345") && page.submitted==1,"Composite conversion updates the supplied model");
                            boolean rejected=false;
                            try { new CustomField("bad",(id,model)->new PhoneInput("other",model)).getInput(); } catch(IllegalStateException expected) { rejected=true; }
                            require(rejected,"ID mismatch guard rejects a factory returning a different ID");
                        } finally { tester.destroy(); }
                    }
                }
            """)
            File(example,"ExamplePage.html").writeText("<html xmlns:wicket='http://wicket.apache.org'><body><form wicket:id='form'><div wicket:id='phone'></div><button wicket:id='save'>Save</button></form></body></html>")
            val classpath=listOf("libraries","springTestLibraries").flatMap { System.getProperty("wicketlens.test.$it").split(File.pathSeparator) }.distinct().joinToString(File.pathSeparator)
            val compiler=System.getProperty("wicketlens.test.javac")
            fun run(command:List<String>,name:String) {
                val log=File(directory,name);val process=ProcessBuilder(command).directory(directory).redirectErrorStream(true).redirectOutput(log).start()
                if(!process.waitFor(45,TimeUnit.SECONDS)) { process.destroyForcibly();fail("Registered-input oracle timed out: ${log.readText()}") }
                assertEquals(log.readText(),0,process.exitValue())
            }
            run(listOf(compiler,"--release","21","-cp",classpath,"-d",directory.path)+example.listFiles()!!.filter { it.extension=="java" }.map { it.path },"compile.log")
            run(listOf(File(File(compiler).parentFile,"java").path,"-cp",directory.path+File.pathSeparator+classpath,"example.ExamplePage"),"runtime.log")
        } finally { directory.deleteRecursively() }
    }
}
