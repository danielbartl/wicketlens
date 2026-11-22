package dev.wicketlens.routes

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.psi.*
import com.intellij.psi.util.CachedValue
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker
import com.intellij.xml.util.XmlStringUtil
import dev.wicketlens.editor.WicketEditorAnalysis

class WicketRouteConflictInspection : LocalInspectionTool() {
    override fun runForWholeFile() = true

    override fun isAvailableForFile(file: PsiFile) = file is PsiJavaFile && WicketEditorAnalysis.supports(file)

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor = object : PsiElementVisitor() {
        override fun visitFile(file: PsiFile) {
            if (file !is PsiJavaFile || !WicketEditorAnalysis.ready(file) || !hasRegistration(file)) return
            val project = file.project
            val routes = CachedValuesManager.getManager(project).getCachedValue(project, CACHE, {
                val result = WicketRouteAnalyzer().analyze(project, sourceSet = "any")
                CachedValueProvider.Result.create(result, PsiModificationTracker.MODIFICATION_COUNT,
                    ProjectRootManager.getInstance(project), VirtualFileManager.VFS_STRUCTURE_MODIFICATIONS)
            }, false)
            val source = file.virtualFile.path
            val sourceSet = if (ProjectFileIndex.getInstance(project).isInTestSourceContent(file.virtualFile)) "test" else "main"
            val byOffset = linkedMapOf<Int, MutableList<String>>()
            for (application in routes.applications.filter { it.sourceSet == sourceSet }) {
                ProgressManager.checkCanceled()
                for (mount in application.mounts) {
                    val conflict = mount.conflict ?: continue
                    if (mount.registration.file == source) {
                        byOffset.getOrPut(mount.registration.offset) { mutableListOf() }
                            .add("${application.className}. $conflict")
                    }
                }
            }
            for ((offset, conflicts) in byOffset) {
                val anchor = file.findElementAt(offset) as? PsiIdentifier ?: continue
                if (isSuppressedFor(anchor)) continue
                holder.registerProblem(anchor, XmlStringUtil.escapeString(conflicts.distinct().joinToString(" ")),
                    ProblemHighlightType.WEAK_WARNING)
            }
        }
    }

    private fun hasRegistration(file: PsiJavaFile): Boolean {
        var found = false
        file.accept(object : JavaRecursiveElementWalkingVisitor() {
            override fun visitElement(element: PsiElement) {
                ProgressManager.checkCanceled()
                if (!found) super.visitElement(element)
            }
            override fun visitMethodCallExpression(expression: PsiMethodCallExpression) {
                val name = expression.methodExpression.referenceName
                if (name in REGISTRATIONS) {
                    val owner = expression.resolveMethod()?.containingClass?.qualifiedName
                    if (owner == WicketRouteAnalyzer.WEB_APPLICATION || owner == WicketRouteMappers.APPLICATION ||
                        owner == WicketRouteMappers.COMPOUND_INTERFACE || owner == WicketRouteMappers.COMPOUND) found = true
                }
                if (!found) super.visitMethodCallExpression(expression)
            }
        })
        return found
    }

    private companion object {
        val CACHE = Key.create<CachedValue<WicketRoutes>>("wicketlens.route.conflicts")
        val REGISTRATIONS = setOf("mount", "mountPage", "mountPackage", "mountResource", "setRootRequestMapper", "add")
    }
}
