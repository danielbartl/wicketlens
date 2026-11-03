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
                val hierarchy = mutableListOf<PsiClass>()
                var type: PsiClass? = app
                while (type != null && type.qualifiedName != WEB_APPLICATION) {
                    val current = type
                    source(current)
                    val currentFile = current.containingFile?.virtualFile
                    val owner = currentFile?.let(index::getModuleForFile)
                    val root = currentFile?.let(index::getSourceRootForFile)
                    if (current is PsiCompiledElement || currentFile == null || !index.isInSourceContent(currentFile) ||
                        current.containingFile !is PsiJavaFile || owner == null ||
                        (root !in ModuleRootManager.getInstance(owner).getSourceRoots(JavaSourceRootType.SOURCE) &&
                            root !in ModuleRootManager.getInstance(owner).getSourceRoots(JavaSourceRootType.TEST_SOURCE))) {
                        notes += "Application ancestry outside project Java sources is not expanded."
                        break
                    }
                    if (current in hierarchy || hierarchy.size >= MAX_HIERARCHY) {
                        truncated = true
                        notes += "Application ancestry reached the hierarchy limit; deeper setup is not expanded."
                        break
                    }
                    hierarchy += current
                    type = current.superClass
                }
                val syntaxErrors = mutableMapOf<PsiFile, Boolean>()
                fun errors(method: PsiMethod) = syntaxErrors.getOrPut(method.containingFile) {
                    PsiTreeUtil.findChildOfType(method.containingFile, PsiErrorElement::class.java) != null
                }
                val active = mutableSetOf<PsiMethod>()
                var invocations = 0
                fun walk(method: PsiMethod, outerConditional: Boolean, outerErrors: Boolean, chain: List<PsiMethod>) {
                    tick()
                    if (method in active) {
                        notes += "Recursive setup call '${method.name}' is not expanded again."
                        return
                    }
                    if (active.size >= MAX_CALL_DEPTH || invocations >= MAX_SETUP_CALLS) {
                        truncated = true
                        notes += "Setup expansion reached its call depth or invocation limit; deeper registrations are not expanded."
                        return
                    }
                    val body = method.body ?: run { notes += "The setup body '${method.name}' is unavailable."; return }
                    source(method)
                    invocations++
                    active += method
                    val setupPath = chain + method
                    val uncertainSource = outerErrors || errors(method)
                    if (uncertainSource) notes += "Java syntax errors make reached declarations unresolved."
                    try { body.accept(object : JavaRecursiveElementWalkingVisitor() {
                        override fun visitElement(element: PsiElement) { tick(); super.visitElement(element) }
                        override fun visitClass(aClass: PsiClass) { notes += "Nested or anonymous class setup is not expanded." }
                        override fun visitLambdaExpression(expression: PsiLambdaExpression) { notes += "Deferred lambda setup is not expanded." }
                        override fun visitMethodReferenceExpression(expression: PsiMethodReferenceExpression) {
                            notes += "Deferred method-reference setup is not expanded."
                            super.visitMethodReferenceExpression(expression)
                        }
                        override fun visitMethodCallExpression(call: PsiMethodCallExpression) {
                            tick()
                            val target = call.resolveMethod()
                            val name = call.methodExpression.referenceName
                            val qualifier = unwrap(call.methodExpression.qualifierExpression)
                            val self = qualifier == null || qualifier is PsiThisExpression && qualifier.qualifier == null ||
                                qualifier is PsiSuperExpression && qualifier.qualifier == null
                            val mountArgs = call.argumentList.expressions
                            val mapper = if (name == "mount") unwrap(mountArgs.singleOrNull()) as? PsiNewExpression else null
                            val mountedMapper = mapper?.takeIf { (it.anonymousClass?.baseClassType?.resolve() ?: it.classReference?.resolve() as? PsiClass)
                                ?.qualifiedName == MOUNTED_MAPPER }
                            if ((name == "mountPage" || name == "mount" && mountedMapper != null) &&
                                (target == null || target.containingClass?.qualifiedName == WEB_APPLICATION)) {
                                if (!self) notes += "A mount on another or unresolved receiver was not attributed to this application."
                                else {
                                    if (mountCount >= MAX_MOUNTS) throw Limit()
                                    mountCount++
                                    val args = mountedMapper?.argumentList?.expressions ?: mountArgs
                                    val path = args.getOrNull(0)?.let { constants.expression(it) as? String }
                                    val page = (unwrap(args.getOrNull(1)) as? PsiClassObjectAccessExpression)?.operand?.type
                                        ?.let { (it as? PsiClassType)?.resolve() }
                                    val validPage = page?.takeIf { InheritanceUtil.isInheritor(it, "org.apache.wicket.Page") }
                                    val reasons = mutableListOf<String>()
                                    if (path == null) reasons += "The mount path is not a reviewed String constant."
                                    if (validPage == null) reasons += "The target is not a resolved page class literal."
                                    val validCall = target != null && call.resolveMethodGenerics().isValidResult &&
                                        if (mountedMapper == null) args.size == 2 else mountArgs.size == 1 &&
                                            target.parameterList.parameters.singleOrNull()?.type?.canonicalText == "org.apache.wicket.request.IRequestMapper"
                                    val validConstructor = mountedMapper == null ||
                                        mountedMapper.resolveMethodGenerics().let { resolution ->
                                            resolution.isValidResult && (resolution.element as? PsiMethod)?.containingClass?.qualifiedName == MOUNTED_MAPPER &&
                                                args.size in 2..3
                                        }
                                    val anonymousMapper = mountedMapper?.anonymousClass != null
                                    if (!validCall) reasons += "The call is not fully resolved to Wicket $name."
                                    if (!validConstructor) reasons += "The MountedMapper constructor is not fully resolved."
                                    if (anonymousMapper) reasons += "Anonymous MountedMapper overrides or initialization are not reviewed."
                                    if (mountedMapper != null) {
                                        reasons += "Inline MountedMapper registration; mapper behavior is not executed."
                                        if (args.size == 3) reasons += "The explicit page-parameter encoder is not analyzed; parameter encoding may differ."
                                    }
                                    if (uncertainSource) reasons += "The reached setup source or its caller has syntax errors."
                                    val conditional = outerConditional || conditional(call, method)
                                    if (conditional) reasons += "The registration has conditional or non-straight-line reachability."
                                    if (setupPath.size > 1 || method.containingClass != app) reasons += "Setup path: " + setupPath.joinToString(" → ") {
                                        "${it.containingClass?.qualifiedName}.${it.name}"
                                    }
                                    val pageSource = validPage?.takeUnless { it is PsiCompiledElement }?.let { source(it); location(it.nameIdentifier ?: it) }
                                    val analyzable = validPage?.let(WicketComponentTypes::ownsMarkup) == true && pageSource != null
                                    val markup = validPage?.takeIf { analyzable }?.let { WicketPageResolver().resolve(it) }
                                    val markupSource = markup?.markupPath?.let {
                                        if (it !in sources && sources.size >= 512) throw Limit()
                                        sources += it; SourceLocation(it, 0)
                                    }
                                    val unresolved = path == null || validPage == null || !validCall ||
                                        !validConstructor || anonymousMapper || uncertainSource
                                    val status = if (unresolved) MountStatus.UNRESOLVED
                                        else if (conditional) MountStatus.CONDITIONAL else MountStatus.DECLARED
                                    if (validPage != null && pageSource == null) reasons += "The page class has no navigable Java source in this snapshot."
                                    if (analyzable && markupSource == null) reasons += "No unique conventional default HTML companion was resolved."
                                    mounts += WicketMount("mount:${file.path}:${app.textOffset}:${call.textOffset}:${mounts.size}", path, validPage?.qualifiedName, status,
                                        location(call.methodExpression.referenceNameElement ?: call), pageSource, markupSource, analyzable, reasons)
                                }
                            } else if (!(name == "init" && qualifier is PsiSuperExpression && target?.containingClass?.qualifiedName == WEB_APPLICATION)) {
                                val superCall = qualifier is PsiSuperExpression && qualifier.qualifier == null
                                if (self && target != null && call.resolveMethodGenerics().isValidResult &&
                                    target.containingClass in hierarchy && target !is PsiCompiledElement &&
                                    !target.hasModifierProperty(PsiModifier.STATIC) && target.returnType == PsiTypes.voidType() &&
                                    target.parameterList.parametersCount == 0 && call.argumentList.expressionCount == 0 &&
                                    (superCall || target.hasModifierProperty(PsiModifier.PRIVATE) || target.hasModifierProperty(PsiModifier.FINAL))) {
                                    walk(target, outerConditional || conditional(call, method), uncertainSource, setupPath)
                                } else notes += "Other init calls, open/parameterized helpers or mapper operations are not expanded; registrations may be incomplete or changed at runtime."
                            }
                            super.visitMethodCallExpression(call)
                        }
                    }) } finally { active.remove(method) }
                }
                val declarations = hierarchy.firstNotNullOfOrNull { owner -> owner.methods.filter {
                    it.name == "init" && it.parameterList.parametersCount == 0
                }.takeIf { it.isNotEmpty() } }.orEmpty()
                val entry = declarations.singleOrNull()
                if (entry != null && !entry.hasModifierProperty(PsiModifier.STATIC) && !entry.hasModifierProperty(PsiModifier.PRIVATE) &&
                    entry.returnType == PsiTypes.voidType() && !entry.hasModifierProperty(PsiModifier.ABSTRACT)) {
                    walk(entry, false, PsiTreeUtil.findChildOfType(app.containingFile, PsiErrorElement::class.java) != null, emptyList())
                } else if (declarations.isEmpty()) notes += "No reviewed source init method was found."
                else notes += "A unique valid init declaration could not be selected."
            } catch (_: Limit) { truncated = true; notes += "Route discovery reached its source, step or mount limit; refresh a narrower scope." }
            applications += WicketRouteApplication(app.qualifiedName!!, index.getModuleForFile(file)!!.name,
                if (index.isInTestSourceContent(file)) "test" else "main", location(app.nameIdentifier ?: app), mounts, notes.toList())
            if (truncated && (mountCount >= MAX_MOUNTS || steps > 20_000 || sources.size > 512)) break
        }
        return WicketRoutes(applications, sources.toList().sorted(), truncated, listOf(
            "Static mountPage and inline MountedMapper declarations from reviewed source setup, not a complete active runtime route table.",
            "Paths are relative to the deployed Wicket application; host, port and context path are unknown.",
            "Java init inheritance, explicit super calls and private/final no-argument instance helpers are reviewed; open/parameterized helpers, mapper variables/factories/wrappers/subclasses, root mapper mutations and resource/package mounts are not expanded."))
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
    companion object {
        const val MOUNTED_MAPPER = "org.apache.wicket.core.request.mapper.MountedMapper"
        const val WEB_APPLICATION = "org.apache.wicket.protocol.http.WebApplication"
        const val MAX_APPLICATIONS = 64
        const val MAX_MOUNTS = 1000
        const val MAX_HIERARCHY = 12
        const val MAX_CALL_DEPTH = 8
        const val MAX_SETUP_CALLS = 128
    }
}
