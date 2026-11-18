package dev.wicketlens

import com.intellij.psi.PsiJavaFile
import com.intellij.testFramework.fixtures.CodeInsightTestFixture

internal object WicketToolbarRegistrationFixture {
    val shell=WicketDataTableFixture.shell
        .replace("public TableShell(String id){super(id);add(new RowsTable(\"table\",List.of(),new ListDataProvider<String>(List.of(\"row\")),10));}","""
            private final DataTable<String,String> table;
            private final class Header extends org.apache.wicket.extensions.ajax.markup.html.repeater.data.table.AjaxFallbackHeadersToolbar<String> {
                Header(DataTable<String,String> table,org.apache.wicket.extensions.markup.html.repeater.data.sort.ISortStateLocator<String> locator){super(table,locator);}
                @Override protected void onBeforeRender(){super.onBeforeRender();}
            }
            public TableShell(String id){super(id);
                table=new RowsTable("table",List.of(),new ListDataProvider<String>(List.of("row")),10);
                table.setOutputMarkupId(true);
                table.addTopToolbar(new Header(table,null));
                table.addBottomToolbar(new Header(table,null));
                add(table);
            }
        """.trimIndent())
    fun install(fixture:CodeInsightTestFixture):PsiJavaFile {
        val page=WicketDataTableFixture.install(fixture)
        val file=com.intellij.psi.PsiManager.getInstance(fixture.project).findFile(fixture.findFileInTempDir("src/main/java/example/TableShell.java"))!!
        com.intellij.openapi.command.WriteCommandAction.runWriteCommandAction(fixture.project) {com.intellij.psi.PsiDocumentManager.getInstance(fixture.project).getDocument(file)!!.setText(shell)}
        com.intellij.psi.PsiDocumentManager.getInstance(fixture.project).commitAllDocuments()
        return page
    }
}
