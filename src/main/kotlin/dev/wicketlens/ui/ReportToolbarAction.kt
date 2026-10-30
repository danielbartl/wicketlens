package dev.wicketlens.ui

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import javax.swing.Icon

/** Toolbar and popup actions read the same current panel state before updating or executing. */
class ReportToolbarAction(text: String, description: String, icon: Icon,
    private val enabled: () -> Boolean, private val execute: () -> Unit,
) : DumbAwareAction(text, description, icon) {
    val isEnabled: Boolean get() = enabled()
    override fun getActionUpdateThread() = ActionUpdateThread.EDT
    override fun update(event: AnActionEvent) { event.presentation.isEnabled = isEnabled }
    override fun actionPerformed(event: AnActionEvent) { if (isEnabled) execute() }
}
