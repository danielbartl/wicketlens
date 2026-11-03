package dev.wicketlens.routes

import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.SearchTextField
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.table.JBTable
import com.intellij.util.ui.JBUI
import dev.wicketlens.ui.ReportToolbarAction
import java.awt.BorderLayout
import java.awt.event.ActionEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.AbstractAction
import javax.swing.JPanel
import javax.swing.KeyStroke
import javax.swing.ListSelectionModel
import javax.swing.event.DocumentEvent
import javax.swing.table.AbstractTableModel
import javax.swing.table.DefaultTableCellRenderer

data class WicketMountRow(val application: WicketRouteApplication, val mount: WicketMount)

/** Value-only project view, independent of the Components editor-following selection. */
class WicketRoutesPanel(project: Project, val service: WicketRoutesService, private val showComponents: () -> Unit = {}) : JPanel(BorderLayout()), Disposable {
    val search = SearchTextField(false).apply { textEditor.emptyText.text = "Filter paths, pages, or applications" }
    val modules = ComboBox((listOf("All modules") + ModuleManager.getInstance(project).modules.map { it.name }.sorted()).toTypedArray())
    val applications = ComboBox(arrayOf("All applications"))
    val sourceSet = ComboBox(arrayOf("main", "test", "any"))
    val details = JBTextArea().apply { isEditable = false; lineWrap = true; wrapStyleWord = true }
    val summary = JBLabel("Static declarations; active runtime routes are not verified.")
    val table = JBTable().apply { selectionModel.selectionMode = ListSelectionModel.SINGLE_SELECTION; emptyText.text = "Refresh to discover mounted URLs." }
    private var state = WicketRoutesState()
    private var updating = false
    var rows: List<WicketMountRow> = emptyList()
        private set
    private val model = object : AbstractTableModel() {
        private val headings = listOf("Mounted path", "Page", "Application", "Module", "Status")
        override fun getRowCount() = rows.size
        override fun getColumnCount() = headings.size
        override fun getColumnName(column: Int) = headings[column]
        override fun getValueAt(row: Int, column: Int): Any = rows[row].let {
            when (column) { 0 -> it.mount.path ?: "<unresolved path>"; 1 -> it.mount.pageClass ?: "<unresolved page>"
                2 -> it.application.className; 3 -> it.application.moduleName; else -> it.mount.status.name }
        }
    }
    val refreshAction = ReportToolbarAction("Refresh mounted URLs", "Discover direct page mount declarations in this project", AllIcons.Actions.Refresh,
        { !state.running }, { service.refresh(selected(modules, "All modules"), selected(applications, "All applications"), sourceSet.selectedItem as String) })
    val cancelAction = ReportToolbarAction("Cancel", "Cancel mounted URL discovery", AllIcons.Actions.Suspend, { state.running }, service::cancel)
    val pageAction = ReportToolbarAction("Go to page", "Open the mounted page class", AllIcons.FileTypes.Java,
        { ready() && selectedRow()?.mount?.pageSource != null }, { selectedRow()?.mount?.pageSource?.let(service::navigate) })
    val htmlAction = ReportToolbarAction("Go to HTML", "Open the page's resolved conventional HTML companion", AllIcons.FileTypes.Html,
        { ready() && selectedRow()?.mount?.markupSource != null }, { selectedRow()?.mount?.markupSource?.let(service::navigate) })
    val registrationAction = ReportToolbarAction("Go to registration", "Open the route registration", AllIcons.Nodes.Method,
        { ready() && selectedRow() != null }, { selectedRow()?.mount?.registration?.let(service::navigate) })
    val analyzeAction = ReportToolbarAction("Analyze page", "Analyze this page in the Components view", AllIcons.Actions.ShowAsTree,
        { ready() && selectedRow()?.mount?.analyzablePage == true }, {
            selectedRow()?.mount?.let { if (service.analyze(it)) showComponents() }
        })
    private val toolbar = ActionManager.getInstance().createActionToolbar("WicketLens.Routes",
        DefaultActionGroup(refreshAction, cancelAction, pageAction, htmlAction, registrationAction, analyzeAction), true)

    init {
        table.model = model
        // Source/path labels are literal text, including strings beginning with <html>.
        table.setDefaultRenderer(Any::class.java, object : DefaultTableCellRenderer() {
            override fun setValue(value: Any?) { putClientProperty("html.disable", true); text = display(value.toString()) }
        })
        toolbar.targetComponent = table
        val filters = JPanel(java.awt.GridLayout(1, 4, JBUI.scale(4), 0)).apply {
            add(search); add(modules); add(applications); add(sourceSet)
        }
        add(JPanel(BorderLayout()).apply { add(toolbar.component, BorderLayout.NORTH); add(filters, BorderLayout.SOUTH) }, BorderLayout.NORTH)
        val splitter = com.intellij.ui.OnePixelSplitter(true, "WicketLens.routes.details", 0.75f).apply {
            firstComponent = JBScrollPane(table); secondComponent = JBScrollPane(details)
        }
        add(splitter, BorderLayout.CENTER); add(summary, BorderLayout.SOUTH)
        search.addDocumentListener(object : DocumentAdapter() { override fun textChanged(event: DocumentEvent) { filter() } })
        modules.addActionListener { if (!updating) filter() }
        applications.addActionListener { if (!updating) filter() }
        sourceSet.addActionListener { if (!updating) filter() }
        table.selectionModel.addListSelectionListener { showDetails(); toolbar.updateActionsAsync() }
        table.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(event: MouseEvent) {
                if (event.clickCount == 2 && event.button == MouseEvent.BUTTON1 && table.rowAtPoint(event.point) == table.selectedRow) openPage()
            }
        })
        table.inputMap.put(KeyStroke.getKeyStroke("ENTER"), "wicketlens.routes.open")
        table.actionMap.put("wicketlens.routes.open", object : AbstractAction() { override fun actionPerformed(event: ActionEvent) { openPage() } })
        service.listen(this) { next ->
            state = next
            val choice = applications.selectedItem
            if (next.snapshot != null) {
                updating = true
                try {
                    applications.removeAllItems(); applications.addItem("All applications")
                    next.snapshot.routes.applications.map { it.className }.distinct().sorted().forEach(applications::addItem)
                    if ((0 until applications.itemCount).any { applications.getItemAt(it) == choice }) applications.selectedItem = choice
                } finally { updating = false }
            }
            filter()
        }
        if (service.state.snapshot == null && !service.state.running) service.refresh()
    }

    fun selectedRow(): WicketMountRow? = rows.getOrNull(table.selectedRow)
    fun filter() {
        val previous = selectedRow()?.mount?.id
        val terms = search.text.trim().lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
        val module = selected(modules, "All modules")
        val application = selected(applications, "All applications")
        val set = sourceSet.selectedItem as String
        rows = state.snapshot?.routes?.applications.orEmpty().filter {
            (module == null || it.moduleName == module) && (application == null || it.className == application) && (set == "any" || it.sourceSet == set)
        }.flatMap { app -> app.mounts.map { WicketMountRow(app, it) } }.filter { row ->
            val text = listOf(row.mount.path, row.mount.pageClass, row.application.className, row.application.moduleName, row.mount.status.name).joinToString(" ").lowercase()
            terms.all(text::contains)
        }
        model.fireTableDataChanged()
        rows.indexOfFirst { it.mount.id == previous }.takeIf { it >= 0 }?.let { table.setRowSelectionInterval(it, it) }
        summary.text = display(state.message + if (state.snapshot?.routes?.truncated == true) " — discovery limits reached" else "")
        summary.putClientProperty("html.disable", true)
        table.emptyText.text = if (state.snapshot == null) state.message else "No matching mount declarations."
        showDetails(); toolbar.updateActionsAsync()
    }
    private fun ready() = !state.running && !state.stale && state.snapshot != null
    private fun openPage() { if (ready()) selectedRow()?.mount?.pageSource?.let(service::navigate) }
    private fun showDetails() {
        val row = selectedRow()
        details.text = if (row == null) state.snapshot?.routes?.let { routes ->
            (routes.notes + routes.applications.flatMap { app -> app.notes.map { "${app.moduleName} / ${app.className}: $it" } }).joinToString("\n")
        } ?: state.message else listOf(
            "Path: ${row.mount.path ?: "unresolved"}", "Page: ${row.mount.pageClass ?: "unresolved"}",
            "Application: ${row.application.className}", "Module/source set: ${row.application.moduleName} / ${row.application.sourceSet}",
            "Status: ${row.mount.status}", "Application-relative declaration; active runtime routes are not verified.",
            *row.mount.notes.toTypedArray(), *row.application.notes.toTypedArray()).joinToString("\n")
        details.caretPosition = 0
    }
    private fun selected(box: ComboBox<String>, all: String) = (box.selectedItem as? String)?.takeUnless { it == all }
    private fun display(value: String) = value.replace("\r", "\\r").replace("\n", "\\n").replace("\t", "\\t")
    override fun dispose() = Unit
}
