package dev.wicketlens.analysis

import com.intellij.openapi.progress.ProgressManager
import com.intellij.psi.*
import com.intellij.psi.util.InheritanceUtil
import com.intellij.psi.util.PsiTreeUtil
import dev.wicketlens.model.*

/** Separate source callback declarations. The source registration is not a reconstructed UI dispatch route. */
internal class WicketActionContexts(private val limits:WicketSubmissionContexts.Limits=WicketSubmissionContexts.Limits()) {
    private class Unknown(val at:PsiElement,override val message:String):RuntimeException(null,null,false,false)
    private class Bound(val at:PsiElement):RuntimeException(null,null,false,false)
    fun analyze(page:PsiClass,primary:PageReport):WicketSubmissionContexts.Result=Session(page,primary).run()
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
        val attachments=WicketDeclaredAttachments(page,primary,limits.depth,::tick,::descendants,::fail,{throw Bound(it)}) {primary.rootNodeId}
        val consumers=WicketConsumerDeclarations(limits.depth,::tick,::source,::fail)
        val constants=WicketSourceConstants(::source) {tick(page)}
        val dataTypes by lazy {WicketConstructorDataLoop(page.constructors.single(),::tick,::source,constants::expression,{false},{source(it)},::fail)}
        val updates=WicketRecordUpdateDeclarations(page,::tick,::source,::descendants,{dataTypes.dataType(it)},::fail)
        val selection=WicketSelectionClearDeclarations(attachments,::tick,::source,::descendants,::fail)
        fun resolved(call:PsiMethodCallExpression):PsiMethod {
            tick(call)
            if(!call.resolveMethodGenerics().isValidResult) fail(call,"The action call is inaccessible or unresolved.")
            return call.resolveMethod() ?: fail(call,"The action call is unresolved.")
        }
        fun run():WicketSubmissionContexts.Result {
            if(primary.markup==null || page.constructors.size!=1) return WicketSubmissionContexts.Result(emptyList(),emptyList(),emptyList(),true)
            try {
                for(callback in descendants(page).filterIsInstance<PsiLambdaExpression>()) {
                    val parameters=callback.parameterList.parameters
                    if(parameters.size!=2 || parameters.first().type.canonicalText!=TARGET || callback.body !is PsiCodeBlock) continue
                    val body=callback.body as PsiCodeBlock
                    val candidate=descendants(body).filterIsInstance<PsiMethodCallExpression>().any {call -> call.resolveMethod()?.let(updates::candidate)==true}
                    if(!candidate) continue
                    source(page)
                    if(reports.size>=limits.contexts) throw Bound(callback)
                    try {reports+=project(callback)}
                    catch(e:Unknown) {diagnostics+=Diagnostic(DiagnosticCode.UNRESOLVED_SCOPE,"Action template withheld: ${e.message}",location(e.at))}
                }
            } catch(e:Bound) {
                return WicketSubmissionContexts.Result(emptyList(),sources.sorted(),listOf(Diagnostic(DiagnosticCode.PARTIAL_ANALYSIS,"Action discovery exceeded its bounded source contract; no action graphs published.",location(e.at))),false)
            } catch(e:Unknown) {
                return WicketSubmissionContexts.Result(emptyList(),sources.sorted(),listOf(Diagnostic(DiagnosticCode.UNRESOLVED_SCOPE,"Action discovery withheld: ${e.message}",location(e.at))),false)
            }
            return WicketSubmissionContexts.Result(reports.toList(),sources.sorted(),diagnostics.toList(),true)
        }
        fun project(callback:PsiLambdaExpression):PageReport {
            val registration=callback.parent.parent as? PsiMethodCallExpression ?: fail(callback,"The action callback must be a direct source registration argument.")
            val constructor=PsiTreeUtil.getParentOfType(callback,PsiMethod::class.java)
            if(constructor?.isConstructor!=true || constructor.containingClass!=page || PsiTreeUtil.getParentOfType(callback,PsiLambdaExpression::class.java,true)!=null)
                fail(callback,"This action contract only describes direct selected-page constructor callback registrations.")
            val method=resolved(registration);source(method)
            val index=registration.argumentList.expressions.indexOf(callback)
            if(method is PsiCompiledElement || index<0) fail(registration,"The callback registration must have a source method declaration.")
            consumers.inspect(method.parameterList.parameters.getOrNull(index)?.type,2,callback)
            val parameters=callback.parameterList.parameters
            val effects=mutableListOf<SubmissionEffect>();val recordUpdates=mutableListOf<RecordUpdateCall>();val clears=mutableListOf<ConfirmationSelectionClear>()
            val shared=WicketSubmissionEffects(callback,primary.rootNodeId,attachments::path,attachments::component,::component,::descendants,::fail,parameters.first())
            for(statement in (callback.body as PsiCodeBlock).statements) {
                tick(statement)
                val call=(statement as? PsiExpressionStatement)?.expression as? PsiMethodCallExpression ?: fail(statement,"The action callback must have only bounded straight-line source declarations.")
                val target=resolved(call)
                val ordinary=shared.declarations(call,target)
                if(ordinary!=null) effects+=ordinary else {
                    val clear=selection.inspect(call,target,parameters.first())
                    if(clear!=null) {
                        shared.acceptTargetForwarding(call.argumentList.expressions.single());clears+=clear
                        effects+=SubmissionEffect("SOURCE_SELECTION_CALL",target.name,location(call),clear.targetNodeId,clear.targetComponentPath,call.argumentList.expressions.map(::location))
                        effects+=SubmissionEffect("AJAX_REFRESH","add",clear.refreshLocation,clear.targetNodeId,clear.targetComponentPath,listOf(clear.refreshArgumentLocation))
                    } else {
                        recordUpdates+=updates.inspect(call,target,parameters.last())
                        effects+=SubmissionEffect("SOURCE_RECORD_UPDATE_CALL",target.name,location(call),primary.rootNodeId,"",call.argumentList.expressions.map(::location))
                    }
                }
                if(effects.size>limits.effects) throw Bound(statement)
            }
            shared.checkTargetUses()
            if(recordUpdates.size!=1) fail(callback,"One reviewed record replacement helper declaration is required.")
            val selected=linkedSetOf(primary.rootNodeId)
            effects.forEach {selected+=attachments.ancestry(it.targetNodeId).map {node->node.nodeId}}
            val nodes=primary.nodes.filter {it.nodeId in selected}.map {it.copy(sourceSetupExpanded=false,ownedMarkupPath=null,fragment=null,template=true,idRenameSafe=false)}
            if(nodes.size>limits.nodes) throw Bound(callback)
            val slots=primary.matching!!.findings.filter {it.javaNodeId in selected}.mapNotNull {it.markupNodeId}.toSet()
            val markup=primary.markup!!.copy(elements=primary.markup.elements.map {it.copy(participatesInComponentTree=it.nodeId in slots,diagnostics=emptyList())},diagnostics=primary.markup.documentDiagnostics,scopeComplete=false)
            val registrationLocation=primary.storedActionRegistrations.singleOrNull {it.callbackLocation==location(callback)}?.registrationLocation ?: location(registration)
            val metadata=ActionTemplate("action:${page.qualifiedName}:${callback.textOffset}",location(callback),registrationLocation,location(method),registration.methodExpression.qualifierExpression?.let(::location),location(parameters.last()),recordUpdates,effects,clears)
            val warning=Diagnostic(DiagnosticCode.UNRESOLVED_SCOPE,"Conditional source action declarations only. Constructor/configuration effects, registration/storage, UI dispatch, visibility, rows, loop counts, equality and enum initialization effects, data/model values, mutation/business/feedback outcomes and later hierarchy remain unknown. No callback, predicate, data helper, record constructor or model is executed; no runtime trigger ID is reconstructed.",location(callback))
            val result=WicketGraphMatcher().match(PageReport(primary.qualifiedClassName,primary.rootNodeId,nodes,primary.markupPath,warnings=listOf(warning),markup=markup,javaSourceFiles=(primary.javaSourceFiles+sources).distinct().sorted(),actionTemplate=metadata))
            if(result.matching!!.findings.any {it.code!=DiagnosticCode.MATCHED}) fail(callback,"The action targets do not have unique initial markup counterparts.")
            return result
        }
        fun component(type:PsiType?)=(type as? PsiClassType)?.resolve()?.let {it.qualifiedName=="org.apache.wicket.Component" || InheritanceUtil.isInheritor(it,"org.apache.wicket.Component")}==true
    }
}
private const val TARGET="org.apache.wicket.ajax.AjaxRequestTarget"
private fun location(e:PsiElement)=SourceLocation(e.containingFile.virtualFile.path,e.textOffset)
