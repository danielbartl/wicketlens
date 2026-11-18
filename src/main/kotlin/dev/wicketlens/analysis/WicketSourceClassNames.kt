package dev.wicketlens.analysis

import com.intellij.openapi.progress.ProgressManager
import com.intellij.psi.*

/** Conventional resource identity for physical top-level and named member classes. */
internal object WicketSourceClassNames {
    const val MAX_DEPTH = 16
    const val MAX_CLASSES = 256

    fun binaryBasename(type: PsiClass): String? {
        val file = type.containingFile as? PsiJavaFile ?: return null
        if (!file.isPhysical || type.qualifiedName == null) return null
        val names = mutableListOf<String>()
        var current = type
        while (true) {
            ProgressManager.checkCanceled()
            if (names.size >= MAX_DEPTH || current is PsiAnonymousClass) return null
            names.add(0, current.name ?: return null)
            when (val parent = current.parent) {
                is PsiClass -> current = parent
                is PsiJavaFile -> return names.joinToString("$").takeIf {
                    current in parent.classes && current.name == file.virtualFile?.nameWithoutExtension
                }
                else -> return null // Local classes have no stable conventional source identity.
            }
        }
    }

    data class Members(val classes: List<PsiClass>, val complete: Boolean)

    fun members(file: PsiJavaFile): Members {
        val result = mutableListOf<PsiClass>()
        var complete = true
        fun visit(type: PsiClass, depth: Int) {
            ProgressManager.checkCanceled()
            if (result.size >= MAX_CLASSES || depth > MAX_DEPTH) { complete = false; return }
            result += type
            type.innerClasses.forEach { visit(it, depth + 1) }
        }
        file.classes.forEach { visit(it, 1) }
        return Members(result, complete)
    }
}
