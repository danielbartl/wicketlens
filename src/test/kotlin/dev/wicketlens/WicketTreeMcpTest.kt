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

class WicketTreeMcpTest:WicketProjectPsiTestCase() {
    private fun file(path:String)=PsiManager.getInstance(project).findFile(myFixture.findFileInTempDir(path))!!
    fun testOptionalNodeRelativeContextsShareNativeFactsAndKeepInitialSelection() {
        val java=WicketTreeFixture.install(myFixture); val all=listOf(java,file("src/main/java/example/Fields.java"),file("src/main/resources/example/Fields.html"))
        val before=all.map { it.text to it.modificationStamp }; val query=WicketPageAnalysisRequest()
        val plain=JsonParser.parseString(query.render(query.capture(project,"example.ExamplePage"))).asJsonObject
        val snapshot=query.capture(project,"example.ExamplePage",includeNavigationContexts=true)
        val json=JsonParser.parseString(query.render(snapshot)).asJsonObject
        assertEquals(plain["analysis"],json["analysis"]);assertFalse(plain.has("navigationContexts"))
        val contexts=snapshot.navigationContexts!!.contexts.filter { it.relationship=="TREE_TEMPLATE" };assertEquals(4,contexts.size)
        val native=WicketEditorAnalysis.navigationPages(java).filter { it.report.treeTemplate!=null }.map { WicketContextExporter().export(it.report) }.toSet()
        assertEquals(native,contexts.map { WicketContextExporter().export(it.report) }.toSet())
        val values=json["navigationContexts"].asJsonObject["contexts"].asJsonArray.filter { it.asJsonObject["relationship"].asString=="TREE_TEMPLATE" }
        values.forEach { value -> val tree=value.asJsonObject["treeTemplate"].asJsonObject
            assertEquals("NODE_RELATIVE",tree["pathScope"].asString);assertEquals("unknown",tree["runtimeNodeIds"].asString);assertEquals("unknown",tree["expansionState"].asString)
            assertTrue(value.asJsonObject["bindings"].asJsonArray.all { it.asJsonObject.has("ancestors") && it.asJsonObject["findings"].asJsonArray.single().asJsonObject["confidence"].asString=="INFERRED" })
        }
        try { query.render(snapshot,WicketMcpReport.CONTEXT,"folders:content:plain:label");fail("Template paths must not select the initial graph") } catch(e:PageAnalysisFailure) { assertTrue(e.code,e.code in setOf("COMPONENT_NOT_FOUND","UNRESOLVED_COMPONENT_PATH")) }
        query.requireCurrent(project,snapshot);assertEquals(before,all.map { it.text to it.modificationStamp })
    }
    fun testIdsAndCoreFactorySlotsNavigateBothWaysWithoutRename() {
        WicketTreeFixture.install(myFixture); val fields=file("src/main/java/example/Fields.java")
        val label=PsiTreeUtil.findChildrenOfType(fields,PsiLiteralExpression::class.java).single { it.value=="label" && (it.parent.parent as? PsiNewExpression)?.classReference?.referenceName=="Label" }
        val targets=label.references.filterIsInstance<WicketIdReference>().single().multiResolve(false).map { it.element }
        assertEquals(2,targets.size);assertTrue(targets.all { it is XmlAttributeValue })
        targets.forEach { assertTrue(it!!.references.filterIsInstance<WicketIdReference>().single().multiResolve(false).any { r->r.element==label }) }
        assertNull(WicketIdRenameTarget.at(fields,label.textOffset))
        val nested=file("src/main/java/example/Nested.java") as PsiJavaFile
        val content=nested.classes.single().methods.single { it.name=="newContentComponent" }
        myFixture.configureFromExistingVirtualFile(nested.virtualFile)
        val offset=content.nameIdentifier!!.textOffset
        val core=WicketTreeDeclarationHandler().getGotoDeclarationTargets(nested.findElementAt(offset),offset,myFixture.editor)!!.single() as XmlAttributeValue
        assertEquals("content",core.value)
        val reverse=core.references.filterIsInstance<WicketIdReference>().single().multiResolve(false)
        assertTrue(reverse.any { it.element==content.nameIdentifier });assertEquals(2,reverse.size)
    }
    fun testUnsavedFactoryEditInvalidatesTreeSnapshot()=freshness("src/main/java/example/Nested.java","new Fields(id,","new Fields(\"wrong\",")
    fun testUnsavedResourceEditInvalidatesTreeSnapshot()=freshness("src/main/resources/example/Fields.html","wicket:id='label'","wicket:id='other'")
    private fun freshness(path:String,before:String,after:String) {
        WicketTreeFixture.install(myFixture);val query=WicketPageAnalysisRequest();val snapshot=query.capture(project,"example.ExamplePage",includeNavigationContexts=true);val changed=file(path)
        assertTrue(snapshot.version.sources.any { it.path==changed.virtualFile.path })
        WriteCommandAction.runWriteCommandAction(project) { PsiDocumentManager.getInstance(project).getDocument(changed)!!.setText(changed.text.replace(before,after)) }
        try { query.requireCurrent(project,snapshot);fail("Expected stale tree snapshot") } catch(e:PageAnalysisFailure) { assertEquals("STALE_SNAPSHOT",e.code) }
        PsiDocumentManager.getInstance(project).commitAllDocuments();val next=query.capture(project,"example.ExamplePage",includeNavigationContexts=true)
        assertTrue(snapshot.version!=next.version)
        if(path.endsWith("Nested.java")) assertEquals(2,next.navigationContexts!!.contexts.count { it.relationship=="TREE_TEMPLATE" })
        else assertTrue(next.navigationContexts!!.contexts.filter { it.relationship=="TREE_TEMPLATE" }.all { it.report.matching!!.findings.any { f->f.code!=DiagnosticCode.MATCHED } })
    }
    fun testWholeResponseLimitIncludesTreeContexts() {
        WicketTreeFixture.install(myFixture);val query=WicketPageAnalysisRequest();val snapshot=query.capture(project,"example.ExamplePage",includeNavigationContexts=true);val nav=snapshot.navigationContexts!!
        val entry=nav.contexts.first { it.relationship=="TREE_TEMPLATE" };val oversized=entry.copy(report=entry.report.copy(warnings=listOf(Diagnostic(DiagnosticCode.PARTIAL_ANALYSIS,"x".repeat(600000)))))
        try { query.render(snapshot.copy(navigationContexts=nav.copy(contexts=listOf(oversized))));fail("Expected response bound") } catch(e:PageAnalysisFailure) { assertEquals("RESPONSE_TOO_LARGE",e.code) }
    }
}
