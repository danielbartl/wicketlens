package dev.wicketlens

import com.google.gson.JsonParser
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.psi.*
import dev.wicketlens.export.WicketContextExporter
import dev.wicketlens.mcp.*

class WicketNavigationToolbarMcpTest:WicketProjectPsiTestCase() {
    fun testThreeViewsShareEagerFactoryNotesAndDependenciesWithoutExportingNavigatorIds() {
        val java=WicketNavigationToolbarFixture.install(myFixture);val before=java.text to java.modificationStamp
        val query=WicketPageAnalysisRequest();val snapshot=query.capture(project,"example.TablePage")
        val structure=JsonParser.parseString(query.render(snapshot)).asJsonObject["analysis"].asJsonObject
        assertEquals(JsonParser.parseString(WicketContextExporter().export(snapshot.report)),structure)
        assertTrue(structure["warnings"].asJsonArray.any {"Reviewed fresh-table navigation-toolbar construction" in it.asJsonObject["message"].asString})
        assertTrue(query.render(snapshot,WicketMcpReport.VALIDATION).contains("Reviewed fresh-table navigation-toolbar construction"))
        val context=JsonParser.parseString(query.render(snapshot,WicketMcpReport.CONTEXT,"shell")).asJsonObject["context"].asJsonObject
        assertEquals(structure["warnings"],context["warnings"])
        assertEquals(setOf("shell","title"),snapshot.report.nodes.mapNotNull {it.wicketId}.toSet())
        for(name in listOf("wicket-extensions-11.0.0.jar","wicket-core-11.0.0.jar","wicket-util-11.0.0.jar")) assertTrue(name,snapshot.version.sources.any {it.path.endsWith(name)})
        query.requireCurrent(project,snapshot);assertEquals(before,java.text to java.modificationStamp)
    }
    private fun stale(path:String,before:String,after:String,commit:Boolean) {
        WicketNavigationToolbarFixture.install(myFixture)
        val file=PsiManager.getInstance(project).findFile(myFixture.findFileInTempDir(path))!!
        val query=WicketPageAnalysisRequest();val snapshot=query.capture(project,"example.TablePage")
        assertTrue(before in file.text)
        WriteCommandAction.runWriteCommandAction(project) {PsiDocumentManager.getInstance(project).getDocument(file)!!.setText(file.text.replace(before,after))}
        if(commit) PsiDocumentManager.getInstance(project).commitAllDocuments()
        try {query.requireCurrent(project,snapshot);fail("Expected stale table source")}
        catch(e:PageAnalysisFailure){assertEquals("STALE_SNAPSHOT",e.code)}
    }
    fun testUncommittedNavigationConstructorChangesInvalidateSnapshots()=stale("src/main/java/example/TableShell.java","super(table);table.setOutputMarkupId(true);","super(table);table.setOutputMarkupId(true);TableShell.this.removeAll();",false)
    fun testDeferredPagerCallbackChangesInvalidateSnapshots()=stale("src/main/java/example/TableShell.java","target.add(table);","target.add(table);TableShell.this.removeAll();",true)
    fun testEagerFactoryChangesInvalidateSnapshots()=stale("src/main/java/example/TableShell.java","new Pager(id,table)","new Pager(id+\"other\",table)",true)
    fun testMarkupChangesInvalidateSnapshots()=stale("src/main/resources/example/TableShell.html","wicket:id='table'","wicket:id='changed'",true)
    fun testWithheldInterfaceEligibilityEditsInvalidateAndRefreshSnapshots() {
        WicketNavigationToolbarFixture.install(myFixture)
        val marker=myFixture.addFileToProject("src/main/java/example/ToolbarMarker.java","package example;interface ToolbarMarker {int TOKEN=seed();static int seed(){System.setProperty(\"toolbar\",\"effect\");return 1;}default String tag(){return \"tag\";}}")
        val shell=PsiManager.getInstance(project).findFile(myFixture.findFileInTempDir("src/main/java/example/TableShell.java"))!!
        WriteCommandAction.runWriteCommandAction(project) {PsiDocumentManager.getInstance(project).getDocument(shell)!!.setText(shell.text.replace("Nav extends NavigationToolbar {","Nav extends NavigationToolbar implements ToolbarMarker {"))}
        PsiDocumentManager.getInstance(project).commitAllDocuments()
        val query=WicketPageAnalysisRequest();val snapshot=query.capture(project,"example.TablePage")
        assertTrue(snapshot.version.sources.any {it.path.endsWith("ToolbarMarker.java")})
        assertFalse(query.render(snapshot).contains("Reviewed fresh-table navigation-toolbar construction"))
        WriteCommandAction.runWriteCommandAction(project) {PsiDocumentManager.getInstance(project).getDocument(marker)!!.setText("package example;interface ToolbarMarker {int TOKEN=1;default String tag(){return \"tag\";}}")}
        PsiDocumentManager.getInstance(project).commitAllDocuments()
        try {query.requireCurrent(project,snapshot);fail("Expected stale withheld interface proof")}
        catch(e:PageAnalysisFailure){assertEquals("STALE_SNAPSHOT",e.code)}
        assertTrue(query.render(query.capture(project,"example.TablePage")).contains("Reviewed fresh-table navigation-toolbar construction"))
    }
    fun testThreeViewsKeepTheWholeResponseBound() {
        WicketNavigationToolbarFixture.install(myFixture)
        val query=WicketPageAnalysisRequest();val snapshot=query.capture(project,"example.TablePage")
        val report=snapshot.report.copy(warnings=snapshot.report.warnings+dev.wicketlens.model.Diagnostic(dev.wicketlens.model.DiagnosticCode.PARTIAL_ANALYSIS,"x".repeat(600000)))
        for(view in listOf(WicketMcpReport.STRUCTURE,WicketMcpReport.VALIDATION,WicketMcpReport.CONTEXT)) {
            try {query.render(snapshot.copy(report=report),view,if(view==WicketMcpReport.CONTEXT) "shell" else null);fail("Expected response bound")}
            catch(e:PageAnalysisFailure){assertEquals("RESPONSE_TOO_LARGE",e.code)}
        }
    }
}
