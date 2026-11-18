package dev.wicketlens

import junit.framework.TestCase
import java.io.File
import java.nio.file.Files
import java.util.concurrent.TimeUnit

class WicketToolbarRegistrationRuntimeTest:TestCase() {
    fun testHeaderRegistrationDefersSortingRenderingAndProviderCallbacks() {
        val directory=Files.createTempDirectory("wicketlens-toolbar-runtime").toFile()
        try {
            val example=File(directory,"example").apply {mkdirs()}
            File(example,"ToolbarRuntimePage.html").writeText("<html><body><table wicket:id='table'></table></body></html>")
            File(example,"ToolbarRuntimePage.java").writeText("""
                package example;
                import java.util.*;
                import org.apache.wicket.model.*;
                import org.apache.wicket.extensions.markup.html.repeater.data.table.*;
                import org.apache.wicket.extensions.markup.html.repeater.data.grid.ICellPopulator;
                import org.apache.wicket.markup.repeater.*;
                import org.apache.wicket.markup.repeater.data.*;
                import org.apache.wicket.markup.html.basic.Label;
                public class ToolbarRuntimePage extends org.apache.wicket.markup.html.WebPage {
                    static int sizes,iterators,models,rows,cells,populates,headers,detaches,hooks,sortReads,sortFactories,renders;
                    static class Provider implements IDataProvider<String> {
                        public long size(){sizes++;return 1;}
                        public Iterator<String> iterator(long first,long count){iterators++;return List.of("row").iterator();}
                        public IModel<String> model(String value){models++;return Model.of(value);}
                        public void detach(){detaches++;}
                    }
                    static class RowsTable extends DataTable<String,String> {
                        RowsTable(String id,List<? extends IColumn<String,String>> columns,IDataProvider<String> provider){super(id,columns,provider,10);}
                        protected Item<String> newRowItem(String id,int index,IModel<String> model){rows++;return super.newRowItem(id,index,model);}
                        protected Item<IColumn<String,String>> newCellItem(String id,int index,IModel<IColumn<String,String>> model){cells++;return super.newCellItem(id,index,model);}
                    }
                    static class Header extends org.apache.wicket.extensions.ajax.markup.html.repeater.data.table.AjaxFallbackHeadersToolbar<String> {
                        Header(DataTable<String,String> table,org.apache.wicket.extensions.markup.html.repeater.data.sort.ISortStateLocator<String> locator){super(table,locator);}
                        protected void onBeforeRender(){renders++;super.onBeforeRender();}
                        protected org.apache.wicket.markup.html.WebMarkupContainer newSortableHeader(String id,String property,org.apache.wicket.extensions.markup.html.repeater.data.sort.ISortStateLocator<String> locator){sortFactories++;return super.newSortableHeader(id,property,locator);}
                    }
                    final Provider provider=new Provider();
                    final List<IColumn<String,String>> columns=List.of(new AbstractColumn<String,String>(Model.of("Header"),"key") {
                        public org.apache.wicket.Component getHeader(String id){headers++;return super.getHeader(id);}
                        public void populateItem(Item<ICellPopulator<String>> item,String id,IModel<String> model){populates++;item.add(new Label(id,model));}
                    });
                    final DataTable<String,String> table=new RowsTable("table",columns,provider);
                    final org.apache.wicket.extensions.markup.html.repeater.data.sort.ISortStateLocator<String> locator=()->{sortReads++;return new org.apache.wicket.extensions.markup.html.repeater.util.SingleSortState<String>();};
                    final Header top=new Header(table,locator),bottom=new Header(table,locator);
                    public ToolbarRuntimePage(){table.addTopToolbar(top);table.addBottomToolbar(bottom);add(table);}
                    public static void main(String[] args) {
                        var tester=new org.apache.wicket.util.tester.WicketTester();
                        try {
                            var page=new ToolbarRuntimePage();
                            if(sizes+iterators+models+rows+cells+populates+headers+detaches+sortReads+sortFactories+renders!=0)throw new AssertionError("Construction or attachment ran a data/render callback");
                            if(page.top.getId().equals(page.bottom.getId()) || page.table.getTopToolbars().get("toolbars:"+page.top.getId())!=page.top || page.table.getBottomToolbars().get("toolbars:"+page.bottom.getId())!=page.bottom)throw new AssertionError("Fresh toolbars were not attached to their matching private slots");
                            if(page.table.getColumns()!=page.columns || page.table.getDataProvider()!=page.provider)throw new AssertionError("Inputs were not retained");
                            if(page.table.getItemsPerPage()!=10 || page.table.getCurrentPage()!=0 || page.table.getBody().get("rows")==null || page.table.get("caption")==null || page.table.get("colGroup")==null || page.table.get("topToolbars")==null || page.table.get("bottomToolbars")==null)throw new AssertionError("Framework shell or paging storage missing");
                            if(page.table.getItemCount()!=1 || sizes!=1 || iterators+models+rows+cells+populates+headers+detaches!=0)throw new AssertionError("Explicit count should invoke size only");
                            tester.startPage(page);tester.assertRenderedPage(ToolbarRuntimePage.class);
                            if(iterators==0 || models==0 || rows==0 || cells==0 || populates==0 || detaches==0 || sortReads==0 || sortFactories==0 || renders==0 || headers==0)throw new AssertionError("Rendering did not invoke later callbacks");
                            new DataTable<String,String>("probe",page.columns,page.provider,10){protected IModel<String> getCaptionModel(){hooks++;return null;}};
                            if(hooks!=1)throw new AssertionError("A source caption override is eager and requires a guard");
                        } finally {tester.destroy();}
                    }
                }
            """.trimIndent())
            val classpath=listOf("libraries","springTestLibraries").flatMap {System.getProperty("wicketlens.test.$it").split(File.pathSeparator)}.distinct().joinToString(File.pathSeparator)
            val compiler=System.getProperty("wicketlens.test.javac")
            fun run(command:List<String>,name:String) {
                val log=File(directory,name);val process=ProcessBuilder(command).directory(directory).redirectErrorStream(true).redirectOutput(log).start()
                if(!process.waitFor(45,TimeUnit.SECONDS)){process.destroyForcibly();fail("DataTable oracle timed out: ${log.readText()}")}
                assertEquals(log.readText(),0,process.exitValue())
            }
            run(listOf(compiler,"--release","21","-cp",classpath,"-d",directory.path,File(example,"ToolbarRuntimePage.java").path),"compile.log")
            run(listOf(File(File(compiler).parentFile,"java").path,"-cp",directory.path+File.pathSeparator+classpath,"example.ToolbarRuntimePage"),"runtime.log")
        } finally {directory.deleteRecursively()}
    }
}
