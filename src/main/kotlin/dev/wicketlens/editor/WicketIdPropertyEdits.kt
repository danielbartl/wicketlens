package dev.wicketlens.editor

import com.intellij.lang.properties.IProperty
import com.intellij.lang.properties.psi.PropertiesFile
import com.intellij.openapi.progress.ProgressManager
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiManager
import dev.wicketlens.analysis.WicketSourceResources
import dev.wicketlens.localization.WicketMessageSources

/** Conventional owner bundles only. A qualified key has a component path followed by a message key. */
internal object WicketIdPropertyEdits {
    fun collect(pathsByOwner: Map<PsiClass, Set<List<String>>>, filesByOwner: Map<PsiClass, List<com.intellij.openapi.vfs.VirtualFile>>): List<WicketIdPropertyEdit>? {
        val edits = mutableListOf<WicketIdPropertyEdit>()
        for ((owner, files) in filesByOwner) {
            val paths = pathsByOwner.getValue(owner)
            val resources = WicketSourceResources.forOwner(owner) ?: return null
            if (files.any { it.length > WicketMessageSources.MAX_BUNDLE_BYTES } ||
                files.map { it.name }.distinct().size != files.size) return null
            if (paths.any { path -> path.any { '.' in it } }) return null
            for (file in files) {
                val owners = resources.owners(file.nameWithoutExtension, markup = false) ?: return null
                if (owners != listOf(owner)) return null
                val bundle = PsiManager.getInstance(owner.project).findFile(file) as? PropertiesFile ?: return null
                for (property in bundle.properties) {
                    ProgressManager.checkCanceled()
                    val key = property.unescapedKey ?: continue
                    val candidates = paths.filter { key.startsWith(it.joinToString(".") + ".") }
                    if (candidates.size > 1) return null
                    val path = candidates.singleOrNull() ?: continue
                    if (edits.size >= 256 || bundle.findPropertiesByKey(key).size != 1) return null
                    val prefix = path.dropLast(1).joinToString(".").let { if (it.isEmpty()) "" else "$it." }
                    val suffix = key.substring(path.joinToString(".").length)
                    edits += PropertyEdit(property, prefix, suffix)
                }
            }
        }
        return edits
    }

    private class PropertyEdit(private val property: IProperty, private val prefix: String, private val suffix: String) : WicketIdPropertyEdit {
        override val element get() = property.psiElement

        override fun conflict(newId: String): String? {
            if ('.' in newId) return "Use an ID without dots when updating path-qualified bundle keys."
            val destination = prefix + newId + suffix
            if (property.propertiesFile.findPropertiesByKey(destination).any { it.psiElement != element })
                return "A Wicket bundle already contains '$destination'."
            return null
        }

        override fun rename(newId: String) {
            property.setName(prefix + newId + suffix)
        }
    }
}
