package dev.wicketlens

import com.google.gson.JsonParser
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.psi.*
import dev.wicketlens.export.WicketContextExporter
import dev.wicketlens.mcp.*

class WicketOwnedAccessorMcpTest:WicketProjectPsiTestCase() {
    fun testThreeViewsShareOwnedAccessorNotesWithoutExportingPrivateChildren() {
        val java=WicketOwnedAccessorFixture.install(myFixture);val before=java.text to java.modificationStamp
        val query=WicketPageAnalysisRequest();val snapshot=query.capture(project,"example.TablePage")
        val structure=JsonParser.parseString(query.render(snapshot)).asJsonObject["analysis"].asJsonObject
        assertEquals(JsonParser.parseString(WicketContextExporter().export(snapshot.report)),structure)
        assertTrue(structure["warnings"].asJsonArray.any {"Reviewed owned component accessor behavior decoration" in it.asJsonObject["message"].asString})
        assertTrue(query.render(snapshot,WicketMcpReport.VALIDATION).contains("Reviewed owned component accessor behavior decoration"))
        val context=JsonParser.parseString(query.render(snapshot,WicketMcpReport.CONTEXT,"shell")).asJsonObject["context"].asJsonObject
        assertEquals(structure["warnings"],context["warnings"])
        assertEquals(setOf("shell","title"),snapshot.report.nodes.mapNotNull {it.wicketId}.toSet())
        for(name in listOf("wicket-core-11.0.0.jar")) assertTrue(name,snapshot.version.sources.any {it.path.endsWith(name)})
        query.requireCurrent(project,snapshot);assertEquals(before,java.text to java.modificationStamp)
    }
    private fun stale(path:String,before:String,after:String,commit:Boolean) {
        WicketOwnedAccessorFixture.install(myFixture)
        val file=PsiManager.getInstance(project).findFile(myFixture.findFileInTempDir(path))!!
        val query=WicketPageAnalysisRequest();val snapshot=query.capture(project,"example.TablePage")
        assertTrue(before in file.text)
        WriteCommandAction.runWriteCommandAction(project) {PsiDocumentManager.getInstance(project).getDocument(file)!!.setText(file.text.replace(before,after))}
        if(commit) PsiDocumentManager.getInstance(project).commitAllDocuments()
        try {query.requireCurrent(project,snapshot);fail("Expected stale table source")}
        catch(e:PageAnalysisFailure){assertEquals("STALE_SNAPSHOT",e.code)}
    }
    fun testUncommittedGetterChangesInvalidateSnapshots()=stale("src/main/java/example/Popover.java","return trigger;","return null;",false)
    fun testConstructorChangesInvalidateSnapshots()=stale("src/main/java/example/Popover.java","super(id);","super(id);getPage();",true)
    fun testOwnerEscapeChangesInvalidateSnapshots()=stale("src/main/java/example/TableShell.java","chooser.getTrigger().add","add(chooser);chooser.getTrigger().add",true)
    fun testMarkupChangesInvalidateSnapshots()=stale("src/main/resources/example/TableShell.html","wicket:id='tools'","wicket:id='changed'",true)
    fun testWithheldInterfaceEligibilityEditsInvalidateAndRefreshSnapshots() {
        WicketOwnedAccessorFixture.install(myFixture)
        val marker=myFixture.addFileToProject("src/main/java/example/ToolbarMarker.java","package example;interface ToolbarMarker {int TOKEN=seed();static int seed(){System.setProperty(\"toolbar\",\"effect\");return 1;}default String tag(){return \"tag\";}}")
        val shell=PsiManager.getInstance(project).findFile(myFixture.findFileInTempDir("src/main/java/example/Popover.java"))!!
        WriteCommandAction.runWriteCommandAction(project) {PsiDocumentManager.getInstance(project).getDocument(shell)!!.setText(shell.text.replace("extends org.apache.wicket.markup.html.panel.Panel {","extends org.apache.wicket.markup.html.panel.Panel implements ToolbarMarker {"))}
        PsiDocumentManager.getInstance(project).commitAllDocuments()
        val query=WicketPageAnalysisRequest();val snapshot=query.capture(project,"example.TablePage")
        assertTrue(snapshot.version.sources.any {it.path.endsWith("ToolbarMarker.java")})
        assertFalse(query.render(snapshot).contains("Reviewed owned component accessor behavior decoration"))
        WriteCommandAction.runWriteCommandAction(project) {PsiDocumentManager.getInstance(project).getDocument(marker)!!.setText("package example;interface ToolbarMarker {int TOKEN=1;default String tag(){return \"tag\";}}")}
        PsiDocumentManager.getInstance(project).commitAllDocuments()
        try {query.requireCurrent(project,snapshot);fail("Expected stale withheld interface proof")}
        catch(e:PageAnalysisFailure){assertEquals("STALE_SNAPSHOT",e.code)}
        assertTrue(query.render(query.capture(project,"example.TablePage")).contains("Reviewed owned component accessor behavior decoration"))
    }
    fun testThreeViewsKeepTheWholeResponseBound() {
        WicketOwnedAccessorFixture.install(myFixture)
        val query=WicketPageAnalysisRequest();val snapshot=query.capture(project,"example.TablePage")
        val report=snapshot.report.copy(warnings=snapshot.report.warnings+dev.wicketlens.model.Diagnostic(dev.wicketlens.model.DiagnosticCode.PARTIAL_ANALYSIS,"x".repeat(600000)))
        for(view in listOf(WicketMcpReport.STRUCTURE,WicketMcpReport.VALIDATION,WicketMcpReport.CONTEXT)) {
            try {query.render(snapshot.copy(report=report),view,if(view==WicketMcpReport.CONTEXT) "shell" else null);fail("Expected response bound")}
            catch(e:PageAnalysisFailure){assertEquals("RESPONSE_TOO_LARGE",e.code)}
        }
    }
}
