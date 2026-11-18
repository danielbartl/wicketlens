package dev.wicketlens.analysis

import com.intellij.openapi.progress.ProgressManager
import com.intellij.psi.*
import com.intellij.psi.util.TypeConversionUtil

/** Storage-only source wrappers of the reviewed AbstractColumn/LambdaColumn constructors; no rendering callbacks run. */
internal class WicketColumnConstruction(
    private val tick:(PsiElement)->Unit,
    private val source:(PsiElement)->Unit,
    private val constant:(PsiExpression)->Any?,
    private val graph:(PsiType?)->Boolean,
    private val deferred:(PsiElement,PsiClass)->Unit,
    private val fail:(PsiElement,String)->Nothing,
    private val find:(String)->PsiClass? = {null},
    private val enumStorage:(PsiReferenceExpression)->Unit = {},
) {
    private val checked=mutableSetOf<PsiClass>()
    private val interfaces=mutableSetOf<PsiClass>()

    fun inspect(type:PsiClass) {
        if(!sourceColumn(type) || !checked.add(type)) return
        source(type)
        type.superClass?.let(::inspect)
        if(type.initializers.isNotEmpty() || type.findMethodsByName("finalize",false).any {it.isPhysical && it.parameterList.parametersCount==0})
            fail(type,"Source column initialization/finalizers are outside the storage-only construction contract.")
        type.implementsListTypes.forEach { interfaceType(it.resolve() ?: fail(type,"A column interface is unresolved."),0) }
        type.fields.forEach { field ->
            tick(field)
            field.initializer?.let { value ->
                if(field.hasModifierProperty(PsiModifier.STATIC)) {
                    if(!field.hasModifierProperty(PsiModifier.FINAL) || constant(value)==null) fail(field,"Source column static initialization is not a reviewed constant.")
                } else {
                    storage(field,type,value)
                    data(value,null,type,0)
                }
            }
        }
        if(type.constructors.isEmpty()) fail(type,"Source columns require explicit constructor delegation.")
        type.constructors.forEach { constructor ->
            source(constructor)
            if(constructor.isVarArgs) fail(constructor,"Source column varargs construction is not reviewed.")
            val statements=constructor.body?.statements?.filterNot {it is PsiEmptyStatement} ?: fail(constructor,"Source column constructor is unavailable.")
            val delegation=(statements.firstOrNull() as? PsiExpressionStatement)?.expression as? PsiMethodCallExpression
                ?: fail(constructor,"Source column construction requires direct delegation.")
            if(delegation.methodExpression.referenceName !in setOf("this","super") || !delegation.resolveMethodGenerics().isValidResult)
                fail(delegation,"Source column delegation does not resolve.")
            delegation.argumentList.expressions.forEach {data(it,constructor,type,0)}
            statements.drop(1).forEach { statement ->
                tick(statement)
                val assignment=(statement as? PsiExpressionStatement)?.expression as? PsiAssignmentExpression
                    ?: fail(statement,"Only owned data storage may follow source column delegation.")
                val reference=assignment.lExpression as? PsiReferenceExpression
                val field=reference?.resolve() as? PsiField ?: fail(assignment,"Source column construction cannot reassign an alias.")
                val value=assignment.rExpression ?: fail(assignment,"Source column storage is incomplete.")
                if(assignment.operationTokenType!=JavaTokenType.EQ || !own(reference.qualifierExpression)) fail(assignment,"Source column construction cannot mutate foreign storage.")
                storage(field,type,value)
                data(value,constructor,type,0)
            }
        }
    }

    /** Record the compiled storage boundary and its reviewed guard dependencies. */
    fun reviewConstructor(method:PsiMethod):String {
        source(method)
        if(method.containingClass?.qualifiedName==LAMBDA) {
            val parent=method.containingClass?.superClass
            if(parent !is PsiCompiledElement || parent.qualifiedName!=ABSTRACT) fail(method,"The LambdaColumn storage superclass is unreviewed.")
            source(parent)
            val args=find("org.apache.wicket.util.lang.Args")
            val guard=args?.findMethodsByName("notNull",false)?.singleOrNull {
                it is PsiCompiledElement && it.hasModifierProperty(PsiModifier.STATIC) &&
                    it.parameterList.parameters.map {p->TypeConversionUtil.erasure(p.type).canonicalText}==listOf("java.lang.Object","java.lang.String") &&
                    it.returnType?.let(TypeConversionUtil::erasure)?.canonicalText=="java.lang.Object"
            } ?: fail(method,"The compiled LambdaColumn null guard is unavailable or unreviewed.")
            source(guard)
            val function=find(SERIALIZABLE_FUNCTION) ?: fail(method,"The compiled LambdaColumn function interface is unavailable.")
            if(function !is PsiCompiledElement) fail(function,"The LambdaColumn function interface must use its reviewed compiled implementation.")
            interfaceType(function,0)
        }
        return method.containingClass!!.name!!
    }

    fun inspectInterface(type:PsiClass)=interfaceType(type,0)

    fun inspectInput(type:PsiClass?) {
        if(type?.isInterface==true && LambdaUtil.getFunctionalInterfaceMethod(JavaPsiFacade.getElementFactory(type.project).createType(type))!=null)
            interfaceType(type,0)
    }

    private fun storage(field:PsiField,owner:PsiClass,at:PsiElement) {
        if(field.containingClass!=owner || !field.hasModifierProperty(PsiModifier.PRIVATE) || field.hasModifierProperty(PsiModifier.STATIC) || graph(field.type))
            fail(at,"Source column construction requires private instance data storage, without component references.")
    }

    private fun data(expression:PsiExpression,constructor:PsiMethod?,owner:PsiClass,depth:Int) {
        tick(expression)
        if(depth>=12) fail(expression,"Source column data expressions exceed the 12-level construction bound.")
        if(graph(expression.type)) fail(expression,"Source column construction cannot acquire component state.")
        inspectInput((expression.type as? PsiClassType)?.resolve())
        fun child(value:PsiExpression)=data(value,constructor,owner,depth+1)
        when(expression) {
            is PsiLiteralExpression,is PsiClassObjectAccessExpression -> Unit
            is PsiParenthesizedExpression -> child(expression.expression ?: fail(expression,"Incomplete column data expression."))
            is PsiPrefixExpression -> {
                if(expression.operationTokenType !in setOf(JavaTokenType.PLUS,JavaTokenType.MINUS,JavaTokenType.EXCL,JavaTokenType.TILDE) || constant(expression)==null)
                    fail(expression,"Column unary storage requires a known constant without effects.")
                child(expression.operand ?: fail(expression,"Incomplete column unary constant."))
            }
            is PsiTypeCastExpression -> child(expression.operand ?: fail(expression,"Incomplete column data cast."))
            is PsiLambdaExpression,is PsiMethodReferenceExpression -> {
                val functional=LambdaUtil.getFunctionalInterfaceType(expression,true) as? PsiClassType
                    ?: fail(expression,"A stored column callback type is unavailable.")
                interfaceType(functional.resolve() ?: fail(expression,"A stored column callback interface is unresolved."),0)
                if(expression is PsiMethodReferenceExpression && expression.qualifierType==null) {
                    val qualifier=expression.qualifierExpression
                    if(qualifier is PsiThisExpression && qualifier.qualifier==null) Unit
                    else if((qualifier as? PsiReferenceExpression)?.resolve() is PsiClass) Unit
                    else qualifier?.let(::child) ?: fail(expression,"Column callback receiver evaluation is unreviewed.")
                }
                deferred(expression,owner)
            }
            is PsiReferenceExpression -> when(val target=expression.resolve()) {
                is PsiParameter -> if(!own(expression.qualifierExpression) || target.declarationScope!=constructor) fail(expression,"Column delegation references a foreign input.")
                is PsiField -> if(target.hasModifierProperty(PsiModifier.STATIC)) {
                    source(target)
                    if(reviewedEnum(target)) {
                        val qualifier=expression.qualifierExpression
                        if(qualifier!=null && (qualifier as? PsiReferenceExpression)?.resolve() !is PsiClass)
                            fail(expression,"Reviewed enum storage requires a type-qualified or statically imported constant.")
                        source(target.containingClass!!.superClass!!)
                        enumStorage(expression)
                    } else if(constant(expression)==null) fail(expression,"Column construction static data is not a known constant.")
                } else {
                    storage(target,owner,expression)
                    if(!own(expression.qualifierExpression)) fail(expression,"Column construction reads foreign storage.")
                }
                else -> fail(expression,"Column construction data reference is unresolved or unreviewed.")
            }
            is PsiMethodCallExpression -> {
                val method=expression.resolveMethod() ?: fail(expression,"Column construction call does not resolve.")
                val name=method.containingClass?.qualifiedName
                val parameters=method.parameterList.parameters.map {TypeConversionUtil.erasure(it.type).canonicalText}
                val approved=method is PsiCompiledElement && method.hasModifierProperty(PsiModifier.STATIC) &&
                    (name=="org.apache.wicket.model.Model" && method.name in setOf("of","ofList") ||
                        name=="java.util.Objects" && method.name=="requireNonNull" && parameters in listOf(listOf("java.lang.Object"),listOf("java.lang.Object","java.lang.String")))
                val qualifier=expression.methodExpression.qualifierExpression
                if(!approved || !expression.resolveMethodGenerics().isValidResult || qualifier!=null && (qualifier as? PsiReferenceExpression)?.resolve() !is PsiClass)
                    fail(expression,"Call '${name}.${method.name}' is outside storage-only column construction; callbacks/models are not invoked.")
                source(method)
                expression.argumentList.expressions.forEach(::child)
            }
            is PsiNewExpression -> {
                val method=expression.resolveConstructor() ?: fail(expression,"Column data construction is unresolved.")
                val name=method.containingClass?.qualifiedName
                if(method !is PsiCompiledElement || expression.anonymousClass!=null || expression.qualifier!=null || !expression.resolveMethodGenerics().isValidResult ||
                    !(name=="org.apache.wicket.model.Model" && expression.argumentList!!.expressionCount<=1 || name=="java.util.ArrayList" && expression.argumentList!!.expressionCount==0))
                    fail(expression,"Column construction allocates unreviewed data or a component.")
                source(method)
                expression.argumentList!!.expressions.forEach(::child)
            }
            else -> fail(expression,"Expression is outside storage-only column construction.")
        }
    }

    private fun interfaceType(type:PsiClass,depth:Int) {
        tick(type)
        if(depth>=12) fail(type,"Column interface initialization exceeds the 12-level bound.")
        if(!interfaces.add(type)) return
        source(type)
        if(!type.isInterface) fail(type,"Column callback initialization requires an interface.")
        if(type is PsiCompiledElement) {
            val name=type.qualifiedName
            if(name !in KNOWN_INTERFACES && name?.startsWith("java.util.function.")!=true)
                fail(type,"Compiled column interface initialization is unreviewed (${name}).")
            return
        }
        if(type.initializers.isNotEmpty() || type.fields.any {it.initializer?.let(constant)==null})
            fail(type,"Column callback/interface initialization has unreviewed static effects.")
        type.extendsListTypes.forEach {interfaceType(it.resolve() ?: fail(type,"Column interface ancestry is unresolved."),depth+1)}
    }

    private fun own(expression:PsiExpression?)=expression==null || expression is PsiThisExpression && expression.qualifier==null

    companion object {
        const val ABSTRACT="org.apache.wicket.extensions.markup.html.repeater.data.table.AbstractColumn"
        const val LAMBDA="org.apache.wicket.extensions.markup.html.repeater.data.table.LambdaColumn"
        private const val SERIALIZABLE_FUNCTION="org.danekja.java.util.function.serializable.SerializableFunction"
        private const val POPULATOR="org.apache.wicket.extensions.markup.html.repeater.data.grid.ICellPopulator"
        private val KNOWN_INTERFACES=setOf("org.apache.wicket.markup.repeater.data.IDataProvider","org.apache.wicket.extensions.markup.html.repeater.data.table.ISortableDataProvider",
            "org.apache.wicket.extensions.markup.html.repeater.data.sort.ISortStateLocator","org.apache.wicket.extensions.markup.html.repeater.data.sort.ISortState",SERIALIZABLE_FUNCTION,"java.io.Serializable","java.lang.Runnable","org.apache.wicket.util.io.IClusterable","org.apache.wicket.model.IDetachable","org.apache.wicket.model.IModel",POPULATOR,
            "org.apache.wicket.extensions.markup.html.repeater.data.table.IColumn","org.apache.wicket.extensions.markup.html.repeater.data.table.IStyledColumn")
        /** This enum has only the four reviewed constants and implicit Enum storage. */
        fun reviewedEnum(field:PsiField):Boolean {
            ProgressManager.checkCanceled()
            if(field !is PsiEnumConstant || field !is PsiCompiledElement) return false
            val type=field.containingClass ?: return false
            val file=type.containingFile?.virtualFile ?: return false
            if(type !is PsiCompiledElement || !type.isEnum || type.qualifiedName!="java.time.format.FormatStyle" ||
                file.fileSystem.protocol!="jrt" || !file.path.endsWith("!/java.base/java/time/format/FormatStyle.class")) return false
            val parent=type.superClass ?: return false
            val parentFile=parent.containingFile?.virtualFile ?: return false
            return parent is PsiCompiledElement && parent.qualifiedName=="java.lang.Enum" && parentFile.fileSystem.protocol=="jrt" &&
                parentFile.path.endsWith("!/java.base/java/lang/Enum.class") &&
                parentFile.path.substringBefore("!/")==file.path.substringBefore("!/") &&
                type.fields.filterIsInstance<PsiEnumConstant>().map {it.name}.toSet()==setOf("FULL","LONG","MEDIUM","SHORT")
        }
        fun constructor(method:PsiMethod):Boolean {
            if(method !is PsiCompiledElement || !method.isConstructor) return false
            val parameters=method.parameterList.parameters.map {TypeConversionUtil.erasure(it.type).canonicalText}
            return when(method.containingClass?.qualifiedName) {
                ABSTRACT -> parameters in listOf(listOf("org.apache.wicket.model.IModel"),listOf("org.apache.wicket.model.IModel","java.lang.Object"))
                LAMBDA -> parameters in listOf(listOf("org.apache.wicket.model.IModel",SERIALIZABLE_FUNCTION),listOf("org.apache.wicket.model.IModel","java.lang.Object",SERIALIZABLE_FUNCTION))
                else -> false
            }
        }
        fun sourceColumn(type:PsiClass):Boolean {
            if(type is PsiCompiledElement) return false
            var current:PsiClass?=type
            repeat(13) {
                ProgressManager.checkCanceled()
                val owner=current ?: return false
                if(owner is PsiCompiledElement) return owner.qualifiedName in setOf(ABSTRACT,LAMBDA)
                current=owner.superClass
            }
            return false
        }
        fun deferredCallback(method:PsiMethod):Boolean {
            if(method.isConstructor || method.hasModifierProperty(PsiModifier.STATIC) || method.name !in setOf("populateItem","detach")) return false
            val parameters=method.parameterList.parameters.map {TypeConversionUtil.erasure(it.type).canonicalText}
            return method.findDeepestSuperMethods().any { parent -> parent is PsiCompiledElement && when {
                method.name=="populateItem" && parameters==listOf("org.apache.wicket.markup.repeater.Item","java.lang.String","org.apache.wicket.model.IModel") -> parent.containingClass?.qualifiedName==POPULATOR && parent.name=="populateItem"
                method.name=="detach" && parameters.isEmpty() -> parent.containingClass?.qualifiedName=="org.apache.wicket.model.IDetachable" && parent.name=="detach"
                else -> false
            } }
        }
    }
}
