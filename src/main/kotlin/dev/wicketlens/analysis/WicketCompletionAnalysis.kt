package dev.wicketlens.analysis

import com.intellij.psi.PsiExpression
import com.intellij.psi.PsiLiteralExpression
import dev.wicketlens.model.SourceLocation

/** An editor-only probe. Normal reports, inspections and MCP never enter this scope. */
internal object WicketCompletionAnalysis {
    private data class Site(val origin: SourceLocation, val markupAttribute: SourceLocation?)
    private val target = ThreadLocal<Site>()
    val active: Boolean get() = target.get() != null

    fun allowsEmpty(expression: PsiExpression?): Boolean {
        val selected = target.get() ?: return false
        return expression is PsiLiteralExpression && expression.value == "" &&
            selected.origin == expression.containingFile.virtualFile?.let { SourceLocation(it.path, expression.textOffset) }
    }

    fun allowsEmpty(id: String?, origin: SourceLocation?): Boolean = id == "" && editingOrigin(origin)

    fun editingOrigin(origin: SourceLocation?) = origin != null && origin == target.get()?.origin
    fun editingAttribute(attribute: SourceLocation?) = attribute != null && attribute == target.get()?.markupAttribute

    fun <T> at(location: SourceLocation, markupAttribute: SourceLocation? = null, action: () -> T): T {
        val previous = target.get()
        target.set(Site(location, markupAttribute))
        return try { action() } finally { if (previous == null) target.remove() else target.set(previous) }
    }
}
