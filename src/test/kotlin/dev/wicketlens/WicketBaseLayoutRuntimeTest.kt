package dev.wicketlens

import junit.framework.TestCase
import java.io.File
import java.nio.file.Files
import java.util.concurrent.TimeUnit

/** Independent framework oracle: three constructor routes render the same shell around concrete extension markup. */
class WicketBaseLayoutRuntimeTest : TestCase() {
    fun testConcreteExtensionsRenderTheSharedShellThroughEveryConstructorEntry() {
        val directory=Files.createTempDirectory("wicketlens-base-layout-runtime").toFile()
        try {
            val example=File(directory,"example").apply { mkdirs() }
            File(example,"Shell.java").writeText(WicketBaseLayoutFixture.layout)
            File(example,"Html.java").writeText(WicketBaseLayoutFixture.htmlClass)
            File(example,"Shell.html").writeText(WicketBaseLayoutFixture.markup)
            for((name,call) in listOf("DefaultPage" to "super()","ParametersPage" to "super(new org.apache.wicket.request.mapper.parameter.PageParameters().add(\"opaque\",\"value\"))","ModelPage" to "super(org.apache.wicket.model.Model.of(\"opaque\"))")) {
                File(example,"$name.java").writeText("package example; public class $name extends Shell { public $name() { $call; add(new org.apache.wicket.markup.html.basic.Label(\"child\",\"Content\")); } }")
                File(example,"$name.html").writeText("<wicket:extend xmlns:wicket='http://wicket.apache.org'><b wicket:id='child'></b></wicket:extend>")
            }
            File(example,"Check.java").writeText("""
                package example; public class Check {
                    public static void main(String[] args) {
                        var tester=new org.apache.wicket.util.tester.WicketTester();
                        try {
                            for(Shell page:new Shell[]{new DefaultPage(),new ParametersPage(),new ModelPage()}) {
                                tester.startPage(page);
                                tester.assertComponent("html",Html.class);
                                tester.assertComponent("title",org.apache.wicket.markup.html.basic.Label.class);
                                tester.assertComponent("nav",org.apache.wicket.markup.html.WebMarkupContainer.class);
                                tester.assertComponent("nav:name",org.apache.wicket.markup.html.basic.Label.class);
                                if(!(page.get("nav:extra") instanceof org.apache.wicket.markup.html.WebMarkupContainer) || page.get("nav:extra").isVisible()) throw new AssertionError("The declared extra slot must exist separately from its runtime visibility");
                                tester.assertComponent("child",org.apache.wicket.markup.html.basic.Label.class);
                                tester.assertContains("Content");
                            }
                        } finally { tester.destroy(); }
                    }
                }
            """)
            val classpath=listOf("libraries","springTestLibraries").flatMap { System.getProperty("wicketlens.test.$it").split(File.pathSeparator) }.distinct().joinToString(File.pathSeparator)
            val compiler=System.getProperty("wicketlens.test.javac")
            fun run(command:List<String>,name:String) {
                val log=File(directory,name);val process=ProcessBuilder(command).directory(directory).redirectErrorStream(true).redirectOutput(log).start()
                if(!process.waitFor(45,TimeUnit.SECONDS)) { process.destroyForcibly();fail("Base-layout oracle timed out: ${log.readText()}") }
                assertEquals(log.readText(),0,process.exitValue())
            }
            run(listOf(compiler,"--release","21","-cp",classpath,"-d",directory.path)+example.listFiles()!!.filter { it.extension=="java" }.map { it.path },"compile.log")
            run(listOf(File(File(compiler).parentFile,"java").path,"-cp",directory.path+File.pathSeparator+classpath,"example.Check"),"runtime.log")
        } finally { directory.deleteRecursively() }
    }
}
