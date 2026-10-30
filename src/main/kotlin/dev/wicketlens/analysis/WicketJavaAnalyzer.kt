package dev.wicketlens.analysis

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.ProgressManager
import com.intellij.psi.JavaRecursiveElementWalkingVisitor
import com.intellij.psi.JavaTokenType
import com.intellij.psi.PsiAssignmentExpression
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiDeclarationStatement
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiEllipsisType
import com.intellij.psi.PsiExpression
import com.intellij.psi.PsiExpressionList
import com.intellij.psi.PsiExpressionStatement
import com.intellij.psi.PsiErrorElement
import com.intellij.psi.PsiField
import com.intellij.psi.PsiLiteralExpression
import com.intellij.psi.PsiLocalVariable
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiMethodCallExpression
import com.intellij.psi.PsiMethodReferenceExpression
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiMember
import com.intellij.psi.PsiModifier
import com.intellij.psi.PsiNewExpression
import com.intellij.psi.PsiEmptyStatement
import com.intellij.psi.PsiLambdaExpression
import com.intellij.psi.PsiParenthesizedExpression
import com.intellij.psi.PsiReferenceExpression
import com.intellij.psi.PsiSuperExpression
import com.intellij.psi.PsiThisExpression
import com.intellij.psi.PsiTypeCastExpression
import com.intellij.psi.util.InheritanceUtil
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.PsiUtil
import dev.wicketlens.model.ComponentNode
import dev.wicketlens.model.Confidence
import dev.wicketlens.model.Diagnostic
import dev.wicketlens.model.DiagnosticCode
import dev.wicketlens.model.PageReport
import dev.wicketlens.model.SourceLocation

/**
 * Read-only, stateless Java discovery. Call under a smart read action, off the UI
 * thread for real projects. The returned report contains no live PSI references.
 */
class WicketJavaAnalyzer {
    fun analyze(page: PsiClass): PageReport? {
        ApplicationManager.getApplication().assertReadAccessAllowed()
        ProgressManager.checkCanceled()
        if (!WicketComponentTypes.ownsMarkup(page)) {
            return null
        }
        val className = page.qualifiedName ?: return null
        val rootId = "page"
        val root = ComponentNode(
            nodeId = rootId,
            wicketId = null,
            componentType = className,
            parentNodeId = null,
            javaLocation = location(page.nameIdentifier ?: page),
            confidence = Confidence.CONFIRMED,
        )
        val expansion = WicketSourceSetupAnalyzer().analyze(page, rootId)
        val discovery = if (expansion == null) Discovery(page, rootId) else null
        val collected = expansion?.nodes ?: requireNotNull(discovery).collect()
        val repeaters = if (expansion != null) emptySet() else collected.filter { node ->
            JavaPsiFacade.getInstance(page.project).findClasses(node.componentType, page.resolveScope).singleOrNull()
                ?.let(WicketRepeaterContract::kind) != null
        }.map { it.nodeId }.toSet()
        val byId = collected.associateBy { it.nodeId }
        val nodes = collected.map { node ->
            if (generateSequence(node.parentNodeId) { byId[it]?.parentNodeId }.take(collected.size).none { it in repeaters }) node
            else node.copy(confidence = Confidence.UNRESOLVED, diagnostics = node.diagnostics + Diagnostic(DiagnosticCode.UNRESOLVED_SCOPE,
                "The repeater item scope is not proved; runtime item IDs cannot be matched as ordinary markup IDs.", node.javaLocation))
        }

        return PageReport(
            qualifiedClassName = className,
            rootNodeId = rootId,
            nodes = (listOf(root) + nodes).sortedWith(compareBy({ it.javaLocation.offset }, { it.nodeId })),
            warnings = listOf(
                Diagnostic(
                    DiagnosticCode.PARTIAL_ANALYSIS,
                    "Partial Java discovery: only reviewed constructors and bounded straight-line source setup are reconstructed; " +
                        "unsupported fields, aliases, dynamic dispatch, and runtime control flow remain unresolved.",
                    location(page),
                ),
            ) + (discovery?.warnings.orEmpty() + expansion?.warnings.orEmpty()).distinct(),
            javaSourceFiles = (expansion?.sources.orEmpty() + listOf(location(page).file) +
                PsiTreeUtil.findChildrenOfType(page, PsiNewExpression::class.java).flatMap { WicketIdConstructors.trace(it)?.sources.orEmpty() }).distinct().sorted(),
        )
    }

    private fun isWicketComponentAdd(call: PsiMethodCallExpression): Boolean {
        if (call.methodExpression.referenceName != "add") return false
        val method = call.resolveMethod() ?: return false
        if (method.containingClass?.qualifiedName != MARKUP_CONTAINER) return false
        val parameter = method.parameterList.parameters.singleOrNull() ?: return false
        val type = parameter.type as? PsiEllipsisType ?: return false
        return (type.componentType as? PsiClassType)?.resolve()?.qualifiedName == COMPONENT
    }

    /** All PSI-keyed state is confined to this invocation and discarded with it. */
    private inner class Discovery(private val page: PsiClass, private val rootId: String) {
        private val candidates = linkedMapOf<PsiExpression, Candidate>()
        private val stableInitializers = mutableMapOf<PsiLocalVariable, PsiNewExpression?>()
        private val fieldInitializers = mutableMapOf<PsiField, PsiNewExpression?>()
        private var fieldScopesSupported: Boolean? = null
        // Source-file scope includes nestmate/outer-class accesses to private fields as well.
        private val fieldReferences by lazy {
            val references = mutableMapOf<PsiField, MutableList<PsiReferenceExpression>>()
            page.containingFile.accept(object : JavaRecursiveElementWalkingVisitor() {
                override fun visitReferenceExpression(expression: PsiReferenceExpression) {
                    ProgressManager.checkCanceled()
                    (expression.resolve() as? PsiField)?.let { references.getOrPut(it) { mutableListOf() } += expression }
                    super.visitReferenceExpression(expression)
                }
            })
            references
        }
        private val linearScopes = mutableMapOf<PsiMethod, Boolean>()
        private val syntaxErrors = PsiTreeUtil.findChildrenOfType(page.containingFile, PsiErrorElement::class.java)
        private val rootScopeEffects = mutableListOf<PsiElement>()
        private val initializer = page.methods.singleOrNull(::isInitializationMethod)
        private var initializationDelegation: PsiMethodCallExpression? = null
        private var initializationUncertain = false
        val warnings = mutableListOf<Diagnostic>()

        private fun gap(message: String, element: PsiElement) {
            warnings += Diagnostic(DiagnosticCode.UNRESOLVED_SCOPE, message, location(element))
        }

        fun collect(): List<ComponentNode> {
            syntaxErrors.forEach {
                ProgressManager.checkCanceled()
                gap("Java syntax is incomplete: ${it.errorDescription}", it)
            }
            if (page.superClass?.qualifiedName !in WicketComponentTypes.markupRoots) {
                gap("Custom inherited component setup is not expanded.", page.extendsList ?: page)
            }
            if (page.constructors.size > 1) gap("Alternative constructors are not reconciled into one runtime scope.", page)
            collectRootScopeEffects()
            prepareInitializationScopes()
            page.accept(object : JavaRecursiveElementWalkingVisitor() {
                override fun visitClass(aClass: PsiClass) {
                    ProgressManager.checkCanceled()
                    // Root effects were checked separately; nested children still have their own scope.
                    if (aClass == page) super.visitClass(aClass)
                }

                override fun visitMethodCallExpression(call: PsiMethodCallExpression) {
                    ProgressManager.checkCanceled()
                    if (isWicketComponentAdd(call)) record(call) else recordOpaqueCall(call)
                    super.visitMethodCallExpression(call)
                }

                override fun visitNewExpression(expression: PsiNewExpression) {
                    val type = expression.anonymousClass ?: expression.classReference?.resolve() as? PsiClass
                    if (type?.let(WicketRepeaterContract::kind) != null) {
                        gap("Repeater population could not be proved as a bounded item template; child scopes remain unresolved.", expression)
                    }
                    if (WicketIdConstructors.trace(expression)?.inlineChildren == true) {
                        gap("A custom FormComponent adds children in its owner's markup; inline child setup is not expanded.", expression)
                    }
                    if (expression.anonymousClass?.let { InheritanceUtil.isInheritor(it, COMPONENT) } == true) {
                        gap("Anonymous component bodies may declare children outside the supported analysis scope.", expression)
                    }
                    super.visitNewExpression(expression)
                }

                override fun visitThisExpression(expression: PsiThisExpression) {
                    val parent = expression.parent
                    // Bound method references are covered by collectRootScopeEffects, including pruned bodies.
                    if (isRootReceiver(expression, page) &&
                        !(parent is PsiReferenceExpression && parent.qualifierExpression == expression)) {
                        gap("The analyzed root escapes to code whose effects are not reconstructed.", expression)
                    }
                    super.visitThisExpression(expression)
                }
            })
            return assembleGraph()
        }

        /** Inspect root captures before pruning nested classes from component discovery. */
        private fun collectRootScopeEffects() {
            fun effect(element: PsiElement, message: String) {
                rootScopeEffects += element
                gap(message, element)
            }
            fun nestedCapture(element: PsiElement) = effect(element,
                "Nested class code accesses the analyzed root; its effects are not reconstructed.")
            page.accept(object : JavaRecursiveElementWalkingVisitor() {
                override fun visitElement(element: PsiElement) {
                    ProgressManager.checkCanceled()
                    super.visitElement(element)
                }

                override fun visitMethodReferenceExpression(expression: PsiMethodReferenceExpression) {
                    val receiver = expression.qualifierExpression?.let(::unparenthesize)
                    // An unqualified super in a nested class belongs to that class.
                    val nestedSuper = receiver is PsiSuperExpression && receiver.qualifier == null &&
                        PsiTreeUtil.getParentOfType(expression, PsiClass::class.java) != page
                    if (receiver != null && !nestedSuper && isRootReceiver(receiver, page)) {
                        effect(expression, "A method reference bound to the analyzed root escapes to code whose effects are not reconstructed.")
                    }
                    super.visitMethodReferenceExpression(expression)
                }

                override fun visitThisExpression(expression: PsiThisExpression) {
                    if (PsiTreeUtil.getParentOfType(expression, PsiClass::class.java) != page && isRootReceiver(expression, page)) {
                        nestedCapture(expression)
                    }
                    super.visitThisExpression(expression)
                }

                override fun visitSuperExpression(expression: PsiSuperExpression) {
                    if (expression.qualifier != null &&
                        PsiTreeUtil.getParentOfType(expression, PsiClass::class.java) != page && isRootReceiver(expression, page)) {
                        nestedCapture(expression)
                    }
                    super.visitSuperExpression(expression)
                }

                override fun visitReferenceExpression(expression: PsiReferenceExpression) {
                    if (PsiTreeUtil.getParentOfType(expression, PsiClass::class.java) != page && isImplicitRootReference(expression)) {
                        nestedCapture(expression)
                    }
                    super.visitReferenceExpression(expression)
                }
            })
        }

        private fun isImplicitRootReference(expression: PsiReferenceExpression): Boolean {
            if (expression.qualifierExpression != null) return false
            val member = expression.resolve() as? PsiMember ?: return false
            if (member.hasModifierProperty(PsiModifier.STATIC)) return false
            val owner = member.containingClass ?: return false
            var context = PsiTreeUtil.getParentOfType(expression, PsiClass::class.java)
            while (context != null) {
                ProgressManager.checkCanceled()
                // The nearest enclosing class that owns/inherits the member supplies its receiver.
                if (context == owner || context.isInheritor(owner, true)) return context == page
                context = PsiTreeUtil.getParentOfType(context, PsiClass::class.java)
            }
            return false
        }

        /** Only an actual override of Component.onInitialize belongs to the supported phase. */
        private fun isInitializationMethod(method: PsiMethod): Boolean =
            method.name == "onInitialize" && method.parameterList.parametersCount == 0 &&
                method.returnType?.canonicalText == "void" &&
                !method.hasModifierProperty(PsiModifier.STATIC) &&
                (method.hasModifierProperty(PsiModifier.PUBLIC) || method.hasModifierProperty(PsiModifier.PROTECTED)) &&
                (method.containingClass?.qualifiedName == COMPONENT ||
                    method.findDeepestSuperMethods().any { it.containingClass?.qualifiedName == COMPONENT })

        /** Validate both phases before recording any edges, including earlier constructor additions. */
        private fun prepareInitializationScopes() {
            var ancestor = page.superClass
            var inheritedInitializer: PsiMethod? = null
            while (ancestor != null) {
                ProgressManager.checkCanceled()
                inheritedInitializer = ancestor.methods.firstOrNull(::isInitializationMethod)
                if (inheritedInitializer != null) break
                ancestor = ancestor.superClass
            }
            if (inheritedInitializer?.containingClass?.qualifiedName !in FRAMEWORK_INITIALIZERS) {
                initializationUncertain = true
                gap("Inherited onInitialize effects are not expanded.", page.extendsList ?: page)
            }
            val method = initializer
            if (method != null) {
                val first = method.body?.statements?.firstOrNull { it !is PsiEmptyStatement }
                val call = (first as? PsiExpressionStatement)?.expression as? PsiMethodCallExpression
                if (call != null && call.methodExpression.referenceName == "onInitialize" &&
                    call.methodExpression.qualifierExpression is PsiSuperExpression &&
                    (call.methodExpression.qualifierExpression as PsiSuperExpression).qualifier == null &&
                    call.argumentList.expressionCount == 0 && call.resolveMethodGenerics().isValidResult &&
                    call.resolveMethod()?.containingClass?.qualifiedName in FRAMEWORK_INITIALIZERS) {
                    initializationDelegation = call
                } else {
                    gap("onInitialize must begin with one direct, reviewed super.onInitialize() call.", method)
                    initializationUncertain = true
                }
                val scopes = page.constructors.toList() + method
                // Neither constructor alternatives/delegation nor custom inherited setup is reconciled.
                if (page.constructors.size > 1 || page.superClass?.qualifiedName !in WicketComponentTypes.markupRoots) {
                    initializationUncertain = true
                }
                scopes.forEach { scope ->
                    if (!isLinearScope(scope) || hasUnsupportedInitializationEffects(scope)) initializationUncertain = true
                }
            }
            if (initializationUncertain) {
                gap("Constructor and onInitialize attachment scopes are unresolved because initialization effects are not established.",
                    method ?: page)
            }
        }

        private fun hasUnsupportedInitializationEffects(method: PsiMethod): Boolean {
            return method.body?.let { hasUnsupportedRootEffects(it, method) } == true
        }

        private fun hasUnsupportedRootEffects(element: PsiElement, method: PsiMethod? = null): Boolean {
            var unsafe = false
            element.accept(object : JavaRecursiveElementWalkingVisitor() {
                override fun visitClass(aClass: PsiClass) {
                    // Nested root captures were already collected before pruning.
                }

                override fun visitMethodCallExpression(call: PsiMethodCallExpression) {
                    ProgressManager.checkCanceled()
                    if (isOpaqueRootCall(call) || call.methodExpression.referenceName == "this" ||
                        (isWicketComponentAdd(call) && (method == null || !isDirectStatement(call, method)))) unsafe = true
                    super.visitMethodCallExpression(call)
                }

                override fun visitThisExpression(expression: PsiThisExpression) {
                    val parent = expression.parent
                    if (isRootReceiver(expression, page) &&
                        !(parent is PsiReferenceExpression && parent.qualifierExpression == expression)) unsafe = true
                    super.visitThisExpression(expression)
                }
            })
            return unsafe
        }

        private fun isOpaqueRootCall(call: PsiMethodCallExpression): Boolean {
            val name = call.methodExpression.referenceName
            if (call == initializationDelegation || name == "super" ||
                !isRootReceiver(call.methodExpression.qualifierExpression, page) || isWicketComponentAdd(call)) return false
            val method = call.resolveMethod()
            if (method?.hasModifierProperty(PsiModifier.STATIC) == true) return false
            val owner = method?.containingClass
            return method == null || owner == page || owner?.qualifiedName == COMPONENT ||
                owner?.let { InheritanceUtil.isInheritor(it, COMPONENT) } == true
        }

        private fun recordOpaqueCall(call: PsiMethodCallExpression) {
            val name = call.methodExpression.referenceName
            if (name == "this") {
                gap("Constructor delegation is not expanded into a runtime component scope.", call)
                return
            }
            if (isOpaqueRootCall(call)) {
                gap("The effects of root call '${name ?: "<unresolved>"}' on component scope are not reconstructed.", call)
            }
        }

        private fun isLinearScope(method: PsiMethod): Boolean = linearScopes.getOrPut(method) {
            val body = method.body
            val simple = body != null && body.statements.all {
                it is PsiDeclarationStatement || it is PsiExpressionStatement || it is PsiEmptyStatement
            }
            val mutations = body?.let { PsiTreeUtil.findChildrenOfType(it, PsiMethodCallExpression::class.java) }
                .orEmpty().any { call ->
                    ProgressManager.checkCanceled()
                    (call.methodExpression.referenceName in STRUCTURAL_METHODS ||
                        call.parent is PsiExpressionStatement && isOpaqueRootCall(call)) &&
                        PsiTreeUtil.getParentOfType(call, PsiClass::class.java) == page &&
                        PsiTreeUtil.getParentOfType(call, PsiMethod::class.java) == method &&
                        PsiTreeUtil.getParentOfType(call, PsiLambdaExpression::class.java) == null &&
                        isRootReceiver(call.methodExpression.qualifierExpression, page)
                }
            val escaped = body != null && rootScopeEffects.any { PsiTreeUtil.isAncestor(body, it, false) }
            if (!simple || mutations || escaped) {
                val scope = if (method.isConstructor) "Constructor" else "onInitialize"
                gap("$scope control flow or unsupported root effects make its attachment scope unresolved.", method)
            }
            simple && !mutations && !escaped && syntaxErrors.isEmpty()
        }

        private fun isAttachmentMethod(method: PsiMethod): Boolean =
            method.containingClass == page && (method.isConstructor || method == initializer)

        private fun isDirectStatement(call: PsiMethodCallExpression, method: PsiMethod): Boolean =
            call.parent is PsiExpressionStatement && call.parent.parent == method.body

        private fun record(call: PsiMethodCallExpression) {
            val method = PsiTreeUtil.getParentOfType(call, PsiMethod::class.java)
            val directStatement = method != null && isAttachmentMethod(method) && isDirectStatement(call, method)
            val receiver = call.methodExpression.qualifierExpression?.let(::unparenthesize)
            fun isField(expression: PsiExpression?) = (expression as? PsiReferenceExpression)?.resolve() is PsiField
            val usesField = isField(receiver) || call.argumentList.expressions.any { isField(unparenthesize(it)) }
            val straight = directStatement && isLinearScope(requireNotNull(method)) && !initializationUncertain &&
                (!usesField || fieldScopeIsSupported())
            val parentId = when {
                isRootReceiver(receiver, page) -> rootId
                else -> receiver?.let(::stableCreation)?.let { candidate(it).node.nodeId }
            }
            call.argumentList.expressions.forEach { argument ->
                ProgressManager.checkCanceled()
                val expression = unparenthesize(argument)
                val creation = stableCreation(expression)
                candidate(creation ?: expression).attachments += Attachment(
                    // Retain a proven local edge even when phase execution is
                    // uncertain. Root attachment and confidence remain unresolved.
                    parentId = if (straight || (directStatement && !isRootReceiver(receiver, page))) parentId else null,
                    straight = straight,
                    source = location(call),
                )
            }
        }

        private fun candidate(expression: PsiExpression): Candidate = candidates.getOrPut(expression) {
            Candidate(describe(expression))
        }

        /** Bind references to constructions, never to variable names or guessed field IDs. */
        private fun stableCreation(expression: PsiExpression): PsiNewExpression? {
            val reference = expression as? PsiReferenceExpression ?: return null
            val declaration = reference.resolve()
            if (declaration is PsiField) {
                if (!fieldInitializers.containsKey(declaration)) fieldInitializers[declaration] = fieldCreation(declaration, reference)
                return fieldInitializers[declaration]
            }
            val variable = declaration as? PsiLocalVariable ?: return null
            if (!stableInitializers.containsKey(variable)) {
                val method = PsiTreeUtil.getParentOfType(variable, PsiMethod::class.java)
                val declaration = variable.parent as? PsiDeclarationStatement
                val initializer = variable.initializer?.let(::unparenthesize) as? PsiNewExpression
                val stable = method != null && isAttachmentMethod(method) &&
                    declaration?.parent == method.body && initializer != null && !hasUnsupportedUses(variable, method)
                stableInitializers[variable] = if (stable) initializer else null
            }
            return stableInitializers[variable]?.takeIf { it.textOffset < reference.textOffset }
        }

        private fun fieldReferenceOnRoot(reference: PsiReferenceExpression): Boolean {
            val qualifier = reference.qualifierExpression?.let(::unparenthesize)
            return qualifier == null || (qualifier is PsiThisExpression && isRootReceiver(qualifier, page))
        }

        private fun fieldCreation(field: PsiField, use: PsiReferenceExpression): PsiNewExpression? {
            fun unsupported(message: String, source: PsiElement = field): PsiNewExpression? {
                gap("Field '${field.name}' is unresolved: $message", source)
                return null
            }
            if (field.containingClass != page) return unsupported("inherited fields are not tracked.", use)
            if (!field.hasModifierProperty(PsiModifier.PRIVATE) || !field.hasModifierProperty(PsiModifier.FINAL) ||
                field.hasModifierProperty(PsiModifier.STATIC)) {
                return unsupported("only private final instance fields are tracked.")
            }
            if (page.constructors.size > 1) return unsupported("alternative constructors are not reconciled.")
            val references = fieldReferences[field].orEmpty()
            val writes = references.filter { PsiUtil.isAccessedForWriting(it) }
            val constructor = page.constructors.singleOrNull()
            var assignment: PsiAssignmentExpression? = null
            val creation = if (field.initializer != null) {
                if (writes.isNotEmpty()) return unsupported("a declaration-initialized field has additional writes.", writes.first())
                field.initializer?.let(::unparenthesize) as? PsiNewExpression
                    ?: return unsupported("the declaration must initialize directly with new.")
            } else {
                val write = writes.singleOrNull() ?: return unsupported("one direct constructor assignment is required.")
                assignment = write.parent as? PsiAssignmentExpression
                if (constructor == null || assignment?.lExpression != write || assignment.operationTokenType != JavaTokenType.EQ ||
                    !fieldReferenceOnRoot(write) || PsiTreeUtil.getParentOfType(write, PsiClass::class.java) != page ||
                    PsiTreeUtil.getParentOfType(write, PsiMethod::class.java) != constructor ||
                    assignment.parent !is PsiExpressionStatement || assignment.parent.parent != constructor.body ||
                    !isLinearScope(constructor)) {
                    return unsupported("the assignment must be a direct statement in one supported constructor.", write)
                }
                assignment.rExpression?.let(::unparenthesize) as? PsiNewExpression
                    ?: return unsupported("the constructor assignment must initialize directly with new.", write)
            }
            references.forEach { reference ->
                ProgressManager.checkCanceled()
                if (assignment?.lExpression == reference) return@forEach
                val method = PsiTreeUtil.getParentOfType(reference, PsiMethod::class.java)
                val call = componentAddUse(reference)
                if (!fieldReferenceOnRoot(reference) || PsiTreeUtil.getParentOfType(reference, PsiClass::class.java) != page ||
                    method == null || !isAttachmentMethod(method) ||
                    PsiTreeUtil.getParentOfType(reference, PsiLambdaExpression::class.java) != null ||
                    call == null || !isWicketComponentAdd(call)) {
                    return unsupported("every read must remain in supported constructor/onInitialize additions on this instance.", reference)
                }
                if (method.isConstructor) {
                    val explicitSuper = method.body?.statements.orEmpty().filterIsInstance<PsiExpressionStatement>()
                        .mapNotNull { it.expression as? PsiMethodCallExpression }
                        .firstOrNull { it.methodExpression.referenceName == "super" }
                    if ((assignment != null && reference.textOffset < assignment.textRange.endOffset) ||
                        (explicitSuper != null && reference.textOffset < explicitSuper.textRange.endOffset)) {
                        return unsupported("a read occurs before field initialization is established.", reference)
                    }
                }
            }
            return creation
        }

        /** A final reference does not make constructor, field-initializer, or escaped-root effects safe. */
        private fun fieldScopeIsSupported(): Boolean {
            fieldScopesSupported?.let { return it }
            var supported = true
            page.constructors.forEach { method ->
                ProgressManager.checkCanceled()
                val first = method.body?.statements?.firstOrNull { it !is PsiEmptyStatement }
                val superCall = method.body?.statements.orEmpty().filterIsInstance<PsiExpressionStatement>()
                    .mapNotNull { it.expression as? PsiMethodCallExpression }
                    .firstOrNull { it.methodExpression.referenceName == "super" }
                if (!isLinearScope(method) || hasUnsupportedInitializationEffects(method) ||
                    (superCall != null && superCall.parent != first)) supported = false
            }
            page.fields.filter { !it.hasModifierProperty(PsiModifier.STATIC) }.forEach { field ->
                ProgressManager.checkCanceled()
                field.initializer?.let { expression ->
                    if (hasUnsupportedRootEffects(expression) || rootScopeEffects.any { PsiTreeUtil.isAncestor(expression, it, false) }) {
                        supported = false
                        gap("Field initializer effects on the analyzed root are not reconstructed.", expression)
                    }
                }
            }
            page.initializers.filter { !it.hasModifierProperty(PsiModifier.STATIC) }.forEach {
                supported = false
                gap("Instance initializer blocks are outside supported field setup.", it)
            }
            if (!supported) gap("Component-field attachment scopes have unsupported initialization effects.", page)
            fieldScopesSupported = supported
            return supported
        }

        private fun componentAddUse(expression: PsiReferenceExpression): PsiMethodCallExpression? {
            var usage: PsiElement = expression
            while (usage.parent is PsiParenthesizedExpression) usage = usage.parent
            val parent = usage.parent
            return when {
                parent is PsiExpressionList -> parent.parent as? PsiMethodCallExpression
                parent is PsiReferenceExpression && parent.qualifierExpression == usage -> parent.parent as? PsiMethodCallExpression
                else -> null
            }
        }

        private fun hasUnsupportedUses(variable: PsiLocalVariable, method: PsiMethod): Boolean {
            var unsafe = false
            method.body?.accept(object : JavaRecursiveElementWalkingVisitor() {
                override fun visitReferenceExpression(expression: PsiReferenceExpression) {
                    ProgressManager.checkCanceled()
                    if (!unsafe && expression.resolve() == variable) {
                        val call = componentAddUse(expression)
                        // Aliases, casts, escapes, other method calls, and nested-class
                        // captures require analysis beyond the current method scope.
                        if (PsiUtil.isAccessedForWriting(expression) || call == null || !isWicketComponentAdd(call) ||
                            PsiTreeUtil.getParentOfType(expression, PsiClass::class.java) != page) {
                            unsafe = true
                        }
                    }
                    if (!unsafe) super.visitReferenceExpression(expression)
                }
            })
            return unsafe
        }

        private fun assembleGraph(): List<ComponentNode> {
            val byId = candidates.values.associateBy { it.node.nodeId }
            val parents = byId.mapValues { (_, candidate) -> candidate.attachments.singleOrNull()?.parentId }
                .toMutableMap()
            val parentIssues = mutableMapOf<String, String>()
            byId.forEach { (id, candidate) ->
                ProgressManager.checkCanceled()
                if (candidate.attachments.size > 1) {
                    parentIssues[id] = "This instance has multiple additions; a unique parent is not established."
                }
            }

            // Remove cycle edges before publishing a graph; preserve the remaining known edges.
            byId.keys.forEach { start ->
                val path = linkedSetOf<String>()
                var current: String? = start
                while (current != null && current != rootId) {
                    ProgressManager.checkCanceled()
                    if (!path.add(current)) {
                        path.dropWhile { it != current }.forEach { cyclicId ->
                            parents[cyclicId] = null
                            parentIssues[cyclicId] = "Container additions form a cycle; parentage is unresolved."
                        }
                        break
                    }
                    current = parents[current]
                }
            }

            fun parentIsResolved(id: String): Boolean {
                var current = parents[id]
                while (current != null && current != rootId) {
                    ProgressManager.checkCanceled()
                    val ancestor = byId[current] ?: return false
                    if (ancestor.node.diagnostics.isNotEmpty() || ancestor.attachments.any { !it.straight }) return false
                    current = parents[current]
                }
                return current == rootId
            }

            return byId.map { (id, candidate) ->
                ProgressManager.checkCanceled()
                val diagnostics = candidate.node.diagnostics.toMutableList()
                candidate.attachments.filter { !it.straight }.forEach {
                    diagnostics += Diagnostic(
                        DiagnosticCode.UNRESOLVED_SCOPE,
                        "The addition's constructor, onInitialize, or control-flow scope is unresolved.",
                        it.source,
                    )
                }
                if (!parentIsResolved(id)) {
                    diagnostics += Diagnostic(
                        DiagnosticCode.UNRESOLVED_PARENT,
                        parentIssues[id] ?: "The component's full parent context under the analyzed root is unresolved.",
                        candidate.attachments.firstOrNull()?.source ?: candidate.node.javaLocation,
                    )
                }
                candidate.node.copy(
                    parentNodeId = parents[id],
                    confidence = if (diagnostics.isEmpty()) Confidence.CONFIRMED else Confidence.UNRESOLVED,
                    diagnostics = diagnostics.toList(),
                )
            }
        }
    }

    private data class Candidate(val node: ComponentNode, val attachments: MutableList<Attachment> = mutableListOf())

    private data class Attachment(val parentId: String?, val straight: Boolean, val source: SourceLocation)

    private fun unparenthesize(expression: PsiExpression): PsiExpression {
        var current = expression
        while (current is PsiParenthesizedExpression) {
            ProgressManager.checkCanceled()
            current = current.expression ?: break
        }
        return current
    }

    private fun describe(argument: PsiExpression): ComponentNode {
        val diagnostics = mutableListOf<Diagnostic>()
        val source = location(argument)
        val creation = argument as? PsiNewExpression
        val trace = creation?.let(WicketIdConstructors::trace)
        val id = if (trace != null) {
            val idExpression = trace.expression
            val literal = (idExpression as? PsiLiteralExpression)?.value as? String
            if (literal == null) {
                diagnostics += Diagnostic(
                    DiagnosticCode.UNRESOLVED_DYNAMIC_ID,
                    "Only literal string component IDs are supported.",
                    location(idExpression),
                )
            } else if (literal.isEmpty() || literal.any { it == ':' || it == '~' }) {
                diagnostics += Diagnostic(
                    DiagnosticCode.UNRESOLVED_ID,
                    "The literal component ID is empty or contains reserved path characters.",
                    idExpression.let(::location),
                )
            }
            literal
        } else {
            diagnostics += Diagnostic(
                if (argument is PsiReferenceExpression) DiagnosticCode.UNRESOLVED_VARIABLE
                else DiagnosticCode.UNRESOLVED_CONSTRUCTOR,
                if (argument is PsiReferenceExpression)
                    "Only supported constructor/onInitialize locals and private final instance fields used solely in supported additions are tracked."
                else "ID semantics are not verified for this expression or constructor.",
                source,
            )
            null
        }

        val type = (creation?.classReference?.resolve() as? PsiClass)?.qualifiedName
            ?: creation?.anonymousClass?.baseClassType?.resolve()?.qualifiedName
            ?: (argument.type as? PsiClassType)?.resolve()?.qualifiedName
            ?: argument.type?.canonicalText ?: "<unresolved>"
        return ComponentNode(
            nodeId = "java:${argument.textOffset}",
            wicketId = id,
            componentType = type,
            parentNodeId = null,
            javaLocation = source,
            confidence = if (diagnostics.isEmpty()) Confidence.CONFIRMED else Confidence.UNRESOLVED,
            diagnostics = diagnostics.toList(),
            idLocation = trace?.expression?.let(::location),
            idRenameSafe = trace?.shared != true,
        )
    }

    private fun location(element: PsiElement): SourceLocation = SourceLocation(
        element.containingFile.virtualFile?.path ?: element.containingFile.name,
        element.textOffset,
    )

    private fun isRootReceiver(receiver: PsiExpression?, page: PsiClass): Boolean {
        var target = receiver?.let(::unparenthesize)
        while (target is PsiTypeCastExpression) {
            ProgressManager.checkCanceled()
            target = target.operand?.let(::unparenthesize)
        }
        return when (target) {
            null -> receiver == null
            is PsiThisExpression -> (target.type as? PsiClassType)?.resolve() == page
            is PsiSuperExpression -> target.qualifier == null || target.qualifier?.resolve() == page
            else -> false
        }
    }

    private companion object {
        const val COMPONENT = "org.apache.wicket.Component"
        const val MARKUP_CONTAINER = "org.apache.wicket.MarkupContainer"
        const val WEB_PAGE = "org.apache.wicket.markup.html.WebPage"
        const val PANEL = "org.apache.wicket.markup.html.panel.Panel"
        // Reviewed Wicket 10.11.0 chain: Component sets its verification flag; MarkupContainer
        // delegates and dequeues; Page delegates and touches the page manager. Queueing and
        // framework-created markup children remain outside the supported static subset.
        val FRAMEWORK_INITIALIZERS = setOf(COMPONENT, MARKUP_CONTAINER, "org.apache.wicket.Page")
        val STRUCTURAL_METHODS = setOf("remove", "removeAll", "replace", "addOrReplace", "queue", "autoAdd")

    }
}
