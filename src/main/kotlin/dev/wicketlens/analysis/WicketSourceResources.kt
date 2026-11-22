package dev.wicketlens.analysis

import com.intellij.openapi.module.Module
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.JavaDirectoryService
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.PsiManager
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.InheritanceUtil
import org.jetbrains.jps.model.java.JavaResourceRootProperties
import org.jetbrains.jps.model.java.JavaSourceRootType

internal class WicketSourceResources private constructor(
    private val module: Module,
    private val test: Boolean,
    private val packageName: String,
    private val directories: Set<Pair<VirtualFile, VirtualFile>>,
    val hasCustomMapping: Boolean,
) {
    private val index = ProjectFileIndex.getInstance(module.project)

    fun files(basename: String, extensions: Set<String>, limit: Int, maxBytes: Long = Long.MAX_VALUE): List<VirtualFile>? {
        val matches = linkedSetOf<VirtualFile>()
        for ((directory, root) in directories) {
            for (file in directory.children) {
                ProgressManager.checkCanceled()
                if (!file.isDirectory && file.extension in extensions && matchesBasename(file.nameWithoutExtension, basename) &&
                    inScope(file) && index.getSourceRootForFile(file) == root && file.length <= maxBytes) {
                    matches += file
                    if (matches.size > limit) return null
                }
            }
        }
        return matches.sortedWith(compareBy({ it.name }, { it.path }))
    }

    /** Check competing owners, including a class whose name looks like another owner's variant. */
    fun owners(stem: String, markup: Boolean): List<PsiClass>? {
        val basenames = prefixes(stem, '_')
        if (basenames.size > 16) return null
        val names = basenames.flatMap { prefixes(it, '$') }.distinct()
        if (names.size > 64) return null
        val scope = GlobalSearchScope.moduleScope(module)
        val sources = names.flatMap { FilenameIndex.getVirtualFilesByName("$it.java", scope) }.distinct()
        if (sources.size > 64) return null
        val manager = PsiManager.getInstance(module.project)
        val result = mutableListOf<PsiClass>()
        for (source in sources) {
            ProgressManager.checkCanceled()
            if (!inScope(source)) continue
            val java = manager.findFile(source) as? PsiJavaFile ?: continue
            if (java.packageName != packageName || javaSourceType(index, source) == null) continue
            val directory = source.parent?.let(manager::findDirectory) ?: continue
            if (JavaDirectoryService.getInstance().getPackage(directory)?.qualifiedName != packageName) continue
            val members = WicketSourceClassNames.members(java)
            if (!members.complete) return null
            result += members.classes.filter { type ->
                ProgressManager.checkCanceled()
                val basename = WicketSourceClassNames.binaryBasename(type)
                basename != null && matchesBasename(stem, basename) &&
                    if (markup) WicketComponentTypes.ownsMarkup(type) else isComponent(type)
            }
        }
        return result
    }

    fun sameScope(other: WicketSourceResources) = module == other.module && test == other.test

    private fun inScope(file: VirtualFile) = index.getModuleForFile(file) == module && index.isInTestSourceContent(file) == test

    companion object {
        private val suffix = Regex("_[A-Za-z0-9_-]{1,127}")

        fun isComponent(type: PsiClass) = InheritanceUtil.isInheritor(type, "org.apache.wicket.Component")

        fun forOwner(owner: PsiClass): WicketSourceResources? {
            val java = owner.containingFile as? PsiJavaFile ?: return null
            val source = java.virtualFile ?: return null
            val directory = source.parent ?: return null
            val project = owner.project
            val index = ProjectFileIndex.getInstance(project)
            val module = index.getModuleForFile(source) ?: return null
            val sourceRoot = index.getSourceRootForFile(source) ?: return null
            val sourceType = javaSourceType(index, source) ?: return null
            val test = sourceType == JavaSourceRootType.TEST_SOURCE
            val psiDirectory = PsiManager.getInstance(project).findDirectory(directory) ?: return null
            if (JavaDirectoryService.getInstance().getPackage(psiDirectory)?.qualifiedName != java.packageName) return null

            val packagePath = java.packageName.replace('.', '/')
            val directories = linkedSetOf(directory to sourceRoot)
            var customMapping = false
            for (folder in WicketPageResolver.resourceFolders(module, test)) {
                ProgressManager.checkCanceled()
                val mapping = (folder.jpsElement.properties as? JavaResourceRootProperties)?.relativeOutputPath
                if (!mapping.isNullOrEmpty()) {
                    customMapping = true
                    continue
                }
                val root = folder.file ?: continue
                val parent = if (packagePath.isEmpty()) root else root.findFileByRelativePath(packagePath) ?: continue
                if (parent.isDirectory) directories += parent to root
            }
            return WicketSourceResources(module, test, java.packageName, directories, customMapping)
        }

        private fun matchesBasename(stem: String, basename: String) =
            stem == basename || stem.startsWith(basename) && suffix.matches(stem.removePrefix(basename))

        private fun prefixes(name: String, separator: Char) =
            listOf(name) + name.indices.filter { name[it] == separator }.map { name.take(it) }.filter { it.isNotEmpty() }

        private fun javaSourceType(index: ProjectFileIndex, source: VirtualFile) =
            index.getContainingSourceRootType(source)?.takeIf { it == JavaSourceRootType.SOURCE || it == JavaSourceRootType.TEST_SOURCE }
    }
}
