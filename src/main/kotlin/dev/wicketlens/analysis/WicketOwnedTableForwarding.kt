package dev.wicketlens.analysis

import com.intellij.psi.*
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.TypeConversionUtil

/** One owned table reference forwarded into AbstractToolbar. No cache, attachment or callback authority. */
internal class WicketOwnedTableForwarding(
    private val tick:(PsiElement)->Unit,
    private val source:(PsiElement)->Unit,
    private val constant:(PsiExpression)->Any?,
    private val inspectTable:(PsiClass)->Unit,
    private val inspectInterface:(PsiClass)->Unit,
    private val reviewedReference:(PsiReferenceExpression)->Boolean,
    private val constructorReferences:(PsiMethod)->List<PsiReferenceExpression>,
    private val constructorAssignments:(PsiMethod)->List<PsiAssignmentExpression>,
    private val find:(String)->PsiClass?,
    private val fail:(PsiElement,String)->Nothing,
) {
    data class Proof(val allocation:PsiNewExpression,val constructor:PsiMethod,val delegation:PsiMethodCallExpression,
        val tableField:PsiField,val tableAllocation:PsiNewExpression,val ownerConstructor:PsiMethod)
    private val scans=mutableMapOf<PsiElement,List<PsiElement>>()
    private fun scan(at:PsiElement):List<PsiElement> = scans.getOrPut(at) {
        source(at);val result=linkedSetOf<PsiElement>()
        at.accept(object:JavaRecursiveElementWalkingVisitor() {
            override fun visitElement(element:PsiElement) {
                if(element is PsiWhiteSpace || element is PsiJavaToken || element is PsiIdentifier || element is PsiComment) return
                tick(element)
                if(element is PsiReferenceExpression || element is PsiAssignmentExpression) result+=element
                super.visitElement(element)
            }
        });result.toList()
    }
    fun inspect(allocation:PsiNewExpression,owner:PsiClass):Proof {
        source(owner)
        val type=allocation.classReference?.resolve() as? PsiClass ?: fail(allocation,"The owned toolbar source type is unavailable.")
        source(type)
        if(allocation.anonymousClass!=null || allocation.qualifier!=null || !allocation.resolveMethodGenerics().isValidResult ||
            type is PsiCompiledElement || type.containingClass!=owner || !type.hasModifierProperty(PsiModifier.FINAL) ||
            type.hasModifierProperty(PsiModifier.STATIC) || type.superClass?.let {it is PsiCompiledElement && it.qualifiedName==TOOLBAR}!=true)
            fail(allocation,"Owned table forwarding requires a final source-owned direct AbstractToolbar allocation.")
        if(type.initializers.isNotEmpty()) fail(type,"Owned toolbar initializer blocks require a separate effect contract.")
        type.implementsListTypes.forEach {inspectInterface(it.resolve() ?: fail(type,"The owned toolbar interface is unresolved."))}
        type.fields.forEach {field -> source(field)
            if(field.hasModifierProperty(PsiModifier.STATIC)) {
                if(!field.hasModifierProperty(PsiModifier.FINAL) || field.initializer?.let(constant)==null) fail(field,"Owned toolbar static effects are unreviewed.")
            } else if(!field.hasModifierProperty(PsiModifier.PRIVATE) || !field.hasModifierProperty(PsiModifier.FINAL) || field.initializer!=null ||
                (field.type as? PsiClassType)?.resolve()?.let {it is PsiCompiledElement && it.qualifiedName=="org.apache.wicket.markup.html.WebMarkupContainer"}!=true)
                fail(field,"Owned toolbar instance storage requires private-final framework containers established by its constructor.")
        }
        type.methods.filter {it.isPhysical && !it.isConstructor}.forEach {method -> source(method)
            if(method.hasModifierProperty(PsiModifier.STATIC) || method.parameterList.parametersCount!=0 || method.name !in setOf("onConfigure","onBeforeRender","onDetach") ||
                method.findDeepestSuperMethods().none {it is PsiCompiledElement && it.containingClass?.qualifiedName=="org.apache.wicket.Component"})
                fail(method,"Owned toolbar source overrides are outside the reviewed deferred lifecycle hooks.")
        }
        val constructor=type.constructors.singleOrNull {it.isPhysical} ?: fail(type,"Owned toolbar forwarding requires one selected source constructor.")
        source(constructor)
        val parameter=constructor.parameterList.parameters.singleOrNull() ?: fail(constructor,"Owned toolbar construction requires one DataTable input.")
        if(constructor!=allocation.resolveConstructor() || constructor.isVarArgs || TypeConversionUtil.erasure(parameter.type).canonicalText!=TABLE)
            fail(constructor,"The owned toolbar constructor input is unreviewed.")
        val delegation=(constructor.body?.statements?.firstOrNull() as? PsiExpressionStatement)?.expression as? PsiMethodCallExpression
            ?: fail(constructor,"The owned toolbar must directly delegate its table to AbstractToolbar.")
        val argument=delegation.argumentList.expressions.singleOrNull() as? PsiReferenceExpression
        val target=delegation.resolveMethod()
        if(delegation.methodExpression.referenceName!="super" || delegation.methodExpression.qualifierExpression!=null ||
            !delegation.resolveMethodGenerics().isValidResult || argument?.qualifierExpression!=null || argument?.resolve()!=parameter ||
            target?.let {it is PsiCompiledElement && it.containingClass?.qualifiedName==TOOLBAR && WicketDataTableToolbars.constructor(it)}!=true)
            fail(delegation,"The toolbar must forward its unchanged table into the exact compiled AbstractToolbar constructor.")
        source(target!!)
        if(scan(constructor).filterIsInstance<PsiReferenceExpression>().filter {it.resolve()==parameter}!=listOf(argument))
            fail(parameter,"The owned table parameter escapes its single constructor forwarding operation.")
        val input=allocation.argumentList?.expressions?.singleOrNull() as? PsiReferenceExpression
            ?: fail(allocation,"Owned toolbar construction requires a direct private table field.")
        val field=input.resolve() as? PsiField ?: fail(input,"Borrowed table inputs and aliases are unreviewed.")
        source(field)
        if(field.containingClass!=owner || !self(input.qualifierExpression,owner) || !field.hasModifierProperty(PsiModifier.PRIVATE) ||
            !field.hasModifierProperty(PsiModifier.FINAL) || field.hasModifierProperty(PsiModifier.STATIC)) fail(input,"The forwarded table is not private-final storage of this source owner.")
        val ownerConstructor=owner.constructors.singleOrNull {it.isPhysical} ?: fail(owner,"Owned table forwarding requires one source owner constructor.")
        source(ownerConstructor)
        if(owner.initializers.isNotEmpty()) fail(owner,"Owned table initializer blocks require a separate effect contract.")
        owner.fields.mapNotNull {it.initializer}.filter {it!=field.initializer}.forEach {initializer ->
            if(scan(initializer).filterIsInstance<PsiReferenceExpression>().any {it.resolve()==field}) fail(initializer,"The owned table escapes into another field initializer.")
        }
        val writes=constructorAssignments(ownerConstructor).onEach(tick).filter {(it.lExpression as? PsiReferenceExpression)?.resolve()==field}
        val write=writes.singleOrNull()
        val creation=if(field.initializer!=null && writes.isEmpty()) field.initializer as? PsiNewExpression else {
            if(field.initializer!=null || write==null || write.operationTokenType!=JavaTokenType.EQ ||
                !self((write.lExpression as PsiReferenceExpression).qualifierExpression,owner) || statement(write)?.parent!=ownerConstructor.body)
                fail(field,"The owned table must have one direct constructor allocation without reassignment.")
            write.rExpression as? PsiNewExpression
        } ?: fail(field,"The owned table allocation is borrowed or unavailable.")
        if(creation.anonymousClass!=null || creation.qualifier!=null) fail(creation,"The owned table allocation must be ordinary and unqualified.")
        val table=creation.classReference?.resolve() as? PsiClass ?: fail(creation,"The owned table type is unavailable.")
        if(!WicketDataTableConstruction.candidate(table)) fail(creation,"The owned table requires the reviewed DataTable construction boundary.")
        inspectTable(table)
        var attachments=0
        constructorReferences(ownerConstructor).forEach {reference ->
            tick(reference)
            if(reference.resolve()!=field || reference==write?.lExpression) return@forEach
            if(generateSequence(reference.parent) {it.parent}.takeWhile {it!=ownerConstructor}.any {it is PsiMethod || it is PsiLambdaExpression || it is PsiClass}) return@forEach
            if(reviewedReference(reference)) return@forEach
            val member=reference.parent as? PsiReferenceExpression;val call=member?.parent as? PsiMethodCallExpression;val method=call?.resolveMethod()
            if(member?.qualifierExpression==reference && method is PsiCompiledElement && method.containingClass?.qualifiedName=="org.apache.wicket.Component" &&
                method.name=="setOutputMarkupId" && call.argumentList.expressions.singleOrNull().let {it is PsiLiteralExpression && it.value is Boolean} && statement(call)?.parent==ownerConstructor.body) return@forEach
            val args=reference.parent as? PsiExpressionList;val addition=args?.parent as? PsiMethodCallExpression;val attach=addition?.resolveMethod()
            if(attach is PsiCompiledElement && attach.containingClass?.qualifiedName=="org.apache.wicket.MarkupContainer" && attach.name=="add" &&
                args.expressions.size==1 && self(addition.methodExpression.qualifierExpression,owner) && statement(addition)?.parent==ownerConstructor.body) {source(attach);attachments++;return@forEach}
            fail(reference,"The owned table escaped or had unreviewed setup in its constructor.")
        }
        if(attachments!=1) fail(field,"The owned table requires one direct attachment to its own source owner.")
        val framework=find(TABLE) ?: fail(allocation,"The compiled DataTable source dependency is unavailable.")
        val id=framework.findMethodsByName("newToolbarId",false).singleOrNull {it is PsiCompiledElement && !it.hasModifierProperty(PsiModifier.STATIC) && it.parameterList.parametersCount==0 && it.returnType?.canonicalText=="java.lang.String"}
            ?: fail(framework,"The compiled toolbar ID-allocation boundary is unavailable.")
        source(id);source(type.superClass!!)
        return Proof(allocation,constructor,delegation,field,creation,ownerConstructor)
    }
    companion object {
        private const val TABLE=WicketDataTableToolbars.TABLE
        private const val TOOLBAR=WicketDataTableToolbars.TOOLBAR
        fun candidate(allocation:PsiNewExpression)=(allocation.classReference?.resolve() as? PsiClass)?.superClass?.qualifiedName==TOOLBAR
        private fun self(at:PsiExpression?,owner:PsiClass)=at==null || at is PsiThisExpression && (at.qualifier==null || at.qualifier?.resolve()==owner)
        private fun statement(at:PsiElement):PsiStatement?=PsiTreeUtil.getParentOfType(at,PsiStatement::class.java)
    }
}
