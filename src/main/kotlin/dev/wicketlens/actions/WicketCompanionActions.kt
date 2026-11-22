package dev.wicketlens.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogBuilder
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import com.intellij.util.IncorrectOperationException
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.ui.FormBuilder
import dev.wicketlens.analysis.AnalysisUnavailable
import dev.wicketlens.analysis.WicketPageResolver
import dev.wicketlens.analysis.WicketPageSelection
import dev.wicketlens.model.MarkupResolutionStatus

class SwitchWicketCompanionAction : WicketEditorFileAction("Switch Wicket Java/Markup", setOf("java", "html")) {
    override fun perform(project: Project, source: VirtualFile, offset: Int) {
        prepare(project, title, { WicketCompanionNavigation.destinations(project, source, offset) }) { destinations ->
            val target = choose(project, title, "Open markup file", destinations) ?: return@prepare
            open(project, target.file, target.offset)
        }
    }
}

class CreateWicketMarkupAction : WicketEditorFileAction("Create Wicket Markup", setOf("java")) {
    override fun available(project: Project, file: VirtualFile, offset: Int): Boolean = try {
        val owner = WicketPageSelection().resolve(project, file.url, offset, null)
        WicketPageResolver().resolve(owner).status == MarkupResolutionStatus.MISSING
    } catch (_: AnalysisUnavailable) {
        false
    }

    override fun perform(project: Project, source: VirtualFile, offset: Int) {
        prepare(project, title, { WicketComponentFiles.markupPlan(project, source, offset) }) { plan ->
            val target = choose(project, title, "Create markup at", plan.destinations) ?: return@prepare
            open(project, WicketComponentFiles.createMarkup(project, plan, target).virtualFile)
        }
    }
}

abstract class WicketEditorFileAction(protected val title: String, private val extensions: Set<String>) : AnAction() {
    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    override fun update(event: AnActionEvent) {
        val project = event.project
        val file = event.getData(CommonDataKeys.VIRTUAL_FILE)
        event.presentation.isEnabledAndVisible = project != null && file != null &&
            file.extension in extensions && !DumbService.isDumb(project) &&
            available(project, file, event.getData(CommonDataKeys.EDITOR)?.caretModel?.offset ?: -1)
    }

    final override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return
        val source = event.getData(CommonDataKeys.VIRTUAL_FILE) ?: return
        val offset = event.getData(CommonDataKeys.EDITOR)?.caretModel?.offset ?: -1
        perform(project, source, offset)
    }

    protected abstract fun perform(project: Project, source: VirtualFile, offset: Int)

    protected open fun available(project: Project, file: VirtualFile, offset: Int) = true
}

class NewWicketPageAction : NewWicketComponentAction(WicketComponentFiles.Kind.PAGE)
class NewWicketPanelAction : NewWicketComponentAction(WicketComponentFiles.Kind.PANEL)

abstract class NewWicketComponentAction internal constructor(private val kind: WicketComponentFiles.Kind) : AnAction() {
    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    override fun update(event: AnActionEvent) {
        val project = event.project
        val directory = event.getData(CommonDataKeys.VIRTUAL_FILE)
        event.presentation.isEnabledAndVisible = project != null && directory != null &&
            WicketComponentFiles.isJavaSourceDirectory(project, directory)
    }

    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return
        val directory = event.getData(CommonDataKeys.VIRTUAL_FILE) ?: return
        val title = "New Wicket ${kind.uiName}"
        val name = Messages.showInputDialog(project, "Java class name", title, null)?.trim() ?: return
        prepare(project, title, {
            val source = PsiManager.getInstance(project).findDirectory(directory)
                ?: throw AnalysisUnavailable("Select a Java package directory in the Project view.")
            WicketComponentFiles.componentPlan(source, name, kind)
        }) { plan ->
            val target = choose(project, title, "Create markup at", plan.destinations) ?: return@prepare
            open(project, WicketComponentFiles.createComponent(project, plan, target).virtualFile)
        }
    }
}

internal fun <T> prepare(project: Project, title: String, read: () -> T, show: (T) -> Unit) {
    ReadAction.nonBlocking<Result<T>> {
        try {
            Result.success(read())
        } catch (failure: AnalysisUnavailable) {
            Result.failure(failure)
        }
    }.inSmartMode(project).withDocumentsCommitted(project).expireWith(project)
        .finishOnUiThread(ModalityState.defaultModalityState()) { result ->
            try {
                show(result.getOrThrow())
            } catch (failure: AnalysisUnavailable) {
                Messages.showErrorDialog(project, failure.message ?: "The Wicket source is unavailable.", title)
            } catch (failure: IncorrectOperationException) {
                Messages.showErrorDialog(project, failure.message ?: "The file operation failed.", title)
            }
        }.submit(AppExecutorUtil.getAppExecutorService())
}

internal fun <T> choose(project: Project, title: String, message: String, choices: List<T>): T? {
    choices.singleOrNull()?.let { return it }
    val options = ComboBox(choices.map { it.toString() }.toTypedArray())
    val dialog = DialogBuilder(project)
        .title(title)
        .centerPanel(FormBuilder.createFormBuilder().addLabeledComponent(message, options).panel)
    dialog.setPreferredFocusComponent(options)
    return if (dialog.showAndGet()) choices.getOrNull(options.selectedIndex) else null
}

private fun open(project: Project, file: VirtualFile, offset: Int = 0) {
    if (!file.isValid) throw AnalysisUnavailable("The destination file is no longer available.")
    OpenFileDescriptor(project, file, offset).navigate(true)
}
