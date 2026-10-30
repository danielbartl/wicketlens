package dev.wicketlens.inspection

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiFile
import com.intellij.xml.util.XmlStringUtil

/** A local inspection also participates in native batch Inspect Code results. */
class WicketUnpairedIdInspection : LocalInspectionTool() {
    override fun runForWholeFile() = true
    override fun isAvailableForFile(file: PsiFile) = WicketIdProblems.supports(file)

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor = object : PsiElementVisitor() {
        override fun visitFile(file: PsiFile) {
            WicketIdProblems.forFile(file).forEach { problem ->
                val leaf = file.findElementAt(problem.start) ?: return@forEach
                val anchor = generateSequence(leaf) { it.parent }
                    .firstOrNull { it.textRange.startOffset == problem.start && it.textRange.endOffset == problem.end }
                    ?: return@forEach
                if (!isSuppressedFor(anchor)) {
                    holder.registerProblem(anchor, XmlStringUtil.escapeString(problem.message), ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                        *WicketIdQuickFixes.forProblem(file, problem))
                }
            }
        }
    }

    companion object {
        const val SHORT_NAME = "WicketUnpairedId"
    }
}
