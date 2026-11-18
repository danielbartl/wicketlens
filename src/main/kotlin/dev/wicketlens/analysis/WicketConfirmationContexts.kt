package dev.wicketlens.analysis

import com.intellij.openapi.progress.ProgressManager
import com.intellij.psi.*
import com.intellij.psi.search.LocalSearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.util.PsiTreeUtil
import dev.wicketlens.model.*

/** Conditional source route from an ask-site consumer through an owned dialog submit hook. Never executes it. */
internal class WicketConfirmationContexts(private val limits:WicketSubmissionContexts.Limits=WicketSubmissionContexts.Limits()) {
    companion object {
        /** Indexed discovery hint only. The full slot/dispatch proof is still required before any binding. */
        fun hasConsumerDeclaration(type:PsiClass)=type.fields.any {field ->
            ProgressManager.checkCanceled()
            field.hasModifierProperty(PsiModifier.PRIVATE) && !field.hasModifierProperty(PsiModifier.STATIC) &&
                LambdaUtil.getFunctionalInterfaceMethod(field.type)?.let {it is PsiCompiledElement && it.containingClass?.qualifiedName=="java.util.function.Consumer" && it.name=="accept"}==true
        }
    }
    data class Result(val reports:List<PageReport>,val sources:List<String>,val diagnostics:List<Diagnostic>,val complete:Boolean)
    private class Unknown(val at:PsiElement,override val message:String):RuntimeException(null,null,false,false)
    private class Bound(val at:PsiElement):RuntimeException(null,null,false,false)
    fun analyze(page:PsiClass,primary:PageReport)=Session(page,primary).run()
    private data class Protocol(val setters:List<PsiMethod>,val slot:PsiField,val hook:PsiMethod,val capture:PsiLocalVariable,
        val clear:PsiAssignmentExpression,val guard:PsiIfStatement,val dispatch:PsiMethodCallExpression,val close:PsiMethod,val closeClear:PsiAssignmentExpression)
    private inner class Session(val page:PsiClass,val primary:PageReport) {
        val sources=linkedSetOf<String>();val diagnostics=mutableListOf<Diagnostic>();val reports=mutableListOf<PageReport>();var steps=0
        fun tick(at:PsiElement) {ProgressManager.checkCanceled();if(++steps>limits.steps || sources.size>limits.sources) throw Bound(at)}
        fun fail(at:PsiElement,message:String):Nothing=throw Unknown(at,message)
        fun source(at:PsiElement) {
            tick(at);val file=at.containingFile ?: fail(at,"Physical source is unavailable.")
            sources+=file.virtualFile?.path?.substringBefore("!/") ?: fail(at,"Physical source is unavailable.")
            if(sources.size>limits.sources) throw Bound(at)
            if(at !is PsiCompiledElement && PsiTreeUtil.findChildOfType(file,PsiErrorElement::class.java)!=null) fail(at,"Java syntax is incomplete.")
        }
        val structure=WicketSourceDescendants(::tick)
        fun descendants(at:PsiElement)=structure.descendants(at)
        val hosts=WicketInitialContentDeclarations(page,::tick,::source,sources,::fail)
        val attachments=WicketDeclaredAttachments(page,primary,limits.depth,::tick,::descendants,::fail,{throw Bound(it)}) {primary.rootNodeId}
        val bridge=WicketDialogSubmissionBridge(page,primary,limits.depth,::tick,::source,::descendants,::fail,attachments)
        val constants=WicketSourceConstants(::source) {tick(page)}
        val dataTypes by lazy {WicketConstructorDataLoop(page.constructors.single(),::tick,::source,constants::expression,{false},{source(it)},::fail)}
        fun own(e:PsiExpression?)=e==null || e is PsiThisExpression && (e.qualifier==null || e.qualifier?.resolve()==page)
        fun ref(e:PsiExpression?,target:PsiElement)=e is PsiReferenceExpression && e.resolve()==target && (e.qualifierExpression==null || e.qualifierExpression is PsiThisExpression)
        fun call(s:PsiStatement?)=(s as? PsiExpressionStatement)?.expression as? PsiMethodCallExpression
        fun assignment(s:PsiStatement?)=(s as? PsiExpressionStatement)?.expression as? PsiAssignmentExpression
        fun resolved(c:PsiMethodCallExpression):PsiMethod {
            if(!c.resolveMethodGenerics().isValidResult) fail(c,"The source call is inaccessible or unresolved.")
            return c.resolveMethod() ?: fail(c,"The source call is unresolved.")
        }
        fun nil(e:PsiExpression?)=e is PsiLiteralExpression && e.value==null
        fun selected(method:PsiMethod,actual:PsiClass)=if(method.hasModifierProperty(PsiModifier.STATIC) || method.hasModifierProperty(PsiModifier.PRIVATE)) method else actual.findMethodBySignature(method,true) ?: method
        private val consumers=WicketConsumerDeclarations(limits.depth,::tick,::source,::fail)
        fun consumer(type:PsiType?,arity:Int,at:PsiElement)=consumers.inspect(type,arity,at)
        fun run():Result {
            if(primary.initialContentSetup==null || primary.markup==null || page.constructors.size!=1) return Result(emptyList(),emptyList(),emptyList(),true)
            try {
                source(page)
                for(site in descendants(page).filterIsInstance<PsiMethodCallExpression>()) {
                    val callback=site.argumentList.expressions.lastOrNull() as? PsiLambdaExpression ?: continue
                    if(callback.parameterList.parameters.singleOrNull()?.type?.canonicalText!=TARGET) continue
                    val variable=(site.methodExpression.qualifierExpression as? PsiReferenceExpression)?.resolve() as? PsiLocalVariable ?: continue
                    val creation=variable.initializer as? PsiNewExpression ?: continue
                    val host=primary.initialContentSetup.hosts.singleOrNull {it.constructionLocation==location(creation)} ?: continue
                    if(reports.size>=limits.contexts) throw Bound(site)
                    try {reports+=project(site,callback,creation,host)}
                    catch(e:Unknown) {diagnostics+=Diagnostic(DiagnosticCode.UNRESOLVED_SCOPE,"Confirmation template withheld: ${e.message}",location(e.at))}
                }
            } catch(e:Bound) {
                return Result(emptyList(),sources.sorted(),listOf(Diagnostic(DiagnosticCode.PARTIAL_ANALYSIS,"Confirmation discovery exceeded its bounded source contract; no confirmation graphs published.",location(e.at))),false)
            }
            return Result(reports.toList(),sources.sorted(),diagnostics.toList(),true)
        }
        fun protocol(site:PsiMethodCallExpression,actual:PsiClass):Protocol {
            val chain=mutableListOf<PsiMethod>();var setter=selected(resolved(site),actual)
            while(true) {
                source(setter)
                if(setter is PsiCompiledElement || setter in chain || chain.size>=limits.depth || setter.hasModifierProperty(PsiModifier.STATIC)) fail(setter,"The confirmation setter must have bounded selected source dispatch.")
                chain+=setter
                val params=setter.parameterList.parameters
                if(params.size!=4 || params.first().type.canonicalText!=TARGET) fail(setter,"The ask protocol requires target, two opaque display values and one consumer.")
                consumer(params.last().type,1,params.last())
                val statements=setter.body?.statements ?: fail(setter,"The confirmation setter body is unavailable.")
                val forwarding=(statements.singleOrNull() as? PsiReturnStatement)?.returnValue as? PsiMethodCallExpression
                if(forwarding!=null) {
                    if(!own(forwarding.methodExpression.qualifierExpression) || !ref(forwarding.argumentList.expressions.firstOrNull(),params.first()) || !ref(forwarding.argumentList.expressions.lastOrNull(),params.last())) fail(forwarding,"The ask overload must forward its unchanged target and consumer.")
                    forwarding.argumentList.expressions.drop(1).dropLast(1).zip(params.drop(1).dropLast(1)).forEach { (arg,param) ->
                        val factory=arg as? PsiMethodCallExpression ?: fail(arg,"Display overloads require exact compiled Model.of forwarding.")
                        val target=resolved(factory);source(target)
                        if(target !is PsiCompiledElement || target.containingClass?.qualifiedName!="org.apache.wicket.model.Model" || target.name!="of" || !target.hasModifierProperty(PsiModifier.STATIC) || !ref(factory.argumentList.expressions.singleOrNull(),param)) fail(factory,"Display overloads require exact compiled Model.of forwarding.")
                    }
                    setter=selected(resolved(forwarding),actual);continue
                }
                val store=assignment(statements.firstOrNull()) ?: fail(setter,"The consumer must first be stored in its own private slot.")
                val left=store.lExpression as? PsiReferenceExpression;val slot=left?.resolve() as? PsiField ?: fail(store,"The callback slot is unavailable.")
                source(slot)
                if(slot.containingClass!=setter.containingClass || !slot.hasModifierProperty(PsiModifier.PRIVATE) || slot.hasModifierProperty(PsiModifier.STATIC) || slot.hasModifierProperty(PsiModifier.FINAL) || slot.initializer!=null || slot.type!=params.last().type || !ref(left,slot) || store.operationTokenType!=JavaTokenType.EQ || !ref(store.rExpression,params.last())) fail(store,"The consumer slot must be private default-null instance storage with an unchanged assignment.")
                if((statements.lastOrNull() as? PsiReturnStatement)?.returnValue.let {it !is PsiThisExpression || it.qualifier!=null}) fail(setter,"The ask setter must return its own receiver.")
                val parameterUses=descendants(setter.body!!).filterIsInstance<PsiReferenceExpression>().filter {it.resolve()==params.last()}
                if(parameterUses!=listOf(store.rExpression)) fail(setter,"The consumer parameter escapes its one slot assignment.")
                val captures=actual.allMethods.filter {it !is PsiCompiledElement && it.returnType==PsiTypes.voidType() && it.parameterList.parameters.singleOrNull()?.type?.canonicalText==TARGET && selected(it,actual)==it}.distinct()
                val candidate=captures.mapNotNull {method ->
                    val first=method.body?.statements?.firstOrNull() as? PsiDeclarationStatement ?: return@mapNotNull null
                    val local=first.declaredElements.singleOrNull() as? PsiLocalVariable ?: return@mapNotNull null
                    if(!ref(local.initializer,slot)) return@mapNotNull null
                    method to local
                }.singleOrNull() ?: fail(slot,"One selected source hook must capture the stored consumer.")
                val hook=candidate.first;val local=candidate.second;source(hook)
                val body=hook.body!!.statements
                val clear=assignment(body.getOrNull(1));val guard=body.getOrNull(2) as? PsiIfStatement
                if(body.size!=3 || clear?.operationTokenType!=JavaTokenType.EQ || !ref(clear.lExpression,slot) || !nil(clear.rExpression) || guard?.elseBranch!=null) fail(hook,"The hook must capture, clear, then guard the same callback slot.")
                val condition=guard?.condition as? PsiBinaryExpression ?: fail(hook,"The callback needs a direct non-null guard.")
                if(condition.operationTokenType!=JavaTokenType.NE || !(ref(condition.lOperand,local)&&nil(condition.rOperand) || nil(condition.lOperand)&&ref(condition.rOperand,local))) fail(condition,"The callback needs its own direct non-null guard.")
                val dispatch=call((guard.thenBranch as? PsiBlockStatement)?.codeBlock?.statements?.singleOrNull()) ?: fail(guard,"The non-null branch must only invoke the consumer.")
                val target=resolved(dispatch);source(target)
                if(target !is PsiCompiledElement || target.containingClass?.qualifiedName!="java.util.function.Consumer" || target.name!="accept" || !ref(dispatch.methodExpression.qualifierExpression,local) || !ref(dispatch.argumentList.expressions.singleOrNull(),hook.parameterList.parameters.single())) fail(dispatch,"The consumer must receive the hook's unchanged Ajax target.")
                val localUses=descendants(hook.body!!).filterIsInstance<PsiReferenceExpression>().filter {it.resolve()==local}
                if(localUses.toSet()!=setOf(condition.lOperand,condition.rOperand,dispatch.methodExpression.qualifierExpression).filterIsInstance<PsiReferenceExpression>().filter {it.resolve()==local}.toSet()) fail(hook,"The captured consumer escapes its guard and dispatch.")
                val refs=mutableListOf<PsiReferenceExpression>()
                ReferencesSearch.search(slot,LocalSearchScope(slot.containingClass!!)).forEach(com.intellij.util.Processor {use ->
                    tick(use.element)
                    if(PsiTreeUtil.getParentOfType(use.element,PsiComment::class.java)==null) refs+=use.element as? PsiReferenceExpression ?: fail(use.element,"The callback slot has an unreviewed reference.")
                    true
                })
                val extras=refs.filter {it!=store.lExpression && it!=local.initializer && it!=clear!!.lExpression}
                val closeRef=extras.singleOrNull() ?: fail(slot,"The callback slot has competing reads, writes or escapes.")
                val closeClear=closeRef.parent as? PsiAssignmentExpression ?: fail(closeRef,"The close hook must only clear the stored callback.")
                val close=PsiTreeUtil.getParentOfType(closeClear,PsiMethod::class.java) ?: fail(closeRef,"The close hook is unavailable.")
                source(close)
                val closeBody=close.body?.statements.orEmpty();val parentCall=call(closeBody.getOrNull(1))
                val base=parentCall?.let(::resolved)
                if(close.hasModifierProperty(PsiModifier.STATIC) || close.returnType!=PsiTypes.voidType() || close.parameterList.parameters.singleOrNull()?.type?.canonicalText!=TARGET || selected(close,actual)!=close || closeBody.size!=2 || assignment(closeBody.first())!=closeClear || !ref(closeClear.lExpression,slot) || !nil(closeClear.rExpression) || closeClear.operationTokenType!=JavaTokenType.EQ || parentCall?.methodExpression?.qualifierExpression !is PsiSuperExpression || base !in close.findSuperMethods() || base?.body?.statements?.isEmpty()!=true || !ref(parentCall.argumentList.expressions.singleOrNull(),close.parameterList.parameters.single())) fail(close,"The source close hook must clear the same slot before its empty base hook.")
                source(base!!)
                // Explicit eager calls to the consumption/cleanup hooks are not configuration declarations.
                chain.forEach {m -> descendants(m.body!!).filterIsInstance<PsiMethodCallExpression>().forEach {c ->
                    val called=selected(resolved(c),actual)
                    if(called==hook || called==close) fail(c,"The ask setter eagerly invokes a lifecycle hook.")
                } }
                return Protocol(chain,slot,hook,local,clear!!,guard,dispatch,close,closeClear)
            }
        }
        fun dataCall(c:PsiMethodCallExpression,method:PsiMethod,data:PsiParameter):ConfirmationDataCall {
            source(method)
            if(method is PsiCompiledElement || method.containingClass!=page || !method.hasModifierProperty(PsiModifier.PRIVATE) || method.hasModifierProperty(PsiModifier.STATIC) || method.returnType!=PsiTypes.voidType() || method.parameterList.parametersCount!=1 || !own(c.methodExpression.qualifierExpression)) fail(c,"The callback has an unreviewed component/helper call.")
            val arg=c.argumentList.expressions.singleOrNull() ?: fail(c,"The data helper needs one symbolic data input.")
            val origin=if(ref(arg,data)) arg else {
                val factory=arg as? PsiMethodCallExpression ?: fail(arg,"The data input must be captured directly or through exact List.of.")
                val target=resolved(factory);source(target)
                if(target !is PsiCompiledElement || target.containingClass?.qualifiedName!="java.util.List" || target.name!="of" || !target.hasModifierProperty(PsiModifier.STATIC)) fail(factory,"Only exact JDK List.of may wrap one captured row.")
                factory.argumentList.expressions.singleOrNull()?.takeIf {ref(it,data)} ?: fail(factory,"The List.of input must be the captured row.")
            }
            val mutation=call(method.body?.statements?.singleOrNull()) ?: fail(method,"Only one direct source data-collection call is reviewed.")
            val target=resolved(mutation);source(target)
            val field=(mutation.methodExpression.qualifierExpression as? PsiReferenceExpression)?.resolve() as? PsiField ?: fail(mutation,"The data storage field is unavailable.")
            source(field)
            val creation=field.initializer as? PsiNewExpression
            if(target !is PsiCompiledElement || target.containingClass?.qualifiedName !in setOf("java.util.List","java.util.Collection") || target.name!="removeAll" || !ref(mutation.methodExpression.qualifierExpression,field) || !ref(mutation.argumentList.expressions.singleOrNull(),method.parameterList.parameters.single()) || field.containingClass!=page || !field.hasModifierProperty(PsiModifier.PRIVATE) || !field.hasModifierProperty(PsiModifier.FINAL) || field.hasModifierProperty(PsiModifier.STATIC) || creation?.anonymousClass!=null || creation?.qualifier!=null || creation?.argumentList?.expressionCount!=0 || creation.classReference?.resolve().let {it !is PsiCompiledElement || (it as? PsiClass)?.qualifiedName!="java.util.ArrayList"} || !dataTypes.dataType(field.type) || !dataTypes.dataType(data.type)) fail(mutation,"The data declaration requires private-final ArrayList-backed reviewed data and exact removeAll forwarding.")
            return ConfirmationDataCall(location(c),location(method),location(field),location(mutation),target.name,location(arg),location(origin))
        }
        fun project(site:PsiMethodCallExpression,callback:PsiLambdaExpression,creation:PsiNewExpression,host:InitialContentHost):PageReport {
            val actual=creation.anonymousClass ?: creation.classReference?.resolve() as? PsiClass ?: fail(creation,"The confirmation host type is unresolved.")
            if(creation.anonymousClass!=null) fail(creation,"Anonymous confirmation host overrides are outside this protocol.")
            val receiver=site.methodExpression.qualifierExpression!!
            if(attachments.component(receiver)!=host.hostNodeId) fail(receiver,"The ask receiver is not the same declared host.")
            val initiating=PsiTreeUtil.getParentOfType(site,PsiLambdaExpression::class.java) ?: fail(site,"The ask site must be declared inside a separate initiating callback.")
            val parameters=initiating.parameterList.parameters
            val registration=initiating.parent.parent as? PsiMethodCallExpression ?: fail(initiating,"The initiating callback must be a direct source call argument.")
            val registered=resolved(registration);source(registered)
            val at=registration.argumentList.expressions.indexOf(initiating)
            if(registered is PsiCompiledElement || parameters.size!=2 || parameters.first().type.canonicalText!=TARGET || at<0) fail(initiating,"The initiating callback needs an unchanged target and symbolic data parameter.")
            consumer(registered.parameterList.parameters.getOrNull(at)?.type,2,initiating)
            if(initiating.body!=site && (initiating.body as? PsiCodeBlock)?.statements?.singleOrNull().let {call(it)}!=site) fail(site,"The initiating callback must directly declare this one ask invocation.")
            if(!ref(site.argumentList.expressions.firstOrNull(),parameters.first())) fail(site,"The ask site must forward the initiating target unchanged.")
            val proof=protocol(site,actual)
            val info=hosts.inspect(creation,host.hostNodeId)
            val route=bridge.inspect(info,proof.hook,host,actual) ?: fail(proof.hook,"The callback hook has no unique standard AjaxButton submit route.")
            val body=callback.body as? PsiCodeBlock ?: fail(callback,"The confirmation consumer needs a straight-line declaration block.")
            val effects=mutableListOf<SubmissionEffect>();val dataCalls=mutableListOf<ConfirmationDataCall>()
            val selectionClears=mutableListOf<ConfirmationSelectionClear>()
            val selectionHelper=WicketSelectionClearDeclarations(attachments,::tick,::source,::descendants,::fail)
            val shared=WicketSubmissionEffects(callback,primary.rootNodeId,attachments::path,attachments::component,{component(it)},::descendants,::fail,callback.parameterList.parameters.single())
            for(statement in body.statements) {
                tick(statement);val c=call(statement) ?: fail(statement,"Confirmation callback control flow is outside the straight-line source contract.")
                val method=resolved(c)
                val declared=shared.declarations(c,method)
                if(declared!=null) effects+=declared else {
                    val selection=selectionHelper.inspect(c,method,callback.parameterList.parameters.single())
                    if(selection!=null) {
                        shared.acceptTargetForwarding(c.argumentList.expressions.single())
                        selectionClears+=selection
                        effects+=SubmissionEffect("SOURCE_SELECTION_CALL",method.name,location(c),selection.targetNodeId,selection.targetComponentPath,c.argumentList.expressions.map(::location))
                        effects+=SubmissionEffect("AJAX_REFRESH","add",selection.refreshLocation,selection.targetNodeId,selection.targetComponentPath,listOf(selection.refreshArgumentLocation))
                    } else {
                        dataCalls+=dataCall(c,method,parameters.last())
                        effects+=SubmissionEffect("SOURCE_DATA_CALL",method.name,location(c),primary.rootNodeId,"",c.argumentList.expressions.map(::location))
                    }
                }
                if(effects.size>limits.effects) throw Bound(statement)
            }
            shared.checkTargetUses()
            if(dataCalls.size!=1) fail(callback,"One bounded source data-call declaration is required.")
            val selected=linkedSetOf(primary.rootNodeId)
            fun include(id:String) {selected+=attachments.ancestry(id).map {it.nodeId}}
            include(route.trigger.nodeId);effects.forEach {include(it.targetNodeId)}
            val nodes=primary.nodes.filter {it.nodeId in selected}.map {it.copy(sourceSetupExpanded=false,ownedMarkupPath=null,fragment=null,template=true,idRenameSafe=false)}
            if(nodes.size>limits.nodes) throw Bound(callback)
            val slots=primary.matching!!.findings.filter {it.javaNodeId in selected}.mapNotNull {it.markupNodeId}.toSet()
            val markup=primary.markup!!.copy(elements=primary.markup.elements.map {it.copy(participatesInComponentTree=it.nodeId in slots,diagnostics=emptyList())},diagnostics=primary.markup.documentDiagnostics,scopeComplete=false)
            val statements=route.method.body!!.statements;val dispatchIndex=statements.indexOfFirst {call(it)==route.dispatch}
            val metadata=ConfirmationRoute(location(site),location(initiating),location(registration),location(registered),proof.setters.map(::location),location(proof.slot),location(proof.hook),location(proof.capture),location(proof.clear),location(proof.guard),location(proof.dispatch),location(proof.close),location(proof.closeClear),location(parameters.last()),dataCalls,selectionClears)
            val submission=SubmissionTemplate("confirmation:${page.qualifiedName}:${site.textOffset}",host.hostNodeId,attachments.path(host.hostNodeId),location(creation),host.slotParentNodeId,attachments.path(host.slotParentNodeId),route.trigger.nodeId,attachments.path(route.trigger.nodeId),"accept",location(callback),location(route.method),location(route.dispatch),statements.take(dispatchIndex).map(::location),statements.drop(dispatchIndex+1).map(::location),effects,confirmation=metadata)
            val warning=Diagnostic(DiagnosticCode.UNRESOLVED_SCOPE,"Conditional source confirmation declarations only. The initiating dispatch and stored callback identity/presence, rows, validation, data/equals/model effects, business/feedback outcomes, close/open state and later hierarchy remain unknown. No callback or data operation is executed." + if(selectionClears.isEmpty()) "" else " Selection-column dispatch and model/collection operations remain unknown.",location(callback))
            val report=PageReport(primary.qualifiedClassName,primary.rootNodeId,nodes,primary.markupPath,warnings=listOf(warning),markup=markup,javaSourceFiles=(primary.javaSourceFiles+sources).distinct().sorted(),submissionTemplate=submission)
            val matched=WicketGraphMatcher().match(report)
            if(matched.matching!!.findings.any {it.code!=DiagnosticCode.MATCHED}) fail(site,"The confirmation route has unmatched or ambiguous declared targets.")
            return matched
        }
        fun component(type:PsiType?)=(type as? PsiClassType)?.resolve()?.let {it.qualifiedName=="org.apache.wicket.Component" || com.intellij.psi.util.InheritanceUtil.isInheritor(it,"org.apache.wicket.Component")}==true
    }
}
private const val TARGET="org.apache.wicket.ajax.AjaxRequestTarget"
private fun location(e:PsiElement)=SourceLocation(e.containingFile.virtualFile.path,e.textOffset)
