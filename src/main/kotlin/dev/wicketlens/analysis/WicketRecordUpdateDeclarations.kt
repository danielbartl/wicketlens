package dev.wicketlens.analysis

import com.intellij.psi.*
import com.intellij.psi.util.TypeConversionUtil
import dev.wicketlens.model.*

/** One source replacement loop and canonical record enum update. Data, predicates and operations are not evaluated. */
internal class WicketRecordUpdateDeclarations(
    private val page:PsiClass, private val tick:(PsiElement)->Unit, private val source:(PsiElement)->Unit,
    private val descendants:(PsiElement)->List<PsiElement>, private val dataType:(PsiType?)->Boolean,
    private val fail:(PsiElement,String)->Nothing,
) {
    /** Discovery hint only. Unsupported variants still need the complete proof and retain diagnostics. */
    fun candidate(method:PsiMethod):Boolean {
        tick(method)
        if(method is PsiCompiledElement || method.containingClass!=page) return false
        val found=method.body?.let {descendants(it).filterIsInstance<PsiMethodCallExpression>().any {it.methodExpression.referenceName=="replaceAll"}}==true
        if(found) source(method)
        return found
    }
    fun inspect(call:PsiMethodCallExpression,helper:PsiMethod,captured:PsiParameter):RecordUpdateCall {
        source(helper)
        val input=helper.parameterList.parameters.singleOrNull()
        if(helper is PsiCompiledElement || helper.containingClass!=page || !helper.hasModifierProperty(PsiModifier.PRIVATE) || helper.hasModifierProperty(PsiModifier.STATIC) ||
            helper.returnType!=PsiTypes.voidType() || input==null || !ownPage(call.methodExpression.qualifierExpression)) fail(call,"The record update requires a private selected-page void helper with one data input.")
        val inputType=input.type as? PsiClassType ?: fail(input,"The record input must be an exact JDK List of source records.")
        val list=inputType.resolve() ?: fail(input,"The record input list is unresolved.")
        source(list)
        if(!jdk(list,"java.util.List") || inputType.parameters.size!=1) fail(input,"The record input must be an exact JDK List of source records.")
        val rowType=inputType.parameters.single();val record=(rowType as? PsiClassType)?.resolve() ?: fail(input,"The source record type is unavailable.")
        source(record)
        if(record is PsiCompiledElement || !record.isRecord || !dataType(rowType)) fail(record,"Only reviewed canonical source record data can flow through this update.")
        val argument=call.argumentList.expressions.singleOrNull() ?: fail(call,"The record helper needs one captured data argument.")
        val origin=if(ref(argument,captured)) argument else {
            val factory=argument as? PsiMethodCallExpression ?: fail(argument,"The input must be captured directly or wrapped once by exact List.of.")
            val method=resolved(factory)
            if(!jdk(method,"java.util.List") || method.name!="of" || !method.hasModifierProperty(PsiModifier.STATIC)) fail(factory,"Only exact JDK List.of may wrap one captured record.")
            factory.argumentList.expressions.singleOrNull()?.takeIf {ref(it,captured)} ?: fail(factory,"List.of must wrap the unchanged captured record.")
        }
        val argumentType=argument.type ?: fail(argument,"The captured argument type is unavailable.")
        if(!TypeConversionUtil.isAssignable(input.type,argumentType) || !dataType(captured.type)) fail(argument,"The captured data does not have the helper's reviewed record/list type.")
        val loop=helper.body?.statements?.singleOrNull() as? PsiForeachStatement ?: fail(helper,"The helper must only declare one enhanced data loop.")
        if(!ref(loop.iteratedValue,input) || loop.iterationParameter.type!=rowType) fail(loop,"The loop must directly iterate the unchanged helper input with its record type.")
        val statement=when(val body=loop.body) {is PsiBlockStatement->body.codeBlock.statements.singleOrNull();else->body}
        val replace=expression(statement) ?: fail(loop,"The loop body must only declare one List.replaceAll call.")
        val replaceMethod=resolved(replace)
        if(!jdk(replaceMethod,"java.util.List") || replaceMethod.name!="replaceAll" || replaceMethod.parameterList.parametersCount!=1 || replaceMethod.returnType!=PsiTypes.voidType())
            fail(replace,"Only the exact JDK List.replaceAll declaration is reviewed.")
        val fieldRef=replace.methodExpression.qualifierExpression as? PsiReferenceExpression ?: fail(replace,"The replacement storage must be the page's own field.")
        val storage=fieldRef.resolve() as? PsiField ?: fail(fieldRef,"The replacement storage is unavailable.")
        source(storage)
        val allocation=storage.initializer as? PsiNewExpression
        val allocated=allocation?.classReference?.resolve() as? PsiClass
        if(allocated!=null) source(allocated)
        if(storage.containingClass!=page || !storage.hasModifierProperty(PsiModifier.PRIVATE) || !storage.hasModifierProperty(PsiModifier.FINAL) || storage.hasModifierProperty(PsiModifier.STATIC) ||
            !ownPage(fieldRef.qualifierExpression) || storage.type!=input.type || !dataType(storage.type) || allocation?.anonymousClass!=null || allocation?.qualifier!=null ||
            allocation?.argumentList?.expressionCount!=0 || allocation?.type?.let {TypeConversionUtil.isAssignable(storage.type,it)}!=true || allocated==null || !jdk(allocated,"java.util.ArrayList")) fail(storage,"The update needs private-final own ArrayList-backed storage with the same record List type.")
        val updater=replace.argumentList.expressions.singleOrNull() as? PsiLambdaExpression ?: fail(replace,"The replacement must use one inline unary updater.")
        val current=updater.parameterList.parameters.singleOrNull() ?: fail(updater,"The updater needs one unchanged record parameter.")
        val functional=updater.functionalInterfaceType as? PsiClassType ?: fail(updater,"The updater functional type is unavailable.")
        val operator=functional.resolve() ?: fail(updater,"The updater functional type is unresolved.")
        source(operator)
        val sam=LambdaUtil.getFunctionalInterfaceMethod(functional) ?: fail(updater,"The updater operation is unavailable.")
        source(sam)
        if(!jdk(operator,"java.util.function.UnaryOperator") || !jdk(sam,"java.util.function.Function") || sam.name!="apply" || sam.parameterList.parametersCount!=1 || current.type!=rowType)
            fail(updater,"The inline updater requires the unchanged JDK UnaryOperator operation on that record type.")
        val conditional=updater.body as? PsiConditionalExpression ?: fail(updater,"The updater must be one conditional expression.")
        val equality=conditional.condition as? PsiMethodCallExpression ?: fail(conditional,"The predicate must declare current.equals(loopRecord).")
        val equals=resolved(equality)
        if(equals.name!="equals" || equals.hasModifierProperty(PsiModifier.STATIC) || equals.returnType!=PsiTypes.booleanType() ||
            equals.parameterList.parameters.singleOrNull()?.type?.canonicalText!="java.lang.Object" ||
            equals.containingClass.let {it!=record && !(it!=null && jdk(it,"java.lang.Record"))} ||
            !ref(equality.methodExpression.qualifierExpression,current) || !ref(equality.argumentList.expressions.singleOrNull(),loop.iterationParameter))
            fail(equality,"The equality declaration must compare the current record to the unchanged loop record; its effects and result stay unknown.")
        if(!ref(conditional.elseExpression,current)) fail(conditional,"The false branch must retain the current record unchanged.")
        val transform=conditional.thenExpression as? PsiMethodCallExpression ?: fail(conditional,"The true branch must declare one record transform call.")
        val method=resolved(transform)
        if(method is PsiCompiledElement || method.containingClass!=record || method.hasModifierProperty(PsiModifier.STATIC) || method.parameterList.parametersCount!=0 || method.returnType!=rowType ||
            transform.argumentList.expressionCount!=0 || !ref(transform.methodExpression.qualifierExpression,current)) fail(transform,"The transform must be a direct zero-input source method on the current record.")
        val result=(method.body?.statements?.singleOrNull() as? PsiReturnStatement)?.returnValue as? PsiNewExpression
            ?: fail(method,"The record transform must only return one fresh canonical record.")
        if(result.classReference?.resolve()!=record || result.anonymousClass!=null || result.qualifier!=null) fail(result,"The transform must return the same canonical record type.")
        val constructor=result.resolveConstructor() ?: fail(result,"The canonical record constructor is unavailable.")
        source(constructor)
        if(constructor.containingClass!=record || constructor.isPhysical) fail(result,"Explicit record constructors are outside this source update contract.")
        val components=record.recordComponents;val arguments=result.argumentList?.expressions.orEmpty()
        if(arguments.size!=components.size) fail(result,"Every record component must have one source constructor argument.")
        var changed=0
        val declarations=components.zip(arguments).map { (component,value) ->
            tick(value)
            val reference=value as? PsiReferenceExpression ?: fail(value,"Record arguments must preserve their own component or declare one source enum constant.")
            val target=reference.resolve() ?: fail(value,"The record argument is unresolved.")
            if(target is PsiEnumConstant) {
                source(target);source(target.containingClass!!)
                if((component.type as? PsiClassType)?.resolve()!=target.containingClass || target.containingClass is PsiCompiledElement || !dataType(component.type)) fail(value,"The replacement constant must belong to that component's reviewed source enum.")
                changed++
                RecordUpdateArgument(component.name,"SOURCE_ENUM_CONSTANT",location(value),location(target),target.containingClass!!.qualifiedName+"."+target.name)
            } else {
                if(target !is PsiField || target!=record.findFieldByName(component.name,false) || target.containingClass!=record || target.type!=component.type || target.hasModifierProperty(PsiModifier.STATIC) ||
                    !target.hasModifierProperty(PsiModifier.PRIVATE) || !target.hasModifierProperty(PsiModifier.FINAL) || !own(reference.qualifierExpression)) fail(value,"Unchanged arguments must preserve their corresponding own record component in order.")
                RecordUpdateArgument(component.name,"PRESERVED_COMPONENT",location(value),location(component),component.name)
            }
        }
        if(changed!=1) fail(result,"The record transform must declare exactly one enum-component substitution.")
        return RecordUpdateCall(location(call),location(helper),location(storage),location(argument),location(origin),location(loop),location(loop.iterationParameter),location(replace),location(updater),
            location(equality),location(equals),location(transform),location(method),record.qualifiedName!!,location(result),declarations)
    }
    private fun resolved(call:PsiMethodCallExpression):PsiMethod {
        tick(call)
        if(!call.resolveMethodGenerics().isValidResult) fail(call,"The record operation is inaccessible or unresolved.")
        return (call.resolveMethod() ?: fail(call,"The record operation is unresolved.")).also(source)
    }
    private fun ownPage(e:PsiExpression?)=own(e) || e is PsiThisExpression && e.qualifier?.resolve()==page
    private fun own(e:PsiExpression?)=e==null || e is PsiThisExpression && e.qualifier==null
    private fun ref(e:PsiExpression?,target:PsiElement)=e is PsiReferenceExpression && e.resolve()==target && own(e.qualifierExpression)
    private fun expression(s:PsiStatement?)=(s as? PsiExpressionStatement)?.expression as? PsiMethodCallExpression
    private fun jdk(element:PsiElement,name:String):Boolean {
        val owner=if(element is PsiMethod) element.containingClass else element as? PsiClass
        val file=owner?.containingFile?.virtualFile ?: return false
        return element is PsiCompiledElement && owner?.qualifiedName==name && file.fileSystem.protocol=="jrt" && file.path.endsWith("!/java.base/"+name.replace('.','/')+".class")
    }
}
private fun location(e:PsiElement)=SourceLocation(e.containingFile.virtualFile.path,e.textOffset)
