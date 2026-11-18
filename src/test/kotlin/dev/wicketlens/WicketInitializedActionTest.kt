package dev.wicketlens

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.progress.*
import com.intellij.openapi.progress.util.ProgressIndicatorBase
import com.intellij.psi.*
import com.intellij.psi.util.PsiTreeUtil
import dev.wicketlens.analysis.*
import dev.wicketlens.model.*

class WicketInitializedActionTest : WicketProjectPsiTestCase() {
    private fun install(body:String=WicketRecordActionFixture.initializedPage,actions:String=WicketConfirmationFixture.actions):PsiJavaFile {
        val java=WicketRecordActionFixture.install(myFixture,body)
        val source=PsiManager.getInstance(project).findFile(myFixture.findFileInTempDir("src/main/java/example/Actions.java"))!!
        WriteCommandAction.runWriteCommandAction(project) {PsiDocumentManager.getInstance(project).getDocument(source)!!.setText(actions)}
        PsiDocumentManager.getInstance(project).commitAllDocuments();return java
    }
    private fun withheld(body:String=WicketRecordActionFixture.initializedPage,actions:String=WicketConfirmationFixture.actions) {
        val java=install(body,actions);val initial=WicketPageAnalyzer().analyze(java.classes.single())!!
        assertTrue(initial.storedActionRegistrations.isEmpty())
        assertTrue(WicketActionContexts().analyze(java.classes.single(),initial).reports.isEmpty())
    }
    fun testInitializerOnlyLocalHasOneFreshIdentityAndTwoDistinctInferredActionContexts() {
        val java=install();val initial=WicketPageAnalyzer().analyze(java.classes.single())!!
        val sites=initial.storedActionRegistrations
        assertEquals(initial.warnings.toString(),2,sites.size)
        assertEquals(1,sites.map {it.allocationLocation}.distinct().size);assertEquals(1,sites.map {it.slotLocation}.distinct().size)
        assertTrue(java.text.substring(sites.first().allocationLocation.offset).startsWith("new Actions<Row>()"))
        assertEquals(2,sites.map {it.registrationLocation}.distinct().size)
        assertTrue(sites.all {java.text.substring(it.registrationLocation.offset).startsWith("addAction")})
        val actions=WicketActionContexts().analyze(java.classes.single(),initial)
        assertEquals(actions.diagnostics.toString(),2,actions.reports.size)
        assertTrue(actions.reports.all {it.matching!!.findings.single().let {f->f.code==DiagnosticCode.MATCHED && f.confidence==Confidence.INFERRED}})
        assertTrue(initial.nodes.filter {it.nodeId!=initial.rootNodeId}.all {it.template && !it.idRenameSafe})
    }
    fun testVarLocalCanUseTheActualGenericAllocationType() {
        val java=install(WicketRecordActionFixture.initializedPage.replace("Actions<Row> actions=", "var actions="))
        assertEquals(2,WicketPageAnalyzer().analyze(java.classes.single())!!.storedActionRegistrations.size)
    }
    fun testSingleInitializerLinkAlsoUsesItsMethodToken() {
        val body=WicketRecordActionFixture.plainPage.replace("Actions<Row> actions=new Actions<>();\n                actions.addAction", "Actions<Row> actions=new Actions<Row>().addAction")
        val java=install(body);val initial=WicketPageAnalyzer().analyze(java.classes.single())!!
        val site=initial.storedActionRegistrations.single()
        assertTrue(java.text.substring(site.registrationLocation.offset).startsWith("addAction"))
        assertEquals(1,WicketActionContexts().analyze(java.classes.single(),initial).reports.size)
    }
    private fun later(statement:String)=WicketRecordActionFixture.initializedPage.replace("            }\n            private void markOpen", "                $statement\n            }\n            private void markOpen")
    fun testInitializerAndLaterRegistrationsShareOneAuditedAllocation() {
        val java=install(later("actions.addAction(\"Later\",(target,row)->{target.add(table);});"))
        val initial=WicketPageAnalyzer().analyze(java.classes.single())!!
        assertEquals(3,initial.storedActionRegistrations.size)
        assertEquals(1,initial.storedActionRegistrations.map {it.allocationLocation}.distinct().size)
        assertEquals(2,WicketActionContexts().analyze(java.classes.single(),initial).reports.size)
    }
    fun testSeparateInitializerLocalsDoNotMergeAllocationIdentities() {
        val java=install(later("var other=new Actions<Row>().addAction(\"Other\",(target,row)->{target.add(table);});"))
        val sites=WicketPageAnalyzer().analyze(java.classes.single())!!.storedActionRegistrations
        assertEquals(3,sites.size);assertEquals(2,sites.map {it.allocationLocation}.distinct().size)
    }
    fun testLaterAliasInvalidatesTheInitializer()=withheld(later("var alias=actions;"))
    fun testLaterReassignmentInvalidatesTheInitializer()=withheld(later("actions=new Actions<>();"))
    fun testLaterDispatchInvalidatesTheInitializer()=withheld(later("actions.click(null,null);"))
    fun testLaterCallbackCaptureInvalidatesTheInitializer()=withheld(later("actions.addAction(\"Captured\",(target,row)->{actions.click(target,row);});"))
    fun testInitializerCannotCaptureItsOwnIncompleteLocal()=withheld(WicketRecordActionFixture.initializedPage.replace("markOpen(List.of(row));", "actions.click(target,row);markOpen(List.of(row));"))
    fun testForeignReceiverReturnInvalidatesEveryInitializerLink()=withheld(actions=WicketConfirmationFixture.actions.replace("return this;","return new Actions<>();"))
    fun testEagerInitializerMethodIsWithheld()=withheld(actions=WicketConfirmationFixture.actions.replace("action=handler;", "handler.accept(null,null);action=handler;"))
    fun testEffectfulAllocationConstructorIsWithheld()=withheld(actions=WicketConfirmationFixture.actions.replace("public Actions() {}", "public Actions() {System.gc();}"))
    fun testAnonymousAllocationOverrideIsWithheld()=withheld(WicketRecordActionFixture.initializedPage.replace("new Actions<Row>()", "new Actions<Row>() {}"))
    fun testFactoryRootedInitializerIsWithheld()=withheld(WicketRecordActionFixture.initializedPage.replace("new Actions<Row>()", "factory()").replace("private void markOpen", "private Actions<Row> factory() {return new Actions<>();} private void markOpen"))
    fun testBorrowedInitializerReceiverIsWithheld()=withheld(WicketRecordActionFixture.initializedPage.replace("public ConfirmationPage()", "public ConfirmationPage(Actions<Row> borrowed)").replace("new Actions<Row>()", "borrowed"))
    fun testInitializerCannotBeWrappedInAnOpaqueCall()=withheld(WicketRecordActionFixture.initializedPage.replace("actions=new Actions<Row>()", "actions=Objects.requireNonNull(new Actions<Row>()").replace("markPaid(List.of(row));target.add(table);});", "markPaid(List.of(row));target.add(table);}));"))
    fun testFieldInitializerDoesNotBorrowConstructorLocalAuthority()=withheld(WicketRecordActionFixture.initializedPage.replace("public ConfirmationPage()", "private Actions<Row> actions;public ConfirmationPage()").replace("Actions<Row> actions=", "actions="))
    fun testSixtyFourInitializerAndLaterLinksFitOneSharedLimit() {
        val extra="actions.addAction(\"Extra\",(target,row)->{target.add(table);});"
        val java=install(later(extra.repeat(62)));val initial=WicketPageAnalyzer().analyze(java.classes.single())!!
        assertEquals(initial.warnings.toString(),64,initial.storedActionRegistrations.size)
    }
    fun testSixtyFiveInitializerAndLaterLinksWithholdTheEntireGroup() {
        val extra="actions.addAction(\"Extra\",(target,row)->{target.add(table);});"
        val java=install(later(extra.repeat(63)));val initial=WicketPageAnalyzer().analyze(java.classes.single())!!
        assertTrue(initial.storedActionRegistrations.isEmpty())
        assertTrue(initial.warnings.toString(),initial.warnings.any {"bounded registration count (64)" in it.message})
    }
    fun testCancellationCannotPublishOrCacheAnInitializerPrefix() {
        val java=install();val calls=PsiTreeUtil.findChildrenOfType(java,PsiMethodCallExpression::class.java).filter {it.methodExpression.referenceName=="addAction"}.sortedBy {it.argumentList.textOffset}
        val method=calls.first().resolveMethod()!!;var reads=0;val indicator=ProgressIndicatorBase()
        val proof=WicketStoredActionDeclarations(java.classes.single(),{ProgressManager.checkCanceled()},{if(it==method && ++reads==3) indicator.cancel()},{_,message->throw IllegalStateException(message)},{})
        try {ProgressManager.getInstance().runProcess(Runnable {proof.inspect(calls.first());fail("Expected cancellation")},indicator);fail("Expected cancellation")}
        catch (_: ProcessCanceledException) {}
        ProgressManager.getInstance().runProcess(Runnable {assertNotNull(proof.inspect(calls.first()));assertNotNull(proof.inspect(calls.last()))},ProgressIndicatorBase())
        assertTrue(reads>3)
    }
}
