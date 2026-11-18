package dev.wicketlens

import com.google.gson.JsonParser
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.psi.*
import dev.wicketlens.analysis.WicketPageAnalyzer
import dev.wicketlens.export.WicketContextExporter
import dev.wicketlens.mcp.*
import dev.wicketlens.model.*

class WicketDeclarationWorkTest : WicketProjectPsiTestCase() {
    private fun change(name:String,before:String,after:String,commit:Boolean=true) {
        val file=PsiManager.getInstance(project).findFile(myFixture.findFileInTempDir("src/main/java/example/$name.java"))!!
        assertTrue(before in file.text)
        WriteCommandAction.runWriteCommandAction(project) {PsiDocumentManager.getInstance(project).getDocument(file)!!.setText(file.text.replace(before,after))}
        if(commit) PsiDocumentManager.getInstance(project).commitAllDocuments()
    }
    private fun install():PsiJavaFile {
        val java=WicketConfirmContentFixture.install(myFixture)
        change("ConfirmBox","public ConfirmBox(String id)","/** declaration documentation */\n".repeat(6000)+"public ConfirmBox(String id)")
        return java
    }
    fun testDocumentationHeavyDeclarationsCompleteWithinTheExistingBudget() {
        val java=install();val report=WicketPageAnalyzer().analyze(java.classes.single())!!
        assertNotNull(report.warnings.toString(),report.initialContentSetup)
        assertFalse(report.warnings.any {it.message.startsWith("Source layout setup stopped:")})
        assertTrue(report.matching!!.findings.all {it.code==DiagnosticCode.MATCHED && it.confidence==Confidence.INFERRED})
    }
    fun testRepeatedHostsKeepSeparateComponentScopesWhileReusingSourceStructure() {
        val java=install()
        change("ConfirmPage","add(new ConfirmBox(\"confirmBox\"));","add(new ConfirmBox(\"confirmBox\"));add(new ConfirmBox(\"second\"));")
        val markup=PsiManager.getInstance(project).findFile(myFixture.findFileInTempDir("src/main/resources/example/ConfirmPage.html"))!!
        WriteCommandAction.runWriteCommandAction(project) {PsiDocumentManager.getInstance(project).getDocument(markup)!!.setText("<div wicket:id='confirmBox'></div><div wicket:id='second'></div>")}
        PsiDocumentManager.getInstance(project).commitAllDocuments()
        val report=WicketPageAnalyzer().analyze(java.classes.single())!!
        val hosts=report.initialContentSetup!!.hosts
        assertEquals(2,hosts.size)
        assertEquals(2,hosts.map {it.contentNodeId}.toSet().size)
        assertTrue(report.matching!!.findings.all {it.code==DiagnosticCode.MATCHED && it.confidence==Confidence.INFERRED})
        assertTrue(report.nodes.filter {it.nodeId!=report.rootNodeId}.all {it.template && !it.idRenameSafe})
    }
    fun testNestedCompetingWritesAreStillInspectedAfterSourceReuse() {
        val java=install()
        change("ConfirmBox","public ConfirmBox(String id)","private class Hidden {void change(){message=null;}}public ConfirmBox(String id)")
        val report=WicketPageAnalyzer().analyze(java.classes.single())!!
        assertNull(report.initialContentSetup)
        assertTrue(report.matching!!.findings.none {it.code==DiagnosticCode.MATCHED})
    }
    fun testThreeMcpViewsShareTheCompleteDeclarationGraphReadOnly() {
        val java=install();val before=java.text to java.modificationStamp
        val query=WicketPageAnalysisRequest();val snapshot=query.capture(project,"example.ConfirmPage")
        val structure=JsonParser.parseString(query.render(snapshot)).asJsonObject
        assertEquals(JsonParser.parseString(WicketContextExporter().export(WicketPageAnalyzer().analyze(java.classes.single())!!)),structure["analysis"])
        val setup=structure["analysis"].asJsonObject["initialContentSetup"]
        assertEquals(setup,JsonParser.parseString(query.render(snapshot,WicketMcpReport.VALIDATION)).asJsonObject["validation"].asJsonObject["initialContentSetup"])
        assertEquals(setup,JsonParser.parseString(query.render(snapshot,WicketMcpReport.CONTEXT,"confirmBox:dialog:form:content")).asJsonObject["context"].asJsonObject["initialContentSetup"])
        query.requireCurrent(project,snapshot);assertEquals(before,java.text to java.modificationStamp)
    }
    fun testUnsavedNestedWriteInvalidatesTheValueSnapshotAndIsReanalyzed() {
        install();val query=WicketPageAnalysisRequest();val snapshot=query.capture(project,"example.ConfirmPage")
        change("ConfirmBox","public ConfirmBox(String id)","private class Hidden {void change(){message=null;}}public ConfirmBox(String id)",false)
        try {query.requireCurrent(project,snapshot);fail("Expected source snapshot invalidation")}
        catch(failure:PageAnalysisFailure) {assertEquals("STALE_SNAPSHOT",failure.code)}
        PsiDocumentManager.getInstance(project).commitAllDocuments()
        assertNull(query.capture(project,"example.ConfirmPage").report.initialContentSetup)
    }
}
