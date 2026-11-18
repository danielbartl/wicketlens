package dev.wicketlens.analysis

import com.intellij.psi.*
import com.intellij.psi.util.InheritanceUtil
import com.intellij.psi.util.TypeConversionUtil

/** Fresh inline component -> private behavior -> unchanged exact Model storage. No callback evaluation. */
internal class WicketFluentComponentConfiguration(
    private val tick:(PsiElement)->Unit,
    private val source:(PsiElement)->Unit,
    private val constant:(PsiExpression)->Any?,
    private val graphType:(PsiType?)->Boolean,
    private val inspectInterface:(PsiClass)->Unit,
    private val fail:(PsiElement,String)->Nothing,
) {
    data class Proof(val allocation:PsiNewExpression,val owners:List<PsiClass>,val constructors:Set<PsiMethod>,
        val behaviors:Set<PsiClass>,val methods:Set<PsiMethod>,val calls:List<PsiMethodCallExpression>)
    private val scans=mutableMapOf<PsiClass,List<PsiElement>>()
    private val enums=mutableSetOf<PsiClass>()
    private fun scan(type:PsiClass):List<PsiElement> = scans.getOrPut(type) {
        source(type);val result=mutableListOf<PsiElement>()
        type.accept(object:JavaRecursiveElementWalkingVisitor() {
            override fun visitElement(element:PsiElement) {
                // Tokens, identifiers and documentation cannot hide additional references or writes.
                // Charge semantic nodes while retaining the shared budget and cancellation checks.
                if(element is PsiWhiteSpace || element is PsiJavaToken || element is PsiIdentifier || element is PsiComment) return
                tick(element)
                if(element is PsiReferenceExpression || element is PsiAssignmentExpression) result+=element
                super.visitElement(element)
            }
        });result
    }
    private fun writes(field:PsiField)=scan(field.containingClass!!).filterIsInstance<PsiAssignmentExpression>()
        .filter {(it.lExpression as? PsiReferenceExpression)?.resolve()==field}
    private fun hierarchy(type:PsiClass):List<PsiClass> {
        val result=mutableListOf<PsiClass>();var current=type
        while(current !is PsiCompiledElement) {
            source(current)
            if(current in result || result.size>=12) fail(current,"Fluent configuration exceeds its source-ancestry bound.")
            result+=current
            if(current.initializers.isNotEmpty()) fail(current,"Fluent configuration cannot run source initializer blocks.")
            current.implementsListTypes.forEach {inspectInterface(it.resolve() ?: fail(current,"A fluent source interface is unresolved."))}
            current=current.superClass ?: fail(current,"The fluent source superclass is unavailable.")
        }
        source(current)
        return result
    }
    private fun selected(declared:PsiMethod,owners:List<PsiClass>):PsiMethod {
        val method=owners.firstNotNullOfOrNull {it.findMethodBySignature(declared,false)} ?: fail(declared,"The fluent source implementation is unavailable.")
        source(method)
        if(method is PsiCompiledElement || method.hasModifierProperty(PsiModifier.STATIC) || method.hasModifierProperty(PsiModifier.SYNCHRONIZED) || method.isVarArgs || method.parameterList.parametersCount!=1)
            fail(method,"Fluent configuration requires one ordinary source input.")
        return method
    }
    private fun operation(method:PsiMethod):PsiMethodCallExpression {
        source(method)
        val statements=method.body?.statements.orEmpty()
        val returned=(statements.lastOrNull() as? PsiReturnStatement)?.returnValue as? PsiThisExpression
        if(statements.size!=2 || returned==null || returned.qualifier!=null)
            fail(method,"A fluent setter must perform one reviewed storage operation and return its own receiver.")
        return (statements.first() as? PsiExpressionStatement)?.expression as? PsiMethodCallExpression
            ?: fail(method,"The fluent setter operation is unreviewed.")
    }
    private fun forward(call:PsiMethodCallExpression,method:PsiMethod) {
        val argument=call.argumentList.expressions.singleOrNull() as? PsiReferenceExpression
        if(!call.resolveMethodGenerics().isValidResult || argument?.qualifierExpression!=null || argument?.resolve()!=method.parameterList.parameters.single())
            fail(call,"Fluent inputs must be forwarded directly without evaluation.")
    }
    private fun ownField(call:PsiMethodCallExpression,owners:List<PsiClass>,final:Boolean):PsiField {
        val receiver=call.methodExpression.qualifierExpression as? PsiReferenceExpression ?: fail(call,"The setter requires owned source field storage.")
        val field=receiver.resolve() as? PsiField ?: fail(receiver,"The setter receiver is not an owned field.")
        source(field)
        if(field.containingClass !in owners || !self(receiver.qualifierExpression,field.containingClass!!) || !field.hasModifierProperty(PsiModifier.PRIVATE) ||
            field.hasModifierProperty(PsiModifier.STATIC) || final && !field.hasModifierProperty(PsiModifier.FINAL))
            fail(field,"Fluent storage requires a private instance field on the selected receiver.")
        return field
    }
    private fun constructors(allocation:PsiNewExpression):Set<PsiMethod> {
        val path=mutableSetOf<PsiMethod>();var current=allocation.resolveConstructor() ?: fail(allocation,"The fluent allocation constructor is unresolved.")
        while(current !is PsiCompiledElement) {
            source(current)
            if(!path.add(current) || path.size>12) fail(current,"Fluent constructor delegation exceeds its source-call bound.")
            val first=(current.body?.statements?.firstOrNull() as? PsiExpressionStatement)?.expression as? PsiMethodCallExpression
            if(first?.methodExpression?.referenceName !in setOf("super","this") || !first!!.resolveMethodGenerics().isValidResult)
                fail(current,"Fluent component constructors require explicit resolved delegation.")
            current=first.resolveMethod() ?: fail(first,"A fluent constructor target is unavailable.")
        }
        source(current)
        if(!WicketComponentIdentity.frameworkConstructor(current)) fail(current,"The fluent component framework constructor is unreviewed.")
        return path
    }
    fun inspect(outer:PsiMethodCallExpression):Proof {
        val calls=mutableListOf<PsiMethodCallExpression>();var cursor:PsiExpression=outer
        while(cursor is PsiMethodCallExpression) {
            tick(cursor)
            if(calls.size>=12) fail(cursor,"Fluent configuration exceeds its 12-operation chain bound.")
            if(!cursor.resolveMethodGenerics().isValidResult || cursor.argumentList.expressionCount!=1)
                fail(cursor,"A fluent chain requires resolved unary source setters.")
            calls+=cursor;cursor=cursor.methodExpression.qualifierExpression ?: fail(cursor,"A fluent chain requires a fresh inline allocation.")
        }
        val allocation=cursor as? PsiNewExpression ?: fail(cursor,"Fluent configuration cannot borrow a local, field, alias or factory result.")
        if(allocation.qualifier!=null || !allocation.resolveMethodGenerics().isValidResult) fail(allocation,"The fresh fluent allocation is qualified or unresolved.")
        val owners=hierarchy(allocation.anonymousClass ?: allocation.classReference?.resolve() as? PsiClass ?: fail(allocation,"The fluent allocation type is unresolved."))
        owners.forEach {owner -> owner.methods.filter {it.isPhysical && !it.isConstructor}.forEach {method ->
            if(method.findDeepestSuperMethods().any {it is PsiCompiledElement && it.containingClass?.qualifiedName?.startsWith("org.apache.wicket.")==true} && !deferred(method))
                fail(method,"A fluent component overrides an unreviewed framework construction hook.")
        }}
        val constructors=constructors(allocation);val behaviors=mutableSetOf<PsiClass>();val methods=mutableSetOf<PsiMethod>()
        calls.asReversed().forEach {call ->
            source(call)
            val declared=call.resolveMethod() ?: fail(call,"The fluent source call is unresolved.")
            val setter=selected(declared,owners);methods+=setter
            val forwardingCall=operation(setter);forward(forwardingCall,setter)
            val field=ownField(forwardingCall,owners,final=true)
            val assignment=writes(field).singleOrNull()
            val creation=if(field.initializer!=null && assignment==null) field.initializer as? PsiNewExpression else {
                if(field.initializer!=null || assignment==null || assignment.operationTokenType!=JavaTokenType.EQ ||
                    !self((assignment.lExpression as PsiReferenceExpression).qualifierExpression,field.containingClass!!)) fail(field,"The fluent behavior allocation is not unique.")
                val constructor=generateSequence(assignment.parent) {it.parent}.filterIsInstance<PsiMethod>().firstOrNull()
                if(constructor !in constructors || generateSequence(assignment.parent) {it.parent}.filterIsInstance<PsiStatement>().firstOrNull()?.parent!=constructor?.body)
                    fail(assignment,"The selected constructor does not directly establish fluent behavior storage.")
                assignment.rExpression as? PsiNewExpression
            } ?: fail(field,"The fluent behavior must be allocated directly.")
            if(creation.anonymousClass!=null || creation.qualifier!=null || creation.argumentList?.expressionCount!=0 || !creation.resolveMethodGenerics().isValidResult)
                fail(creation,"Fluent behavior storage requires a fresh ordinary zero-input source allocation.")
            val behavior=creation.classReference?.resolve() as? PsiClass ?: fail(creation,"The fluent behavior type is unavailable.")
            if(behavior is PsiCompiledElement || !InheritanceUtil.isInheritor(behavior,BEHAVIOR)) fail(creation,"The fluent behavior requires a source storage implementation.")
            val behaviorOwners=hierarchy(behavior);behaviors+=behavior
            var base=behavior
            while(base !is PsiCompiledElement) base=base.superClass ?: fail(base,"The behavior superclass is unavailable.")
            if(base.qualifiedName!=BEHAVIOR) fail(base,"The fluent behavior has an unreviewed compiled constructor.")
            behaviorOwners.forEach {owner ->
                owner.constructors.filter {it.isPhysical && it.parameterList.parametersCount==0}.forEach {constructor ->
                    source(constructor)
                    val statements=constructor.body?.statements.orEmpty()
                    val superCall=(statements.singleOrNull() as? PsiExpressionStatement)?.expression as? PsiMethodCallExpression
                    if(statements.isNotEmpty() && (superCall?.methodExpression?.referenceName!="super" || superCall.argumentList.expressionCount!=0))
                        fail(constructor,"The selected behavior constructor may only delegate without data or effects.")
                }
                owner.fields.forEach {stored -> source(stored);stored.initializer?.let {initial ->
                    if(stored.hasModifierProperty(PsiModifier.STATIC)) {if(!stored.hasModifierProperty(PsiModifier.FINAL) || constant(initial)==null) fail(stored,"Behavior static initialization is unreviewed.")}
                    else if(graphType(stored.type)) fail(stored,"A fluent behavior initializer captures component storage.")
                    else if(initial is PsiMethodCallExpression) model(initial) else if(initial !is PsiLiteralExpression || initial.value!=null && constant(initial)==null)
                        fail(initial,"Fluent behavior initialization requires literal data or exact Model storage.")
                }}
            }
            val behaviorSetter=selected(forwardingCall.resolveMethod() ?: fail(forwardingCall,"The behavior setter is unresolved."),behaviorOwners);methods+=behaviorSetter
            val store=operation(behaviorSetter);forward(store,behaviorSetter)
            val modelField=ownField(store,behaviorOwners,final=false)
            if(writes(modelField).isNotEmpty()) fail(modelField,"The fluent model field is reassigned or borrowed.")
            model(modelField.initializer as? PsiMethodCallExpression ?: fail(modelField,"The fluent model must be initialized directly with Model.of."))
            val target=store.resolveMethod()
            if(target !is PsiCompiledElement || target.containingClass?.qualifiedName !in setOf(MODEL,"org.apache.wicket.model.IModel") || target.name!="setObject" || parameters(target)!=(if(target.containingClass?.qualifiedName==MODEL) listOf("java.io.Serializable") else listOf("java.lang.Object")))
                fail(store,"Fluent configuration requires exact framework Model.setObject storage.")
            source(target)
            val input=call.argumentList.expressions.single() as? PsiReferenceExpression ?: fail(call,"Fluent model configuration requires a source enum constant.")
            if(input.qualifierExpression!=null && (input.qualifierExpression as? PsiReferenceExpression)?.resolve() !is PsiClass)
                fail(input,"Fluent enum inputs must be type-qualified or statically imported.")
            enum(input.resolve() as? PsiEnumConstant ?: fail(input,"The fluent input is not a source enum constant."))
            scan(field.containingClass!!).filterIsInstance<PsiReferenceExpression>().filter {it.resolve()==field && it!=assignment?.lExpression}.forEach {use ->
                val parent=use.parent
                val member=parent as? PsiReferenceExpression;val forwarding=member?.parent as? PsiMethodCallExpression
                if(forwarding?.methodExpression?.qualifierExpression==use) {
                    val ownerMethod=generateSequence(use.parent) {it.parent}.filterIsInstance<PsiMethod>().firstOrNull() ?: fail(use,"The behavior field escapes its setter.")
                    val forwarded=operation(ownerMethod);forward(forwarded,ownerMethod)
                    if(forwarded!=forwarding) fail(use,"The behavior field has additional source uses.")
                } else {
                    val args=parent as? PsiExpressionList;val binding=args?.parent as? PsiMethodCallExpression;val targetBinding=binding?.resolveMethod()
                    if(targetBinding==null || !WicketOwnedComponentAccessor.behaviorAddition(targetBinding) || args.expressionCount!=1 || !self(binding.methodExpression.qualifierExpression,field.containingClass!!))
                        fail(use,"The fluent behavior escapes its own component attachment.")
                    source(targetBinding)
                }
            }
            scan(modelField.containingClass!!).filterIsInstance<PsiReferenceExpression>().filter {it.resolve()==modelField}.forEach {use ->
                val member=use.parent as? PsiReferenceExpression;val invocation=member?.parent as? PsiMethodCallExpression
                val ownerMethod=generateSequence(use.parent) {it.parent}.filterIsInstance<PsiMethod>().firstOrNull()
                if(invocation?.methodExpression?.qualifierExpression!=use || ownerMethod==null ||
                    ownerMethod!=behaviorSetter && !deferred(ownerMethod)) fail(use,"The exact fluent model escapes its setter and deferred rendering hooks.")
            }
        }
        return Proof(allocation,owners,constructors,behaviors,methods,calls.asReversed())
    }
    private fun model(call:PsiMethodCallExpression) {
        val target=call.resolveMethod();val qualifier=call.methodExpression.qualifierExpression
        if(target !is PsiCompiledElement || target.containingClass?.qualifiedName!=MODEL || target.name!="of" || !target.hasModifierProperty(PsiModifier.STATIC) ||
            parameters(target)!=listOf("java.io.Serializable") || !call.resolveMethodGenerics().isValidResult ||
            qualifier!=null && (qualifier !is PsiReferenceExpression || qualifier.resolve() !is PsiClass)) fail(call,"Fluent model storage requires its exact compiled Model.of factory.")
        source(target)
        val argument=call.argumentList.expressions.singleOrNull() as? PsiReferenceExpression ?: fail(call,"Fluent Model defaults require a source enum constant.")
        enum(argument.resolve() as? PsiEnumConstant ?: fail(argument,"The fluent default is not a source enum constant."))
    }
    private fun enum(value:PsiEnumConstant) {
        val type=value.containingClass ?: fail(value,"The fluent enum owner is unavailable.");source(value);source(type)
        if(!enums.add(type)) return
        if(type is PsiCompiledElement || !type.isEnum || type.initializers.isNotEmpty() || type.superClass?.qualifiedName!="java.lang.Enum")
            fail(type,"Fluent enum storage requires a source storage-only enum.")
        source(type.superClass!!)
        type.implementsListTypes.forEach {inspectInterface(it.resolve() ?: fail(type,"The enum interface is unresolved."))}
        val fields=type.fields.filterNot {it is PsiEnumConstant}
        fields.forEach {field ->
            source(field)
            if(field.hasModifierProperty(PsiModifier.STATIC)) {if(!field.hasModifierProperty(PsiModifier.FINAL) || field.initializer?.let(constant)==null) fail(field,"Enum static effects are unreviewed.")}
            else if(!field.hasModifierProperty(PsiModifier.PRIVATE) || !field.hasModifierProperty(PsiModifier.FINAL) || field.initializer!=null ||
                field.type !is PsiPrimitiveType && field.type.canonicalText!="java.lang.String") fail(field,"Fluent enums may only store private-final primitive/String constructor inputs.")
        }
        type.fields.filterIsInstance<PsiEnumConstant>().forEach {item -> source(item)
            if(item.initializingClass!=null || item.argumentList?.expressions.orEmpty().any {it !is PsiLiteralExpression || it.value!=null && constant(it)==null})
                fail(item,"Fluent enum constants require literal data and no anonymous bodies.")
        }
        type.constructors.filter {it.isPhysical}.forEach {constructor -> source(constructor)
            val assigned=mutableSetOf<PsiField>()
            constructor.body?.statements.orEmpty().forEach {statement ->
                val assignment=(statement as? PsiExpressionStatement)?.expression as? PsiAssignmentExpression ?: fail(statement,"Enum constructors may only store their inputs.")
                val left=assignment.lExpression as? PsiReferenceExpression;val field=left?.resolve() as? PsiField
                val right=assignment.rExpression as? PsiReferenceExpression;val parameter=right?.resolve() as? PsiParameter
                if(assignment.operationTokenType!=JavaTokenType.EQ || field !in fields || field!!.hasModifierProperty(PsiModifier.STATIC) || !self(left.qualifierExpression,type) ||
                    !assigned.add(field) || right?.qualifierExpression!=null || parameter?.declarationScope!=constructor)
                    fail(assignment,"The enum constructor has unreviewed source effects.")
            }
            if(assigned!=fields.filterNot {it.hasModifierProperty(PsiModifier.STATIC)}.toSet()) fail(constructor,"The enum constructor does not establish its data fields.")
        }
    }
    companion object {
        private const val MODEL="org.apache.wicket.model.Model"
        private const val BEHAVIOR="org.apache.wicket.behavior.Behavior"
        private fun parameters(method:PsiMethod)=method.parameterList.parameters.map {TypeConversionUtil.erasure(it.type).canonicalText}
        private fun self(expression:PsiExpression?,owner:PsiClass)=expression==null || expression is PsiThisExpression && (expression.qualifier==null || expression.qualifier?.resolve()==owner)
        private fun deferred(method:PsiMethod)=method.findDeepestSuperMethods().any {it is PsiCompiledElement && it.containingClass?.qualifiedName?.startsWith("org.apache.wicket.")==true} &&
            method.name in setOf("onClick","onBeforeRender","onConfigure","onDetach","onComponentTag","beforeRender","afterRender","onException","detach")
        fun candidate(call:PsiMethodCallExpression):Boolean {
            val method=call.resolveMethod() ?: return false
            val qualifier=call.methodExpression.qualifierExpression
            return method !is PsiCompiledElement && !method.isConstructor && !method.hasModifierProperty(PsiModifier.STATIC) && !method.hasModifierProperty(PsiModifier.PRIVATE) && !method.hasModifierProperty(PsiModifier.FINAL) &&
                (qualifier is PsiNewExpression || qualifier is PsiMethodCallExpression) && (method.returnType as? PsiClassType)?.resolve()?.let {InheritanceUtil.isInheritor(it,"org.apache.wicket.Component")}==true
        }
    }
}
