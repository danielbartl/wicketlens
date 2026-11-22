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
import com.intellij.ui.table.JBTable
import dev.wicketlens.analysis.AnalysisUnavailable
import dev.wicketlens.testing.WicketMountedResource
import dev.wicketlens.testing.WicketMountedTests
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.GridLayout
import java.awt.datatransfer.StringSelection
import java.awt.event.ActionEvent
import javax.swing.*
import javax.swing.event.DocumentEvent
import javax.swing.table.DefaultTableModel

/** Review/copy is available even when the module has no configured Java test source root. */
class WicketSpringTestPreview(private val project: Project, private val prepared: PreparedSpringTest) : DialogWrapper(project) {
    private val name = JBTextField(prepared.plan.testName, 35)
    private val mediaType = prepared.plan.mounted?.resource?.let {
        JBTextField(it.expectedContentType.orEmpty(), 20).apply { emptyText.text = "e.g. image/svg+xml" }
    }
    private val bodyHash = prepared.plan.mounted?.resource?.let {
        JBTextField(it.expectedBodySha256.orEmpty(), 52).apply { emptyText.text = "64 hexadecimal characters" }
    }
    private val routeValues = prepared.plan.mounted?.parameters.orEmpty().mapValues { (_, value) -> JBTextField(value, 22) }
    private val roots = JComboBox(prepared.roots.toTypedArray())
    private val source = JBTextArea().apply {
        isEditable = false
        font = EditorColorsManager.getInstance().globalScheme.getFont(EditorFontType.PLAIN)
    }
    private val copy = object : DialogWrapperAction("Copy Scaffold") {
        override fun doAction(event: ActionEvent?) = attempt {
            WicketSpringTestFiles.requireCurrent(project, prepared)
            CopyPasteManager.getInstance().setContents(StringSelection(WicketSpringTestGenerator().render(currentPlan(), name.text)))
            close(OK_EXIT_CODE)
        }
    }

    init {
        title = when {
            mediaType != null -> "Generate Wicket Mounted Resource Test"
            prepared.plan.mounted != null -> "Generate Wicket Mounted Page Test"
            prepared.plan.spring -> "Generate Wicket Spring Test"
            else -> "Generate Wicket Test"
        }
        setOKButtonText("Create Test")
        val previewUpdate = object : DocumentAdapter() { override fun textChanged(event: DocumentEvent) = refresh() }
        name.document.addDocumentListener(previewUpdate)
        mediaType?.document?.addDocumentListener(previewUpdate)
        bodyHash?.document?.addDocumentListener(previewUpdate)
        routeValues.values.forEach { it.document.addDocumentListener(previewUpdate) }
        init()
        refresh()
    }

    private fun validName() = WicketSpringTestFiles.validName(project, name.text, prepared.plan.target) &&
        name.text != prepared.plan.mounted?.applicationClass?.substringAfterLast('.')

    private fun expectedContentType() = mediaType?.text?.trim()?.takeIf { it.isNotEmpty() }
    private fun expectedBodySha256() = bodyHash?.text?.trim()?.takeIf { it.isNotEmpty() }
    private fun routeExamples() = routeValues.mapValues { (_, field) -> field.text }

    private fun currentPlan(): SpringTestPlan {
        val route = prepared.plan.mounted ?: return prepared.plan
        val updated = when {
            route.resource != null -> route.copy(resource = WicketMountedResource(expectedContentType(), expectedBodySha256()))
            routeValues.isNotEmpty() -> WicketMountedTests.withExamples(route, routeExamples())
            else -> route
        }
        return prepared.plan.copy(mounted = updated)
    }

    private fun refresh() {
        val problem = doValidate()
        val valid = problem == null
        source.text = problem?.message ?: WicketSpringTestGenerator().render(currentPlan(), name.text)
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
        val sourceScroll = JBScrollPane(source).apply { preferredSize = Dimension(930, 560) }
        val graph = prepared.plan.dependencyGraph
        add(if (mediaType == null && routeValues.isEmpty() && graph == null) sourceScroll else JTabbedPane().apply {
            addTab("Scaffold", sourceScroll)
            if (routeValues.isNotEmpty()) addTab("Route parameters", fields(routeValues))
            if (mediaType != null && bodyHash != null) addTab("Resource assertions", fields(linkedMapOf(
                "Expected media type (optional)" to mediaType, "Expected SHA-256 (optional)" to bodyHash,
            )))
            if (graph != null) {
                val table = object : DefaultTableModel(arrayOf("Component", "Field", "Lookup", "Test binding"), 0) {
                    override fun isCellEditable(row: Int, column: Int) = false
                }
                graph.owners.forEach { owner -> owner.fields.forEach { field ->
                    table.addRow(arrayOf(owner.path, "${field.name} · ${field.type}", field.lookup.name,
                        field.mock?.let { "Mock ${it.name}" } ?: "Manual setup"))
                } }
                val graphPanel = JPanel(BorderLayout(0, 8)).apply {
                    if (graph.discoveryLimited) add(JBLabel("Source dependency discovery was limited."), BorderLayout.NORTH)
                    add(JBScrollPane(JBTable(table).apply { autoCreateRowSorter = true }), BorderLayout.CENTER)
                }
                addTab("Dependencies", graphPanel)
            }
        }, BorderLayout.CENTER)
        add(JBTextArea((prepared.plan.notes +
            (if (prepared.plan.manual.isNotEmpty()) listOf("Manual binding TODOs: ${prepared.plan.manual.size}. The generated guard must be replaced before the test runs.") else emptyList<String>()) +
            (if (prepared.roots.isEmpty()) listOf("No existing Java test root is configured. Copy the scaffold or configure a test root and generate again.") else emptyList<String>()))
            .joinToString("\n")).apply {
            isEditable = false; lineWrap = true; wrapStyleWord = true; isOpaque = false; columns = 100
        }, BorderLayout.SOUTH)
    }

    private fun fields(fields: Map<String, JBTextField>) = JPanel(BorderLayout()).apply {
        add(JPanel(GridLayout(0, 1, 0, 6)).apply {
            fields.forEach { (label, field) -> add(JPanel(java.awt.FlowLayout(java.awt.FlowLayout.LEADING)).apply {
                add(JBLabel(label).apply { labelFor = field })
                add(field)
            }) }
        }, BorderLayout.NORTH)
    }

    override fun createActions(): Array<Action> = arrayOf(okAction, copy, cancelAction)
    override fun getPreferredFocusedComponent(): JComponent = name
    override fun doValidate(): ValidationInfo? = when {
        !validName() -> ValidationInfo("Choose a valid Java class name different from the subject and scaffold imports.", name)
        !WicketMountedResource.validContentType(expectedContentType()) -> ValidationInfo("Enter a concrete media type without parameters.", mediaType)
        !WicketMountedResource.validBodySha256(expectedBodySha256()) -> ValidationInfo("Enter a SHA-256 hash of 64 hexadecimal characters.", bodyHash)
        else -> try {
            currentPlan()
            null
        } catch (invalid: AnalysisUnavailable) {
            ValidationInfo(invalid.message ?: "Check the route parameter values.")
        }
    }
    override fun doOKAction() = attempt {
        val root = roots.selectedItem as? SpringTestRoot ?: throw AnalysisUnavailable("Configure a Java test source root or copy the scaffold.")
        val created = WicketSpringTestFiles.create(project, prepared.copy(plan = currentPlan()), root, name.text)
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
