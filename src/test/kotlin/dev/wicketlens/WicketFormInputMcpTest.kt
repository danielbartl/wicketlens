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

class WicketFormInputMcpTest : WicketProjectPsiTestCase() {
    private fun file(path:String)=PsiManager.getInstance(project).findFile(myFixture.findFileInTempDir(path))!!
    fun testOptionalFormStructureSharesNativeGraphsAndLeavesInitialSelectors() {
        val java=WicketFormInputFixture.install(myFixture)
        val files=listOf(java,file("src/main/java/example/FieldBase.java"),file("src/main/resources/example/Field.html"),file("src/main/resources/example/Field\$Input.html"))
        val before=files.map { it.text to it.modificationStamp };val query=WicketPageAnalysisRequest()
        val plain=JsonParser.parseString(query.render(query.capture(project,"example.Field"))).asJsonObject
        val snapshot=query.capture(project,"example.Field",includeNavigationContexts=true)
        val json=JsonParser.parseString(query.render(snapshot)).asJsonObject
        assertEquals(plain["analysis"],json["analysis"]);assertFalse(plain.has("navigationContexts"))
        val form=snapshot.navigationContexts!!.contexts.single { it.relationship=="FORM_TEMPLATE" }
        val native=WicketEditorAnalysis.navigationPages(java).single { it.report.formTemplate!=null }.report
        assertEquals(WicketContextExporter().export(native),WicketContextExporter().export(form.report))
        val context=json["navigationContexts"].asJsonObject["contexts"].asJsonArray.single { it.asJsonObject["relationship"].asString=="FORM_TEMPLATE" }.asJsonObject
        val metadata=context["formTemplate"].asJsonObject
        assertEquals("WRAPPER_RELATIVE",metadata["pathScope"].asString);assertEquals("unknown",metadata["conversionResult"].asString);assertEquals("unknown",metadata["validationResult"].asString)
        assertTrue(metadata["hooks"].asJsonArray.all { it.asJsonObject["execution"].asString=="not_evaluated" })
        assertEquals(6,context["bindings"].asJsonArray.size());assertTrue(context["bindings"].asJsonArray.all { it.asJsonObject.has("ancestors") && it.asJsonObject["findings"].asJsonArray.single().asJsonObject["confidence"].asString=="INFERRED" })
        try { query.render(snapshot,WicketMcpReport.CONTEXT,"input:begin");fail("Template-only paths do not select the initial graph") } catch(e:PageAnalysisFailure) { assertTrue(e.code,e.code in setOf("COMPONENT_NOT_FOUND","UNRESOLVED_COMPONENT_PATH")) }
        query.requireCurrent(project,snapshot);assertEquals(before,files.map { it.text to it.modificationStamp })
    }
    fun testCompositeFieldsAndFactorySlotNavigateBothWaysWithoutRename() {
        val java=WicketFormInputFixture.install(myFixture)
        val begin=PsiTreeUtil.findChildrenOfType(java,PsiLiteralExpression::class.java).single { it.value=="begin" }
        val targets=begin.references.filterIsInstance<WicketIdReference>().single().multiResolve(false)
        assertEquals(1,targets.size);assertTrue(targets.single().element!!.containingFile.name=="Field\$Input.html")
        assertTrue(targets.single().element!!.references.filterIsInstance<WicketIdReference>().single().multiResolve(false).any { it.element==begin })
        assertNull(WicketIdRenameTarget.at(java,begin.textOffset))
        val factory=java.classes.single().methods.single { it.name=="make" }
        myFixture.configureFromExistingVirtualFile(java.virtualFile);val offset=factory.nameIdentifier!!.textOffset
        val slot=WicketFormDeclarationHandler().getGotoDeclarationTargets(java.findElementAt(offset),offset,myFixture.editor)!!.single() as XmlAttributeValue
        assertEquals("input",slot.value);assertEquals("Field.html",slot.containingFile.name)
        assertTrue(slot.references.filterIsInstance<WicketIdReference>().single().multiResolve(false).any { it.element==factory.nameIdentifier })
        assertNull(WicketIdRenameTarget.at(slot.containingFile,slot.textOffset+1))
    }
    fun testUnsavedPrivateHelperEditInvalidatesFormSnapshot()=freshness("src/main/java/example/Field.java","part(\"begin\"","part(\"changed\"")
    fun testUnsavedCacheEditInvalidatesFormSnapshot()=freshness("src/main/java/example/FieldBase.java","add(input);","add(input); removeAll();")
    fun testUnsavedCompositeMarkupEditInvalidatesFormSnapshot()=freshness("src/main/resources/example/Field\$Input.html","wicket:id='begin'","wicket:id='changed'")
    private fun freshness(path:String,before:String,after:String) {
        WicketFormInputFixture.install(myFixture);val query=WicketPageAnalysisRequest();val snapshot=query.capture(project,"example.Field",includeNavigationContexts=true);val changed=file(path)
        assertTrue(snapshot.version.sources.any { it.path==changed.virtualFile.path })
        WriteCommandAction.runWriteCommandAction(project) { PsiDocumentManager.getInstance(project).getDocument(changed)!!.setText(changed.text.replace(before,after)) }
        try { query.requireCurrent(project,snapshot);fail("Expected stale form snapshot") } catch(e:PageAnalysisFailure) { assertEquals("STALE_SNAPSHOT",e.code) }
        PsiDocumentManager.getInstance(project).commitAllDocuments();val next=query.capture(project,"example.Field",includeNavigationContexts=true)
        assertTrue(snapshot.version!=next.version)
        if(path.endsWith("FieldBase.java")) assertTrue(next.navigationContexts!!.contexts.none { it.relationship=="FORM_TEMPLATE" })
        else assertTrue(next.navigationContexts!!.contexts.single { it.relationship=="FORM_TEMPLATE" }.report.matching!!.findings.any { it.code!=DiagnosticCode.MATCHED })
    }
    fun testFormProjectionRemainsSubjectToWholeResponseLimit() {
        WicketFormInputFixture.install(myFixture);val query=WicketPageAnalysisRequest();val snapshot=query.capture(project,"example.Field",includeNavigationContexts=true)
        val nav=snapshot.navigationContexts!!;val form=nav.contexts.single { it.relationship=="FORM_TEMPLATE" }
        val large=form.copy(report=form.report.copy(warnings=listOf(Diagnostic(DiagnosticCode.PARTIAL_ANALYSIS,"x".repeat(600000)))))
        try { query.render(snapshot.copy(navigationContexts=nav.copy(contexts=listOf(large))));fail("Expected response bound") } catch(e:PageAnalysisFailure) { assertEquals("RESPONSE_TOO_LARGE",e.code) }
    }
}
