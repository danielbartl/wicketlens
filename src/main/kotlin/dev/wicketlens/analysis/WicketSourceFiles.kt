package dev.wicketlens.analysis

import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.JarFileSystem
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile

/** Find already indexed source/resource files without refreshing or loading application code. */
internal object WicketSourceFiles {
    fun find(project: Project, path: String): VirtualFile? =
        ProjectRootManager.getInstance(project).contentRoots.asSequence().map { it.fileSystem }.distinct()
            .mapNotNull { it.findFileByPath(path) }.firstOrNull()
            ?: if ("!/" in path) JarFileSystem.getInstance().findFileByPath(path)
            else LocalFileSystem.getInstance().findFileByPath(path)
}
