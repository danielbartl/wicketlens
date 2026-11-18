package dev.wicketlens

import com.google.gson.JsonParser
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.psi.*
import dev.wicketlens.export.WicketContextExporter
import dev.wicketlens.mcp.*

class WicketToolbarCacheMcpTest:WicketProjectPsiTestCase() {
    fun testThreeViewsShareCacheProtocolWithoutRuntimeStateOrPrivateIds() {
        val java=WicketToolbarCacheFixture.install(myFixture);val before=java.text to java.modificationStamp
        val query=WicketPageAnalysisRequest();val snapshot=query.capture(project,"example.TablePage")
        val structure=JsonParser.parseString(query.render(snapshot)).asJsonObject["analysis"].asJsonObject
        assertEquals(JsonParser.parseString(WicketContextExporter().export(snapshot.report)),structure)
        assertTrue(structure["warnings"].asJsonArray.any {"Reviewed source null-guarded owned toolbar cache" in it.asJsonObject["message"].asString})
        assertTrue(query.render(snapshot,WicketMcpReport.VALIDATION).contains("Reviewed source null-guarded owned toolbar cache"))
        val context=JsonParser.parseString(query.render(snapshot,WicketMcpReport.CONTEXT,"shell")).asJsonObject["context"].asJsonObject
        assertEquals(structure["warnings"],context["warnings"])
        assertEquals(setOf("shell","title"),snapshot.report.nodes.mapNotNull {it.wicketId}.toSet())
        for(name in listOf("wicket-core-11.0.0.jar","wicket-extensions-11.0.0.jar")) assertTrue(name,snapshot.version.sources.any {it.path.endsWith(name)})
        assertTrue(snapshot.version.sources.any {it.path.endsWith("!/java.base/java/util/List.class")})
        query.requireCurrent(project,snapshot);assertEquals(before,java.text to java.modificationStamp)
    }
    private fun stale(path:String,before:String,after:String,commit:Boolean) {
        WicketToolbarCacheFixture.install(myFixture)
        val file=PsiManager.getInstance(project).findFile(myFixture.findFileInTempDir(path))!!
        val query=WicketPageAnalysisRequest();val snapshot=query.capture(project,"example.TablePage")
        assertTrue(before in file.text)
        WriteCommandAction.runWriteCommandAction(project) {PsiDocumentManager.getInstance(project).getDocument(file)!!.setText(file.text.replace(before,after))}
        if(commit) PsiDocumentManager.getInstance(project).commitAllDocuments()
        try {query.requireCurrent(project,snapshot);fail("Expected stale table source")}
        catch(e:PageAnalysisFailure){assertEquals("STALE_SNAPSHOT",e.code)}
    }

    fun testUncommittedGuardChangesInvalidateSnapshots()=stale("src/main/java/example/TableShell.java","cache==null","cache!=null",false)
    fun testCacheStorageChangesInvalidateSnapshots()=stale("src/main/java/example/TableShell.java","private EmptyToolbar cache;","public EmptyToolbar cache;",true)
    fun testRegistrationChangesInvalidateSnapshots()=stale("src/main/java/example/TableShell.java","addBottomToolbar(cache)","addBottomToolbar(new EmptyToolbar(table))",true)
    fun testMarkupChangesInvalidateSnapshots()=stale("src/main/resources/example/TableShell.html","wicket:id='table'","wicket:id='changed'",true)
    fun testWithheldInterfaceEligibilityEditsInvalidateAndRefreshSnapshots() {
        WicketToolbarCacheFixture.install(myFixture)
        val marker=myFixture.addFileToProject("src/main/java/example/ToolbarMarker.java","package example;interface ToolbarMarker {int TOKEN=seed();static int seed(){System.setProperty(\"toolbar\",\"effect\");return 1;}default String tag(){return \"tag\";}}")
        val shell=PsiManager.getInstance(project).findFile(myFixture.findFileInTempDir("src/main/java/example/TableShell.java"))!!
        WriteCommandAction.runWriteCommandAction(project) {PsiDocumentManager.getInstance(project).getDocument(shell)!!.setText(shell.text.replace("extends AbstractToolbar","extends AbstractToolbar implements ToolbarMarker"))}
        PsiDocumentManager.getInstance(project).commitAllDocuments()
        val query=WicketPageAnalysisRequest();val snapshot=query.capture(project,"example.TablePage")
        assertTrue(snapshot.version.sources.any {it.path.endsWith("ToolbarMarker.java")})
        assertFalse(query.render(snapshot).contains("Reviewed source null-guarded owned toolbar cache"))
        WriteCommandAction.runWriteCommandAction(project) {PsiDocumentManager.getInstance(project).getDocument(marker)!!.setText("package example;interface ToolbarMarker {int TOKEN=1;default String tag(){return \"tag\";}}")}
        PsiDocumentManager.getInstance(project).commitAllDocuments()
        try {query.requireCurrent(project,snapshot);fail("Expected stale withheld interface proof")}
        catch(e:PageAnalysisFailure){assertEquals("STALE_SNAPSHOT",e.code)}
        assertTrue(query.render(query.capture(project,"example.TablePage")).contains("Reviewed source null-guarded owned toolbar cache"))
    }
    fun testCompletedCacheFactAndLaterEffectRemainSharedAndReadOnly() {
        WicketToolbarCacheFixture.install(myFixture)
        val shell=PsiManager.getInstance(project).findFile(myFixture.findFileInTempDir("src/main/java/example/TableShell.java"))!!
        WriteCommandAction.runWriteCommandAction(project) {PsiDocumentManager.getInstance(project).getDocument(shell)!!.setText(shell.text.replace("return this;","getPage().removeAll();return this;"))}
        PsiDocumentManager.getInstance(project).commitAllDocuments()
        val query=WicketPageAnalysisRequest();val snapshot=query.capture(project,"example.TablePage")
        for(view in listOf(WicketMcpReport.STRUCTURE,WicketMcpReport.VALIDATION,WicketMcpReport.CONTEXT)) {
            val json=query.render(snapshot,view,if(view==WicketMcpReport.CONTEXT) "" else null)
            assertTrue(json.contains("Reviewed source null-guarded owned toolbar cache"));assertTrue(json.contains("Source layout setup stopped:"))
        }
        query.requireCurrent(project,snapshot)
    }
    fun testThreeViewsKeepTheWholeResponseBound() {
        WicketToolbarCacheFixture.install(myFixture)
        val query=WicketPageAnalysisRequest();val snapshot=query.capture(project,"example.TablePage")
        val report=snapshot.report.copy(warnings=snapshot.report.warnings+dev.wicketlens.model.Diagnostic(dev.wicketlens.model.DiagnosticCode.PARTIAL_ANALYSIS,"x".repeat(600000)))
        for(view in listOf(WicketMcpReport.STRUCTURE,WicketMcpReport.VALIDATION,WicketMcpReport.CONTEXT)) {
            try {query.render(snapshot.copy(report=report),view,if(view==WicketMcpReport.CONTEXT) "shell" else null);fail("Expected response bound")}
            catch(e:PageAnalysisFailure){assertEquals("RESPONSE_TOO_LARGE",e.code)}
        }
    }
}
