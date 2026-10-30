package dev.wicketlens.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import dev.wicketlens.ui.WicketLensService

class CopyWicketContextAction : DumbAwareAction() {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(event: AnActionEvent) {
        val state = event.project?.getServiceIfCreated(WicketLensService::class.java)?.state
        event.presentation.isEnabledAndVisible = state?.prepared != null && !state.running && !state.stale
    }

    override fun actionPerformed(event: AnActionEvent) {
        event.project?.getServiceIfCreated(WicketLensService::class.java)?.copyContext()
    }
}
