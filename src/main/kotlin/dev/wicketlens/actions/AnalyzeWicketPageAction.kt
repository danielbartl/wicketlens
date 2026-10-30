package dev.wicketlens.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.psi.PsiJavaFile
import dev.wicketlens.ui.WicketLensService

/** Capture only source identity on the EDT; PSI analysis runs in the project service. */
class AnalyzeWicketPageAction : DumbAwareAction() {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(event: AnActionEvent) {
        event.presentation.isEnabledAndVisible = event.project != null &&
            event.getData(CommonDataKeys.PSI_FILE) is PsiJavaFile
    }

    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return
        val file = event.getData(CommonDataKeys.VIRTUAL_FILE) ?: return
        project.service<WicketLensService>().analyze(file.url, event.getData(CommonDataKeys.EDITOR)?.caretModel?.offset ?: -1)
        ToolWindowManager.getInstance(project).getToolWindow("WicketLens")?.activate(null)
    }
}
