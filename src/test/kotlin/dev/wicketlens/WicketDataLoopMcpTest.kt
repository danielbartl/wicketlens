package dev.wicketlens

import com.google.gson.JsonParser
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.psi.*
import dev.wicketlens.export.WicketContextExporter
import dev.wicketlens.mcp.*
import dev.wicketlens.model.DiagnosticCode

class WicketDataLoopMcpTest : WicketProjectPsiTestCase() {
    fun testLoopIsolationUsesTheSharedGraphAndExistingThreeViewsReadOnly() {
        val java=WicketDataLoopFixture.install(myFixture)
        val before=java.text to java.modificationStamp
        val query=WicketPageAnalysisRequest();val snapshot=query.capture(project,"example.ExamplePage")
        assertEquals(1,snapshot.report.matching!!.findings.count { it.code==DiagnosticCode.MATCHED })
        val structure=JsonParser.parseString(query.render(snapshot)).asJsonObject
        assertEquals(JsonParser.parseString(WicketContextExporter().export(snapshot.report)),structure["analysis"])
        assertTrue(structure.toString().contains("collection contents remain unknown"))
        assertEquals(1,structure["schemaVersion"].asInt)
        val validation=query.render(snapshot,WicketMcpReport.VALIDATION)
        val context=JsonParser.parseString(query.render(snapshot,WicketMcpReport.CONTEXT,"title")).asJsonObject["context"].asJsonObject
        assertTrue(validation,validation.contains("collection contents remain unknown"))
        assertTrue(context.toString(),context["hierarchyResolved"].asBoolean)
        assertEquals("MATCHED",context["findings"].asJsonArray.single().asJsonObject["code"].asString)
        query.requireCurrent(project,snapshot);assertEquals(before,java.text to java.modificationStamp)
    }
    fun testUnsafeLoopStillWithholdsBindingsAndRetainsTheBlockingReason() {
        WicketDataLoopFixture.install(myFixture,"for(int i=0;i<3;i++) { removeAll(); }")
        val query=WicketPageAnalysisRequest();val snapshot=query.capture(project,"example.ExamplePage")
        assertTrue(snapshot.report.matching!!.findings.none { it.code==DiagnosticCode.MATCHED })
        for(view in listOf(WicketMcpReport.STRUCTURE,WicketMcpReport.VALIDATION)) {
            val rendered=query.render(snapshot,view)
            assertTrue(rendered,rendered.contains("Constructor data loop is not isolated"))
            assertTrue(rendered,rendered.contains("org.apache.wicket.MarkupContainer.removeAll"))
        }
    }
    fun testUncommittedLoopEditInvalidatesTheSnapshot() {
        val java=WicketDataLoopFixture.install(myFixture)
        val query=WicketPageAnalysisRequest();val snapshot=query.capture(project,"example.ExamplePage")
        WriteCommandAction.runWriteCommandAction(project) { PsiDocumentManager.getInstance(project).getDocument(java)!!.setText(java.text.replace("i<=23","i<=24")) }
        try { query.requireCurrent(project,snapshot);fail("Expected stale loop proof") }
        catch(e:PageAnalysisFailure) { assertEquals("STALE_SNAPSHOT",e.code) }
    }
    fun testImportedBoundsRemainFreshnessDependencies() {
        val limits=myFixture.addFileToProject("src/main/java/example/Limits.java","package example;public class Limits {public static final int COUNT=23;}")
        WicketDataLoopFixture.install(myFixture,WicketDataLoopFixture.body.replace("i<=23","i<=Limits.COUNT"))
        val query=WicketPageAnalysisRequest();val snapshot=query.capture(project,"example.ExamplePage")
        assertEquals(1,snapshot.report.matching!!.findings.count { it.code==DiagnosticCode.MATCHED })
        assertTrue(snapshot.version.sources.any { it.path==limits.virtualFile.path })
        WriteCommandAction.runWriteCommandAction(project) { PsiDocumentManager.getInstance(project).getDocument(limits)!!.setText(limits.text.replace("COUNT=23","COUNT=24")) }
        try { query.requireCurrent(project,snapshot);fail("Expected stale bound") }
        catch(e:PageAnalysisFailure) { assertEquals("STALE_SNAPSHOT",e.code) }
    }
    fun testWholeResponseBoundsStillApplyToLoopDiagnostics() {
        WicketDataLoopFixture.install(myFixture)
        val query=WicketPageAnalysisRequest();val snapshot=query.capture(project,"example.ExamplePage")
        val report=snapshot.report.copy(warnings=snapshot.report.warnings+dev.wicketlens.model.Diagnostic(DiagnosticCode.PARTIAL_ANALYSIS,"x".repeat(600000)))
        try { query.render(snapshot.copy(report=report));fail("Expected response bound") }
        catch(e:PageAnalysisFailure) { assertEquals("RESPONSE_TOO_LARGE",e.code) }
    }
}
