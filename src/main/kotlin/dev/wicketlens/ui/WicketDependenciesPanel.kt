package dev.wicketlens.ui

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.ui.ColoredTreeCellRenderer
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.OnePixelSplitter
import com.intellij.ui.SearchTextField
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.TreeSpeedSearch
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.ui.JBUI
import dev.wicketlens.model.SourceLocation
import dev.wicketlens.spring.*
import java.awt.BorderLayout
import java.awt.event.ActionEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.AbstractAction
import javax.swing.JPanel
import javax.swing.JTree
import javax.swing.KeyStroke
import javax.swing.event.DocumentEvent
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.TreePath
import javax.swing.tree.TreeSelectionModel

data class DependencyTreeEntry(val id: String, val name: String, val type: String?, val hint: String, val details: String,
    val kind: String, val javaLocation: SourceLocation?, val fieldLocation: SourceLocation? = null, val beanLocation: SourceLocation? = null) {
    override fun toString() = listOfNotNull(name, type, hint).joinToString(" ")
}

/** Swing presentation of prepared values; no source resolution or PSI work on the EDT. */
class WicketDependenciesPanel(private val service: WicketLensService, private val selectionChanged: () -> Unit) : JPanel(BorderLayout()) {
    val tree = Tree(DefaultTreeModel(DefaultMutableTreeNode("Dependencies"))).apply {
        isRootVisible = false
        selectionModel.selectionMode = TreeSelectionModel.SINGLE_TREE_SELECTION
        emptyText.text = "Analyze a Wicket page to inspect its injection fields."
        cellRenderer = object : ColoredTreeCellRenderer() {
            override fun customizeCellRenderer(tree: JTree, value: Any?, selected: Boolean, expanded: Boolean, leaf: Boolean, row: Int, hasFocus: Boolean) {
                val entry = (value as? DefaultMutableTreeNode)?.userObject as? DependencyTreeEntry ?: return
                icon = when (entry.kind) { "owner" -> AllIcons.Nodes.Class; "field" -> AllIcons.Nodes.Field; else -> AllIcons.Nodes.Method }
                append(display(entry.name), SimpleTextAttributes.REGULAR_ATTRIBUTES)
                entry.type?.let { append(" : ${display(it)}", SimpleTextAttributes.GRAYED_ATTRIBUTES) }
                if (entry.hint.isNotEmpty()) append("  ${display(entry.hint)}", SimpleTextAttributes.GRAYED_ATTRIBUTES)
            }
        }
    }
    val search = SearchTextField(false).apply {
        textEditor.emptyText.text = "Filter fields, types, or bean names"
        textEditor.accessibleContext.accessibleName = "Filter dependencies"
    }
    val details = JBTextArea().apply { isEditable = false; lineWrap = true; wrapStyleWord = true }
    val summary = JBLabel("Source candidates only; runtime bindings are not verified.")
    private var snapshot: WicketDependencySnapshot? = null
    private var ready = false
    val fieldAction = ReportToolbarAction("Go to injection field", "Open the selected injection field", AllIcons.Nodes.Field,
        { ready && selected()?.fieldLocation != null }, { selected()?.fieldLocation?.let(service::navigate) })
    val beanAction = ReportToolbarAction("Go to bean source", "Open a source candidate; this does not verify the runtime bean", AllIcons.Nodes.Method,
        { ready && selected()?.beanLocation != null }, { selected()?.beanLocation?.let(service::navigate) })
    val toolbar = ActionManager.getInstance().createActionToolbar("WicketLens.Dependencies", DefaultActionGroup(fieldAction, beanAction), true)

    init {
        TreeSpeedSearch.installOn(tree, true) { it.lastPathComponent.toString() }
        val top = JPanel(BorderLayout(JBUI.scale(4), JBUI.scale(4))).apply {
            border = JBUI.Borders.empty(4, 6)
            add(search, BorderLayout.NORTH)
            add(summary, BorderLayout.SOUTH)
        }
        toolbar.targetComponent = this
        top.add(toolbar.component, BorderLayout.CENTER)
        add(top, BorderLayout.NORTH)
        add(OnePixelSplitter(true, "WicketLens.dependencies.details.proportion", 0.7f).apply {
            firstComponent = JBScrollPane(tree).apply { border = JBUI.Borders.empty() }
            secondComponent = JBScrollPane(details).apply { border = JBUI.Borders.empty() }
        }, BorderLayout.CENTER)
        search.addDocumentListener(object : DocumentAdapter() { override fun textChanged(event: DocumentEvent) = rebuild() })
        tree.addTreeSelectionListener {
            details.text = selected()?.details ?: scopeDetails()
            details.caretPosition = 0
            toolbar.updateActionsAsync()
            selectionChanged()
        }
        tree.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(event: MouseEvent) {
                if (event.clickCount == 2 && event.button == MouseEvent.BUTTON1 && tree.getPathForLocation(event.x, event.y) == tree.selectionPath) navigate()
            }
        })
        tree.inputMap.put(KeyStroke.getKeyStroke("ENTER"), "wicketlens.dependency.navigate")
        tree.actionMap.put("wicketlens.dependency.navigate", object : AbstractAction() { override fun actionPerformed(event: ActionEvent) = navigate() })
    }

    fun selected() = (tree.lastSelectedPathComponent as? DefaultMutableTreeNode)?.userObject as? DependencyTreeEntry
    fun showSnapshot(next: WicketDependencySnapshot?, available: Boolean) {
        ready = available
        if (snapshot !== next) { snapshot = next; tree.clearSelection(); rebuild() }
        toolbar.updateActionsAsync()
    }
    private fun navigate() { if (ready) selected()?.javaLocation?.let(service::navigate) }

    private fun rebuild() {
        val previous = selected()?.id
        val root = DefaultMutableTreeNode("Dependencies")
        val model = snapshot
        val query = search.text.trim().split(Regex("\\s+")).filter(String::isNotEmpty)
        var shown = 0
        for (owner in model?.owners.orEmpty()) {
            val ownerKey = "${owner.id}/${owner.path}/${owner.type}"
            val parent = DefaultMutableTreeNode(DependencyTreeEntry("owner/$ownerKey", owner.path, owner.type.substringAfterLast('.'), if (owner.partial) "Source ancestry incomplete" else "",
                "Component: ${owner.path}\nType: ${owner.type}\nSource annotations only; runtime objects are not reconstructed." +
                    (if (owner.partial) "\nSome ancestor fields could not be inspected as Java source." else ""), "owner", owner.location))
            for (field in owner.fields) {
                val text = listOf(owner.path, field.name, field.type, field.declaringClass, field.annotation, field.beanName.orEmpty()) +
                    field.candidates.flatMap { it.names + listOf(it.type, it.declaringClass) }
                if (!query.all { part -> text.any { it.contains(part, ignoreCase = true) } }) continue
                shown++
                val lookup = when (field.lookup) {
                    DependencyLookup.TYPE -> "By type"
                    DependencyLookup.NAME -> "By name: ${display(field.beanName.orEmpty())}"
                    DependencyLookup.UNKNOWN -> "Unknown bean name"
                    DependencyLookup.UNSUPPORTED -> "Unsupported field target"
                    DependencyLookup.SPRING_AUTOWIRING -> "Spring autowiring required"
                }
                val names = field.candidates.size
                val hint = "@${field.annotation} · $lookup · $names${if (field.candidatesTruncated) "+" else ""} source candidate${if (names == 1) "" else "s"}"
                val fieldDetails = "Field: ${field.declaringClass}.${field.name}\nComponent: ${owner.path}\nDeclared type: ${field.type}\nAnnotation: @${field.annotation}\nLookup: $lookup\n" +
                    when (field.required) { true -> "Required injection\n"; false -> "Optional injection\n"; null -> "Required policy not inferred\n" } +
                    if (field.candidates.isEmpty()) "No source candidates in the reviewed scope. This is not a missing-bean finding.\n" else "Source declarations only; runtime selection, profiles, and bean availability are not verified.\n"
                val fieldKey = "$ownerKey/${field.id}"
                val child = DefaultMutableTreeNode(DependencyTreeEntry(fieldKey, field.name, field.type, hint,
                    fieldDetails + field.notes.joinToString("\n") + if (field.candidatesTruncated) "\nCandidate discovery was limited; more sources may exist." else "",
                    "field", field.location, field.location, field.candidates.singleOrNull()?.location))
                for (candidate in field.candidates) {
                    val label = candidate.names.joinToString(" / ")
                    val extra = when (candidate.compatible) { true -> "Declared type compatible"; false -> "Declared types differ; conversions not inferred"; null -> "Type compatibility unknown" }
                    child.add(DefaultMutableTreeNode(DependencyTreeEntry("$fieldKey/bean/${candidate.location.file}:${candidate.location.offset}/$label", label, candidate.type,
                        "Source candidate · context not verified", "Source: ${candidate.declaringClass}\nNames: ${display(label)}\nDeclared type: ${candidate.type}\n$extra\n" +
                            "Runtime binding is not verified." + (if (candidate.conditional) "\nConditional or additional configuration metadata is not evaluated." else "") +
                            (if (candidate.factory) "\nFactoryBean product type is not inferred." else ""), "bean", candidate.location, field.location, candidate.location)))
                }
                parent.add(child)
            }
            if (parent.childCount > 0) root.add(parent)
        }
        tree.model = DefaultTreeModel(root)
        root.children().toList().map { it as DefaultMutableTreeNode }.forEach { tree.expandPath(TreePath(it.path)) }
        val selected = root.preorderEnumeration().toList().map { it as DefaultMutableTreeNode }.singleOrNull { (it.userObject as? DependencyTreeEntry)?.id == previous }
        if (selected != null) tree.selectionPath = TreePath(selected.path)
        details.text = selected()?.details ?: scopeDetails()
        summary.text = if (model == null) "Analyze a Wicket page to inspect its injection fields."
            else "$shown of ${model.fieldCount} injection fields · source candidates only" + if (model.truncated) " · limited" else ""
        summary.toolTipText = model?.application?.let { "Injector registration recognized for $it; runtime binding is not verified." }
            ?: "Application registration is not verified. Resolved @SpringBean expresses local injection intent."
        tree.emptyText.text = when { model == null -> "No analyzed page"; model.fieldCount == 0 -> "No Wicket–Spring injection fields recognized in the reviewed source components."; else -> "No matching dependencies" }
        toolbar.updateActionsAsync()
        selectionChanged()
    }
    private fun scopeDetails() = "Source annotations from the analyzed page and discovered component types.\n" +
        "Bean declarations are source candidates; runtime bindings are not verified.\n" +
        (snapshot?.application?.let { "Injector registration recognized for $it.\n" } ?: "Application registration is not verified.\n") +
        "Compiled-only fields and unobserved models, sessions, helpers, and runtime objects are not inspected."
    private fun display(value: String) = value.replace("\r", "\\r").replace("\n", "\\n").replace("\t", "\\t")
}
