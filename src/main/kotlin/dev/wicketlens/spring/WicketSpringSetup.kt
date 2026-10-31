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
        val configurations: List<String> = emptyList(), val reviewedApplication: String? = null,
        val registrationCount: Int? = null, val constructedInjectors: Int = 0)
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
        for (type in hierarchy) {
            ProgressManager.checkCanceled()
            if (PsiTreeUtil.findChildOfType(type.containingFile, PsiErrorElement::class.java) != null || type.initializers.isNotEmpty() ||
                type.fields.any { it.initializer?.let(WicketSpringSupport::unwrap)?.let { value -> value !is PsiLiteralExpression && value !is PsiClassObjectAccessExpression } == true }) return Setup()
            if (type.constructors.any { constructor -> constructor.body?.statements?.filterNot { it is PsiEmptyStatement }?.any {
                    val call = (it as? PsiExpressionStatement)?.expression as? PsiMethodCallExpression
                    call?.methodExpression?.referenceName != "super" || call.argumentList.expressionCount != 0
                } != false }) return Setup()
        }
        val review = WicketSpringBootstrap(hierarchy).review() ?: return Setup()
        val injection = review.injections.singleOrNull() ?: return Setup(reviewedApplication = app.qualifiedName,
            registrationCount = review.injections.size, constructedInjectors = review.constructed)
        return Setup(true, injection.proxies, app.qualifiedName, injection.configurations, app.qualifiedName, 1, review.constructed)
    }

    internal fun configurations(expression: PsiExpression): List<String> {
        val context = WicketSpringSupport.unwrap(expression) as? PsiNewExpression ?: return emptyList()
        if (context.anonymousClass != null || !context.resolveMethodGenerics().isValidResult ||
            context.resolveConstructor()?.containingClass?.qualifiedName != "org.springframework.context.annotation.AnnotationConfigApplicationContext") return emptyList()
        val arguments = context.argumentList?.expressions?.takeIf { it.isNotEmpty() && it.size <= 16 } ?: return emptyList()
        return arguments.map { argument ->
            val literal = WicketSpringSupport.unwrap(argument) as? PsiClassObjectAccessExpression ?: return emptyList()
            (literal.operand.type as? PsiClassType)?.resolve()?.qualifiedName ?: return emptyList()
        }.distinct()
    }

    internal fun superInit(call: PsiMethodCallExpression): Boolean {
        val qualifier = call.methodExpression.qualifierExpression as? PsiSuperExpression ?: return false
        val method = call.resolveMethod() ?: return false
        return qualifier.qualifier == null && call.methodExpression.referenceName == "init" &&
            call.argumentList.expressionCount == 0 && call.resolveMethodGenerics().isValidResult &&
            method.containingClass?.let { it.qualifiedName in setOf(WEB_APPLICATION, "org.apache.wicket.Application") ||
                InheritanceUtil.isInheritor(it, WEB_APPLICATION) } == true
    }

}
