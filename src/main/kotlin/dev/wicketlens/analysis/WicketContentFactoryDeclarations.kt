package dev.wicketlens.analysis

import com.intellij.psi.*
import com.intellij.psi.util.InheritanceUtil
import com.intellij.psi.util.PsiTreeUtil
import dev.wicketlens.model.*

/** Source declarations in a fresh fragment factory, independent of model/configuration execution. */
internal class WicketContentFactoryDeclarations(
    private val page: PsiClass,
    private val rootNodeId: String,
    private val nodeLimit: Int,
    private val tick: (PsiElement) -> Unit,
    private val source: (PsiElement) -> Unit,
    private val sources: MutableSet<String>,
    private val descendants: (PsiElement) -> List<PsiElement>,
    private val creation: (PsiExpression) -> PsiNewExpression?,
    private val fail: (PsiElement, String) -> Nothing,
    private val bound: (PsiElement) -> Nothing,
) {
    data class Result(val owner: PageReport, val fragment: FragmentReference,
        val configurationLocations: List<SourceLocation>, val callbackLocations: List<SourceLocation>)

    fun analyze(lambda: PsiLambdaExpression): Result {
        source(lambda)
        val parameter = lambda.parameterList.parameters.singleOrNull() ?: fail(lambda, "A unary source lambda is required.")
        val body = lambda.body as? PsiCodeBlock ?: fail(lambda, "Content factories require a straight-line fragment block.")
        val terminal = body.statements.lastOrNull() as? PsiReturnStatement ?: fail(lambda, "The factory must return its fragment local.")
        val returned = terminal.returnValue as? PsiReferenceExpression ?: fail(terminal, "A direct fragment local must be returned.")
        val rootVariable = returned.resolve() as? PsiLocalVariable ?: fail(returned, "The fragment return is not a local.")
        val rootCreation = rootVariable.initializer as? PsiNewExpression ?: fail(rootVariable, "The fragment root must be fresh.")
        if (rootVariable.parent.parent != body || rootCreation.anonymousClass != null ||
            (rootCreation.classReference?.resolve() as? PsiClass)?.qualifiedName != WicketFragmentAnalyzer.TYPE)
            fail(rootCreation, "Only a direct framework Fragment root is supported.")
        val arguments = rootCreation.argumentList?.expressions.orEmpty()
        if (arguments.size != 3 || (arguments[0] as? PsiReferenceExpression)?.resolve() != parameter)
            fail(rootCreation, "The fragment must forward the supplied factory ID.")
        if (parameter.type.canonicalText != "java.lang.String" || rootCreation.resolveConstructor()?.containingClass?.qualifiedName != WicketFragmentAnalyzer.TYPE)
            fail(rootCreation, "The supplied String ID must resolve through the framework Fragment constructor.")
        val definition = arguments[1] as? PsiLiteralExpression ?: fail(rootCreation, "The fragment definition ID must be literal.")
        val definitionId = definition.value as? String ?: fail(definition, "The fragment definition ID must be a String.")
        val provider = arguments[2] as? PsiThisExpression ?: fail(rootCreation, "The fragment provider must be the selected page.")
        if (provider.qualifier != null && provider.qualifier?.resolve() != page) fail(provider, "Foreign fragment providers are unsupported.")
        val parameterUses = descendants(body).filterIsInstance<PsiReferenceExpression>().filter { it.resolve() == parameter }
        if (parameterUses != listOf(arguments[0])) fail(parameter, "The supplied ID escapes its single fragment constructor use.")
        return build(body,rootVariable,rootCreation,terminal,FragmentReference(definitionId,rootNodeId,location(rootCreation),location(definition)),
            "content-factory:${lambda.textOffset}",setOf(returned,arguments[0] as PsiReferenceExpression))
    }

    fun analyzeEvent(event: PsiMethod, rootVariable: PsiLocalVariable, terminal: PsiExpressionStatement, suppliedId: String,
        ignored: Set<PsiStatement> = emptySet(), allowTrailingEffects: Boolean = false): Result {
        source(event)
        val body=event.body ?: fail(event,"Event content source is unavailable.")
        val rootCreation=rootVariable.initializer as? PsiNewExpression ?: fail(rootVariable,"Event content must be one direct fresh Fragment local.")
        if(rootVariable.parent.parent!=body || body.statements.firstOrNull()!=rootVariable.parent || (!allowTrailingEffects && body.statements.lastOrNull()!=terminal) ||
            rootCreation.anonymousClass!=null || rootCreation.resolveConstructor()?.containingClass?.qualifiedName!=WicketFragmentAnalyzer.TYPE)
            fail(rootVariable,"Event content needs a direct framework Fragment declaration and a final transfer.")
        if (allowTrailingEffects && body.statements.dropWhile { it != terminal }.drop(1).any { it !in ignored })
            fail(terminal,"Only reviewed effects may follow the content placement.")
        val args=rootCreation.argumentList?.expressions.orEmpty()
        val constants=WicketSourceConstants(source) { tick(event) }
        val actual=when(val id=args.firstOrNull()) { is PsiLiteralExpression->id.value as? String; is PsiReferenceExpression->constants.resolve(id) as? String;else->null }
        if(args.size !in 3..4 || actual!=suppliedId) fail(rootCreation,"Event content must use the guarded slot ID.")
        val definition=args[1] as? PsiLiteralExpression ?: fail(rootCreation,"Event fragment definitions must be literal.")
        val definitionId=definition.value as? String ?: fail(definition,"The fragment definition must be a String.")
        val provider=args[2] as? PsiThisExpression ?: fail(rootCreation,"Event fragments need the selected page as provider.")
        if(provider.qualifier?.resolve()!=page) fail(provider,"A qualified selected-page fragment provider is required inside an event.")
        val transfer=terminal.expression as? PsiMethodCallExpression ?: fail(terminal,"A direct final event transfer is required.")
        val reference=transfer.argumentList.expressions.firstOrNull() as? PsiReferenceExpression ?: fail(transfer,"The final event transfer must receive the content local.")
        if(reference.resolve()!=rootVariable) fail(reference,"The final event transfer uses a different content local.")
        return build(body,rootVariable,rootCreation,terminal,FragmentReference(definitionId,rootNodeId,location(rootCreation),location(definition)),
            "event-content:${event.textOffset}",setOf(reference),ignored)
    }

    private fun build(body:PsiCodeBlock,rootVariable:PsiLocalVariable,rootCreation:PsiNewExpression,terminal:PsiStatement,
        fragment:FragmentReference,key:String,approved:Set<PsiReferenceExpression>,ignored:Set<PsiStatement> = emptySet()):Result {
        val nodes = mutableListOf(ComponentNode(key, null, WicketFragmentAnalyzer.TYPE, null, location(rootCreation), confidence = Confidence.CONFIRMED, idRenameSafe = false))
        val locals = linkedMapOf<PsiVariable, String>()
        val configuration = linkedSetOf<SourceLocation>()
        val callbacks = linkedSetOf<SourceLocation>()
        val approvedUses = approved.toMutableSet()
        fun component(type: PsiType?) = (type as? PsiClassType)?.resolve()?.let {
            it.qualifiedName == "org.apache.wicket.Component" || InheritanceUtil.isInheritor(it, "org.apache.wicket.Component")
        } == true
        fun behavior(value: PsiExpression) = (value.type as? PsiClassType)?.resolve()?.let { InheritanceUtil.isInheritor(it, "org.apache.wicket.behavior.Behavior") } == true
        fun opaque(value: PsiExpression) {
            descendants(value).filterIsInstance<PsiReferenceExpression>().firstOrNull { it.resolve() in locals }?.let {
                fail(it, "A fresh fragment component escapes through construction, configuration or a callback.")
            }
            descendants(value).forEach { nested ->
                when (nested) {
                    is PsiLambdaExpression -> callbacks += location(nested)
                    is PsiMethod -> if (!nested.isConstructor) callbacks += location(nested)
                    is PsiAnonymousClass -> if (nested.initializers.isNotEmpty() || nested.fields.any { it.initializer != null })
                        fail(nested, "Anonymous configuration initialization effects are unreviewed.")
                }
            }
        }
        fun allocate(value: PsiExpression): String {
            tick(value)
            val allocation = creation(value) ?: fail(value, "Children require fresh constructors and proved fluent receiver returns.")
            val identity = WicketComponentIdentity.trace(allocation) ?: fail(allocation, "The child constructor ID is unproved.")
            sources += identity.sources
            val literal = identity.expression as? PsiLiteralExpression ?: fail(allocation, "Factory child IDs must be literal.")
            val id = literal.value as? String ?: fail(literal, "The child ID is not a String.")
            if (id.isEmpty() && !WicketCompletionAnalysis.allowsEmpty(literal) || id.any { it == ':' || it == '~' }) fail(literal, "The child ID is reserved.")
            val type = allocation.classReference?.resolve() as? PsiClass ?: fail(allocation, "The child type is unavailable.")
            opaque(value)
            descendants(value).filterIsInstance<PsiMethodCallExpression>().forEach { configuration += location(it) }
            if (nodes.size >= nodeLimit) bound(value)
            val nodeId = "$key:${nodes.size}"
            nodes += ComponentNode(nodeId, id, type.qualifiedName!!, null, location(allocation), confidence = Confidence.CONFIRMED,
                idLocation = location(literal), idRenameSafe = false, template = true, diagnostics = listOf(Diagnostic(DiagnosticCode.UNRESOLVED_SCOPE,
                    "Factory child attachment is source-declared; constructor/configuration effects, owned internals, model values and callbacks remain unexpanded.", location(allocation))))
            return nodeId
        }
        for (statement in body.statements) {
            tick(statement)
            if(statement==terminal) continue
            if(statement in ignored) {
                val call=(statement as? PsiExpressionStatement)?.expression as? PsiMethodCallExpression ?: fail(statement,"Unreviewed event configuration.")
                call.argumentList.expressions.forEach(::opaque);configuration += location(call);continue
            }
            when (statement) {
                is PsiEmptyStatement -> Unit
                is PsiDeclarationStatement -> statement.declaredElements.forEach { element ->
                    val variable = element as? PsiLocalVariable ?: fail(element, "Only direct component locals are supported.")
                    if (!component(variable.type)) fail(variable, "Only component construction locals are supported in content factories.")
                    locals[variable] = if (variable == rootVariable) key else allocate(variable.initializer ?: fail(variable, "A component local is uninitialized."))
                }
                is PsiReturnStatement -> if (statement != terminal) fail(statement, "Conditional or early factory returns are unsupported.")
                is PsiExpressionStatement -> {
                    val call = statement.expression as? PsiMethodCallExpression ?: fail(statement, "Assignments and unsupported factory effects are not reconstructed.")
                    val method = call.resolveMethod() ?: fail(call, "A factory call does not resolve.")
                    val receiver = call.methodExpression.qualifierExpression as? PsiReferenceExpression ?: fail(call, "Factory calls need a direct owned component receiver.")
                    val parent = locals[receiver.resolve()] ?: fail(receiver, "The factory call uses an unknown parent or receiver.")
                    approvedUses += receiver
                    val args = call.argumentList.expressions
                    val frameworkAdd = method.name == "add" && method.containingClass?.qualifiedName in setOf("org.apache.wicket.MarkupContainer", "org.apache.wicket.Component")
                    if (frameworkAdd) {
                        args.forEach { argument ->
                            if (component(argument.type)) {
                                if (method.containingClass?.qualifiedName != "org.apache.wicket.MarkupContainer") fail(call, "The component addition is not a framework container addition.")
                                val reference = argument as? PsiReferenceExpression
                                val child = reference?.let { locals[it.resolve()] } ?: allocate(argument)
                                if (reference != null) approvedUses += reference
                                val index = nodes.indexOfFirst { it.nodeId == child }
                                if (child == key || nodes[index].parentNodeId != null) fail(argument, "A component has multiple or cyclic attachments.")
                                nodes[index] = nodes[index].copy(parentNodeId = parent)
                            } else {
                                if (!behavior(argument)) fail(argument, "Only component and Behavior additions are supported.")
                                opaque(argument); configuration += location(call)
                            }
                        }
                    } else {
                        if (method.name in setOf("remove", "removeAll", "replace", "replaceWith", "addOrReplace", "queue"))
                            fail(call, "Factory structural mutations are unsupported.")
                        // Configuration declarations have opaque effects, but may not receive fresh graph components.
                        args.forEach(::opaque); configuration += location(call)
                    }
                }
                else -> fail(statement, "Factory control flow is outside the straight-line declaration contract.")
            }
        }
        if (locals[rootVariable] != key) fail(rootVariable, "The fragment root declaration was not visited.")
        descendants(body).filterIsInstance<PsiReferenceExpression>().filter { it.resolve() in locals && it !in approvedUses }.firstOrNull()?.let {
            fail(it, "A factory component escapes its direct declarations, additions and return.")
        }
        val byId = nodes.associateBy { it.nodeId }
        nodes.drop(1).forEach { node ->
            val chain = generateSequence(node) { byId[it.parentNodeId] }.take(nodes.size + 1).toList()
            if (chain.last().nodeId != key || nodes.count { it.parentNodeId == node.parentNodeId && it.wicketId == node.wicketId } != 1)
                fail(rootCreation, "The factory component graph is unattached, cyclic or ambiguous.")
        }
        if (nodes.size == 1) fail(rootCreation, "The fragment has no declared child bindings.")
        return Result(PageReport(WicketFragmentAnalyzer.TYPE, key, nodes, javaSourceFiles = sources.sorted()),
            fragment, configuration.toList(), callbacks.toList())
    }
}

private fun location(element: PsiElement) = SourceLocation(element.containingFile.virtualFile.path, element.textOffset)
