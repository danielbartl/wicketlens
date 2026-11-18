package dev.wicketlens.analysis

import com.intellij.openapi.progress.ProgressManager
import com.intellij.psi.*
import com.intellij.psi.util.TypeConversionUtil

/** Pure construction only. No provider callback or page query is interpreted. */
internal class WicketProviderConstruction(
    private val tick:(PsiElement)->Unit,
    private val source:(PsiElement)->Unit,
    private val constant:(PsiExpression)->Any?,
    private val inspectInterface:(PsiClass)->Unit,
    private val find:(String)->PsiClass?,
    private val fail:(PsiElement,String)->Nothing,
) {
    fun inspect(creation:PsiNewExpression):PsiClass? {
        val type=creation.anonymousClass ?: creation.classReference?.resolve() as? PsiClass ?: return null
        if(!sourceProvider(type)) return null
        tick(creation)
        if(creation.qualifier!=null || creation.argumentList?.expressionCount!=0)
            fail(creation,"Provider construction requires an unqualified zero-argument source allocation.")
        var owner:PsiClass=type
        val seen=mutableSetOf<PsiClass>()
        while(owner !is PsiCompiledElement) {
            source(owner)
            if(!seen.add(owner) || seen.size>12) fail(owner,"Provider construction exceeds its source-ancestry bound.")
            if(owner.initializers.isNotEmpty()) fail(owner,"Provider initializer blocks are outside the construction-only contract.")
            owner.fields.forEach {field ->
                tick(field)
                if(!field.hasModifierProperty(PsiModifier.STATIC) || !field.hasModifierProperty(PsiModifier.FINAL) || field.initializer?.let(constant)==null)
                    fail(field,"Provider construction permits no instance state or unreviewed static initialization.")
            }
            owner.implementsListTypes.forEach {inspectInterface(it.resolve() ?: fail(owner,"A provider interface is unresolved."))}
            if(owner.methods.any {it.isPhysical && !it.isConstructor && it.name in setOf("finalize","toString","hashCode","equals","compareTo")})
                fail(owner,"Provider object/finalizer hooks need a separate eager-effect contract.")
            owner.constructors.filter {it.isPhysical}.forEach {constructor ->
                source(constructor)
                if(constructor.parameterList.parametersCount!=0 || constructor.isVarArgs) fail(constructor,"Provider constructor inputs are outside this zero-state contract.")
                val statements=constructor.body?.statements?.filterNot {it is PsiEmptyStatement} ?: fail(constructor,"Provider constructor source is unavailable.")
                if(statements.isNotEmpty()) {
                    val delegation=(statements.singleOrNull() as? PsiExpressionStatement)?.expression as? PsiMethodCallExpression
                        ?: fail(constructor,"Only empty provider construction or direct superclass delegation is reviewed.")
                    val target=delegation.resolveMethod()
                    if(delegation.methodExpression.referenceName!="super" || delegation.methodExpression.qualifierExpression!=null || delegation.argumentList.expressionCount!=0 ||
                        !delegation.resolveMethodGenerics().isValidResult || target?.containingClass!=owner.superClass)
                        fail(delegation,"Provider construction requires direct zero-argument superclass delegation.")
                }
            }
            owner=owner.superClass ?: fail(owner,"Provider construction has no resolved superclass.")
        }
        if(owner.qualifiedName!=BASE || owner.constructors.none {it is PsiCompiledElement && it.parameterList.parametersCount==0})
            fail(owner,"The compiled provider construction boundary is unavailable.")
        source(owner)
        val state=find(STATE)
        if(state !is PsiCompiledElement || state.qualifiedName!=STATE || state.superClass?.qualifiedName!="java.lang.Object" ||
            state.constructors.none {it is PsiCompiledElement && it.parameterList.parametersCount==0})
            fail(owner,"The compiled provider sort-state allocation is unavailable or unreviewed.")
        source(state)
        for(name in listOf(PROVIDER,"org.apache.wicket.extensions.markup.html.repeater.data.table.ISortableDataProvider",
            "org.apache.wicket.extensions.markup.html.repeater.data.sort.ISortStateLocator","org.apache.wicket.extensions.markup.html.repeater.data.sort.ISortState",
            "org.apache.wicket.model.IDetachable","org.apache.wicket.util.io.IClusterable")) {
            val protocol=find(name) ?: fail(owner,"A reviewed provider protocol dependency is unavailable ($name).")
            if(protocol !is PsiCompiledElement) fail(protocol,"Provider protocol initialization must use its reviewed compiled implementation.")
            inspectInterface(protocol)
        }
        return type
    }

    companion object {
        const val BASE="org.apache.wicket.extensions.markup.html.repeater.util.SortableDataProvider"
        private const val STATE="org.apache.wicket.extensions.markup.html.repeater.util.SingleSortState"
        private const val PROVIDER="org.apache.wicket.markup.repeater.data.IDataProvider"
        fun sourceProvider(type:PsiClass):Boolean {
            if(type is PsiCompiledElement) return false
            var current:PsiClass?=type
            repeat(13) {
                ProgressManager.checkCanceled()
                val owner=current ?: return false
                if(owner is PsiCompiledElement) return owner.qualifiedName==BASE
                current=owner.superClass
            }
            return false
        }
        fun callback(method:PsiMethod):Boolean {
            if(method.isConstructor || method.hasModifierProperty(PsiModifier.STATIC)) return false
            val parameters=method.parameterList.parameters.map {TypeConversionUtil.erasure(it.type).canonicalText}
            if(!(method.name=="iterator" && parameters==listOf("long","long") || method.name=="size" && parameters.isEmpty() ||
                method.name=="model" && parameters.size==1 || method.name=="detach" && parameters.isEmpty())) return false
            val parents=if(method is PsiCompiledElement) listOf(method)+method.findDeepestSuperMethods().toList() else method.findDeepestSuperMethods().toList()
            return parents.any {it is PsiCompiledElement && it.name==method.name && (it.containingClass?.qualifiedName==PROVIDER ||
                method.name=="detach" && it.containingClass?.qualifiedName=="org.apache.wicket.model.IDetachable")}
        }
    }
}
