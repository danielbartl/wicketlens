package dev.wicketlens.actions

import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.project.DumbAwareAction
import dev.wicketlens.spring.WicketSpringTestService

class GenerateWicketTestAction : DumbAwareAction() {
    override fun getActionUpdateThread() = ActionUpdateThread.BGT
    override fun update(event: AnActionEvent) {
        event.presentation.isEnabledAndVisible = event.project != null &&
            event.getData(CommonDataKeys.VIRTUAL_FILE)?.extension in setOf("java", "html")
    }

    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return
        val file = event.getData(CommonDataKeys.VIRTUAL_FILE) ?: return
        project.getService(WicketSpringTestService::class.java).generateGeneral(file.url, event.getData(CommonDataKeys.EDITOR)?.caretModel?.offset ?: -1)
    }
}
