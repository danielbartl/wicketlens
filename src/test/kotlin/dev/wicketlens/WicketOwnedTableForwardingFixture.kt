package dev.wicketlens

import com.intellij.psi.*
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.testFramework.fixtures.CodeInsightTestFixture

internal object WicketOwnedTableForwardingFixture {
    val shell=WicketDataTableFixture.shell.replace(
        "public TableShell(String id){super(id);add(new RowsTable(\"table\",List.of(),new ListDataProvider<String>(List.of(\"row\")),10));}","""
            private final DataTable<String,String> table;
            private final class EmptyToolbar extends AbstractToolbar {
                private final org.apache.wicket.markup.html.WebMarkupContainer cell;
                EmptyToolbar(DataTable<String,String> table){super(table);cell=new org.apache.wicket.markup.html.WebMarkupContainer("cell");add(cell);}
                @Override protected void onConfigure(){super.onConfigure();setVisible(getTable().getRowCount()==0);}
            }
            public TableShell(String id){super(id);table=new RowsTable("table",List.of(),new ListDataProvider<String>(List.of("row")),10);add(table);}
            public TableShell configure(){new EmptyToolbar(table);return this;}
        """.trimIndent())
    fun install(fixture:CodeInsightTestFixture):PsiJavaFile {
        val java=WicketDataTableFixture.install(fixture)
        val file=PsiManager.getInstance(fixture.project).findFile(fixture.findFileInTempDir("src/main/java/example/TableShell.java"))!!
        WriteCommandAction.runWriteCommandAction(fixture.project) {
            PsiDocumentManager.getInstance(fixture.project).getDocument(file)!!.setText(shell)
            PsiDocumentManager.getInstance(fixture.project).getDocument(java)!!.setText(java.text.replace("new TableShell(\"shell\")","new TableShell(\"shell\").configure()"))
        }
        PsiDocumentManager.getInstance(fixture.project).commitAllDocuments()
        return java
    }
}
