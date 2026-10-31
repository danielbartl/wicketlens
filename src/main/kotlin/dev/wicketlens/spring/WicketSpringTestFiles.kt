package dev.wicketlens.spring

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.psi.*
import dev.wicketlens.analysis.AnalysisUnavailable
import org.jetbrains.jps.model.java.JavaSourceRootProperties
import org.jetbrains.jps.model.java.JavaSourceRootType

data class SpringTestRoot(val url: String, val path: String, val prefix: String) {
    override fun toString() = path
}

/** Creation only in an existing, non-generated Java test root; collision checks precede directory changes. */
object WicketSpringTestFiles {
    fun roots(project: Project, sourceUrl: String, packageName: String): List<SpringTestRoot> {
        val source = VirtualFileManager.getInstance().findFileByUrl(sourceUrl) ?: return emptyList()
        val module = ProjectFileIndex.getInstance(project).getModuleForFile(source) ?: return emptyList()
        return ModuleRootManager.getInstance(module).contentEntries.flatMap { it.sourceFolders.toList() }.mapNotNull { folder ->
            if (folder.rootType != JavaSourceRootType.TEST_SOURCE ||
                (folder.jpsElement.properties as? JavaSourceRootProperties)?.isForGeneratedSources == true) return@mapNotNull null
            val root = folder.file?.takeIf { it.isValid && it.isWritable } ?: return@mapNotNull null
            val prefix = folder.packagePrefix
            if (prefix.isNotEmpty() && packageName != prefix && !packageName.startsWith("$prefix.")) return@mapNotNull null
            SpringTestRoot(root.url, root.path, prefix)
        }
    }

    fun validName(project: Project, name: String, target: String? = null): Boolean =
        PsiNameHelper.getInstance(project).isIdentifier(name) && !name.contains('.') && name != target?.substringAfterLast('.') &&
            name !in setOf("var", "yield", "record", "sealed", "permits", "Page", "WebPage", "WebApplication", "SpringComponentInjector",
                "WicketTester", "AfterEach", "BeforeEach", "Test", "StaticApplicationContext", "Throwable", "UnsupportedOperationException", "Class", "Override")

    fun requireCurrent(project: Project, prepared: PreparedSpringTest) {
        if (PsiDocumentManager.getInstance(project).uncommittedDocuments.isNotEmpty() || !prepared.version.isCurrent(project, 0))
            throw AnalysisUnavailable("The sources changed. Close the preview and generate the test again.")
    }

    fun create(project: Project, prepared: PreparedSpringTest, root: SpringTestRoot, testName: String): PsiJavaFile {
        if (!validName(project, testName, prepared.plan.target)) throw AnalysisUnavailable("Enter a valid Java test class name that differs from the subject and scaffold imports.")
        var created: PsiJavaFile? = null
        WriteCommandAction.runWriteCommandAction(project, "Generate Wicket Spring Test", null, Runnable {
            requireCurrent(project, prepared)
            if (root !in roots(project, prepared.sourceUrl, prepared.plan.packageName))
                throw AnalysisUnavailable("The selected test source root is no longer available.")
            val virtual = VirtualFileManager.getInstance().findFileByUrl(root.url) ?: throw AnalysisUnavailable("The test root is unavailable.")
            val suffix = if (root.prefix.isEmpty()) prepared.plan.packageName else prepared.plan.packageName.removePrefix(root.prefix).removePrefix(".")
            val relative = suffix.replace('.', '/')
            var parent = virtual
            for (part in suffix.split('.').filter { it.isNotEmpty() }) {
                val child = parent.findChild(part) ?: break
                if (!child.isDirectory || !child.isWritable) throw AnalysisUnavailable("A package directory is blocked or read-only: ${child.path}")
                parent = child
            }
            if (virtual.findFileByRelativePath(listOf(relative, "$testName.java").filter { it.isNotEmpty() }.joinToString("/")) != null)
                throw AnalysisUnavailable("$testName.java already exists. Choose another name; existing files are never replaced.")
            val file = PsiFileFactory.getInstance(project).createFileFromText("$testName.java", com.intellij.ide.highlighter.JavaFileType.INSTANCE,
                WicketSpringTestGenerator().render(prepared.plan, testName)) as PsiJavaFile
            var directory = PsiManager.getInstance(project).findDirectory(virtual) ?: throw AnalysisUnavailable("The test root is unavailable.")
            for (part in suffix.split('.').filter { it.isNotEmpty() }) directory = directory.findSubdirectory(part) ?: directory.createSubdirectory(part)
            created = directory.add(file) as PsiJavaFile
        })
        return created!!
    }
}
