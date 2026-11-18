package dev.wicketlens.analysis

import com.intellij.psi.*
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.TypeConversionUtil

/** Fresh owned tables and inline toolbar construction only. Rendering and generated IDs stay opaque. */
internal class WicketDataTableToolbars(
    private val tick:(PsiElement)->Unit,
    private val source:(PsiElement)->Unit,
    private val constant:(PsiExpression)->Any?,
    private val inspectInterface:(PsiClass)->Unit,
    private val inspectTable:(PsiClass)->Unit,
    private val find:(String)->PsiClass?,
    private val fail:(PsiElement,String)->Nothing,
) {
    data class Proof(val call:PsiMethodCallExpression,val allocation:PsiNewExpression,val owners:Set<PsiClass>,val eagerAllocations:List<PsiNewExpression> = emptyList(),val navigation:Boolean = false)
    private data class Construction(val owners:Set<PsiClass>,val eagerAllocations:List<PsiNewExpression> = emptyList(),val navigation:Boolean = false)
    private val navigation by lazy {WicketNavigationToolbarConstruction(tick,source,constant,inspectInterface,find,fail)}
    private data class Body(val references:List<PsiReferenceExpression>,val assignments:List<PsiAssignmentExpression>)
    private val completed=mutableSetOf<PsiMethodCallExpression>()
    private val bodies=mutableMapOf<PsiElement,Body>()

    private fun body(element:PsiElement):Body=bodies.getOrPut(element) {
        val refs=mutableListOf<PsiReferenceExpression>();val writes=mutableListOf<PsiAssignmentExpression>()
        element.accept(object:JavaRecursiveElementWalkingVisitor() {
            override fun visitElement(element:PsiElement) {
                if(element is PsiWhiteSpace || element is PsiJavaToken || element is PsiIdentifier || element is PsiComment) return
                tick(element);super.visitElement(element)
            }
            override fun visitReferenceExpression(expression:PsiReferenceExpression) {refs+=expression;super.visitReferenceExpression(expression)}
            override fun visitAssignmentExpression(expression:PsiAssignmentExpression) {writes+=expression;super.visitAssignmentExpression(expression)}
        });Body(refs,writes)
    }
    private fun body(method:PsiMethod):Body {
        source(method)
        return body(method.body ?: fail(method,"Toolbar allocation source is unavailable.") as PsiElement)
    }

    fun inspectAttachment(call:PsiMethodCallExpression):PsiMethod {
        tick(call)
        val method=call.resolveMethod()?.takeIf(::registrationMethod) ?: fail(call,"Toolbar registration must use its exact compiled DataTable method.")
        if(!call.resolveMethodGenerics().isValidResult) fail(call,"Toolbar registration arguments do not resolve.")
        source(method)
        val attach=method.containingClass?.findMethodsByName("addToolbar",false)?.singleOrNull {
            it is PsiCompiledElement && it.hasModifierProperty(PsiModifier.PRIVATE) && parameters(it)==listOf(TOOLBAR,"$TABLE.ToolbarsContainer") && it.returnType==PsiTypes.voidType()
        } ?: fail(call,"The reviewed private toolbar attachment method is unavailable.")
        source(attach)
        return method
    }

    fun registration(call:PsiMethodCallExpression):Proof {
        inspectAttachment(call)
        val constructor=PsiTreeUtil.getParentOfType(call,PsiMethod::class.java)
            ?.takeIf {it.isConstructor} ?: fail(call,"Toolbar registration requires direct owned constructor setup.")
        source(constructor)
        if(statement(call)?.parent!=constructor.body) fail(call,"Toolbar registration must be a direct constructor statement.")
        val receiver=call.methodExpression.qualifierExpression as? PsiReferenceExpression
            ?: fail(call,"Toolbar registration requires an owned table variable.")
        val variable=receiver.resolve() as? PsiVariable ?: fail(receiver,"The toolbar table variable is unresolved.")
        val owner=constructor.containingClass ?: fail(call,"The toolbar constructor owner is unavailable.")
        if(!self(receiver.qualifierExpression,owner)) fail(receiver,"Toolbar registration cannot borrow a foreign table receiver.")
        val allocation=when(variable) {
            is PsiField -> {
                source(variable)
                if(variable.containingClass!=owner || !variable.hasModifierProperty(PsiModifier.PRIVATE) || !variable.hasModifierProperty(PsiModifier.FINAL) || variable.hasModifierProperty(PsiModifier.STATIC) || variable.initializer!=null)
                    fail(receiver,"Toolbar table storage requires one private-final constructor allocation.")
                val writes=(owner.constructors.flatMap {body(it).assignments}+owner.initializers.flatMap {source(it);body(it.body as PsiElement).assignments})
                    .filter {(it.lExpression as? PsiReferenceExpression)?.resolve()==variable}
                val assignment=writes.singleOrNull() ?: fail(receiver,"Toolbar table allocation is not unique.")
                if(assignment.operationTokenType!=JavaTokenType.EQ || !self((assignment.lExpression as PsiReferenceExpression).qualifierExpression,owner) ||
                    statement(assignment)?.parent!=constructor.body || PsiTreeUtil.getParentOfType(assignment,PsiMethod::class.java)!=constructor)
                    fail(assignment,"Toolbar table allocation must be direct setup in the same constructor.")
                assignment.rExpression as? PsiNewExpression ?: fail(assignment,"Toolbar registration cannot use a borrowed or factory-returned table.")
            }
            is PsiLocalVariable -> {
                if(variable.parent !is PsiDeclarationStatement || variable.parent.parent!=constructor.body)
                    fail(variable,"Toolbar local tables require direct constructor declarations.")
                if(body(constructor).assignments.any {(it.lExpression as? PsiReferenceExpression)?.resolve()==variable})
                    fail(variable,"Toolbar table aliases and reassignment are unreviewed.")
                variable.initializer as? PsiNewExpression ?: fail(variable,"Toolbar registration cannot use a borrowed local table.")
            }
            else -> fail(receiver,"Toolbar registration cannot use a constructor input as its table.")
        }
        if(allocation.textRange.endOffset>=call.textOffset) fail(call,"Toolbar table construction must precede registration.")
        val table=allocation.anonymousClass ?: allocation.classReference?.resolve() as? PsiClass ?: fail(allocation,"The allocated toolbar table type is unresolved.")
        if(!WicketDataTableConstruction.candidate(table)) fail(allocation,"The toolbar table must have the reviewed DataTable construction boundary.")
        inspectTable(table)
        val refs=body(constructor).references
        refs.filter {it.textOffset>=allocation.textRange.endOffset && it.textOffset<call.textOffset && it.resolve()==variable}.forEach {reference ->
            if(completed.any {PsiTreeUtil.isAncestor(it,reference,false)}) return@forEach
            val parent=reference.parent as? PsiReferenceExpression
            val use=parent?.parent as? PsiMethodCallExpression
            val target=use?.resolveMethod()
            if(parent?.qualifierExpression!=reference || target !is PsiCompiledElement || target.containingClass?.qualifiedName!="org.apache.wicket.Component" ||
                target.name!="setOutputMarkupId" || parameters(target)!=listOf("boolean") || use.argumentList.expressions.singleOrNull().let {it !is PsiLiteralExpression || it.value !is Boolean} || statement(use)?.parent!=constructor.body)
                fail(reference,"The toolbar table escaped, was attached, or had unreviewed setup before registration.")
        }
        val toolbar=call.argumentList.expressions.singleOrNull() as? PsiNewExpression
            ?: fail(call,"Toolbar registration requires one fresh inline toolbar allocation.")
        val argument=toolbar.argumentList?.expressions?.firstOrNull() as? PsiReferenceExpression
        if(argument?.resolve()!=variable || !self(argument.qualifierExpression,owner)) fail(toolbar,"Toolbar construction must receive the same owned table as registration.")
        toolbar.argumentList?.expressions?.drop(1)?.forEach {input ->
            val parameter=(input as? PsiReferenceExpression)?.resolve() as? PsiParameter
            if(!(input is PsiLiteralExpression && input.value==null) &&
                (input !is PsiReferenceExpression || input.qualifierExpression!=null || parameter?.declarationScope!=constructor))
                fail(input,"Toolbar locator inputs must be null or direct constructor data parameters without evaluation.")
        }
        val construction=construction(toolbar)
        return Proof(call,toolbar,construction.owners,construction.eagerAllocations,construction.navigation)
    }

    fun complete(proof:Proof) {completed+=proof.call}
    fun reviewedReference(reference:PsiReferenceExpression)=completed.any {PsiTreeUtil.isAncestor(it,reference,false)}
    fun constructorReferences(method:PsiMethod)=body(method).references.distinct()
    fun constructorAssignments(method:PsiMethod)=body(method).assignments.distinct()

    private fun construction(allocation:PsiNewExpression):Construction {
        val actual=allocation.anonymousClass ?: allocation.classReference?.resolve() as? PsiClass ?: fail(allocation,"The toolbar allocation type is unresolved.")
        val chain=mutableListOf<PsiClass>();var base=actual
        while(base !is PsiCompiledElement) {
            source(base)
            if(base in chain || chain.size>=12) fail(base,"Toolbar construction exceeds its source-ancestry bound.")
            chain+=base;base=base.superClass ?: fail(base,"The toolbar superclass is unresolved.")
        }
        source(base)
        if(base.qualifiedName==WicketNavigationToolbarConstruction.NAVIGATION) {
            if(allocation.argumentList?.expressionCount!=1 || allocation.resolveConstructor()==null) fail(allocation,"Navigation toolbar allocation must resolve to its one-table constructor.")
            val result=navigation.inspect(allocation,chain)
            return Construction(result.owners,result.eagerAllocations,true)
        }
        val arity=when(base.qualifiedName) {TOOLBAR->1;HEADERS,AJAX_HEADERS->2
            else -> {
                val at=chain.lastOrNull()?.constructors?.firstOrNull()?.body?.statements?.firstOrNull() ?: allocation
                fail(at,"Toolbar compiled construction is outside the reviewed header/abstract-toolbar contract (${base.qualifiedName}).")
            }}
        if(allocation.argumentList?.expressionCount!=arity) fail(allocation,"The toolbar allocation does not match its reviewed constructor.")
        if(allocation.resolveConstructor()==null) fail(allocation,"The toolbar constructor does not resolve.")
        chain.forEach {owner ->
            if(owner.initializers.isNotEmpty()) fail(owner,"Toolbar initializer blocks are outside the construction-only contract.")
            owner.fields.forEach {field ->
                tick(field)
                if(!field.hasModifierProperty(PsiModifier.STATIC) || !field.hasModifierProperty(PsiModifier.FINAL) || field.initializer?.let(constant)==null)
                    fail(field,"Toolbar wrappers permit only constant static-final storage.")
            }
            owner.implementsListTypes.forEach {inspectInterface(it.resolve() ?: fail(owner,"A toolbar interface is unresolved."))}
            owner.methods.filter {it.isPhysical && !it.isConstructor}.forEach {method ->
                tick(method)
                if(!deferred(method,base.qualifiedName!=TOOLBAR)) fail(method,"Toolbar wrappers may override only the reviewed deferred rendering/sorting hooks.")
            }
            owner.constructors.filter {it.isPhysical}.forEach {constructor ->
                source(constructor)
                val statements=constructor.body?.statements?.filterNot {it is PsiEmptyStatement}.orEmpty()
                val delegation=(statements.singleOrNull() as? PsiExpressionStatement)?.expression as? PsiMethodCallExpression
                    ?: fail(constructor,"Toolbar constructors only forward their inputs in one direct super call.")
                val inputs=constructor.parameterList.parameters;val args=delegation.argumentList.expressions
                if(constructor.isVarArgs || inputs.size!=arity || args.size!=arity || delegation.methodExpression.referenceName!="super" || delegation.methodExpression.qualifierExpression!=null ||
                    !delegation.resolveMethodGenerics().isValidResult || args.indices.any {index ->val ref=args[index] as? PsiReferenceExpression;ref?.qualifierExpression!=null || ref?.resolve()!=inputs[index]})
                    fail(delegation,"Toolbar constructor delegation must forward its inputs in order without additional effects.")
            }
        }
        for(name in listOf(TOOLBAR,HEADERS,AJAX_HEADERS,"org.apache.wicket.markup.repeater.RefreshingView","org.apache.wicket.markup.repeater.RepeatingView",
            "org.apache.wicket.markup.repeater.AbstractRepeater","org.apache.wicket.extensions.markup.html.repeater.data.sort.ISortStateLocator")) {
            val dependency=find(name) ?: fail(allocation,"A toolbar construction dependency is unavailable ($name).")
            source(dependency)
            if(dependency !is PsiCompiledElement) fail(dependency,"Toolbar construction requires reviewed compiled framework dependencies.")
        }
        val expected=mapOf(TOOLBAR to "org.apache.wicket.markup.html.panel.Panel",HEADERS to TOOLBAR,AJAX_HEADERS to HEADERS)
        expected.forEach {(name,parent) ->
            val type=find(name)!!
            if(type.superClass?.qualifiedName!=parent || type.constructors.none(::constructor)) fail(type,"The toolbar constructor or superclass is unreviewed ($name).")
        }
        val id=find(TABLE)?.findMethodsByName("newToolbarId",false)?.singleOrNull {it is PsiCompiledElement && !it.hasModifierProperty(PsiModifier.STATIC) && parameters(it).isEmpty() && it.returnType?.canonicalText=="java.lang.String"}
            ?: fail(allocation,"The compiled toolbar ID-allocation boundary is unavailable.")
        source(id)
        return Construction(chain.toSet())
    }

    companion object {
        const val TABLE=WicketDataTableConstruction.TABLE
        const val TOOLBAR="org.apache.wicket.extensions.markup.html.repeater.data.table.AbstractToolbar"
        const val HEADERS="org.apache.wicket.extensions.markup.html.repeater.data.table.HeadersToolbar"
        const val AJAX_HEADERS="org.apache.wicket.extensions.ajax.markup.html.repeater.data.table.AjaxFallbackHeadersToolbar"
        private fun parameters(method:PsiMethod)=method.parameterList.parameters.map {TypeConversionUtil.erasure(it.type).canonicalText}
        fun registrationMethod(method:PsiMethod)=method is PsiCompiledElement && !method.hasModifierProperty(PsiModifier.STATIC) && method.containingClass?.qualifiedName==TABLE &&
            method.name in setOf("addTopToolbar","addBottomToolbar") && !method.isVarArgs && parameters(method)==listOf(TOOLBAR) && method.returnType==PsiTypes.voidType()
        fun constructor(method:PsiMethod)=WicketNavigationToolbarConstruction.constructor(method) || method is PsiCompiledElement && method.isConstructor && !method.isVarArgs && when(method.containingClass?.qualifiedName) {
            TOOLBAR -> parameters(method)==listOf(TABLE)
            HEADERS,AJAX_HEADERS -> parameters(method)==listOf(TABLE,"org.apache.wicket.extensions.markup.html.repeater.data.sort.ISortStateLocator")
            else -> false
        }
        private fun self(expression:PsiExpression?,owner:PsiClass)=expression==null || expression is PsiThisExpression && (expression.qualifier==null || expression.qualifier?.resolve()==owner)
        private fun statement(at:PsiElement):PsiStatement?=PsiTreeUtil.getParentOfType(at,PsiStatement::class.java)
        private fun deferred(method:PsiMethod,headers:Boolean):Boolean {
            if(method.hasModifierProperty(PsiModifier.STATIC)) return false
            if(method.name in setOf("onBeforeRender","onConfigure","onDetach") && parameters(method).isEmpty())
                return method.findDeepestSuperMethods().any {it is PsiCompiledElement && it.containingClass?.qualifiedName=="org.apache.wicket.Component" && it.name==method.name}
            val types=parameters(method)
            return headers && method.name=="newSortableHeader" && types.size==3 && types.first()=="java.lang.String" && types.last()=="org.apache.wicket.extensions.markup.html.repeater.data.sort.ISortStateLocator" &&
                method.findDeepestSuperMethods().any {it is PsiCompiledElement && it.containingClass?.qualifiedName==HEADERS}
        }
    }
}
