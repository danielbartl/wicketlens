package dev.wicketlens.analysis

import com.intellij.openapi.progress.ProgressManager
import com.intellij.psi.*
import com.intellij.psi.util.InheritanceUtil
import com.intellij.psi.util.PsiTreeUtil
import dev.wicketlens.model.*
import dev.wicketlens.analysis.WicketSourceDeclarations.Id
import dev.wicketlens.analysis.WicketSourceDeclarations.Created

/** Bounded source declarations at reviewed tree factories; no tree/provider/model code is executed. */
internal class WicketTreeContexts(private val limits: Limits = Limits()) {
    data class Limits(val steps: Int = 12000, val sources: Int = 64, val contexts: Int = 8, val depth: Int = 12, val nodes: Int = 64)
    data class Result(val reports: List<PageReport>, val sources: List<String>, val diagnostics: List<Diagnostic>, val complete: Boolean)
    private class Unknown(val at: PsiElement, override val message: String) : RuntimeException(null, null, false, false)
    private class Bound(val at: PsiElement) : RuntimeException(null, null, false, false)
    companion object {
        const val ABSTRACT = "org.apache.wicket.extensions.markup.html.repeater.tree.AbstractTree"
        const val NESTED = "org.apache.wicket.extensions.markup.html.repeater.tree.NestedTree"
        const val TABLE = "org.apache.wicket.extensions.markup.html.repeater.tree.TableTree"
        const val NODE = "org.apache.wicket.extensions.markup.html.repeater.tree.Node"
        private val TREE_IDS = setOf(NESTED, TABLE, NODE)
        fun isSourceTree(type: PsiClass) = type !is PsiCompiledElement && InheritanceUtil.isInheritor(type, ABSTRACT)
    }
    fun analyze(page: PsiClass, primary: PageReport): Result = Session(page, primary).run()
    private inner class Session(val page: PsiClass, val primary: PageReport) {
        val sources = linkedSetOf<String>(); val reports = mutableListOf<PageReport>(); val diagnostics = mutableListOf<Diagnostic>()
        val cache = mutableMapOf<PsiElement,List<PsiElement>>(); val visited = mutableSetOf<PsiElement>(); var steps = 0
        val declarations = WicketSourceDeclarations(page,limits.depth,::tick,::source,sources,::fail,{ throw Bound(it) },{ descendants(it,PsiElement::class.java) })
        fun tick(at: PsiElement) { ProgressManager.checkCanceled(); if (++steps > limits.steps || sources.size > limits.sources) throw Bound(at) }
        fun fail(at: PsiElement, message: String): Nothing = throw Unknown(at, message)
        fun source(at: PsiElement) {
            tick(at); val file=at.containingFile ?: fail(at,"Source is unavailable.")
            sources += file.virtualFile?.path?.substringBefore("!/") ?: fail(at,"Physical source is unavailable.")
            if(sources.size > limits.sources) throw Bound(at)
            if(at !is PsiCompiledElement && PsiTreeUtil.findChildOfType(file,PsiErrorElement::class.java)!=null) fail(at,"Java syntax is incomplete.")
        }
        fun <T: PsiElement> descendants(at:PsiElement,type:Class<T>):List<T> {
            val all=cache.getOrPut(at) {
                val ancestor=generateSequence(at.parent) { it.parent }.firstOrNull { it in cache }
                if(ancestor!=null) return@getOrPut cache.getValue(ancestor).filter { PsiTreeUtil.isAncestor(at,it,false) }
                val items=mutableListOf<PsiElement>()
                at.accept(object:JavaRecursiveElementWalkingVisitor() { override fun visitElement(element:PsiElement) { if(visited.add(element)) tick(element); items+=element; super.visitElement(element) } })
                items
            }
            tick(at); return all.filter(type::isInstance).map(type::cast)
        }
        fun unwrap(e:PsiExpression):PsiExpression = if(e is PsiParenthesizedExpression) e.expression?.let(::unwrap) ?: e else e
        fun self(e:PsiExpression?) = e==null || e is PsiThisExpression && e.qualifier==null
        fun component(type:PsiType?) = (type as? PsiClassType)?.resolve()?.let { it.qualifiedName=="org.apache.wicket.Component" || InheritanceUtil.isInheritor(it,"org.apache.wicket.Component") }==true
        fun direct(e:PsiElement,m:PsiMethod):Boolean = PsiTreeUtil.getParentOfType(e,PsiStatement::class.java)?.parent==m.body &&
            PsiTreeUtil.getParentOfType(e,PsiMethod::class.java)==m && generateSequence(e.parent) { it.parent }.takeWhile { it!=m }.none { it is PsiLambdaExpression }
        fun hierarchy(type:PsiClass):List<PsiClass> {
            val result=mutableListOf<PsiClass>(); var current:PsiClass?=type
            while(current!=null) { tick(current); if(result.size>=limits.depth || current in result) throw Bound(current); source(current); result+=current
                if(current is PsiCompiledElement) break; current=current.superClass }
            return result
        }
        fun id(e:PsiExpression?,env:Map<PsiVariable,Id>)=declarations.id(e,env)
        fun fluent(method:PsiMethod)=declarations.fluent(method)
        fun allocation(e:PsiExpression?, seen:Set<PsiVariable> = emptySet(), depth:Int=0):PsiNewExpression? {
            if(depth>=limits.depth) throw Bound(e ?: page); tick(e ?: page)
            return when(val value=e?.let(::unwrap)) {
                is PsiNewExpression -> value
                is PsiReferenceExpression -> (value.resolve() as? PsiVariable)?.takeIf { it !in seen }?.let { allocation(it.initializer,seen+it,depth+1) }
                is PsiMethodCallExpression -> {
                    val allocated=allocation(value.methodExpression.qualifierExpression,seen,depth+1) ?: return null
                    val declared=value.resolveMethod() ?: return null
                    val runtime=allocated.anonymousClass ?: allocated.classReference?.resolve() as? PsiClass ?: return null
                    val actual=runtime.findMethodBySignature(declared,true) ?: declared
                    allocated.takeIf { fluent(actual) }
                }
                else -> null
            }
        }
        fun run():Result {
            if(primary.markupSuffix.isNotEmpty()) return Result(emptyList(),emptyList(),emptyList(),true)
            var complete=true
            try {
                val hosts=descendants(page,PsiNewExpression::class.java).filter { creation ->
                    val type=creation.anonymousClass?.baseClassType?.resolve() ?: creation.classReference?.resolve() as? PsiClass
                    type?.let(::isSourceTree)==true && PsiTreeUtil.getParentOfType(creation,PsiMethod::class.java)?.let { it.isConstructor && it.containingClass==page }==true
                }
                hosts.forEach { creation -> try { build(creation,hosts) } catch(u:Unknown) {
                    diagnostics+=Diagnostic(DiagnosticCode.UNRESOLVED_SCOPE,"Tree population template withheld: ${u.message}",at(u.at))
                } }
            } catch(b:Bound) { reports.clear(); complete=false; diagnostics+=Diagnostic(DiagnosticCode.PARTIAL_ANALYSIS,
                "Tree discovery exceeded its ${limits.steps}-step/${limits.sources}-source/${limits.contexts}-context/${limits.depth}-level/${limits.nodes}-node bound; no tree template was published.",at(b.at)) }
            return Result(reports,sources.sorted(),diagnostics,complete)
        }
        fun build(creation:PsiNewExpression,hosts:List<PsiNewExpression>) {
            source(page)
            val constructor=PsiTreeUtil.getParentOfType(creation,PsiMethod::class.java)!!
            if(!direct(creation,constructor)) fail(creation,"Tree construction must be direct page constructor setup.")
            creation.anonymousClass?.let { if(it.initializers.isNotEmpty() || it.fields.any { f->f.initializer!=null }) fail(it,"Anonymous tree initialization is unreviewed.") }
            val type=creation.anonymousClass ?: creation.classReference?.resolve() as? PsiClass ?: fail(creation,"Tree type is unavailable.")
            val identity=WicketComponentIdentity.trace(creation,TREE_IDS) ?: fail(creation,"Tree constructor ID forwarding is unproved.")
            sources+=identity.sources
            val hostId=id(identity.expression,emptyMap()) ?: fail(creation,"The host ID must be a known String.")
            if(hosts.count { WicketComponentIdentity.trace(it,TREE_IDS)?.expression?.let { expression->id(expression,emptyMap())?.value }==hostId.value }!=1) fail(creation,"Host IDs are ambiguous.")
            val calls=descendants(constructor.body!!,PsiMethodCallExpression::class.java)
            val additions=calls.filter { call-> direct(call,constructor) && self(call.methodExpression.qualifierExpression) &&
                call.resolveMethod()?.let { it.name=="add" && it.containingClass?.qualifiedName=="org.apache.wicket.MarkupContainer" }==true &&
                call.argumentList.expressions.any { allocation(it)==creation } }
            if(additions.size!=1) fail(creation,"The host needs one direct page attachment.")
            val local=generateSequence(creation.parent) { it.parent }.takeWhile { it !is PsiStatement }.filterIsInstance<PsiLocalVariable>().firstOrNull()
            if(local!=null && descendants(constructor.body!!,PsiAssignmentExpression::class.java).any { (it.lExpression as? PsiReferenceExpression)?.resolve()==local }) fail(local,"The tree local is reassigned.")
            calls.filter { direct(it,constructor) }.forEach { call ->
                if(call.resolveMethod()?.let { it.name in MUTATIONS && component(it.containingClass?.let { type -> JavaPsiFacade.getElementFactory(page.project).createType(type) }) }==true && (self(call.methodExpression.qualifierExpression) || allocation(call.methodExpression.qualifierExpression)==creation)) fail(call,"Known structural mutations invalidate the declared host.")
            }
            val owners=hierarchy(type)
            val framework=when { owners.any { it.qualifiedName==NESTED } -> "NESTED_TREE"; owners.any { it.qualifiedName==TABLE } -> "TABLE_TREE"; else -> fail(type,"Only reviewed nested/table tree population contracts are supported.") }
            val nodeFactory=method(type,"newNodeComponent",2)
            if(nodeFactory is PsiCompiledElement || nodeFactory.findDeepestSuperMethods().none { it.containingClass?.qualifiedName==ABSTRACT }) fail(nodeFactory,"The source node factory does not override AbstractTree.")
            val node=returnedCreation(nodeFactory, mapOf(nodeFactory.parameterList.parameters[0] to Id("node",null)))
            val nodeType=node.creation.anonymousClass?.baseClassType?.resolve() ?: node.creation.classReference?.resolve() as? PsiClass ?: fail(node.creation,"Node type is unavailable.")
            if(!InheritanceUtil.isInheritor(nodeType,NODE)) fail(node.creation,"The factory must return a framework Node subtype.")
            val nodeIdentity=WicketComponentIdentity.trace(node.creation,TREE_IDS) ?: fail(node.creation,"Node ID forwarding is unproved.")
            sources+=nodeIdentity.sources
            if(id(nodeIdentity.expression,node.ids)?.let { it.value=="node" && it.origin==null }!=true) fail(node.creation,"The node must forward its supplied ID.")
            node.creation.anonymousClass?.let { if(it.initializers.isNotEmpty() || it.fields.any { field->field.initializer!=null }) fail(it,"Anonymous node initialization is unreviewed.") }
            val nodeArgs=node.creation.argumentList?.expressions.orEmpty()
            if(nodeArgs.size!=3 || (nodeArgs[1] as? PsiThisExpression)?.let { it.qualifier==null || it.qualifier?.resolve() in owners }!=true ||
                (nodeArgs[2] as? PsiReferenceExpression)?.resolve()!=nodeFactory.parameterList.parameters[1]) fail(node.creation,"Node construction must forward the owning tree and supplied model.")
            val actualNode=node.creation.anonymousClass ?: nodeType
            val nodeOwners=hierarchy(actualNode)
            if(nodeOwners.filter { it !is PsiCompiledElement }.any { owner->owner.methods.any { it.name=="onInitialize" && it.parameterList.parametersCount==0 } }) fail(node.creation,"Custom Node initialization is outside the reviewed contract.")
            val bridge=method(actualNode,"createContent",2)
            val forwarding=(bridge.body?.statements?.singleOrNull() as? PsiReturnStatement)?.returnValue as? PsiMethodCallExpression ?: fail(bridge,"Node content must directly forward to the owning tree factory.")
            val contentFactory=method(type,"newContentComponent",2)
            val childSubtree = if (framework == "NESTED_TREE") {
                val frameworkType = owners.singleOrNull { it.qualifiedName == NESTED && it is PsiCompiledElement }
                val subtreeFactory = frameworkType?.findMethodsByName("newSubtree", false)?.singleOrNull { declared ->
                    val parameters = declared.parameterList.parameters
                    parameters.size == 2 && parameters[0].type.equalsToText("java.lang.String") &&
                        (parameters[1].type as? PsiClassType)?.resolve()?.qualifiedName == "org.apache.wicket.model.IModel"
                }
                val subtree = JavaPsiFacade.getInstance(type.project).findClass(
                    "org.apache.wicket.extensions.markup.html.repeater.tree.nested.Subtree", type.resolveScope)
                val sourceSubtreeFactory = owners.filterNot { it is PsiCompiledElement }.any { owner ->
                    owner.findMethodsByName("newSubtree", false).any { it.parameterList.parametersCount == 2 }
                }
                if (subtreeFactory != null && subtree is PsiCompiledElement && !sourceSubtreeFactory &&
                    type.findMethodBySignature(subtreeFactory, true) == subtreeFactory) {
                    source(subtree)
                    TreeChildSubtree(subtree.qualifiedName!!, at(nodeFactory), at(contentFactory))
                } else null
            } else null
            val q=forwarding.methodExpression.qualifierExpression
            if(forwarding.resolveMethod()!=contentFactory || q!=null && !(q is PsiThisExpression && q.qualifier?.resolve() in owners)) fail(forwarding,"Content dispatch uses a foreign or unreviewed receiver.")
            if(forwarding.argumentList.expressions.map { (it as? PsiReferenceExpression)?.resolve() }!=bridge.parameterList.parameters.toList()) fail(forwarding,"Content ID/model forwarding is unresolved.")
            val content=returnedCreation(contentFactory,mapOf(contentFactory.parameterList.parameters[0] to Id("content",null)))
            val contentType=content.creation.classReference?.resolve() as? PsiClass ?: fail(content.creation,"Content type is unavailable.")
            if(content.creation.anonymousClass!=null || contentType is PsiCompiledElement || !WicketComponentTypes.ownsMarkup(contentType)) fail(content.creation,"Content needs conventional source-owned markup.")
            val contentIdentity=WicketComponentIdentity.trace(content.creation) ?: fail(content.creation,"Content ID forwarding is unproved.")
            sources+=contentIdentity.sources
            if(id(contentIdentity.expression,content.ids)?.let { it.value=="content" && it.origin==null }!=true) fail(content.creation,"Content must forward its supplied ID.")
            val junction=method(actualNode,"createJunctionComponent",1)
            val junctionCreation=returnedCreation(junction,mapOf(junction.parameterList.parameters[0] to Id("junction",null)))
            val junctionIdentity=WicketComponentIdentity.trace(junctionCreation.creation) ?: fail(junctionCreation.creation,"Junction ID forwarding is unproved.")
            sources+=junctionIdentity.sources
            if(id(junctionIdentity.expression,junctionCreation.ids)?.let { it.value=="junction" && it.origin==null }!=true) fail(junctionCreation.creation,"Junction must forward its supplied ID.")
            val contentCtor=content.creation.resolveConstructor() ?: fail(content.creation,"Content constructor is unavailable.")
            source(contentCtor)
            if(contentType.initializers.isNotEmpty() || contentType.fields.any { it.initializer!=null && component(it.type) }) fail(contentType,"Content initialization has unreviewed component effects.")
            val aliases=descendants(contentCtor.body!!,PsiConditionalExpression::class.java).filter { expression ->
                expression.parent is PsiLocalVariable && component((expression.parent as PsiLocalVariable).type)
            }
            val guards=aliases.map { guard(it.condition,contentCtor) }.distinctBy { it.first }
            if(guards.size>2) throw Bound(contentCtor)
            val count=1 shl guards.size
            val nodeMarkup=markup(nodeType); val contentMarkup=markup(contentType)
            for(mask in 0 until count) {
                if(reports.size>=limits.contexts) throw Bound(creation)
                val facts=guards.mapIndexed { i,g -> g.first to (mask and (1 shl i)!=0) }.toMap()
                reports+=project(creation,hostId,type,nodeType,nodeFactory,junction,contentFactory,contentType,contentCtor,junctionCreation.creation,nodeMarkup,contentMarkup,facts,childSubtree)
            }
        }
        fun method(type:PsiClass,name:String,arity:Int):PsiMethod = hierarchy(type).firstNotNullOfOrNull { owner->owner.methods.singleOrNull { it.name==name && it.parameterList.parametersCount==arity && !it.hasModifierProperty(PsiModifier.STATIC) } }
            ?.also(::source) ?: fail(type,"A unique $name factory is unavailable.")
        fun returnedCreation(method:PsiMethod,ids:Map<PsiVariable,Id>):Created=declarations.returnedCreation(method,ids)
        fun guard(e:PsiExpression?,constructor:PsiMethod):Pair<PsiVariable,Boolean> {
            val binary=e?.let(::unwrap) as? PsiBinaryExpression ?: fail(e ?: constructor,"Only stable nullness alias guards are supported.")
            val operands=listOf(binary.lOperand,binary.rOperand)
            val variable=operands.filterIsInstance<PsiReferenceExpression>().singleOrNull()?.resolve() as? PsiVariable ?: fail(binary,"Nullness guard identity is unavailable.")
            if(operands.filterIsInstance<PsiLiteralExpression>().singleOrNull()?.value!=null || operands.none { it is PsiLiteralExpression }) fail(binary,"The guard must compare with null.")
            if(variable !in constructor.parameterList.parameters || descendants(constructor.body!!,PsiAssignmentExpression::class.java).any { (it.lExpression as? PsiReferenceExpression)?.resolve()==variable }) fail(binary,"The nullness guard is not a stable constructor parameter.")
            return variable to when(binary.operationTokenType) { JavaTokenType.NE -> true; JavaTokenType.EQEQ -> false; else -> fail(binary,"Unsupported nullness operation.") }
        }
        fun markup(type:PsiClass):MarkupReport {
            val result=WicketMarkupInheritanceAnalyzer().analyze(type,WicketComponentTypes.kind(type))
            val markup=result.markup ?: fail(type,"Conventional markup is unavailable.")
            if(markup.diagnostics.isNotEmpty() || markup.documentDiagnostics.isNotEmpty() || markup.elements.any { it.participatesInComponentTree && it.hasWicketId && it.confidence!=Confidence.CONFIRMED }) fail(type,"Markup scope is unresolved.")
            sources+=markup.sourceFiles.map { it.substringBefore("!/") }; tick(type)
            return markup
        }
        fun project(host:PsiNewExpression,hostId:Id,hostType:PsiClass,nodeType:PsiClass,nodeFactory:PsiMethod,junctionFactory:PsiMethod,contentFactory:PsiMethod,contentType:PsiClass,ctor:PsiMethod,junction:PsiNewExpression,nodeMarkup:MarkupReport,contentMarkup:MarkupReport,facts:Map<PsiVariable,Boolean>,childSubtree:TreeChildSubtree?):PageReport {
            val key="tree:${at(host).file}:${host.textOffset}:${facts.values.joinToString() }"; val root="$key:root"; val content="$key:content"
            val nodes=mutableListOf(ComponentNode(root,null,nodeType.qualifiedName!!,null,at(host),confidence=Confidence.CONFIRMED,idRenameSafe=false,template=true))
            nodes+=ComponentNode("$key:junction","junction",(junction.anonymousClass?.baseClassType?.resolve() ?: junction.classReference?.resolve() as PsiClass).qualifiedName!!,root,at(junction),confidence=Confidence.CONFIRMED,computedId=true,idRenameSafe=false,template=true)
            nodes+=ComponentNode(content,"content",contentType.qualifiedName!!,root,at(contentFactory),confidence=Confidence.CONFIRMED,computedId=true,idRenameSafe=false,template=true,sourceSetupExpanded=true,ownedMarkupPath=contentMarkup.file)
            nodes += declarations.constructorNodes(ctor,content,"$key:child",limits.nodes - nodes.size,contentType,conditional = { expression ->
                val condition=guard(expression.condition,ctor)
                if(facts.getValue(condition.first)==condition.second) expression.thenExpression else expression.elseExpression
            })
            val markupNodes=nodeMarkup.elements.filter { it.participatesInComponentTree && it.hasWicketId }
            if(markupNodes.map { it.wicketId }.toSet()!=setOf("junction","content") || markupNodes.any { it.parentWicketElementId!=null }) fail(nodeFactory,"Node markup does not have the reviewed junction/content slots.")
            val mids=markupNodes.associate { it.nodeId to "$key:markup:${it.nodeId}" }
            val elements=markupNodes.map { it.copy(nodeId=mids.getValue(it.nodeId),parentElementId=null,parentWicketElementId=null) }.toMutableList()
            val contentSlot=elements.single { it.wicketId=="content" }.nodeId
            val owned=contentMarkup.elements.filter { it.participatesInComponentTree && it.hasWicketId }
            val ownedIds=owned.associate { it.nodeId to "$key:owned:${it.nodeId}" }
            elements+=owned.map { it.copy(nodeId=ownedIds.getValue(it.nodeId),parentElementId=ownedIds[it.parentWicketElementId] ?: contentSlot,parentWicketElementId=ownedIds[it.parentWicketElementId] ?: contentSlot,markupProviderNodeId=content) }
            val conditions=facts.map { (variable,present)->SourceNullnessCondition(variable.name ?: "<unknown>",at(variable),if(present) "NON_NULL" else "NULL") }
            val warning=Diagnostic(DiagnosticCode.PARTIAL_ANALYSIS,"Source-declared node-relative tree template under explicit nullness preconditions. Runtime node ancestry/IDs/count, expansion, selection, models, callback execution, constructor/configuration effects, other page effects and subsequent state remain unknown.",at(host))
            return WicketGraphMatcher().match(PageReport(page.qualifiedName!!,root,nodes,nodeMarkup.file,scopeComplete=false,warnings=listOf(warning),
                markup=MarkupReport(nodeMarkup.file,MarkupKind.PANEL,elements,false,sourceFiles=(nodeMarkup.sourceFiles+contentMarkup.sourceFiles).distinct()),javaSourceFiles=sources.sorted(),
                treeTemplate=TreePopulationTemplate(key,if(InheritanceUtil.isInheritor(hostType,NESTED)) "NESTED_TREE" else "TABLE_TREE",hostType.qualifiedName ?: hostType.superClass!!.qualifiedName!!,hostId.value,at(host),nodeType.qualifiedName!!,at(nodeFactory),at(junctionFactory),at(contentFactory),contentType.qualifiedName!!,conditions, childSubtree)))
        }
    }
}
private fun at(e:PsiElement)=SourceLocation(e.containingFile.virtualFile.path,e.textOffset)
private val MUTATIONS=setOf("remove","removeAll","replace","replaceWith","addOrReplace","queue")
