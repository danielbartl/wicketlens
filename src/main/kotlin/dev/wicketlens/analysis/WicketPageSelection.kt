package dev.wicketlens.analysis

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.PsiManager
import dev.wicketlens.model.PageReport

/** Resolve PSI only inside the caller's smart read action; return a value snapshot. */
class WicketPageSelection {
    fun analyze(project: Project, sourceUrl: String, caretOffset: Int, qualifiedClassName: String? = null, markupSuffix: String = ""): PageReport {
        val page = resolve(project, sourceUrl, caretOffset, qualifiedClassName)
        return WicketPageAnalyzer().analyze(page, markupSuffix)
            ?: throw AnalysisUnavailable(if (qualifiedClassName != null)
                "The previously analyzed class is no longer a resolvable Wicket Page, Panel, or FormComponentPanel."
            else "No resolvable Wicket WebPage, Panel, or FormComponentPanel was found. Check the project's Wicket dependencies.")
    }

    internal fun resolve(project: Project, sourceUrl: String, caretOffset: Int, qualifiedClassName: String?): PsiClass {
        val file = VirtualFileManager.getInstance().findFileByUrl(sourceUrl)
            ?: throw AnalysisUnavailable("The Java file is no longer available. Open it and try again.")
        val java = PsiManager.getInstance(project).findFile(file) as? PsiJavaFile
            ?: throw AnalysisUnavailable("Open a Java source file to analyze a Wicket Page, Panel, or FormComponentPanel.")
        if (qualifiedClassName != null) {
            return com.intellij.psi.util.PsiTreeUtil.findChildrenOfType(java, PsiClass::class.java)
                .singleOrNull { it.qualifiedName == qualifiedClassName && supported(it) }
                ?: throw AnalysisUnavailable("The previously analyzed class is no longer available. Place the caret in the class and run Analyze Wicket Page.")
        }
        var element = if (java.textLength > 0 && caretOffset >= 0)
            java.findElementAt(caretOffset.coerceAtMost(java.textLength - 1)) else null
        var selected: PsiClass? = null
        while (element != null && selected == null) {
            if (element is PsiClass && supported(element)) selected = element
            element = element.parent
        }
        val candidates = if (selected == null) java.classes.filter(::supported) else listOf(selected)
        return candidates.singleOrNull()
            ?: throw AnalysisUnavailable("Place the caret inside the WebPage, Panel, or FormComponentPanel class you want to analyze.")
    }

    private fun supported(type: PsiClass) = WicketComponentTypes.ownsMarkup(type)
}

class AnalysisUnavailable(message: String) : RuntimeException(message)
