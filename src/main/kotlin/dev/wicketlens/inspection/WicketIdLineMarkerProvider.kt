package dev.wicketlens.inspection

import com.intellij.codeInsight.daemon.HighlightDisplayKey
import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.LineMarkerProviderDescriptor
import com.intellij.icons.AllIcons
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.profile.codeInspection.InspectionProjectProfileManager
import com.intellij.psi.JavaTokenType
import com.intellij.psi.PsiElement
import com.intellij.psi.xml.XmlTokenType
import com.intellij.xml.util.XmlStringUtil
import dev.wicketlens.ui.WicketLensService

class WicketIdLineMarkerProvider : LineMarkerProviderDescriptor() {
    override fun getName() = "Possible unpaired Wicket IDs"
    override fun getIcon() = AllIcons.General.Warning

    override fun getLineMarkerInfo(element: PsiElement): LineMarkerInfo<*>? {
        if (element.firstChild != null || element.node.elementType !in ANCHOR_TYPES) return null
        val file = element.containingFile ?: return null
        val problems = WicketIdProblems.forFile(file)
        val problem = problems.firstOrNull { it.start == element.textRange.startOffset } ?: return null
        val key = HighlightDisplayKey.find(WicketUnpairedIdInspection.SHORT_NAME) ?: return null
        val profile = InspectionProjectProfileManager.getInstance(element.project).currentProfile
        if (!profile.isToolEnabled(key, element)) return null
        val inspection = profile.getUnwrappedTool(WicketUnpairedIdInspection.SHORT_NAME, element) ?: return null
        val lineProblems = problems.filter { it.line == problem.line }.filter {
            val anchor = file.findElementAt(it.start)
            anchor != null && !inspection.isSuppressedFor(anchor)
        }
        if (lineProblems.firstOrNull() != problem) return null
        val tooltip = XmlStringUtil.wrapInHtml(lineProblems.joinToString("<br>") { XmlStringUtil.escapeString(it.message) } +
            "<br>Click to show the WicketLens report.")
        return LineMarkerInfo(element, element.textRange, icon, { tooltip }, { _, leaf ->
            leaf.project.service<WicketLensService>().analyze(problem.sourceUrl, problem.classOffset, problem.markupSuffix)
            ToolWindowManager.getInstance(leaf.project).getToolWindow("WicketLens")?.activate(null)
        }, GutterIconRenderer.Alignment.RIGHT, { name })
    }

    companion object {
        private val ANCHOR_TYPES = setOf(JavaTokenType.STRING_LITERAL, JavaTokenType.TEXT_BLOCK_LITERAL,
            XmlTokenType.XML_ATTRIBUTE_VALUE_START_DELIMITER, XmlTokenType.XML_ATTRIBUTE_VALUE_TOKEN)
    }
}
