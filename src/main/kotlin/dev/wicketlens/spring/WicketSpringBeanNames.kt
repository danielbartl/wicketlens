package dev.wicketlens.spring

import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.psi.*
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.AnnotatedElementsSearch
import com.intellij.psi.util.*
import com.intellij.util.Processor

/** Source candidates, never a runtime Spring bean model. Cached records contain no PSI. */
internal object WicketSpringBeanNames {
    const val BEAN = "org.springframework.context.annotation.Bean"
    private const val CONFIG = "org.springframework.context.annotation.Configuration"
    private const val FACTORY = "org.springframework.beans.factory.FactoryBean"
    private val STEREOTYPES = listOf("org.springframework.stereotype.Component", "org.springframework.stereotype.Service",
        "org.springframework.stereotype.Repository", "org.springframework.stereotype.Controller", CONFIG)
    data class Use(val literal: PsiLiteralExpression, val field: PsiField)
    data class Bean(val name: String, val type: String, val owner: String, val url: String, val offset: Int,
        val factory: Boolean, val conditional: Boolean, val productType: String? = null,
        val dereference: Boolean = false, val profiles: List<String> = emptyList(), val profilesUnknown: Boolean = false)
    data class Catalog(val beans: List<Bean> = emptyList(), val validationRoots: Set<String> = emptySet(), val truncated: Boolean = false)
    private val KEY = Key.create<com.intellij.psi.util.CachedValue<Catalog>>("wicketlens.spring.bean.names")

    fun use(element: PsiElement): Use? {
        val literal = element as? PsiLiteralExpression ?: return null
        val file = literal.containingFile
        if (!WicketSpringSupport.supports(file) || literal.value !is String || literal.text.startsWith("\"\"\"")) return null
        val pair = literal.parent as? PsiNameValuePair ?: return null
        if (pair.value != literal) return null // Constants and composed expressions keep their normal Java references.
        val annotation = pair.parent?.parent as? PsiAnnotation ?: return null
        val field = annotation.parent?.parent as? PsiField ?: return null
        if (field.hasModifierProperty(PsiModifier.STATIC) || field.type is PsiPrimitiveType) return null
        return when (annotation.resolveAnnotationType()?.qualifiedName) {
            WicketSpringSupport.BEAN -> if (pair.name == "name") Use(literal, field) else null
            "jakarta.inject.Named" -> if (pair.name in setOf(null, "value") &&
                WicketSpringSupport.annotation(field, WicketSpringSupport.BEAN) == null &&
                WicketSpringSupport.annotation(field, "jakarta.inject.Inject") != null && WicketSpringSetup.forFile(file).registered &&
                (WicketSpringSupport.automatic(field.containingClass) || WicketSpringInjectionPaths.forClass(field.containingClass ?: return null).wicket)) Use(literal, field) else null
            else -> null
        }
    }

    fun at(file: PsiFile, offset: Int): Use? {
        val leaf = file.findElementAt(offset.coerceAtMost((file.textLength - 1).coerceAtLeast(0))) ?: return null
        return use(PsiTreeUtil.getParentOfType(leaf, PsiLiteralExpression::class.java, false) ?: return null)
    }

    fun catalog(file: PsiFile): Catalog {
        if (!WicketSpringSupport.supports(file)) return Catalog()
        return CachedValuesManager.getCachedValue(file, KEY) {
            CachedValueProvider.Result.create(compute(file), PsiModificationTracker.MODIFICATION_COUNT,
                ProjectRootManager.getInstance(file.project), VirtualFileManager.VFS_STRUCTURE_MODIFICATIONS)
        }
    }

    private fun compute(file: PsiFile): Catalog {
        val index = ProjectFileIndex.getInstance(file.project)
        val module = index.getModuleForFile(file.virtualFile) ?: return Catalog()
        val scope = GlobalSearchScope.moduleScope(module)
        val facade = JavaPsiFacade.getInstance(file.project)
        val test = index.isInTestSourceContent(file.virtualFile)
        val beans = mutableListOf<Bean>()
        var declarations = 0
        var truncated = false
        fun eligible(owner: PsiElement): Boolean {
            val virtual = owner.containingFile?.virtualFile ?: return false
            return owner.containingFile is PsiJavaFile && index.getModuleForFile(virtual) == module && index.isInTestSourceContent(virtual) == test
        }
        fun append(owner: PsiMember, names: List<String>, type: PsiType, conditional: Boolean) {
            val containing = if (owner is PsiClass) owner else owner.containingClass ?: return
            val qualified = containing.qualifiedName ?: return
            val resolved = (type as? PsiClassType)?.resolve()
            val factory = resolved?.let { it.qualifiedName == FACTORY || InheritanceUtil.isInheritor(it, FACTORY) } == true
            val product = if (factory) PsiUtil.substituteTypeParameter(type, FACTORY, 0, false)?.takeIf(::exactProductType)?.canonicalText else null
            val profiles = WicketSpringProfiles.read(listOfNotNull(containing, owner.takeUnless { it == containing }))
            for (name in names) {
                val bean = Bean(name, type.canonicalText, qualified, owner.containingFile.virtualFile.url, owner.textRange.startOffset,
                    factory, conditional, product, profiles = profiles.conditions, profilesUnknown = profiles.unknown)
                beans += bean
                if (factory) beans += bean.copy(name = "&$name", dereference = true)
            }
        }
        facade.findClass(BEAN, file.resolveScope)?.let { annotation ->
            AnnotatedElementsSearch.searchPsiMethods(annotation, scope).forEach(Processor { method ->
                ProgressManager.checkCanceled()
                if (!eligible(method)) return@Processor true
                if (++declarations > 256) { truncated = true; return@Processor false }
                val annot = WicketSpringSupport.annotation(method, BEAN) ?: return@Processor true
                val type = method.returnType?.takeIf(::usableType) ?: return@Processor true
                if (method.isConstructor || type == PsiTypes.voidType()) return@Processor true
                val names = names(annot, method.name) ?: return@Processor true
                append(method, names, type, !plainAnnotations(method, setOf(BEAN)) ||
                    method.containingClass?.let { !plainAnnotations(it, setOf(CONFIG)) } != false)
                true
            })
        }
        for (name in STEREOTYPES) {
            if (truncated) break
            facade.findClass(name, file.resolveScope)?.let { annotation ->
                AnnotatedElementsSearch.searchPsiClasses(annotation, scope).forEach(Processor { type ->
                    ProgressManager.checkCanceled()
                    if (!eligible(type)) return@Processor true
                    if (++declarations > 256) { truncated = true; return@Processor false }
                    if (type.isAnnotationType || type.isInterface || type.hasModifierProperty(PsiModifier.ABSTRACT)) return@Processor true
                    val annot = WicketSpringSupport.annotation(type, name) ?: return@Processor true
                    // Several stereotypes with naming attributes require merged-annotation semantics.
                    if (type.modifierList?.annotations.orEmpty().count { it.resolveAnnotationType()?.qualifiedName in STEREOTYPES } > 1) return@Processor true
                    val explicit = annot.findDeclaredAttributeValue("value")
                    val simple = type.name ?: return@Processor true
                    val value = if (explicit == null) "" else (explicit as? PsiLiteralExpression)?.value as? String ?: return@Processor true
                    val beanName = value.ifBlank { if (simple.length > 1 && simple[0].isUpperCase() && simple[1].isUpperCase()) simple else simple.replaceFirstChar { it.lowercaseChar() } }
                    append(type, listOf(beanName), JavaPsiFacade.getElementFactory(file.project).createType(type), true)
                    true
                })
            }
        }
        val configurations = WicketSpringSetup.forFile(file).configurations
        val roots = configurations.filter { qualified ->
            ProgressManager.checkCanceled()
            if (truncated) return@filter false
            val type = facade.findClasses(qualified, file.resolveScope).singleOrNull() ?: return@filter false
            eligible(type) && WicketSpringSupport.annotation(type, CONFIG) != null && plainAnnotations(type, setOf(CONFIG)) &&
                type.superClass?.qualifiedName == "java.lang.Object" && type.implementsListTypes.isEmpty() &&
                type.initializers.isEmpty() && type.fields.none { it.initializer != null } &&
                PsiTreeUtil.findChildOfType(type, PsiErrorElement::class.java) == null &&
                type.methods.filter { !it.isConstructor }.all { method ->
                    val annotation = WicketSpringSupport.annotation(method, BEAN)
                    val returned = method.returnType
                    val resolved = (returned as? PsiClassType)?.resolve()
                    annotation != null && plainAnnotations(method, setOf(BEAN)) && names(annotation, method.name) != null &&
                        returned != null && usableType(returned) && returned != PsiTypes.voidType() &&
                        resolved?.let { beanType -> listOf("org.springframework.beans.factory.config.BeanFactoryPostProcessor",
                            "org.springframework.beans.factory.config.BeanPostProcessor").none { base -> beanType.qualifiedName == base || InheritanceUtil.isInheritor(beanType, base) } } != false
                } &&
                type.constructors.all { constructor -> constructor.body?.statements?.isEmpty() == true }
        }.toSet()
        return Catalog(beans.distinct().sortedWith(compareBy(Bean::name, Bean::url, Bean::offset)), roots.takeIf { it.size == configurations.size }.orEmpty(), truncated)
    }

    private fun plainAnnotations(owner: PsiModifierListOwner, allowed: Set<String>) = owner.modifierList?.annotations.orEmpty().all {
        val name = it.resolveAnnotationType()?.qualifiedName
        name in allowed || name in setOf("java.lang.Deprecated", "java.lang.SuppressWarnings", "java.lang.Override", "java.lang.SafeVarargs")
    }

    private fun names(annotation: PsiAnnotation, fallback: String): List<String>? {
        fun strings(value: PsiAnnotationMemberValue): List<String>? {
            return when (value) {
                is PsiLiteralExpression -> (value.value as? String)?.let(::listOf)
                is PsiArrayInitializerMemberValue -> value.initializers.map { (it as? PsiLiteralExpression)?.value as? String ?: return null }
                else -> null
            }
        }
        val name = annotation.findDeclaredAttributeValue("name")?.let { strings(it) ?: return null }
        val value = annotation.findDeclaredAttributeValue("value")?.let { strings(it) ?: return null }
        if (name != null && value != null && name != value) return null
        val names = name ?: value ?: emptyList()
        return if (names.isEmpty()) listOf(fallback) else names.takeIf { it.size <= 32 && it.all(String::isNotEmpty) }?.distinct()
    }

    private fun usableType(type: PsiType): Boolean = when (type) {
        is PsiClassType -> type.resolve()?.let { it !is PsiTypeParameter } == true && type.parameters.all(::usableType)
        is PsiArrayType -> usableType(type.componentType)
        is PsiWildcardType -> type.bound?.let(::usableType) ?: true
        is PsiPrimitiveType -> true
        else -> false
    }

    private fun exactProductType(type: PsiType, depth: Int = 0): Boolean {
        ProgressManager.checkCanceled()
        if (depth >= 12) return false
        return when (type) {
            is PsiClassType -> type.resolve()?.let { it !is PsiTypeParameter } == true &&
                !type.isRaw && type.parameters.all { exactProductType(it, depth + 1) }
            is PsiArrayType -> exactProductType(type.componentType, depth + 1)
            else -> false // Raw, wildcard and unresolved generic products stay unknown.
        }
    }

    fun target(bean: Bean, file: PsiFile): PsiElement? {
        if (!WicketSpringSupport.supports(file)) return null
        val virtual = VirtualFileManager.getInstance().findFileByUrl(bean.url) ?: return null
        val source = PsiManager.getInstance(file.project).findFile(virtual) ?: return null
        return generateSequence(source.findElementAt(bean.offset)) { it.parent }.firstOrNull { it is PsiMethod || it is PsiClass }
    }

    fun compatible(bean: Bean, field: PsiField): Boolean {
        return compatibility(bean, field) == true
    }

    fun lookupType(bean: Bean): String? = if (bean.factory && !bean.dereference) bean.productType else bean.type

    fun compatibility(bean: Bean, field: PsiField): Boolean? {
        ProgressManager.checkCanceled()
        val text = lookupType(bean) ?: return null
        val type = JavaPsiFacade.getElementFactory(field.project).createTypeFromText(text, field)
        return TypeConversionUtil.isAssignable(field.type, type)
    }

    fun description(bean: Bean): String = buildList {
        if (bean.factory) add(if (bean.dereference) "FactoryBean instance" else "FactoryBean product: ${bean.productType ?: "type unknown"}")
        if (bean.profiles.isNotEmpty()) add("profiles: " + bean.profiles.joinToString(" AND "))
        if (bean.profilesUnknown) add("profile condition unknown")
    }.joinToString("; ")

    fun mismatch(use: Use): String? {
        val name = use.literal.value as? String ?: return null
        if (name.isEmpty()) return null // Empty SpringBean names request by-type lookup.
        val catalog = catalog(use.literal.containingFile)
        if (catalog.truncated || catalog.validationRoots.isEmpty()) return null
        val matches = catalog.beans.filter { it.name == name }
        // Multiple source declarations and conditional/unclassified roots do not identify one bean.
        val bean = matches.singleOrNull()?.takeIf { it.owner in catalog.validationRoots && !it.conditional && !it.factory } ?: return null
        val expected = use.field.type as? PsiClassType ?: return null
        val expectedClass = expected.resolve() ?: return null
        if (expected.parameters.isNotEmpty() || expectedClass is PsiTypeParameter || expectedClass.isEnum ||
            expectedClass.qualifiedName?.startsWith("java.") == true) return null
        if (bean.type == "java.lang.String" && (expectedClass.constructors.any { constructor ->
                constructor.parameterList.parameters.singleOrNull()?.type?.equalsToText("java.lang.String") == true
            } || expectedClass.findMethodsByName("valueOf", true).any { method -> method.hasModifierProperty(PsiModifier.STATIC) &&
                method.parameterList.parameters.singleOrNull()?.type?.equalsToText("java.lang.String") == true })) return null
        val type = JavaPsiFacade.getElementFactory(use.field.project).createTypeFromText(bean.type, use.field) as? PsiClassType ?: return null
        val owner = type.resolve() ?: return null
        if (!owner.hasModifierProperty(PsiModifier.FINAL) || type.parameters.isNotEmpty() || compatible(bean, use.field)) return null
        return "The source bean '$name' in a directly registered configuration declares final type '${bean.type}', incompatible with this field. Verify context selection, conversions, and any runtime bean replacement."
    }
}
