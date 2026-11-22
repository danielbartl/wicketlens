package dev.wicketlens.editor

import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.*
import com.intellij.psi.impl.FakePsiElement
import com.intellij.psi.search.SearchScope
import com.intellij.psi.search.GlobalSearchScope
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

internal class WicketIdRenameTarget(pair: WicketIdPair, initial: WicketIdRenamePlan) : FakePsiElement() {
    private val javaPointer = SmartPointerManager.createPointer(pair.literal)
    private val htmlPointer = SmartPointerManager.createPointer(pair.attribute)
    private val previewContexts = initial.contexts
    private val previewPointers = (initial.attributes + initial.properties.map { it.element }).map {
        SmartPointerManager.createPointer(it)
    }
    val literal get() = javaPointer.element
    val attribute get() = htmlPointer.element
    override fun getParent(): PsiElement? = literal
    override fun getContainingFile(): PsiFile? = literal?.containingFile
    override fun getName() = literal?.value as? String
    override fun getTextRange() = literal?.textRange
    override fun getTextOffset() = literal?.textOffset ?: 0
    override fun getNavigationElement(): PsiElement = literal ?: this
    override fun isValid() = literal?.isValid == true && attribute?.isValid == true
    override fun isWritable() = currentPlan()?.writable() == true
    override fun getPresentableText() = "Wicket ID '$name'"

    fun currentPlan(): WicketIdRenamePlan? {
        val java = literal ?: return null
        val html = attribute ?: return null
        val pair = WicketIdPsi.pair(html)?.takeIf { it.literal == java } ?: return null
        val current = plan(pair) ?: return null
        val preview = previewPointers.mapNotNull { it.element }
        if (current.contexts != previewContexts || preview.size != previewPointers.size ||
            preview.toSet() != (current.attributes + current.properties.map { it.element }).toSet()) return null
        return current
    }

    fun conflict(newName: String): String? {
        if (!validName(newName)) return "A Wicket ID must be nonempty and contain no ':', '~', or control characters."
        val plan = currentPlan() ?: return "The Wicket ID scope changed. Close the preview and retry."
        return conflict(plan, newName)
    }

    private fun conflict(plan: WicketIdRenamePlan, newName: String): String? {
        for (pair in plan.pairs) {
            val report = pair.page.report
            val node = report.nodes.single { it.nodeId == pair.javaNodeId }
            val markup = report.markup!!.elements.single { it.nodeId == pair.markupNodeId }
            if (report.nodes.any { it.nodeId != node.nodeId && it.parentNodeId == node.parentNodeId && it.wicketId == newName } ||
                node.queuedInNodeId != null && report.nodes.any { it.nodeId != node.nodeId && it.queuedInNodeId == node.queuedInNodeId && it.wicketId == newName } ||
                report.markup.elements.any { it.participatesInComponentTree && it.hasWicketId && it.nodeId != markup.nodeId &&
                    it.parentWicketElementId == markup.parentWicketElementId && it.wicketId == newName }) {
                return "Another component already uses '$newName' under this parent in Java or HTML."
            }
        }
        val destinations = plan.properties.mapNotNull { it.conflict(newName) }
        return destinations.firstOrNull()
    }

    override fun setName(name: String): PsiElement {
        if (!validName(name)) throw IncorrectOperationException("Use a valid Wicket ID.")
        val plan = currentPlan() ?: throw IncorrectOperationException("The Wicket ID scope changed. Close the preview and retry.")
        conflict(plan, name)?.let { throw IncorrectOperationException(it) }
        if (!plan.writable()) throw IncorrectOperationException("Every Wicket ID source must be writable.")
        // IntelliJ provides one write command and undo across the complete, rechecked plan.
        val html = plan.attributes.map { it.parent as XmlAttribute }
        plan.properties.forEach { it.rename(name) }
        ElementManipulators.handleContentChange(plan.literal, name)
        html.forEach { it.setValue(name) }
        return this
    }

    companion object {
        fun validName(name: String) = name.isNotEmpty() && name.none { it == ':' || it == '~' || it.isISOControl() }

        internal fun safeBinding(pair: WicketIdPair, requireOwnedLiteral: Boolean = true): Boolean {
            val node = pair.page.report.nodes.single { it.nodeId == pair.javaNodeId }
            val supported = (!requireOwnedLiteral || node.idRenameSafe) && !node.template && !node.conditional &&
                !node.computedId && !node.idUsedInComputation &&
                pair.page.report.nodes.count { it.idLocation == node.idLocation } == 1 &&
                pair.page.report.markup?.scopeComplete == true &&
                pair.page.report.warnings.none { it.code == DiagnosticCode.UNRESOLVED_SCOPE } &&
                pair.page.report.matching?.findings.orEmpty().all {
                it.code in setOf(DiagnosticCode.MATCHED, DiagnosticCode.JAVA_ONLY, DiagnosticCode.MARKUP_ONLY) && it.causes.isEmpty()
            }
            return supported
        }

        fun safe(pair: WicketIdPair): Boolean = plan(pair) != null

        private fun plan(pair: WicketIdPair): WicketIdRenamePlan? {
            val origin = PsiTreeUtil.getParentOfType(pair.literal, PsiClass::class.java) ?: return null
            if (!safeBinding(pair, requireOwnedLiteral = origin.qualifiedName == pair.page.report.qualifiedClassName)) return null
            val root = pair.page.report.nodes.singleOrNull { it.nodeId == pair.page.report.rootNodeId } ?: return null
            val source = WicketIdPsi.source(pair.literal.project, root.javaLocation.file) as? PsiJavaFile ?: return null
            val className = pair.page.report.qualifiedClassName
            val owner = source.classes.singleOrNull { it.qualifiedName == className } ?: run {
                val members = dev.wicketlens.analysis.WicketSourceClassNames.members(source)
                if (!members.complete) return null
                members.classes.singleOrNull { it.qualifiedName == className } ?: return null
            }
            if (owner.containingClass != null && !isolatedMember(owner)) return null
            return WicketIdRenameScopes.collect(pair, owner)
        }

        // Discovery is navigation-only. A member with outside source uses needs a
        // multi-context edit contract; no indexed candidate can authorize a write.
        private fun isolatedMember(owner: PsiClass): Boolean {
            var count = 0
            var isolated = true
            com.intellij.psi.search.searches.ReferencesSearch.search(owner, GlobalSearchScope.projectScope(owner.project)).forEach(com.intellij.util.Processor { reference ->
                com.intellij.openapi.progress.ProgressManager.checkCanceled()
                if (++count > 256 || !PsiTreeUtil.isAncestor(owner, reference.element, false)) {
                    isolated = false
                    false
                } else true
            })
            return isolated
        }

        fun at(file: PsiFile, offset: Int): WicketIdRenameTarget? = WicketIdPsi.valueAt(file, offset)
            ?.let(WicketIdPsi::pair)?.let { pair -> plan(pair)?.let { WicketIdRenameTarget(pair, it) } }
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
        (element as WicketIdRenameTarget).currentPlan()?.let { plan ->
            plan.attributes.map(::WicketIdReference) + plan.properties.map { edit ->
                object : PsiReferenceBase<PsiElement>(edit.element, TextRange(0, edit.element.textLength)) {
                    override fun resolve() = element
                }
            }
        }.orEmpty()

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
