package dev.wicketlens

import com.google.gson.JsonParser
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.psi.*
import dev.wicketlens.editor.WicketEditorAnalysis
import dev.wicketlens.export.WicketContextExporter
import dev.wicketlens.mcp.*
import dev.wicketlens.model.*
import dev.wicketlens.analysis.*

class WicketRegisteredListMcpTest:WicketProjectPsiTestCase() {
    private fun file(name:String)=PsiManager.getInstance(project).findFile(myFixture.findFileInTempDir("src/main/java/example/$name.java"))!!
    fun testRegisteredPopulationSharesAllThreeViewsAndNativeSourceFactsReadOnly() {
        val java=WicketRegisteredListFixture.install(myFixture);val files=listOf(java,file("DecoratedList"),file("Decoration"))
        val before=files.map { it.text to it.modificationStamp };val query=WicketPageAnalysisRequest();val snapshot=query.capture(project,"example.CallbackPage",includeNavigationContexts=true)
        val structure=JsonParser.parseString(query.render(snapshot)).asJsonObject
        val analysis=structure["analysis"].asJsonObject;val population=analysis["populationTemplates"].asJsonArray.single().asJsonObject
        val registration=population["populationTemplate"].asJsonObject["registration"].asJsonObject
        assertEquals("SOURCE_DECLARED_LIST_CALLBACK",registration["kind"].asString)
        assertEquals("reviewed_default_with_behavior_decoration",registration["itemFactory"].asString)
        assertEquals("not_evaluated",registration["callbackExecution"].asString);assertEquals("unknown",registration["behaviorEffects"].asString)
        assertEquals(2,registration["factoryLocations"].asJsonArray.size());assertEquals(1,registration["itemFactoryLocations"].asJsonArray.size())
        val host=analysis["components"].asJsonArray.single { it.asJsonObject.has("registeredListPopulation") }.asJsonObject
        assertEquals(registration,host["registeredListPopulation"])
        val validation=JsonParser.parseString(query.render(snapshot,WicketMcpReport.VALIDATION)).asJsonObject
        assertEquals(registration,validation["validation"].asJsonObject["populationTemplates"].asJsonArray.single().asJsonObject["populationTemplate"].asJsonObject["registration"])
        val context=JsonParser.parseString(query.render(snapshot,WicketMcpReport.CONTEXT,"sections")).asJsonObject
        assertEquals(analysis["populationTemplates"],context["context"].asJsonObject["populationTemplates"])
        val native=WicketEditorAnalysis.navigationPages(java).filter { it.report.populationTemplate?.registration!=null }
        assertEquals(listOf(WicketContextExporter().export(snapshot.report.populationContexts.single())),native.map { WicketContextExporter().export(it.report) }.distinct())
        assertEquals(1,snapshot.navigationContexts!!.contexts.count { it.relationship=="REPEATER_TEMPLATE" && it.report.populationTemplate?.registration!=null })
        try { query.render(snapshot,WicketMcpReport.CONTEXT,"sections:title");fail("Symbolic item paths must not select the primary hierarchy") }
        catch(e:PageAnalysisFailure) { assertTrue(e.code,e.code in setOf("COMPONENT_NOT_FOUND","UNRESOLVED_COMPONENT_PATH")) }
        query.requireCurrent(project,snapshot);assertEquals(before,files.map { it.text to it.modificationStamp })
    }
    fun testUnsavedBridgeChangeInvalidatesSnapshot()=freshness("DecoratedList","callback.accept(item,item.getModelObject());","callback.accept(item,null);")
    fun testUnsavedBehaviorBindingChangeInvalidatesSnapshot()=freshness("Decoration","private final boolean exclusive;","private final boolean exclusive; @Override public void bind(org.apache.wicket.Component component) { component.setVisible(false); }")
    fun testUnsavedBehaviorTemporaryCheckInvalidatesSnapshot()=freshness("Decoration","private final boolean exclusive;","private final boolean exclusive; @Override public boolean isTemporary(org.apache.wicket.Component component) { component.setVisible(false); return false; }")
    fun testUnsavedItemFactoryChangeInvalidatesSnapshot()=freshness("DecoratedList","super.newItem(index,model)","super.newItem(index+1,model)")
    private fun freshness(name:String,before:String,after:String) {
        WicketRegisteredListFixture.install(myFixture);val query=WicketPageAnalysisRequest();val snapshot=query.capture(project,"example.CallbackPage")
        val file=file(name);assertTrue(snapshot.version.sources.toString(),snapshot.version.sources.any { it.path==file.virtualFile.path })
        WriteCommandAction.runWriteCommandAction(project) { PsiDocumentManager.getInstance(project).getDocument(file)!!.setText(file.text.replace(before,after)) }
        try { query.requireCurrent(project,snapshot);fail("Expected stale registered callback snapshot") } catch(e:PageAnalysisFailure) { assertEquals("STALE_SNAPSHOT",e.code) }
        PsiDocumentManager.getInstance(project).commitAllDocuments()
        assertTrue(query.capture(project,"example.CallbackPage").report.nodes.none { it.registeredListPopulation!=null })
    }
    fun testPopulationBoundsStillClearAllRegisteredContexts() {
        val java=WicketRegisteredListFixture.install(myFixture);val type=java.classes.single();val report=WicketPageAnalyzer().analyze(type)!!
        val bounded=WicketPopulationAnalyzer(WicketPopulationAnalyzer.Limits(contexts=0)).analyze(type,report)
        assertFalse(bounded.populationDiscoveryComplete);assertTrue(bounded.populationContexts.isEmpty())
        val query=WicketPageAnalysisRequest();val snapshot=query.capture(project,"example.CallbackPage")
        val json=JsonParser.parseString(query.render(snapshot.copy(report=bounded))).asJsonObject;assertTrue(json["truncated"].asBoolean)
    }
    fun testWholeRegisteredPopulationResponseIsBounded() {
        WicketRegisteredListFixture.install(myFixture);val query=WicketPageAnalysisRequest();val snapshot=query.capture(project,"example.CallbackPage")
        val contexts=snapshot.report.populationContexts.map { it.copy(warnings=listOf(Diagnostic(DiagnosticCode.PARTIAL_ANALYSIS,"x".repeat(600_000)))) }
        try { query.render(snapshot.copy(report=snapshot.report.copy(populationContexts=contexts)));fail("Expected size bound") } catch(e:PageAnalysisFailure) { assertEquals("RESPONSE_TOO_LARGE",e.code) }
    }
}
