package dev.wicketlens.analysis

import com.intellij.openapi.progress.ProgressManager
import com.intellij.psi.*
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.InheritanceUtil
import dev.wicketlens.model.*

/** Complete direct-use proof for one literal, not a search for equal string values. */
internal class WicketIdOriginAudit(private val limits: Limits = Limits()) {
    data class Limits(val steps: Int = 8192, val references: Int = 256, val depth: Int = 12, val sources: Int = 48)
    data class Result(val nodes: List<ComponentNode>, val sources: List<String>, val diagnostics: List<Diagnostic>)
    private class Limit : RuntimeException(null, null, false, false)

    fun analyze(page: PsiClass, nodes: List<ComponentNode>, promote: Boolean): Result {
        val sources = linkedSetOf<String>()
        var steps = 0
        fun tick() {
            ProgressManager.checkCanceled()
            if (++steps > limits.steps || sources.size > limits.sources) throw Limit()
        }
        fun source(element: PsiElement): Boolean {
            tick()
            val file = element.containingFile ?: return false
            sources += file.virtualFile?.path?.substringBefore("!/") ?: return false
            if (sources.size > limits.sources) throw Limit()
            return element is PsiCompiledElement || PsiTreeUtil.findChildOfType(file, PsiErrorElement::class.java) == null
        }
        try {
            val byId = nodes.associateBy { it.nodeId }
            val audited = nodes.map { node ->
                tick()
                if (!promote && !node.idRenameSafe || node.wicketId == null) return@map node
                val location = node.idLocation
                val leaf = location?.takeIf { it.file == page.containingFile.virtualFile?.path }?.let { page.containingFile.findElementAt(it.offset) }
                val literal = leaf?.let { PsiTreeUtil.getParentOfType(it, PsiLiteralExpression::class.java, false) }
                val method = literal?.let { PsiTreeUtil.getParentOfType(it, PsiMethod::class.java) }
                val field = literal?.let { PsiTreeUtil.getParentOfType(it, PsiField::class.java) }
                val ownSetup = method?.containingClass == page && (method.isConstructor || method.name == "onInitialize" && method.parameterList.parametersCount == 0 &&
                    method.findDeepestSuperMethods().any { it.containingClass?.qualifiedName == "org.apache.wicket.Component" })
                val ownField = field?.containingClass == page && field.hasModifierProperty(PsiModifier.PRIVATE) &&
                    field.hasModifierProperty(PsiModifier.FINAL) && !field.hasModifierProperty(PsiModifier.STATIC) &&
                    (field.type as? PsiClassType)?.resolve()?.let { it.qualifiedName == "org.apache.wicket.Component" || InheritanceUtil.isInheritor(it, "org.apache.wicket.Component") } == true
                val eligible = literal?.value == node.wicketId && (ownSetup || ownField) && !node.computedId &&
                    !node.branchMerged && !node.conditional && !node.template && node.idLocations().size == 1 &&
                    generateSequence(node.parentNodeId) { byId[it]?.parentNodeId }.take(nodes.size).none {
                        byId[it]?.let { parent -> parent.fragment != null || parent.sourceSetupExpanded || parent.template } == true
                    } &&
                    nodes.count { location in it.idLocations() } == 1 &&
                    PsiTreeUtil.getParentOfType(literal, PsiLambdaExpression::class.java) == null
                if (!eligible) return@map node.copy(idRenameSafe = false)
                val active = mutableSetOf<PsiVariable>()
                var references = 0
                var terminals = 0
                fun refs(variable: PsiVariable, scope: PsiElement): List<PsiReferenceExpression> {
                    val found = mutableListOf<PsiReferenceExpression>()
                    scope.accept(object : JavaRecursiveElementWalkingVisitor() {
                        override fun visitElement(element: PsiElement) { tick(); super.visitElement(element) }
                        override fun visitReferenceExpression(expression: PsiReferenceExpression) {
                            if (expression.resolve() == variable) {
                                if (++references > limits.references) throw Limit()
                                found += expression
                            }
                            super.visitReferenceExpression(expression)
                        }
                    })
                    return found
                }
                fun follow(expression: PsiExpression, depth: Int): Boolean {
                    tick()
                    if (depth >= limits.depth) throw Limit()
                    val parent = expression.parent
                    if (parent is PsiParenthesizedExpression) return follow(parent, depth + 1)
                    if (parent is PsiLocalVariable && parent.initializer == expression && parent.type.canonicalText == "java.lang.String") {
                        val scope = PsiTreeUtil.getParentOfType(parent, PsiMethod::class.java)?.body ?: return false
                        if (!active.add(parent)) return false
                        try { val uses = refs(parent, scope); return uses.isNotEmpty() && uses.all { follow(it, depth + 1) } }
                        finally { active.remove(parent) }
                    }
                    val arguments = parent as? PsiExpressionList ?: return false
                    val call = arguments.parent
                    val target = when (call) {
                        is PsiNewExpression -> call.resolveConstructor()?.takeIf { call.resolveMethodGenerics().isValidResult }
                        is PsiMethodCallExpression -> call.resolveMethod()?.takeIf { call.resolveMethodGenerics().isValidResult }
                        else -> null
                    } ?: return false
                    val slot = arguments.expressions.indexOf(expression)
                    val parameter = target.parameterList.parameters.getOrNull(slot) ?: return false
                    if (target.isVarArgs || parameter.type.canonicalText != "java.lang.String" || !source(target)) return false
                    if (target is PsiCompiledElement) {
                        if (!WicketComponentIdentity.frameworkConstructor(target) || slot != 0) return false
                        terminals++
                        return terminals <= 1
                    }
                    val exact = target.isConstructor || target.hasModifierProperty(PsiModifier.PRIVATE) ||
                        target.hasModifierProperty(PsiModifier.STATIC) || target.hasModifierProperty(PsiModifier.FINAL) ||
                        target.containingClass?.hasModifierProperty(PsiModifier.FINAL) == true ||
                        (call as? PsiMethodCallExpression)?.methodExpression?.qualifierExpression is PsiSuperExpression
                    val body = target.body ?: return false
                    if (!exact || !active.add(parameter)) return false
                    try { val uses = refs(parameter, body); return uses.isNotEmpty() && uses.all { follow(it, depth + 1) } }
                    finally { active.remove(parameter) }
                }
                node.copy(idRenameSafe = follow(literal, 0) && terminals == 1)
            }
            return Result(audited, sources.sorted(), emptyList())
        } catch (_: Limit) {
            return Result(nodes.map { if (it.wicketId == null) it else it.copy(idRenameSafe = false) }, sources.sorted(),
                listOf(Diagnostic(DiagnosticCode.PARTIAL_ANALYSIS,
                    "Paired rename origin-use audit exceeded its bounded traversal; rename remains unavailable.",
                    SourceLocation(page.containingFile.virtualFile.path, page.textOffset))))
        }
    }
}
