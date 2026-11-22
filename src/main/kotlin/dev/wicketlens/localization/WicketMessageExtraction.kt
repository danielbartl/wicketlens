package dev.wicketlens.localization

import com.intellij.lang.properties.PropertiesFileType
import com.intellij.lang.properties.psi.PropertiesFile
import com.intellij.lang.properties.psi.PropertyKeyValueFormat
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.ElementManipulators
import com.intellij.psi.PsiAnonymousClass
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiExpressionList
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.PsiLiteralExpression
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiNewExpression
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.xml.XmlTag
import com.intellij.psi.xml.XmlText
import dev.wicketlens.actions.WicketComponentFiles
import dev.wicketlens.analysis.AnalysisUnavailable
import dev.wicketlens.analysis.WicketSourceClassNames
import dev.wicketlens.analysis.WicketSourceResources
import dev.wicketlens.editor.WicketEditorAnalysis
import dev.wicketlens.editor.WicketIdPsi

internal object WicketMessageExtraction {
    const val TITLE = "Extract Text to Wicket Bundle"
    private val keyPattern = Regex("[A-Za-z0-9_.-]{1,120}")
    private val nonContentTags = setOf("script", "style", "textarea", "title", "wicket:message", "wicket:remove")

    sealed interface Destination {
        data class Existing(val file: VirtualFile) : Destination {
            override fun toString() = file.path
        }

        data class New(val location: WicketComponentFiles.Destination) : Destination {
            override fun toString() = location.toString()
        }
    }

    data class Plan(
        val source: VirtualFile,
        val selection: TextRange,
        val revision: Long,
        val owner: String,
        val replacement: TextRange,
        val original: String,
        val value: String,
        val destinations: List<Destination>,
        val bundles: List<VirtualFile>,
    )

    private data class SelectedText(val owner: PsiClass, val range: TextRange, val value: String)

    fun prepare(project: Project, source: VirtualFile, selection: TextRange): Plan {
        val manager = PsiManager.getInstance(project)
        val file = manager.findFile(source)?.takeIf { WicketEditorAnalysis.ready(it) && it.isWritable }
            ?: throw AnalysisUnavailable("Open writable Wicket source and wait for indexing to finish.")
        val document = PsiDocumentManager.getInstance(project).getDocument(file)
            ?: throw AnalysisUnavailable("The selected editor document is unavailable.")
        if (selection.isEmpty || selection.startOffset < 0 || selection.endOffset > file.textLength) {
            throw AnalysisUnavailable("Select the text to extract.")
        }
        val selected = when (source.extension) {
            "html" -> htmlText(file, selection)
            "java" -> labelText(file, selection)
            else -> null
        } ?: throw AnalysisUnavailable("Select plain text in a Wicket HTML companion or a complete Java Label text literal.")

        val owner = selected.owner
        val basename = WicketSourceClassNames.binaryBasename(owner)
            ?: throw AnalysisUnavailable("The component has no conventional message bundle name.")
        val resources = WicketSourceResources.forOwner(owner)?.takeUnless { it.hasCustomMapping }
            ?: throw AnalysisUnavailable("The component's conventional resource scope is uncertain.")
        val baseName = "$basename.properties"
        val bundles = resources.files(basename, setOf("properties"), WicketMessageSources.MAX_BUNDLES)
            ?: throw AnalysisUnavailable("The component has too many conventional bundles for this action.")
        if (bundles.map { it.name }.distinct().size != bundles.size || bundles.any {
                it.length > WicketMessageSources.MAX_BUNDLE_BYTES || resources.owners(it.nameWithoutExtension, false)?.singleOrNull() != owner ||
                    manager.findFile(it) !is PropertiesFile
            }) throw AnalysisUnavailable("The component's bundle files are ambiguous or unavailable.")
        if (resources.owners(basename, false)?.singleOrNull() != owner) {
            throw AnalysisUnavailable("The default bundle name could belong to more than one component.")
        }

        val existing = bundles.singleOrNull { it.name == baseName }
        val destinations = if (existing != null) {
            if (!existing.isWritable) throw AnalysisUnavailable("The default message bundle is read-only.")
            listOf(Destination.Existing(existing))
        } else {
            val directory = owner.containingFile.containingDirectory
                ?: throw AnalysisUnavailable("The component's Java package directory is unavailable.")
            WicketComponentFiles.resourceDestinations(directory, baseName).map(Destination::New)
        }
        return Plan(source, selection, document.modificationStamp, owner.qualifiedName.orEmpty(), selected.range,
            selected.range.substring(file.text), selected.value, destinations, bundles)
    }

    fun validKey(key: String) = keyPattern.matches(key)

    fun extract(project: Project, plan: Plan, destination: Destination, key: String): PsiFile {
        if (!validKey(key)) throw AnalysisUnavailable("Use letters, digits, dots, hyphens, or underscores for the key.")
        return WicketComponentFiles.create(project, TITLE) { created, directories ->
            val current = prepare(project, plan.source, plan.selection)
            if (current != plan || destination !in current.destinations) {
                throw AnalysisUnavailable("The source or bundle destinations changed. Select the text and try again.")
            }

            val manager = PsiManager.getInstance(project)
            val documents = PsiDocumentManager.getInstance(project)
            if (current.bundles.any { bundle ->
                    val properties = manager.findFile(bundle) as? PropertiesFile
                    properties == null || properties.findPropertiesByKey(key).isNotEmpty()
                }) throw AnalysisUnavailable("The key already exists in a component bundle.")
            val source = manager.findFile(current.source) ?: throw AnalysisUnavailable("The selected source is unavailable.")
            val document = documents.getDocument(source)
                ?: throw AnalysisUnavailable("The selected editor document is unavailable.")
            val range = current.replacement
            if (!document.isWritable || document.getRangeGuard(range.startOffset, range.endOffset) != null) {
                throw AnalysisUnavailable("The selected text is read-only.")
            }
            val replacement = if (current.source.extension == "html") {
                "<wicket:message key=\"$key\" escape=\"true\">${current.original}</wicket:message>"
            } else {
                "new org.apache.wicket.model.ResourceModel(\"${StringUtil.escapeStringCharacters(key)}\", ${current.original})"
            }
            val bundleFile = when (destination) {
                is Destination.Existing -> manager.findFile(destination.file)
                is Destination.New -> {
                    val file = PsiFileFactory.getInstance(project).createFileFromText(destination.location.filename, PropertiesFileType.INSTANCE, "")
                    WicketComponentFiles.addResource(destination.location, file, created, directories)
                }
            }
            val bundle = bundleFile as? PropertiesFile ?: throw AnalysisUnavailable("The default message bundle is unavailable.")
            val bundleDocument = documents.getDocument(bundle.containingFile)
                ?: throw AnalysisUnavailable("The message bundle document is unavailable.")
            val originalBundleText = bundleDocument.text.takeIf { destination is Destination.Existing }
            try {
                bundle.addProperty(key, current.value, PropertyKeyValueFormat.MEMORY)
                documents.doPostponedOperationsAndUnblockDocument(bundleDocument)
                document.replaceString(range.startOffset, range.endOffset, replacement)
            } catch (failure: Exception) {
                if (originalBundleText != null) {
                    runCatching {
                        documents.doPostponedOperationsAndUnblockDocument(bundleDocument)
                        if (bundleDocument.text != originalBundleText) bundleDocument.setText(originalBundleText)
                    }.exceptionOrNull()?.let(failure::addSuppressed)
                }
                throw failure
            }
            bundle.containingFile
        }
    }

    private fun htmlText(file: PsiFile, range: TextRange): SelectedText? {
        val leaf = file.findElementAt(range.startOffset) ?: return null
        val text = PsiTreeUtil.getParentOfType(leaf, XmlText::class.java, false) ?: return null
        val tag = text.parent as? XmlTag ?: return null
        if (!text.textRange.contains(range) || generateSequence(tag) { it.parentTag }.any { it.name.lowercase() in nonContentTags }) return null
        val content = range.substring(file.text)
        if (content.isBlank() || '<' in content || '&' in content || "\${" in content) return null
        val owner = WicketMessageSources.markupOwner(file) ?: return null
        return SelectedText(owner, range, content)
    }

    private fun labelText(file: PsiFile, range: TextRange): SelectedText? {
        val literal = WicketIdPsi.valueAt(file, range.startOffset) as? PsiLiteralExpression ?: return null
        val value = (literal.value as? String)?.takeIf { it.isNotBlank() && "\${" !in it } ?: return null
        if (literal.text.startsWith("\"\"\"")) return null
        val valueRange = ElementManipulators.getValueTextRange(literal).shiftRight(literal.textRange.startOffset)
        if (range != literal.textRange && range != valueRange) return null
        val arguments = literal.parent as? PsiExpressionList ?: return null
        if (arguments.expressions.getOrNull(1) != literal) return null
        val creation = when (val parent = arguments.parent) {
            is PsiNewExpression -> parent
            is PsiAnonymousClass -> parent.parent as? PsiNewExpression
            else -> null
        } ?: return null
        val constructor = creation.resolveConstructor() ?: return null
        val parameter = constructor.parameterList.parameters.getOrNull(1)?.type ?: return null
        val expectedType = when (constructor.containingClass?.qualifiedName) {
            "org.apache.wicket.markup.html.basic.Label" -> "java.io.Serializable"
            "org.apache.wicket.markup.html.basic.MultiLineLabel" -> "java.lang.String"
            else -> return null
        }
        if (!parameter.equalsToText(expectedType)) return null
        val owner = PsiTreeUtil.getParentOfType(creation, PsiClass::class.java)
            ?.takeIf(WicketSourceResources::isComponent) ?: return null
        return SelectedText(owner, literal.textRange, value)
    }
}
