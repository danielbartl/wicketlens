package dev.wicketlens

import junit.framework.TestCase
import java.io.File
import java.nio.file.Files
import java.util.concurrent.TimeUnit

class WicketDateColumnRuntimeTest : TestCase() {
    fun testRealFrameworkStoresTheEnumDefaultsAndDefersDateModelEvaluation() {
        val directory=Files.createTempDirectory("wicketlens-date-column-runtime").toFile()
        try {
            val example=File(directory,"example").apply {mkdirs()}
            File(example,"DateColumn.java").writeText(WicketDateColumnFixture.column.replace("value->value","value->{Oracle.functions++;return value;}"))
            File(example,"RenderPage.java").writeText("""
                package example;public class RenderPage extends org.apache.wicket.markup.html.WebPage {
                    public RenderPage(DateColumn column){
                        add(column.getHeader("header"));
                        var row=new org.apache.wicket.markup.repeater.Item<org.apache.wicket.extensions.markup.html.repeater.data.grid.ICellPopulator<java.time.LocalDate>>("row",0);
                        column.populateItem(row,"cell",org.apache.wicket.model.Model.of(java.time.LocalDate.of(2026,1,15)));add(row);
                    }
                }
            """.trimIndent())
            File(example,"RenderPage.html").writeText("<html><body><b wicket:id='header'></b><div wicket:id='row'><span wicket:id='cell'></span></div></body></html>")
            File(example,"Oracle.java").writeText("""
                package example;public class Oracle {
                    static int functions,headers,rows;
                    public static void main(String[] args)throws Exception {
                        var tester=new org.apache.wicket.util.tester.WicketTester();
                        try {
                            var locale=java.util.Locale.getDefault();var zone=java.util.TimeZone.getDefault().getID();
                            var header=new org.apache.wicket.model.IModel<String>(){public String getObject(){headers++;return "Date";}};
                            var column=new DateColumn(header);
                            var date=DateColumn.class.getDeclaredField("dateStyle");date.setAccessible(true);
                            var time=DateColumn.class.getDeclaredField("timeStyle");time.setAccessible(true);
                            if(date.get(column)!=java.time.format.FormatStyle.MEDIUM || time.get(column)!=java.time.format.FormatStyle.SHORT)throw new AssertionError("Constructor stores the exact JDK enum constants");
                            if(functions!=0 || headers!=0 || rows!=0)throw new AssertionError("Enum storage does not read models or row functions");
                            if(!locale.equals(java.util.Locale.getDefault()) || !zone.equals(java.util.TimeZone.getDefault().getID()))throw new AssertionError("Column construction does not change locale/time-zone defaults");
                            var row=new org.apache.wicket.model.IModel<java.time.LocalDate>(){public java.time.LocalDate getObject(){rows++;return java.time.LocalDate.of(2026,1,15);}};
                            var data=column.getDataModel(row);
                            if(functions!=0 || rows!=0)throw new AssertionError("Data-model creation remains lazy");
                            if(!java.time.LocalDate.of(2026,1,15).equals(data.getObject()) || functions!=1 || rows!=1)throw new AssertionError("Explicit later model access invokes the function");
                            tester.startPage(new RenderPage(column));tester.assertLabel("header","Date");
                            if(functions<2 || headers==0)throw new AssertionError("Explicit rendering reads the column models later");
                        } finally {tester.destroy();}
                    }
                }
            """.trimIndent())
            val classpath=listOf("libraries","springTestLibraries").flatMap {System.getProperty("wicketlens.test.$it").split(File.pathSeparator)}.distinct().joinToString(File.pathSeparator)
            val compiler=System.getProperty("wicketlens.test.javac")
            fun run(command:List<String>,name:String) {
                val log=File(directory,name);val process=ProcessBuilder(command).directory(directory).redirectErrorStream(true).redirectOutput(log).start()
                if(!process.waitFor(45,TimeUnit.SECONDS)){process.destroyForcibly();fail("Date-column oracle timed out: ${log.readText()}")}
                assertEquals(log.readText(),0,process.exitValue())
            }
            run(listOf(compiler,"--release","21","-cp",classpath,"-d",directory.path)+example.listFiles()!!.filter {it.extension=="java"}.map {it.path},"compile.log")
            run(listOf(File(File(compiler).parentFile,"java").path,"-cp",directory.path+File.pathSeparator+classpath,"example.Oracle"),"runtime.log")
        } finally {directory.deleteRecursively()}
    }
}
