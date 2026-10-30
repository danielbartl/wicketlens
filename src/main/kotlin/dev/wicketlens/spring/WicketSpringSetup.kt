package dev.wicketlens.spring

import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.psi.*
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.ClassInheritorsSearch
import com.intellij.psi.util.CachedValue
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.InheritanceUtil

/** Value-only cached evidence for a single module/source-set application. Unknown setup never becomes a proxy assumption. */
internal object WicketSpringSetup {
    enum class Proxies { UNKNOWN, WRAPPED, RAW }
    data class Setup(val registered: Boolean = false, val proxies: Proxies = Proxies.UNKNOWN, val application: String? = null,
        val configurations: List<String> = emptyList())
    private val KEY = Key.create<CachedValue<Setup>>("wicketlens.spring.setup")
    private const val WEB_APPLICATION = "org.apache.wicket.protocol.http.WebApplication"

    fun forFile(file: PsiFile): Setup {
        if (!WicketSpringSupport.supports(file)) return Setup()
        return CachedValuesManager.getCachedValue(file, KEY) {
            CachedValueProvider.Result.create(compute(file), PsiModificationTracker.MODIFICATION_COUNT,
                ProjectRootManager.getInstance(file.project), VirtualFileManager.VFS_STRUCTURE_MODIFICATIONS)
        }
    }

    private fun compute(file: PsiFile): Setup {
        val index = ProjectFileIndex.getInstance(file.project)
        val module = index.getModuleForFile(file.virtualFile) ?: return Setup()
        val base = JavaPsiFacade.getInstance(file.project).findClass(WEB_APPLICATION, file.resolveScope) ?: return Setup()
        val test = index.isInTestSourceContent(file.virtualFile)
        val applications = mutableListOf<PsiClass>()
        ClassInheritorsSearch.search(base, GlobalSearchScope.moduleScope(module), true).forEach { candidate ->
            ProgressManager.checkCanceled()
            val source = candidate.containingFile?.virtualFile
            if (!candidate.hasModifierProperty(PsiModifier.ABSTRACT) && source != null && index.getModuleForFile(source) == module &&
                index.isInTestSourceContent(source) == test) applications += candidate
            applications.size <= 1
        }
        val app = applications.singleOrNull()?.takeIf { it.qualifiedName != null } ?: return Setup()
        val hierarchy = mutableListOf<PsiClass>()
        var owner: PsiClass? = app
        while (owner?.qualifiedName != WEB_APPLICATION) {
            ProgressManager.checkCanceled()
            if (owner == null || owner is PsiCompiledElement || owner in hierarchy || hierarchy.size >= 12) return Setup()
            hierarchy += owner
            owner = owner.superClass
        }
        val registrations = mutableListOf<Proxies>()
        val reviewed = mutableSetOf<PsiNewExpression>()
        var configurationNames = emptyList<String>()
        for (type in hierarchy.asReversed()) {
            ProgressManager.checkCanceled()
            if (PsiTreeUtil.findChildOfType(type.containingFile, PsiErrorElement::class.java) != null || type.initializers.isNotEmpty() ||
                type.fields.any { it.initializer?.let(WicketSpringSupport::unwrap)?.let { value -> value !is PsiLiteralExpression && value !is PsiClassObjectAccessExpression } == true }) return Setup()
            if (type.constructors.any { constructor -> constructor.body?.statements?.filterNot { it is PsiEmptyStatement }?.any {
                    val call = (it as? PsiExpressionStatement)?.expression as? PsiMethodCallExpression
                    call?.methodExpression?.referenceName != "super" || call.argumentList.expressionCount != 0
                } != false }) return Setup()
            val methods = type.methods.filter { it.name == "init" && it.parameterList.parametersCount == 0 && !it.hasModifierProperty(PsiModifier.STATIC) }
            if (methods.size > 1) return Setup()
            val method = methods.singleOrNull() ?: continue
            val statements = method.body?.statements?.filterNot { it is PsiEmptyStatement } ?: return Setup()
            val first = (statements.firstOrNull() as? PsiExpressionStatement)?.expression as? PsiMethodCallExpression ?: return Setup()
            if (!superInit(first)) return Setup()
            for (statement in statements.drop(1)) {
                val call = (statement as? PsiExpressionStatement)?.expression as? PsiMethodCallExpression ?: return Setup()
                val creation = registration(call) ?: return Setup()
                if (!reviewed.add(creation)) return Setup()
                val arguments = creation.argumentList?.expressions ?: return Setup()
                configurationNames = arguments.getOrNull(1)?.let(::configurations).orEmpty()
                registrations += if (arguments.size < 3) Proxies.WRAPPED else when ((WicketSpringSupport.unwrap(arguments[2]) as? PsiLiteralExpression)?.value) {
                    true -> Proxies.WRAPPED
                    false -> Proxies.RAW
                    else -> Proxies.UNKNOWN
                }
            }
        }
        if (registrations.size != 1 || hierarchy.any { type ->
                PsiTreeUtil.findChildrenOfType(type, PsiNewExpression::class.java).any { expression ->
                    expression.resolveConstructor()?.containingClass?.qualifiedName == WicketSpringSupport.INJECTOR && expression !in reviewed
                }
            }) return Setup()
        return Setup(true, registrations.single(), app.qualifiedName, configurationNames)
    }

    private fun configurations(expression: PsiExpression): List<String> {
        val context = WicketSpringSupport.unwrap(expression) as? PsiNewExpression ?: return emptyList()
        if (context.anonymousClass != null || !context.resolveMethodGenerics().isValidResult ||
            context.resolveConstructor()?.containingClass?.qualifiedName != "org.springframework.context.annotation.AnnotationConfigApplicationContext") return emptyList()
        val arguments = context.argumentList?.expressions?.takeIf { it.isNotEmpty() && it.size <= 16 } ?: return emptyList()
        return arguments.map { argument ->
            val literal = WicketSpringSupport.unwrap(argument) as? PsiClassObjectAccessExpression ?: return emptyList()
            (literal.operand.type as? PsiClassType)?.resolve()?.qualifiedName ?: return emptyList()
        }.distinct()
    }

    private fun superInit(call: PsiMethodCallExpression): Boolean {
        val qualifier = call.methodExpression.qualifierExpression as? PsiSuperExpression ?: return false
        val method = call.resolveMethod() ?: return false
        return qualifier.qualifier == null && call.methodExpression.referenceName == "init" &&
            call.argumentList.expressionCount == 0 && call.resolveMethodGenerics().isValidResult &&
            method.containingClass?.let { it.qualifiedName in setOf(WEB_APPLICATION, "org.apache.wicket.Application") ||
                InheritanceUtil.isInheritor(it, WEB_APPLICATION) } == true
    }

    private fun registration(call: PsiMethodCallExpression): PsiNewExpression? {
        if (!call.resolveMethodGenerics().isValidResult || call.resolveMethod()?.name != "add" ||
            call.resolveMethod()?.containingClass?.qualifiedName != "org.apache.wicket.util.listener.ListenerCollection") return null
        val getter = call.methodExpression.qualifierExpression?.let(WicketSpringSupport::unwrap) as? PsiMethodCallExpression ?: return null
        val method = getter.resolveMethod() ?: return null
        if (!getter.resolveMethodGenerics().isValidResult || getter.argumentList.expressionCount != 0 ||
            method.name != "getComponentInstantiationListeners" || method.containingClass?.qualifiedName != "org.apache.wicket.Application") return null
        val qualifier = getter.methodExpression.qualifierExpression?.let(WicketSpringSupport::unwrap)
        if (qualifier != null && !(qualifier is PsiThisExpression && qualifier.qualifier == null) &&
            !(qualifier is PsiSuperExpression && qualifier.qualifier == null)) return null
        val creation = call.argumentList.expressions.singleOrNull()?.let(WicketSpringSupport::unwrap) as? PsiNewExpression ?: return null
        if (!creation.resolveMethodGenerics().isValidResult || creation.anonymousClass != null ||
            creation.resolveConstructor()?.containingClass?.qualifiedName != WicketSpringSupport.INJECTOR) return null
        val receiver = creation.argumentList?.expressions?.firstOrNull()?.let(WicketSpringSupport::unwrap) as? PsiThisExpression ?: return null
        return creation.takeIf { receiver.qualifier == null }
    }
}
