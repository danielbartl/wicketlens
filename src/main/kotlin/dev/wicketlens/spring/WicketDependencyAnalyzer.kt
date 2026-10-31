package dev.wicketlens.spring

import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.psi.*
import com.intellij.psi.util.PsiTreeUtil
import dev.wicketlens.analysis.WicketSourceFiles
import dev.wicketlens.model.ComponentNode
import dev.wicketlens.model.PageReport
import dev.wicketlens.model.SourceLocation

/** Analyze only discovered component source types. Source candidates never become confirmed runtime bindings. */
class WicketDependencyAnalyzer {
    fun analyze(project: Project, report: PageReport): WicketDependencySnapshot {
        ProgressManager.checkCanceled()
        val primary = report.nodes.singleOrNull { it.nodeId == report.rootNodeId } ?: return WicketDependencySnapshot()
        val file = WicketSourceFiles.find(project, primary.javaLocation.file)?.let { PsiManager.getInstance(project).findFile(it) } as? PsiJavaFile
            ?: return WicketDependencySnapshot()
        if (!WicketSpringSupport.supports(file)) return WicketDependencySnapshot()
        val facade = JavaPsiFacade.getInstance(project)
        if (facade.findClass(WicketSpringSupport.BEAN, file.resolveScope) == null) return WicketDependencySnapshot()
        val setup = WicketSpringSetup.forFile(file)
        val sources = linkedSetOf<String>()
        fun source(element: PsiElement) { element.containingFile?.virtualFile?.takeUnless { element is PsiCompiledElement }?.path?.let(sources::add) }
        setup.application?.let { facade.findClasses(it, file.resolveScope).singleOrNull() }?.let(::source)
        for (name in setup.configurations) facade.findClasses(name, file.resolveScope).singleOrNull()?.let(::source)
        val catalog by lazy { WicketSpringBeanNames.catalog(file) }
        val owners = mutableListOf<DependencyOwner>()
        var count = 0
        var truncated = false
        val nodes = report.nodes.associateBy { it.nodeId }
        fun path(node: ComponentNode): String {
            val parts = mutableListOf<String>()
            var current: ComponentNode? = node
            val seen = mutableSetOf<String>()
            while (current != null && seen.add(current.nodeId)) {
                parts += if (current.nodeId == report.rootNodeId) report.qualifiedClassName.substringAfterLast('.') else current.wicketId ?: "<dynamic ID>"
                current = nodes[current.parentNodeId]
            }
            return parts.asReversed().joinToString(":")
        }
        for (node in report.nodes) {
            ProgressManager.checkCanceled()
            val type = if (node.nodeId == report.rootNodeId)
                PsiTreeUtil.findChildrenOfType(file, PsiClass::class.java).singleOrNull { it.qualifiedName == report.qualifiedClassName }
            else facade.findClasses(node.componentType, file.resolveScope).singleOrNull()
            if (type == null || type is PsiCompiledElement) continue
            if (owners.size >= 64 || count >= 256) { truncated = true; break }
            val fields = mutableListOf<InjectedField>()
            val seen = mutableSetOf<PsiClass>()
            var owner: PsiClass? = type
            var partial = false
            while (owner != null && owner.qualifiedName != "java.lang.Object") {
                ProgressManager.checkCanceled()
                if (owner is PsiCompiledElement) {
                    if (owner.qualifiedName?.startsWith("org.apache.wicket.") != true) partial = true
                    break
                }
                if (!seen.add(owner) || seen.size > 12) { partial = true; break }
                source(owner)
                if (owner.extendsListTypes.any { it.resolve() == null }) partial = true
                if (PsiTreeUtil.findChildOfType(owner, PsiErrorElement::class.java) != null) partial = true
                for (field in owner.fields) {
                    ProgressManager.checkCanceled()
                    if (count >= 256) { truncated = true; break }
                    val bean = WicketSpringSupport.annotation(field, WicketSpringSupport.BEAN)
                    val inject = WicketSpringSupport.annotation(field, "jakarta.inject.Inject")?.takeIf { setup.registered }
                    val autowired = WicketSpringSupport.annotation(field, "org.springframework.beans.factory.annotation.Autowired")?.takeIf { setup.registered }
                    val annotation = bean ?: inject ?: autowired ?: continue
                    val notes = mutableListOf<String>()
                    val static = field.hasModifierProperty(PsiModifier.STATIC)
                    val primitive = field.type is PsiPrimitiveType
                    val lookupAnnotation = bean ?: WicketSpringSupport.annotation(field, "jakarta.inject.Named")?.takeIf { inject != null }
                    val value = lookupAnnotation?.findDeclaredAttributeValue(if (bean != null) "name" else "value")
                    val name = if (value == null) "" else (value as? PsiLiteralExpression)?.value as? String
                    val lookup = when {
                        static || primitive -> DependencyLookup.UNSUPPORTED
                        bean == null && inject == null -> DependencyLookup.SPRING_AUTOWIRING
                        name == null -> DependencyLookup.UNKNOWN
                        name.isEmpty() -> DependencyLookup.TYPE
                        else -> DependencyLookup.NAME
                    }
                    if (static) notes += "Static fields are not supported by Wicket's injector."
                    if (primitive) notes += "Primitive fields cannot receive Wicket's null-only injection."
                    if (lookup == DependencyLookup.UNKNOWN) notes += "The explicit bean name is not a direct literal; selection is not inferred."
                    if (lookup == DependencyLookup.SPRING_AUTOWIRING) {
                        val paths = WicketSpringInjectionPaths.forClass(type)
                        notes += if (paths.spring || paths.managed) "Spring autowiring/management intent is recognized; execution is not verified."
                            else "Wicket's listener does not process @Autowired; verify explicit Spring autowiring or management."
                    }
                    if (field.hasModifierProperty(PsiModifier.TRANSIENT)) notes += "Transient dependency storage; review deserialization and reinjection."
                    if (field.initializer != null && !WicketSpringSupport.repairedInitializer(field)) notes += "Explicit field initialization; review possible construction-time injection overwrites."
                    fun known(type: PsiType, depth: Int = 0): Boolean {
                        ProgressManager.checkCanceled()
                        if (depth >= 12) return false
                        return when (type) {
                            is PsiClassType -> type.resolve()?.let { it !is PsiTypeParameter } == true && type.parameters.all { known(it, depth + 1) }
                            is PsiArrayType -> known(type.componentType, depth + 1)
                            is PsiWildcardType -> type.bound?.let { known(it, depth + 1) } ?: true
                            is PsiPrimitiveType -> true
                            else -> false
                        }
                    }
                    val knownType = known(field.type)
                    fun compatible(candidate: WicketSpringBeanNames.Bean): Boolean? =
                        if (!knownType || candidate.factory) null else WicketSpringBeanNames.compatible(candidate, field)
                    val candidates = if (lookup in setOf(DependencyLookup.NAME, DependencyLookup.TYPE)) {
                        catalog.beans.groupBy { it.url to it.offset }.values
                            .filter { if (lookup == DependencyLookup.NAME) it.any { declaration -> declaration.name == name } else compatible(it.first()) == true }
                            .take(33).mapNotNull { declarations ->
                                val first = declarations.first()
                                val target = WicketSpringBeanNames.target(first, file) ?: return@mapNotNull null
                                source(target)
                                DependencyCandidate(declarations.map { it.name }.distinct().sorted(), first.type, first.owner,
                                    SourceLocation(target.containingFile.virtualFile.path, target.textRange.startOffset), compatible(first), first.conditional, first.factory)
                            }
                    } else emptyList()
                    if (!knownType && lookup in setOf(DependencyLookup.TYPE, DependencyLookup.NAME)) notes += "Generic or unresolved field type; compatibility is not inferred."
                    val requiredValue = bean?.findDeclaredAttributeValue("required")
                    val required = if (bean == null && inject == null) null else if (requiredValue == null) true else (requiredValue as? PsiLiteralExpression)?.value as? Boolean
                    val declaring = owner.qualifiedName ?: owner.name ?: "<anonymous>"
                    fields += InjectedField("${node.nodeId}:$declaring#${field.name}", field.name, field.type.canonicalText, declaring,
                        annotation.resolveAnnotationType()?.name ?: "Injection", lookup, name?.takeIf { lookup == DependencyLookup.NAME }, required,
                        SourceLocation(field.containingFile.virtualFile.path, field.nameIdentifier.textOffset), candidates.take(32), notes,
                        candidates.size > 32 || lookup in setOf(DependencyLookup.NAME, DependencyLookup.TYPE) && catalog.truncated)
                    count++
                }
                owner = owner.superClass
            }
            if (fields.isNotEmpty()) owners += DependencyOwner(node.nodeId, path(node), node.componentType, node.javaLocation, fields, partial)
        }
        return WicketDependencySnapshot(owners, sources.toList(), setup.application, truncated)
    }
}
