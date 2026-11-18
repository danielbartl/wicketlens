package dev.wicketlens.editor

import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.psi.*
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.xml.XmlAttribute
import com.intellij.psi.xml.XmlAttributeValue
import com.intellij.psi.xml.XmlFile
import com.intellij.psi.xml.XmlTag
import dev.wicketlens.analysis.*
import dev.wicketlens.model.Confidence
import dev.wicketlens.model.MarkupKind
import dev.wicketlens.model.MarkupResolutionStatus
import dev.wicketlens.model.SourceLocation

/** Editor hints from a single conventional companion pair. No component bindings are created. */
internal object WicketCompanionIds {
    private const val MAX_FILE_LENGTH = 512 * 1024
    private const val MAX_ELEMENTS = 30_000
    private const val MAX_CANDIDATES = 128
    private const val FRAGMENT = "org.apache.wicket.markup.html.panel.Fragment"

    private data class Companion(val owner: PsiClass, val markup: XmlFile)

    fun suggestions(value: PsiElement): List<WicketIdCandidate> {
        val pair = companion(value) ?: return emptyList()
        val occupied = when (value) {
            is XmlAttributeValue -> {
                val tag = (value.parent as XmlAttribute).parent
                val values = markupIds(pair) ?: return emptyList()
                if (value !in values || hasDuplicateMarkupParent(tag, values)) return emptyList()
                val parent = markupParent(tag)
                values.asSequence().filter { it != value && markupParent((it.parent as XmlAttribute).parent) == parent }
                    .mapNotNull(::id).toSet()
            }
            is PsiLiteralExpression -> {
                val scope = javaSiblingScope(value)
                if (scope == null) emptySet() else (javaIds(pair.owner) ?: return emptyList()).asSequence()
                    .filter { it != value && javaSiblingScope(it) == scope }.mapNotNull(::id).toSet()
            }
            else -> return emptyList()
        }
        val matches = counterparts(value, pair) ?: return emptyList()
        val candidates = matches.mapNotNull { target ->
            val id = id(target)?.takeUnless { it in occupied } ?: return@mapNotNull null
            id to target
        }.groupBy({ it.first }, { it.second })
        if (candidates.size > MAX_CANDIDATES) return emptyList()
        return candidates.toSortedMap().map { (id, targets) ->
            WicketIdCandidate(id, targets.mapNotNull(::componentType).distinct(),
                targets.map { context(it) }.distinct(), Confidence.UNRESOLVED,
                targets.map { SourceLocation(it.containingFile.virtualFile.path, it.textOffset) }, possible = true)
        }
    }

    fun targets(value: PsiElement): List<PsiElement> {
        val id = id(value) ?: return emptyList()
        val pair = companion(value) ?: return emptyList()
        if (value is XmlAttributeValue && value !in (markupIds(pair) ?: return emptyList())) return emptyList()
        val matches = counterparts(value, pair)?.filter { id(it) == id } ?: return emptyList()
        return matches.takeIf { it.size <= MAX_CANDIDATES }.orEmpty()
    }

    private fun companion(value: PsiElement): Companion? {
        val file = value.containingFile
        if (!WicketEditorAnalysis.ready(file) || file.textLength > MAX_FILE_LENGTH) return null
        if (file is PsiJavaFile) {
            if (value !is PsiLiteralExpression || WicketIdPsi.idCreation(value) == null) return null
            val owner = markupOwner(value) ?: return null
            val resolution = WicketPageResolver().resolve(owner)
            if (resolution.status != MarkupResolutionStatus.PAIRED) return null
            val markup = resolveFile(file, resolution.markupUrl ?: return null) as? XmlFile ?: return null
            return Companion(owner, markup)
        }
        if (file !is XmlFile || WicketIdPsi.htmlId(value) == null) return null
        val target = try {
            WicketEditorTargetResolver().resolve(file.project, file.virtualFile.url, value.textOffset)
        } catch (_: AnalysisUnavailable) {
            return null
        }
        val java = resolveFile(file, target.sourceUrl) as? PsiJavaFile ?: return null
        val owner = WicketSourceClassNames.members(java).classes.singleOrNull { it.qualifiedName == target.qualifiedClassName } ?: return null
        return Companion(owner, file)
    }

    private fun resolveFile(origin: PsiFile, url: String): PsiFile? {
        val file = VirtualFileManager.getInstance().findFileByUrl(url) ?: return null
        return PsiManager.getInstance(origin.project).findFile(file)?.takeIf { it.textLength <= MAX_FILE_LENGTH }
    }

    private fun counterparts(value: PsiElement, pair: Companion) =
        if (value is PsiLiteralExpression) markupIds(pair) else javaIds(pair.owner)

    private fun markupOwner(value: PsiElement): PsiClass? =
        generateSequence(value.parent) { it.parent }.filterIsInstance<PsiClass>().firstOrNull(WicketComponentTypes::ownsMarkup)

    private fun javaIds(owner: PsiClass): List<PsiLiteralExpression>? = scan(owner) { element ->
        val literal = element as? PsiLiteralExpression ?: return@scan null
        if (id(literal) == null || WicketIdPsi.idCreation(literal) == null || markupOwner(literal) != owner) null else literal
    }

    private fun markupIds(pair: Companion): List<XmlAttributeValue>? {
        val tags = scan(pair.markup) { it as? XmlTag } ?: return null
        val sections = tags.filter { it.name == "wicket:panel" || it.name == "wicket:extend" }
        val section = sections.singleOrNull()
        if (sections.size > 1 || section == null && WicketComponentTypes.kind(pair.owner) == MarkupKind.PANEL) return emptyList()
        return tags.mapNotNull { tag ->
            ProgressManager.checkCanceled()
            if (tag.name in setOf("wicket:fragment", "wicket:panel", "wicket:extend", "wicket:child")) return@mapNotNull null
            val ancestors = generateSequence(tag) { it.parentTag }.toList()
            if (ancestors.any { it.name == "wicket:remove" } ||
                section != null && section !in ancestors && ancestors.none { it.name == "wicket:head" }) return@mapNotNull null
            tag.getAttribute("wicket:id")?.valueElement?.let(WicketIdPsi::htmlId)
        }
    }

    private fun <T : PsiElement> scan(root: PsiElement, extract: (PsiElement) -> T?): List<T>? {
        var visited = 0
        val result = mutableListOf<T>()
        val complete = PsiTreeUtil.processElements(root) { element ->
            ProgressManager.checkCanceled()
            if (++visited > MAX_ELEMENTS) return@processElements false
            extract(element)?.let(result::add)
            true
        }
        return result.takeIf { complete }
    }

    private fun id(value: PsiElement): String? = when (value) {
        is PsiLiteralExpression -> value.value as? String
        is XmlAttributeValue -> (value.parent as? XmlAttribute)?.displayValue
        else -> null
    }?.takeIf { it.isNotEmpty() && it.length <= 256 && ':' !in it && '~' !in it }

    private fun markupParent(tag: XmlTag): XmlTag? = generateSequence(tag.parentTag) { it.parentTag }.firstOrNull {
        it.getAttribute("wicket:id") != null || it.name in setOf("wicket:panel", "wicket:extend", "wicket:head")
    }

    private fun hasDuplicateMarkupParent(tag: XmlTag, values: List<XmlAttributeValue>): Boolean =
        generateSequence(markupParent(tag), ::markupParent).any { parent ->
            val id = parent.getAttribute("wicket:id")?.displayValue ?: return@any false
            values.count { id(it) == id && markupParent((it.parent as XmlAttribute).parent) == markupParent(parent) } > 1
        }

    /** Only direct adds to the same resolved receiver in the same block identify a source sibling. */
    private fun javaSiblingScope(value: PsiLiteralExpression): Pair<PsiCodeBlock, PsiElement>? {
        val creation = WicketIdPsi.idCreation(value) ?: return null
        val args = creation.parent as? PsiExpressionList ?: return null
        val call = args.parent as? PsiMethodCallExpression ?: return null
        if (call.methodExpression.referenceName != "add" || call.resolveMethod()?.containingClass?.qualifiedName != "org.apache.wicket.MarkupContainer") return null
        val block = (call.parent as? PsiExpressionStatement)?.parent as? PsiCodeBlock ?: return null
        val receiver = when (val qualifier = call.methodExpression.qualifierExpression) {
            null -> PsiTreeUtil.getParentOfType(call, PsiClass::class.java)
            is PsiReferenceExpression -> qualifier.resolve() as? PsiVariable
            is PsiThisExpression -> (qualifier.type as? PsiClassType)?.resolve()
            else -> null
        } ?: return null
        return block to receiver
    }

    private fun componentType(value: PsiElement): String? = (value as? PsiLiteralExpression)
        ?.let(WicketIdPsi::idCreation)?.classOrAnonymousClassReference?.referenceName

    fun presentation(value: PsiElement): Pair<String, String>? {
        if (WicketIdPsi.htmlId(value) == null && (value !is PsiLiteralExpression || WicketIdPsi.idCreation(value) == null)) return null
        val preview = when (value) {
            is XmlAttributeValue -> {
                val attribute = value.parent as XmlAttribute
                "<${attribute.parent.name} ${attribute.text}>"
            }
            is PsiLiteralExpression -> WicketIdPsi.idCreation(value)?.text?.lineSequence()?.firstOrNull()?.trimEnd() ?: return null
            else -> return null
        }
        return preview.take(140) to context(value)
    }

    private fun context(value: PsiElement): String {
        val scope = when (value) {
            is XmlAttributeValue -> generateSequence(markupParent((value.parent as XmlAttribute).parent), ::markupParent)
                .mapNotNull { it.getAttribute("wicket:id")?.displayValue }.take(6).toList().asReversed().joinToString(" / ")
            is PsiLiteralExpression -> javaContext(value)
            else -> ""
        }
        return listOf(value.containingFile.name, scope.take(100)).filter { it.isNotEmpty() }.joinToString(" · ")
    }

    private fun javaContext(value: PsiLiteralExpression): String {
        val creation = WicketIdPsi.idCreation(value) ?: return ""
        val configured = generateSequence<PsiExpression>(creation) { expression ->
            val reference = expression.parent as? PsiReferenceExpression
            if (reference?.qualifierExpression == expression) reference.parent as? PsiMethodCallExpression else null
        }.last()
        val call = (configured.parent as? PsiExpressionList)?.parent as? PsiMethodCallExpression
        val qualifier = call?.methodExpression?.qualifierExpression as? PsiReferenceExpression
        val variable = qualifier?.resolve() as? PsiVariable
        val initializer = variable?.initializer as? PsiNewExpression
        if (initializer?.resolveConstructor()?.containingClass?.qualifiedName == FRAGMENT) {
            val fragment = (initializer.argumentList?.expressions?.getOrNull(1) as? PsiLiteralExpression)?.value as? String
            if (fragment != null) return "fragment $fragment"
        }
        val method = PsiTreeUtil.getParentOfType(creation, PsiMethod::class.java)
        val enclosing = (method?.containingClass as? PsiAnonymousClass)?.parent as? PsiNewExpression
        val container = enclosing?.let { WicketComponentIdentity.trace(it)?.expression as? PsiLiteralExpression }?.value as? String
        return listOfNotNull(container, method?.name, qualifier?.referenceName).joinToString(" / ")
    }
}
