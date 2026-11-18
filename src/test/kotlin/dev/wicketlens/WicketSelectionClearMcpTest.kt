package dev.wicketlens

import com.google.gson.JsonParser
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.progress.*
import com.intellij.psi.*
import com.intellij.psi.util.PsiTreeUtil
import dev.wicketlens.editor.*
import dev.wicketlens.mcp.*
import dev.wicketlens.model.*

class WicketSelectionClearMcpTest : WicketProjectPsiTestCase() {
    private val query=WicketPageAnalysisRequest()
    private fun file(path:String)=PsiManager.getInstance(project).findFile(myFixture.findFileInTempDir(path))!!
    private fun capture()=query.capture(project,"example.ConfirmationPage",view=WicketMcpReport.NAVIGATION)
    private fun response(s:PageAnalysisSnapshot,id:String?=null)=JsonParser.parseString(query.render(s,WicketMcpReport.NAVIGATION,contextId=id)).asJsonObject
    private fun bulk(s:PageAnalysisSnapshot)=s.navigationContexts!!.contexts.single {it.report.submissionTemplate?.confirmation?.selectionClears?.isNotEmpty()==true}
    private fun id(s:PageAnalysisSnapshot):String {
        val position=s.navigationContexts!!.contexts.indexOf(bulk(s))
        return response(s)["navigationContexts"].asJsonObject["contexts"].asJsonArray[position].asJsonObject["contextId"].asString
    }
    fun testSelectedAndLegacyMcpViewsShareNativeDeclarationsAndLeaveInitialViewsUnchanged() {
        val java=WicketSelectionClearFixture.install(myFixture)
        val files=listOf(java,file("src/main/java/example/TableBox.java"),file("src/main/java/example/SelectionBox.java"),file("src/main/java/example/ConfirmBox.java"),file("src/main/resources/example/ConfirmationPage.html"))
        val before=files.map {it.text to it.modificationStamp}
        val s=capture();val plain=query.capture(project,"example.ConfirmationPage")
        assertEquals(plain.report,s.report)
        val context=bulk(s)
        assertEquals(context.report,WicketEditorAnalysis.navigationPages(java).single {it.report.submissionTemplate?.confirmation?.selectionClears?.isNotEmpty()==true}.report)
        val selected=response(s,id(s))["navigationContexts"].asJsonObject["contexts"].asJsonArray.single().asJsonObject
        selected.remove("contextId");assertEquals(WicketNavigationExport.context(context),selected)
        val metadata=selected["submissionTemplate"].asJsonObject["confirmation"].asJsonObject["selectionClears"].asJsonArray.single().asJsonObject
        assertEquals("SOURCE_SELECTION_HELPER",metadata["kind"].asString);assertEquals("not_evaluated",metadata["execution"].asString)
        for(key in listOf("columnPresence","columnDispatch","modelImplementation","modelValue","collectionIdentity","collectionMutability","clearOutcome","refreshOutcome","laterSelection")) assertEquals(key,"unknown",metadata[key].asString)
        assertEquals("rows",metadata["targetComponentPath"].asString)
        assertEquals("java.util.Collection.clear",metadata["collectionOperation"].asString)
        val legacy=query.capture(project,"example.ConfirmationPage",includeNavigationContexts=true)
        assertEquals(WicketNavigationExport.render(s.navigationContexts!!),JsonParser.parseString(query.render(legacy)).asJsonObject["navigationContexts"])
        assertEquals(query.render(plain,WicketMcpReport.VALIDATION),query.render(legacy,WicketMcpReport.VALIDATION))
        assertEquals(query.render(plain,WicketMcpReport.CONTEXT,"rows"),query.render(legacy,WicketMcpReport.CONTEXT,"rows"))
        val row=s.navigationContexts.contexts.single {it.report.submissionTemplate?.confirmation?.selectionClears?.isEmpty()==true}
        assertFalse(WicketNavigationExport.context(row)["submissionTemplate"].asJsonObject["confirmation"].asJsonObject.has("selectionClears"))
        query.requireCurrent(project,s);assertEquals(before,files.map {it.text to it.modificationStamp})
    }
    private fun stale(path:String,from:String,to:String,withheld:Boolean=false) {
        WicketSelectionClearFixture.install(myFixture,tableSource=if(withheld) WicketSelectionClearFixture.table.replace("target.add(this);","target.add(this);System.gc();") else WicketSelectionClearFixture.table)
        val s=capture();val selector=if(withheld) null else id(s)
        val source=file(path);assertTrue(s.version.sources.toString(),s.version.sources.any {it.path==source.virtualFile.path})
        assertTrue(source.text.contains(from))
        WriteCommandAction.runWriteCommandAction(project) {PsiDocumentManager.getInstance(project).getDocument(source)!!.setText(source.text.replace(from,to))}
        try {query.requireCurrent(project,s);fail("Expected stale snapshot")}catch(e:PageAnalysisFailure){assertEquals("STALE_SNAPSHOT",e.code)}
        if(selector!=null) {
            PsiDocumentManager.getInstance(project).commitAllDocuments();val fresh=capture()
            try {response(fresh,selector);fail("Expected stale selection")}catch(e:PageAnalysisFailure){assertEquals("STALE_CONTEXT_SELECTION",e.code)}
        }
    }
    fun testUnsavedHelperGuardInvalidatesSnapshotAndSelectors()=stale("src/main/java/example/TableBox.java","selectionColumn!=null","selectionColumn==null")
    fun testUnsavedGetterInvalidatesSnapshotAndSelectors()=stale("src/main/java/example/SelectionBox.java","return selection;","System.gc();return selection;")
    fun testUnsavedModelFieldInvalidatesSnapshotAndSelectors()=stale("src/main/java/example/SelectionBox.java","private final IModel","public final IModel")
    fun testUnsavedForwardedTargetInvalidatesSnapshotAndSelectors()=stale("src/main/java/example/ConfirmationPage.java","clearSelection(t)","clearSelection(null)")
    fun testUnsavedRefreshMarkupInvalidatesSnapshotAndSelectors()=stale("src/main/resources/example/ConfirmationPage.html","'rows'","'changed'")
    fun testWithheldHelperRemainsAFreshnessDependency()=stale("src/main/java/example/TableBox.java","target.add(this);","target.add(this);System.gc();",true)
    fun testProjectModuleAndSourceSetCannotReuseBulkSelectors() {
        WicketSelectionClearFixture.install(myFixture);val s=capture();val selector=id(s)
        for(other in listOf(s.copy(navigationProjectScope="other-project"),s.copy(moduleName="other"),s.copy(sourceSet="test"))) {
            try {response(other,selector);fail("Expected isolation")}catch(e:PageAnalysisFailure){assertEquals("STALE_CONTEXT_SELECTION",e.code)}
        }
    }
    fun testSelectedBulkResponseKeepsItsWholeByteCap() {
        WicketSelectionClearFixture.install(myFixture);val s=capture();val context=bulk(s)
        val large=context.copy(report=context.report.copy(warnings=listOf(Diagnostic(DiagnosticCode.PARTIAL_ANALYSIS,"界".repeat(200000)))))
        val big=s.copy(navigationContexts=s.navigationContexts!!.copy(contexts=listOf(large)))
        try {response(big,id(big));fail("Expected response bound")}catch(e:PageAnalysisFailure){assertEquals("RESPONSE_TOO_LARGE",e.code)}
    }
    fun testBothConsumersNavigateFromTheSharedTriggerWithoutRenameAuthority() {
        WicketSelectionClearFixture.install(myFixture)
        val hook=PsiTreeUtil.findChildrenOfType(file("src/main/java/example/ConfirmBox.java"),PsiMethod::class.java).single {it.name=="onConfirm"}
        val trigger=WicketSubmissionNavigation.targets(hook).single()
        assertEquals(2,WicketSubmissionNavigation.targets(trigger).size)
        assertNull(WicketIdRenameTarget.at(trigger.containingFile,trigger.textOffset))
    }
    fun testCancellationPropagatesThroughBulkDiscovery() {
        val java=WicketSelectionClearFixture.install(myFixture);val initial=dev.wicketlens.analysis.WicketPageAnalyzer().analyze(java.classes.single())!!
        val indicator=EmptyProgressIndicator()
        assertThrows(ProcessCanceledException::class.java) {ProgressManager.getInstance().runProcess(Runnable {indicator.cancel();dev.wicketlens.analysis.WicketConfirmationContexts().analyze(java.classes.single(),initial)},indicator)}
    }
}
