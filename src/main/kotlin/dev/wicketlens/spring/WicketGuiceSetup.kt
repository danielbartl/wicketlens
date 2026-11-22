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
import com.intellij.util.Processor
import dev.wicketlens.model.SourceLocation

/** A source bootstrap declaration for one application in this module and source set. */
internal object WicketGuiceSetup {
    const val ANNOTATION = "com.google.inject.Inject"
    private const val INJECTOR = "org.apache.wicket.guice.GuiceComponentInjector"
    private const val WEB = "org.apache.wicket.protocol.http.WebApplication"
    private val KEY = Key.create<CachedValue<Evidence>>("wicketlens.guice.setup")
    private data class Evidence(val location: SourceLocation?)

    fun forFile(file: PsiFile): SourceLocation? = CachedValuesManager.getCachedValue(file, KEY) {
        CachedValueProvider.Result.create(Evidence(discover(file)), PsiModificationTracker.MODIFICATION_COUNT,
            ProjectRootManager.getInstance(file.project), VirtualFileManager.VFS_STRUCTURE_MODIFICATIONS)
    }.location

    private fun discover(file: PsiFile): SourceLocation? {
        if (!WicketSpringSupport.supports(file)) return null
        val project = file.project
        val index = ProjectFileIndex.getInstance(project)
        val module = index.getModuleForFile(file.virtualFile) ?: return null
        val facade = JavaPsiFacade.getInstance(project)
        val injector = facade.findClass(INJECTOR, file.resolveScope)?.takeIf { it is PsiCompiledElement } ?: return null
        val web = facade.findClass(WEB, file.resolveScope)?.takeIf { it is PsiCompiledElement } ?: return null
        val test = index.isInTestSourceContent(file.virtualFile)
        val applications = mutableListOf<PsiClass>()
        var visits = 0
        val complete = ClassInheritorsSearch.search(web, GlobalSearchScope.moduleScope(module), true).forEach(Processor { type ->
            ProgressManager.checkCanceled()
            if (++visits > 256) return@Processor false
            val source = type.containingFile?.virtualFile
            if (source != null && index.isInSourceContent(source) && index.getModuleForFile(source) == module &&
                index.isInTestSourceContent(source) == test && !type.hasModifierProperty(PsiModifier.ABSTRACT))
                applications += type
            applications.size <= 1
        })
        if (!complete) return null
        val application = applications.singleOrNull()?.takeIf { it.qualifiedName != null && it.superClass == web }
            ?: return null
        if (application is PsiCompiledElement || PsiTreeUtil.findChildOfType(application.containingFile, PsiErrorElement::class.java) != null)
            return null
        val init = application.findMethodsByName("init", false).singleOrNull { it.parameterList.isEmpty }
            ?.takeIf { it.returnType == PsiTypes.voidType() && !it.hasModifierProperty(PsiModifier.STATIC) }
            ?: return null
        val calls = init.body?.statements?.filterNot { it is PsiEmptyStatement }?.map {
            (it as? PsiExpressionStatement)?.expression as? PsiMethodCallExpression ?: return null
        } ?: return null
        if (calls.size != 2 || !WicketSpringSetup.superInit(calls.first())) return null
        val registration = calls.last()
        val target = registration.resolveMethod() ?: return null
        if (!registration.resolveMethodGenerics().isValidResult || target.name != "add" ||
            target.containingClass?.qualifiedName != "org.apache.wicket.util.listener.ListenerCollection") return null
        val getter = registration.methodExpression.qualifierExpression as? PsiMethodCallExpression ?: return null
        val getterMethod = getter.resolveMethod() ?: return null
        val receiver = getter.methodExpression.qualifierExpression
        if (getterMethod.name != "getComponentInstantiationListeners" ||
            getterMethod.containingClass?.qualifiedName != "org.apache.wicket.Application" ||
            getter.argumentList.expressionCount != 0 ||
            receiver != null && !(receiver is PsiThisExpression && receiver.qualifier == null)) return null
        val allocation = registration.argumentList.expressions.singleOrNull() as? PsiNewExpression ?: return null
        val app = allocation.argumentList?.expressions?.singleOrNull() as? PsiThisExpression ?: return null
        if (app.qualifier != null || allocation.anonymousClass != null || !allocation.resolveMethodGenerics().isValidResult ||
            allocation.resolveConstructor()?.containingClass != injector) return null
        val source = registration.containingFile.virtualFile ?: return null
        return SourceLocation(source.path, registration.methodExpression.referenceNameElement?.textOffset ?: registration.textOffset)
    }
}
