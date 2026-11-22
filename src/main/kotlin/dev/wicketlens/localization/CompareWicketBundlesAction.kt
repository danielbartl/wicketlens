package dev.wicketlens.localization

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.ui.DialogBuilder
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiManager
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import dev.wicketlens.actions.prepare
import dev.wicketlens.analysis.WicketSourceClassNames
import dev.wicketlens.analysis.WicketSourceResources
import dev.wicketlens.editor.WicketEditorAnalysis
import java.awt.Dimension

class CompareWicketBundlesAction : AnAction() {
    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    override fun update(event: AnActionEvent) {
        event.presentation.isEnabledAndVisible = event.project != null &&
            event.getData(CommonDataKeys.VIRTUAL_FILE)?.extension in setOf("java", "html")
    }

    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return
        val file = event.getData(CommonDataKeys.VIRTUAL_FILE) ?: return
        val offset = event.getData(CommonDataKeys.EDITOR)?.caretModel?.offset ?: 0
        val title = "Compare Wicket Bundle Keys"
        prepare(project, title, {
            val psi = PsiManager.getInstance(project).findFile(file)
            val owner = psi?.takeIf(WicketEditorAnalysis::ready)?.let {
                if (file.extension == "html") WicketMessageSources.markupOwner(it)
                else generateSequence(it.findElementAt(offset.coerceIn(0, (it.textLength - 1).coerceAtLeast(0)))) { it.parent }
                    .filterIsInstance<PsiClass>().firstOrNull { type ->
                        WicketSourceResources.isComponent(type) && WicketSourceClassNames.binaryBasename(type) != null
                    }
            }
            if (owner == null) "Place the caret inside a named Wicket component or open its paired markup."
            else compare(owner)
        }) { comparison ->
            val text = JBTextArea(comparison).apply { isEditable = false; caretPosition = 0 }
            DialogBuilder(project).apply {
                setTitle(title)
                setCenterPanel(JBScrollPane(text).apply { preferredSize = Dimension(640, 400) })
                addOkAction()
                show()
            }
        }
    }

    internal fun compare(owner: PsiClass): String {
        val bundles = WicketMessageSources.bundles(owner, requireComplete = true)
        if (bundles.isEmpty()) return "No comparable conventional source bundles were found for ${owner.qualifiedName}."
        val keys = bundles.associateWith { bundle ->
            bundle.properties.mapNotNull {
                ProgressManager.checkCanceled()
                it.unescapedKey
            }.toSortedSet()
        }
        val defaultName = "${WicketSourceClassNames.binaryBasename(owner)}.properties"
        val base = bundles.singleOrNull { it.name == defaultName }
        val reference = base?.let(keys::getValue) ?: keys.values.flatten().toSortedSet()
        return buildString {
            appendLine(owner.qualifiedName)
            appendLine(if (base == null) "Reference is the union of the source bundles." else "Reference is ${base.name}.")
            appendLine("A key absent from a variant can be inherited at runtime.")
            for ((bundle, present) in keys) {
                ProgressManager.checkCanceled()
                appendLine()
                appendLine(bundle.virtualFile.path)
                appendDifference("Absent", reference - present)
                if (base != null) appendDifference("Variant-only", present - reference)
            }
        }
    }

    private fun StringBuilder.appendDifference(label: String, keys: Set<String>) {
        appendLine("$label (${keys.size})")
        keys.asSequence().take(200).forEach { appendLine("  ${it.replace("\n", "\\n").replace("\r", "\\r")}") }
        if (keys.size > 200) appendLine("  … ${keys.size - 200} more")
    }
}
