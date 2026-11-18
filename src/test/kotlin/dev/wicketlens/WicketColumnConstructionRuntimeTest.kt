package dev.wicketlens

import junit.framework.TestCase
import java.io.File
import java.nio.file.Files
import java.util.concurrent.TimeUnit

class WicketColumnConstructionRuntimeTest : TestCase() {
    fun testRealFrameworkOnlyStoresInputsUntilHeaderPopulationAndDetachAreInvoked() {
        val directory=Files.createTempDirectory("wicketlens-column-runtime").toFile()
        try {
            val example=File(directory,"example").apply {mkdirs()}
            File(example,"StorageColumn.java").writeText(WicketColumnConstructionFixture.column
                .replace("super(Model.of(\"\"));","super(model,\"name\");")
                .replace("{return new Label(id,model);}","{Oracle.headers++;return new Label(id,model);}")
                .replace("{item.add(new Label(id,key.apply(row.getObject())));}","{Oracle.cells++;item.add(new Label(id,key.apply(row.getObject())));}"))
            File(example,"RenderPage.java").writeText("""
                package example;public class RenderPage extends org.apache.wicket.markup.html.WebPage {
                    public RenderPage(StorageColumn column){
                        add(column.getHeader("header"));
                        var row=new org.apache.wicket.markup.repeater.Item<org.apache.wicket.extensions.markup.html.repeater.data.grid.ICellPopulator<String>>("row",0);
                        column.populateItem(row,"cell",org.apache.wicket.model.Model.of("Rendered row"));add(row);
                    }
                }
            """.trimIndent())
            File(example,"RenderPage.html").writeText("<html><body><b wicket:id='header'></b><div wicket:id='row'><span wicket:id='cell'></span></div></body></html>")
            File(example,"Oracle.java").writeText("""
                package example;public class Oracle {
                    static int headers,cells,reads,detaches;
                    public static void main(String[] args) {
                        var tester=new org.apache.wicket.util.tester.WicketTester();
                        try {
                            var model=new org.apache.wicket.model.IModel<String>() {
                                public String getObject(){reads++;return "Header";}
                                public void detach(){detaches++;}
                            };
                            var column=new StorageColumn(model);
                            if(column.getDisplayModel()!=model || !"name".equals(column.getSortProperty()))throw new AssertionError("Constructor stores model/sort identity");
                            if(headers!=0 || cells!=0 || reads!=0 || detaches!=0)throw new AssertionError("Constructors cannot execute header/cell/model/detach callbacks");
                            tester.startPage(new RenderPage(column));
                            tester.assertLabel("header","Header");tester.assertLabel("row:cell","Rendered row");
                            if(headers!=1 || cells!=1 || reads==0)throw new AssertionError("Explicit later population renders header and cell");
                            int prior=detaches;column.detach();
                            if(detaches<=prior)throw new AssertionError("Explicit detach invokes model cleanup later");
                        } finally {tester.destroy();}
                    }
                }
            """.trimIndent())
            val classpath=listOf("libraries","springTestLibraries").flatMap {System.getProperty("wicketlens.test.$it").split(File.pathSeparator)}.distinct().joinToString(File.pathSeparator)
            val compiler=System.getProperty("wicketlens.test.javac")
            fun run(command:List<String>,name:String) {
                val log=File(directory,name);val process=ProcessBuilder(command).directory(directory).redirectErrorStream(true).redirectOutput(log).start()
                if(!process.waitFor(45,TimeUnit.SECONDS)){process.destroyForcibly();fail("Column oracle timed out: ${log.readText()}")}
                assertEquals(log.readText(),0,process.exitValue())
            }
            run(listOf(compiler,"--release","21","-cp",classpath,"-d",directory.path)+example.listFiles()!!.filter {it.extension=="java"}.map {it.path},"compile.log")
            run(listOf(File(File(compiler).parentFile,"java").path,"-cp",directory.path+File.pathSeparator+classpath,"example.Oracle"),"runtime.log")
        } finally {directory.deleteRecursively()}
    }
}
