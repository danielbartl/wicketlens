package dev.wicketlens

import com.google.gson.JsonParser
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.psi.*
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.xml.XmlAttributeValue
import dev.wicketlens.editor.*
import dev.wicketlens.export.WicketContextExporter
import dev.wicketlens.mcp.*
import dev.wicketlens.model.*

class WicketBaseLayoutMcpTest : WicketProjectPsiTestCase() {
    private fun file(path:String)=PsiManager.getInstance(project).findFile(myFixture.findFileInTempDir(path))!!
    fun testDefaultStructureValidationAndContextShareBaseTemplateFacts() {
        val java=WicketBaseLayoutFixture.install(myFixture);val files=listOf(java,file("src/main/java/example/Html.java"),file("src/main/resources/example/Shell.html"));val before=files.map { it.text to it.modificationStamp }
        val query=WicketPageAnalysisRequest();val snapshot=query.capture(project,"example.Shell")
        val native=WicketEditorAnalysis.pages(java).single().report
        assertEquals(WicketContextExporter().export(native),WicketContextExporter().export(snapshot.report))
        val structure=JsonParser.parseString(query.render(snapshot)).asJsonObject
        val metadata=structure["analysis"].asJsonObject["baseLayoutTemplate"].asJsonObject
        assertEquals("ALL_REVIEWED_ENTRIES",metadata["constructorAgreement"].asString);assertEquals("DECLARED_IMPLEMENTATIONS",metadata["dispatch"].asString)
        assertEquals(3,metadata["constructors"].asJsonArray.size());assertEquals(1,metadata["extensionSlots"].asJsonArray.size())
        for(key in listOf("runtimePresence","runtimeConstructor","subclassOverrides","extensionContents","modelValues")) assertEquals("unknown",metadata[key].asString)
        val validation=JsonParser.parseString(query.render(snapshot,WicketMcpReport.VALIDATION)).asJsonObject["validation"].asJsonObject
        assertEquals(metadata,validation["baseLayoutTemplate"]);assertEquals(5,validation["summary"].asJsonObject["findingsByCode"].asJsonObject["MATCHED"].asInt)
        val context=JsonParser.parseString(query.render(snapshot,WicketMcpReport.CONTEXT,"nav:name")).asJsonObject["context"].asJsonObject
        assertEquals(metadata,context["baseLayoutTemplate"]);assertTrue(context["hierarchyResolved"].asBoolean);assertEquals("INFERRED",context["findings"].asJsonArray.single().asJsonObject["confidence"].asString)
        assertFalse(context["scopeComplete"].asJsonObject["java"].asBoolean);assertFalse(context["scopeComplete"].asJsonObject["markup"].asBoolean)
        query.requireCurrent(project,snapshot);assertEquals(before,files.map { it.text to it.modificationStamp })
    }
    fun testBothConstructorOriginsNavigateAndRemainProtectedFromRename() {
        val java=WicketBaseLayoutFixture.install(myFixture)
        val origins=PsiTreeUtil.findChildrenOfType(java,PsiLiteralExpression::class.java).filter { it.value=="html" };assertEquals(2,origins.size)
        for(origin in origins) {
            val targets=origin.references.filterIsInstance<WicketIdReference>().single().multiResolve(false);assertEquals(1,targets.size)
            val slot=targets.single().element as XmlAttributeValue
            val reverse=slot.references.filterIsInstance<WicketIdReference>().single().multiResolve(false)
            assertTrue(origins.all { candidate -> reverse.any { it.element==candidate } })
            assertNull(WicketIdRenameTarget.at(java,origin.textOffset));assertNull(WicketIdRenameTarget.at(slot.containingFile,slot.textOffset+1))
        }
        val snapshot=WicketPageAnalysisRequest().capture(project,"example.Shell")
        val json=JsonParser.parseString(WicketContextExporter().export(snapshot.report)).asJsonObject
        val html=json["components"].asJsonArray.single { it.asJsonObject["wicketId"]?.let { !it.isJsonNull && it.asString=="html" }==true }.asJsonObject
        assertEquals(2,html["javaLocations"].asJsonArray.size());assertEquals(2,html["idLocations"].asJsonArray.size())
    }
    fun testUnsavedAlternativeConstructorEditInvalidatesSnapshot()=freshness("src/main/java/example/Shell.java","super(model); add(new Html(\"html\"));","super(model); add(new Html(\"html\")); removeAll();")
    fun testUnsavedHookEditInvalidatesSnapshot()=freshness("src/main/java/example/Shell.java","return new WebMarkupContainer(id).setVisible(false);","return new WebMarkupContainer(\"different\").setVisible(false);")
    fun testUnsavedTransparentHelperEditInvalidatesSnapshot()=freshness("src/main/java/example/Html.java","super(id);","super(\"other\");")
    fun testUnsavedExtensionMarkupEditInvalidatesSnapshot()=freshness("src/main/resources/example/Shell.html","<wicket:child/>","<wicket:child/><wicket:child/>")
    private fun freshness(path:String,before:String,after:String) {
        WicketBaseLayoutFixture.install(myFixture);val query=WicketPageAnalysisRequest();val snapshot=query.capture(project,"example.Shell");val changed=file(path)
        assertTrue(snapshot.version.sources.any { it.path==changed.virtualFile.path })
        WriteCommandAction.runWriteCommandAction(project) { PsiDocumentManager.getInstance(project).getDocument(changed)!!.setText(changed.text.replace(before,after)) }
        try { query.requireCurrent(project,snapshot);fail("Expected stale base template") } catch(e:PageAnalysisFailure) { assertEquals("STALE_SNAPSHOT",e.code) }
        PsiDocumentManager.getInstance(project).commitAllDocuments();val next=query.capture(project,"example.Shell")
        assertTrue(snapshot.version!=next.version)
        if(path.endsWith("Html.java") || path.endsWith("Shell.java") && before.startsWith("return")) assertTrue(next.report.matching!!.findings.any { it.code!=DiagnosticCode.MATCHED })
        else assertNull(next.report.baseLayoutTemplate)
    }
    fun testBaseMetadataRetainsWholeResponseBound() {
        WicketBaseLayoutFixture.install(myFixture);val query=WicketPageAnalysisRequest();val snapshot=query.capture(project,"example.Shell")
        val enlarged=snapshot.report.copy(warnings=listOf(Diagnostic(DiagnosticCode.PARTIAL_ANALYSIS,"x".repeat(600000))))
        try { query.render(snapshot.copy(report=enlarged));fail("Expected response bound") } catch(e:PageAnalysisFailure) { assertEquals("RESPONSE_TOO_LARGE",e.code) }
    }
    fun testIncompleteTemplateDoesNotOfferAnAutomaticCounterpartFix() {
        WicketBaseLayoutFixture.install(myFixture);val html=file("src/main/resources/example/Shell.html")
        WriteCommandAction.runWriteCommandAction(project) { PsiDocumentManager.getInstance(project).getDocument(html)!!.setText(html.text.replace("wicket:id='name'","wicket:id='unmatched'"));PsiDocumentManager.getInstance(project).commitAllDocuments() }
        val report=WicketPageAnalysisRequest().capture(project,"example.Shell").report
        assertTrue(report.matching!!.findings.none { it.code in setOf(DiagnosticCode.JAVA_ONLY,DiagnosticCode.MARKUP_ONLY) })
    }
}
