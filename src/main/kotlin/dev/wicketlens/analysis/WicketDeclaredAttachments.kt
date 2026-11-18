package dev.wicketlens.analysis

import com.intellij.psi.*
import com.intellij.psi.util.PsiTreeUtil
import dev.wicketlens.model.*

/** Shared initial-declaration attachment identity, never evidence of a later runtime path. */
internal class WicketDeclaredAttachments(
    private val page:PsiClass, private val primary:PageReport, private val depth:Int,
    private val tick:(PsiElement)->Unit, private val descendants:(PsiElement)->List<PsiElement>,
    private val fail:(PsiElement,String)->Nothing, private val bound:(PsiElement)->Nothing, private val defaultReceiver:()->String,
) {
    private val initial=primary.nodes.associateBy {it.nodeId}
        fun ancestry(id: String): List<ComponentNode> {
            val result = mutableListOf<ComponentNode>(); val seen = mutableSetOf<String>(); var current: String? = id
            while (current != null) {
                tick(page); if (!seen.add(current)) fail(page, "The declared target ancestry is cyclic.")
                if (result.size >= depth) bound(page)
                val node = initial[current] ?: fail(page, "The declared target ancestry is unavailable.")
                if (node.nodeId != primary.rootNodeId && primary.matching?.findings?.singleOrNull { it.javaNodeId == node.nodeId }?.code != DiagnosticCode.MATCHED)
                    fail(page, "A submission target does not have a unique initial markup counterpart.")
                result += node; current = node.parentNodeId
            }
            if (result.lastOrNull()?.nodeId != primary.rootNodeId) fail(page, "A target is not attached under the selected page.")
            return result.asReversed()
        }
        fun path(id: String) = ancestry(id).drop(1).filterNot { it.templateItem }.joinToString(":") { it.wicketId ?: fail(page, "A source attachment ID is unknown.") }
        fun component(e: PsiExpression): String {
            tick(e)
            if (e is PsiThisExpression) {
                if (e.qualifier == null) return defaultReceiver()
                if (e.qualifier?.resolve() == page) return primary.rootNodeId
                fail(e, "Only the selected host or qualified selected page can be refreshed.")
            }
            val ref = e as? PsiReferenceExpression ?: fail(e, "Refresh targets must be direct stable constructor locals.")
            if (ref.qualifierExpression != null || !ref.advancedResolve(false).isValidResult) fail(ref, "The component reference is qualified or inaccessible.")
            val local = ref.resolve() as? PsiLocalVariable ?: fail(ref, "Only direct unchanged constructor locals are reviewed as captured targets.")
            val constructor = PsiTreeUtil.getParentOfType(local, PsiMethod::class.java)
            if (constructor?.isConstructor != true || constructor.containingClass != page || local.parent.parent != constructor.body ||
                descendants(page).filterIsInstance<PsiAssignmentExpression>().any { (it.lExpression as? PsiReferenceExpression)?.resolve() == local }) fail(local, "A captured component is not an unchanged direct page-constructor local.")
            val allocation = local.initializer as? PsiNewExpression ?: fail(local, "Captured aliases/factories are not a proved component identity.")
            val node = primary.nodes.singleOrNull { it.javaLocation == location(allocation) } ?: fail(local, "The captured component is not in the initial source graph.")
            ancestry(node.nodeId)
            return node.nodeId
        }
}
private fun location(e:PsiElement)=SourceLocation(e.containingFile.virtualFile.path,e.textOffset)
