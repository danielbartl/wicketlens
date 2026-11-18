package dev.wicketlens

import com.intellij.psi.PsiJavaFile
import com.intellij.testFramework.fixtures.CodeInsightTestFixture

internal object WicketRecordActionFixture {
    val plainPage="""
        package example;
        import java.util.*;import org.apache.wicket.markup.html.*;
        public class ConfirmationPage extends WebPage {
            enum State {OPEN,PAID}
            public record Row(String id,State state) implements java.io.Serializable {Row paid(){return new Row(id,State.PAID);}}
            private final List<Row> records=new ArrayList<>();
            public ConfirmationPage() {
                WebMarkupContainer table=new WebMarkupContainer("rows");add(table);
                Actions<Row> actions=new Actions<>();
                actions.addAction("Paid",(target,row)->{markPaid(List.of(row));target.add(table);});
            }
            private void markPaid(List<Row> rows) {for(Row row:rows) records.replaceAll(current->current.equals(row)?current.paid():current);}
        }
    """
    val repeatedPage=plainPage
        .replace("actions.addAction", "actions.addAction(\"First\",(target,row)->{markOpen(List.of(row));target.add(table);});actions.addAction")
        .replace("Row paid(){return new Row(id,State.PAID);}", "Row paid(){return new Row(id,State.PAID);} Row opened(){return new Row(id,State.OPEN);}")
        .replace("private void markPaid", "private void markOpen(List<Row> rows) {for(Row row:rows) records.replaceAll(current->current.equals(row)?current.opened():current);} private void markPaid")
    val chainedPage=repeatedPage.replace("});actions.addAction", "}).addAction")
    val initializedPage=chainedPage.replace("Actions<Row> actions=new Actions<>();\n                actions.addAction", "Actions<Row> actions=new Actions<Row>().addAction")
    val page=WicketSelectionClearFixture.page
        .replace("public record Row(String id) implements java.io.Serializable {}", """
            public enum State {OPEN,PAID}
            public record Row(String id,State state) implements java.io.Serializable {
                Row paid() {return new Row(id,State.PAID);}
                @Override public boolean equals(Object other) {return other instanceof Row r && r.id.equals(id);}
                @Override public int hashCode() {return id.hashCode();}
            }
        """)
        .replace("            }\n            private void delete", """
                actions.addAction("Mark as paid",(target,row)->{markPaid(List.of(row));success("Paid");target.add(table);});
                table.addBulkAction("Mark as paid",(target,rows)->{markPaid(rows);success("Paid rows");table.clearSelection(target);});
            }
            private void markPaid(List<Row> rows) {for(Row row:rows) {records.replaceAll(current->current.equals(row)?current.paid():current);}}
            private void delete""")
    fun install(fixture:CodeInsightTestFixture,body:String=page,table:String=WicketSelectionClearFixture.table):PsiJavaFile =
        WicketSelectionClearFixture.install(fixture,body,table)
}
