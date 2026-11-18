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

class WicketRegisteredFormMcpTest : WicketProjectPsiTestCase() {
    private fun file(name:String)=PsiManager.getInstance(project).findFile(myFixture.findFileInTempDir(name))!!
    fun testRegisteredInputProjectionSharesNativeGraphsAndLeavesInitialAnalysis() {
        val java=WicketRegisteredFormFixture.install(myFixture);val query=WicketPageAnalysisRequest()
        val files=listOf(java,file("src/main/java/example/CustomField.java"),file("src/main/java/example/FieldBase.java"),file("src/main/java/example/PhoneInput.java"),file("src/main/resources/example/PhoneInput.html"),file("src/main/resources/example/CustomField.html"))
        val before=files.map { it.text to it.modificationStamp }
        val plain=JsonParser.parseString(query.render(query.capture(project,"example.ContactPage"))).asJsonObject
        val snapshot=query.capture(project,"example.ContactPage",includeNavigationContexts=true)
        val json=JsonParser.parseString(query.render(snapshot)).asJsonObject
        assertEquals(plain["analysis"],json["analysis"]);assertFalse(plain.has("navigationContexts"))
        val form=snapshot.navigationContexts!!.contexts.single { it.relationship=="FORM_TEMPLATE" }
        assertEquals(WicketContextExporter().export(form.report),WicketContextExporter().export(WicketEditorAnalysis.navigationPages(java).single { it.report.formTemplate!=null }.report))
        val exported=json["navigationContexts"].asJsonObject["contexts"].asJsonArray.single { it.asJsonObject["relationship"].asString=="FORM_TEMPLATE" }.asJsonObject
        val metadata=exported["formTemplate"].asJsonObject
        assertEquals("example.ContactPage",metadata["registration"].asJsonObject["ownerClassName"].asString)
        assertEquals("CONSTRUCTOR_REFERENCE",metadata["registration"].asJsonObject["kind"].asString)
        for(key in listOf("runtimePresence","conversionResult","validationResult","requiredState","modelValues")) assertEquals("unknown",metadata[key].asString)
        assertEquals("WRAPPER_RELATIVE",metadata["pathScope"].asString);assertEquals(6,exported["bindings"].asJsonArray.size())
        assertTrue(exported["bindings"].asJsonArray.all { it.asJsonObject["findings"].asJsonArray.single().asJsonObject["confidence"].asString=="INFERRED" })
        assertEquals(setOf("input:code","input:number"),exported["bindings"].asJsonArray.map { it.asJsonObject["componentPath"].asString }.filter { it.startsWith("input:") }.toSet())
        try { query.render(snapshot,WicketMcpReport.CONTEXT,"input:code");fail("Template paths must not select initial graph") } catch(e:PageAnalysisFailure) { assertTrue(e.code in setOf("COMPONENT_NOT_FOUND","UNRESOLVED_COMPONENT_PATH")) }
        query.requireCurrent(project,snapshot);assertEquals(before,files.map { it.text to it.modificationStamp })
    }
    fun testInputIdsAndRegisteredNewTokenNavigateBothWays() {
        val java=WicketRegisteredFormFixture.install(myFixture);val input=file("src/main/java/example/PhoneInput.java")
        val code=PsiTreeUtil.findChildrenOfType(input,PsiLiteralExpression::class.java).single { it.value=="code" }
        val targets=code.references.filterIsInstance<WicketIdReference>().single().multiResolve(false)
        assertEquals(1,targets.size);assertEquals("PhoneInput.html",targets.single().element!!.containingFile.name)
        assertTrue(targets.single().element!!.references.filterIsInstance<WicketIdReference>().single().multiResolve(false).any { it.element==code })
        val reference=PsiTreeUtil.findChildOfType(java,PsiMethodReferenceExpression::class.java)!!
        myFixture.configureFromExistingVirtualFile(java.virtualFile);val offset=reference.textRange.endOffset-2
        val slot=WicketFormDeclarationHandler().getGotoDeclarationTargets(java.findElementAt(offset),offset,myFixture.editor)!!.single() as XmlAttributeValue
        assertEquals("input",slot.value);assertEquals("CustomField.html",slot.containingFile.name)
        assertTrue(slot.references.filterIsInstance<WicketIdReference>().single().multiResolve(false).any { it.element==reference })
        assertNull(WicketFormDeclarationHandler().getGotoDeclarationTargets(java.findElementAt(reference.textOffset),reference.textOffset,myFixture.editor))
        assertNull(WicketIdRenameTarget.at(slot.containingFile,slot.textOffset+1))
    }
    fun testInputAndWrapperResourceQueriesDiscoverRegistration() {
        WicketRegisteredFormFixture.install(myFixture);val query=WicketPageAnalysisRequest()
        for(name in listOf("example.PhoneInput","example.CustomField")) {
            val snapshot=query.capture(project,name,includeNavigationContexts=true)
            assertTrue(snapshot.navigationContexts!!.complete)
            assertEquals(name,1,snapshot.navigationContexts.contexts.count { it.report.formTemplate?.registration!=null })
        }
    }
    fun testUnsavedRegistrationInvalidatesSnapshot()=freshness("src/main/java/example/ContactPage.java","PhoneInput::new","(id,model)->new PhoneInput(id,model)")
    fun testUnsavedStorageInvalidatesSnapshot()=freshness("src/main/java/example/CustomField.java","private final java.util.function","private java.util.function")
    fun testUnsavedInputMarkupInvalidatesSnapshot()=freshness("src/main/resources/example/PhoneInput.html","wicket:id='number'","wicket:id='other'")
    private fun freshness(path:String,before:String,after:String) {
        WicketRegisteredFormFixture.install(myFixture);val query=WicketPageAnalysisRequest();val snapshot=query.capture(project,"example.ContactPage",includeNavigationContexts=true);val changed=file(path)
        assertTrue(snapshot.version.sources.any { it.path==changed.virtualFile.path })
        WriteCommandAction.runWriteCommandAction(project) { PsiDocumentManager.getInstance(project).getDocument(changed)!!.setText(changed.text.replace(before,after)) }
        try { query.requireCurrent(project,snapshot);fail("Expected stale registration") } catch(e:PageAnalysisFailure) { assertEquals("STALE_SNAPSHOT",e.code) }
        PsiDocumentManager.getInstance(project).commitAllDocuments();val next=query.capture(project,"example.ContactPage",includeNavigationContexts=true)
        if(path.endsWith(".java")) assertTrue(next.navigationContexts!!.contexts.none { it.relationship=="FORM_TEMPLATE" })
        else assertTrue(next.navigationContexts!!.contexts.single { it.relationship=="FORM_TEMPLATE" }.report.matching!!.findings.any { it.code!=DiagnosticCode.MATCHED })
    }
    fun testRegisteredProjectionRetainsWholeResponseBound() {
        WicketRegisteredFormFixture.install(myFixture);val query=WicketPageAnalysisRequest();val snapshot=query.capture(project,"example.ContactPage",includeNavigationContexts=true);val nav=snapshot.navigationContexts!!;val context=nav.contexts.single { it.relationship=="FORM_TEMPLATE" }
        val large=context.copy(report=context.report.copy(warnings=listOf(Diagnostic(DiagnosticCode.PARTIAL_ANALYSIS,"x".repeat(600000)))))
        try { query.render(snapshot.copy(navigationContexts=nav.copy(contexts=listOf(large))));fail("Expected response bound") } catch(e:PageAnalysisFailure) { assertEquals("RESPONSE_TOO_LARGE",e.code) }
    }
}
