package dev.wicketlens.analysis

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.ProgressManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiErrorElement
import com.intellij.psi.PsiFile
import com.intellij.psi.XmlRecursiveElementVisitor
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.xml.XmlAttribute
import com.intellij.psi.xml.XmlEntityRef
import com.intellij.psi.xml.XmlFile
import com.intellij.psi.xml.XmlTag
import com.intellij.psi.xml.XmlTokenType
import com.intellij.psi.html.HtmlTag
import com.intellij.xml.util.XmlTagUtil
import dev.wicketlens.model.Confidence
import dev.wicketlens.model.Diagnostic
import dev.wicketlens.model.DiagnosticCode
import dev.wicketlens.model.MarkupElement
import dev.wicketlens.model.MarkupKind
import dev.wicketlens.model.MarkupReport
import dev.wicketlens.model.SourceLocation

/** Read-only HTML/XML PSI extraction. Call under a read action, off the UI thread. */
class WicketMarkupAnalyzer {
    fun analyze(file: PsiFile, kind: MarkupKind = MarkupKind.PAGE): MarkupReport = extract(file, kind, false)

    internal fun analyzeInheritanceSource(file: PsiFile, kind: MarkupKind, boundaries: Set<SourceLocation> = emptySet()): MarkupReport =
        extract(file, kind, true, boundaries)

    private fun extract(file: PsiFile, kind: MarkupKind, inheritance: Boolean, boundaries: Set<SourceLocation> = emptySet()): MarkupReport {
        ApplicationManager.getApplication().assertReadAccessAllowed()
        ProgressManager.checkCanceled()
        val path = file.virtualFile?.path ?: file.name
        fun location(element: PsiElement) = SourceLocation(path, element.textRange.startOffset)
        fun unresolved(message: String, element: PsiElement = file) = Diagnostic(
            DiagnosticCode.UNRESOLVED_MARKUP, message, location(element),
        )
        if (file !is XmlFile) {
            val issue = unresolved("The companion file does not provide HTML/XML PSI.")
            return MarkupReport(path, kind, emptyList(), false,
                listOf(issue), listOf(issue))
        }

        val tags = mutableListOf<XmlTag>()
        val documentIssues = mutableListOf<Diagnostic>()
        file.accept(object : XmlRecursiveElementVisitor() {
            override fun visitElement(element: PsiElement) {
                ProgressManager.checkCanceled()
                super.visitElement(element)
            }

            override fun visitXmlTag(tag: XmlTag) {
                ProgressManager.checkCanceled()
                tags += tag
                super.visitXmlTag(tag)
            }

            override fun visitErrorElement(element: PsiErrorElement) {
                documentIssues += unresolved("Markup syntax is incomplete: ${element.errorDescription}", element)
                super.visitErrorElement(element)
            }
        })
        if (tags.isEmpty()) documentIssues += unresolved("No markup elements could be extracted.")

        val customPrefixes = linkedSetOf<String>()
        tags.forEach { tag ->
            ProgressManager.checkCanceled()
            val empty = tag.node.findChildByType(XmlTokenType.XML_EMPTY_ELEMENT_END) != null
            val openingClosed = empty || tag.node.findChildByType(XmlTokenType.XML_TAG_END) != null
            val closing = XmlTagUtil.getEndTagNameElement(tag)
            val optionalEnd = tag is HtmlTag && tag.name.lowercase() in HTML_OPTIONAL_OR_VOID
            if (!openingClosed || (!empty && closing == null && !optionalEnd) ||
                (closing != null && !closing.text.equals(tag.name, ignoreCase = tag is HtmlTag))) {
                documentIssues += unresolved("Markup tag boundaries are incomplete or recovered by PSI.", tag)
            }
            tag.attributes.filter { it.name.startsWith("xmlns:") }.forEach { attribute ->
                val prefix = attribute.localName
                val value = attribute.value
                if (prefix != "wicket" && (value.isNullOrBlank() || isWicketUri(value))) {
                    customPrefixes += prefix
                    documentIssues += unresolved("Custom Wicket namespace prefixes are not reconstructed.", attribute)
                }
            }
            if (!inheritance && tag.name in setOf("wicket:extend", "wicket:child")) {
                documentIssues += unresolved("Markup inheritance is not reconstructed.", tag)
            }
        }

        val panels = tags.filter { it.name == "wicket:panel" }
        val extensions = tags.filter { it.name == "wicket:extend" }
        val extension = if (inheritance) extensions.firstOrNull() else null
        if (inheritance && extensions.size > 1) {
            documentIssues += unresolved("Markup inheritance requires exactly one wicket:extend section.")
        }
        val panel = if (kind == MarkupKind.PANEL && panels.size == 1) panels.single() else null
        if (kind == MarkupKind.PANEL && panel == null && extension == null) {
            documentIssues += unresolved("Panel markup requires exactly one wicket:panel section.")
        }

        val headers = tags.filter { it.name == "wicket:head" }
        val htmlHeads = tags.filter { it.name.equals("head", ignoreCase = true) }
        if (headers.size > 1 || htmlHeads.size > 1) documentIssues += unresolved("Multiple header sections in one markup resource are not reconstructed.")
        val activeHeaders = headers.filter { header ->
            val ancestors = generateSequence(header.parentTag) { it.parentTag }
            val misplaced = ancestors.any { it.name in setOf("wicket:panel", "wicket:extend", "wicket:head") || it.getAttribute("wicket:id") != null } ||
                extension != null && header.textOffset > extension.textOffset
            if (misplaced) documentIssues += unresolved("wicket:head must precede and remain outside the panel/extension component region.", header)
            !misplaced
        }.toSet()
        htmlHeads.filter { kind == MarkupKind.PAGE && extension == null }.forEach { head ->
            if (head.getAttribute("wicket:id") != null || generateSequence(head.parentTag) { it.parentTag }.any { it.getAttribute("wicket:id") != null } ||
                tags.firstOrNull { it.name.equals("body", true) }?.let { it.textOffset < head.textOffset } == true) {
                documentIssues += unresolved("A component-owned or misplaced HTML head is not reconstructed.", head)
            }
        }

        val described = linkedMapOf<XmlTag, MarkupElement>()
        val preview = mutableSetOf<String>()
        tags.forEach { tag ->
            ProgressManager.checkCanceled()
            val parent = described[PsiTreeUtil.getParentOfType(tag, XmlTag::class.java)]
            val replaced = parent != null && (parent.location in boundaries || parent.nodeId in preview)
            val active = !replaced && (tag in activeHeaders || if (extension != null) tag == extension || parent?.participatesInComponentTree == true
                else kind == MarkupKind.PAGE || tag == panel || parent?.participatesInComponentTree == true)
            if (replaced) preview += "markup:${tag.textRange.startOffset}"
            val issues = mutableListOf<Diagnostic>()
            val idAttributes = tag.attributes.filter { attribute ->
                attribute.localName.equals("id", ignoreCase = true) && isWicketAttribute(attribute, customPrefixes)
            }
            val idAttribute = idAttributes.singleOrNull()
            val entities = idAttribute?.valueElement?.let {
                PsiTreeUtil.findChildrenOfType(it, XmlEntityRef::class.java)
            }.orEmpty()
            val unknownEntity = entities.any {
                it.text !in BASIC_ENTITIES && !(it.text.startsWith("&#") && it.text.endsWith(";"))
            }
            val value = if (unknownEntity) idAttribute?.value else idAttribute?.displayValue
            if (idAttributes.size > 1) {
                issues += unresolved("The owning element has multiple Wicket ID attributes.", tag)
            } else if (idAttribute != null && value.isNullOrEmpty()) {
                issues += unresolved("The Wicket ID attribute has no nonempty literal value.", idAttribute)
            }
            if (unknownEntity) issues += unresolved("Custom entities in Wicket IDs are not resolved.", idAttribute!!)
            if (value?.any { it == ':' || it == '~' } == true) {
                issues += unresolved("The Wicket ID contains reserved component-path characters.", idAttribute!!)
            }
            if (idAttribute != null && idAttribute.name != "wicket:id") {
                issues += unresolved("Only the exact wicket:id attribute convention is confirmed.", idAttribute)
            }
            tag.attributes.filter { !it.isNamespaceDeclaration && isWicketAttribute(it, customPrefixes) && it !in idAttributes }
                .forEach { issues += unresolved("Wicket attributes that may create or alter component scope are not reconstructed.", it) }

            val wicketTag = tag.namespacePrefix.equals("wicket", ignoreCase = true) ||
                tag.namespacePrefix in customPrefixes || (tag.namespacePrefix.isNotEmpty() && isWicketUri(tag.namespace))
            val structural = (inheritance && tag.name in setOf("wicket:extend", "wicket:child") || tag in activeHeaders) && tag.attributes.isEmpty()
            if (inheritance && tag.name == "wicket:child" && tag.value.text.isNotBlank()) {
                issues += unresolved("Only an empty wicket:child slot is supported.", tag)
            }
            if (wicketTag && !structural && (tag != panel || idAttributes.isNotEmpty())) {
                issues += unresolved("This Wicket tag's component scope is not reconstructed.", tag)
            }
            if (tag.name.equals("head", ignoreCase = true) && kind == MarkupKind.PANEL && active) {
                issues += unresolved("An HTML head inside a Panel component region is not reconstructed.", tag)
            }
            described[tag] = MarkupElement(
                nodeId = "markup:${tag.textRange.startOffset}",
                tagName = tag.name,
                parentElementId = parent?.nodeId,
                hasWicketId = idAttributes.isNotEmpty(),
                wicketId = value?.takeIf { it.isNotEmpty() },
                parentWicketElementId = if (!active || tag == panel || parent?.participatesInComponentTree != true) null
                else if (parent.hasWicketId) parent.nodeId else parent.parentWicketElementId,
                location = location(tag),
                wicketIdLocation = idAttributes.firstOrNull()?.let(::location),
                participatesInComponentTree = active,
                confidence = Confidence.CONFIRMED,
                diagnostics = if (replaced) emptyList() else issues.toList(),
            )
        }

        val elements = described.values.associateBy { it.nodeId }.toMutableMap()
        elements.values.filter { it.participatesInComponentTree && it.wicketId != null }
            .groupBy { it.parentWicketElementId to it.wicketId }.values.filter { it.size > 1 }.forEach { duplicates ->
                duplicates.forEach { element ->
                    ProgressManager.checkCanceled()
                    elements[element.nodeId] = element.copy(diagnostics = element.diagnostics + Diagnostic(
                        DiagnosticCode.UNRESOLVED_MARKUP,
                        "Repeated markup IDs under the same Wicket parent cannot be matched uniquely.",
                        element.wicketIdLocation,
                    ))
                }
            }

        // Preorder ensures parent uncertainty is propagated through ordinary HTML wrappers too.
        elements.keys.toList().forEach { id ->
            ProgressManager.checkCanceled()
            val element = elements.getValue(id)
            val parent = elements[element.parentElementId]
            val uncertainContext = element.participatesInComponentTree && (documentIssues.isNotEmpty() ||
                (parent?.participatesInComponentTree == true && parent.confidence == Confidence.UNRESOLVED))
            val issues = element.diagnostics.toMutableList()
            if (uncertainContext && element.hasWicketId) {
                issues += Diagnostic(DiagnosticCode.UNRESOLVED_PARENT,
                    "The surrounding markup context is unresolved.", element.wicketIdLocation)
            }
            elements[id] = element.copy(
                confidence = if (issues.isNotEmpty() || uncertainContext) Confidence.UNRESOLVED else Confidence.CONFIRMED,
                diagnostics = issues.toList(),
            )
        }
        val diagnostics = (documentIssues + elements.values.flatMap { it.diagnostics }).distinct()
            .sortedWith(compareBy({ it.location?.offset ?: 0 }, { it.code.name }, { it.message }))
        return MarkupReport(path, kind, elements.values.toList(), diagnostics.isEmpty(), diagnostics,
            documentIssues.distinct().sortedBy { it.location?.offset ?: 0 })
    }

    private fun isWicketAttribute(attribute: XmlAttribute, customPrefixes: Set<String>): Boolean =
        !attribute.isNamespaceDeclaration && (attribute.namespacePrefix.equals("wicket", ignoreCase = true) ||
            attribute.namespacePrefix in customPrefixes ||
            (attribute.namespacePrefix.isNotEmpty() && isWicketUri(attribute.namespace)))

    private fun isWicketUri(uri: String): Boolean = uri.startsWith("http://wicket.apache.org")

    private companion object {
        val BASIC_ENTITIES = setOf("&amp;", "&lt;", "&gt;", "&quot;", "&apos;")
        val HTML_OPTIONAL_OR_VOID = setOf(
            "area", "base", "br", "col", "embed", "hr", "img", "input", "link", "meta", "param", "source", "track", "wbr",
            "html", "head", "body", "p", "li", "dt", "dd", "rt", "rp", "optgroup", "option", "colgroup", "thead",
            "tbody", "tfoot", "tr", "td", "th",
        )
    }
}
