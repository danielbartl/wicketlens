package dev.wicketlens.routes

import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.psi.*
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.ClassInheritorsSearch
import com.intellij.psi.util.InheritanceUtil
import com.intellij.psi.util.PsiTreeUtil
import dev.wicketlens.analysis.WicketComponentTypes
import dev.wicketlens.analysis.WicketPageResolver
import dev.wicketlens.analysis.WicketSourceConstants
import dev.wicketlens.model.SourceLocation
import org.jetbrains.jps.model.java.JavaSourceRootType

enum class MountStatus { DECLARED, CONDITIONAL, UNRESOLVED }
data class WicketMount(val id: String, val path: String?, val pageClass: String?, val status: MountStatus,
    val registration: SourceLocation, val pageSource: SourceLocation?, val markupSource: SourceLocation?,
    val analyzablePage: Boolean, val notes: List<String>)
data class WicketRouteApplication(val className: String, val moduleName: String, val sourceSet: String,
    val source: SourceLocation, val mounts: List<WicketMount>, val notes: List<String>)
data class WicketRoutes(val applications: List<WicketRouteApplication>, val sourceFiles: List<String>,
    val truncated: Boolean = false, val notes: List<String> = emptyList())

/** Project-scoped declarations only. No application, mapper or Spring code is executed. */
internal class WicketRouteAnalyzer {
    fun analyze(project: Project, moduleName: String? = null, applicationClass: String? = null, sourceSet: String = "main"): WicketRoutes {
        val modules = com.intellij.openapi.module.ModuleManager.getInstance(project).modules.filter { moduleName == null || it.name == moduleName }
        val index = ProjectFileIndex.getInstance(project)
        val scope = if (moduleName == null) GlobalSearchScope.projectScope(project) else GlobalSearchScope.moduleScope(modules.single())
        val candidates = linkedMapOf<Pair<String, String>, PsiClass>()
        var truncated = false
        var visits = 0
        JavaPsiFacade.getInstance(project).findClasses(WEB_APPLICATION, GlobalSearchScope.allScope(project)).forEach { base ->
            ClassInheritorsSearch.search(base, scope, true).forEach(com.intellij.util.Processor { type ->
                ProgressManager.checkCanceled()
                if (++visits > 4096) { truncated = true; return@Processor false }
                val file = type.containingFile?.virtualFile
                val owner = file?.let(index::getModuleForFile)
                val root = file?.let(index::getSourceRootForFile)
                if (type !is PsiCompiledElement && type !is PsiAnonymousClass && type.containingFile is PsiJavaFile &&
                    !type.hasModifierProperty(PsiModifier.ABSTRACT) && type.qualifiedName != null && file != null && owner in modules &&
                    index.isInContent(file) && (sourceSet == "any" || index.isInTestSourceContent(file) == (sourceSet == "test")) &&
                    (applicationClass == null || type.qualifiedName == applicationClass) &&
                    (root in ModuleRootManager.getInstance(owner!!).getSourceRoots(JavaSourceRootType.SOURCE) ||
                        root in ModuleRootManager.getInstance(owner).getSourceRoots(JavaSourceRootType.TEST_SOURCE))) {
                    val key = file.url to type.qualifiedName!!
                    if (key !in candidates && candidates.size >= MAX_APPLICATIONS) { truncated = true; return@Processor false }
                    candidates[key] = type
                }
                true
            })
        }
        val sources = linkedSetOf<String>()
        var steps = 0
        var mountCount = 0
        fun tick() { ProgressManager.checkCanceled(); if (++steps > 20_000) throw Limit() }
        fun source(element: PsiElement) {
            tick()
            element.containingFile?.virtualFile?.path?.let { path ->
                if (path !in sources && sources.size >= 512) throw Limit()
                sources += path
            }
        }
        val constants = WicketSourceConstants(::source, ::tick)
        val applications = mutableListOf<WicketRouteApplication>()
        for (app in candidates.values.sortedWith(compareBy({ index.getModuleForFile(it.containingFile.virtualFile)?.name }, { it.qualifiedName }, { it.containingFile.virtualFile.path }))) {
            ProgressManager.checkCanceled()
            val file = app.containingFile.virtualFile
            val notes = linkedSetOf<String>()
            val mounts = mutableListOf<WicketMount>()
            try {
                source(app)
                val methods = app.methods.filter { it.name == "init" && it.parameterList.parametersCount == 0 && !it.hasModifierProperty(PsiModifier.STATIC) }
                if (methods.size > 1) notes += "Multiple init declarations prevent a unique setup scope."
                val method = methods.singleOrNull()
                val errors = PsiTreeUtil.findChildOfType(app.containingFile, PsiErrorElement::class.java) != null
                if (errors) notes += "Java syntax errors make these declarations unresolved."
                if (generateSequence(app.superClass) { it.superClass }.take(12).takeWhile { it.qualifiedName != WEB_APPLICATION }
                        .any { parent -> source(parent); parent.methods.any { it.name == "init" && it.parameterList.parametersCount == 0 } })
                    notes += "Inherited application setup is not expanded in this slice."
                if (method?.body != null) {
                    method.body!!.accept(object : JavaRecursiveElementWalkingVisitor() {
                        override fun visitElement(element: PsiElement) { tick(); super.visitElement(element) }
                        override fun visitClass(aClass: PsiClass) { notes += "Nested or anonymous class setup is not expanded." }
                        override fun visitLambdaExpression(expression: PsiLambdaExpression) { notes += "Deferred lambda setup is not expanded." }
                        override fun visitMethodCallExpression(call: PsiMethodCallExpression) {
                            tick()
                            val target = call.resolveMethod()
                            val name = call.methodExpression.referenceName
                            val qualifier = unwrap(call.methodExpression.qualifierExpression)
                            val self = qualifier == null || qualifier is PsiThisExpression && qualifier.qualifier == null ||
                                qualifier is PsiSuperExpression && qualifier.qualifier == null
                            if (name == "mountPage" && (target == null || target.containingClass?.qualifiedName == WEB_APPLICATION)) {
                                if (!self) notes += "A mount on another or unresolved receiver was not attributed to this application."
                                else {
                                    if (mountCount >= MAX_MOUNTS) throw Limit()
                                    mountCount++
                                    val args = call.argumentList.expressions
                                    val path = args.getOrNull(0)?.let { constants.expression(it) as? String }
                                    val page = (unwrap(args.getOrNull(1)) as? PsiClassObjectAccessExpression)?.operand?.type
                                        ?.let { (it as? PsiClassType)?.resolve() }
                                    val validPage = page?.takeIf { InheritanceUtil.isInheritor(it, "org.apache.wicket.Page") }
                                    val reasons = mutableListOf<String>()
                                    if (path == null) reasons += "The mount path is not a reviewed String constant."
                                    if (validPage == null) reasons += "The target is not a resolved page class literal."
                                    if (target == null || !call.resolveMethodGenerics().isValidResult || args.size != 2)
                                        reasons += "The call is not fully resolved to Wicket mountPage."
                                    if (errors) reasons += "The containing Java source has syntax errors."
                                    val conditional = conditional(call, method)
                                    if (conditional) reasons += "The registration has conditional or non-straight-line reachability."
                                    val pageSource = validPage?.takeUnless { it is PsiCompiledElement }?.let { source(it); location(it.nameIdentifier ?: it) }
                                    val analyzable = validPage?.let(WicketComponentTypes::ownsMarkup) == true && pageSource != null
                                    val markup = validPage?.takeIf { analyzable }?.let { WicketPageResolver().resolve(it) }
                                    val markupSource = markup?.markupPath?.let {
                                        if (it !in sources && sources.size >= 512) throw Limit()
                                        sources += it; SourceLocation(it, 0)
                                    }
                                    val unresolved = path == null || validPage == null || target == null ||
                                        !call.resolveMethodGenerics().isValidResult || args.size != 2 || errors
                                    val status = if (unresolved) MountStatus.UNRESOLVED
                                        else if (conditional) MountStatus.CONDITIONAL else MountStatus.DECLARED
                                    if (validPage != null && pageSource == null) reasons += "The page class has no navigable Java source in this snapshot."
                                    if (analyzable && markupSource == null) reasons += "No unique conventional default HTML companion was resolved."
                                    mounts += WicketMount("mount:${file.path}:${call.textOffset}", path, validPage?.qualifiedName, status,
                                        location(call.methodExpression.referenceNameElement ?: call), pageSource, markupSource, analyzable, reasons)
                                }
                            } else if (!(name == "init" && qualifier is PsiSuperExpression && target?.containingClass?.qualifiedName == WEB_APPLICATION)) {
                                notes += "Other init calls, helpers or mapper operations are not expanded; registrations may be incomplete or changed at runtime."
                            }
                            super.visitMethodCallExpression(call)
                        }
                    })
                } else if (method != null) notes += "The application init body is unavailable."
                if (method == null) notes += "No direct init method was found; inherited setup is not expanded."
            } catch (_: Limit) { truncated = true; notes += "Route discovery reached its source, step or mount limit; refresh a narrower scope." }
            applications += WicketRouteApplication(app.qualifiedName!!, index.getModuleForFile(file)!!.name,
                if (index.isInTestSourceContent(file)) "test" else "main", location(app.nameIdentifier ?: app), mounts, notes.toList())
            if (truncated && (mountCount >= MAX_MOUNTS || steps > 20_000 || sources.size > 512)) break
        }
        return WicketRoutes(applications, sources.toList().sorted(), truncated, listOf(
            "Static direct mountPage declarations, not a complete active runtime route table.",
            "Paths are relative to the deployed Wicket application; host, port and context path are unknown.",
            "Only direct Java init setup is inspected; inherited/helpers/custom mappers/resources/package mounts are not expanded."))
    }

    private fun conditional(call: PsiMethodCallExpression, method: PsiMethod): Boolean {
        val body = method.body ?: return true
        val statement = body.statements.firstOrNull { PsiTreeUtil.isAncestor(it, call, false) } ?: return true
        if (generateSequence(call.parent) { it.parent }.takeWhile { it != body }.any {
                it is PsiIfStatement || it is PsiLoopStatement || it is PsiSwitchStatement || it is PsiSwitchExpression ||
                    it is PsiTryStatement || it is PsiSynchronizedStatement || it is PsiConditionalExpression ||
                    it is PsiPolyadicExpression && it.operationTokenType in setOf(JavaTokenType.ANDAND, JavaTokenType.OROR)
            }) return true
        return body.statements.takeWhile { it != statement }.any { previous ->
            previous !is PsiExpressionStatement && previous !is PsiDeclarationStatement && previous !is PsiEmptyStatement
        }
    }

    private fun location(element: PsiElement) = SourceLocation(element.containingFile.virtualFile.path, element.textOffset)
    private fun unwrap(expression: PsiExpression?): PsiExpression? =
        if (expression is PsiParenthesizedExpression) unwrap(expression.expression) else expression
    private class Limit : RuntimeException(null, null, false, false)
    companion object { const val WEB_APPLICATION = "org.apache.wicket.protocol.http.WebApplication"; const val MAX_APPLICATIONS = 64; const val MAX_MOUNTS = 1000 }
}
