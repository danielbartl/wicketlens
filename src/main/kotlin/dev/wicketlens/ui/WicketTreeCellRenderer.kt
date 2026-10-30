package dev.wicketlens.ui

import com.intellij.icons.AllIcons
import com.intellij.ui.ColoredTreeCellRenderer
import com.intellij.ui.SimpleTextAttributes
import dev.wicketlens.model.DiagnosticCode
import javax.swing.JTree
import javax.swing.tree.DefaultMutableTreeNode

/** Theme-aware platform presentation; source text is appended as literal fragments. */
class WicketTreeCellRenderer : ColoredTreeCellRenderer() {
    override fun customizeCellRenderer(tree: JTree, value: Any?, selected: Boolean, expanded: Boolean,
        leaf: Boolean, row: Int, hasFocus: Boolean) {
        val entry = (value as? DefaultMutableTreeNode)?.userObject as? ReportTreeEntry
        if (entry == null) {
            append(value?.toString().orEmpty())
            return
        }
        icon = when (entry.kind) {
            ReportEntryKind.PAGE, ReportEntryKind.CONTAINER -> AllIcons.Nodes.Class
            ReportEntryKind.COMPONENT -> AllIcons.Nodes.Field
            ReportEntryKind.MARKUP -> AllIcons.Nodes.Tag
            ReportEntryKind.GROUP -> AllIcons.Nodes.Folder
            ReportEntryKind.DIAGNOSTIC -> AllIcons.General.Information
        }
        append(entry.name, if (entry.contextOnly) SimpleTextAttributes.GRAYED_ATTRIBUTES else SimpleTextAttributes.REGULAR_ATTRIBUTES)
        entry.typeName?.let { append(" : $it", SimpleTextAttributes.GRAYED_ATTRIBUTES) }
        val status = if (entry.kind == ReportEntryKind.DIAGNOSTIC) null else when (entry.status) {
            DiagnosticCode.MATCHED -> "matched"
            DiagnosticCode.JAVA_ONLY -> "Java only"
            DiagnosticCode.MARKUP_ONLY -> "markup only"
            null -> null
            else -> "unresolved"
        }
        status?.let { append("  $it", SimpleTextAttributes.GRAYED_ATTRIBUTES) }
    }
}
