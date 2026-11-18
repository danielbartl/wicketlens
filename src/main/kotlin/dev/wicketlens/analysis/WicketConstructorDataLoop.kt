package dev.wicketlens.analysis

import com.intellij.psi.*
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.TypeConversionUtil
import java.math.BigInteger

/** Audits one finite counted loop; never executes its body or produces model values. */
internal class WicketConstructorDataLoop(
    private val constructor: PsiMethod,
    private val tick: (PsiElement) -> Unit,
    private val source: (PsiElement) -> Unit,
    private val constant: (PsiExpression) -> Any?,
    private val unsafeValue: (PsiVariable) -> Boolean,
    private val initialize: (PsiClass) -> Unit,
    private val fail: (PsiElement, String) -> Nothing,
) {
    fun audit(loop: PsiForStatement) {
        tick(loop)
        val counter = (loop.initialization as? PsiDeclarationStatement)?.declaredElements?.singleOrNull() as? PsiLocalVariable
            ?: fail(loop, "the counter must be declared by the loop")
        if (counter.type.canonicalText !in setOf("int", "long")) fail(counter, "only int/long counters are reviewed")
        if (counter.initializer?.let { TypeConversionUtil.areTypesAssignmentCompatible(counter.type, it) } != true)
            fail(counter, "the counter initializer is not assignment-compatible")
        fun integer(expression: PsiExpression?): BigInteger {
            val value = expression?.let(constant)
            if (value !is Byte && value !is Short && value !is Int && value !is Long)
                fail(expression ?: loop, "counter ranges require integral constants")
            return BigInteger.valueOf((value as Number).toLong())
        }
        val start = integer(counter.initializer)
        val condition = loop.condition as? PsiBinaryExpression ?: fail(loop, "a direct counter comparison is required")
        if ((condition.lOperand as? PsiReferenceExpression)?.resolve() != counter) fail(condition, "the comparison must read the loop counter directly")
        val bound = integer(condition.rOperand)
        val update = (loop.update as? PsiExpressionStatement)?.expression ?: fail(loop, "one counter increment/decrement is required")
        val (operand, token) = when (update) {
            is PsiPostfixExpression -> update.operand to update.operationTokenType
            is PsiPrefixExpression -> update.operand to update.operationTokenType
            else -> fail(update, "only ++/-- counter updates are reviewed")
        }
        if ((operand as? PsiReferenceExpression)?.resolve() != counter) fail(update, "the update must change only the loop counter")
        val ascending = token == JavaTokenType.PLUSPLUS
        if (!ascending && token != JavaTokenType.MINUSMINUS) fail(update, "only ++/-- counter updates are reviewed")
        val inclusive = when (condition.operationTokenType) {
            JavaTokenType.LT -> { if (!ascending) fail(condition, "counter direction does not prove termination"); false }
            JavaTokenType.LE -> { if (!ascending) fail(condition, "counter direction does not prove termination"); true }
            JavaTokenType.GT -> { if (ascending) fail(condition, "counter direction does not prove termination"); false }
            JavaTokenType.GE -> { if (ascending) fail(condition, "counter direction does not prove termination"); true }
            else -> fail(condition, "only <, <=, > and >= comparisons are reviewed")
        }
        val one = BigInteger.ONE
        val last = if (inclusive) bound else if (ascending) bound - one else bound + one
        val enters = if (ascending) start <= last else start >= last
        val minimum = BigInteger.valueOf(if (counter.type.canonicalText == "int") Int.MIN_VALUE.toLong() else Long.MIN_VALUE)
        val maximum = BigInteger.valueOf(if (counter.type.canonicalText == "int") Int.MAX_VALUE.toLong() else Long.MAX_VALUE)
        val after = if (ascending) last + one else last - one
        if (start < minimum || start > maximum || enters && (after < minimum || after > maximum))
            fail(condition, "the counter can overflow before termination")
        val body = loop.body ?: fail(loop, "the loop body is unavailable")
        fun local(variable: PsiVariable) = variable is PsiLocalVariable && variable != counter && PsiTreeUtil.isAncestor(body, variable, false)
        fun write(expression: PsiExpression) {
            val target = (expression as? PsiReferenceExpression)?.resolve() as? PsiVariable
            if (target == null || !local(target)) fail(expression, "writes outside loop-local data are not reviewed")
        }
        fun freshList(reference: PsiExpression?): Boolean {
            val ref = reference as? PsiReferenceExpression ?: return false
            val variable = ref.resolve() as? PsiVariable ?: return false
            if (ref.qualifierExpression != null && ref.qualifierExpression !is PsiThisExpression) return false
            if (variable is PsiField && (!variable.hasModifierProperty(PsiModifier.PRIVATE) ||
                    !variable.hasModifierProperty(PsiModifier.FINAL) || variable.hasModifierProperty(PsiModifier.STATIC) ||
                    variable.containingClass != constructor.containingClass)) return false
            if (variable !is PsiField && variable !is PsiLocalVariable) return false
            val creation = variable.initializer as? PsiNewExpression ?: return false
            if ((creation.classReference?.resolve() as? PsiClass)?.qualifiedName != "java.util.ArrayList" ||
                creation.anonymousClass != null || creation.argumentList?.expressionCount != 0) return false
            source(variable)
            if (variable is PsiLocalVariable) constructor.body?.accept(object : JavaRecursiveElementWalkingVisitor() {
                override fun visitElement(element: PsiElement) { tick(element); super.visitElement(element) }
                override fun visitAssignmentExpression(expression: PsiAssignmentExpression) {
                    if (expression.textOffset < loop.textOffset && (expression.lExpression as? PsiReferenceExpression)?.resolve() == variable)
                        fail(expression, "the collection reference was reassigned before the loop")
                    super.visitAssignmentExpression(expression)
                }
            })
            return true
        }
        body.accept(object : JavaRecursiveElementWalkingVisitor() {
            override fun visitElement(element: PsiElement) {
                tick(element)
                if (element is PsiLoopStatement || element is PsiReturnStatement || element is PsiThrowStatement ||
                    element is PsiBreakStatement || element is PsiContinueStatement || element is PsiTryStatement ||
                    element is PsiSynchronizedStatement || element is PsiLabeledStatement)
                    fail(element, "nested loops, early exits and exceptional control flow are not reviewed")
                if (element is PsiStatement && element !is PsiBlockStatement && element !is PsiDeclarationStatement &&
                    element !is PsiExpressionStatement && element !is PsiIfStatement && element !is PsiEmptyStatement)
                    fail(element, "only data declarations, expressions, if/else and blocks are reviewed")
                super.visitElement(element)
            }
            override fun visitClass(aClass: PsiClass) { fail(aClass, "local or anonymous classes are not reviewed") }
            override fun visitLambdaExpression(expression: PsiLambdaExpression) { fail(expression, "callback capture is not reviewed") }
            override fun visitMethodReferenceExpression(expression: PsiMethodReferenceExpression) { fail(expression, "method-reference capture is not reviewed") }
            override fun visitInstanceOfExpression(expression: PsiInstanceOfExpression) { fail(expression, "instanceof and record-pattern accessor effects are not reviewed") }
            private fun stringOperands(expression: PsiExpression, operands: List<PsiExpression>) {
                if (expression.type?.canonicalText == "java.lang.String" && operands.any { !scalarType(it.type) })
                    fail(expression, "string concatenation can invoke an unreviewed toString")
            }
            override fun visitBinaryExpression(expression: PsiBinaryExpression) {
                if (expression.operationTokenType == JavaTokenType.PLUS)
                    stringOperands(expression, listOfNotNull(expression.lOperand, expression.rOperand))
                super.visitBinaryExpression(expression)
            }
            override fun visitPolyadicExpression(expression: PsiPolyadicExpression) {
                if (expression.operationTokenType == JavaTokenType.PLUS) stringOperands(expression, expression.operands.toList())
                super.visitPolyadicExpression(expression)
            }
            override fun visitLocalVariable(variable: PsiLocalVariable) {
                if (!dataType(variable.type)) fail(variable, "a loop local has an unreviewed data type")
                super.visitLocalVariable(variable)
            }
            override fun visitThisExpression(expression: PsiThisExpression) {
                val reference = expression.parent as? PsiReferenceExpression
                if (expression.qualifier != null || reference?.qualifierExpression != expression || reference.resolve() !is PsiField)
                    fail(expression, "a component receiver escapes the data loop")
            }
            override fun visitReferenceExpression(expression: PsiReferenceExpression) {
                if (expression.referenceName == "length" && expression.qualifierExpression?.type is PsiArrayType) {
                    super.visitReferenceExpression(expression)
                    return
                }
                when (val target = expression.resolve()) {
                    is PsiEnumConstant -> { if (!dataType(target.type)) fail(expression, "enum initialization is not reviewed"); target.containingClass?.let(initialize) }
                    is PsiVariable -> {
                        if (unsafeValue(target) || !dataType(target.type)) fail(expression,
                            "reference '${expression.text.take(160)}' (${target.type.canonicalText}) can expose component or unreviewed state")
                        if (target is PsiField) {
                            source(target)
                            if (target.hasModifierProperty(PsiModifier.STATIC) && constant(expression) == null)
                                fail(expression, "static data storage is not a reviewed constant")
                        }
                    }
                    is PsiClass, is PsiMethod, is PsiPackage -> Unit
                    else -> fail(expression, "data reference '${expression.text}' does not resolve")
                }
                super.visitReferenceExpression(expression)
            }
            override fun visitAssignmentExpression(expression: PsiAssignmentExpression) {
                write(expression.lExpression)
                if (expression.operationTokenType == JavaTokenType.PLUSEQ)
                    stringOperands(expression, listOfNotNull(expression.lExpression, expression.rExpression))
                super.visitAssignmentExpression(expression)
            }
            override fun visitPostfixExpression(expression: PsiPostfixExpression) { write(expression.operand); super.visitPostfixExpression(expression) }
            override fun visitPrefixExpression(expression: PsiPrefixExpression) {
                if (expression.operationTokenType in setOf(JavaTokenType.PLUSPLUS, JavaTokenType.MINUSMINUS))
                    expression.operand?.let(::write) ?: fail(expression, "an update operand is unavailable")
                super.visitPrefixExpression(expression)
            }
            override fun visitNewExpression(expression: PsiNewExpression) {
                val type = expression.classReference?.resolve() as? PsiClass ?: fail(expression, "a data constructor does not resolve")
                if (expression.anonymousClass != null || !dataType(expression.type)) fail(expression, "construction can acquire a component or unreviewed data")
                val constructor = expression.resolveConstructor() ?: fail(expression, "a data constructor is unavailable")
                source(constructor)
                if (!type.isRecord || type is PsiCompiledElement) fail(expression, "only canonical source record construction is reviewed in the body")
                initialize(type)
                super.visitNewExpression(expression)
            }
            override fun visitMethodCallExpression(expression: PsiMethodCallExpression) {
                val method = expression.resolveMethod() ?: fail(expression, "a data call does not resolve")
                if (!expression.resolveMethodGenerics().isValidResult) fail(expression, "generic data arguments do not resolve")
                val owner = method.containingClass?.qualifiedName
                source(method)
                val allowed = when {
                    owner in setOf("java.util.List", "java.util.Collection", "java.util.ArrayList") ->
                        method.name in setOf("add", "get", "size", "isEmpty") && freshList(expression.methodExpression.qualifierExpression)
                    owner == "java.lang.String" -> method.name in setOf("format", "valueOf", "substring", "charAt", "length", "isEmpty", "concat", "equals", "startsWith", "endsWith", "trim", "strip") &&
                        expression.argumentList.expressions.all { scalarType(it.type) }
                    owner == "java.math.BigDecimal" -> method.hasModifierProperty(PsiModifier.STATIC) && method.name == "valueOf"
                    owner == "java.time.LocalDate" -> method.name in setOf("of", "plusDays", "minusDays", "getYear", "getMonthValue", "getDayOfMonth")
                    owner == "java.lang.Math" -> expression.argumentList.expressions.all { it.type is PsiPrimitiveType }
                    owner == "java.lang.Enum" -> method.name in setOf("name", "ordinal")
                    method !is PsiCompiledElement && method.containingClass?.isRecord == true -> !method.isPhysical &&
                        method.parameterList.parametersCount == 0 && method.containingClass!!.recordComponents.any { it.name == method.name } && dataType(method.returnType)
                    else -> false
                }
                if (!allowed) fail(expression, "call '${owner}.${method.name}' has no data-loop isolation contract")
                super.visitMethodCallExpression(expression)
            }
        })
    }

    private fun scalarType(type: PsiType?) = type is PsiPrimitiveType || type?.canonicalText in SCALARS

    internal fun dataType(type: PsiType?, depth: Int = 0): Boolean {
        tick(constructor)
        if (depth >= 6 || type == null) return false
        if (scalarType(type)) return true
        if (type is PsiArrayType) return dataType(type.componentType, depth + 1)
        val classType = type as? PsiClassType ?: return false
        val owner = classType.resolve() ?: return false
        if (owner.qualifiedName in setOf("java.time.LocalDate", "java.util.Currency", "java.math.BigDecimal")) return true
        if (owner.qualifiedName in setOf("java.util.ArrayList", "java.util.List"))
            return classType.parameters.size == 1 && dataType(classType.parameters.single(), depth + 1)
        if (owner is PsiCompiledElement) return false
        source(owner)
        if (owner.implementsListTypes.any {
                val marker = it.resolve()
                marker !is PsiCompiledElement || marker.qualifiedName != "java.io.Serializable"
            } || owner.findMethodsByName("finalize", false).any { it.isPhysical && it.parameterList.parametersCount == 0 }) return false
        if (owner.initializers.isNotEmpty() || owner.constructors.any { it.isPhysical && it.body != null } || owner.fields.any { field ->
                field !is PsiEnumConstant && if (field.hasModifierProperty(PsiModifier.STATIC))
                    !field.hasModifierProperty(PsiModifier.FINAL) || field.initializer?.let(constant) == null
                else field.initializer != null
            }) return false
        if (owner.isEnum) return owner.fields.filterIsInstance<PsiEnumConstant>().all {
            (it.argumentList?.expressionCount ?: 0) == 0 && it.initializingClass == null
        }
        return owner.isRecord && owner.recordComponents.all { dataType(it.type, depth + 1) }
    }

    private companion object {
        val SCALARS = setOf("java.lang.String", "java.lang.Boolean", "java.lang.Byte", "java.lang.Short", "java.lang.Integer", "java.lang.Long", "java.lang.Character", "java.lang.Float", "java.lang.Double")
    }
}
