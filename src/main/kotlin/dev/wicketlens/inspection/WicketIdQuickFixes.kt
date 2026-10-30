package dev.wicketlens.inspection

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.modcommand.ModCommand
import com.intellij.modcommand.ModCommandQuickFix
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.*
import com.intellij.psi.codeStyle.JavaCodeStyleManager
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.ClassInheritorsSearch
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.xml.XmlAttribute
import com.intellij.psi.xml.XmlAttributeValue
import com.intellij.psi.xml.XmlFile
import com.intellij.psi.xml.XmlTag
import dev.wicketlens.editor.EditorPage
import dev.wicketlens.editor.WicketEditorAnalysis
import dev.wicketlens.editor.WicketIdPsi
import dev.wicketlens.editor.WicketIdRenameTarget
import dev.wicketlens.model.*

/** Fix instances retain values only. Every preview/application replans against committed PSI. */
internal object WicketIdQuickFixes {
    enum class Kind { CHANGE_ID, CREATE_HTML, CREATE_JAVA }
    data class Choice(val owner: String, val id: String, val direction: DiagnosticCode, val kind: Kind,
        val replacement: String = "", val componentType: String = "") {
        val text: String get() = when (kind) {
            Kind.CHANGE_ID -> "Change Wicket ID to '$replacement'"
            Kind.CREATE_HTML -> "Create HTML counterpart for '$id'"
            Kind.CREATE_JAVA -> "Create Java ${componentType.substringAfterLast('.')} for '$id'"
        }
    }
    data class Offer(val choice: Choice, val command: () -> ModCommand)
    private data class Context(val page: EditorPage, val owner: PsiClass, val java: PsiJavaFile, val markup: XmlFile)

    fun forProblem(file: PsiFile, problem: WicketIdProblem): Array<LocalQuickFix> {
        val value = WicketIdPsi.valueAt(file, problem.start) ?: return emptyArray()
        return WicketEditorAnalysis.pages(file).filter { it.sourceUrl == problem.sourceUrl && it.classOffset == problem.classOffset }
            .flatMap { offers(value, it) }.map { WicketIdQuickFix(it.choice) }.toTypedArray()
    }

    fun current(value: PsiElement, choice: Choice): Offer? = WicketEditorAnalysis.pages(value.containingFile)
        .filter { it.report.qualifiedClassName == choice.owner }.flatMap { offers(value, it) }.singleOrNull { it.choice == choice }

    private fun context(project: Project, page: EditorPage): Context? {
        val report = page.report
        if (report.markup?.scopeComplete != true || report.markup.sourceFiles.size != 1 ||
            report.warnings.any { it.code == DiagnosticCode.UNRESOLVED_SCOPE } ||
            report.matching?.findings.orEmpty().any { it.causes.isNotEmpty() || it.code !in PAIR_CODES }) return null
        val root = report.nodes.singleOrNull { it.nodeId == report.rootNodeId } ?: return null
        val java = WicketIdPsi.source(project, root.javaLocation.file) as? PsiJavaFile ?: return null
        val owner = java.classes.singleOrNull { it.qualifiedName == report.qualifiedClassName } ?: return null
        val markup = WicketIdPsi.source(java.project, report.markup.file) as? XmlFile ?: return null
        // Editing a shared base implementation/template needs a wider refactoring than a local fix.
        if (markup.virtualFile.name != "${owner.name}${report.markupSuffix}.html" || owner.initializers.isNotEmpty() ||
            ClassInheritorsSearch.search(owner, GlobalSearchScope.projectScope(owner.project), true).findFirst() != null) return null
        return Context(page, owner, java, markup)
    }

    private fun offers(value: PsiElement, page: EditorPage): List<Offer> {
        ProgressManager.checkCanceled()
        val context = context(value.project, page) ?: return emptyList()
        val report = page.report
        val findings = report.matching!!.findings
        val java = findings.filter { it.code == DiagnosticCode.JAVA_ONLY }.singleOrNull { finding ->
            report.nodes.singleOrNull { it.nodeId == finding.javaNodeId }?.let { WicketIdPsi.javaLiteral(value.project, it) } == value
        }
        val html = findings.filter { it.code == DiagnosticCode.MARKUP_ONLY }.singleOrNull { finding ->
            report.markup!!.elements.singleOrNull { it.nodeId == finding.markupNodeId }?.let { WicketIdPsi.markupValue(value.project, it) } == value
        }
        val finding = java ?: html ?: return emptyList()
        val node = java?.let { f -> report.nodes.single { it.nodeId == f.javaNodeId } }
        val element = html?.let { f -> report.markup!!.elements.single { it.nodeId == f.markupNodeId } }
        val id = node?.wicketId ?: element?.wicketId ?: return emptyList()
        if (!WicketIdRenameTarget.validName(id)) return emptyList()
        val parent = if (node != null) WicketIdPsi.javaPath(report, node.parentNodeId)
            else WicketIdPsi.markupPath(report, element!!.parentWicketElementId)
        if (parent == null) return emptyList()
        fun choice(kind: Kind, replacement: String = "", type: String = "") =
            Choice(report.qualifiedClassName, id, finding.code, kind, replacement, type)
        val result = mutableListOf<Offer>()
        val sharedJava = report.markupSuffix.isNotEmpty() || dev.wicketlens.analysis.WicketPageResolver().resolve(context.owner).relatedVariantPaths.isNotEmpty()
        val editableId = value.isWritable && (node == null || !sharedJava && node.idRenameSafe &&
            report.nodes.count { it.idLocation == node.idLocation } == 1)
        if (editableId) {
            val alternatives = findings.filter { it.code == if (node != null) DiagnosticCode.MARKUP_ONLY else DiagnosticCode.JAVA_ONLY }
                .mapNotNull { opposite ->
                    if (node != null) report.markup!!.elements.single { it.nodeId == opposite.markupNodeId }
                        .takeIf { WicketIdPsi.markupPath(report, it.parentWicketElementId) == parent &&
                            compatible(node.componentType, it, context) }?.wicketId
                    else report.nodes.single { it.nodeId == opposite.javaNodeId }
                        .takeIf { WicketIdPsi.javaPath(report, it.parentNodeId) == parent &&
                            compatible(it.componentType, element!!, context) }?.wicketId
                }.filter(WicketIdRenameTarget::validName).distinct().sorted()
            if (alternatives.size <= 8) alternatives.forEach { replacement ->
                // The opposite finding proves this path is unpaired; the edited side must also be collision-free.
                val collision = if (node != null) report.nodes.any { it.nodeId != node.nodeId &&
                    (WicketIdPsi.javaPath(report, it.parentNodeId) == parent ||
                        node.queuedInNodeId != null && node.queuedInNodeId == it.queuedInNodeId) && it.wicketId == replacement }
                else report.markup!!.elements.any { it.participatesInComponentTree && it.nodeId != element!!.nodeId &&
                    WicketIdPsi.markupPath(report, it.parentWicketElementId) == parent && it.wicketId == replacement }
                if (!collision) result += Offer(choice(Kind.CHANGE_ID, replacement)) {
                    ModCommand.psiUpdate(value) { copy ->
                        if (copy is XmlAttributeValue) (copy.parent as XmlAttribute).setValue(replacement)
                        else ElementManipulators.handleContentChange(copy, replacement)
                    }
                }
            }
        }
        if (node != null) {
            val tagText = htmlFor(node.componentType, id) ?: return result
            val target = markupParent(context, parent) ?: return result
            if (node.componentType == FORM && target.name == "span") return result
            if (target.isWritable) result += Offer(choice(Kind.CREATE_HTML, type = node.componentType)) {
                ModCommand.psiUpdate(target) { copy ->
                    copy.addSubTag(XmlElementFactory.getInstance(copy.project).createHTMLTagFromText(tagText), false)
                }
            }
        } else {
            if (report.nodes.filter { it.repetition != null }.any { view ->
                    WicketIdPsi.javaPath(report, view.nodeId)?.let { parent.take(it.size) == it } == true
                }) return result
            if (sharedJava) return result
            val tag = PsiTreeUtil.getParentOfType(value, XmlTag::class.java) ?: return result
            val type = javaType(tag) ?: return result
            val target = javaParent(context, parent) ?: return result
            val expression = javaExpression(type, id, tag)
            val prototype = JavaPsiFacade.getElementFactory(value.project).createStatementFromText("${target.receiver}.add($expression);", target.body ?: context.owner)
            val call = (prototype as PsiExpressionStatement).expression as PsiMethodCallExpression
            if (call.resolveMethod()?.containingClass?.qualifiedName != "org.apache.wicket.MarkupContainer" ||
                !call.resolveMethodGenerics().isValidResult) return result
            result += Offer(choice(Kind.CREATE_JAVA, type = type)) { target.command(expression, type) }
        }
        return result
    }

    private fun markupParent(context: Context, path: List<String>): XmlTag? {
        val report = context.page.report
        val tag = if (path.isNotEmpty()) {
            val element = report.markup!!.elements.singleOrNull { WicketIdPsi.markupPath(report, it.nodeId) == path &&
                report.matching!!.findings.any { f -> f.code == DiagnosticCode.MATCHED && f.markupNodeId == it.nodeId } } ?: return null
            WicketIdPsi.markupValue(context.java.project, element)?.parent?.parent as? XmlTag
        } else {
            val tags = PsiTreeUtil.findChildrenOfType(context.markup, XmlTag::class.java)
            if (report.markup!!.kind == MarkupKind.PANEL) tags.singleOrNull { it.name == "wicket:panel" }
            else tags.singleOrNull { it.name == "body" } ?: context.markup.rootTag
        } ?: return null
        if (tag.name !in INSERTION_TAGS || path.isEmpty() && generateSequence(tag) { it.parentTag }.any { it.getAttribute("wicket:id") != null }) return null
        return tag
    }

    private data class JavaTarget(val owner: PsiClass, val body: PsiCodeBlock?, val receiver: String) {
        fun command(expression: String, type: String): ModCommand {
            val target: PsiElement = body ?: owner
            return ModCommand.psiUpdate(target) { copy ->
                val factory = JavaPsiFacade.getElementFactory(copy.project)
                val block = copy as? PsiCodeBlock ?: (copy as PsiClass).add(factory.createMethodFromText(
                    "public ${owner.name}() {}", copy)).let { (it as PsiMethod).body!! }
                val style = JavaCodeStyleManager.getInstance(copy.project)
                val container = type == FORM || type == CONTAINER
                val argument = if (container) {
                    val name = style.suggestUniqueVariableName(if (type == FORM) "wicketForm" else "wicketContainer", block, true)
                    val declaration = factory.createStatementFromText("final $type${if (type == FORM) "<java.lang.Void>" else ""} $name = $expression;", block)
                    style.shortenClassReferences(block.addBefore(declaration, block.rBrace))
                    name
                } else expression
                val statement = factory.createStatementFromText("$receiver.add($argument);", block)
                style.shortenClassReferences(block.addBefore(statement, block.rBrace))
            }
        }
    }

    private fun javaParent(context: Context, path: List<String>): JavaTarget? {
        if (!context.java.isWritable || context.owner.constructors.size > 1) return null
        val constructor = context.owner.constructors.singleOrNull()
        if (constructor != null && !straightLine(constructor.body)) return null
        if (path.isEmpty()) {
            if (constructor == null && context.owner.superClass?.qualifiedName != "org.apache.wicket.markup.html.WebPage") return null
            return JavaTarget(context.owner, constructor?.body, "this")
        }
        val report = context.page.report
        val node = report.nodes.singleOrNull { WicketIdPsi.javaPath(report, it.nodeId) == path } ?: return null
        if (node.javaLocation.file != context.java.virtualFile.path || report.nodes.count { it.javaLocation == node.javaLocation } != 1 ||
            report.matching!!.findings.none { it.code == DiagnosticCode.MATCHED && it.javaNodeId == node.nodeId }) return null
        val creation = PsiTreeUtil.getParentOfType(context.java.findElementAt(node.javaLocation.offset), PsiNewExpression::class.java, false) ?: return null
        val variable = creation.parent
        if (variable is PsiLocalVariable && variable.initializer == creation) {
            val method = PsiTreeUtil.getParentOfType(variable, PsiMethod::class.java) ?: return null
            if (method.containingClass != context.owner || !method.isConstructor && method.name != "onInitialize" ||
                variable.parent.parent != method.body || !straightLine(method.body)) return null
            return JavaTarget(context.owner, method.body, variable.name)
        }
        val field = if (variable is PsiField) variable else (variable as? PsiAssignmentExpression)?.let {
            (it.lExpression as? PsiReferenceExpression)?.resolve() as? PsiField
        }
        if (field?.containingClass != context.owner || !field.hasModifierProperty(PsiModifier.PRIVATE) ||
            !field.hasModifierProperty(PsiModifier.FINAL) || field.hasModifierProperty(PsiModifier.STATIC) || constructor?.body == null) return null
        return JavaTarget(context.owner, constructor.body, "this.${field.name}")
    }

    private fun htmlFor(type: String, id: String): String? {
        val escaped = StringUtil.escapeXmlEntities(id)
        return when (type) {
            LABEL -> "<span wicket:id=\"$escaped\"></span>"
            FORM -> "<form wicket:id=\"$escaped\"></form>"
            TEXT -> "<input wicket:id=\"$escaped\" type=\"text\"/>"
            CHECK -> "<input wicket:id=\"$escaped\" type=\"checkbox\"/>"
            BUTTON -> "<button wicket:id=\"$escaped\" type=\"submit\"></button>"
            CONTAINER -> "<div wicket:id=\"$escaped\"></div>"
            else -> null
        }
    }

    private fun compatible(type: String, element: MarkupElement, context: Context): Boolean {
        val tag = WicketIdPsi.markupValue(context.java.project, element)?.parent?.parent as? XmlTag ?: return false
        return when (type) {
            LABEL -> tag.name in setOf("title", "span", "div", "p", "label", "h1", "h2", "h3", "h4", "h5", "h6") &&
                context.page.report.markup!!.elements.none { it.participatesInComponentTree && it.parentWicketElementId == element.nodeId }
            CONTAINER -> tag.name in setOf("div", "span", "section", "fieldset", "main", "article", "aside", "header", "footer")
            FORM, TEXT, CHECK -> javaType(tag) == type
            BUTTON -> javaType(tag) == BUTTON || tag.name == "input" && tag.getAttributeValue("type") == "submit"
            else -> false
        }
    }

    private fun javaType(tag: XmlTag): String? = when (tag.name.lowercase()) {
        "title", "span", "p", "label", "h1", "h2", "h3", "h4", "h5", "h6" -> if (tag.subTags.isEmpty() && PsiTreeUtil.findChildrenOfType(tag, com.intellij.psi.xml.XmlEntityRef::class.java)
                .all { it.text in setOf("&amp;", "&lt;", "&gt;", "&quot;", "&apos;") || it.text.startsWith("&#") }) LABEL else CONTAINER
        "div", "section", "fieldset" -> CONTAINER
        "form" -> FORM
        "input" -> when (tag.getAttributeValue("type")?.lowercase().orEmpty()) { "", "text" -> TEXT; "checkbox" -> CHECK; else -> null }
        "button" -> if (tag.getAttributeValue("type") in setOf(null, "submit")) BUTTON else null
        else -> null
    }

    private fun javaExpression(type: String, id: String, tag: XmlTag): String {
        fun literal(value: String) = "\"${StringUtil.escapeStringCharacters(value)}\""
        val suffix = if (type == FORM) "<java.lang.Void>" else if (type == TEXT) "<java.lang.String>" else ""
        val model = if (type == LABEL) ", ${literal(tag.value.textElements.joinToString("") { it.value })}" else ""
        return "new $type$suffix(${literal(id)}$model)"
    }

    private fun straightLine(body: PsiCodeBlock?) = body != null && body.statements.all {
        it is PsiDeclarationStatement || it is PsiExpressionStatement || it is PsiEmptyStatement
    }

    private val PAIR_CODES = setOf(DiagnosticCode.MATCHED, DiagnosticCode.JAVA_ONLY, DiagnosticCode.MARKUP_ONLY)
    private val INSERTION_TAGS = setOf("body", "div", "span", "section", "main", "form", "fieldset", "wicket:panel", "article", "aside", "header", "footer", "td", "li")
    private const val LABEL = "org.apache.wicket.markup.html.basic.Label"
    private const val FORM = "org.apache.wicket.markup.html.form.Form"
    private const val TEXT = "org.apache.wicket.markup.html.form.TextField"
    private const val CHECK = "org.apache.wicket.markup.html.form.CheckBox"
    private const val BUTTON = "org.apache.wicket.markup.html.form.Button"
    private const val CONTAINER = "org.apache.wicket.markup.html.WebMarkupContainer"
}

private class WicketIdQuickFix(private val choice: WicketIdQuickFixes.Choice) : ModCommandQuickFix() {
    override fun getName() = choice.text
    override fun getFamilyName() = when (choice.kind) {
        WicketIdQuickFixes.Kind.CHANGE_ID -> "Correct unpaired Wicket ID"
        WicketIdQuickFixes.Kind.CREATE_HTML -> "Create Wicket HTML counterpart"
        WicketIdQuickFixes.Kind.CREATE_JAVA -> "Create Wicket Java counterpart"
    }
    override fun perform(project: Project, descriptor: ProblemDescriptor): ModCommand {
        val anchor = descriptor.psiElement ?: return ModCommand.nop()
        val value = WicketIdPsi.valueAt(anchor.containingFile, anchor.textOffset) ?: return ModCommand.nop()
        return WicketIdQuickFixes.current(value, choice)?.command?.invoke() ?: ModCommand.nop()
    }
}
