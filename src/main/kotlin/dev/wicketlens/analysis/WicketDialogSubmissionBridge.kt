package dev.wicketlens.analysis

import com.intellij.psi.*
import com.intellij.psi.util.InheritanceUtil
import com.intellij.psi.util.PsiTreeUtil
import dev.wicketlens.model.*

/** Shared standard AjaxButton to selected source hook route. No dispatch or validation is run. */
internal class WicketDialogSubmissionBridge(
    private val page:PsiClass, private val primary:PageReport, private val depth:Int, private val tick:(PsiElement)->Unit,
    private val source:(PsiElement)->Unit, private val descendants:(PsiElement)->List<PsiElement>,
    private val fail:(PsiElement,String)->Nothing, private val attachments:WicketDeclaredAttachments,
) {
    data class Bridge(val method:PsiMethod,val dispatch:PsiMethodCallExpression,val trigger:ComponentNode)
    private val initial=primary.nodes.associateBy {it.nodeId}
    private fun own(e:PsiExpression?)=e==null || e is PsiThisExpression && e.qualifier==null
    private fun reference(e:PsiExpression?,target:PsiElement)=e is PsiReferenceExpression && e.resolve()==target && own(e.qualifierExpression)
    private fun call(s:PsiStatement?)=(s as? PsiExpressionStatement)?.expression as? PsiMethodCallExpression
    private fun ancestry(id:String)=attachments.ancestry(id)
    private fun under(id:String,host:String)=ancestry(id).any {it.nodeId==host}
        fun inspect(info: WicketInitialContentDeclarations.Host, callback: PsiMethod, host: InitialContentHost, actual:PsiClass): Bridge? {
            val bases = callback.findSuperMethods().toSet()
            val bridges = mutableListOf<Bridge>()
            for (constructor in info.constructors) {
                source(constructor)
                for (allocation in descendants(constructor).filterIsInstance<PsiNewExpression>()) {
                    val anonymous = allocation.anonymousClass ?: continue
                    val base = anonymous.baseClassType.resolve() ?: continue
                    if (base.qualifiedName != BUTTON || base !is PsiCompiledElement) continue
                    val submit = anonymous.methods.singleOrNull { it.name == "onSubmit" } ?: continue
                    source(submit)
                    val target = submit.parameterList.parameters.singleOrNull() ?: continue
                    if (submit.returnType != PsiTypes.voidType() || target.type.canonicalText != TARGET ||
                        submit.findSuperMethods().none { it is PsiCompiledElement && it.containingClass?.qualifiedName == BUTTON }) continue
                    val dispatches = descendants(submit.body ?: continue).filterIsInstance<PsiMethodCallExpression>().filter { c ->
                        val resolved = c.resolveMethod()
                        resolved in bases && (actual.findMethodBySignature(resolved!!, true) == callback)
                    }
                    if (dispatches.isEmpty()) continue
                    if (dispatches.size != 1 || anonymous.fields.isNotEmpty() || anonymous.initializers.isNotEmpty() ||
                        anonymous.methods.any { it.name !in setOf("onSubmit", "onError") }) fail(submit, "The submission button has unreviewed dispatch or initialization overrides.")
                    val dispatch = dispatches.single()
                    val qualifier = dispatch.methodExpression.qualifierExpression
                    val qualifiedHost = qualifier is PsiThisExpression && qualifier.qualifier?.resolve()?.let { it == info.type || InheritanceUtil.isInheritor(info.type, (it as? PsiClass)?.qualifiedName ?: "") } == true
                    if (qualifier != null && !qualifiedHost || !reference(dispatch.argumentList.expressions.singleOrNull(), target) ||
                        PsiTreeUtil.getParentOfType(dispatch, PsiStatement::class.java)?.parent != submit.body) fail(dispatch, "The source hook must receive the unchanged target in one direct outer-host call.")
                    val statements = submit.body!!.statements
                    val at = statements.indexOfFirst { call(it) == dispatch }
                    if (at < 0) fail(dispatch, "The submission hook dispatch is not a direct statement.")
                    statements.take(at).forEach { statement ->
                        val assignment = (statement as? PsiExpressionStatement)?.expression as? PsiAssignmentExpression
                        val ref = assignment?.lExpression as? PsiReferenceExpression
                        val field = ref?.resolve() as? PsiField
                        if (assignment?.operationTokenType != JavaTokenType.EQ || field?.type != PsiTypes.booleanType() || field.hasModifierProperty(PsiModifier.STATIC) ||
                            !own(ref.qualifierExpression) || assignment.rExpression !is PsiLiteralExpression ||
                            generateSequence(info.type) { it.superClass }.take(depth).none { it == field.containingClass }) fail(statement, "The pre-dispatch bridge permits only owned boolean state declarations.")
                    }
                    val form = initial[host.slotParentNodeId] ?: fail(allocation, "The guarded form is unavailable.")
                    val formType = JavaPsiFacade.getInstance(page.project).findClasses(form.componentType, page.resolveScope).singleOrNull()
                    if (formType == null || formType.qualifiedName != FORM && !InheritanceUtil.isInheritor(formType, FORM))
                        fail(allocation, "The guarded content parent must be the button's declared Form.")
                    val parameters = allocation.resolveConstructor()?.parameterList?.parameters.orEmpty()
                    allocation.argumentList?.expressions.orEmpty().forEachIndexed { index, argument ->
                        if ((parameters.getOrNull(index)?.type as? PsiClassType)?.resolve()?.qualifiedName == FORM &&
                            !(argument is PsiLiteralExpression && argument.value == null)) {
                            val field = (argument as? PsiReferenceExpression)?.takeIf { own(it.qualifierExpression) }?.resolve() as? PsiField
                            val declared = info.fields[field]?.let { id -> info.nodes.single { it.nodeId == id } }
                            if (declared?.javaLocation != form.javaLocation) fail(argument, "The explicit submission form differs from the guarded owned form.")
                        }
                    }
                    val trigger = primary.nodes.singleOrNull { it.javaLocation == location(allocation) && under(it.nodeId, host.hostNodeId) }
                        ?: fail(allocation, "The submission trigger does not have one source attachment.")
                    ancestry(trigger.nodeId)
                    if (trigger.parentNodeId != host.slotParentNodeId || info.fields.none { (field, id) ->
                            info.nodes.single { it.nodeId == id }.javaLocation == trigger.javaLocation && field.hasModifierProperty(PsiModifier.PRIVATE) && field.hasModifierProperty(PsiModifier.FINAL)
                        }) fail(allocation, "The trigger must be a private-final owned button directly in the guarded form.")
                    bridges += Bridge(submit, dispatch, trigger)
                }
            }
            return bridges.singleOrNull().also { if (bridges.size > 1) fail(callback, "The callback has ambiguous submission routes.") }
        }
}
private const val TARGET="org.apache.wicket.ajax.AjaxRequestTarget"
private const val FORM="org.apache.wicket.markup.html.form.Form"
private const val BUTTON="org.apache.wicket.ajax.markup.html.form.AjaxButton"
private fun location(e:PsiElement)=SourceLocation(e.containingFile.virtualFile.path,e.textOffset)
