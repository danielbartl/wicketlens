package dev.wicketlens.spring

import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.psi.*
import com.intellij.psi.util.InheritanceUtil
import com.intellij.psi.util.PsiTreeUtil

/** Only resolved Wicket injection intent enables local rules; plain Wicket and generic @Inject stay silent. */
internal object WicketSpringSupport {
    const val BEAN = "org.apache.wicket.spring.injection.annot.SpringBean"
    const val INJECTOR = "org.apache.wicket.spring.injection.annot.SpringComponentInjector"
    const val COMPONENT = "org.apache.wicket.Component"
    const val BEHAVIOR = "org.apache.wicket.behavior.Behavior"

    fun supports(file: PsiFile): Boolean = file is PsiJavaFile && file.isPhysical && file.virtualFile != null &&
        !DumbService.isDumb(file.project) && ProjectFileIndex.getInstance(file.project).isInContent(file.virtualFile) &&
        PsiDocumentManager.getInstance(file.project).uncommittedDocuments.isEmpty()

    fun automatic(type: PsiClass?) = type != null &&
        (InheritanceUtil.isInheritor(type, COMPONENT) || InheritanceUtil.isInheritor(type, BEHAVIOR))

    fun annotation(owner: PsiModifierListOwner, name: String): PsiAnnotation? =
        owner.modifierList?.findAnnotation(name)?.takeIf { it.resolveAnnotationType()?.qualifiedName == name }

    fun injected(field: PsiField, file: PsiFile): Boolean {
        if (field.hasModifierProperty(PsiModifier.STATIC) || field.type is PsiPrimitiveType || !automatic(field.containingClass)) return false
        return annotation(field, BEAN) != null || annotation(field, "jakarta.inject.Inject") != null && WicketSpringSetup.forFile(file).registered
    }

    fun unwrap(expression: PsiExpression): PsiExpression {
        var value = expression
        while (value is PsiParenthesizedExpression) {
            ProgressManager.checkCanceled()
            value = value.expression ?: return value
        }
        return value
    }

    fun nullValue(expression: PsiExpression?) = expression?.let(::unwrap)?.let {
        it is PsiLiteralExpression && it.value == null && it.text == "null"
    } == true

    private fun injectThis(statement: PsiStatement?): Boolean {
        val call = (statement as? PsiExpressionStatement)?.expression as? PsiMethodCallExpression ?: return false
        val target = call.resolveMethod() ?: return false
        if (!call.resolveMethodGenerics().isValidResult || target.name != "inject" ||
            target.containingClass?.qualifiedName != "org.apache.wicket.injection.Injector") return false
        val argument = call.argumentList.expressions.singleOrNull()?.let(::unwrap) as? PsiThisExpression ?: return false
        if (argument.qualifier != null) return false
        val getter = call.methodExpression.qualifierExpression?.let(::unwrap) as? PsiMethodCallExpression ?: return false
        val method = getter.resolveMethod() ?: return false
        return getter.resolveMethodGenerics().isValidResult && getter.argumentList.expressionCount == 0 &&
            method.name == "get" && method.containingClass?.qualifiedName == "org.apache.wicket.injection.Injector" &&
            getter.methodExpression.qualifierExpression?.let { (it as? PsiReferenceExpression)?.resolve() is PsiClass } != false
    }

    fun repairedInitializer(field: PsiField): Boolean {
        if (!nullValue(field.initializer)) return false // Injection deliberately ignores non-null fields.
        val owner = field.containingClass ?: return false
        if (owner.initializers.any { !it.hasModifierProperty(PsiModifier.STATIC) } || owner.fields.any {
                !it.hasModifierProperty(PsiModifier.STATIC) && it.initializer?.let { value ->
                    unwrap(value) !is PsiLiteralExpression && unwrap(value) !is PsiClassObjectAccessExpression
                } == true
            }) return false
        return owner.constructors.isNotEmpty() && owner.constructors.all { constructor ->
            val statements = constructor.body?.statements?.filterNot { it is PsiEmptyStatement }.orEmpty()
            val first = (statements.firstOrNull() as? PsiExpressionStatement)?.expression as? PsiMethodCallExpression
            val offset = if (first?.methodExpression?.referenceName == "super") 1 else 0
            injectThis(statements.getOrNull(offset))
        }
    }

    fun repairedAssignment(expression: PsiAssignmentExpression): Boolean {
        if (!nullValue(expression.rExpression)) return false
        val statement = expression.parent as? PsiExpressionStatement ?: return false
        val block = statement.parent as? PsiCodeBlock ?: return false
        val statements = block.statements.filterNot { it is PsiEmptyStatement }
        return injectThis(statements.getOrNull(statements.indexOf(statement) + 1))
    }

    fun constructionWrite(expression: PsiAssignmentExpression): PsiField? {
        val reference = unwrap(expression.lExpression) as? PsiReferenceExpression ?: return null
        if (!reference.advancedResolve(false).isValidResult) return null
        val field = reference.resolve() as? PsiField ?: return null
        val receiver = reference.qualifierExpression?.let(::unwrap)
        if (receiver != null && !(receiver is PsiThisExpression && receiver.qualifier == null) &&
            !(receiver is PsiSuperExpression && receiver.qualifier == null)) return null
        val owner = PsiTreeUtil.getParentOfType(expression, PsiClass::class.java) ?: return null
        if (!automatic(owner) || !(owner == field.containingClass || field.containingClass?.let { owner.isInheritor(it, true) } == true)) return null
        var parent: PsiElement? = expression.parent
        while (parent != null) {
            ProgressManager.checkCanceled()
            when (parent) {
                is PsiLambdaExpression, is PsiClass -> return null
                is PsiIfStatement -> {
                    val condition = parent.condition?.let(::unwrap) as? PsiLiteralExpression
                    val value = condition?.value as? Boolean
                    if (value != null && PsiTreeUtil.isAncestor(if (value) parent.elseBranch else parent.thenBranch, expression, false)) return null
                }
                is PsiMethod -> return if (parent.isConstructor) field else null
                is PsiClassInitializer -> return if (parent.hasModifierProperty(PsiModifier.STATIC)) null else field
                is PsiField -> return if (parent.hasModifierProperty(PsiModifier.STATIC)) null else field
            }
            parent = parent.parent
        }
        return null
    }

    fun customSerialization(owner: PsiClass): Boolean {
        var type: PsiClass? = owner
        val seen = mutableSetOf<PsiClass>()
        while (type != null && type.qualifiedName !in setOf(COMPONENT, BEHAVIOR)) {
            ProgressManager.checkCanceled()
            if (!seen.add(type) || seen.size > 12) return true
            if (type.methods.any { it.name in setOf("readObject", "readResolve", "writeObject", "writeReplace", "readExternal", "writeExternal") }) return true
            type = type.superClass
        }
        return false
    }
}
