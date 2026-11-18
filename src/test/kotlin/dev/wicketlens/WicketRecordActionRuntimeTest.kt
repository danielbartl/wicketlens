package dev.wicketlens

import junit.framework.TestCase
import java.io.File
import java.nio.file.Files
import java.util.concurrent.TimeUnit

/** Runtime fixture oracle only. The analyzer neither dispatches actions nor predicts their data outcomes. */
class WicketRecordActionRuntimeTest : TestCase() {
    fun testPlainPageStoresTheActionWithoutRunningItDuringConstructionOrRender() {
        storedActionOracle(WicketRecordActionFixture.plainPage,"PAID")
    }
    fun testRepeatedRegistrationsStoreWithoutInvokingAndFixtureDispatchUsesItsSingleSlot() {
        storedActionOracle(WicketRecordActionFixture.repeatedPage,"PAID")
    }
    fun testFluentChainReturnsItsRegistrarWithoutInvokingEitherCallback() {
        storedActionOracle(WicketRecordActionFixture.chainedPage,"PAID")
    }
    fun testInitializerChainBindsItsRegistrarWithoutInvokingEitherCallback() {
        storedActionOracle(WicketRecordActionFixture.initializedPage,"PAID")
    }
    fun testReversingInitializerRegistrationsChangesItsSingleSlotDispatch() {
        storedActionOracle(WicketRecordActionFixture.initializedPage
            .replace("markOpen(List.of(row));","PENDING_CALL;")
            .replace("markPaid(List.of(row));","markOpen(List.of(row));")
            .replace("PENDING_CALL;","markPaid(List.of(row));"),"OPEN")
    }
    fun testReversingChainedFixtureRegistrationsChangesItsSingleSlotDispatch() {
        storedActionOracle(WicketRecordActionFixture.chainedPage
            .replace("markOpen(List.of(row));","PENDING_CALL;")
            .replace("markPaid(List.of(row));","markOpen(List.of(row));")
            .replace("PENDING_CALL;","markPaid(List.of(row));"),"OPEN")
    }
    fun testReversingFixtureRegistrationsChangesTheDispatchedOutcomeWithoutChangingSourceContexts() {
        storedActionOracle(WicketRecordActionFixture.repeatedPage
            .replace("markOpen(List.of(row));","PENDING_CALL;")
            .replace("markPaid(List.of(row));","markOpen(List.of(row));")
            .replace("PENDING_CALL;","markPaid(List.of(row));"),"OPEN")
    }
    private fun storedActionOracle(source:String,expectedState:String) {
        val directory=Files.createTempDirectory("wicketlens-stored-action-runtime").toFile()
        try {
            val example=File(directory,"example").apply {mkdirs()}
            File(example,"Actions.java").writeText(WicketConfirmationFixture.actions)
            File(example,"ConfirmationPage.java").writeText(source
                .replace("public ConfirmationPage()", "public static int transforms;public List<Row> snapshot(){return List.copyOf(records);} public ConfirmationPage()")
                .replace("WebMarkupContainer table=", "records.add(new Row(\"a\",State.OPEN));WebMarkupContainer table=")
                .replace(";add(table);", ";table.setOutputMarkupId(true);add(table);")
                .replace("Row paid(){return", "Row paid(){transforms++;return")
                .replace("Row opened(){return", "Row opened(){transforms++;return")
                .replace("            }\n            private void mark", """
                    add(new org.apache.wicket.ajax.markup.html.AjaxLink<Void>("paid") {
                        @Override public void onClick(org.apache.wicket.ajax.AjaxRequestTarget target) {actions.click(target,new Row("a",State.OPEN));}
                    });
                }
                private void mark"""))
            File(example,"ConfirmationPage.html").writeText("<html xmlns:wicket='http://wicket.apache.org'><body><div wicket:id='rows'></div><a wicket:id='paid'>paid</a></body></html>")
            File(example,"Oracle.java").writeText("""
                package example;public class Oracle {
                    public static void main(String[] args) {
                        var tester=new org.apache.wicket.util.tester.WicketTester();
                        try {
                            var page=new ConfirmationPage();tester.startPage(page);
                            if(ConfirmationPage.transforms!=0 || page.snapshot().get(0).state()!=ConfirmationPage.State.OPEN) throw new AssertionError("Registration/render must only store the callback");
                            tester.executeAjaxEvent("paid","click");
                            if(ConfirmationPage.transforms!=1 || page.snapshot().get(0).state()!=ConfirmationPage.State.$expectedState) throw new AssertionError("The fixture invokes only its stored updater through explicit dispatch");
                            tester.assertComponentOnAjaxResponse("rows");
                        } finally {tester.destroy();}
                    }
                }
            """)
            val classpath=listOf("libraries","springTestLibraries").flatMap {System.getProperty("wicketlens.test.$it").split(File.pathSeparator)}.distinct().joinToString(File.pathSeparator)
            val compiler=System.getProperty("wicketlens.test.javac")
            fun run(command:List<String>,name:String) {
                val log=File(directory,name);val process=ProcessBuilder(command).redirectErrorStream(true).redirectOutput(log).start()
                assertTrue(process.waitFor(60,TimeUnit.SECONDS));assertEquals(log.readText(),0,process.exitValue())
            }
            run(listOf(compiler,"--release","21","-cp",classpath,"-d",directory.path)+listOf("Actions.java","ConfirmationPage.java","Oracle.java").map {File(example,it).path},"compile.log")
            run(listOf(File(File(compiler).parentFile,"java").path,"-cp",directory.path+File.pathSeparator+classpath,"example.Oracle"),"runtime.log")
        } finally {directory.deleteRecursively()}
    }
    fun testRowAndBulkCallbacksRunAfterDispatchWhilePredicateResultsControlRecordUpdates() {
        val directory=Files.createTempDirectory("wicketlens-record-action-runtime").toFile()
        try {
            val example=File(directory,"example").apply {mkdirs()}
            File(example,"DialogBox.java").writeText(WicketConfirmationFixture.base.replace("super(id);","super(id);setOutputMarkupId(true);"))
            File(example,"ConfirmBox.java").writeText(WicketConfirmationFixture.host)
            File(example,"Actions.java").writeText(WicketConfirmationFixture.actions)
            File(example,"SelectionBox.java").writeText(WicketSelectionClearFixture.column)
            File(example,"TableBox.java").writeText(WicketSelectionClearFixture.table.replace("public TableBox clearSelection", "public void click(AjaxRequestTarget target,List<ConfirmationPage.Row> rows) {action.accept(target,rows);} public TableBox clearSelection"))
            File(example,"ConfirmationPage.java").writeText(WicketRecordActionFixture.page
                .replace("public ConfirmationPage()", "public static int transforms;public static boolean match=true;public List<Row> snapshot(){return List.copyOf(records);} public ConfirmationPage()")
                .replace("ConfirmBox confirm=new", "records.add(new Row(\"a\",State.OPEN));records.add(new Row(\"b\",State.OPEN));ConfirmBox confirm=new")
                .replace("Row paid() {return", "Row paid() {transforms++;return")
                .replace("return other instanceof Row r", "return match && other instanceof Row r")
                .replace("            }\n            private void markPaid", """
                    add(new org.apache.wicket.ajax.markup.html.AjaxLink<Void>("rowPaid") {
                        @Override public void onClick(AjaxRequestTarget target) {actions.click(target,new Row("a",State.OPEN));}
                    });
                    add(new org.apache.wicket.ajax.markup.html.AjaxLink<Void>("bulkPaid") {
                        @Override public void onClick(AjaxRequestTarget target) {table.click(target,List.of(new Row("b",State.OPEN)));}
                    });
                }
                private void markPaid"""))
            File(example,"DialogBox.html").writeText(WicketInitialContentFixture.hostMarkup)
            File(example,"ConfirmationPage.html").writeText("<html xmlns:wicket='http://wicket.apache.org'><body>"+WicketConfirmationFixture.markup+"<a wicket:id='rowPaid'>row</a><a wicket:id='bulkPaid'>bulk</a></body></html>")
            File(example,"Oracle.java").writeText("""
                package example;public class Oracle {
                    public static void main(String[] args) {
                        var tester=new org.apache.wicket.util.tester.WicketTester();
                        try {
                            var page=new ConfirmationPage();tester.startPage(page);
                            if(ConfirmationPage.transforms!=0 || page.snapshot().stream().anyMatch(r->r.state()!=ConfirmationPage.State.OPEN)) throw new AssertionError("Construction/render must not invoke the registered record updater");
                            tester.executeAjaxEvent("rowPaid","click");
                            if(ConfirmationPage.transforms!=1 || page.snapshot().get(0).state()!=ConfirmationPage.State.PAID || page.snapshot().get(1).state()!=ConfirmationPage.State.OPEN) throw new AssertionError("The row fixture callback updates only its matching record");
                            tester.assertComponentOnAjaxResponse("rows");
                            tester.executeAjaxEvent("bulkPaid","click");
                            if(ConfirmationPage.transforms!=2 || page.snapshot().stream().anyMatch(r->r.state()!=ConfirmationPage.State.PAID)) throw new AssertionError("The bulk fixture callback reaches the same record helper and table refresh");
                            tester.assertComponentOnAjaxResponse("rows");
                            ConfirmationPage.match=false;
                            var unmatched=new ConfirmationPage();tester.startPage(unmatched);tester.executeAjaxEvent("rowPaid","click");
                            if(ConfirmationPage.transforms!=2 || unmatched.snapshot().stream().anyMatch(r->r.state()!=ConfirmationPage.State.OPEN)) throw new AssertionError("An invoked action with a false equality predicate need not construct an updated record");
                            tester.assertComponentOnAjaxResponse("rows");
                        } finally {tester.destroy();}
                    }
                }
            """)
            val classpath=listOf("libraries","springTestLibraries").flatMap {System.getProperty("wicketlens.test.$it").split(File.pathSeparator)}.distinct().joinToString(File.pathSeparator)
            fun run(command:List<String>,name:String) {
                val log=File(directory,name);val process=ProcessBuilder(command).directory(directory).redirectErrorStream(true).redirectOutput(log).start()
                if(!process.waitFor(45,TimeUnit.SECONDS)) {process.destroyForcibly();fail("Record action oracle timed out: ${log.readText()}")}
                assertEquals(log.readText(),0,process.exitValue())
            }
            val compiler=System.getProperty("wicketlens.test.javac")
            run(listOf(compiler,"--release","21","-cp",classpath,"-d",directory.path)+listOf("DialogBox.java","ConfirmBox.java","Actions.java","SelectionBox.java","TableBox.java","ConfirmationPage.java","Oracle.java").map {File(example,it).path},"compile.log")
            run(listOf(File(File(compiler).parentFile,"java").path,"-cp",directory.path+File.pathSeparator+classpath,"example.Oracle"),"runtime.log")
        } finally {directory.deleteRecursively()}
    }
}
