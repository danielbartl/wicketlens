package dev.wicketlens

import com.google.gson.JsonParser
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.psi.*
import dev.wicketlens.editor.*
import dev.wicketlens.export.WicketContextExporter
import dev.wicketlens.mcp.*
import dev.wicketlens.model.*

class WicketSubmissionMcpTest : WicketProjectPsiTestCase() {
    private fun file(path:String)=PsiManager.getInstance(project).findFile(myFixture.findFileInTempDir(path))!!
    fun testOptionalSubmissionGraphsShareNativeValuesAndLeaveAllDefaultViewsUnchanged() {
        val java=WicketSubmissionFixture.install(myFixture)
        val files=listOf(java,file("src/main/java/example/DialogBox.java"),file("src/main/resources/example/DialogBox.html"),file("src/main/resources/example/SubmitPage.html"))
        val before=files.map { it.text to it.modificationStamp }
        val query=WicketPageAnalysisRequest();val plain=query.capture(project,"example.SubmitPage")
        val snapshot=query.capture(project,"example.SubmitPage",includeNavigationContexts=true)
        assertEquals(WicketContextExporter().export(plain.report),WicketContextExporter().export(snapshot.report))
        val contexts=snapshot.navigationContexts!!.contexts.filter { it.relationship=="SUBMISSION_TEMPLATE" }
        assertEquals(2,contexts.size)
        val native=WicketEditorAnalysis.navigationPages(java).filter { it.report.submissionTemplate!=null }
        assertEquals(contexts.map { WicketContextExporter().export(it.report) },native.map { WicketContextExporter().export(it.report) })
        val json=JsonParser.parseString(query.render(snapshot)).asJsonObject
        val exported=json["navigationContexts"].asJsonObject["contexts"].asJsonArray.filter { it.asJsonObject["relationship"].asString=="SUBMISSION_TEMPLATE" }
        assertEquals(2,exported.size)
        exported.forEach { entry ->
            val context=entry.asJsonObject;val metadata=context["submissionTemplate"].asJsonObject
            assertEquals("SOURCE_DECLARED_SUBMISSION",metadata["kind"].asString);assertEquals("SUBMISSION_TEMPLATE",metadata["phase"].asString)
            assertEquals("PAGE_DECLARED_ATTACHMENT",metadata["pathScope"].asString)
            assertEquals("SOURCE_ON_SUBMIT_ROUTE",metadata["dispatchCondition"].asString)
            assertEquals("not_evaluated",metadata["callbackExecution"].asString)
            for(key in listOf("validationResult","modelValues","defaultFormProcessing","businessOutcome","feedbackOutcome","postCallbackEffects","laterTreeState","openCloseState","behaviorEffects")) assertEquals(key,"unknown",metadata[key].asString)
            assertTrue(context["bindings"].asJsonArray.all { it.asJsonObject.has("ancestors") && it.asJsonObject.has("children") && it.asJsonObject["findings"].asJsonArray.single().asJsonObject["confidence"].asString=="INFERRED" })
        }
        assertFalse(json["analysis"].asJsonObject.has("submissionTemplate"))
        assertEquals(query.render(plain,WicketMcpReport.VALIDATION),query.render(snapshot,WicketMcpReport.VALIDATION))
        assertEquals(query.render(plain,WicketMcpReport.CONTEXT,"save:dialog:form:content"),query.render(snapshot,WicketMcpReport.CONTEXT,"save:dialog:form:content"))
        query.requireCurrent(project,snapshot);assertEquals(before,files.map { it.text to it.modificationStamp })
    }
    fun testReplacementChildrenStayOutsideInitialSelectors() {
        WicketSubmissionFixture.install(myFixture,body=WicketSubmissionFixture.page.replace("success(\"Saved\");", "Fragment c=new Fragment(DialogBox.CONTENT_ID,\"submitted\",SubmitPage.this);c.add(new Label(\"message\"));setContent(c);"))
        val query=WicketPageAnalysisRequest();val snapshot=query.capture(project,"example.SubmitPage",includeNavigationContexts=true)
        assertTrue(snapshot.navigationContexts!!.contexts.any { it.report.submissionTemplate?.contentNodeId!=null })
        try { query.render(snapshot,WicketMcpReport.CONTEXT,"save:dialog:form:content:message");fail("Submission-only children cannot select the initial graph") }
        catch(e:PageAnalysisFailure) { assertTrue(e.code,e.code in setOf("COMPONENT_NOT_FOUND","UNRESOLVED_COMPONENT_PATH")) }
    }
    private fun stale(path:String,from:String,to:String) {
        WicketSubmissionFixture.install(myFixture)
        val query=WicketPageAnalysisRequest();val snapshot=query.capture(project,"example.SubmitPage",includeNavigationContexts=true)
        val source=file(path);assertTrue(snapshot.version.sources.toString(),snapshot.version.sources.any { it.path==source.virtualFile.path })
        WriteCommandAction.runWriteCommandAction(project) { PsiDocumentManager.getInstance(project).getDocument(source)!!.setText(source.text.replace(from,to)) }
        try { query.requireCurrent(project,snapshot);fail("Expected stale submission snapshot") } catch(e:PageAnalysisFailure) { assertEquals("STALE_SNAPSHOT",e.code) }
    }
    fun testUnsavedHookBodyInvalidatesSnapshot()=stale("src/main/java/example/SubmitPage.java","target.add(people);","target.add(SubmitPage.this);")
    fun testUnsavedBridgeDispatchInvalidatesSnapshot()=stale("src/main/java/example/DialogBox.java","onConfirm(target);","if(target!=null) onConfirm(target);")
    fun testUnsavedGuardedSetterInvalidatesSnapshot()=stale("src/main/java/example/DialogBox.java","!CONTENT_ID.equals","CONTENT_ID.equals")
    fun testUnsavedTriggerMarkupInvalidatesSnapshot()=stale("src/main/resources/example/DialogBox.html","'confirm'","'changed'")
    fun testUnsavedRefreshTargetMarkupInvalidatesSnapshot()=stale("src/main/resources/example/SubmitPage.html","'people'","'changed'")
    fun testWholeSubmissionResponseIsBounded() {
        WicketSubmissionFixture.install(myFixture);val query=WicketPageAnalysisRequest();val snapshot=query.capture(project,"example.SubmitPage",includeNavigationContexts=true)
        val navigation=snapshot.navigationContexts!!;val event=navigation.contexts.first { it.report.submissionTemplate!=null }
        val large=event.copy(report=event.report.copy(warnings=listOf(Diagnostic(DiagnosticCode.PARTIAL_ANALYSIS,"x".repeat(600000)))))
        try { query.render(snapshot.copy(navigationContexts=navigation.copy(contexts=listOf(large))));fail("Expected response bound") }
        catch(e:PageAnalysisFailure) { assertEquals("RESPONSE_TOO_LARGE",e.code) }
    }
    fun testNativeHookAndTriggerNavigationIsBidirectionalWithoutRenameAuthority() {
        val java=WicketSubmissionFixture.install(myFixture)
        val callbacks=com.intellij.psi.util.PsiTreeUtil.findChildrenOfType(java,PsiMethod::class.java).filter { it.name=="onConfirm" }
        assertEquals(2,callbacks.size)
        callbacks.forEach { callback ->
            val targets=WicketSubmissionNavigation.targets(callback)
            assertEquals(1,targets.size);assertEquals("DialogBox.html",targets.single().containingFile.name)
            val reverse=targets.single().references.filterIsInstance<PsiPolyVariantReference>().flatMap { it.multiResolve(false).toList() }.mapNotNull { it.element }
            assertTrue(reverse.toString(),callbacks.all { callback -> reverse.any { it==callback.nameIdentifier } })
            assertNull(WicketIdRenameTarget.at(targets.single().containingFile,targets.single().textOffset))
        }
    }

    fun testFreshContentConstructorAndFragmentChildrenNavigateWithoutPairedRename() {
        val java=WicketSubmissionFixture.install(myFixture,body=WicketSubmissionFixture.page.replace("success(\"Saved\");", "Fragment c=new Fragment(DialogBox.CONTENT_ID,\"submitted\",SubmitPage.this);c.add(new Label(\"message\"));setContent(c);"))
        val creation=com.intellij.psi.util.PsiTreeUtil.findChildrenOfType(java,PsiNewExpression::class.java).single { it.classReference?.referenceName=="Fragment" }
        val slot=WicketReplacementNavigation.targets(creation).single()
        assertEquals("DialogBox.html",slot.containingFile.name)
        assertTrue(WicketReplacementNavigation.targets(slot).contains(creation))
        val literal=com.intellij.psi.util.PsiTreeUtil.findChildrenOfType(java,PsiLiteralExpression::class.java).single { it.value=="message" }
        val counterpart=literal.references.filterIsInstance<PsiPolyVariantReference>().single().multiResolve(false).single().element!!
        assertEquals("SubmitPage.html",counterpart.containingFile.name)
        assertTrue(counterpart.references.filterIsInstance<PsiPolyVariantReference>().single().multiResolve(false).any { it.element==literal })
        assertNull(WicketIdRenameTarget.at(literal.containingFile,literal.textOffset))
        assertNull(WicketIdRenameTarget.at(counterpart.containingFile,counterpart.textOffset))
        myFixture.configureFromExistingVirtualFile(java.virtualFile)
        val handler=WicketSubmissionDeclarationHandler()
        val hook=com.intellij.psi.util.PsiTreeUtil.findChildrenOfType(java,PsiMethod::class.java).first { it.name=="onConfirm" }
        val name=hook.nameIdentifier!!
        assertEquals(1,handler.getGotoDeclarationTargets(name,name.textOffset,myFixture.editor)?.size)
    }
}
