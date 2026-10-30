package dev.wicketlens.editor

import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.*
import com.intellij.psi.impl.FakePsiElement
import com.intellij.psi.search.SearchScope
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.ClassInheritorsSearch
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.xml.XmlAttribute
import com.intellij.psi.xml.XmlAttributeValue
import com.intellij.refactoring.listeners.RefactoringElementListener
import com.intellij.refactoring.rename.*
import com.intellij.usageView.UsageInfo
import com.intellij.util.IncorrectOperationException
import com.intellij.util.ProcessingContext
import com.intellij.util.containers.MultiMap
import dev.wicketlens.model.DiagnosticCode

internal class WicketIdRenameTarget(pair: WicketIdPair) : FakePsiElement() {
    private val javaPointer = SmartPointerManager.createPointer(pair.literal)
    private val htmlPointer = SmartPointerManager.createPointer(pair.attribute)
    val literal get() = javaPointer.element
    val attribute get() = htmlPointer.element
    override fun getParent(): PsiElement? = literal
    override fun getContainingFile(): PsiFile? = literal?.containingFile
    override fun getName() = literal?.value as? String
    override fun getTextRange() = literal?.textRange
    override fun getTextOffset() = literal?.textOffset ?: 0
    override fun getNavigationElement(): PsiElement = literal ?: this
    override fun isValid() = literal?.isValid == true && attribute?.isValid == true
    override fun isWritable() = literal?.isWritable == true && attribute?.isWritable == true
    override fun getPresentableText() = "Wicket ID '$name'"

    fun currentPair(): WicketIdPair? = literal?.let(WicketIdPsi::pair)?.takeIf { it.attribute == attribute && safe(it) }

    fun conflict(newName: String): String? {
        if (!validName(newName)) return "A Wicket ID must be nonempty and contain no ':', '~', or control characters."
        val pair = currentPair() ?: return "The Wicket ID pair changed or its scope is unresolved. Close the preview and retry."
        val report = pair.page.report
        val node = report.nodes.single { it.nodeId == pair.javaNodeId }
        val markup = report.markup!!.elements.single { it.nodeId == pair.markupNodeId }
        if (report.nodes.any { it.nodeId != node.nodeId && it.parentNodeId == node.parentNodeId && it.wicketId == newName } ||
            node.queuedInNodeId != null && report.nodes.any { it.nodeId != node.nodeId && it.queuedInNodeId == node.queuedInNodeId && it.wicketId == newName } ||
            report.markup.elements.any { it.participatesInComponentTree && it.hasWicketId && it.nodeId != markup.nodeId &&
                it.parentWicketElementId == markup.parentWicketElementId && it.wicketId == newName }) {
            return "Another component already uses '$newName' under this parent in Java or HTML."
        }
        return null
    }

    override fun setName(name: String): PsiElement {
        conflict(name)?.let { throw IncorrectOperationException(it) }
        val java = requireNotNull(literal)
        val html = requireNotNull(attribute).parent as XmlAttribute
        if (!isWritable) throw IncorrectOperationException("Both Wicket ID sources must be writable.")
        // Native rename owns the write command and undo. Resolve/validate both sides before editing either.
        ElementManipulators.handleContentChange(java, name)
        html.setValue(name)
        return this
    }

    companion object {
        fun validName(name: String) = name.isNotEmpty() && name.none { it == ':' || it == '~' || it.isISOControl() }

        fun safe(pair: WicketIdPair): Boolean {
            val node = pair.page.report.nodes.single { it.nodeId == pair.javaNodeId }
            val supported = node.idRenameSafe && pair.page.report.nodes.count { it.idLocation == node.idLocation } == 1 &&
                pair.page.report.markup?.scopeComplete == true &&
                pair.page.report.warnings.none { it.code == DiagnosticCode.UNRESOLVED_SCOPE } &&
                pair.page.report.matching?.findings.orEmpty().all {
                it.code in setOf(DiagnosticCode.MATCHED, DiagnosticCode.JAVA_ONLY, DiagnosticCode.MARKUP_ONLY) && it.causes.isEmpty()
            }
            if (!supported) return false
            val root = pair.page.report.nodes.singleOrNull { it.nodeId == pair.page.report.rootNodeId } ?: return false
            val owner = (WicketIdPsi.source(pair.literal.project, root.javaLocation.file) as? PsiJavaFile)?.classes
                ?.singleOrNull { it.qualifiedName == pair.page.report.qualifiedClassName } ?: return false
            val markup = dev.wicketlens.analysis.WicketPageResolver().resolve(owner)
            if (pair.page.report.markupSuffix.isNotEmpty() || markup.relatedVariantPaths.isNotEmpty()) return false
            // An inherited resource can be shared by siblings even when this class has no inheritors.
            // Check the selected attribute's resource too: an own wicket:extend can include base IDs.
            if (markup.markupPath == null || markup.markupPath != pair.attribute.containingFile.virtualFile?.path) return false
            // A base implementation can also supply IDs to different descendant companions.
            return ClassInheritorsSearch.search(owner, GlobalSearchScope.projectScope(owner.project), true).findFirst() == null
        }

        fun at(file: PsiFile, offset: Int): WicketIdRenameTarget? = WicketIdPsi.valueAt(file, offset)
            ?.let(WicketIdPsi::pair)?.takeIf(::safe)?.let(::WicketIdRenameTarget)
    }
}

class WicketIdRenameProcessor : RenamePsiElementProcessor() {
    override fun canProcessElement(element: PsiElement) = element is WicketIdRenameTarget
    override fun isInplaceRenameSupported() = false
    override fun forcesShowPreview() = true
    override fun isToSearchInComments(element: PsiElement) = false
    override fun isToSearchForTextOccurrences(element: PsiElement) = false
    override fun getElementToSearchInStringsAndComments(element: PsiElement): PsiElement? = null

    override fun findReferences(element: PsiElement, searchScope: SearchScope, searchInCommentsAndStrings: Boolean): Collection<PsiReference> =
        (element as WicketIdRenameTarget).currentPair()?.attribute?.let { listOf(WicketIdReference(it)) }.orEmpty()

    override fun findExistingNameConflicts(element: PsiElement, newName: String, conflicts: MultiMap<PsiElement, String>) {
        (element as WicketIdRenameTarget).conflict(newName)?.let { conflicts.putValue(element, it) }
    }

    override fun renameElement(element: PsiElement, newName: String, usages: Array<out UsageInfo>, listener: RefactoringElementListener?) {
        (element as WicketIdRenameTarget).setName(newName)
        listener?.elementRenamed(element)
    }
}

class WicketIdRenameInputValidator : RenameInputValidatorEx {
    override fun getPattern(): com.intellij.patterns.ElementPattern<out PsiElement> = PlatformPatterns.psiElement(WicketIdRenameTarget::class.java)
    override fun isInputValid(newName: String, element: PsiElement, context: ProcessingContext) = WicketIdRenameTarget.validName(newName)
    override fun getErrorMessage(newName: String, project: Project): String? =
        if (WicketIdRenameTarget.validName(newName)) null else "Use a nonempty Wicket ID without ':', '~', or control characters."
}

class WicketIdRenameHandler : RenameHandler {
    override fun isAvailableOnDataContext(dataContext: DataContext): Boolean {
        val editor = CommonDataKeys.EDITOR.getData(dataContext) ?: return false
        val file = CommonDataKeys.PSI_FILE.getData(dataContext) ?: return false
        return WicketIdRenameTarget.at(file, editor.caretModel.offset) != null
    }

    override fun invoke(project: Project, editor: Editor, file: PsiFile, dataContext: DataContext) {
        PsiDocumentManager.getInstance(project).commitAllDocuments()
        val target = WicketIdRenameTarget.at(file, editor.caretModel.offset) ?: return
        RenamePsiElementProcessor.forElement(target).createDialog(project, target, target.literal, editor).show()
    }

    override fun invoke(project: Project, elements: Array<out PsiElement>, dataContext: DataContext) {
        val editor = CommonDataKeys.EDITOR.getData(dataContext) ?: return
        val file = CommonDataKeys.PSI_FILE.getData(dataContext) ?: return
        invoke(project, editor, file, dataContext)
    }
}
