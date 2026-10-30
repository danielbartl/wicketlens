package dev.wicketlens.ui

import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.colors.EditorFontType
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.OnePixelSplitter
import com.intellij.ui.SearchTextField
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.TreeSpeedSearch
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTabbedPane
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.ui.JBUI
import dev.wicketlens.model.DiagnosticCode
import java.awt.BorderLayout
import java.awt.event.ActionEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.AbstractAction
import javax.swing.JPanel
import javax.swing.KeyStroke
import javax.swing.event.DocumentEvent
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.TreeSelectionModel
import javax.swing.tree.TreePath

class WicketToolWindowFactory : ToolWindowFactory, DumbAware {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val panel = WicketReportPanel(project.service())
        val content = toolWindow.contentManager.factory.createContent(panel, "Components", false)
        content.setDisposer(panel)
        content.preferredFocusableComponent = panel.hierarchyTree
        toolWindow.contentManager.addContent(content)
    }
}

class WicketReportPanel(private val service: WicketLensService) : SimpleToolWindowPanel(true, true), Disposable {
    val hierarchyTree = Tree(DefaultTreeModel(DefaultMutableTreeNode("No report"))).apply {
        selectionModel.selectionMode = TreeSelectionModel.SINGLE_TREE_SELECTION
        cellRenderer = WicketTreeCellRenderer()
        toolTipText = "Type to find a component. Double-click or press Enter to open its source."
        emptyText.text = "Analyze a Wicket Page or Panel to see its hierarchy."
        isRootVisible = false
    }
    val detailText = JBTextArea().apply { isEditable = false; lineWrap = true; wrapStyleWord = true }
    val reportTabs = JBTabbedPane()
    val detailsSplitter = OnePixelSplitter(true, "WicketLens.component.details.proportion", 0.68f)
    val hierarchySearch = SearchTextField(false).apply {
        textEditor.emptyText.text = "Filter IDs, types, or paths"
        textEditor.accessibleContext.accessibleName = "Filter hierarchy"
        toolTipText = "Filter by ID, component type, or colon-separated path. Separate terms with spaces."
    }
    val hierarchyStatus = ComboBox(HierarchyStatusFilter.entries.toTypedArray()).apply {
        accessibleContext.accessibleName = "Filter findings by status"
        toolTipText = "Choose which findings to show. Parent rows remain for context."
    }
    val filterSummary = JBLabel().apply { isVisible = false }
    val focusSummary = JBLabel().apply { isVisible = false }
    val reportText = JBTextArea().apply {
        isEditable = false
        lineWrap = false
        font = EditorColorsManager.getInstance().globalScheme.getFont(EditorFontType.PLAIN)
        toolTipText = "Select a component or source line. Double-click or press Enter to open its source."
    }
    private val detailsScroll = JBScrollPane(detailText).apply { border = JBUI.Borders.empty() }
    private val status = JBLabel().apply { border = JBUI.Borders.empty(4, 6) }
    private var displayed: PreparedReport? = null
    private var treeSelection = true
    private var detailsVisible = false
    private var filtering = false
    private var unfilteredExpansion = setOf("report")
    private var focusId: String? = null
    private var stateIndex = HierarchyStateIndex(emptyList())
    private var treeNodes = emptyMap<String, DefaultMutableTreeNode>()
    private var findingRows = emptyList<DefaultMutableTreeNode>()
    private val savedViews = java.util.LinkedHashMap<Triple<String, String, String>, HierarchyViewState>(16, 0.75f, true)

    val javaAction = ReportToolbarAction("Go to Java", "Open Java source for the selected component", AllIcons.FileTypes.Java,
        { ready() && selectedLink()?.javaLocation != null }, { navigate(SourceSide.JAVA) })
    val markupAction = ReportToolbarAction("Go to HTML", "Open the class companion or selected component's HTML source", AllIcons.FileTypes.Html,
        { ready() && selectedLink()?.markupLocation != null }, { navigate(SourceSide.MARKUP) })
    val reanalyzeAction = ReportToolbarAction("Re-analyze", "Refresh the original Wicket Page or Panel", AllIcons.Actions.Refresh,
        { service.state.canReanalyze }, { service.reanalyze() })
    val copyAction = ReportToolbarAction("Copy Wicket Context", "Copy the current snapshot as JSON", AllIcons.Actions.Copy,
        { ready() }, { service.copyContext() })
    val cancelAction = ReportToolbarAction("Cancel", "Cancel the current analysis", AllIcons.Actions.Suspend,
        { service.state.running }, { service.cancel() })
    val expandAction = ReportToolbarAction("Expand all", "Expand all component branches", AllIcons.Actions.Expandall,
        { displayed != null && reportTabs.selectedIndex == 0 }, {
            var row = 0
            while (row < hierarchyTree.rowCount) hierarchyTree.expandRow(row++)
        })
    val collapseAction = ReportToolbarAction("Collapse all", "Collapse component branches", AllIcons.Actions.Collapseall,
        { displayed != null && reportTabs.selectedIndex == 0 }, {
            for (row in hierarchyTree.rowCount - 1 downTo 1) hierarchyTree.collapseRow(row)
        })
    val focusAction = ReportToolbarAction("Focus subtree", "Show the selected branch with its parent context", AllIcons.Actions.ShowAsTree,
        { displayed != null && reportTabs.selectedIndex == 0 && selectedEntry()?.let { entry ->
            entry.id != "report" && entry.id != focusId && displayed!!.hierarchy.any { it.parentId == entry.id }
        } == true }, {
            focusId = selectedEntry()?.id
            filterHierarchy()
        })
    val showFullAction = ReportToolbarAction("Show full hierarchy", "Return to the full page using the current search and status", AllIcons.Actions.Back,
        { displayed != null && reportTabs.selectedIndex == 0 && focusId != null }, {
            focusId = null
            filterHierarchy()
        })
    val nextFindingAction = ReportToolbarAction("Next finding", "Select the next unpaired or unresolved component (F2)", AllIcons.Actions.NextOccurence,
        { displayed != null && reportTabs.selectedIndex == 0 && findingRows.isNotEmpty() }, { selectFinding(forward = true) })
    val previousFindingAction = ReportToolbarAction("Previous finding", "Select the previous unpaired or unresolved component (Shift+F2)", AllIcons.Actions.PreviousOccurence,
        { displayed != null && reportTabs.selectedIndex == 0 && findingRows.isNotEmpty() }, { selectFinding(forward = false) })
    val detailsAction: ToggleAction = object : ToggleAction("Show component details", "Show sources, confidence, and uncertainty for the selection",
        AllIcons.Actions.PreviewDetails), DumbAware {
        override fun getActionUpdateThread() = ActionUpdateThread.EDT
        override fun isSelected(event: AnActionEvent) = detailsVisible
        override fun setSelected(event: AnActionEvent, state: Boolean) {
            detailsVisible = state
            detailsSplitter.secondComponent = if (state) detailsScroll else null
        }
        override fun update(event: AnActionEvent) {
            super.update(event)
            event.presentation.isEnabled = displayed != null && reportTabs.selectedIndex == 0
        }
    }
    val followEditorAction: ToggleAction = object : ToggleAction("Follow editor", "Follow the active Wicket Java file or companion HTML",
        AllIcons.General.AutoscrollFromSource), DumbAware {
        override fun getActionUpdateThread() = ActionUpdateThread.EDT
        override fun isSelected(event: AnActionEvent) = service.followEditor
        override fun setSelected(event: AnActionEvent, state: Boolean) = service.setEditorFollowing(state)
    }
    val actionToolbar: ActionToolbar = ActionManager.getInstance().createActionToolbar("WicketLens.Sidebar", DefaultActionGroup().apply {
        add(reanalyzeAction)
        add(followEditorAction)
        add(cancelAction)
        addSeparator()
        add(javaAction)
        add(markupAction)
        add(copyAction)
        addSeparator()
        add(previousFindingAction)
        add(nextFindingAction)
        add(focusAction)
        add(showFullAction)
        addSeparator()
        add(expandAction)
        add(collapseAction)
        add(detailsAction)
    }, true)

    init {
        TreeSpeedSearch.installOn(hierarchyTree, true) { path ->
            ((path.lastPathComponent as? DefaultMutableTreeNode)?.userObject as? ReportTreeEntry)?.let {
                listOfNotNull(it.name, it.typeName).joinToString(" ")
            } ?: path.lastPathComponent.toString()
        }
        detailsSplitter.firstComponent = JBScrollPane(hierarchyTree).apply { border = JBUI.Borders.empty() }
        val filterControls = JPanel(BorderLayout(JBUI.scale(6), JBUI.scale(4))).apply {
            border = JBUI.Borders.empty(4, 6)
            add(hierarchySearch, BorderLayout.CENTER)
            add(hierarchyStatus, BorderLayout.EAST)
            add(JPanel().apply {
                layout = javax.swing.BoxLayout(this, javax.swing.BoxLayout.Y_AXIS)
                add(focusSummary)
                add(filterSummary)
            }, BorderLayout.SOUTH)
        }
        reportTabs.addTab("Hierarchy", JPanel(BorderLayout()).apply {
            add(filterControls, BorderLayout.NORTH)
            add(detailsSplitter, BorderLayout.CENTER)
        })
        reportTabs.addTab("Text report", JBScrollPane(reportText).apply { border = JBUI.Borders.empty() })
        actionToolbar.targetComponent = this
        setToolbar(actionToolbar.component)
        setContent(JPanel(BorderLayout()).apply {
            add(reportTabs, BorderLayout.CENTER)
            add(status, BorderLayout.SOUTH)
        })
        reportText.addCaretListener { treeSelection = false; updateActions() }
        reportTabs.addChangeListener { treeSelection = reportTabs.selectedIndex == 0; updateActions() }
        hierarchyTree.addTreeSelectionListener {
            treeSelection = reportTabs.selectedIndex == 0
            detailText.text = selectedEntry()?.details.orEmpty()
            detailText.caretPosition = 0
            updateActions()
        }
        hierarchySearch.addDocumentListener(object : DocumentAdapter() {
            override fun textChanged(event: DocumentEvent) = filterHierarchy()
        })
        hierarchyStatus.addActionListener { filterHierarchy() }
        hierarchyTree.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(event: MouseEvent) {
                if (event.clickCount == 2 && event.button == MouseEvent.BUTTON1 &&
                    hierarchyTree.getPathForLocation(event.x, event.y) == hierarchyTree.selectionPath) navigatePreferred()
            }
        })
        hierarchyTree.inputMap.put(KeyStroke.getKeyStroke("ENTER"), "wicketlens.navigate")
        hierarchyTree.actionMap.put("wicketlens.navigate", object : AbstractAction() {
            override fun actionPerformed(event: ActionEvent) = navigatePreferred()
        })
        for ((key, name, forward) in listOf(Triple("F2", "wicketlens.nextFinding", true), Triple("shift F2", "wicketlens.previousFinding", false))) {
            hierarchyTree.inputMap.put(KeyStroke.getKeyStroke(key), name)
            hierarchyTree.actionMap.put(name, object : AbstractAction() {
                override fun actionPerformed(event: ActionEvent) {
                    val action = if (forward) nextFindingAction else previousFindingAction
                    if (action.isEnabled) selectFinding(forward)
                }
            })
        }
        reportText.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(event: MouseEvent) {
                if (event.clickCount == 2 && event.button == MouseEvent.BUTTON1) {
                    val link = displayed?.presentation?.linkAt(reportText.viewToModel2D(event.point)) ?: return
                    link.location(link.preferredSide)?.let { service.navigate(it) }
                }
            }
        })
        reportText.inputMap.put(KeyStroke.getKeyStroke("ENTER"), "wicketlens.navigate")
        reportText.actionMap.put("wicketlens.navigate", object : AbstractAction() {
            override fun actionPerformed(event: ActionEvent) = navigatePreferred()
        })
        service.trackEditor(this)
        service.listen(this) { state ->
            val prepared = state.prepared
            status.text = when {
                state.running -> "Analyzing Wicket page…"
                state.stale -> "Out of date — Re-analyze"
                state.message.startsWith("Copied Wicket context") -> "Wicket context copied"
                prepared != null -> {
                    val matches = prepared.report.matching?.findings.orEmpty().count { it.code == DiagnosticCode.MATCHED }
                    "$matches matched · partial snapshot"
                }
                else -> if (service.followEditor) "No Wicket page selected" else "Analyze a Wicket Page or Panel"
            }
            status.toolTipText = "Status: ${state.message}"
            if (displayed !== prepared) {
                rememberView()
                displayed = prepared
                stateIndex = HierarchyStateIndex(prepared?.hierarchy.orEmpty())
                var restore = prepared?.let { savedViews[pageKey(it)] }
                focusId = stateIndex.idFor(restore?.focused)
                if (restore?.focused != null && focusId == null) restore = restore.copy(expanded = restore.unfilteredExpanded, focused = null)
                reportText.text = prepared?.text.orEmpty()
                reportText.caretPosition = 0
                filtering = false
                unfilteredExpansion = restore?.unfilteredExpanded?.mapNotNull(stateIndex::idFor)?.toSet() ?: setOf("report")
                filterHierarchy(preserveSelection = false, restored = restore)
            }
            if (prepared == null) hierarchyTree.emptyText.text = state.message
            updateActions()
        }
    }

    private fun ready() = displayed != null && !service.state.running && !service.state.stale

    private fun selectedEntry() = (hierarchyTree.lastSelectedPathComponent as? DefaultMutableTreeNode)?.userObject as? ReportTreeEntry

    private fun selectedLink(): ReportLink? = if (treeSelection) selectedEntry()?.let {
        ReportLink(0, 0, it.javaLocation, it.markupLocation, if (it.javaLocation != null) SourceSide.JAVA else SourceSide.MARKUP)
    } else displayed?.presentation?.linkAt(reportText.caretPosition)

    private fun navigatePreferred() {
        val link = selectedLink() ?: return
        link.location(link.preferredSide)?.let { service.navigate(it) }
    }

    private fun pageKey(prepared: PreparedReport) = Triple(prepared.report.qualifiedClassName,
        prepared.report.nodes.firstOrNull { it.nodeId == prepared.report.rootNodeId }?.javaLocation?.file.orEmpty(), prepared.report.markupSuffix)

    private fun expandedIds() = treeNodes.filterValues { hierarchyTree.isExpanded(TreePath(it.path)) }.keys

    private fun rememberView() {
        val prepared = displayed ?: return
        savedViews[pageKey(prepared)] = HierarchyViewState(expandedIds().mapNotNull(stateIndex::keyFor).toSet(),
            unfilteredExpansion.mapNotNull(stateIndex::keyFor).toSet(), stateIndex.keyFor(selectedEntry()?.id),
            stateIndex.keyFor(focusId), hierarchySearch.text, hierarchyStatus.selectedItem as? HierarchyStatusFilter ?: HierarchyStatusFilter.ALL)
        if (savedViews.size > 16) savedViews.remove(savedViews.keys.first())
    }

    private fun selectFinding(forward: Boolean) {
        val root = hierarchyTree.model.root as? DefaultMutableTreeNode ?: return
        val order = root.preorderEnumeration().toList().map { it as DefaultMutableTreeNode }
        val positions = order.withIndex().associate { it.value to it.index }
        val current = order.indexOf(hierarchyTree.lastSelectedPathComponent)
        val target = if (forward) findingRows.firstOrNull { positions.getValue(it) > current } ?: findingRows.firstOrNull()
            else findingRows.lastOrNull { current < 0 || positions.getValue(it) < current } ?: findingRows.lastOrNull()
        target?.let {
            val path = TreePath(it.path)
            hierarchyTree.selectionPath = path
            hierarchyTree.scrollPathToVisible(path)
        }
    }

    private fun filterHierarchy(preserveSelection: Boolean = true, restored: HierarchyViewState? = null) {
        val oldSelection = if (restored != null) stateIndex.idFor(restored.selected) else if (preserveSelection) selectedEntry()?.id else null
        val expanded = if (restored != null) restored.expanded.mapNotNull(stateIndex::idFor).toSet()
            else if (preserveSelection) expandedIds() else setOf("report")
        val statusFilter = hierarchyStatus.selectedItem as? HierarchyStatusFilter ?: HierarchyStatusFilter.ALL
        val restoreView = restored != null && restored.query == hierarchySearch.text && restored.status == statusFilter &&
            restored.focused == stateIndex.keyFor(focusId)
        val result = ReportHierarchyFilter().apply(displayed?.hierarchy.orEmpty(), hierarchySearch.text,
            statusFilter, focusId)
        if (restored == null && !filtering && result.active) unfilteredExpansion = expanded
        val restoreExpansion = if (!result.active && (filtering || restored?.let {
                it.query.isNotBlank() || it.status != HierarchyStatusFilter.ALL || it.focused != null
            } == true)) unfilteredExpansion else expanded
        filtering = result.active
        val entries = result.entries
        val nodes = entries.associate { it.id to DefaultMutableTreeNode(it) }
        entries.filter { it.parentId != null }.forEach { entry -> nodes[entry.parentId]?.add(nodes.getValue(entry.id)) }
        hierarchyTree.model = DefaultTreeModel(nodes["report"] ?: DefaultMutableTreeNode("No report"))
        treeNodes = nodes
        hierarchyTree.isRootVisible = entries.isNotEmpty()
        hierarchyTree.clearSelection()
        for (row in hierarchyTree.rowCount - 1 downTo 0) hierarchyTree.collapseRow(row)
        if (result.active && !restoreView) {
            var row = 0
            while (row < hierarchyTree.rowCount) hierarchyTree.expandRow(row++)
        } else {
            restoreExpansion.mapNotNull { nodes[it] }.forEach { hierarchyTree.expandPath(TreePath(it.path)) }
        }
        val expandSelection = hierarchyTree.expandsSelectedPaths
        if (restoreView) hierarchyTree.expandsSelectedPaths = false
        try {
            oldSelection?.let { nodes[it] }?.let { hierarchyTree.selectionPath = TreePath(it.path) }
        } finally { hierarchyTree.expandsSelectedPaths = expandSelection }
        treeSelection = reportTabs.selectedIndex == 0
        detailText.text = selectedEntry()?.details ?: if (result.active) "" else entries.firstOrNull()?.details.orEmpty()
        detailText.caretPosition = 0
        filterSummary.isVisible = result.active && displayed != null
        filterSummary.text = if (result.matchCount == 0) "No matching rows" else "${result.matchCount} matching rows · parents kept for context"
        val focused = displayed?.hierarchy?.firstOrNull { it.id == focusId }
        focusSummary.isVisible = focused != null
        focusSummary.text = focused?.let { "Focused: ${it.name}" }.orEmpty()
        focusSummary.toolTipText = stateIndex.keyFor(focusId)?.path?.filter { it.role in setOf("component", "markup", "template") }
            ?.joinToString(":") { it.name }?.let { "Path: " + it.replace("\r", "\\r").replace("\n", "\\n").replace("\t", "\\t") }
        findingRows = (hierarchyTree.model.root as DefaultMutableTreeNode).preorderEnumeration().toList().map { it as DefaultMutableTreeNode }.filter {
            val entry = it.userObject as? ReportTreeEntry
            entry != null && !entry.contextOnly && entry.kind in setOf(ReportEntryKind.COMPONENT, ReportEntryKind.CONTAINER, ReportEntryKind.MARKUP) &&
                (entry.status in setOf(DiagnosticCode.JAVA_ONLY, DiagnosticCode.MARKUP_ONLY) || entry.status?.name?.startsWith("UNRESOLVED_") == true)
        }
        hierarchyTree.emptyText.text = if (displayed == null) service.state.message else "No matching rows"
        updateActions()
    }

    private fun updateActions() { actionToolbar.updateActionsAsync() }

    private fun navigate(side: SourceSide) { selectedLink()?.location(side)?.let { service.navigate(it) } }

    override fun dispose() = Unit
}
