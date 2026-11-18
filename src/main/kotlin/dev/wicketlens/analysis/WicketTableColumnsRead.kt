package dev.wicketlens.analysis

import com.intellij.psi.*
import com.intellij.psi.util.TypeConversionUtil

/** A borrowed columns reference is size-only, even when declared as Object or aliased in locals. */
internal class WicketTableColumnsRead(
    private val tick:(PsiElement)->Unit,
    private val source:(PsiElement)->Unit,
    private val assignments:(PsiMethod)->List<PsiAssignmentExpression>,
    private val fail:(PsiElement,String)->Nothing,
) {
    fun inspect(call:PsiMethodCallExpression,proof:WicketOwnedTableForwarding.Proof,ownerAllocation:PsiNewExpression?) {
        tick(call)
        val getter=call.resolveMethod() ?: fail(call,"The columns query is unresolved.")
        val receiver=call.methodExpression.qualifierExpression as? PsiReferenceExpression
        val qualifier=receiver?.qualifierExpression
        if(!query(getter) || !call.resolveMethodGenerics().isValidResult || call.argumentList.expressionCount!=0 || receiver?.resolve()!=proof.tableField ||
            qualifier!=null && (qualifier !is PsiThisExpression || qualifier.qualifier!=null && qualifier.qualifier?.resolve()!=proof.tableField.containingClass))
            fail(call,"The columns query requires its directly proved owned table field.")
        source(getter)
        var input=proof.tableAllocation.argumentList?.expressions?.getOrNull(1) ?: fail(call,"The table columns input is unavailable.")
        if(input is PsiReferenceExpression) {
            val parameter=input.resolve() as? PsiParameter ?: fail(input,"Column list aliases and fields do not establish immutable-list identity.")
            source(parameter)
            val constructor=proof.ownerConstructor
            if(input.qualifierExpression!=null || parameter.declarationScope!=constructor || ownerAllocation?.resolveConstructor()!=constructor)
                fail(input,"The columns input must forward its selected owner's constructor parameter.")
            assignments(constructor).forEach {assignment -> tick(assignment)
                if((assignment.lExpression as? PsiReferenceExpression)?.resolve()==parameter) fail(assignment,"The forwarded columns parameter is reassigned.")
            }
            val index=constructor.parameterList.parameters.indexOf(parameter)
            input=ownerAllocation.argumentList?.expressions?.getOrNull(index) ?: fail(call,"The owner columns argument is unavailable.")
        }
        val factory=input as? PsiMethodCallExpression ?: fail(input,"Column size reads require an exact JDK List.of input.")
        val method=factory.resolveMethod() ?: fail(factory,"The columns factory is unresolved.")
        source(method)
        val scope=factory.methodExpression.qualifierExpression
        if(!jdk(method,"java.util.List") || !method.hasModifierProperty(PsiModifier.STATIC) || method.name!="of" ||
            TypeConversionUtil.erasure(method.returnType ?: PsiTypes.voidType()).canonicalText!="java.util.List" || !factory.resolveMethodGenerics().isValidResult ||
            scope!=null && (scope !is PsiReferenceExpression || scope.resolve() !is PsiClass))
            fail(factory,"The columns input does not establish exact JDK immutable-list storage.")
    }
    fun size(method:PsiMethod)=jdk(method,"java.util.List") && method.name=="size" && !method.hasModifierProperty(PsiModifier.STATIC) &&
        method.parameterList.parametersCount==0 && method.returnType==PsiTypes.intType()
    companion object {
        fun query(method:PsiMethod)=method is PsiCompiledElement && method.containingClass?.qualifiedName==WicketDataTableToolbars.TABLE &&
            method.name=="getColumns" && method.hasModifierProperty(PsiModifier.FINAL) && !method.hasModifierProperty(PsiModifier.STATIC) &&
            method.parameterList.parametersCount==0 && method.returnType?.let(TypeConversionUtil::erasure)?.canonicalText=="java.util.List"
        private fun jdk(method:PsiMethod,name:String):Boolean {
            val owner=method.containingClass ?: return false
            val file=owner.containingFile?.virtualFile ?: return false
            return method is PsiCompiledElement && owner is PsiCompiledElement && owner.qualifiedName==name && file.fileSystem.protocol=="jrt" &&
                file.path.endsWith("!/java.base/"+name.replace('.','/')+".class")
        }
    }
}
