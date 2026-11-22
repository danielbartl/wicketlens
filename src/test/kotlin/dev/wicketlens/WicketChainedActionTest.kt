package dev.wicketlens

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.progress.*
import com.intellij.openapi.progress.util.ProgressIndicatorBase
import com.intellij.psi.*
import com.intellij.psi.util.PsiTreeUtil
import dev.wicketlens.analysis.*
import dev.wicketlens.model.*

class WicketChainedActionTest : WicketProjectPsiTestCase() {
    private fun install(page:String=WicketRecordActionFixture.chainedPage,actions:String=WicketConfirmationFixture.actions):PsiJavaFile {
        val java=WicketRecordActionFixture.install(myFixture,page)
        val source=PsiManager.getInstance(project).findFile(myFixture.findFileInTempDir("src/main/java/example/Actions.java"))!!
        WriteCommandAction.runWriteCommandAction(project) {PsiDocumentManager.getInstance(project).getDocument(source)!!.setText(actions)}
        PsiDocumentManager.getInstance(project).commitAllDocuments();return java
    }
    private fun withheld(page:String=WicketRecordActionFixture.chainedPage,actions:String=WicketConfirmationFixture.actions) {
        val java=install(page,actions);val primary=WicketPageAnalyzer().analyze(java.classes.single())!!
        assertTrue(primary.storedActionRegistrations.isEmpty())
        assertTrue(WicketActionContexts().analyze(java.classes.single(),primary).reports.isEmpty())
    }
    fun testEachChainLinkHasDistinctRegistrationProvenanceAndAnIndependentInferredContext() {
        val java=install();val primary=WicketPageAnalyzer().analyze(java.classes.single())!!
        val sites=primary.storedActionRegistrations
        assertEquals(primary.warnings.toString(),2,sites.size)
        assertEquals(2,sites.map {it.registrationLocation}.distinct().size)
        assertTrue(sites.all {java.text.substring(it.registrationLocation.offset).startsWith("addAction")})
        assertEquals(1,sites.map {it.allocationLocation}.distinct().size);assertEquals(1,sites.map {it.slotLocation}.distinct().size)
        val result=WicketActionContexts().analyze(java.classes.single(),primary)
        assertEquals(result.diagnostics.toString(),2,result.reports.size)
        for(report in result.reports) {
            assertTrue(sites.any {it.callbackLocation==report.actionTemplate!!.callbackLocation && it.registrationLocation==report.actionTemplate.registrationLocation})
            assertTrue(report.nodes.all {it.template && !it.idRenameSafe})
            assertTrue(report.matching!!.findings.single().let {it.code==DiagnosticCode.MATCHED && it.confidence==Confidence.INFERRED})
        }
        assertEquals(setOf("example.ConfirmationPage.State.OPEN","example.ConfirmationPage.State.PAID"),result.reports.map {it.actionTemplate!!.updates.single().componentArguments.last().declarationName}.toSet())
    }
    fun testMixedStandaloneAndChainedStatementsAreAuditedTogether() {
        val body=WicketRecordActionFixture.chainedPage.replace("actions.addAction", "actions.addAction(\"Extra\",(target,row)->{target.add(table);});actions.addAction")
        val java=install(body);val primary=WicketPageAnalyzer().analyze(java.classes.single())!!
        assertEquals(3,primary.storedActionRegistrations.size)
        assertEquals(2,WicketActionContexts().analyze(java.classes.single(),primary).reports.size)
    }
    fun testDifferentStorageMethodsCanContinueTheSameChain() {
        val actions=WicketConfirmationFixture.actions.replace("public void click", "public Actions<T> register(String label,SerializableBiConsumer<AjaxRequestTarget,T> handler) {this.action=handler;return this;} public void click")
        val java=install(WicketRecordActionFixture.chainedPage.replace(").addAction(",").register("),actions)
        val primary=WicketPageAnalyzer().analyze(java.classes.single())!!
        assertEquals(2,primary.storedActionRegistrations.map {it.methodLocation}.distinct().size)
        assertEquals(2,WicketActionContexts().analyze(java.classes.single(),primary).reports.size)
    }
    fun testAReplacementReceiverInvalidatesAllLinks()=withheld(actions=WicketConfirmationFixture.actions.replace("return this;","return new Actions<>();"))
    fun testANullReceiverReturnInvalidatesAllLinks()=withheld(actions=WicketConfirmationFixture.actions.replace("return this;","return null;"))
    fun testReturningTheCallbackInsteadOfThisIsWithheld()=withheld(actions=WicketConfirmationFixture.actions.replace("return this;","return (Actions<T>)(Object)handler;"))
    fun testAnEagerFinalLinkInvalidatesTheWholeChain() {
        val actions=WicketConfirmationFixture.actions.replace("public void click", "public Actions<T> eager(String label,SerializableBiConsumer<AjaxRequestTarget,T> handler) {handler.accept(null,null);return this;} public void click")
        withheld(WicketRecordActionFixture.chainedPage.replace(").addAction(",").eager("),actions)
    }
    fun testAnUnreviewedBridgeBetweenLinksIsWithheld() {
        val actions=WicketConfirmationFixture.actions.replace("public void click", "public Actions<T> bridge() {return this;} public void click")
        withheld(WicketRecordActionFixture.chainedPage.replace(").addAction(",").bridge().addAction("),actions)
    }
    fun testFinalDispatchInsteadOfStorageInvalidatesTheChain()=withheld(page=WicketRecordActionFixture.chainedPage.replace("}).addAction(\"Paid\",(target,row)->{markPaid(List.of(row));target.add(table);})", "}).click(null,null)"))
    fun testAChainedResultCannotEscapeAsAnAlias()=withheld(page=WicketRecordActionFixture.chainedPage.replace("actions.addAction", "var alias=actions.addAction"))
    fun testAChainedResultCannotEscapeAsAnArgument()=withheld(page=WicketRecordActionFixture.chainedPage.replace("actions.addAction", "Objects.requireNonNull(actions.addAction").replace("markPaid(List.of(row));target.add(table);});", "markPaid(List.of(row));target.add(table);}));"))
    fun testLaterAliasInvalidatesAnEarlierSafeChain()=withheld(page=WicketRecordActionFixture.chainedPage.replace("            }\n            private void markOpen", "                var alias=actions;\n            }\n            private void markOpen"))
    fun testCallbackCaptureInvalidatesAllLinks()=withheld(page=WicketRecordActionFixture.chainedPage.replace("markOpen(List.of(row));", "actions.click(target,row);markOpen(List.of(row));"))
    fun testBareAllocationChainsUseTheSameStorageProof() {
        val java = install(WicketRecordActionFixture.chainedPage
            .replace("Actions<Row> actions=new Actions<>();", "").replace("actions.addAction", "new Actions<Row>().addAction"))
        val primary = WicketPageAnalyzer().analyze(java.classes.single())!!
        assertEquals(primary.warnings.toString(), 2, primary.storedActionRegistrations.size)
        assertEquals(1, primary.storedActionRegistrations.map { it.allocationLocation }.distinct().size)
        assertEquals(2, WicketActionContexts().analyze(java.classes.single(), primary).reports.size)
    }

    fun testChainedInitializerUsesTheSameStorageAndAttachmentContract() {
        val java=install(WicketRecordActionFixture.initializedPage)
        val primary=WicketPageAnalyzer().analyze(java.classes.single())!!
        assertEquals(primary.warnings.toString(),2,primary.storedActionRegistrations.size)
        assertEquals(2,WicketActionContexts().analyze(java.classes.single(),primary).reports.size)
    }
    fun testSixtyFourLinksAreAcceptedAndTheLimitCountsLinksRatherThanLocalReferences() {
        val link=".addAction(\"Extra\",(target,row)->{target.add(table);})"
        val java=install(WicketRecordActionFixture.chainedPage.replace(").addAction(\"Paid\"", ")"+link.repeat(62)+".addAction(\"Paid\""))
        val primary=WicketPageAnalyzer().analyze(java.classes.single())!!
        assertEquals(primary.warnings.toString(),64,primary.storedActionRegistrations.size)
        assertEquals(64,primary.storedActionRegistrations.map {it.registrationLocation}.distinct().size)
    }
    fun testSixtyFiveLinksPublishNoPartialGroup() {
        val link=".addAction(\"Extra\",(target,row)->{target.add(table);})"
        val java=install(WicketRecordActionFixture.chainedPage.replace(").addAction(\"Paid\"", ")"+link.repeat(63)+".addAction(\"Paid\""))
        val primary=WicketPageAnalyzer().analyze(java.classes.single())!!
        assertTrue(primary.storedActionRegistrations.isEmpty())
        assertTrue(primary.warnings.toString(),primary.warnings.any {"bounded registration count (64)" in it.message})
    }
    fun testCancellationBeforeAChainCompletesCannotCacheItsFirstLink() {
        val java=install();val calls=PsiTreeUtil.findChildrenOfType(java,PsiMethodCallExpression::class.java).filter {it.methodExpression.referenceName=="addAction"}.sortedBy {it.argumentList.textOffset}
        val method=calls.first().resolveMethod()!!;var reads=0;val indicator=ProgressIndicatorBase()
        val proof=WicketStoredActionDeclarations(java.classes.single(),{ProgressManager.checkCanceled()},{if(it==method && ++reads==3) indicator.cancel()},{_,message->throw IllegalStateException(message)},{})
        try {ProgressManager.getInstance().runProcess(Runnable {proof.inspect(calls.first());fail("Expected cancellation before publication")},indicator);fail("Expected cancellation")}
        catch (_: ProcessCanceledException) {}
        ProgressManager.getInstance().runProcess(Runnable {assertNotNull(proof.inspect(calls.first()));assertNotNull(proof.inspect(calls.last()))},ProgressIndicatorBase())
        assertTrue(reads>3)
    }
}
