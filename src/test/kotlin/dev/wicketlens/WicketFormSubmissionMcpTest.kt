package dev.wicketlens

import com.google.gson.JsonParser
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.psi.*
import com.intellij.psi.util.PsiTreeUtil
import dev.wicketlens.editor.*
import dev.wicketlens.export.WicketContextExporter
import dev.wicketlens.mcp.*
import dev.wicketlens.model.*

class WicketFormSubmissionMcpTest : WicketProjectPsiTestCase() {
    private fun file(path:String)=PsiManager.getInstance(project).findFile(myFixture.findFileInTempDir(path))!!
    fun testOptionalFormRoutesShareNativeValuesAndLeaveDefaultViewsUnchanged() {
        val java=WicketFormSubmissionFixture.install(myFixture)
        val files=listOf(java,file("src/main/java/example/SubmitButton.java"),file("src/main/resources/example/FormPage.html"));val before=files.map { it.text to it.modificationStamp }
        val query=WicketPageAnalysisRequest();val plain=query.capture(project,"example.FormPage");val optional=query.capture(project,"example.FormPage",includeNavigationContexts=true)
        assertEquals(WicketContextExporter().export(plain.report),WicketContextExporter().export(optional.report))
        val contexts=optional.navigationContexts!!.contexts.filter { it.report.submissionTemplate?.formCallback!=null }
        assertEquals(2,contexts.size)
        val native=WicketEditorAnalysis.navigationPages(java).filter { it.report.submissionTemplate?.formCallback!=null }
        assertEquals(contexts.map { WicketContextExporter().export(it.report) },native.map { WicketContextExporter().export(it.report) })
        val json=JsonParser.parseString(query.render(optional)).asJsonObject
        val exported=json["navigationContexts"].asJsonObject["contexts"].asJsonArray.filter { it.asJsonObject["submissionTemplate"]?.asJsonObject?.has("formCallback")==true }
        assertEquals(2,exported.size)
        exported.forEach { entry -> val context=entry.asJsonObject;val metadata=context["submissionTemplate"].asJsonObject
            assertEquals("SUBMISSION_TEMPLATE",context["relationship"].asString)
            assertEquals("SOURCE_DECLARED_FORM_CALLBACK",metadata["kind"].asString)
            assertEquals("OWN_COMPANION_DECLARATIONS",metadata["pathScope"].asString)
            assertEquals(if(metadata["callbackName"].asString=="onError") "SOURCE_ON_ERROR_ROUTE" else "SOURCE_ON_SUBMIT_ROUTE",metadata["dispatchCondition"].asString)
            assertEquals("not_evaluated",metadata["callbackExecution"].asString)
            assertTrue(metadata["bridgeLocation"].isJsonNull);assertTrue(metadata["dispatchLocation"].isJsonNull)
            for(key in listOf("initialHierarchy","inheritedMarkupPlacement","initializationEffects","formProcessingEffects")) assertEquals(key,"unknown",metadata["formCallback"].asJsonObject[key].asString)
            assertEquals(2,metadata["formCallback"].asJsonObject["attachmentLocations"].asJsonArray.size())
            for(key in listOf("validationResult","defaultFormProcessing","businessOutcome","feedbackOutcome","laterTreeState")) assertEquals(key,"unknown",metadata[key].asString)
            assertTrue(metadata["effects"].asJsonArray.all { it.asJsonObject["targetAttachmentPhase"].asString=="SOURCE_DECLARED_ADDITIONS" && it.asJsonObject["targetPathScope"].asString=="OWN_COMPANION_DECLARATIONS" })
            assertTrue(context["bindings"].asJsonArray.all { it.asJsonObject.has("ancestors") && it.asJsonObject.has("children") && it.asJsonObject["findings"].asJsonArray.single().asJsonObject["confidence"].asString=="INFERRED" })
        }
        assertFalse(json["analysis"].asJsonObject.has("submissionTemplate"))
        assertEquals(query.render(plain,WicketMcpReport.VALIDATION),query.render(optional,WicketMcpReport.VALIDATION))
        assertEquals(query.render(plain,WicketMcpReport.CONTEXT,nodeId=plain.report.rootNodeId),query.render(optional,WicketMcpReport.CONTEXT,nodeId=optional.report.rootNodeId))
        query.requireCurrent(project,optional);assertEquals(before,files.map { it.text to it.modificationStamp })
    }
    fun testDeclarationsCannotUpgradeAnUnresolvedInitialSelector() {
        WicketFormSubmissionFixture.install(myFixture,body=WicketFormSubmissionFixture.page.replace("public FormPage()", "private void configure() { removeAll(); } public FormPage()").replace(";add(form);",";add(form);configure();"))
        val query=WicketPageAnalysisRequest();val snapshot=query.capture(project,"example.FormPage",includeNavigationContexts=true)
        assertEquals(2,snapshot.navigationContexts!!.contexts.count { it.report.submissionTemplate?.formCallback!=null })
        try { query.render(snapshot,WicketMcpReport.CONTEXT,"invoiceForm:submit");fail("An optional source declaration must not select an unresolved initial path") }
        catch(e:PageAnalysisFailure) { assertTrue(e.code,e.code in setOf("COMPONENT_NOT_FOUND","UNRESOLVED_COMPONENT_PATH")) }
    }
    private fun stale(path:String,from:String,to:String) {
        WicketFormSubmissionFixture.install(myFixture)
        val query=WicketPageAnalysisRequest();val snapshot=query.capture(project,"example.FormPage",includeNavigationContexts=true)
        val source=file(path);assertTrue(snapshot.version.sources.toString(),snapshot.version.sources.any { it.path==source.virtualFile.path })
        WriteCommandAction.runWriteCommandAction(project) { PsiDocumentManager.getInstance(project).getDocument(source)!!.setText(source.text.replace(from,to)) }
        try { query.requireCurrent(project,snapshot);fail("Expected stale form callback snapshot") } catch(e:PageAnalysisFailure) { assertEquals("STALE_SNAPSHOT",e.code) }
    }
    fun testUnsavedCallbackInvalidatesSnapshot()=stale("src/main/java/example/FormPage.java","target.add(form);","target.add(FormPage.this);")
    fun testUnsavedButtonConstructorInvalidatesSnapshot()=stale("src/main/java/example/SubmitButton.java","super(id,label);","super(\"changed\",label);")
    fun testUnsavedOwnMarkupInvalidatesSnapshot()=stale("src/main/resources/example/FormPage.html","'submit'","'changed'")
    fun testNativeCallbackTriggerAndIdNavigationIsBidirectionalWithoutNewEditingAuthority() {
        val java=WicketFormSubmissionFixture.install(myFixture);myFixture.configureFromExistingVirtualFile(java.virtualFile)
        val callbacks=PsiTreeUtil.findChildrenOfType(java,PsiMethod::class.java).filter { it.name in setOf("onSubmit","onError") }
        assertEquals(2,callbacks.size)
        val id=PsiTreeUtil.findChildrenOfType(java,PsiLiteralExpression::class.java).single { it.value=="submit" }
        val priorRename=WicketIdRenameTarget.at(java,id.textOffset)!=null
        callbacks.forEach { callback ->
            val trigger=WicketSubmissionNavigation.targets(callback).single()
            val reverse=trigger.references.filterIsInstance<PsiPolyVariantReference>().single().multiResolve(false).mapNotNull { it.element }
            assertTrue(reverse.toString(),callbacks.all { c -> reverse.any { it==c.nameIdentifier } })
            assertTrue(reverse.contains(id))
            assertEquals(1,WicketSubmissionDeclarationHandler().getGotoDeclarationTargets(callback.nameIdentifier,callback.nameIdentifier!!.textOffset,myFixture.editor)?.size)
        }
        assertEquals(priorRename,WicketIdRenameTarget.at(java,id.textOffset)!=null)
        assertTrue(WicketEditorAnalysis.navigationPages(java).filter { it.report.submissionTemplate?.formCallback!=null }.all { it.report.nodes.filter { n->n.nodeId!=it.report.rootNodeId }.all { n->!n.idRenameSafe } })
    }
    fun testWholeResponseBoundAppliesToOrdinaryContexts() {
        WicketFormSubmissionFixture.install(myFixture);val query=WicketPageAnalysisRequest();val snapshot=query.capture(project,"example.FormPage",includeNavigationContexts=true)
        val navigation=snapshot.navigationContexts!!;val entry=navigation.contexts.first { it.report.submissionTemplate?.formCallback!=null }
        val large=entry.copy(report=entry.report.copy(warnings=listOf(Diagnostic(DiagnosticCode.PARTIAL_ANALYSIS,"x".repeat(600000)))))
        try { query.render(snapshot.copy(navigationContexts=navigation.copy(contexts=listOf(large))));fail("Expected response bound") } catch(e:PageAnalysisFailure) { assertEquals("RESPONSE_TOO_LARGE",e.code) }
    }
}
