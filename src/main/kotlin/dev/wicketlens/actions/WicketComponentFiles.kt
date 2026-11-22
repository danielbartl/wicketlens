package dev.wicketlens.actions

import com.intellij.ide.highlighter.HtmlFileType
import com.intellij.ide.highlighter.JavaFileType
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.JavaDirectoryService
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiNameHelper
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.psi.search.GlobalSearchScope
import dev.wicketlens.analysis.AnalysisUnavailable
import dev.wicketlens.analysis.WicketComponentTypes
import dev.wicketlens.analysis.WicketPageResolver
import dev.wicketlens.analysis.WicketPageSelection
import dev.wicketlens.analysis.WicketSourceClassNames
import dev.wicketlens.model.MarkupKind
import dev.wicketlens.model.MarkupResolutionStatus
import org.jetbrains.jps.model.java.JavaResourceRootProperties
import org.jetbrains.jps.model.java.JavaSourceRootType

internal object WicketComponentFiles {
    enum class Kind(val uiName: String, val baseClass: String) {
        PAGE("Page", WicketComponentTypes.PAGE),
        PANEL("Panel", WicketComponentTypes.PANEL),
    }

    data class Destination(val root: VirtualFile, val packagePath: String, val filename: String) {
        val relativePath = listOf(packagePath, filename).filter { it.isNotEmpty() }.joinToString("/")
        override fun toString() = "${root.path}/$relativePath"
    }

    data class MarkupPlan(
        val owner: SmartPsiElementPointer<PsiClass>,
        val kind: Kind,
        val destinations: List<Destination>,
    )

    data class ComponentPlan(
        val sourceDirectory: VirtualFile,
        val packageName: String,
        val className: String,
        val kind: Kind,
        val destinations: List<Destination>,
    )

    fun markupPlan(project: Project, source: VirtualFile, offset: Int): MarkupPlan {
        val owner = WicketPageSelection().resolve(project, source.url, offset, null)
        return markupPlan(owner)
    }

    private fun markupPlan(owner: PsiClass): MarkupPlan {
        val resolver = WicketPageResolver()
        val resolution = resolver.resolve(owner)
        if (resolution.status != MarkupResolutionStatus.MISSING) {
            throw AnalysisUnavailable(resolution.diagnostics.firstOrNull()?.message ?: "This class already has companion markup.")
        }
        val ancestors = generateSequence(owner.superClass) { it.superClass }
            .takeWhile { it.qualifiedName !in WicketComponentTypes.markupRoots }.take(33).toList()
        if (ancestors.size > 32 || ancestors.any {
                ProgressManager.checkCanceled()
                val inherited = resolver.resolve(it, consumer = owner)
                inherited.status != MarkupResolutionStatus.MISSING || inherited.relatedVariantPaths.isNotEmpty()
            }) {
            throw AnalysisUnavailable("This class may use inherited markup. Create an explicit override in the editor if intended.")
        }

        val java = owner.containingFile.virtualFile
        val directory = PsiManager.getInstance(owner.project).findDirectory(java.parent)
            ?: throw AnalysisUnavailable("The Java package directory is unavailable.")
        val basename = WicketSourceClassNames.binaryBasename(owner)
            ?: throw AnalysisUnavailable("The selected class has no conventional markup filename.")
        val kind = if (WicketComponentTypes.kind(owner) == MarkupKind.PAGE) Kind.PAGE else Kind.PANEL
        return MarkupPlan(SmartPointerManager.createPointer(owner), kind, resourceDestinations(directory, "$basename.html"))
    }

    fun isJavaSourceDirectory(project: Project, directory: VirtualFile): Boolean {
        val type = ProjectFileIndex.getInstance(project).getContainingSourceRootType(directory)
        return directory.isDirectory && (type == JavaSourceRootType.SOURCE || type == JavaSourceRootType.TEST_SOURCE)
    }

    fun componentPlan(directory: PsiDirectory, className: String, kind: Kind): ComponentPlan {
        val project = directory.project
        val source = directory.virtualFile
        if (!isJavaSourceDirectory(project, source) || !source.isWritable) {
            throw AnalysisUnavailable("Select a writable package in a configured Java source root.")
        }
        val names = PsiNameHelper.getInstance(project)
        if (!names.isIdentifier(className) || className in setOf("var", "yield", "record", "sealed", "permits")) {
            throw AnalysisUnavailable("Enter a valid Java class name.")
        }
        val packageName = packageName(directory)
        val index = ProjectFileIndex.getInstance(project)
        val module = index.getModuleForFile(source) ?: throw AnalysisUnavailable("The Java package has no owning module.")
        val scope = GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(module, index.isInTestSourceContent(source))
        val facade = JavaPsiFacade.getInstance(project)
        if (facade.findClass(kind.baseClass, scope) == null) {
            throw AnalysisUnavailable("The module cannot resolve ${kind.baseClass}. Check its Wicket dependencies.")
        }
        val qualifiedName = listOf(packageName, className).filter { it.isNotEmpty() }.joinToString(".")
        if (source.findChild("$className.java") != null || facade.findClass(qualifiedName, scope) != null) {
            throw AnalysisUnavailable("Java class $qualifiedName already exists.")
        }
        return ComponentPlan(source, packageName, className, kind, resourceDestinations(directory, "$className.html"))
    }

    // Match the resolver's same-module and source-set rules. Prefer declared resource roots
    // because many builds exclude HTML stored beside Java sources.
    fun resourceDestinations(directory: PsiDirectory, filename: String): List<Destination> {
        val project = directory.project
        val source = directory.virtualFile
        val index = ProjectFileIndex.getInstance(project)
        val module = index.getModuleForFile(source) ?: throw AnalysisUnavailable("The Java package has no owning module.")
        val sourceRoot = index.getSourceRootForFile(source) ?: throw AnalysisUnavailable("The Java source root is unavailable.")
        val test = index.isInTestSourceContent(source)
        val packagePath = packageName(directory).replace('.', '/')
        val folders = WicketPageResolver.resourceFolders(module, test)
        if (folders.any { (it.jpsElement.properties as? JavaResourceRootProperties)?.relativeOutputPath?.isNotEmpty() == true }) {
            throw AnalysisUnavailable("A resource root has a custom output mapping. The resource destination is uncertain.")
        }

        val adjacent = Destination(source, "", filename)
        val resources = folders.mapNotNull { folder -> folder.file?.let { Destination(it, packagePath, filename) } }.distinct()
        fun inScope(file: VirtualFile, destination: Destination): Boolean {
            val expectedRoot = if (destination == adjacent) sourceRoot else destination.root
            return index.getModuleForFile(file) == module && index.getSourceRootForFile(file) == expectedRoot &&
                index.isInTestSourceContent(file) == test
        }
        for (destination in listOf(adjacent) + resources) {
            val file = destination.root.findFileByRelativePath(destination.relativePath)
            if (file != null && !file.isDirectory && inScope(file, destination)) {
                throw AnalysisUnavailable("A resource already exists at ${file.path}.")
            }
        }

        val candidates = if (folders.isEmpty()) listOf(adjacent) else resources
        return candidates.filter { destination ->
            var parent = destination.root
            for (part in pathParts(destination.packagePath)) {
                val child = parent.findChild(part) ?: break
                parent = child
            }
            parent.isDirectory && parent.isWritable && inScope(parent, destination) &&
                destination.root.findFileByRelativePath(destination.relativePath) == null
        }.sortedBy { it.toString() }.ifEmpty {
            throw AnalysisUnavailable("No writable conventional resource destination was found in this module and source set.")
        }
    }

    fun createMarkup(project: Project, plan: MarkupPlan, destination: Destination): PsiFile =
        create(project, "Create Wicket Markup") { created, directories ->
            val owner = plan.owner.element ?: throw AnalysisUnavailable("The selected Wicket class is no longer available.")
            val current = markupPlan(owner)
            if (current.kind != plan.kind || destination !in current.destinations) {
                throw AnalysisUnavailable("The markup destination changed. Run Create Wicket Markup again.")
            }
            addMarkup(project, destination, current.kind, created, directories)
        }

    fun createComponent(project: Project, plan: ComponentPlan, destination: Destination): PsiFile =
        create(project, "New Wicket ${plan.kind.uiName}") { created, directories ->
            val source = PsiManager.getInstance(project).findDirectory(plan.sourceDirectory)
                ?: throw AnalysisUnavailable("The Java package directory is no longer available.")
            val current = componentPlan(source, plan.className, plan.kind)
            if (current.packageName != plan.packageName || destination !in current.destinations) {
                throw AnalysisUnavailable("The package or markup destination changed. Run the New action again.")
            }
            val filename = "${plan.className}.java"
            val java = PsiFileFactory.getInstance(project).createFileFromText(filename, JavaFileType.INSTANCE, javaText(current))
            val added = addFile(source, java, created)
            addMarkup(project, destination, plan.kind, created, directories)
            added
        }

    fun create(
        project: Project,
        command: String,
        write: (MutableList<Pair<PsiDirectory, String>>, MutableList<PsiDirectory>) -> PsiFile,
    ): PsiFile {
        var result: PsiFile? = null
        PsiDocumentManager.getInstance(project).commitAllDocuments()
        WriteCommandAction.runWriteCommandAction(project, command, null, Runnable {
            if (DumbService.isDumb(project)) throw AnalysisUnavailable("Wait for indexing to finish, then try again.")
            val created = mutableListOf<Pair<PsiDirectory, String>>()
            val directories = mutableListOf<PsiDirectory>()
            try {
                result = write(created, directories)
            } catch (failure: Exception) {
                for ((parent, filename) in created.asReversed()) {
                    runCatching { parent.findFile(filename)?.delete() }.exceptionOrNull()?.let(failure::addSuppressed)
                }
                for (directory in directories.asReversed()) {
                    runCatching {
                        if (directory.virtualFile.children.isEmpty()) directory.delete()
                    }.exceptionOrNull()?.let(failure::addSuppressed)
                }
                throw failure
            }
        })
        return checkNotNull(result)
    }

    private fun addMarkup(
        project: Project,
        destination: Destination,
        kind: Kind,
        created: MutableList<Pair<PsiDirectory, String>>,
        directories: MutableList<PsiDirectory>,
    ): PsiFile {
        val markup = PsiFileFactory.getInstance(project).createFileFromText(destination.filename, HtmlFileType.INSTANCE, markupText(kind))
        return addResource(destination, markup, created, directories)
    }

    fun addResource(
        destination: Destination,
        file: PsiFile,
        created: MutableList<Pair<PsiDirectory, String>>,
        directories: MutableList<PsiDirectory>,
    ): PsiFile {
        var parent = PsiManager.getInstance(file.project).findDirectory(destination.root)
            ?: throw AnalysisUnavailable("The resource root is no longer available.")
        for (part in pathParts(destination.packagePath)) {
            parent = parent.findSubdirectory(part) ?: parent.createSubdirectory(part).also(directories::add)
        }
        return addFile(parent, file, created)
    }

    private fun addFile(parent: PsiDirectory, file: PsiFile, created: MutableList<Pair<PsiDirectory, String>>): PsiFile {
        if (parent.virtualFile.findChild(file.name) != null) throw AnalysisUnavailable("${file.name} already exists.")
        created += parent to file.name
        return parent.add(file) as PsiFile
    }

    private fun packageName(directory: PsiDirectory): String {
        val name = JavaDirectoryService.getInstance().getPackage(directory)?.qualifiedName
            ?: throw AnalysisUnavailable("Select a configured Java package directory.")
        if (name.isNotEmpty() && !PsiNameHelper.getInstance(directory.project).isQualifiedName(name)) {
            throw AnalysisUnavailable("The package directory does not have a valid Java package name.")
        }
        return name
    }

    private fun pathParts(path: String) = path.split('/').filter { it.isNotEmpty() }

    private fun javaText(plan: ComponentPlan): String = buildString {
        if (plan.packageName.isNotEmpty()) append("package ${plan.packageName};\n\n")
        val baseName = plan.kind.baseClass.substringAfterLast('.')
        val superclass = if (baseName == plan.className) plan.kind.baseClass else baseName
        if (superclass == baseName) append("import ${plan.kind.baseClass};\n\n")
        append("public class ${plan.className} extends $superclass {\n")
        if (plan.kind == Kind.PANEL) {
            val idType = if (plan.className == "java") "String" else "java.lang.String"
            append("    public ${plan.className}($idType id) {\n        super(id);\n    }\n")
        }
        append("}\n")
    }

    private fun markupText(kind: Kind) = buildString {
        append("<!DOCTYPE html>\n<html xmlns:wicket=\"http://wicket.apache.org\">\n")
        if (kind == Kind.PAGE) append("<head>\n    <title>Wicket Page</title>\n</head>\n")
        append("<body>\n")
        if (kind == Kind.PANEL) append("    <wicket:panel>\n    </wicket:panel>\n")
        append("</body>\n</html>\n")
    }
}
