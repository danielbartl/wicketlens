package dev.wicketlens

import junit.framework.TestCase
import java.io.File
import java.nio.file.Files
import java.util.concurrent.TimeUnit

/** Independent Wicket 11 rendering oracle; tree instances supply their own runtime node IDs. */
class WicketTreeRuntimeTest:TestCase() {
    fun testNestedAndTablePopulationExpansionAndContentAlternatives() {
        val directory=Files.createTempDirectory("wicketlens-tree-runtime").toFile()
        try {
            val example=File(directory,"example").apply { mkdirs() }
            val table=WicketTreeFixture.table.replace("java.util.List.of()","java.util.List.of(new org.apache.wicket.extensions.markup.html.repeater.tree.table.TreeColumn<String,String>(org.apache.wicket.model.Model.of(\"Name\")))")
            for((name,text) in listOf("Branch" to WicketTreeFixture.node,"Nested" to WicketTreeFixture.nested,"Tabular" to table,"Fields" to WicketTreeFixture.fields)) File(example,"$name.java").writeText(text)
            File(example,"Fields.html").writeText(WicketTreeFixture.fieldsMarkup.replace("<wicket:panel>","<wicket:panel xmlns:wicket='http://wicket.apache.org'>"))
            File(example,"ExamplePage.java").writeText("""
                package example;
                import org.apache.wicket.*;
                import org.apache.wicket.model.*;
                import org.apache.wicket.markup.html.*;
                import org.apache.wicket.markup.html.basic.Label;
                import org.apache.wicket.extensions.markup.html.repeater.tree.*;
                import java.util.*;
                public class ExamplePage extends WebPage {
                    enum Action implements Runnable { INSTANCE; public void run() {} }
                    static final class Provider implements ITreeProvider<String> {
                        public Iterator<String> getRoots() { return List.of("root").iterator(); }
                        public boolean hasChildren(String node) { return node.equals("root"); }
                        public Iterator<String> getChildren(String node) { return List.of("leaf").iterator(); }
                        public IModel<String> model(String node) { return Model.of(node); }
                        public void detach() {}
                    }
                    final Nested folders=new Nested("folders",new Provider());
                    final Tabular accounts=new Tabular("accounts",new Provider());
                    public ExamplePage() {
                        add(folders,accounts);
                        add(new Fields("clickable",Model.of("icon"),Action.INSTANCE));
                        add(new Fields("noIcon",Model.of((String)null),null));
                    }
                    static List<Node<?>> visibleNodes(AbstractTree<String> tree) {
                        List<Node<?>> result=new ArrayList<>();
                        tree.visitChildren(Node.class,(node,visit)->{ if(node.isVisibleInHierarchy()) result.add((Node<?>)node); });
                        return result;
                    }
                    public static void main(String[] args) {
                        org.apache.wicket.util.tester.WicketTester tester=new org.apache.wicket.util.tester.WicketTester();
                        try {
                            ExamplePage page=new ExamplePage();tester.startPage(page);
                            tester.assertComponent("clickable:link:label",Label.class);
                            if(!(page.get("noIcon:plain:icon") instanceof WebMarkupContainer) || page.get("noIcon:plain:icon").isVisible()) throw new AssertionError("The nullable icon has a hidden placeholder");
                            for(AbstractTree<String> tree:List.of(page.folders,page.accounts)) {
                                List<Node<?>> before=visibleNodes(tree);
                                if(before.size()!=1) throw new AssertionError("One visible root: "+tree.getId()+" "+before.size());
                                Node<?> root=before.getFirst();
                                if(root.get("junction")==null || !(root.get("content") instanceof Fields)) throw new AssertionError("Node initialization must create junction and source content");
                                if(!(root.get("content:plain:label") instanceof Label)) throw new AssertionError("Null action selects plain label scope");
                                tree.expand("root");tester.startPage(page);
                                if(visibleNodes(tree).size()!=2) throw new AssertionError("Expansion populates a child: "+tree.getId());
                                tree.collapse("root");tester.startPage(page);
                                if(visibleNodes(tree).size()!=1) throw new AssertionError("Collapse hides children: "+tree.getId());
                            }
                        } finally { tester.destroy(); }
                    }
                }
            """)
            File(example,"ExamplePage.html").writeText("<html xmlns:wicket='http://wicket.apache.org'><body><div wicket:id='folders'></div><div wicket:id='accounts'></div><div wicket:id='clickable'></div><div wicket:id='noIcon'></div></body></html>")
            val classpath=listOf("libraries","springTestLibraries").flatMap { System.getProperty("wicketlens.test.$it").split(File.pathSeparator) }.distinct().joinToString(File.pathSeparator)
            val compiler=System.getProperty("wicketlens.test.javac")
            fun run(command:List<String>,name:String) {
                val log=File(directory,name);val process=ProcessBuilder(command).directory(directory).redirectErrorStream(true).redirectOutput(log).start()
                if(!process.waitFor(45,TimeUnit.SECONDS)) { process.destroyForcibly();fail("Tree runtime check timed out: ${log.readText()}") }
                assertEquals(log.readText(),0,process.exitValue())
            }
            run(listOf(compiler,"--release","21","-cp",classpath,"-d",directory.path)+example.listFiles()!!.filter { it.extension=="java" }.map { it.path },"compile.log")
            run(listOf(File(File(compiler).parentFile,"java").path,"-cp",directory.path+File.pathSeparator+classpath,"example.ExamplePage"),"runtime.log")
        } finally { directory.deleteRecursively() }
    }
}
