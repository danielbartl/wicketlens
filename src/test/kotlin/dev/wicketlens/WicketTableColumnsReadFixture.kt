package dev.wicketlens

import com.intellij.psi.*
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.testFramework.fixtures.CodeInsightTestFixture

internal object WicketTableColumnsReadFixture {
    val shell=WicketOwnedTableForwardingFixture.shell.replace("add(cell);}","cell.add(org.apache.wicket.AttributeModifier.replace(\"colspan\",(org.apache.wicket.model.IModel<Integer>)TableShell.this::columns));add(cell);}")
        .replace("public TableShell configure()","private int columns(){java.util.List<?> columns=table.getColumns();return columns.size();}public TableShell configure()")
    fun install(fixture:CodeInsightTestFixture):PsiJavaFile {
        val java=WicketOwnedTableForwardingFixture.install(fixture)
        val file=PsiManager.getInstance(fixture.project).findFile(fixture.findFileInTempDir("src/main/java/example/TableShell.java"))!!
        WriteCommandAction.runWriteCommandAction(fixture.project) {PsiDocumentManager.getInstance(fixture.project).getDocument(file)!!.setText(shell)}
        PsiDocumentManager.getInstance(fixture.project).commitAllDocuments();return java
    }
}
