package dev.wicketlens.analysis

import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiClass
import dev.wicketlens.model.*
import org.jetbrains.jps.model.java.JavaResourceRootProperties
import org.jetbrains.jps.model.java.JavaResourceRootType

/** Read-only classpath resources for compiled ancestors, scoped to the requesting source module. */
internal class WicketLibraryMarkupResolver {
    fun resolve(owner: PsiClass, consumer: PsiClass, suffix: String): MarkupResolution {
        val index = ProjectFileIndex.getInstance(owner.project)
        fun unavailable(status: MarkupResolutionStatus, message: String, paths: List<String> = emptyList()) =
            MarkupResolution(status, candidatePaths = paths, diagnostics = listOf(Diagnostic(
                DiagnosticCode.UNRESOLVED_MARKUP, message, SourceLocation(owner.containingFile.virtualFile?.path ?: owner.containingFile.name, owner.textOffset))))
        val file = owner.containingFile.virtualFile
        val classRoot = file?.let(index::getClassRootForFile)
        val source = consumer.containingFile.virtualFile
        val module = source?.let(index::getModuleForFile)
        if (file == null || file.extension != "class" || owner.containingClass != null || owner.qualifiedName == null ||
            classRoot == null || module == null || !index.isInLibraryClasses(file)) {
            return unavailable(MarkupResolutionStatus.UNSUPPORTED, "Dependency markup requires a top-level library class and a requesting source module.")
        }
        val test = index.isInTestSourceContent(source)
        val order = ModuleRootManager.getInstance(module).orderEntries().recursively().withoutSdk()
        if (!test) order.productionOnly()
        val roots = order.classes().roots.toMutableSet()
        if (classRoot !in roots) return unavailable(MarkupResolutionStatus.UNSUPPORTED, "The ancestor library is outside the requesting module's classpath.")
        var customMapping = false
        order.forEachModule { dependency ->
            ModuleRootManager.getInstance(dependency).contentEntries.flatMap { it.sourceFolders.toList() }
                .filter { it.rootType == JavaResourceRootType.RESOURCE || test && dependency == module && it.rootType == JavaResourceRootType.TEST_RESOURCE }
                .forEach { folder ->
                    if ((folder.jpsElement.properties as? JavaResourceRootProperties)?.relativeOutputPath?.isNotEmpty() == true) customMapping = true
                    else folder.file?.let(roots::add)
                }
            true
        }
        val packagePath = owner.qualifiedName!!.substringBeforeLast('.', "").replace('.', '/')
        val directories = roots.mapNotNull { root ->
            ProgressManager.checkCanceled()
            if (packagePath.isEmpty()) root else root.findFileByRelativePath(packagePath)
        }.distinctBy { it.url }
        val variants = directories.flatMap { it.children.toList() }.filter {
            it.name.startsWith("${owner.name}_") && it.extension == "html" && !it.isDirectory
        }.map { it.path }.distinct().sorted()
        fun candidates(resourceSuffix: String): List<VirtualFile> = directories.mapNotNull {
            ProgressManager.checkCanceled()
            it.findChild("${owner.name}$resourceSuffix.html")?.takeIf { candidate -> candidate.isValid && !candidate.isDirectory }
        }.distinctBy { it.url }
        var found = candidates(suffix)
        if (customMapping) return unavailable(MarkupResolutionStatus.UNSUPPORTED,
            "A classpath resource root has a custom output mapping; dependency lookup is uncertain.", found.map { it.path }.sorted())
        if (found.isEmpty() && suffix.isNotEmpty()) {
            if (variants.isNotEmpty()) return unavailable(MarkupResolutionStatus.UNSUPPORTED,
                "No exact $suffix dependency variant exists; other variants make locale/style fallback uncertain.", variants)
            found = candidates("")
        }
        val paths = found.map { it.path }.sorted()
        return when (found.size) {
            0 -> unavailable(MarkupResolutionStatus.MISSING, "No conventional markup resource was found for the dependency ancestor.")
            1 -> MarkupResolution(MarkupResolutionStatus.PAIRED, paths.single(), paths,
                markupUrl = found.single().url, relatedVariantPaths = variants)
            else -> unavailable(MarkupResolutionStatus.AMBIGUOUS, "Multiple classpath markup resources were found; no resource was selected.", paths)
        }
    }
}
