package dev.wicketlens.analysis

import com.intellij.openapi.progress.ProgressManager
import com.intellij.psi.*
import com.intellij.psi.util.TypeConversionUtil

/** Exact DataTable construction with the framework's eager factories; no rows or callbacks are interpreted. */
internal class WicketDataTableConstruction(
    private val tick:(PsiElement)->Unit,
    private val source:(PsiElement)->Unit,
    private val constant:(PsiExpression)->Any?,
    private val inspectInterface:(PsiClass)->Unit,
    private val find:(String)->PsiClass?,
    private val fail:(PsiElement,String)->Nothing,
) {
    private val checked=mutableSetOf<PsiClass>()

    fun inspect(type:PsiClass) {
        if(!candidate(type) || type in checked) return
        var owner=type
        val seen=mutableSetOf<PsiClass>()
        while(owner !is PsiCompiledElement) {
            source(owner)
            if(!seen.add(owner) || seen.size>12) fail(owner,"DataTable construction exceeds its source-ancestry bound.")
            if(owner.initializers.isNotEmpty()) fail(owner,"Source DataTable initializer blocks require a separate construction contract.")
            owner.fields.forEach {field ->
                tick(field)
                if(!field.hasModifierProperty(PsiModifier.STATIC) || !field.hasModifierProperty(PsiModifier.FINAL) || field.initializer?.let(constant)==null)
                    fail(field,"Source DataTable wrappers permit only constant static-final storage.")
            }
            owner.implementsListTypes.forEach {inspectInterface(it.resolve() ?: fail(owner,"A DataTable wrapper interface is unresolved."))}
            owner.methods.filter {it.isPhysical && !it.isConstructor}.forEach {method ->
                tick(method)
                if(!rowCallback(method)) fail(method,"Source DataTable wrappers may override only deferred row/cell factories; eager factories and other hooks remain unreviewed.")
            }
            owner.constructors.filter {it.isPhysical}.forEach {method ->
                source(method)
                val statements=method.body?.statements?.filterNot {it is PsiEmptyStatement}.orEmpty()
                val first=(statements.singleOrNull() as? PsiExpressionStatement)?.expression as? PsiMethodCallExpression
                    ?: fail(method,"Source DataTable wrapper construction only forwards four inputs in one direct super call.")
                if(method.isVarArgs || first.methodExpression.referenceName!="super" || first.methodExpression.qualifierExpression!=null || !first.resolveMethodGenerics().isValidResult)
                    fail(method,"Source DataTable construction requires direct superclass delegation without a borrowed enclosing receiver.")
                val inputs=method.parameterList.parameters
                val arguments=first.argumentList.expressions
                if(inputs.size!=4 || arguments.size!=4 || arguments.indices.any {index ->
                    val reference=arguments[index] as? PsiReferenceExpression
                    reference?.qualifierExpression!=null || reference?.resolve()!=inputs[index]
                }) fail(first,"Source DataTable constructor arguments must forward their four inputs without evaluating additional expressions.")
            }
            owner=owner.superClass ?: fail(owner,"The DataTable superclass is unresolved.")
        }
        if(owner.qualifiedName!=TABLE || owner.constructors.none(::constructor)) fail(owner,"The reviewed compiled DataTable constructor is unavailable.")
        val dependencies=linkedMapOf(
            TABLE to "org.apache.wicket.markup.html.panel.Panel",
            "$TABLE.DefaultDataGridView" to GRID,
            "$TABLE.Caption" to "org.apache.wicket.markup.html.basic.Label",
            "$TABLE.ToolbarsContainer" to "org.apache.wicket.markup.html.WebMarkupContainer",
            GRID to ABSTRACT_GRID,
            ABSTRACT_GRID to DATA_VIEW,
            DATA_VIEW to PAGEABLE,
            PAGEABLE to "org.apache.wicket.markup.repeater.RefreshingView",
            "org.apache.wicket.extensions.markup.html.repeater.data.table.ColGroup" to "org.apache.wicket.markup.html.panel.Panel",
            "org.apache.wicket.markup.repeater.RefreshingView" to "org.apache.wicket.markup.repeater.RepeatingView",
            "org.apache.wicket.markup.repeater.RepeatingView" to "org.apache.wicket.markup.repeater.AbstractRepeater",
            "org.apache.wicket.markup.repeater.AbstractRepeater" to "org.apache.wicket.markup.html.WebMarkupContainer",
        )
        dependencies.forEach {(name,parent) ->
            val dependency=find(name)
            dependency?.let(source)
            if(dependency !is PsiCompiledElement || dependency.qualifiedName!=name || dependency.superClass?.qualifiedName!=parent)
                fail(owner,"A reviewed DataTable construction dependency is unavailable or has an unreviewed superclass ($name).")
        }
        val args=find("org.apache.wicket.util.lang.Args")
        args?.let(source)
        val guard=args?.findMethodsByName("notNull",false)?.singleOrNull {
            it is PsiCompiledElement && it.hasModifierProperty(PsiModifier.STATIC) && parameters(it)==listOf("java.lang.Object","java.lang.String") &&
                it.returnType?.let(TypeConversionUtil::erasure)?.canonicalText=="java.lang.Object"
        } ?: fail(owner,"The reviewed DataTable null guard is unavailable.")
        source(guard)
        checked+=type
    }

    companion object {
        const val TABLE="org.apache.wicket.extensions.markup.html.repeater.data.table.DataTable"
        private const val GRID="org.apache.wicket.extensions.markup.html.repeater.data.grid.DataGridView"
        private const val ABSTRACT_GRID="org.apache.wicket.extensions.markup.html.repeater.data.grid.AbstractDataGridView"
        private const val DATA_VIEW="org.apache.wicket.markup.repeater.data.DataViewBase"
        private const val PAGEABLE="org.apache.wicket.markup.repeater.AbstractPageableView"
        private fun parameters(method:PsiMethod)=method.parameterList.parameters.map {TypeConversionUtil.erasure(it.type).canonicalText}
        fun constructor(method:PsiMethod)=method is PsiCompiledElement && method.isConstructor && method.containingClass?.qualifiedName==TABLE && !method.isVarArgs &&
            parameters(method)==listOf("java.lang.String","java.util.List","org.apache.wicket.markup.repeater.data.IDataProvider","long")
        private fun rowCallback(method:PsiMethod)=!method.hasModifierProperty(PsiModifier.STATIC) && method.name in setOf("newRowItem","newCellItem") &&
            parameters(method)==listOf("java.lang.String","int","org.apache.wicket.model.IModel") &&
            method.findDeepestSuperMethods().any {it is PsiCompiledElement && it.containingClass?.qualifiedName==TABLE && it.name==method.name}
        fun candidate(type:PsiClass):Boolean {
            var current:PsiClass?=type
            repeat(13) {
                ProgressManager.checkCanceled()
                val owner=current ?: return false
                if(owner is PsiCompiledElement) return owner.qualifiedName==TABLE
                current=owner.superClass
            }
            return false
        }
    }
}
