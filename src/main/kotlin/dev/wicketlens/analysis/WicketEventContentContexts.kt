package dev.wicketlens.analysis

import com.intellij.openapi.progress.ProgressManager
import com.intellij.psi.*
import com.intellij.psi.util.PsiTreeUtil
import dev.wicketlens.model.*

/** Source-declared Ajax content placements, separate from the initial page snapshot. */
internal class WicketEventContentContexts(private val limits:Limits=Limits()) {
    data class Limits(val steps:Int=20000,val sources:Int=96,val contexts:Int=4,val nodes:Int=128,val depth:Int=12)
    data class Result(val reports:List<PageReport>,val sources:List<String>,val diagnostics:List<Diagnostic>,val complete:Boolean)
    private class Unknown(val at:PsiElement,override val message:String):RuntimeException(null,null,false,false)
    private class Bound(val at:PsiElement):RuntimeException(null,null,false,false)
    fun analyze(page:PsiClass,primary:PageReport):Result=Session(page,primary).run()

    private inner class Session(val page:PsiClass,val primary:PageReport) {
        val sources=linkedSetOf<String>();val reports=mutableListOf<PageReport>();val diagnostics=mutableListOf<Diagnostic>()
        val cache=mutableMapOf<PsiElement,List<PsiElement>>();var steps=0
        fun tick(at:PsiElement) { ProgressManager.checkCanceled();if(++steps>limits.steps || sources.size>limits.sources) throw Bound(at) }
        fun source(at:PsiElement) {
            tick(at);sources+=at.containingFile?.virtualFile?.path?.substringBefore("!/") ?: fail(at,"Physical source is unavailable.")
            if(at !is PsiCompiledElement && PsiTreeUtil.findChildOfType(at.containingFile,PsiErrorElement::class.java)!=null) fail(at,"Java source is incomplete.")
            if(sources.size>limits.sources) throw Bound(at)
        }
        fun fail(at:PsiElement,message:String):Nothing=throw Unknown(at,message)
        fun descendants(at:PsiElement):List<PsiElement> {
            tick(at)
            return cache.getOrPut(at) {
                val all=linkedSetOf<PsiElement>();at.accept(object:JavaRecursiveElementWalkingVisitor() {
                    override fun visitElement(element:PsiElement) { tick(element);all+=element;super.visitElement(element) }
                });all.toList()
            }
        }
        val declarations=WicketSourceDeclarations(page,limits.depth,::tick,::source,sources,::fail,{throw Bound(it)},::descendants)
        val hosts=WicketInitialContentDeclarations(page,::tick,::source,sources,::fail)
        fun call(s:PsiStatement?)=(s as? PsiExpressionStatement)?.expression as? PsiMethodCallExpression
        fun own(e:PsiExpression?)=e==null || e is PsiThisExpression && e.qualifier==null
        fun ref(e:PsiExpression?,target:PsiElement)=e is PsiReferenceExpression && e.resolve()==target && own(e.qualifierExpression)
        fun path(report:PageReport,nodeId:String):String? {
            val nodes=report.nodes.associateBy { it.nodeId };val parts=mutableListOf<String>();val seen=mutableSetOf<String>();var id:String?=nodeId
            while(id!=report.rootNodeId) {
                tick(page);if(id==null || !seen.add(id)) return null
                val node=nodes[id] ?: return null
                if(!node.templateItem) parts+=node.wicketId ?: return null
                id=node.parentNodeId
            }
            return parts.asReversed().joinToString(":")
        }
        fun resolved(call:PsiMethodCallExpression):PsiMethod {
            if(!call.resolveMethodGenerics().isValidResult) fail(call,"Event method resolution is incomplete.")
            return call.resolveMethod() ?: fail(call,"Event method is unresolved.")
        }
        fun dispatched(call:PsiMethodCallExpression, host:WicketInitialContentDeclarations.Host):PsiMethod {
            val declared=resolved(call)
            return if(call.methodExpression.qualifierExpression is PsiSuperExpression || declared.hasModifierProperty(PsiModifier.PRIVATE) || declared.hasModifierProperty(PsiModifier.FINAL)) declared
                else host.type.findMethodBySignature(declared,true) ?: declared
        }
        fun ownedBoolean(reference:PsiExpression?,host:WicketInitialContentDeclarations.Host):Boolean {
            val ref=reference as? PsiReferenceExpression ?: return false
            val field=ref.resolve() as? PsiField ?: return false
            return own(ref.qualifierExpression) && field.type==PsiTypes.booleanType() && !field.hasModifierProperty(PsiModifier.STATIC) &&
                generateSequence(host.type) { it.superClass }.any { it==field.containingClass }
        }
        fun run():Result {
            if(primary.initialContentSetup==null || primary.markup==null) return Result(emptyList(),emptyList(),emptyList(),true)
            try {
                source(page)
                for(site in descendants(page).filterIsInstance<PsiMethodCallExpression>()) {
                    if(site.argumentList.expressionCount!=2 || site.argumentList.expressions.firstOrNull()?.type?.let(declarations::component)!=true) continue
                    val receiver=(site.methodExpression.qualifierExpression as? PsiReferenceExpression)?.resolve() as? PsiLocalVariable ?: continue
                    val creation=receiver.initializer as? PsiNewExpression ?: continue
                    val host=primary.initialContentSetup.hosts.singleOrNull { it.constructionLocation==location(creation) } ?: continue
                    val event=PsiTreeUtil.getParentOfType(site,PsiMethod::class.java) ?: continue
                    if(event.name!="onClick") continue
                    if(reports.size>=limits.contexts) throw Bound(site)
                    try { reports+=project(site,receiver,creation,host,event) }
                    catch(e:Unknown) { diagnostics+=Diagnostic(DiagnosticCode.UNRESOLVED_SCOPE,"Event content template withheld: ${e.message}",location(e.at)) }
                }
            } catch(e:Bound) {
                return Result(emptyList(),sources.sorted(),listOf(Diagnostic(DiagnosticCode.PARTIAL_ANALYSIS,"Event content discovery exceeded its bounded source contract; no event content graphs published.",location(e.at))),false)
            }
            return Result(reports.toList(),sources.sorted(),diagnostics.toList(),true)
        }
        fun transfer(method:PsiMethod,host:WicketInitialContentDeclarations.Host,chain:MutableList<SourceLocation>,active:Set<PsiMethod> = emptySet()):PsiMethodCallExpression {
            source(method)
            if(active.size>=limits.depth) throw Bound(method)
            if(method is PsiCompiledElement || method in active || method.hasModifierProperty(PsiModifier.STATIC)) fail(method,"Source event transfer is unavailable or recursive.")
            chain+=location(method)
            val parameters=method.parameterList.parameters
            if(parameters.size!=2 || parameters[0].type.canonicalText!="org.apache.wicket.Component" || parameters[1].type.canonicalText!=TARGET) fail(method,"The event transfer must receive Component and AjaxRequestTarget.")
            val body=method.body?.statements ?: fail(method,"Event transfer source is unavailable.")
            val forwarding=(body.singleOrNull() as? PsiReturnStatement)?.returnValue as? PsiMethodCallExpression
            if(forwarding!=null && forwarding.methodExpression.qualifierExpression is PsiSuperExpression && forwarding.argumentList.expressions.zip(parameters).size==2 &&
                forwarding.argumentList.expressions.zip(parameters).all { ref(it.first,it.second) }) return transfer(resolved(forwarding),host,chain,active+method)
            val placement=call(body.firstOrNull()) ?: fail(method,"A direct source content placement must precede opening.")
            if(!own(placement.methodExpression.qualifierExpression) || !hosts.isSetter(dispatched(placement,host),host) || !ref(placement.argumentList.expressions.singleOrNull(),parameters[0])) fail(placement,"The supplied event content must go directly to the guarded owned setter.")
            val follow=(body.lastOrNull() as? PsiReturnStatement)?.returnValue as? PsiMethodCallExpression ?: fail(method,"The event transfer must return its own open helper.")
            if(!own(follow.methodExpression.qualifierExpression) || !ref(follow.argumentList.expressions.singleOrNull(),parameters[1])) fail(follow,"The Ajax target must forward unchanged to its own helper.")
            for(statement in body.drop(1).dropLast(1)) {
                val assignment=(statement as? PsiExpressionStatement)?.expression as? PsiAssignmentExpression
                if(assignment?.operationTokenType!=JavaTokenType.EQ || !ownedBoolean(assignment.lExpression,host) || assignment.rExpression !is PsiLiteralExpression) fail(statement,"Only owned boolean state declarations may accompany the content transfer.")
            }
            val helper=dispatched(follow,host)
            source(helper);chain+=location(helper)
            if(helper.parameterList.parameters.singleOrNull()?.type?.canonicalText!=TARGET || !declarations.fluent(helper)) fail(helper,"The open helper must return its own receiver.")
            descendants(helper).forEach { e ->
                if(e is PsiMethodCallExpression) {
                    val target=resolved(e)
                    val owner=target.containingClass?.qualifiedName
                    val render=target.name=="add" && owner in setOf(TARGET,"org.apache.wicket.core.request.handler.IPartialPageRequestHandler")
                    val query=owner=="org.apache.wicket.Component" && target.name=="getMarkupTag"
                    val clear=owner=="org.apache.wicket.markup.html.form.Form" && target.name=="clearInput"
                    if(!render && !query && !clear) fail(e,"The open helper has unreviewed calls beyond form input reset and Ajax rendering.")
                }
                if(e is PsiAssignmentExpression && (e.operationTokenType!=JavaTokenType.EQ || !ownedBoolean(e.lExpression,host))) fail(e,"The open helper changes unreviewed storage.")
                if(e is PsiNewExpression || e is PsiLambdaExpression || e is PsiLoopStatement || e is PsiIfStatement || e is PsiTryStatement || e is PsiSwitchStatement || e is PsiThrowStatement)
                    fail(e,"The open helper has unreviewed control flow or allocation.")
            }
            return placement
        }
        fun project(site:PsiMethodCallExpression,variable:PsiLocalVariable,creation:PsiNewExpression,host:InitialContentHost,event:PsiMethod):PageReport {
            source(event)
            val constructor=PsiTreeUtil.getParentOfType(variable,PsiMethod::class.java)
            if(constructor?.isConstructor!=true || constructor.containingClass!=page || variable.parent.parent!=constructor.body ||
                descendants(page).filterIsInstance<PsiAssignmentExpression>().any { (it.lExpression as? PsiReferenceExpression)?.resolve()==variable }) fail(variable,"The target must be one unchanged direct page-constructor host local.")
            val anonymous=event.containingClass as? PsiAnonymousClass ?: fail(event,"A direct source AjaxLink handler is required.")
            val triggerCreation=anonymous.parent as? PsiNewExpression ?: fail(event,"The event link allocation is unavailable.")
            if(anonymous.baseClassType.resolve() !is PsiCompiledElement || anonymous.baseClassType.resolve()?.qualifiedName!=LINK || anonymous.initializers.isNotEmpty() || anonymous.fields.isNotEmpty() ||
                anonymous.methods.filterNot { it.isConstructor }!=listOf(event) || event.returnType!=PsiTypes.voidType() || event.parameterList.parameters.singleOrNull()?.type?.canonicalText!=TARGET ||
                event.findSuperMethods().none { it.containingClass?.qualifiedName==LINK }) fail(event,"Only the standard AjaxLink.onClick handler is reviewed.")
            val triggerCandidates=(listOf(primary)+primary.populationContexts).flatMap { report ->
                report.nodes.filter { it.javaLocation==location(triggerCreation) && report.matching!!.findings.any { f->f.javaNodeId==it.nodeId && f.code==DiagnosticCode.MATCHED } }.map { report to it }
            }
            val (triggerReport,trigger)=triggerCandidates.singleOrNull() ?: fail(event,"The source event needs one unambiguous matched initial or symbolic-row trigger.")
            val terminal=event.body?.statements?.lastOrNull() as? PsiExpressionStatement ?: fail(event,"A straight-line handler with one final transfer is required.")
            if(terminal.expression!=site || site.argumentList.expressions.getOrNull(1)?.let { ref(it,event.parameterList.parameters.single()) }!=true) fail(site,"The final event transfer must use the handler's own Ajax target.")
            val info=hosts.inspect(creation,host.hostNodeId)
            if(location(info.setter)!=host.setterLocation || info.contentId!=host.contentId) fail(site,"The guarded initial host route is no longer current.")
            val method=resolved(site);val actual=info.type.findMethodBySignature(method,true) ?: method
            val transfers=mutableListOf<SourceLocation>();val replacement=transfer(actual,info,transfers)
            val rootVariable=(site.argumentList.expressions[0] as? PsiReferenceExpression)?.resolve() as? PsiLocalVariable ?: fail(site,"Event content must be a fresh local Fragment.")
            val rootCreation=rootVariable.initializer as? PsiNewExpression ?: fail(rootVariable,"Event content allocation is unavailable.")
            val ignored=event.body!!.statements.filter { statement ->
                val c=call(statement) ?: return@filter false
                if(statement==terminal || (c.methodExpression.qualifierExpression as? PsiReferenceExpression)?.resolve()!=variable) false else {
                    hosts.validateConfiguration(dispatched(c,info),info);true
                }
            }.toSet()
            val declared=WicketContentFactoryDeclarations(page,primary.rootNodeId,limits.nodes,::tick,::source,sources,::descendants,declarations::freshCreation,::fail,{throw Bound(it)})
                .analyzeEvent(event,rootVariable,terminal,host.contentId,ignored)
            val byId=primary.nodes.associateBy { it.nodeId };val ancestry=generateSequence(byId[host.slotParentNodeId]) { byId[it.parentNodeId] }.take(limits.depth+1).toList()
            if(ancestry.size>limits.depth) throw Bound(site)
            val chain=ancestry.asReversed()
            if(chain.firstOrNull()?.nodeId!=primary.rootNodeId || chain.any { it.nodeId!=primary.rootNodeId && primary.matching!!.findings.singleOrNull { f->f.javaNodeId==it.nodeId }?.code!=DiagnosticCode.MATCHED }) fail(site,"The target ancestry is not uniquely matched.")
            val slot=primary.matching!!.findings.singleOrNull { it.javaNodeId==host.contentNodeId && it.code==DiagnosticCode.MATCHED }?.markupNodeId ?: fail(site,"The initial content slot markup is unproved.")
            val content=declared.owner.rootNodeId
            val nodes=chain.map { it.copy(sourceSetupExpanded=false,template=true,idRenameSafe=false,confidence=Confidence.CONFIRMED) }+declared.owner.nodes.map { node ->
                if(node.nodeId==content) node.copy(wicketId=host.contentId,parentNodeId=host.slotParentNodeId,fragment=declared.fragment,idLocation=rootCreation.argumentList!!.expressions[0].let(::location),template=true)
                else node.copy(template=true,idRenameSafe=false)
            }
            if(nodes.size>limits.nodes) throw Bound(site)
            val keep=chain.mapNotNull { n->primary.matching!!.findings.singleOrNull { it.javaNodeId==n.nodeId }?.markupNodeId }.toSet()+slot
            val markup=primary.markup!!.copy(elements=primary.markup.elements.map { it.copy(participatesInComponentTree=it.nodeId in keep,diagnostics=emptyList()) },scopeComplete=false,diagnostics=primary.markup.documentDiagnostics)
            val targetPath=path(primary,host.slotParentNodeId)?.let { "$it:${host.contentId}" } ?: fail(site,"The source target path is unavailable.")
            val triggerPath=path(triggerReport,trigger.nodeId) ?: fail(event,"The source trigger path is unavailable.")
            val metadata=EventContentPlacement(host.hostNodeId,location(creation),location(rootCreation),transfers,host.setterLocation,location(triggerCreation),triggerPath,
                triggerReport.populationTemplate?.contextId,triggerReport.populationTemplate?.hostComponentPath,triggerReport.populationTemplate?.conditions.orEmpty().map { EventRowPrecondition(it.expression,it.result,it.location) },
                declared.configurationLocations,declared.callbackLocations+location(event))
            val warning=Diagnostic(DiagnosticCode.UNRESOLVED_SCOPE,"Source-declared event content placement only. The handler is not executed; runtime presence, row IDs/count, models, visibility, validation, open/close state, other event effects and later replacements remain unknown.",location(event))
            val report=PageReport(page.qualifiedName!!,primary.rootNodeId,nodes,primary.markupPath,warnings=listOf(warning),markup=markup,javaSourceFiles=(primary.javaSourceFiles+sources).distinct().sorted(),
                lifecycle=ReplacementTemplate("event-content:${page.qualifiedName}:${site.textOffset}",targetPath,content,location(rootCreation),location(site),location(event),location(replacement),WicketFragmentAnalyzer.TYPE,directContent=metadata))
            val matched=WicketGraphMatcher().match(WicketFragmentAnalyzer().analyze(page,WicketGraphMatcher().match(report)))
            if(!WicketCompletionAnalysis.active && matched.matching!!.findings.any { it.code!=DiagnosticCode.MATCHED }) fail(site,"Event content declarations do not have unique supported markup counterparts.")
            return matched
        }
    }
}
private const val TARGET="org.apache.wicket.ajax.AjaxRequestTarget"
private const val LINK="org.apache.wicket.ajax.markup.html.AjaxLink"
private fun location(e:PsiElement)=SourceLocation(e.containingFile.virtualFile.path,e.textOffset)
