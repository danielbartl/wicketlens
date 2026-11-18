package dev.wicketlens

import com.google.gson.JsonParser
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.psi.*
import com.intellij.psi.util.PsiTreeUtil
import dev.wicketlens.editor.*
import dev.wicketlens.export.WicketContextExporter
import dev.wicketlens.mcp.*
import dev.wicketlens.model.*

class WicketPopulationMcpTest : WicketProjectPsiTestCase() {
    fun testDefaultThreeViewsAndOptionalNavigationSharePopulationGraphs() {
        val java=WicketPopulationFixture.install(myFixture);val files=listOf(java,file("src/main/java/example/Entry.java"),file("src/main/resources/example/MenuPage.html"));val before=files.map { it.text to it.modificationStamp }
        val query=WicketPageAnalysisRequest();val snapshot=query.capture(project,"example.MenuPage")
        val structure=JsonParser.parseString(query.render(snapshot)).asJsonObject["analysis"].asJsonObject
        assertEquals(2,structure["populationTemplates"].asJsonArray.size());assertTrue(structure["populationDiscoveryComplete"].asBoolean)
        val native=WicketEditorAnalysis.navigationPages(java).filter { it.report.populationTemplate!=null }.map { it.report }
        assertEquals(snapshot.report.populationContexts.map { WicketContextExporter().export(it) },native.map { WicketContextExporter().export(it) })
        for(context in structure["populationTemplates"].asJsonArray) {
            val metadata=context.asJsonObject["populationTemplate"].asJsonObject
            for(key in listOf("runtimePresence","runtimeItemIds","rowCount","modelValues","predicateResults","authorization","visibility")) assertEquals("unknown",metadata[key].asString)
            assertEquals("not_evaluated",metadata["configurationExecution"].asString)
            assertEquals("not_evaluated",metadata["callbackExecution"].asString)
        }
        val validation=JsonParser.parseString(query.render(snapshot,WicketMcpReport.VALIDATION)).asJsonObject["validation"].asJsonObject
        assertEquals(2,validation["populationTemplates"].asJsonArray.size());assertEquals(listOf(4,7),validation["populationTemplates"].asJsonArray.map { it.asJsonObject["summary"].asJsonObject["findingsByCode"].asJsonObject["MATCHED"].asInt })
        val host=JsonParser.parseString(query.render(snapshot,WicketMcpReport.CONTEXT,"sidebar:entries")).asJsonObject["context"].asJsonObject
        assertEquals(structure["populationTemplates"],host["populationTemplates"])
        try { query.render(snapshot,WicketMcpReport.CONTEXT,"sidebar:entries:link:badge");fail("Population paths must not select the primary graph") } catch(e:PageAnalysisFailure) { assertTrue(e.code in setOf("COMPONENT_NOT_FOUND","UNRESOLVED_COMPONENT_PATH")) }
        val withNav=query.capture(project,"example.MenuPage",includeNavigationContexts=true)
        val contexts=withNav.navigationContexts!!.contexts.filter { it.relationship=="REPEATER_TEMPLATE" }
        assertEquals(2,contexts.size);assertEquals(listOf(4,7),contexts.map { it.bindingNodeIds.size })
        assertEquals(native.map { WicketContextExporter().export(it) },contexts.map { WicketContextExporter().export(it.report) })
        query.requireCurrent(project,snapshot);assertEquals(before,files.map { it.text to it.modificationStamp })
    }
    fun testSharedBadgeDeclarationNavigatesToBothMarkupUsesWithoutRename() {
        val java=WicketPopulationFixture.install(myFixture)
        val badge=PsiTreeUtil.findChildrenOfType(java,PsiLiteralExpression::class.java).single { it.value=="badge" }
        val targets=badge.references.filterIsInstance<WicketIdReference>().single().multiResolve(false);assertEquals(2,targets.size)
        for(target in targets) { val element=target.element!!;assertTrue(element.references.filterIsInstance<WicketIdReference>().single().multiResolve(false).any { it.element==badge });assertNull(WicketIdRenameTarget.at(element.containingFile,element.textOffset+1)) }
        assertNull(WicketIdRenameTarget.at(java,badge.textOffset))
    }
    fun testOwnedPopulationsSurviveIndexedCallerDiscoveryExhaustion() {
        val java=WicketPopulationFixture.install(myFixture)
        for(i in 0..17) myFixture.addFileToProject("src/main/java/example/Child$i.java","package example; public class Child$i extends MenuPage {}")
        assertEquals(2,WicketEditorAnalysis.navigationPages(java).count { it.report.populationTemplate!=null })
    }
    fun testUnsavedPredicateInvalidatesPopulationSnapshot()=freshness("src/main/java/example/Entry.java","!items.isEmpty()","items.size()>1")
    fun testUnsavedHelperInvalidatesPopulationSnapshot()=freshness("src/main/java/example/MenuPage.java","new Label(\"badge\"","new Label(\"changed\"")
    fun testUnsavedMarkupInvalidatesPopulationSnapshot()=freshness("src/main/resources/example/MenuPage.html","wicket:id='badge'","wicket:id='changed'")
    private fun file(path:String)=PsiManager.getInstance(project).findFile(myFixture.findFileInTempDir(path))!!
    private fun freshness(path:String,before:String,after:String) {
        WicketPopulationFixture.install(myFixture);val query=WicketPageAnalysisRequest();val snapshot=query.capture(project,"example.MenuPage");val changed=file(path)
        assertTrue(snapshot.version.sources.any { it.path==changed.virtualFile.path })
        WriteCommandAction.runWriteCommandAction(project) { PsiDocumentManager.getInstance(project).getDocument(changed)!!.setText(changed.text.replace(before,after)) }
        try { query.requireCurrent(project,snapshot);fail("Expected stale population snapshot") } catch(e:PageAnalysisFailure) { assertEquals("STALE_SNAPSHOT",e.code) }
        PsiDocumentManager.getInstance(project).commitAllDocuments();assertTrue(snapshot.version!=query.capture(project,"example.MenuPage").version)
    }
    fun testPopulationPayloadRetainsWholeResponseBound() {
        WicketPopulationFixture.install(myFixture);val query=WicketPageAnalysisRequest();val snapshot=query.capture(project,"example.MenuPage");val contexts=snapshot.report.populationContexts
        val large=contexts.first().copy(warnings=listOf(Diagnostic(DiagnosticCode.PARTIAL_ANALYSIS,"x".repeat(600000))))
        try { query.render(snapshot.copy(report=snapshot.report.copy(populationContexts=listOf(large))));fail("Expected response bound") } catch(e:PageAnalysisFailure) { assertEquals("RESPONSE_TOO_LARGE",e.code) }
    }
    fun testDiscoveryExhaustionIsExplicitAndPublishesNoPartialContexts() {
        WicketPopulationFixture.install(myFixture);val query=WicketPageAnalysisRequest();val snapshot=query.capture(project,"example.MenuPage")
        val limited=snapshot.copy(report=snapshot.report.copy(populationContexts=emptyList(),populationDiscoveryComplete=false))
        for(view in listOf(WicketMcpReport.STRUCTURE,WicketMcpReport.VALIDATION,WicketMcpReport.CONTEXT)) {
            val json=JsonParser.parseString(query.render(limited,view,if(view==WicketMcpReport.CONTEXT) "sidebar:entries" else null)).asJsonObject
            assertTrue(json["truncated"].asBoolean)
        }
    }
}
