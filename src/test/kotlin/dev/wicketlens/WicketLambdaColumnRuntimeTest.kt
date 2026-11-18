package dev.wicketlens

import junit.framework.TestCase
import java.io.File
import java.nio.file.Files
import java.util.concurrent.TimeUnit

class WicketLambdaColumnRuntimeTest : TestCase() {
    fun testRealFrameworkStoresTheFunctionAndReadsRowsOnlyThroughTheLaterDataModel() {
        val directory=Files.createTempDirectory("wicketlens-lambda-runtime").toFile()
        try {
            val example=File(directory,"example").apply {mkdirs()}
            File(example,"RowFunction.java").writeText(WicketLambdaColumnFixture.function)
            File(example,"BadgeColumn.java").writeText(WicketLambdaColumnFixture.column.replace("{item.add(new Label(id,getDataModel(row)));}","{Oracle.cells++;item.add(new Label(id,getDataModel(row)));}"))
            File(example,"RenderPage.java").writeText("""
                package example;public class RenderPage extends org.apache.wicket.markup.html.WebPage {
                    public RenderPage(BadgeColumn column){
                        add(column.getHeader("header"));
                        var row=new org.apache.wicket.markup.repeater.Item<org.apache.wicket.extensions.markup.html.repeater.data.grid.ICellPopulator<String>>("row",0);
                        column.populateItem(row,"cell",org.apache.wicket.model.Model.of("rendered row"));add(row);
                    }
                }
            """.trimIndent())
            File(example,"RenderPage.html").writeText("<html><body><b wicket:id='header'></b><div wicket:id='row'><span wicket:id='cell'></span></div></body></html>")
            File(example,"Oracle.java").writeText("""
                package example;public class Oracle {
                    static int functions,headers,rows,cells,detaches;
                    public static void main(String[] args) {
                        var tester=new org.apache.wicket.util.tester.WicketTester();
                        try {
                            var header=new org.apache.wicket.model.IModel<String>(){public String getObject(){headers++;return "Header";}};
                            var row=new org.apache.wicket.model.IModel<String>(){public String getObject(){rows++;return "invoice";}public void detach(){detaches++;}};
                            RowFunction<String,String> value=text->{functions++;return text.toUpperCase(java.util.Locale.ROOT);};
                            RowFunction<String,String> style=text->"accent";
                            var sorted=new BadgeColumn(header,"name",value,style);
                            var unsorted=new org.apache.wicket.extensions.markup.html.repeater.data.table.LambdaColumn<String,String>(header,value::apply);
                            if(sorted.getDisplayModel()!=header || !"name".equals(sorted.getSortProperty()) || unsorted.getSortProperty()!=null)throw new AssertionError("Constructor stores model and sort identity");
                            if(functions!=0 || rows!=0 || headers!=0 || cells!=0 || detaches!=0)throw new AssertionError("Construction does not invoke row/header/model callbacks");
                            var data=sorted.getDataModel(row);
                            if(functions!=0 || rows!=0)throw new AssertionError("Creating the data model does not read a row");
                            if(!"INVOICE".equals(data.getObject()) || functions!=1 || rows!=1)throw new AssertionError("Later model access invokes the stored function");
                            data.detach();if(detaches!=1)throw new AssertionError("The later model detaches its row");
                            boolean rejected=false;
                            try {new org.apache.wicket.extensions.markup.html.repeater.data.table.LambdaColumn<String,String>(header,(org.danekja.java.util.function.serializable.SerializableFunction<String,String>)null);}catch(IllegalArgumentException expected){rejected=true;}
                            if(!rejected || functions!=1)throw new AssertionError("The constructor null guard does not invoke a function");
                            tester.startPage(new RenderPage(sorted));tester.assertLabel("header","Header");tester.assertLabel("row:cell","RENDERED ROW");
                            if(cells!=1 || functions<2 || headers==0)throw new AssertionError("Explicit later rendering creates and reads the cell");
                        } finally {tester.destroy();}
                    }
                }
            """.trimIndent())
            val classpath=listOf("libraries","springTestLibraries").flatMap {System.getProperty("wicketlens.test.$it").split(File.pathSeparator)}.distinct().joinToString(File.pathSeparator)
            val compiler=System.getProperty("wicketlens.test.javac")
            fun run(command:List<String>,name:String) {
                val log=File(directory,name);val process=ProcessBuilder(command).directory(directory).redirectErrorStream(true).redirectOutput(log).start()
                if(!process.waitFor(45,TimeUnit.SECONDS)){process.destroyForcibly();fail("Lambda-column oracle timed out: ${log.readText()}")}
                assertEquals(log.readText(),0,process.exitValue())
            }
            run(listOf(compiler,"--release","21","-cp",classpath,"-d",directory.path)+example.listFiles()!!.filter {it.extension=="java"}.map {it.path},"compile.log")
            run(listOf(File(File(compiler).parentFile,"java").path,"-cp",directory.path+File.pathSeparator+classpath,"example.Oracle"),"runtime.log")
        } finally {directory.deleteRecursively()}
    }
}
