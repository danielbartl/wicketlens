package dev.wicketlens

import com.intellij.openapi.actionSystem.ActionUiKind
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext

internal fun reportActionEvent(action: AnAction): AnActionEvent = AnActionEvent.createEvent(action,
    DataContext.EMPTY_CONTEXT, action.templatePresentation.clone(), "WicketLens.Test", ActionUiKind.NONE, null)

internal fun performReportAction(action: AnAction) {
    val event = reportActionEvent(action)
    action.update(event)
    check(event.presentation.isEnabled) { "Action is disabled: ${event.presentation.text}" }
    action.actionPerformed(event)
}
