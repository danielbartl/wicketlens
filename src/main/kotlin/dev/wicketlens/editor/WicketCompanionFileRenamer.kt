package dev.wicketlens.editor

import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.DumbService
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiNamedElement
import com.intellij.refactoring.rename.naming.AutomaticRenamer
import com.intellij.refactoring.rename.naming.AutomaticRenamerFactory
import com.intellij.refactoring.rename.naming.NameSuggester
import com.intellij.usageView.UsageInfo
import dev.wicketlens.analysis.WicketComponentTypes
import dev.wicketlens.analysis.WicketPageResolver
import dev.wicketlens.analysis.WicketSourceClassNames
import dev.wicketlens.analysis.WicketSourceResources
import dev.wicketlens.model.MarkupResolutionStatus

/** Offers conventional companion filenames through IntelliJ's class rename preview. */
class WicketCompanionFileRenamerFactory : AutomaticRenamerFactory {
    override fun isApplicable(element: PsiElement) = element is PsiClass && companions(element, null).isNotEmpty()
    override fun getOptionName() = "Rename Wicket companion files"
    override fun isEnabled() = true
    override fun setEnabled(enabled: Boolean) = Unit

    override fun createRenamer(element: PsiElement, newName: String, usages: MutableCollection<UsageInfo>): AutomaticRenamer {
        val type = element as PsiClass
        return CompanionRenamer(companions(type, newName), type.name.orEmpty(), newName)
    }

    private fun companions(type: PsiClass, newName: String?): Map<PsiFile, String> {
        if (DumbService.isDumb(type.project)) return emptyMap()
        val oldBasename = WicketSourceClassNames.binaryBasename(type) ?: return emptyMap()
        val java = type.containingFile as? PsiJavaFile ?: return emptyMap()
        val members = WicketSourceClassNames.members(java)
        if (!members.complete) return emptyMap()
        val resources = WicketSourceResources.forOwner(type) ?: return emptyMap()
        if (resources.hasCustomMapping) return emptyMap()
        val renamedBasename = oldBasename.dropLast(type.name?.length ?: return emptyMap()) + newName.orEmpty()
        val affected = members.classes.filter {
            generateSequence(it) { member -> member.containingClass }.any { member -> member == type }
        }.toSet()
        val manager = PsiManager.getInstance(type.project)
        val filesToRename = linkedMapOf<PsiFile, String>()
        for (owner in affected) {
            ProgressManager.checkCanceled()
            if (!WicketSourceResources.isComponent(owner)) continue
            val basename = WicketSourceClassNames.binaryBasename(owner) ?: return emptyMap()
            val markup = WicketComponentTypes.ownsMarkup(owner)
            val extensions = if (markup) setOf("html", "properties") else setOf("properties")
            val files = resources.files(basename, extensions, MAX_FILES) ?: return emptyMap()
            if (files.isEmpty()) continue
            if (markup && files.any { it.extension == "html" } &&
                WicketPageResolver().resolve(owner).status == MarkupResolutionStatus.UNSUPPORTED) return emptyMap()
            if (files.map { it.name }.distinct().size != files.size) return emptyMap()
            val newBase = renamedBasename + basename.removePrefix(oldBasename)
            if (newName != null && newBase != basename) {
                val existing = resources.files(newBase, extensions, MAX_FILES) ?: return emptyMap()
                if (existing.any { it !in files }) return emptyMap()
            }
            for (file in files) {
                val html = file.extension == "html"
                if (resources.owners(file.nameWithoutExtension, html)?.singleOrNull() != owner || !file.isWritable) return emptyMap()
                val suffix = file.name.removePrefix(basename)
                val destination = if (newName == null) file.name else newBase + suffix
                if (newName != null && destination != file.name) {
                    val existing = file.parent.findChild(destination)
                    if (existing != null && existing != file) return emptyMap()
                    val owners = resources.owners(newBase + file.nameWithoutExtension.removePrefix(basename), html) ?: return emptyMap()
                    if (owners.any { it !in affected }) return emptyMap()
                }
                val companion = manager.findFile(file) ?: return emptyMap()
                if (companion in filesToRename || filesToRename.size >= MAX_FILES) return emptyMap()
                filesToRename[companion] = destination
            }
        }
        return filesToRename
    }

    private class CompanionRenamer(private val files: Map<PsiFile, String>, oldName: String, newName: String) : AutomaticRenamer() {
        init {
            myElements.addAll(files.keys)
            suggestAllNames(oldName, newName)
        }

        override fun suggestNameForElement(element: PsiNamedElement, suggester: NameSuggester, newName: String, oldName: String) =
            files.getValue(element as PsiFile)

        override fun allowChangeSuggestedName() = false
        override fun isSelectedByDefault() = true
        override fun getDialogTitle() = "Rename Wicket Companion Files"
        override fun getDialogDescription() = "Choose the Wicket files to rename with the Java class."
        override fun entityName() = "Wicket companion files"
    }

    companion object {
        private const val MAX_FILES = 64
    }
}
