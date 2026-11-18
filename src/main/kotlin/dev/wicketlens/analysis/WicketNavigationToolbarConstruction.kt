package dev.wicketlens.analysis

import com.intellij.psi.*
import com.intellij.psi.util.TypeConversionUtil

/** Construction only: NavigationToolbar's factories are eager; pager link factories are later initialization. */
internal class WicketNavigationToolbarConstruction(
    private val tick:(PsiElement)->Unit,
    private val source:(PsiElement)->Unit,
    private val constant:(PsiExpression)->Any?,
    private val inspectInterface:(PsiClass)->Unit,
    private val find:(String)->PsiClass?,
    private val fail:(PsiElement,String)->Nothing,
) {
    data class Result(val owners:Set<PsiClass>,val eagerAllocations:List<PsiNewExpression>)

    fun inspect(allocation:PsiNewExpression,chain:List<PsiClass>):Result {
        val factories=mutableListOf<PsiMethod>()
        chain.forEach {owner ->
            sourceState(owner)
            owner.methods.filter {it.isPhysical && !it.isConstructor}.forEach {method ->
                tick(method)
                when {
                    factory(method) -> factories+=method
                    rendering(method) -> Unit
                    else -> fail(method,"Navigation toolbar overrides require an eager-factory or deferred-hook contract.")
                }
            }
            owner.constructors.filter {it.isPhysical}.forEach {constructor ->
                val tail=forwarding(constructor,1)
                if(tail.size>1) fail(constructor,"Navigation toolbar construction permits only its forwarded table output flag.")
                tail.forEach {statement ->
                    val call=(statement as? PsiExpressionStatement)?.expression as? PsiMethodCallExpression
                        ?: fail(statement,"Navigation toolbar construction cannot evaluate extra source effects.")
                    val receiver=call.methodExpression.qualifierExpression as? PsiReferenceExpression
                    val target=call.resolveMethod()
                    if(receiver?.qualifierExpression!=null || receiver?.resolve()!=constructor.parameterList.parameters.single() ||
                        target !is PsiCompiledElement || target.containingClass?.qualifiedName!="org.apache.wicket.Component" || target.name!="setOutputMarkupId" ||
                        parameters(target)!=listOf("boolean") || call.argumentList.expressions.singleOrNull().let {it !is PsiLiteralExpression || it.value !is Boolean})
                        fail(call,"Navigation toolbar construction may only set a literal output flag on its forwarded table.")
                    source(target)
                }
            }
        }
        val eager=mutableListOf<PsiNewExpression>();val owners=chain.toMutableSet()
        // First source declaration is the selected concrete dispatch. Base overrides are not invoked implicitly.
        factories.firstOrNull()?.let {method ->
            source(method)
            val statements=method.body?.statements?.filterNot {it is PsiEmptyStatement}.orEmpty()
            val creation=(statements.singleOrNull() as? PsiReturnStatement)?.returnValue as? PsiNewExpression
                ?: fail(method,"The eager paging factory must return one fresh navigator directly.")
            if(method.hasModifierProperty(PsiModifier.SYNCHRONIZED)) fail(method,"Synchronized paging factories are unreviewed.")
            val inputs=method.parameterList.parameters;val args=creation.argumentList?.expressions.orEmpty()
            if(creation.qualifier!=null || args.size!=2 || args.indices.any {index ->
                val reference=args[index] as? PsiReferenceExpression
                reference?.qualifierExpression!=null || reference?.resolve()!=inputs[index]
            }) fail(creation,"The eager paging factory must forward its navigator ID and table without evaluation.")
            owners+=pager(creation)
            eager+=creation
        }
        dependencies(allocation)
        return Result(owners,eager)
    }

    private fun sourceState(owner:PsiClass) {
        source(owner)
        if(owner.initializers.isNotEmpty()) fail(owner,"Navigation construction cannot run source initializer blocks.")
        owner.fields.forEach {field ->
            tick(field)
            if(!field.hasModifierProperty(PsiModifier.STATIC) || !field.hasModifierProperty(PsiModifier.FINAL) || field.initializer?.let(constant)==null)
                fail(field,"Navigation construction permits only constant static-final source storage.")
        }
        owner.implementsListTypes.forEach {inspectInterface(it.resolve() ?: fail(owner,"A navigation interface is unresolved."))}
    }

    private fun forwarding(method:PsiMethod,arity:Int):List<PsiStatement> {
        source(method)
        val statements=method.body?.statements?.filterNot {it is PsiEmptyStatement}.orEmpty()
        val call=(statements.firstOrNull() as? PsiExpressionStatement)?.expression as? PsiMethodCallExpression
            ?: fail(method,"Navigation construction requires direct superclass forwarding.")
        val inputs=method.parameterList.parameters;val args=call.argumentList.expressions
        if(method.isVarArgs || inputs.size!=arity || args.size!=arity || call.methodExpression.referenceName!="super" || call.methodExpression.qualifierExpression!=null ||
            !call.resolveMethodGenerics().isValidResult || args.indices.any {index ->
                val reference=args[index] as? PsiReferenceExpression
                reference?.qualifierExpression!=null || reference?.resolve()!=inputs[index]
            }) fail(call,"Navigation constructors must forward their inputs in order without evaluation.")
        return statements.drop(1)
    }

    private fun pager(creation:PsiNewExpression):Set<PsiClass> {
        if(creation.resolveConstructor()==null) fail(creation,"The paging navigator constructor does not resolve.")
        val owners=mutableSetOf<PsiClass>()
        var current=creation.anonymousClass ?: creation.classReference?.resolve() as? PsiClass ?: fail(creation,"The paging navigator type is unresolved.")
        while(current !is PsiCompiledElement) {
            source(current)
            if(current in owners || owners.size>=12) fail(current,"Paging navigator construction exceeds its source-ancestry bound.")
            owners+=current
            sourceState(current)
            current.methods.filter {it.isPhysical && !it.isConstructor}.forEach {method ->
                tick(method)
                if(!pagerDeferred(method)) fail(method,"Source paging navigators may override only the reviewed later initialization/Ajax/render hooks.")
            }
            current.constructors.filter {it.isPhysical}.forEach {method ->
                val arity=method.parameterList.parametersCount
                if(arity !in 2..3 || parameters(method)!=listOf("java.lang.String","org.apache.wicket.markup.html.navigation.paging.IPageable","org.apache.wicket.markup.html.navigation.paging.IPagingLabelProvider").take(arity))
                    fail(method,"The source paging navigator constructor signature is unreviewed.")
                if(forwarding(method,arity).isNotEmpty()) fail(method,"Paging navigator construction only forwards ID, pageable and optional label provider in one super call.")
            }
            current=current.superClass ?: fail(current,"The paging navigator superclass is unresolved.")
        }
        source(current)
        if(current.qualifiedName !in setOf(PAGER,AJAX_PAGER)) fail(current,"The compiled paging navigator constructor is unreviewed.")
        return owners
    }

    private fun dependencies(at:PsiElement) {
        val parents=linkedMapOf(
            NAVIGATION to WicketDataTableToolbars.TOOLBAR,
            WicketDataTableToolbars.TOOLBAR to "org.apache.wicket.markup.html.panel.Panel",
            PAGER to "org.apache.wicket.markup.html.panel.Panel",
            AJAX_PAGER to PAGER,
            LABEL to "org.apache.wicket.markup.html.basic.Label",
            "$LABEL.LabelModelObject" to "java.lang.Object",
            "org.apache.wicket.AttributeModifier" to "org.apache.wicket.behavior.Behavior",
            "org.apache.wicket.model.Model" to "java.lang.Object",
            "org.apache.wicket.model.StringResourceModel" to "org.apache.wicket.model.LoadableDetachableModel",
        )
        parents.forEach {(name,parent) ->
            val type=find(name) ?: fail(at,"A navigation construction dependency is unavailable ($name).")
            source(type)
            if(type !is PsiCompiledElement || type.superClass?.qualifiedName!=parent) fail(type,"Navigation construction requires the reviewed compiled dependency ($name).")
            if(name in setOf(NAVIGATION,PAGER,AJAX_PAGER) && type.constructors.none(::constructor)) fail(type,"The reviewed navigation constructor is unavailable ($name).")
        }
        val id=find(WicketDataTableConstruction.TABLE)?.findMethodsByName("newToolbarId",false)?.singleOrNull {
            it is PsiCompiledElement && !it.hasModifierProperty(PsiModifier.STATIC) && parameters(it).isEmpty() && it.returnType?.canonicalText=="java.lang.String"
        } ?: fail(at,"The navigation toolbar ID-allocation boundary is unavailable.")
        source(id)
        for(name in listOf("org.apache.wicket.markup.html.navigation.paging.IPageable","org.apache.wicket.markup.html.navigation.paging.IPageableItems","org.apache.wicket.markup.html.navigation.paging.IPagingLabelProvider",
            "org.apache.wicket.model.IModel","org.apache.wicket.model.IComponentAssignedModel","org.apache.wicket.model.IWrapModel")) {
            val type=find(name) ?: fail(at,"A navigation model/protocol dependency is unavailable ($name).")
            source(type)
            if(type !is PsiCompiledElement) fail(type,"Navigation protocol construction requires its reviewed compiled implementation.")
        }
    }

    companion object {
        const val NAVIGATION="org.apache.wicket.extensions.markup.html.repeater.data.table.NavigationToolbar"
        const val PAGER="org.apache.wicket.markup.html.navigation.paging.PagingNavigator"
        const val AJAX_PAGER="org.apache.wicket.ajax.markup.html.navigation.paging.AjaxPagingNavigator"
        private const val LABEL="org.apache.wicket.extensions.markup.html.repeater.data.table.NavigatorLabel"
        private fun parameters(method:PsiMethod)=method.parameterList.parameters.map {TypeConversionUtil.erasure(it.type).canonicalText}
        fun constructor(method:PsiMethod)=method is PsiCompiledElement && method.isConstructor && !method.isVarArgs && when(method.containingClass?.qualifiedName) {
            NAVIGATION -> parameters(method)==listOf(WicketDataTableToolbars.TABLE)
            PAGER,AJAX_PAGER -> parameters(method) in listOf(
                listOf("java.lang.String","org.apache.wicket.markup.html.navigation.paging.IPageable"),
                listOf("java.lang.String","org.apache.wicket.markup.html.navigation.paging.IPageable","org.apache.wicket.markup.html.navigation.paging.IPagingLabelProvider"))
            else -> false
        }
        private fun factory(method:PsiMethod)=method.name=="newPagingNavigator" && !method.hasModifierProperty(PsiModifier.STATIC) &&
            parameters(method)==listOf("java.lang.String",WicketDataTableToolbars.TABLE) && method.findDeepestSuperMethods().any {it is PsiCompiledElement && it.containingClass?.qualifiedName==NAVIGATION}
        private fun rendering(method:PsiMethod)=method.name in setOf("onBeforeRender","onConfigure","onDetach") && !method.hasModifierProperty(PsiModifier.STATIC) && parameters(method).isEmpty() &&
            method.findDeepestSuperMethods().any {it is PsiCompiledElement && it.containingClass?.qualifiedName=="org.apache.wicket.Component"}
        private fun pagerDeferred(method:PsiMethod):Boolean {
            if(rendering(method)) return true
            if(method.hasModifierProperty(PsiModifier.STATIC)) return false
            return method.findDeepestSuperMethods().any {parent ->
                parent is PsiCompiledElement && (parent.containingClass?.qualifiedName==PAGER && parent.name in setOf("newNavigation","newPagingNavigationLink","newPagingNavigationIncrementLink") ||
                    parent.containingClass?.qualifiedName==AJAX_PAGER && parent.name=="onAjaxEvent")
            }
        }
    }
}
