package dev.wicketlens.spring

import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.psi.*
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.util.*

/** Injection intent, not proof that every instance is wired. Bounds or unavailable ancestry leave paths unknown. */
internal object WicketSpringInjectionPaths {
    data class Paths(val wicket: Boolean = false, val spring: Boolean = false, val managed: Boolean = false, val unknown: Boolean = false)
    private val KEY = Key.create<CachedValue<Paths>>("wicketlens.spring.injection.paths")
    private const val AUTOWIRE_FACTORY = "org.springframework.beans.factory.config.AutowireCapableBeanFactory"
    private const val AUTOWIRE_SUPPORT = "org.springframework.web.context.support.SpringBeanAutowiringSupport"

    fun forClass(owner: PsiClass): Paths {
        if (!WicketSpringSupport.supports(owner.containingFile)) return Paths(unknown = true)
        return CachedValuesManager.getCachedValue(owner, KEY) {
            CachedValueProvider.Result.create(compute(owner), PsiModificationTracker.MODIFICATION_COUNT,
                ProjectRootManager.getInstance(owner.project), VirtualFileManager.VFS_STRUCTURE_MODIFICATIONS)
        }
    }

    private fun compute(owner: PsiClass): Paths {
        var wicket = false
        var spring = false
        var managed = false
        var unknown = false
        val hierarchy = mutableSetOf<PsiClass>()
        val files = linkedSetOf<PsiJavaFile>()
        var type: PsiClass? = owner
        while (type != null && type.qualifiedName != "java.lang.Object") {
            ProgressManager.checkCanceled()
            if (!hierarchy.add(type) || hierarchy.size > 12) return Paths(unknown = true)
            if (type.qualifiedName == AUTOWIRE_SUPPORT || InheritanceUtil.isInheritor(type, AUTOWIRE_SUPPORT)) spring = true
            // Published framework bases do not install application-specific manual injection.
            if (type is PsiCompiledElement) {
                if (type.qualifiedName?.startsWith("org.apache.wicket.") != true && type.qualifiedName != AUTOWIRE_SUPPORT) unknown = true
                break
            }
            val file = type.containingFile as? PsiJavaFile ?: return Paths(unknown = true)
            files += file
            if (type.extendsListTypes.any { it.resolve() == null }) unknown = true
            if (PsiTreeUtil.findChildOfType(type, PsiErrorElement::class.java) != null) unknown = true
            when (managed(type)) {
                true -> managed = true
                null -> unknown = true
                false -> Unit
            }
            val parent = type.superClass
            if (parent == null && type.extendsListTypes.isNotEmpty()) unknown = true
            type = parent
        }
        val file = owner.containingFile
        val index = ProjectFileIndex.getInstance(owner.project)
        val module = index.getModuleForFile(file.virtualFile) ?: return Paths(unknown = true)
        val test = index.isInTestSourceContent(file.virtualFile)
        var references = 0
        ReferencesSearch.search(owner, GlobalSearchScope.moduleScope(module)).forEach(com.intellij.util.Processor { reference ->
            ProgressManager.checkCanceled()
            if (++references > 256) { unknown = true; return@Processor false }
            val caller = reference.element.containingFile as? PsiJavaFile ?: return@Processor true
            val virtual = caller.virtualFile ?: return@Processor true
            if (index.getModuleForFile(virtual) == module && index.isInTestSourceContent(virtual) == test) {
                files += caller
                val method = PsiTreeUtil.getParentOfType(reference.element, PsiMethod::class.java)
                if (method != null && WicketSpringSupport.annotation(method, "org.springframework.context.annotation.Bean") != null) managed = true
            }
            if (files.size > 32) { unknown = true; return@Processor false }
            true
        })
        var calls = 0
        for (source in files.take(32)) {
            source.accept(object : JavaRecursiveElementWalkingVisitor() {
                override fun visitElement(element: PsiElement) {
                    ProgressManager.checkCanceled()
                    super.visitElement(element)
                }

                override fun visitMethodCallExpression(expression: PsiMethodCallExpression) {
                    if (++calls > 4096) { unknown = true; stopWalking(); return }
                    super.visitMethodCallExpression(expression)
                    val method = expression.resolveMethod() ?: return
                    if (!expression.resolveMethodGenerics().isValidResult) return
                    val declaringType = method.containingClass ?: return
                    val declaring = declaringType.qualifiedName ?: return
                    val arguments = expression.argumentList.expressions
                    val argument = arguments.firstOrNull()?.let(WicketSpringSupport::unwrap) ?: return
                    val target = (argument.type as? PsiClassType)?.resolve()
                    val sameType = target == owner || target?.isInheritor(owner, true) == true ||
                        argument is PsiThisExpression && target in hierarchy
                    if (sameType) {
                        if (method.name == "inject" && arguments.size == 1 && (declaring == "org.apache.wicket.injection.Injector" ||
                                InheritanceUtil.isInheritor(declaringType, "org.apache.wicket.injection.Injector"))) wicket = true
                        if (declaring == AUTOWIRE_SUPPORT && (method.name == "processInjectionBasedOnCurrentContext" && arguments.size == 1 ||
                                method.name == "processInjectionBasedOnServletContext" && arguments.size == 2) ||
                            (declaring == AUTOWIRE_FACTORY || InheritanceUtil.isInheritor(declaringType, AUTOWIRE_FACTORY)) && (method.name == "autowireBean" && arguments.size == 1 ||
                                method.name == "configureBean" && arguments.size == 2 || method.name == "autowireBeanProperties" && arguments.size == 3) ||
                            declaring == "org.springframework.beans.factory.annotation.AutowiredAnnotationBeanPostProcessor" && method.name == "processInjection" && arguments.size == 1) spring = true
                    }
                    if ((declaring == AUTOWIRE_FACTORY || InheritanceUtil.isInheritor(declaringType, AUTOWIRE_FACTORY)) && method.name in setOf("createBean", "autowire") && argument is PsiClassObjectAccessExpression &&
                        (argument.operand.type as? PsiClassType)?.resolve() == owner) managed = true
                }
            })
            if (calls > 4096) break
        }
        return Paths(wicket, spring, managed, unknown)
    }

    /** A stereotype is management intent even when its context/profile is not statically known. */
    private fun managed(owner: PsiClass): Boolean? {
        val seen = mutableSetOf<PsiClass>()
        fun visit(annotation: PsiAnnotation, depth: Int): Boolean? {
            ProgressManager.checkCanceled()
            val type = annotation.resolveAnnotationType() ?: return null
            if (type.qualifiedName in setOf("org.springframework.stereotype.Component", "org.springframework.beans.factory.annotation.Configurable", "jakarta.inject.Named")) return true
            if (!seen.add(type)) return false
            if (depth >= 8 || seen.size > 64) return null
            var unknown = false
            for (meta in type.modifierList?.annotations.orEmpty()) {
                if (meta.qualifiedName?.startsWith("java.lang.annotation.") == true) continue
                when (visit(meta, depth + 1)) { true -> return true; null -> unknown = true; false -> Unit }
            }
            return if (unknown) null else false
        }
        var unknown = false
        for (annotation in owner.modifierList?.annotations.orEmpty()) {
            when (visit(annotation, 0)) { true -> return true; null -> unknown = true; false -> Unit }
        }
        return if (unknown) null else false
    }
}
