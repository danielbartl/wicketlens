package dev.wicketlens.analysis

import com.intellij.psi.*
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.TypeConversionUtil

/** Identity only: a fresh local owner, a pure getter and its private-final framework child. */
internal class WicketOwnedComponentAccessor(
    private val tick:(PsiElement)->Unit,
    private val source:(PsiElement)->Unit,
    private val inspectInterface:(PsiClass)->Unit,
    private val fail:(PsiElement,String)->Nothing,
) {
    data class Proof(val accessor:PsiMethod,val field:PsiField,val ownerAllocation:PsiNewExpression,val childAllocation:PsiNewExpression,val owners:List<PsiClass>,val constructors:Set<PsiMethod>)
    private data class Scan(val references:List<PsiReferenceExpression>,val writes:List<PsiAssignmentExpression>)
    private val scans=mutableMapOf<PsiElement,Scan>()
    private val completed=mutableSetOf<PsiMethodCallExpression>()

    private fun scan(at:PsiElement):Scan=scans.getOrPut(at) {
        val refs=mutableListOf<PsiReferenceExpression>();val writes=mutableListOf<PsiAssignmentExpression>()
        at.accept(object:JavaRecursiveElementWalkingVisitor() {
            override fun visitElement(element:PsiElement) {tick(element);super.visitElement(element)}
            override fun visitReferenceExpression(expression:PsiReferenceExpression) {refs+=expression;super.visitReferenceExpression(expression)}
            override fun visitAssignmentExpression(expression:PsiAssignmentExpression) {writes+=expression;super.visitAssignmentExpression(expression)}
        });Scan(refs,writes)
    }

    fun inspect(call:PsiMethodCallExpression):Proof {
        source(call)
        val add=call.resolveMethod()?.takeIf(::behaviorAddition) ?: fail(call,"Owned accessor decoration requires the compiled behavior-addition method.")
        source(add)
        if(!call.resolveMethodGenerics().isValidResult || call.argumentList.expressionCount!=1)
            fail(call,"Owned accessor decoration requires one resolved attribute modifier.")
        modifier(call.argumentList.expressions.single(),literal=true)
        val getter=call.methodExpression.qualifierExpression as? PsiMethodCallExpression ?: fail(call,"Owned accessor decoration requires a direct getter call.")
        if(getter.argumentList.expressionCount!=0 || !getter.resolveMethodGenerics().isValidResult)
            fail(getter,"Owned component accessors take no inputs.")
        val receiver=getter.methodExpression.qualifierExpression as? PsiReferenceExpression ?: fail(getter,"An owned accessor requires a fresh local owner.")
        val local=receiver.resolve() as? PsiLocalVariable ?: fail(receiver,"An owned accessor requires a direct local allocation.")
        val method=PsiTreeUtil.getParentOfType(call,PsiMethod::class.java) ?: fail(call,"Owned accessor setup has no source method.")
        source(method)
        if(receiver.qualifierExpression!=null || local.parent !is PsiDeclarationStatement || local.parent.parent!=method.body ||
            local.textRange.endOffset>=call.textOffset || statement(call)?.parent!=method.body)
            fail(receiver,"Owned accessor setup requires an earlier direct declaration in the same method.")
        val allocation=local.initializer as? PsiNewExpression ?: fail(local,"Owned accessors cannot borrow aliased or factory-returned owners.")
        if(allocation.qualifier!=null || !allocation.resolveMethodGenerics().isValidResult)
            fail(allocation,"The accessor owner allocation is qualified or unresolved.")
        val body=scan(method.body!!)
        if(body.writes.any {(it.lExpression as? PsiReferenceExpression)?.resolve()==local})
            fail(local,"An owned accessor cannot use a reassigned local.")
        body.references.filter {it.resolve()==local && it.textOffset<call.textOffset && it!=receiver}.forEach {use ->
            val member=use.parent as? PsiReferenceExpression
            val previous=member?.parent as? PsiMethodCallExpression
            val decoration=previous?.parent as? PsiReferenceExpression
            val decorated=decoration?.parent as? PsiMethodCallExpression
            if(previous==null || previous.methodExpression.qualifierExpression!=use || decorated !in completed)
                fail(use,"The accessor owner escaped or had unreviewed uses before decoration.")
        }
        var type=allocation.anonymousClass ?: allocation.classReference?.resolve() as? PsiClass ?: fail(allocation,"The accessor owner type is unavailable.")
        val declaration=getter.resolveMethod() ?: fail(getter,"The component accessor does not resolve.")
        if(declaration is PsiCompiledElement || declaration.hasModifierProperty(PsiModifier.STATIC))
            fail(getter,"Owned accessors require a source instance method.")
        source(declaration)
        val chain=mutableListOf<PsiClass>();var selected:PsiMethod?=null
        while(type !is PsiCompiledElement) {
            source(type)
            if(type in chain || chain.size>=12) fail(type,"Owned accessor dispatch exceeds its source-ancestry bound.")
            chain+=type
            type.implementsListTypes.forEach {inspectInterface(it.resolve() ?: fail(type,"An accessor-owner interface is unresolved."))}
            if(selected==null) selected=type.findMethodBySignature(declaration,false)
            type=type.superClass ?: fail(type,"The accessor owner superclass is unavailable.")
        }
        val accessor=selected ?: fail(getter,"The selected source accessor implementation is unavailable.")
        source(accessor)
        if(accessor.hasModifierProperty(PsiModifier.SYNCHRONIZED) || accessor.hasModifierProperty(PsiModifier.STATIC) || accessor.parameterList.parametersCount!=0)
            fail(accessor,"The selected accessor must be a pure zero-input field return.")
        val returned=(accessor.body?.statements?.singleOrNull() as? PsiReturnStatement)?.returnValue as? PsiReferenceExpression
            ?: fail(accessor,"The selected accessor must only return its owned field directly.")
        val field=returned.resolve() as? PsiField ?: fail(returned,"The accessor result is not an owned field.")
        source(field)
        if(field.containingClass!=accessor.containingClass || !self(returned.qualifierExpression,field.containingClass!!) ||
            !field.hasModifierProperty(PsiModifier.PRIVATE) || !field.hasModifierProperty(PsiModifier.FINAL) || field.hasModifierProperty(PsiModifier.STATIC))
            fail(field,"The accessor must return its own private-final instance field.")
        val owner=field.containingClass!!;val ownerScan=scan(owner)
        val constructors=constructorPath(allocation,owner)
        val writes=ownerScan.writes.filter {(it.lExpression as? PsiReferenceExpression)?.resolve()==field}
        val assignment=writes.singleOrNull()
        val child=if(field.initializer!=null && writes.isEmpty()) field.initializer as? PsiNewExpression else {
            if(field.initializer!=null || assignment==null || assignment.operationTokenType!=JavaTokenType.EQ ||
                !self((assignment.lExpression as PsiReferenceExpression).qualifierExpression,owner)) fail(field,"The accessor field allocation must be unique.")
            val constructor=PsiTreeUtil.getParentOfType(assignment,PsiMethod::class.java)?.takeIf {it.isConstructor}
                ?: fail(assignment,"The accessor field requires constructor allocation.")
            if(statement(assignment)?.parent!=constructor.body || constructor.containingClass!=owner)
                fail(assignment,"The accessor field allocation must be a direct constructor statement.")
            if(constructor !in constructors) fail(assignment,"The selected constructor does not establish the accessor field.")
            assignment.rExpression as? PsiNewExpression
        } ?: fail(field,"The accessor child must be allocated directly.")
        source(child)
        val childConstructor=child.resolveConstructor()
        if(child.qualifier!=null || child.anonymousClass!=null || childConstructor !is PsiCompiledElement ||
            childConstructor.containingClass?.qualifiedName!=CONTAINER || parameters(childConstructor)!=listOf("java.lang.String") ||
            !child.resolveMethodGenerics().isValidResult || child.argumentList?.expressions?.singleOrNull().let {it !is PsiLiteralExpression || it.value !is String})
            fail(child,"Accessor children require the exact fresh framework WebMarkupContainer(String) construction.")
        source(childConstructor)
        ownerScan.references.filter {it.resolve()==field && it!=returned && it!=assignment?.lExpression}.forEach {use ->
            val constructor=PsiTreeUtil.getParentOfType(use,PsiMethod::class.java)
            if(constructor?.isConstructor!=true || constructor.containingClass!=owner || !self(use.qualifierExpression,owner))
                fail(use,"The accessor child escapes its constructor and selected pure getter.")
            val member=use.parent as? PsiReferenceExpression
            val useCall=member?.parent as? PsiMethodCallExpression
            if(useCall!=null && useCall.methodExpression.qualifierExpression==use) {
                val target=useCall.resolveMethod()
                if(target !is PsiCompiledElement) fail(use,"Source calls cannot acquire the accessor child.")
                source(target)
                when {
                    behaviorAddition(target) -> useCall.argumentList.expressions.forEach {modifier(it,literal=false)}
                    componentAddition(target) -> useCall.argumentList.expressions.forEach {argument ->
                        val label=argument as? PsiNewExpression;val targetLabel=label?.resolveConstructor()
                        if(label?.anonymousClass!=null || label?.qualifier!=null || targetLabel !is PsiCompiledElement || targetLabel.containingClass?.qualifiedName!="org.apache.wicket.markup.html.basic.Label")
                            fail(argument,"Accessor-child constructor contents require direct framework labels.")
                        source(targetLabel)
                    }
                    else -> fail(use,"The accessor child has unreviewed constructor uses.")
                }
            } else {
                val args=use.parent as? PsiExpressionList;val attachment=args?.parent as? PsiMethodCallExpression
                val target=attachment?.resolveMethod()
                if(target==null || !componentAddition(target) || !self(attachment.methodExpression.qualifierExpression,owner) || args.expressionCount!=1)
                    fail(use,"The accessor child escapes its own direct attachment.")
                source(target)
            }
        }
        return Proof(accessor,field,allocation,child,chain,constructors)
    }

    fun complete(call:PsiMethodCallExpression) {completed+=call}

    private fun constructorPath(allocation:PsiNewExpression,owner:PsiClass):Set<PsiMethod> {
        val path=mutableSetOf<PsiMethod>();var current=allocation.resolveConstructor() ?: fail(allocation,"The accessor owner constructor is unresolved.")
        while(current !is PsiCompiledElement) {
            source(current)
            if(current in path || path.size>=12) fail(current,"Accessor constructor delegation exceeds its source-call bound.")
            path+=current
            val statements=current.body?.statements ?: fail(current,"The accessor owner constructor body is unavailable.")
            val first=(statements.firstOrNull() as? PsiExpressionStatement)?.expression as? PsiMethodCallExpression
            if(first?.methodExpression?.referenceName !in setOf("super","this")) {
                if(current.containingClass==owner) return path
                fail(current,"Accessor ownership requires explicit source constructor delegation.")
            }
            if(current.containingClass==owner && first?.methodExpression?.referenceName=="super") return path
            if(!first!!.resolveMethodGenerics().isValidResult) fail(first,"Accessor constructor delegation does not resolve.")
            current=first.resolveMethod() ?: fail(first,"The accessor constructor target is unavailable.")
        }
        fail(allocation,"The selected constructor does not reach the accessor field owner.")
    }

    private fun modifier(expression:PsiExpression,literal:Boolean) {
        val call=expression as? PsiMethodCallExpression ?: fail(expression,"Owned accessor decoration requires a direct AttributeModifier.replace factory.")
        val method=call.resolveMethod();val qualifier=call.methodExpression.qualifierExpression
        if(method !is PsiCompiledElement || method.containingClass?.qualifiedName!="org.apache.wicket.AttributeModifier" || method.name!="replace" ||
            !method.hasModifierProperty(PsiModifier.STATIC) || parameters(method) !in listOf(listOf("java.lang.String","java.io.Serializable"),listOf("java.lang.String","org.apache.wicket.model.IModel")) ||
            qualifier!=null && (qualifier !is PsiReferenceExpression || qualifier.resolve() !is PsiClass) || !call.resolveMethodGenerics().isValidResult)
            fail(call,"The attribute modifier must use its exact compiled factory.")
        source(method)
        val args=call.argumentList.expressions
        if(args.size!=2 || args[0].let {it !is PsiLiteralExpression || it.value !is String || (it.value as String).isEmpty()} ||
            literal && args[1].let {it !is PsiLiteralExpression || it.value !is String})
            fail(call,"Accessor decoration requires literal attribute and replacement strings.")
        if(!literal && args[1] !is PsiLiteralExpression) {
            val model=args[1] as? PsiTypeCastExpression
            if(model?.type?.canonicalText!="org.apache.wicket.model.IModel<java.lang.String>" || model.operand !is PsiMethodReferenceExpression)
                fail(args[1],"Accessor-child initialization permits only stored framework model method references.")
            val protocol=(model.type as? PsiClassType)?.resolve() ?: fail(model,"The stored model interface is unavailable.")
            source(protocol)
            if(protocol !is PsiCompiledElement) fail(protocol,"Stored modifier callbacks require the reviewed compiled model interface.")
        }
        val behavior=method.containingClass!!.superClass ?: fail(method,"AttributeModifier binding is unavailable.")
        source(behavior)
        if(behavior !is PsiCompiledElement || behavior.qualifiedName!="org.apache.wicket.behavior.Behavior")
            fail(behavior,"AttributeModifier requires its reviewed compiled behavior binding.")
    }

    companion object {
        private const val CONTAINER="org.apache.wicket.markup.html.WebMarkupContainer"
        private fun parameters(method:PsiMethod)=method.parameterList.parameters.map {TypeConversionUtil.erasure((it.type as? PsiEllipsisType)?.toArrayType() ?: it.type).canonicalText}
        fun candidate(call:PsiMethodCallExpression):Boolean {
            if(call.resolveMethod()?.let(::behaviorAddition)!=true) return false
            val getter=call.methodExpression.qualifierExpression as? PsiMethodCallExpression ?: return false
            val receiver=getter.methodExpression.qualifierExpression as? PsiReferenceExpression ?: return false
            if(receiver.resolve() !is PsiLocalVariable || getter.argumentList.expressionCount!=0) return false
            val method=getter.resolveMethod() ?: return false
            if(method is PsiCompiledElement) return false
            return ((method.body?.statements?.singleOrNull() as? PsiReturnStatement)?.returnValue as? PsiReferenceExpression)?.resolve() is PsiField
        }
        fun behaviorAddition(method:PsiMethod)=method is PsiCompiledElement && method.containingClass?.qualifiedName=="org.apache.wicket.Component" && method.name=="add" &&
            !method.hasModifierProperty(PsiModifier.STATIC) && parameters(method)==listOf("org.apache.wicket.behavior.Behavior[]")
        private fun componentAddition(method:PsiMethod)=method is PsiCompiledElement && method.containingClass?.qualifiedName=="org.apache.wicket.MarkupContainer" && method.name=="add" &&
            !method.hasModifierProperty(PsiModifier.STATIC) && parameters(method)==listOf("org.apache.wicket.Component[]")
        private fun self(expression:PsiExpression?,owner:PsiClass)=expression==null || expression is PsiThisExpression && (expression.qualifier==null || expression.qualifier?.resolve()==owner)
        private fun statement(at:PsiElement)=PsiTreeUtil.getParentOfType(at,PsiStatement::class.java)
    }
}
