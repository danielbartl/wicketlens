package dev.wicketlens.localization

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.ui.InputValidator
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.TextRange
import dev.wicketlens.actions.choose
import dev.wicketlens.actions.prepare

class ExtractWicketMessageAction : AnAction() {
    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    override fun update(event: AnActionEvent) {
        val editor = event.getData(CommonDataKeys.EDITOR)
        val extension = event.getData(CommonDataKeys.VIRTUAL_FILE)?.extension
        event.presentation.isEnabledAndVisible = event.project != null && editor != null &&
            editor.caretModel.caretCount == 1 && editor.selectionModel.hasSelection() &&
            (extension == "html" || extension == "java")
    }

    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return
        val source = event.getData(CommonDataKeys.VIRTUAL_FILE) ?: return
        val editor = event.getData(CommonDataKeys.EDITOR) ?: return
        if (editor.caretModel.caretCount != 1 || !editor.selectionModel.hasSelection()) return
        val selection = TextRange(editor.selectionModel.selectionStart, editor.selectionModel.selectionEnd)
        val title = WicketMessageExtraction.TITLE
        prepare(project, title, { WicketMessageExtraction.prepare(project, source, selection) }) { plan ->
            val destination = choose(project, title, "Default message bundle", plan.destinations) ?: return@prepare
            val key = Messages.showInputDialog(
                project, "Message key for $destination", title, null, "",
                object : InputValidator {
                    override fun checkInput(inputString: String) = WicketMessageExtraction.validKey(inputString)
                    override fun canClose(inputString: String) = checkInput(inputString)
                },
            ) ?: return@prepare
            WicketMessageExtraction.extract(project, plan, destination, key)
        }
    }
}
