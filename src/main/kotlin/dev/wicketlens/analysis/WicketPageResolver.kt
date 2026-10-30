package dev.wicketlens.analysis

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiCompiledElement
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.PsiModifier
import dev.wicketlens.model.Diagnostic
import dev.wicketlens.model.DiagnosticCode
import dev.wicketlens.model.MarkupResolution
import dev.wicketlens.model.MarkupResolutionStatus
import dev.wicketlens.model.SourceLocation
import org.jetbrains.jps.model.java.JavaResourceRootProperties
import org.jetbrains.jps.model.java.JavaResourceRootType
import org.jetbrains.jps.model.java.JavaSourceRootType

/**
 * Resolves conventional companion files for a source class under a smart read
 * action. Wicket eligibility is checked by WicketPageAnalyzer. Does not parse HTML,
 * refresh the VFS, or retain PSI/project state. Compiled ancestors use the
 * requesting source module's classpath resource resolver.
 */
class WicketPageResolver {
    fun resolve(page: PsiClass, markupSuffix: String = "", consumer: PsiClass = page): MarkupResolution {
        ApplicationManager.getApplication().assertReadAccessAllowed()
        ProgressManager.checkCanceled()
        val psiFile = page.containingFile as? PsiJavaFile
        val file = psiFile?.virtualFile
        val source = SourceLocation(file?.path ?: page.containingFile.name, page.textOffset)
        var variants = emptyList<String>()

        fun unresolved(
            status: MarkupResolutionStatus,
            message: String,
            candidates: List<String> = emptyList(),
        ) = MarkupResolution(
            status = status,
            candidatePaths = candidates,
            diagnostics = listOf(Diagnostic(DiagnosticCode.UNRESOLVED_MARKUP, message, source)),
            relatedVariantPaths = variants,
        )

        if (markupSuffix.isNotEmpty() && (markupSuffix.length > 128 || !markupSuffix.matches(Regex("_[A-Za-z0-9_-]+")))) {
            return unresolved(MarkupResolutionStatus.UNSUPPORTED, "The selected markup variant suffix is not supported.")
        }
        if (generateSequence(page) { it.superClass }.takeWhile { it.qualifiedName !in WicketComponentTypes.markupRoots }.take(32).any { type ->
                ProgressManager.checkCanceled()
                type.methods.any { method -> !method.hasModifierProperty(PsiModifier.STATIC) &&
                    method.name in setOf("getMarkup", "getMarkupType", "getMarkupResourceStream", "getMarkupSourcingStrategy") &&
                    method.findDeepestSuperMethods().any { it.containingClass?.qualifiedName?.startsWith("org.apache.wicket.") == true }
                }
            }) return unresolved(MarkupResolutionStatus.UNSUPPORTED, "A custom Wicket markup provider/type/strategy prevents conventional resource pairing.")
        if (page is PsiCompiledElement) return WicketLibraryMarkupResolver().resolve(page, consumer, markupSuffix)

        if (psiFile == null || !psiFile.isPhysical || file == null || file.extension != "java" ||
            page.containingClass != null || page.qualifiedName == null || page.name != file.nameWithoutExtension) {
            return unresolved(
                MarkupResolutionStatus.UNSUPPORTED,
                "Markup pairing requires a top-level source class with the same basename as its physical Java file.",
            )
        }

        val index = ProjectFileIndex.getInstance(page.project)
        val module = index.getModuleForFile(file)
            ?: return unresolved(MarkupResolutionStatus.UNSUPPORTED, "The Java file has no owning project module.")
        val sourceRoot = index.getSourceRootForFile(file)
            ?: return unresolved(MarkupResolutionStatus.UNSUPPORTED, "The Java file is outside a configured Java source root.")
        val folders = ModuleRootManager.getInstance(module).contentEntries.flatMap { it.sourceFolders.toList() }
        val sourceFolder = folders.singleOrNull { it.file == sourceRoot }
        if (sourceFolder == null || sourceFolder.rootType !in setOf(JavaSourceRootType.SOURCE, JavaSourceRootType.TEST_SOURCE)) {
            return unresolved(MarkupResolutionStatus.UNSUPPORTED, "The Java file is outside a configured Java source root.")
        }
        val relativePackage = file.parent?.let { VfsUtilCore.getRelativePath(it, sourceRoot, '.') }
        val expectedPackage = listOf(sourceFolder.packagePrefix, relativePackage.orEmpty())
            .filter { it.isNotEmpty() }.joinToString(".")
        if (relativePackage == null || expectedPackage != psiFile.packageName) {
            return unresolved(
                MarkupResolutionStatus.UNSUPPORTED,
                "The Java package declaration does not match its configured source directory.",
            )
        }

        val testScope = sourceFolder.rootType == JavaSourceRootType.TEST_SOURCE
        val resourceType = if (testScope) JavaResourceRootType.TEST_RESOURCE else JavaResourceRootType.RESOURCE
        val filename = "${page.name}$markupSuffix.html"
        val packagePath = psiFile.packageName.replace('.', '/')
        val relativePath = if (packagePath.isEmpty()) filename else "$packagePath/$filename"
        val candidates = linkedSetOf<VirtualFile>()
        val variantFiles = linkedSetOf<VirtualFile>()

        fun addCandidate(candidate: VirtualFile?, expectedRoot: VirtualFile?) {
            ProgressManager.checkCanceled()
            if (candidate != null && !candidate.isDirectory && candidate.isValid &&
                index.getModuleForFile(candidate) == module && index.getSourceRootForFile(candidate) == expectedRoot &&
                index.isInTestSourceContent(candidate) == testScope) {
                candidates += candidate
            }
        }

        fun collectVariants(directory: VirtualFile?, expectedRoot: VirtualFile?) {
            directory?.children?.forEach { candidate ->
                ProgressManager.checkCanceled()
                if (candidate.name.startsWith("${page.name}_") && candidate.extension == "html" && !candidate.isDirectory &&
                    index.getModuleForFile(candidate) == module && index.getSourceRootForFile(candidate) == expectedRoot &&
                    index.isInTestSourceContent(candidate) == testScope) variantFiles += candidate
            }
        }

        addCandidate(file.parent?.findChild(filename), sourceRoot)
        collectVariants(file.parent, sourceRoot)
        var unsupportedOutputMapping = false
        folders.filter { it.rootType == resourceType }.forEach { folder ->
            ProgressManager.checkCanceled()
            val root = folder.file ?: return@forEach
            val properties = folder.jpsElement.properties as? JavaResourceRootProperties
            if (properties?.relativeOutputPath?.isNotEmpty() == true) {
                unsupportedOutputMapping = true
            } else {
                addCandidate(root.findFileByRelativePath(relativePath), root)
                collectVariants(if (packagePath.isEmpty()) root else root.findFileByRelativePath(packagePath), root)
            }
        }
        val paths = candidates.map { it.path }.distinct().sorted()
        variants = variantFiles.map { it.path }.sorted()
        if (unsupportedOutputMapping) {
            return unresolved(
                MarkupResolutionStatus.UNSUPPORTED,
                "A resource root has a custom output mapping; conventional markup lookup cannot establish a unique companion.",
                paths,
            )
        }
        if (paths.isEmpty() && markupSuffix.isNotEmpty()) {
            if (variants.isNotEmpty()) return unresolved(MarkupResolutionStatus.UNSUPPORTED,
                "No exact $markupSuffix markup variant exists for ${page.name}; other variants make locale/style fallback uncertain.", variants)
            return resolve(page, consumer = consumer)
        }
        return when (paths.size) {
            0 -> unresolved(
                MarkupResolutionStatus.MISSING,
                "No $filename companion was found beside the Java source or in its module's matching resource package.",
            )
            1 -> MarkupResolution(
                MarkupResolutionStatus.PAIRED, paths.single(), paths,
                markupUrl = candidates.single().url,
                relatedVariantPaths = variants,
            )
            else -> unresolved(
                MarkupResolutionStatus.AMBIGUOUS,
                "Multiple companion markup files were found: ${paths.joinToString()}. No file was selected.",
                paths,
            )
        }
    }
}
