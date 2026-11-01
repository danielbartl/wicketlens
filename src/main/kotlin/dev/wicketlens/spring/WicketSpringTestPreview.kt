package dev.wicketlens.spring

import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.colors.EditorFontType
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.components.*
import dev.wicketlens.analysis.AnalysisUnavailable
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.datatransfer.StringSelection
import java.awt.event.ActionEvent
import javax.swing.*
import javax.swing.event.DocumentEvent

/** Review/copy is available even when the module has no configured Java test source root. */
class WicketSpringTestPreview(private val project: Project, private val prepared: PreparedSpringTest) : DialogWrapper(project) {
    private val name = JBTextField(prepared.plan.testName, 35)
    private val roots = JComboBox(prepared.roots.toTypedArray())
    private val source = JBTextArea().apply {
        isEditable = false
        font = EditorColorsManager.getInstance().globalScheme.getFont(EditorFontType.PLAIN)
    }
    private val copy = object : DialogWrapperAction("Copy Scaffold") {
        override fun doAction(event: ActionEvent?) = attempt {
            WicketSpringTestFiles.requireCurrent(project, prepared)
            CopyPasteManager.getInstance().setContents(StringSelection(WicketSpringTestGenerator().render(prepared.plan, name.text)))
            close(OK_EXIT_CODE)
        }
    }

    init {
        title = if (prepared.plan.spring) "Generate Wicket Spring Test" else "Generate Wicket Test"
        setOKButtonText("Create Test")
        name.document.addDocumentListener(object : DocumentAdapter() { override fun textChanged(event: DocumentEvent) = refresh() })
        init()
        refresh()
    }

    private fun refresh() {
        val valid = WicketSpringTestFiles.validName(project, name.text, prepared.plan.target)
        source.text = if (valid) WicketSpringTestGenerator().render(prepared.plan, name.text) else "Enter a valid Java test class name to preview the scaffold."
        source.caretPosition = 0
        isOKActionEnabled = valid && prepared.roots.isNotEmpty()
        copy.isEnabled = valid
    }

    override fun createCenterPanel(): JComponent = JPanel(BorderLayout(0, 8)).apply {
        val controls = JPanel().apply {
            add(JBLabel("Test class:")); add(this@WicketSpringTestPreview.name)
            if (prepared.roots.isNotEmpty()) { add(JBLabel("Test root:")); add(roots) }
        }
        add(controls, BorderLayout.NORTH)
        add(JBScrollPane(source).apply { preferredSize = Dimension(930, 560) }, BorderLayout.CENTER)
        add(JBTextArea((prepared.plan.notes +
            (if (prepared.plan.manual.isNotEmpty()) listOf("Manual binding TODOs: ${prepared.plan.manual.size}. The generated guard must be replaced before the test runs.") else emptyList<String>()) +
            (if (prepared.roots.isEmpty()) listOf("No existing Java test root is configured. Copy the scaffold or configure a test root and generate again.") else emptyList<String>()))
            .joinToString("\n")).apply {
            isEditable = false; lineWrap = true; wrapStyleWord = true; isOpaque = false; columns = 100
        }, BorderLayout.SOUTH)
    }

    override fun createActions(): Array<Action> = arrayOf(okAction, copy, cancelAction)
    override fun getPreferredFocusedComponent(): JComponent = name
    override fun doValidate(): ValidationInfo? = if (WicketSpringTestFiles.validName(project, name.text, prepared.plan.target)) null else ValidationInfo("Choose a valid Java class name different from the subject and scaffold imports.", name)
    override fun doOKAction() = attempt {
        val root = roots.selectedItem as? SpringTestRoot ?: throw AnalysisUnavailable("Configure a Java test source root or copy the scaffold.")
        val created = WicketSpringTestFiles.create(project, prepared, root, name.text)
        close(OK_EXIT_CODE)
        created.navigate(true)
    }

    private fun attempt(block: () -> Unit) {
        if (doValidate() != null) return
        try { block() } catch (unavailable: AnalysisUnavailable) {
            Messages.showInfoMessage(project, unavailable.message, title)
        }
    }
}
