package dev.wicketlens.mcp

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.intellij.codeInsight.daemon.HighlightDisplayKey
import com.intellij.codeInspection.InspectionManager
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.profile.codeInspection.InspectionProjectProfileManager
import com.intellij.psi.*
import com.intellij.psi.util.PsiTreeUtil
import dev.wicketlens.analysis.WicketSourceFiles
import dev.wicketlens.model.PageReport
import dev.wicketlens.model.SourceLocation
import dev.wicketlens.spring.WicketSpringSetup

data class SpringInspectionSetting(val file: String, val inspectionId: String, val enabled: Boolean, val severity: String)
data class SpringInspectionFinding(val inspectionId: String, val inspectionName: String, val message: String,
    val severity: String, val highlightType: String, val location: SourceLocation, val endOffset: Int,
    val declaringClass: String, val nodeIds: List<String>)
data class SpringInspectionSnapshot(val profileName: String, val settings: List<SpringInspectionSetting>,
    val findings: List<SpringInspectionFinding>, val sourceFiles: List<String>, val limitations: List<String>,
    val truncated: Boolean, val reviewedApplication: String?)

/** Runs the registered native inspections, not a second implementation of their rules. */
internal object WicketSpringInspectionReport {
    val IDS = listOf("WicketSpringInjection", "WicketSpringSerialization", "WicketSpringAnnotation",
        "WicketSpringBeanType", "WicketSpringBootstrap")
    private class LimitReached : RuntimeException()

    fun capture(project: Project, report: PageReport): SpringInspectionSnapshot {
        val profile = InspectionProjectProfileManager.getInstance(project).currentProfile
        val primary = WicketSourceFiles.find(project, report.nodes.single { it.nodeId == report.rootNodeId }.javaLocation.file)
            ?.let { PsiManager.getInstance(project).findFile(it) } as? PsiJavaFile
            ?: throw PageAnalysisFailure("ANALYSIS_UNAVAILABLE", "The selected inspection source is unavailable.")
        val index = ProjectFileIndex.getInstance(project)
        val module = index.getModuleForFile(primary.virtualFile)
        val test = index.isInTestSourceContent(primary.virtualFile)
        val facade = JavaPsiFacade.getInstance(project)
        val types = linkedMapOf<PsiClass, MutableSet<String>>()
        val limitations = linkedSetOf<String>()
        var truncated = false
        fun addHierarchy(first: PsiClass?, nodeId: String?) {
            var type = first
            val seen = mutableSetOf<PsiClass>()
            while (type != null && type.qualifiedName != "java.lang.Object") {
                ProgressManager.checkCanceled()
                if (type is PsiCompiledElement) {
                    if (type.qualifiedName?.startsWith("org.apache.wicket.") != true)
                        limitations += "Compiled custom ancestry is not inspected."
                    break
                }
                if (!seen.add(type) || seen.size > 12) { limitations += "Source ancestry is incomplete or exceeds 12 types."; truncated = true; break }
                val file = type.containingFile
                val virtual = file?.virtualFile
                if (file !is PsiJavaFile || virtual == null || index.getModuleForFile(virtual) != module ||
                    index.isInTestSourceContent(virtual) != test) {
                    limitations += "Source types outside the selected module/source set are not inspected."
                    break
                }
                if (type !in types && types.size >= 64) { truncated = true; limitations += "Source inspection is limited to 64 types."; break }
                types.getOrPut(type) { linkedSetOf() }.apply { nodeId?.let(::add) }
                if (type.extendsListTypes.any { it.resolve() == null }) limitations += "An unresolved source ancestor was not inspected."
                type = type.superClass
            }
        }
        val root = primary.classes.singleOrNull { it.qualifiedName == report.qualifiedClassName }
            ?: PsiTreeUtil.findChildrenOfType(primary, PsiClass::class.java).single { it.qualifiedName == report.qualifiedClassName }
        addHierarchy(root, report.rootNodeId)
        val setup = WicketSpringSetup.forFile(primary)
        setup.reviewedApplication?.let { name -> addHierarchy(facade.findClasses(name, primary.resolveScope).singleOrNull(), null) }
        for (node in report.nodes.filter { it.nodeId != report.rootNodeId }) {
            ProgressManager.checkCanceled()
            val matches = facade.findClasses(node.componentType, primary.resolveScope)
            if (matches.size != 1) { limitations += "Some discovered component types could not be resolved uniquely."; continue }
            addHierarchy(matches.single(), node.nodeId)
        }
        val files = types.keys.map { it.containingFile as PsiJavaFile }.distinctBy { it.virtualFile.url }.sortedBy { it.virtualFile.path }
        val settings = mutableListOf<SpringInspectionSetting>()
        val findings = mutableListOf<SpringInspectionFinding>()
        try {
            for (file in files) for (id in IDS) {
                ProgressManager.checkCanceled()
                val wrapper = profile.getInspectionTool(id, file)
                val key = HighlightDisplayKey.find(id)
                if (wrapper == null || key == null) { limitations += "Inspection $id is unavailable."; continue }
                val enabled = profile.isToolEnabled(key, file)
                val severity = profile.getErrorLevel(key, file).severity.name
                settings += SpringInspectionSetting(file.virtualFile.path, id, enabled, severity)
                if (!enabled) continue
                val tool = wrapper.tool as? LocalInspectionTool ?: continue
                if (!tool.isAvailableForFile(file)) continue
                val holder = object : ProblemsHolder(InspectionManager.getInstance(project), file, false) {
                    override fun registerProblem(descriptor: ProblemDescriptor) {
                        ProgressManager.checkCanceled()
                        val anchor = descriptor.psiElement ?: return
                        val owner = PsiTreeUtil.getParentOfType(anchor, PsiClass::class.java, false) ?: return
                        val nodes = types[owner] ?: return // Do not include unrelated classes sharing a source file.
                        if (tool.isSuppressedFor(anchor) || !profile.isToolEnabled(key, anchor)) return
                        if (findings.size >= 256) throw LimitReached()
                        val range = descriptor.textRangeInElement
                        val start = anchor.textRange.startOffset + (range?.startOffset ?: 0)
                        val end = anchor.textRange.startOffset + (range?.endOffset ?: anchor.textLength)
                        findings += SpringInspectionFinding(id, wrapper.displayName, descriptor.descriptionTemplate,
                            profile.getErrorLevel(key, anchor).severity.name, descriptor.highlightType.name,
                            SourceLocation(file.virtualFile.path, start), end, owner.qualifiedName ?: owner.name.orEmpty(), nodes.sorted())
                    }
                }
                tool.buildVisitor(holder, false).visitFile(file)
            }
        } catch (_: LimitReached) {
            truncated = true
            limitations += "Inspection findings are limited to 256; remaining checks were not completed."
        }
        return SpringInspectionSnapshot(profile.name, settings, findings.distinct().sortedWith(compareBy(
            { it.location.file }, { it.location.offset }, { it.inspectionId }, { it.message })),
            files.map { it.virtualFile.path }, limitations.toList(), truncated, setup.reviewedApplication)
    }

    fun isCurrent(project: Project, snapshot: SpringInspectionSnapshot): Boolean {
        val profile = InspectionProjectProfileManager.getInstance(project).currentProfile
        if (profile.name != snapshot.profileName) return false
        return snapshot.settings.all { state ->
            ProgressManager.checkCanceled()
            val file = WicketSourceFiles.find(project, state.file)?.let { PsiManager.getInstance(project).findFile(it) } ?: return@all false
            val key = HighlightDisplayKey.find(state.inspectionId) ?: return@all false
            profile.isToolEnabled(key, file) == state.enabled && profile.getErrorLevel(key, file).severity.name == state.severity
        }
    }

    fun render(snapshot: SpringInspectionSnapshot) = JsonObject().apply {
        addProperty("schemaVersion", 1)
        addProperty("profileName", snapshot.profileName)
        addProperty("runtimeValidityVerified", false)
        addProperty("truncated", snapshot.truncated)
        addProperty("reviewedApplication", snapshot.reviewedApplication)
        add("scopeNotes", strings(listOf("Native WicketLens Spring inspections in the current IDE inspection profile; disabled and suppressed checks stay excluded.",
            "Scope: selected page, discovered source components, bounded source ancestry, and the reviewed application in the same module/source set.",
            "Empty findings do not prove valid wiring. Unobserved models, sessions, helpers, other modules and runtime objects are not inspected.")))
        add("limitations", strings(snapshot.limitations))
        add("inspectedFiles", strings(snapshot.sourceFiles))
        add("checks", JsonArray().apply { snapshot.settings.forEach { state ->
            add(JsonObject().apply { addProperty("file", state.file); addProperty("inspectionId", state.inspectionId)
                addProperty("enabled", state.enabled); addProperty("severity", state.severity) })
        } })
        add("findings", JsonArray().apply { snapshot.findings.forEach { finding ->
            ProgressManager.checkCanceled()
            add(JsonObject().apply {
                addProperty("inspectionId", finding.inspectionId); addProperty("inspectionName", finding.inspectionName)
                addProperty("message", finding.message); addProperty("severity", finding.severity); addProperty("highlightType", finding.highlightType)
                add("location", JsonObject().apply { addProperty("file", finding.location.file); addProperty("offset", finding.location.offset); addProperty("endOffset", finding.endOffset) })
                addProperty("declaringClass", finding.declaringClass); add("nodeIds", strings(finding.nodeIds))
            })
        } })
    }
    private fun strings(values: List<String>) = JsonArray().apply { values.forEach(::add) }
}
