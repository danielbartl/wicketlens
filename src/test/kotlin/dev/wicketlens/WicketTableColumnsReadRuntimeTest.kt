package dev.wicketlens

import junit.framework.TestCase
import java.io.File
import java.nio.file.Files
import java.util.concurrent.TimeUnit

class WicketTableColumnsReadRuntimeTest:TestCase() {
    fun testImmutableColumnsIdentityAndSizeDoNotInvokeProviderOrElementCallbacks() {
        val directory=Files.createTempDirectory("wicketlens-table-forwarding-runtime").toFile()
        try {
            val example=File(directory,"example").apply {mkdirs()}
            File(example,"ForwardPage.html").writeText("<html><body><table wicket:id='table'></table></body></html>")
            File(example,"ForwardPage\$EmptyToolbar.html").writeText("<wicket:panel><tr><td wicket:id='cell'>Empty</td></tr></wicket:panel>")
            File(example,"ForwardPage.java").writeText("""
                package example;
                import java.util.*;
                import org.apache.wicket.model.*;
                import org.apache.wicket.markup.html.*;
                import org.apache.wicket.markup.html.basic.*;
                import org.apache.wicket.extensions.markup.html.repeater.data.table.*;
                import org.apache.wicket.extensions.markup.html.repeater.data.grid.ICellPopulator;
                import org.apache.wicket.markup.repeater.*;
                import org.apache.wicket.markup.repeater.data.*;
                public class ForwardPage extends WebPage {
                    static int queries,configures,reads,elementCalls,customReads;
                    final List<IColumn<String,String>> columns;
                    final DataTable<String,String> table;
                    final EmptyToolbar first,second;
                    final class EmptyToolbar extends AbstractToolbar {
                        private final WebMarkupContainer cell;
                        EmptyToolbar(DataTable<String,String> table){super(table);cell=new WebMarkupContainer("cell");cell.add(org.apache.wicket.AttributeModifier.replace("colspan",(IModel<Integer>)ForwardPage.this::columnsCount));add(cell);}
                        boolean owns(DataTable<?,?> expected){return getTable()==expected;}
                        protected void onConfigure(){configures++;super.onConfigure();setVisible(getTable().getRowCount()==0);}
                    }
                    private int columnsCount(){reads++;return table.getColumns().size();}
                    public ForwardPage(){
                        var provider=new IDataProvider<String>() {
                            public long size(){queries++;return 0;}
                            public Iterator<String> iterator(long first,long count){queries++;return List.<String>of().iterator();}
                            public IModel<String> model(String value){queries++;return Model.of(value);}
                        };
                        var column=new AbstractColumn<String,String>(Model.of("Value")) {
                            public void populateItem(Item<ICellPopulator<String>> item,String id,IModel<String> model){elementCalls++;item.add(new Label(id,model));}
                            public String toString(){elementCalls++;return "column";}
                            public int hashCode(){elementCalls++;return 1;}
                        };
                        columns=List.of(column);table=new DataTable<String,String>("table",columns,provider,10);
                        add(table);first=new EmptyToolbar(table);second=new EmptyToolbar(table);
                    }
                    public static void main(String[] args){
                        var tester=new org.apache.wicket.util.tester.WicketTester();
                        try {
                            var page=new ForwardPage();
                            if(!page.first.owns(page.table)||!page.second.owns(page.table))throw new AssertionError("Wrong table reference stored");
                            if(page.first.getId().equals(page.second.getId()))throw new AssertionError("Toolbar ID allocation was not distinct");
                            if(page.first.getParent()!=null || page.second.getParent()!=null || ((org.apache.wicket.MarkupContainer)page.table.getBottomToolbars().get("toolbars")).iterator().hasNext())throw new AssertionError("Construction registered a toolbar");
                            if(queries!=0||configures!=0||reads!=0||elementCalls!=0)throw new AssertionError("Construction ran provider/model/element callbacks");
                            if(page.table.getColumns()!=page.columns||page.table.getColumns().size()!=1||queries!=0||reads!=0||elementCalls!=0)throw new AssertionError("Getter/size evaluated values or callbacks");
                            var custom=new ArrayList<IColumn<String,String>>() {public int size(){customReads++;return super.size();}};
                            var probe=new DataTable<String,String>("probe",custom,page.table.getDataProvider(),10);customReads=0;
                            if(probe.getColumns()!=custom||customReads!=0)throw new AssertionError("The getter invoked a custom collection");
                            probe.getColumns().size();if(customReads!=1)throw new AssertionError("Custom size dispatch was not observable");
                            page.table.addBottomToolbar(page.first);
                            if(page.first.getParent()==null||page.second.getParent()!=null)throw new AssertionError("Registration was not separate");
                            tester.startPage(page);tester.assertRenderedPage(ForwardPage.class);
                            if(queries==0||configures==0||reads==0)throw new AssertionError("Later rendering did not run provider/model callbacks");
                        } finally {tester.destroy();}
                    }
                }
            """.trimIndent())
            val classpath=listOf("libraries","springTestLibraries").flatMap {System.getProperty("wicketlens.test.$it").split(File.pathSeparator)}.distinct().joinToString(File.pathSeparator)
            val compiler=System.getProperty("wicketlens.test.javac")
            fun run(command:List<String>,name:String) {
                val log=File(directory,name);val process=ProcessBuilder(command).directory(directory).redirectErrorStream(true).redirectOutput(log).start()
                if(!process.waitFor(45,TimeUnit.SECONDS)){process.destroyForcibly();fail("Toolbar forwarding oracle timed out: ${log.readText()}")}
                assertEquals(log.readText(),0,process.exitValue())
            }
            run(listOf(compiler,"--release","21","-cp",classpath,"-d",directory.path,File(example,"ForwardPage.java").path),"compile.log")
            run(listOf(File(File(compiler).parentFile,"java").path,"-cp",directory.path+File.pathSeparator+classpath,"example.ForwardPage"),"runtime.log")
        } finally {directory.deleteRecursively()}
    }
}
