package dev.wicketlens.analysis

import com.intellij.psi.*
import com.intellij.psi.search.LocalSearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.util.PsiTreeUtil

/** Source null-guarded ownership, not a runtime cache-state evaluation. */
internal class WicketToolbarCacheContract(
    private val tick:(PsiElement)->Unit,
    private val source:(PsiElement)->Unit,
    private val attachment:(PsiMethodCallExpression)->PsiMethod,
    private val fail:(PsiElement,String)->Nothing,
) {
    data class Proof(val field:PsiField,val assignment:PsiAssignmentExpression,val guard:PsiIfStatement,
        val registration:PsiMethodCallExpression,val argument:PsiReferenceExpression,val cells:Set<PsiField>)
    fun inspect(assignment:PsiAssignmentExpression,forward:WicketOwnedTableForwarding.Proof):Proof {
        tick(assignment)
        val left=assignment.lExpression as? PsiReferenceExpression ?: fail(assignment,"The toolbar cache assignment needs its own field receiver.")
        val field=left.resolve() as? PsiField ?: fail(left,"The toolbar cache is not a source field.")
        source(field)
        val owner=forward.tableField.containingClass!!
        val method=PsiTreeUtil.getParentOfType(assignment,PsiMethod::class.java) ?: fail(assignment,"The cache setter is unavailable.")
        source(method)
        if(method.isConstructor || method.hasModifierProperty(PsiModifier.STATIC) || method.containingClass!=owner ||
            field.containingClass!=owner || !self(left.qualifierExpression,owner) || !field.hasModifierProperty(PsiModifier.PRIVATE) ||
            field.hasModifierProperty(PsiModifier.STATIC) || field.hasModifierProperty(PsiModifier.FINAL) || field.initializer!=null ||
            (field.type as? PsiClassType)?.resolve()!=forward.constructor.containingClass || assignment.operationTokenType!=JavaTokenType.EQ ||
            assignment.rExpression!=forward.allocation)
            fail(assignment,"The toolbar cache must be a private default-null field assigned its reviewed owned allocation.")
        val statement=assignment.parent as? PsiExpressionStatement
        val block=statement?.parent as? PsiCodeBlock
        val guard=(block?.parent as? PsiBlockStatement)?.parent as? PsiIfStatement
            ?: fail(assignment,"The toolbar cache requires a direct null-guarded source block.")
        if(guard.parent!=method.body || !nullGuard(guard,field,owner) || block.statements.size!=2 || block.statements.first()!=statement)
            fail(guard,"Toolbar initialization must have one direct field == null guard containing only allocation and registration.")
        val registration=(block.statements[1] as? PsiExpressionStatement)?.expression as? PsiMethodCallExpression
            ?: fail(guard,"The cached toolbar needs one direct same-table registration.")
        val receiver=registration.methodExpression.qualifierExpression as? PsiReferenceExpression
        val argument=registration.argumentList.expressions.singleOrNull() as? PsiReferenceExpression
        if(receiver?.resolve()!=forward.tableField || !self(receiver.qualifierExpression,owner) || argument?.resolve()!=field || !self(argument.qualifierExpression,owner))
            fail(registration,"The cached toolbar must register on the same directly owned table.")
        attachment(registration)
        val writes=linkedSetOf<PsiAssignmentExpression>();val cells=linkedSetOf<PsiField>();var replacements=0
        ReferencesSearch.search(field,LocalSearchScope(owner)).forEach(com.intellij.util.Processor {reference ->
            val at=reference.element;tick(at)
            if(PsiTreeUtil.getParentOfType(at,PsiComment::class.java)!=null) return@Processor true
            val use=at as? PsiReferenceExpression ?: fail(at,"The cache has an unreviewed source reference.")
            val write=use.parent as? PsiAssignmentExpression
            if(write?.lExpression==use) {writes+=write;if(write==assignment)return@Processor true}
            if(use==argument || PsiTreeUtil.isAncestor(guard.condition,use,false)) return@Processor true
            val slot=use.parent as? PsiReferenceExpression;val cell=slot?.resolve() as? PsiField
            val operation=(slot?.parent as? PsiReferenceExpression)?.parent as? PsiMethodCallExpression
            val target=operation?.resolveMethod()
            if(cell!=null && slot.qualifierExpression==use && cell.containingClass==forward.constructor.containingClass &&
                cell.hasModifierProperty(PsiModifier.PRIVATE) && cell.hasModifierProperty(PsiModifier.FINAL) && !cell.hasModifierProperty(PsiModifier.STATIC) &&
                (cell.type as? PsiClassType)?.resolve()?.qualifiedName=="org.apache.wicket.markup.html.WebMarkupContainer" &&
                target is PsiCompiledElement && target.containingClass?.qualifiedName=="org.apache.wicket.MarkupContainer" && target.name=="addOrReplace" &&
                operation.argumentList.expressionCount==1 && operation.resolveMethodGenerics().isValidResult &&
                PsiTreeUtil.getParentOfType(operation,PsiStatement::class.java)?.parent==method.body && operation.textOffset>guard.textRange.endOffset &&
                PsiTreeUtil.getParentOfType(operation,PsiMethod::class.java)==method && self(use.qualifierExpression,owner)) {
                val writesToCell=PsiTreeUtil.findChildrenOfType(forward.constructor,PsiAssignmentExpression::class.java).filter {
                    (it.lExpression as? PsiReferenceExpression)?.resolve()==cell
                }.onEach(tick)
                val creation=writesToCell.singleOrNull()?.rExpression as? PsiNewExpression
                val constructor=creation?.resolveConstructor()
                if(cell.initializer!=null || creation==null || creation.anonymousClass!=null || creation.qualifier!=null ||
                    creation.classReference?.resolve()?.let {it is PsiClass && it is PsiCompiledElement && it.qualifiedName=="org.apache.wicket.markup.html.WebMarkupContainer"}!=true ||
                    constructor?.let(WicketComponentIdentity::frameworkConstructor)!=true)
                    fail(cell,"The cache replacement cell must retain its uniquely allocated plain framework container.")
                source(constructor!!)
                source(cell);source(target);cells+=cell;replacements++;return@Processor true
            }
            fail(use,"The toolbar cache escapes its guard, registration and private-cell replacement.")
        })
        if(writes!=setOf(assignment) || replacements>1) fail(field,"The toolbar cache has competing writes or content placements.")
        return Proof(field,assignment,guard,registration,argument,cells)
    }
    companion object {
        fun nullGuard(guard:PsiIfStatement,field:PsiField,owner:PsiClass):Boolean {
            val condition=guard.condition
            val operands=when(condition) {is PsiBinaryExpression->listOfNotNull(condition.lOperand,condition.rOperand);is PsiPolyadicExpression->condition.operands.toList();else->emptyList()}
            val operator=when(condition) {is PsiBinaryExpression->condition.operationTokenType;is PsiPolyadicExpression->condition.operationTokenType;else->null}
            fun ref(at:PsiExpression) = at is PsiReferenceExpression && at.resolve()==field && self(at.qualifierExpression,owner)
            fun nil(at:PsiExpression) = at is PsiLiteralExpression && at.value==null
            return guard.elseBranch==null && operator==JavaTokenType.EQEQ && operands.size==2 &&
                (ref(operands[0])&&nil(operands[1]) || nil(operands[0])&&ref(operands[1]))
        }
        private fun self(at:PsiExpression?,owner:PsiClass)=at==null || at is PsiThisExpression && (at.qualifier==null || at.qualifier?.resolve()==owner)
    }
}
