package dev.wicketlens

import junit.framework.TestCase
import java.io.File
import java.nio.file.Files
import java.util.concurrent.TimeUnit

/** Independent framework oracle: factory invocation, cache reuse and visibility are different runtime facts. */
class WicketTableToolbarRuntimeTest : TestCase() {
    fun testRuntimeToolbarIdsCacheReuseReplacementAndEmptyVisibility() {
        val directory=Files.createTempDirectory("wicketlens-table-toolbar-runtime").toFile()
        try {
            val example=File(directory,"example").apply { mkdirs() }
            val column="""java.util.List.of(new AbstractColumn<String,String>(Model.of("Value")) {
                public void populateItem(org.apache.wicket.markup.repeater.Item<org.apache.wicket.extensions.markup.html.repeater.data.grid.ICellPopulator<String>> item,String id,IModel<String> row) {
                    item.add(new org.apache.wicket.markup.html.basic.Label(id,row));
                }
            }),new IDataProvider"""
            val host=WicketTableToolbarFixture.host
                .replace("private EmptyToolbar emptyToolbar;","private EmptyToolbar emptyToolbar; private final java.util.List<String> runtimeRows=new java.util.ArrayList<>();")
                .replace("java.util.List.of(),new IDataProvider",column)
                .replace("return java.util.List.<String>of().iterator();","return runtimeRows.iterator();")
                .replace("return 0;","return runtimeRows.size();")
                .replace("private int columns()", """
                    public void rows(String... rows) { runtimeRows.clear(); runtimeRows.addAll(java.util.List.of(rows)); }
                    public void extraToolbar() { table.addBottomToolbar(new NoRecordsToolbar(table)); }
                    public Component toolbar() { return emptyToolbar; }
                    public int toolbarCount() { return ((MarkupContainer)table.getBottomToolbars().get("toolbars")).size(); }
                    private int columns()
                """)
            File(example,"TableHost.java").writeText(host)
            File(example,"EmptyView.java").writeText(WicketTableToolbarFixture.content)
            File(example,"OraclePage.java").writeText("""
                package example;
                public class OraclePage extends org.apache.wicket.markup.html.WebPage {
                    final TableHost host;
                    public OraclePage() {
                        host=new TableHost("empty"); host.extraToolbar();
                        if(host.getParent()!=null)throw new AssertionError("Initial cache setup should have an unattached owner");
                        host.setEmptyState(id -> new EmptyView(id,"First empty","No records"));
                        add(host);
                    }
                    public static void main(String[] args) {
                        var tester=new org.apache.wicket.util.tester.WicketTester();
                        try {
                            OraclePage page=new OraclePage();
                            var toolbar=page.host.toolbar();
                            if(toolbar==null || toolbar.getId()==null || page.host.toolbarCount()!=2 || toolbar.get("cell:content:title")==null) throw new AssertionError("Factory installs content immediately into a lazily allocated, dynamically identified toolbar");
                            tester.startPage(page);
                            String path=toolbar.getPageRelativePath();
                            tester.assertLabel(path+":cell:content:title","First empty");
                            if(!toolbar.isVisible()) throw new AssertionError("Empty provider shows its toolbar");
                            var first=toolbar.get("cell:content");
                            page.host.setEmptyState(id -> new EmptyView(id,"Second empty",(String)null));
                            if(page.host.toolbar()!=toolbar || page.host.toolbarCount()!=2 || first.getParent()!=null || toolbar.get("cell:content")==first) throw new AssertionError("Later factory call reuses the toolbar and replaces only its content");
                            tester.startPage(page); tester.assertLabel(path+":cell:content:title","Second empty");
                            if(toolbar.get("cell:content:message").isVisible()) throw new AssertionError("Message visibility depends on runtime model values");
                            page.host.rows("one");tester.startPage(page);
                            if(toolbar.isVisible() || tester.getLastResponseAsString().contains("Second empty")) throw new AssertionError("Populated provider hides empty state");
                            page.host.rows();tester.startPage(page);
                            if(!toolbar.isVisible()) throw new AssertionError("Empty provider shows the same toolbar again");
                            tester.assertLabel(path+":cell:content:title","Second empty");
                            try { page.host.setEmptyState(id -> new org.apache.wicket.markup.html.WebMarkupContainer("wrong"));throw new AssertionError("Wrong factory ID accepted"); }
                            catch(IllegalArgumentException expected) {}
                        } finally { tester.destroy(); }
                    }
                }
            """)
            File(example,"OraclePage.html").writeText("<html xmlns:wicket='http://wicket.apache.org'><body><div wicket:id='empty'></div></body></html>")
            File(example,"TableHost.html").writeText(WicketTableToolbarFixture.hostMarkup)
            File(example,"TableHost\$EmptyToolbar.html").writeText(WicketTableToolbarFixture.toolbarMarkup)
            File(example,"EmptyView.html").writeText(WicketTableToolbarFixture.contentMarkup)
            val classpath=listOf("libraries","springTestLibraries").flatMap { System.getProperty("wicketlens.test.$it").split(File.pathSeparator) }.distinct().joinToString(File.pathSeparator)
            fun run(command:List<String>,name:String) {
                val log=File(directory,name);val process=ProcessBuilder(command).directory(directory).redirectErrorStream(true).redirectOutput(log).start()
                if(!process.waitFor(45,TimeUnit.SECONDS)) { process.destroyForcibly();fail("Table toolbar runtime check timed out: ${log.readText()}") }
                assertEquals(log.readText(),0,process.exitValue())
            }
            val compiler=System.getProperty("wicketlens.test.javac")
            run(listOf(compiler,"--release","21","-cp",classpath,"-d",directory.path)+listOf("TableHost.java","EmptyView.java","OraclePage.java").map { File(example,it).path },"compile.log")
            run(listOf(File(File(compiler).parentFile,"java").path,"-cp",directory.path+File.pathSeparator+classpath,"example.OraclePage"),"runtime.log")
        } finally { directory.deleteRecursively() }
    }
}
