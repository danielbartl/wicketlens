package dev.wicketlens.analysis

import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.psi.*
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.ClassInheritorsSearch
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.util.PsiTreeUtil
import dev.wicketlens.model.*
import org.jetbrains.jps.model.java.JavaSourceRootType

/** Immutable graph snapshots, shared by native navigation and the optional MCP projection. */
data class NavigationContext(
    val report: PageReport,
    val moduleName: String,
    val sourceSet: String,
    val relationship: String,
    val scopeNodeIds: List<String>,
    val bindingNodeIds: List<String>,
)

data class NavigationContexts(
    val contexts: List<NavigationContext>,
    val complete: Boolean,
    val diagnostics: List<String>,
    val sourceFiles: List<String>,
    val lifecycleDiagnostics: List<Diagnostic> = emptyList(),
)

/** Indexed candidates are hints only; every binding still comes from WicketPageAnalyzer. */
internal class WicketNavigationContextsAnalyzer(private val limits: Limits = Limits()) {
    data class Limits(val references: Int = 256, val inheritors: Int = 256, val candidates: Int = 16,
        val bindings: Int = 256, val sources: Int = 512)

    fun analyze(type: PsiClass, report: PageReport) = analyze(listOf(type to report))

    fun analyze(seeds: List<Pair<PsiClass, PageReport>>): NavigationContexts {
        if (seeds.isEmpty()) return NavigationContexts(emptyList(), true, emptyList(), emptyList())
        val project = seeds.first().first.project
        val index = ProjectFileIndex.getInstance(project)
        val diagnostics = linkedSetOf<String>()
        val sources = linkedSetOf<String>()
        val candidates = linkedSetOf<PsiClass>()
        val reports = linkedMapOf<PsiClass, PageReport>()
        val own = seeds.map { it.first }.toSet()
        val sourceSets = seeds.map { index.isInTestSourceContent(it.first.containingFile.virtualFile) }.toSet()
        val relevantMarkup = seeds.flatMap { (_, report) -> report.markup?.sourceFiles.orEmpty() }.toSet()
        var referenceCount = 0
        var inheritorCount = 0
        var bindingCount = 0

        fun source(type: PsiClass): Boolean {
            ProgressManager.checkCanceled()
            val file = type.containingFile as? PsiJavaFile ?: return false
            val vf = file.virtualFile ?: return false
            val module = index.getModuleForFile(vf) ?: return false
            val root = index.getSourceRootForFile(vf)
            return file.isPhysical && type !is PsiCompiledElement && type.qualifiedName != null && index.isInContent(vf) &&
                index.isInTestSourceContent(vf) in sourceSets &&
                (root in ModuleRootManager.getInstance(module).getSourceRoots(JavaSourceRootType.SOURCE) ||
                    root in ModuleRootManager.getInstance(module).getSourceRoots(JavaSourceRootType.TEST_SOURCE))
        }

        fun candidate(type: PsiClass, seed: PsiClass) {
            if (!source(type) || !WicketComponentTypes.ownsMarkup(type) || type in own) return
            val visible = JavaPsiFacade.getInstance(project).findClasses(seed.qualifiedName!!, type.resolveScope)
            if (visible.size != 1 || visible.single().containingFile.virtualFile != seed.containingFile.virtualFile ||
                visible.single().textOffset != seed.textOffset) return
            if (candidates.size >= limits.candidates && type !in candidates) {
                diagnostics += "Caller discovery exceeds the ${limits.candidates}-candidate bound."
            } else candidates += type
        }

        seeds.forEach { (type, report) ->
            ProgressManager.checkCanceled()
            reports[type] = report
            sources += report.javaSourceFiles
            sources += report.markup?.sourceFiles.orEmpty()
            if (report.markupSuffix.isNotEmpty()) {
                diagnostics += "Caller discovery uses default markup; the selected variant retains its own context only."
                return@forEach
            }
            val scope = GlobalSearchScope.projectScope(project)
            ReferencesSearch.search(type, scope).forEach { reference ->
                ProgressManager.checkCanceled()
                if (++referenceCount > limits.references) {
                    diagnostics += "Caller discovery exceeds the ${limits.references}-reference bound."
                    false
                } else {
                    var owner = PsiTreeUtil.getParentOfType(reference.element, PsiClass::class.java, false)
                    var depth = 0
                    while (owner != null && depth++ < WicketSourceClassNames.MAX_DEPTH) {
                        ProgressManager.checkCanceled()
                        if (owner.qualifiedName != null && WicketComponentTypes.ownsMarkup(owner)) candidate(owner, type)
                        owner = PsiTreeUtil.getParentOfType(owner, PsiClass::class.java, true)
                    }
                    if (owner != null) diagnostics += "Caller ownership exceeds the 16-level named-class bound."
                    true
                }
            }
            ClassInheritorsSearch.search(type, scope, true).forEach { inheritor ->
                ProgressManager.checkCanceled()
                if (++inheritorCount > limits.inheritors) {
                    diagnostics += "Caller discovery exceeds the ${limits.inheritors}-inheritor bound."
                    false
                } else { candidate(inheritor, type); true }
            }
        }

        // A dialog resource can be one source hop below the configurable host.
        // This is a single bounded indexed hop, not transitive helper enumeration.
        candidates.toList().filter { WicketReplacementContexts.hasRegisteredFactory(it) || WicketTreeContexts.isSourceTree(it) || WicketFormContexts.hasRegisteredFactory(it) }.forEach { host ->
            sources += host.containingFile.virtualFile.path
            ReferencesSearch.search(host, GlobalSearchScope.projectScope(project)).forEach(com.intellij.util.Processor { reference ->
                ProgressManager.checkCanceled()
                if (++referenceCount > limits.references) {
                    diagnostics += "Replacement registration discovery exceeds the ${limits.references}-reference bound."
                    false
                } else {
                    var owner = PsiTreeUtil.getParentOfType(reference.element, PsiClass::class.java, false)
                    var depth = 0
                    while (owner != null && depth++ < WicketSourceClassNames.MAX_DEPTH) {
                        if (WicketComponentTypes.ownsMarkup(owner)) candidate(owner, host)
                        owner = PsiTreeUtil.getParentOfType(owner, PsiClass::class.java, true)
                    }
                    if (owner != null) diagnostics += "Replacement registration ownership exceeds the named-class depth bound."
                    true
                }
            })
        }

        candidates.sortedWith(compareBy({ it.containingFile.virtualFile.path }, { it.textOffset })).forEach { type ->
            ProgressManager.checkCanceled()
            if (sources.size > limits.sources) diagnostics += "Caller discovery exceeds the ${limits.sources}-source bound."
            else WicketPageAnalyzer().analyze(type)?.let { report ->
                reports[type] = report
                sources += report.javaSourceFiles
                sources += report.markup?.sourceFiles.orEmpty()
            }
        }
        if (sources.size > limits.sources) diagnostics += "Caller discovery exceeds the ${limits.sources}-source bound."

        val contexts = reports.mapNotNull { (type, report) ->
            ProgressManager.checkCanceled()
            val module = index.getModuleForFile(type.containingFile.virtualFile) ?: return@mapNotNull null
            val scopes = if (type in own || seeds.any { type.isInheritor(it.first, true) }) listOf(report.rootNodeId)
                else report.nodes.filter { node -> seeds.any { it.first.qualifiedName == node.componentType } }.map { it.nodeId }
            val nodes = report.nodes.associateBy { it.nodeId }
            val bindings = report.matching?.findings.orEmpty().filter { finding ->
                ProgressManager.checkCanceled()
                finding.code == DiagnosticCode.MATCHED && (finding.markupLocation?.file in relevantMarkup ||
                    nodes[finding.javaNodeId]?.let { node -> seeds.any { (seed, _) ->
                        (node.idLocations() + node.javaLocation).any { origin -> origin.file == seed.containingFile.virtualFile.path &&
                            seed.textRange.containsOffset(origin.offset) }
                    } } == true)
            }.mapNotNull { it.javaNodeId }
            val fragmentReference = report.fragmentUses.any { it.definitionLocation?.file in relevantMarkup }
            val related = scopes.isNotEmpty() || bindings.isNotEmpty() || fragmentReference
            val remaining = (limits.bindings - bindingCount).coerceAtLeast(0)
            if (bindings.size > remaining) diagnostics += "Navigation contexts exceed the ${limits.bindings}-binding bound."
            val selected = bindings.take(remaining)
            bindingCount += selected.size
            NavigationContext(report, module.name, if (index.isInTestSourceContent(type.containingFile.virtualFile)) "test" else "main",
                if (type in own) "OWN" else if (!related) "CANDIDATE" else if (report.rootNodeId in scopes) "INHERITED" else "CALLER", scopes, selected)
        }
        val events = mutableListOf<NavigationContext>()
        val lifecycleDiagnostics = mutableListOf<Diagnostic>()
        var lifecycleComplete = true
        reports.forEach { (type, report) ->
            ProgressManager.checkCanceled()
            if (sources.size > limits.sources) { lifecycleComplete = false; return@forEach }
            lifecycleComplete = lifecycleComplete && report.populationDiscoveryComplete
            val populationModule = index.getModuleForFile(type.containingFile.virtualFile)
            if(populationModule != null) report.populationContexts.forEach { template ->
                if(seeds.any { it.first.qualifiedName == template.qualifiedClassName } || template.markup?.sourceFiles.orEmpty().any { it in relevantMarkup }) {
                    val selected = template.matching!!.findings.filter { it.code == DiagnosticCode.MATCHED && it.javaNodeId in template.populationTemplate!!.ownedNodeIds }.mapNotNull { it.javaNodeId }
                    val remaining = (limits.bindings-bindingCount).coerceAtLeast(0)
                    if(selected.size>remaining) { lifecycleComplete=false;diagnostics += "Population template bindings exceed the ${limits.bindings}-binding bound." }
                    bindingCount += selected.size.coerceAtMost(remaining)
                    events += NavigationContext(template,populationModule.name,if(index.isInTestSourceContent(type.containingFile.virtualFile)) "test" else "main", "REPEATER_TEMPLATE",listOf(template.populationTemplate!!.itemNodeId),selected.take(remaining))
                }
            }
            val forms = WicketFormContexts().analyze(type, report)
            sources += forms.sources
            lifecycleDiagnostics += forms.diagnostics
            lifecycleComplete = lifecycleComplete && forms.complete
            val formModule = index.getModuleForFile(type.containingFile.virtualFile)
            if (formModule != null) forms.reports.forEach formLoop@ { template ->
                if (seeds.none { it.first.qualifiedName == template.qualifiedClassName || it.first.qualifiedName == template.formTemplate?.inputClassName || it.first.qualifiedName == template.formTemplate?.wrapperClassName || it.first.qualifiedName == template.formTemplate?.registration?.ownerClassName } &&
                    template.markup?.sourceFiles.orEmpty().none { it in relevantMarkup }) return@formLoop
                val selected = template.matching!!.findings.filter { it.code == DiagnosticCode.MATCHED }.mapNotNull { it.javaNodeId }
                val remaining = (limits.bindings - bindingCount).coerceAtLeast(0)
                if (selected.size > remaining) { lifecycleComplete = false; diagnostics += "Form template bindings exceed the ${limits.bindings}-binding bound." }
                bindingCount += selected.size.coerceAtMost(remaining)
                events += NavigationContext(template, formModule.name, if (index.isInTestSourceContent(type.containingFile.virtualFile)) "test" else "main",
                    "FORM_TEMPLATE", listOf(template.formTemplate!!.inputNodeId), selected.take(remaining))
            }
            val trees = WicketTreeContexts().analyze(type, report)
            sources += trees.sources
            lifecycleDiagnostics += trees.diagnostics
            lifecycleComplete = lifecycleComplete && trees.complete
            val treeModule = index.getModuleForFile(type.containingFile.virtualFile)
            if (treeModule != null) trees.reports.forEach treeLoop@ { template ->
                if (seeds.none { it.first.qualifiedName == template.qualifiedClassName || it.first.qualifiedName == template.treeTemplate?.hostClassName || it.first.qualifiedName == template.treeTemplate?.contentClassName || it.first.qualifiedName == template.treeTemplate?.nodeClassName } &&
                    template.markup?.sourceFiles.orEmpty().none { it in relevantMarkup }) return@treeLoop
                val selected = template.matching!!.findings.filter { it.code == DiagnosticCode.MATCHED }.mapNotNull { it.javaNodeId }
                val remaining = (limits.bindings - bindingCount).coerceAtLeast(0)
                if (selected.size > remaining) { lifecycleComplete = false; diagnostics += "Tree template bindings exceed the ${limits.bindings}-binding bound." }
                bindingCount += selected.size.coerceAtMost(remaining)
                events += NavigationContext(template, treeModule.name, if (index.isInTestSourceContent(type.containingFile.virtualFile)) "test" else "main",
                    "TREE_TEMPLATE", listOf(template.rootNodeId), selected.take(remaining))
            }
            val toolbars = WicketTableToolbarContexts().analyze(type, report)
            sources += toolbars.sources
            lifecycleDiagnostics += toolbars.diagnostics
            lifecycleComplete = lifecycleComplete && toolbars.complete
            val toolbarModule = index.getModuleForFile(type.containingFile.virtualFile)
            if (toolbarModule != null) toolbars.reports.forEach toolbarLoop@ { template ->
                val toolbar = template.toolbarTemplate!!
                if (seeds.none { it.first.qualifiedName in setOf(template.qualifiedClassName, toolbar.hostClassName, toolbar.toolbarClassName, toolbar.contentClassName) } &&
                    template.markup?.sourceFiles.orEmpty().none { it in relevantMarkup }) return@toolbarLoop
                val selected = template.matching!!.findings.filter { it.code == DiagnosticCode.MATCHED }.mapNotNull { it.javaNodeId }
                val remaining = (limits.bindings - bindingCount).coerceAtLeast(0)
                if (selected.size > remaining) { lifecycleComplete = false; diagnostics += "Table toolbar bindings exceed the ${limits.bindings}-binding bound." }
                bindingCount += selected.size.coerceAtMost(remaining)
                events += NavigationContext(template, toolbarModule.name, if(index.isInTestSourceContent(type.containingFile.virtualFile)) "test" else "main",
                    "TABLE_TOOLBAR_TEMPLATE", listOf(template.rootNodeId), selected.take(remaining))
            }
            val directEvents = WicketEventContentContexts().analyze(type, report)
            sources += directEvents.sources
            lifecycleDiagnostics += directEvents.diagnostics
            lifecycleComplete = lifecycleComplete && directEvents.complete
            val eventModule = index.getModuleForFile(type.containingFile.virtualFile)
            if(eventModule != null) directEvents.reports.forEach eventLoop@ { template ->
                if(seeds.none { it.first.qualifiedName==template.qualifiedClassName } && template.markup?.sourceFiles.orEmpty().none { it in relevantMarkup }) return@eventLoop
                val selected=template.matching!!.findings.filter { it.code==DiagnosticCode.MATCHED }.mapNotNull { it.javaNodeId }
                val remaining=(limits.bindings-bindingCount).coerceAtLeast(0)
                if(selected.size>remaining) { lifecycleComplete=false;diagnostics+="Event content bindings exceed the ${limits.bindings}-binding bound.";return@eventLoop }
                bindingCount+=selected.size.coerceAtMost(remaining)
                events+=NavigationContext(template,eventModule.name,if(index.isInTestSourceContent(type.containingFile.virtualFile)) "test" else "main","EVENT_TEMPLATE",listOf(template.lifecycle!!.factoryNodeId),selected.take(remaining))
            }
            val dialogs = if (WicketCompletionAnalysis.active) WicketSubmissionContexts.Result(emptyList(), emptyList(), emptyList(), true) else WicketSubmissionContexts().analyze(type, report)
            val ordinaryForms = if (WicketCompletionAnalysis.active) WicketFormSubmissionContexts.Result(emptyList(), emptyList(), emptyList(), true) else WicketFormSubmissionContexts().analyze(type, report)
            val confirmations = if (WicketCompletionAnalysis.active) WicketConfirmationContexts.Result(emptyList(), emptyList(), emptyList(), true) else WicketConfirmationContexts().analyze(type, report)
            val actions = if (WicketCompletionAnalysis.active) WicketSubmissionContexts.Result(emptyList(),emptyList(),emptyList(),true) else WicketActionContexts().analyze(type,report)
            val submissions = WicketSubmissionContexts.Result(dialogs.reports + ordinaryForms.reports + confirmations.reports + actions.reports,
                (dialogs.sources + ordinaryForms.sources + confirmations.sources + actions.sources).distinct(), dialogs.diagnostics + ordinaryForms.diagnostics + confirmations.diagnostics + actions.diagnostics,
                dialogs.complete && ordinaryForms.complete && confirmations.complete && actions.complete)
            sources += submissions.sources
            lifecycleDiagnostics += submissions.diagnostics
            lifecycleComplete = lifecycleComplete && submissions.complete
            if (eventModule != null) submissions.reports.forEach submissionLoop@ { template ->
                if (seeds.none { it.first.qualifiedName == template.qualifiedClassName } && template.markup?.sourceFiles.orEmpty().none { it in relevantMarkup }) return@submissionLoop
                val selected = template.matching!!.findings.filter { it.code == DiagnosticCode.MATCHED }.mapNotNull { it.javaNodeId }
                val remaining = (limits.bindings - bindingCount).coerceAtLeast(0)
                if (selected.size > remaining) { lifecycleComplete = false; diagnostics += "Submission bindings exceed the ${limits.bindings}-binding bound."; return@submissionLoop }
                bindingCount += selected.size
                events += NavigationContext(template, eventModule.name, if (index.isInTestSourceContent(type.containingFile.virtualFile)) "test" else "main",
                    when {template.actionTemplate!=null->"ACTION_TEMPLATE";template.submissionTemplate!!.confirmation==null->"SUBMISSION_TEMPLATE";else->"CONFIRMATION_TEMPLATE"},
                    listOf(template.submissionTemplate?.triggerNodeId ?: template.rootNodeId), selected)
            }
            val replacements = WicketReplacementContexts().analyze(type, report)
            sources += replacements.sources
            lifecycleDiagnostics += replacements.diagnostics
            lifecycleComplete = lifecycleComplete && replacements.complete
            val module = index.getModuleForFile(type.containingFile.virtualFile) ?: return@forEach
            replacements.reports.forEach templateLoop@ { template ->
                if (seeds.none { it.first.qualifiedName == template.qualifiedClassName || it.first.qualifiedName == template.lifecycle?.componentClassName } &&
                    template.markup?.sourceFiles.orEmpty().none { it in relevantMarkup }) return@templateLoop
                val content = template.lifecycle!!.factoryNodeId
                val selected = template.matching!!.findings.filter { it.code == DiagnosticCode.MATCHED }.mapNotNull { it.javaNodeId }
                val remaining = (limits.bindings - bindingCount).coerceAtLeast(0)
                if (selected.size > remaining) { lifecycleComplete = false; diagnostics += "Event template bindings exceed the ${limits.bindings}-binding bound." }
                bindingCount += selected.size.coerceAtMost(remaining)
                events += NavigationContext(template, module.name, if (index.isInTestSourceContent(type.containingFile.virtualFile)) "test" else "main",
                    when { template.lifecycle.contentFactory != null -> "FACTORY_TEMPLATE"
                        template.lifecycle.selection != null -> "SELECTION_TEMPLATE"
                        else -> "EVENT_TEMPLATE" }, listOf(content), selected.take(remaining))
            }
        }
        if (sources.size > limits.sources) { lifecycleComplete = false; diagnostics += "Event template discovery exceeds the ${limits.sources}-source bound." }
        return NavigationContexts(contexts + events, diagnostics.isEmpty() && lifecycleComplete, diagnostics.toList(), sources.sorted(), lifecycleDiagnostics)
    }
}
