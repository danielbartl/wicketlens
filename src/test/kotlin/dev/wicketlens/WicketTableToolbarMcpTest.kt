package dev.wicketlens

import com.google.gson.JsonParser
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.psi.*
import dev.wicketlens.editor.WicketEditorAnalysis
import dev.wicketlens.export.WicketContextExporter
import dev.wicketlens.mcp.*
import dev.wicketlens.model.*

class WicketTableToolbarMcpTest : WicketProjectPsiTestCase() {
    private fun file(path:String)=PsiManager.getInstance(project).findFile(myFixture.findFileInTempDir(path))!!
    fun testToolbarRelativeGraphsMatchNativeAndLeaveDefaultViewsUnchanged() {
        val java=WicketTableToolbarFixture.install(myFixture)
        val files=listOf(java,file("src/main/java/example/TableHost.java"),file("src/main/java/example/EmptyView.java"),file("src/main/resources/example/TableHost\$EmptyToolbar.html"),file("src/main/resources/example/EmptyView.html"),file("src/main/resources/example/EmptyPage.html"))
        val before=files.map { it.text to it.modificationStamp };val query=WicketPageAnalysisRequest()
        val plain=query.capture(project,"example.EmptyPage");val snapshot=query.capture(project,"example.EmptyPage",includeNavigationContexts=true)
        assertEquals(plain.report,snapshot.report)
        val contexts=snapshot.navigationContexts!!.contexts.filter { it.relationship=="TABLE_TOOLBAR_TEMPLATE" };assertEquals(1,contexts.size)
        val native=WicketEditorAnalysis.navigationPages(java).filter { it.report.toolbarTemplate!=null }
        assertEquals(native.map { WicketContextExporter().export(it.report) },contexts.map { WicketContextExporter().export(it.report) })
        val json=JsonParser.parseString(query.render(snapshot)).asJsonObject
        val context=json["navigationContexts"].asJsonObject["contexts"].asJsonArray.single { it.asJsonObject["relationship"].asString=="TABLE_TOOLBAR_TEMPLATE" }.asJsonObject
        val toolbar=context["toolbarTemplate"].asJsonObject
        assertEquals("SOURCE_DECLARED_TABLE_TOOLBAR_CONTENT",toolbar["kind"].asString)
        assertEquals("TABLE_TOOLBAR_TEMPLATE",toolbar["phase"].asString);assertEquals("TOOLBAR_RELATIVE",toolbar["pathScope"].asString)
        assertEquals("source_immediate",toolbar["factoryInvocation"].asString);assertEquals("source_null_guarded",toolbar["initialization"].asString)
        for (key in listOf("runtimePresence","runtimeToolbarId","toolbarInstances","rowCount","visibility","colspan","modelValues","configurationEffects","laterTreeState")) assertEquals("unknown",toolbar[key].asString)
        for (key in listOf("factoryExecution","callbackExecution")) assertEquals("not_evaluated",toolbar[key].asString)
        assertEquals(2,toolbar["callbackLocations"].asJsonArray.size())
        assertEquals(listOf("cell","cell:content","cell:content:title","cell:content:message"),context["bindings"].asJsonArray.map { it.asJsonObject["componentPath"].asString })
        assertTrue(context["bindings"].asJsonArray.all { it.asJsonObject.has("ancestors") && it.asJsonObject.has("children") && it.asJsonObject["findings"].asJsonArray.single().asJsonObject["confidence"].asString=="INFERRED" })
        assertEquals(JsonParser.parseString(query.render(plain)).asJsonObject["analysis"],json["analysis"])
        assertEquals(JsonParser.parseString(query.render(plain,WicketMcpReport.VALIDATION)),JsonParser.parseString(query.render(snapshot,WicketMcpReport.VALIDATION)))
        assertEquals(JsonParser.parseString(query.render(plain,WicketMcpReport.CONTEXT,"")),JsonParser.parseString(query.render(snapshot,WicketMcpReport.CONTEXT,"")))
        for (path in listOf("cell:content:title","empty:grid:bottomToolbars:toolbars:1:cell:content:title")) {
            try { query.render(snapshot,WicketMcpReport.CONTEXT,path);fail("Toolbar template must not supply a primary/runtime path") }
            catch(e:PageAnalysisFailure) { assertTrue(e.code,e.code in setOf("COMPONENT_NOT_FOUND","UNRESOLVED_COMPONENT_PATH")) }
        }
        query.requireCurrent(project,snapshot);assertEquals(before,files.map { it.text to it.modificationStamp })
    }
    fun testUnsavedCachePlacementChangeInvalidatesSnapshot()=freshness("src/main/java/example/TableHost.java","table.addBottomToolbar(emptyToolbar);","table.addTopToolbar(emptyToolbar);")
    fun testUnsavedSelectedContentConstructorChangeInvalidatesSnapshot()=freshness("src/main/java/example/EmptyView.java","Label(\"title\",title)","Label(\"other\",title)")
    fun testUnsavedToolbarCompanionChangeInvalidatesSnapshot()=freshness("src/main/resources/example/TableHost\$EmptyToolbar.html","wicket:id='content'","wicket:id='other'")
    fun testUnsavedContentMarkupChangeInvalidatesSnapshot()=freshness("src/main/resources/example/EmptyView.html","wicket:id='message'","wicket:id='other'")
    fun testUnsavedOwnedTableMarkupChangeInvalidatesSnapshot()=freshness("src/main/resources/example/TableHost.html","wicket:id='grid'","wicket:id='other'")
    private fun freshness(path:String,before:String,after:String) {
        WicketTableToolbarFixture.install(myFixture);val query=WicketPageAnalysisRequest();val snapshot=query.capture(project,"example.EmptyPage",includeNavigationContexts=true)
        val file=file(path);assertTrue(snapshot.version.sources.toString(),snapshot.version.sources.any { it.path==file.virtualFile.path })
        WriteCommandAction.runWriteCommandAction(project) { PsiDocumentManager.getInstance(project).getDocument(file)!!.setText(file.text.replace(before,after)) }
        try { query.requireCurrent(project,snapshot);fail("Expected stale toolbar source snapshot") } catch(e:PageAnalysisFailure) { assertEquals("STALE_SNAPSHOT",e.code) }
        PsiDocumentManager.getInstance(project).commitAllDocuments()
        val next=query.capture(project,"example.EmptyPage",includeNavigationContexts=true);assertTrue(next.version!=snapshot.version)
        val templates=next.navigationContexts!!.contexts.filter { it.relationship=="TABLE_TOOLBAR_TEMPLATE" }
        assertTrue(templates.isEmpty() || templates.single().report.matching!!.findings.any { it.code!=DiagnosticCode.MATCHED })
    }
    fun testWholeToolbarResponseRemainsBounded() {
        WicketTableToolbarFixture.install(myFixture);val query=WicketPageAnalysisRequest();val snapshot=query.capture(project,"example.EmptyPage",includeNavigationContexts=true)
        val navigation=snapshot.navigationContexts!!;val context=navigation.contexts.single { it.relationship=="TABLE_TOOLBAR_TEMPLATE" }
        val large=context.copy(report=context.report.copy(warnings=listOf(Diagnostic(DiagnosticCode.PARTIAL_ANALYSIS,"x".repeat(600_000)))))
        try { query.render(snapshot.copy(navigationContexts=navigation.copy(contexts=listOf(large))));fail("Expected whole response limit") } catch(e:PageAnalysisFailure) { assertEquals("RESPONSE_TOO_LARGE",e.code) }
    }
    fun testPreviouslyWithheldRegistrationKeepsItsSourceDependency() {
        WicketTableToolbarFixture.install(myFixture)
        val host=file("src/main/java/example/TableHost.java")
        val valid=host.text
        WriteCommandAction.runWriteCommandAction(project) { PsiDocumentManager.getInstance(project).getDocument(host)!!.setText(valid.replace("new EmptyToolbar(table)","new EmptyToolbar(table) {}")) }
        PsiDocumentManager.getInstance(project).commitAllDocuments()
        val query=WicketPageAnalysisRequest();val snapshot=query.capture(project,"example.EmptyPage",includeNavigationContexts=true)
        assertTrue(snapshot.navigationContexts!!.contexts.none { it.relationship=="TABLE_TOOLBAR_TEMPLATE" })
        assertTrue(snapshot.version.sources.any { it.path==host.virtualFile.path })
        WriteCommandAction.runWriteCommandAction(project) { PsiDocumentManager.getInstance(project).getDocument(host)!!.setText(valid) }
        try { query.requireCurrent(project,snapshot);fail("Newly eligible toolbar source must invalidate a withheld snapshot") } catch(e:PageAnalysisFailure) { assertEquals("STALE_SNAPSHOT",e.code) }
        PsiDocumentManager.getInstance(project).commitAllDocuments()
        assertEquals(1,query.capture(project,"example.EmptyPage",includeNavigationContexts=true).navigationContexts!!.contexts.count { it.relationship=="TABLE_TOOLBAR_TEMPLATE" })
    }
    fun testIndexedDiscoveryBoundsStayExplicit() {
        val java=WicketTableToolbarFixture.install(myFixture);val type=java.classes.single();val report=dev.wicketlens.analysis.WicketPageAnalyzer().analyze(type)!!
        val navigation=dev.wicketlens.analysis.WicketNavigationContextsAnalyzer(dev.wicketlens.analysis.WicketNavigationContextsAnalyzer.Limits(bindings=1)).analyze(type,report)
        assertFalse(navigation.complete);assertTrue(navigation.diagnostics.isNotEmpty())
    }
}
