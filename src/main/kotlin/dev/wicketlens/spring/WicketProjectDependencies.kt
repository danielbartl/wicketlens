package dev.wicketlens.spring

import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiField
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.AnnotatedElementsSearch
import com.intellij.psi.util.InheritanceUtil
import com.intellij.util.Processor
import dev.wicketlens.model.SourceLocation

internal data class ProjectInjection(val moduleName: String, val sourceSet: String, val field: InjectedField)

internal data class WicketProjectDependencies(
    val fields: List<ProjectInjection>,
    val sourceFiles: Set<String>,
    val truncated: Boolean,
)

internal object WicketProjectDependencyAnalyzer {
    private const val FIELD_LIMIT = 256
    private const val VISIT_LIMIT = 4096
    private const val SOURCE_LIMIT = 512
    private val annotations = listOf(WicketSpringSupport.BEAN, "jakarta.inject.Inject",
        "org.springframework.beans.factory.annotation.Autowired", WicketGuiceSetup.ANNOTATION)

    fun analyze(project: Project, moduleName: String?, sourceSet: String): WicketProjectDependencies {
        val modules = ModuleManager.getInstance(project).modules.filter { moduleName == null || it.name == moduleName }.sortedBy { it.name }
        val facade = JavaPsiFacade.getInstance(project)
        val index = ProjectFileIndex.getInstance(project)
        val analyzer = WicketDependencyAnalyzer()
        val fields = mutableListOf<ProjectInjection>()
        val seen = mutableSetOf<SourceLocation>()
        val sources = linkedSetOf<String>()
        var visits = 0
        var truncated = false

        for (module in modules) {
            val scope = GlobalSearchScope.moduleScope(module)
            val resolution = GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(module, sourceSet != "main")
            val catalogs = mutableMapOf<String, WicketSpringBeanNames.Catalog>()
            val setups = mutableMapOf<String, WicketSpringSetup.Setup>()
            for (name in annotations) {
                ProgressManager.checkCanceled()
                val annotation = facade.findClass(name, resolution) ?: continue
                AnnotatedElementsSearch.searchPsiFields(annotation, scope).forEach(Processor { field ->
                    ProgressManager.checkCanceled()
                    if (++visits > VISIT_LIMIT || fields.size >= FIELD_LIMIT) {
                        truncated = true
                        return@Processor false
                    }
                    val file = field.containingFile
                    val source = file.virtualFile ?: return@Processor true
                    if (!index.isInSourceContent(source) || index.getModuleForFile(source) != module) return@Processor true
                    val set = if (index.isInTestSourceContent(source)) "test" else "main"
                    if (sourceSet != "any" && sourceSet != set) return@Processor true
                    val type = field.containingClass ?: return@Processor true
                    if (!isComponentField(field)) return@Processor true
                    val location = SourceLocation(source.path, field.nameIdentifier.textOffset)
                    if (!seen.add(location)) return@Processor true
                    val setup = setups.getOrPut(set) { WicketSpringSetup.forFile(file) }
                    val value = analyzer.analyzeField(field, type, setup, "${location.file}@${location.offset}",
                        { catalogs.getOrPut(set) { WicketSpringBeanNames.catalog(file) } }) ?: return@Processor true
                    val additional = (value.candidates.map { it.location.file } + location.file + listOfNotNull(value.injectorLocation?.file)).toSet()
                    if (sources.size + additional.count { it !in sources } > SOURCE_LIMIT) {
                        truncated = true
                        return@Processor false
                    }
                    sources += additional
                    fields += ProjectInjection(module.name, set, value)
                    true
                })
                if (truncated) break
            }
            if (truncated) break
        }
        val sorted = fields.sortedWith(compareBy({ it.moduleName }, { it.sourceSet },
            { it.field.declaringClass }, { it.field.location.file }, { it.field.location.offset }))
        return WicketProjectDependencies(sorted, sources, truncated)
    }

    fun isComponentField(field: PsiField): Boolean = field.containingClass?.let {
        InheritanceUtil.isInheritor(it, WicketSpringSupport.COMPONENT)
    } == true
}
