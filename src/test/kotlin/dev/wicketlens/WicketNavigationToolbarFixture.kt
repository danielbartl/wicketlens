package dev.wicketlens

import com.intellij.psi.PsiJavaFile
import com.intellij.testFramework.fixtures.CodeInsightTestFixture

internal object WicketNavigationToolbarFixture {
    val shell=WicketToolbarRegistrationFixture.shell.replace("public TableShell(String id)","""
        private final class Pager extends org.apache.wicket.ajax.markup.html.navigation.paging.AjaxPagingNavigator {
            Pager(String id,org.apache.wicket.markup.html.navigation.paging.IPageable pageable){super(id,pageable);}
            @Override protected org.apache.wicket.markup.html.navigation.paging.PagingNavigation newNavigation(String id,org.apache.wicket.markup.html.navigation.paging.IPageable pageable,org.apache.wicket.markup.html.navigation.paging.IPagingLabelProvider labels){return super.newNavigation(id,pageable,labels);}
        }
        private final class Nav extends NavigationToolbar {
            Nav(DataTable<?,?> table){super(table);table.setOutputMarkupId(true);}
            @Override protected org.apache.wicket.markup.html.navigation.paging.PagingNavigator newPagingNavigator(String id,DataTable<?,?> table){return new Pager(id,table){@Override protected void onAjaxEvent(org.apache.wicket.ajax.AjaxRequestTarget target){target.add(table);}};}
        }
        public TableShell(String id)
    """.trimIndent()).replace("table.addBottomToolbar(new Header(table,null));","table.addBottomToolbar(new Nav(table));")
    fun install(fixture:CodeInsightTestFixture):PsiJavaFile {
        val page=WicketToolbarRegistrationFixture.install(fixture)
        val file=com.intellij.psi.PsiManager.getInstance(fixture.project).findFile(fixture.findFileInTempDir("src/main/java/example/TableShell.java"))!!
        com.intellij.openapi.command.WriteCommandAction.runWriteCommandAction(fixture.project) {com.intellij.psi.PsiDocumentManager.getInstance(fixture.project).getDocument(file)!!.setText(shell)}
        com.intellij.psi.PsiDocumentManager.getInstance(fixture.project).commitAllDocuments()
        return page
    }
}
