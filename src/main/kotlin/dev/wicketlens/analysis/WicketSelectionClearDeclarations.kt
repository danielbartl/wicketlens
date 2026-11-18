package dev.wicketlens.analysis

import com.intellij.psi.*
import com.intellij.psi.util.InheritanceUtil
import dev.wicketlens.model.*

/** One source null-guarded model-collection clear and same-receiver Ajax refresh. Never queries a model. */
internal class WicketSelectionClearDeclarations(
    private val attachments:WicketDeclaredAttachments,
    private val tick:(PsiElement)->Unit,
    private val source:(PsiElement)->Unit,
    private val descendants:(PsiElement)->List<PsiElement>,
    private val fail:(PsiElement,String)->Nothing,
) {
    fun inspect(call:PsiMethodCallExpression,declared:PsiMethod,callbackTarget:PsiParameter):ConfirmationSelectionClear? {
        // Private page data helpers and ordinary compiled operations keep their existing independent contracts.
        if(declared is PsiCompiledElement || declared.hasModifierProperty(PsiModifier.PRIVATE) || declared.hasModifierProperty(PsiModifier.STATIC)) return null
        val receiver=call.methodExpression.qualifierExpression ?: return null
        val declaredOwner=(receiver.type as? PsiClassType)?.resolve() ?: return null
        if(declaredOwner.qualifiedName!="org.apache.wicket.Component" && !InheritanceUtil.isInheritor(declaredOwner,"org.apache.wicket.Component")) return null
        val id=attachments.component(receiver)
        val allocation=((receiver as? PsiReferenceExpression)?.resolve() as? PsiLocalVariable)?.initializer as? PsiNewExpression
            ?: fail(receiver,"The selection helper receiver must retain its direct source allocation.")
        if(allocation.anonymousClass!=null) fail(allocation,"Anonymous selection-helper receiver overrides are outside this contract.")
        val owner=allocation.classReference?.resolve() as? PsiClass ?: fail(allocation,"The actual selection-helper receiver type is unavailable.")
        source(owner);source(declared)
        val helper=if(declared.hasModifierProperty(PsiModifier.PRIVATE) || declared.hasModifierProperty(PsiModifier.STATIC)) declared else owner.findMethodBySignature(declared,true) ?: declared
        source(helper)
        if(helper.containingClass!=owner || helper is PsiCompiledElement || helper.hasModifierProperty(PsiModifier.STATIC) || helper.hasModifierProperty(PsiModifier.PRIVATE) ||
            helper.parameterList.parameters.singleOrNull()?.type?.canonicalText!=TARGET || helper.returnType.let { (it as? PsiClassType)?.resolve() }.let {it==null || it!=helper.containingClass})
            fail(call,"The selection helper requires selected source dispatch, one Ajax target and its own receiver return type.")
        val argument=call.argumentList.expressions.singleOrNull() ?: fail(call,"The selection helper requires one unchanged callback target.")
        if(!ref(argument,callbackTarget)) fail(argument,"The selection helper must receive the callback Ajax target unchanged.")
        val body=helper.body?.statements ?: fail(helper,"The selection helper source body is unavailable.")
        if(body.size!=3) fail(helper,"The selection helper must only declare a guarded collection clear, same-table refresh and receiver return.")
        val guard=body[0] as? PsiIfStatement ?: fail(body[0],"The selection operation requires a direct non-null field guard.")
        val condition=guard.condition as? PsiBinaryExpression ?: fail(guard,"The selection guard is unavailable.")
        val fieldRef=when {
            nil(condition.rOperand)->condition.lOperand as? PsiReferenceExpression
            nil(condition.lOperand)->condition.rOperand as? PsiReferenceExpression
            else->null
        } ?: fail(condition,"The selection guard must compare its own field with null.")
        val column=fieldRef.resolve() as? PsiField ?: fail(fieldRef,"The guarded selection column is not a field.")
        source(column)
        if(condition.operationTokenType!=JavaTokenType.NE || guard.elseBranch!=null || !own(fieldRef.qualifierExpression) ||
            !column.hasModifierProperty(PsiModifier.PRIVATE) || !column.hasModifierProperty(PsiModifier.FINAL) || column.hasModifierProperty(PsiModifier.STATIC) ||
            column.containingClass!=helper.containingClass) fail(guard,"The selection guard requires the helper owner's private-final instance column and no else branch.")
        val branch=when(val then=guard.thenBranch) {is PsiBlockStatement->then.codeBlock.statements.singleOrNull();else->then}
        val clear=expression(branch) ?: fail(guard,"The non-null branch must only declare the model collection clear.")
        val clearMethod=resolved(clear)
        if(!jdk(clearMethod,"java.util.Collection") || clearMethod.name!="clear" || clearMethod.parameterList.parametersCount!=0 || clearMethod.returnType!=PsiTypes.voidType() || clear.argumentList.expressionCount!=0)
            fail(clear,"Only the exact JDK Collection.clear declaration is reviewed.")
        val read=clear.methodExpression.qualifierExpression as? PsiMethodCallExpression ?: fail(clear,"The collection must come directly from the model read.")
        val readMethod=resolved(read)
        if(readMethod !is PsiCompiledElement || readMethod.containingClass?.qualifiedName!="org.apache.wicket.model.IModel" || readMethod.name!="getObject" || read.argumentList.expressionCount!=0 || readMethod.parameterList.parametersCount!=0)
            fail(read,"Only the unchanged compiled IModel.getObject declaration is reviewed; model implementations stay unknown.")
        val getterCall=read.methodExpression.qualifierExpression as? PsiMethodCallExpression ?: fail(read,"The model must come directly from the guarded column getter.")
        val getter=resolved(getterCall)
        val columnType=(column.type as? PsiClassType)?.resolve() ?: fail(column,"The declared column type is unavailable.")
        source(columnType)
        val selected=columnType.findMethodBySignature(getter,true) ?: getter
        source(selected)
        if(getter.containingClass!=columnType || getter is PsiCompiledElement || selected!=getter || getter.hasModifierProperty(PsiModifier.STATIC) || getter.hasModifierProperty(PsiModifier.PRIVATE) ||
            getter.parameterList.parametersCount!=0 || getterCall.argumentList.expressionCount!=0 || !ref(getterCall.methodExpression.qualifierExpression,column))
            fail(getterCall,"The column needs its direct declared source model getter; runtime column dispatch remains unknown.")
        val returned=(getter.body?.statements?.singleOrNull() as? PsiReturnStatement)?.returnValue as? PsiReferenceExpression
            ?: fail(getter,"The source model getter must only return its own field.")
        val model=returned.resolve() as? PsiField ?: fail(returned,"The getter's model storage is unavailable.")
        source(model)
        if(!own(returned.qualifierExpression) || model.containingClass!=getter.containingClass || !model.hasModifierProperty(PsiModifier.PRIVATE) ||
            !model.hasModifierProperty(PsiModifier.FINAL) || model.hasModifierProperty(PsiModifier.STATIC) || (model.type as? PsiClassType)?.resolve().let {it !is PsiCompiledElement || it.qualifiedName!="org.apache.wicket.model.IModel"} ||
            (getter.returnType as? PsiClassType)?.resolve().let {it !is PsiCompiledElement || it.qualifiedName!="org.apache.wicket.model.IModel"})
            fail(getter,"The source model getter requires private-final own IModel storage; its value is not evaluated.")
        val refresh=expression(body[1]) ?: fail(body[1],"The helper requires one direct same-receiver Ajax refresh.")
        val refreshMethod=resolved(refresh)
        val target=helper.parameterList.parameters.single()
        if(refreshMethod !is PsiCompiledElement || refreshMethod.containingClass?.qualifiedName !in setOf(TARGET,"org.apache.wicket.core.request.handler.IPartialPageRequestHandler") || refreshMethod.name!="add" ||
            (refreshMethod.parameterList.parameters.singleOrNull()?.type as? PsiEllipsisType)?.componentType?.canonicalText!="org.apache.wicket.Component" ||
            !ref(refresh.methodExpression.qualifierExpression,target) || !self(refresh.argumentList.expressions.singleOrNull()))
            fail(refresh,"The helper must refresh its own table through the unchanged Ajax target.")
        val result=body[2] as? PsiReturnStatement ?: fail(body[2],"The helper must return its unchanged receiver.")
        if(!self(result.returnValue)) fail(result,"The helper must return its unchanged receiver.")
        if(descendants(helper.body!!).filterIsInstance<PsiReferenceExpression>().filter {it.resolve()==target}!=listOf(refresh.methodExpression.qualifierExpression))
            fail(helper,"The helper Ajax target escapes its one direct refresh.")
        return ConfirmationSelectionClear(location(call),location(helper),id,attachments.path(id),location(column),location(guard),location(getterCall),location(getter),location(model),location(read),location(clear),location(refresh),location(refresh.argumentList.expressions.single()),location(result))
    }
    private fun resolved(call:PsiMethodCallExpression):PsiMethod {
        tick(call)
        if(!call.resolveMethodGenerics().isValidResult) fail(call,"The selection operation is inaccessible or unresolved.")
        return (call.resolveMethod() ?: fail(call,"The selection operation is unresolved.")).also(source)
    }
    private fun own(e:PsiExpression?)=e==null || self(e)
    private fun self(e:PsiExpression?)=e is PsiThisExpression && e.qualifier==null
    private fun ref(e:PsiExpression?,target:PsiElement)=e is PsiReferenceExpression && e.resolve()==target && own(e.qualifierExpression)
    private fun nil(e:PsiExpression?)=e is PsiLiteralExpression && e.value==null
    private fun expression(s:PsiStatement?)=(s as? PsiExpressionStatement)?.expression as? PsiMethodCallExpression
    private fun jdk(method:PsiMethod,name:String):Boolean {
        val file=method.containingFile?.virtualFile ?: return false
        return method is PsiCompiledElement && method.containingClass?.qualifiedName==name && file.fileSystem.protocol=="jrt" && file.path.endsWith("!/java.base/"+name.replace('.','/')+".class")
    }
}
private const val TARGET="org.apache.wicket.ajax.AjaxRequestTarget"
private fun location(e:PsiElement)=SourceLocation(e.containingFile.virtualFile.path,e.textOffset)
