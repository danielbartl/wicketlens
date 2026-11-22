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
import com.intellij.ui.components.ActionLink
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.table.JBTable
import com.intellij.util.ui.JBUI
import dev.wicketlens.ui.ReportToolbarAction
import java.awt.BorderLayout
import java.awt.CardLayout
import java.awt.Cursor
import java.awt.Point
import java.awt.event.ActionEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.font.TextAttribute
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
    private var showApplication = false
    private var mountedPage: MountedPage? = null
    private var selectFirstMount = false
    private val filterLayout = CardLayout()
    private val filters = JPanel(filterLayout)
    private val mountedPageLabel = JBLabel()
    private val clearMountedPage = ActionLink("Clear page filter") {
        mountedPage = null
        selectFirstMount = false
        filterLayout.show(filters, BROWSER_FILTERS)
        filter()
    }
    var rows: List<WicketMountRow> = emptyList()
        private set
    private val model = object : AbstractTableModel() {
        private val headings = listOf("Mounted path", "Page")
        override fun getRowCount() = rows.size
        override fun getColumnCount() = headings.size
        override fun getColumnName(column: Int) = headings[column]
        override fun getValueAt(row: Int, column: Int): Any = rows[row].let { route ->
            if (column == PATH_COLUMN) route.mount.path ?: "<unresolved path>"
            else route.mount.pageClass?.substringAfterLast('.') ?: route.mount.nonPageTarget ?: "<unresolved page>"
        }
    }
    val refreshAction = ReportToolbarAction("Refresh mounted URLs", "Discover direct page mount declarations in this project", AllIcons.Actions.Refresh,
        { !state.running }, ::refreshRoutes)
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
    val mountedTestAction = ReportToolbarAction("Generate Wicket Mounted Test…",
        "Preview a WicketTester test for the selected page or resource mount", AllIcons.Actions.Execute,
        { ready() && selectedRow()?.let { dev.wicketlens.testing.WicketMountedTests.supports(it.mount) } == true }, {
            selectedRow()?.let { project.getService(dev.wicketlens.spring.WicketSpringTestService::class.java).generateMounted(it) }
        })
    private val toolbar = ActionManager.getInstance().createActionToolbar("WicketLens.Routes",
        DefaultActionGroup(refreshAction, cancelAction, pageAction, htmlAction, registrationAction, analyzeAction, mountedTestAction), true)

    init {
        table.model = model
        table.tableHeader.reorderingAllowed = false
        table.columnModel.getColumn(PATH_COLUMN).preferredWidth = JBUI.scale(145)
        table.columnModel.getColumn(1).preferredWidth = JBUI.scale(170)
        // Source/path labels are literal text, including strings beginning with <html>.
        table.setDefaultRenderer(Any::class.java, object : DefaultTableCellRenderer() {
            override fun setValue(value: Any?) { putClientProperty("html.disable", true); text = display(value.toString()) }

            override fun getTableCellRendererComponent(table: javax.swing.JTable, value: Any?, isSelected: Boolean,
                hasFocus: Boolean, row: Int, column: Int): java.awt.Component {
                super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column)
                val entry = rows[row]
                val link = column == PATH_COLUMN && canOpenPath(entry)
                font = if (link) table.font.deriveFont(mapOf(TextAttribute.UNDERLINE to TextAttribute.UNDERLINE_ON)) else table.font
                foreground = when {
                    isSelected -> table.selectionForeground
                    link -> JBUI.CurrentTheme.Link.Foreground.ENABLED
                    else -> table.foreground
                }
                icon = if (column != PATH_COLUMN) null else if (entry.mount.conflict != null) AllIcons.General.Warning else when (entry.mount.status) {
                    MountStatus.DECLARED -> null
                    MountStatus.CONDITIONAL -> AllIcons.General.Information
                    MountStatus.UNRESOLVED -> AllIcons.General.Warning
                }
                toolTipText = if (column == PATH_COLUMN) {
                    val action = when {
                        !link -> ""
                        ready() -> " Click to open the page source."
                        else -> " Click to refresh and open the page source."
                    }
                    (entry.mount.conflict ?: "Mount declaration: ${entry.mount.status.name.lowercase()}.") + action
                } else {
                    if (showApplication) text += " · ${display(entry.application.className.substringAfterLast('.'))}"
                    "${display(entry.mount.pageClass ?: entry.mount.nonPageTarget ?: "Unresolved page")} · ${display(entry.application.className)} · ${display(entry.application.moduleName)}"
                }
                return this
            }
        })
        toolbar.targetComponent = table
        filters.add(JPanel(BorderLayout(0, JBUI.scale(4))).apply {
            add(search, BorderLayout.NORTH)
            add(JPanel(java.awt.GridLayout(1, 3, JBUI.scale(4), 0)).apply {
                add(modules); add(applications); add(sourceSet)
            }, BorderLayout.CENTER)
        }, BROWSER_FILTERS)
        filters.add(JPanel(BorderLayout(JBUI.scale(8), 0)).apply {
            border = JBUI.Borders.empty(4, 6)
            add(mountedPageLabel, BorderLayout.CENTER)
            add(clearMountedPage, BorderLayout.EAST)
        }, PAGE_FILTER)
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
        val pointer = object : MouseAdapter() {
            override fun mouseClicked(event: MouseEvent) {
                if (event.button != MouseEvent.BUTTON1) return
                val row = table.rowAtPoint(event.point)
                if (table.columnAtPoint(event.point) == PATH_COLUMN) {
                    val entry = rows.getOrNull(row) ?: return
                    if (event.clickCount == 1 && canOpenPath(entry)) service.openPage(entry)
                    else if (event.clickCount == 2 && entry.mount.path == null && row == table.selectedRow) openPage()
                } else if (row == table.selectedRow && event.clickCount == 2) openPage()
            }

            override fun mouseMoved(event: MouseEvent) = updateCursor(event.point)
            override fun mouseEntered(event: MouseEvent) = updateCursor(event.point)
            override fun mouseExited(event: MouseEvent) = updateCursor(null)
        }
        table.addMouseListener(pointer)
        table.addMouseMotionListener(pointer)
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

    internal fun showMountedPage(file: String, pageClass: String?) {
        val page = MountedPage(file, pageClass)
        mountedPage = page
        selectFirstMount = true
        mountedPageLabel.text = "Page · ${page.name}"
        mountedPageLabel.toolTipText = pageClass ?: file
        filterLayout.show(filters, PAGE_FILTER)

        val snapshot = state.snapshot
        if (ready() && snapshot?.sourceSet == "any" && snapshot.moduleName == null && snapshot.applicationClass == null) {
            filter()
        } else {
            refreshRoutes()
        }
    }

    private fun refreshRoutes() {
        if (mountedPage != null) service.refresh(sourceSet = "any")
        else service.refresh(selected(modules, "All modules"), selected(applications, "All applications"), sourceSet.selectedItem as String)
    }

    fun selectedRow(): WicketMountRow? = rows.getOrNull(table.selectedRow)
    fun filter() {
        val previous = selectedRow()?.mount?.id
        val page = mountedPage
        val terms = search.text.trim().lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
        val module = selected(modules, "All modules")
        val application = selected(applications, "All applications")
        val set = sourceSet.selectedItem as String
        rows = state.snapshot?.routes?.applications.orEmpty().filter {
            page != null || ((module == null || it.moduleName == module) &&
                (application == null || it.className == application) && (set == "any" || it.sourceSet == set))
        }.flatMap { app -> app.mounts.map { WicketMountRow(app, it) } }.filter { row ->
            if (page != null) page.matches(row.mount) else {
                val text = listOf(row.mount.path, row.mount.pageClass ?: row.mount.nonPageTarget, row.mount.conflict, row.application.className, row.application.moduleName, row.mount.status.name).joinToString(" ").lowercase()
                terms.all(text::contains)
            }
        }
        showApplication = rows.asSequence().map { it.application.source }.distinct().take(2).count() > 1
        model.fireTableDataChanged()
        updateCursor(if (table.isShowing) table.mousePosition else null)
        if (selectFirstMount && rows.isNotEmpty()) {
            selectFirstMount = false
            table.setRowSelectionInterval(0, 0)
            table.scrollRectToVisible(table.getCellRect(0, PATH_COLUMN, true))
        } else {
            rows.indexOfFirst { it.mount.id == previous }.takeIf { it >= 0 }?.let { table.setRowSelectionInterval(it, it) }
        }
        summary.text = display(state.message + if (state.snapshot?.routes?.truncated == true) " — discovery limits reached" else "")
        summary.putClientProperty("html.disable", true)
        table.emptyText.text = if (state.snapshot == null) state.message else "No matching mount declarations."
        showDetails(); toolbar.updateActionsAsync()
    }
    private fun ready() = service.current()
    private fun canOpenPath(row: WicketMountRow) = !state.running && row.mount.path != null && row.mount.pageSource != null
    private fun updateCursor(point: Point?) {
        val link = point != null && table.columnAtPoint(point) == PATH_COLUMN &&
            rows.getOrNull(table.rowAtPoint(point))?.let(::canOpenPath) == true
        table.cursor = Cursor.getPredefinedCursor(if (link) Cursor.HAND_CURSOR else Cursor.DEFAULT_CURSOR)
    }
    private fun openPage() {
        val row = selectedRow() ?: return
        if (canOpenPath(row)) service.openPage(row)
        else if (ready()) row.mount.pageSource?.let(service::navigate)
    }
    private fun showDetails() {
        val row = selectedRow()
        details.text = if (row == null) state.snapshot?.routes?.let { routes ->
            (routes.notes + routes.applications.flatMap { app -> app.notes.map { "${app.moduleName} / ${app.className}: $it" } }).joinToString("\n")
        } ?: state.message else listOf(
            "Path: ${row.mount.path ?: "unresolved"}", "Target: ${row.mount.pageClass ?: row.mount.nonPageTarget ?: "unresolved"}",
            "Application: ${row.application.className}", "Module/source set: ${row.application.moduleName} / ${row.application.sourceSet}",
            "Status: ${row.mount.status}", "Application-relative declaration; active runtime routes are not verified.",
            *row.mount.notes.toTypedArray(), *row.application.notes.toTypedArray()).joinToString("\n")
        details.caretPosition = 0
    }
    private fun selected(box: ComboBox<String>, all: String) = (box.selectedItem as? String)?.takeUnless { it == all }
    private fun display(value: String) = value.replace("\r", "\\r").replace("\n", "\\n").replace("\t", "\\t")
    override fun dispose() = Unit

    private data class MountedPage(val file: String, val pageClass: String?) {
        val name = pageClass?.substringAfterLast('.') ?: file.substringAfterLast('/').substringBeforeLast('.')

        fun matches(mount: WicketMount) = if (pageClass == null) mount.markupSource?.file == file
        else mount.pageSource?.file == file && mount.pageClass == pageClass
    }

    private companion object {
        const val PATH_COLUMN = 0
        const val BROWSER_FILTERS = "routes"
        const val PAGE_FILTER = "page"
    }
}
