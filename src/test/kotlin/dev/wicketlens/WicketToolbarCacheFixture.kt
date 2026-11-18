package dev.wicketlens

import com.intellij.psi.*
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.testFramework.fixtures.CodeInsightTestFixture

internal object WicketToolbarCacheFixture {
    val shell=WicketTableColumnsReadFixture.shell.replace("public TableShell configure(){new EmptyToolbar(table);return this;}","""
        private EmptyToolbar cache;
        public TableShell configure(){
            if(cache==null){cache=new EmptyToolbar(table);table.addBottomToolbar(cache);}
            return this;
        }
    """.trimIndent())
    fun install(fixture:CodeInsightTestFixture):PsiJavaFile {
        val java=WicketTableColumnsReadFixture.install(fixture)
        val file=PsiManager.getInstance(fixture.project).findFile(fixture.findFileInTempDir("src/main/java/example/TableShell.java"))!!
        WriteCommandAction.runWriteCommandAction(fixture.project) {PsiDocumentManager.getInstance(fixture.project).getDocument(file)!!.setText(shell)}
        PsiDocumentManager.getInstance(fixture.project).commitAllDocuments();return java
    }
}
