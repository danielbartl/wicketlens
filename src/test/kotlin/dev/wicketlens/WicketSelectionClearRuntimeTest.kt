package dev.wicketlens

import junit.framework.TestCase
import java.io.File
import java.nio.file.Files
import java.util.concurrent.TimeUnit

/** Fixture oracle: clearing, null-column refresh and failed model dispatch are different runtime outcomes. */
class WicketSelectionClearRuntimeTest : TestCase() {
    fun testBulkConfirmationReadsTheModelOnlyOnSubmitAndRefreshRequiresPriorCallsToReturn() {
        val directory=Files.createTempDirectory("wicketlens-selection-clear-runtime").toFile()
        try {
            val example=File(directory,"example").apply {mkdirs()}
            File(example,"DialogBox.java").writeText(WicketConfirmationFixture.base.replace("super(id);","super(id);setOutputMarkupId(true);"))
            File(example,"ConfirmBox.java").writeText(WicketConfirmationFixture.host)
            File(example,"Actions.java").writeText(WicketConfirmationFixture.actions)
            File(example,"SelectionBox.java").writeText(WicketSelectionClearFixture.column)
            File(example,"TableBox.java").writeText(WicketSelectionClearFixture.table.replace("selectionColumn=null;", "selectionColumn=Oracle.column;")
                .replace("public TableBox clearSelection", "public void click(AjaxRequestTarget target,List<ConfirmationPage.Row> rows) {action.accept(target,rows);} public TableBox clearSelection"))
            File(example,"ConfirmationPage.java").writeText(WicketSelectionClearFixture.page.replace("            }\n            private void delete", """
                    add(new org.apache.wicket.ajax.markup.html.AjaxLink<Void>("bulk") {
                        @Override public void onClick(AjaxRequestTarget target) {table.click(target,java.util.List.of(new Row("r")));}
                    });
                }
                private void delete"""))
            File(example,"DialogBox.html").writeText(WicketInitialContentFixture.hostMarkup)
            File(example,"ConfirmationPage.html").writeText("<html xmlns:wicket='http://wicket.apache.org'><body>"+WicketConfirmationFixture.markup+"<a wicket:id='bulk'>delete</a></body></html>")
            File(example,"Oracle.java").writeText("""
                package example;
                import java.util.*;
                import org.apache.wicket.model.IModel;
                import org.apache.wicket.ajax.AjaxRequestTarget;
                public class Oracle {
                    static SelectionBox column;static int reads;static int refreshes;static int getterCalls;
                    public static void main(String[] args) {
                        var tester=new org.apache.wicket.util.tester.WicketTester();
                        try {
                            Set<ConfirmationPage.Row> selected=new LinkedHashSet<>(List.of(new ConfirmationPage.Row("r")));
                            column=new SelectionBox((IModel<Collection<?>>)()->{reads++;return selected;});
                            var page=new ConfirmationPage();tester.startPage(page);
                            if(reads!=0 || selected.isEmpty()) throw new AssertionError("Construction/rendering must not clear or query this selection");
                            tester.executeAjaxEvent("bulk","click");
                            if(reads!=0 || selected.isEmpty()) throw new AssertionError("Ask only stores the pending callback");
                            tester.executeAjaxEvent("confirm:dialog:form:confirm","click");
                            if(reads!=1 || !selected.isEmpty()) throw new AssertionError("The fixture consumer reaches the guarded model collection clear");
                            tester.assertComponentOnAjaxResponse("rows");
                            reads=0;selected.add(new ConfirmationPage.Row("r"));column=null;
                            tester.startPage(new ConfirmationPage());tester.executeAjaxEvent("bulk","click");tester.executeAjaxEvent("confirm:dialog:form:confirm","click");
                            if(reads!=0 || selected.isEmpty()) throw new AssertionError("A null column skips model access but can still refresh");
                            tester.assertComponentOnAjaxResponse("rows");
                            AjaxRequestTarget target=(AjaxRequestTarget)java.lang.reflect.Proxy.newProxyInstance(Oracle.class.getClassLoader(),new Class<?>[]{AjaxRequestTarget.class},(proxy,method,values)->{
                                if(method.getName().equals("add")) refreshes++;return null;
                            });
                            column=new SelectionBox((IModel<Collection<?>>)()->{reads++;throw new IllegalStateException("model");});
                            try {new TableBox("failed").clearSelection(target);throw new AssertionError("Expected model failure");}
                            catch(IllegalStateException expected) {if(!expected.getMessage().equals("model")) throw expected;}
                            if(refreshes!=0) throw new AssertionError("A failed model read prevents later refresh");
                            column=new SelectionBox(null) {
                                @Override public IModel<? extends Collection<?>> getSelection() {getterCalls++;throw new IllegalStateException("override");}
                            };
                            try {new TableBox("overridden").clearSelection(target);throw new AssertionError("Expected overridden getter");}
                            catch(IllegalStateException expected) {if(!expected.getMessage().equals("override")) throw expected;}
                            if(getterCalls!=1 || refreshes!=0) throw new AssertionError("Runtime column dispatch can differ from its declared getter");
                        } finally {tester.destroy();}
                    }
                }
            """)
            val classpath=listOf("libraries","springTestLibraries").flatMap {System.getProperty("wicketlens.test.$it").split(File.pathSeparator)}.distinct().joinToString(File.pathSeparator)
            fun run(command:List<String>,name:String) {
                val log=File(directory,name);val process=ProcessBuilder(command).directory(directory).redirectErrorStream(true).redirectOutput(log).start()
                if(!process.waitFor(45,TimeUnit.SECONDS)) {process.destroyForcibly();fail("Selection clear oracle timed out: ${log.readText()}")}
                assertEquals(log.readText(),0,process.exitValue())
            }
            val compiler=System.getProperty("wicketlens.test.javac")
            run(listOf(compiler,"--release","21","-cp",classpath,"-d",directory.path)+listOf("DialogBox.java","ConfirmBox.java","Actions.java","SelectionBox.java","TableBox.java","ConfirmationPage.java","Oracle.java").map {File(example,it).path},"compile.log")
            run(listOf(File(File(compiler).parentFile,"java").path,"-cp",directory.path+File.pathSeparator+classpath,"example.Oracle"),"runtime.log")
        } finally {directory.deleteRecursively()}
    }
}
