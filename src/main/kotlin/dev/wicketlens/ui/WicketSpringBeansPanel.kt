package dev.wicketlens.ui

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.SearchTextField
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.table.JBTable
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.ui.JBUI
import com.intellij.xml.util.XmlStringUtil
import dev.wicketlens.mcp.PageAnalysisFailure
import dev.wicketlens.spring.ProjectInjection
import dev.wicketlens.spring.WicketProjectDependencyRequest
import dev.wicketlens.spring.WicketProjectDependencySnapshot
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.BorderLayout
import java.awt.Cursor
import java.awt.event.ActionEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.AbstractAction
import javax.swing.JButton
import javax.swing.JPanel
import javax.swing.KeyStroke
import javax.swing.ListSelectionModel
import javax.swing.event.DocumentEvent
import javax.swing.table.AbstractTableModel
import javax.swing.table.DefaultTableCellRenderer

@Service(Service.Level.PROJECT)
internal class WicketSpringBeansScope(val scope: CoroutineScope)

internal class WicketSpringBeansPanel(private val project: Project) : JPanel(BorderLayout()), Disposable {
    private val scope = project.service<WicketSpringBeansScope>().scope
    private val request = WicketProjectDependencyRequest()
    private var refreshJob: Job? = null
    private var disposed = false
    private var started = false
    private var snapshot: WicketProjectDependencySnapshot? = null
    private var rows: List<ProjectInjection> = emptyList()

    val search = SearchTextField(false).apply {
        textEditor.emptyText.text = "Filter Spring beans"
        textEditor.toolTipText = "Filter by bean, field, component, or module."
    }
    val status = JBLabel("Open this tab to discover Spring injection fields.")
    val table = object : JBTable() {
        override fun getToolTipText(event: MouseEvent): String? {
            val row = rows.getOrNull(rowAtPoint(event.point)) ?: return null
            val field = row.field
            val candidateLines = field.candidates.take(8).map { candidate ->
                val mismatch = if (candidate.compatible == false) " (declared type mismatch)" else ""
                "${candidateNames(candidate.names)} · ${candidate.declaringClass}$mismatch"
            }
            val lines = listOf("${field.declaringClass}.${field.name}", "@${field.annotation} · ${field.type}",
                "${row.moduleName} · ${row.sourceSet}") + candidateLines + field.notes + "Click to open the injection field."
            return XmlStringUtil.wrapInHtml(lines.joinToString("<br>") { XmlStringUtil.escapeString(it) })
        }
    }
    private val model = object : AbstractTableModel() {
        override fun getRowCount() = rows.size
        override fun getColumnCount() = 2
        override fun getColumnName(column: Int) = if (column == 0) "Injection field" else "Bean source"

        override fun getValueAt(rowIndex: Int, columnIndex: Int): String = rows[rowIndex].field.let { field ->
            if (columnIndex == 0) "${field.name} · ${field.declaringClass.substringAfterLast('.')}"
            else when (field.candidates.size) {
                0 -> field.beanName?.let { "${displayName(it)} (source unresolved)" } ?: "Unresolved"
                1 -> candidateNames(field.candidates.single().names)
                else -> "${field.candidates.size} candidates"
            }
        }
    }
    private val refreshButton = JButton("Refresh").apply { addActionListener { refresh() } }
    private val cancelButton = JButton("Cancel").apply {
        isVisible = false
        addActionListener {
            refreshJob?.cancel()
            refreshJob = null
            setBusy(false)
            status.text = "Spring injection discovery cancelled."
        }
    }

    init {
        table.model = model
        table.selectionModel.selectionMode = ListSelectionModel.SINGLE_SELECTION
        table.tableHeader.reorderingAllowed = false
        table.setDefaultRenderer(Any::class.java, object : DefaultTableCellRenderer() {
            override fun setValue(value: Any?) {
                putClientProperty("html.disable", true)
                super.setValue(value)
            }
        })
        table.emptyText.text = "No matching Spring injection fields."
        table.toolTipText = "Click an injection field to open its Java declaration."
        val pointer = object : MouseAdapter() {
            override fun mouseClicked(event: MouseEvent) {
                if (event.button == MouseEvent.BUTTON1 && event.clickCount == 1) {
                    rows.getOrNull(table.rowAtPoint(event.point))?.let(::openInjection)
                }
            }
            override fun mouseMoved(event: MouseEvent) {
                table.cursor = Cursor.getPredefinedCursor(
                    if (table.rowAtPoint(event.point) in rows.indices) Cursor.HAND_CURSOR else Cursor.DEFAULT_CURSOR)
            }
            override fun mouseExited(event: MouseEvent) { table.cursor = Cursor.getDefaultCursor() }
        }
        table.addMouseListener(pointer)
        table.addMouseMotionListener(pointer)
        table.inputMap.put(KeyStroke.getKeyStroke("ENTER"), "wicketlens.spring.openInjection")
        table.actionMap.put("wicketlens.spring.openInjection", object : AbstractAction() {
            override fun actionPerformed(event: ActionEvent) {
                rows.getOrNull(table.selectedRow)?.let(::openInjection)
            }
        })
        search.addDocumentListener(object : DocumentAdapter() {
            override fun textChanged(event: DocumentEvent) { filter() }
        })
        val controls = JPanel(BorderLayout(JBUI.scale(6), 0)).apply {
            border = JBUI.Borders.empty(4)
            add(search, BorderLayout.CENTER)
            add(JPanel().apply { add(refreshButton); add(cancelButton) }, BorderLayout.EAST)
        }
        add(controls, BorderLayout.NORTH)
        add(JBScrollPane(table), BorderLayout.CENTER)
        add(status, BorderLayout.SOUTH)
    }

    fun startDiscovery() {
        if (!started) refresh()
    }

    fun refresh() {
        if (disposed || refreshJob?.isActive == true) return
        started = true
        setBusy(true)
        status.text = "Discovering Spring injection fields…"
        refreshJob = scope.launch {
            try {
                val result = request.prepare(project)
                withContext(Dispatchers.EDT) {
                    snapshot = result
                    filter()
                    val dependencies = result.dependencies
                    val limited = dependencies.truncated || dependencies.fields.any { it.field.candidatesTruncated }
                    status.text = "${dependencies.fields.size} injection fields · Source candidates only" +
                        if (limited) " · Discovery limits reached" else ""
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: ProcessCanceledException) {
                withContext(Dispatchers.EDT) { status.text = "Spring injection discovery cancelled." }
            } catch (failure: Exception) {
                if (failure !is PageAnalysisFailure) logger<WicketSpringBeansPanel>().warn("Spring injection discovery failed", failure)
                withContext(Dispatchers.EDT) {
                    status.text = (failure as? PageAnalysisFailure)?.message ?: "Discovery failed. See the IDE log."
                }
            } finally {
                withContext(Dispatchers.EDT) { setBusy(false) }
            }
        }
    }

    private fun setBusy(busy: Boolean) {
        refreshButton.isEnabled = !busy
        cancelButton.isVisible = busy
    }

    private fun filter() {
        val terms = search.text.trim().split(Regex("\\s+")).filter(String::isNotEmpty)
        rows = snapshot?.dependencies?.fields.orEmpty().filter { row ->
            val field = row.field
            val text = (listOf(row.moduleName, row.sourceSet, field.declaringClass, field.name, field.type,
                field.annotation, field.beanName) + field.candidates.flatMap { it.names + it.declaringClass }).joinToString(" ")
            terms.all { text.contains(it, ignoreCase = true) }
        }
        model.fireTableDataChanged()
    }

    private fun openInjection(row: ProjectInjection) {
        val current = snapshot ?: return
        val location = row.field.location
        val source = current.version.sources.singleOrNull { it.path == location.file } ?: return
        ReadAction.nonBlocking<OpenFileDescriptor?> {
            try {
                request.requireCurrent(project, current)
            } catch (_: PageAnalysisFailure) {
                return@nonBlocking null
            }
            val file = VirtualFileManager.getInstance().findFileByUrl(source.url) ?: return@nonBlocking null
            val document = FileDocumentManager.getInstance().getDocument(file) ?: return@nonBlocking null
            if (location.offset !in 0 until document.textLength) return@nonBlocking null
            OpenFileDescriptor(project, file, location.offset)
        }.inSmartMode(project).withDocumentsCommitted(project).expireWith(this)
            .finishOnUiThread(ModalityState.defaultModalityState()) { descriptor ->
                if (current !== snapshot) return@finishOnUiThread
                if (descriptor?.canNavigate() == true) descriptor.navigate(true)
                else status.text = "Spring bean results are out of date. Refresh to see current source."
            }.submit(AppExecutorUtil.getAppExecutorService())
    }

    private fun candidateNames(names: List<String>) = names.take(3).joinToString(", ", transform = ::displayName) +
        if (names.size > 3) ", …" else ""

    private fun displayName(text: String) = text.take(80).replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t") +
        if (text.length > 80) "…" else ""

    override fun dispose() {
        disposed = true
        refreshJob?.cancel()
    }
}
