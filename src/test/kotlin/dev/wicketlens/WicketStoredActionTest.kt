package dev.wicketlens

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.progress.*
import com.intellij.psi.*
import dev.wicketlens.analysis.*
import dev.wicketlens.model.*

class WicketStoredActionTest : WicketProjectPsiTestCase() {
    private fun install(page: String = WicketRecordActionFixture.plainPage, actions: String = WicketConfirmationFixture.actions): PsiJavaFile {
        val java = WicketRecordActionFixture.install(myFixture, page)
        val source = PsiManager.getInstance(project).findFile(myFixture.findFileInTempDir("src/main/java/example/Actions.java"))!!
        WriteCommandAction.runWriteCommandAction(project) { PsiDocumentManager.getInstance(project).getDocument(source)!!.setText(actions) }
        PsiDocumentManager.getInstance(project).commitAllDocuments()
        return java
    }
    private fun withheld(page: String = WicketRecordActionFixture.plainPage, actions: String = WicketConfirmationFixture.actions) {
        val java = install(page, actions)
        val initial = WicketPageAnalyzer().analyze(java.classes.single())!!
        assertTrue(initial.storedActionRegistrations.isEmpty())
        assertTrue(WicketActionContexts().analyze(java.classes.single(), initial).reports.isEmpty())
    }
    fun testInitialTableAttachmentAndStorageLocationsAreInferredWithoutRenameAuthority() {
        val java = install()
        val report = WicketPageAnalyzer().analyze(java.classes.single())!!
        val table = report.nodes.single { it.wicketId == "rows" }
        assertEquals(report.rootNodeId, table.parentNodeId)
        assertTrue(table.template); assertFalse(table.idRenameSafe); assertFalse(report.scopeComplete)
        assertEquals(Confidence.INFERRED, report.matching!!.findings.single { it.javaNodeId == table.nodeId }.confidence)
        val proof = report.storedActionRegistrations.single()
        assertEquals(java.virtualFile.path, proof.registrationLocation.file)
        assertEquals(java.virtualFile.path, proof.callbackLocation.file)
        assertEquals(java.virtualFile.path, proof.allocationLocation.file)
        assertTrue(proof.methodLocation.file.endsWith("/Actions.java")); assertEquals(proof.methodLocation.file, proof.slotLocation.file)
        assertTrue(report.javaSourceFiles.contains(proof.methodLocation.file))
        val formatted = dev.wicketlens.ui.WicketReportFormatter().format(report)
        assertTrue(formatted.contains("source-proved callback storage")); assertTrue(formatted.contains("MATCHED / INFERRED"))
    }
    fun testOwnQualifiedSlotIsSupported() {
        val java=install(actions=WicketConfirmationFixture.actions.replace("action=handler", "this.action=handler"))
        assertEquals(1,WicketPageAnalyzer().analyze(java.classes.single())!!.storedActionRegistrations.size)
    }
    fun testImplicitEmptyRegistrarConstructorIsSupported() {
        val java=install(actions=WicketConfirmationFixture.actions.replace("public Actions() {}", ""))
        assertEquals(1,WicketPageAnalyzer().analyze(java.classes.single())!!.storedActionRegistrations.size)
    }
    fun testEagerInvocationIsWithheld() = withheld(actions=WicketConfirmationFixture.actions.replace("action=handler;", "handler.accept(null,null);action=handler;"))
    fun testHiddenEagerHelperIsWithheld() = withheld(actions=WicketConfirmationFixture.actions.replace("action=handler;", "run(handler);action=handler;").replace("public void click", "private void run(SerializableBiConsumer<AjaxRequestTarget,T> handler) {handler.accept(null,null);} public void click"))
    fun testReplacedCallbackIsWithheld() = withheld(actions=WicketConfirmationFixture.actions.replace("action=handler", "action=null"))
    fun testAliasedCallbackIsWithheld() = withheld(actions=WicketConfirmationFixture.actions.replace("action=handler;", "var alias=handler;action=alias;"))
    fun testEffectfulConstructorIsWithheld() = withheld(actions=WicketConfirmationFixture.actions.replace("public Actions() {}", "public Actions() {System.gc();}"))
    fun testInitializedSlotIsWithheld() = withheld(actions=WicketConfirmationFixture.actions.replace(" action;", " action=(target,row)->{};"))
    fun testAdditionalFieldInitializationIsWithheld() = withheld(actions=WicketConfirmationFixture.actions.replace("public Actions()", "private Object other=new Object();public Actions()"))
    fun testStaticInitializationIsWithheld() = withheld(actions=WicketConfirmationFixture.actions.replace("public Actions()", "static {System.gc();} public Actions()"))
    fun testNonprivateSlotIsWithheld() = withheld(actions=WicketConfirmationFixture.actions.replace("private SerializableBiConsumer", "public SerializableBiConsumer"))
    fun testForeignReceiverReturnIsWithheld() = withheld(actions=WicketConfirmationFixture.actions.replace("return this;", "return new Actions<>();"))
    fun testBorrowedRegistrarIsWithheld() = withheld(page=WicketRecordActionFixture.plainPage.replace("public ConfirmationPage()", "public ConfirmationPage(Actions<Row> actions)").replace("Actions<Row> actions=new Actions<>();", ""))
    fun testAliasedRegistrarIsWithheld() = withheld(page=WicketRecordActionFixture.plainPage.replace("actions.addAction", "Actions<Row> alias=actions;alias.addAction"))
    fun testReassignedRegistrarIsWithheld() = withheld(page=WicketRecordActionFixture.plainPage.replace("actions.addAction", "actions=new Actions<>();actions.addAction"))
    fun testRegistrarUsedForDispatchInConstructionIsWithheld() = withheld(page=WicketRecordActionFixture.plainPage.replace("actions.addAction", "actions.click(null,null);actions.addAction"))
    fun testAnonymousRegistrarOverrideIsWithheld() = withheld(page=WicketRecordActionFixture.plainPage.replace("new Actions<>()", "new Actions<>() {}"))
    fun testNestedRegistrationIsWithheld() = withheld(page=WicketRecordActionFixture.plainPage.replace("actions.addAction", "Runnable later=()->actions.addAction"))
    fun testUnrelatedConstructorEffectsStillWithholdAttachments() = withheld(page=WicketRecordActionFixture.plainPage.replace("WebMarkupContainer table=", "unknown();WebMarkupContainer table=").replace("private void markPaid", "private void unknown() {removeAll();} private void markPaid"))
    fun testUnrelatedUnreviewedCallbackIsNotSilentlySkipped() = withheld(page=WicketRecordActionFixture.plainPage.replace("Actions<Row> actions=", "Runnable extra=()->removeAll();Actions<Row> actions="))
    fun testMultipleConstructorEntriesCannotBorrowTheStorageProof() = withheld(page=WicketRecordActionFixture.plainPage.replace("private void markPaid", "public ConfirmationPage(String ignored) {this();} private void markPaid"))
    fun testExistingTableOwnedBulkActionPathStillWorks() {
        val java=install(page=WicketRecordActionFixture.plainPage
            .replace("WebMarkupContainer table=new WebMarkupContainer", "TableBox table=new TableBox")
            .replace("actions.addAction", "table.addBulkAction").replace("markPaid(List.of(row))", "markPaid(row)"))
        val initial=WicketPageAnalyzer().analyze(java.classes.single())!!
        val actions=WicketActionContexts().analyze(java.classes.single(),initial)
        assertTrue(initial.storedActionRegistrations.isEmpty())
        assertEquals(initial.warnings.toString()+actions.diagnostics,1,actions.reports.size)
        assertTrue(actions.reports.single().matching!!.findings.all {it.code==DiagnosticCode.MATCHED && it.confidence==Confidence.INFERRED})
    }
    fun testRepeatedRegistrationsRetainSeparateInferredActionContextsAndStorageSites() {
        val java=install(WicketRecordActionFixture.repeatedPage)
        val initial=WicketPageAnalyzer().analyze(java.classes.single())!!
        val registrations=initial.storedActionRegistrations
        assertEquals(initial.warnings.toString(),2,registrations.size)
        assertEquals(1,registrations.map {it.allocationLocation}.distinct().size)
        assertEquals(1,registrations.map {it.slotLocation}.distinct().size)
        assertEquals(2,registrations.map {it.registrationLocation}.distinct().size)
        assertEquals(2,registrations.map {it.callbackLocation}.distinct().size)
        val actions=WicketActionContexts().analyze(java.classes.single(),initial)
        assertEquals(actions.diagnostics.toString(),2,actions.reports.size)
        assertEquals(2,actions.reports.map {it.actionTemplate!!.contextId}.distinct().size)
        assertEquals(setOf("example.ConfirmationPage.State.OPEN","example.ConfirmationPage.State.PAID"),actions.reports.map {it.actionTemplate!!.updates.single().componentArguments.last().declarationName}.toSet())
        assertTrue(actions.reports.all {it.matching!!.findings.single().let {f -> f.code==DiagnosticCode.MATCHED && f.confidence==Confidence.INFERRED}})
        assertTrue(initial.nodes.filter {it.nodeId!=initial.rootNodeId}.all {it.template && !it.idRenameSafe})
    }
    fun testSafeSiblingMethodCanStoreIntoTheSameSlot() {
        val actions=WicketConfirmationFixture.actions.replace("public void click", "public Actions<T> register(String label,SerializableBiConsumer<AjaxRequestTarget,T> handler) {this.action=handler;return this;} public void click")
        val page=WicketRecordActionFixture.repeatedPage.replace("addAction(\"First\"", "register(\"First\"")
        val java=install(page,actions)
        val initial=WicketPageAnalyzer().analyze(java.classes.single())!!
        assertEquals(2,initial.storedActionRegistrations.map {it.methodLocation}.distinct().size)
        assertEquals(2,WicketActionContexts().analyze(java.classes.single(),initial).reports.size)
    }
    fun testSeparateFreshRegistrarsDoNotMergeTheirAllocationIdentities() {
        val page=WicketRecordActionFixture.repeatedPage.replace("});actions.addAction", "});Actions<Row> other=new Actions<>();other.addAction")
        val java=install(page);val initial=WicketPageAnalyzer().analyze(java.classes.single())!!
        assertEquals(2,initial.storedActionRegistrations.map {it.allocationLocation}.distinct().size)
        assertEquals(2,WicketActionContexts().analyze(java.classes.single(),initial).reports.size)
    }
    fun testLaterEagerUseInvalidatesEarlierRegistrations()=withheld(page=WicketRecordActionFixture.repeatedPage.replace("});actions.addAction", "});actions.click(null,null);actions.addAction"))
    fun testLaterAliasInvalidatesEarlierRegistrations()=withheld(page=WicketRecordActionFixture.repeatedPage.replace("});actions.addAction", "});var alias=actions;actions.addAction"))
    fun testLaterReassignmentInvalidatesEarlierRegistrations()=withheld(page=WicketRecordActionFixture.repeatedPage.replace("});actions.addAction", "});actions=new Actions<>();actions.addAction"))
    fun testCallbackCaptureInvalidatesTheWholeRegistrarGroup()=withheld(page=WicketRecordActionFixture.repeatedPage.replace("markOpen(List.of(row));", "actions.click(target,row);markOpen(List.of(row));"))
    fun testDifferentEagerSiblingMethodInvalidatesTheWholeGroup() {
        val actions=WicketConfirmationFixture.actions.replace("public void click", "public Actions<T> eager(String label,SerializableBiConsumer<AjaxRequestTarget,T> handler) {handler.accept(null,null);return this;} public void click")
        withheld(WicketRecordActionFixture.repeatedPage.replace("addAction(\"Paid\"", "eager(\"Paid\""),actions)
    }
    fun testAliasedSiblingCallbackInvalidatesTheWholeGroup()=withheld(page=WicketRecordActionFixture.repeatedPage.replace("actions.addAction(\"Paid\",(target,row)->{markPaid(List.of(row));target.add(table);});", "var handler=(org.danekja.java.util.function.serializable.SerializableBiConsumer<org.apache.wicket.ajax.AjaxRequestTarget,Row>)((target,row)->{markPaid(List.of(row));target.add(table);});actions.addAction(\"Paid\",handler);"))
    fun testNestedSiblingRegistrationInvalidatesTheWholeGroup()=withheld(page=WicketRecordActionFixture.repeatedPage.replace("});actions.addAction", "});Runnable later=()->actions.addAction"))
    fun testConditionalSiblingRegistrationInvalidatesTheWholeGroup()=withheld(page=WicketRecordActionFixture.repeatedPage.replace("});actions.addAction", "});if(System.nanoTime()>0) actions.addAction"))
    fun testChainedRegistrationsUseTheSameInitialAttachmentContract() {
        val java=install(WicketRecordActionFixture.chainedPage)
        val initial=WicketPageAnalyzer().analyze(java.classes.single())!!
        assertEquals(initial.warnings.toString(),2,initial.storedActionRegistrations.size)
        assertEquals(2,WicketActionContexts().analyze(java.classes.single(),initial).reports.size)
    }
    fun testRegistrationFanoutIsBoundedWithoutPartialInitialBindings() {
        val statement="actions.addAction(\"Extra\",(target,row)->{markPaid(List.of(row));target.add(table);});"
        val java=install(WicketRecordActionFixture.plainPage.replace("actions.addAction(\"Paid\"",statement.repeat(64)+"actions.addAction(\"Paid\""))
        val initial=WicketPageAnalyzer().analyze(java.classes.single())!!
        assertTrue(initial.storedActionRegistrations.isEmpty())
        assertTrue(initial.warnings.toString(),initial.warnings.any {"bounded registration count (64)" in it.message})
        assertTrue(WicketActionContexts().analyze(java.classes.single(),initial).reports.isEmpty())
    }
    fun testExactlySixtyFourStorageSitesFitTheDeclaredFanoutBound() {
        val statement="actions.addAction(\"Extra\",(target,row)->{target.add(table);});"
        val java=install(WicketRecordActionFixture.plainPage.replace("actions.addAction(\"Paid\"",statement.repeat(63)+"actions.addAction(\"Paid\""))
        val initial=WicketPageAnalyzer().analyze(java.classes.single())!!
        assertEquals(initial.warnings.toString(),64,initial.storedActionRegistrations.size)
        assertEquals(Confidence.INFERRED,initial.matching!!.findings.single {it.code==DiagnosticCode.MATCHED}.confidence)
    }
    fun testCancellationDuringSiblingAuditDoesNotCacheAPartialGroup() {
        val java=install(WicketRecordActionFixture.repeatedPage)
        val calls=com.intellij.psi.util.PsiTreeUtil.findChildrenOfType(java,PsiMethodCallExpression::class.java).filter {it.methodExpression.referenceName=="addAction"}.sortedBy {it.textOffset}
        val method=calls.first().resolveMethod()!!;var reads=0
        val indicator=com.intellij.openapi.progress.util.ProgressIndicatorBase()
        val proof=WicketStoredActionDeclarations(java.classes.single(),{ProgressManager.checkCanceled()},{if(it==method && ++reads==3) indicator.cancel()},{_,message ->throw IllegalStateException(message)},{})
        try {ProgressManager.getInstance().runProcess(Runnable {proof.inspect(calls.first());fail("Expected cancellation before the group is published")},indicator);fail("Expected cancellation")}
        catch (_: ProcessCanceledException) {}
        val retry=com.intellij.openapi.progress.util.ProgressIndicatorBase()
        ProgressManager.getInstance().runProcess(Runnable {
            assertNotNull(proof.inspect(calls.first()));assertNotNull(proof.inspect(calls.last()))
        },retry)
        assertTrue(reads>3)
    }
    fun testUnsupportedBusinessBodyDoesNotPoisonAnIndependentStoredAction() {
        val java=install(WicketRecordActionFixture.repeatedPage.replace("markOpen(List.of(row));", "System.gc();markOpen(List.of(row));"))
        val initial=WicketPageAnalyzer().analyze(java.classes.single())!!
        assertEquals(2,initial.storedActionRegistrations.size)
        val actions=WicketActionContexts().analyze(java.classes.single(),initial)
        assertEquals(actions.diagnostics.toString(),1,actions.reports.size)
        assertTrue(actions.diagnostics.isNotEmpty())
    }
    fun testCallbackHierarchyMutationDoesNotBecomeARuntimeAttachment() {
        val java=install(page=WicketRecordActionFixture.plainPage.replace("markPaid(List.of(row));", "removeAll();markPaid(List.of(row));"))
        val initial=WicketPageAnalyzer().analyze(java.classes.single())!!
        assertEquals(1,initial.storedActionRegistrations.size)
        assertTrue(initial.nodes.filter {it.nodeId!=initial.rootNodeId}.all {it.template && !it.idRenameSafe})
        assertTrue(WicketActionContexts().analyze(java.classes.single(),initial).reports.isEmpty())
    }
    fun testMissingMarkupKeepsActionTargetsUnresolved() {
        val java=install(page=WicketRecordActionFixture.plainPage.replace("\"rows\"", "\"missing\""))
        val initial=WicketPageAnalyzer().analyze(java.classes.single())!!
        assertEquals(1,initial.storedActionRegistrations.size)
        assertTrue(WicketActionContexts().analyze(java.classes.single(),initial).reports.isEmpty())
    }
    fun testCancelledDiscoveryDoesNotPublishFacts() {
        val java=install();val indicator=com.intellij.openapi.progress.util.ProgressIndicatorBase()
        try {ProgressManager.getInstance().runProcess(Runnable {indicator.cancel();WicketPageAnalyzer().analyze(java.classes.single())},indicator);fail("Expected cancellation")}
        catch (_: ProcessCanceledException) {}
    }
    fun testStorageProofHonorsTheSharedWorkAndCancellationBudget() {
        val java=install();var ticks=0
        val call=com.intellij.psi.util.PsiTreeUtil.findChildrenOfType(java,PsiMethodCallExpression::class.java).single {it.methodExpression.referenceName=="addAction"}
        val indicator=com.intellij.openapi.progress.util.ProgressIndicatorBase()
        val proof=WicketStoredActionDeclarations(java.classes.single(),{if(++ticks>20) indicator.cancel();ProgressManager.checkCanceled()},{},{_,message ->throw IllegalStateException(message)},{})
        try {ProgressManager.getInstance().runProcess(Runnable {proof.inspect(call)},indicator);fail("Expected bounded traversal cancellation")}
        catch (_: ProcessCanceledException) {}
        assertTrue(ticks>20)
    }
}
