package dev.wicketlens.analysis

import com.intellij.openapi.progress.ProgressManager
import com.intellij.psi.*
import com.intellij.psi.util.InheritanceUtil
import com.intellij.psi.util.PsiTreeUtil
import dev.wicketlens.analysis.WicketSourceDeclarations.Id
import dev.wicketlens.model.*

/** Source-declared lazy composite inputs; no conversion, validation or model code is executed. */
internal class WicketFormContexts(private val limits: Limits = Limits()) {
    data class Limits(val steps: Int = 12000, val sources: Int = 64, val contexts: Int = 4, val depth: Int = 12, val nodes: Int = 64)
    data class Result(val reports: List<PageReport>, val sources: List<String>, val diagnostics: List<Diagnostic>, val complete: Boolean)
    private class Unknown(val at: PsiElement, override val message: String) : RuntimeException(null, null, false, false)
    private class Bound(val at: PsiElement) : RuntimeException(null, null, false, false)

    companion object {
        fun hasRegisteredFactory(type: PsiClass) = WicketFormRegistration.hasFactory(type)
    }

    fun analyze(type: PsiClass, primary: PageReport): Result = Session(type, primary).run()

    private inner class Session(val type: PsiClass, val primary: PageReport) {
        val sources = linkedSetOf<String>()
        val reports = mutableListOf<PageReport>()
        val diagnostics = mutableListOf<Diagnostic>()
        val cache = mutableMapOf<PsiElement, List<PsiElement>>()
        val visited = mutableSetOf<PsiElement>()
        var steps = 0
        val registrations = WicketFormRegistration(limits.depth, ::tick, ::source, ::fail, { throw Bound(it) },
            { descendants(it, PsiElement::class.java) })
        val declarations = WicketSourceDeclarations(type, limits.depth, ::tick, ::source, sources, ::fail,
            { throw Bound(it) }, { descendants(it, PsiElement::class.java) })

        fun tick(at: PsiElement) {
            ProgressManager.checkCanceled()
            if (++steps > limits.steps || sources.size > limits.sources) throw Bound(at)
        }
        fun fail(at: PsiElement, message: String): Nothing = throw Unknown(at, message)
        fun source(at: PsiElement) {
            tick(at)
            val file = at.containingFile ?: fail(at, "Source is unavailable.")
            sources += file.virtualFile?.path?.substringBefore("!/") ?: fail(at, "Physical source is unavailable.")
            if (sources.size > limits.sources) throw Bound(at)
            if (at !is PsiCompiledElement && PsiTreeUtil.findChildOfType(file, PsiErrorElement::class.java) != null)
                fail(at, "Java syntax is incomplete.")
        }
        fun <T : PsiElement> descendants(at: PsiElement, kind: Class<T>): List<T> {
            val elements = cache.getOrPut(at) {
                val ancestor = generateSequence(at.parent) { it.parent }.firstOrNull { it in cache }
                if (ancestor != null) return@getOrPut cache.getValue(ancestor).filter { PsiTreeUtil.isAncestor(at, it, false) }
                val result = mutableListOf<PsiElement>()
                at.accept(object : JavaRecursiveElementWalkingVisitor() {
                    override fun visitElement(element: PsiElement) {
                        if (visited.add(element)) tick(element)
                        result += element
                        super.visitElement(element)
                    }
                })
                result
            }
            tick(at)
            return elements.filter(kind::isInstance).map(kind::cast)
        }
        fun hierarchy(owner: PsiClass): List<PsiClass> {
            val result = mutableListOf<PsiClass>()
            var current: PsiClass? = owner
            while (current != null && current !is PsiCompiledElement) {
                if (result.size >= limits.depth || current in result) throw Bound(current)
                source(current)
                result += current
                current = current.superClass
            }
            return result
        }
        fun self(expression: PsiExpression?, owners: List<PsiClass>) = expression == null || expression is PsiThisExpression &&
            (expression.qualifier == null || expression.qualifier?.resolve() in owners)
        fun runtime(method: PsiMethod, owners: List<PsiClass>): PsiMethod = owners.firstNotNullOfOrNull { owner ->
            owner.methods.firstOrNull { candidate -> candidate == method || !candidate.hasModifierProperty(PsiModifier.STATIC) &&
                candidate.findSuperMethods(true).any { it == method } }
        } ?: method

        fun run(): Result {
            if (primary.markupSuffix.isNotEmpty()) return Result(emptyList(), emptyList(), emptyList(), true)
            var complete = true
            try {
                val owners = hierarchy(type)
                WicketFormCacheContract.find(owners, ::tick).forEach { (getter, field) ->
                    try { build(owners, getter, field) }
                    catch (unknown: Unknown) { diagnostics += Diagnostic(DiagnosticCode.UNRESOLVED_SCOPE,
                        "Form input template withheld: ${unknown.message}", location(unknown.at)) }
                }
                descendants(type, PsiNewExpression::class.java).forEach { creation ->
                    try {
                        val registration = registrations.discover(creation) ?: return@forEach
                        val wrapperOwners = hierarchy(registration.wrapper)
                        WicketFormCacheContract.find(wrapperOwners, ::tick).forEach { (getter, field) ->
                            build(wrapperOwners, getter, field, registration)
                        }
                    } catch (unknown: Unknown) { diagnostics += Diagnostic(DiagnosticCode.UNRESOLVED_SCOPE,
                        "Registered form input template withheld: ${unknown.message}", location(unknown.at)) }
                }
            } catch (bound: Bound) {
                complete = false
                reports.clear()
                diagnostics += Diagnostic(DiagnosticCode.PARTIAL_ANALYSIS,
                    "Form input discovery exceeded its ${limits.steps}-step/${limits.sources}-source/${limits.contexts}-context/${limits.depth}-level/${limits.nodes}-node bound; no form template was published.", location(bound.at))
            }
            return Result(reports, sources.sorted(), diagnostics, complete)
        }

        fun build(owners: List<PsiClass>, getter: PsiMethod, field: PsiField, registration: WicketFormRegistration.Registration? = null) {
            val type = registration?.wrapper ?: this.type
            val assignment = descendants(getter, PsiAssignmentExpression::class.java).singleOrNull {
                (it.lExpression as? PsiReferenceExpression)?.resolve() == field
            } ?: fail(getter, "A unique cache assignment is required.")
            val call = assignment.rExpression as? PsiMethodCallExpression ?: fail(assignment, "The cache must invoke a source factory.")
            if (!self(call.methodExpression.qualifierExpression, owners)) fail(call, "The input factory uses a foreign receiver.")
            val declared = call.resolveMethod() ?: fail(call, "The input factory is unavailable.")
            val factory = runtime(declared, owners)
            val output = (factory.returnType as? PsiClassType)?.resolve() ?: return
            if (!WicketComponentTypes.isFormComponent(output) || registration == null && !WicketComponentTypes.ownsMarkup(output)) return
            if (runtime(getter, owners) != getter) fail(getter, "The input accessor is overridden.")
            if (factory is PsiCompiledElement || factory.hasModifierProperty(PsiModifier.STATIC) || factory.parameterList.parametersCount != 2 ||
                factory.parameterList.parameters[0].type.canonicalText != "java.lang.String") fail(factory, "A direct source String/model input factory is required.")
            val id = declarations.id(call.argumentList.expressions.firstOrNull(), emptyMap()) ?: fail(call, "The lazy input ID is not a known String.")
            val inputAt: PsiExpression
            val inputType: PsiClass
            val inputCtor: PsiMethod
            if (registration == null) {
                val created = declarations.returnedCreation(factory, mapOf(factory.parameterList.parameters[0] to Id(id.value, null)))
                if (created.creation.anonymousClass != null || created.creation.qualifier != null) fail(created.creation, "Anonymous or foreign composite input construction is unsupported.")
                inputAt = created.creation
                inputType = created.creation.classReference?.resolve() as? PsiClass ?: fail(created.creation, "The input type is unavailable.")
                inputCtor = created.creation.resolveConstructor() ?: fail(created.creation, "The input constructor is unavailable.")
                val identity = WicketComponentIdentity.trace(created.creation) ?: fail(created.creation, "Input constructor identity is unproved.")
                sources += identity.sources
                if (declarations.id(identity.expression, created.ids)?.let { it.value == id.value && it.origin == null } != true)
                    fail(created.creation, "The input must preserve its supplied ID parameter, not an equal literal.")
            } else {
                registrations.invocation(registration, factory)
                inputAt = registration.reference
                inputCtor = registration.inputConstructor
                inputType = inputCtor.containingClass!!
                // Identity-only scratch PSI. Never added to source graphs or treated as executable code.
                val synthetic = JavaPsiFacade.getElementFactory(type.project).createExpressionFromText(
                    "new ${inputType.qualifiedName}(\"__wicketlens_form_id__\", null)", registration.reference) as PsiNewExpression
                if (synthetic.resolveConstructor() != inputCtor) fail(inputAt, "The input constructor overload is ambiguous.")
                val identity = WicketComponentIdentity.trace(synthetic) ?: fail(inputAt, "The registered input constructor ID is unproved.")
                sources += identity.sources
                if (identity.expression != synthetic.argumentList!!.expressions.first())
                    fail(inputAt, "The registered input must forward its supplied ID, not an equal literal.")
            }
            if (!WicketComponentTypes.isFormComponent(inputType) || !WicketComponentTypes.ownsMarkup(inputType) || inputType is PsiCompiledElement)
                fail(inputAt, "The input must own conventional source composite markup.")
            val guard = getter.body!!.statements.first() as PsiIfStatement
            val body = (guard.thenBranch as PsiBlockStatement).codeBlock
            val attachments = body.statements.filterIsInstance<PsiExpressionStatement>().mapNotNull { it.expression as? PsiMethodCallExpression }.filter { addition ->
                addition.resolveMethod()?.let { it.name == "add" && it.containingClass?.qualifiedName == "org.apache.wicket.MarkupContainer" } == true &&
                    self(addition.methodExpression.qualifierExpression, owners) && addition.argumentList.expressions.singleOrNull()?.let {
                        (it as? PsiReferenceExpression)?.resolve() == field && self(it.qualifierExpression, owners)
                    } == true
            }
            if (attachments.size != 1) fail(getter, "The cache needs one direct attachment to its owner.")
            if (runtime(attachments.single().resolveMethod()!!, owners) != attachments.single().resolveMethod()) fail(getter, "Input attachment is overridden.")
            descendants(getter, PsiReferenceExpression::class.java).filter { it.resolve() == field }.forEach { use ->
                val parent = use.parent
                val inCondition = PsiTreeUtil.isAncestor(guard.condition, use, false)
                val returned = parent is PsiReturnStatement && parent.parent == getter.body && parent.returnValue == use
                val assigned = use == assignment.lExpression
                val member = parent as? PsiReferenceExpression
                val methodCall = member?.parent as? PsiMethodCallExpression
                val queried = member?.qualifierExpression == use && methodCall != null
                val arguments = parent as? PsiExpressionList
                val attached = arguments?.parent == attachments.single() && arguments.expressions.singleOrNull() == use
                if (!inCondition && !returned && !assigned && !queried && !attached) fail(use, "The cache value escapes its direct declaration/configuration flow.")
            }
            descendants(body, PsiMethodCallExpression::class.java).forEach { effect ->
                val target = effect.resolveMethod()
                if (target?.name in (MUTATIONS + setOf("getParent", "getPage", "findParent")) && target?.containingClass?.let { declarations.component(JavaPsiFacade.getElementFactory(type.project).createType(it)) } == true)
                    fail(effect, "The cache has a known structural mutation or enclosing-scope access.")
                if (target?.name == "add" && target.containingClass?.qualifiedName == "org.apache.wicket.MarkupContainer" && effect !in attachments)
                    fail(effect, "The cache has an additional component attachment.")
            }
            val owner = getter.containingClass!!
            val constructor = owner.constructors.singleOrNull() ?: fail(owner, "The wrapper needs one shared structural constructor.")
            owners.takeWhile { it != owner }.forEach { subclass ->
                if (subclass.initializers.isNotEmpty() || subclass.fields.any { declarations.component(it.type) && it.initializer != null })
                    fail(subclass, "Subclass component initialization is unreviewed.")
                if (registration == null) subclass.constructors.forEach { ctor ->
                    if (ctor.body?.statements?.size != 1 || ((ctor.body!!.statements.single() as? PsiExpressionStatement)?.expression as? PsiMethodCallExpression)?.methodExpression?.referenceName !in setOf("super", "this"))
                        fail(ctor, "Subclass constructors must only delegate to the shared wrapper.")
                }
            }
            if (owner.superClass !is PsiCompiledElement) fail(owner, "Source wrapper-base constructor effects need a separate contract.")
            if (owner.initializers.isNotEmpty() || owner.fields.any { declarations.component(it.type) && it.initializer != null && !(it == field && it.initializer is PsiLiteralExpression && (it.initializer as PsiLiteralExpression).value == null) }) fail(owner, "Wrapper component initializer effects are unsupported.")
            if (reports.size >= limits.contexts) throw Bound(factory)
            if (inputType.superClass?.qualifiedName != WicketComponentTypes.FORM_PANEL) fail(inputType, "Only a direct source FormComponentPanel input is supported.")
            if (inputType.initializers.isNotEmpty() || inputType.fields.any { declarations.component(it.type) && it.initializer != null }) fail(inputType, "Input component initializers are unsupported.")
            val key = "form:${type.qualifiedName}:${field.textOffset}" + (registration?.let { ":${this.type.qualifiedName}:${it.reference.textOffset}" } ?: "")
            val root = "$key:root"
            val input = "$key:input"
            val nodes = mutableListOf(ComponentNode(root, null, type.qualifiedName!!, null, location(type.nameIdentifier ?: type),
                confidence = Confidence.CONFIRMED, idRenameSafe = false, template = true))
            nodes += declarations.constructorNodes(constructor, root, "$key:wrapper", limits.nodes - nodes.size, owner, fields = true)
            nodes += ComponentNode(input, id.value, inputType.qualifiedName!!, root, location(inputAt), confidence = Confidence.CONFIRMED,
                idLocation = id.origin?.let(::location), idRenameSafe = false, computedId = true, template = true, sourceSetupExpanded = true)
            nodes += declarations.constructorNodes(inputCtor, input, "$key:inner", limits.nodes - nodes.size, inputType, fields = true)
            val transparent = nodes.filter { node -> node.componentType == "org.apache.wicket.markup.html.TransparentWebMarkupContainer" }.map { it.nodeId }.toSet()
            val extractor = WicketMarkupInheritanceAnalyzer()
            val raw = extractor.analyze(type, WicketComponentTypes.kind(type)).markup ?: fail(type, "Wrapper markup is unavailable.")
            val shell = PageReport(type.qualifiedName!!, root, nodes, raw.file)
            val plan = WicketTransparentMarkup.plan(shell, raw, transparent)
            if (plan.limited || plan.ambiguous.isNotEmpty()) fail(type, "Transparent input scope is ambiguous or bounded.")
            val positions = raw.elements.filter { it.hasWicketId && it.wicketId == id.value }.map { it.location }.toSet()
            if (positions.size != 1) fail(type, "The wrapper input slot is ambiguous.")
            val outer = WicketTransparentMarkup.apply(extractor.analyze(type, WicketComponentTypes.kind(type), positions, transparentLocations = plan.transparentLocations).markup, plan)
                ?: fail(type, "Wrapper markup is unavailable.")
            val inner = extractor.analyze(inputType, WicketComponentTypes.kind(inputType)).markup ?: fail(inputType, "Composite input markup is unavailable.")
            if ((outer.documentDiagnostics + inner.documentDiagnostics).isNotEmpty()) fail(type, "Form markup has document uncertainty.")
            sources += (outer.sourceFiles + inner.sourceFiles).map { it.substringBefore("!/") }
            tick(type)
            val slot = outer.elements.singleOrNull { it.hasWicketId && it.wicketId == id.value && it.participatesInComponentTree }
                ?: fail(type, "A unique active input slot is unavailable.")
            val elements = outer.elements.toMutableList()
            val ids = inner.elements.associate { it.nodeId to "$key:markup:${it.nodeId}" }
            elements += inner.elements.map { element -> element.copy(nodeId = ids.getValue(element.nodeId),
                parentElementId = ids[element.parentElementId] ?: slot.nodeId,
                parentWicketElementId = ids[element.parentWicketElementId] ?: slot.nodeId, markupProviderNodeId = input) }
            val hooks = hierarchy(inputType).flatMap { sourceOwner -> sourceOwner.methods.filter { method ->
                method.name in setOf("convertInput", "checkRequired", "validate") && method.findDeepestSuperMethods().any {
                    it.containingClass?.qualifiedName == WicketComponentTypes.FORM_COMPONENT
                }
            } }.map { method -> FormHookSource(when(method.name) { "convertInput" -> "CONVERSION"; "checkRequired" -> "REQUIRED_CHECK"; else -> "VALIDATION" }, location(method)) }.toMutableList()
            descendants(inputCtor, PsiMethodCallExpression::class.java).filter { validation ->
                validation.resolveMethod()?.let { it.name == "add" && it.containingClass?.qualifiedName == WicketComponentTypes.FORM_COMPONENT } == true &&
                    validation.argumentList.expressions.any { (it.type as? PsiClassType)?.resolve()?.let { arg -> arg.qualifiedName == "org.apache.wicket.validation.IValidator" || InheritanceUtil.isInheritor(arg, "org.apache.wicket.validation.IValidator") } == true }
            }.forEach { hooks += FormHookSource("VALIDATOR_DECLARATION", location(it)) }
            val initialization = owners.firstNotNullOfOrNull { sourceOwner -> sourceOwner.methods.singleOrNull { it.name == "onInitialize" && it.parameterList.parametersCount == 0 } }
                ?.let { method -> descendants(method, PsiMethodCallExpression::class.java).singleOrNull { call -> call.resolveMethod() == getter &&
                    PsiTreeUtil.getParentOfType(call,PsiMethod::class.java) == method && generateSequence(call.parent) { it.parent }.takeWhile { it != method }.none { it is PsiLambdaExpression } } }?.let(::location)
            val warning = Diagnostic(DiagnosticCode.UNRESOLVED_SCOPE,
                "Source-declared lazy form structure only. Constructor/configuration effects, model writes, converters, required state, validation outcomes, behavior binding, callbacks and later tree state remain unexpanded; this is not a current runtime hierarchy.", location(factory))
            reports += WicketGraphMatcher().match(PageReport(this.type.qualifiedName!!, root, nodes.map { node ->
                if (node.nodeId == input) node.copy(ownedMarkupPath = inner.file) else node
            }, outer.file, warnings = listOf(warning), scopeComplete = false,
                markup = outer.copy(elements = elements, scopeComplete = false, sourceFiles = (outer.sourceFiles + inner.sourceFiles).distinct()),
                javaSourceFiles = sources.sorted(), formTemplate = FormInputTemplate(key, type.qualifiedName!!, inputType.qualifiedName!!, input, id.value,
                    location(field), location(getter), location(factory), initialization, hooks,
                    registration?.let { FormInputRegistration(this.type.qualifiedName!!, location(it.creation), location(it.reference),
                        location(inputCtor), location(it.field)) })))
        }
    }
}
private fun location(element: PsiElement) = SourceLocation(element.containingFile.virtualFile.path, element.textOffset)
private val MUTATIONS = setOf("remove", "removeAll", "replace", "replaceWith", "addOrReplace", "queue")
